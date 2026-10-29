import { LessonReader } from '../components/LessonReader';
import { httpLesson } from '../content/httpLesson';

/** Trang chỉ chọn dữ liệu bài; renderer chung sở hữu navigation và tự kiểm tra. */
export function HttpLessonPage() {
  return <LessonReader lesson={httpLesson} />;
}
