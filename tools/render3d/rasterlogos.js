// Update 166: the Cross menu's console logos baked as PNGs (assets/platform_logos_png/<key>.png, longest side 384 px),
// so the app decodes them at once instead of the SVGs through Coil (slow, and they popped in again whenever the
// image cache let them go).
const { chromium } = require('/opt/npm-tools/node_modules/playwright');
const fs = require('fs');
const SRC = process.argv[2], OUT = process.argv[3], MAX = 384;
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  for (const f of fs.readdirSync(SRC).filter(f => f.endsWith('.svg'))) {
    const svg = fs.readFileSync(SRC + '/' + f, 'utf8');
    const url = 'data:image/svg+xml;base64,' + Buffer.from(svg).toString('base64');
    const png = await p.evaluate(async ([u, MAX]) => {
      const img = new Image(); img.src = u; await img.decode();
      let w = img.naturalWidth || 300, h = img.naturalHeight || 150;
      const k = MAX / Math.max(w, h); w = Math.max(1, Math.round(w * k)); h = Math.max(1, Math.round(h * k));
      const c = document.createElement('canvas'); c.width = w; c.height = h;
      const g = c.getContext('2d'); g.imageSmoothingQuality = 'high'; g.drawImage(img, 0, 0, w, h);
      return c.toDataURL('image/png');
    }, [url, MAX]);
    fs.writeFileSync(OUT + '/' + f.replace('.svg', '.png'), Buffer.from(png.split(',')[1], 'base64'));
  }
  await b.close();
})();
