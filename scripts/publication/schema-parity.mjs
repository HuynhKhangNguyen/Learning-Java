import fs from 'node:fs';
import { createRequire } from 'node:module';

const require = createRequire(new URL('../../frontend/package.json', import.meta.url));
const Ajv = require('ajv');
const lock = JSON.parse(fs.readFileSync(new URL('../../frontend/package-lock.json', import.meta.url), 'utf8'));
if (require('ajv/package.json').version !== lock.packages['node_modules/ajv'].version) {
  throw new Error('Installed AJV does not match the existing frontend lockfile');
}
const [schemaPath, ...candidatePaths] = process.argv.slice(2);
if (!schemaPath || candidatePaths.length === 0) {
  throw new Error('Usage: schema-parity.mjs <schema> <candidate...>');
}
const schema = JSON.parse(fs.readFileSync(schemaPath, 'utf8'));
const validate = new Ajv({
  allErrors: true,
  jsonPointers: true,
  removeAdditional: false,
  useDefaults: false,
  coerceTypes: false,
}).compile(schema);

// Chỉ trả verdict bounded; candidate/private bytes không đi vào log/evidence.
const verdicts = candidatePaths.map(path => Boolean(validate(JSON.parse(fs.readFileSync(path, 'utf8')))));
process.stdout.write(JSON.stringify(verdicts));
