package com.shelf.launcher.data

import android.content.Context
import android.os.Build
import android.os.FileObserver
import android.os.SystemClock
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Update 222 (#103): a game's saves go up to Drive within seconds of the emulator writing them — not after the
 * background watcher's first look 10 minutes in (SaveWatchWorker), which a short session never reached.
 *
 * From the game's start until a while after Voltcap is back ([AFTER_GAME_MS]: emulators often write once more as
 * they close):
 * - Android's file events (FileObserver: a file closed after writing, moved / renamed into place, created) on the
 *   game's plain save folders wake it at once. Folders shared through Android's picker send no events — and on
 *   some handhelds even plain folders may not — so it also takes a cheap look every [POLL_MS] (size and date
 *   only, no reading, no network: CloudSync.changedSaves), slower while the screen is off.
 * - A change counts only when it has settled: the same files with the same size and date on two looks at least
 *   [SETTLE_MS] apart, the newest written at least [SETTLE_MS] ago. Several writes / renames of one save are one
 *   change; a memory card being written is never sent half-done.
 * - Then one per-game upload job ([SaveNowWorker]) is queued in WorkManager: it survives Voltcap being closed or
 *   killed and waits for a connection; a newer change is sent by the same job right after, never cut off.
 *   Upload-only: nothing on this device is ever replaced while the game may be running, and Drive's copy is checked
 *   right before it's replaced (CloudSync.push): changed by another handheld meanwhile → both kept, conflict.
 *
 * Nothing runs when save sync or Drive is off, in safe mode, or for a game whose saves Voltcap can't sync.
 * The 10-minute watcher stays as the safety net for when the process dies mid-game.
 */
object SaveObserver {
    private const val POLL_MS = 15_000L
    private const val POLL_ASLEEP_MS = 60_000L
    private const val SETTLE_MS = 4_000L
    private const val AFTER_GAME_MS = 15 * 60_000L
    private const val MAX_MS = 12L * 60 * 60 * 1000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val observers = ArrayList<FileObserver>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var until = Long.MAX_VALUE
    @Volatile private var watching: String? = null

    /** A game was just started from Voltcap. */
    @Synchronized
    fun start(context: Context, game: Game) {
        stop()
        if (com.shelf.launcher.CrashGuard.safeMode) return
        val app = context.applicationContext
        if (!CloudSync.canWatchSaves(app, game)) return
        begin(app, CloudSync.ScopedGame.of(game))
        // (Mid-game sync, update 529: until the emulator writes a save to disk there is nothing to send - said so, not "Synced".)
        SyncStatusStore.update(app, game.stableKey) { if (it.state == SyncState.SYNCED) it.copy(message = NOT_WRITTEN_YET) else it }
    }

    /**
     * Mid-game sync, update 529: picks the watch up again for the game still being played - Voltcap's process was ended while
     * the emulator was in front (low memory: GT78's log shows Voltcap's process killed in the background), and with it the
     * watcher; WorkManager starts the process again for [SaveWatchWorker], which calls this. No-op when already watching it.
     */
    @Synchronized
    fun resume(context: Context, g: CloudSync.ScopedGame): Boolean {
        if (watching == g.key && job?.isActive == true) return true
        if (com.shelf.launcher.CrashGuard.safeMode) return false
        stop()
        begin(context.applicationContext, g)
        return true
    }

    private fun begin(app: Context, g: CloudSync.ScopedGame) {
        watching = g.key
        until = Long.MAX_VALUE
        val started = SystemClock.elapsedRealtime()
        job = scope.launch {
            runCatching { CloudSync.watchDirs(app, g) }.getOrDefault(emptyList()).forEach { dir -> observe(dir) }
            watch(app, g.key, started, Probe(
                changed = { CloudSync.changedSaves(app, g) },
                active = { CloudSync.isActive(app) },
                send = { SaveNowWorker.enqueue(app, g) },
                remote = { CloudSync.probeRemote(app, g).let { o -> if (o.remoteNewer > 0) o.remoteFrom ?: "" else null } },
                name = g.name
            ))
        }
    }

    /** What the watch loop looks at and does - Drive's saves, or (isolated QA app) the dry run in [QaSaveDryRun]. */
    internal class Probe(
        val changed: suspend () -> List<CloudSync.ChangedSave>,
        val active: () -> Boolean,
        val send: suspend () -> Unit,
        /** Another handheld's newer save on Drive: its device (blank when unknown), else null. */
        val remote: (suspend () -> String?)?,
        val name: String
    )

