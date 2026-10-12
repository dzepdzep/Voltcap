package com.shelf.launcher.device.tune

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.shelf.launcher.data.perf.PerfSampler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The tuner's live loop while a profile needs one: AutoTDP, Voltcap's own fan curve / hold-temp
 * control, and re-applying caps the vendor's game daemon resets mid-game. A minimum-priority
 * foreground service so it keeps running while the game has the screen.
 */
class TuneService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!goForeground()) {
            // Started with startForegroundService: stopping without ever calling startForeground still crashes
            // the app, so a plain notification is tried first (update 91).
            GameNotification.tunerOn = false
            runCatching {
                GameNotification.ensureChannels(this)
                startForeground(
                    GameNotification.ID,
                    NotificationCompat.Builder(this, GameNotification.CHANNEL_POWER)
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
        if (loop == null) loop = scope.launch { run() }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        GameNotification.tunerOn = false
        GameNotification.refresh(applicationContext)
        loop?.cancel(); loop = null
        if (sampling) { PerfSampler.release(); sampling = false }
        super.onDestroy()
    }

    private var sampling = false

    private suspend fun run() {
        var tick = 0
        var controller: AutoTdpController? = null
        var controllerFor: TuneProfile? = null
        var integral = 0f
        var lastReassert = 0L
        var lastTemp: Float? = null
        var lastTempAt = 0L
        var lastTickAt = android.os.SystemClock.elapsedRealtime()
        while (scope.isActive) {
            val p = Tuner.currentProfile()
            val st = Tuner.status.value
            val wantSamples = p.autoTdp
            if (wantSamples && !sampling) { PerfSampler.acquire(applicationContext); sampling = true }
            if (!wantSamples && sampling) { PerfSampler.release(); sampling = false }
            val snap = if (sampling) PerfSampler.snapshot.value else null
            // The chip temperature costs a root call: only every tick when the fan or the temperature
            // light needs it, otherwise every 20 s (for the notification).
            val needTemp = (p.fan == FanMode.CURVE || p.fan == FanMode.HOLD_TEMP) || Tuner.ledStatus == LedStatus.TEMPERATURE ||
                Tuner.heatGuardActive
            val nowMs = android.os.SystemClock.elapsedRealtime()
            val temp = snap?.socTempC ?: if (needTemp || nowMs - lastTempAt > 20_000L) {
                lastTempAt = nowMs
                TuneHardware.socTempC().also { lastTemp = it }
            } else lastTemp

            val dt = (nowMs - lastTickAt).coerceIn(0L, 10_000L)
            lastTickAt = nowMs
            if (st.sleeping) { delay(4_000); continue }

            // AutoTDP
            var target: Int? = null
            if (p.autoTdp) {
                if (controllerFor != p) { controller = AutoTdpController(p.autoMode, p.targetFps); controllerFor = p }
                val c = controller!!
                Tuner.setLevel(c.tick(Tuner.autoTdpLevel(), snap?.fps, snap?.powerW))
                target = c.target
            } else { controller = null; controllerFor = null }

            // Fan
            var fan: Int? = null
            if (temp != null && TuneHardware.hasCustomFan()) {
                when (p.fan) {
                    FanMode.CURVE -> fan = curve(p.fanCurve, temp)
                    FanMode.HOLD_TEMP -> {
                        val err = temp - p.holdTempC
                        integral = (integral + err).coerceIn(-60f, 60f)
                        fan = (35 + err * 6f + integral * 0.6f).toInt().coerceIn(15, 100)
                    }
                    else -> integral = 0f
                }
                fan?.let { TuneHardware.setFanPercent(it) }
            }

            // Measurements and the heat guard (games only): the battery's own power reading when the
            // sampler isn't running; watts only count on battery.
            val awake = runCatching { (getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive }.getOrDefault(true)
            if (Tuner.wantsReadings && awake) {
                val (onBattery, batteryW) = batteryPower()
                // The frame rate also when the performance overlay keeps the sampler running.
                val shared = snap ?: PerfSampler.snapshot.value.takeIf { PerfSampler.activeRefs > 0 }
                Tuner.measureSample(shared?.fps, snap?.powerW ?: batteryW, temp, onBattery)
                Tuner.heatTick(temp, dt)
            }
            Tuner.updateLive(snap?.fps, target, snap?.powerW, temp, fan)
            // Caps the vendor's daemon lifted are put back within ~4 s.
            if (nowMs - lastReassert >= 4_000L) { lastReassert = nowMs; runCatching { Tuner.reassert() } }
            if (tick % 5 == 0 && Tuner.ledStatus != LedStatus.OFF) runCatching { statusLight(Tuner.ledStatus, temp) }
            if (tick % 10 == 0) refreshNotification()
            tick++
            // As few wake-ups as the job allows (Voltcap's own power use while a game runs):
            // AutoTDP / hold-temp need a reading a second, a fan curve every 3 s, plain caps every 4 s.
            delay(
                when {
                    p.autoTdp || p.fan == FanMode.HOLD_TEMP -> 1_000L
                    p.fan == FanMode.CURVE -> 3_000L
                    else -> 4_000L
                }
            )
        }
    }

    /** (on battery, watts drawn) from the battery driver; watts null when it doesn't say or a charger is in. */
    private fun batteryPower(): Pair<Boolean, Float?> = runCatching {
        val b = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return@runCatching true to null
        val plugged = b.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (plugged) return@runCatching false to null
        val bm = getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager ?: return@runCatching true to null
        val raw = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val mv = b.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0)
        if (raw == Int.MIN_VALUE || raw == 0 || mv <= 0) return@runCatching true to null
        // Most drivers report microamps; a few milliamps (values far too small to be microamps).
        val amps = Math.abs(raw.toDouble()) / (if (Math.abs(raw) < 20_000) 1_000.0 else 1_000_000.0)
        true to (amps * mv / 1000.0).toFloat().takeIf { it in 0.05f..100f }
    }.getOrDefault(true to null)

    /** Green → amber → red for battery left, or blue → green → red for chip temperature. */
    private fun statusLight(mode: LedStatus, temp: Float?) {
        val hue = when (mode) {
            LedStatus.BATTERY -> {
                val b = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
                val l = b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1); val sc = b.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                if (l < 0 || sc <= 0) return
                (l * 100f / sc).coerceIn(0f, 100f) * 1.2f // 0 % red (0°) … 100 % green (120°)
            }
            LedStatus.TEMPERATURE -> {
                val t = temp ?: return
                // 40 °C and below blue (220°), 60 green (120°), 80+ red (0°).
                when {
                    t <= 40f -> 220f
                    t <= 60f -> 220f - (t - 40f) * 5f
                    else -> (120f - (t - 60f) * 6f).coerceAtLeast(0f)
                }
            }
            LedStatus.OFF -> return
        }
        val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        com.shelf.launcher.data.ControllerLights.apply(applicationContext, true, true, color, 60)
    }

    private fun curve(points: List<FanPoint>, t: Float): Int {
        val pts = points.sortedBy { it.tempC }
        if (t <= pts.first().tempC) return pts.first().percent
        if (t >= pts.last().tempC) return pts.last().percent
        val hi = pts.indexOfFirst { it.tempC >= t }
        val a = pts[hi - 1]; val b = pts[hi]
        val f = (t - a.tempC) / (b.tempC - a.tempC).coerceAtLeast(1)
        return (a.percent + (b.percent - a.percent) * f).toInt()
    }

    /** The in-game notification shared with the overlays (GameNotification): power and live readings. */
    private fun refreshNotification() = GameNotification.refresh(applicationContext)

    private fun goForeground(): Boolean {
        GameNotification.tunerOn = true
        return GameNotification.foreground(this)
    }

    companion object {
        @Volatile private var running = false

        fun ensure(context: Context) {
            if (running) return
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(
                    context.applicationContext, Intent(context.applicationContext, TuneService::class.java)
                )
            }
        }

        fun stop(context: Context) {
            if (!running) return
            runCatching { context.applicationContext.stopService(Intent(context.applicationContext, TuneService::class.java)) }
        }
    }
}
