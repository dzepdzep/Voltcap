package com.shelf.launcher.data

import java.io.File
import java.nio.charset.Charset

/**
 * Reads PlayStation memory cards so Save Capsules can show real saves — also for games that were never
 * started from Voltcap. Nothing here writes to a card.
 *
 *  - PS1 cards: raw 128 KB images (.mcr .mcd .mc .srm .sav), DexDrive .gme and VGS/.mem headers.
 *    The directory (block 0) names each save "BASLUS-00067…"; the save's first frame holds its title.
 *  - PS2 cards: the 8 MB .ps2 file card (with or without ECC bytes) — its FAT file system is walked
 *    to each save folder and its icon.sys title — and PCSX2-family folder cards (a "Mcd001.ps2"
 *    folder holding one folder per save).
 *
 * A save's product code ("SLUS-20312") is matched to a game's disc serial (read from SYSTEM.CNF once
 * and remembered), else its title is matched to the game's name.
 */
object MemoryCards {

    /** One save on a card. [code] is the normalised product code ("SLUS20312"), [title] its own name. */
    data class CardSave(
        val card: String,
        val folder: String,
        val code: String?,
        val title: String,
        val sizeBytes: Long,
        val modified: Long,
        /** (Update 239, #112: a PS2 save's own browser icon — icon.sys and its icon file — for Save Vault's PS2 browser style.) */
        val ps2Icon: Ps2Icons.Source? = null
    )

    private val sjis: Charset? = runCatching { Charset.forName("Shift_JIS") }.getOrNull()

    /** "BASLUS-20312GAME" / "BESLES-12345" / "SLUS_203.12" → "SLUS20312" (null when there's no code). */
    fun normaliseCode(raw: String): String? {
        val m = Regex("([A-Z]{4})[-_ ]?(\\d{3})\\.?(\\d{2})").find(raw.uppercase()) ?: return null
        return m.groupValues[1] + m.groupValues[2] + m.groupValues[3]
    }

