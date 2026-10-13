package com.shelf.launcher.data

import java.io.File

/**
 * Walks a folder tree looking for files whose extension matches a known
 * platform, and turns each into a Game entry. This is deliberately simple —
 * extension-based platform detection and filename-derived titles — rather
 * than trying to match against a games database. Good enough to get a real
 * library on screen; swap in per-file metadata lookups later if you want
 * cleaner titles than "file name, underscores swapped for spaces."
 *
 * Multi-disc games (mainly PS1) are collapsed into a single entry using the
 * .m3u playlist format RetroArch and most PS1 cores already understand
 * natively for in-game disc swapping — this scanner doesn't invent its own
 * disc-switching, it just makes sure an .m3u exists and points at that
 * instead of individual disc files. Three cases:
 *   1. An .m3u already exists in the folder — its listed discs become one
 *      Game entry, and those disc files are excluded from the rest of the
 *      scan so they don't also show up individually.
 *   2. No .m3u exists, but files share a title with only a "(Disc N)" /
 *      "(Disk N)" / "(CD N)" style suffix differing — one is generated
 *      on disk next to the discs (only if that filename isn't already
 *      taken by something unrelated) and used as the entry.
 *   3. Anything left over is scanned as a normal single-file game, same as
 *      before.
 */
object RomScanner {

    /** Extensions that identify a system on their own. */
    private val extensionToPlatform = mapOf(
        "nes" to "NES", "fds" to "NES",
        "sfc" to "SNES", "smc" to "SNES",
        "gba" to "GBA",
        "gb" to "GB", "gbc" to "GBC",
        "z64" to "N64", "n64" to "N64", "v64" to "N64",
        "nds" to "NDS",
        "3ds" to "3DS", "cia" to "3DS",
        "gcm" to "GameCube", "rvz" to "GameCube",
        "wbfs" to "Wii",
        "cso" to "PSP",
        "gdi" to "Dreamcast", "cdi" to "Dreamcast",
        "md" to "Genesis", "gen" to "Genesis", "smd" to "Genesis",
        "sms" to "MasterSystem",
        "pce" to "PCEngine",
        "a26" to "Atari2600",
        "vb" to "VirtualBoy",
        "ws" to "WonderSwan", "wsc" to "WonderSwan",
        "ngp" to "NeoGeoPocket", "ngc" to "NeoGeoPocket",
        "nsp" to "Switch", "xci" to "Switch",
        "wua" to "WiiU", "wux" to "WiiU", "rpx" to "WiiU",
        "gg" to "GameGear", "32x" to "32X",
        "lnx" to "Lynx", "j64" to "Jaguar", "a78" to "Atari7800",
        "vpk" to "PSVita", "p8" to "PICO8", "min" to "PokemonMini",
        "col" to "ColecoVision", "int" to "Intellivision", "vec" to "Vectrex",
        "droiddeck" to "DroidDeck",
    )

    /**
     * Extensions shared by several systems. A .chd could be PS1, PS2, Saturn,
     * Dreamcast or PC Engine CD; a .iso could be PS2, PSP, GameCube or Wii.
     * These can't be resolved by extension alone — the containing folder name
     * decides, which is how ROM collections are organised in practice and how
     * other frontends handle it.
     */
    private val ambiguousExtensions = setOf(
        "chd", "iso", "bin", "cue", "img", "mdf", "nrg", "ccd", "pbp", "zip", "7z"
    )

