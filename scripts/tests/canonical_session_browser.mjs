// R04d: browser fixture riêng, không sửa launcher R04a, FE hoặc production edge.
// Chỉ named localhost:17441 -> Identity18081; PKI/secret từ owned driver stdin,
// chỉ giữ RAM, không trace/video/storage hoặc log cookie/password/proof.
import { createRequire } from 'node:module';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createInterface } from 'node:readline';
import { X509Certificate, createHash, randomUUID } from 'node:crypto';
import https from 'node:https';
import tls from 'node:tls';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const require = createRequire(resolve(root, 'frontend/package.json'));
const { chromium } = require('@playwright/test');
const Ajv = require('ajv'); // Dependency có sẵn theo FE lockfile, không cài thêm.
const validate = new Ajv();
const requestSchema = validate.compile(JSON.parse(await readFile(resolve(root, 'contracts/http/identity/request.schema.json'), 'utf8')));
const responseSchema = validate.compile(JSON.parse(await readFile(resolve(root, 'contracts/http/identity/response.schema.json'), 'utf8')));
const lines = createInterface({ input: process.stdin })[Symbol.asyncIterator]();
const checks = [], held = new Map();
let browser, server, agent, phase = 'setup', runtimeErrors = 0, headerFailure = false;
const check = (ok, label) => { if (!ok) throw new Error(label); checks.push(label); };
const line = async () => {
  // Driver không được giữ browser vô hạn khi lỗi/restart; timeout là fixture
  // control, không sửa transaction/network budget của service sản phẩm.
  let timer;
  try { return await Promise.race([lines.next().then(r => {
    if (r.done || r.value.length > 8192) throw new Error('bounded driver input');
    return JSON.parse(r.value);
  }), new Promise((_, fail) => { timer = setTimeout(() => fail(new Error('driver timeout')), 30000); })]); }
  finally { clearTimeout(timer); }
};
const signal = value => process.stdout.write(JSON.stringify(value) + '\n');
const intent = (purpose, context, key, password) => {
  // Oracle độc lập với Java: domain + zero + bốn length-framed UTF8 fields.
  const hash = createHash('sha256').update(Buffer.from('identity-sensitive-intent-v1\0'));
  for (const field of [purpose, context, key, password ?? '']) {
    const bytes = Buffer.from(field, 'utf8'), size = Buffer.alloc(4);
    size.writeUInt32BE(bytes.length); hash.update(size).update(bytes);
  }
  return hash.digest('hex');
};
try {
  const config = await line();
  if (config.origin !== 'https://localhost:17441' || !['LOGOUT_ALL', 'PASSWORD_CHANGE'].includes(config.purpose)) throw new Error('named fixture required');
  const directory = resolve(config.directory), ownership = JSON.parse(await readFile(resolve(directory, 'ownership.json'), 'utf8'));
  if (resolve(ownership.workspace) !== root || !directory.startsWith(resolve(root, '.tools/trust-lab') + '\\')) throw new Error('owned PKI required');
  const pfx = await readFile(resolve(directory, 'edge.p12'));
  const ca = new X509Certificate(await readFile(resolve(directory, 'ca.crt'))).toString();
  agent = new https.Agent({ pfx, passphrase: config.storePassword, ca, rejectUnauthorized: true,
    checkServerIdentity(host, cert) {
      return tls.checkServerIdentity(host, cert) || (!cert.subjectaltname?.includes('URI:spiffe://backend-lab/lab/identity') ? new Error('identity URI mismatch') : undefined);
    } });
  server = https.createServer({ pfx, passphrase: config.storePassword }, (request, response) => {
    if (['/', '/back', '/favicon.ico'].includes(request.url)) {
      response.writeHead(request.url === '/favicon.ico' ? 204 : 200, { 'Content-Type': 'text/html', 'Cache-Control': 'no-store' });
      response.end('<!doctype html><title>Owned R04d browser fixture</title><p>Session protocol qualification, not learner UI</p>'); return;
    }
    // Không arbitrary route/upstream hoặc forward browser principal headers.
    if (!/^\/api\/v1\/(session|auth\/(login|reauth|logout-all|password-change|operations\/[a-f0-9-]{36}))$/.test(request.url)) {
      response.writeHead(404); response.end(); return;
    }
    const headers = {};
    for (const name of ['content-type', 'cookie', 'origin', 'x-csrf-token', 'x-session-context', 'x-auth-flow', 'idempotency-key']) {
      if (request.headers[name] !== undefined) headers[name] = request.headers[name];
    }
    headers['X-Backend-Lab-Client-IP'] = '127.0.0.1';
    const upstream = https.request({ hostname: '127.0.0.1', servername: 'localhost', port: 18081,
      method: request.method, path: request.url, headers, agent, timeout: 8000 }, incoming => {
      const chunks = []; let size = 0;
      incoming.on('data', chunk => { size += chunk.length; if (size > 16384) upstream.destroy(); else chunks.push(chunk); });
      incoming.on('end', () => {
        try {
          const payload = Buffer.concat(chunks);
          const allowedCookie = request.method === 'POST' && ['/api/v1/session', '/api/v1/auth/login'].includes(request.url);
          if (incoming.headers['cache-control'] !== 'no-store' || (!allowedCookie && incoming.headers['set-cookie'])) headerFailure = true;
          const send = () => { response.writeHead(incoming.statusCode, incoming.headers); response.end(payload); };
          if (request.headers['x-lab-hold'] === 'sensitive-A') {
            // Giữ exact response bytes thực sau server commit, không fake status.
            held.set('sensitive-A', { send, status: incoming.statusCode, data: JSON.parse(payload), noCookie: !incoming.headers['set-cookie'] });
          } else send();
        } catch {
          // Callback error không thoát Node bỏ lại browser child hoặc in body
          // chứa proof. Browser fetch phải FAIL rồi đi qua outer finally.
          headerFailure = true; if (!response.headersSent) response.writeHead(503); response.end();
        }
      });
    });
    upstream.on('timeout', () => upstream.destroy());
    upstream.on('error', () => { if (!response.headersSent) response.writeHead(503); response.end(); });
    request.pipe(upstream);
  });
  await new Promise((done, fail) => { server.once('error', fail); server.listen(17441, '127.0.0.1', done); });
  browser = await chromium.launch({ channel: 'msedge', headless: true });
  const context = await browser.newContext({ ignoreHTTPSErrors: true }); // Chỉ TLS front fixture; upstream CA/SAN verification vẫn bật.
  const monitor = page => {
    page.on('pageerror', () => runtimeErrors++);
    page.on('console', message => {
      // Browser tự log các HTTP denial dự kiến; không coi chúng là JS runtime
      // error. Không in message có thể chứa dữ liệu nhạy cảm vào evidence.
      if (message.type() === 'error' && !/^Failed to load resource: the server responded with a status of (401|403|404|409)\b/.test(message.text())) runtimeErrors++;
    });
  };
  const page = await context.newPage(); monitor(page); await page.goto(config.origin);
  const request = async (target, path, body, headers = {}) => {
    if (body !== undefined && !requestSchema(body)) throw new Error('request schema');
    const result = await target.evaluate(async ({ path, body, headers }) => {
      const response = await fetch(path, { method: body === undefined ? 'GET' : 'POST',
        headers: body === undefined ? headers : { 'Content-Type': 'application/json', ...headers },
        body: body === undefined ? undefined : JSON.stringify(body) });
      return { status: response.status, data: await response.json(), cache: response.headers.get('Cache-Control') };
    }, { path, body, headers });
    if (!responseSchema(result.data) || result.cache !== 'no-store') throw new Error('response schema/cache');
    return result;
  };
  const cookie = async c => (await c.cookies()).find(value => value.name === '__Host-backend-lab-session');
  const login = async (target, name, password) => {
    const flow = await request(target, '/api/v1/session', { schemaVersion: 1 }, { 'X-Auth-Flow': 'credential' });
    if (flow.status !== 200) throw new Error('preauth unavailable');
    return request(target, '/api/v1/auth/login', { schemaVersion: 1, expectedContextId: flow.data.sessionContextId, loginName: name, password }, { 'X-CSRF-TOKEN': flow.data.csrfToken });
  };
  phase = 'login-a';
  const a = await login(page, config.accountA, config.password), originalCookie = await cookie(context);
  check(a.status === 200 && a.data.state === 'ACTIVE', 'browser-A-active');
  check(originalCookie.secure && originalCookie.httpOnly && originalCookie.sameSite === 'Lax' && originalCookie.path === '/' && originalCookie.domain === 'localhost', 'host-secure-httponly-cookie');
  check(!(await page.evaluate(() => document.cookie)).includes('__Host-backend-lab-session'), 'browser-js-cannot-read-cookie');
  const tab = await context.newPage(); monitor(tab); await tab.goto(config.origin);
  const tabA = await request(tab, '/api/v1/session');
  check(tabA.data.sessionContextId === a.data.sessionContextId, 'second-tab-A-binding');
  phase = 'proof';
  const key = randomUUID(), password = config.purpose === 'PASSWORD_CHANGE' ? 'owned browser replacement password 🔐' : undefined;
  const minted = await request(page, '/api/v1/auth/reauth', { schemaVersion: 1, expectedContextId: a.data.sessionContextId,
    password: config.password, purpose: config.purpose, intentHash: intent(config.purpose, a.data.sessionContextId, key, password) },
  { 'X-CSRF-TOKEN': a.data.csrfToken, 'Idempotency-Key': key });
  check(minted.status === 200 && /^[A-Za-z0-9_-]{43}$/.test(minted.data.proof), 'natural-origin-sensitive-proof');
  const path = config.purpose === 'LOGOUT_ALL' ? '/api/v1/auth/logout-all' : '/api/v1/auth/password-change';
  const body = { schemaVersion: 1, expectedContextId: a.data.sessionContextId, reauthProof: minted.data.proof };
  if (password !== undefined) body.newPassword = password;
  const boundHeaders = { 'X-CSRF-TOKEN': a.data.csrfToken, 'Idempotency-Key': key };
  if (!requestSchema(body)) throw new Error('sensitive schema');
  phase = 'sensitive';
  await page.evaluate(({ path, body, headers }) => { window.sensitiveA = fetch(path, { method: 'POST', headers: {
    'Content-Type': 'application/json', ...headers, 'X-Lab-Hold': 'sensitive-A'
  }, body: JSON.stringify(body) }).then(async response => ({ status: response.status, data: await response.json() })); }, { path, body, headers: boundHeaders });
  const heldDeadline = Date.now() + 10000;
  while (!held.has('sensitive-A')) { if (Date.now() > heldDeadline) throw new Error('response hold timeout'); await new Promise(done => setTimeout(done, 25)); }
  const pending = held.get('sensitive-A');
  check(pending.status === 202 && pending.data.state === 'PENDING_FENCES' && pending.noCookie, 'pending-without-cookie');
  // Binding A phải giữ locked trong lúc còn thiếu ACK. Không xóa cookie hoặc
  // mint flow để lách gate; response202 được giữ cả sau server completion.
  signal({ stage: 'PENDING', operationId: pending.data.operationId, contextA: a.data.sessionContextId });
  phase = 'pending';
  if ((await line()).state !== 'PENDING_FENCES') throw new Error('pending DB oracle');
  const locked = await request(tab, '/api/v1/session');
  check(locked.data.state === 'REVOKING' && !locked.data.userId && !locked.data.csrfToken, 'pending-bootstrap-locked');
  const blockedFlow = await request(page, '/api/v1/session', { schemaVersion: 1 }, { 'X-Auth-Flow': 'credential' });
  check(blockedFlow.status === 409 && (await cookie(context)).value === originalCookie.value, 'pending-blocks-new-credential-flow');
  signal({ stage: 'COMPLETE', operationId: pending.data.operationId, contextA: a.data.sessionContextId });
  if ((await line()).state !== 'COMPLETED') throw new Error('dual durable ACK oracle');
  phase = 'login-b';
  const b = await login(page, config.accountB, config.password), cookieB = await cookie(context);
  check(b.status === 200 && b.data.userId !== a.data.userId && b.data.sessionContextId !== a.data.sessionContextId, 'browser-B-independent-owner');
  // Java chụp idleB sau login nhưng trước mọi poll/stale attempt phía dưới.
  signal({ stage: 'B_ACTIVE', operationId: pending.data.operationId, contextB: b.data.sessionContextId });
  if ((await line()).state !== 'B_ACTIVE') throw new Error('B idle snapshot oracle');
  phase = 'reorder'; pending.send(); held.delete('sensitive-A');
  const lateA = await page.evaluate(() => window.sensitiveA);
  check(lateA.status === 202 && (await cookie(context)).value === cookieB.value, 'late-A-no-cookie-B-survives');
  const stale = await request(tab, path, body, boundHeaders);
  check(stale.status === 409 && (await cookie(context)).value === cookieB.value, 'cross-cookie-retry-denied');
  const statusPath = '/api/v1/auth/operations/' + pending.data.operationId;
  check((await request(tab, statusPath, undefined, { 'X-Session-Context': a.data.sessionContextId })).status === 404, 'cross-cookie-status-denied');
  const current = await request(tab, '/api/v1/session');
  check(current.data.sessionContextId === b.data.sessionContextId, 'second-tab-observes-B');
  const wrongCsrf = await request(tab, '/api/v1/auth/reauth', { schemaVersion: 1, expectedContextId: b.data.sessionContextId,
    password: config.password, purpose: 'LEARNING_RESET', intentHash: 'a'.repeat(64) }, { 'X-CSRF-TOKEN': a.data.csrfToken, 'Idempotency-Key': randomUUID() });
  check(wrongCsrf.status === 403, 'old-CSRF-cannot-mint-under-B');
  await page.goto(config.origin + '/back'); await page.goBack(); await page.bringToFront();
  check((await request(page, '/api/v1/session')).data.sessionContextId === b.data.sessionContextId && (await cookie(context)).expires === cookieB.expires, 'Back-focus-no-cookie-renewal');
  phase = 'status';
  // Test-only browser context nhận đúng server-issued A cookie đã lưu ở RAM;
  // fetch tự gửi cookie/Origin, không giả Cookie/Origin bằng browser JS.
  const original = await browser.newContext({ ignoreHTTPSErrors: true }); await original.addCookies([originalCookie]);
  const originalPage = await original.newPage(); monitor(originalPage); await originalPage.goto(config.origin);
  const completed = await request(originalPage, statusPath, undefined, { 'X-Session-Context': a.data.sessionContextId });
  check(completed.status === 200 && completed.data.state === 'COMPLETED' && !completed.data.userId && !completed.data.csrfToken, 'initiating-status-completed-only');
  check((await request(originalPage, statusPath, undefined, { 'X-Session-Context': randomUUID() })).status === 404, 'wrong-context-status-denied');
  const retry = await request(originalPage, path, body, boundHeaders);
  check(retry.status === 200 && retry.data.state === 'COMPLETED', 'original-bound-sensitive-retry-completed');
  const revoked = await request(originalPage, '/api/v1/session');
  check(revoked.data.state !== 'ACTIVE' && !revoked.data.userId && !revoked.data.csrfToken && (await cookie(original)).value === originalCookie.value, 'old-A-cookie-never-resurrects');
  check((await request(page, '/api/v1/session')).data.sessionContextId === b.data.sessionContextId && (await cookie(context)).value === cookieB.value, 'completed-A-does-not-revoke-B');
  const foreign = await context.newPage(); monitor(foreign); await foreign.goto('https://127.0.0.1:17441');
  check((await request(foreign, '/api/v1/session', { schemaVersion: 1 }, { 'X-Auth-Flow': 'credential' })).status === 403, 'foreign-origin-denied');
  check(!headerFailure, 'no-status-proof-sensitive-cookie-emission');
  check(runtimeErrors === 0, 'no-browser-runtime-errors');
  await original.close(); await context.close();
  signal({ stage: 'RESULT', status: 'PASS', purpose: config.purpose, checks: checks.length, cases: checks,
    browser: 'installed Microsoft Edge / Chromium', version: browser.version(), origin: config.origin,
    scope: 'R04d owned canonical sensitive cookie/Origin/multitab/status fixture; not product FE or production edge' });
} catch {
  signal({ stage: 'RESULT', status: 'FAIL', case: phase, completedChecks: checks.length }); process.exitCode = 1;
} finally {
  for (const pending of held.values()) pending.send(); held.clear();
  if (browser) await browser.close();
  if (agent) agent.destroy();
  if (server) { server.closeAllConnections(); await new Promise(done => server.close(done)); }
  process.stdin.destroy();
}
