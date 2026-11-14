package com.shelf.launcher.data

import android.content.Context

/** Whether a game started from Voltcap is still open (PlaytimeStore's open session). */
object PlaySession {
    /** The 8 h window matches PlaytimeStore's counted-session cap: a session left open longer is stale. */
    fun isOpen(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(Profiles.run { ensure(context); fileFor("playtime") }, Context.MODE_PRIVATE)
        val since = prefs.getLong("open_since", 0L)
        return prefs.getString("open_game", null) != null &&
            System.currentTimeMillis() - since < 8L * 60 * 60 * 1000
    }
}
