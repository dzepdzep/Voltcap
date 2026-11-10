package com.shelf.launcher.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.GameArt
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import kotlin.math.roundToInt

/*
 * Update 249 (#119): the two earlier designs for "A on a game in the row", kept as game window styles of their own
 * (Settings › Home › Layout options › Game window › Its motion, and R1 › Popup) next to the windows there were:
 *
 *  - The row opens up (Features.WinMotion.ROW_OPENS): the band of the row rises into a page over the whole screen, the
 *    game's cover grows out of its place in the row to the page's left, and its name, details and buttons come in
 *    beside it. B folds it back down into the row.
 *  - Row reveal (Features.WinMotion.ROW_REVEAL): nothing grows. The art stays where it is and the game you're on stays
 *    in its place; the other games fade away, the shade over the art deepens, the game's name rises from its place by
 *    the row to the top, Play / Continue and the buttons slide down into the row beside the game, and the description,
 *    facts and achievements appear in the room between. B plays it backwards.
 *
 * Both are drawn in a window of their own with no dim of its own, over the home screen, and use the place the game you're
 * on was last drawn (GameOrigin); the row's own cards fade through [RowReveal]. Without a place on this screen (picked on
 * the other screen) the window is the usual one.
 */

/** Update 249: how far the row has turned into the game window, read by the row's cards while they draw. */
internal object RowReveal {
    /** 0…1: the other games in the row (and the name / category lines by it) fade by this much. */
    val others = mutableFloatStateOf(0f)
    /** The game you're on is drawn by the window while it moves (The row opens up): the row's own card hides. */
    val focusHidden = mutableStateOf(false)
    /**
     * 0…1: Row reveal's shade over the home screen's art, drawn by the home screen itself under its rows
     * ([RowRevealShade]) so the game you're on stays bright over it, whatever shape its card is.
     */
    val shade = mutableFloatStateOf(0f)

    /**
     * Update 250 (Shared move): the whole row leaves (the game you're on too), dropping by [drop] (0…1 of [DROP_DP]);
     * the name line and the category are drawn by the window from where they were ([sharedHidden]).
     */
    val rowAll = mutableStateOf(false)
    val drop = mutableFloatStateOf(0f)
    val sharedHidden = mutableStateOf(false)
    const val DROP_DP = 46f

    /** The alpha a row card multiplies by ([focused]: the game you're on). */
    fun alphaFor(focused: Boolean): Float =
        if (focused && !rowAll.value) (if (focusHidden.value) 0f else 1f) else 1f - others.floatValue

    /** The alpha of the parts the home screen adds (logo, info cards): they fade with the row. */
    fun textAlpha(): Float = 1f - others.floatValue

    /** The alpha of the row's own name line and the category name: hidden at once while the window draws them. */
    fun sharedAlpha(): Float = if (sharedHidden.value) 0f else 1f - others.floatValue

    fun reset() {
        others.floatValue = 0f; focusHidden.value = false; shade.floatValue = 0f
        rowAll.value = false; drop.floatValue = 0f; sharedHidden.value = false
    }
}

/**
 * Update 250 (Shared move): where the Voltcap row's name line, its play time and the category name were last drawn
 * (window px), so the game window's title, Played card and eyebrow start exactly there. Plain fields, written while
 * laying out; [at] when (uptime ms), so an old place from another layout isn't used.
 */
internal object GlanceOrigin {
    var name: Rect? = null
    var chip: Rect? = null
    var category: Rect? = null
    var at: Long = 0L

    /** [c]'s place on the screen (the game window is a window of its own: its corner isn't the home screen's). */
    fun onScreen(c: androidx.compose.ui.layout.LayoutCoordinates): Rect {
        val o = c.positionOnScreen()
        return Rect(o.x, o.y, o.x + c.size.width, o.y + c.size.height)
    }
}

private val RevealEase = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

/** v0.6.66: the keys that open (A / Enter / centre) — while the window closes they open it again instead of pressing a button. */
internal fun rowWindowOpenKey(k: androidx.compose.ui.input.key.Key): Boolean =
    k == androidx.compose.ui.input.key.Key.ButtonA || k == androidx.compose.ui.input.key.Key.Enter ||
        k == androidx.compose.ui.input.key.Key.DirectionCenter || k == androidx.compose.ui.input.key.Key.NumPadEnter
private fun part(p: Float, from: Float, to: Float) = ((p - from) / (to - from)).coerceIn(0f, 1f)

