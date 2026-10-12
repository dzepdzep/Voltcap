package com.shelf.launcher.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The R1 panel's home / library / game tiles and helpers (QuickSettings.kt shows them): the home layout, the
 * carousel look, the theme, rescan, scrape missing art, the game's power profile, the battery, the brightness and
 * volume sliders, the two pages (L1 / R1) and "hold a tile for its settings".
 */

/** What the R1 panel needs from the home screen (LauncherScreen builds it). */
class R1Home(
    val appearance: AppearanceSettings,
    val setAppearance: (AppearanceSettings) -> Unit,
    /** Theme in use and switching to another. */
    val themeName: String,
    val themeAction: (() -> Unit)?,
    /** Update 122: the theme in use (its AppTheme name), for the Colours tile; null hides it. */
    val themeKey: String? = null,
    /** Rescan every ROM folder and the apps; true while a scan runs. */
    val scanning: Boolean,
    val onRescan: () -> Unit,
    /** Missing artwork: (done, total) while it runs (total 0 = idle). */
    val scrapeDone: Int, val scrapeTotal: Int,
    val onScrapeMissing: () -> Unit,
    /** The game you're on (key, name, platform, console name), for its power profile; null = none. */
    val gameKey: String?, val gameName: String?, val gamePlatform: String?, val gameConsole: String?,
    /** The other games of that console (for "Copy to all"). */
    val consoleSiblings: () -> List<String>,
    /** Opens Voltcap's settings at a setting's place (its label in the settings search), else at [section]. */
    val openSettingsAt: (section: String?, label: String?) -> Unit,
    /** The achievements progress bar on the wide capsule: is the badge beside the title, and flip that. */
    val badgeNextToTitle: Boolean = false,
    val onToggleBadgePlacement: () -> Unit = {}
)

/** The two pages of tiles (L1 / R1 flip them while the panel is open). */
internal object R1Pages {
    var page by mutableStateOf(0)
    /** QuickSettingsCard is on screen (only then do L1 / R1 flip pages). */
    var active = false
    /** Set by a shoulder press: the page's first tile takes the focus. */
    var focusAfterFlip by mutableStateOf(0)

    fun flip(step: Int) {
        page = (page + step).mod(2)
        focusAfterFlip++
    }

    /** Tiles that belong on the Device page (the handheld's own switches, power, battery, Android). */
    fun onDevicePage(id: String): Boolean =
        id.startsWith("dev_") || id in setOf("mixer", "battery", "batterywatch", "profile", "settings", "navbar", "fps")
}

/** Where holding a tile goes: a setting's label in the settings search (or a section), or Android's own screen. */
internal sealed class R1Target {
    class Voltcap(val section: String?, val label: String?) : R1Target()
    class Android(val action: String) : R1Target()
}

