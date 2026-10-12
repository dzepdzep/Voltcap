package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.ui.theme.ShelfTheme

/** Which fold groups are open, by key — kept while the app runs, so coming back to a page keeps them. */
private val foldOpen = mutableStateMapOf<String, Boolean>()

/** Opens the fold with [key] (e.g. when a link jumps straight to it). */
internal fun openFold(key: String) { foldOpen[key] = true }

/**
 * A collapsible group inside a settings page (or any panel): a header row with a chevron (A / tap
 * opens and closes it) and its controls under it. Use it to group related settings into logical
 * sections; [key] keeps it open or closed while the app runs (defaults to [title]).
 */
@Composable
internal fun FoldGroup(
    title: String,
    summary: String? = null,
    key: String = title,
    startOpen: Boolean = false,
    content: @Composable () -> Unit
) {
    val open = foldOpen[key] ?: startOpen
    val shape = uiRounded(14.dp)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .voltSurface(shape, alpha = if (open) 0.9f else 0.6f, edge = if (open && VoltUi.on) ShelfTheme.accent else null)
                .shelfClickable(shape) { foldOpen[key] = !open }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            groupIconFor(title)?.let { ic ->
                Icon(ic, null, tint = ShelfTheme.accent.copy(alpha = if (open) 1f else 0.8f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (summary != null && !open) {
                    Text(summary, color = ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.ExpandMore, contentDescription = if (open) "Collapse" else "Expand",
                tint = if (open) ShelfTheme.accent else ShelfTheme.textMuted,
                modifier = Modifier.rotate(if (open) 180f else 0f)
            )
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp, end = 2.dp)) {
                content()
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
