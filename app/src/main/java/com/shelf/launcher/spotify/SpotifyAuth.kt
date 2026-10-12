package com.shelf.launcher.spotify

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.shelf.launcher.data.SecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

enum class SpotifyConnection { DISCONNECTED, CONNECTING, CONNECTED, NEEDS_RECONNECT, NO_CLIENT_ID }

/**
 * Spotify sign-in: Authorization Code + PKCE (no client secret exists in the app).
 * The user brings their own Client ID; tokens live only in the encrypted
 * "spotify_encrypted" store (never logged, never backed up). One process-wide
 * instance so every screen sees the same state.
 */
class SpotifyAuth private constructor(app: Context) {
    private val appContext = app.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var catcher: LoopbackCatcher? = null
    private val refreshLock = Mutex()
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    private val prefsLazy = lazy { SecureStore.open(appContext, STORE, SERVICE).prefs }
    private val prefs get() = prefsLazy.value

    @Volatile private var connecting = false
    private val _state = MutableStateFlow(SpotifyConnection.DISCONNECTED)
    val state: StateFlow<SpotifyConnection> = _state
    private val _error = MutableStateFlow<String?>(null)
    /** Last sign-in problem in plain words (never contains a token); null when fine. */
    val lastError: StateFlow<String?> = _error

    init { scope.launch { recompute() } }

    private fun recompute() {
        val s = when {
            connecting -> SpotifyConnection.CONNECTING
            prefs.getString(K_CLIENT, null).isNullOrBlank() -> SpotifyConnection.NO_CLIENT_ID
            !prefs.getString(K_REFRESH, null).isNullOrEmpty() -> SpotifyConnection.CONNECTED
            prefs.getBoolean(K_NEEDS_RECONNECT, false) || SERVICE in SecureStore.pendingRelogin(appContext) ->
                SpotifyConnection.NEEDS_RECONNECT
            else -> SpotifyConnection.DISCONNECTED
        }
        _state.value = s
    }

    // ---- Client ID ----
    suspend fun clientId(): String = withContext(Dispatchers.IO) { prefs.getString(K_CLIENT, null).orEmpty() }

    /** Saves the Client ID. A different ID invalidates any existing sign-in (tokens belong to one app). */
    suspend fun setClientId(id: String) = withContext(Dispatchers.IO) {
        val clean = id.trim()
        val old = prefs.getString(K_CLIENT, null).orEmpty()
        if (clean == old) return@withContext
        prefs.edit().putString(K_CLIENT, clean).commit()
        if (old.isNotEmpty()) clearTokens(flag = false)
        recompute()
    }

