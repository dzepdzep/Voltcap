package com.shelf.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/*
 * 676a (owner: "console / system badges and achievement badges can NEVER overlap anywhere in VoltCap"): one badge lane per
 * tile. Every corner badge on a game's picture (the console badge, the achievement count / progress chip, the NEW unlock pill,
 * the Metacritic score) reports its ACTUAL measured pill to the tile's lane while the tile is measured; when the tile places
 * them, the lane resolves the whole set at once, in the same frame - no badge is drawn first and moved later, so nothing
 * jumps or flickers when a count changes, a logo loads, the text grows (x1.3) or the tile resizes.
 *
 * The rule ([planBadges]):
 *  - a badge keeps the corner its look / setting gives it (console top-left, achievements top-right by default) whenever its
 *    pill stays at least [BADGE_GAP] clear of every badge already placed (borders / glow included: [BadgeBox.reach]);
 *  - badges are placed in [BadgeKind] order: the console badge first (it carries the console's branding), then the
 *    achievement count, the NEW pill, the score - a later badge never moves an earlier one;
 *  - a badge that would touch one before it STACKS: it moves along its own side, away from its edge, to [BADGE_GAP] below
 *    (or above) the badge in the way - the console top-left, the count right under it on the right;
 *  - only when that runs off the tile does it try the other corners (the same side's other edge first);
 *  - a tile too small for both (none of that fits) keeps the earlier badge and leaves the later one out - never one over
 *    the other, never cut, never pushed off the picture. The QA dump reports it (badgecheck.py fails a hidden badge's
 *    overlap), so it can't happen silently.
 * Styles, colours and sizes are the badges' own: the lane only moves pills, it never redraws them.
 */

/** The minimum clear space between two badges' drawn edges. */
internal val BADGE_GAP: Dp = 6.dp

/** The badges a tile can carry, in the order they are placed (the earlier keeps its corner). */
internal enum class BadgeKind { CONSOLE, PROGRESS, NEW_UNLOCK, SCORE,
    /** 676e: a coverless card's name plate - never moved; the badges after the console keep clear of it. */
    CONTENT,
    /** 680a (D356): a band at the tile's bottom that something drawn OVER the tile from outside covers (Channel's name plate) -
     *  never moved; every badge, the console included, keeps clear of it. */
    OVERLAY }

/** One badge as measured: its corner, the margin round it inside the corner, its pill and how far its glow / border reaches. */
internal data class BadgeBox(
    val kind: BadgeKind, val align: Alignment, val mx: Int, val my: Int, val w: Int, val h: Int, val reach: Int = 0
)

/** Where a badge goes: moved by [dx] / [dy] from its own corner, or left out ([shown] false) on a tile with no room for it. */
internal data class BadgeSpot(val dx: Int, val dy: Int, val shown: Boolean = true)

/** The pill of [b] in its own corner of a [tile] tile. */
internal fun badgeRect(b: BadgeBox, tile: IntSize, dir: LayoutDirection, align: Alignment = b.align): IntRect {
    val at = align.align(IntSize(b.w + 2 * b.mx, b.h + 2 * b.my), tile, dir)
    return IntRect(IntOffset(at.x + b.mx, at.y + b.my), IntSize(b.w, b.h))
}

private fun IntRect.grow(by: Int) = if (by == 0) this else IntRect(left - by, top - by, right + by, bottom + by)

/** True when [a] and [b] (drawn edges, glow included) are closer than [gap]. */
internal fun badgesTouch(a: IntRect, b: IntRect, gap: Int): Boolean =
    a.left < b.right + gap && b.left < a.right + gap && a.top < b.bottom + gap && b.top < a.bottom + gap

/**
 * The lane's rule (see the file's head): where each of [boxes] goes on a [tile] tile so no two come within [gap] of each
 * other. Pure, so the unit tests check it with the real devices' numbers.
 */
