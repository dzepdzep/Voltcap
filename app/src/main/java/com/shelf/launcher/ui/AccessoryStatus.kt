package com.shelf.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.shelf.launcher.device.accessories.Accessory
import com.shelf.launcher.device.accessories.AccessoryKind
import com.shelf.launcher.device.accessories.AccessoryMonitor
import com.shelf.launcher.device.accessories.HiddenAccessories
import com.shelf.launcher.device.accessories.AccessoryTransport
import com.shelf.launcher.ui.theme.ShelfTheme

// Connected controllers / headphones in the status bar, and the popup with the details.

/** True only while the host activity is RESUMED - listeners and receivers are gated on this. */
@Composable
internal fun rememberIsResumed(): Boolean {
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) resumed = true
            else if (e == Lifecycle.Event.ON_PAUSE) resumed = false
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return resumed
}

/** Accessory list; the monitor runs only while [enabled], this composable is active and the app is RESUMED. */
@Composable
internal fun rememberAccessories(enabled: Boolean): List<Accessory> {
    val context = LocalContext.current
    val monitor = remember { AccessoryMonitor.get(context) }
    val resumed = rememberIsResumed()
    DisposableEffect(enabled, resumed) {
        if (enabled && resumed) monitor.acquire()
        onDispose { if (enabled && resumed) monitor.release() }
    }
    val list by monitor.state.collectAsState()
    return if (enabled) remember(list, HiddenAccessoriesVersion.value) { list.filterNot { HiddenAccessories.isHidden(context, it) } } else emptyList()
}

/** Bumped when something is hidden / shown, so the lists filter again. */
internal val HiddenAccessoriesVersion = androidx.compose.runtime.mutableStateOf(0)

/** Everything, hidden ones included (Connected devices shows those in their own list). */
@Composable
private fun rememberAllAccessories(): List<Accessory> {
    val context = LocalContext.current
    val monitor = remember { AccessoryMonitor.get(context) }
    val resumed = rememberIsResumed()
    DisposableEffect(resumed) {
        if (resumed) monitor.acquire()
        onDispose { if (resumed) monitor.release() }
    }
    val list by monitor.state.collectAsState()
    return list
}

private fun AccessoryKind.icon(): ImageVector = when (this) {
    AccessoryKind.CONTROLLER -> Icons.Filled.SportsEsports
    AccessoryKind.HEADPHONES -> Icons.Filled.Headphones
    AccessoryKind.EARBUDS -> Icons.Filled.Headset
    AccessoryKind.KEYBOARD -> Icons.Filled.Keyboard
    AccessoryKind.MOUSE -> Icons.Filled.Mouse
    AccessoryKind.SPEAKER -> Icons.Filled.Speaker
    AccessoryKind.OTHER -> Icons.Filled.Bluetooth
}

private fun AccessoryKind.label() = when (this) {
    AccessoryKind.CONTROLLER -> "Controller"
    AccessoryKind.HEADPHONES -> "Headphones"
    AccessoryKind.EARBUDS -> "Earbuds"
    AccessoryKind.KEYBOARD -> "Keyboard"
    AccessoryKind.MOUSE -> "Mouse"
    AccessoryKind.SPEAKER -> "Speaker"
    AccessoryKind.OTHER -> "Device"
}

private fun AccessoryTransport.label(): String? = when (this) {
    AccessoryTransport.BLUETOOTH -> "Bluetooth"
    AccessoryTransport.USB -> "USB"
    AccessoryTransport.WIRELESS_DONGLE -> "2.4 GHz dongle"
    AccessoryTransport.UNKNOWN -> null // only what can be determined is labelled
}

/** The number shown on a chip: earbuds show their weaker bud, everything else the overall level. */
private fun Accessory.chipLevel(): Int? {
    val b = battery ?: return null
    val buds = listOfNotNull(b.left, b.right)
    return if (kind == AccessoryKind.EARBUDS && buds.isNotEmpty()) buds.min() else b.headline
}

