import { pair, type Locale, type Pair } from '../i18n/types.ts';
import type { Lesson, LessonStep } from './types.ts';

type LessonLocaleCopy = {
  title: Pair;
  summary: Pair;
  steps: Record<string, { title: Pair; instruction: Pair }>;
};

// Metadata gốc vẫn giữ nguyên ID/version/rubric. Map này chỉ đổi phần chữ hiển thị,
// nên chuyển locale không tạo một bài học hay tiến độ thứ hai.
export const lessonCopyCatalog: Record<string, LessonLocaleCopy> = {
  'api-debug-500': {
    title: pair('Rescue an API returning 500', 'Xử lý API trả lỗi 500'),
    summary: pair(
      'Send a request, follow the trace, and verify how to handle an invalid title without hiding internal errors.',
      'Gửi một request, lần theo trace và kiểm tra cách xử lý title không hợp lệ mà không che khuất lỗi nội bộ.',
    ),
    steps: {
      intro: {
        title: pair('Where does the request go?', 'Request đi qua những đâu?'),
        instruction: pair(
          'Inspect an annotated request and response before running the simulation.',
          'Quan sát request và response đã được chú giải trước khi chạy mô phỏng.',
        ),
      },
      reproduce: {
        title: pair('Reproduce the 500 error', 'Tái hiện lỗi 500'),
        instruction: pair(
          'Send the missing-title preset through the original code.',
          'Chọn tình huống thiếu title rồi chạy phiên bản code ban đầu.',
        ),
      },
      trace: {
        title: pair('Follow the trace', 'Đọc trace'),
        instruction: pair(
          'Walk through each step and find the last layer that ran.',
          'Đi qua từng bước và xác định layer cuối cùng đã được thực thi.',
        ),
      },
      diagnose: {
        title: pair('Diagnose the cause', 'Chẩn đoán nguyên nhân'),
        instruction: pair(
          'Choose the explanation supported by the trace and observed data.',
          'Chọn lời giải thích khớp với trace và dữ liệu quan sát được.',
        ),
      },
      repair: {
        title: pair('Choose a fix', 'Chọn cách sửa'),
        instruction: pair(
          'Compare the patches and predict the effect of each approach.',
          'So sánh các bản sửa và dự đoán kết quả của từng cách.',
        ),
      },
      verify: {
        title: pair('Verify the contract', 'Kiểm tra lại API contract'),
        instruction: pair(
          'Run the baseline cases with the selected patch.',
          'Chạy các trường hợp cơ bản với bản sửa đã chọn.',
        ),
      },
      challenge: {
        title: pair('New scenario: blank title', 'Tình huống mới: title chỉ có khoảng trắng'),
        instruction: pair(
          'Handle empty strings, whitespace-only strings, and internal storage errors.',
          'Xử lý chuỗi rỗng, chuỗi chỉ có khoảng trắng và lỗi ghi dữ liệu nội bộ.',
        ),
      },
      result: {
        title: pair('Evidence earned', 'Bằng chứng đã thu được'),
        instruction: pair(
          'Review the earned criteria, hint level, and recommended next step.',
          'Xem tiêu chí đã đạt, mức gợi ý và bước nên học tiếp.',
        ),
      },
    },
  },
};

/** Build phải fail nếu lesson/step mới chưa có copy ở cả hai locale. */
export function validateLessonCopyCoverage(lessons: Lesson[], catalog = lessonCopyCatalog): string[] {
  const errors: string[] = [];
  for (const lesson of lessons) {
    const copy = catalog[lesson.id];
    if (!copy) { errors.push(`Missing lesson copy: ${lesson.id}`); continue; }
    for (const step of lesson.steps) if (!copy.steps[step.id]) errors.push(`Missing step copy: ${lesson.id}.${step.id}`);
    for (const stepId of Object.keys(copy.steps)) if (!lesson.steps.some(step => step.id === stepId)) errors.push(`Unknown step copy: ${lesson.id}.${stepId}`);
  }
  for (const lessonId of Object.keys(catalog)) if (!lessons.some(lesson => lesson.id === lessonId)) errors.push(`Unknown lesson copy: ${lessonId}`);
  return errors;
}

/** Lấy copy theo locale mà không sửa object catalog dùng chung. */
export function localizeLesson(lesson: Lesson, locale: Locale) {
  const copy = lessonCopyCatalog[lesson.id];
  return {
    title: copy?.title[locale] ?? lesson.title,
    summary: copy?.summary[locale] ?? lesson.summary,
  };
}

/** Step ID là khóa ổn định để cùng một activity dùng chung trạng thái ở cả hai locale. */
export function localizeLessonStep(lesson: Lesson, step: LessonStep, locale: Locale) {
  const copy = lessonCopyCatalog[lesson.id]?.steps[step.id];
  return {
    title: copy?.title[locale] ?? step.title,
    instruction: copy?.instruction[locale] ?? step.instruction,
  };
}
