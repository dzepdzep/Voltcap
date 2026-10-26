package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.Ps2BrowserStyle
import com.shelf.launcher.data.Ps2Icons
import com.shelf.launcher.data.SaveCapsules
import com.shelf.launcher.data.SourceType
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Update 330: Memory card (2000) — the games as save icons on a memory-card screen, in Voltcap's own drawing.
 *
 * A neutral grey (or grey-blue) screen lit from above; the category as the card's name top left with how many games are
 * on it, the game you're on top right (its save's own two-line title where it has one, in yellow) with its console,
 * time played and achievements under it. The games float in loose rows of small icons: the one you're on lifts,
 * brightens with a soft light behind it and turns (or sways); blue arrows show more rows above / below; button hints at
 * the bottom.
 *
 * Each icon is, in this order: the PS2 game's own save icon — its 3D model read (only read) from your memory cards
 * (Ps2Icons, the Save Vault's reader); else the game's icon / logo (your pick, the artwork's logo, the app's icon); else
 * a small disc printed with its cover (disc consoles) or a little box of its cover; else a plain memory-card symbol.
 * Nothing drawn here is ever called the save's icon unless it is (the game window says "Save icon from your memory card"
 * only then).
 *
 * D-pad / stick: ◀ ▶ ▲ ▼ through the icons (the rows scroll), A the game window, L2 / R2 the next card (category);
 * Endless goes round from the last icon to the first. Touch: tap an icon to pick it, tap it again to open it, drag up /
 * down to scroll.
 */

/** Update 330: how many icons a row holds right now (set as the layout draws, read by its key handler). */
internal object MemCardNav {
    @Volatile var cols: Int = 6
}

private val McYellow = Color(0xFFF6DA4E)
private val McArrow = Color(0xFF2E63E6)
private val McShadow = Shadow(Color.Black.copy(alpha = 0.75f), Offset(1.5f, 1.5f), 3f)

/** Update 330: the screen behind the icons (Layout options › Background; Your theme: nothing drawn here). */
@Composable
internal fun MemCardBackdrop(modifier: Modifier) {
    val back = Features.mcBack.state.value
    if (back == Features.McBack.THEME) return
    val blue = back == Features.McBack.BLUE
    val centre = if (blue) Color(0xFFC6CFDC) else Color(0xFFCBCCCF)
    val mid = if (blue) Color(0xFF8B96A8) else Color(0xFF909297)
    val edge = if (blue) Color(0xFF3C4556) else Color(0xFF444649)
    Canvas(modifier) {
        drawRect(Brush.radialGradient(listOf(centre, mid, edge), center = Offset(size.width * 0.5f, size.height * 0.38f), radius = max(size.width, size.height) * 0.78f))
        // The light from above, and a little shade at the floor.
        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.12f), 0.35f to Color.Transparent))
        drawRect(Brush.verticalGradient(0.72f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.2f)))
    }
}

internal fun memCardOwnsBackground(): Boolean = Features.mcBack.state.value != Features.McBack.THEME

/** Update 330: the Memory card layout. */
@Composable
internal fun StyleMemoryCard(c: StyleCtx, modifier: Modifier = Modifier) {
    val loop = loopRowOn(c)
    SideEffect {
        StyleNav.side = emptyList()
        StyleNav.top = emptyList()
        StyleNav.bar = emptyList()
        StyleLoop.on = loop
    }
    val context = LocalContext.current
    val saveIcons = Features.mcSaveIcons.state.value
    // The saves already on this device (read only, no network): their icons. Once per run, only with PS2 games around.
    LaunchedEffect(saveIcons, c.state.games.size) {
        if (saveIcons && c.state.games.any { Ps2BrowserStyle.isPs2(it.platform) }) {
            runCatching { SaveCapsules.refreshLocal(context, c.state.games, c.state.lastPlayedAt) }
        }
    }
    val caps by SaveCapsules.all.collectAsState()
    val refs = remember(caps, saveIcons) {
        if (!saveIcons) emptyMap() else caps.mapNotNull { cap ->
            if (!Ps2BrowserStyle.isPs2(cap.game.platform)) null else ps2RefsOf(cap).takeIf { it.isNotEmpty() }?.let { cap.game.stableKey to it }
        }.toMap()
    }
    // Plain if / else, never an early return out of the layout.
    if (c.games.isEmpty()) StyleEmpty(modifier) else MemCardScreen(c, refs, modifier)
}

