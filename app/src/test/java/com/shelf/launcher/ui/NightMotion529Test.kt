package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 529 (owner: Moonlight - the Night menu - with Persona 3 Reload-like motion): the motion language's rules. */
class NightMotion529Test {
    private fun src(name: String) = listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")
        .map(::File).first { it.exists() }.readText()

    @Test fun everyLayerIsInPlaceAtTheEndWhateverItsStagger() {
        for (i in 0..12) for (gap in listOf(0.03f, 0.05f, 0.1f)) for (span in listOf(0.3f, 0.5f, 0.9f)) {
            assertEquals(1f, NightMotion.layer(1f, i.toFloat(), gap, span), 0f)
            assertEquals(0f, NightMotion.layer(0f, i.toFloat(), gap, span), 0f)
        }
        // Later layers start later.
        assertTrue(NightMotion.layer(0.3f, 0f, 0.1f, 0.5f) > NightMotion.layer(0.3f, 2f, 0.1f, 0.5f))
    }

    @Test fun theEaseNeverOvershoots() {
        var last = 0f
        for (i in 0..100) {
            val v = NightMotion.ease(i / 100f)
            assertTrue(v in 0f..1f && v >= last); last = v
        }
        assertEquals(1f, NightMotion.ease(1f), 0f)
    }

    @Test fun theListIsStraightAtRest() {
        assertEquals(0f, NightMotion.bend(0f, 3f, 100f), 0f)
        assertTrue(NightMotion.bend(1f, 3f, 100f) > NightMotion.bend(1f, 1f, 100f))
        // (A long glide bends no more than two rows' worth.)
        assertEquals(NightMotion.bend(2f, 3f, 100f), NightMotion.bend(9f, 3f, 100f), 0f)
    }

    @Test fun timingsStayInTheOrdinaryRange() {
        assertTrue(NightMotion.ENTER_MS in 300..600 && NightMotion.ENTER_LITE_MS < NightMotion.ENTER_MS)
        assertTrue(NightMotion.CAT_MS in 180..400)
        assertEquals(400, sceneOpenMs(WinScene.NIGHT_STATUS))
        assertEquals(320, sceneCloseMs(WinScene.NIGHT_STATUS))
    }

    /** Permanent rule 14: a category change moves the games only - the frame's cover, the name and the tag change at once. */
    @Test fun aCategoryChangeOnlyMovesTheGames() {
        val s = src("StyleNightMenu.kt")
        assertTrue(s.contains("tween(if (animate && !CategorySlide.justChanged()) 200 else 0)"))
        assertTrue(s.contains("if (step == 0 || !animate || CategorySlide.justChanged()) { kick.snapTo(0f)"))
        // The sweep is the games' own (in the bars' graphicsLayer), and drawn at its start in the frame the category changes.
        assertTrue(s.contains("if (catOn && CategorySlide.tick != catSeen) 0f else v"))
        assertTrue(s.contains("(1f - catE) * catDir * wPx * 0.2f"))
    }

    @Test fun nothingLoopsOnceSettled() {
        val s = src("StyleNightMenu.kt")
        val list = s.substring(s.indexOf("private fun NightList("), s.indexOf("private fun androidx.compose.ui.graphics.drawscope.DrawScope.nightWave("))
        // No infinite transition / clock in the list: every movement is a one-shot Animatable or a state-driven spring.
        assertTrue(!list.contains("rememberInfiniteTransition") && !list.contains("rememberGcClock") && !list.contains("while (true)"))
        // The waves draw nothing once they have run.
        assertTrue(list.contains("if (t >= 1f) return@Canvas"))
    }
}
