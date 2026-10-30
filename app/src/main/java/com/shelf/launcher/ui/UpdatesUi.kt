package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.AppUpdates
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Update 141: Settings › Updates & downloads — Voltcap's version, the update checker (GitHub releases on
 * dzepdzep/Voltcap) and the extra art packs, in one place.
 */

@Composable
internal fun UpdatesContent() {
    Column {
        VersionCard()
        Spacer(Modifier.height(12.dp))
        PacksCard()
    }
}

@Composable
private fun VersionCard() {
    val context = LocalContext.current
    val (name, code, updated) = remember { AppUpdates.installed(context) }
    val st = AppUpdates.state.value
    var auto by remember { mutableStateOf(AppUpdates.autoCheck(context)) }
    ShelfCard {
        Text("Voltcap $name", color = ShelfTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        val date = if (updated > 0) java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(updated)) else "?"
        Text("Build $code · installed $date", color = ShelfTheme.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        when (st) {
            is AppUpdates.State.Idle -> MutedNote("New versions come from Voltcap's releases on GitHub (${AppUpdates.REPO}).")
            is AppUpdates.State.Checking -> Text("Checking GitHub…", color = ShelfTheme.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            is AppUpdates.State.UpToDate -> Text("✓ Up to date (newest on GitHub: ${st.latest})", color = ShelfTheme.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            is AppUpdates.State.Available -> {
                Text("New version: ${st.release.name}", color = ShelfTheme.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                val mb = if (st.release.apkBytes > 0) " · ${st.release.apkBytes / 1_000_000} MB" else ""
                Text("Released ${st.release.published}$mb", color = ShelfTheme.textMuted, fontSize = 12.sp)
                if (st.release.notes.isNotBlank()) { Spacer(Modifier.height(4.dp)); MutedNote(st.release.notes.take(800)) }
                if (st.release.apkUrl == null) MutedNote("That release has no APK attached yet.")
                // Update 202: a skipped version still shows (and installs) here; the home screen just doesn't ask about it.
                if (AppUpdates.isSkipped(st.release.tag)) MutedNote("You skipped this version: the home screen doesn't ask about it.")
            }
            is AppUpdates.State.Downloading -> {
                val pct = if (st.total > 0) " ${(st.done * 100 / st.total).coerceIn(0, 100)} %" else " ${st.done / 1_000_000} MB"
                Text("Downloading ${st.release.name}…$pct", color = ShelfTheme.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            is AppUpdates.State.Ready -> Text("Downloaded ${st.release.name}: Android's installer asks to update.", color = ShelfTheme.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            is AppUpdates.State.Failed -> MutedNote(st.why)
        }
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            when (st) {
                is AppUpdates.State.Available -> if (st.release.apkUrl != null) {
                    if (AppUpdates.canInstall(context)) Chip("Download and install") { AppUpdates.download(context, st.release) }
                    else Chip("Allow installs from Voltcap") { AppUpdates.allowInstalls(context) }
                }
                is AppUpdates.State.Ready -> Chip("Install") { AppUpdates.install(context, st.file) }
                else -> Unit
            }
            if (st !is AppUpdates.State.Checking && st !is AppUpdates.State.Downloading) {
                Chip("Check for updates", accent = st !is AppUpdates.State.Available) { AppUpdates.check(context) }
            }
        }
        // Update 410: the two variants (same package and signing key). Updates keep the installed one; only this switch changes it.
        val variant = remember(st) { AppUpdates.installedVariant(context) }
        val full = variant == com.shelf.launcher.data.AppVariant.FULL
        Spacer(Modifier.height(6.dp))
        MutedNote(
            if (full) "Installed: full version (notifications, other players' now-playing and controls). Updates keep it."
            else "Installed: compatibility version (no notification access; for devices where the full one is blocked). Updates keep it."
        )
        ChipFlow {
            Chip(if (full) "Switch to the compatibility version" else "Switch to the full version", accent = false) {
                AppUpdates.requestSwitch(context, if (full) com.shelf.launcher.data.AppVariant.COMPAT else com.shelf.launcher.data.AppVariant.FULL)
            }
        }
        Spacer(Modifier.height(6.dp))
        // Update 202: the automatic check (UpdatePrompt.kt), asking on the home screen. Update 204: at start, on coming back to
        // Voltcap and every hour.
        ChipFlow {
            Chip((if (auto) "✓ " else "") + "Check for updates automatically", accent = auto) {
                auto = !auto; AppUpdates.setAutoCheck(context, auto)
            }
        }
        MutedNote(
            "A little after Voltcap starts, when you come back to it (from a game or an app) and every hour while it's on " +
                "screen, only with a connection. A new version asks on the home screen: update now, later, or skip that version."
        )
        val lastAt = remember(st) { AppUpdates.lastCheckedAt(context) }
        if (lastAt > 0) {
            val whenText = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(lastAt))
            Text("Last checked $whenText", color = ShelfTheme.textMuted, fontSize = 11.sp)
        }
        // Update 202: "Skip this version" can be taken back here.
        val skippedTag = AppUpdates.skipped.value
        if (skippedTag.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            MutedNote("Skipped: $skippedTag (Voltcap doesn't ask about it again).")
            Spacer(Modifier.height(4.dp))
            ChipFlow { Chip("Ask about $skippedTag again", accent = false) { AppUpdates.unskip(context) } }
        }
    }
}
