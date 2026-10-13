package com.shelf.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.GameArt
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.NameBackdrop
import com.shelf.launcher.data.RA_DISPLAY_ORDER
import com.shelf.launcher.data.RaAchievementDetail
import com.shelf.launcher.data.RaBadges

/*
 * Update 127: on the Cross menu a game's achievements open in the menu's own style — like a console's trophy list.
 * The game you're on sits on the left (the ones before and after it faded beside it, the category's icon above it),
 * the achievements run down a column next to it, the one you're on bigger with what it asks and when you earned it,
 * and a box top right holds earned / total and the time.
 * ▲ ▼ move · L1 / R1 (L2 / R2) a page · X all / missable / still to get / earned · ▶ comments · A the full list · B or ◀ back.
 * (Update 182: the Missable filter and the comments panel.)
 * Settings › Home › Layout options › Cross menu options › "Achievements in the menu's style" turns it off (the list popup then).
 */

/** Whether a game's achievements open as the Cross menu's own column on [layout]. */
internal fun crossTrophiesOn(layout: HomeLayout): Boolean = layout == HomeLayout.CROSS && Features.crossTrophies.value

/** The full list popup shown instead of the column (A in it); B there comes back to the column. */
internal object CrossTrophyView {
    var full by mutableStateOf(false)
}

private enum class TrophyFilter(val label: String) { ALL("All"), MISSABLE("Missable"), LOCKED("Still to get"), EARNED("Earned") }

