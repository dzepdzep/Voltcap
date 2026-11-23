package com.shelf.launcher.ui

import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.RaAchievementDetail

/*
 * P2 (owner 17 Nov 21:31): the 40 layouts that still opened the shared expanded page get Details / Achievements / Options of
 * their own, each drawn the way its home is drawn - "not the shared pages in different borders". This kit is the part they share
 * and nothing more: the data (the facts, the story, the achievements in the chosen order, the options) and the behaviour (L1 / R1
 * walk the pages (nativeBumpers, on the window's root); the leaving copy's rows do nothing when pressed; the page's first row takes the list focus; move / select sounds; a
 * touch box of at least 32 dp; lists scroll with the D-pad and show a scroll cue). Every layout keeps its own look, its own
 * page names and its own motion. The first family: Record crate (WinPagesCrate.kt).
 */

/** One fact of the game for a native page (label, value) - the same facts every window lists (sceneFacts). */
internal data class NativeFact(val label: String, val value: String)

internal fun nativeFacts(ctx: WinSceneCtx, omit: Set<String> = emptySet()): List<NativeFact> =
    shownFacts(ctx, omit).map { NativeFact(it.first, it.second) }

/** The achievements a native page shows: in the window's chosen order (Features.winTrophyOrder), and how to step that order. */
internal class NativeTrophies(
    /** The rows to draw now - the first [NATIVE_ROWS_FIRST] at once, the rest added a few a frame once the page has opened
     *  ([nativeRowsShown]). Counts, totals and look-ups use [all]. */
    val list: List<RaAchievementDetail>,
    val order: Features.TrophyOrder,
    val nextOrder: () -> Unit,
    val all: List<RaAchievementDetail> = list
) {
    val earned: Int get() = all.count { it.isUnlocked }
    val total: Int get() = all.size
    val gained: List<RaAchievementDetail> get() = all.filter { it.isUnlocked }
    val toGet: List<RaAchievementDetail> get() = all.filterNot { it.isUnlocked }
}

/** 682am (D389): rows composed in the page's first frame. */
internal const val NATIVE_ROWS_FIRST = 10
/** 682am (D389): rows added each frame after the page has opened. */
internal const val NATIVE_ROWS_STEP = 3

/** 682am (D389, pure): how many rows to draw - never fewer than were already shown (a list that refreshes or re-sorts keeps
 *  the rows the D-pad may be on), never more than there are. */
internal fun nativeRowsShown(had: Int, total: Int): Int = maxOf(had, minOf(NATIVE_ROWS_FIRST, total)).coerceAtMost(total)

@Composable
internal fun rememberNativeTrophies(ctx: WinSceneCtx): NativeTrophies {
    val order = Features.winTrophyOrder.state.value
    val details = ctx.state.raAchievementDetails[ctx.g.stableKey]
    val list = remember(details, order) { trophyOrder(details.orEmpty(), order) }
    // (682am, D389 - KONKR atrace kt_big: a 120-achievement game's Achievements page took one 1031 ms frame in Cinema, 194 ms in
    // Arcade - every row composed at once, each with its badge picture and fitted texts. The first rows come at once, the rest a
    // few a frame after the page has slid in: same rows, same order, same look.)
    val shown = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val n = nativeRowsShown(shown.intValue, list.size)
    if (n != shown.intValue) androidx.compose.runtime.SideEffect { shown.intValue = n }
    LaunchedEffect(list) {
        if (shown.intValue >= list.size) return@LaunchedEffect
        kotlinx.coroutines.delay(NATIVE_PAGE_MS.toLong())
        while (shown.intValue < list.size) {
            androidx.compose.runtime.withFrameNanos { }
            shown.intValue = minOf(list.size, maxOf(shown.intValue, NATIVE_ROWS_FIRST) + NATIVE_ROWS_STEP)
        }
    }
    val rows = if (n >= list.size) list else list.subList(0, n)
    return NativeTrophies(rows, order, {
        val all = Features.TrophyOrder.values()
        Features.winTrophyOrder.value = all[(order.ordinal + 1) % all.size]
    }, all = list)
}

/** The pages a native window walks with L1 / R1, in key order - only those the window offers (ctx.pages). Pure. */
internal fun nativeTabs(pages: Set<WinPage>, order: List<WinPage> = NATIVE_TAB_ORDER): List<WinPage> = order.filter { it in pages }

internal val NATIVE_TAB_ORDER = listOf(WinPage.MAIN, WinPage.DETAILS, WinPage.TROPHIES, WinPage.OPTIONS)

