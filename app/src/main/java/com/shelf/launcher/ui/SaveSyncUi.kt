package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.CloudSync
import kotlinx.coroutines.launch
import com.shelf.launcher.data.GameSyncStatus
import com.shelf.launcher.data.SyncDirection
import com.shelf.launcher.data.SyncState
import com.shelf.launcher.data.SyncStatusStore
import com.shelf.launcher.ui.theme.ShelfTheme

/** "just now", "5 min ago", "3 h ago", "2 days ago". */
internal fun syncAgo(at: Long, now: Long = System.currentTimeMillis()): String {
    val mins = ((now - at) / 60_000L).coerceAtLeast(0)
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 60 * 24 -> "${mins / 60} h ago"
        else -> "${mins / (60 * 24)} d ago"
    }
}

/** The badge's short label, e.g. "Saved to Drive · 5 min ago". Null status = nothing recorded yet. */
internal fun syncBadgeLabel(status: GameSyncStatus?): String {
    if (status == null) return "Not synced yet"
    return when (status.state) {
        SyncState.SYNCED ->
            if (status.lastSuccessAt > 0L) "Saved to Drive · ${syncAgo(status.lastSuccessAt)}" else "Saved to Drive"
        SyncState.UPLOADING -> "Uploading…"
        SyncState.DOWNLOADING -> "Downloading…"
        SyncState.OFFLINE -> "Offline — will retry"
        SyncState.CONFLICT -> "Conflict — choose"
        SyncState.NEEDS_SIGN_IN -> "Sign in again"
        SyncState.ERROR -> "Sync failed"
        SyncState.PENDING ->
            if (status.lastSuccessAt > 0L) "Changes waiting to sync" else "Not synced yet"
    }
}

/**
 * Compact per-game save status: animation/icon + short label. Nothing at all unless Drive is [connected]
 * (the caller also checks that the game can sync). [selected] draws the details-window highlight.
 */
@Composable
fun SaveSyncBadge(
    status: GameSyncStatus?,
    connected: Boolean,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    if (!connected) return
    val shape = RoundedCornerShape(50)
    val base = modifier
        .clip(shape)
        .background(if (selected) ShelfTheme.accent.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f))
        .let { if (onClick != null) it.shelfClickable(shape) { onClick() } else it }
        .padding(horizontal = 10.dp, vertical = 4.dp)
    Row(base, verticalAlignment = Alignment.CenterVertically) {
        SyncAnimation(status?.state ?: SyncState.PENDING, 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            syncBadgeLabel(status),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp),
            fontWeight = FontWeight.Medium
        )
    }
}

private fun stateTitle(status: GameSyncStatus?): String = when (status?.state) {
    null -> "Not synced yet"
    // (Update 529: says what was verified, and when it was - never a standing "safe". A save changed after that, e.g. by an emulator
    // opened outside Voltcap, is only seen at the next check.)
    SyncState.SYNCED -> if (status.lastDirection == com.shelf.launcher.data.SyncDirection.UPLOAD && status.lastTransferAt > 0L && status.lastTransferAt == status.lastSuccessAt)
        "Last save uploaded and confirmed on Google Drive" else "Matched Google Drive at the last check"
    SyncState.PENDING -> if (status.lastSuccessAt > 0L) "Changes waiting to sync" else "Not synced yet"
    SyncState.UPLOADING -> "Sending saves to Google Drive…"
    SyncState.DOWNLOADING -> "Getting the newest saves from Google Drive…"
    SyncState.ERROR -> "The last sync didn't work"
    SyncState.CONFLICT -> "Saves differ on this device and in Drive"
    SyncState.NEEDS_SIGN_IN -> "Google Drive needs connecting again"
    SyncState.OFFLINE -> "Can't reach Google Drive right now"
}

/**
 * Save sync — <Game>: the big animation, what happened last, and the actions. Live: reads the status
 * store and the running flag itself, so it updates while a manual sync runs.
 */
