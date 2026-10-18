package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game

/*
 * Home layout › Game shelf (update 90): the category as a bookcase. Every game stands on a shelf showing only its spine
 * (the console's real spine from the template pack, the game's name running up it; a plain spine with the console's
 * logo where there's none). How many shelves there are follows from how many games you have: a few games stand tall on
 * one shelf, a big library fills up to three and the bookcase scrolls up and down. Moving pulls the game you're on
 * out of the shelf: it slides up, turns to show its front (or its 3D box from the side) and then bobs and turns
 * gently while you rest on it. ◀ ▶ go along the shelves, ▲ ▼ to the shelf above / below.
 */

/** The current bookcase, for the D-pad (▲ ▼ go to the nearest spine on the shelf above / below). */
internal object ShelfHome {
    /** Shelf of each game and the middle of its spine (dp, along the shelf). */
    @Volatile var rowOf: IntArray = IntArray(0)
    @Volatile var centerOf: FloatArray = FloatArray(0)

    /** The game on the next shelf [dir] (+1 down, −1 up) nearest to the one you're on, or null at the end. */
    fun vertical(focus: Int, dir: Int): Int? {
        val rows = rowOf
        if (focus !in rows.indices) return null
        val target = rows[focus] + dir
        val x = centerOf.getOrNull(focus) ?: return null
        var best: Int? = null
        var bestD = Float.MAX_VALUE
        for (i in rows.indices) if (rows[i] == target) {
            val d = kotlin.math.abs((centerOf.getOrNull(i) ?: continue) - x)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }
}

/** One spine's place on the bookcase. */
private class ShelfSlot(val row: Int, val x: Dp, val w: Dp, val h: Dp)

private class ShelfPlan(val slots: List<ShelfSlot>, val rows: Int, val rowH: Dp, val rowW: List<Dp>)

/**
 * A spine stands this wide for its height (the pack's own spine shape, else a slim case). Update 124: never wider than
 * a tenth of its height (the cartridge consoles' cardboard spines stood out as chunky blocks; update 126: up to
 * SPINE_MAX_ASPECT, your pack's chunkiest).
 */
private fun spineAspect(platform0: String): Float = (LookStyles.spinePlatform(platform0) ?: platform0).let { platform -> // (Update 228, #18: the spine picked for all)
    // (Update 126: your own console spine's shape first, so its picture fills the slot as it is.)
    (if (Features.coverTemplates.state.value || MediaCovers.own(platform) != null) CustomSpines.of(platform)?.aspect ?: MediaCovers.of(platform)?.shelfSpineAspect() else null)?.coerceIn(0.05f, SPINE_MAX_ASPECT) ?: 0.085f }

/** Update 126: the shape of [platform]'s own console spine (null: none), for the same-height shelves too. */
private fun customSpineAspect(platform0: String): Float? = (LookStyles.spinePlatform(platform0) ?: platform0).let { platform ->
    if (Features.coverTemplates.state.value || MediaCovers.own(platform) != null) CustomSpines.of(platform)?.aspect?.coerceIn(0.05f, SPINE_MAX_ASPECT) else null }

/**
 * Lays the games out on shelves [width] wide: tries one shelf, then two, then three (each shorter, so more spines fit),
 * and takes the first that holds them all; past three shelves the bookcase scrolls.
 */
private fun planShelves(games: List<Game>, width: Dp, height: Dp, real: Boolean, stand: Features.ShelfStand): ShelfPlan {
    val gap = when (stand) { Features.ShelfStand.SPINES -> 2.dp; Features.ShelfStand.FRONTS -> 8.dp; Features.ShelfStand.ANGLED -> 4.dp }
    val maxMm = if (real) games.maxOfOrNull { realCaseMm(it.platform)?.second ?: 190f } ?: 190f else 190f
    var plan: ShelfPlan? = null
    // The R1 Size tile (update 104): taller or shorter shelves.
    val sizeK = GameSize.scale(com.shelf.launcher.data.HomeLayout.SHELF)
    for (k in 1..3) {
        val rowH = (height / k).coerceAtMost(230.dp * sizeK).coerceAtMost(height)
        val spineH = rowH * 0.84f
        val slots = ArrayList<ShelfSlot>(games.size)
        val rowW = ArrayList<Dp>()
        var row = 0
        var x = 0.dp
        for (g in games) {
            val h = if (real) spineH * ((realCaseMm(g.platform)?.second ?: 190f) / maxMm).coerceIn(0.45f, 1f) else spineH
            // (Update 126: a console spine of your own gets a slot of its own shape — it was cut at the sides to fit.)
            val spineW = if (real) (h * spineAspect(g.platform)).coerceIn(12.dp, 48.dp)
                else customSpineAspect(g.platform)?.let { (spineH * it).coerceIn(10.dp, 48.dp) } ?: (spineH * 0.09f).coerceIn(14.dp, 26.dp)
            val frontW = h * pulledAspect(g.platform, false)
            val w = when (stand) {
                Features.ShelfStand.SPINES -> spineW
                Features.ShelfStand.FRONTS -> frontW
                // The spine and the front both turned (35° / 55°): about this wide seen from the front.
                Features.ShelfStand.ANGLED -> spineW * ANGLE_SPINE + frontW * ANGLE_FRONT
            }
            if (x > 0.dp && x + w > width) { rowW += x - gap; row++; x = 0.dp }
            slots += ShelfSlot(row, x, w, h)
            x += w + gap
        }
        rowW += (x - gap).coerceAtLeast(0.dp)
        plan = ShelfPlan(slots, row + 1, rowH, rowW)
        if (row + 1 <= k) break
    }
    return plan!!
}

@Composable
internal fun StyleShelf(c: StyleCtx, modifier: Modifier) {
    // Plain if / else (an early return would unbalance Compose's groups).
    if (c.games.isEmpty()) StyleEmpty(modifier) else StyleShelfBody(c, modifier)
}

@Composable
private fun StyleShelfBody(c: StyleCtx, modifier: Modifier) {
    val real = Features.shelfRealSizes.state.value
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val look = Features.shelfLook.state.value
    Box(modifier.fillMaxSize()) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        val fullH = maxHeight
        val headerH = 34.dp
        val footerH = 44.dp
        val areaH = (maxHeight - headerH - footerH).coerceAtLeast(120.dp)
        val areaW = maxWidth
        val games = c.games
        val templates = Features.coverTemplates.state.value // the spine widths follow the cover templates
        val stand = Features.shelfStand.state.value
        // (Update 228, #18: laid out again when one case or spine is picked for every game — their shapes set the slots.)
        val plan = remember(games, real, areaW, areaH, templates, stand, Features.layoutScale.state.value, com.shelf.launcher.data.Packs.version.intValue,
            Features.caseStyle.state.value, Features.spineStyle.state.value) { planShelves(games, areaW, areaH, real, stand) }
        // (Update 130: worked out once per plan, not on every redraw — with hundreds of games that added up.)
        val rowOf = remember(plan) { IntArray(plan.slots.size) { plan.slots[it].row } }
        // Each shelf is centred in the room: the keys ▲ / ▼ look for the neighbour above / below with that offset.
        val centerOf = remember(plan, areaW) {
            FloatArray(plan.slots.size) {
                val sl = plan.slots[it]
                (((areaW - plan.rowW[sl.row]) / 2).coerceAtLeast(0.dp) + sl.x + sl.w / 2).value
            }
        }
        // The games on each shelf (update 130: it used to look through every game for every shelf, on every frame).
        val slotsByRow = remember(plan) {
            val lists = Array(plan.rows) { ArrayList<Int>() }
            plan.slots.forEachIndexed { i, sl -> if (sl.row in lists.indices) lists[sl.row].add(i) }
            lists
        }
        SideEffect {
            ShelfHome.rowOf = rowOf
            ShelfHome.centerOf = centerOf
        }
        val visibleRows = minOf(plan.rows, ((areaH / plan.rowH) + 0.01f).toInt().coerceAtLeast(1))
        val focusRow = plan.slots.getOrNull(c.focus)?.row ?: 0
        // Update 156: with Endless on and more shelves than fit, the bookcase goes on round: after the last shelf the first
        // ones come again, and the shelf you're on is counted on (its "lap" round the bookcase, from EndlessRow.kt's place
        // on the endless row), so going past the last game to the first scrolls on by one shelf like any step instead of
        // racing back to the top. When every shelf fits there's nothing to scroll: going round is just the next pull-out.
        val loopV = rememberLoopFocus(c)
        // (Update 226, #105: with just one shelf more than fit, a part-shown shelf at the top and the bottom could be the
        // same one — two more than fit means every shelf in view is a different one.)
        val scrolls = loopRowOn(c) && plan.rows >= visibleRows + 2
        val lap = if (scrolls) Math.floorDiv(loopV, games.size) else 0
        val focusVRow = focusRow + lap * plan.rows
        // Scrolls so the shelf you're on stays in view (in the middle once there are more shelves than fit).
        val scroll = remember { Animatable(0f) }
        // (Update 156: whether the bookcase was going round the last time it moved, so only that can jump; without Endless
        // it glides as it always did.)
        val wentRound = remember { booleanArrayOf(false) }
        LaunchedEffect(focusVRow, plan.rows, visibleRows, scrolls) {
            val target = if (scrolls) focusVRow - (visibleRows - 1) / 2f
                else (focusRow - (visibleRows - 1) / 2f).coerceIn(0f, (plan.rows - visibleRows).toFloat().coerceAtLeast(0f))
            // (Update 156: a long way round, e.g. Endless switched on / off after a few laps, jumps instead of racing.)
            val jump = kotlin.math.abs(scroll.value - target) > plan.rows && (scrolls || wentRound[0])
            wentRound[0] = scrolls
            if (jump) scroll.snapTo(target)
            else scroll.animateTo(target, tween(320, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
        }
        // Few shelves: the bookcase sits in the middle of the room.
        val usedH = plan.rowH * visibleRows
        val top = headerH + ((areaH - usedH) / 2).coerceAtLeast(0.dp)

        // Header: the category and how many games.
        Row(Modifier.fillMaxWidth().height(headerH), verticalAlignment = Alignment.CenterVertically) {
            CategoryTitle.Native() // (Update 212: the layout's own category title.)
            Text(c.categoryLabel, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, style = nameTextStyle(com.shelf.launcher.data.NameBackdrop.SHADOW))
            Spacer(Modifier.width(8.dp))
            Text(games.size.toString(), color = ShelfTheme_accent(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        // The pull-out of the game you're on (update 102), like taking a case off a real shelf: 0 = on the shelf, 1 = out
        // and facing you. First it slides up out of its gap, then it turns from its spine to its front while it comes
        // forward. The game you left goes back the same way, the other way round, at the same time.
        val pull = remember { Animatable(0f) }
        // Update 156: which lap of the endless bookcase the game coming out is on (always 0 without it).
        var shownKey by remember { mutableStateOf<String?>(null) }
        var shownLap by remember { mutableStateOf(0) }
        // Update 228 (#17): the games going back, each with its own motion, run apart from the pull-out. One shared
        // "going back" was started inside the pull-out's effect, so the next step (within ~0.3 s, e.g. holding the D-pad)
        // cancelled it: the game going back vanished in one frame and its spine stood back in the shelf at once; the same
        // index looked up in a new category made a random spine there lift; going back to a game still on its way in
        // snapped it in and pulled it out from nothing again; and it went back in 320 ms eased, against 620 ms out.
        // Now: by game, never cut off, at the pull-out's own pace the other way round, and a game you come back to while
        // it's going in comes out again from where it is.
        val backs = remember { androidx.compose.runtime.mutableStateListOf<ShelfBack>() }
        val backScope = androidx.compose.runtime.rememberCoroutineScope()
        val indexOf = remember(games) { HashMap<String, Int>(games.size * 2).also { m -> games.forEachIndexed { i, g -> m.putIfAbsent(g.stableKey, i) } } }
        LaunchedEffect(c.focus, games) {
            val focusKey = games.getOrNull(c.focus)?.stableKey
            val leaving = shownKey
            var from = 0f
            if (leaving == null || leaving !in indexOf) {
                // A new category or list: nothing of it is out yet.
                backs.clear()
            } else if (leaving == focusKey && shownLap == lap) {
                // The same game (its art came in, the list was redone): it stays where it is.
                from = pull.value
            } else if (pull.value > 0f) {
                backs.removeAll { it.key == leaving }
                val b = ShelfBack(leaving, shownLap, Animatable(pull.value))
                backs += b
                backScope.launch {
                    b.a.animateTo(0f, tween((620 * b.a.value).toInt().coerceAtLeast(80), easing = LinearEasing))
                    backs.remove(b)
                }
            }
            // Back to a game that's still on its way in: it comes out again from there.
            backs.firstOrNull { it.key == focusKey && it.lap == lap }?.let { b -> from = b.a.value; backs.remove(b) }
            shownKey = focusKey
            shownLap = lap
            pull.snapTo(from)
            if (from <= 0f) kotlinx.coroutines.delay(40)
            if (from < 1f) pull.animateTo(1f, tween((620 * (1f - from)).toInt().coerceAtLeast(1), easing = LinearEasing))
        }
        // Update 156: the shelf (counted on round the endless bookcase) a game going back stands on.
        fun backVRow(b: ShelfBack): Int = (indexOf[b.key]?.let { plan.slots.getOrNull(it)?.row } ?: -1) + b.lap * plan.rows
        /** How far [i], on shelf [r] (counted on round the endless bookcase), is pulled out right now. */
        fun pulled(i: Int, r: Int): Float {
            if (i == c.focus && r == focusVRow) return pull.value
            if (backs.isEmpty()) return 0f
            val k = games.getOrNull(i)?.stableKey ?: return 0f
            for (b in backs) if (b.key == k && r == backVRow(b)) return b.a.value
            return 0f
        }
        // Update 136: the games either side make room for the one pulled out (half its front's width each way), so its
        // front no longer hides the spines next to it; they close up again as it goes back.
        val sideOut = Features.shelfPulled.state.value == Features.ShelfPulled.SIDE
        // (Update 156: [r] / [fromRow] are the shelves counted on round the endless bookcase; without it, the shelves.)
        fun makeRoom(i: Int, r: Int, from: Int, fromRow: Int, p: Float): Dp {
            if (from !in games.indices || i == from || p <= 0f) return 0.dp
            val sf = plan.slots.getOrNull(from) ?: return 0.dp
            if (plan.slots.getOrNull(i) == null || r != fromRow) return 0.dp
            val bigW = (plan.rowH * 0.9f).coerceAtMost(areaH * 0.7f) * pulledAspect(games[from].platform, sideOut)
            val push = ((bigW - sf.w) / 2 + 6.dp).coerceAtLeast(0.dp) * shelfFront(p)
            return if (i < from) -push else push
        }

        // (Update 229, #94: the shelves end above the footer — a shelf more than fit stood half behind the game's name and
        // console there, its spines running through the text.)
        Box(Modifier.fillMaxSize().clipToBounds().drawWithContent {
            clipRect(bottom = (size.height - footerH.toPx()).coerceAtLeast(0f)) { this@drawWithContent.drawContent() }
        }.categorySlide()) { // (update 112: a new category slides / fades in)
            // Shelves (boards) and their spines.
            // Update 130 (lag going over many games): the scroll is read where things are placed, not while composing, so
            // a scroll no longer rebuilds every shelf and spine on every frame; only a new shelf coming into view does.
            val firstRow by remember { androidx.compose.runtime.derivedStateOf { kotlin.math.floor(scroll.value).toInt() - 1 } }
            for (r in firstRow..(firstRow + visibleRows + 2)) {
                if (!scrolls && (r < 0 || r >= plan.rows)) continue
                // Update 156: [r] counts on round the endless bookcase; [pr] is the shelf of the plan it shows.
                val pr = if (scrolls) r.mod(plan.rows) else r
                val rowLeft = ((areaW - plan.rowW[pr]) / 2).coerceAtLeast(0.dp)
                key(r) {
                ShelfBoard(look, Modifier.offset { IntOffset(0, (top + plan.rowH * (r - scroll.value)).roundToPx()) }.fillMaxWidth().height(plan.rowH))
                for (i in slotsByRow[pr]) {
                    val s = plan.slots[i]
                    val g = games[i]
                    key(g.stableKey) {
                        Box(
                            Modifier
                                .offset { IntOffset((rowLeft + s.x).roundToPx(), (top + plan.rowH * (r - scroll.value) + plan.rowH * 0.9f - s.h).roundToPx()) }
                                .size(s.w, s.h)
                                // Pulled out: it slides up out of its place; a spine then turns away as the front comes round,
                                // a front / turned case hands over to the pulled-out one as that comes forward.
                                .graphicsLayer {
                                    val pv = pulled(i, r)
                                    var room = makeRoom(i, r, c.focus, focusVRow, pull.value)
                                    for (b in backs) room += makeRoom(i, r, indexOf[b.key] ?: -1, backVRow(b), b.a.value)
                                    translationX = room.toPx()
                                    if (pv > 0f) {
                                        translationY = -size.height * PULL_LIFT * shelfSlide(pv)
                                        if (stand == Features.ShelfStand.SPINES) {
                                            rotationY = 90f * shelfTurn(pv)
                                            cameraDistance = 14f * density
                                            alpha = if (shelfTurn(pv) >= 0.99f) 0f else 1f
                                        } else {
                                            alpha = if (shelfFront(pv) > 0f) 0f else 1f
                                        }
                                    }
                                    flatTextFor3d() // Update 177: drawn flat, then turned (Android 13 text cache)
                                }
                                .pointerInput(i) { detectTapGestures { if (i == GridHome.focus) open() else select(i) } }
                        ) {
                            when (stand) {
                                Features.ShelfStand.SPINES -> Spine(c, g, Modifier.fillMaxSize())
                                Features.ShelfStand.FRONTS -> PulledCase(c, g, false, Modifier.fillMaxSize(), detail = 240)
                                Features.ShelfStand.ANGLED -> AngledCase(c, g, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
                }
            }

            // The pulled-out game(s), in front of everything: from the raised spine's place round to its front, then it bobs.
            @Composable
            fun Pulled(index: Int, progress: () -> Float, resting: Boolean, vrow: Int) {
                // (Update 130: read here, so the pull-out's animation redraws only the game coming out, not the whole shelf.)
                val p = progress()
                val s = plan.slots.getOrNull(index) ?: return
                val g = games.getOrNull(index) ?: return
                if (s.row !in 0 until plan.rows) return
                val turn = shelfFront(p)
                if (turn <= 0f) return
                // (Update 156: [vrow] is its shelf counted on round the endless bookcase; without it, its own shelf.)
                val rowTop = top + plan.rowH * (vrow - scroll.value)
                val rowLeft = ((areaW - plan.rowW[s.row]) / 2).coerceAtLeast(0.dp)
                val side = Features.shelfPulled.state.value == Features.ShelfPulled.SIDE
                val frontAspect = pulledAspect(g.platform, side)
                // 25 % smaller than before (it was 1.18 × the shelf's height).
                val bigH = (plan.rowH * 0.9f).coerceAtMost(areaH * 0.7f)
                val bigW = bigH * frontAspect
                // It starts where it stood, raised (a spine: the front at the same height, centred on it; a turned case:
                // its front part) and ends a little above its shelf.
                val startH = s.h
                val startW = when (stand) { Features.ShelfStand.FRONTS -> s.w; else -> startH * frontAspect }
                val startX = when (stand) {
                    Features.ShelfStand.SPINES -> rowLeft + s.x + s.w / 2 - startW / 2
                    Features.ShelfStand.FRONTS -> rowLeft + s.x
                    Features.ShelfStand.ANGLED -> rowLeft + s.x + s.w - startW
                }
                // The angle it comes round from: edge-on for a spine, its turned angle for a turned case, none facing forward.
                val fromAngle = when (stand) { Features.ShelfStand.SPINES -> -90f; Features.ShelfStand.FRONTS -> 0f; Features.ShelfStand.ANGLED -> ANGLE_FRONT_DEG }
                val startY = rowTop + plan.rowH * 0.9f - s.h - s.h * PULL_LIFT
                val endX = (rowLeft + s.x + s.w / 2 - bigW / 2).coerceIn(0.dp, (areaW - bigW).coerceAtLeast(0.dp))
                val endY = (rowTop + plan.rowH * 0.9f - bigH - plan.rowH * 0.06f).coerceIn(top - 10.dp, (fullH - footerH - bigH).coerceAtLeast(0.dp))
                val w = startW + (bigW - startW) * turn
                val h = startH + (bigH - startH) * turn
                // (Update 228, #17: the bob carries on, fading out with the turn, while the game goes back — it stopped
                // dead the moment you moved on, a jump of up to 14° and 3 % of its height.)
                val bob = if (Features.shelfBob.state.value && (!resting || p >= 0.999f)) rememberShelfBob() else null
                val bobK = if (resting) 1f else turn
                Box(
                    Modifier
                        .offset { IntOffset((startX + (endX - startX) * turn).roundToPx(), (startY + (endY - startY) * turn).roundToPx()) }
                        .size(w, h)
                        .then(if (resting) Modifier.focusedCard() else Modifier)
                        .graphicsLayer {
                            // Comes round from edge-on (its spine side) to facing you, then (resting) bobs and turns gently.
                            rotationY = fromAngle * (1f - turn) + (bob?.let { 14f * bobK * kotlin.math.sin(it.second.value * 2f * Math.PI.toFloat()) } ?: 0f)
                            translationY = bob?.let { -size.height * 0.03f * bobK * (0.5f - 0.5f * kotlin.math.cos(it.first.value * 2f * Math.PI.toFloat())) } ?: 0f
                            cameraDistance = 14f * density
                            // A soft shadow grows under it as it comes off the shelf.
                            shadowElevation = 14f * density * turn
                            shape = RoundedCornerShape(3.dp)
                            flatTextFor3d() // Update 177: drawn flat, then turned (Android 13 text cache)
                        }
                        .then(if (resting) Modifier.pointerInput(index) { detectTapGestures { open() } } else Modifier)
                ) {
                    PulledCase(c, g, side, Modifier.fillMaxSize())
                }
            }
            // Update 112: one keyed list (by game) for the one going back and the one coming out, so the game you leave keeps
            // its own picture on the way back — it used to be drawn afresh, flashing its plain cover for a moment.
            // (Update 228, #17: every game still going back, then the one coming out — drawn last, in front.)
            // (One keyed loop, as update 112 made it, so a game keeps its own picture from coming out to going back.)
            val focusKey = games.getOrNull(c.focus)?.stableKey
            val shown = buildList<ShelfBack?> {
                val seenKeys = HashSet<String>()
                for (b in backs) if (b.key != focusKey && b.key in indexOf && seenKeys.add(b.key)) add(b)
                if (c.game != null && focusKey != null) add(null)
            }
            for (b in shown) {
                val k = b?.key ?: focusKey ?: continue
                val idx = if (b == null) c.focus else indexOf[k] ?: continue
                key(k) {
                    Pulled(idx, if (b == null) ({ pull.value }) else ({ b.a.value }), resting = b == null, vrow = if (b == null) focusVRow else backVRow(b))
                }
            }
        }

        // Footer: the game you're on and the count, like a shelf label.
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(footerH), verticalArrangement = Arrangement_Center()) {
            c.game?.let { gg ->
                Text(
                    LocalTitleOptions.current.display(gg.name), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = nameTextStyle(com.shelf.launcher.data.NameBackdrop.SHADOW)
                )
                Text(
                    listOfNotNull(LocalTitleOptions.current.console(gg.platform), c.playtime(gg)?.let { stylePlaytime(it, c.lastPlayed(gg) != null) }, gamesCount(games.size)).joinToString("  ·  "),
                    color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1
                )
            }
        }
    }
    }
}

/** Turned cases (update 103): how much of the spine's / the front's width still shows (cos 35° / cos 55°). */
private const val ANGLE_SPINE = 0.82f
private const val ANGLE_FRONT = 0.57f
private const val ANGLE_FRONT_DEG = 55f

/**
 * A case standing turned on the shelf: its spine on the left and its front, meeting at the corner nearest you, each
 * face going back from it (spine 35°, front 55°), like a box seen from its corner.
 */
@Composable
private fun AngledCase(c: StyleCtx, g: Game, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val h = maxHeight
        val frontW = h * pulledAspect(g.platform, false)
        // What's left of the slot's width is the spine's share.
        val spineShown = (maxWidth - frontW * ANGLE_FRONT).coerceAtLeast(4.dp)
        val spineW = spineShown / ANGLE_SPINE
        Box(
            Modifier.offset(x = spineShown - spineW).size(spineW, h).graphicsLayer {
                transformOrigin = TransformOrigin(1f, 0.5f)
                rotationY = -35f
                cameraDistance = 10f * density
                flatTextFor3d() // Update 177: drawn flat, then turned (Android 13 text cache)
            }
        ) { Spine(c, g, Modifier.fillMaxSize()) }
        Box(
            Modifier.offset(x = spineShown).size(frontW, h).graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0.5f)
                rotationY = ANGLE_FRONT_DEG
                cameraDistance = 10f * density
                flatTextFor3d() // Update 177: drawn flat, then turned (Android 13 text cache)
            }
        ) { PulledCase(c, g, false, Modifier.fillMaxSize(), detail = 240) }
    }
}

/** How far a pulled-out spine rises out of its gap, as a share of its height. */
private const val PULL_LIFT = 0.42f

private fun ease(t: Float): Float = FastOutSlowInEasing.transform(t.coerceIn(0f, 1f))

/** Pull-out phases (0..1 of the whole move): the spine slides up (first 40 %), turns away (35–60 %), the front comes round and forward (45–100 %). */
private fun shelfSlide(p: Float): Float = ease(p / 0.4f)
private fun shelfTurn(p: Float): Float = ease((p - 0.35f) / 0.25f)
private fun shelfFront(p: Float): Float = ease((p - 0.45f) / 0.55f)

@Composable private fun ShelfTheme_accent(): Color = com.shelf.launcher.ui.theme.ShelfTheme.accent

private fun Arrangement_Center() = androidx.compose.foundation.layout.Arrangement.Center

/** Width / height of the pulled-out game: its front (the console's cover frame) or its 3D box seen from the side. */
private fun pulledAspect(platform0: String, side: Boolean): Float {
    // (Update 228, #18: in the case picked for every game — R1 › Case — when there is one.)
    val platform = LookStyles.casePlatform(platform0) ?: platform0
    val box = MediaTemplates.of(platform)?.box
    if (side && box != null) return box.aspect * 1.12f
    val cov = MediaCovers.used(platform)
    return (cov?.frontAspect ?: 0.7f).coerceIn(0.55f, 1.45f)
}

/** The resting motion clocks (bob, turn), on their own beats. */
@Composable
private fun rememberShelfBob(): Pair<androidx.compose.runtime.State<Float>, androidx.compose.runtime.State<Float>> {
    val t = rememberInfiniteTransition(label = "shelfBob")
    val bob = t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "bob")
    val turn = t.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "turn")
    return bob to turn
}

/** The pulled-out game: its front in the console's cover frame (or the plain cover), or the 3D box turned to its side. */
@Composable
private fun PulledCase(c: StyleCtx, g: Game, side: Boolean, modifier: Modifier, detail: Int = 480) =
    // Update 151: the shelf waits for the case (a plain case shape meanwhile), never the cover first (waitForCase).
    PulledOutCase(g, c.cover(g), c.state.physicalMedia[g.stableKey], side, modifier, detail, waitForCase = true)

/**
 * The game pulled out of a shelf of spines (Game shelf, TV room shelf; update 129: shared), as set in "Pulled out, it
 * shows" ([Features.shelfPulled]):
 * - Real case (default): its own scanned case when ScreenScraper has one, else Voltcap's (the console's cover frame);
 * - Voltcap's case: always the console's cover frame with the cover in it;
 * - Plain cover: the cover alone;
 * - 3D box from the side ([side]).
 */
@Composable
internal fun PulledOutCase(
    g: Game, cover: String?, physical: List<Pair<String, String>>?, side: Boolean, modifier: Modifier, detail: Int = 480,
    /**
     * Update 151 (Game shelf): the real case from the first frame. While the game's scans aren't known yet ([physical]
     * null: not looked up, which the shelf does at once) a plain case in the case's shape stands in, and the scan comes
     * in over that plain case, not over the cover. (Moving along the shelf showed the cover, or a wide picture, first
     * and only then the real case.)
     */
    waitForCase: Boolean = false
) {
    val mode = Features.shelfPulled.state.value
    // Update 228 (#18): R1 › Case (one console's case for every game) did nothing here — the shelf drew each game in its
    // own console's case, and its own scan. Now as every other case view (RealMedia.kt): the picked case, with the
    // game's cover in it and never its own scan, the case's own branding left off (LocalCaseOwner).
    val cp = LookStyles.casePlatform(g.platform) ?: g.platform
    val forAll = LookStyles.caseForAll() && cp != g.platform
    val phys = if (forAll) emptyList() else physical
    val box = MediaTemplates.of(cp)?.box
    // The game's own scanned case and spine (update 101). Update 129: "Real case" always looks for the scan first.
    val original = Features.originalCases.state.value || mode == Features.ShelfPulled.FRONT
    val scan = if (original) BoxTexture.front(phys, g.platform) else phys?.firstOrNull { it.first == "Box" }?.second
    val ownSpine = if (original) BoxTexture.spine(phys, g.platform) else null
    // Update 151: not known yet → the plain case shape (only where a scan would win over the cover).
    val waiting = waitForCase && phys == null && ((side && box != null) || mode == Features.ShelfPulled.FRONT)
    if (waiting) {
        val aspect = if (side && box != null) box.aspect * 1.12f
            else MediaCovers.used(cp)?.frontAspect ?: realCaseMm(cp)?.let { it.first / it.second } ?: 0.7f
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val w = minOf(maxWidth, maxHeight * aspect)
            Box(Modifier.size(w, w / aspect).clip(RoundedCornerShape(3.dp)).background(CaseArtPlaceholder).border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(3.dp)))
        }
        return
    }
    if (side && box != null) {
        val (front, isBox) = if (original && scan != null) scan to true else boxFront(box, cover, scan)
        Box3D(box, front, isBox, gameDisplayName(g.name), modifier, spin = false, detail = detail, realSpine = ownSpine)
        return
    }
    val cov = if (mode != Features.ShelfPulled.COVER) MediaCovers.used(cp) else null
    var scanFailed by androidx.compose.runtime.remember(scan) { androidx.compose.runtime.mutableStateOf(false) }
    // (Update 202: never for an Android game — its case is Voltcap's own with the Android band, androidOwnCase.)
    if (mode == Features.ShelfPulled.FRONT && scan != null && !scanFailed && !androidOwnCase(g.platform)) {
        // (Update 151: on the shelf the scan fades in over the plain case, not over the cover.)
        ScanCase(scan, cov?.frontAspect ?: realCaseMm(cp)?.let { it.first / it.second } ?: 0.7f, modifier, onFail = { scanFailed = true }, cover = if (waitForCase) null else cover)
        return
    }
    // (Update 173: an app without art: its icon in the case's window.)
    if (cov != null) androidx.compose.runtime.CompositionLocalProvider(LocalCaseOwner provides if (forAll) g.platform else null) {
        CoverCase(cov, coverOrAppIcon(g, cover), placeholderBrush(g.hue), modifier)
    }
    else Box(modifier.clip(RoundedCornerShape(4.dp)).background(placeholderBrush(g.hue))) {
        // (Update 202: the cover's file's flat strips trimmed — coverModel / TrimMargins.kt; Crop fills the front.)
        if (cover != null) AsyncImage(coverModel(cover), g.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork())
    }
}

/** One spine: the console's real spine with the game's name running up it, or a plain one with the console's logo. */
@Composable
private fun Spine(c: StyleCtx, g: Game, modifier: Modifier) = GameSpine(g, c.state.physicalMedia[g.stableKey], modifier)

/**
 * A game as its spine (the bookcase, and update 113's Spines game look everywhere): [physical] is its scans (null:
 * the ones the home screen has, [HomeScans]).
 */
@Composable
internal fun GameSpine(g: Game, physical: List<Pair<String, String>>?, modifier: Modifier, fit: Boolean = false) {
    // [fit]: kept at a spine's shape in the middle of a box of another shape (a cover-shaped slot).
    if (fit) {
        Box(modifier, contentAlignment = Alignment.Center) {
            SpineBody(g, physical, Modifier.fillMaxHeight().aspectRatio(SPINE_ASPECT, matchHeightConstraintsFirst = true))
        }
    } else SpineBody(g, physical, modifier)
}

@Composable
private fun SpineBody(g: Game, physical: List<Pair<String, String>>?, modifier: Modifier) {
    val scans = physical ?: HomeScans.map[g.stableKey]
    // Not looked up yet (the Spines look on a layout that doesn't ask for scans): ask once it has stayed on screen a moment.
    // (Update 122: always — a game's real spine is used whatever the Original cases switch says.)
    if (physical == null && !HomeScans.map.containsKey(g.stableKey)) {
        LaunchedEffect(g.stableKey) { kotlinx.coroutines.delay(400); HomeScans.need?.invoke(g) }
    }
    // The game's own spine (update 92 / 101): ScreenScraper's spine scan, turned upright when it was scanned lying down
    // (update 123: a strip cut from its box scan only with "Spines cut from box scans" on). Else the console's spine below.
    // Update 122: a real spine scan always wins over Voltcap's own spines (the console's template spine with the name,
    // the strip of the cover): it used to need Original cases on, so games with their real spine still showed ours.
    // (Update 129: none of the games' own spines while one console's spine is picked for all, R1 › Spine.)
    val forAll = LookStyles.spineForAll()
    // (Update 147: "Matching spines" leaves the real scans out, so every spine is Voltcap's own.)
    val matching = Features.spinesOwn.state.value
    // Update 205: its scans aren't known yet (they're all read at start; this is the first moment): a neutral strip in
    // the game's colour, never Voltcap's own spine that then pops to the real one.
    if (physical == null && g.sourceType == com.shelf.launcher.data.SourceType.ROM && !HomeScans.known(g.stableKey) && !forAll && !matching) {
        Box(modifier.clip(RoundedCornerShape(2.dp)).background(placeholderBrush(g.hue)))
        return
    }
    val realSpine = if (forAll || matching) null else scans?.firstOrNull { it.first == "Spine" }?.second
    val own = realSpine ?: if (Features.originalCases.state.value && !forAll && !matching) BoxTexture.spine(scans, g.platform) else null
    var failed by androidx.compose.runtime.remember(own) { androidx.compose.runtime.mutableStateOf(false) }
    if (own != null && !failed) {
        var lying by androidx.compose.runtime.remember(own) { androidx.compose.runtime.mutableStateOf(false) }
        // Update 147: the scan's own shape (width / height standing up), so a slot wider than the spine no longer
        // stretches it sideways; until it has loaded, the usual spine shape.
        var shape by androidx.compose.runtime.remember(own) { androidx.compose.runtime.mutableFloatStateOf(SPINE_ASPECT) }
        SpineSlot(shape, modifier) { inner ->
            Box(inner.clip(RoundedCornerShape(2.dp)).background(placeholderBrush(g.hue))) {
                AsyncImage(
                    own, g.name, contentScale = ContentScale.FillBounds,
                    onSuccess = { s ->
                        val d = s.result.drawable
                        lying = d.intrinsicWidth > d.intrinsicHeight
                        if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                            val a = if (lying) d.intrinsicHeight.toFloat() / d.intrinsicWidth else d.intrinsicWidth.toFloat() / d.intrinsicHeight
                            shape = a.coerceIn(0.03f, 0.35f)
                        }
                    },
                    onError = { failed = true },
                    modifier = if (lying) Modifier.spineText().fillMaxSize() else Modifier.fillMaxSize()
                )
            }
        }
        return
    }
    // Update 125: no real spine: your own console spine (CustomSpines; Steam's and Android's too), the name fitted in the
    // box left for it.
    val tpl = if (Features.coverTemplates.state.value || forAll || matching || MediaCovers.own(g.platform) != null) CustomSpines.of(LookStyles.spinePlatform(g.platform)) else null
    val name = gameDisplayName(g.name)
    if (tpl != null) {
        // (Update 147: at the console spine's own shape, not stretched to a wider slot.)
        SpineSlot(tpl.aspect, modifier) { inner ->
            SpinePicture(tpl, inner.clip(RoundedCornerShape(2.dp))) { ink ->
                Box(Modifier.fillMaxSize().padding(horizontal = 1.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
                    SpineName(name, ink, Modifier.spineText())
                }
            }
        }
        return
    }
    // Update 119: no real spine (no scan of its own, none for its console in the pack): no drawn stand-in any more —
    // a strip of the game's own cover, with its name up it, until a real spine is there.
    val cover = HomeScans.cover?.invoke(g.stableKey)
    Box(modifier.clip(RoundedCornerShape(2.dp)).background(placeholderBrush(g.hue))) {
        if (cover != null) AsyncImage(cover, g.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.45f)))),
            contentAlignment = Alignment.Center
        ) {
            SpineName(name, Color.White, Modifier.spineText())
        }
    }
}

