package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.ui.theme.ShelfTheme

/** A switch row bound to a Features switch. */
@Composable
internal fun FeatureSwitch(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, pref: Features.BoolPref) {
    SwitchRow(icon, title, subtitle, pref.state.value, { pref.value = it })
    Spacer(Modifier.height(8.dp))
}

/** Chips for picking one value of a Features enum. */
@Composable
internal fun <E : Enum<E>> FeatureChoice(label: String, values: Array<E>, pref: Features.EnumPref<E>, name: (E) -> String) {
    Text(label, color = ShelfTheme.textMuted, fontSize = 12.sp)
    Spacer(Modifier.height(6.dp))
    ChipFlow {
        values.forEach { v -> Chip(name(v), accent = v == pref.state.value) { pref.value = v } }
    }
    Spacer(Modifier.height(12.dp))
}

/** What each look of the category screen is, in a line (Settings › Categories › Category screen). */
internal fun categoryLookNote(v: Features.PlatformView): String = when (v) {
    Features.PlatformView.TILES -> "Tiles: every category as a card in a grid you scroll down, its name under / on it."
    Features.PlatformView.SLIDES -> "Slides: one platform at a time filling the screen, ◀ ▶ or swipe to the next, every part placeable (Y › Design this slide)."
    Features.PlatformView.WALL -> "Wall: slanted slices of every platform side by side, the one you're on wider, its logo or name in the middle."
    Features.PlatformView.GRID -> "Grid: a horizontal grid of cards (wide in two rows, or tall 2:3), with drag-and-drop to reorder your platforms."
}

