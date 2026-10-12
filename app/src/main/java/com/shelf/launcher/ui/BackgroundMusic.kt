package com.shelf.launcher.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.BackgroundMusicMode
import com.shelf.launcher.data.SoundStyle
import com.shelf.launcher.ui.theme.AppTheme
import com.shelf.launcher.ui.theme.MusicLoop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** The loop that plays (or would play) with [theme]: the picked track, else the theme's own. */
fun AppearanceSettings.musicLoopFor(theme: AppTheme): MusicLoop =
    (if (musicMode == BackgroundMusicMode.THEME) MusicLoop.named(musicTrack) else null) ?: theme.musicLoop

/**
 * The navigation sounds in effect with [theme]: the console-menu, 32-bit and dark-fantasy loops bring
 * their own set ("Sounds match the music"); everything else uses the picked style.
 */
fun AppearanceSettings.soundStyleFor(theme: AppTheme): SoundStyle =
    if (themeSounds) musicLoopFor(theme).sounds ?: soundStyle else soundStyle

/**
 * Quiet background music for the launcher: a seamless loop per theme, composed
 * and mixed for Voltcap (res/raw/bgm_*.ogg — e-piano, pads, bells, no drums,
 * real stereo reverb), with the old in-code loop as a fallback. Or the
 * person's own audio file, or nothing.
 *
 * It fades in and out, pauses while a game (or any other app) is in front,
 * and while another music app is playing, and has its own volume.
 */
@Composable
fun BackgroundMusicHost(
    loop: MusicLoop,
    mode: BackgroundMusicMode,
    volumePercent: Int,
    customStamp: Long,
    /** Something else is making noise (another music app): stay quiet. */
    suppress: Boolean,
    /** Music of its own for the platform / game you are on (MusicOverrides); plays whatever [mode] says. */
    ownTrack: MusicOverrideSource? = null
) {
    com.shelf.launcher.Breadcrumbs.mark("BackgroundMusicHost")
    val context = LocalContext.current
    val player = remember { BackgroundMusicPlayer(context.applicationContext) }
    DisposableEffect(Unit) {
        BackgroundMusicGate.current = player
        onDispose { player.release() }
    }

    // Only plays while Voltcap is on screen (a game or another app in front = pause).
    val activity = context as? ComponentActivity
    var resumed by remember { mutableStateOf(true) }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumed = true
            if (event == Lifecycle.Event.ON_PAUSE) resumed = false
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(mode, loop, customStamp, ownTrack?.key, ownTrack?.stamp, ownTrack?.restartOnReturn) {
        val custom = BackgroundMusicFiles.file(context)
        when {
            ownTrack != null -> player.setSource(
                MusicSource.Custom(ownTrack.file), "ovr:${ownTrack.key}:${ownTrack.stamp}",
                restartOnReturn = ownTrack.restartOnReturn
            )
            mode == BackgroundMusicMode.THEME -> player.setSource(MusicSource.Theme(loop), "loop:${loop.name}")
            mode == BackgroundMusicMode.CUSTOM && custom != null ->
                player.setSource(MusicSource.Custom(custom), "custom:$customStamp")
            else -> player.setSource(null, "off")
        }
    }
    LaunchedEffect(volumePercent) { player.setVolume(volumePercent / 100f) }
    LaunchedEffect(resumed, suppress, mode, ownTrack != null) {
        player.setPaused(!resumed || suppress || (mode == BackgroundMusicMode.OFF && ownTrack == null))
    }
}

/** A platform's or game's own track for [BackgroundMusicHost]. [stamp] changes when the file is replaced. */
class MusicOverrideSource(val key: String, val file: File, val stamp: Long, val restartOnReturn: Boolean)

sealed class MusicSource {
    class Theme(val loop: MusicLoop) : MusicSource()
    class Custom(val file: File) : MusicSource()
}

/**
 * Lets the parts of Voltcap that start music (Spotify screen, the R1 music tile, media keys) silence
 * the background loop the instant they do, from any thread.
 */
object BackgroundMusicGate {
    @Volatile internal var current: BackgroundMusicPlayer? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Until when the loop stays quiet no matter what (a song change leaves a short silence). */
    @Volatile internal var quietUntil = 0L

    fun otherMusicStarting() {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) current?.haltForOtherMusic()
        else main.post { current?.haltForOtherMusic() }
    }

    /**
     * Next / previous song: the player goes silent for a moment while it loads the next one. The loop
     * must not jump into that gap — it took the audio focus, and the music app paused itself.
     */
    fun songChanging() {
        quietUntil = System.currentTimeMillis() + 12_000L
        otherMusicStarting()
    }
}

