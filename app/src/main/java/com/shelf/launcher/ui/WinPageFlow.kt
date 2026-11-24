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
/**
 * 682j (owner 18 Nov: "correct focus ownership rather than a cooldown"): the page you land on owns the focus - Info its Play,
 * Achievements / Options their first row, and Details its own first item wherever the window draws its own Details ([ownDetails]:
 * the native pages, the TV set's teletext). Details on Play was the shared page's rule (an information page with nothing of its
 * own to stand on); on a native page it made a quick L1 / R1 then A start the game.
 */
internal fun pageFocusTarget(page: WinPage, fromPerf: Boolean, hasOpener: Boolean, ownDetails: Boolean = false): PageFocus = when (page) {
    WinPage.OPTIONS, WinPage.TROPHIES, WinPage.PERF -> PageFocus.LIST
    WinPage.DETAILS -> if (ownDetails) PageFocus.LIST else PageFocus.PLAY
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
    WinScene.TWIN_HINGE,
    // (673f: Disc station - the pages are the deck's tracks, played on its LCD; DiscPlayerPanel.)
    WinScene.DISC_PANEL,
    // (P2 batch 1 - owner 17 Nov: each of the remaining layouts its own pages, NativePageKit + WinPages*.kt.)
    WinScene.RECORD_SLEEVE,
    // (P2 681c: Classic - the charged panel's readout, cells and switches, WinPagesVolt.kt.)
    WinScene.VOLT_PANEL,
    // (P2 681d: Console hub - the nameplate's engraving, service medals and switch panel, WinPagesHub.kt.)
    WinScene.HUB_PLATE,
    // (P2 681e: Resume & discover - the discover feed's about cards, progress shelves and settings pills, WinPagesDiscover.kt.)
    WinScene.DISCOVER_CARD,
    // (P2 batch 2, 682n: Game magazine - the review, the checklist and the reader-service coupons, WinPagesMag.kt.)
    WinScene.MAG_PAGE,
    // (682o: Cinema - the streaming service's More info, Episodes and My List screens, WinPagesCinema.kt.)
    WinScene.CINEMA_PREMIERE,
    // (682p: Arcade cabinets - the machine's instruction card, high-score table and service mode, WinPagesArcade.kt.)
    WinScene.ARCADE_MENU,
    // (682q: Cube menu - the cube's other faces: specs tiles, a wall of little cubes, square control keys, WinPagesCube.kt.)
    WinScene.CUBE_FACE,
    // (682t: Spotlight - the poster's billing block, the end credits and backstage, WinPagesSpot.kt.)
    WinScene.SPOT_POSTER,
    // (682w, P2 batch 3: Terminal - TYPE / DIR / SETUP at the prompt, WinPagesTerm.kt.)
    WinScene.TERM_RUN,
    // (682w: Dashboard - Overview / Progress / Settings tabs, WinPagesDash.kt.)
    WinScene.DASH_CARD,
    // (682w: Library - About this game / Achievements / Manage under a tab strip, WinPagesLibrary.kt.)
    WinScene.LIBRARY_PAGE,
    // (682w: Card binder - CARD TEXT / MOVES / RULES in the card's text box, WinPagesCard.kt.)
    WinScene.CARD_BACK,
    // (682y, P2 batch 4: Side menu - the drawer's submenus, WinPagesSide.kt.)
    WinScene.SIDE_DRAWER, WinScene.ARCTIC_DRAWER, WinScene.DOCK_FLYOUT)

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
internal fun WinAction.inert(): WinAction = WinAction(label, icon, onClick, focus = null, primary = primary, back = back, urgent = urgent, inert = true, status = status)

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
    // (682j: the plain / TV windows' Details key is the return's second choice - GameWindow's page effect.)
    init { if (slot.size > 1 && slot[1] == null) slot[1] = requester(OPENER_DETAILS) }
    /** The requester of the control called [name] (made once per window). */
    fun requester(name: String): FocusRequester = byName.getOrPut(name) { FocusRequester() }
    /** The control called [name] is opening a page: B comes back onto it. */
    fun opened(name: String) { slot[0] = requester(name) }
}

