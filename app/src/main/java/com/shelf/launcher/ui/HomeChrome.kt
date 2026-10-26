package com.shelf.launcher.ui

import com.shelf.launcher.data.dockHere
import kotlinx.coroutines.flow.first

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.border
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import android.view.InputDevice
import android.view.MotionEvent
import kotlin.math.abs
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import com.shelf.launcher.data.actionFor
import com.shelf.launcher.data.hotkeyFor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.shelf.launcher.data.RaBadges
import com.shelf.launcher.ArtConnectionState
import com.shelf.launcher.LauncherViewModel
import com.shelf.launcher.RaConnectionState
import com.shelf.launcher.SpecialFilter
import com.shelf.launcher.forDisplay
import com.shelf.launcher.usesOwnIcon
import kotlinx.coroutines.launch
import com.shelf.launcher.data.ClockHours
import com.shelf.launcher.data.StatusBarPrefs
import androidx.compose.ui.platform.LocalContext
import com.shelf.launcher.ui.theme.ShelfTheme
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.boundsInRoot

// The home screen's smaller pieces — status bar, category pills, button
// legend, controls card, background art — split out of LauncherScreen.kt.


/**
 * Update 330 (phase 4): a layout with its own top row instead of the status bar (Channel grid, the Pocket layouts, Hero
 * tiles' and Twin showcase's status pill) makes room there for the round shortcuts' ☰ — [want] says it should, [rect] is
 * that room (root px) — LauncherScreen puts the ☰ in it (in the corner as before while there's none).
 */
internal object HomeMenuSlot {
    var want by androidx.compose.runtime.mutableStateOf(false)
    var rect by androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)

    /** The room itself (in a row: 44 dp wide; or where [modifier] puts it), while [want]. */
    @Composable
    fun Room(modifier: Modifier = Modifier.width(44.dp).height(28.dp)) {
        if (!want) return
        Spacer(modifier.onGloballyPositioned { val r = it.boundsInRoot(); if (rect != r) rect = r })
        DisposableEffect(Unit) { onDispose { rect = null } }
    }
}

