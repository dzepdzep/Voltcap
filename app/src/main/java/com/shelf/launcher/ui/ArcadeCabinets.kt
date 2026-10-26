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
 * and above: one affine view per machine (2.5D — no 3D engine, a few paths), so the four builds keep their own
 * silhouettes and show their side panels.
 *
 * Proportions, from the real machines and photos of them (original drawings — no names, logos or artwork of theirs):
 * - Classic upright (the 1990s American fighting-game upright): ~0.65 m wide, 0.85 deep, 1.83 tall; a deep side panel,
 *   a marquee box leaning out over a screen tilted back ~24°, a control panel jutting out, coin door and kick plate.
 * - Candy cabinet (the Japanese sit-down cabinets of the early 1990s): ~0.75 × 0.90 × 1.45 m; rounded top, a big screen
 *   tilted back ~33°, a wide two-player deck at seated height, the lower front stepping back for your knees.
 * - Neo-retro (Voltcap's own, after late-1990s Japanese cabinets): ~0.76 × 0.90 × 1.62 m; a body leaning forward, a
 *   near-upright big screen under a speaker hood, a lit strip along its edges.
 * - Bartop: ~0.50 × 0.55 × 0.66 m, standing on a counter — no coin door or kick plate.
 */

/** What a face of the cabinet is (each edge of the side profile, swept across the width). */
internal enum class CabRole { BASE, KICK, FRONT, DOOR, UNDER, LIP, DECK, SCREEN, HOOD_UNDER, SPEAKER, MARQUEE, TOP, BACK,
    /** The bartop's counter: its front and edge, and its top in front of the machine. */
    STAND, COUNTER }

internal enum class CabBuild { UPRIGHT, CANDY, NEO, BARTOP }

/**
 * A cabinet: [w] wide, [d] deep, [h] tall (metres); [pts] its side profile as (forward, up) points going round
 * counter-clockwise (forward 0 = its back); [roles][i] what the face from pts[i] to pts[i+1] is.
 */
internal class CabModel(val build: CabBuild, val w: Float, val d: Float, val h: Float, val pts: List<Offset>, val roles: List<CabRole>) {
    /** Two players' controls (one on a bartop). */
    val players: Int get() = if (build == CabBuild.BARTOP) 1 else 2
    fun edge(r: CabRole): Int = roles.indexOf(r)
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

private fun profile(build: CabBuild, w: Float, d: Float, h: Float, block: ProfileBuilder.() -> Unit): CabModel {
    val b = ProfileBuilder().apply(block)
    return CabModel(build, w, d, h, b.pts, b.roles)
}

internal val CabUpright: CabModel = profile(CabBuild.UPRIGHT, 0.65f, 0.85f, 1.83f) {
    p(0f, 0f, CabRole.BASE)
    p(0.64f, 0f, CabRole.KICK)
    p(0.64f, 0.10f, CabRole.DOOR)
    p(0.64f, 0.86f, CabRole.UNDER)
    p(0.82f, 0.96f, CabRole.LIP)
    p(0.85f, 1.03f, CabRole.DECK)
    p(0.60f, 1.11f, CabRole.SCREEN)
    p(0.42f, 1.52f, CabRole.HOOD_UNDER)
    p(0.60f, 1.58f, CabRole.MARQUEE)
    p(0.63f, 1.80f, CabRole.TOP)
    p(0.04f, 1.83f, CabRole.BACK)
    p(0f, 1.72f, CabRole.BACK)
}

internal val CabCandy: CabModel = profile(CabBuild.CANDY, 0.75f, 0.90f, 1.45f) {
    p(0f, 0f, CabRole.BASE)
    p(0.62f, 0f, CabRole.KICK)
    p(0.62f, 0.08f, CabRole.FRONT)
    // The knee recess, curving out under the deck.
    curve(0.56f, 0.40f, 0.58f, 0.66f, 0.84f, 0.70f, CabRole.UNDER, 4)
    p(0.84f, 0.70f, CabRole.LIP)
    p(0.89f, 0.78f, CabRole.DECK)
    p(0.66f, 0.84f, CabRole.SCREEN)
    p(0.40f, 1.24f, CabRole.MARQUEE)
    // The rounded top.
    curve(0.34f, 1.36f, 0.24f, 1.46f, 0.06f, 1.45f, CabRole.TOP, 5)
    p(0.06f, 1.45f, CabRole.BACK)
    p(0f, 1.30f, CabRole.BACK)
}

internal val CabNeo: CabModel = profile(CabBuild.NEO, 0.76f, 0.90f, 1.62f) {
    p(0f, 0f, CabRole.BASE)
    p(0.52f, 0f, CabRole.KICK)
    p(0.54f, 0.08f, CabRole.FRONT)
    p(0.64f, 0.62f, CabRole.UNDER)
    p(0.88f, 0.72f, CabRole.LIP)
    p(0.90f, 0.79f, CabRole.DECK)
    p(0.68f, 0.85f, CabRole.SCREEN)
    p(0.58f, 1.30f, CabRole.HOOD_UNDER)
    p(0.74f, 1.36f, CabRole.SPEAKER)
    p(0.72f, 1.50f, CabRole.MARQUEE)
    p(0.66f, 1.62f, CabRole.TOP)
    p(0.10f, 1.62f, CabRole.BACK)
    p(0f, 1.45f, CabRole.BACK)
}

internal val CabBartop: CabModel = profile(CabBuild.BARTOP, 0.50f, 0.62f, 1.62f) {
    // The counter it stands on…
    p(0f, 0f, CabRole.BASE)
    p(0.50f, 0f, CabRole.STAND)
    p(0.50f, 0.88f, CabRole.UNDER)
    p(0.62f, 0.92f, CabRole.STAND)
    p(0.62f, 0.96f, CabRole.COUNTER)
    // …and the little machine: a short deck, the screen, a small marquee.
    p(0.55f, 0.96f, CabRole.LIP)
    p(0.57f, 1.02f, CabRole.DECK)
    p(0.40f, 1.11f, CabRole.SCREEN)
    p(0.26f, 1.44f, CabRole.HOOD_UNDER)
    p(0.38f, 1.48f, CabRole.MARQUEE)
    p(0.38f, 1.62f, CabRole.TOP)
    p(0.04f, 1.62f, CabRole.BACK)
    p(0f, 1.56f, CabRole.BACK)
}

/** The bartop's counter top: below this the profile is the counter (wood), above it the machine. */
private const val COUNTER_TOP = 0.96f
private val CounterWood = Color(0xFF5B3B24)

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
        for (u in floatArrayOf(0f, m.w)) for (p in m.pts) {
            val x = rx(u, p.x); val y = ry(p.x, p.y)
            minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
        }
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

    /** The profile's point [i] on the face at [u]. */
    fun at(u: Float, i: Int): Offset = m.pts[i % m.pts.size].let { map(u, it.x, it.y) }

    /** A point on face [i]: [t] along it (0 = its first point), [s] across (0 = left, 1 = right). */
    fun onFace(i: Int, t: Float, s: Float): Offset {
        val a = m.pts[i]; val b = m.pts[(i + 1) % m.pts.size]
        return map(m.w * s, a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }

    /** Whether face [i] faces you (its outward side towards the eye). */
    fun faces(i: Int): Boolean {
        val a = m.pts[i]; val b = m.pts[(i + 1) % m.pts.size]
        val df = b.x - a.x; val dy = b.y - a.y
        val len = hypot(df, dy).coerceAtLeast(1e-4f)
        val nf = dy / len; val ny = -df / len
        return nf * ce + ny * se > 0.02f
    }

    /** How lit face [i] is (light from the front, above and a little left). */
    fun light(i: Int): Float {
        val a = m.pts[i]; val b = m.pts[(i + 1) % m.pts.size]
        val df = b.x - a.x; val dy = b.y - a.y
        val len = hypot(df, dy).coerceAtLeast(1e-4f)
        val nf = dy / len; val ny = -df / len
        return (0.42f + 0.58f * (nf * 0.6f + ny * 0.8f).coerceAtLeast(0f)).coerceIn(0f, 1f)
    }

    /** The screen's glass inside its bezel: top-left, top-right, bottom-left. */
    fun screenQuad(inset: Float = 0.1f): Triple<Offset, Offset, Offset> {
        val i = m.edge(CabRole.SCREEN)
        return Triple(onFace(i, 1f - inset, inset), onFace(i, 1f - inset, 1f - inset), onFace(i, inset * 0.9f, inset))
    }

    /** The marquee's sign: top-left, top-right, bottom-left. */
    fun marqueeQuad(): Triple<Offset, Offset, Offset> {
        val i = m.edge(CabRole.MARQUEE)
        val inset = 0.06f
        return Triple(onFace(i, 1f - 0.12f, inset), onFace(i, 1f - 0.12f, 1f - inset), onFace(i, 0.12f, inset))
    }

    /** The bounds of the whole machine (px). */
    fun bounds(): Rect {
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (u in floatArrayOf(0f, m.w)) for (p in m.pts) { val o = map(u, p.x, p.y); minX = min(minX, o.x); maxX = max(maxX, o.x); minY = min(minY, o.y); maxY = max(maxY, o.y) }
        return Rect(minX, minY, maxX, maxY)
    }
}

/** The ratio width / height a machine takes on screen seen as [view]. */
internal fun cabAspect(m: CabModel, view: CabView): Float {
    val f = CabFrame(m, view, Size(1000f, 1000f))
    val b = f.bounds()
    return (b.width / b.height).coerceIn(0.2f, 2f)
}

/** A machine's paint: front [body], [side] panels, the bright [trim] (moulding / lit strip), the [marquee]'s light, the control [deck]. */
internal class CabPaint(val body: Color, val side: Color, val trim: Color, val marquee: Color, val deck: Color)

/** The paint of [build] in the colour [c] (the game's, or the theme's). Each build has its own scheme, as the real ones. */
internal fun cabPaint(build: CabBuild, c: Color): CabPaint {
    val rich = richArcade(c)
    return when (build) {
        // Black front, side panels printed in the game's colour, bright T-moulding.
        CabBuild.UPRIGHT -> CabPaint(Color(0xFF16171B), lerp(rich, Color.Black, 0.35f), rich, rich, Color(0xFF26272C))
        // White / cream plastic body, light grey sides with a touch of colour, a dark deck.
        CabBuild.CANDY -> CabPaint(Color(0xFFE8E5DD), lerp(Color(0xFFCFD0D2), rich, 0.12f), lerp(Color(0xFFA9ABB0), rich, 0.25f), rich, Color(0xFF2A2C31))
        // Pearl-silver body, the colour on its lit edge strip.
        CabBuild.NEO -> CabPaint(Color(0xFFD7DAE0), Color(0xFFB9BDC6), rich, rich, Color(0xFF16171A))
        // A black box with coloured sides.
        CabBuild.BARTOP -> CabPaint(Color(0xFF1B1C20), lerp(rich, Color.Black, 0.25f), rich, rich, Color(0xFF111215))
    }
}

/** A bright, rich version of [c]: the cabinets' strips and the marquee light. */
internal fun richArcade(c: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()), hsv)
    hsv[1] = hsv[1].coerceIn(0.5f, 0.88f)
    hsv[2] = 0.95f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun quad(a: Offset, b: Offset, c: Offset, d: Offset) = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }

