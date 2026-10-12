package com.shelf.launcher.data

import android.content.Context

/**
 * How to hand a Steam AppID off to GameNative (github.com/utkarshdalal/GameNative
 * — "Native PC gaming with Steam, Epic, GOG and Amazon integrations on
 * Android"). Verified against GameNative's own documented launch intent
 * (its `app.gamenative.LAUNCH_GAME` action, described as "already-exported"
 * in the project's own PR discussion, with an integer `app_id` extra):
 *
 *   am start -n app.gamenative/app.gamenative.MainActivity \
 *       -a app.gamenative.LAUNCH_GAME -ei app_id <steamAppId>
 *
 * The defaults below match that. They're editable because a fork (there's
 * at least one — GameNative CE) or a future rename could use a different
 * package name, and there's no way to guess that from here.
 */
data class GameNativeConfig(
    val packageName: String = DEFAULT_PACKAGE,
    val activityClassName: String = DEFAULT_ACTIVITY,
    val action: String = DEFAULT_ACTION,
    val appIdExtraKey: String = DEFAULT_APP_ID_EXTRA_KEY
) {
    companion object {
        const val DEFAULT_PACKAGE = "app.gamenative"
        const val DEFAULT_ACTIVITY = "app.gamenative.MainActivity"
        const val DEFAULT_ACTION = "app.gamenative.LAUNCH_GAME"
        const val DEFAULT_APP_ID_EXTRA_KEY = "app_id"
    }
}

class GameNativeSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("gamenative_settings", Context.MODE_PRIVATE)

    fun load(): GameNativeConfig = GameNativeConfig(
        packageName = prefs.getString(KEY_PACKAGE, GameNativeConfig.DEFAULT_PACKAGE)!!,
        activityClassName = prefs.getString(KEY_ACTIVITY, GameNativeConfig.DEFAULT_ACTIVITY)!!,
        action = prefs.getString(KEY_ACTION, GameNativeConfig.DEFAULT_ACTION)!!,
        appIdExtraKey = prefs.getString(KEY_EXTRA_KEY, GameNativeConfig.DEFAULT_APP_ID_EXTRA_KEY)!!
    )

    fun save(config: GameNativeConfig) {
        prefs.edit()
            .putString(KEY_PACKAGE, config.packageName)
            .putString(KEY_ACTIVITY, config.activityClassName)
            .putString(KEY_ACTION, config.action)
            .putString(KEY_EXTRA_KEY, config.appIdExtraKey)
            .apply()
    }

    companion object {
        private const val KEY_PACKAGE = "package_name"
        private const val KEY_ACTIVITY = "activity_class"
        private const val KEY_ACTION = "action"
        private const val KEY_EXTRA_KEY = "app_id_extra_key"
    }
}
