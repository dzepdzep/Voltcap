package com.shelf.launcher.ui

import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.togetherWith
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import coil.compose.AsyncImage
import com.shelf.launcher.data.HomeLayout
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect

/**
 * v0.6.66 (D142): the Twin layouts' window — one window folded open over a hinge, like the two screens it's made for.
 *
 * Before: the top half held only the art; the name, console, facts AND three lines of keys shared the bottom half, so on the
 * GT78 (960×640) the facts got ~2½ rows and the last one was cut in half above the keys; on the light Twin desk the window was
 * a dark slab that had nothing to do with the desk's squared paper.
 *
 * Now:
 * - top half = the "top screen": the picture (pinned to the desk's squared paper on Twin desk) with the name strip beside it —
 *   the desk's own name strip (accent bar, name that wraps then shrinks, console · year);
 * - bottom half = the "touch screen": the facts, then the keys. The keys are measured first (their wrapped height is reserved),
 *   the facts get what's left — never less than one whole row — and show WHOLE rows only ([SnapPaging]); when more is left
 *   they page on by themselves, a row boundary at a time, and come back to the top. Details shows everything at once;
 * - Twin desk: light paper, white touch half, pill keys like the desk's round buttons (lit = accent ring), a pale veil
 *   (GameWindow). Twin icons / Twin showcase keep their dark hinge window.
 */
