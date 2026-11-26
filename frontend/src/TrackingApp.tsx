import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type FormEvent,
} from "react";
import {
  ApiError,
  trackingClient,
  type Account,
  type Profile,
  type Request,
} from "./tracking-api";
import { fields, Panel, roles, type Run } from "./tracking-form";
import TrackingPatient from "./TrackingPatient";
import "./tracking.css";

export default function TrackingApp() {
  const [session, setSession] = useState<{ account: Account }>();
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [selected, setSelected] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const request = useMemo(() => trackingClient(), []);

  const run: Run = async (action) => {
    setBusy(true);
    setError("");
    try {
      await action();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Ошибка запроса");
    } finally {
      setBusy(false);
    }
  };

  const loadProfiles = useCallback(async (client: Request) => {
    const next = await client<Profile[]>("/tracking/patients");
    setProfiles(next);
    setSelected((id) =>
      next.some((profile) => profile.id === id) ? id : (next[0]?.id ?? ""),
    );
  }, []);

  useEffect(() => {
    if (session) return;
    void request<Account>("/me")
      .then(async (account) => {
        await loadProfiles(request);
        setSession({ account });
      })
      .catch((failure) =>
        setError(failure instanceof Error ? failure.message : "Ошибка запроса"),
      );
  }, [session, request, loadProfiles]);

  useEffect(() => {
    if (!session) return;
    const refresh = () =>
      void loadProfiles(request).catch((failure) => {
        if (failure instanceof ApiError && failure.status === 401)
          setSession(undefined);
      });
    const timer = window.setInterval(refresh, 8_000);
    window.addEventListener("online", refresh);
    window.addEventListener("focus", refresh);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("online", refresh);
      window.removeEventListener("focus", refresh);
    };
  }, [session, request, loadProfiles]);

  const profile = profiles.find((item) => item.id === selected);

  function enter() {
    void run(async () => {
      const account = await request<Account>("/me");
      await loadProfiles(request);
      setSession({ account });
    });
  }

  function accept(event: FormEvent<HTMLFormElement>) {
    const get = fields(event);
    void run(async () => {
      const joined = await request<Profile>(
        "/tracking/invitations/accept",
        "POST",
        { token: get("token") },
      );
      await loadProfiles(request);
      setSelected(joined.id);
    });
  }

  return (
    <main className="tracking-app">
      <header className="tracking-header">
        <div>
          <h1>Аптечка</h1>
          <p>Приём лекарств и процедуры</p>
        </div>
        {session && (
          <button
            type="button"
            disabled={busy}
            onClick={() => {
              setSession(undefined);
              setProfiles([]);
              setSelected("");
              setError("");
            }}
          >
            Выйти
          </button>
        )}
      </header>
      {error && (
        <p className="tracking-error" role="alert">
          {error}
        </p>
      )}
      {!session ? (
        <Panel title="Общий доступ">
          <p>Авторизация временно отключена.</p>
          <button type="button" disabled={busy} onClick={enter}>
            Войти в приложение
          </button>
        </Panel>
      ) : (
        <fieldset disabled={busy} className="tracking-workspace">
          <div className="tracking-profile">
            <strong>{session.account.displayName}</strong>
            {profiles.length > 0 && (
              <label>
                Подопечный
                <select
                  value={selected}
                  onChange={(event) => setSelected(event.target.value)}
                >
                  {profiles.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name} · {roles[item.role]}
                    </option>
                  ))}
                </select>
              </label>
            )}
          </div>
          <details open={profiles.length === 0}>
            <summary>Принять приглашение</summary>
            <form className="tracking-form" onSubmit={accept}>
              <label>
                Код приглашения
                <input
                  name="token"
                  required
                  autoComplete="off"
                  maxLength={100}
                />
              </label>
              <button>Принять</button>
            </form>
          </details>
          {!profiles.some((item) => item.role === "MANAGER") && (
            <details>
              <summary>Создать профиль подопечного</summary>
              <form
                className="tracking-form"
                onSubmit={(event) => {
                  const get = fields(event);
                  void run(async () => {
                    const created = await request<Profile>(
                      "/tracking/patients",
                      "POST",
                      { name: get("name"), zone: get("zone") },
                    );
                    await loadProfiles(request);
                    setSelected(created.id);
                  });
                }}
              >
                <label>
                  Имя подопечного
                  <input name="name" maxLength={100} required />
                </label>
                <label>
                  Часовой пояс IANA
                  <input
                    name="zone"
                    defaultValue={
                      Intl.DateTimeFormat().resolvedOptions().timeZone
                    }
                    required
                  />
                </label>
                <button>Создать профиль</button>
              </form>
            </details>
          )}
          {profile && (
            <TrackingPatient
              key={profile.id + profile.role}
              account={session.account}
              profile={profile}
              request={request}
              run={run}
              refreshProfiles={() => loadProfiles(request)}
            />
          )}
        </fieldset>
      )}
    </main>
  );
}
