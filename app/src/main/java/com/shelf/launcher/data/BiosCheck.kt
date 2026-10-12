package com.shelf.launcher.data

import java.io.File

/**
 * Which consoles in your library need a BIOS file, and whether it's where the
 * emulators look (Settings › Emulators › BIOS check). Only checks that a file
 * with the right name is there — not that it's a good dump.
 */
object BiosCheck {
    /** One BIOS need: any of [names] satisfies it. */
    data class Need(val what: String, val names: List<String>, val required: Boolean)

    /** Result for one need: where it was found (null = missing). */
    data class Result(val need: Need, val foundAt: String?)

    private val NEEDS: Map<String, List<Need>> = mapOf(
        "PS1" to listOf(Need("PlayStation BIOS (any region)", listOf(
            "scph5501.bin", "scph5500.bin", "scph5502.bin", "scph1001.bin", "scph7001.bin", "scph101.bin", "psxonpsp660.bin"
        ), required = true)),
        "PS2" to listOf(Need("PlayStation 2 BIOS", listOf("*.bin"), required = true)),
        "SegaCD" to listOf(Need("Sega CD / Mega-CD BIOS", listOf("bios_CD_U.bin", "bios_CD_E.bin", "bios_CD_J.bin"), required = true)),
        "Saturn" to listOf(Need("Saturn BIOS", listOf("sega_101.bin", "mpr-17933.bin", "saturn_bios.bin"), required = true)),
        "Dreamcast" to listOf(
            Need("Dreamcast boot ROM", listOf("dc_boot.bin"), required = true),
            Need("Dreamcast flash", listOf("dc_flash.bin"), required = false)
        ),
        "PCEngine" to listOf(Need("PC Engine CD system card (CD games only)", listOf("syscard3.pce", "syscard2.pce", "syscard1.pce"), required = false)),
        "NDS" to listOf(Need("DS BIOS and firmware (some cores)", listOf("bios7.bin", "bios9.bin", "firmware.bin"), required = false)),
        "GBA" to listOf(Need("GBA BIOS (optional, better accuracy)", listOf("gba_bios.bin"), required = false)),
        "NES" to listOf(Need("Famicom Disk System BIOS (FDS games only)", listOf("disksys.rom"), required = false)),
        "NeoGeo" to listOf(Need("Neo Geo BIOS", listOf("neogeo.zip"), required = true)),
        "Arcade" to listOf(Need("Neo Geo BIOS (Neo Geo arcade games)", listOf("neogeo.zip"), required = false))
    )

    private const val STORAGE = "/storage/emulated/0"
    private val PLACES = listOf(
        "RetroArch/system", "RetroArch/system/dc", "RetroArch/system/pcsx2/bios", "bios", "BIOS",
        "ARMSX2/bios", "NetherSX2/bios", "AetherSX2/bios", "Flycast/data", "DuckStation/bios", "melonDS", "DraStic/system"
    )

    /** Folders you pointed the check at (Settings › Emulators › BIOS check › Add a folder). */
    fun userFolders(c: android.content.Context): Set<String> =
        c.applicationContext.getSharedPreferences("bios_check", android.content.Context.MODE_PRIVATE).getStringSet("folders", emptySet()).orEmpty()

    fun setUserFolders(c: android.content.Context, folders: Set<String>) =
        c.applicationContext.getSharedPreferences("bios_check", android.content.Context.MODE_PRIVATE).edit().putStringSet("folders", folders).apply()

    /**
     * Blocking — call off the main thread. [extra] are your own folders: they're searched with their
     * sub-folders (two levels), and for PS2 any .bin in them counts.
     */
    fun check(platforms: Collection<String>, romRoot: String, extra: Collection<String> = emptyList()): Map<String, List<Result>> {
        val dirs = (PLACES.map { File(STORAGE, it) } + File(romRoot) + File(romRoot, "bios") + File(romRoot, "BIOS"))
            .filter { it.isDirectory }
        val userDirs = extra.map { File(it) }.filter { it.isDirectory }.flatMap { root ->
            listOf(root) + (root.listFiles()?.filter { it.isDirectory }.orEmpty()).flatMap { sub ->
                listOf(sub) + sub.listFiles()?.filter { it.isDirectory }.orEmpty()
            }
        }
        val userPaths = extra.map { File(it).absolutePath.trimEnd('/') + "/" }
        val files = (dirs + userDirs).distinct().flatMap { d -> d.listFiles()?.filter { it.isFile }.orEmpty() }
        return platforms.filter { it in NEEDS }.associateWith { platform ->
            NEEDS.getValue(platform).map { need ->
                val hit = files.firstOrNull { f ->
                    need.names.any { n ->
                        // PS2: any .bin in a PS2 emulator's own bios folder (ARMSX2, NetherSX2, pcsx2…)
                        if (n.startsWith("*.")) f.extension.equals(n.removePrefix("*."), true) && (
                            f.parentFile?.path?.contains("sx2", ignoreCase = true) == true ||
                                // A PS2 folder counts only when it's a BIOS folder (not ROMs/PS2 full of games).
                                (f.parentFile?.path?.contains("ps2", ignoreCase = true) == true && f.parentFile?.path?.contains("bios", ignoreCase = true) == true) ||
                                userPaths.any { u -> f.absolutePath.startsWith(u) && f.name.contains("scph", ignoreCase = true) })
                        else f.name.equals(n, ignoreCase = true)
                    }
                }
                Result(need, hit?.absolutePath)
            }
        }
    }

    /** Consoles the library has that need nothing — for the "all good" line. */
    fun needsNothing(platforms: Collection<String>): List<String> = platforms.filter { it !in NEEDS }
}
