package com.shelf.launcher.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/*
 * Update 202: Green Core's own interface sounds, made in code (no recordings, nobody's sounds copied): a short
 * mechanical click for moving, a soft whoosh for the panels changing page, a two-step confirm, a falling "back" and an
 * optional low machine hum (a seamless two-second loop, off by default). Settings › Home › Layout options (Green Core):
 * Interface sounds on / off, their volume, Ambient hum. Off: the layout plays Voltcap's usual UI sounds instead.
 * The samples are written once to the cache folder (like UiSounds' synthesised sets) and played from a small SoundPool.
 */
internal enum class GcSound { CLICK, WHOOSH, CONFIRM, BACK }

internal object GreenCoreSounds {
    private const val RATE = 22050
    @Volatile private var pool: SoundPool? = null
    private val ids = IntArray(GcSound.values().size)
    /** Sample ids SoundPool has finished loading (its callback can come before load() has returned). */
    private val loaded: MutableSet<Int> = java.util.Collections.synchronizedSet(HashSet<Int>())
    @Volatile private var preparing = false
    private var lastClickAt = 0L
    @Volatile private var hum: AudioTrack? = null

    /** Voltcap's own sound switch (Settings › Sounds) — off silences these too. Set by the layout as it draws. */
    @Volatile var master = true

