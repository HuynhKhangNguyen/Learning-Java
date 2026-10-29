import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { pair, type BlockLesson, type LessonBlock, type Pair } from '../content/lesson-blocks';
import { useLocale } from '../i18n/locale-store';
import type { Locale } from '../i18n/types';
import '../pages/http-lesson.css';

function Copy({ text, language }: { text: Pair; language: Locale }) {
  return <span lang={text.en === text.vi ? 'en' : language}>{text[language]}</span>;
}
type CheckState = { selected?: string; submitted: boolean };

/** State theo block ID, ở ngoài section đang xem để đổi mục/ngôn ngữ không xóa bài làm. */
export function LessonReader({ lesson }: { lesson: BlockLesson }) {
  const { locale: language, text } = useLocale();
  const [index, setIndex] = useState(0);
  const [checks, setChecks] = useState<Record<string, CheckState>>({});
  const heading = useRef<HTMLHeadingElement>(null);
  const section = lesson.sections[index];
  const copy = (en: string, vi: string) => <Copy text={pair(en, vi)} language={language} />;
  const allChecks = lesson.sections.flatMap(s => s.blocks).filter(b => b.kind === 'check');
  const attempted = allChecks.filter(b => checks[b.id]?.submitted).length;
  const correct = allChecks.filter(b => checks[b.id]?.submitted && checks[b.id]?.selected === b.correctId).length;
  useEffect(() => { document.title = `${lesson.title[language]} · Backend Lab`; }, [language, lesson.title]);
  function go(next: number) {
    setIndex(next);
    // Heading giữ cùng DOM node: focus báo vùng nội dung mới cho keyboard/screen reader.
    heading.current?.focus();
    heading.current?.scrollIntoView({ block: 'start', behavior: 'instant' });
  }
  return <article className="http-lesson standard-lesson">
    <header className="lesson-hero stack"><span className="tag"><Copy text={lesson.category} language={language} /></span><h1><Copy text={lesson.title} language={language} /></h1><p><Copy text={lesson.summary} language={language} /></p><p className="small"><Copy text={lesson.prerequisites} language={language} /></p><p className="small">{copy('Sample lesson · self-checks stay in this tab session. Leaving or reloading clears them. Reading time has not been measured.', 'Đây là bài học mẫu. Kết quả tự kiểm tra chỉ được giữ trong tab hiện tại và sẽ mất khi bạn reload hoặc rời bài. Thời gian học chưa được đo.')}</p><Link to="/journey">{copy('← Knowledge map', '← Về bản đồ kiến thức')}</Link></header>
    <div className="reader-layout"><nav className="reader-outline" aria-label={text(pair('Lesson contents', 'Mục lục bài học'))}>{lesson.sections.map((s, i) => <button className="button" key={s.id} aria-current={index === i ? 'step' : undefined} onClick={() => go(i)}><span className="section-number">{String(i + 1).padStart(2, '0')}</span><span><Copy text={s.title} language={language} /></span></button>)}<p className="small muted">{copy(`${attempted}/${allChecks.length} self-checks submitted. Reading is separate from demonstrated ability.`, `Bạn đã nộp ${attempted}/${allChecks.length} câu tự kiểm tra. Việc đọc xong bài chưa chứng minh rằng bạn đã vận dụng được kiến thức.`)}</p></nav>
    <div className="reader-main"><section className="panel stack lesson-body" aria-labelledby="reader-heading"><p className="eyebrow">{copy(`SECTION ${index + 1} / ${lesson.sections.length}`, `PHẦN ${index + 1} / ${lesson.sections.length}`)}</p><h2 id="reader-heading" ref={heading} tabIndex={-1}><Copy text={section.title} language={language} /></h2>
      {index === 0 && <div className="learning-goals"><h3>{copy('After this lesson, you can…', 'Sau bài này, bạn có thể…')}</h3><ul>{lesson.objectives.map(o => <li key={o.en}><Copy text={o} language={language} /></li>)}</ul></div>}
      {section.blocks.map(block => <Block key={block.id} block={block} language={language} state={checks[block.id] ?? { submitted: false }} update={state => setChecks(current => ({ ...current, [block.id]: state }))} />)}
      {index === lesson.sections.length - 1 && <aside className="lesson-feedback"><h3>{copy('Your self-check summary', 'Kết quả tự kiểm tra')}</h3><p>{copy(`${correct} correct out of ${attempted} submitted; ${allChecks.length - attempted} unanswered.`, `Bạn trả lời đúng ${correct}/${attempted} câu đã nộp và còn ${allChecks.length - attempted} câu chưa trả lời.`)}</p><p>{copy('This is practice feedback, not official mastery. Review the explanation for any incorrect answer.', 'Đây chỉ là phản hồi luyện tập, chưa phải kết quả đánh giá năng lực chính thức. Hãy đọc lại phần giải thích của những câu trả lời chưa đúng.')}</p>{lesson.nextLesson && <Link className="button primary" to={lesson.nextLesson.path}><Copy text={lesson.nextLesson.label} language={language} /></Link>}</aside>}
    </section><div className="actions"><button className="button" disabled={index === 0} onClick={() => go(index - 1)}>{copy('← Previous section', '← Phần trước')}</button><button className="button primary" disabled={index === lesson.sections.length - 1} onClick={() => go(index + 1)}>{copy('Next section →', 'Phần tiếp →')}</button></div>
    <footer className="panel stack small"><h3>{copy('Sources for this section', 'Nguồn cho phần này')}</h3>{lesson.sources.filter(s => s.sections.includes(section.id)).map(s => <a key={s.url} href={s.url}>{s.title}</a>)}<p><Copy text={lesson.exampleNote} language={language} /></p></footer></div></div>
  </article>;
}

