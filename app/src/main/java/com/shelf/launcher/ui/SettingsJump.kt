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
import kotlinx.coroutines.launch
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Update 148: Settings search (and R1 › hold a tile) goes straight to the setting itself: its page and group open, then
 * the page scrolls to it and it lights up for a moment. It used to stop at the top of the page, a long scroll away.
 */
internal object SettingsJump {
    /** The setting's name to scroll to (a search result's label); cleared once it's in view. */
    var pending by mutableStateOf<String?>(null)

    /**
     * Update 226 (#109): the control picked for [pending] when no control on the page has exactly that name. Many search
     * entries name the layout or explain the setting around the control's own title ("Card binder fan angle" for "Fan
     * angle", "Start in (startup category)"), so the jump opened the page and stopped at its top. Now the longest title of
     * a control on the page that stands in the search label as whole words (at least 4 letters) is lit instead.
     */
    var fallback by mutableStateOf<String?>(null)

    /** The controls on screen now, by their title in lower case (how many of each) — read only to pick [fallback]. */
    private val shown = HashMap<String, Int>()

    fun seen(label: String) { val k = label.lowercase(); shown[k] = (shown[k] ?: 0) + 1 }
    fun gone(label: String) { val k = label.lowercase(); val n = (shown[k] ?: 1) - 1; if (n <= 0) shown.remove(k) else shown[k] = n }

    /** The [fallback] for [want], or null when a control has exactly that name (or none fits). */
    fun bestFor(want: String): String? {
        val w = want.lowercase()
        if (shown.containsKey(w)) return null
        return shown.keys.asSequence()
            .filter { it.length >= 4 && wordsIn(w, it) }
            .sortedWith(compareByDescending<String> { it.length }.thenBy { it })
            .firstOrNull()
    }

    private fun wordsIn(text: String, part: String): Boolean {
        var from = 0
        while (true) {
            val at = text.indexOf(part, from)
            if (at < 0) return false
            val before = at == 0 || !text[at - 1].isLetterOrDigit()
            val end = at + part.length
            val after = end >= text.length || !text[end].isLetterOrDigit()
            if (before && after) return true
            from = at + 1
        }
    }
}

/**
 * The place for setting [label] (its title as the search lists it): scrolled into view and lit when a search picks it.
 * Put on the control's outermost layout (SwitchRow, RowChoice, sliders, groups do this themselves).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun jumpAnchor(label: String): Modifier {
    val pending = SettingsJump.pending
    androidx.compose.runtime.DisposableEffect(label) {
        SettingsJump.seen(label)
        onDispose { SettingsJump.gone(label) }
    }
    // (Update 226, #109: no control named exactly as the search entry — once the page and its groups are open, the one
    // whose title the entry contains is lit instead; every anchor works out the same pick, the picked one claims it.)
    LaunchedEffect(pending) {
        val want = pending ?: run { if (SettingsJump.fallback != null) SettingsJump.fallback = null; return@LaunchedEffect }
        kotlinx.coroutines.delay(550)
        if (SettingsJump.pending == want && SettingsJump.fallback == null && SettingsJump.bestFor(want).equals(label, ignoreCase = true)) {
            SettingsJump.fallback = label
        }
    }
    val hit = pending != null && (pending.equals(label, ignoreCase = true) || SettingsJump.fallback.equals(label, ignoreCase = true))
    val req = remember { BringIntoViewRequester() }
    val flash = remember { Animatable(0f) }
    val accent = ShelfTheme.accent
    val folds = LocalFoldKeys.current
    val flashScope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(hit) {
        if (hit) {
            // Its groups stay open once the jump is over.
            folds.forEach { openFold(it) }
            // (Let the page and its group open first.)
            kotlinx.coroutines.delay(380)
            runCatching { req.bringIntoView() }
            // (Update 187 (fix): the light fades in its own coroutine — clearing the jump ends this effect, which used to
            // stop the fade at its start and leave the setting lit up for good.)
            flashScope.launch {
                flash.snapTo(1f)
                flash.animateTo(0f, tween(1800))
            }
            if (SettingsJump.pending.equals(label, ignoreCase = true) || SettingsJump.fallback.equals(label, ignoreCase = true)) {
                SettingsJump.pending = null
                SettingsJump.fallback = null
            }
        }
    }
    return Modifier
        .bringIntoViewRequester(req)
        .drawBehind {
            val a = flash.value
            if (a > 0f) drawRoundRect(accent.copy(alpha = 0.28f * a), cornerRadius = CornerRadius(12.dp.toPx()))
        }
}
