package com.shelf.launcher.spotify

import android.net.Uri
import android.util.Log
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException

/**
 * Spotify only accepts https or loopback (http://127.0.0.1) redirect URIs for new apps, so the sign-in
 * comes back to a tiny one-shot web server inside Voltcap on 127.0.0.1 (never reachable from outside the
 * device). It answers exactly one request, hands the redirect to [onRedirect], shows a "back to Voltcap"
 * page, and closes. Gives up after [TIMEOUT_MS].
 */
internal class LoopbackCatcher(private val onRedirect: (Uri) -> Unit, private val onTimeout: () -> Unit = {}) {
    // A fixed port: Spotify's dashboard rejects a loopback address without one, so the exact
    // address (with this port) is what gets registered there.
    private val server = ServerSocket().apply {
        reuseAddress = true
        bind(java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 4)
        soTimeout = TIMEOUT_MS
    }
    val port: Int get() = server.localPort
    val redirectUri: String get() = "http://127.0.0.1:$port/callback"

    fun start() {
        Thread {
            try {
                // Browsers may first ask for /favicon.ico etc.: keep accepting until the callback arrives.
                while (!server.isClosed) {
                    val socket = server.accept()
                    // One silent or broken connection (a browser's spare pre-connect) must not end the sign-in:
                    // its own read timeout only drops that connection, and the loop takes the next one.
                    try { socket.use { s ->
                        s.soTimeout = 10_000
                        val line = s.getInputStream().bufferedReader().readLine().orEmpty() // "GET /callback?code=… HTTP/1.1"
                        val path = line.split(' ').getOrNull(1).orEmpty()
                        val isCallback = path.startsWith("/callback")
                        val body = if (isCallback) PAGE else "Not found"
                        val out = s.getOutputStream()
                        out.write(
                            ("HTTP/1.1 ${if (isCallback) "200 OK" else "404 Not Found"}\r\n" +
                                "Content-Type: text/html; charset=utf-8\r\nConnection: close\r\n" +
                                "Content-Length: ${body.toByteArray().size}\r\n\r\n$body").toByteArray()
                        )
                        out.flush()
                        if (isCallback) {
                            onRedirect(Uri.parse("http://127.0.0.1:$port$path"))
                            server.close()
                        }
                    } } catch (e: java.io.IOException) {
                        if (server.isClosed) throw e
                    }
                }
            } catch (_: SocketTimeoutException) {
                // Nobody came back within the time limit: the sign-in is over (not "connecting" forever).
                runCatching(onTimeout)
            } catch (e: Exception) {
                if (!server.isClosed) Log.w("LoopbackCatcher", "sign-in receiver failed", e)
            } finally {
                runCatching { server.close() }
            }
        }.apply { isDaemon = true; name = "spotify-signin" }.start()
    }

    fun close() = runCatching { server.close() }

    companion object {
        const val TIMEOUT_MS = 10 * 60_000
        const val PORT = 43821
        // Brings Voltcap back to the front (the voltcap:// link is handled by SpotifyAuthActivity).
        private const val BACK = "intent://spotify-callback?done=1#Intent;scheme=voltcap;package=com.shelf.launcher;end"
        private val PAGE = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Voltcap</title></head>
            <body style="background:#111;color:#eee;font-family:sans-serif;text-align:center;padding-top:20vh">
            <h2>Spotify is connected</h2><p>You can go back to Voltcap.</p>
            <p><a style="color:#ffd84a;font-size:1.3em" href="$BACK">Back to Voltcap</a></p>
            <script>setTimeout(function(){location.href="$BACK";},300);</script>
            </body></html>
        """.trimIndent()
    }
}
