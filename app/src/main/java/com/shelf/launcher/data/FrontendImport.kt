package com.shelf.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.File

/**
 * Brings favourites, collections and play counts over from other frontends:
 *  - ES-DE: `ES-DE/gamelists/<system>/gamelist.xml` (favourite, playcount, lastplayed) and
 *    `ES-DE/collections/custom-<name>.cfg` (one ROM path per line).
 *  - Pegasus: `metadata.pegasus.txt` / `metadata.txt` in the ROM folders (collections with
 *    `files:` lists) and `pegasus-frontend/favorites.txt` (one path per line).
 *  - Daijisho: its own playlists, when exported to `Daijisho/` as `.txt` / `.m3u` lists of ROM paths.
 *
 * Games are matched by file name (case-insensitive) against Voltcap's ROM library, so different
 * mount points (/storage/XXXX vs /sdcard) don't matter. Only adds: nothing in Voltcap is removed.
 */
object FrontendImport {
    data class Found(
        val source: String,
        val favorites: Set<String> = emptySet(),
        val collections: Map<String, Set<String>> = emptyMap(),
        /** file name → times played (ES-DE). */
        val playCounts: Map<String, Int> = emptyMap(),
        /** file name → last played, epoch ms (ES-DE). */
        val lastPlayed: Map<String, Long> = emptyMap()
    ) {
        val empty: Boolean get() = favorites.isEmpty() && collections.isEmpty() && playCounts.isEmpty() && lastPlayed.isEmpty()
    }

    data class Result(val favorites: Int, val collections: Int, val inCollections: Int, val recent: Int, val sources: List<String>)

    private val _imported = MutableSharedFlow<Result>(extraBufferCapacity = 1)
    /** The view model reloads favourites, collections and recents when an import lands. */
    val imported: SharedFlow<Result> = _imported

