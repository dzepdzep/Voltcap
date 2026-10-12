package com.shelf.launcher.device.audio

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import com.shelf.launcher.device.RootShell

/**
 * Volume per app (the R1 menu, the in-game panel, a Quick Settings tile and a hotkey open it).
 *
 * Android has no public way to change one app's volume. Voltcap has two:
 *  - **Player volume** ([Method.HELPER]): where the handheld has its own root service (AYN / Retroid),
 *    Voltcap starts [MixerHelper] through it as a small privileged process that holds each app's volume on
 *    that app's players (the MIUI / NoMixer way). Nothing to install, no Shizuku.
 *  - **Sound effect** ([Method.EFFECT]): a gain effect Voltcap puts on the app's audio sessions itself
 *    ([MixerEffects]). Needs to see the sessions: through the helper, or Android's audio report with the
 *    DUMP permission (granted once through the root service, or with one adb command anywhere).
 * Automatic picks the player volume when the helper answers, else the effect.
 *
 * Volumes are kept per app (prefs "mixer", device-only) as 0–100 % on top of the media volume, following
 * the ear (50 % ≈ half as loud), and put back after a restart (at Voltcap's start and each game launch).
 * A game can have its own volume for its emulator ("Just for this game", prefs "mixer_games"); it holds
 * while that game runs and the emulator's usual volume comes back after.
 * Blocking calls (root service, a short process, a dump): call them off the main thread.
 */
object AppMixer {
    private const val PREFS = "mixer"
    private const val GAMES = "mixer_games"
    private const val META = "mixer_meta"
    private const val DAEMON = "voltcap_mixd"
    private const val LISTER = "voltcap_mixl"
    /**
     * pgrep / pkill pattern for the helper. "[d]" matches "d" but not itself: the shell running pgrep has
     * the pattern in its own command line, and a plain "voltcap_mixd" would find (or kill) that shell.
     */
    private const val DAEMON_MATCH = "voltcap_mix[d]"

    enum class Method(val label: String, val blurb: String) {
        AUTO("Automatic", "The sound effect on every app you turn down (Android's own, works on any firmware); the root service only finds who's playing."),
        HELPER("Player volume", "Sets each player's own volume through the root service. Some firmware quietly ignores it (the check passes, nothing gets quieter)."),
        EFFECT("Sound effect", "Turns the app's sound down with an effect Voltcap holds. Works without the helper; low-latency game audio may get a few ms more delay.")
    }

    /** One app in the mixer: playing now, set to its own volume, the game running, or added by you. */
    data class AudioApp(
        val packageName: String,
        val label: String,
        val uid: Int,
        val playing: Boolean,
        /** What it plays ("Game", "Media"…), from the players' usage. */
        val kind: String?,
        /** 0–100 %: what holds now (the game's own volume while that game runs). */
        val volume: Int,
        /** The game running from Voltcap plays through this app. */
        val runningGame: String? = null,
        /** [volume] is the running game's own one ("Just for this game"). */
        val gameOnly: Boolean = false,
        /** Added by hand (stays listed at 100 %). */
        val pinned: Boolean = false,
        /** Why this app's volume may not hold (its sound can't be reached), or null when it should. */
        val note: String? = null
    )

    /** What the last look found: shown at the top of the mixer. */
    data class Status(
        /** How volumes are set right now (null: per-app volume isn't possible here). */
        val method: Method?,
        /** Where the list came from ("helper", "Android's audio report"…). */
        val source: String?,
        /** Why something didn't work, or null. */
        val problem: String?
    )

    @Volatile var status: Status = Status(null, null, null)
        private set

    /** Kept for the old card: the status problem. */
    val lastError: String? get() = status.problem

    /** The game running from Voltcap (set at launch, cleared when you're back). */
    data class RunningGame(val key: String, val name: String, val packageName: String)

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun games(c: Context) = c.applicationContext.getSharedPreferences(GAMES, Context.MODE_PRIVATE)
    private fun meta(c: Context) = c.applicationContext.getSharedPreferences(META, Context.MODE_PRIVATE)

    /** The helper can be tried (the handheld's root service is there). */
    val helperPossible: Boolean get() = RootShell.available

    /** Kept for callers: per-app volume can work on this device in some way (root service or DUMP + effects). */
    val supported: Boolean get() = RootShell.available

    /** Per-app volume can work here: the root service, or the effect with Android's audio report. */
    fun available(c: Context): Boolean = RootShell.available || (MixerEffects.supported && (AudioReport.hasDump(c) || AudioSessions.any()))

    /** Update 119: an app announced (or closed) an audio session: the effect loop looks again at once. */
    internal fun sessionsChanged(c: Context) {
        if (effective(c).isNotEmpty() && resolved(c) != null) EffectLoop.start(c, now = true)
        // Spotify was turned down through its own volume before it announced a session: now the effect holds it.
        if (AudioSessions.sessionsFor(SPOTIFY).isNotEmpty() && meta(c).getBoolean("spotify_api_volume", false)) {
            // Update 144: back to full only on the device it was turned down on, never on whatever is active now.
            val device = meta(c).getString("spotify_api_device", null)
            meta(c).edit().putBoolean("spotify_api_volume", false).remove("spotify_api_device").apply()
            if (device != null) Thread {
                runCatching {
                    kotlinx.coroutines.runBlocking {
                        kotlinx.coroutines.withTimeoutOrNull(4_000L) { com.shelf.launcher.spotify.SpotifyRepository(c).setVolume(100, device) }
                    }
                }
            }.start()
        }
    }

