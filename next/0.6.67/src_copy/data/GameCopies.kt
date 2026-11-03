package com.shelf.launcher.data

/**
 * Update 460b (D173, KONKR Pocket): the same game file kept twice — on the internal storage and on the SD card
 * (`/storage/emulated/0/ROMs/n64/Pokemon Snap (USA).z64` and `/storage/3361-6133/ROMs/n64/Pokemon Snap (USA).z64`) — is
 * two library keys. Its saved scans (box, back, spine, label…) were looked up and saved under one copy only, so the
 * other copy's case showed its cover and Voltcap's plain spine although the real scans were on the device.
 *
 * A copy with no scans of its own uses the scans saved for another copy of the very same file: same file name (and
 * extension) in a folder of the same name — never a different region, revision or hack, whose file name differs.
 */
object GameCopies {
    /** What two copies of one game file share: its folder's name and its file name, case ignored (pure — unit-tested). */
    fun copyKey(path: String): String? {
        val p = path.replace('\\', '/').trimEnd('/')
        val file = p.substringAfterLast('/')
        if (file.isBlank() || '.' !in file) return null
        val dir = p.substringBeforeLast('/', "").substringAfterLast('/')
        return (dir + "/" + file).lowercase()
    }

    /** Another key in [keys] that is a copy of [key]'s file, or null (pure — unit-tested). */
    fun siblingOf(key: String, keys: Collection<String>): String? {
        val ck = copyKey(key) ?: return null
        return keys.firstOrNull { it != key && copyKey(it) == ck }
    }
}
