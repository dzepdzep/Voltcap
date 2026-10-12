package com.shelf.launcher.data

import android.content.Context
import com.shelf.launcher.ui.theme.AppTheme

/**
 * Favourite-theme rotation (Settings › Appearance › Theme): switches to a random favourite theme on
 * app start, after a set time, or after a number of game launches, limited to the kinds picked
 * (Static / Calm / Animated). Never picks the theme already in use. Its own bookkeeping (when it last
 * switched, launches since) lives in the internal "theme_rotation_state" file, never exported.
 */
object ThemeRotator {
    private const val PREFS = "theme_rotation_state"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun pick(current: AppTheme): AppTheme? {
        val favs = Features.favoriteThemes.value
        val kinds = Features.themeRotationKinds.value
        return AppTheme.values()
            .filter { it.name in favs && it != current && (kinds.isEmpty() || it.kind.name in kinds) }
            .randomOrNull()
    }

    private fun switched(context: Context) {
        prefs(context).edit().putLong("last", System.currentTimeMillis()).putInt("launches", 0).apply()
    }

    /** At app start: a new favourite when rotation is "on app start". */
    fun onAppStart(context: Context, current: AppTheme): AppTheme? {
        if (Features.themeRotation.value != Features.ThemeRotation.ON_START) return null
        return pick(current)?.also { switched(context) }
    }

    /** Every game launch counts; after the set number, a new favourite. */
    fun onLaunch(context: Context, current: AppTheme): AppTheme? {
        if (Features.themeRotation.value != Features.ThemeRotation.LAUNCHES) return null
        val n = prefs(context).getInt("launches", 0) + 1
        if (n < Features.themeRotationLaunches.value) {
            prefs(context).edit().putInt("launches", n).apply()
            return null
        }
        return pick(current)?.also { switched(context) } ?: run { prefs(context).edit().putInt("launches", n).apply(); null }
    }

    /** Checked every minute and when Voltcap comes back: after the set time, a new favourite. */
    fun onTick(context: Context, current: AppTheme): AppTheme? {
        if (Features.themeRotation.value != Features.ThemeRotation.TIMED) return null
        val last = prefs(context).getLong("last", 0L)
        val now = System.currentTimeMillis()
        if (last == 0L) { prefs(context).edit().putLong("last", now).apply(); return null }
        if (now - last < Features.themeRotationMinutes.value * 60_000L) return null
        return pick(current)?.also { switched(context) }
    }
}
