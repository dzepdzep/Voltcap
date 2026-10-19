package com.shelf.launcher.ui

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

    /** The alpha a row card multiplies by ([focused]: the game you're on). */
    fun alphaFor(focused: Boolean): Float =
        if (focused) (if (focusHidden.value) 0f else 1f) else 1f - others.floatValue

    /** The alpha of the row's name / category lines. */
    fun textAlpha(): Float = 1f - others.floatValue

    fun reset() { others.floatValue = 0f; focusHidden.value = false; shade.floatValue = 0f }
}

private val RevealEase = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun part(p: Float, from: Float, to: Float) = ((p - from) / (to - from)).coerceIn(0f, 1f)

/**
 * The game window as a move of the home screen ([style]); false when it can't be drawn here (no place for the game on
 * this screen), so the caller shows the usual window.
 */
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
    onAchievements: () -> Unit
): Boolean {
    val view = LocalView.current
    val origin = remember(g.stableKey) {
        val here = runCatching { view.display?.displayId }.getOrNull() ?: -1
        if ((GameOrigin.key == g.stableKey || GameOrigin.key == GameOrigin.FOCUSED) && GameOrigin.rect != null &&
            (GameOrigin.displayId == here || GameOrigin.displayId < 0)) GameOrigin.rect else null
    }
    if (origin == null || origin.width < 4f || origin.height < 4f) return false

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
    val closeNow by rememberUpdatedState(onClose)
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                if (anim) p.animateTo(0f, tween(if (opens) 380 else 340, easing = RevealEase)) else p.snapTo(0f)
                RowReveal.reset()
                closeNow()
            }
        }
    }
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
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(20); KeyInput.leaveTouchMode(dlgView)
            for (wait in longArrayOf(60, 140, 250, 400)) { kotlinx.coroutines.delay(wait); runCatching { playFocus.requestFocus() } }
        }
        val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
        LaunchedEffect(windowInfo.isWindowFocused) {
            if (windowInfo.isWindowFocused) { kotlinx.coroutines.delay(30); runCatching { playFocus.requestFocus() } }
        }
        val d = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { close() } }) {
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
                val h = (H * if (narrow) 0.42f else 0.56f)
                val w = h * (c.width / c.height)
                Rect(pad, topY + with(d) { 8.dp.toPx() }, pad + w, topY + with(d) { 8.dp.toPx() } + h)
            } else c
            val textLeft = if (opens) coverEnd.right + with(d) { 28.dp.toPx() } else pad
            val textW = (W - textLeft - pad).coerceAtLeast(with(d) { 200.dp.toPx() })

            var titleSize by remember { mutableStateOf(IntSize.Zero) }
            var buttonsSize by remember { mutableStateOf(IntSize.Zero) }
            val gap = with(d) { 10.dp.toPx() }
            val titleFrom = if (opens) Offset(textLeft, coverEnd.top + with(d) { 30.dp.toPx() })
                else Offset(c.left, (c.top - gap - titleSize.height).coerceAtLeast(topY))
            val titleTo = if (opens) Offset(textLeft, coverEnd.top) else Offset(pad, topY)
            val buttonsFrom = if (opens) Offset(textLeft, coverEnd.bottom - buttonsSize.height + with(d) { 26.dp.toPx() })
                else Offset(c.left, (c.top - gap - buttonsSize.height).coerceAtLeast(topY))
            val buttonsTo = if (opens) Offset(textLeft, coverEnd.bottom - buttonsSize.height)
                else if (c.right + with(d) { 24.dp.toPx() } + buttonsSize.width <= W - pad) Offset(c.right + with(d) { 24.dp.toPx() }, c.center.y - buttonsSize.height / 2f)
                // (No room beside the game: under it, or over it when it's at the bottom edge.)
                else Offset(pad, if (c.bottom + gap + buttonsSize.height <= H - gap) c.bottom + gap else c.top - gap - buttonsSize.height)
            val infoTop = titleTo.y + titleSize.height + with(d) { 14.dp.toPx() }
            val infoBottom = if (opens) buttonsTo.y - with(d) { 14.dp.toPx() }
                else if (buttonsTo.y < c.top) buttonsTo.y - with(d) { 14.dp.toPx() } else c.top - with(d) { 18.dp.toPx() }

            // ---- the cover (The row opens up only: in Row reveal the row's own card stays where it is) ----
            if (opens && cover != null) {
                AsyncImage(
                    rememberCardArtRequest(cover, ArtKind.COVER), g.name, contentScale = ContentScale.Crop,
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
                    .widthIn(max = with(d) { textW.toDp() })
            ) {
                Text(
                    titles.display(g.name), color = Color.White, fontSize = if (narrow) 22.sp else 28.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 1.1.em, style = nameTextStyle(com.shelf.launcher.data.NameBackdrop.SHADOW)
                )
                Text(
                    listOfNotNull(
                        titles.console(g.platform).takeIf { g.sourceType != com.shelf.launcher.data.SourceType.APP },
                        stylePlaytime(playMs, resumed),
                        lastAt?.let { "Last played " + android.text.format.DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), android.text.format.DateUtils.DAY_IN_MILLIS) }
                    ).joinToString("  ·  "),
                    color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
                val summary = meta?.summary?.takeIf { it.isNotBlank() }
                if (summary != null) Text(
                    summary, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 1.4.em,
                    maxLines = if (infoH > with(d) { 170.dp.toPx() }) 5 else 3, overflow = TextOverflow.Ellipsis
                )
                val facts = listOfNotNull(
                    meta?.developer?.let { "Developer" to it }, meta?.genres?.firstOrNull()?.let { "Genre" to it },
                    meta?.releaseYear?.let { "Released" to it.toString() }
                )
                if (facts.isNotEmpty()) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    facts.forEach { (h, v) ->
                        Column {
                            Text(h.uppercase(), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em, maxLines = 1)
                            Text(v, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (total > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text("Achievements  $earned / $total", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.padding(top = 5.dp).width(220.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.16f))) {
                        Box(Modifier.fillMaxWidthFraction(earned.toFloat() / total).height(5.dp).background(accent))
                    }
                }
            }

            // ---- Play / Continue and the buttons ----
            Row(
                Modifier
                    .onSizeChanged { buttonsSize = it }
                    .graphicsLayer {
                        val t = p.value
                        translationX = lerpF(buttonsFrom.x, buttonsTo.x, t)
                        translationY = lerpF(buttonsFrom.y, buttonsTo.y, t)
                        alpha = if (buttonsSize == IntSize.Zero) 0f else if (opens) part(t, 0.4f, 0.9f) else part(t, 0f, 0.35f)
                    },
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                RevealButton(if (resumed) "▶  Continue" else "▶  Play", main = true, accent = accent, onAccent = onAccent, modifier = Modifier.focusRequester(playFocus)) {
                    RowReveal.reset(); onPlay()
                }
                RevealButton("Details", accent = accent, onAccent = onAccent) { RowReveal.reset(); onFullDetails() }
                if (g.raGameId != null || g.platform == "Steam") RevealButton("Achievements", accent = accent, onAccent = onAccent) { RowReveal.reset(); onAchievements() }
                RevealButton("Options", accent = accent, onAccent = onAccent) { RowReveal.reset(); onOptions() }
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
    if (Features.winMotion.state.value == Features.WinMotion.NONE) this
    else this.graphicsLayer { alpha = RowReveal.textAlpha() }
