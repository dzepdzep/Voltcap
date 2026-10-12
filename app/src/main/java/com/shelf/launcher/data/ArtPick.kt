package com.shelf.launcher.data

import android.content.Context
import coil.request.ImageRequest

/**
 * One place that decides which picture is a game's background (Appearance › Artwork › Artwork priority,
 * or the game's own choice in Game settings). Used by the home background, the hero carousel, the game
 * popup, the scene colours and the second screen, so they all agree.
 *
 * A background you picked yourself (Artwork & details › Background) always wins: that is the manual
 * override. Otherwise, by priority:
 *  - Smart: animated hero (when animated artwork plays for this game) → hero → screenshot → wide capsule → cover
 *  - Hero first: hero → wide capsule → screenshot → cover
 *  - Screenshot first: screenshot → hero → wide capsule → cover
 *  - Cover first: cover (yours, then scraped) → hero → screenshot
 */
object ArtPick {

    /** Whether animated pictures play for this game (its own switch, else Animated artwork). */
    fun animates(key: String?): Boolean = GameLooks.of(key).animateArt ?: AnimatedArt.enabled

    fun backdrop(
        key: String?,
        override: ArtOverride?,
        art: GameArt?,
        shots: List<String>? = null,
        priority: Features.ArtPriority = GameLooks.artPriority(key)
    ): String? {
        val shot = shots?.firstOrNull()
        val hero = override?.hero ?: art?.heroUrl
        val wide = override?.wide ?: art?.wideGridUrl
        val cover = override?.cover ?: art?.gridUrl
        return when (priority) {
            Features.ArtPriority.SMART -> override?.hero
                ?: art?.animatedHeroUrl?.takeIf { animates(key) }
                ?: art?.heroUrl ?: shot ?: wide ?: cover
            Features.ArtPriority.HERO -> hero ?: wide ?: shot ?: cover
            Features.ArtPriority.SCREENSHOT -> override?.hero ?: shot ?: hero ?: wide ?: cover
            Features.ArtPriority.COVER -> cover ?: hero ?: shot ?: wide
        }
    }

    /** Same as [backdrop], reading the maps the UI state keeps. */
    fun backdrop(
        game: Game?,
        overrides: Map<String, ArtOverride>,
        art: Map<String, GameArt>,
        shots: Map<String, List<String>>
    ): String? {
        val key = game?.stableKey ?: return null
        return backdrop(key, overrides[key], art[key], shots[key])
    }

    /**
     * A per-game Animated artwork choice for this picture's request: forces it to play (or to stay
     * still) whatever the global switch says. Nothing is added when the game has no choice of its own.
     */
    fun ImageRequest.Builder.animationFor(key: String?): ImageRequest.Builder {
        when (GameLooks.of(key).animateArt) {
            true -> setParameter(AnimatedArt.PARAM_ANIMATE, true, memoryCacheKey = "anim")
            false -> setParameter(AnimatedArt.PARAM_STILL, true, memoryCacheKey = "still")
            null -> Unit
        }
        return this
    }

    /** A plain request for [url] that honours the game's own animation choice (or just [url] when it has none). */
    fun model(context: Context, url: String?, key: String?): Any? {
        if (url == null || GameLooks.of(key).animateArt == null) return url
        return ImageRequest.Builder(context).data(url).animationFor(key).build()
    }
}
