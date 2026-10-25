// Shapes shared by the prescription reader and parser (lib/prescription*.ts).
// Backend payloads are typed in api/types.ts.

export type MedForm = 'Таблетки' | 'Капсулы';

export interface ParsedMed {
  name: string;
  strength: string;
  form: MedForm | '';
  dose: number | '';
  days: number | '';
  times: string;
  frequency: number | null;
  note: string;
  sourceText: string;
  complex: boolean;
  warnings: string[];
  index: number;
}

export interface ReadPage {
  text: string;
  confidence: number | null;
  method: string;
  fileName: string;
  page: number;
  preview: string;
}
