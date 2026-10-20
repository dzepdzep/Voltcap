package com.shelf.launcher.ui

import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Update 285 (Toni: "an icon that's half case, half cartridge"): the game window's Inspect button. The left half is a
 * game case (its spine down the left edge, the cover's window), the right half a cartridge (the cut top corner, the
 * label window, the grip ridges at the foot), side by side in one 24 × 24 icon, drawn in the button's ink.
 */
internal object InspectIcon {
    val vector: ImageVector by lazy {
        ImageVector.Builder("VoltcapInspect", 24.dp, 24.dp, 24f, 24f).apply {
            // The case half: a tall plate, a slot for its spine and the cover's window cut out.
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(3f, 3f); lineTo(11.4f, 3f); lineTo(11.4f, 21f); lineTo(3f, 21f)
                quadTo(2f, 21f, 2f, 20f); lineTo(2f, 4f); quadTo(2f, 3f, 3f, 3f); close()
                // spine slot
                moveTo(3.6f, 4.6f); lineTo(4.6f, 4.6f); lineTo(4.6f, 19.4f); lineTo(3.6f, 19.4f); close()
                // cover window
                moveTo(6f, 5f); lineTo(10f, 5f); lineTo(10f, 19f); lineTo(6f, 19f); close()
            }
            // The cartridge half: its top corner cut, its label window, three ridges at the foot.
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(12.6f, 5f); lineTo(19.5f, 5f); lineTo(22f, 7.5f); lineTo(22f, 20f)
                quadTo(22f, 21f, 21f, 21f); lineTo(12.6f, 21f); close()
                // label window
                moveTo(14.2f, 8.2f); lineTo(20.3f, 8.2f); lineTo(20.3f, 15.6f); lineTo(14.2f, 15.6f); close()
                // ridges
                moveTo(14.2f, 17.2f); lineTo(20.3f, 17.2f); lineTo(20.3f, 17.9f); lineTo(14.2f, 17.9f); close()
                moveTo(14.2f, 18.9f); lineTo(20.3f, 18.9f); lineTo(20.3f, 19.6f); lineTo(14.2f, 19.6f); close()
            }
        }.build()
    }
}
