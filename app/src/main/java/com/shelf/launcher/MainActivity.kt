package com.shelf.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shelf.launcher.ui.LauncherScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.shelf.launcher.ui.theme.ShelfLauncherTheme

class MainActivity : ComponentActivity() {
    /** Android 13+: permission for the achievement unlock pop-ups. */
    private val askNotifications = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* either way is fine: pop-ups just stay off without it */ }

    /** Asked once, and only when it'd be used: RetroAchievements connected and pop-ups on. */
    private fun maybeAskForNotifications() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        // Only the runtime popup is asked here; notifications switched off in Settings show in Permissions.
        if (!com.shelf.launcher.data.Permissions.needsPopup(this, com.shelf.launcher.data.VoltPermission.NOTIFICATIONS)) return
        val prefs = getSharedPreferences("session", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        val appearance = com.shelf.launcher.data.AppearanceStore(this).load()
        // Unlock pop-ups (with RetroAchievements), or the weekly recap once
        // there's something to recap — asked once, never at the very first start.
        val wantsPopups = appearance.unlockPopups && com.shelf.launcher.data.RaCredentialsStore(this).load() != null
        if (wantsPopups) { askForNotificationsOnce(prefs); return }
        if (!appearance.weeklyRecap) return
        // Whether there's play to recap means reading the whole play-time log: off the main thread, so it
        // never holds up the start (update 91).
        val app = applicationContext
        Thread {
            val hasPlay = runCatching { com.shelf.launcher.data.PlaytimeStore(app).loadSessions().isNotEmpty() }.getOrDefault(false)
            if (hasPlay) runOnUiThread { if (!isFinishing && !isDestroyed) askForNotificationsOnce(prefs) }
        }.start()
    }

    private fun askForNotificationsOnce(prefs: android.content.SharedPreferences) {
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // First, before anything that could fail: record crashes and break
        // crash loops (see CrashGuard).
        CrashGuard.install(this)
        // Credential stores (Keystore-backed, slow right after a reboot) open in the background now.
        com.shelf.launcher.data.StartupPrewarm.start(this)
        // Update 188: the saved list of games read now, alongside everything else (RomLibraryPrefetch).
        runCatching { com.shelf.launcher.data.RomLibraryPrefetch.start(this) }
        val crashReport = CrashGuard.takeReport(this)
        if (crashReport == null) CrashGuard.beginLauncher(this)
        // Update 292: a freeze that left no crash record becomes a report; then the freeze watchdog starts.
        if (crashReport == null) com.shelf.launcher.data.crash.CrashReports.adoptOrphanFreeze(this)
        com.shelf.launcher.data.crash.FreezeWatchdog.start(this)

        super.onCreate(savedInstanceState)
        // The newer feature switches (achievements, Living Library, density…): small prefs, read once.
        com.shelf.launcher.data.Features.init(this)
        // The one image loader (permanent art store, no server re-checks), installed before any image loads.
        // It used to be replaced here by a plain one on every activity start, see ArtStorage.install.
        runCatching {
            com.shelf.launcher.data.ArtStorage.install(
                applicationContext, com.shelf.launcher.data.ArtStorageStore(applicationContext).load().maxBytes
            )
        }
        // Settings › Device › Gesture hint (hide navigation bar / force-hide): read before the window shows.
        com.shelf.launcher.data.GestureHint.init(this)
        // Update 131: which of the downloaded art packs are on this device.
        com.shelf.launcher.data.Packs.init(this)
        // (Update 300: the ROM headers kept from before — which cartridge a Game Boy / DS game came on.)
        com.shelf.launcher.data.RomHeaders.init(this)
        // (Update 301: where you set the cuts of your full case scans.)
        com.shelf.launcher.data.FullScanCuts.init(this)
        // (Update 302: copies of your pictures nothing uses any more, removed in the background.)
        applicationContext.let { app -> Thread { com.shelf.launcher.data.UserArt.cleanup(app) }.apply { priority = Thread.MIN_PRIORITY }.start() }
        // Debug builds only: logs (never crashes) any disk or network access on the main thread, the
        // usual cause of stutters. Filter Logcat by "StrictMode" to find them.
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build()
            )
        }
        // Update 208: debug builds only — frame times and the picture caches' hits, logged as "VoltcapPerf" (PerfDiag.kt).
        com.shelf.launcher.ui.PerfDiag.attach(this)
        LaunchInfo.update(intent)
        // (Update 334: "Who's playing?" asked from a game — the in-game Quick menu.)
        if (intent?.getBooleanExtra(com.shelf.launcher.ui.ProfilePicker.EXTRA, false) == true) com.shelf.launcher.ui.ProfilePicker.request = true
        com.shelf.launcher.ui.TestHooks.fromIntent(this, intent)
        com.shelf.launcher.data.QrTransfer.fromIntent(intent)
        // Sleep mode for Wi-Fi / Bluetooth (Settings › Device): listens for screen off only when on.
        runCatching { com.shelf.launcher.data.SleepRadios.init(this) }
        // Discord status ("Playing …"): only loaded when switched on.
        // Not in safe mode: the native Discord bridge is a prime suspect for startup crashes.
        if (!CrashGuard.safeMode && com.shelf.launcher.data.AppearanceStore(this).load().discordPresence) {
            com.shelf.launcher.data.DiscordPresence.attach(this)
        }
        // Back on the home screen never closes Voltcap: it just goes to the
        // background (as a home app) or behind the other apps. Closing it made
        // the next start a slow cold start, with Android's own home screen
        // showing in between.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!com.shelf.launcher.data.HomeLauncher.isDefault(this@MainActivity)) moveTaskToBack(true)
            }
        })
        // Phone web remote (Settings › Controls): its buttons arrive here as key presses.
        com.shelf.launcher.data.WebRemote.onKey = { k ->
            val code = when (k) {
                "UP" -> android.view.KeyEvent.KEYCODE_DPAD_UP; "DOWN" -> android.view.KeyEvent.KEYCODE_DPAD_DOWN
                "LEFT" -> android.view.KeyEvent.KEYCODE_DPAD_LEFT; "RIGHT" -> android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                "A" -> android.view.KeyEvent.KEYCODE_BUTTON_A; "B" -> android.view.KeyEvent.KEYCODE_BUTTON_B
                "X" -> android.view.KeyEvent.KEYCODE_BUTTON_X; "Y" -> android.view.KeyEvent.KEYCODE_BUTTON_Y
                "START" -> android.view.KeyEvent.KEYCODE_BUTTON_START; "SELECT" -> android.view.KeyEvent.KEYCODE_BUTTON_SELECT
                "L1" -> android.view.KeyEvent.KEYCODE_BUTTON_L1; else -> android.view.KeyEvent.KEYCODE_BUTTON_R1
            }
            runOnUiThread {
                val now = android.os.SystemClock.uptimeMillis()
                dispatchKeyEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, code, 0))
                dispatchKeyEvent(android.view.KeyEvent(now, now + 40, android.view.KeyEvent.ACTION_UP, code, 0))
            }
        }
        if (com.shelf.launcher.data.WebRemote.enabled(this)) com.shelf.launcher.data.WebRemote.start(this)
        com.shelf.launcher.data.SystemButtonLayout.init(this)
        enableEdgeToEdge()
        hideSystemBars()
        // The keyboard closing can leave the navigation bar up on some devices: hide it again.
        com.shelf.launcher.ui.SystemBars.watchKeyboard(window)
        maybeAskForNotifications()

        // Watches real frame times so Automatic quality can step down on devices that can't keep up.
        com.shelf.launcher.device.FrameWatch.start(this)
        setContent {
            // "Hide navigation bar" switched: the window follows at once.
            val immersive = com.shelf.launcher.data.GestureHint.immersiveState.value
            androidx.compose.runtime.LaunchedEffect(immersive) {
                hideSystemBars()
                // Android ignores a hide that comes while the bar is still sliding in (Off → On): ask again once it settled.
                for (wait in longArrayOf(120, 350, 800)) { kotlinx.coroutines.delay(wait); hideSystemBars() }
            }
            // Update 130: a safety net. Some screens and handhelds brought the navigation bar back and it stayed (a hide
            // asked while it was still animating, a window handing focus back…). While Voltcap has the focus and no
            // keyboard is open, a bar that is really showing is hidden again (a swipe's short peek isn't "showing").
            androidx.compose.runtime.LaunchedEffect(immersive) {
                if (!immersive) return@LaunchedEffect
                // A device that can't hide it at all would be asked every time: after a few tries in a row, only now and then.
                var tries = 0
                while (true) {
                    kotlinx.coroutines.delay(if (tries < 4) 1500L else 15_000L)
                    if (com.shelf.launcher.ui.SystemBars.navStuck(window, this@MainActivity.hasWindowFocus())) {
                        tries++
                        runCatching { com.shelf.launcher.ui.SystemBars.apply(window) }
                    } else tries = 0
                }
            }
            var report by remember { mutableStateOf(crashReport) }
            val pending = report
            val exportReport = com.shelf.launcher.ui.rememberCrashExport()
            if (pending != null) {
                // Shown before the launcher itself, with plain styling, so it
                // works even when the launcher or a setting is what crashed.
                CrashReportScreen(pending, onContinue = {
                    CrashGuard.beginLauncher(this@MainActivity)
                    report = null
                })
            } else {
                Box(Modifier.fillMaxSize()) {
                    Launcher()
                    if (CrashGuard.safeMode) {
                        SafeModeBanner(
                            onExport = {
                                Thread {
                                    val id = crashReport?.id ?: com.shelf.launcher.data.crash.CrashReports.latestId(applicationContext)
                                    if (id != null) exportReport(id)
                                    else runOnUiThread { android.widget.Toast.makeText(this@MainActivity, "No crash report saved.", android.widget.Toast.LENGTH_SHORT).show() }
                                }.start()
                            },
                            onStartNormally = { CrashGuard.restartNormally(this@MainActivity) },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Launcher() {
        // The ViewModel is hoisted above the theme wrapper so the selected
        // theme can drive the whole tree, including dialogs.
        val vm: LauncherViewModel = viewModel()
        // Only the three things the theme needs are followed, each changing rarely,
        // so a D-pad step (which changes the focused index) doesn't recompose this
        // level — except when the focused cover itself changes.
        val appTheme by remember(vm) { vm.uiState.map { it.appTheme }.distinctUntilChanged() }
            .collectAsState(vm.uiState.value.appTheme)
        val appearance by remember(vm) { vm.uiState.map { it.appearance }.distinctUntilChanged() }
            .collectAsState(vm.uiState.value.appearance)
        // For the accent-from-art option: the focused game's cover.
        val focusedCover by remember(vm) {
            vm.uiState.map { s ->
                val shown = s.forDisplay()
                shown.visibleGames.getOrNull(shown.focusedIndex)?.let {
                    shown.artOverrides[it.stableKey]?.cover ?: shown.art[it.stableKey]?.gridUrl
                }
            }.distinctUntilChanged()
        }.collectAsState(null)

        ShelfLauncherTheme(
            appTheme = appTheme,
            appearance = appearance,
            dynamicArtUrl = focusedCover
        ) {
            LauncherScreen(vm)
        }
    }

    // Every key press also tells which face-button layout the system uses (Xbox / Nintendo), so the
    // button legend can follow the handheld's own setting (SystemButtonLayout).
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) com.shelf.launcher.ui.KeyInput.lastWasKey = true // (Update 233, #120)
        com.shelf.launcher.ui.HomeIdle.poke()
        com.shelf.launcher.data.GameMode.userIsHere() // (Update 182)
        com.shelf.launcher.data.BatterySaver.poke() // (Update 187)
        com.shelf.launcher.ui.SecondDim.poke() // (Update 188: using Voltcap keeps its second screen lit)
        // Update 152: moving through games: animated covers hold still for a moment (AnimatedArt.onMove).
        if (event.action == android.view.KeyEvent.ACTION_DOWN && when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                android.view.KeyEvent.KEYCODE_BUTTON_L1, android.view.KeyEvent.KEYCODE_BUTTON_R1,
                android.view.KeyEvent.KEYCODE_BUTTON_L2, android.view.KeyEvent.KEYCODE_BUTTON_R2 -> true
                else -> false
            }) com.shelf.launcher.data.AnimatedArt.onMove()
        runCatching { com.shelf.launcher.data.SystemButtonLayout.observe(this, event) }
        return super.dispatchKeyEvent(event)
    }

    // Any touch or stick movement also wakes the home screen's animations (see HomeIdle).
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) com.shelf.launcher.ui.KeyInput.lastWasKey = false // (Update 233, #120)
        com.shelf.launcher.ui.HomeIdle.poke()
        com.shelf.launcher.data.GameMode.userIsHere() // (Update 182)
        com.shelf.launcher.data.BatterySaver.poke() // (Update 187)
        com.shelf.launcher.ui.SecondDim.poke() // (Update 188: using Voltcap keeps its second screen lit)
        if (ev.actionMasked == android.view.MotionEvent.ACTION_MOVE) com.shelf.launcher.data.AnimatedArt.onMove() // (Update 152)
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchGenericMotionEvent(ev: android.view.MotionEvent): Boolean {
        com.shelf.launcher.ui.HomeIdle.poke()
        com.shelf.launcher.ui.SecondDim.poke() // (Update 188)
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        LaunchInfo.update(intent)
        // (Update 334: "Who's playing?" asked from a game — the in-game Quick menu.)
        if (intent?.getBooleanExtra(com.shelf.launcher.ui.ProfilePicker.EXTRA, false) == true) com.shelf.launcher.ui.ProfilePicker.request = true
        com.shelf.launcher.ui.TestHooks.fromIntent(this, intent)
        // A Voltcap transfer QR code opened from a camera app: Settings › Backup picks it up.
        com.shelf.launcher.data.QrTransfer.fromIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Save-sync notifications are only for when Voltcap isn't on screen.
        com.shelf.launcher.data.SyncNotifier.appInForeground = true
        // Settings › Device › Gesture hint › Force-hide: hide the device's hint while Voltcap is in front.
        com.shelf.launcher.data.GestureHint.onForeground(applicationContext)
    }

    override fun onResume() {
        super.onResume()
        // Update 188: back in Voltcap (a game ended): its second screen lights up again.
        com.shelf.launcher.ui.SecondDim.poke()
        // Update 125: Sleep mode starts listening as soon as the system rights are there (they can come a moment after start-up).
        runCatching { com.shelf.launcher.data.SleepRadios.init(this) }
        // Adaptive quality: frames after coming back (a game ended, screen on) are always slow; skip them.
        com.shelf.launcher.device.FrameWatch.pause()
        // Notification access on but Android didn't bind the listener after a reboot/update: ask it to.
        com.shelf.launcher.data.ShelfNotificationListener.rebindIfNeeded(applicationContext)
        hideSystemBars()
        // 0.6.15: Settings › Device › Screen rate — the window asks for the fastest mode; where Voltcap can set the rate
        // itself it goes up once Voltcap has really come back (a moment, with the controller).
        com.shelf.launcher.device.VoltcapRate.applyWindow(this)
        // 0.6.18: the charging log notes whether Voltcap or another app was in front, and starts with the app.
        com.shelf.launcher.data.ChargeLog.inFront = true
        // 0.6.18: the SaveVault mark reads Toni's picture from the app's resources.
        com.shelf.launcher.ui.Engraving.app = applicationContext
        com.shelf.launcher.data.ChargeLog.start(this)
        window.decorView.postDelayed({
            // (Top resumed, not window focus: a Settings page or popup over the home has the focus, and it's still Voltcap.)
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) && voltcapOnTop) {
                val app = applicationContext
                Thread { runCatching { com.shelf.launcher.device.VoltcapRate.enter(app) } }.start()
            }
        }, 1_200L)
        // Safety net for GameMode: if Voltcap has really been back in front for a few seconds, the
        // game is over (the view model's own check skips the first moments after a launch).
        window.decorView.postDelayed({
            // Focus too: on a two-screen handheld Voltcap can stay "resumed" on the other screen
            // while the game has the controller.
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) && hasWindowFocus()) {
                com.shelf.launcher.data.GameMode.exit()
            }
        }, 4_000L)
    }

    override fun onDestroy() {
        com.shelf.launcher.device.FrameWatch.stop(this)
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        // Update 187: behind another app Voltcap rests anyway; the battery saver's "away" starts over when it's back.
        com.shelf.launcher.data.BatterySaver.onStop()
        // …and give it back once Voltcap has left the front (a game, another app, screen off).
        com.shelf.launcher.data.GestureHint.onBackground(applicationContext)
        com.shelf.launcher.data.SyncNotifier.appInForeground = false
        // 0.6.15: the screen rate from before Voltcap raised it (Settings › Device › Screen rate).
        val app = applicationContext
        Thread { runCatching { com.shelf.launcher.device.VoltcapRate.leave(app) } }.start()
        com.shelf.launcher.data.ChargeLog.inFront = false
        // Update 274: when Voltcap left the front — a game picked from Recents afterwards still gets its play time.
        runCatching { com.shelf.launcher.data.PlaytimeStore(app).markAway(System.currentTimeMillis()) }
    }

    // A game started (Voltcap hidden) or Android needs memory: give the decoded art back, all of it
    // on small handhelds, so the emulator has the room (MemoryBudget).
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        com.shelf.launcher.device.MemoryBudget.release(this, level)
    }

    /**
     * Update 187: which app has the controller. On a two-screen handheld a game on the other screen can have it while
     * Voltcap stays resumed and drawing here; the battery saver then lets Voltcap rest (BatterySaver.away).
     */
    /** 0.6.15: Voltcap is the activity with the controller (not a game on the other screen). */
    @Volatile private var voltcapOnTop = true

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        voltcapOnTop = isTopResumedActivity
        com.shelf.launcher.data.BatterySaver.onFront(isTopResumedActivity)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
        // Update 182: focus back for a few seconds (the game on the other screen closed): game mode ends.
        if (hasFocus) window.decorView.postDelayed({
            if (hasWindowFocus() && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) com.shelf.launcher.data.GameMode.userIsHere()
        }, 4_000L)
    }

    /** Status bar hidden; navigation bar / gesture pill hidden with "Hide navigation bar" (see ui/SystemBars.kt). */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        com.shelf.launcher.ui.SystemBars.apply(window)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CrashReportScreen(report: CrashGuard.Report, onContinue: () -> Unit) {
    val context = LocalContext.current
    val export = com.shelf.launcher.ui.rememberCrashExport()
    var copied by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(report.settingsWereReset) }
    val continueFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var detailsFocused by remember { mutableStateOf(false) }

    // B / Back does the safe thing: carry on.
    BackHandler(onBack = onContinue)
    // D-pad users land on Continue.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { continueFocus.requestFocus() }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0C0D11))
                .padding(24.dp)
        ) {
            Text("Voltcap closed unexpectedly last time", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    reset -> "The display and app-library settings (theme, icons, emulator marks, category order, " +
                        "screensaver) were reset. Games, accounts, art, achievements and emulator setup are untouched."
                    report.safeMode -> "It kept crashing right after starting, so this start is in Safe mode: the intro, " +
                        "Discord, second screen, video previews, music, controller lights, background sync and news are off. " +
                        "Export the report and send it over so it can be fixed."
                    else -> "Export or copy the details below and send them over so it can be fixed. " +
                        "If Voltcap crashes again on start, reset the display settings."
                },
                color = Color(0xFFB0B6C0),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(14.dp))
            // Focusable so the D-pad can scroll a long trace (Up/Down scroll while there is more; otherwise focus moves on).
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF181B22))
                    .border(if (detailsFocused) 2.dp else 0.dp, if (detailsFocused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                    .onFocusChanged { detailsFocused = it.isFocused }
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when {
                            ev.key == Key.DirectionDown && scroll.canScrollForward -> { scope.launch { scroll.animateScrollBy(240f) }; true }
                            ev.key == Key.DirectionUp && scroll.canScrollBackward -> { scope.launch { scroll.animateScrollBy(-240f) }; true }
                            else -> false
                        }
                    }
                    .focusable()
                    .verticalScroll(scroll)
                    .padding(12.dp)
            ) {
                Text(report.details, color = Color(0xFFD6DAE0), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(14.dp))
            // Update 144: the buttons wrap onto a second line when they don't fit (small handhelds squeezed the last
            // one, Continue — where focus lands — to nothing), and Continue comes first so it's always on the first line.
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CrashButton("Continue", primary = true, modifier = Modifier.focusRequester(continueFocus), onClick = onContinue)
                CrashButton("Copy details") {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Voltcap crash", report.details))
                    copied = true
                }
                if (report.id != null) CrashButton("Export crash report") { export(report.id) }
                if (!reset) {
                    CrashButton("Reset display settings") {
                        val names = CrashGuard.resetSettings(context)
                        reset = true
                        val id = report.id
                        if (id != null) Thread {
                            com.shelf.launcher.data.crash.CrashReports.appendNote(
                                context, id, "Settings reset on request: " + names.joinToString(", ").ifBlank { "none had saved values" }
                            )
                        }.start()
                    }
                }
            }
        }
    }
}

