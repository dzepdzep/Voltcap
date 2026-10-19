package com.shelf.launcher.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.shelf.launcher.data.Game
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.cos
import kotlin.math.sin

/*
 * Update 189: the data side of a game disc, as the real thing looks photographed in daylight — by the disc's physical
 * format, not just its console: a pressed CD is bright mirror silver with fine rainbow diffraction, a DVD a little
 * darker with softer rainbows, a dual-layer DVD a faint champagne tint, Blu-ray cool silver with a blue-violet hint; a
 * PlayStation disc is deep glossy black, a PS2 CD-ROM cobalt blue; a GD-ROM shows its two data zones and the ring
 * between them, a Saturn disc its security ring. The light and the rainbow move as the disc turns (they follow its
 * angle), the tracks are fine concentric lines, and the clear hub, the stacking ring and the hole are at their real
 * places (a 12 cm disc: 15 mm hole, 33 mm clamping area, data from 46 mm to 117 mm; GameCube: 8 cm).
 */

internal enum class DiscFormat {
    CD, DVD, DVD_DL, BLURAY, HDDVD, PS1, PS2_CD, PS2_DVD, PS2_DVD_DL, GDROM, SATURN, GAMECUBE, WIIU;

    /** The disc's diameter against a 12 cm one. */
    val size: Float get() = if (this == GAMECUBE) 80f / 120f else 1f
}

/**
 * Update 248 (#89 finding 11): what a disc image's own bytes say about the disc it came from, apart from the file
 * handling, so it can be tested on made-up images (DiscImagesTest).
 */
internal object DiscImages {
    enum class Kind { CD, DVD, UNKNOWN }

    /**
     * A CHD's unit size and full size: CD frames are stored as 2352 or 2448 bytes (a CD); 2048-byte sectors are a DVD's —
     * or a CD's dumped as a plain ISO (seen on the Thor: PS2 CHDs of 101 MB to 6 GB, all 2048), so only one bigger than
     * any CD can hold (80 minutes, 737 MB) counts as a DVD.
     */
    fun chdKind(unit: Long, bytes: Long = Long.MAX_VALUE): Kind = when (unit) {
        2352L, 2448L -> Kind.CD
        2048L -> if (bytes > CD_MAX) Kind.DVD else Kind.UNKNOWN
        else -> Kind.UNKNOWN
    }

    /** The most an 80-minute CD holds in 2048-byte sectors (360 000 of them). */
    const val CD_MAX = 737_280_000L

    /**
     * A plain image read through [read] (offset, length → bytes, or null past the end): UDF's descriptors ("BEA01",
     * "NSR02" / "NSR03") in the sectors after the ISO 9660 one (from sector 17, 2048-byte sectors) → a DVD; the ISO 9660
     * descriptor in a raw 2352-byte frame (sector 16, its data 16 bytes in) → a CD; the ISO 9660 descriptor alone in
     * 2048-byte sectors → a CD (a PS2 DVD always has UDF too); nothing recognised → unknown.
     */
    fun plainKind(read: (Long, Int) -> ByteArray?): Kind {
        fun idAt(at: Long): String? = read(at, 6)?.let { String(it, 1, 5, Charsets.US_ASCII) }
        val iso2048 = idAt(16L * 2048) == "CD001"
        for (sector in 17..24) {
            val id = idAt(sector.toLong() * 2048) ?: break
            if (id == "BEA01" || id == "NSR02" || id == "NSR03") return Kind.DVD
            if (id == "TEA01") break
        }
        if (idAt(16L * 2352 + 16) == "CD001" || idAt(16L * 2352 + 24) == "CD001") return Kind.CD
        return if (iso2048) Kind.CD else Kind.UNKNOWN
    }

    /** The format for [kind] and the image's full size [bytes] (0 unknown); [ps2]: a PS2 game, else a plain DVD console. */
    fun format(kind: Kind, bytes: Long, ps2: Boolean, base: DiscFormat): DiscFormat {
        val dual = bytes > 4_700_000_000L
        return when (kind) {
            Kind.CD -> if (ps2) DiscFormat.PS2_CD else base
            Kind.DVD -> if (dual) (if (ps2) DiscFormat.PS2_DVD_DL else DiscFormat.DVD_DL) else base
            // Nothing to read (compressed): a PS2 image that fits on a CD with room to spare is taken for one, as before.
            Kind.UNKNOWN -> when {
                ps2 && bytes in 1..DiscImages.CD_MAX -> DiscFormat.PS2_CD
                dual -> if (ps2) DiscFormat.PS2_DVD_DL else DiscFormat.DVD_DL
                else -> base
            }
        }
    }
}

