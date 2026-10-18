package com.shelf.launcher.device.tune

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.shelf.launcher.data.perf.PerfSettings

/**
 * Quick Settings tile for the overlays over games: Off → Stats → Music → Trophies → All, like the R1
 * panel's Overlay tile. Works mid-game: a running overlay picks the change up at once.
 */
@RequiresApi(Build.VERSION_CODES.N)
class OverlayTileService : TileService() {
    // Settings › Device › Quick Settings tiles shows which tiles are in Android's menu.
    override fun onTileAdded() { super.onTileAdded(); com.shelf.launcher.device.QsTiles.mark(this, "overlay", true) }
    override fun onTileRemoved() { super.onTileRemoved(); com.shelf.launcher.device.QsTiles.mark(this, "overlay", false) }

    override fun onStartListening() {
        super.onStartListening()
        listening = java.lang.ref.WeakReference(this) // (Update 241, #82) the overlay service redraws it when it's up / down
        render()
    }

    override fun onStopListening() {
        if (listening?.get() === this) listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        // Without Android's "Display over other apps" nothing can show: open that switch instead of
        // pretending the overlay is on.
        if (!com.shelf.launcher.data.Permissions.granted(applicationContext, com.shelf.launcher.data.VoltPermission.OVERLAY)) {
            com.shelf.launcher.data.Permissions.settingsIntent(applicationContext, com.shelf.launcher.data.VoltPermission.OVERLAY)
                ?.let { openAndCollapse(this, it) }
            return
        }
        val c = PerfSettings.config(applicationContext)
        val next = when (mode(c.showDuringGames, c.musicDuringGames, c.raDuringGames)) {
            "Off" -> Triple(true, false, false)
            "Stats" -> Triple(false, true, false)
            "Music" -> Triple(false, false, true)
            "Trophies" -> Triple(true, true, true)
            else -> Triple(false, false, false)
        }
        PerfSettings.update(applicationContext) { it.copy(showDuringGames = next.first, musicDuringGames = next.second, raDuringGames = next.third) }
        // (Update 241, #82: this called PerfOverlayService.reconfigure, which only acted while GameMode.active — false for a
        // game started outside Voltcap, after Android ended Voltcap's idle process, or on the Thor once its other screen was
        // touched — and dropped a refused background start without a word. So a tap mid-game only changed the setting.)
        val inGame = gameInFront(applicationContext)
        val result = com.shelf.launcher.ui.PerfOverlayService.applyNow(applicationContext, inGame)
        if (result == com.shelf.launcher.ui.PerfOverlayService.Applied.REFUSED) {
            // Android wouldn't start the overlay service from the background (Android 12+ rules; on 15+ "Display over other
            // apps" alone no longer exempts it). An activity Voltcap opens is allowed to: a see-through hop that starts it and
            // closes at once, and the shade collapses so the overlay is seen.
            android.util.Log.i("OverlayTile", "start refused from the tile: starting through OverlayControlActivity (Update 241, #82)")
            openAndCollapse(this, android.content.Intent(this, com.shelf.launcher.ui.OverlayControlActivity::class.java)
                .putExtra(com.shelf.launcher.ui.OverlayControlActivity.EXTRA_START_OVERLAYS, true))
        }
        render()
    }

    private fun mode(s: Boolean, m: Boolean, r: Boolean) = when {
        s && m && r -> "All"
        !s && !m && !r -> "Off"
        s && !m && !r -> "Stats"
        !s && m && !r -> "Music"
        !s && !m && r -> "Trophies"
        else -> listOfNotNull("Stats".takeIf { s }, "Music".takeIf { m }, "Trophies".takeIf { r }).joinToString("+")
    }

