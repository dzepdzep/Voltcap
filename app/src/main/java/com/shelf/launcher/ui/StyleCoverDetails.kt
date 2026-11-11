package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.imageLoader
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.abs
import kotlin.math.floor

/*
 * Update 374 (Toni: "Coverflow details — LaunchBox Big Box 3-Dimensions-inspired"): one scene. The game you're on as a big
 * 3D retail box on the left (the console's own case, or its real scan: Case3D, the same model every case look uses), its
 * facts and story on the right, a shelf of closely packed upright spines along the bottom that glides as ONE row, the
 * selected spine lifting off it. Behind: the game's own art, darkened toward the theme. No new 3D engine: Case3D, GameSpine
 * (the console's real spine / the game's scan), case3dAspect / shelfSpineAspect (each console's real proportions),
 * PickedFacing (the "faces you / turned" setting), Endless (rememberLoopFocus). ◀ ▶ move along the shelf; A / tap opens the game.
 *
 * Batch 523r (owner's sketch, 11 Nov): recomposed — the big case at the upper LEFT under the game's name, console and year; the
 * facts and story at the upper right; the shelf at the lower RIGHT holds the other games as smaller 3D cases (Case3D,
 * case3dAspect: each console's own case and art) turned to show spine and front, packed with a little overlap. The spines
 * (GameSpine) are no longer drawn upright here. The case flight (CoverFlight) and the glide are the same objects as before.
 */

/** ▲ ▼ go on to what lies above / below (the shelf takes ◀ ▶ through the home screen's own previous / next game). */
internal fun coverDetailsKey(ctx: StyleKeyCtx): Boolean = when (ctx.key) {
    Key.DirectionUp -> ctx.reach(true)
    Key.DirectionDown -> ctx.reach(false)
    else -> false
}

/**
 * The room behind the scene: the theme's dark with the game's art over the whole screen (full-bleed, not just the area
 * between the status bar and the legend), kept to its own shape (a portrait cover is shown whole, never stretched), then
 * darkened toward the theme, most under the facts and the shelf.
 */
@Composable
internal fun CoverDetailsBackdrop(modifier: Modifier, art: String? = null) {
    val base = themeDark(Color(0xFF0A0D13), 0.6f)
    val back = themeDark(Color(0xFF070A10), 0.6f)
    Box(modifier.background(Brush.verticalGradient(listOf(themeDark(Color(0xFF121722), 0.6f), base))).clipToBounds()) {
        val portrait = (CoverAspects.rawOf(art) ?: 2f) < 1f
        HeldImage(
            art, Modifier.fillMaxSize(), fadeMs = 450, shared = true,
            contentScale = if (portrait) ContentScale.Fit else ContentScale.Crop,
            imageModifier = if (!LocalLiteEffects.current) Modifier.blur(if (portrait) 24.dp else 6.dp) else Modifier
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to back.copy(alpha = 0.55f), 0.55f to back.copy(alpha = 0.82f), 1f to back.copy(alpha = 0.7f))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.5f to back.copy(alpha = 0.35f), 1f to back.copy(alpha = 0.96f))))
    }
}

private const val SHELF_GAP_DP = 0.5f

/** Update 487 (K1): the gap between Cover details' spines for its Game spacing [pct] (100 = the packed 0.5 dp). */
internal fun coverShelfGapDp(pct: Int): Float = (SHELF_GAP_DP + 4f * (pct - 100) / 100f).coerceAtLeast(0f)
private const val SHELF_MAX_SIDE = 46

@Composable
internal fun StyleCoverDetails(c: StyleCtx, modifier: Modifier = Modifier) {
    val g0 = c.game
    CoverShelfRequests(c)
    if (g0 == null) StyleEmpty(modifier) else CoverDetailsBody(c, g0, modifier)
}