internal object DiscFormats {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, DiscFormat>()

    /** From the console alone (no file read): PS2 reads as a DVD until its image says otherwise. */
    fun quick(platform: String): DiscFormat = when (platform) {
        "PS1" -> DiscFormat.PS1
        "PS2" -> DiscFormat.PS2_DVD
        "Dreamcast" -> DiscFormat.GDROM
        "Saturn" -> DiscFormat.SATURN
        "GameCube" -> DiscFormat.GAMECUBE
        "WiiU" -> DiscFormat.WIIU
        "Wii", "Xbox", "Xbox360" -> DiscFormat.DVD
        "PS3", "PS4", "PS5", "XboxOne", "XboxSeries" -> DiscFormat.BLURAY
        else -> DiscFormat.CD
    }

    /**
     * The game's real format, from its disc image where it tells (blocking: a few bytes read): a PS2 game on a CD-ROM
     * (blue) or a DVD, single or dual layer; a dual-layer Wii / Xbox disc.
     */
    fun of(g: Game): DiscFormat {
        val base = quick(g.platform)
        if (base != DiscFormat.PS2_DVD && base != DiscFormat.DVD) return base
        cache[g.stableKey]?.let { return it }
        val found = runCatching { detect(g, base) }.getOrNull() ?: base
        cache[g.stableKey] = found
        return found
    }

    private const val GB = 1_000_000_000L

    /**
     * Update 248 (#89 finding 11): read from the image itself where it can say. Before, any PS2 image under 900 MB read as
     * a CD-ROM (a small DVD game drew blue), a CHD of a DVD under 900 MB too, and every .img / .mds / .mdf as a CD. Now:
     * a CHD says its sector size (CD frames 2352 / 2448 bytes; 2048-byte sectors past a CD's size are a DVD); a plain image (.iso, .img, .mdf, .bin)
     * is a DVD when it carries UDF's volume descriptors after the ISO 9660 one (PS2 DVDs are UDF bridge discs, PS2 CDs
     * plain ISO 9660), a CD when its sectors are raw 2352-byte frames; a .cue / .ccd / .mds sheet is read through its
     * data file next to it. A compressed ISO (.cso / .zso) and anything unreadable still go by size.
     */
    private fun detect(g: Game, base: DiscFormat): DiscFormat? {
        val path = g.romPath ?: return null
        val ext = path.substringAfterLast('.', "").substringBefore('?').lowercase()
        val ps2 = base == DiscFormat.PS2_DVD
        val f0 = if (path.startsWith("/")) File(path) else return if (ps2 && ext in CD_SHEETS) DiscFormat.PS2_CD else null
        // A cue / ccd / mds sheet: its data file beside it (same name), else what the sheet type usually holds.
        val f = if (ext in CD_SHEETS) dataFileFor(f0) ?: return if (ps2 && ext != "mds") DiscFormat.PS2_CD else null else f0
        if (!f.isFile) return null
        val dataExt = f.name.substringAfterLast('.', "").lowercase()
        val kind: DiscImages.Kind
        val bytes: Long
        when (dataExt) {
            "chd" -> {
                val h = ByteArray(64)
                RandomAccessFile(f, "r").use { it.readFully(h) }
                if (String(h, 0, 8, Charsets.US_ASCII) != "MComprHD") return null
                bytes = u64be(h, 32)
                kind = DiscImages.chdKind(u32be(h, 60), bytes)
            }
            "cso", "zso" -> {
                // A compressed ISO keeps the full size at bytes 8…15 (little-endian).
                val h = ByteArray(16)
                RandomAccessFile(f, "r").use { it.readFully(h) }
                var v = 0L
                for (i in 15 downTo 8) v = (v shl 8) or (h[i].toLong() and 0xFF)
                kind = DiscImages.Kind.UNKNOWN
                bytes = v
            }
            "iso", "img", "mdf", "bin", "nrg" -> {
                kind = RandomAccessFile(f, "r").use { r -> DiscImages.plainKind { at, n -> readAt(r, at, n) } }
                bytes = f.length()
            }
            else -> return null
        }
        return DiscImages.format(kind, bytes, ps2, base)
    }

