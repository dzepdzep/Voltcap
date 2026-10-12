package com.shelf.launcher.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Underclocking from the R1 menu.
 *
 * - **ClusterTune / PULSE**: when one of these community CPU tuners is installed, a tile opens it.
 *   Neither app offers intents, broadcasts or a control API to set a profile from outside (checked
 *   in their sources: only the launcher activity, their Quick Settings tile and its exported
 *   "tile preferences" screen are exported), so Voltcap opens their quick-control screen when it
 *   can, and the app itself otherwise.
 * - **Voltcap's own CPU cap**: where the vendor root service exists ([RootShell], AYN / Retroid with
 *   PServer), a tile cycles a cap on every CPU cluster's `scaling_max_freq` — Off → 85 % → 70 % → 55 %
 *   of each cluster's top speed — snapped to a speed the cluster really has, and read back.
 */
object CpuTuning {
    data class TunerApp(val packageName: String, val label: String)

    private val tunerNames = listOf("clustertune", "pulse")

    /** An installed ClusterTune or PULSE, or null. */
    fun tunerApp(context: Context): TunerApp? = runCatching {
        val pm = context.packageManager
        val launchers = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        launchers.firstNotNullOfOrNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@firstNotNullOfOrNull null
            val label = ri.loadLabel(pm)?.toString().orEmpty()
            val l = label.lowercase()
            val p = pkg.lowercase()
            val match = l == "clustertune" || l == "pulse" || p.contains("clustertune") ||
                (p.contains("pulse") && (p.contains("retro") || p.contains("kei") || p.contains("tune") || l == "pulse"))
            if (match && tunerNames.any { l.contains(it) || p.contains(it) }) TunerApp(pkg, label.ifBlank { "Tuner" }) else null
        }
    }.getOrNull()

    /** Opens the tuner's quick controls (its Quick Settings tile screen), else the app. */
    fun openTuner(context: Context, app: TunerApp): Boolean {
        val pm = context.packageManager
        val quick = Intent("android.service.quicksettings.action.QS_TILE_PREFERENCES")
            .setPackage(app.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val target = if (pm.queryIntentActivities(quick, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()) quick
        else pm.getLaunchIntentForPackage(app.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return target != null && runCatching { context.startActivity(target) }.isSuccess
    }

    // ---- Voltcap's own cap (root service only) ----

    private val steps = listOf(100, 85, 70, 55)
    private const val POLICIES = "/sys/devices/system/cpu/cpufreq"

    private data class Policy(val path: String, val max: Int, val available: List<Int>)

    fun capSupported(): Boolean = RootShell.available && policies().isNotEmpty()

    private fun policies(): List<Policy> {
        val list = RootShell.exec("ls -d $POLICIES/policy*") ?: return emptyList()
        return list.lines().map { it.trim() }.filter { it.matches(Regex("$POLICIES/policy[0-9]+")) }.mapNotNull { p ->
            val max = RootShell.exec("cat $p/cpuinfo_max_freq")?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val avail = RootShell.exec("cat $p/scaling_available_frequencies")
                ?.split(Regex("\\s+"))?.mapNotNull { it.toIntOrNull() }?.sorted().orEmpty()
            Policy(p, max, avail)
        }
    }

    /** The current cap in % of top speed (100 = no cap), from the first cluster. */
    fun currentCap(): Int {
        val p = policies().firstOrNull() ?: return 100
        val now = RootShell.exec("cat ${p.path}/scaling_max_freq")?.trim()?.toIntOrNull() ?: return 100
        return ((now * 100f) / p.max).let { Math.round(it) }.coerceIn(1, 100)
    }

    /** Next step of the cap (Off → 85 → 70 → 55 → Off). Returns the new cap or null when it failed. */
    fun cycleCap(): Int? {
        val cur = currentCap()
        val next = steps.firstOrNull { it < cur - 2 } ?: 100
        return if (applyCap(next)) next else null
    }

    fun applyCap(percent: Int): Boolean {
        val ps = policies()
        if (ps.isEmpty()) return false
        var ok = true
        ps.forEach { p ->
            val wanted = (p.max.toLong() * percent / 100).toInt()
            val target = if (percent >= 100) p.max
            else p.available.filter { it <= wanted }.maxOrNull() ?: wanted
            RootShell.exec("echo $target > ${p.path}/scaling_max_freq")
            val back = RootShell.exec("cat ${p.path}/scaling_max_freq")?.trim()?.toIntOrNull()
            if (back != target) ok = false
        }
        return ok
    }
}
