package com.shelf.launcher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.Features
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Update 185: Undo for the R1 quick settings. A tile (or one of its hold choices, or a layout from the list) that changes
 * how the home screen looks — the look, the layout, the carousel, badges… — leaves an "Undo" at the top of the panel for
 * a few seconds; it puts back exactly what that press changed (the settings it touched and the appearance before it).
 * Presses that change nothing of the kind (Wi-Fi, brightness, opening a page) leave nothing to undo.
 */
internal object R1Undo {
    /** What was changed ("Look", "Carousel view"…), while it can be undone; null otherwise. */
    var label by mutableStateOf<String?>(null)
    /** When it was offered (uptime ms), so a newer change restarts the countdown. */
    var at by mutableLongStateOf(0L)
    private var features: Map<String, String> = emptyMap()
    private var appearance: AppearanceSettings? = null

    /** How long the Undo stays (ms). */
    const val SHOWN_MS = 8_000L

    fun offer(label: String, before: Map<String, String>, after: Map<String, String>, a0: AppearanceSettings?, a1: AppearanceSettings?) {
        val changed = before.filter { (k, v) -> after[k] != v }
        val appChanged = a0 != null && a1 != null && a0 != a1
        if (changed.isEmpty() && !appChanged) return
        features = changed
        appearance = if (appChanged) a0 else null
        this.label = label
        at = android.os.SystemClock.uptimeMillis()
    }

    /** Puts back what the last change changed. */
    fun undo(setAppearance: (AppearanceSettings) -> Unit) {
        Features.restore(features)
        appearance?.let(setAppearance)
        features = emptyMap()
        appearance = null
        label = null
    }

    fun clear() { label = null }
}

/**
 * Update 185: runs [run] (an R1 press) and, a moment later when its changes have landed, offers to undo them. [appearance]:
 * the home screen's appearance now (read again afterwards).
 */
internal fun r1Undoable(label: String, appearance: () -> AppearanceSettings?, scope: CoroutineScope, run: () -> Unit) {
    val f0 = Features.snapshot()
    val a0 = appearance()
    run()
    scope.launch {
        delay(350)
        R1Undo.offer(label.replace('\n', ' ').trim(), f0, Features.snapshot(), a0, appearance())
    }
}
