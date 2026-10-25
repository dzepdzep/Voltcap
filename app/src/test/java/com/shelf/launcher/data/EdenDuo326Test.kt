package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 326: Eden Duo (github.com/igawa6/eden-duo) as a Switch emulator. */
class EdenDuo326Test {
    private val switchGame = Game("Metroid Dread", "Switch", 0, SourceType.ROM, romPath = "/storage/emulated/0/ROMs/switch/Metroid Dread.nsp")

    @Test fun packageIsTheOneInEdenDuosBuild() {
        // duo.gradle: applicationId = 'dev.igawa6.edenduo' (dev builds add '.dev').
        assertEquals("dev.igawa6.edenduo", EmulatorCatalog.EDEN_DUO)
    }

    @Test fun launchProfileStartsTheGameDirectly() {
        val p = LaunchProfiles.find(EmulatorCatalog.EDEN_DUO)
        assertNotNull(p)
        p!!
        // Eden Duo keeps upstream's EmulationActivity (namespace org.yuzu.yuzu_emu) and reads the game from the
        // intent's data (EmulationFragment: a content URI whose name ends in a Switch extension).
        assertEquals(listOf("org.yuzu.yuzu_emu.activities.EmulationActivity"), p.activities)
        assertEquals("android.intent.action.VIEW", p.action)
        assertNull(p.romExtra)
        assertEquals(RomFormat.SHARED_URI, p.format)
        assertNotNull(LaunchProfiles.find(EmulatorCatalog.EDEN_DUO + ".dev"))
    }

    @Test fun suggestedForSwitchAndCountedAsAnEmulator() {
        assertTrue(EmulatorRegistry.suggestionsFor("Switch").any { it.packageName == EmulatorCatalog.EDEN_DUO })
        assertEquals("Eden Duo", EmulatorRegistry.displayNameFor(EmulatorCatalog.EDEN_DUO))
        // The other Switch emulators are still there.
        for (pkg in listOf("dev.eden.eden_emulator", "org.citron.citron_emu")) {
            assertTrue(pkg, EmulatorRegistry.suggestionsFor("Switch").any { it.packageName == pkg })
        }
    }

    @Test fun voltcapLeavesTheSecondScreenToItsCompanion() {
        // On the built-in second screen (AYN Thor) a Switch game in Eden Duo gets "Emulator uses both screens" unless a
        // rule says otherwise; with plain Eden it keeps "Game + info".
        assertEquals(ScreenLayouts.NATIVE, ScreenLayouts.fallback(ScreenSetup.DUAL, switchGame, EmulatorCatalog.EDEN_DUO))
        assertEquals(ScreenLayouts.INFO, ScreenLayouts.fallback(ScreenSetup.DUAL, switchGame, "dev.eden.eden_emulator"))
        assertEquals(ScreenLayouts.INFO, ScreenLayouts.fallback(ScreenSetup.DUAL, switchGame, null))
        // A TV keeps the TV layout.
        assertEquals(ScreenLayouts.TV, ScreenLayouts.fallback(ScreenSetup.TV, switchGame, EmulatorCatalog.EDEN_DUO))
    }

    @Test fun gameProfileCarriesItsOwnEmulator() {
        assertTrue(GameProfile().isEmpty())
        val p = GameProfile(emulatorPackage = EmulatorCatalog.EDEN_DUO)
        assertTrue(!p.isEmpty())
        assertEquals(EmulatorCatalog.EDEN_DUO, p.emulatorPackage)
    }
}
