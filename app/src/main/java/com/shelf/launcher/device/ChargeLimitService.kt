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
            // Update 144: started with startForegroundService, so stopping without ever calling startForeground
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
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Charge limit", NotificationManager.IMPORTANCE_MIN))
        }
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(com.shelf.launcher.R.drawable.ic_stat_voltcap)
            .setContentTitle("80 % charge limit · $status")
            .setContentText("Keeps the battery at ${ChargeLimiter.STOP_AT} % while plugged in. Switch it off in the R1 menu.")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
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

        /** Starts (or updates the notification of) the service. Android may refuse from the background: ignored. */
        fun ensureRunning(context: Context, status: ChargeLimiter.Status) {
            if (running && lastLabel == status.label) return
            lastLabel = status.label
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context.applicationContext, ChargeLimitService::class.java).putExtra(EXTRA_STATUS, status.label)
                )
            }
        }

        fun stop(context: Context) {
            if (!running) return
            lastLabel = null
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
