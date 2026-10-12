// Update 137: VoltCap 3D Shape Library v6.8 → the "3D models" pack.
// Every model: <yaw>.webp (as the library has it, front art dark) + <yaw>_g.webp (the front art's light).
// Custom cartridges / cards (tintable): also the shell in black / mid / white (<yaw>_t0, _tm, _t1; _t1's alpha = where
// the console badge is), no logo, so the app can colour the shell and badge and print any console's logo.
const { srv, fix, sharp, chromium } = require('./drive.js');
const fs = require('fs');
const V = '/mnt/user-data/uploads/VoltCap_3D_Shape_Library_v6_8_Complete--VoltCap_v6_Refined--v6';
const ANGLES = (process.env.ANGLES || '-60,-40,-20,0,20,40,60').split(',').map(Number);
const SIZE = 1024, LONG = 600, PITCH = 6, EXPO = 0.58;
const OUT = process.env.OUT || '/tmp/claude-0/r3d/pack68';
const lin = (u) => u <= 0.04045 ? u / 12.92 : Math.pow((u + 0.055) / 1.055, 2.4);
const MID = lin(0.5);
(async () => {
  await new Promise(r => srv.listen(+(process.env.PORT || 8767), r));
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const slots = JSON.parse(fs.readFileSync(V + '/artwork_slots.json'));
  const cards = JSON.parse(fs.readFileSync(V + '/branding/card_slots.json'));
  const man = JSON.parse(fs.readFileSync(V + '/manifest.json'));
  const only = process.argv.slice(2);
  for (const m of man.models) {
    if (only.length && !only.includes(m.id)) continue;
    const outDir = `${OUT}/${m.id}`;
    if (fs.existsSync(outDir + '/meta.json')) continue;
    const tint = !!cards[m.id] && !m.id.endsWith('_real');
    // The Save Vault's cards keep their own colours (the app prints the save's console logo on them).
    const spare = m.id.startsWith('memory_card') || m.id.startsWith('sd_card');
    const clear = m.id.endsWith('_translucent');
    const p = await b.newPage(); p.on('pageerror', e => console.log('err', e.message));
    await p.goto('http://127.0.0.1:' + (process.env.PORT || 8767) + '/r/render.html'); await p.waitForFunction(() => window.ready);
    const sl = slots[m.id];
    const mm = (bb) => bb ? bb.map(v => v.map(x => x / 1000)) : null;
    const art = mm(sl.front_art.bounds_mm);
    const logo = tint && sl.console_logo ? mm(sl.console_logo.bounds_mm) : null;
    const hide = ['console_logo', 'console_name'];
    const passes = [{ art: 0 }, { art: 0.9 }];
    // Shell (and badge) samples, as sRGB levels: opaque shells need two (the app undoes the tone curve), clear ones five.
    const LEVELS = clear ? [0, 0.25, 0.5, 0.75, 1] : [0, 0.5];
    if (tint) {
      for (const u of LEVELS) passes.push({ art: 0, tint: lin(u), badge: lin(u), hide });       // 2.. t<level>
      passes.push({ art: 0, tint: MID, badge: 0, hide });                                       // badge black (→ where the badge is)
      passes.push({ art: 0, tint: MID, badge: MID, hide: ['console_name'], logoWhite: true });  // the logo's light
      if (clear) passes.push({ art: 0.9, tint: 1, badge: 1, hide });                            // art behind white plastic
    }
    const renders = [];
    let x0 = SIZE, y0 = SIZE, x1 = 0, y1 = 0, trans = 0, tbase = null, mats = {};
    for (const yaw of ANGLES) {
      const r = await p.evaluate(([u, o]) => window.renderAngle(u, o), ['/v68/' + m.file, { size: SIZE, yaw, pitch: PITCH, expo: EXPO, art, logo, passes }]);
      const px = r.px.map(s => fix(s, SIZE));
      trans = r.trans; tbase = r.tbase; mats = r.mats;
      const a = px[0];
      for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) if (a[(y * SIZE + x) * 4 + 3] > 2) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
      renders.push({ yaw, px, quad: r.quad, logo: r.logo });
    }
    await p.close();
    x0 = Math.max(0, x0 - 6); y0 = Math.max(0, y0 - 6); x1 = Math.min(SIZE - 1, x1 + 6); y1 = Math.min(SIZE - 1, y1 + 6);
    const cw = x1 - x0 + 1, ch = y1 - y0 + 1; const k = LONG / Math.max(cw, ch);
    const W = Math.round(cw * k), H = Math.round(ch * k);
    fs.mkdirSync(outDir, { recursive: true });
    const meta = { v: 2, id: m.id, label: m.label, w: W, h: H, art: sl.front_art.image_aspect_ratio, angles: {} };
    if (tint) meta.tint = { sys: cards[m.id].default_system, clear, trans: +trans.toFixed(4), logo: {}, ls: {},
      logoAspect: sl.console_logo ? sl.console_logo.image_aspect_ratio : 0, levels: LEVELS.map(u => Math.round(u * 100)) };
    const hex = (c) => '#' + c.slice(0, 3).map(v => { const u = v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055; return Math.round(Math.min(1, Math.max(0, u)) * 255).toString(16).padStart(2, '0'); }).join('');
    if (tint && spare) { const sh = mats.molded_polymer || mats.translucent_tinted_polycarbonate; meta.tint.own = [hex(sh), hex(mats.console_badge_polymer || [0.85, 0.87, 0.89])]; }
    const norm = (q) => q.map((v, i) => +(i % 2 === 0 ? (v - x0) / cw : (v - y0) / ch).toFixed(5));
    const gainOf = (lit, dark) => {
      const g = Buffer.alloc(lit.length);
      for (let i = 0; i < lit.length; i += 4) {
        let mx = 0;
        for (let c = 0; c < 3; c++) { const d = Math.max(0, lit[i + c] - dark[i + c]); g[i + c] = d; if (d > mx) mx = d; }
        g[i + 3] = Math.min(255, mx * 12) * (dark[i + 3] / 255);
      }
      return g;
    };
    const crop = (buf) => sharp(buf, { raw: { width: SIZE, height: SIZE, channels: 4 } }).extract({ left: x0, top: y0, width: cw, height: ch }).resize(W, H, { kernel: 'lanczos3' });
    for (const r of renders) {
      const [a, c] = r.px;
      await crop(a).webp({ quality: 88, alphaQuality: 100, effort: 5 }).toFile(`${outDir}/${r.yaw}.webp`);
      await crop(gainOf(c, a)).webp({ quality: 85, alphaQuality: 90, effort: 5 }).toFile(`${outDir}/${r.yaw}_g.webp`);
      meta.angles[r.yaw] = norm(r.quad);
      if (!tint) continue;
      const T = r.px.slice(2, 2 + LEVELS.length), tb = r.px[2 + LEVELS.length], tl = r.px[3 + LEVELS.length];
      const t0 = T[0], tm = T[LEVELS.indexOf(0.5)];
      // _b: how much of each pixel is the console badge (what goes dark when the badge alone goes black).
      const bw = Buffer.alloc(t0.length);
      const lum = (bf, i) => 0.3 * bf[i] + 0.59 * bf[i + 1] + 0.11 * bf[i + 2];
      for (let i = 0; i < t0.length; i += 4) {
        const all = lum(tm, i) - lum(t0, i), badge = lum(tm, i) - lum(tb, i);
        const w = all > 3 ? Math.round(255 * Math.min(1, Math.max(0, badge / all))) : (badge > 3 ? 255 : 0);
        bw[i] = bw[i + 1] = bw[i + 2] = w; bw[i + 3] = 255;
      }
      for (let li = 0; li < LEVELS.length; li++)
        await crop(T[li]).webp({ quality: 90, alphaQuality: 100, effort: 5 }).toFile(`${outDir}/${r.yaw}_t${Math.round(LEVELS[li] * 100)}.webp`);
      await crop(bw).webp({ quality: 90, effort: 5 }).toFile(`${outDir}/${r.yaw}_b.webp`);
      if (clear) await crop(gainOf(r.px[4 + LEVELS.length], T[LEVELS.length - 1])).webp({ quality: 85, alphaQuality: 90, effort: 5 }).toFile(`${outDir}/${r.yaw}_gw.webp`);
      // The logo's light: the white logo's brightness where it shows.
      let sum = 0, n = 0;
      for (let i = 0; i < tl.length; i += 4) if (Math.abs(lum(tl, i) - lum(tm, i)) > 20 && tl[i + 3] > 200) { sum += lum(tl, i); n++; }
      meta.tint.ls[r.yaw] = +(n ? sum / n / 255 : 0.8).toFixed(3);
      if (r.logo) meta.tint.logo[r.yaw] = norm(r.logo);
    }
    fs.writeFileSync(outDir + '/meta.json', JSON.stringify(meta));
    console.log(new Date().toISOString(), m.id, W + 'x' + H, tint ? 'tint' : '');
  }
  await b.close(); srv.close();
})();
