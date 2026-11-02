package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 447: the way back from the second screen's dashboard to a two-screen layout is a compact button in the dashboard's
 * header (its end), not the wide pill in the bottom corner that covered the dock.
 */
class TwinBack447Test {
    private fun src(name: String): String {
        val roots = listOf("src_copy/", "../src_copy/", "app/src/main/java/com/shelf/launcher/", "src/main/java/com/shelf/launcher/")
        return roots.map { File(it + name) }.first { it.exists() }.readText().replace("\r\n", "\n")
    }

    @Test fun compactButtonComfortableTarget() {
        assertEquals(44f, TWIN_BACK_TARGET.value, 0.01f)
        assertTrue(TWIN_BACK_VISIBLE.value <= 34f && TWIN_BACK_VISIBLE.value < TWIN_BACK_TARGET.value)
        val t = src("ui/TwinScreens.kt")
        assertTrue("old bottom-corner pill is gone", !t.contains("fun androidx.compose.foundation.layout.BoxScope.TwinBackPill("))
        val b = t.substring(t.indexOf("internal fun TwinBackButton("))
        assertTrue(!b.substring(0, 3000).contains("Alignment.BottomStart"))
        // Same controller id and the same action as before: the layout comes back, settings untouched.
        assertTrue(b.contains("padTargetPlain(\"twin-back\")") && b.contains("TwinScreens.dashboard = false"))
        assertTrue(b.contains("layout.isPocket") && b.contains("pocketBrackets"))
    }

    @Test fun headerKeepsRoomForIt() {
        val h = src("ui/SecondScreenHome.kt")
        assertTrue(h.contains("headerEnd: (@Composable () -> Unit)? = null"))
        assertTrue(h.contains("endInset = headerEndInset + (if (headerEnd != null) TWIN_BACK_TARGET + 4.dp else 0.dp)"))
        assertTrue(h.contains("if (headerEnd != null) { Spacer(Modifier.width(6.dp)); headerEnd() }"))
        val l = src("ui/LauncherScreen.kt")
        assertTrue(l.contains("headerEnd = if (state.appearance.homeLayout.isTwin && TwinScreens.dashboard)"))
        assertTrue("no overlay over the dock any more", !l.contains("TwinBackPill("))
    }
}