/** One save icon, looked up: its 3D model and what its icon.sys says (title, colours). */
private class McSave(val mesh: Ps2IconMesh?, val sys: Ps2Icons.IconSys?)

/** The first readable save icon of [refs] (null refs: none). Cached by Ps2Icons; loaded off the main thread. */
@Composable
private fun rememberMcSave(refs: List<Ps2SaveRef>?): McSave? {
    if (refs.isNullOrEmpty()) return null
    val context = LocalContext.current
    val version = Ps2Icons.version.intValue
    val initial = remember(refs) { refs.firstNotNullOfOrNull { r -> Ps2Icons.cached(r.id)?.takeIf { it.icon != null } } }
    val parsed by produceState(initial, refs, version) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var firstSys: Ps2Icons.Parsed? = null
            for (r in refs.take(6)) {
                val p = runCatching { Ps2Icons.load(context, r.id) }.getOrNull()
                if (p?.icon != null) return@withContext p
                if (firstSys == null && p?.sys != null) firstSys = p
            }
            firstSys
        }
    }
    val icon = parsed?.icon
    val mesh = remember(icon) { icon?.let { runCatching { Ps2IconMesh(it) }.getOrNull() }?.takeIf { it.ok } }
    if (mesh == null && parsed?.sys == null) return null
    return McSave(mesh, parsed?.sys)
}

/** A stable small number per game (its loose place in the row). */
private fun mcJitter(key: String, salt: Int): Float {
    var h = salt * 31 + 7
    for (ch in key) h = h * 131 + ch.code
    return ((h ushr 1) % 1000) / 1000f - 0.5f
}

