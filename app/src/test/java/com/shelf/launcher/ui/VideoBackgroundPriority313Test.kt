package com.shelf.launcher.ui

import com.shelf.launcher.data.BackgroundStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 313: video previews must never erase an explicitly selected background. */
class VideoBackgroundPriority313Test {
    @Test fun automaticFullScreenPreviewWorksOnlyWithGameArt() {
        assertTrue(videoMayReplaceSelectedBackground(BackgroundStyle.ART))
        assertTrue(videoMayReplaceSelectedBackground(BackgroundStyle.BLURRED))
    }

    @Test fun wallpaperAndAnimatedThemeAlwaysKeepPriority() {
        assertFalse(videoMayReplaceSelectedBackground(BackgroundStyle.WALLPAPER))
        assertFalse(videoMayReplaceSelectedBackground(BackgroundStyle.ANIMATED))
        assertFalse(videoMayReplaceSelectedBackground(BackgroundStyle.BLACK))
    }

    @Test fun allBackgroundChoicesHaveExplicitExpectedBehavior() {
        val expected = mapOf(
            BackgroundStyle.ART to true,
            BackgroundStyle.BLURRED to true,
            BackgroundStyle.BLACK to false,
            BackgroundStyle.WALLPAPER to false,
            BackgroundStyle.ANIMATED to false
        )
        assertTrue("New background styles need an explicit video-precedence decision",
            expected.keys.containsAll(BackgroundStyle.values().toSet()))
        BackgroundStyle.values().forEach { style ->
            org.junit.Assert.assertEquals(style.label, expected[style], videoMayReplaceSelectedBackground(style))
        }
    }
}