@Composable
internal fun TwinHinge(ctx: WinSceneCtx) {
    val desk = ctx.state.appearance.homeLayout == HomeLayout.TWIN_DESK
    val a = ctx.accent
    val w = minOf(ctx.maxW, 600.dp)
    val h = minOf(ctx.maxH, 520.dp)
    val short = h < 440.dp
    // The top screen's share; the touch half keeps at least 190 dp (keys + one row of facts on the smallest screens).
    val topMain = (h * (if (short) 0.32f else 0.38f)).coerceIn(92.dp, 220.dp).coerceAtMost((h - 200.dp).coerceAtLeast(72.dp))
    // (529l, Twin family native pages: Details / Achievements / Options open on the touch screen, as a two-screen handheld
    // changes its lower screen. The top screen keeps the picture and the name, folds down to a strip that says which page is
    // open, and gives the touch screen the room; back on Info it comes up again. Immediate with animations off.)
    val anim = rememberAnimationsEnabled()
    val onPage = ctx.page != WinPage.MAIN
    val topPage = twinTopPageHeight(h, topMain)
    val topH by androidx.compose.animation.core.animateDpAsState(
        if (onPage) topPage else topMain,
        if (anim) tween(NATIVE_PAGE_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing) else androidx.compose.animation.core.snap(),
        label = "twinTop"
    )
    val ink = if (desk) DeskInk else Color.White
    val muted = if (desk) DeskMuted else Color.White.copy(alpha = 0.6f)
    val edge = if (desk) DeskEdge else Color.White.copy(alpha = 0.18f)
    val topShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
    val bottomShape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
    // (The window is as tall as its content needs, up to the screen: few facts leave no empty middle.)
    // Update 507 (owner review of 506: "improve hinge-inspired motion"): a clamshell opening — the hinge comes first, then the
    // top screen swings up on its lower edge and the touch screen swings down on its upper edge, both from the hinge; closing
    // folds them shut onto it. (Was one 40° tilt of the whole window.)
    val unfold = { sceneEase(twinUnfold(ctx.reveal())) }
    Column(Modifier.width(w).heightIn(max = h).graphicsLayer {
        alpha = (ctx.reveal() * 3f).coerceIn(0f, 1f)
        if (desk) { shadowElevation = 12.dp.toPx() * unfold(); shape = RoundedCornerShape(14.dp) }
    }) {
        // ── Top screen: the picture and the game's name strip.
        Row(
            Modifier.fillMaxWidth().height(topH).graphicsLayer {
                rotationX = (1f - unfold()) * -88f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                cameraDistance = 12f * density
                // (Update 489, D285: turned text drawn flat — see flatTextFor3d.)
                flatTextFor3d()
            }.clip(topShape)
                .then(if (desk) Modifier.deskPaper().border(1.dp, edge, topShape) else Modifier.background(Color(0xFF101318)))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HingePicture(ctx, desk, Modifier.fillMaxHeight().widthIn(max = w * 0.48f), label = !onPage)
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier.weight(1f).fillMaxHeight().then(
                    if (desk) Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.88f)).border(1.dp, edge, RoundedCornerShape(10.dp))
                    else Modifier
                ).padding(horizontal = if (desk) 10.dp else 0.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(4.dp).fillMaxHeight(0.6f).clip(RoundedCornerShape(2.dp)).background(a))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    if (onPage) {
                        // (529l: on a page the strip says which one is open, over the name on one line - it shrinks to a
                        // readable size, then slides; console and year are on the Details page.)
                        Text(winPageTitle(ctx.page).uppercase(), color = if (desk) muted else a, fontSize = 10.sp, lineHeight = 12.sp,
                            fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, maxLines = 1)
                        StableFullText(ctx.name, color = ink, fontSize = 15.sp, maxLines = 1, fontWeight = FontWeight.Bold, reserve = false)
                    } else {
                        // Wraps, then shrinks to a readable size (CLAUDE.md rule 11) — the box's height is the limit.
                        FitText(ctx.name, ink, maxSize = if (short) 18.sp else 22.sp, minSize = 11.sp, maxLines = 3,
                            modifier = Modifier.weight(1f, fill = false), fontWeight = FontWeight.Bold, lineHeightRatio = 1.14f)
                        Text(listOfNotNull(ctx.console, ctx.year).joinToString(" · "), color = if (desk) muted else a,
                            fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        // ── The hinge.
        Box(Modifier.fillMaxWidth().height(10.dp).graphicsLayer {
            scaleX = 0.3f + 0.7f * sceneEase((ctx.reveal() / 0.3f).coerceIn(0f, 1f))
        }.background(
            if (desk) Brush.verticalGradient(listOf(Color(0xFFB9C2CC), Color(0xFFE9EDF1), Color(0xFFB9C2CC)))
            else Brush.verticalGradient(listOf(Color(0xFF2A2F37), Color(0xFF555C67), Color(0xFF2A2F37)))
        ))
        // ── Touch screen: facts (what's left), then the keys (reserved first).
        Column(
            Modifier.fillMaxWidth().heightIn(min = 150.dp).graphicsLayer {
                rotationX = (1f - unfold()) * 88f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                cameraDistance = 12f * density
                flatTextFor3d()
            }.clip(bottomShape)
                .then(if (desk) Modifier.background(Color(0xFFFBFCFD)).border(1.dp, edge, bottomShape) else Modifier.background(Color(0xFF1A1E25)))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            val twinKey: @Composable (WinAction) -> Unit = { k ->
                if (desk) SceneKey(k, Color.White, a.copy(alpha = 0.18f), DeskInk, DeskInk, DeskEdge, a, RoundedCornerShape(50), height = 30.dp)
                else SceneKey(k, Color.White.copy(alpha = 0.08f), a, Color.White, if (a.red + a.green + a.blue > 1.8f) Color.Black else Color.White,
                    Color.White.copy(alpha = 0.18f), Color.White, RoundedCornerShape(12.dp), height = 30.dp)
            }
            TwinTouchPages(ctx, desk, Modifier.weight(1f, fill = false), twinKey) { live, keys ->
                // (The keys are measured first — not weighted — so their wrapped lines are always whole; the facts get the rest.)
                Column(Modifier.heightIn(min = 130.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.weight(1f, fill = false).fillMaxWidth().padding(bottom = 8.dp)) { HingeFacts(ctx, muted, ink) }
                    KeyRow(keys) { k -> twinKey(k) }
                }
            }
        }
    }
}

/** 529l: the top screen's height while a page is open - a strip with the picture, the page's name and the game's. Pure. */
internal fun twinTopPageHeight(h: Dp, topMain: Dp): Dp = (h * 0.22f).coerceIn(76.dp, 104.dp).coerceAtMost(topMain)

/**
 * 529l: the Twin family's touch screen - Info ([main]) or a page (Details / Achievements / Options) in a well of the screen's
 * own ground, the window's keys under it. A page change swaps the touch screen sideways (forward: in from the right), the
 * leaving one gone in 90 ms so one screen reads per frame; L1 / R1 walk the pages; with animations off it is immediate.
 * [main] gets `live` = false for the copy on its way out (WinAction.inert).
 */
@Composable
internal fun TwinTouchPages(
    ctx: WinSceneCtx, desk: Boolean, modifier: Modifier, key: @Composable (WinAction) -> Unit,
    main: @Composable (live: Boolean, keys: List<WinAction>) -> Unit
) {
    val anim = rememberAnimationsEnabled()
    val tabs = SHELF_TABS.filter { it in ctx.pages }
    val keysOf = rememberPageKeys(ctx)
    androidx.compose.animation.AnimatedContent(
        targetState = ctx.page,
        transitionSpec = {
            val dir = pageDirection(tabs, initialState, targetState)
            if (!anim) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
            else (androidx.compose.animation.slideInHorizontally(tween(NATIVE_PAGE_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { w -> w * dir / 3 } +
                androidx.compose.animation.fadeIn(tween(150, delayMillis = 60))) togetherWith
                (androidx.compose.animation.slideOutHorizontally(tween(NATIVE_PAGE_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { w -> -w * dir / 10 } +
                    androidx.compose.animation.fadeOut(tween(90)))
        },
        contentKey = { it },
        modifier = modifier.fillMaxWidth().onPreviewKeyEvent { e ->
            if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val step = when (e.key) { androidx.compose.ui.input.key.Key.ButtonL1 -> -1; androidx.compose.ui.input.key.Key.ButtonR1 -> 1; else -> return@onPreviewKeyEvent false }
            val to = pageStep(tabs, ctx.page, step)
            if (to != ctx.page) ctx.open(to)
            true
        },
        label = "twinPage"
    ) { p ->
        val live = p == ctx.page
        androidx.compose.runtime.CompositionLocalProvider(LocalShownPage provides p) {
        if (p == WinPage.MAIN) main(live, keysOf(p, live))
        else Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(if (desk) Color(0xFFF2F5F8) else Color.White.copy(alpha = 0.05f))
                .then(if (desk) Modifier.border(1.dp, DeskEdge, RoundedCornerShape(10.dp)) else Modifier).padding(6.dp)) {
                androidx.compose.runtime.CompositionLocalProvider(LocalNativePageHost provides NativePageHost(Color.Transparent, dark = !desk, showsCover = true, showsName = true)) {
                    ctx.pageFor(p, Modifier.fillMaxSize(), live)
                }
            }
            Spacer(Modifier.height(8.dp))
            KeyRow(keysOf(p, live)) { k -> key(k) }
        }
        }
    }
}

/** Update 507: how far the two halves have unfolded for the window's reveal [k] (the hinge leads by 0.15). Pure. */
internal fun twinUnfold(k: Float): Float = ((k - 0.15f) / 0.85f).coerceIn(0f, 1f)

/** Update 507: the top screen's picture well at the picture's own shape, kept between a tall cover and a wide banner. Pure. */
internal fun twinPictureRatio(w: Float, h: Float): Float = (w / h).coerceIn(0.56f, 2.4f)

/** v0.6.66 (D142): Twin desk's window over the desk's own pale paper, not a black slab. */
internal val twinDeskVeil: Brush = Brush.verticalGradient(listOf(Color(0xFFEEF1F4).copy(alpha = 0.6f), Color(0xFFDCE2E8).copy(alpha = 0.84f)))

/** The top screen's picture: whole (Fit), never stretched; on the desk a photo pinned to the paper. No art: the console's name on a tinted card. */
@Composable
private fun HingePicture(ctx: WinSceneCtx, desk: Boolean, modifier: Modifier, label: Boolean = true) {
    val art = ctx.wide ?: ctx.backdrop
    val url = art ?: ctx.cover
    val shape = RoundedCornerShape(if (desk) 8.dp else 10.dp)
    // Update 507 (owner review: "preserve cover proportions"): the well takes the picture's own shape once it is known (a
    // portrait cover stood in a 16:9 well, its blurred copy cut at both sides behind it); until then the usual guess.
    var ratio by androidx.compose.runtime.remember(url) { androidx.compose.runtime.mutableStateOf(if (art != null || url == null) 16f / 9f else 3f / 4f) }
    Box(
        modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true)
            .clip(shape).then(if (desk) Modifier.background(Color.White).border(1.dp, DeskEdge, shape).padding(5.dp) else Modifier.background(Color(0xFF0B0E12))),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            val inner = RoundedCornerShape(if (desk) 4.dp else 8.dp)
            AsyncImage(if (art != null) rememberBackdropRequest(url) else rememberStyleArtRequest(url), ctx.name, Modifier.fillMaxSize().clip(inner), contentScale = ContentScale.Fit,
                onSuccess = { st -> val sz = st.painter.intrinsicSize; if (sz.width > 0f && sz.height > 0f) ratio = twinPictureRatio(sz.width, sz.height) })
        } else {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(placeholderBrush(ctx.g.hue)), contentAlignment = Alignment.Center) {
                // (529l: in the page strip the card is too small for the console's name - ShelfTextRoboTest x1.3 cut it in four
                // lines; there it is the game's colour only, the name is on the Details page.)
                if (label) Text(ctx.console, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp))
            }
        }
    }
}