/**
 * The page area of a native window: [content] draws [WinPage.MAIN] or a page, the layout's own [move] carries one to the
 * next (forward +1 / back -1, never with animations off), L1 / R1 walk the [tabs]. The copy on its way out gets `live` = false
 * (its rows take no focus and no click - WinAction.inert) and [LocalShownPage] says which page it is.
 */
@Composable
internal fun NativePages(
    ctx: WinSceneCtx,
    modifier: Modifier,
    label: String,
    tabs: List<WinPage> = nativeTabs(ctx.pages),
    move: (dir: Int) -> ContentTransform,
    /** (681b) In the page change's scope: a layout can drive its own motion from `transition` (the crate's insert tilts as it is
     *  pulled out, then settles). */
    content: @Composable androidx.compose.animation.AnimatedVisibilityScope.(page: WinPage, live: Boolean) -> Unit
) {
    val anim = rememberAnimationsEnabled()
    AnimatedContent(
        targetState = ctx.page,
        transitionSpec = {
            if (!anim) EnterTransition.None togetherWith ExitTransition.None
            // (682ah, RP Classic x1.3 rh13: the default size animation clipped the new Magazine sheet while its header stepped
            // down - its key row rose into view over ~250 ms. The page area is the layout's to size: no size animation.)
            else move(pageDirection(tabs, initialState, targetState)).using(null)
        },
        contentKey = { it },
        modifier = modifier.nativeBumpers(ctx, tabs),
        label = label
    ) { p ->
        androidx.compose.runtime.CompositionLocalProvider(LocalShownPage provides p) { NativePageOpening { this@AnimatedContent.content(p, p == ctx.page) } }
    }
}

/**
 * L1 / R1 walk [tabs] wherever the D-pad is in the window - put it on the window's root too (its keys sit outside the page
 * area: on Info the focus is on a key, and the bumpers must still turn the page).
 */
internal fun Modifier.nativeBumpers(ctx: WinSceneCtx, tabs: List<WinPage> = nativeTabs(ctx.pages)): Modifier = this.onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    val step = when (e.key) { Key.ButtonL1 -> -1; Key.ButtonR1 -> 1; else -> return@onPreviewKeyEvent false }
    val to = pageStep(tabs, ctx.page, step)
    if (to != ctx.page) ctx.open(to)
    true
}


/** The list focus for a page's first row: the window's own while the page is live, a loose one for the copy that leaves. */
@Composable
internal fun nativeListFocus(ctx: WinSceneCtx, live: Boolean): FocusRequester {
    val loose = remember { FocusRequester() }
    return if (live) ctx.listFocus else loose
}

/** Smallest touch box of a native page's row (dp) - above the 24 dp gate, so a slim printed line is still easy to tap. */
internal const val NATIVE_ROW_MIN = 32

/**
 * A row of a native page that the D-pad stops on and a tap / A presses: the shared part (focus, the move and select sounds, the
 * touch box, inert while leaving). The layout draws it: [content] gets whether the D-pad is on it.
 */
@Composable
internal fun NativeItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focus: FocusRequester? = null,
    live: Boolean = true,
    minTouch: Dp = NATIVE_ROW_MIN.dp,
    back: Boolean = false,
    content: @Composable (focused: Boolean) -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    LaunchedEffect(focused) { if (focused) sounds?.play(UiSound.MOVE) }
    // (682h, emulator review 44272567211: an armed removing row - and an opened achievement - grows a line under it; at the
    // list's bottom that line was cut by the page's edge. A focused row that grows brings itself whole into view again.)
    val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val lastH = remember { intArrayOf(-1) }
    // (682k: a tapped row that grows - the touch screen's armed confirmation - brings itself into view too.)
    val pressedAt = remember { longArrayOf(0L) }
    Box(
        modifier
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .bringIntoViewRequester(bring)
            .onSizeChanged { sz ->
                val grew = lastH[0] >= 0 && sz.height > lastH[0]
                lastH[0] = sz.height
                val justPressed = android.os.SystemClock.uptimeMillis() - pressedAt[0] < 600
                if (grew && (focused || justPressed)) scope.launch { kotlinx.coroutines.delay(16); bring.bringIntoView() }
            }
            .heightIn(min = minTouch)
            // (The copy on its way out stays focusable - disabling it handed the focus to Play for a frame on B (WinFocusRoboTest) -
            // but its press does nothing.)
            .clickable(interactionSource = source, indication = null) {
                if (!live) return@clickable
                WinClickProbe.clicks++
                pressedAt[0] = android.os.SystemClock.uptimeMillis()
                sounds?.play(if (back) UiSound.BACK else UiSound.SELECT)
                onClick()
            }
    ) { content(focused) }
}

