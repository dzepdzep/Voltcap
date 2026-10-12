package com.shelf.launcher.data

import android.content.Context

/*
 * Which screen a plain app (not a library game) opens on.
 *
 * Games keep using the Screen layouts rules (game › collection › console ›
 * emulator › default). Apps have a simpler ladder, most specific first:
 *   this app › its category › the global default.
 * "Where launched" means: the screen you tapped it on.
 */

enum class AppDisplayTarget(val label: String) {
    WHERE_LAUNCHED("The screen I launched it from"),
    MAIN("Main screen"),
    SECOND("Second screen")
}

/** Where the winning choice came from — shown as "set for this app" etc. */
enum class AppDisplaySource { APP, CATEGORY, GLOBAL }

/** Where in Voltcap the launch came from. */
enum class AppLaunchOrigin { MAIN, SECOND }

/** Category keys are free-form strings so later UI can add more (e.g. a console). */
object AppDisplayCategories {
    const val EMULATOR = "emulator"
}

class AppDisplayStore(context: Context) {
    private val prefs = context.getSharedPreferences("app_display", Context.MODE_PRIVATE)

    private fun read(key: String): AppDisplayTarget? =
        prefs.getString(key, null)?.let { name -> AppDisplayTarget.values().firstOrNull { it.name == name } }

    private fun write(key: String, target: AppDisplayTarget?) {
        prefs.edit().apply { if (target == null) remove(key) else putString(key, target.name) }.apply()
    }

    fun appTarget(pkg: String): AppDisplayTarget? = read("app:$pkg")
    fun setAppTarget(pkg: String, target: AppDisplayTarget?) = write("app:$pkg", target)

    fun categoryTarget(category: String): AppDisplayTarget? = read("cat:$category")
    fun setCategoryTarget(category: String, target: AppDisplayTarget?) = write("cat:$category", target)

    fun globalTarget(): AppDisplayTarget = read("global") ?: AppDisplayTarget.WHERE_LAUNCHED
    fun setGlobalTarget(target: AppDisplayTarget) = write("global", target)

    /** This app's own choice › its category's › the global default. */
    fun resolve(pkg: String, category: String?): Pair<AppDisplayTarget, AppDisplaySource> {
        appTarget(pkg)?.let { return it to AppDisplaySource.APP }
        if (category != null) categoryTarget(category)?.let { return it to AppDisplaySource.CATEGORY }
        return globalTarget() to AppDisplaySource.GLOBAL
    }
}
