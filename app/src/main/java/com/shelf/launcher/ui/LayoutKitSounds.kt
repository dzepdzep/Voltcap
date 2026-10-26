package com.shelf.launcher.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/*
 * Update 344: the console-inspired layouts' own interface sounds (Blades, Bubble home, Pocket camera, Panel dashboard,
 * Game magazine, Rental shop, Terminal), all made in code — no recordings, nobody's sounds copied. Each layout has its
 * own small kit (a move tick, a select, a back, a page / blade change, an open and one special sound), written once to
 * the cache folder like Green Core's and played from one small SoundPool holding only the kit in use.
 * Settings › Home › Layout options (each layout): Interface sounds on / off. Off — or Voltcap's own sounds switched off
 * (Settings › Sounds) — they stay silent and the layout plays Voltcap's usual UI sounds instead (UiSounds).
 */

/** Update 344: whose kit. */
internal enum class LayoutKit { BLADES, BUBBLES, CAMERA, PANELS, MAGAZINE, SHOP, TERMINAL }

/** Update 344: the six sounds of a kit. */
internal enum class KitSound { MOVE, SELECT, BACK, PAGE, OPEN, SPECIAL }

internal object LayoutKitSounds {
    private const val RATE = 22050
    @Volatile private var pool: SoundPool? = null
    @Volatile private var kit: LayoutKit? = null
    private val ids = IntArray(KitSound.values().size)
    private val loaded: MutableSet<Int> = java.util.Collections.synchronizedSet(HashSet<Int>())
    @Volatile private var preparing = false
    private var lastMoveAt = 0L

    /** Voltcap's own sound switch (Settings › Sounds); set by the layout as it draws. */
    @Volatile var master = true

    /** The kit's own switch (the layout's Interface sounds), set by the layout as it draws. */
    @Volatile var enabled = true

    /** Whether the kit plays (else the caller plays Voltcap's own). */
    val on: Boolean get() = enabled && master && pool != null && kit != null

