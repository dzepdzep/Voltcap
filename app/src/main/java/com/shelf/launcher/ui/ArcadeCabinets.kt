package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.shelf.launcher.data.Features
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Update 347: Arcade cabinets 2.0 — the machines as solids. Each build is its side profile (the shape you see from the
 * side, in metres, the way the real machines are drawn up), extruded across its width and seen a little from the side
 * and above: one affine view per machine (2.5D — no 3D engine, a few paths).
 *
 * Update 348 (Toni: "believable physical arcade machines, not identical boxes"): each build is now two profiles — the
 * side panels' outline ([CabModel.side]) and the front between them ([CabModel.pts]), set back by the panels' thickness
 * ([CabModel.inset]) — so the screen sits recessed between the panels, the control panel juts out past them, the
 * marquee box overhangs, and the panels' front edges (T-moulding, a rounded plastic edge, an aluminium LED rail, a
 * plywood edge) show. Light: ambient + one key light, no washing out to white; each face darkens towards the floor;
 * contact shadows in layers; the glass of the screen recessed in its bezel with a reflection.
 *
 * Proportions, from photos and listings of the real machines (original drawings — no names, logos or artwork of theirs):
 * - Classic upright (the early-1990s American fighting-game upright; ~143 kg, 25" monitor): ~0.66 m wide, 0.87 deep,
 *   1.87 tall; black laminate with full side art and T-moulding; a deep side panel whose back edge slants in towards the
 *   top; a vertical front with the coin door, the control panel jutting ~20 cm past the panels at ~1 m, the screen
 *   recessed and tilted back ~22°, an angled speaker panel, and the marquee box overhanging at the top.
 * - Japanese candy cabinet (early-1990s sit-down cabinets: fibreglass / plastic shell, white, a black monitor mask): ~0.73
 *   × 0.86 × 1.50 m; one rounded shell (no separate side panels — rounded edges), a low wide deck at seated height, the
 *   lower front stepping back for your knees, a big screen tilted back ~31°, a lit billboard header and a rounded top.
 * - Neo-retro (Voltcap's own, after late-1990s / 2000s Japanese cabinets): ~0.78 × 0.90 × 1.70 m; angular, the lower
 *   body leaning forward, a near-upright big screen (~7°), speakers under a header that juts forward, slim aluminium side
 *   frames with an LED rail.
 * - Bartop: ~0.52 × 0.57 × 0.68 m on a wooden counter — plywood sides, one player, no coin door or kick plate.
 */

/** What a face of the cabinet is (each edge of the front profile, swept across the width between the side panels). */
internal enum class CabRole { BASE, KICK, FRONT, DOOR, UNDER, LIP, DECK, SCREEN, HOOD_UNDER, SPEAKER, MARQUEE, TOP, BACK,
    /** The bartop's counter: its front and edge, and its top in front of the machine. */
    STAND, COUNTER,
    /** Update 348: the plastic mask round the screen (below / above the glass). */
    BEZEL }

internal enum class CabBuild { UPRIGHT, CANDY, NEO, BARTOP }

/** Update 348: what the side panels' front edges are. */
internal enum class CabEdge {
    /** Plastic T-moulding in the trim colour (a highlight down its round middle). */
    TMOLD,
    /** No panels — a rounded plastic shell: its edges catch the light. */
    ROUND,
    /** An aluminium frame with a lit strip. */
    LED,
    /** Plywood: the layered edge of the board. */
    PLY
}

/** Update 348: a plain box under / beside a machine (the bartop's counter), metres; [top] its top's colour. */
internal class CabBox(val u0: Float, val u1: Float, val f0: Float, val f1: Float, val y0: Float, val y1: Float, val color: Color, val top: Color)

/**
 * A cabinet: [w] wide, [d] deep, [h] tall (metres). [pts]: the front profile as (forward, up) points going round
 * counter-clockwise (forward 0 = its back); [roles][i] what the face from pts[i] to pts[i+1] is. Update 348: [side] the side
 * panels' outline (counter-clockwise too; the front profile where there are no panels), [inset] how far the front sits
 * in from each side (the panels' thickness), [edge] what the panels' edges are, [boxes] what it stands on.
 */
internal class CabModel(
    val build: CabBuild, val w: Float, val d: Float, val h: Float, val pts: List<Offset>, val roles: List<CabRole>,
    val side: List<Offset> = pts, val inset: Float = 0f, val edge: CabEdge = CabEdge.TMOLD, val boxes: List<CabBox> = emptyList()
) {
    /** Two players' controls (one on a bartop). */
    val players: Int get() = if (build == CabBuild.BARTOP) 1 else 2
    fun edge(r: CabRole): Int = roles.indexOf(r)
    /** Where the machine itself stands (the bartop's counter top; else the floor). */
    val foot: Float get() = boxes.maxOfOrNull { it.y1 } ?: 0f
}

private class ProfileBuilder {
    val pts = ArrayList<Offset>(); val roles = ArrayList<CabRole>()
    fun p(f: Float, y: Float, r: CabRole) { pts += Offset(f, y); roles += r }
    /** A curved stretch to (f2, y2) through the control point (cf, cy), as [n] faces of role [r]. */
    fun curve(f1: Float, y1: Float, cf: Float, cy: Float, f2: Float, y2: Float, r: CabRole, n: Int = 5) {
        for (i in 0 until n) {
            val t = i / n.toFloat(); val a = (1 - t) * (1 - t); val b = 2 * (1 - t) * t; val c = t * t
            p(a * f1 + b * cf + c * f2, a * y1 + b * cy + c * y2, r)
        }
    }
}

/** A plain outline (counter-clockwise points), with curves. */
private class OutlineBuilder {
    val pts = ArrayList<Offset>()
    fun p(f: Float, y: Float) { pts += Offset(f, y) }
    fun curve(f1: Float, y1: Float, cf: Float, cy: Float, f2: Float, y2: Float, n: Int = 5) {
        for (i in 0 until n) {
            val t = i / n.toFloat(); val a = (1 - t) * (1 - t); val b = 2 * (1 - t) * t; val c = t * t
            p(a * f1 + b * cf + c * f2, a * y1 + b * cy + c * y2)
        }
    }
}

private fun outline(block: OutlineBuilder.() -> Unit): List<Offset> = OutlineBuilder().apply(block).pts

private fun profile(
    build: CabBuild, w: Float, d: Float, h: Float, side: List<Offset>? = null, inset: Float = 0f, edge: CabEdge,
    boxes: List<CabBox> = emptyList(), block: ProfileBuilder.() -> Unit
): CabModel {
    val b = ProfileBuilder().apply(block)
    return CabModel(build, w, d, h, b.pts, b.roles, side ?: b.pts, inset, edge, boxes)
}

/** The classic upright: a deep side panel, the front set 2 cm in between the panels, the control panel jutting out. */
internal val CabUpright: CabModel = profile(
    CabBuild.UPRIGHT, 0.66f, 0.87f, 1.87f, inset = 0.022f, edge = CabEdge.TMOLD,
    side = outline {
        p(0f, 0f); p(0.665f, 0f)
        // Up the front to the control panel, back along the screen, out under the marquee, the top, the slanting back.
        p(0.665f, 0.90f); p(0.655f, 1.00f); p(0.535f, 1.45f); p(0.675f, 1.50f); p(0.685f, 1.87f)
        p(0.22f, 1.87f); p(0f, 1.30f)
    }
) {
    p(0.03f, 0f, CabRole.BASE)
    p(0.645f, 0f, CabRole.KICK)
    p(0.645f, 0.10f, CabRole.DOOR)
    p(0.645f, 0.86f, CabRole.UNDER)
    p(0.85f, 0.94f, CabRole.LIP)
    p(0.87f, 1.00f, CabRole.DECK)
    p(0.60f, 1.05f, CabRole.BEZEL)
    p(0.585f, 1.11f, CabRole.SCREEN)
    p(0.45f, 1.45f, CabRole.HOOD_UNDER)
    p(0.655f, 1.51f, CabRole.MARQUEE)
    p(0.665f, 1.84f, CabRole.TOP)
    p(0.23f, 1.84f, CabRole.BACK)
    p(0.03f, 1.32f, CabRole.BACK)
}

/** The candy cabinet: one rounded shell, a low deck, the big tilted screen under its billboard header. */
internal val CabCandy: CabModel = profile(CabBuild.CANDY, 0.73f, 0.86f, 1.50f, edge = CabEdge.ROUND) {
    p(0f, 0f, CabRole.BASE)
    p(0.58f, 0f, CabRole.KICK)
    // The lower front stepping back for your knees, then curving out under the deck.
    p(0.58f, 0.07f, CabRole.FRONT)
    curve(0.50f, 0.50f, 0.52f, 0.67f, 0.82f, 0.70f, CabRole.UNDER, 4)
    p(0.82f, 0.70f, CabRole.LIP)
    p(0.86f, 0.75f, CabRole.DECK)
    p(0.64f, 0.79f, CabRole.BEZEL)
    p(0.62f, 0.84f, CabRole.SCREEN)
    p(0.40f, 1.20f, CabRole.BEZEL)
    p(0.41f, 1.25f, CabRole.HOOD_UNDER)
    p(0.50f, 1.28f, CabRole.MARQUEE)
    // The rounded top running back.
    curve(0.47f, 1.44f, 0.41f, 1.51f, 0.24f, 1.50f, CabRole.TOP, 5)
    p(0.24f, 1.50f, CabRole.TOP)
    p(0.06f, 1.46f, CabRole.BACK)
    p(0f, 1.30f, CabRole.BACK)
}

/** The neo-retro machine: angular, leaning forward, a near-upright screen, slim aluminium side frames. */
internal val CabNeo: CabModel = profile(
    CabBuild.NEO, 0.78f, 0.90f, 1.72f, inset = 0.03f, edge = CabEdge.LED,
    side = outline {
        p(0f, 0f); p(0.585f, 0f); p(0.665f, 0.70f); p(0.645f, 0.84f); p(0.60f, 1.42f); p(0.745f, 1.49f); p(0.76f, 1.66f)
        p(0.69f, 1.72f); p(0.12f, 1.72f); p(0f, 1.52f)
    }
) {
    p(0.03f, 0f, CabRole.BASE)
    p(0.555f, 0f, CabRole.KICK)
    p(0.56f, 0.06f, CabRole.FRONT)
    p(0.635f, 0.68f, CabRole.UNDER)
    p(0.88f, 0.76f, CabRole.LIP)
    p(0.90f, 0.82f, CabRole.DECK)
    p(0.645f, 0.86f, CabRole.BEZEL)
    p(0.625f, 0.92f, CabRole.SCREEN)
    p(0.565f, 1.44f, CabRole.HOOD_UNDER)
    p(0.715f, 1.49f, CabRole.SPEAKER)
    p(0.735f, 1.58f, CabRole.MARQUEE)
    p(0.725f, 1.69f, CabRole.TOP)
    p(0.12f, 1.70f, CabRole.BACK)
    p(0.03f, 1.52f, CabRole.BACK)
}

/** The bartop's counter top (a game table's height, Update 348 — at a bar's it stood small on a tall box): the machine stands on it. */
private const val COUNTER_TOP = 0.80f
private val CounterWood = Color(0xFF5B3B24)
private val CounterTop = Color(0xFF7A5232)

/** The bartop: a small plywood machine standing on a wooden counter (wider and deeper than it). */
internal val CabBartop: CabModel = profile(
    CabBuild.BARTOP, 0.52f, 0.70f, 1.48f, inset = 0.018f, edge = CabEdge.PLY,
    boxes = listOf(CabBox(-0.14f, 0.66f, -0.06f, 0.70f, 0f, COUNTER_TOP, CounterWood, CounterTop)),
    side = outline {
        p(0f, COUNTER_TOP); p(0.50f, COUNTER_TOP); p(0.50f, 0.84f); p(0.455f, 0.92f); p(0.33f, 1.25f); p(0.425f, 1.30f)
        p(0.43f, 1.48f); p(0.05f, 1.48f); p(0f, 1.40f)
    }
) {
    p(0.02f, COUNTER_TOP, CabRole.BASE)
    p(0.50f, COUNTER_TOP, CabRole.LIP)
    p(0.53f, 0.84f, CabRole.DECK)
    p(0.445f, 0.89f, CabRole.BEZEL)
    p(0.435f, 0.93f, CabRole.SCREEN)
    p(0.30f, 1.25f, CabRole.HOOD_UNDER)
    p(0.405f, 1.30f, CabRole.MARQUEE)
    p(0.41f, 1.46f, CabRole.TOP)
    p(0.05f, 1.46f, CabRole.BACK)
    p(0.02f, 1.40f, CabRole.BACK)
}

internal fun cabModel(b: CabBuild): CabModel = when (b) {
    CabBuild.UPRIGHT -> CabUpright; CabBuild.CANDY -> CabCandy; CabBuild.NEO -> CabNeo; CabBuild.BARTOP -> CabBartop
}

/** The build for a game: the one picked, or (Mixed) one by its console — handhelds on bartops, arcade boards in candy cabinets. */
internal fun cabBuildFor(pick: Features.ArCab, platform: String?): CabBuild = when (pick) {
    Features.ArCab.UPRIGHT -> CabBuild.UPRIGHT
    Features.ArCab.CANDY -> CabBuild.CANDY
    Features.ArCab.NEO -> CabBuild.NEO
    Features.ArCab.BARTOP -> CabBuild.BARTOP
    Features.ArCab.MIXED -> {
        val p = (platform ?: "").uppercase()
        when {
            p in setOf("ARCADE", "MAME", "FBNEO", "FBA", "NEOGEO", "NEO GEO", "CPS1", "CPS2", "CPS3", "NAOMI", "ATOMISWAVE") || p.startsWith("MAME") -> CabBuild.CANDY
            p in setOf("GB", "GBC", "GBA", "NDS", "3DS", "PSP", "PSVITA", "VITA", "NGP", "NGPC", "WS", "WSC", "GG", "LYNX", "APP", "ANDROID") -> CabBuild.BARTOP
            p in setOf("PS2", "PS3", "XBOX", "X360", "GC", "NGC", "WII", "WIIU", "DC", "SWITCH", "PSX", "PS1", "SATURN", "N64") -> CabBuild.NEO
            else -> CabBuild.UPRIGHT
        }
    }
}

/** How a machine is seen: turned [yawDeg] (positive: its right side shows), from [elevDeg] above. */
internal data class CabView(val yawDeg: Float, val elevDeg: Float)

/** Update 348: the outward normal (forward, up) of the edge a → b of a counter-clockwise profile. */
private fun normalOf(a: Offset, b: Offset): Offset {
    val df = b.x - a.x; val dy = b.y - a.y
    val len = hypot(df, dy).coerceAtLeast(1e-4f)
    return Offset(dy / len, -df / len)
}

/**
 * A machine placed in a box of [size] px: [map] puts its points (across, forward, up — metres) on the box. Seen
 * orthographically from the side the view says, scaled to fit the box, standing on its bottom edge, centred.
 */
internal class CabFrame(val m: CabModel, val view: CabView, val size: Size, fill: Float = 1f) {
    private val cy = cos(Math.toRadians(view.yawDeg.toDouble())).toFloat()
    private val sy = sin(Math.toRadians(view.yawDeg.toDouble())).toFloat()
    private val se = sin(Math.toRadians(view.elevDeg.toDouble())).toFloat()
    private val ce = cos(Math.toRadians(view.elevDeg.toDouble())).toFloat()
    val scale: Float
    private val ox: Float
    private val oy: Float

    init {
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        fun take(u: Float, f: Float, y: Float) { val x = rx(u, f); val yy = ry(f, y); minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, yy); maxY = max(maxY, yy) }
        for (u in floatArrayOf(0f, m.w)) { for (p in m.pts) take(u, p.x, p.y); for (p in m.side) take(u, p.x, p.y) }
        for (b in m.boxes) for (u in floatArrayOf(b.u0, b.u1)) for (f in floatArrayOf(b.f0, b.f1)) for (y in floatArrayOf(b.y0, b.y1)) take(u, f, y)
        val bw = maxX - minX; val bh = maxY - minY
        scale = min(size.width / bw, size.height / bh) * fill
        ox = (size.width - bw * scale) / 2f - minX * scale
        oy = size.height - maxY * scale
    }

    private fun rx(u: Float, f: Float) = u * cy + (m.d - f) * sy
    private fun ry(f: Float, y: Float) = -y * ce - (m.d - f) * se

    fun map(u: Float, f: Float, y: Float): Offset = Offset(ox + rx(u, f) * scale, oy + ry(f, y) * scale)

    /** The side that shows (across = its width, or 0). */
    val nearU: Float get() = if (view.yawDeg >= 0f) m.w else 0f
    /** The other side. */
    val farU: Float get() = if (view.yawDeg >= 0f) 0f else m.w
    /** Update 348: the inner face of a side panel (the front's edge on that side). */
    fun innerOf(u: Float): Float = if (u > m.w / 2f) m.w - m.inset else m.inset

    /** The profile's point [i] on the face at [u]. */
    fun at(u: Float, i: Int): Offset = m.pts[i % m.pts.size].let { map(u, it.x, it.y) }

    /** A point on face [i]: [t] along it (0 = its first point), [s] across the front between the panels (0 = left, 1 = right). */
    fun onFace(i: Int, t: Float, s: Float): Offset {
        val a = m.pts[i]; val b = m.pts[(i + 1) % m.pts.size]
        return map(m.inset + (m.w - 2f * m.inset) * s, a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }

    /** Whether the edge a → b of a profile faces you (its outward side towards the eye). */
    fun facesEdge(a: Offset, b: Offset): Boolean { val n = normalOf(a, b); return n.x * ce + n.y * se > 0.02f }

    /** Whether face [i] faces you. */
    fun faces(i: Int): Boolean = facesEdge(m.pts[i], m.pts[(i + 1) % m.pts.size])

    /** How lit the edge a → b is: ambient plus the key light from the front, above. 0…1. */
    fun lightEdge(a: Offset, b: Offset): Float { val n = normalOf(a, b); return (0.22f + 0.78f * (n.x * 0.55f + n.y * 0.83f).coerceAtLeast(0f)).coerceIn(0f, 1f) }

    /** How lit face [i] is. */
    fun light(i: Int): Float = lightEdge(m.pts[i], m.pts[(i + 1) % m.pts.size])

    /** The screen's glass inside its bezel: top-left, top-right, bottom-left. */
    fun screenQuad(inset: Float = 0.1f): Triple<Offset, Offset, Offset> {
        val i = m.edge(CabRole.SCREEN)
        return Triple(onFace(i, 1f - inset, inset), onFace(i, 1f - inset, 1f - inset), onFace(i, inset * 0.9f, inset))
    }

    /** The marquee's sign: top-left, top-right, bottom-left. */
    fun marqueeQuad(): Triple<Offset, Offset, Offset> {
        val i = m.edge(CabRole.MARQUEE)
        val inset = 0.05f
        return Triple(onFace(i, 1f - 0.1f, inset), onFace(i, 1f - 0.1f, 1f - inset), onFace(i, 0.1f, inset))
    }

    /** The bounds of the whole machine (px). */
    fun bounds(): Rect {
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        fun take(o: Offset) { minX = min(minX, o.x); maxX = max(maxX, o.x); minY = min(minY, o.y); maxY = max(maxY, o.y) }
        for (u in floatArrayOf(0f, m.w)) { for (p in m.pts) take(map(u, p.x, p.y)); for (p in m.side) take(map(u, p.x, p.y)) }
        for (b in m.boxes) for (u in floatArrayOf(b.u0, b.u1)) for (f in floatArrayOf(b.f0, b.f1)) for (y in floatArrayOf(b.y0, b.y1)) take(map(u, f, y))
        return Rect(minX, minY, maxX, maxY)
    }
}

/** The ratio width / height a machine takes on screen seen as [view]. */
internal fun cabAspect(m: CabModel, view: CabView): Float {
    val f = CabFrame(m, view, Size(1000f, 1000f))
    val b = f.bounds()
    return (b.width / b.height).coerceIn(0.2f, 2f)
}

/**
 * A machine's paint: front [body], [side] panels, the bright [trim] (moulding / lit strip / stripe), the [marquee]'s light,
 * the control [deck]; Update 348: the panels' [edge] colour, the plastic [mask] round the screen.
 */
internal class CabPaint(val body: Color, val side: Color, val trim: Color, val marquee: Color, val deck: Color, val edge: Color = trim, val mask: Color = Color(0xFF0C0D10))

/** The paint of [build] in the colour [c] (the game's, or the theme's). Each build has its own scheme, as the real ones. */
internal fun cabPaint(build: CabBuild, c: Color): CabPaint {
    val rich = richArcade(c)
    return when (build) {
        // Black laminate front, black side panels (the art printed on them), T-moulding in the game's colour.
        CabBuild.UPRIGHT -> CabPaint(Color(0xFF1B1C21), lerp(Color(0xFF1D1E23), rich, 0.16f), rich, rich, Color(0xFF232429), edge = lerp(rich, Color.Black, 0.12f))
        // Warm white plastic shell (not paper white: it's lit, not glowing), a grey deck, a black monitor mask.
        CabBuild.CANDY -> CabPaint(Color(0xFFD3CFC5), Color(0xFFCBC7BD), rich, rich, Color(0xFF34363C), edge = Color(0xFFE6E2D8), mask = Color(0xFF111215))
        // Gunmetal body, brushed aluminium side frames, the colour on its LED rail.
        CabBuild.NEO -> CabPaint(Color(0xFF24272E), Color(0xFF8D939C), rich, rich, Color(0xFF15161A), edge = Color(0xFFB4BAC3))
        // Painted plywood sides in the game's colour, a black front, the board's pale edge.
        CabBuild.BARTOP -> CabPaint(Color(0xFF1E1F23), lerp(rich, Color.Black, 0.42f), rich, rich, Color(0xFF141518), edge = Color(0xFFCDB28A))
    }
}

/** A bright, rich version of [c]: the cabinets' strips and the marquee light. */
internal fun richArcade(c: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()), hsv)
    hsv[1] = hsv[1].coerceIn(0.5f, 0.88f)
    hsv[2] = 0.92f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun quad(a: Offset, b: Offset, c: Offset, d: Offset) = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }

