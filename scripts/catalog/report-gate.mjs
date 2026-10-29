/** Required names độc lập với runner/test discovery: thiếu file, zero/skip hoặc
 * report giả xanh không đủ ca phải fail trước khi ghi catalog PASS. */
export const requiredCases = [
  'canonical95topics190competenciesMatchSourceDocuments','selectorAllOfKeepsExactCompetencyWithoutStageEdges',
  'bothRoadmapsShareStableIdsAndEveryStageExists','devopsEntryAndDpReadinessHaveNoJavaSpringOrOrmPrerequisite',
  'unknownProperty','wrongSchemaVersion','invalidCatalogVersion','invalidStatus','invalidTopicId','duplicateTopic',
  'duplicateCompetency','orphanCompetency','competencyWrongOwner','missingTopicPrerequisite','missingCapPrerequisite',
  'selfCycleThroughCap','transitiveCycle','malformedSelector','duplicateSelector','invalidStage','orphanPlacement',
  'duplicatePlacement','missingDpReadiness','danglingDpReadiness','ambiguousAlias','mappingFixtureIsDraftAndNotPublishedBody',
  'viewedMappingIsSubsetNotEveryTopicOfSharedUnit','publicProjectionNeverContainsPrivateCaseOrExpected','unitExpectedInjection',
  'unitMissingLocale','unitCanonicalOutsideMapping','unitViewedOutsideMapping','unitCapOutsideMapping',
  'unitPublishedAgainstPlanned','privateCaseUnknownRubricRejected','publicUnitProjectionIsDetachedAndHasNoPrivateMaterial',
];
export function verifyReport(text, exitCode) {
  const names = [...text.matchAll(/^# Subtest: (.+)$/gm)].map(match => match[1].trim());
  const stat = key => {
    const values = [...text.matchAll(new RegExp(`^# ${key} ([0-9]+)$`, 'gm'))];
    if (values.length !== 1) throw new Error('CATALOG_REPORT_INVALID'); return Number(values[0][1]);
  };
  if (exitCode !== 0 || names.length !== requiredCases.length || new Set(names).size !== names.length
      || requiredCases.some(name => !names.includes(name)) || /^not ok /m.test(text)
      || /# (SKIP|TODO)\b/.test(text) || stat('tests') !== requiredCases.length || stat('pass') !== requiredCases.length
      || ['fail','cancelled','skipped','todo'].some(key => stat(key) !== 0)) throw new Error('CATALOG_REPORT_NOT_PASSING');
  return names;
}
