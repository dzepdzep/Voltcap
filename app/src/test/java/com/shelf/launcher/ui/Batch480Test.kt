package com.shelf.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 480: A5 category logos in one colour, D257 Tile home room. */
class Batch480Test {
    private fun src(name: String): String = listOf(
        File("../src_copy/ui/$name"), File("src/main/java/com/shelf/launcher/ui/$name"), File("app/src/main/java/com/shelf/launcher/ui/$name"),
    ).first { it.exists() }.readText().replace("\r\n", "\n")

    // A5: a colour logo tinted one colour became its silhouette (GBA a blob, Sega CD a bar) — the plain icon is used instead.
    @Test fun oneColourLogosUseThePlainIcon() {
        val p = src("PlatformLogos.kt")
        assertTrue(p.contains("val plainIcon = remember(platform, oneColour != null) { if (oneColour != null) PlatformLogos.plain(context, platform) else null }"))
        assertTrue(p.contains("fun oneColour(context: android.content.Context, platform: String)"))
        assertTrue(src("StyleCube.kt").contains("PlatformLogos.oneColour(context, p)"))
        assertTrue(src("CubeHub.kt").contains("PlatformLogos.oneColour(context, p)"))
        assertTrue(src("StyleCrossMenu.kt").contains("if (cut) stop.platform?.let { p -> PlatformLogos.plain(context, p) }"))
        assertTrue(src("StyleConsoleHomes.kt").contains("val oneColourIcon = if (symbols && symbol == null)"))
        // No caller tints the full-colour baked logo any more.
        assertFalse(Regex("PlatformLogos\\.baked\\(context, p\\)\\.getOrNull\\(\\)").containsMatchIn(src("CubeHub.kt")))
    }

    // A5: very wide wordmarks in squarish slots pick the stacked logo at draw time (no pop-in).
    @Test fun wideWordmarkPicksStackedLogo() {
        val p = src("PlatformLogos.kt")
        assertTrue(p.contains("private class FittedLogoPainter("))
        assertTrue(p.contains("return alt.takeIf { ra > 5f && rb < ra / 2f }"))
    }

    // D257: Tile home in the middle with no round buttons — bigger tiles + the facts strip, grouped and centred.
    @Test fun tileHomeUsesTheRoomUnderTheTiles() {
        val t = src("TileHome.kt")
        assertTrue(t.contains("val byHeight = screen.height * tileFraction(size) * scale * LocalTileRoom.current"))
        assertTrue(t.contains("internal fun TileInfoStrip("))
        val s = src("LauncherScreen.kt")
        assertTrue(s.contains("val buttonsUnder = buttonsUnder0 || tileInfoUnder"))
        assertTrue(s.contains("LocalTileRoom provides (if (tileInfoUnder) 1.14f else 1f)"))
    }

    private fun data(name: String): String = listOf(
        File("../src_copy/data/$name"), File("src/main/java/com/shelf/launcher/data/$name"), File("app/src/main/java/com/shelf/launcher/data/$name"),
    ).first { it.exists() }.readText().replace("\r\n", "\n")

    // B4: one motion level (Off / Subtle / Normal / Expressive) as the root recomposer's MotionDurationScale; Normal default.
    @Test fun motionLevelScalesEveryTransition() {
        assertTrue(data("Features.kt").contains("val motion = EnumPref(\"motion_level\", Motion.NORMAL, Motion.values())"))
        val m = src("MotionLevel.kt")
        assertTrue(m.contains("Features.Motion.OFF -> 0f"))
        assertTrue(m.contains("get() = factorFor(Features.motion.value) * system"))
        assertTrue(src("KeyInput.kt").contains("v.setParentCompositionContext(v.createLifecycleAwareWindowRecomposer(MotionLevel, lifecycle))"))
    }

    private fun theme(name: String): String = listOf(
        File("../src_copy/ui/theme/$name"), File("src/main/java/com/shelf/launcher/ui/theme/$name"), File("app/src/main/java/com/shelf/launcher/ui/theme/$name"),
    ).first { it.exists() }.readText().replace("\r\n", "\n")

    // C2: per-layout colours (palettes / yours) over the theme's, inherited when unset; readability guard on own backgrounds.
    @Test fun layoutColoursInheritAndStayReadable() {
        val f = data("Features.kt")
        assertTrue(f.contains("enum class Palette(val label: String, val bg: Long?, val accent: Long?)"))
        assertTrue(f.contains("if (c.palette == Palette.THEME) o.remove(layout)"))
        val t = theme("Theme.kt")
        assertTrue(t.contains("val customBackground = layoutOwn.bg ?: if (custom && !music) look.background else null"))
        assertTrue(t.contains("if (customBackground == null || contrastRatio(base.textPrimary, background) >= 4.5f) base.textPrimary"))
        assertTrue(src("SettingsDialog.kt").contains("LayoutColorsGroup(appearance.homeLayout)"))
    }

    // B4: QA probe measuring a tween's real length under the motion level.
    @Test fun motionProbeLogsRealDuration() {
        assertTrue(src("LauncherScreen.kt").contains("motionprobe tween=\$probeMs real="))
    }

    // A5 (481): a very long category name never runs past a chip row's edge (room beside both hints) nor off Live tiles' header.
    @Test fun longCategoryNamesStayInTheirRow() {
        val w = src("WholeChipRow.kt")
        assertTrue(w.contains("val roomMax = (avail - padS - padE - (if (count > 1) 2 * (hw + gap) else 0)).coerceAtLeast(0)"))
        // (529j: the word is its own LiveWord now, shared by the strip and the one-word window.)
        assertTrue(src("StyleLiveTiles.kt").contains("StableFullText(\n        // (Update 136: as written"))
        assertTrue(src("GameTitles.kt").contains("TestHooks.catName.value != null"))
    }
}
