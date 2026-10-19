package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Update 248 (#89 finding 11): disc images told apart by their own bytes (DiscImages in DiscUnderside.kt). */
class DiscImagesTest {

    /** A made-up image of [size] bytes with [ids] written as volume descriptors ("CD001"…) at the given offsets. */
    private fun image(size: Int, vararg ids: Pair<Long, String>): (Long, Int) -> ByteArray? {
        val b = ByteArray(size)
        for ((at, id) in ids) {
            b[at.toInt()] = 1
            id.toByteArray(Charsets.US_ASCII).copyInto(b, at.toInt() + 1)
        }
        return { at, n -> if (at < 0 || at + n > b.size) null else b.copyOfRange(at.toInt(), at.toInt() + n) }
    }

    private val S = 2048L

    @Test fun udfBridgeIsDvd() {
        val img = image(40 * 2048, 16 * S to "CD001", 17 * S to "BEA01", 18 * S to "NSR02", 19 * S to "TEA01")
        assertEquals(DiscImages.Kind.DVD, DiscImages.plainKind(img))
    }

    @Test fun nsr03AloneIsDvd() {
        val img = image(40 * 2048, 16 * S to "CD001", 17 * S to "CD001", 18 * S to "NSR03")
        assertEquals(DiscImages.Kind.DVD, DiscImages.plainKind(img))
    }

    @Test fun plainIso9660IsCd() {
        val img = image(40 * 2048, 16 * S to "CD001", 17 * S to "CD001")
        assertEquals(DiscImages.Kind.CD, DiscImages.plainKind(img))
    }

    @Test fun rawMode2FramesAreCd() {
        val img = image(40 * 2352, 16L * 2352 + 24 to "CD001")
        assertEquals(DiscImages.Kind.CD, DiscImages.plainKind(img))
    }

    @Test fun rawMode1FramesAreCd() {
        val img = image(40 * 2352, 16L * 2352 + 16 to "CD001")
        assertEquals(DiscImages.Kind.CD, DiscImages.plainKind(img))
    }

    @Test fun nothingKnownIsUnknown() {
        assertEquals(DiscImages.Kind.UNKNOWN, DiscImages.plainKind(image(40 * 2048)))
    }

    @Test fun tooShortIsUnknown() {
        assertEquals(DiscImages.Kind.UNKNOWN, DiscImages.plainKind(image(100)))
    }

    @Test fun chdUnits() {
        assertEquals(DiscImages.Kind.CD, DiscImages.chdKind(2448))
        assertEquals(DiscImages.Kind.CD, DiscImages.chdKind(2352))
        assertEquals(DiscImages.Kind.DVD, DiscImages.chdKind(2048, 1_433_534_464L))
        // (A CD dumped as a plain ISO and made into a CHD has 2048-byte sectors too: CD-sized ones stay undecided.)
        assertEquals(DiscImages.Kind.UNKNOWN, DiscImages.chdKind(2048, 725_000_000L))
        assertEquals(DiscImages.Kind.UNKNOWN, DiscImages.chdKind(4096))
    }

    @Test fun smallPs2DvdStaysDvd() {
        // The old rule read any PS2 image under 900 MB as a CD-ROM.
        assertEquals(DiscFormat.PS2_DVD, DiscImages.format(DiscImages.Kind.DVD, 600_000_000L, true, DiscFormat.PS2_DVD))
    }

    @Test fun ps2CdAndDualLayer() {
        assertEquals(DiscFormat.PS2_CD, DiscImages.format(DiscImages.Kind.CD, 650_000_000L, true, DiscFormat.PS2_DVD))
        assertEquals(DiscFormat.PS2_DVD_DL, DiscImages.format(DiscImages.Kind.DVD, 7_500_000_000L, true, DiscFormat.PS2_DVD))
    }

    @Test fun unknownFallsBackToSize() {
        assertEquals(DiscFormat.PS2_CD, DiscImages.format(DiscImages.Kind.UNKNOWN, 500_000_000L, true, DiscFormat.PS2_DVD))
        assertEquals(DiscFormat.PS2_DVD, DiscImages.format(DiscImages.Kind.UNKNOWN, 2_000_000_000L, true, DiscFormat.PS2_DVD))
        assertEquals(DiscFormat.DVD_DL, DiscImages.format(DiscImages.Kind.UNKNOWN, 8_000_000_000L, false, DiscFormat.DVD))
        assertEquals(DiscFormat.DVD, DiscImages.format(DiscImages.Kind.CD, 8_000_000_000L, false, DiscFormat.DVD))
    }
}
