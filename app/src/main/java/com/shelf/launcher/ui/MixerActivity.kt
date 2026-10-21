package com.shelf.launcher.ui

import androidx.activity.enableEdgeToEdge
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.device.audio.AppMixer
import com.shelf.launcher.ui.theme.ShelfLauncherTheme
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The volume mixer as a small card over whatever is on screen (a game included): the media volume (and the
 * other volumes), then each app that plays sound, with its own volume. Opens from the Voltcap mixer Quick
 * Settings tile, the R1 menu, the in-game panel and a hotkey. Tap outside, Done or B to close.
 */
class MixerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        com.shelf.launcher.CrashGuard.install(this)
        super.onCreate(savedInstanceState)
        // Update 136: drawn edge to edge like Voltcap's home, with the bars hidden from the start. Without it Android
        // painted a black navigation-bar strip along the bottom while this window was open, even with the bar hidden.
        enableEdgeToEdge()
        runCatching { SystemBars.apply(window) }
        com.shelf.launcher.data.Features.init(this)
        com.shelf.launcher.device.SystemSync.start(this)
        val theme = runCatching { com.shelf.launcher.data.ThemeStore(this).load() }.getOrNull()
        val appearance = runCatching { com.shelf.launcher.data.AppearanceStore(this).load() }.getOrNull()
        setContent {
            if (theme != null && appearance != null) ShelfLauncherTheme(appTheme = theme, appearance = appearance) { Panel { finish() } }
            else ShelfLauncherTheme { Panel { finish() } }
        }
    }

    // Update 130: Hide navigation bar applies to this window too (it used to bring the bar back while open).
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) runCatching { SystemBars.apply(window) }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_BUTTON_B) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }

    @Composable
    private fun Panel(onDone: () -> Unit) {
        val screen = screenSizeDp()
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(Unit) { detectTapGestures { onDone() } },
            contentAlignment = Alignment.Center
        ) {
            val shape = uiRounded(18.dp)
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth(0.94f).heightIn(max = screen.height * 0.94f)
                    .padding(vertical = 12.dp)
                    .voltSurface(shape, base = ShelfTheme.colors.surface, alpha = 0.97f)
                    .pointerInput(Unit) { detectTapGestures { } } // taps on the card stay on it
            ) {
                val scroll = rememberScrollState()
                Column(
                    Modifier.weight(1f, fill = false).dpadScroll(scroll).verticalScroll(scroll).padding(16.dp)
                ) { MixerContent() }
                HeaderDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
                    Chip("Done", onClick = onDone)
                }
            }
        }
    }
}

