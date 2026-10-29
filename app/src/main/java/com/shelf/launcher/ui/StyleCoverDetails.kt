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
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth; val h = maxHeight
        val compact = h < 380.dp
        val shelfH = (h * (if (compact) 0.32f else 0.33f)).coerceIn(84.dp, 210.dp)
        val shelfPad = if (compact) 6.dp else 12.dp
        val stageH = (h - shelfH - shelfPad).coerceAtLeast(120.dp)

        // The stage: the big box, then the facts.
        Row(Modifier.fillMaxWidth().height(stageH).padding(start = 28.dp, end = 28.dp, top = if (compact) 4.dp else 26.dp)) {
            val boxW = (w * 0.48f).coerceAtMost(600.dp)
            // Update 392: the case sits at the top of the stage (its reflection runs on into the room under it) instead of floating in
            // the middle of a tall box with an empty band above it.
            Box(Modifier.width(boxW).fillMaxHeight().onGloballyPositioned { CoverFlight.stage = it.boundsInWindow() }, contentAlignment = Alignment.TopCenter) {
                val depth = com.shelf.launcher.data.Features.spineDepth.state.value
                CoverBigCases(c, g, depth, stageH, shelfH, accent)
            }
            Spacer(Modifier.width(if (compact) 18.dp else 32.dp))
            CoverDetailsInfo(c, g, compact, accent, Modifier.weight(1f).fillMaxHeight().riseOnChange(g.stableKey, floor = 0.4f, ms = 240))
        }

        // The shelf of spines along the bottom.
        CoverShelf(c, shelfH, accent, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(shelfH * 1.16f))
    }
}

/** The facts of the game you're on: only the ones the game really has (no blank headings, nothing made up). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverDetailsInfo(c: StyleCtx, g: Game, compact: Boolean, accent: Color, modifier: Modifier) {
    val titles = LocalTitleOptions.current
    val meta = c.state.metadata[g.stableKey]
    Column(modifier, verticalArrangement = Arrangement.Center) {
        StableFullText(
            titles.display(g.name), color = Color.White, fontSize = if (compact) 24.sp else 36.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text(
            titles.console(g.platform), color = accent, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp, lineHeight = 18.sp
        )
        Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
        // Label / value pairs, each only when there is a value.
        val facts = buildList {
            meta?.releaseYear?.let { add("Released" to it.toString()) }
            meta?.developer?.takeIf { it.isNotBlank() }?.let { add("Developer" to it) }
            meta?.publisher?.takeIf { it.isNotBlank() && it != meta.developer }?.let { add("Publisher" to it) }
            meta?.genres?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.let { add("Genre" to it.take(3).joinToString(", ")) }
            c.playtime(g)?.let { add("Played" to stylePlaytime(it, true)) }
            c.progress(g)?.let { (e, t) -> add("Achievements" to "$e / $t") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, value) in facts) Column(Modifier.widthIn(max = 260.dp)) {
                Text(label.uppercase(), color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                Text(value, color = Color.White.copy(alpha = 0.92f), fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp)
            }
        }
        val story = meta?.summary?.takeIf { it.isNotBlank() }
        if (story != null) {
            Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
            RollingStory(story, Color.White.copy(alpha = 0.8f), if (compact) 12.sp else 14.sp, if (compact) 17.sp else 20.sp, if (compact) 54.dp else 104.dp)
        }
    }
}

/** What one spine on the shelf needs: its place's game, width and height in px. */
private class ShelfSpine(val w: Float, val h: Float)

/**
 * The shelf: every game as its console's upright spine, packed edge to edge, gliding as one row under the middle (the place
 * is [rememberGlidePos]); each spine keeps its console's width and height, the one you're on lifts and is outlined.
 */
