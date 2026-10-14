package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.exp
import kotlin.math.min

/*
 * Update 202: Pocket Quest's options (Settings › Home › Layout options, while it is the layout in use) and its own small
 * synthesizer: square / pulse-wave blips made in code (no sound files), played through an AudioTrack on a thread of their
 * own, at their own volume. Off by default — then the layout uses Voltcap's sounds like every other layout.
 */

/** Settings › Home › Layout options (Pocket Quest). Every switch is a Features pref ("pq_…"), backed up with the rest. */
@Composable
internal fun PocketQuestSettings() {
    MutedNote(
        "A full-screen game menu in the style of the great 32-bit handheld RPGs: the game you're on big in the middle with its " +
            "details and Play, your games along the bottom, consoles as tabs on top, pixel-art scenery behind. " +
            "◀ ▶ move · ▲ the panel and the top bar · A " + (if (Features.pqAPlays.state.value) "plays" else "opens the game window") +
            " · Y details · X favourite · " + (if (Features.pqBumpers.state.value) "L1 / R1 (or L2 / R2) console · Start quick settings" else "L2 / R2 console · R1 (or Start) quick settings · L1 shortcuts") +
            " · Select systems (hold: sort & filter). The top bar's slider button opens the quick settings too."
    )
    Spacer(Modifier.height(12.dp))
    // The four themes as small pictures (the chips under them pick).
    val now = Features.pqTheme.state.value
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Features.PqTheme.values().forEach { t ->
            Column(Modifier.weight(1f)) {
                val on = t == now
                Canvas(
                    Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(PqChamfer(5.dp))
                        .border(if (on) 2.dp else 1.dp, if (on) ShelfTheme.accent else Color.White.copy(alpha = 0.25f), PqChamfer(5.dp))
                ) { pqSceneSketch(t) }
                Text(
                    t.label, color = if (on) ShelfTheme.accent else ShelfTheme.textMuted, fontSize = 11.sp, maxLines = 1,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    StyleChoice("Theme", Features.PqTheme.values(), now, { it.label }) { Features.pqTheme.value = it }
    StyleChoice("Background", Features.PqBackground.values(), Features.pqBackground.state.value, { it.label }) { Features.pqBackground.value = it }
    if (Features.pqBackground.state.value == Features.PqBackground.SCENERY) {
        PqSwitch(Icons.Filled.Waves, "Animated scenery", "Clouds drift, motes, bubbles and lights move — slowly, behind everything. Off: a still picture.", Features.pqBgAnim)
        PercentSlider(
            "Parallax", Features.pqParallax.state.value, { Features.pqParallax.value = it.coerceIn(0, 100) },
            valueText = { if (it == 0) "Off" else "$it%" }
        )
        Spacer(Modifier.height(10.dp))
    }
    StyleChoice("Accent colour", Features.PqAccent.values(), Features.pqAccent.state.value, { it.label }) { Features.pqAccent.value = it }
    StyleChoice("Games shown as", Features.PqMedia.values(), Features.pqMedia.state.value, { it.label }) { Features.pqMedia.value = it }
    MutedNote("Your game look is the one from Settings › Game look (cartridges, discs, cases or covers).")
    Spacer(Modifier.height(10.dp))
    PqSwitch(Icons.Filled.Info, "Description", "A few lines about the game in the panel.", Features.pqDescription)
    PqSwitch(Icons.Filled.Image, "Screenshots", "A screenshot and three small ones in the panel (looked up once you rest on a game).", Features.pqScreenshots)
    StyleChoice("Collection size", Features.PqSize.values(), Features.pqSize.state.value, { it.label }) { Features.pqSize.value = it }
    StyleChoice("Animation speed", Features.PqSpeed.values(), Features.pqSpeed.state.value, { it.label }) { Features.pqSpeed.value = it }
    PqSwitch(Icons.Filled.Style, "Gentle 3D tilt", "The big picture of the game you're on leans slowly.", Features.pqTilt)
    PqSwitch(Icons.Filled.Visibility, "Reduced motion", "No tilt, parallax, drifting or launch flash; changes are quick fades.", Features.pqReducedMotion)
    PqSwitch(Icons.Filled.MusicNote, "Retro sounds", "Its own square-wave blips for moving around (off: Voltcap's sounds).", Features.pqSounds)
    if (Features.pqSounds.state.value) {
        PercentSlider(
            "Retro sound volume", Features.pqSoundVolume.state.value, { Features.pqSoundVolume.value = it.coerceIn(0, 100) },
            valueText = { if (it == 0) "Muted" else "$it%" }
        )
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            Chip("Test: move") { PqSynth.play(UiSound.MOVE, force = true) }
            Chip("Test: select") { PqSynth.play(UiSound.SELECT, force = true) }
            Chip("Test: start") { PqSynth.play(UiSound.LAUNCH, force = true) }
        }
        Spacer(Modifier.height(10.dp))
    }
    PqSwitch(Icons.Filled.Tab, "L1 / R1 switch the console", "Off (default): R1 opens the quick settings and L1 the shortcuts, as on the other layouts. L2 / R2 always switch.", Features.pqBumpers)
    PqSwitch(Icons.Filled.PlayArrow, "A starts the game", "Off: A opens the game window, as on the other layouts (Play is still on the panel).", Features.pqAPlays)
}

@Composable
private fun PqSwitch(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, pref: Features.BoolPref) {
    SwitchRow(icon, title, subtitle, pref.state.value, { pref.value = it })
    Spacer(Modifier.height(8.dp))
}

/**
 * Update 202: Pocket Quest's own blips — pulse waves (12.5 / 25 / 50 % duty) with a quick attack and a short decay, a
 * touch of low-pass so they're soft. Made once per sound into a static AudioTrack; played on a thread of their own so
 * the D-pad never waits. Released when the layout goes away.
 */
internal object PqSynth {
    private const val RATE = 22050
    private val tracks = java.util.EnumMap<UiSound, android.media.AudioTrack>(UiSound::class.java)
    private var handler: android.os.Handler? = null
    @Volatile private var lastMoveAt = 0L

    @Synchronized
    private fun handler(): android.os.Handler {
        handler?.let { return it }
        val t = android.os.HandlerThread("pq-synth").apply { start() }
        return android.os.Handler(t.looper).also { handler = it }
    }

    /** Plays [s] when Pocket Quest's own sounds are on ([force]: always, for the settings' test). True = it played. */
    fun play(s: UiSound, force: Boolean = false): Boolean {
        if (!force && !Features.pqSounds.value) return false
        val now = android.os.SystemClock.uptimeMillis()
        if (s == UiSound.MOVE) {
            // A held D-pad repeats quickly: one blip per ~45 ms at most.
            if (now - lastMoveAt < 45L) return true
            lastMoveAt = now
        }
        val vol = (Features.pqSoundVolume.value / 100f).coerceIn(0f, 1f)
        if (vol <= 0f) return true
        handler().post {
            runCatching {
                val t = track(s) ?: return@runCatching
                if (t.playState == android.media.AudioTrack.PLAYSTATE_PLAYING) t.stop()
                t.reloadStaticData()
                t.setVolume(vol)
                t.play()
            }
        }
        return true
    }

    /** Frees the tracks and the thread (the layout went away). */
    fun release() {
        val h = synchronized(this) { val held = handler; handler = null; held } ?: return
        h.post {
            tracks.values.forEach { runCatching { it.release() } }
            tracks.clear()
            h.looper.quitSafely()
        }
    }

    private fun track(s: UiSound): android.media.AudioTrack? {
        tracks[s]?.let { return it }
        val pcm = wave(s)
        val t = runCatching {
            android.media.AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_GAME)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
                .also { it.write(pcm, 0, pcm.size) }
        }.getOrNull() ?: return null
        tracks[s] = t
        return t
    }

    /** The notes of each blip: frequency (Hz), length (s), pulse width (duty). */
    private fun notes(s: UiSound): List<Triple<Double, Double, Double>> = when (s) {
        UiSound.MOVE -> listOf(Triple(1760.0, 0.028, 0.125))
        UiSound.SELECT -> listOf(Triple(988.0, 0.045, 0.25), Triple(1319.0, 0.07, 0.25))
        UiSound.BACK -> listOf(Triple(1319.0, 0.045, 0.25), Triple(880.0, 0.07, 0.25))
        UiSound.OPEN -> listOf(Triple(784.0, 0.04, 0.5), Triple(988.0, 0.04, 0.5), Triple(1175.0, 0.07, 0.5))
        UiSound.LAUNCH -> listOf(Triple(523.0, 0.05, 0.25), Triple(659.0, 0.05, 0.25), Triple(784.0, 0.05, 0.25), Triple(1047.0, 0.16, 0.5))
        else -> listOf(Triple(988.0, 0.05, 0.25))
    }

    private fun wave(s: UiSound): ShortArray {
        val list = notes(s)
        val total = list.sumOf { (it.second * RATE).toInt() }
        val out = ShortArray(total.coerceAtLeast(1))
        var i = 0
        var lp = 0.0
        for ((f, sec, duty) in list) {
            val n = (sec * RATE).toInt().coerceAtLeast(1)
            for (k in 0 until n) {
                val phase = (k * f / RATE) % 1.0
                val raw = if (phase < duty) 1.0 else -1.0
                val env = min(1.0, k / (0.002 * RATE)) * exp(-3.2 * k / n)
                lp += (raw * env - lp) * 0.55
                if (i < out.size) out[i++] = (lp * 0.3 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
            }
        }
        return out
    }
}
