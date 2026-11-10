package com.shelf.launcher.data

import android.content.Context
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor

/**
 * Batch 517 (security): Voltcap's own service (server/voltcap-api, a Cloudflare Worker). It holds Voltcap's IGDB (Twitch)
 * client secret and ScreenScraper developer key, so neither is in the APK any more (0.6.68 had them in BuildConfig, readable
 * by anyone with the APK). Only its address is built in (BuildConfig.VOLTCAP_API_BASE — not a secret); a build without it
 * (a fork) simply shows the developer-key fields as before 0.6.68.
 *
 * The app sends a random installation ID (made here once, never shared with anything else, never backed up). It is NOT
 * authentication — anyone can make one — only the key the service's fair-use limits count by.
 */
object VoltcapService {
    /** The marker saved as "developer key / client ID" when Voltcap's service is used (never a real key). */
    const val SERVICE_ID = "@voltcap-service"

    val baseUrl: HttpUrl? by lazy { com.shelf.launcher.BuildConfig.VOLTCAP_API_BASE.trim().trimEnd('/').takeIf { it.isNotEmpty() }?.toHttpUrlOrNull() }
    val configured: Boolean get() = baseUrl != null
    /** The address with a closing "/", as Retrofit wants it. */
    val retrofitBase: HttpUrl? get() = baseUrl?.let { (it.toString().trimEnd('/') + "/").toHttpUrlOrNull() }

    fun isService(c: ScreenScraperCredentials?): Boolean = c?.devId == SERVICE_ID
    fun isService(c: IgdbCredentials?): Boolean = c?.clientId == SERVICE_ID
    fun isServiceUrl(url: String): Boolean = baseUrl?.let { url.startsWith(it.toString().trimEnd('/') + "/") } == true

    private const val PREFS = "voltcap_service"
    @Volatile private var install: String? = null
    @Volatile private var app: Context? = null

    fun init(context: Context) { if (app == null) app = context.applicationContext }

    /** This installation's random ID (128 bits, URL-safe). */
    fun installId(): String {
        install?.let { return it }
        synchronized(this) {
            install?.let { return it }
            val p = app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val id = p?.getString("install_id", null) ?: run {
                val bytes = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
                android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
                    .also { p?.edit()?.putString("install_id", it)?.apply() }
            }
            install = id
            return id
        }
    }

    fun headers(): Map<String, String> = mapOf(
        "X-Voltcap-Install" to installId(),
        "X-Voltcap-Version" to com.shelf.launcher.BuildConfig.VERSION_NAME
    )

    /** Adds the installation ID to every request to the service (and nothing to any other host). */
    val interceptor = Interceptor { chain ->
        val req = chain.request()
        if (!isServiceUrl(req.url.toString())) chain.proceed(req)
        else chain.proceed(req.newBuilder().apply { headers().forEach { (k, v) -> header(k, v) } }.build())
    }

    /** The service's own explanation from an error answer ({"error":…, "message":…}), or null. */
    fun messageOf(body: String?): String? = runCatching {
        val o = com.google.gson.JsonParser.parseString(body ?: return null).asJsonObject
        if (o.has("error") && o.has("message")) o.get("message").asString else null
    }.getOrNull()
}
