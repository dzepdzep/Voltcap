package com.shelf.launcher.ui

import com.shelf.launcher.data.RaAchievementDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 503 (staging only): badges shown as one panel + warmed for the neighbours, the late-art QA fixture. */
class Batch503Test {
    private fun src(rel: String): String = listOf(File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel")).first { it.exists() }.readText()

    private fun a(id: Int, earned: Boolean) = RaAchievementDetail(id, "t$id", null, 5, "b$id", null, if (earned) "2026-01-01" else null, null, displayOrder = id)

    @Test fun warmIsBoundedAndKeepsTheStripOrder() {
        val list = (0 until 40).map { a(it, it % 3 == 0) }
        val urls = BadgeWarm.urls(list)
        assertEquals(BadgeWarm.PER_GAME, urls.size)
        assertEquals(urls.size, urls.distinct().size)
        assertTrue(urls.all { it.startsWith("https://media.retroachievements.org/Badge/b") })
        assertTrue(urls.first().endsWith("/b0.png")) // the first earned one leads
        assertTrue(BadgeWarm.urls(emptyList()).isEmpty())
    }

    @Test fun everyNavigationStripIsOnePanel() {
        for (f in listOf("ui/HomeParts.kt", "ui/StyleBlades.kt", "ui/StyleBubbles.kt", "ui/StyleMagazine.kt", "ui/HeroCarousel.kt", "ui/StyleSpotlight.kt"))
            assertTrue(f, src(f).contains(".badgeSet("))
        assertTrue(src("ui/LauncherScreen.kt").contains("BadgeWarmEffect(visibleGames, state.focusedIndex, state.raAchievementDetails)"))
        // Cache-only: the warm-up never asks the network.
        assertTrue(src("ui/BadgeWarm.kt").contains(".networkCachePolicy(CachePolicy.DISABLED)"))
    }

    @Test fun artFixtureSpec() {
        assertEquals(800L, QaArtFixture.parse("delay=800"))
        assertEquals(0L, QaArtFixture.parse("delay=0"))
        assertEquals(5000L, QaArtFixture.parse("delay=99999"))
        assertNull(QaArtFixture.parse("off"))
        // Only in the isolated QA app.
        assertTrue(src("data/ArtStorage.kt").contains("if (QaVariant.isolated(context)) add(com.shelf.launcher.ui.QaArtFixture.Delay())"))
        assertTrue(src("LauncherViewModel.kt").contains("if (!com.shelf.launcher.data.QaVariant.isolated(app)) return"))
    }

    @Test fun aFailedLogoIsRememberedSoTheNameDoesNotJump() {
        val u = "file:///qa/missing_logo_503.png"
        assertTrue(!LogoAspects.isFailed(u))
        LogoAspects.noteFailed(u)
        assertTrue(LogoAspects.isFailed(u))
        val t = src("ui/GameCard.kt")
        assertTrue(t.contains("mutableStateOf(logoUrl != null && LogoAspects.isFailed(logoUrl))"))
        assertTrue(t.contains("onError = { LogoAspects.noteFailed(logoUrl); failed = true }"))
        // The logo part shows the name instead of staying empty.
        assertTrue(src("ui/HomeParts.kt").contains("if (model != null && !failed.value)"))
        // Arctic / the other layouts' logo-or-name slot: the name at once on later visits.
        val h = src("ui/HomeCollections.kt")
        assertTrue(h.contains("LogoAspects.isFailed(logo) ||") && h.contains("onError = { LogoAspects.noteFailed(logo); square = true }"))
    }

    @Test fun caseStacksSpinesUseTheShelfDepth() {
        assertTrue(src("ui/StyleCaseStacks.kt").contains("Modifier.fillMaxSize().spineDepth(g.stableKey.hashCode())"))
    }
}
