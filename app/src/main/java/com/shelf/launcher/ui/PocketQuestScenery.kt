package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * Update 202: Pocket Quest's look — what its screens share (StylePocketQuest.kt draws the screens):
 * - the palettes of the four themes (deep blue / indigo panels, warm gold — or cyan — edges), and the accent choice;
 * - chamfered (cut-corner) panels with crisp 1–2 px borders, a thin inner highlight, small corner studs and, on the
 *   darker Deep Lab theme, a faint technical grid;
 * - the small pixel-art pieces (the moving arrow cursor, the ‹ › arrows);
 * - the scenery behind the menu: original pixel-art landscapes made in code (no pictures from anywhere), each a few
 *   layers drawn once into small bitmaps (400 × 180 dots) and stretched with nearest-neighbour filtering, so the pixels
 *   stay crisp. The layers shift a little as you move along the games (parallax), the clouds drift and a few motes,
 *   bubbles or lights move — read from the shared EffectClock in the draw phase only (nothing allocated per frame,
 *   still while the home screen is idle, in the background, on the battery saver's rest or with reduced motion).
 * - the small drawing for the layout picker and the theme chips.
 */

// ---- Palettes ----

/** Update 202: one theme's colours (panels, edges, text). */
internal class PqPalette(
    val theme: Features.PqTheme,
    /** The panels' fill, top to bottom. */
    val top: Color,
    val bottom: Color,
    /** Wells inside a panel (list rows, the collection's tray). */
    val deep: Color,
    /** The dark line outside and inside the edge. */
    val edgeDark: Color,
    /** The edge, the cursor, Play. */
    val accent: Color,
    val accentDeep: Color,
    val accentLight: Color,
    /** Text on [accent]. */
    val onAccent: Color,
    val ink: Color,
    val muted: Color,
    /** The thin inner highlight along a panel's top. */
    val highlight: Color,
    /** The top bar's fill. */
    val bar: Color,
    /** A faint technical grid inside the panels (Deep Lab). */
    val grid: Boolean
)

/** Update 202: the palette in use (theme + accent choice). */
@Composable
internal fun pqPalette(): PqPalette {
    val theme = Features.pqTheme.state.value
    val pick = Features.pqAccent.state.value
    val voltcap = ShelfTheme.accent
    return remember(theme, pick, voltcap) { pqPaletteFor(theme, pick, voltcap) }
}

internal fun pqPaletteFor(theme: Features.PqTheme, pick: Features.PqAccent, voltcap: Color): PqPalette {
    // The theme's own accent first (gold for the bright ones, cyan for the deep ones).
    val own: Pair<Color, Color> = when (theme) {
        Features.PqTheme.ISLAND -> Color(0xFFF2C64B) to Color(0xFFB27A20)
        Features.PqTheme.SUNSET -> Color(0xFFF6C35C) to Color(0xFFB8702A)
        Features.PqTheme.LAB -> Color(0xFF46D9FF) to Color(0xFF1A7FAE)
        Features.PqTheme.OCEAN -> Color(0xFF66E4F0) to Color(0xFF2389A6)
    }
    val acc: Pair<Color, Color> = when (pick) {
        Features.PqAccent.THEME -> own
        Features.PqAccent.GOLD -> Color(0xFFF2C64B) to Color(0xFFB27A20)
        Features.PqAccent.CYAN -> Color(0xFF46D9FF) to Color(0xFF1A7FAE)
        Features.PqAccent.ROSE -> Color(0xFFFF93B5) to Color(0xFFB84676)
        Features.PqAccent.EMERALD -> Color(0xFF63E392) to Color(0xFF268C50)
        Features.PqAccent.SILVER -> Color(0xFFE4E9F1) to Color(0xFF8A95A8)
        Features.PqAccent.VOLTCAP -> voltcap to lerp(voltcap, Color.Black, 0.42f)
    }
    val accent = acc.first
    val onAccent = if (accent.luminance() > 0.42f) Color(0xFF0E1433) else Color.White
    fun make(top: Long, bottom: Long, deep: Long, edgeDark: Long, muted: Long, highlight: Long, bar: Long, grid: Boolean) = PqPalette(
        theme, Color(top), Color(bottom), Color(deep), Color(edgeDark), accent, acc.second, lerp(accent, Color.White, 0.45f), onAccent,
        Color.White, Color(muted), Color(highlight), Color(bar), grid
    )
    return when (theme) {
        Features.PqTheme.ISLAND -> make(0xFF2B4FAA, 0xFF1A2E78, 0xFF132260, 0xFF0A1236, 0xFFC9D7FF, 0xFF7393E4, 0xFF121C4C, false)
        Features.PqTheme.SUNSET -> make(0xFF40318A, 0xFF23175A, 0xFF1A1146, 0xFF12082A, 0xFFE8D6FF, 0xFF9070DA, 0xFF1C1240, false)
        Features.PqTheme.LAB -> make(0xFF13224E, 0xFF0A1232, 0xFF070D26, 0xFF02051A, 0xFFAAC6F2, 0xFF2F5296, 0xFF060B22, true)
        Features.PqTheme.OCEAN -> make(0xFF1B4092, 0xFF0E2464, 0xFF0A1A4E, 0xFF040D2E, 0xFFC4E5FF, 0xFF5089DA, 0xFF0A1A48, false)
    }
}

/** Update 202: the theme's scenery is drawn behind the menu (Pocket Quest › Background), not Voltcap's own background. */
internal fun pqOwnsBackground(): Boolean = Features.pqBackground.state.value == Features.PqBackground.SCENERY

/** Update 202: reduced motion is on (Pocket Quest › Reduced motion), or the system's animations are off. */
internal fun pqReduced(): Boolean = Features.pqReducedMotion.state.value

/** Update 202: how long a move takes, from [ms] at Normal (Pocket Quest › Animation speed). */
internal fun pqMs(ms: Int): Int = when (Features.pqSpeed.state.value) {
    Features.PqSpeed.RELAXED -> ms * 135 / 100
    Features.PqSpeed.NORMAL -> ms
    Features.PqSpeed.SNAPPY -> ms * 65 / 100
}

// ---- Chamfered panels ----

/** Update 202: a rectangle with its four corners cut off at 45° by [c] px, [inset] px inside the box. */
internal fun pqChamferPath(w: Float, h: Float, c: Float, inset: Float, path: Path = Path()): Path {
    val i = inset
    // Moving every edge in by [inset] shortens the cut by (2 − √2) · inset along each side.
    val cc = (c - inset * 0.586f).coerceAtLeast(0f)
    path.reset()
    path.moveTo(i + cc, i)
    path.lineTo(w - i - cc, i)
    path.lineTo(w - i, i + cc)
    path.lineTo(w - i, h - i - cc)
    path.lineTo(w - i - cc, h - i)
    path.lineTo(i + cc, h - i)
    path.lineTo(i, h - i - cc)
    path.lineTo(i, i + cc)
    path.close()
    return path
}

/** Update 202: the cut-corner shape, for clipping a panel's contents. */
internal data class PqChamfer(val cut: Dp, val inset: Dp = 0.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2.5f)
        val i = with(density) { inset.toPx() }
        return Outline.Generic(pqChamferPath(size.width, size.height, c, i))
    }
}

