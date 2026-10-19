package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * #79: Ps2Icons — icon.sys and .ico parsing on small synthetic files. (The object holds an android.util.LruCache:
 * needs unitTests.isReturnDefaultValues = true; only the pure parsers are called here.)
 */
class Ps2IconsTest {

    // ---- byte helpers (little endian, as on the PS2) ----

    private fun ByteArray.putU16(o: Int, v: Int) { this[o] = v.toByte(); this[o + 1] = (v shr 8).toByte() }
    private fun ByteArray.putU32(o: Int, v: Long) {
        this[o] = v.toByte(); this[o + 1] = (v shr 8).toByte(); this[o + 2] = (v shr 16).toByte(); this[o + 3] = (v shr 24).toByte()
    }
    private fun ByteArray.putAscii(o: Int, s: String) { s.toByteArray(Charsets.US_ASCII).copyInto(this, o) }

    // ---- icon.sys ----

    private fun iconSys(title: String = "Grand Theft Auto", lineBreak: Int = 11, size: Int = 964): ByteArray {
        val b = ByteArray(size)
        b.putAscii(0, "PS2D")
        b.putU16(0x06, lineBreak)
        b.putU32(0x0C, 0x40) // background alpha: half
        // Corners: red, mid grey, out-of-range red (clamped), blue.
        b.putU32(0x10, 255); b.putU32(0x14, 0); b.putU32(0x18, 0)
        b.putU32(0x20, 0x80); b.putU32(0x24, 0x80); b.putU32(0x28, 0x80)
        b.putU32(0x30, 1000); b.putU32(0x34, 0); b.putU32(0x38, 0)
        b.putU32(0x40, 0); b.putU32(0x44, 0); b.putU32(0x48, 255)
        b.putAscii(0xC0, title)
        b.putAscii(0x104, "list.ico")
        b.putAscii(0x144, "copy.ico")
        b.putAscii(0x184, "del.ico")
        return b
    }

    @Test
    fun iconSysParsesTitleColoursAndNames() {
        val s = Ps2Icons.parseIconSys(iconSys())
        assertNotNull(s)
        s!!
        assertEquals("Grand Theft", s.line1)
        assertEquals("Auto", s.line2)
        assertEquals(0xFFFF0000.toInt(), s.corners[0])
        assertEquals(0xFF808080.toInt(), s.corners[1])
        assertEquals(0xFFFF0000.toInt(), s.corners[2])
        assertEquals(0xFF0000FF.toInt(), s.corners[3])
        assertEquals(0.5f, s.bgAlpha, 1e-6f)
        assertEquals("list.ico", s.normalIcon)
        assertEquals("copy.ico", s.copyIcon)
        assertEquals("del.ico", s.deleteIcon)
        assertEquals("list.ico", Ps2Icons.normalIconName(iconSys()))
    }

    @Test
    fun iconSysLineBreakOutOfRangeKeepsOneLine() {
        assertEquals("Grand Theft Auto", Ps2Icons.parseIconSys(iconSys(lineBreak = 0))!!.line1)
        assertEquals("", Ps2Icons.parseIconSys(iconSys(lineBreak = 0))!!.line2)
        assertEquals("Grand Theft Auto", Ps2Icons.parseIconSys(iconSys(lineBreak = 200))!!.line1)
    }

    @Test
    fun iconSysAlphaClamped() {
        val b = iconSys()
        b.putU32(0x0C, 0x1000)
        assertEquals(1f, Ps2Icons.parseIconSys(b)!!.bgAlpha, 1e-6f)
    }

    @Test
    fun iconSysMinimumSizeAccepted() {
        assertNotNull(Ps2Icons.parseIconSys(iconSys(size = 0x1C4)))
    }

    @Test
    fun iconSysWrongMagicOrTruncated() {
        val bad = iconSys(); bad.putAscii(0, "PS2X")
        assertNull(Ps2Icons.parseIconSys(bad))
        assertNull(Ps2Icons.parseIconSys(iconSys(size = 0x1C3)))
        assertNull(Ps2Icons.parseIconSys(ByteArray(0)))
        assertNull(Ps2Icons.normalIconName(ByteArray(10)))
    }

    @Test
    fun iconNameWithPathIsRejected() {
        val b = iconSys(); b.putAscii(0x104, "../../evil.ico\u0000")
        assertNull(Ps2Icons.normalIconName(b))
    }