private fun shade(c: Color, k: Float): Color = if (k >= 0.5f) lerp(c, Color.White, (k - 0.5f) * 0.5f) else lerp(Color.Black, c, (k * 2f).coerceIn(0f, 1f))

/**
 * The machine's body: its floor shadow, every face that faces you (front to back, lit by the light's angle) with its
 * parts — coin door, deck with sticks and buttons, the bezel round the screen, the marquee's light ([lit] 0…1: how lit
 * its sign is, [glow]: its light spills round it), the speaker grille. The near side panel goes over everything in
 * [drawCabSide] (after the screen and marquee pictures, which it can hide from the side).
 */
internal fun DrawScope.drawCabBody(f: CabFrame, paint: CabPaint, lit: Float, glow: Boolean, detail: Boolean, dim: Float = 0f) {
    val m = f.m
    val n = m.pts.size
    val u0 = 0f; val u1 = m.w
    // The floor shadow, a little bigger for a lit machine.
    val fl = f.map(-0.06f, m.d + 0.12f, 0f); val fr = f.map(m.w + 0.06f, m.d + 0.12f, 0f)
    val bl = f.map(-0.04f, -0.04f, 0f); val br = f.map(m.w + 0.04f, -0.04f, 0f)
    drawPath(quad(bl, br, fr, fl), Color.Black.copy(alpha = 0.32f))
    val shadowC = Offset((fl.x + fr.x + bl.x + br.x) / 4f, (fl.y + fr.y + bl.y + br.y) / 4f)
    val shadowR = (fr.x - bl.x).coerceAtLeast(10f) * 0.7f
    drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.38f), Color.Transparent), center = shadowC, radius = shadowR), shadowR, shadowC)
    // The faces, back to front.
    val order = (0 until n).filter { f.faces(it) }.sortedBy { i -> (m.pts[i].x + m.pts[(i + 1) % n].x) / 2f }
    val stroke = (f.scale * 0.006f).coerceAtLeast(1f)
    for (i in order) {
        val role = m.roles[i]
        val a0 = f.at(u0, i); val a1 = f.at(u1, i); val b1 = f.at(u1, i + 1); val b0 = f.at(u0, i + 1)
        val face = quad(a0, a1, b1, b0)
        val k = f.light(i) * (1f - dim * 0.55f)
        val base = when (role) {
            CabRole.KICK -> Color(0xFF0E0F12)
            CabRole.DECK, CabRole.LIP -> paint.deck
            CabRole.SCREEN -> Color(0xFF07080A)
            CabRole.MARQUEE -> lerp(Color(0xFF1A1B1F), paint.marquee, 0.25f + 0.75f * lit)
            CabRole.SPEAKER, CabRole.HOOD_UNDER -> lerp(paint.body, Color.Black, 0.55f)
            CabRole.STAND, CabRole.COUNTER -> CounterWood
            else -> if (m.build == CabBuild.BARTOP && m.pts[i].y < COUNTER_TOP - 0.01f) CounterWood else paint.body
        }
        when (role) {
            CabRole.MARQUEE -> {
                // Lit from behind: bright in the middle.
                val c = Offset((a0.x + b1.x) / 2f, (a0.y + b1.y) / 2f)
                val r = hypot(a1.x - a0.x, a1.y - a0.y) * 0.62f
                if (glow && lit > 0.01f) drawPath(face, paint.marquee.copy(alpha = 0.3f * lit), style = Stroke(stroke * 10f))
                drawPath(face, Brush.radialGradient(listOf(lerp(base, Color.White, 0.35f * lit), base, lerp(base, Color.Black, 0.45f)), center = c, radius = r.coerceAtLeast(1f)))
            }
            else -> drawPath(face, shade(base, k))
        }
        // Edges catch a little light.
        drawPath(face, Color.White.copy(alpha = 0.05f + 0.05f * k), style = Stroke(stroke))
        when (role) {
            CabRole.SCREEN -> drawBezel(f, i, paint, lit)
            CabRole.DECK -> drawDeck(f, i, paint, detail)
            CabRole.LIP -> drawPath(quad(a0, a1, f.onFace(i, 0.25f, 1f), f.onFace(i, 0.25f, 0f)), paint.trim.copy(alpha = 0.85f))
            CabRole.DOOR -> drawCoinDoor(f, i, 0.08f, 0.42f, detail)
            CabRole.FRONT -> if (m.build == CabBuild.CANDY) drawCoinDoor(f, i, 0.3f, 0.75f, detail) else if (m.build == CabBuild.NEO) {
                drawCoinDoor(f, i, 0.55f, 0.85f, detail)
                // A lit stripe down the front.
                drawPath(quad(f.onFace(i, 0.05f, 0.47f), f.onFace(i, 0.05f, 0.53f), f.onFace(i, 0.5f, 0.53f), f.onFace(i, 0.5f, 0.47f)), paint.trim.copy(alpha = 0.5f + 0.4f * lit))
            }
            CabRole.SPEAKER -> drawGrille(f, i)
            CabRole.KICK -> drawPath(quad(a0, a1, f.onFace(i, 0.15f, 1f), f.onFace(i, 0.15f, 0f)), Color.White.copy(alpha = 0.06f))
            else -> Unit
        }
    }
}

