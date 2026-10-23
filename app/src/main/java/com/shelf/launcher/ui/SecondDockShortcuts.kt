package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.BuiltinShortcuts
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SecondPage
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Update 193: Voltcap's own shortcuts in the second screen's dock (both looks), next to the apps — your L1 screens and
 * R1 tiles that work from there. Each one does its thing on the second screen: a screen with a second-screen page
 * opens as that page (Achievements, Music, Games, Magnifier); a switch (Lights, Overlay, Endless, Do not disturb…)
 * switches at once and says so in a small note there; Android's own Wi-Fi, Bluetooth and settings panels open on the
 * second screen. Nothing pops up on the main screen. Stored in the dock's list like an app ("voltcap:…").
 */
internal object SecondDockShortcuts {
    /** What can go in the dock (id, name), in the picker's order. */
    val entries: List<Pair<String, String>> = listOf(
        "voltcap:achievements" to "Achievements",
        "voltcap:music" to "Music",
        "voltcap:categories" to "Games",
        "voltcap:r1:magnifier" to "Magnifier",
        "voltcap:spotify" to "Play / pause music",
        "voltcap:explore" to "Explore",
        "voltcap:rediscover" to "Rediscover",
        "voltcap:r1:lights" to "Lights on / off",
        "voltcap:r1:fps" to "Overlay",
        "voltcap:r1:endless" to "Endless row",
        "voltcap:r1:navbar" to "Hide nav bar",
        "voltcap:r1:whoplays" to "Who is playing",
        "voltcap:r1:drawnmedia" to "Drawn / real discs",
        "voltcap:r1:theme" to "Next theme",
        "voltcap:r1:saver" to "Battery saver",
        "voltcap:r1:rescan" to "Rescan library",
        "voltcap:r1:scrape" to "Missing art",
        "voltcap:r1:dnd" to "Do not disturb",
        "voltcap:r1:wifi" to "Wi-Fi",
        "voltcap:r1:bt" to "Bluetooth",
        "voltcap:r1:settings" to "Android settings",
        // Update 207: Voltcap's own screens, opened on the main screen from down here.
        "voltcap:saves" to "Save Vault",
        "voltcap:whattoplay" to "What to play",
        "voltcap:stats" to "Stats",
        "voltcap:search" to "Search",
        "voltcap:vsettings" to "Voltcap settings",
        "voltcap:screensaver" to "Screensaver"
    )

    /** Update 207: the shortcuts that open one of Voltcap's screens on the main screen (the rest work down here). */
    val opensOnMain: Set<String> = setOf("voltcap:saves", "voltcap:whattoplay", "voltcap:stats", "voltcap:search", "voltcap:vsettings", "voltcap:screensaver")

    private val names = entries.toMap()

    fun isShortcut(id: String?): Boolean = id != null && id in names

    /** The page a shortcut opens on the second screen (null: it's a switch or an Android panel). */
    fun page(id: String?): SecondPage? = when (id) {
        "voltcap:achievements" -> SecondPage.ACHIEVEMENTS
        "voltcap:music" -> SecondPage.MUSIC
        "voltcap:categories" -> SecondPage.LIBRARY
        "voltcap:r1:magnifier" -> SecondPage.MAGNIFIER
        else -> null
    }

    /** The shortcut as a dock item (drawn with its own icon). */
    fun game(id: String): Game? = names[id]?.let { label ->
        Game(name = label, platform = "Voltcap", hue = (id.hashCode() and 0x7fffffff) % 360,
            sourceType = com.shelf.launcher.data.SourceType.APP, packageName = id)
    }

    fun all(): List<Game> = entries.mapNotNull { game(it.first) }

    private fun iconOf(id: String) = when (id) {
        "voltcap:r1:theme" -> androidx.compose.material.icons.Icons.Filled.Palette
        "voltcap:r1:saver" -> androidx.compose.material.icons.Icons.Filled.BatterySaver
        "voltcap:search" -> androidx.compose.material.icons.Icons.Filled.Search
        "voltcap:vsettings" -> androidx.compose.material.icons.Icons.Filled.Settings
        else -> builtinIcon(id)
    }

    /** A dock item's icon: an app's own, or a Voltcap shortcut's symbol on a round plate. */
    @Composable
    fun Icon(id: String?, modifier: Modifier) {
        // (Update 319: the dock's icon pack — every item one symbol in the theme's accent, nothing behind it.)
        val mono = com.shelf.launcher.data.Features.secondDockMono.state.value
        if (mono && BuiltinShortcuts.isBuiltin(id) && id != null) {
            Icon(iconOf(id), names[id], tint = ShelfTheme.accent, modifier = modifier.padding(4.dp))
        } else if (mono) {
            MonoAppIcon(id, ShelfTheme.accent, modifier)
        } else if (BuiltinShortcuts.isBuiltin(id) && id != null) {
            Box(modifier.clip(CircleShape).background(ShelfTheme.accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(iconOf(id), names[id], tint = ShelfTheme.accent, modifier = Modifier.fillMaxSize().padding(7.dp))
            }
        } else AppIconArt(id, wide = false, modifier = modifier)
    }
}
