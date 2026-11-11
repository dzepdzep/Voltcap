// Batch 521: the shipped MCP server over stdio (no handheld, no adb call reached): the guard refuses before anything runs.
// run: node tools/adb_mcp/test/server_e2e.test.js
'use strict';
const { spawn } = require('child_process'); const path = require('path'); const assert = require('assert');
const msgs = [
  { jsonrpc: '2.0', id: 1, method: 'initialize', params: { protocolVersion: '2024-11-05', capabilities: {}, clientInfo: { name: 't', version: '1' } } },
  { jsonrpc: '2.0', id: 2, method: 'tools/list' },
  { jsonrpc: '2.0', id: 3, method: 'tools/call', params: { name: 'shell', arguments: { serial: 'X', command: 'pm clear com.shelf.launcher' } } },
  { jsonrpc: '2.0', id: 4, method: 'tools/call', params: { name: 'launch_app', arguments: { serial: 'X', package: 'com.shelf.launcher' } } },
  { jsonrpc: '2.0', id: 5, method: 'tools/call', params: { name: 'install_build', arguments: { serial: 'X', variant: 'release' } } },
  { jsonrpc: '2.0', id: 6, method: 'tools/call', params: { name: 'shell', arguments: { serial: 'X', command: 'settings put global wifi_on 0' } } },
  { jsonrpc: '2.0', id: 7, method: 'tools/call', params: { name: 'install_apk', arguments: { serial: 'adb-1._adb-tls-connect._tcp', path: 'x.apk' } } },
];
const p = spawn(process.execPath, [path.join(__dirname, '..', 'server.js')], { env: Object.assign({}, process.env, { ADB: 'adb-not-used-by-this-test' }) });
let out = ''; p.stdout.on('data', (d) => { out += d; });
p.stdin.write(msgs.map((m) => JSON.stringify(m)).join('\n') + '\n'); p.stdin.end();
setTimeout(() => {
  p.kill();
  const r = {}; out.split('\n').filter(Boolean).forEach((l) => { try { const m = JSON.parse(l); r[m.id] = m; } catch (e) {} });
  const names = r[2].result.tools.map((t) => t.name);
  assert(names.includes('uninstall_qa') && names.includes('install_production'));
  const err = (id) => { const c = r[id] && r[id].result; assert(c && c.isError, 'call ' + id + ' must fail'); return c.content[0].text; };
  assert(/Refused/.test(err(3)) && /Nothing was run/.test(err(3)));
  assert(/Refused/.test(err(4)));
  assert(/Refused/.test(err(5)));
  assert(/system settings/.test(err(6)));
  assert(/not a USB connection/.test(err(7)));
  console.log('server e2e: OK (' + names.length + ' tools)');
}, 3000);
