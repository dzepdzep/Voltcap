package com.shelf.launcher.data

import android.content.Context

/*
 * (Update 239, #112: the PS2 memory-card browser's own save icons, read from the saves themselves.)
 *
 * Every save folder on a PS2 memory card carries the picture the console's browser shows for it:
 *  - icon.sys (964 bytes, "PS2D"): the save's title (Shift-JIS, two lines — the second starts at the byte offset at 0x06),
 *    the background's four corner colours and transparency, light vectors / colours, and the file names of its three
 *    icons (normal / copy / delete) at 0x104, 0x144 and 0x184.
 *  - the icon itself (.ico, PS2 3D icon): magic 0x00010000, the number of animation shapes, the texture type, the vertex
 *    count; per vertex one int16 x, y, z, w per shape, a normal, a UV pair and an RGBA colour; then the animation frames;
 *    then a 128 × 128 texture of 16-bit A1B5G5R5 pixels (RLE-compressed when the texture type has bit 3 set).
 * Nothing here writes anything to a save; all reading is bounds-checked and size-limited, a damaged file only means
 * "no save icon".
 */
object Ps2Icons {

    /** (Update 239, #112: biggest icon file read; real ones are 10–120 KB.) */
    const val MAX_ICON_BYTES = 400_000
    private const val MAX_VERTICES = 30_000
    private const val TEX = 128

    /**
     * (Update 239, #112: where one save's icon comes from — its icon.sys bytes, the normal icon's bytes when they were
     * read with the card (a .ps2 image), or a reader for a file of the save's folder by name (folder cards, read only when
     * the PS2 browser style shows it).)
     */
    class Source(
        val iconSys: ByteArray?,
        val icon: ByteArray? = null,
        val readFile: ((String) -> ByteArray?)? = null
    )

    /** (Update 239, #112: what icon.sys says.) */
    class IconSys(
        val line1: String,
        val line2: String,
        /** Background corner colours (ARGB, opaque): top-left, top-right, bottom-left, bottom-right. */
        val corners: IntArray,
        /** The background's opacity, 0..1 (icon.sys 0x0C: 0..0x80). */
        val bgAlpha: Float,
        val normalIcon: String,
        val copyIcon: String,
        val deleteIcon: String
    )

    /** (Update 239, #112: a decoded icon — shape 0 only, triangles.) */
    class Icon(
        val shapes: Int,
        val textureType: Int,
        val vertexCount: Int,
        /** x, y, z per vertex (fixed point / 4096); y points down, as on the console. */
        val positions: FloatArray,
        /** u, v per vertex, 0..1. */
        val uvs: FloatArray,
        /** ARGB per vertex. */
        val colors: IntArray,
        /** 128 × 128 ARGB, or null for an icon without a texture (drawn in its vertex colours). */
        val texture: IntArray?,
        val frames: Int
    )

    /** (Update 239, #112: a save's look-up result: [icon] null = no usable save icon, the fallback shows.) */
    class Parsed(val sys: IconSys?, val icon: Icon?)

    // ---- Registry (filled by SaveCapsules as it reads cards) and caches ----

    private val sources = java.util.concurrent.ConcurrentHashMap<String, Source>()
    private val parsed = android.util.LruCache<String, Parsed>(96)

    /** (Update 239, #112: goes up when new saves' sources arrive, so tiles that found nothing look again.) */
    val version = androidx.compose.runtime.mutableIntStateOf(0)

    /** (Update 239, #112: a save's identity — the Save Vault's "card · folder" text, its size and date.) */
    fun idOf(where: String, sizeBytes: Long, modified: Long): String = "$where|$sizeBytes|$modified"

    fun register(id: String, source: Source) {
        if (sources.size > 4000) sources.clear()
        sources[id] = source
    }

    fun bump() { version.intValue = version.intValue + 1 }

    /** (Update 239, #112: the look-up already done, without any reading.) */
    fun cached(id: String): Parsed? = parsed.get(id)

