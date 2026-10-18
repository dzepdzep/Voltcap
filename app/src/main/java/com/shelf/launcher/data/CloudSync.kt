package com.shelf.launcher.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.tasks.Tasks
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** A folder of saves on this device, synced with the Drive folder of the same
 * [name] — so "RetroArch saves" on two handhelds meet in one place even when
 * the paths differ. */
data class SyncFolder(
    val name: String,
    /** A normal folder's full path — or, with [treeUri], the part inside the
     * shared tree ("GC", "Wii/title"; empty for the whole tree). */
    val path: String,
    val enabled: Boolean = true,
    /** Set for a folder another app shares through Android's picker (Dolphin). */
    val treeUri: String? = null,
    /** Which files are saves here ([SaveKind] name); null on folders from older versions. */
    val kind: String? = null
) {
    val saveKind: SaveKind get() = SaveKind.of(kind) ?: kindForName(name)

    /** What to show under the name. */
    val label: String
        get() = if (treeUri == null) path else {
            val app = if (treeUri.contains("dolphin", ignoreCase = true)) "Dolphin" else "Shared folder"
            if (path.isEmpty()) app else "$app › $path"
        }
}

data class CloudSyncSettings(
    val connected: Boolean = false,
    val syncSaves: Boolean = true,
    val syncLauncherData: Boolean = true,
    /** Pull saves right before a game starts (and push them after). */
    val syncAroundGames: Boolean = true,
    val folders: List<SyncFolder> = emptyList(),
    /** Background sync every few hours is on unless this is set (named so
     * settings saved before it existed read as "on"). */
    val backgroundOff: Boolean = false,
    /** Hours between background syncs; 0 = the default (6). */
    val backgroundHours: Int = 0,
    val lastSyncAt: Long = 0L,
    val lastResult: String? = null,
    /** "Saves safe on Google Drive" notifications after background syncs are on unless this is set
     * (negated so settings saved before it existed read as "on"). */
    val syncNotificationsOff: Boolean = false
) {
    val backgroundEvery: Int get() = if (backgroundHours <= 0) 6 else backgroundHours
}

data class CloudSyncState(
    val settings: CloudSyncSettings = CloudSyncSettings(),
    val running: Boolean = false,
    val progress: String? = null,
    /** Google took the Drive permission back (or it was never finished). */
    val needsSignIn: Boolean = false,
    /** Why Google refuses when signing in again can't help (this build isn't registered, error 10). */
    val authProblem: String? = null,
    /** Saves that changed on this device *and* in Drive since the last sync
     * (or differ on the very first sync). Left untouched until the player
     * picks which to keep. */
    val conflicts: List<SyncConflict> = emptyList(),
    /** "Decide later" was pressed: don't ask again until the next sync. */
    val conflictsSnoozed: Boolean = false,
    /** Update 208: the Google account syncing (as Drive reports it), once known. */
    val account: String? = null
)

/** One save that changed on both sides. */
data class SyncConflict(
    val folderName: String,
    val folder: SyncFolder,
    val relativePath: String,
    val localModified: Long,
    val remoteId: String,
    val remoteModified: Long,
    /** Which device uploaded Drive's version, when known. */
    val remoteDevice: String?
) {
    val localNewer: Boolean get() = localModified >= remoteModified
}

enum class ConflictChoice { USE_DRIVE, KEEP_LOCAL, NEWEST }

/** How a sync went. [ok] is false when Drive couldn't be checked fully. */
data class SyncOutcome(
    val ok: Boolean,
    val summary: String,
    val conflicts: Int = 0,
    /** Files sent to / fetched from Drive by this run (scoped syncs fill these in). */
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    /** Failed because of the connection or a Google hiccup: trying again later can work. */
    val retry: Boolean = false,
    /** Google wants the sign-in confirmed again; retrying can't help until then. */
    val needsSignIn: Boolean = false
)

/**
 * Google Drive sync: emulator saves and Voltcap's own per-game data.
 *
 * Drive layout ("Voltcap Sync" in My Drive, visible so saves can be grabbed
 * on a PC too):
 *   Voltcap Sync/launcher-data.json
 *   Voltcap Sync/Saves/<folder name>/<path inside that folder>
 * Files keep their relative path as their Drive name ("GBA/pokemon.srm"),
 * one flat Drive folder per synced save folder.
 *
 * Saves: every file is compared with what it was at the last sync. Changed
 * on one side only → that side wins. Changed on both (or different on the
 * very first sync) → nothing is touched and the player is asked which to
 * keep ([SyncConflict]); the one not kept is parked under
 * /Voltcap/Sync conflicts. Deletions are never copied across: a save gone
 * on one device comes back from Drive rather than being wiped everywhere.
 *
 * Launcher data (favourites, playtime, recently played, artwork picks,
 * manual IDs) is merged, not overwritten — see [mergeLauncherData]. ROM keys
 * are stored relative to the ROM folder, so the same games in a different
 * ROM folder on the other device still match.
 */
object CloudSync {
    private const val ROOT = "Voltcap Sync"
    private const val SAVES = "Saves"
    private const val LAUNCHER_FILE = "launcher-data.json"

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** A sync is running right now (Sleep mode leaves Wi-Fi on then). */
    val busy: Boolean get() = mutex.isLocked
    private var loaded = false

    private val _state = MutableStateFlow(CloudSyncState())
    val state: StateFlow<CloudSyncState> = _state.asStateFlow()

    /** What the last finished full sync moved: folder name → changed relative paths (see [gamesTouchedBy]). */
    data class FullSyncResult(
        val serial: Long = 0L,
        val changes: Map<String, List<String>> = emptyMap(),
        val downloads: Map<String, List<String>> = emptyMap(),
        val conflicts: Map<String, List<String>> = emptyMap(),
        val offline: Boolean = false,
        val needsSignIn: Boolean = false,
        /** False when the pass ended early or had failures for a reason other than offline / sign-in. */
        val ok: Boolean = true
    )
    private val _lastFullSync = MutableStateFlow(FullSyncResult())
    val lastFullSync: StateFlow<FullSyncResult> = _lastFullSync.asStateFlow()
    val lastFullSyncChanges: Map<String, List<String>> get() = _lastFullSync.value.changes

    /** Games (by stableKey) whose save files are among [changes] — the same file matching a scoped sync uses. */
    internal fun gamesTouchedBy(changes: Map<String, List<String>>, games: List<Game>): Set<String> =
        fileCountsTouchedBy(changes, games).keys

    /** Like [gamesTouchedBy], with how many of the changed files belong to each game. */
    internal fun fileCountsTouchedBy(changes: Map<String, List<String>>, games: List<Game>): Map<String, Int> {
        if (changes.isEmpty() || games.size > 2000) return emptyMap()
        val settings = _state.value.settings
        val out = HashMap<String, Int>()
        games.forEach { g ->
            if (g.sourceType != SourceType.ROM) return@forEach
            var n = 0
            scopeFor(settings, ScopedGame.of(g)).forEach { (folder, only) ->
                n += changes[folder.name]?.count(only) ?: 0
            }
            if (n > 0) out[g.stableKey] = n
        }
        return out
    }

    /** Fires after a sync changed favourites/playtime/etc. on this device. */
    private val _launcherDataChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val launcherDataChanged: SharedFlow<Unit> = _launcherDataChanged.asSharedFlow()

    /** Set to make a running sync stop before its next file. */
    @Volatile private var stopRequested = false
    private var backgroundJob: Deferred<SyncOutcome>? = null

    // ---- Settings ----

