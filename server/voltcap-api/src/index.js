// voltcap-api — Voltcap's credential proxy (batch 517 proof of concept; NOT deployed).
//
// Voltcap's IGDB (Twitch) client secret and ScreenScraper developer password stay here as Worker secrets, never in the
// APK, git or any answer. Only fixed upstream hosts, endpoints and parameters are reachable (no open proxy).
//
// Secrets (wrangler secret put): TWITCH_CLIENT_ID, TWITCH_CLIENT_SECRET, SS_DEV_ID, SS_DEV_PASSWORD, SS_SOFT_NAME.
// Vars (wrangler.toml): KILL ("" | "all" | "igdb" | "ss" | "ss-media", comma separated), PUBLIC_BASE.
//
// Limits: every IGDB and ScreenScraper API call passes one Durable Object per service (a single instance worldwide, so
// the limits hold across every Worker instance and location): a global spacing (IGDB: 4 requests/s, at most 8 open), plus
// per-install and per-address windows. Installation IDs are random and self-chosen by the app: they are NOT
// authentication, only a key for fair limits; the address window catches an abuser who rotates IDs.
//
// Logging: none. No console output anywhere in this file (checked by the tests); observability is off in wrangler.toml.

const IGDB_ENDPOINTS = new Set(["games", "game_time_to_beats"]);
const SS_ENDPOINTS = new Set(["jeuInfos.php", "jeuRecherche.php", "ssuserInfos.php", "ssinfraInfos.php", "systemesListe.php"]);
// The client's own parameters ScreenScraper may receive (credentials are added here or come from headers).
const SS_PARAMS = new Set(["systemeid", "romtype", "romnom", "romtaille", "crc", "md5", "sha1", "serialnum", "gameid", "recherche", "langue"]);
const SS_SECRET_PARAMS = ["devid", "devpassword", "softname", "ssid", "sspassword"];
const SS_HOSTS = /(^|\.)screenscraper\.fr$/i;
const SS_MEDIA_FILES = /^media[A-Za-z]{2,32}\.php$/;
const SS_MEDIA_PARAMS = /^[a-z0-9_]{1,24}$/i;
// Sizes the app adds itself; not covered by a media address's signature.
const UNSIGNED_MEDIA_PARAMS = new Set(["vsig", "maxwidth", "maxheight"]);
const MEDIA_TIMEOUT_MS = 60000;
const MEDIA_CACHE_MAX_BYTES = 5 * 1024 * 1024;
const INSTALL_ID = /^[A-Za-z0-9_-]{16,64}$/;

const IGDB_CACHE_SECONDS = 7 * 24 * 3600;
const MEDIA_CACHE_SECONDS = 30 * 24 * 3600;
const MAX_IGDB_BODY = 4096;

export function json(status, obj, headers = {}) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...headers },
  });
}

/** Clear failures the app shows as they are. */
export const fail = {
  disabled: (what) => json(503, { error: "disabled", message: `${what} is switched off for now. Try again later.` }),
  badRequest: (why) => json(400, { error: "bad_request", message: why }),
  notFound: () => json(404, { error: "not_found", message: "Unknown address." }),
  method: () => json(405, { error: "method", message: "Method not allowed." }),
  limited: (sec) => json(429, { error: "rate_limited", message: "Too many requests. Try again shortly." }, { "retry-after": String(sec) }),
  busy: (sec) => json(503, { error: "busy", message: "The service is busy. Try again shortly." }, { "retry-after": String(sec) }),
  upstream: (who, status) => json(502, { error: "upstream", message: `${who} answered with an error (${status}).` }),
  timeout: (who) => json(504, { error: "timeout", message: `${who} didn't answer in time.` }),
  config: () => json(500, { error: "config", message: "The service isn't set up yet." }),
};

function killed(env, part) {
  const k = String(env.KILL || "").toLowerCase().split(",").map((s) => s.trim());
  return k.includes("all") || k.includes(part);
}

