package com.shelf.launcher.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Update 190: artwork keeps downloading while Voltcap isn't on screen — behind a game, another app, or with the screen
 * off. Android freezes a launcher that's in the background, so while "Getting art" runs a quiet foreground service
 * (a minimum-priority notification with the count) keeps Voltcap's process awake; it stops by itself when the
 * download is done. Started from Voltcap (on screen); if Android refuses it, the download just runs while on screen
 * as before.
 */
class ArtDownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!goForeground(text())) {
            // Started with startForegroundService: never leave without startForeground (that crashes the app).
            runCatching { startForeground(NOTIFICATION_ID, base().build()) }
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        if (!wanted) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    private fun base(): NotificationCompat.Builder {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Artwork downloads", NotificationManager.IMPORTANCE_MIN))
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(com.shelf.launcher.R.drawable.ic_stat_voltcap)
            .setContentTitle("Getting game artwork")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
    }

    private fun goForeground(text: String): Boolean = runCatching {
        val n = base().setContentText(text).setProgress(total.coerceAtLeast(0), done.coerceAtLeast(0), total <= 0).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIFICATION_ID, n)
        true
    }.getOrDefault(false)

    companion object {
        private const val CHANNEL = "art_downloads"
        private const val NOTIFICATION_ID = 7341
        @Volatile private var running = false
        @Volatile private var wanted = false
        @Volatile private var done = 0
        @Volatile private var total = 0
        @Volatile private var lastNote = 0L

        private fun text(): String = if (total > 0) "$done of $total games · keeps going behind your games" else "Keeps going behind your games"

        private val users = java.util.concurrent.atomic.AtomicInteger(0)

        /** A download started (Voltcap on screen): the service keeps the process awake until every [start] has its [stop]. */
        fun start(context: Context, of: Int) {
            wanted = true
            done = 0; total = of
            if (users.getAndIncrement() > 0 || running) return
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(context.applicationContext, Intent(context.applicationContext, ArtDownloadService::class.java))
            }
        }

        /** How far it is (the notification's count; at most twice a second). */
        fun progress(context: Context, d: Int, t: Int) {
            done = d; total = t
            val now = android.os.SystemClock.uptimeMillis()
            if (!running || now - lastNote < 2_000L) return
            lastNote = now
            runCatching {
                val app = context.applicationContext
                val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(
                    NOTIFICATION_ID,
                    NotificationCompat.Builder(app, CHANNEL).setSmallIcon(com.shelf.launcher.R.drawable.ic_stat_voltcap)
                        .setContentTitle("Getting game artwork").setContentText(text()).setPriority(NotificationCompat.PRIORITY_MIN)
                        .setSilent(true).setOngoing(true).setOnlyAlertOnce(true).setProgress(t.coerceAtLeast(0), d.coerceAtLeast(0), t <= 0).build()
                )
            }
        }

        /** Done (or stopped): the service and its notification go. */
        fun stop(context: Context) {
            if (users.decrementAndGet() > 0) return
            users.set(0)
            wanted = false
            if (!running) return
            runCatching { context.applicationContext.stopService(Intent(context.applicationContext, ArtDownloadService::class.java)) }
        }
    }
}