@Composable
private fun MemCardScreen(c: StyleCtx, refs: Map<String, List<Ps2SaveRef>>, modifier: Modifier) {
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val latestFocus by rememberUpdatedState(c.focus)
    val animate = rememberAnimationsEnabled()
    val lite = LocalLiteEffects.current
    val active = rememberIsActive()
    val windowOpen = GamePopup.game != null
    val sizePref = Features.mcSize.state.value
    val spacing = Features.mcSpacing.state.value
    val loose = Features.mcArrange.state.value == Features.McArrange.LOOSE
    val depth = Features.mcDepth.state.value
    val selMode = Features.mcSelected.state.value
    val showHints = Features.mcHints.state.value
    val floatOn = Features.mcFloat.state.value && animate && !lite && active && !windowOpen && !HomeIdle.calm
    val count = c.games.size
    val focus = c.focus.coerceIn(0, count - 1)
    val density = LocalDensity.current
    val clock = rememberGcClock(floatOn, 1f, 40)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val headerH = (maxHeight * 0.2f).coerceIn(64.dp, 108.dp)
        val hintsH = if (showHints) 30.dp else 10.dp
        val areaW = maxWidth
        val areaH = (maxHeight - headerH - hintsH).coerceAtLeast(60.dp)
        val cols = (areaW / sizePref.cell.dp).toInt().coerceIn(3, 12)
        SideEffect { MemCardNav.cols = cols }
        val cellW = areaW / cols
        val rowH = minOf(cellW * 0.82f, areaH)
        val visRows = floor(areaH / rowH).toInt().coerceAtLeast(1)
        val rows = (count + cols - 1) / cols
        val focusRow = focus / cols
        // The first row in view: moves only as far as needed to keep the icon you're on in view.
        val topHolder = remember { intArrayOf(0) }
        var top = topHolder[0]
        if (focusRow < top) top = focusRow
        if (focusRow > top + visRows - 1) top = focusRow - visRows + 1
        top = top.coerceIn(0, max(0, rows - visRows))
        topHolder[0] = top
        val scroll = remember { Animatable(top.toFloat()) }
        LaunchedEffect(top, animate) {
            if (animate && kotlin.math.abs(scroll.value - top) < 6f) scroll.animateTo(top.toFloat(), tween(280, easing = FastOutSlowInEasing))
            else scroll.snapTo(top.toFloat())
        }
        // A new card (category): the icons come in one after another.
        val intro = remember { Animatable(if (animate) 0f else 1f) }
        LaunchedEffect(c.categoryLabel, count, animate) {
            if (!animate) { intro.snapTo(1f); return@LaunchedEffect }
            intro.snapTo(0f)
            intro.animateTo(1f, tween(620, easing = LinearEasing))
        }
        val firstRow by remember(rows) { derivedStateOf { floor(scroll.value).toInt().coerceAtLeast(0) } }
        val iconSize = cellW * spacing.icon
        val rowHPx = with(density) { rowH.toPx() }

        MemCardHeader(c, refs[c.game?.stableKey], headerH)

        Box(
            Modifier.padding(top = headerH).width(areaW).height(areaH).clipToBounds()
                // The rows fade out at the top and bottom edges instead of being cut.
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val f = (18.dp.toPx() / size.height).coerceIn(0.01f, 0.3f)
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, f to Color.Black, 1f - f to Color.Black, 1f to Color.Transparent),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                    )
                }
                .pointerInput(cols, count) {
                    var acc = 0f
                    detectVerticalDragGestures(onDragStart = { acc = 0f }) { change, dy ->
                        change.consume()
                        acc += dy
                        val step = rowHPx * 0.8f
                        while (acc <= -step) { acc += step; (latestFocus + cols).takeIf { it < count }?.let { select(it) } }
                        while (acc >= step) { acc -= step; (latestFocus - cols).takeIf { it >= 0 }?.let { select(it) } }
                    }
                }
        ) {
            val from = (firstRow - 1).coerceAtLeast(0)
            val to = min(rows - 1, firstRow + visRows + 1)
            for (r in from..to) {
                for (col in 0 until cols) {
                    val i = r * cols + col
                    if (i >= count) break
                    val g = c.games[i]
                    key(g.stableKey) {
                        val dx = if (loose) mcJitter(g.stableKey, 1) * cellW.value * 0.09f else 0f
                        val dy = if (loose) mcJitter(g.stableKey, 2) * rowH.value * 0.16f else 0f
                        val order = ((r - firstRow).coerceAtLeast(0) * cols + col)
                        McCell(
                            c, g, i, selected = i == focus, refs = refs[g.stableKey], iconSize = iconSize, iconW = minOf(cellW * 0.8f, iconSize * 1.45f),
                            depth = depth, selMode = selMode, clock = clock, floatOn = floatOn,
                            intro = intro, introDelay = (order * 0.035f).coerceAtMost(0.6f),
                            modifier = Modifier
                                .offset { IntOffset(((col * cellW.value + dx) * density.density).roundToInt(), (((r - scroll.value) * rowH.value + dy) * density.density).roundToInt()) }
                                .size(cellW, rowH),
                            onTap = { if (latestFocus == i) open() else select(i) }
                        )
                    }
                }
            }
            // More rows above / below: the card's blue arrows.
            val moreAbove = top > 0
            val moreBelow = top + visRows < rows
            val pulse = if (animate && !lite) rememberInfiniteTransition(label = "mcArrow").animateFloat(
                0.55f, 1f, infiniteRepeatable(tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "mcArrowA"
            ).value else 1f
            if (moreAbove) McArrowMark(true, pulse, Modifier.align(Alignment.TopCenter).padding(top = 2.dp))
            if (moreBelow) McArrowMark(false, pulse, Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp))
        }
        if (showHints) McHints(Modifier.align(Alignment.BottomCenter).height(hintsH).padding(bottom = 6.dp))
    }
}

@Composable
private fun McArrowMark(up: Boolean, alpha: Float, modifier: Modifier) {
    Canvas(modifier.size(22.dp, 13.dp).graphicsLayer { this.alpha = alpha }) {
        val p = Path().apply {
            if (up) { moveTo(size.width / 2f, 0f); lineTo(size.width, size.height); lineTo(0f, size.height) }
            else { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height) }
            close()
        }
        drawPath(p, McArrow)
    }
}

