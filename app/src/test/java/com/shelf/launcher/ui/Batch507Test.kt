package com.shelf.launcher.ui

import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 507 (staging only): popup motion setting, P1 popup families, P0 leftovers, custom CD branding. */
class Batch507Test {
    private fun src(rel: String): String = listOf(File("../src_copy/$rel"), File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel"))
        .first { it.exists() }.readText()

    @Test fun popupMotionIsItsOwnSettingAndNothingIsMigrated() {
        assertEquals("anim_popup", Features.popupAnim.key)
        assertEquals(Features.PopupAnim.LAYOUT, Features.popupAnim.default)
        // The game's info keeps its key and its default.
        assertEquals("anim_info", Features.infoAnim.key)
        assertEquals(Features.InfoAnim.NONE, Features.infoAnim.default)
        val g = src("ui/GameWindow.kt")
        assertTrue(g.contains("val layoutMotion = if (scene != null || piece != null) MotionPrefs.popup() else MotionPrefs.info() != Features.InfoAnim.NONE"))
        assertTrue(g.contains("val anim = layoutMotion || cardFlight"))
        assertTrue(g.contains("origin.top != Float.MAX_VALUE && MotionPrefs.popup()"))
        // Instant: no system window fade either (measured: a 6-frame fade remained on the Thor before this).
        assertTrue(g.contains("|| ((scene != null || piece != null) && !MotionPrefs.popup())"))
        // Banner / Row windows still follow the game's info.
        assertTrue(src("ui/GameWindowBanner.kt").contains("val anim = MotionPrefs.info() != Features.InfoAnim.NONE"))
        assertTrue(src("ui/RowRevealWindow.kt").contains("MotionPrefs.info() != Features.InfoAnim.NONE"))
        assertTrue(src("ui/SettingsDialog.kt").contains("RowChoice(\"Popup windows\""))
    }

    @Test fun familiesHaveTheirOwnWindows() {
        val w = src("ui/WinScenes.kt")
        for (f in listOf("CardTurnOver(ctx)", "CubeTurnFace(ctx)", "HubNameplate(ctx)", "TimelineHang(ctx)", "TileLiftCard(ctx)")) assertTrue(f, w.contains(f))
        val s = src("ui/WinScenesStyled.kt")
        assertTrue(s.contains("HomeLayout.SHELF -> GameShelfWindow(ctx)") && s.contains("HomeLayout.STACKS -> CaseStackWindow(ctx)"))
        val f = src("ui/WinSceneFamilies.kt")
        // The cube's writing never has the art behind it; the card's picture is whole.
        assertFalse(f.contains("ContentScale.Crop"))
        assertFalse(f.contains("TextOverflow.Ellipsis"))
        // The case flies out of the home and back.
        val g = src("ui/GameWindow.kt")
        assertTrue(g.contains("val caseFlight = scene == WinScene.INDEX_CARD && piece == null && (layout == HomeLayout.SHELF || layout == HomeLayout.STACKS)"))
        assertTrue(g.contains("LocalSceneOrigin provides (if (discFlight || caseFlight) origin else null)"))
    }

    @Test fun theHomeCaseStaysHiddenUntilItsCaseHasLanded() {
        assertEquals(1f, caseHomeHidden(0.5f), 0f)
        assertEquals(1f, caseHomeHidden(0.01f), 0f)
        assertEquals(0f, caseHomeHidden(0f), 0f)
        // (Shelf flash work, 12 Nov: still hidden for the whole flight, no cross-fade - from the frame the flying copy is drawn,
        // CaseFlightGate; before that the home's copy is the one on screen. ShelfCaseHandOverTest covers the hand-over.)
        assertTrue(src("ui/GameWindow.kt").contains("if (caseFlight) caseHomeHiddenGated(gv, ready) else gv.coerceIn(0f, 1f)"))
        assertEquals(1f, caseHomeHiddenGated(0.01f, ready = true), 0f)
    }

    @Test fun stagesAndGeometry() {
        assertEquals(0f, sceneStage(0.2f, 0.3f, 1f), 0f)
        assertEquals(0.5f, sceneStage(0.65f, 0.3f, 1f), 0.0001f)
        assertEquals(1f, sceneStage(1f, 0.3f, 1f), 0f)
        // Thor's room: the case 238 dp wide (0.7 of a 340 dp case), the window 900 dp at most.
        val (cw, tw) = caseWindowGeometry(916f, 512f, 0.7f)
        assertEquals(238f, cw, 0.01f); assertEquals(900f, tw, 0.01f)
        // GT78's short room: a smaller case, never under 64 dp.
        val (cw2, _) = caseWindowGeometry(596f, 360f, 0.7f)
        assertTrue(cw2 <= 360f * 0.6f * 0.7f + 0.01f && cw2 >= 64f)
    }

    @Test fun twinUnfoldsFromItsHingeAndKeepsThePicturesShape() {
        assertEquals(0f, twinUnfold(0.15f), 0f)
        assertEquals(1f, twinUnfold(1f), 0f)
        assertEquals(0.75f, twinPictureRatio(300f, 400f), 0.0001f)
        assertEquals(0.56f, twinPictureRatio(100f, 1000f), 0.0001f)
        val t = src("ui/WinSceneTwin.kt")
        assertFalse("no cropped copy behind the picture", t.contains("contentScale = ContentScale.Crop, alpha = 0.3f"))
        assertTrue(src("ui/TwinScreens.kt").contains("if (twin && TwinScreens.popupReveal.floatValue > 0f) TwinPopupShade()"))
    }

    @Test fun cinemaTitleOffTheArtAndChannelKeyRenamed() {
        val h = src("ui/WinScenesHomes.kt")
        val art = h.substring(h.indexOf("internal fun CinemaPremiere"), h.indexOf("// The title line: on the page's own dark"))
        assertFalse("title drawn over the picture", art.contains("LogoOrName("))
        val wii = src("ui/WinScenesWii.kt")
        assertFalse(wii.contains("\"Wii Menu\""))
        assertTrue(wii.contains("else \"Channels\""))
    }

    @Test fun customCdBandAndColourLogoRule() {
        // A many-coloured bright logo goes on in colour; a white, a navy or a partly black one does not.
        val colour = IntArray(100) { if (it % 2 == 0) 0xFFE0201C.toInt() else 0xFF20A040.toInt() }
        assertTrue(discLogoGoesInColour(colour))
        assertFalse(discLogoGoesInColour(IntArray(100) { 0xFFFFFFFF.toInt() }))
        // (Update 508: a navy logo is in colour — it goes on outlined, Batch508Test.)
        assertTrue(discLogoGoesInColour(IntArray(100) { 0xFF1A2060.toInt() }))
        // (Update 508: a colour logo with black parts now goes on in colour, outlined — Batch508Test.)
        assertTrue(discLogoGoesInColour(IntArray(100) { if (it < 10) 0xFF000000.toInt() else 0xFFE0201C.toInt() }))
        assertFalse(discLogoGoesInColour(IntArray(5) { 0xFFE0201C.toInt() }))
        // The band stays dark: a third of the colour at its edge, under a tenth at its foot.
        val n64 = 0xFF1E5AA8.toInt()
        val top = discBandTint(n64, 0f); val foot = discBandTint(n64, 1f)
        assertEquals((0xA8 * 0.34f).toInt(), top and 0xFF)
        assertEquals((0xA8 * 0.08f).toInt(), foot and 0xFF)
        assertEquals(0xFF, top ushr 24)
        val box = discLogoBox(390, 510, 512)
        assertEquals(0.25f, box.left, 0.0001f); assertEquals(0.75f, box.right, 0.0001f)
        assertTrue(box.top > 390f / 512f && box.bottom < 510f / 512f)
    }

    @Test fun coverDetailsKeepsItsCaseInView() {
        val g = src("ui/GameWindow.kt")
        assertTrue(g.contains("layout == HomeLayout.COVERDETAILS) coverDetailsVeil"))
        assertTrue(g.contains("!(scene == WinScene.INDEX_CARD && layout == HomeLayout.COVERDETAILS)"))
        assertEquals(WinScene.INDEX_CARD, winSceneFor(HomeLayout.COVERDETAILS))
    }
}