    /**
     * Update 121, Mixer check › Way: "System duck" holds the volumes with a constant VolumeShaper on each player
     * (how Android ducks an app under a notification) instead of the player's own volume. For handhelds where
     * the check passes but nothing gets quieter.
     */
    fun duckWay(c: Context): Boolean = meta(c).getBoolean("duck_way", false)

    /** Switches the way; everything turned down is put back once and held again the new way. Blocking. */
    fun setDuckWay(c: Context, on: Boolean) {
        if (duckWay(c) == on) return
        val had = effective(c).keys
        meta(c).edit().putBoolean("duck_way", on).commit()
        daemonSig = ""
        // Back to normal first (both ways: setting 1.0 clears the shaper and the player's own volume), then held again.
        releasePlayers(c, had)
        apply(c)
    }

    fun method(c: Context): Method = runCatching { Method.valueOf(meta(c).getString("method", null) ?: "AUTO") }.getOrDefault(Method.AUTO)

    /**
     * Update 136: this firmware moves an effect put on one app's audio session onto all of the sound (AYN's Odin 2 /
     * Thor audio server: "createEffect sessionId 0: sessionId_old <app's>"). Turning one app down with the sound effect
     * then turned everything down — or nothing — so Automatic uses the player volume instead. Found by [checkRemap].
     */
    fun fxGlobal(c: Context): Boolean = meta(c).getBoolean("fx_global", false)

    /** Picks how volumes are set; re-applies everything the new way. Blocking. */
    fun setMethod(c: Context, m: Method) {
        val before = effective(c).keys
        // (Update 136: picking Automatic again looks once more whether the sound effect stays on its app here.)
        meta(c).edit().putString("method", m.name).apply { if (m == Method.AUTO) remove("fx_global") }.commit()
        // Moving to the sound effect: the player volumes the helper had set go back to normal (else the two stack up).
        apply(c, resets = if (m == Method.EFFECT) before else emptyList())
    }