@Composable
private fun CoverShelf(c: StyleCtx, shelfH: Dp, accent: Color, modifier: Modifier) {
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
    val posFn: () -> Float = remember(pos) { { pos.value } }
    BoxWithConstraints(modifier.clipToBounds().onGloballyPositioned { CoverFlight.shelf = it.boundsInWindow() }) {
        val wPx = with(density) { maxWidth.toPx() }
        val spineHmax = with(density) { (shelfH * 0.80f).toPx() }
        val gap = with(density) { SHELF_GAP_DP.dp.toPx() }
        val minW = with(density) { 12.dp.toPx() }
        val maxW = with(density) { 56.dp.toPx() }
        // A spine's size from its console: the real width / height of the case it came in.
        fun sizeOf(real: Int): ShelfSpine {
            val gm = c.games.getOrNull(real) ?: return ShelfSpine(minW, spineHmax)
            val hh = spineHmax * (((realCaseMm(gm.platform)?.second ?: 190f) / 190f).coerceIn(0.55f, 1f))
            return ShelfSpine((hh * CaseThickness.orElse(gm, shelfSpineAspect(gm.platform))).coerceIn(with(density) { CaseThickness.MIN_SPINE_DP.dp.toPx() }, maxW), hh)
        }
        val side = ((wPx / 2f) / (minW + gap)).toInt().plus(2).coerceIn(4, SHELF_MAX_SIDE)
        // Where each place's middle stands against the middle of the one at [lo] (px), worked out when [lo] changes only.
        val rel = remember(lo, count, loop, side, wPx, spineHmax, c.games) {
            val a = FloatArray(side * 2 + 3)
            fun real(i: Int) = if (loop) i.mod(count) else i.coerceIn(0, (count - 1).coerceAtLeast(0))
            // index j → place lo + (j - side - 1); the middle of lo is a[side + 1]
            val mid = side + 1
            a[mid] = 0f
            for (j in mid + 1 until a.size) a[j] = a[j - 1] + sizeOf(real(lo + (j - 1 - mid))).w / 2f + gap + sizeOf(real(lo + (j - mid))).w / 2f
            for (j in mid - 1 downTo 0) a[j] = a[j + 1] - sizeOf(real(lo + (j + 1 - mid))).w / 2f - gap - sizeOf(real(lo + (j - mid))).w / 2f
            a
        }
        // The shelf board.
        val board = themeDark(Color(0xFF05070B), 0.5f)
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(shelfH * 0.1f)
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
            val first = if (loop) lo - side else (lo - side).coerceAtLeast(0)
            val last = if (loop) lo + side + 1 else (lo + side + 1).coerceAtMost(count - 1)
            val c0 = wPx / 2f
            // (The places picked before the loop, as Case stacks does: a `continue` in it would give each pass its own group.)
            for (i in (first..last).filter { i -> !(loop && !drawnOnce(i - lo, count)) }) {
                val real = if (loop) i.mod(count) else i
                key(if (loop) "${keys[real]}#${Math.floorDiv(i, count)}" else keys[real]) {
                    val sp = sizeOf(real)
                    val gm = c.games[real]
                    val near = i == lo || i == lo + 1
                    val j = (i - lo) + side + 1
                    Box(
                        Modifier
                            .size(with(density) { sp.w.toDp() }, with(density) { sp.h.toDp() })
                            .align(Alignment.BottomStart)
                            .zIndex(if (near) 10f else 0f)
                            .graphicsLayer {
                                val f = posFn() - lo
                                val cam = f * (rel[(side + 2).coerceAtMost(rel.lastIndex)] - rel[side + 1])
                                translationX = c0 + rel[j.coerceIn(0, rel.lastIndex)] - cam - sp.w / 2f
                                // Lifts as it comes under the middle, back down as it leaves.
                                val sel = (1f - abs(i - posFn())).coerceIn(0f, 1f)
                                translationY = -(shelfH.toPx() * 0.1f) - sel * 10.dp.toPx()
                                val s = 1f + 0.05f * sel
                                scaleX = s; scaleY = s
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                                // (404: this slot's place in the window, for the case flying out of / back into it.)
                                CoverFlight.shelf?.let { sh -> CoverFlight.spines[gm.stableKey] = Rect(sh.left + translationX, sh.bottom + translationY - sp.h, sh.left + translationX + sp.w, sh.bottom + translationY) }
                                // The ends of the shelf fade out instead of stopping hard.
                                val fromMid = abs(translationX + sp.w / 2f - c0)
                                alpha = ((wPx / 2f - fromMid) / (wPx * 0.08f)).coerceIn(0f, 1f) * (1f - CoverFlight.hide(gm.stableKey)) // (404: the slot is empty while its case is out on the way)
                            }
                            .drawBehind {
                                val cam = (posFn() - lo) * (rel[(side + 2).coerceAtMost(rel.lastIndex)] - rel[side + 1])
                                drawCaseTop(com.shelf.launcher.data.Features.spineDepth.state.value, c0 - (c0 + rel[j.coerceIn(0, rel.lastIndex)] - cam - sp.w / 2f))
                            }
                            .pointerInput(real) { detectTapGestures { if (StyleNav.zone != StyleNav.Zone.GAMES) StyleNav.leave(); if (real == latestFocus) open() else select(real) } }
                    ) {
                        GameSpine(gm, c.state.physicalMedia[gm.stableKey], Modifier.fillMaxSize(), realFirst = true)
                        // The outline of the one you're on, fading with its lift.
                        Box(
                            Modifier.fillMaxSize()
                                .graphicsLayer { alpha = (1f - abs(i - posFn())).coerceIn(0f, 1f) }
                                .border(2.dp, accent, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CoverDetailsSettings() {
    MutedNote(
        "The game you're on as a big 3D box on the left (the console's own case, or its real scan), its year, studio, genre and " +
            "story beside it, and a shelf of upright spines along the bottom that glides as one row. ◀ ▶ move along the shelf, A or a tap opens the game, " +
            "swipe the shelf to move. The box faces you or stands turned like the others (Game look › The one you're on)."
    )
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
    const val MS = 340
    /** How hidden the slot's own spine is (0 = drawn, 1 = empty slot): only while its case is clearly away from it. */
    fun hide(key: String): Float {
        val p = flights[key]?.value ?: return 0f
        return (p * 6f).coerceIn(0f, 1f) * ((1f - p) * 6f).coerceIn(0f, 1f)
    }
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
        if (old != null && !quick) hold.leaving.add(old)
        while (hold.leaving.size > 2) hold.leaving.removeAt(0).also { CoverFlight.flights.remove(it.game.stableKey) }
        if (quick) { hold.leaving.clear() }
    }
    val cur = hold.cur!!
    LaunchedEffect(cur) {
        CoverFlight.flights[cur.game.stableKey] = cur.p.asState()
        try {
            cur.p.animateTo(1f, tween((CoverFlight.MS * (1f - cur.p.value)).toInt().coerceAtLeast(1), easing = FastOutSlowInEasing))
        } finally { if (cur.p.value >= 1f) CoverFlight.flights.remove(cur.game.stableKey) }
    }
    for (o in hold.leaving.toList()) {
        LaunchedEffect(o) {
            CoverFlight.flights[o.game.stableKey] = o.p.asState()
            try {
                o.p.animateTo(0f, tween((CoverFlight.MS * o.p.value).toInt().coerceAtLeast(1), easing = FastOutSlowInEasing))
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
                        val s0 = (sp.height / size.height / 0.85f).coerceIn(0.05f, 1f)
                        val s = CoverFlight.lerp(s0, 1f, p)
                        val cx = CoverFlight.lerp(sp.center.x, st.left + size.width / 2f, p)
                        val cy = CoverFlight.lerp(sp.center.y, st.top + size.height / 2f, p)
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = s; scaleY = s
                        translationX = cx - s * size.width / 2f - st.left
                        translationY = cy - s * size.height / 2f - st.top
                    }
                    alpha = (p * 4f).coerceIn(0f, 1f)
                }
        ) {
            Case3D(
                gm, c.cover(gm), c.state.physicalMedia[gm.stableKey], if (PickedFacing.faces) bigCaseYaw(depth) else PickedFacing.TURN,
                case3dAspect(gm, false), Modifier.fillMaxSize(), reflect = !LocalLiteEffects.current, pitch = bigCasePitch(depth),
                frame = false, frameColor = accent,
                realFront = c.type == com.shelf.launcher.data.CardArtType.CASE || c.type == com.shelf.launcher.data.CardArtType.CASE_REAL,
                fill = bigCaseFill(depth)
            )
        }
    }
}
