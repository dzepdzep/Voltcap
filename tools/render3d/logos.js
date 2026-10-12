const { sharp } = require('./drive.js'); const fs = require('fs');
const B = '/mnt/user-data/uploads/VoltCap_3D_Shape_Library_v6_8_Complete--VoltCap_v6_Refined--v6/branding';
(async () => {
  const sys = JSON.parse(fs.readFileSync(B + '/systems.json')).systems; const out = {};
  for (const s of sys) out[s.system_id] = [s.shell_color_srgb, s.badge_color_srgb];
  fs.writeFileSync('logos68/logos/systems.json', JSON.stringify(out));
  for (const f of fs.readdirSync(B + '/logos')) {
    await sharp(B + '/logos/' + f).resize({ width: 640, height: 320, fit: 'inside', withoutEnlargement: true }).webp({ quality: 92, alphaQuality: 100, effort: 6 }).toFile('logos68/logos/' + f.replace('.png', '.webp'));
  }
})();
