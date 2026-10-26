package com.shelf.launcher.ui

import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.BackgroundStyle
import com.shelf.launcher.data.CardArtType
import com.shelf.launcher.data.DockPosition
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.statusOn

/*
 * Update 202: where each R1 tile shows — the one place that decides it (QuickSettingsCard asks here for every tile it
 * builds, and an L1 shortcut to a tile asks here too).
 *
 * A tile shows only where pressing it changes something: in the home layout in use, with the game look in use, on this
 * device. Everywhere else it is hidden — not greyed out — and keeps its place in your tile order (Edit) for when it
 * applies again. Greyed out is kept only for "not right now, and you can change that": the greyed tile's press says
 * what to change. Worked out from what each tile's setting changes and which layouts / looks read that setting.
 */

/** What the R1 panel knows when it decides (built by QuickSettingsCard). */
internal class R1Ctx(
    /** The home screen's settings; null when the panel has no home behind it (then only the device tiles are built). */
    val a: AppearanceSettings?,
    /** A second display is connected right now (the AYN Thor's lower screen, a TV…): Dual screen, Magnifier. */
    val secondDisplay: Boolean,
    /** This device has Bluetooth. */
    val bluetooth: Boolean,
    /** Controller lights were found (ControllerLights.describe). */
    val lights: Boolean,
    /** RomM is set up (Settings › Accounts › RomM). */
    val romm: Boolean,
    /** RetroAchievements accounts saved ("Who plays" asks between two). */
    val raAccounts: Int,
    /** The theme in use can take its colours from the game or the music (Colours tile). */
    val themeRecolorable: Boolean,
    /** The battery driver's 80 % charge limit works on this device (read with the device tiles). */
    val chargeLimitWorks: Boolean,
    /** Update 329: the console of the game you're on (null: none / not known) — the Cartridge and Disc tiles' games. */
    val focusPlatform: String? = null
)

/** Update 202: a tile shows, is hidden (it would do nothing here), or shows greyed out with [Grey.why]. */
internal sealed class R1Fit {
    object Show : R1Fit()
    object Hide : R1Fit()
    /** Not right now, but you can change that: the greyed tile's press shows [why] (what to change). */
    class Grey(val why: String) : R1Fit()
}

