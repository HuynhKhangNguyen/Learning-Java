import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { validateCatalog, publicOutline, publicUnit, validatePrivateCase } from './validate.mjs';

const root = fileURLToPath(new URL('../../', import.meta.url));
const read = path => JSON.parse(readFileSync(new URL(path, import.meta.url), 'utf8'));
const base = read('../../content-v2/catalog/catalog.json');
const fixture = read('../../contracts/catalog/fixtures/mapping.json');
const fresh = () => structuredClone(base);
const topic = (catalog, id) => catalog.topics.find(item => item.id === id);

test('canonical95topics190competenciesMatchSourceDocuments', () => {
  // Importer không phải oracle duy nhất: tests dưới còn có anchors độc lập.
  const generated = spawnSync('python', ['-X', 'utf8', 'scripts/catalog/import_outline.py'], { cwd: root, encoding: 'utf8', maxBuffer: 4 * 1024 * 1024 });
  assert.equal(generated.status, 0, generated.stderr); assert.deepEqual(base, JSON.parse(generated.stdout));
  assert.equal(base.topics.length, 95); assert.equal(base.competencies.length, 190);
  assert.equal(new Set(base.topics.map(item => item.id)).size, 95);
  assert.equal(base.units.length, 0); assert.ok(base.topics.every(item => item.status === 'planned'));
  validateCatalog(base);
});
test('selectorAllOfKeepsExactCompetencyWithoutStageEdges', () => {
  assert.deepEqual(topic(base, 'TOP-ENV-004').prerequisites, [{kind:'competency',id:'CAP-ENV-002-A'}]);
  assert.deepEqual(topic(base, 'TOP-CTR-001').prerequisites, [{kind:'competency',id:'CAP-ENV-002-B'}]);
  assert.deepEqual(topic(base, 'TOP-COL-004').prerequisites, [{kind:'topic',id:'TOP-COL-001'},{kind:'topic',id:'TOP-JAVA-010'}]);
});
test('bothRoadmapsShareStableIdsAndEveryStageExists', () => {
  assert.equal(new Set(base.placements.filter(p => p.roadmap === 'devops').map(p => p.stage)).size, 12);
  assert.equal(new Set(base.placements.filter(p => p.roadmap === 'java-backend').map(p => p.stage)).size, 11);
  assert.ok(base.placements.some(p => p.roadmap === 'java-backend' && p.topicId === 'TOP-CTR-001'));
  assert.ok(base.placements.some(p => p.roadmap === 'devops' && p.topicId === 'TOP-CTR-001'));
  assert.equal(base.milestones.length, 6);
});
test('devopsEntryAndDpReadinessHaveNoJavaSpringOrOrmPrerequisite', () => {
  const visited = new Set();
  function visit(id) {
    if (visited.has(id)) return; visited.add(id);
    for (const selector of topic(base,id).prerequisites) visit(selector.kind === 'topic' ? selector.id : base.competencies.find(cap => cap.id === selector.id).topicId);
  }
  for (const dp of base.milestones) for (const selector of dp.readiness) visit(selector.kind === 'topic' ? selector.id : base.competencies.find(cap => cap.id === selector.id).topicId);
  assert.ok([...visited].every(id => !/^TOP-(JAVA|SPR|DATA)-/.test(id)));
});

