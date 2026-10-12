package com.shelf.launcher.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.InputStream

/** Size and date of one file in a synced save folder. */
internal data class LocalEntry(val size: Long, val modified: Long)

/**
 * A save folder as the sync sees it: list files, read one, replace one.
 * Two kinds — a normal folder on internal storage ([FileTree]), or a folder
 * another app shares through Android's file picker ([DocTree]; Dolphin keeps
 * its saves in Android/data, which Voltcap can't open directly on Android 11+).
 */
internal interface SyncTree {
    fun exists(): Boolean
    /** Every file, keyed by its path inside the folder ("GBA/zelda.srm"). */
    fun list(): Map<String, LocalEntry>
    fun open(rel: String): InputStream
    /** An upload body that streams the file. */
    fun body(rel: String, entry: LocalEntry): RequestBody
    /** Replaces (or creates) [rel] with the contents of [from]. */
    fun write(rel: String, from: File, modified: Long)
    /** Fresh size/date after [write] — the SAF kind can't choose its own date. */
    fun entry(rel: String): LocalEntry?
}

/** Folders emulators keep save states in — never synced. */
internal val STATE_DIRS = setOf("states", "savestates", "sstates", "statesaves", "ppsspp_state")

/** RetroArch .state/.stateN/.state.auto (+ thumbnails), PPSSPP .ppst,
 * DraStic .dss, Citra .cst, NetherSX2/ARMSX2 .p2s, Mupen .stN, mGBA .ssN,
 * Dolphin .s01–.s09. */
private val STATE_FILE = Regex(
    ".*\\.(state\\d*|state\\.auto|ppst|dss|cst|p2s|st\\d|ss\\d|s0\\d)(\\.png|\\.jpg)?$",
    RegexOption.IGNORE_CASE
)

/** Save states are left out of sync, always: in-game saves only. */
fun isSaveState(relativePath: String): Boolean =
    STATE_FILE.matches(relativePath.substringAfterLast('/')) ||
        relativePath.split('/').dropLast(1).any { it.lowercase() in STATE_DIRS }

/** A folder that is itself a save-state folder ("…/RetroArch/states"). */
fun isStateFolder(path: String): Boolean =
    path.trimEnd('/').substringAfterLast('/').lowercase() in STATE_DIRS

internal const val MAX_SYNC_FILE_BYTES = 512L * 1024 * 1024
private val BINARY = "application/octet-stream".toMediaType()

internal class FileTree(private val root: File) : SyncTree {
    override fun exists() = root.isDirectory

    override fun list(): Map<String, LocalEntry> {
        val out = mutableMapOf<String, LocalEntry>()
        val rootPath = root.absolutePath.trimEnd('/') + "/"
        root.walkTopDown()
            .onEnter { it == root || (!it.name.startsWith(".") && it.name.lowercase() !in STATE_DIRS) }
            .filter { it.isFile && !it.name.startsWith(".") && it.length() <= MAX_SYNC_FILE_BYTES }
            .forEach { out[it.absolutePath.removePrefix(rootPath)] = LocalEntry(it.length(), it.lastModified()) }
        return out
    }

    override fun open(rel: String): InputStream = File(root, rel).inputStream()

    override fun body(rel: String, entry: LocalEntry): RequestBody = File(root, rel).asRequestBody(BINARY)

    override fun write(rel: String, from: File, modified: Long) {
        val target = File(root, rel)
        target.parentFile?.mkdirs()
        // Written next to it first, so a failure never leaves half a save.
        val part = File(target.parentFile, ".${target.name}.voltcap-part")
        from.copyTo(part, overwrite = true)
        // Flush to storage before the swap, so a power cut can't leave an empty file behind.
        runCatching { java.io.FileOutputStream(part, true).use { it.fd.sync() } }
        if (part.length() != from.length()) {
            part.delete()
            error("Couldn't write ${target.name} completely — the old save was kept.")
        }
        // rename() replaces the old file in one step: there's never a moment without a save.
        if (!part.renameTo(target)) {
            // Rare (some storage won't rename over a file): keep the old one aside until the new one is in.
            val old = File(target.parentFile, ".${target.name}.voltcap-old")
            old.delete()
            val hadOld = target.exists()
            if (hadOld && !target.renameTo(old)) {
                part.delete()
                error("Couldn't replace ${target.name}.")
            }
            val ok = part.renameTo(target) || runCatching { part.copyTo(target, overwrite = true); true }.getOrDefault(false)
            if (!ok || target.length() != from.length()) {
                target.delete()
                if (hadOld) old.renameTo(target)
                part.delete()
                error("Couldn't replace ${target.name} — the old save was put back.")
            }
            part.delete()
            old.delete()
        }
        target.setLastModified(modified)
    }

