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
 * The rule ([planBadges]) - 676w, owner 16 Nov ("REVISE GLOBAL BADGE COLLISION RULE": no second row under the console):
 *  - every badge keeps the corner its look / setting gives it (console top-left, achievements top-right by default);
 *  - when they don't all fit there at least [BADGE_GAP] apart (borders / glow included: [BadgeBox.reach]) and clear of the
 *    card's own name plate ([BadgeKind.CONTENT]), ALL of them shrink by the same factor - the whole badge (icon, text, bar,
 *    padding, border) drawn smaller, each anchored in its own corner - to the largest size where they fit side by side with
 *    the same [BADGE_GAP] between them. Nothing moves to another row or corner;
 *  - the size never goes under [BADGE_MIN_SCALE] (they stay readable). A tile with no fit even there leaves the latest badge
 *    out (score, then NEW, then the count - [BadgeKind] order, the console last and never) and tries the rest again at full
 *    size: never one over the other, never cut, never pushed off the picture. The QA dump reports it (badgecheck.py).
 * Styles, colours and branding are the badges' own: the lane only scales and places pills, it never redraws them.
 */

/** The minimum clear space between two badges' drawn edges. */
internal val BADGE_GAP: Dp = 6.dp

/** 676w: the smallest a badge is drawn to make room beside another (below this a badge is left out instead). */
internal const val BADGE_MIN_SCALE = 0.72f

/** The badges a tile can carry, in the order they are placed (the earlier keeps its corner). */
internal enum class BadgeKind { CONSOLE, PROGRESS, NEW_UNLOCK, SCORE,
    /** 676e: a coverless card's name plate - never moved; the badges after the console keep clear of it. */
    CONTENT }

/** One badge as measured: its corner, the margin round it inside the corner, its pill and how far its glow / border reaches. */
internal data class BadgeBox(
    val kind: BadgeKind, val align: Alignment, val mx: Int, val my: Int, val w: Int, val h: Int, val reach: Int = 0
)

/**
 * Where a badge goes: moved by [dx] / [dy] from its own corner (676w: always 0 - badges keep their corner), drawn at [scale]
 * about the point ([ox], [oy] as fractions of the pill) that sits in its corner, or left out ([shown] false).
 */
internal data class BadgeSpot(val dx: Int, val dy: Int, val shown: Boolean = true, val scale: Float = 1f, val ox: Float = 0f, val oy: Float = 0f)

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
 * The lane's rule (see the file's head): which of [boxes] are shown on a [tile] tile and at what common scale, so no two come
 * within [gap] of each other. Pure, so the unit tests check it with the real devices' numbers.
 */
internal fun planBadges(tile: IntSize, boxes: List<BadgeBox>, gap: Int, dir: LayoutDirection = LayoutDirection.Ltr): Map<BadgeKind, BadgeSpot> {
    val out = HashMap<BadgeKind, BadgeSpot>()
    val content = boxes.filter { it.kind == BadgeKind.CONTENT }
    for (cb in content) out[cb.kind] = BadgeSpot(0, 0)
    val plate = content.map { badgeRect(it, tile, dir) }
    val set = boxes.filter { it.kind != BadgeKind.CONTENT }.sortedBy { it.kind.ordinal }.toMutableList()
    // The point of the pill that stays in its corner while it shrinks: 0 start / top, 0.5 centre, 1 end / bottom (absolute).
    fun pivot(b: BadgeBox): Pair<Float, Float> =
        b.align.align(IntSize(0, 0), IntSize(2, 0), dir).x / 2f to b.align.align(IntSize(0, 0), IntSize(0, 2), dir).y / 2f
    fun scaled(b: BadgeBox, k: Float): IntRect {
        val r = badgeRect(b, tile, dir)
        if (k == 1f) return r.grow(b.reach)
        val (fx, fy) = pivot(b)
        val ax = r.left + fx * r.width; val ay = r.top + fy * r.height
        val w = r.width * k; val h = r.height * k; val reach = (b.reach * k).toInt()
        val left = kotlin.math.round(ax - fx * w).toInt(); val top = kotlin.math.round(ay - fy * h).toInt()
        return IntRect(left, top, left + kotlin.math.round(w).toInt(), top + kotlin.math.round(h).toInt()).grow(reach)
    }
    fun fits(k: Float): Boolean {
        val rs = set.map { it to scaled(it, k) }
        for (x in rs.indices) for (y in x + 1 until rs.size) if (badgesTouch(rs[x].second, rs[y].second, gap)) return false
        // (676e: the name plate is fixed; the console was placed before it and keeps its place - the others keep clear of it.)
        return rs.none { (b, r) -> b.kind != BadgeKind.CONSOLE && plate.any { badgesTouch(r, it, gap) } }
    }
    while (set.isNotEmpty()) {
        var k = 1f
        if (!fits(1f)) {
            if (set.size == 1 || !fits(BADGE_MIN_SCALE)) {
                // No common size fits: the latest badge is left out, the rest tried again from full size.
                val drop = if (set.size == 1) set.last() else set.last { it.kind != BadgeKind.CONSOLE }
                if (set.size == 1 && drop.kind == BadgeKind.CONSOLE) { k = 1f } // the console alone always keeps its place
                else { out[drop.kind] = BadgeSpot(0, 0, shown = false); set.remove(drop); continue }
            } else {
                // The largest common size that fits (a size that fits stays fitting when smaller: corners only pull apart).
                var lo = BADGE_MIN_SCALE; var hi = 1f
                repeat(10) { val mid = (lo + hi) / 2f; if (fits(mid)) lo = mid else hi = mid }
                k = kotlin.math.floor(lo * 100f) / 100f
            }
        }
        for (b in set) { val (fx, fy) = pivot(b); out[b.kind] = BadgeSpot(0, 0, true, k, fx, fy) }
        break
    }
    return out
}

