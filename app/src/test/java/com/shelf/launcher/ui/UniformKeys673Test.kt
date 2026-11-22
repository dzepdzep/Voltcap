package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 673g, owner brief 15 Nov ("VOLTCAP - FINAL GLOBAL BUTTON & MENU DESIGN SYSTEM"): every key in a group the same width and height,
 * horizontal = one row that scrolls a whole key at a time, never wrapped; one MORE (no three dots), last; status lines not keys.
 */
class UniformKeys673Test {
    private fun src(name: String) = listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")
        .map(::File).first { it.exists() }.readText()

    @Test fun keysThatFitShareTheWholeRowEqually() {
        val g = uniformRowGeom(n = 4, need = 120, width = 600, gap = 10, gutter = 30)
        assertFalse(g.scrolls)
        assertEquals((600 - 3 * 10) / 4, g.keyW)            // every key the same, at least its word
        assertTrue(g.keyW >= 120)
        assertEquals(4, g.visible); assertEquals(0, g.maxOffset); assertEquals(0, g.viewStart)
    }

    @Test fun keysThatDontFitScrollWholeKeysNeverNarrowerThanTheirWords() {
        // 7 keys of 180 px in 400 px (KONKR x1.3, Disc station with More): two whole keys between the arrows
        val g = uniformRowGeom(n = 7, need = 180, width = 400, gap = 8, gutter = 30)
        assertTrue(g.scrolls)
        assertEquals(1, g.visible)                           // (400 - 60 = 340: one 180 px key at a time, its own width - not stretched)
        assertEquals(180, g.keyW)
        assertEquals(g.visible * g.keyW + (g.visible - 1) * 8, g.viewW)
        assertEquals((7 - g.visible) * g.pitch, g.maxOffset) // the far end is whole keys away
        val h = uniformRowGeom(n = 7, need = 120, width = 600, gap = 8, gutter = 20)
        assertTrue(h.scrolls); assertEquals(4, h.visible)    // (560 + 8) / 128 = 4 whole keys
        assertTrue(h.keyW >= 120)
        assertTrue(h.viewStart >= 20 - 4 && h.viewStart + h.viewW <= 600)
    }

    @Test fun aSingleKeyIsNeverStretchedAcrossTheRow() {
        val g = uniformRowGeom(n = 1, need = 150, width = 600, gap = 8, gutter = 20)
        assertEquals(150, g.keyW); assertFalse(g.scrolls); assertEquals(225, g.viewStart)
    }

    @Test fun theDpadBringsAKeyIntoViewAWholeKeyAtATime() {
        val g = uniformRowGeom(n = 7, need = 120, width = 600, gap = 8, gutter = 20)
        assertEquals(0, uniformRowScrollFor(0, 0, g))
        assertEquals(0, uniformRowScrollFor(3, 0, g))                      // already whole in view: no move
        assertEquals(g.pitch, uniformRowScrollFor(4, 0, g))                // one key on
        assertEquals(3 * g.pitch, uniformRowScrollFor(6, 0, g))            // the last key at the right edge
        assertEquals(2 * g.pitch, uniformRowScrollFor(2, 3 * g.pitch, g))  // back: the key at the left edge
        for (i in 0 until 7) assertEquals(0, uniformRowScrollFor(i, 0, g) % g.pitch)
        assertEquals(0, uniformRowScrollFor(5, 0, uniformRowGeom(3, 100, 600, 8, 20)))  // a row that fits never scrolls
    }

    @Test fun moreIsAWordedKeyNeverThreeDots() {
        val more = src("WinScenesMore.kt")
        assertFalse(more.contains("Text(\"•••\""))
        assertTrue(src("GameWindow.kt").contains("WinAction(\"More\", I.Layers,"))
        assertFalse(src("GameWindow.kt").contains("WinAction(\"More\", I.MoreHoriz"))
        // More / Less last in every scene's keys
        assertTrue(more.contains("all.filterNot(PopupLab::isMore) + all.filter(PopupLab::isMore)"))
    }

    @Test fun statusLinesStandUnderTheKeysNotAmongThem() {
        val gw = src("GameWindow.kt")
        assertTrue(gw.contains("urgent = sv.urgent, status = true))"))
        assertTrue(gw.contains("WinAction(p.perf.summary, I.Speed, st.onPerf, status = true)"))
        val more = src("WinScenesMore.kt")
        assertTrue(more.contains("val actions = keys.filterNot { it.status }"))
        assertTrue(more.contains("if (actions.isNotEmpty()) UniformKeyRow(actions, gap, twoLineOnlyWhenAlone = twoLineOnlyWhenAlone, slots = slots, key = key)"))
        assertTrue(more.contains("if (LocalKeyAsStatus.current) { SceneStatusLine(a, k, font, size); return }"))
        // the inert copy of a page's keys keeps the flag
        assertTrue(src("WinPageFlow.kt").contains("inert = true, status = status)"))
    }

    @Test fun everyKeyGroupIsUniform() {
        val more = src("WinScenesMore.kt")
        // KeyRow is one uniform row (no wrapping FlowRow), keys fill the size the group gives them, centred
        val kr = more.substring(more.indexOf("internal fun KeyRow("), more.indexOf("internal data class UniformRowGeom"))
        assertFalse(kr.contains("FlowRow("))
        assertTrue(more.contains("Modifier.then(if (uniform) Modifier.fillMaxSize() else if (wide) Modifier.fillMaxWidth() else Modifier)"))
        assertTrue(more.contains("it.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))"))
        // pages' bars, the TV strip, the plain window and the shared page use the same groups
        assertTrue(src("WinSceneShelfPages.kt").contains("UniformKeyRow(keys.filterNot { it.status }, gap, key = key)"))
        assertTrue(Regex("UniformKeyRow\\(tvItems\\.indices").containsMatchIn(src("GameWindow.kt")))
        assertTrue(src("GameWindow.kt").contains("UniformKeyColumn(all.map"))
        assertTrue(src("GameWindow.kt").contains("UniformKeyRow(ctx.buttons.filterNot { it.status }, 12.dp)"))
        // vertical menus (Cross, Side): one width, one height
        assertTrue(src("WinScenesHomes2.kt").contains("UniformKeyColumn(actions, 4.dp, fill = true, key = key)"))
    }
}