    override fun entry(rel: String): LocalEntry? =
        File(root, rel).takeIf { it.isFile }?.let { LocalEntry(it.length(), it.lastModified()) }
}

/**
 * A folder inside a tree another app shared through Android's picker
 * (OPEN_DOCUMENT_TREE), e.g. Dolphin's own "Dolphin" entry. [subPath] is the
 * part inside the shared tree ("GC", "Wii/title"), matched ignoring case.
 */
internal class DocTree(context: Context, private val tree: Uri, private val subPath: String) : SyncTree {
    private val resolver = context.contentResolver
    private val hasPermission = resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }

    private data class Doc(val id: String, val name: String, val isDir: Boolean, val size: Long, val modified: Long)

    private val index = mutableMapOf<String, Doc>()
    private var rootId: String? = null

    private val columns = arrayOf(
        Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED
    )

    private fun uri(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    private fun read(c: android.database.Cursor) = Doc(
        id = c.getString(0),
        name = c.getString(1) ?: "",
        isDir = c.getString(2) == Document.MIME_TYPE_DIR,
        size = if (c.isNull(3)) 0L else c.getLong(3),
        modified = if (c.isNull(4)) 0L else c.getLong(4)
    )

    private fun children(parentId: String): List<Doc> {
        val out = mutableListOf<Doc>()
        resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId), columns, null, null, null)
            ?.use { c -> while (c.moveToNext()) out += read(c) }
        return out
    }

    private fun single(id: String): Doc? =
        resolver.query(uri(id), columns, null, null, null)?.use { c -> if (c.moveToFirst()) read(c) else null }

    private fun resolveRoot(): String? {
        rootId?.let { return it }
        if (!hasPermission) return null
        var id = DocumentsContract.getTreeDocumentId(tree)
        for (segment in subPath.split('/').filter { it.isNotEmpty() }) {
            id = children(id).firstOrNull { it.isDir && it.name.equals(segment, ignoreCase = true) }?.id ?: return null
        }
        rootId = id
        return id
    }

    override fun exists() = runCatching { resolveRoot() != null }.getOrDefault(false)

    override fun list(): Map<String, LocalEntry> {
        index.clear()
        val root = resolveRoot() ?: return emptyMap()
        val out = mutableMapOf<String, LocalEntry>()
        fun walk(id: String, prefix: String) {
            children(id).forEach { d ->
                if (d.name.startsWith(".")) return@forEach
                val rel = prefix + d.name
                if (d.isDir) {
                    if (d.name.lowercase() in STATE_DIRS) return@forEach
                    index[rel] = d
                    walk(d.id, "$rel/")
                } else if (d.size <= MAX_SYNC_FILE_BYTES) {
                    index[rel] = d
                    out[rel] = LocalEntry(d.size, d.modified)
                }
            }
        }
        walk(root, "")
        return out
    }

    override fun open(rel: String): InputStream {
        val doc = index[rel] ?: error("$rel isn't there any more.")
        return resolver.openInputStream(uri(doc.id)) ?: error("Couldn't read $rel.")
    }

    override fun body(rel: String, entry: LocalEntry): RequestBody = object : RequestBody() {
        override fun contentType() = BINARY
        override fun contentLength() = entry.size
        override fun writeTo(sink: BufferedSink) {
            open(rel).use { sink.writeAll(it.source()) }
        }
    }

    private fun ensureDir(rel: String): String {
        if (rel.isEmpty()) return resolveRoot() ?: error("Folder not reachable.")
        index[rel]?.let { if (it.isDir) return it.id }
        val parent = ensureDir(rel.substringBeforeLast('/', ""))
        val name = rel.substringAfterLast('/')
        val id = children(parent).firstOrNull { it.isDir && it.name == name }?.id
            ?: DocumentsContract.createDocument(resolver, uri(parent), Document.MIME_TYPE_DIR, name)
                ?.let { DocumentsContract.getDocumentId(it) }
            ?: error("Couldn't make the folder $name.")
        index[rel] = Doc(id, name, true, 0L, 0L)
        return id
    }

    override fun write(rel: String, from: File, modified: Long) {
        val existing = index[rel]?.takeIf { !it.isDir }
        val target = if (existing != null) uri(existing.id) else {
            val parent = ensureDir(rel.substringBeforeLast('/', ""))
            DocumentsContract.createDocument(resolver, uri(parent), "application/octet-stream", rel.substringAfterLast('/'))
                ?: error("Couldn't create $rel.")
        }
        // Shared folders can't swap files in one step, so: keep the current save in memory/cache,
        // write the new one, read it back, and put the old one back if anything doesn't match.
        val backup: File? = if (existing != null) runCatching {
            File.createTempFile("saf", ".bak", from.parentFile).also { b ->
                resolver.openInputStream(target)?.use { input -> b.outputStream().use { input.copyTo(it) } }
                    ?: error("unreadable")
            }
        }.getOrNull() else null
        fun writeFrom(src: File) {
            // "wt" truncates; plain "w" can leave old bytes behind a shorter save.
            val out = runCatching { resolver.openOutputStream(target, "wt") }.getOrNull()
                ?: runCatching { resolver.openOutputStream(target, "rwt") }.getOrNull()
                ?: error("Couldn't write $rel.")
            out.use { o -> src.inputStream().use { it.copyTo(o) }; o.flush() }
        }
        fun matches(src: File): Boolean = runCatching {
            resolver.openInputStream(target)?.use { input ->
                src.inputStream().use { expected -> streamsEqual(input, expected) }
            } ?: false
        }.getOrDefault(false)
        try {
            writeFrom(from)
            if (!matches(from)) {
                if (backup != null) runCatching { writeFrom(backup) }
                error("Couldn't write $rel completely — the old save was put back.")
            }
        } catch (e: Exception) {
            if (backup != null && !matches(backup)) runCatching { writeFrom(backup) }
            // A file made just now that didn't arrive whole: removed, or the next sync would see a
            // newer local save and send the broken one to Drive.
            if (existing == null) runCatching { DocumentsContract.deleteDocument(resolver, target) }
            throw e
        } finally {
            backup?.delete()
        }
        single(DocumentsContract.getDocumentId(target))?.let { index[rel] = it }
    }

    override fun entry(rel: String): LocalEntry? = index[rel]?.let { LocalEntry(it.size, it.modified) }

    /** Names of the folders at the top of the shared tree (to spot Dolphin). */
    fun topFolders(): List<String> =
        runCatching { children(DocumentsContract.getTreeDocumentId(tree)).filter { it.isDir }.map { it.name } }
            .getOrDefault(emptyList())

    fun displayName(): String? =
        runCatching { single(DocumentsContract.getTreeDocumentId(tree))?.name }.getOrNull()
}

