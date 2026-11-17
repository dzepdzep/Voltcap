package com.shelf.launcher.ui

import com.shelf.launcher.data.RaAchievementDetail
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 678o (HW-20 QA hook): `--ei voltcap_test_achnew N` with `--ez voltcap_test_achlist true` makes the focused game's N
 * newest unlocks count as new once more before its Achievements page opens - so 678m's "opens on the new unlocks" can be checked
 * on a handheld without a real RetroAchievements unlock. Isolated QA app only, the number used once.
 */
class AchNewHook678oTest {
    private fun a(id: Int, earned: String?) = RaAchievementDetail(id, "A$id", null, 5, null, null, earned, null)

    @Test fun newestUnlocksAreTheLatestEarned() {
        val list = listOf(a(1, "2026-10-01 10:00:00"), a(2, null), a(3, "2026-11-02 09:00:00"), a(4, "2026-11-01 23:59:59"), a(5, null))
        assertEquals(listOf(3, 4), qaNewestUnlocks(list, 2))
        assertEquals("locked achievements never count", listOf(3, 4, 1), qaNewestUnlocks(list, 9))
        assertEquals(emptyList<Int>(), qaNewestUnlocks(emptyList(), 2))
    }

    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun hookIsQaOnlyAndRunsBeforeThePageOpens() {
        val hooks = source("ui/TestHooks.kt")
        assertTrue("the hook is read only in the isolated QA app",
            hooks.contains("""getIntExtra("voltcap_test_achnew", 0)?.let { n -> if (n > 0 && com.shelf.launcher.data.QaVariant.isolated(context)) achNew = n }"""))
        val dlg = source("ui/AchievementListDialog.kt")
        val fn = dlg.substring(dlg.indexOf("internal fun qaMarkNewUnlocks("))
        assertTrue("used once", fn.indexOf("TestHooks.achNew = 0") in 0 until fn.indexOf("qaMarkNew(context"))
        assertTrue("QA app only, also at the call", fn.contains("!com.shelf.launcher.data.QaVariant.isolated(context)) return"))
        val ls = source("ui/LauncherScreen.kt")
        val call = ls.indexOf("qaMarkNewUnlocks(testCtx, g, state.raAchievementDetails[g.stableKey])")
        assertTrue("marked before the page opens", call >= 0 && call < ls.indexOf("viewingAchievementsForGame = g", call))
        val seen = source("data/AchievementSeen.kt")
        assertTrue("only the hook calls qaMarkNew", Regex("""qaMarkNew\(""").findAll(dlg + ls).count() == 1 && seen.contains("fun qaMarkNew("))
    }
}
