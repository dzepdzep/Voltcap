package com.shelf.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 685a (H16, Card binder + Storage boxes under the window guards): the binder card's front, once turned edge-on, is out of
 * sight and leaves the semantics (its texts were reported "hidden by a parent" on KONKR x1.0 and x1.3 - read twice by a screen
 * reader); Storage's fact labels keep their words whole ("LAST PLAYED" lost "PLAYED" at x1.3). WindowWordsSurvey682Test
 * NEXT_685 is the rendered guard; these pin the rule and the source.
 */
class CardFront685Test {
    @Test fun theFrontIsAwayOnlyBeforeItShowsAndAfterItsHalfOfTheTurn() {
        assertTrue(cardFrontAway(0f))
        assertTrue(cardFrontAway(0.01f))
        assertFalse(cardFrontAway(0.1f))
        assertFalse(cardFrontAway(0.45f))
        assertTrue(cardFrontAway(0.5f))
        assertTrue(cardFrontAway(1f))
    }

    private fun src(name: String) = listOf("app/src/main/java/com/shelf/launcher/ui/$name", "src/main/java/com/shelf/launcher/ui/$name")
        .map(::File).first { it.exists() }.readText()

    @Test fun theTurnedAwayFrontIsNotComposed() {
        val s = src("WinSceneFamilies.kt")
        assertTrue(s.contains("if (!pickedUp && !frontAway) Box(Modifier.matchParentSize().graphicsLayer {"))
    }

    @Test fun storageFactLabelsKeepWholeWords() {
        val s = src("WinScenes.kt")
        assertFalse(s.contains("Text(label.uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, maxLines = 1)"))
        assertTrue(s.contains("WholeWordsText(label.uppercase(), color = Color.White, fontSize = 11.sp"))
    }
}
