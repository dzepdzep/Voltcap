package com.shelf.launcher.ui

import androidx.compose.ui.res.imageResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.shelf.launcher.data.CardArtType
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/*
 * Update 330: Cardboard box — looking down into real cardboard storage boxes of your games.
 *
 * (Toni: "a top-down view into a cardboard box, two rows of games inside, each stored upright so that you mostly see the
 * spines, packed tightly like real games in storage; if one box is full, the rest go on in the next" — and: "a convincing
 * 3D cardboard box viewed almost directly from above… real wall thickness, folds, corrugated edges, good textures,
 * lighting, contact shadows".)
 * The box is built to measure (mm): two DVD-length rows deep, 420 mm across (Case size: narrower for bigger cases on the
 * screen, wider for smaller), walls 230 mm high and 5 mm thick, its four flaps folded out; a camera 1.5 m above looks
 * straight down (Camera angle: moved toward the front, so more of the back wall shows) and everything is drawn through
 * it (BoxCam) — what's higher up is nearer and bigger, so the walls' insides, the cases' ends between the rows and the
 * rim's cut edge show as they would. The cardboard is a generated kraft / white material (tools/box/gen_cardboard.py:
 * fibres, mottling, fluted walls, the flutes in the cut edge, lit from the upper left), each surface shaded by how it
 * faces the light, darker where walls meet the floor, with the rows' contact shadows on the floor.
 * Each case stands on its long edge, spine up, its real length and thickness (CaseCatalog / the console's spine), packed
 * side by side: as many as physically fit across the back row, then the front row, then Box 2, Box 3…
 * Browsing: the case you're on is lit and comes up a little out of its slot (the one before goes back down first). A
 * slides it straight up out of the box toward you, then turns it toward its cover, and its window opens with the case
 * itself as its piece and an insert sheet beside it (WinScenes.kt); B: the case flies back above its slot, turns back to
 * its spine and slides down into the same place. Y lifts it and turns it to its cover where it is (Y or B: back).
 * D-pad: ◀ ▶ along a row (past a box's side, the box before / after — at once or after a knock against its wall),
 * ▲ ▼ the other row, past them what lies there. Touch: tap a case to pick it, again to pull it out; swipe to change box;
 * the arrows under the box's label.
 */

/** Update 330: the boxes as laid out (read by the key handler), the case turned to its cover, the case being pulled out. */
internal object BoxNav {
    /** Per game (in the category's order): its box, its row (0 back, 1 front), and its middle across the row (0…1). */
    @Volatile var slotBox: IntArray = IntArray(0)
    @Volatile var slotRow: IntArray = IntArray(0)
    @Volatile var slotX: FloatArray = FloatArray(0)
    @Volatile var boxes: Int = 1
    /** Rows deep in the box as drawn (Update 400: 1 / 2 / 3, BoxRows). */
    @Volatile var rows: Int = 2
    /** The game whose case is turned to its cover, or null. */
    var flipped by mutableStateOf<String?>(null)
    @Volatile var current: String? = null
    @Volatile var canFlip: Boolean = false
    /** The game being pulled out of the box for its window (A / a tap), or null; back down when its window closes. */
    var pull by mutableStateOf<String?>(null)
    /** Pulls the case you're on out (set while the box is shown). */
    @Volatile var pullAsk: (() -> Unit)? = null
    /** How far the pulled-out case has turned toward its cover (its window's piece starts there): degrees from its spine. */
    const val OUT_TURN = 34f
    /** Update 402: the yaw (degrees from facing you) the case has at the moment A pulls it: 90 = spine-on in its slot (the flight starts there), less when it was turned to its cover with Y. */
    @Volatile var startYaw: Float = 90f
    /** A ◀ / ▶ that knocked against the box's wall (Box switching: After a knock). */
    @Volatile var knocked: Int = 0
}

/** The game nearest [x] (0…1 across) in [box]'s [row], or -1. */
private fun boxNearest(box: Int, row: Int, x: Float): Int {
    var best = -1; var bd = Float.MAX_VALUE
    val b = BoxNav.slotBox; val r = BoxNav.slotRow; val xs = BoxNav.slotX
    for (i in b.indices) if (b[i] == box && r[i] == row) { val d = abs(xs[i] - x); if (d < bd) { bd = d; best = i } }
    return best
}

/** The [n]th row by distance from [row] (n = 1: the next one down, 2: the one above it, ...). */
internal fun boxRowByDistance(row: Int, n: Int): Int { val step = (n + 1) / 2; return if (n % 2 == 1) row + step else row - step }

/** The first / last game of [box]'s [row] (the nearest row with games when this one is empty), or -1. */
private fun boxEnd(box: Int, row: Int, last: Boolean): Int {
    val b = BoxNav.slotBox; val r = BoxNav.slotRow
    var found = -1
    // (This row, else the nearest row that has games.)
    for (pass in 0 until BoxNav.rows.coerceAtLeast(2) * 2) {
        val want = if (pass == 0) row else boxRowByDistance(row, pass)
        for (i in b.indices) if (b[i] == box && r[i] == want) { if (!last) return i; found = i }
        if (found >= 0) return found
    }
    return found
}

/** Update 330: the box's D-pad — along a row, past its side into the box before / after, ▲ ▼ the other row. */
internal fun cardboardBoxKey(ctx: StyleKeyCtx): Boolean {
    if (BoxNav.pull != null) return true
    val b = BoxNav.slotBox
    val f = ctx.focus
    if (f !in b.indices || b.size != ctx.count) return false
    val box = b[f]; val row = BoxNav.slotRow[f]
    fun go(t: Int): Boolean {
        if (t >= 0 && t != f) { ctx.setFocus(t); ctx.sound(UiSound.MOVE) }
        return ctx.take()
    }
    fun sameRow(i: Int) = i in b.indices && b[i] == box && BoxNav.slotRow[i] == row
    return when (ctx.key) {
        Key.DirectionLeft, Key.DirectionRight -> {
            val dir = if (ctx.key == Key.DirectionRight) 1 else -1
            if (sameRow(f + dir)) { BoxNav.knocked = 0; go(f + dir) }
            else {
                val next = box + dir
                // The first box's left side: the home screen's own (its menu beside it).
                if (next < 0) return false
                // (Endless: from the last box on round to the first.)
                if (next >= BoxNav.boxes) {
                    if (StyleLoop.on && BoxNav.boxes > 1) { BoxNav.knocked = 0; return go(boxEnd(0, row, last = false)) }
                    if (ctx.first) EdgeBump.hit(dir); return ctx.take()
                }
                // (After a knock: the first press only knocks against the box's wall.)
                if (Features.boxSwitch.state.value == Features.BoxSwitch.KNOCK && BoxNav.knocked != dir) {
                    if (ctx.first) { BoxNav.knocked = dir; EdgeBump.hit(dir) }
                    return ctx.take()
                }
                BoxNav.knocked = 0
                go(boxEnd(next, row, last = dir < 0))
            }
        }
        Key.DirectionUp -> { val r = BoxRows.rowStep(row, -1, BoxNav.rows); val t = if (r != null) boxNearest(box, r, BoxNav.slotX[f]) else -1; if (t >= 0) go(t) else ctx.reach(true) }
        Key.DirectionDown -> { val r = BoxRows.rowStep(row, 1, BoxNav.rows); val t = if (r != null) boxNearest(box, r, BoxNav.slotX[f]) else -1; if (t >= 0) go(t) else ctx.reach(false) }
        else -> false
    }
}

