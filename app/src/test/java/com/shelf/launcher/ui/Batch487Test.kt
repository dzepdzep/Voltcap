package com.shelf.launcher.ui

import androidx.compose.ui.graphics.Color
import com.shelf.launcher.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import com.shelf.launcher.data.Features.SaveVaultStyle as S

/** Update 487: E7 owner-approved — paged stories (D263 / D281 / D265), Play hierarchy, touch sizes, Cinema actions. */
class Batch487Test {
    private fun src(name: String): String = listOf(
        File("../src_copy/ui/$name"), File("src/main/java/com/shelf/launcher/ui/$name"), File("app/src/main/java/com/shelf/launcher/ui/$name"),
    ).first { it.exists() }.readText()

    @Test fun pagesStartOnWholeLinesAndTheLastIsFull() {
        assertEquals(emptyList<Int>(), storyPageStarts(10, 0))
        assertEquals(listOf(0), storyPageStarts(4, 5))
        assertEquals(listOf(0), storyPageStarts(5, 5))
        // 13 lines in a 9-line box (the Twin window's 007 story): page 2 ends on the last line, no empty half.
        assertEquals(listOf(0, 4), storyPageStarts(13, 9))
        assertEquals(listOf(0, 3, 6, 7), storyPageStarts(10, 3))
        storyPageStarts(37, 4).forEach { assertTrue(it + 4 <= 37) }
    }

    @Test fun pagerWrapsAndIgnoresSinglePage() {
        val p = StoryPager()
        assertFalse(p.next())
        p.pages = 3
        assertTrue(p.next()); assertTrue(p.next()); assertTrue(p.next())
        assertEquals(0, p.page)
        assertTrue(p.prev()); assertEquals(2, p.page)
        assertEquals("3 / 3", storyPageLabel(2, 3))
    }

    @Test fun litKeyWordIsReadable() {
        // Tiles: white on lilac measured 2.9 : 1.
        val lilac = Color(0xFF9D94AF)
        val k = sceneKeyColors(true, true, Color(0xFF222222), lilac, Color.White, Color.White, Color.Gray, lilac)
        assertTrue(contrastRatio(k.onInk, lilac) >= 4.5f)
        // A lit ring the same colour as the key is replaced by one that shows.
        assertTrue(contrastRatio(k.onEdge, lilac) >= 1.4f)
    }

    @Test fun playStandsOutWhereAllKeysWereAlike() {
        val a = Color(0xFF6A4FC9)
        val play = sceneKeyColors(true, false, Color.White, a, Color(0xFF222222), Color.White, Color(0xFFDDDDDD), a)
        val other = sceneKeyColors(false, false, Color.White, a, Color(0xFF222222), Color.White, Color(0xFFDDDDDD), a)
        assertTrue(play.fill != other.fill)
        assertTrue(play.firmEdge)
        assertTrue(contrastRatio(play.ink, play.fill) >= 4.5f)
    }

    @Test fun coverDetailsKeepsItsStory() {
        val c = src("StyleCoverDetails.kt")
        assertTrue(c.contains("PagedStory("))
        assertFalse(c.contains("RollingStory(story"))
        assertTrue(c.contains("val tight = !compact && story != null && maxHeight < coverDetailsTightBelow"))
    }

    @Test fun twinStoryTurnsByHandWithFactsPinned() {
        val t = src("WinSceneTwin.kt")
        assertTrue(t.contains("auto = false"))
        assertTrue(t.contains("windowKeys = true"))
        assertFalse(t.contains("SnapRows(Modifier.fillMaxWidth(), textLine"))
        assertTrue(src("GameWindow.kt").contains("WinStory.key(e.key)"))
    }

    @Test fun touchSizesAndCinemaRow() {
        assertFalse(src("WinScenesMore.kt").contains("minTouch = SCENE_KEY_TOUCH"))
        assertTrue(src("SecondScreenHome.kt").contains(".size(width = 48.dp, height = 80.dp)"))
        assertTrue(src("WinScenes.kt").contains("Box(Modifier.size(48.dp), contentAlignment = Alignment.Center)"))
        assertTrue(src("WinScenesHomes.kt").contains("the round keys run under both columns"))
    }

    @Test fun saveLooksTheSameInGridAndUpClose() {
        assertEquals(SaveObjectKind.PS2_BROWSER, saveObjectKind(true, S.CARD_3D, true))
        assertEquals(SaveObjectKind.CARD_3D, saveObjectKind(false, S.CARD_3D, true))
        assertEquals(SaveObjectKind.DRAWN, saveObjectKind(false, S.CARD_3D, false))
        assertEquals(SaveObjectKind.DRAWN, saveObjectKind(false, S.MEMORY_CARD, false))
        assertEquals(SaveObjectKind.CAPSULE, saveObjectKind(false, S.CAPSULE, false))
        assertEquals(SaveObjectKind.ICON, saveObjectKind(false, S.CLASSIC, false))
        assertTrue(src("SaveExplorer.kt").contains("look = look, state = st.state, ps2 = ps2, logo = logo"))
        // (488: the PS2 switch only for PS2 games — every other save keeps its physical look.)
        assertTrue(src("SaveExplorer.kt").contains("Ps2BrowserStyle.on.value && com.shelf.launcher.data.Ps2BrowserStyle.isPs2(game.platform), vaultStyle"))
    }

    @Test fun spinePicturesAreKeptAtScreenSize() {
        assertEquals(null, spineFitSize(80, 900))
        // (Update 488: SPINE_MAX_PX 1280 -> 960, K4 — twice as many fit the cache.)
        assertEquals(93 to 1280, spineFitSize(300, 4096, 1280))
        assertEquals(70 to 960, spineFitSize(300, 4096))
        assertEquals(960 to 70, spineFitSize(4096, 300))
        assertTrue(src("CoverCases.kt").contains("val fit = spineFitSize(src.width, src.height)"))
    }

    @Test fun screensaverAndDetailsAboveTheHomePieces() {
        assertTrue(SCREENSAVER_Z > 9f && DETAILS_Z > 6.5f && DETAILS_Z < 9f)
        val l = src("LauncherScreen.kt")
        assertTrue(l.contains("modifier = Modifier.zIndex(SCREENSAVER_Z)"))
        assertTrue(l.contains("modifier = Modifier.zIndex(DETAILS_Z)"))
    }

    @Test fun gameSpacingIsPerLayoutAndKeepsTheDefault() {
        assertEquals(0.5f, coverShelfGapDp(100), 0.001f)
        assertEquals(0f, coverShelfGapDp(50), 0.001f)
        assertEquals(6.5f, coverShelfGapDp(250), 0.001f)
        val f = File(listOf("../src_copy/data/Features.kt", "src/main/java/com/shelf/launcher/data/Features.kt", "app/src/main/java/com/shelf/launcher/data/Features.kt").first { File(it).exists() }).readText()
        assertTrue(f.contains("val shelfSpacing = IntPref(\"shelf_spacing\", 100, 50..250)"))
        assertTrue(f.contains("val cdSpacing = IntPref(\"cd_spacing\", 100, 50..250)"))
    }
}
