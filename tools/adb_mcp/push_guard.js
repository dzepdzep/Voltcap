// Sprint 525 (owner: "verify that all necessary secrets and sensitive credentials are excluded from GitHub pushes"):
// what git_push_branch refuses to push. Before 525 it read only secret_denylist.txt (one value) while the APK check
// (app/build.gradle.kts verifyNoEmbeddedSecrets) also reads screenscraper.properties and igdb.properties. Now one list:
//   - every secret value the APK check knows, plus the signing passwords (keystore.properties) and the Worker's local
//     secrets (server/voltcap-api/.dev.vars), searched in EVERY commit being pushed, not just the tip (a value added
//     and removed again would still be in the history);
//   - files that hold secrets may not be in a pushed commit at all, whatever they contain.
// Values are never printed or returned in messages - only where they come from.
'use strict';
const fs = require('fs'); const path = require('path');

function readProps(file) {
  const out = {};
  if (!fs.existsSync(file)) return out;
  for (const raw of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const l = raw.trim();
    if (!l || l.startsWith('#') || l.startsWith('!')) continue;
    const m = l.match(/^([^=:\s]+)\s*[=:]\s*(.*)$/);
    if (m) out[m[1]] = m[2].trim().replace(/^"(.*)"$/, '$1');
  }
  return out;
}

/** The values that may never leave the PC, as [{ from, value }] (from = file › key, never the value). */
function denyValues(root) {
  const out = [];
  const add = (from, v, min) => { if (typeof v === 'string' && v.trim().length >= min) out.push({ from, value: v.trim() }); };
  const ss = readProps(path.join(root, 'screenscraper.properties'));
  add('screenscraper.properties › devPassword', ss.devPassword, 4);
  add('screenscraper.properties › devId', ss.devId, 4);
  const ig = readProps(path.join(root, 'igdb.properties'));
  add('igdb.properties › clientSecret', ig.clientSecret, 4);
  add('igdb.properties › clientId', ig.clientId, 6);
  const ks = readProps(path.join(root, 'keystore.properties'));
  add('keystore.properties › storePassword', ks.storePassword, 4);
  add('keystore.properties › keyPassword', ks.keyPassword, 4);
  const dv = readProps(path.join(root, 'server', 'voltcap-api', '.dev.vars'));
  for (const k of Object.keys(dv)) add('.dev.vars › ' + k, dv[k], 6);
  const deny = path.join(root, 'secret_denylist.txt');
  if (fs.existsSync(deny)) fs.readFileSync(deny, 'utf8').split(/\r?\n/).forEach((l, i) => { if (!l.trim().startsWith('#')) add('secret_denylist.txt line ' + (i + 1), l, 6); });
  // one entry per distinct value
  const seen = new Set();
  return out.filter((e) => (seen.has(e.value) ? false : (seen.add(e.value), true)));
}

/** Files that hold secrets: never in a pushed commit (paths as git prints them, forward slashes). */
const SECRET_FILE = /(^|\/)(keystore\.properties|screenscraper\.properties|igdb\.properties|local\.properties|secret_denylist\.txt|\.dev\.vars|[^/]*\.jks|[^/]*\.keystore|[^/]*\.p12|[^/]*\.pem|google-services\.json|client_secret[^/]*\.json)$/i;
function secretFiles(paths) { return paths.filter((p) => SECRET_FILE.test(String(p).trim())); }

/** The sources the check must have: a push is refused when the APK check's own files can't be read. */
function missingSources(root) {
  return ['secret_denylist.txt', 'screenscraper.properties', 'igdb.properties'].filter((f) => !fs.existsSync(path.join(root, f)));
}

module.exports = { readProps, denyValues, secretFiles, missingSources, SECRET_FILE };
