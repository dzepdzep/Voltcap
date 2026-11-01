package com.shelf.launcher.data

import com.shelf.launcher.data.EsdeMedia.Kind as K
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Update 422: ES-DE media matching (exact console + ROM name), priority and content-address names. */
class EsdeMedia422Test {
    private val idx = EsdeMedia.Index(
        mapOf(
            "gba" to mapOf(K.COVER to mapOf("pokemon emerald (usa)" to "file:///es/gba/covers/Pokemon Emerald (USA).png"),
                K.MARQUEE to mapOf("pokemon emerald (usa)" to "file:///es/gba/marquees/m.png")),
            "gbc" to mapOf(K.SCREENSHOT to mapOf("pokemon silver" to "file:///es/gbc/screenshots/s.png")),
            "mygames" to mapOf(K.COVER to mapOf("zelda" to "file:///es/mygames/covers/zelda.png"))
        ), "/es", 0, 0L
    )
    private fun g(name: String, platform: String, path: String) = Game(name, platform, 0, SourceType.ROM, path)

    @Test fun exactConsoleAndName() {
        val f = EsdeMedia.find(g("Pokemon Emerald", "GBA", "/storage/roms/gba/Pokemon Emerald (USA).gba"), idx)!!
        assertEquals("file:///es/gba/covers/Pokemon Emerald (USA).png", f.files[K.COVER])
        // Another console's folder never lends its picture; a near name doesn't match.
        assertNull(EsdeMedia.find(g("Pokemon Emerald", "GB", "/storage/roms/gb/Pokemon Emerald (USA).gb"), idx))
        assertNull(EsdeMedia.find(g("Pokemon Emerald", "GBA", "/storage/roms/gba/Pokemon Emerald (Europe).gba"), idx))
    }

    @Test fun romFolderNameIsTriedToo() {
        assertEquals("file:///es/mygames/covers/zelda.png", EsdeMedia.find(g("Zelda", "SNES", "/roms/mygames/zelda.sfc"), idx)!!.files[K.COVER])
    }

    @Test fun contentAddressesAreDecoded() {
        val f = EsdeMedia.find(g("Pokemon Silver", "GBC", "content://com.android.externalstorage.documents/tree/primary%3ARoms/document/primary%3ARoms%2Fgbc%2FPokemon%20Silver.gbc"), idx)
        assertEquals("file:///es/gbc/screenshots/s.png", f!!.files[K.SCREENSHOT])
    }

    @Test fun priority() {
        val game = g("Pokemon Emerald", "GBA", "/r/gba/Pokemon Emerald (USA).gba")
        val mine = GameArt(gridUrl = "https://x/cover.png", heroUrl = null)
        assertEquals("https://x/cover.png", EsdeMedia.mergeArt(game, mine, preferEsde = false, idx = idx)!!.gridUrl)
        assertEquals("file:///es/gba/covers/Pokemon Emerald (USA).png", EsdeMedia.mergeArt(game, mine, preferEsde = true, idx = idx)!!.gridUrl)
        assertEquals("file:///es/gba/marquees/m.png", EsdeMedia.mergeArt(game, mine, preferEsde = false, idx = idx)!!.logoUrl)
        // Apps / Steam games are never matched.
        assertNull(EsdeMedia.find(Game("App", "Android", 0, SourceType.APP, null, "a.b"), idx))
    }
}
