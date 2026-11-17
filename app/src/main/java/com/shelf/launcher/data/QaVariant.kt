package com.shelf.launcher.data

import android.content.Context

/**
 * Update 500 (D295, permanent fix): the isolated QA app "Voltcap QA" (package com.shelf.launcher.qa).
 *
 * A separate application ID, so Android gives it its own private data: it never sees the owner's Voltcap settings,
 * history, profiles, artwork, sign-ins or Drive state, and installing / restarting it never restarts the owner's Voltcap
 * (no MY_PACKAGE_REPLACED for another package). On top of that, everything that could reach outside its own box is off
 * by code, not by a setting: no Drive sync / backup / last-played / Drive-only saves ([CloudSync.qaPaused]), every game
 * launch blocked (TestHooks.interceptLaunch, no file can allow one), no charge-limit control, no root commands or stick
 * lights through the handheld's vendor service (device/RootShell, VendorAdapter — found on the Thor: the first QA start
 * called AYN's PServerBinder), never offered as the home app; its manifest (src/qa) drops all-files access, the home alias, the boot / update receivers and the notification
 * listener. The ordinary Voltcap (package without the suffix) is unchanged by this file.
 */
object QaVariant {
    const val SUFFIX = ".qa"
    @Volatile private var known: Boolean? = null

    fun isolatedPackage(pkg: String?): Boolean = pkg != null && pkg.endsWith(SUFFIX)

    fun isolated(context: Context): Boolean =
        known ?: isolatedPackage(runCatching { context.applicationContext.packageName }.getOrNull()).also { known = it }

    /**
     * Without a context: the process name is the package name (Android 9+), so this is right from the first line of any
     * process — services and workers included. MainActivity.onCreate also asks [isolated] first.
     */
    val isolatedNow: Boolean get() = known ?: (if (android.os.Build.VERSION.SDK_INT >= 28)
        runCatching { android.app.Application.getProcessName() }.getOrNull()?.substringBefore(':')?.let { isolatedPackage(it) }
        else null)?.also { known = it } ?: false

    /**
     * 676d (D334b): a QA script is driving this isolated QA app - the launch-block file qaguard.sh / popclip.sh / navbar676.sh
     * write while they run. Never true in the owner's Voltcap (another package).
     */
    fun harnessActive(context: Context): Boolean = isolated(context) &&
        runCatching { java.io.File(context.getExternalFilesDir(null), "qa_nolaunch").exists() }.getOrDefault(false)

    /**
     * 676d (D334b, RP Nova n676c13): what Back on the home screen does when it isn't handled inside it. The owner's Voltcap and
     * the QA app used by hand: to the background unless it is the home app (as before). The QA app while a script drives it:
     * nothing - moving it back showed the default home, the OWNER's Voltcap, and the script's next keys were aimed at it.
     */
    internal fun backAtRootLeaves(isDefaultHome: Boolean, harness: Boolean): Boolean = !isDefaultHome && !harness
}
