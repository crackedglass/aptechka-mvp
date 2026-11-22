import type { FormEvent, PropsWithChildren } from "react";

export const roles = {
  MANAGER: "Менеджер",
  CAREGIVER: "Помощник",
  PATIENT: "Подопечный",
};
export type Run = (action: () => Promise<void>) => Promise<void>;

export function Panel({
  title,
  children,
}: PropsWithChildren<{ title: string }>) {
  return (
    <section className="tracking-panel">
      <h2>{title}</h2>
      {children}
    </section>
  );
}

export function fields(event: FormEvent<HTMLFormElement>) {
  event.preventDefault();
  const data = new FormData(event.currentTarget);
  return (name: string) => String(data.get(name) ?? "").trim();
}
