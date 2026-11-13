package com.shelf.launcher.data

/**
 * Update 529 (save sync P0, findings 3 / 4): when a remembered hash of a save may be reused instead of reading the file again.
 *
 * Before, a hash was reused whenever the file's size and date were the same as when it was hashed. A save is usually the same
 * size every time (SRAM, memory cards, PSP / Dolphin files), so the date alone decided - and dates are not reliable:
 *  - a shared (SAF) folder whose provider reports no date gives 0 every time: every later save looked unchanged, forever;
 *  - FAT / exFAT cards keep dates in 2 s steps, many providers in whole seconds: a save written twice within one step kept
 *    the date of the first write, and the second write was never seen;
 *  - an emulator that writes a temporary file and renames it can carry an older date over.
 *
 * The rule now (the "racy clean" rule git uses for its index): a remembered hash is trusted only when the file has a real date
 * ([modified] > 0), size and date are unchanged, and that date lies clearly - [MARGIN_MS] - before the moment the hash was
 * taken ([hashedAt]). Then any later write must have moved the date, even on coarse storage. Otherwise the file is read again.
 * Saves are small, so the extra reads cost little; files with no date at all are read every time they are looked at (that is
 * the only way to see their changes).
 */
internal object SaveChangeDetect {
    /** More than the coarsest date step seen on handheld storage (FAT's 2 s) plus clock rounding. */
    const val MARGIN_MS = 3_000L

    fun trusted(cachedSize: Long, cachedModified: Long, hashedAt: Long, size: Long, modified: Long): Boolean =
        modified > 0L && hashedAt > 0L && cachedSize == size && cachedModified == modified && modified <= hashedAt - MARGIN_MS

    /**
     * A save's state as the watcher compares it between looks: size, date AND content hash, so a rewrite that keeps both size
     * and date (same-size save, coarse or missing date) still counts as a new state.
     */
    fun signature(folder: String, path: String, size: Long, modified: Long, md5: String): String = "$folder/$path:$size:$modified:$md5"
}
