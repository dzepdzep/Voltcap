package com.shelf.launcher.ui

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Phase 4 (spine flicker, 12 Nov): rememberDerived (Case3D's spine pipeline) on every frame while its source picture goes
 * through what a scrolling shelf does to it. The derived spine is tagged with the address it was made from, so a frame
 * that shows NOTHING (the dark waiting strip: EMPTY) and a frame that shows ANOTHER game's spine (SWAP) are told apart.
 * Not hardware: the device's decode timing and GPU upload are not in this.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class SpineCacheLifecycleRoboTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private class Made(val from: String)

    private var src by mutableStateOf<Bitmap?>(null)
    private var model by mutableStateOf<String?>(null)
    private val seen = mutableListOf<String>()
    private var makes = 0

    private fun bmp() = Bitmap.createBitmap(8, 40, Bitmap.Config.ARGB_8888)

    private fun frames(n: Int) = repeat(n) {
        Thread.sleep(3)   // (the derive runs on Dispatchers.Default, a real thread)
        compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun start() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val m = model
            val v = rememberDerived<Made>(src, "spineTest|0", model = m) { _ -> makes++; Made(m ?: "?") }
            seen += v?.from ?: "EMPTY"
        }
    }

    /** What was shown from frame [from] on (composition records once per recomposition). */
    private fun since(from: Int) = seen.drop(from)

    @Test fun sameArtworkDecodedAgainNeverShowsTheDarkStrip() {
        start()
        model = "spine://a|600"; src = bmp(); frames(10)
        assertEquals("a made", "spine://a|600", seen.last())
        val mark = seen.size
        // Coil lets the picture go (scrolled away) and decodes it again: a new Bitmap for the same address.
        src = null; frames(3)
        src = bmp(); frames(10)
        assertTrue("EMPTY / other spine while the same art was decoded again: ${since(mark)}", since(mark).all { it == "spine://a|600" })
    }

    @Test fun twoCustomSpinesNeverShowTheOldOneForTheNewGame() {
        start()
        model = "spine://a|600"; src = bmp(); frames(10)
        val mark = seen.size
        // the next game: its address and its picture change together (the picture arrives a few frames later)
        model = "spine://b|600"; src = null; frames(4)
        src = bmp(); frames(10)
        val after = since(mark)
        assertTrue("A's spine shown for B: $after", after.none { it == "spine://a|600" })
        assertEquals("spine://b|600", after.last())
    }

    @Test fun backToAGameAlreadyMadeIsImmediateWithoutItsPicture() {
        start()
        model = "spine://a|600"; src = bmp(); frames(10)
        model = "spine://b|600"; src = bmp(); frames(10)
        val mark = seen.size
        // back to A: its address is known; its picture is not decoded yet
        model = "spine://a|600"; src = null; frames(2)
        assertEquals("A's spine at once, from its address (no dark strip)", "spine://a|600", seen.last())
        src = bmp(); frames(10)
        assertTrue(since(mark).all { it == "spine://a|600" })
    }

    @Test fun rapidSwitchingSettlesOnTheRightSpine() {
        start()
        val keys = listOf("a", "b", "c", "a", "d", "b")
        for (k in keys) { model = "spine://$k|600"; src = bmp(); frames(1) }
        frames(20)
        assertEquals("spine://b|600", seen.last())
        // never a spine of a game that wasn't asked for in that frame's model... at most the asked one or EMPTY while made
        assertTrue(seen.all { it == "EMPTY" || it.startsWith("spine://") })
    }

    @Test fun firstTimeIsTheOnlyEmptyAndItIsBrief() {
        start()
        model = "spine://new|600"; src = bmp(); frames(10)
        val empties = seen.takeWhile { it == "EMPTY" }.size
        assertTrue("a spine never seen before: the waiting strip for at most a few frames ($empties)", empties <= 3)
        assertTrue(seen.drop(empties).all { it == "spine://new|600" })
    }
}