    private fun render() {
        val tile = qsTile ?: return
        val c = PerfSettings.config(this)
        val m = mode(c.showDuringGames, c.musicDuringGames, c.raDuringGames)
        val allowed = com.shelf.launcher.data.Permissions.granted(this, com.shelf.launcher.data.VoltPermission.OVERLAY)
        // (Update 241, #82) Only "on" when it really is on over the game: chosen but not up (refused, no trophies, no window)
        // shows the reason instead. Outside a game it's the choice for the next one.
        val showing = com.shelf.launcher.ui.PerfOverlayService.showing
        val inGame = showing || com.shelf.launcher.data.GameMode.active || com.shelf.launcher.data.PlaySession.isOpen(this)
        val note = com.shelf.launcher.ui.PerfOverlayService.lastNote
        val missing = allowed && m != "Off" && inGame && !showing && note != null
        tile.label = if (allowed) "Overlay: $m" else "Overlay"
        tile.state = if (!allowed || m == "Off" || missing) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        // Hold the tile for the panel (which overlays, size, opacity, moving them): Android's tile settings.
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = when {
            !allowed -> "Tap to allow"
            missing -> "$note · tap to retry"
            else -> "${c.overlayScale}% · ${c.overlayOpacity}% · hold for more"
        }
        tile.updateTile()
    }

    companion object {
        /** (Update 241, #82) The tile while the shade shows it. */
        @Volatile private var listening: java.lang.ref.WeakReference<OverlayTileService>? = null

        /** (Update 241, #82) Redraws the tile now if the shade shows it, else asks Android to when it opens next. */
        fun refresh(context: Context) {
            val t = listening?.get()
            if (t != null) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { runCatching { t.render() } }
                return
            }
            runCatching { TileService.requestListeningState(context, android.content.ComponentName(context, OverlayTileService::class.java)) }
        }

        /**
         * (Update 241, #82) A game (or any app but Voltcap) is in front, so the overlay may go up now. Voltcap's own game
         * mode first; else, with usage access, whether another app has a resumed activity (a game started outside
         * Voltcap, or Voltcap's process ended while it ran); else Voltcap's open play session (kept across that).
         */
        fun gameInFront(context: Context): Boolean {
            if (com.shelf.launcher.data.GameMode.active) return true
            return otherAppResumed(context) ?: com.shelf.launcher.data.PlaySession.isOpen(context)
        }

        /** Null without usage access. Voltcap itself and the system bars don't count. */
        @Suppress("DEPRECATION")
        private fun otherAppResumed(context: Context): Boolean? {
            if (!com.shelf.launcher.data.Permissions.usageAccess(context)) return null
            return runCatching {
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val now = System.currentTimeMillis()
                val events = usm.queryEvents(now - 8 * 60 * 60_000L, now) // PlaySession's 8 h cap
                val ev = UsageEvents.Event()
                val own = context.packageName
                val open = HashSet<String>()
                while (events.hasNextEvent()) {
                    events.getNextEvent(ev)
                    val pkg = ev.packageName ?: continue
                    if (pkg == own || pkg == "com.android.systemui") continue
                    val id = pkg + "/" + (ev.className ?: "")
                    when (ev.eventType) {
                        UsageEvents.Event.MOVE_TO_FOREGROUND -> open += id // = ACTIVITY_RESUMED
                        UsageEvents.Event.MOVE_TO_BACKGROUND, 23, 24 -> open -= id // ACTIVITY_PAUSED, ACTIVITY_STOPPED, ACTIVITY_DESTROYED
                    }
                }
                open.isNotEmpty()
            }.getOrNull()
        }
    }
}

/** Opens [intent] from a Quick Settings tile and closes the shade (Android 14 needs a PendingIntent). */
@RequiresApi(Build.VERSION_CODES.N)
internal fun openAndCollapse(service: TileService, intent: android.content.Intent) {
    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        if (Build.VERSION.SDK_INT >= 34) {
            service.startActivityAndCollapse(
                android.app.PendingIntent.getActivity(service, intent.hashCode(), intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else @Suppress("DEPRECATION") service.startActivityAndCollapse(intent)
    }
}
