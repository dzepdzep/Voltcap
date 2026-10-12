package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.ConflictChoice
import com.shelf.launcher.data.SyncConflict
import com.shelf.launcher.ui.theme.ShelfTheme
import java.text.DateFormat
import java.util.Date

private fun whenText(ms: Long): String {
    val today = DateFormat.getDateInstance(DateFormat.SHORT).format(Date())
    val date = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(ms))
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
    return if (date == today) "today $time" else "$date $time"
}

/**
 * Saves that changed on this device and on another one since the last sync.
 * Nothing has been touched yet; whichever version isn't kept is parked in
 * /Voltcap/Sync conflicts, so no choice here can lose a save.
 *
 * [gameName] is set when a game is waiting to start on the answer.
 */
@Composable
fun SyncConflictDialog(
    conflicts: List<SyncConflict>,
    gameName: String?,
    onChoice: (ConflictChoice) -> Unit,
    onLater: () -> Unit
) {
    val thisDevice = android.os.Build.MODEL
    val allNewerHere = conflicts.all { it.localNewer }
    val allNewerThere = conflicts.none { it.localNewer }
    ShelfDialog(
        icon = Icons.Filled.SyncProblem,
        title = if (conflicts.size == 1) "This save changed on two devices" else "${conflicts.size} saves changed on two devices",
        subtitle = if (gameName != null) "Before $gameName starts, pick which to keep." else "Pick which to keep.",
        onDismiss = onLater,
        footer = {
            Chip(if (gameName != null) "Cancel" else "Decide later", accent = false, onClick = onLater)
            if (!allNewerHere && !allNewerThere) {
                Chip("Newest of each", accent = false) { onChoice(ConflictChoice.NEWEST) }
            }
            Chip(
                "Keep $thisDevice's" + if (allNewerHere) " (newer)" else "",
                accent = allNewerHere
            ) { onChoice(ConflictChoice.KEEP_LOCAL) }
            Chip(
                "Use Drive's" + if (allNewerThere) " (newer)" else "",
                accent = !allNewerHere
            ) { onChoice(ConflictChoice.USE_DRIVE) }
        }
    ) {
        ShelfCard {
            MutedNote(
                "You probably played on another device while this one was offline, or this is the first sync " +
                    "and both devices already had saves. The version you don't keep is saved in " +
                    "Voltcap/Sync conflicts — nothing gets lost."
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                conflicts.take(6).forEach { c ->
                    Column {
                        Text(
                            c.relativePath.substringAfterLast('/'),
                            color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${c.folderName} · here: ${whenText(c.localModified)}" +
                                (if (c.localNewer) " (newer)" else "") +
                                " · Drive${c.remoteDevice?.let { " (from $it)" } ?: ""}: ${whenText(c.remoteModified)}" +
                                (if (!c.localNewer) " (newer)" else ""),
                            color = ShelfTheme.textMuted, fontSize = 12.sp
                        )
                    }
                }
                if (conflicts.size > 6) MutedNote("…and ${conflicts.size - 6} more.")
            }
        }
    }
}

/** Starting a game, Google Drive couldn't be checked for newer saves. */
@Composable
fun SyncOfflineDialog(
    gameName: String,
    reason: String,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    ShelfDialog(
        icon = Icons.Filled.CloudOff,
        title = "Couldn't check for newer saves",
        subtitle = reason,
        onDismiss = onCancel,
        footer = {
            Chip("Cancel", accent = false, onClick = onCancel)
            Chip("Try again", accent = false, onClick = onRetry)
            Chip("Play anyway", onClick = onPlay)
        }
    ) {
        ShelfCard {
            MutedNote(
                "If you played $gameName on another device since the last sync, that progress isn't on this one yet. " +
                    "Playing anyway is safe: if both devices end up with different saves, Voltcap asks which to keep " +
                    "at the next sync instead of overwriting either."
            )
        }
    }
}

/**
 * A small pill in a corner while Google Drive sync is doing something —
 * "Checking Google Drive for newer saves…", "RetroArch saves: 3/40", the
 * result for a few seconds. Never in the middle of the screen, never blocks.
 */
