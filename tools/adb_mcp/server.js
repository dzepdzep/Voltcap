#!/usr/bin/env node
// Voltcap ADB bridge: a tiny MCP server (stdio, no dependencies) so Claude can see and drive the handheld over adb.
// Why it exists: the Claude desktop bridge strips any tool argument named "device", which breaks mobile-mcp.
// Nothing here is called "device"; the handheld is picked with "serial" (optional when only one is plugged in).
//
// Env (all optional):
//   ADB        full path to adb.exe (default: %LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe, else "adb" on PATH)
//   SHOTS_DIR  where screenshots are saved (default: <this folder>\..\..\screens)
//
// Update 216: a few FIXED build tools for the project's own workflow (no general shell):
//   sync_sources   exactly the two robocopy lines of HANDOVER rule 13 (src_copy -> app source, project_copy -> root)
//   gradle_start   runs gradlew.bat with ONE task from an allow-list, in the background (log in Claude outputs\builds)
//   gradle_status / gradle_stop
//   install_build  adb install -r of the newest built APK (update in place: app data kept; never -d, never uninstall)
//   git_status     read-only: branch, uncommitted files, last commits
//   git_commit_push (update 217, user-approved 18 Oct): git add -A, commit, push origin main — only on main, never
//                  --force, refuses while Gradle runs or when nothing changed.
//   publish_release (update 218, user-approved 18 Oct "after a larger batch"): runs tools\release\release.ps1
//                  <version> "<notes>" in the background (version bump, release build, commit + push, GitHub release
//                  that the in-app updater offers). Only with everything committed, gh signed in and a higher version.
// Nothing here uninstalls or deletes.
'use strict';
const { execFile, spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const guard = require('./guard');                                     // Batch 521: fail-closed protection of the real Voltcap

function findAdb() {
  if (process.env.ADB) return process.env.ADB;
  const local = process.env.LOCALAPPDATA ? path.join(process.env.LOCALAPPDATA, 'Android', 'Sdk', 'platform-tools', 'adb.exe') : null;
  if (local && fs.existsSync(local)) return local;
  return 'adb';
}
const ADB = findAdb();
const SHOTS = process.env.SHOTS_DIR || path.resolve(__dirname, '..', '..', 'screens');
const ROOT = path.resolve(__dirname, '..', '..');                       // the ShelfLauncher project folder
const BUILDS = path.join(ROOT, 'Claude outputs', 'builds');             // git-ignored
// (Update 437, owner-authorized: assembleCompat — the compat variant release.ps1 also builds; still a fixed list, no free commands.)
// (Batch 521: assembleQa - the isolated QA build type, debug-key signed.)
const GRADLE_TASKS = ['assembleQa', 'assembleRelease', 'assembleCompat', 'assembleDebug', ':app:compileReleaseKotlin', ':app:compileDebugKotlin', 'lintRelease', 'testDebugUnitTest'];
let job = null;                                                          // the one Gradle run at a time

function buildEnv() {
  const env = Object.assign({}, process.env);
  if (!env.JAVA_HOME) {
    // Gradle needs a JDK: Android Studio's own one when JAVA_HOME isn't set for this app.
    const cands = ['C:\\Program Files\\Android\\Android Studio\\jbr', path.join(process.env.LOCALAPPDATA || '', 'Programs', 'Android Studio', 'jbr')];
    for (const c of cands) if (fs.existsSync(path.join(c, 'bin', 'java.exe'))) { env.JAVA_HOME = c; break; }
  }
  return env;
}
function runCapture(cmd, args, opts = {}) {
  return new Promise((resolve) => {
    const p = spawn(cmd, args, { cwd: opts.cwd || ROOT, env: opts.env || process.env, windowsHide: true });
    let out = '';
    p.stdout.on('data', (d) => { out += d; }); p.stderr.on('data', (d) => { out += d; });
    p.on('error', (e) => resolve({ code: -1, out: out + '\n' + e.message }));
    p.on('close', (code) => resolve({ code, out }));
  });
}
const tail = (t, n) => t.split(/\r?\n/).filter((l) => l.trim() !== '').slice(-n).join('\n');
function newestApk(variant) {
  const dir = path.join(ROOT, 'app', 'build', 'outputs', 'apk', variant);
  if (!fs.existsSync(dir)) return null;
  const apks = fs.readdirSync(dir).filter((f) => f.endsWith('.apk')).map((f) => path.join(dir, f));
  apks.sort((a, b) => fs.statSync(b).mtimeMs - fs.statSync(a).mtimeMs);
  return apks[0] || null;
}
/** (Update 437) Which outputs folder an assemble task fills: assembleDebug → debug, assembleCompat → compat, else release. */
function apkVariantOf(task) { return task === 'assembleDebug' ? 'debug' : (task === 'assembleCompat' ? 'compat' : (task === 'assembleQa' ? 'qa' : 'release')); }
/** (Update 437) "0.6.65" → [0, 6, 65]; compare(a, b) < 0 when a is older. */
function verParts(x) { return String(x).replace(/^v/, '').split('.').map((n) => parseInt(n, 10) || 0); }
function verCompare(a, b) { const x = verParts(a), y = verParts(b); for (let i = 0; i < 3; i++) { if ((x[i] || 0) !== (y[i] || 0)) return (x[i] || 0) - (y[i] || 0); } return 0; }
function jobStatus(lines) {
  if (!job) return 'No Gradle run yet.';
  const log = fs.existsSync(job.log) ? fs.readFileSync(job.log, 'utf8') : '';
  const secs = Math.round(((job.ended || Date.now()) - job.started) / 1000);
  const state = job.code === null ? 'RUNNING' : (job.code === 0 ? 'SUCCESS' : 'FAILED (exit ' + job.code + ')');
  const errs = log.split(/\r?\n/).filter((l) => /^e: |^w: .*(error)|FAILURE:|What went wrong|> Task .*FAILED|BUILD (SUCCESSFUL|FAILED)/.test(l)).slice(0, 40);
  let s = `Gradle ${job.task}: ${state} after ${secs} s\nLog: ${job.log}`;
  if (errs.length) s += '\n--- key lines ---\n' + errs.join('\n');
  s += '\n--- last lines ---\n' + tail(log, lines || 30);
  if (job.code === 0 && /^assemble/.test(job.task)) { const v = apkVariantOf(job.task); const a = newestApk(v); if (a) s += `\nAPK: ${a} (${new Date(fs.statSync(a).mtimeMs).toISOString()})`; }
  return s;
}

function adb(args, serial, opts = {}) {
  const full = (serial ? ['-s', serial] : []).concat(args);
  return new Promise((resolve, reject) => {
    execFile(ADB, full, { encoding: opts.binary ? 'buffer' : 'utf8', maxBuffer: 64 * 1024 * 1024, timeout: opts.timeout || 30000, windowsHide: true },
      (err, stdout, stderr) => {
        if (err) reject(new Error((stderr && stderr.toString()) || err.message));
        else resolve(stdout);
      });
  });
}

// ---------- PNG decode / downscale / encode (screencap gives 8-bit RGBA or RGB, not interlaced) ----------
const CRC_TABLE = (() => { const t = new Int32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c; } return t; })();
function crc32(buf) { let c = -1; for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8); return (c ^ -1) >>> 0; }