// ---- What counts as a save ----

/**
 * Which files in a folder are saves. Only these are synced — logs, configs,
 * BIOS files, shaders, installed games and save states never are.
 */
enum class SaveKind {
    /** A folder you picked yourself: every known save type, nothing else. */
    GENERIC,
    /** RetroArch's saves folder: in-game saves of every core. */
    RETROARCH,
    /** PPSSPP SAVEDATA: whole per-game folders (their icons are part of the save). */
    PSP_SAVEDATA,
    /** PCSX2 family (ARMSX2, NetherSX2, AetherSX2): .ps2 cards and folder cards. */
    PS2_MEMCARDS,
    /** DuckStation, ePSXe: PS1 memory cards. */
    PS1_MEMCARDS,
    /** DraStic's backup folder. */
    NDS,
    /** Flycast's data folder: VMU saves only (the BIOS lives there too). */
    DREAMCAST_VMU,
    /** Dolphin GC: .gci saves and .raw memory cards (not IPL/DSP dumps). */
    DOLPHIN_GC,
    /** Dolphin Wii/title: only the …/data/ save folders, not installed channels. */
    DOLPHIN_WII,
    /** Citra: only title …/data/ save folders, not installed games or DLC. */
    CITRA,
    /** Mupen64Plus: EEPROM, SRAM, FlashRAM, Controller Pak. */
    N64;

    companion object {
        fun of(name: String?): SaveKind? = values().firstOrNull { it.name == name }
    }
}

private val RETRO_SAVES = setOf(
    "srm", "sav", "rtc", "eep", "fla", "sra", "mpk", "mcr", "mcd", "ps2", "dsv", "bkr", "bcr",
    "smpc", "brm", "nv", "flash", "vmu", "vms", "gci"
)
private val PS1_CARDS = setOf("mcd", "mcr", "mc", "gme", "mcs", "psm")
private val N64_SAVES = setOf("eep", "sra", "fla", "mpk", "srm")
private val VMU_SAVE = Regex("vmu_save_.*\\.bin", RegexOption.IGNORE_CASE)

private fun ext(name: String) = name.substringAfterLast('.', "").lowercase()

/** Icons, sound and video a PSP save carries for the XMB — not needed to load it. */
private val PSP_MEDIA = setOf("png", "jpg", "at3", "pmf", "mp4")

