package com.shelf.launcher.device.tune

import com.shelf.launcher.device.RootShell

/**
 * Low-level clock, governor and fan access for the performance tuner. Everything goes through the
 * vendor root service ([RootShell]: AYN / Retroid PServer); without it nothing here does anything.
 * Several writes are chained into one command per call to keep binder round trips down.
 *
 * Hardware facts this relies on (Qualcomm handhelds):
 *  - CPU: one cpufreq policy per cluster under /sys/devices/system/cpu/cpufreq/policyN with
 *    scaling_max_freq / scaling_min_freq (kHz), scaling_available_frequencies and governors.
 *  - Adreno GPU: capped by *power level* (0 = fastest) through kgsl-3d0/max_pwrlevel, floored with
 *    min_pwrlevel; the kernel refuses a max level slower than the min level, so the min is widened
 *    to the slowest level before a cap is written. The level table is gpu_available_frequencies.
 *  - Fan (AYN / Retroid): settings `fan_mode` (1 quiet, 4 smart, 5 sport, 6 manual) and in manual
 *    mode a PWM at /sys/class/gpio5_pwm2 (duty out of period).
 */
object TuneHardware {
    data class Cluster(
        val policy: String,
        val name: String,
        val cpus: String,
        /** Available speeds, kHz, slowest first. */
        val freqs: List<Int>,
        val maxKhz: Int,
        val minKhz: Int,
        val governors: List<String>
    )

    data class Gpu(
        val root: String,
        /** Level 0 = fastest; MHz per level. */
        val levelMhz: List<Int>
    ) {
        val slowest: Int get() = levelMhz.lastIndex
    }

    private const val CPUFREQ = "/sys/devices/system/cpu/cpufreq"
    private const val FAN = "/sys/class/gpio5_pwm2"

    @Volatile private var clusterCache: List<Cluster>? = null
    @Volatile private var gpuCache: Gpu? = null
    @Volatile private var gpuProbed = false
    @Volatile private var fanProbed = false
    @Volatile private var fanPeriod: Int? = null

    val available: Boolean get() = RootShell.available

    private fun safe(path: String) = path.matches(Regex("[A-Za-z0-9_/.@\\-]+"))

    fun read(path: String): String? {
        if (!safe(path)) return null
        val out = RootShell.exec("cat $path") ?: return null
        return out.trim().takeUnless { it.isEmpty() || it.contains("No such file") || it.contains("denied") }
    }

    /**
     * Writes value=path pairs, in order; true when every command ran.
     * Update 201: it used to send one long "echo … ; echo …" chain, which AYN's root service cuts after 255
     * characters: only the first cluster's governor / max / min ever landed, and the GPU caps, the msm_performance
     * caps and the boost switches after them never ran (Odin 2 Portal: Underclock Medium left the prime core at
     * 3.19 GHz and the GPU at max_pwrlevel 0). Now split into commands that fit ([RootShell.execAll]).
     */
    fun writeAll(writes: List<Pair<String, String>>): Boolean {
        val parts = writes.filter { (p, v) -> safe(p) && v.matches(Regex("[A-Za-z0-9_.:\\- ]+")) }
            .map { (p, v) -> if (v.contains(' ') || v.contains(':')) "echo '$v' > $p" else "echo $v > $p" }
        if (parts.isEmpty()) return true
        return RootShell.execAll(parts)
    }