    /**
     * Mid-game sync, update 529 - isolated QA app only: the same watch loop (file events, polling, settling, the restart after the
     * process was ended) on a synthetic save folder, with the upload replaced by a local copy that is checked the way Drive's
     * checksum is ([QaSaveDryRun]). It measures what can be measured without your Google account: write -> found -> settled ->
     * snapshot verified.
     */
    @Synchronized
    fun startDry(context: Context, dir: File): Boolean {
        val app = context.applicationContext
        if (!QaVariant.isolated(app)) return false
        if (watching == QaSaveDryRun.KEY && job?.isActive == true) return true
        stop()
        dir.mkdirs()
        watching = QaSaveDryRun.KEY
        until = Long.MAX_VALUE
        val started = SystemClock.elapsedRealtime()
        job = scope.launch {
            observe(dir)
            watch(app, QaSaveDryRun.KEY, started, Probe(
                changed = { QaSaveDryRun.changed(dir) }, active = { true }, send = { QaSaveDryRun.upload(dir) }, remote = null, name = "QA dry run"
            ))
        }
        return true
    }

    internal const val NOT_WRITTEN_YET = "No new save written to disk since the game started"
    /** How often a running game's Drive copy is looked at for another handheld's newer save (screen on, nothing pending). */
    private const val REMOTE_MS = 3 * 60_000L

    /** Voltcap is back from the game: keep watching a while (the emulator may write as it closes), then stop. */
    fun gameEnded() {
        if (watching != null) until = SystemClock.elapsedRealtime() + AFTER_GAME_MS
        wake.trySend(Unit)
    }

    /** Which game's saves are being watched now (null: none). */
    fun watchingKey(): String? = watching

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        observers.forEach { runCatching { it.stopWatching() } }
        observers.clear()
        watching = null
    }

    private fun observe(dir: File) {
        val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.CREATE or FileObserver.DELETE
        val o = if (Build.VERSION.SDK_INT >= 29) object : FileObserver(dir, mask) {
            override fun onEvent(event: Int, path: String?) { wake.trySend(Unit) }
        } else @Suppress("DEPRECATION") object : FileObserver(dir.path, mask) {
            override fun onEvent(event: Int, path: String?) { wake.trySend(Unit) }
        }
        runCatching { o.startWatching(); synchronized(this) { observers += o } }
    }

    private suspend fun watch(app: Context, key: String, started: Long, p: Probe) {
        var lastSig: String? = null
        var sentSig: String? = null
        var remoteAt = SystemClock.elapsedRealtime()
        var told = false
        // (Mid-game sync, update 529: the first look is at once - a watcher brought back after the process was ended has missed
        // the file events of the saves written meanwhile; Flip 2 dry run: 15 s of a 43 s delay were this first wait.)
        var first = true
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val now = SystemClock.elapsedRealtime()
            if (now > until || now - started > MAX_MS) break
            // A pending change is looked at again soon; otherwise the next file event or the regular look.
            val awake = com.shelf.launcher.ui.ScreenState.isInteractive(app)
            val wait = if (first) 0L else if (lastSig != null) SETTLE_MS else if (awake) POLL_MS else POLL_ASLEEP_MS
            first = false
            val woke = withTimeoutOrNull(wait) { wake.receive() } != null
            // A burst of events (one save written in several steps): wait for it to go quiet first.
            if (woke) while (withTimeoutOrNull(SETTLE_MS) { wake.receive() } != null) Unit
            if (!p.active()) continue
            val changed = runCatching { p.changed() }.getOrDefault(emptyList())
            if (changed.isEmpty()) {
                lastSig = null
                // (Mid-game sync, update 529: now and then, with nothing of ours pending, look whether another handheld put a newer
                // save of this game on Drive. Never applied while the game runs (upload-only); a discreet note once, and the
                // after-game sync / Play's check bring it in when the emulator has closed.)
                if (p.remote != null && awake && SystemClock.elapsedRealtime() - remoteAt >= REMOTE_MS) {
                    remoteAt = SystemClock.elapsedRealtime()
                    val from = runCatching { p.remote.invoke() }.getOrNull()
                    if (from != null && !told) { told = true; SyncNotifier.remoteNewer(app, key, p.name, from.ifBlank { null }) }
                }
                continue
            }
            // (Update 529: the content hash is part of it - a same-size save rewritten within one date step is a new state.)
            val sig = changed.sortedBy { it.folder + "/" + it.path }.joinToString("|") { SaveChangeDetect.signature(it.folder, it.path, it.size, it.modified, it.md5) }
            val newest = changed.maxOf { it.modified }
            val settled = sig == lastSig && System.currentTimeMillis() - newest >= SETTLE_MS
            if (!settled && sig != lastSig && sig != sentSig) {
                SaveSyncTrace.mark(key, "detected", newest)
                SyncStatusStore.update(app, key) { it.copy(state = SyncState.PENDING, message = "New save found — waiting for the file to settle", updatedAt = System.currentTimeMillis()) }
            }
            lastSig = sig
            if (!settled || sig == sentSig) continue
            sentSig = sig
            SaveSyncTrace.mark(key, "settled", newest)
            SyncStatusStore.update(app, key) { it.copy(state = SyncState.PENDING, message = "New save — waiting to upload", updatedAt = System.currentTimeMillis()) }
            p.send()
        }
        synchronized(this) {
            if (watching == key) { observers.forEach { runCatching { it.stopWatching() } }; observers.clear(); watching = null }
        }
    }
}

