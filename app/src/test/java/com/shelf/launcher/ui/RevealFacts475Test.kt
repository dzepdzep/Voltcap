package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 475 (D245): the row-style window keeps every fact, the achievements count included, whatever the story's length. */
class RevealFacts475Test {
    @Test fun everyFactAndTheAchievementsCount() {
        val f = revealFacts("HAL Laboratory", "Simulator", 2000, 3, 109)
        assertEquals(listOf("Developer", "Genre", "Released", "Achievements"), f.map { it.first })
        assertEquals("3 / 109", f.last().second)
    }

    @Test fun missingOnesAreLeftOutNotBlank() {
        assertTrue(revealFacts(null, " ", null, 0, 0).isEmpty())
        assertEquals(listOf("Released"), revealFacts("", null, 1999, 0, 0).map { it.first })
    }

    @Test fun theStoryNeverComesBeforeTheFactsInTheLayout() {
        // (The story is the Column's only weighted child: Compose measures it last, in the room the facts leave.)
        val src = java.io.File("src/main/java/com/shelf/launcher/ui/RowRevealWindow.kt").readText()
        val room = src.substringAfter("// ---- what appears in the room they leave ----").substringBefore("// ---- Play / Continue")
        assertTrue(room.contains("StoryLines("))
        assertTrue(room.indexOf("StoryLines(") < room.indexOf("revealFacts("))
        assertTrue(room.contains("Modifier.weight(1f, fill = false)"))
    }
}

/** Update 475 (D246 / D247): rows are shown whole or not at all. */
class WholeRows475Test {
    @Test fun storyShowsWholeLinesOrNone() {
        assertEquals(0, storyLinesThatFit(17, 18f, 4))
        assertEquals(2, storyLinesThatFit(40, 18f, 4))
        assertEquals(4, storyLinesThatFit(400, 18f, 4))
        assertEquals(4, storyLinesThatFit(androidx.compose.ui.unit.Constraints.Infinity, 18f, 4))
    }

    @Test fun onlyRowsThatFitWhole() {
        assertEquals(1, wholeRowsThatFit(listOf(30, 30, 30), 59))
        assertEquals(2, wholeRowsThatFit(listOf(30, 30, 30), 60))
        assertEquals(0, wholeRowsThatFit(listOf(30), 29))
        assertEquals(3, wholeRowsThatFit(listOf(30, 30, 30), Int.MAX_VALUE))
    }
}

/** Update 475 (D248): logo shapes kept on disk by a hashed address — the title's place no longer differs on the first visit. */
class LogoAspects475Test {
    @Test fun roundTripAndNoAddressInTheFile() {
        val url = "https://example.invalid/media?ssid=someone&sspassword=secret&id=1"
        val k = LogoAspects.keyOf(url)
        assertEquals(16, k.length)
        val text = LogoAspects.encode(mapOf(k to 2.5f)) + "garbage\n" + LogoAspects.line(k, 3.25f)
        assertTrue(!text.contains("example") && !text.contains("secret"))
        assertEquals(3.25f, LogoAspects.decode(text)[k]!!, 0.0001f)
        assertEquals(1, LogoAspects.decode(text).size)
    }
}