function decodePng(buf) {
  let pos = 8, w = 0, h = 0, depth = 0, ctype = 0, inter = 0; const idat = [];
  while (pos < buf.length) {
    const len = buf.readUInt32BE(pos); const type = buf.toString('ascii', pos + 4, pos + 8); const data = buf.subarray(pos + 8, pos + 8 + len);
    if (type === 'IHDR') { w = data.readUInt32BE(0); h = data.readUInt32BE(4); depth = data[8]; ctype = data[9]; inter = data[12]; }
    else if (type === 'IDAT') idat.push(data);
    else if (type === 'IEND') break;
    pos += 12 + len;
  }
  if (depth !== 8 || (ctype !== 6 && ctype !== 2) || inter !== 0) throw new Error(`unsupported PNG (depth ${depth}, type ${ctype}, interlace ${inter})`);
  const bpp = ctype === 6 ? 4 : 3, stride = w * bpp;
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const out = Buffer.alloc(w * h * 4);
  let prev = Buffer.alloc(stride), cur = Buffer.alloc(stride);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (stride + 1)]; const line = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    for (let i = 0; i < stride; i++) {
      const a = i >= bpp ? cur[i - bpp] : 0, b = prev[i], c = i >= bpp ? prev[i - bpp] : 0;
      let v = line[i];
      if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1;
      else if (f === 4) { const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
      cur[i] = v & 0xff;
    }
    for (let x = 0; x < w; x++) {
      const o = (y * w + x) * 4, s = x * bpp;
      out[o] = cur[s]; out[o + 1] = cur[s + 1]; out[o + 2] = cur[s + 2]; out[o + 3] = bpp === 4 ? cur[s + 3] : 255;
    }
    const t = prev; prev = cur; cur = t;
  }
  return { w, h, rgba: out };
}

function downscale(img, k) {
  if (k <= 1) return img;
  const w = Math.floor(img.w / k), h = Math.floor(img.h / k), out = Buffer.alloc(w * h * 4), n = k * k;
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let r = 0, g = 0, b = 0, a = 0;
    for (let yy = 0; yy < k; yy++) for (let xx = 0; xx < k; xx++) {
      const s = ((y * k + yy) * img.w + (x * k + xx)) * 4; r += img.rgba[s]; g += img.rgba[s + 1]; b += img.rgba[s + 2]; a += img.rgba[s + 3];
    }
    const o = (y * w + x) * 4; out[o] = r / n; out[o + 1] = g / n; out[o + 2] = b / n; out[o + 3] = a / n;
  }
  return { w, h, rgba: out };
}

function encodePng(img) {
  const stride = img.w * 3, raw = Buffer.alloc((stride + 1) * img.h);
  for (let y = 0; y < img.h; y++) {
    raw[y * (stride + 1)] = 0;
    for (let x = 0; x < img.w; x++) { const s = (y * img.w + x) * 4, o = y * (stride + 1) + 1 + x * 3; raw[o] = img.rgba[s]; raw[o + 1] = img.rgba[s + 1]; raw[o + 2] = img.rgba[s + 2]; }
  }
  const chunk = (type, data) => { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type, 'ascii'), data]); const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td)); return Buffer.concat([len, td, crc]); };
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(img.w, 0); ihdr.writeUInt32BE(img.h, 4); ihdr[8] = 8; ihdr[9] = 2; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

