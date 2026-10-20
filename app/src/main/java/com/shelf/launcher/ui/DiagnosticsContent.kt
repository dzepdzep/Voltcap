package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Game
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** One line of the diagnostics page. */
private data class Check(val group: String, val name: String, val ok: Boolean?, val detail: String)

/**
 * Settings › Diagnostics: everything Voltcap depends on, checked in one go — root service, FPS
 * sources, permissions, storage, emulators, accounts, lights, tuner, charge limit — with a report
 * to copy or share when something's wrong.
 */
@Composable
internal fun DiagnosticsContent(games: List<Game>, emulatorPlatforms: Set<String>) {
    val context = LocalContext.current
    var run by remember { mutableIntStateOf(0) }
    val shareScope = androidx.compose.runtime.rememberCoroutineScope()
    val checks by produceState<List<Check>?>(null, run) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { collect(context, games, emulatorPlatforms) }.getOrElse { listOf(Check("Voltcap", "Diagnostics", false, it.toString())) } }
    }
    SettingsHeading("Diagnostics")
    val c = checks
    if (c == null) { ShelfCard { MutedNote("Checking…") }; return }
    ShelfCard {
        val bad = c.count { it.ok == false }
        Text(if (bad == 0) "Everything checked out." else if (bad == 1) "1 thing needs a look." else "$bad things need a look.", color = if (bad == 0) ShelfTheme.accent else ShelfTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            Chip("Check again", accent = false) { run++ }
            Chip("Share report") { shareScope.launch {
                // (Update 149: with the newest crash report and this run's recent log, personal details taken out.)
                val text = report(context, c) + withContext(Dispatchers.IO) {
                    runCatching {
                        buildString {
                            com.shelf.launcher.data.crash.CrashReports.latestReportText(context)?.let { appendLine(); appendLine("[Latest crash report]"); appendLine(it) }
                            com.shelf.launcher.data.crash.CrashReports.recentLog()?.let { appendLine(); appendLine("[Recent log]"); appendLine(it) }
                        }
                    }.getOrDefault("")
                }
                runCatching {
                    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .setPrimaryClip(android.content.ClipData.newPlainText("Voltcap diagnostics", text))
                }
                runCatching {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_SUBJECT, "Voltcap diagnostics").putExtra(android.content.Intent.EXTRA_TEXT, text)
                    context.startActivity(android.content.Intent.createChooser(send, "Share report").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } }
        }
    }
    c.groupBy { it.group }.forEach { (group, list) ->
        Spacer(Modifier.height(10.dp))
        ShelfCard {
            SectionLabel(group)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                list.forEach { ch ->
                    Text(
                        (when (ch.ok) { true -> "✓ "; false -> "✗ "; null -> "• " }) + ch.name,
                        color = if (ch.ok == false) ShelfTheme.colors.textPrimary else ShelfTheme.textMuted,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.shelfFocusable()
                    )
                    if (ch.detail.isNotBlank()) MutedNote(ch.detail)
                }
            }
        }
    }
}

