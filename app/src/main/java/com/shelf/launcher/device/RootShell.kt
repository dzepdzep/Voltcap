package com.shelf.launcher.device

import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * Commands as root through the vendor's own system service, where the firmware ships one.
 * Today that's AYN's "PServerBinder" (Odin 2 family, Thor): the same service AYN's settings use
 * for fan/performance modes and Voltcap already uses for the stick lights. Nothing else is
 * tried — no su, no exploits. Everything here is a no-op returning null when it isn't there.
 *
 * Calls block (binder round trip, usually a few ms): call them off the main thread.
 *
 * Update 201: two limits of AYN's service, read from its binary (/system/bin/pservice, the same build on the
 * Odin 2 Portal and the Thor) and seen on both handhelds:
 *  - each argument is copied into a 256-byte buffer without a length check and the next argument ("1") is
 *    copied right after it, so a command longer than 255 characters is cut there and gets a stray "1"
 *    appended. Voltcap's clock writes were one long "echo … ; echo …" chain: only the first CPU cluster's
 *    writes ran, and every GPU cap, msm_performance cap and boost switch after them was silently dropped.
 *    Long chains go through [execAll], which splits them into commands that fit.
 *  - the reply is the command's FIRST LINE only (one fgets): `ls` of several paths, a loop printing one line
 *    per item or `cat a ; cat b` came back with the first entry only (the tuner saw one CPU cluster out of
 *    three and no CPU temperature at all). Commands with several lines of output go through [execJoined].
 */
object RootShell {
    private const val TAG = "RootShell"

    /** Update 201: the longest command the service runs intact (see above). */
    const val MAX_COMMAND = 250

    /*
     * Update 360 (Thor, 27 Oct: charged to 100 % with the limit on). It was looked up once per run ("by lazy"): when
     * Android's backup restarted Voltcap at 20:09 the first look-up came back empty, that empty answer stayed for the whole
     * run, and the charge limit took the Thor for a handheld without a charge switch. Now an empty or dead answer is looked
     * up again (at most every 5 s), and a call that hits a dead service looks it up and tries once more.
     */
    @Volatile private var cached: IBinder? = null
    @Volatile private var lastLookup = 0L

    private fun lookup(): IBinder? = runCatching {
        val sm = Class.forName("android.os.ServiceManager")
        sm.getDeclaredMethod("getService", String::class.java).invoke(null, "PServerBinder") as? IBinder
    }.getOrNull()

    private val pserver: IBinder?
        get() {
            cached?.let { if (it.isBinderAlive) return it }
            val now = android.os.SystemClock.elapsedRealtime()
            if (lastLookup != 0L && now - lastLookup < 5_000L) return cached?.takeIf { it.isBinderAlive }
            lastLookup = now
            cached = lookup()
            return cached
        }

    val available: Boolean get() = pserver != null

    /**
     * Runs [cmd] and returns its trimmed output ("" for none), or null when it couldn't run.
     * (Update 360: one call at a time — AYN's service crashed on the Thor, 27 Oct 20:09, inside its request handling,
     * while Voltcap's charge limit, tuner and stick lights could all be calling it at once.)
     */
    @Synchronized
    fun exec(cmd: String): String? {
        val first = run1(cmd)
        if (first !== DEAD) return first as String?
        // (Update 360: the service went away — e.g. restarted — look it up again and try once more.)
        cached = null; lastLookup = 0L
        return run1(cmd).let { if (it === DEAD) null else it as String? }
    }

    private val DEAD = Any()

    private fun run1(cmd: String): Any? {
        val b = pserver ?: return null
        // Update 201: the service would cut it (see the class comment); said in the log so it doesn't go unnoticed again.
        if (cmd.length > MAX_COMMAND) Log.w(TAG, "Command of ${cmd.length} chars is cut by the root service: ${cmd.take(60)}…")
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeStringArray(arrayOf(cmd, "1"))
            b.transact(0, data, reply, 0)
            reply.createByteArray()?.toString(Charsets.UTF_8)?.trim()?.takeUnless { it == "null" } ?: ""
        } catch (e: android.os.DeadObjectException) {
            Log.w(TAG, "PServer went away; looking it up again")
            DEAD
        } catch (e: Exception) {
            Log.w(TAG, "PServer command failed", e)
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /**
     * Update 201: runs [cmds] (each a complete shell command) packed into as few calls as fit the service's
     * command limit, joined by " ; ". True when every call ran. A single command over the limit still runs on its
     * own (and is logged): callers keep their commands short.
     */
    fun execAll(cmds: List<String>): Boolean {
        if (cmds.isEmpty()) return true
        if (pserver == null) return false
        var ok = true
        val batch = StringBuilder()
        fun flush() {
            if (batch.isEmpty()) return
            if (exec(batch.toString()) == null) ok = false
            batch.setLength(0)
        }
        cmds.forEach { c ->
            if (batch.isNotEmpty() && batch.length + 3 + c.length > MAX_COMMAND) flush()
            if (batch.isNotEmpty()) batch.append(" ; ")
            batch.append(c)
        }
        flush()
        return ok
    }

    /**
     * Update 201: runs [cmd] with its output joined into one line (lines separated by [sep]), since the service only
     * returns the first line. Null when it couldn't run. [cmd] plus the join must fit [MAX_COMMAND].
     */
    fun execJoined(cmd: String, sep: Char = ' '): String? =
        exec("{ $cmd ; } 2>/dev/null | tr '\\n' '$sep'")

    /** Whether [packageName] has a live process: true / false, or null when it can't be told. */
    fun isRunning(packageName: String): Boolean? {
        if (!packageName.matches(Regex("[A-Za-z0-9._]+"))) return null
        val out = exec("pidof $packageName") ?: return null
        return out.split(Regex("\\s+")).any { it.toIntOrNull() != null }
    }
}
