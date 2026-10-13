package com.shelf.launcher.ui

import android.app.NotificationManager
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Gamepad
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
    val foundLights by produceState(initialValue = emptyList<String>(), refresh) {
        value = withContext(Dispatchers.IO) {
            runCatching { com.shelf.launcher.data.ControllerLights.describe(context) }.getOrDefault(emptyList())
        }
    }
    val hasLights = foundLights.isNotEmpty()
    var showLights by remember { mutableStateOf(false) }
    var showOverlayLayout by remember { mutableStateOf(false) }
    var showGameSize by remember { mutableStateOf(false) }
    // A tile's hold list (update 104: the Player and Round shortcuts tiles): title and the choices.
    var choiceSheet by remember { mutableStateOf<Pair<String, List<R1Choice>>?>(null) }
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
        val n = (uses[id] ?: 0) + 1
        uses = uses + (id to n)
        tilePrefs.edit().putInt("use_$id", n).apply()
    }

    // Every tile, in the default order; the saved order is applied on top (new tiles go at the end).
    val allTiles = buildList {
        add(R1Tile("wifi", if (wifiOn) Icons.Filled.Wifi else Icons.Filled.WifiOff, "Wi-Fi", wifiOn) { QuickControls.toggleWifi(context); refresh++ })
        add(R1Tile("bt", if (bluetoothOn) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled, "Bluetooth", bluetoothOn) { QuickControls.toggleBluetooth(context); refresh++ })
        add(R1Tile("dnd", Icons.Filled.DoNotDisturbOn, "Do not disturb", dndOn) { QuickControls.toggleDnd(context); refresh++ })
        if (hasLights) add(R1Tile("lights", Icons.Filled.Lightbulb,
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
        // What the games are drawn as in the layout in use: Covers → Real cases → real sizes → Cartridges & discs → Discs only.
        if (gameLook != null && onCycleGameLook != null) {
            // Update 177: greyed out in a layout that draws its games its own way (the look would change nothing there).
            val layoutNow = home?.appearance?.homeLayout
            val own = layoutNow?.let { layoutOwnLook(it) }
            if (own != null) add(R1Tile("gamelook", Icons.Filled.Album, "Look\n$gameLook", false, disabled = true) {
                android.widget.Toast.makeText(context, "${layoutNow.label} draws its games as $own — the look is for the other layouts.", android.widget.Toast.LENGTH_SHORT).show()
            })
            else add(R1Tile("gamelook", Icons.Filled.Album, "Look\n$gameLook", gameLook != "Covers") { onCycleGameLook() })
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
            // Update 128: only while the Cartridges look is on — tap: the next of your pack's 14 cartridge shapes;
            // hold: Original / Custom, Opaque / Clear and every shape (also Settings › Game look › Cartridges).
            // (Update 131: only with some of your cartridge pack downloaded — Settings › Game look › Extra art packs.)
            if (home.appearance.cardArtType == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC && F.cartsOnly.state.value &&
                com.shelf.launcher.data.Packs.anyCarts()) {
                val look = F.cartLook.state.value
                add(R1Tile("cartlook", Icons.Filled.Memory, "Cartridge\n" + look.label, F.cartSource.state.value == CartSource.CUSTOM,
                    longAction = {
                        choiceSheet = "Cartridges" to (
                            CartSource.values().map { s -> R1Choice(s.label, F.cartSource.value == s) { F.cartSource.value = s } } +
                                listOf(
                                    R1Choice("Opaque", !F.cartClear.value) { F.cartClear.value = false },
                                    R1Choice("Clear", F.cartClear.value) { F.cartClear.value = true }
                                ) +
                                CartLook.values().map { l -> R1Choice(l.label, F.cartLook.value == l) { F.cartLook.value = l } }
                            )
                    }) {
                    val all = CartLook.values()
                    F.cartLook.value = all[(all.indexOf(F.cartLook.value) + 1) % all.size]
                })
            }
            // Update 129: one console's disc / case / spine for every game, only while that look is on — tap: the next
            // console (or each game's own), hold: the list. (Like the Cartridge tile for the Cartridges look.)
            val lookNow = home.appearance.cardArtType
            fun styleTile(id: String, word: String, pref: com.shelf.launcher.data.Features.StringPref, list: List<String>, after: () -> Unit = {}) {
                val now = pref.state.value
                val name = if (now.isEmpty()) "Own" else com.shelf.launcher.data.ConsoleNames.display(now)
                add(R1Tile(id, Icons.Filled.Album, "$word\n$name", now.isNotEmpty(),
                    longAction = {
                        choiceSheet = "$word for every game" to (
                            listOf(R1Choice("Each game's own", pref.value.isEmpty()) { pref.value = "" }) +
                                list.map { p -> R1Choice(com.shelf.launcher.data.ConsoleNames.display(p), pref.value == p) { pref.value = p; after() } }
                            )
                    }) {
                    pref.value = LookStyles.next(pref.value, list)
                    if (pref.value.isNotEmpty()) after()
                })
            }
            // (A picked disc is drawn, so "Drawn discs" goes on with it.)
            if (lookNow == com.shelf.launcher.data.CardArtType.DISC) styleTile("discstyle", "Disc", F.discStyle, LookStyles.DISCS) { F.mediaShells.value = true }
            if (lookNow == com.shelf.launcher.data.CardArtType.CASE || lookNow == com.shelf.launcher.data.CardArtType.CASE_REAL) styleTile("casestyle", "Case", F.caseStyle, LookStyles.cases())
            if (lookNow == com.shelf.launcher.data.CardArtType.COVER && F.spineLook.state.value) styleTile("spinestyle", "Spine", F.spineStyle, LookStyles.spines())
            // Update 159: 3D on / off for the looks with real things (cases, cartridges, discs): your 3D shape library's
            // pieces and cases instead of the flat drawings — one tap, without leaving the look you're on.
            val caseLookNow = lookNow == com.shelf.launcher.data.CardArtType.CASE || lookNow == com.shelf.launcher.data.CardArtType.CASE_REAL
            if (caseLookNow || lookNow == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC) {
                // Case looks: their own switch (the cases in 3D); Cartridges & discs / Cartridges: the 3D models.
                val on3d = if (caseLookNow) F.caseModels3d.state.value else F.modelFinish.state.value != com.shelf.launcher.ui.ModelFinish.OFF
                add(R1Tile("look3d", Icons.Filled.ViewInAr, if (on3d) "3D\non" else "3D\noff", on3d) {
                    // Update 162: 3D for the cartridges you see — each console's real cartridge, or your custom ones only
                    // when the Cartridges look is on your pack (clear when you picked clear).
                    // (Update 164: and back to the same kind flat — 3D custom → Custom cartridges, 3D clear → Clear, 3D → as before.)
                    if (caseLookNow) F.caseModels3d.value = !on3d
                    else if (on3d) {
                        val was = F.modelFinish.value
                        setModelsLook(false)
                        if (was == com.shelf.launcher.ui.ModelFinish.CUSTOM || was == com.shelf.launcher.ui.ModelFinish.CLEAR) {
                            F.cartsOnly.value = true
                            F.cartSource.value = CartSource.CUSTOM
                            F.cartClear.value = was == com.shelf.launcher.ui.ModelFinish.CLEAR
                        }
                    } else {
                        val custom = F.cartsOnly.value && F.cartSource.value == CartSource.CUSTOM
                        F.modelFinish.value = if (!custom) com.shelf.launcher.ui.ModelFinish.REAL
                            else if (F.cartClear.value) com.shelf.launcher.ui.ModelFinish.CLEAR else com.shelf.launcher.ui.ModelFinish.CUSTOM
                    }
                    // No 3D models downloaded yet: where to get them.
                    if (!on3d && !com.shelf.launcher.data.Packs.anyModels()) home.openSettingsAt("Game look", "3D models")
                })
            }
        }
        if (home != null) {
            val a = home.appearance
            // Home layout: Row → Tiles → Hero → Grid → TV room (each keeps its own look).
            // Tap = the next layout, hold = the list of every layout (there are fifteen now).
            add(R1Tile("layout", Icons.Filled.Dashboard, "Layout\n" + a.homeLayout.label.removeSuffix(" home"), false, longAction = { showLayoutList = true }) {
                val all = com.shelf.launcher.data.HomeLayout.pickable
                home.setAppearance(a.copy(homeLayout = all[(all.indexOf(a.homeLayout) + 1) % all.size]))
            })
            // Update 179: the two-screen layouts — the gap between their tiles (tap: the next, hold: the list).
            if (a.homeLayout == com.shelf.launcher.data.HomeLayout.TWIN_ICONS || a.homeLayout == com.shelf.launcher.data.HomeLayout.TWIN_DESK) {
                val pref = if (a.homeLayout == com.shelf.launcher.data.HomeLayout.TWIN_ICONS) com.shelf.launcher.data.Features.twinGap else com.shelf.launcher.data.Features.twinDeskGap
                val gaps = TWIN_GAPS.filter { it <= (if (pref === com.shelf.launcher.data.Features.twinGap) 32 else 24) }
                val now = pref.state.value
                add(R1Tile("twingap", Icons.Filled.ViewModule, "Gaps\n" + gapLabel(now), false,
                    longAction = { choiceSheet = "Gap between tiles" to gaps.map { g -> R1Choice(gapLabel(g), now == g) { pref.value = g } } }) {
                    pref.value = gaps[(gaps.indexOf(now).coerceAtLeast(0) + 1) % gaps.size]
                })
            }
            // Options of the layout in use (they only show while that layout is the one on screen).
            run {
                val f = com.shelf.launcher.data.Features
                when (a.homeLayout) {
                    com.shelf.launcher.data.HomeLayout.SHELF -> add(R1Tile("shelflook", Icons.Filled.ViewModule, "Shelves\n" + f.shelfLook.state.value.label, false,
                        longAction = { f.shelfBackdrop.value = !f.shelfBackdrop.value }) {
                        val all = com.shelf.launcher.data.Features.ShelfLook.values()
                        f.shelfLook.value = all[(f.shelfLook.value.ordinal + 1) % all.size]
                    })
                    com.shelf.launcher.data.HomeLayout.SPOT -> add(R1Tile("spotlight", Icons.Filled.ViewModule, "Tiles\n" + f.spotTilesAt.state.value.label, false,
                        // Hold (update 104): where the names go — the tile's name and the big title.
                        longAction = {
                            val F = com.shelf.launcher.data.Features
                            choiceSheet = "Spotlight · names" to (
                                com.shelf.launcher.data.Features.SpotName.values().map { n -> R1Choice("Tile name: " + n.label, F.spotNameAt.value == n) { F.spotNameAt.value = n } } +
                                    com.shelf.launcher.data.Features.SpotAcross.values().map { n -> R1Choice("Title: " + n.label, F.spotTitleAt.value == n) { F.spotTitleAt.value = n } } +
                                    com.shelf.launcher.data.Features.SpotAlong.values().map { n -> R1Choice("Title: " + n.label, F.spotTitleRow.value == n) { F.spotTitleRow.value = n } }
                                )
                        }) {
                        f.spotTilesAt.value = if (f.spotTilesAt.value == com.shelf.launcher.data.Features.SpotSide.TOP) com.shelf.launcher.data.Features.SpotSide.BOTTOM else com.shelf.launcher.data.Features.SpotSide.TOP
                    })
                    com.shelf.launcher.data.HomeLayout.BAR -> add(R1Tile("bartabs", Icons.Filled.Label, if (f.barTabsCurrent.state.value) "Tab\ncategory" else "Tabs\nshortcuts", f.barTabsCurrent.state.value,
                        longAction = { f.barArtCut.value = !f.barArtCut.value }) {
                        f.barTabsCurrent.value = !f.barTabsCurrent.value
                    })
                    // Folders → logos on tiles → logos cut out (update 104).
                    com.shelf.launcher.data.HomeLayout.CROSS -> add(R1Tile("crosscut", Icons.Filled.Label,
                        when { f.crossFolders.state.value -> "Consoles\nfolders"; f.crossLogoCut.state.value -> "Logos\ncut out"; else -> "Logos\non tiles" }, !f.crossFolders.state.value) {
                        when {
                            f.crossFolders.value -> { f.crossFolders.value = false; f.crossLogoCut.value = false }
                            !f.crossLogoCut.value -> f.crossLogoCut.value = true
                            else -> { f.crossFolders.value = true; f.crossLogoCut.value = false }
                        }
                    })
                    com.shelf.launcher.data.HomeLayout.CLASSIC -> add(R1Tile("headertight", Icons.Filled.Label, if (f.homeHeaderTight.state.value) "Header\ntight" else "Header\nroomy", f.homeHeaderTight.state.value) {
                        f.homeHeaderTight.value = !f.homeHeaderTight.value
                    })
                    else -> Unit
                }
                // Real scraped discs / cartridges (off) or Voltcap's own drawn ones (on).
                // (Update 129: only with the looks it changes — Discs only, Cartridges & discs — not beside every look.)
                if (a.cardArtType == com.shelf.launcher.data.CardArtType.DISC ||
                    (a.cardArtType == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC && !f.cartsOnly.state.value)
                ) add(R1Tile("drawnmedia", Icons.Filled.Album, if (f.mediaShells.state.value) "Drawn\ndiscs" else "Real\ndiscs", f.mediaShells.state.value) {
                    f.mediaShells.value = !f.mediaShells.value
                })
                // The second screen as a magnifier of the main one (tap: the Magnifier page on / off, hold: start the capture).
                if (dualScreen && f.secondMagnifier.state.value) {
                    val on = a.secondPage == com.shelf.launcher.data.SecondPage.MAGNIFIER
                    add(R1Tile("magnifier", Icons.Filled.Cast, if (on) "Magnifier\non" else "Magnifier", on,
                        longAction = { ScreenMirror.requestStart() }) {
                        home.setAppearance(a.copy(secondPage = if (on) com.shelf.launcher.data.SecondPage.ART else com.shelf.launcher.data.SecondPage.MAGNIFIER))
                    })
                }
                add(R1Tile("whoplays", Icons.Filled.Person, if (f.whoPlays.state.value) "Who plays\non" else "Who plays\noff", f.whoPlays.state.value) {
                    f.whoPlays.value = !f.whoPlays.value
                })
            }
            // Where the music player sits: tap = next corner, hold = move it anywhere (Edit Home).
            run {
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
                    when {
                        off -> home.setAppearance(a.copy(musicPlayerPosition = all.first(), miniPlayer = true))
                        moved -> { HomeWidgets.reset(HomeWidgets.MUSIC); home.setAppearance(a.copy(miniPlayer = true)) }
                        a.musicPlayerPosition.ordinal == all.lastIndex -> home.setAppearance(a.copy(miniPlayer = false))
                        else -> home.setAppearance(a.copy(musicPlayerPosition = all[a.musicPlayerPosition.ordinal + 1], miniPlayer = true))
                    }
                })
            }
            // Endless: the row goes round from the last game to the first (off: it bumps against the end).
            // (Update 182: greyed out on Hero tiles, which has no Endless.)
            if (!endlessApplies(a.homeLayout)) add(R1Tile("endless", Icons.Filled.Repeat, "Endless\noff", false, disabled = true) {
                android.widget.Toast.makeText(context, "${a.homeLayout.label} stops at the first and last game — no Endless there.", android.widget.Toast.LENGTH_SHORT).show()
            }) else add(R1Tile("endless", Icons.Filled.Repeat, if (a.loopRow) "Endless\non" else "Endless\noff", a.loopRow) {
                home.setAppearance(a.copy(loopRow = !a.loopRow))
            })
            // The round shortcut buttons: show / hide. Hold (update 104): where they sit, folded or open, move, off.
            add(R1Tile("roundshort", Icons.Filled.Apps, if (a.tileShortcuts) "Round\nshortcuts" else "Shortcuts\noff", a.tileShortcuts,
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
                home.setAppearance(a.copy(tileShortcuts = !a.tileShortcuts))
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
            add(R1Tile("gamesize", Icons.Filled.PhotoSizeSelectLarge, "Size\n" + GameSize.label(a), false, longAction = { showGameSize = true }) {
                GameSize.step(a) { home.setAppearance(it) }
            })
            // Update 136, Art drop: how far the cover drops below the case template's top band (Box art looks) or the
            // 3D model window's top (3D models) — each look its own. Update 137: the panel closes and the slider shows
            // on the home screen, under the game (above it with the row at the bottom), changing it live.
            val dropModel = a.cardArtType == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC && Models3D.on()
            val dropCase = a.cardArtType == com.shelf.launcher.data.CardArtType.CASE || a.cardArtType == com.shelf.launcher.data.CardArtType.CASE_REAL
            if (dropModel || dropCase) {
                // (Update 137: Art position — moved with drag / D-pad and sized with pinch / L1 R1.)
                val moved = ArtPlace.moved(dropModel)
                add(R1Tile("artdrop", Icons.Filled.VerticalAlignBottom, "Art position\n" + if (moved) "Moved" else "Centred", moved) {
                    HomeAdjust.open = HomeAdjust.Target(HomeAdjust.Kind.ART_DROP, model = dropModel)
                })
            }
            // Update 137, Cartridge colour (3D models, Custom or Clear): the custom cartridges' colour, for the game
            // you're on or all of them; shown on the home screen like Art drop.
            val finish = com.shelf.launcher.data.Features.modelFinish.state.value
            if (dropModel && (finish == ModelFinish.CUSTOM || finish == ModelFinish.CLEAR)) {
                ModelColors.version.intValue
                val gp = home.gamePlatform; val gn = home.gameName
                val mine = (if (gp != null && gn != null) ModelColors.game(gp, gn) else null) ?: ModelColors.all()
                val which = when {
                    gp != null && gn != null && ModelColors.game(gp, gn) != null -> "This game"
                    ModelColors.all() != null -> "All"
                    else -> "Console"
                }
                add(R1Tile("cartcolour", Icons.Filled.FormatColorFill, "Cart colour\n$which", mine != null, dot = mine?.let { Color(it) }) {
                    HomeAdjust.open = HomeAdjust.Target(HomeAdjust.Kind.CART_COLOUR, model = true, platform = gp, name = gn)
                })
            }
            // Corners of the covers and tiles (update 104): Square → Rounded → Extra round → Capsule.
            add(R1Tile("corners", Icons.Filled.RoundedCorner, "Corners\n" + a.cardCorners.label, a.cardCorners != com.shelf.launcher.data.CardCorners.SQUARE) {
                val all = com.shelf.launcher.data.CardCorners.values()
                home.setAppearance(a.copy(cardCorners = all[(a.cardCorners.ordinal + 1) % all.size]))
            })
            // Where the game row sits (Voltcap row and Tile home): Top → Middle → Bottom.
            if (a.homeLayout == com.shelf.launcher.data.HomeLayout.CLASSIC || a.homeLayout == com.shelf.launcher.data.HomeLayout.TILES) {
                add(R1Tile("rowpos", Icons.Filled.SwapVert, "Row\n" + a.dockPosition.label, false) {
                    val all = com.shelf.launcher.data.DockPosition.values()
                    home.setAppearance(a.copy(dockPosition = all[(a.dockPosition.ordinal + 1) % all.size]))
                })
            }
            // RomM (update 93): browse your RomM server and download games (only once it's set up).
            if (remember { com.shelf.launcher.data.RommCredentialsStore(context).load() != null }) {
                add(R1Tile("romm", Icons.Filled.CloudDownload, "RomM", false) { RommUi.showing = true })
            }
            // Carousel look of the layout in use (TV room: Games beside the TV › Carousel).
            val tv = a.homeLayout == com.shelf.launcher.data.HomeLayout.TV
            val carouselOn = if (tv) com.shelf.launcher.data.Features.tvRoomGames.state.value == TvRoom.Games.CAROUSEL
            else a.homeLayout.name in com.shelf.launcher.data.Features.carouselLayouts.state.value
            // Hold (update 104): the carousel's view (Tucked, Record flip, Wheel, Deck, Flat row).
            // Update 113: the hold list also has how many games show each side of the middle one (1–5).
            val sides = com.shelf.launcher.data.Features.tvCarouselSide.state.value
            val edge = com.shelf.launcher.data.Features.carouselEdge.state.value
            // Update 182: greyed out in a layout without a carousel (as the Look tile), like Settings, which hides it there.
            if (!carouselApplies(a.homeLayout)) add(R1Tile("carousel", Icons.Filled.ViewCarousel, "Carousel", false, disabled = true) {
                android.widget.Toast.makeText(context, "${a.homeLayout.label} has no carousel — it's for the other layouts.", android.widget.Toast.LENGTH_SHORT).show()
            }) else add(R1Tile("carousel", Icons.Filled.ViewCarousel, if (carouselOn) "Carousel\n" + com.shelf.launcher.data.Features.carouselView.state.value.label + " · " + (if (edge) "edge" else "$sides") else "Carousel", carouselOn,
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
            // Theme: the next favourite (hold: the theme list).
            add(R1Tile("theme", Icons.Filled.Palette, "Theme\n" + home.themeName, false,
                longAction = { home.openSettingsAt("Theme", "Themes") }) {
                home.themeAction?.invoke() ?: home.openSettingsAt("Theme", "Themes")
            })
            // Update 122: where the colours come from — the theme's own, the game you're on, or the music playing (its
            // album cover). Same as Theme › Colours follow, for the theme in use; hold: that setting.
            home.themeKey?.let { tk ->
                val theme = com.shelf.launcher.ui.theme.AppTheme.values().firstOrNull { it.name == tk }
                if (theme != null && theme.recolorable) {
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
        deviceTiles.forEach { (control, st) ->
            add(R1Tile("dev_" + control.name, deviceIcon(control), deviceLabel(control, st.value), st.on) {
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
    }
    val byId = allTiles.associateBy { it.id }
    val ordered = order.mapNotNull { byId[it] } + allTiles.filter { it.id !in order }
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
        order = merged
        tilePrefs.edit().putString("order", merged.joinToString(",")).apply()
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
        // The two pages: L1 / R1 flip them (or tap a tab).
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf("Quick", "Device").forEachIndexed { i, name ->
                val on = page == i
                Box(
                    Modifier
                        .clip(uiRounded(50))
                        .background(if (on) ShelfTheme.accent.copy(alpha = 0.22f) else Color.Transparent)
                        .shelfClickable(uiRounded(50)) { if (!on) { R1Pages.page = i; picked = null } }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(name, color = if (on) ShelfTheme.accent else ShelfTheme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("L1 / R1", color = ShelfTheme.textMuted.copy(alpha = 0.7f), fontSize = 11.sp)
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
                if (editing) picked = if (picked == t.id) null else t.id else { countUse(t.id); t.action() }
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
    choiceSheet?.let { (title, choices) -> R1ChoicePopup(title, choices, onDismiss = { choiceSheet = null }) }
    if (showLayoutList && home != null) LayoutListPopup(home, onDismiss = { showLayoutList = false })
    if (showBatteryWatch) BatteryWatchPopup(onDismiss = { showBatteryWatch = false })
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
    /** Update 177: greyed out — it does nothing in the layout in use (its action only says why). */
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
                fontSize = 9.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(9.sp), maxFontSize = 9.sp),
                lineHeight = androidx.compose.ui.unit.TextUnit(1.167f, androidx.compose.ui.unit.TextUnitType.Em),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
        com.shelf.launcher.device.DeviceControl.CONTROLLER_STYLE -> "Buttons"
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
private fun LayoutListPopup(home: R1Home, onDismiss: () -> Unit) {
    val a = home.appearance
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        ImmersiveDialogWindow()
        ShelfCard {
            SectionLabel("Home layout")
            MutedNote("Pick the layout; each keeps its own options (hold the tile for its settings page).")
            Spacer(Modifier.height(10.dp))
            // Fifteen layouts: scrolls on a short screen, and the D-pad starts on the one in use.
            val current = remember { FocusRequester() }
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { current.requestFocus() } }
            androidx.compose.foundation.layout.Column(
                Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())
            ) {
                ChipFlow {
                    com.shelf.launcher.data.HomeLayout.pickable.forEach { l ->
                        Box(if (a.homeLayout == l) Modifier.focusRequester(current) else Modifier) {
                            Chip((if (a.homeLayout == l) "✓ " else "") + l.label, accent = a.homeLayout == l) { home.setAppearance(a.copy(homeLayout = l)); onDismiss() }
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
