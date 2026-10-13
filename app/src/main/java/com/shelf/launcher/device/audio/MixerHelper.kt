package com.shelf.launcher.device.audio

/**
 * The privileged half of the volume mixer. It is not part of the app's normal running: Voltcap starts it
 * through the handheld's root service ([com.shelf.launcher.device.RootShell], AYN / Retroid PServer) as
 * its own little process —
 *
 *     CLASSPATH=<Voltcap's apk> app_process /system/bin --nice-name=voltcap_mixd com.shelf.launcher.device.audio.MixerHelper …
 *
 * (or with -Djava.class.path, whichever the firmware's shell accepts: [AppMixer] remembers the one that
 * answered) — because Android only gives per-player volume to privileged callers: the audio service's list
 * of active players (who plays what, and a handle to each player) and each player's own volume are system
 * APIs. The same way MIUI's and the open-source AppMixer / NoMixer do it, it sets the volume of every
 * player that belongs to an app (the player's own volume, on top of the media volume).
 *
 * Every answer is one line starting with [MARK], so it can be picked out of whatever else the shell prints
 * (runtime warnings) and survives root services that only hand back the first line.
 *
 * Modes:
 *  - `ping`: "VOLTCAP_MIX PONG uid=<uid> sdk=<sdk>" — the helper starts at all.
 *  - `list`: "VOLTCAP_MIX OK;total;hidden;uid,usage,state,session;…" ("VOLTCAP_MIX ERR:<why>" when the audio
 *    service couldn't be asked).
 *  - `apply uid:volume …`: sets those volumes once and answers "VOLTCAP_MIX SET;<players set>;<failed>".
 *  - `daemon uid:volume …`: keeps those volumes on every player of those apps, new ones included (games and
 *    players re-create theirs), checking four times a second. A volume of 1.0 is applied once and dropped (that
 *    app goes back to normal). Runs until Voltcap stops it (pkill) or the device restarts.
 *  - `hold uid:volume …`, update 168: sets those volumes once on every player now (as `apply`) and answers with the
 *    list (as `list`) — one process for both. Used where the firmware ends the background `daemon` right after its
 *    start (the Retroid Pocket Classic's root service): Voltcap runs it each time Android reports a player starting.
 *  - `--duck` before the volumes (`apply` / `daemon`), update 121: the second way, the one Android itself
 *    uses to duck an app under a notification — a constant VolumeShaper (fixed id [SHAPER_ID]) on each
 *    player, which the app's own volume calls can't undo; the player's own volume goes back to 1.0 (the two
 *    would multiply). Without it a volume of 1.0 also clears that shaper, so switching ways never leaves an
 *    app quiet.
 *
 * Nothing here may touch the app's Context or other app classes: there is no app in this process.
 * Kept whole by R8 (app/src/main/keepRules/rules.keep): app_process calls [main] by name.
 */
object MixerHelper {
    const val MARK = "VOLTCAP_MIX"
    /** The id of Voltcap's volume shaper on a player (re-applying the same id replaces it, never stacks). */
    private const val SHAPER_ID = 0x564F4C

    @JvmStatic
    fun main(args: Array<String>) {
        exemptHiddenApi()
        when (args.firstOrNull()) {
            "ping" -> say("PONG uid=${android.os.Process.myUid()} sdk=${android.os.Build.VERSION.SDK_INT}")
            "list" -> runCatching {
                val all = configs()
                val list = players(all)
                val hidden = all.size - list.size
                say((listOf("OK", "${all.size}", "$hidden") + list.map { p -> "${p.uid},${p.usage},${p.state},${p.session},${if (p.proxy) 1 else 0}" }).joinToString(";"))
            }.onFailure { say("ERR:" + why(it)) }
            "apply" -> runCatching {
                val duck = "--duck" in args
                val wanted = parse(args.drop(1))
                var ok = 0
                var failed = 0
                players().forEach { p -> wanted[p.uid]?.let { v -> if (hold(p, v, duck)) ok++ else failed++ } }
                say("SET;$ok;$failed")
            }.onFailure { say("ERR:" + why(it)) }
            "hold" -> runCatching {
                val duck = "--duck" in args
                val wanted = parse(args.drop(1))
                val all = configs()
                val list = players(all)
                list.forEach { p -> wanted[p.uid]?.let { v -> hold(p, v, duck) } }
                val hidden = all.size - list.size
                say((listOf("OK", "${all.size}", "$hidden") + list.map { p -> "${p.uid},${p.usage},${p.state},${p.session},${if (p.proxy) 1 else 0}" }).joinToString(";"))
            }.onFailure { say("ERR:" + why(it)) }
            "daemon" -> daemon(args.drop(1), duck = "--duck" in args)
            else -> System.err.println("mixer: ping | list | apply uid:vol … | daemon uid:vol …")
        }
    }

    private fun say(line: String) {
        println("$MARK $line")
        System.out.flush()
    }

    /** The reason for an answer line: no spaces or separators, so the line stays one token. */
    private fun why(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return "${root.javaClass.simpleName}:${root.message.orEmpty()}".replace(Regex("[\\s;]+"), "_").take(120)
    }

    private class Player(val uid: Int, val usage: Int, val state: Int, val session: Int, val config: Any, val proxy: Boolean = true)

    private fun exemptHiddenApi() {
        runCatching {
            val vmRuntime = Class.forName("dalvik.system.VMRuntime")
            val runtime = vmRuntime.getDeclaredMethod("getRuntime").invoke(null)
            vmRuntime.getDeclaredMethod("setHiddenApiExemptions", Array<String>::class.java).invoke(runtime, arrayOf("L"))
        }
    }

