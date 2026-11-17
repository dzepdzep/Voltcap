package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import androidx.compose.ui.unit.dp

/**
 * 672b (owner design decisions, 14 Nov): Case flow - the case lifts out of the flow, turns into the window and goes back to
 * exactly its place; Rental shop - the shelf's own case comes off the board, is held beside the tag with its slot left empty and
 * goes back into the same slot. Both on the pickup trip the other physical layouts use (PickupOverlay), never a second copy.
 */
class Pickups672Test {
    private fun src(name: String) = listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")
        .map(::File).first { it.exists() }.readText()

    @Test fun bothHomesKeepTheirPictureOfTheGameForTheTrip() {
        val s = src("StyleHomes.kt")
        assertTrue(s.contains("HomeLayout.CASEFLOW -> androidx.compose.runtime.CompositionLocalProvider(LocalPickupSnapshot provides true) { StyleCaseFlow(c, modifier) }"))
        assertTrue(s.contains("HomeLayout.SHOP -> androidx.compose.runtime.CompositionLocalProvider(LocalPickupSnapshot provides true) { StyleRentalShop(c, modifier) }"))
    }

    @Test fun theWindowFliesThemWithTheHomesOwnPicture() {
        val s = src("GameWindow.kt")
        // Case flow turns over like the Record crate's sleeve; Rental shop is a sprite trip to the case's place by the tag.
        assertTrue(s.contains("(scene == WinScene.CASE_MEDIA && layout == HomeLayout.CASEFLOW)) && piece == null && originLayer != null"))
        assertTrue(s.contains("val shopFlight = scene == WinScene.SHOP_TAG && layout == HomeLayout.SHOP && piece == null && originLayer != null"))
        assertTrue(s.contains("|| sleeveFlip || consoleFlight || shopFlight"))
    }

    @Test fun caseFlowWindowTurnsInOnceTheCaseIsEdgeOn() {
        val s = src("WinScenes.kt")
        val body = s.substring(s.indexOf("private fun CaseFlowMedia("), s.indexOf("private fun CaseFlowMedia(") + 2500)
        assertTrue(body.contains("val flip = LocalSceneOrigin.current != null"))
        assertTrue(body.contains(".pickupFlipTarget(flip)"))
        assertTrue(body.contains("rotationY = -90f * t"))
        // the case at home doesn't also come forward while it is away
        val home = src("StyleCaseFlow.kt")
        assertTrue(home.contains("val expandOn = if (pickupOwn) 0f else 1f"))
        assertTrue(home.contains("(1f + 0.14f * expand * expandOn * (1f - near))"))
    }

    @Test fun rentalTagHoldsTheCaseOnlyWhenItWasTakenOff() {
        val s = src("WinScenesConsole.kt")
        val body = s.substring(s.indexOf("internal fun RentalTag("), s.indexOf("private fun RentalTagCard("))
        // no trip (motion off): the tag alone - never a copy of a case still on the shelf
        assertTrue(body.contains("if (origin == null) { RentalTagCard(ctx, minOf(ctx.maxW, 460.dp)); return }"))
        assertTrue(body.contains(".storageFlightSlot(true)"))
        assertTrue(body.contains("if (storageSlotHidden(true, ctx.reveal())) alpha = 0f"))
        assertTrue(body.contains("PickupTarget(flight, rootAt.value, uniform = false) { SceneCase(ctx, Modifier.fillMaxSize()) }"))
    }

    @Test fun theShelfStaysInViewBehindTheTag() {
        val s = src("SceneOwnColors.kt")
        assertTrue(s.contains("Brush.verticalGradient(listOf(s.wallBottom.copy(alpha = 0.42f), Color.Black.copy(alpha = 0.66f)))"))
    }

    // 672a follow-up (KONKR x1.3 k673a): Play as wide as Sleeve notes only while both still share their row.
    @Test fun crateKeyMinKeepsTheRowTogether() {
        assertEquals(150f, crateKeyMin(150.dp, 450.dp, 6.dp).value, 0.01f)
        assertEquals(224f, crateKeyMin(250.dp, 480.dp, 6.dp).value, 0.01f)
        assertEquals(94f, crateKeyMin(300.dp, 400.dp, 6.dp).value, 0.01f)
        assertEquals(0f, crateKeyMin(500.dp, 400.dp, 6.dp).value, 0.01f)
    }

    // 672a follow-up (KONKR x1.3 k673a): Cardboard box's sheet gives its margins to the words when it is short at large text.
    @Test fun boxSheetTightensOnlyShortSheetsWithLargeText() {
        val s = src("WinScenes.kt")
        val body = s.substring(s.indexOf("private fun BoxInsertSheet("), s.indexOf("private fun BoxInsertSheet(") + 9000)
        assertTrue(body.contains("val tight = bigPageText() && ctx.maxH < 420.dp"))
        assertTrue(body.contains("top = if (tight) 10.dp else 20.dp, bottom = if (tight) 8.dp else 14.dp"))
        assertTrue(body.contains("Spacer(Modifier.height(if (tight) 6.dp else 12.dp))"))
    }
}
