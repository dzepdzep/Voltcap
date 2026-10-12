package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.CardCorner
import com.shelf.launcher.data.Features

/**
 * Update 151: every place the achievement count can sit on the game you're on, as one list — the four corners each
 * their own choice next to Above the name / Next to the name / Under the cover / Off. The R1 Achievements tile steps
 * through it (its old "In the corner" only ever meant the corner picked in Settings, so the top right and the bottom
 * right could not be reached from it). A corner sets the "Where on the game" pref to In the corner plus the corner.
 */
internal enum class AchSpot(val label: String, val short: String, val place: Features.AchPlace, val corner: CardCorner?) {
    TOP_LEFT("Top left corner", "Top left", Features.AchPlace.CORNER, CardCorner.TOP_LEFT),
    TOP_RIGHT("Top right corner", "Top right", Features.AchPlace.CORNER, CardCorner.TOP_RIGHT),
    BOTTOM_LEFT("Bottom left corner", "Bottom left", Features.AchPlace.CORNER, CardCorner.BOTTOM_LEFT),
    BOTTOM_RIGHT("Bottom right corner", "Bottom right", Features.AchPlace.CORNER, CardCorner.BOTTOM_RIGHT),
    ABOVE("Above the name", "Above", Features.AchPlace.ABOVE, null),
    NAME("Next to the name", "By name", Features.AchPlace.NAME, null),
    UNDER("Under the cover", "Under", Features.AchPlace.UNDER, null),
    OFF("Off", "Off", Features.AchPlace.OFF, null);

    companion object {
        /** The spot for a saved place and corner. */
        fun of(place: Features.AchPlace, corner: CardCorner): AchSpot =
            if (place == Features.AchPlace.CORNER) values().first { it.corner == corner }
            else values().first { it.place == place }
    }
}

/**
 * Update 151: the small upright tick between a progress bar (or the trophy) and its "0/69" count, so the two read as
 * two parts. [height] follows the text it sits next to; [gap] is the room on each side.
 */
@Composable
internal fun ProgressTick(height: Dp = 9.dp, gap: Dp = 4.dp, color: Color = Color.White.copy(alpha = 0.45f)) {
    Box(
        Modifier.padding(horizontal = gap).width(1.dp).height(height)
            .background(color)
    )
}
