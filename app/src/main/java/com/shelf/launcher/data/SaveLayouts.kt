package com.shelf.launcher.data

/**
 * (Update 241, #70 / #72 / #73: what kind of storage a save file belongs to, and which files make up one logical save.)
 *
 * Read-only path parsing — nothing here opens, writes or moves a file. Used by:
 * - [SaveKind.accepts] (a folder you picked yourself also recognises 3DS / Wii title folders, whose files are often
 *   called just "main" or "banner.bin" — before, a picked folder dropped them: no extension it knew);
 * - [SaveCapsules] (one save per 3DS / Wii title folder, never one per file; such files never go to step 4's
 *   name matching);
 * - [CloudSync] (a multi-file save changed on both devices is one conflict, never a half-and-half merge).
 */
internal object SaveLayouts {

    /** The kind of storage a save file is part of (#70). */
    enum class Storage {
        /** One file per game, named after it: .srm / .sav / .eep / .dsv… */
        IN_GAME_FILE,
        /** One file holding several games' saves: PS1 .mcd/.mcr, PS2 .ps2, GameCube .raw, Dreamcast VMU, N64 .mpk. */
        MEMORY_CARD_IMAGE,
        /** A PS2 folder memory card ("Mcd001.ps2/…"): one card made of many files. */
        FOLDER_CARD,
        /** A per-game save folder of several files (PSP SAVEDATA/<save>/…). */
        FOLDER_SAVE,
        /** A per-title folder named by title id (3DS title/0004xxxx/<low>/data/…, Wii title/0001xxxx/<low>/data/…). */
        TITLE_FOLDER,
        /** A packaged export needing its own importer (Wii data.bin outside the NAND) — listed, never restored. */
        EXPORT_ARCHIVE,
        /** Save states — never synced. */
        SAVE_STATE,
        UNKNOWN
    }

    /**
     * One 3DS or Wii title folder: [prefix] is the path up to and including "data" (the unit's identity inside its
     * synced folder), [high] / [low] the title id's halves (lower case hex), [profile] what comes before "title"
     * (3DS: "<id0>/<id1>", empty when the folder starts below it).
     */
    data class TitleUnit(val platform: String, val prefix: String, val high: String, val low: String, val profile: String) {
        val titleId: String get() = (high + low).uppercase()
    }

    private val HEX8 = Regex("[0-9a-fA-F]{8}")

    /**
     * 3DS title types whose saves live on the SD card: applications (00040000) and demos (00040002). System titles
     * (00040010…, on the NAND), updates (0004000e) and DLC (0004008c) hold no game save.
     */
    private val N3DS_GAME_TYPES = setOf("00040000", "00040002")

    /** Wii title types that hold game saves (discs, WiiWare / channels, disc channels) — as in SyncTrees. */
    private val WII_GAME_TYPES = setOf("00010000", "00010001", "00010004")

    /** Install / licence files, never saves. */
    private val WII_NOT_SAVES = setOf("tmd", "tik", "app", "cert", "wad")

    /**
     * Citra / Lime3DS / Azahar: ".../Nintendo 3DS/<id0>/<id1>/title/<high>/<low>/data/00000001/<files>" plus
     * ".../data/00000001.metadata" (Citra's archive_savedata.cpp: "%08x/%08x/data/00000001/" and
     * "%08x/%08x/data/00000001.metadata"). Every file below data/ belongs to the save, whatever its name ("main",
     * "savedata.dat", no extension at all). Null when [rel] isn't inside such a folder (extdata, content, DLC…).
     */
    fun n3ds(rel: String): TitleUnit? {
        val parts = rel.split('/')
        for (t in parts.indices.reversed()) {
            if (!parts[t].equals("title", ignoreCase = true)) continue
            if (parts.size < t + 5) continue
            val high = parts[t + 1]
            val low = parts[t + 2]
            if (!HEX8.matches(high) || !HEX8.matches(low)) continue
            if (high.lowercase() !in N3DS_GAME_TYPES) continue
            if (!parts[t + 3].equals("data", ignoreCase = true)) continue
            if (parts.last().startsWith(".")) return null
            return TitleUnit("3ds", parts.take(t + 4).joinToString("/"), high.lowercase(), low.lowercase(), parts.take(t).joinToString("/"))
        }
        // A folder set up at ".../title" itself: "<high>/<low>/data/…".
        if (parts.size >= 4 && parts[0].lowercase() in N3DS_GAME_TYPES && HEX8.matches(parts[1]) &&
            parts[2].equals("data", ignoreCase = true) && !parts.last().startsWith(".")
        ) return TitleUnit("3ds", parts.take(3).joinToString("/"), parts[0].lowercase(), parts[1].lowercase(), "")
        return null
    }