/** Holding tile [id] opens this (null = nothing, or the tile has its own hold action). */
internal fun r1HoldTarget(id: String): R1Target? = when {
    id == "wifi" -> R1Target.Android(Settings.ACTION_WIFI_SETTINGS)
    id == "bt" -> R1Target.Android(Settings.ACTION_BLUETOOTH_SETTINGS)
    id == "dnd" -> R1Target.Android("android.settings.ZEN_MODE_SETTINGS")
    id == "lights" -> R1Target.Voltcap("Theme", "Controller lights")
    id == "music" -> R1Target.Voltcap("Home", "Music player")
    id == "saves" -> R1Target.Voltcap("Sync", "Google Drive")
    id == "dual" -> R1Target.Voltcap("Screens", null)
    id == "navbar" -> R1Target.Voltcap("Device", "Hide navigation bar")
    id == "sort" -> R1Target.Voltcap("Library", "Sort games by")
    id == "rowpos" -> R1Target.Voltcap("Home", "Where the row sits")
    id == "romm" -> R1Target.Voltcap("Accounts", "RomM")
    id == "gamelook" || id == "media" || id == "badges" -> R1Target.Voltcap("Game look", "Game look")
    id == "layout" -> R1Target.Voltcap("Home", "Home layout")
    id == "carousel" -> R1Target.Voltcap("Home", "Carousel look")
    id == "theme" -> R1Target.Voltcap("Theme", "Themes")
    id == "colors" -> R1Target.Voltcap("Theme", "Colours follow (theme, game or music)")
    id == "rescan" -> R1Target.Voltcap("Library", "Your games")
    id == "scrape" -> R1Target.Voltcap("Metadata", "Artwork source")
    id == "profile" -> R1Target.Voltcap("Performance", "Performance tuner")
    id == "mixer" -> R1Target.Voltcap("Audio", null)
    id == "settings" -> R1Target.Voltcap(null, null)
    id == "dev_AIRPLANE" -> R1Target.Android(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
    id == "dev_REFRESH_RATE" -> R1Target.Android(Settings.ACTION_DISPLAY_SETTINGS)
    id == "dev_CONTROLLER_STYLE" || id == "dev_FLOATING_BUTTON" -> R1Target.Voltcap("Controls", null)
    id.startsWith("dev_") -> R1Target.Voltcap("Performance", null)
    else -> null
}

internal fun openAndroidScreen(context: Context, action: String) {
    runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

// ---- Battery ----

/** Percent, charging, and the time left (to empty, or to full while charging) when Android / the gauge can tell. */
internal class BatteryNow(val percent: Int, val charging: Boolean, val minutesLeft: Int?)

internal fun readBattery(context: Context): BatteryNow {
    val sticky = runCatching { context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
    val level = sticky?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = sticky?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = sticky?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
    val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else 0
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
    val minutes: Int? = runCatching {
        if (bm == null) return@runCatching null
        if (charging) {
            if (android.os.Build.VERSION.SDK_INT >= 28) bm.computeChargeTimeRemaining().takeIf { it > 0 }?.let { (it / 60_000L).toInt() } else null
        } else {
            // What's left in the battery over what flows out now (µAh / µA = hours).
            val left = bm.getLongProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            val now = kotlin.math.abs(bm.getLongProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
            // Some gauges report mA instead of µA: anything under 5 is no reading at all.
            val draw = if (now in 5..5_000) now * 1000 else now
            if (left > 0 && draw > 1_000) ((left.toDouble() / draw) * 60).toInt().takeIf { it in 1..(60 * 48) } else null
        }
    }.getOrNull()
    return BatteryNow(percent, charging, minutes)
}

internal fun minutesLabel(m: Int): String = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

/** Holding the battery tile: the charge controls in one place (80 % limit, charge bypass) and Android's battery page. */
@Composable
internal fun BatteryChargePopup(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val battery = remember(refresh) { readBattery(context) }
    // Root / sysfs reads: off the main thread.
    val charge by androidx.compose.runtime.produceState<Triple<Boolean, Boolean, String>?>(null, refresh) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                Triple(
                    com.shelf.launcher.device.ChargeLimiter.supported(context),
                    com.shelf.launcher.device.ChargeLimiter.isOn(context),
                    com.shelf.launcher.device.ChargeLimiter.currentStatus(context).label
                )
            }.getOrNull()
        }
    }
    val bypass by androidx.compose.runtime.produceState<com.shelf.launcher.device.ControlState?>(null, refresh) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (com.shelf.launcher.device.DeviceControl.BYPASS_CHARGING in com.shelf.launcher.device.DeviceControls.available(context))
                    com.shelf.launcher.device.DeviceControls.read(context, com.shelf.launcher.device.DeviceControl.BYPASS_CHARGING)
                else null
            }.getOrNull()
        }
    }
    val limitSupported = charge?.first == true
    val limitOn = charge?.second == true
    val statusLabel = charge?.third.orEmpty()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    ShelfDialog(
        icon = Icons.Filled.BatteryChargingFull, title = "Battery & charging",
        subtitle = "${battery.percent}%" + (battery.minutesLeft?.let { " · " + minutesLabel(it) + if (battery.charging) " to full" else " left" } ?: "") +
            if (battery.charging) " · charging" else "",
        onDismiss = onDismiss
    ) {
        if (limitSupported) {
            SwitchRow(
                Icons.Filled.BatteryChargingFull, "80 % charge limit",
                "Stops charging at 80 % to spare the battery." + (if (statusLabel.isNotEmpty()) " Now: $statusLabel." else ""),
                limitOn, { on ->
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { com.shelf.launcher.device.ChargeLimiter.setOn(context, on) } }
                        refresh++
                    }
                }
            )
        } else if (charge != null) MutedNote("This handheld's charge limit can't be switched from Voltcap.")
        val bp = bypass
        if (bp != null) {
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                Icons.Filled.Speed, "Charge bypass",
                "Plugged in, the handheld runs from the charger and leaves the battery alone (less heat while playing).",
                bp.on, {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { com.shelf.launcher.device.DeviceControls.next(context, com.shelf.launcher.device.DeviceControl.BYPASS_CHARGING) }
                        }
                        refresh++
                    }
                }
            )
        }
        Spacer(Modifier.height(10.dp))
        ChipFlow {
            Chip("Android battery settings", accent = false) { openAndroidScreen(context, Intent.ACTION_POWER_USAGE_SUMMARY) }
        }
    }
}

