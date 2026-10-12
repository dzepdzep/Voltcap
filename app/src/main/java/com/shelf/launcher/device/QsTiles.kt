package com.shelf.launcher.device

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build

/**
 * Voltcap's tiles for Android's Quick Settings (the pull-down menu): which there are, whether each one is in
 * the menu, and adding them —
 *  - Android 13+: Android's own "Add tile?" prompt, one tile at a time;
 *  - with the handheld's root service (AYN / Retroid): all at once, straight into the menu;
 *  - otherwise the steps to do it by hand (Edit › drag them up).
 * Whether a tile is in the menu comes from the tiles themselves (Android tells each one when it's added or
 * removed) and, with the root service, from the menu's own list.
 */
object QsTiles {
    private const val PREFS = "qs_tiles"

    data class Tile(val id: String, val className: String, val label: String, val what: String, val iconRes: Int)

    val all: List<Tile> = listOf(
        Tile("mixer", "com.shelf.launcher.device.audio.MixerTileService", "Volume mixer",
            "Each app's own volume, in a small card over the game.", com.shelf.launcher.R.drawable.ic_tile_mixer),
        Tile("overlay", "com.shelf.launcher.device.tune.OverlayTileService", "Voltcap overlay",
            "Tap: off → stats → music → trophies → all. Hold: the in-game panel.", com.shelf.launcher.R.drawable.ic_tile_overlay),
        Tile("power", "com.shelf.launcher.device.tune.TunerTileService", "Voltcap power / TDP",
            "Tap steps the power tier or AutoTDP, mid-game too.", com.shelf.launcher.R.drawable.ic_tile_power),
        Tile("underclock", "com.shelf.launcher.device.tune.UnderclockTileService", "Voltcap underclock",
            "Off → light → medium → heavy: cooler, longer battery.", com.shelf.launcher.R.drawable.ic_tile_underclock)
    )

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Called by the tiles: Android added / removed one from the menu. */
    fun mark(c: Context, id: String, added: Boolean) {
        prefs(c).edit().putBoolean(id, added).apply()
    }

    fun component(c: Context, t: Tile) = ComponentName(c.packageName, t.className)

    /** The menu's own tile list, read through the root service (null without it). Blocking. */
    private fun systemList(): String? =
        if (RootShell.available) RootShell.exec("settings get secure sysui_qs_tiles")?.trim()?.takeIf { it.isNotEmpty() && it != "null" } else null

    /** Each tile → in the menu (true / false), or null when Voltcap can't tell yet. Blocking (root service). */
    fun states(c: Context): Map<String, Boolean?> {
        val list = systemList()
        val p = prefs(c)
        return all.associate { t ->
            val simple = t.className.substringAfterLast('.')
            t.id to when {
                list != null -> list.split(',').any { it.startsWith("custom(") && it.contains(c.packageName) && it.contains(simple) }
                p.contains(t.id) -> p.getBoolean(t.id, false)
                else -> null
            }
        }
    }

    /** Android's own "Add tile?" prompt is there (Android 13+). */
    val canAsk: Boolean get() = Build.VERSION.SDK_INT >= 33

    /**
     * Asks Android to add [t] (its "Add tile to Quick Settings?" prompt). [onResult] gets true (added or
     * already there), false (said no) or null (Android couldn't ask). Main thread; Voltcap must be in front.
     */
    fun request(activity: Activity, t: Tile, onResult: (Boolean?) -> Unit) {
        if (Build.VERSION.SDK_INT < 33) { onResult(null); return }
        val sbm = activity.getSystemService(StatusBarManager::class.java) ?: run { onResult(null); return }
        runCatching {
            sbm.requestAddTileService(
                component(activity, t), t.label, Icon.createWithResource(activity, t.iconRes),
                activity.mainExecutor
            ) { code ->
                val r = when (code) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED,
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> true
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> false
                    else -> null
                }
                if (r != null) mark(activity, t.id, r)
                onResult(r)
            }
        }.onFailure { onResult(null) }
    }

    /**
     * Adds every missing Voltcap tile to the end of the menu through the root service (no prompts). True when
     * the menu's list now has them all. Blocking.
     */
    fun addAllWithRoot(c: Context): Boolean {
        val list = systemList() ?: return false
        // Only plain tile specs: anything unexpected in the list and it's left alone.
        if (!list.matches(Regex("[A-Za-z0-9_.,/()\\-]+"))) return false
        val have = list.split(',').filter { it.isNotBlank() }
        val missing = all.filter { t -> have.none { it.startsWith("custom(") && it.contains(c.packageName) && it.contains(t.className.substringAfterLast('.')) } }
        if (missing.isEmpty()) return true
        val spec = (have + missing.map { "custom(${component(c, it).flattenToShortString()})" }).joinToString(",")
        RootShell.exec("settings put secure sysui_qs_tiles '$spec'")
        val ok = states(c).values.all { it == true }
        if (ok) all.forEach { mark(c, it.id, true) }
        return ok
    }
}
