package com.shelf.launcher.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Google Drive sync every few hours, even while Voltcap isn't open
 * (Settings › Sync › In the background). Android runs it when there's a
 * network and the battery isn't low, so the exact time drifts a little.
 *
 * Saves are left alone while a game is running — changing a save under a
 * running emulator could lose progress — only favourites/playtime sync then.
 */
class CloudSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        if (!CloudSync.isActive(app)) return Result.success()
        val outcome = CloudSync.sync(app, skipSaves = gameIsRunning(app))
        // Done, or Google genuinely wants the sign-in confirmed (retrying can't help until the
        // player does that): success. Offline, timeouts and other hiccups: Android retries later
        // with backoff. The needs-sign-in flag is saved and cleared by the next good authorisation,
        // so a stale "true" can't swallow real failures.
        return if (outcome.ok || DriveAuth.needsSignIn(app)) Result.success() else Result.retry()
    }

    /**
     * A game started from Voltcap that hasn't come back yet (PlaytimeStore's open session).
     *
     * The 8 h window stays as it was (it matches PlaytimeStore's counted-session cap): a session left
     * open longer is treated as stale so saves don't stay unsynced for days. [SaveWatchWorker] keeps
     * uploading (upload-only, never overwriting local files) for up to 12 h of an open session, so
     * the periodic worker's skip only affects downloads/conflict handling, which is what must wait.
     */
    private fun gameIsRunning(context: Context): Boolean = PlaySession.isOpen(context)

    companion object {
        private const val NAME = "voltcap-drive-sync"

        /** Starts, changes or stops the schedule to match the Sync settings. */
        fun schedule(context: Context) {
            val settings = CloudSync.state.value.settings
            val work = WorkManager.getInstance(context.applicationContext)
            if (!settings.connected || settings.backgroundOff || !(settings.syncSaves || settings.syncLauncherData)) {
                work.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(
                settings.backgroundEvery.toLong(), TimeUnit.HOURS
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            ).build()
            work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
