import type { ChoiceExercise, Lesson } from './types.ts';

export type ContentIssueCode =
  | 'DUPLICATE_LESSON_ID' | 'DUPLICATE_STEP_ID' | 'DUPLICATE_EXERCISE_ID'
  | 'DUPLICATE_OBJECTIVE_ID' | 'DUPLICATE_EVIDENCE_ID' | 'DUPLICATE_PATCH_ID' | 'DUPLICATE_CASE_ID'
  | 'MISSING_PREREQUISITE' | 'PREREQUISITE_CYCLE' | 'MISSING_ENTRY_STEP' | 'MISSING_NEXT_STEP'
  | 'MISSING_OBJECTIVE' | 'MISSING_EXERCISE' | 'MISSING_EVIDENCE' | 'MISSING_CASE'
  | 'MISSING_CORRECT_OPTION' | 'MISSING_PATCH_RESULT' | 'INVALID_SIMULATION_RESULT'
  | 'INVALID_PUBLISHED_LESSON';

export interface ContentIssue { code: ContentIssueCode; path: string; message: string }

function duplicates(values: string[]) {
  const seen = new Set<string>();
  return new Set(values.filter((value) => seen.has(value) || !seen.add(value)));
}

function reportDuplicates(issues: ContentIssue[], code: ContentIssueCode, path: string, values: string[]) {
  for (const id of duplicates(values)) issues.push({ code, path, message: `ID bị trùng: ${id}` });
}

