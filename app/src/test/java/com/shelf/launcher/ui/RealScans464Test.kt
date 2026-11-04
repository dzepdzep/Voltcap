package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 464 (owner: "do not change the appearance of my existing real cartridge scans"): PhotoShells only ever replaces the
 * DRAWN (procedural) shell's front; the real-photo path — which picture is chosen, how it's drawn, turned and inspected —
 * never touches it. Guarded at the source, as the device comparison (qa464, RC vs staging pixel diffs) can't run in CI.
 */
class RealScans464Test {
    private val root = File("src/main/java/com/shelf/launcher")
    private fun src(n: String) = File(root, n).readText()

    /** The body of the function named [name] in [text]: its block `{ … }`, or for `= …` up to the next top-level declaration. */
    private fun body(text: String, name: String): String {
        val m = Regex("""fun (<[^>]*> )?$name\(""").find(text) ?: error("no fun $name")
        var k = m.range.last; var d = 0
        while (true) { val c = text[k]; if (c == '(') d++ else if (c == ')') { d--; if (d == 0) break }; k++ }
        var j = k + 1
        while (text[j] != '{' && text[j] != '=') j++
        if (text[j] == '=') {
            val n = Regex("""\n(internal |private |public |@Composable|fun |/\*\*|object |class )""").find(text, j)
            return text.substring(m.range.first, n?.range?.first ?: text.length)
        }
        d = 0; k = j
        while (true) { if (text[k] == '{') d++ else if (text[k] == '}') { d--; if (d == 0) break }; k++ }
        return text.substring(m.range.first, k + 1)
    }

    @Test fun realPhotoChoiceNeverConsultsPhotoShells() {
        // Which picture a cartridge shows: the game's own photo (or your pick) is chosen here, before any drawn shell exists.
        val card = src("ui/GameCard.kt")
        for (f in listOf("typedArtFor", "drawnLabelScan")) assertFalse(f, body(card, f).contains("PhotoShells"))
        assertFalse(body(src("ui/PhysicalMedia.kt"), "realScanOf").contains("PhotoShells"))
        assertFalse(src("data/PhysicalOverrides.kt").contains("PhotoShells"))
        // The own photo is still the last word for CARTRIDGE_DISC when nothing was picked.
        assertTrue(body(card, "typedArtFor").contains("""physical?.firstOrNull { it.first == "Disc / cartridge" }?.second"""))
    }

    @Test fun realPhotoDrawingNeverConsultsPhotoShells() {
        assertFalse(body(src("ui/PhysicalMedia.kt"), "RealMediaPicture").contains("PhotoShells"))
        assertFalse(body(src("ui/SolidPiece.kt"), "rememberPhotoSolid").contains("PhotoShells"))
        assertFalse(src("ui/RealMedia.kt").contains("PhotoShells"))
        assertFalse(src("ui/LaunchThrow.kt").contains("PhotoShells"))
    }

    @Test fun photoShellsOnlyInTheDrawnShellPath() {
        val users = root.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "PhotoShells.kt" && it.readText().contains("PhotoShells.") }
            .map { it.name }.toSortedSet()
        assertEquals(sortedSetOf("CartShells.kt", "Features.kt", "LauncherViewModel.kt", "MediaInspectView.kt", "SettingsDialog.kt", "SolidPiece.kt", "TestHooks.kt"), users)
        // SolidPiece: only the drawn shell's solid (rememberShellSolid), never the photo's.
        val solid = src("ui/SolidPiece.kt")
        val inShell = body(solid, "rememberShellSolid")
        assertEquals(solid.split("PhotoShells.").size - 1, inShell.split("PhotoShells.").size - 1)
        // Inspect: only for a drawn shell pick ("~sh:"), never a real scan.
        val insp = src("ui/MediaInspectView.kt")
        assertTrue(insp.contains("p.pick?.platform?.takeIf { it.startsWith(SHELL_PREFIX) }?.let { pp ->"))
        assertTrue(insp.contains("pp.startsWith(SHELL_PREFIX) && photoTone != null -> Color(photoTone)"))
        // CartShells: only from the drawn front (frontFor / cachedFrontFor / photoFront) and the art's cache key.
        val cs = src("ui/CartShells.kt")
        val inFront = listOf("frontFor", "cachedFrontFor", "photoFront").sumOf { body(cs, it).split("PhotoShells.").size - 1 }
        val inKey = cs.lines().count { it.contains("PhotoShells.version.intValue") && it.contains("labelsV") }
        assertEquals(cs.split("PhotoShells.").size - 1, inFront + inKey)
    }

    @Test fun aMissingOrFailedTemplateFallsBackToTheDrawnShell() {
        val cs = src("ui/CartShells.kt")
        assertTrue(cs.contains("return photo ?: drawn"))
        assertTrue(body(cs, "photoFront").contains("if (PhotoShells.template(s, colour) == null) return null"))
        val ps = src("ui/PhotoShells.kt")
        // No template → no photo front, no photo sides; a failed photo is remembered as failed, never retried in a loop.
        assertTrue(body(ps, "front").contains("val t = template(shell, colour) ?: return null"))
        assertTrue(body(ps, "sideTone").contains("val t = template(shell, colour) ?: return null"))
        assertTrue(ps.contains("if (t != null) { templates[key] = t;") && ps.contains("} else failed += key"))
        // Loading never throws into drawing: it's caught (runCatching) and off the screen's thread.
        assertTrue(body(ps, "load").contains("runCatching"))
        // The setting off: nothing photo-made at all.
        assertTrue(body(ps, "template").contains("if (!com.shelf.launcher.data.Features.photoShells.value || !supports(shell.id)) return null"))
    }

    @Test fun customModelsAndPicksComeFirst() {
        // Your pack / custom model picks are drawn before the drawn shell (and so before any photo-made front).
        val front = body(src("ui/CartShells.kt"), "frontFor")
        assertTrue(front.indexOf("packColourParts(key)") < front.indexOf("photoFront("))
        // Only official games' photos make templates (a hack's photo is its base game's).
        val p = PhotoShells.photosOf(mapOf("x (Hack).gbc" to listOf("Disc / cartridge" to "https://x/m?systemeid=10&media=support-2D"))) { true }
        assertTrue(p.isEmpty())
    }
}
