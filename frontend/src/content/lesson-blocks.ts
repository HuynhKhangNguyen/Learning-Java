export { pair, type Pair } from '../i18n/types.ts';
import type { Pair } from '../i18n/types.ts';

/** Discriminated union giữ dữ liệu biên soạn tách JSX, không nhận HTML/script tùy ý. */
export type LessonBlock = { id: string } & (
  | { kind: 'text'; title: Pair; body: Pair }
  | { kind: 'callout'; title: Pair; body: Pair }
  | { kind: 'diagram'; title: Pair; nodes: { label: Pair; detail: Pair }[]; forward: Pair; backward: Pair; caption: Pair }
  | { kind: 'message'; title: Pair; lines: { code: string; explanation: Pair }[]; caption: Pair }
  | { kind: 'table'; title: Pair; columns: Pair[]; rows: Pair[][] }
  | { kind: 'example'; title: Pair; method: string; body: Pair; request: string; response: string; before: string; after: string; pitfall: Pair }
  | { kind: 'check'; title: Pair; question: Pair; options: { id: string; text: Pair; feedback: Pair }[]; correctId: string }
);
export interface BlockLesson {
  id: string;
  version: number;
  category: Pair;
  exampleNote: Pair;
  nextLesson?: { path: string; label: Pair };
  title: Pair;
  summary: Pair;
  prerequisites: Pair;
  objectives: Pair[];
  sections: { id: string; title: Pair; blocks: LessonBlock[] }[];
  sources: { title: string; url: string; sections: string[]; reviewedAt: string }[];
}
