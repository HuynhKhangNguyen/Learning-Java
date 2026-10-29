import type { BlockLesson } from './lesson-blocks.ts';

/** Kiểm bundle biên soạn typed trước build/test; chưa là validator payload API không tin cậy. */
export function validateBlockLesson(lesson: BlockLesson): string[] {
  const errors: string[] = [];
  const ids = new Set<string>();
  const addId = (id: string) => { if (!id.trim() || ids.has(id)) errors.push(`Duplicate/empty ID: ${id}`); ids.add(id); };
  // Duyệt mọi pair, gồm feedback/caption: tránh chỉ kiểm bản dịch heading.
  function translations(value: unknown, path: string): void {
    if (!value || typeof value !== 'object') return;
    const record = value as Record<string, unknown>;
    if ('en' in record || 'vi' in record) {
      for (const locale of ['en', 'vi']) if (typeof record[locale] !== 'string' || !(record[locale] as string).trim()) errors.push(`Missing ${locale}: ${path}`);
    } else for (const [key, item] of Object.entries(record)) translations(item, `${path}.${key}`);
  }
  translations(lesson, lesson.id);
  if (!lesson.sections.length || !lesson.objectives.length || !lesson.sources.length) errors.push('Missing sections, objectives or sources');
  for (const section of lesson.sections) {
    addId(section.id);
    if (!section.blocks.length) errors.push(`Empty section: ${section.id}`);
    if (!lesson.sources.some(s => s.sections.includes(section.id))) errors.push(`Unreferenced section: ${section.id}`);
    for (const b of section.blocks) {
      addId(b.id);
      if (b.kind === 'check') {
        const options = b.options.map(o => o.id);
        if (options.length < 2 || new Set(options).size !== options.length || !options.includes(b.correctId)) errors.push(`Invalid answer contract: ${b.id}`);
      }
      if (b.kind === 'table' && (!b.columns.length || !b.rows.length || b.rows.some(r => r.length !== b.columns.length))) errors.push(`Invalid table: ${b.id}`);
      if (b.kind === 'diagram' && b.nodes.length !== 2) errors.push(`Diagram requires two endpoints: ${b.id}`);
      if (b.kind === 'message' && !b.lines.length) errors.push(`Empty message: ${b.id}`);
    }
  }
  for (const source of lesson.sources) {
    if (!source.url.startsWith('https://') || !source.reviewedAt || source.sections.some(id => !lesson.sections.some(s => s.id === id))) errors.push(`Invalid source: ${source.title}`);
  }
  return errors;
}
