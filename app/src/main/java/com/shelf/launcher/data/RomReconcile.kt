package com.shelf.launcher.data

import java.io.File

/**
 * 681r (owner, 17 Nov - "I delete the only game of a console folder, rescan, and it is still listed"): what a finished ROM scan
 * makes of the library. One place, pure, tested (RescanPrune681rTest).
 *
 * - A game the scan read the place of and did not find is gone: it leaves every list (its console, All games, Mix, search,
 *   favourites' view, the home layouts' lists), and a console left with no games leaves the category selector - categories
 *   are made from the games. Its saves, history, achievements, art, metadata, favourite mark and settings are NOT touched (all
 *   keyed by its path - the same path brings them all back when the game is put back).
 * - A game under a place the scan could NOT read - a ROM folder whose storage is not there (an SD card out, a volume not
 *   mounted), a folder whose listing is refused (a permission taken back) - is kept as it was: unreadable is never deleted.
 * - A scan that read nothing at all is no verdict ([apply] null): the saved list stays.
 * - An automatic scan (at start, on resume) that found no games anywhere is no verdict either: storage often isn't ready
 *   right after the device starts. A rescan you start is - when every folder was read, an empty result empties the list.
 */
object RomReconcile {
    /** The ROM games after [scan] over [roots]; null: leave the list as it is. */
    fun apply(previous: List<Game>, scan: RomScanner.ScanResult, roots: List<String>, explicit: Boolean): List<Game>? {
        if (roots.isNotEmpty() && scan.unreadable.size >= roots.size) return null // (nothing could be read)
        if (scan.games.isEmpty() && !explicit) return null
        val shielded = (scan.unreadable + scan.failedDirs).map { norm(it) }.filter { it.isNotEmpty() }
        val found = scan.games.mapTo(HashSet()) { it.stableKey }
        val kept = if (shielded.isEmpty()) emptyList() else previous.filter { g ->
            g.stableKey !in found && g.romPath?.let { p -> under(norm(p), shielded) } == true
        }
        return scan.games + kept
    }

    /** A game's file / folder path in one spelling: separators, no trailing slash, lower case (Android storage ignores case). */
    private fun norm(p: String): String = runCatching { File(p).absolutePath }.getOrDefault(p).replace('\\', '/').trimEnd('/').lowercase()

    private fun under(path: String, dirs: List<String>): Boolean = dirs.any { d -> path == d || path.startsWith("$d/") }

    /**
     * The console category to show after the library changed: a console with no games left is no category any more, so its
     * selection falls back to All. Collections (yours, franchises) and the Android games list keep their own rules; your own
     * collections are never removed by this (CollectionsStore keeps them, empty).
     */
    fun platformFilterAfter(filter: String?, available: List<String>, isCollection: Boolean, isAndroidGames: Boolean): String? =
        if (filter == null || isCollection || isAndroidGames || filter in available) filter else null
}
