package com.shelf.launcher.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject

/**
 * Facts about a platform for the platform browser's slides ("SONY 1994"): the maker and the year it came out.
 * A built-in list for the common consoles; anything scraped from ScreenScraper (Platform Options › Scrape platform
 * media) wins over it.
 */
object PlatformInfo {
    private val builtIn: Map<String, Pair<String, Int>> = mapOf(
        "PS1" to ("SONY" to 1994), "PS2" to ("SONY" to 2000), "PS3" to ("SONY" to 2006), "PS4" to ("SONY" to 2013),
        "PSP" to ("SONY" to 2004), "PSVita" to ("SONY" to 2011),
        "GameCube" to ("NINTENDO" to 2001), "Wii" to ("NINTENDO" to 2006), "WiiU" to ("NINTENDO" to 2012),
        "Switch" to ("NINTENDO" to 2017), "NES" to ("NINTENDO" to 1983), "SNES" to ("NINTENDO" to 1990),
        "N64" to ("NINTENDO" to 1996), "GB" to ("NINTENDO" to 1989), "GBC" to ("NINTENDO" to 1998),
        "GBA" to ("NINTENDO" to 2001), "NDS" to ("NINTENDO" to 2004), "3DS" to ("NINTENDO" to 2011),
        "VirtualBoy" to ("NINTENDO" to 1995),
        "Genesis" to ("SEGA" to 1988), "MasterSystem" to ("SEGA" to 1985), "SegaCD" to ("SEGA" to 1991),
        "Saturn" to ("SEGA" to 1994), "Dreamcast" to ("SEGA" to 1998), "GameGear" to ("SEGA" to 1990),
        "32X" to ("SEGA" to 1994), "Naomi" to ("SEGA" to 1998),
        "PCEngine" to ("NEC" to 1987), "PCEngineCD" to ("NEC" to 1988),
        "NeoGeo" to ("SNK" to 1990), "NeoGeoCD" to ("SNK" to 1994), "NeoGeoPocket" to ("SNK" to 1998),
        "WonderSwan" to ("BANDAI" to 1999),
        "Atari2600" to ("ATARI" to 1977), "Atari7800" to ("ATARI" to 1986), "Lynx" to ("ATARI" to 1989),
        "Jaguar" to ("ATARI" to 1993),
        "3DO" to ("PANASONIC" to 1993), "Xbox" to ("MICROSOFT" to 2001), "Xbox360" to ("MICROSOFT" to 2005),
        // (Pass 4: first release, any region — for Generations.)
        "XboxOne" to ("MICROSOFT" to 2013), "XboxSeries" to ("MICROSOFT" to 2020), "PS5" to ("SONY" to 2020),
        "Atari5200" to ("ATARI" to 1982),
        "Amiga" to ("COMMODORE" to 1985), "C64" to ("COMMODORE" to 1982),
        "ColecoVision" to ("COLECO" to 1982), "Intellivision" to ("MATTEL" to 1979), "Vectrex" to ("GCE" to 1982)
    )

    fun maker(platform: String): String? = media(platform)?.maker ?: builtIn[platform]?.first
    fun year(platform: String): Int? = media(platform)?.year ?: builtIn[platform]?.second

    /** "SONY 1994", or whatever part is known. */
    fun line(platform: String): String? =
        listOfNotNull(maker(platform), year(platform)?.toString()).joinToString(" ").takeIf { it.isNotBlank() }

    // ---- Scraped / chosen media, per platform ----
    class PlatformMedia(
        val logo: String? = null,
        val photo: String? = null,
        val fanart: String? = null,
        val video: String? = null,
        val maker: String? = null,
        val year: Int? = null
    )

    /** Bumped whenever the store changes, so screens showing the media recompose. */
    val version = mutableStateOf(0)

    private var store: PlatformMediaStore? = null
    fun init(context: Context) { if (store == null) store = PlatformMediaStore(context.applicationContext) }
    fun media(platform: String): PlatformMedia? = store?.get(platform)
    fun save(platform: String, m: PlatformMedia) { store?.put(platform, m); version.value++ }
    fun clear(platform: String) { store?.remove(platform); version.value++ }

    /** Your own intro video for a platform (a file picked in Platform Options), if any. */
    fun introVideo(platform: String): String? = store?.introVideo(platform)
    fun setIntroVideo(platform: String, uri: String?) { store?.setIntroVideo(platform, uri); version.value++ }
}

private class PlatformMediaStore(context: Context) {
    private val prefs = context.getSharedPreferences("platform_media", Context.MODE_PRIVATE)
    /** Content addresses of picked files only mean something on this device (SettingsScopes: DEVICE). */
    private val local = context.getSharedPreferences("platform_intro", Context.MODE_PRIVATE)

    fun get(platform: String): PlatformInfo.PlatformMedia? {
        val raw = prefs.getString("m:$platform", null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            fun s(k: String) = o.optString(k, "").takeIf { it.isNotBlank() }
            PlatformInfo.PlatformMedia(s("logo"), s("photo"), s("fanart"), s("video"), s("maker"), o.optInt("year", 0).takeIf { it > 0 })
        }.getOrNull()
    }

    fun put(platform: String, m: PlatformInfo.PlatformMedia) {
        val o = JSONObject()
        m.logo?.let { o.put("logo", it) }
        m.photo?.let { o.put("photo", it) }
        m.fanart?.let { o.put("fanart", it) }
        m.video?.let { o.put("video", it) }
        m.maker?.let { o.put("maker", it) }
        m.year?.let { o.put("year", it) }
        prefs.edit().putString("m:$platform", o.toString()).apply()
    }

    fun remove(platform: String) { prefs.edit().remove("m:$platform").apply() }
    fun introVideo(platform: String): String? = local.getString("intro:$platform", null)?.takeIf { it.isNotBlank() }
    fun setIntroVideo(platform: String, uri: String?) {
        local.edit().apply { if (uri == null) remove("intro:$platform") else putString("intro:$platform", uri) }.apply()
    }
}

/**
 * Your own order of the platforms (the platform browser's drag-and-drop mode). Platforms you haven't placed
 * keep the alphabetical order after the ones you have. Prefs "platform_order".
 */
object PlatformOrder {
    /** Bumped when the order changes, so the tiles are worked out again. */
    val version = mutableStateOf(0)
    private var order: List<String> = emptyList()
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("platform_order", Context.MODE_PRIVATE)
        prefs = p
        order = p.getString("order", "").orEmpty().split('|').filter { it.isNotBlank() }
        version.value++
    }

    /** [platforms] (alphabetical) in your order. */
    fun sort(platforms: List<String>): List<String> =
        if (order.isEmpty()) platforms
        else platforms.sortedBy { p -> order.indexOf(p).let { if (it < 0) Int.MAX_VALUE else it } }

    /** Moves the platform at [from] to [to] within [current] (the order on screen) and remembers the result. */
    fun move(current: List<String>, from: Int, to: Int) {
        if (from !in current.indices || to !in current.indices || from == to) return
        val list = current.toMutableList()
        list.add(to, list.removeAt(from))
        order = list
        prefs?.edit()?.putString("order", list.joinToString("|"))?.apply()
        version.value++
    }

    fun reset() {
        order = emptyList()
        prefs?.edit()?.remove("order")?.apply()
        version.value++
    }

    /** Update 214: the order as saved now, and putting it back (a category move cancelled with B). */
    fun snapshot(): List<String> = order
    fun restore(saved: List<String>) {
        order = saved
        prefs?.edit()?.apply { if (saved.isEmpty()) remove("order") else putString("order", saved.joinToString("|")) }?.apply()
        version.value++
    }
}
