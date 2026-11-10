'use strict';
// Batch 521: fail-closed protection of the owner's real Voltcap (com.shelf.launcher) for every QA install / shell step that
// goes through tools/adb_mcp/server.js. No dependencies; pure functions plus checks that take an `adb(args)` runner, so
// test/guard.test.js exercises the shipped code with fake devices and synthetic APKs (nothing is installed by the tests).
//
// Rules (each one alone blocks):
//   1. The APK's own binary manifest must say package com.shelf.launcher.qa (read from the APK, not from a file name or a
//      build setting). Unreadable = refused.
//   2. Its signer certificate (APK Signature Scheme v2/v3) must NOT be the production signer pinned below. Unreadable = refused.
//   3. The target must be named explicitly by serial, connected over USB (no Wi-Fi / mDNS / emulator serials), report the
//      same ro.serialno, not be a TV (leanback), and its model must be in qa_devices.json.
//   4. The production app's versionCode / lastUpdateTime / signer are read before and after; any change is reported as an alarm.
//   5. Production installs exist only as install_production: the APK's sha256 must be one of production_pins.json (published
//      release files), never -d, never uninstall.
// A shell command that clears, uninstalls, disables, force-stops or runs as the production app, or changes system settings
// / radios, is refused (rule 6).
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const crypto = require('crypto');

const PRODUCTION_PACKAGE = 'com.shelf.launcher';
const QA_PACKAGE = 'com.shelf.launcher.qa';
const PRODUCTION_SIGNER_SHA256 = '88ce4b67ae3825a04585114aec25cfaf5023e0b55cfdb61f375d30945f344197';

