package com.shelf.launcher.device.tune

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
        render()
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
        com.shelf.launcher.ui.PerfOverlayService.reconfigure(applicationContext)
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
        tile.label = if (allowed) "Overlay: $m" else "Overlay"
        tile.state = if (!allowed || m == "Off") Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        // Hold the tile for the panel (which overlays, size, opacity, moving them): Android's tile settings.
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (allowed) "${c.overlayScale}% · ${c.overlayOpacity}% · hold for more" else "Tap to allow"
        tile.updateTile()
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
