package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Appearance › Game row › "<layout> options": each style home's own section (shown while that layout is
 * picked, like the TV room's and the hero carousel's). Every switch here is a Features pref, so it is backed
 * up and moves to another device with the other look settings. Card size / corners / artwork / effect stay in
 * "Cards · <layout>" below it (each layout keeps its own).
 */

/** What the folded section says about [layout]. */
internal fun styleHomeSummary(layout: HomeLayout): String = when (layout) {
    HomeLayout.DASH -> "Title, Now playing, row, player & status cards, glass"
    HomeLayout.LIVE -> "Category tabs, tiles per page, colour, backdrop"
    HomeLayout.SIDE -> "Picture, power capsule, width, gold trim, art behind, row title"
    HomeLayout.CROSS -> "Colour wash, ribbons, size, names, details, achievements, Trophies category"
    HomeLayout.SPOT -> "Look (Home menu, Row & menu, Big picture), button hints, tiles, art and shade"
    HomeLayout.BAR -> "Top icons, tile size, bigger selection, names, Start, art behind"
    HomeLayout.HUB -> "Art behind"
    HomeLayout.RESUME -> "Art behind"
    HomeLayout.CHAN -> "Tiles per row, pale backdrop, names"
    HomeLayout.CIRCUIT -> "Board colour, flowing current, chip labels, board behind"
    HomeLayout.SHELF -> "Spines, fronts or turned, real sizes, front or side view, bob & turn, wood / dark / glass"
    // Update 146.
    HomeLayout.LIBRARY -> "Games per row, the game panel, tabs"
    HomeLayout.STACKS -> "Stacks in perspective, button bar, mirror floor"
    HomeLayout.TIMELINE -> "Order by last played, release year or added, dates"
    HomeLayout.ARCADE -> "Marquee glow, screen lines, cabinet colour"
    HomeLayout.CONSOLES -> "Console pictures, how many games slide out"
    HomeLayout.DISCS -> "Spinning disc, about the game"
    else -> ""
}

/** The options of the style home [layout]. */
@Composable
internal fun StyleHomeSettings(layout: HomeLayout) {
    when (layout) {
        HomeLayout.DASH -> DashSettings()
        HomeLayout.LIVE -> LiveSettings()
        HomeLayout.SIDE -> SideSettings()
        HomeLayout.CROSS -> CrossSettings()
        HomeLayout.SPOT -> SpotSettings()
        HomeLayout.BAR -> BarSettings()
        HomeLayout.SHELF -> ShelfSettings()
        HomeLayout.HUB -> HubSettings()
        HomeLayout.RESUME -> ResumeSettings()
        HomeLayout.CHAN -> ChannelSettings()
        HomeLayout.CIRCUIT -> CircuitSettings()
        // Update 146.
        HomeLayout.LIBRARY -> LibrarySettings()
        HomeLayout.STACKS -> CaseStacksSettings()
        HomeLayout.TIMELINE -> TimelineSettings()
        HomeLayout.ARCADE -> ArcadeSettings()
        HomeLayout.CONSOLES -> ConsoleStackSettings()
        HomeLayout.DISCS -> DiscStationSettings()
        else -> Unit
    }
    // Update 104: every layout can show the game's info parts (Spotlight and Icon bar have it in their own options).
    if (layout != HomeLayout.SPOT && layout != HomeLayout.BAR) {
        Spacer(Modifier.height(10.dp))
        OverlayPartsChoice(layout)
    }
    Spacer(Modifier.height(4.dp))
    MutedNote("The menus use your round shortcuts: which ones, and how many this layout shows, are in Settings › Home › Round shortcuts.")
}

/** Whether [layout] has round-shortcut options of its own (shown in Settings › Home › Round shortcuts). */
internal fun styleHasShortcutSettings(layout: HomeLayout): Boolean =
    layout == HomeLayout.DASH || layout == HomeLayout.LIVE || layout == HomeLayout.HUB

