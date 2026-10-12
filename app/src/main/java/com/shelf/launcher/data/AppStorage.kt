package com.shelf.launcher.data

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File

/**
 * Where Voltcap keeps its own files: inside Android's private app folder (default), or in a
 * "Voltcap" folder at the top of the internal storage that you can see, copy and back up.
 *
 * In the visible folder:
 *  - Art, Guides, Music (your own background track), Sounds (your own UI sounds),
 *  - Data (ROM library list, RetroAchievements data and game lists),
 *  - Crash reports, Settings snapshots, and Settings/voltcap-settings.json — a full copy of the
 *    settings (no sign-ins) refreshed whenever Voltcap goes to the background,
 *  - Sync backups / Sync conflicts (Drive).
 * Android itself only lets an app keep its settings database and sign-ins in its private folder;
 * those stay there (the settings file above is the copy you can carry around).
 *
 * Switching either way copies what's missing or newer from the other place the first time each
 * folder is used, so nothing is lost. The visible folder needs "All files access"; without it
 * Voltcap quietly stays in its private folder.
 */
object AppStorage {
    private const val PREFS = "app_storage"
    private const val KEY = "root_folder"

    fun useRootFolder(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setUseRootFolder(context: Context, on: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        synced.clear()
    }

    fun canUseRootFolder(): Boolean = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    fun active(context: Context): Boolean = useRootFolder(context) && canUseRootFolder()

    /** /storage/emulated/0/Voltcap */
    fun rootFolder(): File = File(Environment.getExternalStorageDirectory(), "Voltcap")

    private val synced = java.util.Collections.synchronizedSet(HashSet<String>())

    /** The folder for [sub] ("Art", "Guides"…): the visible Voltcap folder when chosen and allowed, else [fallback]. */
    fun dir(context: Context, sub: String, fallback: File, migrate: Boolean = true): File {
        if (active(context)) {
            val f = File(rootFolder(), sub)
            if (f.isDirectory || f.mkdirs()) {
                if (migrate) bringOver(fallback, f, "d:$sub")
                return f
            }
        }
        if (migrate && !useRootFolder(context)) bringOver(File(rootFolder(), sub), fallback, "b:$sub")
        return fallback
    }

    /** One file ([name] in [sub]) — same rules as [dir]. */
    fun file(context: Context, sub: String, name: String, fallback: File): File {
        val visible = File(File(rootFolder(), sub), name)
        if (active(context)) {
            val parent = visible.parentFile
            if (parent != null && (parent.isDirectory || parent.mkdirs())) {
                copyIfNewer(fallback, visible, "f:$sub/$name")
                return visible
            }
        }
        if (!useRootFolder(context)) copyIfNewer(visible, fallback, "fb:$sub/$name")
        return fallback
    }

    private fun copyIfNewer(src: File, dst: File, key: String) {
        if (!synced.add(key)) return
        runCatching {
            if (src.isFile && (!dst.exists() || src.lastModified() > dst.lastModified() + 1000)) {
                dst.parentFile?.mkdirs()
                src.copyTo(dst, overwrite = true)
                dst.setLastModified(src.lastModified())
            }
        }
    }

    /** Copies files that are missing or newer from [from] into [to], once per run (best effort). */
    private fun bringOver(from: File, to: File, key: String) {
        if (!synced.add(key)) return
        runCatching {
            if (!from.isDirectory) return
            from.walkTopDown().filter { it.isFile }.take(20_000).forEach { src ->
                val rel = src.relativeTo(from).path
                val dst = File(to, rel)
                if (!dst.exists() || src.lastModified() > dst.lastModified() + 1000) {
                    dst.parentFile?.mkdirs()
                    src.copyTo(dst, overwrite = true)
                    dst.setLastModified(src.lastModified())
                }
            }
        }
    }

    /** Writes Settings/voltcap-settings.json in the visible folder (all settings, no sign-ins). */
    fun mirrorSettings(context: Context) {
        if (!active(context)) return
        runCatching {
            val dir = File(rootFolder(), "Settings").apply { mkdirs() }
            val tmp = File(dir, "voltcap-settings.json.tmp")
            Backup.exportTo(context, tmp, includeGameData = true, includeDeviceSetup = true)
            tmp.renameTo(File(dir, "voltcap-settings.json"))
        }
    }
}
