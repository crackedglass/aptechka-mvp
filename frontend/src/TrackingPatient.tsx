import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type FormEvent,
} from "react";
import { fields, Panel, roles, type Run } from "./tracking-form";
import TrackingPlans from "./TrackingPlans";
import {
  ApiError,
  localInput,
  showTime,
  type Account,
  type Action,
  type Day,
  type Handler,
  type Invitation,
  type Notice,
  type Occurrence,
  type Plan,
  type Profile,
  type Request,
  type Revision,
} from "./tracking-api";

const statuses = {
  PENDING: "Ожидается",
  OVERDUE: "Просрочено",
  COMPLETED: "Выполнено",
  CANCELLED: "Отменено",
};
const actions: Record<Action, string> = {
  CLAIM: "Взять в работу",
  RELEASE: "Освободить",
  TAKEOVER: "Перенять",
  REPORT: "Подтвердить выполнение",
  CORRECT: "Исправить время",
  RETRACT: "Отозвать отчёт",
  CANCEL: "Отменить событие",
  REOPEN: "Открыть снова",
};
type Refresh = () => Promise<void>;

export default function TrackingPatient({
  account,
  profile,
  request,
  run,
  refreshProfiles,
}: {
  account: Account;
  profile: Profile;
  request: Request;
  run: Run;
  refreshProfiles: Refresh;
}) {
  const [date, setDate] = useState("");
  const [day, setDay] = useState<Day>();
  const [plans, setPlans] = useState<Plan[]>([]);
  const [notices, setNotices] = useState<Notice[]>([]);
  const [claims, setClaims] = useState<Occurrence[]>([]);
  const [invitations, setInvitations] = useState<Invitation[]>([]);
  const [issued, setIssued] = useState("");
  const [problem, setProblem] = useState("");
  const [view, setView] = useState<"day" | "plans" | "access" | "inbox">("day");
  const loadSequence = useRef(0);
  const root = `/tracking/patients/${profile.id}`;
  const manager = profile.role === "MANAGER";

  const load = useCallback(
    async (signal?: AbortSignal) => {
      const sequence = ++loadSequence.current;
      const current = () =>
        !signal?.aborted && sequence === loadSequence.current;
      const next = await request<Day>(
        `${root}/day${date ? `?date=${date}` : ""}`,
      );
      if (!current()) return;
      setDay(next);
      setProblem("");
      if (manager && view === "plans") {
        const nextPlans = await request<Plan[]>(`${root}/plans`);
        if (current()) setPlans(nextPlans);
      }
      if (manager && view === "inbox") {
        const nextNotices = await request<Notice[]>(`${root}/inbox`);
        if (current()) setNotices(nextNotices);
      }
      if (manager && view === "access") {
        const [nextClaims, nextInvites] = await Promise.all([
          request<Occurrence[]>(`${root}/claims`),
          request<Invitation[]>(`${root}/invitations`),
        ]);
        if (current()) {
          setClaims(nextClaims);
          setInvitations(nextInvites);
        }
      }
    },
    [date, manager, request, root, view],
  );

  useEffect(() => {
    let active: AbortController | undefined;
    const refresh = () => {
      active?.abort();
      active = new AbortController();
      const { signal } = active;
      void load(signal).catch((failure) => {
        if (signal.aborted) return;
        if (
          failure instanceof ApiError &&
          [401, 403, 404].includes(failure.status)
        ) {
          setDay(undefined);
          setPlans([]);
          setClaims([]);
          setInvitations([]);
          setNotices([]);
        }
        setProblem(
          failure instanceof Error
            ? failure.message
            : "Не удалось обновить данные",
        );
      });
    };
    refresh();
    const timer = window.setInterval(refresh, 8_000);
    window.addEventListener("focus", refresh);
    window.addEventListener("online", refresh);
    return () => {
      active?.abort();
      window.clearInterval(timer);
      window.removeEventListener("focus", refresh);
      window.removeEventListener("online", refresh);
    };
  }, [load]);

  const refresh = () => load();
  const goTo = (slot: { day: string }) => {
    setDate(slot.day);
    setView("day");
  };

  return (
    <>
      <nav className="tracking-actions" aria-label="Разделы">
        <button
          type="button"
          aria-pressed={view === "day"}
          onClick={() => setView("day")}
        >
          Календарь
        </button>
        {manager && (
          <>
            <button
              type="button"
              aria-pressed={view === "plans"}
              onClick={() => setView("plans")}
            >
              Планы
            </button>
            <button
              type="button"
              aria-pressed={view === "access"}
              onClick={() => setView("access")}
            >
              Доступ
            </button>
            <button
              type="button"
              aria-pressed={view === "inbox"}
              onClick={() => setView("inbox")}
            >
              Уведомления
            </button>
          </>
        )}
      </nav>
      {problem && (
        <p className="tracking-error" role="alert">
          {problem}
        </p>
      )}
      {view === "day" && (
        <Panel title={profile.name}>
          <div className="tracking-actions">
            <label>
              День · {profile.zone}
              <input
                type="date"
                value={date || day?.today || ""}
                onChange={(event) => setDate(event.target.value)}
              />
            </label>
            <button type="button" onClick={() => setDate("")}>
              Сегодня
            </button>
          </div>
          {day && day.date === (date || day.today) && (
            <>
              {!day.occurrences.length && (
                <p>На этот день ничего не запланировано.</p>
              )}
              <div className="tracking-list">
                {day.occurrences.map((item) => (
                  <Task
                    key={item.id}
                    item={item}
                    account={account}
                    profile={profile}
                    request={request}
                    root={root}
                    run={run}
                    refresh={refresh}
                  />
                ))}
              </div>
            </>
          )}
        </Panel>
      )}
      {manager && day && view === "plans" && (
        <TrackingPlans
          root={root}
          today={day.today}
          zone={profile.zone}
          plans={plans}
          request={request}
          run={run}
          refresh={refresh}
        />
      )}
      {manager && day && view === "access" && (
        <Panel title="Профиль и доступ">
          <form
            className="tracking-form"
            onSubmit={(event) => {
              const get = fields(event);
              void run(async () => {
                await request(root, "PUT", {
                  name: get("name"),
                  zone: get("zone"),
                });
                await refreshProfiles();
                await refresh();
              });
            }}
          >
            <label>
              Имя подопечного
              <input
                name="name"
                defaultValue={profile.name}
                required
                maxLength={100}
              />
            </label>
            <label>
              Часовой пояс IANA
              <input name="zone" defaultValue={profile.zone} required />
            </label>
            <button>Сохранить профиль</button>
          </form>
          <form
            className="tracking-form"
            onSubmit={(event) => {
              const get = fields(event);
              void run(async () => {
                const result = await request<{ token: string }>(
                  `${root}/invitations`,
                  "POST",
                  { role: get("role"), email: get("email") },
                );
                setIssued(result.token);
                await refresh();
              });
            }}
          >
            <label>
              Пригласить
              <select name="role">
                <option value="CAREGIVER">Помощника</option>
                <option value="PATIENT">Подопечного</option>
              </select>
            </label>
            <label>
              Почта
              <input type="email" name="email" maxLength={254} required />
            </label>
            <button>Создать код</button>
          </form>
          {issued && (
            <label>
              Передайте код приглашённому пользователю
              <input
                readOnly
                value={issued}
                onFocus={(event) => event.target.select()}
              />
            </label>
          )}
          <ul className="tracking-list">
            {invitations.map((invite) => {
              const pending =
                invite.state === "PENDING" &&
                new Date(invite.expires).getTime() > Date.now();
              const text = {
                ACCEPTED: "Принято",
                REVOKED: "Отозвано",
                SUPERSEDED: "Заменено",
                PENDING: pending ? "Ожидает ответа" : "Истекло",
              };
              return (
                <li key={invite.id}>
                  {invite.email} · {roles[invite.role]} ·{" "}
                  {text[invite.state as keyof typeof text]}
                  {pending && (
                    <button
                      type="button"
                      onClick={() =>
                        void run(async () => {
                          await request(
                            `${root}/invitations/${invite.id}`,
                            "DELETE",
                          );
                          setIssued("");
                          await refresh();
                        })
                      }
                    >
                      Отозвать приглашение
                    </button>
                  )}
                </li>
              );
            })}
          </ul>
          {profile.caregiver && (
            <>
              <p>
                Новый помощник получит доступ после принятия приглашения и
                освобождения задач текущего помощника.
              </p>
              {claims
                .filter((claim) => claim.handler?.actor === profile.caregiver)
                .map((claim) => (
                  <button
                    type="button"
                    key={claim.id}
                    onClick={() => goTo(claim.slot)}
                  >
                    В работе · {claim.instruction.title} · {claim.slot.day}{" "}
                    {claim.slot.time}
                  </button>
                ))}
              <button
                type="button"
                onClick={() =>
                  void run(async () => {
                    await request(`${root}/caregiver`, "DELETE");
                    await refreshProfiles();
                    await refresh();
                  })
                }
              >
                Отозвать доступ помощника
              </button>
            </>
          )}
        </Panel>
      )}
      {manager && day && view === "inbox" && (
        <Panel title="Уведомления">
          {!notices.length && <p>Уведомлений пока нет.</p>}
          <ul className="tracking-list">
            {notices.map((notice) => (
              <li key={notice.id}>
                <strong>
                  {notice.kind === "PREVIOUS_DAY"
                    ? "Отчёт за прошлый день"
                    : "Последний отчёт отозван"}
                </strong>
                <div>
                  {notice.title} · {notice.slot.day}{" "}
                  {notice.slot.time.slice(0, 5)}
                </div>
                <div>
                  {showTime(notice.recorded, profile.zone)} ·{" "}
                  {notice.read ? "Прочитано" : "Новое"}
                </div>
                <div className="tracking-actions">
                  <button type="button" onClick={() => goTo(notice.slot)}>
                    Открыть день {notice.slot.day}
                  </button>
                  <button
                    type="button"
                    onClick={() =>
                      void run(async () => {
                        await request(`${root}/inbox/${notice.id}`, "PUT", {
                          requestId: crypto.randomUUID(),
                          read: !notice.read,
                        });
                        await refresh();
                      })
                    }
                  >
                    {notice.read
                      ? "Отметить непрочитанным"
                      : "Отметить прочитанным"}
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </Panel>
      )}
    </>
  );
}

function Task({
  item,
  account,
  profile,
  request,
  root,
  run,
  refresh,
}: {
  item: Occurrence;
  account: Account;
  profile: Profile;
  request: Request;
  root: string;
  run: Run;
  refresh: Refresh;
}) {
  const [selection, setSelection] = useState<{
    action: Action;
    handler: Handler | null;
    latest: Revision | undefined;
  }>();
  const action = selection?.action;
  const [acknowledged, setAcknowledged] = useState<number>();
  const own = item.reports.find((lineage) => lineage[0].author === account.id);
  const latest = own?.[own.length - 1];
  const handler = item.handler;
  const open = item.status !== "CANCELLED";
  const incomplete = open && item.status !== "COMPLETED";
  const manager = profile.role === "MANAGER";
  const time = (value: string) => showTime(value, profile.zone);

  function send(next: Action, details = {}) {
    void run(async () => {
      try {
        await request(`${root}/occurrences`, "POST", {
          requestId: crypto.randomUUID(),
          day: item.slot.day,
          occurrence: item.id,
          action: next,
          ...details,
        });
        setSelection(undefined);
        setAcknowledged(undefined);
      } catch (failure) {
        if (failure instanceof ApiError && failure.status === 409) {
          setAcknowledged(undefined);
          await refresh();
        }
        throw failure;
      }
      await refresh();
    });
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    const get = fields(event);
    if (!action) return;
    send(action, {
      actual: get("actual") ? new Date(get("actual")).toISOString() : null,
      reason: get("reason") || null,
      expectedHandler: selection?.handler,
      acknowledgment:
        action === "REPORT" && item.completedAt ? acknowledged : undefined,
      author:
        action === "CORRECT" || action === "RETRACT"
          ? selection?.latest?.author
          : undefined,
      reportRevision: selection?.latest?.seq,
    });
  }

  function choose(next: Action) {
    setSelection({ action: next, handler, latest });
    setAcknowledged(undefined);
  }

  function history(revision: Revision) {
    const label =
      revision.action === "RETRACT"
        ? "Отозван"
        : revision.action === "CORRECT"
          ? "Исправлен"
          : "Записан";
    return (
      <li key={revision.seq}>
        {label} {time(revision.recorded)}
        {revision.actual && <> · Выполнено {time(revision.actual)}</>}
        {revision.reason && <> · {revision.reason}</>}
      </li>
    );
  }

  return (
    <article className="tracking-task">
      <h3>
        {item.slot.time.slice(0, 5)} · {item.instruction.title}
      </h3>
      {(item.zone !== profile.zone ||
        showTime(item.slot.scheduled, item.zone).slice(-5) !==
          item.slot.time.slice(0, 5)) && (
        <p>
          Запланировано {showTime(item.slot.scheduled, item.zone)} ({item.zone})
        </p>
      )}
      <p>
        {item.instruction.kind === "MEDICATION"
          ? `Полная доза ${item.instruction.dose} ${item.instruction.unit}`
          : item.instruction.instructions}
      </p>
      <p>
        <strong>{statuses[item.status]}</strong>
        {item.completedAt && (
          <> · Уже отмечено выполненным {time(item.completedAt)}</>
        )}
        {handler && (
          <>
            {" "}
            · В работе у {handler.name} с {time(handler.since)}
          </>
        )}
      </p>
      <div className="tracking-actions">
        {incomplete && !handler && (
          <button type="button" onClick={() => send("CLAIM")}>
            {actions.CLAIM}
          </button>
        )}
        {handler?.actor === account.id && (
          <button
            type="button"
            onClick={() => send("RELEASE", { expectedHandler: handler })}
          >
            {actions.RELEASE}
          </button>
        )}
        {incomplete && handler && handler.actor !== account.id && (
          <button type="button" onClick={() => choose("TAKEOVER")}>
            {actions.TAKEOVER}
          </button>
        )}
        {open && !own && (
          <button type="button" onClick={() => choose("REPORT")}>
            {actions.REPORT}
          </button>
        )}
        {latest && latest.action !== "RETRACT" && (
          <>
            <button type="button" onClick={() => choose("CORRECT")}>
              {actions.CORRECT}
            </button>
            <button type="button" onClick={() => choose("RETRACT")}>
              {actions.RETRACT}
            </button>
          </>
        )}
        {manager && incomplete && !handler && !item.reports.length && (
          <button type="button" onClick={() => choose("CANCEL")}>
            {actions.CANCEL}
          </button>
        )}
        {manager && !open && (
          <button type="button" onClick={() => send("REOPEN")}>
            {actions.REOPEN}
          </button>
        )}
      </div>
      {action && (
        <form className="tracking-form" key={action} onSubmit={submit}>
          <h4>{actions[action]}</h4>
          {action === "TAKEOVER" && selection?.handler && (
            <p>Текущий исполнитель при выборе · {selection.handler.name}</p>
          )}
          {(action === "REPORT" || action === "CORRECT") && (
            <label>
              Фактическое время (часовой пояс устройства)
              <input
                name="actual"
                type="datetime-local"
                required
                max={localInput()}
                defaultValue={localInput(
                  action === "CORRECT" && selection?.latest?.actual
                    ? new Date(selection.latest.actual)
                    : new Date(),
                )}
              />
            </label>
          )}
          {(action === "CORRECT" ||
            action === "RETRACT" ||
            action === "TAKEOVER") && (
            <label>
              Причина
              <textarea name="reason" required maxLength={1000} />
            </label>
          )}
          {action === "REPORT" && item.completedAt && (
            <label>
              <input
                type="checkbox"
                checked={acknowledged === item.completionRevision}
                onChange={(event) =>
                  setAcknowledged(
                    event.target.checked ? item.completionRevision : undefined,
                  )
                }
              />
              Уже отмечено выполненным {time(item.completedAt)}. Подтверждаю то
              же действие.
            </label>
          )}
          <div className="tracking-actions">
            <button
              disabled={
                action === "REPORT" &&
                !!item.completedAt &&
                acknowledged !== item.completionRevision
              }
            >
              Сохранить
            </button>
            <button type="button" onClick={() => setSelection(undefined)}>
              Закрыть
            </button>
          </div>
        </form>
      )}
      {item.reports.length > 0 && (
        <details>
          <summary>История отчётов</summary>
          {item.reports.map((lineage) => (
            <div key={lineage[0].seq}>
              <h4>
                {lineage[0].author === account.id
                  ? "Ваш отчёт"
                  : lineage[0].authorName}
              </h4>
              <ol>{lineage.map(history)}</ol>
            </div>
          ))}
        </details>
      )}
    </article>
  );
}
