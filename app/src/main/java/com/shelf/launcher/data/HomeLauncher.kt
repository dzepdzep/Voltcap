package com.shelf.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Shelf as the device's home screen. The HOME entry is an activity-alias
 * that starts disabled; switching it on makes Shelf one of the home apps
 * Android offers, then Android's own chooser sets it as the default.
 */
object HomeLauncher {
    private fun alias(context: Context) = ComponentName(context, "com.shelf.launcher.HomeAlias")

    fun isOffered(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(alias(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /** Whether Shelf is the current default home app. */
    fun isDefault(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /** Offers Shelf as a home app (or stops) and opens Android's home-app picker. */
    fun setOffered(context: Context, offered: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            alias(context),
            if (offered) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        if (offered) openHomePicker(context)
    }

    fun openHomePicker(context: Context) {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
