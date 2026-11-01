package com.shelf.launcher.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Update 422: ES-DE's own downloaded artwork, reused read-only.
 *
 * ES-DE keeps its scraped media as `<media dir>/<system>/<type>/<rom base name>.<ext>` (default media dir:
 * `ES-DE/downloaded_media`, or the `MediaDirectory` set in `ES-DE/settings/es_settings.xml`). VoltCap never writes,
 * moves, renames or deletes anything there: it only lists the folders and hands the files' addresses to the image loader.
 *
 * - Where: the folder the user picked (Storage Access Framework tree, read permission only, persisted), else ES-DE's
 *   standard locations read directly (VoltCap already has all-files access for the ROM scan).
 * - Matching: the game's console → ES-DE system folders ([SYSTEMS]) plus the ROM's own parent folder name (ROM folders
 *   usually mirror ES-DE's), and the ROM file's exact base name. No fuzzy or region guessing — a game with no exact match is
 *   reported as unmatched instead of getting another game's picture.
 * - Priority ([Features.esdePriority]): VoltCap's own picks (Artwork & details) always win; then ES-DE first, or ES-DE
 *   only where VoltCap has nothing. Missing / removed storage: the index is kept, a picture that can't be read simply
 *   doesn't load and the next source shows; Rescan / Disconnect in Settings.
 * - Pre-rendered 3D boxes and mix images are flat pictures (offered in the artwork picker), never used as 3D models.
 */
object EsdeMedia {
    /** ES-DE system folder names for VoltCap's console codes (the folder the ROM sits in is tried too). */
    val SYSTEMS: Map<String, List<String>> = mapOf(
        "PS1" to listOf("psx"), "PS2" to listOf("ps2"), "PS3" to listOf("ps3"), "PSP" to listOf("psp"), "PSVita" to listOf("psvita"),
        "GameCube" to listOf("gc"), "Wii" to listOf("wii"), "WiiU" to listOf("wiiu"), "Switch" to listOf("switch"),
        "NES" to listOf("nes", "famicom"), "SNES" to listOf("snes", "sfc", "snesna"), "N64" to listOf("n64"),
        "GB" to listOf("gb"), "GBC" to listOf("gbc"), "GBA" to listOf("gba"), "NDS" to listOf("nds"), "3DS" to listOf("n3ds", "3ds"),
        "VirtualBoy" to listOf("virtualboy"), "Genesis" to listOf("genesis", "megadrive"), "MegaDrive" to listOf("megadrive", "genesis"),
        "MasterSystem" to listOf("mastersystem"), "SegaCD" to listOf("segacd", "megacd"), "Saturn" to listOf("saturn"),
        "Dreamcast" to listOf("dreamcast"), "GameGear" to listOf("gamegear"), "PCEngine" to listOf("pcengine", "tg16"),
        "PCEngineCD" to listOf("pcenginecd", "tg-cd"), "NeoGeo" to listOf("neogeo"), "NeoGeoCD" to listOf("neogeocd"),
        "NeoGeoPocket" to listOf("ngpc", "ngp"), "WonderSwan" to listOf("wonderswancolor", "wonderswan"),
        "Atari2600" to listOf("atari2600"), "Atari7800" to listOf("atari7800"), "Lynx" to listOf("atarilynx"), "Jaguar" to listOf("atarijaguar"),
        "Arcade" to listOf("arcade", "mame", "fbneo"), "32X" to listOf("sega32x"), "Naomi" to listOf("naomi"), "Atomiswave" to listOf("atomiswave"),
        "3DO" to listOf("3do"), "Xbox" to listOf("xbox"), "Xbox360" to listOf("xbox360"), "DOS" to listOf("dos"), "ScummVM" to listOf("scummvm"),
        "MSX" to listOf("msx"), "C64" to listOf("c64"), "Amiga" to listOf("amiga"), "PICO8" to listOf("pico8"), "Pico8" to listOf("pico8"),
        "PokemonMini" to listOf("pokemini"), "ColecoVision" to listOf("colecovision"), "Intellivision" to listOf("intellivision"),
        "Vectrex" to listOf("vectrex"), "CDi" to listOf("cdimono1")
    )

    /** The media types used, ES-DE folder name → what VoltCap does with it. */
    enum class Kind(val folder: String, val label: String) {
        COVER("covers", "Cover"), BOX3D("3dboxes", "3D box (picture)"), BACK("backcovers", "Back cover"),
        SCREENSHOT("screenshots", "Screenshot"), TITLE("titlescreens", "Title screen"), FANART("fanart", "Fan art"),
        MARQUEE("marquees", "Logo / marquee"), PHYSICAL("physicalmedia", "Disc / cartridge"), MIX("miximages", "Mix image"),
        VIDEO("videos", "Video")
    }

    val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp")
    val VIDEO_EXT = setOf("mp4", "webm", "mkv")

    /** One game's ES-DE files (addresses the image loader reads), by kind. */
    data class Found(val files: Map<Kind, String>)

    /** The scan's result: system → kind → rom base name (lower case) → address; plus what was skipped. */
    class Index(
        val bySystem: Map<String, Map<Kind, Map<String, String>>>,
        val root: String,
        val unsupported: Int,
        val scannedAt: Long
    )

    @Volatile var index: Index? = null
        private set

    private const val PREFS = "esde_media"

    // ---- settings (own small prefs file; device-bound: the folder is on this handheld) ----
    fun treeUri(ctx: Context): String? = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tree", null)
    fun enabled(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", false)

    fun setTree(ctx: Context, uri: Uri) {
        // Read only: the permission asked for and kept is FLAG_GRANT_READ_URI_PERMISSION, never write.
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("tree", uri.toString()).putBoolean("on", true).apply()
    }

    fun useAutoFolder(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("tree").putBoolean("on", true).apply()
    }

    /** Disconnect: forget the folder (and its permission) and the index. ES-DE's files are untouched. */
    fun disconnect(ctx: Context) {
        treeUri(ctx)?.let { t -> runCatching { ctx.contentResolver.releasePersistableUriPermission(Uri.parse(t), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        runCatching { File(ctx.filesDir, "esde_index.json").delete() } // (our own cache file, not ES-DE's)
        index = null
    }

    // ---- finding the media folder ----
    private fun storageRoots(): List<File> = buildList {
        add(File("/storage/emulated/0"))
        runCatching { File("/storage").listFiles()?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" }?.let { addAll(it) } }
    }

    /** ES-DE's media folders on this device, the standard places and es_settings.xml's MediaDirectory. */
    fun autoRoots(): List<File> {
        val out = ArrayList<File>()
        for (r in storageRoots()) {
            for (base in listOf(File(r, "ES-DE"), File(r, "Android/data/org.es_de.frontend/files/ES-DE"))) {
                val settings = File(base, "settings/es_settings.xml")
                runCatching {
                    if (settings.canRead()) Regex("""name="MediaDirectory"\s+value="([^"]*)"""").find(settings.readText())?.groupValues?.get(1)
                        ?.takeIf { it.isNotBlank() }?.let { p -> out += File(p.replace("%ESPATH%", base.parent ?: "")) }
                }
                out += File(base, "downloaded_media")
            }
        }
        return out.filter { runCatching { it.isDirectory && it.canRead() }.getOrDefault(false) }.distinctBy { it.absolutePath }
    }

    // ---- scanning (read-only listing) ----
    /** Builds the index from the picked folder (or the standard places). Never throws; an unreadable folder gives an empty index. */
    fun scan(ctx: Context): Index {
        var unsupported = 0
        val by = HashMap<String, HashMap<Kind, HashMap<String, String>>>()
        fun put(system: String, kind: Kind, name: String, address: String) {
            val ext = name.substringAfterLast('.', "").lowercase()
            val ok = if (kind == Kind.VIDEO) ext in VIDEO_EXT else ext in IMAGE_EXT
            if (!ok) { unsupported++; return }
            by.getOrPut(system.lowercase()) { HashMap() }.getOrPut(kind) { HashMap() }[name.substringBeforeLast('.').lowercase()] = address
        }
        val tree = treeUri(ctx)
        var root = ""
        if (tree != null) {
            root = tree
            runCatching {
                // (Plain DocumentsContract listing — read-only queries, no extra library.)
                val treeUri = Uri.parse(tree)
                val topId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                var top = children(ctx, treeUri, topId)
                // The picked folder can be ES-DE's own folder, its downloaded_media, or the media folder itself.
                top.firstOrNull { it.isDir && it.name.equals("downloaded_media", true) }?.let { top = children(ctx, treeUri, it.id) }
                top.filter { it.isDir }.forEach { sys ->
                    children(ctx, treeUri, sys.id).filter { it.isDir }.forEach { kd ->
                        val kind = Kind.values().firstOrNull { it.folder.equals(kd.name, true) } ?: return@forEach
                        children(ctx, treeUri, kd.id).filter { !it.isDir }.forEach { f ->
                            put(sys.name, kind, f.name, android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, f.id).toString())
                        }
                    }
                }
            }
        } else {
            for (m in autoRoots()) {
                if (root.isEmpty()) root = m.absolutePath
                m.listFiles()?.filter { it.isDirectory }?.forEach { sys ->
                    sys.listFiles()?.filter { it.isDirectory }?.forEach { kd ->
                        val kind = Kind.values().firstOrNull { it.folder.equals(kd.name, true) } ?: return@forEach
                        kd.listFiles()?.forEach { f -> if (f.isFile) put(sys.name, kind, f.name, Uri.fromFile(f).toString()) }
                    }
                }
            }
        }
        val idx = Index(by, root, unsupported, System.currentTimeMillis())
        index = idx
        runCatching { save(ctx, idx) }
        return idx
    }

    /** %XX decoding of a content address's name (a plain path passes through; "+" stays "+"). */
    internal fun pct(s: String): String = if ('%' !in s) s else runCatching { java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)

    /** ES-DE's pictures first (true) or only where VoltCap has none (false, the default). */
    fun prefer(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("prefer", false)
    fun setPrefer(ctx: Context, on: Boolean) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("prefer", on).apply() }

    private class Doc(val id: String, val name: String, val isDir: Boolean)

    private fun children(ctx: Context, tree: Uri, parentId: String): List<Doc> {
        val uri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val out = ArrayList<Doc>()
        ctx.contentResolver.query(uri, arrayOf(
            android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID, android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
            while (c.moveToNext()) out += Doc(c.getString(0), c.getString(1) ?: "", c.getString(2) == android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
        }
        return out
    }

    /** The game's own base name (its ROM file without the extension), lower case. */
    fun baseName(g: Game): String? {
        if (g.sourceType != SourceType.ROM) return null
        val path = g.romPath ?: return null
        val last = pct(path.substringAfterLast('/')).substringAfterLast(':').substringAfterLast('/')
        return last.substringBeforeLast('.').lowercase().takeIf { it.isNotBlank() }
    }

    /** The ES-DE system folders tried for [g]: its console's, then the folder its ROM is in. */
    fun systemsFor(g: Game): List<String> {
        val parent = pct(g.romPath ?: "").trimEnd('/').substringBeforeLast('/').substringAfterLast('/').substringAfterLast(':').lowercase()
        return (SYSTEMS[g.platform].orEmpty() + listOf(parent, g.platform.lowercase())).filter { it.isNotBlank() }.distinct()
    }

    /** [g]'s files in [idx], or null when nothing matches exactly. */
    fun find(g: Game, idx: Index? = index): Found? {
        val i = idx ?: return null
        val name = baseName(g) ?: return null
        for (sys in systemsFor(g)) {
            val kinds = i.bySystem[sys] ?: continue
            val files = Kind.values().mapNotNull { k -> kinds[k]?.get(name)?.let { k to it } }.toMap()
            if (files.isNotEmpty()) return Found(files)
        }
        return null
    }

    /** What a scan matched for [games]: matched / unmatched counts and the unmatched names (for the report). */
    fun report(games: List<Game>, idx: Index? = index): Triple<Int, Int, List<String>> {
        val roms = games.filter { it.sourceType == SourceType.ROM }
        val miss = roms.filter { find(it, idx) == null }
        return Triple(roms.size - miss.size, miss.size, miss.map { it.name })
    }

    /**
     * The art fields ES-DE gives [g]: cover (cover, else the flat 3D box picture), hero / backdrop (fan art, else a
     * screenshot), logo (marquee). [keep] = what VoltCap already has; [preferEsde] decides who wins a field both have.
     */
    fun mergeArt(g: Game, keep: GameArt?, preferEsde: Boolean, idx: Index? = index): GameArt? {
        val f = find(g, idx)?.files ?: return keep
        val cover = f[Kind.COVER] ?: f[Kind.BOX3D]
        val hero = f[Kind.FANART] ?: f[Kind.SCREENSHOT]
        val logo = f[Kind.MARQUEE]
        fun pick(mine: String?, theirs: String?) = if (preferEsde) theirs ?: mine else mine ?: theirs
        return GameArt(
            gridUrl = pick(keep?.gridUrl, cover), wideGridUrl = keep?.wideGridUrl, heroUrl = pick(keep?.heroUrl, hero),
            logoUrl = pick(keep?.logoUrl, logo), animatedHeroUrl = keep?.animatedHeroUrl, stillGridUrl = keep?.stillGridUrl
        )
    }

    /** True for an address that came from ES-DE's folder (to take it back out on Disconnect). */
    fun isEsde(address: String?, idx: Index? = index): Boolean {
        if (address == null) return false
        val r = idx?.root?.takeIf { it.isNotEmpty() } ?: return address.contains("/downloaded_media/") || address.contains("org.es_de")
        return address.startsWith(r) || address.startsWith(Uri.fromFile(File(r)).toString()) || address.contains("/downloaded_media/")
    }

    // ---- our cached index (in VoltCap's own files, so a start doesn't rescan) ----
    private fun save(ctx: Context, idx: Index) {
        val o = org.json.JSONObject().put("root", idx.root).put("unsupported", idx.unsupported).put("at", idx.scannedAt)
        val sys = org.json.JSONObject()
        idx.bySystem.forEach { (s, kinds) ->
            val ko = org.json.JSONObject()
            kinds.forEach { (k, names) -> ko.put(k.name, org.json.JSONObject(names as Map<*, *>)) }
            sys.put(s, ko)
        }
        o.put("systems", sys)
        File(ctx.filesDir, "esde_index.json").writeText(o.toString())
    }

    /** Loads the cached index (no scan); null when there is none or ES-DE media is off. */
    fun load(ctx: Context): Index? {
        if (!enabled(ctx)) return null
        index?.let { return it }
        return runCatching {
            val o = org.json.JSONObject(File(ctx.filesDir, "esde_index.json").readText())
            val sys = o.getJSONObject("systems")
            val by = HashMap<String, Map<Kind, Map<String, String>>>()
            sys.keys().forEach { s ->
                val ko = sys.getJSONObject(s)
                val kinds = HashMap<Kind, Map<String, String>>()
                ko.keys().forEach { kn ->
                    val k = Kind.values().firstOrNull { it.name == kn } ?: return@forEach
                    val no = ko.getJSONObject(kn)
                    kinds[k] = no.keys().asSequence().associateWith { no.getString(it) }
                }
                by[s] = kinds
            }
            Index(by, o.optString("root"), o.optInt("unsupported"), o.optLong("at")).also { index = it }
        }.getOrNull()
    }

    /** For tests: an index built by hand. */
    internal fun setIndexForTest(i: Index?) { index = i }
}
