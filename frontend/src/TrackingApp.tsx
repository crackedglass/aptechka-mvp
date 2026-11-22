import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type FormEvent,
} from "react";
import {
  ApiError,
  basic,
  trackingClient,
  type Account,
  type Profile,
  type Request,
} from "./tracking-api";
import { fields, Panel, roles, type Run } from "./tracking-form";
import TrackingPatient from "./TrackingPatient";
import "./tracking.css";

export default function TrackingApp() {
  const [session, setSession] = useState<{
    account: Account;
    authorization: string;
  }>();
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [selected, setSelected] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [register, setRegister] = useState(false);
  const request = useMemo(
    () => trackingClient(session?.authorization),
    [session],
  );

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

  function login(event: FormEvent<HTMLFormElement>) {
    const form = event.currentTarget;
    const get = fields(event);
    const email = get("email");
    const password = String(new FormData(form).get("password") ?? "");
    void run(async () => {
      if (register) {
        await request("/auth/register", "POST", {
          email,
          password,
          displayName: get("name"),
        });
      }
      const authorization = basic(email, password);
      const client = trackingClient(authorization);
      const account = await client<Account>("/me");
      await loadProfiles(client);
      setSession({ account, authorization });
      form.reset();
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
        <Panel title={register ? "Регистрация" : "Вход"}>
          <form onSubmit={login} className="tracking-form">
            {register && (
              <label>
                Имя
                <input
                  name="name"
                  autoComplete="name"
                  required
                  maxLength={100}
                />
              </label>
            )}
            <label>
              Почта
              <input
                name="email"
                type="email"
                autoComplete="username"
                required
                maxLength={254}
              />
            </label>
            <label>
              Пароль
              <input
                name="password"
                type="password"
                autoComplete={register ? "new-password" : "current-password"}
                minLength={register ? 8 : undefined}
                maxLength={72}
                required
              />
            </label>
            <button disabled={busy}>
              {register ? "Создать аккаунт" : "Войти"}
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={() => setRegister(!register)}
            >
              {register ? "Уже есть аккаунт" : "Создать аккаунт"}
            </button>
          </form>
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