/** Update 202: whether tile [id] shows, given [x] — see the comment at the top of this file. */
internal fun r1TileFit(id: String, x: R1Ctx): R1Fit {
    fun on(b: Boolean): R1Fit = if (b) R1Fit.Show else R1Fit.Hide

    // ---- Device and app tiles: the same on every layout. ----
    when (id) {
        // Wi-Fi, Do not disturb, play / pause music, Save Vault, nav bar, overlays over games, battery, battery watcher,
        // battery saver, volume mixer, Android settings: Android / Voltcap-wide, every layout and device.
        "wifi", "dnd", "music", "saves", "navbar", "fps", "battery", "batterywatch", "saver", "mixer", "settings" -> return R1Fit.Show
        // Bluetooth: devices with Bluetooth.
        "bt" -> return on(x.bluetooth)
        // Controller lights: only where lights were found.
        "lights" -> return on(x.lights)
        // Dual screen (useSecondScreen): only with a second display connected — on a one-screen handheld it switches
        // nothing (the Twin / Pocket layouts stack both halves there whatever it says).
        "dual" -> return on(x.secondDisplay)
        // Rescan, missing art, theme: the library and the theme, every layout.
        "rescan", "scrape", "theme" -> return R1Fit.Show
        // Colours follow (theme / game / music): themes that can be recoloured.
        "colors" -> return on(x.themeRecolorable)
        // RomM: once it's set up.
        "romm" -> return on(x.romm)
        // Who plays (ask at start-up): only with two RetroAchievements accounts saved — with one there's no one to ask.
        // (Update 332: the profile selector — always there.)
        "whoplays" -> return on(true)
        // The game's power profile: devices with the performance tuner, with a game you're on (both already decide
        // whether the tile can be built at all: its label is read only then — QuickSettingsCard's profileLabel).
        "profile" -> return R1Fit.Show
    }
    // The handheld's own switches (DeviceControls.available already lists only the hardware this device has: fan,
    // performance, TDP, buttons…). The 80 % charge limit is listed on every device, so here it hides where the
    // battery driver has no limit (it used to show "Not supported").
    if (id.startsWith("dev_")) return if (id == "dev_CHARGE_LIMIT") on(x.chargeLimitWorks) else R1Fit.Show

    // ---- Home tiles: need the home screen (not built without it). ----
    val a = x.a ?: return R1Fit.Hide
    val l = a.homeLayout
    val look = a.cardArtType
    val classic = l == HomeLayout.CLASSIC || l == HomeLayout.TILES

    return when (id) {
        // Layout, Sort, Edit home: every layout (the sort also orders Timeline's same-date / undated games).
        "layout", "sort", "edithome" -> R1Fit.Show
        // Update 245: the game popup's style — only while A opens a game window at all (Settings › Home › Layout options ›
        // Game window); off, A starts the game, so the tile is greyed and says where to switch it back on.
        "popupstyle" -> if (com.shelf.launcher.data.Features.gameWindow.value) R1Fit.Show
            else R1Fit.Grey("Game window is off: A starts the game straight away. Switch it on in Settings › Home › Layout options › Game window.")
        // Music player position and Round shortcuts: every layout (style homes: in the status bar / moved in Edit Home /
        // off; the shortcuts' corner ☰ or Edit Home spot).
        // (Update 329, found by the R1 tile walk on the AYN Thor: with the player taken off in Edit Home (✕) only the status
        // bar can hold it — on a layout without one the tile changed nothing; greyed there, saying how to bring it back.)
        "musicspot" -> if ("music" in Features.hiddenParts.state.value && !a.statusOn)
            R1Fit.Grey("The music player was taken off the home screen in Edit Home and this layout has no status bar for it. Bring it back in Edit Home (+ Add › Taken off › Music player).")
            else R1Fit.Show
        // (Update 226, #98: not on a style home that has the shortcuts in its own menu — the corner ☰ copy stays out
        // there, so the tile switched something you couldn't see.)
        "roundshort" -> on(!(l.styleHome && styleHasShortcuts(l)))
        // Corners: every layout — the covers and tiles where the layout uses your corners, and the category screen's
        // tiles (Categories, reached from every layout) everywhere.
        "corners" -> R1Fit.Show

        // Look (the game look): layouts that draw your look — not the ones with their own (layoutOwnLook: Console hub,
        // Resume, Channel grid, Hero tiles, Twin icons / desk, Live tiles, Game shelf, Disc station, Record crate, Card
        // binder, Arcade). Green Core draws it at the sphere; Pocket Quest only with Games shown as › Your game look.
        "gamelook" -> lookFit(l)
        // Cartridge shape (your pack): Cartridges & discs drawn with the pack (not Real shells, unless disc games go on a
        // cartridge), with some of the pack downloaded, where the look is drawn.
        // (Update 325: on Real shells too — the tile is the way back and forth between each console's own and the pack.)
        "cartlook" -> if (look == CardArtType.CARTRIDGE_DISC && com.shelf.launcher.data.Packs.anyCarts()) {
            // (Update 329, found by the R1 tile walk: on a disc game the pack shape changes nothing — disc games stay discs
            // unless they go on a cartridge (Game look › Disc games: On a cartridge).)
            val fp = x.focusPlatform
            if (lookFit(l) == R1Fit.Show && fp != null && mediaKind(fp) == "disc" && !Features.cartsOnly.state.value)
                R1Fit.Grey("The game you're on is a disc game — it stays a disc. The cartridge shape is for cartridge games (or put disc games on a cartridge: Game look › Disc games).")
            else lookFit(l)
        } else R1Fit.Hide
        // Disc for every game: the Discs look where the look is drawn, and Disc station (always discs, LookStyles.disc).
        "discstyle" -> (if (l == HomeLayout.DISCS) R1Fit.Show else if (look == CardArtType.DISC) lookFit(l) else R1Fit.Hide).let { f ->
            // (Update 329, found by the R1 tile walk: a cartridge game on a disc keeps its own console's plain disc whatever
            // console is picked (update 141: no other console's printed disc on it) — the pick is for the disc games.)
            val fp = x.focusPlatform
            if (f == R1Fit.Show && fp != null && mediaKind(fp) != "disc")
                R1Fit.Grey("The game you're on is a cartridge game: as a disc it keeps its own console's plain disc. The disc picked for every game is for disc games (PlayStation, GameCube…).")
            else f
        }
        // Case for every game: the case looks where the look is drawn, and Game shelf (its pulled-out game is a case).
        "casestyle" -> when {
            // (Update 330: Case flow's cases take the case picked for every game too.)
            l == HomeLayout.SHELF || l == HomeLayout.CASEFLOW -> R1Fit.Show
            look == CardArtType.CASE || look == CardArtType.CASE_REAL -> lookFit(l)
            else -> R1Fit.Hide
        }
        // Spine for every game: the Spines look where the look is drawn (not Pocket Quest: spines show as covers there),
        // Game shelf (always spines) and TV room's Shelf of spines.
        "spinestyle" -> when {
            l == HomeLayout.SHELF -> R1Fit.Show
            l == HomeLayout.TV && Features.tvRoomGames.state.value == TvRoom.Games.SHELF -> R1Fit.Show
            look == CardArtType.COVER && Features.spineLook.state.value && l != HomeLayout.POCKET -> lookFit(l)
            else -> R1Fit.Hide
        }
        // The game's own photo (drawn / real discs and cartridges): the Discs and Cartridges & discs looks, where drawn.
        // (Update 326: no tile since 325 — "Own photos / Drawn only" went with "Drawn only"; an old L1 shortcut to it says so,
        // LauncherScreen. Kept for that shortcut's check.)
        "drawnmedia" -> if (look == CardArtType.DISC || look == CardArtType.CARTRIDGE_DISC) lookFit(l) else R1Fit.Hide
        // Art position (the cover in the case template): the case looks, where the look is drawn.
        "artdrop" -> if (look == CardArtType.CASE || look == CardArtType.CASE_REAL) lookFit(l) else R1Fit.Hide

        // Console badges: every layout that puts them on its games — not Disc station, Card binder, Arcade, Game shelf or
        // Hero carousel (they draw no badges); RomM's browser shows them on every layout once RomM is set up.
        // (Update 326, audit: Hero carousel draws them on its covers since 317al, and Pocket ribbon on its cartridge when
        // that's on (Layout options) — the tile was hidden there while the badges still showed.)
        "badges" -> on(x.romm || when (l) {
            HomeLayout.POCKET_RIBBON -> Features.ribbonCart.state.value
            else -> l !in setOf(HomeLayout.DISCS, HomeLayout.CARDS, HomeLayout.ARCADE, HomeLayout.SHELF,
                // (Update 226, #98: the Pocket layouts draw no console badges either.)
                HomeLayout.POCKET_CLASSIC,
                // (Update 330: Memory card and Case flow draw none — the case's printed band names its console; nor do
                // Storage boxes and File select, their own icons and blocks.)
                HomeLayout.MEMCARD, HomeLayout.CASEFLOW, HomeLayout.STORAGE, HomeLayout.FILESELECT,
                // (Nor the Cardboard box: its cases by their spines, the spine names the console.)
                HomeLayout.BOX, HomeLayout.ORBIT, HomeLayout.AIRY, HomeLayout.NIGHT,
                // (Update 344: Bubble home's bubbles carry none.)
                HomeLayout.BUBBLES, HomeLayout.CAMERA, HomeLayout.TERMINAL)
        })
        // Update 326 (audit): Outline (Settings › Game look › Coloured frame, R1 tile of 325) — only where it's drawn: the
        // Voltcap row, Tile home, Grid, Hero carousel, TV room (their cards), and the carousel view of Side menu / Case
        // stacks. The other style homes draw their own focus ring, so the tile switched nothing there.
        "outline" -> when {
            !(!l.styleHome || (l == HomeLayout.SIDE || l == HomeLayout.STACKS) && l.name in Features.carouselLayouts.state.value) -> R1Fit.Hide
            // (Update 329, found by the R1 tile walk: cartridges and discs stand free, with no frame round them — the Voltcap
            // row, Tile home, Hero carousel and the carousel views draw the outline only round covers, cases and tiles.)
            (look == CardArtType.DISC || look == CardArtType.CARTRIDGE_DISC) &&
                (classic || l == HomeLayout.HERO || l.name in Features.carouselLayouts.state.value) -> R1Fit.Hide
            // (Update 329: the Grid draws it only with "The game you're on: Glow" — Outline always rings it, Raise & spin
            // never does — except round the case looks' cases.)
            l == HomeLayout.GRID && l.name !in Features.carouselLayouts.state.value && look != CardArtType.CASE && look != CardArtType.CASE_REAL &&
                Features.gridFocus.state.value != Features.GridFocus.GLOW ->
                R1Fit.Grey("On the Grid the outline goes with The game you're on: Glow (Settings › Home › Layout options). With ${Features.gridFocus.state.value.label} it isn't drawn.")
            else -> R1Fit.Show
        }
        // Achievements place (the chip on the game you're on): the Voltcap row, Grid, Arcade, Case stacks and the style
        // homes drawn with StyleArt — not Game shelf, Disc station, Card binder, Record crate, Hero carousel, TV room,
        // Circuit board (its readout has the charge bar) or the two Pocket layouts; Pocket Quest only with your look.
        // (Update 329, found by the R1 tile walk: the carousel view — Voltcap row, Grid, Side menu, Case stacks — draws no
        // achievement chip on its games, so the tile changed nothing while it's on.)
        "progressbar" -> if (l in setOf(HomeLayout.CLASSIC, HomeLayout.GRID, HomeLayout.SIDE, HomeLayout.STACKS) &&
            l.name in Features.carouselLayouts.state.value) R1Fit.Hide else when (l) {
            HomeLayout.SHELF, HomeLayout.DISCS, HomeLayout.CARDS, HomeLayout.CRATE, HomeLayout.HERO, HomeLayout.TV,
            HomeLayout.CIRCUIT, HomeLayout.POCKET_CLASSIC, HomeLayout.MEMCARD, HomeLayout.CASEFLOW,
            HomeLayout.STORAGE, HomeLayout.FILESELECT, HomeLayout.BOX, HomeLayout.ORBIT, HomeLayout.AIRY, HomeLayout.NIGHT,
            HomeLayout.BUBBLES, HomeLayout.CAMERA, HomeLayout.TERMINAL -> R1Fit.Hide
            // (Update 326, audit: Pocket ribbon's cartridge on top is drawn with StyleArt, which puts the chip on it.)
            HomeLayout.POCKET_RIBBON -> on(Features.ribbonCart.state.value)
            // (Update 204: hidden, not greyed, while Pocket Quest shows plain covers.)
            HomeLayout.POCKET -> on(Features.pqMedia.state.value != Features.PqMedia.COVERS)
            else -> R1Fit.Show
        }
        // Game size: the layouts that read it (GameSize) — not Disc station, the Twin layouts, Custom layout, the Pocket
        // layouts, Green Core (their own sizes, or none) or Pocket Quest (its own Size in Layout options).
        "gamesize" -> on(l !in setOf(
            // Update 344: Blades sizes the game to its blade; Bubble home its bubbles to the page.
            HomeLayout.BLADES, HomeLayout.BUBBLES, HomeLayout.CAMERA, HomeLayout.PANELS, HomeLayout.MAGAZINE, HomeLayout.SHOP, HomeLayout.TERMINAL,
            HomeLayout.DISCS, HomeLayout.TWIN_DESK, HomeLayout.TWIN_ICONS, HomeLayout.TWIN_SHOWCASE, HomeLayout.CUSTOM,
            HomeLayout.POCKET_CLASSIC, HomeLayout.POCKET_RIBBON, HomeLayout.GREENCORE, HomeLayout.POCKET,
            // Update 330: their own Icon size / Case size (Layout options).
            HomeLayout.MEMCARD, HomeLayout.CASEFLOW, HomeLayout.BOX, HomeLayout.STORAGE, HomeLayout.FILESELECT,
            HomeLayout.ORBIT, HomeLayout.AIRY, HomeLayout.NIGHT
        ))
        // (Update 239, #93: the Cube menu needs no rule of its own — its games are drawn with StyleArt in your look, size
        // (GameSize), badges and achievement chip, its row goes round with Endless; Carousel / Row position / Twin gaps
        // hide there as on every style home, and Background hides while its own room backdrop is on: styleOwnsBackground.)
        // Endless row: every layout but Hero tiles (it stops at the ends there); Green Core and Pocket Quest go round.
        "endless" -> on(endlessApplies(l))
        // Carousel: the layouts with a carousel look (Voltcap row, Hero, Grid, TV room, Side menu, Case stacks).
        // Green Core's own "carousel" is its Layout options › Browse, not this.
        "carousel" -> on(carouselApplies(l))
        // Row position: the Voltcap row and the Tile home (update 235, #15/#16: a layout again — Where the tiles sit).
        // (Update 230, #09: on Hero carousel it puts the covers on the left / right — QuickSettings.kt.)
        "rowpos" -> on(classic || l == HomeLayout.HERO)
        // Update 307: the category names — the layouts with the category bar (Voltcap row, Tile home, Grid), and any layout
        // with the category bar added as a part (Edit Home › + Add).
        // (Update 315: every layout — each keeps its own choice, CategoryBars; where the categories are the layout's own
        // menu it's greyed, saying so.)
        "categories" -> when {
            l in com.shelf.launcher.data.CategoryBars.MENU -> R1Fit.Grey("On this layout the categories are its own menu, so they stay.")
            // (Update 329, found by the R1 tile walk on the AYN Thor: Twin icons draws no category name of its own — only
            // the Additional category bar (Layout options) shows it, so the tile changed nothing without it.)
            l == HomeLayout.TWIN_ICONS && !Features.twinSharedCategories.state.value &&
                "TWIN_ICONS:category" in Features.hiddenParts.state.value ->
                R1Fit.Grey("The Category name was taken off Twin icons in Edit Home. Bring it back there (+ Add › Taken off), or switch on Layout options › Additional category bar.")
            else -> R1Fit.Show
        }
        // Game header tight / roomy: the Voltcap row with its game header on (Settings › Home › Game header).
        // (Update 235, #15/#16: the Voltcap row only — the Tile home shows no game header.)
        "headertight" -> on(l == HomeLayout.CLASSIC && Features.homeHeader.state.value)
        // Gaps: the two Twin layouts (their own gap each).
        "twingap" -> on(l == HomeLayout.TWIN_ICONS || l == HomeLayout.TWIN_DESK || l == HomeLayout.TWIN_SHOWCASE)
        // Each layout's own option: Game shelf, Spotlight, Icon bar, Cross menu, Pocket Quest (theme).
        "shelflook" -> on(l == HomeLayout.SHELF)
        "spotlight" -> on(l == HomeLayout.SPOT)
        // (Update 226, #98: Icon bar's tabs only show with its icons on.)
        "bartabs" -> on(l == HomeLayout.BAR && Features.barIcons.state.value)
        "crosscut" -> on(l == HomeLayout.CROSS)
        "pqtheme" -> on(l == HomeLayout.POCKET)
        // Magnifier (second screen page): with a second display and the Magnifier page switched on (Settings › Screens);
        // greyed while Dual screen is off.
        "magnifier" -> when {
            !x.secondDisplay || !Features.secondMagnifier.state.value -> R1Fit.Hide
            !a.useSecondScreen -> R1Fit.Grey("The second screen is off — turn on Dual screen first.")
            // (Update 329, found by the R1 tile walk: the two-screen layouts — Twin desk / icons / showcase, the Pocket
            // layouts — put their own bottom half on the second screen, so the page switched there didn't show. Voltcap's
            // normal second screen, opened from the layout (TwinScreens.dashboard), shows it.)
            l.isTwin && !TwinScreens.dashboard ->
                R1Fit.Grey("${l.label} uses the second screen for its lower half. Open Voltcap's normal second screen from the layout (Layout options › Open normal second screen) to see the Magnifier there.")
            else -> R1Fit.Show
        }
        // Background (art + videos / art / animation / still): every layout whose background shows — not Green Core or
        // the two Pocket layouts (always their own); greyed where the layout's own background is on (its option).
        "background" -> backgroundFit(l)
        // Background picture fit: where Voltcap's background picture shows — as Background, and not on Hero carousel
        // (its own hero over it) or TV room (the room fills the screen); greyed while another background is picked, the
        // layout draws the game's art itself, or the Voltcap row's games aren't at the bottom.
        "herofit" -> when {
            l == HomeLayout.HERO || l == HomeLayout.TV -> R1Fit.Hide
            backgroundFit(l) != R1Fit.Show -> backgroundFit(l)
            // (Update 204: hidden where the layout shows the game's art itself — it was greyed out.)
            l.styleHome && styleArtBehind(l) -> R1Fit.Hide
            a.backgroundStyle != BackgroundStyle.ART ->
                R1Fit.Grey("The background is ${a.backgroundStyle.label.replaceFirstChar { it.lowercase() }} — the fit is for the game's own picture (Background: game art).")
            l == HomeLayout.CLASSIC && a.dockPosition != DockPosition.BOTTOM ->
                R1Fit.Grey("${l.label} shows the whole picture only with its row at the bottom (Row position).")
            else -> R1Fit.Show
        }
        // ("media", "look3d" and other ids from older versions: not built any more, nothing to decide.)
        else -> R1Fit.Show
    }
}