/** Top left: the card (category) and how many games are on it. Top right: the game you're on. */
@Composable
private fun MemCardHeader(c: StyleCtx, refs: List<Ps2SaveRef>?, height: Dp) {
    val g = c.game
    val titles = LocalTitleOptions.current
    val save = if (Features.mcTitle.state.value) rememberMcSave(refs) else null
    val outline = TextStyle(shadow = McShadow)
    // The card's name is the layout's own category label (no second title over it); R1 › Categories › Off takes it away.
    val caption = CategoryTitle.captionOn(c.state.appearance)
    if (caption) CategoryTitle.Native(null)
    Row(Modifier.fillMaxWidth().height(height).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
        if (!caption) Spacer(Modifier.weight(0.45f)) else Row(Modifier.weight(0.45f), verticalAlignment = Alignment.CenterVertically) {
            GenericCardSymbol(Modifier.size(30.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(c.categoryLabel, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = outline)
                val n = c.games.size
                Text("$n ${if (n == 1) "Game" else "Games"}", color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, maxLines = 1, style = outline)
            }
        }
        if (Features.mcTitle.state.value && g != null) {
            Column(Modifier.weight(0.55f), horizontalAlignment = Alignment.End) {
                val l1 = save?.sys?.line1?.takeIf { it.isNotBlank() }
                val l2 = save?.sys?.line2?.takeIf { it.isNotBlank() }
                if (l1 != null) {
                    Text(l1, color = McYellow, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, style = outline)
                    if (l2 != null) Text(l2, color = McYellow, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, style = outline)
                } else {
                    Text(titles.display(g.name), color = McYellow, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, style = outline)
                }
                val bits = listOfNotNull(
                    titles.console(g.platform).takeIf { it.isNotBlank() },
                    c.playtime(g)?.let { stylePlaytime(it) },
                    c.progress(g)?.let { (e, t) -> "★ $e / $t" }
                )
                if (bits.isNotEmpty()) Text(bits.joinToString("  ·  "), color = Color.White, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, style = outline)
            }
        } else Spacer(Modifier.weight(0.55f))
    }
}

/** The few button hints along the bottom. */
@Composable
private fun McHints(modifier: Modifier) {
    val outline = TextStyle(shadow = McShadow)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        @Composable
        fun Hint(button: String, word: String) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(Color(0xFF2A2C31).copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                    Text(button, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp)
                }
                Spacer(Modifier.width(5.dp))
                Text(word, color = Color.White, fontSize = 12.sp, style = outline)
            }
        }
        Hint("A", "Enter")
        Hint("L2", "Previous card")
        Hint("R2", "Next card")
        Hint("R1", "Options")
    }
}

/** One icon's place: the light behind the one you're on, its shadow, the icon floating over it. */
@Composable
private fun McCell(
    c: StyleCtx, g: Game, index: Int, selected: Boolean, refs: List<Ps2SaveRef>?, iconSize: Dp, iconW: Dp,
    depth: Features.McDepth, selMode: Features.McSelected, clock: androidx.compose.runtime.FloatState, floatOn: Boolean,
    intro: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, introDelay: Float, modifier: Modifier, onTap: () -> Unit
) {
    val lift by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow), label = "mcLift")
    val phase = remember(g.stableKey) { (mcJitter(g.stableKey, 3) + 0.5f) * 6.28f }
    Box(
        modifier
            .graphicsLayer {
                val k = ((intro.value - introDelay) / 0.4f).coerceIn(0f, 1f)
                val bob = if (floatOn) sin(clock.floatValue * 1.4f + phase) * 2.2.dp.toPx() else 0f
                translationY = bob - lift * 7.dp.toPx() + (1f - k) * 26.dp.toPx()
                alpha = k
            }
            .pointerInput(index) { detectTapGestures(onTap = { onTap() }) },
        contentAlignment = Alignment.Center
    ) {
        // The soft light behind the icon you're on, and every icon's shadow on the floor.
        Canvas(Modifier.fillMaxSize()) {
            if (lift > 0.01f) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.5f * lift), Color.White.copy(alpha = 0.16f * lift), Color.Transparent),
                        center = center, radius = size.minDimension * 0.58f
                    ), radius = size.minDimension * 0.58f
                )
            }
            val sw = iconSize.toPx() * (0.62f - 0.1f * lift)
            drawOval(
                Color.Black.copy(alpha = 0.2f - 0.08f * lift),
                Offset(center.x - sw / 2f, center.y + iconSize.toPx() * 0.42f), Size(sw, iconSize.toPx() * 0.12f)
            )
        }
        Box(
            Modifier.size(iconW, iconSize)
                .graphicsLayer {
                    val s = 1f + 0.16f * lift
                    scaleX = s; scaleY = s
                    alpha = 0.86f + 0.14f * lift
                }
                .gameOrigin(g.stableKey, selected),
            contentAlignment = Alignment.Center
        ) { McIcon(c, g, refs, selected, depth, selMode, clock) }
    }
}