private fun report(context: android.content.Context, checks: List<Check>): String = buildString {
    val pi = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    appendLine("Voltcap ${pi?.versionName ?: "?"} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
    checks.groupBy { it.group }.forEach { (g, list) ->
        appendLine(); appendLine("[$g]")
        list.forEach { appendLine((when (it.ok) { true -> "OK  "; false -> "BAD "; null -> "    " }) + it.name + if (it.detail.isNotBlank()) " — " + it.detail else "") }
    }
}

private fun collect(context: android.content.Context, games: List<Game>, emulatorPlatforms: Set<String>): List<Check> {
    val out = ArrayList<Check>()
    val pi = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    out += Check("Voltcap", "Version", null, "${pi?.versionName ?: "?"} on ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}")
    out += Check("Voltcap", "Safe mode", !com.shelf.launcher.CrashGuard.safeMode, if (com.shelf.launcher.CrashGuard.safeMode) "On after repeated crashes — see Crash reports below." else "Off")
    out += Check("Voltcap", "Default home app", null, if (com.shelf.launcher.data.HomeLauncher.isDefault(context)) "Voltcap is the home app" else "Another app is the home app")

    // Device
    val root = com.shelf.launcher.device.RootShell.available
    // (0.6.18: Retroid's firmware has the same service — it said "AYN" on the Flip 2.)
    val vendor = if (android.os.Build.MODEL.contains("Retroid", ignoreCase = true)) "Retroid" else if (android.os.Build.MANUFACTURER.equals("AYN", true) || android.os.Build.BRAND.equals("AYN", true)) "AYN" else "vendor firmware"
    out += Check("Device", "Vendor root service", null, if (root) "Available ($vendor) — clocks, controller style, L2 / R2 mode and the handheld's own switches" else "Not on this firmware — those features are limited")
    val clusters = runCatching { com.shelf.launcher.device.tune.TuneHardware.clusters() }.getOrDefault(emptyList())
    out += Check("Device", "Performance tuner", if (root) clusters.isNotEmpty() else null,
        if (clusters.isEmpty()) "No CPU control" else clusters.joinToString { "${it.name} ${it.maxKhz / 1000} MHz" } +
            (com.shelf.launcher.device.tune.TuneHardware.gpu()?.let { g -> " · GPU ${g.levelMhz.firstOrNull()} MHz (${g.levelMhz.size} levels)" } ?: "") +
            (if (com.shelf.launcher.device.tune.TuneHardware.hasCustomFan()) " · fan control" else ""))
    out += Check("Device", "Charge limit", null, com.shelf.launcher.device.ChargeLimiter.currentStatus(context).label)
    // 0.6.18: the charging log (read-only; Android/data/com.shelf.launcher/files/charge_log.csv).
    out += Check("Device", "Charging log", null, com.shelf.launcher.data.ChargeLog.summary(context) ?: "Nothing logged yet — it notes temperatures once a minute while the handheld charges.")
    out += Check("Device", "Stick lights", null, com.shelf.launcher.data.ControllerLights.describe(context).joinToString().ifBlank { "None found" })

    // FPS
    com.shelf.launcher.data.perf.FpsDiagnosis.explain(context, com.shelf.launcher.data.perf.PerfSampler.gamePackage).forEach {
        out += Check("Frame rate (FPS)", it.source, if (it.source.startsWith("A game")) null else it.ok, it.detail)
    }

    // Permissions
    com.shelf.launcher.data.VoltPermission.values().filter { com.shelf.launcher.data.Permissions.applies(it) }.forEach { p ->
        val ok = runCatching { com.shelf.launcher.data.Permissions.granted(context, p) }.getOrDefault(false)
        out += Check("Permissions", p.title, if (ok) true else if (p.required) false else null, if (ok) "" else p.why)
    }

    // Library
    val roms = games.filter { it.sourceType == com.shelf.launcher.data.SourceType.ROM }
    out += Check("Library", "Games", roms.isNotEmpty() || games.isNotEmpty(), "${roms.size} ROMs, ${games.count { it.sourceType == com.shelf.launcher.data.SourceType.APP }} apps, ${games.count { it.sourceType == com.shelf.launcher.data.SourceType.STEAM }} Steam")
    val platforms = roms.map { it.platform }.distinct()
    val noEmu = platforms.filter { it !in emulatorPlatforms }
    out += Check("Library", "Emulators set up", noEmu.isEmpty(),
        if (noEmu.isEmpty()) "Every console has an emulator" else "No emulator yet: " + noEmu.joinToString { com.shelf.launcher.data.ConsoleNames.display(it) })
    val unreadable = roms.take(400).count { g -> g.romPath?.startsWith("/") == true && !java.io.File(g.romPath!!).canRead() }
    out += Check("Library", "ROM files readable", unreadable == 0, if (unreadable == 0) "" else "$unreadable ROM files can't be read — check the Files permission")

    // Accounts
    out += Check("Accounts", "RetroAchievements", null, if (com.shelf.launcher.data.RaCredentialsStore(context).load() != null) "Connected" else "Not connected")
    out += Check("Accounts", "ScreenScraper", null, if (com.shelf.launcher.data.ScreenScraperStore(context).load() != null) "Connected" else "Not connected")
    out += Check("Accounts", "Steam achievements", null, if (com.shelf.launcher.data.SteamAchievements.account(context) != null) "Connected" else "Not connected")
    val cloud = com.shelf.launcher.data.CloudSync.state.value
    out += Check("Accounts", "Google Drive sync", null, if (cloud.conflicts.isNotEmpty()) "${cloud.conflicts.size} save conflict(s) waiting" else "No conflicts")

    // Network
    val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val online = runCatching { cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true }.getOrDefault(false)
    out += Check("Network", "Internet", online, if (online) "" else "Offline — art, achievements and sync wait until you're online")
    out += Check("Network", "Phone remote", null, if (com.shelf.launcher.data.WebRemote.running) (com.shelf.launcher.data.WebRemote.address() ?: "No Wi-Fi") else "Off")

    // Storage
    val free = runCatching { android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path).availableBytes }.getOrDefault(-1L)
    // Update 144: an unreadable size (-1) is "unknown", not a green tick.
    out += Check("Storage", "Free space", if (free < 0) null else free >= 1_000_000_000L, if (free >= 0) "%.1f GB free".format(free / 1e9) else "Unknown")
    return out
}
