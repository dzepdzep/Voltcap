package com.shelf.launcher.data

import java.io.File

/**
 * Your own screenshots per game (details window › Your screenshots): the
 * emulators' screenshot folders, matched by the ROM's file name or the
 * game's name — RetroArch saves them as "<ROM name>-YYMMDD-HHMMSS.png".
 * Emulators that name them by game ID (PPSSPP, Dolphin) only match when the
 * name is in the file. Extra folders: Settings › Library › Your screenshots.
 */
object Screenshots {
    val DEFAULT_FOLDERS = listOf(
        "RetroArch/screenshots", "PPSSPP/PSP/SCREENSHOT", "PSP/SCREENSHOT",
        "ARMSX2/snaps", "NetherSX2/snaps", "AetherSX2/snaps", "DuckStation/screenshots",
        "dolphin-emu/ScreenShots", "DraStic/screenshots"
    )
    private const val STORAGE = "/storage/emulated/0"
    private val IMAGE = setOf("png", "jpg", "jpeg", "webp")

    private data class Listing(val at: Long, val files: List<File>)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Listing>()

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    /** Every image under [dir] (two levels deep), listed at most once a minute. */
    private fun images(dir: File): List<File> {
        val now = System.currentTimeMillis()
        cache[dir.path]?.let { if (now - it.at < 60_000) return it.files }
        val files = if (!dir.isDirectory) emptyList() else dir.walkTopDown().maxDepth(3)
            .filter { it.isFile && it.extension.lowercase() in IMAGE }.toList()
        cache[dir.path] = Listing(now, files)
        return files
    }

    /** Newest first. Blocking — call off the main thread. */
    fun find(game: Game, extraFolders: Collection<String>): List<File> {
        val stem = game.romPath?.substringAfterLast('/')?.substringBeforeLast('.')?.let(::norm)
        val title = norm(com.shelf.launcher.ui.stripRegionTags(game.name))
        if ((stem == null || stem.length < 3) && title.length < 3) return emptyList()
        val dirs = DEFAULT_FOLDERS.map { File(STORAGE, it) } + extraFolders.map { File(it) }
        return dirs.distinctBy { it.path.lowercase() }.flatMap { images(it) }
            .filter { f ->
                val n = norm(f.nameWithoutExtension)
                (stem != null && stem.length >= 3 && n.startsWith(stem)) || (title.length >= 4 && n.startsWith(title))
            }
            .distinctBy { it.path }
            .sortedByDescending { it.lastModified() }
            .take(60)
    }
}
