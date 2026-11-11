// Batch 521: regression tests for tools/adb_mcp/guard.js (run: node tools/adb_mcp/test/guard.test.js [real.apk ...]).
// Synthetic APKs (built here: zip + binary manifest + APK signing block) and fake adb runners - nothing is installed and no
// handheld is needed. Real APK paths given on the command line are also checked (package / signer read from the file).
'use strict';
const assert = require('assert'); const crypto = require('crypto'); const fs = require('fs'); const os = require('os'); const path = require('path');
const zlib = require('zlib');
const g = require('../guard');

// ---------- synthetic APK builder ----------
function axml(pkg, versionCode) {
  const strs = ['versionCode', 'package', 'manifest', pkg];
  const enc = strs.map((x) => { const b = Buffer.alloc(2 + x.length * 2 + 2); b.writeUInt16LE(x.length, 0); b.write(x, 2, 'utf16le'); return b; });
  const offs = []; let o = 0; for (const b of enc) { offs.push(o); o += b.length; }
  let data = Buffer.concat(enc); if (data.length % 4) data = Buffer.concat([data, Buffer.alloc(4 - (data.length % 4))]);
  const spHead = Buffer.alloc(28); const sp = Buffer.concat([spHead, Buffer.alloc(offs.length * 4), data]);
  sp.writeUInt16LE(1, 0); sp.writeUInt16LE(28, 2); sp.writeUInt32LE(sp.length, 4); sp.writeUInt32LE(strs.length, 8);
  sp.writeUInt32LE(0, 12); sp.writeUInt32LE(0, 16); sp.writeUInt32LE(28 + offs.length * 4, 20); sp.writeUInt32LE(0, 24);
  offs.forEach((x, i) => sp.writeUInt32LE(x, 28 + i * 4));
  const rm = Buffer.alloc(12); rm.writeUInt16LE(0x0180, 0); rm.writeUInt16LE(8, 2); rm.writeUInt32LE(12, 4); rm.writeUInt32LE(0x0101021b, 8);
  const attrs = [[1, 3, 3, 0], [0, -1, 0x10, versionCode]];               // package (string), versionCode (int, res id)
  const el = Buffer.alloc(36 + attrs.length * 20);
  el.writeUInt16LE(0x0102, 0); el.writeUInt16LE(16, 2); el.writeUInt32LE(el.length, 4); el.writeInt32LE(-1, 12);
  el.writeInt32LE(-1, 16); el.writeInt32LE(2, 20); el.writeUInt16LE(20, 24); el.writeUInt16LE(20, 26); el.writeUInt16LE(attrs.length, 28);
  attrs.forEach(([name, raw, type, val], i) => { const a = 36 + i * 20; el.writeInt32LE(-1, a); el.writeInt32LE(name, a + 4); el.writeInt32LE(raw, a + 8); el.writeUInt16LE(8, a + 12); el[a + 15] = type; el.writeUInt32LE(type === 3 ? raw : val, a + 16); });
  const body = Buffer.concat([sp, rm, el]); const head = Buffer.alloc(8); head.writeUInt16LE(3, 0); head.writeUInt16LE(8, 2); head.writeUInt32LE(8 + body.length, 4);
  return Buffer.concat([head, body]);
}
function sigBlock(cert) {
  const u32 = (n) => { const b = Buffer.alloc(4); b.writeUInt32LE(n); return b; };
  const lp = (b) => Buffer.concat([u32(b.length), b]);
  const signedData = Buffer.concat([lp(Buffer.alloc(0)), lp(lp(cert))]);    // digests (empty), certificates [cert]
  const signer = Buffer.concat([lp(signedData), lp(Buffer.alloc(0)), lp(Buffer.alloc(0))]);
  const value = lp(lp(signer));
  const pair = Buffer.alloc(12); pair.writeBigUInt64LE(BigInt(4 + value.length), 0); pair.writeUInt32LE(0x7109871a, 8);
  const pairs = Buffer.concat([pair, value]);
  const size = BigInt(pairs.length + 8 + 16);
  const s1 = Buffer.alloc(8); s1.writeBigUInt64LE(size); const s2 = Buffer.alloc(8); s2.writeBigUInt64LE(size);
  return Buffer.concat([s1, pairs, s2, Buffer.from('APK Sig Block 42')]);
}
function apk(pkg, cert, { deflate = true, signed = true, manifest = true } = {}) {
  const name = Buffer.from('AndroidManifest.xml'); const raw = axml(pkg, 77);
  const data = deflate ? zlib.deflateRawSync(raw) : raw;
  const lh = Buffer.alloc(30); lh.writeUInt32LE(0x04034b50, 0); lh.writeUInt16LE(deflate ? 8 : 0, 8); lh.writeUInt32LE(data.length, 18); lh.writeUInt32LE(raw.length, 22); lh.writeUInt16LE(name.length, 26);
  const entryName = manifest ? name : Buffer.from('classes.dex');
  const local = Buffer.concat([lh.subarray(0, 26), (() => { const b = Buffer.alloc(4); b.writeUInt16LE(entryName.length, 0); return b; })(), entryName, data]);
  const sb = signed ? sigBlock(cert) : Buffer.alloc(0);
  const cdOff = local.length + sb.length;
  const ch = Buffer.alloc(46); ch.writeUInt32LE(0x02014b50, 0); ch.writeUInt16LE(deflate ? 8 : 0, 10); ch.writeUInt32LE(data.length, 20); ch.writeUInt32LE(raw.length, 24); ch.writeUInt16LE(entryName.length, 28); ch.writeUInt32LE(0, 42);
  const cd = Buffer.concat([ch, entryName]);
  const eocd = Buffer.alloc(22); eocd.writeUInt32LE(0x06054b50, 0); eocd.writeUInt16LE(1, 8); eocd.writeUInt16LE(1, 10); eocd.writeUInt32LE(cd.length, 12); eocd.writeUInt32LE(cdOff, 16);
  return Buffer.concat([local, sb, cd, eocd]);
}
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'guard-'));
const write = (n, b) => { const f = path.join(tmp, n); fs.writeFileSync(f, b); return f; };
// A certificate whose sha256 IS the pinned production digest can't be made, so the production signer is simulated by
// pinning: the test swaps in a digest it can produce (and restores it).
const qaCert = Buffer.from('qa-debug-key-certificate'); const prodCert = Buffer.from('release-key-certificate');
const prodDigest = crypto.createHash('sha256').update(prodCert).digest('hex');
let n = 0; const ok = (c, m) => { assert(c, m); n++; };