/**
 * Update 330: A pulls the case you're on out of the box (its window opens once it's out); Y turns it to its cover (and
 * back); B turns it back first. True = handled.
 */
internal fun cardboardBoxButton(event: KeyEvent, sound: (UiSound) -> Unit): Boolean {
    val n = event.nativeKeyEvent
    val down = event.type == KeyEventType.KeyDown
    return when (event.key) {
        Key.ButtonA, Key.Enter, Key.DirectionCenter -> {
            val ask = BoxNav.pullAsk ?: return false
            if (BoxNav.current == null) return false
            if (down && n.repeatCount == 0 && BoxNav.pull == null) { sound(UiSound.SELECT); ask() }
            true
        }
        Key.ButtonY -> {
            if (down && n.repeatCount == 0 && BoxNav.canFlip && BoxNav.current != null && BoxNav.pull == null) {
                BoxNav.flipped = if (BoxNav.flipped == BoxNav.current) null else BoxNav.current
                sound(UiSound.OPEN)
            }
            BoxNav.canFlip
        }
        Key.ButtonB, Key.Back -> if (BoxNav.flipped != null) {
            if (down && n.repeatCount == 0) { BoxNav.flipped = null; sound(UiSound.BACK) }
            true
        } else false
        else -> false
    }
}

/** Update 330: the floor the box stands on (the box itself is drawn with the games). */
@Composable
internal fun CardboardBoxBackdrop(modifier: Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF3A3632), Color(0xFF26231F), Color(0xFF161412))))
        // Floorboards, faint.
        val n = 9
        for (i in 1 until n) {
            val x = size.width * i / n
            drawLine(Color.Black.copy(alpha = 0.18f), Offset(x, 0f), Offset(x + size.width * 0.04f, size.height), 2f)
        }
    }
}

private fun boxJitter(key: String, salt: Int): Float {
    var h = salt * 977 + 13
    for (ch in key) h = h * 31 + ch.code
    return ((h ushr 1) % 1000) / 1000f - 0.5f
}

@Composable
internal fun StyleCardboardBox(c: StyleCtx, modifier: Modifier = Modifier) {
    val g0 = c.game
    val loop = loopRowOn(c)
    SideEffect {
        StyleLoop.on = loop
        StyleNav.side = emptyList()
        StyleNav.top = emptyList()
        StyleNav.bar = emptyList()
        val k = g0?.stableKey
        if (BoxNav.current != k) {
            BoxNav.current = k
            if (BoxNav.flipped != null && BoxNav.flipped != k) BoxNav.flipped = null
        }
    }
    DisposableEffect(Unit) {
        onDispose { BoxNav.rows = 2; BoxNav.current = null; BoxNav.flipped = null; BoxNav.canFlip = false; BoxNav.pull = null; BoxNav.pullAsk = null; BoxNav.slotBox = IntArray(0) }
    }
    if (c.games.isEmpty()) StyleEmpty(modifier) else BoxScene(c, modifier)
}

/** One case's place in a box (mm, the box's inside: x across from its middle, y from its back wall): row, left edge, thickness, length, standing height. */
internal class BoxSlot(val box: Int, val row: Int, val x0: Float, val thick: Float, val y0: Float, val len: Float, val stand: Float) {
    val x1 get() = x0 + thick
    val y1 get() = y0 + len
}

/**
 * The box as built (mm) and the camera over it: a box [wMm] across and [dMm] deep inside, its walls [wallH] high and
 * [t] thick; the camera [hc] above the floor, over the middle across and [yc] from the back wall, looking straight down.
 * A point (x, y, z) is drawn at ([sx], [sy]) — nearer the camera (higher up), bigger.
 */
internal class BoxCam(val wMm: Float, val dMm: Float, val wallH: Float, val t: Float, val hc: Float, val yc: Float, val f: Float, val cx: Float, val cy: Float, val vh: Float = 0f, val rows: Int = 2, val lane: Float = BOX_LEN) {
    fun sx(x: Float, z: Float) = cx + f * x / (hc - z)
    fun sy(y: Float, z: Float) = cy + f * (y - yc) / (hc - z)
    fun o(x: Float, y: Float, z: Float) = Offset(sx(x, z), sy(y, z))
    /** Screen px per mm at height [z]. */
    fun k(z: Float) = f / (hc - z)
}

/** The longest case the box is made for (a DVD-size case), the gap between the rows, the room at the walls (mm). */
internal const val BOX_LEN = 191f
internal const val BOX_ROW_GAP = 26f
internal const val BOX_MARGIN = 3f

internal fun boxCam(wPx: Float, hPx: Float, tilt: Float, sizeK: Float, rows: Int = 2, lane: Float = BOX_LEN): BoxCam {
    // A real storage box: two DVD-length rows deep (Update 400: or 1 / 3 lanes of the category's longest case, BoxRows),
    // as wide as the case size asks (smaller cases: a wider box, more to a row).
    val d = BoxRows.depthFor(rows, lane)
    val w = 420f / sizeK
    val wallH = 250f; val t = 5f
    // (Close enough above it that the walls' insides and the cases' ends show its depth.)
    val hc = 950f
    // Tilted: the camera moves toward the front, so the back wall's inside shows more.
    val yc = d / 2f + d * 0.42f * tilt
    // As big as fits: the rim from just under the top of the screen to its foot, and never wider than the screen.
    val top = hPx * 0.035f; val bottom = hPx * 0.995f
    val fH = (bottom - top) * (hc - wallH) / (d + 2 * t)
    val fW = wPx * 0.96f * (hc - wallH) / (w + 2 * t)
    val f = minOf(fH, fW)
    // (A box shallower than the screen, one row or a narrow screen, sits in the middle of it, not at the top.)
    val boxH = f * (d + 2 * t) / (hc - wallH)
    val top2 = top + ((bottom - top) - boxH).coerceAtLeast(0f) / 2f
    val cy = top2 - f * (-t - yc) / (hc - wallH)
    return BoxCam(w, d, wallH, t, hc, yc, f, wPx / 2f, cy, hPx, rows, lane)
}

/** One game's case in the box (mm): its length (along the box's depth), standing height, thickness and Messy offset. */
internal class CaseMm(val len: Float, val stand: Float, val thick: Float, val extra: Float)