/**
 * Update 147: a spine of shape [aspect] (width / height) in [modifier]'s slot: the slot's full height, as wide as that
 * shape makes it (never wider than the slot), in the middle — a wider slot no longer stretches the picture.
 */
@Composable
private fun SpineSlot(aspect: Float, modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w = if (maxHeight == androidx.compose.ui.unit.Dp.Infinity) maxWidth else minOf(maxWidth, maxHeight * aspect)
        content(Modifier.width(w).fillMaxHeight())
    }
}

/**
 * Update 124: a game's name up its spine — as big as fits (11 sp down to 6 sp), over two lines when it has to, so long
 * names no longer end in "…" after a few letters.
 */
@Composable
private fun SpineName(name: String, ink: Color, modifier: Modifier) {
    Text(
        // (Update 129: down to 4.5 sp, so a name like "God of War" fits a slim spine whole instead of being cut.)
        name, color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 1.05.em,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 4.5.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
        modifier = modifier
    )
}

/** Lays text out turned on its side (reads top to bottom), as printed on a spine. */
private fun Modifier.spineText(): Modifier = this
    .layout { measurable, constraints ->
        val p = measurable.measure(Constraints(maxWidth = constraints.maxHeight.takeIf { it != Constraints.Infinity } ?: 2000, maxHeight = constraints.maxWidth))
        layout(p.height, p.width) { p.placeWithLayer(-(p.width - p.height) / 2, (p.width - p.height) / 2) { rotationZ = 90f } }
    }

