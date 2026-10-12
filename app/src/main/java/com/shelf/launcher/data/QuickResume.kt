package com.shelf.launcher.data

import android.content.Context
import android.content.Intent

/**
 * Quick resume: starting the game you just left, while its emulator is still alive in the
 * background, brings that emulator back to the front instead of booting the game again — you're
 * exactly where you were, like a console's suspend. Off when the emulator was closed (or Android
 * killed it), and for anything else: then the game starts normally.
 */
object QuickResume {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("quick_resume", Context.MODE_PRIVATE)

    fun noteLaunched(context: Context, gameKey: String, pkg: String?) {
        prefs(context).edit().putString("key", gameKey).putString("pkg", pkg).putLong("at", System.currentTimeMillis()).apply()
    }

    /** The emulator of [gameKey] is still running from the last launch. Blocking (root check). */
    fun canResume(context: Context, gameKey: String, pkg: String?): Boolean {
        if (pkg == null) return false
        val p = prefs(context)
        if (p.getString("key", null) != gameKey || p.getString("pkg", null) != pkg) return false
        val running = com.shelf.launcher.device.RootShell.isRunning(pkg)
        if (running != null) return running
        // Without the root service: trust a recent session (Android rarely kills a backgrounded
        // emulator within a few minutes) — the emulator restarts the game itself if it was killed.
        val ended = EmulatorGuard.endedAt(context)
        return ended > 0L && System.currentTimeMillis() - ended < 10 * 60_000L
    }

    /** Brings the running emulator's task to the front without restarting it. */
    fun resume(context: Context, pkg: String): Boolean = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        // No CLEAR_TASK / CLEAR_TOP: those would end the running game.
        intent.flags = intent.flags and (Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).inv()
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
