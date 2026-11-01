// Update 437: checks of server.js's pure helpers (run: node tools/adb_mcp/test/helpers.test.js). The helpers are read out of server.js
// itself, so the test exercises the shipped code without starting the MCP server.
const fs = require('fs'); const path = require('path'); const assert = require('assert');
const src = fs.readFileSync(path.join(__dirname, '..', 'server.js'), 'utf8');
const grab = (name) => { const m = src.match(new RegExp('function ' + name + '\\([^)]*\\) \\{.*\\}')); assert(m, name + ' not found'); return m[0]; };
const tasks = src.match(/const GRADLE_TASKS = (\[[^\]]+\])/)[1];
eval(grab('apkVariantOf') + grab('verParts') + grab('verCompare') + '; global.T = { apkVariantOf, verCompare, GRADLE_TASKS: ' + tasks + ' };');
const H = global.T;
assert(H.GRADLE_TASKS.includes('assembleCompat'));
assert(!H.GRADLE_TASKS.some((t) => /[\s;&|]/.test(t)), 'tasks are plain names, no shell');
assert.strictEqual(H.apkVariantOf('assembleCompat'), 'compat');
assert.strictEqual(H.apkVariantOf('assembleDebug'), 'debug');
assert.strictEqual(H.apkVariantOf('assembleRelease'), 'release');
assert(H.verCompare('0.6.65', 'v0.6.64') > 0);
assert.strictEqual(H.verCompare('0.6.65', '0.6.65'), 0);
assert(H.verCompare('0.6.64', '0.6.65') < 0);
assert(H.verCompare('0.6.100', '0.6.65') > 0);
assert(H.verCompare('0.7.0', '0.6.99') > 0);
assert(/enum: \['release', 'compat', 'debug'\]/.test(src), 'install_build offers compat');
assert(/Tag v\$\{v\} already exists/.test(src) && /isn't newer than the latest published release/.test(src), 'publish guards present');
assert(/Not on main - nothing done/.test(src) && /Commit everything first/.test(src) && /not signed in/.test(src), 'old safeguards kept');
console.log('adb_mcp helpers: OK');