internal fun caseMm(g: Game, messy: Boolean): CaseMm {
    val mm = realCaseMm(g.platform)
    val len = (mm?.second ?: 190f).coerceIn(90f, BOX_LEN)
    val stand = (mm?.first ?: 135f).coerceIn(60f, 200f)
    // (Update 393: the thickness the selected 3D case has — CaseThickness — not a per-console spine guess.)
    val thick = (len * CaseThickness.orElse(g, shelfSpineAspect(g.platform)) * (if (messy) 1f + (boxJitter(g.stableKey, 5) + 0.5f) * 0.06f else 1f)).coerceIn(5f, 60f)
    val extra = if (messy) (boxJitter(g.stableKey, 4) + 0.5f) * 3f else 0f
    return CaseMm(len, stand, thick, extra)
}

/** Packs the games into boxes: across the back row as many as physically fit (each its own case's thickness), then the front row, then the next box. */
internal fun packBoxes(games: List<Game>, cam: BoxCam, gapMm: Float, messy: Boolean): List<BoxSlot> {
    val out = ArrayList<BoxSlot>(games.size)
    val usable = cam.wMm - 2 * BOX_MARGIN
    val rows = cam.rows.coerceAtLeast(1)
    val gapRows = BoxRows.gapFor(rows)
    var box = 0; var row = 0; var x = 0f
    for (g in games) {
        val m = caseMm(g, messy)
        // (Never longer than its lane: BoxRows only offers a row count the category's longest case fits.)
        val len = minOf(m.len, cam.lane)
        val stand = m.stand; val thick = m.thick; val extra = m.extra
        if (x > 0f && x + extra + thick > usable) {
            row++; x = 0f
            if (row >= rows) { box++; row = 0 }
        }
        // The back row against the back wall, the front row against the front wall, a middle row in the middle of its lane
        // (Messy: not quite).
        val push = if (messy) (boxJitter(g.stableKey, 6) + 0.5f) * 6f else 0f
        val y0 = when {
            row == 0 -> BOX_MARGIN + push
            row == rows - 1 -> cam.dMm - BOX_MARGIN - len - push
            else -> BOX_MARGIN + row * (cam.lane + gapRows) + (cam.lane - len) / 2f + (push - 3f)
        }
        out += BoxSlot(box, row, -cam.wMm / 2f + BOX_MARGIN + x + extra, thick, y0, len, stand)
        x += extra + thick + gapMm
    }
    return out
}

/** The cardboard's own surfaces (generated, tools/box/gen_cardboard.py): its face, its fluted walls, its cut edge. */
private class BoxMaterials(val face: android.graphics.Bitmap, val wall: android.graphics.Bitmap, val flute: android.graphics.Bitmap, val tint: android.graphics.ColorFilter?, val top: Long, val deep: Long)

@Composable
private fun rememberBoxMaterials(board: Features.BoxCardboard): BoxMaterials {
    val white = board == Features.BoxCardboard.WHITE
    val face = com.shelf.launcher.ui.cachedImageResource(if (white) com.shelf.launcher.R.drawable.box_white else com.shelf.launcher.R.drawable.box_kraft)
    val wall = com.shelf.launcher.ui.cachedImageResource(if (white) com.shelf.launcher.R.drawable.box_white_wall else com.shelf.launcher.R.drawable.box_kraft_wall)
    val flute = com.shelf.launcher.ui.cachedImageResource(com.shelf.launcher.R.drawable.box_flute)
    return remember(face, wall, flute, board) {
        BoxMaterials(face.asAndroidBitmap(), wall.asAndroidBitmap(), flute.asAndroidBitmap(),
            if (board == Features.BoxCardboard.DARK) android.graphics.LightingColorFilter(0xFF6A625A.toInt(), 0) else null, board.top, board.deep)
    }
}

/** Fills [path] with [bmp] repeated, [mmPerTile] mm a tile at [pxPerMm], turned [deg]; then [shade] over it (black, its alpha). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.texturedPath(
    path: Path, bmp: android.graphics.Bitmap, tint: android.graphics.ColorFilter?, pxPerMm: Float, mmPerTile: Float, deg: Float, shade: Float, origin: Offset
) {
    drawIntoCanvas { cv ->
        val sh = android.graphics.BitmapShader(bmp, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
        val s = pxPerMm * mmPerTile / bmp.width
        sh.setLocalMatrix(android.graphics.Matrix().apply { setScale(s, s); postRotate(deg); postTranslate(origin.x, origin.y) })
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG).apply { shader = sh; colorFilter = tint }
        cv.nativeCanvas.drawPath(path.asAndroidPath(), p)
    }
    if (shade > 0f) drawPath(path, Color.Black.copy(alpha = shade.coerceAtMost(1f)))
}

private fun quad(a: Offset, b: Offset, c: Offset, d: Offset) = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }

/**
 * The box itself, under its games: its four flaps folded out over the floor, its floor and the inside of its four walls
 * (each in its own light — lit from the upper left — with the shade where walls meet the floor and the walls' shadow),
 * the cut, fluted top edge of its walls, and the floor's shade along the rows' feet (contact shadows).
 */