/** The round shortcuts inside [layout]'s own menus (moved here from its Layout options, so they live in one place). */
@Composable
internal fun StyleShortcutSettings(layout: HomeLayout) {
    when (layout) {
        HomeLayout.DASH -> {
            Toggle(Icons.Filled.Menu, "Menu on the left", "Round buttons down the left side.", Features.dashMenu)
            if (Features.dashMenu.state.value) {
                Toggle(Icons.Filled.Label, "Names beside the buttons", "Off: a slim column of round buttons only.", Features.dashMenuLabels)
                Slider("Buttons in the menu", Features.dashMenuCount, 2, 12) { "$it" }
            }
            Slider("Quick access buttons (0 = no strip)", Features.dashQuick, 0, 8) { if (it == 0) "Off" else "$it" }
        }
        HomeLayout.LIVE -> Slider("Shortcut tiles (0 = none)", Features.liveBar, 0, 6) { if (it == 0) "Off" else "$it" }
        HomeLayout.HUB -> Toggle(Icons.Filled.Apps, "Shortcuts along the top", "Your round shortcuts as small icons.", Features.hubIcons)
        else -> Unit
    }
}

@Composable
private fun <T> StyleChoice(label: String, values: Array<T>, current: T, name: (T) -> String, onPick: (T) -> Unit) {
    Text(label, color = ShelfTheme.textMuted, fontSize = 12.sp, modifier = jumpAnchor(label))
    Spacer(Modifier.height(6.dp))
    ChipFlow { values.forEach { v -> Chip(name(v), accent = v == current) { onPick(v) } } }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Toggle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, pref: Features.BoolPref) {
    SwitchRow(icon, title, subtitle, pref.state.value, { pref.value = it })
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Slider(label: String, pref: Features.IntPref, min: Int, max: Int, text: (Int) -> String) {
    PercentSlider(label, pref.state.value, { pref.value = it.coerceIn(min, max) }, min = min, max = max, valueText = text)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun DashSettings() {
    MutedNote("Glass panels: a menu of round buttons on the left, the game you're on big in “Now playing”, the row under it, Quick access, and a player card and system status on the right. ▲ (or ◀ on the first game) opens the menu, ▼ Quick access.")
    Spacer(Modifier.height(10.dp))
    ShelfTextField(Features.dashTitle.state.value, { Features.dashTitle.value = it.take(40) }, "Title (empty = none)", leadingIcon = Icons.Filled.Title)
    Spacer(Modifier.height(8.dp))
    ShelfTextField(Features.dashSubtitle.state.value, { Features.dashSubtitle.value = it.take(60) }, "Line under the title", leadingIcon = Icons.Filled.ShortText)
    Spacer(Modifier.height(10.dp))
    Toggle(Icons.Filled.PlayArrow, "Now playing card", "The game you're on big, with Play / Continue. Off: the row gets the room.", Features.dashHero)
    ShelfTextField(Features.dashRowTitle.state.value, { Features.dashRowTitle.value = it.take(40) }, "Row title (empty = the category's name)", leadingIcon = Icons.Filled.TextFields)
    Spacer(Modifier.height(10.dp))
    Toggle(Icons.Filled.AccountCircle, "Player card", "Your RetroAchievements picture and name, battery and library bars (wide screens).", Features.dashProfile)
    Toggle(Icons.Filled.MonitorHeart, "System status", "Battery, library, time played and network (wide screens).", Features.dashStatus)
    Toggle(Icons.Filled.MusicNote, "Music player", "The song that's playing, bottom right in the same style. Needs a wide screen.", Features.styleMusic)
    if (Features.styleMusic.state.value) Toggle(Icons.Filled.MusicNote, "Album cover", "The song's cover in the player. Off: just the song and the buttons.", Features.dashPlayerArt)
    StyleChoice("Panels", Features.PanelLook.values(), Features.dashPanel.state.value, { it.label }) { Features.dashPanel.value = it }
    Slider("Panel strength", Features.dashOpacity, 15, 100) { "$it%" }
    Toggle(Icons.Filled.ContentCut, "Cut corners", "Angled corners on the panels. Off: rounded.", Features.dashCut)
}

/**
 * Update 146: what fills the background of a layout that has a pale backdrop of its own (Live tiles, Channel grid):
 * that light backdrop, your theme's own background, or the game you're on's art (dimmed as the theme says).
 */
@Composable
private fun BackdropChoice(light: Features.BoolPref, art: Features.BoolPref) {
    val now = when {
        art.state.value -> 2
        light.state.value -> 0
        else -> 1
    }
    StyleChoice("Background", arrayOf(0, 1, 2), now, { when (it) { 0 -> "Pale"; 1 -> "Your theme"; else -> "The game's art" } }) {
        light.value = it == 0
        art.value = it == 2
    }
    MutedNote("Pale: a light backdrop with dark writing. Your theme: the theme's own background (Settings › Theme). The game's art: the game you're on fills the screen.")
}

@Composable
private fun LiveSettings() {
    MutedNote("Flat live tiles: the categories as words on top (L2 / R2 or tap), a page of game tiles, coloured shortcut tiles under them. ◀ ▶ ▲ ▼ move round the tiles, ▼ from the bottom row reaches the shortcuts.")
    Spacer(Modifier.height(10.dp))
    StyleChoice("Tiles per page", Features.LivePage.values(), Features.livePage.state.value, { it.label }) { Features.livePage.value = it }
    StyleChoice("Colour", Features.LiveColor.values(), Features.liveColor.state.value, { it.label }) { Features.liveColor.value = it }
    Toggle(Icons.Filled.Tab, "Category names on top", "The one you're in, the one before it and the next ones.", Features.liveTabs)
    BackdropChoice(Features.liveLight, Features.liveArt)
    Toggle(Icons.Filled.Label, "Names on the tiles", "The game's name along the bottom of its tile.", Features.liveNames)
    Toggle(Icons.Filled.MusicNote, "Music player", "The song that's playing, as the last tile of the bottom row.", Features.styleMusic)
}

@Composable
private fun SideSettings() {
    MutedNote("A dark menu down the left (your picture, your shortcuts, a charging power capsule), the game's art behind and the row along the bottom. ▲ (or ◀ on the first game) goes into the menu.")
    Spacer(Modifier.height(10.dp))
    StyleChoice("At the top of the menu", Features.SideEmblem.values(), Features.sideEmblem.state.value, { it.label }) { Features.sideEmblem.value = it }
    Slider("Menu width", Features.sideWidth, 140, 280) { "$it dp" }
    Toggle(Icons.Filled.BatteryChargingFull, "Power capsule", "Voltcap's glass capsule charging up, with the game you're on under it (taller screens).", Features.sideOrb)
    Toggle(Icons.Filled.Style, "Gold trim", "Off: the theme's colour.", Features.sideGold)
    Toggle(Icons.Filled.TextFields, "Capitals", "Menu names in capitals.", Features.sideCaps)
    Toggle(Icons.Filled.Image, "Game art behind", "The game you're on across the whole screen. Off: your theme.", Features.sideArt)
    Toggle(Icons.Filled.Title, "Category name above the row", "", Features.sideRowTitle)
}

@Composable
private fun CrossSettings() {
    MutedNote("The categories as icons across the screen, the games as a list down from the one you're on. ◀ ▶ change the category, ▲ ▼ the game.")
    Spacer(Modifier.height(10.dp))
    StyleChoice("Colour wash", Features.CrossTint.values(), Features.crossTint.state.value, { it.label }) { Features.crossTint.value = it }
    Toggle(Icons.Filled.Waves, "Ribbons", "Slow light ribbons across the background (still with Lite effects).", Features.crossWaves)
    Slider("Size", Features.crossScale, 70, 140) { "$it%" }
    Toggle(Icons.Filled.Label, "Category name", "Under the icon you're on.", Features.crossLabels)
    Toggle(Icons.Filled.Folder, "Consoles as folders", "Each console as a plain white folder with its logo on it (no name under it); Favourites, Recent and the other lists keep their own icons. Off: the console logos. Folder logos: Dan Patrick's v2 Platform Logos.", Features.crossFolders)
    if (!Features.crossFolders.state.value) Toggle(Icons.Filled.Label, "Tiles in the accent colour", "The tile behind each console logo takes the accent colour (the game's or the theme's, whichever colours follow); the logos keep their own colours. Off: plain glass tiles.", Features.crossLogoTint)
    if (!Features.crossFolders.state.value) Toggle(Icons.Filled.Label, "Cut-out logos", "The console logos as clean cut-outs: no tile behind them, just the logo's shape in white (in the accent colour with \"Tiles in the accent colour\" on). Off: logos on glass tiles.", Features.crossLogoCut)
    Toggle(Icons.Filled.Info, "Details under the game", "Time played, console and last played.", Features.crossInfo)
    // Update 127.
    Toggle(Icons.Filled.EmojiEvents, "Achievements in the menu's style", "Y (or Achievements in the game window) opens the game's achievements as a column beside it, like a console's trophy list: ▲ ▼ move, X all / still to get / earned, A the full list, B back. Off: the achievement list popup.", Features.crossTrophies)
    // Update 132.
    Toggle(Icons.Filled.EmojiEvents, "Trophies category", "A Trophies icon in the strip: every game with achievements, last played first, with how far you are under the one you're on; A opens its achievements. Only on this layout (move it in Settings › Categories › Category order).", Features.crossTrophyCat)
}

@Composable
private fun SpotSettings() {
    MutedNote("The game's art across the screen, small tiles along the top, its big title, Play, ⋯ and activity cards. ▼ goes to Play and the cards.")
    Spacer(Modifier.height(10.dp))
    StyleChoice("Look", Features.SpotLook.values(), Features.spotLook.state.value, { it.label }) { Features.spotLook.value = it }
    if (Features.spotLook.state.value == Features.SpotLook.ROW_MENU) MutedNote("Row & menu: round buttons with your profile, points and the time on top (▲), the row with the game you're on big and Start under it, then Overview · Recent activity · Related (▼).")
    // Update 132.
    if (Features.spotLook.state.value == Features.SpotLook.HOME_MENU) {
        MutedNote("Home menu: your profile, your shortcuts as icons and the time along the very top (▲, the name of the one you're on under it), a row of big tiles with the game's name under the one you're on, and what the buttons do along the bottom (▼; each one is also a button).")
        Toggle(Icons.Filled.PlayArrow, "Button hints along the bottom", "What A, X, Y, Start and Select do on the game you're on. They stand in for the legend on this look.", Features.spotHints)
        Toggle(Icons.Filled.Label, "Status bar", "The status bar along the top too. Off: the menu's own profile, battery and clock (the very top is the menu's).", Features.spotStatusBar)
    }
    StyleChoice("Tiles along the top", Features.TileShape.values(), Features.spotShape.state.value, { it.label }) { Features.spotShape.value = it }
    // (Update 132: these are the other looks' own; the Home menu has its own layout.)
    if (Features.spotLook.state.value != Features.SpotLook.HOME_MENU) {
        StyleChoice("Tiles sit at the", Features.SpotSide.values(), Features.spotTilesAt.state.value, { it.label }) { Features.spotTilesAt.value = it }
        StyleChoice("Name of the game", Features.SpotName.values(), Features.spotNameAt.state.value, { it.label }) { Features.spotNameAt.value = it }
        StyleChoice("Big title across", Features.SpotAcross.values(), Features.spotTitleAt.state.value, { it.label }) { Features.spotTitleAt.value = it }
        StyleChoice("Big title sits", Features.SpotAlong.values(), Features.spotTitleRow.state.value, { it.label }) { Features.spotTitleRow.value = it }
        Toggle(Icons.Filled.Title, "Logo as the title", "The game's logo when it has one (and Game names › Logos is on).", Features.spotLogo)
        Toggle(Icons.Filled.Dashboard, "Activity cards", "Achievements, time played, last played (taller screens).", Features.spotCards)
        Toggle(Icons.Filled.Tab, "Category names", "The one you're in and the next one, top left.", Features.spotTabs)
    }
    Toggle(Icons.Filled.Image, "Game art behind", "The game you're on across the whole screen. Off: your theme.", Features.spotArt)
    Slider("Shade over the art", Features.spotShade, 0, 90) { "$it%" }
    Spacer(Modifier.height(8.dp))
    OverlayPartsChoice(HomeLayout.SPOT)
}

@Composable
private fun BarSettings() {
    MutedNote("Your shortcuts as icons along the top, a row of tiles with the one you're on bigger, and its name, Start and time played under it. ▲ goes up to the icons.")
    Spacer(Modifier.height(10.dp))
    Toggle(Icons.Filled.Apps, "Tabs along the top", "A text tab along the top, like the console's Games / Media.", Features.barIcons)
    Toggle(Icons.Filled.Label, "Only the category you're in", "The tab is the category you're on, with ◀ ▶ to step through the categories. Off: your shortcuts as tabs.", Features.barTabsCurrent)
    Slider("Tile size", Features.barTile, 70, 150) { "$it%" }
    Slider("The game you're on pops out", Features.barGrow, 100, 200) { "$it%" }
    Toggle(Icons.Filled.ViewModule, "Square tiles", "Off: the game look's own shape (Settings › Game look).", Features.barSquares)
    Toggle(Icons.Filled.Label, "Names on every tile", "The one you're on always has its name under it.", Features.barNames)
    Toggle(Icons.Filled.PlayArrow, "Name, Start and time played", "Lower down at the left, over the game's art.", Features.barStart)
    Toggle(Icons.Filled.Image, "Game art behind", "The game you're on across the screen. Off: your theme.", Features.barArt)
    if (Features.barArt.state.value) Toggle(Icons.Filled.Image, "Cut at the middle", "The art fills the top half and fades out at the middle of the screen. Off: it covers the whole screen.", Features.barArtCut)
    Spacer(Modifier.height(8.dp))
    OverlayPartsChoice(HomeLayout.BAR)
}


@Composable
private fun ShelfSettings() {
    MutedNote("The category as a bookcase: every game shows its spine, the one you're on is pulled out to show its front. ◀ ▶ along the shelf, ▲ ▼ to the shelf above / below. The more games, the more shelves (up to three on screen, then it scrolls).")
    Spacer(Modifier.height(10.dp))
    StyleChoice("The games stand", Features.ShelfStand.values(), Features.shelfStand.state.value, { it.label }) { Features.shelfStand.value = it }
    MutedNote(
        when (Features.shelfStand.state.value) {
            Features.ShelfStand.SPINES -> "Spines out, like books in a bookcase: the most games per shelf."
            Features.ShelfStand.FRONTS -> "Every case facing you, side by side: fewer per shelf, so the bookcase scrolls sooner."
            Features.ShelfStand.ANGLED -> "Every case turned a little, so its spine and its front both show."
        }
    )
    Spacer(Modifier.height(8.dp))
    Toggle(Icons.Filled.ViewModule, "Real sizes", "Every case at its console's real size (a DS case shorter than a PS2 one). Off: all the same size.", Features.shelfRealSizes)
    StyleChoice("Pulled out, it shows", Features.ShelfPulled.values(), Features.shelfPulled.state.value, { it.label }) { Features.shelfPulled.value = it }
    Toggle(Icons.Filled.ViewModule, "Bob and turn", "The game you pulled out floats a little and turns gently left and right.", Features.shelfBob)
    StyleChoice("Shelves", Features.ShelfLook.values(), Features.shelfLook.state.value, { it.label }) { Features.shelfLook.value = it }
    Toggle(Icons.Filled.ViewModule, "Bookcase background", "The whole screen behind the shelves is the bookcase: a back panel, the side uprights and a top crown. Off: your wallpaper shows between the shelves.", Features.shelfBackdrop)
    Toggle(Icons.Filled.Style, "Original spines and cases", "Each game's own spine scan and case front from ScreenScraper. A game without a spine scan gets its console's spine with its name. Off: the console's spine and Voltcap's case template. Same switch as Game look › Original cases.", Features.originalCases)
    Toggle(Icons.Filled.Style, "Spines cut from box scans", "A game without a spine scan uses a strip cut out of its full box scan instead (it can be cut in the wrong place). Off: the console's spine with its name. Same switch as in Game look.", Features.spineCuts)
    // Update 108: games still on the drawn spine are looked up again (ScreenScraper keeps adding box scans).
    Spacer(Modifier.height(6.dp))
    val asked = Features.spineRecheckAt.state.value.toLongOrNull()
    Chip(if (asked != null && System.currentTimeMillis() - asked < 60_000) "✓ Looking again as you move along" else "Look for spines again", accent = false) {
        Features.spineRecheckAt.value = System.currentTimeMillis().toString()
    }
    MutedNote("A game shows a drawn spine only when ScreenScraper has no spine or full box scan for it. They're looked up again every two weeks; this asks again now for each game as the shelf comes to it.")
}


@Composable
private fun HubSettings() {
    MutedNote("The game's art full screen, your profile, shortcuts and the clock along the top, a row of tiles with the game you're on big at the left, and three cards under it (Play / Continue, Achievements, Details). ▲ goes to the shortcuts, ▼ to the cards.")
    Spacer(Modifier.height(10.dp))
    Toggle(Icons.Filled.Image, "Game art behind", "The game you're on across the whole screen. Off: your theme.", Features.hubArt)
    Toggle(Icons.Filled.EmojiEvents, "Next achievements", "The achievements still to earn slide slowly past, faint, behind the Achievements card (the ones most players get first at the top).", Features.resNextAch)
}

@Composable
private fun ResumeSettings() {
    MutedNote("The game you're on as a big square with four shortcut tiles under it, the next games in a row, and Discover cards (Play / Continue, Achievements, Details). ◀ on the first game goes to the shortcut tiles, ▼ to the cards.")
    Spacer(Modifier.height(10.dp))
    Toggle(Icons.Filled.Image, "Game art behind", "The game you're on across the whole screen. Off: your theme.", Features.resArt)
    Toggle(Icons.Filled.EmojiEvents, "Next achievements", "The achievements still to earn slide slowly past, faint, behind the Achievements card (the ones most players get first at the top).", Features.resNextAch)
}

@Composable
private fun ChannelSettings() {
    MutedNote("Pages of wide tiles, a curved bar at the bottom with the clock; the round button on the left changes category, the one on the right opens the game's details. ▲ ▼ ◀ ▶ move along the tiles.")
    Spacer(Modifier.height(10.dp))
    Slider("Tiles per row", Features.chanCols, 3, 6) { "$it" }
    BackdropChoice(Features.chanLight, Features.chanArt)
    Toggle(Icons.Filled.Label, "Name on the tile you're on", "A small name pill along the bottom of the selected tile.", Features.chanNames)
    Toggle(Icons.Filled.Label, "Scroll on continuously", "◀ ▶ move a column over and the grid glides along with you. Off: pages that turn at the edge.", Features.gridsContinuous)
    MutedNote("The game window (A on a game: Home · Details · Start) is in Home › Game window, for every layout.")
    Toggle(Icons.Filled.Label, "Corner symbol", "The circle bottom left shows the category as a symbol (the console's small picture, or its logo in one colour). Off: the console's logo in its colours, or the category's name.", Features.chanSymbols)
    Toggle(Icons.Filled.Label, "Corner buttons", "Profile, Settings, notifications and save sync as round buttons in the top right corner (▲ from the top row reaches them).", Features.chanCorner)
    Toggle(Icons.Filled.Label, "Status bar", "The status bar along the top on this layout (off by default: the grid has its clock and corner buttons).", Features.chanStatusBar)
}

@Composable
private fun CircuitSettings() {
    MutedNote("Voltcap's own layout: your games as chips on a circuit board, zig-zagging along a glowing power line. The game you're on is powered up and a spark runs to it as you move. ◀ ▶ along the board, ▼ Play / Settings / Details, ▲ the category.")
    Spacer(Modifier.height(10.dp))
    StyleChoice("Board colours", Features.CircuitColors.values(), Features.circuitColors.state.value, { it.label }) { Features.circuitColors.value = it }
    Toggle(Icons.Filled.Label, "Current flowing", "Little dashes of current run along the power line while you rest on a game (still with Lite effects).", Features.circuitCurrent)
    Toggle(Icons.Filled.Label, "Chip labels", "U1, U2… printed beside each chip, like a real board.", Features.circuitLabels)
    StyleChoice(
        "Behind the board", arrayOf(true, false), Features.circuitBackdrop.state.value,
        { if (it) "The circuit board" else "Your background (theme scene, picture…)" }
    ) { Features.circuitBackdrop.value = it }
    MutedNote("Your background is whatever Theme › Background is set to: the theme's animated scene, your own picture or video.")
}

