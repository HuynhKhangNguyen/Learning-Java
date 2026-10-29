import { useEffect, useRef } from 'react';
import { FlaskConical, House, Layers3 } from 'lucide-react';
import { Link, NavLink, Outlet, useLocation, useNavigationType } from 'react-router';
import { getLesson } from '../content/catalog';
import { localizeLesson } from '../content/lesson-copy';
import { useLocale } from '../i18n/locale-store';
import { pair, type Locale } from '../i18n/types';
import { paths } from '../routes';

function sectionName(pathname: string, locale: Locale) {
  const text = (en: string, vi: string) => pair(en, vi)[locale];
  if (pathname === '/') return text('Today', 'Hôm nay');
  if (pathname.startsWith('/journey')) return text('Journey', 'Hành trình');
  if (pathname.endsWith('/challenge')) return text('Challenge', 'Thử thách');
  if (pathname.endsWith('/result')) return text('Result', 'Kết quả');
  if (pathname.startsWith('/learn')) return text('Lab', 'Phòng lab');
  return text('Navigation', 'Điều hướng');
}

export function AppShell() {
  const { locale, setLocale, text } = useLocale();
  const { pathname } = useLocation();
  const navigationType = useNavigationType();
  const mainRef = useRef<HTMLElement>(null);
  const lesson = pathname.startsWith('/learn/') ? getLesson(pathname.split('/')[2]) : undefined;
  const lessonCopy = lesson ? localizeLesson(lesson, locale) : undefined;

  useEffect(() => {
    mainRef.current?.focus({ preventScroll: true });
    if (navigationType !== 'POP') window.scrollTo({ top: 0, behavior: 'instant' });
  }, [navigationType, pathname]);

  return (
    <>
      <a className="skip-link" href="#main-content">{text(pair('Skip to main content', 'Đến nội dung chính'))}</a>
      <div className="development-note">{['/learn/http-foundations', '/learn/request-lifecycle'].includes(pathname) ? text(pair('Experience lesson · Examples and self-check results stay in this session.', 'Bài học trải nghiệm · Ví dụ và kết quả tự kiểm tra chỉ được giữ trong phiên này.')) : text(pair('Work in progress · The HTTP sample is available; other labs are being prepared.', 'Sản phẩm đang được phát triển · Bài mẫu HTTP đã mở; các phòng lab khác đang được chuẩn bị.'))}</div>
      <div className="app-shell">
        <aside className="sidebar">
          <Link to={paths.home} className="brand" aria-label={text(pair('Backend Lab — home', 'Backend Lab — trang chủ'))}>
            <span className="brand-mark" aria-hidden="true">{'{ }'}</span>
            <span>backend <span className="brand-light">lab</span><small>{text(pair('UNDERSTAND DEEPLY. BUILD IT YOURSELF.', 'HIỂU SÂU. TỰ LÀM ĐƯỢC.'))}</small></span>
          </Link>
          <nav className="primary-nav" aria-label={text(pair('Primary navigation', 'Điều hướng chính'))}>
            <NavLink to={paths.home} end><House aria-hidden="true" />{text(pair('Today', 'Hôm nay'))}</NavLink>
            <NavLink to="/journey"><Layers3 aria-hidden="true" />{text(pair('Journey', 'Hành trình'))}</NavLink>
            <NavLink to="/learn"><FlaskConical aria-hidden="true" />{text(pair('Lab', 'Phòng lab'))}</NavLink>
          </nav>
          <div className="sidebar-note"><span>{text(pair('Your roadmap', 'Lộ trình của bạn'))}</span><strong>Java Backend</strong><p>{text(pair('One concept at a time.', 'Học từng khái niệm.'))}<br />{text(pair('One capability at a time.', 'Làm chủ từng kỹ năng.'))}</p></div>
        </aside>
        <div className="app-body">
          <header className="topbar">
            <div><Link to={paths.home}>{text(pair('Learning space', 'Không gian học'))}</Link><span aria-hidden="true"> / </span><strong>{sectionName(pathname, locale)}</strong>{lessonCopy && <><span aria-hidden="true"> / </span><span>{lessonCopy.title}</span></>}</div>
            <div className="topbar-actions"><span>{text(pair('JAVA BACKEND · LEARN BY PRACTICE', 'JAVA BACKEND · HỌC QUA THỰC HÀNH'))}</span><fieldset className="global-language-switch"><legend>{text(pair('Display language', 'Ngôn ngữ hiển thị'))}</legend>{(['vi', 'en'] as const).map(option => <label key={option}><input type="radio" name="global-language" checked={locale === option} onChange={() => setLocale(option)} /><span>{option === 'vi' ? 'Tiếng Việt' : 'English'}</span></label>)}</fieldset></div>
          </header>
          <main id="main-content" ref={mainRef} tabIndex={-1} className="page-content"><Outlet /></main>
          <footer className="app-footer"><span>{text(pair('Learn at your own pace.', 'Học theo nhịp của bạn.'))}</span><span>{text(pair('Learning progress is not recorded yet.', 'Chưa ghi nhận tiến độ học.'))}</span></footer>
        </div>
      </div>
    </>
  );
}
