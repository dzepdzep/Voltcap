// voltcap-mock-staging: FAKE Twitch / IGDB / ScreenScraper for testing the deployed STAGING Worker on Cloudflare.
// Same behaviour as runtime/mock-upstream.mjs. Every call is recorded in one Durable Object (GET /__calls, POST /__reset).
// Holds no real credential; it accepts only the fake ones from runtime/dev.vars.example.
const DEV = "fakedevid", DEVPW = "FAKE_SS_DEVPW_987", SECRET = "FAKE_TWITCH_SECRET_123456789";
// A real 1x1 PNG and a minimal PDF, so the app can decode / open what it downloads.
const PNG = Uint8Array.from(atob("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="), (c) => c.charCodeAt(0));
const PDF = new TextEncoder().encode("%PDF-1.1\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj 3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n" + "%" + " ".repeat(2048) + "\n%%EOF\n"); // padded past the app's 1 KB "real PDF" check

export class Recorder {
  // Kept in the object's storage: an idle Durable Object can be evicted between test steps.
  constructor(state) { this.state = state; }
  async fetch(req) {
    const st = this.state.storage;
    const u = new URL(req.url);
    const calls = (await st.get("calls")) || [];
    if (u.pathname === "/add") { calls.push(await req.json()); if (calls.length > 1500) calls.shift(); await st.put("calls", calls); return new Response("ok"); }
    if (u.pathname === "/mark") { const { id, aborted } = await req.json(); const c = calls.find((x) => x.id === id); if (c) { c.aborted = aborted; await st.put("calls", calls); } return new Response("ok"); }
    if (u.pathname === "/token") { const n = ((await st.get("token")) || 0) + 1; await st.put("token", n); return new Response(String(n)); }
    if (u.pathname === "/current") return new Response(String((await st.get("token")) || 0));
    if (u.pathname === "/reset") { await st.put("calls", []); return new Response("ok"); }
    return new Response(JSON.stringify(calls), { headers: { "content-type": "application/json" } });
  }
}

export default {
  async fetch(req, env, ctx) {
    const u = new URL(req.url);
    const rec = env.REC.get(env.REC.idFromName("all"));
    const R = (path, body) => rec.fetch("https://rec" + path, body === undefined ? {} : { method: "POST", body: JSON.stringify(body) });
    if (u.pathname === "/__calls") return R("/calls");
    if (u.pathname === "/__reset") return R("/reset", {});
    const body = req.method === "POST" ? await req.text() : "";
    const id = crypto.randomUUID();
    await R("/add", { id, path: u.pathname, query: u.search, body, range: req.headers.get("range"), auth: req.headers.get("authorization"), at: Date.now() });
    if (u.pathname === "/twitch") {
      const ok = new URLSearchParams(body).get("client_secret") === SECRET && !u.search.includes(SECRET);
      if (!ok) return Response.json({ message: "invalid client secret" }, { status: 403 });
      const n = await (await R("/token", {})).text();
      return Response.json({ access_token: "tok" + n, expires_in: 5000000 });
    }
    if (u.pathname.startsWith("/igdb/")) {
      const n = await (await R("/current")).text();
      if (req.headers.get("authorization") !== `Bearer tok${n}`) return new Response("", { status: 401 });
      await new Promise((r) => setTimeout(r, 100));
      return Response.json([{ id: 1, name: "Super Mario World", summary: "Mock summary (staging).", first_release_date: 658540800, genres: [{ id: 8, name: "Platform" }] }]);
    }
    if (u.pathname === "/ss/jeuInfos.php" || u.pathname === "/ss/ssuserInfos.php") {
      const ok = u.searchParams.get("devpassword") === DEVPW && u.searchParams.get("ssid") === "player1" && u.searchParams.get("sspassword") === "userpw123";
      if (!ok) return new Response("Erreur de login : Verifier vos identifiants utilisateurs !", { status: 403 });
      const media = (m, file = "mediaJeu.php") => `https://neoclone.screenscraper.fr/api2/${file}?devid=${DEV}&devpassword=${DEVPW}&softname=Voltcap&ssid=player1&sspassword=userpw123&systemeid=4&jeuid=1&media=${m}`;
      const jeu = { id: "1", noms: [{ region: "us", text: "Super Mario World" }], systeme: { id: "4" }, synopsis: [{ langue: "en", text: "Mock synopsis (staging)." }],
        rom: { romfilename: "Super Mario World (USA).sfc" },
        medias: [{ type: "box-2D", region: "us", url: media("box-2D(us)") }, { type: "manuel", region: "us", url: media("manuel(us)", "mediaManuelJeu.php") },
          { type: "video-normalized", url: media("video-normalized", "mediaVideoJeu.php") }, { type: "ss", region: "wor", url: media("ss(wor)") }] };
      return new Response(JSON.stringify({ response: { ssuser: { id: "player1", maxthreads: "1" }, jeu } }).replace(/\//g, "\\/"), { headers: { "content-type": "application/json" } });
    }
    if (/^\/ss\/media\w+\.php$/.test(u.pathname) || u.pathname === "/ss/redirected.php") {
      const m = u.searchParams.get("media") || "";
      if (u.pathname !== "/ss/redirected.php" && u.searchParams.get("devpassword") !== DEVPW) return new Response("Erreur", { headers: { "content-type": "text/html" } });
      if (m === "redir") return Response.redirect(`${u.origin}/ss/redirected.php?x=1`, 302);
      if (m === "evil") return new Response(null, { status: 302, headers: { location: "https://evil.example/steal" } });
      if (m === "slow") {
        // 1 KB every 200 ms for 20 s; a cancel from the reader is recorded.
        let i = 0, timer;
        const stream = new ReadableStream({
          pull(c) { return new Promise((r) => { timer = setTimeout(() => { if (i++ >= 100) { c.close(); } else c.enqueue(new Uint8Array(1024).fill(1)); r(); }, 200); }); },
          cancel() { clearTimeout(timer); ctx.waitUntil(R("/mark", { id, aborted: true })); },
        });
        ctx.waitUntil(R("/mark", { id, aborted: false }));
        return new Response(stream, { headers: { "content-type": "video/mp4", "content-length": String(100 * 1024) } });
      }
      if (m.startsWith("manuel")) return new Response(PDF, { headers: { "content-type": "application/force-download; name=\"mock-manual.pdf\"", "content-length": String(PDF.length) } });
      if (m.startsWith("video")) {
        const data = new Uint8Array(5000).fill(7);
        const r = /bytes=(\d+)-(\d+)/.exec(req.headers.get("range") || "");
        if (r) { const a = +r[1], b = +r[2]; return new Response(data.slice(a, b + 1), { status: 206, headers: { "content-type": "video/mp4", "content-range": `bytes ${a}-${b}/5000`, "content-length": String(b - a + 1) } }); }
        return new Response(data, { headers: { "content-type": "video/mp4", "content-length": "5000" } });
      }
      const range = /bytes=(\d+)-(\d+)/.exec(req.headers.get("range") || "");
      if (range) { const a = +range[1], b = Math.min(+range[2], PNG.length - 1); return new Response(PNG.slice(a, b + 1), { status: 206, headers: { "content-type": "video/mp4", "content-range": `bytes ${a}-${b}/${PNG.length}`, "content-length": String(b - a + 1) } }); }
      return new Response(PNG, { headers: { "content-type": "image/png", "content-length": String(PNG.length) } });
    }
    return new Response("not found", { status: 404 });
  },
};
