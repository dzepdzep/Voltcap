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
    val list: List<RaAchievementDetail>,
    val order: Features.TrophyOrder,
    val nextOrder: () -> Unit
) {
    val earned: Int get() = list.count { it.isUnlocked }
    val total: Int get() = list.size
    val gained: List<RaAchievementDetail> get() = list.filter { it.isUnlocked }
    val toGet: List<RaAchievementDetail> get() = list.filterNot { it.isUnlocked }
}

@Composable
internal fun rememberNativeTrophies(ctx: WinSceneCtx): NativeTrophies {
    val order = Features.winTrophyOrder.state.value
    val details = ctx.state.raAchievementDetails[ctx.g.stableKey]
    val list = remember(details, order) { trophyOrder(details.orEmpty(), order) }
    return NativeTrophies(list, order) {
        val all = Features.TrophyOrder.values()
        Features.winTrophyOrder.value = all[(order.ordinal + 1) % all.size]
    }
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
            else move(pageDirection(tabs, initialState, targetState))
        },
        contentKey = { it },
        modifier = modifier.nativeBumpers(ctx, tabs),
        label = label
    ) { p ->
        androidx.compose.runtime.CompositionLocalProvider(LocalShownPage provides p) { this@AnimatedContent.content(p, p == ctx.page) }
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