    /**
     * Dolphin's Wii NAND: "<type>/<low>/data/<files>" inside Wii/title (the "Dolphin Wii" folder), or with "title/"
     * (or "Wii/title/") in front when a bigger folder was picked. The low half is the game code in hex ("524d4345" =
     * "RMCE"). Only game title types; install files (.tmd, .app…) never count.
     */
    fun wii(rel: String): TitleUnit? {
        val parts = rel.split('/')
        if (parts.size < 4) return null
        val name = parts.last()
        if (name.startsWith(".") || name.substringAfterLast('.', "").lowercase() in WII_NOT_SAVES) return null
        for (i in 0..parts.size - 4) {
            if (parts[i] !in WII_GAME_TYPES) continue
            if (i > 0 && !parts[i - 1].equals("title", ignoreCase = true)) continue
            if (!HEX8.matches(parts[i + 1]) || !parts[i + 2].equals("data", ignoreCase = true)) continue
            return TitleUnit("wii", parts.take(i + 3).joinToString("/"), parts[i], parts[i + 1].lowercase(), parts.take(i).joinToString("/"))
        }
        return null
    }

    /** A 3DS or Wii title folder's file (see [n3ds], [wii]). */
    fun titleUnit(rel: String): TitleUnit? = n3ds(rel) ?: wii(rel)

    /**
     * The logical save [rel] belongs to when it's one of several files that only work together (#73): a 3DS / Wii title
     * folder, a PSP save folder. Null for single-file saves and memory-card images (a PS2 folder card is handled by
     * CloudSync's own folder-card rule, unchanged).
     */
    fun unitOf(kind: SaveKind, rel: String): String? {
        titleUnit(rel)?.let { return it.prefix }
        val parts = rel.split('/')
        if (parts.size < 2) return null
        if (parts.dropLast(1).any { it.lowercase().endsWith(".ps2") }) return null
        if (kind == SaveKind.PSP_SAVEDATA) return parts.first()
        val s = parts.dropLast(1).indexOfFirst { it.equals("SAVEDATA", ignoreCase = true) }
        if (s >= 0 && parts.size >= s + 3) return parts.take(s + 2).joinToString("/")
        return null
    }

    private val CARD_EXTS = setOf("mcd", "mcr", "mc", "gme", "mcs", "psm", "vgs", "mem", "ddf", "vm1", "ps2", "raw", "mpk", "vmu", "vms")
    private val GAME_FILE_EXTS = setOf("srm", "sav", "rtc", "eep", "fla", "sra", "dsv", "bkr", "bcr", "smpc", "brm", "nv", "flash", "gci")
    private val VMU_SAVE = Regex("vmu_save_.*\\.bin", RegexOption.IGNORE_CASE)

    /** What kind of storage [rel] (a path inside a save folder of [kind]) is part of (#70). */
    fun storageOf(kind: SaveKind, rel: String): Storage {
        if (isSaveState(rel)) return Storage.SAVE_STATE
        if (titleUnit(rel) != null) return Storage.TITLE_FOLDER
        val parts = rel.split('/')
        val name = parts.last()
        val ext = name.substringAfterLast('.', "").lowercase()
        if (parts.dropLast(1).any { it.lowercase().endsWith(".ps2") }) return Storage.FOLDER_CARD
        if (unitOf(kind, rel) != null) return Storage.FOLDER_SAVE
        // A Wii save exported from the System Menu: a packaged, encrypted archive — not a NAND folder.
        if (name.equals("data.bin", ignoreCase = true)) return Storage.EXPORT_ARCHIVE
        if (ext in CARD_EXTS || VMU_SAVE.matches(name)) return Storage.MEMORY_CARD_IMAGE
        if (ext in GAME_FILE_EXTS) return Storage.IN_GAME_FILE
        return Storage.UNKNOWN
    }
}
