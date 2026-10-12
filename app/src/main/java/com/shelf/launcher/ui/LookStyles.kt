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

    /** The disc every game shows as with the Discs only look, or null (each its own). */
    fun disc(): String? = Features.discStyle.state.value.takeIf { it.isNotEmpty() && MediaShell.of(it) != null }

    /** The console whose case [platform]'s game is drawn in (its own unless one is picked for all). */
    fun casePlatform(platform: String?): String? =
        Features.caseStyle.state.value.takeIf { it.isNotEmpty() && platform != null && (MediaCovers.of(it) != null || MediaTemplates.of(it)?.box != null) } ?: platform

    /** Whether one case is picked for all (the games' own case scans then don't show). */
    fun caseForAll(): Boolean = Features.caseStyle.state.value.isNotEmpty()

    /** The console whose spine [platform]'s game gets (its own unless one is picked for all). */
    fun spinePlatform(platform: String?): String? =
        Features.spineStyle.state.value.takeIf { it.isNotEmpty() && CustomSpines.of(it) != null } ?: platform

    /** Whether one spine is picked for all (the games' own spine scans then don't show). */
    fun spineForAll(): Boolean = Features.spineStyle.state.value.isNotEmpty()

    /** The next of "" (own) and [list] after [now]. */
    fun next(now: String, list: List<String>): String {
        val all = listOf("") + list
        return all[(all.indexOf(now).coerceAtLeast(0) + 1) % all.size]
    }
}