/** The black bezel round the screen with a thin line in the cabinet's colour, and the glass behind the picture. */
private fun DrawScope.drawBezel(f: CabFrame, i: Int, paint: CabPaint, lit: Float) {
    val (tl, tr, bl) = f.screenQuad(0.07f)
    val br = Offset(tr.x + bl.x - tl.x, tr.y + bl.y - tl.y)
    val rim = quad(tl, tr, br, bl)
    drawPath(rim, paint.trim.copy(alpha = 0.25f + 0.35f * lit), style = Stroke((f.scale * 0.006f).coerceAtLeast(1f)))
    val (gtl, gtr, gbl) = f.screenQuad(0.1f)
    val gbr = Offset(gtr.x + gbl.x - gtl.x, gtr.y + gbl.y - gtl.y)
    drawPath(quad(gtl, gtr, gbr, gbl), Color(0xFF020304))
}

/** The control deck: a stick and a block of buttons for each player; [detail] draws them all (small machines: fewer). */
private fun DrawScope.drawDeck(f: CabFrame, i: Int, paint: CabPaint, detail: Boolean) {
    val players = f.m.players
    val r = (f.scale * (if (f.m.build == CabBuild.BARTOP) 0.022f else 0.026f)).coerceAtLeast(1.5f)
    val colors = listOf(paint.trim, Color(0xFFF2C14E), Color(0xFF4FC3F7), Color(0xFFEF5350), Color(0xFF66BB6A), Color(0xFFECEFF1))
    for (pl in 0 until players) {
        val left = if (players == 1) 0.22f else if (pl == 0) 0.1f else 0.56f
        val span = if (players == 1) 0.6f else 0.36f
        // The stick: its shadow, shaft and ball.
        val s = f.onFace(i, 0.55f, left + span * 0.12f)
        drawOval(Color.Black.copy(alpha = 0.5f), Offset(s.x - r * 1.3f, s.y - r * 0.45f), Size(r * 2.6f, r * 0.9f))
        val top = Offset(s.x, s.y - r * 2.6f)
        drawLine(Color(0xFF2B2C31), s, top, strokeWidth = r * 0.55f, cap = StrokeCap.Round)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF8A80), Color(0xFFE53935), Color(0xFF7E1716)), center = top - Offset(r * 0.3f, r * 0.3f), radius = r * 1.3f), r * 1.05f, top)
        if (!detail && players > 1 && pl == 1) continue
        // The buttons: two rows of three.
        val rows = if (detail) 2 else 1
        for (row in 0 until rows) for (col in 0 until 3) {
            val c = f.onFace(i, if (rows == 1) 0.5f else 0.36f + row * 0.32f, left + span * (0.42f + col * 0.19f) + row * 0.02f)
            val bc = colors[(row * 3 + col) % colors.size]
            drawOval(Color.Black.copy(alpha = 0.5f), Offset(c.x - r * 0.95f, c.y - r * 0.35f), Size(r * 1.9f, r * 0.95f))
            drawOval(bc, Offset(c.x - r * 0.8f, c.y - r * 0.55f), Size(r * 1.6f, r * 0.9f))
            drawOval(Color.White.copy(alpha = 0.35f), Offset(c.x - r * 0.45f, c.y - r * 0.45f), Size(r * 0.6f, r * 0.3f))
        }
    }
}

