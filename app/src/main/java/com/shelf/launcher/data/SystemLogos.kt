package com.shelf.launcher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Console logos from ES-DE's system logo collection (https://gitlab.com/es-de/themes/system-logos, SVG files), for the
 * blank CD a game without a disc of its own is drawn on ("Discs only" / "All CDs"): the CD then carries its own
 * console's logo instead of the PlayStation 2 print.
 *
 * Nothing is bundled: the collection's file list is read once through GitLab's API (kept 30 days), and each logo is
 * downloaded the first time a console needs it, into the app's files (system_logos/<console>.svg), so it works
 * offline after that. The list is searched by ES-DE's system names ([ESDE]), so a reshuffle of the collection's folders
 * doesn't break it. Decoded with Coil's SVG decoder (io.coil-kt:coil-svg).
 */
object SystemLogos {
    private const val API = "https://gitlab.com/api/v4/projects/es-de%2Fthemes%2Fsystem-logos"
    private const val PREFS = "system_logos"
    private const val MAX_AGE = 30L * 86_400_000L
    /** After a failed lookup, wait this long before trying the network again for that console. */
    private const val RETRY_MS = 6L * 3_600_000L

    /** Voltcap console key → ES-DE system names to look for, best first. */
    private val ESDE: Map<String, List<String>> = mapOf(
        "NES" to listOf("nes", "famicom"), "SNES" to listOf("snes", "sfc"), "N64" to listOf("n64"),
        "GB" to listOf("gb"), "GBC" to listOf("gbc"), "GBA" to listOf("gba"), "NDS" to listOf("nds"), "3DS" to listOf("n3ds", "3ds"),
        "VirtualBoy" to listOf("virtualboy"), "GameCube" to listOf("gc", "gamecube"), "Wii" to listOf("wii"), "WiiU" to listOf("wiiu"),
        "Switch" to listOf("switch"), "PS1" to listOf("psx", "ps1"), "PS2" to listOf("ps2"), "PS3" to listOf("ps3"), "PS4" to listOf("ps4"),
        "PSP" to listOf("psp"), "PSVita" to listOf("psvita"), "Genesis" to listOf("genesis", "megadrive"),
        "MasterSystem" to listOf("mastersystem"), "GameGear" to listOf("gamegear"), "SegaCD" to listOf("segacd", "megacd"),
        "32X" to listOf("sega32x"), "Saturn" to listOf("saturn"), "Dreamcast" to listOf("dreamcast"),
        "PCEngine" to listOf("pcengine", "tg16"), "PCEngineCD" to listOf("pcenginecd", "tg-cd"), "NeoGeo" to listOf("neogeo"),
        "NeoGeoCD" to listOf("neogeocd"), "NeoGeoPocket" to listOf("ngpc", "ngp"), "WonderSwan" to listOf("wonderswancolor", "wonderswan"),
        "Atari2600" to listOf("atari2600"), "Atari7800" to listOf("atari7800"), "Lynx" to listOf("atarilynx"),
        "Jaguar" to listOf("atarijaguar"), "3DO" to listOf("3do"), "ColecoVision" to listOf("colecovision"),
        "Intellivision" to listOf("intellivision"), "Vectrex" to listOf("vectrex"), "PokemonMini" to listOf("pokemini"),
        "C64" to listOf("c64"), "Xbox" to listOf("xbox"), "Xbox360" to listOf("xbox360"), "Pico8" to listOf("pico8"),
        // Update 91: the platform key is "PICO8" (the line above never matched), and the other platforms Voltcap knows.
        "PICO8" to listOf("pico8"), "Arcade" to listOf("arcade", "mame"), "Naomi" to listOf("naomi"), "Atomiswave" to listOf("atomiswave"),
        "DOS" to listOf("dos"), "ScummVM" to listOf("scummvm"), "MSX" to listOf("msx", "msx1"), "Amiga" to listOf("amiga"),
        "Android" to listOf("android", "androidapps"), "Steam" to listOf("steam")
    )

    /** Logos shipped with the app (assets/system_logos, ES-DE's colour set, update 90): no download needed. */
    private val BUNDLED = setOf("3do","amiga","android","androidapps","arcade","atari2600","atari7800","atarijaguar","atarilynx","atomiswave","c64","colecovision","dos","dreamcast","famicom","gamegear","gb","gba","gbc","gc","genesis","intellivision","mame","mastersystem","megacd","megadrive","msx","msx1","n3ds","n64","naomi","nds","neogeo","neogeocd","nes","ngp","ngpc","pcengine","pcenginecd","pico8","pokemini","ps2","ps3","ps4","psp","psvita","psx","saturn","scummvm","sega32x","segacd","sfc","snes","steam","switch","tg-cd","tg16","vectrex","virtualboy","wii","wiiu","wonderswan","wonderswancolor","xbox","xbox360")
    /** ES-DE-style system discs shipped with the app (assets/system_discs, 512 px overlays: rim, hub, logos, band). */
    private val DISCS = setOf("3do","atari2600","atari7800","atarijaguar","atarilynx","c64","colecovision","dreamcast","famicom","gamegear","gb","gba","gbc","gc","genesis","intellivision","mastersystem","megacd","megadrive","n3ds","n64","nds","neogeo","neogeocd","nes","ngp","ngpc","pcengine","pcenginecd","pico8","pokemini","ps2","ps3","ps4","psp","psvita","psx","saturn","sega32x","segacd","sfc","snes","switch","tg-cd","tg16","vectrex","virtualboy","wii","wiiu","wonderswan","wonderswancolor","xbox","xbox360")

