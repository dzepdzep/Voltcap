package com.shelf.launcher.data

/**
 * A small, dependency-free QR code maker (byte mode, error correction level M, versions 1–20), enough
 * for the settings transfer link. Follows the QR Code standard (ISO/IEC 18004): data and Reed-Solomon
 * error correction interleaved in blocks, the finder / timing / alignment patterns, format and version
 * information, and the mask with the lowest penalty.
 */
class QrCode private constructor(val size: Int, private val modules: Array<BooleanArray>) {
    /** Dark module at column [x], row [y]. */
    fun dark(x: Int, y: Int): Boolean = x in 0 until size && y in 0 until size && modules[y][x]

    companion object {
        // Error correction level M.
        private val ECC_PER_BLOCK = intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26)
        private val NUM_BLOCKS = intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16)
        private const val FORMAT_BITS_M = 0

        /** The QR code for [text] (UTF-8), or null if it's too long for version 20. */
        fun encode(text: String): QrCode? {
            val data = text.toByteArray(Charsets.UTF_8)
            var version = 1
            while (true) {
                if (version > 20) return null
                val ccBits = if (version <= 9) 8 else 16
                if (data.size < (1 shl ccBits) && 4 + ccBits + data.size * 8 <= dataCodewords(version) * 8) break
                version++
            }
            val capacityBits = dataCodewords(version) * 8
            val bits = BitBuf()
            bits.append(0b0100, 4)
            bits.append(data.size, if (version <= 9) 8 else 16)
            data.forEach { bits.append(it.toInt() and 0xFF, 8) }
            bits.append(0, minOf(4, capacityBits - bits.length))
            bits.append(0, (8 - bits.length % 8) % 8)
            var pad = 0xEC
            while (bits.length < capacityBits) { bits.append(pad, 8); pad = pad xor 0xEC xor 0x11 }
            val codewords = ByteArray(bits.length / 8)
            for (i in 0 until bits.length) if (bits[i]) codewords[i ushr 3] = (codewords[i ushr 3].toInt() or (1 shl (7 - (i and 7)))).toByte()
            return Builder(version).build(interleave(version, codewords))
        }

        private fun rawModules(ver: Int): Int {
            var result = (16 * ver + 128) * ver + 64
            if (ver >= 2) {
                val numAlign = ver / 7 + 2
                result -= (25 * numAlign - 10) * numAlign - 55
                if (ver >= 7) result -= 36
            }
            return result
        }

        private fun dataCodewords(ver: Int): Int = rawModules(ver) / 8 - ECC_PER_BLOCK[ver] * NUM_BLOCKS[ver]

        private fun interleave(ver: Int, data: ByteArray): ByteArray {
            val numBlocks = NUM_BLOCKS[ver]
            val eccLen = ECC_PER_BLOCK[ver]
            val raw = rawModules(ver) / 8
            val numShort = numBlocks - raw % numBlocks
            val shortLen = raw / numBlocks
            val divisor = rsDivisor(eccLen)
            val blocks = ArrayList<ByteArray>()
            var k = 0
            for (i in 0 until numBlocks) {
                val len = shortLen - eccLen + if (i < numShort) 0 else 1
                val dat = data.copyOfRange(k, k + len)
                k += len
                val ecc = rsRemainder(dat, divisor)
                val block = ByteArray(shortLen + 1)
                // Short blocks get a placeholder byte so every block lines up.
                dat.copyInto(block, 0)
                ecc.copyInto(block, block.size - eccLen)
                blocks += block
            }
            val out = ByteArray(raw)
            var n = 0
            for (i in 0 until shortLen + 1) for (j in 0 until numBlocks) {
                if (i != shortLen - eccLen || j >= numShort) out[n++] = blocks[j][i]
            }
            return out
        }

        private fun rsDivisor(degree: Int): ByteArray {
            val result = ByteArray(degree)
            result[degree - 1] = 1
            var root = 1
            for (i in 0 until degree) {
                for (j in 0 until degree) {
                    result[j] = gfMul(result[j].toInt() and 0xFF, root).toByte()
                    if (j + 1 < degree) result[j] = (result[j].toInt() xor result[j + 1].toInt()).toByte()
                }
                root = gfMul(root, 0x02)
            }
            return result
        }

        private fun rsRemainder(data: ByteArray, divisor: ByteArray): ByteArray {
            val result = ByteArray(divisor.size)
            for (b in data) {
                val factor = (b.toInt() xor result[0].toInt()) and 0xFF
                System.arraycopy(result, 1, result, 0, result.size - 1)
                result[result.size - 1] = 0
                for (i in result.indices) result[i] = (result[i].toInt() xor gfMul(divisor[i].toInt() and 0xFF, factor)).toByte()
            }
            return result
        }

        private fun gfMul(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z and 0xFF
        }
    }

    private class BitBuf {
        private val bits = java.util.BitSet()
        var length = 0
            private set
        fun append(value: Int, n: Int) { for (i in n - 1 downTo 0) { if ((value ushr i) and 1 != 0) bits.set(length); length++ } }
        operator fun get(i: Int) = bits[i]
    }

    private class Builder(private val version: Int) {
        val size = version * 4 + 17
        val modules = Array(size) { BooleanArray(size) }
        val function = Array(size) { BooleanArray(size) }

        fun build(codewords: ByteArray): QrCode {
            drawFunctionPatterns()
            drawCodewords(codewords)
            var best = 0
            var bestPenalty = Int.MAX_VALUE
            for (m in 0 until 8) {
                applyMask(m)
                drawFormat(m)
                val p = penalty()
                if (p < bestPenalty) { bestPenalty = p; best = m }
                applyMask(m) // undo (XOR)
            }
            applyMask(best)
            drawFormat(best)
            return QrCode(size, modules)
        }

        private fun set(x: Int, y: Int, dark: Boolean) { modules[y][x] = dark; function[y][x] = true }

        private fun drawFunctionPatterns() {
            for (i in 0 until size) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) }
            finder(3, 3); finder(size - 4, 3); finder(3, size - 4)
            val pos = alignmentPositions()
            val n = pos.size
            for (i in 0 until n) for (j in 0 until n) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue
                for (dy in -2..2) for (dx in -2..2) set(pos[i] + dx, pos[j] + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
            }
            drawFormat(0)
            drawVersion()
        }

        private fun finder(x: Int, y: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val d = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until size && yy in 0 until size) set(xx, yy, d != 2 && d != 4)
            }
        }

        private fun alignmentPositions(): IntArray {
            if (version == 1) return IntArray(0)
            val numAlign = version / 7 + 2
            val step = (version * 8 + numAlign * 3 + 5) / (numAlign * 4 - 4) * 2
            val result = IntArray(numAlign)
            result[0] = 6
            var pos = size - 7
            for (i in numAlign - 1 downTo 1) { result[i] = pos; pos -= step }
            return result
        }

        private fun bit(v: Int, i: Int) = (v ushr i) and 1 != 0

        private fun drawFormat(mask: Int) {
            val data = FORMAT_BITS_M shl 3 or mask
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = (data shl 10 or rem) xor 0x5412
            for (i in 0..5) set(8, i, bit(bits, i))
            set(8, 7, bit(bits, 6)); set(8, 8, bit(bits, 7)); set(7, 8, bit(bits, 8))
            for (i in 9 until 15) set(14 - i, 8, bit(bits, i))
            for (i in 0 until 8) set(size - 1 - i, 8, bit(bits, i))
            for (i in 8 until 15) set(8, size - 15 + i, bit(bits, i))
            set(8, size - 8, true)
        }

        private fun drawVersion() {
            if (version < 7) return
            var rem = version
            repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
            val bits = version shl 12 or rem
            for (i in 0 until 18) {
                val b = bit(bits, i)
                val a = size - 11 + i % 3
                val c = i / 3
                set(a, c, b); set(c, a, b)
            }
        }

        private fun drawCodewords(data: ByteArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) for (j in 0..1) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!function[y][x] && i < data.size * 8) {
                        modules[y][x] = bit(data[i ushr 3].toInt(), 7 - (i and 7))
                        i++
                    }
                }
                right -= 2
            }
        }

        private fun applyMask(m: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                if (function[y][x]) continue
                val invert = when (m) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (invert) modules[y][x] = !modules[y][x]
            }
        }

        /** Runs of 5+, 2×2 blocks, finder look-alikes and dark/light balance (the standard's rules). */
        private fun penalty(): Int {
            var p = 0
            for (pass in 0..1) for (a in 0 until size) {
                var run = 0
                var color = false
                val line = BooleanArray(size)
                for (b in 0 until size) {
                    val c = if (pass == 0) modules[a][b] else modules[b][a]
                    line[b] = c
                    if (b > 0 && c == color) { run++; if (run == 5) p += 3 else if (run > 5) p++ } else { color = c; run = 1 }
                }
                for (b in 0..size - 7) {
                    if (line[b] && !line[b + 1] && line[b + 2] && line[b + 3] && line[b + 4] && !line[b + 5] && line[b + 6]) {
                        val before = (b - 4 until b).all { it < 0 || !line[it] }
                        val after = (b + 7 until b + 11).all { it >= size || !line[it] }
                        if (before || after) p += 40
                    }
                }
            }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) p += 3
            }
            var dark = 0
            for (row in modules) for (c in row) if (c) dark++
            val total = size * size
            val k = (kotlin.math.abs(dark * 20 - total * 10) + total - 1) / total - 1
            p += maxOf(0, k) * 10
            return p
        }
    }
}
