package com.shelf.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Update 151: saves on Google Drive in a save folder this device doesn't sync — one set up on another handheld only, or
 * named differently there ("Armsx2 memcards" next to this device's "ARMSX2 memory cards"). The Save Vault only looked
 * at this device's own folders, so Game Boy, GameCube and other saves synced from elsewhere never showed. Drive is
 * listed at most every 20 minutes (and kept on disk, so they still show offline). Reads the network: call off the
 * main thread.
 */
internal object DriveOnlySaves {

    data class Entry(
        val folder: String, val rel: String, val id: String, val md5: String?,
        val size: Long, val modified: Long, val device: String?
    )

    private const val FOLDER_MIME = "application/vnd.google-apps.folder"
    private const val MAX_AGE_MS = 20 * 60_000L

    /** The last listing (when, what); read from disk the first time. */
    @Volatile private var memory: Pair<Long, List<Entry>>? = null
    @Volatile private var stale = false

    /** After a sync: the next look lists Drive again. */
    fun invalidate() { stale = true }

    private fun cacheFile(context: Context) = File(context.filesDir, "drive_only_saves_v1.json")

    /**
     * Update 241 (#76): Drive would be listed again by the next [entries] (nothing kept yet, or the copy is older than
     * 20 minutes / a sync happened since) — the Save Vault then first shows what it has without waiting for it.
     */
    fun needsFetch(context: Context): Boolean {
        val app = context.applicationContext
        CloudSync.load(app)
        if (!CloudSync.state.value.settings.connected) return false
        val cached = memory ?: readCache(app)?.also { memory = it }
        val age = if (cached != null) System.currentTimeMillis() - cached.first else -1L
        return cached == null || stale || age < 0L || age >= MAX_AGE_MS
    }

    /**
     * Every save in Drive's folders this device has no enabled folder for (empty when Drive isn't connected).
     * [allowFetch] false (update 241, #76): only the copy kept on disk, never the network.
     */
    fun entries(context: Context, allowFetch: Boolean = true): List<Entry> {
        val app = context.applicationContext
        CloudSync.load(app)
        val settings = CloudSync.state.value.settings
        if (!settings.connected) return emptyList()
        val mine = settings.folders.filter { it.enabled }.map { it.name.lowercase() }.toSet()
        val now = System.currentTimeMillis()
        val cached = memory ?: readCache(app)?.also { memory = it }
        val age = if (cached != null) now - cached.first else -1L
        val list = if (cached != null && !stale && age >= 0L && age < MAX_AGE_MS) cached.second
        else if (!allowFetch) cached?.second.orEmpty()
        else fetch(app)?.also { fresh -> memory = now to fresh; stale = false; runCatching { writeCache(app, now, fresh) } }
            ?: cached?.second.orEmpty()
        return list.filter { it.folder.lowercase() !in mine }
    }

    /**
     * The same saves as the vault's loose saves, each with the kind of folder it's in (by the folder's name, else by
     * what's in it), only the files that kind counts as saves.
     */
    fun loose(context: Context, allowFetch: Boolean = true): List<CloudSync.LooseSave> {
        val all = entries(context, allowFetch)
        if (all.isEmpty()) return emptyList()
        val out = mutableListOf<CloudSync.LooseSave>()
        all.groupBy { it.folder }.forEach { (folder, files) ->
            val kind = kindOf(folder, files.map { it.rel })
            files.forEach { e ->
                if (!kind.accepts(e.rel)) return@forEach
                var title = e.rel.substringAfterLast('/').substringBeforeLast('.')
                // Dolphin: "<maker>-<game id>-<name>.gci".
                if (kind == SaveKind.DOLPHIN_GC) title = title.replace(Regex("^[0-9A-Za-z]{2}-[A-Za-z0-9]{4}-"), "")
                out += CloudSync.LooseSave(kind, folder, e.rel, title.trim(), e.size, e.modified, e.device, false)
            }
        }
        return out
    }

    /** Which kind of save folder [folder] is: by its name (the usual folders), else by the files in it. */
    fun kindOf(folder: String, rels: List<String>): SaveKind {
        kindForName(folder).takeIf { it != SaveKind.GENERIC }?.let { return it }
        val names = rels.map { it.substringAfterLast('/') }
        val exts = names.map { it.substringAfterLast('.', "").lowercase() }
        val dirs = rels.map { r -> r.split('/').dropLast(1).map { it.lowercase() } }
        return when {
            exts.any { it == "gci" } || folder.contains("gamecube", true) -> SaveKind.DOLPHIN_GC
            dirs.any { d -> d.size >= 3 && d[0].length == 8 && d[0].startsWith("0001000") && d[2] == "data" } -> SaveKind.DOLPHIN_WII
            // (Update 241, #72: a 3DS save folder set up on another device only, under any name — its files are "main",
            // "00000001.metadata"…, no extension: as a plain folder every one of them was dropped.)
            rels.any { SaveLayouts.n3ds(it) != null } -> SaveKind.CITRA
            exts.any { it == "ps2" } || dirs.any { d -> d.any { it.endsWith(".ps2") } } -> SaveKind.PS2_MEMCARDS
            names.any { it.equals("PARAM.SFO", true) } -> SaveKind.PSP_SAVEDATA
            exts.isNotEmpty() && exts.all { it in PS1_EXTS } -> SaveKind.PS1_MEMCARDS
            exts.isNotEmpty() && exts.all { it in N64_EXTS } -> SaveKind.N64
            else -> SaveKind.GENERIC
        }
    }

    private val PS1_EXTS = setOf("mcd", "mcr", "mc", "gme", "mcs", "psm")
    private val N64_EXTS = setOf("eep", "sra", "fla", "mpk")

    /**
     * The PSP saves among [files] (Drive-only PPSSPP folders): one per save folder ("ULUS10041DATA00/…"), named from its
     * PARAM.SFO (a small download, kept) or else its folder.
     */
    fun pspSaves(context: Context, files: List<CloudSync.LooseSave>, allowNetwork: Boolean = true): List<MemoryCards.CardSave> {
        val out = mutableListOf<MemoryCards.CardSave>()
        files.filter { it.path.contains('/') }.groupBy { it.folder to it.path.substringBeforeLast('/') }.forEach { (key, list) ->
            val (folder, dir) = key
            val save = dir.substringAfterLast('/')
            val sfo = list.firstOrNull { it.path.substringAfterLast('/').equals("PARAM.SFO", true) }
            val title = sfo?.let { bytes(context, folder, it.path, 256_000L, allowNetwork) }?.let { SaveCapsules.sfoTitle(it) }?.takeIf { it.isNotBlank() } ?: save
            val from = list.firstOrNull()?.fromDevice
            out += MemoryCards.CardSave(
                "SAVEDATA", "$folder · synced from ${from ?: "another device"}", MemoryCards.normaliseCode(save), title,
                list.sumOf { it.size }, list.maxOf { it.modified }
            )
        }
        return out
    }

    /**
     * A Drive-only save's bytes (memory cards, GameCube saves, a PSP save's PARAM.SFO), up to [maxBytes]: downloaded
     * once and kept in the cache by its content's hash. Null when it isn't one of these files, Drive isn't reachable or
     * it's too big.
     */
    fun bytes(context: Context, folder: String, rel: String, maxBytes: Long, allowNetwork: Boolean = true): ByteArray? = runCatching {
        val app = context.applicationContext
        val list = memory?.second ?: readCache(app)?.second ?: return@runCatching null
        val e = list.firstOrNull { it.folder == folder && it.rel == rel } ?: return@runCatching null
        if (e.size > maxBytes) return@runCatching null
        val name = (e.md5 ?: e.id).replace(Regex("[^A-Za-z0-9]"), "")
        val cache = File(File(app.cacheDir, "remote_saves"), "$name.bin")
        if (cache.length() > 0) return@runCatching cache.readBytes()
        if (!allowNetwork) return@runCatching null // (Update 241, #76)
        if (!CloudSync.state.value.settings.connected) return@runCatching null
        val bytes = DriveClient(app).downloadBytes(e.id, maxBytes)
        if (bytes.isEmpty() || bytes.size > maxBytes) return@runCatching null
        runCatching { cache.parentFile?.mkdirs(); cache.writeBytes(bytes) }
        bytes
    }.getOrNull()

    /** Drive's "Voltcap Sync/Saves/<folder>/…": every folder, every save in it (the newest of twins). Null when Drive can't be read. */
    private fun fetch(context: Context): List<Entry>? = runCatching {
        val drive = DriveClient(context)
        val root = drive.list("root", "name='Voltcap Sync' and mimeType='$FOLDER_MIME'").firstOrNull { it.isFolder }
        val saves = root?.let { r -> drive.list(r.id, "name='Saves' and mimeType='$FOLDER_MIME'").firstOrNull { it.isFolder } }
        val out = mutableListOf<Entry>()
        if (saves != null) {
            drive.list(saves.id, "mimeType='$FOLDER_MIME'").filter { it.isFolder }.forEach { dir ->
                drive.list(dir.id).filter { !it.isFolder && !isSaveState(it.name) }
                    .groupBy { it.name }.values.mapNotNull { same -> same.maxByOrNull { it.modifiedTime } }
                    .forEach { f -> out += Entry(dir.name, f.name, f.id, f.md5, f.size, f.modifiedTime, f.device) }
            }
        }
        out.toList()
    }.getOrNull()

    private fun readCache(context: Context): Pair<Long, List<Entry>>? = runCatching {
        val f = cacheFile(context)
        if (!f.isFile) return@runCatching null
        val o = JSONObject(f.readText())
        val arr = o.getJSONArray("files")
        val list = (0 until arr.length()).map { i ->
            val e = arr.getJSONObject(i)
            Entry(
                e.getString("f"), e.getString("r"), e.getString("i"), e.optString("m").ifBlank { null },
                e.optLong("s"), e.optLong("t"), e.optString("d").ifBlank { null }
            )
        }
        o.optLong("at") to list
    }.getOrNull()

    private fun writeCache(context: Context, at: Long, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject().put("f", e.folder).put("r", e.rel).put("i", e.id).put("m", e.md5 ?: "")
                    .put("s", e.size).put("t", e.modified).put("d", e.device ?: "")
            )
        }
        val f = cacheFile(context)
        val tmp = File(f.path + ".tmp")
        tmp.writeText(JSONObject().put("at", at).put("files", arr).toString())
        tmp.renameTo(f)
    }
}