/**
 * Update 348: a surface's colour under [k] light (0…1): ambient + the key light, never past the paint's own colour (a
 * white cabinet stays a lit white — its edges and shading readable — instead of glowing).
 */
internal fun litColor(c: Color, k: Float): Color {
    val f = 0.26f + 0.74f * k.coerceIn(0f, 1f)
    return Color(c.red * f, c.green * f, c.blue * f, c.alpha)
}

/** A polygon at across = [u] through the profile points [pts]. */
private fun CabFrame.polygon(u: Float, pts: List<Offset>): Path = Path().apply {
    pts.forEachIndexed { i, p -> val o = map(u, p.x, p.y); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
    close()
}

/** A face filled with [c], a little lighter at its top end and darker at its lower end (the room's light from above). */
private fun DrawScope.fillFace(face: Path, a: Offset, b: Offset, c: Color) {
    val (hi, lo) = if (a.y <= b.y) a to b else b to a
    if (lo.y - hi.y < 2f) { drawPath(face, c); return }
    drawPath(face, Brush.linearGradient(listOf(lerp(c, Color.White, 0.035f), lerp(c, Color.Black, 0.16f)), start = hi, end = lo))
}

/**
 * Contact shadow of a footprint (forward [f0]…[f1], across [u0]…[u1], at height [y]): three layers, tight and dark at the
 * base, wide and faint further out — the machine stands on the floor instead of floating over it.
 */
private fun DrawScope.contactShadow(f: CabFrame, u0: Float, u1: Float, f0: Float, f1: Float, y: Float, strength: Float) {
    for ((grow, a) in listOf(0.20f to 0.10f, 0.08f to 0.20f, 0.012f to 0.42f)) {
        val p = quad(f.map(u0 - grow, f0 - grow, y), f.map(u1 + grow, f0 - grow, y), f.map(u1 + grow, f1 + grow * 1.4f, y), f.map(u0 - grow, f1 + grow * 1.4f, y))
        drawPath(p, Color.Black.copy(alpha = a * strength))
    }
}

/** The counter (or any box) a machine stands on: its top, front and the side that shows. */
private fun DrawScope.drawBox(f: CabFrame, b: CabBox, dim: Float) {
    val near = if (f.view.yawDeg >= 0f) b.u1 else b.u0
    val k = 1f - dim * 0.55f
    contactShadow(f, b.u0, b.u1, b.f0, b.f1, b.y0, 1f)
    // Front.
    val front = quad(f.map(b.u0, b.f1, b.y1), f.map(b.u1, b.f1, b.y1), f.map(b.u1, b.f1, b.y0), f.map(b.u0, b.f1, b.y0))
    fillFace(front, f.map(b.u0, b.f1, b.y1), f.map(b.u0, b.f1, b.y0), litColor(b.color, 0.62f * k))
    // Panelling on the front: a few vertical boards.
    for (s in 1 until 6) {
        val u = b.u0 + (b.u1 - b.u0) * s / 6f
        drawLine(Color.Black.copy(alpha = 0.22f), f.map(u, b.f1, b.y1 - 0.04f), f.map(u, b.f1, b.y0 + 0.08f), (f.scale * 0.003f).coerceAtLeast(1f))
    }
    // The near side.
    val side = quad(f.map(near, b.f0, b.y1), f.map(near, b.f1, b.y1), f.map(near, b.f1, b.y0), f.map(near, b.f0, b.y0))
    drawPath(side, litColor(b.color, 0.42f * k))
    // The top (its edge catching the light) and a kick at its foot.
    val top = quad(f.map(b.u0, b.f0, b.y1), f.map(b.u1, b.f0, b.y1), f.map(b.u1, b.f1, b.y1), f.map(b.u0, b.f1, b.y1))
    drawPath(top, litColor(b.top, 0.9f * k))
    drawLine(Color.White.copy(alpha = 0.22f * k), f.map(b.u0, b.f1, b.y1), f.map(b.u1, b.f1, b.y1), (f.scale * 0.005f).coerceAtLeast(1f))
    drawPath(quad(f.map(b.u0, b.f1, 0.08f), f.map(b.u1, b.f1, 0.08f), f.map(b.u1, b.f1, b.y0), f.map(b.u0, b.f1, b.y0)), Color.Black.copy(alpha = 0.45f))
}

/** The side panels' front edges between across = [ua] and [ub] (T-moulding, a rounded edge, an LED rail, plywood). */
private fun DrawScope.drawPanelEdges(f: CabFrame, paint: CabPaint, ua: Float, ub: Float, lit: Float, dim: Float, nearSide: Boolean) {
    val m = f.m
    val pts = m.side
    val n = pts.size
    val stroke = (f.scale * 0.004f).coerceAtLeast(1f)
    for (i in 0 until n) {
        val a = pts[i]; val b = pts[(i + 1) % n]
        if (!f.facesEdge(a, b)) continue
        val face = quad(f.map(ua, a.x, a.y), f.map(ub, a.x, a.y), f.map(ub, b.x, b.y), f.map(ua, b.x, b.y))
        val k = f.lightEdge(a, b) * (1f - dim * 0.55f)
        when (m.edge) {
            CabEdge.TMOLD -> {
                drawPath(face, litColor(paint.edge, 0.55f + 0.45f * k))
                // The moulding is round: a highlight down its middle.
                drawLine(Color.White.copy(alpha = 0.18f * k), f.map((ua + ub) / 2f, a.x, a.y), f.map((ua + ub) / 2f, b.x, b.y), stroke)
            }
            CabEdge.ROUND -> {
                // The shell's rounded edge: brighter where it turns to the light, darker as it turns away.
                val c = if (nearSide) lerp(litColor(paint.edge, k), litColor(paint.side, 0.5f), 0.45f) else litColor(paint.edge, k * 0.95f)
                drawPath(face, c)
            }
            CabEdge.LED -> {
                drawPath(face, litColor(paint.edge, 0.5f + 0.5f * k))
                // The lit strip along the frame.
                val glowA = (0.35f + 0.65f * lit) * (1f - dim * 0.7f)
                drawLine(paint.trim.copy(alpha = 0.22f * glowA), f.map((ua + ub) / 2f, a.x, a.y), f.map((ua + ub) / 2f, b.x, b.y), stroke * 4f)
                drawLine(lerp(paint.trim, Color.White, 0.35f).copy(alpha = glowA), f.map((ua + ub) / 2f, a.x, a.y), f.map((ua + ub) / 2f, b.x, b.y), stroke * 1.2f)
            }
            CabEdge.PLY -> {
                drawPath(face, litColor(paint.edge, 0.5f + 0.5f * k))
                // The board's plies.
                for (s in 1..2) {
                    val u = ua + (ub - ua) * s / 3f
                    drawLine(Color(0xFF6B4E2E).copy(alpha = 0.55f), f.map(u, a.x, a.y), f.map(u, b.x, b.y), stroke * 0.6f)
                }
            }
        }
    }
}

/**
 * The machine's body: what it stands on, its contact shadow (and its reflection on a polished floor, [reflect] > 0), the
 * far side panel's inside and front edge, every front face that faces you (back to front, lit by the light's angle)
 * with its parts — coin door, deck with sticks and buttons, the bezel round the screen, the marquee's light ([lit] 0…1:
 * how lit its sign is, [glow]: its light spills round it), the speaker grille. The near side panel goes over everything
 * in [drawCabSide] (after the screen and marquee pictures, which it can hide from the side).
 */
internal fun DrawScope.drawCabBody(f: CabFrame, paint: CabPaint, lit: Float, glow: Boolean, detail: Boolean, dim: Float = 0f, reflect: Float = 0f) {
    val m = f.m
    val n = m.pts.size
    for (b in m.boxes) drawBox(f, b, dim)
    val foot = m.foot
    val front = m.side.filter { it.y <= foot + 0.01f }.maxOfOrNull { it.x } ?: m.d
    if (reflect > 0f && m.boxes.isEmpty()) drawReflection(f, paint, reflect * (1f - dim * 0.6f))
    contactShadow(f, 0f, m.w, 0.01f, front, foot, if (m.boxes.isEmpty()) 1f else 0.8f)
    // A soft pool round it.
    val c0 = f.map(m.w / 2f, front * 0.6f, foot)
    val r = (f.map(m.w + 0.3f, front * 0.6f, foot).x - f.map(-0.3f, front * 0.6f, foot).x).coerceAtLeast(10f) * 0.55f
    drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent), center = c0, radius = r), Offset(c0.x - r, c0.y - r * 0.32f), Size(r * 2f, r * 0.64f))

    val u0 = m.inset; val u1 = m.w - m.inset
    if (m.inset > 0f) {
        // The far panel's inside (in its own shadow), and its front edge.
        val farIn = f.innerOf(f.farU)
        drawPath(f.polygon(farIn, m.side), litColor(paint.side, 0.30f * (1f - dim * 0.5f)))
        drawPanelEdges(f, paint, f.farU, farIn, lit, dim, nearSide = false)
    }
    // The front faces, back to front.
    val order = (0 until n).filter { f.faces(it) }.sortedBy { i -> (m.pts[i].x + m.pts[(i + 1) % n].x) / 2f }
    val stroke = (f.scale * 0.005f).coerceAtLeast(1f)
    for (i in order) {
        val role = m.roles[i]
        val a0 = f.at(u0, i); val a1 = f.at(u1, i); val b1 = f.at(u1, i + 1); val b0 = f.at(u0, i + 1)
        val face = quad(a0, a1, b1, b0)
        val k = f.light(i) * (1f - dim * 0.55f)
        val base = when (role) {
            CabRole.KICK -> if (m.build == CabBuild.UPRIGHT) Color(0xFF55595F) else Color(0xFF101114)
            CabRole.DECK, CabRole.LIP -> paint.deck
            CabRole.SCREEN, CabRole.BEZEL -> paint.mask
            CabRole.MARQUEE -> lerp(Color(0xFF1A1B1F), paint.marquee, 0.2f + 0.65f * lit)
            CabRole.SPEAKER, CabRole.HOOD_UNDER -> lerp(paint.body, Color.Black, if (m.build == CabBuild.CANDY) 0.25f else 0.5f)
            CabRole.STAND, CabRole.COUNTER -> CounterWood
            else -> paint.body
        }
        when (role) {
            CabRole.MARQUEE -> {
                // Lit from behind: brightest in the middle; its light spills a little round it (no bloom past the box).
                val c = Offset((a0.x + b1.x) / 2f, (a0.y + b1.y) / 2f)
                val rr = hypot(a1.x - a0.x, a1.y - a0.y) * 0.62f
                if (glow && lit > 0.01f) drawPath(face, paint.marquee.copy(alpha = 0.16f * lit), style = Stroke(stroke * 6f))
                drawPath(face, Brush.radialGradient(listOf(lerp(base, Color.White, 0.28f * lit), base, lerp(base, Color.Black, 0.5f)), center = c, radius = rr.coerceAtLeast(1f)))
            }
            else -> fillFace(face, a0, b0, litColor(base, k))
        }
        // Edges: a fine light line where a face turns to the light, a dark one at its foot.
        drawLine(Color.White.copy(alpha = 0.10f * k), b0, b1, stroke)
        drawLine(Color.Black.copy(alpha = 0.35f), a0, a1, stroke)
        when (role) {
            CabRole.SCREEN -> drawBezel(f, i, paint, lit)
            CabRole.DECK -> drawDeck(f, i, paint, detail)
            CabRole.LIP -> drawPath(quad(a0, a1, f.onFace(i, 0.3f, 1f), f.onFace(i, 0.3f, 0f)), litColor(paint.trim, 0.55f + 0.45f * k).copy(alpha = 0.9f))
            CabRole.DOOR -> drawCoinDoor(f, i, 0.08f, 0.40f, detail)
            CabRole.FRONT -> when (m.build) {
                CabBuild.CANDY -> drawCoinDoor(f, i, 0.25f, 0.70f, detail)
                CabBuild.NEO -> {
                    drawCoinDoor(f, i, 0.5f, 0.82f, detail)
                    // A lit stripe down the front.
                    drawPath(quad(f.onFace(i, 0.05f, 0.48f), f.onFace(i, 0.05f, 0.52f), f.onFace(i, 0.42f, 0.52f), f.onFace(i, 0.42f, 0.48f)), paint.trim.copy(alpha = 0.45f + 0.4f * lit))
                }
                else -> Unit
            }
            CabRole.SPEAKER -> drawGrille(f, i, 2)
            CabRole.HOOD_UNDER -> if (m.build == CabBuild.UPRIGHT) drawGrille(f, i, 2)
            CabRole.KICK -> if (m.build == CabBuild.UPRIGHT) drawLine(Color.White.copy(alpha = 0.22f * k), f.onFace(i, 0.8f, 0f), f.onFace(i, 0.8f, 1f), stroke)
            CabRole.MARQUEE -> {
                // The marquee's retaining strips, top and bottom.
                drawPath(quad(a0, a1, f.onFace(i, 0.07f, 1f), f.onFace(i, 0.07f, 0f)), Color(0xFF2A2C31))
                drawPath(quad(b0, b1, f.onFace(i, 0.93f, 1f), f.onFace(i, 0.93f, 0f)), Color(0xFF2A2C31))
            }
            else -> Unit
        }
    }
    // The foot of the front in the floor's shade (ambient occlusion).
    if (m.boxes.isEmpty()) {
        val y1 = 0.14f
        val p = quad(f.map(u0, front, y1), f.map(u1, front, y1), f.map(u1, front, 0f), f.map(u0, front, 0f))
        drawPath(p, Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), startY = f.map(u0, front, y1).y, endY = f.map(u0, front, 0f).y))
    }
    if (m.inset > 0f) {
        // The near panel's inner end of anything jutting out past it (the control panel's end).
        drawPath(f.polygon(f.innerOf(f.nearU), m.pts), litColor(paint.deck, 0.38f * (1f - dim * 0.5f)))
    }
}