// ---------- rule 1 + 2: the APK ----------
{
  const id = g.apkIdentity(write('qa.apk', apk('com.shelf.launcher.qa', qaCert)));
  ok(id.package === 'com.shelf.launcher.qa' && id.versionCode === 77, 'reads package + versionCode from the binary manifest');
  ok(id.signers.length === 1 && id.signers[0] === crypto.createHash('sha256').update(qaCert).digest('hex'), 'reads the signer digest');
  ok(g.qaApkProblems(id).length === 0, 'QA package + QA key = allowed');
  ok(g.apkIdentity(write('qa-stored.apk', apk('com.shelf.launcher.qa', qaCert, { deflate: false }))).package === 'com.shelf.launcher.qa', 'stored (uncompressed) manifest read too');
  const prod = g.apkIdentity(write('prod.apk', apk('com.shelf.launcher', qaCert)));
  ok(g.qaApkProblems(prod).some((p) => /real Voltcap/.test(p)), 'production package refused even with a non-release key');
  ok(g.qaApkProblems(g.apkIdentity(write('other.apk', apk('com.example.other', qaCert)))).length === 1, 'any other package refused');
  ok(g.qaApkProblems(g.apkIdentity(write('suffix.apk', apk('com.shelf.launcher.qax', qaCert)))).length === 1, 'look-alike package refused');
  ok(g.qaApkProblems(g.apkIdentity(write('unsigned.apk', apk('com.shelf.launcher.qa', qaCert, { signed: false })))).some((p) => /signer/.test(p)), 'unsigned / unreadable signer refused');
  ok(g.qaApkProblems(g.apkIdentity(write('nomanifest.apk', apk('com.shelf.launcher.qa', qaCert, { manifest: false })))).some((p) => /unreadable/.test(p)), 'no manifest refused');
  ok(g.qaApkProblems(g.apkIdentity(write('junk.apk', Buffer.from('not a zip at all')))).length === 2, 'garbage file refused on both rules');
  ok(g.qaApkProblems(null).length === 2, 'no identity refused');
  // release key: QA package but production signer
  const real = g.PRODUCTION_SIGNER_SHA256;
  const fake = { ...g.apkIdentity(write('qa-rel.apk', apk('com.shelf.launcher.qa', prodCert))) };
  fake.signers = fake.signers.map((s) => (s === prodDigest ? real : s));
  ok(g.qaApkProblems(fake).some((p) => /production \(release\) key/.test(p)), 'QA package signed with the release key refused');
}
// ---------- rule 3: the target ----------
function fakeAdb(dev) {
  return async (args, serial) => {
    const a = args.join(' ');
    if (a === 'devices -l') return 'List of devices attached\n' + dev.list + '\n';
    if (serial !== dev.serial) throw new Error('device not found');
    if (a === 'shell getprop ro.product.model') return dev.model + '\n';
    if (a === 'shell getprop ro.serialno') return (dev.ro || dev.serial) + '\n';
    if (a === 'shell pm list features') return dev.tv ? 'feature:android.software.leanback\n' : 'feature:android.hardware.touchscreen\n';
    if (a.startsWith('shell dumpsys package com.shelf.launcher')) return dev.dumpsys || '';
    throw new Error('unexpected adb ' + a);
  };
}
const devices = { models: [{ model: 'Retroid_Pocket_Classic', serial: 'MC94516AQF091001112' }, { model: 'Some_Handheld' }] };
(async () => {
  const rp = { serial: 'MC94516AQF091001112', model: 'Retroid_Pocket_Classic', list: 'MC94516AQF091001112    device product:parrot model:Retroid_Pocket_Classic device:parrot transport_id:1' };
  ok((await g.targetProblems(rp.serial, fakeAdb(rp), devices)).problems.length === 0, 'known USB handheld allowed');
  ok((await g.targetProblems('', fakeAdb(rp), devices)).problems.length === 1, 'no serial = refused (no default target)');
  const tv = 'adb-55171HFAG0UPTH-TkW6bs._adb-tls-connect._tcp';
  ok(/not a USB/.test((await g.targetProblems(tv, fakeAdb(rp), devices)).problems[0]), 'Google TV Streamer (mDNS wireless) refused');
  ok(/not a USB/.test((await g.targetProblems('192.168.1.20:5555', fakeAdb(rp), devices)).problems[0]), 'ip:port refused');
  ok(/not a USB/.test((await g.targetProblems('emulator-5554', fakeAdb(rp), devices)).problems[0]), 'emulator refused');
  const tvUsb = { serial: 'TVUSB1234', model: 'Some_Handheld', tv: true, list: 'TVUSB1234    device model:Some_Handheld' };
  ok((await g.targetProblems(tvUsb.serial, fakeAdb(tvUsb), devices)).problems.some((p) => /TV device/.test(p)), 'leanback device refused even over USB');
  const unknown = { serial: 'ZZZ11111', model: 'Unknown_Box', list: 'ZZZ11111    device model:Unknown_Box' };
  ok((await g.targetProblems(unknown.serial, fakeAdb(unknown), devices)).problems.some((p) => /qa_devices\.json/.test(p)), 'unlisted handheld refused');
  const wrongSerial = { serial: 'MC94516AQF091001112', model: 'Retroid_Pocket_Classic', ro: 'OTHER', list: rp.list };
  ok((await g.targetProblems(wrongSerial.serial, fakeAdb(wrongSerial), devices)).problems.some((p) => /differs/.test(p)), 'ro.serialno mismatch refused');
  const unauth = { serial: 'MC94516AQF091001112', model: 'Retroid_Pocket_Classic', list: 'MC94516AQF091001112    unauthorized transport_id:4' };
  ok((await g.targetProblems(unauth.serial, fakeAdb(unauth), devices)).problems.some((p) => /not listed as a ready/.test(p)), 'unauthorized / offline refused');
  const otherSerialSameModel = { serial: 'MC00000000', model: 'Retroid_Pocket_Classic', list: 'MC00000000    device model:Retroid_Pocket_Classic' };
  ok((await g.targetProblems(otherSerialSameModel.serial, fakeAdb(otherSerialSameModel), devices)).problems.length === 1, 'serial-pinned entry does not admit another unit of the model');
  const broken = async () => { throw new Error('closed'); };
  ok((await g.targetProblems('MC94516AQF091001112', broken, devices)).problems.length === 1, 'adb failure = refused');
  // ---------- rule 4: production watch ----------
  const ds = 'Packages:\n  Package [com.shelf.launcher] (abc):\n    versionCode=74 minSdk=24 targetSdk=36\n    lastUpdateTime=2026-11-06 10:00:00\n    signatures=PackageSignatures{1 version:3, signatures:[88ce4b67], past signatures:[]}\n';
  const st = await g.productionState(rp.serial, fakeAdb({ ...rp, dumpsys: ds }));
  ok(st.installed && st.versionCode === '74' && st.signatures === '88ce4b67', 'reads the real Voltcap state');
  ok(g.sameProduction(st, { ...st }) && !g.sameProduction(st, { ...st, lastUpdateTime: 'x' }) && !g.sameProduction(st, { ...st, versionCode: '76' }), 'detects a changed real Voltcap');
  // ---------- rule 5: production pins ----------
  const pins = { apks: [{ sha256: 'aa' }] };
  const pid = { sha256: 'aa', package: 'com.shelf.launcher', signers: [g.PRODUCTION_SIGNER_SHA256] };
  ok(g.productionApkProblems(pid, pins).length === 0, 'pinned published APK allowed for install_production');
  ok(g.productionApkProblems({ ...pid, sha256: 'bb' }, pins).length === 1, 'unpinned production APK refused (e.g. a local release build)');
  ok(g.productionApkProblems({ ...pid, package: 'com.shelf.launcher.qa' }, pins).length === 1, 'QA APK refused for install_production');
  ok(g.productionApkProblems({ ...pid, signers: ['00'] }, pins).length === 1, 'wrong signer refused for install_production');
  const realPins = g.loadPins(path.join(__dirname, '..'));
  ok(realPins.apks.some((x) => x.sha256.startsWith('1101965042cc84c0')), 'production_pins.json pins the published 0.6.69');
  // ---------- rule 6: shell ----------
  const refused = ['pm clear com.shelf.launcher', 'pm uninstall com.shelf.launcher', 'cmd package uninstall -k com.shelf.launcher', 'am force-stop com.shelf.launcher',
    'am start -n com.shelf.launcher/.MainActivity', 'run-as com.shelf.launcher ls', 'monkey -p com.shelf.launcher 1', 'ls /data/data/com.shelf.launcher/shared_prefs',
    'cat /sdcard/Android/data/com.shelf.launcher/files/x', 'settings put global wifi_on 0', 'settings put system screen_brightness 10', 'svc wifi disable',
    'cmd bluetooth_manager disable', 'reboot', 'setprop persist.x 1', 'pm uninstall com.shelf.launcher.qa', 'pm install /data/local/tmp/a.apk',
    'rm -rf /sdcard/RetroArch/saves', 'rm -rf /sdcard/', 'cp x /sdcard/Download/y', 'echo x > /sdcard/Voltcap/a', 'appops set com.shelf.launcher RUN_IN_BACKGROUND deny',
    'cmd notification set_dnd on', 'wm size 640x480', 'P=com.shelf.launcher; F=/sdcard/Android/data/$P/files', 'PKG="com.shelf.launcher"', 'pm disable-user com.shelf.launcher', 'ls; am force-stop com.shelf.launcher'];
  for (const c of refused) ok(g.shellProblems(c).length > 0, 'refused: ' + c);
  const allowed = ['am force-stop com.shelf.launcher.qa', 'am start -n com.shelf.launcher.qa/com.shelf.launcher.MainActivity --es voltcap_test_layout GRID',
    'dumpsys package com.shelf.launcher', 'pidof com.shelf.launcher', 'getprop ro.product.model', 'ls /sdcard/Android/data/com.shelf.launcher.qa/files',
    'rm -rf /sdcard/Android/data/com.shelf.launcher.qa/files/out', 'rm -rf /data/local/tmp/vqa/out', 'sha256sum /sdcard/RetroArch/saves/a.srm',
    'settings get system screen_brightness', 'P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files', 'dumpsys meminfo com.shelf.launcher.qa', 'input keyevent 20', 'sh /data/local/tmp/vqa/vqa.sh seq "R R"'];
  for (const c of allowed) ok(g.shellProblems(c).length === 0, 'allowed: ' + c + ' -> ' + g.shellProblems(c));
  ok(g.scriptProblems('# am force-stop com.shelf.launcher (comment)\nam force-stop com.shelf.launcher.qa\n').length === 0, 'script comments ignored');
  ok(g.scriptProblems('P=x\nam force-stop com.shelf.launcher\n')[0].startsWith('line 2'), 'script line numbers reported');
  // ---------- the shipped tools ----------
  const vqa = fs.readFileSync(path.join(__dirname, '..', '..', 'qa', 'device', 'vqa.sh'), 'utf8');
  ok(g.scriptProblems(vqa).length === 0 && /^PKG=com\.shelf\.launcher\.qa$/m.test(vqa), 'vqa.sh passes the guard and targets the QA app');
  for (const f of ['hwgate520.sh', 'mem520.sh', 'ctl516.sh', 'xcase516.sh', 'flash513.sh', 'popclip.sh', 'navclip.sh', 'qaguard.sh', 'evidence.sh', 'gate521.sh', 'tile521.sh', 'glyph521.sh']) {
    const p = path.join(__dirname, '..', '..', 'qa', 'device', f);
    if (fs.existsSync(p)) ok(g.scriptProblems(fs.readFileSync(p, 'utf8')).length === 0, f + ' passes the guard');
  }
  const server = fs.readFileSync(path.join(__dirname, '..', 'server.js'), 'utf8');
  ok(/case 'install_apk': return qaInstall\(/.test(server) && /return qaInstall\(a\.serial, apk, false\)/.test(server), 'both install tools go through qaInstall');
  ok(!/adb\(\['install'/.test(server.replace(/async function qaInstall[\s\S]*?\n}\n/, '').replace(/case 'install_production'[\s\S]*?case 'git_status'/, '')), 'no other adb install path in server.js');
  ok(/enum: \['qa', 'debug'\]/.test(server) && !/enum: \['release', 'compat', 'debug'\]/.test(server), 'install_build no longer offers release / compat');
  ok(/required: \['serial'\]/.test(server), 'install_build needs an explicit serial');
  const qaFn = server.match(/async function qaInstall[\s\S]*?\n}\n/)[0];
  ok(qaFn.indexOf('targetProblems') < qaFn.indexOf("adb(['install") && qaFn.indexOf('qaApkProblems') < qaFn.indexOf("adb(['install"), 'checks run before adb install');
  const devs = g.loadDevices(path.join(__dirname, '..'));
  ok(devs.models.length > 0 && !JSON.stringify(devs).includes('Google_TV') && !JSON.stringify(devs).includes('adb-tls'), 'qa_devices.json lists no TV / wireless entry');
  // ---------- real APKs given on the command line ----------
  for (const f of process.argv.slice(2)) {
    const id = g.apkIdentity(f); const p = g.qaApkProblems(id);
    console.log(`  ${path.basename(f)}: ${id.package} vc ${id.versionCode} signer ${(id.signers[0] || '-').slice(0, 16)} -> ${p.length ? 'REFUSED: ' + p.join('; ') : 'QA install allowed'}`);
  }
  fs.rmSync(tmp, { recursive: true, force: true });
  console.log(`guard: OK (${n} checks)`);
})().catch((e) => { console.error('guard: FAIL', e.message); process.exit(1); });
