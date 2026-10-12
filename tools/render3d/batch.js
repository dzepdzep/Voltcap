const { srv, fix, sharp, chromium } = require('./drive.js');
const fs = require('fs');
const ANGLES = [-60, -40, -20, 0, 20, 40, 60];
const SIZE = 1024, LONG = 600, PITCH = 6, EXPO = 0.58;
(async () => {
  await new Promise(r => srv.listen(8766, r));
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const slots = JSON.parse(fs.readFileSync('/mnt/user-data/uploads/v6/artwork_slots.json'));
  const man = JSON.parse(fs.readFileSync('/mnt/user-data/uploads/v6/manifest.json'));
  const only = process.argv.slice(2);
  for (const m of man.models) {
    if (only.length && !only.includes(m.id)) continue;
    const outDir = `/tmp/claude-0/r3d/pack/${m.id}`;
    if (fs.existsSync(outDir + '/meta.json')) continue;
    const p = await b.newPage(); p.on('pageerror', e => console.log('err', e.message));
    await p.goto('http://127.0.0.1:8766/r/render.html'); await p.waitForFunction(() => window.ready);
    const slot = slots[m.id].front_art;
    const art = slot.bounds_mm.map(v => v.map(x => x / 1000));
    const renders = [];
    let x0 = SIZE, y0 = SIZE, x1 = 0, y1 = 0;
    for (const yaw of ANGLES) {
      const r = await p.evaluate(([u, o]) => window.renderAngle(u, o), ['/v6/' + m.file, { size: SIZE, yaw, pitch: PITCH, expo: EXPO, art, white: 0.9 }]);
      const a = fix(r.p0, SIZE), c = fix(r.p1, SIZE);
      for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) if (a[(y * SIZE + x) * 4 + 3] > 2) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
      renders.push({ yaw, a, c, quad: r.quad });
    }
    await p.close();
    x0 = Math.max(0, x0 - 6); y0 = Math.max(0, y0 - 6); x1 = Math.min(SIZE - 1, x1 + 6); y1 = Math.min(SIZE - 1, y1 + 6);
    const cw = x1 - x0 + 1, ch = y1 - y0 + 1; const k = LONG / Math.max(cw, ch);
    const W = Math.round(cw * k), H = Math.round(ch * k);
    fs.mkdirSync(outDir, { recursive: true });
    const meta = { v: 1, id: m.id, label: m.label, w: W, h: H, art: slot.image_aspect_ratio, angles: {} };
    for (const r of renders) {
      // gain = what the front art adds per unit of cover brightness; its alpha = where the art shows
      const g = Buffer.alloc(r.a.length);
      for (let i = 0; i < r.a.length; i += 4) {
        let mx = 0;
        for (let ch2 = 0; ch2 < 3; ch2++) { const d = Math.max(0, r.c[i + ch2] - r.a[i + ch2]); g[i + ch2] = d; if (d > mx) mx = d; }
        g[i + 3] = Math.min(255, mx * 12) * (r.a[i + 3] / 255);
      }
      const crop = (buf) => sharp(buf, { raw: { width: SIZE, height: SIZE, channels: 4 } }).extract({ left: x0, top: y0, width: cw, height: ch }).resize(W, H, { kernel: 'lanczos3' });
      await crop(r.a).webp({ quality: 88, alphaQuality: 100, effort: 5 }).toFile(`${outDir}/${r.yaw}.webp`);
      await crop(g).webp({ quality: 85, alphaQuality: 90, effort: 5 }).toFile(`${outDir}/${r.yaw}_g.webp`);
      meta.angles[r.yaw] = r.quad.map((v, i) => +(i % 2 === 0 ? (v - x0) / cw : (v - y0) / ch).toFixed(5));
    }
    fs.writeFileSync(outDir + '/meta.json', JSON.stringify(meta));
    console.log(new Date().toISOString(), m.id, W + 'x' + H);
  }
  await b.close(); srv.close();
})();
