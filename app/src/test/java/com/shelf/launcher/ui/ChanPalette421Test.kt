package com.shelf.launcher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Features.ChanPalette
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Update 421: Channel grid palettes (Original Pale, OLED Black, Light Blue, Light Yellow, Custom). */
class ChanPalette421Test {
    @Before fun reset() = clean()
    @After fun after() = clean()
    private fun clean() {
        Features.chanPalette.state.value = ""
        Features.chanDark.state.value = false
        Features.chanLight.state.value = true
        Features.chanCustom.state.value = 0xFFDCE8F4.toInt()
    }

    @Test fun oldSavesKeepTheirLook() {
        assertEquals(ChanPalette.PALE, chanPaletteNow())
        Features.chanDark.state.value = true
        assertEquals(ChanPalette.OLED, chanPaletteNow())
        assertTrue(chanOled())
    }

    @Test fun pickingKeepsTheDarkSwitchInStep() {
        setChanPalette(ChanPalette.OLED); assertTrue(Features.chanDark.state.value)
        setChanPalette(ChanPalette.BLUE); assertFalse(Features.chanDark.state.value)
        assertEquals(ChanPalette.BLUE, chanPaletteNow())
        assertTrue(chanLightLook())
    }

    @Test fun originalPaleIsUnchanged() {
        val p = chanColorsFor(ChanPalette.PALE, 0)
        assertEquals(Color(0xFFF6F8FB), p.bgTop); assertEquals(Color(0xFFDDE2E9), p.bgBottom)
        assertEquals(Color.White, p.surface); assertEquals(Color(0xFF4A5461), p.ink)
    }

    @Test fun customStaysReadable() {
        for (argb in listOf(0xFFFFE0F0.toInt(), 0xFFB0D0FF.toInt(), 0xFF808080.toInt(), 0xFF203040.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt())) {
            val p = chanColorsFor(ChanPalette.CUSTOM, argb)
            val la = p.ink.luminance() + 0.05f; val lb = p.surface.luminance() + 0.05f
            val ratio = if (la > lb) la / lb else lb / la
            assertTrue("custom ${Integer.toHexString(argb)}: contrast $ratio", ratio >= 4.5f)
        }
    }

    @Test fun everyPaletteHasALabel() {
        for (p in ChanPalette.values()) assertTrue(p.label.isNotBlank())
    }
}
