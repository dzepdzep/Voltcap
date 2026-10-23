package com.shelf.launcher.device

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Keeps the 80 % charge limit working while a game runs or Voltcap is closed: a minimum-priority
 * foreground service that only exists while the limit is on and the charger is in. It keeps
 * Voltcap's process (and so [ChargeLimiter]'s battery listener) alive; [ChargeLimiter] stops it
 * as soon as the charger comes out or the limit is switched off.
 */
class ChargeLimitService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_STATUS) ?: ChargeLimiter.status.value.label
        if (!goForeground(label)) {
            // Update 144: maybe started with startForegroundService, so stopping without ever calling startForeground
            // would crash the app. A plain notification is tried first, like the tuner's (update 91).
            runCatching {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
                    nm.createNotificationChannel(NotificationChannel(CHANNEL, "Charge limit", NotificationManager.IMPORTANCE_MIN))
                }
            }
            runCatching {
                startForeground(
                    NOTIFICATION_ID,
                    NotificationCompat.Builder(this, CHANNEL)
                        .setSmallIcon(com.shelf.launcher.R.drawable.ic_stat_voltcap)
                        .setContentTitle("Voltcap")
                        .setSilent(true)
                        .build()
                )
            }
            stopSelf()
            return START_NOT_STICKY
        }
        // (Update 317: stopped while it was still starting — see stop — so it goes now it's safely in the foreground.)
        if (intent != null && !wanted) {
            runCatching { androidx.core.app.ServiceCompat.stopForeground(this, androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE) }
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        // The listener lives in the process; make sure it's there (after a restart, say).
        ChargeLimiter.start(applicationContext)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    private fun goForeground(status: String): Boolean = runCatching {
        val notification = notificationFor(this, status)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    }.getOrDefault(false)

    companion object {
        private const val CHANNEL = "charge_limit"
        private const val NOTIFICATION_ID = 7720
        private const val EXTRA_STATUS = "status"
        @Volatile private var running = false
        @Volatile private var lastLabel: String? = null
        /** Update 317: whether the limit wants the service (a start still on its way checks it once it's in the foreground). */
        @Volatile private var wanted = false

        private fun notificationFor(context: Context, status: String): android.app.Notification {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Charge limit", NotificationManager.IMPORTANCE_MIN))
            }
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(com.shelf.launcher.R.drawable.ic_stat_voltcap)
                .setContentTitle("80 % charge limit · $status")
                .setContentText("Keeps the battery at ${ChargeLimiter.STOP_AT} % while plugged in. Switch it off in the R1 menu.")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        }

        /** Starts (or updates the notification of) the service. Android may refuse from the background: ignored. */
        fun ensureRunning(context: Context, status: ChargeLimiter.Status) {
            wanted = true
            if (running && lastLabel == status.label) return
            val app = context.applicationContext
            // Update 317 (Flip 2 crash on a first launch, ForegroundServiceDidNotStartInTimeException): a new label while it
            // runs only updates its notification. Each startForegroundService gives the service 10 s to call
            // startForeground on the main thread — a main thread busy with the first launch's loading missed it.
            if (running) {
                lastLabel = status.label
                runCatching {
                    (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                        .notify(NOTIFICATION_ID, notificationFor(app, status.label))
                }
                return
            }
            lastLabel = status.label
            val intent = Intent(app, ChargeLimitService::class.java).putExtra(EXTRA_STATUS, status.label)
            // With Voltcap in front a plain start is allowed, and it has no deadline to miss (the service still puts
            // itself in the foreground at once). From the background Android refuses it: the foreground start then.
            val started = runCatching { app.startService(intent) }.isSuccess
            if (!started) runCatching { androidx.core.content.ContextCompat.startForegroundService(app, intent) }
        }

        fun stop(context: Context) {
            wanted = false
            lastLabel = null
            // (Update 317: not while a start is on its way — stopping a service started for the foreground before it got
            // there crashes the app; it stops itself as soon as it's in, seeing it's no longer wanted.)
            if (!running) return
            runCatching { context.applicationContext.stopService(Intent(context.applicationContext, ChargeLimitService::class.java)) }
        }
    }
}

/** After a restart: picks the charge limit up again (and starts the service if the charger is in). */
class ChargeLimitBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != "android.intent.action.QUICKBOOT_POWERON") return
        ChargeLimiter.start(context.applicationContext)
        // The performance tuner's device-wide profile, if it's set to come back after a restart.
        if (com.shelf.launcher.device.tune.TuneStore(context).applyOnBoot) com.shelf.launcher.device.tune.Tuner.start(context.applicationContext)
    }
}
