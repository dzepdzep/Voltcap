package com.shelf.launcher.device.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.shelf.launcher.R
import com.shelf.launcher.data.Features
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The volume mixer in Android's pull-down: one quiet notification listing the apps that are playing (or have their
 * own volume) with − / + steps and a level bar for each. A notification can't hold a real slider (Android only
 * allows buttons, text and progress bars in it), so each tap moves the volume 10 %; the mixer card has the sliders.
 *
 * Refreshes when Voltcap starts, when a game starts / ends, after each tap and with its ↻ button; it does not
 * poll, so an app that starts playing later shows up on the next refresh.
 * Settings: the mixer card › "Show in the notification shade" ([Features.mixerNotification]).
 */
object MixerNotification {
    const val ID = 7731
    private const val CHANNEL = "voltcap_mixer"
    internal const val ACTION = "com.shelf.launcher.MIXER_NOTE"
    private const val ROWS = 4
    private const val STEP = 10
    private val busy = AtomicBoolean(false)
    @Volatile private var again = false
    /** The rows as last drawn: a tap redraws them at once, before the (slow) volume change has finished. */
    private val lock = Any()
    private var last: List<AppMixer.AudioApp> = emptyList()

    /** Posts, updates or (when switched off) removes it, on a background thread. Cheap to call often. */
    fun refreshAsync(context: Context) {
        val app = context.applicationContext
        Features.init(app)
        if (!Features.mixerNotification.state.value) {
            runCatching { app.getSystemService(NotificationManager::class.java)?.cancel(ID) }
            return
        }
        if (!busy.compareAndSet(false, true)) { again = true; return }
        Thread({
            try {
                do {
                    again = false
                    runCatching { post(app) }
                } while (again)
            } finally {
                busy.set(false)
                // A request that came in after the last check isn't dropped (update 91).
                if (again) refreshAsync(app)
            }
        }, "voltcap-mixnote").start()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Volume mixer", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Per-app volume (− / +) in the notification shade."
                    setShowBadge(false)
                }
            )
        }
    }

    /** Blocking (looks up who is playing): call off the main thread. */
    private fun post(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // Nothing here could hold a volume (no root service, no audio effects): don't offer controls that do nothing.
        if (!AppMixer.available(context)) { nm.cancel(ID); return }
        ensureChannel(context)
        val apps = runCatching { AppMixer.apps(context) }.getOrDefault(emptyList())
            .filter { it.playing || it.volume < 100 || it.runningGame != null || it.pinned }
            .take(ROWS)
        synchronized(lock) { last = apps }
        show(context, apps)
    }

    /** Draws the notification for [apps] (no lookups: quick, any thread). */
    private fun show(context: Context, apps: List<AppMixer.AudioApp>) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context)
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val iconTint = if (night) 0xFFFFFFFF.toInt() else 0xFF303030.toInt()

        val big = RemoteViews(context.packageName, R.layout.mixer_notification)
        big.setOnClickPendingIntent(R.id.mix_refresh, broadcast(context, 1, null, 0))
        big.setInt(R.id.mix_refresh, "setColorFilter", iconTint)
        big.setOnClickPendingIntent(R.id.mix_open, open(context))
        big.removeAllViews(R.id.mix_rows)
        apps.forEachIndexed { i, a ->
            val row = RemoteViews(context.packageName, R.layout.mixer_notification_row)
            row.setTextViewText(R.id.mix_name, a.label)
            row.setProgressBar(R.id.mix_bar, 100, a.volume.coerceIn(0, 100), false)
            row.setTextViewText(R.id.mix_pct, "${a.volume}%")
            row.setInt(R.id.mix_minus, "setColorFilter", iconTint)
            row.setInt(R.id.mix_plus, "setColorFilter", iconTint)
            row.setOnClickPendingIntent(R.id.mix_minus, broadcast(context, 10 + i * 2, a.packageName, -STEP))
            row.setOnClickPendingIntent(R.id.mix_plus, broadcast(context, 11 + i * 2, a.packageName, STEP))
            big.addView(R.id.mix_rows, row)
        }
        big.setViewVisibility(R.id.mix_empty, if (apps.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE)

        val line = if (apps.isEmpty()) "Nothing is playing" else apps.joinToString(" · ") { "${it.label} ${it.volume}%" }
        val n: Notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_voltcap)
            .setContentTitle("Volume mixer")
            .setContentText(line)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(big)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(open(context))
            .build()
        nm.notify(ID, n)
    }

    private fun open(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 5, AppMixer.openIntent(context), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** A button: moves [pkg]'s volume by [delta] (pkg null = just look again). Distinct data makes each its own PendingIntent. */
    private fun broadcast(context: Context, code: Int, pkg: String?, delta: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code,
        Intent(context, MixerNotificationReceiver::class.java).setAction(ACTION)
            .setData(Uri.parse("voltcap://mixer/${pkg ?: "-"}/$delta"))
            .putExtra("pkg", pkg).putExtra("delta", delta),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    internal fun handle(context: Context, pkg: String?, delta: Int) {
        if (pkg != null && delta != 0) {
            // Step from what the row shows (it may be the running game's own volume), and redraw right away.
            val row = synchronized(lock) { last.firstOrNull { it.packageName == pkg } }
            val now = row?.volume ?: AppMixer.effective(context)[pkg] ?: 100
            val target = (now + delta).coerceIn(0, 100)
            if (row != null) synchronized(lock) {
                last = last.map { if (it.packageName == pkg) it.copy(volume = target) else it }
                runCatching { show(context, last) }
            }
            val r = runCatching { AppMixer.setVolume(context, pkg, target, forGame = row?.gameOnly == true) }
            if (r.isFailure) android.util.Log.w("MixerNotification", "setVolume failed", r.exceptionOrNull())
        }
        refreshAsync(context)
    }
}

/** The buttons of [MixerNotification] (not exported: only Voltcap's own PendingIntents reach it). */
class MixerNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MixerNotification.ACTION) return
        val pending = goAsync()
        // Setting a volume talks to the helper / audio effects: off the main thread.
        Thread {
            try {
                MixerNotification.handle(context.applicationContext, intent.getStringExtra("pkg"), intent.getIntExtra("delta", 0))
            } finally { pending.finish() }
        }.start()
    }
}
