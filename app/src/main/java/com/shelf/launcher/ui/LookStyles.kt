package com.shelf.launcher.ui

import com.shelf.launcher.data.Features

/*
 * Update 129: one console's disc, case or spine for every game — picked on the R1 panel's Disc tile (Discs only look),
 * Case tile (Real cases looks) or Spine tile (Spines look); "" (Own) = each game's own, as before. The cartridges have
 * theirs on the Cartridge tile (CustomCarts.kt).
 */
internal object LookStyles {
    /** The consoles whose disc (or UMD) can be every game's. */
    val DISCS = listOf(
        "PS1", "PS2", "PS3", "PS4", "PSP", "Saturn", "Dreamcast", "SegaCD", "GameCube", "Wii", "WiiU",
        "NeoGeoCD", "PCEngineCD", "3DO", "Xbox", "Xbox360"
    )

    /** The consoles whose case can be every game's (the ones with a case picture). */
    fun cases(): List<String> = MediaCovers.keys().filter { it != "App" }

    /** The consoles whose spine can be every game's. */
    fun spines(): List<String> = CustomSpines.keys().filter { it != "App" }

    /*
     * Update 326 (R1 audit): a pick applies only where its R1 tile does — the layout and look in use (r1TileFit's rule
     * for "discstyle" / "casestyle" / "spinestyle"). Before, a Case or Spine picked for every game in the Cases / Spines
     * look stayed on after switching to another look: the tile was hidden there, but the 3D cases in the game window,
     * Inspect and the carousels still all wore the picked console's case / spine. The pick itself is kept and applies
     * again as soon as its look is back ([sync], called by the home screen whenever the layout / look changes).
     */
    private val discLive = androidx.compose.runtime.mutableStateOf(true)
    private val caseLive = androidx.compose.runtime.mutableStateOf(true)
    private val spineLive = androidx.compose.runtime.mutableStateOf(true)

    /** Update 326: whether each pick applies with [a] (the home screen's settings) — the R1 tiles' own rule. */
    fun liveFor(a: com.shelf.launcher.data.AppearanceSettings): Triple<Boolean, Boolean, Boolean> {
        val x = R1Ctx(a, secondDisplay = false, bluetooth = false, lights = false, romm = false, raAccounts = 0,
            themeRecolorable = false, chargeLimitWorks = false)
        return Triple(r1TileFit("discstyle", x) != R1Fit.Hide, r1TileFit("casestyle", x) != R1Fit.Hide, r1TileFit("spinestyle", x) != R1Fit.Hide)
    }

    /** Update 326: the home screen hands over [liveFor]'s answer (main thread). */
    fun sync(live: Triple<Boolean, Boolean, Boolean>) {
        if (discLive.value != live.first) discLive.value = live.first
        if (caseLive.value != live.second) caseLive.value = live.second
        if (spineLive.value != live.third) spineLive.value = live.third
    }

    private fun discPick(): String = if (discLive.value) Features.discStyle.state.value else ""
    private fun casePick(): String = if (caseLive.value) Features.caseStyle.state.value else ""
    private fun spinePick(): String = if (spineLive.value) Features.spineStyle.state.value else ""

    /** The disc every game shows as with the Discs only look, or null (each its own). */
    fun disc(): String? = discPick().takeIf { it.isNotEmpty() && MediaShell.of(it) != null }

    /** The console whose case [platform]'s game is drawn in (its own unless one is picked for all). */
    fun casePlatform(platform: String?): String? =
        casePick().takeIf { it.isNotEmpty() && platform != null && (MediaCovers.of(it) != null || MediaTemplates.of(it)?.box != null) } ?: platform

    /** Whether one case is picked for all (the games' own case scans then don't show). */
    fun caseForAll(): Boolean = casePick().isNotEmpty()

    /** The console whose spine [platform]'s game gets (its own unless one is picked for all). */
    fun spinePlatform(platform: String?): String? =
        spinePick().takeIf { it.isNotEmpty() && CustomSpines.of(it) != null } ?: platform

    /** Whether one spine is picked for all (the games' own spine scans then don't show). */
    fun spineForAll(): Boolean = spinePick().isNotEmpty()

    /** The next of "" (own) and [list] after [now]. */
    fun next(now: String, list: List<String>): String {
        val all = listOf("") + list
        return all[(all.indexOf(now).coerceAtLeast(0) + 1) % all.size]
    }
}