/**
 * Update 326: every tile id the R1 panel builds that r1TileFit decides for (the device tiles "dev_…" are decided by their
 * prefix). A test (R1Compat326Test) reads QuickSettings.kt and fails when a tile is added without a rule here — the
 * Outline tile of 325 fell through to "show everywhere" that way.
 */
internal val R1_RULED_TILES: Set<String> = setOf(
    "wifi", "dnd", "music", "saves", "navbar", "fps", "battery", "batterywatch", "saver", "mixer", "settings",
    "bt", "lights", "dual", "rescan", "scrape", "theme", "colors", "romm", "whoplays", "profile",
    "layout", "sort", "edithome", "popupstyle", "musicspot", "roundshort", "corners",
    "gamelook", "cartlook", "discstyle", "casestyle", "spinestyle", "artdrop",
    "badges", "outline", "progressbar", "gamesize", "endless", "carousel", "rowpos", "categories", "headertight", "twingap",
    "shelflook", "spotlight", "bartabs", "crosscut", "pqtheme", "magnifier", "background", "herofit"
)

/**
 * Update 202: the look tiles' fit in layout [l]: hidden where the layout draws its games its own way.
 * Update 204: and while Pocket Quest shows plain covers (they were greyed out there: only what applies shows now).
 */
private fun lookFit(l: HomeLayout): R1Fit = when {
    layoutOwnLook(l) != null -> R1Fit.Hide
    l == HomeLayout.POCKET && Features.pqMedia.state.value != Features.PqMedia.LOOK -> R1Fit.Hide
    else -> R1Fit.Show
}

