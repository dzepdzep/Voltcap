package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 423: every home layout has its own preview picture in the layout picker, at the screen's 16:9, never borrowed. */
class LayoutPreviews423Test {
    private fun res(l: HomeLayout) = listOf("src/main/res/drawable-nodpi", "app/src/main/res/drawable-nodpi")
        .map { File(it, "lp_${l.name.lowercase()}.webp") }.firstOrNull { it.exists() }

    @Test fun everyLayoutHasItsOwnPictureFile() {
        val missing = HomeLayout.values().filter { res(it) == null }
        assertTrue("layouts without lp_<name>.webp: $missing", missing.isEmpty())
        // Not borrowed: each picture is a different file (no two layouts sharing bytes).
        val sizes = HomeLayout.values().map { res(it)!!.readBytes().contentHashCode() }
        assertEquals("two layouts share the same picture", sizes.size, sizes.toSet().size)
    }

    @Test fun everyLayoutMapsToADistinctResource() {
        val ids = HomeLayout.values().map { layoutPreviewRes(it) }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun picturesAre16by9() {
        // WebP (VP8 / VP8L / VP8X) header: read the canvas size without an image library.
        for (l in HomeLayout.values()) {
            val b = res(l)!!.readBytes()
            val (w, h) = webpSize(b)
            assertTrue("${l.name}: ${w}x$h is not 16:9", kotlin.math.abs(w.toFloat() / h - 16f / 9f) < 0.02f)
        }
    }

    private fun webpSize(b: ByteArray): Pair<Int, Int> {
        fun u(i: Int) = b[i].toInt() and 0xFF
        val chunk = String(b, 12, 4, Charsets.US_ASCII)
        return when (chunk) {
            "VP8X" -> (1 + u(24) + (u(25) shl 8) + (u(26) shl 16)) to (1 + u(27) + (u(28) shl 8) + (u(29) shl 16))
            "VP8L" -> { val bits = u(21) or (u(22) shl 8) or (u(23) shl 16) or (u(24) shl 24); ((bits and 0x3FFF) + 1) to (((bits shr 14) and 0x3FFF) + 1) }
            else -> ((u(26) or (u(27) shl 8)) and 0x3FFF) to ((u(28) or (u(29) shl 8)) and 0x3FFF)
        }
    }
}