/** The coin door: a metal plate with two lit coin slots, between [t0] and [t1] up face [i]. */
private fun DrawScope.drawCoinDoor(f: CabFrame, i: Int, t0: Float, t1: Float, detail: Boolean) {
    val a = f.onFace(i, t0, 0.34f); val b = f.onFace(i, t0, 0.66f); val c = f.onFace(i, t1, 0.66f); val d = f.onFace(i, t1, 0.34f)
    drawPath(quad(a, b, c, d), Brush.verticalGradient(listOf(Color(0xFF5A5E64), Color(0xFF2E3136)), startY = d.y, endY = a.y))
    for (s in 0 until 2) {
        val x0 = 0.4f + s * 0.12f
        val p0 = f.onFace(i, t0 + (t1 - t0) * 0.62f, x0); val p1 = f.onFace(i, t0 + (t1 - t0) * 0.62f, x0 + 0.07f)
        val p2 = f.onFace(i, t0 + (t1 - t0) * 0.86f, x0 + 0.07f); val p3 = f.onFace(i, t0 + (t1 - t0) * 0.86f, x0)
        drawPath(quad(p0, p1, p2, p3), Color(0xFFFF8A3D))
    }
    if (detail) {
        val l = f.onFace(i, t0 + (t1 - t0) * 0.3f, 0.5f)
        drawCircle(Color(0xFF15161A), (f.scale * 0.008f).coerceAtLeast(1f), l)
    }
}

