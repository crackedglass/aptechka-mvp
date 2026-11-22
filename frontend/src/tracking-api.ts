/// <reference types="vite/client" />

export type Role = "MANAGER" | "CAREGIVER" | "PATIENT";
export type Profile = {
  id: string;
  name: string;
  zone: string;
  role: Role;
  caregiver: string | null;
};
export type Account = { id: string; email: string; displayName: string };
export type Action =
  | "CLAIM"
  | "RELEASE"
  | "TAKEOVER"
  | "REPORT"
  | "CORRECT"
  | "RETRACT"
  | "CANCEL"
  | "REOPEN";
export type Instruction = {
  kind: "MEDICATION" | "PROCEDURE";
  title: string;
  dose: number | null;
  unit: string | null;
  instructions: string | null;
  start: string;
  end: string | null;
  weekdays: string[];
  times: string[];
};
export type Plan = { plan: string; version: number; instruction: Instruction };
export type Handler = {
  actor: string;
  name: string;
  since: string;
  revision: number;
};
export type Slot = { day: string; time: string; scheduled: string };
export type Revision = {
  seq: number;
  action: Action;
  author: string;
  authorName: string;
  actual: string | null;
  recorded: string;
  reason: string | null;
};
export type Occurrence = {
  id: string;
  slot: Slot;
  zone: string;
  instruction: Instruction;
  status: "PENDING" | "OVERDUE" | "COMPLETED" | "CANCELLED";
  completedAt: string | null;
  completionRevision: number;
  handler: Handler | null;
  reports: Revision[][];
};
export type Day = { date: string; today: string; occurrences: Occurrence[] };
export type Preview = {
  version: number;
  effective: string;
  claimed: Occurrence[];
  reported: Occurrence[];
};
export type Notice = {
  id: number;
  kind: "PREVIOUS_DAY" | "LAST_RETRACTED";
  slot: Slot;
  title: string;
  recorded: string;
  read: boolean;
};
export type Invitation = {
  id: string;
  role: Role;
  email: string;
  expires: string;
  state: string;
};

export class ApiError extends Error {
  constructor(
    public status: number,
    detail: string,
  ) {
    super(detail);
  }
}

export function trackingClient(authorization?: string) {
  const pending = new Map<string, string>();
  return async function request<T>(
    path: string,
    method = "GET",
    body?: unknown,
  ): Promise<T> {
    const base = import.meta.env.VITE_API_URL?.replace(/\/$/, "") ?? "";
    let fingerprint: string | undefined;
    if (
      body &&
      typeof body === "object" &&
      "requestId" in body &&
      typeof body.requestId === "string"
    ) {
      const { requestId, ...command } = body;
      fingerprint = JSON.stringify([path, method, command]);
      const key = pending.get(fingerprint) ?? requestId;
      pending.set(fingerprint, key);
      body = { ...command, requestId: key };
    }
    const response = await fetch(`${base}/api${path}`, {
      method,
      credentials: "omit",
      cache: "no-store",
      headers: {
        "X-Requested-With": "XMLHttpRequest",
        ...(authorization ? { Authorization: authorization } : {}),
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const raw = await response.text();
    if (fingerprint && response.status < 500) pending.delete(fingerprint);
    if (!response.ok) {
      const fallback =
        response.status === 401
          ? "Проверьте почту и пароль"
          : `Ошибка запроса (${response.status})`;
      let detail = fallback;
      try {
        detail = JSON.parse(raw).detail || fallback;
      } catch {
        // Some authorization failures have an empty response.
      }
      throw new ApiError(response.status, detail);
    }
    return (raw ? JSON.parse(raw) : undefined) as T;
  };
}

export type Request = ReturnType<typeof trackingClient>;

export function basic(email: string, password: string) {
  const bytes = new TextEncoder().encode(`${email}:${password}`);
  return `Basic ${btoa(String.fromCharCode(...bytes))}`;
}

export function localInput(value = new Date()) {
  const local = new Date(value.getTime() - value.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

export function showTime(instant: string, timeZone: string) {
  return new Intl.DateTimeFormat("ru", {
    timeZone,
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(instant));
}