@Composable
internal fun StatusBar(
    scrapeDone: Int,
    scrapeTotal: Int,
    raMatchDone: Int,
    raMatchTotal: Int,
    artSaveDone: Int,
    artSaveTotal: Int,
    detailsDone: Int = 0,
    detailsTotal: Int = 0,
    raProfile: com.shelf.launcher.data.RaUserProfile?,
    onProfileClick: () -> Unit = {},
    onSearchButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
    nowPlaying: com.shelf.launcher.data.NowPlaying? = null,
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    showLyrics: Boolean = false,
    miniFocus: Int? = null,
    onSeek: (Long) -> Unit = {},
    /** Drawn along the bottom edge (when the games sit at the top). */
    atBottom: Boolean = false,
    /** Update 329: a pale backdrop behind it (Twin desk's paper, Live tiles / Channel grid / Hero tiles pale): a stronger shade. */
    paleBehind: Boolean = false,
    /** Waiting notifications; the bell is hidden at 0. */
    notificationCount: Int = 0,
    /** Tap on the bell: the notifications it counts. */
    onNotifications: () -> Unit = {},
    /** Google Drive sync indicator (left of the clock). */
    syncSlot: (@Composable () -> Unit)? = null,
    /** Achievements earned just before the last game ended (0 = no trophy). */
    trophyCount: Int = 0,
    onTrophy: () -> Unit = {},
    /** Update 185: a tap on the progress text — what's running in the background (BackgroundWorkDialog). */
    onProgressClick: () -> Unit = {},
    /**
     * Update 330 (phase 4): room for the round shortcuts' ☰ in the bar itself — -1 at its left end, 1 just before the
     * clock and icons, 0 none — and where that room is (root px), for the ☰ drawn over it (LauncherScreen).
     */
    menuSlot: Int = 0,
    onMenuSlot: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    /** Update 333: how it's dressed for the layout it's on (StatusLooks.kt); the plain look by default. */
    look0: StatusLook = PlainStatusLook
) {
    com.shelf.launcher.Breadcrumbs.mark("StatusBar")
    val look = animatedLook(look0)
    val ink = look.ink
    val barAccent = if (look.accent == Color.Unspecified) ShelfTheme.accent else look.accent
    val battery = rememberBatteryInfo()
    val context = LocalContext.current
    val bar by StatusBarPrefs.state(context).collectAsState()
    val clockText = rememberClockText(bar.clock, bar.hours, bar.showDate)
    val wifiConnected = rememberWifiConnected()
    val accessories = rememberAccessories(bar.accessories)
    var showDevices by remember { mutableStateOf(false) }
    // Narrow screens: the clock and the first accessory keep their place, RA points go first.
    val widthDp = LocalConfiguration.current.screenWidthDp
    val maxChips = if (widthDp < 480) 1 else 2

    // Tight to the screen corner, like Android's own status bar: small
    // padding, and the icons pinned to the top (or bottom) edge instead of
    // centred on the taller music player next to them.
    // Update 119: the work running in the background as a thin line along the bar's edge — always there while it
    // runs (the words beside it give way to the music player on small screens), filling up as it goes.
    val running = listOf(scrapeDone to scrapeTotal, artSaveDone to artSaveTotal, detailsDone to detailsTotal, raMatchDone to raMatchTotal)
        .firstOrNull { it.second > 0 }
    val lineColor = barAccent
    val shown by androidx.compose.animation.core.animateFloatAsState(
        running?.let { (d, t) -> (d.toFloat() / t).coerceIn(0f, 1f) } ?: 0f,
        androidx.compose.animation.core.tween(400), label = "taskLine"
    )
    // The clock, battery, icons and your picture (update 334: grouped and put together the layout's way).
    val cluster: @Composable () -> Unit = {
            // Compact, like Android's own: small icons, narrow gaps. The bell only shows when there's something, with how many.
            // Update 202: the clock, battery, icons and profile are moved / resized / taken off in Edit Home (each layout its own).
            // Update 334: grouped — time · devices · actions · system · you — and put together the layout's way (StatusLooks):
            // one box, a box per group, a readout, a label, a ruled line or nothing, with what the look puts between groups.
            // Narrow screens: tighter, no separators, the percentage left to the gauge.
            val compact = widthDp < 520
            val gap = if (compact) (look.gap - 2.dp).coerceAtLeast(3.dp) else look.gap
            val segmented = look.backing == BarBacking.SEGMENTS
            val sep = look.sep.takeUnless { compact || segmented }
            val textStyle = look.textStyle(uiFontStyle().merge(CenteredLine))
            val small = 12.sp
            val voltGauge = VoltUi.on && com.shelf.launcher.data.Features.voltBattery.state.value
            val clockShown = clockText != null && OwnClock.count == 0
            val pieceH = Modifier.heightIn(min = look.avatarSize.coerceAtMost(26.dp))
            @Composable
            fun Sep() {
                if (sep != null) Text(sep, color = ink.copy(alpha = 0.45f), fontSize = small, style = textStyle, modifier = Modifier.padding(horizontal = gap / 2))
                else Spacer(Modifier.width(if (segmented) gap else gap + 2.dp))
            }
            @Composable
            fun Group(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
                Row(Modifier.statusSegment(look), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap), content = content)
            }
            Row(
                Modifier.editablePart(LayoutParts.id(HomeEditState.layout, "status"), "Clock, battery & icons").statusContainer(look).then(pieceH),
                verticalAlignment = Alignment.CenterVertically
            ) {
            if (menuSlot > 0) Spacer(Modifier.width(44.dp).height(28.dp).onGloballyPositioned { onMenuSlot(it.boundsInRoot()) })
            // Time (and the Drive sync beside it). (Update 333: no clock while the layout shows its own — OwnClock.)
            if (syncSlot != null || clockShown) {
                Group {
                    syncSlot?.invoke()
                    if (clockShown) Box(contentAlignment = Alignment.CenterEnd) {
                        val t = look.cased(clockText!!)
                        // An LCD's unlit segments behind the digits.
                        if (look.ghost) Text(t.map { if (it.isDigit()) '8' else it }.joinToString(""), color = ink.copy(alpha = 0.12f), fontSize = look.clockSize, fontWeight = look.clockWeight, maxLines = 1, softWrap = false, style = textStyle.merge(androidx.compose.ui.text.TextStyle(shadow = null)))
                        Text(t, color = ink, fontSize = look.clockSize, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(look.clockSize), maxFontSize = look.clockSize), lineHeight = androidx.compose.ui.unit.TextUnit(1.25f, androidx.compose.ui.unit.TextUnitType.Em), fontWeight = look.clockWeight, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false, style = textStyle)
                    }
                }
                Sep()
            }
            // Controllers and other devices.
            if (accessories.isNotEmpty()) {
                Group { AccessoryChips(accessories, maxChips, ink) { showDevices = true } }
                Sep()
            }
            // Search, the trophy from the last game, notifications.
            Group {
                Icon(Icons.Filled.Search, null, tint = ink, modifier = Modifier.size(look.iconSize).tapOnly(onSearchButtonClick))
                if (trophyCount > 0) ReturnTrophy(trophyCount, onTrophy)
                if (notificationCount > 0) Row(Modifier.tapOnly(onNotifications), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Notifications, null, tint = ink, modifier = Modifier.size(look.iconSize))
                    Text(
                        if (notificationCount > 99) "99+" else "$notificationCount",
                        color = ink, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold,
                        style = textStyle, modifier = Modifier.padding(start = 1.dp)
                    )
                }
            }
            Sep()
            // Wi-Fi and the battery.
            Group {
                Icon(if (wifiConnected) Icons.Filled.Wifi else Icons.Filled.WifiOff, null, tint = ink, modifier = Modifier.size(look.iconSize))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    // Volt looks: a little charge gauge (HomeExtras.kt) — update 334: in the bar's ink unless it's really low.
                    if (voltGauge) VoltBattery(battery.percent, battery.charging, LowBatteryColor, ink, fill = if (look.plainBattery) ink else null)
                    else Icon(
                        if (battery.charging) Icons.Filled.BatteryChargingFull else batteryIconFor(battery.percent, false), null,
                        tint = if (!battery.charging && battery.percent < 15) LowBatteryColor else ink,
                        modifier = Modifier.size(look.iconSize)
                    )
                    if (bar.showPercent && !(compact && voltGauge)) Text("${battery.percent}%", color = ink, fontSize = small, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(small), maxFontSize = small), lineHeight = androidx.compose.ui.unit.TextUnit(1.333f, androidx.compose.ui.unit.TextUnitType.Em), fontWeight = look.weight, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false, style = textStyle)
                }
            }
            Sep()
            // You: RetroAchievements points (wide screens) and your picture. Falls back to the profile's initial, so the
            // bar never looks empty.
            val avatarUrl = profilePictureUrl(RaBadges.mediaUrl(raProfile?.UserPic))
            Group {
                if (raProfile != null && widthDp >= 600) raProfile.TotalPoints?.let { points ->
                    Text(
                        "$points", color = barAccent, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold,
                        // (0.6.17: a dark edge — in the theme's colour over a picture of the same colour it disappeared.
                        //  Update 334: not on a box of its own, where it's on the box's colour.)
                        style = if (look.boxed || segmented || ink.luminance() < 0.5f) textStyle else textStyle.merge(
                            androidx.compose.ui.text.TextStyle(
                                shadow = look.glow?.let { androidx.compose.ui.graphics.Shadow(it, androidx.compose.ui.geometry.Offset.Zero, 10f) } ?: androidx.compose.ui.graphics.Shadow(
                                    Color.Black.copy(alpha = 0.85f), androidx.compose.ui.geometry.Offset(0f, 1f),
                                    2.5f * androidx.compose.ui.platform.LocalDensity.current.density
                                )
                            )
                        )
                    )
                }
                val pic = Modifier.size(look.avatarSize)
                    .then(if (look.ring != null) Modifier.border(2.dp, look.ring, look.avatarShape()).padding(3.dp) else Modifier)
                    .clip(look.avatarShape()).tapOnly(onProfileClick)
                if (avatarUrl != null) {
                    AsyncImage(model = avatarUrl, contentDescription = raProfile?.User, contentScale = ContentScale.Crop, modifier = pic)
                } else {
                    Box(pic.background(barAccent), contentAlignment = Alignment.Center) {
                        // (Update 332: the playing profile's initial.)
                        Text(
                            com.shelf.launcher.data.Profiles.active()?.name?.trim()?.take(1)?.uppercase() ?: "P1",
                            color = if (barAccent.luminance() > 0.55f) Color(0xFF14171C) else Color.White,
                            fontWeight = FontWeight.Bold, fontSize = if (look.avatarSize < 24.dp) 11.sp else 13.sp, style = look.textStyle(uiFontStyle().merge(CenteredLine)).merge(androidx.compose.ui.text.TextStyle(shadow = null, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified))
                        )
                    }
                }
            }
            }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Update 136: a soft shade from the screen's edge, so the white clock, icons and points stay readable over a
            // bright picture. (Update 329: darker over a pale backdrop. Update 333: the layout's own backing — StatusLooks.)
            .statusBacking(look, atBottom)
            .drawBehind {
                if (running == null) return@drawBehind
                val h = 2.dp.toPx()
                val y = if (atBottom) 0f else size.height - h
                drawRect(lineColor.copy(alpha = 0.22f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, h))
                drawRect(lineColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Size(size.width * shown, h))
            }
            .padding(start = 14.dp, end = 12.dp, top = if (atBottom) 4.dp else 6.dp, bottom = if (atBottom) 6.dp else 4.dp),
        verticalAlignment = if (atBottom) Alignment.Bottom else Alignment.Top,
        // Whatever room the (fixed-size) player leaves goes between the pieces, so the clock, icons and
        // profile always stay pinned in their corner (they used to sit right next to the player).
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (menuSlot < 0) Spacer(Modifier.width(44.dp).height(28.dp).onGloballyPositioned { onMenuSlot(it.boundsInRoot()) })
        if (look.left) { cluster(); Spacer(Modifier.width(14.dp)) }
        // Optional mini music player, top left (Appearance setting).
        // It gives way to the icons on the right: the clock, battery and the profile picture keep their
        // corner and the player shrinks into what's left (it used to push the profile off the screen).
        if (nowPlaying != null) {
            Box(Modifier.weight(1f, fill = false)) {
                MiniMusicPlayer(
                    nowPlaying,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    showLyrics = showLyrics,
                    focusedSlot = miniFocus,
                    onSeek = onSeek,
                    width = if (widthDp < 480) 170.dp else 200.dp
                )
            }
            Spacer(Modifier.width(14.dp))
        }
        // Quiet progress readouts (scraping, details, saving art, matching achievements). One line,
        // in the space left over, clipped — never wrapping, so the bar's height never changes and
        // nothing below it gets pushed down on small screens.
        val progress = listOfNotNull(
            if (scrapeTotal > 0) (if (widthDp < 700) "Art $scrapeDone/$scrapeTotal" else "Getting art $scrapeDone/$scrapeTotal") else null,
            if (detailsTotal > 0) "Details $detailsDone/$detailsTotal" else null,
            if (artSaveTotal > 0) (if (widthDp < 700) "Saving $artSaveDone/$artSaveTotal" else "Saving art $artSaveDone/$artSaveTotal") else null,
            if (raMatchTotal > 0) (if (widthDp < 700) "RA $raMatchDone/$raMatchTotal" else "Matching achievements $raMatchDone/$raMatchTotal") else null
        )
        // With the player there it takes (nearly) all the spare room; the readouts get the rest.
        Box(Modifier.weight(if (nowPlaying != null) 0.001f else 1f).clipToBounds()) {
            if (progress.isNotEmpty() && !(nowPlaying != null && widthDp < 600)) {
                Text(
                    modifier = Modifier.tapOnly(onProgressClick),
                    text = 
                    progress.joinToString("  ·  "),
                    color = barAccent,
                    fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        // (Update 339: in the top right corner, or the top left for a look that wants it — StatusLook.left.)
        if (!look.left) cluster()
    }
    if (showDevices) ConnectedDevicesDialog { showDevices = false }
}

internal val LowBatteryColor = Color(0xFFFF6B6B)

/** Level 0..100 and whether it's charging; [charging] is true while plugged in and not yet full. */
internal data class BatteryInfo(val percent: Int = 100, val charging: Boolean = false)

/** Bars icon for [percent]: alert under 15 %, then 0-6 bars, full from 100 %. */
internal fun batteryIconFor(percent: Int, charging: Boolean): androidx.compose.ui.graphics.vector.ImageVector {
    if (charging) return Icons.Filled.BatteryChargingFull
    if (percent < 15) return Icons.Filled.BatteryAlert
    return when ((percent.coerceIn(0, 100) * 7) / 100) {
        0 -> Icons.Filled.Battery0Bar
        1 -> Icons.Filled.Battery1Bar
        2 -> Icons.Filled.Battery2Bar
        3 -> Icons.Filled.Battery3Bar
        4 -> Icons.Filled.Battery4Bar
        5 -> Icons.Filled.Battery5Bar
        6 -> Icons.Filled.Battery6Bar
        else -> Icons.Filled.BatteryFull
    }
}

private fun android.content.Intent.toBatteryInfo(old: BatteryInfo): BatteryInfo {
    val level = getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
    val status = getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
    val plugged = getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0)
    val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
        (status == android.os.BatteryManager.BATTERY_STATUS_FULL && plugged != 0)
    val pct = if (level >= 0 && scale > 0) (level * 100) / scale else old.percent
    return BatteryInfo(pct, charging && status != android.os.BatteryManager.BATTERY_STATUS_FULL)
}

/** Live battery level + charging via the sticky ACTION_BATTERY_CHANGED broadcast - event driven,
 * registered/unregistered with the composable so it doesn't leak. */
@Composable
internal fun rememberBatteryInfo(): BatteryInfo {
    com.shelf.launcher.Breadcrumbs.mark("rememberBatteryInfo")
    val context = androidx.compose.ui.platform.LocalContext.current
    var info by remember { mutableStateOf(BatteryInfo()) }

    // Registered only while RESUMED (no battery wake-ups in a game); each resume re-reads the sticky intent.
    val resumed = rememberIsResumed()
    DisposableEffect(resumed) {
        if (!resumed) return@DisposableEffect onDispose { }
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                intent?.let { info = it.toBatteryInfo(info) }
            }
        }
        // registerReceiver returns the current sticky battery intent immediately,
        // so this also gives an initial value without waiting for the next change.
        val sticky = context.registerReceiver(receiver, filter)
        sticky?.let { info = it.toBatteryInfo(info) }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    return info
}

