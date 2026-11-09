package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 492 (staging only): 3D media faces (seam order, connector slot, clear sides), pinned comments' size. */
class Batch492Test {
    private fun src(rel: String): String = listOf(
        File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel")
    ).first { it.exists() }.readText()

    @Test fun seamIsDrawnInItsPlaceAmongTheSlicesNotOnTop() {
        // Home screen / Save Vault pieces: the seam goes through drawSideCopies(atZ), not after the copies.
        val sp = src("ui/SolidPiece.kt")
        assertTrue(sp.contains("if (!zDone && passed(z)) { zDone = true; onZ!!.invoke(this) }"))
        assertTrue(!sp.contains("if (cut != null) clipPath(cut) { drawSeam() } else drawSeam()"))
        assertTrue(sp.contains("smear, edge, m, step, buf, seamZ, seamDraw)"))
        // Inspect: inside the slice loop, far to near.
        val mi = src("ui/MediaInspectView.kt")
        val loop = mi.substring(mi.indexOf("var seamDone = false"), mi.indexOf("if (!seamDone) at(seamZ) { drawLayer(seam) }"))
        assertTrue(loop.contains("if (!seamDone && (if (c >= 0f) z > seamZ else z < seamZ)) { seamDone = true; at(seamZ) { drawLayer(seam) } }"))
        assertTrue(loop.indexOf("seamDone = true") < loop.indexOf("atIn(z, inset) { drawLayer(layer) }"))
    }

    /** The order rule itself: far to near, the seam lands right before the first slice nearer than it — both ways round. */
    @Test fun seamOrderFarToNear() {
        fun order(c: Float, depth: Float, n: Int, seamZ: Float): List<String> {
            val out = ArrayList<String>(); var done = false
            for (k in 0..n) {
                val f = k.toFloat() / n
                val z = if (c >= 0f) -depth / 2f + depth * f else depth / 2f - depth * f
                if (!done && (if (c >= 0f) z > seamZ else z < seamZ)) { done = true; out += "seam" }
                out += "s$k"
            }
            if (!done) out += "seam"
            return out
        }
        // Facing you (front nearest): the seam 2 mm from the back of 7.8 is near the far end of the slices.
        val front = order(1f, 7.8f, 8, 7.8f / 2f - 5.8f)
        assertTrue(front.indexOf("seam") in 1..4)
        // Seen from behind: the seam is near the start (the back is nearest now, so the front half is drawn first).
        val back = order(-1f, 7.8f, 8, 7.8f / 2f - 5.8f)
        assertTrue(back.indexOf("seam") >= 5)
        assertEquals(10, front.size); assertEquals(10, back.size)
    }

    @Test fun connectorContentsFadeAsTheOpeningTurnsEdgeOn() {
        val u = src("ui/CartUnderside.kt")
        assertTrue(u.contains("internal const val SLOT_SEEN = 0.2f"))
        assertTrue(u.contains("if (!contents || see <= 0f) return@clipPath"))
        assertTrue(u.contains("solid(this, s, v, plastic, inner * see)"))
        assertTrue(u.contains("shade(gold, 0.55f + 0.5f * v.light(0f, 0f, nz), see)"))
        // The walls of the slot stay as they were (plastic, unfaded): the sliver shows them.
        assertTrue(u.contains("drawPath(path, shade(plastic, 0.5f * v.light(nx, ny, nz), inner))"))
    }

    @Test fun clearPiecesKeepAWholeSide() {
        val sp = src("ui/SolidPiece.kt")
        // solid slices in one layer, laid down at the plastic's strength (not each slice faint)
        assertTrue(sp.contains("val sheet = clear != null && spec.gb == null"))
        assertTrue(sp.contains("edgeL.alpha = if (sheet) 1f else"))
        assertTrue(sp.contains("androidx.compose.ui.graphics.Paint().apply { alpha = clear!!.coerceIn(0.2f, 0.8f) }"))
        assertTrue(sp.contains("if (sheet) drawContext.canvas.restore()"))
    }

    @Test fun pinnedCommentsReadAsTheListsDo() {
        val o = src("ui/RaOverlayView.kt")
        assertEquals(11f, COMMENT_NAME_SP); assertEquals(12f, COMMENT_TEXT_SP)
        assertTrue(o.contains("cleanComment(c.CommentText.orEmpty()))")) // (496: through addComment, shared with the QA fixture)
        assertTrue(o.contains("list.sortedByDescending { it.Submitted ?: \"\" }.take(12)"))
        assertTrue(!o.contains("setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)\n                    setPadding(px(6f), px(3f), 0, px(3f))"))
        // The list's detail view uses the same sizes.
        val d = src("ui/AchievementDetailDialog.kt")
        assertTrue(d.contains("fontSize = 11.sp, fontWeight = FontWeight.SemiBold") && d.contains("fontSize = 12.sp, lineHeight = 16.sp"))
    }
}