/**
 * The game window as a move of the home screen ([style]); false when it can't be drawn here (no place for the game on
 * this screen), so the caller shows the usual window.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun RowMotionWindow(
    style: Features.WinMotion,
    g: Game,
    state: LauncherUiState,
    art: Map<String, GameArt>,
    onClose: () -> Unit,
    onPlay: () -> Unit,
    onFullDetails: () -> Unit,
    onNeedMedia: (Game) -> Unit,
    onOptions: () -> Unit,
    onAchievements: () -> Unit,
    /** Update 325 (Toni: "some popups are missing the inspect function"): the game window with the piece out; null: an app. */
    onInspect: (() -> Unit)? = null
): Boolean {
    val view = LocalView.current
    val origin = remember(g.stableKey) {
        val here = runCatching { view.display?.displayId }.getOrNull() ?: -1
        if ((GameOrigin.key == g.stableKey || GameOrigin.key == GameOrigin.FOCUSED) && GameOrigin.rect != null &&
            (GameOrigin.displayId == here || GameOrigin.displayId < 0)) GameOrigin.rect else null
    }
    if (origin == null || origin.width < 4f || origin.height < 4f) { if (QaTrace.on) android.util.Log.e("VoltcapQA", "row window: no origin, scene window instead"); return false }
    if (QaTrace.on) android.util.Log.e("VoltcapQA", "row window open $style")

    val k = g.stableKey
    val titles = LocalTitleOptions.current
    val cover = state.artOverrides[k]?.cover ?: art[k]?.gridUrl
    val resumed = state.lastPlayedAt[k] != null
    val meta = state.metadata[k]
    val playMs = state.playtime[k]
    val lastAt = state.lastPlayedAt[k]
    val trophies = state.raAchievementDetails[k].orEmpty()
    val prog = state.raProgress[k]
    val total = if (trophies.isNotEmpty()) trophies.size else prog?.NumPossibleAchievements ?: 0
    val earned = if (trophies.isNotEmpty()) trophies.count { it.isUnlocked } else prog?.NumAchieved ?: 0
    val accent = ShelfTheme.accent
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF111111) else Color.White
    val anim = MotionPrefs.info() != Features.InfoAnim.NONE && rememberAnimationsEnabled()
    val opens = style == Features.WinMotion.ROW_OPENS

    // 0: the home screen as it was; 1: the details.
    val p = remember(k) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    var closeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val closeNow by rememberUpdatedState(onClose)
    val close: () -> Unit = {
        if (QaTrace.on) android.util.Log.e("VoltcapQA", "row close closing=$closing")
        if (!closing) {
            closing = true
            closeJob = scope.launch {
                if (anim) p.animateTo(0f, tween(if (opens) 380 else 340, easing = RevealEase)) else p.snapTo(0f)
                RowReveal.reset()
                closeNow()
            }
        }
    }
    // v0.6.66 popup audit (Odin, Channel grid + The row opens up, A B A B at 250 ms started Donkey Konga 2): while the move
    // went back, the closing window still had the focus on ▶ Continue, so an A meant for the home screen pressed it. Now a
    // closing window takes no button presses; an A while it closes opens it again (the D108 rule of the other windows).
    val reopen: () -> Unit = {
        if (closing) {
            closeJob?.cancel(); closeJob = null; closing = false
            scope.launch { if (anim) p.animateTo(1f, tween(if (opens) 420 else 380, easing = RevealEase)) else p.snapTo(1f) }
        }
    }
    val live: (() -> Unit) -> Unit = { f -> if (!closing) f() }
    var swallowUp by remember { mutableStateOf<androidx.compose.ui.input.key.Key?>(null) }
    // (Batch 523, owner: "Details opens a completely different-looking popup" - in Row opens / Row reveal, Details now opens
    // the whole story and every fact IN this window, its type and colours, where the short version was; B / Less go back.
    // The grid's action panel - no room for a story - still opens the full details screen.)
    var showAll by remember(g.stableKey) { mutableStateOf(false) }
    val detailsFocus = remember { FocusRequester() }
    var openKeyDown by remember { mutableStateOf(false) }
    // The row's cards follow the move while it draws.
    LaunchedEffect(p) {
        androidx.compose.runtime.snapshotFlow { p.value }.collect { v ->
            RowReveal.others.floatValue = part(v, 0f, 0.56f)
            RowReveal.focusHidden.value = opens && v > 0.001f
            RowReveal.shade.floatValue = if (opens) 0f else v
        }
    }
    DisposableEffect(k) { onDispose { RowReveal.reset() } }
    LaunchedEffect(k) {
        onNeedMedia(g)
        if (anim) p.animateTo(1f, tween(if (opens) 560 else 520, easing = RevealEase)) else p.snapTo(1f)
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = close,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        ImmersiveDialogWindow()
        // No dim and no window animation of its own: the move is the animation, over the home screen as it is.
        val dlgView = LocalView.current
        DisposableEffect(dlgView) {
            (dlgView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w ->
                runCatching { w.setDimAmount(0f); w.setWindowAnimations(0) }
            }
            onDispose { }
        }
        val playFocus = remember { FocusRequester() }
        val storyFocus = remember { FocusRequester() }
        // (Update 325, Toni: "moving right from Continue it goes straight back to Continue" — here and in Row reveal: Play
        // was asked for again and again for the first ~0.9 s, and again whenever the window got the focus back, so a step
        // to the next button was taken back. Once a button has the focus it's left alone.)
        val buttonsFocused = remember { androidx.compose.runtime.mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(20); KeyInput.leaveTouchMode(dlgView)
            for (wait in longArrayOf(60, 140, 250, 400)) {
                kotlinx.coroutines.delay(wait)
                if (buttonsFocused.value) break
                runCatching { playFocus.requestFocus() }
            }
        }
        val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
        LaunchedEffect(windowInfo.isWindowFocused) {
            if (windowInfo.isWindowFocused) { kotlinx.coroutines.delay(30); if (!buttonsFocused.value) runCatching { playFocus.requestFocus() } }
        }
        val d = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            // (The release of the A that opened it again is that A's too: a button clicks on release, so it pressed
            // ▶ Continue right after re-opening — GT78 Cross menu, Shared move, started the game.)
            if (swallowUp != null && e.key == swallowUp && e.type == androidx.compose.ui.input.key.KeyEventType.KeyUp) { swallowUp = null; return@onPreviewKeyEvent true }
            // (…and the release of the A that opened this window on the home screen: its press went to the home screen,
            // only the release arrives here — a button clicks on release, so it started the game the moment it opened.)
            if (rowWindowOpenKey(e.key)) {
                if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) { openKeyDown = true; HomeKeyGuard.aDown = false }
                else if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyUp && !openKeyDown) return@onPreviewKeyEvent true
                else if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyUp) openKeyDown = false
            }
            // (Batch 523: B with the story open goes back to the short view first, not out of the window.)
            if (showAll && !closing && (e.key == androidx.compose.ui.input.key.Key.Back || e.key == androidx.compose.ui.input.key.Key.ButtonB ||
                    e.key == androidx.compose.ui.input.key.Key.Escape)) {
                if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyUp) { showAll = false; runCatching { detailsFocus.requestFocus() } }
                return@onPreviewKeyEvent true
            }
            if (!closing) return@onPreviewKeyEvent false
            if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && rowWindowOpenKey(e.key)) { swallowUp = e.key; reopen() }
            true
        }.pointerInput(Unit) { detectTapGestures { if (closing) reopen() else close() } }) {
            val W = constraints.maxWidth.toFloat()
            val H = constraints.maxHeight.toFloat()
            val pad = with(d) { 24.dp.toPx() }
            val topY = maxOf(with(d) { 44.dp.toPx() }, H * 0.08f)
            // The window lies over the whole screen from its top-left corner, as the home screen's own window does.
            val c = origin
            val narrow = W < with(d) { 640.dp.toPx() }

            // ---- the shade / the page ----
            if (opens) {
                // The row's band (the row and what's under it) rises into a page over the whole screen.
                val band = Rect(0f, (c.top - with(d) { 18.dp.toPx() }).coerceIn(0f, H), W, H)
                Canvas(Modifier.fillMaxSize()) {
                    val t = p.value
                    val top = lerpF(band.top, 0f, t)
                    val r = with(d) { 26.dp.toPx() } * (1f - t)
                    drawRoundRect(
                        Brush.verticalGradient(listOf(Color(0xFA101318), Color(0xFF0A0C10)), startY = top, endY = H),
                        topLeft = Offset(0f, top), size = Size(W, H - top + r),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r), alpha = part(t, 0f, 0.25f)
                    )
                    drawRect(accent.copy(alpha = 0.10f * t), topLeft = Offset(0f, top), size = Size(W, 2f * density))
                }
            } else {
                // The art stays; the home screen deepens its own shade under its rows (RowRevealShade), so the game you're
                // on stays bright over it. Here only a little more behind the words, never reaching the game.
                // (v0.6.66 popup review: a whole-screen layout draws no RowRevealShade and its own title sat under the window's —
                // the name twice on Cinema / Arctic / Cross menu / Disc station. There the shade covers everything but the game.)
                // (Update 461 popup audit, KONKR + Odin: the 0.8 shade still let a layout's own bright title / menu show through the
                // words — Cube menu, File select, Game rentals — and the Hero carousel, Channel grid and TV guide had none, so the
                // window's words sat on their covers and text. WindowShade: those three too, and 0.9.)
                if (WindowShade.on(state.appearance.homeLayout)) Canvas(Modifier.fillMaxSize()) {
                    val t = p.value
                    clipRect(c.left - 6f, c.top - 6f, c.right + 6f, c.bottom + 6f, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
                        drawRect(Color.Black.copy(alpha = WindowShade.ALPHA * t))
                    }
                }
                Canvas(Modifier.fillMaxSize()) {
                    val t = p.value
                    val keep = with(d) { 10.dp.toPx() }
                    if (c.top > H / 2f) clipRect(0f, 0f, W, (c.top - keep).coerceAtLeast(0f)) {
                        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f * t), Color.Black.copy(alpha = 0.25f * t)), endY = c.top))
                    } else clipRect(0f, (c.bottom + keep).coerceAtMost(H), W, H) {
                        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f * t), Color.Black.copy(alpha = 0.45f * t)), startY = c.bottom))
                    }
                }
            }

            // ---- where everything goes ----
            // The Row opens up: the cover grows from its place to the page's left; the text and buttons beside it.
            // Row reveal: the cover doesn't move; the name rises from above it to the top, the buttons slide down into
            // the row beside it.
            val coverEnd: Rect = if (opens) {
                // (v0.6.66 popup review: a wide card — Cinema's, the Channel grid's — grew into a cover wider than half the screen
                // and pushed the text and buttons off its right edge. At most 42 % of the width now, its own shape kept.)
                var h = (H * if (narrow) 0.42f else 0.56f)
                val aspect = c.width / c.height
                var w = h * aspect
                val maxW = W * 0.42f
                if (w > maxW) { w = maxW; h = w / aspect }
                Rect(pad, topY + with(d) { 8.dp.toPx() }, pad + w, topY + with(d) { 8.dp.toPx() } + h)
            } else c
            val textLeft = if (opens) coverEnd.right + with(d) { 28.dp.toPx() } else pad
            // (v0.6.66: never wider than the room that is really left — the 200 dp floor ran the text off a small screen.)
            val textW = (W - textLeft - pad).coerceAtLeast(with(d) { 120.dp.toPx() })

            var titleSize by remember { mutableStateOf(IntSize.Zero) }
            var buttonsSize by remember { mutableStateOf(IntSize.Zero) }
            val gap = with(d) { 10.dp.toPx() }
            // v0.6.66 (D142): the Channel grid (and any whole-screen layout with no room beside the game) put the bare name and
            // buttons over unrelated tiles. There the name, its line and the buttons sit together in an action panel of their own
            // right under the game (over it when it's in the bottom row), a notch pointing at the game, on the panel's dark backing.
            val sideRoom = c.right + with(d) { 24.dp.toPx() } + buttonsSize.width <= W - pad
            val inner = with(d) { 8.dp.toPx() }
            val panelPadPx = with(d) { 12.dp.toPx() }
            val notchPx = with(d) { 9.dp.toPx() }
            val panelHPx = 2 * panelPadPx + titleSize.height + inner + buttonsSize.height
            val fitsBelow = c.bottom + gap + notchPx + panelHPx <= H - gap
            val fitsAbove = c.top - gap - notchPx - panelHPx >= gap
            // (Only where it fits whole above or under the game — a tall Case stacks case leaves no room: the buttons go as before.)
            val panel = !opens && state.appearance.homeLayout.styleHome &&
                (state.appearance.homeLayout == com.shelf.launcher.data.HomeLayout.CHAN || !sideRoom) && (fitsBelow || fitsAbove)
            val panelPad = if (panel) panelPadPx else 0f
            val notch = if (panel) notchPx else 0f
            val panelH = panelHPx
            val panelBelow = fitsBelow
            val panelTop = if (panelBelow) c.bottom + gap + notch else c.top - gap - notch - panelH
            val titleFrom = if (opens) Offset(textLeft, coverEnd.top + with(d) { 30.dp.toPx() })
                else if (panel) Offset(c.left, if (panelBelow) c.bottom - titleSize.height else c.top)
                else Offset(c.left, (c.top - gap - titleSize.height).coerceAtLeast(topY))
            val titleTo = if (opens) Offset(textLeft, coverEnd.top) else if (panel) Offset(pad + panelPad, panelTop + panelPad) else Offset(pad, topY)
            val buttonsFrom = if (opens) Offset(textLeft, coverEnd.bottom - buttonsSize.height + with(d) { 26.dp.toPx() })
                else Offset(c.left, (c.top - gap - buttonsSize.height).coerceAtLeast(topY))
            // (v0.6.66: a short cover — a wide card's — left the wrapped buttons over the name: never above the name's line.)
            val buttonsTo = if (opens) Offset(textLeft, maxOf(coverEnd.bottom - buttonsSize.height, titleTo.y + titleSize.height + with(d) { 12.dp.toPx() }))
                else if (panel) Offset(pad + panelPad, titleTo.y + titleSize.height + inner)
                else if (sideRoom) Offset(c.right + with(d) { 24.dp.toPx() }, c.center.y - buttonsSize.height / 2f)
                // (No room beside the game: under it, or over it when it's at the bottom edge.)
                else Offset(pad, if (c.bottom + gap + buttonsSize.height <= H - gap) c.bottom + gap else c.top - gap - buttonsSize.height)
            // (The panel holds the name and the buttons only: the story and facts are a press away, Details.)
            val infoTop = titleTo.y + titleSize.height + with(d) { 14.dp.toPx() }
            val infoBottom = if (panel) infoTop else if (opens) buttonsTo.y - with(d) { 14.dp.toPx() }
                else if (buttonsTo.y < c.top) buttonsTo.y - with(d) { 14.dp.toPx() } else c.top - with(d) { 18.dp.toPx() }

            // ---- the cover (The row opens up only: in Row reveal the row's own card stays where it is) ----
            if (opens && cover != null) {
                // (Batch 523, Smart Fill: the opened card cut a portrait cover's foot off in a square tile's shape - it now
                // fills by a light crop or shows the whole cover over its soft copy; never stretched.)
                SmartArt(
                    cover, g.name, request = rememberCardArtRequest(cover, ArtKind.COVER), well = Color(0xFF101114),
                    modifier = Modifier
                        .graphicsLayer {
                            val t = p.value
                            val l = lerpF(c.left, coverEnd.left, t); val tp = lerpF(c.top, coverEnd.top, t)
                            val w = lerpF(c.width, coverEnd.width, t); val h = lerpF(c.height, coverEnd.height, t)
                            translationX = l; translationY = tp
                            scaleX = w / coverEnd.width; scaleY = h / coverEnd.height
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                            shadowElevation = 18f * t
                            shape = RoundedCornerShape(8.dp); clip = true
                        }
                        .size(with(d) { coverEnd.width.toDp() }, with(d) { coverEnd.height.toDp() })
                )
            }

            // ---- (D142) the grid's action panel behind the name and buttons, with its notch at the game ----
            if (panel && buttonsSize != IntSize.Zero) Canvas(Modifier.fillMaxSize()) {
                val t = p.value
                val a = part(t, 0f, 0.35f)
                val slide = (1f - part(t, 0f, 0.6f)) * with(d) { 14.dp.toPx() } * (if (panelBelow) -1f else 1f)
                val top = panelTop + slide
                val bottom = panelTop + panelH + slide
                val r = with(d) { 14.dp.toPx() }
                val fill = Color(0xF0121519)
                drawRoundRect(fill, topLeft = Offset(pad, top), size = Size(W - 2 * pad, bottom - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r), alpha = a)
                drawRoundRect(accent.copy(alpha = 0.55f), topLeft = Offset(pad, top), size = Size(W - 2 * pad, bottom - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r), alpha = a,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * density))
                val nx = c.center.x.coerceIn(pad + r + notch, W - pad - r - notch)
                val path = androidx.compose.ui.graphics.Path().apply {
                    if (panelBelow) { moveTo(nx - notch, top + 1f); lineTo(nx, top - notch); lineTo(nx + notch, top + 1f) }
                    else { moveTo(nx - notch, bottom - 1f); lineTo(nx, bottom + notch); lineTo(nx + notch, bottom - 1f) }
                    // (this.close(): a bare close() here is the window's own close — a local beats the Path's member — and shut
                    // the window the moment the panel was drawn.)
                    this.close()
                }
                drawPath(path, fill, alpha = a)
            }

            // ---- the name and its line ----
            Column(
                Modifier
                    .onSizeChanged { titleSize = it }
                    .graphicsLayer {
                        val t = p.value
                        translationX = lerpF(titleFrom.x, titleTo.x, t)
                        translationY = lerpF(titleFrom.y, titleTo.y, t)
                        // (Row reveal: it comes in as the row's own name line fades — RowReveal.textAlpha — so the two never
                        // show at once at the start or the end.)
                        alpha = if (titleSize == IntSize.Zero) 0f else if (opens) part(t, 0.35f, 0.85f) else part(t, 0.12f, 0.4f)
                    }
                    .widthIn(max = with(d) { (if (panel) W - 2 * pad - 2 * panelPad else textW).toDp() })
            ) {
                // (Batch 522, popup sweep ROW_OPENS / ROW_REVEAL: the name and the line under it ended in "…" (rule 11) - whole now:
                // smaller, then sliding.)
                WinFullText(
                    titles.display(g.name), color = Color.White, fontSize = if (narrow) 22.sp else 28.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, lineHeight = 1.1.em, style = nameTextStyle(com.shelf.launcher.data.NameBackdrop.SHADOW)
                )
                WinFullText(
                    listOfNotNull(
                        titles.console(g.platform).takeIf { g.sourceType != com.shelf.launcher.data.SourceType.APP },
                        stylePlaytime(playMs, resumed),
                        lastAt?.let { "Last played " + android.text.format.DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), android.text.format.DateUtils.DAY_IN_MILLIS) }
                    ).joinToString("  ·  "),
                    color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // ---- what appears in the room they leave ----
            val infoH = (infoBottom - infoTop)
            if (infoH > with(d) { 40.dp.toPx() }) Column(
                Modifier
                    .graphicsLayer {
                        // (After the name has passed over it on its way up — Row reveal's name rises across this room.)
                        val t = part(p.value, if (opens) 0.42f else 0.55f, 1f)
                        translationX = textLeft
                        translationY = infoTop + with(d) { 14.dp.toPx() } * (1f - t)
                        alpha = t
                    }
                    // (Row reveal: the left part only, clear of the game's art and logo on the right.)
                    .width(with(d) { minOf(if (opens) textW else minOf(textW, W * 0.62f), with(d) { 760.dp.toPx() }).toDp() })
                    .height(with(d) { infoH.toDp() })
            ) {
                // (Update 475, D245: the facts and the achievements come first — they are short and exist nowhere else in this
                // window; the story takes only the room they leave (the whole story is one press away, Details) and gives way
                // entirely on a short screen. Before, the story claimed its 3–5 lines first and the facts / achievements under it
                // were laid out with no height at all: KONKR 17 combinations, Odin 5, k471a / k474a.)
                val summary = meta?.summary?.takeIf { it.isNotBlank() }
                if (showAll) {
                    // (Batch 523: Details in this window - the whole story, scrolled with ▲ ▼ / a finger, then every fact.)
                    val sc = androidx.compose.foundation.rememberScrollState()
                    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { storyFocus.requestFocus() } }
                    val storyScope = androidx.compose.runtime.rememberCoroutineScope()
                    Column(Modifier.weight(1f).focusRequester(storyFocus)
                        // ▲ ▼ scroll the story while there's more; at its end ▼ goes on to the buttons (Less is there).
                        .onKeyEvent { e ->
                            if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onKeyEvent false
                            val dir = when (e.key) { androidx.compose.ui.input.key.Key.DirectionDown -> 1; androidx.compose.ui.input.key.Key.DirectionUp -> -1; else -> return@onKeyEvent false }
                            if ((dir > 0 && !sc.canScrollForward) || (dir < 0 && !sc.canScrollBackward)) return@onKeyEvent false
                            storyScope.launch { sc.animateScrollBy(dir * 180f) }; true
                        }
                        .focusable().verticalScroll(sc)) {
                        Text(summary ?: "No description yet.", color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, lineHeight = (13 * 1.45f).sp)
                        val all = revealFacts(meta?.developer, meta?.genres?.joinToString(", "), meta?.releaseYear, earned, total)
                        if (all.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(
                            Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            all.forEach { (h, v) ->
                                Column {
                                    Text(h.uppercase(), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em, maxLines = 1)
                                    Text(v, color = Color.White, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                } else {
                if (summary != null) StoryLines(
                    summary, Color.White.copy(alpha = 0.9f), 13.sp, (13 * 1.4f).sp, if (infoH > with(d) { 170.dp.toPx() }) 5 else 3,
                    Modifier.weight(1f, fill = false)
                )
                val facts = revealFacts(meta?.developer, meta?.genres?.firstOrNull(), meta?.releaseYear, earned, total)
                if (facts.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(
                    Modifier.padding(top = if (summary != null) 10.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    facts.forEach { (h, v) ->
                        Column {
                            Text(h.uppercase(), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em, maxLines = 1)
                            Text(v, color = Color.White, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
                if (total > 0 && infoH > with(d) { 150.dp.toPx() }) {
                    Box(Modifier.padding(top = 8.dp).width(220.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.16f))) {
                        Box(Modifier.fillMaxWidthFraction(earned.toFloat() / total).height(5.dp).background(accent))
                    }
                }
                }
            }

            // ---- Play / Continue and the buttons ----
            androidx.compose.foundation.layout.FlowRow(
                Modifier
                    // (v0.6.66: the buttons wrap onto a second line inside the text column instead of running off the screen.)
                    .widthIn(max = with(d) { (if (opens) textW else W - 2 * pad - 2 * panelPad).toDp() })
                    .onSizeChanged { buttonsSize = it }
                    .onFocusChanged { buttonsFocused.value = it.hasFocus }
                    .graphicsLayer {
                        val t = p.value
                        translationX = lerpF(buttonsFrom.x, buttonsTo.x, t)
                        translationY = lerpF(buttonsFrom.y, buttonsTo.y, t)
                        alpha = if (buttonsSize == IntSize.Zero) 0f else if (opens) part(t, 0.4f, 0.9f) else part(t, 0f, 0.35f)
                    },
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RevealButton(if (resumed) "▶  Continue" else "▶  Play", main = true, accent = accent, onAccent = onAccent, modifier = Modifier.focusRequester(playFocus)) {
                    live { RowReveal.reset(); onPlay() }
                }
                RevealButton(if (showAll) "Less" else "Details", accent = accent, onAccent = onAccent, modifier = Modifier.focusRequester(detailsFocus)) {
                    // (Batch 523: in this window where it has room for the story; the grid's panel opens the details screen.)
                    if (infoBottom - infoTop > with(d) { 40.dp.toPx() }) showAll = !showAll else live { RowReveal.reset(); onFullDetails() }
                }
                if (g.raGameId != null || g.platform == "Steam") RevealButton("Achievements", accent = accent, onAccent = onAccent) { live { RowReveal.reset(); onAchievements() } }
                RevealButton("Options", accent = accent, onAccent = onAccent) { live { RowReveal.reset(); onOptions() } }
                if (onInspect != null) RevealButton("◧  Inspect", accent = accent, onAccent = onAccent) { live { RowReveal.reset(); onInspect() } }
            }

            // ---- Update 325 (Toni: "add some stuff underneath there so it isn't so empty — achievements or something"):
            // under the buttons, the game's achievement badges (earned first, the rest faint), or a few of its screenshots
            // when it has none. Only where there's room for it. ----
            val stripTop = buttonsTo.y + buttonsSize.height + with(d) { 16.dp.toPx() }
            val stripH = with(d) { 58.dp.toPx() }
            val badges = trophies.sortedWith(compareBy<com.shelf.launcher.data.RaAchievementDetail> { if (it.isUnlocked) 0 else 1 }.then(com.shelf.launcher.data.RA_DISPLAY_ORDER))
                .mapNotNull { a -> com.shelf.launcher.data.RaBadges.url(a.badgeName)?.let { it to a.isUnlocked } }.take(10)
            val shots = if (badges.isEmpty()) state.screenshots[k].orEmpty().take(4) else emptyList()
            if (!panel && !showAll && buttonsSize != IntSize.Zero && (badges.isNotEmpty() || shots.isNotEmpty()) && stripTop + stripH <= H - with(d) { 12.dp.toPx() }) Row(
                Modifier
                    .graphicsLayer {
                        val t = p.value
                        translationX = buttonsTo.x
                        translationY = stripTop + with(d) { 10.dp.toPx() } * (1f - part(t, 0.5f, 1f))
                        alpha = part(t, 0.55f, 1f)
                    }
                    .widthIn(max = with(d) { (W - buttonsTo.x - pad).coerceAtLeast(0f).toDp() }),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                if (badges.isNotEmpty()) {
                    // (Update 471, found in the 471 sweep — KONKR, Row opens: the badges filled the strip and "+99" was squeezed to
                    // one letter a line. As many badges as fit with room for the count; the count on one line.)
                    val room = with(d) { (W - buttonsTo.x - pad).coerceAtLeast(0f).toDp() }
                    val fit = if (total > badges.size || ((badges.size * 52).dp > room)) (((room - 52.dp) / 52.dp).toInt()).coerceIn(0, badges.size) else badges.size
                    badges.take(fit).forEach { (url, unlocked) ->
                        AsyncImage(
                            url, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                                .graphicsLayer { alpha = if (unlocked) 1f else 0.35f }
                                .border(1.dp, if (unlocked) accent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        )
                    }
                    if (total > fit) Text("+${total - fit}", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, softWrap = false)
                } else shots.forEach { url ->
                    AsyncImage(
                        url, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.height(58.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    )
                }
            }
        }
    }
    return true
}

/** A part of the width, for the achievements bar. */
private fun Modifier.fillMaxWidthFraction(f: Float): Modifier = this.fillMaxWidth(f.coerceIn(0f, 1f))

@Composable
private fun RevealButton(label: String, main: Boolean = false, accent: Color, onAccent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    LaunchedEffect(focused) { if (focused) sounds?.play(UiSound.MOVE) }
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (focused) accent else if (main) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.13f))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.2f), shape)
            .clickable(interactionSource = source, indication = null) { sounds?.play(UiSound.SELECT); onClick() }
            .padding(horizontal = if (main) 22.dp else 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, color = if (focused) onAccent else if (main) Color(0xFF111111) else Color.White,
            fontSize = if (main) 16.sp else 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
        )
    }
}

/**
 * Update 249 (#119): Row reveal's shade over the home screen's art, under its rows (LauncherScreen draws it before them):
 * deepest at the top, for the words the game window lays over it.
 */
/**
 * Update 461 (popup audit): the layouts whose own picture and words stay under the Row reveal / Shared move windows (nothing
 * of them steps aside) — every whole-screen layout, plus the Hero carousel, Channel grid and TV guide — and how deep the
 * shade over them goes, so no title, menu or cover shows through the window's words (pure — unit-tested).
 */
internal object WindowShade {
    const val ALPHA = 0.9f
    fun on(layout: com.shelf.launcher.data.HomeLayout): Boolean =
        layout.styleHome || layout == com.shelf.launcher.data.HomeLayout.HERO || layout == com.shelf.launcher.data.HomeLayout.GRID ||
            layout == com.shelf.launcher.data.HomeLayout.TV
}

@Composable
internal fun RowRevealShade(modifier: Modifier) {
    Canvas(modifier) {
        val t = RowReveal.shade.floatValue
        if (t <= 0.001f) return@Canvas
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.72f * t), Color.Black.copy(alpha = 0.5f * t), Color.Black.copy(alpha = 0.6f * t))))
        drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.3f * t), Color.Transparent), startX = 0f, endX = size.width * 0.7f))
    }
}

