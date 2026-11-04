package com.shelf.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 454 (D164): a QA layout preview survives a category switch (the category's own / default layout is not applied). */
class PreviewCategory454Test {
    @Test fun previewKeepsItsLayoutAcrossCategories() {
        val f = listOf("src_copy/", "../src_copy/", "app/src/main/java/com/shelf/launcher/", "src/main/java/com/shelf/launcher/")
            .map { File(it + "LauncherViewModel.kt") }.first { it.exists() }
        val s = f.readText().replace("\r\n", "\n")
        val body = s.substring(s.indexOf("private fun categoryLayoutFor(next: LauncherUiState)")).take(900)
        assertTrue(body.contains("if (com.shelf.launcher.ui.TestHooks.previewOnly) return null"))
        // …and a preview never saves (unchanged since update 328).
        assertTrue(s.contains("if (com.shelf.launcher.ui.TestHooks.previewOnly) return com.shelf.launcher.data.swapLayoutLook(old, settingsIn)"))
    }
}

/** Update 454 (Thor): Pocket classic + popup style Classic — one A opened Details on its press and its release clicked Play. */
class DetailsHeldA454Test {
    @Test fun aHeldWhileDetailsOpensIsSwallowedOnRelease() {
        val s = listOf("src_copy/ui/", "../src_copy/ui/", "app/src/main/java/com/shelf/launcher/ui/", "src/main/java/com/shelf/launcher/ui/")
            .map { File(it + "LauncherScreen.kt") }.first { it.exists() }.readText().replace("\r\n", "\n")
        assertTrue(s.contains("if (HomeKeyGuard.aDown) HomeKeyGuard.swallowAUp = true\n        detailsAction = DetailsAction.PLAY"))
        val root = s.substring(s.indexOf("// (Update 454: …and the release of the A that opened the Details window on its press goes nowhere.)"))
        // a new press clears it first, the held press's release is taken before anything (the window's Play) sees it
        assertTrue(root.indexOf("event.type == KeyEventType.KeyDown) HomeKeyGuard.swallowAUp = false") < root.indexOf("return@onPreviewKeyEvent true"))
        assertTrue(root.indexOf("return@onPreviewKeyEvent true") < root.indexOf("HomeKeyGuard.trackA(event.key, event.type)\n                if (QaTrace.on && GamePopup.game"))
    }
}