/**
 * The facts as label-over-value cells (3 a row when the window is wide enough) and the summary. Update 487 (D265,
 * owner-approved): the facts stay put; only the story turns, in pages of whole lines with a "1 / 2" count, by L2 / R2 or a
 * tap on it — never by itself (the whole box used to page every 2.8 s: facts gone, a half line on top, a near-empty last page).
 */
@Composable
private fun HingeFacts(ctx: WinSceneCtx, label: Color, value: Color) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = if (maxWidth >= 430.dp) 3 else 2
        val facts = remember(ctx.g, ctx.played, ctx.last, ctx.earned, ctx.total) { sceneFacts(ctx) }
        val summary = ctx.summary
        Column(Modifier.fillMaxWidth()) {
            WholeRows(Modifier.fillMaxWidth()) {
                facts.chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        row.forEach { (l, v) ->
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(l.uppercase(), color = label, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, softWrap = false)
                                Text(v, color = value, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (summary != null) {
                val pager = remember(ctx.g.stableKey) { StoryPager() }
                PagedStory(
                    summary, value.copy(alpha = 0.8f), 11.sp, 14.sp, maxLines = 40,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp), pager = pager, auto = false,
                    countColor = label, tapToTurn = true, windowKeys = true
                )
            }
        }
    }
}

