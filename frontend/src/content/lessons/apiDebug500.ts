import type { Lesson, SimulationResult } from '../types.ts';

const result = (status: 201 | 400 | 500, writesRecord: boolean, extra: Partial<SimulationResult> = {}): SimulationResult => ({ status, writesRecord, ...extra });
const invalid = result(400, false, { errorCode: 'INVALID_TITLE' });
const internal = result(500, false, { errorCode: 'INTERNAL_ERROR' });
const badRequest = result(400, false, { errorCode: 'BAD_REQUEST' });
const created = (storedTitle: string) => result(201, true, { storedTitle });

export const apiDebug500Lesson: Lesson = {
  schemaVersion: 1,
  id: 'api-debug-500',
  version: 1,
  contentStatus: 'draft',
  domain: 'http-api',
  title: 'Cứu API trả lỗi 500',
  summary: 'Gửi request, lần theo trace và kiểm chứng cách xử lý title không hợp lệ mà không che lỗi nội bộ.',
  difficulty: 'beginner',
  estimatedMinutes: 20,
  prerequisiteLessonIds: [],
  entryStepId: 'intro',
  objectives: [
    { id: 'read-http', description: 'Đọc method, path, JSON và status của request/response.' },
    { id: 'trace-failure', description: 'Lần theo trace để tìm bước thất bại từ bằng chứng.' },
    { id: 'explain-null', description: 'Giải thích vì sao title null gây lỗi trước khi ghi database.' },
    { id: 'validate-boundary', description: 'Phân loại lỗi input và lỗi nội bộ đúng ranh giới API.' },
    { id: 'verify-contract', description: 'Kiểm tra cả trường hợp hợp lệ, không hợp lệ và lỗi lưu trữ.' },
  ],
  evidenceCriteria: [
    { id: 'c1-reproduce', description: 'Tái hiện 500 với request thiếu title ở bản original.' },
    { id: 'c2-locate', description: 'Xác định service và title=null là bằng chứng của điểm lỗi.' },
    { id: 'c3-contract', description: 'Chọn patch đạt đủ sáu trường hợp kiểm tra.' },
    { id: 'c4-blank', description: 'Xử lý title blank và giải thích vì sao null-only chưa đủ.' },
    { id: 'c5-internal', description: 'Giữ lỗi ghi dữ liệu nội bộ là lỗi 500.' },
  ],
  completionEvidenceIds: ['c1-reproduce', 'c2-locate', 'c3-contract', 'c4-blank', 'c5-internal'],
  steps: [
    { id: 'intro', title: 'Request đi đâu?', activity: 'concept', instruction: 'Quan sát request và response có chú giải trước khi chạy mô phỏng.', objectiveIds: ['read-http'], exerciseIds: [], evidenceIds: [], nextStepId: 'reproduce' },
    { id: 'reproduce', title: 'Tái hiện lỗi 500', activity: 'simulation', instruction: 'Gửi preset thiếu title với bản code original.', objectiveIds: ['read-http'], exerciseIds: [], evidenceIds: ['c1-reproduce'], nextStepId: 'trace' },
    { id: 'trace', title: 'Lần theo dấu vết', activity: 'simulation', instruction: 'Đi từng bước và tìm lớp cuối cùng đã chạy.', objectiveIds: ['trace-failure'], exerciseIds: [], evidenceIds: ['c2-locate'], nextStepId: 'diagnose' },
    { id: 'diagnose', title: 'Chẩn đoán nguyên nhân', activity: 'diagnosis', instruction: 'Chọn lời giải thích khớp với trace và dữ liệu quan sát được.', objectiveIds: ['explain-null'], exerciseIds: ['diagnose-cause'], evidenceIds: [], nextStepId: 'repair' },
    { id: 'repair', title: 'Chọn cách sửa', activity: 'repair', instruction: 'So sánh các patch và dự đoán ảnh hưởng của từng cách.', objectiveIds: ['validate-boundary'], exerciseIds: ['choose-patch'], evidenceIds: [], nextStepId: 'verify' },
    { id: 'verify', title: 'Kiểm chứng contract', activity: 'verification', instruction: 'Chạy bộ case nền với patch đã chọn.', objectiveIds: ['verify-contract'], exerciseIds: ['verify-core-cases'], evidenceIds: ['c3-contract'], nextStepId: 'challenge' },
    { id: 'challenge', title: 'Tình huống mới: title blank', activity: 'challenge', instruction: 'Giải quyết chuỗi rỗng, khoảng trắng và lỗi ghi nội bộ.', objectiveIds: ['validate-boundary', 'verify-contract'], exerciseIds: ['challenge-cases', 'explain-blank', 'classify-storage-error'], evidenceIds: ['c4-blank', 'c5-internal'], nextStepId: 'result' },
    { id: 'result', title: 'Bằng chứng đã đạt', activity: 'result', instruction: 'Xem tiêu chí đã đạt, mức gợi ý và bước nên học tiếp.', objectiveIds: [], exerciseIds: [], evidenceIds: [] },
  ],
  exercises: [
    { id: 'diagnose-cause', type: 'single-choice', prompt: 'Nguyên nhân nào khớp với trace?', correctOptionId: 'missing-validation', options: [
      { id: 'database-down', label: 'Database không phản hồi', feedback: 'Trace cho thấy repository chưa được gọi.' },
      { id: 'missing-validation', label: 'title được dùng trước khi kiểm tra null', feedback: 'Đúng: service gọi strip() trên title=null.' },
      { id: 'wrong-method', label: 'Client dùng sai HTTP method', feedback: 'Request đã vào controller nên đây không phải dấu hiệu chính.' },
    ] },
    { id: 'choose-patch', type: 'single-choice', prompt: 'Patch nào giữ đúng ranh giới lỗi?', correctOptionId: 'validate-title', options: [
      { id: 'null-only', label: 'Chỉ kiểm tra null', feedback: 'Chuỗi rỗng và toàn khoảng trắng vẫn lọt qua.' },
      { id: 'catch-all-400', label: 'Đổi mọi exception thành 400', feedback: 'Cách này che lỗi nội bộ của server.' },
      { id: 'validate-title', label: 'Validate missing/null/blank tại biên API', feedback: 'Đúng: input sai thành 400, lỗi nội bộ vẫn là 500.' },
    ] },
    { id: 'verify-core-cases', type: 'case-matrix', prompt: 'Chạy bốn case nền.', requiredCaseIds: ['missing', 'null', 'valid', 'storage-failure'] },
    { id: 'challenge-cases', type: 'case-matrix', prompt: 'Chạy hai case blank chưa xuất hiện ở phần kiểm chứng.', requiredCaseIds: ['empty', 'whitespace'] },
    { id: 'explain-blank', type: 'single-choice', prompt: 'Vì sao null-only chưa đủ?', correctOptionId: 'blank-remains', options: [
      { id: 'blank-remains', label: 'Chuỗi blank không phải null và vẫn có thể tạo dữ liệu rỗng', feedback: 'Đúng: phải kiểm tra sau khi trim/strip hoặc dùng constraint tương đương.' },
      { id: 'java-bug', label: 'Java luôn đổi blank thành null', feedback: 'Java không tự thực hiện chuyển đổi này.' },
    ] },
    { id: 'classify-storage-error', type: 'single-choice', prompt: 'Lỗi ghi dữ liệu nội bộ nên trả gì?', correctOptionId: 'keep-500', options: [
      { id: 'all-400', label: '400 vì request đã đi qua API', feedback: 'Request hợp lệ; đổi lỗi nội bộ thành 400 làm sai contract.' },
      { id: 'keep-500', label: '500 với response chung, chi tiết ở log nội bộ', feedback: 'Đúng: client không gây ra lỗi này và không nhận stack trace.' },
    ] },
  ],
  simulation: {
    id: 'task-api-request-flow-v1',
    label: 'Mô phỏng tương tác — không chạy Java hoặc database thật',
    disclaimer: 'Kết quả được tạo từ state machine cố định để dạy luồng request, không phải output của Spring.',
    limitations: ['Chỉ hỗ trợ POST /tasks và trường title.', 'Mỗi lần chạy dùng fixture riêng.', 'Không mô phỏng mạng hoặc database đầy đủ.'],
    patches: [
      { id: 'original', title: 'Bản lỗi', summary: 'Xử lý title trước validation.' },
      { id: 'null-only', title: 'Kiểm tra null', summary: 'Từ chối missing/null nhưng nhận blank.' },
      { id: 'catch-all-400', title: 'Catch mọi lỗi', summary: 'Biến mọi exception thành 400.' },
      { id: 'validate-title', title: 'Validate title', summary: 'Từ chối missing/null/blank tại controller.' },
    ],
    cases: [
      { id: 'missing', label: '{}', request: {}, expectedByPatch: { original: internal, 'null-only': invalid, 'catch-all-400': badRequest, 'validate-title': invalid } },
      { id: 'null', label: 'title=null', request: { title: null }, expectedByPatch: { original: internal, 'null-only': invalid, 'catch-all-400': badRequest, 'validate-title': invalid } },
      { id: 'empty', label: 'title=""', request: { title: '' }, expectedByPatch: { original: created(''), 'null-only': created(''), 'catch-all-400': created(''), 'validate-title': invalid } },
      { id: 'whitespace', label: 'title="   "', request: { title: '   ' }, expectedByPatch: { original: created(''), 'null-only': created(''), 'catch-all-400': created(''), 'validate-title': invalid } },
      { id: 'valid', label: 'title="  Học Java  "', request: { title: '  Học Java  ' }, expectedByPatch: { original: created('Học Java'), 'null-only': created('Học Java'), 'catch-all-400': created('Học Java'), 'validate-title': created('Học Java') } },
      { id: 'storage-failure', label: 'title hợp lệ + lỗi lưu', request: { title: 'Học Java' }, forceStorageFailure: true, expectedByPatch: { original: internal, 'null-only': internal, 'catch-all-400': badRequest, 'validate-title': internal } },
    ],
  },
  references: [
    { title: 'RFC 9110 — HTTP Semantics', publisher: 'IETF', url: 'https://www.rfc-editor.org/rfc/rfc9110.html', reviewedAt: '2026-09-19' },
  ],
};