    /**
     * (Update 239, #112: the icon of save [id]: memory, then the small disk cache, then its source. Blocking (files,
     * maybe Drive): call it off the main thread. Null = nothing known about this save yet (no source registered).)
     */
    fun load(context: Context, id: String): Parsed? {
        parsed.get(id)?.let { return it }
        readDisk(context, id)?.let { (sys, ico) ->
            val p = build(sys, ico)
            parsed.put(id, p)
            return p
        }
        val src = sources[id] ?: return null
        val sysBytes = src.iconSys?.takeIf { it.size <= 4096 }
        val sys = sysBytes?.let { parseIconSys(it) }
        val name = sys?.normalIcon?.let { safeName(it) }
        val ico = src.icon ?: name?.let { n -> runCatching { src.readFile?.invoke(n) }.getOrNull() }
        val icoOk = ico?.takeIf { it.size in 20..MAX_ICON_BYTES }
        val p = Parsed(sys, icoOk?.let { runCatching { parseIco(it) }.getOrNull() })
        parsed.put(id, p)
        // Kept on disk when it's final: an icon, or a save that certainly has none (a reader that found nothing may have
        // been offline — asked again next time Voltcap starts).
        if (icoOk != null || sys == null || name == null) runCatching { writeDisk(context, id, sysBytes, icoOk) }
        return p
    }

    private fun build(sys: ByteArray?, ico: ByteArray?): Parsed {
        val s = sys?.let { runCatching { parseIconSys(it) }.getOrNull() }
        return Parsed(s, ico?.let { runCatching { parseIco(it) }.getOrNull() })
    }

    /** (Update 239, #112: an icon file name from icon.sys, only when it is a plain name — never a path.) */
    fun safeName(name: String): String? = name.trim().takeIf {
        it.isNotEmpty() && it.length <= 64 && !it.contains('/') && !it.contains('\\') && it != "." && it != ".."
    }

    /** (Update 239, #112: the normal icon's file name from raw icon.sys bytes, or null.) */
    fun normalIconName(iconSys: ByteArray): String? =
        runCatching { parseIconSys(iconSys)?.normalIcon?.let { safeName(it) } }.getOrNull()

    // ---- icon.sys ----