    /** Loads the samples (made on first use, then read from the cache). Call off the main thread. */
    fun prepare(context: Context) {
        if (pool != null || preparing) return
        preparing = true
        try {
            val app = context.applicationContext
            val p = SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            p.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) loaded += sampleId
            }
            GcSound.values().forEach { s ->
                val file = File(app.cacheDir, "gc_snd_${s.name.lowercase()}_v1.wav")
                if (!file.exists()) runCatching {
                    val tmp = File(file.path + ".tmp")
                    tmp.writeBytes(wav(synth(s)))
                    if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
                }
                if (file.exists()) ids[s.ordinal] = runCatching { p.load(file.path, 1) }.getOrDefault(0)
            }
            pool = p
        } catch (_: Throwable) {
        } finally {
            preparing = false
        }
    }

    /** Plays [s] when the layout's sounds are on (else nothing; the caller plays Voltcap's own). */
    fun play(s: GcSound) {
        val p = pool ?: return
        if (!master) return
        val id = ids[s.ordinal]
        if (id == 0 || id !in loaded) return
        // A held D-pad repeats quickly: the clicks are thinned out so they don't pile into a buzz.
        if (s == GcSound.CLICK) {
            val now = SystemClock.uptimeMillis()
            if (now - lastClickAt < 45) return
            lastClickAt = now
        }
        val v = (com.shelf.launcher.data.Features.gcVolume.value / 100f).coerceIn(0f, 1f)
        if (v <= 0f) return
        runCatching { p.play(id, v, v, 1, 0, 1f) }
    }

    /** Whether the layout's own sounds are in use (else Voltcap's UiSounds). */
    val on: Boolean get() = com.shelf.launcher.data.Features.gcSounds.value

    /** Starts / stops the ambient hum. Safe to call often; [volume] 0–1. */
    fun hum(play: Boolean, volume: Float) {
        if (!play || android.os.Build.VERSION.SDK_INT < 23) { stopHum(); return }
        val t = hum ?: makeHum()?.also { hum = it } ?: return
        runCatching {
            t.setVolume((volume * 0.55f).coerceIn(0f, 1f))
            if (t.playState != AudioTrack.PLAYSTATE_PLAYING) t.play()
        }
    }

    fun stopHum() {
        val t = hum ?: return
        hum = null
        runCatching { t.stop() }
        runCatching { t.release() }
    }

    private fun makeHum(): AudioTrack? {
        if (android.os.Build.VERSION.SDK_INT < 23) return null
        return runCatching { buildHum() }.getOrNull()
    }

    @androidx.annotation.RequiresApi(23)
    private fun buildHum(): AudioTrack {
        val pcm = humLoop()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size)
        track.setLoopPoints(0, pcm.size, -1)
        return track
    }

    // ---- The synthesis ----

    private fun synth(s: GcSound): ShortArray = when (s) {
        // A short, dry mechanical tick: two high partials that die away in ~25 ms over a pinch of noise.
        GcSound.CLICK -> normalize(mix(0.03, 0.0 to tone(1850.0, 0.03, 0.9, 140.0), 0.0 to tone(925.0, 0.03, 0.5, 90.0), 0.0 to noise(0.008, 0.6, 0.35)), 0.55)
        // The panels sliding: band-limited noise rising and falling, a low sweep under it.
        GcSound.WHOOSH -> normalize(mix(0.36, 0.0 to whoosh(0.36), 0.0 to sweep(70.0, 150.0, 0.36, 0.35)), 0.5)
        // A low thump, then a short rising chirp.
        GcSound.CONFIRM -> normalize(mix(0.2, 0.0 to tone(120.0, 0.08, 1.0, 40.0), 0.035 to sweep(620.0, 1240.0, 0.11, 0.6)), 0.6)
        // The chirp falling.
        GcSound.BACK -> normalize(sweep(1100.0, 480.0, 0.1, 0.8), 0.5)
    }

    /** A sine at [f] with an exponential decay ([decay] per second). */
    private fun tone(f: Double, seconds: Double, level: Double, decay: Double): DoubleArray {
        val n = (seconds * RATE).toInt()
        return DoubleArray(n) { i -> val t = i.toDouble() / RATE; sin(2 * PI * f * t) * exp(-decay * t) * level }
    }

    /** A sine gliding from [from] to [to] Hz, a quick attack and a smooth fade. */
    private fun sweep(from: Double, to: Double, seconds: Double, level: Double): DoubleArray {
        val n = (seconds * RATE).toInt()
        var phase = 0.0
        return DoubleArray(n) { i ->
            val x = i.toDouble() / n
            val f = from + (to - from) * x
            phase += 2 * PI * f / RATE
            val env = minOf(1.0, x * 25.0) * (1.0 - x) * (1.0 - x)
            sin(phase) * env * level
        }
    }

    private fun noise(seconds: Double, brightness: Double, level: Double): DoubleArray {
        val n = (seconds * RATE).toInt()
        val r = Random(202)
        var last = 0.0
        return DoubleArray(n) { i ->
            val raw = r.nextDouble() * 2 - 1
            last += (raw - last) * brightness
            last * level * (1.0 - i.toDouble() / n)
        }
    }

    /** Noise through a one-pole low-pass whose cut-off rises then falls, swelling in and out. */
    private fun whoosh(seconds: Double): DoubleArray {
        val n = (seconds * RATE).toInt()
        val r = Random(2001)
        var lp = 0.0
        return DoubleArray(n) { i ->
            val x = i.toDouble() / n
            val bell = sin(PI * x)
            val k = 0.02 + 0.22 * bell
            lp += ((r.nextDouble() * 2 - 1) - lp) * k
            lp * bell * bell
        }
    }

    /** A seamless 2 s loop: 55 Hz and its harmonics (whole cycles in the loop), slowly breathing once per loop. */
    private fun humLoop(): ShortArray {
        val n = RATE * 2
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val breathe = 0.82 + 0.18 * sin(2 * PI * 0.5 * t)
            val v = (sin(2 * PI * 55.0 * t) * 0.55 + sin(2 * PI * 110.0 * t) * 0.25 + sin(2 * PI * 82.5 * t) * 0.12 + sin(2 * PI * 165.0 * t) * 0.05) * breathe
            out[i] = (v * 0.5 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun mix(seconds: Double, vararg parts: Pair<Double, DoubleArray>): DoubleArray {
        val n = parts.maxOf { (at, d) -> (at * RATE).toInt() + d.size }.coerceAtLeast((seconds * RATE).toInt())
        val out = DoubleArray(n)
        for ((at, d) in parts) {
            val o = (at * RATE).toInt()
            for (i in d.indices) out[o + i] += d[i]
        }
        return out
    }

    private fun normalize(samples: DoubleArray, peak: Double): ShortArray {
        val max = samples.maxOfOrNull { kotlin.math.abs(it) }?.takeIf { it > 0.0 } ?: 1.0
        val k = peak / max
        // A 3 ms fade at the very end, so no sample ends on a click.
        val fade = (RATE * 0.003).toInt().coerceAtMost(samples.size)
        return ShortArray(samples.size) { i ->
            val tail = if (i >= samples.size - fade) (samples.size - i).toDouble() / fade else 1.0
            (samples[i] * k * tail * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun wav(samples: ShortArray): ByteArray {
        val out = ByteArrayOutputStream()
        fun int(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF); out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF) }
        fun short(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        val data = samples.size * 2
        out.write("RIFF".toByteArray()); int(36 + data); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); int(16); short(1); short(1); int(RATE); int(RATE * 2); short(2); short(16)
        out.write("data".toByteArray()); int(data)
        for (s in samples) short(s.toInt())
        return out.toByteArray()
    }
}
