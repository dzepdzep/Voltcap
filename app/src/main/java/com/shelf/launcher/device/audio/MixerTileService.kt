package com.shelf.launcher.device.audio

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.shelf.launcher.device.tune.openAndCollapse

/**
 * Android Quick Settings tile for the volume mixer: a tap opens it as a small card over what's on screen
 * (a game included). Shows how many apps have their own volume.
 */
@RequiresApi(Build.VERSION_CODES.N)
class MixerTileService : TileService() {
    // Settings › Device › Quick Settings tiles shows which tiles are in Android's menu.
    override fun onTileAdded() { super.onTileAdded(); com.shelf.launcher.device.QsTiles.mark(this, "mixer", true) }
    override fun onTileRemoved() { super.onTileRemoved(); com.shelf.launcher.device.QsTiles.mark(this, "mixer", false) }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        // Update 144: apps uninstalled since don't count (they can't be seen or cleared in the mixer's list).
        val set = AppMixer.volumes(applicationContext).keys.count { pkg ->
            runCatching { packageManager.getApplicationInfo(pkg, 0) }.isSuccess
        }
        tile.label = "Volume mixer"
        tile.state = if (set > 0) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (set > 0) "$set app${if (set == 1) "" else "s"} set" else "Per app"
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        openAndCollapse(this, AppMixer.openIntent(applicationContext))
    }
}
