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
 * Update 149: Settings › Backup › Automatic backup to Google Drive — every day or every week, the whole backup (settings,
 * game data and, if ticked, accounts) goes to My Drive › Voltcap › voltcap-backup.json by itself, so a new or reset
 * handheld can always take everything back at its first start. Needs Google Drive connected (any Drive button does
 * that). Android runs it with a network and the battery not low, never while a game is open.
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    enum class Every(val label: String, val hours: Long) { OFF("Off", 0), DAILY("Every day", 24), WEEKLY("Every week", 24 * 7) }

    override suspend fun doWork(): Result {
        val app = applicationContext
        Features.init(app)
        val every = Features.autoBackup.value
        if (every == Every.OFF) return Result.success()
        CloudSync.load(app)
        if (!CloudSync.state.value.settings.connected) return Result.success()
        if (PlaySession.isOpen(app)) return Result.retry()
        val last = Features.autoBackupAt.value.toLongOrNull() ?: 0L
        // (A little early is fine: the schedule's own timing drifts by a few hours.)
        if (System.currentTimeMillis() - last < TimeUnit.HOURS.toMillis(every.hours) - TimeUnit.HOURS.toMillis(3)) return Result.success()
        return runCatching {
            // Update 154: with this handheld's own setup too (charge limit, performance tuner / AutoTDP, folders,
            // emulators…). After a reinstall they were gone and the backup didn't have them; restoring on another
            // handheld still leaves them out (Backup: only when the backup is from this device, or you pick them).
            Backup.exportToDrive(app, includeGameData = true, includeDeviceSetup = true, includeAccounts = Features.autoBackupAccounts.value)
            Features.autoBackupAt.value = System.currentTimeMillis().toString()
            Result.success()
        }.getOrElse { if (DriveAuth.needsSignIn(app)) Result.success() else Result.retry() }
    }

    companion object {
        private const val NAME = "voltcap-auto-backup"

        /** Starts or stops the schedule to match Settings › Backup › Automatic backup. */
        fun schedule(context: Context) {
            val work = WorkManager.getInstance(context.applicationContext)
            if (Features.autoBackup.value == Every.OFF) {
                work.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                ).build()
            work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