/** Wii title types that hold game saves: discs (00010000), WiiWare/channels
 * (00010001) and disc channels (00010004). System titles (00000001, 00010002,
 * 00010008) hold settings and system data — never synced. */
private val WII_GAME_TYPES = setOf("00010000", "00010001", "00010004")

/** Install / licence files, never saves. */
private val WII_NOT_SAVES = setOf("tmd", "tik", "app", "cert", "wad")

/** Whether [rel] (a path inside the folder) is a save for this kind of folder. */
fun SaveKind.accepts(rel: String): Boolean {
    if (isSaveState(rel)) return false
    val parts = rel.split('/')
    val name = parts.last()
    val dirs = parts.dropLast(1).map { it.lowercase() }
    if (name.startsWith(".")) return false
    return when (this) {
        SaveKind.RETROARCH -> ext(name) in RETRO_SAVES || VMU_SAVE.matches(name) ||
            ("savedata" in dirs && ext(name) !in PSP_MEDIA) || dirs.any { it.endsWith(".ps2") }
        // PARAM.SFO + the game's own data files; the save won't load without the SFO.
        SaveKind.PSP_SAVEDATA -> dirs.isNotEmpty() && ext(name) !in PSP_MEDIA
        SaveKind.PS2_MEMCARDS -> ext(name) == "ps2" || dirs.any { it.endsWith(".ps2") }
        SaveKind.PS1_MEMCARDS -> ext(name) in PS1_CARDS
        SaveKind.NDS -> ext(name) == "dsv" || ext(name) == "sav"
        SaveKind.DREAMCAST_VMU -> VMU_SAVE.matches(name)
        SaveKind.DOLPHIN_GC -> ext(name) == "gci" || ext(name) == "raw"
        // Only <type>/<game>/data/… of game titles, e.g. 00010000/52534245/data/…
        SaveKind.DOLPHIN_WII -> dirs.size >= 3 && dirs[0] in WII_GAME_TYPES && dirs[2] == "data" &&
            ext(name) !in WII_NOT_SAVES
        SaveKind.CITRA -> "data" in dirs && "content" !in dirs
        SaveKind.N64 -> ext(name) in N64_SAVES
        SaveKind.GENERIC -> ext(name) in RETRO_SAVES || ext(name) in PS1_CARDS || VMU_SAVE.matches(name) ||
            ("savedata" in dirs && ext(name) !in PSP_MEDIA) || dirs.any { it.endsWith(".ps2") }
    }
}

// ---- Suggested save folders ----

/**
 * [keyword]: when none of the [candidates] exist, any folder at the top of
 * internal storage whose name contains it is tried too, with [sub] inside —
 * so "Armsx2", "ARMSX2-data" or "armsx2 (1)" are still found.
 */
private class Preset(
    val name: String,
    val kind: SaveKind,
    val candidates: List<String>,
    val keyword: String? = null,
    val sub: String? = null
)

/**
 * Emulator save folders offered when they exist on this device — paths
 * relative to internal storage, matched ignoring upper/lower case. The name
 * is the Drive folder, so it's the same on every device. Only in-game saves:
 * no save-state folders, ever.
 */
private val PRESETS = listOf(
    Preset("RetroArch saves", SaveKind.RETROARCH, listOf("RetroArch/saves"), "retroarch", "saves"),
    Preset("PPSSPP saves", SaveKind.PSP_SAVEDATA, listOf("PPSSPP/PSP/SAVEDATA", "PSP/SAVEDATA"), "ppsspp", "PSP/SAVEDATA"),
    Preset("ARMSX2 memory cards", SaveKind.PS2_MEMCARDS, listOf("ARMSX2/memcards", "Android/data/com.armsx2/files/memcards", "Android/data/come.nanodata.armsx2/files/memcards"), "armsx2", "memcards"),
    Preset("NetherSX2 memory cards", SaveKind.PS2_MEMCARDS, listOf("NetherSX2/memcards", "AetherSX2/memcards"), "nethersx2", "memcards"),
    Preset("DuckStation memory cards", SaveKind.PS1_MEMCARDS, listOf("DuckStation/memcards"), "duckstation", "memcards"),
    Preset("ePSXe memory cards", SaveKind.PS1_MEMCARDS, listOf("epsxe/memcards"), "epsxe", "memcards"),
    Preset("DraStic saves", SaveKind.NDS, listOf("DraStic/backup"), "drastic", "backup"),
    Preset("Flycast saves", SaveKind.DREAMCAST_VMU, listOf("Flycast/data"), "flycast", "data"),
    Preset("Dolphin GameCube", SaveKind.DOLPHIN_GC, listOf("dolphin-emu/GC")),
    Preset("Dolphin Wii", SaveKind.DOLPHIN_WII, listOf("dolphin-emu/Wii/title")),
    Preset("Citra saves", SaveKind.CITRA, listOf("citra-emu/sdmc/Nintendo 3DS")),
    Preset("M64Plus FZ saves", SaveKind.N64, listOf("mupen64plus/GameData"))
)

