package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/*
 * Update 202: Pocket Quest — a full-screen game menu in the style of the great 32-bit handheld RPGs (original names,
 * drawings and scenery only; your own game art is shown as it is). Three regions:
 * - a compact top bar: the VOLTCAP plate (opens Select a System), your consoles / categories as tabs with their icons
 *   (the one you're in on a gold plate that glides between tabs), Systems, Search, Sort & filter, Settings, battery
 *   and clock;
 * - the showcase: the game you're on big on the left (your game look — cartridge, disc, case or cover — with ‹ › and a
 *   gentle 3D lean), a chamfered blue panel with gold edges on the right: its name, console, year, genre, play time,
 *   achievements, a few lines about it, a screenshot with three small ones, and the gold Play button;
 * - the collection: the console's games along the bottom as their media, gliding; the one you're on raised in a gold
 *   frame under a moving pixel arrow (the showcase follows at once).
 * Pages over it: Select a System (every console with its icon and game count) and the game's details (a big picture,
 * screenshots, its facts and a list: Play, View Details, Game Options, Achievements, Favourite, Manage Save Data, Save
 * Vault, Game Window — the same handlers the other layouts use).
 * The scenery behind is PocketQuestScenery.kt (StyleBackLayer draws it); the options are PocketQuestSettings.kt.
 *
 * Controller: ◀ ▶ games · ▲ the panel's buttons, then the top bar · ▼ back down (then what lies below) · A plays (or
 * the window: Layout options) · Y details · X favourite · L2 / R2 console · L1 / R1 the shortcuts / quick settings as
 * everywhere (update 204; "L1 / R1 switch the console" in Layout options makes them console keys again) · Start the
 * quick settings (R1 panel) · Select Select a System (held: sort & filter) · B back. Touch: everything can be tapped;
 * swipe the collection; the top bar's Quick settings button (update 204).
 */

/** Update 202: where the controller is on Pocket Quest (kept while you play and come back; reset when the layout goes). */
internal object PqNav {
    enum class Page { BROWSE, SYSTEMS, DETAILS }
    enum class Zone { ROW, SHOW, TOP }

    var page by mutableStateOf(Page.BROWSE)
    var zone by mutableStateOf(Zone.ROW)
    /** The button the D-pad is on in [Zone.SHOW] (0 Play, 1 Details, 2 Favourite) or [Zone.TOP] (tabs, then the tools). */
    var index by mutableIntStateOf(0)
    var sysCursor by mutableIntStateOf(0)
    var detCursor by mutableIntStateOf(0)
    /** A game starting (its key), for the launch flash; [launchTick] counts the flashes. */
    var launching by mutableStateOf<String?>(null)
    var launchTick by mutableIntStateOf(0)

    @Volatile var ctx: StyleCtx? = null
    /** Tabs in the top bar as last drawn, and Select a System's columns. */
    @Volatile var tabCount = 1
    @Volatile var sysCols = 5
    /** The game asked for last (the focus may not have caught up yet when keys come quickly), and for which list. */
    @Volatile var lastSet = -1
    @Volatile var lastSetGames: List<Game>? = null
    @Volatile var selectArmed = false
    internal val keysDown = HashSet<Int>()

    /** Everything the controller moves here, for the "▼ changed nothing" check (LauncherScreen). */
    fun signature(): Any = listOf(page, zone, index, sysCursor, detCursor)

    fun reset() {
        page = Page.BROWSE; zone = Zone.ROW; index = 0; sysCursor = 0; detCursor = 0
        launching = null; lastSet = -1; lastSetGames = null; selectArmed = false
        keysDown.clear()
    }
}

/**
 * The number of tools after the tabs in the top bar: Systems, Search, Sort & filter, Quick settings, Settings.
 * (Update 204: Quick settings — the R1 panel's tiles — added; they could only be reached with Start before.)
 */
private const val PQ_TOOLS = 5

