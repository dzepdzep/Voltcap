// Render every theme, then make contact sheets for visual review.
const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
(async () => {
  const browser = await chromium.launch({headless: true,
    executablePath: process.env.CHROME_EXECUTABLE,
    args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader']});
  try {
    const page = await browser.newPage();
    await page.goto(pathToFileURL(path.join(__dirname, 'preview.html')).href + '?theme=VOLT&t=8&w=480&h=270');
    const frames = await page.evaluate(() => DATA.themes.map(theme => {
      draw(theme, 8, 480, 270);
      const error = gl.getError();
      if (error) throw new Error(theme.name + ': GL error ' + error);
      return { name: theme.name, label: theme.label, image: document.getElementById('gl').toDataURL() };
    }));
    const out = path.join(__dirname, 'refresh-review');
    fs.mkdirSync(out, {recursive: true});
    const gallery = await browser.newPage({viewport: {width: 1440, height: 1100}});
    for (let i = 0; i < frames.length; i += 24) {
      await gallery.setContent('<style>body{margin:0;padding:16px;background:#0c1018;color:#eee;font:14px sans-serif;display:grid;grid-template-columns:repeat(4,1fr);gap:12px}figure{margin:0}img{width:100%;border-radius:8px}figcaption{padding:6px}</style>' +
        frames.slice(i,i+24).map(f => `<figure><img src="${f.image}"><figcaption>${f.label}</figcaption></figure>`).join(''));
      await gallery.screenshot({path: path.join(out, `sheet-${1+i/24}.png`), fullPage: true});
    }
    fs.writeFileSync(path.join(out, 'coverage.json'), JSON.stringify(frames.map(({name,label}) => ({name,label})), null, 2));
    console.log(`Rendered ${frames.length} theme previews without WebGL errors.`);
  } finally { await browser.close(); }
})().catch(e => {console.error(e); process.exitCode = 1;});