/** Update 202: the Background tile's fit in layout [l] (styleOwnsBackground: the layout covers Voltcap's background). */
private fun backgroundFit(l: HomeLayout): R1Fit = when {
    l == HomeLayout.GREENCORE || l == HomeLayout.POCKET_CLASSIC || l == HomeLayout.POCKET_RIBBON -> R1Fit.Hide
    !l.styleHome || !styleOwnsBackground(l) -> R1Fit.Show
    // A custom layout's own colour / picture comes with its file.
    l == HomeLayout.CUSTOM -> R1Fit.Hide
    // (Update 204: hidden while the layout draws its own background — they were greyed out. They show again once its
    // Layout options › Background is set to Voltcap's.)
    else -> R1Fit.Hide
}

/** Update 202: a second display is connected right now (as SecondScreenHost looks for one): the Dual screen tile's switch. */
internal fun hasSecondDisplay(context: android.content.Context): Boolean = runCatching {
    val dm = context.getSystemService(android.hardware.display.DisplayManager::class.java) ?: return@runCatching false
    dm.displays.any { d ->
        d.displayId != android.view.Display.DEFAULT_DISPLAY && d.isValid && (d.flags and android.view.Display.FLAG_PRIVATE) == 0
    }
}.getOrDefault(false)

/** Update 202: this device has Bluetooth at all. */
internal fun hasBluetooth(context: android.content.Context): Boolean = runCatching {
    (context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter != null
}.getOrDefault(true)