@Composable
internal fun StylePocketQuest(c: StyleCtx, modifier: Modifier = Modifier) {
    val pal = pqPalette()
    SideEffect {
        PqNav.ctx = c
        if (PqNav.lastSet == c.focus || PqNav.lastSetGames !== c.games) PqNav.lastSet = -1
        StyleNav.side = emptyList(); StyleNav.top = emptyList(); StyleNav.bar = emptyList()
        StyleLoop.on = loopRowOn(c)
    }
    DisposableEffect(Unit) { onDispose { PqSynth.release(); PqNav.reset(); PqNavHost.parallax.floatValue = 0f } }
    val reduced = pqReduced()
    val g = c.game
    val actions by rememberUpdatedState(c.actions)
    // The game you rest on: its details, screenshots and achievement list looked up (not for every game passed over).
    LaunchedEffect(g?.stableKey) {
        val game = g ?: return@LaunchedEffect
        kotlinx.coroutines.delay(320)
        actions.needMetadata(game)
        if (Features.pqScreenshots.value) actions.needMedia(game)
        if (game.raGameId != null) actions.needAchievements(game)
    }
    // The scenery's parallax follows your place in the list, eased.
    val n = c.games.size
    val target = if (n > 1) c.focus.toFloat() / (n - 1) * 2f - 1f else 0f
    LaunchedEffect(target, reduced) {
        if (reduced) { PqNavHost.parallax.floatValue = 0f; return@LaunchedEffect }
        androidx.compose.animation.core.animate(PqNavHost.parallax.floatValue, target, animationSpec = tween(pqMs(900), easing = FastOutSlowInEasing)) { v, _ ->
            PqNavHost.parallax.floatValue = v
        }
    }
    // A console change: the games slide out / in from the side it came from, the edges flash brighter.
    val swap = remember { Animatable(0f) }
    val pulse = remember { Animatable(0f) }
    val shownCat = remember { arrayOfNulls<Any>(1) }
    val catKey = "${c.stop}|${c.categoryLabel}"
    LaunchedEffect(catKey) {
        val first = shownCat[0] == null
        shownCat[0] = catKey
        if (first || reduced) return@LaunchedEffect
        pulse.snapTo(1f)
        swap.snapTo(CategorySlide.dir.toFloat().takeIf { it != 0f } ?: 1f)
        kotlinx.coroutines.coroutineScope {
            launch { swap.animateTo(0f, tween(pqMs(300), easing = FastOutSlowInEasing)) }
            launch { pulse.animateTo(0f, tween(pqMs(650))) }
        }
    }
    // The launch flash (the item grows, a short bright flash), then the usual start.
    val flash = remember { Animatable(0f) }
    val tick = PqNav.launchTick
    LaunchedEffect(tick) {
        if (tick == 0) return@LaunchedEffect
        flash.snapTo(0f)
        flash.animateTo(1f, tween(pqMs(140)))
        flash.animateTo(0f, tween(pqMs(220)))
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < 360.dp || maxWidth < 560.dp
        val barH = if (compact) 34.dp else 42.dp
        Column(Modifier.fillMaxSize()) {
            PqTopBar(c, pal, compact, pulse, Modifier.fillMaxWidth().height(barH))
            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
            AnimatedContent(
                targetState = PqNav.page, label = "pqPage",
                transitionSpec = {
                    if (reduced) fadeIn(tween(120)) togetherWith fadeOut(tween(90))
                    else (fadeIn(tween(pqMs(190))) + scaleIn(tween(pqMs(220)), initialScale = 0.97f)) togetherWith fadeOut(tween(pqMs(120)))
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { page ->
                when (page) {
                    PqNav.Page.BROWSE -> PqBrowse(c, pal, compact, swap, flash)
                    PqNav.Page.SYSTEMS -> PqSystems(c, pal, compact)
                    PqNav.Page.DETAILS -> PqDetails(c, pal, compact)
                }
            }
        }
        // The retro confirmation flash over everything.
        if (!reduced) Canvas(Modifier.fillMaxSize()) {
            val f = flash.value
            if (f > 0.01f) {
                drawRect(pal.accentLight.copy(alpha = 0.28f * f))
                val band = size.height * 0.5f * f
                drawRect(Color.White.copy(alpha = 0.22f * f), Offset(0f, size.height / 2f - band / 2f), Size(size.width, band))
            }
        }
    }
}

// ---- Top bar ----

@Composable
private fun PqTopBar(c: StyleCtx, pal: PqPalette, compact: Boolean, pulse: Animatable<Float, AnimationVector1D>, modifier: Modifier) {
    val single = c.stop < 0 || c.stops.isEmpty()
    val tabs = if (single) 1 else c.stops.size
    SideEffect { PqNav.tabCount = tabs }
    val inTop = PqNav.page == PqNav.Page.BROWSE && PqNav.zone == PqNav.Zone.TOP
    val live by rememberUpdatedState(c)
    Box(modifier) {
        Box(Modifier.fillMaxSize().pqPanel(pal, cut = 8.dp, edge = 1.dp, alpha = 0.93f, deep = true, studs = false))
        // A thin accent line under the bar, brighter for a moment when the console changes.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.96f).height(2.dp)
                .graphicsLayer { alpha = 0.35f + 0.65f * pulse.value }
                .background(pal.accent)
        )
        Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // The brand plate (a tap: Select a System).
            Box(
                Modifier.fillMaxHeight().padding(vertical = 4.dp)
                    .pqPanel(pal, cut = 6.dp, edge = 1.dp, studs = false)
                    .tapOnly { pqOpenSystems(live) }
                    .padding(horizontal = if (compact) 8.dp else 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("VOLTCAP", color = pal.ink, fontFamily = pocketFont(), fontSize = if (compact) 10.sp else 13.sp, maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.width(6.dp))
            PqTabs(c, pal, compact, tabs, single, inTop, Modifier.weight(1f).fillMaxHeight())
            Spacer(Modifier.width(4.dp))
            val toolSize = if (compact) 26.dp else 32.dp
            PqTool(pal, Icons.Filled.GridView, "Systems", inTop && PqNav.index == tabs, toolSize) { PqNav.zone = PqNav.Zone.TOP; PqNav.index = tabs; pqOpenSystems(live) }
            PqTool(pal, Icons.Filled.Search, "Search", inTop && PqNav.index == tabs + 1, toolSize) { live.actions.search() }
            PqTool(pal, Icons.Filled.SwapVert, "Sort & filter", inTop && PqNav.index == tabs + 2, toolSize) { live.actions.sort() }
            // Update 204: the R1 panel (quick tiles), by touch and from the top bar with the D-pad.
            PqTool(pal, Icons.Filled.Tune, "Quick settings", inTop && PqNav.index == tabs + 3, toolSize) { live.actions.quickMenu() }
            PqTool(pal, Icons.Filled.Settings, "Settings", inTop && PqNav.index == tabs + 4, toolSize) { live.actions.settings() }
            Spacer(Modifier.width(6.dp))
            // (Update 330, phase 4: the round shortcuts' ☰ here, before the battery and clock — in a top corner it lay on
            // the VOLTCAP plate or the clock.)
            HomeMenuSlot.Room(Modifier.width(40.dp).height(toolSize))
            PqStatus(pal, compact)
            Spacer(Modifier.width(6.dp))
        }
    }
}

/** The console tabs: a gold plate glides to the one you're in; the D-pad's tab (in the top bar) gets an outline and the arrow. */
@Composable
private fun PqTabs(c: StyleCtx, pal: PqPalette, compact: Boolean, tabs: Int, single: Boolean, inTop: Boolean, modifier: Modifier) {
    val current = if (single) 0 else c.stop.coerceAtLeast(0)
    val lit = if (inTop && PqNav.index < tabs) PqNav.index else current
    val places = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }
    val live by rememberUpdatedState(c)
    val here = places[current]
    val ix = animateFloatAsState((here?.first ?: 0).toFloat(), tween(pqMs(260), easing = FastOutSlowInEasing), label = "pqTabX")
    val iw = animateFloatAsState((here?.second ?: 0).toFloat(), tween(pqMs(260), easing = FastOutSlowInEasing), label = "pqTabW")
    val plate = remember(pal) { Brush.verticalGradient(listOf(pal.accentLight, pal.accent, pal.accentDeep)) }
    CategoryTitle.Native() // (Update 212: the layout's own category title.)
    // (Update 294, Toni: "never show a partly hidden chip": whole tabs only, "+N ›" for the rest, the lit one always
    // whole — WholeChipRow. The plate is placed from each tab's place read against the row's own.)
    val rowX = remember { floatArrayOf(0f) }
    WholeChipRow(
        tabs, lit,
        modifier.onPlaced { rowX[0] = it.positionInRoot().x }
            .drawBehind {
                // The plate under the tab you're in (read while drawing: the glide redraws, never recomposes).
                val w = iw.value
                // (Never under a tab that's hidden past the row's ends.)
                if (w > 0f && ix.value >= -1f && ix.value + w <= size.width + 1f) {
                    val inset = 5.dp.toPx()
                    val notch = 2.dp.toPx()
                    val x = ix.value
                    drawRect(pal.edgeDark, Offset(x - 1f, inset - 1f), Size(w + 2f, size.height - 2 * inset + 2f))
                    drawRect(plate, Offset(x, inset), Size(w, size.height - 2 * inset))
                    drawRect(Color.White.copy(alpha = 0.5f), Offset(x + notch, inset + 1f), Size(w - 2 * notch, 1.dp.toPx()))
                    // Notched corners, a pixel-art plate.
                    val bg = pal.deep
                    drawRect(bg, Offset(x, inset), Size(notch, notch)); drawRect(bg, Offset(x + w - notch, inset), Size(notch, notch))
                    drawRect(bg, Offset(x, size.height - inset - notch), Size(notch, notch)); drawRect(bg, Offset(x + w - notch, size.height - inset - notch), Size(notch, notch))
                }
            },
        spacing = 0.dp, hintColor = pal.ink.copy(alpha = 0.78f), hintSize = if (compact) 11.sp else 13.sp
    ) { t ->
                val st = if (single) null else c.stops.getOrNull(t)
                val isHere = t == current
                val dpad = inTop && PqNav.index == t
                Row(
                    Modifier.fillMaxHeight()
                        .onPlaced { co ->
                            val v = (co.positionInRoot().x - rowX[0]).toInt() to co.size.width
                            if (places[t] != v) places[t] = v
                        }
                        .padding(vertical = 5.dp)
                        .then(if (dpad) Modifier.border(1.5.dp, pal.accentLight, PqChamfer(4.dp)) else Modifier)
                        .tapOnly {
                            PqNav.zone = PqNav.Zone.TOP; PqNav.index = t
                            val cc = live
                            if (single) cc.actions.category(1) else if (t != cc.stop && cc.stop >= 0) cc.actions.category(pocketStepsTo(cc, t))
                        }
                        .padding(horizontal = if (compact) 7.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val ink = if (isHere) pal.onAccent else pal.ink.copy(alpha = if (dpad) 1f else 0.78f)
                    PqStopIcon(st, if (isHere) pal.onAccent else pal.muted, if (compact) 14.dp else 17.dp)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (st == null) c.categoryLabel else pqShortName(st), color = ink, fontSize = if (compact) 11.sp else 13.sp,
                        fontWeight = if (isHere) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, softWrap = false
                    )
                    if (dpad) {
                        Spacer(Modifier.width(3.dp))
                        PqCursor(pal, Modifier.size(7.dp, 9.dp).graphicsLayer { scaleX = -1f })
                    }
                }
    }
}

/** A stop's small icon: RetroAchievements' console icon when there is one, else the category's symbol. */
@Composable
private fun PqStopIcon(st: StyleStop?, tint: Color, size: Dp) {
    val p = st?.platform
    val ra = if (p != null && !com.shelf.launcher.data.isCollectionKey(p)) com.shelf.launcher.data.ConsoleLogos.raIcon(p) else null
    if (ra != null) AsyncImage(ra, null, contentScale = ContentScale.Fit, modifier = Modifier.size(size))
    else Icon(st?.icon ?: Icons.Filled.Apps, null, tint = tint, modifier = Modifier.size(size))
}

/**
 * The tab's name. (Update 329, Toni: "on by default the real official names, not GBA, PS2…": the official name — the short
 * one (GBA, SNES, PS1…) only with Settings › Home › Name & logo › Short console names.)
 */
internal fun pqShortName(st: StyleStop): String {
    val p = st.platform ?: return st.label
    if (com.shelf.launcher.data.isCollectionKey(p)) return st.label
    return com.shelf.launcher.data.ConsoleNames.label(p)
}

@Composable
private fun PqTool(pal: PqPalette, icon: ImageVector, label: String, lit: Boolean, size: Dp, onTap: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 2.dp).size(size)
            .then(if (lit) Modifier.pqGoldButton(pal, cut = 5.dp, lit = true) else Modifier.pqPanel(pal, cut = 5.dp, edge = 1.dp, studs = false, deep = true))
            .tapOnly(onTap),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = if (lit) pal.onAccent else pal.ink, modifier = Modifier.size(size * 0.56f))
    }
}

