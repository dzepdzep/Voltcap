package com.shelf.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.ArtOverride
import com.shelf.launcher.data.CardArtType
import com.shelf.launcher.data.FocusEffect
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.GameArt
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Home layout › Grid: the category you're in as a grid that fills the screen, in the card artwork you picked
 * (covers, wide capsules, boxes, drawn cartridges / discs, heroes, screenshots — each with its own proportions)
 * and the same focus effect as the row (soft glow, electric). ◀ ▶ ▲ ▼ move, A opens, L2 / R2 change the
 * category; everything else works as on the other layouts.
 */
internal object GridHome {
    /** Columns as last laid out: the step for ▲ ▼. */
    @Volatile var columns = 5
    /** The selection, for tap handlers that must not restart (a restart mid-tap loses the tap). */
    @Volatile var focus = 0
}

/** Width / height of a grid card for an artwork type. */
private fun gridAspect(type: CardArtType, tiles: Boolean = false, spines: Boolean = false): Float = if (tiles) 1f else if (spines) SPINE_ASPECT else when (type) {
    // "Discs only" keeps the cover's shape: the other games' covers fill their cell, the discs sit in it.
    CardArtType.COVER, CardArtType.CASE, CardArtType.CASE_REAL, CardArtType.DISC -> 0.7f
    CardArtType.CAPSULE, CardArtType.HERO, CardArtType.SCREENSHOT -> 1.78f
    CardArtType.CARTRIDGE_DISC -> 1f
}

private fun gridCellWidth(type: CardArtType, compact: Boolean = false, tiles: Boolean = false, spines: Boolean = false): Dp = if (tiles) (if (compact) 88.dp else 126.dp) else if (spines) (if (compact) 30.dp else 40.dp) else when (type) {
    CardArtType.COVER, CardArtType.CASE, CardArtType.CASE_REAL, CardArtType.DISC -> if (compact) 78.dp else 112.dp
    CardArtType.CARTRIDGE_DISC -> if (compact) 88.dp else 126.dp
    else -> if (compact) 150.dp else 210.dp
}

