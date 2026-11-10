// Fake Twitch / IGDB / ScreenScraper on http://127.0.0.1:8788 for runtime tests of the Worker (fake credentials only).
// Records what it received (GET /__calls) so the checks can verify the Worker added / removed the right things.
import http from "node:http";
const PORT = Number(process.env.MOCK_PORT || 8788);
const DEV = "fakedevid", DEVPW = "FAKE_SS_DEVPW_987", SECRET = "FAKE_TWITCH_SECRET_123456789";
const calls = [];
let tokenNo = 0;
const media = (q) => `https://neoclone.screenscraper.fr/api2/mediaJeu.php?devid=${DEV}&devpassword=${DEVPW}&softname=Voltcap&ssid=&sspassword=&systemeid=4&jeuid=1&media=${q}`;
http.createServer(async (req, res) => {
  const u = new URL(req.url, "http://x");
  let body = ""; for await (const c of req) body += c;
  if (u.pathname === "/__calls") { res.end(JSON.stringify(calls)); return; }
  const call = { path: u.pathname, query: u.search, body, range: req.headers.range || null, auth: req.headers.authorization || null, at: Date.now() };
  calls.push(call);
  if (u.pathname === "/twitch") {
    const ok = new URLSearchParams(body).get("client_secret") === SECRET && !u.search.includes(SECRET);
    res.writeHead(ok ? 200 : 403, { "content-type": "application/json" });
    res.end(JSON.stringify(ok ? { access_token: "tok" + (++tokenNo), expires_in: 5000000 } : { message: "invalid client secret" })); return;
  }
  if (u.pathname.startsWith("/igdb/")) {
    if (req.headers.authorization !== `Bearer tok${tokenNo}`) { res.writeHead(401); res.end(); return; }
    await new Promise((r) => setTimeout(r, 100)); // IGDB takes a moment
    res.writeHead(200, { "content-type": "application/json" }); res.end(JSON.stringify([{ id: 1, name: "Super Mario World" }])); return;
  }
  if (u.pathname === "/ss/jeuInfos.php") {
    const ok = u.searchParams.get("devpassword") === DEVPW && u.searchParams.get("ssid") === "player1" && u.searchParams.get("sspassword") === "userpw123";
    res.writeHead(ok ? 200 : 403, { "content-type": "application/json" });
    res.end(ok ? JSON.stringify({ response: { ssuser: { id: "player1", maxthreads: "1" }, jeu: { medias: [{ type: "box-2D", url: media("box-2D(us)") }] } } }).replace(/\//g, "\\/") : "Erreur de login"); return;
  }
  if (u.pathname === "/ss/mediaJeu.php" || u.pathname === "/ss/redirected.php") {
    if (u.searchParams.get("devpassword") !== DEVPW && u.pathname !== "/ss/redirected.php") { res.writeHead(200, { "content-type": "text/html" }); res.end("Erreur"); return; }
    if (u.searchParams.get("media") === "redir") { res.writeHead(302, { location: `http://127.0.0.1:${PORT}/ss/redirected.php?x=1` }); res.end(); return; }
    if (u.searchParams.get("media") === "slow") { // 1 KB every 200 ms for 20 s: records whether the Worker gave up early
      res.writeHead(200, { "content-type": "video/mp4", "content-length": String(100 * 1024) });
      call.aborted = false;
      res.on("close", () => { if (!res.writableFinished) call.aborted = true; });
      for (let i = 0; i < 100 && !res.destroyed; i++) { res.write(Buffer.alloc(1024, 1)); await new Promise((r) => setTimeout(r, 200)); }
      res.end(); return;
    }
    if (u.searchParams.get("media") === "evil") { res.writeHead(302, { location: "https://evil.example/steal" }); res.end(); return; }
    const data = Buffer.alloc(5000, 7);
    const m = /bytes=(\d+)-(\d+)/.exec(req.headers.range || "");
    if (m) { const a = +m[1], b = +m[2]; res.writeHead(206, { "content-type": "video/mp4", "content-range": `bytes ${a}-${b}/5000`, "content-length": String(b - a + 1) }); res.end(data.subarray(a, b + 1)); return; }
    res.writeHead(200, { "content-type": "image/png", "content-length": "5000" }); res.end(data); return;
  }
  res.writeHead(404); res.end();
}).listen(PORT, "127.0.0.1", () => process.stdout.write(`mock upstream on ${PORT}\n`));