/** Battery (a small pixel battery) and the clock. */
@Composable
private fun PqStatus(pal: PqPalette, compact: Boolean) {
    val battery = rememberBatteryInfo()
    val time = rememberTwinTime(seconds = false)
    val context = LocalContext.current
    val clock = remember(time.longValue) {
        runCatching { android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(time.longValue)) }.getOrDefault("")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(if (compact) 16.dp else 19.dp, if (compact) 8.dp else 9.dp)) {
            pocketBattery(1.dp.toPx().coerceAtLeast(1f), battery.percent, if (battery.charging) pal.accent else pal.ink)
        }
        Spacer(Modifier.width(7.dp))
        Text(clock, color = pal.ink, fontSize = if (compact) 12.sp else 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

// ---- Browse: the showcase and the collection ----

@Composable
private fun PqBrowse(
    c: StyleCtx, pal: PqPalette, compact: Boolean,
    swap: Animatable<Float, AnimationVector1D>, flash: Animatable<Float, AnimationVector1D>
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val share = when (Features.pqSize.state.value) {
            Features.PqSize.SMALL -> 0.27f
            Features.PqSize.MEDIUM -> 0.33f
            Features.PqSize.LARGE -> 0.40f
        }
        val rowH = (maxHeight * share).coerceIn(72.dp, 280.dp)
        val g = c.game
        Column(Modifier.fillMaxSize()) {
            if (g == null) {
                PqEmpty(pal, Modifier.weight(1f).fillMaxWidth())
            } else {
                PqShowcase(c, g, pal, compact, Modifier.weight(1f).fillMaxWidth())
            }
            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
            PqCollection(c, pal, swap, flash, Modifier.fillMaxWidth().height(rowH))
        }
    }
}

