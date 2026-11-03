package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 462: the controller contract (tools/qa/INPUT_CONTRACT.md). Select = Settings, Start = game options, R1 = quick
 * menu, L1 = shortcuts on every layout; a layout keeps one only in a context of its own.
 */
class InputContract462Test {
    private val none = InputContract.Ctx()

    @Test fun everyLayoutLeavesTheGlobalButtons() {
        for (l in HomeLayout.values()) for (code in InputContract.RESERVED)
            assertEquals("${l.name} key $code", InputContract.Route.HOME, InputContract.route(code, l, none))
    }

    @Test fun pocketQuestSelectIsSettings() {
        // The reported bug: Select on Pocket Quest opened Select a System.
        assertTrue(InputContract.leaveToHome(InputContract.SELECT, HomeLayout.POCKET, none))
        assertTrue(InputContract.leaveToHome(InputContract.SELECT, HomeLayout.POCKET, InputContract.Ctx(pqBumpers = true)))
        assertTrue(InputContract.leaveToHome(InputContract.SELECT, HomeLayout.POCKET_CLASSIC, InputContract.Ctx(pocketPage = true)))
    }

    @Test fun ownedContextsKeepTheirKeys() {
        // Pocket classic's pages jump 15 with L1 / R1; Pocket Quest's L1 / R1 console switch is the user's own option.
        assertEquals(InputContract.Route.LAYOUT, InputContract.route(InputContract.R1, HomeLayout.POCKET_CLASSIC, InputContract.Ctx(pocketPage = true)))
        assertEquals(InputContract.Route.LAYOUT, InputContract.route(InputContract.L1, HomeLayout.POCKET, InputContract.Ctx(pqBumpers = true)))
        // ...but not elsewhere
        assertEquals(InputContract.Route.HOME, InputContract.route(InputContract.R1, HomeLayout.POCKET, InputContract.Ctx(pocketPage = true)))
        assertEquals(InputContract.Route.HOME, InputContract.route(InputContract.R1, HomeLayout.BLADES, InputContract.Ctx(pqBumpers = true)))
        // A launch animation holds every global button (rapid presses open nothing over the starting game).
        for (l in listOf(HomeLayout.POCKET, HomeLayout.POCKET_CLASSIC, HomeLayout.POCKET_RIBBON))
            assertEquals(InputContract.Route.HOLD, InputContract.route(InputContract.SELECT, l, InputContract.Ctx(launching = true)))
        assertEquals(InputContract.Route.HOME, InputContract.route(InputContract.SELECT, HomeLayout.GREENCORE, InputContract.Ctx(launching = true)))
    }

    @Test fun otherKeysStayTheLayouts() {
        for (code in listOf(96, 97, 99, 100, 104, 105, 19, 20, 21, 22)) // A B X Y L2 R2 D-pad
            assertEquals(InputContract.Route.LAYOUT, InputContract.route(code, HomeLayout.POCKET, none))
    }

    private fun src(n: String) = java.io.File(
        listOf("../src_copy/ui", "src/main/java/com/shelf/launcher/ui", "app/src/main/java/com/shelf/launcher/ui").first { java.io.File(it, n).exists() }, n
    ).readText()

    @Test fun theGateComesBeforeEveryLayoutHandler() {
        val s = src("StyleHomes.kt")
        val gate = s.indexOf("InputContract.route(event.nativeKeyEvent.keyCode, layout, InputContract.live())")
        assertTrue("gate missing", gate > 0)
        for (h in listOf("pocketClassicKey(", "pocketRibbonKey(", "greenCoreKey(", "pocketQuestKey(", "cubeKey(", "bladesKey(", "bubblesKey(",
            "pocketCameraKey(", "panelsKey(", "magazineKey(", "rentalShopKey(", "terminalKey(", "cinemaKey(", "arcticKey(", "artLibKey(", "twinCatsKey("))
            assertTrue("$h before the gate", s.indexOf("return $h") > gate)
    }

    @Test fun noLayoutRebindsSelectOrStart() {
        // Select used to sort (8 layouts) and open Select a System (Pocket Quest); Start opened the quick menu.
        for (f in listOf("StyleBlades.kt", "StyleMagazine.kt", "StylePanels.kt", "StylePocketCamera.kt", "StyleTerminal.kt", "StyleRentalShop.kt",
            "StyleGreenCore.kt", "StyleBubbles.kt")) {
            val s = src(f)
            assertTrue("$f Select", !s.contains("Key.ButtonSelect) return up"))
            assertTrue("$f Start", !s.contains("k == Key.ButtonStart || k == Key.Menu"))
        }
        assertTrue(!src("StylePocketQuest.kt").contains("pqOpenSystems(c)\n            }\n        }\n        return true"))
    }

    @Test fun themedPickerRoutesFromTheGlobalCategoryScreen() {
        assertTrue(ThemedPicker.has(HomeLayout.POCKET) && ThemedPicker.has(HomeLayout.GREENCORE) && ThemedPicker.has(HomeLayout.POCKET_CLASSIC))
        assertTrue(!ThemedPicker.has(HomeLayout.CLASSIC))
        assertTrue(src("LauncherScreen.kt").contains("ThemedPicker.open(state.appearance.homeLayout)) return"))
    }
}