/**
 * Update 249 (#119): the home screen's own parts (the game's logo, info cards, the category name — Edit Home) fade as
 * the row turns into the game window, so they don't show through its words. Nothing while no motion style is picked.
 */
@Composable
internal fun Modifier.rowRevealFade(): Modifier =
    if (ActiveWinMotion.value == Features.WinMotion.NONE) this
    else this.graphicsLayer { alpha = RowReveal.textAlpha() }

/**
 * Update 409 (D105): the row motion the home layout on screen uses (its own popup style, else the global default). Set by the
 * home screen from `PopupStyle.motion(layout)`; the row fades read it, so a layout set to another style never fades its parts.
 */
internal object ActiveWinMotion {
    var value by androidx.compose.runtime.mutableStateOf(Features.WinMotion.NONE)
}

/**
 * Update 475 (D245): the facts the row-style window shows under the story — developer, genre, year and the achievements
 * count as a fact like the others (it stays in view on every screen; the progress bar is drawn only when there is room).
 */
internal fun revealFacts(developer: String?, genre: String?, year: Int?, earned: Int, total: Int): List<Pair<String, String>> = listOfNotNull(
    developer?.takeIf { it.isNotBlank() }?.let { "Developer" to it }, genre?.takeIf { it.isNotBlank() }?.let { "Genre" to it },
    year?.let { "Released" to it.toString() }, total.takeIf { it > 0 }?.let { "Achievements" to "$earned / $it" }
)
