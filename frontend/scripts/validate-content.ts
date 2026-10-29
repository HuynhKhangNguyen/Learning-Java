import { lessonCatalog } from '../src/content/catalog.ts';
import { assertValidContent } from '../src/content/validate.ts';
import { httpLesson } from '../src/content/httpLesson.ts';
import { validateBlockLesson } from '../src/content/validate-block-lesson.ts';
import { lessonCopyCatalog, validateLessonCopyCoverage } from '../src/content/lesson-copy.ts';
import { javaStages, devopsStages } from '../src/data/journey.ts';
import { validateLocalePairs } from '../src/i18n/validate.ts';

assertValidContent(lessonCatalog);
// Kiểm tra bài đọc mới cùng catalog cũ để thiếu bản dịch hoặc sai ID không lọt qua build.
const blockErrors = validateBlockLesson(httpLesson);
if (blockErrors.length) throw new Error(blockErrors.join('\n'));
console.log(`Block lesson hợp lệ: ${httpLesson.id}, ${httpLesson.sections.length} phần.`);
const localeErrors = [
  ...validateLocalePairs(lessonCopyCatalog, 'lessonCopyCatalog'),
  ...validateLocalePairs({ javaStages, devopsStages }, 'journeys'),
  ...validateLessonCopyCoverage(lessonCatalog),
];
if (localeErrors.length) throw new Error(localeErrors.join('\n'));
console.log('Locale catalog hợp lệ: lesson metadata và hai roadmap có đủ EN/VI.');
const steps = lessonCatalog.reduce((total, lesson) => total + lesson.steps.length, 0);
const exercises = lessonCatalog.reduce((total, lesson) => total + lesson.exercises.length, 0);
console.log(`Content hợp lệ: ${lessonCatalog.length} bài, ${steps} bước, ${exercises} bài tập.`);