@Composable
fun SaveSyncDialog(
    gameKey: String,
    gameName: String,
    detailLine: String?,
    onSyncNow: () -> Unit,
    onResolveConflict: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    /** Update 529c: the game, for "Earlier versions" (null hides it). */
    scoped: CloudSync.ScopedGame? = null
) {
    var showVersions by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (showVersions && scoped != null) { EarlierVersionsDialog(scoped, gameName) { showVersions = false }; return }
    val all by SyncStatusStore.all.collectAsState()
    val sync by CloudSync.state.collectAsState()
    val status = all[gameKey]
    val busy = sync.running || status?.state == SyncState.UPLOADING || status?.state == SyncState.DOWNLOADING
    val conflict = status?.state == SyncState.CONFLICT
    ShelfDialog(
        icon = Icons.Filled.CloudSync,
        title = "Save sync — $gameName",
        onDismiss = onDismiss,
        footer = {
            // (Update 213: says what it syncs — this game only, not everything.)
            Chip(if (busy) "Syncing…" else "Sync this game", enabled = !busy) { onSyncNow() }
            if (conflict) Chip("Resolve conflict", accent = false) { onResolveConflict() }
            if (scoped != null) Chip("Earlier versions", accent = false) { showVersions = true }
            Chip("Open Sync settings", accent = false) { onOpenSettings() }
        }
    ) {
        ShelfCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SyncAnimation(status?.state ?: SyncState.PENDING, 64.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stateTitle(status), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    status?.message?.takeIf {
                        // (Mid-game sync, update 529: the step a save is at - found, settling, waiting to upload, newer on Drive, not
                        // written to disk yet - shows under the title too.)
                        status.state in setOf(SyncState.ERROR, SyncState.OFFLINE, SyncState.CONFLICT, SyncState.NEEDS_SIGN_IN, SyncState.PENDING, SyncState.UPLOADING) ||
                            it == com.shelf.launcher.data.SaveObserver.NOT_WRITTEN_YET
                    }?.let { MutedNote(it) }
                }
            }
        }
        val last = status?.lastSuccessAt?.takeIf { it > 0L }
        if (last != null) {
            ShelfCard {
                SectionLabel("Last successful sync")
                val stamp = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                    .format(java.util.Date(last))
                val dir = when (status.lastDirection) {
                    SyncDirection.UPLOAD -> "Sent to Drive"
                    SyncDirection.DOWNLOAD -> "Received from Drive"
                    SyncDirection.NONE -> "Checked"
                }
                val files = if (status.fileCount > 0) " · ${status.fileCount} file${if (status.fileCount == 1) "" else "s"}" else ""
                Text("$stamp (${syncAgo(last)})", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                MutedNote(dir + files)
            }
        }
        if (!detailLine.isNullOrBlank()) {
            ShelfCard { MutedNote(detailLine) }
        }
    }
}

/**
 * Google Drive at a glance in the R1 panel (the per-game badge used to sit in the details window):
 * what the last sync did, Sync now, the focused game's save status, and a way into Settings › Sync.
 * Shows nothing until Drive is connected.
 */
@Composable
fun SyncQuickCard(
    /** The game the home screen is on, when its saves can sync; null hides its row. */
    gameKey: String?,
    gameName: String?,
    onOpenGameSync: (() -> Unit)?,
    onOpenSettings: () -> Unit
) {
    val sync by CloudSync.state.collectAsState()
    val s = sync.settings
    if (!s.connected) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val all by SyncStatusStore.all.collectAsState()
    val overall = when {
        sync.needsSignIn -> SyncState.NEEDS_SIGN_IN
        sync.running -> SyncState.UPLOADING
        sync.conflicts.isNotEmpty() -> SyncState.CONFLICT
        s.lastSyncAt > 0L -> SyncState.SYNCED
        else -> SyncState.PENDING
    }
    ShelfCard {
        SectionLabel("Google Drive")
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SyncAnimation(overall, 22.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    sync.authProblem != null -> "This build isn't registered with Google (error 10) — see Settings › Sync"
                    sync.needsSignIn -> "Google wants you to confirm access again"
                    sync.running -> sync.progress ?: "Syncing…"
                    sync.conflicts.isNotEmpty() -> "${sync.conflicts.size} save${if (sync.conflicts.size == 1) "" else "s"} differ — choose which to keep"
                    s.lastSyncAt > 0L -> "Last sync ${syncAgo(s.lastSyncAt)}" + (s.lastResult?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                    else -> "Connected. Not synced yet."
                },
                color = ShelfTheme.colors.textPrimary,
                fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp),
                modifier = Modifier.weight(1f)
            )
        }
        if (gameKey != null && gameName != null && onOpenGameSync != null) {
            Spacer(Modifier.height(8.dp))
            SaveSyncBadge(
                status = all[gameKey],
                connected = true,
                onClick = onOpenGameSync
            )
            Spacer(Modifier.height(2.dp))
            MutedNote(gameName)
        }
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            Chip(if (sync.running) "Syncing…" else "Sync now", enabled = !sync.running) {
                // CloudSync's own scope: closing the panel mustn't cancel a sync halfway.
                CloudSync.syncInBackground(context)
            }
            if (sync.conflicts.isNotEmpty() && !sync.running) {
                Chip("Choose saves", accent = false) { CloudSync.unsnoozeConflicts() }
            }
            Chip("Sync settings", accent = false, onClick = onOpenSettings)
        }
    }
}


