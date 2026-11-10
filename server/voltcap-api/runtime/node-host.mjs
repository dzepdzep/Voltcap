// A stand-in host for runtime/check.mjs where workerd isn't available: serves the Worker over real HTTP from Node, with
// one in-memory Durable Object per name and an in-memory Cache. Not a substitute for `wrangler dev` (workerd) before deploy.
import http from "node:http";
import { readFileSync } from "node:fs";
import worker, { IgdbGate, SsGate } from "../src/index.js";
const env = {};
for (const l of readFileSync(new URL("./dev.vars.example", import.meta.url), "utf8").split("\n")) {
  const m = /^([A-Z_]+)=(.*)$/.exec(l.trim()); if (m) env[m[1]] = m[2];
}
class Storage { constructor() { this.m = new Map(); } async get(k) { return this.m.get(k); } async put(k, v) { this.m.set(k, v); } }
function ns(Cls) { const o = new Map(); return { idFromName: (n) => n, get: (id) => { if (!o.has(id)) o.set(id, new Cls({ storage: new Storage() }, env)); const x = o.get(id); return { fetch: (u, i) => x.fetch(new Request(u, i)) }; } }; }
env.IGDB_GATE = ns(IgdbGate); env.SS_GATE = ns(SsGate);
const store = new Map();
env.__cache = { async match(r) { const v = store.get(r.url); return v ? new Response(v.body, { headers: v.headers }) : undefined; },
  async put(r, res) { store.set(r.url, { body: new Uint8Array(await res.arrayBuffer()), headers: Object.fromEntries(res.headers) }); } };
http.createServer(async (req, res) => {
  const ac = new AbortController(); res.on("close", () => { if (!res.writableFinished) ac.abort(); });
  const chunks = []; for await (const c of req) chunks.push(c);
  const r = new Request("http://127.0.0.1:8787" + req.url, { method: req.method, headers: { ...req.headers, "cf-connecting-ip": "127.0.0.1" },
    body: ["GET", "HEAD"].includes(req.method) ? undefined : Buffer.concat(chunks), signal: ac.signal });
  const out = await worker.fetch(r, env, { waitUntil: () => {} });
  res.writeHead(out.status, Object.fromEntries(out.headers));
  if (!out.body) { res.end(); return; }
  const reader = out.body.getReader();
  ac.signal.addEventListener("abort", () => reader.cancel().catch(() => {}));
  try { for (;;) { const { done, value } = await reader.read(); if (done) break; if (!res.write(value)) await new Promise((r) => res.once("drain", r)); } } catch {}
  res.end();
}).listen(8787, "127.0.0.1", () => process.stdout.write("worker (node host) on 8787\n"));
