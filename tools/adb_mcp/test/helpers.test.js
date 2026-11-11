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
assert(/enum: \['qa', 'debug'\]/.test(src), 'install_build offers only the QA builds (Batch 521)');
assert.strictEqual(H.apkVariantOf('assembleQa'), 'qa');
assert(H.GRADLE_TASKS.includes('assembleQa'));
assert(/Tag v\$\{v\} already exists/.test(src) && /isn't newer than the latest published release/.test(src), 'publish guards present');
assert(/Not on main - nothing done/.test(src) && /Commit everything first/.test(src) && /not signed in/.test(src), 'old safeguards kept');
// FW1: isolated worktree builds - a fixed allow-list, git-ignored folder, fast-forward only, no signing keys copied.
const wts = src.match(/const WORKTREES = (\{[^\n]+\});/); assert(wts, 'WORKTREES allow-list present');
assert(/'Claude outputs', 'worktrees', 'fw1'/.test(wts[1]) && /branch: 'feature\/dual-welcome'/.test(wts[1]), 'fw1 -> feature/dual-welcome in Claude outputs');
const copy = src.match(/const WORKTREE_COPY = (\[[^\]]+\])/)[1];
assert(!/keystore|\.jks/i.test(copy), 'no signing keys copied into a worktree');
assert(/'merge', '--ff-only'/.test(src) && /has local changes - nothing done/.test(src), 'worktree update is fast-forward only and refuses local changes');
assert(/\^\[A-Za-z0-9_.-\]\+\\\\.bundle\$/.test(src) || /\.bundle\$\/\.test\(name\)/.test(src), 'bundle is a plain file name in backups');
console.log('adb_mcp helpers: OK');
