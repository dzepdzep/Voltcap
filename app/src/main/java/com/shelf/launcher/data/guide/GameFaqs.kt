package com.shelf.launcher.data.guide

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * Finds a game's own page on GameFAQs (its list of guides, "…/faqs") instead of the site's search page.
 *
 * GameFAQs has no public API and its search needs the page's scripts, so the game's page is looked up
 * through a plain web search restricted to gamefaqs.gamespot.com: the first result on the right
 * console's section (…/snes/588301-…) is the game. The answer is remembered per game. When nothing
 * is found, the GameFAQs search with the console in the query is used, as before.
 */
object GameFaqs {
    private const val PREFS = "gamefaqs_pages"

    /** Voltcap platform key → GameFAQs' section in its URLs. */
    private val SLUG = mapOf(
        "NES" to "nes", "SNES" to "snes", "N64" to "n64", "GameCube" to "gamecube", "GC" to "gamecube",
        "Wii" to "wii", "WiiU" to "wii-u", "Switch" to "switch", "GB" to "gameboy", "GBC" to "gbc",
        "GBA" to "gba", "NDS" to "ds", "3DS" to "3ds", "PS1" to "ps", "PSX" to "ps", "PS2" to "ps2",
        "PS3" to "ps3", "PSP" to "psp", "PSVita" to "vita", "Genesis" to "genesis", "MegaDrive" to "genesis",
        "SegaCD" to "segacd", "Saturn" to "saturn", "Dreamcast" to "dreamcast", "GameGear" to "gamegear",
        "MasterSystem" to "sms", "Xbox" to "xbox", "PC" to "pc", "Arcade" to "arcade", "NeoGeo" to "neo",
        "TG16" to "tg16", "PCEngine" to "tg16", "Atari2600" to "2600", "N3DS" to "3ds"
    )

    /** GameFAQs' section for [platform], when known. */
    fun slug(platform: String?): String? = platform?.let { p -> SLUG[p] ?: SLUG.entries.firstOrNull { it.key.equals(p, true) }?.value }

    /** The search page, with the console's name in the query so the right version comes first. */
    fun searchUrl(title: String, platform: String?): String =
        // The console's name is in the query now, as documented (update 91).
        "https://gamefaqs.gamespot.com/search?game=" +
            Uri.encode(listOfNotNull(title, platform?.takeIf { it.isNotBlank() }?.let { com.shelf.launcher.data.ConsoleNames.display(it) }).joinToString(" "))

    /** The game's own guide list ("https://gamefaqs.gamespot.com/snes/588301-…/faqs"), or null. Network: call off the main thread. */
    suspend fun gamePage(context: Context, gameKey: String, title: String, platform: String?): String? = withContext(Dispatchers.IO) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(gameKey, null)?.let { return@withContext it.ifEmpty { null } }
        val slug = slug(platform)
        val q = "site:gamefaqs.gamespot.com " + (if (slug != null) "$slug " else "") + "\"$title\" faqs"
        val result = runCatching { search(q, slug) }
        // Remember a miss too (empty), so it isn't looked up again every time — but not a failed lookup
        // (offline, the search refusing): that one is tried again next time.
        if (result.isSuccess) prefs.edit().putString(gameKey, result.getOrNull().orEmpty()).apply()
        result.getOrNull()
    }

    private fun search(query: String, slug: String?): String? {
        val conn = URL("https://html.duckduckgo.com/html/?q=" + Uri.encode(query)).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000; conn.readTimeout = 12_000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Voltcap")
        val html = try {
            if (conn.responseCode !in 200..299) error("Search answered ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        // Result links are either direct or wrapped as /l/?uddg=<encoded url>.
        val links = Regex("href=\"([^\"]+)\"").findAll(html).map { m ->
            val h = m.groupValues[1].replace("&amp;", "&")
            Regex("uddg=([^&]+)").find(h)?.groupValues?.get(1)?.let { URLDecoder.decode(it, "UTF-8") } ?: h
        }
        val page = Regex("^https://gamefaqs\\.gamespot\\.com/([a-z0-9-]+)/(\\d+-[a-z0-9-]+)")
        for (link in links) {
            val m = page.find(link) ?: continue
            if (slug != null && m.groupValues[1] != slug) continue
            return "https://gamefaqs.gamespot.com/${m.groupValues[1]}/${m.groupValues[2]}/faqs"
        }
        return null
    }
}
