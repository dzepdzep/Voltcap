package com.shelf.launcher.device.tune

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/**
 * Quick Settings tile that opens Voltcap's Quick menu (achievements, saved Bluetooth headsets and
 * controllers) over the game that is running, without leaving it.
 */
@RequiresApi(Build.VERSION_CODES.N)
class QuickMenuTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        // A cold process (launcher killed during a game) hasn't loaded the settings yet (update 91).
        com.shelf.launcher.data.Features.init(applicationContext)
        val tile = qsTile ?: return
        tile.label = "Quick menu"
        tile.state = if (com.shelf.launcher.data.Features.quickMenuInGame.state.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = "Achievements · Bluetooth"
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        openAndCollapse(this, Intent(this, com.shelf.launcher.ui.QuickMenuActivity::class.java))
    }
}
