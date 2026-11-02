package com.shelf.launcher.ui

import androidx.compose.ui.res.imageResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
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
import androidx.compose.foundation.layout.requiredSize
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
    /** Update 415: the games left to right as laid out (position -> game index) and back (game index -> position); identity in Original order. */
    @Volatile var order: IntArray = IntArray(0)
    @Volatile var pos: IntArray = IntArray(0)
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
        // (Update 415: the leftmost / rightmost by position across the row, whatever order the cases were laid out in.)
        var bx = if (last) -1f else 2f
        for (i in b.indices) if (b[i] == box && r[i] == want) { val xx = BoxNav.slotX[i]; if (if (last) xx > bx else xx < bx) { bx = xx; found = i } }
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
    // (Update 415: the neighbour along the row is the next case as laid out, which is the next game only in Original order.)
    val neighbour = { dir: Int -> BoxNav.pos.getOrNull(f)?.let { BoxNav.order.getOrNull(it + dir) } ?: (f + dir) }
    return when (ctx.key) {
        Key.DirectionLeft, Key.DirectionRight -> {
            val dir = if (ctx.key == Key.DirectionRight) 1 else -1
            if (sameRow(neighbour(dir))) { BoxNav.knocked = 0; go(neighbour(dir)) }
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
            // (Update 421: Classic opens the details window straight away — no case is pulled out for a window that never comes.)
            if (PopupStyle.opensClassic(com.shelf.launcher.data.HomeLayout.BOX.name)) return false
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

/** Update 415: a cardboard divider standing between two size groups (mm, the box's inside, same frame as [BoxSlot]). */
internal class BoxDivider(val box: Int, val row: Int, val x0: Float, val y0: Float, val len: Float, val stand: Float) {
    val x1 get() = x0 + BOX_DIVIDER_MM
    val y1 get() = y0 + len
}

/** The result of packing: [slots] per game (the game's own index), [dividers], and [order] = the games left to right as laid out (position -> game index). */
internal class BoxPack(val slots: List<BoxSlot>, val dividers: List<BoxDivider>, val order: IntArray)

internal const val BOX_DIVIDER_MM = 3f
/** Cases whose lengths fall in the same 20 mm step are one size group (a few mm of difference is not a different size). */
private const val SIZE_STEP_MM = 20f

internal fun sizeGroup(g: Game): Int = (caseMm(g, false).len / SIZE_STEP_MM).roundToInt()

/** The games' left-to-right order: the category's own ([bySize] false), or grouped by case size, groups in order of first appearance and each group in the category's order. */
internal fun boxOrder(games: List<Game>, bySize: Boolean): IntArray {
    if (!bySize) return IntArray(games.size) { it }
    val groups = games.map { sizeGroup(it) }
    val first = LinkedHashMap<Int, Int>()
    for (gr in groups) first.getOrPut(gr) { first.size }
    return games.indices.sortedWith(compareBy({ first.getValue(groups[it]) }, { it })).toIntArray()
}

/** Packs the games into boxes: across the back row as many as physically fit (each its own case's thickness), then the front row, then the next box. */
internal fun packBoxes(games: List<Game>, cam: BoxCam, gapMm: Float, messy: Boolean): List<BoxSlot> =
    packBoxesFull(games, cam, gapMm, messy, bySize = false, dividers = false).slots

/**
 * Update 415: the same packing with an order. [bySize] lays the cases out grouped by size (real dimensions untouched: only the order
 * changes), [dividers] stands a thin cardboard card between two groups that share a row (never where the row wraps).
 */
internal fun packBoxesFull(games: List<Game>, cam: BoxCam, gapMm: Float, messy: Boolean, bySize: Boolean, dividers: Boolean): BoxPack {
    val slots = arrayOfNulls<BoxSlot>(games.size)
    val divs = ArrayList<BoxDivider>()
    val order = boxOrder(games, bySize)
    val usable = cam.wMm - 2 * BOX_MARGIN
    val rows = cam.rows.coerceAtLeast(1)
    val gapRows = BoxRows.gapFor(rows)
    // Update 422 (owner screenshot: one box with 20 cases in the back row and a single case alone in the front row, the floor
    // empty): the last, partly filled box shares its games evenly over its rows instead of filling the back row first. Full
    // boxes are packed exactly as before. A first pass finds where the last box starts; the second packs that box's rows to an
    // even share of its width (never past the box's own width).
    var balanceFrom = Int.MAX_VALUE; var balanceLimit = usable
    run {
        var b = 0; var r = 0; var xx = 0f; var startOfBox = 0
        val widths = FloatArray(order.size)
        order.forEachIndexed { pos, idx ->
            val m = caseMm(games[idx], messy); val need = m.extra + m.thick
            widths[pos] = need + gapMm
            if (xx > 0f && xx + need > usable) { r++; xx = 0f; if (r >= rows) { b++; r = 0; startOfBox = pos } }
            xx += need + gapMm
        }
        // (A handful of cases — under three a row — stay together in the back row, as updates 400-416 checked on the Thor.)
        if (rows > 1 && order.size - startOfBox >= rows * 3) {
            // The last box: an even share each (at least its widest case, so every row gets one).
            var total = 0f; var widest = 0f
            for (pos in startOfBox until order.size) { total += widths[pos]; widest = maxOf(widest, widths[pos]) }
            val share = total / rows
            // (A nearly full last box is left as packed: an even share there could push its last case into a new box.)
            if (total <= rows * usable * 0.85f) balanceFrom = startOfBox
            balanceLimit = minOf(usable, maxOf(share + widest * 0.5f, widest))
        }
    }
    var box = 0; var row = 0; var x = 0f
    // The back row against the back wall, the front row against the front wall, a middle row in the middle of its lane (Messy: not quite).
    fun yFor(len: Float, push: Float) = when {
        row == 0 -> if (rows == 1) minOf(BOX_MARGIN + push, cam.dMm - BOX_MARGIN - len) else BOX_MARGIN + push
        row == rows - 1 -> cam.dMm - BOX_MARGIN - len - push
        else -> BOX_MARGIN + row * (cam.lane + gapRows) + (cam.lane - len) / 2f + (push - 3f)
    }
    var prev: Game? = null; var prevSlot: BoxSlot? = null
    for ((pos, idx) in order.withIndex()) {
        val g = games[idx]
        val m = caseMm(g, messy)
        val rowLimit = if (pos >= balanceFrom) balanceLimit else usable
        // (Never longer than its lane: BoxRows only offers a row count the category's longest case fits.)
        val len = minOf(m.len, cam.lane)
        val stand = m.stand; val thick = m.thick; val extra = m.extra
        val div = dividers && bySize && prev != null && x > 0f && sizeGroup(prev) != sizeGroup(g)
        val need = extra + thick + (if (div) BOX_DIVIDER_MM + gapMm else 0f)
        if (x > 0f && x + need > rowLimit && (row < rows - 1 || x + need > usable)) {
            row++; x = 0f
            if (row >= rows) { box++; row = 0 }
        } else if (div) {
            val ps = prevSlot!!
            val dl = maxOf(ps.len, len)
            // (A divider is a card a little lower than the cases it stands between.)
            divs += BoxDivider(box, row, -cam.wMm / 2f + BOX_MARGIN + x, yFor(dl, 0f), dl, minOf(ps.stand, stand) * 0.85f)
            x += BOX_DIVIDER_MM + gapMm
        }
        val push = if (messy) (boxJitter(g.stableKey, 6) + 0.5f) * 6f else 0f
        val sl = BoxSlot(box, row, -cam.wMm / 2f + BOX_MARGIN + x + extra, thick, yFor(len, push), len, stand)
        slots[idx] = sl
        x += extra + thick + gapMm
        prev = g; prevSlot = sl
    }
    val packed = slots.map { it!! }
    // v0.6.66 (D128): a category that fills only part of one row (1-3 games, a short category) stood in the box's back-left
    // corner with the rest of the floor bare. Such a lone row now stands in the middle of the box — centred across, and in
    // the middle of the floor of a deeper box — as cases put into an empty box would. Rows, order and D-pad steps unchanged.
    if (packed.isNotEmpty() && packed.all { it.box == 0 && it.row == 0 }) {
        val dx = ((usable - (x - gapMm)) / 2f).coerceAtLeast(0f)
        val longest = packed.maxOf { it.len }
        val dy = if (rows > 1) ((cam.dMm - longest) / 2f - BOX_MARGIN).coerceAtLeast(0f) else 0f
        if (dx > 0f || dy > 0f) return BoxPack(
            packed.map { BoxSlot(it.box, it.row, it.x0 + dx, it.thick, it.y0 + dy, it.len, it.stand) },
            divs.map { BoxDivider(it.box, it.row, it.x0 + dx, it.y0 + dy, it.len, it.stand) },
            order
        )
    }
    return BoxPack(packed, divs, order)
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
private fun BoxBody(cam: BoxCam, mat: BoxMaterials, slots: List<BoxSlot>, dividers: List<BoxDivider>, shownBox: Int) {
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
        // (The flaps' shadow on the table under them, then the flaps — lit by how they face the light.)
        // Update 421 (owner screenshot: "heavy black outline round the box and flaps"): the shadow was a 14 mm black STROKE
        // round every flap, which read as a drawn outline. Now a soft contact shadow only: the flap's own shape, offset
        // away from the light (upper left) in a few faint layers, so it fades out instead of ending in a line.
        val shadowStep = kH * 5f
        for (p in listOf(fb, fl, fr, ff)) {
            for (i in 1..4) translate(left = shadowStep * i * 0.6f, top = shadowStep * i) { drawPath(p, Color.Black.copy(alpha = 0.045f)) }
        }
        for ((p, sh) in listOf(fb to 0.04f, fl to 0.14f, fr to 0.0f, ff to 0.08f)) texturedPath(p, mat.face, mat.tint, kH, 260f, 0f, sh, origin)
        // The flaps' cut outer edges: a thin lit line (board edge catching the light), not a dark keyline.
        for (p in listOf(fb, fl, fr, ff)) drawPath(p, Color(0xFFF3DFB8).copy(alpha = 0.16f), style = androidx.compose.ui.graphics.drawscope.Stroke(kH * 1.2f))
        // The creases where the flaps fold: a soft dark fold with its lit lip next to it.
        val crease = Color(0xFF3A2814).copy(alpha = 0.16f)
        val lip = Color(0xFFFFF0D0).copy(alpha = 0.12f)
        drawLine(crease, c.o(-hw - t, -t, H), c.o(hw + t, -t, H), kH * 1.6f)
        drawLine(crease, c.o(-hw - t, d + t, H), c.o(hw + t, d + t, H), kH * 1.6f)
        drawLine(crease, c.o(-hw - t, -t, H), c.o(-hw - t, d + t, H), kH * 1.6f)
        drawLine(crease, c.o(hw + t, -t, H), c.o(hw + t, d + t, H), kH * 1.6f)
        drawLine(lip, c.o(-hw - t, -t - 3f, H), c.o(hw + t, -t - 3f, H), kH * 1.2f)
        drawLine(lip, c.o(-hw - t - 3f, -t, H), c.o(-hw - t - 3f, d + t, H), kH * 1.2f)

        // The floor.
        val floor = quad(c.o(-hw, 0f, 0f), c.o(hw, 0f, 0f), c.o(hw, d, 0f), c.o(-hw, d, 0f))
        texturedPath(floor, mat.face, mat.tint, k0, 260f, 90f, 0.14f, c.o(0f, 0f, 0f))
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

        // Update 415: cardboard dividers between size groups: the top edge and the face turned toward the middle of the box.
        for (dv in dividers) {
            if (dv.box != shownBox) continue
            val z = dv.stand
            val top = quad(c.o(dv.x0, dv.y0, z), c.o(dv.x1, dv.y0, z), c.o(dv.x1, dv.y1, z), c.o(dv.x0, dv.y1, z))
            val faceX = if (dv.x1 <= 0f) dv.x1 else dv.x0
            val face = quad(c.o(faceX, dv.y0, 0f), c.o(faceX, dv.y1, 0f), c.o(faceX, dv.y1, z), c.o(faceX, dv.y0, z))
            drawPath(face, Color(0xFF9C7440))
            drawPath(top, Color(0xFFD2AC74))
            drawPath(top, Color(0xFF3A2814).copy(alpha = 0.14f), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
        }

        // The walls' insides: back (lit), right (facing the light), left (in its own shade), front (in shade).
        val back = quad(c.o(-hw, 0f, H), c.o(hw, 0f, H), c.o(hw, 0f, 0f), c.o(-hw, 0f, 0f))
        val front = quad(c.o(-hw, d, 0f), c.o(hw, d, 0f), c.o(hw, d, H), c.o(-hw, d, H))
        val left = quad(c.o(-hw, 0f, H), c.o(-hw, 0f, 0f), c.o(-hw, d, 0f), c.o(-hw, d, H))
        val right = quad(c.o(hw, 0f, H), c.o(hw, 0f, 0f), c.o(hw, d, 0f), c.o(hw, d, H))
        // (Update 421: the inside was near-black on the shaded walls; real kraft board stays brown in shade.)
        texturedPath(back, mat.wall, mat.tint, kH, 240f, 90f, 0.08f, origin)
        texturedPath(left, mat.wall, mat.tint, kH, 240f, 0f, 0.26f, origin)
        texturedPath(right, mat.wall, mat.tint, kH, 240f, 0f, 0.02f, origin)
        texturedPath(front, mat.wall, mat.tint, kH, 240f, 90f, 0.18f, origin)
        // Each wall a little darker toward the floor (less light gets down there), warm brown rather than black.
        fun wallAo(p: Path, from: Offset, to: Offset) = drawPath(p, Brush.linearGradient(listOf(Color.Transparent, Color(0xFF2A1B0C).copy(alpha = 0.24f)), start = from, end = to))
        wallAo(back, c.o(0f, 0f, H), c.o(0f, 0f, 0f))
        wallAo(left, c.o(-hw, d / 2f, H), c.o(-hw, d / 2f, 0f))
        wallAo(right, c.o(hw, d / 2f, H), c.o(hw, d / 2f, 0f))
        wallAo(front, c.o(0f, d, H), c.o(0f, d, 0f))
        // The inside corners' seams.
        // (Update 421: a soft fold where two walls meet, not a black line.)
        val seam = Color(0xFF2A1B0C).copy(alpha = 0.18f)
        for ((x, y) in listOf(-hw to 0f, hw to 0f, -hw to d, hw to d)) drawLine(seam, c.o(x, y, 0f), c.o(x, y, H), kH * 3f)

        // The walls' cut top edge: the board's thickness, its fluting showing.
        fun edge(a: Offset, b: Offset, outerA: Offset, outerB: Offset, deg: Float) {
            val p = quad(a, b, outerB, outerA)
            texturedPath(p, mat.flute, mat.tint, kH, t * 8f, deg, 0f, a)
            // (Update 421, Thor: the fluting alone read as a dark line drawn round the opening; a cut kraft edge is light —
            // the corrugation shows faintly through the board colour, lit on its outer lip.)
            drawPath(p, Color(mat.top).copy(alpha = 0.62f))
            drawLine(Color.White.copy(alpha = 0.28f), outerA, outerB, 1.5f)
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


/**
 * Update 403: the colour of a case's side / end by what it is made of (lit edge, then the shade toward the floor). Cardboard boxes
 * (N64, GBA, SNES...) show cardboard, plastic cases a mid grey: the earlier near-black gradient on every case read as hollow
 * black slabs between the spines (reference photos 02-04). Shading stays restrained (no black).
 */
internal fun boxSideTone(platform: String?, spine: Int? = null): Pair<Color, Color> {
    // (Update 408, D97: a printed box end is clearly darker than the tan floor, else it reads as an empty ledge of the floor.)
    val base = if (CaseCatalog.of(platform, CaseRegion.US)?.cardboard == true) Color(0xFF7D6B55) to Color(0xFF3B3022)
    else Color(0xFF8A8D96) to Color(0xFF3C3E45)
    if (spine == null) return base
    // Update 410 (D97): the case's own body / ends / sides are made of what its spine is printed on (a white DS case has white
    // ends, a black PS2 case black ones, a red box red): the generic grey / cardboard ends beside a printed spine read as hollow
    // empty faces. A quarter of the material tone keeps a black case from collapsing into a black hole.
    val s = Color(spine or 0xFF000000.toInt())
    val lit = mix(mix(s, Color.Black, 0.12f), base.first, 0.2f)
    return lit to mix(mix(s, Color.Black, 0.55f), base.second, 0.25f)
}

/** Update 410 (D97): each game's spine colour once its picture has loaded (observable: the end / side faces repaint). */
internal object BoxTones {
    val map = androidx.compose.runtime.mutableStateMapOf<String, Int>()
}

/** Share of the case height kept below its top edge for the side face (Update 405: a thin attached rim, never a slab to the floor). */
internal const val BOX_SIDE_KEEP = 0.7f

internal fun boxSideFaceX(s0: Float, s1: Float): Pair<Float, Boolean> =
    if ((s0 + s1) / 2f > 0f) s0 to true else s1 to false



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
        val bySize = Features.boxOrder.state.value == Features.BoxOrder.BY_SIZE
        val dividerOn = Features.boxDividers.state.value
        val pack = remember(c.games, cam, gapMm, messy, bySize, dividerOn) { packBoxesFull(c.games, cam, gapMm, messy, bySize, dividerOn) }
        val slots = pack.slots
        val boxes = (slots.lastOrNull()?.box ?: 0) + 1
        // Update 415: once this category has been still for a moment, the spine pictures of the categories L2 / R2 go to next are decoded
        // ahead (low priority, off the UI thread, only scans already known), so a cold category's box finds them in memory.
        val warmCtx = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(c.categoryLabel) {
            kotlinx.coroutines.delay(BOX_PREWARM_IDLE_MS)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { warmNeighbourSpines(warmCtx, c.state) }
        }
        SideEffect {
            BoxNav.slotBox = IntArray(slots.size) { slots[it].box }
            BoxNav.slotRow = IntArray(slots.size) { slots[it].row }
            BoxNav.slotX = FloatArray(slots.size) { (slots[it].x0 + slots[it].thick / 2f + cam.wMm / 2f) / cam.wMm }
            BoxNav.order = pack.order
            BoxNav.pos = IntArray(pack.order.size).also { p -> pack.order.forEachIndexed { n, g -> p[g] = n } }
            BoxNav.boxes = boxes
            BoxNav.rows = cam.rows
        }
        val box = slots.getOrNull(focus)?.box ?: 0
        val firstOf = remember(slots, pack) { IntArray(boxes) { b -> pack.order.firstOrNull { slots[it].box == b } ?: 0 } }
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
                BoxBody(cam, mat, slots, pack.dividers, shownBox)
                // Update 414: the cases of this box appear TOGETHER, once each has its picture and spine (BoxReveal): no dark waiting
                // strips, no cases spawning one by one. Cases seen before are warm (shown at once); a missing / slow picture waits at most BOX_REVEAL_MAX_MS.
                val inBox = remember(slots, shownBox) { slots.indices.filter { slots[it].box == shownBox }.map { c.games[it].stableKey } }
                // Composed a few per frame (nearest the case you are on first), so a crowded box never freezes one frame; drawn in slot order.
                val rank = remember(slots, shownBox) {
                    val idx = slots.indices.filter { slots[it].box == shownBox }.sortedBy { kotlin.math.abs(it - latestFocus) }
                    IntArray(slots.size) { Int.MAX_VALUE }.also { r -> idx.forEachIndexed { n, i -> r[i] = n } }
                }
                // (Update 416: sliced even when every case was seen before; the warm box was composed in ONE frame, a ~0.5 s freeze on the Pocket Classic. Hidden until all are composed.)
                var upto by remember(slots, shownBox) { androidx.compose.runtime.mutableIntStateOf(BOX_COMPOSE_FIRST) }
                if (upto < inBox.size) LaunchedEffect(slots, shownBox) {
                    while (upto < inBox.size) { androidx.compose.runtime.withFrameNanos { }; upto = minOf(inBox.size, upto + BOX_COMPOSE_PER_FRAME) }
                }
                BoxReveal(inBox, animate, composed = upto >= inBox.size) { ready ->
                    // The spines (the case you are on, and one coming out, drawn by itself over them).
                    for (i in slots.indices) {
                        val sl = slots[i]
                        if (sl.box != shownBox || rank[i] >= upto) continue
                        val g = c.games[i]
                        key(g.stableKey) {
                            BoxCase(
                                c, g, i, sl, cam, on = i == focus, liftK = liftK, messy = messy, speed = speed, tilt = tilt, animate = animate,
                                onTap = { if (latestFocus == i) BoxNav.pullAsk?.invoke() else if (BoxNav.pull == null) select(i) },
                                needPhysical = needPhysical, onReady = { ready(g.stableKey) }
                            )
                        }
                    }
                }
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
        // Update 417: the tag only sits in the free strip beside the box (never over cases): full card when roomy, a compact title
        // card in a narrower strip, none when there is no strip (the popup / the pulled-out case's window carry the details).
        if (sideRoom > BOX_TAG_MIN_DP.dp) Column(
            Modifier.align(Alignment.CenterEnd)
                .width(sideRoom).padding(horizontal = if (roomy) 12.dp else 5.dp)
                .graphicsLayer { alpha = if (windowOn || BoxNav.pull != null) 0f else 1f; rotationZ = if (roomy) 2f else 0f }
                .clip(RoundedCornerShape(3.dp)).background(Color(0xFFF7F1E2))
                .border(1.dp, Color(0xFF8E7F63), RoundedCornerShape(3.dp))
                .padding(horizontal = if (roomy) 10.dp else 6.dp, vertical = 6.dp)
        ) {
            FullText(titles.display(g.name), color = Color(0xFF2B2620), fontSize = if (roomy) 16.sp else 13.sp, lineHeight = if (roomy) 18.sp else 15.sp, fontWeight = FontWeight.Bold, maxLines = if (roomy) 3 else 5)
            if (roomy) {
                Spacer(Modifier.height(3.dp))
                listOfNotNull(titles.console(g.platform).takeIf { it.isNotBlank() }, stylePlaytime(c.playtime(g), c.lastPlayed(g) != null),
                    c.progress(g)?.let { (e, t) -> "$e / $t ★" }).forEach { FullText(it, color = Color(0xFF5B5246), fontSize = 12.sp, maxLines = 1) }
                Spacer(Modifier.height(4.dp))
                Text("A  pull it out\nY  look at its cover", color = Color(0xFF8A7D69), fontSize = 10.sp, lineHeight = 13.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/** Update 417: the narrowest free strip (dp) beside the box that still takes the compact title card. */
internal const val BOX_TAG_MIN_DP = 84
internal const val BOX_PREWARM_IDLE_MS = 600L
internal const val BOX_PREWARM_MAX = 60

/** Update 415: decodes the real spine scans (already looked up) of the categories next to the current one, nearest first, at most [BOX_PREWARM_MAX]. */
private fun warmNeighbourSpines(context: android.content.Context, state: com.shelf.launcher.LauncherUiState) {
    if (LookStyles.spineForAll() || Features.spinesOwn.state.value) return
    val stops = com.shelf.launcher.data.CategoryStops.stops(state)
    if (stops.size < 2) return
    val cur = stops.indexOfFirst { (sp, pl) -> sp == state.specialFilter && pl == state.platformFilter }
    for (step in intArrayOf(1, -1)) {
        val (sp, pl) = stops[com.shelf.launcher.data.CategoryStops.stepFrom(cur, step, stops.size)]
        val games = runCatching { state.copy(specialFilter = sp, platformFilter = pl).visibleGames }.getOrDefault(emptyList())
        val urls = games.take(BOX_PREWARM_MAX).mapNotNull { g -> HomeScans.of(g.stableKey)?.firstOrNull { it.first == "Spine" }?.second as? String }
        if (urls.isNotEmpty()) warmSoft(context, urls, 600)
    }
}

/** Update 414: cases whose picture + spine were ready once (main thread only): they are drawn at once next time, no hold. */
internal object BoxReady {
    val warm = HashSet<String>()
    val faces = HashSet<String>()
    fun note(k: String) { if (warm.size > 4000) warm.clear(); warm.add(k) }
}
internal const val BOX_REVEAL_MAX_MS = 900L
internal const val BOX_COMPOSE_FIRST = 6
internal const val BOX_COMPOSE_PER_FRAME = 4
internal const val BOX_REVEAL_FADE_MS = 120

/**
 * Update 414: holds the cases of one box (composed, so their art decodes) invisible until all are whole, then shows them together
 * (a short fade, none in Lite). All warm already: shown from the very first frame. Bounded: after [BOX_REVEAL_MAX_MS] whatever is there shows.
 */
@Composable
private fun BoxReveal(keys: List<String>, animate: Boolean, composed: Boolean = true, content: @Composable (ready: (String) -> Unit) -> Unit) {
    // Update 416: "seen before" no longer skips the gate: the bitmaps of a case seen before may have left the memory caches, and a case
    // drawn then is the dark waiting strip (a dark slab for a frame or two on a warm revisit). Warm cases are ready within a frame or two
    // and show without a fade; the others fade in as before.
    val allWarm = remember { keys.all { it in BoxReady.warm } }
    var revealed by remember { mutableStateOf(false) }
    val pending = remember { HashSet<String>().apply { addAll(keys) } }
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        if (revealed && composed) 1f else 0f, if (animate && !allWarm) tween(BOX_REVEAL_FADE_MS) else tween(0), label = "boxReveal"
    )
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(BOX_REVEAL_MAX_MS); revealed = true }
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) {
        content { k ->
            BoxReady.note(k)
            if (pending.remove(k) && pending.isEmpty()) revealed = true
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
    animate: Boolean, onTap: () -> Unit, needPhysical: (Game) -> Unit, onReady: () -> Unit = {}
) {
    val k = g.stableKey
    val density = LocalDensity.current
    val physical = c.state.physicalMedia[k]
    LaunchedEffect(k) { if (physical == null) needPhysical(g) }
    val pulled = on && BoxNav.pull == k
    // (Update 402: a case pulled from its cover-turned state keeps that turn until the flight takes over.)
    val flippedNow = on && (BoxNav.flipped == k || (pulled && BoxNav.startYaw < 89f))
    // 0 in its slot … PEEK lit … 1 out of the box and turned.
    // Update 411: no automatic "peek" — the focused case is only highlighted (the others dim a little) and stays in its slot until
    // A / Y moves it. The case is ONE Case3D object resting and lifted, so nothing is swapped or spawned part-way up.
    val target = if (flippedNow) 1f else 0f
    val rise = remember { Animatable(0f) }
    LaunchedEffect(target) {
        if (!animate) { rise.snapTo(target); return@LaunchedEffect }
        // Time proportional to the way left, so a reversal mid-way retraces the same path without a jump.
        val ms = (900 * kotlin.math.abs(target - rise.value) / speed).toInt().coerceAtLeast(1)
        rise.animateTo(target, tween(ms, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
    }
    // Update 414: a case whose spine is not made yet draws nothing (never the dark waiting strip / a slab); it shows the frame it is whole.
    var whole by remember(k) { mutableStateOf(false) }
    // (Its front / back pictures only matter once it can be picked up: the case you are on and its neighbours, then any seen before.)
    var wanted by remember(k) { mutableStateOf(k in BoxReady.faces) }
    val faces = wanted || on || pulled || kotlin.math.abs(index - c.focus) <= 2
    LaunchedEffect(faces) { if (faces) { wanted = true; BoxReady.faces.add(k) } }
    // Update 424 (owner: "an artificial dark / brown wash over the cases"): no dimming of the cases you are not on — every case keeps
    // its own colours; the one you are on is marked by a fine neutral edge only.
    val flip by animateFloatAsState(if (flippedNow) 1f else 0f, if (animate) tween((520 / speed).toInt(), delayMillis = (500 / speed).toInt()) else tween(0), label = "boxFlip")
    var warm by remember(k) { mutableStateOf(false) }
    LaunchedEffect(on) { if (on) { kotlinx.coroutines.delay(220); warm = true } else warm = false }
    val jr = if (messy) boxJitter(k, 1) * 1.6f else 0f
    // Its spine, from above: the top face of the case at its standing height.
    val z = sl.stand
    val left = cam.sx(sl.x0, z); val right = cam.sx(sl.x1, z)
    val top = cam.sy(sl.y0, z); val bottom = cam.sy(sl.y1, z)
    val x0 = with(density) { left.toDp() }; val y0 = with(density) { top.toDp() }
    val wDp = with(density) { (right - left).toDp() }; val hDp = with(density) { (bottom - top).toDp() }
    // Update 411: ONE real Case3D (the catalog's thickness / console geometry, spine on the spine face, solid top / bottom / back)
    // resting and lifted alike; the slot-sized outer box takes the taps and carries the lift, the case itself is as wide as its front.
    val aspect = remember(k) { case3dAspect(g, false) }
    val outMm = (cam.wallH - z + 150f) * liftK
    val caseLenPx = bottom - top
    // Update 424 (owner screenshots: cases overlapping, poking into the next row): Case3D draws with its own camera 3.4 case-heights
    // away, so a case standing spine-on came out 1 / q bigger than its slot (a DVD case 12 % wider and longer, an N64 box 26 %): the
    // neighbours were drawn over each other. Its room is q of the slot, so the spine it draws is exactly the slot again (and the flight
    // out of the box starts from that same size).
    val q = boxCaseFit(aspect)
    val caseW = caseLenPx * aspect
    val cxPx = (left + right) / 2f; val cyPx = (top + bottom) / 2f
    // Update 424: resting, the case is drawn through the box's own camera (top, the side and end that face the camera, contact
    // shadow); Case3D takes over the moment it lifts (a short cross-fade, same size and place).
    var spineBmp by remember(k) { mutableStateOf<android.graphics.Bitmap?>(null) }
    val towardRight = (sl.x0 + sl.x1) / 2f > 0f
    val sideX = if (towardRight) sl.x0 else sl.x1
    val sidePx = kotlin.math.abs(cam.sx(sideX, z) - cam.sx(sideX, 0f))
    val cover = c.cover(g)
    val sideCover = rememberSoftBitmap(if (towardRight && sidePx > 3f) cover else null, 320, trim = true)
    val toneArgb = BoxTones.map[k]
    // The case nearer the middle is in front of the one beside it, toward the wall (looking down from above the middle).
    val zRest = 1f - (kotlin.math.abs((sl.x0 + sl.x1) / 2f) / (cam.wMm / 2f)).coerceIn(0f, 1f) * 0.9f
    Box(
        Modifier.offset(x0, y0).size(wDp, hDp)
            .zIndex(if (rise.value > 0.001f) 5f else zRest)
            .graphicsLayer { alpha = if (whole || rise.value > 0.001f) 1f else 0f }
            .graphicsLayer {
                val r = rise.value
                val up = smoothUp(r) * outMm
                // Where its middle is drawn at that height (perspective: away from the camera's axis as it nears it).
                val kz = cam.k(z + up) / cam.k(z)
                scaleX = kz; scaleY = kz
                val axX = cam.cx; val axY = cam.sy(cam.yc, 0f)
                // (Only part of the drift away from the camera's axis, and never off the screen.)
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
            .pointerInput(index) { detectTapGestures { if (flip > 0.5f) BoxNav.flipped = null else onTap() } },
        contentAlignment = Alignment.Center
    ) {
        val restA = if (spineBmp == null) 0f else (1f - rise.value * 14f).coerceIn(0f, 1f)
        // The resting case, through the box's camera.
        if (restA > 0.001f) Canvas(Modifier.matchParentSize().graphicsLayer { alpha = restA }) {
            drawIntoCanvas { cv ->
                BoxCaseDraw.draw(cv.nativeCanvas, cam, sl, left, top, spineBmp!!, sideCover, toneArgb, g.platform, towardRight, sideX)
            }
        }
        // Its shadow on the cases below (only while it is up: nothing is drawn for a resting case).
        Canvas(Modifier.requiredSize(with(density) { caseW.toDp() }, hDp)) {
            val r = smoothUp(rise.value)
            if (r < 0.001f) return@Canvas
            val sw = (right - left) * (1f + 0.6f * r)
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
        val back = remember(k, physical) { defaultCaseBack(g, physical, cover, aspect) }
        Box(Modifier.requiredSize(with(density) { (caseW * q).toDp() }, with(density) { (caseLenPx * q).toDp() }).gameOrigin(k, on)
            .graphicsLayer { alpha = 1f - restA }) {
            Case3D(g, cover, physical, 0f, aspect, Modifier.fillMaxSize(), pitch = -3f * tilt,
                yawOf = { 90f - BoxNav.OUT_TURN * smoothTurn(rise.value) - 55f * flip }, shadeOf = { 0f },
                realFront = true, back = back, fill = 1f, onReady = { whole = true; onReady() }, readyNeedsFront = false, faces = faces,
                onSpine = { b -> spineBmp = b; if (BoxTones.map[k] == null) BoxTones.map[k] = BoxCaseDraw.average(b) })
        }
        // Update 417 / 424: the case you are on: a fine neutral lit edge round its top (no warm sheen over its art, no fill), visible
        // on black and on white spines alike. Follows the case outline (slot while resting, its width as it turns out).
        val edge by animateFloatAsState(if (on) 1f else 0f, if (animate) tween((180 / speed).toInt()) else tween(0), label = "boxEdge")
        if (edge > 0.01f) Canvas(Modifier.requiredSize(with(density) { caseW.toDp() }, hDp)) {
            val w = (right - left) + (caseW * q - (right - left)) * smoothTurn(rise.value)
            val a = edge * (1f - 0.35f * smoothUp(rise.value))
            val px = 1.dp.toPx()
            val tl = Offset((size.width - w) / 2f, 0f)
            val cr = androidx.compose.ui.geometry.CornerRadius(2f * px)
            drawRoundRect(Color.Black.copy(alpha = 0.35f * a), tl - Offset(px, px), Size(w + 2f * px, size.height + 2f * px), cr,
                style = androidx.compose.ui.graphics.drawscope.Stroke(px))
            drawRoundRect(Color.White.copy(alpha = 0.75f * a), tl, Size(w, size.height), cr,
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f * px))
        }
    }
    if (warm && flip <= 0.02f && !pulled) PrewarmInspectCase(g, c.state, Modifier.offset(x0, y0).size(with(density) { caseW.toDp() }, hDp))
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
    StyleChoice("Order", Features.BoxOrder.values(), Features.boxOrder.state.value, { it.label }) { Features.boxOrder.value = it }
    MutedNote("Original order: the cases follow the category's sort order. Grouped by size: cases of the same real size stand together (each group keeps the sort order), so rows pack without gaps; the selection still moves along what you see.")
    if (Features.boxOrder.state.value == Features.BoxOrder.BY_SIZE) Toggle(Icons.Filled.ViewWeek, "Cardboard dividers", "A thin cardboard card between size groups that share a row.", Features.boxDividers)
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

/**
 * Update 424: how much of its room a Case3D standing spine-on may take so its spine comes out exactly the room's size: Case3D
 * looks from 3.4 case-heights away, and the spine of a case [aspect] wide (front width / height) stands half a front width nearer
 * that eye than the case's middle — drawn 3.4 / (3.4 − aspect / 2) times bigger.
 */
internal fun boxCaseFit(aspect: Float): Float = (1f - aspect.coerceIn(0.3f, 2f) / 6.8f).coerceIn(0.6f, 1f)

/**
 * Update 424 (owner screenshots: cases overlapping, floating spines with stepped shadows, hollow-looking sides): a resting case in
 * the Cardboard box drawn through the box's own camera — a solid block standing on the floor. Its top is the spine picture, exactly
 * its slot; the side that faces the camera (the front cover on the cases right of the middle, the back's plain shell on the left)
 * and the end facing the camera go down to the floor in the box's perspective, lit from the upper left, darker toward the floor;
 * a soft contact shadow where it meets the floor. Nothing over the artwork's own colours.
 */
internal object BoxCaseDraw {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
    private val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val path = android.graphics.Path()
    private val matrix = android.graphics.Matrix()
    private val src = FloatArray(8)
    private val dst = FloatArray(8)
    private val rect = android.graphics.RectF()

    /** The spine picture's average colour (a coarse grid of samples), for the case's own body colour on its sides and ends. */
    fun average(b: android.graphics.Bitmap): Int {
        if (b.width < 1 || b.height < 1) return 0xFF808080.toInt()
        var r = 0L; var g = 0L; var bl = 0L; var n = 0
        for (i in 0 until 6) for (j in 0 until 12) {
            val p = runCatching { b.getPixel(((i + 0.5f) * b.width / 6f).toInt().coerceIn(0, b.width - 1), ((j + 0.5f) * b.height / 12f).toInt().coerceIn(0, b.height - 1)) }.getOrNull() ?: continue
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; bl += p and 0xFF; n++
        }
        if (n == 0) return 0xFF808080.toInt()
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt()
    }

    private fun scale(argb: Int, k: Float): Int {
        val r = (((argb shr 16) and 0xFF) * k).toInt().coerceIn(0, 255)
        val g = (((argb shr 8) and 0xFF) * k).toInt().coerceIn(0, 255)
        val b = ((argb and 0xFF) * k).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun quad(c: android.graphics.Canvas, p: FloatArray, color: Int, shadeTop: Float, shadeBottom: Float) {
        path.reset(); path.moveTo(p[0], p[1]); path.lineTo(p[2], p[3]); path.lineTo(p[4], p[5]); path.lineTo(p[6], p[7]); path.close()
        fill.shader = null; fill.color = color; c.drawPath(path, fill)
        // Less light down toward the floor (between the cases): top edge = p[0..3], floor edge = p[4..7].
        val ty = (p[1] + p[3]) / 2f; val tx = (p[0] + p[2]) / 2f; val by = (p[5] + p[7]) / 2f; val bx = (p[4] + p[6]) / 2f
        fill.shader = android.graphics.LinearGradient(tx, ty, bx, by, android.graphics.Color.argb((shadeTop * 255).toInt(), 0, 0, 0),
            android.graphics.Color.argb((shadeBottom * 255).toInt(), 0, 0, 0), android.graphics.Shader.TileMode.CLAMP)
        c.drawPath(path, fill); fill.shader = null
    }

    /** Draws the case of slot [s] (screen px relative to ([ox], [oy]), the top-left of its top). */
    fun draw(
        c: android.graphics.Canvas, cam: BoxCam, s: BoxSlot, ox: Float, oy: Float, spine: android.graphics.Bitmap,
        cover: android.graphics.Bitmap?, tone: Int?, platform: String?, towardRight: Boolean, sideX: Float
    ) {
        val z = s.stand
        fun px(x: Float, zz: Float) = cam.sx(x, zz) - ox
        fun py(y: Float, zz: Float) = cam.sy(y, zz) - oy
        // The case's own colour: its spine's, toward the console's material (plastic grey / cardboard) a little.
        val (lit, dark) = boxSideTone(platform, tone)
        val body = android.graphics.Color.argb(255, (lit.red * 255).toInt(), (lit.green * 255).toInt(), (lit.blue * 255).toInt())
        val bodyDark = android.graphics.Color.argb(255, (dark.red * 255).toInt(), (dark.green * 255).toInt(), (dark.blue * 255).toInt())
        // The end facing the camera (the back row's front end, the front row's back end; none for a case under the camera).
        val endY = if (s.y1 <= cam.yc) s.y1 else if (s.y0 >= cam.yc) s.y0 else Float.NaN
        if (!endY.isNaN()) {
            dst[0] = px(s.x0, z); dst[1] = py(endY, z); dst[2] = px(s.x1, z); dst[3] = py(endY, z)
            dst[4] = px(s.x1, 0f); dst[5] = py(endY, 0f); dst[6] = px(s.x0, 0f); dst[7] = py(endY, 0f)
            // (Light from the upper left: an end facing the back of the box is lit, one facing the front is in shade.)
            val facesFront = endY == s.y1
            quad(c, dst, if (facesFront) scale(body, 0.78f) else scale(body, 0.98f), 0.02f, 0.42f)
            // Its top edge catching the light.
            fill.color = android.graphics.Color.argb(46, 255, 255, 255); fill.strokeWidth = 1f
            c.drawLine(dst[0], dst[1], dst[2], dst[3], fill)
        }
        // The side facing the camera: down to the floor along the case's whole length.
        dst[0] = px(sideX, z); dst[1] = py(s.y0, z); dst[2] = px(sideX, z); dst[3] = py(s.y1, z)
        dst[4] = px(sideX, 0f); dst[5] = py(s.y1, 0f); dst[6] = px(sideX, 0f); dst[7] = py(s.y0, 0f)
        if (kotlin.math.abs(dst[0] - dst[6]) > 0.6f) {
            if (towardRight && cover != null) {
                // Its front cover, standing on its long edge: the spine edge up, the picture's top toward the back wall.
                src[0] = 0f; src[1] = 0f; src[2] = 0f; src[3] = cover.height.toFloat()
                src[4] = cover.width.toFloat(); src[5] = cover.height.toFloat(); src[6] = cover.width.toFloat(); src[7] = 0f
                matrix.reset()
                if (matrix.setPolyToPoly(src, 0, dst, 0, 4)) {
                    paint.colorFilter = android.graphics.LightingColorFilter(android.graphics.Color.rgb(222, 222, 222), 0)
                    c.save(); c.concat(matrix); c.drawBitmap(cover, 0f, 0f, paint); c.restore()
                    paint.colorFilter = null
                }
                // (Shade toward the floor over the picture.)
                val tmp = floatArrayOf(dst[0], dst[1], dst[2], dst[3], dst[4], dst[5], dst[6], dst[7])
                path.reset(); path.moveTo(tmp[0], tmp[1]); path.lineTo(tmp[2], tmp[3]); path.lineTo(tmp[4], tmp[5]); path.lineTo(tmp[6], tmp[7]); path.close()
                fill.shader = android.graphics.LinearGradient(tmp[0], tmp[1], tmp[6], tmp[7], android.graphics.Color.argb(0, 0, 0, 0),
                    android.graphics.Color.argb(110, 0, 0, 0), android.graphics.Shader.TileMode.CLAMP)
                c.drawPath(path, fill); fill.shader = null
            } else {
                // The other side (the back) in the shade, the shell's own colour.
                quad(c, dst, if (towardRight) body else scale(bodyDark, 1.25f), 0.04f, 0.45f)
            }
        }
        // The top: the spine picture, exactly the slot.
        rect.set(px(s.x0, z), py(s.y0, z), px(s.x1, z), py(s.y1, z))
        c.drawBitmap(spine, null, rect, paint)
        // A fine bevel: the lit long edge toward the light, a darker one away from it, the end toward the camera a touch darker.
        fill.strokeWidth = 1f
        fill.color = android.graphics.Color.argb(40, 255, 255, 255); c.drawLine(rect.left + 0.5f, rect.top, rect.left + 0.5f, rect.bottom, fill)
        fill.color = android.graphics.Color.argb(60, 0, 0, 0); c.drawLine(rect.right - 0.5f, rect.top, rect.right - 0.5f, rect.bottom, fill)
    }
}