/** The icon itself, in the order the layout's notes give. */
@Composable
private fun McIcon(
    c: StyleCtx, g: Game, refs: List<Ps2SaveRef>?, selected: Boolean, depth: Features.McDepth,
    selMode: Features.McSelected, clock: androidx.compose.runtime.FloatState
) {
    val mesh = rememberMcSave(refs)?.mesh
    if (mesh != null) Box(Modifier.fillMaxHeight().aspectRatio(1f).graphicsLayer { scaleX = 1.2f; scaleY = 1.2f }) { McMeshIcon(mesh, selected, selMode, clock) }
    else McArtIcon(c, g, selected, depth, selMode, clock)
}

/** A game without a save icon: its icon / logo, else a disc / box of its cover, else the plain card symbol. */
@Composable
private fun McArtIcon(c: StyleCtx, g: Game, selected: Boolean, depth: Features.McDepth, selMode: Features.McSelected, clock: androidx.compose.runtime.FloatState) {
    // The flat pictures turn a little toward you, and sway when you're on them.
    val swayOn = selected && selMode != Features.McSelected.LIFT
    val tilt = depth.tilt
    val turnMod = Modifier.fillMaxSize().graphicsLayer {
        cameraDistance = 14f * density
        rotationY = -tilt + if (swayOn) sin(clock.floatValue * 2.2f) * 16f else 0f
    }
    // The game's own logo (a clear picture that floats well), not the square achievement icon that can stand in for it.
    val logo = bigLogo(g, c.state)
    val appIcon = if (g.sourceType == SourceType.APP) rememberAppIcon(g.packageName) else null
    val cover = c.cover(g)
    @Composable
    fun ArtFallback() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxHeight().aspectRatio(1f), contentAlignment = Alignment.Center) {
                when {
                    cover != null && mediaKind(g.platform) == "disc" -> McDiscIcon(cover, depth, turnMod)
                    cover != null -> McBoxIcon(cover, depth, turnMod)
                    else -> GenericCardSymbol(Modifier.fillMaxSize(0.8f))
                }
            }
        }
    }
    when {
        appIcon != null -> Box(Modifier.fillMaxHeight().aspectRatio(1f).then(turnMod), contentAlignment = Alignment.Center) {
            if (depth.edge > 0f) Image(appIcon, null, Modifier.fillMaxSize(0.8f).offset(depth.edge.dp, depth.edge.dp), colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.45f)))
            Image(appIcon, null, Modifier.fillMaxSize(0.8f))
        }
        logo != null -> SubcomposeAsyncImage(
            model = logo, contentDescription = null, modifier = Modifier.fillMaxSize(),
            loading = { ArtFallback() }, error = { ArtFallback() },
            success = { st ->
                Box(turnMod, contentAlignment = Alignment.Center) {
                    // A shallow edge: the logo again, dark, just behind it.
                    if (depth.edge > 0f) Image(st.painter, null, Modifier.fillMaxSize().offset(depth.edge.dp, depth.edge.dp),
                        contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.45f)))
                    Image(st.painter, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
        )
        else -> ArtFallback()
    }
}

/** The save's own 3D icon: held at a three-quarter turn, turning (or swaying) when you're on it. */
@Composable
private fun McMeshIcon(mesh: Ps2IconMesh, selected: Boolean, selMode: Features.McSelected, clock: androidx.compose.runtime.FloatState) {
    val buffer = remember { SoftBuffer() }
    val turning = selected && selMode == Features.McSelected.TURN
    val turn = if (turning) rememberInfiniteTransition(label = "mcTurn").animateFloat(
        0f, 360f, infiniteRepeatable(tween(7_000, easing = LinearEasing)), label = "mcTurnA"
    ) else null
    val sway = selected && selMode == Features.McSelected.SWAY
    Canvas(Modifier.fillMaxSize()) {
        val a = turn?.value ?: if (sway) -25f + sin(clock.floatValue * 2.2f) * 22f else -25f
        drawPs2Mesh(mesh, buffer, a, null)
    }
}