/**
 * Update 222 (#103): uploads one game's changed saves now (upload-only), queued by [SaveObserver]. One unique queue per
 * game: an upload is never cut off by the next change, and (update 529) a change made while it uploads queues the next job. Offline → WorkManager retries with backoff; survives Voltcap being closed.
 */
class SaveNowWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        if (!CloudSync.isActive(app) || com.shelf.launcher.CrashGuard.safeMode) return Result.success()
        val key = inputData.getString("key") ?: return Result.success()
        val game = CloudSync.ScopedGame(key, inputData.getString("name") ?: key, inputData.getString("rom"), inputData.getString("platform") ?: "")
        // Already sent by an earlier job (or the after-game sync): nothing to do.
        if (CloudSync.changedSaves(app, game).isEmpty()) return Result.success()
        SaveSyncTrace.mark(game.key, "upload-start", 0L)
        var outcome = CloudSync.syncScoped(app, game, uploadOnly = true)
        // Saved again while this one was uploading (the queue keeps one job per game): settle, then send that too.
        var round = 1
        while (outcome.ok && round < 3 && CloudSync.changedSaves(app, game).isNotEmpty()) {
            kotlinx.coroutines.delay(5_000)
            outcome = CloudSync.syncScoped(app, game, uploadOnly = true)
            round++
        }
        SaveSyncTrace.mark(game.key, if (outcome.ok && outcome.uploaded > 0) "verified" else "upload-ended ok=${outcome.ok} up=${outcome.uploaded} writing=${outcome.stillWriting}", 0L)
        SyncNotifier.afterBackgroundSync(app, game.key, game.name, outcome, firstAttempt = runAttemptCount == 0, midGame = SaveObserver.watchingKey() == game.key)
        // (Update 529, finding 2: a change still unsent when this job ends is never dropped - it used to end in success after
        // three rounds, and the watcher had already counted that change as sent. Retried with backoff until it is on Drive;
        // a change that is blocked (sign-in) stays PENDING in the status and is sent by the next sync that can.)
        val unsent = !outcome.needsSignIn && CloudSync.changedSaves(app, game).isNotEmpty()
        return when {
            outcome.needsSignIn -> Result.success()
            outcome.retry -> Result.retry()
            unsent && runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            unsent -> {
                SyncStatusStore.update(app, game.key) { it.copy(state = SyncState.PENDING, message = "Not on Google Drive yet — will try again", updatedAt = System.currentTimeMillis()) }
                PostGameSyncWorker.enqueue(app, game)
                Result.success()
            }
            else -> Result.success()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(NOTIFICATION_ID, SyncNotifier.workingNotification(applicationContext))

    companion object {
        private const val NOTIFICATION_ID = 0x5A_4FFB
        /** Retries (30 s, 1, 2, 4 ... min) before the change is handed to the after-game queue. */
        internal const val MAX_ATTEMPTS = 8

        /** Update 529: [game]'s unique save-now queue name. Pure. */
        internal fun workName(key: String): String = "voltcap-save-now-" + PostGameSyncWorker.stableId(key)

        fun enqueue(context: Context, game: CloudSync.ScopedGame) {
            val request = OneTimeWorkRequestBuilder<SaveNowWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(
                    Data.Builder().putString("key", game.key).putString("name", game.name)
                        .putString("rom", game.romPath).putString("platform", game.platform).build()
                )
                .addTag("voltcap-save-now")
                .build()
            runCatching {
                WorkManager.getInstance(context.applicationContext)
                    // (Update 529, finding 2: APPEND_OR_REPLACE, was KEEP - a save made while this game's upload runs was dropped by
                    // KEEP; now it queues a job that runs right after, which sends it, or finds it already sent.)
                    .enqueueUniqueWork(workName(game.key), ExistingWorkPolicy.APPEND_OR_REPLACE, request)
            }
        }
    }
}
