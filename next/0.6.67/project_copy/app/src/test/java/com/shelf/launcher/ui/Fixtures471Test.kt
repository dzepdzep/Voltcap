package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 471: Cover details' facts keep every value (the header line has the console and year, so those two are not
 * repeated — nothing else is dropped), and the Box sheet's story opening is whole sentences, never cut mid-word.
 */
class Fixtures471Test {
    private val facts = listOf(
        "Console" to "Nintendo 64", "Year" to "2000", "Made by" to "HAL Laboratory",
        "Genre" to "Simulator, Strategy", "Played" to "Never played", "Trophies" to "0 / 109"
    )

    @Test fun coverDetailsKeepsEveryOtherFact() {
        val shown = shownFacts(facts, COVER_DETAIL_OMIT)
        assertEquals(listOf("Made by", "Genre", "Played", "Trophies"), shown.map { it.first })
        assertEquals("HAL Laboratory", shown.first { it.first == "Made by" }.second)
        assertEquals("Simulator, Strategy", shown.first { it.first == "Genre" }.second)
    }

    @Test fun coverDetailsMoreMovesWhatYouPlayedToItsOwnLine() {
        // With More open every fact is on the two lines under the name: nothing left in the list, nothing lost.
        assertEquals(emptyList<String>(), shownFacts(facts, COVER_DETAIL_OMIT_MORE).map { it.first })
        assertEquals("Nintendo 64 · 2000 · HAL Laboratory · Simulator, Strategy", coverInfoLine("Nintendo 64", "2000", "HAL Laboratory", "Simulator, Strategy"))
        assertEquals("Nintendo 64", coverInfoLine("Nintendo 64", null, null, " "))
        assertEquals("Never played  ·  0 / 109 trophies", coverPlayedLine("Never played", null, 0, 109))
        assertEquals("Played 3 h  ·  last Yesterday", coverPlayedLine("3 h", "Yesterday", 0, 0))
    }

    @Test fun nothingOmittedMeansEverything() {
        assertEquals(facts, shownFacts(facts, emptySet()))
    }

    @Test fun storyLeadIsWholeSentences() {
        val story = "While Pokémon Stadium 2 does have a storyline, progress in the game can be made by winning Cups in the Stadium. " +
            "When all Cups have been won and the Gym Leader Castle completed, the player's Rival will appear. Defeating the Rival unlocks Round 2."
        val (lead, more) = storyLead(story)
        assertTrue(more)
        assertTrue(lead.endsWith("."))
        assertTrue(story.startsWith(lead))
        assertFalse(lead.contains('…'))
    }

    @Test fun shortStoryIsWhole() {
        val (lead, more) = storyLead("A short story.")
        assertEquals("A short story.", lead)
        assertFalse(more)
    }

    @Test fun noSentenceEndCutsAtAWord() {
        val story = (1..80).joinToString(" ") { "word$it" }
        val (lead, more) = storyLead(story)
        assertTrue(more)
        assertTrue(story.startsWith(lead))
        assertTrue(story[lead.length] == ' ')
    }
}
