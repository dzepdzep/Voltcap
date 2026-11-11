package com.shelf.launcher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.shelf.launcher.data.Features
import com.shelf.launcher.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 505 (staging only): batch 505 — the last fixed-colour popup scenes follow their home, Case Flow's staged entrance, QA labels. */
class Batch505Test {
    private fun src(rel: String): String = listOf(File("../src_copy/$rel"), File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel"))
        .first { it.exists() }.readText()

    @Test fun sceneLitTakesTheLayoutsOwnColourUnlessTheLayoutAsksForAnAccent() {
        val own = Color(0xFFFF4FD8); val mine = Color(0xFF00C853); val ink = Color(0xFF1C1C1C)
        // No colours of its own for this layout: the home's own identity colour.
        val a = sceneLitFor(false, mine, own, ink)
        assertTrue(contrastRatio(a, ink) >= 2.95f)
        assertTrue(contrastRatio(a, own) < 1.2f) // still the shop's neon, not the theme's
        // Your colours for this layout: the resolved accent.
        val b = sceneLitFor(true, mine, own, ink)
        assertTrue(contrastRatio(b, mine) < 1.4f)
        // Always readable: a dark accent on a dark ground is lightened, a light one on paper darkened.
        assertTrue(contrastRatio(sceneLitFor(true, Color(0xFF151A3A), own, Color(0xFF0A0D22)), Color(0xFF0A0D22)) >= 2.95f)
        assertTrue(contrastRatio(sceneLitFor(true, Color(0xFFFFF59D), own, Color(0xFFEDE4D3)), Color(0xFFEDE4D3)) >= 2.95f)
    }

    @Test fun crossWashIsSharedAndTheColumnStaysDark() {
        assertNull(crossWash(Features.CrossTint.NONE, Color.White, Color.Red))
        assertEquals(Color.White to Color.Red, crossWash(Features.CrossTint.THEME, Color.White, Color.Red))
        // A light theme's background becomes a dark column white writing reads on.
        val deep = sceneDark(Color(0xFFF3F4F6))
        assertTrue(deep.luminance() <= 0.06f && contrastRatio(Color.White, deep) >= 7f)
        assertTrue(src("ui/StyleCrossMenu.kt").contains("crossWash(tint, ShelfTheme.colors.background, accent)"))
    }

    @Test fun everyBatchSceneUsesTheSharedResolver() {
        val h2 = src("ui/WinScenesHomes2.kt")
        for (l in listOf("HomeLayout.CROSS", "HomeLayout.SPOT", "HomeLayout.LIBRARY")) assertTrue(l, h2.contains("sceneLit(com.shelf.launcher.data.$l"))
        val c = src("ui/WinScenesConsole.kt")
        assertTrue(c.contains("sceneLit(com.shelf.launcher.data.HomeLayout.SHOP, shopColors(com.shelf.launcher.data.Features.rsWall.state.value).neon, ink)"))
        assertTrue(c.contains("sceneLit(com.shelf.launcher.data.HomeLayout.BUBBLES, bubbleWallLit(wallNow), ground)"))
        val m = src("ui/WinScenesMore.kt")
        assertTrue(m.contains("sceneLit(com.shelf.launcher.data.HomeLayout.CRATE,") && m.contains("sceneLit(com.shelf.launcher.data.HomeLayout.ARTLIB,"))
        assertTrue(src("ui/WinScenesStyled.kt").contains("sceneLit(HomeLayout.POCKET_CLASSIC, own, paper)"))
        // The fixed colours are gone from those scenes.
        assertTrue(!h2.contains("val green = Color(0xFF5BA32B)") && !c.contains("val neon = Color(0xFFFF4FD8)"))
        assertTrue(!src("ui/WinScenesStyled.kt").contains("val blue = Color(0xFF4A78CF)"))
        // Rental Shop / Bubbles: the dim behind follows the wall too.
        assertTrue(src("ui/GameWindow.kt").contains("sceneVeilFor(it)"))
        // Disc Station and Circuit (504) unchanged.
        assertTrue(m.contains("val lcd = legibleArtAccent(ShelfTheme.accent, lcdBg, lcdBg)") || m.contains("legibleArtAccent(") )
        assertTrue(h2.contains("circuitSheetColorsFor("))
    }

    @Test fun bubbleWallsGiveAReadableRing() {
        for (w in Features.BvWall.values()) {
            val ground = sceneDark(bubbleWallInk(w), 0.03f)
            assertTrue(w.name, contrastRatio(sceneLitFor(false, Color.Red, bubbleWallLit(w), ground), ground) >= 2.95f)
        }
    }

    @Test fun caseFlowOpensFromTheCaseAndReadsTheRevealWhileDrawing() {
        val t = src("ui/WinScenes.kt")
        assertTrue(t.contains("translationX = (1f - e) * -32.dp.toPx()"))
        assertTrue(t.contains("val e = sceneEase(((ctx.reveal() - 0.3f) / 0.45f).coerceIn(0f, 1f))"))
    }

    /** Device finding (Thor, printed test disc): a drag on the disc out of its case left its slow turn running under the finger. */
    @Test fun handsOnWhatCameOutPausesItsTurnToo() {
        val m = src("ui/MediaInspectView.kt")
        assertTrue(m.contains("fun releaseContent(scope: CoroutineScope, touch: Boolean = true)"))
        assertTrue(m.contains("val dur = if (touch) INSPECT_IDLE_RETURN_MS else Features.inspectReturnSpeed.state.value.ms"))
        assertTrue(m.contains("if (content) motion.releaseContent(this, touch = false)"))
        assertTrue(m.contains("if (content) motion.idlePaused.value = true"))
        // The content's own Initial-pass hands-on handler, before its tap / drag.
        val i = m.indexOf("// (Update 505: hands on what came out")
        assertTrue(i > 0 && m.indexOf("motion.touchDown()", i) in i..(i + 900) && m.indexOf("motion.touchUp(scope)", i) in i..(i + 2600)) // (Batch 523: its pinch zoom added in the same handler)
    }

    /** Thor repro (long-title fixtures): Hero wheel side names ended in "…" after two lines; the rows have room for three. */
    @Test fun heroWheelNamesTakeAThirdLineWhereTheRowsHaveRoom() {
        assertEquals(3, heroWheelNameLines(48f, 12f))     // a side name, 10 sp
        assertEquals(2, heroWheelNameLines(48f, 15.6f))   // the one you're on, 13 sp (it slides when longer)
        assertEquals(2, heroWheelNameLines(34f, 12f))     // compact wide covers: no room, as before
        val t = src("ui/HeroCarousel.kt")
        assertTrue(t.contains("gameDisplayName(g.name), scrolling = sel > 0.5f, lines = nameLines,"))
    }

    /** Thor repro: template spines dropped the last word ("…A Link To The") — measured without the drawn style's letter spacing. */
    @Test fun aSpineNameIsMeasuredInTheStyleItIsDrawnIn() {
        val base = androidx.compose.ui.text.TextStyle(letterSpacing = androidx.compose.ui.unit.TextUnit(0.5f, androidx.compose.ui.unit.TextUnitType.Sp))
        val st = spineNameStyle(base, 9f)
        assertEquals(0.5f, st.letterSpacing.value, 0.001f)
        assertEquals(9f, st.fontSize.value, 0.001f)
        val t = src("ui/ShelfHome.kt")
        assertTrue(t.contains("name, spineNameStyle(base, s),") && t.contains("style = spineNameStyle(base, fit.first),"))
    }

    @Test fun theQaAppReadsOnlyItsOwnLabelsFolder() {
        val t = src("data/LabelFolder.kt")
        assertTrue(t.contains("if (QaVariant.isolatedNow)") && t.contains("\"Android/data/com.shelf.launcher\" + QaVariant.SUFFIX + \"/files/Labels\""))
        assertTrue(t.contains("else listOf(java.io.File(android.os.Environment.getExternalStorageDirectory(), \"Voltcap/Labels\"))"))
    }
}