/** Plays one looping source at a time; all calls come from the main thread. */
class BackgroundMusicPlayer(private val context: Context) {
    private var track: AudioTrack? = null
    /** Shipped / custom track: two MediaPlayers handing over gap-free (see [GaplessLoop]). */
    private var loop: GaplessLoop? = null
    private var key: String? = null
    private var paused = true
    private var volume = 0.25f
    private var gain = 0f
    private var loadJob: Job? = null
    private var fadeJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    /** Where each override was when you left it (it carries on from there unless "restart on return" is on). */
    private val positions = HashMap<String, Int>()
    // The track fading out while the next one fades in (Features.musicCrossfade).
    private var outLoop: GaplessLoop? = null
    private var outTrack: AudioTrack? = null
    private var outJob: Job? = null

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        applyVolume()
    }

    // ---- Other music (Spotify & co.) ----
    // The loop holds a "may duck" audio focus while it plays, so the moment another app starts music
    // (it asks for full focus) Android tells us and the loop stops at once — no waiting for the
    // now-playing card. It then stays quiet until nothing else has been playing for a few seconds.
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    /** True while the loop is actually allowed to sound (wanted, focus held, nobody else playing). */
    private var playing = false
    private var blockedByOther = false
    private var watchJob: Job? = null
    private var startJob: Job? = null
    private val focusListener = android.media.AudioManager.OnAudioFocusChangeListener { change ->
        scope.launch {
            when (change) {
                android.media.AudioManager.AUDIOFOCUS_LOSS,
                android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> haltForOtherMusic()
                else -> Unit // a notification ducking us briefly is fine
            }
        }
    }
    private val focusRequest: Any? = if (android.os.Build.VERSION.SDK_INT >= 26) {
        android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
    } else null

    private fun requestFocus(): Boolean = runCatching {
        val r = if (android.os.Build.VERSION.SDK_INT >= 26) {
            audio.requestAudioFocus(focusRequest as android.media.AudioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(focusListener, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
        r == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }.getOrDefault(true)

    private fun abandonFocus() {
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                audio.abandonAudioFocusRequest(focusRequest as android.media.AudioFocusRequest)
            } else {
                @Suppress("DEPRECATION")
                audio.abandonAudioFocus(focusListener)
            }
        }
    }

    /**
     * Is another app's music / video playing? Not audio.isMusicActive: Voltcap's own navigation
     * sounds play on the same stream, so while you moved around the menus the loop thought "someone
     * else is playing" and never started (only the click sounds were heard). On Android 8+ only media
     * playback (Spotify, YouTube, a video) counts; Voltcap's sounds and the loop itself are USAGE_GAME.
     */
    private fun otherMusicActive(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            return runCatching {
                audio.activePlaybackConfigurations.any { c ->
                    val a = c.audioAttributes
                    a.usage == AudioAttributes.USAGE_MEDIA ||
                        (a.usage != AudioAttributes.USAGE_GAME && a.contentType == AudioAttributes.CONTENT_TYPE_MUSIC)
                }
            }.getOrDefault(false)
        }
        // Older Android: our own sounds can't be told apart; ignore a short click by asking twice (caller).
        return audio.isMusicActive && System.currentTimeMillis() - UiSoundClock.lastPlayed > 1_500L
    }

    /** Someone else's music is starting (or Voltcap is about to start it): silence the loop now. */
    fun haltForOtherMusic() {
        startJob?.cancel()
        if (paused) return
        blockedByOther = true
        if (playing) {
            playing = false
            fadeJob?.cancel()
            gain = 0f
            applyVolume()
            pauseOutput()
            abandonFocus()
        }
        watchOtherMusic()
    }

    /** Waits (cheaply, every 2.5 s) until no other music has played for two checks in a row, then fades back in. */
    private fun watchOtherMusic() {
        if (watchJob?.isActive == true) return
        watchJob = scope.launch {
            var quiet = 0
            while (!paused && blockedByOther) {
                delay(2_500)
                val busy = otherMusicActive() ||
                    System.currentTimeMillis() < BackgroundMusicGate.quietUntil ||
                    com.shelf.launcher.data.ShelfNotificationListener.anyBusy(context)
                // Three quiet checks (~7.5 s): the gap between two songs is shorter than that.
                quiet = if (busy) 0 else quiet + 1
                if (quiet >= 3) {
                    blockedByOther = false
                    if (!paused) start()
                }
            }
        }
    }

    /** Starts the loop unless other music is playing (checked twice, so a UI click doesn't count). */
    private fun tryStart() {
        startJob?.cancel()
        startJob = scope.launch {
            if (otherMusicActive()) {
                delay(700)
                if (otherMusicActive()) { blockedByOther = true; watchOtherMusic(); return@launch }
            }
            if (!paused) start()
        }
    }

    private fun start() {
        if (playing || paused) return
        if (!requestFocus()) { blockedByOther = true; watchOtherMusic(); return }
        playing = true
        resumeOutput()
        fadeTo(1f, 2000)
    }

    fun setPaused(p: Boolean) {
        if (paused == p) return
        paused = p
        if (p) {
            startJob?.cancel()
            watchJob?.cancel()
            blockedByOther = false
            val was = playing
            playing = false
            if (was) fadeTo(0f, 300) { pauseOutput(); abandonFocus() } else pauseOutput()
        } else {
            tryStart()
        }
    }

    fun setSource(source: MusicSource?, newKey: String, restartOnReturn: Boolean = true) {
        if (newKey == key) return
        // Leaving an override: remember where it was, so coming back can carry on from there.
        key?.takeIf { it.startsWith("ovr:") }?.let { k -> loop?.position()?.let { positions[k] = it } }
        val startAt = if (newKey.startsWith("ovr:") && !restartOnReturn) positions[newKey] ?: 0 else 0
        key = newKey
        loadJob?.cancel()
        // Fade the old track out while the new one fades in; otherwise cut it off.
        val fade = com.shelf.launcher.data.Features.musicCrossfade.value && playing && source != null && (loop != null || track != null)
        if (fade) {
            crossfadeOut()
            gain = 0f
            applyVolume()
        } else {
            releaseOutput()
            // (update 91) The next track fades in instead of starting at full volume the moment it's ready.
            if (playing && source != null) {
                fadeJob?.cancel()
                gain = 0f
            }
        }
        when (source) {
            null -> Unit
            // The built-in tracks ship as OGG loops (res/raw): mixed offline with real reverb, stereo,
            // 44.1 kHz. MediaPlayer's own isLooping cut a little off the end before restarting on
            // these devices, so two players hand over with setNextMediaPlayer instead (gap-free).
            // If one can't be opened, the old synthesised loop plays.
            is MusicSource.Theme -> {
                val id = ThemeMusic.rawFor(source.loop)
                val l = GaplessLoop(attributes, open = { p ->
                    context.resources.openRawResourceFd(id).use { afd -> p.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) }
                }, onReady = { applyVolume(); if (playing) loop?.start() })
                if (l.load()) {
                    loop = l
                } else {
                    l.release()
                    loadJob = scope.launch {
                        val pcm = withContext(Dispatchers.Default) { ThemeMusic.pcm(context, source.loop) }
                        if (pcm != null) {
                            track = buildTrack(pcm)
                            applyVolume()
                            if (playing) track?.play()
                        }
                    }
                }
            }
            is MusicSource.Custom -> {
                val l = GaplessLoop(attributes, open = { p -> p.setDataSource(source.file.path) },
                    onReady = { applyVolume(); if (playing) loop?.start() }, startAtMs = startAt)
                if (l.load()) loop = l else l.release()
            }
        }
        if (playing && source != null) fadeTo(1f, 1_400)
    }

    /** Moves the playing track aside and lets it fade to nothing over the next second or so. */
    private fun crossfadeOut() {
        outJob?.cancel()
        releaseOutgoing()
        outLoop = loop
        outTrack = track
        loop = null
        track = null
        val startVolume = (volume * gain).coerceIn(0f, 1f)
        outJob = scope.launch {
            val steps = 24
            for (i in 1..steps) {
                delay(1_400L / steps)
                val v = startVolume * (1f - i / steps.toFloat())
                runCatching { outTrack?.setVolume(v) }
                outLoop?.setVolume(v)
            }
            releaseOutgoing()
        }
    }

    private fun releaseOutgoing() {
        outTrack?.let { runCatching { it.stop() }; runCatching { it.release() } }
        outTrack = null
        outLoop?.release()
        outLoop = null
    }

    fun release() {
        if (BackgroundMusicGate.current === this) BackgroundMusicGate.current = null
        abandonFocus()
        scope.cancel()
        releaseOutput()
    }

    private fun buildTrack(pcm: ShortArray): AudioTrack? = runCatching {
        val t = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(ThemeMusic.RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(pcm, 0, pcm.size)
        // Endless, gap-free loop over the whole buffer.
        t.setLoopPoints(0, pcm.size, -1)
        t
    }.getOrNull()

    private fun applyVolume() {
        val v = (volume * gain).coerceIn(0f, 1f)
        runCatching { track?.setVolume(v) }
        loop?.setVolume(v)
    }

    private fun resumeOutput() {
        runCatching { track?.play() }
        loop?.start()
    }

    private fun pauseOutput() {
        outJob?.cancel()
        releaseOutgoing()
        runCatching { track?.pause() }
        loop?.pause()
    }

    private fun releaseOutput() {
        outJob?.cancel()
        releaseOutgoing()
        track?.let { runCatching { it.stop() }; runCatching { it.release() } }
        track = null
        loop?.release()
        loop = null
    }

    private fun fadeTo(target: Float, ms: Long, done: () -> Unit = {}) {
        fadeJob?.cancel()
        val start = gain
        fadeJob = scope.launch {
            val steps = 20
            for (i in 1..steps) {
                delay(ms / steps)
                gain = start + (target - start) * i / steps
                applyVolume()
            }
            done()
        }
    }
}

