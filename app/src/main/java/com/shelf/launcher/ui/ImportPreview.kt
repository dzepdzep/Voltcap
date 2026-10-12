package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.shelf.launcher.data.SettingsTransfer
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Import Preview: before anything is replaced, what the incoming settings would change, grouped
 * (Appearance, Themes, Layout, Categories, Achievements, Save Vault, Music, Controls, Other). Untick a
 * group to keep this device's own. Device-only settings and sign-ins are never part of it.
 */
@Composable
internal fun ImportPreviewCard(
    preview: SettingsTransfer.Preview,
    onApply: (excluded: Set<SettingsTransfer.Group>) -> Unit,
    onCancel: () -> Unit
) {
    var excluded by remember(preview) { mutableStateOf(emptySet<SettingsTransfer.Group>()) }
    ShelfCard {
        SectionLabel("Import preview")
        val from = listOfNotNull(preview.info.sourceModel, preview.info.appVersion?.let { "Voltcap $it" }).joinToString(" · ")
        MutedNote(
            (if (from.isNotEmpty()) "From $from. " else "") +
                "Nothing has changed yet. Untick what you'd rather keep as it is on this device. " +
                "This device's own setup (folders, emulators, screens) and sign-ins always stay."
        )
        // Sign-ins the file doesn't carry: say so up front, and which ones can never travel.
        val portableMissing = preview.missingAccounts.filter { it !in preview.perDevice }
        if (portableMissing.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            MutedNote(
                "No sign-ins in this file for " + portableMissing.joinToString(", ") + ". It was saved without \"Include accounts\" " +
                    "(or by a Voltcap from before accounts could go along). Export it again on the other device with " +
                    "\"Include accounts\" on, or sign in here afterwards."
            )
        }
        if (preview.perDevice.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            MutedNote(preview.perDevice.joinToString(", ") + (if (preview.perDevice.size == 1) " signs" else " sign") +
                " in on each device (they can't travel in a file): sign in here after importing.")
        }
        Spacer(Modifier.height(8.dp))
        if (preview.groups.isEmpty()) {
            MutedNote("These settings are the same as yours: there's nothing to change.")
        }
        preview.groups.forEach { g ->
            val on = g.group !in excluded
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .shelfClickable(shape) { excluded = if (on) excluded + g.group else excluded - g.group }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (on) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, null,
                    tint = if (on) ShelfTheme.accent else ShelfTheme.textMuted, modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${g.group.label} · ${g.changes} change${if (g.changes == 1) "" else "s"}",
                        color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        g.examples.joinToString(", ") + if (g.changes > g.examples.size) "…" else "",
                        color = ShelfTheme.textMuted, fontSize = 11.sp, maxLines = 2
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            if (preview.groups.isNotEmpty()) Chip("Import selected and restart", enabled = excluded.size < preview.groups.size) { onApply(excluded) }
            Chip("Cancel", accent = false) { onCancel() }
        }
    }
}

/**
 * Settings › Backup › Share with another device: Google Drive (your own sign-in) or a nearby Voltcap
 * on the same Wi-Fi with a 6-digit PIN. Whatever arrives opens in the Import Preview ([onReceived]).
 */
