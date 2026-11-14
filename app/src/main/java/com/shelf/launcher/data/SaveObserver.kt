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
        val g = CloudSync.ScopedGame.of(game)
        watching = g.key
        until = Long.MAX_VALUE
        val started = SystemClock.elapsedRealtime()
        job = scope.launch {
            runCatching { CloudSync.watchDirs(app, g) }.getOrDefault(emptyList()).forEach { dir -> observe(dir) }
            watch(app, g, started)
        }
    }

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

    private suspend fun watch(app: Context, g: CloudSync.ScopedGame, started: Long) {
        var lastSig: String? = null
        var sentSig: String? = null
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val now = SystemClock.elapsedRealtime()
            if (now > until || now - started > MAX_MS) break
            // A pending change is looked at again soon; otherwise the next file event or the regular look.
            val awake = com.shelf.launcher.ui.ScreenState.isInteractive(app)
            val wait = if (lastSig != null) SETTLE_MS else if (awake) POLL_MS else POLL_ASLEEP_MS
            val woke = withTimeoutOrNull(wait) { wake.receive() } != null
            // A burst of events (one save written in several steps): wait for it to go quiet first.
            if (woke) while (withTimeoutOrNull(SETTLE_MS) { wake.receive() } != null) Unit
            if (!CloudSync.isActive(app)) continue
            val changed = runCatching { CloudSync.changedSaves(app, g) }.getOrDefault(emptyList())
            if (changed.isEmpty()) { lastSig = null; continue }
            val sig = changed.sortedBy { it.folder + "/" + it.path }.joinToString("|") { "${it.folder}/${it.path}:${it.size}:${it.modified}" }
            val newest = changed.maxOf { it.modified }
            val settled = sig == lastSig && System.currentTimeMillis() - newest >= SETTLE_MS
            lastSig = sig
            if (!settled || sig == sentSig) continue
            sentSig = sig
            SyncStatusStore.update(app, g.key) { it.copy(state = SyncState.PENDING, message = "New save — sending to Google Drive", updatedAt = System.currentTimeMillis()) }
            SaveNowWorker.enqueue(app, g)
        }
        synchronized(this) {
            if (watching == g.key) { observers.forEach { runCatching { it.stopWatching() } }; observers.clear(); watching = null }
        }
    }
}

/**
 * Update 222 (#103): uploads one game's changed saves now (upload-only), queued by [SaveObserver]. One unique job per
 * game (KEEP: an upload is never cut off by the next change); a change made while it uploads is sent by the same job
 * right after. Offline → WorkManager retries with backoff; survives Voltcap being closed.
 */
class SaveNowWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        if (!CloudSync.isActive(app) || com.shelf.launcher.CrashGuard.safeMode) return Result.success()
        val key = inputData.getString("key") ?: return Result.success()
        val game = CloudSync.ScopedGame(key, inputData.getString("name") ?: key, inputData.getString("rom"), inputData.getString("platform") ?: "")
        // Already sent by an earlier job (or the after-game sync): nothing to do.
        if (CloudSync.changedSaves(app, game).isEmpty()) return Result.success()
        var outcome = CloudSync.syncScoped(app, game, uploadOnly = true)
        // Saved again while this one was uploading (the queue keeps one job per game): settle, then send that too.
        var round = 1
        while (outcome.ok && round < 3 && CloudSync.changedSaves(app, game).isNotEmpty()) {
            kotlinx.coroutines.delay(5_000)
            outcome = CloudSync.syncScoped(app, game, uploadOnly = true)
            round++
        }
        SyncNotifier.afterBackgroundSync(app, game.key, game.name, outcome, firstAttempt = runAttemptCount == 0)
        return when {
            outcome.needsSignIn -> Result.success()
            outcome.retry -> Result.retry()
            // Stopped halfway (a game start stops syncs) with the change still unsent: try again shortly.
            !outcome.ok && runAttemptCount < 4 && CloudSync.changedSaves(app, game).isNotEmpty() -> Result.retry()
            else -> Result.success()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(NOTIFICATION_ID, SyncNotifier.workingNotification(applicationContext))

    companion object {
        private const val NOTIFICATION_ID = 0x5A_4FFB

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
                    .enqueueUniqueWork("voltcap-save-now-" + game.key.hashCode(), ExistingWorkPolicy.KEEP, request)
            }
        }
    }
}
