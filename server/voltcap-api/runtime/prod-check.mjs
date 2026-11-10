// Batch 519: checks of the deployed PRODUCTION Worker against the real providers (read-only, a handful of requests).
// node runtime/prod-check.mjs https://voltcap-api.<sub>.workers.dev   — env CHECK_SECRETS: values that must never appear
// in an answer (newline separated; passed by task.ps1, never printed). Prints names and statuses only.
import assert from "node:assert/strict";
const BASE = process.argv[2];
const SECRETS = String(process.env.CHECK_SECRETS || "").split("\n").map((s) => s.trim()).filter((s) => s.length >= 4);
const H = { "x-voltcap-install": "prod-check-" + Date.now() };
let failed = 0;
async function step(name, fn) { try { await fn(); console.log("PASS", name); } catch (e) { failed++; console.log("FAIL", name, String(e.message).slice(0, 160)); } }
const clean = (t) => { SECRETS.forEach((s) => assert.ok(!t.includes(s), "a secret reached the client")); assert.ok(!/devpassword|sspassword/i.test(t), "credential parameter in answer"); };
console.log(`checking ${SECRETS.length} secret values`);
await step("health", async () => { const r = await fetch(BASE + "/v1/health"); assert.equal(r.status, 200); assert.deepEqual(await r.json(), { ok: true, igdb: true, ss: true }); });
await step("no install id -> 400, unknown path -> 404, wrong method -> 405", async () => {
  assert.equal((await fetch(BASE + "/v1/ss/jeuInfos.php")).status, 400);
  assert.equal((await fetch(BASE + "/v1/fetch?url=https://example.com", { headers: H })).status, 404);
  assert.equal((await fetch(BASE + "/v1/igdb/games", { headers: H })).status, 405);
});
await step("IGDB (real): Super Mario World found, answer clean, repeat from cache", async () => {
  const body = 'search "Super Mario World"; fields name, first_release_date; limit 5;';
  const r = await fetch(BASE + "/v1/igdb/games", { method: "POST", headers: H, body });
  const t = await r.text(); assert.equal(r.status, 200, t.slice(0, 120)); clean(t);
  assert.ok(JSON.parse(t).some((g) => /super mario world/i.test(g.name)));
  const r2 = await fetch(BASE + "/v1/igdb/games", { method: "POST", headers: H, body }); await r2.text();
  console.log("      repeat x-cache=" + r2.headers.get("x-cache"));
});
await step("IGDB validation -> 400", async () => assert.equal((await fetch(BASE + "/v1/igdb/games", { method: "POST", headers: H, body: "fields *; limit 500;" })).status, 400));
let media = {};
await step("ScreenScraper (real, developer key only): lookup clean, media addresses signed, not cacheable", async () => {
  const r = await fetch(BASE + "/v1/ss/jeuInfos.php?systemeid=4&romtype=rom&romnom=" + encodeURIComponent("Super Mario World (USA).sfc"), { headers: H });
  const t = await r.text(); assert.equal(r.status, 200, t.slice(0, 160)); clean(t);
  assert.match(r.headers.get("cache-control") || "", /no-store/);
  const medias = JSON.parse(t).response.jeu.medias;
  for (const type of ["box-2D", "manuel", "video-normalized", "video"]) { const m = medias.find((x) => x.type === type); if (m && !media[type.startsWith("video") ? "video" : type]) media[type.startsWith("video") ? "video" : type] = m.url; }
  const all = medias.map((m) => m.url).filter(Boolean);
  console.log(`      ${all.length} media addresses, ${all.filter((u) => u.includes("vsig=")).length} signed, ${all.filter((u) => /devid=|devpassword=/.test(u)).length} with a developer key`);
  assert.ok(all.length > 0 && all.every((u) => !/devid=|devpassword=|sspassword=/.test(u)));
});
const via = (u, extra = "") => { const x = new URL(u); return `${BASE}/v1/ss/media/${x.pathname.split("/").pop()}${x.search}${extra}`; };
await step("signed cover (real) through the service, then tampered -> 403", async () => {
  const r = await fetch(via(media["box-2D"], "&maxwidth=600"), { headers: H });
  const b = await r.arrayBuffer(); assert.equal(r.status, 200); assert.match(r.headers.get("content-type") || "", /^image\//); console.log(`      cover ${r.headers.get("content-type")} ${b.byteLength} B`);
  assert.equal((await fetch(via(media["box-2D"]).replace(/jeuid=(\d+)/, (_, n) => "jeuid=" + (Number(n) + 1)), { headers: H })).status, 403);
});
await step("manual PDF (real) through the service", async () => {
  assert.ok(media.manuel, "no manual address (lookup failed or none offered)");
  const r = await fetch(via(media.manuel), { headers: H }); const b = await r.arrayBuffer();
  assert.equal(r.status, 200); console.log(`      manual ${r.headers.get("content-type")} ${b.byteLength} B`);
});
await step("clip (real) with a Range request", async () => {
  assert.ok(media.video, "no clip address (lookup failed or none offered)");
  const r = await fetch(via(media.video), { headers: { ...H, range: "bytes=0-65535" } }); const b = await r.arrayBuffer();
  assert.ok(r.status === 206 || r.status === 200, String(r.status));
  console.log(`      clip status ${r.status} ${r.headers.get("content-type")} ${b.byteLength} B content-range=${r.headers.get("content-range")}`);
});
await step("credential parameters from a client refused", async () => assert.equal((await fetch(`${BASE}/v1/ss/media/mediaJeu.php?devpassword=x`, { headers: H })).status, 400));
console.log(failed ? `${failed} FAILED` : "ALL PASSED");
process.exit(failed ? 1 : 0);
