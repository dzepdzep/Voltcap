package com.shelf.launcher.ui

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
    val tick = remember { mutableIntStateOf(0) }
    val focusIdx = remember { mutableIntStateOf(-1) }
    val stepPx = with(LocalDensity.current) { 40.dp.toPx() }
    val drag = remember { floatArrayOf(0f) }
    SubcomposeLayout(
        modifier
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
        val childCs = Constraints(0, (avail - padS - padE).coerceAtLeast(0), 0, cs.maxHeight)
        val ps: List<Placeable> = (0 until count).map { i ->
            subcompose("c$i") {
                val bring = remember(i) { { win.keep = i; win.manual = false; tick.intValue++; Unit } }
                Box(Modifier.then(ChipBringElement(bring)).onFocusChanged { if (it.hasFocus) focusIdx.intValue = i }) { chip(i) }
            }.first().measure(childCs)
        }
        val hw = subcompose("probe") { ChipHint("+99 ›", hintColor, hintSize) {} }.first().measure(Constraints()).width

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
        val hl = if (hiddenL > 0) subcompose("hl") {
            ChipHint("‹ +$hiddenL", hintColor, hintSize) {
                // A page back: as many chips as the window shows.
                val shown = (endFor(win.start) - win.start + 1).coerceAtLeast(1)
                win.start = (win.start - shown).coerceAtLeast(0); win.manual = true; tick.intValue++
            }
        }.first().measure(Constraints()) else null
        val hr = if (hiddenR > 0) subcompose("hr") {
            ChipHint("+$hiddenR ›", hintColor, hintSize) { win.start = (endFor(win.start) + 1).coerceAtMost(count - 1); win.manual = true; tick.intValue++ }
        }.first().measure(Constraints()) else null

        val height = maxOf(ps.maxOfOrNull { it.height } ?: 0, hl?.height ?: 0, hr?.height ?: 0).coerceIn(cs.minHeight, cs.maxHeight)
        var content = 0
        for (i in s..e) if (i in ps.indices) content += ps[i].width + if (i > s) gap else 0
        // As wide as its chips when they all fit (at least what the caller asks for: fillMaxWidth / weight); the whole
        // width when some are hidden.
        val needed = padS + content + padE + (if (hl != null) hw + gap else 0) + (if (hr != null) hw + gap else 0)
        val width = if (hl == null && hr == null || !cs.hasBoundedWidth) needed.coerceIn(cs.minWidth, cs.maxWidth) else cs.maxWidth
        val leftEdge = padS + if (hl != null) hw + gap else 0
        val rightEdge = width - padE - if (hr != null) hw + gap else 0
        val x0 = when (align) {
            Alignment.CenterHorizontally -> leftEdge + ((rightEdge - leftEdge - content) / 2).coerceAtLeast(0)
            Alignment.End -> (rightEdge - content).coerceAtLeast(leftEdge)
            else -> leftEdge
        }
        layout(width, height) {
            var x = x0
            for (i in s..e) {
                if (i !in ps.indices) continue
                val p = ps[i]
                p.place(x, (height - p.height) / 2)
                x += p.width + gap
            }
            // The others just past the edges (clipped), still there for D-pad focus to move onto.
            var lx = -gap
            for (i in s - 1 downTo 0) { val p = ps[i]; lx -= p.width; p.place(lx, (height - p.height) / 2); lx -= gap }
            var rx = width + gap
            for (i in e + 1 until count) { val p = ps[i]; p.place(rx, (height - p.height) / 2); rx += p.width + gap }
            hl?.place(padS, (height - hl.height) / 2)
            hr?.place(width - padE - hr.width, (height - hr.height) / 2)
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
