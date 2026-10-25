package com.shelf.launcher.data

import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Computes the same game fingerprint RetroAchievements uses to recognise a
 * ROM or disc — the hash the emulator itself sends when you load a game.
 * Follows rcheevos' "rhash" rules per system:
 *
 *  - Cartridges: MD5 of the file, minus copier/emulator headers (NES iNES/FDS,
 *    SNES 512-byte, PC Engine 512-byte, Lynx, Atari 7800); N64 converted to
 *    big-endian first; .zip files hashed by their first entry.
 *  - Nintendo DS: header + ARM9 + ARM7 + icon, not the whole file.
 *  - Arcade: MD5 of the file name (no path, no extension).
 *  - PlayStation / PS2: MD5 of the boot executable's name + its contents,
 *    found through SYSTEM.CNF on the disc (BIN/CUE, ISO, IMG).
 *  - PSP: PARAM.SFO + EBOOT.BIN from an ISO.
 *  - Sega CD / Saturn: first 512 bytes of the disc.
 *
 * Disc formats: BIN/CUE, ISO, IMG and CHD (v5, see Chd.kt).
 *
 * Not handled here (they fall back to title matching, and can be given a
 * hash by hand): CSO, PBP, 7z, GameCube/Wii, Dreamcast, PC Engine CD.
 */
object RaHasher {

    /** Bumped whenever hashing learns something new (e.g. CHD support), so
     * games that didn't match by hash before are re-checked automatically. */
    const val VERSION = 2

    sealed class Result {
        data class Hashed(val md5: String) : Result()
        data class Unsupported(val reason: String) : Result()
        data class Failed(val reason: String) : Result()
    }

    private val cartridgePlatforms = setOf(
        "NES", "SNES", "N64", "GB", "GBC", "GBA", "NDS", "Genesis", "MasterSystem",
        "PCEngine", "Atari2600", "VirtualBoy", "WonderSwan", "NeoGeoPocket"
    )

    fun hash(romPath: String, platform: String): Result {
        return try {
        var file = File(romPath)
        if (file.extension.equals("m3u", true)) {
            file = firstM3uEntry(file) ?: return Result.Failed("Playlist is empty or unreadable")
        }
        if (!file.isFile) return Result.Failed("File not found")
        val ext = file.extension.lowercase()

        when {
            platform == "Arcade" || platform == "NeoGeo" -> Result.Hashed(md5(file.nameWithoutExtension.toByteArray()))
            ext in setOf("cso", "pbp", "7z", "rvz", "wbfs", "gcm", "gdi", "cdi") ->
                Result.Unsupported(".$ext files can't be hashed yet")
            platform == "PS1" -> DiscImage.open(file)?.use { hashPlayStation(it, ps2 = false) }
                ?: Result.Unsupported("Unrecognised disc image")
            platform == "PS2" -> DiscImage.open(file)?.use { hashPlayStation(it, ps2 = true) }
                ?: Result.Unsupported("Unrecognised disc image")
            platform == "PSP" -> DiscImage.open(file)?.use { hashPsp(it) }
                ?: Result.Unsupported("Unrecognised disc image")
            platform == "SegaCD" || platform == "Saturn" -> DiscImage.open(file)?.use { disc ->
                val header = disc.readSector(0).copyOf(512)
                Result.Hashed(md5(header))
            } ?: Result.Unsupported("Unrecognised disc image")
            platform in cartridgePlatforms -> hashCartridge(file, platform)
            else -> Result.Unsupported("No hashing rule for ${ConsoleNames.label(platform)} yet")
        }
    } catch (e: Exception) {
        Result.Failed(e.message ?: e::class.java.simpleName)
    } catch (e: OutOfMemoryError) {
        // (update 91) A huge ROM must not take the whole app down: it just isn't hashed.
        Result.Failed("File too large to hash")
    }
    }

    // ---- Cartridges ----