    /** Saved volumes, package → percent (only the ones not at 100 %). */
    fun volumes(c: Context): Map<String, Int> =
        prefs(c).all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it.coerceIn(0, 100) } }.toMap()

    /** Apps added by hand ("Add an app"): listed even when quiet and at 100 %. */
    fun pinned(c: Context): Set<String> = meta(c).getStringSet("pinned", emptySet()).orEmpty()

    fun pin(c: Context, pkg: String, on: Boolean) {
        val now = pinned(c).toMutableSet()
        if (on) now += pkg else now -= pkg
        meta(c).edit().putStringSet("pinned", now).apply()
    }

    fun runningGame(c: Context): RunningGame? {
        val m = meta(c)
        val key = m.getString("game_key", null) ?: return null
        val pkg = m.getString("game_pkg", null) ?: return null
        return RunningGame(key, m.getString("game_name", null) ?: "this game", pkg)
    }

    /** A game's own volume for its emulator / app, or null (it uses that app's usual volume). */
    fun gameVolume(c: Context, key: String): Int? = games(c).getString(key, null)?.substringAfterLast('|')?.toIntOrNull()?.coerceIn(0, 100)

    /** The volumes that hold right now: the saved ones, with the running game's own one on top. */
    fun effective(c: Context): Map<String, Int> {
        val base = volumes(c).toMutableMap()
        val g = runningGame(c)
        if (g != null) gameVolume(c, g.key)?.let { base[g.packageName] = it }
        // Volumes saved for apps that are no longer installed don't count (they can't be seen or cleared in the list, and
        // would keep the mixer's background work running for ever).
        return base.filterValues { it < 100 }.filterKeys { uidOf(c, it) != null }
    }

    /** Sets one app's volume (or the running game's own one with [forGame]) and applies it. Blocking. */
    fun setVolume(c: Context, packageName: String, percent: Int, forGame: Boolean = false) {
        val v = percent.coerceIn(0, 100)
        val before = effective(c).keys
        val g = runningGame(c)
        if (forGame && g != null && g.packageName == packageName) {
            games(c).edit().putString(g.key, "$packageName|$v").commit()
        } else {
            val p = prefs(c)
            if (v >= 100) p.edit().remove(packageName).commit() else p.edit().putInt(packageName, v).commit()
        }
        apply(c, resets = before - effective(c).keys)
        MixerNotification.refreshAsync(c)
        // Update 119: Spotify, before it has announced its audio session (it does as it starts playing): its own
        // volume through your Spotify account (Settings › Music › Spotify), where Spotify lets this device's volume be set.
        // Update 144: only on this handheld's own Spotify device while it plays; without a device id Spotify would
        // set the volume of whatever is playing (a speaker, a phone, a PC).
        if (packageName == SPOTIFY && AudioSessions.sessionsFor(SPOTIFY).isEmpty()) runCatching {
            val (ok, device) = kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(4_000L) {
                    val repo = com.shelf.launcher.spotify.SpotifyRepository(c)
                    val id = repo.localActiveDeviceId()
                    if (id == null) false to null else (repo.setVolume(v, id) is com.shelf.launcher.spotify.SpotifyResult.Ok<*>) to id
                }
            } ?: (false to null)
            // Remembered (with the device), so it goes back to full once the sound effect takes over (else the two would stack up).
            meta(c).edit().putBoolean("spotify_api_volume", ok && v < 100)
                .putString("spotify_api_device", if (ok && v < 100) device else null).apply()
        }
    }

    private const val SPOTIFY = "com.spotify.music"

    /** "Just for this game" on / off for the running game: on starts from what the app has now. Blocking. */
    fun setGameOnly(c: Context, on: Boolean) {
        val g = runningGame(c) ?: return
        val before = effective(c).keys
        if (on) {
            val now = volumes(c)[g.packageName] ?: 100
            games(c).edit().putString(g.key, "${g.packageName}|$now").commit()
        } else games(c).edit().remove(g.key).commit()
        apply(c, resets = before - effective(c).keys)
    }

    /** Every app back to 100 % (the running game's own volume too). Blocking. */
    fun resetAll(c: Context) {
        val had = effective(c).keys
        prefs(c).edit().clear().commit()
        runningGame(c)?.let { games(c).edit().remove(it.key).commit() }
        apply(c, resets = had)
        MixerNotification.refreshAsync(c)
    }

    /** A game started from Voltcap: its own volume (if it has one) holds while it runs. Blocking. */
    fun onGameLaunch(c: Context, key: String, name: String, packageName: String?) {
        val before = effective(c).keys
        val m = meta(c).edit()
        if (packageName == null) m.remove("game_key").remove("game_pkg").remove("game_name")
        else m.putString("game_key", key).putString("game_pkg", packageName).putString("game_name", name)
        m.commit()
        val changed = gameVolume(c, key) != null || before != effective(c).keys
        if (changed) apply(c, resets = before - effective(c).keys) else ensure(c)
        MixerNotification.refreshAsync(c)
    }

    /** Back in Voltcap: the emulator's usual volume again. Blocking. */
    fun onGameEnded(c: Context) {
        val g = runningGame(c) ?: return
        val hadOwn = gameVolume(c, g.key) != null
        val before = effective(c).keys
        meta(c).edit().remove("game_key").remove("game_pkg").remove("game_name").commit()
        if (hadOwn) apply(c, resets = before - effective(c).keys)
        MixerNotification.refreshAsync(c)
    }

    /** At start-up and game launch: holds the saved volumes again (after a restart the helper is gone). Blocking. */
    fun ensure(c: Context) {
        // Update 119: the sessions apps announce for equalizers (Spotify's own), listened for from the start.
        AudioSessions.register(c)
        // The notification-shade mixer is posted (or removed) at every start / game launch.
        MixerNotification.refreshAsync(c)
        if (!available(c) || effective(c).isEmpty()) return
        // Update 122: Automatic moved to the sound effect: once, the old helper is stopped and the player volumes it
        // set are put back (they'd stack up with the effect where they did work).
        if (!meta(c).getBoolean("auto_effect_122", false)) {
            meta(c).edit().putBoolean("auto_effect_122", true).apply()
            if (method(c) == Method.AUTO && resolved(c) == Method.EFFECT && RootShell.available) {
                RootShell.exec("pkill -f '$DAEMON_MATCH'")
                daemonSig = ""
                releasePlayers(c, effective(c).keys)
            }
        }
        // First time this run: find out whether the helper really answers (Automatic falls back to the effect).
        if (helperOk == null && RootShell.available) helperOk = helper(c, "list")?.let { parseList(it) } != null
        when (resolved(c)) {
            Method.HELPER -> {
                // Already running: leave it (a fresh one would only repeat the same volumes).
                if (!daemonRunning()) apply(c) else EffectLoop.start(c)
            }
            Method.EFFECT -> EffectLoop.start(c)
            else -> Unit
        }
    }

    // ---- who's playing ----

    /** Apps playing right now, plus the ones with their own volume, the game running and added ones. Blocking. */
    fun apps(c: Context): List<AudioApp> {
        val app = c.applicationContext
        val pm = app.packageManager
        val saved = volumes(app)
        val eff = effective(app)
        val game = runningGame(app)
        val pins = pinned(app)
        data class Seen(var playing: Boolean, val usages: MutableSet<Int>)
        val byUid = LinkedHashMap<Int, Seen>()
        val found = discover(app)
        found.players.forEach { p ->
            if (p.uid < 10_000) return@forEach // Android itself (system sounds, media server)
            val s = byUid.getOrPut(p.uid) { Seen(false, mutableSetOf()) }
            if (p.started) s.playing = true
            s.usages += p.usage
        }
        val m = resolved(app)
        status = Status(
            method = m,
            source = found.source,
            problem = found.problem ?: when {
                !available(app) -> null
                m == null -> "Per-app volume can't be set here: the helper didn't answer and Android's audio report can't be read."
                else -> null
            }
        )
        val list = ArrayList<AudioApp>()
        val listed = HashSet<String>()
        fun noteFor(uid: Int, volume: Int): String? {
            if (volume >= 100) return null
            // The players the sound effect has to cover: the ones the helper has no handle on (or all, in effect mode).
            val covered = found.players.filter { it.uid == uid && (m == Method.EFFECT || !it.controllable) }
            if (covered.isEmpty()) return null
            // (Update 119: a native player without a session is fine when the app announced one itself.)
            val reachable = covered.filter { it.session > 0 }
            // (Update 125: no session at all — native low-latency audio: its player volume is held instead, with the root service.)
            if (reachable.isEmpty() && (sessionlessHeld(uid) || (RootShell.available && m == Method.EFFECT))) return null
            if (reachable.isEmpty()) return "Its sound has no audio session Voltcap can reach yet: pause and play it once, so it announces one."
            val states = reachable.map { MixerEffects.stateOf(it.session) }
            return when {
                states.any { it == 2 } -> "Android refused the sound effect on this app's stream (low-latency game audio often does), so it can't be turned down."
                states.any { it == 0 } -> "Turning it down… (the sound effect is attaching to its audio)"
                else -> null
            }
        }
        fun add(pkg: String, uid: Int, seen: Seen?) {
            if (!listed.add(pkg)) return
            val isGame = game?.packageName == pkg
            list += AudioApp(
                packageName = pkg,
                label = label(app, pkg),
                uid = uid,
                playing = seen?.playing == true,
                kind = seen?.let { kind(it.usages) },
                volume = eff[pkg] ?: 100,
                runningGame = game?.name?.takeIf { isGame },
                gameOnly = isGame && game != null && gameVolume(app, game.key) != null,
                pinned = pkg in pins,
                note = noteFor(uid, eff[pkg] ?: 100)
            )
        }
        byUid.forEach { (uid, seen) ->
            val pkg = runCatching { pm.getPackagesForUid(uid)?.firstOrNull() }.getOrNull() ?: return@forEach
            add(pkg, uid, seen)
        }
        (listOfNotNull(game?.packageName) + saved.keys + pins).forEach { pkg ->
            if (pkg in listed) return@forEach
            val uid = uidOf(app, pkg) ?: return@forEach
            add(pkg, uid, null)
        }
        return list.sortedWith(
            compareByDescending<AudioApp> { it.runningGame != null }
                .thenByDescending { it.playing }
                .thenBy { it.label.lowercase() }
        )
    }

    /** Apps that can be added by hand: everything with a launcher icon, by name. Blocking. */
    fun launchables(c: Context): List<Pair<String, String>> {
        val pm = c.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
            .map { it.activityInfo.packageName }
            .filter { it != c.packageName }
            .distinct()
            .map { it to label(c, it) }
            .sortedBy { it.second.lowercase() }
    }

    fun openIntent(c: Context): Intent =
        Intent(c.applicationContext, com.shelf.launcher.ui.MixerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** One line of the mixer check. */
    data class Check(val step: String, val ok: Boolean?, val detail: String)

    /**
     * Every step of the mixer, one by one, for "Mixer check": root service, the helper starting, the audio
     * service answering, Android's audio report, setting a volume. Blocking (a few seconds).
     * (Update 144: every detail starts with a capital, as it's shown on its own under its step.)
     */
    fun diagnose(c: Context): List<Check> {
        val app = c.applicationContext
        val out = ArrayList<Check>()
        // Information only (no pass / fail): which Android this is.
        out += Check("Android", null, "Android ${androidName()} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
        val root = RootShell.available
        out += Check("Root service", root, if (root) (RootShell.exec("id")?.take(90) ?: "Answers") else "Not on this firmware (AYN / Retroid have one)")
        if (root) {
            val apk = app.applicationInfo.sourceDir.orEmpty()
            out += Check("App file", apk.matches(SAFE_PATH), if (apk.matches(SAFE_PATH)) apk.takeLast(60) else "Path has characters the shell can't take: $apk")
            var pong: Int? = null
            for (i in VARIANTS.indices) {
                val cmd = command(app, "voltcap_mixp", i) ?: continue
                val raw = RootShell.exec("$cmd ping 2>&1 | tr '\\n' ' '") ?: RootShell.exec("$cmd ping")
                val ok = raw?.contains("${MixerHelper.MARK} PONG") == true
                out += Check("Helper start (${VARIANT_NAMES[i]})", ok, raw?.let { answerOf(it, all = true) ?: it.take(140) }?.ifBlank { "No output" } ?: "The root service didn't run it")
                if (ok) { pong = i; break }
            }
            if (pong != null) {
                meta(app).edit().putInt("variant", pong).apply()
                run {
                    val nativeRaw = RootShell.exec("${command(app, LISTER, pong)} list 2>/dev/null").orEmpty()
                    val native = answerOf(nativeRaw)?.let { parseList(it) }?.filter { !it.controllable }.orEmpty()
                    if (native.isNotEmpty()) out += Check(
                        "Native players", true,
                        "${native.size} player${if (native.size == 1) "" else "s"} without a volume handle (Spotify's native audio): turned down with the sound effect"
                    )
                }
                val raw = RootShell.exec("${command(app, LISTER, pong)} list 2>/dev/null").orEmpty()
                val ans = answerOf(raw)
                val players = ans?.let { parseList(it) }
                out += Check("Audio service (helper)", players != null,
                    when {
                        players != null -> "${players.size} player${if (players.size == 1) "" else "s"} with their app" + (ans.split(';').getOrNull(2)?.toIntOrNull()?.takeIf { it > 0 }?.let { " · $it hidden" } ?: "")
                        ans != null -> ans.take(140)
                        else -> raw.take(140).ifBlank { "No answer" }
                    })
            }
        }
        val dumpOk = AudioReport.hasDump(app) || AudioReport.selfGrant(app)
        out += Check("DUMP permission", dumpOk, if (dumpOk) "Granted (Android's audio report)" else "Not granted · ${AudioReport.adbCommand(app)}")
        if (dumpOk) {
            val p = AudioReport.inProcess(app)
            // Players but no audio session among them: the sound effect has nothing to attach to.
            out += Check("Audio report (Voltcap)", p != null && (p.isEmpty() || p.any { it.session > 0 }), p?.let { "${it.size} players · ${it.count { x -> x.session > 0 }} with a session" } ?: "Couldn't be read")
        }
        if (root) {
            val p = AudioReport.viaRoot()
            out += Check("Audio report (root)", p?.isNotEmpty(), p?.let { "${it.size} players" } ?: "Dumpsys didn't run")
        }
        out += Check(
            "Sound effect", MixerEffects.supported && !fxGlobal(app),
            when {
                !MixerEffects.supported -> "Needs Android 9"
                // (Update 136)
                fxGlobal(app) -> "This firmware puts an app's effect on all of the sound, so it isn't used (Player volume instead)"
                else -> "Available (Dynamics processing)"
            }
        )
        val m = resolved(app)
        out += Check("Volumes set with", m != null, (m?.label ?: "Nothing works here yet") + if (m == Method.HELPER && duckWay(app)) " · system duck way" else "")
        // Update 121: the background process that holds the volumes is really running (a firmware that cleans up
        // its shell's processes ends it right after the start: then the check passed, but nothing stayed quieter).
        val turnedDown = effective(app)
        if (m == Method.HELPER) out += if (turnedDown.isEmpty()) Check("Volume keeper", null, "Nothing turned down right now (turn an app down, then check again)")
        else {
            val alive = daemonRunning()
            Check("Volume keeper", alive, if (alive) "Running, holding ${turnedDown.size} app${if (turnedDown.size == 1) "" else "s"}" else "Not running: the firmware ended it after the start (Voltcap starts it again every few seconds)")
        }
        // Which of the turned-down apps are playing, and whether their players can be reached at all.
        if (root && turnedDown.isNotEmpty()) {
            val raw = helper(app, "list")
            val players = raw?.let { parseList(it) }
            if (players != null) turnedDown.keys.forEach { pkg ->
                val uid = uidOf(app, pkg) ?: return@forEach
                val mine = players.filter { it.uid == uid }
                val handles = mine.count { it.controllable }
                // Update 122: with the sound effect (Automatic now): is the effect really on this app's sessions?
                if (m == Method.EFFECT) {
                    val sessions = mine.map { it.session }.filter { it > 0 }.distinct()
                    val on = sessions.count { MixerEffects.stateOf(it) == 1 }
                    val refusedHere = sessions.count { MixerEffects.stateOf(it) == 2 }
                    out += Check(
                        "${label(app, pkg)} (${turnedDown[pkg]} %)",
                        if (mine.isEmpty()) null else on > 0,
                        when {
                            mine.isEmpty() -> "Not playing right now (play something in it, then check again)"
                            sessions.isEmpty() -> "Playing, but Android reports no audio session for it: the effect has nothing to go on"
                            on > 0 -> "Effect on $on of ${sessions.size} session${if (sessions.size == 1) "" else "s"}" + if (refusedHere > 0) " · $refusedHere refused" else ""
                            refusedHere > 0 -> "Android refused the effect on its session (exclusive low-latency audio)"
                            else -> "${sessions.size} session${if (sessions.size == 1) "" else "s"} found, the effect goes on within a few seconds (check again)"
                        }
                    )
                    return@forEach
                }
                out += Check(
                    "${label(app, pkg)} (${turnedDown[pkg]} %)",
                    if (mine.isEmpty()) null else handles > 0 || MixerEffects.attached > 0,
                    when {
                        mine.isEmpty() -> "Not playing right now (play something in it, then check again)"
                        handles == mine.size -> "$handles player${if (handles == 1) "" else "s"}, all with a volume handle"
                        handles > 0 -> "${mine.size} players, $handles with a volume handle (the others get the sound effect)"
                        else -> "${mine.size} player${if (mine.size == 1) "" else "s"} without a volume handle (native audio): only the sound effect can turn it down"
                    }
                )
            }
        }
        if (m == Method.EFFECT && effective(app).isNotEmpty()) out += Check(
            "Effect now", MixerEffects.attached > 0 && MixerEffects.refused == 0,
            if (MixerEffects.active || MixerEffects.refused > 0) "${MixerEffects.attached} session(s) turned down, ${MixerEffects.refused} refused"
            else "No session of the apps you turned down found yet (play something in them)"
        )
        return out
    }

    // ---- internals ----

    private fun androidName(): String = when (Build.VERSION.SDK_INT) {
        33 -> "13"; 34 -> "14"; 35 -> "15"; 36 -> "16"; 32, 31 -> "12"; 30 -> "11"; 29 -> "10"; 28 -> "9"
        else -> Build.VERSION.RELEASE ?: "?"
    }

    private val SAFE_PATH = Regex("[A-Za-z0-9_./=+~-]+")
    private val VARIANT_NAMES = listOf("CLASSPATH", "-Djava.class.path", "64-bit")

    /** env, binary, VM option. */
    private val VARIANTS = listOf(
        Triple("CLASSPATH=%apk%", "/system/bin/app_process", ""),
        Triple("", "/system/bin/app_process", "-Djava.class.path=%apk%"),
        Triple("CLASSPATH=%apk%", "/system/bin/app_process64", "")
    )

    /** The helper's command line for launch [variant] (null when the app's path isn't shell-safe). */
    private fun command(c: Context, niceName: String, variant: Int, launcher: String = ""): String? {
        val apk = c.applicationInfo.sourceDir ?: return null
        if (!apk.matches(SAFE_PATH)) return null
        val (env, bin, vm) = VARIANTS.getOrElse(variant) { VARIANTS[0] }
        return listOf(env.replace("%apk%", apk), launcher, bin, vm.replace("%apk%", apk), "/system/bin", "--nice-name=$niceName", MixerHelper::class.java.name)
            .filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** The helper's answer (after its mark) in [raw] shell output, or null. [all]: the rest of the line. */
    private fun answerOf(raw: String, all: Boolean = false): String? {
        val at = raw.indexOf(MixerHelper.MARK + " ")
        if (at < 0) return null
        val rest = raw.substring(at + MixerHelper.MARK.length + 1)
        return if (all) rest.lineSequence().first().trim() else rest.takeWhile { !it.isWhitespace() }
    }

    /** "OK;total;hidden;uid,usage,state,session;…" → players, or null for anything else. */
    private fun parseList(ans: String): List<MixerPlayer>? {
        val parts = ans.split(';')
        if (parts.firstOrNull() != "OK") return null
        return parts.drop(3).mapNotNull { e ->
            val f = e.split(',')
            val uid = f.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            MixerPlayer(uid, f.getOrNull(1)?.toIntOrNull() ?: 0, f.getOrNull(2)?.toIntOrNull() == 2 /* STARTED */, f.getOrNull(3)?.toIntOrNull() ?: 0, f.getOrNull(4) != "0")
        }
    }

    /** The volumes the helper process was last started with, and when (so an unchanged set isn't restarted, and a dead one is). */
    @Volatile private var daemonSig = ""
    @Volatile private var daemonStartedAt = 0L

    private fun daemonRunning(): Boolean =
        RootShell.exec("pgrep -f '$DAEMON_MATCH'")?.lines()?.any { it.trim().toIntOrNull() != null } == true

    @Volatile private var helperOk: Boolean? = null
    /** Consecutive looks the helper didn't answer: one hiccup (a slow start under load) doesn't drop it for good. */
    @Volatile private var helperFails = 0
    /** When the helper is tried again after it failed (Automatic doesn't spawn a process on every look while it can't run). */
    @Volatile private var helperRetryAt = 0L

    /** Runs the helper's [mode] with the launch variant that worked (trying the others once). */
    private fun helper(c: Context, mode: String): String? {
        if (!RootShell.available) return null
        val m = meta(c)
        val known = m.getInt("variant", -1)
        val order = (listOf(known) + VARIANTS.indices).filter { it >= 0 }.distinct()
        for (v in order) {
            val cmd = command(c, LISTER, v) ?: return null
            val ans = RootShell.exec("$cmd $mode 2>/dev/null")?.let { answerOf(it) }
            if (ans != null) {
                if (v != known) m.edit().putInt("variant", v).apply()
                return ans
            }
            // A known-good way that stopped answering: don't walk the others on every look.
            if (v == known && helperOk == true) break
        }
        return null
    }

    private class Found(val players: List<MixerPlayer>, val source: String?, val problem: String?)

    /** Who's playing: the helper, else Android's audio report (in Voltcap, else through root). */
    private fun discover(c: Context, fast: Boolean = false): Found {
        val f = discoverPlayers(c, fast)
        // Update 119: plus the sessions apps announced themselves (Spotify's native audio has none in the report).
        // Only for apps whose sound isn't reached already (a player with a session, or one the helper holds itself):
        // else the app would be turned down twice.
        val announced = AudioSessions.players(c).filter { a ->
            f.players.none { it.session == a.session } &&
                f.players.none { it.uid == a.uid && (it.session > 0 || (it.controllable && f.source == "helper")) }
        }
        if (announced.isEmpty()) return f
        return Found(f.players + announced, f.source ?: "apps' own audio sessions", f.problem)
    }

    private fun discoverPlayers(c: Context, fast: Boolean = false): Found {
        var problem: String? = null
        // Looking for sessions to put the effect on: Android's own report, read in Voltcap in ~100 ms, instead of a new
        // helper process (a second or two each) on every look.
        if (fast && AudioReport.hasDump(c)) AudioReport.inProcess(c)?.let { return Found(it, "Android's audio report", null) }
        val now = System.currentTimeMillis()
        val tryHelper = RootShell.available && (helperOk != false || method(c) == Method.HELPER || now >= helperRetryAt)
        if (tryHelper) {
            val ans = helper(c, "list")
            val list = ans?.let { parseList(it) }
            if (list != null) { helperOk = true; helperFails = 0 }
            else {
                helperFails++
                if (helperOk != true || helperFails >= 2) { helperOk = false; helperRetryAt = now + 5 * 60_000L }
            }
            if (list != null) {
                val hidden = ans.split(';').getOrNull(2)?.toIntOrNull() ?: 0
                if (list.isNotEmpty() || hidden == 0) return Found(list, "helper", null)
                problem = "Android hid which app owns its $hidden player${if (hidden == 1) "" else "s"} from the helper on this firmware."
            } else {
                problem = if (ans != null) "The helper couldn't ask Android's audio service (${ans.removePrefix("ERR:").take(70)})."
                else "The mixer helper didn't start through the root service. Run Mixer check for details."
            }
        }
        if (AudioReport.hasDump(c) || AudioReport.selfGrant(c)) {
            AudioReport.inProcess(c)?.let { return Found(it, "Android's audio report", problem.takeIf { helperOk == false && method(c) == Method.HELPER }) }
        }
        AudioReport.viaRoot()?.takeIf { it.isNotEmpty() }?.let { return Found(it, "Android's audio report (root)", problem.takeIf { method(c) == Method.HELPER }) }
        return Found(emptyList(), null, problem)
    }

    /** How volumes are set now (null: no way works here). */
    private fun resolved(c: Context): Method? {
        val pick = method(c)
        val helper = RootShell.available && helperOk != false
        val effect = MixerEffects.supported && (AudioReport.hasDump(c) || RootShell.available || AudioSessions.any())
        return when (pick) {
            Method.HELPER -> Method.HELPER.takeIf { RootShell.available }
            Method.EFFECT -> Method.EFFECT.takeIf { effect }
            // Update 122: the sound effect first. The player-volume way (and System duck) are calls into the app's own
            // process from the root service, which some firmware drops without a word (the AYN Thor: every check passed,
            // nothing got quieter). The effect is Voltcap's own, on the app's audio session in Android's audio server.
            Method.AUTO -> when {
                effect && !fxGlobal(c) -> Method.EFFECT
                // (Update 136: an effect this firmware spreads over all the sound is no per-app volume at all, so
                // without the helper nothing is used.)
                helper -> Method.HELPER
                else -> null
            }
        }
    }

    /** Percent → player volume / gain, following the ear (50 % ≈ a quarter of the amplitude, half as loud). */
    internal fun amplitude(percent: Int): Float {
        val p = percent.coerceIn(0, 100) / 100f
        return p * p
    }

    /**
     * Applies the volumes that hold now, the way picked: (re)starts the helper with them (putting [resets]
     * back to 100 % once), or hands them to the effect loop. Nothing to hold: no helper, no effects.
     */
    @Synchronized
    private fun apply(c: Context, resets: Collection<String> = emptyList()) {
        val app = c.applicationContext
        val vols = effective(app)
        when (resolved(app)) {
            Method.HELPER -> {
                // The helper turns down players that have a handle; native streams (Spotify's AAudio / OpenSL) get the
                // sound effect instead (EffectLoop only touches the players the helper reports as not controllable).
                if (vols.isEmpty()) EffectLoop.stop() else EffectLoop.start(app, now = true)
                val specs = vols.mapNotNull { (pkg, v) -> uidOf(app, pkg)?.let { "$it:${"%.4f".format(java.util.Locale.US, amplitude(v))}" } } +
                    resets.distinct().filter { it !in vols }.mapNotNull { pkg -> uidOf(app, pkg)?.let { "$it:1.0000" } }
                val duck = if (duckWay(app)) "--duck " else ""
                val sig = if (specs.isEmpty()) "" else duck + specs.distinct().sorted().joinToString(" ")
                // The same volumes already held by a live helper: leave it. (Every restart left the app loud for the
                // second or two the new process takes to start, and each slider step restarted it.)
                if (sig.isNotEmpty() && resets.isEmpty() && sig == daemonSig && daemonRunning()) return
                // (Also ends a start of an older update still waiting in the root service: its shell names the helper.)
                RootShell.exec("pkill -f '$DAEMON_MATCH'")
                daemonSig = sig
                if (specs.isEmpty()) return
                daemonStartedAt = android.os.SystemClock.uptimeMillis()
                // In the background, in its own session (setsid, where the firmware has it), so it outlives this
                // call and Voltcap itself.
                val variant = meta(app).getInt("variant", 0).coerceAtLeast(0)
                val cmd = command(app, DAEMON, variant, launcher = "\$S") ?: return
                // Update 136: started from a subshell that exits at once ("( … & )"), and from a thread of its own. The
                // root service waited for the shell that started it to end, and that shell stayed until the helper
                // ended: this call never came back, so it kept this lock and every later volume change waited on it for
                // good (the first volume held, nothing after it changed). A start still stuck from before ends with the
                // pkill above (its shell's command line names the helper too).
                val launch = "S=\$(command -v setsid); ( $cmd daemon $duck${specs.distinct().joinToString(" ")} </dev/null >/dev/null 2>&1 & ); exit 0"
                // Only if no newer set of volumes came in meanwhile (that one starts its own).
                starter.execute { if (daemonSig == sig) runCatching { RootShell.exec(launch) } }
            }
            Method.EFFECT -> {
                if (RootShell.available) RootShell.exec("pkill -f '$DAEMON_MATCH'")
                // An app put back to 100 % must really get its player volume back too: the helper may have lowered it
                // before (the effects only cover what they're on; this used to leave it quiet for good).
                releasePlayers(app, resets)
                if (vols.isEmpty()) EffectLoop.stop() else EffectLoop.start(app, now = true)
            }
            else -> { EffectLoop.stop(); releasePlayers(app, resets) }
        }
    }

    /**
     * Update 136: after the effects are put on, looks in the audio server's own report whether they really sit on the
     * apps' sessions. None of them there (the firmware moved them to the output mix, session 0): [fxGlobal] is noted,
     * the effects come off and Automatic switches to the player volume. Needs the DUMP permission (granted through the
     * root service); without it nothing changes. Blocking; from the effect loop.
     */
    private fun checkRemap(c: Context) {
        if (fxGlobal(c) || !MixerEffects.active || !AudioReport.hasDump(c)) return
        val sessions = MixerEffects.sessions()
        if (sessions.isEmpty()) return
        val d = runCatching { AudioReport.dump("media.audio_flinger") }.getOrNull() ?: return
        // A report cut short says nothing.
        if (!d.contains("Effect Chains")) return
        if (sessions.any { d.contains("effects for session $it\n") || d.contains("effects for session $it ") }) return
        android.util.Log.w("AppMixer", "effects on sessions $sessions aren't on them in the audio server: the firmware moves them to all of the sound")
        meta(c).edit().putBoolean("fx_global", true).commit()
        MixerEffects.releaseAll()
        apply(c)
    }

    /** Update 136: starts of the helper process, one after another, never on [apply]'s lock. */
    private val starter = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "voltcap-mix-start").apply { isDaemon = true } }

    /** What [holdSessionless] last set: "uid:amp:players" entries. */
    @Volatile private var sessionlessSig = ""

    /**
     * Update 125: apps whose sound has no audio session (emulators with low-latency native audio, e.g. AetherSX2 /
     * ARMSX2) can't take the sound effect: with the root service there, their players' own volume is set instead
     * (once per change: a new player, another volume). Blocking; called from the effect loop.
     */
    private fun holdSessionless(c: Context, amp: Map<Int, Float>, players: List<MixerPlayer>) {
        if (!RootShell.available) return
        val specs = amp.filter { (uid, a) ->
            a < 0.999f && players.any { it.uid == uid } && players.filter { it.uid == uid }.all { it.session <= 0 }
        }.map { (uid, a) -> Triple(uid, a, players.count { it.uid == uid }) }
        val sig = specs.sortedBy { it.first }.joinToString(" ") { "${it.first}:${"%.4f".format(java.util.Locale.US, it.second)}:${it.third}" }
        if (sig == sessionlessSig) return
        sessionlessSig = sig
        if (specs.isEmpty()) return
        helper(c, "apply " + specs.joinToString(" ") { "${it.first}:${"%.4f".format(java.util.Locale.US, it.second)}" })
    }

    /** True when an app without audio sessions has its player volume held instead (see [holdSessionless]). */
    private fun sessionlessHeld(uid: Int): Boolean = RootShell.available && sessionlessSig.split(' ').any { it.startsWith("$uid:") }

    /** Puts these apps' player volumes back to 100 % once (the helper's `apply`), when the root service is there. Blocking. */
    private fun releasePlayers(c: Context, pkgs: Collection<String>) {
        if (!RootShell.available) return
        val specs = pkgs.distinct().mapNotNull { uidOf(c, it)?.let { uid -> "$uid:1.0000" } }
        if (specs.isEmpty()) return
        // 1.0 the plain way puts back both the player's own volume and the system-duck shaper.
        helper(c, "apply " + specs.joinToString(" "))
    }

    /**
     * Holds the volumes with [MixerEffects] while any app is turned down: looks for new sessions every few
     * seconds and at once when Android reports a player starting or stopping.
     */
    private object EffectLoop {
        @Volatile private var thread: Thread? = null
        private val lock = Object()
        @Volatile private var wake = false
        private var callback: AudioManager.AudioPlaybackCallback? = null

        fun start(c: Context, now: Boolean = false) {
            val app = c.applicationContext
            synchronized(lock) {
                if (thread?.isAlive == true) { if (now) poke(); return }
                thread = Thread({ run(app) }, "voltcap-mixer").apply { isDaemon = true; start() }
            }
            if (callback == null) runCatching {
                val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val cb = object : AudioManager.AudioPlaybackCallback() {
                    override fun onPlaybackConfigChanged(configs: MutableList<android.media.AudioPlaybackConfiguration>?) { poke() }
                }
                am.registerAudioPlaybackCallback(cb, android.os.Handler(android.os.Looper.getMainLooper()))
                callback = cb
            }
        }

        fun stop() {
            synchronized(lock) { thread?.interrupt(); thread = null }
            MixerEffects.releaseAll()
        }

        private fun poke() = synchronized(lock) { wake = true; lock.notifyAll() }

        private fun run(c: Context) {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val vols = effective(c)
                    val how = resolved(c)
                    if (vols.isEmpty() || (how != Method.EFFECT && how != Method.HELPER)) break
                    val amp = HashMap<Int, Float>()
                    vols.forEach { (pkg, v) -> uidOf(c, pkg)?.let { amp[it] = amplitude(v) } }
                    // With the helper holding the volumes only the players it has no handle on need the effect.
                    val found = discover(c, fast = how == Method.EFFECT)
                    // A look that failed (a timeout, a helper hiccup) is not "nobody is playing": the effects stay as they were.
                    if (found.source != null) {
                        val players = found.players.let { all -> if (how == Method.HELPER) all.filter { !it.controllable } else all }
                        // (Update 136: no effect at all where the firmware would put it on all of the sound.)
                        if (how == Method.HELPER && fxGlobal(c)) MixerEffects.releaseAll() else MixerEffects.sync(amp, players)
                        if (how == Method.EFFECT) holdSessionless(c, amp, found.players)
                        checkRemap(c)
                    }
                    // The helper process can die (the firmware cleans up its shell, a memory squeeze): bring it back
                    // instead of leaving the volumes unheld until the next slider move. Not within seconds of a start.
                    if (how == Method.HELPER && android.os.SystemClock.uptimeMillis() - daemonStartedAt > 8_000L && !daemonRunning()) {
                        daemonSig = ""
                        apply(c)
                    }
                    synchronized(lock) {
                        if (!wake) lock.wait(if (how == Method.HELPER) 5_000L else 4_000L)
                        wake = false
                    }
                    // Let a burst of player changes settle into one look.
                    Thread.sleep(250L)
                }
            } catch (_: InterruptedException) {
            } finally {
                synchronized(lock) { if (thread === Thread.currentThread()) thread = null }
                val how = resolved(c)
                if (effective(c).isEmpty() || (how != Method.EFFECT && how != Method.HELPER)) MixerEffects.releaseAll()
            }
        }
    }

    private fun label(c: Context, pkg: String): String = runCatching {
        val pm = c.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun uidOf(c: Context, pkg: String): Int? = runCatching { c.packageManager.getApplicationInfo(pkg, 0).uid }.getOrNull()

    private fun kind(usages: Set<Int>): String? = when {
        14 in usages -> "Game"            // USAGE_GAME
        1 in usages -> "Media"            // USAGE_MEDIA
        usages.any { it in 2..10 } -> "Calls & alerts"
        usages.isEmpty() -> null
        else -> "Sound"
    }
}
