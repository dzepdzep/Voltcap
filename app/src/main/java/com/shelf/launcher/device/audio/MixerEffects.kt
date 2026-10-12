package com.shelf.launcher.device.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.shelf.launcher.device.RootShell
import kotlin.math.log10

/** One player Android reported: whose it is, what it plays, whether it's playing, its audio session. */
internal data class MixerPlayer(
    val uid: Int, val usage: Int, val started: Boolean, val session: Int,
    /** The helper can set this player's own volume. False for native audio (AAudio / OpenSL: Spotify, many emulators), which has no player handle. */
    val controllable: Boolean = true
)

/**
 * The mixer's second way to set an app's volume, for when the privileged helper can't run (or its
 * player volume doesn't take on some firmware): a "Dynamics processing" sound effect, Android's own,
 * put on that app's audio sessions by Voltcap itself, with its input gain turned down. Equalizer apps
 * attach to other apps' sessions the same way. Works for any app whose session we can see — through the
 * helper's list, Android's audio report (needs the DUMP permission, granted once through the root
 * service or adb) or the root service's dumpsys.
 *
 * The effects live in Voltcap's own process: they hold while Voltcap runs (as the home app it normally
 * does) and are put back by [AppMixer.ensure] after a restart. A low-latency game stream can lose its
 * fast path while an effect is on it (a few ms more latency): only apps you turned down get one.
 */
internal object MixerEffects {
    private const val TAG = "MixerEffects"
    /** session → the effect on it. */
    private val held = HashMap<Int, DynamicsProcessing>()

    val supported: Boolean get() = Build.VERSION.SDK_INT >= 28

    /** Sessions Android refused an effect on, in the last [sync]. */
    private val refusedSessions = HashSet<Int>()

    /** 1 = the effect is on this session, 2 = Android refused it, 0 = not tried yet. */
    @Synchronized
    fun stateOf(session: Int): Int = when {
        session in held -> 1
        session in refusedSessions -> 2
        else -> 0
    }

    /** How many sessions the last [sync] turned down, and how many refused an effect. */
    @Volatile var attached = 0
        private set
    @Volatile var refused = 0
        private set

    /**
     * Puts [ampByUid] (uid → 0..1, only apps below 100 %) on those apps' sessions among [players] and
     * takes the effect off every other session.
     */
    @Synchronized
    fun sync(ampByUid: Map<Int, Float>, players: List<MixerPlayer>) {
        if (!supported) return
        val wanted = HashMap<Int, Float>()
        players.forEach { p ->
            val amp = ampByUid[p.uid] ?: return@forEach
            if (p.session > 0) wanted[p.session] = amp
        }
        val it = held.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.key !in wanted) { runCatching { e.value.release() }; it.remove() }
        }
        var ok = 0
        var bad = 0
        refusedSessions.clear()
        wanted.forEach { (session, amp) ->
            val db = if (amp <= 0.0005f) -96f else (20.0 * log10(amp.toDouble())).toFloat().coerceIn(-96f, 0f)
            val fx = held[session] ?: runCatching { create(session) }
                .onFailure { Log.w(TAG, "no effect on session $session: ${it.message}") }
                .getOrNull()?.also { held[session] = it }
            if (fx == null) { bad++; refusedSessions += session; return@forEach }
            runCatching {
                fx.setInputGainAllChannelsTo(db)
                if (!fx.enabled) fx.enabled = true
            }.onSuccess { ok++ }.onFailure {
                bad++
                refusedSessions += session
                runCatching { fx.release() }
                held.remove(session)
            }
        }
        attached = ok
        refused = bad
    }

    @Synchronized
    fun releaseAll() {
        held.values.forEach { runCatching { it.release() } }
        held.clear()
        refusedSessions.clear()
        attached = 0
        refused = 0
    }

    @get:Synchronized
    val active: Boolean get() = held.isNotEmpty()

    /** Update 136: the sessions an effect is on now. */
    @Synchronized
    fun sessions(): Set<Int> = held.keys.toSet()

    private fun create(session: Int): DynamicsProcessing {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
            false, 0, false, 0, false, 0, false
        ).build()
        // Highest priority: an equalizer app on the same session doesn't take the control away.
        return DynamicsProcessing(Int.MAX_VALUE, session, config)
    }
}

/**
 * Who is playing, read from Android's own audio report ("dumpsys audio", its playback list): uid, usage,
 * state and session of each player. In Voltcap's own process when it holds the DUMP permission (no root
 * needed), else through the root service.
 */
internal object AudioReport {
    private const val DUMP = "android.permission.DUMP"
    /** The root service is asked to grant it once per run, not on every look. */
    @Volatile private var triedGrant = false

    fun hasDump(c: Context): Boolean = c.checkSelfPermission(DUMP) == PackageManager.PERMISSION_GRANTED

    /** Grants Voltcap the DUMP permission through the root service (once). True when it has it. */
    fun selfGrant(c: Context): Boolean {
        if (hasDump(c)) return true
        if (!RootShell.available || triedGrant) return false
        triedGrant = true
        val pkg = c.packageName
        if (!pkg.matches(Regex("[A-Za-z0-9._]+"))) return false
        RootShell.exec("pm grant $pkg $DUMP")
        return hasDump(c)
    }

    /** The adb command that grants it on devices without a root service. */
    fun adbCommand(c: Context) = "adb shell pm grant ${c.packageName} $DUMP"

    /** Players from Voltcap's own dump of the audio service, or null when it can't read it. */
    fun inProcess(c: Context): List<MixerPlayer>? {
        if (!hasDump(c)) return null
        val text = runCatching { dumpService("audio") }.onFailure { Log.w("AudioReport", "dump failed", it) }.getOrNull() ?: return null
        return parse(text.lineSequence())
    }

