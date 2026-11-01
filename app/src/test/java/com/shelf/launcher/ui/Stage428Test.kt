package com.shelf.launcher.ui

import com.shelf.launcher.ui.theme.ThemeMotif
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 428: fixes from the Thor captures of the bright / light themes behind the real home screen, and D107. */
class Stage428Test {
    private fun src(name: String): String =
        File(listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name").first { File(it).exists() }).readText()

    @Test fun onlyTheMeasuredBrightScenesGetTheTextShade() {
        assertTrue(BRIGHT_SCENES.containsAll(listOf(ThemeMotif.MANGA_BEAT, ThemeMotif.COMIC_PANELS, ThemeMotif.MAGICAL, ThemeMotif.PX_FROST)))
        // Dark scenes stay as they are (no blanket dimming).
        assertTrue(listOf(ThemeMotif.MINIMAL, ThemeMotif.NEBULA, ThemeMotif.CHILL_MOON, ThemeMotif.COVER_FOCUS, ThemeMotif.PIXEL_WORLD).none { it in BRIGHT_SCENES })
        assertTrue(src("LauncherScreen.kt").contains("if (sceneMotif in BRIGHT_SCENES) SceneTextShade()"))
    }

    @Test fun lobbyWashFollowsTheWriting() {
        assertTrue(src("LobbyScene.kt").contains("val lightText = colors.textPrimary.luminance() > 0.5f"))
    }

    @Test fun cinemaStoryAndArcticPlotAreReachableWithTheDpad() {
        val s = src("WinScenesHomes.kt")
        assertTrue(s.contains("sceneReadable(s, ctx.accent)"))
        assertTrue(s.contains("sceneReadable(s2, accent)"))
    }
}