/**
 * Update 529c (save sync, risk 5 - rollback was manual): the kept earlier versions of this game's save files (Voltcap/Sync backups,
 * each verified when it was made), newest first, and Restore - only after a confirmation that says what happens. Controller: every
 * row's Restore is a chip (D-pad + A); B closes the confirmation first, then the list.
 */
@Composable
internal fun EarlierVersionsDialog(game: CloudSync.ScopedGame, gameName: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var versions by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<CloudSync.EarlierVersion>?>(null) }
    var confirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<CloudSync.EarlierVersion?>(null) }
    var result by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var busy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(game.key, result) {
        versions = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { CloudSync.earlierVersions(context, game) }.getOrDefault(emptyList())
        }
    }
    val stamp = androidx.compose.runtime.remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT) }
    confirm?.let { v ->
        ShelfDialog(
            icon = Icons.Filled.CloudSync, title = "Restore this version?", subtitle = v.rel.substringAfterLast('/'),
            onDismiss = { confirm = null },
            footer = {
                Chip("Cancel", accent = false) { confirm = null }
                Chip(if (busy) "Restoring…" else "Restore", enabled = !busy) {
                    busy = true
                    scope.launch {
                        result = CloudSync.restoreEarlierVersion(context, game, v).summary
                        busy = false; confirm = null
                    }
                }
            }
        ) {
            ShelfCard {
                Text("Kept ${stamp.format(java.util.Date(v.version.createdAt))}" + (v.version.meta?.device?.takeIf { it.isNotBlank() }?.let { " on $it" } ?: ""),
                    color = Color.White, fontSize = 14.sp)
                v.version.meta?.reason?.takeIf { it.isNotBlank() }?.let { MutedNote("Why it was kept: $it") }
            }
            MutedNote("The save on this device now is backed up first, so this can be undone the same way. The game's next sync sends " +
                "the restored save to Google Drive, and your other handhelds get it like any newer save (with their own backup).")
        }
        return
    }
    ShelfDialog(icon = Icons.Filled.CloudSync, title = "Earlier versions — $gameName", onDismiss = onDismiss,
        footer = { Chip("Close", accent = false, onClick = onDismiss) }) {
        result?.let { ShelfCard { MutedNote(it) } }
        val list = versions
        when {
            list == null -> MutedNote("Looking…")
            list.isEmpty() -> MutedNote("No earlier versions are kept for this game yet. Voltcap keeps one each time Google Drive's " +
                "version replaces a save here, and before every restore. Versions kept on Google Drive itself are under \"Manage versions\" in Drive.")
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { v ->
                    ShelfCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(v.rel, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                MutedNote(stamp.format(java.util.Date(v.version.createdAt)) +
                                    (v.version.meta?.device?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") +
                                    " · ${v.version.file.length()} bytes")
                            }
                            Spacer(Modifier.width(10.dp))
                            Chip("Restore", accent = false) { confirm = v }
                        }
                    }
                }
            }
        }
    }
}
