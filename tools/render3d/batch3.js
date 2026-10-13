// Update 166: the "3D models" pack all the way round — every piece from 18 angles (−160 … 180, every 20°), so a piece
// turned in the inspect view shows its real sides and back, in its own colours (the custom / clear ones painted on every
// side). Made from your library's v6.11 and, where there is one, its blank-branding variant (no native lettering; a plain
// badge the app prints the console's logo on). Same files as batch2.js (update 137), per angle:
//   <yaw>.webp, <yaw>_g.webp (only where the front art faces you), and for paintable pieces <yaw>_t<level>, <yaw>_b,
//   <yaw>_gw (clear). meta.json v3: "yaws" lists the angles; "angles" (the art window) and tint.logo only where they show.
// The real cartridges / cards are paintable too now, in their own colours (tint.own, like the Save Vault's cards): only
// their blank badge gets the console's logo.
const { srv, fix, sharp, chromium } = require('./drive.js');
const fs = require('fs');
const A = '/mnt/user-data/uploads/all';
const V = A + '/VoltCap_v6_Refined/v6';
const BB = A + '/VoltCap_Blank_Branding_Variants';
const ANGLES = (process.env.ANGLES || '-160,-140,-120,-100,-80,-60,-40,-20,0,20,40,60,80,100,120,140,160,180').split(',').map(Number);
const SIZE = 1024, LONG = 600, PITCH = 6, EXPO = 0.58;
const OUT = process.env.OUT || '/tmp/claude-0/r3d166/pack611';
const PORT = +(process.env.PORT || 8767);
const lin = (u) => u <= 0.04045 ? u / 12.92 : Math.pow((u + 0.055) / 1.055, 2.4);
const MID = lin(0.5);
// The front (art window, logo plate) faces you: within ±80°.
const faces = (yaw) => Math.cos(yaw * Math.PI / 180) > 0.15;
(async () => {
  await new Promise(r => srv.listen(PORT, r));
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const slots = JSON.parse(fs.readFileSync(V + '/artwork_slots.json'));
  const cards = JSON.parse(fs.readFileSync(V + '/branding/card_slots.json'));
  const bcards = JSON.parse(fs.readFileSync(BB + '/branding/card_slots.json'));
  const man = JSON.parse(fs.readFileSync(V + '/manifest.json'));
  const plan = JSON.parse(fs.readFileSync(__dirname + '/glbplan.json'));
  const only = process.argv.slice(2);
  for (const id of Object.keys(plan)) {
    if (only.length && !only.includes(id)) continue;
    const outDir = `${OUT}/${id}`;
    if (fs.existsSync(outDir + '/meta.json')) continue;
    const file = plan[id], blank = file.includes('blank_branding');
    const card = (blank && (bcards[id + '_blank_branding'] || bcards[id])) || cards[id];
    const label = (man.models.find(m => m.id === id) || {}).label || id;
    const real = id.endsWith('_real');
    const tint = !!card;
    // The Save Vault's cards and the real pieces keep their own colours (the app prints the console logo on them).
    const spare = id.startsWith('memory_card') || id.startsWith('sd_card');
    const own = spare || real;
    const clear = id.endsWith('_translucent');
    const p = await b.newPage(); p.on('pageerror', e => console.log('err', e.message));
    await p.goto('http://127.0.0.1:' + PORT + '/r/render.html'); await p.waitForFunction(() => window.ready);
    const sl = slots[id];
    const mm = (bb) => bb ? bb.map(v => v.map(x => x / 1000)) : null;
    const art = mm(sl.front_art.bounds_mm);
    const hide = ['console_logo', 'console_name'];
    const passes = [{ art: 0 }, { art: 0.9 }];
    const LEVELS = clear ? [0, 0.25, 0.5, 0.75, 1] : [0, 0.5];
    // The Save Vault's cards only sway within ±60° (SwayingModel): the seven angles there are enough (SPARE_ANGLES).
    const ANG = spare && process.env.SPARE_ANGLES ? process.env.SPARE_ANGLES.split(',').map(Number) : ANGLES;
    if (tint) {
      for (const u of LEVELS) passes.push({ art: 0, tint: lin(u), badge: lin(u), hide });
      passes.push({ art: 0, tint: MID, badge: 0, hide });
      passes.push({ art: 0, tint: MID, badge: MID, hide: ['console_name'], logoWhite: true });
      if (clear) passes.push({ art: 0.9, tint: 1, badge: 1, hide });
    }
    const renders = [];
    let x0 = SIZE, y0 = SIZE, x1 = 0, y1 = 0, trans = 0, mats = {}, logoBox = null;
    for (const yaw of ANG) {
      const r = await p.evaluate(([u, o]) => window.renderAngle(u, o), ['/all/' + file, { size: SIZE, yaw, pitch: PITCH, expo: EXPO, art, logoNode: tint ? 'console_logo' : null, passes }]);
      const px = r.px.map(s => fix(s, SIZE));
      trans = r.trans; mats = r.mats; logoBox = r.logoBox;
      const a = px[0];
      for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) if (a[(y * SIZE + x) * 4 + 3] > 2) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
      renders.push({ yaw, px, quad: r.quad, logo: r.logo });
    }
    await p.close();
    x0 = Math.max(0, x0 - 6); y0 = Math.max(0, y0 - 6); x1 = Math.min(SIZE - 1, x1 + 6); y1 = Math.min(SIZE - 1, y1 + 6);
    const cw = x1 - x0 + 1, ch = y1 - y0 + 1; const k = LONG / Math.max(cw, ch);
    const W = Math.round(cw * k), H = Math.round(ch * k);
    fs.mkdirSync(outDir, { recursive: true });
    const meta = { v: 3, id, label, w: W, h: H, art: sl.front_art.image_aspect_ratio, yaws: ANG, angles: {} };
    const logoAspect = logoBox ? (logoBox[1][0] - logoBox[0][0]) / Math.max(1e-6, logoBox[1][1] - logoBox[0][1]) : 0;
    if (tint) meta.tint = { sys: card.default_system, clear, trans: +trans.toFixed(4), logo: {}, ls: {}, logoAspect: +logoAspect.toFixed(4), levels: LEVELS.map(u => Math.round(u * 100)) };
    const hex = (c) => '#' + c.slice(0, 3).map(v => { const u = v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055; return Math.round(Math.min(1, Math.max(0, u)) * 255).toString(16).padStart(2, '0'); }).join('');
    if (tint && own) { const sh = mats.molded_polymer || mats.translucent_tinted_polycarbonate || [0.5, 0.5, 0.5]; meta.tint.own = [hex(sh), hex(mats.console_badge_polymer || mats.blank_branding_label || [0.85, 0.87, 0.89])]; }
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
    const lum = (bf, i) => 0.3 * bf[i] + 0.59 * bf[i + 1] + 0.11 * bf[i + 2];
    for (const r of renders) {
      const [a, c] = r.px;
      const front = faces(r.yaw);
      await crop(a).webp({ quality: 88, alphaQuality: 100, effort: 5 }).toFile(`${outDir}/${r.yaw}.webp`);
      if (front) {
        await crop(gainOf(c, a)).webp({ quality: 85, alphaQuality: 90, effort: 5 }).toFile(`${outDir}/${r.yaw}_g.webp`);
        meta.angles[r.yaw] = norm(r.quad);
      }
      if (!tint) continue;
      const T = r.px.slice(2, 2 + LEVELS.length), tb = r.px[2 + LEVELS.length], tl = r.px[3 + LEVELS.length];
      const t0 = T[0], tm = T[LEVELS.indexOf(0.5)];
      const bw = Buffer.alloc(t0.length);
      for (let i = 0; i < t0.length; i += 4) {
        const all = lum(tm, i) - lum(t0, i), badge = lum(tm, i) - lum(tb, i);
        const w = all > 3 ? Math.round(255 * Math.min(1, Math.max(0, badge / all))) : (badge > 3 ? 255 : 0);
        bw[i] = bw[i + 1] = bw[i + 2] = w; bw[i + 3] = 255;
      }
      for (let li = 0; li < LEVELS.length; li++)
        await crop(T[li]).webp({ quality: 90, alphaQuality: 100, effort: 5 }).toFile(`${outDir}/${r.yaw}_t${Math.round(LEVELS[li] * 100)}.webp`);
      await crop(bw).webp({ quality: 90, effort: 5 }).toFile(`${outDir}/${r.yaw}_b.webp`);
      if (clear && front) await crop(gainOf(r.px[4 + LEVELS.length], T[LEVELS.length - 1])).webp({ quality: 85, alphaQuality: 90, effort: 5 }).toFile(`${outDir}/${r.yaw}_gw.webp`);
      if (front && r.logo) {
        let sum = 0, n = 0;
        for (let i = 0; i < tl.length; i += 4) if (Math.abs(lum(tl, i) - lum(tm, i)) > 20 && tl[i + 3] > 200) { sum += lum(tl, i); n++; }
        meta.tint.ls[r.yaw] = +(n ? sum / n / 255 : 0.8).toFixed(3);
        meta.tint.logo[r.yaw] = norm(r.logo);
      }
    }
    fs.writeFileSync(outDir + '/meta.json', JSON.stringify(meta));
    console.log(new Date().toISOString(), id, W + 'x' + H, tint ? (own ? 'own' : 'tint') : '', blank ? 'blank' : '');
  }
  await b.close(); srv.close();
})();