// ---------- tools ----------
const SERIAL = { type: 'string', description: 'adb serial of the handheld (see list_handhelds). Leave out when only one is plugged in.' };
const KEYS = {
  A: 'KEYCODE_BUTTON_A', B: 'KEYCODE_BUTTON_B', X: 'KEYCODE_BUTTON_X', Y: 'KEYCODE_BUTTON_Y',
  L1: 'KEYCODE_BUTTON_L1', R1: 'KEYCODE_BUTTON_R1', L2: 'KEYCODE_BUTTON_L2', R2: 'KEYCODE_BUTTON_R2',
  START: 'KEYCODE_BUTTON_START', SELECT: 'KEYCODE_BUTTON_SELECT',
  UP: 'KEYCODE_DPAD_UP', DOWN: 'KEYCODE_DPAD_DOWN', LEFT: 'KEYCODE_DPAD_LEFT', RIGHT: 'KEYCODE_DPAD_RIGHT', CENTER: 'KEYCODE_DPAD_CENTER',
  BACK: 'KEYCODE_BACK', HOME: 'KEYCODE_HOME', MENU: 'KEYCODE_MENU', ENTER: 'KEYCODE_ENTER',
  VOLUME_UP: 'KEYCODE_VOLUME_UP', VOLUME_DOWN: 'KEYCODE_VOLUME_DOWN', POWER: 'KEYCODE_POWER'
};
const TOOLS = [
  { name: 'list_handhelds', description: 'List Android handhelds connected over adb (serial, state, model).', inputSchema: { type: 'object', properties: {} } },
  { name: 'screenshot', description: 'Take a screenshot of the handheld. Saves the full-size PNG in SHOTS_DIR and returns its path; with inline=true also returns a smaller copy as an image.',
    inputSchema: { type: 'object', properties: { serial: SERIAL, name: { type: 'string', description: 'File name without extension (default shot_<time>)' }, display: { type: 'integer', description: 'Display id for a second screen (from list_displays); leave out for the main screen' }, inline: { type: 'boolean', description: 'Also return the picture inline (default false)' }, scale: { type: 'integer', description: 'Inline copy: shrink by this whole factor (default 2)' } } } },
  { name: 'list_displays', description: 'List the handheld\'s screens (for a second screen like the AYN Thor\'s).', inputSchema: { type: 'object', properties: { serial: SERIAL } } },
  { name: 'tap', description: 'Tap at x,y in screen pixels.', inputSchema: { type: 'object', properties: { serial: SERIAL, x: { type: 'number' }, y: { type: 'number' } }, required: ['x', 'y'] } },
  { name: 'swipe', description: 'Swipe from x1,y1 to x2,y2 in screen pixels over ms milliseconds (default 300). Same start and end with a long ms = long press.',
    inputSchema: { type: 'object', properties: { serial: SERIAL, x1: { type: 'number' }, y1: { type: 'number' }, x2: { type: 'number' }, y2: { type: 'number' }, ms: { type: 'integer' } }, required: ['x1', 'y1', 'x2', 'y2'] } },
  { name: 'press', description: 'Press controller / system buttons in order: ' + Object.keys(KEYS).join(', ') + ', or any KEYCODE_* name or number. "times" repeats each.',
    inputSchema: { type: 'object', properties: { serial: SERIAL, keys: { type: 'array', items: { type: 'string' } }, times: { type: 'integer' }, gap_ms: { type: 'integer', description: 'Pause between presses (default 150)' } }, required: ['keys'] } },
  { name: 'type_text', description: 'Type text into the focused field.', inputSchema: { type: 'object', properties: { serial: SERIAL, text: { type: 'string' } }, required: ['text'] } },
  { name: 'screen_size', description: 'Screen size and density.', inputSchema: { type: 'object', properties: { serial: SERIAL } } },
  { name: 'foreground_app', description: 'Which app / activity is in front.', inputSchema: { type: 'object', properties: { serial: SERIAL } } },
  { name: 'launch_app', description: 'Start an app by package name (default: the isolated QA app com.shelf.launcher.qa; the real Voltcap is refused).', inputSchema: { type: 'object', properties: { serial: SERIAL, package: { type: 'string' } } } },
  { name: 'logcat', description: 'Recent log lines (default 300), optionally only lines containing "grep".', inputSchema: { type: 'object', properties: { serial: SERIAL, lines: { type: 'integer' }, grep: { type: 'string' }, crashes_only: { type: 'boolean' } } } },
  { name: 'shell', description: 'Run an adb shell command on the handheld and return its output.', inputSchema: { type: 'object', properties: { serial: SERIAL, command: { type: 'string' } }, required: ['command'] } },
  // (Update 144: installing a test build and moving files, without anyone at the PC.)
  { name: 'install_apk', description: 'Install a QA APK file from this PC on one handheld (adb install -r). Same fail-closed checks as install_build: only com.shelf.launcher.qa, not release-signed, USB handheld in qa_devices.json; "downgrade" only for the QA app.',
    inputSchema: { type: 'object', properties: { serial: { type: 'string', description: 'USB serial - required' }, path: { type: 'string', description: 'Full path of the .apk on this PC' }, downgrade: { type: 'boolean' } }, required: ['serial', 'path'] } },
  { name: 'push', description: 'Copy a file from this PC to the handheld.', inputSchema: { type: 'object', properties: { serial: SERIAL, pc_path: { type: 'string' }, handheld_path: { type: 'string' } }, required: ['pc_path', 'handheld_path'] } },
  { name: 'pull', description: 'Copy a file from the handheld to this PC.', inputSchema: { type: 'object', properties: { serial: SERIAL, handheld_path: { type: 'string' }, pc_path: { type: 'string' } }, required: ['handheld_path', 'pc_path'] } },
  { name: 'sync_sources', description: 'The project\'s own copy step (HANDOVER rule 13): robocopy src_copy into app\\src\\main\\java\\com\\shelf\\launcher, then project_copy over the project root (/E /NJH /NP). Nothing is deleted.', inputSchema: { type: 'object', properties: {} } },
  { name: 'gradle_start', description: 'Start gradlew.bat with one allowed task in the project folder, in the background: ' + GRADLE_TASKS.join(', ') + '. Check with gradle_status.',
    inputSchema: { type: 'object', properties: { task: { type: 'string', enum: GRADLE_TASKS } }, required: ['task'] } },
  { name: 'gradle_status', description: 'State of the current/last Gradle run: running/success/failed, compile errors, last log lines, built APK.', inputSchema: { type: 'object', properties: { lines: { type: 'integer', description: 'Last log lines to show (default 30)' } } } },
  { name: 'gradle_stop', description: 'Stop the running Gradle build.', inputSchema: { type: 'object', properties: {} } },
  { name: 'install_build', description: 'Install the newest built ISOLATED QA app (com.shelf.launcher.qa; variant qa = assembleQa, or debug = assembleDebug, which is the QA app by default) on ONE handheld named by its USB serial, with adb install -r. Fail-closed (Batch 521): the APK\'s own manifest must say com.shelf.launcher.qa and it must not be signed with the release key; the handheld must be USB, not a TV, and listed in qa_devices.json; the real Voltcap is checked before and after. Production builds are refused here (install_production).',
    inputSchema: { type: 'object', properties: { serial: { type: 'string', description: 'USB serial of the handheld (list_handhelds) - required' }, variant: { type: 'string', enum: ['qa', 'debug'] } }, required: ['serial'] } },
  { name: 'uninstall_qa', description: 'Remove ONLY the isolated QA app (com.shelf.launcher.qa) and its test data from one USB handheld - needed once when the QA signing key changes (Batch 521). The real Voltcap cannot be named.',
    inputSchema: { type: 'object', properties: { serial: { type: 'string', description: 'USB serial - required' } }, required: ['serial'] } },
  { name: 'install_production', description: 'Owner-approved upgrade of the REAL Voltcap only: adb install -r of a published release APK whose sha256 is pinned in tools/adb_mcp/production_pins.json (never -d, never uninstall). Anything else is refused.',
    inputSchema: { type: 'object', properties: { serial: { type: 'string', description: 'USB serial - required' }, path: { type: 'string', description: 'Full path of the published .apk on this PC' }, sha256: { type: 'string', description: 'Expected sha256 (must match the file and a pin)' } }, required: ['serial', 'path', 'sha256'] } },
  { name: 'git_status', description: 'Read-only: current branch, uncommitted files and the last commits of the project.', inputSchema: { type: 'object', properties: {} } },
  { name: 'publish_release', description: 'Publish a Voltcap release with tools\\release\\release.ps1: bumps versionName/versionCode (or keeps an already staged version and its code), builds the release + compat APKs, commits + pushes, and creates the GitHub release the in-app updater offers. Needs everything committed first, and a version higher than the current one, or equal to the staged one when it is newer than the latest published release and its tag does not exist yet. Runs in the background: check with gradle_status.',
    inputSchema: { type: 'object', properties: { version: { type: 'string', description: 'e.g. 0.6.9' }, notes: { type: 'string', description: 'Release notes (plain text)' } }, required: ['version', 'notes'] } },
  { name: 'git_commit_push', description: 'Commit everything (git add -A) with the given message and push to origin main. Only on branch main, never force; refuses while Gradle runs or when nothing changed.',
    inputSchema: { type: 'object', properties: { message: { type: 'string', description: 'Commit title, e.g. "Update 217: ..."' }, body: { type: 'string', description: 'Optional second paragraph (e.g. Co-Authored-By lines)' } }, required: ['message'] } }
];


