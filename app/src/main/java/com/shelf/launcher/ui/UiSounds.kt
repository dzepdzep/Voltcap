package com.shelf.launcher.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class UiSound(val label: String) {
    MOVE("Moving"),
    SELECT("Selecting"),
    BACK("Going back"),
    OPEN("Opening menus"),
    LAUNCH("Starting a game"),
    INTRO("Start-up animation"),
    SYNC_DONE("Saves backed up"),
    SYNC_ALERT("Save sync needs you")
}

/** Provided at the root, so every screen and popup can play UI sounds. */
val LocalUiSounds = staticCompositionLocalOf<UiSounds?> { null }

/**
 * Short interface sounds: a soft tick for moving, a rising two-note chime for
 * selecting, the same falling for going back, and an airy whoosh for opening
 * menus. Console / Electric / Soft chimes are synthesised on first run; the
 * Chill, 32-bit and Dark fantasy sets are produced sound files (res/raw/ui_*,
 * made with tools/audio/ui_sounds.py). Meant to feel console-like without
 * copying anyone's sounds. Plus optional haptics.
 */
class UiSounds(context: Context) {
    private val appContext = context.applicationContext
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                // Game audio follows the media volume. The old "sonification"
                // channel follows the system/touch volume, which is often
                // muted — that's why the sounds couldn't be heard.
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    /** What each sound plays right now (the person's own file, or the current set's). */
    private val ids = HashMap<UiSound, Int>()
    /** Built-in samples already in the pool, by "SET/SOUND": switching sets back and forth reuses them. */
    private val builtin = HashMap<String, Int>()
    /** The person's own sounds (they win over every set). */
    private val custom = HashMap<UiSound, Int>()