    /**
     * Folder names mapped to systems. Matched case-insensitively against the
     * ROM's parent folder (and its parents, walking up), so
     * /ROMs/PS2/game.chd resolves correctly where the extension can't.
     */
    private val folderAliasToPlatform: Map<String, String> = mapOf(
        "ps1" to "PS1", "psx" to "PS1", "playstation" to "PS1", "psone" to "PS1",
        "sony playstation" to "PS1",
        "ps2" to "PS2", "playstation2" to "PS2", "playstation 2" to "PS2",
        "sony playstation 2" to "PS2",
        "psp" to "PSP", "playstation portable" to "PSP",
        "psvita" to "PSVita", "vita" to "PSVita",
        "gc" to "GameCube", "gamecube" to "GameCube", "ngc" to "GameCube",
        "wii" to "Wii",
        "dc" to "Dreamcast", "dreamcast" to "Dreamcast",
        "saturn" to "Saturn", "segasaturn" to "Saturn",
        "segacd" to "SegaCD", "megacd" to "SegaCD",
        "pcecd" to "PCEngine", "pcenginecd" to "PCEngine",
        "arcade" to "Arcade", "mame" to "Arcade", "fbneo" to "Arcade", "fba" to "Arcade",
        "neogeo" to "NeoGeo",
        "nes" to "NES", "famicom" to "NES",
        "snes" to "SNES", "superfamicom" to "SNES", "sfc" to "SNES",
        "n64" to "N64", "nintendo64" to "N64",
        "gba" to "GBA", "gameboyadvance" to "GBA",
        "gb" to "GB", "gameboy" to "GB",
        "gbc" to "GBC", "gameboycolor" to "GBC",
        "nds" to "NDS", "ds" to "NDS",
        "3ds" to "3DS",
        "genesis" to "Genesis", "megadrive" to "Genesis", "md" to "Genesis",
        "mastersystem" to "MasterSystem", "sms" to "MasterSystem",
        "ps3" to "PS3", "playstation3" to "PS3", "playstation 3" to "PS3",
        "ps4" to "PS4", "playstation4" to "PS4", "playstation 4" to "PS4",
        "switch" to "Switch", "nintendoswitch" to "Switch", "nintendo switch" to "Switch",
        "wiiu" to "WiiU", "wii u" to "WiiU",
        "xbox" to "Xbox", "xbox360" to "Xbox360", "xbox 360" to "Xbox360",
        "gamegear" to "GameGear", "gg" to "GameGear", "game gear" to "GameGear",
        "32x" to "32X", "sega32x" to "32X",
        "naomi" to "Naomi", "atomiswave" to "Atomiswave",
        "neogeocd" to "NeoGeoCD",
        "pcengine" to "PCEngine", "tg16" to "PCEngine", "turbografx16" to "PCEngine",
        "atari2600" to "Atari2600", "atari7800" to "Atari7800", "lynx" to "Lynx", "jaguar" to "Jaguar",
        "3do" to "3DO", "dos" to "DOS", "scummvm" to "ScummVM", "msx" to "MSX",
        "c64" to "C64", "commodore64" to "C64", "amiga" to "Amiga",
        "pico8" to "PICO8", "pico-8" to "PICO8", "pokemini" to "PokemonMini",
        "colecovision" to "ColecoVision", "intellivision" to "Intellivision", "vectrex" to "Vectrex",
        "virtualboy" to "VirtualBoy", "vb" to "VirtualBoy",
        "wonderswan" to "WonderSwan", "ngp" to "NeoGeoPocket", "neogeopocket" to "NeoGeoPocket",
    )

    /** The Voltcap console a folder name stands for ("psx", "Game Boy Advance"…), or null (RomM downloads, update 93). */
    fun platformForFolderName(name: String): String? {
        val n = name.trim().lowercase()
        return folderAliasToPlatform[n] ?: folderAliasToPlatform[n.replace(" ", "").replace("-", "").replace("_", "")]
    }

    /**
     * Resolves a file to a system: folder name first (the only thing that can
     * disambiguate shared disc formats), then extension, then nothing.
     */
    private fun platformFor(file: File, rootPath: String, deep: Boolean = false): String? {
        val ext = file.extension.lowercase()
        val root = File(rootPath).absoluteFile

        // Deep search: a console folder anywhere on the way down counts, the
        // nearest one first — ROMs/Nintendo/SNES/game.sfc is SNES, not
        // "Nintendo"; ROMs/Sony/PS2/Japan/game.chd is PS2.
        if (deep) {
            var dir: File? = file.absoluteFile.parentFile
            while (dir != null && dir.absolutePath.startsWith(root.absolutePath) && dir.absolutePath != root.absolutePath) {
                val name = dir.name.lowercase().replace('_', ' ').replace('-', ' ').trim()
                val known = folderAliasToPlatform[name] ?: folderAliasToPlatform[name.replace(" ", "")]
                if (known != null) return known
                dir = dir.parentFile
            }
        }

        // The top-level folder under the scan root IS the console, by
        // convention: ROMs/PS2/game.chd, ROMs/Nintendo 64/game.z64. That's how
        // ROM collections are organised, and it's the only thing that can tell
        // a PS1 .chd from a PS2 .chd.
        val topFolder = topLevelFolderUnder(file, root)
        if (topFolder != null) {
            val name = topFolder.lowercase().replace('_', ' ').replace('-', ' ').trim()
            val known = folderAliasToPlatform[name] ?: folderAliasToPlatform[name.replace(" ", "")]
            // A recognised alias gets the canonical label ("ps2" -> "PS2");
            // anything else keeps the folder's own name, so an arbitrary
            // collection layout still groups correctly instead of collapsing
            // into "Disc".
            if (known != null) return known
            if (isLikelyRom(ext)) return topFolder
        }

        extensionToPlatform[ext]?.let { return it }
        if (ext in ambiguousExtensions) return "Disc"
        return null
    }