/** On a polished floor: the machine's faint upside-down reflection, fading away from its foot. */
private fun DrawScope.drawReflection(f: CabFrame, paint: CabPaint, a: Float) {
    val m = f.m
    val u = f.nearU
    val path = Path()
    m.side.forEachIndexed { i, p -> val o = f.map(u, p.x, -p.y); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
    path.close()
    val front = Path()
    m.pts.forEachIndexed { i, p -> val o = f.map(m.w / 2f, p.x, -p.y); if (i == 0) front.moveTo(o.x, o.y) else front.lineTo(o.x, o.y) }
    front.close()
    val top = f.map(u, m.d, 0f).y
    val bottom = f.map(u, m.d, -m.h * 0.55f).y
    val c = lerp(paint.side, paint.marquee, 0.25f)
    drawPath(path, Brush.verticalGradient(listOf(c.copy(alpha = 0.32f * a), Color.Transparent), startY = top, endY = bottom))
    drawPath(front, Brush.verticalGradient(listOf(lerp(paint.body, paint.marquee, 0.2f).copy(alpha = 0.28f * a), Color.Transparent), startY = top, endY = bottom))
}

/** The plastic mask round the screen, the glass set back in it (its shadowed upper lip, its lit lower lip), a trim line. */
private fun DrawScope.drawBezel(f: CabFrame, i: Int, paint: CabPaint, lit: Float) {
    val (tl, tr, bl) = f.screenQuad(0.06f)
    val br = Offset(tr.x + bl.x - tl.x, tr.y + bl.y - tl.y)
    drawPath(quad(tl, tr, br, bl), Color.Black.copy(alpha = 0.55f))
    drawPath(quad(tl, tr, br, bl), paint.trim.copy(alpha = 0.18f + 0.25f * lit), style = Stroke((f.scale * 0.004f).coerceAtLeast(1f)))
    val (gtl, gtr, gbl) = f.screenQuad(0.1f)
    val gbr = Offset(gtr.x + gbl.x - gtl.x, gtr.y + gbl.y - gtl.y)
    drawPath(quad(gtl, gtr, gbr, gbl), Color(0xFF020304))
    // The lower lip of the recess catches the light.
    drawLine(Color.White.copy(alpha = 0.16f), Offset((bl.x + gbl.x) / 2f, (bl.y + gbl.y) / 2f), Offset((br.x + gbr.x) / 2f, (br.y + gbr.y) / 2f), (f.scale * 0.005f).coerceAtLeast(1f))
}

/** The control deck: a stick and a block of buttons for each player; [detail] draws them all (small machines: fewer). */
private fun DrawScope.drawDeck(f: CabFrame, i: Int, paint: CabPaint, detail: Boolean) {
    val players = f.m.players
    val r = (f.scale * (if (f.m.build == CabBuild.BARTOP) 0.020f else 0.024f)).coerceAtLeast(1.5f)
    val colors = listOf(paint.trim, Color(0xFFF2C14E), Color(0xFF4FC3F7), Color(0xFFEF5350), Color(0xFF66BB6A), Color(0xFFECEFF1))
    // The panel's printed overlay: a darker plate under the controls.
    if (detail) drawPath(quad(f.onFace(i, 0.12f, 0.04f), f.onFace(i, 0.12f, 0.96f), f.onFace(i, 0.9f, 0.96f), f.onFace(i, 0.9f, 0.04f)), Color.Black.copy(alpha = 0.22f))
    for (pl in 0 until players) {
        val left = if (players == 1) 0.22f else if (pl == 0) 0.1f else 0.56f
        val span = if (players == 1) 0.6f else 0.36f
        // The stick: its shadow, shaft and ball.
        val s = f.onFace(i, 0.55f, left + span * 0.12f)
        drawOval(Color.Black.copy(alpha = 0.5f), Offset(s.x - r * 1.3f, s.y - r * 0.45f), Size(r * 2.6f, r * 0.9f))
        val top = Offset(s.x, s.y - r * 2.6f)
        drawLine(Color(0xFF2B2C31), s, top, strokeWidth = r * 0.55f, cap = StrokeCap.Round)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF8A80), Color(0xFFD83430), Color(0xFF6E1514)), center = top - Offset(r * 0.3f, r * 0.3f), radius = r * 1.3f), r * 1.05f, top)
        if (!detail && players > 1 && pl == 1) continue
        // The buttons: two rows of three.
        val rows = if (detail) 2 else 1
        for (row in 0 until rows) for (col in 0 until 3) {
            val c = f.onFace(i, if (rows == 1) 0.5f else 0.36f + row * 0.32f, left + span * (0.42f + col * 0.19f) + row * 0.02f)
            val bc = colors[(row * 3 + col) % colors.size]
            drawOval(Color.Black.copy(alpha = 0.5f), Offset(c.x - r * 0.95f, c.y - r * 0.35f), Size(r * 1.9f, r * 0.95f))
            drawOval(litColor(bc, 0.85f), Offset(c.x - r * 0.8f, c.y - r * 0.55f), Size(r * 1.6f, r * 0.9f))
            drawOval(Color.White.copy(alpha = 0.3f), Offset(c.x - r * 0.45f, c.y - r * 0.45f), Size(r * 0.6f, r * 0.3f))
        }
    }
}