@Composable
internal fun rememberBatteryPercent(): Int = rememberBatteryInfo().percent

/** Wi-Fi connected or not, from a NetworkCallback that only exists while the app is RESUMED
 * (no signal-strength polling). */
@Composable
internal fun rememberWifiConnected(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resumed = rememberIsResumed()
    fun current(): Boolean = runCatching {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
    }.getOrDefault(false)
    var connected by remember { mutableStateOf(current()) }
    DisposableEffect(resumed) {
        if (!resumed) return@DisposableEffect onDispose {}
        connected = current()
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val live = HashSet<android.net.Network>()
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { synchronized(live) { live += network; connected = true } }
            override fun onLost(network: android.net.Network) { synchronized(live) { live -= network; connected = live.isNotEmpty() } }
        }
        val registered = runCatching {
            cm?.registerNetworkCallback(
                android.net.NetworkRequest.Builder().addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI).build(), cb
            )
        }.isSuccess
        onDispose { if (registered) runCatching { cm?.unregisterNetworkCallback(cb) } }
    }
    return connected
}

/** Compact clock text, or null when the clock is off. Refreshed by minute ticks / time / timezone
 * broadcasts registered only while RESUMED - no timer loop. */
@Composable
internal fun rememberClockText(enabled: Boolean, hours: ClockHours, showDate: Boolean): String? {
    if (!enabled) return null
    val context = androidx.compose.ui.platform.LocalContext.current
    val resumed = rememberIsResumed()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    DisposableEffect(resumed) {
        if (!resumed) return@DisposableEffect onDispose {}
        now = System.currentTimeMillis()
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                now = System.currentTimeMillis()
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_TIME_TICK)
            addAction(android.content.Intent.ACTION_TIME_CHANGED)
            addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return remember(now, hours, showDate) {
        val locale = java.util.Locale.getDefault()
        val use24 = when (hours) {
            ClockHours.SYSTEM -> android.text.format.DateFormat.is24HourFormat(context)
            ClockHours.H12 -> false
            ClockHours.H24 -> true
        }
        fun fmt(skeleton: String) = java.text.SimpleDateFormat(
            android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale
        ).format(java.util.Date(now))
        val time = fmt(if (use24) "Hm" else "hm")
        if (showDate) fmt("EEEd") + "  " + time else time
    }
}

@Composable
internal fun SearchBar(
    query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier,
    /** Update 256: Enter / A / ▼ — the search stays, the bar closes and the controller is back on the games. */
    onDone: () -> Unit = {},
    /** Update 256: B / Esc — the bar closes and the whole category comes back. */
    onCancel: () -> Unit = {},
    /** 0.6.15: over a layout's own top row — a darker pill so the row's icons don't show through the text. */
    solid: Boolean = false
) {
    com.shelf.launcher.Breadcrumbs.mark("SearchBar")
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TextInputActiveEffect(focusRequester)
    // (Update 256: the keyboard goes with the bar — it stayed up on the other screen and took the next B.)
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val done: () -> Unit = { keyboard?.hide(); onDone() }
    val cancel: () -> Unit = { keyboard?.hide(); onCancel() }

    Row(
        modifier
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (solid) Color(0xF0101014) else Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { done() }, onDone = { done() }),
            modifier = Modifier
                .width(220.dp)
                .focusRequester(focusRequester)
                // (Update 256: the text field kept the controller once it had the focus — B did nothing and the only way
                // out was touch; found on the Thor while launching games by search. The controller's own keys now leave it.)
                .onPreviewKeyEvent { e ->
                    val k = e.key
                    // 0.6.15 (#120, Thor): Android's Back too, once the keyboard is down (it did nothing; only B left the bar).
                    val isCancel = k == androidx.compose.ui.input.key.Key.ButtonB || k == androidx.compose.ui.input.key.Key.Escape ||
                        k == androidx.compose.ui.input.key.Key.Back
                    val isDone = k == androidx.compose.ui.input.key.Key.ButtonA || k == androidx.compose.ui.input.key.Key.Enter ||
                        k == androidx.compose.ui.input.key.Key.NumPadEnter || k == androidx.compose.ui.input.key.Key.DirectionDown
                    if (!isCancel && !isDone) return@onPreviewKeyEvent false
                    if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyUp) { if (isCancel) cancel() else done() }
                    true
                }
        )
    }
}

@Composable
internal fun PlatformFilterRow(
    /** Space under the pills, down to the game's name (Game row › Spacing). */
    bottomGap: androidx.compose.ui.unit.Dp = 10.dp,
    platforms: List<String>,
    selected: String?,
    special: SpecialFilter?,
    order: List<String>,
    /** Collection category keys ("col:…", "fr:…", "st:…"), shown where COLLECTIONS sits. */
    collections: List<String> = emptyList(),
    onSelect: (String?) -> Unit,
    onSelectSpecial: (SpecialFilter?) -> Unit,
    /** Short name of the active sort ("A–Z"); null hides the sort chip. */
    sortLabel: String? = null,
    onSort: () -> Unit = {},
    /** Lines the pills up with the row: start (left end), centre or end (right). */
    align: Alignment.Horizontal = Alignment.Start,
    /** Where the pills start / end at the screen edge; null = where the game name starts. */
    edge: androidx.compose.ui.unit.Dp? = null
) {
    com.shelf.launcher.Breadcrumbs.mark("PlatformFilterRow")
    // Every pill needs its own key: no category listed twice.
    val orderU = remember(order) { order.distinct() }
    val platformsU = remember(platforms) { platforms.distinct() }
    val collectionsU = remember(collections, platformsU) { collections.distinct().filter { it !in platformsU } }
    val itemKeys = remember(orderU, platformsU, collectionsU) {
        buildList {
            orderU.forEach { key ->
                when (key) {
                    com.shelf.launcher.data.CategorySettings.ALL -> add(key)
                    com.shelf.launcher.data.CategorySettings.CONSOLES -> platformsU.forEach { add("p:$it") }
                    com.shelf.launcher.data.CategorySettings.COLLECTIONS -> collectionsU.forEach { add("p:$it") }
                    else -> if (runCatching { SpecialFilter.valueOf(key) }.isSuccess) add(key)
                }
            }
        }
    }
    val selectedKey = when {
        special != null -> special.name
        selected != null -> "p:$selected"
        else -> com.shelf.launcher.data.CategorySettings.ALL
    }
    // (Update 294, Toni: "never show a partly hidden chip": whole pills only, "+N ›" for the rest, the selected one always
    // whole — WholeChipRow. It used to slide the selected pill onto the game name's line in a LazyRow, which left a pill
    // cut at the screen's edge. The pills still start where the game name does, and sit left / centred / right with it.)
    // (Update 308, Toni: "smooth horizontal scrolling instead of abruptly hiding categories or jumping between groups":
    // one row that glides to keep the category you're in in view, its ends fading out softly — GlidingChipRow — in place
    // of update 294's pages of whole pills with "+N ›".)
    val e = edge ?: dockTextStart()
    Row(Modifier.padding(bottom = bottomGap), verticalAlignment = Alignment.CenterVertically) {
    GlidingChipRow(
        count = itemKeys.size, focused = itemKeys.indexOf(selectedKey), modifier = Modifier.weight(1f),
        spacing = 6.dp, align = align, startPadding = e, endPadding = if (align == Alignment.Start) 24.dp else e
    ) { i ->
        val key = itemKeys[i]
        when {
            key == com.shelf.launcher.data.CategorySettings.ALL -> FilterPill(
                label = "All",
                isSelected = selected == null && special == null,
                onClick = { onSelectSpecial(null); onSelect(null) }
            )
            key.startsWith("p:") -> {
                val p = key.removePrefix("p:")
                if (p in collectionsU) FilterPill(
                    label = com.shelf.launcher.data.collectionLabel(p),
                    isSelected = selected == p && special == null,
                    onClick = { onSelect(p) }
                ) else FilterPill(
                    label = when (p) {
                        com.shelf.launcher.ANDROID_GAMES_PLATFORM -> "Android games"
                        "App" -> "Other apps"
                        else -> LocalTitleOptions.current.console(p)
                    },
                    isSelected = selected == p && special == null,
                    onClick = { onSelect(p) },
                    logo = com.shelf.launcher.data.ConsoleLogos.iconFor(p)
                )
            }
            else -> {
                val filter = runCatching { SpecialFilter.valueOf(key) }.getOrNull()
                if (filter != null) FilterPill(label = filter.label, isSelected = special == filter, onClick = { onSelectSpecial(filter) })
            }
        }
    }
    if (sortLabel != null) SortChip(sortLabel, onSort, Modifier.padding(end = 24.dp))
    }
}

/**
 * Update 308: the category bar's pills in one row that scrolls smoothly: the chip you're on ([focused]) glides into view
 * (kept a pill's width clear of the ends where it can), touch drags and flings it, and the ends fade out softly where
 * more pills are past them — nothing is hidden behind "+N" or swapped a group at a time. When everything fits, the pills
 * sit at [align] as before.
 */