    /** Name of the first folder below [root] on this file's path, or null if
     * the file sits directly in the root. */
    private fun topLevelFolderUnder(file: File, root: File): String? {
        var candidate: File? = file.absoluteFile.parentFile
        var lastName: String? = null
        while (candidate != null && candidate.absolutePath.startsWith(root.absolutePath)) {
            if (candidate.absolutePath == root.absolutePath) return lastName
            lastName = candidate.name
            candidate = candidate.parentFile
        }
        return lastName
    }

    /** Guards against every stray folder becoming a "console" — only files
     * that look like ROMs adopt their folder's name as a platform. */
    private fun isLikelyRom(ext: String): Boolean =
        ext.isNotBlank() && (ext in ambiguousExtensions || extensionToPlatform.containsKey(ext) ||
            ext in setOf("rom", "col", "int", "a78", "lnx", "32x", "sg", "pc2", "min", "vec"))

    private val discPattern = Regex("""(?i)[\(\[]\s*(?:disc|disk|cd)\s*(\d+)(?:\s*(?:of|/)\s*\d+)?\s*[\)\]]""")

    data class ScanResult(
        val games: List<Game>,
        val skippedExtensions: Set<String>,
        val multiDiscGroupsCreated: Int = 0,
        val multiDiscWriteFailures: Int = 0,
        /** Folders that don't exist or can't be read. */
        val unreadable: List<String> = emptyList()
    )

    /**
     * Every ROM folder at once. A game found through two folders (one inside
     * the other) counts once. Folders that can't be read are reported.
     */
    fun scanAll(rootPaths: List<String>, deep: Boolean): ScanResult {
        val results = rootPaths.map { scan(it, deep) }
        val games = results.flatMap { it.games }.distinctBy { it.romPath }.sortedBy { it.name }
        return ScanResult(
            games,
            results.flatMap { it.skippedExtensions }.toSet(),
            results.sumOf { it.multiDiscGroupsCreated },
            results.sumOf { it.multiDiscWriteFailures },
            unreadable = rootPaths.filterIndexed { i, _ -> results[i].unreadable.isNotEmpty() }
        )
    }

    /**
     * Update 175: files that are never a game, whatever folder they're in: pictures, texts, a disc's system files
     * (PARAM.SFO, PS3_DISC.SFB, an update's .PUP), checksums and the like.
     */
    private val notGames = setOf(
        "txt", "nfo", "diz", "pdf", "htm", "html", "url", "xml", "json", "ini", "cfg", "log",
        "png", "jpg", "jpeg", "gif", "bmp", "webp", "ico",
        "sfo", "sfb", "pup", "edat", "rap", "sprx", "self", "sdat",
        "md5", "sha1", "sfv", "crc", "db", "ds_store", "tmp", "part", "torrent"
    )

    /** Update 175: a folder that is a whole game (a PS3 disc or game folder). */
    private fun isFolderGame(dir: File): Boolean =
        File(dir, "PS3_GAME").isDirectory || File(dir, "PS3_DISC.SFB").isFile

    /** Update 175: a folder game's console: PS3 for a PS3 game folder, else its folders' console. */
    private fun folderGamePlatform(dir: File, rootPath: String, deep: Boolean): String? =
        if (File(dir, "PS3_GAME").isDirectory || File(dir, "PS3_DISC.SFB").isFile) "PS3"
        else platformFor(File(dir, "x.iso"), rootPath, deep)

    /** Update 175: a folder game's name: its PARAM.SFO title, else the folder's name without its serial or ".iso". */
    private fun folderGameTitle(dir: File): String {
        val sfo = File(dir, "PS3_GAME/PARAM.SFO")
        val title = runCatching { if (sfo.isFile && sfo.length() < 64_000) SaveCapsules.sfoTitle(sfo.readBytes()) else null }.getOrNull()
        if (!title.isNullOrBlank()) return title.replace('\n', ' ').trim()
        val raw = dir.name.removeSuffix(".iso").removeSuffix(".ISO").removeSuffix(".ps3").removeSuffix(".PS3")
            .replace(Regex("""\s*\[?\b[A-Z]{4}\d{5}\b\]?"""), "").trim()
        return prettifyName(raw.ifBlank { dir.name })
    }

