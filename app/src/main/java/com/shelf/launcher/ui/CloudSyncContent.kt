package com.shelf.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.shelf.launcher.data.CloudSync
import com.shelf.launcher.data.SyncFolder
import com.shelf.launcher.data.isStateFolder
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Settings › Sync: Google Drive for emulator saves and Voltcap's game data. */
@Composable
internal fun CloudSyncContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { CloudSync.load(context) }
    val state by CloudSync.state.collectAsState()
    val s = state.settings
    var message by remember { mutableStateOf<String?>(null) }
    var pickingFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SyncFolder?>(null) }
    var showConflicts by remember { mutableStateOf(false) }
    var showDolphinHelp by remember { mutableStateOf(false) }
    // Another app's folder (Dolphin…) through Android's own picker.
    // Picking a new place for a folder: a normal one…
    var relocating by remember { mutableStateOf<SyncFolder?>(null) }
    // …or one shared by another app (picked again in Android's picker).
    var relocatingTree by remember { mutableStateOf<SyncFolder?>(null) }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val moving = relocatingTree
        relocatingTree = null
        when {
            uri == null -> Unit
            moving != null -> {
                message = if (CloudSync.keepAccess(context, uri)) {
                    CloudSync.changeLocation(context, moving, moving.path, uri.toString())
                    "${moving.name} now uses the folder you picked."
                } else "Android didn't allow Voltcap to keep access to that folder."
            }
            else -> scope.launch { message = CloudSync.addSharedFolder(context, uri) }
        }
    }

    val signIn = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        scope.launch {
            message = CloudSync.finishConnect(context, result.data) ?: "Connected — syncing now."
        }
    }
    val connect: () -> Unit = {
        scope.launch {
            message = null
            runCatching { CloudSync.connect(context) }
                .onSuccess { pending ->
                    if (pending != null) {
                        signIn.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    } else {
                        message = "Connected — syncing now."
                    }
                }
                .onFailure { message = com.shelf.launcher.data.DriveErrors.explain(it) }
        }
    }

    // Update 208: another Google account, picked from Android's own list of the accounts on this device.
    val pickAccount = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val email = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME) ?: return@rememberLauncherForActivityResult
        scope.launch {
            message = "Switching to $email…"
            runCatching { CloudSync.switchAccount(context, email) }
                .onSuccess { pending ->
                    if (pending != null) signIn.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    else message = "Now syncing with $email."
                }
                .onFailure { message = com.shelf.launcher.data.DriveErrors.explain(it) }
        }
    }
    val chooseAccount: () -> Unit = {
        val current = state.account ?: com.shelf.launcher.data.DriveAuth.chosenAccount(context)
        runCatching {
            pickAccount.launch(
                android.accounts.AccountManager.newChooseAccountIntent(
                    current?.let { android.accounts.Account(it, "com.google") }, null, arrayOf("com.google"),
                    null, null, null, null
                )
            )
        }.onFailure { message = "Android's account list couldn't be opened here." }
    }

    // ---- Account ----
    ShelfCard {
        SectionLabel("Google Drive")
        MutedNote(
            when {
                !s.connected -> "Keeps your saves, favourites and playtime the same on every device you play on. " +
                    "Voltcap only sees the \"Voltcap Sync\" folder it makes in your Drive — nothing else there."
                state.authProblem != null -> state.authProblem.orEmpty()
                state.needsSignIn -> "Google wants you to confirm access again."
                state.running -> state.progress ?: "Syncing…"
                s.lastSyncAt > 0 -> "Last sync " +
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s.lastSyncAt)) +
                    ": " + (s.lastResult ?: "")
                else -> "Connected. Not synced yet."
            }
        )
        // Update 208: which Google account it syncs with (it never said, and couldn't be changed).
        if (s.connected) {
            Spacer(Modifier.height(6.dp))
            MutedNote(
                "Account: " + (state.account ?: com.shelf.launcher.data.DriveAuth.chosenAccount(context)
                    ?: "not known yet — shown after the next sync")
            )
        }
        Spacer(Modifier.height(10.dp))
        ChipFlow {
            if (!s.connected || state.needsSignIn) {
                Chip(if (state.needsSignIn) "Reconnect Google Drive" else "Connect Google Drive", onClick = connect)
            }
            if (s.connected) {
                Chip(if (state.running) "Syncing…" else "Sync now", enabled = !state.running) {
                    scope.launch { message = CloudSync.sync(context).summary }
                }
                if (state.running) Chip("Stop", accent = false) { CloudSync.stop() }
                Chip("Switch account", accent = false, enabled = !state.running, onClick = chooseAccount)
                BrowseSavesButton(onClick = { SaveExplorerHook.request() })
                Chip("Clean up Drive", accent = false, enabled = !state.running) {
                    scope.launch { message = CloudSync.cleanUpDrive(context).summary }
                }
                Chip("Disconnect", accent = false) {
                    CloudSync.disconnect(context)
                    message = "Disconnected. Your files stay in Drive."
                }
            }
        }
        message?.let {
            Spacer(Modifier.height(8.dp))
            MutedNote(it)
        }
    }

    if (state.conflicts.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        ShelfCard {
            SectionLabel("${state.conflicts.size} save${if (state.conflicts.size == 1) "" else "s"} to decide on")
            MutedNote("Changed on this device and on another one. Left as they are until you pick.")
            Spacer(Modifier.height(10.dp))
            Chip("Choose which to keep", enabled = !state.running) { showConflicts = true }
        }
    }
    if (showConflicts && state.conflicts.isNotEmpty() && !state.running) {
        SyncConflictDialog(
            conflicts = state.conflicts,
            gameName = null,
            onChoice = { choice ->
                showConflicts = false
                scope.launch { message = CloudSync.resolveConflicts(context, choice).summary }
            },
            onLater = { showConflicts = false }
        )
    }

    Spacer(Modifier.height(12.dp))
    SwitchRow(
        Icons.Filled.SaveAlt, "Sync game saves",
        "In-game saves from the folders below (save states are always left out). Changed on one device → copied to the other. Changed on both → Voltcap asks which " +
            "to keep and parks the other in Voltcap/Sync conflicts. Deleting a save never deletes it on other devices.",
        s.syncSaves,
        onChange = { on -> CloudSync.updateSettings(context) { it.copy(syncSaves = on) } }
    )
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.History, "Fetch saves before playing",
        "When you open a game's details, its newest saves come down from Drive in the background — the game itself " +
            "never waits. Saves go up when you come back to Voltcap (close the game in the emulator first).",
        s.syncAroundGames,
        onChange = { on -> CloudSync.updateSettings(context) { it.copy(syncAroundGames = on) } }
    )
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.Star, "Sync favourites and playtime",
        "Also recently played, artwork picks and manual IDs. Merged, not replaced: playtime adds up across devices. " +
            "Games match when they sit at the same place inside the ROM folder on each device.",
        s.syncLauncherData,
        onChange = { on -> CloudSync.updateSettings(context) { it.copy(syncLauncherData = on) } }
    )
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.History, "Last played on every device",
        "A game you played on another device moves to the front here too. One tiny file on Drive, checked when " +
            "Voltcap opens, after you finish a game and at most every 20 minutes — never during a game. Games match by " +
            "console and ROM file name, so the folders can differ between devices.",
        com.shelf.launcher.data.Features.syncLastPlayed.state.value,
        onChange = { on -> com.shelf.launcher.data.Features.syncLastPlayed.value = on }
    )

    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.Schedule, "Sync in the background",
        "Every ${s.backgroundEvery} hours, even when Voltcap isn't open (with a network and enough battery). " +
            "Saves are left alone while a game is running.",
        !s.backgroundOff,
        onChange = { on -> CloudSync.updateSettings(context) { it.copy(backgroundOff = !on) } }
    )
    if (!s.backgroundOff) {
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            listOf(3, 6, 12, 24).forEach { h ->
                Chip("Every $h h", accent = s.backgroundEvery == h) {
                    CloudSync.updateSettings(context) { it.copy(backgroundHours = h) }
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.Notifications, "Sync notifications",
        "A quiet note when saves were backed up or restored while Voltcap wasn't open, or when Drive needs attention.",
        !s.syncNotificationsOff,
        onChange = { on -> CloudSync.updateSettings(context) { it.copy(syncNotificationsOff = !on) } }
    )

    // ---- Folders ----
    Spacer(Modifier.height(12.dp))
    ShelfCard {
        SectionLabel("Save folders")
        MutedNote(
            "Folders with the same name sync together, wherever they are on each device — " +
                "rename one to match if the automatic names differ."
        )
        // The ARMSX2 check: installed, but its memory cards out of reach (private folder) or not added.
        val armsx2Note = remember(s.folders) {
            runCatching { com.shelf.launcher.data.armsx2Problem(context, s.folders) }.getOrNull()
        }
        if (armsx2Note != null) {
            Spacer(Modifier.height(8.dp))
            MutedNote("⚠ $armsx2Note")
        }
        Spacer(Modifier.height(10.dp))
        if (s.folders.isEmpty()) EmptyState("No folders yet. Add one below.")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.folders.forEach { folder ->
                val exists = remember(folder) { CloudSync.isReachable(context, folder) }
                // The row opens rename / pause / remove; "Change" picks another location.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        MenuRow(
                            if (folder.enabled && exists) Icons.Filled.Folder else Icons.Filled.FolderOff,
                            folder.name + if (!folder.enabled) " (paused)" else "",
                            folder.label + if (!exists) " — not found here, press Change" else ""
                        ) { renaming = folder }
                    }
                    Spacer(Modifier.width(8.dp))
                    Chip("Change", accent = !exists) {
                        if (folder.treeUri != null) {
                            relocatingTree = folder
                            pickTree.launch(null)
                        } else relocating = folder
                    }
                }
            }
        }
        val suggestions = remember(s.folders) { CloudSync.suggestedFolders() }
        val dolphin = remember(s.folders) { CloudSync.suggestDolphin(context) }
        if (suggestions.isNotEmpty() || dolphin) {
            Spacer(Modifier.height(12.dp))
            Text("Found on this device", color = ShelfTheme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                suggestions.forEach { preset ->
                    Chip("+ ${preset.name}", accent = false) {
                        CloudSync.updateSettings(context) { it.copy(folders = it.folders + preset) }
                    }
                }
                if (dolphin) Chip("+ Dolphin (GameCube & Wii)", accent = false) { showDolphinHelp = true }
            }
        }
        Spacer(Modifier.height(12.dp))
        ChipFlow {
            Chip("Add folder…") { pickingFolder = true }
            Chip("Add from another app…", accent = false) { pickTree.launch(null) }
        }
        MutedNote("Save states are never synced — only in-game saves and memory cards.")
    }

    if (showDolphinHelp) {
        ShelfDialog(
            icon = Icons.Filled.CloudSync,
            title = "Add Dolphin's saves",
            subtitle = "Dolphin keeps them in Android/data, which Android only opens through its own picker.",
            onDismiss = { showDolphinHelp = false },
            footer = {
                Chip("Cancel", accent = false) { showDolphinHelp = false }
                Chip("Open the picker") {
                    showDolphinHelp = false
                    pickTree.launch(null)
                }
            }
        ) {
            ShelfCard {
                MutedNote(
                    "1. In the picker, open the menu (☰ top left) and choose Dolphin.\n" +
                        "2. Stay at the top of Dolphin's folder (where GC and Wii are) and press \"Use this folder\".\n" +
                        "3. Allow access.\n\n" +
                        "Voltcap then syncs GameCube memory cards (GC) and Wii saves (Wii/title) — nothing else from Dolphin. " +
                        "If Dolphin isn't in the picker's menu, open Dolphin once and try again."
                )
            }
        }
    }

    if (pickingFolder) {
        FolderBrowserDialog(
            startPath = "/storage/emulated/0",
            title = "Pick a save folder",
            // Save-state folders don't even show up.
            showFolder = { !isStateFolder(it.absolutePath) },
            onSelect = { path ->
                pickingFolder = false
                when {
                    isStateFolder(path) -> message = "That's a save-state folder — states are never synced."
                    s.folders.none { it.path == path } -> {
                        var name = CloudSync.defaultName(path)
                        var n = 2
                        val base = name
                        // Update 144: case doesn't count (as in rename): "saves" and "Saves" would share one Drive folder.
                        while (s.folders.any { it.name.equals(name, ignoreCase = true) }) name = "$base ${n++}"
                        CloudSync.updateSettings(context) {
                            it.copy(folders = it.folders + SyncFolder(name, path, kind = com.shelf.launcher.data.SaveKind.GENERIC.name))
                        }
                    }
                }
            },
            onDismiss = { pickingFolder = false }
        )
    }

    relocating?.let { folder ->
        FolderBrowserDialog(
            startPath = folder.path.takeIf { java.io.File(it).isDirectory }
                ?: java.io.File(folder.path).parentFile?.takeIf { it.isDirectory }?.absolutePath
                ?: "/storage/emulated/0",
            title = "New place for ${folder.name}",
            showFolder = { !isStateFolder(it.absolutePath) },
            onSelect = { path ->
                relocating = null
                message = if (isStateFolder(path)) "That's a save-state folder — states are never synced."
                else {
                    CloudSync.changeLocation(context, folder, path)
                    "${folder.name} now syncs $path."
                }
            },
            onDismiss = { relocating = null }
        )
    }

    renaming?.let { folder ->
        var name by remember(folder) { mutableStateOf(folder.name) }
        // Two folders on this device with one name would share one Drive folder and mix their saves.
        val clash = s.folders.any { it != folder && it.name.equals(name.trim().replace("/", "-"), ignoreCase = true) }
        fun replace(replacement: SyncFolder?) {
            CloudSync.updateSettings(context) { st ->
                st.copy(folders = st.folders.mapNotNull { if (it == folder) replacement else it })
            }
            renaming = null
        }
        ShelfDialog(
            icon = Icons.Filled.CloudSync,
            title = folder.name,
            subtitle = folder.label,
            onDismiss = { renaming = null },
            footer = {
                Chip("Remove", accent = false) { replace(null) }
                Chip(if (folder.enabled) "Pause" else "Resume", accent = false) {
                    replace(folder.copy(enabled = !folder.enabled))
                }
                Chip("Save", enabled = name.isNotBlank() && !clash) {
                    val clean = name.trim().replace("/", "-")
                    replace(folder.copy(name = clean))
                }
            }
        ) {
            ShelfCard {
                ShelfTextField(value = name, onValueChange = { name = it }, label = "Name in Google Drive")
                if (clash) {
                    Spacer(Modifier.height(6.dp))
                    MutedNote("Another folder on this device already has that name: both would go into one Drive folder. Pick another name.")
                }
                Spacer(Modifier.height(8.dp))
                MutedNote(
                    "Give the same folder on your other device the same name. Removing it only stops syncing — " +
                        "no files are deleted, here or in Drive."
                )
            }
        }
    }
}