/** A native page's list: it scrolls (the D-pad brings the row it is on into view), with the slim scroll cue in [ink]. */
@Composable
internal fun Modifier.nativeScroll(ink: Color): Modifier {
    val state = rememberScrollState()
    return this.pageScrollCue(state, ink).verticalScroll(state)
}

/** Which achievement a page has opened (A on it shows all it asks); one at a time, forgotten with the game. */
@Composable
internal fun rememberOpenTrophy(ctx: WinSceneCtx): androidx.compose.runtime.MutableState<Int?> =
    remember(ctx.g.stableKey) { mutableStateOf<Int?>(null) }

/**
 * (681b, owner 17 Nov: "show destructive-option confirmations") A row that removes something asks first: the first press arms
 * it (the row says so - its layout draws [armed]), a second press does it; moving off the row disarms it. One row at a time.
 */
internal class NativeConfirm {
    var armed by mutableStateOf<Int?>(null)
    /** True when [i]'s press should run now (it was armed); otherwise arms it. Pure on its state. */
    fun press(i: Int, destructive: Boolean): Boolean {
        if (!destructive) { armed = null; return true }
        return if (armed == i) { armed = null; true } else { armed = i; false }
    }
    fun leave(i: Int) { if (armed == i) armed = null }
}

@Composable
internal fun rememberNativeConfirm(ctx: WinSceneCtx): NativeConfirm = remember(ctx.g.stableKey) { NativeConfirm() }

/**
 * 682t (owner 18 Nov, RP Classic review: the Hub's medal discs were empty black icons - the badge pictures had not loaded there).
 * An achievement's badge with a fallback that is always drawn first: the achievement's own mark - its first letter on a disc in
 * the layout's colour (earned) or grey (to get) - and the picture over it when it loads. A badge that never loads (offline, no
 * badge, a fixture) leaves a readable mark, never an empty black square. [locked] greys a picture that is not earned.
 */
@Composable
internal fun NativeBadge(badgeName: String?, title: String?, earned: Boolean, accent: Color, modifier: Modifier,
                         shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp), locked: Boolean = true) {
    val ink = if (earned) accent else Color(0xFF6B6F78)
    Box(modifier.clip(shape).background(ink.copy(alpha = if (earned) 0.32f else 0.22f))
        .border(1.dp, ink.copy(alpha = 0.7f), shape), contentAlignment = Alignment.Center) {
        Text(nativeBadgeMark(title), color = if (earned) Color.White else Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Black,
            fontSize = 12.sp, maxLines = 1, softWrap = false)
        com.shelf.launcher.data.RaBadges.url(badgeName)?.let { url ->
            coil.compose.AsyncImage(url, title, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                colorFilter = if (!earned && locked) androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }) else null,
                alpha = if (!earned && locked) 0.6f else 1f, modifier = Modifier.matchParentSize())
        }
    }
}

/** The fallback mark: the title's first letter or digit, upper case; "★" with none. Pure. */
internal fun nativeBadgeMark(title: String?): String = title?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "★"

/** 682v: [NativeBadge]'s fallback mark alone, for a page's own badge frame - drawn under the picture, so a badge that never
 *  loads shows the achievement's letter instead of an empty black tile (RP Classic, owner 18 Nov). */
@Composable
internal fun androidx.compose.foundation.layout.BoxScope.NativeBadgeMark(title: String?, earned: Boolean, ink: Color = Color.White) {
    Text(nativeBadgeMark(title), color = ink.copy(alpha = if (earned) 0.85f else 0.45f), fontWeight = FontWeight.Black, fontSize = 11.sp,
        maxLines = 1, softWrap = false, modifier = Modifier.align(Alignment.Center))
}


/** 682ag: how long after a page appears its first focus does not scroll it (ms). */
internal const val NATIVE_OPEN_HOLD_MS = 600L

/** 682ag (pure): the scroll a focus asks for while the page is opening - none in the first [NATIVE_OPEN_HOLD_MS]. */
internal fun nativeOpeningScroll(sinceOpenMs: Long, wanted: Float): Float = if (sinceOpenMs < NATIVE_OPEN_HOLD_MS) 0f else wanted

/**
 * 682ag (owner 23 Nov 13:32, page-transition audit on the KONKR x1.3 clips): a page's first focus is often below its top (the
 * story under the facts on Details), and focusing it scrolled the page for ~250 ms right after it had appeared - the content
 * jumped up under the new heading. While a page opens its first focus scrolls nothing: the page appears at its top and stays
 * there; D-pad Down / a touch scroll it afterwards as before (a row that grows still brings itself into view after that).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NativePageOpening(content: @Composable () -> Unit) {
    val openedAt = remember { android.os.SystemClock.uptimeMillis() }
    val default = androidx.compose.foundation.gestures.LocalBringIntoViewSpec.current
    val spec = remember(default, openedAt) {
        object : androidx.compose.foundation.gestures.BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                nativeOpeningScroll(android.os.SystemClock.uptimeMillis() - openedAt, default.calculateScrollDistance(offset, size, containerSize))
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides spec, content = content)
}

/** 682ag: how long a native page's outgoing copy takes to fade out (ms) - the incoming one starts after it, and a page's heading
 *  changes then: never a new heading over the old page's content, never both pages' text at once. */
