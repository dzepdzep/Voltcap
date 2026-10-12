package com.shelf.launcher.data

/**
 * Kept so nothing breaks if this file is still in the project: the artwork priority lives in [ArtPick] now
 * (it adds the animated hero, Screenshot first and per-game choices). Prefer ArtPick.backdrop.
 */
fun preferredArtwork(priority: Features.ArtPriority, art: GameArt?, own: ArtOverride?, shots: List<String> = emptyList()): String? =
    ArtPick.backdrop(key = null, override = own, art = art, shots = shots, priority = priority)
