import test from 'node:test';
import assert from 'node:assert/strict';
import { requiredCases, verifyReport } from './report-gate.mjs';
// Fixture report chỉ kiểm gate, không thay actual schema/DAG runtime tests.
const passing = () => requiredCases.map((name,i)=>`# Subtest: ${name}\nok ${i+1} - ${name}`).join('\n')
  + `\n# tests 38\n# pass 38\n# fail 0\n# cancelled 0\n# skipped 0\n# todo 0\n`;
test('gateAcceptsExactReport',()=>assert.equal(verifyReport(passing(),0).length,38));
for (const [name,mutate,exit] of [
  ['empty',()=>'',0],['nativeFailure',text=>text,1],['missingCase',text=>text.replace('# Subtest: unknownProperty','unknownProperty'),0],
  ['zero',text=>text.replace('# tests 38','# tests 0'),0],['skip',text=>text.replace('# skipped 0','# skipped 1'),0],
  ['hiddenFailure',text=>text+'not ok 99 - hidden\n',0],['duplicateSummary',text=>text+'# tests 38\n',0],
  ['wrongCase',text=>text.replace('unknownProperty','wrongProperty'),0],['todo',text=>text+'# TODO fixture\n',0],
]) test(name,()=>assert.throws(()=>verifyReport(mutate(passing()),exit)));
