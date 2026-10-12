package com.shelf.launcher.data

/**
 * Known Android emulators, used to *suggest* a sensible default when you're
 * assigning an emulator to a console.
 *
 * Important design note: this registry is a convenience, never a gate. The
 * picker always also lists every installed app, so if a package name here is
 * wrong, outdated, or the app is a fork with a different id (NetherSX2 vs
 * AetherSX2, RetroArch's various ABI-specific builds, paid vs free variants),
 * you can still pick the right app manually. Treat these as best-effort
 * hints — they're from general knowledge, not verified against each app's
 * current manifest.
 */
data class KnownEmulator(
    val packageName: String,
    val displayName: String,
    val platforms: Set<String>
)

object EmulatorRegistry {

    val known: List<KnownEmulator> = listOf(
        // Multi-system
        KnownEmulator("com.retroarch", "RetroArch", ALL_PLATFORMS),
        KnownEmulator("com.retroarch.aarch64", "RetroArch (64-bit)", ALL_PLATFORMS),
        KnownEmulator("com.swordfish.lemuroid", "Lemuroid", ALL_PLATFORMS),

        // PlayStation
        KnownEmulator("com.github.stenzek.duckstation", "DuckStation", setOf("PS1")),
        KnownEmulator("xyz.aethersx2.android", "AetherSX2 / NetherSX2", setOf("PS2")),
        KnownEmulator("org.ppsspp.ppsspp", "PPSSPP", setOf("PSP")),
        KnownEmulator("org.ppsspp.ppssppgold", "PPSSPP Gold", setOf("PSP")),
        KnownEmulator("org.vita3k.emulator", "Vita3K", setOf("PSVita")),

        // Nintendo
        KnownEmulator("org.dolphinemu.dolphinemu", "Dolphin", setOf("GameCube", "Wii")),
        KnownEmulator("org.mupen64plusae.v3.fzurita", "Mupen64Plus FZ", setOf("N64")),
        KnownEmulator("org.mupen64plusae.v3.fzurita.pro", "M64Plus FZ Pro", setOf("N64")),
        KnownEmulator("com.dsemu.drastic", "DraStic", setOf("NDS")),
        KnownEmulator("me.magnum.melonds", "melonDS", setOf("NDS")),
        KnownEmulator("org.citra.citra_emu", "Citra", setOf("3DS")),
        KnownEmulator("com.fastemulator.gba", "My Boy!", setOf("GBA")),
        KnownEmulator("com.fastemulator.gbafree", "My Boy! Free", setOf("GBA")),
        KnownEmulator("it.dbtecno.pizzaboygba", "Pizza Boy GBA", setOf("GBA")),
        KnownEmulator("it.dbtecno.pizzaboy", "Pizza Boy GBC", setOf("GB", "GBC")),
        KnownEmulator("com.explusalpha.Snes9xPlus", "Snes9x EX+", setOf("SNES")),
        KnownEmulator("com.explusalpha.NesEmu", "NES.emu", setOf("NES")),

        // Sega
        KnownEmulator("com.explusalpha.MdEmu", "MD.emu", setOf("Genesis", "MegaDrive")),
        KnownEmulator("io.recompiled.redream", "Redream", setOf("Dreamcast")),
        KnownEmulator("com.flycast.emulator", "Flycast", setOf("Dreamcast", "Arcade")),

        // Arcade
        KnownEmulator("com.seleuco.mame4droid", "MAME4droid", setOf("Arcade")),

        // PlayStation 4 (shadPS4's Android port: games are picked inside the emulator for now)
        KnownEmulator("com.shadps4.emu", "shadPS4", setOf("PS4")),
    ).let { hand -> hand + catalogEmulators().filter { c -> hand.none { it.packageName == c.packageName } } }

    /** Known emulators that claim support for [platform]. */
    fun suggestionsFor(platform: String): List<KnownEmulator> =
        known.filter { it.platforms == ALL_PLATFORMS || it.platforms.any { p -> p.equals(platform, true) } }

    /**
     * Update 144: none for a package name a catalog build only borrows (Genshin Impact, AnTuTu — EmulatorCatalog):
     * the real app installed under it showed as "eden (Optimized)". Whether such a build really is the emulator is
     * checked where the installed app is known (EmulatorCatalog.isRealEmulator).
     */
    fun displayNameFor(packageName: String?): String? =
        if (packageName == null || packageName in EmulatorCatalog.BORROWED_NAMES) null
        else known.firstOrNull { it.packageName == packageName }?.displayName

}

/** Every standalone emulator in the generated catalog that isn't listed by hand above. */
private fun catalogEmulators(): List<KnownEmulator> =
    EmulatorCatalog.standalone.flatMap { (platform, entries) -> entries.map { it.packageName to (it.name to platform) } }
        .groupBy({ it.first }, { it.second })
        .map { (pkg, list) -> KnownEmulator(pkg, catalogLabel(list.first().first), list.map { it.second }.toSet()) }

/**
 * Update 144: the catalog's names as labels — starting with a capital ("azahar" → "Azahar"); names that are just a
 * package id ("com.PceEmu") stay as they are (the installed app's own name is shown for those where it's known).
 */
private fun catalogLabel(name: String): String {
    // Update 144: a first word with its own capitals is the name's real spelling ("ePSXe", "aPS3e", "hakuX" — not
    // "EPSXe"), and one with a dot is a package id, even with more after it ("com.seleuco.mame4droid 2024").
    val first = name.substringBefore(' ')
    return if (first.contains('.') || first.any { it.isUpperCase() }) name else name.replaceFirstChar { it.titlecase() }
}

/** Sentinel meaning "handles essentially any system" — multi-system
 * frontends like RetroArch shouldn't be listed per-console. */
internal val ALL_PLATFORMS: Set<String> = setOf("*")
