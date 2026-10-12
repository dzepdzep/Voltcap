package com.shelf.launcher.data

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Sunday evening: "8 h 20 min this week · most played: Pokémon Emerald ·
 * 2 finished". Settings › Stats › Weekly recap. Nothing is sent after a week
 * without play.
 */
class RecapWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @SuppressLint("MissingPermission") // checked with UnlockNotifier.canNotify
    override suspend fun doWork(): Result {
        val app = applicationContext
        // Once a year (December / the Voltcap anniversary): a nudge that the yearly recap is ready.
        runCatching { notifyYearRecap(app) }
        if (!AppearanceStore(app).load().weeklyRecap || !UnlockNotifier.canNotify(app)) return Result.success()
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val sessions = PlaytimeStore(app).loadSessions().filter { it.start >= weekAgo }
        val total = sessions.sumOf { it.ms }
        if (total < 60_000) return Result.success()

        val top = sessions.groupBy { it.key }.mapValues { (_, s) -> s.sumOf { it.ms } }.maxByOrNull { it.value }
        val topName = top?.key?.let { key ->
            if ('/' !in key) {
                // An app (package name) or a steam:<id> entry: its label, not "com.example".
                runCatching { app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(key, 0)).toString() }
                    .getOrNull() ?: key
            } else com.shelf.launcher.ui.stripRegionTags(key.substringAfterLast('/').substringBeforeLast('.'))
        }
        val finished = CollectionsStore(app).loadStatuses().values
            .count { it.status == PlayStatus.FINISHED && it.at >= weekAgo }
        val days = sessions.map { it.start / (24L * 60 * 60 * 1000) }.distinct().size

        val text = buildString {
            append("${PlaytimeStore.format(total)} over $days day${if (days == 1) "" else "s"}")
            if (topName != null && top != null) append(" · most played: $topName (${PlaytimeStore.format(top.value)})")
            if (finished > 0) append(" · $finished finished")
        }
        if (Build.VERSION.SDK_INT >= 26) {
            app.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Weekly recap", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(com.shelf.launcher.R.drawable.ic_stat_voltcap)
            .setContentTitle("Your week in Voltcap")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(4242, n) }
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun notifyYearRecap(app: Context) {
        if (!UnlockNotifier.canNotify(app)) return
        val due = YearRecap.due(app) ?: return
        val prefs = app.getSharedPreferences("year_recap", Context.MODE_PRIVATE)
        if (prefs.getBoolean("notified_" + due.id, false)) return
        prefs.edit().putBoolean("notified_" + due.id, true).apply()
        if (Build.VERSION.SDK_INT >= 26) {
            app.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Weekly recap", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = app.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
            android.app.PendingIntent.getActivity(app, 4243, it, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(com.shelf.launcher.R.drawable.ic_stat_voltcap)
            .setContentTitle(due.title)
            .setContentText("Your recap is ready: top games, total time, your longest streak. Open Voltcap to see it.")
            .setAutoCancel(true)
            .apply { if (open != null) setContentIntent(open) }
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(4243, n) }
    }

    companion object {
        private const val NAME = "voltcap-weekly-recap"
        private const val CHANNEL = "weekly_recap"

        /** Every Sunday around 18:00, or cancelled when switched off. */
        fun schedule(context: Context, on: Boolean) {
            val work = WorkManager.getInstance(context.applicationContext)
            if (!on) {
                work.cancelUniqueWork(NAME)
                return
            }
            val now = Calendar.getInstance()
            val next = (now.clone() as Calendar).apply {
                set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                set(Calendar.HOUR_OF_DAY, 18); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                if (!after(now)) add(Calendar.WEEK_OF_YEAR, 1)
            }
            val request = PeriodicWorkRequestBuilder<RecapWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
                .build()
            // KEEP: re-scheduling at every start mustn't push Sunday back.
            work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