@Composable
internal fun GlidingChipRow(
    count: Int, focused: Int, modifier: Modifier = Modifier, spacing: androidx.compose.ui.unit.Dp = 6.dp,
    align: Alignment.Horizontal = Alignment.Start, startPadding: androidx.compose.ui.unit.Dp = 0.dp,
    endPadding: androidx.compose.ui.unit.Dp = 0.dp, chip: @Composable (Int) -> Unit
) {
    val scroll = androidx.compose.foundation.rememberScrollState()
    val lefts = remember(count) { IntArray(count) }
    val widths = remember(count) { IntArray(count) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val margin = with(density) { 48.dp.roundToPx() }
    var viewW by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var laidOut by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val first = remember { booleanArrayOf(true) }
    // Update 310: remeasuring a pill should not cancel/restart the ongoing scroll animation.
    // This repeated restart could look like a flashing category bar on dual screens.
    androidx.compose.runtime.LaunchedEffect(focused, viewW, count) {
        if (focused !in 0 until count || viewW <= 0) return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { laidOut }.first { widths[focused] > 0 }
        val l = lefts[focused]; val r = l + widths[focused]
        val cur = scroll.value
        val target = when {
            l - margin < cur -> l - margin
            r + margin > cur + viewW -> (r + margin - viewW)
            else -> cur
        }.coerceIn(0, scroll.maxValue)
        if (target == cur) return@LaunchedEffect
        if (first[0]) scroll.scrollTo(target) else scroll.animateScrollTo(target, androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        first[0] = false
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(400); first[0] = false }
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .onSizeChanged { viewW = it.width }
            // The ends fade out where pills carry on past them (drawn off-screen once, then masked).
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = 28.dp.toPx()
                if (scroll.value > 0) drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fade),
                    size = androidx.compose.ui.geometry.Size(fade, size.height), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                )
                if (scroll.value < scroll.maxValue) drawRect(
                    Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width),
                    topLeft = androidx.compose.ui.geometry.Offset(size.width - fade, 0f),
                    size = androidx.compose.ui.geometry.Size(fade, size.height), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                )
            }
    ) {
        val minW = maxWidth
        Row(
            Modifier.horizontalScroll(scroll).widthIn(min = minW).padding(start = startPadding, end = endPadding),
            horizontalArrangement = Arrangement.spacedBy(spacing, align),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until count) {
                Box(Modifier.onGloballyPositioned { c ->
                    val x = c.positionInParent().x.toInt()
                    if (i < lefts.size && (lefts[i] != x || widths[i] != c.size.width)) {
                        lefts[i] = x; widths[i] = c.size.width; laidOut++
                    }
                }) { chip(i) }
            }
        }
    }
}

/** The active sort at the end of the category bar; tap to change it. */
@Composable
internal fun SortChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .tapOnly(onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort games", tint = Color.White, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = Color.White, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** Smaller category pills (Tile-style home, where the bar shares the screen with the music player). */
internal val LocalCompactPills = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
internal fun FilterPill(label: String, isSelected: Boolean, onClick: () -> Unit, logo: String? = null) {
    com.shelf.launcher.Breadcrumbs.mark("FilterPill")
    val compact = LocalCompactPills.current
    // Tile home's size slider makes the pills grow with the tiles (1 elsewhere).
    val ps = if (compact) LocalTileScale.current.coerceIn(0.9f, 1.5f) else 1f
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isSelected) ShelfTheme.accent else Color.White.copy(alpha = 0.08f))
            .tapOnly(onClick)
            // (Update 308: a little smaller — 12 / 5 dp padding, 12 sp, a 15 dp logo; were 14 / 7, 13 sp, 17 dp.)
            .padding(horizontal = (if (compact) 10.dp else 12.dp) * ps, vertical = (if (compact) 4.dp else 5.dp) * ps)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (logo != null) {
                coil.compose.AsyncImage(logo, null, Modifier.size((if (compact) 14.dp else 15.dp) * ps).clip(RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(5.dp))
            }
            Text(
                label,
                color = if (isSelected) onAccentColor() else Color.White,
                fontSize = (if (compact) 11.sp else 12.sp) * ps,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun ControlBar(
    onMenuClick: () -> Unit,
    onFavorite: () -> Unit,
    onShortcuts: () -> Unit,
    onNotifications: () -> Unit,
    onAchievements: () -> Unit,
    onOptions: () -> Unit,
    onSelect: () -> Unit,
    onBack: () -> Unit,
    onHelp: () -> Unit = {},
    onNextCategory: () -> Unit = {},
    onPrevCategory: () -> Unit = {},
    onQuickJump: () -> Unit = {},
    /** The button set to "Play the last game" (Settings › Controls), if any. */
    continueButton: com.shelf.launcher.data.MusicButton? = null,
    onContinue: () -> Unit = {}
) {
    com.shelf.launcher.Breadcrumbs.mark("ControlBar")
    Row(
        Modifier
            .fillMaxWidth()
            // Slim and see-through: the art carries on behind the legend
            // instead of ending in a solid black band.
            .height(24.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f))))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Only the shortcuts you can't guess: Select (menu) and L2 on the left, R2 and Start (options)
        // on the right, mirrored like the buttons on the handheld. A small "R3 · all controls" in the
        // middle opens the full list (A / B / X / Y and the rest live there).
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Full words only where both sides and the middle fit side by side (they used to run into the
            // middle entry on screens around 520–700 dp wide).
            val labels = if (maxWidth < 720.dp) HintLabels.SHORT else HintLabels.FULL
            LegendThree(
                left = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HintEntry(HintSpec("SELECT", null, "MENU", "MENU", drop = 0, onClick = onMenuClick), labels)
                        Spacer(Modifier.width(8.dp))
                        HintEntry(HintSpec("L2", null, "PREVIOUS CATEGORY", "PREV", drop = 0, onClick = onPrevCategory), labels)
                    }
                },
                center = {
                    Box(Modifier.graphicsLayer { alpha = 0.75f }) {
                        HintEntry(HintSpec("R3", null, "ALL CONTROLS", "HELP", drop = 0, onClick = onHelp), labels)
                    }
                },
                right = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HintEntry(HintSpec("R2", null, "NEXT CATEGORY", "NEXT", drop = 0, onClick = onNextCategory), labels)
                        Spacer(Modifier.width(8.dp))
                        HintEntry(HintSpec("START", null, "OPTIONS", "OPTS", drop = 0, onClick = onOptions), labels)
                    }
                }
            )
        }
    }
}

/**
 * Three legend groups on one line: [left] at the start, [right] at the end and [center] in the middle —
 * moved to the free gap between them when the true middle would touch one, and left out when even that
 * gap is too small. Nothing overlaps.
 */
@Composable
private fun LegendThree(left: @Composable () -> Unit, center: @Composable () -> Unit, right: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(contents = listOf(left, center, right), modifier = Modifier.fillMaxWidth()) { (l, c, r), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val lp = l.firstOrNull()?.measure(loose)
        val rp = r.firstOrNull()?.measure(loose)
        val cp = c.firstOrNull()?.measure(loose)
        val w = constraints.maxWidth
        val h = maxOf(lp?.height ?: 0, rp?.height ?: 0, cp?.height ?: 0)
        val gap = 12.dp.roundToPx()
        val lw = lp?.width ?: 0
        val rw = rp?.width ?: 0
        layout(w, h) {
            lp?.placeRelative(0, (h - lp.height) / 2)
            // Not enough room for both sides: the start group wins, the end one is left out.
            if (rp != null && lw + gap + rw <= w) rp.placeRelative(w - rw, (h - rp.height) / 2)
            if (cp != null) {
                val free0 = lw + gap
                val free1 = w - rw - gap
                val mid = (w - cp.width) / 2
                val x = when {
                    mid >= free0 && mid + cp.width <= free1 -> mid
                    free1 - free0 >= cp.width -> free0 + (free1 - free0 - cp.width) / 2
                    else -> -1
                }
                if (x >= 0) cp.placeRelative(x, (h - cp.height) / 2)
            }
        }
    }
}

/** Every control on one card — opened with R3, closed with any button. */
@Composable
internal fun ControlsHelpCard(
    hotkeys: List<com.shelf.launcher.data.Hotkey> = emptyList(),
    musicButtons: Map<com.shelf.launcher.data.MusicAction, com.shelf.launcher.data.MusicButton> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        "◀ ▶" to "Move through games",
        "A" to "Open a game's details",
        "B" to "Every category",
        "X" to "Favorite",
        "Y" to "Achievements",
        "START" to "Game options",
        "SELECT" to "Menu & settings",
        "L1 / R1" to "Shortcuts / quick menu (sort, sync, notifications)",
        "L2 / R2" to "Previous / next category",
        "L3" to "Jump by letter",
        "Right stick" to "Letters (home) · scroll (details)",
        "▲" to "Into the music player"
    ) +
        // Your own hotkeys and music buttons (Settings › Controls).
        hotkeys.filter { it.button != com.shelf.launcher.data.MusicButton.NONE && it.action != com.shelf.launcher.data.HotkeyAction.OFF }
            .map { hk ->
                hk.button.label to (
                    if (hk.action == com.shelf.launcher.data.HotkeyAction.OPEN_APP) "Open ${hk.appLabel ?: "app"}"
                    else hk.action.label
                    )
            } +
        musicButtons.filterValues { it != com.shelf.launcher.data.MusicButton.NONE }
            .map { (action, button) -> button.label to "Music: ${action.label.lowercase()}" }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.widthIn(max = 920.dp).fillMaxWidth()) {
    // Three columns (two on a narrow screen, one on a tiny one), filled top to bottom, then left to right:
    // three on a small second screen left each description a few words wide and cut off.
    val columns = when {
        maxWidth < 380.dp -> 1
        maxWidth < 600.dp -> 2
        else -> 3
    }
    val perColumn = (rows.size + columns - 1) / columns
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ShelfTheme.colors.surface.copy(alpha = 0.96f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Controls", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("Any button closes this", color = ShelfTheme.textMuted.copy(alpha = 0.7f), fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            rows.chunked(perColumn.coerceAtLeast(1)).forEach { column ->
                Column(Modifier.weight(1f)) {
                    column.forEach { (button, what) ->
                        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.widthIn(min = 64.dp).padding(end = 8.dp)) { GlyphPill(button) }
                            Text(
                                what,
                                color = ShelfTheme.textMuted,
                                fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp),
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            // Keeps columns the same width when there are fewer than three.
            repeat(columns - rows.chunked(perColumn.coerceAtLeast(1)).size) { Spacer(Modifier.weight(1f)) }
        }
    }
    }
}