/** The game's power profile (own / console / device-wide), short, for the tile: "Own · Balanced". */
internal fun r1ProfileLabel(context: Context, key: String, platform: String?): String {
    val store = com.shelf.launcher.device.tune.TuneStore(context)
    val own = store.forKey(key)
    val cons = platform?.takeIf { it.isNotBlank() && store.consoleProfiles }?.let { store.forKey("plat:$it") }
    val p = own ?: cons ?: store.global()
    val where = when { own != null -> "Own"; cons != null -> "Console"; else -> "Device" }
    return where + " · " + p.tier.label
}

/** Pressing the power tile: the game's performance profile right in the panel (the same card as in its game settings). */
@Composable
internal fun GameProfilePopup(home: R1Home, onDismiss: () -> Unit) {
    val key = home.gameKey ?: return
    ShelfDialog(
        icon = Icons.Filled.Speed, title = "Power for " + (home.gameName ?: "this game"),
        subtitle = "Set when the game starts, put back when you return to Voltcap.",
        onDismiss = onDismiss, maxContentHeightFraction = 0.75f
    ) {
        GamePerformanceProfile(key, home.gameName.orEmpty(), home.gamePlatform, home.gameConsole, home.consoleSiblings)
    }
}

// ---- Sliders ----

/**
 * A slim slider for the top of the panel: icon, bar, value. Tap or drag the bar; with the D-pad, ◀ ▶ change it by
 * [step] while it has the focus.
 */
@Composable
internal fun QuickSlider(
    icon: ImageVector, percent: Int, onSet: (Int) -> Unit, step: Int = 10, label: String = "$percent%",
    /** ◀ ▶ as a step down / up (−1 / +1) instead of [step] %, e.g. one real volume step (update 119). */
    onKeyStep: ((Int) -> Unit)? = null
) {
    var widthPx by remember { mutableStateOf(1) }
    val latest = androidx.compose.runtime.rememberUpdatedState(onSet)
    val keyStep = androidx.compose.runtime.rememberUpdatedState(onKeyStep)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(uiRounded(10.dp))
            .shelfFocusable(uiRounded(10.dp))
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { keyStep.value?.invoke(-1) ?: latest.value((percent - step).coerceIn(0, 100)); true }
                    Key.DirectionRight -> { keyStep.value?.invoke(1) ?: latest.value((percent + step).coerceIn(0, 100)); true }
                    else -> false
                }
            }
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = ShelfTheme.colors.textPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .height(24.dp)
                .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                .pointerInput(Unit) {
                    detectTapGestures { o -> latest.value((o.x / widthPx * 100).toInt().coerceIn(0, 100)) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        latest.value((change.position.x / widthPx * 100).toInt().coerceIn(0, 100))
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(Modifier.fillMaxWidth().height(if (VoltUi.on) 6.dp else 4.dp).clip(uiRounded(3.dp)).background(Color.White.copy(alpha = 0.12f))) {
                Box(Modifier.fillMaxWidth(percent / 100f).fillMaxHeight().background(ShelfTheme.accent))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = ShelfTheme.textMuted, fontSize = 12.sp, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
    }
}

/** Media volume, 0–100 %. */
internal fun mediaVolumePercent(context: Context): Int = runCatching {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    // (Update 119: rounded, the same way it's set, so a value never reads back lower than it was set.)
    Math.round(am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) * 100f / max)
}.getOrDefault(50)

/**
 * Sets the media volume to the system's step nearest [percent] and returns the percent that step really is (what
 * the bar should show). Update 119: Android has only so many volume steps (15 on many devices): the bar showed the
 * value asked for (93 %), then the step it really landed on (90 %) — it "jumped back". Now it shows the real value.
 */
internal fun setMediaVolumePercent(context: Context, percent: Int): Int = runCatching {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val step = Math.round(percent.coerceIn(0, 100) / 100f * max)
    am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, step, 0)
    Math.round(step * 100f / max)
}.getOrDefault(percent)

/** Update 119: the media volume one real step up ([dir] 1) or down (−1); returns its percent. */
internal fun stepMediaVolume(context: Context, dir: Int): Int = runCatching {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val step = (am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) + dir).coerceIn(0, max)
    am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, step, 0)
    Math.round(step * 100f / max)
}.getOrDefault(mediaVolumePercent(context))