private class ShelfPalette(
    val wallTop: Color, val wallBottom: Color,
    val topFace: List<Color>, val front: List<Color>, val edge: Color, val shine: Color,
    val upright: List<Color>, val grain: Color
)

private fun shelfPalette(look: Features.ShelfLook): ShelfPalette = when (look) {
    Features.ShelfLook.WOOD -> ShelfPalette(
        Color(0x00000000), Color(0x66291A0F),
        listOf(Color(0xFFB98757), Color(0xFF9C6D43)), listOf(Color(0xFF7A5232), Color(0xFF54351F), Color(0xFF3A2516)),
        Color(0xFF24150B), Color(0x55FFE2BE),
        listOf(Color(0xFF5A3A22), Color(0xFF7A5232), Color(0xFF4A2F1B)), Color(0x22000000)
    )
    Features.ShelfLook.GLASS -> ShelfPalette(
        Color(0x00FFFFFF), Color(0x26FFFFFF),
        listOf(Color(0xCCFFFFFF), Color(0x88E7F0F8)), listOf(Color(0x77D8E6F2), Color(0x44B9CCDD), Color(0x22FFFFFF)),
        Color(0x66FFFFFF), Color(0xAAFFFFFF),
        listOf(Color(0x44FFFFFF), Color(0x22FFFFFF), Color(0x44FFFFFF)), Color(0x10FFFFFF)
    )
    Features.ShelfLook.DARK -> ShelfPalette(
        Color(0x00000000), Color(0x77000000),
        listOf(Color(0xFF50545F), Color(0xFF3C3F49)), listOf(Color(0xFF2E3039), Color(0xFF1F2128), Color(0xFF121317)),
        Color(0xFF08090B), Color(0x44FFFFFF),
        listOf(Color(0xFF1A1B21), Color(0xFF2A2C35), Color(0xFF15161B)), Color(0x14FFFFFF)
    )
}