    private val CD_SHEETS = setOf("cue", "ccd", "mds")

    /** The data file a cue / ccd / mds sheet points at: the same name with a data extension, in the same folder. */
    private fun dataFileFor(sheet: File): File? {
        val stem = sheet.name.substringBeforeLast('.')
        val dir = sheet.parentFile ?: return null
        for (e in listOf("bin", "img", "mdf", "iso")) {
            File(dir, "$stem.$e").takeIf { it.isFile }?.let { return it }
            File(dir, "$stem.${e.uppercase()}").takeIf { it.isFile }?.let { return it }
        }
        return null
    }

    private fun readAt(r: RandomAccessFile, at: Long, n: Int): ByteArray? = runCatching {
        if (at < 0 || at + n > r.length()) return@runCatching null
        val b = ByteArray(n); r.seek(at); r.readFully(b); b
    }.getOrNull()

    private fun u32be(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)
    private fun u64be(b: ByteArray, o: Int): Long = (u32be(b, o) shl 32) or u32be(b, o + 4)
}

/** One format's look: the metal's colours (centre, middle, edge), its gloss, its rainbow and a tint over it. */
private class UndersideLook(
    val metal: List<Color>, val gloss: Float, val glossColor: Color, val rainbow: Float, val rainbowColors: List<Color>,
    val tint: Color, val hub: Color, val tracks: Float
)

private val RAINBOW = listOf(
    Color(0xFFFF6A6A), Color(0xFFFFC46A), Color(0xFFE9FF6A), Color(0xFF6AFFA0), Color(0xFF6AD6FF),
    Color(0xFF8A7AFF), Color(0xFFE07AFF), Color(0xFFFF6A6A)
)
private val RAINBOW_COOL = listOf(
    Color(0xFF7AA8FF), Color(0xFF9A7AFF), Color(0xFF6AD6FF), Color(0xFFB59BFF), Color(0xFF7AA8FF)
)
private val RAINBOW_VIOLET = listOf(
    Color(0xFF8C6BFF), Color(0xFF5A7BFF), Color(0xFFB36BFF), Color(0xFF6AA8FF), Color(0xFF8C6BFF)
)

private fun lookOf(f: DiscFormat): UndersideLook = when (f) {
    DiscFormat.CD, DiscFormat.SATURN, DiscFormat.GDROM -> UndersideLook(
        listOf(Color(0xFFEDEFF3), Color(0xFFD3D7DE), Color(0xFFB6BBC4)), 0.6f, Color.White, 0.2f, RAINBOW, Color.Transparent, Color(0x55E6E9EE), 0.05f
    )
    DiscFormat.DVD, DiscFormat.PS2_DVD, DiscFormat.GAMECUBE, DiscFormat.HDDVD -> UndersideLook(
        listOf(Color(0xFFDCDFE5), Color(0xFFC0C5CD), Color(0xFFA2A8B2)), 0.45f, Color.White, if (f == DiscFormat.HDDVD) 0.1f else 0.12f, RAINBOW,
        Color.Transparent, Color(0x55E2E5EA), 0.035f
    )
    DiscFormat.DVD_DL, DiscFormat.PS2_DVD_DL -> UndersideLook(
        listOf(Color(0xFFE2DCCD), Color(0xFFC9BFA8), Color(0xFFA99D84)), 0.42f, Color(0xFFFFF4DE), 0.1f, RAINBOW,
        Color(0x26D9B26F), Color(0x55E8E2D6), 0.035f
    )
    DiscFormat.BLURAY, DiscFormat.WIIU -> UndersideLook(
        listOf(Color(0xFFD9DDE6), Color(0xFFBAC0CE), Color(0xFF9CA3B5)), 0.45f, Color(0xFFF0F3FF), 0.07f, RAINBOW_COOL,
        Color(0x1A6F64FF), Color(0x55E3E6EE), 0.02f
    )
    DiscFormat.PS1 -> UndersideLook(
        listOf(Color(0xFF1A1B20), Color(0xFF0E0F13), Color(0xFF060709)), 0.2f, Color(0xFFE6EAF2), 0.035f, RAINBOW,
        Color.Transparent, Color(0x99141519), 0.025f
    )
    DiscFormat.PS2_CD -> UndersideLook(
        listOf(Color(0xFF3550C9), Color(0xFF22339A), Color(0xFF2B1F73)), 0.38f, Color(0xFFD8DEFF), 0.07f, RAINBOW_VIOLET,
        Color.Transparent, Color(0x7731409A), 0.03f
    )
}

