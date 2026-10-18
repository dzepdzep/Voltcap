package com.shelf.launcher.data

import android.content.Context
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
import java.util.concurrent.TimeUnit

/**
 * Uploads a running game's saves without Voltcap in front.
 *
 * One chain per play session: [startSession] queues a first check ~10 min after the game starts; each
 * check looks (cheaply, no network, no hashing of unchanged files) whether the game's save files changed
 * since the last sync, waits until the changed files have sat untouched for [STABLE_MS] (the emulator may
 * still be writing), uploads them (upload-only: nothing on this device is ever overwritten while the
 * session is open), then queues the next check 10 → 20 → 40 → 60 min later. The chain ends when Voltcap
 * comes back (session closed, [endSession] cancels it) or after [MAX_SESSION_MS].
 *
 * WorkManager keeps it across process death and reboots; nothing runs between checks (no wake lock,
 * service or loop).
 */
class SaveWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        val game = gameFrom(inputData) ?: return Result.success()
        val started = inputData.getLong(K_START, 0L)
        val step = inputData.getInt(K_STEP, 0)
        val now = System.currentTimeMillis()

        // Session over (Voltcap resumed and closed it, or another game started), or too old: stop the chain.
        if (openSessionStart(app, game.key) != started || now - started > MAX_SESSION_MS) return Result.success()
        if (!CloudSync.isActive(app) || com.shelf.launcher.CrashGuard.safeMode) return Result.success()

        val changed = CloudSync.changedSaves(app, game)
        if (changed.isEmpty()) {
            // Asleep / locked (the game is paused too): nothing will change soon, so the next look is an hour away.
            val asleep = !com.shelf.launcher.ui.ScreenState.isInteractive(app)
            return next(app, inputData, step + 1, if (asleep) TimeUnit.MINUTES.toMillis(60) else delayFor(step + 1))
        }

        // Something changed. Too fresh (or a clock in the future) → look again soon instead of uploading mid-write.
        val newest = changed.maxOf { it.modified }
        if (now - newest < STABLE_MS) {
            SyncStatusStore.update(app, game.key) { it.copy(state = SyncState.PENDING) }
            return next(app, inputData, step, RECHECK_MS)
        }
        SyncStatusStore.update(app, game.key) { it.copy(state = SyncState.PENDING) }

        val outcome = CloudSync.syncScoped(app, game, uploadOnly = true)
        SyncNotifier.afterBackgroundSync(app, game.key, game.name, outcome, firstAttempt = runAttemptCount == 0)
        return when {
            outcome.needsSignIn -> Result.success() // retrying can't help until the player signs in again
            outcome.retry -> Result.retry() // offline: back off, same work
            else -> next(app, inputData, step + 1, delayFor(step + 1))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(NOTIFICATION_ID, SyncNotifier.workingNotification(applicationContext))

    private fun next(app: Context, current: Data, step: Int, delayMs: Long): Result {
        // Don't queue another link if we were cancelled (session ended) in the meantime.
        if (isStopped) return Result.success()
        enqueue(app, current, step, delayMs)
        return Result.success()
    }

    companion object {
        private const val TAG = "voltcap-save-watch"
        private const val K_KEY = "key"
        private const val K_NAME = "name"
        private const val K_ROM = "rom"
        private const val K_PLATFORM = "platform"
        private const val K_START = "start"
        private const val K_STEP = "step"
        private const val NOTIFICATION_ID = 0x5A_4FFC // not SyncNotifier's summary id (0x5A_4FFE)

        /** Files must have sat untouched this long before they're uploaded. */
        const val STABLE_MS = 90_000L
        /** Look again this soon when the newest change is still too fresh. */
        private const val RECHECK_MS = 2 * 60_000L
        /** The watcher gives up after this long. */
        const val MAX_SESSION_MS = 12L * 60 * 60 * 1000
        // (Update 222: first look after 3 min — SaveObserver sends saves within seconds; this chain is the safety net when the process dies.)
        private val STEPS_MIN = longArrayOf(3, 10, 20, 40, 60)

        /** Minutes between checks grow 10 → 20 → 40 → 60 (cap). */
        private fun delayFor(step: Int): Long = TimeUnit.MINUTES.toMillis(STEPS_MIN[step.coerceIn(0, STEPS_MIN.size - 1)])

        private fun gameFrom(d: Data): CloudSync.ScopedGame? {
            val key = d.getString(K_KEY) ?: return null
            return CloudSync.ScopedGame(key, d.getString(K_NAME) ?: key, d.getString(K_ROM), d.getString(K_PLATFORM) ?: "")
        }

        private fun dataFor(game: CloudSync.ScopedGame, start: Long, step: Int): Data = Data.Builder()
            .putString(K_KEY, game.key).putString(K_NAME, game.name)
            .putString(K_ROM, game.romPath).putString(K_PLATFORM, game.platform)
            .putLong(K_START, start).putInt(K_STEP, step).build()

        /** The open session's start time if [key] is still the game being played, else null. */
        private fun openSessionStart(context: Context, key: String): Long? {
            val prefs = context.getSharedPreferences("playtime", Context.MODE_PRIVATE)
            if (prefs.getString("open_game", null) != key) return null
            return prefs.getLong("open_since", 0L).takeIf { it > 0L }
        }

        private fun enqueue(context: Context, current: Data, step: Int, delayMs: Long) {
            val game = gameFrom(current) ?: return
            val start = current.getLong(K_START, 0L)
            val request = OneTimeWorkRequestBuilder<SaveWatchWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .setInputData(dataFor(game, start, step))
                .addTag(TAG)
                .build()
            // A new name per link: REPLACE on the running link's own name would cancel it mid-run.
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork("$TAG-$start-$step-${System.currentTimeMillis() / 1000}", ExistingWorkPolicy.KEEP, request)
        }

        /** A game just started. [startedAt] is the same time given to PlaytimeStore.startSession. */
        fun startSession(context: Context, game: Game, startedAt: Long) {
            if (com.shelf.launcher.CrashGuard.safeMode) return
            if (!CloudSync.canWatchSaves(context, game)) return
            val app = context.applicationContext
            // A previous game's chain (two quick sessions back to back) is dropped first.
            WorkManager.getInstance(app).cancelAllWorkByTag(TAG)
            enqueue(app, dataFor(CloudSync.ScopedGame.of(game), startedAt, 0), 0, delayFor(0))
        }

        /** The session ended (Voltcap is back): stop watching. */
        fun endSession(context: Context) {
            runCatching { WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(TAG) }
        }
    }
}