// Mỗi mutation phá một invariant riêng; không dùng validator để tự tính expected.
const negatives = [
  ['unknownProperty', c => { c.extra = true; }, 'SCHEMA_INVALID'],
  ['wrongSchemaVersion', c => { c.schemaVersion = 1; }, 'SCHEMA_INVALID'],
  ['invalidCatalogVersion', c => { c.catalogVersion = 0; }, 'SCHEMA_INVALID'],
  ['invalidStatus', c => { c.topics[0].status = 'complete'; }, 'SCHEMA_INVALID'],
  ['invalidTopicId', c => { c.topics[0].id = 'TOP-ENV-1'; }, 'SCHEMA_INVALID'],
  ['duplicateTopic', c => { c.topics.push({...c.topics[0],title:'different'}); }, 'TOPIC_DUPLICATE'],
  ['duplicateCompetency', c => { c.competencies.push({...c.competencies[0],description:'different'}); }, 'COMPETENCY_DUPLICATE'],
  ['orphanCompetency', c => { c.competencies[0].topicId = 'TOP-NONE-999'; }, 'COMPETENCY_TOPIC_MISSING'],
  ['competencyWrongOwner', c => { c.competencies[0].topicId = c.topics[1].id; }, 'COMPETENCY_OWNER_MISMATCH'],
  ['missingTopicPrerequisite', c => { c.topics[0].prerequisites = [{kind:'topic',id:'TOP-NONE-999'}]; }, 'PREREQUISITE_TOPIC_MISSING'],
  ['missingCapPrerequisite', c => { c.topics[0].prerequisites = [{kind:'competency',id:'CAP-ENV-002-Z'}]; }, 'PREREQUISITE_COMPETENCY_MISSING'],
  ['selfCycleThroughCap', c => { c.topics[0].prerequisites = [{kind:'competency',id:'CAP-ENV-001-A'}]; }, 'PREREQUISITE_CYCLE'],
  ['transitiveCycle', c => { topic(c,'TOP-ENV-002').prerequisites = [{kind:'topic',id:'TOP-ENV-004'}]; }, 'PREREQUISITE_CYCLE'],
  ['malformedSelector', c => { c.topics[0].prerequisites = [{kind:'topic',id:'TOP-ENV-002.A'}]; }, 'SCHEMA_INVALID'],
  ['duplicateSelector', c => { c.topics[2].prerequisites.push({...c.topics[2].prerequisites[0]}); }, 'SCHEMA_INVALID'],
  ['invalidStage', c => { c.placements[0].stage = 'S99'; }, 'SCHEMA_INVALID'],
  ['orphanPlacement', c => { c.placements[0].topicId = 'TOP-NONE-999'; }, 'PLACEMENT_TOPIC_MISSING'],
  ['duplicatePlacement', c => { c.placements.push({...c.placements[0]}); }, 'SCHEMA_INVALID'],
  ['missingDpReadiness', c => { c.milestones[0].readiness = []; }, 'SCHEMA_INVALID'],
  ['danglingDpReadiness', c => { c.milestones[0].readiness = [{kind:'topic',id:'TOP-NONE-999'}]; }, 'PREREQUISITE_TOPIC_MISSING'],
  ['ambiguousAlias', c => { c.aliases = [{legacyId:'old',topicId:'TOP-ENV-001'},{legacyId:'old',topicId:'TOP-ENV-002'}]; }, 'ALIAS_AMBIGUOUS'],
];
for (const [name, mutate, code] of negatives) test(name, () => {
  const value = fresh(); mutate(value); const before = structuredClone(value);
  assert.throws(() => validateCatalog(value), new RegExp(code)); assert.deepEqual(value,before);
});

