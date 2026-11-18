package com.shelf.launcher.ui

import androidx.compose.ui.unit.Constraints
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 684a (H16): the no-art plate's console caption. Timeline's compact plate is 52 dp inside and "PlayStation" at the readable
 * minimum broke inside the word (WindowWordsSurvey682Test TIMELINE x1.0 / x1.3 failed on the old code). Where the window names
 * the console beside the plate, a caption that cannot keep its words whole is left out; everywhere else it stays.
 */
class NoArtCaption684Test {
    @Test fun aWordThatFitsKeepsTheCaption() = assertTrue(noArtCaptionShown(true, 48, 52))

    @Test fun aTooWideWordBesideTheNamedConsoleDropsTheCaption() = assertFalse(noArtCaptionShown(true, 57, 52))

    @Test fun withoutTheNameBesideTheCaptionAlwaysStays() = assertTrue(noArtCaptionShown(false, 57, 52))

    @Test fun unboundedRoomKeepsTheCaption() = assertTrue(noArtCaptionShown(true, 900, Constraints.Infinity))
}
