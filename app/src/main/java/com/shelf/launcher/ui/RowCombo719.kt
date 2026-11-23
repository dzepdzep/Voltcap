package com.shelf.launcher.ui

import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.CategoryBar
import com.shelf.launcher.data.CategoryBars
import com.shelf.launcher.data.DockPosition
import com.shelf.launcher.data.RowInfo

/**
 * 719a (owner 22:43, Task 1): 716a (the Voltcap row's column never starts under the status bar) passed with the default
 * settings only. Its real case is a combination: the game header on the home screen + Game info / news next to the row + the
 * category bar of every category + a song in the status bar's music player, at x1.3 text. These helpers let a QA run set
 * that combination as a preview (`--es voltcap_test_rowcombo on`, nothing saved) and read where the column landed
 * (`--ez voltcap_test_rowcol true` -> one `rowcol ...` QA log line), graded by tools/qa/tools/row716.py.
 */
object RowCombo719 {
    /** [a] with Game info + news next to the row and the category bar of every category on [a]'s layout (its own choice too). */
    fun appearance(a: AppearanceSettings): AppearanceSettings {
        val m = CategoryBars.parse(a.categoryBarByLayout).toMutableMap()
        m[a.homeLayout] = CategoryBar.FULL
        return a.copy(rowInfo = RowInfo.BOTH, categoryBar = CategoryBar.FULL, categoryBarByLayout = CategoryBars.format(m))
    }

    /**
     * The QA line (logged as `rowcol <this>`), all in dp, one decimal: `CLASSIC MIDDLE top=52.0 bottom=410.0 bar=48.0
     * screen=448.0 legend=on header=on info=BOTH cats=FULL player=on`. [barPx] is the status bar's bottom edge when the bar
     * runs along the top (0: off or at the bottom, as with Row place Top). Pure.
     */
    fun line(
        layout: String, place: DockPosition, topPx: Float, bottomPx: Float, barPx: Float, screenPx: Float, density: Float,
        legend: Boolean, header: Boolean, info: RowInfo, cats: CategoryBar, player: Boolean
    ): String {
        fun dp(px: Float) = "%.1f".format(java.util.Locale.US, px / density.coerceAtLeast(0.1f))
        fun on(b: Boolean) = if (b) "on" else "off"
        return "$layout ${place.name} top=${dp(topPx)} bottom=${dp(bottomPx)} bar=${dp(barPx)} screen=${dp(screenPx)} " +
            "legend=${on(legend)} header=${on(header)} info=${info.name} cats=${cats.name} player=${on(player)}"
    }
}