@Composable
private fun BoxBody(cam: BoxCam, mat: BoxMaterials, slots: List<BoxSlot>, shownBox: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val c = cam
        val hw = c.wMm / 2f; val d = c.dMm; val H = c.wallH; val t = c.t
        val k0 = c.k(0f); val kH = c.k(H)
        // The flaps, folded out round the top and drooping a little: the long ones front and back, the short ones at the sides.
        val flap = c.wMm * 0.5f; val sideFlap = d * 0.5f
        val droop = 70f
        val fb = quad(c.o(-hw - t, -t, H), c.o(hw + t, -t, H), c.o(hw + t + 6f, -t - flap, H - droop), c.o(-hw - t - 6f, -t - flap, H - droop))
        val ff = quad(c.o(-hw - t, d + t, H), c.o(hw + t, d + t, H), c.o(hw + t + 6f, d + t + flap, H - droop), c.o(-hw - t - 6f, d + t + flap, H - droop))
        val fl = quad(c.o(-hw - t, -t + 4f, H), c.o(-hw - t, d + t - 4f, H), c.o(-hw - t - sideFlap, d + t - 10f, H - droop), c.o(-hw - t - sideFlap, -t + 10f, H - droop))
        val fr = quad(c.o(hw + t, -t + 4f, H), c.o(hw + t, d + t - 4f, H), c.o(hw + t + sideFlap, d + t - 10f, H - droop), c.o(hw + t + sideFlap, -t + 10f, H - droop))
        val origin = c.o(0f, 0f, H)
        // (The flaps' shadow on the floor under them, then the flaps — lit by how they face the light.)
        for ((p, sh) in listOf(fb to 0.06f, fl to 0.2f, fr to 0.02f, ff to 0.12f)) {
            drawPath(p, Color.Black.copy(alpha = 0.35f), style = androidx.compose.ui.graphics.drawscope.Stroke(c.k(H) * 14f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            texturedPath(p, mat.face, mat.tint, kH, 260f, 0f, sh, origin)
        }
        // The creases where the flaps fold.
        val crease = Color.Black.copy(alpha = 0.32f)
        drawLine(crease, c.o(-hw - t, -t, H), c.o(hw + t, -t, H), kH * 2.5f)
        drawLine(crease, c.o(-hw - t, d + t, H), c.o(hw + t, d + t, H), kH * 2.5f)
        drawLine(crease, c.o(-hw - t, -t, H), c.o(-hw - t, d + t, H), kH * 2.5f)
        drawLine(crease, c.o(hw + t, -t, H), c.o(hw + t, d + t, H), kH * 2.5f)

        // The floor.
        val floor = quad(c.o(-hw, 0f, 0f), c.o(hw, 0f, 0f), c.o(hw, d, 0f), c.o(-hw, d, 0f))
        texturedPath(floor, mat.face, mat.tint, k0, 260f, 90f, 0.22f, c.o(0f, 0f, 0f))
        // The fold down the middle of the floor (where its flaps meet), and the floor's shade at the walls.
        drawLine(Color.Black.copy(alpha = 0.14f), c.o(-hw, d / 2f, 0f), c.o(hw, d / 2f, 0f), k0 * 1.6f)
        drawLine(Color.White.copy(alpha = 0.08f), c.o(-hw, d / 2f + 2f, 0f), c.o(hw, d / 2f + 2f, 0f), k0 * 1.2f)
        val fl0 = c.o(-hw, 0f, 0f); val fr1 = c.o(hw, d, 0f)
        val ao = 34f * k0
        drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent), startX = fl0.x, endX = fl0.x + ao * 3f), fl0, Size(ao * 3f, fr1.y - fl0.y))
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f)), startX = fr1.x - ao, endX = fr1.x), Offset(fr1.x - ao, fl0.y), Size(ao, fr1.y - fl0.y))
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent), startY = fl0.y, endY = fl0.y + ao), fl0, Size(fr1.x - fl0.x, ao))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f)), startY = fr1.y - ao * 1.5f, endY = fr1.y), Offset(fl0.x, fr1.y - ao * 1.5f), Size(fr1.x - fl0.x, ao * 1.5f))

        // The rows' feet on the floor: a soft contact shadow along the gap between the rows (the light from the upper left).
        // Update 403: contact shadow PER CASE, at its own foot only (width = the case's thickness, a short soft fall-off into the
        // gap). The earlier row-wide bands were sized by the row's longest case (and the last case's side), so they drew hard-edged
        // dark rectangles on the floor that belonged to no case (reference photos 02-04).
        for (s in slots) {
            if (s.box != shownBox) continue
            val toGap = s.y1 < c.yc
            if (!toGap && s.y0 <= c.yc) continue
            val edgeY = if (toGap) s.y1 else s.y0
            val a = c.o(s.x0, edgeY, 0f); val b = c.o(s.x1 + 6f, edgeY, 0f)
            val depth = 14f * k0
            if (toGap) drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent), startY = a.y, endY = a.y + depth), Offset(a.x, a.y), Size(b.x - a.x, depth))
            else drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.16f)), startY = a.y - depth, endY = a.y), Offset(a.x, a.y - depth), Size(b.x - a.x, depth))
        }

        // The walls' insides: back (lit), right (facing the light), left (in its own shade), front (in shade).
        val back = quad(c.o(-hw, 0f, H), c.o(hw, 0f, H), c.o(hw, 0f, 0f), c.o(-hw, 0f, 0f))
        val front = quad(c.o(-hw, d, 0f), c.o(hw, d, 0f), c.o(hw, d, H), c.o(-hw, d, H))
        val left = quad(c.o(-hw, 0f, H), c.o(-hw, 0f, 0f), c.o(-hw, d, 0f), c.o(-hw, d, H))
        val right = quad(c.o(hw, 0f, H), c.o(hw, 0f, 0f), c.o(hw, d, 0f), c.o(hw, d, H))
        texturedPath(back, mat.wall, mat.tint, kH, 240f, 90f, 0.12f, origin)
        texturedPath(left, mat.wall, mat.tint, kH, 240f, 0f, 0.42f, origin)
        texturedPath(right, mat.wall, mat.tint, kH, 240f, 0f, 0.04f, origin)
        texturedPath(front, mat.wall, mat.tint, kH, 240f, 90f, 0.3f, origin)
        // Each wall darker toward the floor (less light gets down there).
        fun wallAo(p: Path, from: Offset, to: Offset) = drawPath(p, Brush.linearGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.38f)), start = from, end = to))
        wallAo(back, c.o(0f, 0f, H), c.o(0f, 0f, 0f))
        wallAo(left, c.o(-hw, d / 2f, H), c.o(-hw, d / 2f, 0f))
        wallAo(right, c.o(hw, d / 2f, H), c.o(hw, d / 2f, 0f))
        wallAo(front, c.o(0f, d, H), c.o(0f, d, 0f))
        // The inside corners' seams.
        val seam = Color.Black.copy(alpha = 0.45f)
        for ((x, y) in listOf(-hw to 0f, hw to 0f, -hw to d, hw to d)) drawLine(seam, c.o(x, y, 0f), c.o(x, y, H), 2f)

        // The walls' cut top edge: the board's thickness, its fluting showing.
        fun edge(a: Offset, b: Offset, outerA: Offset, outerB: Offset, deg: Float) {
            val p = quad(a, b, outerB, outerA)
            texturedPath(p, mat.flute, mat.tint, kH, t * 8f, deg, 0f, a)
            drawLine(Color.White.copy(alpha = 0.22f), outerA, outerB, 1.5f)
        }
        edge(c.o(-hw, 0f, H), c.o(hw, 0f, H), c.o(-hw - t, -t, H), c.o(hw + t, -t, H), 0f)
        edge(c.o(-hw, d, H), c.o(hw, d, H), c.o(-hw - t, d + t, H), c.o(hw + t, d + t, H), 0f)
        edge(c.o(-hw, 0f, H), c.o(-hw, d, H), c.o(-hw - t, -t, H), c.o(-hw - t, d + t, H), 90f)
        edge(c.o(hw, 0f, H), c.o(hw, d, H), c.o(hw + t, -t, H), c.o(hw + t, d + t, H), 90f)
        // (Layout options › Wear and tape: a strip of tape over the back edge, scuffs along the rim.)
        if (Features.boxWear.state.value) {
            val tape = quad(c.o(-30f, -t - 60f, H - 6f), c.o(30f, -t - 60f, H - 6f), c.o(30f, 40f, H - 2f), c.o(-30f, 40f, H - 2f))
            drawPath(tape, Color(0xFFE8DCB8).copy(alpha = 0.55f))
            for (i in 0 until 9) {
                val x = -hw + c.wMm * ((i * 37) % 100) / 100f
                drawLine(Color.Black.copy(alpha = 0.12f), c.o(x, -t * 0.5f, H), c.o(x + 14f, -t * 0.5f, H), kH * 1.5f)
            }
        }
    }
}

