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
 */
object RootShell {
    private const val TAG = "RootShell"

    private val pserver: IBinder? by lazy {
        runCatching {
            val sm = Class.forName("android.os.ServiceManager")
            sm.getDeclaredMethod("getService", String::class.java).invoke(null, "PServerBinder") as? IBinder
        }.getOrNull()
    }

    val available: Boolean get() = pserver != null

    /** Runs [cmd] and returns its trimmed output ("" for none), or null when it couldn't run. */
    fun exec(cmd: String): String? {
        val b = pserver ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeStringArray(arrayOf(cmd, "1"))
            b.transact(0, data, reply, 0)
            reply.createByteArray()?.toString(Charsets.UTF_8)?.trim()?.takeUnless { it == "null" } ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "PServer command failed", e)
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /** Whether [packageName] has a live process: true / false, or null when it can't be told. */
    fun isRunning(packageName: String): Boolean? {
        if (!packageName.matches(Regex("[A-Za-z0-9._]+"))) return null
        val out = exec("pidof $packageName") ?: return null
        return out.split(Regex("\\s+")).any { it.toIntOrNull() != null }
    }
}