/** The things the layout asks the home screen for, outside composition (spines of the games near the one you're on). */
@Composable
private fun CoverShelfRequests(c: StyleCtx) {
    androidx.compose.runtime.SideEffect {
        StyleNav.side = emptyList(); StyleNav.top = emptyList(); StyleNav.bar = emptyList()
    }
    val games = c.games
    // Update 398: the background and the big case's cover of the games either side are decoded in the exact shape the scene
    // draws them in, so moving on shows both the frame the game is picked (no solid case face, no late background).
    val warmCtx = androidx.compose.ui.platform.LocalContext.current
    val coverPx = cardArtPx(ArtKind.COVER)
    LaunchedEffect(c.focus, games.size, coverPx) {
        kotlinx.coroutines.delay(60)
        val loader = warmCtx.imageLoader
        val handles = ArrayList<coil.request.Disposable>()
        val loop = loopRowOn(c)
        val order = listOf(1, -1, 2, -2, 3, -3)
        val near = order.mapNotNull { d -> games.getOrNull(if (loop) (c.focus + d).mod(games.size.coerceAtLeast(1)) else c.focus + d) }
        handles += warmHeld(warmCtx, near.mapNotNull { c.backdrop(it) })
        // The big case's own front / back / spine pictures, decoded as Case3D reads them (Case3D showed the game's colour plate meanwhile).
        val realFront = c.type == com.shelf.launcher.data.CardArtType.CASE || c.type == com.shelf.launcher.data.CardArtType.CASE_REAL
        warmCase3d(warmCtx, near.map { gg -> case3dModels(gg, c.cover(gg), c.state.physicalMedia[gg.stableKey], realFront) })
        near.mapNotNull { c.cover(it) }.distinct().forEach { handles += loader.enqueue(artRequest(warmCtx, it, ArtKind.COVER, coverPx, fadeMillis = 0)) }
        try { kotlinx.coroutines.awaitCancellation() } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { kotlinx.coroutines.delay(1500) }
            handles.forEach { it.dispose() }
        }
    }
    val needSpine by rememberUpdatedState(c.actions.needSpine)
    LaunchedEffect(c.focus, games.size) {
        kotlinx.coroutines.delay(150)
        val loop = loopRowOn(c)
        val seen = HashSet<Int>()
        for (i in ((c.focus - SHELF_MAX_SIDE)..(c.focus + SHELF_MAX_SIDE)).sortedBy { abs(it - c.focus) }) {
            val at = if (loop) i.mod(games.size) else i
            if (!seen.add(at)) continue
            val gg = games.getOrNull(at) ?: continue
            val list = c.state.physicalMedia[gg.stableKey]
            if (list == null || (BoxTexture.spine(list, gg.platform) == null && abs(i - c.focus) <= 10)) {
                needSpine(gg)
                kotlinx.coroutines.delay(60)
            }
        }
    }
}

/**
 * Update 400 (reference: a boxed game seen from a little to the side and above, its spine and top showing): the big case's
 * turn and tilt follow Settings > Spine depth (Off: facing you, as before; Subtle: a little; Realistic 3D: the reference's).
 * Fixed per setting: nothing moves when the game or the category changes.
 */
