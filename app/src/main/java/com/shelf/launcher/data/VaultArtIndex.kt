package com.shelf.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Batch 523 (owner: "Save Vault entries without artwork when the game isn't on this handheld look broken"): what a save's
 * game looked like, kept so its Save Vault entry stays complete on a device where the game isn't installed.
 *
 * Why entries lost their picture: a save's picture came only from THIS device's library — its scraped art is kept per
 * device (keyed by the ROM's path) and never synced; a save whose game isn't here got a fresh look-up by the name on its
 * memory card or file ("WWESmackDown!vs.RAW"), which often finds nothing, and then showed two letters on a blank label.
 *
 * Now every device that HAS the game records, for each game with saves: its console, the names its saves go by (the game's
 * name, its file's name, the saves' own titles) and the web addresses of its cover, logo and icon (never a picture's bytes,
 * never a local file). The list is kept here (files/vault_art_index.json) and in "Voltcap Sync/vault-art.json" on Drive,
 * merged by union (nothing is ever removed by another device), ~200 bytes a game. A device without the game finds its entry
 * by console + any of those names. The pictures themselves come through the image cache like every other cover.
 */
object VaultArtIndex {
    data class Entry(
        /** SaveCapsules.canonicalPlatform of the console ("PS2", "GameCube", "Genesis"). */
        val platform: String,
        /** [norm]alised names the game's saves go by. */
        val names: Set<String>,
        val cover: String? = null,
        val logo: String? = null,
        val icon: String? = null,
        val at: Long = 0L
    ) {
        val id: String get() = "$platform|${names.minOrNull().orEmpty()}"
    }

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()
    @Volatile private var loaded = false
    /** QA only (isolated app, `voltcap_test_vault_fixture`): entries shown but never written or synced. */
    @Volatile var fixture: List<Entry> = emptyList()

    /** Letters and digits of a name, lower case, without (region) / [tags]: "WWE SmackDown! vs. RAW (USA)" → "wwesmackdownvsraw". Pure. */
    fun norm(name: String): String = name.replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ").lowercase().filter { it.isLetterOrDigit() }

    fun platformKey(platform: String): String = SaveCapsules.canonicalPlatform(platform)

    /** Only web addresses travel between devices (a picked file or ES-DE's local picture means nothing on another one). Pure. */
    fun shareable(url: String?): String? = url?.takeIf { it.startsWith("https://") || it.startsWith("http://") }

    /** The entry for a save named [name] on [platform]: same console and one of its names; else, by name alone, when only one console has it. Pure. */
    fun find(list: List<Entry>, platform: String, name: String): Entry? {
        val n = norm(name); if (n.length < 3) return null
        val p = platformKey(platform)
        val byName = list.filter { n in it.names }
        return byName.firstOrNull { it.platform == p } ?: byName.distinctBy { it.platform }.singleOrNull()
    }

    fun find(platform: String, name: String): Entry? = find(fixture + _entries.value, platform, name)

    /** Union of two lists: same game (console + a shared name) → one entry, names joined, the newer one's pictures first. Pure. */
    fun merge(a: List<Entry>, b: List<Entry>): List<Entry> {
        val out = ArrayList<Entry>()
        for (e in (a + b).sortedBy { it.at }) {
            val i = out.indexOfFirst { it.platform == e.platform && it.names.any { n -> n in e.names } }
            if (i < 0) out += e else {
                val o = out[i]
                out[i] = Entry(o.platform, o.names + e.names, e.cover ?: o.cover, e.logo ?: o.logo, e.icon ?: o.icon, maxOf(o.at, e.at))
            }
        }
        return out.sortedBy { it.id }
    }

    fun toJson(list: List<Entry>): String = JSONObject().put("format", 1).put("games", JSONArray().apply {
        list.forEach { e ->
            put(JSONObject().put("p", e.platform).put("n", JSONArray(e.names.sorted())).put("at", e.at).apply {
                e.cover?.let { put("c", it) }; e.logo?.let { put("l", it) }; e.icon?.let { put("i", it) }
            })
        }
    }).toString()

    fun fromJson(s: String): List<Entry> = runCatching {
        val a = JSONObject(s).getJSONArray("games")
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val names = o.optJSONArray("n")?.let { n -> (0 until n.length()).map { n.getString(it) }.filter { it.isNotBlank() }.toSet() }.orEmpty()
            if (names.isEmpty()) null else Entry(o.getString("p"), names,
                shareable(o.optString("c").ifBlank { null }), shareable(o.optString("l").ifBlank { null }),
                shareable(o.optString("i").ifBlank { null }), o.optLong("at"))
        }
    }.getOrDefault(emptyList())

    private fun file(context: Context) = File(context.filesDir, "vault_art_index.json")

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val f = file(context)
        if (f.isFile) _entries.value = fromJson(runCatching { f.readText() }.getOrDefault(""))
    }

    /** Adds what this device knows (games it has, with saves). Written only when something changed. */
    fun record(context: Context, found: List<Entry>) {
        load(context)
        val useful = found.filter { it.names.isNotEmpty() && (it.cover != null || it.logo != null || it.icon != null) }
        if (useful.isEmpty()) return
        val merged = merge(_entries.value, useful)
        if (sameContent(merged, _entries.value)) return
        _entries.value = merged
        write(context, merged)
    }

    /** What changed apart from the time stamps. Pure. */
    fun sameContent(a: List<Entry>, b: List<Entry>): Boolean = a.map { it.copy(at = 0L) } == b.map { it.copy(at = 0L) }

    private fun write(context: Context, list: List<Entry>) = runCatching {
        val f = file(context); val tmp = File(f.path + ".tmp")
        tmp.writeText(toJson(list)); tmp.renameTo(f)
    }

    /** CloudSync: Drive's copy merged in; returns the union to upload when Drive's differs (null: nothing to upload). */
    fun mergeRemote(context: Context, remote: List<Entry>): List<Entry>? {
        load(context)
        val merged = merge(_entries.value, remote)
        if (!sameContent(merged, _entries.value)) { _entries.value = merged; write(context, merged) }
        return if (sameContent(merged, remote)) null else merged
    }
}
