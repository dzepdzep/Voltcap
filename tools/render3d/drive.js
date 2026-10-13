const { chromium } = require('/opt/npm-tools/node_modules/playwright');
const sharp = require('/opt/npm-tools/node_modules/sharp');
const http = require('http'), fs = require('fs'), path = require('path');
const ROOT = { '/r/': '/tmp/claude-0/r3d166/', '/all/': '/mnt/user-data/uploads/all/', '/v6/': '/mnt/user-data/uploads/v6/', '/v68/': '/mnt/user-data/uploads/VoltCap_3D_Shape_Library_v6_8_Complete--VoltCap_v6_Refined--v6/' };
const srv = http.createServer((q, s) => {
  for (const [k, v] of Object.entries(ROOT)) if (q.url.startsWith(k)) {
    const f = path.join(v, decodeURIComponent(q.url.slice(k.length)));
    if (fs.existsSync(f)) { s.writeHead(200, { 'Content-Type': f.endsWith('.html') ? 'text/html' : f.endsWith('.js') ? 'text/javascript' : 'application/octet-stream' }); return fs.createReadStream(f).pipe(s); }
  }
  s.writeHead(404); s.end();
});
// premultiplied, bottom-up RGBA -> straight, top-down
function fix(b64, size) {
  const src = Buffer.from(b64, 'base64'); const out = Buffer.alloc(src.length); const row = size * 4;
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    const i = (size - 1 - y) * row + x * 4, o = y * row + x * 4; const a = src[i + 3];
    out[o + 3] = a;
    for (let c = 0; c < 3; c++) out[o + c] = a ? Math.min(255, Math.round(src[i + c] * 255 / a)) : 0;
  }
  return out;
}
module.exports = { srv, fix, sharp, chromium };
if (require.main === module) (async () => {
  await new Promise(r => srv.listen(8765, r));
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage(); p.on('console', m => console.log('page:', m.text())); p.on('pageerror', e => console.log('err', e.message));
  await p.goto('http://127.0.0.1:8765/r/render.html'); await p.waitForFunction(() => window.ready);
  const slots = JSON.parse(fs.readFileSync('/mnt/user-data/uploads/v6/artwork_slots.json'));
  const man = JSON.parse(fs.readFileSync('/mnt/user-data/uploads/v6/manifest.json'));
  const ids = process.argv.slice(2);
  for (const id of ids) {
    const m = man.models.find(x => x.id === id);
    const art = slots[id].front_art.bounds_mm.map(v => v.map(x => x / 1000));
    for (const yaw of [20]) {
      const t0 = Date.now();
      const r = await p.evaluate(([u, o]) => window.renderAngle(u, o), ['/v6/' + m.file, { size: 1024, yaw, pitch: 6, expo: 0.6, art }]);
      const a = fix(r.p0, r.size), c = fix(r.p1, r.size);
      await sharp(c, { raw: { width: r.size, height: r.size, channels: 4 } }).png().toFile(`/tmp/claude-0/r3d/out_${id}_${yaw}.png`);
      await sharp(a, { raw: { width: r.size, height: r.size, channels: 4 } }).png().toFile(`/tmp/claude-0/r3d/out0_${id}_${yaw}.png`);
      console.log(id, yaw, Date.now() - t0, 'ms', JSON.stringify(r.quad.map(v => Math.round(v))));
    }
  }
  await b.close(); srv.close();
})();
