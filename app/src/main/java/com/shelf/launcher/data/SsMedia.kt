package com.shelf.launcher.data

import android.content.Context
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.File

/**
 * Batch 517 (security): ScreenScraper media addresses without credentials.
 *
 * Confirmed on the RP Classic (QA app, 0.6.68): every address in a ScreenScraper answer (mediaJeu / mediaVideoJeu /
 * mediaManuelJeu / mediaFichierJeu / mediaGroup / mediaCompagnie .php) carries devid, devpassword, softname, ssid and
 * sspassword, and a download needs the developer key (without devpassword ScreenScraper answers with an error page).
 * 0.6.68 stored those addresses as they came: screenshots_v2, manual_urls, physical_media, art_cache and art_overrides —
 * so Export (even without game data), "Send my settings to Drive", nearby transfer, the Drive backup and the Drive sync of
 * picked covers all carried the developer password.
 *
 * Now:
 *  - an address is stored, exported and synced only [clean] (the five parameters removed);
 *  - the developer key and the account are put in only when a picture / clip / manual is downloaded ([resolve], through
 *    [interceptor] for the OkHttp clients and [forPlayer] for MediaPlayer) and never written anywhere: with Voltcap's own
 *    service the download goes through it (it holds the key); with a developer key you entered yourself, yours is added;
 *  - [migrateOnce] cleans what older versions stored, once, and renames the saved pictures to their clean address so no
 *    artwork is downloaded again.
 */
object SsMedia {
    val CREDENTIAL_PARAMS = listOf("devid", "devpassword", "softname", "ssid", "sspassword")
    /** An address in text: plain, or as JSON writes it (Gson's "\u003d" / "\u0026", and "\/"). */
    private val ANY_URL = Regex("""https?:(?:\\/|/){2}(?:[^\s"'<>\\]|\\/|\\u[0-9a-fA-F]{4})*""")
    private val U_ESCAPE = Regex("""\\u([0-9a-fA-F]{4})""")
    private fun decode(raw: String): String = U_ESCAPE.replace(raw.replace("\\/", "/")) { it.groupValues[1].toInt(16).toChar().toString() }
    private const val MIGRATION_VERSION = 1

    fun isScreenScraper(host: String): Boolean = host.equals("screenscraper.fr", true) || host.endsWith(".screenscraper.fr", true)

    /** [url] without credentials (other addresses unchanged). */
    fun clean(url: String): String {
        val h = url.toHttpUrlOrNull() ?: return url
        if (!isScreenScraper(h.host)) return url
        if (CREDENTIAL_PARAMS.none { h.queryParameter(it) != null || it in h.queryParameterNames }) return url
        val b = h.newBuilder()
        CREDENTIAL_PARAMS.forEach { b.removeAllQueryParameters(it) }
        return b.build().toString()
    }

    /** Every ScreenScraper address inside [text] (JSON, "label\taddress" lists, escaped "\/" too) cleaned. */
    fun cleanText(text: String): String {
        if (!text.contains("screenscraper", true)) return text
        return ANY_URL.replace(text) { m ->
            val raw = m.value
            val plain = decode(raw)
            val c = clean(plain)
            // (Still valid JSON: "=" and "&" need no escape; "\/" kept where the text used it.)
            if (c == plain) raw else if (raw.contains("\\/")) c.replace("/", "\\/") else c
        }
    }

    /** True when [text] still holds a ScreenScraper credential parameter (for checks and tests). */
    fun hasCredentials(text: String): Boolean =
        Regex("""(?:[?&]|\\u0026|\\u003f)(devpassword|sspassword|devid|ssid)(?:=|\\u003d)(?!&|\\u0026|"|\s|$)""", RegexOption.IGNORE_CASE).containsMatchIn(text)

    // ---- downloads ----

    @Volatile private var app: Context? = null
    @Volatile private var cached: Pair<Long, ScreenScraperCredentials?>? = null

    fun init(context: Context) { if (app == null) app = context.applicationContext }

    /** Forget the remembered sign-in (after connecting / disconnecting ScreenScraper). */
    fun signInChanged() { cached = null }

    private fun creds(): ScreenScraperCredentials? {
        val now = System.currentTimeMillis()
        cached?.let { (at, c) -> if (now - at < 30_000L) return c }
        val c = app?.let { runCatching { ScreenScraperStore(it).load() }.getOrNull() }
        cached = now to c
        return c
    }