/** The coin door: a brushed metal plate with two lit coin slots and a lock, between [t0] and [t1] up face [i]. */
private fun DrawScope.drawCoinDoor(f: CabFrame, i: Int, t0: Float, t1: Float, detail: Boolean) {
    val a = f.onFace(i, t0, 0.34f); val b = f.onFace(i, t0, 0.66f); val c = f.onFace(i, t1, 0.66f); val d = f.onFace(i, t1, 0.34f)
    val door = quad(a, b, c, d)
    drawPath(door, Brush.linearGradient(listOf(Color(0xFF6A6E75), Color(0xFF3A3D43), Color(0xFF575B62)), start = d, end = b))
    drawPath(door, Color.Black.copy(alpha = 0.5f), style = Stroke((f.scale * 0.004f).coerceAtLeast(1f)))
    for (s in 0 until 2) {
        val x0 = 0.4f + s * 0.12f
        val p0 = f.onFace(i, t0 + (t1 - t0) * 0.62f, x0); val p1 = f.onFace(i, t0 + (t1 - t0) * 0.62f, x0 + 0.07f)
        val p2 = f.onFace(i, t0 + (t1 - t0) * 0.86f, x0 + 0.07f); val p3 = f.onFace(i, t0 + (t1 - t0) * 0.86f, x0)
        drawPath(quad(p0, p1, p2, p3), Color(0xFFE8752E))
    }
    if (detail) {
        val l = f.onFace(i, t0 + (t1 - t0) * 0.3f, 0.5f)
        drawCircle(Color(0xFF15161A), (f.scale * 0.008f).coerceAtLeast(1f), l)
    }
}

