package com.shelf.launcher.data

import android.content.Context

/**
 * What never counts as play in Stats (playtime, streaks, top games): Voltcap itself, every other
 * home-screen launcher, and system apps (unless it's a known emulator or marked as a game by hand).
 * Only package-name keys are checked; ROM paths and Steam ids always count.
 */
object StatsExclusions {
    @Volatile private var launchers: Set<String>? = null
    private val systemCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun excluded(context: Context, key: String): Boolean {
        if (key.contains('/') || key.contains(':') || !key.contains('.')) return false
        val own = context.packageName
        if (key == own || key.startsWith("$own.")) return true
        if (key in launchers(context)) return true
        return systemCache.getOrPut(key) { isPlainSystemApp(context, key) }
    }

    private fun launchers(context: Context): Set<String> = launchers ?: runCatching {
        val home = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        context.packageManager.queryIntentActivities(home, 0).mapNotNull { it.activityInfo?.packageName }.toSet()
    }.getOrDefault(emptySet()).also { launchers = it }

    private fun isPlainSystemApp(context: Context, pkg: String): Boolean = runCatching {
        if (EmulatorRegistry.displayNameFor(pkg) != null || CoreRegistry.isRetroArch(pkg)) return@runCatching false
        if (pkg in LibraryAppsStore(context).load().forcedGames) return@runCatching false
        @Suppress("DEPRECATION")
        val info = context.packageManager.getApplicationInfo(pkg, 0)
        val system = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        @Suppress("DEPRECATION")
        val flagGame = (info.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0
        val isGame = flagGame || (android.os.Build.VERSION.SDK_INT >= 26 && info.category == android.content.pm.ApplicationInfo.CATEGORY_GAME)
        system && !isGame
    }.getOrDefault(false)
}