/**
 * A gap-free loop of one audio file: two MediaPlayers, the second queued with
 * [MediaPlayer.setNextMediaPlayer] so Android starts it the instant the first ends; on the
 * hand-over the finished one is released and a fresh copy is queued behind the new one.
 * (MediaPlayer.isLooping restarts the decoder and audibly cuts the end of the track.)
 * All calls on the main thread.
 */
internal class GaplessLoop(
    private val attributes: AudioAttributes,
    /** Points a fresh player at the file (setDataSource). */
    private val open: (MediaPlayer) -> Unit,
    /** The first copy is ready to play. */
    private val onReady: () -> Unit,
    /** Where the first copy starts (a platform's music carrying on from where you left it). */
    private val startAtMs: Int = 0
) {
    private var current: MediaPlayer? = null
    private var queued: MediaPlayer? = null
    private var queuedReady = false
    private var ready = false
    private var wantPlaying = false
    private var volume = 0f
    private var released = false

    /** Starts preparing; false when the file can't be opened at all. */
    fun load(): Boolean {
        current = create { p ->
            ready = true
            if (startAtMs > 0) runCatching { p.seekTo(startAtMs) }
            p.setVolume(volume, volume)
            queueNext()
            onReady()
        } ?: return false
        return true
    }

    private fun create(onPrepared: (MediaPlayer) -> Unit): MediaPlayer? = runCatching {
        val p = MediaPlayer()
        try {
            p.setAudioAttributes(attributes)
            open(p)
        } catch (e: Exception) {
            runCatching { p.release() }
            throw e
        }
        p.setOnPreparedListener { if (!released) onPrepared(it) }
        p.setOnErrorListener { mp, _, _ ->
            if (mp === current) ready = false
            if (mp === queued) queuedReady = false
            true
        }
        p.prepareAsync()
        p
    }.getOrNull()

    private fun queueNext() {
        val cur = current ?: return
        queued?.let { runCatching { it.release() } }
        queuedReady = false
        queued = create { next ->
            next.setVolume(volume, volume)
            queuedReady = runCatching { cur.setNextMediaPlayer(next) }.isSuccess
        }
        cur.setOnCompletionListener { done ->
            val next = queued
            if (next != null && queuedReady) {
                // Android has already started `next`: hand over and queue another copy behind it.
                current = next
                queued = null
                runCatching { done.release() }
                if (!wantPlaying) runCatching { next.pause() }
                queueNext()
            } else {
                // The next copy wasn't ready in time: plain restart (rare, very short files only).
                runCatching { done.seekTo(0); if (wantPlaying) done.start() }
            }
        }
    }

    fun start() {
        wantPlaying = true
        if (ready) runCatching { current?.let { if (!it.isPlaying) it.start() } }
    }

    /** Where the playing copy is, in ms (null when it isn't ready). */
    fun position(): Int? = if (ready) runCatching { current?.currentPosition }.getOrNull() else null

    fun pause() {
        wantPlaying = false
        runCatching { current?.takeIf { it.isPlaying }?.pause() }
    }

    fun setVolume(v: Float) {
        volume = v
        runCatching { current?.setVolume(v, v) }
        runCatching { queued?.setVolume(v, v) }
    }

    fun release() {
        released = true
        ready = false
        queued?.let { runCatching { it.release() } }
        current?.let { runCatching { it.release() } }
        queued = null
        current = null
    }
}