@Composable
private fun PqEmpty(pal: PqPalette, modifier: Modifier) {
    Box(modifier.pqPanel(pal, cut = 14.dp, edge = 2.dp), contentAlignment = Alignment.Center) {
        Text(
            (if (Features.pqBumpers.state.value) "No games here.\nL1 / R1 or the tabs above for another console." else "No games here.\nL2 / R2 or the tabs above for another console."), color = pal.muted, fontSize = 14.sp,
            textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun PqShowcase(c: StyleCtx, g: Game, pal: PqPalette, compact: Boolean, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val artW = maxWidth * (if (compact) 0.33f else 0.36f)
        Row(Modifier.fillMaxSize()) {
            PqStage(c, g, pal, Modifier.width(artW).fillMaxHeight())
            Spacer(Modifier.width(if (compact) 8.dp else 14.dp))
            PqInfoPanel(c, g, pal, compact, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** The game you're on, big, in a gold-cornered frame with ‹ ›; it slides in from the side you moved to. */
@Composable
private fun PqStage(c: StyleCtx, g: Game, pal: PqPalette, modifier: Modifier) {
    val reduced = pqReduced()
    val tilt = Features.pqTilt.state.value && !reduced && !LocalLiteEffects.current
    // Which way you moved (for the slide).
    val dir = remember { intArrayOf(c.focus, 1) }
    if (dir[0] != c.focus) { dir[1] = if (c.focus > dir[0]) 1 else -1; dir[0] = c.focus }
    val way = dir[1]
    val live by rememberUpdatedState(c)
    BoxWithConstraints(modifier) {
        val arrowW = if (maxWidth < 200.dp) 10.dp else 14.dp
        val boxW = (maxWidth - arrowW * 2 - 10.dp).coerceAtLeast(40.dp)
        val boxH = (maxHeight - 14.dp).coerceAtLeast(40.dp)
        // A pool of light under it.
        Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.82f).height(16.dp)) {
            drawOval(Brush.radialGradient(listOf(pal.accent.copy(alpha = 0.32f), Color.Transparent)))
        }
        // Gold corner brackets round its place.
        Canvas(Modifier.align(Alignment.Center).size(boxW + 8.dp, boxH + 6.dp)) {
            val l = minOf(size.width, size.height) * 0.12f
            val t = 2.dp.toPx()
            val o = pal.accent
            val d = pal.edgeDark
            for (k in 0 until 4) {
                val x = if (k % 2 == 0) 0f else size.width
                val y = if (k < 2) 0f else size.height
                val sx = if (k % 2 == 0) 1f else -1f
                val sy = if (k < 2) 1f else -1f
                val left = if (sx > 0) x else x - l
                val top = if (sy > 0) y else y - t
                drawRect(d, Offset(left - 1f, top - 1f), Size(l + 2f, t + 2f))
                drawRect(o, Offset(left, top), Size(l, t))
                val left2 = if (sx > 0) x else x - t
                val top2 = if (sy > 0) y else y - l
                drawRect(d, Offset(left2 - 1f, top2 - 1f), Size(t + 2f, l + 2f))
                drawRect(o, Offset(left2, top2), Size(t, l))
            }
        }
        AnimatedContent(
            targetState = g, label = "pqStage",
            transitionSpec = {
                if (reduced) fadeIn(tween(110)) togetherWith fadeOut(tween(80))
                else (slideInHorizontally(tween(pqMs(240), easing = FastOutSlowInEasing)) { w -> w / 5 * way } + fadeIn(tween(pqMs(200)))) togetherWith
                    (slideOutHorizontally(tween(pqMs(200))) { w -> -w / 6 * way } + fadeOut(tween(pqMs(150))))
            },
            modifier = Modifier.align(Alignment.Center).size(boxW, boxH)
        ) { shown ->
            Box(Modifier.fillMaxSize().tapOnly { pqOpenDetails(live) }, contentAlignment = Alignment.Center) {
                PqMediaBig(c, shown, tilt, Modifier.fillMaxSize())
            }
        }
        if (c.games.size > 1) {
            PqArrow(pal, right = false, Modifier.align(Alignment.CenterStart).size(arrowW, arrowW * 1.5f).tapOnly { pqStepSound(live, -1) })
            PqArrow(pal, right = true, Modifier.align(Alignment.CenterEnd).size(arrowW, arrowW * 1.5f).tapOnly { pqStepSound(live, 1) })
        }
    }
}

/** The games drawn as covers here: Layout options › Games shown as › Cover art, or the Spines look (a spine is too thin to show off). */
@Composable
private fun pqCovers(c: StyleCtx): Boolean = Features.pqMedia.state.value == Features.PqMedia.COVERS || spinesOn(c.type)

/** The game big, in your game look (its cartridge, disc, case, cover…) or its cover, fitted; with the gentle lean. */
@Composable
private fun PqMediaBig(c: StyleCtx, g: Game, tilt: Boolean, modifier: Modifier) {
    val covers = pqCovers(c)
    val aspect = if (covers) 0.72f else styleAspect(c.type).coerceIn(0.5f, 1.9f)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, maxHeight * aspect)
        val h = w / aspect
        Box(
            Modifier.size(w, h).graphicsLayer {
                if (tilt) {
                    val t = EffectClock.seconds.floatValue
                    rotationY = sin(t * 0.55f) * 6f
                    rotationX = sin(t * 0.4f + 1f) * 1.5f
                    cameraDistance = 14f * density
                    flatTextFor3d() // (Update 294: its lean never stops — drawn flat, then turned, Android 13's text cache)
                }
            }
        ) {
            if (covers) PqCover(c, g, Modifier.fillMaxSize()) else StyleArt(c, g, Modifier.fillMaxSize(), corner = 4.dp)
        }
    }
}

/** The plain cover (Cover art, or the Spines look): your picture, else the app's icon, else its name. */
@Composable
private fun PqCover(c: StyleCtx, g: Game, modifier: Modifier) {
    val titles = LocalTitleOptions.current
    val url = c.cover(g) ?: c.wide(g)
    // (0.6.15: its own layer, so the cut-corner clip is drawn once, not again on every frame the strip moves.)
    Box(modifier.gameOrigin(g.stableKey, g.stableKey == c.game?.stableKey).graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .clip(PqChamfer(4.dp)).background(placeholderBrush(g.hue))) {
        when {
            // (Update 202: a cover with its file's flat strips trimmed — TrimMargins.kt; Crop fills the chamfered card.)
            url != null -> AsyncImage(rememberStyleArtRequest(url, trim = url == c.cover(g)), g.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork())
            g.sourceType == com.shelf.launcher.data.SourceType.APP -> AppIconArt(g.packageName, wide = false, modifier = Modifier.fillMaxSize())
            else -> FullText(
                titles.display(g.name), color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, textAlign = TextAlign.Center,
                maxLines = 4, modifier = Modifier.align(Alignment.Center).padding(6.dp)
            )
        }
        ConsoleBadge(g.platform, packageName = g.packageName)
    }
}

/** The chamfered panel: name, facts, a few lines, Play / Details / Favourite; a screenshot and three small ones beside. */
@Composable
private fun PqInfoPanel(c: StyleCtx, g: Game, pal: PqPalette, compact: Boolean, modifier: Modifier) {
    val shotsOn = Features.pqScreenshots.state.value
    val descOn = Features.pqDescription.state.value
    val fade = if (pqReduced()) 100 else pqMs(200)
    Box(modifier.pqPanel(pal, cut = if (compact) 10.dp else 14.dp, edge = 2.dp)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = if (compact) 10.dp else 16.dp, vertical = if (compact) 8.dp else 14.dp)) {
            val shots = shotsOn && maxWidth >= 360.dp && maxHeight >= 120.dp
            val shotsW = maxWidth * 0.40f
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    AnimatedContent(
                        targetState = g, label = "pqInfo",
                        transitionSpec = { fadeIn(tween(fade)) togetherWith fadeOut(tween(fade * 2 / 3)) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) { shown -> PqInfoText(c, shown, pal, compact, descOn) }
                    Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                    PqPanelButtons(c, g, pal, compact)
                }
                if (shots) {
                    Spacer(Modifier.width(12.dp))
                    PqShots(c, g, pal, Modifier.width(shotsW).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun PqInfoText(c: StyleCtx, g: Game, pal: PqPalette, compact: Boolean, descOn: Boolean) {
    val titles = LocalTitleOptions.current
    val meta = c.state.metadata[g.stableKey]
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PqDiamond(pal, Modifier.size(if (compact) 7.dp else 9.dp))
            Spacer(Modifier.width(7.dp))
            FitText(
                titles.display(g.name), pal.ink, maxSize = if (compact) 17.sp else 25.sp, minSize = 11.sp, maxLines = 2,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
            )
        }
        PqRule(pal, Modifier.padding(top = 3.dp, bottom = 5.dp).fillMaxWidth().height(2.dp))
        PqMetaLine(c, g, pal, compact)
        val summary = meta?.summary?.trim()?.takeIf { it.isNotEmpty() }
        if (descOn && summary != null) {
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            FullText(
                summary, color = pal.muted, fontSize = if (compact) 11.sp else 13.sp, lineHeight = if (compact) 14.sp else 18.sp,
                maxLines = if (compact) 2 else 4
            )
        }
    }
}

/** Console · year · genre on one line; play time, achievements and the favourite star on the next. */
@Composable
private fun PqMetaLine(c: StyleCtx, g: Game, pal: PqPalette, compact: Boolean) {
    val titles = LocalTitleOptions.current
    val meta = c.state.metadata[g.stableKey]
    val size = if (compact) 11.sp else 13.sp
    val icon = if (compact) 13.dp else 15.dp
    val facts = buildList {
        add(titles.console(g.platform))
        meta?.releaseYear?.let { add(it.toString()) }
        meta?.genres?.firstOrNull()?.let { add(it) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        PqStopIcon(StyleStop(null, g.platform, titles.console(g.platform), null), pal.accent, icon)
        Spacer(Modifier.width(6.dp))
        FullText(facts.joinToString("  ·  "), color = pal.ink.copy(alpha = 0.9f), fontSize = size, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
    Spacer(Modifier.height(3.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Schedule, null, tint = pal.accent, modifier = Modifier.size(icon))
        Spacer(Modifier.width(4.dp))
        Text(stylePlaytime(c.playtime(g), c.lastPlayed(g) != null), color = pal.muted, fontSize = size, maxLines = 1, softWrap = false)
        c.progress(g)?.let { (earned, total) ->
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Filled.EmojiEvents, null, tint = if (earned >= total) com.shelf.launcher.ui.theme.Ds.gold else pal.accent, modifier = Modifier.size(icon))
            Spacer(Modifier.width(4.dp))
            Text("$earned / $total", color = pal.muted, fontSize = size, maxLines = 1, softWrap = false)
        }
        if (g.stableKey in c.state.favorites) {
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Filled.Star, "Favourite", tint = pal.accent, modifier = Modifier.size(icon))
        }
    }
}

/** A small pixel diamond (the heading's mark). */
@Composable
private fun PqDiamond(pal: PqPalette, modifier: Modifier) {
    Canvas(modifier) {
        pqPixelArt(arrayOf("..#..", ".#o#.", "#o+o#", ".#+#.", "..#.."), pal.edgeDark, pal.accent, pal.accentLight)
    }
}

/** A dashed pixel rule under the title: accent dashes fading out to the right. */
@Composable
private fun PqRule(pal: PqPalette, modifier: Modifier) {
    Canvas(modifier) {
        val step = 6.dp.toPx()
        var x = 0f
        while (x < size.width) {
            val a = (1f - x / size.width).coerceIn(0.1f, 1f)
            drawRect(pal.accent.copy(alpha = a), Offset(x, 0f), Size(step * 0.6f, size.height))
            x += step
        }
    }
}

/** Play (gold, with the A glyph), Details and the favourite star: the panel's buttons (▲ from the games). */
@Composable
private fun PqPanelButtons(c: StyleCtx, g: Game, pal: PqPalette, compact: Boolean) {
    val live by rememberUpdatedState(c)
    val inShow = PqNav.page == PqNav.Page.BROWSE && PqNav.zone == PqNav.Zone.SHOW
    val h = if (compact) 34.dp else 46.dp
    val resumed = c.lastPlayed(g) != null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val playOn = inShow && PqNav.index == 0
        Box(Modifier.weight(1f, fill = false).widthIn(min = 120.dp, max = 300.dp)) {
            Row(
                Modifier.fillMaxWidth().height(h)
                    .graphicsLayer { val s = if (playOn) 1.03f else 1f; scaleX = s; scaleY = s }
                    .pqGoldButton(pal, lit = playOn)
                    .tapOnly { PqNav.zone = PqNav.Zone.SHOW; PqNav.index = 0; live.game?.let { pqLaunch(live, it) } }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PqKeyGlyph("A", pal, if (compact) 20.dp else 26.dp)
                Spacer(Modifier.width(9.dp))
                Text(
                    if (resumed) "Continue" else "Play Game", color = pal.onAccent, fontSize = if (compact) 14.sp else 19.sp,
                    // 0.6.15: shrinks to fit when the screenshots column takes room (Thor top screen cut it to "Play Ga…").
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = if (compact) 14.sp else 19.sp),
                    fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
                )
            }
            if (playOn) PqCursor(pal, Modifier.align(Alignment.CenterStart).offset(x = (-15).dp).size(9.dp, 12.dp))
        }
        Spacer(Modifier.width(8.dp))
        PqSquareButton(pal, Icons.Filled.Info, "Details", inShow && PqNav.index == 1, h) {
            PqNav.zone = PqNav.Zone.SHOW; PqNav.index = 1; pqOpenDetails(live)
        }
        Spacer(Modifier.width(6.dp))
        val fav = g.stableKey in c.state.favorites
        PqSquareButton(pal, if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, "Favourite", inShow && PqNav.index == 2, h) {
            PqNav.zone = PqNav.Zone.SHOW; PqNav.index = 2; live.game?.let { live.actions.favorite(it) }
        }
    }
}

@Composable
private fun PqSquareButton(pal: PqPalette, icon: ImageVector, label: String, lit: Boolean, size: Dp, onTap: () -> Unit) {
    Box(
        Modifier.size(size)
            .then(if (lit) Modifier.pqGoldButton(pal, cut = 6.dp, lit = true) else Modifier.pqPanel(pal, cut = 6.dp, edge = 1.dp, deep = true, studs = false))
            .tapOnly(onTap),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = if (lit) pal.onAccent else pal.accent, modifier = Modifier.size(size * 0.5f))
    }
}

/** A face button as printed on your controller (the A glyph on Play, the hints). */
@Composable
private fun PqKeyGlyph(symbol: String, pal: PqPalette, size: Dp) {
    val label = ButtonGlyphStyle.relabel(symbol)
    val wide = label.length > 1
    Box(
        Modifier.height(size).then(if (wide) Modifier.widthIn(min = size) else Modifier.width(size))
            .clip(if (wide) RoundedCornerShape(50) else CircleShape).background(pal.edgeDark)
            .border(1.dp, pal.accentLight.copy(alpha = 0.8f), if (wide) RoundedCornerShape(50) else CircleShape)
            .padding(horizontal = if (wide) 6.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = pal.accentLight, fontSize = (size.value * (if (wide) 0.42f else 0.6f)).sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/** A screenshot (its slideshow while you rest on the game) and three small ones under it (a tap shows that one big). */
@Composable
private fun PqShots(c: StyleCtx, g: Game, pal: PqPalette, modifier: Modifier) {
    val shots = c.state.screenshots[g.stableKey].orEmpty()
    val reduced = pqReduced()
    var pick by remember(g.stableKey) { mutableIntStateOf(0) }
    val count = shots.size.coerceAtMost(4)
    LaunchedEffect(g.stableKey, count, reduced) {
        if (count < 2 || reduced) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(4200)
            if (!HomeIdle.calm) pick = (pick + 1) % count
        }
    }
    val big = shots.getOrNull(pick) ?: c.backdrop(g)
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth().pqPanel(pal, cut = 6.dp, edge = 1.dp, deep = true, studs = false).padding(4.dp).clip(PqChamfer(4.dp))) {
            Crossfade(targetState = big, animationSpec = tween(if (reduced) 90 else pqMs(280)), label = "pqShot") { u ->
                if (u != null) AsyncImage(u, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().background(placeholderBrush(g.hue)))
            }
        }
        if (count > 1) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().aspectRatio(3f * 1.45f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val others = (0 until count).filter { it != pick }.take(3)
                for (k in 0 until 3) {
                    val i = others.getOrNull(k)
                    Box(
                        Modifier.weight(1f).fillMaxHeight().pqPanel(pal, cut = 4.dp, edge = 1.dp, deep = true, studs = false).padding(3.dp)
                            .then(if (i != null) Modifier.tapOnly { pick = i } else Modifier)
                    ) {
                        if (i != null) AsyncImage(shots[i], null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(PqChamfer(3.dp)))
                    }
                }
            }
        }
    }
}

