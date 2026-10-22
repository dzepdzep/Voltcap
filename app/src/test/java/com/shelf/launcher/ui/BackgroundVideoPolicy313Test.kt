package com.shelf.launcher.ui

import com.shelf.launcher.data.BackgroundStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression for user backgrounds being replaced by a delayed fullscreen clip. */
class BackgroundVideoPolicy313Test {
    @Test fun onlyGameArtModesPermitFullscreenGameplayPreview() {
        assertTrue(allowsFullscreenGameplayVideo(BackgroundStyle.ART))
        assertTrue(allowsFullscreenGameplayVideo(BackgroundStyle.BLURRED))
    }

    @Test fun personalOrAnimatedBackgroundsStaySelectedAfterDelay() {
        assertFalse(allowsFullscreenGameplayVideo(BackgroundStyle.WALLPAPER))
        assertFalse(allowsFullscreenGameplayVideo(BackgroundStyle.ANIMATED))
        assertFalse(allowsFullscreenGameplayVideo(BackgroundStyle.BLACK))
    }

    @Test fun allBackgroundStylesHaveExpectedPolicy() {
        val permitted = BackgroundStyle.values().filter(::allowsFullscreenGameplayVideo)
        org.junit.Assert.assertEquals(setOf(BackgroundStyle.ART, BackgroundStyle.BLURRED), permitted.toSet())
    }
}