internal fun bigCaseYaw(d: com.shelf.launcher.data.Features.SpineDepth) = when (d) {
    com.shelf.launcher.data.Features.SpineDepth.OFF -> 0f
    com.shelf.launcher.data.Features.SpineDepth.SUBTLE -> 14f
    com.shelf.launcher.data.Features.SpineDepth.REALISTIC -> 24f
}
internal fun bigCasePitch(d: com.shelf.launcher.data.Features.SpineDepth) = when (d) {
    com.shelf.launcher.data.Features.SpineDepth.OFF -> 4f
    com.shelf.launcher.data.Features.SpineDepth.SUBTLE -> 9f
    com.shelf.launcher.data.Features.SpineDepth.REALISTIC -> 12f
}
/** Update 403: a bigger turn needs more room round the case (the nearer edge grows with perspective): the same case, less of the canvas, so it is never cut or zoomed (update 402's try at a bigger yaw was). */
internal fun bigCaseFill(d: com.shelf.launcher.data.Features.SpineDepth) = when (d) {
    com.shelf.launcher.data.Features.SpineDepth.OFF -> 0.94f
    com.shelf.launcher.data.Features.SpineDepth.SUBTLE -> 0.86f
    com.shelf.launcher.data.Features.SpineDepth.REALISTIC -> 0.78f
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverDetailsBody(c: StyleCtx, g: Game, modifier: Modifier) {
    val accent = ShelfTheme.accent
    LaunchedEffect(g.stableKey) { kotlinx.coroutines.delay(250); c.actions.needMetadata(g) }
    // Batch 523r (owner's sketch): the game you're on as ONE big 3D case on the upper left under its title and console; its
    // facts and story at the upper right; the other games as smaller 3D cases on a shelf at the lower right, turned so their
    // spines and part of their fronts show, packed side by side with a little overlap. Moving on, the next case leaves the
    // shelf and grows / turns into the big place while the one you were on shrinks back into its own slot; the shelf glides
    // as one row. Endless: each game once, never repeated to fill the shelf.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth; val h = maxHeight
        val compact = h < 380.dp
        val pad = if (compact) 14.dp else 26.dp
        val leftW = (w * 0.44f).coerceIn(180.dp, 660.dp) // (523t, owner: the big case a little bigger)
        val shelfH = (h * (if (compact) 0.50f else 0.48f)).coerceIn(96.dp, 360.dp)
        val rightW = (w - leftW - pad * 2.2f).coerceAtLeast(120.dp)
        var bigAspect by remember { mutableStateOf(0.8f) }

        // The shelf first: the case flying out of it / back into it passes over it.
        CoverCaseShelf(
            c, bigAspect, accent,
            Modifier.align(Alignment.BottomEnd).padding(end = pad * 0.5f, bottom = pad * 0.35f).width(rightW + pad * 0.7f).height(shelfH)
        )
        // The facts at the upper right, above the shelf.
        CoverDetailsInfo(
            c, g, compact, accent,
            Modifier.align(Alignment.TopEnd).padding(top = pad, end = pad).width(rightW).height((h - shelfH - pad * 1.6f).coerceAtLeast(60.dp))
                .riseOnChange(g.stableKey, floor = 0.4f, ms = 240),
            head = false
        )
        // The title and the big case on the left (over everything: it flies to and from the shelf).
        // (523t, owner: the big case a little higher - the title block is tighter and the case's room starts right under it.)
        // (523t, KONKR with real-length names: the title's room is one line + the console line; a name that needs a second line
        // runs over the top of the case's room - its empty margin - so the big case keeps one size and place for every game.)
        val headH = if (compact) 46.dp else 54.dp
        Column(Modifier.zIndex(2f).padding(start = pad, top = pad * 0.8f, bottom = pad * 0.2f).width(leftW).fillMaxHeight()) {
            Spacer(Modifier.height(headH + 2.dp))
            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f).onGloballyPositioned {
                    CoverFlight.stage = it.boundsInWindow()
                    if (it.size.height > 0) bigAspect = it.size.width.toFloat() / it.size.height
                },
                contentAlignment = Alignment.TopCenter
            ) {
                val depth = com.shelf.launcher.data.Features.spineDepth.state.value
                CoverBigCases(c, g, depth, maxHeight, 0.dp, accent)
            }
        }
        CoverDetailsHead(c, g, compact, accent, Modifier.zIndex(3f).padding(start = pad, top = pad * 0.8f).width(leftW))
    }
}

/**
 * 523r: the game's name, and its console and year, over the big case. 523t (owner: "synchronise the title with the case"):
 * the name changes WITH the case flight - the one you left fades out while its case starts back, the new one comes in as its
 * case arrives (read from the flight's own progress while drawing). A category change or no animation: at once, as before.
 */
@Composable
private fun CoverDetailsHead(c: StyleCtx, g: Game, compact: Boolean, accent: Color, modifier: Modifier) {
    var last by remember { mutableStateOf(g) }
    val old = remember(g.stableKey) { last.takeIf { it.stableKey != g.stableKey } }
    androidx.compose.runtime.SideEffect { last = g }
    val key = g.stableKey
    val density = LocalDensity.current
    Box(modifier) {
        if (old != null) CoverHeadText(c, old, compact, accent, Modifier.fillMaxWidth().graphicsLayer {
            alpha = coverHeadOut(CoverFlight.flights[key]?.value ?: 1f)
        })
        CoverHeadText(c, g, compact, accent, Modifier.fillMaxWidth().graphicsLayer {
            val a = coverHeadIn(CoverFlight.flights[key]?.value ?: 1f)
            alpha = a
            translationY = (1f - a) * with(density) { 8.dp.toPx() }
        })
    }
}

