package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 682ag (owner visual review, 23 Nov): the pure pieces behind the TV room's teletext grid, the Arctic drawer's menu / info
 * split and the Icon bar's achievement detail plate. Fails on the 682af code (the helpers did not exist; the Arctic menu was a
 * fixed share whatever its longest word needed, the info list a fixed 92 dp field column).
 */
class Review682agTest {
    @Test fun teletextGridKeepsItsSizeWhereTheLineIsWideEnough() {
        assertEquals(1f, ttxGridScale(rowPx = 24 * 20f, cellPx = 20f), 0f)
        assertEquals(1f, ttxGridScale(rowPx = 40 * 20f, cellPx = 20f), 0f)
    }

    @Test fun teletextGridStepsDownOnANarrowSetButNeverUnderItsFloor() {
        // RP Classic x1.3: about 17 characters a line - the text steps down so 24 fit, as far as the floor allows
        val s = ttxGridScale(rowPx = 17 * 20f, cellPx = 20f)
        assertTrue(s < 1f)
        assertEquals(TTX_MIN_SCALE, s, 0.0001f)
        // (owner 23 Nov 13:32: one step, never a size that keeps shrinking with the set)
        assertEquals(TTX_MIN_SCALE, ttxGridScale(rowPx = 22 * 20f, cellPx = 20f), 0f)
        assertEquals(TTX_MIN_SCALE, ttxGridScale(rowPx = 10 * 20f, cellPx = 20f), 0f)
        assertEquals(1f, ttxGridScale(0f, 20f), 0f)
        assertEquals(1f, ttxGridScale(100f, 0f), 0f)
    }

    @Test fun arcticMenuTakesTheWidthItsLongestWordNeedsWithinItsShare() {
        val total = 380f; val gap = 18f
        // a short menu keeps the split's share
        assertEquals((total - gap) * 0.45f, arcticMenuWidth(total, 100f, gap), 0.01f)
        // "Achievements" at x1.3 needs more - the menu grows to it
        assertEquals(190f, arcticMenuWidth(total, 190f, gap), 0.01f)
        // but never past 60 % of the drawer: the info list keeps room
        assertEquals((total - gap) * 0.6f, arcticMenuWidth(total, 400f, gap), 0.01f)
    }

    @Test fun arcticInfoStacksTheFieldOverItsValueWhereTheColumnIsNarrow() {
        assertTrue(arcticStackFacts(150f)) // 150 - 92 = 58 dp for a value: stacked
        assertFalse(arcticStackFacts(260f)) // 168 dp beside a 92 dp field: side by side
    }

    @Test fun dockDetailNamesTheAchievementItsPointsAndState() {
        val (name, line, desc) = dockDetail("  Defeat the first colossus ", 6, earned = true, description = "Beat him.")
        assertEquals("Defeat the first colossus", name)
        assertEquals("6 points · Earned", line)
        assertEquals("Beat him.", desc)
        val (n2, l2, d2) = dockDetail(null, null, earned = false, description = "  ")
        assertEquals("Untitled", n2)
        assertEquals("0 points · Not earned yet", l2)
        assertNull(d2)
    }

    @Test fun terminalConfirmationIsThreeShortLines() {
        assertEquals(listOf("ARE YOU SURE? (Y/N)", "A AGAIN = YES", "MOVE AWAY = NO"), termConfirmLines())
        assertTrue(termConfirmLines().all { it.length <= 20 })
    }

    @Test fun terminalLinesKeepPathsAndSwitchesWhole() {
        val t = termKeepTokens("C:\\> DIR ACHIEVE\\ /O:SET_ORDER")
        assertEquals("C:\\> DIR ACHIEVE\\ /O:SET_ORDER", t.replace("\u2060", ""))
        assertTrue("/\u2060O:\u2060SET_ORDER" in t)
        assertFalse("\\\u2060 " in t) // nothing added before a space
    }

    @Test fun magazineCalloutsUseTwoColumnsOnlyWhereTheLongestWordFits() {
        assertTrue(magTwoColumns(widthPx = 600f, longestWordPx = 200f, arrowPx = 20f, gapPx = 16f, padPx = 40f))
        assertFalse(magTwoColumns(widthPx = 400f, longestWordPx = 200f, arrowPx = 20f, gapPx = 16f, padPx = 40f))
    }

    @Test fun nightCommandColumnFollowsItsLongestLabelWithinItsShare() {
        val w = androidx.compose.ui.unit.Dp(620f)
        assertEquals(w * 0.26f, nightCommandWidth(w, androidx.compose.ui.unit.Dp(40f)))
        assertEquals(androidx.compose.ui.unit.Dp(130f + 66f), nightCommandWidth(w, androidx.compose.ui.unit.Dp(130f)))
        assertEquals(w * 0.34f, nightCommandWidth(w, androidx.compose.ui.unit.Dp(400f)))
    }

    @Test fun sideDrawerTakesMoreOfASquarerScreenAndLargerText() {
        assertEquals(0.5f, sideDrawerShare(1280f, 720f, 1f), 0.0001f)
        assertEquals(0.58f, sideDrawerShare(620f, 540f, 1f), 0.0001f)
        assertEquals(0.62f, sideDrawerShare(620f, 540f, 1.3f), 0.0001f) // capped
        assertEquals(0.59f, sideDrawerShare(1280f, 720f, 1.3f), 0.0001f)
    }

    @Test fun nightCommandEndMarginTakesTheColumnsTurn() {
        // six commands on the Status page: ~180 dp of column, turned 4 deg - the last key drifts ~12.6 dp right (+2 dp)
        assertEquals(180f * 0.0698f + 2f, nightCmdDrift(6).value, 0.01f)
        assertTrue(nightCmdDrift(2).value < nightCmdDrift(6).value)
        assertEquals(30f * 0.0698f + 2f, nightCmdDrift(0).value, 0.01f)
    }

    @Test fun coverflowProposalKeepsEachValueTogether() {
        assertEquals("PlayStation\u00A02\u00A0\u00A0·  2005\u00A0\u00A0·  Team\u00A0Ico", coverFactsLine(listOf("PlayStation 2", " 2005 ", "Team Ico", " ")))
    }
}
