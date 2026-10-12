#!/usr/bin/env node
// Voltcap ADB bridge: a tiny MCP server (stdio, no dependencies) so Claude can see and drive the handheld over adb.
// Why it exists: the Claude desktop bridge strips any tool argument named "device", which breaks mobile-mcp.
// Nothing here is called "device"; the handheld is picked with "serial" (optional when only one is plugged in).
//
// Env (all optional):
//   ADB        full path to adb.exe (default: %LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe, else "adb" on PATH)
//   SHOTS_DIR  where screenshots are saved (default: <this folder>\..\..\screens)
'use strict';
const { execFile } = require('child_process');
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

function findAdb() {
  if (process.env.ADB) return process.env.ADB;
  const local = process.env.LOCALAPPDATA ? path.join(process.env.LOCALAPPDATA, 'Android', 'Sdk', 'platform-tools', 'adb.exe') : null;
  if (local && fs.existsSync(local)) return local;
  return 'adb';
}
const ADB = findAdb();
const SHOTS = process.env.SHOTS_DIR || path.resolve(__dirname, '..', '..', 'screens');

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
  { name: 'launch_app', description: 'Start an app by package name (default com.shelf.launcher).', inputSchema: { type: 'object', properties: { serial: SERIAL, package: { type: 'string' } } } },
  { name: 'logcat', description: 'Recent log lines (default 300), optionally only lines containing "grep".', inputSchema: { type: 'object', properties: { serial: SERIAL, lines: { type: 'integer' }, grep: { type: 'string' }, crashes_only: { type: 'boolean' } } } },
  { name: 'shell', description: 'Run an adb shell command on the handheld and return its output.', inputSchema: { type: 'object', properties: { serial: SERIAL, command: { type: 'string' } }, required: ['command'] } },
  // (Update 144: installing a test build and moving files, without anyone at the PC.)
  { name: 'install_apk', description: 'Install an APK from this PC on the handheld (adb install -r; "downgrade" also allows an older version).',
    inputSchema: { type: 'object', properties: { serial: SERIAL, path: { type: 'string', description: 'Full path of the .apk on this PC' }, downgrade: { type: 'boolean' } }, required: ['path'] } },
  { name: 'push', description: 'Copy a file from this PC to the handheld.', inputSchema: { type: 'object', properties: { serial: SERIAL, pc_path: { type: 'string' }, handheld_path: { type: 'string' } }, required: ['pc_path', 'handheld_path'] } },
  { name: 'pull', description: 'Copy a file from the handheld to this PC.', inputSchema: { type: 'object', properties: { serial: SERIAL, handheld_path: { type: 'string' }, pc_path: { type: 'string' } }, required: ['handheld_path', 'pc_path'] } }
];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const text = (t) => ({ content: [{ type: 'text', text: t }] });

async function call(name, a) {
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
      const pkg = a.package || 'com.shelf.launcher';
      return text((await adb(['shell', 'monkey', '-p', pkg, '-c', 'android.intent.category.LAUNCHER', '1'], s)).trim());
    }
    case 'logcat': {
      const args = ['logcat', '-d', '-t', String(a.lines || 300)];
      if (a.crashes_only) args.push('-b', 'crash');
      let out = await adb(args, s, { timeout: 20000 });
      if (a.grep) out = out.split('\n').filter((l) => l.includes(a.grep)).join('\n');
      return text(out.trim() || '(empty)');
    }
    case 'shell': return text((await adb(['shell', a.command], s, { timeout: 60000 })).trim() || '(no output)');
    case 'install_apk': {
      const args = ['install', '-r'];
      if (a.downgrade) args.push('-d');
      args.push(a.path);
      return text((await adb(args, s, { timeout: 180000 })).trim());
    }
    case 'push': return text((await adb(['push', a.pc_path, a.handheld_path], s, { timeout: 180000 })).trim());
    case 'pull': {
      fs.mkdirSync(path.dirname(a.pc_path), { recursive: true });
      return text((await adb(['pull', a.handheld_path, a.pc_path], s, { timeout: 180000 })).trim());
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
      send({ jsonrpc: '2.0', id: m.id, result: { protocolVersion: (m.params && m.params.protocolVersion) || '2024-11-05', capabilities: { tools: {} }, serverInfo: { name: 'voltcap-adb', version: '1.0.0' } } });
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
