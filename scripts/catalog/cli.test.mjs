import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { randomUUID } from 'node:crypto';

const root=fileURLToPath(new URL('../../',import.meta.url));
const run=(program,args)=>spawnSync(program,args,{cwd:root,encoding:'utf8',timeout:120000,maxBuffer:1024*1024});
// Test negative qua process thật: exit code là oracle, không dùng text PASS
// hoặc command double thay helper. Không Java/DB/network trong các case này.
for(const [name,args] of [
  ['wrongService',['-Task','contract','-Service','learning']],
  ['wrongTask',['-Task','db','-Service','content']],
  ['allServices',['-Task','contract','-Service','all']],
  ['ignoredTrust',['-Task','contract','-Service','content','-TrustConfig','fixture']],
  ['ignoredCase',['-Task','contract','-Service','content','-Case','SEC-01']],
]) test(name,()=>{const result=run('pwsh',['-NoProfile','-File','scripts/services.ps1',...args,'-EvidenceTask','R05']);assert.equal(result.error,undefined);assert.notEqual(result.status,0);});
test('unknownCatalogArgumentFails',()=>{const result=run(process.execPath,['scripts/catalog/contract.mjs','--unknown']);assert.equal(result.status,1);assert.match(result.stderr,/CATALOG_ARGUMENTS_INVALID/);});
test('missingCatalogFails',()=>{const result=run(process.execPath,['scripts/catalog/contract.mjs','--catalog','content-v2/catalog/absent.json']);assert.equal(result.status,1);});
test('schemaInvalidCatalogFails',()=>{const result=run(process.execPath,['scripts/catalog/contract.mjs','--catalog','contracts/catalog/fixtures/mapping.json']);assert.equal(result.status,1);});
test('validShapeButDifferentSnapshotCannotBorrowCanonicalTests',()=>{
  // Giữ95/190 và references hợp lệ, chỉ đổi title: structural validator cho
  // phép, nhưng CMD04 không được lấy test của snapshot chuẩn chứng nhận file này.
  const dir=resolve(root,'artifacts/quality/R05','cli-fixture-'+randomUUID());mkdirSync(dir,{recursive:true});
  const data=JSON.parse(readFileSync(resolve(root,'content-v2/catalog/catalog.json'),'utf8'));data.topics[0].title='Different fixture title';
  const path=resolve(dir,'different-catalog.json');writeFileSync(path,JSON.stringify(data),'utf8');
  const result=run(process.execPath,['scripts/catalog/contract.mjs','--catalog',path]);
  assert.equal(result.status,1);assert.match(result.stderr,/CANONICAL_SNAPSHOT_DIFFERS/);
});