internal fun planBadges(tile: IntSize, boxes: List<BadgeBox>, gap: Int, dir: LayoutDirection = LayoutDirection.Ltr): Map<BadgeKind, BadgeSpot> {
    val out = HashMap<BadgeKind, BadgeSpot>()
    val placed = ArrayList<IntRect>() // drawn edges, glow included
    // (680a: an overlay band is there before anything else - the console stacks away from it too.)
    for (ob in boxes.filter { it.kind == BadgeKind.OVERLAY }) { out[ob.kind] = BadgeSpot(0, 0); placed += badgeRect(ob, tile, dir) }
    // (676e: the console badge first, then the card's own content (its name plate, fixed where it is), then the rest.)
    val content = boxes.filter { it.kind == BadgeKind.CONTENT }
    for (cb in content) out[cb.kind] = BadgeSpot(0, 0)
    var contentAdded = content.isEmpty()
    for (b in boxes.filter { it.kind != BadgeKind.CONTENT && it.kind != BadgeKind.OVERLAY }.sortedBy { it.kind.ordinal }) {
        if (!contentAdded && b.kind != BadgeKind.CONSOLE) { content.forEach { placed += badgeRect(it, tile, dir) }; contentAdded = true }
        val own = badgeRect(b, tile, dir)
        fun clear(r: IntRect) = placed.none { badgesTouch(r.grow(b.reach), it, gap) }
        fun inside(r: IntRect) = r.grow(b.reach).let { it.left >= 0 && it.top >= 0 && it.right <= tile.width && it.bottom <= tile.height }
        // Its own corner as it is (a badge alone keeps exactly the place it always had, even on a tile it overhangs).
        if (clear(own)) { out[b.kind] = BadgeSpot(0, 0); placed += own.grow(b.reach); continue }
        val v = b.align.align(IntSize(0, 0), IntSize(0, 2), dir).y // 0 top, 1 centre, 2 bottom
        val h = b.align.align(IntSize(0, 0), IntSize(2, 0), dir).x // 0 start, 1 centre, 2 end (absolute)
        fun corner(hx: Int, vy: Int): Alignment = when {
            vy == 2 && hx == 0 -> Alignment.BottomStart; vy == 2 && hx == 2 -> Alignment.BottomEnd; vy == 2 -> Alignment.BottomCenter
            vy == 1 && hx == 0 -> Alignment.CenterStart; vy == 1 && hx == 2 -> Alignment.CenterEnd; vy == 1 -> Alignment.Center
            hx == 0 -> Alignment.TopStart; hx == 2 -> Alignment.TopEnd; else -> Alignment.TopCenter
        }.let { if (dir == LayoutDirection.Rtl && it != Alignment.TopCenter && it != Alignment.BottomCenter && it != Alignment.Center) mirror(it) else it }
        val flipV = if (v == 2) 0 else 2
        val flipH = if (h == 2) 0 else if (h == 0) 2 else 1
        // Its own corner first, then the same side's other edge, then the other side (its edge, then the other).
        val anchors = listOf(b.align, corner(h, flipV), corner(flipH, v), corner(flipH, flipV)).distinct()
        var chosen: IntRect? = null
        for (a in anchors) {
            var r = badgeRect(b, tile, dir, a)
            val down = a.align(IntSize(0, 0), IntSize(0, 2), dir).y != 2
            // Stack away from the edge past whatever is in the way (at most one step per badge already placed).
            for (step in 0..placed.size) {
                if (!inside(r) && !(a == b.align && step == 0)) break
                val hit = placed.filter { badgesTouch(r.grow(b.reach), it, gap) }
                if (hit.isEmpty()) { if (inside(r) || a == b.align) chosen = r; break }
                r = if (down) r.translate(0, hit.maxOf { it.bottom } + gap + b.reach - r.top)
                else r.translate(0, hit.minOf { it.top } - gap - b.reach - r.bottom)
            }
            if (chosen != null) break
        }
        val c = chosen
        if (c == null) out[b.kind] = BadgeSpot(0, 0, shown = false)
        else { out[b.kind] = BadgeSpot(c.left - own.left, c.top - own.top); placed += c.grow(b.reach) }
    }
    return out
}

private fun mirror(a: Alignment): Alignment = when (a) {
    Alignment.TopStart -> Alignment.TopEnd; Alignment.TopEnd -> Alignment.TopStart
    Alignment.BottomStart -> Alignment.BottomEnd; Alignment.BottomEnd -> Alignment.BottomStart
    Alignment.CenterStart -> Alignment.CenterEnd; Alignment.CenterEnd -> Alignment.CenterStart
    else -> a
}