private const val STORAGE = "/storage/emulated/0"

/** "ARMSX2/memcards" finds "Armsx2/memcards" too. */
private fun resolveIgnoringCase(base: File, rel: String): File? {
    var current = base
    for (segment in rel.split('/').filter { it.isNotEmpty() }) {
        val exact = File(current, segment)
        current = if (exact.isDirectory) exact
        else current.listFiles()?.firstOrNull { it.isDirectory && it.name.equals(segment, ignoreCase = true) }
            ?: return null
    }
    return current
}

internal fun presetFolders(): List<SyncFolder> {
    val storage = File(STORAGE)
    val topLevel by lazy { storage.listFiles()?.filter { it.isDirectory }.orEmpty() }
    return PRESETS.mapNotNull { preset ->
        val found = preset.candidates.firstNotNullOfOrNull { resolveIgnoringCase(storage, it) }
            ?: if (preset.keyword != null && preset.sub != null) {
                topLevel.filter { it.name.contains(preset.keyword, ignoreCase = true) }
                    .firstNotNullOfOrNull { resolveIgnoringCase(it, preset.sub) }
            } else null
        found?.let { SyncFolder(preset.name, it.absolutePath, kind = preset.kind.name) }
    }
}

/** For folders added before kinds existed: guess from the preset name. */
internal fun kindForName(name: String): SaveKind =
    PRESETS.firstOrNull { it.name == name }?.kind ?: SaveKind.GENERIC

/** Dolphin's package names (official + popular forks). */
internal val DOLPHIN_PACKAGES = listOf("org.dolphinemu.dolphinemu", "org.dolphinemu.mmjr", "org.dolphinemu.handheld")

/** Byte-for-byte comparison of two streams (both read to the end). */
internal fun streamsEqual(a: InputStream, b: InputStream): Boolean {
    val bufA = ByteArray(64 * 1024)
    val bufB = ByteArray(64 * 1024)
    while (true) {
        val n = a.readNBytesCompat(bufA)
        val m = b.readNBytesCompat(bufB)
        if (n != m) return false
        if (n <= 0) return true
        for (i in 0 until n) if (bufA[i] != bufB[i]) return false
    }
}

/** Fills [buf] as far as the stream allows (InputStream.readNBytes needs API 33). */
private fun InputStream.readNBytesCompat(buf: ByteArray): Int {
    var total = 0
    while (total < buf.size) {
        val r = read(buf, total, buf.size - total)
        if (r < 0) break
        total += r
    }
    return total
}

/** ARMSX2's package names (GitHub builds, Play build). */
internal val ARMSX2_PACKAGES = listOf("com.armsx2", "come.nanodata.armsx2", "com.nanodata.armsx2")

/**
 * The ARMSX2 check: ARMSX2 is installed but Voltcap can't see its memory cards — they're still in
 * ARMSX2's private folder (Android/data/…), which no other app may read since Android 11. Returns
 * what to tell the player, or null when all is well (or ARMSX2 isn't installed).
 */
internal fun armsx2Problem(context: Context, folders: List<SyncFolder>): String? {
    val pm = context.packageManager
    val installed = ARMSX2_PACKAGES.any { pkg -> runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess }
    if (!installed) return null
    val card = folders.firstOrNull {
        it.saveKind == SaveKind.PS2_MEMCARDS && (it.name.contains("armsx2", true) || it.path.contains("armsx2", true))
    }
    if (card != null) {
        val dir = File(card.path)
        // Picked through Android's picker: can't be checked from here; trust it.
        if (card.treeUri != null) return null
        if (!dir.isDirectory) return "ARMSX2's memory card folder (${card.path}) is gone — pick it again, or point ARMSX2 at a shared folder."
        if (card.path.contains("/Android/data/") && dir.listFiles().isNullOrEmpty()) {
            return "ARMSX2 keeps its memory cards in its private folder, which Android hides from other apps. In ARMSX2's settings, set the App Data Folder to a shared one (e.g. /ARMSX2) — then Voltcap finds ARMSX2/memcards by itself."
        }
        return null
    }
    return "ARMSX2 is installed, but no ARMSX2 memory card folder is synced. In ARMSX2, set the App Data Folder to a shared folder (e.g. /ARMSX2), then add \"ARMSX2 memory cards\" here. (Save states are never synced.)"
}
