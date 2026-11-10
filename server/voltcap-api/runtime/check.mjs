// Runtime checks against a running Worker (wrangler dev, or node-host.mjs): node runtime/check.mjs http://127.0.0.1:8787
// Expects mock-upstream.mjs on 8788 and the Worker started with MOCK_UPSTREAM=http://127.0.0.1:8788 and the FAKE secrets
// from runtime/dev.vars.example. Exit code 0 = all passed. Prints names and statuses only.
import assert from "node:assert/strict";
const BASE = process.argv[2] || "http://127.0.0.1:8787", MOCK = process.argv[3] || "http://127.0.0.1:8788";
// On workers.dev Cloudflare's Cache API does nothing (it needs a zone / custom domain): cache checks are reported, not asserted.
const REMOTE = !/127\.0\.0\.1|localhost/.test(BASE);
const notes = [];
const SECRETS = ["FAKE_SS_DEVPW_987", "FAKE_TWITCH_SECRET_123456789", "userpw123"];
const H = { "x-voltcap-install": "runtime-check-0123456789" };
let failed = 0;
const RUN = Date.now() % 1000000; // unique query ids per run: a reused local state (cache, Durable Objects) can't hide a failure
async function step(name, fn) { try { await fn(); console.log("PASS", name); } catch (e) { failed++; console.log("FAIL", name, e.message); } }
const noSecret = (t) => SECRETS.forEach((s) => assert.ok(!t.includes(s), "secret leaked"));
await step("health", async () => assert.equal((await fetch(BASE + "/v1/health")).status, 200));
await step("no install id -> 400", async () => assert.equal((await fetch(BASE + "/v1/ss/jeuInfos.php")).status, 400));
await step("unknown path -> 404 (no open proxy)", async () => assert.equal((await fetch(BASE + "/v1/fetch?url=https://example.com", { headers: H })).status, 404));
await step("IGDB through the Worker (token in body, secret never returned)", async () => {
  const r = await fetch(BASE + "/v1/igdb/games", { method: "POST", headers: H, body: 'search "Super Mario World"; fields name; limit 5;' });
  const t = await r.text(); assert.equal(r.status, 200, t); noSecret(t); assert.match(t, /Super Mario World/);
  const r2 = await fetch(BASE + "/v1/igdb/games", { method: "POST", headers: H, body: 'search "Super Mario World"; fields name; limit 5;' });
  if (REMOTE) notes.push(`IGDB repeat query x-cache=${r2.headers.get("x-cache")}`); else assert.equal(r2.headers.get("x-cache"), "hit");
});
await step("IGDB query validation", async () => assert.equal((await fetch(BASE + "/v1/igdb/games", { method: "POST", headers: H, body: "fields *; limit 500;" })).status, 400));
let mediaUrl = "";
await step("ScreenScraper login + lookup: account in headers, answer clean, signed, never cached", async () => {
  const r = await fetch(BASE + "/v1/ss/jeuInfos.php?systemeid=4&romnom=Super%20Mario%20World%20(USA).sfc", { headers: { ...H, "x-ss-user": "player1", "x-ss-password": "userpw123" } });
  const t = await r.text(); assert.equal(r.status, 200, t); noSecret(t);
  assert.ok(/no-store/.test(r.headers.get("cache-control"))); assert.ok(!/devid|devpassword|sspassword/.test(t));
  mediaUrl = JSON.parse(t).response.jeu.medias[0].url; assert.match(mediaUrl, /vsig=/);
});
await step("wrong ScreenScraper login is reported, not leaked", async () => {
  const r = await fetch(BASE + "/v1/ss/jeuInfos.php?systemeid=4", { headers: { ...H, "x-ss-user": "player1", "x-ss-password": "wrong" } });
  assert.equal(r.status, 403); noSecret(await r.text());
});
await step("signed media download (full, then cached)", async () => {
  const u = new URL(mediaUrl); const r = await fetch(`${BASE}/v1/ss/media/mediaJeu.php${u.search}&maxwidth=600`, { headers: H });
  assert.equal(r.status, 200); assert.match(r.headers.get("content-type") || "", /^image\//);
  const n = (await r.arrayBuffer()).byteLength; assert.ok(n > 0 && n === Number(r.headers.get("content-length") || n)); // local mock 5000 B, deployed mock a real 70 B PNG
});
await step("tampered media signature -> 403", async () => {
  const u = new URL(mediaUrl); const r = await fetch(`${BASE}/v1/ss/media/mediaJeu.php${u.search.replace("jeuid=1", "jeuid=9")}`, { headers: H });
  assert.equal(r.status, 403);
});
await step("range request -> 206 streamed", async () => {
  const r = await fetch(`${BASE}/v1/ss/media/mediaJeu.php?systemeid=4&jeuid=1&media=video`, { headers: { ...H, range: "bytes=100-199" } });
  assert.equal(r.status, 206); assert.equal(r.headers.get("content-range"), "bytes 100-199/5000"); assert.equal((await r.arrayBuffer()).byteLength, 100);
});
await step("redirect to a non-ScreenScraper host refused", async () => {
  assert.equal((await fetch(`${BASE}/v1/ss/media/mediaJeu.php?systemeid=4&jeuid=1&media=evil`, { headers: H })).status, 502);
});
await step("client credentials in media addresses refused", async () => {
  assert.equal((await fetch(`${BASE}/v1/ss/media/mediaJeu.php?devpassword=x`, { headers: H })).status, 400);
});
await step("signed media: second download served from the Cache API (upstream not asked again)", async () => {
  const before = (await (await fetch(MOCK + "/__calls")).json()).filter((c) => c.path === "/ss/mediaJeu.php" && c.query.includes("box-2D")).length;
  const u = new URL(mediaUrl); const r = await fetch(`${BASE}/v1/ss/media/mediaJeu.php${u.search}&maxwidth=600`, { headers: H });
  assert.equal(r.status, 200); await r.arrayBuffer();
  const after = (await (await fetch(MOCK + "/__calls")).json()).filter((c) => c.path === "/ss/mediaJeu.php" && c.query.includes("box-2D")).length;
  if (REMOTE) notes.push(`signed picture repeat: upstream asked ${after - before} more time(s)`); else assert.equal(after, before);
});
await step("Durable Object keeps the Twitch token (one token request for every IGDB call)", async () => {
  for (let i = 0; i < 3; i++) await (await fetch(BASE + "/v1/igdb/games", { method: "POST", headers: H, body: `fields name; where id = ${RUN * 1000 + 900 + i};` })).text();
  const calls = await (await fetch(MOCK + "/__calls")).json();
  assert.ok(calls.filter((c) => c.path === "/twitch").length <= 1);
});
await step("global IGDB limit across 30 concurrent requests: >= 250 ms apart upstream, overflow refused", async () => {
  const t0 = Date.now();
  const rs = await Promise.all(Array.from({ length: 30 }, (_, i) => fetch(BASE + "/v1/igdb/games", { method: "POST",
    headers: { "x-voltcap-install": `burst-${RUN}-${String(i).padStart(10, "0")}` }, body: `fields name; where id = ${RUN * 1000 + 100 + i};` })
    .then(async (r) => { await r.text(); return r.status; })));
  const ok = rs.filter((s) => s === 200).length, busy = rs.filter((s) => s === 503).length;
  const times = (await (await fetch(MOCK + "/__calls")).json()).filter((c) => c.path === "/igdb/games" && c.at >= t0).map((c) => c.at).sort((a, b) => a - b);
  const gaps = times.slice(1).map((t, i) => t - times[i]);
  console.log(`      burst: ${ok} ok, ${busy} busy, upstream calls ${times.length}, min gap ${Math.min(...gaps)} ms`);
  assert.equal(ok + busy, 30); assert.ok(busy > 0); assert.ok(Math.min(...gaps) >= 230, "spacing");
});
await step("per-install window: the 41st IGDB call in a minute from one install is refused", async () => {
  const I = { "x-voltcap-install": `single-${RUN}-000000000` }; const codes = [];
  for (let i = 0; i < 42; i++) codes.push((await fetch(BASE + "/v1/igdb/games", { method: "POST", headers: I, body: `fields name; where id = ${RUN * 1000 + 500 + i};` })).status);
  console.log(`      statuses: ${codes.join(",")}`);
  assert.equal(codes.indexOf(429), 40);
});
await step("a client that disconnects cancels the upstream media download", async () => {
  const ac = new AbortController();
  const r = await fetch(`${BASE}/v1/ss/media/mediaJeu.php?systemeid=4&jeuid=1&media=slow`, { headers: H, signal: ac.signal });
  const reader = r.body.getReader(); await reader.read();
  ac.abort(); await new Promise((res) => setTimeout(res, 3000));
  const slow = (await (await fetch(MOCK + "/__calls")).json()).filter((c) => c.query.includes("media=slow")).pop();
  assert.ok(slow && slow.aborted === true, "upstream still streaming after the client left");
});
await step("upstream saw the credentials only server-side", async () => {
  const calls = await (await fetch(MOCK + "/__calls")).json();
  const tw = calls.find((c) => c.path === "/twitch"); assert.ok(tw && tw.body.includes("client_secret=") && !tw.query.includes("FAKE"));
  const ss = calls.find((c) => c.path === "/ss/jeuInfos.php"); assert.ok(ss.query.includes("devpassword=FAKE_SS_DEVPW_987"));
});
notes.forEach((n) => console.log("NOTE", n));
console.log(failed ? `${failed} FAILED` : "ALL PASSED");
process.exit(failed ? 1 : 0);
