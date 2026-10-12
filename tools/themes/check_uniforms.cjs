// Node alternative to check_uniforms.py. PLAYWRIGHT_MODULE can point to a bundled installation.
const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
(async () => {
  const source = path.resolve(process.argv[2] || 'src_copy');
  const kt = fs.readFileSync(path.join(source, 'ui/ThemeShaders.kt'), 'utf8');
  const block = kt.slice(kt.indexOf('fun uniforms')).split('\n    }')[0];
  const ours = Object.fromEntries([...block.matchAll(/ThemeMotif\.(\w+) -> (.*)/g)].map(m => [m[1], [...m[2].matchAll(/"(\w+)"/g)].map(x => x[1])]));
  const browser = await chromium.launch({ headless: true, ...(process.env.CHROME_EXECUTABLE ? { executablePath: process.env.CHROME_EXECUTABLE } : {}), args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  await page.goto(pathToFileURL(path.join(__dirname, 'preview.html')).href);
  const results = await page.evaluate(() => Object.fromEntries(Object.keys(DATA.scenes).map(m => {
    try {
      const p = program(m), n = gl.getProgramParameter(p, gl.ACTIVE_UNIFORMS);
      return [m, Array.from({length:n}, (_,i) => gl.getActiveUniform(p,i).name.replace(/Tex$/, '').replace(/\[0\]$/, ''))];
    } catch(e) { return [m, String(e)]; }
  })));
  let failures = 0;
  for (const [motif, uniforms] of Object.entries(results)) {
    if (!Array.isArray(uniforms)) { console.error(motif, uniforms); failures++; continue; }
    const extra = (ours[motif] || []).filter(u => !uniforms.includes(u));
    const missing = uniforms.filter(u => !(ours[motif] || []).includes(u) && !['uArt','uCover','uLabel','uHistory','uShot'].includes(u));
    if (extra.length || missing.length) { console.error(motif, {extra, missing}); failures++; }
  }
  await page.screenshot({ path: path.join(__dirname, 'verified-themes.png'), fullPage: false });
  await browser.close();
  console.log(`Checked ${Object.keys(results).length} scenes; ${failures} failures.`);
  process.exitCode = failures ? 1 : 0;
})().catch(e => { console.error(e); process.exitCode = 1; });
