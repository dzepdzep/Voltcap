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
internal val NATIVE_PAGE_SCENES: Set<WinScene> = setOf(WinScene.STORAGE_RECORD, WinScene.NIGHT_STATUS)

internal fun nativePages(scene: WinScene?): Boolean = scene != null && scene in NATIVE_PAGE_SCENES

/** How long a native page change runs (ms); focus moves into the new page after it, so the leaving page can't take it. */
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
internal fun WinAction.inert(): WinAction = WinAction(label, icon, onClick, focus = null, primary = primary, back = back, urgent = urgent)
