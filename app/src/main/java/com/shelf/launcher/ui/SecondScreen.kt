package com.shelf.launcher.ui

import android.app.Presentation
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.NowPlaying
import com.shelf.launcher.data.PlaytimeStore
import com.shelf.launcher.data.RaAward
import com.shelf.launcher.data.RaGameProgress
import com.shelf.launcher.data.SourceType
import com.shelf.launcher.ui.theme.ShelfTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import android.os.Handler
import android.os.Looper
import android.view.ViewTreeObserver
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Finds a second display (e.g. the AYN Thor's bottom screen), if there is one.
 * Presentation-capable displays are preferred; otherwise any non-main display.
 */
fun findSecondDisplay(activity: ComponentActivity): Display? = runCatching {
    val manager = activity.getSystemService(DisplayManager::class.java) ?: return@runCatching null
    fun usable(d: Display) = d.displayId != Display.DEFAULT_DISPLAY && d.isValid &&
        (d.flags and Display.FLAG_PRIVATE) == 0
    manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull { usable(it) }
        ?: manager.displays.firstOrNull { usable(it) }
}.getOrNull()

/**
 * Lets an app launched onto the second display be seen: a Presentation is a
 * window above every activity on its display, so while an app is opened there
 * the Presentation steps aside. It comes back when Voltcap is used again
 * (resumed, or its main window regains focus).
 */
object SecondScreenGate {
    private val _suppressed = kotlinx.coroutines.flow.MutableStateFlow(false)
    val suppressed: kotlinx.coroutines.flow.StateFlow<Boolean> = _suppressed
    @Volatile private var since = 0L

    fun suppress() {
        since = System.currentTimeMillis()
        _suppressed.value = true
    }

    fun release() {
        _suppressed.value = false
    }

    /** Focus events right after a launch are the screens settling, not the user coming back. */
    fun releaseIfSettled(minMs: Long = 1_500L) {
        if (System.currentTimeMillis() - since > minMs) release()
    }
}

/**
 * Shows [content] on the second display while [enabled] and a second display
 * exists. It shares this screen's composition — same theme, colours, sounds
 * and live state — so whatever it shows updates as you use the main screen.
 * The controller keeps driving everything from the main screen; the second
 * screen also takes touch.
 *
 * Follows displays coming and going (a TV plugged in, a cover screen), and
 * steps aside while an app is opened on the second display ([SecondScreenGate]).
 *
 * Returns whether a second screen is actually being used, so the main screen
 * can hand things over to it.
 */
@Composable
fun SecondScreenHost(
    enabled: Boolean,
    /** Called on every touch or key on the second screen (resets the idle timer). */
    onUserActivity: () -> Unit = {},
    content: @Composable () -> Unit
): Boolean {
    com.shelf.launcher.Breadcrumbs.mark("SecondScreenHost")
    // Runs the same steps on every redraw — no early exits — whether or not
    // there is a second screen. Drawing code that leaves early can corrupt
    // Compose's bookkeeping on this compiler version.
    val activity = LocalContext.current as? ComponentActivity
    val suppressed by SecondScreenGate.suppressed.collectAsState()
    var displayEpoch by remember { mutableIntStateOf(0) }

    // Displays added or removed while Voltcap is open.
    DisposableEffect(activity) {
        val manager = activity?.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { displayEpoch++ }
            override fun onDisplayRemoved(displayId: Int) { displayEpoch++ }
            override fun onDisplayChanged(displayId: Int) {}
        }
        runCatching { manager?.registerDisplayListener(listener, Handler(Looper.getMainLooper())) }
        onDispose { runCatching { manager?.unregisterDisplayListener(listener) } }
    }

    // Back at the main screen after using an app on the second one.
    DisposableEffect(activity) {
        val decor = activity?.window?.decorView
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) SecondScreenGate.releaseIfSettled()
        }
        runCatching { decor?.viewTreeObserver?.addOnWindowFocusChangeListener(listener) }
        onDispose {
            runCatching {
                val vto = decor?.viewTreeObserver
                if (vto != null && vto.isAlive) vto.removeOnWindowFocusChangeListener(listener)
            }
        }
    }

    val display = remember(enabled, activity, displayEpoch) {
        if (enabled && activity != null) findSecondDisplay(activity) else null
    }
    val currentContent by rememberUpdatedState(content)
    val currentActivity by rememberUpdatedState(onUserActivity)
    val presentationHolder = remember { arrayOfNulls<Presentation>(1) }
    val textInputs by TextInputFocus.active.collectAsState()
    val parent = rememberCompositionContext()
    // Only true once the second screen actually came up — some devices report
    // extra displays (cover screens, casting) that refuse to show anything.
    var showing by remember { mutableStateOf(false) }

    DisposableEffect(display?.displayId, activity, suppressed) {
        var dimView: android.view.View? = null // (Update 188)
        val presentation = if (display != null && activity != null && !suppressed) {
            runCatching {
                object : Presentation(activity, display) {
                    // The controller always drives the main screen. If this window ever gets the keys (a tap
                    // here used to move the focus to this screen, and the D-pad / B then did nothing — stuck on
                    // the second screen), they're handed straight to the main screen.
                    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                        runCatching { currentActivity() }
                        val handled = runCatching { activity.dispatchKeyEvent(event) }.getOrDefault(false)
                        return handled || super.dispatchKeyEvent(event)
                    }
                    // Update 188: a touch here keeps it lit; while it's dimmed (SecondDim) the first touch only wakes it —
                    // that whole touch is used up, so it doesn't press what's under it.
                    private var swallow = false
                    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
                        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) swallow = SecondDim.dimmed
                        if (swallow) {
                            if (ev.actionMasked == android.view.MotionEvent.ACTION_UP || ev.actionMasked == android.view.MotionEvent.ACTION_CANCEL) {
                                swallow = false
                                SecondDim.poke()
                            }
                            return true
                        }
                        SecondDim.poke()
                        return super.dispatchTouchEvent(ev)
                    }
                    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
                        val handled = runCatching { activity.dispatchGenericMotionEvent(event) }.getOrDefault(false)
                        return handled || super.dispatchGenericMotionEvent(event)
                    }
                    // The back swipe on the second screen (AYN Thor's lower screen) used to close this window
                    // and leave the stock launcher's drawer there. Now it flips the second screen's pages.
                    @Deprecated("Dialog back")
                    override fun onBackPressed() {
                        runCatching { currentActivity() }
                        SecondScreenGestures.onBack?.invoke()
                    }
                }.apply {
                    setCancelable(false)
                    // Update 188: the screen's content with a black layer over it for dimming (SecondDim).
                    val dimLayer = android.view.View(context).apply { setBackgroundColor(android.graphics.Color.BLACK) }
                    val root = android.widget.FrameLayout(context)
                    root.addView(ComposeView(context).apply {
                        setParentCompositionContext(parent)
                        setContent {
                            // Watch touches without consuming them, only to reset the idle timers.
                            LaunchedEffect(Unit) { SecondIdle.poke(); SecondIdle.watch() }
                            Box(
                                Modifier.fillMaxSize().pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent(PointerEventPass.Initial)
                                            SecondIdle.poke()
                                            com.shelf.launcher.data.BatterySaver.poke() // (Update 187: a touch here wakes a resting Voltcap)
                                            currentActivity()
                                        }
                                    }
                                }
                            ) {
                                // Dual screens: both displays animate at once, so on handhelds already
                                // running scenes below Full the second screen goes one step lighter
                                // (Full stays Full on strong devices). Update 121: untouched for a while,
                                // its background runs at ~15 fps until the next touch there.
                                val slow = SecondIdle.idle && com.shelf.launcher.data.Features.secondSlowWhenUnused.state.value
                                androidx.compose.runtime.CompositionLocalProvider(
                                    LocalSceneQuality provides secondScreenQuality(LocalSceneQuality.current),
                                    LocalSceneMinFrameMs provides (if (slow) 66 else 0),
                                    // Update 187: the battery saver's low-battery rest holds the scene here too.
                                    LocalSceneCovered provides (LocalSceneCovered.current || com.shelf.launcher.data.BatterySaver.low),
                                    LocalOnSecondScreen provides true // Update 177: the controller can come here (SecondPad.kt)
                                ) {
                                    currentContent()
                                    SecondPadHint()
                                }
                            }
                        }
                    }, android.widget.FrameLayout.LayoutParams(-1, -1))
                    root.addView(dimLayer, android.widget.FrameLayout.LayoutParams(-1, -1))
                    setContentView(root)
                    SecondDim.attach(window, dimLayer)
                    dimView = dimLayer
                    window?.decorView?.let { decor ->
                        decor.setViewTreeLifecycleOwner(activity)
                        decor.setViewTreeViewModelStoreOwner(activity)
                        decor.setViewTreeSavedStateRegistryOwner(activity)
                    }
                    // No text fields here, so the keyboard must never target this window. And it never takes
                    // the focus: touches still work, the controller stays on the main screen.
                    window?.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                    show()
                }
            }.getOrNull()
        } else null
        presentationHolder[0] = presentation
        showing = presentation != null
        SecondScreenShown.value = presentation != null
        onDispose {
            showing = false
            SecondScreenShown.value = false
            presentationHolder[0] = null
            // (Update 188: its dimming layer goes with it.)
            dimView?.let { SecondDim.detach(it) }
            presentation?.let { runCatching { it.dismiss() } }
        }
    }

    // While a text field is being edited on the main display, the second
    // screen can't take focus (touches still work): otherwise the keyboard
    // fails or opens for the wrong display. Focus goes back to the main
    // window afterwards.
    LaunchedEffect(textInputs > 0, showing) {
        val window = presentationHolder[0]?.window ?: return@LaunchedEffect
        runCatching {
            if (textInputs > 0) {
                window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            } else {
                // Stays not focusable (see above); the main window gets its focus back.
                window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                activity?.window?.decorView?.requestFocus()
            }
        }
    }
    // While an app has the second display, the screen still counts as in use.
    return showing || (suppressed && display != null)
}