@Composable
internal fun CrossTrophies(
    game: Game,
    state: LauncherUiState,
    games: List<Game>,
    art: Map<String, GameArt>,
    loading: Boolean,
    startLocked: Boolean,
    onFull: () -> Unit,
    onDismiss: () -> Unit,
    onLoadComments: (Int) -> Unit = {}
) {
    val sounds = LocalUiSounds.current
    val details = state.raAchievementDetails[game.stableKey]
    val all = remember(details) { details.orEmpty().sortedWith(RA_DISPLAY_ORDER) }
    var filter by remember(game.stableKey) { mutableStateOf(if (startLocked) TrophyFilter.LOCKED else TrophyFilter.ALL) }
    val list = remember(all, filter) {
        when (filter) {
            TrophyFilter.ALL -> all
            TrophyFilter.MISSABLE -> all.filter { it.isMissable }
            TrophyFilter.LOCKED -> all.filter { !it.isUnlocked }
            TrophyFilter.EARNED -> all.filter { it.isUnlocked }
        }
    }
    var index by remember(game.stableKey) { mutableIntStateOf(0) }
    val sel = index.coerceIn(0, (list.size - 1).coerceAtLeast(0))
    // Read when a key or a drag comes in (the gesture handlers outlive this composition).
    val listNow by rememberUpdatedState(list)
    val close by rememberUpdatedState(onDismiss)
    val full by rememberUpdatedState(onFull)
    // Keys pressed before the column opened (the Y that opened it, let go in here) do nothing.
    val openedAt = remember { android.os.SystemClock.uptimeMillis() }
    // Update 182: ▶ opens the comments people left on the achievement you're on; ▲ ▼ read on, ◀ / B close them.
    var commentsFor by remember(game.stableKey) { mutableStateOf<RaAchievementDetail?>(null) }
    val commentScroll = androidx.compose.foundation.rememberScrollState()
    val loadComments by rememberUpdatedState(onLoadComments)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun openComments() {
        val a = listNow.getOrNull(index.coerceIn(0, (listNow.size - 1).coerceAtLeast(0))) ?: return
        commentsFor = a
        loadComments(a.id)
        scope.launch { commentScroll.scrollTo(0) }
        sounds?.play(UiSound.OPEN)
    }
    fun move(d: Int) {
        val l = listNow
        if (l.isEmpty()) return
        val cur = index.coerceIn(0, l.size - 1)
        val next = (cur + d).coerceIn(0, l.size - 1)
        if (next != cur) {
            index = next
            sounds?.play(UiSound.MOVE)
        }
    }
    fun back() {
        sounds?.play(UiSound.BACK)
        close()
    }

    Dialog(
        onDismissRequest = { back() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        ImmersiveDialogWindow()
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            for (wait in longArrayOf(30, 120, 300)) {
                kotlinx.coroutines.delay(wait)
                runCatching { focus.requestFocus() }
            }
        }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(20_000)
                now = System.currentTimeMillis()
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { e ->
                    if (e.nativeKeyEvent.downTime < openedAt) return@onKeyEvent true
                    val down = e.type == KeyEventType.KeyDown
                    val up = e.type == KeyEventType.KeyUp
                    if (commentsFor != null) when (e.key) {
                        Key.DirectionUp -> { if (down) scope.launch { commentScroll.animateScrollBy(-180f) }; true }
                        Key.DirectionDown -> { if (down) scope.launch { commentScroll.animateScrollBy(180f) }; true }
                        Key.DirectionLeft, Key.ButtonB, Key.Back, Key.Escape, Key.DirectionRight, Key.ButtonA, Key.Enter, Key.DirectionCenter -> {
                            if (up) { commentsFor = null; sounds?.play(UiSound.BACK) }
                            true
                        }
                        else -> true
                    } else when (e.key) {
                        Key.DirectionUp -> { if (down) move(-1); true }
                        Key.DirectionDown -> { if (down) move(1); true }
                        Key.ButtonL1, Key.ButtonL2, Key.PageUp -> { if (down) move(-8); true }
                        Key.ButtonR1, Key.ButtonR2, Key.PageDown -> { if (down) move(8); true }
                        Key.DirectionLeft, Key.ButtonB, Key.Back, Key.Escape, Key.ButtonY -> { if (up) back(); true }
                        Key.ButtonX -> {
                            if (up) {
                                filter = TrophyFilter.values()[(filter.ordinal + 1) % TrophyFilter.values().size]
                                index = 0
                                sounds?.play(UiSound.SELECT)
                            }
                            true
                        }
                        Key.ButtonA, Key.Enter, Key.DirectionCenter -> {
                            if (up) { sounds?.play(UiSound.OPEN); full() }
                            true
                        }
                        Key.DirectionRight -> { if (up) openComments(); true }
                        else -> false
                    }
                }
        ) {
            // The menu's own colour wash and ribbons behind (with the wash off, the home screen shows through darkened).
            if (Features.crossTint.state.value == Features.CrossTint.NONE) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)))
            CrossBackdrop()
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val compact = maxHeight < 380.dp
                val k = Features.crossScale.state.value / 100f * (if (compact) 0.8f else 1f)
                val w = maxWidth
                val h = maxHeight
                val anchor = h * 0.4f
                val rowH = 36.dp * k
                val iconS = 28.dp * k
                val iconB = 54.dp * k
                val focusH = 82.dp * k
                val gap = 8.dp * k
                val listX = (w * 0.28f).coerceAtLeast(150.dp * k)
                val textW = (w - listX - iconB - 16.dp * k - 24.dp).coerceAtLeast(120.dp)

                // Touch: drag anywhere to move along the column, tap the left side (the game) to go back.
                var drag by remember { mutableFloatStateOf(0f) }
                val stepPx = with(androidx.compose.ui.platform.LocalDensity.current) { rowH.toPx() }
                val backX = with(androidx.compose.ui.platform.LocalDensity.current) { (listX - 8.dp).toPx() }
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(stepPx) {
                            detectVerticalDragGestures(onDragEnd = { drag = 0f }, onDragCancel = { drag = 0f }) { _, dy ->
                                drag += dy
                                while (drag <= -stepPx) { drag += stepPx; move(1) }
                                while (drag >= stepPx) { drag -= stepPx; move(-1) }
                            }
                        }
                        .pointerInput(backX) {
                            detectTapGestures { p -> if (p.x < backX) back() }
                        }
                ) {
                CrossTrophyGames(game, state, games, art, listX, anchor + focusH / 2, k)

                val pos by animateFloatAsState(sel.toFloat(), tween(140), label = "crossTrophy")
                if (list.isEmpty()) {
                    Text(
                        when {
                            loading && all.isEmpty() -> "Loading achievements…"
                            all.isEmpty() -> "No achievements for this game"
                            else -> "None here · X: " + TrophyFilter.values()[(filter.ordinal + 1) % TrophyFilter.values().size].label
                        },
                        color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp * k, style = nameTextStyle(NameBackdrop.SHADOW),
                        modifier = Modifier.offset(x = listX, y = anchor + focusH / 2 - 10.dp * k)
                    )
                } else {
                    val above = (anchor / rowH).toInt() + 2
                    val below = ((h - anchor) / rowH).toInt() + 2
                    val from = (pos.toInt() - above).coerceAtLeast(0)
                    val to = (pos.toInt() + 1 + below).coerceAtMost(list.lastIndex)
                    for (i in from..to) androidx.compose.runtime.key(list[i].id) {
                        val rel = i - pos
                        val y = when {
                            rel >= 1f -> anchor + focusH + rowH * (rel - 1f)
                            rel >= 0f -> anchor + focusH * rel
                            rel >= -1f -> anchor - (rowH + gap) * -rel
                            else -> anchor - rowH - gap - rowH * (-rel - 1f)
                        }
                        // (Drawn only while on screen; no early return out of key { } — it breaks the Android build.)
                        if (y > -focusH && y < h) {
                            val near = (1f - kotlin.math.abs(rel)).coerceIn(0f, 1f)
                            TrophyRow(
                                list[i], on = i == sel, near = near, k = k,
                                modifier = Modifier.offset(x = listX, y = y).height(lerp(rowH, focusH, near))
                                    .alpha(if (rel < -0.3f) 0.5f else 1f)
                                    .pointerInput(i, sel) { detectTapGestures { if (i == sel) full() else { index = i; sounds?.play(UiSound.MOVE) } } },
                                iconSize = lerp(iconS, iconB, near), iconBox = iconB, textW = textW
                            )
                        }
                    }
                }

                CrossTrophyBox(all, filter, now, k, Modifier.align(Alignment.TopEnd).padding(top = 14.dp * k))
                Text(
                    if (commentsFor != null) "▲▼ Read   B Close" else
                        "▲▼ Move   X " + TrophyFilter.values()[(filter.ordinal + 1) % TrophyFilter.values().size].label + "   ▶ Comments   A Full list   B Back",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp * k, maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 10.dp)
                )
                commentsFor?.let { a ->
                    CrossComments(
                        a, state.raComments[a.id], a.id in state.raCommentsLoading, commentScroll, k,
                        Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
                            .width((w * 0.46f).coerceIn(220.dp, 420.dp)).height(h * 0.78f)
                            .pointerInput(a.id) { detectTapGestures { commentsFor = null } }
                    )
                }
                }
            }
        }
    }
}

