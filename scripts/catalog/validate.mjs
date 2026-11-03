import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';

const require = createRequire(new URL('../../frontend/package.json', import.meta.url));
const Ajv = require('ajv');
const read = (path) => JSON.parse(readFileSync(new URL(path, import.meta.url), 'utf8'));
// Dùng đúng dependency đã khóa trong FE, chỉ cho tooling authoring. Không
// thêm dependency runtime/backend, không tự tải module thiếu hoặc đổi lockfile.
const lock = read('../../frontend/package-lock.json');
if (require('ajv/package.json').version !== lock.packages['node_modules/ajv'].version) {
  throw new Error('Catalog validator dependency differs from existing lockfile');
}
const ajv = new Ajv({ allErrors: true, jsonPointers: true, removeAdditional: false, useDefaults: false, coerceTypes: false });
const catalogShape = ajv.compile(read('../../contracts/catalog/catalog-v2.schema.json'));
const privateShapes = new Map([
  [2, ajv.compile(read('../../contracts/catalog/private-case-v2.schema.json'))],
  [3, ajv.compile(read('../../contracts/catalog/private-case-v3.schema.json'))],
]);

function requireShape(validator, value) {
  // Không sửa input khi validation lỗi; error chỉ nêu path/keyword, không
  // dump body/private expected hoặc nội dung người học ra log.
  if (!validator(value)) throw new Error('SCHEMA_INVALID: ' + validator.errors.map(e => `${e.dataPath}:${e.keyword}`).join(', '));
}
function check(condition, code) { if (!condition) throw new Error(code); }
function index(items, code) {
  const result = new Map();
  for (const item of items) { check(!result.has(item.id), code); result.set(item.id, item); }
  return result;
}
const copy = value => JSON.parse(JSON.stringify(value));

/** Kiểm schema trước graph để malformed input không gây dereference tùy ý.
 * Trả chính dữ liệu không thay đổi; không ghi file/publish hoặc tạo progress.
 */
export function validateCatalog(catalog) {
  requireShape(catalogShape, catalog);
  const topics = index(catalog.topics, 'TOPIC_DUPLICATE');
  const caps = index(catalog.competencies, 'COMPETENCY_DUPLICATE');
  const units = index(catalog.units, 'UNIT_DUPLICATE');
  index(catalog.packages, 'PACKAGE_DUPLICATE'); index(catalog.milestones, 'MILESTONE_DUPLICATE');
  for (const cap of caps.values()) {
    check(topics.has(cap.topicId), 'COMPETENCY_TOPIC_MISSING');
    check(cap.id.replace(/^CAP-/, 'TOP-').replace(/-[A-Z]$/, '') === cap.topicId, 'COMPETENCY_OWNER_MISMATCH');
  }
  for (const topic of topics.values()) check(catalog.competencies.some(cap => cap.topicId === topic.id), 'TOPIC_WITHOUT_COMPETENCY');
  const resolve = selector => {
    if (selector.kind === 'topic') { check(topics.has(selector.id), 'PREREQUISITE_TOPIC_MISSING'); return selector.id; }
    check(caps.has(selector.id), 'PREREQUISITE_COMPETENCY_MISSING'); return caps.get(selector.id).topicId;
  };
  // DFS ba màu (chưa thăm/đang thăm/đã xong) bắt cả cycle bắc cầu và
  // self-reference qua CAP. Chặng/P/DP không tự sinh cạnh prerequisite.
  const visiting = new Set(), visited = new Set();
  function visit(id) {
    check(!visiting.has(id), 'PREREQUISITE_CYCLE'); if (visited.has(id)) return;
    visiting.add(id);
    for (const selector of topics.get(id).prerequisites) visit(resolve(selector));
    visiting.delete(id); visited.add(id);
  }
  for (const id of topics.keys()) visit(id);
  for (const milestone of catalog.milestones) for (const selector of milestone.readiness) resolve(selector);
  const placements = new Set();
  for (const placement of catalog.placements) {
    check(topics.has(placement.topicId), 'PLACEMENT_TOPIC_MISSING');
    const key = `${placement.roadmap}/${placement.stage}/${placement.topicId}`;
    check(!placements.has(key), 'PLACEMENT_DUPLICATE'); placements.add(key);
  }
  const aliases = new Set();
  for (const alias of catalog.aliases) {
    check(topics.has(alias.topicId), 'ALIAS_TOPIC_MISSING');
    check(!topics.has(alias.legacyId) && !aliases.has(alias.legacyId), 'ALIAS_AMBIGUOUS'); aliases.add(alias.legacyId);
  }
  for (const unit of units.values()) {
    for (const id of unit.topicIds) check(topics.has(id), 'UNIT_TOPIC_MISSING');
    check(unit.topicIds.includes(unit.canonicalTopicId), 'UNIT_CANONICAL_TOPIC_MISMATCH');
    for (const id of unit.competencyIds) check(caps.has(id) && unit.topicIds.includes(caps.get(id).topicId), 'UNIT_COMPETENCY_MISMATCH');
    for (const id of unit.viewedTopicIds) check(unit.topicIds.includes(id), 'UNIT_VIEWED_TOPIC_MISMATCH');
    if (unit.status === 'published') for (const id of unit.topicIds) check(topics.get(id).status === 'published', 'PLANNED_TOPIC_BODY');
  }
  for (const pkg of catalog.packages) {
    for (const id of pkg.topicIds) check(topics.has(id), 'PACKAGE_TOPIC_MISSING');
    for (const id of pkg.unitIds) {
      check(units.has(id), 'PACKAGE_UNIT_MISSING');
      check(units.get(id).topicIds.every(topic => pkg.topicIds.includes(topic)), 'PACKAGE_MAPPING_MISMATCH');
    }
  }
  return catalog;
}

