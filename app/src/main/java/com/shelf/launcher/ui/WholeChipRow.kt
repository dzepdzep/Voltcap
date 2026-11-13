package com.shelf.launcher.ui

import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.launch

/*
 * Update 294 (Toni: "nothing cut off at the screen edge, anywhere"): one rule for every row of chips / tabs that used to
 * scroll sideways (a LazyRow or a scrolling Row) and so showed a chip cut in half at the edge — Hub › Game Library's last
 * category tab ("App") ended at the screen's edge on the Thor.
 *  - Only chips that fit whole are shown; the rest are laid out just outside the row (clipped), never half in it.
 *  - "‹ +N" / "+N ›" at an end says how many more there are that way; a tap on it pages by whole chips.
 *  - It moves by whole chips: a sideways drag steps one chip at a time.
 *  - The chip you're on ([focused], or the one holding real focus) is always one of the whole ones shown.
 * Every chip stays composed and placed, so D-pad focus can still move onto the next one (it's just past the edge): the
 * row then moves so it's whole.
 */
private class ChipWindow {
    var start = 0
    /** The chip kept whole (the last one asked for, by [WholeChipRow]'s focused or by real focus); -1 none. */
    var keep = -1
    var lastFocused = Int.MIN_VALUE
    var lastFocus = -1
    /** Paged by hand (a hint tap / a drag): leave the window where it was put until the chip you're on changes. */
    var manual = false
    // 529j (owner video VID20261113180355, compact screens: "the incoming categories suddenly spawn at the edge"): the
    // window used to be re-laid at once - every chip jumped a chip's width and the next one appeared whole at the edge in
    // one frame. Now the strip glides from the old window to the new one: [fromShift] -> [toShift] is the strip's offset,
    // [fromL..fromR] -> [toL..toR] the span where chips show whole (ChipGlide), the hints fade with it.
    var fromShift = 0f; var toShift = 0f
    var fromL = 0f; var toL = 0f; var fromR = 0f; var toR = 0f
    var fromHl = 0f; var toHl = 0f; var fromHr = 0f; var toHr = 0f
    var hlText = ""; var hrText = ""
    var placed = false; var lastCount = -1; var lastWidth = -1
    /** QA: the row's id in the chipglide log, and whether its last logged frame was at rest. */
    val qaId = Integer.toHexString(System.identityHashCode(this))
    var qaRest = true
    var qaShift = Float.NaN
    var job: kotlinx.coroutines.Job? = null
}

/**
 * 529j: the pure parts of [WholeChipRow]'s glide (ChipGlide529Test). At rest the row is exactly update 294's: only whole
 * chips show. Between two windows the strip moves as one piece and a chip shows as much as it is inside the span of whole
 * chips - an incoming chip slides in from the edge and comes up as it enters, never appears whole at once.
 */
internal object ChipGlide {
    const val MS = 280
    /** QA A/B only (voltcap_test_chipglide legacy): the old instant re-layout, for a same-build before / after recording. */
    @Volatile var legacy = false
    /** Tests only (ChipStrip529RoboTest): each placement's chip x and how much of each shows. Null in the app. */
    @Volatile var probe: ((FloatArray, FloatArray) -> Unit)? = null

    /**
     * QA only (QaTrace on): one `chipglide` line per placed frame of a row that is moving, and its first frame at rest -
     * `row=<id> k=<progress> a=<how much of each chip shows> x=<each chip's x>` - so tools/qa/tools/chipglide.py can check
     * frame by frame on the handheld that nothing came in whole in one frame (a pop-in) and nothing jumped.
     */
    fun qa(row: String, k: Float, xs: FloatArray, al: FloatArray) {
        if (!QaTrace.on) return
        QaTrace.event("chipglide", "row=$row k=${"%.3f".format(k)} a=" + al.joinToString(",") { "%.2f".format(it) } +
            " x=" + xs.joinToString(",") { it.toInt().toString() })
    }
    fun lerp(a: Float, b: Float, k: Float): Float = a + (b - a) * k
    /** A new glide starts where the strip is drawn now ([k] of the way from [from] to [to]) - mid-glide too. Pure. */
    fun restart(from: Float, to: Float, k: Float): Float = lerp(from, to, k)
    /** Where each chip starts on the strip (chip 0 at 0). */
    fun starts(widths: IntArray, gap: Int): IntArray {
        val out = IntArray(widths.size); var x = 0
        for (i in widths.indices) { out[i] = x; x += widths[i] + gap }
        return out
    }
    /** How much of a chip at [x] (width [w]) lies inside [l, r]: 0 (none) .. 1 (whole). */
    fun inside(x: Float, w: Float, l: Float, r: Float): Float {
        if (w <= 0f || r <= l) return 0f
        return ((minOf(x + w, r) - maxOf(x, l)) / w).coerceIn(0f, 1f)
    }
}

