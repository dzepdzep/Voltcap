// node --test server/voltcap-api/test — the proxy's rules against fake upstreams (no network, fake credentials only).
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import worker, { Gate, sanitizeSs, checkIgdbBody, mediaSig, upstream, SsGate } from "../src/index.js";

const FAKE = {
  TWITCH_CLIENT_ID: "fakeclientid000000000000000000",
  TWITCH_CLIENT_SECRET: "FAKE_TWITCH_SECRET_123456789",
  SS_DEV_ID: "fakedevid",
  SS_DEV_PASSWORD: "FAKE_SS_DEVPW_987",
  SS_SOFT_NAME: "Voltcap",
};
const SECRETS = [FAKE.TWITCH_CLIENT_SECRET, FAKE.SS_DEV_PASSWORD];
const INSTALL = "qa517-install-0123456789abcdef";

// ---- fakes ----
class FakeStorage { constructor() { this.m = new Map(); } async get(k) { return this.m.get(k); } async put(k, v) { this.m.set(k, v); } }
function namespace(make) {
  const objects = new Map();
  return {
    idFromName: (n) => n,
    get: (id) => {
      if (!objects.has(id)) objects.set(id, make({ storage: new FakeStorage() }));
      const o = objects.get(id);
      return { fetch: (u, init) => o.fetch(new Request(u, init)) };
    },
    objects,
  };
}
class FakeCache {
  constructor() { this.m = new Map(); }
  async match(req) { const v = this.m.get(req.url); return v ? new Response(v.body, { headers: v.headers }) : undefined; }
  async put(req, res) { this.m.set(req.url, { body: await res.text(), headers: Object.fromEntries(res.headers) }); }
}
const ctx = () => { const waits = []; return { waitUntil: (p) => waits.push(p), done: () => Promise.all(waits) }; };

/** Fake upstreams: records every call; [handler] answers. */
function upstreams(handler) {
  const calls = [];
  globalThis.fetch = async (input, init = {}) => {
    const url = typeof input === "string" ? input : input.url;
    const body = init.body instanceof URLSearchParams ? init.body.toString() : (init.body ?? null);
    const call = { url, body, headers: init.headers || {}, at: Date.now() };
    calls.push(call);
    return handler(call, init.signal);
  };
  return calls;
}
function hang(signal) { return new Promise((_, rej) => signal.addEventListener("abort", () => rej(new Error("aborted")))); }

function env(extra = {}, gateOpts = {}) {
  const e = { ...FAKE, PUBLIC_BASE: "https://api.voltcap.example", KILL: "", __cache: new FakeCache(), ...extra };
  e.IGDB_GATE = namespace((st) => new Gate(st, e, "igdb", gateOpts));
  e.SS_GATE = namespace((st) => new Gate(st, e, "ss", gateOpts));
  return e;
}
const req = (path, init = {}) => new Request("https://api.voltcap.example" + path, {
  ...init, headers: { "x-voltcap-install": INSTALL, "cf-connecting-ip": "198.51.100.7", ...(init.headers || {}) },
});
function twitchAndIgdb(call) {
  if (call.url.startsWith("https://id.twitch.tv/")) return new Response(JSON.stringify({ access_token: "faketoken", expires_in: 5000000 }));
  if (call.url.startsWith("https://api.igdb.com/v4/")) return new Response(JSON.stringify([{ id: 1, name: "Super Mario World" }]));
  return new Response("unexpected upstream", { status: 599 });
}
const noSecret = (text) => SECRETS.forEach((s) => assert.ok(!text.includes(s), "a secret reached the client"));

// ---- tests ----
test("source has no logging", () => {
  const src = readFileSync(new URL("../src/index.js", import.meta.url), "utf8");
  assert.ok(!/console\./.test(src));
});

test("install id required, unknown paths and methods refused", async () => {
  upstreams(() => assert.fail("no upstream call expected"));
  const e = env();
  assert.equal((await worker.fetch(new Request("https://x/v1/igdb/games", { method: "POST", body: "fields name;" }), e, ctx())).status, 400);
  for (const p of ["/v1/igdb/companies", "/v1/igdb/../games", "/v1/ss/jeuDelete.php", "/v1/fetch?url=https://evil.example", "/v1/ss/media/evil.exe", "/"])
    assert.equal((await worker.fetch(req(p, { method: p.includes("igdb") ? "POST" : "GET", body: p.includes("igdb") ? "fields name;" : undefined }), e, ctx())).status, 404, p);
  assert.equal((await worker.fetch(req("/v1/igdb/games"), e, ctx())).status, 405);
});