/**
 * "Sync now after a game": an expedited one-time job, so the upload finishes even if Voltcap goes
 * straight back to the background (or the process is killed) after the game. Scoped sync for the game
 * that just ended first, then the normal full sync. Offline → WorkManager retries with backoff.
 */
class PostGameSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        if (!CloudSync.isActive(app) || com.shelf.launcher.CrashGuard.safeMode) return Result.success()
        val key = inputData.getString("key")
        // A retry can run after the next game started: then the emulator owns the save files.
        val sessionOpen = PlaySession.isOpen(app)
        if (key != null) {
            val game = CloudSync.ScopedGame(
                key, inputData.getString("name") ?: key, inputData.getString("rom"), inputData.getString("platform") ?: ""
            )
            // The session is over, so downloads are safe too (no upload-only restriction), unless a newer one is open.
            val scoped = CloudSync.syncScoped(app, game, uploadOnly = sessionOpen)
            SyncNotifier.afterBackgroundSync(app, key, game.name, scoped, firstAttempt = runAttemptCount == 0)
            if (scoped.needsSignIn) return Result.success()
            if (scoped.retry) return Result.retry()
        }
        val full = CloudSync.sync(app, skipSaves = sessionOpen)
        return if (full.ok || DriveAuth.needsSignIn(app)) {
            if (DriveAuth.needsSignIn(app)) SyncNotifier.signInNeeded(app)
            Result.success()
        } else Result.retry()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(0x5A_4FFD, SyncNotifier.workingNotification(applicationContext))

    companion object {
        private const val NAME = "voltcap-post-game-sync"

        /** [game] null: only the full sync. */
        fun enqueue(context: Context, game: CloudSync.ScopedGame?) {
            val data = Data.Builder().apply {
                if (game != null) {
                    putString("key", game.key); putString("name", game.name)
                    putString("rom", game.romPath); putString("platform", game.platform)
                }
            }.build()
            val request = OneTimeWorkRequestBuilder<PostGameSyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .setInputData(data)
                .build()
            // REPLACE: the newest game's job wins; an older one that already ran is just done.
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