/**
 * The collection: the console's games along the bottom as their media, gliding to keep the one you're on in the middle,
 * where it stands raised and a little bigger in a fixed gold frame under the moving arrow. Only the games on screen are
 * composed; the glide is read while drawing. Swipe to move; tap a game to pick it, tap it again for its window.
 */
@Composable
private fun PqCollection(
    c: StyleCtx, pal: PqPalette, swap: Animatable<Float, AnimationVector1D>, flash: Animatable<Float, AnimationVector1D>, modifier: Modifier
) {
    val n = c.games.size
    val keys = remember(c.games) { gameKeys(c.games) }
    val reduced = pqReduced()
    val covers = pqCovers(c)
    val aspect = if (covers) 0.72f else styleAspect(c.type).coerceIn(0.55f, 1.8f)
    val loop = loopRowOn(c)
    val vFocus = rememberLoopFocus(c)
    val pos = remember { Animatable(vFocus.toFloat()) }
    val firstKey = keys.firstOrNull()
    val shownList = remember { arrayOfNulls<Any>(2) }
    val stiffness = when (Features.pqSpeed.state.value) {
        Features.PqSpeed.RELAXED -> 200f
        Features.PqSpeed.NORMAL -> 360f
        Features.PqSpeed.SNAPPY -> 700f
    }
    LaunchedEffect(vFocus, n, firstKey) {
        val target = vFocus.toFloat()
        val newList = shownList[0] != firstKey || shownList[1] != n
        shownList[0] = firstKey
        shownList[1] = n
        if (reduced || newList || abs(pos.value - target) > 10f) pos.snapTo(target)
        else pos.animateTo(target, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = stiffness))
    }
    val lo by remember { derivedStateOf { floor(pos.value).toInt() } }
    val live by rememberUpdatedState(c)
    val density = LocalDensity.current
    Box(modifier.pqPanel(pal, cut = 12.dp, edge = 1.5.dp, deep = true, alpha = 0.88f)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 4.dp).clipToBounds()
                .pointerInput(Unit) {
                    var acc = 0f
                    detectHorizontalDragGestures(onDragEnd = { acc = 0f }, onDragCancel = { acc = 0f }) { ch, dx ->
                        ch.consume()
                        acc += dx
                        val step = 56.dp.toPx()
                        while (acc <= -step) { acc += step; pqStepSound(live, 1) }
                        while (acc >= step) { acc -= step; pqStepSound(live, -1) }
                    }
                }
        ) {
            // (Room above the one you're on for its lift, its bigger size and the arrow.)
            val itemH = maxHeight * 0.70f
            val itemW = itemH * aspect
            val gap = (itemW * 0.16f).coerceIn(8.dp, 22.dp)
            val pitchPx = with(density) { (itemW + gap).toPx() }
            val centerPx = with(density) { (maxWidth / 2).toPx() }
            val itemWPx = with(density) { itemW.toPx() }
            val liftPx = with(density) { (maxHeight * 0.05f).toPx() }
            val bottomPad = maxHeight * 0.06f
            val side = ((maxWidth / (itemW + gap)) / 2f).toInt() + 2
            val halfW = with(density) { (maxWidth / 2).toPx() }.coerceAtLeast(1f)
            // The gold frame in the middle (the one you're on stands in it).
            if (n > 0) PqSelector(
                pal, liftPx,
                Modifier.align(Alignment.BottomCenter).padding(bottom = (bottomPad - 5.dp).coerceAtLeast(0.dp)).size(itemW * 1.12f + 12.dp, itemH * 1.12f + 10.dp)
            )
            Box(
                Modifier.fillMaxSize().edgeBump().graphicsLayer {
                    val s = swap.value
                    translationX = s * 70.dp.toPx()
                    alpha = (1f - abs(s) * 0.85f).coerceIn(0f, 1f)
                }
            ) {
                val first = if (loop) lo - side else (lo - side).coerceAtLeast(0)
                val last = if (loop) lo + side + 1 else (lo + side + 1).coerceAtMost(n - 1)
                // (The range is clamped to the games without Endless; with it, every place stands for a game.)
                // (Update 246, #60: the places picked before the loop — a `continue` in it made Compose give each pass its
                // own group, so a step that moved every game one place along threw them all away and built them again.)
                for (i in (first..last).filter { i -> !(loop && !drawnOnce(i - lo, n)) }) {
                    // (Update 218, #105: going round a short list, each game once — no copies of the same game at the sides.)
                    val real = if (loop) i.mod(n) else i
                    key(if (loop) "${keys[real]}#${Math.floorDiv(i, n)}" else keys[real]) {
                        PqSlot(
                            c, i, real, i == vFocus, pos, flash, covers, pitchPx, centerPx, itemWPx, liftPx, halfW,
                            Modifier.align(Alignment.BottomStart).padding(bottom = bottomPad).size(itemW, itemH)
                        )
                    }
                }
            }
            // The arrow over the one you're on.
            if (n > 0) PqCursor(pal, Modifier.align(Alignment.TopCenter).size(10.dp, 13.dp), down = true)
        }
        if (n > 1) {
            PqArrow(pal, right = false, Modifier.align(Alignment.CenterStart).padding(start = 6.dp).size(11.dp, 17.dp).tapOnly { pqStepSound(live, -1) })
            PqArrow(pal, right = true, Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(11.dp, 17.dp).tapOnly { pqStepSound(live, 1) })
        }
    }
}

/** One game in the collection, placed while drawing from the glide ([pos]): lifted, bigger and lit when it's the one you're on. */
@Composable
private fun PqSlot(
    c: StyleCtx, i: Int, real: Int, selected: Boolean, pos: Animatable<Float, AnimationVector1D>, flash: Animatable<Float, AnimationVector1D>,
    covers: Boolean, pitchPx: Float, centerPx: Float, itemWPx: Float, liftPx: Float, halfW: Float, modifier: Modifier
) {
    val g = c.games[real]
    val lift = animateFloatAsState(if (selected) 1f else 0f, tween(pqMs(170)), label = "pqLift")
    val launching = PqNav.launching == g.stableKey
    val live by rememberUpdatedState(c)
    Box(
        modifier
            .zIndex(if (selected) 2f else 0f)
            .graphicsLayer {
                val x = centerPx + (i - pos.value) * pitchPx - itemWPx / 2f
                translationX = x
                val l = lift.value
                val f = if (launching) flash.value else 0f
                val s = 1f + 0.12f * l + 0.24f * f
                scaleX = s; scaleY = s
                transformOrigin = TransformOrigin(0.5f, 1f)
                translationY = -liftPx * l
                // The far ends fade into the tray.
                val d = abs(x + itemWPx / 2f - centerPx) / halfW
                alpha = (1.35f - d * 0.75f).coerceIn(0.3f, 1f)
            }
            .tapOnly {
                val cc = live
                PqNav.zone = PqNav.Zone.ROW
                if (real == cc.focus) cc.actions.open() else { PqNav.lastSet = -1; cc.actions.select(real) }
            }
    ) {
        if (covers) PqCover(c, g, Modifier.fillMaxSize()) else StyleArt(c, g, Modifier.fillMaxSize(), corner = 3.dp)
    }
}

