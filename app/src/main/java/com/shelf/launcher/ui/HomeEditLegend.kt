package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Update 151: Edit Home's button legend along the bottom (HomeEdit.kt › HomeEditBar), now fitted to the screen. On a
 * small screen (a 620 × 540 dp handheld) the one long line didn't fit: the hint shrank to nothing and the last buttons
 * (Style, Done and the fold arrow) were cut off past the right edge. Below [LEGEND_ONE_LINE] it is two lines: a short
 * hint with the fold arrows, then smaller buttons that wrap onto a further line if they still don't fit.
 */

/** Narrower than this, the legend takes two lines. */
private val LEGEND_ONE_LINE = 900.dp

/** The open Edit Home legend (the folded tab stays in HomeEditBar). */
@Composable
internal fun BoxScope.HomeEditBarOpen() {
    // (zIndex here: it sits over the home screen's other parts, as the bar did.)
    BoxWithConstraints(Modifier.matchParentSize().zIndex(9f)) {
        val narrow = maxWidth < LEGEND_ONE_LINE
        if (!narrow) {
            Row(
                Modifier.align(Alignment.BottomCenter).safeZonePadding().clip(RoundedCornerShape(50))
                    .background(ShelfTheme.colors.surfaceRaised.copy(alpha = 0.95f)).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendFold(-1)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Edit Home · ${HomeWidgets.label(HomeEditState.selected)} · drag or D-pad to move, corner or L2 / R2 to resize (tap it: Style), side and bottom handles to stretch",
                    color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = fitMin(12.sp), maxFontSize = 12.sp), fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(10.dp))
                // Same as the X / Y buttons: position and size (update 91).
                Chip("X Reset position", accent = false) { HomeEditState.resetOne(HomeEditState.selected) }
                Spacer(Modifier.width(6.dp))
                Chip("Y Reset layout", accent = false) { HomeEditState.resetEvery() }
                Spacer(Modifier.width(6.dp))
                Chip("Select ✕", accent = false) { HomeWidgets.remove(HomeEditState.selected) }
                Spacer(Modifier.width(6.dp))
                Chip("Start + Add", accent = false) { HomeEditState.adding = true }
                Spacer(Modifier.width(6.dp))
                Chip("L3 ✎ Style", accent = false) { HomeEditState.styling = HomeEditState.selected }
                Spacer(Modifier.width(6.dp))
                Chip("A Done") { HomeEditState.done() }
                Spacer(Modifier.width(8.dp))
                LegendFold(1)
            }
        } else {
            Column(
                Modifier.align(Alignment.BottomCenter).safeZonePadding().widthIn(max = maxWidth - 8.dp)
                    .clip(RoundedCornerShape(16.dp)).background(ShelfTheme.colors.surfaceRaised.copy(alpha = 0.95f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendFold(-1)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Edit Home · ${HomeWidgets.label(HomeEditState.selected)} · D-pad moves, L2 / R2 resize, tap the corner for Style",
                        color = ShelfTheme.colors.textPrimary, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = fitMin(11.sp), maxFontSize = 11.sp),
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    LegendFold(1)
                }
                Spacer(Modifier.height(5.dp))
                LegendKeys()
            }
        }
    }
}

/** The buttons in small chips, wrapping onto another line when they don't fit. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LegendKeys() {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        LegendKey("X Reset part") { HomeEditState.resetOne(HomeEditState.selected) }
        LegendKey("Y Reset all") { HomeEditState.resetEvery() }
        LegendKey("Select ✕ Remove") { HomeWidgets.remove(HomeEditState.selected) }
        LegendKey("Start + Add") { HomeEditState.adding = true }
        LegendKey("L3 Style") { HomeEditState.styling = HomeEditState.selected }
        LegendKey("A Done", accent = true) { HomeEditState.done() }
    }
}

/** One small button of the legend. */
@Composable
private fun LegendKey(label: String, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (accent) ShelfTheme.accent else Color.White.copy(alpha = 0.10f))
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            label, color = if (accent) onAccentColor() else ShelfTheme.colors.textPrimary,
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false
        )
    }
}

/** The arrow that folds the legend away to that side ([side] -1 left, 1 right). */
@Composable
private fun LegendFold(side: Int) {
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(ShelfTheme.colors.surface)
            .pointerInput(side) { detectTapGestures { HomeEditState.barFolded = side } },
        contentAlignment = Alignment.Center
    ) { Text(if (side < 0) "◀" else "▶", color = ShelfTheme.colors.textPrimary, fontSize = 13.sp) }
}