@Composable
internal fun HomeGameGrid(
    games: List<Game>,
    focus: Int,
    art: Map<String, GameArt>,
    overrides: Map<String, ArtOverride>,
    physicalMedia: Map<String, List<Pair<String, String>>>,
    screenshots: Map<String, List<String>>,
    cardArtType: CardArtType,
    onSelect: (Int) -> Unit,
    onOpen: () -> Unit,
    onNeedPhysical: (Game) -> Unit,
    onNeedScreenshots: (Game) -> Unit,
    modifier: Modifier = Modifier,
    /** On the second screen (Games on the second screen): smaller cards, and no Volt perch / tour spots
     *  (those are placed in the main screen's window). */
    secondScreen: Boolean = false,
    /** The game's name under each card. */
    showNames: Boolean = true,
    /** Card size: 1 = the default cell width (Appearance › Cards › Card size). */
    sizeScale: Float = 1f,
    /** Update 125: earned / total achievements of a game, for the progress chip on the game you're on. */
    progressOf: (Game) -> Pair<Int, Int>? = { null },
    /** Update 156: Endless (R1) — the grid carries on with the first games again after the last ones; the view
     *  model's D-pad step ([loopTick] / [loopStep]) tells it which way a step went. */
    loop: Boolean = LocalCardLook.current.loopRow && !secondScreen,
    loopTick: Int = 0,
    loopStep: Int = 0
) {
    val look = LocalCardLook.current
    // Update 156: going round needs two games at least.
    val looping = loop && games.size >= 2
    val loopTap = androidx.compose.runtime.remember { LoopTap() }
    val titles = LocalTitleOptions.current
    val lite = LocalLiteEffects.current
    val hiddenMedia = LocalHiddenMedia.current
    val pulse = rememberFocusPulse(animate = !lite && look.focusEffect != FocusEffect.NONE)
    // Games from several consoles: box art is the plain cover, the same size in every cell.
    val mixed = androidx.compose.runtime.remember(games) { mixedConsoles(games) }
    // "Box art · real sizes": every case against the biggest one in the grid.
    val realRef = androidx.compose.runtime.remember(games, cardArtType) { realCaseRef(cardArtType, games) }
    // Box / disc / screenshot art is looked up only around the selection.
    // Square tiles (Features.gridTiles): every game is its cover as a rounded square, whatever the card artwork is.
    // (Update 174: the Tile game look too — it had no effect on the Grid.)
    val tiles = com.shelf.launcher.data.Features.gridTiles.state.value || (cardArtType == CardArtType.COVER && carouselTiles())
    // Update 113: the Spines look — a wall of spines, like a bookcase.
    val spines = !tiles && spinesOn(cardArtType)
    val needsLookup = !tiles && (cardArtType.wantsMedia() || cardArtType == CardArtType.SCREENSHOT)
    LaunchedEffect(focus, cardArtType, games.size) {
        if (!needsLookup) return@LaunchedEffect
        kotlinx.coroutines.delay(120)
        for (i in (focus - GridHome.columns)..(focus + GridHome.columns * 2)) {
            // Update 156: going round, the games after the last one are the first ones again.
            val g = (if (looping) games.getOrNull(i.mod(games.size)) else games.getOrNull(i)) ?: continue
            if (cardArtType == CardArtType.SCREENSHOT) { if (!screenshots.containsKey(g.stableKey)) { onNeedScreenshots(g); kotlinx.coroutines.delay(90) } }
            else if (!physicalMedia.containsKey(g.stableKey)) { onNeedPhysical(g); kotlinx.coroutines.delay(90) }
        }
    }
    val select by rememberUpdatedState(onSelect)
    val open by rememberUpdatedState(onOpen)
    BoxWithConstraints(modifier) {
        val gap = if (spines) 4.dp else if (secondScreen) 10.dp else 14.dp
        // Layout options › Games across the screen (main screen only), else from the card size.
        val fixedCols = if (secondScreen) 0 else com.shelf.launcher.data.Features.rowGamesFor(com.shelf.launcher.data.HomeLayout.GRID)
        val cols = if (fixedCols > 0) fixedCols.coerceIn(2, if (spines) 40 else 12)
            else ((maxWidth + gap) / (gridCellWidth(cardArtType, secondScreen, tiles, spines) * sizeScale + gap)).toInt().coerceIn(2, if (spines) 40 else 10)
        GridHome.columns = cols
        // (Update 173: the games repeat only when there are more than fit in a row — two Android games stood fifteen times.)
        val drawLoop = looping && games.size > cols
        // Update 156: going round, the games repeat as copies and the grid starts in the middle copy; the selection is
        // on its endless virtual place, so last -> first is one more step (the grid scrolls on a row at most).
        val n = games.size
        val vFocus = rememberLoopPlace(focus, n, drawLoop, loopTick, loopStep, games, loopTap, rowStep = cols)
        val count = if (drawLoop) n * LOOP_COPIES else n
        val focusItem = if (drawLoop) ((LOOP_COPIES / 2) * n + vFocus).coerceIn(0, count - 1) else focus
        val plainState = rememberLazyGridState()
        val loopState = androidx.compose.runtime.remember(drawLoop) {
            if (drawLoop) androidx.compose.foundation.lazy.grid.LazyGridState(((focusItem / cols) - 1).coerceAtLeast(0) * cols) else null
        }
        val state = loopState ?: plainState
        // Keep the selection in view, a row of context above it.
        LaunchedEffect(focusItem, cols) {
            val visible = state.layoutInfo.visibleItemsInfo
            val inView = visible.any { it.index == focusItem } && visible.firstOrNull()?.index != focusItem && (visible.lastOrNull()?.index ?: -1) - focusItem >= cols
            val target = ((focusItem / cols) - 1).coerceAtLeast(0) * cols
            // Update 156: going round, a long way (a new category) jumps instead of racing over the copies.
            if (!inView) runCatching {
                if (drawLoop && kotlin.math.abs(state.firstVisibleItemIndex - target) > cols * 8) state.scrollToItem(target)
                else state.animateScrollToItem(target)
            }
        }
        val keys = androidx.compose.runtime.remember(games) { gameKeys(games) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols), state = state,
            modifier = Modifier.fillMaxSize().categorySlide(),
            horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(if (secondScreen) 10.dp else 12.dp),
            // Room for the glow / bolts round the edge cards.
            contentPadding = PaddingValues(12.dp)
        ) {
            // Update 156: going round, item [vi] is game vi mod n (each copy with its own key).
            items(count, key = { if (drawLoop) "${keys[it.mod(n)]}#${it / n}" else keys[it] }) { vi ->
                val i = if (drawLoop) vi.mod(n) else vi
                val g = games[i]
                val on = vi == focusItem
                val k = g.stableKey
                val o = overrides[k]
                val a = art[k]
                val cover = o?.cover ?: a?.gridUrl
                // Cartridges / discs this layout leaves out: that game's cover instead.
                val gArt = if (tiles) CardArtType.COVER else artForGame(cardArtType, g.platform, hiddenMedia, mixed)
                val typed = typedArtFor(gArt, physicalMedia[k], screenshots[k], g.platform, k)
                // (Update 205: the drawn piece wears its real label scan when it has one.)
                val labelScan = if (typed == null) drawnLabelScan(physicalMedia[k], g.platform) else null
                val url = cardArtUrl(gArt, false, cover, o?.wide ?: a?.wideGridUrl, o?.hero ?: a?.heroUrl, typed)
                val lift by animateFloatAsState(if (on) 1f else 0f, tween(if (on) 110 else 160), label = "gridLift")
                val corner = (if (tiles) maxOf(look.cornerDp, 14) else look.cornerDp).dp
                val shape = uiRounded(corner)
                // How the game you're on stands out (Home › Layout options › Game you're on): raised and turning
                // gently (default), an outline, or the glow / electric effect.
                val focusStyle = com.shelf.launcher.data.Features.gridFocus.state.value
                val effect = if (!on || focusStyle != com.shelf.launcher.data.Features.GridFocus.GLOW) FocusEffect.NONE
                    else if (lite && look.focusEffect == FocusEffect.ELECTRIC) FocusEffect.GLOW else look.focusEffect
                val outlined = on && (focusStyle == com.shelf.launcher.data.Features.GridFocus.OUTLINE || look.focusBorder && focusStyle == com.shelf.launcher.data.Features.GridFocus.GLOW)
                val spin = if (on && focusStyle == com.shelf.launcher.data.Features.GridFocus.LIFT) gridSpin() else null
                // Cartridge / disc / box drawn as the real thing: it stands free, facing you (no card behind it).
                // (Update 119: a real scraped cartridge / disc picture stands free too, whole — it used to be cut to the
                // card's shape, so the Cartridges & discs look looked like covers in the Grid.)
                val realScan = gArt == CardArtType.CARTRIDGE_DISC && !mediaShellsOn && typed != null && url == typed
                val free = isFreeMedia(gArt, g.platform) || realScan || (drawnMediaWanted(gArt, typed) && MediaShell.of(g.platform) != null)
                // Update 203: a drawn cartridge turns as a solid piece (its thickness showing) — the slot hands it the turn
                // instead of turning the flat picture (SolidPiece.kt).
                val solidCart = spin != null && !spines && gArt == CardArtType.CARTRIDGE_DISC && !realScan && drawnMediaWanted(gArt, typed)
                Column(Modifier.zIndex(if (on) 1f else 0f)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(gridAspect(cardArtType, tiles, spines))
                            // Grows from its bottom edge (up and sideways), so the bigger card never covers its own name underneath.
                            .graphicsLayer {
                                if (focusStyle == com.shelf.launcher.data.Features.GridFocus.LIFT) {
                                    // Up a little and turning slowly left and right (read in the draw phase only).
                                    val s = 1f + 0.04f * lift
                                    scaleX = s; scaleY = s
                                    translationY = -size.height * 0.05f * lift
                                    rotationY = if (solidCart) 0f else (spin?.value ?: 0f) * lift
                                    cameraDistance = 12f * density
                                } else {
                                    val s = 1f + (if (free) 0.1f else 0.05f) * lift
                                    scaleX = s; scaleY = s; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                                }
                                flatTextFor3d() // Update 177: drawn flat, then turned (Android 13 text cache)
                            }
                            .then(if (on && !secondScreen) Modifier.focusedCard() else Modifier)
                            .then(if (free) Modifier.mediaFloorGlow(on, ShelfTheme.accent, pulse) else Modifier
                            .then(
                                when (effect) {
                                    FocusEffect.GLOW -> Modifier.softGlow(ShelfTheme.accent, corner, 0.55f + 0.6f * pulse)
                                    FocusEffect.ELECTRIC -> Modifier.softGlow(ShelfTheme.accent, corner, 0.3f + 0.2f * pulse).electricFrame(ShelfTheme.accent, corner, pulse = pulse)
                                    else -> Modifier
                                }
                            )
                            .border(if (outlined) 2.5.dp else 0.dp, if (outlined) ShelfTheme.accent else Color.Transparent, shape)
                            .clip(shape)
                            .background(placeholderBrush(g.hue)))
                            // Update 156: a tapped copy is where the selection goes (going round; its place counted from the
                            // middle copy, as rememberLoopPlace counts).
                            .pointerInput(vi) { detectTapGestures { if (i == GridHome.focus) open() else { if (drawLoop) loopTap.virtual = vi - (LOOP_COPIES / 2) * n; select(i) } } }
                    ) { ProvidePieceTurn(if (solidCart) PieceTurn({ (spin?.value ?: 0f) * lift }, 12f) else null) {
                        val pick = if (drawnMediaWanted(gArt, typed)) mediaFor(g.platform, mixed, cardArtType == CardArtType.DISC, gameKey = g.stableKey) else null
                        val box3d = if (gArt == CardArtType.CASE) caseMarker(g.platform) else null
                        // (Update 173: an app without art: its icon on the label / in the case.)
                        val artOrIcon = if (pick != null || box3d != null) coverOrAppIcon(g, url ?: cover) else null
                        when {
                            spines -> GameSpine(g, physicalMedia[g.stableKey], Modifier.fillMaxSize(), fit = true)
                            pick != null -> Box(Modifier.fillMaxSize().awardShine(g.stableKey, on)) {
                                MediaFocus(on) { PhysicalMedia(pick.shell, labelScan ?: url ?: artOrIcon, Modifier.fillMaxSize().padding(8.dp), platform = pick.platform, labelIsScan = labelScan != null || (typed != null && url == typed), caseCover = cover ?: artOrIcon, title = g.name) }
                            }
                            // Box art as the real box, straight from the front.
                            box3d != null -> MediaFocus(on) { CaseArt(
                                g.platform, cover ?: artOrIcon, typed, Modifier.fillMaxSize().padding(4.dp).awardShine(g.stableKey, on), title = gameDisplayName(g.name), real = realRef,
                                caseModifier = if (on && look.focusBorder) Modifier.border(2.dp, ShelfTheme.accent, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)) else Modifier
                            ) }
                            // Filled edge to edge (cropped, never letterboxed): no empty bands round the picture. (A real
                            // cartridge / disc scan stands free and whole — media, not a cover; no card behind it.)
                            // Update 202: the cover in the shared style shape with its file's flat strips trimmed
                            // (TrimMargins.kt); other pictures as before.
                            url != null -> AsyncImage(if (!realScan && url == cover) rememberStyleArtRequest(url, trim = true) else url, g.name, contentScale = if (realScan) ContentScale.Fit else ContentScale.Crop, modifier = Modifier.fillMaxSize().then(if (realScan) Modifier.padding(6.dp) else Modifier).pixelArtwork())
                            g.sourceType == com.shelf.launcher.data.SourceType.APP -> AppIconArt(g.packageName, wide = gridAspect(cardArtType, tiles) > 1f, modifier = Modifier.fillMaxSize())
                            else -> Text(titles.display(g.name), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), textAlign = TextAlign.Center, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.Center).padding(8.dp))
                        }
                        // Update 115: the console's badge (update 141: on every look; too narrow a slot skips it).
                        ConsoleBadge(g.platform, packageName = g.packageName)
                        // Update 125: the achievement progress on the game you're on (R1 Progress tile).
                        // (Update 129: placed by the R1 Achievements tile.)
                        val chipAt = achChipAlign(look.trophyCorner)
                        if (on && !spines && chipAt != null && look.achievementsOnCards) progressOf(g)?.let { (e, t) ->
                            GameProgressChip(e, t, Modifier.align(chipAt))
                        }
                    } }
                    if (showNames) Spacer(Modifier.height(5.dp))
                    // Two lines' room for every name, so the rows stay even; a longer one slides while you're on it.
                    if (showNames) ScrollingName(
                        titles.display(g.name), scrolling = on, color = if (on) ShelfTheme.accent else ShelfTheme.colors.textPrimary.copy(alpha = 0.85f),
                        fontSize = 11.sp, lineHeight = 13.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
    GridHome.focus = focus
}

/**
 * Keys for a lazy list of games: each game's own key, made unique when the same game is in the row twice
 * (two items with one key crash a lazy list).
 */
internal fun gameKeys(games: List<com.shelf.launcher.data.Game>): List<String> {
    val seen = HashSet<String>(games.size * 2)
    return games.mapIndexed { i, g -> val k = g.stableKey; if (seen.add(k)) k else "$k#$i" }
}

/** The grid's "Raise & gentle spin": the game you're on turns slowly a few degrees left and right (still with Lite effects). */
@Composable
private fun gridSpin(): androidx.compose.runtime.State<Float>? {
    val still = LocalLiteEffects.current || HomeIdle.calm
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "gridSpin")
    val a = t.animateFloat(
        -9f, 9f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse
        ), label = "gridSpinAngle"
    )
    return if (still) null else a
}