/** A small disc printed with the game's cover, lying back a little, with its edge. */
@Composable
private fun McDiscIcon(cover: String, depth: Features.McDepth, turnMod: Modifier) {
    Box(turnMod.graphicsLayer { rotationX = 28f; cameraDistance = 14f * density }, contentAlignment = Alignment.Center) {
        val d = Modifier.fillMaxSize(0.86f)
        Box(d.offset(0.dp, (1.5f + depth.edge).dp).clip(CircleShape).background(Color(0xFF6C7078)))
        Box(d.clip(CircleShape).background(Color(0xFFD9DCE1))) {
            AsyncImage(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                drawCircle(Color.White.copy(alpha = 0.18f), r, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.08f))
                drawCircle(Color(0xFFBFC3C9), r * 0.3f)
                drawCircle(Color(0xFF55595F), r * 0.12f)
                drawArc(Color.White.copy(alpha = 0.25f), 200f, 60f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.5f),
                    topLeft = Offset(r * 0.35f, r * 0.35f), size = Size(r * 1.3f, r * 1.3f))
            }
        }
    }
}

/** A little box of the game's cover, its spine side showing. */
@Composable
private fun McBoxIcon(cover: String, depth: Features.McDepth, turnMod: Modifier) {
    BoxWithConstraints(turnMod, contentAlignment = Alignment.Center) {
        val h = maxHeight * 0.9f
        val w = minOf(h * 0.7f, maxWidth * 0.78f)
        val side = w * (0.1f + depth.edge * 0.03f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (depth.edge > 0f) Box(
                Modifier.width(side).height(h * 0.97f)
                    .graphicsLayer { transformOrigin = TransformOrigin(1f, 0.5f); rotationY = 62f; cameraDistance = 14f * density }
                    .background(Color(0xFF2B2E33))
            ) {
                AsyncImage(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.55f,
                    colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.35f), androidx.compose.ui.graphics.BlendMode.Darken))
            }
            Box(Modifier.width(w).height(h).clip(RoundedCornerShape(2.dp)).background(Color(0xFF202226))
                .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp))) {
                AsyncImage(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent, Color.Transparent))))
            }
        }
    }
}

/** Update 330: Settings › Home › Layout options (Memory card). Every one its own "mc_…" Features pref. */
@Composable
internal fun MemoryCardSettings() {
    MutedNote(
        "Your games as small save icons floating on a memory-card screen: the category top left, the game you're on top right. " +
            "PS2 games show the 3D icon of their own save when one is on your memory cards (only read, never changed); the others " +
            "their icon or logo, a small disc or box of their cover, or a plain memory-card symbol. ◀ ▶ ▲ ▼ move, A opens the game, " +
            "L2 / R2 the next category. Tap an icon to pick it, again to open it; drag to scroll."
    )
    Spacer(Modifier.height(10.dp))
    StyleChoice("Icon size", Features.McSize.values(), Features.mcSize.state.value, { it.label }) { Features.mcSize.value = it }
    StyleChoice("Spacing", Features.McSpacing.values(), Features.mcSpacing.state.value, { it.label }) { Features.mcSpacing.value = it }
    StyleChoice("Arrangement", Features.McArrange.values(), Features.mcArrange.state.value, { it.label }) { Features.mcArrange.value = it }
    StyleChoice("Depth", Features.McDepth.values(), Features.mcDepth.state.value, { it.label }) { Features.mcDepth.value = it }
    MutedNote("How far the drawn icons turn toward you and how thick their edge looks. Save icons are always the save's own 3D model.")
    StyleChoice("The icon you're on", Features.McSelected.values(), Features.mcSelected.state.value, { it.label }) { Features.mcSelected.value = it }
    StyleChoice("Background", Features.McBack.values(), Features.mcBack.state.value, { it.label }) { Features.mcBack.value = it }
    Spacer(Modifier.height(8.dp))
    Toggle(Icons.Filled.Save, "Save icons from your memory cards", "PS2 games show their own save's 3D icon when one is found on this device's memory cards. Off: the game's own artwork.", Features.mcSaveIcons)
    Toggle(Icons.Filled.Title, "Title at the top right", "The game you're on: its save's own title where it has one, else its name, with console, time played and achievements.", Features.mcTitle)
    Toggle(Icons.Filled.Help, "Button hints", "A, L2 / R2 and R1 along the bottom.", Features.mcHints)
    Toggle(Icons.Filled.Waves, "Floating icons", "The icons float gently; they rest when the home screen goes quiet, with Lite effects and with Android's animations off.", Features.mcFloat)
}