    /** Cartridge ROMs are small (N64/NDS at most a few hundred MB), and the
     * header rules need random access, so the file is read whole — except
     * NDS, which only hashes a few regions. */
    private fun hashCartridge(file: File, platform: String): Result {
        if (file.extension.equals("zip", true)) {
            ZipFile(file).use { zip ->
                val entry = zip.entries().asSequence().firstOrNull { !it.isDirectory }
                    ?: return Result.Failed("Zip file is empty")
                // (update 91) Zipped ROMs are unpacked into memory (three hash at once): a lower cap keeps that safe.
                if (entry.size > ZIP_CAP) return Result.Unsupported("Zipped ROM too large to hash (unzip it)")
                val bytes = zip.getInputStream(entry).use { it.readBytesCapped(ZIP_CAP) }
                return hashCartridgeBytes(bytes, platform)
            }
        }
        if (platform == "NDS") return RandomAccessFile(file, "r").use { hashNintendoDs(it) }
        return hashCartridgeBytes(file.inputStream().use { it.readBytesCapped() }, platform)
    }

    private fun hashCartridgeBytes(data: ByteArray, platform: String): Result {
        val md = MessageDigest.getInstance("MD5")
        when (platform) {
            "NES" -> {
                val headered = data.startsWith(byteArrayOf(0x4E, 0x45, 0x53, 0x1A)) ||  // "NES\x1A"
                    data.startsWith(byteArrayOf(0x46, 0x44, 0x53, 0x1A))                // "FDS\x1A"
                md.update(data, if (headered) 16 else 0, data.size - if (headered) 16 else 0)
            }
            "SNES" -> {
                val skip = if (data.size % 8192 == 512) 512 else 0
                md.update(data, skip, data.size - skip)
            }
            "PCEngine" -> {
                val skip = if (data.size % 1024 == 512) 512 else 0
                md.update(data, skip, data.size - skip)
            }
            "N64" -> md.update(toBigEndianN64(data))
            "NDS" -> return RandomAccessFileBytes(data).let { hashNintendoDs(it) }
            else -> {
                // Lynx and 7800 headers are only present in some dumps.
                val skip = when {
                    data.startsWith("LYNX".toByteArray()) -> 64
                    data.size > 128 && data[0] == 0x01.toByte() &&
                        String(data, 1, 9, Charsets.US_ASCII) == "ATARI7800" -> 128
                    else -> 0
                }
                md.update(data, skip, data.size - skip)
            }
        }
        return Result.Hashed(md.digest().toHex())
    }

    /** .z64 is big-endian (the canonical order), .v64 swaps each byte pair,
     * .n64 is little-endian 32-bit. Detected from the first four bytes. */
    private fun toBigEndianN64(data: ByteArray): ByteArray {
        if (data.size < 4) return data
        val b0 = data[0].toInt() and 0xFF
        return when (b0) {
            0x37 -> data.copyOf().also { out ->          // v64
                var i = 0
                while (i + 1 < out.size) { val t = out[i]; out[i] = out[i + 1]; out[i + 1] = t; i += 2 }
            }
            0x40 -> data.copyOf().also { out ->          // n64
                var i = 0
                while (i + 3 < out.size) {
                    val a = out[i]; val b = out[i + 1]
                    out[i] = out[i + 3]; out[i + 1] = out[i + 2]; out[i + 2] = b; out[i + 3] = a
                    i += 4
                }
            }
            else -> data                                  // z64 (0x80) or unknown
        }
    }

    /** Minimal random-access view so NDS hashing works for both plain files
     * and ROMs pulled out of a zip. */
    private interface RandomAccess {
        fun length(): Long
        fun readAt(offset: Long, size: Int): ByteArray
    }

    private class RandomAccessFileBytes(val data: ByteArray) : RandomAccess {
        override fun length() = data.size.toLong()
        override fun readAt(offset: Long, size: Int): ByteArray {
            if (offset >= data.size) return ByteArray(0)
            val end = minOf(data.size.toLong(), offset + size).toInt()
            return data.copyOfRange(offset.toInt(), end)
        }
    }

    private fun hashNintendoDs(file: RandomAccessFile): Result = hashNintendoDs(object : RandomAccess {
        override fun length() = file.length()
        override fun readAt(offset: Long, size: Int): ByteArray {
            if (offset >= file.length()) return ByteArray(0)
            val count = minOf(size.toLong(), file.length() - offset).toInt()
            val buf = ByteArray(count)
            file.seek(offset)
            file.readFully(buf)
            return buf
        }
    })