internal val LocalPageOpeners = androidx.compose.runtime.staticCompositionLocalOf<PageOpeners?> { null }


/**
 * 682j (owner 18 Nov, "prefer correct focus ownership, event consumption and action routing rather than a fixed cooldown"): a
 * page change of a window that draws its own pages hands the focus over in the same event. Root cause of "A after L1 / R1 starts
 * the game": for the frames between the turn and the new page lighting its own control, the focus stayed where it was - on
 * Play (the TV set's keys stay on screen on every page), or it fell to the next control (the B Back hint under the window, so A
 * closed it) - and Details itself put the focus on Play. Now the control that had the focus gives it up at once to this
 * holder, an invisible stop that can only take the focus while it holds; every press that comes in meanwhile (A, the D-pad,
 * more L1 / R1) is kept in order and delivered to whatever the new page lights - nothing is suppressed, nothing is lost, and
 * nothing reaches the control that was lit before the turn. B / Back is not kept: it goes back at once and drops the presses
 * meant for the page it leaves.
 */
internal class PageHandoff {
    val focus = FocusRequester()
    @Volatile var holding = false
        private set
    /** Whether the holder has the focus (its onFocusChanged). */
    var focused = false
    private val held = ArrayList<android.view.KeyEvent>()
    /** Take the focus off whatever has it - called by the page change itself, inside the event that turns the page. */
    fun hold() {
        holding = true
        val r = runCatching { focus.requestFocus() }
        if (r.isFailure || r.getOrNull() == false) holding = false
    }
    /** A key that reached the holder: kept for the new page (true), or let through (B / Back, or not holding). */
    fun keep(e: android.view.KeyEvent): Boolean {
        if (!holding) return false
        if (e.keyCode in HANDOFF_BACK_KEYS) { held.clear(); return false }
        if (held.size < HANDOFF_MAX_KEPT) held += android.view.KeyEvent(e)
        return true
    }
    /** The new page lit its control: deliver what was kept, in order (a press that turns the page again is kept anew).
     *  [onPlay]: what got the focus is Play (the page's own control could not be lit, or the page starts on Play) - then a
     *  confirm press kept while the page changed ([HANDOFF_CONFIRM_KEYS], down and up) is dropped: it was pressed before Play was
     *  on screen lit, so it never starts the game (682ar, owner 24 Nov). The D-pad and the bumpers still go through, in order;
     *  a press made once Play is lit starts the game as always. */
    fun release(onPlay: Boolean = false, deliver: (android.view.KeyEvent) -> Unit) {
        holding = false
        if (held.isEmpty()) return
        val out = ArrayList(held); held.clear()
        out.forEach { if (!(onPlay && it.keyCode in HANDOFF_CONFIRM_KEYS)) deliver(it) }
    }
    internal val keptCount: Int get() = held.size
}

internal val HANDOFF_BACK_KEYS = intArrayOf(android.view.KeyEvent.KEYCODE_BACK, android.view.KeyEvent.KEYCODE_BUTTON_B, android.view.KeyEvent.KEYCODE_ESCAPE)
internal const val HANDOFF_MAX_KEPT = 48
/** 682ar: the keys that press the lit control - never handed to Play from a page change ([PageHandoff.release]). */
internal val HANDOFF_CONFIRM_KEYS = intArrayOf(android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER,
    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER, android.view.KeyEvent.KEYCODE_BUTTON_A, android.view.KeyEvent.KEYCODE_SPACE,
    android.view.KeyEvent.KEYCODE_BUTTON_START)

/** The window's page with [PageHandoff]: a change in a window with its own pages ([own]) hands the focus over as it happens. */
internal class HandoffPage(initial: WinPage, private val h: PageHandoff) {
    private val s = androidx.compose.runtime.mutableStateOf(initial)
    var own = false
    operator fun getValue(r: Any?, p: kotlin.reflect.KProperty<*>): WinPage = s.value
    operator fun setValue(r: Any?, p: kotlin.reflect.KProperty<*>, v: WinPage) {
        if (v != s.value && own) h.hold()
        s.value = v
    }
}
