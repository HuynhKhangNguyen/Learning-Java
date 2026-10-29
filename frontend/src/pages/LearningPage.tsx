import { ArrowLeft } from 'lucide-react';
import { Link, Navigate, useParams } from 'react-router';
import { PageHeading } from '../components/PageHeading';
import { PreparationPanel } from '../components/PreparationPanel';
import { getLesson, getLessonStep } from '../content/catalog';
import { localizeLesson, localizeLessonStep } from '../content/lesson-copy';
import { useLocale } from '../i18n/locale-store';
import { pair } from '../i18n/types';
import { paths } from '../routes';
import { NotFoundPage } from './NotFoundPage';

export function LearningPage() {
  const { locale, text } = useLocale();
  const { lessonId, stepId } = useParams();
  const lesson = lessonId ? getLesson(lessonId) : undefined;
  if (!lesson) return <NotFoundPage />;
  const step = stepId ? getLessonStep(lesson, stepId) : undefined;
  if (!step) return <Navigate to={`/learn/${lesson.id}/${lesson.entryStepId}`} replace />;
  const stepNumber = lesson.steps.findIndex((item) => item.id === step.id) + 1;
  const lessonCopy = localizeLesson(lesson, locale);
  const stepCopy = localizeLessonStep(lesson, step, locale);
  const title = step.activity === 'challenge' ? text(pair('Challenge in preparation', 'Thử thách đang được chuẩn bị')) : step.activity === 'result' ? text(pair('No learning results yet', 'Chưa có kết quả học tập')) : lessonCopy.title;
  const description = step.activity === 'result' ? text(pair('Results appear only after you complete and verify the exercise.', 'Kết quả chỉ xuất hiện sau khi bạn hoàn thành bài tập và hệ thống kiểm tra được bằng chứng.')) : lessonCopy.summary;
  const status = lesson.contentStatus === 'draft'
    ? text(pair('IN AUTHORING', 'ĐANG BIÊN SOẠN'))
    : lesson.contentStatus === 'planned'
      ? text(pair('PLANNED', 'ĐÃ LÊN KẾ HOẠCH'))
      : text(pair('PUBLISHED', 'ĐÃ XUẤT BẢN'));

  return (
    <>
      <PageHeading eyebrow={text(pair(`Lab · HTTP & API · Step ${stepNumber}/${lesson.steps.length}`, `Phòng lab · HTTP & API · Bước ${stepNumber}/${lesson.steps.length}`))} title={title} description={description} />
      <PreparationPanel title={step.activity === 'result' ? text(pair('You have not completed any exercises', 'Bạn chưa hoàn thành bài tập nào')) : stepCopy.title} actions={<Link className="button primary" to={paths.journey}><ArrowLeft aria-hidden="true" />{text(pair('Back to journey', 'Về hành trình'))}</Link>}>
        <div className="stack readable">
          <p>{step.activity === 'result' ? text(pair('The app does not yet have data to verify skills or calculate progress. Return to the journey to see the content in preparation.', 'Ứng dụng chưa có dữ liệu để xác minh kỹ năng hoặc tính tiến độ học. Bạn có thể quay lại lộ trình để xem những nội dung đang được chuẩn bị.')) : stepCopy.instruction}</p>
          <p className="small muted">{text(pair('The lesson outline, objectives, and criteria have been validated. The interactive simulation is not implemented yet, so this step does not record completion.', 'Đề cương, mục tiêu và tiêu chí của bài đã được kiểm tra. Phần mô phỏng tương tác chưa được triển khai nên bước này chưa thể ghi nhận là đã hoàn thành.'))}</p>
          <div className="actions"><span className="tag">{status}</span><span className="small muted">{text(pair(`About ${lesson.estimatedMinutes} minutes · ${lesson.objectives.length} objectives`, `Khoảng ${lesson.estimatedMinutes} phút · ${lesson.objectives.length} mục tiêu`))}</span></div>
        </div>
      </PreparationPanel>
    </>
  );
}