/**
 * Update 202: a layered 32-bit-style panel behind the content: a dark outline, the accent edge ([edge] wide, brighter
 * when [lit]), a dark line inside it, the fill (blue / indigo, or the darker well with [deep]), a thin highlight along
 * the top, the corner studs and, on the darker theme, a faint grid. Built once per size (drawWithCache).
 */
internal fun Modifier.pqPanel(
    pal: PqPalette,
    cut: Dp = 10.dp,
    edge: Dp = 1.5.dp,
    lit: Boolean = false,
    alpha: Float = 0.95f,
    deep: Boolean = false,
    studs: Boolean = true
): Modifier = this.drawWithCache {
    val w = size.width
    val h = size.height
    val c = cut.toPx().coerceAtMost(minOf(w, h) / 2.5f)
    val one = 1.dp.toPx().coerceAtLeast(1f)
    val e = (edge.toPx() * (if (lit) 1.35f else 1f)).coerceAtLeast(1f)
    val p0 = pqChamferPath(w, h, c, 0f)
    val p1 = pqChamferPath(w, h, c, one)
    val p2 = pqChamferPath(w, h, c, one + e)
    val p3 = pqChamferPath(w, h, c, one + e + one)
    val edgeBrush = Brush.verticalGradient(listOf(if (lit) pal.accentLight else pal.accent, if (lit) pal.accent else pal.accentDeep))
    val fill = if (deep) Brush.verticalGradient(listOf(pal.deep.copy(alpha = alpha), lerp(pal.deep, Color.Black, 0.3f).copy(alpha = alpha)))
        else Brush.verticalGradient(listOf(pal.top.copy(alpha = alpha), pal.bottom.copy(alpha = alpha)))
    val inner = one * 2f + e
    val hiY = inner + one
    val gridStep = 14.dp.toPx()
    val gridInk = pal.highlight.copy(alpha = 0.09f)
    val hiInk = pal.highlight.copy(alpha = if (deep) 0.3f else 0.6f)
    val stud = 2.5.dp.toPx().coerceAtLeast(2f)
    val studAt = c * 0.5f + one + e * 0.5f
    onDrawBehind {
        drawPath(p0, pal.edgeDark)
        drawPath(p1, edgeBrush)
        drawPath(p2, pal.edgeDark)
        drawPath(p3, fill)
        if (pal.grid) clipPath(p3) {
            var x = inner + gridStep
            while (x < w) { drawRect(gridInk, Offset(x, 0f), Size(one, h)); x += gridStep }
            var y = inner + gridStep
            while (y < h) { drawRect(gridInk, Offset(0f, y), Size(w, one)); y += gridStep }
        }
        val hiW = w - 2f * (inner + c * 0.6f)
        if (hiW > 0f) drawRect(hiInk, Offset(inner + c * 0.6f, hiY), Size(hiW, one))
        if (studs && c >= 5f) {
            for (k in 0 until 4) {
                val sx = if (k % 2 == 0) studAt else w - studAt
                val sy = if (k < 2) studAt else h - studAt
                drawRect(pal.edgeDark, Offset(sx - stud - one, sy - stud - one), Size(stud * 2f + one * 2f, stud * 2f + one * 2f))
                drawRect(pal.accentLight, Offset(sx - stud, sy - stud), Size(stud * 2f, stud * 2f))
                drawRect(pal.accentDeep, Offset(sx, sy), Size(stud, stud))
            }
        }
    }
}

/** Update 202: the gold (accent) button fill: a bright chamfered bar with a darker foot and a light top line. */
internal fun Modifier.pqGoldButton(pal: PqPalette, cut: Dp = 7.dp, lit: Boolean = false): Modifier = this.drawWithCache {
    val w = size.width
    val h = size.height
    val c = cut.toPx().coerceAtMost(minOf(w, h) / 2.5f)
    val one = 1.dp.toPx().coerceAtLeast(1f)
    val p0 = pqChamferPath(w, h, c, 0f)
    val p1 = pqChamferPath(w, h, c, one)
    val p2 = pqChamferPath(w, h, c, one * (if (lit) 3f else 2f))
    val body = Brush.verticalGradient(0f to pal.accentLight, 0.5f to pal.accent, 1f to pal.accentDeep)
    val ring = if (lit) Color.White else pal.accentDeep
    onDrawBehind {
        drawPath(p0, pal.edgeDark)
        drawPath(p1, ring)
        drawPath(p2, body)
        drawRect(Color.White.copy(alpha = 0.55f), Offset(c, one * 3f), Size((w - 2f * c).coerceAtLeast(0f), one))
    }
}

// ---- Pixel art ----

/** Update 202: the arrow cursor (points right), '#' outline, '+' the accent, 'o' its light. */
internal val PQ_ARROW = arrayOf(
    "##......",
    "#o#.....",
    "#+o#....",
    "#++o#...",
    "#+++o#..",
    "#++++##.",
    "#+++##..",
    "#++##...",
    "#+##....",
    "###....."
)

/** Update 202: a chunky ‹ (points left). */
internal val PQ_LEFT = arrayOf(
    "....##",
    "...#o#",
    "..#o+#",
    ".#o++#",
    "#o+++#",
    ".#+++#",
    "..#++#",
    "...#+#",
    "....##"
)

/** Update 202: draws [rows] filling the box, one cell per dot; '#' [ink], '+' [fill], 'o' [light]. */
internal fun DrawScope.pqPixelArt(rows: Array<String>, ink: Color, fill: Color, light: Color = fill) {
    val cols = rows[0].length
    val cw = size.width / cols
    val ch = size.height / rows.size
    for (y in rows.indices) {
        val r = rows[y]
        for (x in 0 until cols) {
            val col = when (r[x]) { '#' -> ink; '+' -> fill; 'o' -> light; else -> Color.Unspecified }
            if (col.isSpecified) drawRect(col, Offset(floor(x * cw), floor(y * ch)), Size(ceil(cw), ceil(ch)))
        }
    }
}

/** Update 202: the moving pixel arrow (bobs along its direction; still with reduced motion). [down]: points down. */
@Composable
internal fun PqCursor(pal: PqPalette, modifier: Modifier, down: Boolean = false) {
    val still = Features.pqReducedMotion.state.value
    Canvas(
        modifier.graphicsLayer {
            val t = if (still) 0f else EffectClock.seconds.floatValue
            val b = (sin(t * 6f) * 0.5f + 0.5f) * 3.dp.toPx()
            if (down) { translationY = b; rotationZ = 90f } else translationX = b
        }
    ) { pqPixelArt(PQ_ARROW, pal.edgeDark, pal.accent, pal.accentLight) }
}

/** Update 202: the ‹ › arrows ([right] mirrors it), nudging outward gently. */
@Composable
internal fun PqArrow(pal: PqPalette, right: Boolean, modifier: Modifier) {
    val still = Features.pqReducedMotion.state.value
    Canvas(
        modifier.graphicsLayer {
            val t = if (still) 0f else EffectClock.seconds.floatValue
            val b = (sin(t * 4f) * 0.5f + 0.5f) * 2.dp.toPx()
            if (right) { scaleX = -1f; translationX = b } else translationX = -b
        }
    ) { pqPixelArt(PQ_LEFT, pal.edgeDark, pal.accent, pal.accentLight) }
}

