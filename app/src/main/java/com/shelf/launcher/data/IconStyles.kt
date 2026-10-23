package com.shelf.launcher.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/**
 * Update 321 (Toni: icon pack tweaks): an app's own icon style, over Features.monoIconStyle — Detail, Solid, or its own
 * colours (the one-colour look left out for it). Set from the second screen dock's long-press menu. Prefs "icon_styles".
 */
object IconStyles {
    private const val PREFS = "icon_styles"
    private val styles = mutableStateMapOf<String, Features.MonoIconStyle>()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            Features.MonoIconStyle.values().firstOrNull { it.name == v }?.let { styles[k] = it }
        }
    }

    /** [pkg]'s style: its own choice, else the one for every app (Compose state). */
    fun of(pkg: String?): Features.MonoIconStyle = pkg?.let { styles[it] } ?: Features.monoIconStyle.state.value

    /** Whether [pkg] has a choice of its own. */
    fun hasOwn(pkg: String?): Boolean = pkg != null && styles.containsKey(pkg)

    /** [pkg]'s next style (Detail → Solid → Own colours → as every app), saved. */
    fun next(context: Context, pkg: String) {
        init(context)
        val order = listOf<Features.MonoIconStyle?>(Features.MonoIconStyle.DETAIL, Features.MonoIconStyle.SOLID, Features.MonoIconStyle.ORIGINAL, null)
        val n = order[(order.indexOf(styles[pkg]) + 1) % order.size]
        if (n == null) styles.remove(pkg) else styles[pkg] = n
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .apply { if (n == null) remove(pkg) else putString(pkg, n.name) }.apply()
    }
}