    private fun hashNintendoDs(rom: RandomAccess): Result {
        var header = rom.readAt(0, 0x200)
        if (header.size < 0x160) return Result.Failed("File too small for a DS ROM")
        var base = 0L
        // SuperCard dumps carry an extra 512-byte loader in front of the real header.
        if (header[0] == 0x2E.toByte() && header[1] == 0.toByte() && header[2] == 0.toByte() &&
            header[3] == 0xEA.toByte() && header[0xB0] == 0x44.toByte() && header[0xB1] == 0x46.toByte() &&
            header[0xB2] == 0x96.toByte() && header[0xB3] == 0.toByte()
        ) {
            base = 0x200
            header = rom.readAt(base, 0x200)
        }
        val arm9Addr = header.u32le(0x20); val arm9Size = header.u32le(0x2C)
        val arm7Addr = header.u32le(0x30); val arm7Size = header.u32le(0x3C)
        val iconAddr = header.u32le(0x68)
        if (arm9Size + arm7Size > 16L * 1024 * 1024) return Result.Failed("DS ROM header looks corrupt")

        val md = MessageDigest.getInstance("MD5")
        md.update(header, 0, 0x160)
        md.update(rom.readAt(base + arm9Addr, arm9Size.toInt()))
        md.update(rom.readAt(base + arm7Addr, arm7Size.toInt()))
        md.update(rom.readAt(base + iconAddr, 0xA00))
        return Result.Hashed(md.digest().toHex())
    }

    // ---- Discs ----

    private fun hashPlayStation(disc: DiscImage, ps2: Boolean): Result {
        val cnf = disc.findFile("SYSTEM.CNF")
        val exeName: String
        val exe: DiscImage.FileEntry
        if (cnf == null) {
            if (ps2) return Result.Failed("No SYSTEM.CNF on disc")
            exe = disc.findFile("PSX.EXE") ?: return Result.Failed("No SYSTEM.CNF or PSX.EXE on disc")
            exeName = "PSX.EXE"
        } else {
            val text = String(disc.readFile(cnf, maxBytes = 2048), Charsets.ISO_8859_1)
            exeName = bootExecutable(text, if (ps2) "BOOT2" else "BOOT", if (ps2) "cdrom0:" else "cdrom:")
                ?: return Result.Failed("SYSTEM.CNF has no boot entry")
            exe = disc.findFile(exeName) ?: return Result.Failed("Boot file $exeName not found on disc")
        }

        var size = exe.size
        if (!ps2) {
            // A PS-X EXE header states the real program size; RetroAchievements
            // hashes exactly that much (plus the 2 KB header), not the file size.
            val first = disc.readSector(exe.lba)
            if (String(first, 0, 7, Charsets.US_ASCII) == "PS-X EX") {
                size = first.u32le(28) + 2048
            }
        }
        val md = MessageDigest.getInstance("MD5")
        md.update(exeName.toByteArray(Charsets.ISO_8859_1))
        disc.streamFile(exe.lba, size) { chunk, len -> md.update(chunk, 0, len) }
        return Result.Hashed(md.digest().toHex())
    }

    /** Mirrors rcheevos: find `BOOT = cdrom:\PATH;1`, keep PATH exactly as
     * written (it's part of the hash), stop at whitespace or ';'. */
    internal fun bootExecutable(cnf: String, key: String, prefix: String): String? {
        var i = 0
        while (i < cnf.length) {
            if (cnf.regionMatches(i, key, 0, key.length)) {
                var p = i + key.length
                while (p < cnf.length && cnf[p].isWhitespace()) p++
                if (p < cnf.length && cnf[p] == '=') {
                    p++
                    while (p < cnf.length && cnf[p].isWhitespace()) p++
                    if (cnf.regionMatches(p, prefix, 0, prefix.length, ignoreCase = true)) p += prefix.length
                    while (p < cnf.length && cnf[p] == '\\') p++
                    val start = p
                    while (p < cnf.length && !cnf[p].isWhitespace() && cnf[p] != ';') p++
                    if (p > start) return cnf.substring(start, p)
                }
            }
            i++
        }
        return null
    }

