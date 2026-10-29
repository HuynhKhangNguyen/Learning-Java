export type ContentStatus = 'planned' | 'draft' | 'published';
export type Difficulty = 'beginner' | 'intermediate' | 'advanced';
export type LearningStatus = 'not-started' | 'in-progress' | 'viewed' | 'passed' | 'applied' | 'review';
export type ActivityType = 'concept' | 'simulation' | 'diagnosis' | 'repair' | 'verification' | 'challenge' | 'result';

export interface LearningObjective {
  id: string;
  description: string;
}

export interface EvidenceCriterion {
  id: string;
  description: string;
}

export interface LessonStep {
  id: string;
  title: string;
  activity: ActivityType;
  instruction: string;
  objectiveIds: string[];
  exerciseIds: string[];
  evidenceIds: string[];
  nextStepId?: string;
}

export interface ChoiceOption {
  id: string;
  label: string;
  feedback: string;
}

export interface ChoiceExercise {
  id: string;
  type: 'single-choice';
  prompt: string;
  options: ChoiceOption[];
  correctOptionId: string;
}

export interface MatrixExercise {
  id: string;
  type: 'case-matrix';
  prompt: string;
  requiredCaseIds: string[];
}

export type Exercise = ChoiceExercise | MatrixExercise;

export interface SimulationResult {
  status: 201 | 400 | 500;
  writesRecord: boolean;
  storedTitle?: string;
  errorCode?: 'INVALID_TITLE' | 'BAD_REQUEST' | 'INTERNAL_ERROR';
}

export interface SimulationCase {
  id: string;
  label: string;
  request: { title?: string | null };
  forceStorageFailure?: boolean;
  expectedByPatch: Record<string, SimulationResult>;
}

export interface SimulationPatch {
  id: string;
  title: string;
  summary: string;
}

export interface SimulationDefinition {
  id: string;
  label: string;
  disclaimer: string;
  limitations: string[];
  patches: SimulationPatch[];
  cases: SimulationCase[];
}

export interface LessonReference {
  title: string;
  publisher: string;
  url: string;
  reviewedAt: string;
}

export interface Lesson {
  schemaVersion: 1;
  id: string;
  version: number;
  contentStatus: ContentStatus;
  domain: string;
  title: string;
  summary: string;
  difficulty: Difficulty;
  estimatedMinutes: number;
  prerequisiteLessonIds: string[];
  entryStepId: string;
  objectives: LearningObjective[];
  evidenceCriteria: EvidenceCriterion[];
  completionEvidenceIds: string[];
  steps: LessonStep[];
  exercises: Exercise[];
  simulation?: SimulationDefinition;
  references: LessonReference[];
}

export interface AttemptEvidence {
  evidenceId: string;
  earnedAt: string;
  payload?: Record<string, string | number | boolean>;
}

export interface LessonAttempt {
  id: string;
  startedAt: string;
  completedAt?: string;
  hintLevel: 0 | 1 | 2 | 3;
  answers: Record<string, string | string[]>;
  evidence: AttemptEvidence[];
}

export interface LessonProgress {
  schemaVersion: 1;
  lessonId: string;
  lessonVersion: number;
  currentStepId: string;
  status: LearningStatus;
  input: { title?: string | null };
  patchId: string;
  attempts: LessonAttempt[];
  updatedAt: string;
}
