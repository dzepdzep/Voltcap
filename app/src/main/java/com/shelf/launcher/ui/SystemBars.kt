package com.shelf.launcher.ui

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.shelf.launcher.data.GestureHint

/*
 * Voltcap's system bars (Settings › Device › Gesture hint › Hide navigation bar).
 *
 * Android shows or hides the bars for whichever window has focus, so Voltcap's window *and each of its
 * popups* ask for the same thing: the status bar always hidden (Voltcap draws its own), the navigation bar /
 * gesture pill hidden when "Hide navigation bar" is on. A swipe from the edge shows the bars for a moment
 * (gestures keep working) and they hide again by themselves. After the on-screen keyboard closes they're hidden
 * again too (some devices leave the navigation bar up otherwise).
 */
internal object SystemBars {
    /** Applies the bars to [window]. Main thread. */
    fun apply(window: Window, immersive: Boolean = GestureHint.immersiveState.value) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())
        if (immersive) controller.hide(WindowInsetsCompat.Type.navigationBars())
        else controller.show(WindowInsetsCompat.Type.navigationBars())
    }

    /**
     * Hides the bars again once the keyboard has closed. Android 11+ only; it watches the window's own decor
     * view and lets every inset animation carry on to the views below (Compose's imePadding keeps working).
     */
    fun watchKeyboard(window: Window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val decor = window.decorView
        decor.setWindowInsetsAnimationCallback(object : WindowInsetsAnimation.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            override fun onProgress(insets: WindowInsets, running: MutableList<WindowInsetsAnimation>): WindowInsets = insets
            override fun onEnd(animation: WindowInsetsAnimation) {
                if (animation.typeMask and WindowInsets.Type.ime() == 0) return
                val imeShown = decor.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
                if (!imeShown) decor.post { apply(window) }
            }
        })
    }

    /**
     * Update 130: the navigation bar is really up (not a swipe's short peek) while "Hide navigation bar" is on, the
     * window has the focus and no keyboard is open — it should be hidden again.
     */
    fun navStuck(window: Window, focused: Boolean): Boolean {
        if (!focused || !GestureHint.immersiveState.value) return false
        val insets = window.decorView.rootWindowInsets ?: return false
        val c = WindowInsetsCompat.toWindowInsetsCompat(insets, window.decorView)
        return c.isVisible(WindowInsetsCompat.Type.navigationBars()) && !c.isVisible(WindowInsetsCompat.Type.ime())
    }

    fun stopWatching(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.decorView.setWindowInsetsAnimationCallback(null)
    }
}

/**
 * Call first thing inside a Dialog's content: the popup's window hides the bars like Voltcap's own
 * (otherwise opening a popup brings the gesture pill back while it's open). Does nothing outside a Dialog.
 */
@Composable
internal fun ImmersiveDialogWindow() {
    val view: View = LocalView.current
    val immersive = GestureHint.immersiveState.value
    DisposableEffect(view, immersive) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            runCatching { SystemBars.apply(window, immersive); SystemBars.watchKeyboard(window) }
        }
        onDispose { if (window != null) runCatching { SystemBars.stopWatching(window) } }
    }
    // Switching "Hide navigation bar" back on right after it was off: Android ignores a hide that comes while the bar is
    // still sliding in, so the bar stayed. Ask again once it has settled.
    androidx.compose.runtime.LaunchedEffect(view, immersive) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        for (wait in longArrayOf(120, 350, 800)) {
            kotlinx.coroutines.delay(wait)
            runCatching { SystemBars.apply(window, immersive) }
        }
    }
}