    private val audio: Any by lazy {
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "audio")
            ?: error("no audio service")
        Class.forName("android.media.IAudioService\$Stub").getMethod("asInterface", android.os.IBinder::class.java).invoke(null, binder)
            ?: error("no audio interface")
    }

    /** Every active player with its app's uid (only privileged callers get real uids and player handles). */
    private fun configs(): List<Any> =
        (audio.javaClass.getMethod("getActivePlaybackConfigurations").invoke(audio) as? List<*>)?.filterNotNull() ?: emptyList()

    private fun players(list: List<Any> = configs()): List<Player> {
        return list.mapNotNull { c ->
            val cls = c.javaClass
            val uid = runCatching { cls.getMethod("getClientUid").invoke(c) as Int }.getOrNull() ?: return@mapNotNull null
            val state = runCatching { cls.getMethod("getPlayerState").invoke(c) as Int }.getOrDefault(-1)
            val usage = runCatching {
                (cls.getMethod("getAudioAttributes").invoke(c) as android.media.AudioAttributes).usage
            }.getOrDefault(0)
            val session = runCatching { cls.getMethod("getSessionId").invoke(c) as Int }.getOrDefault(0)
            // Native audio streams (AAudio / OpenSL ES) have no player handle: their volume can't be set here.
            val proxy = runCatching { cls.getMethod("getPlayerProxy").invoke(c) != null }.getOrDefault(false)
            if (uid < 0) null else Player(uid, usage, state, session, c, proxy)
        }
    }

    /** Sets one player's own volume (0..1). */
    private fun setVolume(p: Player, volume: Float): Boolean = runCatching {
        val proxy = p.config.javaClass.getMethod("getPlayerProxy").invoke(p.config) ?: return false
        proxy.javaClass.getMethod("setVolume", Float::class.javaPrimitiveType).invoke(proxy, volume)
        true
    }.getOrDefault(false)

    /** A constant volume shaper at [volume] with Voltcap's id (null where the id can't be set: it would stack). */
    private fun shaper(volume: Float): android.media.VolumeShaper.Configuration? = runCatching {
        val b = android.media.VolumeShaper.Configuration.Builder()
            .setCurve(floatArrayOf(0f, 1f), floatArrayOf(volume, volume))
            .setInterpolatorType(android.media.VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
            .setDuration(20L)
        // Hidden setter (Android's own ducking uses it): without a fixed id every apply would add another shaper.
        b.javaClass.getMethod("setId", Int::class.javaPrimitiveType).invoke(b, SHAPER_ID)
        b.build()
    }.getOrNull()

    /** Puts Voltcap's shaper on one player at [volume] (1.0 = no change, which also replaces an older one). */
    private fun setShaper(p: Player, volume: Float): Boolean = runCatching {
        val conf = shaper(volume) ?: return false
        val proxy = p.config.javaClass.getMethod("getPlayerProxy").invoke(p.config) ?: return false
        proxy.javaClass.getMethod(
            "applyVolumeShaper", android.media.VolumeShaper.Configuration::class.java, android.media.VolumeShaper.Operation::class.java
        ).invoke(proxy, conf, android.media.VolumeShaper.Operation.PLAY)
        true
    }.getOrDefault(false)

    /**
     * Holds [volume] on one player the chosen way. [duck]: the shaper carries it and the player's own volume is
     * 1.0; otherwise the player's own volume, and the shaper is put back to 1.0 when the app goes back to normal
     * (a switch from the other way). A player the shaper can't reach falls back to its own volume.
     */
    private fun hold(p: Player, volume: Float, duck: Boolean): Boolean = when {
        duck && setShaper(p, volume) -> { setVolume(p, 1f); true }
        else -> {
            val ok = setVolume(p, volume)
            if (volume >= 0.999f) setShaper(p, 1f)
            ok
        }
    }

    /** A player's id for this run (to put the shaper on each new player once), or null. */
    private fun idOf(p: Player): Int? = runCatching { p.config.javaClass.getMethod("getPlayerInterfaceId").invoke(p.config) as Int }.getOrNull()

    private fun parse(specs: List<String>): HashMap<Int, Float> {
        val wanted = HashMap<Int, Float>()
        specs.filter { !it.startsWith("--") }.forEach { s ->
            val uid = s.substringBefore(':').toIntOrNull() ?: return@forEach
            val vol = s.substringAfter(':').toFloatOrNull()?.coerceIn(0f, 1f) ?: return@forEach
            wanted[uid] = vol
        }
        return wanted
    }

    private fun daemon(specs: List<String>, duck: Boolean) {
        val wanted = parse(specs)
        // Volume 1.0: put that app back to normal once, then leave it alone.
        val resets = wanted.filterValues { it >= 0.999f }.keys
        var first = true
        var failures = 0
        // The duck way: each player gets its shaper once (it stays on the player), again only when its volume changes.
        val shaped = HashMap<Int, Float>()
        while (true) {
            val players = runCatching { players() }.onSuccess { failures = 0 }.onFailure { failures++ }.getOrDefault(emptyList())
            // The audio service can't be asked here (firmware too different): don't keep a useless process.
            if (failures >= 10) return
            players.forEach { p ->
                val v = wanted[p.uid] ?: return@forEach
                if (v >= 0.999f && !first) return@forEach
                if (duck) {
                    val id = idOf(p)
                    if (id == null || shaped[id] != v) { if (hold(p, v, duck = true) && id != null) shaped[id] = v }
                } else hold(p, v, duck = false)
            }
            if (first) { resets.forEach { wanted.remove(it) }; first = false }
            // Nothing left to hold: done.
            if (wanted.isEmpty()) return
            // A quarter of a second: a game's new player is loud for at most that long (a second let the start through).
            Thread.sleep(250L)
        }
    }
}
