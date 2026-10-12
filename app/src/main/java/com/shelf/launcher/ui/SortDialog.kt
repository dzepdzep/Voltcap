package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.SortMode

/**
 * How the games in the category you're in are ordered. Opened from the sort
 * chip on the category bar, Start › Sort…, or the Sort hotkey. D-pad: up/down
 * through the list, A picks, B closes.
 *
 * @param override this category's own pick, or null when it follows the default.
 * @param globalMode the default (Settings › Library › Sort games by).
 * @param keepsOwnOrder the category (Recent, Most played…) has a built-in order it keeps until you pick one.
 */
@Composable
fun SortDialog(
    categoryName: String,
    override: SortMode?,
    globalMode: SortMode,
    keepsOwnOrder: Boolean,
    onPick: (SortMode) -> Unit,
    onUseDefault: () -> Unit,
    onApplyToAll: (SortMode) -> Unit,
    onDismiss: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("SortDialog")
    // What the list is sorted by right now (null: the category's built-in order).
    val active: SortMode? = override ?: if (keepsOwnOrder) null else globalMode
    ShelfMenuDialog(
        title = "Sort games",
        subtitle = "This category: $categoryName",
        onDismiss = onDismiss,
        icon = Icons.Filled.Sort
    ) {
        SectionLabel("This category: $categoryName")
        RadioLine(
            label = "Use default",
            selected = override == null,
            blurb = if (keepsOwnOrder) "Keeps its own order" else globalMode.label
        ) { onUseDefault(); onDismiss() }
        SortMode.values().forEach { mode ->
            RadioLine(label = mode.label, selected = override == mode) { onPick(mode); onDismiss() }
        }
        // Direction only where two modes are each other's reverse.
        if (active == SortMode.NAME || active == SortMode.NAME_DESC) {
            Spacer(Modifier.height(4.dp))
            MenuRow(
                Icons.Filled.SwapVert,
                if (active == SortMode.NAME) "Switch to Z–A" else "Switch to A–Z"
            ) {
                onPick(if (active == SortMode.NAME) SortMode.NAME_DESC else SortMode.NAME)
                onDismiss()
            }
        }
        if (active != null) {
            Spacer(Modifier.height(4.dp))
            MenuRow(
                Icons.Filled.DoneAll,
                "Apply to all categories",
                "Everything sorted by ${active.label}"
            ) { onApplyToAll(active); onDismiss() }
        }
        Spacer(Modifier.height(4.dp))
        MutedNote("The default is set under Settings › Library › Sort games by.")
    }
}