    /** Shift-JIS card titles use full-width letters: "ＦＩＮＡＬ　ＦＡＮＴＡＳＹ" → "FINAL FANTASY". */
    fun cleanTitle(bytes: ByteArray): String {
        var end = bytes.indexOf(0)
        if (end < 0) end = bytes.size
        val text = runCatching { String(bytes, 0, end, sjis ?: Charsets.ISO_8859_1) }.getOrDefault("")
        val sb = StringBuilder()
        for (c in text) {
            when {
                c.code in 0xFF01..0xFF5E -> sb.append((c.code - 0xFEE0).toChar())
                c == '　' -> sb.append(' ')
                c.code < 0x20 -> sb.append(' ')
                else -> sb.append(c)
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    // ---- PS1 ----

    private const val PS1_SIZE = 131072

    /** The 128 KB card inside a file of any of the PS1 formats, or null. */
    private fun ps1Image(bytes: ByteArray): ByteArray? {
        fun at(off: Int) = if (bytes.size >= off + PS1_SIZE && bytes[off] == 'M'.code.toByte() && bytes[off + 1] == 'C'.code.toByte())
            bytes.copyOfRange(off, off + PS1_SIZE) else null
        return at(0) ?: at(3904) /* .gme */ ?: at(64) /* .vgs/.mem */ ?: at(128)
    }

    /** What [bytes] is, by its content (update 109): 1 = a PS1 card, 2 = a PS2 card, 0 = neither. */
    fun cardType(bytes: ByteArray): Int = when {
        bytes.size >= 0x200 && String(bytes, 0, PS2_MAGIC.length, Charsets.US_ASCII) == PS2_MAGIC -> 2
        ps1Image(bytes) != null -> 1
        else -> 0
    }

    /** The saves on a card of either console, told apart by its content; null when [bytes] isn't a card. */
    fun readAny(name: String, bytes: ByteArray, modified: Long): Pair<Int, List<CardSave>>? = when (cardType(bytes)) {
        2 -> 2 to readPs2File(name, bytes, modified)
        1 -> 1 to readPs1(name, bytes, modified)
        else -> null
    }

    fun readPs1(name: String, bytes: ByteArray, modified: Long): List<CardSave> {
        val card = ps1Image(bytes) ?: return emptyList()
        val out = mutableListOf<CardSave>()
        for (slot in 1..15) {
            val f = slot * 128
            val state = card[f].toInt() and 0xFF
            if (state != 0x51) continue // first block of a save in use
            val size = u32le(card, f + 4)
            val fileName = String(card, f + 10, 20, Charsets.US_ASCII).trimEnd('\u0000', ' ')
            val block = card.copyOfRange(slot * 8192, slot * 8192 + 128)
            val title = if (block[0] == 'S'.code.toByte() && block[1] == 'C'.code.toByte())
                cleanTitle(block.copyOfRange(4, 68)) else ""
            out += CardSave(
                card = name, folder = fileName, code = normaliseCode(fileName),
                title = title.ifBlank { fileName }, sizeBytes = size, modified = modified
            )
        }
        return out
    }

    // ---- PS2 file card ----

    private const val PS2_MAGIC = "Sony PS2 Memory Card Format "

    fun readPs2File(name: String, bytes: ByteArray, modified: Long): List<CardSave> = runCatching {
        if (bytes.size < 0x200 || String(bytes, 0, PS2_MAGIC.length, Charsets.US_ASCII) != PS2_MAGIC) return emptyList()
        val pageLen = u16le(bytes, 0x28).takeIf { it in 256..4096 } ?: 512
        val pagesPerCluster = u16le(bytes, 0x2A).takeIf { it in 1..16 } ?: 2
        val allocOffset = u32le(bytes, 0x34).toInt()
        val rootCluster = u32le(bytes, 0x3C).toInt()
        val ifc = IntArray(32) { u32le(bytes, 0x50 + it * 4).toInt() }
        val clusterBytes = pageLen * pagesPerCluster
        // With ECC every page is followed by 16 spare bytes.
        val rawPage = if (bytes.size % (pageLen + 16) == 0 && bytes.size % pageLen != 0) pageLen + 16
            else if (bytes.size == 8650752) pageLen + 16 else pageLen

        fun cluster(abs: Int): ByteArray {
            val out = ByteArray(clusterBytes)
            for (p in 0 until pagesPerCluster) {
                val src = (abs.toLong() * pagesPerCluster + p) * rawPage
                if (src + pageLen > bytes.size) return out
                System.arraycopy(bytes, src.toInt(), out, p * pageLen, pageLen)
            }
            return out
        }
        val perCluster = clusterBytes / 4
        fun fatEntry(rel: Int): Int {
            val fatOffset = rel % perCluster
            val indirectIndex = rel / perCluster
            val indirectOffset = indirectIndex % perCluster
            val dblIndex = indirectIndex / perCluster
            if (dblIndex >= ifc.size) return -1
            val indirect = cluster(ifc[dblIndex])
            val fatCluster = u32le(indirect, indirectOffset * 4).toInt()
            return u32le(cluster(fatCluster), fatOffset * 4).toInt()
        }
        /** A file's / folder's data (cluster chain from [start], relative to the allocation start). */
        fun chain(start: Int, maxBytes: Int): ByteArray {
            val buf = java.io.ByteArrayOutputStream()
            var c = start
            var guard = 0
            while (c >= 0 && buf.size() < maxBytes && guard++ < 8192) {
                buf.write(cluster(c + allocOffset))
                val e = fatEntry(c)
                if (e == -1 || (e and 0x80000000.toInt()) == 0) break
                val next = e and 0x7FFFFFFF
                if (next == 0x7FFFFFFF) break
                c = next
            }
            return buf.toByteArray()
        }
        data class Entry(val mode: Int, val length: Int, val cluster: Int, val name: String, val modified: Long)
        fun entries(dirCluster: Int, count: Int): List<Entry> {
            val data = chain(dirCluster, (count + 1) * 512)
            return (0 until minOf(count, data.size / 512)).map { i ->
                val o = i * 512
                Entry(
                    mode = u16le(data, o), length = u32le(data, o + 4).toInt(), cluster = u32le(data, o + 0x10).toInt(),
                    name = String(data, o + 0x40, 32, Charsets.ISO_8859_1).substringBefore('\u0000'),
                    modified = ps2Time(data, o + 0x18)
                )
            }
        }
        // The root's own "." entry says how many entries it has.
        val root0 = entries(rootCluster, 1).firstOrNull() ?: return emptyList()
        val root = entries(rootCluster, root0.length.coerceIn(2, 512))
        root.drop(2).filter { (it.mode and 0x8000) != 0 && (it.mode and 0x0020) != 0 && !isSystemFolder(it.name) }.map { dir ->
            val files = entries(dir.cluster, dir.length.coerceIn(2, 256))
            val icon = files.firstOrNull { it.name.equals("icon.sys", true) && (it.mode and 0x8000) != 0 }
            // (Update 239, #112: icon.sys read once — for the title, and kept with the save's icon file for the PS2 browser style.)
            val sysBytes = icon?.let { f -> chain(f.cluster, 1024).takeIf { it.size >= 0x104 } }
            val title = sysBytes?.let { cleanTitle(it.copyOfRange(0xC0, 0x104)) }
            val size = files.drop(2).sumOf { it.length.toLong().coerceAtLeast(0) }
            val ps2Icon = if (sysBytes == null) Ps2Icons.Source(null) else sysBytes.let { sb ->
                runCatching {
                    val sys = sb.copyOf(minOf(sb.size, (icon?.length ?: sb.size).coerceIn(0x104, 1024)))
                    val icoName = Ps2Icons.normalIconName(sys)
                    val f = icoName?.let { n -> files.drop(2).firstOrNull { it.name.equals(n, true) && (it.mode and 0x8000) != 0 && (it.mode and 0x0020) == 0 } }
                    val ico = f?.takeIf { it.length in 20..Ps2Icons.MAX_ICON_BYTES }?.let { e ->
                        chain(e.cluster, e.length).takeIf { it.size >= e.length }?.copyOf(e.length)
                    }
                    Ps2Icons.Source(sys, ico)
                }.getOrNull()
            }
            CardSave(name, dir.name, normaliseCode(dir.name), title?.takeIf { it.isNotBlank() } ?: dir.name, size, dir.modified.takeIf { it > 0 } ?: modified, ps2Icon)
        }
    }.getOrDefault(emptyList())

    /** PS2 dates: sec, min, hour, day, month, year(u16) in Japan time (UTC+9). */
    private fun ps2Time(b: ByteArray, o: Int): Long = runCatching {
        val sec = b[o + 1].toInt() and 0xFF; val min = b[o + 2].toInt() and 0xFF; val hour = b[o + 3].toInt() and 0xFF
        val day = b[o + 4].toInt() and 0xFF; val month = b[o + 5].toInt() and 0xFF; val year = u16le(b, o + 6)
        if (year < 2000 || month !in 1..12 || day !in 1..31) return 0L
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT+9"))
        cal.clear(); cal.set(year, month - 1, day, hour, min, sec)
        cal.timeInMillis
    }.getOrDefault(0L)

    // ---- PS2 folder card (one folder per save) ----

    /**
     * [dirs]: save folder name → (icon.sys bytes or null, total size, newest file date). (Update 239, #112: [readers]: save
     * folder name → a reader for one of its files by name, so the PS2 browser style can fetch the save's icon when shown.)
     */
    fun readPs2Folder(
        name: String, dirs: Map<String, Triple<ByteArray?, Long, Long>>,
        readers: Map<String, (String) -> ByteArray?> = emptyMap()
    ): List<CardSave> =
        dirs.filterKeys { !isSystemFolder(it) }.map { (dir, info) ->
            val title = info.first?.takeIf { it.size >= 0x104 }?.let { cleanTitle(it.copyOfRange(0xC0, 0x104)) }
            CardSave(name, dir, normaliseCode(dir), title?.takeIf { it.isNotBlank() } ?: dir, info.second, info.third,
                Ps2Icons.Source(info.first, null, readers[dir]))
        }

    /** The console's own data ("BEDATA-SYSTEM": system configuration, browser data), not a game save. */
    private fun isSystemFolder(name: String) = name.uppercase().let { it.contains("DATA-SYSTEM") || it.startsWith("BXDATA") }

    // ---- Disc serials (to match saves to games) ----

    private val serialCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * The game's product code, from its file name when it carries one ("… [SLUS-20312].iso"), else from
     * SYSTEM.CNF's boot file (PS1/PS2). "" = none found. Remembered per file (size + date).
     */
    fun serialFor(context: android.content.Context, romPath: String, platform: String): String? {
        normaliseCode(romPath.substringAfterLast('/'))?.let { return it }
        val f = File(romPath)
        if (!f.isFile) return null
        val cacheKey = "$romPath|${f.length()}|${f.lastModified()}"
        serialCache[cacheKey]?.let { return it.ifEmpty { null } }
        val prefs = context.getSharedPreferences("disc_serials", android.content.Context.MODE_PRIVATE)
        prefs.getString(cacheKey, null)?.let { serialCache[cacheKey] = it; return it.ifEmpty { null } }
        val p = platform.lowercase()
        val ps2 = p == "ps2" || p.contains("playstation 2")
        val found = runCatching {
            var file = f
            if (file.extension.equals("m3u", true)) {
                file = file.readLines().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                    ?.let { File(f.parentFile, it) } ?: return@runCatching null
            }
            DiscImage.open(file)?.use { disc ->
                if (p == "psp" || p.contains("portable")) {
                    val sfo = disc.findFile("PSP_GAME\\PARAM.SFO") ?: return@use null
                    return@use SaveCapsules.sfoValue(disc.readFile(sfo, 4096), "DISC_ID")?.let { normaliseCode(it) }
                }
                val cnf = disc.findFile("SYSTEM.CNF") ?: return@use null
                val text = String(disc.readFile(cnf, 2048), Charsets.ISO_8859_1)
                val exe = RaHasher.bootExecutable(text, if (ps2) "BOOT2" else "BOOT", if (ps2) "cdrom0:" else "cdrom:")
                exe?.let { normaliseCode(it.substringAfterLast('\\')) }
            }
        }.getOrNull()
        serialCache[cacheKey] = found ?: ""
        prefs.edit().putString(cacheKey, found ?: "").apply()
        return found
    }

    /** Loose title comparison: letters and digits only, roman numerals left as they are. */
    fun titleKey(s: String): String =
        // Update 151: accents off first, so a save titled "POKéMON COLOSSEUM" matches "Pokemon Colosseum (USA)".
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .lowercase().replace(Regex("\\(.*?\\)|\\[.*?]"), " ").replace("&", " and ")
            .replace(Regex("[^a-z0-9]+"), " ").trim()

    /** True when a card save's title names this game ("FINAL FANTASY X" ~ "Final Fantasy X (USA)"). */
    fun titleMatches(saveTitle: String, gameName: String): Boolean {
        val a = titleKey(saveTitle)
        val b = titleKey(gameName)
        if (a.length < 3 || b.length < 3) return false
        if (a == b) return true
        // Save titles often add a slot or chapter after the name ("FINAL FANTASY X Ch.3 Save 01").
        // (Update 245, #74: not when what follows is a sequel's number — "FINAL FANTASY X-2" is not Final Fantasy X,
        // "Kingdom Hearts 2" not Kingdom Hearts; found by the fixture tests.)
        return (a.startsWith("$b ") && !sequelAfter(a, b)) || (b.length >= 6 && a.startsWith(b) && !a[b.length].isDigit() && a[b.length] != ' ')
    }

    /** Update 245: true when the word right after [b] in [a] is a sequel's number (2, 3…, II, III…, X). */
    internal fun sequelAfter(a: String, b: String): Boolean {
        val next = a.substring(b.length).trim().substringBefore(' ')
        return next.isNotEmpty() && (next.all { it.isDigit() } || next in SEQUEL_ROMAN)
    }

    private val SEQUEL_ROMAN = setOf("ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x", "xi", "xii", "xiii", "xiv", "xv", "xvi")

    // ---- GameCube (update 112) ----

    private const val GC_BLOCK = 0x2000
    private val cp1252: Charset = runCatching { Charset.forName("windows-1252") }.getOrDefault(Charsets.ISO_8859_1)

    private fun u16be(b: ByteArray, o: Int): Int = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
    private fun u32be(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    private fun gcText(b: ByteArray, o: Int, len: Int, japanese: Boolean): String {
        if (o < 0 || o + len > b.size) return ""
        var end = o
        while (end < o + len && b[end] != 0.toByte()) end++
        val text = runCatching { String(b, o, end - o, if (japanese) (sjis ?: cp1252) else cp1252) }.getOrDefault("")
        return text.replace(Regex("[\\u0000-\\u001F]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    /** A GameCube game code ("GZLE01") as printed in saves and disc headers: four letters/digits and a two-character maker. */
    private fun gcCode(b: ByteArray, o: Int): String? {
        if (o + 6 > b.size) return null
        val s = String(b, o, 6, Charsets.US_ASCII)
        return s.takeIf { Regex("^[A-Z0-9]{4}[A-Z0-9]{2}$").matches(it) }
    }

    /**
     * One save's directory entry (0x40 bytes at [d]) in a card image or a .gci file; [data] is where the save's blocks
     * start in [b] (the comment — the game's own title — is at data + comment offset).
     */
    private fun gcEntry(card: String, b: ByteArray, d: Int, data: Int, sizeBytes: Long, modified: Long): CardSave? {
        if (d + 0x40 > b.size) return null
        if ((0 until 4).all { b[d + it] == 0xFF.toByte() }) return null
        val code = gcCode(b, d) ?: return null
        val fileName = gcText(b, d + 0x08, 32, false)
        val commentAt = u32be(b, d + 0x3C)
        val japanese = code[3] == 'J'
        val title = if (commentAt in 0..0x7FFFFFFFL) gcText(b, (data + commentAt).toInt(), 32, japanese) else ""
        return CardSave(card, fileName.ifBlank { code }, code, title.ifBlank { fileName.ifBlank { code } }, sizeBytes, modified)
    }

    /** The save in a Dolphin .gci file (one save: its 0x40-byte entry, then its blocks), or null when it isn't one. */
    fun readGci(name: String, bytes: ByteArray, modified: Long): CardSave? = runCatching {
        if (bytes.size < 0x40 + GC_BLOCK) return null
        val blocks = u16be(bytes, 0x38)
        if (blocks <= 0 || 0x40L + blocks.toLong() * GC_BLOCK > bytes.size + GC_BLOCK) return null
        gcEntry(name, bytes, 0, 0x40, bytes.size.toLong(), modified)
    }.getOrNull()

    /** The saves on a GameCube memory card image (Dolphin's .raw), or an empty list when it isn't one. */
    fun readGcCard(name: String, bytes: ByteArray, modified: Long): List<CardSave> = runCatching {
        if (bytes.size < GC_BLOCK * 5 || bytes.size % GC_BLOCK != 0) return emptyList()
        // Two copies of the directory (blocks 1 and 2): the one updated last counts.
        fun counter(block: Int) = u16be(bytes, block * GC_BLOCK + 0x1FFA)
        val dir = if (counter(2) > counter(1) && counter(2) != 0xFFFF) 2 else 1
        val out = mutableListOf<CardSave>()
        for (i in 0 until 127) {
            val d = dir * GC_BLOCK + i * 0x40
            val first = u16be(bytes, d + 0x36)
            val count = u16be(bytes, d + 0x38)
            if (first < 5 || count <= 0 || (first + count) * GC_BLOCK > bytes.size) continue
            gcEntry(name, bytes, d, first * GC_BLOCK, count.toLong() * GC_BLOCK, modified)?.let { out += it }
        }
        out
    }.getOrDefault(emptyList())

    private val gcIdCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * A GameCube disc's game code ("GZLE01") from its header: plain .iso / .gcm / NKit (at 0), RVZ / WIA (in their own
     * header, at 0x58) and CISO (at 0x8000). Remembered per file; null when it can't be read.
     */
    private val n3dsIdCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Update 223 (#67 / #72): a 3DS game's title id, low half ("0011c500") — Citra / Azahar keep its save in
     * ".../title/00040000/<this>/data/". Read from the ROM's header: an NCSD image (.3ds / .cci) has it at 0x108, an
     * NCCH (.cxi / .app) at 0x118; else from "[000400000011C500]" in the file name (.cia). Null when unknown.
     */
    fun n3dsTitleLow(romPath: String): String? {
        n3dsIdCache[romPath]?.let { return it.ifEmpty { null } }
        val fromName = Regex("\\[?0004000[0-9A-Fa-f]([0-9A-Fa-f]{8})]?").find(romPath.substringAfterLast('/'))?.groupValues?.get(1)?.lowercase()
        val id = fromName ?: runCatching {
            val f = File(romPath)
            if (!f.isFile || f.length() < 0x200) return@runCatching null
            java.io.RandomAccessFile(f, "r").use { r ->
                val head = ByteArray(0x120)
                r.readFully(head)
                val magic = String(head, 0x100, 4, Charsets.ISO_8859_1)
                // (Update 241, #72 / #74: an installable .cia carries its title id in its TMD — read from there, so a .cia
                // without "[0004000…]" in its name still gets its own save instead of "Unassigned".)
                if (magic != "NCSD" && magic != "NCCH") return@use ciaTitleLow(r, head)
                val at = if (magic == "NCSD") 0x108 else 0x118
                // Little-endian u64: bytes at..at+3 are the low half.
                "%02x%02x%02x%02x".format(head[at + 3], head[at + 2], head[at + 1], head[at])
            }
        }.getOrNull()
        n3dsIdCache[romPath] = id ?: ""
        return id
    }

    /**
     * Update 241 (#72): a CIA's title id (low half) from its TMD. Layout (3dbrew "CIA" / "Title metadata"): header size
     * (0x2020), cert chain, ticket and TMD sizes at 0x00 / 0x08 / 0x0C / 0x10, each section 64-byte aligned; the TMD
     * starts with its signature (type 0x10003 / 0x10004 / 0x10005 → 0x200 / 0x100 / 0x3C bytes, plus padding), then the
     * header, whose title id (big-endian u64) is at 0x4C. Null when it isn't one.
     */
    private fun ciaTitleLow(r: java.io.RandomAccessFile, head: ByteArray): String? = runCatching {
        if (u32le(head, 0) != 0x2020L) return@runCatching null
        fun align(x: Long) = (x + 63L) and 63L.inv()
        val certs = u32le(head, 0x08); val ticket = u32le(head, 0x0C); val tmd = u32le(head, 0x10)
        if (tmd < 0xC4L) return@runCatching null
        val tmdAt = align(0x2020L) + align(certs) + align(ticket)
        if (tmdAt + 0x300 > r.length()) return@runCatching null
        val sig = ByteArray(4); r.seek(tmdAt); r.readFully(sig)
        val skip = when (u32be(sig, 0)) { 0x10003L -> 0x200 + 0x3C; 0x10004L -> 0x100 + 0x3C; 0x10005L -> 0x3C + 0x40; else -> return@runCatching null }
        val id = ByteArray(8); r.seek(tmdAt + 4 + skip + 0x4C); r.readFully(id)
        // 3DS titles: high half 0004xxxx.
        if (id[0] != 0.toByte() || id[1] != 4.toByte()) return@runCatching null
        "%02x%02x%02x%02x".format(id[4], id[5], id[6], id[7])
    }.getOrNull()

    fun gcGameId(romPath: String): String? {
        gcIdCache[romPath]?.let { return it.ifEmpty { null } }
        val id = runCatching {
            val f = File(romPath)
            if (!f.isFile) return@runCatching null
            java.io.RandomAccessFile(f, "r").use { r ->
                val head = ByteArray(0x60)
                r.readFully(head)
                val magic = String(head, 0, 4, Charsets.ISO_8859_1)
                when {
                    magic.startsWith("RVZ") || magic.startsWith("WIA") -> gcCode(head, 0x58)
                    magic == "CISO" -> {
                        val b = ByteArray(6); r.seek(0x8000); r.readFully(b); gcCode(b, 0)
                    }
                    // Update 151: Wii .wbfs (the disc header after the 512-byte WBFS header), for the Save Vault's Wii saves.
                    magic == "WBFS" -> {
                        val b = ByteArray(6); r.seek(0x200); r.readFully(b); gcCode(b, 0)
                    }
                    // (Update 241, #53: Dolphin's .gcz — the disc header is the start of its first (zlib) block.)
                    head[0] == 0x01.toByte() && head[1] == 0xC0.toByte() && head[2] == 0x0B.toByte() && head[3] == 0xB1.toByte() -> gczCode(r, head)
                    else -> gcCode(head, 0)
                }
            }
        }.getOrNull()
        gcIdCache[romPath] = id ?: ""
        return id
    }

    /**
     * Update 241 (#53): the game code at the start of a GCZ image (Dolphin's CompressedBlob: 32-byte header — compressed
     * size u64 at 0x08, block count u32 at 0x1C — then one u64 offset per block (top bit = stored uncompressed), one
     * u32 hash per block, then the blocks). Only the first block's first bytes are inflated.
     */
    private fun gczCode(r: java.io.RandomAccessFile, head: ByteArray): String? = runCatching {
        val blocks = u32le(head, 0x1C)
        if (blocks <= 0L || blocks > 50_000_000L) return@runCatching null
        val compressedSize = u32le(head, 0x08) or (u32le(head, 0x0C) shl 32)
        val ptr = ByteArray(16); r.seek(0x20); r.readFully(ptr, 0, if (blocks > 1) 16 else 8)
        val p0 = u32le(ptr, 0) or (u32le(ptr, 4) shl 32)
        val stored = p0 < 0L
        val off0 = p0 and Long.MAX_VALUE
        val end = if (blocks > 1) (u32le(ptr, 8) or (u32le(ptr, 12) shl 32)) and Long.MAX_VALUE else compressedSize
        val len = (end - off0).coerceIn(0L, 64L * 1024)
        if (len < 6) return@runCatching null
        val dataAt = 0x20L + blocks * 12L
        val buf = ByteArray(len.toInt()); r.seek(dataAt + off0); r.readFully(buf)
        if (stored) return@runCatching gcCode(buf, 0)
        val out = ByteArray(64)
        val inf = java.util.zip.Inflater()
        try {
            inf.setInput(buf)
            var n = 0
            while (n < 6) {
                val k = inf.inflate(out, n, out.size - n)
                if (k <= 0) break
                n += k
            }
            if (n < 6) null else gcCode(out, 0)
        } finally {
            inf.end()
        }
    }.getOrNull()

    private fun u16le(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun u32le(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or
            ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)
}