/** The fixed gold frame in the middle of the collection (raised with the game standing in it), with a soft glow. */
@Composable
private fun PqSelector(pal: PqPalette, liftPx: Float, modifier: Modifier) {
    Box(
        modifier
            .graphicsLayer { translationY = -liftPx }
            .drawWithCache {
                val cut = 8.dp.toPx()
                val one = 1.dp.toPx().coerceAtLeast(1f)
                val p = pqChamferPath(size.width, size.height, cut, one * 2f)
                val wide = Stroke(width = one * 5f)
                val mid = Stroke(width = one * 2.5f)
                val thin = Stroke(width = one)
                val glow = Brush.radialGradient(listOf(pal.accent.copy(alpha = 0.28f), Color.Transparent), radius = size.maxDimension * 0.62f)
                val stud = 2.5.dp.toPx()
                onDrawBehind {
                    drawRect(glow)
                    drawPath(p, pal.edgeDark, style = wide)
                    drawPath(p, pal.accent, style = mid)
                    drawPath(p, pal.accentLight.copy(alpha = 0.7f), style = thin)
                    for (k in 0 until 4) {
                        val sx = if (k % 2 == 0) cut * 0.5f + one * 2f else size.width - cut * 0.5f - one * 2f
                        val sy = if (k < 2) cut * 0.5f + one * 2f else size.height - cut * 0.5f - one * 2f
                        drawRect(pal.edgeDark, Offset(sx - stud - one, sy - stud - one), Size(stud * 2f + 2f * one, stud * 2f + 2f * one))
                        drawRect(pal.accentLight, Offset(sx - stud, sy - stud), Size(stud * 2f, stud * 2f))
                    }
                }
            }
    )
}

// ---- Select a System ----

@Composable
private fun PqSystems(c: StyleCtx, pal: PqPalette, compact: Boolean) {
    val counts = remember(c.state.games) { c.state.games.groupingBy { it.platform }.eachCount() }
    val stops = c.stops
    val live by rememberUpdatedState(c)
    Column(Modifier.fillMaxSize().pqPanel(pal, cut = 16.dp, edge = 2.dp).padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp)) {
        PqHeader("Select a System", pal, compact)
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
        if (stops.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Only one category here — add consoles in Settings › Library.", color = pal.muted, fontSize = 13.sp)
            }
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val cardW = if (compact) 96.dp else 128.dp
                val cols = (maxWidth / (cardW + 10.dp)).toInt().coerceIn(2, 9)
                SideEffect { PqNav.sysCols = cols }
                val grid = rememberLazyGridState(initialFirstVisibleItemIndex = (PqNav.sysCursor / cols * cols).coerceIn(0, (stops.size - 1).coerceAtLeast(0)))
                val cursor = PqNav.sysCursor.coerceIn(0, stops.size - 1)
                LaunchedEffect(cursor) {
                    val info = grid.layoutInfo
                    val seen = info.visibleItemsInfo.firstOrNull { it.index == cursor }
                    val whole = seen != null && seen.offset.y >= 0 && seen.offset.y + seen.size.height <= info.viewportEndOffset
                    if (!whole) runCatching { grid.animateScrollToItem((cursor / cols * cols - cols).coerceAtLeast(0)) }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols), state = grid, modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                ) {
                    items(stops.size) { i ->
                        val st = stops[i]
                        val count = when {
                            i == c.stop -> c.games.size
                            st.special == null && st.platform == null -> c.state.games.size
                            st.special == null && st.platform != null && !com.shelf.launcher.data.isCollectionKey(st.platform) -> counts[st.platform]
                            else -> null
                        }
                        PqSystemCard(pal, st, count, lit = i == cursor, here = i == c.stop, compact = compact) {
                            if (PqNav.sysCursor == i) pqPickSystem(live, i) else { PqNav.sysCursor = i; pqBeep(UiSound.MOVE) }
                        }
                    }
                }
            }
        }
        PqHints(pal, compact, listOf("A" to "Choose", "B" to "Back", "SELECT" to "Sort & filter", (if (Features.pqBumpers.state.value) "L1 / R1" else "L2 / R2") to "Console"))
    }
}

@Composable
private fun PqSystemCard(pal: PqPalette, st: StyleStop, count: Int?, lit: Boolean, here: Boolean, compact: Boolean, onTap: () -> Unit) {
    val lift = animateFloatAsState(if (lit) 1f else 0f, tween(pqMs(150)), label = "pqSysLift")
    Box {
        Column(
            Modifier.fillMaxWidth().aspectRatio(0.92f)
                .graphicsLayer {
                    val l = lift.value
                    translationY = -4.dp.toPx() * l
                    val s = 1f + 0.05f * l
                    scaleX = s; scaleY = s
                }
                .pqPanel(pal, cut = 9.dp, edge = if (lit) 2.dp else 1.dp, lit = lit, deep = !lit && !here, studs = lit)
                .tapOnly(onTap)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val p = st.platform
                if (p != null && !com.shelf.launcher.data.isCollectionKey(p)) {
                    PlatformLogo(p, st.label, Modifier.fillMaxWidth(0.86f).fillMaxHeight(0.8f), textColor = pal.ink, textSize = if (compact) 11.sp else 13.sp)
                } else Icon(st.icon, null, tint = if (lit) pal.accentLight else pal.accent, modifier = Modifier.fillMaxSize(0.55f))
            }
            FullText(
                pqShortName(st), color = pal.ink, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, textAlign = TextAlign.Center
            )
            Text(
                when { count == null -> if (here) "You're here" else " "; count == 1 -> "1 Game"; else -> "$count Games" },
                color = if (lit || here) pal.accent else pal.muted, fontSize = if (compact) 10.sp else 11.sp, maxLines = 1, softWrap = false
            )
        }
        if (lit) PqCursor(pal, Modifier.align(Alignment.TopCenter).offset(y = (-12).dp).size(9.dp, 12.dp), down = true)
    }
}

/** A window's title on a small gold-edged plate, in the pixel heading type. */
@Composable
private fun PqHeader(text: String, pal: PqPalette, compact: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.pqPanel(pal, cut = 6.dp, edge = 1.5.dp, studs = false).padding(horizontal = 18.dp, vertical = if (compact) 4.dp else 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = pal.ink, fontFamily = pocketFont(), fontSize = if (compact) 12.sp else 15.sp, maxLines = 1, softWrap = false)
        }
    }
}

/** The button hints along a window's foot. */
@Composable
private fun PqHints(pal: PqPalette, compact: Boolean, hints: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        hints.forEachIndexed { k, (key, word) ->
            if (k > 0) Spacer(Modifier.width(14.dp))
            PqKeyGlyph(key, pal, if (compact) 16.dp else 18.dp)
            Spacer(Modifier.width(5.dp))
            Text(word, color = pal.muted, fontSize = if (compact) 10.sp else 12.sp, maxLines = 1, softWrap = false)
        }
    }
}

// ---- Game details ----

/** One line of the details list. */
internal class PqAction(val label: String, val icon: ImageVector, val sub: String?, val run: () -> Unit)

/** The details list's lines for [g] — the same handlers as the other layouts' details and game window. */
internal fun pqDetailActions(c: StyleCtx, g: Game): List<PqAction> = buildList {
    add(PqAction(if (c.lastPlayed(g) != null) "Continue" else "Play Game", Icons.Filled.PlayArrow, null) { pqLaunch(c, g) })
    add(PqAction("View Details", Icons.Filled.Info, "Everything about it") { c.actions.details() })
    add(PqAction("Game Options", Icons.Filled.Tune, "Emulator, artwork, collections…") { c.actions.options(g) })
    val prog = c.progress(g)
    add(PqAction("View Achievements", Icons.Filled.EmojiEvents, prog?.let { "${it.first} / ${it.second}" } ?: if (g.raGameId == null) "Not linked yet" else null) { c.actions.achievements(g) })
    val running = g.stableKey in com.shelf.launcher.data.RunningGames.keys
    val fav = g.stableKey in c.state.favorites
    add(PqAction(
        when { running -> "Close the Running Game"; fav -> "Remove from Favourites"; else -> "Add to Favourites" },
        if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, if (running) "It's still open in the background" else null
    ) { c.actions.favorite(g) })
    add(PqAction("Manage Save Data", Icons.Filled.SdCard, "Back up, sync, restore") { c.actions.sync(g) })
    c.shortcuts.firstOrNull { it.label == com.shelf.launcher.data.SAVES_NAME }?.let { sc ->
        add(PqAction(com.shelf.launcher.data.SAVES_NAME, Icons.Filled.CloudSync, "Every save, every game") { sc.onClick() })
    }
    add(PqAction("Game Window", Icons.Filled.OpenInNew, "The quick window with its own pages") { c.actions.open() })
}

