package com.shelf.launcher.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.device.DeviceInfo
import com.shelf.launcher.device.DeviceProfileStore
import com.shelf.launcher.device.DeviceProfiles
import com.shelf.launcher.device.InfoRow
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings > Device: what this handheld reports, the vendor profile in use,
 * and crash reports. Collected once when opened and again on Refresh - never polled.
 * The caller supplies the pane's scroll state so ▲▼ scroll it (see ScrollStop).
 */
@Composable
fun DeviceInfoContent(scroll: androidx.compose.foundation.ScrollState? = null) {
    com.shelf.launcher.Breadcrumbs.mark("DeviceInfoContent")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<DeviceInfo?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) {
        info = null
        info = runCatching { DeviceInfo.collect(context) }.getOrNull() ?: DeviceInfo(emptyList())
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (scroll != null) ScrollStop(scroll)
        ChipFlow {
            Chip("Refresh") { reload++ }
            Chip("Copy all", enabled = info != null) {
                info?.let { copyText(context, it.toText()) }
            }
        }
        val data = info
        if (data == null) {
            MutedNote("Reading device information…")
        } else {
            data.sections.forEach { section ->
                SettingsHeading(section.title)
                ShelfCard {
                    section.rows.forEachIndexed { i, row ->
                        if (i > 0) Spacer(Modifier.height(6.dp))
                        InfoLine(row)
                    }
                }
            }
        }
        var showDevices by remember { mutableStateOf(false) }
        MenuRow(
            androidx.compose.material.icons.Icons.Filled.Bluetooth, "Connected devices",
            "Controllers and Bluetooth accessories with their battery levels."
        ) { showDevices = true }
        if (showDevices) ConnectedDevicesDialog { showDevices = false }
        VendorProfileCard()
        // (0.6.17: crash reports are on the Diagnostics page; the notes pointing at Permissions / Overlay went.)
    }
}

@Composable
private fun InfoLine(row: InfoRow) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            row.label, color = ShelfTheme.textMuted, fontSize = 12.sp, lineHeight = 16.sp,
            modifier = Modifier.widthIn(max = 150.dp).weight(0.4f)
        )
        Spacer(Modifier.width(10.dp))
        val v = row.value
        Text(
            v ?: "Not available",
            color = if (v == null) ShelfTheme.textMuted else ShelfTheme.colors.textPrimary,
            fontSize = 13.sp, lineHeight = 17.sp,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun VendorProfileCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf<com.shelf.launcher.device.VendorAdapter?>(null) }
    var overrideId by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf(0) }
    LaunchedEffect(version) {
        withContext(Dispatchers.IO) {
            val a = DeviceProfiles.current(context)
            overrideId = DeviceProfileStore(context).override()
            current = a
        }
    }
    SettingsHeading("Vendor profile")
    ShelfCard {
        val a = current
        if (a == null) {
            MutedNote("Detecting…")
        } else {
            val detected = DeviceProfiles.detect()
            InfoLine(InfoRow("In use", a.displayName + if (overrideId != null) " (chosen by you)" else " (detected)"))
            Spacer(Modifier.height(6.dp))
            InfoLine(InfoRow("Detected from this device", detected.displayName))
            Spacer(Modifier.height(8.dp))
            if (a.capabilities.isEmpty()) {
                MutedNote("No vendor-specific features for this profile.")
            } else {
                ChipFlow { a.capabilities.forEach { Chip(it.label, accent = false) {} } }
            }
            Spacer(Modifier.height(10.dp))
            SectionLabel("Profile")
            MutedNote("Automatic is right for nearly every device. Pick one only if your handheld isn't recognised.")
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                Chip("Automatic", accent = overrideId == null) {
                    scope.launch { withContext(Dispatchers.IO) { DeviceProfiles.setOverride(context, null) }; version++ }
                }
                DeviceProfiles.all.forEach { adapter ->
                    Chip(adapter.displayName, accent = overrideId == adapter.id) {
                        scope.launch { withContext(Dispatchers.IO) { DeviceProfiles.setOverride(context, adapter.id) }; version++ }
                    }
                }
            }
        }
    }
}

private fun copyText(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Device information", text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
}
