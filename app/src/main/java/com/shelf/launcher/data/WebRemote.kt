package com.shelf.launcher.data

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Phone web remote: a small page served by Voltcap on the local network (port [PORT]). Open the
 * address on your phone, enter the 4-digit code shown in Settings, and you get a D-pad, A / B /
 * Start / Select, and a searchable game list that starts a game on the handheld.
 *
 * Only works while Voltcap is in front (the buttons are fed to Voltcap itself, not to other apps),
 * only on the same Wi-Fi, and every request must carry the code.
 */
object WebRemote {
    const val PORT = 8765

    /** Feeds a button to Voltcap ("UP", "A", "START"…). Set by MainActivity. */
    @Volatile var onKey: ((String) -> Unit)? = null
    /** Starts the game with this key. Set by the home screen. */
    @Volatile var onLaunch: ((String) -> Unit)? = null
    /** (key, name, console) of every game. Set by the home screen. */
    @Volatile var games: () -> List<Triple<String, String, String>> = { emptyList() }

    @Volatile private var server: ServerSocket? = null

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("web_remote", Context.MODE_PRIVATE)

    fun enabled(c: Context) = prefs(c).getBoolean("on", false)

    fun pin(c: Context): String = prefs(c).getString("pin", null) ?: (1000 + kotlin.random.Random.nextInt(9000)).toString().also {
        prefs(c).edit().putString("pin", it).apply()
    }

    fun newPin(c: Context): String { prefs(c).edit().remove("pin").apply(); return pin(c) }

    fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("on", on).apply()
        if (on) start(c) else stop()
    }

    val running: Boolean get() = server != null

    /** http://<wifi address>:PORT, or null without a local network. */
    fun address(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()?.let { "http://$it:$PORT" }

    fun start(c: Context) {
        if (server != null) return
        val code = pin(c)
        val ss = runCatching { ServerSocket(PORT) }.getOrNull() ?: return
        server = ss
        Thread {
            while (!ss.isClosed) {
                val s = runCatching { ss.accept() }.getOrNull() ?: break
                // A phone sends a few requests at a time: more than this at once is not a remote.
                if (busy.incrementAndGet() > 16) { busy.decrementAndGet(); runCatching { s.close() }; continue }
                Thread {
                    try { runCatching { handle(s, code) } } finally { runCatching { s.close() }; busy.decrementAndGet() }
                }.apply { isDaemon = true }.start()
            }
        }.apply { isDaemon = true; name = "WebRemote" }.start()
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private val busy = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * Wrong codes per address: after [MAX_TRIES] that address is shut out for [LOCK_MS] (a 4-digit code
     * could otherwise be guessed by trying all 10 000 from another device on the Wi-Fi).
     */
    private class Tries(var count: Int, var lockedUntil: Long)
    private val tries = HashMap<String, Tries>()
    private const val MAX_TRIES = 8
    private const val LOCK_MS = 5 * 60_000L

    private fun lockedOut(ip: String): Boolean = synchronized(tries) {
        val t = tries[ip] ?: return false
        if (t.lockedUntil > System.currentTimeMillis()) return true
        if (t.lockedUntil != 0L) { tries.remove(ip); return false }
        false
    }

    private fun noteWrong(ip: String) = synchronized(tries) {
        val t = tries.getOrPut(ip) { Tries(0, 0L) }
        t.count++
        if (t.count >= MAX_TRIES) { t.lockedUntil = System.currentTimeMillis() + LOCK_MS; t.count = 0 }
        if (tries.size > 200) tries.entries.removeAll { it.value.lockedUntil < System.currentTimeMillis() && it.value.count == 0 }
    }

    private fun noteRight(ip: String) = synchronized(tries) { tries.remove(ip) }

    private val KEYS = setOf("UP", "DOWN", "LEFT", "RIGHT", "A", "B", "X", "Y", "START", "SELECT", "L1", "R1")

    private fun handle(s: Socket, code: String) {
        s.soTimeout = 5000
        val reader = BufferedReader(InputStreamReader(s.getInputStream()))
        val line = reader.readLine() ?: return
        val target = line.split(' ').getOrNull(1) ?: "/"
        val path = target.substringBefore('?')
        val q = target.substringAfter('?', "").split('&').filter { '=' in it }
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        val ip = s.inetAddress?.hostAddress ?: "?"
        if (path != "/" && lockedOut(ip)) { respond(s, 429, "text/plain", "Too many wrong codes. Try again in a few minutes."); return }
        val authed = q["t"] == code
        if (path != "/") { if (authed) noteRight(ip) else noteWrong(ip) }
        val (type, body) = when {
            path == "/" -> "text/html; charset=utf-8" to PAGE
            !authed -> { Thread.sleep(300); respond(s, 403, "text/plain", "Wrong code"); return }
            path == "/check" -> "text/plain" to "ok"
            path == "/key" && q["k"] in KEYS -> { onKey?.invoke(q["k"]!!); "text/plain" to "ok" }
            path == "/launch" && q["id"] != null -> { onLaunch?.invoke(q["id"]!!); "text/plain" to "ok" }
            path == "/games" -> {
                val term = q["q"].orEmpty().lowercase()
                val list = games().filter { term.isBlank() || it.second.lowercase().contains(term) }.take(60)
                "application/json" to list.joinToString(",", "[", "]") { (k, n, p) ->
                    "{\"id\":${json(k)},\"name\":${json(n)},\"console\":${json(p)}}"
                }
            }
            else -> { respond(s, 404, "text/plain", "Not found"); return }
        }
        respond(s, 200, type, body)
    }

    private fun json(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""

    private fun respond(s: Socket, status: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $status ${if (status == 200) "OK" else "Error"}\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        s.getOutputStream().apply { write(head.toByteArray()); write(bytes); flush() }
    }

    private val PAGE = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>Voltcap remote</title><style>
:root{--bg:#110c08;--fg:#fbf5ec;--ac:#f59a2c;--mu:#b8a994;--sf:#1f1812}
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}body{margin:0;font-family:system-ui,sans-serif;background:var(--bg);color:var(--fg);padding:16px;max-width:520px;margin:auto}
h1{font-size:18px;color:var(--ac)}input{width:100%;padding:12px;border-radius:12px;border:1px solid #444;background:var(--sf);color:var(--fg);font-size:16px}
.pad{display:grid;grid-template-columns:repeat(3,64px);gap:8px;justify-content:center;margin:16px 0}
button{border:0;border-radius:14px;background:var(--sf);color:var(--fg);font-size:18px;font-weight:600;height:64px}
button:active{background:var(--ac);color:#000}.row{display:flex;gap:8px;justify-content:center;margin:8px 0}.row button{flex:1}
.g{display:block;width:100%;text-align:left;height:auto;padding:12px;margin:6px 0;font-size:15px}.g small{color:var(--mu);display:block;font-weight:400}
#login{margin-top:30vh}</style></head><body>
<div id="login"><h1>Voltcap remote</h1><p style="color:var(--mu)">Enter the code from Settings › Controls › Phone remote.</p>
<input id="pin" inputmode="numeric" maxlength="4" placeholder="Code"><p></p><button style="width:100%" onclick="login()">Connect</button><p id="err" style="color:#f66"></p></div>
<div id="app" hidden><h1>Voltcap remote</h1>
<div class="pad"><span></span><button onclick="k('UP')">▲</button><span></span><button onclick="k('LEFT')">◀</button><button onclick="k('A')">A</button><button onclick="k('RIGHT')">▶</button><span></span><button onclick="k('DOWN')">▼</button><span></span></div>
<div class="row"><button onclick="k('B')">B</button><button onclick="k('X')">X</button><button onclick="k('Y')">Y</button></div>
<div class="row"><button onclick="k('L1')">L1</button><button onclick="k('SELECT')">Select</button><button onclick="k('START')">Start</button><button onclick="k('R1')">R1</button></div>
<h1 style="margin-top:24px">Start a game</h1><input id="q" placeholder="Search your library" oninput="find()"><div id="list"></div></div>
<script>
let t=localStorage.getItem('vc_pin')||'';
async function login(){t=document.getElementById('pin').value.trim();const r=await fetch('/check?t='+t);if(r.ok){localStorage.setItem('vc_pin',t);show()}else document.getElementById('err').textContent=r.status==429?'Too many wrong codes: wait a few minutes':'Wrong code'}
function show(){document.getElementById('login').hidden=true;document.getElementById('app').hidden=false;find()}
function k(x){fetch('/key?k='+x+'&t='+t);if(navigator.vibrate)navigator.vibrate(15)}
let timer;function find(){clearTimeout(timer);timer=setTimeout(async()=>{const q=document.getElementById('q').value;const r=await fetch('/games?t='+t+'&q='+encodeURIComponent(q));if(!r.ok)return;const g=await r.json();const l=document.getElementById('list');l.innerHTML='';g.forEach(x=>{const b=document.createElement('button');b.className='g';b.innerHTML='';b.textContent=x.name;const s=document.createElement('small');s.textContent=x.console;b.appendChild(s);b.onclick=()=>{if(confirm('Start '+x.name+'?'))fetch('/launch?id='+encodeURIComponent(x.id)+'&t='+t)};l.appendChild(b)})},200)}
if(t)fetch('/check?t='+t).then(r=>{if(r.ok)show()});
</script></body></html>"""
}