    // ---- Login ----
    /** Opens Spotify's consent page in a Custom Tab. False when there's no Client ID or no browser. */
    suspend fun beginLogin(context: Context): Boolean {
        val url = withContext(Dispatchers.IO) {
            val cid = prefs.getString(K_CLIENT, null)?.trim().orEmpty()
            if (cid.isEmpty()) return@withContext null
            val verifier = randomString(64)
            val state = randomString(32)
            prefs.edit().putString(K_VERIFIER, verifier).putString(K_STATE, state).commit()
            connecting = true
            _error.value = null
            recompute()
            // The redirect comes back to a one-shot receiver on 127.0.0.1 (Spotify no longer accepts
            // custom voltcap:// redirects for new apps). Its exact address is kept for the code swap.
            catcher?.close()
            val c = runCatching {
                LoopbackCatcher(onRedirect = { uri -> handleCallback(uri) }, onTimeout = {
                    if (connecting) {
                        cancelLogin()
                        _error.value = "Spotify sign-in timed out. Try Connect again."
                    }
                })
            }.getOrElse {
                _error.value = "Couldn't start the sign-in receiver (port ${LoopbackCatcher.PORT} busy). Close and reopen Voltcap, then try again."
                connecting = false
                recompute()
                return@withContext null
            }
            catcher = c
            c.start()
            prefs.edit().putString(K_REDIRECT, c.redirectUri).commit()
            Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
                .appendQueryParameter("client_id", cid)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", c.redirectUri)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", challengeFor(verifier))
                .appendQueryParameter("state", state)
                .appendQueryParameter("scope", SCOPES)
                .build()
        } ?: return false
        return withContext(Dispatchers.Main) {
            try {
                val intent = CustomTabsIntent.Builder().build().intent.setData(url)
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                withContext(Dispatchers.IO) { cancelLogin() }
                _error.value = "No web browser found to sign in with."
                false
            }
        }
    }

    /** Forgets a half-finished sign-in (the user closed the browser tab). */
    fun cancelLogin() {
        catcher?.close()
        catcher = null
        scope.launch {
            prefs.edit().remove(K_VERIFIER).remove(K_STATE).commit()
            connecting = false
            recompute()
        }
    }

    /** Called by [SpotifyAuthActivity] with the redirect. Verifies `state`, then swaps the code for tokens. */
    fun handleCallback(uri: Uri) {
        scope.launch {
            val expectedState = prefs.getString(K_STATE, null)
            val verifier = prefs.getString(K_VERIFIER, null)
            val fail = { msg: String -> connecting = false; _error.value = msg; recompute() }
            val gotState = uri.getQueryParameter("state")
            if (expectedState.isNullOrEmpty() || verifier.isNullOrEmpty() || gotState == null ||
                !MessageDigest.isEqual(expectedState.toByteArray(), gotState.toByteArray())
            ) {
                // A forged/stale callback is rejected but must not abort a real sign-in in progress:
                // the stored state and verifier stay for the genuine redirect.
                _error.value = "Sign-in was not started from Voltcap or has expired. Try Connect again."
                return@launch
            }
            // One-shot: only a matching redirect consumes the state, so a replay finds nothing.
            prefs.edit().remove(K_STATE).remove(K_VERIFIER).commit()
            uri.getQueryParameter("error")?.let {
                return@launch fail(if (it == "access_denied") "Spotify sign-in was cancelled." else "Spotify refused the sign-in ($it).")
            }
            val code = uri.getQueryParameter("code") ?: return@launch fail("Spotify sent no sign-in code.")
            val cid = prefs.getString(K_CLIENT, null).orEmpty()
            try {
                val body = FormBody.Builder()
                    .add("grant_type", "authorization_code").add("code", code)
                    .add("redirect_uri", prefs.getString(K_REDIRECT, null) ?: REDIRECT_URI).add("client_id", cid)
                    .add("code_verifier", verifier).build()
                val result = tokenRequest(body)
                if (result.error != null || result.json == null) {
                    return@launch fail(
                        if (result.error == "invalid_client") "Spotify doesn't recognise that Client ID."
                        else "Spotify couldn't finish sign-in (${result.error ?: "error"}). Check the redirect URI in your Spotify app."
                    )
                }
                saveTokens(result.json)
                prefs.edit().remove(K_NEEDS_RECONNECT).commit()
                SecureStore.clearRelogin(appContext, SERVICE)
                connecting = false
                _error.value = null
                recompute()
            } catch (e: IOException) {
                fail("Couldn't reach Spotify. Check the connection and try again.")
            }
        }
    }

    /** Signs out on this device (keeps the Client ID). */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        connecting = false
        clearTokens(flag = false)
        SecureStore.clearRelogin(appContext, SERVICE)
        _error.value = null
        recompute()
    }

    // ---- Tokens ----
    /** A usable access token, refreshing first if it is (nearly) expired. Null when not signed in. Throws IOException on network trouble. */
    suspend fun accessToken(): String? {
        val (t, exp) = withContext(Dispatchers.IO) { prefs.getString(K_ACCESS, null) to prefs.getLong(K_EXPIRES, 0L) }
        if (!t.isNullOrEmpty() && System.currentTimeMillis() < exp - SKEW_MS) return t
        return refresh(stale = t)
    }

    /** After a 401: refresh unless someone else already replaced [rejected]. */
    suspend fun refreshAfterRejection(rejected: String): String? = refresh(stale = rejected)

    /** Single-flight: concurrent callers queue on the mutex and reuse the winner's fresh token. */
    private suspend fun refresh(stale: String?): String? = refreshLock.withLock {
        withContext(Dispatchers.IO) {
            val cur = prefs.getString(K_ACCESS, null)
            val exp = prefs.getLong(K_EXPIRES, 0L)
            if (!cur.isNullOrEmpty() && cur != stale && System.currentTimeMillis() < exp - SKEW_MS) return@withContext cur
            val rt = prefs.getString(K_REFRESH, null)
            if (rt.isNullOrEmpty()) return@withContext null
            val cid = prefs.getString(K_CLIENT, null).orEmpty()
            val body = FormBody.Builder().add("grant_type", "refresh_token")
                .add("refresh_token", rt).add("client_id", cid).build()
            val result = tokenRequest(body) // IOException propagates: offline is not "logged out"
            when {
                result.json != null -> { saveTokens(result.json); result.json.get("access_token").asString }
                result.error == "invalid_grant" || result.error == "invalid_client" -> {
                    // Refresh token revoked/expired: one clean login is required.
                    clearTokens(flag = true)
                    recompute()
                    null
                }
                else -> throw IOException("Spotify token service unavailable")
            }
        }
    }

    private class TokenResult(val json: JsonObject?, val error: String?)

    private fun tokenRequest(body: FormBody): TokenResult {
        val req = Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            val obj = runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull()
            if (r.isSuccessful && obj != null && obj.has("access_token")) return TokenResult(obj, null)
            val err = obj?.get("error")?.takeIf { it.isJsonPrimitive }?.asString
            return if (r.code in 400..499) TokenResult(null, err ?: "http_${r.code}") else throw IOException("HTTP ${r.code}")
        }
    }

    private fun saveTokens(json: JsonObject) {
        val expiresIn = json.get("expires_in")?.asLong ?: 3600L
        val e = prefs.edit()
            .putString(K_ACCESS, json.get("access_token").asString)
            .putLong(K_EXPIRES, System.currentTimeMillis() + expiresIn * 1000)
        // Spotify may rotate the refresh token; keep the old one when it doesn't.
        json.get("refresh_token")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotEmpty() }
            ?.let { e.putString(K_REFRESH, it) }
        json.get("scope")?.takeIf { it.isJsonPrimitive }?.asString?.let { e.putString(K_SCOPES, it) }
        e.commit() // commit: losing a rotated refresh token would force a re-login
    }

    private fun clearTokens(flag: Boolean) {
        val e = prefs.edit().remove(K_ACCESS).remove(K_EXPIRES).remove(K_REFRESH).remove(K_SCOPES)
        if (flag) e.putBoolean(K_NEEDS_RECONNECT, true) else e.remove(K_NEEDS_RECONNECT)
        e.commit()
        if (flag) SecureStore.flagRelogin(appContext, SERVICE)
    }

    /** Scopes Spotify actually granted (space-separated), or empty. */
    suspend fun grantedScopes(): String = withContext(Dispatchers.IO) { prefs.getString(K_SCOPES, null).orEmpty() }

    companion object {
        const val STORE = "spotify_encrypted"
        const val SERVICE = "Spotify"
        /** Exactly what goes into the Spotify app's "Redirect URIs" (loopback with a fixed port —
         * the dashboard refuses it without the port). */
        const val REDIRECT_URI = "http://127.0.0.1:${LoopbackCatcher.PORT}/callback"
        private const val K_REDIRECT = "pending_redirect"
        const val SCOPES = "user-read-playback-state user-modify-playback-state user-read-currently-playing " +
            "playlist-read-private playlist-read-collaborative user-library-read user-read-recently-played user-follow-read"
        private const val SKEW_MS = 60_000L
        private const val K_CLIENT = "client_id"
        private const val K_ACCESS = "access_token"
        private const val K_EXPIRES = "expires_at"
        private const val K_REFRESH = "refresh_token"
        private const val K_SCOPES = "scopes"
        private const val K_VERIFIER = "pending_verifier"
        private const val K_STATE = "pending_state"
        private const val K_NEEDS_RECONNECT = "needs_reconnect"
        private const val URL_SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

        @Volatile private var instance: SpotifyAuth? = null
        fun get(context: Context): SpotifyAuth = instance ?: synchronized(this) {
            instance ?: SpotifyAuth(context.applicationContext).also { instance = it }
        }

        /** PKCE verifier / state: RFC 7636 unreserved characters from SecureRandom. */
        internal fun randomString(len: Int): String {
            val r = SecureRandom()
            return buildString(len) { repeat(len) { append(URL_SAFE[r.nextInt(URL_SAFE.length)]) } }
        }

        /** S256 challenge: base64url(SHA-256(verifier)) without padding. */
        internal fun challengeFor(verifier: String): String =
            Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
    }
}