test("IGDB: secret stays server-side, token in body, query validated, public answers cached", async () => {
  const calls = upstreams(twitchAndIgdb);
  const e = env(); const c = ctx();
  const body = 'search "Super Mario World"; fields name,summary; limit 5;';
  const r = await worker.fetch(req("/v1/igdb/games", { method: "POST", body }), e, c);
  await c.done();
  assert.equal(r.status, 200);
  const text = await r.text(); noSecret(text);
  assert.equal(r.headers.get("x-cache"), "miss");
  const tok = calls.find((x) => x.url.startsWith("https://id.twitch.tv/"));
  assert.ok(!tok.url.includes(FAKE.TWITCH_CLIENT_SECRET), "secret must not be in an address");
  assert.ok(tok.body.includes("client_secret="));
  const ig = calls.find((x) => x.url.startsWith("https://api.igdb.com/"));
  assert.equal(ig.headers.authorization, "Bearer faketoken");
  // Same query again: from the cache, no upstream call.
  const before = calls.length;
  const r2 = await worker.fetch(req("/v1/igdb/games", { method: "POST", body }), e, ctx());
  assert.equal(r2.headers.get("x-cache"), "hit"); assert.equal(calls.length, before);
  assert.equal(await r2.text(), text);
  // Validation.
  for (const bad of ["fields *; limit 500;", "query games \"a\" { fields name; };", "", "x".repeat(5000), "fields name;\u0000"])
    assert.equal((await worker.fetch(req("/v1/igdb/games", { method: "POST", body: bad }), e, ctx())).status, 400, bad.slice(0, 20));
  assert.equal(checkIgdbBody("fields name; limit 50;"), null);
});

test("IGDB: a rotated secret / revoked token is fetched again once", async () => {
  let tokens = 0; let igdb = 0;
  upstreams((call) => {
    if (call.url.startsWith("https://id.twitch.tv/")) return new Response(JSON.stringify({ access_token: "t" + (++tokens), expires_in: 5000000 }));
    igdb++; return call.headers.authorization === "Bearer t1" ? new Response("", { status: 401 }) : new Response("[]");
  });
  const r = await worker.fetch(req("/v1/igdb/games", { method: "POST", body: "fields name; limit 1;" }), env(), ctx());
  assert.equal(r.status, 200); assert.equal(tokens, 2); assert.equal(igdb, 2);
});

test("global limit holds across many Worker instances: spacing and max open", async () => {
  // One gate object worldwide; 30 calls from "different Worker instances" (separate fetch invocations, separate installs/IPs).
  let open = 0, maxOpen = 0;
  const calls = upstreams(async (call) => {
    if (call.url.startsWith("https://id.twitch.tv/")) return new Response(JSON.stringify({ access_token: "faketoken", expires_in: 5000000 }));
    open++; maxOpen = Math.max(maxOpen, open);
    await new Promise((r) => setTimeout(r, 60)); open--;
    return new Response("[]");
  });
  const e = env({}, { cfg: { spacing: 25, maxOpen: 8, maxWait: 400 } });
  const results = await Promise.all(Array.from({ length: 30 }, (_, i) => worker.fetch(new Request("https://api.voltcap.example/v1/igdb/games", {
    method: "POST", body: `fields name; where id = ${i};`,
    headers: { "x-voltcap-install": `install-${String(i).padStart(16, "0")}`, "cf-connecting-ip": `203.0.113.${i}` },
  }), e, ctx())));
  const ok = results.filter((r) => r.status === 200).length;
  const busy = results.filter((r) => r.status === 503).length;
  const igdbTimes = calls.filter((c) => c.url.startsWith("https://api.igdb.com/")).map((c) => c.at).sort((a, b) => a - b);
  for (let i = 1; i < igdbTimes.length; i++) assert.ok(igdbTimes[i] - igdbTimes[i - 1] >= 22, `calls ${i - 1}/${i} only ${igdbTimes[i] - igdbTimes[i - 1]} ms apart`);
  assert.ok(maxOpen <= 8, `max open ${maxOpen}`);
  assert.equal(ok + busy, 30); assert.ok(busy > 0, "a burst past the queue must be refused, not queued forever");
  assert.equal(e.IGDB_GATE.objects.size, 1, "one gate object for the whole service");
});