// ---------- APK reading (zip central directory + AXML + APK signing block) ----------
function findEocd(buf) {
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 22 - 65535); i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) return i;
  }
  return -1;
}
function zipEntry(buf, name) {
  const eocd = findEocd(buf);
  if (eocd < 0) return null;
  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  for (let i = 0; i < count; i++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) return null;
    const method = buf.readUInt16LE(p + 10), csize = buf.readUInt32LE(p + 20);
    const nlen = buf.readUInt16LE(p + 28), xlen = buf.readUInt16LE(p + 30), clen = buf.readUInt16LE(p + 32);
    const lho = buf.readUInt32LE(p + 42);
    const n = buf.toString('utf8', p + 46, p + 46 + nlen);
    if (n === name) {
      if (buf.readUInt32LE(lho) !== 0x04034b50) return null;
      const start = lho + 30 + buf.readUInt16LE(lho + 26) + buf.readUInt16LE(lho + 28);
      const raw = buf.subarray(start, start + csize);
      if (method === 0) return Buffer.from(raw);
      if (method === 8) return zlib.inflateRawSync(raw);
      return null;
    }
    p += 46 + nlen + xlen + clen;
  }
  return null;
}
/** package / versionCode / versionName of the <manifest> element of a binary AndroidManifest.xml (null if unreadable). */
function axmlManifest(data) {
  if (!data || data.length < 8 || data.readUInt16LE(0) !== 0x0003) return null;
  let pos = 8; const strings = []; let resIds = [];
  while (pos + 8 <= data.length) {
    const type = data.readUInt16LE(pos), csize = data.readUInt32LE(pos + 4);
    if (csize < 8) return null;
    if (type === 0x0001) {
      const count = data.readUInt32LE(pos + 8), flags = data.readUInt32LE(pos + 16), sStart = data.readUInt32LE(pos + 20);
      const utf8 = (flags & 0x100) !== 0, base = pos + sStart;
      for (let i = 0; i < count; i++) {
        let p = base + data.readUInt32LE(pos + 28 + i * 4);
        if (utf8) {
          p += (data[p] & 0x80) ? 2 : 1;
          let ln = data[p];
          if (ln & 0x80) { ln = ((ln & 0x7f) << 8) | data[p + 1]; p += 2; } else p += 1;
          strings.push(data.toString('utf8', p, p + ln));
        } else {
          let ln = data.readUInt16LE(p); p += 2;
          if (ln & 0x8000) { ln = ((ln & 0x7fff) << 16) | data.readUInt16LE(p); p += 2; }
          strings.push(data.toString('utf16le', p, p + ln * 2));
        }
      }
    } else if (type === 0x0180) {
      resIds = []; for (let q = pos + 8; q < pos + csize; q += 4) resIds.push(data.readUInt32LE(q));
    } else if (type === 0x0102) {
      const name = strings[data.readInt32LE(pos + 20)];
      if (name === 'manifest') {
        const aStart = data.readUInt16LE(pos + 24), aSize = data.readUInt16LE(pos + 26), aCount = data.readUInt16LE(pos + 28);
        const out = {};
        for (let i = 0; i < aCount; i++) {
          const a = pos + 16 + aStart + i * aSize;
          const nIdx = data.readInt32LE(a + 4), rawIdx = data.readInt32LE(a + 8), dType = data[a + 15], dVal = data.readUInt32LE(a + 16);
          let key = nIdx >= 0 ? strings[nIdx] : '?';
          const rid = nIdx >= 0 && nIdx < resIds.length ? resIds[nIdx] : 0;
          if (rid === 0x0101021b) key = 'versionCode'; else if (rid === 0x0101021c) key = 'versionName';
          out[key] = rawIdx >= 0 ? strings[rawIdx] : (dType === 0x10 || dType === 0x11 ? dVal : (dType === 3 ? strings[dVal] : dVal));
        }
        return out;
      }
    }
    pos += csize;
  }
  return null;
}
/** SHA-256 of every signer certificate in the v2 / v3 signing blocks (empty array = unsigned / unreadable). */
function signerDigests(buf) {
  try {
    const eocd = findEocd(buf); if (eocd < 0) return [];
    const cd = buf.readUInt32LE(eocd + 16);
    if (cd < 24 || buf.toString('latin1', cd - 16, cd) !== 'APK Sig Block 42') return [];
    const size = Number(buf.readBigUInt64LE(cd - 24));
    let p = cd - size; const out = [];
    while (p < cd - 24) {
      const len = Number(buf.readBigUInt64LE(p)), id = buf.readUInt32LE(p + 8);
      if (id === 0x7109871a || id === 0xf05368c0) {
        let q = p + 12 + 4 + 4 + 4;
        q += 4 + buf.readUInt32LE(q);
        q += 4;
        const clen = buf.readUInt32LE(q); q += 4;
        out.push(crypto.createHash('sha256').update(buf.subarray(q, q + clen)).digest('hex'));
      }
      p += 8 + len;
    }
    return [...new Set(out)];
  } catch (e) { return []; }
}
function apkIdentity(file) {
  const buf = fs.readFileSync(file);
  let manifest = null;
  try { manifest = axmlManifest(zipEntry(buf, 'AndroidManifest.xml')); } catch (e) { manifest = null; }
  return { file, sha256: crypto.createHash('sha256').update(buf).digest('hex'), package: manifest && manifest.package,
    versionCode: manifest && manifest.versionCode, versionName: manifest && manifest.versionName, signers: signerDigests(buf) };
}
/** Rules 1 + 2: [] when the APK is the isolated QA app; otherwise the reasons it is refused. */
function qaApkProblems(id) {
  const p = [];
  if (!id || !id.package) p.push('package unreadable from the APK manifest');
  else if (id.package !== QA_PACKAGE) p.push(`package ${id.package} is not ${QA_PACKAGE}` + (id.package === PRODUCTION_PACKAGE ? ' (it is the real Voltcap)' : ''));
  if (!id || !id.signers || id.signers.length === 0) p.push('signer certificate unreadable');
  else if (id.signers.includes(PRODUCTION_SIGNER_SHA256)) p.push('signed with the production (release) key');
  return p;
}

