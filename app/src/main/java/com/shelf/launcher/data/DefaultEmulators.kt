package com.shelf.launcher.data

/**
 * Sensible starting assignments, applied once for platforms that have no
 * emulator set yet — and only when the app is actually installed, so nothing
 * gets pointed at something that isn't there.
 *
 * Applied once (tracked in PreferencesStore) so a deliberate change is never
 * silently reverted on the next scan.
 */
object DefaultEmulators {

    private data class Default(
        val platform: String,
        val packageNames: List<String>,
        val coreId: String? = null
    )

    private val defaults = listOf(
        Default("PS2", listOf("xyz.aethersx2.android")),
        Default("PS1", listOf("com.github.stenzek.duckstation")),
        Default("GameCube", listOf("org.dolphinemu.dolphinemu")),
        Default("Wii", listOf("org.dolphinemu.dolphinemu")),
        Default("PSP", listOf("org.ppsspp.ppssppgold", "org.ppsspp.ppsspp")),
        // RetroArch-based, each with the requested core
        Default("GBA", RETROARCH, "mgba"),
        Default("GB", RETROARCH, "gambatte"),
        Default("GBC", RETROARCH, "gambatte"),
        Default("N64", RETROARCH, "mupen64plus_next_gles3"),
    )

    fun configFor(platform: String, installedPackages: Set<String>): EmulatorConfig? {
        val default = defaults.firstOrNull { it.platform.equals(platform, true) } ?: return null
        val pkg = default.packageNames.firstOrNull { it in installedPackages } ?: return null

        return if (default.coreId != null) {
            EmulatorConfig(
                platform = platform,
                mode = LaunchMode.EXPLICIT_INTENT,
                packageName = pkg,
                activityClassName = CoreRegistry.RETROARCH_ACTIVITY,
                romExtraKey = CoreRegistry.ROM_EXTRA_KEY,
                coreExtraKey = CoreRegistry.CORE_EXTRA_KEY,
                coreExtraValue = CoreRegistry.corePath(pkg, default.coreId)
            )
        } else {
            EmulatorConfig(
                platform = platform,
                mode = LaunchMode.EXPLICIT_INTENT,
                packageName = pkg
            )
        }
    }
}

private val RETROARCH = listOf("com.retroarch.aarch64", "com.retroarch")
