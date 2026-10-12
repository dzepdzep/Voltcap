package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.shelf.launcher.data.Backup
import com.shelf.launcher.data.SecureStore
import com.shelf.launcher.data.SnapshotInfo
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Two things that belong next to Export / Import: "Sign in again" notices for
 * services whose saved login was lost (restored onto a new device, or the
 * Keystore key changed), and the automatic safety copies Voltcap makes
 * before every import, each with a Restore button.
 *
 * Shows nothing when there is nothing to say. D-pad friendly: only Chips.
 */
@Composable
internal fun BackupProfilesCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshots by remember { mutableStateOf<List<SnapshotInfo>>(emptyList()) }
    var services by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirm by remember { mutableStateOf<SnapshotInfo?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val s = runCatching { Backup.listSnapshots(context) }.getOrDefault(emptyList())
            val p = runCatching { SecureStore.pendingRelogin(context) }.getOrDefault(emptyList())
            snapshots = s
            services = p
        }
    }
    if (snapshots.isEmpty() && services.isEmpty() && message == null) return

    Spacer(Modifier.height(12.dp))
    ShelfCard {
        if (services.isNotEmpty()) {
            SectionLabel("Sign in again")
            MutedNote(
                "Sign-ins never travel between devices (and can't be read after a restore), so these need entering once more. " +
                    "The notice goes away when you sign in, or dismiss it."
            )
            services.forEach { service ->
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Sign in again to $service",
                        color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    Chip("Dismiss", accent = false) {
                        SecureStore.clearRelogin(context, service)
                        services = services - service
                    }
                }
            }
            if (snapshots.isNotEmpty()) Spacer(Modifier.height(14.dp))
        }
        if (snapshots.isNotEmpty()) {
            SectionLabel("Settings before an import")
            MutedNote("Voltcap saves your current settings just before every import (the last 5 are kept) so you can undo it.")
            snapshots.forEach { snap ->
                Spacer(Modifier.height(8.dp))
                val whenText = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                    .format(java.util.Date(snap.created))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "$whenText · ${snap.files} files",
                        color = ShelfTheme.colors.textPrimary, fontSize = 13.sp
                    )
                    Chip("Restore", accent = false) { confirm = snap }
                }
            }
            confirm?.let { snap ->
                Spacer(Modifier.height(10.dp))
                Text(
                    "Put these settings back? Voltcap restarts afterwards.",
                    color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("Restore and restart") {
                        confirm = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching { Backup.restoreSnapshot(context, snap.id) } }
                            result.onSuccess { Backup.restart(context) }
                                .onFailure { message = "Couldn't restore: ${it.message}" }
                        }
                    }
                    Chip("Cancel", accent = false) { confirm = null }
                }
            }
        }
        message?.let {
            Spacer(Modifier.height(8.dp))
            MutedNote(it)
        }
    }
}
