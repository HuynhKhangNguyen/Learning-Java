import { ArrowLeft } from 'lucide-react';
import { Link } from 'react-router';
import { PageHeading } from '../components/PageHeading';
import { useLocale } from '../i18n/locale-store';
import { pair } from '../i18n/types';
import { paths } from '../routes';

export function NotFoundPage() {
  const { text } = useLocale();
  return (
    <>
      <PageHeading eyebrow={text(pair('Page not found', 'Không tìm thấy trang'))} title={text(pair('This path has no content yet.', 'Đường dẫn này hiện chưa có nội dung.'))} description={text(pair('The lesson or page you are looking for does not exist. Return to the journey to keep exploring.', 'Bài học hoặc trang bạn đang tìm không tồn tại. Hãy quay lại lộ trình để tiếp tục khám phá.'))} />
      <div className="actions"><Link className="button primary" to={paths.journey}><ArrowLeft aria-hidden="true" />{text(pair('Back to journey', 'Về hành trình'))}</Link><Link className="button" to={paths.home}>{text(pair('Home', 'Trang chủ'))}</Link></div>
    </>
  );
}
