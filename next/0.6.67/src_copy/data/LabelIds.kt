package com.shelf.launcher.data

/**
 * Update 459: what a generated cartridge label may print to identify a game — never an invented catalogue number.
 *
 * - [Id.code]: the real product code, read from the ROM's own header where the format keeps one (Game Boy Advance
 *   "AGB-AXVE-USA", Nintendo DS "NTR-…", Nintendo 64 "NUS-NSME-USA", newer Game Boy / Color games "CGB-AAUE-USA",
 *   Mega Drive / Genesis "GM 00001009-00"). Null when the header has none (most original Game Boy, NES, SNES games).
 * - [Id.unofficial]: "FAN-MADE" for a ROM hack, "HOMEBREW" for homebrew / aftermarket / unlicensed releases — printed
 *   instead of any code (a hack's header still carries its base game's code, which would claim to be that product).
 * - [Id.region]: "USA" / "EUR" / "JPN" from the file's region tag (or the header), null when unknown.
 *
 * Read once per session in the background ([index]); a label made before its header was read is made again
 * ([version]). Nothing is written anywhere.
 */
object LabelIds {
    data class Id(val code: String?, val unofficial: String?, val region: String?)

    private val map = java.util.concurrent.ConcurrentHashMap<String, Id>()
    /** Bumped when header codes come in, so generated labels are drawn again with them. */
    val version = androidx.compose.runtime.mutableIntStateOf(0)
    @Volatile private var indexedFor = 0

    private val NON = Regex("[^a-z0-9]+")
    private fun norm(s: String): String = NON.replace(s.lowercase(), "")

    /** The label facts for a game shown as [title] (its display name or raw name), or null when unknown. */
    fun of(title: String?): Id? = title?.let { map[norm(it)] ?: map[norm(com.shelf.launcher.ui.gameDisplayName(it))] }

    private val HOMEBREW = Regex("""[(\[](homebrew|aftermarket|unl|unlicensed|pd|public domain)[)\]]""", RegexOption.IGNORE_CASE)

    /** Hack / homebrew tag for a game called [name] from file [path] (pure). */
    fun unofficialOf(name: String, path: String?): String? = when {
        RomHacks.isModified(name, path) -> "FAN-MADE"
        HOMEBREW.containsMatchIn(name) || (path != null && HOMEBREW.containsMatchIn(path.substringAfterLast('/'))) -> "HOMEBREW"
        else -> null
    }

    /** Region from No-Intro style tags in a name or file name (pure). */
    fun regionOf(name: String): String? {
        val tags = Regex("""\(([^)]*)\)""").findAll(name).map { it.groupValues[1].lowercase() }.toList()
        for (t in tags) {
            val parts = t.split(',').map { it.trim() }
            if ("usa" in parts || "us" in parts) return "USA"
            if ("europe" in parts || "eu" in parts || "germany" in parts || "france" in parts || "uk" in parts || "spain" in parts || "italy" in parts) return "EUR"
            if ("japan" in parts || "jp" in parts) return "JPN"
            if ("world" in parts) return "USA"
        }
        return null
    }

    /** Region letter in Nintendo product codes (the code's last character) → its suffix; null when unknown (pure). */
    fun nintendoRegion(letter: Char): String? = when (letter) {
        'E' -> "USA"; 'P' -> "EUR"; 'J' -> "JPN"; 'D' -> "NOE"; 'F' -> "FRA"; 'S' -> "ESP"; 'I' -> "ITA"; 'U' -> "AUS"; 'K' -> "KOR"
        else -> null
    }

    private fun alnum4(b: ByteArray, at: Int): String? {
        if (at + 4 > b.size) return null
        val s = String(b, at, 4, Charsets.US_ASCII)
        return s.takeIf { it.all { c -> c in 'A'..'Z' || c in '0'..'9' } }
    }

    /**
     * The product code in a ROM [head] (its first 512 bytes at least) for file extension [ext], or null (pure — unit-tested).
     */
    fun codeFromHeader(ext: String, head: ByteArray): String? = runCatching {
        when (ext.lowercase()) {
            "gba" -> alnum4(head, 0xAC)?.let { c -> "AGB-$c-" + (nintendoRegion(c[3]) ?: return@runCatching null) }
            "nds" -> alnum4(head, 0x0C)?.let { c -> "NTR-$c-" + (nintendoRegion(c[3]) ?: return@runCatching null) }
            "gb", "gbc" -> {
                if (head.size < 0x150) return@runCatching null
                val cgb = head[0x143].toInt() and 0xFF
                if (cgb != 0x80 && cgb != 0xC0) return@runCatching null
                alnum4(head, 0x13F)?.let { c -> (if (cgb == 0xC0) "CGB-" else "DMG-") + c + "-" + (nintendoRegion(c[3]) ?: return@runCatching null) }
            }
            "z64", "n64", "v64" -> {
                if (head.size < 0x40) return@runCatching null
                val b = when {
                    head[0] == 0x80.toByte() && head[1] == 0x37.toByte() -> head
                    head[0] == 0x37.toByte() && head[1] == 0x80.toByte() -> ByteArray(head.size) { i -> head[i xor 1] }
                    head[0] == 0x40.toByte() && head[3] == 0x80.toByte() -> ByteArray(head.size) { i -> head[(i and 3.inv()) + 3 - (i and 3)] }
                    else -> return@runCatching null
                }
                val s = String(b, 0x3B, 4, Charsets.US_ASCII)
                if (!s.all { it in 'A'..'Z' || it in '0'..'9' }) return@runCatching null
                "NUS-$s-" + (nintendoRegion(s[3]) ?: return@runCatching null)
            }
            "md", "gen", "bin" -> {
                if (head.size < 0x18E || String(head, 0x100, 4, Charsets.US_ASCII) != "SEGA") return@runCatching null
                String(head, 0x180, 14, Charsets.US_ASCII).trim().takeIf { it.length >= 6 && it.startsWith("GM") && it.any { c -> c.isDigit() } }
            }
            else -> null
        }
    }.getOrNull()

    /** Reads what the labels need for [games], once per library, in the background. */
    suspend fun index(games: List<Game>) {
        val tag = games.size * 31 + (games.firstOrNull()?.romPath?.hashCode() ?: 0)
        if (tag == indexedFor) return
        indexedFor = tag
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            for (g in games) {
                if (g.sourceType != SourceType.ROM) continue
                val path = g.romPath
                val un = unofficialOf(g.name, path)
                val region = regionOf(path?.substringAfterLast('/') ?: g.name) ?: regionOf(g.name)
                val ext = path?.substringAfterLast('.', "")?.lowercase().orEmpty()
                val code = if (un != null || path == null || ext !in HEADER_EXT) null else readHead(path)?.let { codeFromHeader(ext, it) }
                val id = Id(code, un, region ?: code?.substringAfterLast('-')?.takeIf { it.length == 3 })
                if (id.code != null || id.unofficial != null || id.region != null) {
                    map[norm(g.name)] = id
                    map[norm(com.shelf.launcher.ui.gameDisplayName(g.name))] = id
                }
            }
            // (Once, at the end: each bump makes the cartridges on screen draw their fronts again.)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { version.intValue++ }
        }
    }

    private val HEADER_EXT = setOf("gba", "nds", "gb", "gbc", "z64", "n64", "v64", "md", "gen", "bin")

    private fun readHead(path: String): ByteArray? = runCatching {
        val f = java.io.File(path)
        if (!f.isFile || f.length() < 0x200) return@runCatching null
        java.io.RandomAccessFile(f, "r").use { r -> ByteArray(0x200).also { r.readFully(it) } }
    }.getOrNull()
}