/** A speaker grille: rows of holes on [blocks] speakers. */
private fun DrawScope.drawGrille(f: CabFrame, i: Int, blocks: Int) {
    val r = (f.scale * 0.0045f).coerceAtLeast(0.8f)
    for (bk in 0 until blocks) {
        val s0 = if (blocks == 1) 0.12f else 0.08f + bk * 0.56f
        val span = if (blocks == 1) 0.76f else 0.32f
        for (row in 0 until 3) for (col in 0 until 7) {
            val p = f.onFace(i, 0.25f + row * 0.25f, s0 + span * (col + 0.5f) / 7f)
            drawCircle(Color.Black.copy(alpha = 0.6f), r, p)
        }
    }
}

/**
 * Over the screen's picture (drawn after it): the glass — the bezel's shadow falling on its top, a soft reflection
 * across it. Then the near side panel over everything: its paint (lit by the side it's on, darker towards the floor),
 * the game's art printed on it ([art], when given; each build its own way — a full print, a framed panel, a stripe), its
 * front edge (T-moulding, the shell's rounded edge, an LED rail, plywood).
 */
internal fun DrawScope.drawCabSide(f: CabFrame, paint: CabPaint, art: ImageBitmap?, lit: Float, dim: Float = 0f) {
    val m = f.m
    drawGlass(f)
    val u = f.nearU
    val path = f.polygon(u, m.side)
    val k = (if (f.view.yawDeg >= 0f) 0.48f else 0.72f) * (1f - dim * 0.5f)
    val top = f.map(u, 0f, m.h); val bottom = f.map(u, 0f, m.foot)
    if (m.inset > 0f) drawPanelEdges(f, paint, f.innerOf(u), u, lit, dim, nearSide = true)
    drawPath(path, Brush.verticalGradient(listOf(litColor(paint.side, k + 0.1f), litColor(paint.side, k - 0.12f)), startY = top.y, endY = bottom.y))
    when (m.build) {
        CabBuild.UPRIGHT, CabBuild.BARTOP -> if (art != null) clipPath(path) { printArt(f, art, u, 0.04f, m.d, m.foot + 0.06f, m.h, 0.88f * (1f - dim * 0.6f)) }
        CabBuild.NEO -> {
            // A framed art panel low on the side (the frame's aluminium round it).
            val f0 = 0.10f; val f1 = 0.55f; val y0 = 0.14f; val y1 = 0.66f
            val panel = quad(f.map(u, f0, y1), f.map(u, f1 + 0.06f, y1), f.map(u, f1, y0), f.map(u, f0, y0))
            drawPath(panel, Color(0xFF14161B))
            if (art != null) clipPath(panel) { printArt(f, art, u, f0, f1 + 0.06f, y0, y1, 0.92f * (1f - dim * 0.6f)) }
            drawPath(panel, litColor(paint.edge, 0.7f), style = Stroke((f.scale * 0.006f).coerceAtLeast(1f)))
        }
        CabBuild.CANDY -> clipPath(path) {
            // The shell's two-tone sweep: a stripe in the game's colour rising from the back to the deck.
            val s = Path().apply {
                val p0 = f.map(u, 0f, 0.30f); val p1 = f.map(u, 0.86f, 0.62f); val p2 = f.map(u, 0.86f, 0.70f); val p3 = f.map(u, 0f, 0.40f)
                moveTo(p0.x, p0.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); close()
            }
            drawPath(s, litColor(paint.trim, k + 0.25f))
            val s2 = Path().apply {
                val p0 = f.map(u, 0f, 0.43f); val p1 = f.map(u, 0.86f, 0.72f); val p2 = f.map(u, 0.86f, 0.745f); val p3 = f.map(u, 0f, 0.46f)
                moveTo(p0.x, p0.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); close()
            }
            drawPath(s2, litColor(lerp(paint.trim, Color.White, 0.3f), k + 0.25f))
        }
    }
    clipPath(path) {
        // Darker towards the floor, and a faint sheen across the panel's upper part.
        val foot = f.map(u, 0f, m.foot + 0.25f)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f)), startY = foot.y, endY = bottom.y))
        val s0 = f.map(u, 0f, m.h); val s1 = f.map(u, m.d, m.h * 0.55f)
        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.05f), Color.Transparent), start = s0, end = s1))
    }
    when (m.edge) {
        CabEdge.ROUND -> {
            // The shell's rounded edges, on both sides of the front.
            val b = 0.035f
            drawPanelEdges(f, paint, f.farU, if (f.farU == 0f) b else m.w - b, lit, dim, nearSide = false)
            drawPanelEdges(f, paint, if (u == 0f) b else m.w - b, u, lit, dim, nearSide = true)
        }
        CabEdge.TMOLD -> drawPath(path, lerp(paint.edge, Color.Black, 0.3f * (1f - lit) + dim * 0.4f), style = Stroke((f.scale * 0.006f).coerceAtLeast(1.2f)))
        CabEdge.LED -> drawPath(path, litColor(paint.edge, 0.55f), style = Stroke((f.scale * 0.005f).coerceAtLeast(1f)))
        CabEdge.PLY -> drawPath(path, litColor(paint.edge, 0.5f), style = Stroke((f.scale * 0.004f).coerceAtLeast(1f)))
    }
}

