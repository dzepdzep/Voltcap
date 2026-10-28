package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
 * Next version (Toni: "Cinema Home — Netflix-inspired"): a cinematic browsing home of Voltcap's own.
 * - The billboard: the game you're on fills the top of the screen — its art drifting slowly closer, its logo (or name),
 *   a line of facts, its story rolling up when it's long, Play and More info. Rest on a game and its gameplay clip fades
 *   in over the art (Video previews; Layout options › Gameplay previews).
 * - Below it, rows of collections — Continue playing, the category, Your favourites, Most played, Not played yet — each a
 *   strip of wide art (or posters). ▲ ▼ glide from row to row (the row you're in rises to just under the billboard), ◀ ▶
 *   along it; the game you're on lifts a little with a light frame and its name under it.
 * - ▲ from the first row reaches Play / More info on the billboard. A opens Cinema Premiere (WinScenesHomes.kt): the
 *   billboard grown into the game's own page.
 */

internal object CinemaNav {
    @Volatile var ctx: StyleCtx? = null
    @Volatile var rows: List<HomeRow> = emptyList()
    val cursor = RowCursor()
    /** On the billboard's buttons (0 Play, 1 More info), or -1 on the rows. */
    var hero by mutableIntStateOf(-1)
    /** Bumped on every move, so the rows' glide follows (the cursor itself isn't state). */
    var tick by mutableIntStateOf(0)
    internal val keysDown = HashSet<Int>()
}

internal fun cinemaSwitches() = HomeRowSwitches(
    Features.cnRowContinue.value, Features.cnRowFav.value, Features.cnRowMost.value, Features.cnRowNew.value
)

internal fun cinemaKey(event: KeyEvent, reach: (Boolean) -> Boolean): Boolean {
    val c = CinemaNav.ctx ?: return false
    val code = event.nativeKeyEvent.keyCode
    val down = event.type == KeyEventType.KeyDown
    val up = event.type == KeyEventType.KeyUp
    if (up && CinemaNav.keysDown.remove(code)) return true
    val k = event.key
    val isA = k == Key.ButtonA || k == Key.Enter || k == Key.DirectionCenter
    val rows = CinemaNav.rows
    val select: (Int) -> Unit = { i -> c.actions.select(i); CinemaNav.tick++ }
    fun take(): Boolean { CinemaNav.keysDown += code; return true }
    // The billboard's buttons.
    if (CinemaNav.hero >= 0) {
        if (isA) {
            if (up) { val g = c.game; if (g != null) { if (CinemaNav.hero == 0) c.actions.play(g) else c.actions.open() } }
            return true
        }
        if (!down) return false
        val first = event.nativeKeyEvent.repeatCount == 0
        when (k) {
            Key.DirectionLeft, Key.DirectionRight -> { CinemaNav.hero = if (k == Key.DirectionRight) 1 else 0; return take() }
            Key.DirectionDown -> { CinemaNav.hero = -1; CinemaNav.tick++; return take() }
            Key.DirectionUp -> { if (first) reach(true); return take() }
            Key.ButtonB, Key.Back -> { if (first) { CinemaNav.hero = -1; CinemaNav.tick++ }; return take() }
            else -> return false
        }
    }
    if (!down) return false
    val first = event.nativeKeyEvent.repeatCount == 0
    CinemaNav.cursor.follow(rows, c.focus)
    when (k) {
        Key.DirectionLeft, Key.DirectionRight -> {
            val d = if (k == Key.DirectionRight) 1 else -1
            if (!CinemaNav.cursor.along(rows, d, select) && first) EdgeBump.hit(d)
            return take()
        }
        Key.DirectionUp -> {
            if (!CinemaNav.cursor.across(rows, -1, select) && first) { CinemaNav.hero = 0 }
            return take()
        }
        Key.DirectionDown -> {
            if (!CinemaNav.cursor.across(rows, 1, select) && first) reach(false)
            return take()
        }
        else -> return if (rows.isEmpty() && (isA || k == Key.ButtonX || k == Key.ButtonY)) take() else false
    }
}

/** Cinema's room: near-black, a faint vignette. */
@Composable
internal fun CinemaBackdrop(modifier: Modifier) {
    Box(modifier.background(Brush.radialGradient(listOf(Color(0xFF17171B), Color(0xFF0B0B0D), Color(0xFF060607)))))
}

/** The colour for what's lit: the cinema's red, or your theme's. */
@Composable
internal fun cinemaAccent(): Color =
    if (Features.cnAccent.state.value == Features.CnAccent.CINEMA) Color(0xFFE50914) else ShelfTheme.accent

@Composable
internal fun StyleCinema(c: StyleCtx, modifier: Modifier = Modifier) {
    val switches = HomeRowSwitches(
        Features.cnRowContinue.state.value, Features.cnRowFav.state.value, Features.cnRowMost.state.value, Features.cnRowNew.state.value
    )
    val rows = remember(c.games, c.state.lastPlayedAt, c.state.favorites, c.state.playtime, c.categoryLabel, switches.continuePlaying, switches.favourites, switches.mostPlayed, switches.notPlayed) {
        homeRows(c, switches)
    }
    SideEffect {
        CinemaNav.ctx = c; CinemaNav.rows = rows
        CinemaNav.cursor.follow(rows, c.focus)
        StyleNav.side = emptyList(); StyleNav.top = emptyList(); StyleNav.bar = emptyList()
    }
    DisposableEffect(Unit) { onDispose { CinemaNav.keysDown.clear(); CinemaNav.hero = -1 } }
    androidx.activity.compose.BackHandler(enabled = CinemaNav.hero >= 0 && GamePopup.game == null) { CinemaNav.hero = -1 }
    val g = c.game
    LaunchedEffect(g?.stableKey) {
        val gg = g ?: return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        c.actions.needMetadata(gg)
    }
    if (g == null || rows.isEmpty()) { StyleEmpty(modifier); return }
    val accent = cinemaAccent()
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth; val h = maxHeight
        val compact = Features.cnHero.state.value == Features.CnHero.COMPACT || h < 420.dp
        val heroH = h * (if (compact) 0.50f else 0.60f)
        // Where the billboard's words end (a two-line name or a long fact line can run past the art): the rows start
        // below them, never over the buttons.
        var textBottom by remember { mutableStateOf(0.dp) }
        CinemaBillboard(c, g, Modifier.fillMaxWidth().height(heroH + 40.dp), heroH, w, accent, compact, onTextBottom = { textBottom = it })
        val rowsTop = maxOf(heroH - 12.dp, textBottom + 14.dp)
        // The rows: the one you're in rises to just under the billboard; the ones before it slide away above.
        @Suppress("UNUSED_VARIABLE") val t = CinemaNav.tick
        CinemaNav.cursor.follow(rows, c.focus)
        val rowNow = CinemaNav.cursor.row
        val tiles = Features.cnTiles.state.value
        val rowGap = 14.dp
        val titleH = if (compact) 26.dp else 30.dp
        val nameH = 38.dp
        fun tileH(r: Int): Dp = when {
            tiles == Features.CnTiles.POSTER || (tiles == Features.CnTiles.MIXED && r == 0) -> (h * 0.26f).coerceIn(96.dp, 200.dp)
            else -> (h * 0.19f).coerceIn(70.dp, 150.dp)
        }
        val shift by animateFloatAsState(rowNow.toFloat(), if (rememberAnimationsEnabled()) tween(420, easing = FastOutSlowInEasing) else tween(0), label = "cnRows")
        // Each row's top, from the first one's (poster rows are taller); the glide runs between them.
        val tops = remember(rows.size, tiles, h) { FloatArray(rows.size + 1).also { a -> for (i in rows.indices) a[i + 1] = a[i] + (titleH + tileH(i) + nameH + rowGap).value } }
        fun topAt(x: Float): Float { val i = x.toInt().coerceIn(0, rows.lastIndex); val f = x - i; return tops[i] + (tops[(i + 1).coerceAtMost(rows.size)] - tops[i]) * f }
        val inHero = CinemaNav.hero >= 0
        Box(Modifier.fillMaxSize().clipToBounds()) {
            for ((ri, r) in rows.withIndex()) {
                if (ri < rowNow - 1 || ri > rowNow + 2) continue
                androidx.compose.runtime.key(r.key) {
                    val here = ri == rowNow && !inHero
                    val poster = tiles == Features.CnTiles.POSTER || (tiles == Features.CnTiles.MIXED && ri == 0)
                    Column(
                        Modifier.fillMaxWidth().graphicsLayer {
                            translationY = rowsTop.toPx() + (tops[ri] - topAt(shift)) * density
                            alpha = when { ri < shift - 0.5f -> (1f - (shift - ri)).coerceIn(0f, 1f); else -> 1f } * (if (ri == rowNow) 1f else 0.72f)
                        }
                    ) {
                        Text(
                            r.title + if (r.key == "all") "" else "", color = Color.White.copy(alpha = if (here) 1f else 0.8f),
                            fontSize = if (compact) 14.sp else 17.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.height(titleH).padding(start = 40.dp, bottom = 6.dp)
                        )
                        CinemaRow(c, r, ri, here, poster, tileH(ri), nameH, w, accent, select = { i -> select(i); CinemaNav.cursor.follow(rows, i); CinemaNav.tick++ }, open = open)
                    }
                }
            }
        }
    }
}