/** The person's own background track (Settings › Audio › Background music). */
object BackgroundMusicFiles {
    private const val MAX_BYTES = 30L * 1024 * 1024

    class ImportResult(val name: String, val error: String?)

    private fun dir(context: Context) = com.shelf.launcher.data.AppStorage.dir(context, "Music", File(context.filesDir, "bg_music")).apply { mkdirs() }

    fun file(context: Context): File? =
        dir(context).listFiles()?.firstOrNull { it.nameWithoutExtension == "custom" }

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }

    /** Copies the picked audio file into the app's own storage. */
    fun import(context: Context, uri: android.net.Uri): ImportResult {
        val resolver = context.contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "Your music"
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..4 } ?: "audio"
        return try {
            val target = File(dir(context), "custom.$ext")
            val temp = File(dir(context), "incoming.tmp")
            var total = 0L
            resolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) {
                            temp.delete()
                            return ImportResult(name, "That file is too big — pick one under 30 MB.")
                        }
                        out.write(buffer, 0, read)
                    }
                }
            } ?: return ImportResult(name, "Couldn't open that file.")
            dir(context).listFiles()?.filter { it.name != temp.name }?.forEach { it.delete() }
            if (!temp.renameTo(target)) { temp.copyTo(target, overwrite = true); temp.delete() }
            ImportResult(name, null)
        } catch (e: Exception) {
            ImportResult(name, "Couldn't use that file: ${e.message}")
        }
    }
}

