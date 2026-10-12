package com.shelf.launcher.data

import android.content.Context
import com.shelf.launcher.device.RootShell

/**
 * Keeps Drive from writing into saves while an emulator may still have them open.
 *
 * Coming back to Voltcap ends the play session, but the emulator usually stays alive in the
 * background with the game (and its save) in memory — a save downloaded under it would be
 * overwritten again, or confuse it. So after a game, local saves are only replaced once the
 * emulator's process is known to be gone (checked as root on devices that allow it, e.g. AYN),
 * or — where that can't be checked — once [GRACE_MS] has passed since the game was left.
 * Uploads are never held back: reading a save is safe.
 */
object EmulatorGuard {
    private const val PREFS = "emulator_guard"
    const val GRACE_MS = 15 * 60_000L

    /** A game was started with [packageName] (the emulator, or the app itself). */
    fun noteLaunched(context: Context, packageName: String?) {
        prefs(context).edit()
            .putString("pkg", packageName)
            .putLong("ended_at", 0L)
            .putLong("launched_at", System.currentTimeMillis())
            .apply()
    }

    /** When the last session ended (0 = still open / unknown). */
    fun endedAt(context: Context): Long = prefs(context).getLong("ended_at", 0L)

    /** Voltcap is back in front: the session ended now (the emulator may live on). */
    fun noteSessionEnded(context: Context) {
        val p = prefs(context)
        if (p.getString("pkg", null) != null && p.getLong("ended_at", 0L) == 0L) {
            p.edit().putLong("ended_at", System.currentTimeMillis()).apply()
        }
    }

    /**
     * Whether local saves may be replaced right now. The reason (shown to the player) when not.
     * Blocking: may ask the root service (a few ms) — call off the main thread.
     */
    fun downloadsBlocked(context: Context): String? {
        if (PlaySession.isOpen(context)) return "A game is open"
        val p = prefs(context)
        val pkg = p.getString("pkg", null) ?: return null
        val label = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault("The emulator")
        return when (RootShell.isRunning(pkg)) {
            true -> "$label is still running in the background"
            false -> { clear(context); null }
            null -> {
                // Never told the session ended (Voltcap was closed meanwhile): count from the launch.
                val from = p.getLong("ended_at", 0L).takeIf { it > 0L } ?: p.getLong("launched_at", 0L)
                val since = System.currentTimeMillis() - from
                if (since in 0 until GRACE_MS) "$label may still be open in the background"
                else { clear(context); null }
            }
        }
    }

    private fun clear(context: Context) {
        prefs(context).edit().remove("pkg").remove("ended_at").remove("launched_at").apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
