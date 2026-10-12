package com.shelf.launcher.ui

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Scrolls this item into view whenever it becomes the selected one. Voltcap's D-pad selection is its
 * own state (not Android focus), so on small screens an item selected inside a scrolling row (tabs,
 * numbers, chips) used to stay off screen — you could only see up to "Manual" while the selection
 * was further along. Works inside any horizontal / vertical scroll and lazy list.
 */
internal fun Modifier.revealWhen(selected: Boolean): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) {
        if (selected) runCatching { requester.bringIntoView() }
    }
    this.bringIntoViewRequester(requester)
}

/**
 * One line of text whose box is exactly its line height with the glyphs centred in it — so a Text next
 * to an Icon of the same height lines up with it (no more labels sitting a pixel or two above or below
 * the icons in rows: status bar, tabs, chips).
 */
internal val CenteredLine = androidx.compose.ui.text.TextStyle(
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
    )
)