    @Test
    fun safeName() {
        assertEquals("list.ico", Ps2Icons.safeName("  list.ico "))
        assertNull(Ps2Icons.safeName(""))
        assertNull(Ps2Icons.safeName("   "))
        assertNull(Ps2Icons.safeName("."))
        assertNull(Ps2Icons.safeName(".."))
        assertNull(Ps2Icons.safeName("a/b.ico"))
        assertNull(Ps2Icons.safeName("a\\b.ico"))
        assertNull(Ps2Icons.safeName("x".repeat(65)))
        assertEquals("x".repeat(64), Ps2Icons.safeName("x".repeat(64)))
    }

    // ---- .ico ----

    private val texBytes = 128 * 128 * 2

    /** Header + [verts] vertices of one shape; [count] is what the header claims. */
    private fun icoHead(verts: Int, count: Long = verts.toLong(), texType: Int = 0x07, shapes: Int = 1, extra: Int = 0): ByteArray {
        val stride = 8 * shapes + 16
        val b = ByteArray(20 + verts * stride + extra)
        b.putU32(0, 0x00010000)
        b.putU32(4, shapes.toLong())
        b.putU32(8, texType.toLong())
        b.putU32(16, count)
        for (i in 0 until verts) {
            val o = 20 + i * stride
            b.putU16(o, 4096)          // x = 1.0
            b.putU16(o + 2, -2048)     // y = -0.5
            b.putU16(o + 4, 0)         // z
            val t = o + 8 * shapes + 8 // after the shapes and the normal
            b.putU16(t, 4096)          // u = 1.0
            b.putU16(t + 2, 2048)      // v = 0.5
            b[t + 4] = 0x80.toByte(); b[t + 5] = 0x40; b[t + 6] = 0; b[t + 7] = 0x80.toByte()
        }
        return b
    }

    private fun withTail(head: ByteArray, tail: ByteArray) = head + tail

    private fun rawTexture(pixel: Int): ByteArray {
        val t = ByteArray(texBytes)
        for (i in 0 until 128 * 128) t.putU16(i * 2, pixel)
        return t
    }

    /** An RLE block: u32 length, then the u16 codes. */
    private fun rle(vararg codes: Int): ByteArray {
        val b = ByteArray(4 + codes.size * 2)
        b.putU32(0, (codes.size * 2).toLong())
        codes.forEachIndexed { i, c -> b.putU16(4 + i * 2, c) }
        return b
    }

    @Test
    fun icoWithPlainTexture() {
        val ico = Ps2Icons.parseIco(withTail(icoHead(3), rawTexture(0x001F)))
        assertNotNull(ico)
        ico!!
        assertEquals(1, ico.shapes)
        assertEquals(3, ico.vertexCount)
        assertEquals(0, ico.frames)
        assertEquals(1f, ico.positions[0], 1e-6f)
        assertEquals(-0.5f, ico.positions[1], 1e-6f)
        assertEquals(1f, ico.uvs[0], 1e-6f)
        assertEquals(0.5f, ico.uvs[1], 1e-6f)
        // 0x80 = full brightness: doubled (0x80 → 0xFF clamped, 0x40 → 0x80).
        assertEquals(0xFFFF8000.toInt(), ico.colors[0])
        assertNotNull(ico.texture)
        assertEquals(128 * 128, ico.texture!!.size)
        assertEquals(0xFFFF0000.toInt(), ico.texture!![0]) // A1B5G5R5 red 31 → 0xFF
        assertEquals(0xFFFF0000.toInt(), ico.texture!![128 * 128 - 1])
    }

    @Test
    fun icoVertexCountRoundedDownToTriangles() {
        val ico = Ps2Icons.parseIco(withTail(icoHead(3, count = 5), rawTexture(0)))
        assertEquals(3, ico!!.vertexCount)
        assertEquals(9, ico.positions.size)
    }

    @Test
    fun icoTwoShapesUsesShapeZero() {
        val ico = Ps2Icons.parseIco(withTail(icoHead(3, shapes = 2), rawTexture(0)))
        assertNotNull(ico)
        assertEquals(2, ico!!.shapes)
        assertEquals(0.5f, ico.uvs[1], 1e-6f)
    }

    @Test
    fun icoAnimationIsSkippedBeforeTexture() {
        val anim = ByteArray(20 + 16 + 2 * 8)
        anim.putU32(0, 1)        // animation header id
        anim.putU32(16, 1)       // one frame
        anim.putU32(20 + 4, 2)   // two keys
        val ico = Ps2Icons.parseIco(withTail(withTail(icoHead(3), anim), rawTexture(0x03E0)))
        assertNotNull(ico)
        assertEquals(1, ico!!.frames)
        assertEquals(0xFF00FF00.toInt(), ico.texture!![0]) // green 31
    }