/**
 * The built-in tracks. Normally the shipped OGG loops play ([rawFor]); this
 * in-code version (one slow, seamless loop per mood, about 30–60 s, 22 kHz
 * mono, synthesised on first use and kept in the cache) is only the fallback
 * when a file can't be opened.
 */
object ThemeMusic {
    const val RATE = 22050
    // 2: no percussion (the synthwave bass pulse is gone too). 3: per loop, four new moods.
    private const val VERSION = 3

    /** The shipped loop (res/raw/bgm_*.ogg). */
    fun rawFor(loop: MusicLoop): Int = when (loop) {
        MusicLoop.VOLT -> com.shelf.launcher.R.raw.bgm_volt
        MusicLoop.TIDE -> com.shelf.launcher.R.raw.bgm_tide
        MusicLoop.EMBER -> com.shelf.launcher.R.raw.bgm_ember
        MusicLoop.COBALT -> com.shelf.launcher.R.raw.bgm_cobalt
        MusicLoop.SYNTHWAVE -> com.shelf.launcher.R.raw.bgm_synthwave
        MusicLoop.MATCHA -> com.shelf.launcher.R.raw.bgm_matcha
        MusicLoop.MONO -> com.shelf.launcher.R.raw.bgm_mono
        MusicLoop.HOME -> com.shelf.launcher.R.raw.bgm_home
        MusicLoop.HAZE -> com.shelf.launcher.R.raw.bgm_haze
        MusicLoop.MEMORY -> com.shelf.launcher.R.raw.bgm_memory
        MusicLoop.HOLLOW -> com.shelf.launcher.R.raw.bgm_hollow
    }

    private class Partials(val decay: Double, val parts: List<Pair<Double, Double>>)

    private val SOFT = Partials(0.45, listOf(1.0 to 1.0, 2.0 to 0.16))
    private val BELL = Partials(1.3, listOf(1.0 to 1.0, 2.76 to 0.32, 5.4 to 0.1))
    private val MARIMBA = Partials(0.32, listOf(1.0 to 1.0, 3.9 to 0.12, 9.2 to 0.03))
    private val BRIGHT = Partials(0.22, listOf(1.0 to 1.0, 2.0 to 0.5, 3.0 to 0.3))
    private val SINE = Partials(2.2, listOf(1.0 to 1.0))

    private class Style(
        val bpm: Double,
        val chords: List<List<Int>>,
        val barsPerChord: Int,
        val padAttack: Double,
        val padHarmonics: List<Double>,
        val pluck: Partials,
        val pluckPerBeat: Int,
        val pluckChance: Double,
        val pluckLevel: Double,
        val bassLevel: Double,
        val bassPulse: Boolean,
        val airLevel: Double,
        val reverb: Double
    )