/** One achievement in the column: its badge, a small cup in its tier's colour, its name; the one you're on also what it asks. */
@Composable
private fun TrophyRow(
    a: RaAchievementDetail, on: Boolean, near: Float, k: Float, modifier: Modifier,
    iconSize: Dp, iconBox: Dp, textW: Dp
) {
    val earned = a.isUnlocked
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(iconBox), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(iconSize)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(if (on) 2.dp else 1.dp, Color.White.copy(alpha = if (on) 0.95f else 0.45f), RoundedCornerShape(3.dp))
            ) {
                RaBadges.url(a.badgeName)?.let { url ->
                    AsyncImage(
                        url, a.title, contentScale = ContentScale.Crop,
                        colorFilter = if (earned) null else GreyMatrix, alpha = if (earned) 1f else 0.6f,
                        modifier = Modifier.fillMaxSize().padding(if (on) 2.dp else 1.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(16.dp * k))
        Column(Modifier.width(textW)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.EmojiEvents, null, tint = trophyTier(a).copy(alpha = if (earned) 1f else 0.45f),
                    modifier = Modifier.size((10f + 3f * near).dp * k)
                )
                Spacer(Modifier.width(5.dp * k))
                Text(
                    a.title ?: "Achievement", color = Color.White.copy(alpha = if (earned) 1f else 0.78f),
                    fontSize = (13f + 4f * near).sp * k, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = nameTextStyle(NameBackdrop.SHADOW)
                )
            }
            if (on) {
                a.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp * k, lineHeight = 14.sp * k,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, style = nameTextStyle(NameBackdrop.SHADOW),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    trophyMeta(a), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp * k, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** The game on the left: the one you're on with ◀ beside it, the ones before and after it faded, the category's icon above. */
@Composable
private fun CrossTrophyGames(game: Game, state: LauncherUiState, games: List<Game>, art: Map<String, GameArt>, listX: Dp, centerY: Dp, k: Float) {
    val gw = 76.dp * k
    val gh = gw * 0.47f
    val gx = (listX - gw - 34.dp * k).coerceAtLeast(10.dp)
    val gy = centerY - gh / 2
    val small = gw * 0.6f
    val smallH = small * 0.47f
    val sx = gx + gw * 0.78f
    val at = games.indexOfFirst { it.stableKey == game.stableKey }
    fun wide(g: Game): Any? {
        val o = state.artOverrides[g.stableKey]
        val a = art[g.stableKey]
        return o?.wide ?: a?.wideGridUrl ?: o?.hero ?: a?.heroUrl ?: o?.cover ?: a?.gridUrl
    }
    @Composable
    fun thumb(g: Game, x: Dp, y: Dp, width: Dp, height: Dp, alpha: Float) {
        Box(
            Modifier.offset(x = x, y = y).size(width, height).alpha(alpha)
                .clip(RoundedCornerShape(2.dp)).background(placeholderBrush(g.hue))
        ) {
            wide(g)?.let { AsyncImage(it, g.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
    }
    if (at >= 0) {
        for (j in 1..3) games.getOrNull(at - j)?.let { thumb(it, sx, gy - 10.dp * k - (smallH + 9.dp * k) * j + (gh - smallH), small, smallH, 0.32f) }
        for (j in 1..5) games.getOrNull(at + j)?.let { thumb(it, sx, gy + gh + 10.dp * k + (smallH + 9.dp * k) * (j - 1), small, smallH, 0.32f) }
    }
    thumb(game, gx, gy, gw, gh, 1f)
    Text(
        "◀", color = Color.White, fontSize = 11.sp * k,
        modifier = Modifier.offset(x = gx + gw + 10.dp * k, y = centerY - 8.dp * k)
    )
    // The category's icon above the game, as on the menu.
    val titles = LocalTitleOptions.current
    val stop = remember(state.categoryOrder, state.availablePlatforms, state.collections, state.specialFilter, state.platformFilter, titles) {
        val (stops, cur) = styleStops(state, titles)
        stops.getOrNull(cur)
    }
    if (stop != null) {
        val cs = 34.dp * k
        Box(Modifier.offset(x = gx + gw / 2 - cs / 2, y = gy - cs - 30.dp * k).size(cs), contentAlignment = Alignment.Center) {
            CrossIconTile(stop, cs)
        }
    }
}

/** Top right: earned / total with a cup, and the date and time, in a thin outlined box. */
@Composable
private fun CrossTrophyBox(all: List<RaAchievementDetail>, filter: TrophyFilter, now: Long, k: Float, modifier: Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val clock = remember(now) {
        val pattern = if (android.text.format.DateFormat.is24HourFormat(ctx)) "dd/MM  HH:mm" else "dd/MM  h:mm a"
        java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date(now))
    }
    val shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
    Column(modifier, horizontalAlignment = Alignment.End) {
        Row(
            Modifier.clip(shape).background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.55f), shape)
                .padding(horizontal = 12.dp * k, vertical = 5.dp * k),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (all.isNotEmpty()) {
                Icon(Icons.Filled.EmojiEvents, null, tint = Color.White, modifier = Modifier.size(13.dp * k))
                Spacer(Modifier.width(4.dp))
                val earned = all.count { it.isUnlocked }
                Text("$earned / ${all.size}  ·  ${earned * 100 / all.size} %", color = Color.White, fontSize = 12.sp * k, maxLines = 1)
                Spacer(Modifier.width(16.dp * k))
            }
            Text(clock, color = Color.White, fontSize = 12.sp * k, maxLines = 1)
        }
        if (filter != TrophyFilter.ALL) {
            Text(
                filter.label, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp * k, style = nameTextStyle(NameBackdrop.SHADOW),
                modifier = Modifier.padding(top = 4.dp, end = 12.dp)
            )
        }
    }
}

/** Bronze, silver or gold by points (the set's last one, "win condition", gold too). */
private fun trophyTier(a: RaAchievementDetail): Color {
    val pts = a.points ?: 0
    return when {
        pts >= 25 || a.type.equals("win_condition", ignoreCase = true) -> Color(0xFFE6C24F)
        pts >= 10 -> Color(0xFFCDD2DA)
        else -> Color(0xFFC98650)
    }
}

/** "10 points · Earned 18/5/2026 · 23 % of players" (or "Not earned yet"). */
private fun trophyMeta(a: RaAchievementDetail): String {
    val raw = a.dateEarnedHardcore ?: a.dateEarned
    val earned = raw?.let {
        runCatching {
            // Update 144: RetroAchievements dates are UTC (else the day can be wrong near midnight).
            val d = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(it.trim())
            "Earned " + (if (d != null) java.text.SimpleDateFormat("d/M/yyyy", java.util.Locale.getDefault()).format(d) else it.take(10))
        }.getOrDefault("Earned " + it.take(10))
    } ?: "Not earned yet"
    return listOfNotNull(
        a.points?.let { "$it points" },
        earned,
        a.unlockPercent?.let { "${it.toInt().coerceAtLeast(if (it > 0f) 1 else 0)} % of players" }
    ).joinToString("  ·  ")
}

/** Update 182: the comments people left on an achievement's page, in a dark panel over the right of the column. */
@Composable
private fun CrossComments(
    a: RaAchievementDetail, comments: List<com.shelf.launcher.data.RaComment>?, loading: Boolean,
    scroll: androidx.compose.foundation.ScrollState, k: Float, modifier: Modifier
) {
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier.clip(shape).background(Color.Black.copy(alpha = 0.78f)).border(1.dp, Color.White.copy(alpha = 0.45f), shape)
            .padding(horizontal = 14.dp * k, vertical = 10.dp * k)
    ) {
        Text(
            a.title ?: "Achievement", color = Color.White, fontSize = 14.sp * k, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            if (a.isMissable) "Comments  ·  Missable" else "Comments", color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp * k, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
        )
        Column(Modifier.weight(1f).verticalScroll(scroll)) {
            when {
                comments == null && loading -> Text("Loading comments…", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp * k)
                comments == null -> Text(
                    "Couldn't load the comments (no connection, or no RetroAchievements sign-in). Close and open them again to retry.",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp * k
                )
                comments.isEmpty() -> Text("No comments yet.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp * k)
                else -> comments.forEach { cm ->
                    Text(
                        (cm.User ?: "Player") + (cm.Submitted?.let { "  ·  " + it.take(10) } ?: ""),
                        color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp * k, fontWeight = FontWeight.Medium, maxLines = 1,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        cm.CommentText.orEmpty().trim(), color = Color.White.copy(alpha = 0.92f),
                        fontSize = 12.sp * k, lineHeight = 16.sp * k
                    )
                }
            }
        }
    }
}