/** Voltcap's own second screen is up right now (not just a display there, or one an app has taken). */
internal object SecondScreenShown {
    var value by androidx.compose.runtime.mutableStateOf(false)
}

/**
 * What the second screen shows while no details window is open: the focused
 * game (cover, title, playtime, award, achievement progress) over its
 * blurred background art, and the music player when something is playing.
 */
@Composable
fun CompanionPanel(
    game: Game?,
    coverUrl: String?,
    heroUrl: String?,
    playtimeMs: Long?,
    award: RaAward?,
    progress: RaGameProgress?,
    nowPlaying: NowPlaying?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("CompanionPanel")
    Box(Modifier.fillMaxSize().background(ShelfTheme.background)) {
        if (heroUrl != null) {
            // Blurred once (update 121), not on every redraw of the panel over it.
            BakedBlurImage(heroUrl, 32.dp, Modifier.fillMaxSize(), alpha = 0.4f)
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, ShelfTheme.background.copy(alpha = 0.9f)))
            )
        )
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            if (game == null) {
                Text("Voltcap", color = ShelfTheme.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(96.dp)
                            .height(134.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        if (coverUrl != null) {
                            AsyncImage(
                                model = coverUrl, contentDescription = game.name,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                        } else if (game.sourceType == SourceType.APP) {
                            AppIconArt(game.packageName, wide = false, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            game.name, color = ShelfTheme.colors.textPrimary, fontSize = 24.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(24.sp), maxFontSize = 24.sp),
                            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Text(game.platform, color = ShelfTheme.secondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if ((playtimeMs ?: 0L) > 0L) {
                            Text(
                                "Played ${PlaytimeStore.format(playtimeMs ?: 0L)}",
                                color = ShelfTheme.textMuted, fontSize = 13.sp
                            )
                        }
                        if (award != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(award.label.uppercase(), color = ShelfTheme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        val possible = progress?.NumPossibleAchievements ?: 0
                        if (possible > 0) {
                            val earned = progress?.NumAchieved ?: 0
                            Spacer(Modifier.height(10.dp))
                            Text("$earned / $possible achievements", color = ShelfTheme.colors.textPrimary, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                            ) {
                                Box(
                                    Modifier.fillMaxWidth((earned.toFloat() / possible).coerceIn(0f, 1f))
                                        .height(5.dp).background(ShelfTheme.accent)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (nowPlaying != null) {
                MusicPlayer(nowPlaying, onPlayPause, onNext, onPrevious, onSeek)
            }
        }
    }
}


/** The scene quality for the second display: one step below the main screen's, unless that's Full. */
internal fun secondScreenQuality(main: com.shelf.launcher.data.SceneQuality): com.shelf.launcher.data.SceneQuality = when (main) {
    com.shelf.launcher.data.SceneQuality.AUTO, com.shelf.launcher.data.SceneQuality.HIGH -> main
    // The second screen stays at Full but 30 fps (two 60 fps scenes at once is a lot of battery).
    com.shelf.launcher.data.SceneQuality.SMOOTH -> com.shelf.launcher.data.SceneQuality.HIGH
    // The same quality as the main screen (it used to drop a whole step, so it always looked blocky there).
    com.shelf.launcher.data.SceneQuality.BALANCED, com.shelf.launcher.data.SceneQuality.LOW,
    com.shelf.launcher.data.SceneQuality.LOWEST -> main
}