/** A tile's lane: what its badges measured, read back when they are placed (snapshot state, so a change re-places them). */
internal class BadgeLane {
    internal var tile by mutableStateOf(IntSize.Zero)
    internal var gap by mutableStateOf(0)
    internal var dir by mutableStateOf(LayoutDirection.Ltr)
    internal val boxes = mutableStateMapOf<BadgeKind, BadgeBox>()
    /** 680a (D356): px at the tile's bottom covered by an overlay drawn over the tile from outside (0: none) - set by [badgeTile]. */
    internal var overlayBottom by mutableStateOf(0)
    internal val id: String get() = System.identityHashCode(this).toString(16)

    internal fun report(b: BadgeBox) { if (boxes[b.kind] != b) boxes[b.kind] = b }
    internal fun remove(k: BadgeKind) { boxes.remove(k) }
    internal fun spot(k: BadgeKind): BadgeSpot {
        val t = tile
        if (t.width <= 0 || t.height <= 0) return BadgeSpot(0, 0)
        val all = boxes.values.toList().let { if (overlayBottom > 0) it + BadgeBox(BadgeKind.OVERLAY, Alignment.BottomStart, 0, 0, t.width, overlayBottom) else it }
        return planBadges(t, all, gap, dir)[k] ?: BadgeSpot(0, 0)
    }
}

/** 680a (D356): the height of an overlay a caller draws over the tile's bottom (Channel's name plate); the badges keep clear. */
internal val LocalBadgeOverlayBottom = staticCompositionLocalOf { 0.dp }

/** The lane of the tile being drawn (null: no lane - the badge sits in its corner as before). */
internal val LocalBadgeLane = staticCompositionLocalOf<BadgeLane?> { null }

@Composable
internal fun rememberBadgeLane(): BadgeLane = remember { BadgeLane() }

/**
 * 676e (RP Nova x1.3 n676d13 / n676e13, Cube: "'Shadow Of The Colossus' and '6/10' drawn over each other"): on the text of a
 * coverless card's name plate - it joins the tile's lane as fixed CONTENT where it is drawn, so the badges after the console keep
 * clear of it (beside, stacked, another corner) or, on a tile with no room for both, leave it out - never over the name. No lane:
 * nothing changes.
 */
internal fun Modifier.laneContent(lane: BadgeLane?, inset: Dp, shiftY: Dp = 0.dp): Modifier = if (lane == null) this else this.then(LaneContentElement(lane, inset, shiftY))

private data class LaneContentElement(val lane: BadgeLane, val inset: Dp, val shiftY: Dp) : ModifierNodeElement<LaneContentNode>() {
    override fun create() = LaneContentNode(lane, inset, shiftY)
    override fun update(node: LaneContentNode) { if (node.lane !== lane) node.lane.remove(BadgeKind.CONTENT); node.lane = lane; node.inset = inset; node.shiftY = shiftY }
}

/** [shiftY] (676z): room a padding before this node keeps above the content (the console's band) - added to its reported top. */
private class LaneContentNode(var lane: BadgeLane, var inset: Dp, var shiftY: Dp = 0.dp) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val p = measurable.measure(constraints)
        // The plate's box fills the tile less [inset] and centres the text: its margin from the tile's edges is the slack round it.
        val insetPx = inset.roundToPx()
        val mx = ((constraints.maxWidth - p.width) / 2).coerceAtLeast(0) + insetPx
        val my = ((constraints.maxHeight - p.height) / 2).coerceAtLeast(0) + insetPx + shiftY.roundToPx()
        lane.report(BadgeBox(BadgeKind.CONTENT, Alignment.TopStart, mx, my, p.width, p.height))
        return layout(p.width, p.height) { p.place(0, 0) }
    }
    override fun onDetach() { lane.remove(BadgeKind.CONTENT) }
}

/**
 * 676z2 (Nova x1.3 Tiles: the count over a coverless tile's name): content in a CORNER of the tile ([align], held [mx] / [my] off
 * its edges by the paddings before this) - fixed CONTENT in the lane, as [laneContent] for a centred plate.
 */
internal fun Modifier.laneContentAt(lane: BadgeLane?, align: Alignment, mx: Dp, my: Dp): Modifier =
    if (lane == null) this else this.then(LaneCornerContentElement(lane, align, mx, my))

private data class LaneCornerContentElement(val lane: BadgeLane, val align: Alignment, val mx: Dp, val my: Dp) : ModifierNodeElement<LaneCornerContentNode>() {
    override fun create() = LaneCornerContentNode(lane, align, mx, my)
    override fun update(node: LaneCornerContentNode) {
        if (node.lane !== lane) node.lane.remove(BadgeKind.CONTENT)
        node.lane = lane; node.align = align; node.mx = mx; node.my = my
    }
}