    @Test
    fun icoRleRepeatRun() {
        val ico = Ps2Icons.parseIco(withTail(icoHead(3, texType = 0x0F), rle(0x4000, 0x7C00)))
        val tex = ico!!.texture
        assertNotNull(tex)
        assertEquals(0xFF0000FF.toInt(), tex!![0]) // blue 31
        assertEquals(0xFF0000FF.toInt(), tex[128 * 128 - 1])
    }

    @Test
    fun icoRleCopyThenRepeat() {
        // 0xFFFE: copy the next 2 pixels as they are; 0x3FFE: repeat the next pixel 16382 times.
        val ico = Ps2Icons.parseIco(withTail(icoHead(3, texType = 0x0F), rle(0xFFFE, 0x001F, 0x03E0, 0x3FFE, 0x7C00)))
        val tex = ico!!.texture!!
        assertEquals(0xFFFF0000.toInt(), tex[0])
        assertEquals(0xFF00FF00.toInt(), tex[1])
        assertEquals(0xFF0000FF.toInt(), tex[2])
        assertEquals(0xFF0000FF.toInt(), tex[128 * 128 - 1])
    }

    @Test
    fun icoRleShortRunIsPaddedGrey() {
        val ico = Ps2Icons.parseIco(withTail(icoHead(3, texType = 0x0F), rle(5000, 0x7C00)))
        val tex = ico!!.texture!!
        assertEquals(0xFF0000FF.toInt(), tex[4999])
        assertEquals(0xFF404040.toInt(), tex[5000])
    }

    @Test
    fun icoRleTooLittleDataMeansNoTexture() {
        val ico = Ps2Icons.parseIco(withTail(icoHead(3, texType = 0x0F), rle(100, 0x7C00)))
        assertNotNull(ico)
        assertNull(ico!!.texture)
    }

    @Test
    fun icoRleBogusLengthMeansNoTexture() {
        val tail = ByteArray(12) { 0xFF.toByte() }
        tail.putU32(0, 0x7FFFFFFF)
        val ico = Ps2Icons.parseIco(withTail(icoHead(3, texType = 0x0F), tail))
        assertNotNull(ico)
        assertNull(ico!!.texture)
    }

    @Test
    fun icoWithoutTextureBytes() {
        val ico = Ps2Icons.parseIco(icoHead(3))
        assertNotNull(ico)
        assertNull(ico!!.texture)
    }

    @Test
    fun icoWrongMagicOrTooShort() {
        val b = withTail(icoHead(3), rawTexture(0))
        b.putU32(0, 0x00020000)
        assertNull(Ps2Icons.parseIco(b))
        assertNull(Ps2Icons.parseIco(ByteArray(19)))
        assertNull(Ps2Icons.parseIco(ByteArray(0)))
    }

    @Test
    fun icoAbsurdShapeCounts() {
        val zero = icoHead(3); zero.putU32(4, 0)
        assertNull(Ps2Icons.parseIco(zero))
        val many = icoHead(3); many.putU32(4, 65)
        assertNull(Ps2Icons.parseIco(many))
    }

    @Test
    fun icoAbsurdVertexCounts() {
        assertNull(Ps2Icons.parseIco(icoHead(3, count = 2)))
        assertNull(Ps2Icons.parseIco(icoHead(3, count = 30_001)))
        assertNull(Ps2Icons.parseIco(icoHead(3, count = 0xFFFFFFFFL)))
        // Claims more vertices than the file holds.
        assertNull(Ps2Icons.parseIco(icoHead(3, count = 300)))
    }

    @Test
    fun icoTruncatedAnimationDoesNotCrash() {
        val anim = ByteArray(20)
        anim.putU32(0, 1)
        anim.putU32(16, 1000) // 1000 frames claimed, none present
        val ico = Ps2Icons.parseIco(withTail(icoHead(3), anim))
        assertNotNull(ico)
        assertEquals(1000, ico!!.frames)
        assertNull(ico.texture)
    }

    @Test
    fun icoHugeAnimationKeyCountDoesNotCrash() {
        val anim = ByteArray(20 + 16)
        anim.putU32(0, 1)
        anim.putU32(16, 1)
        anim.putU32(20 + 4, 0xFFFFFFFFL) // clamped to 65536 keys, past the end
        val ico = Ps2Icons.parseIco(withTail(withTail(icoHead(3), anim), rawTexture(0)))
        assertNotNull(ico)
        // The animation runs past the end: the plain texture is taken from the file's last 32 KB.
        assertEquals(0xFF000000.toInt(), ico!!.texture!![0])
    }
}