test("per-install and per-address windows", async () => {
  upstreams(twitchAndIgdb);
  const e = env({}, { cfg: { spacing: 0, perInstall: [3, 60000], perIp: [5, 60000] } });
  const codes = [];
  for (let i = 0; i < 5; i++) codes.push((await worker.fetch(req("/v1/igdb/games", { method: "POST", body: `fields name; where id = ${i};` }), e, ctx())).status);
  assert.deepEqual(codes, [200, 200, 200, 429, 429]);
  // Rotating install IDs from one address still hits the address window.
  const rot = [];
  for (let i = 0; i < 4; i++) rot.push((await worker.fetch(new Request("https://api.voltcap.example/v1/igdb/games", {
    method: "POST", body: `fields name; where id = ${100 + i};`,
    headers: { "x-voltcap-install": `rotating-${String(i).padStart(16, "0")}`, "cf-connecting-ip": "198.51.100.7" },
  }), e, ctx())).status);
  assert.ok(rot.includes(429));
});

test("ScreenScraper: account in headers, credentials added server-side, answer sanitized and never cached", async () => {
  const answer = JSON.stringify({ response: { ssuser: { id: "player1" }, jeu: { medias: [
    { type: "box-2D", url: `https://neoclone.screenscraper.fr/api2/mediaJeu.php?devid=${FAKE.SS_DEV_ID}&devpassword=${FAKE.SS_DEV_PASSWORD}&softname=Voltcap&ssid=player1&sspassword=userpw123&systemeid=4&jeuid=1&media=box-2D(us)` },
    { type: "manuel", url: `https://neoclone.screenscraper.fr/api2/mediaManuelJeu.php?devid=${FAKE.SS_DEV_ID}&devpassword=${FAKE.SS_DEV_PASSWORD}&softname=Voltcap&ssid=&sspassword=&systemeid=4&jeuid=1&media=manuel(us)` },
  ] } } }).replace(/\//g, "\\/");
  const calls = upstreams((call) => new Response(answer, { headers: { "content-type": "application/json" } }));
  const e = env({ SS_PROXY_MEDIA: "1" });
  const r = await worker.fetch(req("/v1/ss/jeuInfos.php?systemeid=4&romtype=rom&romnom=Super%20Mario%20World%20(USA).sfc", {
    headers: { "x-ss-user": "player1", "x-ss-password": "userpw123" },
  }), e, ctx());
  assert.equal(r.status, 200);
  assert.equal(r.headers.get("cache-control"), "private, no-store");
  const text = await r.text(); noSecret(text);
  assert.ok(!text.includes("userpw123") && !text.includes("devpassword") && !text.includes("sspassword") && !text.includes("devid"));
  assert.ok(text.includes("https:\\/\\/api.voltcap.example\\/v1\\/ss\\/media\\/mediaJeu.php?"), "media pointed at the proxy");
  const up = new URL(calls[0].url);
  assert.equal(up.hostname, "api.screenscraper.fr");
  assert.equal(up.searchParams.get("devpassword"), FAKE.SS_DEV_PASSWORD);
  assert.equal(up.searchParams.get("ssid"), "player1");
  // Client parameters outside the list, or credentials in the address, are refused.
  for (const q of ["devpassword=x", "url=https://evil.example", "ssid=a", "systemeid=" + "9".repeat(600)])
    assert.equal((await worker.fetch(req("/v1/ss/jeuInfos.php?" + q), e, ctx())).status, 400, q);
});

test("ScreenScraper default mode: addresses stay on ScreenScraper, stripped (the app adds the route when downloading)", async () => {
  const answer = `{"url":"https:\\/\\/neoclone.screenscraper.fr\\/api2\\/mediaJeu.php?devid=${FAKE.SS_DEV_ID}&devpassword=${FAKE.SS_DEV_PASSWORD}&softname=Voltcap&ssid=&sspassword=&systemeid=4&jeuid=1&media=box-2D(us)"}`;
  upstreams(() => new Response(answer, { headers: { "content-type": "application/json" } }));
  const r = await worker.fetch(req("/v1/ss/jeuInfos.php?systemeid=4&romnom=x.sfc"), env({ SS_PROXY_MEDIA: "0" }), ctx());
  const j = JSON.parse(await r.text());
  assert.equal(j.url, "https://neoclone.screenscraper.fr/api2/mediaJeu.php?systemeid=4&jeuid=1&media=box-2D(us)");
});

test("ScreenScraper media: no credentials from the client, picture cached, error pages never cached", async () => {
  let n = 0;
  upstreams((call) => { n++; const u = new URL(call.url);
    assert.equal(u.searchParams.get("devpassword"), FAKE.SS_DEV_PASSWORD);
    return u.searchParams.get("jeuid") === "404" ? new Response("Erreur : media non trouvé", { headers: { "content-type": "text/html" } })
      : new Response(new Uint8Array([137, 80, 78, 71]), { headers: { "content-type": "image/png", "content-length": "4" } }); });
  const e = env({ ALLOW_UNSIGNED_MEDIA: "1" });
  const c1 = ctx();
  const r = await worker.fetch(req("/v1/ss/media/mediaJeu.php?systemeid=4&jeuid=1&media=box-2D(us)&maxwidth=600"), e, c1); await c1.done();
  assert.equal(r.status, 200); assert.equal(r.headers.get("content-type"), "image/png");
  await worker.fetch(req("/v1/ss/media/mediaJeu.php?systemeid=4&jeuid=1&media=box-2D(us)&maxwidth=600"), e, ctx());
  assert.equal(n, 1, "second download from the cache");
  assert.equal((await worker.fetch(req("/v1/ss/media/mediaJeu.php?jeuid=404"), e, ctx())).status, 502);
  for (const q of ["devid=x", "ssid=a&sspassword=b", "softname=x"]) assert.equal((await worker.fetch(req("/v1/ss/media/mediaJeu.php?" + q), e, ctx())).status, 400, q);
});

test("kill switch and timeouts give clear answers", async () => {
  upstreams(twitchAndIgdb);
  const k = env({ KILL: "igdb" });
  const r = await worker.fetch(req("/v1/igdb/games", { method: "POST", body: "fields name;" }), k, ctx());
  assert.equal(r.status, 503); assert.equal((await r.json()).error, "disabled");
  assert.deepEqual(await (await worker.fetch(new Request("https://x/v1/health"), k, ctx())).json(), { ok: true, igdb: false, ss: true });
  assert.equal((await worker.fetch(req("/v1/ss/jeuInfos.php?systemeid=1"), env({ KILL: "all" }), ctx())).status, 503);
  upstreams((call, signal) => call.url.startsWith("https://id.twitch.tv/") ? twitchAndIgdb(call) : hang(signal));
  const t = await worker.fetch(req("/v1/igdb/games", { method: "POST", body: "fields name;" }), env({}, { timeoutMs: 50 }), ctx());
  assert.equal(t.status, 504);
  const cfg = await worker.fetch(req("/v1/igdb/games", { method: "POST", body: "fields name;" }), env({ TWITCH_CLIENT_SECRET: "" }), ctx());
  assert.equal(cfg.status, 500); assert.equal((await cfg.json()).error, "config");
});

test("sanitizer keeps non-ScreenScraper addresses and blanks stray secrets", async () => {
  const s = await sanitizeSs(`{"a":"https://example.com/x?devpassword=keep","b":"note ${FAKE.SS_DEV_PASSWORD}"}`, [FAKE.SS_DEV_PASSWORD], "");
  assert.ok(s.includes("https://example.com/x?devpassword=keep")); assert.ok(!s.includes(FAKE.SS_DEV_PASSWORD));
});

// ---- batch 518: signed media, ranges, redirects, cancellation, test-only upstream override ----
const KEY = "FAKE_MEDIA_SIGNING_KEY_0123456789";
const png = (extra = {}) => new Response(new Uint8Array(1000).fill(7), { headers: { "content-type": "image/png", "content-length": "1000", ...extra } });

test("answers carry signed media addresses; only signed (or, in transition, unsigned) media are served", async () => {
  const answer = `{"url":"https:\\/\\/neoclone.screenscraper.fr\\/api2\\/mediaJeu.php?devid=${FAKE.SS_DEV_ID}&devpassword=${FAKE.SS_DEV_PASSWORD}&softname=Voltcap&ssid=&sspassword=&systemeid=4&jeuid=1&media=box-2D(us)"}`;
  upstreams((call) => call.url.includes("jeuInfos") ? new Response(answer, { headers: { "content-type": "application/json" } }) : png());
  const e = env({ MEDIA_SIGNING_KEY: KEY });
  const j = JSON.parse(await (await worker.fetch(req("/v1/ss/jeuInfos.php?systemeid=4"), e, ctx())).text());
  const u = new URL(j.url);
  assert.equal(u.searchParams.get("vsig"), await mediaSig(KEY, "mediaJeu.php", new URLSearchParams("systemeid=4&jeuid=1&media=box-2D(us)")));
  noSecret(j.url);
  const signedPath = `/v1/ss/media/mediaJeu.php${u.search}&maxwidth=600`; // the app adds sizes; they are outside the signature
  assert.equal((await worker.fetch(req(signedPath), e, ctx())).status, 200);
  const tampered = signedPath.replace("jeuid=1", "jeuid=2");
  assert.equal((await worker.fetch(req(tampered), e, ctx())).status, 403);
  assert.equal((await worker.fetch(req("/v1/ss/media/mediaJeu.php?systemeid=4&jeuid=1&media=box-2D(us)"), e, ctx())).status, 403, "unsigned refused by default");
  assert.equal((await worker.fetch(req("/v1/ss/media/mediaJeu.php?systemeid=4&jeuid=1&media=box-2D(us)"), env({ MEDIA_SIGNING_KEY: KEY, ALLOW_UNSIGNED_MEDIA: "1" }), ctx())).status, 200);
  assert.equal((await worker.fetch(req(signedPath.replace("maxwidth=600", "maxwidth=60000")), e, ctx())).status, 400);
});

test("media limits use the Rate Limiting bindings (per install and per address)", async () => {
  upstreams(() => png());
  const seen = []; let allow = 2;
  const rl = { limit: async ({ key }) => { seen.push(key); return { success: allow-- > 0 }; } };
  const e = env({ ALLOW_UNSIGNED_MEDIA: "1", MEDIA_UNSIGNED_RL: rl });
  assert.equal((await worker.fetch(req("/v1/ss/media/mediaJeu.php?jeuid=1"), e, ctx())).status, 200);
  assert.deepEqual(seen, [`i:${INSTALL}`, "a:198.51.100.7"]);
  assert.equal((await worker.fetch(req("/v1/ss/media/mediaJeu.php?jeuid=2"), e, ctx())).status, 429);
});

test("range requests stream through with 206 and are never cached", async () => {
  let n = 0;
  upstreams((call) => { n++; assert.equal(call.headers.range, "bytes=0-99");
    return new Response(new Uint8Array(100), { status: 206, headers: { "content-type": "video/mp4", "content-range": "bytes 0-99/5000", "content-length": "100" } }); });
  const e = env({ ALLOW_UNSIGNED_MEDIA: "1" });
  const r = await worker.fetch(req("/v1/ss/media/mediaVideoJeu.php?jeuid=1&media=video", { headers: { range: "bytes=0-99" } }), e, ctx());
  assert.equal(r.status, 206); assert.equal(r.headers.get("content-range"), "bytes 0-99/5000"); assert.equal(r.headers.get("cache-control"), "no-store");
  assert.equal((await r.arrayBuffer()).byteLength, 100);
  await worker.fetch(req("/v1/ss/media/mediaVideoJeu.php?jeuid=1&media=video", { headers: { range: "bytes=0-99" } }), e, ctx());
  assert.equal(n, 2, "no cached partial answers");
  assert.equal((await worker.fetch(req("/v1/ss/media/mediaVideoJeu.php?jeuid=1", { headers: { range: "items=1-2" } }), e, ctx())).status, 416);
});

test("redirects: followed only to ScreenScraper over https, never to other hosts, never for API calls", async () => {
  upstreams((call) => call.url.startsWith("https://api.screenscraper.fr/") ? new Response(null, { status: 302, headers: { location: "https://neoclone.screenscraper.fr/api2/mediaJeu.php?jeuid=1" } }) : png());
  const e = env({ ALLOW_UNSIGNED_MEDIA: "1" });
  assert.equal((await worker.fetch(req("/v1/ss/media/mediaJeu.php?jeuid=1"), e, ctx())).status, 200);
  const calls = upstreams((call) => call.url.startsWith("https://api.screenscraper.fr/") ? new Response(null, { status: 302, headers: { location: "https://evil.example/steal" } }) : png());
  const r = await worker.fetch(req("/v1/ss/media/mediaJeu.php?jeuid=1"), env({ ALLOW_UNSIGNED_MEDIA: "1" }), ctx()); // fresh cache
  assert.equal(r.status, 502); assert.ok(!calls.some((c) => c.url.includes("evil.example")));
  noSecret(await r.text());
  upstreams(() => new Response(null, { status: 302, headers: { location: "https://neoclone.screenscraper.fr/x" } }));
  assert.equal((await worker.fetch(req("/v1/ss/jeuInfos.php?systemeid=1"), env(), ctx())).status, 502);
});

test("a client that goes away cancels the upstream download", async () => {
  let aborted = false;
  upstreams((call, signal) => new Promise((_, rej) => signal.addEventListener("abort", () => { aborted = true; rej(new Error("aborted")); })));
  const ac = new AbortController();
  const p = worker.fetch(new Request("https://api.voltcap.example/v1/ss/media/mediaJeu.php?jeuid=1", { signal: ac.signal, headers: { "x-voltcap-install": INSTALL } }), env({ ALLOW_UNSIGNED_MEDIA: "1" }), ctx());
  setTimeout(() => ac.abort(), 30);
  const r = await p;
  assert.ok(aborted); assert.equal(r.status, 499);
});

test("test-only upstream override works only for localhost", () => {
  assert.equal(upstream({ MOCK_UPSTREAM: "http://127.0.0.1:8788" }, "ss"), "http://127.0.0.1:8788/ss");
  assert.equal(upstream({ MOCK_UPSTREAM: "https://evil.example" }, "ss"), "https://api.screenscraper.fr/api2");
  assert.equal(upstream({}, "twitch"), "https://id.twitch.tv/oauth2/token");
  // The deployed fake services: only for the staging Worker, only that host, only https.
  const mock = "https://voltcap-mock-staging.someone.workers.dev";
  assert.equal(upstream({ STAGE: "staging", MOCK_UPSTREAM: mock }, "ss"), mock + "/ss");
  assert.equal(upstream({ MOCK_UPSTREAM: mock }, "ss"), "https://api.screenscraper.fr/api2", "production ignores it");
  assert.equal(upstream({ STAGE: "staging", MOCK_UPSTREAM: "https://evil.workers.dev" }, "ss"), "https://api.screenscraper.fr/api2");
  assert.equal(upstream({ STAGE: "staging", MOCK_UPSTREAM: "http://voltcap-mock-staging.someone.workers.dev" }, "ss"), "https://api.screenscraper.fr/api2");
});

test("a rotated Twitch secret makes the gate fetch a new token at once (the kept one is tied to the old secret)", async () => {
  let tokens = 0;
  upstreams((call) => call.url.startsWith("https://id.twitch.tv/") ? new Response(JSON.stringify({ access_token: "t" + (++tokens), expires_in: 5000000 })) : new Response("[]"));
  const e = env();
  await worker.fetch(req("/v1/igdb/games", { method: "POST", body: "fields name; where id = 1;" }), e, ctx());
  await worker.fetch(req("/v1/igdb/games", { method: "POST", body: "fields name; where id = 2;" }), e, ctx());
  assert.equal(tokens, 1);
  e.TWITCH_CLIENT_SECRET = "FAKE_ROTATED_SECRET_000000000"; // wrangler secret put: same Durable Object storage, new secret
  await worker.fetch(req("/v1/igdb/games", { method: "POST", body: "fields name; where id = 3;" }), e, ctx());
  assert.equal(tokens, 2);
});

test("batch 519: ScreenScraper may take ~45 s; the gate waits 60 s and allows 12 calls in flight", () => {
  const g = new SsGate({ storage: { get: async () => null, put: async () => {} } }, {});
  assert.equal(g.timeoutMs, 60000);
  assert.equal(g.maxOpen, 12);
  assert.equal(g.spacing, 500);
});

test("batch 519: ROM-hack template addresses lose their credential parameters too", async () => {
  const raw = '{"hacks":[{"downloadurl":"#screenscraperserveur#api2/mediaFichierHack.php?devid=xxx&devpassword=yyy&softname=zzz&ssid=test&sspassword=test&hackid=23","medias":[{"url":"#screenscraperserveur#api2/mediaHack.php?devid=xxx&devpassword=yyy&softname=zzz&ssid=test&sspassword=test&hackid=23&media=sstitle"}]}]}';
  const out = await sanitizeSs(raw, ["realpw1234"], "", "k");
  assert.ok(!/devid|devpassword|sspassword|ssid=|softname/i.test(out), out);
  assert.match(out, /"#screenscraperserveur#api2\/mediaFichierHack\.php\?hackid=23"/);
  assert.match(out, /mediaHack\.php\?hackid=23&media=sstitle"/);
  JSON.parse(out);
});
