package com.shelf.launcher.ui

import com.shelf.launcher.data.Game

/**
 * Update 393: ONE source for how thick a game's case is, against its height. Case3D (the selected / pulled-out case) takes it
 * from the case's spec (CaseCatalog: console, region, multi-disc case); the Game shelf, Cardboard box and Cover details used
 * a per-console spine guess instead, so a case changed thickness the moment it was selected and neighbours sat with gaps.
 */
internal object CaseThickness {
    /** Thickness / height of [g]'s case as Case3D draws it, or null where no measured spec exists (use [fallback]). */
    fun of(g: Game): Float? = CaseCatalog.forGame(g)?.takeIf { it.check != CaseCheck.PLACEHOLDER }?.depth?.let(::clamp)

    fun clamp(depth: Float): Float = depth.coerceIn(MIN_DEPTH, MAX_DEPTH)

    /** [of] else [fallback] (a spine shape), so every layout agrees with the 3D case. */
    fun orElse(g: Game, fallback: Float): Float = of(g) ?: fallback

    /**
     * Width of a spine picture of shape [scan] (width / height) put in a slot of shape [slot]: a scan within [SNAP] of the
     * slot's shape fills it (cases then touch, no hairline gaps); one further off keeps its own authentic shape inside the
     * slot (never wider than it) — an intentional size difference stays visible.
     */
    fun spineFill(slot: Float, scan: Float): Float =
        if (scan >= slot / SNAP && scan <= slot * SNAP) slot else minOf(slot, scan)

    const val MIN_DEPTH = 0.05f
    const val MAX_DEPTH = 0.26f
    const val SNAP = 1.15f

    /** Narrowest a real-size spine slot gets (dp). The old 12 dp made the slot of a 40 dp tall case 2-3 times as wide as its spine. */
    const val MIN_SPINE_DP = 6f
}
