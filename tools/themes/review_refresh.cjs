// Render every theme, then make contact sheets for visual review.
// Update 427: every scene gen.py knows (it stopped at 150), three moments of each (t = 2, 9, 17 s: one still frame hides
// motion problems), and a "home" state with a mock launcher on top (a row of covers, the focused one ringed, its title,
// a status bar and a button row) — a fine background can still be unusable behind the real interface.
//   node tools/themes/review_refresh.cjs   (after python tools/themes/gen.py)
const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const TIMES = [2, 9, 17];
(async () => {
  const browser = await chromium.launch({headless: true,
    executablePath: process.env.CHROME_EXECUTABLE,
    args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--allow-file-access-from-files']});
  try {
    const page = await browser.newPage();
    await page.goto(pathToFileURL(path.join(__dirname, 'preview.html')).href + '?theme=VOLT&t=8&w=480&h=270');
    await page.waitForFunction(() => document.title === 'done' || document.title === 'error', null, {timeout: 120000});
    const frames = await page.evaluate(async (times) => { await window.READY; const out = []; for (const theme of DATA.themes) {
      const shots = [], errors = [], grabs = [];
      for (const t of times) {
        try { draw(theme, t, 480, 270); const e = gl.getError(); if (e) errors.push('GL error ' + e); shots.push(document.getElementById('gl').toDataURL());
          const px = new Uint8Array(480 * 270 * 4); gl.readPixels(0, 0, 480, 270, gl.RGBA, gl.UNSIGNED_BYTE, px); grabs.push(px); }
        catch (e) { errors.push(String(e.message || e).slice(0, 160)); shots.push(null); }
      }
      // Numbers behind the review (update 427): the title zone's light (top-left, where home titles sit; GL rows go up from
      // the bottom), the picture's detail (luminance spread), and how much it moves between the moments.
      const L = (a, i) => (0.2126 * a[i] + 0.7152 * a[i + 1] + 0.0722 * a[i + 2]) / 255;
      let m = {};
      if (grabs.length === times.length) {
        let tz = 0, tn = 0, sum = 0, sq = 0, n = 0, mv = 0;
        const a = grabs[1];
        for (let y = 0; y < 270; y += 2) for (let x = 0; x < 480; x += 2) { const i = (y * 480 + x) * 4; const l = L(a, i); sum += l; sq += l * l; n++;
          if (y > 270 * 0.62 && x < 480 * 0.6) { tz += l; tn++; }
          mv += Math.abs(l - L(grabs[0], i)) + Math.abs(l - L(grabs[2], i)); }
        const mean = sum / n;
        m = { titleZone: +(tz / tn).toFixed(3), mean: +mean.toFixed(3), detail: +Math.sqrt(Math.max(0, sq / n - mean * mean)).toFixed(3), motion: +(mv / n / 2).toFixed(4) };
      }
      out.push({ name: theme.name, label: theme.label, motif: theme.motif, colors: theme.colors, shots, errors, metrics: m });
    } return out; }, TIMES);
    const out = path.join(__dirname, 'refresh-review');
    fs.mkdirSync(out, {recursive: true});
    for (const f of fs.readdirSync(out)) if (/^sheet-\d+\.(png|jpg)$/.test(f)) fs.unlinkSync(path.join(out, f));
    const css = (c) => 'rgb(' + c.map(v => Math.round(v * 255)).join(',') + ')';
    // The home state: the 2nd moment with a mock interface in the theme's own colours on top.
    const home = f => `<div class="home"><img src="${f.shots[1] || ''}"><div class="bar" style="color:${css(f.colors[2])}">17:26 · 82%</div>
      <div class="row">${[0,1,2,3,4].map(i => `<i class="${i===1?'on':''}" style="${i===1?'outline-color:'+css(f.colors[2]):''};background:hsl(${(i*61+200)%360},45%,40%)"></i>`).join('')}</div>
      <div class="title">A Long Game Title That Wraps<br><span>Console · 12 h played</span></div>
      <div class="keys"><b style="background:${css(f.colors[2])}">Play</b><b>Details</b></div></div>`;
    const gallery = await browser.newPage({viewport: {width: 1600, height: 1100}});
    const PER = 6;
    for (let i = 0; i < frames.length; i += PER) {
      await gallery.setContent(`<style>body{margin:0;padding:12px;background:#0c1018;color:#eee;font:13px sans-serif}
        .t{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-bottom:14px}img{width:100%;border-radius:6px;display:block}
        h3{margin:4px 0;font-size:14px}.err{color:#ff7b7b}.home{position:relative}.home .bar{position:absolute;top:4px;right:8px;font:bold 11px sans-serif;text-shadow:0 1px 2px #000}
        .home .row{position:absolute;left:6%;right:6%;top:42%;display:flex;gap:4%}.home i{flex:1;aspect-ratio:3/4;border-radius:4px}.home i.on{outline:3px solid;outline-offset:2px;transform:scale(1.08)}
        .home .title{position:absolute;left:6%;top:16%;font:bold 15px sans-serif;color:#fff;text-shadow:0 1px 3px #000}.home .title span{font:11px sans-serif;opacity:.8}
        .home .keys{position:absolute;left:6%;bottom:8%;display:flex;gap:6px}.home b{padding:3px 12px;border-radius:12px;background:rgba(255,255,255,.18);color:#fff;font:bold 11px sans-serif}</style>` +
        frames.slice(i, i + PER).map(f => `<h3>${f.label} <small>(${f.name} · ${f.motif})</small>${f.errors.length ? ' <span class="err">' + f.errors[0] + '</span>' : ''}</h3>
          <div class="t">${f.shots.map(s => s ? `<img src="${s}">` : '<div class="err">no frame</div>').join('')}${home(f)}</div>`).join(''));
      await gallery.screenshot({path: path.join(out, `sheet-${1 + i / PER}.jpg`), fullPage: true, type: 'jpeg', quality: 72});
    }
    fs.writeFileSync(path.join(out, 'coverage.json'), JSON.stringify(frames.map(({name, label, motif, errors, metrics}) => ({name, label, motif, times: TIMES, errors, metrics})), null, 1));
    const bad = frames.filter(f => f.errors.length);
    console.log(`Rendered ${frames.length} themes x ${TIMES.length} moments + home state; ${bad.length} with errors${bad.length ? ': ' + bad.map(f => f.name).join(', ') : ''}.`);
    if (bad.length) process.exitCode = 1;
  } finally { await browser.close(); }
})().catch(e => {console.error(e); process.exitCode = 1;});
