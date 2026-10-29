import { apiDebug500Lesson } from './lessons/apiDebug500.ts';
import type { Lesson } from './types.ts';
import { assertValidContent } from './validate.ts';

export const lessonCatalog: Lesson[] = [apiDebug500Lesson];
assertValidContent(lessonCatalog);

export function getLesson(lessonId: string): Lesson | undefined {
  return lessonCatalog.find((lesson) => lesson.id === lessonId);
}

export function getLessonStep(lesson: Lesson, stepId: string) {
  return lesson.steps.find((step) => step.id === stepId);
}
