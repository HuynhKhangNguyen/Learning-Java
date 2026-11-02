import fs from 'node:fs';
import { createRequire } from 'node:module';

const require = createRequire(new URL('../../frontend/package.json', import.meta.url));
const Ajv = require('ajv');
const lock = JSON.parse(
  fs.readFileSync(new URL('../../frontend/package-lock.json', import.meta.url), 'utf8'),
);
const installedVersion = require('ajv/package.json').version;
const lockedVersion = lock.packages['node_modules/ajv'].version;

if (installedVersion !== lockedVersion) {
  throw new Error('Installed AJV does not match the existing frontend lockfile');
}

const contractRoot = new URL('../../contracts/http/content/public/', import.meta.url);
const names = ['catalog', 'topic', 'unit'];
const ajv = new Ajv({
  allErrors: true,
  jsonPointers: true,
  removeAdditional: false,
  useDefaults: false,
  coerceTypes: false,
});

for (const name of names) {
  const schema = JSON.parse(
    fs.readFileSync(new URL(`${name}-response-v1.schema.json`, contractRoot), 'utf8'),
  );
  const example = JSON.parse(
    fs.readFileSync(new URL(`examples/${name}-response-v1.json`, contractRoot), 'utf8'),
  );
  const validate = ajv.compile(schema);
  if (!validate(example)) {
    throw new Error(`${name} public example does not match its schema`);
  }

  // Schema phải đóng ở root và object nghiệp vụ; field riêng tư chèn vào
  // bất kỳ lớp DTO chính nào cũng không được bị AJV bỏ qua hoặc tự xóa.
  const rootLeak = JSON.parse(JSON.stringify(example));
  rootLeak.principal = 'private-sentinel';
  if (validate(rootLeak)) {
    throw new Error(`${name} schema accepted a private root field`);
  }
  const businessLeak = JSON.parse(JSON.stringify(example));
  const business = name === 'catalog' ? businessLeak.roadmaps[0] : businessLeak[name];
  business.expected = 'private-sentinel';
  if (validate(businessLeak)) {
    throw new Error(`${name} schema accepted a private business field`);
  }
}

process.stdout.write(JSON.stringify({ status: 'PASS', schemas: names.length }));