/** The mixer itself: media and other volumes, then one row per app that plays sound (or has its own volume). */
@Composable
internal fun MixerContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppMixer.AudioApp>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var method by remember { mutableStateOf(AppMixer.method(context)) }
    var picking by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    // Before a mute, so unmuting brings that volume back.
    val beforeMute = remember { mutableStateMapOf<String, Int>() }
    val pending = remember { HashMap<String, Job>() }
    // Update 122: volumes you just moved, kept on screen for a moment over what a look in the background reads (it
    // could still read the old value while the new one was being saved: the slider jumped to 100 % and back).
    val heldUntil = remember { HashMap<String, Long>() }
    fun merged(fresh: List<AppMixer.AudioApp>?): List<AppMixer.AudioApp>? {
        val now = android.os.SystemClock.uptimeMillis()
        val local = apps?.associateBy { it.packageName }.orEmpty()
        return fresh?.map { f ->
            val keep = (heldUntil[f.packageName] ?: 0L) > now || pending[f.packageName]?.isActive == true
            local[f.packageName]?.takeIf { keep }?.let { f.copy(volume = it.volume) } ?: f
        }
    }
    val sync by com.shelf.launcher.device.SystemSync.version.collectAsState()
    // (Update 299: also when a volume was changed elsewhere — the second screen's Volume Mixer widget — AppMixer.changes.)
    val changes by AppMixer.changes.collectAsState()
    LaunchedEffect(reload, changes) {
        apps = merged(withContext(Dispatchers.IO) { runCatching { AppMixer.apps(context) }.getOrDefault(emptyList()) })
    }
    // A slow second look: a game that just started making sound shows up without pressing Refresh.
    LaunchedEffect(Unit) {
        while (true) {
            delay(6_000L)
            apps = merged(withContext(Dispatchers.IO) { runCatching { AppMixer.apps(context) }.getOrNull() }) ?: apps
        }
    }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(120); runCatching { firstFocus.requestFocus() } }

    fun setLocal(pkg: String, v: Int) {
        heldUntil[pkg] = android.os.SystemClock.uptimeMillis() + 3_000L
        apps = apps?.map { if (it.packageName == pkg) it.copy(volume = v) else it }
    }
    /** Applies after a short pause, so holding ◀ ▶ or dragging doesn't restart the helper on every step. */
    fun commit(a: AppMixer.AudioApp, v: Int) {
        setLocal(a.packageName, v)
        // (Update 204: numbered as it's asked, so a slower earlier step finishing last can't put an older volume back.)
        val ticket = AppMixer.ticket(a.packageName)
        pending.remove(a.packageName)?.cancel()
        pending[a.packageName] = scope.launch {
            delay(280)
            withContext(Dispatchers.IO) { runCatching { AppMixer.setVolume(context, a.packageName, v, forGame = a.gameOnly, ticket = ticket) } }
            // (Shown a moment longer than it takes to set, so a look in the background reads the new one.)
            if (!AppMixer.stale(a.packageName, ticket)) setLocal(a.packageName, v)
        }
    }

    val status = AppMixer.status
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(Icons.Filled.VolumeUp, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Volume mixer", color = ShelfTheme.colors.textPrimary, fontSize = 17.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(17.sp), maxFontSize = 17.sp), fontWeight = FontWeight.Bold, fontFamily = headingFont(), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(
                when {
                    !AppMixer.available(context) -> "Media volume only on this device"
                    status.method == null && apps != null -> "Per-app volume isn't working yet"
                    status.method != null -> "Per app · ${status.method.label}" + (status.source?.let { " · from $it" } ?: "")
                    else -> "Each app's volume, on top of the media volume"
                },
                color = ShelfTheme.textMuted, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
    Spacer(Modifier.height(12.dp))

    // ---- system volumes ----
    StreamRow("Media", AudioManager.STREAM_MUSIC, sync, Modifier.focusRequester(firstFocus))
    FoldGroup("Other volumes", summary = "Notifications, ringtone, alarms, system sounds, calls", key = "mixer_other") {
        StreamRow("Notifications", AudioManager.STREAM_NOTIFICATION, sync)
        StreamRow("Ringtone", AudioManager.STREAM_RING, sync)
        StreamRow("Alarms", AudioManager.STREAM_ALARM, sync)
        StreamRow("System sounds", AudioManager.STREAM_SYSTEM, sync)
        StreamRow("Calls", AudioManager.STREAM_VOICE_CALL, sync)
    }

    Spacer(Modifier.height(8.dp))
    SettingsHeading("Apps")
    if (!AppMixer.available(context)) {
        MutedNote(
            "Volume per app needs the handheld's own root service (AYN and Retroid firmware have one), " +
                "or Android's audio report: connect to a computer once and run\n${com.shelf.launcher.device.audio.AudioReport.adbCommand(context)}"
        )
    } else {
        val list = apps
        when {
            list == null -> MutedNote("Looking for apps playing sound…")
            list.isEmpty() -> MutedNote(status.problem ?: "No app is playing sound right now. Start the game or music (or add an app below) and it shows here.")
            else -> {
                status.problem?.let { p ->
                    VoltEdgeCard(com.shelf.launcher.ui.theme.Ds.warning) { Text(p, color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, lineHeight = 16.sp) }
                    Spacer(Modifier.height(6.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { a ->
                        MixerAppRow(
                            a,
                            onVolume = { v -> commit(a, v) },
                            onMute = {
                                if (a.volume == 0) commit(a, beforeMute.remove(a.packageName) ?: 100)
                                else { beforeMute[a.packageName] = a.volume; commit(a, 0) }
                            },
                            onFull = { commit(a, 100) },
                            onGameOnly = { on ->
                                scope.launch {
                                    withContext(Dispatchers.IO) { runCatching { AppMixer.setGameOnly(context, on) } }
                                    reload++
                                }
                            },
                            onUnpin = {
                                AppMixer.pin(context, a.packageName, false)
                                scope.launch { withContext(Dispatchers.IO) { runCatching { AppMixer.setVolume(context, a.packageName, 100) } }; reload++ }
                            }
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    ChipFlow {
        Chip("Refresh", accent = false) { reload++ }
        if (AppMixer.available(context)) Chip(if (picking) "Close the app list" else "Add an app", accent = false) { picking = !picking }
        if (apps?.any { it.volume != 100 } == true) Chip("All back to 100 %", accent = false) {
            apps = apps?.map { it.copy(volume = 100) }
            scope.launch(Dispatchers.IO) { runCatching { AppMixer.resetAll(context) } }
        }
        Chip(if (checking) "Hide the check" else "Mixer check", accent = false) { checking = !checking }
    }
    if (picking) AddAppList(apps.orEmpty().map { it.packageName }.toSet()) { pkg ->
        AppMixer.pin(context, pkg, true)
        picking = false
        reload++
    }
    if (checking) MixerCheck()
    if (AppMixer.available(context)) {
        Spacer(Modifier.height(6.dp))
        FoldGroup("How volumes are set", summary = method.label, key = "mixer_method") {
            AppMixer.Method.entries.forEach { m ->
                RadioLine(m.label, method == m, m.blurb) {
                    method = m
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { AppMixer.setMethod(context, m) } }
                        reload++
                    }
                }
            }
        }
        MutedNote("50 % sounds about half as loud. Kept after a restart; an app that changes its own volume (a fade) is brought back within a second or two.")
    }
    if (AppMixer.available(context)) {
        val f = com.shelf.launcher.data.Features
        SwitchRow(
            Icons.Filled.VolumeUp, "Mixer in the notification shade",
            "A notification in Android's pull-down with − / + per app (10 % a tap) and a ↻ to look again. A notification can't hold a slider; the sliders are here.",
            f.mixerNotification.state.value,
            { on ->
                f.mixerNotification.value = on
                com.shelf.launcher.device.audio.MixerNotification.refreshAsync(context)
            }
        )
    }
}

/** One of Android's own volumes (media, notifications…), with ◀ ▶ steps. */
@Composable
internal fun StreamRow(label: String, stream: Int, sync: Any?, modifier: Modifier = Modifier, padId: String? = null) {
    val context = LocalContext.current
    val am = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember(stream) { runCatching { am.getStreamMaxVolume(stream) }.getOrDefault(1).coerceAtLeast(1) }
    val min = remember(stream) { if (android.os.Build.VERSION.SDK_INT >= 28) runCatching { am.getStreamMinVolume(stream) }.getOrDefault(0) else 0 }
    var level by remember(stream) { mutableIntStateOf(runCatching { am.getStreamVolume(stream) }.getOrDefault(0)) }
    var refused by remember { mutableStateOf(false) }
    // Changed with the volume keys or Android's own slider: follow it.
    LaunchedEffect(sync) { level = runCatching { am.getStreamVolume(stream) }.getOrDefault(level) }
    val percent = level * 100 / max
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$percent %", color = ShelfTheme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        VolumeBar(
            percent = percent,
            steps = max,
            onChange = { p ->
                val step = ((p * max + 50) / 100).coerceIn(min, max)
                refused = runCatching { am.setStreamVolume(stream, step, 0) }.isFailure
                level = runCatching { am.getStreamVolume(stream) }.getOrDefault(level)
            },
            onToggle = {
                val target = if (level > min) min else (max * 0.6f).roundToInt().coerceAtLeast(min + 1)
                refused = runCatching { am.setStreamVolume(stream, target, 0) }.isFailure
                level = runCatching { am.getStreamVolume(stream) }.getOrDefault(level)
            },
            live = true,
            padId = padId
        )
        if (refused) MutedNote("Android didn't allow this change (Do not disturb is probably on).")
    }
}

/** One app: icon, name, what it plays, its volume bar with mute, and the game-only switch while that game runs. */
@Composable
private fun MixerAppRow(
    a: AppMixer.AudioApp,
    onVolume: (Int) -> Unit,
    onMute: () -> Unit,
    onFull: () -> Unit,
    onGameOnly: (Boolean) -> Unit,
    onUnpin: () -> Unit
) {
    val shape = uiRounded(14.dp)
    Column(
        Modifier.fillMaxWidth()
            .voltSurface(shape, edge = if (a.runningGame != null && VoltUi.on) ShelfTheme.accent else null)
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = rememberAppIcon(a.packageName)
            Box(Modifier.size(34.dp).clip(uiRounded(9.dp)).background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
                if (icon != null) Image(icon, null, modifier = Modifier.size(34.dp))
                else Icon(Icons.Filled.Apps, null, tint = ShelfTheme.textMuted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(a.label, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(14.sp), maxFontSize = 14.sp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (a.playing) { PlayingBars(); Spacer(Modifier.width(5.dp)) }
                    val note = listOfNotNull(
                        a.runningGame?.let { "Playing $it" },
                        a.kind.takeIf { a.runningGame == null },
                        "quiet".takeIf { !a.playing && a.runningGame == null }
                    ).joinToString(" · ").replaceFirstChar { it.uppercase() } // Update 144: "Quiet" when it comes first.
                    Text(note, color = if (a.playing) ShelfTheme.accent else ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(if (a.volume == 0) "Muted" else "${a.volume} %", color = if (a.volume == 100) ShelfTheme.textMuted else ShelfTheme.colors.textPrimary, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(2.dp))
        VolumeBar(percent = a.volume, steps = 20, onChange = onVolume, onToggle = onMute)
        a.note?.let { Text(it, color = com.shelf.launcher.ui.theme.Ds.warning, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 2.dp)) }
        if (a.runningGame != null || a.volume != 100 || a.pinned) {
            ChipFlow(Modifier.padding(top = 2.dp)) {
                if (a.runningGame != null) Chip((if (a.gameOnly) "✓ " else "") + "Just for this game", accent = a.gameOnly) { onGameOnly(!a.gameOnly) }
                if (a.volume != 100 && a.volume != 0) Chip("100 %", accent = false, onClick = onFull)
                if (a.pinned) Chip("Remove", accent = false, onClick = onUnpin)
            }
        }
    }
}

/**
 * The volume bar: one D-pad stop (◀ ▶ = 5 %, A = mute / unmute) with the shared focus ring, a mute button
 * and a slider for touch. Reports when let go, or every step with [live].
 */
@Composable
internal fun VolumeBar(percent: Int, steps: Int, onChange: (Int) -> Unit, onToggle: () -> Unit, live: Boolean = false, padId: String? = null) {
    var drag by remember(percent) { mutableStateOf(percent.toFloat()) }
    var focused by remember { mutableStateOf(false) }
    val step = if (steps in 1..19) (100f / steps) else 5f
    // (Update 299: on the second screen — the Widget Board's Volume Mixer — the controller reaches it through SecondPad:
    // ◀ ▶ the same steps, A mute / unmute.)
    fun stepBy(dir: Int) {
        val next = (drag + dir * step).coerceIn(0f, 100f)
        val snapped = ((next / step).roundToInt() * step).coerceIn(0f, 100f).roundToInt()
        if (snapped != drag.roundToInt()) { drag = snapped.toFloat(); onChange(snapped) }
    }
    Row(
        Modifier.fillMaxWidth()
            .then(if (padId != null) Modifier.padTarget(padId, onMove = { d -> if (d <= 1) { stepBy(if (d == 0) -1 else 1); true } else false }) { onToggle() } else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                val dir = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
                if (dir != 0) {
                    if (e.type == KeyEventType.KeyDown) stepBy(dir)
                    true
                } else if (e.key == Key.ButtonA || e.key == Key.Enter || e.key == Key.DirectionCenter) {
                    if (e.type == KeyEventType.KeyUp) onToggle()
                    true
                } else false
            }
            .focusable()
            .focusRing(focused, RoundedCornerShape(10.dp))
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape)
                .background(if (percent == 0) com.shelf.launcher.ui.theme.Ds.danger.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
                .pointerInput(Unit) { detectTapGestures { onToggle() } },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (percent == 0) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                contentDescription = if (percent == 0) "Unmute" else "Mute",
                tint = if (percent == 0) com.shelf.launcher.ui.theme.Ds.danger else ShelfTheme.textMuted,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Slider(
            modifier = Modifier.weight(1f).height(32.dp).focusProperties { canFocus = false },
            value = drag,
            onValueChange = { drag = it; if (live) onChange(it.roundToInt()) },
            onValueChangeFinished = { onChange(drag.roundToInt()) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = ShelfTheme.accent,
                activeTrackColor = ShelfTheme.accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
            )
        )
    }
}

/** Three little bars bouncing: this app is playing sound right now. */
@Composable
private fun PlayingBars() {
    val accent = ShelfTheme.accent
    val still = LocalLiteEffects.current || !rememberAnimationsEnabled()
    val t = if (still) null else rememberInfiniteTransition(label = "mixBars").animateFloat(
        0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "mixBarsT"
    )
    Canvas(Modifier.size(width = 11.dp, height = 10.dp)) {
        val w = size.width / 3f
        val ph = t?.value ?: 0.3f
        for (i in 0..2) {
            val h = size.height * (0.35f + 0.65f * kotlin.math.abs(kotlin.math.sin((ph + i * 0.33f) * Math.PI)).toFloat())
            drawRect(accent, Offset(i * w + w * 0.15f, size.height - h), Size(w * 0.7f, h))
        }
    }
}

/** Every launchable app, to add one before it plays (its volume then holds from its first sound). */
@Composable
private fun AddAppList(already: Set<String>, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var all by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(Unit) { all = withContext(Dispatchers.IO) { runCatching { AppMixer.launchables(context) }.getOrDefault(emptyList()) } }
    Spacer(Modifier.height(8.dp))
    val shape = uiRounded(12.dp)
    Column(Modifier.fillMaxWidth().voltSurface(shape).padding(8.dp)) {
        val list = all?.filter { it.first !in already }
        when {
            list == null -> MutedNote("Reading your apps…")
            list.isEmpty() -> MutedNote("Every app is already in the mixer.")
            else -> list.forEach { (pkg, label) ->
                Row(
                    Modifier.fillMaxWidth().clip(uiRounded(10.dp)).shelfClickable(uiRounded(10.dp)) { onPick(pkg) }.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = rememberAppIcon(pkg)
                    if (icon != null) Image(icon, null, modifier = Modifier.size(24.dp)) else Spacer(Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(label, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** "Mixer check": each step of the mixer and what it answered, to see where it stops (and to share). */
@Composable
private fun MixerCheck() {
    val context = LocalContext.current
    var lines by remember { mutableStateOf<List<AppMixer.Check>?>(null) }
    var copied by remember { mutableStateOf(false) }
    var runs by remember { mutableIntStateOf(0) }
    var duck by remember { mutableStateOf(AppMixer.duckWay(context)) }
    var switching by remember { mutableStateOf(false) }
    LaunchedEffect(runs) { lines = null; lines = withContext(Dispatchers.IO) { runCatching { AppMixer.diagnose(context) }.getOrElse { listOf(AppMixer.Check("Check", false, it.message ?: "failed")) } } }
    Spacer(Modifier.height(8.dp))
    val shape = uiRounded(12.dp)
    Column(Modifier.fillMaxWidth().voltSurface(shape).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val l = lines
        if (l == null) MutedNote("Checking each step (a few seconds)…")
        else {
            l.forEach { c ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.padding(top = 2.dp).size(16.dp).clip(CircleShape).background(
                            when (c.ok) { true -> com.shelf.launcher.ui.theme.Ds.success; false -> com.shelf.launcher.ui.theme.Ds.danger; null -> ShelfTheme.accent.copy(alpha = 0.55f) }
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Update 121: a line that's only information (which Android, nothing playing yet) shows an "i",
                        // not an empty circle that read as a failed step.
                        Icon(
                            when (c.ok) { true -> Icons.Filled.Check; false -> Icons.Filled.Close; null -> Icons.Filled.Info },
                            null, tint = Color.White, modifier = Modifier.size(11.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.step, color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(c.detail, color = ShelfTheme.textMuted, fontSize = 11.sp, lineHeight = 14.sp)
                    }
                }
            }
            ChipFlow {
                Chip(if (copied) "✓ Copied" else "Copy the report", accent = false) {
                    val text = l.joinToString("\n") { "${when (it.ok) { true -> "OK"; false -> "NO"; null -> "INFO" }} ${it.step}: ${it.detail}" }
                    runCatching {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Voltcap mixer check", text))
                        copied = true
                    }
                }
                Chip("Check again", accent = false) { copied = false; runs++ }
            }
            // Update 121: the second way of the player-volume helper (update 122: only shown with "Player volume" picked;
            // Automatic uses the sound effect now).
            if (AppMixer.method(context) == AppMixer.Method.HELPER) {
                Text("Way", color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                ChipFlow {
                    Chip((if (!duck) "✓ " else "") + "Player volume", accent = !duck) {
                        if (duck && !switching) { switching = true; duck = false }
                    }
                    Chip((if (duck) "✓ " else "") + "System duck", accent = duck) {
                        if (!duck && !switching) { switching = true; duck = true }
                    }
                }
                LaunchedEffect(duck) {
                    if (duck != AppMixer.duckWay(context)) {
                        withContext(Dispatchers.IO) { runCatching { AppMixer.setDuckWay(context, duck) } }
                        switching = false
                        runs++
                    } else switching = false
                }
                MutedNote(
                    "Player volume sets each app's own player volume. System duck lowers it the way Android quiets an app under " +
                        "a notification, which the app can't undo. If every step above passes but an app doesn't get quieter, " +
                        "try the other way while it plays."
                )
            } else MutedNote("Volumes are set with the sound effect on each app's audio session. If an app line above says \"refused\" " +
                "or \"no audio session\", that app's sound can't take an effect: copy the report and send it.")
        }
    }
}
