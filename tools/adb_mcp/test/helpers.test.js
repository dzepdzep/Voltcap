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
const wts = src.match(/const WORKTREES = (\{[\s\S]*?\n?\});/); assert(wts, 'WORKTREES allow-list present'); // (multi-line since 12 Nov)
assert(/'Claude outputs', 'worktrees', 'fw1'/.test(wts[1]) && /branch: 'feature\/dual-welcome'/.test(wts[1]), 'fw1 -> feature/dual-welcome in Claude outputs');
const copy = src.match(/const WORKTREE_COPY = (\[[^\]]+\])/)[1];
assert(!/keystore|\.jks/i.test(copy), 'no signing keys copied into a worktree');
assert(/'merge', '--ff-only'/.test(src) && /has local changes - nothing done/.test(src), 'worktree update is fast-forward only and refuses local changes');
assert(/\^\[A-Za-z0-9_.-\]\+\\\\.bundle\$/.test(src) || /\.bundle\$\/\.test\(name\)/.test(src), 'bundle is a plain file name in backups');
assert(/'core.autocrlf=false', '-c', 'core.eol=lf', 'worktree', 'add'/.test(src), 'worktrees are checked out with LF like the main tree');
// Sprint 525: the branch push checks every value the APK check knows, in every pushed commit; only pnat / s3d / ci are pushable.
assert(/require\('\.\/push_guard'\)/.test(src) && /'rev-list', head, '--not', '--remotes=origin'/.test(src) && /pg\.secretFiles\(/.test(src), 'push secret check: push_guard, every new commit, secret files');
const pushable = src.match(/const PUSHABLE = (\{[^}]+\})/)[1];
assert(/pnat/.test(pushable) && /s3d/.test(pushable) && /ci:/.test(pushable) && !/int|main|fw1/.test(pushable), 'only the three development branches are pushable');
{ const body = src.slice(src.indexOf("case 'git_push_branch'"), src.indexOf("case 'git_commit_push'"));
  assert(/\['push', '--no-follow-tags', 'origin', head \+ ':refs\/heads\/' \+ branch\]/.test(body) && !/'--force'|'--tags'/.test(body), 'never forced, no tags'); }
console.log('adb_mcp helpers: OK');