internal const val NATIVE_OUT_MS = 90

/**
 * 682ag (owner 23 Nov 13:32, transition audit KONKR x1.3: Magazine's masthead said THE REVIEW, Library's tab moved and Arctic's
 * heading changed in the first frame while the old page was still on screen): the page a heading shows - [page], [NATIVE_OUT_MS]
 * after it changed (the moment the old content is gone); at once with animations off.
 */
@Composable
internal fun rememberHeadingPage(page: WinPage): WinPage {
    val anim = rememberAnimationsEnabled()
    val shown = remember { androidx.compose.runtime.mutableStateOf(page) }
    LaunchedEffect(page, anim) {
        if (anim && shown.value != page) kotlinx.coroutines.delay(NATIVE_OUT_MS.toLong())
        shown.value = page
    }
    return if (anim) shown.value else page
}

/** 682ap (D386 / D389 profiling): [vcTrace] (PerfTrace.kt) for a composable [content] - its composition as a named section. */
@Composable
internal inline fun vcTraceC(name: String, content: @Composable () -> Unit) {
    val on = android.os.Build.VERSION.SDK_INT >= 29 && android.os.Trace.isEnabled()
    if (on) android.os.Trace.beginSection(name)
    content()
    if (on) android.os.Trace.endSection()
}

/**
 * 682aq (owner 22:43 - D389 / D386: "efficient or lazy row composition ... no blocking, no visible staggered filling"): a native
 * page's achievement rows built lazily - only the rows on screen (and the one the D-pad moves to next) are composed and measured,
 * so a 120-achievement page costs what its first screen costs. Same look as [nativeScroll]: the page's scroll cue and faded
 * edges; the D-pad scrolls it as it scrolled the column (bring-into-view; beyond-bounds focus).
 */
@Composable
internal fun NativeRows(
    ink: androidx.compose.ui.graphics.Color, modifier: Modifier, spacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.foundation.lazy.LazyColumn(
        modifier.nativeLazyCue(state, ink), state = state,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing), content = content
    )
}

/** 682aq: [pageScrollCue] + faded edges for a lazy list - the thumb from the rows laid out so far (their average height). */
internal fun Modifier.nativeLazyCue(state: androidx.compose.foundation.lazy.LazyListState, ink: androidx.compose.ui.graphics.Color): Modifier =
    this.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val info = state.layoutInfo
            val shown = info.visibleItemsInfo
            if (shown.isEmpty()) return@drawWithContent
            val vh = size.height
            val avg = shown.sumOf { it.size }.toFloat() / shown.size + info.mainAxisItemSpacing
            val total = avg * info.totalItemsCount
            val max = (total - vh).coerceAtLeast(0f)
            if (max > 0.5f && (state.canScrollForward || state.canScrollBackward)) {
                val w = 3.dp.toPx(); val inset = 1.dp.toPx()
                val thumb = (vh * vh / (vh + max)).coerceAtLeast(18.dp.toPx()).coerceAtMost(vh)
                val off = (state.firstVisibleItemIndex * avg + state.firstVisibleItemScrollOffset).coerceIn(0f, max)
                val y = (vh - thumb) * (off / max)
                val x = size.width - w - inset
                drawRoundRect(ink.copy(alpha = 0.16f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Size(w, vh),
                    androidx.compose.ui.geometry.CornerRadius(w / 2f))
                drawRoundRect(ink.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(w, thumb),
                    androidx.compose.ui.geometry.CornerRadius(w / 2f))
            }
            val e = 22.dp.toPx().coerceAtMost(vh / 3f)
            if (state.canScrollBackward) drawRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(0f to androidx.compose.ui.graphics.Color.Transparent, 1f to androidx.compose.ui.graphics.Color.Black, startY = 0f, endY = e),
                size = androidx.compose.ui.geometry.Size(size.width, e), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
            if (state.canScrollForward) drawRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(0f to androidx.compose.ui.graphics.Color.Black, 1f to androidx.compose.ui.graphics.Color.Transparent, startY = vh - e, endY = vh),
                topLeft = androidx.compose.ui.geometry.Offset(0f, vh - e), size = androidx.compose.ui.geometry.Size(size.width, e),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
        }
