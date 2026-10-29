import { ArrowRight, ArrowUpRight, Monitor, Server } from 'lucide-react';
import { Link } from 'react-router';
import { PageHeading } from '../components/PageHeading';
import { getLesson } from '../content/catalog';
import { localizeLesson } from '../content/lesson-copy';
import { useLocale } from '../i18n/locale-store';
import { pair } from '../i18n/types';
import { paths } from '../routes';

export function HomePage() {
  const { locale, text } = useLocale();
  const lesson = getLesson('api-debug-500');
  const lessonCopy = lesson ? localizeLesson(lesson, locale) : undefined;
  return (
    <>
      <PageHeading eyebrow={text(pair('A little curiosity, one step forward', 'Một chút tò mò, thêm một bước tiến'))} title={text(pair('Understand backend systems from the inside.', 'Hiểu backend từ bên trong.'))} description={text(pair('Send a request. Watch it travel through the system. Work out what is happening.', 'Gửi một request, quan sát nó đi qua hệ thống và tự giải thích điều đang xảy ra.'))} />
      <section className="panel stack"><span className="tag">{text(pair('TWO SAMPLE LESSONS · EN / VI', 'HAI BÀI HỌC MẪU · EN / VI'))}</span><h2>HTTP Fundamentals → Request lifecycle</h2><p>{text(pair('Understand requests, responses, and each HTTP method through before-and-after examples and self-checks. Then trace a request inside a backend system.', 'Tìm hiểu request, response và các HTTP method qua ví dụ trước–sau cùng câu hỏi tự kiểm tra. Sau đó, bạn sẽ theo dấu một request bên trong backend.'))}</p><Link className="button primary" to="/learn/http-foundations">{text(pair('Start the sample lesson', 'Bắt đầu bài mẫu'))} <ArrowRight aria-hidden="true" /></Link><Link to="/learn/request-lifecycle">{text(pair('Next lesson: Request lifecycle', 'Bài tiếp theo: Request lifecycle'))}</Link></section>
      <section className="mission-hero" aria-labelledby="mission-title">
        <div className="mission-copy">
          <div className="actions"><span className="tag">{text(pair('FIRST LAB · IN PREPARATION', 'LAB ĐẦU TIÊN · ĐANG CHUẨN BỊ'))}</span><span className="small muted">{text(pair(`About ${lesson?.estimatedMinutes ?? 20} minutes`, `Khoảng ${lesson?.estimatedMinutes ?? 20} phút`))}</span></div>
          <h2 id="mission-title">{lessonCopy?.title ?? text(pair('Rescue an API returning 500', 'Xử lý API trả lỗi 500'))}</h2>
          <p className="muted">{lessonCopy?.summary ?? text(pair('This task has not been created yet. Follow the request, find evidence in the log, and verify your fix.', 'Bài thực hành này chưa hoàn thiện. Hãy lần theo request, tìm bằng chứng trong log rồi kiểm tra lại cách sửa.'))}</p>
          <p className="small">{text(pair('New to APIs? The lab will include a short introduction before the exercise.', 'Mới học API? Phòng lab sẽ có phần giới thiệu ngắn trước khi bạn thực hành.'))}</p>
          <Link className="button primary" to={paths.journey}>{text(pair('Explore the journey', 'Khám phá hành trình'))} <ArrowRight aria-hidden="true" /></Link>
          <Link className="text-link" to={paths.lab}>{text(pair('View the lab in preparation', 'Xem phòng lab đang chuẩn bị'))} <ArrowUpRight aria-hidden="true" /></Link>
        </div>
        <div className="mission-scene" aria-label={text(pair('Illustration of the lab scenario, not an execution result', 'Minh họa tình huống của lab, chưa phải kết quả chạy'))}>
          <p className="eyebrow">{text(pair('Inside the mission · illustration', 'Bên trong nhiệm vụ · minh họa'))}</p>
          <div className="mini-flow">
            <div className="mini-node"><Monitor aria-hidden="true" /><strong>Client</strong><span>POST /tasks</span></div>
            <ArrowRight aria-hidden="true" />
            <div className="mini-node issue"><Server aria-hidden="true" /><strong>API</strong><span>500 · Internal server error</span></div>
          </div>
          <div className="code-preview"><span>// {text(pair('A clue to investigate', 'Một manh mối cần điều tra'))}</span><code>title = null<br />NullPointerException</code></div>
          <p className="small muted">{text(pair('What happens before the data reaches the database?', 'Điều gì xảy ra trước khi dữ liệu đến database?'))}</p>
        </div>
      </section>
      <div className="home-panels">
        <section className="panel"><p className="eyebrow">{text(pair('Learn to do it yourself', 'Học để tự mình làm được'))}</p><ol className="outcome-list"><li><span>01</span>{text(pair('Understand how data travels through an application.', 'Hiểu cách dữ liệu đi qua ứng dụng.'))}</li><li><span>02</span>{text(pair('Find a cause from concrete evidence.', 'Tìm nguyên nhân từ bằng chứng cụ thể.'))}</li><li><span>03</span>{text(pair('Try the fix in a new scenario.', 'Áp dụng cách sửa vào tình huống mới.'))}</li></ol></section>
        <section className="panel stack"><p className="eyebrow">{text(pair('From foundations to deployment', 'Từ nền tảng đến triển khai'))}</p><h2>{text(pair('Java → API → data → Docker', 'Java → API → dữ liệu → Docker'))}</h2><p className="muted small">{text(pair('Each chapter unlocks another capability. Content will be added one stage at a time.', 'Mỗi chương mở thêm một khả năng. Nội dung sẽ được bổ sung theo từng chặng.'))}</p><Link className="text-link" to={paths.journey}>{text(pair('View the stages', 'Xem các chặng'))} <ArrowRight aria-hidden="true" /></Link></section>
      </div>
    </>
  );
}