/** The billboard: the game's art (and clip), its logo or name, facts, story and the two buttons. */
@Composable
private fun CinemaBillboard(c: StyleCtx, g: com.shelf.launcher.data.Game, modifier: Modifier, heroH: Dp, w: Dp, accent: Color, compact: Boolean, onTextBottom: (Dp) -> Unit = {}) {
    val dens = androidx.compose.ui.platform.LocalDensity.current
    val titles = LocalTitleOptions.current
    val meta = c.state.metadata[g.stableKey]
    val lite = LocalLiteEffects.current
    // The clip: after resting on the game a moment (looked up even when the home's own Video previews is off — the
    // billboard is where it's meant to be), unless Gameplay previews is off.
    val previewsOn = Features.cnPreview.state.value && c.state.appearance.videoPreviews && !lite
    var clipFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(g.stableKey, previewsOn) {
        clipFor = null
        if (!previewsOn) return@LaunchedEffect
        kotlinx.coroutines.delay(600)
        c.actions.needVideo(g)
        kotlinx.coroutines.delay(1400)
        clipFor = g.stableKey
    }
    val clip = if (clipFor == g.stableKey) c.state.videoUrls[g.stableKey] else null
    Box(modifier) {
        // The art, edge to edge, fading into the room at its left and bottom.
        Box(Modifier.fillMaxWidth().height(heroH + 40.dp).clipToBounds()) {
            HeldImage(c.backdrop(g), Modifier.fillMaxSize(), fadeMs = 380, zoom = true)
            if (clip != null) {
                VideoCrossfade(
                    clip, c.state.appearance.videoSound, Modifier.fillMaxSize(),
                    paused = com.shelf.launcher.data.BatterySaver.low || HomeIdle.rest || (Features.clipIdlePause.state.value && HomeIdle.calm)
                )
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xFF0B0B0D).copy(alpha = 0.92f), 0.45f to Color(0xFF0B0B0D).copy(alpha = 0.45f), 1f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.25f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color(0xFF0B0B0D))))
        }
        // The text block, rising in for each game.
        Column(
            Modifier.onGloballyPositioned { onTextBottom(with(dens) { it.size.height.toDp() }) }
                .padding(start = 40.dp, top = if (compact) 40.dp else 56.dp).widthIn(max = w * 0.46f).riseOnChange(g.stableKey, floor = 0.4f, ms = 240)
        ) {
            val logo = if (Features.cnLogo.state.value) c.logo(g) else null
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Movie, null, tint = accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                StableFullText(titles.console(g.platform).uppercase(), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            LogoOrName(logo, titles.display(g.name), Modifier.height(if (compact) 54.dp else 84.dp).fillMaxWidth()) {
                StableFullText(titles.display(g.name), color = Color.White, fontSize = if (compact) 26.sp else 36.sp, fontWeight = FontWeight.Black, maxLines = 2,
                    lineHeight = if (compact) 28.sp else 38.sp)
            }
            Spacer(Modifier.height(8.dp))
            val facts = listOfNotNull(
                meta?.releaseYear?.toString(),
                meta?.genres?.firstOrNull(),
                c.playtime(g)?.let { stylePlaytime(it, true) },
                c.progress(g)?.let { (e, t) -> "★ $e / $t" }
            )
            // (Update 363: both lines keep their room before the game's details have arrived, so the buttons don't move.)
            Row(Modifier.heightIn(min = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                if (facts.isNotEmpty()) {
                    if (c.progress(g)?.let { it.first >= it.second } == true) {
                        Text("MASTERED", color = Color(0xFF46D369), fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.width(10.dp))
                    }
                    StableFullText(facts.joinToString("   "), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            val story = meta?.summary?.takeIf { it.isNotBlank() }
            if (!compact) {
                Spacer(Modifier.height(8.dp))
                if (story != null) RollingStory(story, Color.White.copy(alpha = 0.82f), 13.sp, 18.sp, 54.dp, roll = Features.cnRoll.state.value) else Spacer(Modifier.height(54.dp))
            }
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CinemaButton(Icons.Filled.PlayArrow, if (c.lastPlayed(g) != null) "Continue" else "Play", CinemaNav.hero == 0, white = true) { c.actions.play(g) }
                CinemaButton(Icons.Filled.Info, "More info", CinemaNav.hero == 1, white = false) { c.actions.open() }
            }
        }
    }
}

@Composable
private fun CinemaButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, lit: Boolean, white: Boolean, onClick: () -> Unit) {
    val k by animateFloatAsState(if (lit) 1.06f else 1f, tween(180), label = "cnBtn")
    Row(
        Modifier.graphicsLayer { scaleX = k; scaleY = k }
            .clip(RoundedCornerShape(6.dp))
            .background(if (white) Color.White.copy(alpha = if (lit) 1f else 0.88f) else Color(0xFF6D6D6E).copy(alpha = if (lit) 0.9f else 0.6f))
            .then(if (lit) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier)
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (white) Color.Black else Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (white) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** One row of tiles: the one you're on held near the left edge, the row gliding under it. */
@Composable
private fun CinemaRow(
    c: StyleCtx, r: HomeRow, ri: Int, here: Boolean, poster: Boolean, tileH: Dp, nameH: Dp, screenW: Dp, accent: Color,
    select: (Int) -> Unit, open: () -> Unit
) {
    val tileW = if (poster) tileH * 0.70f else tileH * 1.78f
    val gap = 8.dp
    val stride = tileW + gap
    val col = if (here) CinemaNav.cursor.col(CinemaNav.rows).coerceIn(0, r.games.lastIndex) else (CinemaNav.cursor.cols[r.key] ?: 0).coerceIn(0, r.games.lastIndex)
    val glide by animateFloatAsState(col.toFloat(), if (rememberAnimationsEnabled()) tween(320, easing = FastOutSlowInEasing) else tween(0), label = "cnGlide")
    val visible = ((screenW / stride).toInt() + 2)
    Box(Modifier.fillMaxWidth().height(tileH + nameH)) {
        val lo = (col - 2).coerceAtLeast(0); val hi = (col + visible).coerceAtMost(r.games.lastIndex)
        for (k in lo..hi) {
            val gi = r.games[k]
            val game = c.games.getOrNull(gi) ?: continue
            androidx.compose.runtime.key(game.stableKey) {
                val on = here && gi == c.focus && k == col
                val lift by animateFloatAsState(if (on) 1f else 0f, tween(220), label = "cnLift")
                Column(
                    Modifier.zIndex(if (on) 2f else 0f)
                        .graphicsLayer {
                            translationX = (40.dp + stride * (k - glide)).toPx()
                            val s = 1f + 0.08f * lift; scaleX = s; scaleY = s
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }
                        .width(tileW)
                        .pointerInput(gi) { detectTapGestures { if (on) open() else select(gi) } }
                ) {
                    Box(
                        Modifier.size(tileW, tileH).clip(RoundedCornerShape(6.dp)).background(Color(0xFF222226))
                            .then(if (on) Modifier.focusedCard().border(3.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier)
                    ) {
                        val url = if (poster) c.cover(game) else c.wide(game)
                        if (url != null) AsyncImage(rememberStyleArtRequest(url), titles(game), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else FullText(titles(game), color = Color.White, fontSize = 13.sp, maxLines = 3, modifier = Modifier.align(Alignment.Center).padding(8.dp))
                        // A thin progress line for games you've played, like the rows' "keep watching" bar.
                        c.progress(game)?.let { (e, t) ->
                            if (e in 1 until t) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f))) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(e.toFloat() / t).background(accent))
                            }
                        }
                    }
                }
            }
        }
        // The name of the game you're on, under its card: as wide as the row leaves it and on up to two lines, so the
        // whole name shows (R18) in the space kept for it, clear of the next row's title.
        if (here) {
            val game = c.games.getOrNull(c.focus)
            if (game != null && r.games.getOrNull(col) == c.focus) {
                // (Update 363: the width is the one the name has where the row settles (the tile's end of the glide) and the
                // position is read in layout, so the name keeps its wrapping for the whole glide instead of re-wrapping — one
                // line, two, one — as the room beside it changed every frame, and the row doesn't recompose per frame.)
                val xRest = 40.dp
                val xMax = (screenW - 280.dp).coerceAtLeast(40.dp)
                Text(
                    titles(game), color = Color.White, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                    modifier = Modifier
                        .offset { androidx.compose.ui.unit.IntOffset((40.dp + stride * (col - glide)).coerceIn(40.dp, xMax).roundToPx(), (tileH * 1.04f + 6.dp).roundToPx()) }
                        .width((screenW - xRest - 24.dp).coerceAtLeast(120.dp))
                )
            }
        }
    }
}