/**
 * The data side, centred at [c] with outer radius [r], seen at [view] degrees (the disc's turn: the light and the
 * rainbow follow it). The hole is cut through what's under it.
 */
internal fun DrawScope.drawDiscUnderside(c: Offset, r: Float, format: DiscFormat, view: Float) {
    val look = lookOf(format)
    val gc = format == DiscFormat.GAMECUBE
    // Radii (share of the outer radius): 12 cm discs vs the 8 cm GameCube disc (same 15 mm hole, 33 mm clamp).
    val hole = if (gc) 0.19f else 0.125f
    val clamp = if (gc) 0.41f else 0.275f
    val dataIn = if (gc) 0.55f else 0.383f
    val dataOut = if (gc) 0.965f else 0.975f
    val a = Math.toRadians(view.toDouble())
    // The metal: a mirror reflects the room in two bright lobes and two darker ones (the way a disc's underside
    // catches the light), turning round as the disc turns.
    drawCircle(Brush.radialGradient(look.metal, center = c, radius = r), radius = r * dataOut, center = c)
    val m0 = look.metal[0]; val m1 = look.metal[1]; val m2 = look.metal[2]
    // Update 228 (#37): the line across the middle of disc backs. The lobes were 9 colour stops 45° apart, blended in
    // straight lines: each bright / dark stop and the one opposite it made a hard crease from edge to edge through the
    // centre, and they showed through the clear hub. Now the same two bright and two dark lobes, rising and falling
    // smoothly (33 stops along a cosine), and kept off the clear hub (no metal there on a real disc).
    val hubCut = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r * clamp)) }
    val lobes = List(33) { i ->
        val k = cos(4.0 * Math.PI * i / 32).toFloat() // +1: bright (0°, 180°), -1: dark (90°, 270°)
        if (k >= 0f) androidx.compose.ui.graphics.lerp(m1, m0, k) else androidx.compose.ui.graphics.lerp(m1, m2, -k)
    }
    clipPath(hubCut, androidx.compose.ui.graphics.ClipOp.Difference) {
        rotate(view * 0.9f + 45f, c) {
            drawCircle(Brush.sweepGradient(lobes, c), radius = r * dataOut, center = c, alpha = 0.8f)
        }
    }
    // The fine tracks: faint concentric lines across the data area.
    val dataMid = r * (dataIn + dataOut) / 2f
    val dataW = r * (dataOut - dataIn)
    val n = 46
    for (i in 0..n) {
        val rr = r * (dataIn + (dataOut - dataIn) * i / n)
        drawCircle(if (i % 2 == 0) Color.White else Color.Black, radius = rr, center = c, alpha = look.tracks, style = Stroke(0.7f))
    }
    // Format marks: a GD-ROM's two areas and the ring between them, a Saturn's security ring, a dual-layer's layer edge.
    when (format) {
        DiscFormat.GDROM -> {
            drawCircle(Color.White, radius = r * (dataIn + 0.06f), center = c, alpha = 0.08f, style = Stroke(r * 0.12f))
            drawCircle(Color.Black, radius = r * 0.615f, center = c, alpha = 0.32f, style = Stroke(r * 0.022f))
            drawCircle(Color.Black, radius = r * 0.8f, center = c, alpha = 0.06f, style = Stroke(r * 0.33f))
        }
        DiscFormat.SATURN -> {
            drawCircle(Color.Black, radius = r * 0.95f, center = c, alpha = 0.16f, style = Stroke(r * 0.03f))
            for (i in 0 until 180) {
                val t = i * 2.0 * Math.PI / 180
                val x0 = c.x + cos(t).toFloat() * r * 0.937f; val y0 = c.y + sin(t).toFloat() * r * 0.937f
                val x1 = c.x + cos(t).toFloat() * r * 0.962f; val y1 = c.y + sin(t).toFloat() * r * 0.962f
                drawLine(Color.Black, Offset(x0, y0), Offset(x1, y1), strokeWidth = 0.8f, alpha = 0.14f)
            }
        }
        DiscFormat.DVD_DL, DiscFormat.PS2_DVD_DL -> drawCircle(Color(0xFF8A7448), radius = r * 0.78f, center = c, alpha = 0.1f, style = Stroke(r * 0.006f))
        else -> Unit
    }
    // The rainbow: light split by the tracks, turning round as the disc turns (never painted on: faint, in the data area).
    // (Two bands, beside the bright lobes, as diffraction shows on a real disc.)
    if (look.rainbow > 0f) rotate(view * 0.9f + 28f, c) {
        drawCircle(Brush.sweepGradient(look.rainbowColors + look.rainbowColors.drop(1), c), radius = dataMid, center = c, alpha = (look.rainbow * 1.4f).coerceAtMost(0.35f), style = Stroke(dataW))
    }
    // The light's reflection: a soft band across the mirror, sliding as it turns.
    val dir = Offset(cos(a + 0.6).toFloat(), sin(a + 0.6).toFloat())
    val shift = sin(a * 1.3).toFloat() * r * 0.5f
    val mid = Offset(c.x + dir.x * shift, c.y + dir.y * shift)
    // (Update 228, #37: a soft rise and fall — the band peaked in a sharp point, which showed as a thin line across the
    // disc — and kept off the clear hub.)
    val g = look.glossColor
    val k = look.gloss
    val band = Brush.linearGradient(
        0f to Color.Transparent, 0.38f to Color.Transparent, 0.42f to g.copy(alpha = k * 0.2f), 0.46f to g.copy(alpha = k * 0.7f),
        0.5f to g.copy(alpha = k), 0.54f to g.copy(alpha = k * 0.7f), 0.58f to g.copy(alpha = k * 0.2f), 0.62f to Color.Transparent,
        1f to Color.Transparent,
        start = Offset(mid.x - dir.x * r, mid.y - dir.y * r), end = Offset(mid.x + dir.x * r, mid.y + dir.y * r)
    )
    clipPath(hubCut, androidx.compose.ui.graphics.ClipOp.Difference) {
        drawCircle(band, radius = r * dataOut, center = c)
        if (look.tint.alpha > 0f) drawCircle(look.tint, radius = r * dataOut, center = c)
    }
    // The mirror band inside the data (no tracks) and the lead-in edge.
    drawCircle(Brush.radialGradient(look.metal.reversed(), center = c, radius = r * dataIn), radius = (r * (dataIn + clamp) / 2f), center = c, style = Stroke(r * (dataIn - clamp)))
    drawCircle(Color.Black, radius = r * dataIn, center = c, alpha = 0.12f, style = Stroke(r * 0.006f))
    // The clear rim round the edge (a Wii U disc's edge rounded: lit, then shaded).
    if (format == DiscFormat.WIIU) {
        drawCircle(Brush.radialGradient(0.9f to Color.White.copy(alpha = 0.5f), 1f to Color.Black.copy(alpha = 0.35f), center = c, radius = r), radius = r * (1f + dataOut) / 2f, center = c, style = Stroke(r * (1f - dataOut)))
    } else {
        drawCircle(Color(0xFFC9CDD4).copy(alpha = if (format == DiscFormat.PS1) 0.25f else 0.55f), radius = r * (1f + dataOut) / 2f, center = c, style = Stroke(r * (1f - dataOut)))
    }
    drawCircle(Color.Black, radius = r - 0.5f, center = c, alpha = 0.25f, style = Stroke(1f))
    // The clear hub: see-through plastic, the stacking ring raised in it, and the hole right through.
    drawCircle(look.hub, radius = (r * (clamp + hole) / 2f), center = c, style = Stroke(r * (clamp - hole)))
    val stack = r * (hole + (clamp - hole) * 0.62f)
    drawCircle(Color.White, radius = stack, center = c, alpha = 0.32f, style = Stroke(r * 0.012f))
    drawCircle(Color.Black, radius = stack + r * 0.012f, center = c, alpha = 0.16f, style = Stroke(r * 0.008f))
    drawCircle(Color.White, radius = r * clamp, center = c, alpha = 0.2f, style = Stroke(r * 0.006f))
    drawCircle(Color.Black, radius = r * hole, center = c, blendMode = BlendMode.Clear)
    drawCircle(Color.White, radius = r * hole, center = c, alpha = 0.3f, style = Stroke(r * 0.008f))
}