@Composable
internal fun SettingsShareCard(
    /** Update 147: only the receiving half (the first start's "from another device"): no sending, no Drive. */
    receiveOnly: Boolean = false,
    onReceived: (org.json.JSONObject) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var sendPin by remember { mutableStateOf<String?>(null) }
    var sendJob by remember { mutableStateOf<Job?>(null) }
    var offers by remember { mutableStateOf<List<com.shelf.launcher.data.LocalTransfer.Offer>?>(null) }
    var picked by remember { mutableStateOf<com.shelf.launcher.data.LocalTransfer.Offer?>(null) }
    var pinText by remember { mutableStateOf("") }
    var withAccounts by remember { mutableStateOf(true) }
    ShelfCard {
        SectionLabel(if (receiveOnly) "From another device" else "Share settings with another device")
        MutedNote(
            if (receiveOnly) "On your other device open Settings › Backup › Share settings, then Nearby device › Send (a PIN) or QR code › Show QR code to send. Both on the same Wi-Fi."
            else "Only the look and behaviour travel: never sign-ins, never this device's folders or emulators. You see what changes before anything is applied."
        )
        Spacer(Modifier.height(6.dp))
        if (!receiveOnly) Chip(if (withAccounts) "✓ Include accounts (RetroAchievements, SteamGridDB, ScreenScraper…)" else "Include accounts (RetroAchievements, SteamGridDB, ScreenScraper…)", accent = withAccounts) { withAccounts = !withAccounts }
        if (!receiveOnly) MutedNote("Logins and API keys go along so the other device is signed in too (Google Drive, Spotify and Discord sign in on each device). The receiving side can still leave them out in the preview.")
        Spacer(Modifier.height(8.dp))
        if (!receiveOnly) FoldGroup("Google Drive", summary = "Uses the Google account set up under Sync", key = "share_drive", startOpen = true) {
            ChipFlow {
                Chip("Send my settings to Drive", enabled = !busy) {
                    busy = true; status = "Sending…"
                    scope.launch {
                        status = runCatching { withContext(Dispatchers.IO) { SettingsTransfer.sendToDrive(context, withAccounts) } }
                            .fold({ "Sent. On the other device: Settings › Backup › Get settings from Drive." }, { "Couldn't send: ${(it.message ?: "unknown error")}" })
                        busy = false
                    }
                }
                Chip("Get settings from Drive", accent = false, enabled = !busy) {
                    busy = true; status = "Looking on Drive…"
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { SettingsTransfer.fetchFromDrive(context) } }
                            .onSuccess { status = null; onReceived(it) }
                            .onFailure { status = "Couldn't get them: ${(it.message ?: "unknown error")}" }
                        busy = false
                    }
                }
            }
        }
        FoldGroup("Nearby device (PIN)", summary = "Both on the same Wi-Fi, no account needed", key = "share_nearby", startOpen = receiveOnly) {
            val pin = sendPin
            if (pin != null) {
                Text("PIN  $pin", color = ShelfTheme.accent, fontSize = 26.sp, fontWeight = FontWeight.Black)
                MutedNote("On the other device: Settings › Backup › Nearby device › Receive, then type this PIN. Waiting up to 3 minutes…")
                Chip("Stop", accent = false) { sendJob?.cancel(); sendPin = null }
            } else ChipFlow {
                if (!receiveOnly) Chip("Send to a nearby device", enabled = !busy) {
                    val p = com.shelf.launcher.data.LocalTransfer.newPin()
                    sendPin = p
                    sendJob = scope.launch {
                        // Stop cancels this: no "nobody picked them up" afterwards (update 91).
                        val ok = try {
                            com.shelf.launcher.data.LocalTransfer.send(context, p, withAccounts)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            false
                        }
                        status = if (ok) "Sent to the other device." else "Nobody picked them up (or the PIN was wrong)."
                        sendPin = null
                    }
                }
                Chip("Receive from a nearby device", accent = false, enabled = !busy) {
                    busy = true; status = "Looking for Voltcap nearby…"; offers = null; picked = null
                    scope.launch {
                        val found = runCatching { com.shelf.launcher.data.LocalTransfer.discover(context) }.getOrDefault(emptyList())
                        offers = found
                        status = if (found.isEmpty()) "No Voltcap is sending nearby. Start \"Send to a nearby device\" on the other one first." else null
                        busy = false
                    }
                }
            }
            offers?.takeIf { it.isNotEmpty() }?.let { list ->
                Spacer(Modifier.height(6.dp))
                ChipFlow { list.forEach { o -> Chip(o.name, accent = picked == o) { picked = o } } }
            }
            picked?.let { o ->
                Spacer(Modifier.height(6.dp))
                ShelfTextField(value = pinText, onValueChange = { pinText = it.filter(Char::isDigit).take(6) }, label = "PIN from ${o.name}")
                Spacer(Modifier.height(6.dp))
                Chip("Get settings", enabled = pinText.length == 6 && !busy) {
                    busy = true; status = "Receiving…"
                    scope.launch {
                        runCatching {
                            val text = com.shelf.launcher.data.LocalTransfer.receive(context, o, pinText)
                            com.shelf.launcher.data.Backup.readDocument(text)
                        }.onSuccess { status = null; offers = null; picked = null; pinText = ""; onReceived(it) }
                            .onFailure { status = "Couldn't receive: ${it.message}" }
                        busy = false
                    }
                }
            }
        }
        FoldGroup("QR code", summary = "Scan to connect — no typing, no discovery", key = "share_qr") {
            QrTransferSection(withAccounts, receiveOnly, onStatus = { status = it }, onReceived = onReceived)
        }
        status?.let { Spacer(Modifier.height(6.dp)); MutedNote(it) }
    }
}

/**
 * Share settings › QR code. Send: this device listens (like the PIN transfer) and shows a QR code with
 * its address, port and PIN, plus the same as a short code for devices without a camera. Receive: scan
 * it (Play services' scanner, no camera permission), or type the short code and PIN. A phone camera
 * scanning it opens Voltcap there too (voltcap://transfer link → this section).
 */