    private val storageRoots: List<File>
        get() = listOf(File("/storage/emulated/0")) +
            (File("/storage").listFiles()?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" }.orEmpty())

    private fun fileKey(path: String): String = path.replace('\\', '/').substringAfterLast('/').trim().lowercase()

    /** Everything found on the device, per frontend (blocking: reads files). */
    fun scan(romRoots: List<String>): List<Found> = listOfNotNull(esde(), pegasus(romRoots), daijisho()).filter { !it.empty }

    // ---- ES-DE ----

    private fun esde(): Found? {
        val roots = storageRoots.flatMap { listOf(File(it, "ES-DE"), File(it, "Android/data/org.es_de.frontend/files/ES-DE")) }
            .filter { it.isDirectory }
        if (roots.isEmpty()) return null
        val favorites = HashSet<String>()
        val counts = HashMap<String, Int>()
        val last = HashMap<String, Long>()
        val collections = HashMap<String, MutableSet<String>>()
        val gameRe = Regex("<game>(.*?)</game>", RegexOption.DOT_MATCHES_ALL)
        fun tag(block: String, name: String) = Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(block)?.groupValues?.get(1)?.trim()
        roots.forEach { root ->
            File(root, "gamelists").walkTopDown().maxDepth(2).filter { it.name == "gamelist.xml" }.forEach { xml ->
                val text = runCatching { xml.readText() }.getOrNull() ?: return@forEach
                gameRe.findAll(text).forEach { m ->
                    val block = m.groupValues[1]
                    val path = tag(block, "path")?.let(::unescape) ?: return@forEach
                    val key = fileKey(path)
                    if (tag(block, "favorite") == "true") favorites += key
                    tag(block, "playcount")?.toIntOrNull()?.takeIf { it > 0 }?.let { counts[key] = it }
                    tag(block, "lastplayed")?.let(::esdeTime)?.let { last[key] = it }
                }
            }
            File(root, "collections").listFiles()?.filter { it.name.startsWith("custom-") && it.name.endsWith(".cfg") }?.forEach { cfg ->
                val name = cfg.name.removePrefix("custom-").removeSuffix(".cfg").trim()
                val keys = runCatching { cfg.readLines() }.getOrNull().orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.map(::fileKey)
                if (name.isNotEmpty() && keys.isNotEmpty()) collections.getOrPut(name) { HashSet() } += keys
            }
        }
        return Found("ES-DE", favorites, collections, counts, last)
    }

    /** ES-DE writes 20240131T204512. */
    private fun esdeTime(s: String): Long? = runCatching {
        java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss", java.util.Locale.US).parse(s)?.time
    }.getOrNull()

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&apos;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")

    // ---- Pegasus ----

    private fun pegasus(romRoots: List<String>): Found? {
        val favorites = HashSet<String>()
        val collections = HashMap<String, MutableSet<String>>()
        storageRoots.map { File(it, "pegasus-frontend/favorites.txt") }.filter { it.isFile }.forEach { f ->
            runCatching { f.readLines() }.getOrNull().orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.forEach { favorites += fileKey(it) }
        }
        // metadata files sit in the ROM folders (one per system folder).
        val metas = romRoots.map(::File).filter { it.isDirectory }.flatMap { root ->
            root.walkTopDown().maxDepth(2).filter { it.isFile && (it.name == "metadata.pegasus.txt" || it.name == "metadata.txt") }.toList()
        }
        metas.forEach { meta ->
            var collection: String? = null
            var inFiles = false
            runCatching { meta.readLines() }.getOrNull().orEmpty().forEach { raw ->
                val line = raw.trimEnd()
                when {
                    line.startsWith("collection:") -> { collection = line.substringAfter(':').trim(); inFiles = false }
                    line.startsWith("game:") -> { collection = null; inFiles = false }
                    line.startsWith("files:") || line.startsWith("file:") -> {
                        inFiles = collection != null
                        line.substringAfter(':').trim().takeIf { it.isNotEmpty() && collection != null }?.let {
                            collections.getOrPut(collection!!) { HashSet() } += fileKey(it)
                        }
                    }
                    inFiles && (raw.startsWith(" ") || raw.startsWith("\t")) && line.isNotBlank() ->
                        collections.getOrPut(collection!!) { HashSet() } += fileKey(line.trim())
                    line.contains(':') -> inFiles = false
                }
            }
        }
        return Found("Pegasus", favorites, collections)
    }

    // ---- Daijisho ----

    private fun daijisho(): Found? {
        val dirs = storageRoots.map { File(it, "Daijisho") }.filter { it.isDirectory }
        if (dirs.isEmpty()) return null
        val collections = HashMap<String, MutableSet<String>>()
        dirs.forEach { d ->
            d.walkTopDown().maxDepth(3).filter { it.isFile && (it.extension == "txt" || it.extension == "m3u") }.forEach { f ->
                val keys = runCatching { f.readLines() }.getOrNull().orEmpty().map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('.') }.map(::fileKey)
                if (keys.isNotEmpty()) collections.getOrPut(f.nameWithoutExtension) { HashSet() } += keys
            }
        }
        return Found("Daijisho", collections = collections)
    }

    // ---- applying ----

    /** Adds what was found to Voltcap's own stores. [games] = the current library. Blocking. */
    fun apply(context: Context, found: List<Found>, games: List<Game>): Result {
        val byFile = games.filter { it.romPath != null }.groupBy { fileKey(it.romPath!!) }
        fun keysFor(file: String) = byFile[file].orEmpty().map { it.stableKey }

        val favStore = FavoritesStore(context)
        val newFavs = found.flatMap { it.favorites }.flatMap(::keysFor).toSet() - favStore.loadAll()
        if (newFavs.isNotEmpty()) favStore.addAll(newFavs)

        val colStore = CollectionsStore(context)
        val user = colStore.loadUser().toMutableMap()
        var placed = 0
        var made = 0
        found.forEach { f ->
            f.collections.forEach { (name, files) ->
                val keys = files.flatMap(::keysFor)
                if (keys.isEmpty()) return@forEach
                val clean = name.replace(",", " ").take(40)
                val before = user[clean].orEmpty()
                if (before.isEmpty()) made++
                val merged = before + keys
                placed += merged.size - before.size
                user[clean] = merged
            }
        }
        if (placed > 0) colStore.saveUser(user)

        // Last played from ES-DE fills Voltcap's "Continue playing" where Voltcap has nothing newer.
        val recents = RecentlyPlayedStore(context)
        var recent = 0
        found.flatMap { it.lastPlayed.entries }.forEach { (file, at) ->
            keysFor(file).forEach { key -> if (recents.recordIfNewer(key, at)) recent++ }
        }
        val result = Result(newFavs.size, made, placed, recent, found.map { it.source })
        _imported.tryEmit(result)
        return result
    }
}