/** How much text a legend entry shows. */
internal enum class HintLabels { FULL, SHORT, NONE }

internal class HintSpec(
    val symbol: String,
    val second: String?,
    val label: String,
    val short: String,
    val drop: Int,
    val onClick: (() -> Unit)? = null
)

/**
 * Lays the legend out in one row. Every entry starts with its full label;
 * while the row is too wide, labels are shortened and then dropped one entry
 * at a time — least useful first — so most entries keep their words and
 * nothing is ever cut off at the edge.
 */
@Composable
internal fun FittingHints(hints: List<HintSpec>) {
    androidx.compose.ui.layout.SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val gap = 10.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, maxWidth = androidx.compose.ui.unit.Constraints.Infinity)
        fun measure(i: Int, mode: HintLabels) =
            subcompose("$i-$mode") { HintEntry(hints[i], mode) }.first().measure(loose)

        val modes = MutableList(hints.size) { HintLabels.FULL }
        val placeables = MutableList(hints.size) { measure(it, HintLabels.FULL) }
        fun total() = placeables.sumOf { it.width } + gap * 2 * (hints.size - 1).coerceAtLeast(0)
        val byDrop = hints.indices.sortedBy { hints[it].drop }
        for (step in listOf(HintLabels.SHORT, HintLabels.NONE)) {
            for (i in byDrop) {
                if (total() <= constraints.maxWidth) break
                if (modes[i].ordinal < step.ordinal) {
                    modes[i] = step
                    placeables[i] = measure(i, step)
                }
            }
        }
        // Packed together on the left, not spread across the whole screen.
        val between = gap * 2
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            var x = 0
            placeables.forEach { p ->
                p.placeRelative(x, (height - p.height) / 2)
                x += p.width + between
            }
        }
    }
}

/** One legend entry: one or two button glyphs and what they do. */
@Composable
internal fun HintEntry(hint: HintSpec, labels: HintLabels) {
    com.shelf.launcher.Breadcrumbs.mark("HintGlyph")
    val onClick = hint.onClick
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = (if (onClick != null) Modifier.clip(RoundedCornerShape(50)).tapOnly(onClick) else Modifier)
            .padding(4.dp)
    ) {
        GlyphPill(hint.symbol)
        if (hint.second != null) {
            Spacer(Modifier.width(4.dp))
            GlyphPill(hint.second)
        }
        val text = when (labels) {
            HintLabels.FULL -> hint.label
            HintLabels.SHORT -> hint.short
            HintLabels.NONE -> ""
        }
        if (text.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                color = ShelfTheme.textMuted,
                fontSize = 10.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(10.sp), maxFontSize = 10.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                softWrap = false
            )
        }
    }
}

/**
 * Which face-button names the legend uses (Appearance › Controls › Button style). Set by the home screen
 * from the saved setting; plain Compose state, so every glyph on screen follows a change at once.
 */
internal object ButtonGlyphStyle {
    /** What is printed on the buttons (Settings › Controls › Button legend). */
    var current by androidx.compose.runtime.mutableStateOf(com.shelf.launcher.data.ButtonGlyphs.XBOX)
    /** How the system has the buttons set up: followed automatically, or forced Xbox / Nintendo. */
    var layout by androidx.compose.runtime.mutableStateOf(com.shelf.launcher.data.ButtonLayoutMode.AUTO)

    private val face = Regex("""(?<![A-Za-z0-9])[ABXY](?![A-Za-z0-9])""")

    /** Physical spots of the face buttons. */
    enum class Spot { SOUTH, EAST, WEST, NORTH }

    /** The system's A/B and X/Y swaps right now (Nintendo layout swaps both). Reads Compose state. */
    private fun swaps(): Pair<Boolean, Boolean> = when (layout) {
        com.shelf.launcher.data.ButtonLayoutMode.XBOX -> false to false
        com.shelf.launcher.data.ButtonLayoutMode.NINTENDO -> true to true
        com.shelf.launcher.data.ButtonLayoutMode.AUTO -> {
            val ab = com.shelf.launcher.data.SystemButtonLayout.abSwapped ?: false
            ab to (com.shelf.launcher.data.SystemButtonLayout.xySwapped ?: ab)
        }
    }

    /** Where the button Android calls [key] ("A", "B", "X", "Y") physically is, with the system's layout. */
    fun spotOf(key: String): Spot? {
        val (ab, xy) = swaps()
        return when (key) {
            "A" -> if (ab) Spot.EAST else Spot.SOUTH
            "B" -> if (ab) Spot.SOUTH else Spot.EAST
            "X" -> if (xy) Spot.NORTH else Spot.WEST
            "Y" -> if (xy) Spot.WEST else Spot.NORTH
            else -> null
        }
    }

    /** What is printed at [spot] on this controller. */
    fun printedAt(spot: Spot, style: com.shelf.launcher.data.ButtonGlyphs = current): String = when (style) {
        com.shelf.launcher.data.ButtonGlyphs.XBOX -> when (spot) { Spot.SOUTH -> "A"; Spot.EAST -> "B"; Spot.WEST -> "X"; Spot.NORTH -> "Y" }
        com.shelf.launcher.data.ButtonGlyphs.NINTENDO -> when (spot) { Spot.SOUTH -> "B"; Spot.EAST -> "A"; Spot.WEST -> "Y"; Spot.NORTH -> "X" }
        // PlayStation shapes sit where the Xbox letters do: ✕ bottom, ○ right, □ left, △ top.
        com.shelf.launcher.data.ButtonGlyphs.PLAYSTATION -> when (spot) { Spot.SOUTH -> "✕"; Spot.EAST -> "○"; Spot.WEST -> "□"; Spot.NORTH -> "△" }
    }

    /**
     * "A" (Android's name, what the code listens for) as the label printed on the button that sends it:
     * the system layout says which physical button that is, the printed style says what it's called.
     */
    fun relabel(symbol: String, style: com.shelf.launcher.data.ButtonGlyphs = current): String =
        face.replace(symbol) { m -> spotOf(m.value)?.let { printedAt(it, style) } ?: m.value }
}

/** The PlayStation shape for a face button (by its Android name), or null. */
private enum class PsShape(val color: Color) {
    CROSS(Color(0xFF7FA8FF)), CIRCLE(Color(0xFFFF6B6B)), SQUARE(Color(0xFFFF8AD8)), TRIANGLE(Color(0xFF4FD6A6))
}

/**
 * Round for one-letter buttons, a small pill for L1 / SELECT etc. — never wraps, and the letter or shape
 * always sits dead centre in its circle: sizes are in dp (not sp, so a big system font size can't push
 * the letter out of the circle) and the font's extra top/bottom padding is trimmed.
 */
@Composable
internal fun GlyphPill(symbol: String) {
    val style = ButtonGlyphStyle.current
    val ps = if (style == com.shelf.launcher.data.ButtonGlyphs.PLAYSTATION) when (ButtonGlyphStyle.spotOf(symbol)) {
        ButtonGlyphStyle.Spot.SOUTH -> PsShape.CROSS; ButtonGlyphStyle.Spot.EAST -> PsShape.CIRCLE
        ButtonGlyphStyle.Spot.WEST -> PsShape.SQUARE; ButtonGlyphStyle.Spot.NORTH -> PsShape.TRIANGLE
        null -> null
    } else null
    if (ps != null) {
        androidx.compose.foundation.Canvas(
            Modifier
                .size(18.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            val c = center
            val r = size.minDimension * 0.24f
            val w = size.minDimension * 0.09f
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
            when (ps) {
                PsShape.CROSS -> {
                    drawLine(ps.color, androidx.compose.ui.geometry.Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Offset(c.x + r, c.y + r), w, androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(ps.color, androidx.compose.ui.geometry.Offset(c.x + r, c.y - r), androidx.compose.ui.geometry.Offset(c.x - r, c.y + r), w, androidx.compose.ui.graphics.StrokeCap.Round)
                }
                PsShape.CIRCLE -> drawCircle(ps.color, r * 1.05f, c, style = stroke)
                PsShape.SQUARE -> drawRect(ps.color, androidx.compose.ui.geometry.Offset(c.x - r * 0.95f, c.y - r * 0.95f), androidx.compose.ui.geometry.Size(r * 1.9f, r * 1.9f), style = stroke)
                PsShape.TRIANGLE -> {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(c.x, c.y - r * 1.1f)
                        lineTo(c.x + r * 1.15f, c.y + r * 0.85f)
                        lineTo(c.x - r * 1.15f, c.y + r * 0.85f)
                        close()
                    }
                    drawPath(path, ps.color, style = stroke)
                }
            }
        }
        return
    }
    val text = ButtonGlyphStyle.relabel(symbol, style)
    val single = text.length == 1
    // Xbox letters keep their familiar colours; the other layouts stay white.
    val tint = if (style == com.shelf.launcher.data.ButtonGlyphs.XBOX && single) when (text) {
        "A" -> Color(0xFF7BE07B); "B" -> Color(0xFFFF7B7B); "X" -> Color(0xFF7FB4FF); "Y" -> Color(0xFFFFD866); else -> Color.White
    } else Color.White
    val density = androidx.compose.ui.platform.LocalDensity.current
    val sizeDp = when {
        single -> 10.5f
        text.length > 2 -> 8f
        else -> 9f
    }
    val fontSize = with(density) { sizeDp.dp.toSp() }
    Box(
        Modifier
            .height(18.dp)
            .then(if (single) Modifier.size(18.dp) else Modifier.widthIn(min = 18.dp))
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = if (single) 0.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = tint,
            fontSize = fontSize, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(fontSize), maxFontSize = fontSize),
            lineHeight = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            softWrap = false,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = uiFontStyle().merge(androidx.compose.ui.text.TextStyle(
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                )
            ))
        )
    }
}

