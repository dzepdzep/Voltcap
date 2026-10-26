package com.shelf.launcher.ui

import com.shelf.launcher.data.categoryBarHere
import com.shelf.launcher.data.withCategoryBar
import com.shelf.launcher.data.dockHere
import com.shelf.launcher.data.withDockHere
import com.shelf.launcher.data.statusOn
import android.app.NotificationManager
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.ui.theme.ShelfTheme

/** Update 219: the controller lights found last time this run (null = not looked yet) — see foundLightsOrNull. */
@Volatile private var r1LightsCache: List<String>? = null

/**
 * The quick-settings strip at the top of the R1 panel, like Android's own
 * pull-down: Wi-Fi, Bluetooth, Do Not Disturb, single/dual screen, Android
 * settings, and a brightness control.
 *
 * Android limits what an app may switch by itself: since Android 10 Wi-Fi
 * and Bluetooth can only be switched from Android's own panel, so those
 * tiles open it. Do Not Disturb and brightness need a one-time permission
 * each; until it's given, the tile takes you to the screen to give it.
 */
@Composable
fun QuickSettingsCard(
    dualScreen: Boolean,
    onToggleDualScreen: () -> Unit,
    lightsEnabled: Boolean = false,
    lightsColor: Int = 0,
    lightsBrightness: Int = 70,
    onLightsChange: (enabled: Boolean, color: Int, brightness: Int) -> Unit = { _, _, _ -> },
    lightsOffWithScreensaver: Boolean = true,
    lightsFlashOnUnlock: Boolean = true,
    onLightsAdvancedChange: (offWithScreensaver: Boolean, flashOnUnlock: Boolean) -> Unit = { _, _ -> },
    musicPlaying: Boolean = false,
    /** The home row's current sort (short form, e.g. "A–Z"); null hides the Sort tile. */
    sortLabel: String? = null,
    onSort: (() -> Unit)? = null,
    /** The home layout's game look (Covers, Real cases…); null hides the Game look tile. */
    gameLook: String? = null,
    onCycleGameLook: (() -> Unit)? = null,
    /** Home / library / game tiles (layout, carousel, theme, rescan, art, power) and settings jumps; null hides them. */
    home: R1Home? = null
) {
    com.shelf.launcher.Breadcrumbs.mark("QuickSettingsCard")
    val context = LocalContext.current
    // L1 / R1 flip the pages only while this card is shown (QuickSheet reads it).
    DisposableEffect(Unit) { R1Pages.active = true; onDispose { R1Pages.active = false } }
    // Bumped whenever something may have changed outside the app (coming back
    // from a Settings screen, or right after a tap), so every tile re-reads.
    var refresh by remember { mutableStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Changes made in Android's shade (or Voltcap's own tiles) while this is open: read everything again.
    val sync by com.shelf.launcher.device.SystemSync.version.collectAsState()
    val wifiOn = remember(refresh, sync) { QuickControls.isWifiOn(context) }
    val bluetoothOn = remember(refresh, sync) { QuickControls.isBluetoothOn(context) }
    val dndOn = remember(refresh, sync) { QuickControls.isDndOn(context) }
    val brightness = remember(refresh, sync) { QuickControls.brightnessPercent(context) }
    val canWriteBrightness = remember(refresh) { QuickControls.canWriteSettings(context) }

    // Whether any controller lights exist is a binder / sysfs probe, so it runs
    // off the main thread and only again on resume — never per recomposition.
    // (Update 219: the panel used to start with "no lights" and add the RGB tile a moment later, which moved every
    // tile after it under your finger. Lights once found are kept — for this run, and across restarts as
    // "lights_found" — so the tile list is right from the first frame.)
    val lightsSaved = remember { context.getSharedPreferences("r1_tiles", android.content.Context.MODE_PRIVATE).getBoolean("lights_found", false) }
    val foundLightsOrNull by produceState(initialValue = r1LightsCache, refresh) {
        value = withContext(Dispatchers.IO) {
            runCatching { com.shelf.launcher.data.ControllerLights.describe(context) }.getOrDefault(emptyList()).let { found ->
                // (The handheld's light service can answer "none" for a moment after Voltcap starts — seen on the Thor —
                // so lights once found stay found: a "none" never takes the tile away again.)
                if (found.isEmpty() && !r1LightsCache.isNullOrEmpty()) return@let r1LightsCache!!
                r1LightsCache = found
                if (found.isNotEmpty() && !lightsSaved) context.getSharedPreferences("r1_tiles", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("lights_found", true).apply()
                found
            }
        }
    }
    val foundLights = foundLightsOrNull.orEmpty()
    val hasLights = foundLights.isNotEmpty() || lightsSaved
    var showLights by remember { mutableStateOf(false) }
    var showOverlayLayout by remember { mutableStateOf(false) }
    var showGameSize by remember { mutableStateOf(false) }
    // Update 202: holding the Look tile — the choices of the main look in use (GameLookPopup).
    var showLookChoices by remember { mutableStateOf(false) }
    // A tile's hold list (update 104: the Player and Round shortcuts tiles): title and the choices.
    var choiceSheet by remember { mutableStateOf<Pair<String, List<R1Choice>>?>(null) }
    // Update 185: presses that change the look can be undone for a few seconds (R1Undo).
    val undoScope = androidx.compose.runtime.rememberCoroutineScope()
    val liveHome = androidx.compose.runtime.rememberUpdatedState(home)
    val undoable: (String, () -> Unit) -> Unit = remember { { label, run -> r1Undoable(label, { liveHome.value?.appearance }, undoScope, run) } }
    var showLayoutList by remember { mutableStateOf(false) }
    val lightsTileFocus = remember { FocusRequester() }

    // Which tiles show and in what order (Edit: pick a tile, move it, hide / show it). Kept per device.
    val tilePrefs = remember { context.getSharedPreferences("r1_tiles", android.content.Context.MODE_PRIVATE) }
    var hidden by remember { mutableStateOf(tilePrefs.getStringSet("hidden", emptySet())!!.toSet()) }
    var order by remember { mutableStateOf(tilePrefs.getString("order", "").orEmpty().split(',').filter { it.isNotBlank() }) }
    var editing by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<String?>(null) }
    var moreOpen by remember { mutableStateOf(tilePrefs.getBoolean("more_open", false)) }
    fun toggleHidden(id: String) {
        hidden = if (id in hidden) hidden - id else hidden + id
        tilePrefs.edit().putStringSet("hidden", hidden).apply()
    }
    // The handheld's own switches (80% limit, fan, performance…): read off the main thread.
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var deviceRefresh by remember { mutableStateOf(0) }
    val deviceTiles by produceState(initialValue = emptyList<Pair<com.shelf.launcher.device.DeviceControl, com.shelf.launcher.device.ControlState>>(), deviceRefresh, refresh, sync) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                // (0.6.18: looked up here, off the main thread — the Controller tile's hold opens it.)
                com.shelf.launcher.device.DeviceControls.handheldSettingsIntent(context)
                com.shelf.launcher.device.DeviceControls.available(context).map { it to com.shelf.launcher.device.DeviceControls.read(context, it) }
            }.getOrDefault(emptyList())
        }
    }

    val perfCfg by com.shelf.launcher.data.perf.PerfSettings.state.collectAsState()
    remember { com.shelf.launcher.data.perf.PerfSettings.config(context) }

    // Battery: percent and time left, read again every half minute while the panel is open.
    val batteryNow by produceState(remember { readBattery(context) }, refresh, sync) {
        while (true) {
            value = withContext(Dispatchers.IO) { readBattery(context) }
            kotlinx.coroutines.delay(30_000)
        }
    }
    var showBattery by remember { mutableStateOf(false) }
    var showBatteryWatch by remember { mutableStateOf(false) }
    var showSaver by remember { mutableStateOf(false) } // (Update 187)
    var showProfile by remember { mutableStateOf(false) }
    // The game's power profile, short ("Own · Balanced"): a prefs read, off the main thread.
    val profileLabel by produceState<String?>(null, home?.gameKey, refresh, sync, showProfile) {
        val key = home?.gameKey
        value = if (key == null || !com.shelf.launcher.device.tune.Tuner.supported) null
        else withContext(Dispatchers.IO) { runCatching { r1ProfileLabel(context, key, home.gamePlatform) }.getOrNull() }
    }
    // Media volume, shown at once when you change it.
    var volume by remember(refresh, sync) { mutableStateOf(mediaVolumePercent(context)) }
    var brightnessNow by remember(brightness) { mutableStateOf(brightness) }
    // "Most used first" (Edit): how often each tile was pressed.
    var mostUsed by remember { mutableStateOf(tilePrefs.getBoolean("most_used", false)) }
    var uses by remember { mutableStateOf(tilePrefs.all.filterKeys { it.startsWith("use_") }.mapNotNull { (k, v) -> (v as? Int)?.let { k.removePrefix("use_") to it } }.toMap()) }
    fun countUse(id: String) {
        // (Update 329: a test walk's presses don't count towards "most used".)
        if (TestHooks.previewOnly) return
        val n = (uses[id] ?: 0) + 1
        uses = uses + (id to n)
        tilePrefs.edit().putInt("use_$id", n).apply()
    }

    // Update 202: what decides which tiles show here (r1TileFit, R1TileRules.kt) — read again on resume / after a tap.
    val secondDisplayHere = remember(refresh) { hasSecondDisplay(context) }
    val bluetoothHere = remember { hasBluetooth(context) }
    val rommSetUp = remember(refresh) { runCatching { com.shelf.launcher.data.RommCredentialsStore(context).load() != null }.getOrDefault(false) }
    val raAccountCount = remember(refresh) { runCatching { com.shelf.launcher.data.RaCredentialsStore(context).usernames().count { it != null } }.getOrDefault(0) }
    val r1Ctx = R1Ctx(
        a = home?.appearance,
        secondDisplay = secondDisplayHere,
        bluetooth = bluetoothHere,
        lights = hasLights,
        romm = rommSetUp,
        raAccounts = raAccountCount,
        themeRecolorable = home?.themeKey?.let { tk -> com.shelf.launcher.ui.theme.AppTheme.values().firstOrNull { it.name == tk }?.recolorable } == true,
        // (Listed on every device; read with the other device tiles. Not read yet: nothing to hide.)
        chargeLimitWorks = deviceTiles.firstOrNull { it.first == com.shelf.launcher.device.DeviceControl.CHARGE_LIMIT }?.second?.supported ?: true,
        focusPlatform = home?.gamePlatform
    )

    // Every tile, in the default order; the saved order is applied on top (new tiles go at the end).
    // (Update 202: every tile is built; r1TileFit then drops the ones that do nothing here — see the end of the list.)
    val allTiles = buildList {
        add(R1Tile("wifi", if (wifiOn) Icons.Filled.Wifi else Icons.Filled.WifiOff, "Wi-Fi", wifiOn) { QuickControls.toggleWifi(context); refresh++ })
        add(R1Tile("bt", if (bluetoothOn) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled, "Bluetooth", bluetoothOn) { QuickControls.toggleBluetooth(context); refresh++ })
        add(R1Tile("dnd", Icons.Filled.DoNotDisturbOn, "Do not disturb", dndOn) { QuickControls.toggleDnd(context); refresh++ })
        // (Update 202: whether it shows — lights found — is decided with every other tile in r1TileFit, R1TileRules.kt.)
        add(R1Tile("lights", Icons.Filled.Lightbulb,
            when { !lightsEnabled -> "RGB system"; com.shelf.launcher.data.Features.lightsHeldOff.state.value -> "RGB off"; else -> "RGB on" },
            lightsEnabled && !com.shelf.launcher.data.Features.lightsHeldOff.state.value,
            dot = Color(com.shelf.launcher.data.LightsTheme.resolve(lightsColor)).copy(alpha = 1f), focus = lightsTileFocus) { showLights = true })
        add(R1Tile("music", Icons.Filled.MusicNote, if (musicPlaying) "Pause music" else "Play Spotify", musicPlaying) { MusicApps.playPause(context, musicPlaying); refresh++ })
        // The memory-card save browser, one press from the R1 panel.
        add(R1Tile("saves", Icons.Filled.SdCard, com.shelf.launcher.data.SAVES_NAME, false) { SaveExplorerHook.request() })
        // Edit Home: drag and resize the music player, round shortcuts and the other home parts (the panel steps aside).
        add(R1Tile("edithome", Icons.Filled.OpenWith, "Edit\nhome", false) { HomeEditState.open() })
        add(R1Tile("dual", Icons.Filled.Smartphone, if (dualScreen) "Dual screen" else "One screen", dualScreen) { onToggleDualScreen() })
        // Hide the navigation bar / gesture pill (same switch as Settings › Device › Gesture hint).
        run {
            val hideNav = com.shelf.launcher.data.GestureHint.immersiveState.value
            add(R1Tile("navbar", if (hideNav) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Hide nav\nbar", hideNav) {
                com.shelf.launcher.data.GestureHint.setImmersive(context, !hideNav)
            })
        }
        // One tile for everything shown over games: a press steps Off → Stats → Music → Trophies → All
        // three; one on its own sits top left, together they spread over the corners (PerfConfig).
        // Hold it to choose freely which are on and where each goes.
        val ovStats = perfCfg.showDuringGames
        val ovMusic = perfCfg.musicDuringGames
        val ovRa = perfCfg.raDuringGames
        val overlayMode = when {
            ovStats && ovMusic && ovRa -> "All"
            !ovStats && !ovMusic && !ovRa -> "Off"
            ovStats && !ovMusic && !ovRa -> "Stats"
            !ovStats && ovMusic && !ovRa -> "Music"
            !ovStats && !ovMusic && ovRa -> "Trophies"
            else -> listOfNotNull("Stats".takeIf { ovStats }, "Music".takeIf { ovMusic }, "Trophies".takeIf { ovRa }).joinToString("+")
        }
        add(R1Tile("fps", Icons.Filled.Layers, "Overlay\n$overlayMode", overlayMode != "Off", longAction = { showOverlayLayout = true }) {
            val next = when (overlayMode) {
                "Off" -> Triple(true, false, false)
                "Stats" -> Triple(false, true, false)
                "Music" -> Triple(false, false, true)
                "Trophies" -> Triple(true, true, true)
                else -> Triple(false, false, false)
            }
            com.shelf.launcher.data.perf.PerfSettings.update(context) { it.copy(showDuringGames = next.first, musicDuringGames = next.second, raDuringGames = next.third) }
            com.shelf.launcher.ui.PerfOverlayService.reconfigure(context)
            if (next.first && com.shelf.launcher.data.perf.PerfAutoSetup.possible) {
                Thread { runCatching { com.shelf.launcher.data.perf.PerfAutoSetup.run(context.applicationContext) } }.start()
            }
        })
        if (sortLabel != null && onSort != null) add(R1Tile("sort", Icons.AutoMirrored.Filled.Sort, sortLabel, false) { onSort() })
        // What the games are drawn as: Covers → Cases → Cartridges & discs → Art (update 202: the four main looks, each as
        // you last had it); hold: the choices of the one in use, with small pictures (GameLookPopup).
        if (gameLook != null && onCycleGameLook != null) {
            // (Update 177 greyed it out in a layout that draws its games its own way. Update 202: hidden there instead —
            // r1TileFit, R1TileRules.kt.)
            add(R1Tile("gamelook", Icons.Filled.Album, "Look\n$gameLook", gameLook != "Covers",
                longAction = if (home != null) ({ showLookChoices = true }) else null) { onCycleGameLook() })
        }
        // The console badges: off, the small logo, the console's own big logo.
        if (home != null) {
            val F = com.shelf.launcher.data.Features
            // (Update 125: the Media tile — Covers → Cartridges & discs → Discs only — is part of the Look tile now, with
            // Cartridges only added; saved tile orders just skip the old "media" id.)
            // Update 125: Off → Console badges → Logo badges (the console's own logo on its colour) → Off.
            val badgesOn = F.consoleBadges.state.value
            val logoBadges = F.consoleBadgeLogos.state.value
            add(R1Tile("badges", Icons.Filled.Label,
                when { !badgesOn -> "Badges\noff"; logoBadges -> "Logo\nbadges"; else -> "Console\nbadges" }, badgesOn) {
                when {
                    !F.consoleBadges.value -> { F.consoleBadges.value = true; F.consoleBadgeLogos.value = false }
                    !F.consoleBadgeLogos.value -> F.consoleBadgeLogos.value = true
                    else -> { F.consoleBadges.value = false; F.consoleBadgeLogos.value = false }
                }
            })
            // (Update 325, Toni: "an R1 tile for the game you're on — with a colour outline or not": Coloured frame on / off.)
            run {
                val a = home.appearance
                add(R1Tile("outline", Icons.Filled.CropFree, if (a.focusBorder) "Outline\non" else "Outline\noff", a.focusBorder) {
                    home.setAppearance(home.appearance.copy(focusBorder = !home.appearance.focusBorder))
                })
            }
            // Update 128: your pack's cartridge shape — tap: the next of its 14 shapes; hold: every shape.
            // (Update 131: only with some of your cartridge pack downloaded — Settings › Game look › Extra art packs.)
            // (Update 202: while the pack is drawn — Cartridges & discs with Your pack's shapes or Clear plastic, or disc
            // games on a cartridge. Original / Custom and Opaque / Clear left its hold list: they're the Look tile's
            // Cartridge choices now.)
            // (Update 202: when it shows is decided in r1TileFit, R1TileRules.kt.)
            // (Update 325, Toni: "changing the R1 cartridge can't go back to each cartridge's own original look": the tile
            // starts on and comes back round to each console's own cartridge — Own → each pack shape → Own; the hold list has
            // it first.)
            run {
                val look = F.cartLook.state.value
                val own = cartNow() == LookCart.REAL && !F.cartsOnly.state.value
                add(R1Tile("cartlook", Icons.Filled.Memory, "Cartridge\n" + (if (own) "Own" else look.label), !own,
                    longAction = {
                        choiceSheet = "Cartridge" to (
                            listOf(R1Choice("Each console's own", own) { pickCart(LookCart.REAL) }) +
                                CartLook.values().map { l -> R1Choice(l.label, !own && F.cartLook.value == l) { F.cartLook.value = l; if (cartNow() == LookCart.REAL) pickCart(LookCart.PACK) } }
                            )
                    }) {
                    val all = CartLook.values()
                    when {
                        own -> { F.cartLook.value = all.first(); pickCart(LookCart.PACK) }
                        F.cartLook.value == all.last() -> pickCart(LookCart.REAL)
                        else -> F.cartLook.value = all[all.indexOf(F.cartLook.value) + 1]
                    }
                })
            }
            // Update 129: one console's disc / case / spine for every game, only while that look is on — tap: the next
            // console (or each game's own), hold: the list. (Like the Cartridge tile for the Cartridges look.)
            // (Update 202: where each shows — that look, and the layouts that always draw discs / cases / spines — is
            // decided in r1TileFit, R1TileRules.kt; the lists are read only when pressed.)
            fun styleTile(id: String, word: String, pref: com.shelf.launcher.data.Features.StringPref, consoles: () -> List<String>, after: () -> Unit = {}) {
                val now = pref.state.value
                // (Update 329, seen on the AYN Thor: a long official name — "Nintendo Entertainment System" — was cut off in
                // the tile's small caption; there it's the short name, the hold list keeps the full ones.)
                val name = if (now.isEmpty()) "Own" else com.shelf.launcher.data.ConsoleNames.label(now)
                    .let { if (it.length > 14) com.shelf.launcher.data.ConsoleNames.short(now) else it }
                add(R1Tile(id, Icons.Filled.Album, "$word\n$name", now.isNotEmpty(),
                    longAction = {
                        choiceSheet = "$word for every game" to (
                            listOf(R1Choice("Each game's own", pref.value.isEmpty()) { pref.value = "" }) +
                                consoles().map { p -> R1Choice(com.shelf.launcher.data.ConsoleNames.label(p), pref.value == p) { pref.value = p; after() } }
                            )
                    }) {
                    pref.value = LookStyles.next(pref.value, consoles())
                    if (pref.value.isNotEmpty()) after()
                })
            }
            // (A picked disc is drawn, so "Drawn discs" goes on with it.)
            // (Update 325: no longer turns "Drawn only" on — it's gone.)
            styleTile("discstyle", "Disc", F.discStyle, { LookStyles.DISCS })
            styleTile("casestyle", "Case", F.caseStyle, { LookStyles.cases() })
            styleTile("spinestyle", "Spine", F.spineStyle, { LookStyles.spines() })
            // (Update 159: the "3D on / off" tile (look3d) stood here. Update 200: removed — most looks are 3D already, and the
            // pre-rendered 3D models it switched on are retired; the 3D cartridges looks (Game look tile, Settings › Game look)
            // draw the drawn shells with real depth. A saved tile order with "look3d" in it just skips it.)
        }
        if (home != null) {
            val a = home.appearance
            // Home layout: Row → Tiles → Hero → Grid → TV room (each keeps its own look).
            // Tap = the next layout, hold = the list of every layout (there are fifteen now).
            add(R1Tile("layout", Icons.Filled.Dashboard, "Layout\n" + a.homeLayout.label.removeSuffix(" home"), false, longAction = { showLayoutList = true }) {
                val all = com.shelf.launcher.data.HomeLayout.pickable
                home.setAppearance(a.copy(homeLayout = all[(all.indexOf(a.homeLayout) + 1) % all.size]))
            })
            // Update 245 (R1 Popup Style): which game window A opens in this layout — the same settings as Settings › Home ›
            // Layout options › Game window (Features.gameWindow / classicWindowLayouts / winBanner), so the two never
            // disagree. Tap: the next style; hold: the list. Only the styles that work today are listed; with the game
            // window off the tile is greyed (r1TileFit) and says where to switch it on.
            run {
                val F = com.shelf.launcher.data.Features
                val lname = a.homeLayout.name
                val styles = PopupStyle.values()
                val now = PopupStyle.current(lname)
                add(R1Tile("popupstyle", Icons.Filled.OpenInNew, "Popup\n" + now.short, false,
                    longAction = {
                        choiceSheet = "Game popup · ${a.homeLayout.label}" to styles.map { st -> R1Choice(st.label, PopupStyle.current(lname) == st) { PopupStyle.set(lname, st) } }
                    }) {
                    if (F.gameWindow.value) PopupStyle.set(lname, styles[(PopupStyle.current(lname).ordinal + 1) % styles.size])
                })
            }
            // Update 179: the two-screen layouts — the gap between their tiles (tap: the next, hold: the list).
            // (Update 202: every tile is built; which show where is decided in r1TileFit, R1TileRules.kt.)
            run {
                // (Update 329, found by the R1 tile walk: Twin showcase reads Twin icons' gap (twinGap, at least 4 dp) — the
                // tile changed Twin desk's gap there, so nothing moved.)
                val showcase = a.homeLayout == com.shelf.launcher.data.HomeLayout.TWIN_SHOWCASE
                val pref = if (a.homeLayout == com.shelf.launcher.data.HomeLayout.TWIN_ICONS || showcase) com.shelf.launcher.data.Features.twinGap else com.shelf.launcher.data.Features.twinDeskGap
                val gaps = TWIN_GAPS.filter { it <= (if (pref === com.shelf.launcher.data.Features.twinGap) 32 else 24) && (!showcase || it >= 4) }
                val now = pref.state.value
                add(R1Tile("twingap", Icons.Filled.ViewModule, "Gaps\n" + gapLabel(now), false,
                    longAction = { choiceSheet = "Gap between tiles" to gaps.map { g -> R1Choice(gapLabel(g), now == g) { pref.value = g } } }) {
                    pref.value = gaps[(gaps.indexOf(now).coerceAtLeast(0) + 1) % gaps.size]
                })
            }
            // Options of the layout in use (they only show while that layout is the one on screen — r1TileFit).
            run {
                val f = com.shelf.launcher.data.Features
                run {
                    add(R1Tile("shelflook", Icons.Filled.ViewModule, "Shelves\n" + f.shelfLook.state.value.label, false,
                        longAction = { f.shelfBackdrop.value = !f.shelfBackdrop.value }) {
                        val all = com.shelf.launcher.data.Features.ShelfLook.values()
                        f.shelfLook.value = all[(f.shelfLook.value.ordinal + 1) % all.size]
                    })
                    // (Update 226, #98: each Spotlight look reads different options — Big picture: where the tiles sit, the
                    // tile names and the title; Home menu: where the tiles sit only; Row & menu: the tile names only (its
                    // tiles always sit on top). The tile and its hold sheet offer only what the look on screen reads.)
                    val spotLookNow = f.spotLook.state.value
                    if (spotLookNow == com.shelf.launcher.data.Features.SpotLook.ROW_MENU) {
                        add(R1Tile("spotlight", Icons.Filled.ViewModule, "Names\n" + when (f.spotNameAt.state.value) { com.shelf.launcher.data.Features.SpotName.RIGHT -> "beside"; com.shelf.launcher.data.Features.SpotName.BELOW -> "under"; com.shelf.launcher.data.Features.SpotName.ABOVE -> "above"; else -> "hidden" }, false,
                            longAction = {
                                choiceSheet = "Spotlight · tile names" to com.shelf.launcher.data.Features.SpotName.values().map { n ->
                                    R1Choice("Tile name: " + n.label, f.spotNameAt.value == n) { f.spotNameAt.value = n }
                                }
                            }) {
                            val all = com.shelf.launcher.data.Features.SpotName.values()
                            f.spotNameAt.value = all[(f.spotNameAt.value.ordinal + 1) % all.size]
                        })
                    } else add(R1Tile("spotlight", Icons.Filled.ViewModule, "Tiles\n" + f.spotTilesAt.state.value.label, false,
                        // Hold (update 104): where the names go — the tile's name and the big title.
                        longAction = {
                            val F = com.shelf.launcher.data.Features
                            val sides = com.shelf.launcher.data.Features.SpotSide.values().map { n -> R1Choice("Tiles: " + n.label, F.spotTilesAt.value == n) { F.spotTilesAt.value = n } }
                            choiceSheet = if (spotLookNow == com.shelf.launcher.data.Features.SpotLook.HOME_MENU) "Spotlight · tiles" to sides
                            else "Spotlight · tiles & names" to (sides +
                                com.shelf.launcher.data.Features.SpotName.values().map { n -> R1Choice("Tile name: " + n.label, F.spotNameAt.value == n) { F.spotNameAt.value = n } } +
                                    com.shelf.launcher.data.Features.SpotAcross.values().map { n -> R1Choice("Title: " + n.label, F.spotTitleAt.value == n) { F.spotTitleAt.value = n } } +
                                    com.shelf.launcher.data.Features.SpotAlong.values().map { n -> R1Choice("Title: " + n.label, F.spotTitleRow.value == n) { F.spotTitleRow.value = n } }
                                )
                        }) {
                        f.spotTilesAt.value = if (f.spotTilesAt.value == com.shelf.launcher.data.Features.SpotSide.TOP) com.shelf.launcher.data.Features.SpotSide.BOTTOM else com.shelf.launcher.data.Features.SpotSide.TOP
                    })
                    add(R1Tile("bartabs", Icons.Filled.Label, if (f.barTabsCurrent.state.value) "Tab\ncategory" else "Tabs\nshortcuts", f.barTabsCurrent.state.value,
                        // (Update 235, #08: hold — where the games sit, and the art's cut at the middle, in one sheet.)
                        longAction = {
                            val F = com.shelf.launcher.data.Features
                            choiceSheet = "Icon bar · games & art" to (com.shelf.launcher.data.Features.SpotSide.values().map { n -> R1Choice("Games: " + n.label, F.barGamesAt.value == n) { F.barGamesAt.value = n } } +
                                R1Choice("Art cut at the middle", F.barArtCut.value) { F.barArtCut.value = !F.barArtCut.value })
                        }) {
                        f.barTabsCurrent.value = !f.barTabsCurrent.value
                    })
                    // Folders → logos on tiles → logos cut out (update 104).
                    add(R1Tile("crosscut", Icons.Filled.Label,
                        when { f.crossFolders.state.value -> "Consoles\nfolders"; f.crossLogoCut.state.value -> "Logos\ncut out"; else -> "Logos\non tiles" }, !f.crossFolders.state.value) {
                        when {
                            f.crossFolders.value -> { f.crossFolders.value = false; f.crossLogoCut.value = false }
                            !f.crossLogoCut.value -> f.crossLogoCut.value = true
                            else -> { f.crossFolders.value = true; f.crossLogoCut.value = false }
                        }
                    })
                    // (Update 202: the Voltcap row's game header — only with the header on, r1TileFit.)
                    add(R1Tile("headertight", Icons.Filled.Label, if (f.homeHeaderTight.state.value) "Header\ntight" else "Header\nroomy", f.homeHeaderTight.state.value) {
                        f.homeHeaderTight.value = !f.homeHeaderTight.value
                    })
                    // Update 202: Pocket Quest — tap: the next theme; hold: pick one.
                    add(R1Tile("pqtheme", Icons.Filled.ViewModule, "Theme\n" + f.pqTheme.state.value.label, false,
                        longAction = {
                            choiceSheet = "Pocket Quest · theme" to com.shelf.launcher.data.Features.PqTheme.values().map { t -> R1Choice(t.label, f.pqTheme.value == t) { f.pqTheme.value = t } }
                        }) {
                        val all = com.shelf.launcher.data.Features.PqTheme.values()
                        f.pqTheme.value = all[(f.pqTheme.value.ordinal + 1) % all.size]
                    })
                }
                // The game's own cartridge / disc photo when one is found (off: Voltcap's drawn ones).
                // (Update 202: named as Cartridges & discs' "The game's own photo" choice, which it flips; it was "Real discs /
                // Drawn discs". Only with that look, as before — r1TileFit.)
                // (Update 325: the "Own photos / Drawn only" tile is gone with "Drawn only" — own photos always.)
                // The second screen as a magnifier of the main one (tap: the Magnifier page on / off, hold: start the capture).
                // (Update 202: with a second display and the Magnifier page on; greyed while Dual screen is off — r1TileFit.)
                run {
                    val on = a.secondPage == com.shelf.launcher.data.SecondPage.MAGNIFIER
                    add(R1Tile("magnifier", Icons.Filled.Cast, if (on) "Magnifier\non" else "Magnifier", on,
                        longAction = { ScreenMirror.requestStart() }) {
                        home.setAppearance(a.copy(secondPage = if (on) com.shelf.launcher.data.SecondPage.ART else com.shelf.launcher.data.SecondPage.MAGNIFIER))
                    })
                }
                // (Update 332: "Who's playing?" — the profile selector, any time; hold: asked at start-up or not.)
                add(R1Tile("whoplays", Icons.Filled.Person, "Who's\nplaying", false,
                    longAction = {
                        val asks = f.startupMode.value != com.shelf.launcher.data.Features.StartupMode.CATEGORIES
                        f.startupMode.value = if (asks) com.shelf.launcher.data.Features.StartupMode.CATEGORIES else com.shelf.launcher.data.Features.StartupMode.PROFILES_LAYOUT
                    }) {
                    ProfilePicker.request = true
                })
            }
            // Where the music player sits: tap = next corner, hold = move it anywhere (Edit Home).
            // (Update 226, #98: a style home shows the player only in the status bar or where it was moved in Edit Home —
            // the other corners drew nothing there, so the tile stepped through places that changed nothing. On style
            // homes it's status bar → Off; hold: status bar, anywhere or off.)
            if (a.homeLayout.styleHome) run {
                val moved = HomeWidgets.position(HomeWidgets.MUSIC) != null
                val off = !a.miniPlayer
                val top = com.shelf.launcher.data.MusicPlayerPosition.TOP_LEFT
                val inBar = !off && !moved && a.musicPlayerPosition == top
                val where = when {
                    off -> "Off"
                    moved -> "Free"
                    a.musicPlayerPosition != top -> "Hidden"
                    !a.statusOn -> "No bar"
                    else -> "Status bar"
                }
                add(R1Tile("musicspot", Icons.Filled.MusicNote, "Player\n" + where, a.miniPlayer,
                    longAction = {
                        choiceSheet = "Music player" to listOf(
                            R1Choice(if (a.statusOn) "In the status bar" else "In the status bar (turn the status bar on)", inBar) {
                                if (moved) HomeWidgets.reset(HomeWidgets.MUSIC)
                                home.setAppearance(a.copy(musicPlayerPosition = top, miniPlayer = true))
                            },
                            R1Choice("Anywhere (move in Edit Home)", !off && moved) {
                                if (off) home.setAppearance(a.copy(miniPlayer = true))
                                HomeEditState.open()
                            },
                            R1Choice("Off", off) { home.setAppearance(a.copy(miniPlayer = false)) }
                        )
                    }) {
                    when {
                        // (Update 329, found by the R1 tile walk: on a style home without Voltcap's status bar — Hero tiles,
                        // Spotlight's Home menu, Channel grid, the Pocket layouts, Twin showcase — "in the status bar" draws
                        // nothing, so the tap only switched an invisible player on and off. There it puts the player at the
                        // top right on its own (as moved in Edit Home, where it can go anywhere) and takes it off again.)
                        // (Taken off in Edit Home, the player can only be in the status bar — the tile is greyed without
                        // one, r1TileFit.)
                        HomeWidgets.hidden(HomeWidgets.MUSIC) ->
                            if (off || a.musicPlayerPosition != top) home.setAppearance(a.copy(musicPlayerPosition = top, miniPlayer = true))
                            else home.setAppearance(a.copy(miniPlayer = false))
                        !a.statusOn -> when {
                            off -> { if (!moved) HomeWidgets.set(HomeWidgets.MUSIC, 1f, 0f); home.setAppearance(a.copy(miniPlayer = true)) }
                            !moved -> HomeWidgets.set(HomeWidgets.MUSIC, 1f, 0f)
                            else -> home.setAppearance(a.copy(miniPlayer = false))
                        }
                        off -> home.setAppearance(a.copy(musicPlayerPosition = top, miniPlayer = true))
                        moved -> { HomeWidgets.reset(HomeWidgets.MUSIC); home.setAppearance(a.copy(musicPlayerPosition = top, miniPlayer = true)) }
                        a.musicPlayerPosition != top -> home.setAppearance(a.copy(musicPlayerPosition = top, miniPlayer = true))
                        else -> home.setAppearance(a.copy(miniPlayer = false))
                    }
                })
            } else run {
                val moved = HomeWidgets.position(HomeWidgets.MUSIC) != null
                val where = if (moved) "Free" else a.musicPlayerPosition.label
                val off = !a.miniPlayer
                add(R1Tile("musicspot", Icons.Filled.MusicNote, "Player\n" + if (off) "Off" else where, a.miniPlayer,
                    longAction = {
                        // Hold (update 104): every place to put it, moving it anywhere, or off.
                        val pos = com.shelf.launcher.data.MusicPlayerPosition.values().map { p ->
                            R1Choice(p.label, !off && !moved && a.musicPlayerPosition == p) {
                                if (moved) HomeWidgets.reset(HomeWidgets.MUSIC)
                                home.setAppearance(a.copy(musicPlayerPosition = p, miniPlayer = true))
                            }
                        }
                        choiceSheet = "Music player" to (pos + listOf(
                            R1Choice("Anywhere (move in Edit Home)", !off && moved) {
                                if (off) home.setAppearance(a.copy(miniPlayer = true))
                                HomeEditState.open()
                            },
                            R1Choice("Off", off) { home.setAppearance(a.copy(miniPlayer = false)) }
                        ))
                    }) {
                    // A tap steps through the places, then Off after the last one, then the first place again (update 125).
                    val all = com.shelf.launcher.data.MusicPlayerPosition.values()
                    val top = com.shelf.launcher.data.MusicPlayerPosition.TOP_LEFT
                    when {
                        // (Update 329, found by the R1 tile walk on the AYN Thor: with the player taken off in Edit Home (✕)
                        // only the status bar can show it — the corners stepped through drew nothing. Then: status bar ↔ off.)
                        HomeWidgets.hidden(HomeWidgets.MUSIC) ->
                            if (off || a.musicPlayerPosition != top) home.setAppearance(a.copy(musicPlayerPosition = top, miniPlayer = true))
                            else home.setAppearance(a.copy(miniPlayer = false))
                        off -> home.setAppearance(a.copy(musicPlayerPosition = all.first(), miniPlayer = true))
                        moved -> { HomeWidgets.reset(HomeWidgets.MUSIC); home.setAppearance(a.copy(miniPlayer = true)) }
                        a.musicPlayerPosition.ordinal == all.lastIndex -> home.setAppearance(a.copy(miniPlayer = false))
                        else -> home.setAppearance(a.copy(musicPlayerPosition = all[a.musicPlayerPosition.ordinal + 1], miniPlayer = true))
                    }
                })
            }
            // Endless: the row goes round from the last game to the first (off: it bumps against the end).
            // (Update 182 greyed it out on Hero tiles, which has no Endless. Update 202: hidden there — r1TileFit.)
            add(R1Tile("endless", Icons.Filled.Repeat, if (a.loopRow) "Endless\non" else "Endless\noff", a.loopRow,
                // (Update 324) Hold: fill the row with repeats, or each game once.
                longAction = {
                    val fill = com.shelf.launcher.data.Features.endlessFill.value
                    choiceSheet = "Endless" to listOf(
                        R1Choice("Fill the row (games repeat)", fill) { com.shelf.launcher.data.Features.endlessFill.value = true; home.setAppearance(a.copy(loopRow = true)) },
                        R1Choice("Each game once", !fill) { com.shelf.launcher.data.Features.endlessFill.value = false; home.setAppearance(a.copy(loopRow = true)) },
                        R1Choice("Off", !a.loopRow) { home.setAppearance(a.copy(loopRow = false)) }
                    )
                }) {
                home.setAppearance(a.copy(loopRow = !a.loopRow))
            })
            // The round shortcut buttons: show / hide. Hold (update 104): where they sit, folded or open, move, off.
            // (Update 329, found by the R1 tile walk on the AYN Thor: "By the row" exists only on Tile home — on every
            // other layout it means off, as in Settings › Home › Round shortcuts. With it the tap switched the Tile home's
            // buttons on and off and nothing changed here. Off the Tile home the tap now shows them in a corner / takes
            // them off this layout, like Settings does, and the tile says whether they show here.)
            val shortcutRowHere = a.homeLayout == com.shelf.launcher.data.HomeLayout.TILES
            val shortcutsHere = a.tileShortcuts && (shortcutRowHere || a.shortcutSpot != com.shelf.launcher.data.ShortcutSpot.ROW)
            add(R1Tile("roundshort", Icons.Filled.Apps, if (shortcutsHere) "Round\nshortcuts" else "Shortcuts\noff", shortcutsHere,
                longAction = {
                    val corner = a.shortcutSpot != com.shelf.launcher.data.ShortcutSpot.ROW && a.shortcutSpot != com.shelf.launcher.data.ShortcutSpot.FREE
                    val folded = !com.shelf.launcher.data.Features.shortcutsOpen.value
                    choiceSheet = "Round shortcuts" to (
                        com.shelf.launcher.data.ShortcutSpot.values().filter { it != com.shelf.launcher.data.ShortcutSpot.FREE }.map { p ->
                            R1Choice(p.label, a.tileShortcuts && a.shortcutSpot == p) { home.setAppearance(a.copy(tileShortcuts = true, shortcutSpot = p)) }
                        } + listOf(
                            R1Choice("Anywhere · move in Edit Home", a.tileShortcuts && a.shortcutSpot == com.shelf.launcher.data.ShortcutSpot.FREE) {
                                home.setAppearance(a.copy(tileShortcuts = true, shortcutSpot = com.shelf.launcher.data.ShortcutSpot.FREE))
                                HomeEditState.open()
                            },
                            R1Choice("In a corner: folded behind ☰", a.tileShortcuts && corner && folded) {
                                com.shelf.launcher.data.Features.shortcutsOpen.value = false
                                if (!corner || !a.tileShortcuts) home.setAppearance(a.copy(tileShortcuts = true, shortcutSpot = com.shelf.launcher.data.ShortcutSpot.BOTTOM_RIGHT))
                            },
                            R1Choice("In a corner: always open", a.tileShortcuts && corner && !folded) {
                                com.shelf.launcher.data.Features.shortcutsOpen.value = true
                                if (!corner || !a.tileShortcuts) home.setAppearance(a.copy(tileShortcuts = true, shortcutSpot = com.shelf.launcher.data.ShortcutSpot.BOTTOM_RIGHT))
                            },
                            R1Choice("Off", !a.tileShortcuts) { home.setAppearance(a.copy(tileShortcuts = false)) }
                        ))
                }) {
                when {
                    shortcutRowHere -> home.setAppearance(a.copy(tileShortcuts = !a.tileShortcuts))
                    // Off on this layout (the Tile home keeps its buttons by the tiles).
                    shortcutsHere -> home.setAppearance(a.copy(shortcutSpot = com.shelf.launcher.data.ShortcutSpot.ROW))
                    // On: in a corner (the one picked before, else bottom right), folded behind ☰ as the hold list's.
                    else -> home.setAppearance(a.copy(tileShortcuts = true,
                        shortcutSpot = if (a.shortcutSpot == com.shelf.launcher.data.ShortcutSpot.ROW) com.shelf.launcher.data.ShortcutSpot.BOTTOM_RIGHT else a.shortcutSpot))
                }
            })
            // Update 129: the Achievements tile (was Progress): where the count / progress sits on the game you're on —
            // covers, cartridges, discs, every layout's pictures and the wide capsule alike.
            // In the corner → Above the name → Next to the name → Under the cover → Off. Hold: the list.
            run {
                val achPref = com.shelf.launcher.data.Features.achPlace
                // Update 151: the four corners are their own steps (Top left → Top right → Bottom left → Bottom right
                // → Above → By name → Under → Off); a corner also sets the trophy corner.
                val spot = AchSpot.of(achPref.state.value, a.trophyCorner)
                fun put(s: AchSpot) {
                    val p = s.place
                    achPref.value = p
                    val byName = p == com.shelf.launcher.data.Features.AchPlace.NAME
                    val under = p == com.shelf.launcher.data.Features.AchPlace.UNDER
                    if ((byName || under) && byName != home.badgeNextToTitle) home.onToggleBadgePlacement()
                    home.setAppearance(when (p) {
                        com.shelf.launcher.data.Features.AchPlace.CORNER ->
                            a.copy(achievementsOnCards = true, coverAchievements = true, trophyOnBanner = true, progressOnBanner = false,
                                trophyCorner = s.corner ?: a.trophyCorner)
                        com.shelf.launcher.data.Features.AchPlace.ABOVE ->
                            a.copy(achievementsOnCards = true, coverAchievements = true, progressOnBanner = true, progressAbove = true)
                        com.shelf.launcher.data.Features.AchPlace.NAME, com.shelf.launcher.data.Features.AchPlace.UNDER ->
                            a.copy(achievementsOnCards = true, coverAchievements = true, progressOnBanner = true, progressAbove = false)
                        com.shelf.launcher.data.Features.AchPlace.OFF -> a.copy(progressOnBanner = false, trophyOnBanner = false)
                    })
                }
                val all = AchSpot.values()
                add(R1Tile("progressbar", Icons.Filled.EmojiEvents, "Achievements\n${spot.short}", spot != AchSpot.OFF,
                    longAction = {
                        choiceSheet = "Achievements on the game you're on" to all.map { s -> R1Choice(s.label, spot == s) { put(s) } }
                    }) {
                    put(all[(spot.ordinal + 1) % all.size])
                })
            }
            // Game size of the layout in use (update 104: every layout, it used to only size the Voltcap row):
            // press cycles Small / Medium / Large, hold opens a slider.
            // (Update 329, found by the R1 tile walk: Case stacks and Grid in their Carousel view draw the carousel, which
            // has its own Game size (Settings › Home › Carousel) and doesn't read the layout's — the tile changed nothing
            // there. With the carousel on it steps the carousel's size.)
            val carouselSized = (a.homeLayout == com.shelf.launcher.data.HomeLayout.STACKS || a.homeLayout == com.shelf.launcher.data.HomeLayout.GRID) &&
                a.homeLayout.name in com.shelf.launcher.data.Features.carouselLayouts.state.value
            if (carouselSized) {
                val cs = com.shelf.launcher.data.Features.carouselSize.state.value
                val csLabel = when { cs < 93 -> "Small"; cs > 110 -> "Large"; else -> "Medium" }
                add(R1Tile("gamesize", Icons.Filled.PhotoSizeSelectLarge, "Size\n$csLabel", false, longAction = { showGameSize = true }) {
                    com.shelf.launcher.data.Features.carouselSize.value = GameSize.STEPS.firstOrNull { it > cs + 2 } ?: GameSize.STEPS.first()
                })
            } else add(R1Tile("gamesize", Icons.Filled.PhotoSizeSelectLarge, "Size\n" + GameSize.label(a), false, longAction = { showGameSize = true }) {
                GameSize.step(a) { home.setAppearance(it) }
            })
            // Update 136, Art drop: how far the cover drops below the case template's top band (Box art looks) — each look its
            // own. Update 137: the panel closes and the slider shows on the home screen, under the game (above it with the
            // row at the bottom), changing it live.
            // (Update 200: no longer for the 3D models' window, and the "Cart colour" tile (cartcolour) for their custom
            // pieces is gone: the pre-rendered 3D models are retired — the 3D cartridges looks are drawn shells.)
            // (Update 202: the case looks, where the look is drawn — r1TileFit.)
            run {
                // (Update 137: Art position — moved with drag / D-pad and sized with pinch / L1 R1.)
                val moved = ArtPlace.moved(false)
                add(R1Tile("artdrop", Icons.Filled.VerticalAlignBottom, "Art position\n" + if (moved) "Moved" else "Centred", moved) {
                    HomeAdjust.open = HomeAdjust.Target(HomeAdjust.Kind.ART_DROP, model = false)
                })
            }
            // Corners of the covers and tiles (update 104): Square → Rounded → Extra round → Capsule.
            add(R1Tile("corners", Icons.Filled.RoundedCorner, "Corners\n" + a.cardCorners.label, a.cardCorners != com.shelf.launcher.data.CardCorners.SQUARE) {
                val all = com.shelf.launcher.data.CardCorners.values()
                home.setAppearance(a.copy(cardCorners = all[(a.cardCorners.ordinal + 1) % all.size]))
            })
            // Update 307: the category names on the home screen (Settings › Categories › On the home screen › Category
            // name): press steps Just the current one → All categories → Off; hold: the list. Where the layout draws the
            // category bar (r1TileFit).
            run {
                // (Update 315: on every layout, each keeping its own — CategoryBars; a caption is just shown or off.)
                val lay = a.homeLayout
                val bar = a.categoryBarHere
                val order = com.shelf.launcher.data.CategoryBars.choices(lay)
                fun short(b: com.shelf.launcher.data.CategoryBar) = com.shelf.launcher.data.CategoryBars.short(lay, b)
                add(R1Tile("categories", Icons.Filled.Category, "Categories\n" + short(bar), bar != com.shelf.launcher.data.CategoryBar.OFF,
                    longAction = {
                        choiceSheet = "Category names · " + lay.label to order.map { b -> R1Choice(short(b), bar == b) { home.setAppearance(a.withCategoryBar(lay, b)) } }
                    }) {
                    home.setAppearance(a.withCategoryBar(lay, order[(order.indexOf(bar).coerceAtLeast(0) + 1) % order.size]))
                })
            }
            // Where the game row sits (Voltcap row and Tile home): Top → Middle → Bottom. (Update 202: r1TileFit.)
            run {
                // (Update 230, #09: Hero carousel had no tile for its cover side — there this tile puts the covers on the
                // left / right (Hero settings › Covers); the Voltcap row and Tile home keep Top → Middle → Bottom.)
                if (a.homeLayout == com.shelf.launcher.data.HomeLayout.HERO) {
                    val coversRight = com.shelf.launcher.data.Features.heroCoversRight.state.value
                    // (Update 234, #30: with Covers beside the hero set to None only the info moves — it sits opposite the covers' side.)
                    val noCovers = com.shelf.launcher.data.Features.heroSideCovers.state.value == com.shelf.launcher.data.Features.HeroSideCovers.NONE
                    add(R1Tile("rowpos", Icons.Filled.SwapVert, if (noCovers) "Info\n" + (if (coversRight) "Left" else "Right") else "Covers\n" + if (coversRight) "Right" else "Left", false) {
                        com.shelf.launcher.data.Features.heroCoversRight.toggle()
                    })
                } else
                // (Update 315: Tile home's own place — in the middle by default; the Voltcap row keeps its own.)
                add(R1Tile("rowpos", Icons.Filled.SwapVert, "Row\n" + a.dockHere.label, false) {
                    val all = com.shelf.launcher.data.DockPosition.values()
                    home.setAppearance(a.withDockHere(all[(a.dockHere.ordinal + 1) % all.size]))
                })
            }
            // RomM (update 93): browse your RomM server and download games (only once it's set up — rommSetUp, r1TileFit).
            add(R1Tile("romm", Icons.Filled.CloudDownload, "RomM", false) { RommUi.showing = true })
            // Update 348 (Toni: "changing carousels in Case flow is gone where it's needed the most"): Case flow's own
            // carousel shapes on the Carousel tile — tap: the next shape, hold: the list (Layout options keeps the sliders).
            if (a.homeLayout == com.shelf.launcher.data.HomeLayout.CASEFLOW) {
                val now = CaseFlowShape.current()
                add(R1Tile("carousel", Icons.Filled.ViewCarousel, "Carousel\n" + (now?.label ?: "Your own"), true,
                    longAction = {
                        choiceSheet = "Case flow carousel" to CaseFlowShape.values().map { v -> R1Choice(v.label, now == v) { v.apply() } }
                    }) {
                    val all = CaseFlowShape.values()
                    all[((now?.ordinal ?: -1) + 1) % all.size].apply()
                })
            } else {
            // Carousel look of the layout in use (TV room: Games beside the TV › Carousel).
            val tv = a.homeLayout == com.shelf.launcher.data.HomeLayout.TV
            val carouselOn = if (tv) com.shelf.launcher.data.Features.tvRoomGames.state.value == TvRoom.Games.CAROUSEL
            else a.homeLayout.name in com.shelf.launcher.data.Features.carouselLayouts.state.value
            // Hold (update 104): the carousel's view (Tucked, Record flip, Wheel, Deck, Flat row).
            // Update 113: the hold list also has how many games show each side of the middle one (1–5).
            val sides = com.shelf.launcher.data.Features.tvCarouselSide.state.value
            val edge = com.shelf.launcher.data.Features.carouselEdge.state.value
            // (Update 182 greyed it out in a layout without a carousel. Update 202: hidden there, as Settings does — r1TileFit.)
            add(R1Tile("carousel", Icons.Filled.ViewCarousel, if (carouselOn) "Carousel\n" + com.shelf.launcher.data.Features.carouselView.state.value.label + " · " + (if (edge) "edge" else "$sides") else "Carousel", carouselOn,
                longAction = {
                    val turnOn = {
                        if (!carouselOn) {
                            if (tv) com.shelf.launcher.data.Features.tvRoomGames.value = TvRoom.Games.CAROUSEL
                            else com.shelf.launcher.data.Features.carouselLayouts.toggle(a.homeLayout.name)
                        }
                    }
                    // Update 182: "Carousel off" first (at the end it was below the screen's edge on small screens).
                    val off = R1Choice("Carousel off", !carouselOn) {
                        if (carouselOn) {
                            if (tv) com.shelf.launcher.data.Features.tvRoomGames.value = TvRoom.Games.SPINES
                            else com.shelf.launcher.data.Features.carouselLayouts.toggle(a.homeLayout.name)
                        }
                    }
                    choiceSheet = "Carousel view · games each side" to (listOf(off) + CarouselView.values().map { v ->
                        // (Update 137: the 3D-case views say where they're real cases.)
                        R1Choice(v.label + if (v.cases) " (3D cases with a case look)" else "", com.shelf.launcher.data.Features.carouselView.value == v) {
                            com.shelf.launcher.data.Features.carouselView.value = v
                            // (Update 134: Arc and Turned row come with their glossy floor.)
                            if (v.cases) { com.shelf.launcher.data.Features.carouselReflect.value = true; com.shelf.launcher.data.Features.carouselStage.value = true }
                            turnOn()
                        }
                    } + (1..5).map { n ->
                        R1Choice("$n each side", !edge && sides == n) {
                            com.shelf.launcher.data.Features.carouselEdge.value = false
                            com.shelf.launcher.data.Features.tvCarouselSide.value = n
                            turnOn()
                        }
                    } + listOf(80, 100, 120, 140, 160).map { gp ->
                        // Update 185: the gap between the games, right here (also Settings › Carousel › Spacing).
                        R1Choice("Gap $gp %", com.shelf.launcher.data.Features.carouselGap.value == gp) {
                            com.shelf.launcher.data.Features.carouselGap.value = gp
                            turnOn()
                        }
                    } + R1Choice("Spread a short list",
                        com.shelf.launcher.data.Features.carouselSpread.value) {
                        com.shelf.launcher.data.Features.carouselSpread.value = !com.shelf.launcher.data.Features.carouselSpread.value
                    } + R1Choice("To the edge", edge) {
                        // Update 119: games all the way to the screen's edges.
                        com.shelf.launcher.data.Features.carouselEdge.value = true
                        turnOn()
                    })
                }) {
                // Update 175: a tap turns the carousel on, then steps through its views (Tucked behind, Record flip, Wheel…
                // Box flow) and round again — it used to turn the carousel off. Off: hold the tile › Carousel off.
                if (!carouselOn) {
                    if (tv) com.shelf.launcher.data.Features.tvRoomGames.value = TvRoom.Games.CAROUSEL
                    else com.shelf.launcher.data.Features.carouselLayouts.toggle(a.homeLayout.name)
                } else {
                    val views = CarouselView.values()
                    val next = views[(com.shelf.launcher.data.Features.carouselView.value.ordinal + 1) % views.size]
                    com.shelf.launcher.data.Features.carouselView.value = next
                    // (As picking it from the list: the 3D-case views come with their glossy floor.)
                    if (next.cases) { com.shelf.launcher.data.Features.carouselReflect.value = true; com.shelf.launcher.data.Features.carouselStage.value = true }
                }
            })
            }
            // Theme: the next favourite (hold: the theme list).
            // Update 186: what's behind the home screen, in one tap: the game's picture with its video clip → the picture
            // only → the theme's animated background → a still background (the theme's own colour). Hold: every choice.
            run {
                val artLayer = a.homeLayout.styleHome && styleArtBehind(a.homeLayout)
                val clips = (a.backgroundStyle == com.shelf.launcher.data.BackgroundStyle.ART || a.backgroundStyle == com.shelf.launcher.data.BackgroundStyle.BLURRED) && a.videoPreviews
                val now = when {
                    artLayer -> if (clips) "Art +\nvideos" else "Art\nonly"
                    a.backgroundStyle == com.shelf.launcher.data.BackgroundStyle.ART && a.videoPreviews -> "Art +\nvideos"
                    a.backgroundStyle == com.shelf.launcher.data.BackgroundStyle.ART -> "Art\nonly"
                    a.backgroundStyle == com.shelf.launcher.data.BackgroundStyle.BLURRED -> "Blurred\nart"
                    a.backgroundStyle == com.shelf.launcher.data.BackgroundStyle.ANIMATED -> "Theme\nanimation"
                    a.backgroundStyle == com.shelf.launcher.data.BackgroundStyle.WALLPAPER -> "Your\nwallpaper"
                    else -> "Still\nbackground"
                }
                add(R1Tile("background", Icons.Filled.Wallpaper, now, a.backgroundStyle != com.shelf.launcher.data.BackgroundStyle.BLACK,
                    longAction = {
                        choiceSheet = "Background" to listOf(
                            R1Choice("Game art + video clips", a.backgroundStyle == com.shelf.launcher.data.BackgroundStyle.ART && a.videoPreviews) { home.setAppearance(a.copy(backgroundStyle = com.shelf.launcher.data.BackgroundStyle.ART, videoPreviews = true)) },
                            R1Choice("Game art only", a.backgroundStyle == com.shelf.launcher.data.BackgroundStyle.ART && !a.videoPreviews) { home.setAppearance(a.copy(backgroundStyle = com.shelf.launcher.data.BackgroundStyle.ART, videoPreviews = false)) },
                            R1Choice("Blurred game art", a.backgroundStyle == com.shelf.launcher.data.BackgroundStyle.BLURRED) { home.setAppearance(a.copy(backgroundStyle = com.shelf.launcher.data.BackgroundStyle.BLURRED)) },
                            R1Choice("Theme animation", a.backgroundStyle == com.shelf.launcher.data.BackgroundStyle.ANIMATED) { home.setAppearance(a.copy(backgroundStyle = com.shelf.launcher.data.BackgroundStyle.ANIMATED)) },
                            R1Choice("Your wallpaper", a.backgroundStyle == com.shelf.launcher.data.BackgroundStyle.WALLPAPER) { home.setAppearance(a.copy(backgroundStyle = com.shelf.launcher.data.BackgroundStyle.WALLPAPER)) },
                            R1Choice("Still background", a.backgroundStyle == com.shelf.launcher.data.BackgroundStyle.BLACK) { home.setAppearance(a.copy(backgroundStyle = com.shelf.launcher.data.BackgroundStyle.BLACK)) }
                        )
                    }) {
                    home.setAppearance(
                        when {
                            // (Update 329, found by the R1 tile walk and on the AYN Thor: a style home showing the game's art
                            // across the whole screen (Side menu, Spotlight, Icon bar, Console hub, Resume & discover… —
                            // their Layout options › Background) covers the theme animation and the still background, so
                            // two of the four taps changed nothing. There the tap switches the clips on and off.)
                            artLayer -> if (clips) a.copy(videoPreviews = false)
                                else a.copy(backgroundStyle = if (a.backgroundStyle == com.shelf.launcher.data.BackgroundStyle.BLURRED) a.backgroundStyle else com.shelf.launcher.data.BackgroundStyle.ART, videoPreviews = true)
                            a.backgroundStyle == com.shelf.launcher.data.BackgroundStyle.ART && a.videoPreviews -> a.copy(videoPreviews = false)
                            a.backgroundStyle == com.shelf.launcher.data.BackgroundStyle.ART -> a.copy(backgroundStyle = com.shelf.launcher.data.BackgroundStyle.ANIMATED)
                            a.backgroundStyle == com.shelf.launcher.data.BackgroundStyle.ANIMATED -> a.copy(backgroundStyle = com.shelf.launcher.data.BackgroundStyle.BLACK)
                            else -> a.copy(backgroundStyle = com.shelf.launcher.data.BackgroundStyle.ART, videoPreviews = true)
                        }
                    )
                })
            }
            // Update 196: how the game's picture fits the background in this layout — Fill the screen or the Whole picture
            // (Layout options › Background & game names, per layout).
            // (Update 202: hidden where the layout shows no background picture; greyed out — with why — while another
            // background is picked, the layout draws the art itself, or the Voltcap row's games aren't at the bottom.
            // All decided in r1TileFit, R1TileRules.kt.)
            run {
                com.shelf.launcher.data.Features.heroFitByLayout.state.value
                com.shelf.launcher.data.Features.carouselLayouts.state.value
                com.shelf.launcher.data.Features.spineLook.state.value
                val layout = a.homeLayout
                val fit = com.shelf.launcher.data.HeroFitPlan.of(layout, a.heroFit, a.cardArtType)
                val whole = fit == com.shelf.launcher.data.HeroFit.WHOLE
                add(R1Tile("herofit", Icons.Filled.AspectRatio, "Picture\n" + if (whole) "Whole" else "Fill", whole,
                    longAction = {
                        choiceSheet = "Background picture · ${layout.label}" to com.shelf.launcher.data.HeroFit.values().map { f ->
                            R1Choice(f.label, f == fit) { com.shelf.launcher.data.HeroFitPlan.put(layout, f) }
                        }
                    }) {
                    com.shelf.launcher.data.HeroFitPlan.put(layout, if (whole) com.shelf.launcher.data.HeroFit.FILL else com.shelf.launcher.data.HeroFit.WHOLE)
                })
            }
            add(R1Tile("theme", Icons.Filled.Palette, "Theme\n" + home.themeName, false,
                longAction = { home.openSettingsAt("Theme", "Themes") }) {
                home.themeAction?.invoke() ?: home.openSettingsAt("Theme", "Themes")
            })
            // Update 122: where the colours come from — the theme's own, the game you're on, or the music playing (its
            // album cover). Same as Theme › Colours follow, for the theme in use; hold: that setting.
            home.themeKey?.let { tk ->
                val theme = com.shelf.launcher.ui.theme.AppTheme.values().firstOrNull { it.name == tk }
                // (Update 202: only for themes that can be recoloured — themeRecolorable, r1TileFit.)
                if (theme != null) {
                    val F = com.shelf.launcher.data.Features
                    val look = F.lookFor(tk)
                    val now = when {
                        look.colorSource != com.shelf.launcher.data.Features.ColorSource.GAME -> 0
                        F.artColorSource.state.value == com.shelf.launcher.data.Features.ArtColorSource.ALBUM -> 2
                        else -> 1
                    }
                    add(R1Tile("colors", Icons.Filled.ColorLens, "Colours\n" + listOf("Theme", "Game", "Music")[now], now != 0,
                        longAction = { home.openSettingsAt("Theme", "Colours follow (theme, game or music)") }) {
                        val l = F.lookFor(tk)
                        when ((now + 1) % 3) {
                            0 -> if (l.colorSource != com.shelf.launcher.data.Features.ColorSource.CUSTOM) F.setLook(tk, l.copy(colorSource = com.shelf.launcher.data.Features.ColorSource.THEME))
                            1 -> { F.setLook(tk, l.copy(colorSource = com.shelf.launcher.data.Features.ColorSource.GAME)); F.artColorSource.value = com.shelf.launcher.data.Features.ArtColorSource.GAME }
                            else -> { F.setLook(tk, l.copy(colorSource = com.shelf.launcher.data.Features.ColorSource.GAME)); F.artColorSource.value = com.shelf.launcher.data.Features.ArtColorSource.ALBUM }
                        }
                    })
                }
            }
            add(R1Tile("rescan", Icons.Filled.Refresh, if (home.scanning) "Scanning…" else "Rescan\nlibrary", home.scanning) {
                if (!home.scanning) home.onRescan()
            })
            val scraping = home.scrapeTotal > 0
            add(R1Tile("scrape", Icons.Filled.Image, if (scraping) "Art\n${home.scrapeDone}/${home.scrapeTotal}" else "Missing\nart", scraping) {
                if (!scraping) home.onScrapeMissing()
            })
            // The game you're on: its power profile, changed right here.
            val pl = profileLabel
            if (home.gameKey != null && pl != null) add(R1Tile("profile", Icons.Filled.Speed, "Power\n$pl", !pl.startsWith("Device")) { showProfile = true })
        }
        // Battery: percent and time left; hold for the charge settings.
        run {
            val b = batteryNow
            val left = b.minutesLeft?.let { minutesLabel(it) + if (b.charging) " ⚡" else "" } ?: if (b.charging) "Charging" else ""
            add(R1Tile("battery", batteryIconFor(b.percent, b.charging), "${b.percent}%" + if (left.isNotEmpty()) "\n$left" else "", b.charging,
                longAction = { showBattery = true }) { showBattery = true })
        }
        // How much battery Voltcap itself uses, and what to change (Settings › Device › Battery watcher).
        add(R1Tile("batterywatch", Icons.Filled.BatteryAlert, "Battery\nwatcher", com.shelf.launcher.data.Features.batteryWatch.state.value) { showBatteryWatch = true })
        // Update 187: the battery saver — a tap steps through when it saves (battery low → always → off); hold: its settings.
        run {
            val mode = com.shelf.launcher.data.Features.saverMode.state.value
            val short = when (mode) {
                com.shelf.launcher.data.BatterySaver.Mode.AUTO -> if (com.shelf.launcher.data.BatterySaver.low) "Saving now" else "When low"
                com.shelf.launcher.data.BatterySaver.Mode.ALWAYS -> "Always"
                com.shelf.launcher.data.BatterySaver.Mode.OFF -> "Off"
            }
            add(R1Tile("saver", Icons.Filled.BatterySaver, "Battery saver\n$short", mode != com.shelf.launcher.data.BatterySaver.Mode.OFF,
                longAction = { showSaver = true }) {
                val modes = com.shelf.launcher.data.BatterySaver.Mode.values()
                com.shelf.launcher.data.Features.saverMode.value = modes[(mode.ordinal + 1) % modes.size]
            })
        }
        deviceTiles.forEach { (control, st) ->
            // 0.6.18: hold Controller style: the handheld's own settings (Input › Controller style), else Voltcap's Controls.
            val hold: (() -> Unit)? = if (control == com.shelf.launcher.device.DeviceControl.CONTROLLER_STYLE || control == com.shelf.launcher.device.DeviceControl.TRIGGER_MODE) {
                com.shelf.launcher.device.DeviceControls.handheldSettingsIntent(context)?.let { i -> { runCatching { context.startActivity(i) }; Unit } }
            } else null
            add(R1Tile("dev_" + control.name, deviceIcon(control), deviceLabel(control, st.value), st.on, longAction = hold) {
                scope.launch {
                    if (!st.supported) {
                        android.widget.Toast.makeText(context, "Not supported on this device", android.widget.Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val done = withContext(Dispatchers.IO) { runCatching { com.shelf.launcher.device.DeviceControls.next(context, control) }.getOrDefault(false) }
                    if (!done) {
                        val fallback = com.shelf.launcher.device.DeviceControls.fallbackIntent(control)
                        if (fallback != null) runCatching { context.startActivity(fallback) }
                        else android.widget.Toast.makeText(context, "Not supported on this device", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    deviceRefresh++
                }
            })
        }
        // Volume per app (handhelds with the root service; elsewhere it shows the media volume only).
        add(R1Tile("mixer", Icons.Filled.VolumeUp, "Volume\nmixer", com.shelf.launcher.device.audio.AppMixer.volumes(context).isNotEmpty()) {
            runCatching { context.startActivity(com.shelf.launcher.device.audio.AppMixer.openIntent(context)) }
        })
        add(R1Tile("settings", Icons.Filled.Settings, "Android settings", false) { QuickControls.openAndroidSettings(context) })
    }.let { list ->
        // Update 202: every tile asks r1TileFit (R1TileRules.kt) whether it does something here — in this layout, with
        // this look, on this device. Hidden where it doesn't (it keeps its place in your order for when it applies
        // again); greyed out — its press says what to change — where it's only "not right now". (Update 182's greying of
        // the look tiles in a layout with its own look is replaced by this: they're hidden there now.)
        list.mapNotNull { t ->
            when (val fit = r1TileFit(t.id, r1Ctx)) {
                is R1Fit.Grey -> R1Tile(t.id, t.icon, t.label, false, dot = t.dot, focus = t.focus, disabled = true) {
                    android.widget.Toast.makeText(context, fit.why, android.widget.Toast.LENGTH_LONG).show()
                }
                R1Fit.Hide -> null
                R1Fit.Show -> t
            }
        }
    }
    val byId = allTiles.associateBy { it.id }
    // Update 326: a test walk over adb reads which tiles the panel shows here (TestHooks; off without its flag file):
    // "~" after a greyed tile. Compared with the R1 matrix (R1Compat326Test) layout by layout, look by look.
    if (TestHooks.enabled(context)) {
        val dump = allTiles.joinToString(",") { if (it.disabled) it.id + "~" else it.id }
        androidx.compose.runtime.SideEffect { TestHooks.lastTiles = dump }
        LaunchedEffect(dump) {
            android.util.Log.e("VoltcapTest", "r1 layout=${home?.appearance?.homeLayout} look=${home?.appearance?.cardArtType} tiles=$dump")
        }
    }
    // (Update 325, Toni: "I can't find the R1 tile for the outline": a tile new since you last arranged them went to the
    // very end, behind the arrow. It now comes in right after the tile it's built beside — Outline after Badges.)
    val ordered = run {
        val out = order.mapNotNull { byId[it] }.toMutableList()
        allTiles.forEachIndexed { i, t ->
            if (t.id in order) return@forEachIndexed
            val before = allTiles.getOrNull(i - 1)?.id
            val at = out.indexOfFirst { it.id == before }
            if (at >= 0) out.add(at + 1, t) else out.add(t)
        }
        out
    }
    // An L1 shortcut for a tile: press it once now that the panel is up (the tiles may still be loading, so wait a moment).
    // A request the panel never showed (no access on this layout…) must not fire at the next R1 open.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { R1Shortcut.pending = null } }
    val wantedTile = R1Shortcut.pending
    LaunchedEffect(wantedTile, byId.size) {
        val id = R1Shortcut.pending ?: return@LaunchedEffect
        val tile = byId[id]
        if (tile != null) {
            R1Shortcut.pending = null
            countUse(id)
            tile.action()
        } else if (id != "lights" && id != "profile" && !id.startsWith("dev_") && r1TileFit(id, r1Ctx) == R1Fit.Hide) {
            // Update 202: a tile that does nothing in this layout / look / on this device (r1TileFit): said at once.
            R1Shortcut.pending = null
            android.widget.Toast.makeText(context, "That R1 tile does nothing here (this layout, game look or device)", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            // Tiles that fill in later (a lights probe, the game's profile) get a few seconds before it is "not available".
            kotlinx.coroutines.delay(3000)
            if (R1Shortcut.pending == id) {
                R1Shortcut.pending = null
                android.widget.Toast.makeText(context, "That R1 tile isn't available here right now", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    // Two pages (L1 / R1, or the tabs): Quick, and Device (the handheld's switches, power, battery, Android).
    val page = R1Pages.page
    fun onThisPage(id: String) = R1Pages.onDevicePage(id) == (page == 1)
    // Moves within this page; the other page's tiles keep their places.
    fun move(id: String, steps: Int) {
        val ids = ordered.map { it.id }
        val pageIds = ids.filter { onThisPage(it) }.toMutableList()
        val from = pageIds.indexOf(id)
        if (from < 0) return
        val to = (from + steps).coerceIn(0, pageIds.lastIndex)
        if (to == from) return
        pageIds.removeAt(from); pageIds.add(to, id)
        var k = 0
        val merged = ids.map { if (onThisPage(it)) pageIds[k++] else it }
        // Update 202: tiles not shown in this layout / on this device (r1TileFit) keep their saved places: the shown ones
        // take their new order in the slots they had, the others stay where they were in the saved order.
        val shownNow = merged.toSet()
        val full = order.distinct() + merged.filter { it !in order }
        var j = 0
        val kept = full.map { if (it in shownNow) merged[j++] else it }
        order = kept
        tilePrefs.edit().putString("order", kept.joinToString(",")).apply()
    }
    // In edit mode every tile shows (hidden ones faded); otherwise the first five, then the arrow.
    // "Most used first": the tiles you press most come first (your own order breaks ties; not while editing).
    val pageTiles = ordered.filter { onThisPage(it.id) }
    val shown = when {
        editing -> pageTiles
        mostUsed -> pageTiles.filter { it.id !in hidden }.sortedByDescending { uses[it.id] ?: 0 }
        else -> pageTiles.filter { it.id !in hidden }
    }
    val firstTileFocus = remember { FocusRequester() }
    LaunchedEffect(R1Pages.focusAfterFlip) {
        if (R1Pages.focusAfterFlip > 0) { kotlinx.coroutines.delay(60); runCatching { firstTileFocus.requestFocus() } }
    }
    var rowWidthPx by remember { mutableStateOf(0) }
    val arrowPx = with(androidx.compose.ui.platform.LocalDensity.current) { 20.dp.toPx() }
    // Always five per row; one step is a fifth of the row.
    val perRow = 5
    val pitchPx = ((rowWidthPx - arrowPx) / 5f).coerceAtLeast(1f)
    // Drag gestures outlive recompositions: they read these through State.
    val moveLatest = androidx.compose.runtime.rememberUpdatedState<(String, Int) -> Unit>({ id, n -> move(id, n) })
    val perRowLatest = androidx.compose.runtime.rememberUpdatedState(perRow)
    val pitchLatest = androidx.compose.runtime.rememberUpdatedState(pitchPx)
    val rowPitchPx = with(androidx.compose.ui.platform.LocalDensity.current) { (com.shelf.launcher.ui.theme.Ds.tileBox + 26.dp + com.shelf.launcher.ui.theme.Ds.s2).toPx() }

    // Swipe the card sideways to flip its pages (left: Device, right: Quick) - the sliders and the reorder drags take their own.
    Box(
        Modifier.pointerInput(editing) {
            if (editing) return@pointerInput
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragCancel = { total = 0f },
                onDragEnd = {
                    val way = -total
                    if (way > 60.dp.toPx() && R1Pages.page == 0) { R1Pages.page = 1; picked = null }
                    else if (way < -60.dp.toPx() && R1Pages.page == 1) { R1Pages.page = 0; picked = null }
                },
                onHorizontalDrag = { _, amount -> total += amount }
            )
        }
    ) {
    ShelfCard { VoltRail(brightnessNow / 100f) {
        // Brightness and media volume across the top: tap / drag the bar, or ◀ ▶ while it has the focus.
        QuickSlider(Icons.Filled.BrightnessMedium, brightnessNow, onSet = { v ->
            if (!canWriteBrightness) { QuickControls.stepBrightness(context, 0); return@QuickSlider }
            brightnessNow = v.coerceIn(2, 100)
            QuickControls.setBrightness(context, brightnessNow)
        })
        if (!canWriteBrightness) MutedNote("To change brightness, Android needs you to allow \"Modify system settings\" for Voltcap once — the bar takes you there.")
        Spacer(Modifier.height(4.dp))
        QuickSlider(if (volume == 0) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, volume, onSet = { v ->
            // (Update 119: the real step it landed on, so the bar never jumps back; ◀ ▶ one real step each.)
            volume = setMediaVolumePercent(context, v)
        }, onKeyStep = { d -> volume = stepMediaVolume(context, d) })
        Spacer(Modifier.height(8.dp))
        // Update 185: Undo for the last change made here (a few seconds).
        val undoLabel = R1Undo.label
        LaunchedEffect(R1Undo.at) {
            val at0 = R1Undo.at
            kotlinx.coroutines.delay(R1Undo.SHOWN_MS)
            if (R1Undo.at == at0) R1Undo.clear()
        }
        val undoShown = undoLabel != null && home != null
        // The two pages: L1 / R1 flip them (or tap a tab).
        // (Update 220: the Undo bar now sits over this row while it shows, in the tabs' own height, instead of being
        // added above the tiles — that pushed every tile down under your finger and back up when it went.)
        Box {
        Row(Modifier.graphicsLayer { alpha = if (undoShown) 0f else 1f }, verticalAlignment = Alignment.CenterVertically) {
            listOf("Quick", "Device").forEachIndexed { i, name ->
                val on = page == i
                Box(
                    Modifier
                        .clip(uiRounded(50))
                        .background(if (on) ShelfTheme.accent.copy(alpha = 0.22f) else Color.Transparent)
                        .shelfClickable(uiRounded(50), enabled = !undoShown) { if (!on) { R1Pages.page = i; picked = null } }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(name, color = if (on) ShelfTheme.accent else ShelfTheme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("L1 / R1", color = ShelfTheme.textMuted.copy(alpha = 0.7f), fontSize = 11.sp)
        }
        if (undoShown) {
            // (Update 186: swipe it away sideways — or ✕ — when you don't need it.)
            var swipe by remember(R1Undo.at) { mutableStateOf(0f) }
            val swipeOut = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
            Row(
                Modifier.matchParentSize()
                    .graphicsLayer { translationX = swipe; alpha = (1f - kotlin.math.abs(swipe) / (swipeOut * 2.5f)).coerceIn(0f, 1f) }
                    .pointerInput(R1Undo.at) {
                        detectHorizontalDragGestures(
                            onDragEnd = { if (kotlin.math.abs(swipe) > swipeOut) R1Undo.clear() else swipe = 0f },
                            onDragCancel = { swipe = 0f }
                        ) { ch, dx -> ch.consume(); swipe += dx }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Changed: $undoLabel", color = ShelfTheme.textMuted, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                R1UndoPill("↶ Undo", accent = true) { R1Undo.undo { liveHome.value?.setAppearance?.invoke(it) } }
                Spacer(Modifier.width(6.dp))
                R1UndoPill("✕", accent = false) { R1Undo.clear() }
            }
        }
        }
        Spacer(Modifier.height(6.dp))
        // Rows of five tiles; a small arrow beside the first row opens the rest (it takes no tile spot).
        @Composable
        fun Tile(t: R1Tile, cell: Modifier) = androidx.compose.runtime.key(t.id) {
            val isHidden = t.id in hidden
            val isPicked = editing && picked == t.id
            var drag by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
            val latestEditing = androidx.compose.runtime.rememberUpdatedState(editing)
            QuickTile(
                icon = t.icon, label = t.label, on = (t.on && !editing) || isPicked, dotColor = t.dot,
                dimmed = (editing && isHidden) || (!editing && t.disabled), editMark = if (editing) !isHidden else null,
                // Hold: the tile's own second action, else its settings page (Voltcap's or Android's).
                onLongClick = if (editing) null else t.longAction ?: r1HoldTarget(t.id)?.let { target ->
                    {
                        when (target) {
                            is R1Target.Voltcap -> home?.openSettingsAt(target.section, target.label)
                            is R1Target.Android -> openAndroidScreen(context, target.action)
                        }
                        Unit
                    }
                },
                modifier = cell
                    .then(
                        if (t.focus != null) Modifier.focusRequester(t.focus)
                        else if (t.id == shown.firstOrNull()?.id) Modifier.focusRequester(firstTileFocus) else Modifier
                    )
                    .graphicsLayer { translationX = drag.x; translationY = drag.y }
                    .zIndex(if (drag != androidx.compose.ui.geometry.Offset.Zero) 1f else 0f)
                    // Edit mode, D-pad: with a tile picked, ◀ ▶ ▲ ▼ move it instead of the focus.
                    .onPreviewKeyEvent { e ->
                        if (!isPicked || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionLeft -> { move(t.id, -1); true }
                            Key.DirectionRight -> { move(t.id, 1); true }
                            Key.DirectionUp -> { move(t.id, -perRow); true }
                            Key.DirectionDown -> { move(t.id, perRow); true }
                            else -> false
                        }
                    }
                    // Edit mode, touch: long-press and drag a tile to its new place.
                    .pointerInput(t.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { if (latestEditing.value) picked = t.id },
                            onDrag = { change, amount -> if (latestEditing.value) { change.consume(); drag += amount } },
                            onDragEnd = {
                                val steps = kotlin.math.round(drag.x / pitchLatest.value).toInt() +
                                    kotlin.math.round(drag.y / rowPitchPx).toInt() * perRowLatest.value
                                drag = androidx.compose.ui.geometry.Offset.Zero
                                if (steps != 0) moveLatest.value(t.id, steps)
                            },
                            onDragCancel = { drag = androidx.compose.ui.geometry.Offset.Zero }
                        )
                    }
            ) {
                if (editing) picked = if (picked == t.id) null else t.id else { countUse(t.id); undoable(t.label.substringBefore("\n")) { t.action() } }
            }
        }
        val firstFive = shown.take(5)
        val rest = shown.drop(5)
        val arrowW = 20.dp
        // Update 119: flipping between Quick and Device slides the tiles in from the side they come from (they used to
        // just appear).
        val pageSlide = remember { androidx.compose.animation.core.Animatable(0f) }
        val lastPage = remember { intArrayOf(page) }
        LaunchedEffect(page) {
            if (lastPage[0] == page) return@LaunchedEffect
            val dir = if (page > lastPage[0]) 1f else -1f
            lastPage[0] = page
            if (!com.shelf.launcher.data.Features.animPageSlide.value) return@LaunchedEffect
            pageSlide.snapTo(dir)
            pageSlide.animateTo(0f, androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
        Column(
            Modifier.fillMaxWidth().onSizeChanged { rowWidthPx = it.width }
                .graphicsLayer { val v = pageSlide.value; translationX = v * size.width * 0.35f; alpha = 1f - 0.85f * kotlin.math.abs(v) },
            verticalArrangement = Arrangement.spacedBy(com.shelf.launcher.ui.theme.Ds.s2)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                firstFive.forEach { Tile(it, Modifier.weight(1f)) }
                repeat(5 - firstFive.size) { Spacer(Modifier.weight(1f)) }
                Box(
                    Modifier
                        .width(arrowW)
                        .height(com.shelf.launcher.ui.theme.Ds.tileBox)
                        .clip(uiRounded(8.dp))
                        .shelfClickable(uiRounded(8.dp)) {
                            moreOpen = !moreOpen
                            tilePrefs.edit().putBoolean("more_open", moreOpen).apply()
                            if (!moreOpen) { editing = false; picked = null }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (moreOpen || editing) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        if (moreOpen || editing) "Fewer tiles" else "More tiles",
                        tint = ShelfTheme.colors.textPrimary, modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (moreOpen || editing) {
                // null = the Edit tile (never hideable), always last.
                (rest + listOf<R1Tile?>(null)).chunked(5).forEach { row ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        row.forEach { t ->
                            if (t != null) Tile(t, Modifier.weight(1f))
                            else QuickTile(icon = Icons.Filled.Edit, label = if (editing) "Done" else "Edit", on = editing, modifier = Modifier.weight(1f)) {
                                editing = !editing; picked = null
                            }
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                        Spacer(Modifier.width(arrowW))
                    }
                }
            }
        }
        if (editing) {
            Spacer(Modifier.height(6.dp))
            val p = picked?.let { byId[it] }
            if (p != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundStep(Icons.Filled.ChevronLeft, "Move left") { move(p.id, -1) }
                    Spacer(Modifier.width(6.dp))
                    RoundStep(Icons.Filled.ChevronRight, "Move right") { move(p.id, 1) }
                    Spacer(Modifier.width(6.dp))
                    RoundStep(if (p.id in hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        if (p.id in hidden) "Show" else "Hide") { toggleHidden(p.id) }
                    Spacer(Modifier.width(8.dp))
                    Text(p.label + if (p.id in hidden) " (hidden)" else "", color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
            }
            MutedNote("Pick a tile, then move it with ◀ ▶ (or the D-pad) and hide / show it. With a finger: hold a tile and drag it. The first five sit in front of the arrow. Each page (Quick / Device) keeps its own order.")
            Spacer(Modifier.height(6.dp))
            SwitchRow(
                Icons.Filled.Speed, "Most used first",
                "The tiles you press most move to the front (your order breaks ties). Off: your own order.",
                mostUsed, { mostUsed = it; tilePrefs.edit().putBoolean("most_used", it).apply() }
            )
        }
    } }
    }

    if (showOverlayLayout) {
        OverlayLayoutPopup(onDismiss = { showOverlayLayout = false })
    }
    if (showGameSize && home != null) GameSizePopup(home, onDismiss = { showGameSize = false })
    // Update 202: holding the Look tile.
    if (showLookChoices && home != null) GameLookPopup(home.appearance, home.setAppearance, onDismiss = { showLookChoices = false })
    choiceSheet?.let { (title, choices) ->
        R1ChoicePopup(title, choices.map { c -> R1Choice(c.label, c.on) { undoable(title.substringBefore(" ·")) { c.run() } } }, onDismiss = { choiceSheet = null })
    }
    if (showLayoutList && home != null) LayoutListPopup(home, onDismiss = { showLayoutList = false }, undoable = undoable)
    if (showBatteryWatch) BatteryWatchPopup(onDismiss = { showBatteryWatch = false })
    if (showSaver) BatterySaverPopup(onDismiss = { showSaver = false })
    if (showBattery) BatteryChargePopup(onDismiss = { showBattery = false; refresh++ })
    if (showProfile && home != null) GameProfilePopup(home, onDismiss = { showProfile = false })
    if (showLights) {
        LightsPopup(
            enabled = lightsEnabled,
            color = lightsColor,
            brightness = lightsBrightness,
            offWithScreensaver = lightsOffWithScreensaver,
            flashOnUnlock = lightsFlashOnUnlock,
            foundLights = foundLights,
            onLightsChange = onLightsChange,
            onAdvancedChange = onLightsAdvancedChange,
            onDismiss = {
                showLights = false
                // Back to the panel with the Lights tile focused, if it can take it.
            }
        )
    }
    // Once the popup is gone, hand focus back to the tile that opened it.
    var wasShowing by remember { mutableStateOf(false) }
    LaunchedEffect(showLights) {
        if (wasShowing && !showLights) {
            kotlinx.coroutines.delay(60)
            runCatching { lightsTileFocus.requestFocus() }
        }
        wasShowing = showLights
    }
}

/** One R1 quick tile (id is what the order / hidden prefs store). */
private class R1Tile(
    val id: String, val icon: ImageVector, val label: String, val on: Boolean,
    val dot: Color? = null, val focus: FocusRequester? = null,
    /** Hold (long press): a second action, e.g. the overlay layout. */
    val longAction: (() -> Unit)? = null,
    /** Greyed out (update 177). Update 202: only "not right now, and you can change that" (R1Fit.Grey) — its action says
     *  what to change; a tile that does nothing here is hidden instead (r1TileFit, R1TileRules.kt). */
    val disabled: Boolean = false,
    val action: () -> Unit
)

/** A compact icon button with a short caption; lit with the accent when [on]. */
@Composable
private fun QuickTile(
    icon: ImageVector,
    label: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    /** Small colour dot in the corner (the lights tile shows the current colour). */
    dotColor: Color? = null,
    /** Edit mode: hidden tiles are faded. */
    dimmed: Boolean = false,
    /** Edit mode: ✓ (shown) or + (hidden) in the corner; null outside edit mode. */
    editMark: Boolean? = null,
    /** Hold: a second action (null = none). */
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    // One look for every tile (Ds): the same box, radius and icon size; off = a quiet neutral plate
    // with the icon in the accent, on = filled with the accent. The caption always takes two lines'
    // height, so tiles with one-line and two-line captions sit on the same line.
    val shape = uiRounded(com.shelf.launcher.ui.theme.Ds.rMedium)
    // Volt: dark glass tiles; a lit tile keeps the glass, gets an accent outline and an accent glow inside.
    val volt = VoltUi.on
    val accent = ShelfTheme.accent
    Column(
        modifier.alpha(if (dimmed) 0.4f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(com.shelf.launcher.ui.theme.Ds.tileBox)
                .then(
                    if (volt) Modifier
                        .voltSurface(shape, base = ShelfTheme.colors.textPrimary.copy(alpha = com.shelf.launcher.ui.theme.Ds.quietFill).compositeOver(ShelfTheme.colors.surfaceRaised))
                        .background(if (on) androidx.compose.ui.graphics.Brush.radialGradient(listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.06f))) else androidx.compose.ui.graphics.SolidColor(Color.Transparent))
                        .border(if (on) 1.5.dp else 0.dp, if (on) accent else Color.Transparent, shape)
                    else Modifier
                        .clip(shape)
                        .background(
                            if (on) ShelfTheme.accent
                            else ShelfTheme.colors.textPrimary.copy(alpha = com.shelf.launcher.ui.theme.Ds.quietFill).compositeOver(ShelfTheme.colors.surfaceRaised)
                        )
                        .border(1.dp, if (on) Color.Transparent else ShelfTheme.colors.textPrimary.copy(alpha = 0.06f), shape)
                )
                .then(if (onLongClick != null) Modifier.shelfClickableLong(shape, onLongClick) { onClick() } else Modifier.shelfClickable(shape) { onClick() }),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, label,
                tint = when {
                    volt && on -> accent
                    volt -> ShelfTheme.colors.textPrimary.copy(alpha = 0.78f)
                    on -> onAccentColor()
                    else -> ShelfTheme.accent
                },
                modifier = Modifier.size(com.shelf.launcher.ui.theme.Ds.icon)
            )
            if (dotColor != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                        .border(1.dp, Color.Black.copy(alpha = 0.5f), CircleShape)
                )
            }
            if (editMark != null) {
                Text(
                    if (editMark) "✓" else "+",
                    color = if (on && !volt) onAccentColor() else ShelfTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(com.shelf.launcher.ui.theme.Ds.s1))
        // Two caption lines at the system's font size too (a bigger font ran the second line into the row below).
        val captionH = maxOf(22.dp, with(androidx.compose.ui.platform.LocalDensity.current) { (10.5.sp * 2).toDp() } + 1.dp)
        Box(Modifier.fillMaxWidth().height(captionH), contentAlignment = Alignment.TopCenter) {
            Text(
                label,
                color = ShelfTheme.colors.textPrimary,
                fontSize = 9.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 9.sp),
                lineHeight = androidx.compose.ui.unit.TextUnit(1.167f, androidx.compose.ui.unit.TextUnitType.Em),
                fontWeight = FontWeight.SemiBold,
                // (Update 274: a one-word label on one line, made smaller to fit — "Achievements" broke as "Achievement / s".)
                // (Update 285, Toni: "a lot of stuff still written with …": a two-line caption ("Edit\nhome", "Look\nCovers",
                // "Achievements\nFocus") has no space, so it was cut to one line — "Edit…", "Look…". A line break now gets its
                // two lines, each kept on one line and made smaller to fit.)
                maxLines = if (label.trim().contains(' ') || label.trim().contains('\n')) 2 else 1,
                softWrap = label.trim().contains(' ') && !label.trim().contains('\n'),
                // (Compose lays a text that doesn't wrap and ends in "…" out as one line, whatever its line breaks —
                // so the unwrapped captions are clipped instead; the font shrinks until each line fits.)
                overflow = if (label.trim().contains(' ') && !label.trim().contains('\n')) TextOverflow.Ellipsis else TextOverflow.Clip,
                textAlign = TextAlign.Center,
                style = uiFontStyle().merge(androidx.compose.ui.text.TextStyle(
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                )),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Short tile captions: the name, and the current setting on a second line. */
private fun deviceLabel(c: com.shelf.launcher.device.DeviceControl, value: String?): String {
    val name = when (c) {
        com.shelf.launcher.device.DeviceControl.CHARGE_LIMIT -> "Charge 80%"
        com.shelf.launcher.device.DeviceControl.BYPASS_CHARGING -> "Bypass\ncharging"
        com.shelf.launcher.device.DeviceControl.AIRPLANE -> "Airplane"
        com.shelf.launcher.device.DeviceControl.PERFORMANCE -> "Mode"
        com.shelf.launcher.device.DeviceControl.FAN -> "Fan"
        com.shelf.launcher.device.DeviceControl.REFRESH_RATE -> "Screen"
        com.shelf.launcher.device.DeviceControl.CONTROLLER_STYLE -> "Controller"
        com.shelf.launcher.device.DeviceControl.TRIGGER_MODE -> "L2 / R2"
        com.shelf.launcher.device.DeviceControl.FLOATING_BUTTON -> "Floating\nbutton"
        // The tuner app's own name (ClusterTune / PULSE) on the first line.
        com.shelf.launcher.device.DeviceControl.TUNER_APP -> return (value ?: "CPU tuner") + "\nOpen"
        com.shelf.launcher.device.DeviceControl.CPU_CAP -> "Power"
        com.shelf.launcher.device.DeviceControl.AUTO_TDP -> "AutoTDP"
        com.shelf.launcher.device.DeviceControl.UNDERCLOCK -> "Underclock"
    }
    // Controller layout names follow the legend's wording (Xbox / Retro).
    val v = value?.replace("Nintendo", "Retro")
    // Never the same word twice ("Perform. Performance"): the value alone when it already says it.
    if (v != null && v.contains(name.replace("\n", " "), ignoreCase = true)) return v
    return if (v != null && !name.contains('\n')) "$name\n$v" else name
}

private fun deviceIcon(c: com.shelf.launcher.device.DeviceControl): ImageVector = when (c) {
    com.shelf.launcher.device.DeviceControl.CHARGE_LIMIT -> Icons.Filled.BatteryChargingFull
    com.shelf.launcher.device.DeviceControl.BYPASS_CHARGING -> Icons.Filled.Power
    com.shelf.launcher.device.DeviceControl.AIRPLANE -> Icons.Filled.AirplanemodeActive
    com.shelf.launcher.device.DeviceControl.PERFORMANCE -> Icons.Filled.Speed
    com.shelf.launcher.device.DeviceControl.FAN -> Icons.Filled.Air
    com.shelf.launcher.device.DeviceControl.REFRESH_RATE -> Icons.Filled.Refresh
    com.shelf.launcher.device.DeviceControl.CONTROLLER_STYLE -> Icons.Filled.Gamepad
    com.shelf.launcher.device.DeviceControl.TRIGGER_MODE -> Icons.Filled.VideogameAsset
    com.shelf.launcher.device.DeviceControl.FLOATING_BUTTON -> Icons.Filled.TouchApp
    com.shelf.launcher.device.DeviceControl.TUNER_APP -> Icons.Filled.Tune
    com.shelf.launcher.device.DeviceControl.CPU_CAP -> Icons.Filled.Memory
    com.shelf.launcher.device.DeviceControl.AUTO_TDP -> Icons.Filled.Speed
    com.shelf.launcher.device.DeviceControl.UNDERCLOCK -> Icons.Filled.DeveloperBoard
}

@Composable
internal fun RoundStep(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(ShelfTheme.colors.textPrimary.copy(alpha = com.shelf.launcher.ui.theme.Ds.quietFill).compositeOver(ShelfTheme.colors.surfaceRaised))
            .shelfClickable(CircleShape) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = ShelfTheme.accent, modifier = Modifier.size(18.dp))
    }
}

/**
 * A rainbow bar for picking any hue. Tap or drag with touch; with the D-pad,
 * focus it and press left / right (hold to sweep). The marker shows the
 * current hue.
 */
@Composable
internal fun HueSlider(color: Int, onColor: (Int) -> Unit) {
    val hsv = remember(color) { FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) } }
    val hue = hsv[0]
    // Greys and white have no hue of their own — the bar still starts from red.
    val isGrey = hsv[1] < 0.05f
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    val shape = uiRounded(12.dp)
    // The touch handlers below outlive recompositions: always call the latest callback (an old one
    // would carry the popup's old settings back with it).
    val latestOnColor by androidx.compose.runtime.rememberUpdatedState(onColor)
    fun pick(h: Float) {
        val wrapped = ((h % 360f) + 360f) % 360f
        latestOnColor(android.graphics.Color.HSVToColor(floatArrayOf(wrapped, 1f, 1f)))
    }
    val rainbow = remember { (0..12).map { Color.hsv(it * 30f % 360f, 1f, 1f) } }
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(shape)
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(rainbow))
            .focusRing(focused, shape)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val step = when (event.key) {
                    Key.DirectionLeft -> -6f
                    Key.DirectionRight -> 6f
                    else -> return@onKeyEvent false
                }
                pick((if (isGrey) 0f else hue) + step)
                sounds?.play(UiSound.MOVE)
                true
            }
            .focusable(interactionSource = source)
            .pointerInput(Unit) {
                detectTapGestures { offset -> pick(offset.x / size.width * 360f) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    pick((change.position.x / size.width).coerceIn(0f, 0.999f) * 360f)
                }
            }
    ) {
        if (!isGrey) {
            val x = maxWidth * (hue / 360f)
            Box(
                Modifier
                    .padding(start = (x - 3.dp).coerceAtLeast(0.dp))
                    .width(6.dp)
                    .fillMaxHeight()
                    .clip(uiRounded(3.dp))
                    .background(Color.White)
                    .border(1.dp, Color.Black.copy(alpha = 0.5f), uiRounded(3.dp))
            )
        }
    }
}

/**
 * Starting music without leaving Shelf: a "play" media key goes to the app
 * that played last (usually Spotify), which resumes in the background. If that
 * doesn't reach it, Spotify is asked directly.
 */
object MusicApps {
    const val SPOTIFY = "com.spotify.music"

    fun playPause(context: Context, playing: Boolean) {
        val code = if (playing) android.view.KeyEvent.KEYCODE_MEDIA_PAUSE else android.view.KeyEvent.KEYCODE_MEDIA_PLAY
        // Starting music: silence Voltcap's own loop first (it would also make "is anything playing?" below say yes).
        if (!playing) BackgroundMusicGate.otherMusicStarting()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        runCatching {
            audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
            audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
        }
        if (!playing && !audio.isMusicActive) {
            // Nothing picked it up — wake Spotify itself.
            listOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP).forEach { action ->
                runCatching {
                    context.sendBroadcast(
                        Intent(Intent.ACTION_MEDIA_BUTTON)
                            .setPackage(SPOTIFY)
                            .putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(action, code))
                    )
                }
            }
        }
    }

    /** Opens [packageName] (the app that's playing), or Spotify. */
    fun open(context: Context, packageName: String?) {
        val pm = context.packageManager
        val intent = (packageName?.let { pm.getLaunchIntentForPackage(it) } ?: pm.getLaunchIntentForPackage(SPOTIFY))
            ?: return
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** The system calls behind the quick-settings tiles. */
object QuickControls {

    private fun start(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    // ---- Wi-Fi ----

    fun isWifiOn(context: Context): Boolean = runCatching {
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    fun toggleWifi(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android's own Wi-Fi pop-up — the only way apps may switch it now.
            if (!start(context, Intent(Settings.Panel.ACTION_WIFI))) start(context, Intent(Settings.ACTION_WIFI_SETTINGS))
        } else {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            runCatching { wifi.isWifiEnabled = !wifi.isWifiEnabled }
        }
    }

    // ---- Bluetooth ----

    fun isBluetoothOn(context: Context): Boolean = runCatching {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled == true
    }.getOrDefault(false)

    @Suppress("DEPRECATION", "MissingPermission")
    fun toggleBluetooth(context: Context) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            val done = runCatching {
                if (adapter.isEnabled) adapter.disable() else adapter.enable()
            }.getOrDefault(false)
            if (done) return
        }
        start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    // ---- Do Not Disturb ----

    fun isDndOn(context: Context): Boolean = runCatching {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }.getOrDefault(false)

    fun toggleDnd(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            start(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        runCatching {
            nm.setInterruptionFilter(
                if (isDndOn(context)) NotificationManager.INTERRUPTION_FILTER_ALL
                else NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
        }
    }

    // ---- Brightness ----

    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun brightnessPercent(context: Context): Int = runCatching {
        val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        Math.round(raw / 255f * 100f).coerceIn(0, 100)
    }.getOrDefault(50)

    fun stepBrightness(context: Context, deltaPercent: Int) {
        if (!canWriteSettings(context)) {
            start(
                context,
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))
            )
            return
        }
        val next = (brightnessPercent(context) + deltaPercent).coerceIn(2, 100)
        runCatching {
            val resolver = context.contentResolver
            // Auto-brightness would undo the change right away.
            Settings.System.putInt(
                resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, Math.round(next / 100f * 255f))
        }
    }

    /** Sets the brightness to [percent] (manual mode, so auto-brightness doesn't undo it). */
    fun setBrightness(context: Context, percent: Int) {
        if (!canWriteSettings(context)) { stepBrightness(context, 0); return }
        runCatching {
            val resolver = context.contentResolver
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, Math.round(percent.coerceIn(2, 100) / 100f * 255f))
        }
    }

    // ---- Android settings ----

    fun openAndroidSettings(context: Context) {
        start(context, Intent(Settings.ACTION_SETTINGS))
    }
}


/**
 * Hold the Overlay tile: which overlays show over games and where each one sits. Picking a corner
 * another overlay already has swaps the two. One on its own keeps the corner picked for it.
 */
/** Hold on the Layout tile: every home layout to pick from. */
@Composable
private fun LayoutListPopup(home: R1Home, onDismiss: () -> Unit, undoable: (String, () -> Unit) -> Unit = { _, run -> run() }) {
    val a = home.appearance
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        ImmersiveDialogWindow()
        ShelfCard {
            SectionLabel("Home layout")
            MutedNote("Pick the layout; each keeps its own options (hold the tile for its settings page).")
            Spacer(Modifier.height(10.dp))
            // (Update 337: a picture of the layout the cursor is on.)
            var shown by remember { mutableStateOf(a.homeLayout) }
            LayoutPreview(shown, Modifier.widthIn(max = 300.dp))
            Spacer(Modifier.height(10.dp))
            // Fifteen layouts: scrolls on a short screen, and the D-pad starts on the one in use.
            val current = remember { FocusRequester() }
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { current.requestFocus() } }
            androidx.compose.foundation.layout.Column(
                // (Update 182: as tall as the screen leaves room for — Done was cut off at the bottom on the Thor.)
                Modifier.heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp - 200).coerceIn(120, 300).dp)
                    .verticalScroll(rememberScrollState())
            ) {
                ChipFlow {
                    com.shelf.launcher.data.HomeLayout.pickable.forEach { l ->
                        Box(if (a.homeLayout == l) Modifier.focusRequester(current) else Modifier) {
                            Chip((if (a.homeLayout == l) "✓ " else "") + l.label, accent = a.homeLayout == l,
                                modifier = Modifier.onFocusChanged { if (it.isFocused) shown = l }) { undoable("Layout") { home.setAppearance(a.copy(homeLayout = l)) }; onDismiss() }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Chip("Done", onClick = onDismiss)
        }
    }
}

/** Hold on the Size tile: Small / Medium / Large and a % slider, for the layout in use (update 104 / 105). */
@Composable
private fun GameSizePopup(home: R1Home, onDismiss: () -> Unit) {
    val a = home.appearance
    val pct = GameSize.pct(a)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        ImmersiveDialogWindow()
        ShelfCard {
            SectionLabel("Game size · " + a.homeLayout.label)
            MutedNote(
                if (GameSize.paged(a.homeLayout)) "Small / Medium / Large: how many games fit on a page. The % makes them smaller on it."
                else "How big the games of this layout are drawn. Each layout keeps its own size."
            )
            Spacer(Modifier.height(10.dp))
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
            ChipFlow {
                listOf("Small" to GameSize.STEPS[0], "Medium" to GameSize.STEPS[1], "Large" to GameSize.STEPS[2]).forEachIndexed { i, (label, v) ->
                    Box(if (i == 0) Modifier.focusRequester(first) else Modifier) {
                        Chip(label, accent = kotlin.math.abs(pct - v) <= 2) { GameSize.set(a, v) { home.setAppearance(it) } }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            GameSizeSlider(a) { home.setAppearance(it) }
            Spacer(Modifier.height(10.dp))
            Chip("Done", onClick = onDismiss)
        }
    }
}

/** One choice of a tile's hold list. */
private class R1Choice(val label: String, val on: Boolean, val run: () -> Unit)

/** A tile's hold list (update 104): its choices as chips; picking one does it and closes the list. */
@Composable
private fun R1ChoicePopup(title: String, choices: List<R1Choice>, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        ImmersiveDialogWindow()
        // Update 182: the list scrolls (on a short screen its last choices were cut off below the edge).
        val maxH = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.86f).dp
        Box(Modifier.heightIn(max = maxH)) { ShelfCard {
            Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionLabel(title)
            Spacer(Modifier.height(8.dp))
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
            val start = choices.indexOfFirst { it.on }.coerceAtLeast(0)
            ChipFlow {
                choices.forEachIndexed { i, c ->
                    Box(if (i == start) Modifier.focusRequester(first) else Modifier) {
                        Chip((if (c.on) "✓ " else "") + c.label, accent = c.on) { c.run(); onDismiss() }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Chip("Close", onClick = onDismiss)
            }
        } }
    }
}

@Composable
private fun OverlayLayoutPopup(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cfg by com.shelf.launcher.data.perf.PerfSettings.state.collectAsState()
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        ImmersiveDialogWindow()
        ShelfCard {
            SectionLabel("Overlays over games")
            MutedNote("Turn each on or off and pick its corner; picking a taken corner swaps the two. In a game you can also drag an overlay to another corner. Changes here apply when you go back to the game.")
            val eff = cfg.effectiveCorners()
            val rows = listOf(
                Triple("Stats", cfg.showDuringGames, eff.first),
                Triple("Music", cfg.musicDuringGames, eff.second),
                Triple("Trophies", cfg.raDuringGames, eff.third)
            )
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
            rows.forEachIndexed { i, (name, on, corner) ->
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(if (i == 0) Modifier.focusRequester(first) else Modifier) {
                        Chip((if (on) "✓ " else "") + name, accent = on) {
                            com.shelf.launcher.data.perf.PerfSettings.update(context) {
                                when (i) {
                                    0 -> it.copy(showDuringGames = !on)
                                    1 -> it.copy(musicDuringGames = !on)
                                    else -> it.copy(raDuringGames = !on)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    com.shelf.launcher.data.perf.PerfCorner.values().forEach { c ->
                        val short = when (c) {
                            com.shelf.launcher.data.perf.PerfCorner.TOP_LEFT -> "↖"
                            com.shelf.launcher.data.perf.PerfCorner.TOP_RIGHT -> "↗"
                            com.shelf.launcher.data.perf.PerfCorner.BOTTOM_LEFT -> "↙"
                            com.shelf.launcher.data.perf.PerfCorner.BOTTOM_RIGHT -> "↘"
                        }
                        Chip(short, accent = on && corner == c, enabled = on) {
                            com.shelf.launcher.data.perf.PerfSettings.update(context) { x ->
                                val (p, m, r) = x.effectiveCorners()
                                val mine = listOf(p, m, r)[i]
                                var n = x.copy(corner = p, musicCorner = m, raCorner = r)
                                // Whoever has that corner takes this one's old place.
                                if (i != 0 && p == c) n = n.copy(corner = mine)
                                if (i != 1 && m == c) n = n.copy(musicCorner = mine)
                                if (i != 2 && r == c) n = n.copy(raCorner = mine)
                                when (i) { 0 -> n.copy(corner = c); 1 -> n.copy(musicCorner = c); else -> n.copy(raCorner = c) }
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            PercentSlider("Size", cfg.overlayScale, { v -> com.shelf.launcher.data.perf.PerfSettings.update(context) { it.copy(overlayScale = v) } }, min = 50, max = 200)
            PercentSlider("Opacity", cfg.overlayOpacity, { v -> com.shelf.launcher.data.perf.PerfSettings.update(context) { it.copy(overlayOpacity = v) } }, min = 20, max = 100)
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                Chip(if (cfg.overlaysMovable) "✓ Drag to move in games" else "Drag to move in games", accent = cfg.overlaysMovable) {
                    com.shelf.launcher.data.perf.PerfSettings.update(context) { it.copy(overlaysMovable = !it.overlaysMovable) }
                }
                val arrows = com.shelf.launcher.data.Features.overlayArrows.value
                Chip(if (arrows) "✓ Fold arrows" else "Fold arrows", accent = arrows) {
                    com.shelf.launcher.data.Features.overlayArrows.toggle()
                    PerfOverlayService.reconfigure(context)
                }
                Chip("Done", accent = false) { onDismiss() }
            }
        }
    }
}

/**
 * Volt look: the quick settings hang off a vertical voltage rail on the left, with the lightning capsule
 * riding it at the screen's brightness [level] (0..1, top = bright). Classic: just the content.
 */
@Composable
private fun VoltRail(level: Float, content: @Composable () -> Unit) {
    if (!VoltUi.on) { Column { content() }; return }
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp)) { content() }
        Box(Modifier.matchParentSize()) {
            VoltageLine(Modifier.align(Alignment.CenterStart).padding(start = 6.dp).fillMaxHeight(), vertical = true)
            VoltNode(Modifier.align(androidx.compose.ui.BiasAlignment(-1f, (1f - 2f * level.coerceIn(0f, 1f)))))
        }
    }
}

/** Update 182: layouts that have a carousel look (the same ones Settings › Home › Layout offers it for). */
internal fun carouselApplies(layout: com.shelf.launcher.data.HomeLayout): Boolean =
    layout == com.shelf.launcher.data.HomeLayout.TV || !layout.styleHome ||
        layout == com.shelf.launcher.data.HomeLayout.SIDE || layout == com.shelf.launcher.data.HomeLayout.STACKS

// (Update 182's LOOK_SUB_TILES — the look's own tiles, greyed out in a layout with its own look — stood here. Update 202:
// gone; r1TileFit (R1TileRules.kt) hides each where it does nothing, and shows the disc / case / spine ones in the
// layouts that always draw discs, cases or spines.)

/** Update 220: the R1 panel's Undo / ✕ — in the height of the Quick / Device tabs they sit over (Chip is taller). */
@Composable
private fun R1UndoPill(label: String, accent: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(uiRounded(50))
            .background(if (accent) ShelfTheme.accent else Color.White.copy(alpha = 0.08f))
            .shelfClickable(uiRounded(50)) { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(label, color = if (accent) onAccentColor() else ShelfTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}


/**
 * Update 245 (R1 Popup Style tile): the game windows A can open, read from and written to the settings Settings › Home ›
 * Layout options › Game window already uses — nothing of its own. [OWN] and [BANNER] share the "Its style (every layout)"
 * switch (Features.winBanner); [CLASSIC] is this layout's "A opens: the classic details window" (classicWindowLayouts).
 */
internal enum class PopupStyle(val label: String, val short: String) {
    OWN("The layout's own window", "Own"),
    BANNER("Banner page", "Banner"),
    // (Update 249, #119: Toni's two earlier designs, as styles of their own — Features.winMotion, RowRevealWindow.kt.)
    ROW_OPENS("The row opens up", "Opens"),
    ROW_REVEAL("Row reveal", "Reveal"),
    SHARED("Shared move", "Shared"),
    CLASSIC("Classic details window", "Classic");

    companion object {
        fun current(layout: String): PopupStyle {
            val F = com.shelf.launcher.data.Features
            return when {
                layout in F.classicWindowLayouts.value -> CLASSIC
                F.winMotion.value == com.shelf.launcher.data.Features.WinMotion.ROW_OPENS -> ROW_OPENS
                F.winMotion.value == com.shelf.launcher.data.Features.WinMotion.ROW_REVEAL -> ROW_REVEAL
                F.winMotion.value == com.shelf.launcher.data.Features.WinMotion.SHARED -> SHARED
                F.winBanner.value -> BANNER
                else -> OWN
            }
        }

        fun set(layout: String, style: PopupStyle) {
            val F = com.shelf.launcher.data.Features
            val classicNow = layout in F.classicWindowLayouts.value
            when (style) {
                CLASSIC -> if (!classicNow) F.classicWindowLayouts.toggle(layout)
                OWN, BANNER, ROW_OPENS, ROW_REVEAL, SHARED -> {
                    if (classicNow) F.classicWindowLayouts.toggle(layout)
                    // (The banner page stays as it was under a motion style: it's what shows where the motion can't.)
                    if (style == OWN || style == BANNER) F.winBanner.value = style == BANNER
                    F.winMotion.value = when (style) {
                        ROW_OPENS -> com.shelf.launcher.data.Features.WinMotion.ROW_OPENS
                        ROW_REVEAL -> com.shelf.launcher.data.Features.WinMotion.ROW_REVEAL
                        SHARED -> com.shelf.launcher.data.Features.WinMotion.SHARED
                        else -> com.shelf.launcher.data.Features.WinMotion.NONE
                    }
                }
            }
        }
    }
}
