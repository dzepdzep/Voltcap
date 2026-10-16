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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Update 212: Edit Home's toolbar (it replaces the update 151 legend): the selected part and what it reaches, the
 * actions — Elements, + Add, Undo, Preview, Discard, Done — and the button hints with this controller's own glyphs.
 * At the bottom, or at the top while the selected part is low on the screen (so it never sits over what you're
 * placing); it folds away to either side with its arrows. Touch and controller do the same things (HomeEdit.kt › onKey).
 */

/** Narrower than this, the toolbar takes two lines and the hints are short. */
private val TOOLBAR_ONE_LINE = 900.dp

@Composable
internal fun BoxScope.HomeEditToolbar() {
    BoxWithConstraints(Modifier.matchParentSize().zIndex(9f)) {
        val narrow = maxWidth < TOOLBAR_ONE_LINE
        val dens = LocalDensity.current.density
        val sel = HomeEditState.selected
        val b = HomeEditState.bounds[sel]
        // Low on the screen (its middle below 62 %): the bar goes to the top.
        val atTop = b != null && b.center.y / dens > maxHeight.value * 0.62f
        Column(
            Modifier.align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter).safeZonePadding().widthIn(max = maxWidth - 8.dp)
                .onGloballyPositioned { HomeEditState.chrome["bar"] = it.boundsInRoot() }
                .clip(RoundedCornerShape(16.dp)).background(ShelfTheme.colors.surfaceRaised.copy(alpha = 0.95f))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendFold(-1)
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Text(
                        "Edit Home · " + HomeWidgets.label(sel) + if (!HomeEditSession.shown(sel)) " (hidden)" else "",
                        color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(HomeEditSession.scopeOf(sel), color = ShelfTheme.textMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                if (!narrow) ToolbarActions()
                Spacer(Modifier.width(6.dp))
                LegendFold(1)
            }
            if (narrow) { Spacer(Modifier.size(4.dp)); ToolbarActions() }
            Spacer(Modifier.size(4.dp))
            Hints(narrow)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ToolbarActions() {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        ToolKey("☰ Elements") { HomeEditState.elements = true }
        ToolKey("+ Add") { HomeEditState.adding = true }
        ToolKey("↶ Undo", enabled = HomeEditSession.undoCount > 0) { HomeEditSession.undo() }
        ToolKey("◉ Preview") { HomeEditState.preview = true }
        ToolKey("✎ Adjust") { HomeEditState.inspectorTab = 0; HomeEditState.styling = HomeEditState.selected }
        ToolKey("Discard") { if (HomeEditSession.dirty()) HomeEditState.leaving = true else HomeEditSession.save() }
        ToolKey("✓ Done", accent = true) { HomeEditSession.save() }
    }
}

/** The buttons' hints, with the glyphs printed on this controller (GlyphPill). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Hints(narrow: Boolean) {
    val list = buildList {
        add("A" to "Adjust"); add("B" to "Back")
        add("✚" to "Move"); add("L1" to if (narrow) "R1 Pick" else "R1 Pick part")
        add("L2" to "R2 Size"); add("X" to "Reset part"); add("Y" to "Undo")
        add("SELECT" to "Hide"); add("START" to "Done")
    }
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        list.forEach { (k, l) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (k == "✚") Text("✚", color = ShelfTheme.colors.textPrimary, fontSize = 12.sp) else GlyphPill(k)
                Spacer(Modifier.width(4.dp))
                Text(l, color = ShelfTheme.textMuted, fontSize = 10.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** One button of the toolbar. */
@Composable
private fun ToolKey(label: String, accent: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (accent) ShelfTheme.accent else Color.White.copy(alpha = if (enabled) 0.10f else 0.04f))
            .pointerInput(label, enabled) { detectTapGestures { if (enabled) onClick() } }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            label, color = if (accent) onAccentColor() else ShelfTheme.colors.textPrimary.copy(alpha = if (enabled) 1f else 0.4f),
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false
        )
    }
}

/** The arrow that folds the toolbar away to that side ([side] -1 left, 1 right). */
@Composable
private fun LegendFold(side: Int) {
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(ShelfTheme.colors.surface)
            .pointerInput(side) { detectTapGestures { HomeEditState.barFolded = side } },
        contentAlignment = Alignment.Center
    ) { Text(if (side < 0) "◀" else "▶", color = ShelfTheme.colors.textPrimary, fontSize = 13.sp) }
}
