package com.shelf.launcher.ui

import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout

/**
 * Update 462: the controller contract shared by every home layout (tools/qa/INPUT_CONTRACT.md).
 *
 * The home screen owns a few buttons on every layout: Select opens Settings, Start the focused game's options, R1 the
 * quick menu, L1 the shortcuts (and L2 / R2 switch the category, B on the home opens the category picker). A layout can
 * draw and move things its own way, but it doesn't take one of these buttons for something else unless it is in a
 * context of its own at that moment (a page of its own that uses the bumpers to jump, a launch animation playing, or an
 * option the user switched on). Before this, Pocket Quest's Select opened Select a System and eight layouts' Select sorted,
 * so Settings couldn't be reached from the controller there.
 *
 * Precedence (highest first): the system / a game's overlay, then an open popup (aDialogIsOpen: styleHomeKey isn't
 * called at all), then these global buttons, then the layout's own navigation. Select + another button (the hotkey combos)
 * never reaches a layout: LauncherScreen takes it first.
 */
internal object InputContract {
    const val SELECT = android.view.KeyEvent.KEYCODE_BUTTON_SELECT // 109
    const val START = android.view.KeyEvent.KEYCODE_BUTTON_START // 108
    const val MENU = android.view.KeyEvent.KEYCODE_MENU // 82 (what Android sends for an unclaimed Select)
    const val L1 = android.view.KeyEvent.KEYCODE_BUTTON_L1 // 102
    const val R1 = android.view.KeyEvent.KEYCODE_BUTTON_R1 // 103

    /** The buttons the home screen keeps on every layout. */
    val RESERVED = setOf(SELECT, START, MENU, L1, R1)

    /** What a layout is doing right now that may own a reserved button. */
    data class Ctx(
        /** Pocket classic: on its Games / Categories page (L1 / R1 jump 15 there). */
        val pocketPage: Boolean = false,
        /** Pocket Quest: "L1 / R1 change the console" switched on by the user (Features.pqBumpers). */
        val pqBumpers: Boolean = false,
        /** The layout's own launch animation is playing: it holds every button for that moment. */
        val launching: Boolean = false
    )

    /** Who gets a key: the layout's own handler, the home screen (the global action), or nobody (held for a moment). */
    enum class Route { LAYOUT, HOME, HOLD }

    fun route(code: Int, layout: HomeLayout, ctx: Ctx): Route {
        if (code !in RESERVED) return Route.LAYOUT
        // A launch animation is playing: nothing else opens over the game that's starting (rapid presses).
        if (ctx.launching && (layout == HomeLayout.POCKET || layout == HomeLayout.POCKET_CLASSIC || layout == HomeLayout.POCKET_RIBBON)) return Route.HOLD
        if (code == L1 || code == R1) {
            if (layout == HomeLayout.POCKET_CLASSIC && ctx.pocketPage) return Route.LAYOUT
            if (layout == HomeLayout.POCKET && ctx.pqBumpers) return Route.LAYOUT
        }
        return Route.HOME
    }

    /** True when [code] belongs to the home screen right now: the layout's handler must leave it (return false). */
    fun leaveToHome(code: Int, layout: HomeLayout, ctx: Ctx): Boolean = route(code, layout, ctx) == Route.HOME

    /** The live context (read on each key). */
    fun live(): Ctx = Ctx(
        pocketPage = PocketNav.page != PocketNav.Page.HOME,
        pqBumpers = Features.pqBumpers.value,
        launching = PqNav.launching != null || PocketNav.launching
    )
}

/**
 * Update 462: the category picker of a layout that has its own (Pocket Quest's Select a System, Green Core's Systems,
 * Pocket classic's Categories page). B on the home screen and the Category screen hotkey open it instead of the plain
 * category screen. Same data as L2 / R2 (StyleCtx.stops: the visible categories in their saved order); picking one steps
 * there like L2 / R2 do, so a category's own layout setting is never written; B closes it on the same game.
 * False = this layout has none (or isn't drawn yet): the plain category screen opens.
 */
internal object ThemedPicker {
    fun has(layout: HomeLayout): Boolean =
        layout == HomeLayout.POCKET || layout == HomeLayout.GREENCORE || layout == HomeLayout.POCKET_CLASSIC

    fun open(layout: HomeLayout): Boolean {
        if (!Features.pbThemed.value) return false
        when (layout) {
            HomeLayout.POCKET -> {
                val c = PqNav.ctx ?: return false
                if (PqNav.page != PqNav.Page.SYSTEMS) pqOpenSystems(c)
                return true
            }
            HomeLayout.GREENCORE -> {
                val c = GreenCoreNav.ctx ?: return false
                if (GreenCoreNav.page != GreenCoreNav.Page.SYSTEMS) gcActivate(c, GcItem.SYSTEMS.ordinal)
                return true
            }
            HomeLayout.POCKET_CLASSIC -> {
                val c = PocketNav.ctx ?: return false
                if (PocketNav.page != PocketNav.Page.CATEGORIES) {
                    PocketSounds.play?.invoke(UiSound.OPEN)
                    PocketNav.cursor = c.stop.coerceAtLeast(0)
                    PocketNav.page = PocketNav.Page.CATEGORIES
                }
                return true
            }
            else -> return false
        }
    }
}
