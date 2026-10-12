package com.shelf.launcher.data

import org.json.JSONObject

/**
 * Per-game look (Game settings › Look for this game): what changes on the home screen while this game
 * is the one you're on. Every field null = follow the global setting. Stored as JSON in the
 * "voltcap_features" prefs ([Features.gameLooks]) so it is backed up and shared like the other looks.
 *
 * Reads go through Compose state: a screen that calls [of] recomposes when a look changes.
 */
object GameLooks {

    /** Small mood layer over the background picture while this game is focused (no sound). */
    enum class Ambience(val label: String, val blurb: String) {
        MOTES("Particles", "Slow motes of light drifting up in the art's colours."),
        RAIN("Rain", "Fine rain streaks and a cool tint."),
        SNOW("Snow", "Soft flakes falling at different depths."),
        EMBERS("Embers", "Warm sparks rising from the bottom."),
        CRT_NOISE("CRT noise", "Scanlines, a rolling bar and a little static."),
        LIGHT("Art lighting", "A soft light in the art's own colours sweeping slowly across.")
    }

    data class Look(
        /** AppTheme name used as the animated background while this game is focused. */
        val theme: String? = null,
        val artPriority: Features.ArtPriority? = null,
        /** Play animated artwork for this game (null = the global Animated artwork setting). */
        val animateArt: Boolean? = null,
        /** CRT treatment (scanlines, curvature shade, glow) on this game's background picture. */
        val crt: Boolean? = null,
        val intensity: Features.Intensity? = null,
        val ambience: Ambience? = null,
        /** A state you set yourself; null = worked out from play time and achievements. */
        val state: GameState? = null,
        /** How the background art changes when you move onto this game (null = Appearance › Background art motion). */
        val transition: HeroMotion? = null
    ) {
        fun isEmpty() = this == Look()

        fun encode(): JSONObject = JSONObject().apply {
            theme?.let { put("theme", it) }
            artPriority?.let { put("art", it.name) }
            animateArt?.let { put("anim", it) }
            crt?.let { put("crt", it) }
            intensity?.let { put("int", it.name) }
            ambience?.let { put("amb", it.name) }
            state?.let { put("state", it.name) }
            transition?.let { put("tr", it.name) }
        }

        companion object {
            fun decode(o: JSONObject) = Look(
                theme = o.optString("theme").takeIf { it.isNotEmpty() },
                artPriority = Features.ArtPriority.values().firstOrNull { it.name == o.optString("art") },
                animateArt = if (o.has("anim")) o.optBoolean("anim") else null,
                crt = if (o.has("crt")) o.optBoolean("crt") else null,
                intensity = Features.Intensity.values().firstOrNull { it.name == o.optString("int") },
                ambience = Ambience.values().firstOrNull { it.name == o.optString("amb") },
                state = GameState.values().firstOrNull { it.name == o.optString("state") },
                transition = HeroMotion.values().firstOrNull { it.name == o.optString("tr") }
            )
        }
    }

    private var cacheKey: String? = null
    private var cache: Map<String, Look> = emptyMap()

    private fun all(): Map<String, Look> {
        val raw = Features.gameLooks.state.value
        if (raw != cacheKey) {
            cache = runCatching {
                val o = JSONObject(raw)
                o.keys().asSequence().associateWith { k -> Look.decode(o.getJSONObject(k)) }
            }.getOrDefault(emptyMap())
            cacheKey = raw
        }
        return cache
    }

    /** This game's look ([key] = Game.stableKey); an empty [Look] when nothing is set. */
    fun of(key: String?): Look = key?.let { all()[it] } ?: Look()

    fun update(key: String, change: (Look) -> Look) {
        val next = change(of(key))
        val o = runCatching { JSONObject(Features.gameLooks.value) }.getOrDefault(JSONObject())
        if (next.isEmpty()) o.remove(key) else o.put(key, next.encode())
        Features.gameLooks.value = o.toString()
    }

    fun reset(key: String) = update(key) { Look() }

    /** Artwork priority for this game (its own, else the global one). */
    fun artPriority(key: String?): Features.ArtPriority = of(key).artPriority ?: Features.artPriority.state.value

    /** The background transition for this game (its own, else [global]). */
    fun transition(key: String?, global: HeroMotion): HeroMotion = of(key).transition ?: global

    /** Theme intensity for this game (its own, else the global one). */
    fun intensity(key: String?): Features.Intensity = of(key).intensity ?: Features.themeIntensity.state.value
}

/** The small state mark on covers (Settings › Library › Game states). */
enum class GameState(val label: String) {
    NEW("NEW"),
    PLAYING("PLAYING"),
    ON_HOLD("ON HOLD"),
    COMPLETED("COMPLETED"),
    MASTERED("MASTERED"),
    PERFECT("100%");

    companion object {
        private const val DAY = 24L * 60 * 60 * 1000

        /**
         * The state worked out for one game. Your own pick (Game settings) wins; then, strongest first:
         * mastered (RetroAchievements), 100 % (every achievement), completed (beaten or marked Finished),
         * playing (running now, marked Playing, or played in the last week), on hold (marked Playing but
         * untouched for a month), new (just added, never played). Null = no mark.
         */
        fun derive(
            manual: GameState?,
            award: RaAward?,
            progress: RaGameProgress?,
            status: PlayStatus?,
            lastPlayed: Long?,
            running: Boolean,
            isNewArrival: Boolean,
            now: Long = System.currentTimeMillis()
        ): GameState? {
            manual?.let { return it }
            if (award == RaAward.MASTERED) return MASTERED
            val total = progress?.NumPossibleAchievements ?: 0
            val got = progress?.NumAchieved ?: 0
            if (award == RaAward.COMPLETED || (total > 0 && got >= total)) return PERFECT
            if (award == RaAward.BEATEN || award == RaAward.BEATEN_HARDCORE || status == PlayStatus.FINISHED) return COMPLETED
            val age = lastPlayed?.let { now - it }
            if (running || (age != null && age < 7 * DAY)) return PLAYING
            if (status == PlayStatus.PLAYING) return if (age != null && age > 30 * DAY) ON_HOLD else PLAYING
            if (isNewArrival && lastPlayed == null) return NEW
            return null
        }
    }
}
