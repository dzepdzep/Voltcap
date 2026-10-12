package com.shelf.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Lists installed apps the same way a launcher's app drawer does — resolving
 * ACTION_MAIN / CATEGORY_LAUNCHER — and turns each into a Game entry so it
 * can sit in the same dock as scanned ROMs.
 *
 * Note: on Android 11+ this needs the <queries> block in AndroidManifest.xml
 * declaring the MAIN/LAUNCHER intent filter, or package-visibility rules will
 * hide most other apps from this query. It's already there — see the
 * <queries> element — but worth knowing if this ever returns suspiciously few
 * results after a manifest edit.
 *
 * This deliberately doesn't try to guess which installed apps are "games" —
 * it lists everything launchable, same as an app drawer would. A category
 * filter or a per-app hide toggle is the natural next improvement if that's
 * too noisy in practice.
 */
object AppScanner {

    fun scan(context: Context): List<Game> = scanOrNull(context).orEmpty()

    /** Like [scan] but null when the package manager failed, so a refresh
     * can keep the old list instead of wiping it. */
    fun scanOrNull(context: Context): List<Game>? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolveInfos = try {
            // Flags deliberately 0, NOT MATCH_DEFAULT_ONLY: that flag only
            // returns activities declaring CATEGORY_DEFAULT, and plenty of
            // launcher activities (DuckStation, RetroArch and other emulators
            // among them) declare only CATEGORY_LAUNCHER — so it silently hid
            // exactly the apps this list exists to show.
            pm.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            return null
        }

        return resolveInfos
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map { info ->
                val packageName = info.activityInfo.packageName
                Game(
                    name = info.loadLabel(pm).toString(),
                    platform = "App",
                    hue = hueFor(packageName),
                    sourceType = SourceType.APP,
                    packageName = packageName
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    /**
     * A cheap "did any package change since [lastSeq]" check for when Voltcap
     * comes back to the front. Returns the new sequence number and whether
     * anything changed (API 26+; older Android always reports "unknown" and
     * relies on the package broadcasts).
     */
    fun changeCheck(context: Context, lastSeq: Int?): Pair<Int?, Boolean> {
        if (android.os.Build.VERSION.SDK_INT < 26) return null to false
        return runCatching {
            val changed = context.packageManager.getChangedPackages(lastSeq ?: 0)
            if (changed == null) lastSeq to false
            else changed.sequenceNumber to (lastSeq != null && changed.packageNames.isNotEmpty())
        }.getOrDefault(lastSeq to false)
    }

    private fun hueFor(seed: String): Int = (seed.hashCode() and 0x7fffffff) % 360
}

/**
 * The installed-app list, shared process-wide so Settings screens can use it
 * instead of running their own package-manager scans on the main thread.
 * The view model publishes every scan here.
 */
object InstalledAppsHub {
    val apps = kotlinx.coroutines.flow.MutableStateFlow<List<Game>>(emptyList())
}
