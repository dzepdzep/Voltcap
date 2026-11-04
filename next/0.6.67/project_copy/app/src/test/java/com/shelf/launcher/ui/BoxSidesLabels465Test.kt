package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 465: (1) the plain-colour box sides (the default since update 466; a style picked in Settings stays), and a real scan
 * or your picture of a side still wins; (2) no doubled print on a cartridge label: a real label scan gets no generated
 * header, footer or code, and a photo-made DS / 3DS card covers the photo's whole sticker (its own code strip included).
 */
class BoxSidesLabels465Test {
    private fun src(n: String) = File("src/main/java/com/shelf/launcher/$n").readText()

    @Test fun plainColourIsTheDefaultAndAPickedStyleStays() {
        // Update 466 (owner): Plain colour became the default; a style picked in Settings is marked and kept.
        val f = src("data/Features.kt")
        assertTrue(f.contains("""COLOUR("Plain colour from the box art")"""))
        assertTrue(f.contains("""val boxSides = EnumPref("box_sides", BoxSides.COLOUR, BoxSides.values())"""))
        assertTrue(f.contains("""val boxSidesChosen = BoolPref("box_sides_chosen", false)"""))
        assertTrue(src("ui/SettingsDialog.kt").contains("Features.boxSides.value = it; com.shelf.launcher.data.Features.boxSidesChosen.value = true"))
        // FILL, AUTHENTIC, PLAIN keep their names (saved values stay readable).
        assertEquals(listOf("FILL", "AUTHENTIC", "PLAIN", "COLOUR"), com.shelf.launcher.data.Features.BoxSides.values().map { it.name })
    }

    /** Update 467: no saved value ever changes; an existing install without one keeps Fill; only a new install gets Plain colour. */
    @Test fun migrationNeverOverwritesAStoredValue() {
        val F = com.shelf.launcher.data.Features
        fun run(saved: String?, existing: Boolean): String? {
            val p = FakePrefs(saved?.let { mutableMapOf<String, Any?>("box_sides" to it) } ?: mutableMapOf())
            F.boxSides.state.value = saved?.let { n -> com.shelf.launcher.data.Features.BoxSides.values().first { it.name == n } } ?: F.boxSides.default
            F.migrate467(p, existing)
            return p.m["box_sides"] as String?
        }
        for (v in listOf("FILL", "AUTHENTIC", "PLAIN", "COLOUR")) {
            assertEquals(v, run(v, existing = true)); assertEquals(v, run(v, existing = false))
        }
        assertEquals("FILL", run(null, existing = true))          // upgraded install: keeps what it showed
        assertEquals(com.shelf.launcher.data.Features.BoxSides.FILL, F.boxSides.state.value)
        assertEquals(null, run(null, existing = false))           // new install: Plain colour default, nothing written
        assertEquals(com.shelf.launcher.data.Features.BoxSides.COLOUR, F.boxSides.state.value)
        val once = FakePrefs(mutableMapOf("m467_box_sides_done" to true))
        F.migrate467(once, true); assertEquals(null, once.m["box_sides"]) // runs once only
        F.boxSides.state.value = F.boxSides.default
    }

    /** Update 467: Stadium 2's plain sides are its own dark indigo background, not its red logo; other covers unchanged. */
    @Test fun stadium2SidesAreItsIndigoBackground() {
        assertEquals(0xFF120123.toInt(), BoxSides.measuredColour("Pokemon Stadium 2", "n64"))
        assertEquals(0xFF120123.toInt(), BoxSides.measuredColour("Pokémon Stadium 2", "N64"))
        assertEquals(null, BoxSides.measuredColour("Pokemon Stadium", "n64"))
        assertEquals(null, BoxSides.measuredColour("Pokemon Stadium 2", "gbc"))
    }