function mapped() {
  const value = fresh(); value.units.push(structuredClone(fixture.unit));
  value.packages.push({id:'PKG-CATALOG-FIXTURE',topicIds:[...fixture.unit.topicIds],unitIds:[fixture.unit.id]});
  return value;
}
test('mappingFixtureIsDraftAndNotPublishedBody', () => { const c=mapped(); validateCatalog(c); validatePrivateCase(c,fixture.privateCase); assert.throws(()=>publicUnit(c,fixture.unit.id),/UNIT_BODY_UNAVAILABLE/); });
test('viewedMappingIsSubsetNotEveryTopicOfSharedUnit', () => { const c=mapped(); validateCatalog(c); assert.deepEqual(c.units[0].viewedTopicIds,['TOP-COL-002']); });
test('publicProjectionNeverContainsPrivateCaseOrExpected', () => {
  const c=mapped(); const before=structuredClone(c); const publicValue=publicOutline(c);
  assert.deepEqual(Object.keys(publicValue).sort(),['catalogVersion','placements','schemaVersion','topics']);
  assert.ok(!JSON.stringify(publicValue).includes(fixture.privateCase.expected.sentinel)); assert.deepEqual(c,before);
});
for (const [name,mutate,code] of [
  ['unitExpectedInjection',u=>{u.expected={answer:42};},'SCHEMA_INVALID'],
  ['unitMissingLocale',u=>{delete u.blocks[0].text.en;},'SCHEMA_INVALID'],
  ['unitCanonicalOutsideMapping',u=>{u.canonicalTopicId='TOP-ENV-001';},'UNIT_CANONICAL_TOPIC_MISMATCH'],
  ['unitViewedOutsideMapping',u=>{u.viewedTopicIds=['TOP-ENV-001'];},'UNIT_VIEWED_TOPIC_MISMATCH'],
  ['unitCapOutsideMapping',u=>{u.competencyIds=['CAP-ENV-001-A'];},'UNIT_COMPETENCY_MISMATCH'],
  ['unitPublishedAgainstPlanned',u=>{u.status='published';},'PLANNED_TOPIC_BODY'],
]) test(name,()=>{const c=mapped();mutate(c.units[0]);assert.throws(()=>validateCatalog(c),new RegExp(code));});
test('privateCaseUnknownRubricRejected',()=>{const c=mapped(),p=structuredClone(fixture.privateCase);p.rubricTargets=['CAP-ENV-001-A'];assert.throws(()=>validatePrivateCase(c,p),/PRIVATE_RUBRIC_TARGET_MISMATCH/);});
test('publicUnitProjectionIsDetachedAndHasNoPrivateMaterial',()=>{
  const c=mapped(); for(const id of fixture.unit.topicIds)topic(c,id).status='published';c.units[0].status='published';
  const result=publicUnit(c,fixture.unit.id);result.title.en='changed';assert.notEqual(c.units[0].title.en,'changed');
  assert.ok(!JSON.stringify(result).includes('PRIVATE_EXPECTED'));
});


// Metadata chỉ được đưa vào pipeline private; public projection không đổi contract.
test('privateV3MetadataAcceptedAndPrivateProjectionExcluded', () => {
  const value = read('../../contracts/fixtures/exposure/r08-private-a-v3.json');
  const catalog = mapped();
  validatePrivateCase(catalog, value);
  const publicValue = JSON.stringify(publicOutline(catalog));
  assert.ok(!publicValue.includes(value.expected.sentinel));
  assert.ok(!publicValue.includes(value.allocation.prompt.en));
});

test('privateV3UnknownMissingVersionAndSchemaReferencesRejected', () => {
  const original = read('../../contracts/fixtures/exposure/r08-private-a-v3.json');
  const mutations = [
    value => { delete value.allocation; },
    value => { value.schemaVersion = 4; },
    value => { value.schemaVersion = 2; },
    value => { value.allocation.owner = 'forged'; },
    value => { value.allocation.mode = 'clean'; },
    value => { value.allocation.inputSchema = { $ref: 'https://untrusted.invalid/schema' }; },
    value => { value.allocation.inputSchema = { properties: { x: { default: 42 } } }; },
    value => { value.allocation.inputSchema = { type: 'not-a-type' }; },
  ];
  for (const mutate of mutations) {
    const value = structuredClone(original);
    mutate(value);
    const before = structuredClone(value);
    assert.throws(() => validatePrivateCase(mapped(), value));
    assert.deepEqual(value, before);
  }
});


test('privateV3SharedMalformedSchemaCorpusRejectsRegexContradictionAndUnsupportedKeyword', () => {
  const original = read('../../contracts/fixtures/exposure/r08-private-a-v3.json');
  const corpus = read('../../contracts/fixtures/exposure/r08-invalid-schemas.json');
  for (const schema of corpus) {
    const value = structuredClone(original);
    value.allocation.inputSchema = schema;
    assert.throws(() => validatePrivateCase(mapped(), value));
  }
});
