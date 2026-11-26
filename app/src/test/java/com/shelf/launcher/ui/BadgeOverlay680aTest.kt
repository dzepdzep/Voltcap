package com.shelf.launcher.ui

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 680a (D356, KONKR x1.3 Channel): a band an overlay covers at the tile's bottom (Channel's name plate) - badges keep clear. */
class BadgeOverlay680aTest {
    private val gap = 9
    private val tile = IntSize(246, 136) // KONKR x1.3 Channel tile (k679p13 numbers, px)

    @Test fun progressAtTheBottomRisesAboveThePlate() {
        val band = 41
        val progress = BadgeBox(BadgeKind.PROGRESS, Alignment.BottomEnd, 6, 6, 105, 36)
        val overlay = BadgeBox(BadgeKind.OVERLAY, Alignment.BottomStart, 0, 0, tile.width, band)
        val plan = planBadges(tile, listOf(progress, overlay), gap)
        val s = plan.getValue(BadgeKind.PROGRESS)
        assertTrue(s.shown)
        val r = badgeRect(progress, tile, androidx.compose.ui.unit.LayoutDirection.Ltr).translate(s.dx, s.dy)
        assertTrue("progress $r over the plate band", r.bottom + gap <= tile.height - band)
        assertEquals(BadgeSpot(0, 0), plan.getValue(BadgeKind.OVERLAY)) // the overlay itself never moves
    }

    @Test fun noOverlayNothingMoves() {
        val progress = BadgeBox(BadgeKind.PROGRESS, Alignment.BottomEnd, 6, 6, 105, 36)
        assertEquals(BadgeSpot(0, 0), planBadges(tile, listOf(progress), gap).getValue(BadgeKind.PROGRESS))
    }

    @Test fun channelPassesItsPlateBand() {
        val s = listOf("src/main/java/com/shelf/launcher/ui/StyleConsoleHomes.kt", "app/src/main/java/com/shelf/launcher/ui/StyleConsoleHomes.kt")
            .map { File(it) }.first { it.exists() }.readText()
        assertTrue(s.contains("CompositionLocalProvider(LocalBadgeOverlayBottom provides plateBand)"))
    }
}