@Composable
private fun PqDetails(c: StyleCtx, pal: PqPalette, compact: Boolean) {
    val g = c.game
    if (g == null) {
        PqEmpty(pal, Modifier.fillMaxSize())
    } else {
        val reduced = pqReduced()
        val tilt = Features.pqTilt.state.value && !reduced && !LocalLiteEffects.current
        val actions = pqDetailActions(c, g)
        val cursor = PqNav.detCursor.coerceIn(0, actions.size - 1)
        val shots = c.state.screenshots[g.stableKey].orEmpty()
        val live by rememberUpdatedState(c)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val showShots = Features.pqScreenshots.state.value && shots.isNotEmpty() && maxWidth >= 600.dp
            Row(Modifier.fillMaxSize()) {
                // The picture.
                Box(
                    Modifier.weight(0.3f).fillMaxHeight().pqPanel(pal, cut = 12.dp, edge = 2.dp).padding(14.dp)
                        .tapOnly { live.game?.let { live.actions.open() } },
                    contentAlignment = Alignment.Center
                ) { PqMediaBig(c, g, tilt, Modifier.fillMaxSize()) }
                Spacer(Modifier.width(10.dp))
                // Its facts and the list.
                Column(Modifier.weight(0.44f).fillMaxHeight().pqPanel(pal, cut = 12.dp, edge = 2.dp).padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp)) {
                    val titles = LocalTitleOptions.current
                    FitText(titles.display(g.name), pal.ink, maxSize = if (compact) 16.sp else 22.sp, minSize = 11.sp, maxLines = 2, fontWeight = FontWeight.Bold)
                    PqRule(pal, Modifier.padding(top = 3.dp, bottom = 5.dp).fillMaxWidth().height(2.dp))
                    PqMetaLine(c, g, pal, compact)
                    val summary = c.state.metadata[g.stableKey]?.summary?.trim()?.takeIf { it.isNotEmpty() }
                    if (Features.pqDescription.state.value && summary != null) {
                        Spacer(Modifier.height(5.dp))
                        FullText(summary, color = pal.muted, fontSize = if (compact) 10.sp else 12.sp, lineHeight = if (compact) 13.sp else 16.sp, maxLines = if (compact) 2 else 3)
                    }
                    Spacer(Modifier.height(if (compact) 5.dp else 8.dp))
                    val list = rememberLazyListState()
                    LaunchedEffect(cursor) {
                        val info = list.layoutInfo
                        val seen = info.visibleItemsInfo.firstOrNull { it.index == cursor }
                        val whole = seen != null && seen.offset >= info.viewportStartOffset && seen.offset + seen.size <= info.viewportEndOffset
                        if (!whole) runCatching { list.animateScrollToItem((cursor - 1).coerceAtLeast(0)) }
                    }
                    LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(actions.size) { i ->
                            PqActionRow(pal, actions[i], lit = i == cursor, compact = compact) {
                                PqNav.detCursor = i
                                actions[i].run()
                            }
                        }
                    }
                    PqHints(pal, compact, listOf("A" to "Select", "B" to "Back", "◀ ▶" to "Game", "X" to "Favourite"))
                }
                if (showShots) {
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(0.26f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (k in 0 until 3) {
                            val url = shots.getOrNull(k)
                            Box(Modifier.weight(1f).fillMaxWidth().pqPanel(pal, cut = 8.dp, edge = 1.5.dp, deep = true, studs = k == 0).padding(5.dp)) {
                                if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(PqChamfer(5.dp)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PqActionRow(pal: PqPalette, a: PqAction, lit: Boolean, compact: Boolean, onTap: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(start = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().height(if (compact) 28.dp else 34.dp)
                .then(if (lit) Modifier.pqGoldButton(pal, cut = 6.dp) else Modifier.pqPanel(pal, cut = 6.dp, edge = 1.dp, deep = true, studs = false, alpha = 0.8f))
                .tapOnly(onTap)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(a.icon, null, tint = if (lit) pal.onAccent else pal.accent, modifier = Modifier.size(if (compact) 14.dp else 17.dp))
            Spacer(Modifier.width(8.dp))
            FullText(
                a.label, color = if (lit) pal.onAccent else pal.ink, fontSize = if (compact) 12.sp else 14.sp,
                fontWeight = if (lit) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            a.sub?.let {
                Text(it, color = if (lit) pal.onAccent.copy(alpha = 0.75f) else pal.muted, fontSize = if (compact) 10.sp else 11.sp, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(6.dp))
            }
            Text("›", color = if (lit) pal.onAccent else pal.accent, fontSize = if (compact) 14.sp else 17.sp, fontWeight = FontWeight.Bold)
        }
        if (lit) PqCursor(pal, Modifier.align(Alignment.CenterStart).offset(x = (-14).dp).size(9.dp, 12.dp))
    }
}

// ---- What the controller and taps do ----

/** Plays [s] as Pocket Quest's own blip (Layout options › Retro sounds), else Voltcap's sound. */
internal fun pqBeep(s: UiSound) {
    if (!PqSynth.play(s)) PocketSounds.play?.invoke(s)
}

/** Moves [d] games (round the ends with Endless on). False when there's nowhere to go. */
internal fun pqStep(c: StyleCtx, d: Int): Boolean {
    val n = c.games.size
    if (n == 0) return false
    val f = (if (PqNav.lastSet >= 0 && PqNav.lastSetGames === c.games) PqNav.lastSet else c.focus).coerceIn(0, n - 1)
    var next = f + d
    if (next !in 0 until n) {
        if (StyleLoop.on && n >= 2) next = next.mod(n) else return false
    }
    if (next == f) return false
    PqNav.lastSet = next
    PqNav.lastSetGames = c.games
    c.actions.select(next)
    return true
}

/** [pqStep] with its sound, or the knock at the end. */
internal fun pqStepSound(c: StyleCtx, d: Int) {
    if (pqStep(c, d)) pqBeep(UiSound.MOVE) else EdgeBump.hit(d)
}

/** Starts [g]: the item grows and the screen flashes briefly, then Voltcap's usual start (its own animation, sounds…). */
internal fun pqLaunch(c: StyleCtx, g: Game) {
    if (PqNav.launching != null) return
    if (Features.pqReducedMotion.value) { c.actions.play(g); return }
    PqNav.launching = g.stableKey
    PqNav.launchTick++
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        PqNav.launching = null
        c.actions.play(g)
    }, pqMs(230).toLong())
}

internal fun pqOpenSystems(c: StyleCtx) {
    PqNav.sysCursor = c.stop.coerceAtLeast(0)
    PqNav.page = PqNav.Page.SYSTEMS
    pqBeep(UiSound.OPEN)
}

internal fun pqOpenDetails(c: StyleCtx) {
    if (c.game == null) return
    PqNav.detCursor = 0
    PqNav.page = PqNav.Page.DETAILS
    pqBeep(UiSound.OPEN)
}

internal fun pqBackToBrowse() {
    PqNav.page = PqNav.Page.BROWSE
    PqNav.zone = PqNav.Zone.ROW
    pqBeep(UiSound.BACK)
}

/** Select a System › A: jumps to that console (as many L2 / R2 steps as it is away) and back to the games. */
internal fun pqPickSystem(c: StyleCtx, i: Int) {
    val steps = pocketStepsTo(c, i)
    PqNav.page = PqNav.Page.BROWSE
    PqNav.zone = PqNav.Zone.ROW
    if (steps != 0) c.actions.category(steps) else pqBeep(UiSound.SELECT)
}

/** A on the top bar's tab / tool [i]. */
private fun pqTopPress(c: StyleCtx, i: Int) {
    val tabs = PqNav.tabCount
    when {
        i < tabs -> {
            val single = c.stop < 0 || c.stops.isEmpty()
            if (single) c.actions.category(1)
            else if (i != c.stop) c.actions.category(pocketStepsTo(c, i))
        }
        i == tabs -> pqOpenSystems(c)
        i == tabs + 1 -> c.actions.search()
        i == tabs + 2 -> c.actions.sort()
        i == tabs + 3 -> c.actions.quickMenu()
        else -> c.actions.settings()
    }
}

/**
 * Update 202: Pocket Quest's keys — all of them while it's on, before the home screen's own (StyleHomes.styleHomeKey).
 * True = handled. B on the games, L2 / R2, the stick clicks and the system keys stay the home screen's.
 */
internal fun pocketQuestKey(event: KeyEvent, reach: (Boolean) -> Boolean, sound: (UiSound) -> Unit): Boolean {
    val c = PqNav.ctx ?: return false
    val native = event.nativeKeyEvent
    val code = native.keyCode
    val down = event.type == KeyEventType.KeyDown
    val up = event.type == KeyEventType.KeyUp
    val first = native.repeatCount == 0
    val k = event.key
    fun beep(s: UiSound) { if (!PqSynth.play(s)) sound(s) }
    // Select: a short press opens Select a System (on it: sort & filter); held half a second, sort & filter straight away.
    if (code == android.view.KeyEvent.KEYCODE_BUTTON_SELECT) {
        if (down && first) PqNav.selectArmed = true
        if (up && PqNav.selectArmed) {
            PqNav.selectArmed = false
            if (PqNav.launching == null) {
                val held = native.eventTime - native.downTime >= 450L
                if (held || PqNav.page == PqNav.Page.SYSTEMS) c.actions.sort() else pqOpenSystems(c)
            }
        }
        return true
    }
    if (up && PqNav.keysDown.remove(code)) return true
    if (!down) return false
    fun take(): Boolean { PqNav.keysDown += code; return true }
    // While the launch flash plays, the buttons wait (a quarter of a second).
    if (PqNav.launching != null) return if (android.view.KeyEvent.isGamepadButton(code) || k == Key.DirectionLeft || k == Key.DirectionRight || k == Key.DirectionUp || k == Key.DirectionDown) take() else false
    val g = c.game
    // The same on every page.
    when (k) {
        Key.ButtonL1, Key.ButtonR1 -> if (Features.pqBumpers.value) {
            if (first) c.actions.category(if (k == Key.ButtonR1) 1 else -1)
            return take()
        } else return false
        Key.ButtonStart, Key.Menu -> { if (first) c.actions.quickMenu(); return take() }
        Key.ButtonX -> { if (first && g != null && PqNav.page != PqNav.Page.SYSTEMS) c.actions.favorite(g); return take() }
        else -> Unit
    }
    when (PqNav.page) {
        PqNav.Page.SYSTEMS -> {
            val n = c.stops.size
            if (n == 0) { if (k == Key.ButtonB || k == Key.Back || k == Key.ButtonA) { if (first) pqBackToBrowse(); return take() } }
            val cols = PqNav.sysCols.coerceAtLeast(1)
            val cur = PqNav.sysCursor.coerceIn(0, (n - 1).coerceAtLeast(0))
            val next = when (k) {
                Key.DirectionLeft -> cur - 1
                Key.DirectionRight -> cur + 1
                Key.DirectionUp -> if (cur - cols >= 0) cur - cols else { if (first) reach(true); return take() }
                Key.DirectionDown -> if (cur + cols < n) cur + cols else if (cur / cols < (n - 1) / cols) n - 1 else return take()
                Key.ButtonA, Key.Enter, Key.DirectionCenter -> { if (first && n > 0) pqPickSystem(c, cur); return take() }
                Key.ButtonB, Key.Back, Key.ButtonY -> { if (first) pqBackToBrowse(); return take() }
                else -> return false
            }
            val clamped = next.coerceIn(0, (n - 1).coerceAtLeast(0))
            if (clamped != cur) { PqNav.sysCursor = clamped; beep(UiSound.MOVE) }
            return take()
        }
        PqNav.Page.DETAILS -> {
            if (g == null) { if (first) pqBackToBrowse(); return take() }
            val list = pqDetailActions(c, g)
            val cur = PqNav.detCursor.coerceIn(0, list.size - 1)
            when (k) {
                Key.DirectionUp -> if (cur > 0) { PqNav.detCursor = cur - 1; beep(UiSound.MOVE) } else if (first) reach(true)
                Key.DirectionDown -> if (cur < list.size - 1) { PqNav.detCursor = cur + 1; beep(UiSound.MOVE) } else if (first) reach(false)
                Key.DirectionLeft, Key.DirectionRight -> pqStepSound(c, if (k == Key.DirectionRight) 1 else -1)
                Key.ButtonA, Key.Enter, Key.DirectionCenter -> if (first) list[cur].run()
                Key.ButtonB, Key.Back, Key.ButtonY -> if (first) pqBackToBrowse()
                else -> return false
            }
            return take()
        }
        PqNav.Page.BROWSE -> Unit
    }
    // The games, the panel's buttons and the top bar.
    val tabs = PqNav.tabCount
    val topN = tabs + PQ_TOOLS
    when (PqNav.zone) {
        PqNav.Zone.ROW -> when (k) {
            Key.DirectionLeft, Key.DirectionRight -> { pqStepSoundWith(c, if (k == Key.DirectionRight) 1 else -1, first, ::beep) }
            Key.DirectionUp -> if (first) {
                if (g != null) { PqNav.zone = PqNav.Zone.SHOW; PqNav.index = 0 } else { PqNav.zone = PqNav.Zone.TOP; PqNav.index = c.stop.coerceIn(0, topN - 1) }
                beep(UiSound.MOVE)
            }
            Key.DirectionDown -> if (first) reach(false)
            Key.ButtonA, Key.Enter, Key.DirectionCenter -> if (first && g != null) {
                if (Features.pqAPlays.value) pqLaunch(c, g) else c.actions.open()
            }
            Key.ButtonY -> if (first) pqOpenDetails(c)
            // B on the games: the home screen's own (back out to the categories, when that's on).
            else -> return false
        }
        PqNav.Zone.SHOW -> when (k) {
            Key.DirectionLeft -> if (PqNav.index > 0) { PqNav.index--; beep(UiSound.MOVE) }
            Key.DirectionRight -> if (PqNav.index < 2) { PqNav.index++; beep(UiSound.MOVE) }
            Key.DirectionUp -> if (first) { PqNav.zone = PqNav.Zone.TOP; PqNav.index = (if (c.stop >= 0 && c.stops.isNotEmpty()) c.stop else 0).coerceIn(0, topN - 1); beep(UiSound.MOVE) }
            Key.DirectionDown -> { PqNav.zone = PqNav.Zone.ROW; beep(UiSound.MOVE) }
            Key.ButtonA, Key.Enter, Key.DirectionCenter -> if (first && g != null) when (PqNav.index) {
                0 -> pqLaunch(c, g)
                1 -> pqOpenDetails(c)
                else -> c.actions.favorite(g)
            }
            Key.ButtonY -> if (first) pqOpenDetails(c)
            Key.ButtonB, Key.Back -> { PqNav.zone = PqNav.Zone.ROW; beep(UiSound.BACK) }
            else -> return false
        }
        PqNav.Zone.TOP -> when (k) {
            Key.DirectionLeft -> if (PqNav.index > 0) { PqNav.index--; beep(UiSound.MOVE) }
            Key.DirectionRight -> if (PqNav.index < topN - 1) { PqNav.index++; beep(UiSound.MOVE) }
            Key.DirectionUp -> if (first) reach(true)
            Key.DirectionDown -> { PqNav.zone = if (g != null) PqNav.Zone.SHOW else PqNav.Zone.ROW; PqNav.index = 0; beep(UiSound.MOVE) }
            Key.ButtonA, Key.Enter, Key.DirectionCenter -> if (first) pqTopPress(c, PqNav.index.coerceIn(0, topN - 1))
            Key.ButtonY -> if (first) pqOpenDetails(c)
            Key.ButtonB, Key.Back -> { PqNav.zone = PqNav.Zone.ROW; beep(UiSound.BACK) }
            else -> return false
        }
    }
    return take()
}

/** ◀ ▶ on the games: a step (with [beep]'s sound), or the knock at the end on a fresh press. */
private fun pqStepSoundWith(c: StyleCtx, d: Int, first: Boolean, beep: (UiSound) -> Unit) {
    if (pqStep(c, d)) beep(UiSound.MOVE) else if (first) EdgeBump.hit(d)
}