@Composable
internal fun WholeChipRow(
    count: Int,
    /** The chip you're on (kept whole); -1 when the row follows real focus only. */
    focused: Int,
    modifier: Modifier = Modifier,
    spacing: Dp = 6.dp,
    /** Where the whole chips sit when they don't fill the row. */
    align: Alignment.Horizontal = Alignment.Start,
    startPadding: Dp = 0.dp,
    endPadding: Dp = 0.dp,
    hintColor: Color = Color.White.copy(alpha = 0.72f),
    hintSize: TextUnit = 12.sp,
    chip: @Composable (Int) -> Unit
) {
    val win = remember { ChipWindow() }
    // Update 460 (KONKR Pocket crash in 0.6.66: IndexOutOfBounds 6 / 6 in the Cube hub's tabs): when the list gets
    // shorter, an old chip's slot can still be recomposed once before it is dropped — it then draws nothing instead of
    // asking its caller for an item that no longer exists.
    val liveCount = androidx.compose.runtime.rememberUpdatedState(count)
    val tick = remember { mutableIntStateOf(0) }
    val focusIdx = remember { mutableIntStateOf(-1) }
    val stepPx = with(LocalDensity.current) { 40.dp.toPx() }
    val drag = remember { floatArrayOf(0f) }
    // 529j: the glide's progress (0 = where the strip was, 1 = the new window); read only when placing, so a glide
    // re-places the chips each frame without measuring them again.
    val glide = remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    SubcomposeLayout(
        modifier
            // (Update 467, QA only: a row that shows whole chips and "+N" for the rest — the gate reads its hidden chips
            // like a scrolling list's, reachable with L1 / R1 and a swipe.)
            .then(if (QaTrace.on) Modifier.semantics { this[QaClip.Paged] = true } else Modifier)
            // Clipped sideways only, so a chip's focus ring / glow above and below still shows.
            .drawWithContent { clipRect(0f, -size.height, size.width, size.height * 2f) { this@drawWithContent.drawContent() } }
            .pointerInput(count) {
                detectHorizontalDragGestures(onDragStart = { drag[0] = 0f }) { ch, dx ->
                    drag[0] += dx
                    if (abs(drag[0]) >= stepPx) {
                        win.start = (win.start + if (drag[0] < 0f) 1 else -1).coerceIn(0, (count - 1).coerceAtLeast(0))
                        win.manual = true
                        drag[0] = 0f
                        tick.intValue++
                    }
                    ch.consume()
                }
            }
    ) { cs ->
        tick.intValue // a page by hand lays the row out again
        val fIdx = focusIdx.intValue
        val avail = if (cs.hasBoundedWidth) cs.maxWidth else Int.MAX_VALUE / 4
        val padS = startPadding.roundToPx()
        val padE = endPadding.roundToPx()
        val gap = spacing.roundToPx()
        // (Update 481, A5: a chip never wider than the room left beside both "‹ +N" / "+N ›" hints — a very long category
        // name kept whole ran past the row's right edge, under the hint (Live tiles' header, the Cube's pills). The chip's
        // own FullText then shrinks it and slides it.)
        val hw = subcompose("probe") { ChipHint("+99 ›", hintColor, hintSize) {} }.first().measure(Constraints()).width
        val roomMax = (avail - padS - padE - (if (count > 1) 2 * (hw + gap) else 0)).coerceAtLeast(0)
        val childCs = Constraints(0, roomMax, 0, cs.maxHeight)
        val ps: List<Placeable> = (0 until count).map { i ->
            subcompose("c$i") {
                val bring = remember(i) { { win.keep = i; win.manual = false; tick.intValue++; Unit } }
                Box(Modifier.then(ChipBringElement(bring)).onFocusChanged { if (it.hasFocus) focusIdx.intValue = i }) { if (i < liveCount.value) chip(i) }
            }.first().measure(childCs)
        }

        // The last chip that fits whole when the window starts at [s] (at least [s] itself).
        fun endFor(s: Int): Int {
            if (count == 0) return -1
            val left = padS + if (s > 0) hw + gap else 0
            fun fit(limit: Int): Int {
                var x = left
                var e = s - 1
                var i = s
                while (i < count) {
                    val w = ps[i].width + if (i > s) gap else 0
                    if (x + w > limit) break
                    x += w; e = i; i++
                }
                return e
            }
            val all = fit(avail - padE)
            if (all == count - 1) return all
            return maxOf(fit(avail - padE - hw - gap), s)
        }

        // Which chip to keep whole: the one that changed last — the row's own (focused) or the one holding focus.
        if (focused != win.lastFocused) { win.lastFocused = focused; if (focused >= 0) { win.keep = focused; win.manual = false } }
        if (fIdx != win.lastFocus) { win.lastFocus = fIdx; if (fIdx >= 0) { win.keep = fIdx; win.manual = false } }
        var s = win.start.coerceIn(0, (count - 1).coerceAtLeast(0))
        val keep = win.keep
        if (!win.manual && keep in 0 until count) {
            if (keep < s) s = keep
            while (s < keep && endFor(s) < keep) s++
        }
        // No empty room at the end while there are chips hidden before.
        if (endFor(s) == count - 1) while (s > 0 && endFor(s - 1) == count - 1) s--
        win.start = s
        val e = endFor(s)

        val hiddenL = s
        val hiddenR = (count - 1 - e).coerceAtLeast(0)
        if (hiddenL > 0) win.hlText = "‹ +$hiddenL"
        if (hiddenR > 0) win.hrText = "+$hiddenR ›"
        // (529j: a hint that is going away stays composed while it fades out, with the last count it showed.)
        val k0 = androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { glide.floatValue }
        val hlShown = hiddenL > 0 || ChipGlide.lerp(win.fromHl, win.toHl, k0) > 0.001f
        val hrShown = hiddenR > 0 || ChipGlide.lerp(win.fromHr, win.toHr, k0) > 0.001f
        val hl = if (hlShown && win.hlText.isNotEmpty()) subcompose("hl") {
            ChipHint(win.hlText, hintColor, hintSize) {
                // A page back: as many chips as the window shows.
                val shown = (endFor(win.start) - win.start + 1).coerceAtLeast(1)
                win.start = (win.start - shown).coerceAtLeast(0); win.manual = true; tick.intValue++
            }
        }.first().measure(Constraints()) else null
        val hr = if (hrShown && win.hrText.isNotEmpty()) subcompose("hr") {
            ChipHint(win.hrText, hintColor, hintSize) { win.start = (endFor(win.start) + 1).coerceAtMost(count - 1); win.manual = true; tick.intValue++ }
        }.first().measure(Constraints()) else null

        val height = maxOf(ps.maxOfOrNull { it.height } ?: 0, hl?.height ?: 0, hr?.height ?: 0).coerceIn(cs.minHeight, cs.maxHeight)
        var content = 0
        for (i in s..e) if (i in ps.indices) content += ps[i].width + if (i > s) gap else 0
        // As wide as its chips when they all fit (at least what the caller asks for: fillMaxWidth / weight); the whole
        // width when some are hidden.
        val hasL = hiddenL > 0; val hasR = hiddenR > 0
        val needed = padS + content + padE + (if (hasL) hw + gap else 0) + (if (hasR) hw + gap else 0)
        val width = if (!hasL && !hasR || !cs.hasBoundedWidth) needed.coerceIn(cs.minWidth, cs.maxWidth) else cs.maxWidth
        val leftEdge = padS + if (hasL) hw + gap else 0
        val rightEdge = width - padE - if (hasR) hw + gap else 0
        val x0 = when (align) {
            Alignment.CenterHorizontally -> leftEdge + ((rightEdge - leftEdge - content) / 2).coerceAtLeast(0)
            Alignment.End -> (rightEdge - content).coerceAtLeast(leftEdge)
            else -> leftEdge
        }
        val starts = ChipGlide.starts(IntArray(ps.size) { ps[it].width }, gap)
        // The new window: the strip's offset (chip [s] at x0), the span of whole chips, which hints show.
        val tShift = (x0 - (starts.getOrNull(s) ?: 0)).toFloat()
        val tL = x0.toFloat(); val tR = (x0 + content).toFloat()
        val tHl = if (hasL) 1f else 0f; val tHr = if (hasR) 1f else 0f
        val fresh = !win.placed || count != win.lastCount || width != win.lastWidth || ChipGlide.legacy
        if (fresh) {
            // First layout, another list or another width: straight to it (nothing to glide from).
            win.job?.cancel()
            win.fromShift = tShift; win.toShift = tShift; win.fromL = tL; win.toL = tL; win.fromR = tR; win.toR = tR
            win.fromHl = tHl; win.toHl = tHl; win.fromHr = tHr; win.toHr = tHr
            glide.floatValue = 1f
        } else if (tShift != win.toShift || tL != win.toL || tR != win.toR || tHl != win.toHl || tHr != win.toHr) {
            // From where it is drawn now (mid-glide too: a fast run of presses never jumps back) to the new window.
            win.fromShift = ChipGlide.restart(win.fromShift, win.toShift, k0); win.toShift = tShift
            win.fromL = ChipGlide.restart(win.fromL, win.toL, k0); win.toL = tL
            win.fromR = ChipGlide.restart(win.fromR, win.toR, k0); win.toR = tR
            win.fromHl = ChipGlide.restart(win.fromHl, win.toHl, k0); win.toHl = tHl
            win.fromHr = ChipGlide.restart(win.fromHr, win.toHr, k0); win.toHr = tHr
            win.job?.cancel()
            glide.floatValue = 0f
            win.job = scope.launch {
                // (On Voltcap's motion clock - MotionLevel: Off / Android's Remove animations finish it at once.)
                androidx.compose.animation.core.Animatable(0f).animateTo(
                    1f, androidx.compose.animation.core.tween(ChipGlide.MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) { glide.floatValue = value }
                glide.floatValue = 1f
            }
        }
        win.placed = true; win.lastCount = count; win.lastWidth = width
        layout(width, height) {
            val k = glide.floatValue
            val shift = ChipGlide.lerp(win.fromShift, win.toShift, k)
            val l = ChipGlide.lerp(win.fromL, win.toL, k)
            val r = ChipGlide.lerp(win.fromR, win.toR, k)
            // Every chip stays placed (D-pad focus can move onto the next one); the ones not in the span sit just past the
            // edges (clipped, not under a finger), the ones in it at their place on the strip, shown as much as they are in.
            // (Off-span chips keep the strip's order past each edge, nearest first, so D-pad left / right from the end chip
            // lands on its neighbour, as before.)
            val xs = FloatArray(ps.size) { starts[it] + shift }
            val al = FloatArray(ps.size) { ChipGlide.inside(xs[it], ps[it].width.toFloat(), l, r) }
            ChipGlide.probe?.invoke(xs, al)
            if (QaTrace.on) {
                // (Every frame of a glide, the first frame at rest after it, and any frame the strip stands somewhere new -
                // the legacy instant path included, so the A/B's "before" is in the log too.)
                val moved = shift != win.qaShift
                if (k < 1f || moved || !win.qaRest) { ChipGlide.qa(win.qaId, k, xs, al); win.qaRest = k >= 1f && !moved; win.qaShift = shift }
            }
            // (Placed in update 294's order - the shown chips left to right, then the hidden ones before them nearest first,
            // then the ones after - so drawing, accessibility and "the last one drawn" stay as they were.)
            for (i in ps.indices) {
                if (al[i] > 0.001f) { val p = ps[i]; p.placeWithLayer(kotlin.math.round(xs[i]).toInt(), (height - p.height) / 2) { alpha = al[i] } }
            }
            var lx = -gap
            for (i in ps.indices.reversed()) {
                val p = ps[i]
                if (al[i] <= 0.001f && xs[i] < l) { lx -= p.width; p.place(lx, (height - p.height) / 2); lx -= gap }
            }
            var rx = width + gap
            for (i in ps.indices) {
                val p = ps[i]
                if (al[i] <= 0.001f && xs[i] >= l) { p.place(rx, (height - p.height) / 2); rx += p.width + gap }
            }
            val aHl = ChipGlide.lerp(win.fromHl, win.toHl, k)
            val aHr = ChipGlide.lerp(win.fromHr, win.toHr, k)
            hl?.let { if (aHl > 0.001f) it.placeWithLayer(padS, (height - it.height) / 2) { alpha = aHl } else it.place(-width, 0) }
            hr?.let { if (aHr > 0.001f) it.placeWithLayer(width - padE - it.width, (height - it.height) / 2) { alpha = aHr } else it.place(width * 2, 0) }
        }
    }
}

/**
 * A chip asked to be brought into view (the second screen's pad lighting it — padTarget — or focus moving onto it):
 * the row moves so it's whole.
 */
private class ChipBringNode(var onBring: () -> Unit) : Modifier.Node(), androidx.compose.ui.relocation.BringIntoViewModifierNode {
    override suspend fun bringIntoView(childCoordinates: androidx.compose.ui.layout.LayoutCoordinates, boundsProvider: () -> androidx.compose.ui.geometry.Rect?) {
        onBring()
    }
}

private class ChipBringElement(val onBring: () -> Unit) : androidx.compose.ui.node.ModifierNodeElement<ChipBringNode>() {
    override fun create() = ChipBringNode(onBring)
    override fun update(node: ChipBringNode) { node.onBring = onBring }
    override fun equals(other: Any?) = other is ChipBringElement && other.onBring === onBring
    override fun hashCode() = onBring.hashCode()
}

@Composable
private fun ChipHint(text: String, color: Color, size: TextUnit, onTap: () -> Unit) {
    val tap = androidx.compose.runtime.rememberUpdatedState(onTap)
    Text(
        text, color = color, fontSize = size, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
        modifier = Modifier.pointerInput(text) { detectTapGestures { tap.value() } }.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}