    private fun styleFor(loop: MusicLoop): Style = when (loop) {
        // Voltcap's own: warm and a little electric, A minor.
        MusicLoop.VOLT -> Style(
            bpm = 66.0,
            chords = listOf(
                listOf(45, 57, 60, 64, 71),
                listOf(41, 53, 57, 60, 67),
                listOf(48, 55, 59, 62, 64),
                listOf(43, 55, 59, 62, 64)
            ),
            barsPerChord = 2, padAttack = 1.6, padHarmonics = listOf(1.0, 0.35, 0.15),
            pluck = SOFT, pluckPerBeat = 1, pluckChance = 0.55, pluckLevel = 0.35,
            bassLevel = 0.5, bassPulse = false, airLevel = 0.05, reverb = 0.4
        )
        // Water: slow, glassy bells and a rush of air, D major.
        MusicLoop.TIDE -> Style(
            bpm = 56.0,
            chords = listOf(
                listOf(38, 50, 54, 57, 61),
                listOf(47, 54, 57, 61, 64),
                listOf(43, 50, 54, 59, 62),
                listOf(45, 52, 57, 61, 66)
            ),
            barsPerChord = 2, padAttack = 2.6, padHarmonics = listOf(1.0, 0.25),
            pluck = BELL, pluckPerBeat = 1, pluckChance = 0.5, pluckLevel = 0.3,
            bassLevel = 0.4, bassPulse = false, airLevel = 0.12, reverb = 0.55
        )
        // Embers: soft, low and warm, D minor.
        MusicLoop.EMBER -> Style(
            bpm = 60.0,
            chords = listOf(
                listOf(38, 50, 57, 60, 64),
                listOf(46, 53, 57, 62, 65),
                listOf(41, 53, 57, 60, 64),
                listOf(48, 55, 60, 62, 67)
            ),
            barsPerChord = 2, padAttack = 2.0, padHarmonics = listOf(1.0, 0.4, 0.2),
            pluck = SOFT, pluckPerBeat = 1, pluckChance = 0.5, pluckLevel = 0.3,
            bassLevel = 0.5, bassPulse = false, airLevel = 0.06, reverb = 0.45
        )
        // Deep blue: airy, open fifths and sparse bells, E minor.
        MusicLoop.COBALT -> Style(
            bpm = 70.0,
            chords = listOf(
                listOf(40, 52, 55, 59, 66),
                listOf(48, 52, 55, 59, 62),
                listOf(43, 50, 55, 62, 69),
                listOf(50, 57, 62, 64, 69)
            ),
            barsPerChord = 3, padAttack = 2.2, padHarmonics = listOf(1.0, 0.3, 0.1),
            pluck = BELL, pluckPerBeat = 2, pluckChance = 0.3, pluckLevel = 0.28,
            bassLevel = 0.4, bassPulse = false, airLevel = 0.08, reverb = 0.5
        )
        // Restrained retro: soft saw-ish pads, a gentle pulse and small arps, A minor.
        MusicLoop.SYNTHWAVE -> Style(
            bpm = 84.0,
            chords = listOf(
                listOf(45, 52, 57, 60, 64),
                listOf(41, 48, 53, 57, 60),
                listOf(48, 55, 60, 64, 67),
                listOf(43, 50, 55, 59, 62)
            ),
            barsPerChord = 4, padAttack = 1.2, padHarmonics = listOf(1.0, 0.5, 0.33, 0.25),
            pluck = BRIGHT, pluckPerBeat = 2, pluckChance = 0.5, pluckLevel = 0.22,
            bassLevel = 0.55, bassPulse = false, airLevel = 0.03, reverb = 0.4
        )
        // Tea garden: woody plucks over a calm C major pentatonic bed.
        MusicLoop.MATCHA -> Style(
            bpm = 68.0,
            chords = listOf(
                listOf(48, 55, 62, 64, 69),
                listOf(45, 52, 55, 59, 60),
                listOf(41, 52, 57, 60, 67),
                listOf(43, 50, 57, 59, 64)
            ),
            barsPerChord = 3, padAttack = 2.0, padHarmonics = listOf(1.0, 0.2),
            pluck = MARIMBA, pluckPerBeat = 1, pluckChance = 0.55, pluckLevel = 0.35,
            bassLevel = 0.4, bassPulse = false, airLevel = 0.04, reverb = 0.4
        )
        // OLED black: almost nothing — a low drone and the odd faint tone.
        MusicLoop.MONO -> Style(
            bpm = 48.0,
            chords = listOf(
                listOf(45, 52, 57),
                listOf(43, 50, 55),
                listOf(41, 48, 53),
                listOf(43, 50, 55)
            ),
            barsPerChord = 2, padAttack = 3.0, padHarmonics = listOf(1.0, 0.15),
            pluck = SINE, pluckPerBeat = 1, pluckChance = 0.12, pluckLevel = 0.3,
            bassLevel = 0.4, bassPulse = false, airLevel = 0.02, reverb = 0.6
        )
        // Calm console home screen: wide E-major pads and soft bells.
        MusicLoop.HOME -> Style(
            bpm = 60.0,
            chords = listOf(
                listOf(40, 59, 63, 66, 71),
                listOf(37, 56, 64, 71, 75),
                listOf(33, 52, 61, 68, 71),
                listOf(35, 54, 61, 64, 69)
            ),
            barsPerChord = 2, padAttack = 2.6, padHarmonics = listOf(1.0, 0.22),
            pluck = BELL, pluckPerBeat = 1, pluckChance = 0.35, pluckLevel = 0.26,
            bassLevel = 0.35, bassPulse = false, airLevel = 0.03, reverb = 0.6
        )
        // 128-bit dream: misty D-lydian pads and glassy bells.
        MusicLoop.HAZE -> Style(
            bpm = 64.0,
            chords = listOf(
                listOf(38, 57, 66, 73, 76),
                listOf(42, 61, 69, 76, 80),
                listOf(35, 54, 62, 69, 76),
                listOf(37, 56, 66, 71, 75)
            ),
            barsPerChord = 2, padAttack = 2.8, padHarmonics = listOf(1.0, 0.3, 0.1),
            pluck = BELL, pluckPerBeat = 2, pluckChance = 0.3, pluckLevel = 0.24,
            bassLevel = 0.4, bassPulse = false, airLevel = 0.06, reverb = 0.6
        )
        // 32-bit menu: D-flat major sevenths, marimba-ish plucks.
        MusicLoop.MEMORY -> Style(
            bpm = 80.0,
            chords = listOf(
                listOf(37, 53, 56, 60, 63),
                listOf(41, 56, 60, 63, 67),
                listOf(42, 58, 61, 65, 68),
                listOf(44, 54, 58, 61, 65)
            ),
            barsPerChord = 1, padAttack = 0.8, padHarmonics = listOf(1.0, 0.4, 0.2),
            pluck = MARIMBA, pluckPerBeat = 2, pluckChance = 0.45, pluckLevel = 0.3,
            bassLevel = 0.5, bassPulse = false, airLevel = 0.0, reverb = 0.35
        )
        // Dark fantasy: D minor with the dark E-flat, low and slow.
        MusicLoop.HOLLOW -> Style(
            bpm = 52.0,
            chords = listOf(
                listOf(38, 45, 50, 53, 64),
                listOf(34, 41, 50, 57, 64),
                listOf(39, 46, 55, 62, 67),
                listOf(33, 52, 57, 61, 64)
            ),
            barsPerChord = 2, padAttack = 3.0, padHarmonics = listOf(1.0, 0.3, 0.12),
            pluck = SOFT, pluckPerBeat = 2, pluckChance = 0.4, pluckLevel = 0.3,
            bassLevel = 0.45, bassPulse = false, airLevel = 0.05, reverb = 0.6
        )
    }

