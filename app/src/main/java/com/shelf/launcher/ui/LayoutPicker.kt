package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Update 375: the one layout picker with a confirmation step (R1 hold on the Layout tile, Settings › Categories › Home layout
 * per category). Moving the D-pad over a layout only shows its picture and what it is; pressing it makes it the candidate;
 * nothing changes on the home screen until Apply. Cancel / Back leave everything as it was. The list stays open while you
 * browse and keeps its scroll position.
 *
 * [active] is the layout in force (marked "✓ In use"); [followLabel] adds a "Follow …" button for pickers that can remove an
 * override ([onFollow]); [followsDefault] says nothing is overridden now.
 */
@Composable
internal fun LayoutPickerDialog(
    title: String,
    note: String,
    active: HomeLayout,
    onApply: (HomeLayout) -> Unit,
    onDismiss: () -> Unit,
    followLabel: String? = null,
    followsDefault: Boolean = false,
    onFollow: () -> Unit = {}
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ImmersiveDialogWindow()
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Box(Modifier.fillMaxWidth(0.9f).widthIn(max = 900.dp)) { ShelfCard {
            SectionLabel(title)
            MutedNote(note)
            Spacer(Modifier.height(10.dp))
            // The layout whose picture is shown (the one the D-pad is on) and the one chosen to apply.
            var shown by remember { mutableStateOf(active) }
            var candidate by remember { mutableStateOf<HomeLayout?>(null) }
            val current = remember { FocusRequester() }
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { current.requestFocus() } }
            val listH = (LocalConfiguration.current.screenHeightDp - 250).coerceIn(120, 330).dp
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    LayoutPreview(shown)
                    Spacer(Modifier.height(8.dp))
                    Text(shown.label, color = ShelfTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            shown == candidate && shown != active -> "Chosen: Apply switches to it"
                            shown == active -> "In use now"
                            else -> "Press to choose it, then Apply"
                        },
                        color = ShelfTheme.textMuted, fontSize = 12.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    MutedNote(layoutBlurb(shown))
                }
                Spacer(Modifier.width(12.dp))
                val scroll = rememberScrollState()
                Box(Modifier.weight(1f).heightIn(max = listH).verticalScroll(scroll)) {
                    ChipFlow {
                        HomeLayout.pickable.forEach { l ->
                            Box(if (l == active) Modifier.focusRequester(current) else Modifier) {
                                Chip(
                                    (if (l == active) "✓ " else "") + l.label, accent = l == candidate || (candidate == null && l == active),
                                    modifier = Modifier.onFocusChanged { if (it.isFocused && shown != l) shown = l }
                                ) { candidate = l; shown = l }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val pick = candidate
            ChipFlow {
                Chip(
                    if (pick != null && pick != active) "Apply ${pick.label}" else "Apply",
                    accent = pick != null && pick != active, enabled = pick != null && pick != active
                ) { onApply(pick!!); onDismiss() }
                if (followLabel != null) {
                    Chip(followLabel, accent = false, enabled = !followsDefault) { onFollow(); onDismiss() }
                }
                Chip("Cancel", accent = false, onClick = onDismiss)
            }
        } } }
    }
}
