package com.shelf.launcher.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.JsonObject
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.QueryMap
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * ScreenScraper (screenscraper.fr) — the database emulation frontends use.
 * Finds ROMs by filename, size and checksum rather than a title search, and
 * has box art, 3D boxes, screenshots, fan art and logos for retro systems.
 *
 * Every request needs a developer ID/password (requested once from
 * ScreenScraper's forum, per app) and, optionally, the person's own free
 * ScreenScraper account, which raises their daily quota.
 *
 * Only for ROMs: ScreenScraper doesn't cover Android apps.
 */
data class ScreenScraperCredentials(
    val devId: String,
    val devPassword: String,
    val username: String?,
    val password: String?
)

class ScreenScraperStore(context: Context) {
    private val app = context.applicationContext
    private val prefs: SharedPreferences = SecureStore.open(app, ENCRYPTED, "ScreenScraper", listOf(FALLBACK)).prefs

    fun load(): ScreenScraperCredentials? {
        val devId = prefs.getString(KEY_DEV_ID, null) ?: return null
        val devPassword = prefs.getString(KEY_DEV_PASSWORD, null) ?: return null
        return ScreenScraperCredentials(
            devId, devPassword,
            prefs.getString(KEY_USER, null),
            prefs.getString(KEY_PASSWORD, null)
        )
    }

    fun save(c: ScreenScraperCredentials) {
        prefs.edit()
            .putString(KEY_DEV_ID, c.devId)
            .putString(KEY_DEV_PASSWORD, c.devPassword)
            .putString(KEY_USER, c.username)
            .putString(KEY_PASSWORD, c.password)
            .apply()
        SecureStore.clearRelogin(app, "ScreenScraper")
    }

    fun clear() = prefs.edit().clear().apply()


    companion object {
        private const val ENCRYPTED = "screenscraper_credentials_encrypted"
        private const val FALLBACK = "screenscraper_credentials"
        private const val KEY_DEV_ID = "dev_id"
        private const val KEY_DEV_PASSWORD = "dev_password"
        private const val KEY_USER = "username"
        private const val KEY_PASSWORD = "password"
    }
}

interface ScreenScraperApi {
    // Raw bodies: on errors ScreenScraper answers with plain text (sometimes
    // French) instead of JSON, so parsing is done by hand.
    @GET("jeuInfos.php")
    suspend fun gameInfo(@QueryMap params: Map<String, String>): Response<ResponseBody>

    @GET("ssuserInfos.php")
    suspend fun userInfo(@QueryMap params: Map<String, String>): Response<ResponseBody>

    @GET("ssinfraInfos.php")
    suspend fun infraInfo(@QueryMap params: Map<String, String>): Response<ResponseBody>

    /** Every system with its maker, start date and artwork (logo, photo, fan art, video). */
    @GET("systemesListe.php")
    suspend fun systemList(@QueryMap params: Map<String, String>): Response<ResponseBody>
}

fun createScreenScraperApi(): ScreenScraperApi {
    // ScreenScraper is often slow under load; the default 10s gives up too early.
    val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        // Update 182: never longer than this per request (a server trickling bytes kept a lookup open for good).
        .callTimeout(90, TimeUnit.SECONDS)
        .build()
    return Retrofit.Builder()
        .baseUrl("https://api.screenscraper.fr/api2/")
        .client(client)
        .build()
        .create(ScreenScraperApi::class.java)
}

class ScreenScraperRepository(private val api: ScreenScraperApi) {

    // Free accounts get one request "thread" — requests go strictly one at a
    // time, with a short gap, so the scraper never trips the limit.
    // Update 175: accounts allowed more threads (ScreenScraper says how many in every answer, "maxthreads") get that
    // many requests at once (up to 4), with the gap shared between them — scraping a big library goes that much faster.
    private val gate = Mutex()
    @Volatile private var slots = kotlinx.coroutines.sync.Semaphore(1)
    @Volatile private var threads = 1
    private val gson = Gson()
    private var lastRequestAt = 0L

    private suspend fun <T> throttled(block: suspend () -> T): T {
        val s = slots
        s.acquire()
        try {
            gate.withLock {
                val gap = (MIN_GAP_MS / threads).coerceAtLeast(400L)
                val wait = gap - (System.currentTimeMillis() - lastRequestAt)
                if (wait > 0) delay(wait)
                lastRequestAt = System.currentTimeMillis()
            }
            return block()
        } finally {
            // One thread: the gap counts from the end of the last answer (as before).
            if (threads == 1) synchronized(this) { lastRequestAt = System.currentTimeMillis() }
            s.release()
        }
    }

    /** Update 175: the number of requests at once the account may make, read from an answer's "ssuser". */
    private fun learnThreads(root: JsonObject?) {
        val n = root?.obj("response")?.obj("ssuser")?.str("maxthreads")?.toIntOrNull() ?: return
        val want = n.coerceIn(1, 4)
        if (want != threads) {
            threads = want
            slots = kotlinx.coroutines.sync.Semaphore(want)
        }
    }

    private fun baseParams(c: ScreenScraperCredentials): MutableMap<String, String> = mutableMapOf(
        "devid" to c.devId,
        "devpassword" to c.devPassword,
        "softname" to SOFT_NAME,
        "output" to "json"
    ).apply {
        if (!c.username.isNullOrBlank() && !c.password.isNullOrBlank()) {
            put("ssid", c.username)
            put("sspassword", c.password)
        }
    }

    /** Null when the credentials work, otherwise a message to show. */
    suspend fun testConnection(c: ScreenScraperCredentials): String? = try {
        val response = throttled {
            if (c.username.isNullOrBlank()) api.infraInfo(baseParams(c)) else api.userInfo(baseParams(c))
        }
        val body = response.body()?.string() ?: response.errorBody()?.string().orEmpty()
        when {
            response.isSuccessful && parseJson(body) != null -> null
            else -> explainError(response.code(), body)
        }
    } catch (e: Exception) {
        "Couldn't reach ScreenScraper: ${e.message ?: e::class.java.simpleName}"
    }

    // Recent lookups, so fetching art and details for the same game costs one
    // request, not two. Small on purpose — it only needs to bridge the two calls.
    private val recent = object : LinkedHashMap<String, JsonObject?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JsonObject?>?) = size > 48
    }

    /** ScreenScraper's record ("jeu") for one ROM, or null if unknown / not a ROM. */
    private suspend fun lookup(c: ScreenScraperCredentials, game: Game): JsonObject? {
        if (game.sourceType != SourceType.ROM) return null
        synchronized(recent) { if (recent.containsKey(game.stableKey)) return recent[game.stableKey] }
        // File checks (and an .m3u read) off the main thread: callers may be on it (update 91).
        val (file, length) = withContext(Dispatchers.IO) { romFileFor(game)?.let { it to it.length() } } ?: return null

        val params = baseParams(c).apply {
            put("romtype", "rom")
            put("romnom", file.name)
            put("romtaille", length.toString())
            SYSTEM_IDS[game.platform]?.let { put("systemeid", it.toString()) }
            // A checksum makes the match exact. Skipped for big disc images,
            // where reading gigabytes per game would make scraping crawl —
            // name + size already identify those well.
            if (length <= MAX_HASH_BYTES) {
                md5Of(file)?.let { put("md5", it) }
            }
        }

        val response = try {
            throttled { api.gameInfo(params) }
        } catch (e: Exception) {
            return null // network trouble: don't remember, so a retry can work
        }
        val jeu = if (response.isSuccessful) {
            parseJson(response.body()?.string().orEmpty())?.also { learnThreads(it) }?.obj("response")?.obj("jeu")
        } else {
            runCatching { response.errorBody()?.close() } // an unread error body keeps its connection busy
            null
        }
        // Only a clear "not found" is remembered; server trouble isn't.
        if (response.isSuccessful || response.code() == 404) {
            synchronized(recent) { recent[game.stableKey] = jeu }
        }
        return jeu
    }

    /** Art for one ROM, or null if ScreenScraper doesn't know it (or it's not a ROM). */
    suspend fun fetchArt(c: ScreenScraperCredentials, game: Game): GameArt? {
        val jeu = lookup(c, game) ?: return null
        val medias = jeu.arr("medias")?.mapNotNull { it as? JsonObject } ?: return null

        fun pick(vararg types: String, maxWidth: Int): String? {
            for (type in types) {
                val candidates = medias.filter { it.str("type") == type }
                if (candidates.isEmpty()) continue
                val best = candidates.minByOrNull { regionRank(it.str("region")) } ?: continue
                val url = best.str("url") ?: continue
                return if ("mediaJeu.php" in url && "maxwidth" !in url) "$url&maxwidth=$maxWidth" else url
            }
            return null
        }

        val art = GameArt(
            gridUrl = pick("box-2D", "box-3D", maxWidth = 600),
            wideGridUrl = pick("screenmarquee", "fanart", "ss", maxWidth = 920),
            heroUrl = pick("fanart", "ss", "sstitle", maxWidth = 1920),
            logoUrl = pick("wheel-hd", "wheel", maxWidth = 640)
        )
        return art.takeIf { it.gridUrl != null || it.heroUrl != null || it.wideGridUrl != null }
    }

    /**
     * Details for the details window. ScreenScraper has no playtime data, so
     * those fields stay empty here (IGDB can fill them in). Its rating is
     * out of 20 — scaled to 0-100 to match the score tile.
     */
    suspend fun fetchMetadata(c: ScreenScraperCredentials, game: Game): GameMetadata? {
        val jeu = lookup(c, game) ?: return null

        val title = regional(jeu.arr("noms"))
        val summary = inLanguage(jeu.arr("synopsis"))
        val year = regional(jeu.arr("dates"))?.take(4)?.toIntOrNull()
        val rating = jeu.obj("note")?.str("text")?.toDoubleOrNull()
        val developer = jeu.obj("developpeur")?.str("text")
        val publisher = jeu.obj("editeur")?.str("text")
        val genres = jeu.arr("genres")
            ?.mapNotNull { (it as? JsonObject)?.let { g -> inLanguage(g.arr("noms")) } }
            .orEmpty()
            .distinct()

        if (summary == null && year == null && rating == null && genres.isEmpty() && developer == null) return null
        return GameMetadata(
            title = title,
            summary = summary,
            releaseYear = year,
            criticScore = rating?.let { Math.round(it * 5).toInt() }?.coerceIn(0, 100),
            criticCount = null,
            genres = genres,
            developer = developer,
            hastilyHours = null,
            normallyHours = null,
            completelyHours = null,
            publisher = publisher
        )
    }

    /** In-game screenshots (and title/fan art) for the details window's media row. */
    suspend fun screenshots(c: ScreenScraperCredentials, game: Game): List<String> {
        val jeu = lookup(c, game)?.takeIf { isConfidentMatch(it, game) } ?: return emptyList()
        val medias = jeu.arr("medias")?.mapNotNull { it as? JsonObject } ?: return emptyList()
        return listOf("ss", "sstitle", "fanart").flatMap { type ->
            medias.filter { it.str("type") == type }
                .sortedBy { regionRank(it.str("region")) }
                .take(1)
                .mapNotNull { it.str("url") }
                .map { url -> if ("mediaJeu.php" in url && "maxwidth" !in url) "$url&maxwidth=640" else url }
        }
    }

    /** The physical game: box front / back, 3D box and the disc or cartridge,
     * as (label, address) pairs — for the details window. */
    suspend fun physicalMedia(c: ScreenScraperCredentials, game: Game): List<Pair<String, String>> {
        val jeu = lookup(c, game)?.takeIf { isConfidentMatch(it, game) } ?: return emptyList()
        val medias = jeu.arr("medias")?.mapNotNull { it as? JsonObject } ?: return emptyList()
        return listOf(
            "box-2D" to "Box",
            "box-2D-back" to "Back of the box",
            "box-3D" to "Box (3D)",
            "support-2D" to "Disc / cartridge",
            // The case's spine (update 92): the Game shelf stands each game on its own scraped spine.
            "box-2D-side" to "Spine",
            // Just the printed label: what the drawn cartridge / disc wears (PhysicalMedia).
            "support-texture" to "Disc label",
            // The whole case scanned flat, back | spine | front (update 101): the Game shelf's spines and the cases'
            // fronts are cut from it (BoxTexture.kt) when there's no separate spine / front scan.
            "box-texture" to "Box (full scan)"
        ).mapNotNull { (type, label) ->
            val best = medias.filter { it.str("type") == type }.minByOrNull { regionRank(it.str("region")) }
            val url = best?.str("url") ?: return@mapNotNull null
            // The full scan is fetched bigger: the spine is only a few percent of its width.
            val max = if (type == "box-texture") 1600 else 640
            label to (if ("mediaJeu.php" in url && "maxwidth" !in url) "$url&maxwidth=$max" else url)
        }
    }

    private var systemListCache: com.google.gson.JsonArray? = null

    /**
     * A platform's own artwork from ScreenScraper's system list (Platform Options › Scrape platform media):
     * the logo, a photo of the console, fan art, an intro clip, plus its maker and start year.
     * Null when the platform is unknown there or the list couldn't be fetched.
     */
    suspend fun platformMedia(c: ScreenScraperCredentials, platform: String): PlatformInfo.PlatformMedia? {
        val id = SYSTEM_IDS[platform] ?: return null
        val list = systemListCache ?: throttled {
            val r = api.systemList(baseParams(c))
            if (!r.isSuccessful) return@throttled null
            parseJson(r.body()?.string().orEmpty())?.obj("response")?.arr("systemes")
        }?.also { systemListCache = it } ?: return null
        val system = list.mapNotNull { it as? JsonObject }.firstOrNull { it.str("id")?.toIntOrNull() == id } ?: return null
        val medias = system.arr("medias")?.mapNotNull { it as? JsonObject }.orEmpty()
        fun best(vararg types: String): String? = types.firstNotNullOfOrNull { type ->
            medias.filter { it.str("type") == type }.minByOrNull { regionRank(it.str("region")) }?.str("url")
        }
        return PlatformInfo.PlatformMedia(
            logo = best("wheel", "logo-monochrome", "logo-svg"),
            photo = best("photo", "illustration", "BoitierConsole3D"),
            fanart = best("fanart", "screenmarquee"),
            video = best("video-normalized", "video"),
            maker = system.str("compagnie")?.uppercase(),
            year = system.str("datedebut")?.take(4)?.toIntOrNull()
        )
    }

    /** The scanned game manual (PDF), best region first, when ScreenScraper has one. */
    suspend fun manualUrl(c: ScreenScraperCredentials, game: Game): String? {
        val jeu = lookup(c, game)?.takeIf { isConfidentMatch(it, game) } ?: return null
        val medias = jeu.arr("medias")?.mapNotNull { it as? JsonObject } ?: return null
        return medias.filter { it.str("type") == "manuel" }.minByOrNull { regionRank(it.str("region")) }?.str("url")
    }

    /** A short gameplay clip for the video preview — the "normalized" version
     * (smaller, consistent format) when ScreenScraper has one. */
    suspend fun videoUrl(c: ScreenScraperCredentials, game: Game): String? {
        val jeu = lookup(c, game)?.takeIf { isConfidentMatch(it, game) } ?: return null
        val medias = jeu.arr("medias")?.mapNotNull { it as? JsonObject } ?: return null
        return (medias.firstOrNull { it.str("type") == "video-normalized" }
            ?: medias.firstOrNull { it.str("type") == "video" })?.str("url")
    }

    /** Text from a [{region, text}] list, best region first. */
    /**
     * Whether ScreenScraper's match can be trusted for pictures and clips.
     * Small ROMs are matched by checksum, which is exact. Big disc images are
     * matched by file name and size only, which often lands on the wrong game
     * — there the game's name has to agree with the file's too.
     */
    private suspend fun isConfidentMatch(jeu: JsonObject, game: Game): Boolean {
        // File checks off the main thread (update 91).
        val length = withContext(Dispatchers.IO) { romFileFor(game)?.length() } ?: return false
        if (length <= MAX_HASH_BYTES) return true
        val theirs = jeu.arr("noms")?.mapNotNull { (it as? JsonObject)?.str("text") }.orEmpty()
        val ours = words(game.name)
        if (ours.isEmpty()) return true
        return theirs.any { name ->
            val w = words(name)
            w.isNotEmpty() && ours.count { it in w } >= (ours.size + 1) / 2
        }
    }

    private fun words(name: String): Set<String> =
        name.replace(Regex("[(\\[][^)\\]]*[)\\]]"), " ")
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 1 && it !in setOf("the", "of", "and", "disc") }
            .toSet()

    private fun regional(list: com.google.gson.JsonArray?): String? =
        list?.mapNotNull { it as? JsonObject }
            ?.filter { !it.str("text").isNullOrBlank() }
            ?.minByOrNull { regionRank(it.str("region")) }
            ?.str("text")

    /** Text from a [{langue, text}] list, English first. */
    private fun inLanguage(list: com.google.gson.JsonArray?): String? {
        val entries = list?.mapNotNull { it as? JsonObject }?.filter { !it.str("text").isNullOrBlank() } ?: return null
        return (entries.firstOrNull { it.str("langue") == "en" } ?: entries.firstOrNull())?.str("text")
    }

    /** Multi-disc .m3u entries are scraped by their first disc. */
    private fun romFileFor(game: Game): File? {
        val path = game.romPath ?: return null
        val file = File(path)
        if (!file.extension.equals("m3u", true)) return file.takeIf { it.isFile }
        return runCatching {
            file.readLines().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                ?.let { line -> File(line).let { if (it.isAbsolute) it else File(file.parentFile, line) } }
        }.getOrNull()?.takeIf { it.isFile }
    }

    private suspend fun md5Of(file: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val md = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    private fun parseJson(body: String): JsonObject? =
        runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull()

    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.str(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.arr(key: String): com.google.gson.JsonArray? =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    /** World, then English-speaking regions, then Europe/Japan, then anything. */
    private fun regionRank(region: String?): Int = when (region?.lowercase()) {
        "wor" -> 0
        "us" -> 1
        "uk" -> 2
        "eu" -> 3
        "ss" -> 4
        "jp" -> 5
        null, "" -> 6
        else -> 7
    }

    private fun explainError(code: Int, body: String): String {
        val detail = body.trim().take(140)
        return when (code) {
            400 -> "ScreenScraper rejected the request: $detail"
            401 -> "ScreenScraper is busy and closed to non-members right now — try again later."
            403 -> "Developer ID or password not accepted by ScreenScraper."
            404 -> "ScreenScraper didn't recognise that account."
            423 -> "ScreenScraper's API is temporarily closed — try again later."
            426 -> "ScreenScraper has blocked this app's developer ID."
            429, 430, 431 -> "ScreenScraper quota reached for today — it resets daily."
            else -> "ScreenScraper answered $code: $detail"
        }
    }

    companion object {
        private const val SOFT_NAME = "ShelfLauncher"
        private const val MIN_GAP_MS = 1200L
        private const val MAX_HASH_BYTES = 64L * 1024 * 1024

        /** Shelf platform names → ScreenScraper system IDs. The ID narrows
         * the search to the right console; unknown platforms still work by
         * filename, just less precisely. */
        val SYSTEM_IDS = mapOf(
            "Genesis" to 1, "MasterSystem" to 2, "NES" to 3, "SNES" to 4,
            "GB" to 9, "GBC" to 10, "VirtualBoy" to 11, "GBA" to 12,
            "GameCube" to 13, "N64" to 14, "NDS" to 15, "Wii" to 16, "3DS" to 17,
            "SegaCD" to 20, "Saturn" to 22, "Dreamcast" to 23,
            "NeoGeoPocket" to 25, "Atari2600" to 26, "PCEngine" to 31,
            "WonderSwan" to 45, "PS1" to 57, "PS2" to 58, "PSP" to 61,
            "Arcade" to 75, "NeoGeo" to 142
        )
    }
}

/**
 * Remembered video addresses per game (Game.stableKey). An empty string means
 * "asked, ScreenScraper has none" — so a game is only ever looked up once.
 */
class VideoUrlStore(context: Context) {
    private val prefs = context.getSharedPreferences("video_urls", Context.MODE_PRIVATE)

    fun get(stableKey: String): String? = prefs.getString(stableKey, null)
    fun put(stableKey: String, url: String?) = prefs.edit().putString(stableKey, url ?: "").apply()
    fun clear() = prefs.edit().clear().apply()
}