/**
 * The focused game's art behind everything.
 *
 * Classic: a plain crossfade. Quick (the default, Appearance › Background art
 * motion): the old picture stays until the new one has actually loaded, then
 * the new one fades in over it in a fraction of a second — no blank flash, no
 * pop-in. With the zoom on, the picture then drifts slowly closer while you
 * rest on the game, 5–25 % over about twenty seconds, easing out and stopping
 * there. Only the draw layer changes while it zooms (no recomposition), and it
 * pauses while a game is running or the screen is off.
 * [blurred] softens it (Blur needs Android 12+; older devices show it sharp).
 */
@Composable
internal fun BackdropArt(
    focusedGame: com.shelf.launcher.data.Game?,
    state: com.shelf.launcher.LauncherUiState,
    blurred: Boolean,
    /** Top of the row of games, in pixels — where "Whole picture" ends. */
    rowTopPx: Float = 0f,
    rowFilled: Boolean = true,
    /** Update 329: bottom of the row of games, in pixels — where "Whole picture" starts with the row at the top. */
    rowBottomPx: Float = 0f
) {
    com.shelf.launcher.Breadcrumbs.mark("BackdropArt")
    // The game's own transition (Game settings › Look for this game) wins over Background art motion.
    // (Update 119: and a layout's own choice — Layout options › Animations in this layout — over the general one.)
    MotionPrefs.observe()
    val motion = com.shelf.launcher.data.GameLooks.transition(focusedGame?.stableKey, MotionPrefs.hero(state.appearance.heroMotion, state.appearance.homeLayout))
    Box(Modifier.fillMaxSize()) {
        // Update 104: the classic crossfade goes through the held layers too. A plain crossfade faded the old picture
        // out before the new one had loaded (a quick black screen), and dimmed both half way through.
        run {
            HeroLayers(
                focusedGame, state, blurred, rowTopPx, rowFilled, rowBottomPx = rowBottomPx,
                zoomOn = motion == com.shelf.launcher.data.HeroMotion.QUICK_ZOOM || motion == com.shelf.launcher.data.HeroMotion.KEN_BURNS,
                motion = motion
            )
        }
        // Scrim so the top status bar and bottom dock stay legible (once, over every layer).
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        // Lighter than it used to be, so the art reaches
                        // all the way down instead of fading out at the row.
                        // (Update 317aq, Toni: a white haze at the top and bottom with a light theme — it took the theme's
                        // background colour. Always a dark shade now: the status bar's text is white.)
                        0.0f to Color.Black.copy(alpha = 0.45f),
                        0.18f to Color.Transparent,
                        0.6f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.45f)
                    )
                )
        )
    }
}

/** One background picture in the quick-fade stack. */
private class HeroLayer(val id: Long, val game: com.shelf.launcher.data.Game?) {
    val alpha = androidx.compose.animation.core.Animatable(0f)
    val zoom = androidx.compose.animation.core.Animatable(1f)
    var ready by mutableStateOf(false)
    /** Fully faded in. */
    var shown by mutableStateOf(false)
}

/** How long the new picture takes to fade in once it has loaded. */
private const val HeroFadeMillis = 170
/** A picture that is still loading after this long fades in anyway (its placeholder). */
private const val HeroReadyTimeoutMillis = 1200L
/** The slow zoom: long, easing out, then it stays put. */
private const val HeroZoomMillis = 22_000

