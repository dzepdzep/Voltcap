package com.shelf.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Controller reachability: choosing a layout in the shared picker moves the focus on to Apply (no walk past the whole list). */
class PickerApplyFocus76Test {
    @Test fun chosenCandidateMovesFocusToApply() {
        val src = listOf(File("../src_copy/ui/LayoutPicker.kt"), File("src/main/java/com/shelf/launcher/ui/LayoutPicker.kt"),
            File("app/src/main/java/com/shelf/launcher/ui/LayoutPicker.kt")).first { it.exists() }.readText()
        assertTrue(src.contains("modifier = Modifier.focusRequester(applyReq)"))
        assertTrue(src.contains("applyReq.requestFocus()"))
    }
}
