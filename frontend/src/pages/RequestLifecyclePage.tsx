import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { pair, questions, sectionNames, theory, type Pair } from '../content/httpFoundations';
import { useLocale } from '../i18n/locale-store';
import type { Locale } from '../i18n/types';
import './http-lesson.css';

const nodes = ['Client', 'Controller', 'Service', 'Repository', 'Database'];
const scenarios = [
  { id: 'valid', text: pair('Valid title', 'Title hợp lệ') },
  { id: 'invalid', text: pair('Missing title', 'Thiếu title') },
  { id: 'db-error', text: pair('Valid title, database failure', 'Title hợp lệ nhưng database gặp lỗi') },
];

/** Cặp nội dung đặt sát nhau; lang giúp screen reader chọn đúng phát âm. */
function Copy({ value, language }: { value: Pair; language: Locale }) {
  return <span lang={value.en === value.vi ? 'en' : language}>{value[language]}</span>;
}

/** Language chỉ đổi trình bày, giữ nguyên answers/stage/result trong phiên component. */
export function RequestLifecyclePage() {
  const { locale: language } = useLocale();
  const [section, setSection] = useState(0);
  const [stage, setStage] = useState(-1);
  const [scenario, setScenario] = useState('valid');
  const [answers, setAnswers] = useState<Record<number, number>>({});
  const [submitted, setSubmitted] = useState(false);
  const copy = (en: string, vi: string) => <Copy value={pair(en, vi)} language={language} />;
  const invalid = scenario === 'invalid';
  // Validation chặn trước repository; database-error giả định ghi thất bại và chưa commit.
  const terminal = stage === (invalid ? 2 : 4);
  const status = invalid ? '400 Bad Request' : scenario === 'db-error' ? '500 Internal Server Error' : '201 Created';
  const score = questions.filter((q, i) => answers[i] === q.correct).length;
  useEffect(() => { document.title = `${pair('Request lifecycle', 'Vòng đời request')[language]} · Backend Lab`; }, [language]);
  const stageCopy = [pair('Client prepares the request.', 'Client chuẩn bị request.'), pair('Controller reads the body and passes input to the service.', 'Controller đọc body rồi chuyển dữ liệu đầu vào cho service.'), pair('Service validates title and business rules.', 'Service kiểm tra title bằng validation rồi áp dụng quy tắc nghiệp vụ.'), pair('Repository performs the write.', 'Repository thực hiện thao tác ghi dữ liệu.')];
  return <article className="http-lesson">
    <header className="panel stack"><span className="tag">{copy('SAMPLE LESSON · 30–40 MINUTES', 'BÀI MẪU · 30–40 PHÚT')}</span><h1>{copy('How does a request travel through a backend?', 'Một request đi qua backend như thế nào?')}</h1><p>{copy('Understand HTTP, follow the data, and distinguish input errors from system failures.', 'Tìm hiểu HTTP, lần theo đường đi của dữ liệu và phân biệt lỗi đầu vào với lỗi hệ thống.')}</p><p className="small muted">{copy('Prerequisites: variables, functions, and basic conditions. Results stay in this session only; leaving or reloading starts over.', 'Bạn cần biết biến, hàm và câu điều kiện cơ bản. Kết quả chỉ được giữ trong phiên này; nếu reload hoặc rời bài, bạn sẽ bắt đầu lại.')}</p><Link to="/journey/java-backend">{copy('← Knowledge map', '← Về bản đồ kiến thức')}</Link></header>
    <nav className="lesson-tabs" aria-label={language === 'en' ? 'Lesson sections' : 'Các phần bài học'}>{sectionNames.map((s, i) => <button key={s.en} className="button" aria-current={section === i ? 'step' : undefined} onClick={() => setSection(i)}>{i + 1}. <Copy value={s} language={language} /></button>)}</nav>
    <p className="small muted">{copy(`Viewing section ${section + 1}/5 · Navigation does not award a test result.`, `Đang xem phần ${section + 1}/5 · Chuyển phần chỉ để đọc, không tạo kết quả kiểm tra.`)}</p>
    <section className="panel stack lesson-body" aria-labelledby="lesson-section"><h2 id="lesson-section"><Copy value={sectionNames[section]} language={language} /></h2>
      {section < 3 && <>{(section === 0 || section === 2) && <Flow stage={-1} language={language} />}{theory[section].map(block => <section className="stack" key={block.id}><h3><Copy value={block.title} language={language} /></h3>{'code' in block && <pre lang="en">{block.code}</pre>}<p><Copy value={block.body} language={language} /></p></section>)}{section === 0 && <details><summary>{copy('Key terms — glossary', 'Các thuật ngữ chính')}</summary><ul>{[
        pair('Request — a message asking a server to perform an operation.', 'Request — thông điệp yêu cầu server thực hiện một thao tác.'), pair('Response — the server’s reply, including status and optional content.', 'Response — phản hồi của server, gồm status code và có thể có body.'), pair('Validation — checking input against syntax, type, and business rules.', 'Validation — kiểm tra dữ liệu đầu vào theo cú pháp, kiểu dữ liệu và quy tắc nghiệp vụ.'), pair('API contract — agreed input, outputs, behavior, and errors.', 'API contract — thỏa thuận về dữ liệu đầu vào, kết quả, hành vi và lỗi của API.'),
      ].map(term => <li key={term.en}><Copy value={term} language={language} /></li>)}</ul></details>}</>}
      {section === 3 && <>
        <p>{copy('Choose an input, then advance one step at a time. This browser model does not send HTTP or run Java/PostgreSQL.', 'Chọn một tình huống đầu vào rồi đi qua từng bước. Mô hình trong trình duyệt này không gửi HTTP và cũng không chạy Java hay PostgreSQL thật.')}</p>
        <label>{copy('Scenario', 'Tình huống')} <select value={scenario} onChange={e => { setScenario(e.target.value); setStage(-1); }}>{scenarios.map(s => <option key={s.id} value={s.id}>{s.text[language]}</option>)}</select></label>
        <Flow stage={stage} language={language} /><pre lang="en">{invalid ? 'POST /tasks\n{}' : 'POST /tasks\n{"title":"Learn Java"}'}</pre>
        <div className="actions"><button className="button primary" disabled={terminal} onClick={() => setStage(s => s + 1)}>{copy('Next step', 'Đi tiếp')}</button><button className="button" onClick={() => setStage(-1)}>{copy('Reset simulation', 'Chạy lại mô phỏng')}</button></div>
        <div className="lesson-feedback" role="status">{stage < 0 ? copy('No request sent yet. Select Next step to begin.', 'Chưa có request nào được gửi. Chọn “Đi tiếp” để bắt đầu.') : terminal ? <>{status} · {scenario === 'valid' ? copy('The model writes one task.', 'Mô hình ghi một task.') : copy('The model writes no task.', 'Mô hình không ghi task nào.')}</> : <>{nodes[stage]}: <Copy value={stageCopy[stage]} language={language} /></>}</div>
        <p>{copy('Try all three scenarios. Missing title stops at the service before database access. A database failure passes validation but does not create a task successfully.', 'Hãy thử cả ba tình huống. Khi thiếu title, request dừng ở service trước khi truy cập database. Khi database gặp lỗi, request đã qua validation nhưng task vẫn không được tạo thành công.')}</p>
      </>}
      {section === 4 && <>
        <p>{copy('Five questions to check your understanding. This sample self-check is not server-verified mastery evidence.', 'Năm câu hỏi giúp bạn tự kiểm tra mức độ hiểu bài. Kết quả bản mẫu này chưa được server xác minh và chưa phải bằng chứng năng lực chính thức.')}</p>
        {questions.map((q, i) => <fieldset key={q.id}><legend>{i + 1}. <Copy value={q.text} language={language} /></legend>{q.options.map((o, j) => <label className="lesson-option" key={j}><input type="radio" name={`q${i}`} checked={answers[i] === j} disabled={submitted} onChange={() => setAnswers(a => ({ ...a, [i]: j }))} /><span><Copy value={o} language={language} /></span></label>)}{submitted && <div className="lesson-feedback">{answers[i] === q.correct ? copy('Correct.', 'Đúng.') : <>{copy('Not correct. Answer:', 'Chưa đúng. Đáp án:')} <Copy value={q.options[q.correct]} language={language} /></>}<p><Copy value={q.why} language={language} /></p></div>}</fieldset>)}
        <button className="button primary" disabled={Object.keys(answers).length !== questions.length || submitted} onClick={() => setSubmitted(true)}>{copy('Submit answers', 'Nộp câu trả lời')}</button>
        {submitted && <div role="status" className="lesson-feedback"><h3>{copy(`Self-check result: ${score}/5`, `Kết quả tự kiểm tra: ${score}/5`)}</h3><p>{score === 5 ? copy('You answered this sample correctly. Next: practise Java basics, then build a real endpoint.', 'Bạn trả lời đúng các câu của bản mẫu. Tiếp theo: thực hành Java cơ bản rồi xây endpoint thật.') : copy('Read each explanation and revisit the relevant section before retrying.', 'Đọc giải thích ở từng câu và quay lại phần liên quan trước khi thử lại.')}</p><button className="button" onClick={() => { setAnswers({}); setSubmitted(false); }}>{copy('Try again', 'Làm lại kiểm tra')}</button></div>}
      </>}
    </section>
    <div className="actions"><button className="button" disabled={section === 0} onClick={() => setSection(s => s - 1)}>{copy('← Previous section', '← Phần trước')}</button><button className="button primary" disabled={section === 4} onClick={() => setSection(s => s + 1)}>{copy('Next section →', 'Phần tiếp →')}</button></div>
    <footer className="small muted">{copy('Further reading:', 'Đọc thêm:')} <a href="https://www.rfc-editor.org/rfc/rfc9110.html">HTTP Semantics · RFC 9110</a> · <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html">Java String API</a>. {copy('Scope: request/response and error classification, not all of HTTP or Java backend development.', 'Bài chỉ tập trung vào request/response và cách phân loại lỗi, chưa bao quát toàn bộ HTTP hay quá trình phát triển Java Backend.')}</footer>
  </article>;
}

