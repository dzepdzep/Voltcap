package com.shelf.launcher.ui

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/*
 * Popup pages, shared rules (popup motion work, 12 Nov). One place decides where the D-pad goes when a game window's page
 * changes, which layout windows draw their own pages, and the page order L1 / R1 walk. The look and the motion stay the
 * layout's own (WinScenes*.kt); only the behaviour is shared, so every layout gets it the same way.
 */

/** Where the D-pad goes after a page change. */
internal enum class PageFocus { LIST, PERF, OPENER, PLAY }

/**
 * The page's focus target. Back on the first page the D-pad returns to the control that opened the page (B never lands on
 * Play first: Play lit for a moment after B made a quick A start the game). Pure.
 */
internal fun pageFocusTarget(page: WinPage, fromPerf: Boolean, hasOpener: Boolean): PageFocus = when (page) {
    WinPage.OPTIONS, WinPage.TROPHIES, WinPage.PERF -> PageFocus.LIST
    WinPage.DETAILS -> PageFocus.PLAY
    WinPage.MAIN -> when {
        fromPerf -> PageFocus.PERF
        hasOpener -> PageFocus.OPENER
        else -> PageFocus.PLAY
    }
}

/**
 * The layout windows that draw their own Details / Achievements / Options inside their own design (their tabs, their paper,
 * their motion) instead of the shared expanded page (update 426). A scene joins only once its pages are readable at
 * 640 × 427 dp and ×1.3 text (popup motion review).
 */
internal val NATIVE_PAGE_SCENES: Set<WinScene> = setOf(WinScene.STORAGE_RECORD, WinScene.NIGHT_STATUS, WinScene.TILE_CARD, WinScene.BLADE_GUIDE,
    // (529k: the shelf family - Game shelf, Case stacks, Console stack, Coverflow details - ShelfFamilyPages.)
    WinScene.INDEX_CARD,
    // (529l: the Twin family - Twin desk, Twin icons, Twin showcase - pages on the touch screen, TwinTouchPages.)
    WinScene.TWIN_HINGE)

internal fun nativePages(scene: WinScene?): Boolean = scene != null && scene in NATIVE_PAGE_SCENES

/** How long a native page change runs (ms). Focus goes into the new page at once: the leaving copy is inert (WinAction.inert). */
internal const val NATIVE_PAGE_MS = 280

/** L1 / R1: the page [step] away from [at] in [tabs], wrapping. Pure. */
internal fun pageStep(tabs: List<WinPage>, at: WinPage, step: Int): WinPage =
    if (tabs.isEmpty()) at else tabs[(tabs.indexOf(at).coerceAtLeast(0) + step).mod(tabs.size)]

/** The way a page change moves: +1 forward (to a later tab), -1 back. Pure. */
internal fun pageDirection(tabs: List<WinPage>, from: WinPage, to: WinPage): Int {
    val d = tabs.indexOf(to) - tabs.indexOf(from)
    return if (d < 0) -1 else 1
}

/**
 * Asks [f] for the focus once it is attached: tries each frame for [frames] frames (the page it lives on may only be
 * composed in the next one). True when it took the focus.
 */
internal suspend fun focusWhenAttached(f: FocusRequester, frames: Int = 8): Boolean {
    repeat(frames) {
        withFrameNanos { }
        val r = runCatching { f.requestFocus() }
        if (r.isSuccess && r.getOrNull() != false) return true
    }
    return false
}

/** A copy of [a] with no focus requester: the leaving page of a native page change can't hold or take the focus. */
internal fun WinAction.inert(): WinAction = WinAction(label, icon, onClick, focus = null, primary = primary, back = back, urgent = urgent, inert = true)

/** What opens for a game (popup infrastructure, 12 Nov): one decision for every opener, read by [GameWindow]. */
internal enum class WinPresentation {
    /** The classic details window (PopupStyle CLASSIC). */
    CLASSIC,
    /** Game Glance's shared move (GlanceWindow.kt). */
    GLANCE,
    /** The row opens / row reveal (RowMotionWindow); with no row drawn it falls back to the banner or the layout's own window. */
    ROW_MOTION,
    /** The banner page, the same for every layout. */
    BANNER,
    /** The layout's own window (WinScenes*.kt). */
    SCENE,
    /** The plain window (no scene for the layout, or Settings › Game window › the layouts' own windows off). */
    PLAIN
}

/**
 * The presentation [layout]'s game window uses. [inspect]: Inspect was pressed in this window; [inspectForced]: the menu's
 * Inspect… / Game Glance asked for this game's piece (only Classic gives way to it, as before). Reads the popup style prefs.
 */
internal fun winPresentation(layout: com.shelf.launcher.data.HomeLayout, inspect: Boolean, inspectForced: Boolean): WinPresentation {
    val motion = PopupStyle.motion(layout.name)
    return when {
        PopupStyle.opensClassic(layout.name) && !inspect && !inspectForced -> WinPresentation.CLASSIC
        inspect -> ownWindow(layout)
        motion == com.shelf.launcher.data.Features.WinMotion.SHARED -> WinPresentation.GLANCE
        motion != com.shelf.launcher.data.Features.WinMotion.NONE -> WinPresentation.ROW_MOTION
        PopupStyle.banner(layout.name) -> WinPresentation.BANNER
        else -> ownWindow(layout)
    }
}

private fun ownWindow(layout: com.shelf.launcher.data.HomeLayout): WinPresentation =
    if (com.shelf.launcher.data.Features.winScenes.state.value && winSceneFor(layout) != null) WinPresentation.SCENE else WinPresentation.PLAIN

/**
 * True when [page] of [scene]'s window is drawn by the shared expanded page instead of the scene itself. The main page is
 * always the scene's; Cross keeps its options list; the native-page scenes keep every page. Pure.
 */
internal fun sharedPageFor(scene: WinScene?, page: WinPage): Boolean =
    page != WinPage.MAIN && scene != WinScene.CROSS_OPTIONS && !nativePages(scene)

/**
 * The page openers of a window that is not a layout scene (the plain window, the TV room's), Phase 2 (12 Nov): its Details /
 * Game options / Achievements buttons register here as they open a page, so B comes back onto them - not onto Play (found by
 * WinFocusRoboTest: in the TV room an A 100 ms after B started the game). The scene windows keep their own (SceneBody.opener).
 */
internal class PageOpeners(private val slot: Array<FocusRequester?>) {
    private val byName = HashMap<String, FocusRequester>()
    /** The requester of the control called [name] (made once per window). */
    fun requester(name: String): FocusRequester = byName.getOrPut(name) { FocusRequester() }
    /** The control called [name] is opening a page: B comes back onto it. */
    fun opened(name: String) { slot[0] = requester(name) }
}

internal val LocalPageOpeners = androidx.compose.runtime.staticCompositionLocalOf<PageOpeners?> { null }
