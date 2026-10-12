const { chromium } = require('/opt/node-tools/node_modules/playwright');
const fs = require('fs');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 960, height: 540 } });
  await p.goto('file://' + __dirname + '/' + (process.env.PAGE || 'intro.html'));
  const mode = process.argv[2] || 'stills';
  const dur = +(process.env.DUR || 2750);
  fs.mkdirSync(__dirname + '/frames', { recursive: true });
  const times = mode === 'stills' ? JSON.parse(process.env.STILLS || '[100,400]')
    : [...Array(Math.ceil(dur / 1000 * 30))].map((_, i) => i * 1000 / 30);
  let i = 0;
  for (const t of times) {
    const d = await p.evaluate((t) => window.render(t, false), t);
    const name = mode === 'stills' ? 's_' + t : 'f_' + String(i).padStart(4, '0');
    fs.writeFileSync(`${__dirname}/frames/${name}.png`, Buffer.from(d.split(',')[1], 'base64'));
    i++;
  }
  await b.close();
})();