@Composable
private fun QrTransferSection(withAccounts: Boolean, receiveOnly: Boolean, onStatus: (String?) -> Unit, onReceived: (org.json.JSONObject) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<com.shelf.launcher.data.QrTransfer.Target?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    val camera = remember { com.shelf.launcher.data.QrTransfer.hasCamera(context) }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }

    fun receive(t: com.shelf.launcher.data.QrTransfer.Target) {
        busy = true; onStatus("Connecting to ${t.name}…")
        scope.launch {
            runCatching { com.shelf.launcher.data.Backup.readDocument(com.shelf.launcher.data.LocalTransfer.fetch(t.host, t.port, t.pin)) }
                .onSuccess { onStatus(null); code = ""; pin = ""; onReceived(it) }
                .onFailure { onStatus("Couldn't receive from ${t.name}: ${it.message ?: "no answer"}. Both on the same Wi-Fi, and the code still showing?") }
            busy = false
        }
    }
    // A QR code opened from outside (phone camera → voltcap://transfer): receive it right away.
    val pending by com.shelf.launcher.data.QrTransfer.pending.collectAsState()
    LaunchedEffect(pending) {
        val t = pending ?: return@LaunchedEffect
        com.shelf.launcher.data.QrTransfer.pending.value = null
        receive(t)
    }

    val showing = target
    if (showing != null) {
        val link = remember(showing) { com.shelf.launcher.data.QrTransfer.link(showing) }
        val qr = remember(link) { com.shelf.launcher.data.QrCode.encode(link) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (qr != null) QrCodeImage(qr, Modifier.size(168.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Scan with the other device", color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                MutedNote("Settings › Backup › Share settings › QR code › Scan. No camera there? Type the code and PIN:")
                Text(showing.shortCode, color = ShelfTheme.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("PIN  ${showing.pin}", color = ShelfTheme.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                MutedNote("Waiting up to 3 minutes; one device can receive.")
                Spacer(Modifier.height(6.dp))
                Chip("Stop", accent = false) { job?.cancel(); target = null; onStatus(null) }
            }
        }
    } else {
        ChipFlow {
            if (!receiveOnly) Chip("Show QR code to send", enabled = !busy) {
                val host = com.shelf.launcher.data.LocalTransfer.localAddress(context)
                if (host == null) { onStatus("Connect to Wi-Fi first: the other device reaches this one over the local network."); return@Chip }
                val p = com.shelf.launcher.data.LocalTransfer.newPin()
                val name = "Voltcap ${android.os.Build.MODEL}"
                job = scope.launch {
                    // Stop / leaving cancels this: the status Stop set stays (update 91).
                    val ok = try {
                        com.shelf.launcher.data.LocalTransfer.send(context, p, withAccounts) { port ->
                            target = com.shelf.launcher.data.QrTransfer.Target(host, port, p, name)
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        false
                    }
                    target = null
                    onStatus(if (ok) "Sent to the other device." else "Nobody scanned it in time.")
                }
            }
            if (camera) Chip("Scan a QR code", accent = false, enabled = !busy) {
                com.shelf.launcher.data.QrTransfer.scan(context) { text ->
                    val t = com.shelf.launcher.data.QrTransfer.parse(text)
                    when {
                        text == null -> onStatus("No code scanned. No camera scanner on this device? Type the code below.")
                        t == null -> onStatus("That isn't a Voltcap transfer code.")
                        else -> receive(t)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        MutedNote(if (camera) "Or type the code shown under the QR on the other device:" else "This device has no camera: type the code shown under the QR on the other device.")
        Spacer(Modifier.height(4.dp))
        ShelfTextField(value = code, onValueChange = { code = it.filter { c -> c.isDigit() || c == '.' || c == ':' }.take(21) }, label = "Code (e.g. 192.168.1.20:40123)")
        Spacer(Modifier.height(4.dp))
        ShelfTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(6) }, label = "PIN")
        Spacer(Modifier.height(6.dp))
        val typed = com.shelf.launcher.data.QrTransfer.parseShort(code, pin)
        Chip("Get settings", enabled = typed != null && !busy) { typed?.let { receive(it) } }
    }
}

/** A QR code drawn crisp at any size, dark on white with the standard quiet border. */
@Composable
internal fun QrCodeImage(qr: com.shelf.launcher.data.QrCode, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.background(Color.White, RoundedCornerShape(8.dp))) {
        val n = qr.size + 8
        val cell = kotlin.math.floor(minOf(size.width, size.height) / n)
        val ox = (size.width - cell * n) / 2f + 4 * cell
        val oy = (size.height - cell * n) / 2f + 4 * cell
        for (y in 0 until qr.size) for (x in 0 until qr.size) if (qr.dark(x, y)) {
            drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(ox + x * cell, oy + y * cell), size = androidx.compose.ui.geometry.Size(cell, cell))
        }
    }
}
