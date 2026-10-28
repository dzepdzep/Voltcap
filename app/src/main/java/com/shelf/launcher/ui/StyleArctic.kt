package com.shelf.launcher.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Next version (Toni: "Arctic Hub — Kodi Arctic Fuse 2-inspired"): a media-centre home of Voltcap's own.
 * - A navigation rail down the left: your categories (and Settings) as icons; go to it (◀ from the first tile) and it
 *   folds open with their names, the category you're in marked. A or ▶ opens one.
 * - The game you're on fills the room behind as fanart (sharp, blurred or none), with its clear logo or name at the top,
 *   an info line of small icons (year, console, genre, time played, achievements) and its plot rolling when it's long.
 * - Widgets below it: Spotlight (wide art) then rows of posters — Continue playing, the category, Favourites, Most played,
 *   Not played yet (Layout options). ▲ ▼ between widgets, ◀ ▶ along. A opens the Arctic info drawer (WinScenesHomes.kt).
 */

internal object ArcticNav {
    @Volatile var ctx: StyleCtx? = null
    @Volatile var rows: List<HomeRow> = emptyList()
    val cursor = RowCursor()
    /** On the rail (folded open). */
    var nav by mutableStateOf(false)
    var navSel by mutableIntStateOf(0)
    var tick by mutableIntStateOf(0)
    internal val keysDown = HashSet<Int>()
}

/** The rail's entries: the category stops, then Settings. */
private fun arcticNavCount(c: StyleCtx) = c.stops.size + 1

internal fun arcticKey(event: KeyEvent, reach: (Boolean) -> Boolean): Boolean {
    val c = ArcticNav.ctx ?: return false
    val code = event.nativeKeyEvent.keyCode
    val down = event.type == KeyEventType.KeyDown
    val up = event.type == KeyEventType.KeyUp
    if (up && ArcticNav.keysDown.remove(code)) return true
    val k = event.key
    val isA = k == Key.ButtonA || k == Key.Enter || k == Key.DirectionCenter
    fun take(): Boolean { ArcticNav.keysDown += code; return true }
    val rows = ArcticNav.rows
    val select: (Int) -> Unit = { i -> c.actions.select(i); ArcticNav.tick++ }
    if (ArcticNav.nav) {
        val n = arcticNavCount(c)
        fun pick() {
            val i = ArcticNav.navSel
            if (i >= c.stops.size) c.actions.settings()
            else if (c.stop >= 0 && i != c.stop) c.actions.category(i - c.stop)
            ArcticNav.nav = false; ArcticNav.cursor.row = 0; ArcticNav.cursor.cols.clear(); ArcticNav.tick++
        }
        if (isA) { if (up) pick(); return true }
        if (!down) return false
        val first = event.nativeKeyEvent.repeatCount == 0
        when (k) {
            Key.DirectionUp -> { ArcticNav.navSel = (ArcticNav.navSel - 1).coerceAtLeast(0); return take() }
            Key.DirectionDown -> { ArcticNav.navSel = (ArcticNav.navSel + 1).coerceAtMost(n - 1); return take() }
            Key.DirectionRight, Key.ButtonB, Key.Back -> { if (first) ArcticNav.nav = false; return take() }
            Key.DirectionLeft -> return take()
            else -> return false
        }
    }
    if (!down) return false
    val first = event.nativeKeyEvent.repeatCount == 0
    ArcticNav.cursor.follow(rows, c.focus)
    when (k) {
        Key.DirectionLeft -> {
            if (!ArcticNav.cursor.along(rows, -1, select) && first) { ArcticNav.navSel = c.stop.coerceAtLeast(0); ArcticNav.nav = true }
            return take()
        }
        Key.DirectionRight -> { if (!ArcticNav.cursor.along(rows, 1, select) && first) EdgeBump.hit(1); return take() }
        Key.DirectionUp -> { if (!ArcticNav.cursor.across(rows, -1, select) && first) reach(true); return take() }
        Key.DirectionDown -> { if (!ArcticNav.cursor.across(rows, 1, select) && first) reach(false); return take() }
        else -> return if (rows.isEmpty() && (isA || k == Key.ButtonX || k == Key.ButtonY)) take() else false
    }
}