/** Pure (523t): the leaving title's opacity at the new case's flight [p] — gone before the cases cross. */
internal fun coverHeadOut(p: Float): Float = (1f - p / 0.35f).coerceIn(0f, 1f)
/** Pure (523t): the new title's opacity at its case's flight [p] — in while the case comes to rest. */
internal fun coverHeadIn(p: Float): Float { val t = ((p - 0.35f) / 0.5f).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

@Composable
private fun CoverHeadText(c: StyleCtx, g: Game, compact: Boolean, accent: Color, modifier: Modifier) {
    val titles = LocalTitleOptions.current
    val year = c.state.metadata[g.stableKey]?.releaseYear
    Column(modifier) {
        FullText(
            titles.display(g.name), color = Color.White, fontSize = if (compact) 22.sp else 28.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, modifier = Modifier.fillMaxWidth(), reserve = false
        )
        Spacer(Modifier.height(2.dp))
        Text(
            titles.console(g.platform) + (year?.let { " · $it" } ?: ""),
            color = accent, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, lineHeight = 18.sp
        )
    }
}

/** The facts of the game you're on: only the ones the game really has (no blank headings, nothing made up). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverDetailsInfo(c: StyleCtx, g: Game, compact: Boolean, accent: Color, modifier: Modifier, head: Boolean = true) {
    val titles = LocalTitleOptions.current
    val meta = c.state.metadata[g.stableKey]
    val story = meta?.summary?.takeIf { it.isNotBlank() }
    BoxWithConstraints(modifier) {
        // Update 487 (D281, Thor 1080p): a two-line title and facts wrapped to two rows used the whole column and the story
        // (lines = 0) was dropped. When the column is short and there is a story, the facts go on one line under the console
        // (achievements beside the console) and the title is a little smaller, so the story keeps ≥ 3 whole lines.
        val tight = !compact && story != null && maxHeight < coverDetailsTightBelow
        Column(Modifier.fillMaxSize(), verticalArrangement = if (head) Arrangement.Center else Arrangement.Top) {
            val ach = c.progress(g)
            // (523r: the title and console stand over the big case - CoverDetailsHead - and this column starts with the facts.)
            if (head) {
            StableFullText(
                titles.display(g.name), color = Color.White, fontSize = if (compact) 24.sp else if (tight) 30.sp else 36.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (tight && ach != null) titles.console(g.platform) + " · " + ach.first + " / " + ach.second + " achievements" else titles.console(g.platform),
                color = accent, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp, lineHeight = 18.sp
            )
            Spacer(Modifier.height(if (compact) 8.dp else if (tight) 8.dp else 14.dp))
            }
            // Label / value pairs, each only when there is a value.
            val facts = buildList {
                if (head) meta?.releaseYear?.let { add("Released" to it.toString()) }
                meta?.developer?.takeIf { it.isNotBlank() }?.let { add("Developer" to it) }
                if (!tight) meta?.publisher?.takeIf { it.isNotBlank() && it != meta.developer }?.let { add("Publisher" to it) }
                meta?.genres?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.let { add("Genre" to it.take(if (tight) 2 else 3).joinToString(", ")) }
                if (!tight) c.playtime(g)?.let { add("Played" to stylePlaytime(it, true)) }
                if (!tight || !head) ach?.let { (e, t) -> add("Achievements" to "$e / $t") }
            }
            if (tight) {
                if (facts.isNotEmpty()) Text(
                    facts.joinToString("  ·  ") { it.second }, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp,
                    fontWeight = FontWeight.Medium, lineHeight = 19.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis   // qa-ok: TRUNCATE (all facts on Details)
                )
            } else FlowRow(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, value) in facts) Column(Modifier.widthIn(max = 260.dp)) {
                    Text(label.uppercase(), color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                    Text(value, color = Color.White.copy(alpha = 0.92f), fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp)
                }
            }
            if (story != null) {
                Spacer(Modifier.height(if (compact || tight) 8.dp else 14.dp))
                // (Update 432: only as tall as the room left. Update 487 (D263, owner-approved): whole-line pages with a page
                // count instead of the endless roll — never a half line, nothing moving while you read.)
                val small = compact || tight
                Box(Modifier.weight(1f, fill = false)) {
                    PagedStory(
                        story, Color.White.copy(alpha = 0.8f), if (small) 13.sp else 14.sp, if (small) 18.sp else 20.sp,
                        maxLines = if (compact) 3 else 5, modifier = Modifier.fillMaxWidth(), auto = true
                    )
                }
            }
        }
    }
}

/** Update 487: below this column height (with a story) Cover details puts its facts on one line (D281). */
internal val coverDetailsTightBelow = 270.dp

/** Batch 523r: how far the shelf's cases are turned (front to the right, spine and part of the front showing). */
internal const val COVER_ROW_YAW = 32f

/**
 * Pure (523r): where a shelf case [d] places from the game you're on stands, in slots from that game's own spot. The game
 * you're on is out in the big place, so its spot takes no room: the one before it and the one after it stand one slot apart.
 */
internal fun coverRowX(d: Float): Float = when {
    d >= 1f -> d - 0.5f
    d <= -1f -> d + 0.5f
    else -> d * 0.5f
}

/** Pure (523r): how wide a case of shape [aspect] (w / h) looks at [COVER_ROW_YAW], as a share of its height (front + spine). */
internal fun coverRowSeenWidth(aspect: Float): Float =
    aspect * kotlin.math.cos(Math.toRadians(COVER_ROW_YAW.toDouble())).toFloat() + 0.18f * kotlin.math.sin(Math.toRadians(COVER_ROW_YAW.toDouble())).toFloat()

/** Pure (523r): Endless — the first place of the [n] places a game can take (one before the game you're on, the rest after). */
internal fun coverRowLead(n: Int): Float = if (n >= 3) -1.5f else -0.5f

/**
 * Pure (523r): the place of game [r] with the row at [pos]. Endless: each game exactly once, in [lead, lead + n) — with few
 * games they stand side by side after the one you're on, never repeated to fill the shelf.
 */
internal fun coverRowD(r: Int, pos: Float, n: Int, loop: Boolean): Float {
    if (!loop || n <= 0) return r - pos
    val lead = coverRowLead(n)
    val x = (r - pos - lead).mod(n.toFloat())
    return x + lead
}

/**
 * Pure (523r): a case's opacity at place [d]: it fades out past the one before the game you're on (left end) and, Endless,
 * as it nears the far end of the loop, so a game going round from one end to the other never jumps while seen.
 */
internal fun coverRowAlpha(d: Float, n: Int, loop: Boolean): Float {
    if (!loop) return ((d + 1.5f) / 0.5f).coerceIn(0f, 1f)
    val lead = coverRowLead(n)
    val inL = ((d - lead) / 0.5f).coerceIn(0f, 1f)
    val inR = ((lead + n - d) / 0.5f).coerceIn(0f, 1f)
    return minOf(inL, inR)
}

/**
 * Batch 523r: the shelf at the lower right — the other games as smaller 3D cases (their console's own case and art, Case3D),
 * turned [COVER_ROW_YAW] so the spine and part of the front show, packed side by side with a little overlap, gliding as one
 * row ([rememberGlidePos]). Each case stands in a box of the big case's own shape ([bigAspect]), so the case flying between
 * a slot and the big place only scales and moves: no jump at either end.
 */
@Composable
private fun CoverCaseShelf(c: StyleCtx, bigAspect: Float, accent: Color, modifier: Modifier) {
    val count = c.games.size
    val keys = remember(c.games) { gameKeys(c.games) }
    val loop = loopRowOn(c)
    val vFocus = rememberLoopFocus(c)
    val pos = rememberGlidePos(vFocus, count, keys, loop)
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val latestFocus by rememberUpdatedState(c.focus)
    val latestCount by rememberUpdatedState(count)
    val latestLoop by rememberUpdatedState(loop)
    val density = LocalDensity.current
    val lo by remember { derivedStateOf { floor(pos.value).toInt() } }
    val depth = com.shelf.launcher.data.Features.spineDepth.state.value
    val lite = LocalLiteEffects.current
    val realFront = c.type == com.shelf.launcher.data.CardArtType.CASE || c.type == com.shelf.launcher.data.CardArtType.CASE_REAL
    BoxWithConstraints(modifier.onGloballyPositioned { CoverFlight.shelf = it.boundsInWindow() }) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val boardPx = hPx * 0.06f
        val slotH = hPx * 0.97f - boardPx
        val boxW = slotH * bigAspect
        // The cases' usual shape here (the middle one of the games near you: a list of one console has one), so a row of wide
        // jewel cases stands as close as a row of tall DVD cases - each a fifth over the next (523r, GT78 first look: PS1
        // cases hid most of each other).
        val aspect = remember(c.games, c.focus / 16) {
            val near = (0 until minOf(count, 15)).map { k -> c.games[(c.focus + k - 7).mod(count.coerceAtLeast(1))] }
            near.map { case3dAspect(it, false) }.sorted().let { it.getOrNull(it.size / 2) ?: 0.71f }
        }
        val caseH = minOf(slotH, boxW / aspect) * bigCaseFill(depth)
        val seenW = caseH * coverRowSeenWidth(aspect)
        // (Layout options › Game spacing: 100 % = a fifth of each case under the next.)
        val slotW = seenW * 0.80f * (0.6f + 0.4f * Features.cdSpacing.state.value / 100f)
        val margin = seenW * 0.55f
        // How many slots the shelf shows after the game you're on.
        val after = ((wPx - margin) / slotW).toInt() + 2
        // The shelf board.
        val board = themeDark(Color(0xFF05070B), 0.5f)
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(with(density) { boardPx.toDp() })
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), board)))
        )
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                var acc = 0f
                val stepPx = 44.dp.toPx()
                detectHorizontalDragGestures(onDragEnd = { acc = 0f }, onDragCancel = { acc = 0f }) { change, dx ->
                    change.consume()
                    acc += dx
                    val n = latestCount
                    if (n > 0) {
                        var target = latestFocus
                        val wrap = latestLoop
                        while (acc <= -stepPx) { acc += stepPx; target = if (wrap) (target + 1).mod(n) else (target + 1).coerceAtMost(n - 1) }
                        while (acc >= stepPx) { acc -= stepPx; target = if (wrap) (target - 1).mod(n) else (target - 1).coerceAtLeast(0) }
                        if (target != latestFocus) {
                            if (StyleNav.zone != StyleNav.Zone.GAMES) StyleNav.leave()
                            select(target)
                        }
                    }
                }
            }
        ) {
            // The games near the row's place (each game once; Endless: its one place in the loop).
            val shown = (0 until count).filter { r -> coverRowD(r, lo.toFloat(), count, loop).let { it >= -2.6f && it <= after + 1f } }
            for (real in shown) key(keys[real]) {
                val gm = c.games[real]
                Box(
                    Modifier
                        .size(with(density) { boxW.toDp() }, with(density) { slotH.toDp() })
                        .align(Alignment.TopStart)
                        .zIndex(coverRowD(real, lo.toFloat(), count, loop))
                        .graphicsLayer {
                            val d = coverRowD(real, pos.value, count, loop)
                            val cx = margin + slotW * (coverRowX(d) + 1f)
                            translationX = cx - boxW / 2f
                            translationY = hPx - boardPx - slotH
                            // (The slot is empty while its case is out in the big place or on its way.)
                            CoverFlight.shelf?.let { sh -> CoverFlight.spines[gm.stableKey] = Rect(sh.left + translationX, sh.top + translationY, sh.left + translationX + boxW, sh.top + translationY + slotH) }
                            alpha = coverRowAlpha(d, count, loop) * (1f - CoverFlight.hide(gm.stableKey)) *
                                ((wPx + boxW * 0.2f - (cx + slotW * 0.5f)) / (slotW * 0.8f)).coerceIn(0f, 1f) // (the right edge: out of room)
                        }
                        .pointerInput(real) { detectTapGestures { if (StyleNav.zone != StyleNav.Zone.GAMES) StyleNav.leave(); if (real == latestFocus) open() else select(real) } }
                ) {
                    Case3D(
                        gm, c.cover(gm), c.state.physicalMedia[gm.stableKey], COVER_ROW_YAW, case3dAspect(gm, false), Modifier.fillMaxSize(),
                        reflect = !lite, pitch = bigCasePitch(depth), realFront = realFront, fill = bigCaseFill(depth),
                        // (Further along the shelf, a little darker: depth.)
                        shadeOf = { (coverRowD(real, pos.value, count, loop) - 1f).coerceIn(0f, 4f) * 0.05f }
                    )
                }
            }
        }
    }
}