    /** Loads [k]'s samples (made on first use, then read from the cache); a different kit's are let go. Off the main thread. */
    @Synchronized
    fun prepare(context: Context, k: LayoutKit) {
        if (kit == k && pool != null) return
        if (preparing) return
        preparing = true
        try {
            release()
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
            p.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loaded += sampleId }
            KitSound.values().forEach { s ->
                val file = File(app.cacheDir, "kit_${k.name.lowercase()}_${s.name.lowercase()}_v1.wav")
                if (!file.exists()) runCatching {
                    val tmp = File(file.path + ".tmp")
                    tmp.writeBytes(wav(synth(k, s)))
                    if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
                }
                if (file.exists()) ids[s.ordinal] = runCatching { p.load(file.path, 1) }.getOrDefault(0)
            }
            pool = p
            kit = k
        } catch (_: Throwable) {
        } finally {
            preparing = false
        }
    }

    /** Lets [k]'s samples go (its layout left the screen); another kit loaded meanwhile is kept. */
    @Synchronized
    fun releaseIf(k: LayoutKit) { if (kit == k) release() }

    @Synchronized
    private fun release() {
        val p = pool
        pool = null
        kit = null
        loaded.clear()
        ids.fill(0)
        if (p != null) runCatching { p.release() }
    }

    /**
     * Plays [s] from [k]'s kit when it's on and loaded; else Voltcap's own [fallback] (through the home screen's player).
     * A held D-pad's quick repeats are thinned so the ticks don't pile into a buzz.
     */
    fun play(k: LayoutKit, s: KitSound, fallback: UiSound?) {
        val p = pool
        val id = ids[s.ordinal]
        if (enabled && master && p != null && kit == k && id != 0 && id in loaded) {
            if (s == KitSound.MOVE) {
                val now = SystemClock.uptimeMillis()
                if (now - lastMoveAt < 45) return
                lastMoveAt = now
            }
            val v = (com.shelf.launcher.data.Features.kitVolume.value / 100f).coerceIn(0f, 1f)
            if (v > 0f) runCatching { p.play(id, v, v, 1, 0, 1f) }
            return
        }
        if (fallback != null) PocketSounds.play?.invoke(fallback)
    }

    // ---- The synthesis ----

    internal fun synth(k: LayoutKit, s: KitSound): ShortArray = when (k) {
        // Glassy, airy: high soft ticks, a breathy blade swish, a bright two-note chime.
        LayoutKit.BLADES -> when (s) {
            KitSound.MOVE -> normalize(mix(0.05, 0.0 to tone(2350.0, 0.04, 0.8, 120.0), 0.0 to tone(3520.0, 0.03, 0.35, 160.0), 0.0 to noise(0.006, 0.7, 0.2)), 0.42)
            KitSound.PAGE -> normalize(mix(0.42, 0.0 to whoosh(0.42, 11), 0.05 to sweep(900.0, 2600.0, 0.3, 0.18)), 0.5)
            KitSound.SELECT -> normalize(mix(0.32, 0.0 to bell(1318.5, 0.22, 0.8), 0.07 to bell(1975.5, 0.25, 0.7)), 0.55)
            KitSound.BACK -> normalize(mix(0.2, 0.0 to bell(1174.7, 0.12, 0.7), 0.06 to bell(880.0, 0.14, 0.6)), 0.5)
            KitSound.OPEN -> normalize(mix(0.5, 0.0 to whoosh(0.3, 5), 0.08 to bell(1568.0, 0.3, 0.6), 0.16 to bell(2093.0, 0.32, 0.5)), 0.55)
            KitSound.SPECIAL -> normalize(mix(0.7, 0.0 to bell(783.99, 0.6, 0.6), 0.1 to bell(1174.7, 0.55, 0.5), 0.2 to bell(1568.0, 0.5, 0.45)), 0.55)
        }
        // Soft, round, bubbly: pops that fall in pitch, a peel swish, a warm three-note bloom.
        LayoutKit.BUBBLES -> when (s) {
            KitSound.MOVE -> normalize(pop(1100.0, 640.0, 0.05), 0.45)
            KitSound.SELECT -> normalize(mix(0.2, 0.0 to pop(700.0, 1300.0, 0.08), 0.06 to pop(900.0, 1700.0, 0.08)), 0.55)
            KitSound.BACK -> normalize(pop(1300.0, 520.0, 0.09), 0.5)
            KitSound.PAGE -> normalize(mix(0.36, 0.0 to whoosh(0.34, 3), 0.18 to noise(0.05, 0.9, 0.25)), 0.5)
            KitSound.OPEN -> normalize(mix(0.6, 0.0 to bell(659.25, 0.45, 0.5), 0.08 to bell(830.6, 0.45, 0.45), 0.16 to bell(987.8, 0.5, 0.45), 0.0 to whoosh(0.3, 9)), 0.55)
            KitSound.SPECIAL -> normalize(mix(0.4, 0.0 to pop(500.0, 1500.0, 0.12), 0.1 to bell(1318.5, 0.3, 0.5)), 0.55)
        }
        // 1-bit handheld: square-wave blips, a shutter snap.
        LayoutKit.CAMERA -> when (s) {
            KitSound.MOVE -> normalize(square(1046.5, 0.03, 0.6), 0.32)
            KitSound.SELECT -> normalize(mix(0.12, 0.0 to square(1318.5, 0.04, 0.6), 0.05 to square(1760.0, 0.06, 0.6)), 0.34)
            KitSound.BACK -> normalize(mix(0.12, 0.0 to square(880.0, 0.04, 0.6), 0.05 to square(587.3, 0.06, 0.6)), 0.34)
            KitSound.PAGE -> normalize(mix(0.16, 0.0 to square(523.3, 0.03, 0.5), 0.04 to square(784.0, 0.03, 0.5), 0.08 to square(1046.5, 0.05, 0.5)), 0.32)
            KitSound.OPEN -> normalize(mix(0.2, 0.0 to square(659.3, 0.05, 0.5), 0.06 to square(987.8, 0.05, 0.5), 0.12 to square(1318.5, 0.07, 0.5)), 0.32)
            // The shutter: a sharp click, a burst of grit, a second click.
            KitSound.SPECIAL -> normalize(mix(0.22, 0.0 to noise(0.012, 1.0, 0.9), 0.012 to crunch(0.09), 0.12 to noise(0.01, 1.0, 0.8)), 0.5)
        }
        // Clean, later dashboard: muted ticks, a deep panel swing, a soft pad.
        LayoutKit.PANELS -> when (s) {
            KitSound.MOVE -> normalize(mix(0.04, 0.0 to tone(1600.0, 0.03, 0.7, 170.0), 0.0 to tone(800.0, 0.03, 0.4, 120.0)), 0.36)
            KitSound.PAGE -> normalize(mix(0.4, 0.0 to whoosh(0.38, 21), 0.0 to sweep(120.0, 70.0, 0.36, 0.3)), 0.5)
            KitSound.SELECT -> normalize(mix(0.3, 0.0 to pad(523.25, 0.26), 0.0 to pad(784.0, 0.26), 0.04 to tone(1568.0, 0.12, 0.25, 30.0)), 0.5)
            KitSound.BACK -> normalize(mix(0.2, 0.0 to pad(587.3, 0.16), 0.05 to pad(392.0, 0.18)), 0.45)
            KitSound.OPEN -> normalize(mix(0.5, 0.0 to whoosh(0.3, 17), 0.1 to pad(659.25, 0.35), 0.1 to pad(987.8, 0.35)), 0.5)
            KitSound.SPECIAL -> normalize(mix(0.6, 0.0 to pad(523.25, 0.5), 0.12 to pad(659.25, 0.45), 0.24 to pad(784.0, 0.4)), 0.5)
        }
        // Paper: a page turn, a pencil tick, a rubber stamp.
        LayoutKit.MAGAZINE -> when (s) {
            KitSound.MOVE -> normalize(mix(0.03, 0.0 to noise(0.012, 0.95, 0.6), 0.0 to tone(3100.0, 0.015, 0.25, 260.0)), 0.3)
            KitSound.PAGE -> normalize(pageTurn(0.42), 0.52)
            KitSound.SELECT -> normalize(mix(0.14, 0.0 to tone(140.0, 0.1, 1.0, 45.0), 0.0 to noise(0.03, 0.4, 0.5)), 0.6)
            KitSound.BACK -> normalize(pageTurn(0.24), 0.42)
            KitSound.OPEN -> normalize(mix(0.5, 0.0 to pageTurn(0.3), 0.22 to pageTurn(0.26)), 0.5)
            KitSound.SPECIAL -> normalize(mix(0.2, 0.0 to tone(110.0, 0.14, 1.0, 30.0), 0.0 to noise(0.05, 0.3, 0.6)), 0.6)
        }
        // A plastic shop: case clacks, a shelf slide, the door's little bell.
        LayoutKit.SHOP -> when (s) {
            KitSound.MOVE -> normalize(mix(0.04, 0.0 to tone(1450.0, 0.025, 0.6, 210.0), 0.0 to noise(0.01, 0.8, 0.45)), 0.34)
            KitSound.SELECT -> normalize(mix(0.12, 0.0 to clack(0.05), 0.045 to clack(0.04)), 0.55)
            KitSound.BACK -> normalize(mix(0.14, 0.0 to tone(180.0, 0.08, 0.9, 50.0), 0.0 to clack(0.03)), 0.5)
            KitSound.PAGE -> normalize(mix(0.5, 0.0 to whoosh(0.46, 31), 0.0 to sweep(90.0, 60.0, 0.4, 0.2)), 0.45)
            KitSound.OPEN -> normalize(mix(0.2, 0.0 to clack(0.05), 0.03 to whoosh(0.15, 7)), 0.5)
            KitSound.SPECIAL -> normalize(mix(0.9, 0.0 to bell(2637.0, 0.8, 0.6), 0.12 to bell(2093.0, 0.75, 0.5)), 0.45)
        }
        // A keyboard and a speaker beep.
        LayoutKit.TERMINAL -> when (s) {
            KitSound.MOVE -> normalize(keyClick(0.035, 1), 0.42)
            KitSound.SELECT -> normalize(mix(0.14, 0.0 to keyClick(0.045, 2), 0.05 to square(880.0, 0.06, 0.35)), 0.42)
            KitSound.BACK -> normalize(mix(0.12, 0.0 to keyClick(0.03, 3), 0.06 to keyClick(0.03, 4)), 0.4)
            KitSound.PAGE -> normalize(mix(0.2, 0.0 to keyClick(0.03, 5), 0.07 to keyClick(0.03, 6), 0.13 to keyClick(0.03, 7)), 0.4)
            KitSound.OPEN -> normalize(mix(0.2, 0.0 to keyClick(0.045, 8), 0.06 to square(1174.7, 0.08, 0.3)), 0.42)
            // The monitor switching on: a low thump under a short rising whine.
            KitSound.SPECIAL -> normalize(mix(0.5, 0.0 to tone(60.0, 0.3, 1.0, 9.0), 0.02 to sweep(4000.0, 7200.0, 0.4, 0.08)), 0.5)
        }
    }

    private fun tone(f: Double, seconds: Double, level: Double, decay: Double): DoubleArray {
        val n = (seconds * RATE).toInt()
        return DoubleArray(n) { i -> val t = i.toDouble() / RATE; sin(2 * PI * f * t) * exp(-decay * t) * level }
    }

    /** A soft bell: a fundamental and two quieter partials, a quick attack and a long fall. */
    private fun bell(f: Double, seconds: Double, level: Double): DoubleArray {
        val n = (seconds * RATE).toInt()
        return DoubleArray(n) { i ->
            val t = i.toDouble() / RATE
            val att = minOf(1.0, t * 400.0)
            (sin(2 * PI * f * t) + 0.32 * sin(2 * PI * f * 2.0 * t) * exp(-t * 18.0) + 0.12 * sin(2 * PI * f * 3.01 * t) * exp(-t * 30.0)) *
                att * exp(-t * 9.0) * level
        }
    }

    /** A warm pad note: a sine with a soft attack and release. */
    private fun pad(f: Double, seconds: Double): DoubleArray {
        val n = (seconds * RATE).toInt()
        return DoubleArray(n) { i ->
            val x = i.toDouble() / n
            val t = i.toDouble() / RATE
            val env = minOf(1.0, x * 10.0) * (1.0 - x)
            (sin(2 * PI * f * t) + 0.2 * sin(2 * PI * f * 2.0 * t)) * env * 0.6
        }
    }

    private fun sweep(from: Double, to: Double, seconds: Double, level: Double): DoubleArray {
        val n = (seconds * RATE).toInt()
        var phase = 0.0
        return DoubleArray(n) { i ->
            val x = i.toDouble() / n
            phase += 2 * PI * (from + (to - from) * x) / RATE
            sin(phase) * minOf(1.0, x * 25.0) * (1.0 - x) * (1.0 - x) * level
        }
    }

    /** A bubble's pop: a sine gliding [from] → [to], round envelope. */
    private fun pop(from: Double, to: Double, seconds: Double): DoubleArray {
        val n = (seconds * RATE).toInt()
        var phase = 0.0
        return DoubleArray(n) { i ->
            val x = i.toDouble() / n
            val f = from + (to - from) * (1.0 - (1.0 - x) * (1.0 - x))
            phase += 2 * PI * f / RATE
            sin(phase) * sin(PI * minOf(1.0, x * 1.15)).coerceAtLeast(0.0)
        }
    }

    private fun square(f: Double, seconds: Double, level: Double): DoubleArray {
        val n = (seconds * RATE).toInt()
        return DoubleArray(n) { i ->
            val t = i.toDouble() / RATE
            val x = i.toDouble() / n
            (if (sin(2 * PI * f * t) >= 0) 1.0 else -1.0) * level * (if (x > 0.85) (1.0 - x) / 0.15 else 1.0)
        }
    }

    private fun noise(seconds: Double, brightness: Double, level: Double, seed: Int = 344): DoubleArray {
        val n = (seconds * RATE).toInt()
        val r = Random(seed)
        var last = 0.0
        return DoubleArray(n) { i ->
            last += ((r.nextDouble() * 2 - 1) - last) * brightness
            last * level * (1.0 - i.toDouble() / n)
        }
    }

    /** Coarse, stepped noise (a 1-bit grit). */
    private fun crunch(seconds: Double): DoubleArray {
        val n = (seconds * RATE).toInt()
        val r = Random(98)
        var v = 0.0
        return DoubleArray(n) { i ->
            if (i % 9 == 0) v = if (r.nextBoolean()) 0.7 else -0.7
            v * (1.0 - i.toDouble() / n)
        }
    }

    private fun whoosh(seconds: Double, seed: Int): DoubleArray {
        val n = (seconds * RATE).toInt()
        val r = Random(seed)
        var lp = 0.0
        return DoubleArray(n) { i ->
            val x = i.toDouble() / n
            val bell = sin(PI * x)
            lp += ((r.nextDouble() * 2 - 1) - lp) * (0.02 + 0.22 * bell)
            lp * bell * bell
        }
    }

    /** A page turning: a quick rising rustle and a soft flap at the end. */
    private fun pageTurn(seconds: Double): DoubleArray {
        val n = (seconds * RATE).toInt()
        val r = Random(1990)
        var lp = 0.0
        return DoubleArray(n) { i ->
            val x = i.toDouble() / n
            val env = if (x < 0.75) x / 0.75 * (0.6 + 0.4 * sin(x * 40.0)) else (1.0 - x) / 0.25 * 1.3
            lp += ((r.nextDouble() * 2 - 1) - lp) * (0.25 + 0.5 * x)
            lp * env.coerceAtLeast(0.0)
        }
    }

    /** A plastic case knocking: a damped mid tone over a sharp transient. */
    private fun clack(seconds: Double): DoubleArray = mix(seconds, 0.0 to tone(620.0, seconds, 0.8, 90.0), 0.0 to tone(1340.0, seconds, 0.4, 140.0), 0.0 to noise(0.006, 1.0, 0.8))

    /** A mechanical key: a click transient and a short resonant body (a little different for each [variant]). */
    private fun keyClick(seconds: Double, variant: Int): DoubleArray {
        val body = 2100.0 + variant * 137.0
        return mix(seconds, 0.0 to noise(0.004, 1.0, 1.0, 700 + variant), 0.0 to tone(body, seconds, 0.35, 220.0), 0.012 to noise(0.006, 0.6, 0.35, 900 + variant))
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
        val max = samples.maxOfOrNull { abs(it) }?.takeIf { it > 0.0 } ?: 1.0
        val k = peak / max
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

/**
 * Update 344: keeps [kit] loaded while the layout is on screen and its switches current (Voltcap's sounds, the layout's own
 * [ownOn] switch). Call once from the layout's composable.
 */
@androidx.compose.runtime.Composable
internal fun rememberLayoutKit(kit: LayoutKit, ownOn: Boolean, master: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.SideEffect {
        LayoutKitSounds.master = master
        LayoutKitSounds.enabled = ownOn
    }
    androidx.compose.runtime.LaunchedEffect(kit, ownOn) {
        if (ownOn) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { LayoutKitSounds.prepare(context, kit) }
    }
    androidx.compose.runtime.DisposableEffect(kit) { onDispose { LayoutKitSounds.releaseIf(kit) } }
}
