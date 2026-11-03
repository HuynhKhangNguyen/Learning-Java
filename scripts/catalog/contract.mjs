import { readFileSync, writeFileSync, mkdirSync, realpathSync, statSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { resolve, sep } from 'node:path';
import { randomUUID, createHash } from 'node:crypto';
import { isDeepStrictEqual } from 'node:util';
import { validateCatalog } from './validate.mjs';
import { verifyReport } from './report-gate.mjs';

const root = realpathSync(fileURLToPath(new URL('../../',import.meta.url)));
const hash = path => createHash('sha256').update(readFileSync(path)).digest('hex');

/** CMD04 authoring standalone: schema/data rồi exact tests; không Java/DB/TLS.
 * Evidence luôn ở UUID mới dưới own artifacts, không ghi đè report hoặc catalog.
 */
try {
  const major = Number(process.versions.node.split('.')[0]);
  if (major < 24) throw new Error('CATALOG_REQUIRES_NODE24');
  const args=process.argv.slice(2);
  if (args.length !== 0 && !(args.length === 2 && args[0] === '--catalog')) throw new Error('CATALOG_ARGUMENTS_INVALID');
  const path=realpathSync(resolve(root,args[1] ?? 'content-v2/catalog/catalog.json'));
  if (!path.startsWith(root+sep) || statSync(path).size > 2*1024*1024) throw new Error('CATALOG_INPUT_OUTSIDE_OR_TOO_LARGE');
  const catalog=JSON.parse(readFileSync(path,'utf8')); validateCatalog(catalog);
  if (catalog.topics.length !== 95 || catalog.competencies.length !== 190) throw new Error('CANONICAL_COUNTS_DIFFER');
  // Test source-doc parity chạy trên snapshot chuẩn; file override cũng phải
  // cùng semantics, không được dùng test của snapshot khác để chứng nhận nó.
  const canonical=JSON.parse(readFileSync(resolve(root,'content-v2/catalog/catalog.json'),'utf8'));
  if(!isDeepStrictEqual(catalog,canonical))throw new Error('CANONICAL_SNAPSHOT_DIFFERS');
  // Spawn native không shell string, có timeout/buffer bound. Child error/nonzero
  // hoặc thiếu required cases không được chuyển thành PASS từ stdout một phần.
  const result=spawnSync(process.execPath,['--test','--test-reporter=tap',resolve(root,'scripts/catalog/catalog.test.mjs')],
    {cwd:root,encoding:'utf8',timeout:120000,maxBuffer:4*1024*1024});
  if(result.error)throw new Error('CATALOG_TEST_PROCESS_UNAVAILABLE');
  const cases=verifyReport(result.stdout,result.status);
  const runId=randomUUID(),directory=resolve(root,'artifacts/quality/R05',runId);
  mkdirSync(directory,{recursive:true});
  const inputs={};
  for(const name of ['scripts/services.ps1','scripts/catalog/contract.mjs','scripts/catalog/validate.mjs','scripts/catalog/catalog.test.mjs',
    'scripts/catalog/report-gate.mjs','scripts/catalog/report-gate.test.mjs','scripts/catalog/cli.test.mjs','scripts/catalog/import_outline.py',
    'contracts/catalog/catalog-v2.schema.json','contracts/catalog/private-case-v2.schema.json',
    'contracts/catalog/private-case-v3.schema.json','contracts/catalog/fixtures/mapping.json',
    'contracts/fixtures/exposure/r08-private-a-v3.json',
    'content-v2/catalog/catalog.json','frontend/package.json','frontend/package-lock.json',
    'docs/curriculum/core-foundations.md','docs/curriculum/core-api-data.md','docs/curriculum/core-security-operations.md','docs/curriculum/devops-catalog.md']) {
    inputs[name]=hash(resolve(root,name));
  }
  const report={status:'PASS',task:'BL-R05',command:'CMD-04 catalog',runId,scope:'Standalone catalog schema/reference/DAG/mapping; no DB/API/publication/learning evidence',
    nodeVersion:process.version,inputFile:path,inputSha256:hash(path),topics:catalog.topics.length,competencies:catalog.competencies.length,
    placements:catalog.placements.length,milestones:catalog.milestones.length,tests:cases.length,failures:0,skipped:0,cases,inputs};
  writeFileSync(resolve(directory,'contract-tests.tap'),result.stdout,'utf8');
  writeFileSync(resolve(directory,'report.json'),JSON.stringify(report,null,2)+'\n','utf8');
  console.log(JSON.stringify({status:report.status,scope:report.scope,runId,tests:report.tests}));
} catch(failure) {
  // JSON errors/schema diagnostics có thể chứa input; chỉ phát error code cố định
  // hoặc schema path/keyword đã scrub, không dump document hoặc private expected.
  const message=String(failure.message);
  console.error(/^[A-Z_0-9]+$/.test(message)?message:'CATALOG_VALIDATION_FAILED');process.exitCode=1;
}
