package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.dp
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Update 148: Settings search (and R1 › hold a tile) goes straight to the setting itself: its page and group open, then
 * the page scrolls to it and it lights up for a moment. It used to stop at the top of the page, a long scroll away.
 */
internal object SettingsJump {
    /** The setting's name to scroll to (a search result's label); cleared once it's in view. */
    var pending by mutableStateOf<String?>(null)
}

/**
 * The place for setting [label] (its title as the search lists it): scrolled into view and lit when a search picks it.
 * Put on the control's outermost layout (SwitchRow, RowChoice, sliders, groups do this themselves).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun jumpAnchor(label: String): Modifier {
    val pending = SettingsJump.pending
    val hit = pending != null && pending.equals(label, ignoreCase = true)
    val req = remember { BringIntoViewRequester() }
    val flash = remember { Animatable(0f) }
    val accent = ShelfTheme.accent
    val folds = LocalFoldKeys.current
    LaunchedEffect(hit) {
        if (hit) {
            // Its groups stay open once the jump is over.
            folds.forEach { openFold(it) }
            // (Let the page and its group open first.)
            kotlinx.coroutines.delay(380)
            runCatching { req.bringIntoView() }
            if (SettingsJump.pending.equals(label, ignoreCase = true)) SettingsJump.pending = null
            flash.snapTo(1f)
            flash.animateTo(0f, tween(1800))
        }
    }
    return Modifier
        .bringIntoViewRequester(req)
        .drawBehind {
            val a = flash.value
            if (a > 0f) drawRoundRect(accent.copy(alpha = 0.28f * a), cornerRadius = CornerRadius(12.dp.toPx()))
        }
}