/** Outline anonymous là allowlist, không spread authoring bundle/private case.
 * Locale của metadata cũ chưa là bài song ngữ hoặc published body.
 */
export function publicOutline(catalog) {
  validateCatalog(catalog);
  return { schemaVersion: 2, catalogVersion: catalog.catalogVersion,
    topics: catalog.topics.map(({ id, title, status, prerequisites }) => ({ id, title, status, prerequisites: copy(prerequisites) })),
    placements: copy(catalog.placements) };
}

/** Unit mapping do server resolve về sau; client không tự chọn CAP/viewed list.
 * R05 chỉ kiểm contract, chưa endpoint, viewed-event hoặc learner evidence.
 */
export function publicUnit(catalog, id) {
  validateCatalog(catalog);
  const unit = catalog.units.find(item => item.id === id);
  check(unit && unit.status === 'published', 'UNIT_BODY_UNAVAILABLE');
  return copy(unit);
}

/** Private expected chỉ được gắn với unit/CAP đã khai báo, không tự thêm
 * năng lực khi chấm. Hàm này không trả public DTO hoặc publish case; caller
 * về sau vẫn phải bảo vệ disclosure theo auth/publication/journal contracts.
 */
export function validatePrivateCase(catalog, value) {
  validateCatalog(catalog);
  const shape = privateShapes.get(value?.schemaVersion);
  check(shape, 'PRIVATE_CASE_VERSION_UNSUPPORTED');
  requireShape(shape, value);
  if (value.schemaVersion === 3) {
    const schema = value.allocation.inputSchema;
    // Không compile schema learner: chỉ kiểm syntax, không resolve network/file
    // hoặc chạy plugin. Recursive scan cấm keyword chứa đáp án hay reference.
    let count = 0;
    function inspect(node, depth) {
      check(depth <= 16 && ++count <= 4096, 'PRIVATE_SCHEMA_BUDGET');
      if (node && typeof node === 'object') {
        for (const [key, child] of Object.entries(node)) {
          check(!['$ref', '$id', 'default', 'examples', 'example', 'expected'].includes(key),
            'PRIVATE_SCHEMA_KEYWORD_DENIED');
          inspect(child, depth + 1);
        }
      }
    }
    inspect(schema, 0);
    check(ajv.validateSchema(schema), 'PRIVATE_SCHEMA_INVALID');
  }
  const unit = catalog.units.find(item => item.id === value.unitId);
  check(unit, 'PRIVATE_CASE_UNIT_MISSING');
  for (const id of value.rubricTargets) check(unit.competencyIds.includes(id), 'PRIVATE_RUBRIC_TARGET_MISMATCH');
  return value;
}