/** The glass over the picture: the bezel's shadow on its top, a soft diagonal reflection. */
private fun DrawScope.drawGlass(f: CabFrame) {
    val (tl, tr, bl) = f.screenQuad(0.1f)
    val br = Offset(tr.x + bl.x - tl.x, tr.y + bl.y - tl.y)
    val glass = quad(tl, tr, br, bl)
    val down = Offset(tl.x + (bl.x - tl.x) * 0.16f, tl.y + (bl.y - tl.y) * 0.16f)
    drawPath(glass, Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), start = tl, end = down))
    val mid = Offset((tl.x + br.x) / 2f, (tl.y + br.y) / 2f)
    drawPath(glass, Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent), start = Offset(tl.x, tl.y), end = Offset(mid.x + (mid.x - tl.x) * 0.2f, mid.y + (mid.y - tl.y) * 0.2f)))
}

/**
 * The game's art printed on the side plane at across = [u], over forward [f0]…[f1] and up [y0]…[y1] — cropped to fill
 * that area (its own proportions kept), [alpha] into the paint, then the panel's shading over it.
 */
private fun DrawScope.printArt(f: CabFrame, art: ImageBitmap, u: Float, f0: Float, f1: Float, y0: Float, y1: Float, alpha: Float) {
    val o = f.map(u, f0, y0); val fx = f.map(u, f1, y0); val fy = f.map(u, f0, y1)
    val ax = fx - o; val ay = fy - o
    val pw = f1 - f0; val ph = y1 - y0
    val ia = art.width.toFloat() / art.height
    val pa = pw / ph
    val (sw, sh) = if (ia > pa) Pair(art.height * pa, art.height.toFloat()) else Pair(art.width.toFloat(), art.width / pa)
    val sx0 = (art.width - sw) / 2f; val sy0 = (art.height - sh) / 2f
    // Image px (sx, sy) → panel (forward = f0 + sx/sw·w, up = y0 + (1 − sy/sh)·h) → screen.
    val mtx = androidx.compose.ui.graphics.Matrix()
    val a = ax.x / sw; val b = -ay.x / sh; val c = ax.y / sw; val d = -ay.y / sh
    val tx = o.x + ay.x - a * sx0 - b * sy0; val ty = o.y + ay.y - c * sx0 - d * sy0
    mtx.values[androidx.compose.ui.graphics.Matrix.ScaleX] = a
    mtx.values[androidx.compose.ui.graphics.Matrix.SkewX] = b
    mtx.values[androidx.compose.ui.graphics.Matrix.TranslateX] = tx
    mtx.values[androidx.compose.ui.graphics.Matrix.SkewY] = c
    mtx.values[androidx.compose.ui.graphics.Matrix.ScaleY] = d
    mtx.values[androidx.compose.ui.graphics.Matrix.TranslateY] = ty
    withTransform({ transform(mtx) }) { drawImage(art, alpha = alpha) }
    // The panel's own light over the print (it's printed on, not glowing).
    val top = f.map(u, f0, y1); val bottom = f.map(u, f0, y0)
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.42f)), startY = top.y, endY = bottom.y))
}