    /**
     * Update 201: reads several files in as few root calls as fit (the service returns one line per call, so the
     * values come back joined by '|'). Same order as [paths]; null for a file that is missing or unreadable.
     */
    fun readMany(paths: List<String>): List<String?> {
        if (paths.isEmpty()) return emptyList()
        val out = ArrayList<String?>(paths.size)
        // "cd <dir> && for f in a b; do echo "$(cat $f)"; done", joined: as many files per call as fit.
        fun command(chunk: List<String>): String {
            val dir = chunk.map { it.substringBeforeLast('/') }.reduce { a, b -> a.commonPrefix(b) }.substringBeforeLast('/', "")
                .takeIf { it.length > 1 && chunk.all { p -> p.startsWith("$it/") } }
            val names = if (dir != null) chunk.map { it.removePrefix("$dir/") } else chunk
            return (if (dir != null) "cd $dir && " else "") + "for f in ${names.joinToString(" ")}; do echo \"\$(cat \$f)\"; done"
        }
        val joinCost = 40 // "{ … ; } 2>/dev/null | tr '\n' '|'"
        var i = 0
        while (i < paths.size) {
            var chunk = listOf(paths[i])
            var j = i + 1
            while (j < paths.size && command(chunk + paths[j]).length + joinCost <= RootShell.MAX_COMMAND) { chunk = chunk + paths[j]; j++ }
            val safeChunk = chunk.all { safe(it) }
            val line = if (safeChunk) RootShell.execJoined(command(chunk), '|') else null
            val values = line?.split('|')
            chunk.indices.forEach { k ->
                out += values?.getOrNull(k)?.trim()?.takeUnless { it.isEmpty() || it.contains("No such file") || it.contains("denied") }
            }
            i = j
        }
        return out
    }

    private fun String.commonPrefix(other: String): String {
        val n = minOf(length, other.length)
        var k = 0
        while (k < n && this[k] == other[k]) k++
        return substring(0, k)
    }

    // ---- CPU ----

    fun clusters(): List<Cluster> {
        clusterCache?.let { return it }
        if (!available) return emptyList()
        // Update 201: `ls -d` printed one policy per line and AYN's root service only returns the first line, so every
        // AYN handheld showed a single "CPU (cores 0 1 2)" cluster: Big and Prime were never capped, AutoTDP only moved
        // the little cores and the prime-core slider was missing. The shell's glob prints them all on one line.
        val ls = RootShell.exec("echo $CPUFREQ/policy*") ?: return emptyList()
        val raw = ls.split(Regex("\\s+")).map { it.trim() }.filter { it.matches(Regex("$CPUFREQ/policy[0-9]+")) }.mapNotNull { p ->
            val max = read("$p/cpuinfo_max_freq")?.toIntOrNull() ?: return@mapNotNull null
            val min = read("$p/cpuinfo_min_freq")?.toIntOrNull() ?: 0
            val freqs = read("$p/scaling_available_frequencies")?.split(Regex("\\s+"))?.mapNotNull { it.toIntOrNull() }?.sorted()
                ?.ifEmpty { null } ?: listOf(min, max).filter { it > 0 }.distinct()
            val govs = read("$p/scaling_available_governors")?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty()
            val cpus = read("$p/related_cpus").orEmpty()
            Triple(p, cpus, Cluster(p, "", cpus, freqs, max, min, govs))
        }.sortedBy { it.third.maxKhz }
        val names = when (raw.size) {
            1 -> listOf("CPU")
            2 -> listOf("Little", "Big")
            3 -> listOf("Little", "Big", "Prime")
            4 -> listOf("Little", "Mid", "Big", "Prime")
            else -> raw.indices.map { "Cluster ${it + 1}" }
        }
        val list = raw.mapIndexed { i, (_, _, c) -> c.copy(name = names[i]) }
        clusterCache = list
        return list
    }

    /** The real speed at or below [khz] (the slowest one if none is). */
    fun snap(c: Cluster, khz: Int): Int = c.freqs.filter { it <= khz }.maxOrNull() ?: c.freqs.firstOrNull() ?: khz

    fun currentMax(c: Cluster): Int? = read("${c.policy}/scaling_max_freq")?.toIntOrNull()
    fun currentMin(c: Cluster): Int? = read("${c.policy}/scaling_min_freq")?.toIntOrNull()
    fun currentGovernor(c: Cluster): String? = read("${c.policy}/scaling_governor")

    /** Picks the kernel governor for a choice: performance / the vendor's balanced one / powersave. */
    fun governorFor(c: Cluster, choice: GovernorChoice): String? = when (choice) {
        GovernorChoice.SYSTEM -> null
        GovernorChoice.PERFORMANCE -> c.governors.firstOrNull { it == "performance" }
        GovernorChoice.BALANCED -> listOf("walt", "schedutil", "interactive", "ondemand").firstOrNull { it in c.governors }
        GovernorChoice.POWERSAVE -> listOf("powersave", "conservative").firstOrNull { it in c.governors }
    }

