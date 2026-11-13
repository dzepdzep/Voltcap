package com.shelf.launcher.data

import android.content.Context

/** Whether a game started from Voltcap is still open (PlaytimeStore's open session). */
object PlaySession {
    /** The 8 h window matches PlaytimeStore's counted-session cap: a session left open longer is stale. */
    fun isOpen(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(Profiles.run { ensure(context); fileFor("playtime") }, Context.MODE_PRIVATE)
        val since = prefs.getLong("open_since", 0L)
        val now = System.currentTimeMillis()
        // (Update 529c: a session from before this boot can't still be running - the emulator went down with the handheld - so the
        // background sync, downloads included, isn't held back for 8 hours after a restart.)
        return prefs.getString("open_game", null) != null && now - since < 8L * 60 * 60 * 1000 &&
            !BootSession.startedBeforeBoot(since, now, android.os.SystemClock.elapsedRealtime())
    }
}
