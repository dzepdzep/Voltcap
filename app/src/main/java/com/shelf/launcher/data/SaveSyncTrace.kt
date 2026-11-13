package com.shelf.launcher.data

/**
 * Mid-game sync, update 529: one log line per step of a save's way to Drive - found (with how old the file's own write was),
 * settled, upload started, uploaded and verified - so the delay from the emulator's write to the verified upload can be
 * measured on a handheld (`adb logcat -s VoltcapSync`). Only times and the game's key; never file contents.
 */
internal object SaveSyncTrace {
    private val firstWrite = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** [fileModified]: the newest changed file's own date (0 = not known at this step). */
    fun mark(key: String, step: String, fileModified: Long) {
        val now = System.currentTimeMillis()
        if (fileModified > 0L && step == "detected") firstWrite[key] = fileModified
        val since = firstWrite[key]?.let { now - it }
        runCatching { android.util.Log.i("VoltcapSync", "$step key=${key.hashCode()} t=$now" + (since?.let { " sinceWriteMs=$it" } ?: "")) }
        if (step == "verified") firstWrite.remove(key)
    }
}