/** The cases' ends toward the camera (between the rows) and the sheen and shade over the spines, drawn round the spines. */
@Composable
private fun BoxCaseEnds(cam: BoxCam, slots: List<BoxSlot>, games: List<Game>, shownBox: Int, lifted: Int, mat: BoxMaterials) {
    Canvas(Modifier.fillMaxSize()) {
        val c = cam
        for (i in slots.indices) {
            val s = slots[i]
            if (s.box != shownBox || i == lifted) continue
            // The end that faces the gap between the rows (seen from above it: the camera is over the gap).
            // (Update 400: whichever end faces the camera's axis; a case lying across it shows neither.)
            val y = if (s.y1 < c.yc) s.y1 else s.y0
            val seen = s.y1 < c.yc || s.y0 > c.yc
            if (!seen) continue
            val p = quad(c.o(s.x0, y, s.stand), c.o(s.x1, y, s.stand), c.o(s.x1, y, 0f), c.o(s.x0, y, 0f))
            val tone = boxSideTone(games.getOrNull(i)?.platform)
            drawPath(p, Brush.linearGradient(listOf(tone.first, tone.second), start = c.o(0f, y, s.stand), end = c.o(0f, y, 0f)))
            drawLine(Color.Black.copy(alpha = 0.6f), c.o(s.x1, y, s.stand), c.o(s.x1, y, 0f), 1f)
            drawLine(Color.Black.copy(alpha = 0.35f), c.o(s.x0, y, s.stand), c.o(s.x0, y, 0f), 1f)
            // Its top edge catching the light.
            drawLine(Color.White.copy(alpha = 0.16f), c.o(s.x0, y, s.stand), c.o(s.x1, y, s.stand), 1f)
        }
    }
}

/**
 * The side face of each case toward the middle of the box (Update 395): the camera is over the middle, so a case left of it
 * shows its right side and one right of it its left side wherever a lower neighbour doesn't hide it. Drawn from the same
 * geometry (x, y, standing height) as the spine, so a tall case beside a short one stands as a solid body, not a floating
 * edge over the dark floor. Lit from the upper left: faces turned left are lighter; dark where they meet the floor.
 */
/**
 * Update 403: the colour of a case's side / end by what it is made of (lit edge, then the shade toward the floor). Cardboard boxes
 * (N64, GBA, SNES...) show cardboard, plastic cases a mid grey: the earlier near-black gradient on every case read as hollow
 * black slabs between the spines (reference photos 02-04). Shading stays restrained (no black).
 */
internal fun boxSideTone(platform: String?): Pair<Color, Color> =
    // (Update 408, D97: a printed box end is clearly darker than the tan floor, else it reads as an empty ledge of the floor.)
    if (CaseCatalog.of(platform, CaseRegion.US)?.cardboard == true) Color(0xFF7D6B55) to Color(0xFF3B3022)
    else Color(0xFF8A8D96) to Color(0xFF3C3E45)

/** Share of the case height kept below its top edge for the side face (Update 405: a thin attached rim, never a slab to the floor). */
internal const val BOX_SIDE_KEEP = 0.7f

internal fun boxSideFaceX(s0: Float, s1: Float): Pair<Float, Boolean> =
    if ((s0 + s1) / 2f > 0f) s0 to true else s1 to false

@Composable
private fun BoxCaseSides(cam: BoxCam, slots: List<BoxSlot>, games: List<Game>, shownBox: Int, lifted: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val c = cam
        for (i in slots.indices) {
            val s = slots[i]
            if (s.box != shownBox || i == lifted) continue
            val (xb, facesLeft) = boxSideFaceX(s.x0, s.x1)
            // Update 405: only the rim of the wall (the top ~30 % of its fall to the floor), attached to the top face. The whole wall down
            // to the floor read as an empty slab / detached block beside and under the cases (owner, 405).
            val zb = s.stand * BOX_SIDE_KEEP
            val p = quad(c.o(xb, s.y0, s.stand), c.o(xb, s.y1, s.stand), c.o(xb, s.y1, zb), c.o(xb, s.y0, zb))
            val tone = boxSideTone(games.getOrNull(i)?.platform)
            val topC = if (facesLeft) tone.first else mix(tone.first, tone.second, 0.45f)
            drawPath(p, Brush.verticalGradient(listOf(topC, tone.second), startY = c.sy(s.y0, s.stand), endY = c.sy(s.y1, zb)))
            // The rim where the top face turns into the side: the plastic edge catching the light.
            drawLine(Color.White.copy(alpha = if (facesLeft) 0.2f else 0.1f), c.o(xb, s.y0, s.stand), c.o(xb, s.y1, s.stand), 1f)
        }
    }
}

/**
 * The plastic's sheen over each case's own top face, and a little shade on the cases nearest the walls.
 * Update 403: drawn PER CASE inside that case's own rectangle. It used to be one rectangle over each whole row (its bounding
 * box, empty floor included) with a 45 % black bar at the row's left end: that was the flat dark plate / lighter block seen on
 * the floor beside and below the cases (reference photos 02-04).
 */
@Composable
private fun BoxSheen(cam: BoxCam, slots: List<BoxSlot>, shownBox: Int, lifted: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val c = cam
        val w = c.wMm / 2f
        for (i in slots.indices) {
            val s = slots[i]
            if (s.box != shownBox || i == lifted) continue
            val a = c.o(s.x0, s.y0, s.stand); val b = c.o(s.x1, s.y1, s.stand)
            val sz = Size(b.x - a.x, b.y - a.y)
            if (sz.width <= 0f || sz.height <= 0f) continue
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.White.copy(alpha = 0.04f), Color.Transparent),
                start = a, end = Offset(a.x + sz.width * 0.6f, b.y)), a, sz)
            // Darker toward the walls (less light reaches in there): only the case's own face, never the floor.
            val edge = (1f - ((w - kotlin.math.abs((s.x0 + s.x1) / 2f)) / 90f)).coerceIn(0f, 1f)
            if (edge > 0f) drawRect(Color.Black.copy(alpha = 0.22f * edge), a, sz)
        }
    }
}