    /** The address to actually download a stored ScreenScraper media address from (never stored). */
    fun resolve(url: HttpUrl, c: ScreenScraperCredentials? = creds()): HttpUrl {
        if (!isScreenScraper(url.host)) return url
        val file = url.pathSegments.lastOrNull().orEmpty()
        if (!file.startsWith("media")) return url
        val clean = url.newBuilder().apply { CREDENTIAL_PARAMS.forEach { removeAllQueryParameters(it) } }.build()
        if (c == null) return clean
        if (VoltcapService.isService(c)) {
            val base = VoltcapService.baseUrl ?: return clean
            return base.newBuilder().addPathSegments("v1/ss/media").addPathSegment(file).encodedQuery(clean.encodedQuery).build()
        }
        // (A developer key you entered goes straight to ScreenScraper: the service's signature isn't needed there.)
        val b = clean.newBuilder().removeAllQueryParameters("vsig")
            .addQueryParameter("devid", c.devId).addQueryParameter("devpassword", c.devPassword)
            .addQueryParameter("softname", ScreenScraperDevKey.softName)
        if (!c.username.isNullOrBlank() && !c.password.isNullOrBlank()) b.addQueryParameter("ssid", c.username).addQueryParameter("sspassword", c.password)
        return b.build()
    }

    fun resolve(url: String): String = url.toHttpUrlOrNull()?.let { resolve(it).toString() } ?: url

    /** For MediaPlayer: the address to open and the headers it needs (Voltcap's service wants the install id). */
    fun forPlayer(url: String): Pair<String, Map<String, String>> {
        val r = resolve(url)
        return r to (if (VoltcapService.isServiceUrl(r)) VoltcapService.headers() else emptyMap())
    }

    /** OkHttp: ScreenScraper media requests get their credentials (or go through Voltcap's service) on the way out. */
    val interceptor = Interceptor { chain ->
        val req = chain.request()
        val target = resolve(req.url)
        if (target == req.url) chain.proceed(req) else {
            val b = req.newBuilder().url(target)
            if (VoltcapService.isServiceUrl(target.toString())) VoltcapService.headers().forEach { (k, v) -> b.header(k, v) }
            chain.proceed(b.build())
        }
    }

    // ---- what older versions stored ----

    @Volatile private var migrated = false

    /**
     * Once per install (flag in the INTERNAL "migrations" prefs): every settings file that can travel (GLOBAL / DEVICE,
     * per-profile copies too) has its ScreenScraper addresses cleaned, and each saved picture is renamed to its clean
     * address. Safe to call from any start path (activity, backup, sync worker); blocking, quick (string work only).
     * Returns the number of values changed.
     */
    @Synchronized
    fun migrateOnce(context: Context): Int {
        if (migrated) return 0
        val c = context.applicationContext
        val flags = c.getSharedPreferences("migrations", Context.MODE_PRIVATE)
        if (flags.getInt("ss_url_hygiene", 0) >= MIGRATION_VERSION) { migrated = true; return 0 }
        val dir = File(c.applicationInfo.dataDir, "shared_prefs")
        val renames = LinkedHashMap<String, String>()
        var changed = 0
        dir.listFiles().orEmpty().filter { it.name.endsWith(".xml") }.map { it.name.removeSuffix(".xml") }
            .filter { SettingsScopes.scopeOf(it).let { s -> s == SettingsScope.GLOBAL || s == SettingsScope.DEVICE } }
            .forEach { name ->
                val prefs = c.getSharedPreferences(name, Context.MODE_PRIVATE)
                val edit = prefs.edit(); var any = false
                for ((k, v) in prefs.all) {
                    when (v) {
                        is String -> cleanText(v).let { n -> if (n != v) { collect(v, renames); edit.putString(k, n); any = true; changed++ } }
                        is Set<*> -> {
                            val old = v.filterIsInstance<String>().toSet()
                            val new = old.map { cleanText(it) }.toSet()
                            if (new != old) { old.forEach { collect(it, renames) }; edit.putStringSet(k, new); any = true; changed++ }
                        }
                    }
                }
                if (any) edit.commit()
            }
        runCatching { ArtFiles.renameSaved(renames) }
        flags.edit().putInt("ss_url_hygiene", MIGRATION_VERSION).putInt("ss_url_hygiene_values", changed).commit()
        migrated = true
        return changed
    }

    /** Each ScreenScraper address in [text] → its clean form, for renaming the saved pictures. */
    private fun collect(text: String, into: MutableMap<String, String>) {
        ANY_URL.findAll(text).forEach { m ->
            val plain = decode(m.value)
            val c = clean(plain)
            if (c != plain) into[plain] = c
        }
    }
}