    /** Folders never worth walking: hidden ones and Android's app-data trees. */
    private fun skipFolder(dir: File, root: File): Boolean {
        if (dir.absolutePath == root.absolutePath) return false
        val name = dir.name
        if (name.startsWith(".")) return true
        // /storage/emulated/0/Android (data, obb, media of every app).
        if (name == "Android" && (File(dir, "data").isDirectory || File(dir, "obb").isDirectory)) return true
        return false
    }

    fun scan(rootPath: String, deep: Boolean = false): ScanResult {
        val root = File(rootPath)
        if (!root.exists() || !root.isDirectory) {
            return ScanResult(emptyList(), emptySet(), unreadable = listOf(rootPath))
        }
        val absRoot = root.absoluteFile

        val knownFiles = mutableListOf<File>()
        val m3uFiles = mutableListOf<File>()
        val skipped = mutableSetOf<String>()
        // Update 175: games that are a whole folder (a PS3 game: PS3_GAME / PS3_DISC.SFB inside) — one game each, its
        // folder not walked (every file in it — EBOOT.BIN, ICON0.PNG, PARAM.SFO, the DAT files — was listed as a game).
        val folderGames = mutableListOf<File>()

        root.walkTopDown()
            .onEnter { dir ->
                if (skipFolder(dir.absoluteFile, absRoot)) false
                else if (dir.absolutePath != absRoot.absolutePath && isFolderGame(dir)) { folderGames += dir; false }
                else true
            }
            .filter { it.isFile }
            .forEach { file ->
                val ext = file.extension.lowercase()
                when {
                    ext == "m3u" -> m3uFiles += file
                    // (Update 175: never a picture, a text or a disc's system file — in a console's folder every file
                    // used to count as one of its games.)
                    ext in notGames -> {}
                    platformFor(file, rootPath, deep) != null -> knownFiles += file
                    ext.isNotBlank() -> skipped += ext
                }
            }

        val games = mutableListOf<Game>()
        folderGames.forEach { dir ->
            val platform = folderGamePlatform(dir, rootPath, deep) ?: return@forEach
            games += Game(
                name = folderGameTitle(dir),
                platform = platform,
                hue = hueFor(dir.name),
                sourceType = SourceType.ROM,
                romPath = dir.absolutePath
            )
        }
        val consumedPaths = mutableSetOf<String>()

        // The track files a .cue / .gdi sheet points at (Game.bin, Game (Track 2).bin…) are part of that
        // disc, not games of their own: only the sheet is listed, as emulators expect.
        knownFiles.filter { it.extension.equals("cue", true) || it.extension.equals("gdi", true) }.forEach { sheet ->
            sheetTracks(sheet).forEach { consumedPaths += it.absolutePath }
        }
        consumedPaths.removeAll(knownFiles.filter { it.extension.equals("cue", true) || it.extension.equals("gdi", true) }
            .map { it.absolutePath }.toSet())

        // Pass 1: an existing .m3u playlist is authoritative for whatever it lists.
        m3uFiles.forEach { m3u ->
            val discPaths = parseM3u(m3u)
            val platform = discPaths.firstOrNull()?.let { platformFor(it, rootPath, deep) }
            if (platform != null && discPaths.isNotEmpty()) {
                games += Game(
                    name = prettifyName(m3u.nameWithoutExtension),
                    platform = platform,
                    hue = hueFor(m3u.name),
                    sourceType = SourceType.ROM,
                    romPath = m3u.absolutePath
                )
                discPaths.forEach { consumedPaths += it.absolutePath }
            }
        }

        // Pass 2: group whatever's left by (folder, title with disc marker
        // stripped, extension) to find undeclared multi-disc sets.
        val remaining = knownFiles.filterNot { it.absolutePath in consumedPaths }
        val discGroups = LinkedHashMap<String, MutableList<Pair<Int, File>>>()
        val discGroupTitles = mutableMapOf<String, String>()
        val singleFiles = mutableListOf<File>()

        remaining.forEach { file ->
            val match = discPattern.find(file.nameWithoutExtension)
            val discNumber = match?.groupValues?.get(1)?.toIntOrNull()
            if (match == null || discNumber == null) {
                singleFiles += file
                return@forEach
            }
            val title = cleanupTitle(file.nameWithoutExtension.removeRange(match.range))
            val groupKey = "${file.parent}|${title.lowercase()}|${file.extension.lowercase()}"
            discGroups.getOrPut(groupKey) { mutableListOf() } += discNumber to file
            discGroupTitles.putIfAbsent(groupKey, title)
        }

        var groupsCreated = 0
        var groupWriteFailures = 0

        discGroups.forEach { (groupKey, discs) ->
            if (discs.size < 2) {
                // Only one file actually matched this title — not a real
                // multi-disc set, so don't strip its name; scan it normally.
                singleFiles += discs.map { it.second }
                return@forEach
            }

            val sortedDiscs = discs.sortedBy { it.first }
            val firstFile = sortedDiscs.first().second
            val parentDir = firstFile.parentFile
            val title = discGroupTitles[groupKey] ?: firstFile.nameWithoutExtension
            val platform = platformFor(firstFile, rootPath, deep)

            if (parentDir == null || platform == null) {
                singleFiles += sortedDiscs.map { it.second }
                return@forEach
            }

            val m3uFile = File(parentDir, "$title.m3u")
            if (m3uFile.exists()) {
                // Something's already at this path that Pass 1 didn't parse
                // as covering these discs — don't overwrite or guess, just
                // fall back to listing the discs individually.
                singleFiles += sortedDiscs.map { it.second }
                return@forEach
            }

            val written = try {
                m3uFile.writeText(sortedDiscs.joinToString("\n") { it.second.name } + "\n")
                true
            } catch (e: Exception) {
                false
            }

            if (written) {
                games += Game(
                    name = prettifyName(title),
                    platform = platform,
                    hue = hueFor(title),
                    sourceType = SourceType.ROM,
                    romPath = m3uFile.absolutePath
                )
                groupsCreated++
            } else {
                groupWriteFailures++
                singleFiles += sortedDiscs.map { it.second }
            }
        }

        singleFiles.forEach { file ->
            val platform = platformFor(file, rootPath, deep) ?: return@forEach
            games += Game(
                name = prettifyName(file.nameWithoutExtension),
                platform = platform,
                hue = hueFor(file.name),
                sourceType = SourceType.ROM,
                romPath = file.absolutePath
            )
        }

        return ScanResult(games.sortedBy { it.name }, skipped, groupsCreated, groupWriteFailures)
    }

