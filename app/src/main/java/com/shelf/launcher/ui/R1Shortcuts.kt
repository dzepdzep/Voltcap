package com.shelf.launcher.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * R1 tiles as L1 shortcuts. A shortcut is stored like Voltcap's other own screens ("voltcap:r1:<tile id>",
 * see BuiltinShortcuts). Tapping it opens the R1 panel and presses that tile once: the panel builds every
 * tile with its current state, so the tile does exactly what it does when tapped there.
 */
internal object R1Shortcut {
    const val PREFIX = "voltcap:r1:"

    /** The tile to press as soon as the R1 panel is up (set by the L1 panel, taken by the R1 panel). */
    var pending by mutableStateOf<String?>(null)

    fun tileOf(id: String?): String? = id?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)

    fun icon(id: String?): ImageVector = when (tileOf(id)) {
        "wifi" -> Icons.Filled.Wifi
        "bt" -> Icons.Filled.Bluetooth
        "dnd" -> Icons.Filled.DoNotDisturbOn
        "lights" -> Icons.Filled.Lightbulb
        "music" -> Icons.Filled.MusicNote
        "musicspot" -> Icons.Filled.MusicNote
        "saves" -> Icons.Filled.SdCard
        "edithome" -> Icons.Filled.OpenWith
        "dual" -> Icons.Filled.Smartphone
        "rowpos" -> Icons.Filled.SwapVert
        "navbar" -> Icons.Filled.VisibilityOff
        "fps" -> Icons.Filled.Layers
        "gamelook", "media" -> Icons.Filled.Album
        "badges" -> Icons.Filled.Label
        "layout" -> Icons.Filled.Dashboard
        "endless" -> Icons.Filled.Repeat
        "roundshort" -> Icons.Filled.Apps
        "progressbar" -> Icons.Filled.EmojiEvents
        "gamesize" -> Icons.Filled.PhotoSizeSelectLarge
        "corners" -> Icons.Filled.RoundedCorner
        "romm" -> Icons.Filled.CloudDownload
        "carousel" -> Icons.Filled.ViewCarousel
        "theme" -> Icons.Filled.Palette
        "colors" -> Icons.Filled.ColorLens
        "rescan" -> Icons.Filled.Refresh
        "scrape" -> Icons.Filled.Image
        "profile" -> Icons.Filled.Speed
        "batterywatch", "battery" -> Icons.Filled.BatteryAlert
        "mixer" -> Icons.Filled.VolumeUp
        "settings" -> Icons.Filled.Settings
        "sort" -> Icons.AutoMirrored.Filled.Sort
        "shelflook", "spotlight", "bartabs", "crosscut", "headertight" -> Icons.Filled.Dashboard
        "drawnmedia" -> Icons.Filled.Album
        "magnifier" -> Icons.Filled.Search
        "whoplays" -> Icons.Filled.Person
        "herofit" -> Icons.Filled.AspectRatio
        else -> Icons.Filled.Bolt
    }
}