@Composable
private fun HeroLayers(
    focusedGame: com.shelf.launcher.data.Game?,
    state: com.shelf.launcher.LauncherUiState,
    blurred: Boolean,
    rowTopPx: Float,
    rowFilled: Boolean,
    zoomOn: Boolean,
    motion: com.shelf.launcher.data.HeroMotion = com.shelf.launcher.data.HeroMotion.QUICK,
    rowBottomPx: Float = 0f
) {
    val layers = remember { mutableStateListOf<HeroLayer>() }
    // How long the new picture takes to come in, by style (the moving ones need a little longer).
    // Transition speed (Fast / Normal / Cinematic) scales every style.
    val speedK = com.shelf.launcher.data.Features.transitionSpeed.state.value.ms / 240f
    val enterMillis = (when (motion) {
        com.shelf.launcher.data.HeroMotion.SLIDE -> 320
        com.shelf.launcher.data.HeroMotion.PARALLAX -> 420
        com.shelf.launcher.data.HeroMotion.ZOOM_THROUGH -> 340
        com.shelf.launcher.data.HeroMotion.IRIS -> 460
        com.shelf.launcher.data.HeroMotion.COVER_FLOW -> 480
        com.shelf.launcher.data.HeroMotion.PIXEL -> 420
        com.shelf.launcher.data.HeroMotion.CRT -> 440
        com.shelf.launcher.data.HeroMotion.MEMORY_CARD -> 400
        else -> HeroFadeMillis
    } * speedK).toInt().coerceAtLeast(60)
    val nextId = remember { longArrayOf(0L) }
    val animationsOn = rememberAnimationsEnabled()
    val active = rememberIsActive()
    val targetKey = focusedGame?.stableKey
    // Back from the screen being off / a game: anything half-done is dropped and the game you're on gets a
    // fresh picture (layers frozen mid-fade, or loads cut off while the screen was off, used to leave an
    // older game's picture up).
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val top = layers.lastOrNull()
        if (top != null && top.shown && top.game?.stableKey == targetKey) {
            layers.removeAll { it !== top }
            return@LaunchedEffect
        }
        val onScreen = layers.lastOrNull { it.shown }
        layers.removeAll { it !== onScreen }
        layers.add(HeroLayer(nextId[0]++, focusedGame))
    }
    LaunchedEffect(targetKey) {
        if (layers.lastOrNull()?.let { it.game?.stableKey == targetKey } == true) return@LaunchedEffect
        // Pictures for games you flew past that never got to show are dropped
        // (their loads are cancelled with them); the one on screen stays until
        // the new one covers it. Never more than three at once.
        val onScreen = layers.lastOrNull { it.shown }
        layers.removeAll { it !== onScreen && it.alpha.value <= 0f }
        // Keep the picture on screen; drop the oldest of the others.
        while (layers.size >= 3) {
            val drop = layers.firstOrNull { it !== onScreen } ?: break
            layers.remove(drop)
        }
        layers.add(HeroLayer(nextId[0]++, focusedGame))
    }
    val zoomTarget = 1f + state.appearance.heroZoomPercent.coerceIn(5, 25) / 100f
    for (layer in layers) {
        androidx.compose.runtime.key(layer.id) {
            val isTop = layer === layers.lastOrNull()
            // The newest picture uses the latest game object (art can arrive later).
            val game = if (isTop && focusedGame?.stableKey == layer.game?.stableKey) focusedGame else layer.game
            LaunchedEffect(layer.id) {
                kotlinx.coroutines.delay(HeroReadyTimeoutMillis)
                layer.ready = true
            }
            LaunchedEffect(layer.id, layer.ready) {
                if (!layer.ready) return@LaunchedEffect
                if (animationsOn) {
                    layer.alpha.animateTo(1f, tween(enterMillis, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                } else layer.alpha.snapTo(1f)
                layer.shown = true
                // Fully covering now: everything under it can go.
                val at = layers.indexOf(layer)
                if (at > 0) repeat(at) { layers.removeAt(0) }
            }
            // Slow drift closer while you rest on this game; only the top picture,
            // only once it's fully in, and paused while a game runs / screen is off.
            LaunchedEffect(layer.id, isTop, zoomOn, animationsOn, active, zoomTarget, layer.shown) {
                if (!zoomOn || !animationsOn) { layer.zoom.snapTo(1f); return@LaunchedEffect }
                if (!isTop || !active || !layer.shown) return@LaunchedEffect
                val left = ((zoomTarget - layer.zoom.value) / (zoomTarget - 1f)).coerceIn(0f, 1f)
                layer.zoom.animateTo(
                    zoomTarget,
                    tween(
                        (HeroZoomMillis * left).toInt().coerceAtLeast(1),
                        easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.0f, 0.2f, 1f)
                    )
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = layer.alpha.value
                        // The picture coming in after this one (if any): how far it is.
                        val next = layers.getOrNull(layers.indexOf(layer) + 1)?.alpha?.value ?: 0f
                        when (motion) {
                            com.shelf.launcher.data.HeroMotion.SLIDE -> {
                                alpha = p
                                translationX = (1f - p) * size.width * 0.18f
                            }
                            com.shelf.launcher.data.HeroMotion.PARALLAX -> {
                                alpha = if (p > 0f) 1f else 0f
                                translationX = (1f - p) * size.width - next * size.width * 0.35f
                            }
                            com.shelf.launcher.data.HeroMotion.ZOOM_THROUGH -> {
                                alpha = p
                                val sc = (1.14f - 0.14f * p) * (1f + 0.1f * next)
                                scaleX = sc; scaleY = sc
                            }
                            com.shelf.launcher.data.HeroMotion.IRIS -> alpha = if (p > 0f) 1f else 0f
                            com.shelf.launcher.data.HeroMotion.COVER_FLOW -> {
                                // New one swings in from the right; the old one turns away to the left.
                                alpha = if (p > 0f) 1f else 0f
                                cameraDistance = 9f * density
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (next > 0f) 0f else 1f, 0.5f)
                                rotationY = (1f - p) * -58f + next * 50f
                                flatTextFor3d()
                                translationX = (1f - p) * size.width * 0.42f - next * size.width * 0.38f
                                val sc = 1f - 0.16f * (1f - p) - 0.12f * next
                                scaleX = sc; scaleY = sc
                            }
                            com.shelf.launcher.data.HeroMotion.PIXEL -> alpha = if (p > 0f) 1f else 0f
                            com.shelf.launcher.data.HeroMotion.CRT -> {
                                // A bright line opens out sideways, then up and down into the picture;
                                // the old picture collapses to a line under it.
                                alpha = if (p > 0f) 1f else 0f
                                val open = (p / 0.35f).coerceIn(0f, 1f)
                                val tall = ((p - 0.35f) / 0.65f).coerceIn(0f, 1f)
                                scaleX = (0.04f + 0.96f * open) * (1f - 0.02f * next)
                                scaleY = (0.008f + 0.992f * tall * tall) * (1f - 0.97f * next)
                            }
                            com.shelf.launcher.data.HeroMotion.MEMORY_CARD -> {
                                alpha = p
                                translationY = (1f - p) * size.height * 0.22f - next * size.height * 0.1f
                                val sc = 0.94f + 0.06f * p
                                scaleX = sc; scaleY = sc
                            }
                            com.shelf.launcher.data.HeroMotion.KEN_BURNS -> {
                                alpha = p
                                // A slow pan with the zoom, left or right by turns.
                                val pan = ((layer.zoom.value - 1f) / (zoomTarget - 1f).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                                translationX = size.width * 0.035f * pan * (if (layer.id % 2L == 0L) 1f else -1f)
                                translationY = -size.height * 0.015f * pan
                            }
                            else -> alpha = p
                        }
                    }
                    .then(
                        if (motion == com.shelf.launcher.data.HeroMotion.PIXEL) Modifier.drawWithContent {
                            val p = layer.alpha.value
                            if (p >= 1f) { drawContent(); return@drawWithContent }
                            if (p <= 0f) return@drawWithContent
                            // Blocks of ~1/24 of the width appear in a fixed scattered order.
                            val cell = (size.width / 24f).coerceAtLeast(8f)
                            val cols = kotlin.math.ceil(size.width / cell).toInt()
                            val rows = kotlin.math.ceil(size.height / cell).toInt()
                            val blocks = androidx.compose.ui.graphics.Path()
                            for (j in 0 until rows) for (i in 0 until cols) {
                                val h = ((i * 73856093) xor (j * 19349663) xor (layer.id.toInt() * 83492791)) and 0x3FF
                                if (h / 1023f < p) blocks.addRect(androidx.compose.ui.geometry.Rect(i * cell, j * cell, (i + 1) * cell, (j + 1) * cell))
                            }
                            clipPath(blocks) { this@drawWithContent.drawContent() }
                        } else if (motion == com.shelf.launcher.data.HeroMotion.CRT) Modifier.drawWithContent {
                            drawContent()
                            // The flash of the tube warming up, fading as the picture opens.
                            val p = layer.alpha.value
                            if (p in 0.001f..0.999f) drawRect(Color.White, alpha = (1f - p) * 0.85f)
                        } else if (motion == com.shelf.launcher.data.HeroMotion.IRIS) Modifier.drawWithContent {
                            val p = layer.alpha.value
                            if (p >= 1f) { drawContent(); return@drawWithContent }
                            val r = kotlin.math.hypot(size.width, size.height) / 2f * p
                            val circle = androidx.compose.ui.graphics.Path().apply {
                                addOval(androidx.compose.ui.geometry.Rect(center, r))
                            }
                            clipPath(circle) { this@drawWithContent.drawContent() }
                        } else Modifier
                    )
            ) {
                BackdropArtLayer(
                    game, state, blurred, rowTopPx, rowFilled, rowBottomPx = rowBottomPx,
                    zoom = if (zoomOn) ({ layer.zoom.value }) else null,
                    onReady = { layer.ready = true }
                )
            }
        }
    }
}

/**
 * The picture itself: the hero (filling the screen, or the whole picture over a
 * soft copy of itself), an app's own icon, or a colour wash. [onReady] fires
 * once there is something to show (loaded, failed, or nothing to load).
 * [zoom] scales the picture from the centre (read in the draw phase only).
 */