    /** Files a CUE sheet (`FILE "Game (Track 1).bin" BINARY`) or a GDI list (`1 0 4 2352 track01.bin 0`) names. */
    private fun sheetTracks(sheet: File): List<File> = try {
        val dir = sheet.parentFile
        val lines = sheet.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        if (sheet.extension.equals("cue", true)) {
            lines.filter { it.startsWith("FILE", ignoreCase = true) }.mapNotNull { line ->
                val name = Regex("\"(.+)\"").find(line)?.groupValues?.get(1) ?: line.split(Regex("\\s+")).getOrNull(1)
                name?.let { File(dir, it.substringAfterLast('/').substringAfterLast('\\')) }
            }
        } else {
            // First line is the track count; then "<n> <lba> <type> <sector size> <file> <offset>" (file may be quoted).
            lines.drop(1).mapNotNull { line ->
                val name = Regex("\"(.+)\"").find(line)?.groupValues?.get(1) ?: line.split(Regex("\\s+")).getOrNull(4)
                name?.let { File(dir, it) }
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun parseM3u(m3u: File): List<File> = try {
        m3u.readLines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line -> File(line).let { if (it.isAbsolute) it else File(m3u.parentFile, line) } }
    } catch (e: Exception) {
        emptyList()
    }

    private fun cleanupTitle(raw: String): String =
        raw.replace(Regex("""\s+"""), " ")
            // Deliberately does NOT strip brackets: removing a mid-name disc
            // marker from "Parasite Eve II (Disc 1) (USA)" leaves a trailing
            // ")" that belongs to the region tag, and stripping it produced
            // mangled titles like "Parasite Eve II (USA" — which also became
            // the generated .m3u filename.
            .trim(' ', '-', '_')
            .trim()

    private fun prettifyName(raw: String): String =
        raw.replace('_', ' ').replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

    /** Deterministic placeholder-gradient hue from the filename, so the same
     * ROM gets the same color across rescans until real art loads. */
    private fun hueFor(seed: String): Int = (seed.hashCode() and 0x7fffffff) % 360
}