// ---------- target device (rule 3) ----------
function loadDevices(dir) {
  try { return JSON.parse(fs.readFileSync(path.join(dir || __dirname, 'qa_devices.json'), 'utf8')); } catch (e) { return { models: [] }; }
}
function serialProblems(serial) {
  const s = String(serial || '');
  if (!s) return ['name the handheld by its USB serial (list_handhelds) - no default target for installs'];
  if (/[:]|\._adb-tls|\._tcp|^emulator-/i.test(s)) return [`${s} is not a USB connection (Wi-Fi / mDNS / emulator serials are refused)`];
  if (!/^[A-Za-z0-9._-]{4,64}$/.test(s)) return [`${s} does not look like a USB serial`];
  return [];
}
async function targetProblems(serial, adb, devices) {
  const p = serialProblems(serial);
  if (p.length) return { problems: p };
  const prop = async (k) => String(await adb(['shell', 'getprop', k], serial)).trim();
  let model = '', ser = '', tv = false, transportOk = false;
  try {
    const list = String(await adb(['devices', '-l']));
    const line = list.split(/\r?\n/).find((l) => l.split(/\s+/)[0] === serial) || '';
    transportOk = /\sdevice\s/.test(line + ' ') && !/tcp|tls/i.test(line);
    model = await prop('ro.product.model');
    ser = await prop('ro.serialno');
    const feats = String(await adb(['shell', 'pm', 'list', 'features'], serial));
    tv = /android\.software\.leanback\b|android\.hardware\.type\.television/.test(feats);
  } catch (e) { return { problems: ['could not read the handheld: ' + e.message] }; }
  if (!transportOk) p.push(`${serial} is not listed as a ready USB device`);
  if (ser && ser !== serial) p.push(`ro.serialno ${ser} differs from ${serial}`);
  if (tv) p.push(`${model || serial} is a TV device - QA installs are only for handhelds`);
  const entries = (devices && devices.models) || [];
  const known = entries.some((m) => (m.serial && m.serial === serial) || (!m.serial && m.model && m.model === model));
  if (!known) p.push(`${model || '?'} ${serial} is not in tools/adb_mcp/qa_devices.json (add it once it is positively identified)`);
  return { problems: p, model };
}