@Composable
internal fun CoverDetailsSettings() {
    MutedNote(
        "The game you're on as a big 3D box at the upper left (the console's own case, or its real scan) under its name, its year, studio, genre and " +
            "story beside it, and the other games as smaller cases on a shelf at the lower right, turned so their spines and fronts show. " +
            "◀ ▶ move along the shelf (the next case comes out into the big place), A or a tap opens the game, swipe the shelf to move. " +
            "The box faces you or stands turned like the others (Game look › The one you're on)."
    )
    Spacer(Modifier.height(8.dp))
    // Update 487 (K1): this layout's own room between the spines (100 % = packed, as before).
    PercentSlider("Game spacing", Features.cdSpacing.state.value, { Features.cdSpacing.value = it.coerceIn(50, 250) }, min = 50, max = 250, valueText = { "$it%" })
}

/**
 * Update 404 (D95): the big case comes OUT of the shelf slot of the game you're on and the case that was out goes back into its
 * own slot, both at the same time (before, the new case simply stood there: a spawn). One object per game, a slot's spine is
 * hidden only while its case is away; the flight reads the slot's real window bounds each frame.
 */
internal object CoverFlight {
    /** The spines' last bounds in the window (written by the shelf, read by the flying case). */
    val spines = HashMap<String, Rect>()
    /** The big case's stage (window bounds). */
    var stage: Rect? = null
    var shelf: Rect? = null
    /** Game key -> its flight (0 = in its slot, 1 = out in the big place); present only while it moves. */
    val flights = androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.runtime.State<Float>>()
    const val MS = 380
    /** Starts moving at once (no slow-in pause), settles softly. */
    val EASE = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.6f, 0.2f, 1f)
    /** How hidden the slot's own spine is (0 = drawn, 1 = empty slot): only while its case is clearly away from it. */
    fun hide(key: String): Float {
        val p = flights[key]?.value ?: return 0f
        // (Batch 523, owner: "physically connected pickup and return — no duplicate copies, the original hidden while lifted":
        // the slot is empty the moment its case leaves it and until the same case is back in it; there is never a spine and
        // a case of the same game on screen together — the case itself turns from its spine to its front on the way out.)
        return if (p > 0.001f) 1f else 0f
    }
    /** Batch 523: the flying case's turn — spine-on in its slot ([SPINE_YAW]), round to [to] in the first 60 % of the way. */
    fun yaw(p: Float, to: Float): Float {
        val t = (p / 0.6f).coerceIn(0f, 1f); val e = t * t * (3f - 2f * t)
        return SPINE_YAW + (to - SPINE_YAW) * e
    }
    /** 523r: the shelf's cases stand turned [COVER_ROW_YAW], so a case leaves / reaches its slot at that turn. */
    const val SPINE_YAW = COVER_ROW_YAW
    /** Update 405: ONE ramp for both the case (alpha) and its slot (1 - alpha): the spine turns into the case in place, the slot stays empty while the case is out. */
    fun appear(p: Float): Float { val t = (p / 0.3f).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
    /** Pure: the case's scale and centre (as a share 0..1 between slot and big place) for flight [p]. */
    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}

