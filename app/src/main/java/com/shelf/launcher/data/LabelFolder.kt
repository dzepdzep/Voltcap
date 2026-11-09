package com.shelf.launcher.data

/**
 * Update 460: your own cartridge / disc labels, dropped as picture files into the **Voltcap/Labels** folder on the device's
 * storage (sub-folders per console are fine): `Pokemon Brown.png`, `Pokemon - Crystal Clear (Hack).jpg`… A file is matched
 * to a game by its name — the file name without its extension against the game's ROM file name and its shown name, with
 * region tags, punctuation and case ignored.
 *
 * This is where a printable fan-made label for a ROM hack (or a label you scanned yourself) goes: Voltcap bundles and
 * downloads none — the label collections found for ROM hacks carry no licence for redistribution. A label found here is
 * worn as the game's label scan ("Disc label"): the drawn cartridge prints it on its whole sticker.
 *
 * Order (PhysicalOverrides.apply): the label you picked for the game (Artwork & details) → the real label scan
 * (ScreenScraper) → this folder → the cover. For a ROM hack this folder comes before the scraped scan, since a hack is
 * matched to its base game and that scan is the base game's label.
 *
 * Read once in the background ([index]); nothing is written.
 */
object LabelFolder {
    private val EXT = setOf("png", "jpg", "jpeg", "webp")
    @Volatile private var byName: Map<String, String> = emptyMap()
    @Volatile private var indexed = false
    /** Bumped when the folder has been read (so the games' lists are made again with what it holds). */
    val version = androidx.compose.runtime.mutableIntStateOf(0)

    private val NON = Regex("[^a-z0-9]+")
    private val TAGS = Regex("""\s*[(\[][^)\]]*[)\]]""")

    /** The name a label file or a game is matched by (pure — unit-tested). */
    fun norm(name: String): String = NON.replace(TAGS.replace(name.substringBeforeLast('.').let { if ('/' in it) it.substringAfterLast('/') else it }, "").lowercase(), "")

    /** The folders looked in. */
    // (Update 505: the isolated QA app reads only a Labels folder inside its own files — never the shared Voltcap/Labels the
    // owner's Voltcap uses — so a QA script can give a fixture game a printed test label without touching the owner's folder.)
    fun roots(): List<java.io.File> = if (QaVariant.isolatedNow)
        listOf(java.io.File(android.os.Environment.getExternalStorageDirectory(), "Android/data/com.shelf.launcher" + QaVariant.SUFFIX + "/files/Labels"))
        else listOf(java.io.File(android.os.Environment.getExternalStorageDirectory(), "Voltcap/Labels"))

    /** Builds the name → file table from [dirs] (two folder levels; pure apart from reading the folders — unit-tested). */
    fun scan(dirs: List<java.io.File>): Map<String, String> {
        val out = HashMap<String, String>()
        fun walk(d: java.io.File, depth: Int) {
            val list = runCatching { d.listFiles() }.getOrNull() ?: return
            for (f in list.sortedBy { it.name.lowercase() }) {
                if (f.isDirectory) { if (depth < 2) walk(f, depth + 1); continue }
                if (f.extension.lowercase() !in EXT || f.length() <= 0L) continue
                val k = norm(f.name)
                if (k.isNotEmpty()) out.putIfAbsent(k, f.absolutePath)
            }
        }
        for (d in dirs) if (d.isDirectory) walk(d, 0)
        return out
    }

    /** Reads the folder (off the main thread). */
    fun index() {
        byName = scan(roots())
        indexed = true
        version.intValue++
    }

    /** For tests: the table to use. */
    fun setForTest(map: Map<String, String>) { byName = map; indexed = true }

    /** The label file (as a file:// address) for the game kept under [key] (its ROM path), or null. */
    fun forKey(key: String, shownName: String? = null): String? {
        if (!indexed || byName.isEmpty()) return null
        val path = byName[norm(key)] ?: shownName?.let { byName[norm(it)] } ?: return null
        return "file://$path"
    }

    /** Whether [url] is a label from this folder (for where a picture came from). */
    fun isFolderLabel(url: String?): Boolean = url != null && roots().any { url.startsWith("file://" + it.absolutePath) }

    /**
     * [list] (a game's scraped real-media pictures) with the folder's label for [key] worn as its "Disc label": where it
     * has no label scan, or always for a ROM hack ([hack]) — whose scraped label and cartridge photo are its base game's
     * (pure — unit-tested). A label you picked yourself is applied after this and still wins.
     */
    fun merge(list: List<Pair<String, String>>, folderLabel: String?, hack: Boolean): List<Pair<String, String>> {
        if (folderLabel == null) return list
        val hasReal = list.any { it.first == "Disc label" }
        if (hasReal && !hack) return list
        val drop = if (hack) setOf("Disc label", "Disc / cartridge") else setOf("Disc label")
        return list.filter { it.first !in drop } + ("Disc label" to folderLabel)
    }
}
