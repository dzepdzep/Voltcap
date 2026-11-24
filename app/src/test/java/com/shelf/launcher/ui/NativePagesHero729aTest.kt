package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 729a (class C): the Hero banner (Hero carousel + Hero tiles) draws its own billboard pages - not the shared page bodies. */
class NativePagesHero729aTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText()

    @Test fun bothHeroLayoutsHaveTheBannersOwnPages() {
        for (l in listOf(HomeLayout.HERO, HomeLayout.HERO_TILES)) assertEquals(WinScene.HERO_BANNER, winSceneFor(l))
        assertTrue(nativePages(WinScene.HERO_BANNER))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.HERO_BANNER, p))
        val f = src("WinScenesHomes2.kt")
        val start = f.indexOf("internal fun HeroBanner(")
        val scene = f.substring(start, f.indexOf("\n}\n", start))
        assertTrue(scene.contains("HeroPage(ctx, pg, pageLive, a, Modifier.fillMaxSize())"))
        assertTrue(scene.contains(".nativeBumpers(ctx)") && scene.contains("slots = sceneKeySlots(ctx)"))
        assertTrue(scene.contains("HeroTabStrip(nativeTabs(ctx.pages), heading)"))
        assertFalse(scene.contains("ctx.pageBody("))
        // only Performance falls back to the shared page
        assertEquals(1, src("WinPagesHero.kt").split("ctx.pageFor(").size - 1)
    }

    @Test fun pageNames() {
        assertEquals(listOf("SPOTLIGHT", "THE FEATURE", "HALL OF FAME", "QUICK ACTIONS"), NATIVE_TAB_ORDER.map(::heroPageName))
    }

    @Test fun hallOfFameWords() {
        assertEquals("Unlocked 12 Mar 2024", heroUnlockLine(true, "12 Mar 2024"))
        assertEquals("Unlocked", heroUnlockLine(true, null)); assertEquals("Unlocked", heroUnlockLine(true, " "))
        assertEquals("Locked", heroUnlockLine(false, "12 Mar 2024"))
        assertEquals("3 / 12", heroFigure(3, 12))
        assertEquals("achievement unlocked", heroFigureWords(1)); assertEquals("achievements unlocked", heroFigureWords(12))
        assertEquals(0.25f, heroProgress(3, 12), 1e-6f); assertEquals(0f, heroProgress(0, 0), 0f); assertEquals(1f, heroProgress(9, 4), 0f)
        assertEquals("25 pts", heroPoints(25)); assertEquals("1 pt", heroPoints(1)); assertNull(heroPoints(null))
    }

    @Test fun noTruncationAndNoIntrinsicsOnThePages() {
        val c = src("WinPagesHero.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
        assertFalse(c.contains("IntrinsicSize"))
    }
}