    /** Players from the root service's dumpsys (joined into one line: some return only the first). */
    fun viaRoot(): List<MixerPlayer>? {
        if (!RootShell.available) return null
        val out = RootShell.exec("dumpsys audio | grep 'u/pid:' | tr '\\n' '|'") ?: return null
        return parse(out.split('|', '\n').asSequence())
    }

    fun parse(lines: Sequence<String>): List<MixerPlayer> {
        val uidRe = Regex("u/pid:\\s*(\\d+)/")
        val stateRe = Regex("state:(\\w+)")
        val usageRe = Regex("usage=(USAGE_\\w+)")
        // "dumpsys audio" prints its players as "… -- session ID: 3881 …" (toString() says "sessionId:3881"):
        // both are read (only the second was, so no session was ever found from the report — nothing turned down).
        val sessionRe = Regex("(?:sessionId|session ID|session id):\\s*(-?\\d+)")
        val stateRe2 = Regex("state:\\s*(\\w+)")
        return lines.mapNotNull { line ->
            val uid = uidRe.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val state = (stateRe.find(line) ?: stateRe2.find(line))?.groupValues?.get(1).orEmpty()
            if (state == "released") return@mapNotNull null
            val usage = when (usageRe.find(line)?.groupValues?.get(1)) {
                "USAGE_MEDIA" -> 1
                "USAGE_GAME" -> 14
                null -> 0
                else -> 5
            }
            val session = sessionRe.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            MixerPlayer(uid, usage, state == "started", session)
        }.distinct().toList()
    }

    /** A system service's dump as text (asynchronous dump into a pipe, read here, 4 s at most). */
    /** A system service's dump ("power", "audio"…), read in Voltcap's own process; needs the DUMP permission. */
    fun dump(name: String, vararg args: String): String? = dumpService(name, arrayOf(*args))

    private fun dumpService(name: String, args: Array<String> = arrayOf()): String? {
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, name) as? IBinder ?: return null
        val pipe = ParcelFileDescriptor.createPipe()
        val read = pipe[0]
        val write = pipe[1]
        return try {
            binder.dumpAsync(write.fileDescriptor, args)
            write.close()
            val watchdog = Thread { runCatching { Thread.sleep(4_000L); read.close() } }.apply { isDaemon = true; start() }
            val text = runCatching {
                ParcelFileDescriptor.AutoCloseInputStream(read).use { s ->
                    val buf = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    while (buf.size() < 4 * 1024 * 1024) {
                        val n = s.read(chunk)
                        if (n < 0) break
                        buf.write(chunk, 0, n)
                    }
                    buf.toString(Charsets.UTF_8.name())
                }
            }.getOrNull()
            watchdog.interrupt()
            text
        } finally {
            runCatching { write.close() }
            runCatching { read.close() }
        }
    }
}

/**
 * Update 119: the audio sessions apps announce themselves for equalizers (Android's "open audio effect control
 * session" broadcast — Spotify sends it as it starts playing). Spotify plays through native audio that Android's
 * report shows without a session, so its sound couldn't be reached and turning it down did nothing; with the
 * session it announced, the mixer's sound effect goes on its stream like an equalizer app's would. Kept on the
 * device too (a session lives as long as the app's player), so a restart of Voltcap still knows it.
 */
internal object AudioSessions {
    private const val PREFS = "mixer_sessions"
    /** package → its open sessions. */
    private val open = HashMap<String, MutableSet<Int>>()
    @Volatile private var registered = false
    @Volatile private var loaded = false

    @Synchronized
    private fun load(c: Context) {
        if (loaded) return
        loaded = true
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.forEach { (pkg, v) ->
            (v as? String)?.split(',')?.mapNotNull { it.toIntOrNull() }?.filter { it > 0 }?.let { if (it.isNotEmpty()) open[pkg] = it.toMutableSet() }
        }
    }

    @Synchronized
    private fun save(c: Context) {
        val e = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear()
        open.forEach { (pkg, s) -> if (s.isNotEmpty()) e.putString(pkg, s.joinToString(",")) }
        e.apply()
    }

    /** Starts listening (once per process). */
    fun register(c: Context) {
        if (registered) return
        registered = true
        val app = c.applicationContext
        load(app)
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: android.content.Intent) {
                val session = intent.getIntExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, 0)
                val pkg = intent.getStringExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME) ?: return
                if (session <= 0) return
                synchronized(this@AudioSessions) {
                    when (intent.action) {
                        android.media.audiofx.AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                            // An app keeps one or two players: the newest few are enough.
                            val s = open.getOrPut(pkg) { LinkedHashSet() }
                            s += session
                            while (s.size > 4) s.remove(s.first())
                        }
                        android.media.audiofx.AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> open[pkg]?.remove(session)
                    }
                    save(app)
                }
                // The mixer looks again at once (a turned-down app gets its effect on the new stream).
                runCatching { AppMixer.sessionsChanged(app) }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.media.audiofx.AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(android.media.audiofx.AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else app.registerReceiver(receiver, filter)
        }
    }

    @Synchronized
    fun sessionsFor(pkg: String): Set<Int> = open[pkg]?.toSet().orEmpty()

    @Synchronized
    fun any(): Boolean = open.values.any { it.isNotEmpty() }

    /** The announced sessions as players for the sound effect (no player handle: only the effect reaches them). */
    @Synchronized
    fun players(c: Context): List<MixerPlayer> {
        load(c)
        val pm = c.packageManager
        return open.flatMap { (pkg, sessions) ->
            val uid = runCatching { pm.getApplicationInfo(pkg, 0).uid }.getOrNull() ?: return@flatMap emptyList()
            sessions.map { MixerPlayer(uid, 1, true, it, controllable = false) }
        }
    }
}