    fun parseIconSys(b: ByteArray): IconSys? {
        if (b.size < 0x1C4) return null
        if (b[0] != 'P'.code.toByte() || b[1] != 'S'.code.toByte() || b[2] != '2'.code.toByte() || b[3] != 'D'.code.toByte()) return null
        val title = b.copyOfRange(0xC0, 0x104)
        var end = 0
        while (end < title.size && title[end] != 0.toByte()) end++
        val br = u16(b, 0x06)
        val (l1, l2) = if (br in 1 until end) {
            MemoryCards.cleanTitle(title.copyOfRange(0, br)) to MemoryCards.cleanTitle(title.copyOfRange(br, end))
        } else MemoryCards.cleanTitle(title.copyOfRange(0, end)) to ""
        val corners = IntArray(4) { i ->
            val o = 0x10 + i * 16
            val r = u32(b, o).coerceIn(0, 255).toInt()
            val g = u32(b, o + 4).coerceIn(0, 255).toInt()
            val bl = u32(b, o + 8).coerceIn(0, 255).toInt()
            (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
        }
        val alpha = (u32(b, 0x0C).coerceIn(0, 0x80).toFloat() / 0x80).coerceIn(0f, 1f)
        fun name(o: Int): String {
            var e = o
            while (e < o + 64 && e < b.size && b[e] != 0.toByte()) e++
            return String(b, o, e - o, Charsets.ISO_8859_1).trim()
        }
        return IconSys(l1, l2, corners, alpha, name(0x104), name(0x144), name(0x184))
    }

    // ---- .ico ----

    fun parseIco(b: ByteArray): Icon? {
        if (b.size < 20 || u32(b, 0) != 0x00010000L) return null
        val shapes = u32(b, 4).toInt().takeIf { it in 1..64 } ?: return null
        val texType = u32(b, 8).toInt()
        var count = u32(b, 16).takeIf { it in 3..MAX_VERTICES.toLong() }?.toInt() ?: return null
        count -= count % 3
        val stride = 8 * shapes + 16
        val vertexEnd = 20L + count.toLong() * stride
        if (vertexEnd > b.size) return null
        val pos = FloatArray(count * 3)
        val uv = FloatArray(count * 2)
        val col = IntArray(count)
        var maxC = 0
        for (i in 0 until count) {
            val o = 20 + i * stride
            pos[i * 3] = s16(b, o) / 4096f
            pos[i * 3 + 1] = s16(b, o + 2) / 4096f
            pos[i * 3 + 2] = s16(b, o + 4) / 4096f
            val t = o + 8 * shapes + 8 // after the shapes and the normal
            uv[i * 2] = s16(b, t) / 4096f
            uv[i * 2 + 1] = s16(b, t + 2) / 4096f
            val c = t + 4
            val r = b[c].toInt() and 0xFF; val g = b[c + 1].toInt() and 0xFF; val bl = b[c + 2].toInt() and 0xFF
            maxC = maxOf(maxC, r, g, bl)
            col[i] = (r shl 16) or (g shl 8) or bl
        }
        // PS2 colours often use 0x80 as full brightness: doubled when none goes above it.
        val dbl = maxC in 1..0x80
        for (i in 0 until count) {
            var c = col[i]
            if (dbl) {
                val r = minOf(255, ((c shr 16) and 0xFF) * 2); val g = minOf(255, ((c shr 8) and 0xFF) * 2); val bl = minOf(255, (c and 0xFF) * 2)
                c = (r shl 16) or (g shl 8) or bl
            }
            col[i] = c or (0xFF shl 24)
        }
        // The animation: header (id 1, frame length, speed, play offset, frame count), then per frame a shape id, a key
        // count, two more words and the keys (time, value). Walked only to find where the texture starts.
        var p = vertexEnd.toInt()
        var frames = 0
        if (p + 20 <= b.size && u32(b, p) == 1L) {
            frames = u32(b, p + 16).coerceIn(0, 4096).toInt()
            p += 20
            for (f in 0 until frames) {
                if (p + 16 > b.size) break
                val keys = u32(b, p + 4).coerceIn(0, 65_536).toInt()
                p += 16 + keys * 8
                if (p > b.size) break
            }
        }
        val compressed = (texType and 0x08) != 0
        val rawSize = TEX * TEX * 2
        val tex = when {
            compressed -> compressedTexture(b, p, vertexEnd.toInt())
                // (Marked compressed but the plain texture ends the file exactly: taken as it is.)
                ?: rawTexture(b, p).takeIf { p.toLong() + rawSize == b.size.toLong() }
            // Plain: where the animation ends, else the file's last 32 KB (the texture always ends it).
            p.toLong() + rawSize == b.size.toLong() -> rawTexture(b, p)
            b.size - rawSize >= vertexEnd -> rawTexture(b, b.size - rawSize)
            else -> null
        }
        return Icon(shapes, texType, count, pos, uv, col, tex, frames)
    }

    /** 32 KB of plain pixels at [at]. */
    private fun rawTexture(b: ByteArray, at: Int): IntArray? {
        if (at < 0 || at.toLong() + TEX * TEX * 2 > b.size) return null
        return IntArray(TEX * TEX) { i -> pixel(u16(b, at + i * 2)) }
    }

    /**
     * The RLE texture: a u32 size, then 16-bit codes — a code with bit 15 set copies the next (0x8000 - (code ^ 0x8000))
     * pixels as they are, any other code repeats the next pixel that many times. Its start is found where the animation
     * ends; if that doesn't add up (the size must end the file), where a size does.
     */
    private fun compressedTexture(b: ByteArray, expected: Int, from: Int): IntArray? {
        fun fits(o: Int): Boolean {
            if (o < from || o + 4 > b.size) return false
            val len = u32(b, o)
            return len >= 4 && o + 4 + len == b.size.toLong()
        }
        val at = when {
            fits(expected) -> expected
            else -> {
                var found = -1
                var o = from
                while (o + 4 <= b.size) { if (fits(o)) { found = o; break }; o += 4 }
                found
            }
        }
        if (at < 0) return null
        val end = b.size
        val out = IntArray(TEX * TEX)
        var n = 0
        var i = at + 4
        while (n < out.size && i + 2 <= end) {
            val code = u16(b, i); i += 2
            if ((code and 0x8000) != 0) {
                val len = 0x8000 - (code xor 0x8000)
                var k = 0
                while (k < len && n < out.size && i + 2 <= end) { out[n++] = pixel(u16(b, i)); i += 2; k++ }
            } else {
                if (i + 2 > end) break
                val px = pixel(u16(b, i)); i += 2
                var k = 0
                while (k < code && n < out.size) { out[n++] = px; k++ }
            }
        }
        if (n < out.size / 4) return null // hardly anything came out: not the texture after all
        while (n < out.size) out[n++] = 0xFF404040.toInt()
        return out
    }

    /** A1B5G5R5 → opaque ARGB (the browser draws icons opaque). */
    private fun pixel(v: Int): Int {
        val r = v and 0x1F; val g = (v shr 5) and 0x1F; val bl = (v shr 10) and 0x1F
        return (0xFF shl 24) or (((r shl 3) or (r shr 2)) shl 16) or (((g shl 3) or (g shr 2)) shl 8) or ((bl shl 3) or (bl shr 2))
    }

    // ---- Disk cache (cacheDir/ps2_save_icons): the raw icon.sys + icon, so icons show before the cards are read again ----

    private fun cacheFile(context: Context, id: String): java.io.File {
        val hex = java.security.MessageDigest.getInstance("SHA-1").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
        return java.io.File(java.io.File(context.cacheDir, "ps2_save_icons"), "$hex.bin")
    }

    private fun readDisk(context: Context, id: String): Pair<ByteArray?, ByteArray?>? = runCatching {
        val f = cacheFile(context, id)
        if (!f.isFile || f.length() > MAX_ICON_BYTES + 8192) return null
        java.io.DataInputStream(f.inputStream().buffered()).use { inp ->
            if (inp.readInt() != 0x56504931) return null // "VPI1"
            fun part(max: Int): ByteArray? {
                val n = inp.readInt()
                if (n < 0) return null
                if (n > max) throw java.io.IOException("too big")
                return ByteArray(n).also { inp.readFully(it) }
            }
            part(4096) to part(MAX_ICON_BYTES)
        }
    }.getOrNull()

    private fun writeDisk(context: Context, id: String, sys: ByteArray?, ico: ByteArray?) {
        val f = cacheFile(context, id)
        val dir = f.parentFile ?: return
        dir.mkdirs()
        // Kept small: the oldest go when there are many.
        dir.listFiles()?.takeIf { it.size > 400 }?.sortedBy { it.lastModified() }?.take(100)?.forEach { it.delete() }
        val tmp = java.io.File(f.path + ".tmp")
        java.io.DataOutputStream(tmp.outputStream().buffered()).use { out ->
            out.writeInt(0x56504931)
            if (sys == null) out.writeInt(-1) else { out.writeInt(sys.size); out.write(sys) }
            if (ico == null) out.writeInt(-1) else { out.writeInt(ico.size); out.write(ico) }
        }
        if (!tmp.renameTo(f)) tmp.delete()
    }

    private fun u16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun s16(b: ByteArray, o: Int): Int = u16(b, o).toShort().toInt()
    private fun u32(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or
            ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)
}

/**
 * (Update 239, #112: Save Vault › Filters & look › "PS2 browser style": PS2 saves shown as the console's memory-card
 * browser shows them — their own 3D save icon on the save's background. Off by default; only how saves are drawn.)
 */
object Ps2BrowserStyle {
    /** Read in a screen → that screen redraws when it changes. */
    val on = androidx.compose.runtime.mutableStateOf(false)
    @Volatile private var loaded = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("save_vault_ps2", Context.MODE_PRIVATE)

    fun load(context: Context): Boolean {
        if (!loaded) {
            loaded = true
            on.value = runCatching { prefs(context).getBoolean("ps2_browser_style", false) }.getOrDefault(false)
        }
        return on.value
    }

    fun set(context: Context, value: Boolean) {
        loaded = true
        on.value = value
        prefs(context).edit().putBoolean("ps2_browser_style", value).apply()
    }

    /** A PS2 console key as the library / Save Vault name it ("ps2", "PS2", "Sony PlayStation 2"). */
    fun isPs2(platform: String?): Boolean {
        val p = platform?.lowercase() ?: return false
        return p == "ps2" || p.contains("playstation 2")
    }
}
