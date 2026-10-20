package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Update 250 (#119, approved): "Shared move" — the game window as Game Glance 3.1.1 opens its game page. The art behind
 * never moves; only its shading turns. The row of games drops a little and fades (220 ms, Game Glance's leave). The
 * row's own parts travel to their places on the page in one move (450 ms, cubic-bezier(.3, .55, .15, 1)): the category
 * name becomes the page's eyebrow, the game's name grows into the page title, its play time becomes the Played card,
 * and Play / Continue with the round buttons glide in from the name down to the page's action row. The page's own
 * parts (the other cards, the facts and the description) fade in over the last 250 ms, done as the move lands. B plays
 * the move backwards. Positions are translate + scale only, nothing is laid out again during the move.
 *
 * Its sources are the Voltcap row's name line, play time and category name (GlanceOrigin); on other layouts the name
 * starts at the game's own card (GameOrigin), and what has no source fades in where it stands.
 */

private val GlanceEase = CubicBezierEasing(0.3f, 0.55f, 0.15f, 1f)
private const val MOVE_MS = 450
private const val LEAVE_MS = 220

private fun lerpG(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun partG(p: Float, from: Float, to: Float) = ((p - from) / (to - from)).coerceIn(0f, 1f)

@Composable
internal fun GlanceWindow(
    g: Game,
    state: LauncherUiState,
    art: Map<String, GameArt>,
    onClose: () -> Unit,
    onPlay: () -> Unit,
    onFullDetails: () -> Unit,
    onNeedMedia: (Game) -> Unit,
    onOptions: () -> Unit,
    onAchievements: () -> Unit,
    /** Update 285: the game window with the game's piece out (null: an app — no Inspect button). */
    onInspect: (() -> Unit)? = null
) {
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
    val category = categoryKeyLabel(state.sortCategoryKey)

    // Where the parts start (taken once as it opens). The Voltcap row's own name line when it was drawn just now; else
    // just above the game's own card on this screen.
    val view = LocalView.current
    val fromRow = remember(k) { android.os.SystemClock.uptimeMillis() - GlanceOrigin.at < 4000L &&
        state.appearance.homeLayout == com.shelf.launcher.data.HomeLayout.CLASSIC }
    val sources = remember(k) {
        val fresh = android.os.SystemClock.uptimeMillis() - GlanceOrigin.at < 4000L &&
            state.appearance.homeLayout == com.shelf.launcher.data.HomeLayout.CLASSIC
        val here = runCatching { view.display?.displayId }.getOrNull() ?: -1
        val card = GameOrigin.rect?.takeIf { (GameOrigin.key == k || GameOrigin.key == GameOrigin.FOCUSED) && (GameOrigin.displayId == here || GameOrigin.displayId < 0) }
        Triple(
            if (fresh) GlanceOrigin.name else card?.let { Rect(it.left, it.top - 30f, it.right, it.top - 6f) },
            if (fresh) GlanceOrigin.chip else null,
            if (fresh) GlanceOrigin.category else null
        )
    }
    val (srcName0, srcChip0, srcCat0) = sources

    // The move (0 = Home, 1 = the page) and the row's leave.
    val p = remember(k) { Animatable(0f) }
    val leave = remember(k) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val closeNow by rememberUpdatedState(onClose)
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                if (anim) {
                    launch { delay((MOVE_MS - LEAVE_MS).toLong()); leave.animateTo(0f, tween(LEAVE_MS, easing = LinearEasing)) }
                    p.animateTo(0f, tween(MOVE_MS, easing = GlanceEase))
                    if (leave.value > 0f) leave.animateTo(0f, tween(60))
                } else { p.snapTo(0f); leave.snapTo(0f) }
                RowReveal.reset()
                closeNow()
            }
        }
    }
    LaunchedEffect(leave) {
        androidx.compose.runtime.snapshotFlow { leave.value }.collect { v ->
            RowReveal.rowAll.value = true
            RowReveal.others.floatValue = v
            RowReveal.drop.floatValue = v
        }
    }
    DisposableEffect(k) { onDispose { RowReveal.reset() } }
    LaunchedEffect(k) {
        onNeedMedia(g)
        if (anim) {
            launch { leave.animateTo(1f, tween(LEAVE_MS, easing = LinearEasing)) }
            p.animateTo(1f, tween(MOVE_MS, easing = GlanceEase))
        } else { leave.snapTo(1f); p.snapTo(1f) }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = close,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        ImmersiveDialogWindow()
        val dlgView = LocalView.current
        DisposableEffect(dlgView) {
            (dlgView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w ->
                runCatching { w.setDimAmount(0f); w.setWindowAnimations(0) }
            }
            // The row's name line and the category hide the moment this window draws its own (same frame as possible).
            RowReveal.sharedHidden.value = true
            onDispose { }
        }
        val playFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(20); KeyInput.leaveTouchMode(dlgView)
            for (wait in longArrayOf(60, 140, 250, 400)) { delay(wait); runCatching { playFocus.requestFocus() } }
        }
        val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
        LaunchedEffect(windowInfo.isWindowFocused) {
            if (windowInfo.isWindowFocused) { delay(30); runCatching { playFocus.requestFocus() } }
        }
        val d = LocalDensity.current
        // The window's own corner on the screen: the row's places are kept in screen terms (GlanceOrigin.onScreen).
        var rootAt by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { rootAt = it.positionOnScreen() }.pointerInput(Unit) { detectTapGestures { close() } }) {
            val shift = rootAt ?: androidx.compose.ui.geometry.Offset.Zero
            val srcName = srcName0?.let { if (fromRow) it.translate(-shift.x, -shift.y) else it }
            val srcChip = srcChip0?.translate(-shift.x, -shift.y)
            val srcCat = srcCat0?.translate(-shift.x, -shift.y)
            val W = constraints.maxWidth.toFloat()
            val H = constraints.maxHeight.toFloat()
            val narrow = W < with(d) { 700.dp.toPx() }
            val pad = with(d) { (if (narrow) 22.dp else 34.dp).toPx() }
            val topY = maxOf(with(d) { 40.dp.toPx() }, H * 0.075f)
            val gap = with(d) { 16.dp.toPx() }

            // ---- the shading: from the bottom (Home's) to the left side (the page's) ----
            Canvas(Modifier.fillMaxSize()) {
                val t = p.value
                drawRect(Brush.horizontalGradient(
                    listOf(Color.Black.copy(alpha = 0.86f * t), Color.Black.copy(alpha = 0.62f * t), Color.Black.copy(alpha = 0.12f * t)),
                    startX = 0f, endX = size.width
                ))
                drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f * t), Color.Transparent), endY = size.height * 0.3f))
            }

            // Sizes measured once laid out (translate + scale only during the move).
            var eyebrowSize by remember { mutableStateOf(IntSize.Zero) }
            var titleSize by remember { mutableStateOf(IntSize.Zero) }
            var cardsSize by remember { mutableStateOf(IntSize.Zero) }
            var actionsSize by remember { mutableStateOf(IntSize.Zero) }
            val iconPx = with(d) { (if (narrow) 40.dp else 50.dp).toPx() }
            val titleSp = if (narrow) 26f else 34f
            val eyebrowY = topY
            val titleY = eyebrowY + eyebrowSize.height + with(d) { 6.dp.toPx() }
            val cardsY = titleY + maxOf(titleSize.height.toFloat(), iconPx) + gap
            val actionsY = cardsY + cardsSize.height + gap
            val restY = actionsY + actionsSize.height + gap
            val nameX = pad + iconPx + with(d) { 14.dp.toPx() }
            // The title's start: the row's name, scaled from its own text size (15 sp) to the title's.
            val nameScale0 = 15f / titleSp
            val ready = titleSize != IntSize.Zero && cardsSize != IntSize.Zero && rootAt != null

            // ---- the eyebrow (from the category name) ----
            Text(
                listOfNotNull(category.uppercase(), lastAt?.let { if (android.text.format.DateUtils.isToday(it)) "TODAY" else null }).joinToString("  ·  "),
                color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.14.em, maxLines = 1,
                modifier = Modifier
                    .onSizeChanged { eyebrowSize = it }
                    .graphicsLayer {
                        val t = p.value
                        transformOrigin = TransformOrigin(0f, 0f)
                        val s0 = srcCat?.let { (it.height / eyebrowSize.height.coerceAtLeast(1)).coerceIn(0.6f, 2.2f) } ?: 1f
                        val s = lerpG(s0, 1f, t)
                        scaleX = s; scaleY = s
                        translationX = lerpG(srcCat?.left ?: pad, pad, t)
                        translationY = lerpG(srcCat?.top ?: (eyebrowY + gap), eyebrowY, t)
                        alpha = if (!ready) 0f else if (srcCat != null) 1f else partG(t, 0.3f, 1f)
                    }
            )

            // ---- the cover icon beside the title ----
            if (cover != null) AsyncImage(
                rememberCardArtRequest(cover, ArtKind.COVER), g.name, contentScale = ContentScale.Crop,
                modifier = Modifier
                    .graphicsLayer {
                        val t = p.value
                        translationX = pad; translationY = titleY + (maxOf(titleSize.height.toFloat(), iconPx) - iconPx) / 2f
                        val s = lerpG(0.6f, 1f, t); scaleX = s; scaleY = s
                        alpha = if (!ready) 0f else partG(t, 0.25f, 0.8f)
                        shape = RoundedCornerShape(8.dp); clip = true
                    }
                    .size(with(d) { iconPx.toDp() })
            )

            // ---- the title (from the row's name) ----
            Text(
                titles.display(g.name), color = Color.White, fontSize = titleSp.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 1.1.em, style = nameTextStyle(com.shelf.launcher.data.NameBackdrop.SHADOW),
                modifier = Modifier
                    .onSizeChanged { titleSize = it }
                    .widthIn(max = with(d) { (W - nameX - pad).coerceAtLeast(120f).toDp() })
                    .graphicsLayer {
                        val t = p.value
                        transformOrigin = TransformOrigin(0f, 0f)
                        val s = lerpG(if (srcName != null) nameScale0 else 0.9f, 1f, t)
                        scaleX = s; scaleY = s
                        val ty = titleY + (maxOf(titleSize.height.toFloat(), iconPx) - titleSize.height) / 2f
                        translationX = lerpG(srcName?.left ?: nameX, nameX, t)
                        translationY = lerpG(srcName?.top ?: (ty + gap), ty, t)
                        alpha = if (!ready) 0f else if (srcName != null) 1f else partG(t, 0f, 0.6f)
                    }
            )

            // ---- the stat cards (Played from the play time; the others fade in where they stand) ----
            val ttb = meta?.normallyHours ?: meta?.hastilyHours
            val score = meta?.metacritic ?: meta?.criticScore
            val cards = buildList {
                add("PLAYED" to stylePlaytime(playMs, resumed))
                if (total > 0) add("ACHIEVEMENTS" to "$earned / $total")
                add("LAST PLAYED" to (lastAt?.let { android.text.format.DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), android.text.format.DateUtils.DAY_IN_MILLIS).toString() } ?: "Never"))
                if (ttb != null && ttb > 0.0) add("TIME TO BEAT" to "${ttb.toInt().coerceAtLeast(1)} h")
                if (score != null && score > 0) add("SCORE" to score.toString())
            }
            Row(
                Modifier
                    .onSizeChanged { cardsSize = it }
                    .graphicsLayer { translationX = pad; translationY = cardsY },
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                cards.forEachIndexed { i, (head, value) ->
                    var cardPos by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                    var cardSize by remember { mutableStateOf(IntSize.Zero) }
                    Column(
                        Modifier
                            .onSizeChanged { cardSize = it }
                            .onGloballyPositioned { cardPos = it.positionInWindow() }
                            .graphicsLayer {
                                val t = p.value
                                if (i == 0 && srcChip != null && cardSize != IntSize.Zero) {
                                    // The Played card grows out of the play time by the name.
                                    transformOrigin = TransformOrigin(0f, 0f)
                                    val sx = lerpG(srcChip.width / cardSize.width, 1f, t)
                                    val sy = lerpG(srcChip.height / cardSize.height, 1f, t)
                                    scaleX = sx; scaleY = sy
                                    translationX = (srcChip.left - cardPos.x) * (1f - t)
                                    translationY = (srcChip.top - cardPos.y) * (1f - t)
                                    alpha = if (!ready) 0f else partG(t, 0f, 0.35f)
                                } else alpha = if (!ready) 0f else partG(t, 0.55f, 1f)
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.09f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .widthIn(min = if (narrow) 84.dp else 104.dp)
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(head, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em, maxLines = 1)
                        Text(value, color = Color.White, fontSize = if (narrow) 15.sp else 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        if (head == "ACHIEVEMENTS" && total > 0) Box(Modifier.padding(top = 5.dp).width(70.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.16f))) {
                            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = earned.toFloat() / total; transformOrigin = TransformOrigin(0f, 0f) }.background(accent))
                        }
                    }
                }
            }

            // ---- Play / Continue and the round buttons (from the name down to the action row) ----
            Row(
                Modifier
                    .onSizeChanged { actionsSize = it }
                    .graphicsLayer {
                        val t = p.value
                        translationX = lerpG(srcName?.left ?: pad, pad, t)
                        translationY = lerpG(srcName?.bottom ?: (actionsY - gap), actionsY, t)
                        alpha = if (!ready) 0f else partG(t, 0.3f, 0.8f)
                    },
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                GlancePill(if (resumed) "Continue" else "Play", accent, onAccent, Modifier.focusRequester(playFocus)) { RowReveal.reset(); onPlay() }
                GlanceRound(Icons.Filled.Info, "Details", accent) { RowReveal.reset(); onFullDetails() }
                if (g.raGameId != null || g.platform == "Steam") GlanceRound(Icons.Filled.EmojiEvents, "Achievements", accent) { RowReveal.reset(); onAchievements() }
                if (onInspect != null) GlanceRound(InspectIcon.vector, "Inspect", accent) { RowReveal.reset(); onInspect() }
                GlanceRound(Icons.Filled.MoreHoriz, "Options", accent) { RowReveal.reset(); onOptions() }
            }

            // ---- the facts and the description (the page's own: in over the last 250 ms) ----
            Column(
                Modifier
                    .graphicsLayer {
                        // (The move eases out fast, so "the last 250 ms" is the last part of its travel, not of its time.)
                        val t = partG(p.value, 0.72f, 1f)
                        translationX = pad; translationY = restY + with(d) { 10.dp.toPx() } * (1f - t)
                        alpha = if (!ready) 0f else t
                    }
                    .width(with(d) { minOf(W - 2 * pad, with(d) { 720.dp.toPx() }).toDp() })
            ) {
                val facts = listOfNotNull(
                    titles.console(g.platform).takeIf { g.sourceType != com.shelf.launcher.data.SourceType.APP },
                    meta?.releaseYear?.toString(), meta?.developer, meta?.genres?.firstOrNull()
                )
                if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                meta?.summary?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(8.dp))
                    val room = H - restY - with(d) { 40.dp.toPx() }
                    Text(
                        it, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 1.45.em,
                        maxLines = (room / with(d) { 19.dp.toPx() }).toInt().coerceIn(1, 5), overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun GlancePill(label: String, accent: Color, onAccent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    LaunchedEffect(focused) { if (focused) sounds?.play(UiSound.MOVE) }
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(if (focused) accent else Color.White)
            .border(2.dp, if (focused) Color.White else Color.Transparent, shape)
            .clickable(interactionSource = source, indication = null) { sounds?.play(UiSound.SELECT); onClick() }
            .padding(horizontal = 26.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("▶  $label", color = if (focused) onAccent else Color(0xFF111111), fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun GlanceRound(icon: ImageVector, label: String, accent: Color, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    LaunchedEffect(focused) { if (focused) sounds?.play(UiSound.MOVE) }
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(if (focused) accent else Color.White.copy(alpha = 0.14f))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(interactionSource = source, indication = null) { sounds?.play(UiSound.SELECT); onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp)) }
}