/**
 * Lays [content] out flat ([nomW] × [nomH] px) and puts it on the parallelogram [quad] gives — top-left, top-right,
 * bottom-left, px in the parent (null: hidden) — by two graphics layers (a turn, a stretch and a turn: any such shape), so
 * a picture, a clip or a logo sits on a face of a machine. [quad] is read while drawing only: a machine turning as the
 * camera glides moves its pictures without recomposing anything. Its bounds in the window (the game window's grow-out)
 * follow the layers.
 */
@Composable
internal fun AffineBox(nomW: Int, nomH: Int, quad: () -> Triple<Offset, Offset, Offset>?, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val w = nomW.coerceAtLeast(1); val h = nomH.coerceAtLeast(1)
    Box(
        modifier
            .layout { m, _ -> val pl = m.measure(Constraints.fixed(w, h)); layout(w, h) { pl.place(0, 0) } }
            .graphicsLayer {
                val q = quad()
                if (q == null) { alpha = 0f; return@graphicsLayer }
                val t = affineOf(q, w, h)
                alpha = 1f
                translationX = t[4]; translationY = t[5]
                rotationZ = t[3]; scaleX = t[1]; scaleY = t[2]
                transformOrigin = TransformOrigin.Center
            }
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            val q = quad() ?: return@graphicsLayer
            rotationZ = affineOf(q, w, h)[0]
            transformOrigin = TransformOrigin.Center
        }, content = content)
    }
}