    var enabled = true
    var volume = 0.5f
    var haptics = true
    /** 0-100. */
    var hapticStrength = 60
    /** Any attached view — last-resort haptic feedback. */
    var hapticView: View? = null
    private val vibrator: android.os.Vibrator? = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
    }.getOrNull()

    private var lastMoveAt = 0L

    /**
     * Built-in sound set; changing it swaps every built-in sound at once. Sets already used stay
     * loaded, so a theme that brings its own set (and switching back) costs nothing the second time.
     */
    var style: com.shelf.launcher.data.SoundStyle = com.shelf.launcher.data.SoundStyle.CONSOLE
        set(value) {
            if (field == value) return
            field = value
            UiSound.values().forEach { assign(it) }
        }

    /** Waiting for its file to finish loading (right after start-up, or a set heard for the first time). */
    private var pendingSample = 0

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && sampleId != 0 && sampleId == pendingSample) {
                pendingSample = 0
                playSample(sampleId)
            }
        }
        UiSound.values().forEach { loadCustom(it); assign(it) }
    }

    private fun loadCustom(sound: UiSound) {
        val file = CustomSounds.file(appContext, sound) ?: return
        val id = runCatching { pool.load(file.path, 1) }.getOrDefault(0)
        if (id != 0) custom[sound] = id
    }

    /** Points [sound] at the person's own file, else the current set's sample (loading it if needed). */
    private fun assign(sound: UiSound) {
        val id = custom[sound] ?: builtinId(style, sound)
        if (id != 0) ids[sound] = id else ids.remove(sound)
    }

    /** The built-in sample for [sound] in [set] (0 if it can't be made). */
    private fun builtinId(set: com.shelf.launcher.data.SoundStyle, sound: UiSound): Int {
        // The start-up and save-sync cues are the same in every set.
        val key = if (sound in SHARED) "ALL/${sound.name}" else "${set.name}/${sound.name}"
        builtin[key]?.let { return it }
        val id = loadBuiltin(set, sound)
        if (id != 0) builtin[key] = id
        return id
    }

    private fun loadBuiltin(set: com.shelf.launcher.data.SoundStyle, sound: UiSound): Int {
        // Produced sound files (res/raw): the start-up cue (timed to the intro's hit at 1.05 s), the
        // save-sync cues, and the Chill / 32-bit / Dark fantasy sets.
        val raw = rawFor(set, sound)
        if (raw != 0) {
            val id = runCatching { pool.load(appContext, raw, 1) }.getOrDefault(0)
            if (id != 0) return id
        }
        val file = File(appContext.cacheDir, "ui_${sound.name.lowercase()}_${set.name.lowercase()}_v3.wav")
        // Written to a temp file first: a half-written file (killed mid-write) would otherwise stay broken.
        if (!file.exists()) runCatching {
            val tmp = File(file.path + ".tmp")
            tmp.writeBytes(wav(synthesize(sound, set)))
            if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
        }
        return if (file.exists()) runCatching { pool.load(file.path, 1) }.getOrDefault(0) else 0
    }

    private fun rawFor(set: com.shelf.launcher.data.SoundStyle, sound: UiSound): Int {
        return when (sound) {
            UiSound.INTRO -> com.shelf.launcher.R.raw.voltcap_intro
            UiSound.SYNC_DONE -> com.shelf.launcher.R.raw.sync_done
            UiSound.SYNC_ALERT -> com.shelf.launcher.R.raw.sync_alert
            else -> when (set) {
                com.shelf.launcher.data.SoundStyle.CHILL -> when (sound) {
                    UiSound.MOVE -> com.shelf.launcher.R.raw.ui_chill_move
                    UiSound.SELECT -> com.shelf.launcher.R.raw.ui_chill_select
                    UiSound.BACK -> com.shelf.launcher.R.raw.ui_chill_back
                    UiSound.OPEN -> com.shelf.launcher.R.raw.ui_chill_open
                    else -> com.shelf.launcher.R.raw.ui_chill_launch
                }
                com.shelf.launcher.data.SoundStyle.RETRO32 -> when (sound) {
                    UiSound.MOVE -> com.shelf.launcher.R.raw.ui_retro32_move
                    UiSound.SELECT -> com.shelf.launcher.R.raw.ui_retro32_select
                    UiSound.BACK -> com.shelf.launcher.R.raw.ui_retro32_back
                    UiSound.OPEN -> com.shelf.launcher.R.raw.ui_retro32_open
                    else -> com.shelf.launcher.R.raw.ui_retro32_launch
                }
                com.shelf.launcher.data.SoundStyle.DARK -> when (sound) {
                    UiSound.MOVE -> com.shelf.launcher.R.raw.ui_dark_move
                    UiSound.SELECT -> com.shelf.launcher.R.raw.ui_dark_select
                    UiSound.BACK -> com.shelf.launcher.R.raw.ui_dark_back
                    UiSound.OPEN -> com.shelf.launcher.R.raw.ui_dark_open
                    else -> com.shelf.launcher.R.raw.ui_dark_launch
                }
                else -> 0
            }
        }
    }

    /** Call after a custom sound was picked or reset. */
    fun reload(sound: UiSound) {
        custom.remove(sound)?.let { runCatching { pool.unload(it) } }
        loadCustom(sound)
        assign(sound)
    }

    /** Plays [sound] from [set] without switching to it (the sound-style chips in Settings). */
    fun preview(set: com.shelf.launcher.data.SoundStyle, sound: UiSound) {
        if (!enabled || volume <= 0f) return
        val id = custom[sound] ?: builtinId(set, sound)
        if (id != 0 && !playSample(id)) pendingSample = id
    }

    private var lastActionAt = 0L

    fun play(sound: UiSound) {
        UiSoundClock.lastPlayed = System.currentTimeMillis()
        if (sound == UiSound.MOVE) {
            // Holding the D-pad fires moves very fast — thin them out.
            val now = SystemClock.uptimeMillis()
            if (now - lastMoveAt < 45) return
            lastMoveAt = now
        } else {
            // One press = one sound. A press often reaches two handlers (the button's own click
            // sound + the popup it opens, or key-down and key-up paths): the second within 90 ms is
            // dropped instead of doubling up.
            val now = SystemClock.uptimeMillis()
            if (now - lastActionAt < 90) return
            lastActionAt = now
        }
        if (enabled && volume > 0f) {
            val level = volume * if (sound == UiSound.MOVE) 0.7f else 1f
            ids[sound]?.let { pool.play(it, level, level, 1, 0, 1f) }
        }
        if (haptics) buzz(sound)
    }

    /**
     * A real vibration rather than the view's "haptic feedback": that one
     * follows Android's touch-vibration setting (often off on handhelds) and
     * its tick is too faint to feel through a grip. Falls back to the
     * controller's own rumble when the device has no motor of its own.
     */
    // FLAG_IGNORE_GLOBAL_SETTING is deprecated, but it is only the last-resort
    // fallback for devices with no motor and no controller rumble.
    @Suppress("DEPRECATION")
    fun buzz(sound: UiSound) {
        val strength = hapticStrength.coerceIn(0, 100)
        if (strength == 0) return
        val ms = when (sound) {
            UiSound.MOVE -> 12L
            UiSound.SELECT -> 26L
            UiSound.BACK -> 20L
            UiSound.OPEN -> 30L
            UiSound.LAUNCH -> 70L
            UiSound.INTRO -> 90L
            UiSound.SYNC_DONE -> 18L
            UiSound.SYNC_ALERT -> 40L
        }
        val target = vibrator?.takeIf { it.hasVibrator() } ?: gamepadVibrator()
        if (target == null) {
            hapticView?.performHapticFeedback(
                if (sound == UiSound.MOVE) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
            return
        }
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val effect = if (target.hasAmplitudeControl()) {
                    android.os.VibrationEffect.createOneShot(ms, (strength * 255 / 100).coerceIn(1, 255))
                } else {
                    // No amplitude control: strength stretches the pulse instead.
                    android.os.VibrationEffect.createOneShot(
                        (ms * (0.5f + strength / 100f)).toLong().coerceAtLeast(6L),
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    )
                }
                @Suppress("DEPRECATION")
                target.vibrate(effect, VIBRATION_ATTRIBUTES)
            } else {
                @Suppress("DEPRECATION")
                target.vibrate(ms)
            }
        }
    }

    /** The first connected controller with rumble, for devices without a motor. */
    private fun gamepadVibrator(): android.os.Vibrator? = runCatching {
        android.view.InputDevice.getDeviceIds().asSequence()
            .mapNotNull { android.view.InputDevice.getDevice(it) }
            .filter { it.supportsSource(android.view.InputDevice.SOURCE_GAMEPAD) }
            .mapNotNull { device ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    device.vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    device.vibrator
                }
            }
            .firstOrNull { it.hasVibrator() }
    }.getOrNull()

    /**
     * Like [play], but if the sound is still loading (the intro plays the
     * moment the app opens) it starts as soon as it's ready instead of being
     * silently skipped.
     */
    fun playWhenReady(sound: UiSound) {
        if (!enabled || volume <= 0f) return
        val id = ids[sound] ?: return
        if (!playSample(id)) pendingSample = id
    }

    private fun playSample(id: Int): Boolean = pool.play(id, volume, volume, 2, 0, 1f) != 0

    fun release() = pool.release()

    // ---- Synthesis (44.1 kHz, 16-bit mono) ----

    private fun synthesize(sound: UiSound, set: com.shelf.launcher.data.SoundStyle): ShortArray =
        when {
            sound == UiSound.INTRO -> classic(sound)
            set == com.shelf.launcher.data.SoundStyle.ELECTRIC -> electric(sound)
            set == com.shelf.launcher.data.SoundStyle.CLASSIC -> classic(sound)
            // Console, and the produced sets' stand-in if their file can't be opened.
            else -> console(sound)
        }

    /**
     * The console set: soft, rounded plucks with a little room around them —
     * the calm, airy feel of a PlayStation-style menu (made from scratch, not
     * copied). Quiet enough for every step, warm rather than bright.
     */
    private fun console(sound: UiSound): ShortArray = normalize(
        when (sound) {
            // A small, soft "tock" — barely there, like a bubble.
            UiSound.MOVE -> room(
                pluck(1175.0, 0.06, decay = 0.016, level = 1.0),
                delay = 0.028, gain = 0.22, repeats = 2
            )
            // A warm two-note pluck (a fifth) with a soft sparkle after it.
            UiSound.SELECT -> room(
                layer(
                    0.3,
                    0.0 to pluck(660.0, 0.25, decay = 0.07),
                    0.0 to pluck(990.0, 0.25, decay = 0.06, level = 0.6),
                    0.045 to pluck(1320.0, 0.22, decay = 0.06, level = 0.35)
                ),
                delay = 0.05, gain = 0.25, repeats = 3
            )
            // One low, soft pluck going away.
            UiSound.BACK -> room(
                layer(
                    0.25,
                    0.0 to pluck(587.0, 0.22, decay = 0.06, glideTo = 540.0),
                    0.0 to pluck(880.0, 0.18, decay = 0.04, level = 0.3)
                ),
                delay = 0.05, gain = 0.22, repeats = 3
            )
            // An airy lift: a soft swell rising into a pluck.
            UiSound.OPEN -> room(
                layer(
                    0.42,
                    0.0 to pad(listOf(440.0, 660.0), 0.2, attack = 0.12, level = 0.35),
                    0.0 to noise(0.2, brightness = 0.08, level = 0.25),
                    0.13 to pluck(880.0, 0.25, decay = 0.07, level = 0.8),
                    0.13 to pluck(1320.0, 0.2, decay = 0.05, level = 0.3)
                ),
                delay = 0.06, gain = 0.28, repeats = 3
            )
            // Starting a game: a deep, warm swell that blooms and fades, with
            // a soft pluck on top as it opens.
            UiSound.LAUNCH -> room(
                layer(
                    1.2,
                    0.0 to pad(listOf(220.0, 330.0, 440.0, 660.0), 1.0, attack = 0.22, level = 0.8),
                    0.0 to noise(0.5, brightness = 0.05, level = 0.3),
                    0.2 to pluck(880.0, 0.5, decay = 0.14, level = 0.6),
                    0.26 to pluck(1320.0, 0.45, decay = 0.12, level = 0.35)
                ),
                delay = 0.09, gain = 0.3, repeats = 4
            )
            UiSound.INTRO, UiSound.SYNC_DONE, UiSound.SYNC_ALERT -> DoubleArray(1)
        }
    )

    /** A rounded sine pluck (a touch of 2nd harmonic), soft attack, optional glide. */
    private fun pluck(
        freq: Double,
        seconds: Double,
        decay: Double,
        level: Double = 1.0,
        glideTo: Double = freq
    ): DoubleArray {
        val n = (RATE * seconds).toInt()
        var phase = 0.0
        return DoubleArray(n) { i ->
            val t = i / RATE.toDouble()
            val f = freq + (glideTo - freq) * minOf(1.0, t / seconds)
            phase += 2 * PI * f / RATE
            val attack = minOf(1.0, t / 0.004)
            (sin(phase) + 0.18 * sin(phase * 2)) * attack * exp(-t / decay) * level
        }
    }

    /** A soft chord that swells in over [attack] seconds and fades out. */
    private fun pad(freqs: List<Double>, seconds: Double, attack: Double, level: Double = 1.0): DoubleArray {
        val n = (RATE * seconds).toInt()
        return DoubleArray(n) { i ->
            val t = i / RATE.toDouble()
            val env = minOf(1.0, t / attack) * exp(-maxOf(0.0, t - attack) / (seconds * 0.35))
            var v = 0.0
            freqs.forEachIndexed { k, f -> v += sin(2 * PI * f * t) / (k + 1) }
            v * env * level / freqs.size
        }
    }

    /** A few quiet echoes — the "room" that makes it feel airy rather than dry. */
    private fun room(dry: DoubleArray, delay: Double, gain: Double, repeats: Int): DoubleArray {
        val step = (delay * RATE).toInt()
        val out = DoubleArray(dry.size + step * repeats)
        var g = 1.0
        for (r in 0..repeats) {
            val offset = r * step
            for (i in dry.indices) out[offset + i] += dry[i] * g
            g *= gain
        }
        return out
    }

    /**
     * The electric set, to fit Voltcap: short synth blips that glide in pitch,
     * a little static crackle, and a power-up for starting a game — techy,
     * but short and soft enough to hear on every step.
     */
    private fun electric(sound: UiSound): ShortArray = normalize(
        when (sound) {
            // A tiny digital blip, falling slightly — like a relay clicking.
            UiSound.MOVE -> layer(
                0.05,
                0.0 to blip(2600.0, 1900.0, 0.035, duty = 0.3, decay = 0.012),
                0.0 to noise(0.006, brightness = 0.6, level = 0.25)
            )
            // Two rising blips with a spark on top.
            UiSound.SELECT -> layer(
                0.26,
                0.0 to blip(880.0, 1320.0, 0.09, duty = 0.25, decay = 0.04),
                0.06 to blip(1320.0, 1980.0, 0.16, duty = 0.25, decay = 0.06),
                0.06 to zap(0.08, level = 0.25)
            )
            // The same, falling — powering down.
            UiSound.BACK -> layer(
                0.24,
                0.0 to blip(1480.0, 990.0, 0.09, duty = 0.3, decay = 0.04, level = 0.85),
                0.055 to blip(990.0, 620.0, 0.15, duty = 0.3, decay = 0.06, level = 0.85)
            )
            // A charging sweep up into a bright blip.
            UiSound.OPEN -> layer(
                0.32,
                0.0 to sweep(300.0, 1600.0, 0.16, level = 0.5),
                0.0 to noise(0.14, brightness = 0.25, level = 0.2),
                0.14 to blip(1760.0, 2350.0, 0.16, duty = 0.2, decay = 0.06, level = 0.7)
            )
            // Power-up: a rising charge, the spark, a low thump and a bright
            // synth chord that rings out.
            UiSound.LAUNCH -> layer(
                1.0,
                0.0 to sweep(180.0, 1400.0, 0.3, level = 0.6),
                0.28 to zap(0.3),
                0.28 to thump(0.22),
                0.3 to blip(1046.5, 1046.5, 0.7, duty = 0.35, decay = 0.25, level = 0.55),
                0.3 to blip(1318.5, 1318.5, 0.7, duty = 0.35, decay = 0.22, level = 0.4),
                0.3 to blip(1568.0, 1568.0, 0.7, duty = 0.35, decay = 0.2, level = 0.35)
            )
            UiSound.INTRO, UiSound.SYNC_DONE, UiSound.SYNC_ALERT -> DoubleArray(1)
        }
    )

    /**
     * A pulse-wave synth note gliding from [from] to [to] Hz, softened by a
     * gentle low-pass so it's buzzy but not harsh.
     */
    private fun blip(
        from: Double,
        to: Double,
        seconds: Double,
        duty: Double,
        decay: Double,
        level: Double = 1.0
    ): DoubleArray {
        val n = (RATE * seconds).toInt()
        var phase = 0.0
        var smooth = 0.0
        return DoubleArray(n) { i ->
            val t = i / RATE.toDouble()
            val p = i / n.toDouble()
            val freq = from + (to - from) * (1 - exp(-p * 5))
            phase = (phase + freq / RATE) % 1.0
            val raw = if (phase < duty) 1.0 else -1.0
            smooth += 0.35 * (raw - smooth)
            val attack = minOf(1.0, t / 0.002)
            smooth * attack * exp(-t / decay) * level * 0.6
        }
    }

    /** A saw wave rising from [from] to [to] Hz and swelling in — charging up. */
    private fun sweep(from: Double, to: Double, seconds: Double, level: Double = 1.0): DoubleArray {
        val n = (RATE * seconds).toInt()
        var phase = 0.0
        var smooth = 0.0
        return DoubleArray(n) { i ->
            val p = i / n.toDouble()
            val freq = from * Math.pow(to / from, p)
            phase = (phase + freq / RATE) % 1.0
            smooth += 0.2 * ((phase * 2 - 1) - smooth)
            smooth * sin(PI * p).let { it * it } * level
        }
    }

    private fun classic(sound: UiSound): ShortArray = normalize(
        when (sound) {
            // A soft, woody tick — short enough to fire on every step.
            UiSound.MOVE -> layer(
                0.07,
                0.0 to bell(1568.0, 0.06, decay = 0.014, partials = WOOD),
                0.0 to noise(0.008, brightness = 0.35, level = 0.35)
            )
            // Two glassy notes going up (B5 → F#6).
            UiSound.SELECT -> layer(
                0.42,
                0.0 to bell(987.8, 0.3, decay = 0.09, partials = GLASS),
                0.06 to bell(1480.0, 0.36, decay = 0.11, partials = GLASS)
            )
            // The same, going down (D6 → G5), a little softer.
            UiSound.BACK -> layer(
                0.4,
                0.0 to bell(1174.7, 0.26, decay = 0.07, partials = GLASS, level = 0.8),
                0.055 to bell(784.0, 0.34, decay = 0.1, partials = GLASS, level = 0.8)
            )
            // Airy swell with a chime on top.
            UiSound.OPEN -> layer(
                0.4,
                0.0 to whoosh(0.2),
                0.09 to bell(1318.5, 0.3, decay = 0.1, partials = GLASS, level = 0.45)
            )
            // Start-up fanfare: a low thump, a quick C-major arpeggio and a
            // ringing top chord — clearly different from the menu sounds.
            UiSound.LAUNCH -> layer(
                1.1,
                0.0 to thump(0.2),
                0.0 to bell(523.25, 0.3, decay = 0.1, partials = GLASS, level = 0.7),
                0.07 to bell(659.25, 0.3, decay = 0.1, partials = GLASS, level = 0.7),
                0.14 to bell(783.99, 0.3, decay = 0.1, partials = GLASS, level = 0.7),
                0.21 to bell(1046.5, 0.85, decay = 0.3, partials = GLASS),
                0.21 to bell(1318.5, 0.85, decay = 0.3, partials = GLASS, level = 0.6),
                0.21 to bell(1568.0, 0.85, decay = 0.28, partials = GLASS, level = 0.45)
            )
            // Played from res/raw (sync_done / sync_alert); nothing to synthesise.
            UiSound.SYNC_DONE, UiSound.SYNC_ALERT -> DoubleArray(1)
            // Voltcap's intro, timed to the animation: the capsule drops in
            // (whoosh) and lands (thump + woody wobble ticks), colour sweeps
            // through it (rising hum), the bolt strikes (crack + zap + low
            // boom) and the name rises with a bright G-major chime.
            UiSound.INTRO -> layer(
                2.3,
                0.08 to whoosh(0.34),
                0.42 to thump(0.18),
                0.42 to noise(0.03, brightness = 0.4, level = 0.5),
                0.55 to bell(440.0, 0.06, decay = 0.02, partials = WOOD, level = 0.35),
                0.68 to bell(440.0, 0.06, decay = 0.02, partials = WOOD, level = 0.2),
                0.7 to whoosh(0.42),
                1.1 to noise(0.05, brightness = 0.8, level = 1.0),
                1.1 to zap(0.42),
                1.1 to thump(0.35),
                1.35 to bell(783.99, 0.35, decay = 0.1, partials = GLASS, level = 0.5),
                1.41 to bell(987.77, 0.35, decay = 0.1, partials = GLASS, level = 0.5),
                1.47 to bell(1174.66, 0.9, decay = 0.32, partials = GLASS, level = 0.65),
                1.47 to bell(1567.98, 0.9, decay = 0.3, partials = GLASS, level = 0.45),
                1.47 to bell(783.99, 0.9, decay = 0.35, partials = GLASS, level = 0.3)
            )
        }
    )

    /** A struck tone: several partials (frequency ratio to strength), the
     * higher ones dying away faster, like a bell or a glass. */
    private fun bell(
        freq: Double,
        seconds: Double,
        decay: Double,
        partials: List<Pair<Double, Double>>,
        level: Double = 1.0
    ): DoubleArray {
        val n = (RATE * seconds).toInt()
        val total = partials.sumOf { it.second }
        return DoubleArray(n) { i ->
            val t = i / RATE.toDouble()
            val attack = minOf(1.0, t / 0.003) // 3 ms fade-in, no click
            var v = 0.0
            for ((ratio, amp) in partials) {
                v += amp * sin(2 * PI * freq * ratio * t) * exp(-t * kotlin.math.sqrt(ratio) / decay)
            }
            v / total * attack * level
        }
    }

    /** A short burst of filtered noise. */
    private fun noise(seconds: Double, brightness: Double, level: Double): DoubleArray {
        val n = (RATE * seconds).toInt()
        val random = Random(3)
        var filtered = 0.0
        return DoubleArray(n) { i ->
            filtered += brightness * ((random.nextDouble() * 2 - 1) - filtered)
            filtered * level * exp(-i / (n * 0.35))
        }
    }

    /** A soft low thump that glides down in pitch. */
    private fun thump(seconds: Double): DoubleArray {
        val n = (RATE * seconds).toInt()
        var phase = 0.0
        return DoubleArray(n) { i ->
            val t = i / RATE.toDouble()
            val freq = 60.0 + 90.0 * exp(-t / 0.04)
            phase += 2 * PI * freq / RATE
            sin(phase) * exp(-t / 0.07) * 0.9
        }
    }

    /** Filtered noise whose brightness rises, with a faint rising tone under it. */
    private fun whoosh(seconds: Double): DoubleArray {
        val n = (RATE * seconds).toInt()
        val random = Random(7)
        var filtered = 0.0
        return DoubleArray(n) { i ->
            val p = i / n.toDouble()
            val cutoff = 0.02 + 0.2 * p // one-pole low-pass opening up
            filtered += cutoff * ((random.nextDouble() * 2 - 1) - filtered)
            val env = sin(PI * p) // swell in and out
            val glide = 0.2 * sin(2 * PI * (380.0 + 520.0 * p) * i / RATE)
            (filtered * 2.4 + glide) * env * 0.6
        }
    }

    /**
     * An electric crackle: a harsh buzz sweeping down in pitch, chopped into
     * random bursts, over bright noise — the bolt.
     */
    private fun zap(seconds: Double, level: Double = 1.0): DoubleArray {
        val n = (RATE * seconds).toInt()
        val random = Random(11)
        var phase = 0.0
        var filtered = 0.0
        var gate = 1.0
        return DoubleArray(n) { i ->
            val t = i / RATE.toDouble()
            val freq = 170.0 + 1500.0 * exp(-t / 0.05)
            phase += 2 * PI * freq / RATE
            // New burst level every ~3 ms: that stutter is what makes it crackle.
            if (i % 128 == 0) gate = if (random.nextDouble() < 0.75) 0.6 + random.nextDouble() * 0.4 else 0.1
            val buzz = (if (sin(phase) >= 0) 0.45 else -0.45) + 0.3 * sin(phase * 2.01)
            filtered += 0.55 * ((random.nextDouble() * 2 - 1) - filtered)
            (buzz * 0.55 + filtered * 0.8) * gate * exp(-t / 0.1) * level
        }
    }

    /** Mixes parts that start at the given times (seconds) into one sound. */
    private fun layer(seconds: Double, vararg parts: Pair<Double, DoubleArray>): DoubleArray {
        val out = DoubleArray((RATE * seconds).toInt())
        for ((start, part) in parts) {
            val offset = (start * RATE).toInt()
            for (i in part.indices) {
                val j = offset + i
                if (j < out.size) out[j] += part[i]
            }
        }
        return out
    }

    /** Scales to a healthy, consistent loudness and converts to 16-bit. */
    private fun normalize(samples: DoubleArray, peak: Double = 0.85): ShortArray {
        val max = samples.maxOfOrNull { kotlin.math.abs(it) }?.takeIf { it > 0 } ?: 1.0
        val gain = peak / max
        return ShortArray(samples.size) { i ->
            (samples[i] * gain * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun concat(vararg parts: ShortArray): ShortArray {
        val out = ShortArray(parts.sumOf { it.size })
        var offset = 0
        parts.forEach { System.arraycopy(it, 0, out, offset, it.size); offset += it.size }
        return out
    }

    private fun wav(samples: ShortArray): ByteArray {
        val dataSize = samples.size * 2
        val out = ByteArrayOutputStream(44 + dataSize)
        fun int(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF); out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF) }
        fun short(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        out.write("RIFF".toByteArray()); int(36 + dataSize); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); int(16); short(1); short(1); int(RATE); int(RATE * 2); short(2); short(16)
        out.write("data".toByteArray()); int(dataSize)
        samples.forEach { short(it.toInt()) }
        return out.toByteArray()
    }

    companion object {
        // Game usage: not muted by the touch-vibration switch the way
        // sonification / touch feedback is.
        private val VIBRATION_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        private const val RATE = 44100
        /** The same in every set. */
        private val SHARED = setOf(UiSound.INTRO, UiSound.SYNC_DONE, UiSound.SYNC_ALERT)
        private val WOOD = listOf(1.0 to 1.0, 2.0 to 0.3, 3.9 to 0.1)
        private val GLASS = listOf(1.0 to 1.0, 2.76 to 0.35, 5.4 to 0.12, 8.93 to 0.05)
    }
}

