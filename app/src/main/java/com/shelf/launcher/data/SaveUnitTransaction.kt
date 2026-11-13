package com.shelf.launcher.data

import java.io.File

/**
 * Update 529 (save sync P0): one multi-file save (a PS2 folder card, a 3DS / Wii title folder, a PSP save folder) replaced by
 * Drive's version as ONE change - all of its files or none. Used by [CloudSync] for every such download (sync and conflict
 * choices). Pure JVM: the steps are passed in, so the order and the failure handling are unit-tested with fakes.
 *
 *  1. every file is fetched and checked against Drive's checksum - nothing on this device changes yet;
 *  2. every file about to be replaced is backed up (each backup verified; one that can't be made stops everything);
 *  3. the files are written; if one write fails, the files already written are put back from their backups and the error is
 *     passed on - the save is then exactly what it was (a file that didn't exist before stays: nothing is ever deleted).
 *
 * The fetched temporary files are always removed.
 */
internal object SaveUnitTransaction {
    class Item(val rel: String, val expectMd5: String?, val modified: Long)

    class Incomplete(message: String) : Exception(message)

    /**
     * Returns rel → md5 of every file written, or null when [fetch] returned null for one (the save changed here meanwhile:
     * nothing was touched). Throws when a download doesn't match, a backup fails or a write fails (after putting back).
     */
    fun <B> run(
        items: List<Item>,
        fetch: (Item) -> File?,
        md5: (File) -> String,
        backup: (Item) -> B?,
        restore: (Item, B) -> Unit,
        write: (Item, File) -> Unit
    ): Map<String, String>? {
        val fetched = linkedMapOf<String, File>()
        try {
            val sums = linkedMapOf<String, String>()
            for (item in items) {
                val f = fetch(item) ?: return null
                fetched[item.rel] = f
                val got = md5(f)
                if (item.expectMd5 != null && !item.expectMd5.equals(got, ignoreCase = true)) {
                    throw Incomplete("Download of ${item.rel} was incomplete - the save on this device was left untouched.")
                }
                sums[item.rel] = got
            }
            val backups = linkedMapOf<String, B>()
            for (item in items) backup(item)?.let { backups[item.rel] = it }
            val written = mutableListOf<Item>()
            try {
                for (item in items) {
                    write(item, fetched.getValue(item.rel))
                    written += item
                }
            } catch (e: Exception) {
                for (item in written.asReversed()) backups[item.rel]?.let { b -> runCatching { restore(item, b) } }
                throw e
            }
            return sums
        } finally {
            fetched.values.forEach { it.delete() }
        }
    }
}