    // ---- Vendor overrides (why caps used to "not stick") ----

    /**
     * Qualcomm's perf HAL / game daemon doesn't touch scaling_max_freq: it writes its own limits to
     * msm_performance's `cpu_max_freq` / `cpu_min_freq` ("cpu:kHz cpu:kHz …"), which win over
     * scaling_max_freq, and boosts every touch / app launch through cpu_boost and sched_boost. So a cap
     * written only to scaling_max_freq was lifted again within seconds. Voltcap writes its caps there
     * too and turns the boosts off while a limit is active (put back afterwards).
     */
    data class Overrides(
        val msmMax: String?,
        val msmMin: String?,
        /** Boost knobs found on this device, with their stock values: path to value. */
        val boosts: Map<String, String>
    )

    @Volatile private var overridesCache: Overrides? = null
    @Volatile private var overridesProbed = false

    fun overrides(): Overrides? {
        if (overridesProbed) return overridesCache
        overridesProbed = true
        if (!available) return null
        val msmDirs = listOf("/sys/kernel/msm_performance/parameters", "/sys/module/msm_performance/parameters")
        val msmMax = msmDirs.map { "$it/cpu_max_freq" }.firstOrNull { read(it) != null }
        val msmMin = msmDirs.map { "$it/cpu_min_freq" }.firstOrNull { read(it) != null }
        val boostCandidates = listOf(
            "/sys/devices/system/cpu/cpu_boost/input_boost_ms",
            "/sys/module/cpu_boost/parameters/input_boost_ms",
            "/sys/devices/system/cpu/cpu_boost/sched_boost_on_input",
            "/sys/module/cpu_boost/parameters/sched_boost_on_input",
            "/proc/sys/walt/sched_boost",
            "/proc/sys/kernel/sched_boost",
            "/sys/devices/system/cpu/cpu_boost/powerkey_input_boost_ms"
        )
        val boosts = LinkedHashMap<String, String>()
        boostCandidates.forEach { p -> read(p)?.takeIf { it.matches(Regex("-?[0-9]+")) }?.let { boosts[p] = it } }
        overridesCache = Overrides(msmMax, msmMin, boosts)
        return overridesCache
    }

    /** The first CPU number of a cluster ("4 5 6" → 4), for msm_performance's "cpu:kHz" lists. */
    fun firstCpu(c: Cluster): Int? = c.cpus.split(Regex("[\\s,-]+")).firstOrNull { it.isNotBlank() }?.toIntOrNull()
        ?: c.policy.substringAfterLast("policy").toIntOrNull()

    /** Update 201: every CPU of a cluster ("3 4 5 6" or "3-6" → 3, 4, 5, 6), for msm_performance's per-CPU lists. */
    fun cpusOf(c: Cluster): List<Int> {
        val list = c.cpus.split(Regex("[\\s,]+")).filter { it.isNotBlank() }.flatMap { part ->
            val range = part.split('-').mapNotNull { it.trim().toIntOrNull() }
            when {
                range.size == 2 && range[0] <= range[1] -> (range[0]..range[1]).toList()
                range.size == 1 -> range
                else -> emptyList()
            }
        }.distinct()
        return list.ifEmpty { listOfNotNull(firstCpu(c)) }
    }

    // ---- GPU ----

    fun gpu(): Gpu? {
        if (gpuProbed) return gpuCache
        gpuProbed = true
        if (!available) return null
        val root = listOf("/sys/class/kgsl/kgsl-3d0", "/sys/devices/platform/soc@0/3d00000.gpu/kgsl/kgsl-3d0")
            .firstOrNull { read("$it/max_pwrlevel") != null } ?: return null
        val table = (read("$root/gpu_available_frequencies") ?: read("$root/devfreq/available_frequencies"))
            ?.split(Regex("\\s+"))?.mapNotNull { it.toLongOrNull() }?.filter { it > 0 }
            ?.sortedDescending()?.map { (if (it > 100_000_000L) it / 1_000_000L else it / 1000L).toInt() }
            ?: return null
        val levels = read("$root/num_pwrlevels")?.toIntOrNull()
        gpuCache = Gpu(root, if (levels != null && levels in 1..table.size) table.take(levels) else table)
        return gpuCache
    }

