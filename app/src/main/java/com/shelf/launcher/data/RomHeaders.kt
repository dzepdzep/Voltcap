package com.shelf.launcher.data

import android.content.Context
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Update 300: what a ROM's own header says about the physical cartridge it came on — read once, off the main thread,
 * a few hundred bytes only, and kept (with the file's size and date, so a changed file is read again).
 *
 * - Game Boy / Game Boy Color: the CGB flag at 0x0143 (Pan Docs, "The Cartridge Header"): 0x80 = enhanced for the Game
 *   Boy Color and still playable on a Game Boy (the black dual-mode Game Pak), 0xC0 = Game Boy Color only (the clear
 *   one), anything else = an original Game Boy game. Trusted only when the header checksum at 0x014D matches.
 *   https://gbdev.io/pandocs/The_Cartridge_Header.html
 * - Nintendo DS: the unit code at 0x012 (00h = DS, 02h = DS + DSi, 03h = DSi only — the white DSi-exclusive card).
 *   Trusted only when the header CRC-16 at 0x15E matches. https://dsibrew.org/wiki/DSi_cartridge_header
 *   A DSi-only title whose title ID (0x230) is in the DSiWare category (high word 00030004 — 00030000 is a game card)
 *   was a download, never a card: it isn't given the white card (seen on the Thor: Cave Story, Dragon Quest Wars).
 *
 * The header describes the software, not the plastic: verified editions (MediaEditions) and your own picks still win.
 * It never changes the game's console, emulator, launch, saves or identity — it's only asked which shell to draw.
 * Plain files, content addresses and .zip archives are read; .7z (and anything unreadable) stays unknown, and the
 * earlier rules (the file name's "(GB Compatible)" tag) apply.
 */
object RomHeaders {
    enum class Gb { DMG, CGB_DUAL, CGB_ONLY }
    enum class Nds { NDS, DSI_ENHANCED, DSI_ONLY, DSIWARE }

    /** The consoles whose headers are read. */
    private val GB_PLATFORMS = setOf("GB", "GBC")
    private val NDS_PLATFORMS = setOf("NDS")
    fun reads(platform: String?): Boolean = platform in GB_PLATFORMS || platform in NDS_PLATFORMS

    // ---- the header itself (pure; unit-tested) ----

    /** The Game Boy header's verdict, or null when [h] is too short or its checksum doesn't match. */
    fun gbOf(h: ByteArray): Gb? {
        if (h.size < 0x150) return null
        var x = 0
        for (i in 0x134..0x14C) x = (x - (h[i].toInt() and 0xFF) - 1) and 0xFF
        if (x != (h[0x14D].toInt() and 0xFF)) return null
        return when (h[0x143].toInt() and 0xFF) {
            0x80 -> Gb.CGB_DUAL
            0xC0 -> Gb.CGB_ONLY
            else -> Gb.DMG
        }
    }

    /** The DS header's unit code, or null when [h] is too short, its CRC doesn't match or the code is unknown. */
    fun ndsOf(h: ByteArray): Nds? {
        if (h.size < 0x160) return null
        val want = (h[0x15E].toInt() and 0xFF) or ((h[0x15F].toInt() and 0xFF) shl 8)
        if (crc16(h, 0x15E) != want) return null
        return when (h[0x12].toInt() and 0xFF) {
            0x00 -> Nds.NDS
            0x02 -> Nds.DSI_ENHANCED
            0x03 -> if (isDsiWare(h)) Nds.DSIWARE else Nds.DSI_ONLY
            else -> null
        }
    }

    /** The title ID's category at 0x234 (little-endian high word) is 00030004: DSiWare. */
    private fun isDsiWare(h: ByteArray): Boolean {
        if (h.size < 0x238) return false
        val hi = (h[0x234].toInt() and 0xFF) or ((h[0x235].toInt() and 0xFF) shl 8) or ((h[0x236].toInt() and 0xFF) shl 16) or ((h[0x237].toInt() and 0xFF) shl 24)
        return hi == 0x00030004
    }

    /** CRC-16 (poly 0xA001 reflected, start 0xFFFF) of the first [len] bytes — the DS header's own. */
    fun crc16(b: ByteArray, len: Int): Int {
        var crc = 0xFFFF
        for (i in 0 until len) {
            crc = crc xor (b[i].toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        return crc and 0xFFFF
    }

    // ---- kept answers ----

    @Volatile private var app: Context? = null
    private val prefs by lazy { app?.getSharedPreferences("rom_headers", Context.MODE_PRIVATE) }
    /** path → "size|modified|code" (code: g0 / g8 / gC, n0 / n2 / n3 / nw, x = unknown). */
    private val known = ConcurrentHashMap<String, String>()
    private val checked: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val done: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "rom-headers").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }

    /** Bumped when an answer changes (gathered: one bump per burst), so the cartridges drawn are looked up again. */
    val version = androidx.compose.runtime.mutableIntStateOf(0)
    private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private val bump = Runnable { version.intValue++ }

    /** Once, at start (the saved answers are read off the main thread). */
    fun init(context: Context) {
        if (app != null) return
        app = context.applicationContext
        worker.execute {
            runCatching { prefs?.all?.forEach { (k, v) -> (v as? String)?.let { known.putIfAbsent(k, it) } } }
        }
    }

    /** The Game Boy verdict for [path] when it's known; else null, and it's read in the background. */
    fun gb(path: String?, platform: String?): Gb? {
        if (path == null || platform !in GB_PLATFORMS) return null
        version.intValue // (read: a screen drawing this cartridge is redrawn when the answer arrives)
        return when (codeOf(path)) { "g0" -> Gb.DMG; "g8" -> Gb.CGB_DUAL; "gC" -> Gb.CGB_ONLY; else -> null }
    }

    /** The DS verdict for [path] when it's known; else null, and it's read in the background. */
    fun nds(path: String?, platform: String?): Nds? {
        if (path == null || platform !in NDS_PLATFORMS) return null
        version.intValue
        return when (codeOf(path)) { "n0" -> Nds.NDS; "n2" -> Nds.DSI_ENHANCED; "n3" -> Nds.DSI_ONLY; "nw" -> Nds.DSIWARE; else -> null }
    }

    /** True while [path]'s header hasn't been looked at yet this run (its answer may still change). */
    fun pending(path: String?): Boolean = path != null && path !in done

    private fun codeOf(path: String): String? {
        val k = known[path]
        // (Looked at again once per run in the background: a changed file — new size or date — is read again.)
        if (checked.add(path)) worker.execute {
            // (Before init: tried again on the next ask, not marked as looked at.)
            if (app == null) { checked.remove(path); return@execute }
            try { refresh(path, shownNothing = k == null) } finally { done += path }
        }
        return k?.substringAfterLast('|')
    }

    /** [shownNothing]: the screen asked before an answer was there — it's told whatever is found (even an old one). */
    private fun refresh(path: String, shownNothing: Boolean) {
        val c = app ?: return
        // (Read here, after the saved answers were loaded — the same worker, in order.)
        val had = known[path]
        val stamp = runCatching { stampOf(c, path) }.getOrNull() ?: return
        // (An unreadable one is tried again once a run: a card that wasn't mounted, a permission granted since.)
        if (had != null && had.substringBeforeLast('|') == stamp && !had.endsWith("|x")) {
            if (shownNothing) { main.removeCallbacks(bump); main.postDelayed(bump, 400) }
            return
        }
        val code = runCatching { read(c, path) }.getOrNull() ?: "x"
        val v = "$stamp|$code"
        known[path] = v
        runCatching { prefs?.edit()?.putString(path, v)?.apply() }
        if (shownNothing || had?.substringAfterLast('|') != code) { main.removeCallbacks(bump); main.postDelayed(bump, 400) }
    }

    /** "size|modified" of the file (or content document). */
    private fun stampOf(c: Context, path: String): String? {
        if (path.startsWith("content://")) {
            val uri = android.net.Uri.parse(path)
            c.contentResolver.query(uri, arrayOf(android.provider.DocumentsContract.Document.COLUMN_SIZE, android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { cur ->
                if (cur.moveToFirst()) return "${cur.getLong(0)}|${cur.getLong(1)}"
            }
            return null
        }
        val f = File(path)
        return if (f.isFile) "${f.length()}|${f.lastModified()}" else null
    }

    /** The header's code, or null when it can't be read (no file, .7z, too short, a checksum that doesn't match). */
    private fun read(c: Context, path: String): String? {
        val lower = path.lowercase()
        val head = when {
            lower.endsWith(".zip") -> zipHead(c, path)
            lower.endsWith(".7z") -> null
            else -> open(c, path)?.use { readUpTo(it, HEAD) }
        } ?: return null
        gbOf(head)?.let { return when (it) { Gb.DMG -> "g0"; Gb.CGB_DUAL -> "g8"; Gb.CGB_ONLY -> "gC" } }
        ndsOf(head)?.let { return when (it) { Nds.NDS -> "n0"; Nds.DSI_ENHANCED -> "n2"; Nds.DSI_ONLY -> "n3"; Nds.DSIWARE -> "nw" } }
        return null
    }

    private fun open(c: Context, path: String): InputStream? =
        if (path.startsWith("content://")) c.contentResolver.openInputStream(android.net.Uri.parse(path)) else File(path).takeIf { it.isFile }?.inputStream()

    /** How much of a ROM is read: the Game Boy header ends at 0x150, the DS one's title ID at 0x238. */
    private const val HEAD = 0x240

    /** The first ROM inside a .zip (its first [HEAD] bytes only). */
    private fun zipHead(c: Context, path: String): ByteArray? {
        val exts = setOf("gb", "gbc", "sgb", "cgb", "nds", "dsi")
        open(c, path)?.use { raw ->
            java.util.zip.ZipInputStream(raw.buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (!e.isDirectory && e.name.substringAfterLast('.', "").lowercase() in exts) return readUpTo(z, HEAD)
                }
            }
        }
        return null
    }

    private fun readUpTo(input: InputStream, n: Int): ByteArray {
        val out = ByteArray(n)
        var got = 0
        while (got < n) {
            val r = input.read(out, got, n - got)
            if (r <= 0) break
            got += r
        }
        return if (got == n) out else out.copyOf(got)
    }
}
