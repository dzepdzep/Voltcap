package com.shelf.launcher.ui

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.shelf.launcher.data.Backup
import com.shelf.launcher.data.SettingsScope
import com.shelf.launcher.data.SettingsScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What "Reset settings" leaves alone although it counts as portable: your library data, not looks or behaviour. */
private val KEEP_ON_RESET = setOf(
    "collections", "favorites", "playtime", "recently_played", "art_overrides", "game_overrides", "metadata_cache",
    "art_cache", "ra_matches", "ra_icons", "screenshots", "screenshots_v2", "video_urls", "steam_games",
    "game_profiles", "shortcuts", "hidden_apps", "library_apps", "platform_media", "platform_order", "physical_media",
    // Found covers and matches (library data, not looks).
    "save_vault_art", "manual_scrape", "save_capsules", "art_matching"
)

/** Per-device settings that are still just looks (not folders, emulators or storage), so they go back too. */
// (platform_intro holds the picked intro-video files: those are not just looks, so it stays.)
private val DEVICE_LOOKS = setOf("r1_tiles", "screen_layouts", "gesture_hint")

private fun prefFiles(context: Context): List<String> =
    File(context.applicationInfo.dataDir, "shared_prefs").listFiles { f -> f.name.endsWith(".xml") }
        ?.map { it.name.removeSuffix(".xml") }.orEmpty()

/** Puts every look and behaviour setting back to its default (library data, accounts and folders stay). */
private fun resetSettingsNow(context: Context): Int {
    var n = 0
    prefFiles(context).forEach { name ->
        val scope = SettingsScopes.scopeOf(name)
        val wipe = (scope == SettingsScope.GLOBAL && name !in KEEP_ON_RESET) || name in DEVICE_LOOKS
        if (wipe) {
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            // A global file that also holds this device's own picks (folders, wallpaper, second-screen settings…) keeps those.
            val keep = if (name !in DEVICE_LOOKS && SettingsScopes.hasDeviceKeys(name)) prefs.all.filterKeys { SettingsScopes.isDeviceKey(name, it) } else emptyMap()
            val e = prefs.edit().clear()
            keep.forEach { (k, v) ->
                @Suppress("UNCHECKED_CAST")
                when (v) {
                    is Boolean -> e.putBoolean(k, v); is Int -> e.putInt(k, v); is Long -> e.putLong(k, v)
                    is Float -> e.putFloat(k, v); is String -> e.putString(k, v); is Set<*> -> e.putStringSet(k, v as Set<String>)
                }
            }
            e.commit()
            n++
        }
    }
    return n
}

/** Settings › Backup › Reset app: back to defaults, or a clean first start. Both ask first. */
@Composable
internal fun ResetAppCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf(0) } // 1 = settings, 2 = everything
    ShelfCard {
        SectionLabel("Reset app")
        MutedNote(
            "Reset settings puts every look and behaviour setting back to its default: layout, theme, game look, R1 tiles, " +
                "music player, sounds. Your games, play time, favourites, collections, saved art, ROM folders and sign-ins stay. " +
                "Reset everything wipes Voltcap's data on this device, like a fresh install."
        )
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            Chip("Reset settings", accent = false) { ask = 1 }
            Chip("Reset everything", accent = false) { ask = 2 }
        }
    }
    if (ask != 0) {
        Dialog(onDismissRequest = { ask = 0 }) {
            ImmersiveDialogWindow()
            ShelfCard {
                Column(Modifier.padding(4.dp)) {
                    SectionLabel(if (ask == 1) "Reset all settings?" else "Reset everything?")
                    MutedNote(
                        if (ask == 1) "Voltcap restarts with its default look and behaviour. Your library, saves and accounts are kept."
                        else "This deletes Voltcap's data on this device: settings, play time, favourites, saved art, and sign-ins. " +
                            "Game files and saves on the device are not touched. It can't be undone."
                    )
                    Spacer(Modifier.height(10.dp))
                    ChipFlow {
                        Chip(if (ask == 1) "Reset settings" else "Wipe everything") {
                            val what = ask
                            ask = 0
                            if (what == 1) {
                                scope.launch {
                                    withContext(Dispatchers.IO) { runCatching { resetSettingsNow(context) } }
                                    Backup.restart(context)
                                }
                            } else {
                                runCatching { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData() }
                            }
                        }
                        Chip("Cancel", accent = false) { ask = 0 }
                    }
                }
            }
        }
    }
}

/** Settings › Device › Works on this device: what this handheld can run, and which settings travel to other devices. */
@Composable
internal fun DeviceCompatCard() {
    val context = LocalContext.current
    val facts by produceState<List<Pair<String, Boolean>>?>(null) {
        value = withContext(Dispatchers.IO) {
            val lite = runCatching { com.shelf.launcher.device.DevicePerformance.isLowEnd(context) }.getOrDefault(false)
            val smallRam = runCatching { com.shelf.launcher.device.MemoryBudget.small(context) }.getOrDefault(false)
            val screens = runCatching { (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).displays.size }.getOrDefault(1)
            val lights = runCatching { com.shelf.launcher.data.ControllerLights.describe(context).isNotEmpty() }.getOrDefault(false)
            val tune = runCatching { com.shelf.launcher.device.tune.Tuner.supported }.getOrDefault(false)
            val mixer = runCatching { com.shelf.launcher.device.audio.AppMixer.available(context) }.getOrDefault(false)
            listOf(
                "Animated themes, glows and scenes (full effects)" to !(lite || smallRam),
                "Second screen / dual-screen layouts" to (screens > 1),
                "Controller lights" to lights,
                "Power tuning (TDP, CPU / GPU limits)" to tune,
                "Volume mixer per app" to mixer
            )
        }
    }
    val files = remember { prefFiles(context).map { SettingsScopes.scopeOf(it) } }
    ShelfCard {
        SectionLabel("Works on this device")
        val list = facts
        if (list == null) MutedNote("Checking this device…")
        else {
            list.forEach { (name, ok) -> MutedNote((if (ok) "✓  " else "✕  ") + name) }
            if (!list[0].second) {
                Spacer(Modifier.height(6.dp))
                MutedNote("This device is better off with Lite effects: pick the quieter themes and keep animated scenes off (Settings › Performance).")
            }
        }
        Spacer(Modifier.height(10.dp))
        MutedNote(
            "Settings that move to another device with a backup: looks, layout, themes, categories, favourites. " +
                "Settings that only make sense here (ROM folders, emulators, second-screen layouts, R1 tile order, lights, power tuning) " +
                "stay on this device, and sign-ins are never exported. Here: ${files.count { it == SettingsScope.GLOBAL }} portable, " +
                "${files.count { it == SettingsScope.DEVICE }} for this device only."
        )
    }
}
