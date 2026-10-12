package com.shelf.launcher.data

/**
 * Common libretro cores per system, so picking RetroArch for a console can be
 * followed by picking *which* core runs it — otherwise RetroArch opens to its
 * own menu instead of booting the game.
 *
 * Core ids are the file names RetroArch's Android core downloader uses:
 * `<id>_libretro_android.so`. They have to match exactly — asking RetroArch
 * for a core file that isn't there makes it hang on a black screen.
 */
data class LibretroCore(val id: String, val displayName: String)

object CoreRegistry {

    private val byPlatform: Map<String, List<LibretroCore>> = mapOf(
        "NES" to listOf(
            LibretroCore("nestopia", "Nestopia UE"),
            LibretroCore("mesen", "Mesen"),
            LibretroCore("fceumm", "FCEUmm"),
            LibretroCore("quicknes", "QuickNES"),
        ),
        "SNES" to listOf(
            LibretroCore("snes9x", "Snes9x"),
            LibretroCore("bsnes", "bsnes"),
            LibretroCore("snes9x2010", "Snes9x 2010"),
            LibretroCore("bsnes_hd_beta", "bsnes HD"),
            LibretroCore("mesen-s", "Mesen-S"),
        ),
        "GB" to listOf(
            LibretroCore("gambatte", "Gambatte"),
            LibretroCore("sameboy", "SameBoy"),
        ),
        "GBC" to listOf(
            LibretroCore("gambatte", "Gambatte"),
            LibretroCore("sameboy", "SameBoy"),
        ),
        "GBA" to listOf(
            LibretroCore("mgba", "mGBA"),
            LibretroCore("vba_next", "VBA Next"),
            LibretroCore("gpsp", "gpSP"),
        ),
        "N64" to listOf(
            // Android builds of Mupen64Plus-Next are split by graphics API;
            // there is no plain "mupen64plus_next" core on Android.
            LibretroCore("mupen64plus_next_gles3", "Mupen64Plus-Next GLES3"),
            LibretroCore("mupen64plus_next_gles2", "Mupen64Plus-Next GLES2"),
            LibretroCore("parallel_n64", "ParaLLEl N64"),
        ),
        "PS1" to listOf(
            LibretroCore("swanstation", "SwanStation"),
            LibretroCore("pcsx_rearmed", "PCSX ReARMed"),
            LibretroCore("mednafen_psx_hw", "Beetle PSX HW"),
        ),
        "PS2" to listOf(
            // PS2 via libretro is demanding; standalone AetherSX2/NetherSX2 is
            // usually the better pick, but the core exists.
            LibretroCore("pcsx2", "PCSX2"),
        ),
        "Saturn" to listOf(
            LibretroCore("mednafen_saturn", "Beetle Saturn"),
            LibretroCore("yabasanshiro", "YabaSanshiro"),
        ),
        "PSP" to listOf(
            LibretroCore("ppsspp", "PPSSPP"),
        ),
        "NDS" to listOf(
            LibretroCore("melonds", "melonDS"),
            LibretroCore("desmume", "DeSmuME"),
        ),
        "Genesis" to listOf(
            LibretroCore("genesis_plus_gx", "Genesis Plus GX"),
            LibretroCore("picodrive", "PicoDrive"),
        ),
        "MegaDrive" to listOf(
            LibretroCore("genesis_plus_gx", "Genesis Plus GX"),
            LibretroCore("picodrive", "PicoDrive"),
        ),
        "Dreamcast" to listOf(
            LibretroCore("flycast", "Flycast"),
        ),
        "Arcade" to listOf(
            LibretroCore("fbneo", "FinalBurn Neo"),
            LibretroCore("mame2003_plus", "MAME 2003-Plus"),
            LibretroCore("mame2010", "MAME 2010"),
        ),
    )

    /** The hand-picked cores first, then every other core the catalog knows for this console. */
    fun coresFor(platform: String): List<LibretroCore> {
        val mine = byPlatform.entries.firstOrNull { it.key.equals(platform, true) }?.value.orEmpty()
        val more = EmulatorCatalog.cores.entries.firstOrNull { it.key.equals(platform, true) }?.value.orEmpty()
        return (mine + more.filter { c -> mine.none { it.id == c.id } })
    }

    /** RetroArch keeps cores under its own private data dir, so the path
     * depends on which RetroArch build is installed (standard vs aarch64). */
    fun corePath(retroarchPackage: String, coreId: String): String =
        "/data/data/$retroarchPackage/cores/${coreId}_libretro_android.so"

    /** Core ids older Shelf versions saved that don't exist on Android. */
    private val renamedCores = mapOf(
        "mupen64plus_next" to "mupen64plus_next_gles3",
    )

    /** Repairs a saved core path that points at a core name Android builds
     * of RetroArch don't use, so existing setups work without re-picking. */
    fun fixCorePath(path: String): String {
        val file = path.substringAfterLast('/')
        val id = file.removeSuffix("_libretro_android.so")
        val fixed = renamedCores[id] ?: return path
        return path.substringBeforeLast('/') + "/${fixed}_libretro_android.so"
    }

    /** True for packages that look like a RetroArch build — used to decide
     * whether to offer core selection at all. */
    fun isRetroArch(packageName: String?): Boolean =
        packageName != null && packageName.startsWith("com.retroarch")

    const val ROM_EXTRA_KEY = "ROM"
    const val CORE_EXTRA_KEY = "LIBRETRO"
    const val RETROARCH_ACTIVITY = "com.retroarch.browser.retroactivity.RetroActivityFuture"
}
