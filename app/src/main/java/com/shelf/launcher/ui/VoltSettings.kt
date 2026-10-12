package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Settings for update 24: Interface style (Volt / Classic), the voltage line and focus glow, the library
 * view (grid / list). Plus the icon every settings group gets beside its name.
 */

/** An icon for a settings group / sub-page, picked by its name (null = none). First match wins. */
internal fun groupIconFor(title: String): ImageVector? {
    val t = title.lowercase()
    return when {
        "preset" in t -> Icons.Filled.AutoAwesome
        "layout" in t -> Icons.Filled.Dashboard
        "disc" in t || "cases" in t -> Icons.Filled.Album
        "effect" in t || "battery" in t -> Icons.Filled.Speed
        "animation" in t -> Icons.Filled.AutoAwesome
        "interface" in t || "style" in t -> Icons.Filled.Style
        "library" in t || "grid" in t -> Icons.Filled.ViewModule
        "second screen" in t || "screens" in t || "display" in t -> Icons.Filled.Tv
        "theme" in t -> Icons.Filled.Palette
        "colour" in t || "color" in t -> Icons.Filled.ColorLens
        "achievement" in t || "trophies" in t || "unlock" in t -> Icons.Filled.EmojiEvents
        "music" in t || "song" in t || "lyric" in t -> Icons.Filled.MusicNote
        "visual" in t -> Icons.Filled.GraphicEq
        "save" in t -> Icons.Filled.Save
        "light" in t -> Icons.Filled.Lightbulb
        "screensaver" in t -> Icons.Filled.DarkMode
        "screenshot" in t -> Icons.Filled.Screenshot
        "background" in t || "wallpaper" in t -> Icons.Filled.Wallpaper
        "artwork" in t || "cover" in t || "art " in t || t.startsWith("art") -> Icons.Filled.Image
        "video" in t || "clip" in t -> Icons.Filled.Movie
        "sound" in t || "audio" in t -> Icons.Filled.VolumeUp
        "hotkey" in t || "button" in t || "legend" in t || "control" in t -> Icons.Filled.SportsEsports
        "size" in t || "text" in t || "name" in t -> Icons.Filled.TextFields
        "card" in t || "frame" in t -> Icons.Filled.CropSquare
        "categor" in t -> Icons.Filled.Category
        "row" in t -> Icons.Filled.ViewCarousel
        "detail" in t || "info" in t -> Icons.Filled.Info
        "scrap" in t || "source" in t -> Icons.Filled.CloudDownload
        "startup" in t -> Icons.Filled.PlayCircle
        "shortcut" in t -> Icons.Filled.Bolt
        "sync" in t || "cloud" in t || "drive" in t -> Icons.Filled.Cloud
        "home" in t -> Icons.Filled.Home
        "power" in t || "perform" in t || "overlay" in t -> Icons.Filled.Speed
        "emulator" in t -> Icons.Filled.VideogameAsset
        "region" in t || "duplicate" in t -> Icons.Filled.Public
        "collection" in t -> Icons.Filled.Collections
        "import" in t -> Icons.Filled.Input
        "running" in t || "resume" in t || "recent" in t -> Icons.Filled.History
        "device" in t -> Icons.Filled.Smartphone
        "account" in t || "profile" in t -> Icons.Filled.Person
        "game" in t -> Icons.Filled.SportsEsports
        else -> null
    }
}