    private fun hashPsp(disc: DiscImage): Result {
        val sfo = disc.findFile("PSP_GAME\\PARAM.SFO") ?: return Result.Failed("No PSP_GAME\\PARAM.SFO on disc")
        val eboot = disc.findFile("PSP_GAME\\SYSDIR\\EBOOT.BIN") ?: return Result.Failed("No EBOOT.BIN on disc")
        val md = MessageDigest.getInstance("MD5")
        disc.streamFile(sfo.lba, sfo.size) { chunk, len -> md.update(chunk, 0, len) }
        disc.streamFile(eboot.lba, eboot.size) { chunk, len -> md.update(chunk, 0, len) }
        return Result.Hashed(md.digest().toHex())
    }

    // ---- Helpers ----

    private fun firstM3uEntry(m3u: File): File? =
        m3u.readLines().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            ?.let { line -> File(line).let { if (it.isAbsolute) it else File(m3u.parentFile, line) } }

    /** The biggest zip entry read into memory for hashing. */
    private const val ZIP_CAP = 128L * 1024 * 1024

    /** Guard against pulling a multi-GB file into memory by mistake. */
    private fun InputStream.readBytesCapped(cap: Long = 512L * 1024 * 1024): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > cap) throw IllegalStateException("File too large to hash as a cartridge")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    internal fun md5(data: ByteArray): String = MessageDigest.getInstance("MD5").digest(data).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    internal fun ByteArray.u32le(offset: Int): Long =
        (this[offset].toLong() and 0xFF) or
            ((this[offset + 1].toLong() and 0xFF) shl 8) or
            ((this[offset + 2].toLong() and 0xFF) shl 16) or
            ((this[offset + 3].toLong() and 0xFF) shl 24)
}

/**
 * Reads the data track of a CD/DVD image (ISO, raw BIN, CUE sheet) and walks
 * its ISO 9660 file system. Only what hashing needs: find a file by path and
 * read its sectors.
 */
internal abstract class DiscImage : java.io.Closeable {

    data class FileEntry(val lba: Long, val size: Long, val isDirectory: Boolean)

    /** The 2048 user-data bytes of one sector of the first data track. */
    abstract fun readSector(lba: Long): ByteArray

    fun readFile(entry: FileEntry, maxBytes: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        streamFile(entry.lba, minOf(entry.size, maxBytes.toLong())) { chunk, len -> out.write(chunk, 0, len) }
        return out.toByteArray()
    }

    fun streamFile(lba: Long, size: Long, sink: (ByteArray, Int) -> Unit) {
        var remaining = size
        var sector = lba
        while (remaining > 0) {
            val data = readSector(sector++)
            val n = minOf(remaining, 2048L).toInt()
            sink(data, n)
            remaining -= n
        }
    }

    /** Path components separated by '\' or '/'. Names compare
     * case-insensitively, ignoring the ";1" version suffix. */
    fun findFile(path: String): FileEntry? {
        val pvd = readSector(16)
        if (String(pvd, 1, 5, Charsets.US_ASCII) != "CD001") return null
        var dir = FileEntry(pvd.u32(156 + 2), pvd.u32(156 + 10), true)
        val parts = path.split('\\', '/').filter { it.isNotEmpty() }
        for ((index, part) in parts.withIndex()) {
            val found = findInDirectory(dir, part) ?: return null
            if (index < parts.lastIndex && !found.isDirectory) return null
            dir = found
        }
        return dir
    }

    private fun findInDirectory(dir: FileEntry, name: String): FileEntry? {
        val sectors = ((dir.size + 2047) / 2048).coerceAtMost(256)
        for (s in 0 until sectors) {
            val data = readSector(dir.lba + s)
            var off = 0
            while (off < 2048) {
                val len = data[off].toInt() and 0xFF
                if (len == 0) break  // rest of this sector is padding
                if (off + 33 > 2048) break
                val nameLen = data[off + 32].toInt() and 0xFF
                if (off + 33 + nameLen <= 2048 && nameLen > 0) {
                    val raw = String(data, off + 33, nameLen, Charsets.ISO_8859_1)
                    val clean = raw.substringBefore(';').trimEnd('.')
                    if (clean.equals(name, ignoreCase = true)) {
                        val flags = data[off + 25].toInt()
                        return FileEntry(data.u32(off + 2), data.u32(off + 10), flags and 0x02 != 0)
                    }
                }
                off += len
            }
        }
        return null
    }