// ---- Scenery ----

/** Update 202: the scenery's layers for a theme, drawn once (off the main thread) and kept for the theme in use. */
internal object PqScenery {
    /** Width / height of every layer in dots, and the width shown at once (the rest is room for the parallax). */
    const val W = 400
    const val H = 180
    const val VIEW_W = 320

    class Layers(
        val theme: Features.PqTheme,
        /** Sky and far land: barely moves. */
        val back: ImageBitmap,
        /** Clouds / haze: drifts slowly and wraps round. */
        val clouds: ImageBitmap,
        /** The near land: shifts most as you move along. */
        val near: ImageBitmap,
        /** Where the small live touches sit (sparkles, lights), in the layer's dots. */
        val spotsX: IntArray,
        val spotsY: IntArray
    )

    @Volatile private var cached: Layers? = null

    fun cachedFor(t: Features.PqTheme): Layers? = cached?.takeIf { it.theme == t }

    /** The layers for [t] (made now if they aren't kept already). Run it off the main thread. */
    fun build(t: Features.PqTheme): Layers {
        cachedFor(t)?.let { return it }
        val made = when (t) {
            Features.PqTheme.ISLAND -> buildIsland()
            Features.PqTheme.SUNSET -> buildSunset()
            Features.PqTheme.LAB -> buildLab()
            Features.PqTheme.OCEAN -> buildOcean()
        }
        cached = made
        return made
    }
}

/** A small pixel canvas (ARGB ints); [wrap] repeats it sideways (for the drifting clouds). */
private class PqPx(val w: Int, val h: Int, val wrap: Boolean = false) {
    val px = IntArray(w * h)

    fun set(x: Int, y: Int, c: Int) {
        if (y < 0 || y >= h) return
        val xx = if (wrap) Math.floorMod(x, w) else x
        if (xx < 0 || xx >= w) return
        px[y * w + xx] = c
    }

    fun get(x: Int, y: Int): Int {
        if (y < 0 || y >= h) return 0
        val xx = if (wrap) Math.floorMod(x, w) else x
        if (xx < 0 || xx >= w) return 0
        return px[y * w + xx]
    }

    fun rect(x0: Int, y0: Int, rw: Int, rh: Int, c: Int) {
        for (y in y0 until y0 + rh) for (x in x0 until x0 + rw) set(x, y, c)
    }

    fun hline(x0: Int, x1: Int, y: Int, c: Int) { for (x in x0 until x1) set(x, y, c) }
    fun vline(x: Int, y0: Int, y1: Int, c: Int) { for (y in y0 until y1) set(x, y, c) }

    fun disc(cx: Int, cy: Int, r: Int, c: Int) {
        if (r <= 0) { set(cx, cy, c); return }
        val rr = r * r + r
        for (dy in -r..r) for (dx in -r..r) if (dx * dx + dy * dy <= rr) set(cx + dx, cy + dy, c)
    }