/**
 * Rows shown whole: the viewport ends on a row boundary (the last child, when [textLine] > 0, is a paragraph with that line
 * height: its lines are boundaries too). When the rows don't all fit, it pages by itself — the first row not shown whole
 * becomes the top — and returns to the top after the last page.
 */
@Composable
internal fun SnapRows(modifier: Modifier, textLine: Dp = 0.dp, textTop: Dp = 0.dp, content: @Composable () -> Unit) {
    val target = remember { mutableIntStateOf(0) }
    val off = remember { Animatable(0f) }
    val info = remember { IntArray(2) } // [next page offset or -1, total height]
    // (Update 467, QA only: marked as a paging box, so the geometry gate reads its rows like a scrolling list's.)
    val qa = if (QaTrace.on) Modifier.semantics { this[QaClip.Paged] = true } else Modifier
    // (Update 469, D221: the box keeps its first page's height on every page — a shorter later page (the story's last lines)
    // made the window shrink and grow every 2.8 s, the keys under it jumped, and a tap aimed at More / Play landed on the
    // veil and closed the window (KONKR, Twin desk). Later pages are cut to whole rows inside that height; the rest of the
    // box stays empty.)
    val vis = remember { IntArray(1) }
    Layout(content, modifier.clipToBounds().then(qa).drawWithContent {
        clipRect(bottom = vis[0].toFloat().coerceAtMost(size.height)) { this@drawWithContent.drawContent() }
    }) { ms, c ->
        val ps = ms.map { it.measure(Constraints(maxWidth = c.maxWidth)) }
        val bounds = SnapPaging.bounds(ps.map { it.height }, textLine.roundToPx(), textTop.roundToPx())
        val avail = if (c.hasBoundedHeight) c.maxHeight else Int.MAX_VALUE
        val boxH = SnapPaging.window(bounds, 0, avail)
        val top = target.intValue.coerceAtMost(bounds.last())
        val shown = SnapPaging.window(bounds, top, boxH)
        info[0] = SnapPaging.next(bounds, top, boxH) ?: -1
        info[1] = bounds.last()
        vis[0] = shown
        layout(c.maxWidth, boxH.coerceIn(c.minHeight, maxOf(c.minHeight, avail))) {
            var y = -off.value.toInt()
            ps.forEach { it.placeRelative(0, y); y += it.height }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2800)
            val next = info[0]
            val to = if (next > 0) next else 0
            if (to == target.intValue) continue
            target.intValue = to
            off.animateTo(to.toFloat(), tween(if (to == 0) 420 else 640))
            if (to == 0) delay(1200)
        }
    }
}

/** v0.6.66 (D142): whole-row paging maths (pure, unit-tested in TwinHinge142Test). Heights / offsets in px. */
internal object SnapPaging {
    /** Row boundaries: 0, each row's bottom; inside a closing paragraph ([textLine] > 0) every line's bottom too. */
    fun bounds(heights: List<Int>, textLine: Int, textTop: Int): List<Int> {
        val out = ArrayList<Int>(heights.size + 8); out += 0
        var y = 0
        heights.forEachIndexed { i, hh ->
            if (i == heights.lastIndex && textLine > 0) {
                var b = y + textTop + textLine
                while (b < y + hh - textLine / 3) { out += b; b += textLine }
            }
            y += hh
            if (out.last() != y) out += y
        }
        return out
    }

    /** The height shown from [top]: down to the last boundary that fits in [avail]; never 0 (one row taller than the room is cut, not hidden). */
    fun window(bounds: List<Int>, top: Int, avail: Int): Int {
        val total = bounds.last()
        if (avail <= 0) return 0
        if (total - top <= avail) return total - top
        val end = bounds.lastOrNull { it > top && it <= top + avail }
        return (end ?: (top + avail)) - top
    }

    /** Where the next page starts (the first boundary not shown whole), or null when everything from [top] is shown. */
    fun next(bounds: List<Int>, top: Int, avail: Int): Int? {
        val shown = window(bounds, top, avail)
        val end = top + shown
        return if (end >= bounds.last() || shown <= 0) null else end
    }
}
