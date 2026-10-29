// R04a isolated TLS/browser fixture, NOT a production edge or application FE.
// Uses the existing locked Playwright dependency and installed Edge; no install,
// OS trust changes, persisted cookies, trace/video or secret response logging.
import { createRequire } from 'node:module';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import https from 'node:https';
import tls from 'node:tls';
import { X509Certificate, randomUUID } from 'node:crypto';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const require = createRequire(resolve(root, 'frontend/package.json'));
const { chromium } = require('@playwright/test');
const Ajv = require('ajv'); // existing locked dev transitive dependency; no install
const schemaValidator = new Ajv();
const requestSchema = schemaValidator.compile(JSON.parse(await readFile(resolve(root, 'contracts/http/identity/request.schema.json'), 'utf8')));
const responseSchema = schemaValidator.compile(JSON.parse(await readFile(resolve(root, 'contracts/http/identity/response.schema.json'), 'utf8')));
let browser, server, agent;
const held = new Map();
const cases = [];
let currentCase = 'setup';
function check(ok, name) { if (!ok) throw new Error(name); cases.push(name); }
async function waitHeld(label) {
  const deadline = Date.now() + 10000;
  while (!held.has(label)) { if (Date.now() > deadline) throw new Error('hold timeout'); await new Promise(r => setTimeout(r, 25)); }
}
function release(label) { const fn = held.get(label); if (!fn) throw new Error('missing hold'); held.delete(label); fn(); }
try {
  const chunks = []; for await (const chunk of process.stdin) chunks.push(chunk);
  const config = JSON.parse(Buffer.concat(chunks).toString('utf8'));
  const url = new URL(config.origin);
  if (url.hostname !== 'localhost' || url.protocol !== 'https:') throw new Error('owned loopback origin required');
  const pfx = await readFile(resolve(config.directory, 'edge.p12'));
  const ca = new X509Certificate(await readFile(resolve(config.directory, 'ca.crt'))).toString();
  agent = new https.Agent({ pfx, passphrase: config.storePassword, ca, rejectUnauthorized: true,
    checkServerIdentity(host, cert) {
      const bad = tls.checkServerIdentity(host, cert);
      return bad || (!cert.subjectaltname?.includes('URI:spiffe://backend-lab/lab/identity') ? new Error('identity SAN mismatch') : undefined);
    } });
  server = https.createServer({ pfx, passphrase: config.storePassword }, async (request, response) => {
    try {
      if (request.url === '/' || request.url === '/back') {
        response.writeHead(200, { 'Content-Type': 'text/html', 'Cache-Control': 'no-store' });
        response.end('<!doctype html><title>R04a browser fixture</title><p>Isolated session protocol test</p>'); return;
      }
      // This fixture does not accept arbitrary hosts/routes or identity headers.
      if (!request.url.startsWith('/api/v1/')) { response.writeHead(404); response.end(); return; }
      const headers = {};
      for (const name of ['content-type','cookie','origin','x-csrf-token','x-session-context','x-auth-flow','idempotency-key']) {
        if (request.headers[name] !== undefined) headers[name] = request.headers[name];
      }
      headers['X-Backend-Lab-Client-IP'] = '127.0.0.1'; // overwrite; never forward client-supplied value
      const upstream = https.request({ hostname: '127.0.0.1', servername: 'localhost', port: 8080,
        method: request.method, path: request.url, headers, agent, timeout: 8000 }, incoming => {
        const body = []; incoming.on('data', chunk => body.push(chunk));
        incoming.on('end', () => {
          const send = () => { response.writeHead(incoming.statusCode, incoming.headers); response.end(Buffer.concat(body)); };
          const label = request.headers['x-lab-hold'];
          if (label) { send.snapshot = { status: incoming.statusCode, headers: incoming.headers, body: Buffer.concat(body) }; held.set(label, send); } else send();
        });
      });
      upstream.on('timeout', () => upstream.destroy());
      upstream.on('error', () => { if (!response.headersSent) response.writeHead(503); response.end(); });
      request.pipe(upstream);
    } catch { if (!response.headersSent) response.writeHead(503); response.end(); }
  });
  await new Promise((done, fail) => { server.once('error', fail); server.listen(Number(url.port), '127.0.0.1', done); });
  browser = await chromium.launch({ channel: 'msedge', headless: true });
  const context = await browser.newContext({ ignoreHTTPSErrors: true }); // self-signed front-end fixture only; upstream verification remains on
  const page = await context.newPage();
  await page.goto(config.origin);
  async function request(path, body, headers = {}, target = page) {
    if (body !== undefined && !requestSchema(body)) throw new Error('fixture request schema');
    const result = await target.evaluate(async ({ path, body, headers }) => {
      const response = await fetch(path, { method: body === undefined ? 'GET' : 'POST',
        headers: body === undefined ? headers : { 'Content-Type': 'application/json', ...headers },
        body: body === undefined ? undefined : JSON.stringify(body) });
      return { status: response.status, data: await response.json(), cache: response.headers.get('Cache-Control') };
    }, { path, body, headers });
    if (!responseSchema(result.data)) throw new Error('provider response schema');
    return result;
  }
  const beginHeaders = { 'X-Auth-Flow': 'credential' };
  const loginBody = (name, flow) => ({ schemaVersion: 1, expectedContextId: flow.sessionContextId, loginName: name, password: config.password });
  const sessionCookie = async () => (await context.cookies()).find(cookie => cookie.name === '__Host-backend-lab-session');

  currentCase = 'origin-cookie';
  const anonymous = await request('/api/v1/session');
  check(anonymous.status === 200 && anonymous.data.state === 'ANONYMOUS' && !(await sessionCookie()), 'read-only bootstrap does not mint cookie');
  // The browser supplies Origin naturally on this POST; no forged forbidden header.
  // Two flows start before either cookie exists. The second response is held.
  await page.evaluate(() => { window.pendingB = fetch('/api/v1/session', { method: 'POST', headers: {
    'Content-Type': 'application/json', 'X-Auth-Flow': 'credential', 'X-Lab-Hold': 'preauth-b'
  }, body: JSON.stringify({ schemaVersion: 1 }) }).then(async r => ({ status: r.status, data: await r.json() })); });
  await waitHeld('preauth-b');
  const preA = await request('/api/v1/session', { schemaVersion: 1 }, beginHeaders);
  check(preA.status === 200 && preA.data.csrfToken && preA.cache === 'no-store', 'browser POST Origin and preauth CSRF');
  const beforeLogin = await sessionCookie();
  check(beforeLogin?.secure && beforeLogin.httpOnly && beforeLogin.sameSite === 'Lax' && beforeLogin.path === '/', 'Secure HttpOnly host cookie policy');
  check(!(await page.evaluate(() => document.cookie)).includes('__Host-backend-lab-session'), 'JavaScript cannot read session cookie');
  const activeA = await request('/api/v1/auth/login', loginBody(config.accountA, preA.data), { 'X-CSRF-TOKEN': preA.data.csrfToken });
  check(activeA.status === 200 && activeA.data.state === 'ACTIVE' && (await sessionCookie()).value !== beforeLogin.value, 'login cookie context rotation');

  currentCase = 'logout-a-after-b';
  await page.evaluate(({ contextId, csrf, key }) => {
    window.pendingLogoutA = fetch('/api/v1/auth/logout', { method: 'POST', headers: {
      'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf, 'Idempotency-Key': key, 'X-Lab-Hold': 'logout-a'
    }, body: JSON.stringify({ schemaVersion: 1, expectedContextId: contextId }) }).then(async r => ({ status: r.status, data: await r.json() }));
  }, { contextId: activeA.data.sessionContextId, csrf: activeA.data.csrfToken, key: randomUUID() });
  await waitHeld('logout-a'); // real server has already committed local deny
  release('preauth-b');
  const preB = await page.evaluate(() => window.pendingB);
  const afterLatePreauth = await request('/api/v1/session');
  check(preB.status === 200 && afterLatePreauth.data.state === 'ANONYMOUS' && afterLatePreauth.data.sessionContextId !== activeA.data.sessionContextId, 'late preauth changes binding, never preserves A authority');
  const badContext = await request('/api/v1/auth/login', loginBody(config.accountB, activeA.data), { 'X-CSRF-TOKEN': activeA.data.csrfToken });
  check(badContext.status === 409, 'old A context cannot consume B flow');
  const activeB = await request('/api/v1/auth/login', loginBody(config.accountB, preB.data), { 'X-CSRF-TOKEN': preB.data.csrfToken });
  check(activeB.status === 200 && activeB.data.state === 'ACTIVE' && activeB.data.userId !== activeA.data.userId, 'B login has independent owner context');
  const cookieB = await sessionCookie();
  release('logout-a');
  const logoutA = await page.evaluate(() => window.pendingLogoutA);
  check(logoutA.status === 202 && logoutA.data.state === 'PENDING_FENCES' && (await sessionCookie()).value === cookieB.value, 'late logout A cannot clear B cookie');
  const staleStatus = await request('/api/v1/auth/operations/' + logoutA.data.operationId, undefined, { 'X-Session-Context': activeA.data.sessionContextId });
  check(staleStatus.status === 404, 'A status with B cookie is not authority');

  currentCase = 'multi-tab-back-focus';
  const otherTab = await context.newPage(); await otherTab.goto(config.origin);
  const tabB = await request('/api/v1/session', undefined, {}, otherTab);
  check(tabB.status === 200 && tabB.data.sessionContextId === activeB.data.sessionContextId, 'another tab observes current B context');
  const staleMutation = await request('/api/v1/auth/logout', { schemaVersion: 1, expectedContextId: activeA.data.sessionContextId },
    { 'X-CSRF-TOKEN': activeA.data.csrfToken, 'Idempotency-Key': randomUUID() }, otherTab);
  check(staleMutation.status === 409 && (await sessionCookie()).value === cookieB.value, 'stale tab cannot revoke B');
  await page.goto(config.origin + '/back'); await page.goBack(); await page.bringToFront();
  const focusB = await request('/api/v1/session');
  check(focusB.status === 200 && focusB.data.sessionContextId === activeB.data.sessionContextId && (await sessionCookie()).expires === cookieB.expires, 'Back focus bootstrap has no sliding cookie');
  const wrongCsrf = await request('/api/v1/auth/logout', { schemaVersion: 1, expectedContextId: activeB.data.sessionContextId },
    { 'X-CSRF-TOKEN': preA.data.csrfToken, 'Idempotency-Key': randomUUID() });
  check(wrongCsrf.status === 403, 'old flow CSRF cannot mutate B');
  currentCase = 'browser-origin-negative';
  const foreign = await context.newPage(); await foreign.goto(config.origin.replace('localhost', '127.0.0.1'));
  const originDenied = await request('/api/v1/session', { schemaVersion: 1 }, beginHeaders, foreign);
  check(originDenied.status === 403, 'actual browser different Origin cannot mint a flow');
  const examples = JSON.parse(await readFile(resolve(root, 'contracts/http/identity/examples.json'), 'utf8'));
  check(requestSchema(examples.bootstrap) && requestSchema(examples.logout) && responseSchema(examples.pending) && responseSchema(examples.error), 'public examples validate against executable schemas');
  check(!requestSchema({ schemaVersion: 2 }) && !requestSchema({ schemaVersion: 1, owner: 'forged' }) && !requestSchema({ schemaVersion: 1, expectedContextId: 'not-a-uuid' }), 'schema rejects version owner and invalid binding');

  currentCase = 'late-login-set-cookie';
  const racingContext = await browser.newContext({ ignoreHTTPSErrors: true });
  const racingPage = await racingContext.newPage(); await racingPage.goto(config.origin);
  const racingFlow = await request('/api/v1/session', { schemaVersion: 1 }, beginHeaders, racingPage);
  await racingPage.evaluate(({ name, password, flow }) => {
    window.delayedLogin = fetch('/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json',
      'X-CSRF-TOKEN': flow.csrfToken, 'X-Lab-Hold': 'late-login' }, body: JSON.stringify({ schemaVersion: 1,
      expectedContextId: flow.sessionContextId, loginName: name, password }) }).then(async r => ({ status: r.status, data: await r.json() }));
  }, { name: config.accountA, password: config.password, flow: racingFlow.data });
  await waitHeld('late-login');
  // Fault mediator sees the actual committed login response in RAM, revokes its
  // handle through real mTLS HTTP, then delivers that original response late.
  // No fabricated cookie, mocked authority or database rewrite is involved.
  const snapshot = held.get('late-login').snapshot;
  const lateA = JSON.parse(snapshot.body.toString('utf8'));
  const lateCookie = snapshot.headers['set-cookie'][0].split(';')[0];
  const revokeBody = JSON.stringify({ schemaVersion: 1, expectedContextId: lateA.sessionContextId });
  const revoked = await new Promise((done, fail) => {
    const req = https.request({ hostname: '127.0.0.1', servername: 'localhost', port: 8080, path: '/api/v1/auth/logout', method: 'POST', agent,
      headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(revokeBody), Origin: config.origin,
        'X-Backend-Lab-Client-IP': '127.0.0.1', Cookie: lateCookie, 'X-CSRF-TOKEN': lateA.csrfToken, 'Idempotency-Key': randomUUID() } }, res => {
      res.resume(); res.on('end', () => done(res.statusCode));
    }); req.on('error', fail); req.end(revokeBody);
  });
  check(revoked === 202, 'held original login is canonically revoked before response delivery');
  const racingBFlow = await request('/api/v1/session', { schemaVersion: 1 }, beginHeaders, racingPage);
  const racingB = await request('/api/v1/auth/login', loginBody(config.accountB, racingBFlow.data), { 'X-CSRF-TOKEN': racingBFlow.data.csrfToken }, racingPage);
  check(racingB.status === 200, 'B rotates cookie before late original A login response');
  release('late-login'); await racingPage.evaluate(() => window.delayedLogin);
  const lateBootstrap = await request('/api/v1/session', undefined, {}, racingPage);
  check(lateBootstrap.data.state === 'REVOKING' && !lateBootstrap.data.userId && !lateBootstrap.data.csrfToken, 'late login Set-Cookie is applied but revoked A never regains authority');
  await racingContext.close();
  const version = browser.version();
  await context.close();
  process.stdout.write(JSON.stringify({ status: 'PASS', checks: cases.length, cases, browser: 'installed Microsoft Edge / Chromium', version,
    scope: 'R04a real browser session-cookie/context fixture; not production FE/edge/distributed completion' }));
} catch {
  // Never stringify errors containing request/cookie/password material.
  process.stderr.write(JSON.stringify({ status: 'FAIL', case: currentCase, completedChecks: cases.length })); process.exitCode = 1;
} finally {
  for (const fn of held.values()) fn(); held.clear();
  if (browser) await browser.close();
  if (server) { server.closeAllConnections(); await new Promise(done => server.close(done)); }
  if (agent) agent.destroy();
}