/** Một sơ đồ chung; dịch caption và trạng thái, không nhân bản mô hình theo language. */
function Flow({ stage, language }: { stage: number; language: Locale }) {
  const label = language === 'en' ? 'Request flow, horizontally scrollable on small screens' : 'Sơ đồ request, có thể cuộn ngang trên màn nhỏ';
  return <figure className="lesson-flow" tabIndex={0} aria-label={label}><svg viewBox="0 0 840 130" role="img" aria-label="Client → Controller → Service → Repository → Database"><title>{language === 'vi' ? 'Đường đi của request' : 'Request flow'}</title>{nodes.map((n, i) => <g key={n}>{i < 4 && <path d={`M ${i * 168 + 147} 55 h 20 m -7 -5 l 7 5 -7 5`} fill="none" stroke="currentColor" strokeWidth="2" />}<rect x={i * 168 + 4} y="18" width="140" height="74" rx="14" fill={stage === i ? 'var(--accent-soft)' : 'var(--surface)'} stroke="currentColor" /><text x={i * 168 + 74} y="48" textAnchor="middle" fill="currentColor" fontSize="16">{n}</text><text x={i * 168 + 74} y="74" textAnchor="middle" fill="currentColor" fontSize="11">{stage === i ? (language === 'vi' ? 'Đang xử lý' : 'Processing') : `${language === 'vi' ? 'Bước' : 'Step'} ${i + 1}`}</text></g>)}</svg><figcaption><Copy value={pair('Client → Controller → Service → Repository → Database. The response travels back to the client. On small screens, scroll horizontally or use arrow keys when focused.', 'Request đi theo chiều Client → Controller → Service → Repository → Database; response quay ngược lại client. Trên màn hình nhỏ, bạn có thể cuộn ngang hoặc chọn sơ đồ rồi dùng phím mũi tên.')} language={language} /></figcaption></figure>;
}