// Batch 521: the ONLY way an APK reaches a handheld from these tools (install_build / install_apk). Fail-closed: anything the
// guard can't positively confirm as the isolated QA app on a known USB handheld is refused before adb install runs.
async function qaInstall(serialArg, apk, downgrade) {
  const t = await guard.targetProblems(serialArg, adb, guard.loadDevices());
  if (t.problems.length) throw new Error('Refused (nothing installed): ' + t.problems.join('; '));
  if (!apk || !fs.existsSync(apk)) throw new Error('No such APK: ' + apk);
  const id = guard.apkIdentity(apk);
  const p = guard.qaApkProblems(id);
  if (p.length) throw new Error('Refused (nothing installed): ' + path.basename(apk) + ': ' + p.join('; '));
  const before = await guard.productionState(serialArg, adb);
  let dm = null;
  try {
    const meta = JSON.parse(fs.readFileSync(path.join(path.dirname(apk), 'output-metadata.json'), 'utf8'));
    const sdk = parseInt((await adb(['shell', 'getprop', 'ro.build.version.sdk'], serialArg)).trim(), 10) || 0;
    const bp = (meta.baselineProfiles || []).find((b) => sdk >= b.minApi && sdk <= b.maxApi);
    const f = bp && bp.baselineProfiles && bp.baselineProfiles[0] ? path.join(path.dirname(apk), bp.baselineProfiles[0]) : null;
    if (f && fs.existsSync(f) && /-(qa|debug)\.dm$/.test(f)) dm = f;
  } catch (e) { dm = null; }
  const flags = downgrade ? ['-r', '-d'] : ['-r'];
  let out;
  if (dm) {
    try { out = '(with profile ' + path.basename(dm) + ')\n' + await adb(['install-multiple', ...flags, apk, dm], serialArg, { timeout: 300000 }); }
    catch (e) { dm = null; }
  }
  if (!dm) out = await adb(['install', ...flags, apk], serialArg, { timeout: 300000 });
  const after = await guard.productionState(serialArg, adb);
  const same = guard.sameProduction(before, after);
  return text(`${apk} (${new Date(fs.statSync(apk).mtimeMs).toISOString()})\n${id.package} ${id.versionName} signer ${id.signers[0].slice(0, 16)}... on ${t.model} ${serialArg}\n` + out.trim() +
    `\nreal Voltcap ${same ? 'unchanged' : 'CHANGED - ALARM'}: versionCode ${after.versionCode || '-'}, last update ${after.lastUpdateTime || '-'}`);
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const text = (t) => ({ content: [{ type: 'text', text: t }] });

// git / robocopy steps one after another (two at once collide on git's index lock).
let serial = Promise.resolve();
function queued(fn) { const run = serial.then(fn, fn); serial = run.catch(() => {}); return run; }

async function call(name, a) {
  if (name === 'git_commit_push' || name === 'sync_sources' || name === 'git_status' || name === 'publish_release') return queued(() => call2(name, a));
  return call2(name, a);
}

async function call2(name, a) {
  const s = a.serial;
  switch (name) {
    case 'list_handhelds': {
      const out = await adb(['devices', '-l']);
      return text(out.trim() || 'No handheld found. Plug it in with USB debugging on.');
    }
    case 'screenshot': {
      const args = ['exec-out', 'screencap', '-p'];
      if (a.display !== undefined) args.splice(2, 0, '-d', String(a.display));
      const png = await adb(args, s, { binary: true, timeout: 20000 });
      if (!png || png.length < 100 || png[0] !== 137) throw new Error('screencap returned no PNG');
      fs.mkdirSync(SHOTS, { recursive: true });
      const file = path.join(SHOTS, (a.name || ('shot_' + new Date().toISOString().replace(/[:.]/g, '-'))) + '.png');
      fs.writeFileSync(file, png);
      const img = decodePng(png);
      const res = { content: [{ type: 'text', text: `Saved ${file} (${img.w}x${img.h}, ${png.length} bytes)` }] };
      if (a.inline) {
        const small = encodePng(downscale(img, Math.max(1, a.scale || 2)));
        res.content.push({ type: 'image', data: small.toString('base64'), mimeType: 'image/png' });
      }
      return res;
    }
    case 'list_displays': return text((await adb(['shell', 'dumpsys', 'SurfaceFlinger', '--display-id'], s)).trim());
    case 'tap': await adb(['shell', 'input', 'tap', String(Math.round(a.x)), String(Math.round(a.y))], s); return text(`Tapped ${a.x},${a.y}`);
    case 'swipe': await adb(['shell', 'input', 'swipe', ...[a.x1, a.y1, a.x2, a.y2].map((v) => String(Math.round(v))), String(a.ms || 300)], s); return text('Swiped');
    case 'press': {
      const times = Math.max(1, a.times || 1), gap = a.gap_ms === undefined ? 150 : a.gap_ms;
      for (const k of a.keys) {
        const code = KEYS[String(k).toUpperCase()] || String(k);
        for (let i = 0; i < times; i++) { await adb(['shell', 'input', 'keyevent', code], s); if (gap) await sleep(gap); }
      }
      return text(`Pressed ${a.keys.join(', ')}${times > 1 ? ' ×' + times : ''}`);
    }
    case 'type_text': await adb(['shell', 'input', 'text', String(a.text).replace(/ /g, '%s').replace(/(["'`\\$&|;<>()])/g, '\\$1')], s); return text('Typed');
    case 'screen_size': return text((await adb(['shell', 'wm', 'size'], s)).trim() + '\n' + (await adb(['shell', 'wm', 'density'], s)).trim());
    case 'foreground_app': {
      const out = await adb(['shell', 'dumpsys', 'activity', 'activities'], s);
      const line = out.split('\n').find((l) => /topResumedActivity|mResumedActivity/.test(l));
      return text(line ? line.trim() : 'Unknown');
    }
    case 'launch_app': {
      const pkg = a.package || guard.QA_PACKAGE;
      if (pkg === guard.PRODUCTION_PACKAGE) throw new Error('Refused (Batch 521): launching the real Voltcap from the tools is not allowed - use ' + guard.QA_PACKAGE + '.');
      return text((await adb(['shell', 'monkey', '-p', pkg, '-c', 'android.intent.category.LAUNCHER', '1'], s)).trim());
    }
    case 'logcat': {
      const args = ['logcat', '-d', '-t', String(a.lines || 300)];
      if (a.crashes_only) args.push('-b', 'crash');
      let out = await adb(args, s, { timeout: 20000 });
      if (a.grep) out = out.split('\n').filter((l) => l.includes(a.grep)).join('\n');
      return text(out.trim() || '(empty)');
    }
    case 'shell': {
      const why = guard.shellProblems(a.command);
      if (why.length) throw new Error('Refused (Batch 521 guard): this command ' + why.join('; ') + '. Nothing was run.');
      return text((await adb(['shell', a.command], s, { timeout: 60000 })).trim() || '(no output)');
    }
    case 'install_apk': return qaInstall(a.serial, a.path, !!a.downgrade);
    case 'push': {
      if (guard.shellProblems('push ' + a.handheld_path).length || /\/(RetroArch|Voltcap)\b/i.test(String(a.handheld_path)))
        throw new Error('Refused (Batch 521 guard): ' + a.handheld_path + ' belongs to the real Voltcap or your saves.');
      if (/\.(sh|txt)$/i.test(String(a.pc_path)) && fs.existsSync(a.pc_path) && fs.statSync(a.pc_path).size < 2e6) {
        const bad = guard.scriptProblems(fs.readFileSync(a.pc_path, 'utf8'));
        if (bad.length) throw new Error('Refused (Batch 521 guard): ' + path.basename(a.pc_path) + ' would act on the real Voltcap / system:\n' + bad.slice(0, 12).join('\n'));
      }
      return text((await adb(['push', a.pc_path, a.handheld_path], s, { timeout: 180000 })).trim());
    }
    case 'pull': {
      fs.mkdirSync(path.dirname(a.pc_path), { recursive: true });
      return text((await adb(['pull', a.handheld_path, a.pc_path], s, { timeout: 180000 })).trim());
    }
    case 'sync_sources': {
      if (job && job.code === null) throw new Error('A Gradle run is in progress - wait for it (gradle_status) before copying sources.');
      const src = path.join(ROOT, 'src_copy'), dst = path.join(ROOT, 'app', 'src', 'main', 'java', 'com', 'shelf', 'launcher'), prj = path.join(ROOT, 'project_copy');
      const out = [];
      for (const [from, to] of [[src, dst], [prj, ROOT]]) {
        if (!fs.existsSync(from)) { out.push(`(no ${from})`); continue; }
        const r = await runCapture('robocopy', [from, to, '/E', '/NJH', '/NP']);
        // robocopy: 0-7 = success (1 = files copied), 8+ = failure
        out.push(`robocopy ${path.basename(from)} -> exit ${r.code}${r.code >= 8 ? ' FAILED' : ''}\n` + tail(r.out, 25));
        if (r.code >= 8) break;
      }
      return text(out.join('\n\n'));
    }
    case 'gradle_start': {
      if (!GRADLE_TASKS.includes(a.task)) throw new Error('Task not allowed: ' + a.task);
      if (job && job.code === null) throw new Error('Gradle is already running ' + job.task + ' (gradle_status / gradle_stop).');
      fs.mkdirSync(BUILDS, { recursive: true });
      const log = path.join(BUILDS, 'gradle_' + a.task.replace(/[^A-Za-z]/g, '') + '_' + new Date().toISOString().replace(/[:.]/g, '-') + '.log');
      const ws = fs.createWriteStream(log);
      const p = spawn(process.env.ComSpec || 'cmd.exe', ['/d', '/c', 'gradlew.bat', a.task, '--console=plain'], { cwd: ROOT, env: buildEnv(), windowsHide: true });
      job = { task: a.task, started: Date.now(), ended: null, code: null, log, pid: p.pid };
      const j = job;
      p.stdout.pipe(ws, { end: false }); p.stderr.pipe(ws, { end: false });
      p.on('error', (e) => { ws.write('\n' + e.message + '\n'); });
      p.on('close', (code) => { j.code = code === null ? -1 : code; j.ended = Date.now(); ws.end(); });
      return text(`Started gradlew ${a.task} (pid ${p.pid}). Log: ${log}\nCheck with gradle_status.`);
    }
    case 'gradle_status': return text(jobStatus(a.lines));
    case 'gradle_stop': {
      if (!job || job.code !== null) return text('No Gradle run in progress.');
      await runCapture('taskkill', ['/PID', String(job.pid), '/T', '/F']);
      return text('Stopped ' + job.task + '.');
    }
    case 'install_build': {
      const variant = a.variant || 'qa';
      if (!['qa', 'debug'].includes(variant)) throw new Error('Refused (Batch 521): install_build only installs the isolated QA app (variant qa or debug). Production: install_production.');
      if (job && job.code === null) throw new Error('Gradle is still running - wait for it first.');
      const apk = newestApk(variant);
      if (!apk) throw new Error('No built ' + variant + ' APK in app\\build\\outputs\\apk\\' + variant);
      return qaInstall(a.serial, apk, false);
    }
    case 'uninstall_qa': {
      const t = await guard.targetProblems(a.serial, adb, guard.loadDevices());
      if (t.problems.length) throw new Error('Refused: ' + t.problems.join('; '));
      const before = await guard.productionState(a.serial, adb);
      const out = await adb(['uninstall', guard.QA_PACKAGE], a.serial, { timeout: 60000 }).catch((e) => e.message);
      const after = await guard.productionState(a.serial, adb);
      return text(`uninstall ${guard.QA_PACKAGE} on ${t.model}: ${String(out).trim()}\nreal Voltcap ${guard.sameProduction(before, after) ? 'unchanged' : 'CHANGED - ALARM'}`);
    }
    case 'install_production': {
      const t = await guard.targetProblems(a.serial, adb, guard.loadDevices());
      if (t.problems.length) throw new Error('Refused: ' + t.problems.join('; '));
      if (!a.path || !fs.existsSync(a.path)) throw new Error('No such file: ' + a.path);
      const id = guard.apkIdentity(a.path);
      const p = guard.productionApkProblems(id, guard.loadPins());
      if (String(a.sha256 || '').toLowerCase() !== id.sha256) p.push('sha256 does not match the one given');
      if (p.length) throw new Error('Refused: ' + p.join('; '));
      const before = await guard.productionState(a.serial, adb);
      const out = await adb(['install', '-r', a.path], a.serial, { timeout: 300000 });
      const after = await guard.productionState(a.serial, adb);
      return text(`${path.basename(a.path)} ${id.versionName} (${id.versionCode}) sha256 ${id.sha256}\n${out.trim()}\nreal Voltcap: ${before.versionCode} -> ${after.versionCode}`);
    }
    case 'git_status': {
      const b = await runCapture('git', ['-C', ROOT, 'rev-parse', '--abbrev-ref', 'HEAD']);
      const st = await runCapture('git', ['-C', ROOT, 'status', '--porcelain']);
      const lg = await runCapture('git', ['-C', ROOT, 'log', '-5', '--oneline']);
      return text(`Branch: ${b.out.trim()}\nUncommitted:\n${st.out.trim() || '(none)'}\nLast commits:\n${lg.out.trim()}`);
    }
    case 'publish_release': {
      if (job && job.code === null) throw new Error('A build is running - wait for it first.');
      const v = String(a.version || '').trim();
      if (!/^\d+\.\d+\.\d+$/.test(v)) throw new Error('Version must look like 0.6.9');
      const notes = String(a.notes || '').replace(/["`$]/g, "'").trim();
      if (!notes) throw new Error('Release notes are needed.');
      const gradleFile = fs.readFileSync(path.join(ROOT, 'app', 'build.gradle.kts'), 'utf8');
      const cur = (gradleFile.match(/versionName\s*=\s*"([^"]+)"/) || [])[1] || '0.0.0';
      // (Update 437: an already staged version — build.gradle.kts already says v, as release.ps1 supports — is allowed, but only
      // when it is newer than the latest published release and its tag doesn't exist; checked below once gh is known to work.)
      if (verCompare(v, cur) < 0) throw new Error(`Version ${v} is older than the current ${cur}.`);
      const staged = verCompare(v, cur) === 0;
      let ws0 = '';
      const br = (await runCapture('git', ['-C', ROOT, 'rev-parse', '--abbrev-ref', 'HEAD'])).out.trim();
      if (br !== 'main') throw new Error('Not on main - nothing done.');
      const st = (await runCapture('git', ['-C', ROOT, 'status', '--porcelain'])).out.trim();
      if (st) throw new Error('Commit everything first (git_commit_push) - the release must match what is on GitHub:\n' + st);
      const ghDir = 'C:\\Program Files\\GitHub CLI';
      const env = buildEnv();
      if (fs.existsSync(path.join(ghDir, 'gh.exe')) && !String(env.Path || env.PATH || '').toLowerCase().includes('github cli')) {
        const k = env.Path !== undefined ? 'Path' : 'PATH'; env[k] = (env[k] || '') + ';' + ghDir;
      }
      const gh = await runCapture(fs.existsSync(path.join(ghDir, 'gh.exe')) ? path.join(ghDir, 'gh.exe') : 'gh', ['auth', 'status'], { env });
      if (gh.code !== 0) throw new Error('GitHub CLI (gh) is missing or not signed in: run tools\\release\\release.ps1 once by hand (it installs gh and signs in) - nothing done.');
      // (Update 437) Never re-publish: the tag must not exist (remote, local or as a release) and v must be newer than the latest release.
      const ghExe = fs.existsSync(path.join(ghDir, 'gh.exe')) ? path.join(ghDir, 'gh.exe') : 'gh';
      const latest = await runCapture(ghExe, ['api', 'repos/dzepdzep/Voltcap/releases/latest', '--jq', '.tag_name'], { env });
      if (latest.code !== 0 || !/^v?\d+\.\d+\.\d+/.test(latest.out.trim())) throw new Error('Could not read the latest published release - nothing done.\n' + latest.out.trim());
      if (verCompare(v, latest.out.trim()) <= 0) throw new Error(`Version ${v} isn't newer than the latest published release ${latest.out.trim()} - nothing done.`);
      const remoteTag = await runCapture('git', ['-C', ROOT, 'ls-remote', '--tags', 'origin', 'refs/tags/v' + v]);
      const localTag = await runCapture('git', ['-C', ROOT, 'tag', '--list', 'v' + v]);
      const rel = await runCapture(ghExe, ['release', 'view', 'v' + v, '-R', 'dzepdzep/Voltcap'], { env });
      if (remoteTag.out.trim() || localTag.out.trim() || rel.code === 0) throw new Error(`Tag v${v} already exists - nothing done.`);
      if (staged) ws0 = `(staged version ${v} kept with its code; latest published ${latest.out.trim()})\n`;
      fs.mkdirSync(BUILDS, { recursive: true });
      const log = path.join(BUILDS, 'release_' + v + '_' + new Date().toISOString().replace(/[:.]/g, '-') + '.log');
      const ws = fs.createWriteStream(log);
      if (ws0) ws.write(ws0);
      const p = spawn('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', path.join(ROOT, 'tools', 'release', 'release.ps1'), v, notes],
        { cwd: ROOT, env, windowsHide: true });
      job = { task: 'release ' + v, started: Date.now(), ended: null, code: null, log, pid: p.pid };
      const j = job;
      p.stdout.pipe(ws, { end: false }); p.stderr.pipe(ws, { end: false });
      p.on('error', (e) => { ws.write('\n' + e.message + '\n'); });
      p.on('close', (code) => { j.code = code === null ? -1 : code; j.ended = Date.now(); ws.end(); });
      return text(`Publishing Voltcap ${v} (${staged ? 'staged' : 'was ' + cur}). Log: ${log}\nCheck with gradle_status.`);
    }
    case 'git_commit_push': {
      if (job && job.code === null) throw new Error('Gradle is running - wait for it first.');
      const msg = String(a.message || '').trim();
      if (!msg) throw new Error('A commit message is needed.');
      const br = (await runCapture('git', ['-C', ROOT, 'rev-parse', '--abbrev-ref', 'HEAD'])).out.trim();
      if (br !== 'main') throw new Error('Not on main (on ' + br + ') - nothing done.');
      const st = await runCapture('git', ['-C', ROOT, 'status', '--porcelain']);
      if (!st.out.trim()) return text('Nothing to commit.');
      const add = await runCapture('git', ['-C', ROOT, 'add', '-A']);
      if (add.code !== 0) throw new Error('git add failed:\n' + add.out);
      const cargs = ['-C', ROOT, 'commit', '-q', '-m', msg];
      if (a.body) cargs.push('-m', String(a.body));
      const c = await runCapture('git', cargs);
      if (c.code !== 0) throw new Error('git commit failed:\n' + c.out);
      const p = await runCapture('git', ['-C', ROOT, 'push', 'origin', 'main']);
      const lg = await runCapture('git', ['-C', ROOT, 'log', '-1', '--oneline']);
      return text(`Committed: ${lg.out.trim()}\nChanged:\n${st.out.trim()}\nPush: ${p.code === 0 ? 'OK' : 'FAILED (exit ' + p.code + ')'}\n${tail(p.out, 10)}`);
    }
    default: throw new Error('Unknown tool ' + name);
  }
}

// ---------- MCP over stdio (newline-delimited JSON-RPC 2.0) ----------
function send(msg) { process.stdout.write(JSON.stringify(msg) + '\n'); }
async function handle(m) {
  if (m.id === undefined || m.id === null) return; // notifications
  try {
    if (m.method === 'initialize') {
      send({ jsonrpc: '2.0', id: m.id, result: { protocolVersion: (m.params && m.params.protocolVersion) || '2024-11-05', capabilities: { tools: {} }, serverInfo: { name: 'voltcap-adb', version: '1.3.0' } } });
    } else if (m.method === 'tools/list') {
      send({ jsonrpc: '2.0', id: m.id, result: { tools: TOOLS } });
    } else if (m.method === 'tools/call') {
      try { send({ jsonrpc: '2.0', id: m.id, result: await call(m.params.name, m.params.arguments || {}) }); }
      catch (e) { send({ jsonrpc: '2.0', id: m.id, result: { content: [{ type: 'text', text: 'Error: ' + e.message }], isError: true } }); }
    } else if (m.method === 'ping') {
      send({ jsonrpc: '2.0', id: m.id, result: {} });
    } else {
      send({ jsonrpc: '2.0', id: m.id, error: { code: -32601, message: 'Method not found: ' + m.method } });
    }
  } catch (e) { send({ jsonrpc: '2.0', id: m.id, error: { code: -32603, message: e.message } }); }
}
let buf = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (d) => {
  buf += d; let i;
  while ((i = buf.indexOf('\n')) >= 0) {
    const line = buf.slice(0, i).trim(); buf = buf.slice(i + 1);
    if (!line) continue;
    let m; try { m = JSON.parse(line); } catch { continue; }
    handle(m);
  }
});
module.exports = { decodePng, downscale, encodePng };
