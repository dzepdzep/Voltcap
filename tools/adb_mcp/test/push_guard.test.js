// Sprint 525: tools/adb_mcp/push_guard.js (run: node tools/adb_mcp/test/push_guard.test.js). Synthetic files only.
'use strict';
const assert = require('assert'); const fs = require('fs'); const os = require('os'); const path = require('path');
const g = require('../push_guard');
const root = fs.mkdtempSync(path.join(os.tmpdir(), 'pg-'));
fs.writeFileSync(path.join(root, 'screenscraper.properties'), 'softName=Voltcap\r\ndevId=devid12\r\ndevPassword=pa55word\r\n');
fs.writeFileSync(path.join(root, 'igdb.properties'), '# c\nclientId = abcdefgh12\nclientSecret: s3cr3tvalue\n');
fs.writeFileSync(path.join(root, 'keystore.properties'), 'storeFile=rel.jks\nstorePassword=storepw1\nkeyPassword=storepw1\nkeyAlias=voltcap\n');
fs.mkdirSync(path.join(root, 'server', 'voltcap-api'), { recursive: true });
fs.writeFileSync(path.join(root, 'server', 'voltcap-api', '.dev.vars'), 'SS_PASSWORD="workerpw99"\nSHORT=abc\n');
fs.writeFileSync(path.join(root, 'secret_denylist.txt'), '# values\nlistedvalue1\npa55word\n\n');
const v = g.denyValues(root);
const vals = v.map((e) => e.value);
for (const x of ['devid12', 'pa55word', 's3cr3tvalue', 'abcdefgh12', 'storepw1', 'workerpw99', 'listedvalue1']) assert(vals.includes(x), 'missing ' + x);
assert(!vals.includes('Voltcap') && !vals.includes('abc') && !vals.includes('voltcap') && !vals.includes('rel.jks'), 'non-secrets stay out');
assert.strictEqual(vals.filter((x) => x === 'pa55word').length, 1, 'one entry per value');
assert.strictEqual(vals.filter((x) => x === 'storepw1').length, 1);
assert(v.every((e) => !e.from.includes(e.value)), 'a source name never holds the value');
assert.deepStrictEqual(g.missingSources(root), []);
assert.deepStrictEqual(g.missingSources(fs.mkdtempSync(path.join(os.tmpdir(), 'pg-'))).length, 3);
assert.deepStrictEqual(g.secretFiles(['app/src/A.kt', 'keystore.properties', 'app/release.jks', 'server/voltcap-api/.dev.vars', 'x/igdb.properties',
  'docs/SECRET_NOTES.md', 'tools/release/apk_secret_scan.py', 'app/google-services.json', 'a/b.pem']),
  ['keystore.properties', 'app/release.jks', 'server/voltcap-api/.dev.vars', 'x/igdb.properties', 'app/google-services.json', 'a/b.pem']);
console.log('push_guard: ' + v.length + ' values from ' + new Set(v.map((e) => e.from.split(' ')[0])).size + ' sources; all checks passed');