    /** The finished loop for [loop], from the cache or freshly made. Blocking. */
    fun pcm(context: Context, loop: MusicLoop): ShortArray? = runCatching {
        val file = File(context.cacheDir, "bgm_${loop.name.lowercase()}_v$VERSION.pcm")
        if (file.exists() && file.length() > 1000) {
            val bytes = file.readBytes()
            val out = ShortArray(bytes.size / 2)
            java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out)
            out
        } else {
            val samples = synthesize(styleFor(loop), loop.ordinal + 10)
            val buffer = java.nio.ByteBuffer.allocate(samples.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            buffer.asShortBuffer().put(samples)
            // Temp file first: a half-written cache (killed mid-write) would play a cut-off loop from then on.
            runCatching {
                val tmp = File(file.path + ".tmp")
                tmp.writeBytes(buffer.array())
                if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
            }
            samples
        }
    }.getOrNull()

    private fun hz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    private fun synthesize(s: Style, seed: Int): ShortArray {
        val beat = 60.0 / s.bpm
        val chordSeconds = beat * 4 * s.barsPerChord
        val total = (chordSeconds * s.chords.size * RATE).toInt()
        val out = DoubleArray(total)
        val rng = java.util.Random(seed.toLong())

        s.chords.forEachIndexed { ci, chord ->
            val start = (ci * chordSeconds * RATE).toInt()
            // Pad: every note as a slow swell.
            chord.forEach { note ->
                addTone(out, start, hz(note), chordSeconds, s.padAttack, 2.6, s.padHarmonics, 1.0 / chord.size, 0.7)
            }
            // Bass under the chord.
            val root = chord.minOf { it }.let { if (it >= 52) it - 12 else it }
            addTone(out, start, hz(root), chordSeconds, 0.6, 1.0, listOf(1.0, 0.25), s.bassLevel * 0.5, 0.0)
            if (s.bassPulse) {
                val eighths = (chordSeconds / (beat / 2)).toInt()
                for (k in 0 until eighths) {
                    addPluck(
                        out, start + (k * beat / 2 * RATE).toInt(), hz(root),
                        SOFT.parts, 0.16, s.bassLevel * (if (k % 2 == 0) 0.5 else 0.3)
                    )
                }
            }
            // A few soft notes on top, from the chord's own notes.
            if (s.pluckPerBeat > 0) {
                val stepSeconds = beat / s.pluckPerBeat
                val steps = (chordSeconds / stepSeconds).toInt()
                for (k in 0 until steps) {
                    if (rng.nextDouble() < s.pluckChance) {
                        val note = chord[rng.nextInt(chord.size)]
                        val lifted = note + if (note < 55) 24 else 12
                        addPluck(
                            out, start + (k * stepSeconds * RATE).toInt(), hz(lifted),
                            s.pluck.parts, s.pluck.decay, s.pluckLevel * (0.6 + 0.4 * rng.nextDouble())
                        )
                    }
                }
            }
        }

        // Air: slow-breathing, low-passed noise.
        if (s.airLevel > 0.0) {
            var low = 0.0
            val cycles = s.chords.size
            for (i in out.indices) {
                val white = rng.nextDouble() * 2.0 - 1.0
                low += 0.08 * (white - low)
                val breath = 0.6 + 0.4 * sin(2.0 * PI * cycles * i / out.size)
                out[i] += low * breath * s.airLevel * 4.0
            }
        }

        val wet = reverbCyclic(out, s.reverb)
        var peak = 1e-9
        for (v in wet) peak = maxOf(peak, abs(v))
        val gain = 0.6 / peak
        return ShortArray(wet.size) { i ->
            (wet[i] * gain * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /** Adds a note that swells in, holds, and fades out; runs past the end and wraps to the start, so the loop is seamless. */
    private fun addTone(
        out: DoubleArray, startIdx: Int, freq: Double, hold: Double, attack: Double, release: Double,
        harmonics: List<Double>, level: Double, detune: Double
    ) {
        val n = ((hold + release) * RATE).toInt()
        val a = attack.coerceAtMost(hold / 2)
        val w1 = 2.0 * PI * freq / RATE
        val w2 = 2.0 * PI * (freq + detune) / RATE
        for (i in 0 until n) {
            val t = i / RATE.toDouble()
            val env = when {
                t < a -> { val x = t / a; x * x * (3.0 - 2.0 * x) }
                t < hold -> 1.0
                else -> 0.5 * (1.0 + cos(PI * ((t - hold) / release).coerceIn(0.0, 1.0)))
            }
            var v = 0.0
            for (h in harmonics.indices) {
                val k = (h + 1).toDouble()
                if (freq * k > 9000.0) break
                v += harmonics[h] * (sin(w1 * i * k) + if (detune != 0.0) sin(w2 * i * k) else 0.0)
            }
            out[(startIdx + i) % out.size] += v * env * level
        }
    }

    private fun addPluck(
        out: DoubleArray, startIdx: Int, freq: Double, parts: List<Pair<Double, Double>>,
        decay: Double, level: Double
    ) {
        val n = (minOf(decay * 6.0, 4.0) * RATE).toInt()
        for (i in 0 until n) {
            val t = i / RATE.toDouble()
            val env = exp(-t / decay) * minOf(1.0, t / 0.004)
            var v = 0.0
            for ((ratio, amp) in parts) {
                if (freq * ratio > 9000.0) continue
                v += amp * sin(2.0 * PI * freq * ratio * t)
            }
            out[(startIdx + i) % out.size] += v * env * level
        }
    }

    /** A few comb filters, computed round the loop so the tail runs into the start. */
    private fun reverbCyclic(x: DoubleArray, mix: Double): DoubleArray {
        val n = x.size
        val delays = doubleArrayOf(0.113, 0.187, 0.271, 0.379).map { (it * RATE).toInt() }
        val feedback = doubleArrayOf(0.62, 0.58, 0.54, 0.5)
        val wet = DoubleArray(n)
        delays.forEachIndexed { di, d ->
            val y = x.copyOf()
            repeat(3) {
                for (i in 0 until n) y[i] = x[i] + feedback[di] * y[(i - d + n) % n]
            }
            for (i in 0 until n) wet[i] += y[i] * 0.25
        }
        var low = 0.0
        return DoubleArray(n) { i ->
            low += 0.35 * (wet[i] - low)
            x[i] + mix * low
        }
    }
}

/** When Voltcap last played one of its own interface sounds (set by UiSounds), so they never count as "other music". */
object UiSoundClock {
    @Volatile var lastPlayed = 0L
}
