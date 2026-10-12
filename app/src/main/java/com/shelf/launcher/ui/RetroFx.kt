package com.shelf.launcher.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Little 8-bit style effects for the achievement and recap animations, synthesised on first use (no
 * audio files shipped, nothing copied from any game): a swooping "fly" as a badge flies in, a soft
 * glide as it slows, a bright "star pop" as it locks into place, and a thump for a podium cover
 * landing. They follow the UI sounds' on/off switch and volume (see [UiSounds]).
 */
enum class RetroSound { FLY, GLIDE, LOCK, LAND }

object RetroFx {
    private const val RATE = 22050
    private var pool: SoundPool? = null
    private val ids = HashMap<RetroSound, Int>()
    private var lastAt = HashMap<RetroSound, Long>()

    private fun ensure(context: Context): SoundPool {
        pool?.let { return it }
        val p = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            ).build()
        pool = p
        val dir = context.applicationContext.cacheDir
        RetroSound.values().forEach { s ->
            val f = File(dir, "retrofx_${s.name.lowercase()}_v1.wav")
            if (!f.exists()) runCatching { f.writeBytes(wav(synth(s))) }
            if (f.exists()) runCatching { ids[s] = p.load(f.path, 1) }
        }
        return p
    }

    /** Loads the sounds ahead of time (the first [play] would otherwise be silent while they load). */
    fun warm(context: Context) { runCatching { ensure(context) } }

    /** Plays [s] if UI sounds are on. Repeats of the same sound closer than 40 ms are dropped. */
    fun play(context: Context, sounds: UiSounds?, s: RetroSound, pitch: Float = 1f) {
        if (sounds != null && (!sounds.enabled || sounds.volume <= 0f)) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - (lastAt[s] ?: 0L) < 40) return
        lastAt[s] = now
        val vol = (sounds?.volume ?: 0.5f) * when (s) { RetroSound.FLY -> 0.45f; RetroSound.GLIDE -> 0.35f; RetroSound.LOCK -> 0.6f; RetroSound.LAND -> 0.7f }
        runCatching {
            val p = ensure(context)
            ids[s]?.let { p.play(it, vol, vol, 1, 0, pitch.coerceIn(0.5f, 2f)) }
        }
    }

    // ---- Synthesis ----

    private fun square(phase: Double, duty: Double = 0.5) = if ((phase / (2 * PI)) % 1.0 < duty) 1.0 else -1.0

    private fun synth(s: RetroSound): DoubleArray = when (s) {
        // A quick upward-then-down square sweep, like something whooshing past.
        RetroSound.FLY -> {
            val n = (RATE * 0.22).toInt(); var ph = 0.0
            DoubleArray(n) { i ->
                val t = i.toDouble() / n
                val f = 300.0 + 900.0 * sin(t * PI)
                ph += 2 * PI * f / RATE
                square(ph, 0.25) * 0.5 * sin(t * PI)
            }
        }
        // A soft falling triangle-ish slide as the badge slows down.
        RetroSound.GLIDE -> {
            val n = (RATE * 0.18).toInt(); var ph = 0.0
            DoubleArray(n) { i ->
                val t = i.toDouble() / n
                val f = 900.0 - 500.0 * t
                ph += 2 * PI * f / RATE
                (2.0 / PI) * kotlin.math.asin(sin(ph)) * 0.6 * (1.0 - t)
            }
        }
        // The "star pop": two fast bright blips (a fifth up) with a sparkle tail.
        RetroSound.LOCK -> {
            val n = (RATE * 0.3).toInt(); var ph = 0.0
            DoubleArray(n) { i ->
                val sec = i.toDouble() / RATE
                val f = when { sec < 0.045 -> 988.0; sec < 0.09 -> 1480.0; else -> 1976.0 }
                ph += 2 * PI * f / RATE
                val env = if (sec < 0.09) 1.0 else exp(-(sec - 0.09) / 0.05)
                square(ph, 0.5) * 0.45 * env
            }
        }
        // A short low thump with a little pitch drop.
        RetroSound.LAND -> {
            val n = (RATE * 0.16).toInt(); var ph = 0.0
            DoubleArray(n) { i ->
                val sec = i.toDouble() / RATE
                val f = 160.0 - 80.0 * (sec / 0.16)
                ph += 2 * PI * f / RATE
                sin(ph) * exp(-sec / 0.05) + square(ph * 2, 0.5) * 0.15 * exp(-sec / 0.02)
            }
        }
    }

    private fun wav(samples: DoubleArray): ByteArray {
        val peak = samples.maxOfOrNull { kotlin.math.abs(it) }?.takeIf { it > 0 } ?: 1.0
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { data.putShort((it / peak * 0.85 * Short.MAX_VALUE).toInt().toShort()) }
        val body = data.array()
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt(36 + body.size); h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(1); h.putInt(RATE); h.putInt(RATE * 2); h.putShort(2); h.putShort(16)
        h.put("data".toByteArray()); h.putInt(body.size)
        return h.array() + body
    }
}
