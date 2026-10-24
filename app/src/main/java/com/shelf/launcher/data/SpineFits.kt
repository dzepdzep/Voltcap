package com.shelf.launcher.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/**
 * Update 321 (Toni, after many rounds of spine fitting: one rule for every scan keeps getting some wrong): a game's own
 * choice of how its spine scan goes onto its 3D case's side — the game's menu › Spine fit. Auto is the usual fitting
 * (Case3D); the others are for the odd scan that comes out wrong. Prefs "spine_fit" (stableKey → mode name).
 */
object SpineFits {
    enum class Mode(val label: String, val about: String) {
        AUTO("Auto", "Fitted the usual way"),
        FILL("Fill", "Zoomed to fill the side; its ends or edges may be cut"),
        WHOLE("Whole", "The whole scan inside the side, never cut"),
        STRETCH("Stretch", "Stretched across the side, its whole height kept")
    }

    private const val PREFS = "spine_fit"
    private val modes = mutableStateMapOf<String, Mode>()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            Mode.values().firstOrNull { it.name == v }?.let { modes[k] = it }
        }
    }

    /** [key]'s choice (Compose state: a case redraws when it changes). */
    fun of(key: String?): Mode = key?.let { modes[it] } ?: Mode.AUTO

    /** The next choice for [key], saved. */
    fun next(context: Context, key: String) {
        init(context)
        val n = Mode.values()[(of(key).ordinal + 1) % Mode.values().size]
        if (n == Mode.AUTO) modes.remove(key) else modes[key] = n
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .apply { if (n == Mode.AUTO) remove(key) else putString(key, n.name) }.apply()
    }
}