/**
 * Sounds the person picked themselves, one per [UiSound], copied into the
 * app's own storage so they keep working if the original file moves.
 */
object CustomSounds {
    private const val MAX_BYTES = 3L * 1024 * 1024

    private fun dir(context: Context) = com.shelf.launcher.data.AppStorage.dir(context, "Sounds", File(context.filesDir, "ui_sounds")).apply { mkdirs() }

    fun file(context: Context, sound: UiSound): File? =
        dir(context).listFiles()?.firstOrNull { it.nameWithoutExtension == sound.name.lowercase() }

    fun has(context: Context, sound: UiSound): Boolean = file(context, sound) != null

    /** Copies the picked audio file in. Returns an error to show, or null. */
    fun import(context: Context, sound: UiSound, uri: android.net.Uri): String? {
        val resolver = context.contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        val ext = name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.length in 2..4 } ?: "audio"
        val target = File(dir(context), "${sound.name.lowercase()}.$ext")
        return try {
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_BYTES) return "That file is too big — pick a short sound (under 3 MB)."
                }
                out.toByteArray()
            } ?: return "Couldn't open that file."
            clear(context, sound)
            target.writeBytes(bytes)
            null
        } catch (e: Exception) {
            "Couldn't use that file: ${e.message}"
        }
    }

    fun clear(context: Context, sound: UiSound) {
        dir(context).listFiles()?.filter { it.nameWithoutExtension == sound.name.lowercase() }?.forEach { it.delete() }
    }
}