    private class FakePrefs(val m: MutableMap<String, Any?>) : android.content.SharedPreferences {
        override fun getAll(): MutableMap<String, *> = m
        override fun getString(k: String?, d: String?) = (m[k] as String?) ?: d
        override fun getStringSet(k: String?, d: MutableSet<String>?) = d
        override fun getInt(k: String?, d: Int) = d
        override fun getLong(k: String?, d: Long) = d
        override fun getFloat(k: String?, d: Float) = d
        override fun getBoolean(k: String?, d: Boolean) = (m[k] as Boolean?) ?: d
        override fun contains(k: String?) = m.containsKey(k)
        override fun registerOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun edit(): android.content.SharedPreferences.Editor = object : android.content.SharedPreferences.Editor {
            override fun putString(k: String?, v: String?): android.content.SharedPreferences.Editor { m[k!!] = v; return this }
            override fun putStringSet(k: String?, v: MutableSet<String>?): android.content.SharedPreferences.Editor = this
            override fun putInt(k: String?, v: Int): android.content.SharedPreferences.Editor = this
            override fun putLong(k: String?, v: Long): android.content.SharedPreferences.Editor = this
            override fun putFloat(k: String?, v: Float): android.content.SharedPreferences.Editor = this
            override fun putBoolean(k: String?, v: Boolean): android.content.SharedPreferences.Editor { m[k!!] = v; return this }
            override fun remove(k: String?): android.content.SharedPreferences.Editor { m.remove(k); return this }
            override fun clear(): android.content.SharedPreferences.Editor { m.clear(); return this }
            override fun commit() = true
            override fun apply() {}
        }
    }

    @Test fun realSideScansAndYourPicturesWinOverTheColour() {
        val s = src("ui/BoxSides.kt")
        // A side's own picture is taken first in the colour mode too (pick), the colour only where there's none.
        assertTrue(s.contains("mode == com.shelf.launcher.data.Features.BoxSides.FILL || mode == com.shelf.launcher.data.Features.BoxSides.COLOUR || com.shelf.launcher.data.ArtProvenance.isScan(it)"))
        val i = s.indexOf("if (b != null) DerivedArt.of(b, \"side|")
        assertTrue(i > 0 && i < s.indexOf("} else if (oneColour && front != null) {"))
        // The colour comes from the cover's artwork (its middle / art window), not a printed band at its edge.
        assertTrue(s.contains("fun boxColour(front: Bitmap, platform: String? = null): Int?"))
        // The spine is never a generated side: it's the case's own scan / cut (case3dModels), not one of these sides.
        assertEquals(listOf("RIGHT", "TOP", "BOTTOM"), BoxSide.values().map { it.name })
    }

    @Test fun noGeneratedPrintOverARealLabelScan() {
        val cs = src("ui/CartShells.kt")
        assertTrue(cs.contains("val plaque = header?.takeIf { !scan }"))
        assertTrue(cs.contains("""val switchHeader = if (plaque == null && s.id == "switch" && !scan)"""))
        assertTrue(cs.contains("""val paperOn = plaque == null && switchHeader == null && !scan && art != null && (s.id == "ds" || s.id == "3ds")"""))
        assertTrue(cs.contains("val ids = if (!scan) com.shelf.launcher.data.LabelIds.of(title) else null"))
        // The product code is printed only from the ROM header (no invented codes) and only with a cover.
        assertTrue(cs.contains("val idText = ids?.code"))
    }

    @Test fun photoCardsCoverTheirWholeSticker() {
        val ps = src("ui/PhotoShells.kt")
        assertTrue(ps.contains("""private val WHOLE = setOf("ds", "3ds")"""))
        assertTrue(ps.contains("""val label = if (clear || shell.id in WHOLE) est else findSticker(out, est, plastic)"""))
        assertTrue(ps.contains("""val sl = if (shell.id in WHOLE) STICKER[shell.id]!! else shell.label"""))
        // The DS / 3DS sticker runs to the code strip (31 mm from 1.6 mm of a 35 mm card).
        assertTrue(ps.contains(""""ds" to floatArrayOf(2.9f, 1.6f, 27f, 31f), "3ds" to floatArrayOf(2.9f, 1.6f, 27f, 31f)"""))
    }
}