    /** A line from (x0, y0) to (x1, y1), [t] dots thick. */
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, c: Int, t: Int = 1) {
        val n = max(kotlin.math.abs(x1 - x0), kotlin.math.abs(y1 - y0)).toInt().coerceAtLeast(1)
        for (i in 0..n) {
            val x = (x0 + (x1 - x0) * i / n).roundToInt()
            val y = (y0 + (y1 - y0) * i / n).roundToInt()
            for (k in 0 until t) set(x + k, y, c)
        }
    }

    /** Rows [y0]..[y1) between [x0]..[x1) in [stops], ordered-dithered between neighbouring colours (the 32-bit banding). */
    fun vGradient(y0: Int, y1: Int, stops: IntArray, x0: Int = 0, x1: Int = w, spanFrom: Int = y0, spanTo: Int = y1) {
        val span = (spanTo - spanFrom).coerceAtLeast(1)
        val last = stops.size - 1
        for (y in y0 until y1) {
            val f = ((y - spanFrom).toFloat() / span).coerceIn(0f, 1f) * last
            val i = f.toInt().coerceIn(0, (last - 1).coerceAtLeast(0))
            val frac = f - i
            for (x in x0 until x1) {
                val th = (BAYER[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f
                set(x, y, if (last == 0) stops[0] else if (frac > th) stops[i + 1] else stops[i])
            }
        }
    }

    /**
     * A ridge of hills / mountains: its top wanders around [base] − up to [amp], filled down to [bottom]; the faces
     * rising to the right catch [light], the high tops get [snow] (0 = none).
     */
    fun ridge(rnd: Random, base: Int, amp: Int, rough: Int, bottom: Int, fill: Int, light: Int, snow: Int, period: Float, p1: Float, p2: Float) {
        val tops = IntArray(w)
        var walk = 0f
        for (x in 0 until w) {
            walk = (walk + (rnd.nextInt(3) - 1) * 0.9f).coerceIn(-rough.toFloat(), rough.toFloat())
            val s = sin(x / period * 6.2832f + p1) * 0.6f + sin(x / (period * 0.43f) * 6.2832f + p2) * 0.4f
            tops[x] = (base - amp * (0.5f + 0.5f * s) + walk).roundToInt()
        }
        val snowLine = base - (amp * 0.74f).toInt()
        for (x in 0 until w) {
            val top = tops[x]
            val lit = x > 0 && tops[x - 1] > top
            for (y in top until bottom) {
                var col = fill
                if (light != 0 && lit && y < top + 7) col = light
                if (snow != 0 && top < snowLine && y < top + 2 + (snowLine - top) / 2) col = snow
                set(x, y, col)
            }
        }
    }

    /** A puffy pixel cloud: a row of discs over a shadow row, a flat-ish base. */
    fun cloud(rnd: Random, x: Int, y: Int, k: Int, white: Int, shade: Int) {
        val puffs = 3 + rnd.nextInt(3)
        val rs = IntArray(puffs) { k + 2 + rnd.nextInt(k + 2) }
        val gaps = IntArray(puffs) { rnd.nextInt(3) }
        var px0 = x
        for (i in 0 until puffs) { disc(px0, y + 2, rs[i], shade); px0 += rs[i] + gaps[i] }
        px0 = x
        for (i in 0 until puffs) { disc(px0, y - (if (i % 2 == 1) 2 else 0), rs[i] - 1, white); px0 += rs[i] + gaps[i] }
    }

    /** A leaning palm tree from its foot at ([x], [baseY]). */
    fun palm(x: Int, baseY: Int, height: Int, lean: Int, trunk: Int, trunkDark: Int, leaf: Int, leafDark: Int) {
        for (t in 0 until height) {
            val f = t.toFloat() / height
            val fx = (x + lean * f * f).roundToInt()
            val yy = baseY - t
            set(fx, yy, if (t % 3 == 0) trunkDark else trunk)
            set(fx + 1, yy, trunkDark)
        }
        val topX = x + lean
        val topY = baseY - height
        val angles = floatArrayOf(-3.0f, -2.45f, -1.85f, -1.25f, -0.65f, -0.1f)
        for (ai in angles.indices) {
            val a = angles[ai]
            val len = 9 + (ai % 3) * 2
            for (k in 0..len) {
                val fx = topX + cos(a) * k
                val fy = topY + sin(a) * k + k * k * 0.07f
                set(fx.roundToInt(), fy.roundToInt(), leaf)
                set(fx.roundToInt(), fy.roundToInt() + 1, leafDark)
            }
        }
        disc(topX, topY + 1, 1, trunkDark)
    }

    fun image(): ImageBitmap = android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()

    companion object {
        val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
    }
}

private fun c(v: Long): Int = v.toInt()

/** Lighter by [t] (0–1) toward white, keeping alpha. */
private fun lighten(argb: Int, t: Float): Int {
    val a = argb ushr 24
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    fun ch(v: Int) = (v + (255 - v) * t).roundToInt().coerceIn(0, 255)
    return (a shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}

/** Island Sky: a bright sky with clouds, blue mountains far off, the sea, a tropical shore with palms. */
private fun buildIsland(): PqScenery.Layers {
    val w = PqScenery.W
    val h = PqScenery.H
    val rnd = Random(2021)
    val b = PqPx(w, h)
    b.vGradient(0, 114, intArrayOf(c(0xFF2A52BE), c(0xFF3769D2), c(0xFF5590E4), c(0xFF83B7F0), c(0xFFBCDDF7)))
    // A pale sun high on the right, with a soft ring.
    b.disc(318, 30, 13, c(0xFFD6EBFB)); b.disc(318, 30, 9, c(0xFFFFF6D2)); b.disc(318, 30, 6, c(0xFFFFFFFF))
    b.ridge(rnd, 108, 36, 3, 114, c(0xFF7D8ED6), c(0xFF9CAEEA), c(0xFFEEF3FF), 130f, 0.4f, 2.1f)
    b.ridge(rnd, 112, 14, 2, 114, c(0xFF5F74C2), 0, 0, 64f, 1.3f, 0.2f)
    b.vGradient(114, h, intArrayOf(c(0xFF3F8EDC), c(0xFF2F78CC), c(0xFF2465B8), c(0xFF1A509E), c(0xFF143E82)))
    b.hline(0, w, 114, c(0xFFC4E8FF))
    repeat(300) {
        val y = 116 + (Math.pow(rnd.nextDouble(), 1.7) * (h - 118)).toInt()
        val x = rnd.nextInt(w)
        val len = 2 + rnd.nextInt(6)
        b.hline(x, x + len, y, if (rnd.nextInt(4) == 0) c(0xFFE9F7FF) else c(0xFF79B4EA))
    }
    val back = b.image()

    val cl = PqPx(w, h, wrap = true)
    for (i in 0 until 7) cl.cloud(rnd, i * 57 + rnd.nextInt(24), 14 + rnd.nextInt(62), 3 + rnd.nextInt(4), c(0xFFFFFFFF), c(0xFFC5D9F4))
    val clouds = cl.image()

    val n = PqPx(w, h)
    val grass = c(0xFF46A83E); val grassDark = c(0xFF2E7E2E); val dirt = c(0xFF9A6A3C); val dirtDark = c(0xFF73492A)
    val sand = c(0xFFEED9A0); val sandDark = c(0xFFD4B979); val foam = c(0xFFF4FBFF)
    // The island on the left: a grassy cliff sloping down to a beach.
    for (x in 0 until 178) {
        val top = if (x < 72) 88 + (sin(x * 0.17f) * 2f).roundToInt() else 88 + (x - 72) * (x - 72) / 255
        if (top >= 118) {
            for (y in top until 131) n.set(x, y, if ((x + y) % 5 == 0) sandDark else sand)
            n.set(x, 131, if (x % 3 == 0) foam else c(0xFFBFE6FF))
        } else {
            for (y in top until top + 3) n.set(x, y, if (y == top) c(0xFF6CC85A) else grass)
            for (y in top + 3 until 134) {
                val stripe = ((y - top) / 4 + x / 9) % 3 == 0
                n.set(x, y, if (y > 126) dirtDark else if (stripe) dirtDark else dirt)
            }
            n.set(x, top + 3, grassDark)
            n.set(x, 134, if (x % 2 == 0) foam else c(0xFFBFE6FF))
        }
    }
    n.palm(38, 89, 24, 7, c(0xFF8A5C34), c(0xFF5E3C20), c(0xFF34A04A), c(0xFF1F6E30))
    n.palm(66, 88, 30, -5, c(0xFF8A5C34), c(0xFF5E3C20), c(0xFF3BAE50), c(0xFF22763A))
    n.palm(128, 103, 20, 6, c(0xFF8A5C34), c(0xFF5E3C20), c(0xFF34A04A), c(0xFF1F6E30))
    // A small rocky islet far right, with one palm.
    for (x in 318 until 374) {
        val d = kotlin.math.abs(x - 346)
        val top = 118 + d * d / 70
        for (y in top until 126) n.set(x, y, if (y - top < 2) c(0xFF6FA06A) else if ((x + y) % 4 == 0) c(0xFF4E5E7A) else c(0xFF65769A))
        n.set(x, 126, foam)
    }
    n.palm(350, 119, 14, 4, c(0xFF8A5C34), c(0xFF5E3C20), c(0xFF34A04A), c(0xFF1F6E30))
    // Bushes along the very bottom (mostly behind the collection, for depth at the edges).
    for (i in 0 until 26) {
        val x = if (i < 14) i * 9 + rnd.nextInt(5) else 292 + (i - 14) * 9 + rnd.nextInt(5)
        n.disc(x, 170 + rnd.nextInt(6), 6 + rnd.nextInt(5), if (i % 3 == 0) c(0xFF1F6E30) else c(0xFF2A8A3A))
    }
    val near = n.image()
    val sx = IntArray(16) { 150 + rnd.nextInt(230) }
    val sy = IntArray(16) { 118 + rnd.nextInt(44) }
    return PqScenery.Layers(Features.PqTheme.ISLAND, back, clouds, near, sx, sy)
}

/** Sunset Ruins: a warm sunset with a striped sun, violet mesas, old columns and arches in silhouette. */
private fun buildSunset(): PqScenery.Layers {
    val w = PqScenery.W
    val h = PqScenery.H
    val rnd = Random(3141)
    val sky = intArrayOf(c(0xFF27124C), c(0xFF461D6C), c(0xFF77297A), c(0xFFB5437A), c(0xFFE56A52), c(0xFFF6A247), c(0xFFFFD380))
    val b = PqPx(w, h)
    b.vGradient(0, 140, sky, spanFrom = 0, spanTo = 140)
    // The striped sun.
    b.disc(272, 106, 27, c(0xFFFFB45C)); b.disc(272, 106, 23, c(0xFFFFE19A)); b.disc(272, 106, 16, c(0xFFFFF2CC))
    for (k in 0 until 5) {
        val y = 108 + k * 5
        b.vGradient(y, y + 1 + k / 2, sky, x0 = 240, x1 = 305, spanFrom = 0, spanTo = 140)
    }
    // A few early stars up high.
    repeat(40) { b.set(rnd.nextInt(w), rnd.nextInt(40), if (rnd.nextInt(3) == 0) c(0xFFFFF0D8) else c(0xFFB89AD8)) }
    b.ridge(rnd, 128, 22, 2, 140, c(0xFF6A2A5E), c(0xFF8E3E70), 0, 92f, 0.7f, 1.9f)
    b.ridge(rnd, 138, 9, 1, 142, c(0xFF4A1A48), 0, 0, 48f, 2.2f, 0.4f)
    b.vGradient(140, h, intArrayOf(c(0xFF3A1640), c(0xFF26102E), c(0xFF170A1E)))
    val back = b.image()

    val cl = PqPx(w, h, wrap = true)
    for (i in 0 until 9) {
        val x = i * 45 + rnd.nextInt(20)
        val y = 12 + rnd.nextInt(74)
        val len = 22 + rnd.nextInt(40)
        val th = 2 + rnd.nextInt(2)
        val glow = if (y > 60) c(0xFFF7B072) else c(0xFFD9799A)
        cl.rect(x, y, len, th, glow)
        cl.rect(x + 3, y + th, len - 6, 1, if (y > 60) c(0xFFC2577A) else c(0xFF8E3E7E))
        cl.rect(x + 5, y - 1, len - 12, 1, lighten(glow, 0.35f))
    }
    val clouds = cl.image()

    val n = PqPx(w, h)
    val sil = c(0xFF2A1034); val silDark = c(0xFF1C0A24); val rim = c(0xFFE9806A)
    // The ground.
    for (x in 0 until w) {
        val top = 150 + (sin(x * 0.07f) * 2f + sin(x * 0.23f)).roundToInt()
        for (y in top until h) n.set(x, y, if (y < top + 1) c(0xFF4A1E46) else sil)
    }
    fun column(x: Int, height: Int, broken: Boolean) {
        val base = 152
        val top = base - height
        n.rect(x, top, 7, height, sil)
        if (!broken) {
            n.rect(x - 2, top - 3, 11, 3, sil)
        } else {
            for (k in 0 until 7) n.vline(x + k, top - (k * 5 % 4), top, sil)
        }
        n.rect(x - 2, base - 3, 11, 3, sil)
        n.vline(x + 6, top, base - 3, rim)
        n.vline(x + 2, top + 3, base - 4, silDark)
    }
    column(22, 52, false); column(44, 40, true); column(66, 56, false); column(206, 30, true)
    // An arch.
    n.rect(118, 104, 9, 48, sil); n.rect(160, 104, 9, 48, sil)
    for (y in 76..106) for (x in 114..172) {
        val dx = x - 143; val dy = y - 106
        val d2 = dx * dx + dy * dy
        if (d2 in 17 * 17..29 * 29) n.set(x, y, sil)
    }
    for (y in 104 until 150) n.set(168, y, rim)
    for (x in 143..171) { val dy = -kotlin.math.sqrt((29 * 29 - (x - 143) * (x - 143)).toDouble().coerceAtLeast(0.0)).toInt(); n.set(x, 106 + dy, rim) }
    // A stepped temple on the right.
    for (k in 0 until 6) {
        val x0 = 312 + k * 6
        val ww = 86 - k * 12
        val y0 = 150 - (k + 1) * 8
        n.rect(x0, y0, ww, 8, sil)
        n.hline(x0, x0 + ww, y0, c(0xFF5A2450))
        n.vline(x0 + ww - 1, y0, y0 + 8, rim)
    }
    n.rect(346, 94, 18, 8, sil); n.rect(352, 88, 6, 6, sil)
    // Rubble.
    repeat(14) { val x = rnd.nextInt(w); n.rect(x, 147 + rnd.nextInt(4), 3 + rnd.nextInt(5), 2 + rnd.nextInt(3), sil) }
    val near = n.image()
    val sx = IntArray(18) { rnd.nextInt(w) }
    val sy = IntArray(18) { rnd.nextInt(150) }
    return PqScenery.Layers(Features.PqTheme.SUNSET, back, clouds, near, sx, sy)
}

/** Deep Lab: a dark navy hall — wall panels with lit windows, a perspective floor grid, tanks, a capsule, desks. */
private fun buildLab(): PqScenery.Layers {
    val w = PqScenery.W
    val h = PqScenery.H
    val rnd = Random(7)
    val b = PqPx(w, h)
    b.vGradient(0, 120, intArrayOf(c(0xFF02040F), c(0xFF050A22), c(0xFF081236), c(0xFF0B1844)))
    // Wall panels with rows of small windows.
    var py = 12
    while (py < 112) {
        var px = 4
        while (px < w) {
            val pw = 34 + rnd.nextInt(12)
            n2Panel(b, rnd, px, py, pw, 24)
            px += pw + 3
        }
        py += 27
    }
    // Conduits.
    for (k in 0 until 7) { val x = 20 + k * 57; b.rect(x, 0, 2, 118, c(0xFF0F1E4E)); b.vline(x, 0, 118, c(0xFF1A3270)) }
    // The floor and its perspective grid.
    b.vGradient(118, h, intArrayOf(c(0xFF061030), c(0xFF040A20), c(0xFF02061A)))
    b.hline(0, w, 118, c(0xFF2A5AA8))
    for (k in 1..10) b.hline(0, w, 118 + (k * k * 0.62f).toInt(), c(0xFF10306A))
    for (j in -14..14) b.line(200f + j * 6f, 118f, 200f + j * 44f, 180f, c(0xFF10306A))
    val back = b.image()

    val cl = PqPx(w, h, wrap = true)
    for (i in 0 until 10) cl.disc(i * 41 + rnd.nextInt(20), 30 + rnd.nextInt(90), 10 + rnd.nextInt(12), 0x2A2A5DA8)
    for (i in 0 until 40) cl.set(rnd.nextInt(w), rnd.nextInt(130), 0x8846D9FF.toInt())
    val clouds = cl.image()

    val n = PqPx(w, h)
    val body = c(0xFF0A1230); val edge = c(0xFF1F4C8C); val cyan = c(0xFF46D9FF); val cyanDim = c(0xFF12507A)
    // A tank on the left.
    n.disc(31, 72, 23, body); n.rect(8, 72, 47, 82, body)
    n.vline(54, 66, 154, edge); n.vline(8, 66, 154, c(0xFF0E1A44))
    n.rect(18, 88, 7, 54, cyanDim); n.rect(18, 112, 7, 30, cyan); n.rect(18, 112, 7, 1, c(0xFFC8F4FF))
    n.rect(34, 92, 12, 3, edge); n.rect(34, 100, 12, 3, edge)
    // Pipes along the floor.
    n.rect(0, 140, w, 4, c(0xFF142456)); n.hline(0, w, 140, c(0xFF2A4A8C))
    for (x in 0 until w step 40) n.rect(x, 138, 4, 8, c(0xFF1A2E66))
    // A capsule.
    n.rect(86, 58, 34, 94, body); n.disc(103, 60, 17, body)
    n.rect(92, 64, 22, 72, c(0xFF0E3A66)); n.vline(96, 68, 130, cyan); n.vline(97, 68, 100, c(0xFF8FE9FF))
    n.rect(80, 140, 46, 12, body); n.hline(80, 126, 140, edge)
    // Desks with screens on the right.
    n.rect(298, 122, 96, 30, body); n.hline(298, 394, 122, edge)
    n.rect(306, 102, 26, 16, edge); n.rect(308, 104, 22, 12, c(0xFF0E4A78))
    for (k in 0 until 4) n.hline(310, 312 + rnd.nextInt(16), 106 + k * 3, cyan)
    n.rect(338, 98, 32, 20, edge); n.rect(340, 100, 28, 16, c(0xFF0E4A78))
    for (k in 0 until 6) n.vline(343 + k * 4, 114 - rnd.nextInt(12), 115, cyan)
    n.rect(376, 106, 14, 12, edge); n.rect(378, 108, 10, 8, c(0xFF2A1250))
    // Cables from the ceiling.
    for (k in 0 until 6) { val x = 140 + k * 26 + rnd.nextInt(10); val len = 10 + rnd.nextInt(30); n.rect(x, 0, 2, len, body); n.set(x, len, cyan) }
    val near = n.image()
    val sx = IntArray(14) { if (it < 7) 300 + rnd.nextInt(90) else 10 + rnd.nextInt(110) }
    val sy = IntArray(14) { if (it < 7) 124 + rnd.nextInt(24) else 70 + rnd.nextInt(70) }
    return PqScenery.Layers(Features.PqTheme.LAB, back, clouds, near, sx, sy)
}

/** One wall panel of the lab: an outline, a dark face and (sometimes) a row or two of lit windows. */
private fun n2Panel(b: PqPx, rnd: Random, x: Int, y: Int, w: Int, h: Int) {
    b.rect(x, y, w, h, c(0xFF0A1438))
    b.hline(x, x + w, y, c(0xFF1A2E6A)); b.vline(x, y, y + h, c(0xFF15265E))
    b.hline(x, x + w, y + h - 1, c(0xFF060C26))
    if (rnd.nextInt(3) != 0) {
        val rows = 1 + rnd.nextInt(2)
        for (r in 0 until rows) {
            var xx = x + 4
            while (xx < x + w - 4) {
                val lit = rnd.nextInt(5) == 0
                b.rect(xx, y + 6 + r * 7, 3, 2, if (lit) c(0xFF3FD0FF) else c(0xFF12406E))
                xx += 5
            }
        }
    }
}

/** Ocean Depths: a deep blue sky with drifting clouds, the sea from its surface down, light rays, kelp and rocks. */
private fun buildOcean(): PqScenery.Layers {
    val w = PqScenery.W
    val h = PqScenery.H
    val rnd = Random(4242)
    val b = PqPx(w, h)
    b.vGradient(0, 58, intArrayOf(c(0xFF163786), c(0xFF2A56B2), c(0xFF4A80D4), c(0xFF7EAEEA)))
    b.hline(0, w, 58, c(0xFFD6EEFF))
    b.vGradient(59, 68, intArrayOf(c(0xFF4A8EE0), c(0xFF3672C8)))
    for (k in 0 until 60) { val x = rnd.nextInt(w); b.hline(x, x + 2 + rnd.nextInt(5), 59 + rnd.nextInt(9), c(0xFFA9D8FF)) }
    b.vGradient(68, h, intArrayOf(c(0xFF2559AA), c(0xFF1D4A95), c(0xFF163B7E), c(0xFF102E68), c(0xFF0A2252), c(0xFF06183E)))
    // Light rays slanting down from the surface.
    for (r in 0 until 6) {
        val x0 = 30 + r * 66 + rnd.nextInt(16)
        for (y in 69 until 172) {
            val cx = x0 + ((y - 69) * 0.45f).toInt()
            val half = (7 - (y - 69) / 22).coerceAtLeast(1)
            val k = (1f - (y - 69) / 103f).coerceIn(0f, 1f) * 0.16f
            for (x in cx - half..cx + half) {
                if (PqPx.BAYER[(y and 3) * 4 + (x and 3)] < 9) b.set(x, y, lighten(b.get(x, y), k))
            }
        }
    }
    b.ridge(rnd, 164, 16, 2, h, c(0xFF0A2350), c(0xFF113466), 0, 80f, 0.3f, 1.7f)
    val back = b.image()

    val cl = PqPx(w, h, wrap = true)
    for (i in 0 until 6) cl.cloud(rnd, i * 66 + rnd.nextInt(20), 12 + rnd.nextInt(30), 2 + rnd.nextInt(3), c(0xFFE8F4FF), c(0xFF9DBDE8))
    val clouds = cl.image()

    val n = PqPx(w, h)
    // The sea floor: rocks and sand.
    for (x in 0 until w) {
        val top = 168 + (sin(x * 0.05f) * 4f + sin(x * 0.17f) * 2f).roundToInt()
        for (y in top until h) n.set(x, y, if (y == top) c(0xFF1E4C7A) else if ((x * 7 + y) % 9 == 0) c(0xFF0B2448) else c(0xFF081B3C))
    }
    for (k in 0 until 7) {
        val x = rnd.nextInt(w); val r = 6 + rnd.nextInt(8)
        n.disc(x, 170, r, c(0xFF0A1F45)); n.disc(x - 2, 168, r - 3, c(0xFF12306A))
    }
    // Kelp swaying up from the floor.
    for (k in 0 until 11) {
        val x0 = if (k < 6) 6 + k * 22 + rnd.nextInt(8) else 270 + (k - 6) * 24 + rnd.nextInt(8)
        val tall = 40 + rnd.nextInt(46)
        for (t in 0 until tall) {
            val x = x0 + (sin(t * 0.18f + k) * 3f).roundToInt()
            val y = 172 - t
            n.set(x, y, c(0xFF13606E)); n.set(x + 1, y, c(0xFF0E4A58))
            if (t % 7 == 3) { n.set(x + 2, y, c(0xFF1C7A84)); n.set(x + 3, y - 1, c(0xFF1C7A84)) }
            if (t % 7 == 6) { n.set(x - 1, y, c(0xFF1C7A84)); n.set(x - 2, y - 1, c(0xFF1C7A84)) }
        }
    }
    // Coral fans.
    for (k in 0 until 5) {
        val x = 150 + k * 22 + rnd.nextInt(10)
        for (a in 0 until 7) {
            val ang = -2.6f + a * 0.35f
            for (s in 0 until 9 + rnd.nextInt(5)) n.set(x + (cos(ang) * s).roundToInt(), 170 + (sin(ang) * s).roundToInt(), if (k % 2 == 0) c(0xFF2C4A92) else c(0xFF3A3A88))
        }
    }
    val near = n.image()
    val sx = IntArray(18) { rnd.nextInt(w) }
    val sy = IntArray(18) { 59 + rnd.nextInt(8) }
    return PqScenery.Layers(Features.PqTheme.OCEAN, back, clouds, near, sx, sy)
}

/**
 * Update 202: the theme's scenery filling [modifier] (StyleBackLayer puts it behind the menu): the layers made once,
 * stretched with nearest-neighbour filtering; parallax from [PqNavHost.parallax]; slow drift and live touches from the
 * EffectClock (drawing only). Draws nothing with Voltcap's own background picked.
 */
@Composable
internal fun PocketQuestBackdrop(modifier: Modifier) {
    if (!pqOwnsBackground()) return
    val theme = Features.pqTheme.state.value
    val layers by produceState(PqScenery.cachedFor(theme), theme) {
        if (value?.theme != theme) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { PqScenery.build(theme) }
    }
    val reduced = Features.pqReducedMotion.state.value
    val animOn = Features.pqBgAnim.state.value && !reduced && !LocalLiteEffects.current && rememberAnimationsEnabled()
    val parallaxK = if (reduced) 0f else Features.pqParallax.state.value / 100f
    val pal = pqPalette()
    val fallback = remember(pal) { Brush.verticalGradient(listOf(lerp(pal.top, Color.Black, 0.2f), pal.bottom, pal.edgeDark)) }
    val shade = remember { Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.2f), 0.22f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f)) }
    val spark = remember(theme) {
        when (theme) {
            Features.PqTheme.ISLAND -> Color(0xFFFFFFFF)
            Features.PqTheme.SUNSET -> Color(0xFFFFD27A)
            Features.PqTheme.LAB -> Color(0xFF46D9FF)
            Features.PqTheme.OCEAN -> Color(0xFFCFF2FF)
        }
    }
    Canvas(modifier) {
        val l = layers
        if (l == null || l.theme != theme) { drawRect(fallback); return@Canvas }
        val t = if (animOn) EffectClock.seconds.floatValue else 0f
        val s = max(size.height / PqScenery.H, size.width / PqScenery.VIEW_W)
        val dw = (PqScenery.W * s).roundToInt()
        val dh = (PqScenery.H * s).roundToInt()
        val oy = ((size.height - dh) * 0.55f).roundToInt()
        val x0 = (size.width - dw) / 2f
        val margin = (PqScenery.W - PqScenery.VIEW_W) / 2f * s
        val p = PqNavHost.parallax.floatValue * parallaxK
        val src = IntSize(PqScenery.W, PqScenery.H)
        val dst = IntSize(dw, dh)
        // Far: barely moves.
        drawImage(l.back, IntOffset.Zero, src, IntOffset((x0 - p * margin * 0.25f).roundToInt(), oy), dst, filterQuality = FilterQuality.None)
        // Clouds: drift and wrap, a little parallax.
        val drift = ((t * 2.6f) % PqScenery.W) * s
        val cx = (x0 - p * margin * 0.5f - drift).roundToInt()
        drawImage(l.clouds, IntOffset.Zero, src, IntOffset(cx, oy), dst, filterQuality = FilterQuality.None)
        drawImage(l.clouds, IntOffset.Zero, src, IntOffset(cx + dw, oy), dst, filterQuality = FilterQuality.None)
        // Near: moves most.
        val nx = x0 - p * margin
        drawImage(l.near, IntOffset.Zero, src, IntOffset(nx.roundToInt(), oy), dst, filterQuality = FilterQuality.None)
        // The live touches, one dot each (positions from the clock: nothing allocated).
        if (animOn) {
            val dot = Size(s, s)
            when (l.theme) {
                Features.PqTheme.ISLAND -> for (i in l.spotsX.indices) {
                    val a = sin(t * 2.2f + i * 1.7f)
                    if (a > 0.6f) drawRect(spark.copy(alpha = (a - 0.6f) * 2.5f), Offset(x0 - p * margin * 0.25f + l.spotsX[i] * s, oy + l.spotsY[i] * s), dot)
                }
                Features.PqTheme.SUNSET -> for (i in l.spotsX.indices) {
                    val speed = 3f + (i % 5)
                    val vy = 150f - ((t * speed + i * 23f) % 140f)
                    val vx = l.spotsX[i] + sin(t * 0.7f + i) * 3f
                    val a = 0.35f + 0.35f * sin(t * 2f + i)
                    drawRect(spark.copy(alpha = a.coerceIn(0f, 1f)), Offset(nx + vx * s, oy + vy * s), dot)
                }
                Features.PqTheme.LAB -> for (i in l.spotsX.indices) {
                    val on = ((t * 1.3f + i * 0.37f) % 2f) < 1f
                    drawRect((if (i % 4 == 0) Color(0xFFFF5AC8) else spark).copy(alpha = if (on) 0.95f else 0.25f), Offset(nx + l.spotsX[i] * s, oy + l.spotsY[i] * s), dot)
                }
                Features.PqTheme.OCEAN -> for (i in l.spotsX.indices) {
                    if (i % 3 == 0) {
                        // Glints on the surface.
                        val a = sin(t * 2.4f + i * 1.3f)
                        if (a > 0.5f) drawRect(spark.copy(alpha = (a - 0.5f) * 2f), Offset(x0 - p * margin * 0.25f + l.spotsX[i] * s, oy + l.spotsY[i] * s), dot)
                    } else {
                        // Bubbles rising.
                        val vy = 176f - ((t * (6f + (i % 4) * 2f) + i * 29f) % 104f)
                        val vx = l.spotsX[i] + sin(t * 1.5f + i) * 2f
                        drawRect(spark.copy(alpha = 0.55f), Offset(nx + vx * s, oy + vy * s), dot)
                    }
                }
            }
        }
        drawRect(shade)
    }
}