private class LaneCornerContentNode(var lane: BadgeLane, var align: Alignment, var mx: Dp, var my: Dp) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val p = measurable.measure(constraints)
        lane.report(BadgeBox(BadgeKind.CONTENT, align, mx.roundToPx(), my.roundToPx(), p.width, p.height))
        return layout(p.width, p.height) { p.place(0, 0) }
    }
    override fun onDetach() { lane.remove(BadgeKind.CONTENT) }
}

/** QA (QaClip "badges"): a badge's kind and its tile's lane, "PROGRESS#1a2b". */
internal val BadgeTag = SemanticsPropertyKey<String>("VoltcapBadge")
/** QA: the tile a lane belongs to. */
internal val BadgeTileTag = SemanticsPropertyKey<String>("VoltcapBadgeTile")
private var SemanticsPropertyReceiver.badgeTag by BadgeTag
private var SemanticsPropertyReceiver.badgeTileTag by BadgeTileTag

/**
 * On the tile's Box, last in its chain (so its size is the box the badges align in): the tile's size for [lane]. The badges'
 * own sizes are measured inside it, so all of them are known before any is placed.
 */
internal fun Modifier.badgeTile(lane: BadgeLane?, overlayBottom: Dp = 0.dp): Modifier = if (lane == null) this else this
    .then(BadgeTileElement(lane, overlayBottom))
    .semantics { badgeTileTag = lane.id }

private data class BadgeTileElement(val lane: BadgeLane, val overlayBottom: Dp = 0.dp) : ModifierNodeElement<BadgeTileNode>() {
    override fun create() = BadgeTileNode(lane, overlayBottom)
    override fun update(node: BadgeTileNode) { node.lane = lane; node.overlayBottom = overlayBottom }
}

private class BadgeTileNode(var lane: BadgeLane, var overlayBottom: Dp = 0.dp) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val p = measurable.measure(constraints)
        val size = IntSize(p.width, p.height)
        if (lane.tile != size) lane.tile = size
        val g = BADGE_GAP.roundToPx()
        if (lane.gap != g) lane.gap = g
        if (lane.dir != layoutDirection) lane.dir = layoutDirection
        val ob = overlayBottom.roundToPx()
        if (lane.overlayBottom != ob) lane.overlayBottom = ob
        return layout(p.width, p.height) { p.place(0, 0) }
    }
}

/**
 * On a badge's pill, right after the margin that holds it off its corner ([mx] / [my] - the padding before this in the
 * chain) and before its size / shape: the pill joins [lane] as [kind] in the [align] corner. [reach]: how far a glow or an
 * outer border reaches past the measured pill. No lane: nothing changes.
 */
internal fun Modifier.laneBadge(lane: BadgeLane?, kind: BadgeKind, align: Alignment, mx: Dp, my: Dp = mx, reach: Dp = 0.dp): Modifier =
    if (lane == null) this else this
        .then(LaneBadgeElement(lane, kind, align, mx, my, reach))
        .semantics { badgeTag = "${kind.name}#${lane.id}" }

private data class LaneBadgeElement(val lane: BadgeLane, val kind: BadgeKind, val align: Alignment, val mx: Dp, val my: Dp, val reach: Dp) :
    ModifierNodeElement<LaneBadgeNode>() {
    override fun create() = LaneBadgeNode(lane, kind, align, mx, my, reach)
    override fun update(node: LaneBadgeNode) {
        if (node.lane !== lane || node.kind != kind) node.lane.remove(node.kind)
        node.lane = lane; node.kind = kind; node.align = align; node.mx = mx; node.my = my; node.reach = reach
    }
}

private class LaneBadgeNode(var lane: BadgeLane, var kind: BadgeKind, var align: Alignment, var mx: Dp, var my: Dp, var reach: Dp) :
    Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val p = measurable.measure(constraints)
        lane.report(BadgeBox(kind, align, mx.roundToPx(), my.roundToPx(), p.width, p.height, reach.roundToPx()))
        return layout(p.width, p.height) {
            val s = lane.spot(kind)
            // (676e: a badge with no room is not placed at all - nothing drawn, no text left under another one.)
            if (s.shown) p.place(s.dx, s.dy)
        }
    }
    override fun onDetach() { lane.remove(kind) }
}