    /** Hz for a GPU level, for devfreq's max_freq / min_freq (thermal and the perf HAL use those too). */
    fun gpuHz(g: Gpu, level: Int): Long? = g.levelMhz.getOrNull(level)?.toLong()?.times(1_000_000L)

    fun hasGpuDevfreq(g: Gpu): Boolean = read("${g.root}/devfreq/max_freq") != null

    fun gpuLevels(g: Gpu): Pair<Int?, Int?> = read("${g.root}/max_pwrlevel")?.toIntOrNull() to read("${g.root}/min_pwrlevel")?.toIntOrNull()

    /** The level (index) whose speed is at or below [percent] of the top speed. */
    fun gpuLevelFor(g: Gpu, percent: Int): Int {
        val top = g.levelMhz.firstOrNull() ?: return 0
        if (percent >= 100) return 0
        val want = top * percent / 100
        val i = g.levelMhz.indexOfFirst { it <= want }
        return if (i < 0) g.slowest else i
    }

    // ---- Fan ----

    fun fanPeriod(): Int? {
        if (fanProbed) return fanPeriod
        fanProbed = true
        if (!available || read("$FAN/duty") == null) return null
        fanPeriod = read("$FAN/period")?.toIntOrNull()?.takeIf { it > 0 } ?: 100
        return fanPeriod
    }

    fun hasCustomFan(): Boolean = fanPeriod() != null

    fun fanMode(): Int? = RootShell.exec("settings get system fan_mode")?.trim()?.toIntOrNull()

    fun setFanMode(mode: Int) { RootShell.exec("settings put system fan_mode $mode") }

    fun setFanPercent(percent: Int): Boolean {
        val period = fanPeriod() ?: return false
        return writeAll(listOf("$FAN/duty" to (period * percent.coerceIn(0, 100) / 100).toString()))
    }

    fun fanRpm(): Int? = read("$FAN/speed")?.toIntOrNull()

    // ---- Temperature (root can read zones apps can't) ----

    /** The CPU / GPU thermal zones' numbers (thermal_zoneN). */
    @Volatile private var zones: List<String>? = null

    /**
     * Update 201: the hottest CPU / GPU zone, °C. Both the zone list (one line per zone) and the readings
     * ("cat a ; cat b") came back as their first line only from AYN's root service; zone 0 is a modem sensor ("pa")
     * on the Odin 2 Portal and the Thor, so no zone was ever found and this was always null: Voltcap's fan curve and
     * hold-temp never set a fan speed (the fan stayed at AYN's manual 50 %), the heat guard never stepped and the
     * temperature light never changed outside AutoTDP. Both now come back on one line.
     */
    fun socTempC(): Float? {
        if (!available) return null
        val z = zones ?: run {
            val out = RootShell.execJoined("for z in /sys/class/thermal/thermal_zone*; do echo \"\${z##*zone} \$(cat \$z/type)\"; done", ';')
                ?: return null
            out.split(';').mapNotNull { entry ->
                val parts = entry.trim().split(' ')
                if (parts.size < 2 || parts[0].toIntOrNull() == null) return@mapNotNull null
                val type = parts[1].lowercase()
                // "socd" is the SoC droop detector (a level, not a temperature).
                if ((type.contains("cpu") || type.contains("gpu") || type.contains("soc")) && type != "socd") parts[0] else null
            }.take(24).also { zones = it }
        }
        if (z.isEmpty()) return null
        val out = RootShell.execJoined("for n in ${z.joinToString(" ")}; do cat /sys/class/thermal/thermal_zone\$n/temp; done") ?: return null
        return out.split(Regex("\\s+")).mapNotNull { it.trim().toFloatOrNull() }
            .map { if (it > 1000f) it / 1000f else it }.filter { it in 5f..130f }.maxOrNull()
    }
}
