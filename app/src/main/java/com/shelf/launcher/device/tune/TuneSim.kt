package com.shelf.launcher.device.tune

/**
 * 682bd, QA ONLY (owner 25 Nov 21:24 - "verify ... on isolated QA builds"; the KONKR has no vendor root service and the
 * isolated QA app never runs root commands): an in-memory stand-in for the tuner's hardware - three CPU clusters, an
 * Adreno-style GPU level table, thermal zones with a temperature the test sets, a fan - so the tuner, its notification, the
 * heat guard and the R1 tile can be walked on a handheld without touching a single real clock, fan or thermal file.
 *
 * Only [TuneHardware] asks it, and only while [on]; [on] can only be set in the isolated QA app (TestHooks
 * `voltcap_test_tunesim`, QaVariant.isolatedNow). Nothing here reaches RootShell, sysfs or Android settings: reads and
 * writes go to [files], a map. The production app never turns it on.
 */
object TuneSim {
    @Volatile var on = false
        private set

    /** The temperature every simulated CPU / GPU zone reports, °C (set by the test, e.g. a trace step by step). */
    @Volatile var tempC: Float = 45f

    /** Simulated firmware trip on the guard's zones, °C (the RP Classic's first passive trip). */
    const val TRIP_C = 95

    private const val CPUFREQ = "/sys/devices/system/cpu/cpufreq"
    private const val GPU = "/sys/class/kgsl/kgsl-3d0"
    internal val files = java.util.concurrent.ConcurrentHashMap<String, String>()
    @Volatile var fanMode = 4
    /** Every write the tuner made, in order (the QA log reads it; it says what WOULD have been written). */
    val writes = java.util.concurrent.CopyOnWriteArrayList<String>()

    /** Turns the stand-in on (with a fresh model) or off. Returns false (nothing done) outside the isolated QA app. */
    fun set(enable: Boolean, isolatedQa: Boolean): Boolean {
        if (enable && !isolatedQa) return false
        if (enable) model()
        on = enable
        TuneHardware.resetCaches()
        return true
    }

    private fun model() {
        files.clear(); writes.clear(); tempC = 45f; fanMode = 4
        val clusters = listOf(
            Triple("policy0", "0 1 2", listOf(300000, 691200, 1075200, 1478400, 1804800, 2016000)),
            Triple("policy3", "3 4 5 6", listOf(499200, 1113600, 1555200, 1996800, 2400000, 2803200)),
            Triple("policy7", "7", listOf(595200, 1267200, 1977600, 2496000, 2956800, 3187200))
        )
        clusters.forEach { (pol, cpus, f) ->
            val d = "$CPUFREQ/$pol"
            files["$d/cpuinfo_max_freq"] = f.last().toString(); files["$d/cpuinfo_min_freq"] = f.first().toString()
            files["$d/scaling_available_frequencies"] = f.joinToString(" ")
            files["$d/scaling_available_governors"] = "walt schedutil performance powersave conservative"
            files["$d/related_cpus"] = cpus
            files["$d/scaling_max_freq"] = f.last().toString(); files["$d/scaling_min_freq"] = f.first().toString()
            files["$d/scaling_governor"] = "walt"
        }
        files["$GPU/max_pwrlevel"] = "0"; files["$GPU/min_pwrlevel"] = "6"; files["$GPU/num_pwrlevels"] = "7"
        files["$GPU/gpu_available_frequencies"] = listOf(719, 680, 615, 550, 475, 401, 220).joinToString(" ") { (it * 1_000_000L).toString() }
    }

    fun read(path: String): String? = files[path]

    fun write(path: String, value: String) { files[path] = value; writes += "$path=$value" }

    fun policies(): String = files.keys.filter { it.endsWith("/cpuinfo_max_freq") }.map { it.substringBeforeLast('/') }.sorted().joinToString(" ")
}
