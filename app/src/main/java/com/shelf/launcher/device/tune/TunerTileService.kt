package com.shelf.launcher.device.tune

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/**
 * Android Quick Settings tile: cycles the device-wide power — the tiers (Stock → Max → Balanced → Power
 * saving → Eco), then AutoTDP Efficient / Balanced / Smooth — without opening Voltcap. Long press opens
 * Voltcap's settings (Android's own tile behaviour).
 */
@RequiresApi(Build.VERSION_CODES.N)
class TunerTileService : TileService() {
    // Settings › Device › Quick Settings tiles shows which tiles are in Android's menu.
    override fun onTileAdded() { super.onTileAdded(); com.shelf.launcher.device.QsTiles.mark(this, "power", true) }
    override fun onTileRemoved() { super.onTileRemoved(); com.shelf.launcher.device.QsTiles.mark(this, "power", false) }

    override fun onStartListening() {
        super.onStartListening()
        // The first look at the chip asks the root service: never on the shade's thread.
        Thread { runCatching { render() } }.start()
    }

    override fun onClick() {
        super.onClick()
        val app = applicationContext
        Thread {
            runCatching { Tuner.cycleTile(app) }
            runCatching { GameNotification.refresh(app) }
            runCatching { render() }
        }.start()
    }

    private fun render() {
        val tile = qsTile ?: return
        val p = TuneStore(this).global()
        tile.label = if (p.autoTdp) "AutoTDP ${p.autoMode.label}" else "Power: ${p.tier.label}"
        tile.state = if (!Tuner.supported) Tile.STATE_UNAVAILABLE else if (p.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (p.autoTdp) "AutoTDP ${p.autoMode.label}" else p.tier.blurb
        tile.updateTile()
    }
}