/**
 * One shelf of the bookcase: the back of the row (darker toward the board, so the games sit in a recess) and a board
 * with a lit top face, a front edge that catches the light, and a shadow thrown on the games' feet and on the wall below.
 */
@Composable
private fun ShelfBoard(look: Features.ShelfLook, modifier: Modifier) {
    val pal = remember(look) { shelfPalette(look) }
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val boardH = h * 0.1f
        val boardTop = h - boardH
        val topFaceH = boardH * 0.38f
        // The recess behind the games: light at the top, darker where they stand.
        drawRect(Brush.verticalGradient(listOf(pal.wallTop, pal.wallBottom), startY = 0f, endY = boardTop), size = androidx.compose.ui.geometry.Size(w, boardTop))
        // A soft shadow from the board above, along the top of the recess.
        drawRect(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (look == Features.ShelfLook.GLASS) 0.10f else 0.28f), Color.Transparent), startY = 0f, endY = h * 0.07f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.07f)
        )
        // The board's top face (what the games stand on) …
        drawRect(
            Brush.verticalGradient(pal.topFace, startY = boardTop, endY = boardTop + topFaceH),
            topLeft = androidx.compose.ui.geometry.Offset(0f, boardTop), size = androidx.compose.ui.geometry.Size(w, topFaceH)
        )
        // … its front …
        drawRect(
            Brush.verticalGradient(pal.front, startY = boardTop + topFaceH, endY = h),
            topLeft = androidx.compose.ui.geometry.Offset(0f, boardTop + topFaceH), size = androidx.compose.ui.geometry.Size(w, boardH - topFaceH)
        )
        // … the light on its front edge and the dark line under it.
        drawLine(pal.shine, androidx.compose.ui.geometry.Offset(0f, boardTop + topFaceH), androidx.compose.ui.geometry.Offset(w, boardTop + topFaceH), strokeWidth = 1.5f)
        drawLine(pal.edge, androidx.compose.ui.geometry.Offset(0f, h - 1f), androidx.compose.ui.geometry.Offset(w, h - 1f), strokeWidth = 2f)
        // Wood grain / brushed lines along the board.
        if (look != Features.ShelfLook.GLASS) {
            var y = boardTop + topFaceH + (boardH - topFaceH) * 0.3f
            while (y < h - 2f) {
                drawLine(pal.grain, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(w, y), strokeWidth = 1f)
                y += (boardH - topFaceH) * 0.34f
            }
        }
        // The shadow the board throws on whatever is under it.
        drawRect(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.38f), Color.Transparent), startY = h, endY = h + 9.dp.toPx()),
            topLeft = androidx.compose.ui.geometry.Offset(0f, h), size = androidx.compose.ui.geometry.Size(w, 9.dp.toPx())
        )
    }
}