async function sha256(text) {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** Validates an IGDB Apicalypse body: printable, bounded, limit ≤ 50, no multiquery tricks. Returns an error or null. */
export function checkIgdbBody(body) {
  if (!body || body.length > MAX_IGDB_BODY) return "The query is empty or too long.";
  if (!/^[\x20-\x7E\t\r\n]+$/.test(body)) return "The query has characters that aren't allowed.";
  for (const m of body.matchAll(/\blimit\s+(\d+)/gi)) if (Number(m[1]) > 50) return "limit may be at most 50.";
  if (/\boffset\s+(\d+)/i.test(body) && Number(body.match(/\boffset\s+(\d+)/i)[1]) > 500) return "offset may be at most 500.";
  if (/\bquery\b/i.test(body)) return "multiquery isn't allowed.";
  return null;
}

/**
 * The upstream base address of [kind] ("ss" | "igdb" | "twitch"). MOCK_UPSTREAM (runtime tests only) is honoured only when
 * it points at localhost, so a misconfigured production variable can never send credentials elsewhere.
 */
export function upstream(env, kind) {
  const mock = mockBase(env);
  if (mock) return `${mock}/${kind}`;
  return { ss: "https://api.screenscraper.fr/api2", igdb: "https://api.igdb.com/v4", twitch: "https://id.twitch.tv/oauth2/token" }[kind];
}
function mockBase(env) {
  const m = String(env.MOCK_UPSTREAM || "");
  if (!m) return "";
  try {
    const u = new URL(m);
    const local = u.hostname === "localhost" || u.hostname === "127.0.0.1";
    // The staging Worker (STAGE = "staging", set only in [env.staging]) may use the deployed fake services instead.
    const stagingMock = env.STAGE === "staging" && u.protocol === "https:" && /^voltcap-mock-staging\.[a-z0-9-]+\.workers\.dev$/.test(u.hostname);
    return local || stagingMock ? m.replace(/\/$/, "") : "";
  } catch { return ""; }
}

/**
 * fetch(), except that the staging Worker reaches the deployed fake services through its service binding (MOCK): one Worker
 * on workers.dev can't fetch another Worker of the same account directly (Cloudflare answers with an error page).
 */
function upFetch(env, url, init) {
  const mock = mockBase(env);
  if (mock && env.MOCK && env.STAGE === "staging" && String(url).startsWith(mock + "/")) return env.MOCK.fetch(url, init);
  return fetch(url, init);
}

/** A redirect from ScreenScraper may only lead to ScreenScraper over https (or the local mock in tests). */
function redirectAllowed(env, u) {
  const mock = mockBase(env);
  if (mock && u.hostname === new URL(mock).hostname) return true;
  return u.protocol === "https:" && SS_HOSTS.test(u.hostname);
}

/** Short HMAC of a media address's own parameters (sizes excluded), so only addresses this service handed out work. */
export async function mediaSig(key, file, params) {
  const canon = [...params].filter(([k]) => !UNSIGNED_MEDIA_PARAMS.has(k.toLowerCase())).map(([k, v]) => `${k}=${v}`).sort().join("&");
  const k = await crypto.subtle.importKey("raw", new TextEncoder().encode(key), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const mac = new Uint8Array(await crypto.subtle.sign("HMAC", k, new TextEncoder().encode(`${file}?${canon}`)));
  return btoa(String.fromCharCode(...mac.slice(0, 16))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function sameString(a, b) {
  if (a.length !== b.length) return false;
  let d = 0;
  for (let i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return d === 0;
}

/**
 * Removes credentials from every ScreenScraper address in [text] (JSON or plain), signs media addresses ([signKey]) and
 * points them at this proxy when [mediaBase] is set. A last guard blanks any remaining occurrence of the secrets.
 */
export async function sanitizeSs(text, secrets, mediaBase, signKey) {
  // Real answers also hold ROM-hack entries whose addresses are templates ("#screenscraperserveur#api2/mediaHack.php?devid=…",
  // placeholder values, batch 519): their credential parameters are dropped the same way.
  const re = /(?:https?:(?:\\\/|\/){2}|#screenscraperserveur#)[^"\s<>]+/g;
  const done = new Map();
  for (const m of text.matchAll(re)) {
    const raw = m[0];
    if (done.has(raw)) continue;
    const escaped = raw.includes("\\/");
    const plain = escaped ? raw.replace(/\\\//g, "/") : raw;
    const TPL = "#screenscraperserveur#";
    const tpl = plain.startsWith(TPL);
    let u;
    try { u = new URL(tpl ? "https://www.screenscraper.fr/" + plain.slice(TPL.length) : plain); } catch { done.set(raw, raw); continue; }
    if (!SS_HOSTS.test(u.hostname)) { done.set(raw, raw); continue; }
    // The credentials removed from the query as written (no re-encoding: the app keys saved pictures by the exact address).
    const q = plain.indexOf("?");
    const kept = q < 0 ? [] : plain.slice(q + 1).split("&").filter((kv) => {
      if (!kv) return false;
      let name; try { name = decodeURIComponent(kv.split("=")[0]).toLowerCase(); } catch { return false; }
      return !SS_SECRET_PARAMS.includes(name) && name !== "vsig";
    });
    const base = q < 0 ? plain : plain.slice(0, q);
    const file = u.pathname.split("/").pop() || "";
    if (signKey && !tpl && SS_MEDIA_FILES.test(file)) kept.push("vsig=" + await mediaSig(signKey, file, new URLSearchParams(kept.join("&"))));
    const query = kept.length ? "?" + kept.join("&") : "";
    let s = (mediaBase && !tpl && SS_MEDIA_FILES.test(file)) ? `${mediaBase}/v1/ss/media/${file}${query}` : base + query;
    if (escaped) s = s.replace(/\//g, "\\/");
    done.set(raw, s);
  }
  let out = text.replace(re, (raw) => done.get(raw) ?? raw);
  for (const v of secrets) if (v && v.length >= 4) out = out.split(v).join("");
  return out;
}

async function withTimeout(promiseFn, ms) {
  const ctl = new AbortController();
  const t = setTimeout(() => ctl.abort("timeout"), ms);
  try { return await promiseFn(ctl.signal); } finally { clearTimeout(t); }
}

function clientKey(request) {
  const install = request.headers.get("x-voltcap-install") || "";
  const ip = request.headers.get("cf-connecting-ip") || "unknown";
  return { install, ip };
}

// ---------------------------------------------------------------------------------------------------------------------
// The Worker

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;
    if (path === "/v1/health") return json(200, { ok: true, igdb: !killed(env, "igdb"), ss: !killed(env, "ss") });

    const { install } = clientKey(request);
    if (!INSTALL_ID.test(install)) return fail.badRequest("Missing or malformed X-Voltcap-Install.");

    let m;
    if ((m = path.match(/^\/v1\/igdb\/([a-z_]+)$/))) {
      if (killed(env, "igdb")) return fail.disabled("IGDB");
      if (request.method !== "POST") return fail.method();
      if (!IGDB_ENDPOINTS.has(m[1])) return fail.notFound();
      const body = await request.text();
      const bad = checkIgdbBody(body);
      if (bad) return fail.badRequest(bad);
      // Public, identical for everyone: cached per Cloudflare location, keyed by endpoint + query (no client data).
      const cacheKey = new Request(`https://cache.voltcap.invalid/igdb/${m[1]}/${await sha256(body)}`);
      const cache = env.__cache ?? caches.default;
      const hit = await cache.match(cacheKey);
      if (hit) return new Response(hit.body, { status: 200, headers: { "content-type": "application/json", "cache-control": "no-store", "x-cache": "hit" } });
      const res = await gate(env.IGDB_GATE, request, { endpoint: m[1], body });
      if (res.status === 200) {
        const text = await res.text();
        ctx.waitUntil(cache.put(cacheKey, new Response(text, { headers: { "content-type": "application/json", "cache-control": `public, max-age=${IGDB_CACHE_SECONDS}` } })));
        return new Response(text, { status: 200, headers: { "content-type": "application/json", "cache-control": "no-store", "x-cache": "miss" } });
      }
      return res;
    }

    if ((m = path.match(/^\/v1\/ss\/media\/([A-Za-z.]+)$/))) {
      if (killed(env, "ss") || killed(env, "ss-media")) return fail.disabled("ScreenScraper media");
      if (request.method !== "GET") return fail.method();
      if (!SS_MEDIA_FILES.test(m[1])) return fail.notFound();
      return ssMedia(request, env, ctx, m[1], url.searchParams);
    }

    if ((m = path.match(/^\/v1\/ss\/([A-Za-z]+\.php)$/))) {
      if (killed(env, "ss")) return fail.disabled("ScreenScraper");
      if (request.method !== "GET") return fail.method();
      if (!SS_ENDPOINTS.has(m[1])) return fail.notFound();
      const params = {};
      for (const [k, v] of url.searchParams) {
        if (!SS_PARAMS.has(k)) return fail.badRequest(`Parameter "${k}" isn't allowed.`);
        if (v.length > 512) return fail.badRequest(`Parameter "${k}" is too long.`);
        params[k] = v;
      }
      // The person's own ScreenScraper account travels in headers (never in an address that could be logged anywhere).
      const user = request.headers.get("x-ss-user") || "";
      const password = request.headers.get("x-ss-password") || "";
      if (user.length > 128 || password.length > 128) return fail.badRequest("Account details too long.");
      return gate(env.SS_GATE, request, { endpoint: m[1], params, user, password });
    }
    return fail.notFound();
  },
};

/** Forwards to the service's single gate object, with the client's install and address for its limits. */
async function gate(ns, request, payload) {
  if (!ns) return fail.config();
  const { install, ip } = clientKey(request);
  const stub = ns.get(ns.idFromName("global"));
  return stub.fetch("https://gate.invalid/call", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ ...payload, install, ip }),
  });
}

/**
 * Media downloads (pictures, clips, manuals): no personal data, so whole pictures (up to 5 MB) are cached per location. Only addresses
 * this service signed are served (plus, while ALLOW_UNSIGNED_MEDIA is "1", unsigned ones from older stored data, under a
 * tighter limit), so the developer key can't be used for arbitrary ScreenScraper downloads. Range requests stream through
 * (never cached); a client that disconnects cancels the upstream download; redirects only to ScreenScraper.
 */
async function ssMedia(request, env, ctx, file, search) {
  if (!env.SS_DEV_ID || !env.SS_DEV_PASSWORD) return fail.config();
  const q = new URLSearchParams();
  let vsig = "";
  for (const [k, v] of search) {
    const lk = k.toLowerCase();
    if (!SS_MEDIA_PARAMS.test(k) || SS_SECRET_PARAMS.includes(lk)) return fail.badRequest(`Parameter "${k}" isn't allowed.`);
    if (v.length > 256) return fail.badRequest(`Parameter "${k}" is too long.`);
    if ((lk === "maxwidth" || lk === "maxheight") && !/^\d{1,4}$/.test(v)) return fail.badRequest(`Parameter "${k}" must be a size.`);
    if (lk === "vsig") { vsig = v; continue; }
    q.append(k, v);
  }
  const signed = !!(env.MEDIA_SIGNING_KEY && vsig) && sameString(vsig, await mediaSig(env.MEDIA_SIGNING_KEY, file, q));
  if (vsig && !signed) return json(403, { error: "bad_signature", message: "This media address isn't valid any more. Look the game up again." });
  if (!signed && env.ALLOW_UNSIGNED_MEDIA !== "1") return json(403, { error: "unsigned", message: "This media address is from an older version. Look the game up again." });
  // Fair-use limits (Cloudflare's Rate Limiting: per location, approximate) per install and per address.
  const { install, ip } = clientKey(request);
  const rl = signed ? env.MEDIA_RL : env.MEDIA_UNSIGNED_RL;
  if (rl) for (const key of ["i:" + install, "a:" + ip]) if (!(await rl.limit({ key })).success) return fail.limited(30);
  const range = request.headers.get("range");
  if (range && !/^bytes=\d*-\d*$/.test(range)) return json(416, { error: "range", message: "Unsupported range." });
  const cache = env.__cache ?? caches.default;
  const cacheKey = new Request(`https://cache.voltcap.invalid/ssmedia/${file}?${q}`);
  if (!range) { const hit = await cache.match(cacheKey); if (hit) return hit; }
  q.set("devid", env.SS_DEV_ID); q.set("devpassword", env.SS_DEV_PASSWORD); q.set("softname", env.SS_SOFT_NAME || "Voltcap");
  const signals = [AbortSignal.timeout(MEDIA_TIMEOUT_MS)];
  if (request.signal) signals.push(request.signal);
  const signal = signals.length > 1 && AbortSignal.any ? AbortSignal.any(signals) : signals[0];
  let target = `${upstream(env, "ss")}/${file}?${q}`;
  let up;
  for (let hop = 0; ; hop++) {
    try {
      up = await upFetch(env, target, { signal, redirect: "manual", headers: range ? { range } : {} });
    } catch {
      if (request.signal && request.signal.aborted) return new Response(null, { status: 499 });
      return fail.timeout("ScreenScraper");
    }
    const loc = up.status >= 300 && up.status < 400 ? up.headers.get("location") : null;
    if (!loc) break;
    let next;
    try { next = new URL(loc, target); } catch { return fail.upstream("ScreenScraper", "bad redirect"); }
    if (hop >= 3 || !redirectAllowed(env, next)) return fail.upstream("ScreenScraper", "redirect refused");
    target = next.toString();
  }
  if (up.status === 429) return fail.busy(30);
  if (up.status !== 200 && up.status !== 206) return fail.upstream("ScreenScraper", up.status);
  const type = up.headers.get("content-type") || "application/octet-stream";
  if (/json|text|html/i.test(type)) return fail.upstream("ScreenScraper", "no media"); // an error page: never cached, never passed on
  const headers = { "content-type": type, "accept-ranges": "bytes", "cache-control": up.status === 200 ? `public, max-age=${MEDIA_CACHE_SECONDS}` : "no-store" };
  for (const h of ["content-length", "content-range", "content-disposition", "last-modified", "etag"]) { const v = up.headers.get(h); if (v) headers[h] = v; }
  const res = new Response(up.body, { status: up.status, headers });
  const size = Number(up.headers.get("content-length") || "0");
  // Only pictures are cached: caching tees the body, and the cache's copy would keep a cancelled clip / manual download
  // running to the end (measured in workerd). Clips and manuals stream straight through and stop when the client leaves.
  if (up.status === 200 && !range && /^image\//i.test(type) && size > 0 && size <= MEDIA_CACHE_MAX_BYTES) ctx.waitUntil(cache.put(cacheKey, res.clone()).catch(() => {}));
  return res;
}

// ---------------------------------------------------------------------------------------------------------------------
// The gate: one Durable Object instance per service ("global"), so its counters are exact across the whole world.

class Window {
  constructor(limit, ms) { this.limit = limit; this.ms = ms; this.hits = new Map(); }
  /** True when [key] may make one more call now. */
  take(key, now) {
    const arr = (this.hits.get(key) || []).filter((t) => now - t < this.ms);
    if (arr.length >= this.limit) { this.hits.set(key, arr); return false; }
    arr.push(now); this.hits.set(key, arr);
    if (this.hits.size > 50000) for (const [k, v] of this.hits) if (!v.length || now - v[v.length - 1] >= this.ms) this.hits.delete(k);
    return true;
  }
}

export class Gate {
  /** [kind] "igdb" | "ss" picks upstream and limits; [now]/[sleep] are replaceable for tests. */
  constructor(state, env, kind, opts = {}) {
    this.state = state; this.env = env; this.kind = kind;
    this.now = opts.now || (() => Date.now());
    this.sleep = opts.sleep || ((ms) => new Promise((r) => setTimeout(r, ms)));
    const cfg = kind === "igdb"
      ? { spacing: 250, maxOpen: 8, maxWait: 4000, perInstall: [40, 60000], perIp: [120, 60000] }
      // ScreenScraper itself sometimes takes ~45 s to answer (measured direct from a PC as well, batch 519), so more calls
      // may be in flight at once; the 500 ms spacing still caps the request rate at 2/s.
      : { spacing: 500, maxOpen: 12, maxWait: 6000, perInstall: [30, 60000], perIp: [90, 60000] };
    Object.assign(cfg, opts.cfg || {});
    Object.assign(this, cfg);
    this.timeoutMs = opts.timeoutMs || (kind === "igdb" ? 15000 : 60000);
    this.nextSlot = 0; this.open = 0;
    this.installs = new Window(...cfg.perInstall);
    this.ips = new Window(...cfg.perIp);
  }

  async fetch(request) {
    const p = await request.json();
    const t0 = this.now();
    if (!this.installs.take("i:" + p.install, t0) || !this.ips.take("a:" + p.ip, t0)) return fail.limited(30);
    // IGDB: the token first, so the slot below is the moment IGDB is really called (a token fetch must not squeeze two
    // calls closer than the spacing — found by the concurrency test once the token check took a little longer).
    let tok = null;
    if (this.kind === "igdb") { tok = await this.token(false); if (!tok) return fail.config(); }
    const now = this.now();
    // Global spacing: each call takes the next free slot; one too far ahead is refused instead of queued forever.
    const slot = Math.max(now, this.nextSlot);
    // [open] counts calls waiting for their slot AND in flight, so never more than maxOpen at once (IGDB allows 8 open).
    if (slot - now > this.maxWait || this.open >= this.maxOpen) return fail.busy(2);
    this.nextSlot = slot + this.spacing;
    this.open++;
    try {
      if (slot > now) await this.sleep(slot - now);
      return this.kind === "igdb" ? await this.igdb(p, tok) : await this.ss(p);
    } finally { this.open--; }
  }

  // ---- IGDB ----
  /** One token request at a time: calls arriving together share it. */
  token(force) {
    if (!force && this.pending) return this.pending;
    const pr = this.fetchToken(force).finally(() => { if (this.pending === pr) this.pending = null; });
    if (!force) this.pending = pr;
    return pr;
  }

  async fetchToken(force) {
    const e = this.env;
    if (!e.TWITCH_CLIENT_ID || !e.TWITCH_CLIENT_SECRET) return null;
    // The kept token belongs to the credentials it was made with: a rotated secret (or another client ID) gets a fresh one.
    const owner = (await sha256(`${e.TWITCH_CLIENT_ID}\u0000${e.TWITCH_CLIENT_SECRET}`)).slice(0, 16);
    if (!force) {
      const t = await this.state.storage.get("twitch");
      if (t && t.owner === owner && t.exp - this.now() > 24 * 3600 * 1000) return t.token;
    }
    // The secret goes in the form body, never in an address.
    const r = await withTimeout((signal) => upFetch(e, upstream(e, "twitch"), {
      method: "POST", signal, redirect: "manual",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ client_id: e.TWITCH_CLIENT_ID, client_secret: e.TWITCH_CLIENT_SECRET, grant_type: "client_credentials" }),
    }), 10000);
    if (!r.ok) return null;
    const j = await r.json();
    if (!j.access_token) return null;
    await this.state.storage.put("twitch", { token: j.access_token, owner, exp: this.now() + (Number(j.expires_in) || 3600) * 1000 });
    return j.access_token;
  }

  async igdb(p, first) {
    for (let attempt = 0; attempt < 2; attempt++) {
      const tok = attempt === 0 && first ? first : await this.token(attempt > 0);
      if (!tok) return fail.config();
      let r;
      try {
        r = await withTimeout((signal) => upFetch(this.env, `${upstream(this.env, "igdb")}/${p.endpoint}`, {
          method: "POST", signal, redirect: "manual",
          headers: { "client-id": this.env.TWITCH_CLIENT_ID, authorization: `Bearer ${tok}`, accept: "application/json", "content-type": "text/plain" },
          body: p.body,
        }), this.timeoutMs);
      } catch { return fail.timeout("IGDB"); }
      if (r.status === 401 && attempt === 0) continue; // token revoked / rotated: fetch a new one once
      if (r.status === 429) return fail.busy(2);
      if (!r.ok) return fail.upstream("IGDB", r.status);
      return new Response(await r.text(), { status: 200, headers: { "content-type": "application/json", "cache-control": "no-store" } });
    }
    return fail.upstream("IGDB", 401);
  }

  // ---- ScreenScraper ----
  async ss(p) {
    const e = this.env;
    if (!e.SS_DEV_ID || !e.SS_DEV_PASSWORD) return fail.config();
    const q = new URLSearchParams({ devid: e.SS_DEV_ID, devpassword: e.SS_DEV_PASSWORD, softname: e.SS_SOFT_NAME || "Voltcap", output: "json", ...p.params });
    if (p.user && p.password) { q.set("ssid", p.user); q.set("sspassword", p.password); }
    let r;
    try {
      r = await withTimeout((signal) => upFetch(e, `${upstream(e, "ss")}/${p.endpoint}?${q}`, { signal, redirect: "manual" }), this.timeoutMs);
    } catch { return fail.timeout("ScreenScraper"); }
    if (r.status >= 300 && r.status < 400) return fail.upstream("ScreenScraper", r.status); // never followed for API calls
    const text = await r.text();
    const mediaBase = e.SS_PROXY_MEDIA === "1" ? (e.PUBLIC_BASE || "") : "";
    const clean = await sanitizeSs(text, [e.SS_DEV_PASSWORD, p.password, e.SS_DEV_ID.length >= 6 ? e.SS_DEV_ID : ""], mediaBase, e.MEDIA_SIGNING_KEY);
    // Personal (it carries the person's account and quota): never cached anywhere. ScreenScraper's own status codes and
    // error texts (e.g. a wrong login) pass through so the app can explain them.
    return new Response(clean, {
      status: r.status,
      headers: { "content-type": r.headers.get("content-type") || "text/plain; charset=utf-8", "cache-control": "private, no-store" },
    });
  }
}

/** The two Durable Object classes bound in wrangler.toml. */
export class IgdbGate extends Gate { constructor(state, env) { super(state, env, "igdb"); } }
export class SsGate extends Gate { constructor(state, env) { super(state, env, "ss"); } }