// ---------- production app watch (rule 4) ----------
async function productionState(serial, adb) {
  const out = String(await adb(['shell', 'dumpsys', 'package', PRODUCTION_PACKAGE], serial).catch(() => ''));
  const pick = (re) => ((out.match(re) || [])[1] || '').trim();
  return { installed: /Package \[com\.shelf\.launcher\]/.test(out), versionCode: pick(/versionCode=(\d+)/),
    lastUpdateTime: pick(/lastUpdateTime=([^\r\n]+)/), signatures: pick(/signatures=PackageSignatures\{[^\[]*\[([^\]]*)\]/) };
}
function sameProduction(a, b) { return a.installed === b.installed && a.versionCode === b.versionCode && a.lastUpdateTime === b.lastUpdateTime && a.signatures === b.signatures; }

// ---------- production installs (rule 5) ----------
function loadPins(dir) {
  try { return JSON.parse(fs.readFileSync(path.join(dir || __dirname, 'production_pins.json'), 'utf8')); } catch (e) { return { apks: [] }; }
}
function productionApkProblems(id, pins) {
  const p = [];
  const pin = (pins.apks || []).find((x) => x.sha256 === id.sha256);
  if (!pin) p.push(`sha256 ${id.sha256.slice(0, 16)}... is not a pinned published release (production_pins.json)`);
  if (id.package !== PRODUCTION_PACKAGE) p.push(`package ${id.package} is not ${PRODUCTION_PACKAGE}`);
  if (!id.signers.includes(PRODUCTION_SIGNER_SHA256)) p.push('not signed with the production key');
  return p;
}

// ---------- shell (rule 6) ----------
const PROD = 'com\\.shelf\\.launcher(?!\\.qa)(?![\\w.])';
const SHELL_BLOCKS = [
  [new RegExp(`\\b(pm|cmd\\s+package)\\s+(clear|uninstall|disable(-user)?|hide|suspend|reset-permissions|revoke|set-installer|install-existing|path)\\b[^;|&]*${PROD}`), 'changes the real Voltcap package'],
  [new RegExp(`\\b(am|cmd\\s+activity)\\s+(force-stop|kill|crash|clear-debug-app|set-debug-app|instrument|start|start-activity|start-service|broadcast)\\b[^;|&]*${PROD}`), 'acts on the real Voltcap process'],
  [new RegExp(`\\b(run-as|monkey)\\b[^;|&]*${PROD}`), 'runs inside / launches the real Voltcap'],
  [new RegExp(`/(data/data|data/user/\\d+|sdcard/Android/data|storage/emulated/\\d+/Android/data)/${PROD}`), 'touches the real Voltcap\'s files'],
  [/\b(pm|cmd\s+package)\s+(uninstall|clear|install|install-create)\b/, 'installs / uninstalls / clears a package (use install_build / install_apk)'],
  [/\bsettings\s+(put|delete|reset)\b/, 'changes system settings'],
  [/\b(svc\s+(wifi|bluetooth|data|nfc|power)|cmd\s+(wifi|bluetooth_manager|connectivity)\s+(set|enable|disable)|cmd\s+notification\s+set_dnd)\b/, 'changes radios / Do not disturb'],
  [/\b(reboot|setprop)\b|\bwm\s+(size|density)\s+(\d|reset)/, 'reboots or changes system properties / screen size'],
  [/\brm\s+(-\w+\s+)*[^;|&]*(\s|=|^)(\/|\/sdcard\/?|\/storage\/emulated\/0\/?|\/data\/?)(\s|;|$)/, 'deletes a whole storage root'],
  [/(?:\b(?:rm|mv|cp|tee|touch|mkdir|sed\s+-i)\b|>)[^;|&]*\/(RetroArch|PPSSPP|dolphin-emu|AetherSX2|NetherSX2|DCIM|Pictures|Download|Documents|Voltcap(?![\w.-]))\b/i, 'writes to your saves / personal folders'],
  [/\b(appops\s+set|cmd\s+appops\s+set|pm\s+grant)\b[^;|&]*com\.shelf\.launcher(?![\w.])/, 'changes the real Voltcap\'s permissions'],
];
/** Every non-comment line of a script that would be refused if typed into the shell tool (push of .sh files). */
function scriptProblems(textContent) {
  return String(textContent || '').split(/\r?\n/).map((l, i) => [i + 1, l]).filter(([, l]) => !/^\s*#/.test(l))
    .map(([n, l]) => [n, shellProblems(l)]).filter(([, p]) => p.length).map(([n, p]) => `line ${n}: ${p.join('; ')}`);
}
function shellProblems(cmd) {
  const c = String(cmd || '');
  return SHELL_BLOCKS.filter(([re]) => re.test(c)).map(([, why]) => why);
}

module.exports = { PRODUCTION_PACKAGE, QA_PACKAGE, PRODUCTION_SIGNER_SHA256, zipEntry, axmlManifest, signerDigests, apkIdentity,
  qaApkProblems, serialProblems, targetProblems, loadDevices, productionState, sameProduction, loadPins, productionApkProblems, shellProblems, scriptProblems };

// CLI dry run (installs nothing): node guard.js check-apk <apk> | node guard.js check-target <serial>
if (require.main === module) {
  const [cmd, arg] = process.argv.slice(2);
  if (cmd === 'check-apk') {
    const id = apkIdentity(arg); const p = qaApkProblems(id);
    console.log(JSON.stringify({ apk: path.basename(arg), package: id.package, versionCode: id.versionCode, signers: id.signers.map((s) => s.slice(0, 16)), sha256: id.sha256, qaInstallAllowed: p.length === 0, problems: p }));
  } else if (cmd === 'check-target') {
    const { execFile } = require('child_process');
    const ADB = process.env.ADB || 'adb';
    const run = (args, serial) => new Promise((res, rej) => execFile(ADB, (serial ? ['-s', serial] : []).concat(args), { encoding: 'utf8', timeout: 30000, windowsHide: true }, (e, o, se) => e ? rej(new Error(se || e.message)) : res(o)));
    targetProblems(arg, run, loadDevices()).then(async (t) => {
      const prod = serialProblems(arg).length ? null : await productionState(arg, run).catch(() => null);   // never talk to a non-USB target
      console.log(JSON.stringify({ serial: arg, model: t.model, qaInstallAllowed: t.problems.length === 0, problems: t.problems, production: prod }));
    });
  } else { console.log('usage: node guard.js check-apk <apk> | check-target <serial>'); process.exitCode = 2; }
}