private val ArcticSlate = Color(0xFF0D141D)

/** Arctic hub's room when there's no fanart: deep slate. */
@Composable
internal fun ArcticBackdrop(modifier: Modifier) {
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF15202D), ArcticSlate, Color(0xFF080C12)))))
}

@Composable
internal fun StyleArctic(c: StyleCtx, modifier: Modifier = Modifier) {
    val switches = HomeRowSwitches(
        Features.arRowContinue.state.value, Features.arRowFav.state.value, Features.arRowMost.state.value, Features.arRowNew.state.value
    )
    val rows = remember(c.games, c.state.lastPlayedAt, c.state.favorites, c.state.playtime, c.categoryLabel, switches.continuePlaying, switches.favourites, switches.mostPlayed, switches.notPlayed) {
        homeRows(c, switches)
    }
    SideEffect {
        ArcticNav.ctx = c; ArcticNav.rows = rows
        ArcticNav.cursor.follow(rows, c.focus)
        StyleNav.side = emptyList(); StyleNav.top = emptyList(); StyleNav.bar = emptyList()
    }
    DisposableEffect(Unit) { onDispose { ArcticNav.keysDown.clear(); ArcticNav.nav = false } }
    androidx.activity.compose.BackHandler(enabled = ArcticNav.nav && GamePopup.game == null) { ArcticNav.nav = false }
    val g = c.game
    LaunchedEffect(g?.stableKey) { val gg = g ?: return@LaunchedEffect; kotlinx.coroutines.delay(250); c.actions.needMetadata(gg) }
    val accent = ShelfTheme.accent
    val navMode = Features.arNav.state.value
    val railOpen = navMode == Features.ArNav.OPEN || (navMode == Features.ArNav.FOLD && ArcticNav.nav)
    val railW by animateDpAsState(if (railOpen) 230.dp else 68.dp, tween(260, easing = FastOutSlowInEasing), label = "arRail")
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth; val h = maxHeight
        // The fanart behind everything, washed to the left and bottom so the text reads.
        val art = Features.arArt.state.value
        if (g != null && art != Features.ArArt.PLAIN) {
            Box(Modifier.fillMaxSize().clipToBounds()) {
                HeldImage(c.backdrop(g), Modifier.fillMaxSize(), fadeMs = 450,
                    imageModifier = if (art == Features.ArArt.BLURRED && !LocalLiteEffects.current) Modifier.blur(18.dp) else Modifier)
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to ArcticSlate, 0.35f to ArcticSlate.copy(alpha = 0.78f), 0.7f to ArcticSlate.copy(alpha = 0.25f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to ArcticSlate.copy(alpha = 0.35f), 1f to ArcticSlate.copy(alpha = 0.96f))))
        val contentStart = 68.dp + 22.dp
        if (g == null || rows.isEmpty()) StyleEmpty(Modifier.fillMaxSize().padding(start = contentStart))
        else {
            // The widgets start under the info block's last line (a two-line name or wrapped facts push them down).
            var infoBottom by remember { mutableStateOf(0.dp) }
            val dens = androidx.compose.ui.platform.LocalDensity.current
            ArcticInfo(c, g, Modifier.onGloballyPositioned { infoBottom = with(dens) { (it.positionInParent().y + it.size.height).toDp() } }
                .padding(start = contentStart, top = 44.dp, end = 24.dp).widthIn(max = (w - contentStart) * 0.62f), accent, compact = h < 420.dp)
            ArcticWidgets(c, rows, Modifier.fillMaxSize().padding(start = contentStart), h, w - contentStart, accent, top = maxOf(h * 0.42f, infoBottom + 12.dp))
        }
        // The rail, over the widgets when it folds open (a soft shadow at its edge).
        ArcticRail(c, Modifier.zIndex(5f).width(railW).fillMaxHeight(), railOpen, accent)
    }
}

