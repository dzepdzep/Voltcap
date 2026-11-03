package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 462: the photo-made shells take only official games' own cartridge photos, by console. */
class PhotoShells462Test {
    @Test fun officialPhotosBySystem() {
        val raw = mapOf(
            "silver.gbc" to listOf("Disc / cartridge" to "https://x/mediaJeu.php?systemeid=10&jeuid=1&media=support-2D", "Box" to "https://x/b?systemeid=10"),
            "brown.gb" to listOf("Disc / cartridge" to "https://x/mediaJeu.php?systemeid=9&jeuid=2&media=support-2D"),
            "tetris.gb" to listOf("Disc / cartridge" to "https://x/mediaJeu.php?systemeid=9&jeuid=3&media=support-2D"),
            "nocart.gba" to listOf("Box" to "https://x/b?systemeid=12"),
            "mario.n64" to listOf("Disc / cartridge" to "https://x/m?systemeid=14&media=support-2D")
        )
        val p = PhotoShells.photosOf(raw) { it.startsWith("brown") }
        assertEquals(setOf("9", "10"), p.keys)
        assertEquals(listOf("tetris.gb" to "https://x/mediaJeu.php?systemeid=9&jeuid=3&media=support-2D"), p["9"])
        assertEquals(1, p["10"]!!.size)
    }

    @Test fun shellsCovered() {
        for (id in listOf("gb", "gbc_dual", "gbc", "gba")) assertTrue(id, PhotoShells.supports(id))
        assertTrue(!PhotoShells.supports("n64"))
        assertEquals("gba", PhotoShells.shellOfGame("x.gba", "12"))
        assertEquals("gb", PhotoShells.shellOfGame("x.gb", "9"))
        assertEquals(0f, PhotoShells.colourDistance(0xFFA3A2A8.toInt(), 0xFFA3A2A8.toInt()), 0.01f)
        assertTrue(PhotoShells.colourDistance(0xFFA3A2A8.toInt(), 0xFFD4AF37.toInt()) > 80f) // grey vs a gold pak
    }

    @Test fun wiredIntoTheDrawnShells() {
        val s = java.io.File(listOf("../src_copy/ui", "src/main/java/com/shelf/launcher/ui", "app/src/main/java/com/shelf/launcher/ui").first { java.io.File(it, "CartShells.kt").exists() }, "CartShells.kt").readText()
        assertTrue(s.contains("return photoFront(s, colour, drawn) ?: drawn"))
    }
}