/** Appearance › Interface style. */
@Composable
internal fun InterfaceStyleSettings() {
    val F = Features
    ShelfCard {
        Text("Interface style", color = ShelfTheme.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        PictoChoice(Features.UiStyle.values(), F.uiStyle.state.value, { it.label }) {
            val was = F.uiStyle.value
            F.uiStyle.value = it
            // The 32-bit console look goes with square pixel corners; leaving it takes them away again (they
            // used to stay, with the pixel font). The font is its own choice below.
            if (it == Features.UiStyle.PIXEL) F.pixelUi.value = true
            else if (was == Features.UiStyle.PIXEL) F.pixelUi.value = false
        }
        Spacer(Modifier.height(6.dp))
        MutedNote(
            "Glass: dark glass panels with a top sheen. Energy capsule: panels like the Voltcap capsule — a dark dot-matrix " +
                "core with a glowing double rim. 32-bit console: flat panels with a pixel bevel and scanlines, a hard pixel " +
                "focus frame and dashed lines (the headings in the 32-bit type). All three use your theme's colours, the voltage " +
                "line and the accent switches. Memory card: smoky translucent plastic with a label strip and grip ridges. " +
                "CRT: softly lit screens with scanlines and a red / blue fringe (the focus splits too). Neon sign: dark panels " +
                "edged by a glowing tube that flickers on. Cartridge: plastic shells with side grips and the content on a " +
                "label with an accent stripe. Classic: the look before."
        )
    }
    Spacer(Modifier.height(8.dp))
    PixelStyleCard()
    Spacer(Modifier.height(8.dp))
    SettingsGrid {
        item { m -> SwitchRow(Icons.Filled.Bolt, "Voltage line sparks", "A spark runs along the accent lines now and then. Off: still lines.", F.voltLine.state.value, { F.voltLine.value = it }, m) }
        item { m -> SwitchRow(Icons.Filled.AutoAwesome, "Focus glow", "A soft glow inside the accent outline of what you're on. Off: just the outline.", F.uiGlow.state.value, { F.uiGlow.value = it }, m) }
        item { m -> SwitchRow(Icons.Filled.BlurOn, "Soft blur behind the games", "A light blur and shade only behind the games, their names and the categories, so they read on busy backgrounds.", F.rowBlur.state.value > 0, { F.rowBlur.value = if (it) 35 else 0 }, m) }
        item { m -> SwitchRow(Icons.Filled.Lightbulb, "Cover light", "A soft light in the colours of the game you're on, behind its card.", F.coverLight.state.value, { F.coverLight.value = it }, m) }
        item { m -> SwitchRow(Icons.Filled.BatteryChargingFull, "Charge gauge battery", "The status bar's battery as a little gauge in your theme's colours.", F.voltBattery.state.value, { F.voltBattery.value = it }, m) }
    }
    RowBlurStrength()
}

/** The strength of the soft blur behind the games (shown while it's on). */
@Composable
internal fun RowBlurStrength() {
    if (Features.rowBlur.state.value > 0) {
        Spacer(Modifier.height(6.dp))
        PercentSlider("Soft blur strength", Features.rowBlur.state.value, { Features.rowBlur.value = it.coerceAtLeast(1) }, min = 5, max = 100, live = true)
    }
}

/** Lets a setting close the Settings window (set by the window while it's open). */
internal object SettingsCloser {
    var close: (() -> Unit)? = null
}

/** Cartridge / disc look: the real scraped picture (default) or Voltcap's own drawn shell + label. */
@Composable
internal fun MediaShellSwitch() {
    SwitchRow(
        Icons.Filled.Album, "Use Voltcap's drawn cartridges / discs",
        "Off (default): the real cartridge or disc picture scraped for each game (ScreenScraper), as it is. " +
            "On: Voltcap's own drawn cartridge, card, UMD or disc (your template pack) with the game's label scan behind its " +
            "window — or, without a scan, the cartridge with the system's printed label and the cover in it.",
        Features.mediaShells.state.value, { Features.mediaShells.value = it }
    )
    if (Features.mediaShells.state.value) {
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            Icons.Filled.Album, "Bigger GameCube discs",
            "GameCube discs are 8 cm, so they're drawn smaller than 12 cm discs. On: as big as the others.",
            Features.bigGcDiscs.state.value, { Features.bigGcDiscs.value = it }
        )
        Spacer(Modifier.height(8.dp))
        SectionLabel("Game Boy Color cartridges")
        ChipFlow {
            Features.GbcCart.values().forEach { c ->
                Chip(c.label, accent = Features.gbcCart.state.value == c) { Features.gbcCart.value = c }
            }
        }
    }
}