/** A speaker grille: rows of holes. */
private fun DrawScope.drawGrille(f: CabFrame, i: Int) {
    val r = (f.scale * 0.004f).coerceAtLeast(0.8f)
    for (row in 0 until 4) for (col in 0 until 14) {
        val p = f.onFace(i, 0.2f + row * 0.2f, 0.12f + col * 0.055f)
        drawCircle(Color.Black.copy(alpha = 0.55f), r, p)
    }
}

/**
 * The near side panel, over the rest: its paint (darker towards the floor), the game's art printed on it ([art], when
 * given — clipped to the panel, cropped to fill), the bright moulding round its edge ([lit] brightens it; the neo-retro
 * machine's is a lit strip).
 */
internal fun DrawScope.drawCabSide(f: CabFrame, paint: CabPaint, art: ImageBitmap?, lit: Float, dim: Float = 0f) {
    val m = f.m
    val u = f.nearU
    val path = Path().apply {
        m.pts.forEachIndexed { i, p -> val o = f.map(u, p.x, p.y); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
        close()
    }
    val k = (if (f.view.yawDeg >= 0f) 0.62f else 0.86f) * (1f - dim * 0.5f)
    val top = f.map(u, 0f, m.h); val bottom = f.map(u, 0f, 0f)
    drawPath(path, Brush.verticalGradient(listOf(shade(paint.side, k + 0.12f), shade(paint.side, k - 0.12f)), startY = top.y, endY = bottom.y))
    if (m.build == CabBuild.BARTOP) clipPath(path) {
        // The counter's side, in wood, under the machine.
        val c0 = f.map(u, 0f, 0f); val c1 = f.map(u, m.d, 0f); val c2 = f.map(u, m.d, COUNTER_TOP); val c3 = f.map(u, 0f, COUNTER_TOP)
        drawPath(quad(c0, c1, c2, c3), shade(CounterWood, k))
        drawLine(Color.White.copy(alpha = 0.12f), c3, c2, (f.scale * 0.004f).coerceAtLeast(1f))
    }
    if (art != null) clipPath(path) {
        // The panel's plane: forward (0…d) and up (0…h) on it; the art cropped to fill it, printed faintly into the paint.
        // (On the bartop only the machine above the counter carries it.)
        val y0 = if (m.build == CabBuild.BARTOP) COUNTER_TOP else 0f
        val o = f.map(u, 0f, y0); val fx = f.map(u, m.d, y0); val fy = f.map(u, 0f, m.h)
        val ax = fx - o; val ay = fy - o
        val pw = m.d; val ph = m.h - y0
        val ia = art.width.toFloat() / art.height
        val pa = pw / ph
        val (sw, sh) = if (ia > pa) Pair(art.height * pa, art.height.toFloat()) else Pair(art.width.toFloat(), art.width / pa)
        val sx0 = (art.width - sw) / 2f; val sy0 = (art.height - sh) / 2f
        // Image px (sx, sy) → panel (forward = sx/sw·d, up = (1 − sy/sh)·h) → screen.
        val mtx = androidx.compose.ui.graphics.Matrix()
        val a = ax.x / sw; val b = -ay.x / sh; val c = ax.y / sw; val d = -ay.y / sh
        val tx = o.x + ay.x - a * sx0 - b * sy0; val ty = o.y + ay.y - c * sx0 - d * sy0
        mtx.values[androidx.compose.ui.graphics.Matrix.ScaleX] = a
        mtx.values[androidx.compose.ui.graphics.Matrix.SkewX] = b
        mtx.values[androidx.compose.ui.graphics.Matrix.TranslateX] = tx
        mtx.values[androidx.compose.ui.graphics.Matrix.SkewY] = c
        mtx.values[androidx.compose.ui.graphics.Matrix.ScaleY] = d
        mtx.values[androidx.compose.ui.graphics.Matrix.TranslateY] = ty
        withTransform({ transform(mtx) }) {
            drawImage(art, alpha = 0.55f * (1f - dim * 0.6f))
        }
        // Printed onto the paint: the panel's light and its shading over the picture.
        drawPath(path, Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), startY = top.y, endY = bottom.y))
    }
    // The moulding (or lit strip) round the panel.
    val w = (f.scale * (if (m.build == CabBuild.NEO) 0.012f else 0.009f)).coerceAtLeast(1.2f)
    if (m.build == CabBuild.NEO) drawPath(path, paint.trim.copy(alpha = 0.25f * (0.4f + 0.6f * lit)), style = Stroke(w * 4f))
    drawPath(path, lerp(paint.trim, Color.Black, 0.35f * (1f - lit) + dim * 0.4f), style = Stroke(w))
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