@Composable
private fun ArcticRail(c: StyleCtx, modifier: Modifier, open: Boolean, accent: Color) {
    val titles = LocalTitleOptions.current
    Column(
        // Open, the rail is solid: the game's info under it must not show through its labels.
        modifier.background(if (open) Brush.horizontalGradient(listOf(Color(0xFF0A1018), Color(0xFF0C141E))) else Brush.horizontalGradient(listOf(Color(0xF20A1018), Color(0xE60A1018), Color(0x990A1018))))
            .padding(top = 40.dp, bottom = 14.dp, start = 10.dp, end = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val n = arcticNavCount(c)
        // Keeps the picked entry in view on a long list of consoles: a window around it.
        val sel = if (ArcticNav.nav) ArcticNav.navSel else c.stop.coerceAtLeast(0)
        val shown = 9
        val lo = (sel - shown / 2).coerceIn(0, (n - shown).coerceAtLeast(0)); val hi = (lo + shown - 1).coerceAtMost(n - 1)
        for (i in lo..hi) {
            val isSettings = i >= c.stops.size
            val stop = c.stops.getOrNull(i)
            val focused = ArcticNav.nav && i == ArcticNav.navSel
            val current = !isSettings && i == c.stop
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (focused) accent.copy(alpha = 0.9f) else if (current) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                    .pointerInput(i) { detectTapGestures {
                        if (isSettings) c.actions.settings() else if (c.stop >= 0 && i != c.stop) c.actions.category(i - c.stop)
                        ArcticNav.nav = false
                    } }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    val logo = stop?.logo
                    if (logo != null) AsyncImage(logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else Icon(if (isSettings) Icons.Filled.Settings else stop?.icon ?: Icons.Filled.SportsEsports, null,
                        tint = if (focused) Color.Black else Color.White.copy(alpha = if (current) 1f else 0.75f), modifier = Modifier.size(22.dp))
                }
                if (open) {
                    Spacer(Modifier.width(12.dp))
                    // A long console name ("Super Nintendo Entertainment System") wraps onto a second line inside the rail (R18).
                    Text(if (isSettings) "Settings" else stop?.label ?: "", color = if (focused) Color.Black else Color.White.copy(alpha = if (current) 1f else 0.82f),
                        fontSize = 15.sp, lineHeight = 18.sp, fontWeight = if (current || focused) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (Features.arClock.state.value) {
            val now = remember { mutableStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(20_000); now.value = System.currentTimeMillis() } }
            val ctxA = androidx.compose.ui.platform.LocalContext.current
            val f = remember { java.text.SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(ctxA)) "HH:mm" else "h:mm", java.util.Locale.getDefault()) }
            Text(f.format(java.util.Date(now.value)), color = Color.White.copy(alpha = 0.85f), fontSize = if (open) 22.sp else 13.sp, fontWeight = FontWeight.Light,
                modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun ArcticInfo(c: StyleCtx, g: com.shelf.launcher.data.Game, modifier: Modifier, accent: Color, compact: Boolean) {
    val titles = LocalTitleOptions.current
    val meta = c.state.metadata[g.stableKey]
    Column(modifier.riseOnChange(g.stableKey)) {
        val logo = c.logo(g)
        if (logo != null) AsyncImage(rememberStyleArtRequest(logo, trim = true), titles.display(g.name), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
            modifier = Modifier.height(if (compact) 46.dp else 70.dp).fillMaxWidth())
        else FullText(titles.display(g.name), color = Color.White, fontSize = if (compact) 24.sp else 32.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        Spacer(Modifier.height(8.dp))
        // Facts wrap onto a second line rather than run off the edge (R18).
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            @Composable fun info(icon: ImageVector, text: String?) {
                if (text.isNullOrBlank()) return
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(text, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, fontWeight = FontWeight.Medium, softWrap = false)
                }
            }
            info(Icons.Filled.CalendarMonth, meta?.releaseYear?.toString())
            info(Icons.Filled.SportsEsports, titles.console(g.platform))
            info(Icons.Filled.Label, meta?.genres?.firstOrNull())
            info(Icons.Filled.Timer, c.playtime(g)?.let { stylePlaytime(it, true) })
            info(Icons.Filled.EmojiEvents, c.progress(g)?.let { (e, t) -> "$e / $t" })
        }
        val story = meta?.summary?.takeIf { it.isNotBlank() }
        if (story != null && !compact) {
            Spacer(Modifier.height(8.dp))
            RollingStory(story, Color.White.copy(alpha = 0.78f), 13.sp, 18.sp, 54.dp)
        }
    }
}

/** The widgets: the row you're in big at the bottom half, the next one peeking under it. */
@Composable
private fun ArcticWidgets(c: StyleCtx, rows: List<HomeRow>, modifier: Modifier, h: Dp, w: Dp, accent: Color, top: Dp) {
    @Suppress("UNUSED_VARIABLE") val t = ArcticNav.tick
    ArcticNav.cursor.follow(rows, c.focus)
    val rowNow = ArcticNav.cursor.row
    val spotlight = Features.arFirst.state.value == Features.ArFirst.SPOTLIGHT
    val posterH = (h * 0.30f).coerceIn(100.dp, 230.dp)
    val wideH = (h * 0.24f).coerceIn(80.dp, 180.dp)
    fun rowH(i: Int) = 24.dp + (if (spotlight && i == 0) wideH else posterH) + ArcticNameH + 16.dp
    val tops = remember(rows.size, spotlight, h) { FloatArray(rows.size + 1).also { a -> for (i in rows.indices) a[i + 1] = a[i] + rowH(i).value } }
    fun topAt(x: Float): Float { val i = x.toInt().coerceIn(0, rows.lastIndex); val f = x - i; return tops[i] + (tops[(i + 1).coerceAtMost(rows.size)] - tops[i]) * f }
    val shift by animateFloatAsState(rowNow.toFloat(), if (rememberAnimationsEnabled()) tween(380, easing = FastOutSlowInEasing) else tween(0), label = "arRows")
    val baseY = top
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    Box(modifier.clipToBounds()) {
        for ((ri, r) in rows.withIndex()) {
            if (ri < rowNow - 1 || ri > rowNow + 2) continue
            androidx.compose.runtime.key(r.key) {
                val here = ri == rowNow && !ArcticNav.nav
                val wide = spotlight && ri == 0
                Column(Modifier.fillMaxWidth().graphicsLayer {
                    translationY = baseY.toPx() + (tops[ri] - topAt(shift)) * density
                    alpha = (if (ri < shift - 0.5f) (1f - (shift - ri)).coerceIn(0f, 1f) else 1f) * (if (ri == rowNow) 1f else 0.6f)
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(24.dp).padding(bottom = 6.dp)) {
                        Text(r.title.uppercase(), color = Color.White.copy(alpha = if (here) 0.95f else 0.7f), fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                        Text("  ·  ${r.games.size}", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                    ArcticRow(c, r, here, if (wide) wideH else posterH, wide, w, accent, { i -> select(i); ArcticNav.cursor.follow(rows, i); ArcticNav.tick++ }, open)
                }
            }
        }
    }
}

@Composable
private fun ArcticRow(c: StyleCtx, r: HomeRow, here: Boolean, tileH: Dp, wide: Boolean, screenW: Dp, accent: Color, select: (Int) -> Unit, open: () -> Unit) {
    val titles = LocalTitleOptions.current
    val tileW = if (wide) tileH * 1.78f else tileH * 0.68f
    val gap = 12.dp
    val stride = tileW + gap
    val col = (ArcticNav.cursor.cols[r.key] ?: 0).coerceIn(0, (r.games.size - 1).coerceAtLeast(0))
    // The row moves only when the one you're on would leave the view (Arctic's widgets scroll in steps, not per move).
    val perView = ((screenW - 24.dp) / stride).toInt().coerceAtLeast(1)
    val first = remember(r.key) { intArrayOf(0) }
    if (col < first[0]) first[0] = col
    if (col > first[0] + perView - 1) first[0] = col - perView + 1
    val glide by animateFloatAsState(first[0].toFloat(), if (rememberAnimationsEnabled()) tween(300, easing = FastOutSlowInEasing) else tween(0), label = "arGlide")
    Box(Modifier.fillMaxWidth().height(tileH + ArcticNameH)) {
        val lo = (first[0] - 1).coerceAtLeast(0); val hi = (first[0] + perView + 1).coerceAtMost(r.games.lastIndex)
        for (k in lo..hi) {
            val gi = r.games[k]
            val game = c.games.getOrNull(gi) ?: continue
            androidx.compose.runtime.key(game.stableKey) {
                val on = here && k == col && gi == c.focus
                val lift by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "arLift")
                Column(
                    Modifier.zIndex(if (on) 2f else 0f).width(tileW)
                        .graphicsLayer {
                            translationX = (stride * (k - glide)).toPx(); val s = 1f + 0.05f * lift; scaleX = s; scaleY = s
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }
                        .pointerInput(gi) { detectTapGestures { if (on) open() else select(gi) } }
                ) {
                    Box(
                        Modifier.size(tileW, tileH).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1B2633))
                            .then(if (on) Modifier.focusedCard().border(2.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier)
                    ) {
                        val url = if (wide) c.wide(game) else c.cover(game)
                        if (url != null) AsyncImage(rememberStyleArtRequest(url), titles.display(game.name), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else FullText(titles.display(game.name), color = Color.White, fontSize = 13.sp, maxLines = 3, modifier = Modifier.align(Alignment.Center).padding(8.dp))
                        if (game.stableKey in c.state.favorites) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(8.dp).clip(CircleShape).background(accent))
                    }
                }
            }
        }
        // The name of the game you're on, under its card: as wide as the row leaves it (not the card's own width) and on
        // up to two lines, so the whole name shows (R18), clear of the next row's title.
        if (here) {
            val k = r.games.indexOf(c.focus).takeIf { it == col } ?: -1
            val game = c.games.getOrNull(c.focus)
            if (k >= 0 && game != null) {
                val x = (stride * (k - glide)).coerceIn(0.dp, (screenW - 280.dp).coerceAtLeast(0.dp))
                Text(
                    titles.display(game.name), color = Color.White, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                    modifier = Modifier.offset(x = x, y = tileH + 6.dp).width((screenW - x - 16.dp).coerceAtLeast(120.dp))
                )
            }
        }
    }
}

private val ArcticNameH = 40.dp

@Composable
internal fun ArcticSettings() {
    MutedNote(
        "A media-centre home: a navigation rail of your categories that folds open (◀ from the first tile), the game you're " +
            "on as fanart behind its logo and info, widgets of your games below. A opens the Arctic info drawer."
    )
    Spacer(Modifier.height(10.dp))
    StyleChoice("Navigation rail", Features.ArNav.values(), Features.arNav.state.value, { it.label }) { Features.arNav.value = it }
    StyleChoice("Behind", Features.ArArt.values(), Features.arArt.state.value, { it.label }) { Features.arArt.value = it }
    StyleChoice("First widget", Features.ArFirst.values(), Features.arFirst.state.value, { it.label }) { Features.arFirst.value = it }
    Toggle(Icons.Filled.ViewQuilt, "Clock on the rail", "The time at the foot of the navigation rail.", Features.arClock)
    SectionLabel("Widgets")
    Toggle(Icons.Filled.ViewQuilt, "Continue playing", "The games you played last, newest first.", Features.arRowContinue)
    Toggle(Icons.Filled.ViewQuilt, "Favourites", "The category's favourites.", Features.arRowFav)
    Toggle(Icons.Filled.ViewQuilt, "Most played", "By time played.", Features.arRowMost)
    Toggle(Icons.Filled.ViewQuilt, "Not played yet", "Games you haven't started.", Features.arRowNew)
}
