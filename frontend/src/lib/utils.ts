import type {MedForm} from '../types.ts';

export function iso(d = new Date()): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
export const dateObj = (s: string) => new Date(s + 'T12:00:00');
export function addDays(s: string, n: number): string {
  const d = dateObj(s);
  d.setDate(d.getDate() + n);
  return iso(d);
}
export const fmt = (s: string, opts: Intl.DateTimeFormatOptions = {day: 'numeric', month: 'long'}) => dateObj(s).toLocaleDateString('ru-RU', opts);
export const plural = (n: number, forms: [string, string, string]) => forms[n % 100 >= 11 && n % 100 <= 14 ? 2 : n % 10 === 1 ? 0 : n % 10 >= 2 && n % 10 <= 4 ? 1 : 2];
export const norm = (s: unknown) => String(s).trim().toLowerCase().replace(/\s+/g, ' ');
export const unitFor = (form: MedForm | '') => form === 'Капсулы' ? 'капс.' : 'табл.';

export function download(name: string, text: string, type = 'text/plain;charset=utf-8') {
  const url = URL.createObjectURL(new Blob([text], {type})), a = document.createElement('a');
  a.href = url;
  a.download = name;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
