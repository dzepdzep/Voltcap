package com.shelf.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features

/*
 * Update 181: Layout options of the two Pocket layouts (PocketClassic.kt, PocketRibbon.kt).
 */

@Composable
internal fun PocketClassicSettings() {
    MutedNote(
        "A 2004-style two-screen pocket menu. Top: your name, the time and battery in a thin bar in your colour, a clock " +
            "and this month's calendar. Bottom: the game you're on (◀ ▶ change it, A starts it), Achievements, the category " +
            "you're in (A: its games) and All categories (A: every category), with Settings, Statistics and Sleep along the " +
            "bottom edge. B goes back. Two screens: the menu on the lower one; one screen: one above the other."
    )
    Spacer(Modifier.height(10.dp))
    PocketAccentChoice(Features.classicAccent, PocketAccent.BLUE)
    PocketNameField()
    SwitchRow(Icons.Filled.Schedule, "Clock with hands", "Off: big digits.", Features.classicAnalogue.state.value, { Features.classicAnalogue.value = it })
    SwitchRow(Icons.Filled.CalendarMonth, "Calendar", "This month beside the clock. Off: the clock in the middle.", Features.classicCalendar.state.value, { Features.classicCalendar.value = it })
    SwitchRow(Icons.Filled.GridView, "Small icons along the bottom", "Settings, Statistics and Sleep.", Features.classicToolbar.state.value, { Features.classicToolbar.value = it })
    StyleChoice("Game picture", Features.PocketArt.values(), Features.classicArt.state.value, { it.label }) { Features.classicArt.value = it }
    PocketCommonSettings()
}

@Composable
internal fun PocketRibbonSettings() {
    val context = LocalContext.current.applicationContext
    var note by remember { mutableStateOf<String?>(null) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) note = if (PocketPhoto.set(context, uri)) { Features.ribbonTop.value = Features.RibbonTop.PHOTO; "Photo set." } else "Couldn't use that picture."
    }
    MutedNote(
        "A 2008-style two-screen pocket menu. Top: a framed picture. Bottom: a row of square icons — your categories; A " +
            "opens one and its games fill the row, B goes back — with the one you're on in the middle, its name in a speech " +
            "bubble and a START tab under it (START starts the game, A opens its game window). Swipe the row, tap the scroll bar."
    )
    Spacer(Modifier.height(10.dp))
    PocketAccentChoice(Features.ribbonAccent, PocketAccent.SKY)
    PocketNameField()
    StyleChoice("Top screen picture", Features.RibbonTop.values(), Features.ribbonTop.state.value, { it.label }) { Features.ribbonTop.value = it }
    if (Features.ribbonTop.state.value == Features.RibbonTop.PHOTO) {
        ChipFlow { Chip("Pick a photo") { runCatching { pickPhoto.launch(arrayOf("image/*")) } } }
        note?.let { MutedNote(it) }
    }
    SwitchRow(Icons.Filled.Slideshow, "Slideshow", "Screenshots change every 5 seconds (Screenshots slideshow).", Features.ribbonSlides.state.value, { Features.ribbonSlides.value = it })
    SwitchRow(Icons.Filled.Info, "Status line", "Volume, your name, the date, time and battery along the top.", Features.ribbonStatus.state.value, { Features.ribbonStatus.value = it })
    SwitchRow(Icons.Filled.Label, "Console under the name", "The speech bubble shows the console (or the number of games) under the name.", Features.ribbonNames.state.value, { Features.ribbonNames.value = it })
    SwitchRow(Icons.Filled.GridView, "Open on the categories", "Off: on the games of the category you're in.", Features.ribbonStartOnCategories.state.value, { Features.ribbonStartOnCategories.value = it })
    StyleChoice("Icon size", arrayOf(24, 28, 32, 36, 40), Features.ribbonIcon.state.value, { "$it" }) { Features.ribbonIcon.value = it }
    StyleChoice("Space between icons", arrayOf(4, 6, 10, 14, 18), Features.ribbonGap.state.value, { "$it" }) { Features.ribbonGap.value = it }
    StyleChoice("Top screen's share (one display)", arrayOf(40, 45, 50, 55, 60), Features.ribbonSplit.state.value, { "$it %" }) { Features.ribbonSplit.value = it }
    PocketCommonSettings()
}

@Composable
private fun PocketAccentChoice(pref: Features.StringPref, def: PocketAccent) {
    val now = runCatching { PocketAccent.valueOf(pref.state.value) }.getOrDefault(def)
    SectionLabel("Colour")
    ChipFlow {
        PocketAccent.values().forEach { a ->
            Chip((if (a == now) "✓ " else "") + a.label, accent = a == now) { pref.value = a.name }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PocketNameField() {
    var name by remember { mutableStateOf(Features.pocketName.value) }
    ShelfTextField(name, { name = it.take(10); Features.pocketName.value = name }, "Your name (up to 10 letters; empty: your RetroAchievements name)")
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PocketCommonSettings() {
    Spacer(Modifier.height(10.dp))
    SectionLabel("Both Pocket layouts")
    StyleChoice("Screens", Features.PocketArrange.values(), Features.pocketArrange.state.value, { it.label }) { Features.pocketArrange.value = it }
    SwitchRow(Icons.Filled.GridView, "Pixel-perfect", "Whole pixels per dot, the 4:3 screen exactly (sharp). Off: as big as fits, wider when there's room (nothing stretched).",
        Features.pocketPixel.state.value, { Features.pocketPixel.value = it })
    SwitchRow(Icons.Filled.MusicNote, "Pocket sounds", "Tiny chirps for moving, picking and going back while a Pocket layout is on. Off: your usual sound set.",
        Features.pocketSounds.state.value, { Features.pocketSounds.value = it })
    StyleChoice("Animation speed", Features.PocketAnim.values(), Features.pocketAnim.state.value, { it.label }) { Features.pocketAnim.value = it }
    // Which category it opens on.
    val titles = LocalTitleOptions.current
    val keys = listOf("" to "Where you were", "ALL" to "All", "FAVORITES" to "Favorites", "RECENT" to "Recent") +
        com.shelf.launcher.data.FolderArt.platforms.map { it to titles.console(it) }
    val now = Features.pocketStartCategory.state.value
    SectionLabel("Opens on")
    ChipFlow {
        keys.forEach { (k, label) -> Chip((if (k == now) "✓ " else "") + label, accent = k == now) { Features.pocketStartCategory.value = k } }
    }
}
