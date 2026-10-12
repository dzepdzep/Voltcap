package com.shelf.launcher.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Permissions
import com.shelf.launcher.data.VoltPermission
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Every permission Voltcap uses, with one "Allow everything" button that walks through them:
 * the small Android popups first (all together), then each special-access screen in turn —
 * a short note says what to switch on, and coming back opens the next one by itself.
 * On AYN handhelds the firmware grants them all at once, no screens at all.
 * Used by the first-start setup and Settings › Device.
 */
@Composable
fun PermissionsSetup(modifier: Modifier = Modifier, showAll: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    // The guided walk: which screens were already opened this time, and whether it's running.
    var guiding by remember { mutableStateOf(false) }
    // True while one of Android's settings screens is open for the walk (runtime popups don't count).
    var awaitingScreen by remember { mutableStateOf(false) }
    val asked = remember { mutableSetOf<VoltPermission>() }

    val missing = remember(refresh) { Permissions.missing(context) }
    // Every row's state read from Android itself (refreshed on every return to Voltcap), on-demand ones too.
    val allowedNow = remember(refresh) {
        VoltPermission.values().filter { Permissions.applies(it) }.associateWith { Permissions.granted(context, it) }
    }

    fun openNext() {
        val next = Permissions.missing(context).firstOrNull { it !in asked && Permissions.settingsIntent(context, it) != null }
        if (next == null) {
            guiding = false
            val left = Permissions.missing(context)
            note = when {
                left.isEmpty() -> "All set — everything Voltcap can use is allowed."
                left.all { it == VoltPermission.FPS } -> "All set. Only the FPS number still needs the one-time PC step below (optional)."
                else -> "Some were skipped — tap Allow next to them any time."
            }
            refresh++
            return
        }
        asked += next
        // The note shows on top of Android's screen, so it's clear what to switch on there.
        Toast.makeText(context, "${next.title}: ${next.how}", Toast.LENGTH_LONG).show()
        awaitingScreen = true
        if (!Permissions.openSettings(context, next)) { awaitingScreen = false; openNext() }
    }

    val askRuntime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
        if (guiding) openNext()
    }

    // Coming back from one of Android's screens: re-check, and carry on with the next one.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refresh++
                if (guiding && awaitingScreen) {
                    awaitingScreen = false
                    scope.launch { delay(500); if (guiding) openNext() }
                }
            }
        }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }

    fun allowEverything() {
        note = null
        scope.launch {
            if (Permissions.canGrantDirectly) {
                busy = true
                val left = withContext(Dispatchers.IO) { runCatching { Permissions.grantAllDirectly(context) }.getOrDefault(Permissions.missing(context)) }
                busy = false
                refresh++
                if (left.isEmpty()) { note = "All set — this handheld allowed everything at once."; return@launch }
            }
            asked.clear()
            guiding = true
            val runtime = Permissions.missing(context).filter { Permissions.needsPopup(context, it) }.mapNotNull { it.runtime }.distinct()
            if (runtime.isNotEmpty()) askRuntime.launch(runtime.toTypedArray()) else openNext()
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShelfCard {
            Text(
                if (missing.isEmpty()) "✓ Everything is allowed" else "${missing.size} permission${if (missing.size == 1) "" else "s"} to allow",
                color = if (missing.isEmpty()) ShelfTheme.accent else ShelfTheme.colors.textPrimary,
                fontSize = 15.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            MutedNote(
                if (Permissions.canGrantDirectly)
                    "This handheld lets Voltcap switch them all on in one tap — no settings screens."
                else
                    "One tap walks you through them: Android shows each screen, a note says what to switch on, " +
                        "and pressing Back moves on to the next. Nothing here is shared with anyone."
            )
            if (missing.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ChipFlow {
                    Chip(if (busy) "Allowing…" else "Allow everything", enabled = !busy) { allowEverything() }
                }
            }
            note?.let { Spacer(Modifier.height(6.dp)); MutedNote(it) }
        }

        ShelfCard {
            VoltPermission.values().filter { Permissions.applies(it) }.forEachIndexed { i, p ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                val ok = allowedNow[p] == true
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (ok) "✓" else "•",
                        color = if (ok) ShelfTheme.accent else ShelfTheme.textMuted,
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(20.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            p.title + when { p.required -> "  (needed)"; p.onDemand -> "  (optional)"; else -> "" },
                            color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                        MutedNote(p.why)
                    }
                    if (!ok && p != VoltPermission.FPS) {
                        Spacer(Modifier.width(8.dp))
                        Chip("Allow", accent = p.required) {
                            val rt = p.runtime
                            if (rt != null && Permissions.needsPopup(context, p)) askRuntime.launch(arrayOf(rt))
                            else {
                                Toast.makeText(context, "${p.title}: ${p.how}", Toast.LENGTH_LONG).show()
                                Permissions.openSettings(context, p)
                            }
                        }
                    }
                }
                if (!ok && p == VoltPermission.FPS) {
                    Column(Modifier.padding(start = 20.dp, top = 4.dp)) {
                        if (Permissions.canGrantDirectly) {
                            MutedNote("\"Allow everything\" above takes care of it on this handheld.")
                        } else {
                            MutedNote("Optional. Android only allows this from a computer, once: enable USB debugging, connect, and run")
                            Text(Permissions.FPS_ADB_COMMAND, color = ShelfTheme.accent, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                            ChipFlow {
                                Chip("Copy command", accent = false) {
                                    runCatching {
                                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        cm.setPrimaryClip(android.content.ClipData.newPlainText("ADB command", Permissions.FPS_ADB_COMMAND))
                                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showAll) AllPermissionsCard(refresh)
    }
}

/** Plain words for every permission in Voltcap's manifest (the ones Android grants without asking too). */
private val PERMISSION_WHY: Map<String, String> = mapOf(
    "android.permission.INTERNET" to "Artwork, game info, RetroAchievements, Steam, lyrics, cloud sync and guides.",
    "android.permission.ACCESS_NETWORK_STATE" to "Waits for a connection before syncing or scraping, and works offline from saved data.",
    "android.permission.ACCESS_WIFI_STATE" to "The Wi-Fi tile and signal in the status bar; the web remote's address.",
    "android.permission.CHANGE_WIFI_STATE" to "The Wi-Fi switch in the R1 quick settings.",
    "android.permission.QUERY_ALL_PACKAGES" to "Sees your installed apps and emulators, so they can be listed, launched and matched to games.",
    "android.permission.MANAGE_EXTERNAL_STORAGE" to "Finds your ROMs anywhere and syncs emulator saves (\"Files on this device\" above).",
    "android.permission.READ_EXTERNAL_STORAGE" to "Reads ROMs and saves on older Android versions.",
    "android.permission.WRITE_EXTERNAL_STORAGE" to "Writes saves and the Voltcap folder on older Android versions.",
    "android.permission.READ_MEDIA_IMAGES" to "Picks your own pictures for covers and backgrounds.",
    "android.permission.READ_MEDIA_VIDEO" to "Picks your own video backgrounds.",
    "android.permission.READ_MEDIA_AUDIO" to "Plays your own music and sounds.",
    "android.permission.POST_NOTIFICATIONS" to "Save-sync progress, achievement pop-ups and the recaps (\"Show notifications\" above).",
    "android.permission.SYSTEM_ALERT_WINDOW" to "The performance, music and RetroAchievements overlays over games.",
    "android.permission.WRITE_SETTINGS" to "Brightness and screen timeout from the R1 quick settings.",
    "android.permission.ACCESS_NOTIFICATION_POLICY" to "The Do Not Disturb tile.",
    "android.permission.BLUETOOTH_CONNECT" to "Battery levels of Bluetooth controllers and headphones.",
    "android.permission.BLUETOOTH" to "Bluetooth accessories on older Android versions.",
    "android.permission.BLUETOOTH_ADMIN" to "The Bluetooth tile on older Android versions.",
    "android.permission.VIBRATE" to "Haptics: the tick when moving and the tap when selecting.",
    "android.permission.WAKE_LOCK" to "Keeps a save sync or download going when the screen turns off.",
    "android.permission.RECEIVE_BOOT_COMPLETED" to "Starts the weekly recap and background sync again after a restart.",
    "android.permission.FOREGROUND_SERVICE" to "The overlays and save sync keep running while you play.",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE" to "The in-game overlays (Android 14 needs a reason for them).",
    "android.permission.FOREGROUND_SERVICE_DATA_SYNC" to "Save sync running in the background.",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" to "Voltcap's own background music.",
    "android.permission.PACKAGE_USAGE_STATS" to "Knows which game is in front, for play time and the running-games glow.",
    "android.permission.KILL_BACKGROUND_PROCESSES" to "Closes a game you quit from the running-games list.",
    "android.permission.REQUEST_DELETE_PACKAGES" to "Uninstalls an app from its options in Voltcap (Android still asks you to confirm).",
    "android.permission.REQUEST_INSTALL_PACKAGES" to "Installs an emulator you downloaded from the emulator list.",
    "android.permission.SET_WALLPAPER" to "Sets a cover or theme as the system wallpaper.",
    "android.permission.EXPAND_STATUS_BAR" to "Opens Android's own quick settings from the R1 panel.",
    "android.permission.DUMP" to "The FPS number in the performance overlay (\"Read frame rate\" above).",
    "android.permission.RECORD_AUDIO" to "Music-reactive themes listen to what's playing (nothing is recorded or saved).",
    "android.permission.MODIFY_AUDIO_SETTINGS" to "Volume and audio focus for previews and background music.",
    "android.permission.USE_FULL_SCREEN_INTENT" to "Shows a save-sync question over a game when it needs you.",
    "android.permission.SCHEDULE_EXACT_ALARM" to "Sleep timer and scheduled jobs on time.",
    "com.android.launcher.permission.INSTALL_SHORTCUT" to "Lets other apps add shortcuts to Voltcap.",
    "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" to "Notification access: music controls and the R1 notifications list."
)

/**
 * Settings › Permissions: every permission in Voltcap's manifest — including the ones Android gives
 * without asking — with what each is used for and whether it's on right now.
 */
@Composable
private fun AllPermissionsCard(refresh: Int) {
    val context = LocalContext.current
    // Special-access ones are app-ops / settings switches, not "granted" in the package info: ask Android properly.
    fun specialGranted(name: String): Boolean? = when (name) {
        "android.permission.SYSTEM_ALERT_WINDOW" -> Permissions.granted(context, VoltPermission.OVERLAY)
        "android.permission.MANAGE_EXTERNAL_STORAGE" -> Permissions.granted(context, VoltPermission.FILES)
        "android.permission.WRITE_SETTINGS" -> Permissions.granted(context, VoltPermission.SYSTEM_SETTINGS)
        "android.permission.ACCESS_NOTIFICATION_POLICY" -> Permissions.granted(context, VoltPermission.DND)
        "android.permission.PACKAGE_USAGE_STATS" -> Permissions.usageAccess(context)
        "android.permission.REQUEST_INSTALL_PACKAGES" -> runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        "android.permission.POST_NOTIFICATIONS" -> Permissions.granted(context, VoltPermission.NOTIFICATIONS)
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" -> Permissions.granted(context, VoltPermission.NOTIFICATION_ACCESS)
        "android.permission.BLUETOOTH_CONNECT" -> Permissions.granted(context, VoltPermission.BLUETOOTH)
        "android.permission.RECORD_AUDIO" -> Permissions.granted(context, VoltPermission.MICROPHONE)
        "android.permission.DUMP" -> Permissions.granted(context, VoltPermission.FPS)
        else -> null
    }
    val list = remember(refresh) {
        runCatching {
            val pm = context.packageManager
            val info = if (android.os.Build.VERSION.SDK_INT >= 33)
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(android.content.pm.PackageManager.GET_PERMISSIONS.toLong()))
            else @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
            val names = info.requestedPermissions.orEmpty()
            val flags = info.requestedPermissionsFlags ?: IntArray(names.size)
            names.mapIndexed { i, n ->
                Triple(n, specialGranted(n) ?: (flags.getOrElse(i) { 0 } and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED != 0), PERMISSION_WHY[n])
            }
                .sortedWith(compareBy({ it.third == null }, { it.first }))
        }.getOrDefault(emptyList())
    }
    var open by remember { mutableStateOf(false) }
    ShelfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Every permission (${list.size})", color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                MutedNote("Everything Voltcap's install asks Android for, also the ones given without a question, and what each one is for.")
            }
            Spacer(Modifier.width(8.dp))
            Chip(if (open) "Hide" else "Show", accent = false) { open = !open }
        }
        if (open) {
            list.forEach { (name, granted, why) ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text(if (granted) "✓" else "•", color = if (granted) ShelfTheme.accent else ShelfTheme.textMuted,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            name.substringAfterLast('.').replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                            color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(why ?: "Used by a library Voltcap includes.", color = ShelfTheme.textMuted, fontSize = 11.sp, lineHeight = 14.sp)
                        Text(name, color = ShelfTheme.textMuted.copy(alpha = 0.6f), fontSize = 9.sp)
                    }
                    Text(if (granted) "On" else "Off", color = if (granted) ShelfTheme.accent else ShelfTheme.textMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Once at startup for people past the first-start setup (e.g. after an update added new
 * permissions): if something useful isn't allowed yet, offer the same checklist. Asked once per
 * new set of missing permissions; Settings › Device › Permissions has it any time.
 */
object PermissionsPrompt {
    private const val PREFS = "permissions_prompt"

    fun shouldShow(context: android.content.Context): Boolean {
        if (!com.shelf.launcher.data.FirstRun.isDone(context)) return false
        val missing = Permissions.missing(context).filter { it != VoltPermission.FPS || Permissions.canGrantDirectly }
        if (missing.isEmpty()) return false
        val key = missing.joinToString(",") { it.name }
        return context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString("asked_for", null) != key
    }

    fun markAsked(context: android.content.Context) {
        val key = Permissions.missing(context).filter { it != VoltPermission.FPS || Permissions.canGrantDirectly }
            .joinToString(",") { it.name }
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putString("asked_for", key).apply()
    }
}

@Composable
fun PermissionsPromptDialog(onDismiss: () -> Unit) {
    ShelfDialog(
        icon = Icons.Filled.Security,
        title = "A few permissions",
        subtitle = "So everything in Voltcap works",
        onDismiss = onDismiss,
        maxContentHeightFraction = 0.7f,
        footer = { Chip("Done", onClick = onDismiss) }
    ) {
        PermissionsSetup()
    }
}