/** Plain button with a clear focus outline; A (gamepad) activates it like Enter. */
@Composable
private fun CrashButton(label: String, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val chain = modifier
        .onPreviewKeyEvent { ev ->
            // Compose's click handling knows Enter / D-pad centre; make gamepad A do the same.
            if (ev.key == Key.ButtonA) {
                if (ev.type == KeyEventType.KeyUp) onClick()
                true
            } else false
        }
        .onFocusChanged { focused = it.hasFocus }
        .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
    if (primary) Button(onClick = onClick, modifier = chain) { Text(label) }
    else OutlinedButton(onClick = onClick, modifier = chain) { Text(label) }
}

/** Shown over the launcher during a safe-mode run. */
@Composable
private fun SafeModeBanner(onExport: () -> Unit, onStartNormally: () -> Unit, modifier: Modifier = Modifier) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Row(
            modifier
                .padding(8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xE6181B22))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Safe mode — Voltcap closed unexpectedly.", color = Color.White, fontSize = 12.sp)
            CrashButton("Export report", onClick = onExport)
            CrashButton("Start normally", onClick = onStartNormally)
        }
    }
}

/** How Voltcap was opened, for things like skipping the intro on the home button. */
object LaunchInfo {
    @Volatile var fromHomeButton: Boolean = false
        private set

    fun update(intent: android.content.Intent?) {
        fromHomeButton = intent?.hasCategory(android.content.Intent.CATEGORY_HOME) == true
    }

    /** The intro plays on a normal start, and when Voltcap comes up as the
     * home screen right after the device boots — not on every home press. */
    fun shouldPlayIntro(): Boolean =
        !CrashGuard.safeMode && (!fromHomeButton || android.os.SystemClock.elapsedRealtime() < 3 * 60_000L)
}