    /** The bundled colour logo for [platform] (an asset address Coil can load with its SVG decoder), or null. */
    fun bundled(platform: String): String? =
        ESDE[platform]?.firstOrNull { it in BUNDLED }?.let { "file:///android_asset/system_logos/$it.svg" }

    /** The bundled system disc overlay for [platform] (asset path under assets/), or null. */
    fun discAsset(platform: String): String? =
        ESDE[platform]?.firstOrNull { it in DISCS }?.let { "system_discs/$it.png" }

    /** What to load for [platform]'s logo: only the logos shipped with the app (update 91: nothing is downloaded any more). */
    suspend fun model(context: Context, platform: String): Any? = bundled(platform)

    private val lock = Mutex()
    @Volatile private var paths: List<String>? = null
    @Volatile private var branch: String? = null

    /** The console's logo file (an SVG), downloading it when needed; null when there's none or no connection. Call off the main thread is not needed. */
    suspend fun get(context: Context, platform: String): File? = withContext(Dispatchers.IO) {
        val names = ESDE[platform] ?: return@withContext null
        val dir = File(context.filesDir, "system_logos").apply { mkdirs() }
        val file = File(dir, "$platform.svg")
        if (file.length() > 0) return@withContext file
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - prefs.getLong("fail_$platform", 0L) < RETRY_MS) return@withContext null
        lock.withLock {
            if (file.length() > 0) return@withLock file
            val list = fileList(context) ?: run { prefs.edit().putLong("fail_$platform", System.currentTimeMillis()).apply(); return@withLock null }
            val path = pick(list, names) ?: run { prefs.edit().putLong("fail_$platform", System.currentTimeMillis()).apply(); return@withLock null }
            val ref = branch ?: "master"
            val enc = URLEncoder.encode(path, "UTF-8").replace("+", "%20")
            val bytes = download("$API/repository/files/$enc/raw?ref=${URLEncoder.encode(ref, "UTF-8")}")
            if (bytes == null || bytes.isEmpty() || bytes.size > 2_000_000) {
                prefs.edit().putLong("fail_$platform", System.currentTimeMillis()).apply()
                return@withLock null
            }
            val tmp = File(dir, "$platform.svg.part")
            tmp.writeBytes(bytes)
            tmp.renameTo(file)
            file.takeIf { it.length() > 0 }
        }
    }

    /**
     * The best file for [names]: a file named after the system (ps2.svg), else one inside a folder named after it; SVG
     * before anything else, the plain / colour version before white, black, dark, light, mono or outline ones.
     */
    private fun pick(list: List<String>, names: List<String>): String? {
        for (n in names) {
            val hits = list.filter { p ->
                val parts = p.lowercase().split('/')
                val base = parts.last().substringBeforeLast('.')
                base == n || parts.dropLast(1).lastOrNull() == n
            }
            if (hits.isEmpty()) continue
            return hits.minByOrNull { p ->
                val l = p.lowercase()
                val base = l.substringAfterLast('/').substringBeforeLast('.')
                var score = 0
                if (!l.endsWith(".svg")) score += 100
                if (base != n) score += 20
                listOf("white", "black", "dark", "light", "mono", "outline", "alt", "small", "text").forEach { if (it in l) score += 10 }
                if ("color" in l || "colour" in l) score -= 5
                score * 1000 + l.length
            }
        }
        return null
    }

    /** Every logo file's path in the collection (cached for [MAX_AGE]). */
    private fun fileList(context: Context): List<String>? {
        paths?.let { return it }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString("paths", null)
        if (saved != null && System.currentTimeMillis() - prefs.getLong("at", 0L) < MAX_AGE) {
            branch = prefs.getString("branch", null)
            return saved.split('\n').filter { it.isNotBlank() }.also { paths = it }
        }
        val info = download(API)?.toString(Charsets.UTF_8)
        val ref = info?.let { runCatching { com.google.gson.JsonParser.parseString(it).asJsonObject.get("default_branch")?.asString }.getOrNull() } ?: "master"
        val out = ArrayList<String>()
        var page = 1
        while (page in 1..40) {
            val text = download("$API/repository/tree?recursive=true&per_page=100&page=$page&ref=${URLEncoder.encode(ref, "UTF-8")}")?.toString(Charsets.UTF_8)
                ?: break
            val arr = runCatching { com.google.gson.JsonParser.parseString(text).asJsonArray }.getOrNull() ?: break
            if (arr.size() == 0) break
            arr.forEach { e ->
                val o = e.asJsonObject
                val path = o.get("path")?.asString ?: return@forEach
                val l = path.lowercase()
                if (o.get("type")?.asString == "blob" && (l.endsWith(".svg") || l.endsWith(".png"))) out += path
            }
            if (arr.size() < 100) break
            page++
        }
        if (out.isEmpty()) return saved?.split('\n')?.filter { it.isNotBlank() }?.also { paths = it }
        branch = ref
        paths = out
        prefs.edit().putString("paths", out.joinToString("\n")).putString("branch", ref).putLong("at", System.currentTimeMillis()).apply()
        return out
    }

    private fun download(url: String): ByteArray? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12_000; conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", "Voltcap")
        try {
            if (conn.responseCode !in 200..299) null else conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
