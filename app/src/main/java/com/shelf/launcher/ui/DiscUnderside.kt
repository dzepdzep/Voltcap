package com.shelf.launcher.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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

    private fun detect(g: Game, base: DiscFormat): DiscFormat? {
        val path = g.romPath ?: return null
        val ext = path.substringAfterLast('.', "").substringBefore('?').lowercase()
        val ps2 = base == DiscFormat.PS2_DVD
        if (ext in setOf("cue", "bin", "ccd", "img", "mds", "gdi")) return if (ps2) DiscFormat.PS2_CD else base
        val f = if (path.startsWith("/")) File(path) else return null
        if (!f.isFile) return null
        val bytes: Long = when (ext) {
            "iso" -> f.length()
            "chd" -> {
                // A CHD (v5) says it itself: CD frames are 2448 bytes, DVD sectors 2048; and its full size.
                val h = ByteArray(64)
                RandomAccessFile(f, "r").use { it.readFully(h) }
                if (String(h, 0, 8, Charsets.US_ASCII) != "MComprHD") return null
                val unit = u32be(h, 60)
                if (unit == 2448L || unit == 2352L) return if (ps2) DiscFormat.PS2_CD else base
                u64be(h, 32)
            }
            "cso", "zso" -> {
                // A compressed ISO keeps the full size at bytes 8…15 (little-endian).
                val h = ByteArray(16)
                RandomAccessFile(f, "r").use { it.readFully(h) }
                var v = 0L
                for (i in 15 downTo 8) v = (v shl 8) or (h[i].toLong() and 0xFF)
                v
            }
            else -> return null
        }
        return when {
            ps2 && bytes in 1 until 900_000_000L -> DiscFormat.PS2_CD
            bytes > (4.7 * GB).toLong() -> if (ps2) DiscFormat.PS2_DVD_DL else DiscFormat.DVD_DL
            else -> base
        }
    }

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
    // The metal, lit a little more or less as it turns.
    val turnLight = 0.9f + 0.1f * cos(a).toFloat()
    drawCircle(Brush.radialGradient(look.metal, center = c, radius = r), radius = r * dataOut, center = c)
    if (turnLight < 0.999f) drawCircle(Color.Black, radius = r * dataOut, center = c, alpha = (1f - turnLight) * 0.6f)
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
    if (look.rainbow > 0f) rotate(view * 1.6f, c) {
        drawCircle(Brush.sweepGradient(look.rainbowColors, c), radius = dataMid, center = c, alpha = look.rainbow, style = Stroke(dataW))
    }
    // The light's reflection: a soft band across the mirror, sliding as it turns.
    val dir = Offset(cos(a + 0.6).toFloat(), sin(a + 0.6).toFloat())
    val shift = sin(a * 1.3).toFloat() * r * 0.5f
    val mid = Offset(c.x + dir.x * shift, c.y + dir.y * shift)
    val band = Brush.linearGradient(
        0f to Color.Transparent, 0.42f to Color.Transparent, 0.5f to look.glossColor.copy(alpha = look.gloss),
        0.58f to Color.Transparent, 1f to Color.Transparent,
        start = Offset(mid.x - dir.x * r, mid.y - dir.y * r), end = Offset(mid.x + dir.x * r, mid.y + dir.y * r)
    )
    drawCircle(band, radius = r * dataOut, center = c)
    if (look.tint.alpha > 0f) drawCircle(look.tint, radius = r * dataOut, center = c)
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
