package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 675b: a QaClip dump appears whole (written beside the target, renamed into place) - the QA scripts wait for the file. */
class QaClipWriteWhole675bTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun writesTheWholeTextAndLeavesNoPart() {
        val f = java.io.File(tmp.root, "e529a_probe.json")
        QaClip.writeWhole(f, "{\"windows\":[]}")
        assertEquals("{\"windows\":[]}", f.readText())
        assertFalse(java.io.File(tmp.root, "e529a_probe.json.part").exists())
    }

    @Test fun replacesAnOlderDump() {
        val f = java.io.File(tmp.root, "x_main.json").apply { writeText("old, much longer than the new one") }
        QaClip.writeWhole(f, "new")
        assertEquals("new", f.readText())
        assertEquals(listOf("x_main.json"), tmp.root.list()!!.sorted())
    }
}