/** 676w: where [b]'s pill is drawn on a [tile] tile with its [spot] (scaled about its corner point) - for the tests and QA. */
internal fun badgeShownRect(b: BadgeBox, tile: IntSize, dir: LayoutDirection, spot: BadgeSpot): IntRect {
    val r = badgeRect(b, tile, dir).translate(spot.dx, spot.dy)
    if (spot.scale == 1f) return r
    val ax = r.left + spot.ox * r.width; val ay = r.top + spot.oy * r.height
    val w = r.width * spot.scale; val h = r.height * spot.scale
    val left = kotlin.math.round(ax - spot.ox * w).toInt(); val top = kotlin.math.round(ay - spot.oy * h).toInt()
    return IntRect(left, top, left + kotlin.math.round(w).toInt(), top + kotlin.math.round(h).toInt())
}

/** A tile's lane: what its badges measured, read back when they are placed (snapshot state, so a change re-places them). */
internal class BadgeLane {
    internal var tile by mutableStateOf(IntSize.Zero)
    internal var gap by mutableStateOf(0)
    internal var dir by mutableStateOf(LayoutDirection.Ltr)
    internal val boxes = mutableStateMapOf<BadgeKind, BadgeBox>()
    internal val id: String get() = System.identityHashCode(this).toString(16)

    internal fun report(b: BadgeBox) { if (boxes[b.kind] != b) boxes[b.kind] = b }
    internal fun remove(k: BadgeKind) { boxes.remove(k) }
    internal fun spot(k: BadgeKind): BadgeSpot {
        val t = tile
        if (t.width <= 0 || t.height <= 0) return BadgeSpot(0, 0)
        return planBadges(t, boxes.values.toList(), gap, dir)[k] ?: BadgeSpot(0, 0)
    }
}

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
internal fun Modifier.badgeTile(lane: BadgeLane?): Modifier = if (lane == null) this else this
    .then(BadgeTileElement(lane))
    .semantics { badgeTileTag = lane.id }

private data class BadgeTileElement(val lane: BadgeLane) : ModifierNodeElement<BadgeTileNode>() {
    override fun create() = BadgeTileNode(lane)
    override fun update(node: BadgeTileNode) { node.lane = lane }
}

private class BadgeTileNode(var lane: BadgeLane) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val p = measurable.measure(constraints)
        val size = IntSize(p.width, p.height)
        if (lane.tile != size) lane.tile = size
        val g = BADGE_GAP.roundToPx()
        if (lane.gap != g) lane.gap = g
        if (lane.dir != layoutDirection) lane.dir = layoutDirection
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
            // (676w: shrunk as a whole about the point in its corner - the same layer scale for every part of it.)
            if (s.shown) {
                if (s.scale == 1f) p.place(s.dx, s.dy)
                else p.placeWithLayer(s.dx, s.dy) {
                    scaleX = s.scale; scaleY = s.scale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(s.ox, s.oy)
                }
            }
        }
    }
    override fun onDetach() { lane.remove(kind) }
}
