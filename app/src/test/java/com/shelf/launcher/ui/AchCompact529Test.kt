package com.shelf.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 529, achievements compact hotfix (owner: "opening Achievements should show the achievements immediately, not a huge wall
 * of controls"). Each check fails on the code before the hotfix.
 */
class AchCompact529Test {
    private fun src(name: String) = listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")
        .map(::File).first { it.exists() }.readText()

    @Test fun noRowOpensBecauseTheDpadRestsOnIt() {
        val s = src("AchievementListDialog.kt")
        assertFalse(s.contains("restingId"))
        assertFalse(s.contains("kotlinx.coroutines.delay(380)"))
        assertFalse(s.contains("Features.achCommentsAuto.state.value"))
        // The Settings switch for it is gone too (it had nothing left to switch).
        assertFalse(src("SettingsDialog.kt").contains("checked = com.shelf.launcher.data.Features.achCommentsAuto.state.value"))
    }

    @Test fun commentsAreFetchedOnlyWhenOpened() {
        val l = src("AchievementListDialog.kt")
        assertFalse(l.contains("detailId = id; onLoadComments(id)"))
        assertFalse(l.contains("detailId = a.id; onLoadComments(a.id)"))
        val d = src("AchievementDetailDialog.kt")
        assertFalse(d.contains("LaunchedEffect(achievement.id) { if (comments == null) onLoadComments() }"))
        assertFalse(d.contains("LaunchedEffect(achievement.id) { onLoadComments() }"))
        assertTrue(d.contains("if (commentsOpen && comments == null && !commentsLoading) onLoadComments()"))
        val w = src("GameWindow.kt")
        assertFalse(w.contains("if (!opened) onLoadComments(a.id)"))
        assertTrue(w.contains("if (commentsOpen && comments == null && !loading) onLoad()"))
    }

    @Test fun controlsAreOneBarWithFoldedPanels() {
        val s = src("AchievementListDialog.kt")
        assertTrue(s.contains("AchControlBar("))
        for (p in listOf("AchPanel.FILTER", "AchPanel.SORT", "AchPanel.VIEW", "AchPanel.SESSION")) assertTrue(s.contains("AchPanelBody(panel == $p)"))
        // The filter / order / view chips no longer stand open above the list, nor the help text, nor the Last session card.
        assertFalse(s.contains("Text(\"LAST SESSION\""))
        assertFalse(s.contains("Chip(if (autoComments) \"✓ Comments\" else \"Comments\""))
        // The list opens on its first achievement.
        assertTrue(s.contains("Modifier.initialFocus()"))
    }

    @Test fun bClosesTheInnerThingFirst() {
        val s = src("AchievementListDialog.kt")
        assertTrue(s.contains("BackHandler(enabled = detailId != null)"))
        assertTrue(s.contains("BackHandler(enabled = panel != null)"))
        val d = src("AchievementDetailDialog.kt")
        assertTrue(d.contains("BackHandler(enabled = commentsOpen)"))
        assertTrue(d.contains("BackHandler(enabled = detailsOpen || commentsOpen)"))
        assertTrue(src("GameWindow.kt").contains("BackHandler(enabled = commentsOpen) { commentsOpen = false }"))
    }

    @Test fun foldsAreQuick() {
        assertTrue(ACH_FOLD_MS in 150..220)
    }
}
