package com.shelf.launcher.ui

import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.FocusPosition
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.TileFocus

/**
 * 723a (owner Task 2 / 23 Nov 22:43 item 4): R1's Focus position tile - where the game you're on stays across the screen
 * while you browse (not the vertical Row position tile). Audit of the 53 layouts (docs/claude/R1_FOCUS_POSITION_723.md):
 * only two layouts have a horizontal focus anchor of their own, and the tile reuses exactly those settings - no new pref:
 * - Voltcap row: Settings › Home › Layout options › Game you're on (FocusPosition: Left / Middle), read by GameDock;
 * - Tile home: its Game you're on (TileFocus: Moves to the edges / Left / Middle / Right), read by TileHome.
 * Every other layout has no such setting (its own fixed browsing, a grid, a carousel that always centres, Hero's cover
 * side), so the tile is hidden there (r1TileFit). A press steps to the next choice, a hold lists them all.
 * Pure: the R1 panel (QuickSettings) and the tests use the same steps.
 */
internal object FocusPosTile {
    const val ID = "focuspos"

    /** The layouts whose own setting this tile changes. */
    fun applies(l: HomeLayout): Boolean = l == HomeLayout.CLASSIC || l == HomeLayout.TILES

    /** The choices of [a]'s layout, in Settings' order, with their full names (the hold list). Empty where it doesn't apply. */
    fun choices(a: AppearanceSettings): List<String> = when (a.homeLayout) {
        HomeLayout.CLASSIC -> FocusPosition.values().map { it.label }
        HomeLayout.TILES -> TileFocus.values().map { it.label }
        else -> emptyList()
    }

    /** Index of [a]'s current choice in [choices] (-1 where it doesn't apply). */
    fun current(a: AppearanceSettings): Int = when (a.homeLayout) {
        HomeLayout.CLASSIC -> a.focusPosition.ordinal
        HomeLayout.TILES -> a.tileFocus.ordinal
        else -> -1
    }

    /** The tile's two lines: "Focus" and the current choice (Tile home's "Moves to the edges" as the word "Edges"). */
    fun label(a: AppearanceSettings): String = "Focus\n" + when (a.homeLayout) {
        HomeLayout.CLASSIC -> a.focusPosition.label
        HomeLayout.TILES -> if (a.tileFocus == TileFocus.EDGES) "Edges" else a.tileFocus.label
        else -> ""
    }

    /** [a] with choice [i] of [choices] (out of range / another layout: unchanged). */
    fun pick(a: AppearanceSettings, i: Int): AppearanceSettings = when (a.homeLayout) {
        HomeLayout.CLASSIC -> FocusPosition.values().getOrNull(i)?.let { a.copy(focusPosition = it) } ?: a
        HomeLayout.TILES -> TileFocus.values().getOrNull(i)?.let { a.copy(tileFocus = it) } ?: a
        else -> a
    }

    /** One press: the next choice, round to the first after the last. */
    fun next(a: AppearanceSettings): AppearanceSettings {
        val n = choices(a).size
        return if (n == 0) a else pick(a, (current(a) + 1) % n)
    }

    /**
     * Why the tile is greyed right now (null: it works). [carousel]: the layout's carousel look is on (the game you're on is
     * always in the middle there - GameDock / TileHome ignore the setting); [pinnedFirst]: the Voltcap row's "Covers · first
     * one wide" (game 1 never moves from the left end - pinnedFirstOn). Both are "not right now, and you can change that".
     */
    fun greyReason(a: AppearanceSettings, carousel: Boolean, pinnedFirst: Boolean): String? = when {
        !applies(a.homeLayout) -> null
        carousel -> "With the carousel look on, the game you're on always stays in the middle. Switch the carousel off (Carousel tile) to choose where it stays."
        a.homeLayout == HomeLayout.CLASSIC && pinnedFirst ->
            "With \"Covers · first one wide\" the first game stays at the left end. Pick another \"It shows as\" (Settings › Home › Layout options) to choose where the game you're on stays."
        else -> null
    }
}
