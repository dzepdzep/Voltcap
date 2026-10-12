package com.shelf.launcher.ui

import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.CardSize
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout

/*
 * The R1 Size tile for every layout (update 104). It used to only change the Voltcap row's card size, so on every other
 * layout it seemed to do nothing. Now it changes the size the layout in use really draws its games at: the row's card
 * size, the Tile home's tile size, the Cross menu's / Icon bar's own size, or (the other layouts) their game size
 * (Features.layoutScale, read with [scale]).
 */
internal object GameSize {
    /** Small / Medium / Large as a percentage. */
    val STEPS = intArrayOf(85, 100, 120)
    const val MIN = 70
    const val MAX = 140

    /** The size of the games in [layout] (1 = normal); the layouts without their own size read this. */
    fun scale(layout: HomeLayout): Float = (pctFor(layout) / 100f)

    private fun parse(raw: String): Map<String, Int> = raw.split(';').mapNotNull { p ->
        val i = p.indexOf('='); if (i <= 0) null else p.substring(0, i) to (p.substring(i + 1).toIntOrNull() ?: return@mapNotNull null)
    }.toMap()

    private fun pctFor(layout: HomeLayout): Int = (parse(Features.layoutScale.state.value)[layout.name] ?: 100).coerceIn(fineRange(layout))

    /** Layouts whose Small / Medium / Large are steps of their own (the row / grid's card size, and the paged layouts,
     *  whose games fill the page: there the size is how many fit on it). Their % slider fine-tunes on top (update 105). */
    fun stepsOnly(layout: HomeLayout): Boolean = layout in setOf(HomeLayout.CLASSIC, HomeLayout.GRID, HomeLayout.CHAN, HomeLayout.LIVE)

    /** The paged layouts: their games fill the page, so the % only makes them smaller in it. */
    fun paged(layout: HomeLayout): Boolean = layout == HomeLayout.CHAN || layout == HomeLayout.LIVE

    /** The % slider's range for [layout]. */
    fun fineRange(layout: HomeLayout): IntRange = if (paged(layout) || layout == HomeLayout.TV) 60..100 else MIN..MAX

    /** The % size of [layout]'s games (update 105): on the step layouts it's on top of Small / Medium / Large. */
    fun fine(layout: HomeLayout): Int = pctFor(layout)

    /** Sets [layout]'s % size. */
    fun setFine(layout: HomeLayout, pct: Int) {
        val v = ((pct / 5) * 5).coerceIn(fineRange(layout))
        val m = parse(Features.layoutScale.value).toMutableMap()
        if (v == 100) m.remove(layout.name) else m[layout.name] = v
        Features.layoutScale.value = m.entries.joinToString(";") { "${it.key}=${it.value}" }
    }

    /** What the % slider shows and sets for [a]'s layout: its own size where it has one (Tile home, Cross menu, Icon
     *  bar), else the per-layout % size. */
    fun sliderPct(a: AppearanceSettings): Int = when (a.homeLayout) {
        HomeLayout.TILES -> a.tileScale
        HomeLayout.CROSS -> Features.crossScale.state.value
        HomeLayout.BAR -> Features.barTile.state.value
        else -> fine(a.homeLayout)
    }

    fun sliderRange(layout: HomeLayout): IntRange = when (layout) {
        HomeLayout.TILES -> com.shelf.launcher.data.TILE_SCALE_MIN..com.shelf.launcher.data.TILE_SCALE_MAX
        HomeLayout.CROSS, HomeLayout.BAR -> MIN..MAX
        else -> fineRange(layout)
    }

    fun setSlider(a: AppearanceSettings, pct: Int, apply: (AppearanceSettings) -> Unit) {
        when (a.homeLayout) {
            HomeLayout.TILES, HomeLayout.CROSS, HomeLayout.BAR -> set(a, pct, apply)
            else -> setFine(a.homeLayout, pct)
        }
    }

    /** The size of [layout] now, as a percentage. */
    fun pct(a: AppearanceSettings): Int = when (a.homeLayout) {
        HomeLayout.CLASSIC, HomeLayout.GRID -> when (a.cardSize) { CardSize.SMALL -> 85; CardSize.LARGE -> 120; CardSize.MEDIUM -> 100 }
        HomeLayout.CHAN -> when (Features.chanCols.state.value) { 3 -> 120; 4 -> 100; 5 -> 85; else -> 75 }
        HomeLayout.LIVE -> when (Features.livePage.state.value) { Features.LivePage.SIX -> 85; Features.LivePage.BIG_FOUR -> 100; Features.LivePage.BIG_TWO -> 120 }
        HomeLayout.TILES -> a.tileScale
        HomeLayout.CROSS -> Features.crossScale.state.value
        HomeLayout.BAR -> Features.barTile.state.value
        else -> pctFor(a.homeLayout)
    }

    /** Sets [layout]'s size to [pct] (the row gets the nearest of its three sizes). */
    fun set(a: AppearanceSettings, pct: Int, apply: (AppearanceSettings) -> Unit) {
        val v = if (a.homeLayout == HomeLayout.TILES) pct else pct.coerceIn(MIN, MAX)
        when (a.homeLayout) {
            HomeLayout.CHAN -> Features.chanCols.value = when { v < 93 -> 5; v > 110 -> 3; else -> 4 }
            HomeLayout.LIVE -> Features.livePage.value = when { v < 93 -> Features.LivePage.SIX; v > 110 -> Features.LivePage.BIG_TWO; else -> Features.LivePage.BIG_FOUR }
            HomeLayout.CLASSIC, HomeLayout.GRID -> {
                val pick = when { v < 93 -> CardSize.SMALL; v > 110 -> CardSize.LARGE; else -> CardSize.MEDIUM }
                apply(a.copy(cardSize = pick))
            }
            HomeLayout.TILES -> apply(a.copy(tileScale = ((v / 5) * 5).coerceIn(com.shelf.launcher.data.TILE_SCALE_MIN, com.shelf.launcher.data.TILE_SCALE_MAX)))
            HomeLayout.CROSS -> Features.crossScale.value = v
            HomeLayout.BAR -> Features.barTile.value = v
            else -> setFine(a.homeLayout, v)
        }
    }

    /** Small → Medium → Large → Small. */
    fun step(a: AppearanceSettings, apply: (AppearanceSettings) -> Unit) {
        val now = pct(a)
        val next = STEPS.firstOrNull { it > now + 2 } ?: STEPS.first()
        set(a, next, apply)
    }

    fun label(a: AppearanceSettings): String {
        val p = pct(a)
        val f = fine(a.homeLayout)
        return when {
            p < 93 -> "Small"
            p > 110 -> "Large"
            else -> "Medium"
        } + when {
            stepsOnly(a.homeLayout) && f != 100 -> " $f%"
            !stepsOnly(a.homeLayout) && p !in STEPS -> " $p%"
            else -> ""
        }
    }
}