/** For [AffineBox]: the turn, stretch, turn and the move that put a [w] × [h] box on the parallelogram [q]. */
internal fun affineOf(q: Triple<Offset, Offset, Offset>, w: Int, h: Int): FloatArray {
    val (p0, p1, p3) = q
    val a = (p1.x - p0.x) / w; val c = (p1.y - p0.y) / w
    val b = (p3.x - p0.x) / h; val d = (p3.y - p0.y) / h
    val dec = decompose2(a, b, c, d)
    // Where the box's own centre goes (p0 + A·centre), its top-left placed so that turning round its centre lands it there.
    val cx = w / 2f; val cy = h / 2f
    return floatArrayOf(dec[0], dec[1], dec[2], dec[3], p0.x + a * cx + b * cy - cx, p0.y + c * cx + d * cy - cy)
}

/**
 * The 2×2 matrix [[a, b], [c, d]] (columns: where x and y go) as a turn by [0], a stretch by [1], [2], a turn by [3]
 * (degrees, clockwise on screen): A = R(r2)·S·R(r1).
 */
internal fun decompose2(a: Float, b: Float, c: Float, d: Float): FloatArray {
    val e = (a + d) / 2f; val f = (a - d) / 2f; val g = (c + b) / 2f; val h = (c - b) / 2f
    val q = sqrt(e * e + h * h); val r = sqrt(f * f + g * g)
    val sx = q + r; val sy = q - r
    val a1 = atan2(g, f); val a2 = atan2(h, e)
    val r1 = (a2 - a1) / 2f; val r2 = (a2 + a1) / 2f
    return floatArrayOf(Math.toDegrees(r1.toDouble()).toFloat(), sx, sy, Math.toDegrees(r2.toDouble()).toFloat())
}

/**
 * Update 348 (Toni: "prevent neighbouring machines from visually overlapping"): the angles on the hall's arc (radius
 * [radius] m round a point behind the machine you're on, which stands [z0] m from the eye) at which the machines 0, 1, 2 …
 * places from it stand, each the first angle at which a machine [w] wide and [d] deep — turned as the hall turns it,
 * [yawBias] degrees more — stands clear of the one before by [gap] (metres, as seen at z0), the one you're on stepped
 * [forward] m out. Projected, so it holds on screen: further machines stand closer together on the arc, never on top of
 * each other. Up to [n] places, none past [maxPhi].
 */
internal fun hallSlots(radius: Float, z0: Float, w: Float, d: Float, gap: Float, yawBias: Float, forward: Float, n: Int = 8, maxPhi: Float = 1.35f): FloatArray {
    val bias = Math.toRadians(abs(yawBias).toDouble()).toFloat()
    fun z(p: Float) = z0 + radius * (1f - cos(p))
    fun x(p: Float) = radius * sin(p)
    fun half(p: Float, zz: Float): Float {
        val yaw = abs(atan2(x(p), zz) - p) + bias
        return 0.5f * (w * abs(cos(yaw)) + d * abs(sin(yaw)))
    }
    val t = FloatArray(n + 1)
    val zc = z0 - forward
    var edge = half(0f, zc) / zc
    for (k in 1..n) {
        var p = t[k - 1]
        while (p < maxPhi) {
            p += 0.004f
            val zz = z(p)
            if ((x(p) - half(p, zz)) / zz >= edge + gap / z0) break
        }
        t[k] = p.coerceAtMost(maxPhi)
        val zz = z(t[k])
        edge = (x(t[k]) + half(t[k], zz)) / zz
    }
    return t
}

/** A place [s] (places from the machine you're on, fractional while gliding, signed) as its angle on the arc, from [slots]. */
internal fun hallPhi(slots: FloatArray, s: Float): Float {
    val a = abs(s)
    val i = a.toInt()
    val p = if (i + 1 < slots.size) slots[i] + (slots[i + 1] - slots[i]) * (a - i) else slots.last() + (a - (slots.size - 1)) * 0.2f
    return if (s < 0f) -p else p
}
