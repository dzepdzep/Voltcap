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
        // (Update 155: not on a Retroid, where the device's own setting hides it — asking too brought the pill back.)
        if (GestureHint.windowHidesNav(immersive)) controller.hide(WindowInsetsCompat.Type.navigationBars())
        else controller.show(WindowInsetsCompat.Type.navigationBars())
    }

    /**
     * Hides the bars again once the keyboard has closed, and (676b) once a navigation bar that came back has settled.
     * Android 11+ only; it watches the window's own decor view and lets every inset animation carry on to the views below
     * (Compose's imePadding keeps working). Event-driven: it acts only when Android reports a bar animation ending, never on
     * a timer - so a device that can't hide the bar is asked once per time the bar comes in, not every few seconds.
     */
    fun watchKeyboard(window: Window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val decor = window.decorView
        decor.setWindowInsetsAnimationCallback(object : WindowInsetsAnimation.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            override fun onProgress(insets: WindowInsets, running: MutableList<WindowInsetsAnimation>): WindowInsets = insets
            override fun onEnd(animation: WindowInsetsAnimation) {
                val ime = animation.typeMask and WindowInsets.Type.ime() != 0
                val nav = animation.typeMask and WindowInsets.Type.navigationBars() != 0
                if (!ime && !nav) return
                val now = decor.rootWindowInsets ?: return
                val imeShown = now.isVisible(WindowInsets.Type.ime())
                val navShown = now.isVisible(WindowInsets.Type.navigationBars())
                if (reapplyAfter(ime, nav, imeShown, navShown, GestureHint.windowHidesNav(), decor.hasWindowFocus())) decor.post { apply(window) }
            }
        })
    }

    /**
     * 676b: whether a bar animation that just ended should be answered by asking for the bars again: the keyboard closed, or
     * the navigation bar came in for real (not a swipe's transient peek, which Android doesn't report to the window) while
     * Hide navigation bar wants it hidden, the window has the focus and no keyboard is up.
     */
    internal fun reapplyAfter(imeAnim: Boolean, navAnim: Boolean, imeShown: Boolean, navShown: Boolean, wantHidden: Boolean, focused: Boolean): Boolean =
        (imeAnim && !imeShown) || (navAnim && navShown && wantHidden && focused && !imeShown)

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
    val deviceHides = GestureHint.deviceNavHidden.value // (Update 449)
    DisposableEffect(view, immersive, deviceHides) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            runCatching { SystemBars.apply(window, immersive); SystemBars.watchKeyboard(window) }
        }
        onDispose { if (window != null) runCatching { SystemBars.stopWatching(window) } }
    }
    // Switching "Hide navigation bar" back on right after it was off: Android ignores a hide that comes while the bar is
    // still sliding in, so the bar stayed. Ask again once it has settled.
    androidx.compose.runtime.LaunchedEffect(view, immersive, deviceHides) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        for (wait in longArrayOf(120, 350, 800)) {
            kotlinx.coroutines.delay(wait)
            runCatching { SystemBars.apply(window, immersive) }
        }
    }
}
