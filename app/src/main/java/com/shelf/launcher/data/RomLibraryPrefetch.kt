package com.shelf.launcher.data

import android.content.Context
import java.io.File
import java.util.concurrent.FutureTask

/**
 * Update 188: the saved list of your games (the last scan's, "rom_library.json") is read the moment Voltcap starts, on
 * its own thread, while the screen and the rest get ready — it used to start only once the home screen's view model
 * had loaded everything else, and the categories showed "0 games" and empty covers for two seconds or so after a start.
 * The view model takes the result ([take]); the time it took goes to the log ("Voltcap library").
 */
object RomLibraryPrefetch {
    private var task: FutureTask<List<Game>>? = null
    private var started = false

    /** Where the list is kept (as the view model has it). */
    fun fileOf(context: Context): File {
        val app = context.applicationContext
        return AppStorage.file(app, "Data", "rom_library.json", File(app.filesDir, "rom_library.json"))
    }

    /** Starts reading it (once per process). */
    fun start(context: Context) {
        val app = context.applicationContext
        synchronized(this) {
            if (started) return
            started = true
            val t = FutureTask { read(fileOf(app)) }
            task = t
            Thread(t, "voltcap-library").apply { isDaemon = true }.start()
        }
    }

    /** The list read at start-up (waiting for it if it's still being read), or read now when it wasn't started. */
    fun take(file: () -> File): List<Game> {
        val t = synchronized(this) { task.also { task = null } }
        if (t != null) runCatching { return t.get() }
        return read(file())
    }

    @Suppress("SENSELESS_COMPARISON")
    fun read(file: File): List<Game> {
        val t0 = android.os.SystemClock.elapsedRealtime()
        if (!file.exists()) return emptyList()
        val type = object : com.google.gson.reflect.TypeToken<List<Game>>() {}.type
        val list = runCatching {
            com.google.gson.Gson().fromJson<List<Game>>(file.readText(), type)
                // Gson can leave fields null in a damaged file.
                ?.filter { it.name != null && it.platform != null && it.sourceType != null }
                // Update 408: Android .trashed-* placeholders indexed by an older scan are invalid records (files untouched).
                ?.filterNot { RomScanner.isTrashedPath(it.romPath) }
        }.getOrNull().orEmpty()
        runCatching { android.util.Log.i("Voltcap library", "${list.size} games read in ${android.os.SystemClock.elapsedRealtime() - t0} ms (${file.length() / 1024} KB)") }
        return list
    }
}
