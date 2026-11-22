import { useState, type FormEvent } from "react";
import { fields, Panel, type Run } from "./tracking-form";
import {
  localInput,
  showTime,
  type Instruction,
  type Plan,
  type Preview,
  type Request,
} from "./tracking-api";

const week = [
  ["MONDAY", "Пн"],
  ["TUESDAY", "Вт"],
  ["WEDNESDAY", "Ср"],
  ["THURSDAY", "Чт"],
  ["FRIDAY", "Пт"],
  ["SATURDAY", "Сб"],
  ["SUNDAY", "Вс"],
];

export default function TrackingPlans({
  root,
  today,
  zone,
  plans,
  request,
  run,
  refresh,
}: {
  root: string;
  today: string;
  zone: string;
  plans: Plan[];
  request: Request;
  run: Run;
  refresh: () => Promise<void>;
}) {
  const [editing, setEditing] = useState<Plan | "new" | null>(null);
  const [preview, setPreview] = useState<{
    view: Preview;
    draft: Instruction | null;
    stop: boolean;
  }>();
  const [kind, setKind] = useState<Instruction["kind"]>("MEDICATION");
  const [repeating, setRepeating] = useState(false);
  const original =
    editing && editing !== "new" ? editing.instruction : undefined;

  function edit(plan: Plan | "new") {
    const initial = plan === "new" ? undefined : plan.instruction;
    setKind(initial?.kind ?? "MEDICATION");
    setRepeating(Boolean(initial?.weekdays.length));
    setPreview(undefined);
    setEditing(plan);
  }

  function submit(event: FormEvent<HTMLFormElement>, stop: boolean) {
    const get = fields(event);
    const draft: Instruction | null = stop
      ? null
      : {
          kind,
          title: get("title"),
          dose: kind === "MEDICATION" ? Number(get("dose")) : null,
          unit: kind === "MEDICATION" ? get("unit") : null,
          instructions: kind === "PROCEDURE" ? get("instructions") : null,
          start: get("start"),
          end: repeating ? get("end") || null : null,
          weekdays: repeating
            ? week.filter(([day]) => get(day)).map(([day]) => day)
            : [],
          times: get("times")
            .split(",")
            .map((time) => time.trim()),
        };
    const effective = get("effective");
    void run(async () => {
      if (repeating && draft && !draft.weekdays.length)
        throw new Error("Выберите хотя бы один день недели");
      if (editing === "new") {
        await request(`${root}/plans`, "POST", {
          requestId: crypto.randomUUID(),
          instruction: draft,
        });
        setEditing(null);
        await refresh();
      } else if (editing) {
        if (!effective) throw new Error("Укажите время применения");
        const at = new Date(effective).toISOString();
        const view = await request<Preview>(
          `${root}/plans/${editing.plan}/preview?effective=${encodeURIComponent(at)}`,
        );
        if (view.version !== editing.version) {
          setEditing(null);
          await refresh();
          throw new Error("План изменился. Откройте его снова.");
        }
        setPreview({ view, draft, stop });
      }
    });
  }

  function save() {
    if (!preview || !editing || editing === "new") return;
    void run(async () => {
      await request(`${root}/plans`, "POST", {
        requestId: crypto.randomUUID(),
        plan: editing.plan,
        version: preview.view.version,
        effective: preview.view.effective,
        instruction: preview.draft,
        stop: preview.stop,
      });
      setPreview(undefined);
      setEditing(null);
      await refresh();
    });
  }

  return (
    <Panel title="Планы лечения">
      <button type="button" onClick={() => edit("new")}>
        Создать план
      </button>
      <ul className="tracking-list">
        {plans.map((plan) => (
          <li key={plan.plan}>
            {plan.instruction.title}{" "}
            <button type="button" onClick={() => edit(plan)}>
              Изменить
            </button>
          </li>
        ))}
      </ul>
      {editing && (
        <form
          key={editing === "new" ? editing : editing.plan}
          className="tracking-form"
          onChange={() => setPreview(undefined)}
          onSubmit={(event) =>
            submit(
              event,
              (event.nativeEvent as SubmitEvent).submitter?.getAttribute(
                "value",
              ) === "stop",
            )
          }
        >
          <label>
            Вид
            <select
              value={kind}
              onChange={(event) =>
                setKind(event.target.value as Instruction["kind"])
              }
            >
              <option value="MEDICATION">Лекарство</option>
              <option value="PROCEDURE">Процедура</option>
            </select>
          </label>
          <label>
            Название
            <input
              name="title"
              defaultValue={original?.title}
              required
              maxLength={200}
            />
          </label>
          {kind === "MEDICATION" ? (
            <>
              <label>
                Полная доза
                <input
                  name="dose"
                  type="number"
                  step="any"
                  defaultValue={original?.dose ?? undefined}
                  required
                />
              </label>
              <label>
                Единица измерения
                <input
                  name="unit"
                  defaultValue={original?.unit ?? undefined}
                  required
                  maxLength={60}
                />
              </label>
            </>
          ) : (
            <label>
              Инструкция
              <textarea
                name="instructions"
                defaultValue={original?.instructions ?? undefined}
                required
                maxLength={4000}
              />
            </label>
          )}
          <label>
            Начало
            <input
              name="start"
              type="date"
              defaultValue={original?.start ?? today}
              required
            />
          </label>
          <label>
            <input
              type="checkbox"
              checked={repeating}
              onChange={(event) => setRepeating(event.target.checked)}
            />{" "}
            Повторять по дням недели
          </label>
          {repeating && (
            <>
              <div className="tracking-actions">
                {week.map(([day, label]) => (
                  <label key={day}>
                    <input
                      name={day}
                      type="checkbox"
                      defaultChecked={original?.weekdays.includes(day)}
                    />{" "}
                    {label}
                  </label>
                ))}
              </div>
              <label>
                Конец
                <input
                  name="end"
                  type="date"
                  defaultValue={original?.end ?? undefined}
                />
              </label>
            </>
          )}
          <label>
            Время подопечного{repeating && " (ЧЧ:ММ через запятую)"}
            <input
              name="times"
              type={repeating ? "text" : "time"}
              defaultValue={
                original?.times.map((time) => time.slice(0, 5)).join(", ") ??
                "09:00"
              }
              required
            />
          </label>
          {editing !== "new" && (
            <label>
              Применить с (часовой пояс устройства)
              <input
                name="effective"
                type="datetime-local"
                defaultValue={localInput()}
                required
              />
            </label>
          )}
          <div className="tracking-actions">
            <button>
              {editing === "new" ? "Создать" : "Предварительный просмотр"}
            </button>
            {editing !== "new" && (
              <button value="stop" formNoValidate>
                Просмотр остановки плана
              </button>
            )}
            <button
              type="button"
              onClick={() => {
                setEditing(null);
                setPreview(undefined);
              }}
            >
              Закрыть
            </button>
          </div>
        </form>
      )}
      {preview && (
        <section className="tracking-preview">
          <h3>{preview.stop ? "Остановка плана" : "Изменение плана"}</h3>
          <p>
            Действует с {showTime(preview.view.effective, zone)} ({zone}).
          </p>
          {preview.view.claimed.map((item) => (
            <p key={item.id}>
              В работе · {item.slot.day} {item.slot.time} · {item.handler?.name}
            </p>
          ))}
          {preview.view.reported.map((item) => (
            <p key={item.id}>
              Отчёт сохранится · {item.slot.day} {item.slot.time}
            </p>
          ))}
          <button type="button" onClick={save}>
            Подтвердить {preview.stop ? "остановку" : "изменение"}
          </button>
        </section>
      )}
    </Panel>
  );
}