@Composable
private fun BoxScene(c: StyleCtx, modifier: Modifier) {
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val needPhysical by rememberUpdatedState(c.actions.needPhysical)
    val latestFocus by rememberUpdatedState(c.focus)
    val count = c.games.size
    val animate = rememberAnimationsEnabled()
    val speed = Features.boxSpeed.state.value.k
    val messy = Features.boxArrange.state.value == Features.BoxArrange.MESSY
    val spacing = Features.boxSpacing.state.value / 100f
    val liftK = Features.boxLift.state.value / 100f
    val tilt = (Features.boxTilt.state.value / 32f).coerceIn(0f, 1f)
    val sizeK = Features.boxCaseSize.state.value.k
    val board = Features.boxCardboard.state.value
    val labelStyle = Features.boxLabel.state.value
    val focus = c.focus.coerceIn(0, count - 1)
    val gKey = c.games[focus].stableKey
    val windowOn = GamePopup.game != null
    val density = LocalDensity.current
    val titles = LocalTitleOptions.current
    val mat = rememberBoxMaterials(board)

    // A / a tap on the case you're on: pull it out, then its window opens (with the case itself as its piece).
    val focusKey by rememberUpdatedState(gKey)
    DisposableEffect(Unit) {
        BoxNav.pullAsk = { if (GamePopup.game == null && BoxNav.pull == null) { BoxNav.startYaw = boxPickStartYaw(BoxNav.flipped == focusKey); BoxNav.flipped = null; BoxNav.pull = focusKey } }
        onDispose { BoxNav.pullAsk = null }
    }
    SideEffect { BoxNav.canFlip = true }
    val pull = BoxNav.pull
    LaunchedEffect(pull) {
        val k = pull ?: return@LaunchedEffect
        // (Update 402: no 900 ms wait for an in-box rise: the window opens at once and the shared flight is the one movement.)
        kotlinx.coroutines.yield()
        if (c.games.none { it.stableKey == k }) { BoxNav.pull = null; return@LaunchedEffect }
        // The window shows the case itself, whatever the look (MediaInspect.force — cleared when the window closes).
        MediaInspect.testShell = null; MediaInspect.testPose = null
        MediaInspect.forceSeq++; MediaInspect.force = k
        open()
        // (A game set to start at once opens no window: back down it goes.)
        kotlinx.coroutines.delay(1200)
        if (GamePopup.game?.stableKey != k) { BoxNav.pull = null; if (MediaInspect.force == k) MediaInspect.force = null }
    }
    // Its window closed (the case has flown back above its slot): down into its place.
    val popupKey = GamePopup.game?.stableKey
    LaunchedEffect(popupKey) {
        if (popupKey == null && BoxNav.pull != null) {
            val k = BoxNav.pull
            kotlinx.coroutines.delay(60)
            if (GamePopup.game == null && BoxNav.pull == k) BoxNav.pull = null
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val gapMm = 0.6f * spacing
        // Update 400: how many rows deep (Auto / 1 / 2 / 3): a physical decision from the category's cases and this screen.
        val rowsPref = Features.boxRows.state.value
        val plan = remember(c.games, sizeK, gapMm, messy) { BoxRows.plan(c.games, 420f / sizeK, gapMm, messy) }
        val decision = remember(rowsPref, plan, wPx, hPx, tilt, sizeK, density.density) { BoxRows.decide(rowsPref, plan, wPx, hPx, tilt, sizeK, density.density) }
        val cam = remember(wPx, hPx, tilt, sizeK, decision.rows, plan) { boxCam(wPx, hPx, tilt, sizeK, decision.rows, BoxRows.laneFor(decision.rows, plan)) }
        val slots = remember(c.games, cam, gapMm, messy) { packBoxes(c.games, cam, gapMm, messy) }
        val boxes = (slots.lastOrNull()?.box ?: 0) + 1
        SideEffect {
            BoxNav.slotBox = IntArray(slots.size) { slots[it].box }
            BoxNav.slotRow = IntArray(slots.size) { slots[it].row }
            BoxNav.slotX = FloatArray(slots.size) { (slots[it].x0 + slots[it].thick / 2f + cam.wMm / 2f) / cam.wMm }
            BoxNav.boxes = boxes
            BoxNav.rows = cam.rows
        }
        val box = slots.getOrNull(focus)?.box ?: 0
        val firstOf = remember(slots) { IntArray(boxes) { b -> slots.indexOfFirst { it.box == b }.coerceAtLeast(0) } }
        val liftedIdx = if (BoxNav.pull == gKey || BoxNav.flipped == gKey) focus else -1

        AnimatedContent(
            targetState = c.categoryLabel to box, label = "boxSwitch",
            transitionSpec = {
                if (!animate) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else if (targetState.first != initialState.first) fadeIn(tween(0)) togetherWith fadeOut(tween(0)) // (394: a new category swaps at once)
                else {
                    // The next box slides in from the side you went (the boxes stand side by side on the floor).
                    val dir = if (targetState.second >= initialState.second) 1 else -1
                    (slideInHorizontally(tween((460 / speed).toInt())) { it * dir }) togetherWith (slideOutHorizontally(tween((460 / speed).toInt())) { -it * dir })
                }
            },
            modifier = Modifier.fillMaxSize().pointerInput(boxes, firstOf) {
                var acc = 0f
                var done = false
                // (One box a swipe, however long the swipe.)
                detectHorizontalDragGestures(onDragStart = { acc = 0f; done = false }) { ch, dx ->
                    ch.consume(); acc += dx
                    if (BoxNav.pull != null || done) return@detectHorizontalDragGestures
                    val s = 140f
                    val b = BoxNav.slotBox.getOrNull(latestFocus) ?: 0
                    if (acc <= -s) { done = true; if (b + 1 < boxes) select(firstOf[b + 1]) }
                    if (acc >= s) { done = true; if (b > 0) select(firstOf[b - 1]) }
                }
            }
        ) { (_, shownBox) ->
            Box(Modifier.fillMaxSize()) {
                BoxBody(cam, mat, slots, shownBox)
                BoxCaseSides(cam, slots, c.games, shownBox, if (shownBox == box) liftedIdx else -1)
                BoxCaseEnds(cam, slots, c.games, shownBox, if (shownBox == box) liftedIdx else -1, mat)
                // The spines (the case you're on, and one coming out, drawn by itself over them).
                for (i in slots.indices) {
                    val sl = slots[i]
                    if (sl.box != shownBox) continue
                    val g = c.games[i]
                    key(g.stableKey) {
                        BoxCase(
                            c, g, i, sl, cam, on = i == focus, liftK = liftK, messy = messy, speed = speed, tilt = tilt, animate = animate,
                            onTap = { if (latestFocus == i) BoxNav.pullAsk?.invoke() else if (BoxNav.pull == null) select(i) },
                            needPhysical = needPhysical
                        )
                    }
                }
                BoxSheen(cam, slots, shownBox, if (shownBox == box) liftedIdx else -1)
            }
        }

        // The box's label (on the flap at its left, or over its corner when there's no room) and the game's tag (at its right).
        val caption = CategoryTitle.captionOn(c.state.appearance)
        if (caption) CategoryTitle.Native(null)
        val rimL = cam.sx(-cam.wMm / 2f - cam.t, cam.wallH)
        val sideRoom = with(density) { rimL.toDp() }
        val roomy = sideRoom > 190.dp
        val text = buildString {
            if (caption) append(c.categoryLabel.uppercase()).append("\n")
            append("BOX ${box + 1}")
            if (boxes > 1) append(" OF $boxes")
            // (A forced row count the cases / screen can't take: said, not silently ignored.)
            BoxRows.limitText(decision)?.let { append("\n").append(it) }
        }
        Column(
            Modifier.align(if (roomy) Alignment.CenterStart else Alignment.TopStart)
                .then(if (roomy) Modifier.width(sideRoom).padding(horizontal = 14.dp) else Modifier.padding(start = 10.dp, top = 6.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (labelStyle == Features.BoxLabel.MARKER) Text(
                text, color = Color(0xFF1C140C).copy(alpha = 0.86f), fontSize = 24.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Cursive, textAlign = TextAlign.Center, modifier = Modifier.rotate(-4f)
            ) else if (labelStyle == Features.BoxLabel.STICKER) Box(
                Modifier.rotate(-3f).clip(RoundedCornerShape(2.dp)).background(Color(0xFFF4EFE3))
                    .border(1.dp, Color(0xFF8E7F63), RoundedCornerShape(2.dp)).padding(horizontal = 14.dp, vertical = 6.dp)
            ) { Text(text, color = Color(0xFF2B2620), fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center) }
            if (boxes > 1) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BoxArrow("◀", box > 0) { if (box > 0 && BoxNav.pull == null) select(firstOf[box - 1]) }
                BoxArrow("▶", box < boxes - 1) { if (box < boxes - 1 && BoxNav.pull == null) select(firstOf[box + 1]) }
            }
        }
        val g = c.games[focus]
        Column(
            Modifier.align(if (roomy) Alignment.CenterEnd else Alignment.TopEnd)
                .then(if (roomy) Modifier.width(sideRoom).padding(horizontal = 12.dp) else Modifier.padding(end = 10.dp, top = 6.dp).widthIn(max = maxWidth * 0.4f))
                .graphicsLayer { alpha = if (windowOn || BoxNav.pull != null) 0f else 1f; rotationZ = 2f }
                .clip(RoundedCornerShape(3.dp)).background(Color(0xFFF7F1E2))
                .border(1.dp, Color(0xFF8E7F63), RoundedCornerShape(3.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            FullText(titles.display(g.name), color = Color(0xFF2B2620), fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 3)
            Spacer(Modifier.height(3.dp))
            listOfNotNull(titles.console(g.platform).takeIf { it.isNotBlank() }, stylePlaytime(c.playtime(g), c.lastPlayed(g) != null),
                c.progress(g)?.let { (e, t) -> "$e / $t ★" }).forEach { FullText(it, color = Color(0xFF5B5246), fontSize = 12.sp, maxLines = 1) }
            Spacer(Modifier.height(4.dp))
            Text("A  pull it out\nY  look at its cover", color = Color(0xFF8A7D69), fontSize = 10.sp, lineHeight = 13.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun BoxArrow(t: String, enabled: Boolean, onTap: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFF4EFE3).copy(alpha = if (enabled) 0.95f else 0.35f))
            .pointerInput(enabled) { detectTapGestures { if (enabled) onTap() } },
        contentAlignment = Alignment.Center
    ) { Text(t, color = Color(0xFF2B2620), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

/**
 * One case in the box, standing spine-up in its slot: its spine seen from above. The one you're on is lit and comes up
 * a little out of its slot (after the one before has gone back down); pulled out, it slides straight up out of the box
 * toward you, then turns toward its cover — and back down into the same slot.
 */
@Composable
private fun BoxCase(
    c: StyleCtx, g: Game, index: Int, sl: BoxSlot, cam: BoxCam, on: Boolean, liftK: Float, messy: Boolean, speed: Float, tilt: Float,
    animate: Boolean, onTap: () -> Unit, needPhysical: (Game) -> Unit
) {
    val k = g.stableKey
    val density = LocalDensity.current
    val physical = c.state.physicalMedia[k]
    LaunchedEffect(k) { if (physical == null) needPhysical(g) }
    val pulled = on && BoxNav.pull == k
    // (Update 402: a case pulled from its cover-turned state keeps that turn until the flight takes over.)
    val flippedNow = on && (BoxNav.flipped == k || (pulled && BoxNav.startYaw < 89f))
    // 0 in its slot … PEEK lit … 1 out of the box and turned.
    val peek = 0.1f
    // Update 402: pulled from its slot it does NOT rise and turn inside the box first (that was a separate cutscene before the
    // flight): the flight starts at once from the lit case where it stands, spine-on.
    val target = when { flippedNow -> 1f; on -> peek; else -> 0f }
    val rise = remember { Animatable(0f) }
    LaunchedEffect(target) {
        if (!animate) { rise.snapTo(target); return@LaunchedEffect }
        // The case before goes back down first, then this one comes up.
        if (target in 0.01f..peek && rise.value < 0.01f) kotlinx.coroutines.delay((160 / speed).toLong())
        val ms = ((if (target > peek || rise.value > peek) 900 else 360) / speed).toInt()
        rise.animateTo(target, tween(ms, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
    }
    val flip by animateFloatAsState(if (flippedNow) 1f else 0f, if (animate) tween((520 / speed).toInt(), delayMillis = (500 / speed).toInt()) else tween(0), label = "boxFlip")
    var warm by remember(k) { mutableStateOf(false) }
    LaunchedEffect(on) { if (on) { kotlinx.coroutines.delay(220); warm = true } else warm = false }
    val jr = if (messy) boxJitter(k, 1) * 1.6f else 0f
    // Its spine, from above: the top face of the case at its standing height.
    val z = sl.stand
    val left = cam.sx(sl.x0, z); val right = cam.sx(sl.x1, z)
    val top = cam.sy(sl.y0, z); val bottom = cam.sy(sl.y1, z)
    val lit = on || rise.value > 0.001f
    val x0 = with(density) { left.toDp() }; val y0 = with(density) { top.toDp() }
    val wDp = with(density) { (right - left).toDp() }; val hDp = with(density) { (bottom - top).toDp() }
    if (!lit) Box(
        Modifier.offset(x0, y0).size(wDp, hDp)
            .graphicsLayer { rotationZ = jr }
            // Update 409 (D97): the case is a solid body — where its spine picture is narrower than its slot (SpineSlot keeps the
            // picture's own shape) the margin is the case's own side material, not a see-through gap whose shading / hairlines
            // read as a thin rectangle round the case.
            .background(boxSideTone(g.platform).let { mix(it.first, it.second, 0.35f) })
            .pointerInput(index) { detectTapGestures { onTap() } }
    ) {
        GameSpine(g, physical, Modifier.fillMaxSize())
        // The hairline gap to the next case.
        // (Update 409, D97: only the two vertical separators — the full rectangle read as an outline round the case.)
        Box(Modifier.fillMaxSize().drawBehind {
            val lw = 0.6.dp.toPx(); val col = Color.Black.copy(alpha = 0.55f)
            drawLine(col, Offset(0f, 0f), Offset(0f, size.height), lw)
            drawLine(col, Offset(size.width, 0f), Offset(size.width, size.height), lw)
        })
    }
    else {
        // (Update 402: no dark / coloured slot rectangle behind a lifted case — the empty place is just the box floor.)
        val aspect = remember(k) { case3dAspect(g, false) }
        // Lifting: straight up first (toward you — nearer the camera, so bigger), then turning toward its cover.
        val outMm = (cam.wallH - z + 150f) * liftK
        val caseLenPx = bottom - top
        val caseW = caseLenPx * aspect
        val cxPx = (left + right) / 2f; val cyPx = (top + bottom) / 2f
        Box(
            Modifier.zIndex(5f)
                .offset(with(density) { (cxPx - caseW / 2f).toDp() }, y0)
                .size(with(density) { caseW.toDp() }, hDp)
                .graphicsLayer {
                    val r = rise.value
                    val up = smoothUp(r) * outMm
                    // Where its middle is drawn at that height (perspective: away from the camera's axis as it nears it).
                    val kz = cam.k(z + up) / cam.k(z)
                    scaleX = kz; scaleY = kz
                    val axX = cam.cx; val axY = cam.sy(cam.yc, 0f)
                    // (Only part of the drift away from the camera's axis: lifted straight up, it should stay over its slot
                    // to the eye — the full drift sent a case at the box's corner flying off over the flap.)
                    // Update 341: …but never off the screen (a case from the front row, lifted and turned to its cover,
                    // was cut off at the bottom edge).
                    val m = 10.dp.toPx()
                    fun keepIn(t: Float, mid: Float, half: Float, room: Float): Float {
                        if (room <= 0f) return t
                        val lo = half + m - mid; val hi = room - m - half - mid
                        return if (lo > hi) (lo + hi) / 2f else t.coerceIn(lo, hi)
                    }
                    translationX = keepIn((cxPx - axX) * (kz - 1f) * 0.3f, cxPx, caseW * kz / 2f, cam.cx * 2f)
                    translationY = keepIn((cyPx - axY) * (kz - 1f) * 0.3f, cyPx, caseLenPx * kz / 2f, cam.vh)
                    rotationZ = jr * (1f - r)
                }
                .pointerInput(index) { detectTapGestures { if (flip > 0.5f) BoxNav.flipped = null else onTap() } }
        ) {
            // Its shadow on the cases below, further off and softer the higher it is.
            Canvas(Modifier.fillMaxSize()) {
                val r = smoothUp(rise.value)
                val sw = (right - left) * (1f + 0.6f * r)
                // Update 409 (D97): a feathered shadow (layers growing out, each faint) — the hard rounded rectangle, wider than the
                // 3D case, showed as a thin dark rectangle round the lifted case's neighbours.
                val base = 0.2f * (1f - r * 0.5f)
                val layers = 6
                val step = 2.5f + 5f * r
                for (li in 0 until layers) {
                    val grow = (layers - 1 - li) * step
                    drawRoundRect(Color.Black.copy(alpha = base / layers),
                        Offset(size.width / 2f - sw / 2f + 30f * r - grow, 22f * r - grow),
                        Size(sw + 2f * grow, size.height + 2f * grow), androidx.compose.ui.geometry.CornerRadius(8f + grow))
                }
            }
            val cover = c.cover(g)
            val back = remember(k, physical) { defaultCaseBack(g, physical, cover, aspect) }
            Box(Modifier.fillMaxSize().gameOrigin(k, on)) {
                Case3D(g, cover, physical, 0f, aspect, Modifier.fillMaxSize(), pitch = -3f * tilt,
                    yawOf = { 90f - BoxNav.OUT_TURN * smoothTurn(rise.value) - 55f * flip }, realFront = true, back = back, fill = 1f)
            }
            // (Update 402: no rectangle round the lit case — it sat off the lifted case's own bounds and read as a block.
            // Focus is shown by the lift itself.)
        }
        if (warm && flip <= 0.02f && !pulled) PrewarmInspectCase(g, c.state, Modifier.offset(x0, y0).size(with(density) { caseW.toDp() }, hDp))
    }
}

/** Lifting: up over the first 60 % of the way (the peek is the start of it). */
private fun smoothUp(r: Float): Float { val t = (r / 0.6f).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
/** …then turning toward its cover over the rest. */
private fun smoothTurn(r: Float): Float { val t = ((r - 0.55f) / 0.45f).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

private fun mix(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

/** Update 330: Settings › Home › Layout options (Cardboard box). Every one its own "box_…" Features pref. */
@Composable
internal fun CardboardBoxSettings() {
    MutedNote(
        "Looking down into cardboard storage boxes: two tightly packed rows of cases, spines up; when a box is full the rest go " +
            "on in the next. The case you're on comes up a little; A pulls it right out and its window opens beside it, B puts it " +
            "back in its slot. ◀ ▶ along a row — past the box's side, the box before / after — ▲ ▼ the other row, Y turns the " +
            "case to its cover. Tap to pick, tap again to pull it out, swipe to change box."
    )
    Spacer(Modifier.height(10.dp))
    StyleChoice("Case size", Features.BoxCaseSize.values(), Features.boxCaseSize.state.value, { it.label }) { Features.boxCaseSize.value = it }
    PercentSlider("Spacing", Features.boxSpacing.state.value, { Features.boxSpacing.value = it.coerceIn(70, 150) }, min = 70, max = 150, valueText = { "$it%" })
    StyleChoice("Rows", Features.BoxRowsPref.values(), Features.boxRows.state.value, { it.label }) { Features.boxRows.value = it }
    MutedNote("Auto: three rows for compact cases (DS, 3DS, Game Boy Advance boxes) when a category fills more than two and they stay readable on this screen, otherwise two. " +
        "A forced 3 that the cases or the screen can't take shows two and says so; 1 gives a single long row.")
    StyleChoice("Packing", Features.BoxArrange.values(), Features.boxArrange.state.value, { it.label }) { Features.boxArrange.value = it }
    MutedNote("Neat: straight and evenly packed. Messy: a little crooked and uneven, as after a rummage.")
    PercentSlider("Camera angle", Features.boxTilt.state.value, { Features.boxTilt.value = it.coerceIn(0, 32) }, min = 0, max = 32,
        valueText = { if (it == 0) "Straight down" else "$it°" })
    PercentSlider("How far it comes out", Features.boxLift.state.value, { Features.boxLift.value = it.coerceIn(40, 160) }, min = 40, max = 160, valueText = { "$it%" })
    StyleChoice("Speed", Features.BoxSpeed.values(), Features.boxSpeed.state.value, { it.label }) { Features.boxSpeed.value = it }
    StyleChoice("Box label", Features.BoxLabel.values(), Features.boxLabel.state.value, { it.label }) { Features.boxLabel.value = it }
    StyleChoice("Past a box's side", Features.BoxSwitch.values(), Features.boxSwitch.state.value, { it.label }) { Features.boxSwitch.value = it }
    MutedNote("At once: ◀ ▶ past the box's side goes straight into the box before / after. After a knock: the first press knocks against its wall, the next goes on.")
    StyleChoice("Cardboard", Features.BoxCardboard.values(), Features.boxCardboard.state.value, { it.label }) { Features.boxCardboard.value = it }
    Toggle(Icons.Filled.ViewWeek, "Wear and tape", "A strip of tape over the back edge and a few scuffs along the rim. Off: a clean new box.", Features.boxWear)
}

/** Update 402: the yaw (degrees from facing you) a case has when A pulls it: spine-on (90) in its slot, or as turned to its cover with Y. */
internal fun boxPickStartYaw(turnedToCover: Boolean): Float = if (turnedToCover) 90f - BoxNav.OUT_TURN - 55f else 90f