/** Update 202: what the backdrop reads from the layout (kept out of the layout's own nav state on purpose). */
internal object PqNavHost {
    /** −1 (first game) … 1 (last game), eased: the scenery's parallax. */
    val parallax = androidx.compose.runtime.mutableFloatStateOf(0f)
}

// ---- Small pictures (layout picker, theme chips) ----

/** Update 202: [theme]'s scenery as a quick vector sketch filling the draw area (no bitmaps). */
internal fun DrawScope.pqSceneSketch(theme: Features.PqTheme) {
    val w = size.width
    val h = size.height
    when (theme) {
        Features.PqTheme.ISLAND -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF2A52BE), Color(0xFF83B7F0), Color(0xFFBCDDF7)), 0f, h * 0.62f))
            drawCircle(Color(0xFFFFF6D2), h * 0.07f, Offset(w * 0.8f, h * 0.17f))
            drawPath(Path().apply { moveTo(0f, h * 0.62f); lineTo(w * 0.15f, h * 0.42f); lineTo(w * 0.28f, h * 0.55f); lineTo(w * 0.45f, h * 0.36f); lineTo(w * 0.62f, h * 0.58f); lineTo(w * 0.8f, h * 0.45f); lineTo(w, h * 0.62f); close() }, Color(0xFF7D8ED6))
            drawRect(Brush.verticalGradient(listOf(Color(0xFF3F8EDC), Color(0xFF143E82)), h * 0.62f, h), Offset(0f, h * 0.62f), Size(w, h * 0.38f))
            drawPath(Path().apply { moveTo(0f, h * 0.5f); lineTo(w * 0.18f, h * 0.5f); lineTo(w * 0.4f, h * 0.74f); lineTo(0f, h * 0.76f); close() }, Color(0xFF46A83E))
            drawRect(Color(0xFFEED9A0), Offset(w * 0.12f, h * 0.7f), Size(w * 0.28f, h * 0.05f))
            for (i in 0..2) drawCircle(Color.White, h * (0.05f + i * 0.012f), Offset(w * (0.25f + i * 0.05f), h * 0.2f))
        }
        Features.PqTheme.SUNSET -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF27124C), Color(0xFFB5437A), Color(0xFFF6A247), Color(0xFFFFD380)), 0f, h * 0.78f))
            drawCircle(Color(0xFFFFE19A), h * 0.16f, Offset(w * 0.68f, h * 0.58f))
            for (k in 0..3) drawRect(Color(0xFFE56A52), Offset(w * 0.5f, h * (0.6f + k * 0.04f)), Size(w * 0.36f, h * 0.012f * (k + 1)))
            drawRect(Color(0xFF2A1034), Offset(0f, h * 0.8f), Size(w, h * 0.2f))
            for (i in 0..2) drawRect(Color(0xFF2A1034), Offset(w * (0.06f + i * 0.06f), h * (0.5f + (i % 2) * 0.06f)), Size(w * 0.025f, h * 0.32f))
            drawPath(Path().apply { moveTo(w * 0.75f, h * 0.82f); lineTo(w * 0.8f, h * 0.62f); lineTo(w * 0.92f, h * 0.62f); lineTo(w * 0.97f, h * 0.82f); close() }, Color(0xFF2A1034))
        }
        Features.PqTheme.LAB -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF02040F), Color(0xFF0B1844)), 0f, h * 0.66f))
            for (r in 0..2) for (k in 0..6) drawRect(Color(0xFF0E1C4E), Offset(w * (0.02f + k * 0.14f), h * (0.08f + r * 0.18f)), Size(w * 0.12f, h * 0.15f))
            for (k in 0..8) drawRect(Color(0xFF3FD0FF).copy(alpha = 0.7f), Offset(w * (0.05f + k * 0.11f), h * (0.13f + (k % 3) * 0.18f)), Size(w * 0.02f, h * 0.02f))
            drawRect(Color(0xFF040A20), Offset(0f, h * 0.66f), Size(w, h * 0.34f))
            for (k in 1..4) drawRect(Color(0xFF10306A), Offset(0f, h * (0.66f + k * k * 0.02f)), Size(w, h * 0.008f))
            drawRect(Color(0xFF0A1230), Offset(w * 0.04f, h * 0.38f), Size(w * 0.12f, h * 0.46f))
            drawRect(Color(0xFF46D9FF), Offset(w * 0.07f, h * 0.6f), Size(w * 0.02f, h * 0.16f))
        }
        Features.PqTheme.OCEAN -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF163786), Color(0xFF7EAEEA)), 0f, h * 0.32f), Offset.Zero, Size(w, h * 0.32f))
            drawRect(Brush.verticalGradient(listOf(Color(0xFF2559AA), Color(0xFF06183E)), h * 0.32f, h), Offset(0f, h * 0.32f), Size(w, h * 0.68f))
            drawRect(Color(0xFFD6EEFF), Offset(0f, h * 0.32f), Size(w, h * 0.01f))
            for (i in 0..1) drawCircle(Color(0xFFE8F4FF), h * 0.05f, Offset(w * (0.3f + i * 0.4f), h * 0.12f))
            for (k in 0..4) drawRect(Color(0xFF13606E), Offset(w * (0.04f + k * 0.05f), h * (0.62f - (k % 2) * 0.1f)), Size(w * 0.012f, h * 0.4f))
            drawRect(Color(0xFF081B3C), Offset(0f, h * 0.9f), Size(w, h * 0.1f))
        }
    }
}

