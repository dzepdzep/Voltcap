package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** #79: MemoryCards — pure header readers (PS1 / GameCube cards, .gci, 3DS / CIA / GC disc ids) on synthetic bytes. */
class MemoryCardsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ---- byte helpers ----

    private fun ByteArray.putAscii(o: Int, s: String) { s.toByteArray(Charsets.US_ASCII).copyInto(this, o) }
    private fun ByteArray.putU32le(o: Int, v: Long) {
        this[o] = v.toByte(); this[o + 1] = (v shr 8).toByte(); this[o + 2] = (v shr 16).toByte(); this[o + 3] = (v shr 24).toByte()
    }
    private fun ByteArray.putU16be(o: Int, v: Int) { this[o] = (v shr 8).toByte(); this[o + 1] = v.toByte() }
    private fun ByteArray.putU32be(o: Int, v: Long) {
        this[o] = (v shr 24).toByte(); this[o + 1] = (v shr 16).toByte(); this[o + 2] = (v shr 8).toByte(); this[o + 3] = v.toByte()
    }
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun file(name: String, b: ByteArray): String = File(tmp.root, name).also { it.writeBytes(b) }.path

    // ---- codes and titles ----

    @Test
    fun normaliseCode() {
        assertEquals("SLUS20312", MemoryCards.normaliseCode("BASLUS-20312GAME"))
        assertEquals("SLES12345", MemoryCards.normaliseCode("BESLES-12345"))
        assertEquals("SLUS20312", MemoryCards.normaliseCode("SLUS_203.12"))
        assertEquals("SLUS20312", MemoryCards.normaliseCode("slus_203.12"))
        assertEquals("SCUS94455", MemoryCards.normaliseCode("Gran Turismo [SCUS-94455].bin"))
        assertNull(MemoryCards.normaliseCode("Pokemon Red"))
        assertNull(MemoryCards.normaliseCode("SLUS-2031"))
        assertNull(MemoryCards.normaliseCode(""))
    }

    @Test
    fun cleanTitleFullWidthShiftJis() {
        // "ＡＢＣ　１" in Shift-JIS, then a terminator and junk.
        val sjis = bytes(0x82, 0x60, 0x82, 0x61, 0x82, 0x62, 0x81, 0x40, 0x82, 0x50, 0x00, 0x41)
        assertEquals("ABC 1", MemoryCards.cleanTitle(sjis))
    }

    @Test
    fun cleanTitleControlCharsAndEmpty() {
        assertEquals("A B", MemoryCards.cleanTitle(bytes(0x41, 0x0A, 0x09, 0x42)))
        assertEquals("", MemoryCards.cleanTitle(ByteArray(0)))
        assertEquals("", MemoryCards.cleanTitle(ByteArray(8)))
    }

    @Test
    fun titleKeyAndTitleMatches() {
        assertEquals("pokemon colosseum", MemoryCards.titleKey("POKéMON COLOSSEUM (USA)"))
        assertEquals("ratchet and clank", MemoryCards.titleKey("Ratchet & Clank [!]"))
        assertTrue(MemoryCards.titleMatches("FINAL FANTASY X Ch.3 Save 01", "Final Fantasy X (USA)"))
        assertTrue(MemoryCards.titleMatches("Pokemon Colosseum", "POKéMON COLOSSEUM (USA)"))
        assertFalse(MemoryCards.titleMatches("FINAL FANTASY X", "Final Fantasy XII (USA)"))
        assertFalse(MemoryCards.titleMatches("Tekken3", "Tekken (USA)")) // digit straight after: another game
        assertFalse(MemoryCards.titleMatches("AB", "AB"))                // too short to compare
        assertFalse(MemoryCards.titleMatches("Gran Turismo", "Ridge Racer"))
    }

    /** Suspected bug (MemoryCards.kt:274): "X-2" / "2" after a space reads as a save-slot suffix of the first game. */
    @Test
    fun sequelTitleDoesNotMatchBaseGame() {
        assertFalse(MemoryCards.titleMatches("FINAL FANTASY X-2", "Final Fantasy X (USA)"))
        assertFalse(MemoryCards.titleMatches("Kingdom Hearts 2", "Kingdom Hearts (USA)"))
    }

    // ---- card type / PS1 ----

    private fun ps1Card(): ByteArray {
        val c = ByteArray(131072)
        c.putAscii(0, "MC")
        // Directory frame of slot 1: in use (first block), 8 KB, file name.
        c[128] = 0x51
        c.putU32le(128 + 4, 8192)
        c.putAscii(128 + 10, "BASLUS-00067DQ7")
        // Slot 1's first block: "SC" header, title at 4.
        c.putAscii(8192, "SC")
        c.putAscii(8192 + 4, "DRAGON QUEST VII")
        // Slot 2: free.
        c[256] = 0xA0.toByte()
        return c
    }

    @Test
    fun cardTypeByContent() {
        assertEquals(1, MemoryCards.cardType(ps1Card()))
        val gme = ByteArray(3904 + 131072); ps1Card().copyInto(gme, 3904)
        assertEquals(1, MemoryCards.cardType(gme))
        val ps2 = ByteArray(0x200); ps2.putAscii(0, "Sony PS2 Memory Card Format ")
        assertEquals(2, MemoryCards.cardType(ps2))
        assertEquals(0, MemoryCards.cardType(ByteArray(131072)))
        assertEquals(0, MemoryCards.cardType(bytes(0x4D, 0x43))) // "MC" but far too short
        assertEquals(0, MemoryCards.cardType(ByteArray(0)))
    }

    @Test
    fun readPs1Saves() {
        val saves = MemoryCards.readPs1("epsxe000.mcr", ps1Card(), 1234L)
        assertEquals(1, saves.size)
        val s = saves[0]
        assertEquals("epsxe000.mcr", s.card)
        assertEquals("BASLUS-00067DQ7", s.folder)
        assertEquals("SLUS00067", s.code)
        assertEquals("DRAGON QUEST VII", s.title)
        assertEquals(8192L, s.sizeBytes)
        assertEquals(1234L, s.modified)
        val any = MemoryCards.readAny("epsxe000.mcr", ps1Card(), 0L)
        assertEquals(1, any?.first)
    }

    @Test
    fun readPs1Negatives() {
        assertTrue(MemoryCards.readPs1("x.mcr", ByteArray(131072), 0L).isEmpty())
        assertTrue(MemoryCards.readPs1("x.mcr", ByteArray(100), 0L).isEmpty())
        assertNull(MemoryCards.readAny("x.bin", ByteArray(1000), 0L))
    }

    @Test
    fun readPs2FileGarbageIsEmptyNotACrash() {
        val b = ByteArray(0x4000); b.putAscii(0, "Sony PS2 Memory Card Format ")
        b.putU32le(0x3C, 0x7FFFFFFF) // absurd root cluster
        assertTrue(MemoryCards.readPs2File("Mcd001.ps2", b, 0L).isEmpty())
        assertTrue(MemoryCards.readPs2File("Mcd001.ps2", ByteArray(0x100), 0L).isEmpty())
    }

    @Test
    fun readPs2FolderSkipsSystemFolders() {
        val sys = ByteArray(0x104); sys.putAscii(0xC0, "SAVE TITLE")
        val dirs = mapOf(
            "BASLUS-20312" to Triple<ByteArray?, Long, Long>(sys, 100L, 5L),
            "BEDATA-SYSTEM" to Triple<ByteArray?, Long, Long>(null, 1L, 1L),
            "BASLUS-99999" to Triple<ByteArray?, Long, Long>(null, 2L, 2L)
        )
        val saves = MemoryCards.readPs2Folder("Mcd001.ps2", dirs).associateBy { it.folder }
        assertEquals(setOf("BASLUS-20312", "BASLUS-99999"), saves.keys)
        assertEquals("SAVE TITLE", saves["BASLUS-20312"]!!.title)
        assertEquals("SLUS20312", saves["BASLUS-20312"]!!.code)
        assertEquals("BASLUS-99999", saves["BASLUS-99999"]!!.title) // no icon.sys: the folder name
    }

    // ---- GameCube ----

    private fun gcEntry(b: ByteArray, d: Int, code: String, fileName: String, first: Int, count: Int, comment: Long) {
        b.putAscii(d, code)
        b.putAscii(d + 0x08, fileName)
        b.putU16be(d + 0x36, first)
        b.putU16be(d + 0x38, count)
        b.putU32be(d + 0x3C, comment)
    }

    @Test
    fun readGci() {
        val b = ByteArray(0x40 + 0x2000)
        gcEntry(b, 0, "GZLE01", "gczelda", 0, 1, 0)
        b.putAscii(0x40, "Zelda: The Wind Waker")
        val s = MemoryCards.readGci("01-GZLE-gczelda.gci", b, 9L)
        assertNotNull(s)
        assertEquals("GZLE01", s!!.code)
        assertEquals("gczelda", s.folder)
        assertEquals("Zelda: The Wind Waker", s.title)
        assertEquals(b.size.toLong(), s.sizeBytes)
    }

    @Test
    fun readGciNegatives() {
        val lower = ByteArray(0x40 + 0x2000); gcEntry(lower, 0, "gzle01", "x", 0, 1, 0)
        assertNull(MemoryCards.readGci("a.gci", lower, 0L))
        val noBlocks = ByteArray(0x40 + 0x2000); gcEntry(noBlocks, 0, "GZLE01", "x", 0, 0, 0)
        assertNull(MemoryCards.readGci("a.gci", noBlocks, 0L))
        val tooMany = ByteArray(0x40 + 0x2000); gcEntry(tooMany, 0, "GZLE01", "x", 0, 100, 0)
        assertNull(MemoryCards.readGci("a.gci", tooMany, 0L))
        assertNull(MemoryCards.readGci("a.gci", ByteArray(0x40), 0L))
        val empty = ByteArray(0x40 + 0x2000) { 0xFF.toByte() }
        assertNull(MemoryCards.readGci("a.gci", empty, 0L))
    }

    @Test
    fun readGciCommentOutOfRangeFallsBackToFileName() {
        val b = ByteArray(0x40 + 0x2000)
        gcEntry(b, 0, "GZLE01", "gczelda", 0, 1, 0x7FFFFF00)
        assertEquals("gczelda", MemoryCards.readGci("a.gci", b, 0L)!!.title)
        // Offset whose end overflows Int (0x40 + 0x7FFFFFA0 + 32 > Int.MAX_VALUE): still no crash.
        gcEntry(b, 0, "GZLE01", "gczelda", 0, 1, 0x7FFFFFA0)
        assertEquals("gczelda", MemoryCards.readGci("a.gci", b, 0L)!!.title)
    }

    @Test
    fun readGcCardUsesNewestDirectory() {
        val blk = 0x2000
        val card = ByteArray(blk * 6)
        // Block 1 (older directory): one save; block 2 (newer, higher counter): another.
        gcEntry(card, 1 * blk, "GALE01", "melee", 5, 1, 0)
        gcEntry(card, 2 * blk, "GZLE01", "gczelda", 5, 1, 0)
        card.putU16be(1 * blk + 0x1FFA, 1)
        card.putU16be(2 * blk + 0x1FFA, 2)
        card.putAscii(5 * blk, "Wind Waker")
        val saves = MemoryCards.readGcCard("MemoryCardA.USA.raw", card, 0L)
        assertEquals(1, saves.size)
        assertEquals("GZLE01", saves[0].code)
        assertEquals("Wind Waker", saves[0].title)
        assertEquals(blk.toLong(), saves[0].sizeBytes)
        // Same card, block 1 newer.
        card.putU16be(1 * blk + 0x1FFA, 3)
        assertEquals("GALE01", MemoryCards.readGcCard("a.raw", card, 0L).single().code)
    }

    @Test
    fun readGcCardNegatives() {
        assertTrue(MemoryCards.readGcCard("a.raw", ByteArray(0x2000 * 4), 0L).isEmpty())   // too small
        assertTrue(MemoryCards.readGcCard("a.raw", ByteArray(0x2000 * 6 + 1), 0L).isEmpty()) // not whole blocks
        val card = ByteArray(0x2000 * 6)
        gcEntry(card, 0x2000, "GZLE01", "x", 5, 50, 0) // runs past the card
        gcEntry(card, 0x2040, "GALE01", "y", 2, 1, 0)  // starts inside the system blocks
        assertTrue(MemoryCards.readGcCard("a.raw", card, 0L).isEmpty())
    }

    // ---- 3DS title ids ----

    @Test
    fun n3dsTitleLowFromFileName() {
        assertEquals("0011c500", MemoryCards.n3dsTitleLow("/nonexistent/Pokemon Y [000400000011C500].cia"))
    }

    @Test
    fun n3dsTitleLowFromNcsdAndNcch() {
        val ncsd = ByteArray(0x200); ncsd.putAscii(0x100, "NCSD"); ncsd.putU32le(0x108, 0x0011C500)
        assertEquals("0011c500", MemoryCards.n3dsTitleLow(file("game.3ds", ncsd)))
        val ncch = ByteArray(0x200); ncch.putAscii(0x100, "NCCH"); ncch.putU32le(0x118, 0x00055D00)
        assertEquals("00055d00", MemoryCards.n3dsTitleLow(file("game.cxi", ncch)))
    }

    private fun cia(sigType: Long, idHigh: Long = 0x00040000, headerSize: Long = 0x2020): ByteArray {
        val certs = 0xA00L; val ticket = 0x350L; val tmd = 0xB34L
        val tmdAt = 0x2040 + 0xA00 + 0x380
        val b = ByteArray(tmdAt + 0x400)
        b.putU32le(0x00, headerSize)
        b.putU32le(0x08, certs); b.putU32le(0x0C, ticket); b.putU32le(0x10, tmd)
        b.putU32be(tmdAt, sigType)
        val skip = when (sigType) { 0x10003L -> 0x200 + 0x3C; 0x10005L -> 0x3C + 0x40; else -> 0x100 + 0x3C }
        val id = tmdAt + 4 + skip + 0x4C
        b.putU32be(id, idHigh); b.putU32be(id + 4, 0x0011C500)
        return b
    }

    @Test
    fun n3dsTitleLowFromCiaTmd() {
        assertEquals("0011c500", MemoryCards.n3dsTitleLow(file("a.cia", cia(0x10004))))
        assertEquals("0011c500", MemoryCards.n3dsTitleLow(file("b.cia", cia(0x10003))))
        assertEquals("0011c500", MemoryCards.n3dsTitleLow(file("c.cia", cia(0x10005))))
    }

    @Test
    fun n3dsTitleLowNegatives() {
        assertNull(MemoryCards.n3dsTitleLow(file("badsig.cia", cia(0x12345))))
        assertNull(MemoryCards.n3dsTitleLow(file("badheader.cia", cia(0x10004, headerSize = 0x2000))))
        assertNull(MemoryCards.n3dsTitleLow(file("wii.cia", cia(0x10004, idHigh = 0x00010000))))
        assertNull(MemoryCards.n3dsTitleLow(file("short.3ds", ByteArray(0x100))))
        assertNull(MemoryCards.n3dsTitleLow(file("zeros.3ds", ByteArray(0x400))))
        assertNull(MemoryCards.n3dsTitleLow(File(tmp.root, "missing.3ds").path))
    }

    // ---- GameCube / Wii disc ids ----

    @Test
    fun gcGameIdPlainRvzWbfsCiso() {
        val iso = ByteArray(0x100); iso.putAscii(0, "GZLE01")
        assertEquals("GZLE01", MemoryCards.gcGameId(file("zelda.iso", iso)))
        val rvz = ByteArray(0x100); rvz.putAscii(0, "RVZ\u0001"); rvz.putAscii(0x58, "GALE01")
        assertEquals("GALE01", MemoryCards.gcGameId(file("melee.rvz", rvz)))
        val wbfs = ByteArray(0x400); wbfs.putAscii(0, "WBFS"); wbfs.putAscii(0x200, "RMCE01")
        assertEquals("RMCE01", MemoryCards.gcGameId(file("mk.wbfs", wbfs)))
        val ciso = ByteArray(0x8100); ciso.putAscii(0, "CISO"); ciso.putAscii(0x8000, "GMSE01")
        assertEquals("GMSE01", MemoryCards.gcGameId(file("sunshine.ciso", ciso)))
    }

    private fun gcz(code: String, stored: Boolean): ByteArray {
        val header = ByteArray(0x80).also { it.putAscii(0, code) }
        val block = if (stored) header else {
            val d = java.util.zip.Deflater()
            d.setInput(header); d.finish()
            val out = ByteArray(256)
            val n = d.deflate(out); d.end()
            out.copyOf(n)
        }
        val b = ByteArray(0x2C + block.size + 0x80)
        b[0] = 0x01; b[1] = 0xC0.toByte(); b[2] = 0x0B; b[3] = 0xB1.toByte()
        b.putU32le(0x08, block.size.toLong()) // compressed size (low half)
        b.putU32le(0x1C, 1)                   // one block
        if (stored) b[0x20 + 7] = 0x80.toByte() // offset 0, top bit: stored uncompressed
        block.copyInto(b, 0x2C)               // after one u64 offset and one u32 hash
        return b
    }

    @Test
    fun gcGameIdFromGcz() {
        assertEquals("GZLE01", MemoryCards.gcGameId(file("zelda.gcz", gcz("GZLE01", stored = false))))
        assertEquals("GALE01", MemoryCards.gcGameId(file("melee.gcz", gcz("GALE01", stored = true))))
    }

    @Test
    fun gcGameIdNegatives() {
        val lower = ByteArray(0x100); lower.putAscii(0, "gzle01")
        assertNull(MemoryCards.gcGameId(file("lower.iso", lower)))
        assertNull(MemoryCards.gcGameId(file("tiny.iso", ByteArray(0x10)))) // shorter than the header
        val noBlocks = gcz("GZLE01", stored = false); noBlocks.putU32le(0x1C, 0)
        assertNull(MemoryCards.gcGameId(file("noblocks.gcz", noBlocks)))
        val garbage = gcz("GZLE01", stored = false); for (i in 0x2C until garbage.size) garbage[i] = 0x55
        assertNull(MemoryCards.gcGameId(file("garbage.gcz", garbage)))
        val ciso = ByteArray(0x100); ciso.putAscii(0, "CISO") // code would be past the end
        assertNull(MemoryCards.gcGameId(file("short.ciso", ciso)))
        assertNull(MemoryCards.gcGameId(File(tmp.root, "missing.iso").path))
    }
}
