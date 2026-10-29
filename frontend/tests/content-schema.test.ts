import assert from 'node:assert/strict';
import { httpLesson } from '../src/content/httpLesson.ts';
import { validateBlockLesson } from '../src/content/validate-block-lesson.ts';
import { lessonCopyCatalog, validateLessonCopyCoverage } from '../src/content/lesson-copy.ts';
import { javaStages, devopsStages } from '../src/data/journey.ts';
import { validateLocalePairs } from '../src/i18n/validate.ts';
import { test } from 'node:test';

test('block lesson has complete bilingual content and valid IDs/answers/sources', () => {
  assert.deepEqual(validateBlockLesson(httpLesson), []);
});

test('block validator rejects missing translation, ambiguous answer, broken table and source link', () => {
  const lesson = structuredClone(httpLesson);
  lesson.sections[0].blocks[0].title.vi = '';
  lesson.sections[1].id = lesson.sections[0].id;
  const blocks = lesson.sections.flatMap(s => s.blocks);
  const check = blocks.find(b => b.kind === 'check')!;
  if (check.kind === 'check') check.correctId = 'missing-option';
  const table = blocks.find(b => b.kind === 'table')!;
  if (table.kind === 'table') table.rows[0].pop();
  lesson.sources[0].sections.push('missing-section');
  const errors = validateBlockLesson(lesson).join('\n');
  for (const expected of ['Missing vi', 'Duplicate/empty ID', 'Invalid answer contract', 'Invalid table', 'Invalid source']) assert.ok(errors.includes(expected), errors);
});

test('global locale catalogs contain complete English and Vietnamese pairs', () => {
  assert.deepEqual(validateLocalePairs({ lessonCopyCatalog, javaStages, devopsStages }), []);
  assert.deepEqual(validateLessonCopyCoverage([apiDebug500Lesson]), []);
  const broken = structuredClone(javaStages);
  broken[0].topics[0][1].en = '';
  assert.match(validateLocalePairs(broken).join('\n'), /Missing en/);
  const missingStep = structuredClone(lessonCopyCatalog);
  delete missingStep['api-debug-500'].steps.intro;
  assert.match(validateLessonCopyCoverage([apiDebug500Lesson], missingStep).join('\n'), /Missing step copy/);
});
import { apiDebug500Lesson } from '../src/content/lessons/apiDebug500.ts';
import type { Lesson } from '../src/content/types.ts';
import { validateContent } from '../src/content/validate.ts';

const copyLesson = () => structuredClone(apiDebug500Lesson) as Lesson;
const codes = (lessons: Lesson[]) => validateContent(lessons).map((issue) => issue.code);

test('bài API 500 hợp lệ và có đủ ma trận 6 case × 4 patch', () => {
  assert.deepEqual(validateContent([copyLesson()]), []);
  assert.equal(apiDebug500Lesson.simulation?.cases.length, 6);
  for (const item of apiDebug500Lesson.simulation?.cases ?? []) assert.equal(Object.keys(item.expectedByPatch).length, 4);
});

test('phát hiện lesson, step và exercise id bị trùng', () => {
  const lesson = copyLesson();
  lesson.steps.push(structuredClone(lesson.steps[0]));
  lesson.exercises.push(structuredClone(lesson.exercises[0]));
  const result = codes([lesson, copyLesson()]);
  assert.ok(result.includes('DUPLICATE_LESSON_ID'));
  assert.ok(result.includes('DUPLICATE_STEP_ID'));
  assert.ok(result.includes('DUPLICATE_EXERCISE_ID'));
});

test('phát hiện tham chiếu step, objective, exercise, evidence và case không tồn tại', () => {
  const lesson = copyLesson();
  lesson.entryStepId = 'missing-entry';
  lesson.steps[0].nextStepId = 'missing-next';
  lesson.steps[0].objectiveIds = ['missing-objective'];
  lesson.steps[0].exerciseIds = ['missing-exercise'];
  lesson.completionEvidenceIds = ['missing-evidence'];
  const matrix = lesson.exercises.find((exercise) => exercise.type === 'case-matrix');
  if (matrix?.type === 'case-matrix') matrix.requiredCaseIds.push('missing-case');
  const result = codes([lesson]);
  for (const expected of ['MISSING_ENTRY_STEP', 'MISSING_NEXT_STEP', 'MISSING_OBJECTIVE', 'MISSING_EXERCISE', 'MISSING_EVIDENCE', 'MISSING_CASE']) assert.ok(result.includes(expected as never), `thiếu ${expected}`);
});

test('phát hiện prerequisite thiếu và prerequisite có vòng', () => {
  const missing = copyLesson();
  missing.prerequisiteLessonIds = ['unknown'];
  assert.ok(codes([missing]).includes('MISSING_PREREQUISITE'));
  const first = copyLesson();
  const second = copyLesson();
  first.id = 'first'; first.prerequisiteLessonIds = ['second'];
  second.id = 'second'; second.prerequisiteLessonIds = ['first'];
  assert.ok(codes([first, second]).includes('PREREQUISITE_CYCLE'));
});

test('phát hiện đáp án và kết quả patch bị thiếu hoặc không nhất quán', () => {
  const lesson = copyLesson();
  const choice = lesson.exercises.find((exercise) => exercise.type === 'single-choice');
  if (choice?.type === 'single-choice') choice.correctOptionId = 'missing-option';
  if (lesson.simulation) {
    delete lesson.simulation.cases[0].expectedByPatch.original;
    lesson.simulation.cases[1].expectedByPatch.original = { status: 500, writesRecord: true, errorCode: 'INTERNAL_ERROR' };
  }
  const result = codes([lesson]);
  assert.ok(result.includes('MISSING_CORRECT_OPTION'));
  assert.ok(result.includes('MISSING_PATCH_RESULT'));
  assert.ok(result.includes('INVALID_SIMULATION_RESULT'));
});
