package com.shelf.launcher.ui

import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.BackgroundStyle
import com.shelf.launcher.data.CardArtType
import com.shelf.launcher.data.DockPosition
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout

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
    val chargeLimitWorks: Boolean
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
        "whoplays" -> return on(x.raAccounts >= 2)
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
        // Music player position and Round shortcuts: every layout (style homes: in the status bar / moved in Edit Home /
        // off; the shortcuts' corner ☰ or Edit Home spot).
        "musicspot", "roundshort" -> R1Fit.Show
        // Corners: every layout — the covers and tiles where the layout uses your corners, and the category screen's
        // tiles (Categories, reached from every layout) everywhere.
        "corners" -> R1Fit.Show

        // Look (the game look): layouts that draw your look — not the ones with their own (layoutOwnLook: Console hub,
        // Resume, Channel grid, Hero tiles, Twin icons / desk, Live tiles, Game shelf, Disc station, Record crate, Card
        // binder, Arcade). Green Core draws it at the sphere; Pocket Quest only with Games shown as › Your game look.
        "gamelook" -> lookFit(l)
        // Cartridge shape (your pack): Cartridges & discs drawn with the pack (not Real shells, unless disc games go on a
        // cartridge), with some of the pack downloaded, where the look is drawn.
        "cartlook" -> if (look == CardArtType.CARTRIDGE_DISC && (cartNow() != LookCart.REAL || Features.cartsOnly.state.value) &&
            com.shelf.launcher.data.Packs.anyCarts()) lookFit(l) else R1Fit.Hide
        // Disc for every game: the Discs look where the look is drawn, and Disc station (always discs, LookStyles.disc).
        "discstyle" -> if (l == HomeLayout.DISCS) R1Fit.Show else if (look == CardArtType.DISC) lookFit(l) else R1Fit.Hide
        // Case for every game: the case looks where the look is drawn, and Game shelf (its pulled-out game is a case).
        "casestyle" -> when {
            l == HomeLayout.SHELF -> R1Fit.Show
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
        "drawnmedia" -> if (look == CardArtType.DISC || look == CardArtType.CARTRIDGE_DISC) lookFit(l) else R1Fit.Hide
        // Art position (the cover in the case template): the case looks, where the look is drawn.
        "artdrop" -> if (look == CardArtType.CASE || look == CardArtType.CASE_REAL) lookFit(l) else R1Fit.Hide

        // Console badges: every layout that puts them on its games — not Disc station, Card binder, Arcade, Game shelf or
        // Hero carousel (they draw no badges); RomM's browser shows them on every layout once RomM is set up.
        "badges" -> on(x.romm || l !in setOf(HomeLayout.DISCS, HomeLayout.CARDS, HomeLayout.ARCADE, HomeLayout.SHELF, HomeLayout.HERO))
        // Achievements place (the chip on the game you're on): the Voltcap row, Grid, Arcade, Case stacks and the style
        // homes drawn with StyleArt — not Game shelf, Disc station, Card binder, Record crate, Hero carousel, TV room,
        // Circuit board (its readout has the charge bar) or the two Pocket layouts; Pocket Quest only with your look.
        "progressbar" -> when (l) {
            HomeLayout.SHELF, HomeLayout.DISCS, HomeLayout.CARDS, HomeLayout.CRATE, HomeLayout.HERO, HomeLayout.TV,
            HomeLayout.CIRCUIT, HomeLayout.POCKET_CLASSIC, HomeLayout.POCKET_RIBBON -> R1Fit.Hide
            // (Update 204: hidden, not greyed, while Pocket Quest shows plain covers.)
            HomeLayout.POCKET -> on(Features.pqMedia.state.value != Features.PqMedia.COVERS)
            else -> R1Fit.Show
        }
        // Game size: the layouts that read it (GameSize) — not Disc station, the Twin layouts, Custom layout, the Pocket
        // layouts, Green Core (their own sizes, or none) or Pocket Quest (its own Size in Layout options).
        "gamesize" -> on(l !in setOf(
            HomeLayout.DISCS, HomeLayout.TWIN_DESK, HomeLayout.TWIN_ICONS, HomeLayout.CUSTOM,
            HomeLayout.POCKET_CLASSIC, HomeLayout.POCKET_RIBBON, HomeLayout.GREENCORE, HomeLayout.POCKET
        ))
        // Endless row: every layout but Hero tiles (it stops at the ends there); Green Core and Pocket Quest go round.
        "endless" -> on(endlessApplies(l))
        // Carousel: the layouts with a carousel look (Voltcap row, Hero, Grid, TV room, Side menu, Case stacks).
        // Green Core's own "carousel" is its Layout options › Browse, not this.
        "carousel" -> on(carouselApplies(l))
        // Row position: the Voltcap row (and the old Tile home).
        "rowpos" -> on(classic)
        // Game header tight / roomy: the Voltcap row with its game header on (Settings › Home › Game header).
        "headertight" -> on(classic && Features.homeHeader.state.value)
        // Gaps: the two Twin layouts (their own gap each).
        "twingap" -> on(l == HomeLayout.TWIN_ICONS || l == HomeLayout.TWIN_DESK)
        // Each layout's own option: Game shelf, Spotlight, Icon bar, Cross menu, Pocket Quest (theme).
        "shelflook" -> on(l == HomeLayout.SHELF)
        "spotlight" -> on(l == HomeLayout.SPOT)
        "bartabs" -> on(l == HomeLayout.BAR)
        "crosscut" -> on(l == HomeLayout.CROSS)
        "pqtheme" -> on(l == HomeLayout.POCKET)
        // Magnifier (second screen page): with a second display and the Magnifier page switched on (Settings › Screens);
        // greyed while Dual screen is off.
        "magnifier" -> when {
            !x.secondDisplay || !Features.secondMagnifier.state.value -> R1Fit.Hide
            !a.useSecondScreen -> R1Fit.Grey("The second screen is off — turn on Dual screen first.")
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
 * Update 202: the look tiles' fit in layout [l]: hidden where the layout draws its games its own way.
 * Update 204: and while Pocket Quest shows plain covers (they were greyed out there: only what applies shows now).
 */
private fun lookFit(l: HomeLayout): R1Fit = when {
    layoutOwnLook(l) != null -> R1Fit.Hide
    l == HomeLayout.POCKET && Features.pqMedia.state.value == Features.PqMedia.COVERS -> R1Fit.Hide
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