export function validateContent(lessons: Lesson[]): ContentIssue[] {
  const issues: ContentIssue[] = [];
  reportDuplicates(issues, 'DUPLICATE_LESSON_ID', 'lessons', lessons.map((lesson) => lesson.id));
  const lessonIds = new Set(lessons.map((lesson) => lesson.id));

  for (const lesson of lessons) {
    const base = `lessons.${lesson.id}`;
    const stepIds = new Set(lesson.steps.map((step) => step.id));
    const exerciseIds = new Set(lesson.exercises.map((exercise) => exercise.id));
    const objectiveIds = new Set(lesson.objectives.map((objective) => objective.id));
    const evidenceIds = new Set(lesson.evidenceCriteria.map((criterion) => criterion.id));
    reportDuplicates(issues, 'DUPLICATE_STEP_ID', `${base}.steps`, lesson.steps.map((step) => step.id));
    reportDuplicates(issues, 'DUPLICATE_EXERCISE_ID', `${base}.exercises`, lesson.exercises.map((exercise) => exercise.id));
    reportDuplicates(issues, 'DUPLICATE_OBJECTIVE_ID', `${base}.objectives`, lesson.objectives.map((objective) => objective.id));
    reportDuplicates(issues, 'DUPLICATE_EVIDENCE_ID', `${base}.evidenceCriteria`, lesson.evidenceCriteria.map((criterion) => criterion.id));

    for (const prerequisiteId of lesson.prerequisiteLessonIds) if (!lessonIds.has(prerequisiteId)) issues.push({ code: 'MISSING_PREREQUISITE', path: `${base}.prerequisiteLessonIds`, message: `Không tìm thấy bài prerequisite: ${prerequisiteId}` });
    if (!stepIds.has(lesson.entryStepId)) issues.push({ code: 'MISSING_ENTRY_STEP', path: `${base}.entryStepId`, message: `Không tìm thấy bước mở đầu: ${lesson.entryStepId}` });
    for (const evidenceId of lesson.completionEvidenceIds) if (!evidenceIds.has(evidenceId)) issues.push({ code: 'MISSING_EVIDENCE', path: `${base}.completionEvidenceIds`, message: `Không tìm thấy evidence: ${evidenceId}` });

    for (const step of lesson.steps) {
      const stepPath = `${base}.steps.${step.id}`;
      if (step.nextStepId && !stepIds.has(step.nextStepId)) issues.push({ code: 'MISSING_NEXT_STEP', path: `${stepPath}.nextStepId`, message: `Không tìm thấy bước tiếp theo: ${step.nextStepId}` });
      for (const id of step.objectiveIds) if (!objectiveIds.has(id)) issues.push({ code: 'MISSING_OBJECTIVE', path: `${stepPath}.objectiveIds`, message: `Không tìm thấy mục tiêu: ${id}` });
      for (const id of step.exerciseIds) if (!exerciseIds.has(id)) issues.push({ code: 'MISSING_EXERCISE', path: `${stepPath}.exerciseIds`, message: `Không tìm thấy bài tập: ${id}` });
      for (const id of step.evidenceIds) if (!evidenceIds.has(id)) issues.push({ code: 'MISSING_EVIDENCE', path: `${stepPath}.evidenceIds`, message: `Không tìm thấy evidence: ${id}` });
    }

    const caseIds = new Set(lesson.simulation?.cases.map((item) => item.id) ?? []);
    for (const exercise of lesson.exercises) {
      if (exercise.type === 'single-choice' && !exercise.options.some((option) => option.id === exercise.correctOptionId)) issues.push({ code: 'MISSING_CORRECT_OPTION', path: `${base}.exercises.${exercise.id}.correctOptionId`, message: `Không tìm thấy đáp án đúng: ${exercise.correctOptionId}` });
      if (exercise.type === 'case-matrix') for (const caseId of exercise.requiredCaseIds) if (!caseIds.has(caseId)) issues.push({ code: 'MISSING_CASE', path: `${base}.exercises.${exercise.id}.requiredCaseIds`, message: `Không tìm thấy simulation case: ${caseId}` });
    }

    if (lesson.simulation) {
      const patchIds = lesson.simulation.patches.map((patch) => patch.id);
      reportDuplicates(issues, 'DUPLICATE_PATCH_ID', `${base}.simulation.patches`, patchIds);
      reportDuplicates(issues, 'DUPLICATE_CASE_ID', `${base}.simulation.cases`, lesson.simulation.cases.map((item) => item.id));
      for (const item of lesson.simulation.cases) for (const patchId of patchIds) {
        const outcome = item.expectedByPatch[patchId];
        const path = `${base}.simulation.cases.${item.id}.expectedByPatch.${patchId}`;
        if (!outcome) issues.push({ code: 'MISSING_PATCH_RESULT', path, message: `Case chưa có kết quả cho patch: ${patchId}` });
        else if ((outcome.status === 201) !== outcome.writesRecord) issues.push({ code: 'INVALID_SIMULATION_RESULT', path, message: 'Trong fixture này, chỉ response 201 mới được ghi record.' });
      }
    }

    if (lesson.contentStatus === 'published' && (lesson.objectives.length === 0 || lesson.completionEvidenceIds.length === 0 || lesson.references.length === 0)) issues.push({ code: 'INVALID_PUBLISHED_LESSON', path: base, message: 'Bài published phải có mục tiêu, tiêu chí hoàn thành và nguồn tham chiếu.' });
  }

  const byId = new Map(lessons.map((lesson) => [lesson.id, lesson]));
  const visited = new Set<string>();
  const active = new Set<string>();
  function visit(id: string) {
    if (active.has(id)) { issues.push({ code: 'PREREQUISITE_CYCLE', path: `lessons.${id}.prerequisiteLessonIds`, message: `Phát hiện vòng prerequisite tại bài: ${id}` }); return; }
    if (visited.has(id)) return;
    active.add(id);
    for (const dependency of byId.get(id)?.prerequisiteLessonIds ?? []) if (byId.has(dependency)) visit(dependency);
    active.delete(id);
    visited.add(id);
  }
  for (const lesson of lessons) visit(lesson.id);
  return issues;
}

export function assertValidContent(lessons: Lesson[]): void {
  const issues = validateContent(lessons);
  if (issues.length) throw new Error(`Dữ liệu bài học không hợp lệ:\n${issues.map((issue) => `- [${issue.code}] ${issue.path}: ${issue.message}`).join('\n')}`);
}

export function isChoiceExercise(exercise: Lesson['exercises'][number]): exercise is ChoiceExercise {
  return exercise.type === 'single-choice';
}
