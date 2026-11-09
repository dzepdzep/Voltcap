package com.shelf.launcher.ui

import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer

/**
 * Update 233 (#120): whether the last thing you did was a button press (not a touch). After a touch Android is in "touch
 * mode": a window opened with A then showed no highlight, the first ◀ / ▶ only lit Play (Android eats that press to
 * leave touch mode) — so A, ▶, A started the game instead of opening Performance. Windows opened by a button leave touch
 * mode as they open ([leaveTouchMode]) so the first press already moves.
 */
internal object KeyInput {
    @Volatile var lastWasKey: Boolean = false

    /** Opened by a button press while Android is in touch mode: leave it, so the window's buttons can take the focus now. */
    fun leaveTouchMode(view: android.view.View) {
        if (lastWasKey && view.isInTouchMode) runCatching { view.requestFocusFromTouch() }
    }
}

/**
 * Update 477 (D252): after a touch, the first D-pad press on the home screen did nothing. Android eats a navigation key that
 * takes it out of touch mode (ViewRootImpl's early "leave touch mode and consume" step) before any app code sees it — the QA
 * trace showed one key of two reaching the activity on the KONKR (Android 12). This root leaves touch mode in the pre-IME
 * step, which comes first, so that press is delivered and moves as usual. Gamepad buttons were already handled
 * (MainActivity, update 467); touch behaviour is unchanged (touch mode starts again with the next touch).
 */
internal class KeyRootComposeView(context: android.content.Context) : androidx.compose.ui.platform.AbstractComposeView(context) {
    private val content = androidx.compose.runtime.mutableStateOf<(@androidx.compose.runtime.Composable () -> Unit)?>(null)
    override var shouldCreateCompositionOnAttachedToWindow: Boolean = false
        private set

    fun setContent(c: @androidx.compose.runtime.Composable () -> Unit) {
        shouldCreateCompositionOnAttachedToWindow = true
        content.value = c
        if (isAttachedToWindow) createComposition()
    }

    @androidx.compose.runtime.Composable
    override fun Content() { content.value?.invoke() }

    override fun getAccessibilityClassName(): CharSequence = KeyRootComposeView::class.java.name

    override fun dispatchKeyEventPreIme(event: android.view.KeyEvent): Boolean {
        if (leavesTouchModeFirst(event.action, event.keyCode, isInTouchMode)) {
            runCatching { (findFocus() ?: this).requestFocusFromTouch() }
            if (QaTrace.on) QaTrace.event("leftouch", android.view.KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_"))
        }
        return super.dispatchKeyEventPreIme(event)
    }
}

/** Update 477 (D252): a D-pad press while Android is in touch mode leaves it first (pre-IME), so the press is not eaten. */
internal fun leavesTouchModeFirst(action: Int, keyCode: Int, inTouchMode: Boolean): Boolean =
    inTouchMode && action == android.view.KeyEvent.ACTION_DOWN && keyCode in NAV_KEYS

internal val NAV_KEYS = setOf(
    android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT, android.view.KeyEvent.KEYCODE_DPAD_UP,
    android.view.KeyEvent.KEYCODE_DPAD_DOWN, android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_TAB,
    android.view.KeyEvent.KEYCODE_PAGE_UP, android.view.KeyEvent.KEYCODE_PAGE_DOWN, android.view.KeyEvent.KEYCODE_MOVE_HOME,
    android.view.KeyEvent.KEYCODE_MOVE_END
)

/** Update 477 (D252): the activity's Compose content in [KeyRootComposeView] (ComponentActivity sets the view-tree owners). */
@OptIn(androidx.compose.ui.InternalComposeUiApi::class)
internal fun androidx.activity.ComponentActivity.setKeyRootContent(c: @androidx.compose.runtime.Composable () -> Unit) {
    val v = KeyRootComposeView(this)
    // Update 480 (B4): the root recomposer runs with Voltcap's own motion scale (MotionLevel) — every window and dialog
    // composed under it shares it.
    MotionLevel.refresh(this)
    v.setParentCompositionContext(v.createLifecycleAwareWindowRecomposer(MotionLevel, lifecycle))
    v.setContent(c)
    setContentView(v, android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT))
}
