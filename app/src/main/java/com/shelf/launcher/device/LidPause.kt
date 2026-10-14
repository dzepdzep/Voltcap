package com.shelf.launcher.device

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import com.shelf.launcher.device.audio.AudioReport
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.ShelfNotificationListener

/**
 * Closing a clamshell / dual-screen handheld (AYN Thor, Retroid Pocket Flip…) turns its screens off: then every
 * music app that's playing (Spotify included) and Voltcap's own background music pause (Settings › Device ›
 * "Pause music when closed"). Android has no "lid closed" broadcast, so when the screen goes off the reason is
 * looked up: the hinge-angle sensor (closed = nearly flat shut), else the reason the power service gives for
 * the last sleep ("lid_switch" / "device_folded" against "power_button" / "timeout"). Locking with the power
 * button or the screen timing out does NOT pause unless "Also pause when locking" is on. When the reason
 * can't be told (no sensor, no way to read the power service) it does nothing: music is never stopped on a
 * guess. Music apps are reached through Voltcap's notification access.
 */
object LidPause {
    private const val TAG = "LidPause"
    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        runCatching {
            app.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    if (i.action != Intent.ACTION_SCREEN_OFF) return
                    val onLock = Features.pauseOnLock.value
                    val onClose = Features.pauseOnClose.value
                    if (!onLock && !onClose) return
                    if (onLock) { pauseAll(app); return }
                    // Lid only: working out why the screen went off waits for a sensor / a dump, so off the main thread.
                    val pending = goAsync()
                    Thread {
                        try { if (closedByLid(app) == true) pauseAll(app) }
                        catch (e: Throwable) { Log.w(TAG, "lid check failed", e) }
                        finally { runCatching { pending.finish() } }
                    }.apply { isDaemon = true; start() }
                }
            }, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }
    }

    /** True: the lid / the fold closed. False: some other way (power button, timeout). Null: can't be told. */
    fun closedByLid(context: Context): Boolean? {
        hingeClosed(context)?.let { return it }
        return sleepReasonIsLid(context)
    }

    /** The hinge-angle sensor's one reading: closed when under ~25°. Null when the device has none / it says nothing. */
    private fun hingeClosed(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < 30) return null
        val sm = context.getSystemService(SensorManager::class.java) ?: return null
        val hinge = sm.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) ?: return null
        val latch = CountDownLatch(1)
        var angle: Float? = null
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { if (angle == null) { angle = e.values.firstOrNull(); latch.countDown() } }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        return try {
            if (!sm.registerListener(l, hinge, SensorManager.SENSOR_DELAY_NORMAL)) return null
            latch.await(700, TimeUnit.MILLISECONDS)
            angle?.let { it <= 25f }
        } finally { runCatching { sm.unregisterListener(l) } }
    }

    /** Why the power service last put the screen to sleep, from its own dump (needs DUMP, or the root service). */
    private fun sleepReasonIsLid(context: Context): Boolean? {
        val text = (if (AudioReport.hasDump(context)) runCatching { AudioReport.dump("power") }.getOrNull() else null)
            ?: RootShell.exec("dumpsys power | grep -i -E 'sleepreason|sleep reason|lid'")
            ?: return null
        val reason = Regex("(?i)last\\s*(?:global\\s*)?sleep\\s*reason\\s*[=:]\\s*([A-Za-z_0-9 ]+)").find(text)?.groupValues?.get(1)?.trim()
        if (reason != null) {
            val r = reason.lowercase()
            Log.i(TAG, "last sleep reason: $r")
            return when {
                "lid" in r || "fold" in r || r == "3" || r == "13" -> true // 3 = lid switch, 13 = device fold, when the dump prints numbers
                r.isNotEmpty() -> false
                else -> null
            }
        }
        return null
    }

    fun pauseAll(context: Context) {
        // Voltcap's own loop. Update 202: held until the screen is back on and unlocked (MusicBlackout). It was
        // haltForOtherMusic, which treats the pause as "another app's music" and fades the loop back in after ~7.5 s
        // of nobody else playing — with the lid still shut.
        runCatching { com.shelf.launcher.ui.MusicBlackout.holdForLid(context) }
        // Every app playing music.
        runCatching {
            val m = context.getSystemService(MediaSessionManager::class.java) ?: return
            m.getActiveSessions(ComponentName(context, ShelfNotificationListener::class.java)).forEach { c ->
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) runCatching { c.transportControls.pause() }
            }
        }
    }
}