/** Settings › Categories › Category screen: the options of the look in use (only that look's). */
@Composable
internal fun CategoryLookOptions(view: Features.PlatformView) {
    Column {
        when (view) {
            Features.PlatformView.TILES -> Unit // its tile size is a setting of the appearance (passed in by the caller)
            Features.PlatformView.SLIDES -> {
                Text("What the slide shows", color = ShelfTheme.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                FeatureSwitch(Icons.Filled.Image, "Logo", "The platform's logo (scraped in Platform Options), else its name in big letters.", Features.pbShowLogo)
                FeatureSwitch(Icons.Filled.Title, "Platform name", "Its full name.", Features.pbShowName)
                FeatureSwitch(Icons.Filled.ViewCarousel, "Cover fan", "A few covers from the platform, fanned out.", Features.pbShowCovers)
                FeatureSwitch(Icons.Filled.PlayArrow, "Last played card", "The game you played last on that platform.", Features.pbShowLastPlayed)
                FeatureSwitch(Icons.Filled.Image, "Shadow", "A soft shadow under the covers.", Features.pbShowShadow)
                FeatureSwitch(Icons.Filled.Info, "Game count", "\"113 Games (9 Favorites)\".", Features.pbShowCount)
                FeatureSwitch(Icons.Filled.Info, "Maker and year", "\"SONY 1994\".", Features.pbShowMaker)
                FeatureSwitch(Icons.Filled.Image, "Skewed bands", "The grey slanted bands behind the covers.", Features.pbBands)
                Chip("Reset the slide design", accent = false) { Features.pbSlideLayout.value = "" }
                Spacer(Modifier.height(4.dp))
                MutedNote("To design the slide: open the category screen, press Y on a platform and choose \"Design this slide\".")
                Spacer(Modifier.height(10.dp))
            }
            Features.PlatformView.WALL -> {
                FeatureSwitch(Icons.Filled.Image, "Logo in the middle", "The platform's scraped logo (else its name).", Features.pbShowLogo)
            }
            Features.PlatformView.GRID -> {
                FeatureSwitch(Icons.Filled.ViewCarousel, "Tall 2:3 cards", "Off: wide cards in two rows.", Features.pbTallCards)
                FeatureSwitch(Icons.Filled.Title, "Platform name on the card", "", Features.pbShowName)
                FeatureSwitch(Icons.Filled.Info, "Game count on the card", "", Features.pbShowCount)
                FeatureSwitch(Icons.Filled.Refresh, "Drag-and-drop reorder", "Hold a card and drag it (controller: Start picks it up, ◀ ▶ move it, A drops it).", Features.pbReorder)
                Chip("Back to the alphabetical order", accent = false) { com.shelf.launcher.data.PlatformOrder.reset() }
                Spacer(Modifier.height(12.dp))
            }
        }
        if (view != Features.PlatformView.TILES) {
            Text("Every look but Tiles", color = ShelfTheme.textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            FeatureSwitch(Icons.Filled.PlayArrow, "Browsing animations", "Slides animate when you switch; the Wall slides in the direction you browse.", Features.pbAnimate)
            FeatureSwitch(Icons.Filled.Info, "Button legend", "The row of button hints at the bottom.", Features.pbShowLegend)
            FeatureSwitch(Icons.Filled.Info, "Game count in the action bar", "", Features.pbBarCount)
            FeatureSwitch(Icons.Filled.Search, "Random-game button in the action bar", "X on the controller.", Features.pbBarRandom)
        }
    }
}

/** Settings › Categories › Per platform: what each platform can have of its own (set with Y on the category screen). */
@Composable
internal fun PerPlatformSettings() {
    Column {
        MutedNote("On the category screen press Y on a platform for its Platform Options: your own picture, scraped logo / photo / fan art, intro video, music, and the slide design.")
        Spacer(Modifier.height(10.dp))
        FeatureSwitch(
            Icons.Filled.Videocam, "Play platform intro videos",
            "After the launch animation and before the game starts; any key or tap skips it.",
            Features.platformIntroVideos
        )
        FeatureSwitch(
            Icons.Filled.MusicNote, "Fade between music overrides",
            "A platform's or game's own music fades into the next one instead of cutting or overlapping. \"Restart on return\" is switched on per platform / game.",
            Features.musicCrossfade
        )
        FeatureSwitch(Icons.Filled.Star, "Platform media scraping", "Offer \"Scrape platform media\" (logo, photo, fan art from ScreenScraper) in Platform Options.", Features.platformMediaScrape)
    }
}

/** Settings › Appearance › Category screen › Game details per view. */
@Composable
internal fun GameFactsSettings() {
    Column {
        MutedNote("Choose which facts each view shows. A fact missing from the game's data stays hidden either way.")
        Spacer(Modifier.height(8.dp))
        FactsRow("Game row (next to the row)", Features.factsRow)
        FactsRow("Hero carousel", Features.factsList)
        FactsRow("TV room", Features.factsGrid)
        FactsRow("Details window", Features.factsDetails)
        FactsRow("Second screen", Features.factsSecond)
        Spacer(Modifier.height(6.dp))
        Text("Detail pane layers (list and grid)", color = ShelfTheme.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        FeatureSwitch(Icons.Filled.Image, "Cover art", "The game's cover at the back of the pane.", Features.detailLayerCover)
        FeatureSwitch(Icons.Filled.Image, "Screenshots", "Screenshots fading in turn.", Features.detailLayerScreenshots)
        FeatureSwitch(Icons.Filled.Videocam, "Preview video", "The preview clip, when there is one.", Features.detailLayerVideo)
        FeatureSwitch(Icons.Filled.Title, "Game logo", "The game's logo over the layers.", Features.detailLayerLogo)
    }
}

@Composable
private fun FactsRow(label: String, pref: Features.SetPref) {
    Text(label, color = ShelfTheme.textMuted, fontSize = 12.sp)
    Spacer(Modifier.height(6.dp))
    ChipFlow {
        Features.GameFact.values().forEach { f ->
            Chip(f.label, accent = f.name in pref.state.value) { pref.toggle(f.name) }
        }
    }
    Spacer(Modifier.height(12.dp))
}
