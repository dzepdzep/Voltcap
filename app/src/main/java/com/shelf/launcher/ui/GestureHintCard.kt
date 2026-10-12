package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.GestureHint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings › Device › Gesture hint: hide the white navigation pill while Voltcap is open.
 * "Hide navigation bar" is plain immersive mode (on by default); "Force-hide gesture hint" changes the
 * device's own hint setting for devices that keep drawing the pill — through the handheld's root service,
 * or a permission granted once from a computer. No Shizuku. See data/GestureHint.kt.
 */
@Composable
internal fun GestureHintCard() {
    val context = LocalContext.current
    val immersive = GestureHint.immersiveState.value
    var force by remember { mutableStateOf(GestureHint.forceOn(context)) }
    var status by remember { mutableStateOf("…") }
    var needsAdb by remember { mutableStateOf(false) }
    // Bumped to re-check after a change (the checks may ask the root service: off the main thread).
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh, force) {
        if (refresh > 0) kotlinx.coroutines.delay(400) // let the change land first
        val r = withContext(Dispatchers.IO) {
            runCatching { Pair(GestureHint.status(context), GestureHint.needsAdb(context)) }.getOrNull()
        }
        if (r != null) { status = r.first; needsAdb = r.second }
    }
    Spacer(Modifier.height(12.dp))
    ShelfCard {
        SectionLabel("Gesture hint (navigation pill)")
        MutedNote("Hides the white bar at the bottom while Voltcap is open. Gesture navigation keeps working: swipe from the edge as usual.")
        Spacer(Modifier.height(10.dp))
        SwitchRow(
            Icons.Filled.Gesture, "Hide navigation bar",
            "Android's immersive mode. A swipe from the edge shows it for a moment; it hides again by itself. Popups and the keyboard keep it hidden too.",
            checked = immersive,
            onChange = { GestureHint.setImmersive(context, it) }
        )
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            Icons.Filled.VisibilityOff, "Force-hide gesture hint",
            "Only if the pill still shows. Turns off the device's own gesture-hint setting while Voltcap is in front, " +
                "and puts it back when you leave Voltcap or turn this off. No other app has to run.",
            checked = force,
            onChange = {
                force = it
                GestureHint.setForce(context, it)
                refresh++
            }
        )
        if (needsAdb) {
            Spacer(Modifier.height(8.dp))
            MutedNote("Once, from a computer with USB debugging on (it stays after restarts and updates):")
            Spacer(Modifier.height(4.dp))
            MutedNote(GestureHint.ADB_GRANT)
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                Chip("Copy command", accent = true) {
                    runCatching {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("adb", GestureHint.ADB_GRANT))
                    }
                }
                Chip("Check again") { refresh++ }
            }
        }
        Spacer(Modifier.height(6.dp))
        MutedNote(status + if (force) " Turn this off before uninstalling Voltcap, so the hint comes back for good." else "")
    }
}