@Composable
private fun BackdropArtLayer(
    game: com.shelf.launcher.data.Game?,
    state: com.shelf.launcher.LauncherUiState,
    blurred: Boolean,
    rowTopPx: Float,
    rowFilled: Boolean,
    zoom: (() -> Float)?,
    onReady: () -> Unit,
    rowBottomPx: Float = 0f
) {
    // Artwork priority (Appearance › Artwork, or this game's own): see ArtPick.
    val heroUrl = com.shelf.launcher.data.ArtPick.backdrop(game, state.artOverrides, state.art, state.screenshots)
    val animKey = com.shelf.launcher.data.GameLooks.of(game?.stableKey).animateArt
    val readyNow by rememberUpdatedState(onReady)
    val zoomModifier = if (zoom != null) Modifier.graphicsLayer {
        val z = zoom()
        scaleX = z
        scaleY = z
    } else Modifier
    Box(Modifier.fillMaxSize()) {
        if (heroUrl != null) {
            val heroContext = androidx.compose.ui.platform.LocalContext.current
            // Per game (Artwork & details) wins over the global switch:
            // 1 = off, 2/4/6/8 = that factor, none = automatic.
            val perGame = game?.let { state.heroUpscale[it.stableKey] }
            // Lite effects: no automatic upscaling (a per-game choice still applies), and blur
            // comes from decoding the picture tiny and letting it stretch — no blur pass at all.
            val lite = LocalLiteEffects.current
            val sharpen = if (perGame != null) perGame > 1 else state.appearance.sharpHeroes && !lite
            // "Whole picture" only makes sense with the row at the bottom;
            // with the games at the top or middle the art fills the screen.
            // Only the capsule row (Voltcap row) cuts the picture short above itself; the tile layouts and the rest
            // keep the hero full screen even with their row at the bottom.
            // Update 125: the fit is per layout (its options); "Whole picture" there on any layout, the Voltcap row only with
            // its row at the bottom as before.
            com.shelf.launcher.data.Features.heroFitByLayout.state.value
            com.shelf.launcher.data.Features.carouselLayouts.state.value
            val layoutNow = state.appearance.homeLayout
            com.shelf.launcher.data.Features.spineLook.state.value
            val fitNow = com.shelf.launcher.data.HeroFitPlan.of(layoutNow, state.appearance.heroFit, state.appearance.cardArtType)
            val whole = !blurred && rowFilled && fitNow == com.shelf.launcher.data.HeroFit.WHOLE &&
                (layoutNow != com.shelf.launcher.data.HomeLayout.CLASSIC || state.appearance.dockHere == com.shelf.launcher.data.DockPosition.BOTTOM)
            // Decoded at screen size (heroes are ~3840 px wide) and, when
            // blurred, at half of it — the blur hides the detail. Whole
            // picture uses ONE request for both copies (sharp + blurred
            // backdrop), so it is decoded once.
            val screen = screenSizeDp()
            val heroDensity = androidx.compose.ui.platform.LocalDensity.current.density
            // Capped (1920 px, 1280 on small handhelds): a full-screen background is the biggest
            // picture in memory, and the screen is filled just as well by a stretched one.
            val heroCap = com.shelf.launcher.device.MemoryBudget.heroMaxWidth(heroContext)
            val heroFull = (screen.width.value * heroDensity).toInt().coerceAtLeast(1)
            val heroW = minOf(heroFull, heroCap)
            val heroH = ((screen.height.value * heroDensity) * heroW / heroFull).toInt().coerceAtLeast(1)
            val heroScale = if (whole) coil.size.Scale.FIT else coil.size.Scale.FILL
            // Blurred: decoded at a quarter size and blurred smoothly in the bitmap itself (SmoothBlur),
            // then stretched with bilinear filtering — clean on every device, no blocky pixels.
            // (An eighth of the size: the blur hides the detail, and the decode + blur is four times cheaper than at a quarter.)
            val heroDiv = if (blurred) 8 else 1
            // A load that failed (Wi-Fi still waking after the screen was off — Sleep mode turns it off — or a
            // hiccup) is tried again a few times, and once more whenever the home screen comes back, instead
            // of the old picture staying up for good.
            var retry by remember(heroUrl) { androidx.compose.runtime.mutableIntStateOf(0) }
            var failed by remember(heroUrl) { mutableStateOf(false) }
            val heroActive = rememberIsActive()
            LaunchedEffect(failed, heroActive) {
                if (!failed || !heroActive) return@LaunchedEffect
                kotlinx.coroutines.delay(if (retry < 2) 1_500L else 4_000L)
                if (retry < 6) { failed = false; retry++ } else readyNow()
            }
            val heroRequest = remember(heroUrl, sharpen, perGame, heroW, heroH, heroScale, heroDiv, blurred, animKey, retry) {
                with(com.shelf.launcher.data.ArtPick) {
                    coil.request.ImageRequest.Builder(heroContext).animationFor(game?.stableKey)
                }
                    .data(heroUrl)
                    .size(heroW / heroDiv, heroH / heroDiv)
                    .scale(heroScale)
                    .precision(coil.size.Precision.INEXACT)
                    .memoryCacheKey("$heroUrl|hero|${heroW / heroDiv}x${heroH / heroDiv}|$heroScale|$sharpen|$perGame|$blurred|$animKey")
                    // Small heroes are upscaled crisply (see SharpUpscale) —
                    // Settings › Appearance › "Upscale small backgrounds".
                    .apply {
                        if (blurred) transformations(SmoothBlur(radius = (heroW / heroDiv / 45).coerceIn(3, 10)))
                        else if (sharpen) transformations(SharpUpscale(factor = perGame?.takeIf { it > 1 }))
                    }
                    .crossfade(false)
                    .build()
            }
            // The soft copy behind "Whole picture": its own tiny decode. Update 121: blurred once there
            // (SmoothBlur) instead of a full-screen blur re-run under the zoom on every frame; Lite keeps it unblurred.
            val softRequest = remember(heroUrl, heroW, heroH, lite) {
                coil.request.ImageRequest.Builder(heroContext)
                    .data(heroUrl)
                    .size((heroW / 14).coerceAtLeast(1), (heroH / 14).coerceAtLeast(1))
                    .scale(coil.size.Scale.FILL)
                    .precision(coil.size.Precision.INEXACT)
                    .memoryCacheKey("$heroUrl|soft|${heroW / 14}x${heroH / 14}|$lite")
                    // (Update 130: blurred in Lite too — unblurred, the tiny copy stretched over the screen was blocky.
                    // The blur is done once, on the small picture, so it costs nothing per frame.)
                    .transformations(SmoothBlur(radius = 3))
                    .crossfade(false)
                    .build()
            }
            val onState: (coil.compose.AsyncImagePainter.State) -> Unit = { st ->
                if (st is coil.compose.AsyncImagePainter.State.Success) readyNow()
                else if (st is coil.compose.AsyncImagePainter.State.Error) failed = true
            }
            if (whole) {
                // Heroes are ~3840x1240 (3.1:1) — far wider than the
                // screen, so Crop cut off almost half of them. Instead the
                // whole picture sits full-width at the top, and a soft,
                // darkened copy of it fills the space around it (on
                // Android 11 and older the copy is just darker, not blurred).
                // The zoom only moves the soft copy, so the sharp picture's
                // bottom edge stays lined up with the cards.
                AsyncImage(
                    model = softRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
                    modifier = Modifier.fillMaxSize().then(zoomModifier)
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Color.Black.copy(
                                alpha = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) 0.35f else 0.6f
                            )
                        )
                )
                // The sharp picture fills the space above the row exactly,
                // so its bottom edge lines up with the top of the cards.
                val density = androidx.compose.ui.platform.LocalDensity.current
                val dock = state.appearance.dockHere
                val above = if (dock == com.shelf.launcher.data.DockPosition.BOTTOM) {
                    with(density) { rowTopPx.toDp() }
                } else 0.dp
                // Update 329 (Toni, seen on the Thor: Tile home with wide capsules at the top — "the art was only up there and
                // the bottom side was blurred"): with the games at the top the whole picture sat at the top too, behind them,
                // and the free space below was only the blurred copy. It now fills the space under the row, as it fills the
                // space above it with the games at the bottom; with the games in the middle it sits in the middle.
                val below = if (dock == com.shelf.launcher.data.DockPosition.TOP && rowBottomPx > 0f) {
                    with(density) { rowBottomPx.toDp() }
                } else 0.dp
                AsyncImage(
                    model = heroRequest,
                    contentDescription = null,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                    contentScale = if (above > 0.dp || below > 0.dp) ContentScale.Crop else ContentScale.Fit,
                    alignment = if (above > 0.dp || below > 0.dp) Alignment.Center
                        else if (dock == com.shelf.launcher.data.DockPosition.MIDDLE) Alignment.Center else Alignment.TopCenter,
                    onState = onState,
                    // A few dp past the card's top edge, tucked under it, so no
                    // sliver of the blurred copy shows between art and card.
                    modifier = (when {
                        above > 0.dp -> Modifier.fillMaxWidth().height(above + 8.dp)
                        // (Tucked a few dp under the row's bottom edge the same way.)
                        below > 0.dp -> Modifier.fillMaxSize().padding(top = (below - 8.dp).coerceAtLeast(0.dp))
                        else -> Modifier.fillMaxSize()
                    }).pixelArtwork()
                )
            } else {
                AsyncImage(
                    model = heroRequest,
                    contentDescription = null,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                    // Fill the screen: Crop keeps the frame filled on any
                    // aspect ratio, cutting off the sides of wide heroes.
                    contentScale = ContentScale.Crop,
                    onState = onState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(zoomModifier)
                        .pixelArtwork()
                        // (Blur is already in the picture: see SmoothBlur.)
                )
            }
        } else {
            // Nothing to load: show right away.
            LaunchedEffect(Unit) { readyNow() }
            if (game != null && game.sourceType == com.shelf.launcher.data.SourceType.APP) {
                // No background art for this app: its own icon, blown up and
                // blurred, instead of a plain colour.
                AppIconArt(
                    game.packageName, wide = true,
                    modifier = Modifier.fillMaxSize().then(zoomModifier).then(if (LocalLiteEffects.current) Modifier else Modifier.blur(28.dp))
                )
            } else if (game != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.hsl(game.hue.toFloat(), 0.5f, 0.18f),
                                    Color.hsl(((game.hue + 40) % 360).toFloat(), 0.4f, 0.07f)
                                )
                            )
                        )
                )
            }
        }
    }
}

/** The same top/bottom shading the art uses, for backgrounds that aren't art. */
@Composable
internal fun DockScrim() {
    com.shelf.launcher.Breadcrumbs.mark("DockScrim")
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to ShelfTheme.background.copy(alpha = 0.55f),
                    0.2f to Color.Transparent,
                    0.55f to Color.Transparent,
                    0.8f to ShelfTheme.background.copy(alpha = 0.6f),
                    1.0f to ShelfTheme.background.copy(alpha = 0.8f)
                )
            )
    )
}


/** The category name, animated when it changes (L2 / R2) per [anim]. */
@Composable
internal fun CategoryNameSwap(label: String, anim: com.shelf.launcher.data.CategoryAnim, content: @Composable (String) -> Unit) {
    if (anim == com.shelf.launcher.data.CategoryAnim.NONE) { content(label); return }
    androidx.compose.animation.AnimatedContent(
        targetState = label,
        transitionSpec = {
            val inSpec = androidx.compose.animation.core.tween<Float>(260)
            when (anim) {
                com.shelf.launcher.data.CategoryAnim.SLIDE ->
                    (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(280)) { it / 2 } + androidx.compose.animation.fadeIn(inSpec)) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(220)) { -it / 3 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180)))
                com.shelf.launcher.data.CategoryAnim.DROP ->
                    (androidx.compose.animation.slideInVertically(androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 500f)) { -it } + androidx.compose.animation.fadeIn(inSpec)) togetherWith
                        (androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(180)) { it / 2 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)))
                else -> androidx.compose.animation.fadeIn(inSpec) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180))
            }.using(androidx.compose.animation.SizeTransform(clip = false))
        },
        label = "categoryName"
    ) { l -> content(l) }
}


/** The gold trophy in the status bar after a session with unlocks: a gentle shine, the count; tap opens them. */
@Composable
private fun ReturnTrophy(count: Int, onClick: () -> Unit) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "trophy")
    val glow by t.animateFloat(
        0.55f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1400), androidx.compose.animation.core.RepeatMode.Reverse
        ), label = "glow"
    )
    // The glow is read while drawing only, so the status bar doesn't recompose every frame.
    val gold = com.shelf.launcher.ui.theme.Ds.gold
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .drawBehind { drawRect(gold.copy(alpha = 0.18f * glow)) }
            .tapOnly(onClick)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.EmojiEvents, null,
            tint = gold, modifier = Modifier.size(15.dp).graphicsLayer { alpha = 0.7f + 0.3f * glow }
        )
        Text(
            "$count", color = Color.White, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold,
            style = uiFontStyle().merge(CenteredLine), modifier = Modifier.padding(start = 2.dp)
        )
    }
}
