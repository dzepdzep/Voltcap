const { srv, fix, sharp, chromium } = require('./drive.js');
const fs = require('fs');
const V = '/mnt/user-data/uploads/VoltCap_3D_Shape_Library_v6_8_Complete--VoltCap_v6_Refined--v6';
const lin = (u) => u <= 0.04045 ? u / 12.92 : Math.pow((u + 0.055) / 1.055, 2.4);
const hex = (h) => [1, 3, 5].map(i => lin(parseInt(h.slice(i, i + 2), 16) / 255));
(async () => {
  await new Promise(r => srv.listen(8768, r));
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const man = JSON.parse(fs.readFileSync(V + '/manifest.json'));
  const [id, yaw, shell, badge] = process.argv.slice(2);
  const m = man.models.find(x => x.id === id);
  const p = await b.newPage(); p.on('pageerror', e => console.log('err', e.message));
  await p.goto('http://127.0.0.1:8768/r/render.html'); await p.waitForFunction(() => window.ready);
  const r = await p.evaluate(([u, o]) => window.renderAngle(u, o), ['/v68/' + m.file, { size: 1024, yaw: +yaw, pitch: 6, expo: 0.58, passes: [{ art: 0 }, { art: 0, tint: hex(shell), badge: hex(badge), hide: ['console_logo', 'console_name'] }] }]);
  const SIZE = 1024, a = fix(r.px[0], SIZE), t = fix(r.px[1], SIZE);
  let x0 = SIZE, y0 = SIZE, x1 = 0, y1 = 0;
  for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) if (a[(y * SIZE + x) * 4 + 3] > 2) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
  x0 = Math.max(0, x0 - 6); y0 = Math.max(0, y0 - 6); x1 = Math.min(SIZE - 1, x1 + 6); y1 = Math.min(SIZE - 1, y1 + 6);
  const cw = x1 - x0 + 1, ch = y1 - y0 + 1; const k = 600 / Math.max(cw, ch);
  await sharp(t, { raw: { width: SIZE, height: SIZE, channels: 4 } }).extract({ left: x0, top: y0, width: cw, height: ch }).resize(Math.round(cw * k), Math.round(ch * k), { kernel: 'lanczos3' }).png().toFile(`truth_${id}.png`);
  await b.close(); srv.close();
})();