/** Up to [maxChips] compact chips; the rest collapse into "+N". Whole group opens the popup. */
@Composable
internal fun AccessoryChips(accessories: List<Accessory>, maxChips: Int, tint: Color = Color.White, onClick: () -> Unit) {
    if (accessories.isEmpty() || maxChips <= 0) return
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.clip(shape).shelfClickable(shape) { onClick() }.padding(horizontal = 3.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shown = if (accessories.size <= maxChips) accessories else accessories.take(1)
        shown.forEachIndexed { i, a ->
            if (i > 0) Spacer(Modifier.width(6.dp))
            Icon(a.kind.icon(), a.name, tint = tint, modifier = Modifier.size(16.dp))
            val level = a.chipLevel()
            if (level != null) {
                Spacer(Modifier.width(2.dp))
                Icon(
                    batteryIconFor(level, a.battery?.charging == true), null,
                    tint = if (level < 15 && a.battery?.charging != true) LowBatteryColor else tint,
                    modifier = Modifier.size(14.dp)
                )
                Text("$level%", color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (accessories.size > shown.size) {
            Spacer(Modifier.width(4.dp))
            Text("+${accessories.size - shown.size}", color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun ConnectedDevicesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val all = rememberAllAccessories()
    val version = HiddenAccessoriesVersion.value
    val accessories = remember(all, version) { all.filterNot { HiddenAccessories.isHidden(context, it) } }
    val hiddenOnes = remember(all, version) { all.filter { HiddenAccessories.isHidden(context, it) } }
    var hideBuiltIn by remember { mutableStateOf(HiddenAccessories.hideBuiltIn(context)) }
    var permitted by remember { mutableStateOf(AccessoryMonitor.hasBluetoothPermission(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        if (granted) AccessoryMonitor.get(context).refresh()
    }
    ShelfDialog(
        icon = Icons.Filled.Bluetooth,
        title = "Connected devices",
        subtitle = "Controllers and Bluetooth accessories, with the battery levels they report.",
        onDismiss = onDismiss,
        footer = {
            if (AccessoryMonitor.bluetoothAvailable(context)) {
                Chip("Bluetooth settings", accent = false) {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
            Chip("Close", onClick = onDismiss)
        }
    ) {
        if (!permitted && AccessoryMonitor.bluetoothAvailable(context)) {
            Chip("Allow Bluetooth access to show headphone battery") {
                ask.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
            Spacer(Modifier.height(10.dp))
            MutedNote("Without it, Bluetooth headphones and other accessories aren't listed. Controllers still are.")
            Spacer(Modifier.height(10.dp))
        }
        if (accessories.isEmpty()) {
            EmptyState("No controllers or accessories connected.")
        } else {
            accessories.forEachIndexed { i, a ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                AccessoryRow(a, hidden = false) { HiddenAccessories.setHidden(context, a.hideKey, true); HiddenAccessoriesVersion.value++ }
            }
        }
        Spacer(Modifier.height(12.dp))
        SwitchRow(
            icon = Icons.Filled.Bluetooth,
            title = "Hide the handheld's own controls",
            subtitle = "Many handhelds list their built-in buttons as a connected pad (AYN's as an \"Xbox Wireless Controller\"). Off shows it again.",
            checked = hideBuiltIn,
            onChange = { hideBuiltIn = it; HiddenAccessories.setHideBuiltIn(context, it); HiddenAccessoriesVersion.value++ }
        )
        if (hiddenOnes.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionLabel("Hidden")
            hiddenOnes.forEachIndexed { i, a ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                AccessoryRow(a, hidden = true) {
                    HiddenAccessories.setHidden(context, a.hideKey, false)
                    if (a.builtIn && hideBuiltIn) { hideBuiltIn = false; HiddenAccessories.setHideBuiltIn(context, false) }
                    HiddenAccessoriesVersion.value++
                }
            }
        }
    }
}

/** One device; A (or tap) hides it — or, in the Hidden list, shows it again. */
@Composable
private fun AccessoryRow(a: Accessory, hidden: Boolean, onToggleHidden: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ShelfTheme.colors.surfaceRaised.copy(alpha = if (hidden) 0.5f else 1f))
            .shelfClickable(shape) { onToggleHidden() }
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(a.kind.icon(), null, tint = ShelfTheme.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.name + if (a.builtIn) " (built in)" else "", color = ShelfTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Text(if (hidden) "A: show" else "A: hide", color = ShelfTheme.textMuted, fontSize = 10.sp)
            }
            Text(
                listOfNotNull(a.kind.label(), a.transport.label()).joinToString(" · "),
                color = ShelfTheme.textMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            val b = a.battery
            if (b == null || b.headline == null && b.case == null) {
                Text("Battery: Not reported", color = ShelfTheme.textMuted, fontSize = 13.sp)
            } else {
                val parts = buildList {
                    b.levelPercent?.let { add("$it%") }
                    if (b.left != null || b.right != null) {
                        add("L ${b.left?.let { "$it" } ?: "-"} · R ${b.right?.let { "$it" } ?: "-"}")
                    }
                    b.case?.let { add("Case $it%") }
                    if (b.charging == true) add("Charging")
                }
                Text("Battery: " + parts.joinToString("  ·  "), color = ShelfTheme.colors.textPrimary, fontSize = 13.sp)
                Text(b.source.replaceFirstChar { it.uppercase() }, color = ShelfTheme.textMuted, fontSize = 11.sp)
            }
        }
    }
}