    private fun prefs(context: Context) = context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val settings = prefs(context).getString("settings_json", null)
            ?.let { runCatching { gson.fromJson(it, CloudSyncSettings::class.java) }.getOrNull() }
            ?: CloudSyncSettings()
        // Gson leaves missing lists null on old data.
        @Suppress("SENSELESS_COMPARISON")
        val folders = (settings.folders ?: emptyList())
            // Older versions offered save-state folders; states are never synced now.
            .filter { !isStateFolder(it.path) && !it.name.contains("states", ignoreCase = true) }
        val safe = settings.copy(folders = folders)
        _state.update { it.copy(settings = safe, needsSignIn = DriveAuth.needsSignIn(context), authProblem = DriveAuth.problem(context), account = DriveAuth.driveAccount(context)) }
    }

    fun updateSettings(context: Context, transform: (CloudSyncSettings) -> CloudSyncSettings) {
        load(context)
        val before = _state.value.settings
        _state.update { it.copy(settings = transform(it.settings)) }
        val after = _state.value.settings
        prefs(context).edit().putString("settings_json", gson.toJson(after)).apply()
        // The background schedule follows these; lastSyncAt etc. don't touch it.
        if (before.connected != after.connected || before.backgroundOff != after.backgroundOff ||
            before.backgroundEvery != after.backgroundEvery || before.syncSaves != after.syncSaves ||
            before.syncLauncherData != after.syncLauncherData
        ) {
            runCatching { CloudSyncWorker.schedule(context) }
        }
    }

    fun isActive(context: Context): Boolean {
        load(context)
        val s = _state.value.settings
        return s.connected && (s.syncSaves || s.syncLauncherData)
    }

    fun shouldSyncAroundGames(context: Context): Boolean {
        load(context)
        val s = _state.value.settings
        return s.connected && s.syncSaves && s.syncAroundGames && s.folders.any { it.enabled }
    }

    /**
     * Update 208: with saves on and no save folder set up at all (a fresh install — the list lives in Voltcap's own data,
     * so reinstalling emptied it), the emulators' save folders found on this device are added: the same ones Settings ›
     * Sync offers under "Found on this device", named as before, so they meet their Drive folders again. Only folders
     * with something in them; done once — removing them all afterwards is left alone. Returns what was added.
     */
    fun autoAddFolders(context: Context): List<SyncFolder> {
        load(context)
        val s = _state.value.settings
        if (!s.connected || !s.syncSaves || s.folders.isNotEmpty()) return emptyList()
        if (prefs(context).getBoolean("auto_folders_208", false)) return emptyList()
        val found = runCatching { presetFolders() }.getOrDefault(emptyList())
            .filter { f -> runCatching { File(f.path).list()?.isNotEmpty() == true }.getOrDefault(false) }
        if (found.isEmpty()) return emptyList()
        prefs(context).edit().putBoolean("auto_folders_208", true).apply()
        updateSettings(context) { it.copy(folders = it.folders + found) }
        runCatching { SyncLog.add(context, "Save folders found and added: " + found.joinToString(", ") { it.name } + ".") }
        return found
    }

    /** Known emulator save folders that exist here and aren't added yet. */
    fun suggestedFolders(): List<SyncFolder> {
        val have = _state.value.settings.folders
        return presetFolders().filter { preset ->
            // Update 144: names compare without case, as Drive folders are found by name.
            have.none { it.path.equals(preset.path, ignoreCase = true) || it.name.equals(preset.name, ignoreCase = true) }
        }
    }

    /** Dolphin is installed, keeps its saves in Android/data, and isn't added yet. */
    fun suggestDolphin(context: Context): Boolean {
        val installed = DOLPHIN_PACKAGES.any { pkg ->
            runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        }
        return installed && _state.value.settings.folders.none { it.name.startsWith("Dolphin") }
    }

    /**
     * A folder picked in Android's picker (another app's files). Dolphin's
     * whole folder becomes two synced folders — GameCube memory cards (GC)
     * and Wii saves (Wii/title) — so shaders, textures and states stay out.
     * Returns what was added, for the status line.
     */
    suspend fun addSharedFolder(context: Context, uri: android.net.Uri): String = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }.onFailure { return@withContext "Android didn't allow Voltcap to keep access to that folder." }
        val tree = DocTree(context, uri, "")
        val top = tree.topFolders()
        val have = _state.value.settings.folders
        val added = mutableListOf<SyncFolder>()
        fun offer(folder: SyncFolder) {
            if (have.none { it.treeUri == folder.treeUri && it.path.equals(folder.path, true) || it.name == folder.name }) {
                added += folder
            }
        }
        val gc = top.firstOrNull { it.equals("GC", true) }
        val wii = top.firstOrNull { it.equals("Wii", true) }
        if (gc != null || wii != null) {
            gc?.let { offer(SyncFolder("Dolphin GameCube", it, treeUri = uri.toString(), kind = SaveKind.DOLPHIN_GC.name)) }
            if (wii != null && DocTree(context, uri, "$wii/title").exists()) {
                offer(SyncFolder("Dolphin Wii", "$wii/title", treeUri = uri.toString(), kind = SaveKind.DOLPHIN_WII.name))
            }
        } else {
            val name = tree.displayName() ?: "Shared folder"
            if (isStateFolder(name)) return@withContext "That's a save-state folder — states are never synced."
            offer(SyncFolder(name, "", treeUri = uri.toString(), kind = SaveKind.GENERIC.name))
        }
        if (added.isEmpty()) return@withContext "Nothing new to add from that folder."
        updateSettings(context) { it.copy(folders = it.folders + added) }
        "Added " + added.joinToString(" and ") { it.name } + "."
    }

    /**
     * Points [folder] somewhere else (a suggested path that's wrong on this
     * device, a moved emulator folder…). Name and save type stay, so it keeps
     * syncing with the same Drive folder; remembered file hashes are dropped
     * so the new place is read fresh. Returns the updated folder.
     */
    fun changeLocation(context: Context, folder: SyncFolder, path: String, treeUri: String? = null): SyncFolder {
        val moved = folder.copy(path = path, treeUri = treeUri)
        updateSettings(context) { st -> st.copy(folders = st.folders.map { if (it == folder) moved else it }) }
        val state = loadFolderState(context, folder.name)
        state.hashes.clear()
        saveFolderState(context, folder.name, state)
        return moved
    }

    /** Takes lasting access to a folder picked in Android's picker. */
    fun keepAccess(context: Context, uri: android.net.Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }.isSuccess

    private fun treeFor(context: Context, folder: SyncFolder): SyncTree =
        if (folder.treeUri != null) DocTree(context, android.net.Uri.parse(folder.treeUri), folder.path)
        else FileTree(File(folder.path))

    /** Whether a folder is reachable right now (for the settings list). */
    fun isReachable(context: Context, folder: SyncFolder): Boolean =
        runCatching { treeFor(context, folder).exists() }.getOrDefault(false)

    /** "…/RetroArch/saves" → "RetroArch saves". */
    fun defaultName(path: String): String {
        val parts = path.trimEnd('/').split('/').filter { it.isNotEmpty() }
        return parts.takeLast(2).joinToString(" ").ifEmpty { "Saves" }
    }

    // ---- Connecting ----

    /**
     * Asks Play Services for Drive access. Returns the sign-in screen to show
     * when the player still has to agree (first time, or access was revoked);
     * null when access was granted straight away.
     */
    suspend fun connect(context: Context): PendingIntent? = withContext(Dispatchers.IO) {
        val result = try {
            Tasks.await(
                Identity.getAuthorizationClient(context).authorize(DriveAuth.request(context)), 60, TimeUnit.SECONDS
            )
        } catch (e: java.util.concurrent.ExecutionException) {
            noteProblem(context, e.cause ?: e)
            throw e.cause ?: e
        }
        if (result.hasResolution()) return@withContext result.pendingIntent
        markConnected(context)
        null
    }

    /**
     * The sign-in screen from [connect] came back. Null when access was granted, otherwise
     * what to tell the player (cancelled, blocked by Google, this build not registered…).
     */
    suspend fun finishConnect(context: Context, data: Intent?): String? = withContext(Dispatchers.IO) {
        if (data == null) return@withContext DriveErrors.NOT_COMPLETED
        val outcome = runCatching {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken != null
        }
        val granted = outcome.getOrDefault(false)
        if (granted) {
            markConnected(context)
            return@withContext null
        }
        outcome.exceptionOrNull()?.let { noteProblem(context, it); DriveErrors.explain(it) } ?: DriveErrors.NOT_COMPLETED
    }

    /** Remembers a refusal that signing in again can't fix, so Settings › Sync can say what's wrong. */
    private fun noteProblem(context: Context, t: Throwable) {
        if (DriveErrors.statusCode(t) == 10) {
            DriveAuth.setProblem(context, DriveErrors.NOT_REGISTERED)
            _state.update { it.copy(authProblem = DriveErrors.NOT_REGISTERED) }
        }
    }

    private fun markConnected(context: Context) {
        DriveAuth.forget()
        DriveAuth.setNeedsSignIn(context, false)
        updateSettings(context) { it.copy(connected = true) }
        _state.update { it.copy(needsSignIn = false, authProblem = null) }
        syncInBackground(context)
    }

    /** Stops syncing on this device. Drive keeps its copy; Google access can be
     * removed at myaccount.google.com › Security › Third-party connections. */
    fun disconnect(context: Context) {
        stopRequested = true
        DriveAuth.forget()
        DriveAuth.setDriveAccount(context, null)
        _state.update { it.copy(account = null) }
        updateSettings(context) { it.copy(connected = false) }
        _state.update { it.copy(needsSignIn = false) }
    }

    /**
     * Update 208: sync with another Google account ([email], picked in Android's account list). What this device knew of
     * the old account's Drive — which files it had, the change feed, the folders' places — is dropped, so the new Drive
     * is read fresh: files that differ become questions (nothing is overwritten), files only here go up. Then connects as
     * [connect] does (the consent screen to show, or null).
     */
    suspend fun switchAccount(context: Context, email: String): PendingIntent? = withContext(Dispatchers.IO) {
        stop()
        mutex.withLock {
            stopRequested = false
            runCatching { DriveAuth.token(context).let { com.google.android.gms.auth.GoogleAuthUtil.clearToken(context, it) } }
            DriveAuth.forget()
            DriveAuth.setChosenAccount(context, email)
            DriveAuth.setDriveAccount(context, null)
            DriveClient.forgetFolders()
            prefs(context).edit().remove("changes_token").apply()
            // (The folders' records and the favourites' merge base — the new Drive is merged as a first sync, both sides
            // together, never as "removed there". This device's own play-time copy stays.)
            runCatching { File(context.filesDir, "cloud_sync").listFiles()?.filter { it.name != "playtime-v2.json" }?.forEach { it.delete() } }
            runCatching { File(context.filesDir, "drive_only_saves_v1.json").delete() }
            _state.update { it.copy(account = null, conflicts = emptyList()) }
        }
        stopWatching()
        connect(context)
    }

    // ---- Running ----

    fun syncInBackground(context: Context, savesOnly: Boolean = false): Deferred<SyncOutcome>? {
        if (!isActive(context)) return null
        val app = context.applicationContext
        val job = scope.async { sync(app, savesOnly) }
        backgroundJob = job
        return job
    }

    /**
     * The quick check before a game starts: only the folders that can hold
     * this game's saves, and in them only its own files (matched by the ROM's
     * name) — or the whole folder for shared memory cards (PS2, PS1, GC…).
     * Favourites/playtime are left for the sync after playing.
     */
    fun syncForGameInBackground(context: Context, game: Game): Deferred<SyncOutcome>? {
        if (!shouldSyncAroundGames(context) || game.sourceType != SourceType.ROM) return null
        val app = context.applicationContext
        val job = scope.async { syncForGame(app, game) }
        backgroundJob = job
        return job
    }

    /** Kinds whose files are named after the ROM ("Zelda (USA).srm"). */
    private val PER_GAME_KINDS = setOf(SaveKind.RETROARCH, SaveKind.GENERIC, SaveKind.NDS)

    private suspend fun syncForGame(context: Context, game: Game): SyncOutcome =
        syncScoped(context, ScopedGame.of(game), uploadOnly = false)

    /** What a scoped sync needs to know about a game (also carried in WorkManager input data). */
    data class ScopedGame(val key: String, val name: String, val romPath: String?, val platform: String) {
        companion object {
            fun of(game: Game) = ScopedGame(game.stableKey, game.name, game.romPath, game.platform)
        }
    }

    /** Folder → predicate for the files of this game; empty when no enabled folder can hold its saves. */
    private fun scopeFor(settings: CloudSyncSettings, game: ScopedGame): List<Pair<SyncFolder, (String) -> Boolean>> {
        val stem = game.romPath?.substringAfterLast('/')?.substringBeforeLast('.')?.lowercase()
            ?: return emptyList()
        val shared = kindsForPlatform(game.platform)
        return settings.folders.filter { it.enabled }.mapNotNull { folder ->
            when (folder.saveKind) {
                in shared -> folder to { _: String -> true }
                in PER_GAME_KINDS -> folder to { rel: String ->
                    rel.substringAfterLast('/').substringBefore('.').lowercase() == stem ||
                        rel.substringAfterLast('/').substringBeforeLast('.').lowercase() == stem
                }
                else -> null
            }
        }
    }

    /** One file that is really a save of a game (Save Capsules). */
    data class SaveFile(val folder: String, val path: String, val size: Long, val modified: Long, val fromDevice: String?, val onDevice: Boolean)

    /**
     * This game's own save files in the configured save folders (per-game kinds only — files named after
     * the ROM), on this device or recorded as synced from another one. Reads folders: call off the main
     * thread. Memory cards (shared by many games) are read by [SaveCapsules] instead.
     */
    fun perGameSaveFiles(context: Context, game: Game): List<SaveFile> {
        load(context)
        if (game.sourceType != SourceType.ROM) return emptyList()
        val stem = game.romPath?.substringAfterLast('/')?.substringBeforeLast('.')?.lowercase() ?: return emptyList()
        fun ours(rel: String): Boolean {
            val n = rel.substringAfterLast('/')
            return n.substringBefore('.').lowercase() == stem || n.substringBeforeLast('.').lowercase() == stem
        }
        val out = mutableListOf<SaveFile>()
        _state.value.settings.folders.filter { it.enabled && it.saveKind in PER_GAME_KINDS && !isStateFolder(it.path) }.forEach { folder ->
            val kind = folder.saveKind
            val local = runCatching { treeFor(context, folder).takeIf { it.exists() }?.list() }.getOrNull().orEmpty()
            local.forEach { (rel, e) ->
                if (kind.accepts(rel) && ours(rel) && e.size > 0) out += SaveFile(folder.name, rel, e.size, e.modified, null, true)
            }
            loadFolderState(context, folder.name).base.forEach { (rel, b) ->
                if (rel !in local && kind.accepts(rel) && ours(rel)) out += SaveFile(folder.name, rel, 0L, b.at, b.from, false)
            }
        }
        return out
    }

    /**
     * [perGameSaveFiles] for many games at once (Save Vault): each save folder is listed once (not once per game —
     * on a big library that was thousands of folder listings), then every file is matched to its game's ROM name.
     * Game.stableKey → its save files.
     */
    fun perGameSaveFilesAll(context: Context, games: List<Game>): Map<String, List<SaveFile>> {
        load(context)
        val byStem = HashMap<String, MutableList<Game>>()
        games.forEach { g ->
            if (g.sourceType != SourceType.ROM) return@forEach
            val stem = g.romPath?.substringAfterLast('/')?.substringBeforeLast('.')?.lowercase() ?: return@forEach
            byStem.getOrPut(stem) { mutableListOf() } += g
        }
        if (byStem.isEmpty()) return emptyMap()
        fun owners(rel: String): List<Game> {
            val n = rel.substringAfterLast('/')
            val a = byStem[n.substringBefore('.').lowercase()]
            val b = byStem[n.substringBeforeLast('.').lowercase()]
            return when {
                a == null -> b.orEmpty()
                b == null || b === a -> a
                else -> (a + b).distinct()
            }
        }
        val out = HashMap<String, MutableList<SaveFile>>()
        _state.value.settings.folders.filter { it.enabled && it.saveKind in PER_GAME_KINDS && !isStateFolder(it.path) }.forEach { folder ->
            val kind = folder.saveKind
            val local = runCatching { treeFor(context, folder).takeIf { it.exists() }?.list() }.getOrNull().orEmpty()
            local.forEach { (rel, e) ->
                if (e.size <= 0 || !kind.accepts(rel)) return@forEach
                owners(rel).forEach { g -> out.getOrPut(g.stableKey) { mutableListOf() } += SaveFile(folder.name, rel, e.size, e.modified, null, true) }
            }
            loadFolderState(context, folder.name).base.forEach { (rel, b) ->
                if (rel in local || !kind.accepts(rel)) return@forEach
                owners(rel).forEach { g -> out.getOrPut(g.stableKey) { mutableListOf() } += SaveFile(folder.name, rel, 0L, b.at, b.from, false) }
            }
        }
        return out
    }


    /** A save file found by [looseSaveFiles]: named after the game it belongs to (maybe a game that isn't in this library). */
    data class LooseSave(
        val kind: SaveKind, val folder: String, val path: String, val title: String,
        val size: Long, val modified: Long, val fromDevice: String?, val onDevice: Boolean
    )

    /**
     * Every save file in the enabled folders of kinds whose file names carry the game's name (RetroArch / generic /
     * DraStic: "Zelda (USA).srm"; Mupen64Plus: "GOLDENEYE 007.eep"; Dolphin GameCube: "01-GLME-Luigi's Mansion.gci") —
     * from this device and recorded as synced from another one. The Save Vault matches them to library games and
     * shows the rest as "games not on this device". Reads folders: call off the main thread.
     */
    fun looseSaveFiles(context: Context): List<LooseSave> {
        load(context)
        val out = mutableListOf<LooseSave>()
        // Update 151: Dolphin Wii too (the Save Vault matches its saves by the game code in their folder's name).
        val kinds = PER_GAME_KINDS + SaveKind.N64 + SaveKind.DOLPHIN_GC + SaveKind.DOLPHIN_WII
        _state.value.settings.folders.filter { it.enabled && it.saveKind in kinds && !isStateFolder(it.path) }.forEach { folder ->
            val kind = folder.saveKind
            fun titleOf(rel: String): String {
                var n = rel.substringAfterLast('/').substringBeforeLast('.')
                // Dolphin: "<maker>-<game id>-<name>.gci".
                if (kind == SaveKind.DOLPHIN_GC) n = n.replace(Regex("^[0-9A-Za-z]{2}-[A-Za-z0-9]{4}-"), "")
                return n.trim()
            }
            val local = runCatching { treeFor(context, folder).takeIf { it.exists() }?.list() }.getOrNull().orEmpty()
            local.forEach { (rel, e) ->
                if (e.size > 0 && kind.accepts(rel)) out += LooseSave(kind, folder.name, rel, titleOf(rel), e.size, e.modified, null, true)
            }
            loadFolderState(context, folder.name).base.forEach { (rel, b) ->
                if (rel !in local && kind.accepts(rel)) out += LooseSave(kind, folder.name, rel, titleOf(rel), 0L, b.at, b.from, false)
            }
        }
        return out
    }

    /**
     * Update 122: the folders [localFileBytes] read from, listed once (a minute at most). A shared-storage folder (the
     * Android/data kind, picked through Android's folder picker) only knows a file after a listing: a fresh one never
     * found any, so every GameCube save (read by its content) was dropped from the Save Vault.
     */
    private val bytesTrees = java.util.concurrent.ConcurrentHashMap<String, Pair<SyncTree, Long>>()

    /** Update 112: a save file on this device in synced folder [folderName] ([rel] inside it), up to [maxBytes]; null otherwise. */
    internal fun localFileBytes(context: Context, folderName: String, rel: String, maxBytes: Long): ByteArray? = runCatching {
        load(context)
        val folder = _state.value.settings.folders.firstOrNull { it.name == folderName } ?: return null
        val now = android.os.SystemClock.uptimeMillis()
        val tree = bytesTrees[folderName]?.takeIf { now - it.second < 60_000L }?.first
            ?: treeFor(context, folder).also { t -> t.list(); bytesTrees[folderName] = t to now }
        val size = tree.entry(rel)?.size ?: return null
        if (size <= 0 || size > maxBytes) return null
        tree.open(rel).use { it.readBytes() }
    }.getOrNull()

    /**
     * Update 109: the files in the PS1 / PS2 memory-card folders that are only on Drive (synced from another device),
     * so the Save Vault can open those cards too and list their games ([looseSaveFiles] leaves these kinds out).
     * Reads folder state only (no network).
     */
    internal fun remoteCardFiles(context: Context): List<LooseSave> {
        load(context)
        val out = mutableListOf<LooseSave>()
        _state.value.settings.folders.filter { it.enabled && (it.saveKind == SaveKind.PS1_MEMCARDS || it.saveKind == SaveKind.PS2_MEMCARDS) }.forEach { folder ->
            val local = runCatching { treeFor(context, folder).takeIf { it.exists() }?.list() }.getOrNull().orEmpty()
            loadFolderState(context, folder.name).base.forEach { (rel, b) ->
                if (rel !in local) out += LooseSave(folder.saveKind, folder.name, rel, rel.substringAfterLast('/').substringBeforeLast('.'), 0L, b.at, b.from, false)
            }
        }
        return out
    }

    /**
     * A save file that is only on Google Drive (synced from another device): its bytes, so the Save Vault can open a
     * memory card from another device (update 108). Downloaded once and kept in the cache by its content's hash; null
     * when Drive isn't connected, the file isn't known, it's bigger than [maxBytes] or can't be fetched. Network: call
     * off the main thread.
     */
    fun remoteFileBytes(context: Context, folderName: String, rel: String, maxBytes: Int = 16_000_000): ByteArray? {
        load(context)
        if (!_state.value.settings.connected) return null
        val b = loadFolderState(context, folderName).base[rel] ?: return null
        val cache = File(File(context.cacheDir, "remote_saves"), b.md5.replace(Regex("[^A-Za-z0-9]"), "") + ".bin")
        if (cache.length() > 0) return runCatching { cache.readBytes() }.getOrNull()
        val bytes = runCatching { DriveClient(context).downloadBytes(b.remoteId, maxBytes.toLong()) }.getOrNull() ?: return null
        if (bytes.isEmpty() || bytes.size > maxBytes) return null
        runCatching { cache.parentFile?.mkdirs(); cache.writeBytes(bytes) }
        return bytes
    }

    /** Every enabled PS1 / PS2 memory-card folder (name, readable tree), for [SaveCapsules]. */
    internal fun memoryCardTrees(context: Context): List<Triple<String, SaveKind, SyncTree>> {
        load(context)
        return _state.value.settings.folders
            // (Update 104: folders you picked yourself too — a memory card kept there is read like any other.)
            .filter { it.enabled && (it.saveKind == SaveKind.PS1_MEMCARDS || it.saveKind == SaveKind.PS2_MEMCARDS || it.saveKind == SaveKind.RETROARCH || it.saveKind == SaveKind.GENERIC) }
            .mapNotNull { f -> runCatching { treeFor(context, f).takeIf { it.exists() } }.getOrNull()?.let { Triple(f.name, f.saveKind, it) } }
    }

    /** True when saves are synced, connected, and this game's platform has a folder to watch. */
    fun canWatchSaves(context: Context, game: Game): Boolean {
        load(context)
        if (!_state.value.settings.let { it.connected && it.syncSaves }) return false
        if (game.sourceType != SourceType.ROM) return false
        return scopeFor(_state.value.settings, ScopedGame.of(game)).isNotEmpty()
    }

    /** One save file that differs from what the last sync recorded. */
    data class ChangedSave(val folder: String, val path: String, val size: Long, val modified: Long)

    /**
     * Cheap probe (no hashing, no network): this game's save files whose size/date no longer match
     * what the last sync recorded, or that were never synced. Files that are unchanged since the
     * recorded hash are skipped without being read. A changed date with identical content still
     * shows up here; the real sync's hash check then finds nothing to send.
     */
    fun changedSaves(context: Context, game: ScopedGame): List<ChangedSave> {
        load(context)
        val settings = _state.value.settings
        if (!settings.connected || !settings.syncSaves) return emptyList()
        val out = mutableListOf<ChangedSave>()
        scopeFor(settings, game).forEach { (folder, only) ->
            if (isStateFolder(folder.path)) return@forEach
            val tree = treeFor(context, folder)
            if (!tree.exists()) return@forEach
            val state = loadFolderState(context, folder.name)
            val kind = folder.saveKind
            tree.list().forEach { (rel, entry) ->
                if (!kind.accepts(rel) || !only(rel)) return@forEach
                val base = state.base[rel]
                val hashed = state.hashes[rel]
                val unchanged = base != null && hashed != null && hashed.size == entry.size &&
                    hashed.modified == entry.modified && hashed.md5 == base.md5
                if (!unchanged) out += ChangedSave(folder.name, rel, entry.size, entry.modified)
            }
        }
        return out
    }

    /**
     * Syncs just [game]'s saves. [uploadOnly]: never touch local files (used while the game may still be
     * running); Drive-side changes are left for the next full sync and conflicts are only recorded.
     * Keeps the per-game [SyncStatusStore] entry up to date.
     */
    internal suspend fun syncScoped(context: Context, game: ScopedGame, uploadOnly: Boolean): SyncOutcome {
        if (uploadOnly) return syncScopedLocked(context, game, true)
        // Right after a game (or before the next one), the emulator may still sit in the background with the
        // old save loaded — it would write its own back over Drive's. Then: send ours up, replace nothing.
        val held = withContext(Dispatchers.IO) { EmulatorGuard.downloadsBlocked(context) }
            ?: return syncScopedLocked(context, game, false)
        val out = syncScopedLocked(context, game, true)
        return out.copy(summary = "$held — saves on this device weren't replaced. Close it fully to get Drive's newest save.")
    }

    private suspend fun syncScopedLocked(context: Context, game: ScopedGame, uploadOnly: Boolean): SyncOutcome = mutex.withLock {
        load(context)
        val settings = _state.value.settings
        if (!settings.connected) return@withLock SyncOutcome(false, "Google Drive isn't connected.")
        val scoped = scopeFor(settings, game)
        if (scoped.isEmpty()) return@withLock SyncOutcome(true, "Nothing to check.")
        stopRequested = false
        val report = Report()
        // A game that never had saves gets no status entry just because it was looked at (resting on a
        // game or opening its details fetches early): the entry only stays when files really moved.
        val hadStatus = SyncStatusStore.get(context, game.key) != null
        SyncStatusStore.update(context, game.key) {
            it.copy(state = if (uploadOnly) SyncState.UPLOADING else SyncState.DOWNLOADING, message = null)
        }
        _state.update { it.copy(running = true, progress = if (uploadOnly) "Sending saves to Google Drive…" else "Checking Google Drive for newer saves…") }
        var cancelled = false
        val outcome = try {
            withContext(Dispatchers.IO) {
                val drive = DriveClient(context)
                val savesRoot = drive.folder(SAVES, drive.folder(ROOT))
                scoped.forEach { (folder, only) ->
                    if (!stopRequested) syncFolder(context, drive, savesRoot, folder, report, only, uploadOnly)
                }
                // Only this game's conflicts are replaced; others stay as they were.
                val names = scoped.map { it.first.name }.toSet()
                _state.update { st ->
                    st.copy(conflicts = st.conflicts.filterNot { c ->
                        c.folderName in names && scoped.any { (f, only) -> f.name == c.folderName && only(c.relativePath) }
                    } + report.conflictList)
                }
                SyncOutcome(
                    !stopRequested && report.failed == 0, report.summary(), report.conflictList.size,
                    uploaded = report.uploaded, downloaded = report.downloaded
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            cancelled = true
            if (hadStatus) SyncStatusStore.update(context, game.key) { it.copy(state = SyncState.PENDING, message = null) }
            else SyncStatusStore.remove(context, game.key)
            SyncOutcome(false, "Sync stopped.")
        } catch (e: DriveSignInNeeded) {
            _state.update { it.copy(needsSignIn = true) }
            SyncOutcome(false, if (e is DriveNotRegistered) e.message ?: DriveErrors.NOT_REGISTERED else "Google Drive needs connecting again (Settings › Sync).", needsSignIn = true)
        } catch (e: DriveRetryable) {
            SyncOutcome(false, e.message ?: "Couldn't reach Google Drive.", retry = true)
        } catch (e: Exception) {
            DriveClient.forgetFolders()
            SyncOutcome(false, "Couldn't check Drive: ${e.message ?: e.javaClass.simpleName}")
        }
        _state.update { it.copy(running = false, progress = null, needsSignIn = DriveAuth.needsSignIn(context), authProblem = DriveAuth.problem(context)) }
        // Stopped: the status was already put back above; don't turn it into an error (update 91).
        if (cancelled) return@withLock outcome
        val now = System.currentTimeMillis()
        SyncStatusStore.update(context, game.key) { old ->
            when {
                outcome.needsSignIn -> old.copy(state = SyncState.NEEDS_SIGN_IN, message = "Sign in to Google Drive again", updatedAt = now)
                outcome.retry -> old.copy(state = SyncState.OFFLINE, message = "Offline — will retry automatically", updatedAt = now)
                outcome.conflicts > 0 -> old.copy(state = SyncState.CONFLICT, message = "Conflict — open Voltcap to choose", updatedAt = now)
                !outcome.ok -> old.copy(state = SyncState.ERROR, message = outcome.summary, updatedAt = now)
                else -> {
                    val moved = outcome.uploaded + outcome.downloaded
                    old.copy(
                        state = SyncState.SYNCED, message = null, updatedAt = now, lastSuccessAt = now,
                        lastDirection = when {
                            outcome.downloaded > 0 -> SyncDirection.DOWNLOAD
                            outcome.uploaded > 0 -> SyncDirection.UPLOAD
                            else -> old.lastDirection
                        },
                        fileCount = if (moved > 0) moved else old.fileCount
                    )
                }
            }
        }
        if (!hadStatus && outcome.ok && outcome.uploaded + outcome.downloaded == 0 && outcome.conflicts == 0) {
            SyncStatusStore.remove(context, game.key)
        }
        outcome
    }

    // ---- Watching for the other device ----

    private var watchJob: Job? = null

    /**
     * While Voltcap is on screen: every 90 s, one small request to Drive's
     * change feed. When the other handheld uploaded a save (or favourites /
     * playtime), sync straight away — so the newest save is usually here
     * before you even pick the game. Stops when a game starts.
     */
    fun startWatching(context: Context) {
        if (!isActive(context) || watchJob?.isActive == true) return
        val app = context.applicationContext
        watchJob = scope.launch {
            val prefs = prefs(app)
            val me = DriveClient.installId(app)
            var token: String? = prefs.getString("changes_token", null)
            while (true) {
                // Battery: no Drive polling while the screen is off / locked; the first check after
                // it comes back on catches up on everything.
                if (!com.shelf.launcher.ui.ScreenState.isInteractive(app)) {
                    kotlinx.coroutines.delay(60_000)
                    continue
                }
                try {
                    val drive = DriveClient(app)
                    if (token == null) {
                        token = drive.startPageToken()
                    } else {
                        val (changes, next) = drive.changes(token)
                        token = next
                        val fromElsewhere = changes.any { !it.removed && it.install != null && it.install != me }
                        if (fromElsewhere && !mutex.isLocked) sync(app)
                    }
                    prefs.edit().putString("changes_token", token).apply()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: DriveSignInNeeded) {
                    _state.update { it.copy(needsSignIn = true) }
                    return@launch
                } catch (e: Exception) {
                    // Offline or a stale token: start the feed over next time.
                    if (e.message?.contains("40") == true) token = null
                }
                kotlinx.coroutines.delay(90_000)
            }
        }
    }

    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
    }

    /** "Decide later" on the conflict prompt. */
    fun snoozeConflicts() {
        _state.update { it.copy(conflictsSnoozed = true) }
    }

    /** Brings a snoozed conflict prompt back (Save sync dialog › Resolve conflict). */
    fun unsnoozeConflicts() {
        _state.update { it.copy(conflictsSnoozed = false) }
    }

    /** Asks a running sync to stop before touching another file. */
    fun stop() {
        stopRequested = true
        backgroundJob?.cancel()
    }

    /** Runs one full sync. */
    suspend fun sync(context: Context, savesOnly: Boolean = false, skipSaves: Boolean = false): SyncOutcome = mutex.withLock {
        // While a game is open its emulator owns the save files: never download/overwrite them.
        val skipSaves = skipSaves || PlaySession.isOpen(context)
        // The last emulator may still hold its saves in memory: send ours up, but don't replace any yet.
        val holdDownloads = !skipSaves && withContext(Dispatchers.IO) { EmulatorGuard.downloadsBlocked(context) } != null
        load(context)
        // (Update 208: the emulators' save folders found on the device, when none are set up yet.)
        withContext(Dispatchers.IO) { runCatching { autoAddFolders(context) } }
        val settings = _state.value.settings
        if (!settings.connected) return@withLock SyncOutcome(false, "Google Drive isn't connected.")
        stopRequested = false
        val report = Report()
        report.noFolders = settings.syncSaves && settings.folders.none { it.enabled }
        _state.update { it.copy(running = true, progress = "Connecting to Google Drive…") }
        var offline = false
        var signIn = false
        val summary = try {
            withContext(Dispatchers.IO) {
                val drive = DriveClient(context)
                // (Update 208: which Google account this is, for Settings › Sync.)
                if (DriveAuth.driveAccount(context) == null) runCatching { drive.accountEmail() }.getOrNull()?.let {
                    DriveAuth.setDriveAccount(context, it)
                    _state.update { st -> st.copy(account = it) }
                }
                val root = drive.folder(ROOT)
                if (settings.syncSaves && !skipSaves) {
                    val savesRoot = drive.folder(SAVES, root)
                    settings.folders.filter { it.enabled }.forEach { folder ->
                        if (!stopRequested) syncFolder(context, drive, savesRoot, folder, report, uploadOnly = holdDownloads)
                    }
                }
                if (settings.syncLauncherData && !savesOnly && !stopRequested) {
                    _state.update { it.copy(progress = "Favourites and playtime…") }
                    try {
                        if (syncLauncherData(context, drive, root)) report.launcherChanged = true
                    } catch (e: LauncherDataUnreadable) {
                        report.failed++
                    }
                }
                val complete = !stopRequested && report.failed == 0
                if (settings.syncSaves && !skipSaves && !stopRequested) {
                    // A finished pass knows every open conflict; a new one asks again.
                    _state.update { st ->
                        st.copy(
                            conflicts = report.conflictList,
                            conflictsSnoozed = st.conflictsSnoozed && report.conflictList.isNotEmpty() &&
                                report.conflictList.map { it.relativePath } == st.conflicts.map { it.relativePath }
                        )
                    }
                }
                SyncOutcome(complete, report.summary(), report.conflictList.size)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            SyncOutcome(false, "Sync stopped.")
        } catch (e: DriveSignInNeeded) {
            signIn = true
            _state.update { it.copy(needsSignIn = true) }
            SyncOutcome(false, "Google Drive needs connecting again (Settings › Sync).")
        } catch (e: Exception) {
            offline = e is DriveRetryable || e is java.io.IOException
            SyncOutcome(false, "Sync failed: ${e.message ?: e.javaClass.simpleName}")
        }
        _lastFullSync.value = FullSyncResult(
            serial = _lastFullSync.value.serial + 1,
            changes = report.changedPaths.mapValues { it.value.toList() },
            downloads = report.downloadedPaths.mapValues { it.value.toList() },
            conflicts = report.conflictList.groupBy({ it.folderName }, { it.relativePath }),
            offline = offline, needsSignIn = signIn,
            ok = summary.ok
        )
        updateSettings(context) { it.copy(lastSyncAt = System.currentTimeMillis(), lastResult = summary.summary) }
        runCatching { SyncLog.add(context, summary.summary) }
        _state.update { it.copy(running = false, progress = null, needsSignIn = DriveAuth.needsSignIn(context), authProblem = DriveAuth.problem(context)) }
        summary
    }

    /**
     * Settles every open conflict the way the player chose. The version not
     * kept is saved under /Voltcap/Sync conflicts first, so a wrong tap
     * never loses a save.
     */
    suspend fun resolveConflicts(context: Context, choice: ConflictChoice): SyncOutcome = mutex.withLock {
        val open = _state.value.conflicts
        if (open.isEmpty()) return@withLock SyncOutcome(true, "Nothing to decide.")
        _state.update { it.copy(running = true, progress = "Sorting out saves…") }
        var done = 0
        // Settled in folder order, not list order: remember which ones, so the rest stay open (update 91).
        val settled = mutableSetOf<SyncConflict>()
        val outcome = try {
            withContext(Dispatchers.IO) {
                val drive = DriveClient(context)
                val device = Build.MODEL.replace(Regex("[^A-Za-z0-9]+"), "-")
                open.groupBy { it.folderName }.forEach { (folderName, items) ->
                    val state = loadFolderState(context, folderName)
                    val remoteDir = drive.folder(folderName, drive.folder(SAVES, drive.folder(ROOT)))
                    val tree = treeFor(context, items.first().folder)
                    val local = tree.list()
                    // "Newest" on a PS2 folder card decides for the whole card (newest file on either side),
                    // so its files always come from one device.
                    val cardUsesDrive = items.groupBy { folderCardOf(it.relativePath) }
                        .filterKeys { it != null }
                        .mapValues { (_, cs) -> cs.maxOf { it.remoteModified } > cs.maxOf { it.localModified } }
                    items.forEach { c ->
                        val rel = c.relativePath
                        val entry = local[rel]
                        val useDrive = when (choice) {
                            ConflictChoice.USE_DRIVE -> true
                            ConflictChoice.KEEP_LOCAL -> false
                            ConflictChoice.NEWEST -> cardUsesDrive[folderCardOf(rel)] ?: !c.localNewer
                        }
                        if (useDrive || entry == null) {
                            // Park this device's version first.
                            if (entry != null) {
                                val copy = conflictCopy(context, folderName, rel, device)
                                copy.parentFile?.mkdirs()
                                tree.open(rel).use { input -> copy.outputStream().use { input.copyTo(it) } }
                            }
                            pull(context, drive, tree, rel, c.remoteId, c.remoteModified, null, state, c.remoteDevice)
                        } else {
                            // Park Drive's version first.
                            val tag = c.remoteDevice?.replace(Regex("[^A-Za-z0-9]+"), "-") ?: "drive"
                            drive.download(c.remoteId, conflictCopy(context, folderName, rel, tag), c.remoteModified)
                            push(drive, tree, rel, entry, localHash(state, tree, rel, entry), remoteDir, c.remoteId, state)
                        }
                        done++
                        settled += c
                    }
                    saveFolderState(context, folderName, state)
                }
                SyncOutcome(true, "Sorted out $done save${if (done == 1) "" else "s"}. The other versions are in Voltcap/Sync conflicts.")
            }
        } catch (e: DriveSignInNeeded) {
            _state.update { it.copy(needsSignIn = true) }
            SyncOutcome(false, "Google Drive needs connecting again (Settings › Sync).")
        } catch (e: Exception) {
            SyncOutcome(false, "Couldn't finish: ${e.message ?: e.javaClass.simpleName}")
        }
        // Whatever wasn't settled comes up again at the next sync.
        _state.update {
            it.copy(
                running = false, progress = null,
                conflicts = if (outcome.ok) emptyList() else it.conflicts.filterNot { c -> c in settled },
                conflictsSnoozed = false
            )
        }
        outcome
    }

    // ---- Per-game status ----

    private fun ago(at: Long): String {
        val minutes = (System.currentTimeMillis() - at) / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 60 * 24 -> "${minutes / 60} h ago"
            else -> "${minutes / (60 * 24)} days ago"
        }
    }

    /** Folder kinds whose shared files (memory cards) belong to a platform. */
    internal fun kindsForPlatform(platform: String): Set<SaveKind> {
        val p = platform.lowercase()
        return when {
            p == "ps2" || p.contains("playstation 2") -> setOf(SaveKind.PS2_MEMCARDS)
            p == "psp" || p.contains("portable") -> setOf(SaveKind.PSP_SAVEDATA)
            p == "ps1" || p == "psx" || p == "playstation" -> setOf(SaveKind.PS1_MEMCARDS)
            p == "gc" || p == "ngc" || p.contains("gamecube") -> setOf(SaveKind.DOLPHIN_GC)
            p == "wii" -> setOf(SaveKind.DOLPHIN_WII)
            p == "3ds" -> setOf(SaveKind.CITRA)
            p == "dc" || p.contains("dreamcast") -> setOf(SaveKind.DREAMCAST_VMU)
            p == "n64" -> setOf(SaveKind.N64)
            p == "nds" || p == "ds" -> setOf(SaveKind.NDS)
            else -> emptySet()
        }
    }

    /**
     * One line for the details window: when this game's save last synced and
     * from which device — "Saves synced 5 min ago · from AYN Odin 2". Games
     * whose saves share one file (PS2/PS1 memory cards, PSP) show when that
     * folder last synced. Null when there's nothing to say. Reads files: call
     * off the main thread.
     */
    fun saveStatusFor(context: Context, game: Game): String? {
        load(context)
        val s = _state.value.settings
        if (!s.connected || !s.syncSaves || game.sourceType != SourceType.ROM) return null
        val stem = game.romPath?.substringAfterLast('/')?.substringBeforeLast('.')?.lowercase() ?: return null
        var best: Base? = null
        // A problem recorded by the last scoped/background sync beats "synced N min ago".
        SyncStatusStore.get(context, game.stableKey)?.let { st ->
            if (st.state in setOf(SyncState.CONFLICT, SyncState.NEEDS_SIGN_IN, SyncState.OFFLINE, SyncState.ERROR) && st.message != null) {
                return "Saves: ${st.message}"
            }
        }
        s.folders.filter { it.enabled }.forEach { folder ->
            loadFolderState(context, folder.name).base.forEach { (rel, b) ->
                val name = rel.substringAfterLast('/').substringBeforeLast('.').lowercase()
                if (name == stem && b.at > (best?.at ?: 0L)) best = b
            }
        }
        best?.let { b ->
            val from = b.from?.takeIf { it != Build.MODEL }?.let { " · from $it" } ?: ""
            return "Saves synced ${ago(b.at)}$from"
        }
        val kinds = kindsForPlatform(game.platform)
        if (kinds.isEmpty()) return null
        val times = context.getSharedPreferences("cloud_sync_folders", Context.MODE_PRIVATE)
        val folder = s.folders.filter { it.enabled && it.saveKind in kinds }
            .maxByOrNull { times.getLong(it.name, 0L) } ?: return null
        val at = times.getLong(folder.name, 0L).takeIf { it > 0L } ?: return null
        return "${folder.name} synced ${ago(at)}"
    }

    // ---- Drive clean-up ----

    /**
     * Moves files an older version uploaded that aren't saves (logs, configs,
     * BIOS, states…) to Drive's bin — recoverable there for 30 days. Only
     * looks inside the synced folders.
     */
    suspend fun cleanUpDrive(context: Context): SyncOutcome = mutex.withLock {
        load(context)
        val settings = _state.value.settings
        if (!settings.connected) return@withLock SyncOutcome(false, "Google Drive isn't connected.")
        _state.update { it.copy(running = true, progress = "Cleaning up Drive…") }
        val outcome = try {
            withContext(Dispatchers.IO) {
                val drive = DriveClient(context)
                val savesRoot = drive.folder(SAVES, drive.folder(ROOT))
                var binned = 0
                settings.folders.forEach { folder ->
                    val kind = folder.saveKind
                    val remoteDir = drive.folder(folder.name, savesRoot)
                    drive.list(remoteDir).filter { !it.isFolder && !kind.accepts(it.name) }.forEach { f ->
                        _state.update { it.copy(progress = "Cleaning up ${folder.name}…") }
                        drive.trash(f.id)
                        binned++
                    }
                }
                SyncOutcome(
                    true,
                    if (binned == 0) "Drive is already tidy — only saves in there."
                    else "Moved $binned non-save file${if (binned == 1) "" else "s"} to Drive's bin (recoverable for 30 days)."
                )
            }
        } catch (e: DriveSignInNeeded) {
            _state.update { it.copy(needsSignIn = true) }
            SyncOutcome(false, "Google Drive needs connecting again (Settings › Sync).")
        } catch (e: Exception) {
            SyncOutcome(false, "Couldn't clean up: ${e.message ?: e.javaClass.simpleName}")
        }
        _state.update { it.copy(running = false, progress = null, needsSignIn = DriveAuth.needsSignIn(context), authProblem = DriveAuth.problem(context)) }
        outcome
    }

    private class Report {
        var uploaded = 0
        var downloaded = 0
        var failed = 0
        var launcherChanged = false
        /** Update 208: saves are on but no save folder is set up — nothing of the saves was looked at. */
        var noFolders = false
        val conflictList = mutableListOf<SyncConflict>()
        /** Folder name → files sent or fetched (all / fetched only), for the per-game status mapping. */
        val changedPaths = mutableMapOf<String, MutableList<String>>()
        val downloadedPaths = mutableMapOf<String, MutableList<String>>()
        fun summary(): String {
            val parts = mutableListOf<String>()
            if (uploaded > 0) parts += "$uploaded uploaded"
            if (downloaded > 0) parts += "$downloaded downloaded"
            if (conflictList.isNotEmpty()) parts += "${conflictList.size} save${if (conflictList.size == 1) "" else "s"} changed on two devices — pick which to keep"
            if (failed > 0) parts += "$failed failed"
            // (Update 208: never "Everything up to date" when no saves were looked at — it said so for days after a
            // reinstall had left the save folders list empty, while nothing went up to Drive.)
            if (noFolders) return (if (parts.isEmpty()) "" else parts.joinToString(", ") + ". ") +
                "No saves synced: no save folders are set up (Settings › Sync › Save folders)."
            return if (parts.isEmpty()) "Everything up to date." else parts.joinToString(", ") + "."
        }
    }

    // ---- Saves ----

    /** What a file was when this device last synced it. */
    /** [at]: when this device last synced the file; [from]: the device whose
     * version it is now (for "Saves synced 5 min ago · from AYN Odin 2"). */
    private data class Base(val md5: String, val remoteId: String, val at: Long = 0L, val from: String? = null)
    /** A file's hash, reused while its size and date stay the same. */
    private data class Hashed(val size: Long, val modified: Long, val md5: String)
    private data class FolderState(
        val base: MutableMap<String, Base> = mutableMapOf(),
        val hashes: MutableMap<String, Hashed> = mutableMapOf()
    )

    private fun stateFile(context: Context, name: String): File {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(File(context.filesDir, "cloud_sync"), "$safe.json")
    }

    private fun loadFolderState(context: Context, name: String): FolderState {
        val file = stateFile(context, name)
        if (!file.exists()) return FolderState()
        return runCatching {
            gson.fromJson(file.readText(), FolderState::class.java)?.let {
                @Suppress("SENSELESS_COMPARISON")
                FolderState(
                    if (it.base == null) mutableMapOf() else it.base.toMutableMap(),
                    if (it.hashes == null) mutableMapOf() else it.hashes.toMutableMap()
                )
            }
        }.getOrNull() ?: FolderState()
    }

    private fun saveFolderState(context: Context, name: String, state: FolderState) {
        val file = stateFile(context, name)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(gson.toJson(state))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun md5(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("MD5")
        input.use {
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = it.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun md5(file: File): String = md5(file.inputStream())

    private fun localHash(state: FolderState, tree: SyncTree, rel: String, entry: LocalEntry): String {
        state.hashes[rel]?.let { if (it.size == entry.size && it.modified == entry.modified) return it.md5 }
        val hash = md5(tree.open(rel))
        state.hashes[rel] = Hashed(entry.size, entry.modified, hash)
        return hash
    }

    private fun tempFile(context: Context): File =
        File.createTempFile("sync", ".part", File(context.cacheDir, "sync").apply { mkdirs() })

    /** Drive's version of [rel] into [tree]; returns the hash now on disk. */
    private fun pull(context: Context, drive: DriveClient, tree: SyncTree, rel: String, id: String, modified: Long, md5Hint: String?, state: FolderState, from: String? = null, backupFolder: String? = null): String {
        val tmp = tempFile(context)
        try {
            drive.download(id, tmp, modified)
            // Never trust a download blindly: an interrupted or mangled transfer must not reach the
            // emulator's folder. Drive's own checksum has to match what actually arrived.
            val md5 = md5(tmp)
            if (md5Hint != null && !md5Hint.equals(md5, ignoreCase = true)) {
                error("Download of $rel was incomplete — the save on this device was left untouched.")
            }
            // The version about to be replaced goes to Voltcap/Sync backups first (last 3 kept).
            backupFolder?.let { backupBeforeOverwrite(context, tree, rel, it) }
            tree.write(rel, tmp, modified)
            tree.entry(rel)?.let { state.hashes[rel] = Hashed(it.size, it.modified, md5) }
            state.base[rel] = Base(md5, id, System.currentTimeMillis(), from)
            return md5
        } finally {
            tmp.delete()
        }
    }

    /** This device's [rel] into Drive. */
    private fun push(drive: DriveClient, tree: SyncTree, rel: String, entry: LocalEntry, md5: String, remoteDir: String, existingId: String?, state: FolderState) {
        val up = drive.upload(tree.body(rel, entry), rel, remoteDir, existingId, entry.modified)
        state.base[rel] = Base(md5, up.id, System.currentTimeMillis(), Build.MODEL)
    }

    /**
     * Copies the local [rel] (if any) to Voltcap/Sync backups/<folder>/ before Drive's version replaces it,
     * keeping the newest [BACKUPS_KEPT] per file. Best effort: a failed backup never blocks the sync, but
     * a save is never replaced by an unverified download (see [pull]).
     */
    private fun backupBeforeOverwrite(context: Context, tree: SyncTree, rel: String, folderName: String) {
        runCatching {
            if (tree.entry(rel) == null) return
            val base = File(Environment.getExternalStorageDirectory(), "Voltcap/Sync backups")
                .takeIf { it.mkdirs() || it.isDirectory }
                ?: File(context.getExternalFilesDir(null) ?: context.filesDir, "Sync backups")
            val dir = File(File(base, folderName), rel).parentFile ?: return
            dir.mkdirs()
            val name = rel.substringAfterLast('/')
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val out = File(dir, "$name.$stamp")
            tree.open(rel).use { input -> out.outputStream().use { input.copyTo(it) } }
            dir.listFiles { f -> f.isFile && f.name.startsWith("$name.") }
                ?.sortedByDescending { it.name }
                ?.drop(BACKUPS_KEPT)
                ?.forEach { it.delete() }
        }
    }

    private const val BACKUPS_KEPT = 3

    private fun conflictsDir(context: Context, folder: String): File {
        val base = File(Environment.getExternalStorageDirectory(), "Voltcap/Sync conflicts")
            .takeIf { it.mkdirs() || it.isDirectory }
            ?: File(context.getExternalFilesDir(null) ?: context.filesDir, "Sync conflicts")
        return File(base, folder)
    }

    private fun conflictCopy(context: Context, folder: String, rel: String, tag: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return File(conflictsDir(context, folder), "$rel.$tag-$stamp")
    }

    /** [only]: limit to these files (a single game's saves before it starts); null = everything. */
    private fun syncFolder(
        context: Context, drive: DriveClient, savesRoot: String, folder: SyncFolder, report: Report,
        only: ((String) -> Boolean)? = null,
        /** Never write to local files: Drive-side changes are left for a later, full sync. */
        uploadOnly: Boolean = false
    ) {
        if (isStateFolder(folder.path)) return
        val tree = treeFor(context, folder)
        if (!tree.exists()) return
        _state.update { it.copy(progress = "${folder.name}: comparing…") }
        val state = loadFolderState(context, folder.name)
        val remoteDir = drive.folder(folder.name, savesRoot)
        // Two devices uploading the same new file at once can make twins; the
        // newest one counts.
        val kind = folder.saveKind
        // Only real saves, both ways: anything else an older version uploaded
        // is simply ignored (left in Drive, never downloaded).
        val remote = drive.list(remoteDir).filter { !it.isFolder && kind.accepts(it.name) }
            .groupBy { it.name }.mapValues { (_, same) -> same.maxByOrNull { it.modifiedTime }!! }
        val local = tree.list().filterKeys { kind.accepts(it) }
        val all = (local.keys + remote.keys).filter { only == null || only(it) }.sorted()
        // PS2 folder memory cards (ARMSX2 / NetherSX2 "Mcd001.ps2/…") are one save made of many files:
        // mixing files from two devices corrupts the card. A card changed on both sides is one conflict
        // for every changed file in it, never a half-and-half merge.
        val mixedCards = mixedFolderCards(all, local, remote, state, tree)
        all.forEachIndexed { index, rel ->
            if (stopRequested) return@forEachIndexed
            _state.update { it.copy(progress = "${folder.name}: ${index + 1}/${all.size}") }
            try {
                val entry = local[rel]
                val r = remote[rel]
                val l = entry?.let { localHash(state, tree, rel, it) }
                val b = state.base[rel]

                fun upload() {
                    push(drive, tree, rel, entry!!, l!!, remoteDir, r?.id, state)
                    report.uploaded++
                    report.changedPaths.getOrPut(folder.name) { mutableListOf() } += rel
                }
                fun download() {
                    pull(context, drive, tree, rel, r!!.id, r.modifiedTime, r.md5, state, r.device, backupFolder = folder.name)
                    report.downloaded++
                    report.changedPaths.getOrPut(folder.name) { mutableListOf() } += rel
                    report.downloadedPaths.getOrPut(folder.name) { mutableListOf() } += rel
                }
                // Changed on both sides: touch nothing, ask the player.
                fun conflict() {
                    report.conflictList += SyncConflict(
                        folderName = folder.name,
                        folder = folder,
                        relativePath = rel,
                        localModified = entry!!.modified,
                        remoteId = r!!.id,
                        remoteModified = r.modifiedTime,
                        remoteDevice = r.device
                    )
                }

                val card = folderCardOf(rel)
                when {
                    card != null && card in mixedCards -> if (l != r?.md5) {
                        if (l != null && r != null) conflict()
                        // A file only one side has inside a mixed card: left alone until the card is settled.
                    }
                    l != null && r == null -> upload()
                    l == null && r != null -> if (!uploadOnly) download() // new elsewhere, or deleted here: never deleted everywhere
                    l != null && r != null -> when {
                        // Same on both sides: keep when/where it was last synced.
                        l == r.md5 -> {
                            state.base[rel] = b?.takeIf { it.md5 == l && it.remoteId == r.id && it.at > 0L }
                                ?: Base(l, r.id, System.currentTimeMillis(), r.device)
                        }
                        b == null -> conflict()                  // both have it, never synced before
                        r.md5 == b.md5 -> upload()               // only changed here
                        l == b.md5 -> if (!uploadOnly) download() // only changed on the other device
                        else -> conflict()                       // changed on both
                    }
                }
            } catch (e: DriveSignInNeeded) {
                saveFolderState(context, folder.name, state)
                throw e
            } catch (e: DriveOffline) {
                // No point trying the remaining files: the worker retries the whole sync later.
                saveFolderState(context, folder.name, state)
                throw e
            } catch (e: Exception) {
                report.failed++
            }
            // Saved as it goes, so an interrupted sync doesn't redo (or
            // misjudge) what's already done.
            if (index % 25 == 24) saveFolderState(context, folder.name, state)
        }
        // Forget hashes of files that are gone.
        state.hashes.keys.retainAll(local.keys + state.base.keys)
        saveFolderState(context, folder.name, state)
        if (!stopRequested && only == null) {
            context.getSharedPreferences("cloud_sync_folders", Context.MODE_PRIVATE).edit()
                .putLong(folder.name, System.currentTimeMillis()).apply()
        }
    }

    /** "Mcd001.ps2/…/file" → "Mcd001.ps2" (the folder card it belongs to), else null. */
    private fun folderCardOf(rel: String): String? {
        val parts = rel.split('/')
        val i = parts.dropLast(1).indexOfFirst { it.lowercase().endsWith(".ps2") }
        return if (i < 0) null else parts.take(i + 1).joinToString("/")
    }

    /** Folder cards with changes on this device AND in Drive since the last sync. */
    private fun mixedFolderCards(
        all: List<String>, local: Map<String, LocalEntry>, remote: Map<String, DriveFile>, state: FolderState, tree: SyncTree
    ): Set<String> {
        val changedHere = mutableSetOf<String>()
        val changedThere = mutableSetOf<String>()
        for (rel in all) {
            val card = folderCardOf(rel) ?: continue
            val entry = local[rel]
            val r = remote[rel]
            val l = entry?.let { runCatching { localHash(state, tree, rel, it) }.getOrNull() }
            val b = state.base[rel]
            if (l != null && r != null && l == r.md5) continue
            if (l != null && (b == null || l != b.md5)) changedHere += card
            if (r != null && (b == null || r.md5 != b.md5)) changedThere += card
        }
        return changedHere intersect changedThere
    }

    // ---- Launcher data ----

    private const val ROM_PREFIX = "rom:"

    private fun romRoot(context: Context): String =
        LibrarySettingsStore(context).loadRootPath().trimEnd('/') + "/"

    private fun toPortable(key: String, root: String) =
        if (key.startsWith(root)) ROM_PREFIX + key.removePrefix(root) else key

    private fun toLocal(key: String, root: String) =
        if (key.startsWith(ROM_PREFIX)) root + key.removePrefix(ROM_PREFIX) else key

    // ---- Update 182: playtime and last played matched by console + file name, not by folder ----
    // The keys were the ROM's path under the library folder, so two handhelds with the console's games in differently
    // named folders ("psx" / "PS1", a subfolder per game, the SD card…) never matched: each kept only its own hours.
    // Now a ROM counts as "romf:<console>/<file name>" for these two (the console worked out from the folders the way
    // the scan does). Old path keys on Drive and here are folded into it once (hours added up — the paths never
    // matched, so they never counted the same play twice); a new key wins over an old one an older version still writes.

    /** Update 182: every game this device has (stable keys), to put synced playtime back on the right game. */
    @Volatile var libraryKeys: (() -> Collection<String>)? = null

    private const val FILE_PREFIX = "romf:"

    /** The device-independent key of a ROM (portable "rom:…" or a full path), or null for apps, Steam games… */
    private fun fileKey(key: String): String? {
        if (key.startsWith(FILE_PREFIX)) return key
        val path = when {
            key.startsWith(ROM_PREFIX) -> key.removePrefix(ROM_PREFIX)
            key.startsWith("/") -> key
            else -> return null
        }
        val parts = path.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val folders = parts.dropLast(1)
        val console = folders.asReversed().firstNotNullOfOrNull { runCatching { RomScanner.platformForFolderName(it) }.getOrNull() }
            ?: folders.lastOrNull() ?: "-"
        return FILE_PREFIX + console.lowercase() + "/" + parts.last().lowercase()
    }

    /** [m] with every ROM under its file key; entries landing on one key [combine]d. */
    private fun byFile(m: Map<String, Long>, combine: (Long, Long) -> Long): Map<String, Long> {
        val fresh = m.keys.filter { it.startsWith(FILE_PREFIX) }.toSet()
        val out = LinkedHashMap<String, Long>()
        for ((k, v) in m) {
            val f = fileKey(k)
            if (f == null) { out[k] = v; continue }
            if (f in fresh && k != f) continue
            out[f] = out[f]?.let { combine(it, v) } ?: v
        }
        return out
    }

    private fun normalized(d: LauncherData): LauncherData =
        d.copy(playtime = byFile(d.playtime, Long::plus), recent = byFile(d.recent) { a, b -> maxOf(a, b) })

    /** [d]'s file keys back on this device's own games (portable keys); unknown ones stay as they are. */
    private fun localized(context: Context, d: LauncherData, local: LauncherData): LauncherData {
        val root = romRoot(context)
        val index = HashMap<String, String>()
        val known = runCatching { libraryKeys?.invoke() }.getOrNull().orEmpty().map { toPortable(it, root) } +
            local.playtime.keys + local.recent.keys
        for (k in known) fileKey(k)?.let { f -> if (f != k) index.putIfAbsent(f, k) }
        fun back(m: Map<String, Long>, combine: (Long, Long) -> Long): Map<String, Long> {
            val out = LinkedHashMap<String, Long>()
            for ((k, v) in m) {
                val to = index[k] ?: k
                out[to] = out[to]?.let { combine(it, v) } ?: v
            }
            return out
        }
        return d.copy(playtime = back(d.playtime, Long::plus), recent = back(d.recent) { a, b -> maxOf(a, b) })
    }

    private val longMapType = object : TypeToken<Map<String, Long>>() {}.type

    /** Per-game data in a device-independent shape. Objects are kept as
     * canonical JSON strings so two devices compare them the same way. */
    private data class LauncherData(
        val favorites: Set<String> = emptySet(),
        val playtime: Map<String, Long> = emptyMap(),
        val recent: Map<String, Long> = emptyMap(),
        val artOverrides: Map<String, String> = emptyMap(),
        val gameOverrides: Map<String, String> = emptyMap(),
        /** Update 117: each device's recent play sessions (device id → its entry, OtherDevices.kt), for the recaps. */
        val devices: Map<String, String> = emptyMap(),
        /** Update 202: play time format 2 (per device); null in a file only older versions wrote. See [PlaytimeV2]. */
        val pt: PlaytimeV2? = null
    )

    /**
     * Update 202: play time, format 2 — "playtimeV2" in launcher-data.json ("format": 2).
     *  - [devices]: device id → that device's own play time per game (file keys). Only that device adds to it; every
     *    copy merges by max, so a sync repeated after an interrupted one, a restore or a stale base can't add hours twice.
     *  - [legacy]: per game, the time from before format 2 that the one-time repair could vouch for (merged by max).
     *  - [since]: when counting per device began (the first device to switch sets it; play ending later is in [devices]).
     * Shown total = legacy + the sum over devices. The old "playtime" map is still written (that total) for older versions
     * to show, but is never read back as a source once format 2 data exists: their "Drive + what changed here" sums
     * can't inflate it again. Older versions drop "playtimeV2" when they upload; each device keeps a copy in its sync
     * base (launcher-base.json) and puts it back on its next sync.
     */
    private data class PlaytimeV2(
        val since: Long = 0L,
        val legacy: Map<String, Long> = emptyMap(),
        val devices: Map<String, Map<String, Long>> = emptyMap()
    )

    /** Update 117: a game's name and console from this device's library (set by the home screen), for the shared sessions. */
    @Volatile var gameInfo: ((String) -> Pair<String, String>?)? = null

    private fun canonical(o: JSONObject): String {
        val sorted = JSONObject()
        o.keys().asSequence().sorted().forEach { k -> if (!o.isNull(k)) sorted.put(k, o.get(k)) }
        return sorted.toString()
    }

    private fun objectMap(json: String?): Map<String, JSONObject> {
        if (json.isNullOrEmpty()) return emptyMap()
        return runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().mapNotNull { k -> o.optJSONObject(k)?.let { k to it } }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun longMap(json: String?): Map<String, Long> =
        json?.let { runCatching { gson.fromJson<Map<String, Long>>(it, longMapType) }.getOrNull() } ?: emptyMap()

    /** A picture picked from this device (content://) means nothing elsewhere. */
    private fun isLocalOnly(o: JSONObject): Boolean =
        o.keys().asSequence().any { o.optString(it).startsWith("content:") || o.optString(it).startsWith("file:") }

    private fun readLocal(context: Context): LauncherData {
        val root = romRoot(context)
        val p = { k: String -> toPortable(k, root) }
        val fav = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
            .getStringSet("favorite_keys", emptySet()).orEmpty()
        val play = longMap(context.getSharedPreferences("playtime", Context.MODE_PRIVATE).getString("totals_json", null))
        val recent = longMap(context.getSharedPreferences("recently_played", Context.MODE_PRIVATE).getString("last_played_json", null))
        val art = objectMap(context.getSharedPreferences("art_overrides", Context.MODE_PRIVATE).getString("overrides_json", null))
        val ids = objectMap(context.getSharedPreferences("game_overrides", Context.MODE_PRIVATE).getString("overrides_json", null))
        return LauncherData(
            favorites = fav.map(p).toSet(),
            playtime = play.mapKeys { p(it.key) },
            recent = recent.mapKeys { p(it.key) },
            artOverrides = art.filterValues { !isLocalOnly(it) }.entries.associate { p(it.key) to canonical(it.value) },
            gameOverrides = ids.entries.associate { p(it.key) to canonical(it.value) },
            devices = runCatching {
                mapOf(OtherDevices.deviceId(context) to canonical(JSONObject(OtherDevices.ownEntry(context, p, gameInfo))))
            }.getOrDefault(emptyMap())
        )
    }

    private fun writeLocal(context: Context, data: LauncherData, ownSeen: Map<String, Long>? = null) {
        val root = romRoot(context)
        val l = { k: String -> toLocal(k, root) }
        context.getSharedPreferences("favorites", Context.MODE_PRIVATE).edit()
            .putStringSet("favorite_keys", data.favorites.map(l).toSet()).commit()
        // Update 202: through PlaytimeStore (its lock): a session that ended while this sync ran is kept on top.
        PlaytimeStore(context).replaceTotals(data.playtime.mapKeys { l(it.key) }, ownSeen)
        context.getSharedPreferences("recently_played", Context.MODE_PRIVATE).edit()
            .putString("last_played_json", gson.toJson(data.recent.mapKeys { l(it.key) })).commit()

        // Artwork picked from this device's own files stays as it is.
        val artPrefs = context.getSharedPreferences("art_overrides", Context.MODE_PRIVATE)
        val localOnly = objectMap(artPrefs.getString("overrides_json", null)).filterValues { isLocalOnly(it) }
        val art = JSONObject()
        data.artOverrides.forEach { (k, v) -> art.put(l(k), JSONObject(v)) }
        localOnly.forEach { (k, v) -> art.put(k, v) }
        artPrefs.edit().putString("overrides_json", art.toString()).commit()

        val ids = JSONObject()
        data.gameOverrides.forEach { (k, v) -> ids.put(l(k), JSONObject(v)) }
        context.getSharedPreferences("game_overrides", Context.MODE_PRIVATE).edit()
            .putString("overrides_json", ids.toString()).commit()
    }

    private fun toJson(d: LauncherData): String = JSONObject()
        .put("app", "Voltcap")
        .put("format", 1)
        .put("updated", System.currentTimeMillis())
        .put("device", Build.MODEL)
        .put("favorites", JSONArray(d.favorites.sorted()))
        .put("playtime", JSONObject(d.playtime as Map<*, *>))
        .put("recent", JSONObject(d.recent as Map<*, *>))
        .put("artOverrides", JSONObject().apply { d.artOverrides.forEach { (k, v) -> put(k, JSONObject(v)) } })
        .put("gameOverrides", JSONObject().apply { d.gameOverrides.forEach { (k, v) -> put(k, JSONObject(v)) } })
        .put("devices", JSONObject().apply { d.devices.forEach { (k, v) -> put(k, JSONObject(v)) } })
        .apply {
            // Update 202: playtime format 2 (per device, see PlaytimeV2); "format" 2 marks a file that has it.
            val pt = d.pt ?: return@apply
            put("format", 2)
            put("playtimeV2", playtimeV2Json(pt))
        }
        .toString()

    private fun playtimeV2Json(pt: PlaytimeV2): JSONObject = JSONObject()
        .put("v", 2)
        .put("since", pt.since)
        .put("legacy", JSONObject(pt.legacy as Map<*, *>))
        .put("devices", JSONObject().apply { pt.devices.forEach { (id, m) -> put(id, JSONObject(m as Map<*, *>)) } })

    /**
     * Update 202: this device's last copy of the format-2 play data (cloud_sync/playtime-v2.json), written after the repair
     * and every sync: one more copy for [playtimeV2] to merge, so the repair's result isn't lost when the sync that ran
     * it fails before its upload, or an older version's upload and a missing base leave no other copy.
     */
    private fun playtimeCopyFile(context: Context) = File(File(context.filesDir, "cloud_sync"), "playtime-v2.json")

    private fun loadPlaytimeCopy(context: Context): PlaytimeV2? = runCatching {
        val f = playtimeCopyFile(context)
        if (!f.exists()) null else playtimeV2From(JSONObject(f.readText()))
    }.getOrNull()

    private fun savePlaytimeCopy(context: Context, pt: PlaytimeV2) {
        runCatching {
            val f = playtimeCopyFile(context)
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(JSONObject().put("playtimeV2", playtimeV2Json(pt)).toString())
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }

    /**
     * Update 220 (#01): where [key]'s total comes from, as (where, ms): the other handhelds' counters and the older total
     * carried over from before play time was counted per device (update 202), from this device's last copy of the
     * shared play data. Empty without one (Save Vault sync off). This device's own part is PlaytimeStore.loadOwn.
     */
    fun playSources(context: Context, key: String): List<Pair<String, Long>> = runCatching {
        val pt = loadPlaytimeCopy(context) ?: return emptyList()
        val sk = syncKeyOf(key, romRoot(context))
        val me = OtherDevices.deviceId(context)
        val labels = OtherDevices.labels(context)
        val out = ArrayList<Pair<String, Long>>()
        pt.devices.forEach { (id, m) -> if (id != me) m[sk]?.takeIf { it > 0L }?.let { out += (labels[id] ?: "Another device") to it } }
        pt.legacy[sk]?.takeIf { it > 0L }?.let { out += "Older total" to it }
        out
    }.getOrDefault(emptyList())

    /** Update 202: the "playtimeV2" object (null when missing, or written by a newer, unknown version of it). */
    private fun playtimeV2From(o: JSONObject): PlaytimeV2? {
        val p = o.optJSONObject("playtimeV2") ?: return null
        if (p.optInt("v", 0) != 2) return null
        fun longs(m: JSONObject?): Map<String, Long> {
            if (m == null) return emptyMap()
            return m.keys().asSequence().associateWith { m.optLong(it) }.filterValues { it > 0L }
        }
        val devs = p.optJSONObject("devices")
        val devices = HashMap<String, Map<String, Long>>()
        if (devs != null) devs.keys().forEach { id -> devices[id] = longs(devs.optJSONObject(id)) }
        return PlaytimeV2(
            since = p.optLong("since", 0L),
            legacy = longs(p.optJSONObject("legacy")),
            devices = devices
        )
    }

    private fun fromJson(text: String): LauncherData {
        val o = JSONObject(text)
        fun longs(name: String): Map<String, Long> {
            val m = o.optJSONObject(name) ?: return emptyMap()
            return m.keys().asSequence().associateWith { m.optLong(it) }
        }
        fun objects(name: String): Map<String, String> {
            val m = o.optJSONObject(name) ?: return emptyMap()
            return m.keys().asSequence().mapNotNull { k -> m.optJSONObject(k)?.let { k to canonical(it) } }.toMap()
        }
        val fav = o.optJSONArray("favorites") ?: JSONArray()
        return LauncherData(
            favorites = (0 until fav.length()).map { fav.getString(it) }.toSet(),
            playtime = longs("playtime"),
            recent = longs("recent"),
            artOverrides = objects("artOverrides"),
            gameOverrides = objects("gameOverrides"),
            devices = objects("devices"),
            pt = runCatching { playtimeV2From(o) }.getOrNull()
        )
    }

    /**
     * Three-way merge against [base], the result of the last sync on this
     * device (null on the first one):
     * - favourites: stars added or removed here since the last sync win,
     *   everything else follows Drive. First sync: both sets together.
     * - playtime: (old format, Update 202: replaced right after by [playtimeV2]) Drive's total plus whatever was
     *   played here since the last sync. Not safe to repeat: when this device's totals moved without the base
     *   (a sync cut off after its upload, a restore / Settings import), the same hours were added again.
     * - recently played: the latest time from either side.
     * - artwork picks and manual IDs: an entry changed here wins, otherwise Drive's.
     */
    private fun mergeLauncherData(base: LauncherData?, local: LauncherData, remote: LauncherData): LauncherData {
        val favorites = if (base == null) local.favorites + remote.favorites
        else (remote.favorites - (base.favorites - local.favorites)) + (local.favorites - base.favorites)

        val playtime = (local.playtime.keys + remote.playtime.keys).associateWith { k ->
            val l = local.playtime[k] ?: 0L
            val r = remote.playtime[k] ?: 0L
            if (base == null) maxOf(l, r)
            else maxOf(r + maxOf(0L, l - (base.playtime[k] ?: 0L)), l)
        }

        val recent = (local.recent.keys + remote.recent.keys).associateWith { k ->
            maxOf(local.recent[k] ?: 0L, remote.recent[k] ?: 0L)
        }

        fun objects(b: Map<String, String>?, l: Map<String, String>, r: Map<String, String>): Map<String, String> {
            val out = mutableMapOf<String, String>()
            (l.keys + r.keys + b?.keys.orEmpty()).forEach { k ->
                val value = when {
                    b == null -> l[k] ?: r[k]
                    l[k] != b[k] -> l[k]      // changed (or removed) here
                    else -> r[k]
                }
                if (value != null) out[k] = value
            }
            return out
        }

        return LauncherData(
            favorites = favorites,
            playtime = playtime,
            recent = recent,
            artOverrides = objects(base?.artOverrides, local.artOverrides, remote.artOverrides),
            gameOverrides = objects(base?.gameOverrides, local.gameOverrides, remote.gameOverrides),
            // Every device keeps its own entry: this device's replaces its old one, the others' stay as Drive has them.
            devices = remote.devices + local.devices
        )
    }

    // ---- Update 202: playtime format 2 (per-device counters) and the one-time repair ----

    /** What [playtimeV2] worked out: the synced data, the shown totals (sync keys), and the own counter it read. */
    private class PlaytimeResult(val pt: PlaytimeV2, val totals: Map<String, Long>, val ownSeen: Map<String, Long>)

    /** One play session in sync keys (the repair's view). */
    private data class PlaySpan(val key: String, val start: Long, val ms: Long)

    /** Update 202: a game key of this device as the sync uses it (file key for ROMs, else the portable key). */
    private fun syncKeyOf(key: String, root: String): String {
        val p = toPortable(key, root)
        return fileKey(p) ?: p
    }

    /** Update 202: [m] (this device's keys) under the sync keys; entries landing on one key are added up (separate play). */
    private fun syncKeys(m: Map<String, Long>, root: String): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        for ((k, v) in m) {
            if (v <= 0L) continue
            val f = syncKeyOf(k, root)
            out[f] = (out[f] ?: 0L) + v
        }
        return out
    }

    /** Update 202: per key, the largest value of any copy (counters only grow: the largest copy is the newest). */
    private fun maxMerge(maps: List<Map<String, Long>>): Map<String, Long> {
        val out = HashMap<String, Long>()
        maps.forEach { m -> m.forEach { (k, v) -> if (v > 0L && v > (out[k] ?: 0L)) out[k] = v } }
        return out
    }

    /**
     * Update 202: play time, format 2. Every copy of the per-device data (Drive's, the last sync's base, this device's
     * own counter) merges by max per device and game; the shown total per game = legacy + the sum over devices. Nothing
     * is ever added from a difference between two copies, so running it again (after an interrupted sync, a restore, an
     * older version's upload) gives the same result. The first time on this device it runs the repair ([repairPlaytime]).
     */
    private fun playtimeV2(context: Context, local: LauncherData, base: LauncherData?, remote: LauncherData): PlaytimeResult {
        val root = romRoot(context)
        val me = OtherDevices.deviceId(context)
        val retired = runCatching { OtherDevices.retiredIds(context) }.getOrDefault(emptySet())
        val store = PlaytimeStore(context)
        // Drive's copy, the base's and this device's own one: older versions upload without "playtimeV2", the others keep it.
        val known = listOfNotNull(remote.pt, base?.pt, loadPlaytimeCopy(context))
        val knownLegacy = maxMerge(known.map { it.legacy })
        val knownSince = known.map { it.since }.filter { it > 0L }.minOrNull() ?: 0L
        val others = remote.devices - me - retired
        val (legacy, since, ownSeen) = synchronized(PlaytimeStore.LOCK) {
            when {
                // First sync with format 2 here: the repair, and this device's own counter from its session log.
                !store.ownSeeded() -> {
                    val r = repairPlaytime(store, local, others, knownSince, knownLegacy, me, root, seedOwn = true)
                    Triple(r.first, r.second, store.loadOwn())
                }
                // Repaired before, but no copy of the format-2 data is left (Drive's file written by an older version
                // AND the base gone): work the legacy part out again; this device's own counter stays as it is.
                known.isEmpty() -> {
                    val s = store.ownSince().takeIf { it > 0L } ?: System.currentTimeMillis()
                    val r = repairPlaytime(store, local, others, s, emptyMap(), me, root, seedOwn = false)
                    Triple(r.first, r.second, store.loadOwn())
                }
                else -> Triple(knownLegacy, store.ownSince().takeIf { it > 0L } ?: knownSince, store.loadOwn())
            }
        }
        val mine = syncKeys(ownSeen, root)
        val devices = HashMap<String, Map<String, Long>>()
        (known.flatMap { it.devices.keys } + me).toSet().forEach { id ->
            // An id this device used before: its play is in this device's own counter now.
            if (id in retired) return@forEach
            val copies = known.mapNotNull { it.devices[id] } + (if (id == me) listOf(mine) else emptyList())
            val m = maxMerge(copies)
            if (m.isNotEmpty()) devices[id] = m
        }
        val totals = HashMap<String, Long>()
        legacy.forEach { (k, v) -> if (v > 0L) totals[k] = v }
        devices.values.forEach { m -> m.forEach { (k, v) -> totals[k] = (totals[k] ?: 0L) + v } }
        val pt = PlaytimeV2(since, legacy, devices)
        savePlaytimeCopy(context, pt)
        return PlaytimeResult(pt, totals, ownSeen)
    }

    /**
     * Update 202: the one-time repair of totals inflated by the old merge (call holding PlaytimeStore.LOCK).
     * Returns (legacy per game, since).
     *
     * Every session ever counted is also in a session log (this device's, and each other device's newest
     * OtherDevices.SHARE_MAX in the synced "devices" entries). Per game:
     *  - all those sessions together, each counted once: the same game + start time listed by two devices (a log copied
     *    with a backup / Settings transfer, an old id's entry) is one session;
     *  - "covered" = the logs reach back to the game's first session (no log that dropped old sessions starts after it):
     *    the legacy part is then exactly the sessions that ended before [since] — the inflated total is rebuilt;
     *  - not covered (no sessions for it, or play older than a full log reaches): the logs can't tell real play from
     *    double-counted; the old total is kept as it is (minus the play since [since], counted per device), only once
     *    per game for all devices (a legacy value already on Drive is kept, never added to).
     * Legacy values from two devices merge by max: the sessions each one saw are real, so the larger is never too much.
     * [seedOwn]: this device's own counter = its own logged sessions that ended from [since] on (a session also listed
     * by a device with a smaller id is that one's), replacing what it held (already in the log).
     */
    private fun repairPlaytime(
        store: PlaytimeStore, local: LauncherData, others: Map<String, String>, sharedSince: Long,
        knownLegacy: Map<String, Long>, me: String, root: String, seedOwn: Boolean
    ): Pair<Map<String, Long>, Long> {
        // The first device to switch sets the moment; the others take it from Drive.
        val since = if (sharedSince > 0L) sharedSince else System.currentTimeMillis()
        val myLog = store.loadSessions()
        val mine = myLog.map { PlaySpan(syncKeyOf(it.key, root), it.start, it.ms) }
        val lists = HashMap<String, List<PlaySpan>>()
        others.forEach { (id, text) ->
            runCatching {
                val arr = JSONObject(text).optJSONArray("s") ?: JSONArray()
                lists[id] = (0 until arr.length()).mapNotNull { i ->
                    val e = arr.optJSONArray(i) ?: return@mapNotNull null
                    val p = e.optString(0)
                    val ms = e.optLong(2)
                    if (p.isEmpty() || ms <= 0L) null else PlaySpan(fileKey(p) ?: p, e.optLong(1), ms)
                }
            }
        }
        // Each session once (game + start time), and which devices list it.
        val distinct = HashMap<Pair<String, Long>, Long>()
        val owners = HashMap<Pair<String, Long>, MutableSet<String>>()
        fun addSpan(id: String, s: PlaySpan) {
            val k = s.key to s.start
            distinct[k] = maxOf(distinct[k] ?: 0L, s.ms)
            owners.getOrPut(k) { mutableSetOf() }.add(id)
        }
        mine.forEach { addSpan(me, it) }
        lists.forEach { (id, l) -> l.forEach { addSpan(id, it) } }

        // A log that's full has dropped its oldest sessions: it vouches only for play from its first entry on.
        val fullFrom = ArrayList<Long>()
        if (myLog.size >= PlaytimeStore.SESSION_LOG_MAX) mine.minOfOrNull { it.start }?.let { fullFrom += it }
        lists.values.forEach { l -> if (l.size >= OtherDevices.SHARE_MAX) l.minOfOrNull { it.start }?.let { fullFrom += it } }
        val logFrom = fullFrom.maxOrNull() ?: Long.MIN_VALUE

        val before = HashMap<String, Long>()
        val after = HashMap<String, Long>()
        val first = HashMap<String, Long>()
        distinct.forEach { (k, ms) ->
            val (key, start) = k
            if (start + ms < since) before[key] = (before[key] ?: 0L) + ms else after[key] = (after[key] ?: 0L) + ms
            first[key] = minOf(first[key] ?: Long.MAX_VALUE, start)
        }
        val old = local.playtime   // this device's old totals (sync keys): what the old merge left, maybe inflated
        val legacy = HashMap(knownLegacy)
        var rebuilt = 0
        var kept = 0
        val notes = ArrayList<String>()
        for (key in old.keys + before.keys + after.keys + knownLegacy.keys) {
            val f = first[key]
            val covered = f != null && f > logFrom
            val value = if (covered) maxOf(knownLegacy[key] ?: 0L, before[key] ?: 0L)
                else knownLegacy[key] ?: maxOf(0L, (old[key] ?: 0L) - (after[key] ?: 0L))
            if (value > 0L) legacy[key] = value else legacy.remove(key)
            if (covered) rebuilt++ else kept++
            val was = old[key] ?: 0L
            if (notes.size < 30 && was != value + (after[key] ?: 0L)) {
                notes += "$key: ${was / 60_000} min -> ${(value + (after[key] ?: 0L)) / 60_000} min" + if (covered) "" else " (kept, not covered)"
            }
        }

        if (seedOwn) {
            val own = HashMap<String, Long>()
            myLog.forEach { s ->
                if (s.start + s.ms < since) return@forEach
                val k = syncKeyOf(s.key, root) to s.start
                // Listed by a device with a smaller id too: that device counts it.
                if (owners[k].orEmpty().any { it != me && it < me }) return@forEach
                own[s.key] = (own[s.key] ?: 0L) + s.ms
            }
            store.seedOwn(own, since)
        }
        runCatching {
            android.util.Log.i(
                "CloudSync",
                "Update 202 playtime repair: since=$since, ${distinct.size} sessions from ${lists.size + 1} devices, " +
                    "$rebuilt games rebuilt from the logs, $kept kept; changed: ${notes.joinToString("; ")}"
            )
        }
        return legacy to since
    }

    /** Drive's launcher-data file is there but couldn't be read this time. */
    private class LauncherDataUnreadable : Exception("Couldn't read favourites and playtime from Google Drive")

    /** True when this device's data changed. */
    private fun syncLauncherData(context: Context, drive: DriveClient, root: String): Boolean {
        val baseFile = File(File(context.filesDir, "cloud_sync"), "launcher-base.json")
        val baseRaw = baseFile.takeIf { it.exists() }?.let { runCatching { fromJson(it.readText()) }.getOrNull() }
        val existing = drive.list(root, "name='$LAUNCHER_FILE'").filter { !it.isFolder }.maxByOrNull { it.modifiedTime }
        // Drive's copy couldn't be read (a dropped download, a damaged file): merging against "nothing" would
        // read as "everything was removed on the other device" and wipe favourites, artwork picks and manual
        // IDs here. Skip this round instead; the next sync tries again.
        val remoteRaw = if (existing == null) LauncherData() else
            runCatching { fromJson(drive.downloadBytes(existing.id).decodeToString()) }.getOrNull()
                ?: throw LauncherDataUnreadable()
        val rawLocal = readLocal(context)
        // Update 182: playtime / last played under console + file name on every side (see fileKey).
        val local = normalized(rawLocal)
        val base = baseRaw?.let { normalized(it) }
        val remote = normalized(remoteRaw)
        // Update 202: play time from the per-device counters (format 2, PlaytimeV2) — the old "Drive + what changed here"
        // sum isn't used any more. The sync base counts as a copy too (it's kept even when Drive's file was recreated).
        val play = playtimeV2(context, local, base, remote)
        // No file on Drive (first sync, or the Drive folder was deleted / recreated): merge like a first sync
        // (both sides together), never as if Drive had removed everything since the last one.
        val merged = mergeLauncherData(if (existing == null) null else base, local, remote).let { m ->
            // Update 144: entries under ids this device used before (OtherDevices.deviceId) go, so its old play isn't
            // counted again as "another device".
            val retired = runCatching { OtherDevices.retiredIds(context) }.getOrDefault(emptySet())
            if (retired.isEmpty()) m else m.copy(devices = m.devices - retired)
        }.copy(playtime = play.totals, pt = play.pt)

        if (merged != remoteRaw || existing == null) {
            drive.uploadBytes(toJson(merged).toByteArray(), LAUNCHER_FILE, root, existing?.id)
        }
        // (Update 117: the other devices' play sessions are kept for the recaps; they never count as a change here.)
        runCatching {
            val rootDir = romRoot(context)
            OtherDevices.store(context, merged.devices - OtherDevices.deviceId(context)) { k -> toLocal(k, rootDir) }
        }
        // (Update 182: written back under this device's own game keys; compared with what's stored here now.)
        val mine = localized(context, merged, rawLocal)
        // (Update 202: the per-device play data is Drive's / the base's, not compared with this device's prefs.)
        val changedHere = mine.copy(devices = emptyMap(), pt = null) != rawLocal.copy(devices = emptyMap(), pt = null)
        if (changedHere) writeLocal(context, mine, play.ownSeen)
        baseFile.parentFile?.mkdirs()
        baseFile.writeText(toJson(merged))
        if (changedHere) _launcherDataChanged.tryEmit(Unit)
        return changedHere
    }
}