/** Renderer chọn theo kind đã khai báo; nội dung không được thực thi như HTML/JavaScript. */
function Block({ block: b, language, state, update }: { block: LessonBlock; language: Locale; state: CheckState; update: (state: CheckState) => void }) {
  const c = (text: Pair) => <Copy text={text} language={language} />;
  if (b.kind === 'check') return <fieldset className="inline-check" data-check-id={b.id}><legend>{c(b.title)}</legend><p>{c(b.question)}</p>{b.options.map(o => <label className="lesson-option" key={o.id}><input type="radio" name={b.id} checked={state.selected === o.id} disabled={state.submitted} onChange={() => update({ selected: o.id, submitted: false })} /><span>{c(o.text)}</span></label>)}<button className="button primary" disabled={!state.selected || state.submitted} onClick={() => update({ ...state, submitted: true })}>{c(pair('Check answer', 'Kiểm tra câu trả lời'))}</button>{state.submitted && <div role="status" className="lesson-feedback"><strong>{c(state.selected === b.correctId ? pair('Correct.', 'Chính xác.') : pair('Not yet. Review the explanation.', 'Chưa chính xác. Hãy đọc phần giải thích bên dưới.'))}</strong><p>{c(b.options.find(o => o.id === state.selected)!.feedback)}</p>{state.selected !== b.correctId && <p>{c(pair('Expected answer:', 'Đáp án phù hợp:'))} {c(b.options.find(o => o.id === b.correctId)!.text)}</p>}<button className="button" onClick={() => update({ submitted: false })}>{c(pair('Try again', 'Chọn lại'))}</button></div>}</fieldset>;
  if (b.kind === 'diagram') return <figure className="exchange-figure"><h3>{c(b.title)}</h3><div className="exchange-nodes">{b.nodes.map((n, i) => <div key={n.label.en} className="exchange-node"><svg viewBox="0 0 120 80" role="img" aria-label={language === 'vi' ? n.label.vi : n.label.en}>{i === 0 ? <><rect x="15" y="10" width="90" height="55" rx="7" /><path d="M15 25h90M5 72h110" /><circle cx="25" cy="18" r="2" /></> : <><rect x="20" y="8" width="80" height="64" rx="8" /><path d="M20 30h80M20 51h80" /><circle cx="86" cy="20" r="3" /><circle cx="86" cy="41" r="3" /><circle cx="86" cy="62" r="3" /></>}</svg><strong>{c(n.label)}</strong><p className="small">{c(n.detail)}</p></div>)}</div><div className="exchange-arrow">→ {c(b.forward)}</div><div className="exchange-arrow response-arrow">← {c(b.backward)}</div><figcaption>{c(b.caption)}</figcaption></figure>;
  if (b.kind === 'message') return <section className="message-block"><h3>{c(b.title)}</h3><div className="annotated-message">{b.lines.map((line, i) => <div className="message-row" key={i}><pre>{line.code || ' '}</pre><p>{c(line.explanation)}</p></div>)}</div><p className="small muted">{c(b.caption)}</p></section>;
  if (b.kind === 'table') return <section><h3>{c(b.title)}</h3><div className="lesson-table-scroll" tabIndex={0} role="region" aria-label={language === 'vi' ? b.title.vi : b.title.en}><table><caption className="small">{c(pair('Scroll horizontally on small screens if needed.', 'Nếu dùng màn hình nhỏ, bạn có thể cuộn ngang để xem hết bảng.'))}</caption><thead><tr>{b.columns.map((col, i) => <th key={i} scope="col">{c(col)}</th>)}</tr></thead><tbody>{b.rows.map((row, i) => <tr key={i}>{row.map((cell, j) => <td key={j}>{c(cell)}</td>)}</tr>)}</tbody></table></div></section>;
  if (b.kind === 'example') return <section className={`method-example method-${b.method.toLowerCase()}`}><span className="method-badge">{b.method}</span><h3>{c(b.title)}</h3><p>{c(b.body)}</p><div className="lesson-columns"><div><h4>{c(pair('Request', 'Request — yêu cầu'))}</h4><pre>{b.request}</pre></div><div><h4>{c(pair('Response', 'Response — phản hồi'))}</h4><pre>{b.response}</pre></div></div><div className="before-after"><div><h4>{c(pair('Before', 'Trước'))}</h4><pre>{b.before}</pre></div><span aria-hidden="true">→</span><div><h4>{c(pair('After', 'Sau'))}</h4><pre>{b.after}</pre></div></div><p className="lesson-feedback">{c(b.pitfall)}</p></section>;
  return <section className={b.kind === 'callout' ? 'lesson-feedback' : 'stack'}><h3>{c(b.title)}</h3><p>{c(b.body)}</p></section>;
}
