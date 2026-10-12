package com.shelf.launcher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.SystemClock
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Live levels of the music that is playing, for the "Now playing" theme.
 *
 * Uses Android's audio [Visualizer] on the output mix (session 0): Voltcap gets a few frequency
 * snapshots a second of the sound going to the speaker. Nothing is recorded or stored and nothing
 * leaves the device, but Android still files this under the "Microphone" (RECORD_AUDIO) permission,
 * so it only runs after the person switched it on (MusicThemeSettings.realAudio) and allowed it.
 *
 * Reference-counted ([acquire] / [release]) and only held while the scene is on screen and music
 * plays. Some players (hardware-offloaded audio) never reach the Visualizer; that shows up as
 * silence while something plays, and the scene then falls back to simulated motion ([live] false).
 */
object AudioReactive {
    /** Number of equalizer bands in [eq]. Declared first: members below are sized with it. */
    const val EQ_BANDS = 8

    /** 0..1 levels, normalised against the song's own recent peaks. */
    @Volatile var bass = 0f; private set
    @Volatile var mid = 0f; private set
    @Volatile var high = 0f; private set
    /**
     * Eight bands from deep bass to air (about 40 Hz to 12 kHz, spaced like an equalizer's sliders),
     * 0..1 each against its own recent peak. Read by the scene's equalizer wave.
     */
    val eq = FloatArray(EQ_BANDS)
    /** elapsedRealtime of the last detected beat (kick), 0 = none yet. */
    @Volatile var beatAt = 0L; private set
    /** True while real levels are coming in (not silent, not failed). */
    @Volatile var live = false; private set
    /** The Visualizer couldn't be created on this device (until the next app start). */
    @Volatile var unsupported = false; private set

    private var visualizer: Visualizer? = null
    private var users = 0
    private var lastSound = 0L
    private var started = 0L
    private val peak = floatArrayOf(1f, 1f, 1f)
    private val eqPeak = FloatArray(EQ_BANDS) { 1f }
    /** Band edges in Hz (log spaced), for [eq]. */
    private val EQ_EDGES = floatArrayOf(35f, 80f, 160f, 320f, 640f, 1300f, 2600f, 5200f, 12000f)
    private var bassAvg = 0f

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Starts listening (main thread). Returns false if it can't: no permission or no Visualizer. */
    @Synchronized
    fun acquire(context: Context): Boolean {
        if (unsupported || !hasPermission(context)) return false
        users++
        if (visualizer != null) return true
        var made: Visualizer? = null
        return try {
            val v = Visualizer(0).also { made = it }
            val range = Visualizer.getCaptureSizeRange()
            v.captureSize = 1024.coerceIn(range[0], range[1])
            v.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
            val rate = minOf(Visualizer.getMaxCaptureRate(), 20_000) // mHz: up to 20 snapshots a second
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit
                override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft != null) onFft(fft, samplingRate)
                }
            }, rate, false, true)
            v.enabled = true
            visualizer = v
            started = SystemClock.elapsedRealtime()
            lastSound = 0L
            true
        } catch (t: Throwable) {
            // UnsupportedOperationException: the device won't share its output (until the next app start).
            // (update 91) Other failures (the audio session busy, a route change) may pass: tried again next time.
            if (t is UnsupportedOperationException) unsupported = true
            // A half set-up Visualizer is let go now that it may be tried again.
            runCatching { made?.release() }
            users = (users - 1).coerceAtLeast(0)
            android.util.Log.w("AudioReactive", "Visualizer unavailable", t)
            false
        }
    }

    @Synchronized
    fun release() {
        if (users == 0) return
        users--
        if (users > 0) return
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
        live = false
        bass = 0f; mid = 0f; high = 0f
        eq.fill(0f)
    }

    private fun onFft(fft: ByteArray, samplingRateMilliHz: Int) {
        val n = fft.size / 2
        if (n < 8) return
        val hzPerBin = samplingRateMilliHz / 1000f / fft.size
        fun band(lo: Float, hi: Float): Float {
            val a = (lo / hzPerBin).toInt().coerceIn(1, n - 1)
            val b = (hi / hzPerBin).toInt().coerceIn(a + 1, n)
            var sum = 0f
            for (k in a until b) sum += hypot(fft[2 * k].toFloat(), fft[2 * k + 1].toFloat())
            return sum / (b - a)
        }
        val raw = floatArrayOf(band(30f, 160f), band(160f, 2000f), band(2000f, 9000f))
        val now = SystemClock.elapsedRealtime()
        if (raw[0] + raw[1] + raw[2] > 0.5f) lastSound = now
        // Silent for 3 s although we're listening: offloaded audio or a quiet passage — simulate.
        live = lastSound != 0L && now - lastSound < 3000

        val out = FloatArray(3)
        for (i in 0..2) {
            // Each band against its own falling peak, so quiet and loud songs both fill 0..1. The peak
            // falls within a few seconds (it used to take ~10), so the picture keeps moving after a loud
            // part instead of sitting low; the floor is low so quiet music still registers.
            peak[i] = max(max(raw[i], peak[i] * 0.985f), 1.5f)
            // A gentle curve lifts the middle of the range: small changes in level read as motion.
            out[i] = sqrt((raw[i] / peak[i]).coerceIn(0f, 1f))
        }
        bass = out[0]; mid = out[1]; high = out[2]
        for (b in 0 until EQ_BANDS) {
            val v = band(EQ_EDGES[b], EQ_EDGES[b + 1])
            eqPeak[b] = max(max(v, eqPeak[b] * 0.985f), 1f)
            eq[b] = sqrt((v / eqPeak[b]).coerceIn(0f, 1f))
        }

        // Kick: bass jumps well above its running average, at most ~4 times a second.
        val isBeat = raw[0] > bassAvg * 1.35f && out[0] > 0.6f && now - beatAt > 240
        bassAvg = bassAvg * 0.92f + raw[0] * 0.08f
        if (isBeat) beatAt = now
    }
}
