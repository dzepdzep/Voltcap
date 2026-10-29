package com.shelf.launcher.ui

import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.foundation.gestures.detectTapGestures
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.NowPlaying
import com.shelf.launcher.data.ShelfNotification
import com.shelf.launcher.ui.theme.ShelfTheme

@Composable
fun NotificationsPanel(
    hasAccess: Boolean,
    errorMessage: String?,
    notifications: List<ShelfNotification>,
    nowPlaying: NowPlaying?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onDismissNotification: (String) -> Unit,
    onOpenNotificationApp: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Opens the Spotify library screen; null hides the chip. */
    onBrowseSpotify: (() -> Unit)? = null,
    dualScreen: Boolean = true,
    onToggleDualScreen: () -> Unit = {},
    lightsEnabled: Boolean = false,
    lightsColor: Int = 0,
    lightsBrightness: Int = 70,
    onLightsChange: (Boolean, Int, Int) -> Unit = { _, _, _ -> },
    lightsOffWithScreensaver: Boolean = true,
    lightsFlashOnUnlock: Boolean = true,
    onLightsAdvancedChange: (Boolean, Boolean) -> Unit = { _, _ -> },
    /** Background jobs to show progress for: label to (done, total). */
    backgroundTasks: List<Pair<String, Pair<Int, Int>>> = emptyList(),
    onStopTasks: (() -> Unit)? = null,
    /** Home row sort, shown as a tile in the quick settings (null hides it). */
    sortLabel: String? = null,
    onSort: (() -> Unit)? = null,
    /** The home layout's game look, shown as a tile in the quick settings (null hides it). */
    gameLook: String? = null,
    onCycleGameLook: (() -> Unit)? = null,
    /** Home / library / game tiles and "hold for settings" (R1Extras.kt); null hides them. */
    home: R1Home? = null,
    /** Google Drive card: the focused game's saves (null key hides that row). */
    syncGameKey: String? = null,
    syncGameName: String? = null,
    onOpenGameSync: (() -> Unit)? = null,
    onOpenSyncSettings: () -> Unit = {},
    /** Clears every notification in one go (falls back to one by one). */
    onDismissAll: (() -> Unit)? = null,
    /** Just the notifications (update 105: opened from a bell), without the quick tiles and background jobs. */
    notificationsOnly: Boolean = false,
    /** QA: (C3) opens the per-job Background work window (its Stop buttons); null hides the "Details" chip. */
    onShowWork: (() -> Unit)? = null,
    /** QA: (C4) opens the Drive sync status window; shown only while Drive is connected; null hides it. */
    onShowSyncStatus: (() -> Unit)? = null
) {
    com.shelf.launcher.Breadcrumbs.mark("NotificationsPanel")
    val context = LocalContext.current
    val messages = notifications.filter { it.isMessage }
    val others = notifications.filterNot { it.isMessage }

    // A compact sheet in the top-right corner (it covers the status bar, so the clock, Wi-Fi and
    // battery from there are repeated in its first line).
    QuickSheet(onDismiss = onDismiss, header = { QuickSheetHeader(onClose = onDismiss) }) {
        if (VoltUi.on) VoltageLine(Modifier.fillMaxWidth().padding(top = 4.dp))
        Spacer(Modifier.height(if (VoltUi.on) 4.dp else 8.dp))
        if (!notificationsOnly) QuickSettingsCard(
            dualScreen = dualScreen,
            onToggleDualScreen = onToggleDualScreen,
            lightsEnabled = lightsEnabled,
            lightsColor = lightsColor,
            lightsBrightness = lightsBrightness,
            onLightsChange = onLightsChange,
            lightsOffWithScreensaver = lightsOffWithScreensaver,
            lightsFlashOnUnlock = lightsFlashOnUnlock,
            onLightsAdvancedChange = onLightsAdvancedChange,
            musicPlaying = nowPlaying?.isPlaying == true,
            sortLabel = sortLabel,
            onSort = onSort,
            gameLook = gameLook,
            onCycleGameLook = onCycleGameLook,
            home = home
        )
        Spacer(Modifier.height(8.dp))
        // Drive sync moved to the home status bar (tap the cloud there).
        // QA: (C4) …and here, for the controller: "Sync status" opens the same window while Drive is connected.
        val driveSync by com.shelf.launcher.data.CloudSync.state.collectAsState()
        if (!notificationsOnly && onShowSyncStatus != null && driveSync.settings.connected) {
            Chip("Sync status", accent = false, onClick = onShowSyncStatus)
            Spacer(Modifier.height(12.dp))
        }

        val running = if (notificationsOnly) emptyList() else backgroundTasks.filter { (_, p) -> p.second > 0 }
        if (running.isNotEmpty()) {
            ShelfCard {
                SectionLabel("Running in the background")
                running.forEach { (label, progress) ->
                    val (done, total) = progress
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label,
                            color = ShelfTheme.colors.textPrimary,
                            fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("$done / $total", color = ShelfTheme.textMuted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(uiRounded(3.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((done.toFloat() / total).coerceIn(0f, 1f))
                                .height(6.dp)
                                .background(ShelfTheme.accent)
                        )
                    }
                }
                // QA: (C3) "Details" opens BackgroundWorkDialog (stop one job) with the controller, not only by tapping the status bar.
                if (onStopTasks != null || onShowWork != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onStopTasks != null) Chip("Stop all", accent = false, onClick = onStopTasks)
                        if (onShowWork != null) Chip("Details", accent = false, onClick = onShowWork)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        errorMessage?.let {
            ShelfCard {
                Text("Couldn't read notifications", color = Color(0xFFB5583C), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                MutedNote(it)
            }
            Spacer(Modifier.height(12.dp))
        }

        if (!hasAccess && !com.shelf.launcher.data.ShelfNotificationListener.isBuiltIn(context)) {
            ShelfCard {
                SectionLabel("Not part of this Voltcap build")
                MutedNote(
                    "To keep Voltcap installable on phones and handhelds where Google Play Protect blocks apps " +
                        "that can read notifications, the standard Voltcap does not include notification access. " +
                        "Showing notifications and controlling other apps' playback needs the optional " +
                        "\"notifications\" build (Voltcap-<version>-notify.apk) from the GitHub releases page; " +
                        "it installs over this one and keeps your games and settings."
                )
            }
        } else if (!hasAccess) {
            ShelfCard {
                SectionLabel("Notification access needed")
                MutedNote(
                    "Voltcap needs notification access to show notifications and control playback. " +
                        "Android only allows this from its own Settings screen."
                )
                Spacer(Modifier.height(12.dp))
                Chip("Open notification access settings", onClick = { openNotificationAccess(context) })
                Spacer(Modifier.height(10.dp))
                MutedNote(
                    "It's not under Apps › Voltcap › Permissions (that screen always says \"no permissions " +
                        "required\"). If the button doesn't land on it, look for Settings › Notifications › " +
                        "Notification access, or Settings › Apps › Special app access › Notification access."
                )
            }
        } else {
            // --- Music: one compact line ---
            if (nowPlaying == null) {
                // "Play Spotify" is a tile under ☰ More now; browsing stays here.
                onBrowseSpotify?.let { ChipFlow { Chip("Browse Spotify", accent = false, onClick = it) } }
            } else {
                CompactNowPlaying(nowPlaying, onPlayPause, onNext, onPrevious, onOpenApp = { MusicApps.open(context, nowPlaying.packageName) })
            }

            // Messages first, then everything else — each grouped under its app,
            // newest app first.
            val groups = (messages + others).groupBy { it.packageName }.values
                .sortedWith(compareByDescending<List<ShelfNotification>> { group -> group.any { it.isMessage } }
                    .thenByDescending { group -> group.maxOf { it.postedAt } })

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (notifications.isEmpty()) "No notifications" else "${notifications.size} notification${if (notifications.size == 1) "" else "s"}",
                    color = ShelfTheme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (notifications.isNotEmpty()) {
                    Chip("Clear all", accent = false, onClick = { if (onDismissAll != null) onDismissAll() else notifications.forEach { onDismissNotification(it.key) } })
                }
            }
            groups.forEach { group ->
                Spacer(Modifier.height(5.dp))
                NotificationGroup(group, onDismissNotification, onOpenNotificationApp)
            }
        }
    }
}

/**
 * Sends the person to the notification-access toggle. API 30+ can deep-link
 * to this app's own entry; falls back to the list screen, then app details —
 * silently failing here strands them on the wrong screen.
 */
private fun openNotificationAccess(context: android.content.Context) {
    val component = android.content.ComponentName(
        context,
        com.shelf.launcher.data.ShelfNotificationListener::class.java
    ).flattenToString()

    val attempts = buildList {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            add(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component)
            )
        }
        add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        add(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:" + context.packageName))
        )
    }

    for (intent in attempts) {
        val ok = runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (ok) return
    }
}

/** One app's notifications: its icon and name once, then each notification. */
@Composable
private fun NotificationGroup(
    group: List<ShelfNotification>,
    onDismiss: (String) -> Unit,
    onOpen: (String) -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("NotificationGroup")
    val first = group.first()
    val icon = rememberAppIcon(first.packageName)
    Column(
        Modifier
            .fillMaxWidth()
            // Volt: glass with a coloured left edge saying what kind of news it is.
            .voltSurface(uiRounded(12.dp), edge = if (VoltUi.on) notificationEdge(group) else null)
            .padding(vertical = 5.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                androidx.compose.foundation.Image(
                    icon, null,
                    modifier = Modifier
                        .size(16.dp)
                        .clip(uiRounded(6.dp))
                )
            } else {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(uiRounded(6.dp))
                        .background(ShelfTheme.accent.copy(alpha = 0.3f))
                )
            }
            Spacer(Modifier.width(7.dp))
            Text(
                first.appLabel,
                color = ShelfTheme.colors.textPrimary,
                fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (group.any { it.isMessage }) {
                Text("MESSAGE", color = ShelfTheme.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
        group.sortedByDescending { it.postedAt }.forEach { notification ->
            // (QA pass 3, D26: the ✕ sits inside the notification's own button, so ▶ never reached it — focus search
            // looks beside the row, not inside it. ▶ / ◀ now step between the notification and its ✕.)
            val rowFocus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
            val dismissFocus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
            var onDismissBtn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(uiRounded(12.dp))
                    .onPreviewKeyEvent { e ->
                        if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) false
                        else when (e.key) {
                            androidx.compose.ui.input.key.Key.DirectionRight -> if (!onDismissBtn) runCatching { dismissFocus.requestFocus() }.isSuccess else true
                            androidx.compose.ui.input.key.Key.DirectionLeft -> if (onDismissBtn) runCatching { rowFocus.requestFocus() }.isSuccess else false
                            else -> false
                        }
                    }
                    .focusRequester(rowFocus)
                    .shelfClickable { onOpen(notification.packageName) }
                    .padding(start = 25.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            notification.title ?: first.appLabel,
                            color = ShelfTheme.colors.textPrimary,
                            fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(timeAgo(notification.postedAt), color = ShelfTheme.textMuted, fontSize = 10.sp)
                    }
                    notification.text?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            color = ShelfTheme.textMuted,
                            fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp),
                            lineHeight = androidx.compose.ui.unit.TextUnit(1.273f, androidx.compose.ui.unit.TextUnitType.Em),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .onFocusChanged { onDismissBtn = it.isFocused }
                        .focusRequester(dismissFocus)
                        .shelfClickable(CircleShape) { onDismiss(notification.key) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, "Dismiss", tint = ShelfTheme.textMuted, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

/**
 * The left edge of a notification card: the accent for messages, green for things that finished (downloads,
 * installs, syncs), red for errors and warnings, a quiet grey for the rest.
 */
@Composable
private fun notificationEdge(group: List<ShelfNotification>): Color {
    val text = group.joinToString(" ") { (it.title ?: "") + " " + (it.text ?: "") }.lowercase()
    return when {
        Regex("fail|error|couldn.?t|low battery|overheat|warning").containsMatchIn(text) -> com.shelf.launcher.ui.theme.Ds.danger
        group.any { it.isMessage } -> ShelfTheme.accent
        Regex("complete|done|installed|downloaded|synced|ready|finished|unlocked").containsMatchIn(text) -> com.shelf.launcher.ui.theme.Ds.success
        else -> Color.White.copy(alpha = 0.32f)
    }
}

private fun timeAgo(postedAt: Long): String {
    val minutes = (System.currentTimeMillis() - postedAt) / 60_000
    return when {
        minutes < 1 -> "Now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        else -> "${minutes / (60 * 24)}d"
    }
}

/** The R1 sheet's frame: top-right, compact, scrolls when long; tapping outside or B closes it. */
@Composable
internal fun QuickSheet(
    onDismiss: () -> Unit,
    /** 0.6.15 (#120 Q46): stays at the top while the rest scrolls (the time, battery and ✕). */
    header: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val sounds = LocalUiSounds.current
    androidx.compose.runtime.LaunchedEffect(Unit) { sounds?.play(UiSound.OPEN) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { sounds?.play(UiSound.BACK); onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ImmersiveDialogWindow()
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .fillMaxSize()
                // L1 / R1 flip the quick-settings pages (R1Pages), wherever the focus is in the sheet.
                .onPreviewKeyEvent { e ->
                    if (!R1Pages.active || e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        androidx.compose.ui.input.key.Key.ButtonL1 -> { R1Pages.flip(-1); true }
                        androidx.compose.ui.input.key.Key.ButtonR1 -> { R1Pages.flip(1); true }
                        else -> false
                    }
                }
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.TopEnd
        ) {
            val sheetWidth = (maxWidth * 0.42f).coerceIn(300.dp, 380.dp).coerceAtMost(maxWidth - 16.dp)
            Column(
                Modifier
                    .padding(top = 6.dp, end = 6.dp)
                    .width(sheetWidth)
                    .heightIn(max = maxHeight - 12.dp)
                    .voltSurface(uiRounded(18.dp), base = ShelfTheme.colors.surface)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), uiRounded(18.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(top = if (header != null) 12.dp else 0.dp)
            ) {
                if (header != null) androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 12.dp)) { header() }
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = if (header != null) 0.dp else 12.dp),
                    content = content
                )
            }
        }
    }
}

/** First line of the sheet: what the status bar showed (time · date, Wi-Fi, battery) and ✕. */
@Composable
private fun QuickSheetHeader(onClose: () -> Unit) {
    val battery = rememberBatteryInfo()
    val wifi = rememberWifiConnected()
    val now by androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(15_000) }
    }
    val context = LocalContext.current
    // Time in the device's own 12 / 24 h setting, date in the device's language and order.
    val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(now))
    val date = java.text.SimpleDateFormat(
        android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEEMMMd"),
        java.util.Locale.getDefault()
    ).format(java.util.Date(now))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(time, color = ShelfTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(date, color = ShelfTheme.textMuted, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Icon(if (wifi) Icons.Filled.Wifi else Icons.Filled.WifiOff, null, tint = ShelfTheme.colors.textPrimary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Icon(
            batteryIconFor(battery.percent, battery.charging), null,
            tint = if (!battery.charging && battery.percent < 15) LowBatteryColor else ShelfTheme.colors.textPrimary,
            modifier = Modifier.size(15.dp)
        )
        Text("${battery.percent}%", color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        // 80 % charge limit at work: Limiting / Charging / Paused at 80 %.
        val limit by com.shelf.launcher.device.ChargeLimiter.status.collectAsState()
        if (limit == com.shelf.launcher.device.ChargeLimiter.Status.LIMITING || limit == com.shelf.launcher.device.ChargeLimiter.Status.PAUSED) {
            Spacer(Modifier.width(6.dp))
            Text(
                limit.label, color = ShelfTheme.accent, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.clip(uiRounded(50)).background(ShelfTheme.accent.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        CloseButton(onClose, size = 28.dp)
    }
}

/** Now playing in one line: cover-less title / artist, previous · play · next, tap the text to open the app. */
@Composable
private fun CompactNowPlaying(
    np: com.shelf.launcher.data.NowPlaying,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenApp: () -> Unit
) {
    val art = androidx.compose.runtime.remember(np.art) { np.art?.asImageBitmap() }
    var page by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(PlayerPage.PLAYER) }
    Box(
        Modifier
            .fillMaxWidth()
            .playerGestures(page) { page = it }
            .voltSurface(uiRounded(12.dp))
    ) {
        // The album art, lightly blurred, behind this player only (not the whole sheet).
        ArtBackdrop(np.art, Modifier.matchParentSize())
    if (page != PlayerPage.PLAYER) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundStep(Icons.Filled.ChevronLeft, "Back to player") { page = PlayerPage.PLAYER }
            Spacer(Modifier.width(4.dp))
            if (page == PlayerPage.PLAYLISTS) RoundStep(Icons.Filled.Subtitles, "Lyrics") { page = PlayerPage.LYRICS }
            else RoundStep(Icons.Filled.LibraryMusic, "Playlists") { page = PlayerPage.PLAYLISTS }
            Spacer(Modifier.width(6.dp))
            if (page == PlayerPage.PLAYLISTS) {
                CompactPlaylists(height = 42.dp, textSize = 11.sp, modifier = Modifier.weight(1f), focusableItems = true) { page = PlayerPage.PLAYER }
            } else {
                CompactLyrics(np, height = 42.dp, textSize = 12.sp, modifier = Modifier.weight(1f))
            }
        }
    } else
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (art != null) {
            androidx.compose.foundation.Image(
                art, np.album ?: np.title,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(42.dp).clip(uiRounded(8.dp))
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f).shelfClickable { onOpenApp() }) {
            Text(np.title ?: "Playing", color = Color.White, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.SemiBold, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            np.artist?.let {
                Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        RoundStep(Icons.Filled.SkipPrevious, "Previous", onPrevious)
        Spacer(Modifier.width(4.dp))
        RoundStep(if (np.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (np.isPlaying) "Pause" else "Play", onPlayPause)
        Spacer(Modifier.width(4.dp))
        RoundStep(Icons.Filled.SkipNext, "Next", onNext)
        Spacer(Modifier.width(4.dp))
        // D-pad route to the pages (touch can swipe instead).
        RoundStep(Icons.Filled.LibraryMusic, "Playlists and lyrics") { page = PlayerPage.PLAYLISTS }
    }
    }
}