    private fun ByteArray.u32(offset: Int): Long =
        (this[offset].toLong() and 0xFF) or
            ((this[offset + 1].toLong() and 0xFF) shl 8) or
            ((this[offset + 2].toLong() and 0xFF) shl 16) or
            ((this[offset + 3].toLong() and 0xFF) shl 24)

    companion object {
        /** Opens a disc image's first data track. CUE sheets are followed to
         * their first BIN; raw 2352-byte sectors are detected by their sync
         * pattern, so ".iso" files that are really raw dumps still work. */
        fun open(file: File): DiscImage? {
            if (file.extension.equals("chd", true)) return ChdDiscImage.open(file)
            var target = file
            var cueMode: String? = null
            var pregapBytes = 0L
            if (file.extension.equals("cue", true)) {
                val cue = parseCue(file) ?: return null
                target = cue.first
                cueMode = cue.second
                pregapBytes = cue.third
            }
            if (!target.isFile) return null
            val raf = RandomAccessFile(target, "r")

            val (sectorSize, dataOffset) = try {
                when (cueMode?.uppercase()) {
                    "MODE1/2048" -> 2048 to 0
                    "MODE1/2352" -> 2352 to 16
                    "MODE2/2352" -> 2352 to 24
                    "MODE2/2336" -> 2336 to 8
                    else -> detectLayout(raf)
                }
            } catch (e: Exception) {
                // A read error here would otherwise leave the file open for good (one per library scan).
                raf.close()
                throw e
            }
            val start = if (pregapBytes > 0) pregapBytes * sectorSize / 2352 else 0L
            return RawDiscImage(raf, start, sectorSize, dataOffset)
        }

        private fun detectLayout(raf: RandomAccessFile): Pair<Int, Int> {
            val head = ByteArray(16)
            raf.seek(0)
            if (raf.read(head) < 16) return 2048 to 0
            val sync = head[0] == 0.toByte() && (1..10).all { head[it] == 0xFF.toByte() } && head[11] == 0.toByte()
            if (!sync) return 2048 to 0
            return if (head[15].toInt() == 2) 2352 to 24 else 2352 to 16
        }

        /** Returns (first BIN file, first track mode, byte offset of track 1's INDEX 01). */
        private fun parseCue(cue: File): Triple<File, String?, Long>? {
            var binFile: File? = null
            var mode: String? = null
            var index01Frames = 0L
            var inFirstTrack = false
            cue.readLines().forEach { rawLine ->
                val line = rawLine.trim()
                when {
                    line.startsWith("FILE", true) && binFile == null -> {
                        val name = Regex("\"(.+)\"").find(line)?.groupValues?.get(1)
                            ?: line.split(Regex("\\s+")).getOrNull(1)
                        if (name != null) binFile = File(cue.parentFile, name)
                    }
                    line.startsWith("TRACK", true) -> {
                        if (mode == null) {
                            mode = line.split(Regex("\\s+")).getOrNull(2)
                            inFirstTrack = true
                        } else inFirstTrack = false
                    }
                    line.startsWith("INDEX 01", true) && inFirstTrack -> {
                        val parts = line.split(Regex("\\s+")).getOrNull(2)?.split(":")
                        if (parts != null && parts.size == 3) {
                            index01Frames = (parts[0].toLong() * 60 + parts[1].toLong()) * 75 + parts[2].toLong()
                        }
                    }
                }
            }
            val bin = binFile ?: return null
            return Triple(bin, mode, index01Frames * 2352)
        }
    }
}

/** BIN / ISO / IMG images: plain sectors on disk, 2048 or 2352 bytes each. */
internal class RawDiscImage(
    private val raf: RandomAccessFile,
    private val trackStart: Long,
    private val sectorSize: Int,
    private val dataOffset: Int
) : DiscImage() {

    override fun readSector(lba: Long): ByteArray {
        val buf = ByteArray(2048)
        val pos = trackStart + lba * sectorSize + dataOffset
        if (pos >= raf.length()) return buf
        raf.seek(pos)
        raf.read(buf, 0, minOf(2048L, raf.length() - pos).toInt())
        return buf
    }

    override fun close() = raf.close()
}