private class CoverOut(val game: Game, from: Float) { val p = Animatable(from) }
private class CoverHold { var cur: CoverOut? = null; val leaving = ArrayList<CoverOut>() }

@Composable
private fun CoverBigCases(c: StyleCtx, g: Game, depth: com.shelf.launcher.data.Features.SpineDepth, stageH: Dp, shelfH: Dp, accent: Color) {
    val animate = rememberAnimationsEnabled() && !LocalLiteEffects.current
    val hold = remember { CoverHold() }
    var tick by remember { mutableStateOf(0) }
    if (hold.cur?.game?.stableKey != g.stableKey) {
        val old = hold.cur
        val quick = !animate || old == null || CategorySlide.justChanged()
        val back = hold.leaving.firstOrNull { it.game.stableKey == g.stableKey }
        if (back != null) { hold.leaving.remove(back); hold.cur = back }
        else hold.cur = CoverOut(g, if (quick) 1f else 0f)
        // (405: registered at once, in this very composition, so the slot is never drawn for a frame while its case is out.)
        CoverFlight.flights[hold.cur!!.game.stableKey] = hold.cur!!.p.asState()
        if (old != null && quick) CoverFlight.flights.remove(old.game.stableKey)
        if (old != null && !quick) hold.leaving.add(old)
        while (hold.leaving.size > 2) hold.leaving.removeAt(0).also { CoverFlight.flights.remove(it.game.stableKey) }
        if (quick) { hold.leaving.forEach { CoverFlight.flights.remove(it.game.stableKey) }; hold.leaving.clear() }
    }
    val cur = hold.cur!!
    DisposableEffect(Unit) { onDispose { CoverFlight.flights.clear() } }
    LaunchedEffect(cur) {
        CoverFlight.flights[cur.game.stableKey] = cur.p.asState()
        try {
            cur.p.animateTo(1f, tween((CoverFlight.MS * (1f - cur.p.value)).toInt().coerceAtLeast(1), easing = CoverFlight.EASE))
        } finally { }
    }
    for (o in hold.leaving.toList()) {
        LaunchedEffect(o) {
            CoverFlight.flights[o.game.stableKey] = o.p.asState()
            try {
                o.p.animateTo(0f, tween((CoverFlight.MS * o.p.value).toInt().coerceAtLeast(1), easing = CoverFlight.EASE))
                hold.leaving.remove(o); tick++
            } finally { if (o.p.value <= 0f) CoverFlight.flights.remove(o.game.stableKey) }
        }
    }
    val density = LocalDensity.current
    val all = hold.leaving.toList() + cur
    @Suppress("UNUSED_EXPRESSION") tick
    for (o in all) key(o.game.stableKey) {
        val gm = o.game
        Box(
            Modifier.fillMaxWidth().requiredHeight(stageH + shelfH * 0.12f).zIndex(if (o === cur) 1f else 0f)
                .graphicsLayer {
                    val p = o.p.value
                    val st = CoverFlight.stage
                    val sp = CoverFlight.spines[gm.stableKey]
                    if (p < 1f && st != null && sp != null && size.width > 0f) {
                        val s0 = (sp.height / size.height).coerceIn(0.05f, 1f) // (523r: the slot's box has the big box's shape)
                        val s = CoverFlight.lerp(s0, 1f, p)
                        val cx = CoverFlight.lerp(sp.center.x, st.left + size.width / 2f, p)
                        val cy = CoverFlight.lerp(sp.center.y, st.top + size.height / 2f, p) - kotlin.math.sin(Math.PI.toFloat() * p) * sp.height * 0.18f // (405: lifts up out of the slot, then forward; 523r: a gentle arc)
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = s; scaleY = s
                        translationX = cx - s * size.width / 2f - st.left
                        translationY = cy - s * size.height / 2f - st.top
                    }
                    // (Batch 523: the same object all the way - whole from the first frame, no fade from / into a second copy.)
                    alpha = if (p > 0.001f) 1f else 0f
                }
        ) {
            val restYaw = if (PickedFacing.faces) bigCaseYaw(depth) else PickedFacing.TURN
            Case3D(
                gm, c.cover(gm), c.state.physicalMedia[gm.stableKey], restYaw,
                case3dAspect(gm, false), Modifier.fillMaxSize(), reflect = !LocalLiteEffects.current, pitch = bigCasePitch(depth),
                yawOf = { CoverFlight.yaw(o.p.value, restYaw) },
                frame = false, frameColor = accent,
                realFront = c.type == com.shelf.launcher.data.CardArtType.CASE || c.type == com.shelf.launcher.data.CardArtType.CASE_REAL,
                fill = bigCaseFill(depth)
            )
        }
    }
}
