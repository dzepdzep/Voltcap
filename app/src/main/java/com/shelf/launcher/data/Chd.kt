package com.shelf.launcher.data

import org.tukaani.xz.LZMAInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.zip.Inflater

/**
 * Reads CHD v5 disc images (MAME's compressed format, made with `chdman`)
 * well enough to hash them for RetroAchievements.
 *
 * A CHD stores the disc in "hunks" of a few frames each, every hunk
 * compressed on its own. A Huffman-coded map at the front says how each hunk
 * is stored. Hashing only needs a handful of sectors (SYSTEM.CNF and the boot
 * executable), so only the hunks holding those are ever decompressed — a
 * 600 MB PS1 image costs a few hundred KB of work.
 *
 * Supported: CD images (cdlz / cdzl — LZMA and zlib) and DVD-style images
 * (lzma / zlib), plus uncompressed and self-referencing hunks. Not supported:
 * FLAC- or Huffman-coded hunks (audio, rarely data), and CHDs that depend on
 * a separate parent file. Follows MAME's chd.cpp / chdcodec.cpp / huffman.cpp.
 */
internal class ChdDiscImage private constructor(
    private val raf: RandomAccessFile,
    private val header: Header,
    private val map: Array<MapEntry>
) : DiscImage() {

    private class Header(
        val compressors: IntArray,
        val logicalBytes: Long,
        val mapOffset: Long,
        val hunkBytes: Int,
        val unitBytes: Int
    ) {
        val hunkCount: Int get() = ((logicalBytes + hunkBytes - 1) / hunkBytes).toInt()
        /** CD images store 2448-byte frames (2352 sector + 96 subcode). */
        val isCd: Boolean get() = unitBytes == CD_FRAME_SIZE
    }

    private class MapEntry(val type: Int, val length: Int, val offset: Long)

    private var cachedHunk = -1
    private var cachedData = ByteArray(0)

    override fun readSector(lba: Long): ByteArray {
        val out = ByteArray(2048)
        if (header.isCd) {
            val framesPerHunk = header.hunkBytes / CD_FRAME_SIZE
            val hunkIndex = (lba / framesPerHunk).toInt()
            if (hunkIndex >= map.size) return out
            val data = hunk(hunkIndex)
            val frame = ((lba % framesPerHunk) * CD_FRAME_SIZE).toInt()
            // Raw sectors start with a sync pattern; the mode byte then says
            // where the 2048 user bytes begin (16 for mode 1, 24 for mode 2).
            val raw = data[frame] == 0.toByte() &&
                (1..10).all { data[frame + it] == 0xFF.toByte() } &&
                data[frame + 11] == 0.toByte()
            val userOffset = when {
                !raw -> 0
                data[frame + 15].toInt() == 2 -> 24
                else -> 16
            }
            System.arraycopy(data, frame + userOffset, out, 0, 2048)
        } else {
            val position = lba * 2048
            val hunkIndex = (position / header.hunkBytes).toInt()
            if (hunkIndex >= map.size) return out
            val data = hunk(hunkIndex)
            val within = (position % header.hunkBytes).toInt()
            System.arraycopy(data, within, out, 0, minOf(2048, data.size - within))
        }
        return out
    }

    private fun hunk(index: Int): ByteArray {
        if (index != cachedHunk) {
            cachedData = decodeHunk(index, depth = 0)
            cachedHunk = index
        }
        return cachedData
    }

    private fun decodeHunk(index: Int, depth: Int): ByteArray {
        val entry = map[index]
        return when (entry.type) {
            0, 1, 2, 3 -> decompress(header.compressors[entry.type], readAt(raf, entry.offset, entry.length))
            TYPE_NONE -> readAt(raf, entry.offset, header.hunkBytes)
            TYPE_ZERO -> ByteArray(header.hunkBytes)
            TYPE_SELF -> {
                if (depth > 16) throw IOException("CHD hunk references loop")
                decodeHunk(entry.offset.toInt(), depth + 1)
            }
            TYPE_PARENT -> throw IOException("This CHD needs its parent CHD file, which isn't supported")
            else -> throw IOException("Unknown CHD hunk type ${entry.type}")
        }
    }

    private fun decompress(codec: Int, src: ByteArray): ByteArray = when (codec) {
        CODEC_ZLIB -> inflate(src, 0, src.size, header.hunkBytes)
        CODEC_LZMA -> lzma(src, 0, src.size, header.hunkBytes)
        CODEC_CD_ZLIB -> cdHunk(src) { s, off, len, size -> inflate(s, off, len, size) }
        CODEC_CD_LZMA -> cdHunk(src) { s, off, len, size -> lzma(s, off, len, size) }
        else -> throw IOException(
            "This CHD stores part of the disc with the '${fourcc(codec)}' codec, which Voltcap can't read yet"
        )
    }

    /**
     * CD hunks: a bitmap of frames whose sync/ECC was stripped, the length of
     * the compressed sector data, then sector data and subcode compressed
     * separately. Subcode isn't needed for hashing, so it's skipped. Stripped
     * sync patterns are put back so raw-sector detection keeps working; the
     * ECC bytes aren't, since they sit outside the 2048 user bytes.
     */
    private inline fun cdHunk(src: ByteArray, base: (ByteArray, Int, Int, Int) -> ByteArray): ByteArray {
        val frames = header.hunkBytes / CD_FRAME_SIZE
        val lengthBytes = if (header.hunkBytes < 65536) 2 else 3
        val eccBytes = (frames + 7) / 8
        val headerBytes = eccBytes + lengthBytes
        var baseLength = ((src[eccBytes].toInt() and 0xFF) shl 8) or (src[eccBytes + 1].toInt() and 0xFF)
        if (lengthBytes > 2) baseLength = (baseLength shl 8) or (src[eccBytes + 2].toInt() and 0xFF)

        val sectors = base(src, headerBytes, baseLength, frames * CD_SECTOR_SIZE)
        val out = ByteArray(header.hunkBytes)
        for (frame in 0 until frames) {
            System.arraycopy(sectors, frame * CD_SECTOR_SIZE, out, frame * CD_FRAME_SIZE, CD_SECTOR_SIZE)
            if ((src[frame / 8].toInt() shr (frame % 8)) and 1 != 0) {
                System.arraycopy(CD_SYNC, 0, out, frame * CD_FRAME_SIZE, CD_SYNC.size)
            }
        }
        return out
    }

    override fun close() = raf.close()

    companion object {
        private const val CD_FRAME_SIZE = 2448
        private const val CD_SECTOR_SIZE = 2352
        private val CD_SYNC = byteArrayOf(
            0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00
        )

        private fun tag(s: String): Int =
            (s[0].code shl 24) or (s[1].code shl 16) or (s[2].code shl 8) or s[3].code

        private val CODEC_ZLIB = tag("zlib")
        private val CODEC_LZMA = tag("lzma")
        private val CODEC_CD_ZLIB = tag("cdzl")
        private val CODEC_CD_LZMA = tag("cdlz")

        // Map entry types, as in MAME's chd.h.
        private const val TYPE_NONE = 4
        private const val TYPE_SELF = 5
        private const val TYPE_PARENT = 6
        private const val RLE_SMALL = 7
        private const val RLE_LARGE = 8
        private const val SELF_0 = 9
        private const val SELF_1 = 10
        private const val PARENT_SELF = 11
        private const val PARENT_0 = 12
        private const val PARENT_1 = 13
        private const val TYPE_ZERO = -1 // our own: an unallocated hunk in an uncompressed CHD

        // CHD's LZMA settings: lc=3, lp=0, pb=2 → properties byte (pb*5+lp)*9+lc.
        private const val LZMA_PROPS: Byte = 93

        fun open(file: File): ChdDiscImage {
            val raf = RandomAccessFile(file, "r")
            try {
                val h = readAt(raf, 0, 124)
                if (String(h, 0, 8, Charsets.US_ASCII) != "MComprHD") throw IOException("Not a CHD file")
                val version = u32be(h, 12)
                if (version != 5L) {
                    throw IOException("Only CHD version 5 is supported (this one is v$version) — re-create it with a current chdman")
                }
                val header = Header(
                    compressors = IntArray(4) { u32be(h, 16 + it * 4).toInt() },
                    logicalBytes = u64be(h, 32),
                    mapOffset = u64be(h, 40),
                    hunkBytes = u32be(h, 56).toInt(),
                    unitBytes = u32be(h, 60).toInt()
                )
                if (header.hunkBytes <= 0 || header.unitBytes <= 0) throw IOException("CHD header looks corrupt")
                return ChdDiscImage(raf, header, readMap(raf, header))
            } catch (e: Exception) {
                raf.close()
                throw e
            }
        }

        private fun readMap(raf: RandomAccessFile, h: Header): Array<MapEntry> {
            val count = h.hunkCount

            // Uncompressed CHD: plain 4-byte offsets, in units of whole hunks.
            if (h.compressors[0] == 0) {
                val raw = readAt(raf, h.mapOffset, count * 4)
                return Array(count) { i ->
                    val offset = u32be(raw, i * 4) * h.hunkBytes
                    if (offset == 0L) MapEntry(TYPE_ZERO, 0, 0) else MapEntry(TYPE_NONE, h.hunkBytes, offset)
                }
            }

            val mapHeader = readAt(raf, h.mapOffset, 16)
            val mapBytes = u32be(mapHeader, 0).toInt()
            val firstOffset = u48be(mapHeader, 4)
            val lengthBits = mapHeader[12].toInt() and 0xFF
            val selfBits = mapHeader[13].toInt() and 0xFF
            val parentBits = mapHeader[14].toInt() and 0xFF
            val bits = BitReader(readAt(raf, h.mapOffset + 16, mapBytes))

            // Pass 1: each hunk's storage type, Huffman-coded with run-length escapes.
            val huffman = MapHuffman(bits)
            val types = IntArray(count)
            var last = 0
            var repeat = 0
            for (i in 0 until count) {
                if (repeat > 0) {
                    types[i] = last
                    repeat--
                    continue
                }
                when (val value = huffman.decode(bits)) {
                    RLE_SMALL -> { types[i] = last; repeat = 2 + huffman.decode(bits) }
                    RLE_LARGE -> {
                        types[i] = last
                        repeat = 2 + 16 + (huffman.decode(bits) shl 4)
                        repeat += huffman.decode(bits)
                    }
                    else -> { types[i] = value; last = value }
                }
            }

            // Pass 2: lengths and offsets, in the same bit stream.
            var current = firstOffset
            var lastSelf = 0L
            var lastParent = 0L
            val unitsPerHunk = (h.hunkBytes / h.unitBytes).toLong()
            return Array(count) { i ->
                var type = types[i]
                var offset = current
                var length = 0
                when (type) {
                    0, 1, 2, 3 -> {
                        length = bits.read(lengthBits).toInt()
                        current += length
                        bits.read(16) // CRC
                    }
                    TYPE_NONE -> {
                        length = h.hunkBytes
                        current += length
                        bits.read(16) // CRC
                    }
                    TYPE_SELF -> { offset = bits.read(selfBits); lastSelf = offset }
                    TYPE_PARENT -> { offset = bits.read(parentBits); lastParent = offset }
                    SELF_1 -> { lastSelf++; type = TYPE_SELF; offset = lastSelf }
                    SELF_0 -> { type = TYPE_SELF; offset = lastSelf }
                    PARENT_SELF -> {
                        type = TYPE_PARENT
                        offset = i.toLong() * h.hunkBytes / h.unitBytes
                        lastParent = offset
                    }
                    PARENT_1 -> { lastParent += unitsPerHunk; type = TYPE_PARENT; offset = lastParent }
                    PARENT_0 -> { type = TYPE_PARENT; offset = lastParent }
                }
                MapEntry(type, length, offset)
            }
        }

        private fun inflate(src: ByteArray, offset: Int, length: Int, outSize: Int): ByteArray {
            val inflater = Inflater(true) // raw deflate, no zlib header
            try {
                // Raw inflate wants one spare byte past the end of the input.
                val input = ByteArray(length + 1)
                System.arraycopy(src, offset, input, 0, length)
                inflater.setInput(input)
                val out = ByteArray(outSize)
                var produced = 0
                while (produced < outSize && !inflater.finished()) {
                    val n = inflater.inflate(out, produced, outSize - produced)
                    if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                    produced += n
                }
                return out
            } finally {
                inflater.end()
            }
        }

        private fun lzma(src: ByteArray, offset: Int, length: Int, outSize: Int): ByteArray {
            // The dictionary never needs to be larger than the output itself.
            val dictionary = maxOf(4096, outSize)
            LZMAInputStream(ByteArrayInputStream(src, offset, length), outSize.toLong(), LZMA_PROPS, dictionary).use { stream ->
                val out = ByteArray(outSize)
                var produced = 0
                while (produced < outSize) {
                    val n = stream.read(out, produced, outSize - produced)
                    if (n < 0) break
                    produced += n
                }
                return out
            }
        }

        private fun fourcc(codec: Int): String =
            String(charArrayOf(
                ((codec shr 24) and 0xFF).toChar(), ((codec shr 16) and 0xFF).toChar(),
                ((codec shr 8) and 0xFF).toChar(), (codec and 0xFF).toChar()
            ))

        private fun readAt(raf: RandomAccessFile, position: Long, size: Int): ByteArray {
            val buf = ByteArray(size)
            raf.seek(position)
            raf.readFully(buf)
            return buf
        }

        private fun u32be(b: ByteArray, o: Int): Long =
            ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
                ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

        private fun u48be(b: ByteArray, o: Int): Long =
            ((b[o].toLong() and 0xFF) shl 40) or ((b[o + 1].toLong() and 0xFF) shl 32) or u32be(b, o + 2)

        private fun u64be(b: ByteArray, o: Int): Long = (u32be(b, o) shl 32) or u32be(b, o + 4)
    }

    /** MSB-first bit reader over the compressed map. Reads past the end yield zeros. */
    private class BitReader(private val data: ByteArray) {
        private var position = 0L

        fun read(count: Int): Long {
            var value = 0L
            repeat(count) { value = (value shl 1) or bit(position++) }
            return value
        }

        fun peek(count: Int): Int {
            var value = 0
            var p = position
            repeat(count) { value = (value shl 1) or bit(p++).toInt() }
            return value
        }

        fun skip(count: Int) { position += count }

        private fun bit(p: Long): Long {
            val index = (p ushr 3).toInt()
            if (index >= data.size) return 0
            return ((data[index].toInt() shr (7 - (p and 7).toInt())) and 1).toLong()
        }
    }

    /**
     * The map's Huffman decoder: 16 symbols, codes up to 8 bits, its tree
     * stored as run-length-encoded 4-bit code lengths (MAME import_tree_rle),
     * then canonical codes assigned longest-first.
     */
    private class MapHuffman(bits: BitReader) {
        private val lookup = IntArray(256) // (symbol shl 5) or codeLength

        init {
            val lengths = IntArray(16)
            var current = 0
            while (current < 16) {
                var nodeBits = bits.read(4).toInt()
                if (nodeBits != 1) {
                    lengths[current++] = nodeBits
                } else {
                    nodeBits = bits.read(4).toInt()
                    if (nodeBits == 1) {
                        lengths[current++] = nodeBits
                    } else {
                        var repeat = bits.read(4).toInt() + 3
                        while (repeat-- > 0 && current < 16) lengths[current++] = nodeBits
                    }
                }
            }

            val histogram = IntArray(33)
            lengths.forEach { histogram[it]++ }
            var start = 0
            for (length in 32 downTo 1) {
                val next = (start + histogram[length]) shr 1
                histogram[length] = start
                start = next
            }
            val codes = IntArray(16)
            for (symbol in 0 until 16) {
                if (lengths[symbol] > 0) codes[symbol] = histogram[lengths[symbol]]++
            }

            for (symbol in 0 until 16) {
                val length = lengths[symbol]
                if (length == 0 || length > 8) continue
                val shift = 8 - length
                val first = codes[symbol] shl shift
                val last = ((codes[symbol] + 1) shl shift) - 1
                for (i in first..minOf(last, 255)) lookup[i] = (symbol shl 5) or length
            }
        }

        fun decode(bits: BitReader): Int {
            val entry = lookup[bits.peek(8)]
            bits.skip(entry and 0x1F)
            return entry shr 5
        }
    }
}