/**
 * Update 202: the layout picker's picture of Pocket Quest (PresetPreview): the scenery, the top bar with its tabs, the
 * big case with ‹ ›, the gold-edged panel with Play, and the collection along the bottom with the one you're on framed.
 */
internal fun DrawScope.pqPreview() {
    val pal = pqPaletteFor(Features.pqTheme.value, Features.pqAccent.value, Color(0xFF7C9CFF))
    val w = size.width
    val h = size.height
    pqSceneSketch(Features.pqTheme.value)
    fun box(x: Float, y: Float, bw: Float, bh: Float, fill: Color, edge: Color?, cut: Float = 0.012f) {
        val p = pqChamferPath(bw * w, bh * h, cut * w, 0f)
        drawContext.transform.translate(x * w, y * h)
        if (edge != null) drawPath(p, edge)
        val inner = pqChamferPath(bw * w, bh * h, cut * w, (0.004f * w).coerceAtLeast(1f))
        drawPath(inner, fill)
        drawContext.transform.translate(-x * w, -y * h)
    }
    // Top bar and its tabs.
    drawRect(pal.bar.copy(alpha = 0.92f), Offset.Zero, Size(w, h * 0.09f))
    drawRect(pal.accent, Offset(0f, h * 0.09f), Size(w, h * 0.006f))
    drawRect(Color.White.copy(alpha = 0.85f), Offset(w * 0.02f, h * 0.035f), Size(w * 0.09f, h * 0.025f))
    for (i in 0..6) box(0.15f + i * 0.075f, 0.02f, 0.065f, 0.05f, if (i == 0) pal.accent else pal.top, null)
    // The big case with ‹ ›.
    drawRect(Color.Black.copy(alpha = 0.25f), Offset(w * 0.12f, h * 0.68f), Size(w * 0.2f, h * 0.02f))
    drawRect(Color(0xFF3A8F5A), Offset(w * 0.12f, h * 0.15f), Size(w * 0.2f, h * 0.53f))
    drawRect(Color.White.copy(alpha = 0.3f), Offset(w * 0.14f, h * 0.2f), Size(w * 0.16f, h * 0.1f))
    for (right in listOf(false, true)) {
        val ax = if (right) w * 0.35f else w * 0.075f
        drawPath(Path().apply {
            if (right) { moveTo(ax, h * 0.37f); lineTo(ax + w * 0.02f, h * 0.41f); lineTo(ax, h * 0.45f) }
            else { moveTo(ax + w * 0.02f, h * 0.37f); lineTo(ax, h * 0.41f); lineTo(ax + w * 0.02f, h * 0.45f) }
            close()
        }, pal.accent)
    }
    // The info panel with Play.
    box(0.42f, 0.14f, 0.55f, 0.52f, pal.top.copy(alpha = 0.95f), pal.accent, 0.02f)
    drawRect(Color.White, Offset(w * 0.45f, h * 0.2f), Size(w * 0.24f, h * 0.045f))
    for (r in 0..2) drawRect(Color.White.copy(alpha = 0.5f), Offset(w * 0.45f, h * (0.3f + r * 0.05f)), Size(w * (0.24f - r * 0.03f), h * 0.02f))
    box(0.45f, 0.53f, 0.24f, 0.08f, pal.accent, pal.edgeDark)
    drawRect(Color(0xFF6FB36A), Offset(w * 0.72f, h * 0.2f), Size(w * 0.22f, h * 0.24f))
    for (k in 0..2) drawRect(Color(0xFF5A8FD6), Offset(w * (0.72f + k * 0.075f), h * 0.48f), Size(w * 0.065f, h * 0.1f))
    // The collection.
    box(0.02f, 0.72f, 0.96f, 0.24f, pal.deep.copy(alpha = 0.92f), pal.accentDeep, 0.015f)
    for (i in -3..3) {
        val big = i == 0
        val bw = if (big) 0.11f else 0.09f
        val bh = if (big) 0.2f else 0.16f
        val x = 0.5f + i * 0.12f - bw / 2f
        val y = if (big) 0.735f else 0.765f
        if (big) box(x - 0.008f, y - 0.012f, bw + 0.016f, bh + 0.024f, pal.accent, null)
        drawRect(Color.hsv(((i + 4) * 47 % 360).toFloat(), 0.55f, 0.6f), Offset(x * w, y * h), Size(bw * w, bh * h))
    }
}
