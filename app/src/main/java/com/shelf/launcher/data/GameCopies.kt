package com.shelf.launcher.data

/**
 * Update 460b (D173, KONKR Pocket): the same game file kept twice — on the internal storage and on the SD card
 * (`/storage/emulated/0/ROMs/n64/Pokemon Snap (USA).z64` and `/storage/3361-6133/ROMs/n64/Pokemon Snap (USA).z64`) — is
 * two library keys. Its saved scans (box, back, spine, label…) were looked up and saved under one copy only, so the
 * other copy's case showed its cover and Voltcap's plain spine although the real scans were on the device.
 *
 * A copy with no scans of its own uses the scans saved for another copy of the very same file: the same path below its
 * storage and the same size — never a different region, revision, hack or dump, whose file name or size differs. Your own
 * picks for the copy (PhysicalOverrides) still go on top.
 */
object GameCopies {
    // The storage a path is on: the internal storage (/storage/emulated/<user>, /sdcard, /data/media/<user>) or a
    // removable card / USB drive (/storage/<id>, /mnt/media_rw/<id>).
    private val ROOT = Regex("""^(?:/storage/emulated/\d+|/sdcard|/data/media/\d+|/mnt/media_rw/[^/]+|/storage/(?!emulated/)[^/]+)/""", RegexOption.IGNORE_CASE)

    /**
     * What two copies of one game file share: the WHOLE path below the storage it is on, case ignored —
     * `ROMs/n64/Pokemon Snap (USA).z64` for both copies above (pure — unit-tested). Null for anything that is not a file
     * on a known storage, so nothing else ever shares. (Update 461: the whole path, not only the folder and file name —
     * two games' `CD1/game.chd` must never match.)
     */
    fun copyKey(path: String): String? {
        val p = path.replace('\\', '/').trimEnd('/')
        val file = p.substringAfterLast('/')
        if (file.isBlank() || '.' !in file) return null
        val m = ROOT.find(p) ?: return null
        val rel = p.substring(m.range.last + 1)
        if (rel.isBlank()) return null
        return rel.lowercase()
    }

    /** Another key in [keys] that is a copy of [key]'s file, or null (pure — unit-tested). */
    fun siblingOf(key: String, keys: Collection<String>): String? {
        val ck = copyKey(key) ?: return null
        return keys.firstOrNull { it != key && copyKey(it) == ck }
    }

    /** Update 461: both files exist with the same size (a different dump never shares); true when a size is unknown. */
    fun sameSize(a: Long, b: Long): Boolean = a <= 0L || b <= 0L || a == b
}
