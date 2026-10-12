package com.shelf.launcher.device.tune

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/**
 * Android Quick Settings tile for the underclock (ClusterTune / PULSE style): Off → Light → Medium → Heavy
 * (→ Custom once you've set your own speeds) → Off. Works mid-game: a game on the device-wide profile
 * takes the new ceilings at once. The subtitle shows the real top speeds on this chip. Long press opens
 * Voltcap's in-game panel (Android's tile settings), like the other Voltcap tiles.
 */
@RequiresApi(Build.VERSION_CODES.N)
class UnderclockTileService : TileService() {
    // Settings › Device › Quick Settings tiles shows which tiles are in Android's menu.
    override fun onTileAdded() { super.onTileAdded(); com.shelf.launcher.device.QsTiles.mark(this, "underclock", true) }
    override fun onTileRemoved() { super.onTileRemoved(); com.shelf.launcher.device.QsTiles.mark(this, "underclock", false) }

    override fun onStartListening() {
        super.onStartListening()
        // The first look at the chip asks the root service: never on the shade's thread.
        Thread { runCatching { render() } }.start()
    }

    override fun onClick() {
        super.onClick()
        val app = applicationContext
        // Reading the chip and writing clocks go through the root service (binder calls): off the tile's thread.
        Thread {
            if (!Tuner.supported) { runCatching { render() }; return@Thread }
            runCatching { Tuner.cycleUnderclock(app) }
            runCatching { GameNotification.refresh(app) }
            runCatching { render() }
        }.start()
    }

    private fun render() {
        val tile = qsTile ?: return
        val app = applicationContext
        val p = TuneStore(app).global()
        tile.label = when {
            p.underclock == Underclock.AUTO -> "Underclock: with power"
            p.underclock == Underclock.OFF -> "Underclock"
            else -> "Underclock: ${p.effectiveUnderclock.label}"
        }
        tile.state = when {
            !Tuner.supported -> Tile.STATE_UNAVAILABLE
            p.underclocked -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (!Tuner.supported) "Needs the handheld's root service"
            else runCatching { Tuner.underclockSummary(p) }.getOrDefault(p.effectiveUnderclock.label)
        }
        tile.updateTile()
    }
}