/**
 * The whole screen as the bookcase the shelves are in (Game shelf › Bookcase background): a back panel with faint
 * planks, the two side uprights where the shelves end, and a top crown above the first shelf.
 */
@Composable
internal fun ShelfBackdrop(look: Features.ShelfLook, modifier: Modifier) {
    val pal = remember(look) { shelfPalette(look) }
    val back = when (look) {
        Features.ShelfLook.WOOD -> listOf(Color(0xFF3B2718), Color(0xFF2A1A10))
        Features.ShelfLook.GLASS -> listOf(Color(0x14FFFFFF), Color(0x08FFFFFF))
        Features.ShelfLook.DARK -> listOf(Color(0xFF16171C), Color(0xFF0D0E11))
    }
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val post = 18.dp.toPx()
        drawRect(Brush.verticalGradient(back))
        // Faint vertical planks on the back panel.
        var x = post
        val plank = (w - post * 2) / 9f
        while (x < w - post) {
            drawLine(pal.grain, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, h), strokeWidth = 1f)
            x += plank
        }
        // Side uprights, lit from the left.
        drawRect(Brush.horizontalGradient(pal.upright, startX = 0f, endX = post), size = androidx.compose.ui.geometry.Size(post, h))
        drawRect(Brush.horizontalGradient(pal.upright.reversed(), startX = w - post, endX = w), topLeft = androidx.compose.ui.geometry.Offset(w - post, 0f), size = androidx.compose.ui.geometry.Size(post, h))
        drawLine(pal.shine, androidx.compose.ui.geometry.Offset(post, 0f), androidx.compose.ui.geometry.Offset(post, h), strokeWidth = 1f)
        drawLine(pal.edge, androidx.compose.ui.geometry.Offset(w - post, 0f), androidx.compose.ui.geometry.Offset(w - post, h), strokeWidth = 1.5f)
        // Inner shadow down both uprights, onto the back panel.
        drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), startX = post, endX = post + 22.dp.toPx()), topLeft = androidx.compose.ui.geometry.Offset(post, 0f), size = androidx.compose.ui.geometry.Size(22.dp.toPx(), h))
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), startX = w - post - 22.dp.toPx(), endX = w - post), topLeft = androidx.compose.ui.geometry.Offset(w - post - 22.dp.toPx(), 0f), size = androidx.compose.ui.geometry.Size(22.dp.toPx(), h))
        // The crown along the top.
        val crown = 10.dp.toPx()
        drawRect(Brush.verticalGradient(pal.topFace + pal.front.take(1)), size = androidx.compose.ui.geometry.Size(w, crown))
        drawLine(pal.edge, androidx.compose.ui.geometry.Offset(0f, crown), androidx.compose.ui.geometry.Offset(w, crown), strokeWidth = 2f)
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent), startY = crown, endY = crown + 14.dp.toPx()), topLeft = androidx.compose.ui.geometry.Offset(0f, crown), size = androidx.compose.ui.geometry.Size(w, 14.dp.toPx()))
    }
}


/** Update 228 (#17): a game going back onto the shelf: which one, its lap round the endless bookcase, its own motion. */
private class ShelfBack(
    val key: String,
    val lap: Int,
    val a: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>
)