@Composable
private fun titles(g: com.shelf.launcher.data.Game): String = LocalTitleOptions.current.display(g.name)

@Composable
internal fun CinemaSettings() {
    MutedNote(
        "The game you're on as a big billboard — its art, logo, story and gameplay preview — with rows of your collections " +
            "below. ▲ ▼ go from row to row, ◀ ▶ along it, ▲ from the first row to Play and More info. A opens Cinema " +
            "Premiere, the game's own page."
    )
    Spacer(Modifier.height(10.dp))
    StyleChoice("Billboard", Features.CnHero.values(), Features.cnHero.state.value, { it.label }) { Features.cnHero.value = it }
    StyleChoice("Tiles", Features.CnTiles.values(), Features.cnTiles.state.value, { it.label }) { Features.cnTiles.value = it }
    StyleChoice("Highlight colour", Features.CnAccent.values(), Features.cnAccent.state.value, { it.label }) { Features.cnAccent.value = it }
    Toggle(Icons.Filled.Movie, "Gameplay previews", "Rest on a game and its clip fades in over the billboard (needs Video previews on).", Features.cnPreview)
    Toggle(Icons.Filled.Movie, "Logo on the billboard", "The game's logo instead of its name, when it has one.", Features.cnLogo)
    Toggle(Icons.Filled.Movie, "Story rolls up", "A long story rolls up slowly so all of it can be read. Off: it stays at its start.", Features.cnRoll)
    SectionLabel("Rows")
    Toggle(Icons.Filled.Movie, "Continue playing", "The games you played last, newest first.", Features.cnRowContinue)
    Toggle(Icons.Filled.Movie, "Your favourites", "The category's favourites.", Features.cnRowFav)
    Toggle(Icons.Filled.Movie, "Most played", "By time played.", Features.cnRowMost)
    Toggle(Icons.Filled.Movie, "Not played yet", "Games you haven't started.", Features.cnRowNew)
}