@Composable
fun SyncCornerNotice(
    notice: String?,
    modifier: Modifier = Modifier,
    /** What the notice is about: picks its colour, icon and sound (null: progress or a plain note). */
    kind: com.shelf.launcher.data.SyncNoticeKind? = null
) {
    val sync by com.shelf.launcher.data.CloudSync.state.collectAsState()
    val text = notice ?: if (sync.running) (sync.progress ?: "Syncing…") else null
    val sounds = LocalUiSounds.current
    // A soft chime when saves are safe, a gentle two-note cue when something needs you — once per notice.
    androidx.compose.runtime.LaunchedEffect(notice, kind) {
        if (notice == null || kind == null) return@LaunchedEffect
        sounds?.play(
            if (kind == com.shelf.launcher.data.SyncNoticeKind.PROBLEM) UiSound.SYNC_ALERT else UiSound.SYNC_DONE
        )
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = text != null,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) +
            androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(220)) { it / 3 },
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(220)) +
            androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(220)) { it / 4 },
        modifier = modifier
    ) {
        // Keeps the last text and look while fading out.
        // Plain holder (not state), updated before it's read: no state write during composition.
        val last = androidx.compose.runtime.remember { arrayOf<Any?>("", null) }
        if (text != null) { last[0] = text; last[1] = if (notice != null) kind else null }
        val shown = last[0] as String
        val shownKind = last[1] as com.shelf.launcher.data.SyncNoticeKind?
        val running = sync.running && notice == null
        val tint = when (shownKind) {
            com.shelf.launcher.data.SyncNoticeKind.DONE -> SyncGood
            com.shelf.launcher.data.SyncNoticeKind.RESTORED -> SyncRestored
            com.shelf.launcher.data.SyncNoticeKind.PROBLEM -> SyncWarn
            null -> ShelfTheme.accent
        }
        val title = shown.substringBefore(" — ")
        val detail = shown.substringAfter(" — ", "").takeIf { it.isNotBlank() }
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        androidx.compose.foundation.layout.Row(
            Modifier
                .widthIn(max = 340.dp)
                .clip(shape)
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(tint.copy(alpha = 0.22f), androidx.compose.ui.graphics.Color(0xE6101014))
                    )
                )
                .border(1.dp, tint.copy(alpha = 0.35f), shape)
                .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            if (running) {
                // Which way the files are going: a game's own status says so; otherwise the wording does.
                val all by com.shelf.launcher.data.SyncStatusStore.all.collectAsState()
                val moving = all.values.filter {
                    it.state == com.shelf.launcher.data.SyncState.UPLOADING || it.state == com.shelf.launcher.data.SyncState.DOWNLOADING
                }.maxByOrNull { it.updatedAt }?.state
                val direction = moving ?: if (sync.progress?.contains("Checking", ignoreCase = true) == true)
                    com.shelf.launcher.data.SyncState.DOWNLOADING else com.shelf.launcher.data.SyncState.UPLOADING
                SyncAnimation(direction, 22.dp)
            } else {
                // A round badge with the result's icon, popping in.
                val pop = androidx.compose.runtime.remember(shown) { androidx.compose.animation.core.Animatable(0.6f) }
                androidx.compose.runtime.LaunchedEffect(shown) {
                    pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 600f))
                }
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .size(26.dp)
                        .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(tint),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        when (shownKind) {
                            com.shelf.launcher.data.SyncNoticeKind.DONE -> Icons.Filled.CloudDone
                            com.shelf.launcher.data.SyncNoticeKind.RESTORED -> Icons.Filled.CloudDownload
                            com.shelf.launcher.data.SyncNoticeKind.PROBLEM -> Icons.Filled.SyncProblem
                            null -> Icons.Filled.CloudSync
                        },
                        null,
                        tint = androidx.compose.ui.graphics.Color(0xFF101014),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    title,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (detail != null) {
                    Text(
                        detail,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Drive sync in the status bar: a small animated cloud and, while something happens, a short line
 * ("Favourites and playtime…", the result for a few seconds). Tap for what happened last.
 */
@Composable
fun SyncStatusChip(
    notice: String?,
    kind: com.shelf.launcher.data.SyncNoticeKind?,
    onClick: () -> Unit
) {
    val sync by com.shelf.launcher.data.CloudSync.state.collectAsState()
    if (!sync.settings.connected) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val sounds = LocalUiSounds.current
    androidx.compose.runtime.LaunchedEffect(notice, kind) {
        if (notice == null) return@LaunchedEffect
        com.shelf.launcher.data.SyncLog.add(context, notice)
        if (kind != null) sounds?.play(
            if (kind == com.shelf.launcher.data.SyncNoticeKind.PROBLEM) UiSound.SYNC_ALERT else UiSound.SYNC_DONE
        )
    }
    val state = when {
        sync.needsSignIn -> com.shelf.launcher.data.SyncState.NEEDS_SIGN_IN
        kind == com.shelf.launcher.data.SyncNoticeKind.PROBLEM -> com.shelf.launcher.data.SyncState.ERROR
        sync.running -> com.shelf.launcher.data.SyncState.UPLOADING
        sync.conflicts.isNotEmpty() -> com.shelf.launcher.data.SyncState.CONFLICT
        sync.settings.lastSyncAt > 0L -> com.shelf.launcher.data.SyncState.SYNCED
        else -> com.shelf.launcher.data.SyncState.PENDING
    }
    // Just the cloud — no text next to it any more (it pushed and squashed the corner player). While
    // saves travel the cloud animates (a spark circling it inside a turning halo of colour); a fresh
    // result shows as its mark for a while. What happened is in the window the cloud opens.
    SyncAnimation(state, 16.dp, Modifier.tapOnly(onClick), fresh = notice != null)
}

/** Tapping the status bar's cloud: Drive at a glance plus the last things sync did. */
@Composable
fun SyncStatusDialog(
    gameKey: String?,
    gameName: String?,
    onOpenGameSync: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    /** The library and cover lookup, for "What moved" (the last sync's games, with their covers). */
    games: List<com.shelf.launcher.data.Game> = emptyList(),
    coverFor: (String) -> String? = { null }
) {
    var showMoved by remember { mutableStateOf(false) }
    val lastFull by com.shelf.launcher.data.CloudSync.lastFullSync.collectAsState()
    val moved = lastFull.changes.values.sumOf { it.size } > 0
    // A small popup (not a full window): where Drive stands, Sync now, Browse saves, and the last few events.
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { com.shelf.launcher.data.SyncLog.load(context) }
    val log by com.shelf.launcher.data.SyncLog.entries.collectAsState()
    val sync by com.shelf.launcher.data.CloudSync.state.collectAsState()
    val all by com.shelf.launcher.data.SyncStatusStore.all.collectAsState()
    val s = sync.settings
    val sounds = LocalUiSounds.current
    androidx.compose.runtime.LaunchedEffect(Unit) { sounds?.play(UiSound.OPEN) }
    val overall = when {
        sync.needsSignIn -> com.shelf.launcher.data.SyncState.NEEDS_SIGN_IN
        sync.running -> com.shelf.launcher.data.SyncState.UPLOADING
        sync.conflicts.isNotEmpty() -> com.shelf.launcher.data.SyncState.CONFLICT
        s.lastSyncAt > 0L -> com.shelf.launcher.data.SyncState.SYNCED
        else -> com.shelf.launcher.data.SyncState.PENDING
    }
    val line = when {
        !s.connected -> "Not connected"
        sync.authProblem != null -> "This build isn't registered with Google (error 10) — see Settings › Sync"
        sync.needsSignIn -> "Google wants you to confirm access again"
        sync.running -> sync.progress ?: "Syncing…"
        sync.conflicts.isNotEmpty() -> "${sync.conflicts.size} save${if (sync.conflicts.size == 1) "" else "s"} differ"
        s.lastSyncAt > 0L -> "Last sync ${syncAgo(s.lastSyncAt)}"
        else -> "Connected · not synced yet"
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { sounds?.play(UiSound.BACK); onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ImmersiveDialogWindow()
        Column(
            Modifier
                .widthIn(min = 280.dp, max = 380.dp)
                .clip(shape)
                .background(ShelfTheme.colors.surface)
                .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f), shape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                SyncAnimation(overall, 22.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Google Drive", color = ShelfTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        line, color = ShelfTheme.textMuted, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                CloseButton({ sounds?.play(UiSound.BACK); onDismiss() }, size = 28.dp)
            }
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BrowseSavesButton(onClick = { SaveExplorerHook.request() })
                if (s.connected) Chip(if (sync.running) "Syncing…" else "Sync now", accent = false, enabled = !sync.running) {
                    com.shelf.launcher.data.CloudSync.syncInBackground(context)
                }
                Spacer(Modifier.weight(1f))
                Chip("⚙", accent = false, onClick = onOpenSettings)
            }
            if (sync.conflicts.isNotEmpty() && !sync.running) {
                Chip("Choose which saves to keep", accent = true) { com.shelf.launcher.data.CloudSync.unsnoozeConflicts() }
            }
            if (gameKey != null && gameName != null && onOpenGameSync != null) {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    SaveSyncBadge(status = all[gameKey], connected = true, onClick = onOpenGameSync)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        gameName, color = ShelfTheme.textMuted, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                }
            }
            if (moved) {
                Chip("What moved last sync", accent = false) { showMoved = true }
            }
            if (log.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    log.take(3).forEachIndexed { i, e ->
                        // The newest "… downloaded / uploaded" line opens what moved.
                        val opens = i == 0 && moved && (e.text.contains("downloaded") || e.text.contains("uploaded"))
                        androidx.compose.foundation.layout.Row(
                            if (opens) Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).shelfClickable(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)) { showMoved = true }
                            else Modifier
                        ) {
                            Text(syncAgo(e.at), color = ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), modifier = Modifier.width(72.dp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(
                                e.text + if (opens) "  ›" else "", color = if (opens) ShelfTheme.accent else ShelfTheme.colors.textPrimary, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
    if (showMoved) SyncMovedDialog(lastFull, games, coverFor) { showMoved = false }
}

/**
 * What the last sync moved, game by game: covers drop one after another into glowing capsules (the
 * Voltcap capsule), the ones that came down from Drive first, then what went up; anything that isn't a
 * game's save is counted at the end.
 */
@Composable
private fun SyncMovedDialog(
    result: com.shelf.launcher.data.CloudSync.FullSyncResult,
    games: List<com.shelf.launcher.data.Game>,
    coverFor: (String) -> String?,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sounds = LocalUiSounds.current
    val uploads = remember(result) {
        result.changes.mapValues { (folder, paths) -> paths - result.downloads[folder].orEmpty().toSet() }.filterValues { it.isNotEmpty() }
    }
    // Matching files to games walks the whole library: off the main thread.
    val counts by androidx.compose.runtime.produceState<Pair<Map<String, Int>, Map<String, Int>>?>(null, result, games) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            com.shelf.launcher.data.CloudSync.fileCountsTouchedBy(result.downloads, games) to
                com.shelf.launcher.data.CloudSync.fileCountsTouchedBy(uploads, games)
        }
    }
    val down = counts?.first.orEmpty()
    val up = counts?.second.orEmpty()
    val tooMany = games.size > 2000
    val byKey = remember(games) { games.associateBy { it.stableKey } }
    val other = (result.changes.values.sumOf { it.size } - down.values.sum() - up.values.sum()).coerceAtLeast(0)
    val rows = down.map { Triple(it.key, it.value, true) } + up.map { Triple(it.key, it.value, false) }
    val anims = remember(result.serial, counts != null) { List(rows.size) { androidx.compose.animation.core.Animatable(0f) } }
    androidx.compose.runtime.LaunchedEffect(result.serial, counts != null) {
        if (counts == null) return@LaunchedEffect
        RetroFx.warm(context)
        anims.forEachIndexed { i, a ->
            launch { a.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 300f)) }
            kotlinx.coroutines.delay(150)
            if (i < 12) RetroFx.play(context, sounds, RetroSound.LAND, pitch = if (rows[i].third) 1f else 1.25f)
        }
    }
    ShelfDialog(
        icon = androidx.compose.material.icons.Icons.Filled.CloudDownload,
        title = "What moved",
        subtitle = listOfNotNull(
            down.values.sum().takeIf { it > 0 }?.let { "$it down" },
            up.values.sum().takeIf { it > 0 }?.let { "$it up" },
            other.takeIf { it > 0 }?.let { "$it other files" }
        ).joinToString(" · ").ifBlank { "Last sync" },
        onDismiss = onDismiss,
        widthFraction = 0.7f
    ) {
        when {
            counts == null -> MutedNote("Matching files to games…")
            tooMany -> MutedNote("Your library is too big to match files to games here — the files are counted above.")
            rows.isEmpty() -> MutedNote("No game saves moved — only Voltcap's own data (favourites, play time, settings).")
        }
        rows.forEachIndexed { i, (key, n, isDown) ->
            val p = anims.getOrNull(i)?.value ?: 1f
            val capsule = androidx.compose.foundation.shape.RoundedCornerShape(50)
            val tint = if (isDown) ShelfTheme.accent else ShelfTheme.colors.secondary
            androidx.compose.foundation.layout.Row(
                Modifier
                    .padding(vertical = 4.dp)
                    .graphicsLayer {
                        // Drops in from above (down) or rises from below (up), into place.
                        translationY = (1f - p) * (if (isDown) -160f else 160f)
                        alpha = p.coerceIn(0f, 1f)
                    }
                    .fillMaxWidth()
                    .clip(capsule)
                    .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(tint.copy(alpha = 0.35f), tint.copy(alpha = 0.08f))))
                    .border(1.dp, tint.copy(alpha = 0.6f), capsule)
                    .shelfFocusable(capsule)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                val cover = coverFor(key)
                Box(Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tint.copy(alpha = 0.3f))) {
                    if (cover != null) coil.compose.AsyncImage(cover, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    byKey[key]?.name?.let { gameDisplayName(it) } ?: key.substringAfterLast('/'),
                    color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.SemiBold, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Text(
                    (if (isDown) "↓ " else "↑ ") + "$n file${if (n == 1) "" else "s"}",
                    color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp)
                )
            }
        }
    }
}
