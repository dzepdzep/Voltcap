package com.shelf.launcher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.shownIn
import com.shelf.launcher.data.ArtOverride
import com.shelf.launcher.data.ConsoleNames
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.GameArt
import com.shelf.launcher.data.GameMetadata
import com.shelf.launcher.data.NowPlaying
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Home layout › TV room: the game you're on plays on an old TV standing in a cozy lamp-lit room — its video clip,
 * else its screenshots in turn, else its art. The set is the star; beside it, quieter, the console's sticker and
 * your games as a grid of cases (with or without their spines) or a shelf of spines, and under the set a slim strip
 * with the game's name, facts and story. Volt sits on the TV.
 * ◀ ▶ ▲ ▼ move, A opens, L2 / R2 change category.
 */
object TvRoom {
    /** How the games beside the TV look. */
    enum class Games(val label: String) {
        // (Update 126: the grid draws the games as the R1 Look picks — it used to turn every look into 3D cases with
        // their spines. SPINES keeps its saved name; it is the grid that follows the Look now.)
        SPINES("Grid · as the Look"), COVERS("Grid · no 3D cases"), SHELF("Shelf of spines"),
        /** One game big in the middle, the next ones each side smaller and behind it (GameCarousel). */
        CAROUSEL("Carousel")
    }
}

/** A colour for a console's case spine: PlayStation black, Nintendo grey / red, Sega blue, otherwise its own hue. */
private fun spineColor(platform: String): Color = when (platform) {
    "PS1", "PS2", "PS3", "PSP", "PSVita" -> Color(0xFF111114)
    "PS4" -> Color(0xFF0B3C8C)
    "GameCube", "Wii", "WiiU", "Switch", "NDS", "3DS" -> Color(0xFFD7D9DE)
    "N64", "SNES", "NES" -> Color(0xFF2C2C30)
    "GB", "GBC", "GBA" -> Color(0xFF3B3F9C)
    "Genesis", "MasterSystem", "SegaCD", "32X", "GameGear" -> Color(0xFF0F2C78)
    "Saturn" -> Color(0xFF1B1B1B)
    "Dreamcast" -> Color(0xFFE9E6E0)
    "Xbox", "Xbox360" -> Color(0xFF1F7A2E)
    else -> Color.hsv((platform.hashCode().mod(360)).toFloat(), 0.55f, 0.45f)
}

private fun spineInk(c: Color): Color = if (c.red * 0.3f + c.green * 0.59f + c.blue * 0.11f > 0.6f) Color(0xFF1A1A1E) else Color.White

/** Lays its content out turned on its side (reads bottom to top), as on a case spine. */
private fun Modifier.sideways(): Modifier = this
    .layout { measurable, constraints ->
        val p = measurable.measure(Constraints(maxWidth = constraints.maxHeight.takeIf { it != Constraints.Infinity } ?: 2000, maxHeight = constraints.maxWidth))
        layout(p.height, p.width) { p.place(-(p.width - p.height) / 2, (p.width - p.height) / 2) }
    }
    .graphicsLayer { rotationZ = -90f }

@Composable
internal fun TvRoomHome(
    games: List<Game>,
    focus: Int,
    art: Map<String, GameArt>,
    overrides: Map<String, ArtOverride>,
    screenshots: Map<String, List<String>>,
    videoUrls: Map<String, String>,
    metadata: Map<String, GameMetadata>,
    playtime: Map<String, Long>,
    lastPlayed: Map<String, Long>,
    nowPlaying: NowPlaying?,
    videoSound: Boolean,
    onSelect: (Int) -> Unit,
    onOpen: () -> Unit,
    onNeedMedia: (Game) -> Unit,
    modifier: Modifier = Modifier,
    /** The TV room's card art (Appearance › Game row › Cards): cartridges / discs / boxes show as the objects. */
    cardArtType: com.shelf.launcher.data.CardArtType = com.shelf.launcher.data.CardArtType.COVER,
    physicalMedia: Map<String, List<Pair<String, String>>> = emptyMap(),
    onNeedPhysical: (Game) -> Unit = {},
    /** How far the room reaches past this box (the home screen's padding round it), so it fills the screen. */
    roomBleed: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
    /** Recently unlocked achievements' badges (newest first), on the wall left of the set (update 107). */
    recentBadges: List<String> = emptyList(),
    /**
     * Update 125: the achievements of the game you're on (badge, earned), for the wall instead of everything you
     * recently unlocked: earned in colour, still to get in grey.
     */
    gameBadges: (Game) -> List<Pair<String, Boolean>> = { emptyList() },
    /** Update 135: Endless (R1) — the carousel goes round; the view model's D-pad step for its endless place. */
    loop: Boolean = false,
    loopTick: Int = 0,
    loopStep: Int = 0
) {
    val game = games.getOrNull(focus.coerceIn(0, (games.size - 1).coerceAtLeast(0)))
    // The focused game's clip, screenshots and story are fetched once you rest on it a moment.
    LaunchedEffect(game?.stableKey) {
        val g = game ?: return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        onNeedMedia(g)
    }
    val mode = Features.tvRoomGames.state.value
    BoxWithConstraints(modifier) {
        // The room picture fills the screen (past this box's padding too); the set in it is the star. The games, the
        // console's sticker and the game's facts sit quietly in a column on the dark wall to its right.
        val density = androidx.compose.ui.platform.LocalDensity.current
        val dir = androidx.compose.ui.platform.LocalLayoutDirection.current
        val place = with(density) {
            TvRoomArt.place(
                boxW = maxWidth.toPx(), boxH = maxHeight.toPx(),
                bleedL = roomBleed.calculateLeftPadding(dir).toPx(), bleedT = roomBleed.calculateTopPadding().toPx(),
                bleedR = roomBleed.calculateRightPadding(dir).toPx(), bleedB = roomBleed.calculateBottomPadding().toPx()
            )
        }
        val gamesLeft = with(density) { maxOf(place.tvRight + 12.dp.toPx(), maxWidth.toPx() * 0.42f).toDp() }
            .coerceAtMost(maxWidth - 180.dp).coerceAtLeast(0.dp)
        val gamesW = maxWidth - gamesLeft
        if (game != null) RetroTv(game, art, overrides, screenshots[game.stableKey].orEmpty(), videoUrls[game.stableKey], videoSound, focus, place)
        else TvRoomPicture(place)
        // Update 107: the game's info on the floor under the set (between its legs, where the room was empty) and the
        // recently unlocked badges on the strip of wall between the lamp and the set, clear of the lamp, the books and
        // the plush figure. Both only where the picture leaves room for them.
        val under = with(density) {
            val top = place.y + TvRoomArt.underTv.top * place.scale
            val left = (place.x + TvRoomArt.underTv.left * place.scale).coerceAtLeast(0f)
            val right = minOf(place.x + TvRoomArt.underTv.right * place.scale, gamesLeft.toPx() - 8.dp.toPx())
            val bottom = minOf(place.y + TvRoomArt.underTv.bottom * place.scale, maxHeight.toPx())
            androidx.compose.ui.geometry.Rect(left, top, right, bottom)
        }
        val underOn = Features.tvInfoUnder.state.value && game != null &&
            with(density) { under.width >= 200.dp.toPx() && under.height >= 44.dp.toPx() }
        if (underOn && game != null) with(density) {
            Box(
                Modifier.offset(x = under.left.toDp(), y = under.top.toDp())
                    .size(under.width.toDp(), minOf(under.height.toDp(), 96.dp))
                    .editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.TV, "info"), "Game info")
                    .riseOnChange(game.stableKey)
            ) {
                TvInfoBar(
                    game, metadata[game.stableKey], playtime[game.stableKey], lastPlayed[game.stableKey],
                    cover = overrides[game.stableKey]?.cover ?: art[game.stableKey]?.gridUrl,
                    shots = screenshots[game.stableKey].orEmpty(),
                    logo = overrides[game.stableKey]?.logo ?: art[game.stableKey]?.logoUrl
                )
            }
        }
        // Update 125: the wall shows the achievements of the game you're on (earned in colour, the rest in grey), bigger
        // than before (they were cut to the narrow strip of wall); Edit Home still moves and sizes them.
        val wallBadges = if (game != null) gameBadges(game) else emptyList()
        if (Features.tvRecentBadges.state.value && wallBadges.isNotEmpty()) with(density) {
            val wall = TvRoomArt.badgeWall
            val left = (place.x + wall.left * place.scale).coerceAtLeast(2.dp.toPx())
            val right = place.x + wall.right * place.scale
            val top = place.y + wall.top * place.scale
            val bottom = minOf(place.y + wall.bottom * place.scale, maxHeight.toPx())
            val w = (right - left).toDp()
            val size = (w * 0.9f).coerceIn(40.dp, 60.dp)
            val gap = 6.dp
            val rows = (((bottom - top).toDp() + gap) / (size + gap)).toInt().coerceIn(1, 6)
            // Two columns when one isn't enough (still on the wall's strip, centred on it).
            val cols = if (wallBadges.size > rows) 2 else 1
            val shown = wallBadges.take(rows * cols)
            val gridW = size * cols + gap * (cols - 1)
            Column(
                Modifier.offset(x = (left.toDp() + (w - gridW) / 2).coerceAtLeast(2.dp), y = top.toDp()).width(gridW)
                    .editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.TV, "badges"), "Achievements")
                    .riseOnChange(game?.stableKey ?: ""),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                shown.chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { (url, earned) ->
                            androidx.compose.runtime.key(url) {
                                coil.compose.AsyncImage(
                                    url, null,
                                    colorFilter = if (earned) null else TvLockedGrey, alpha = if (earned) 1f else 0.55f,
                                    modifier = Modifier.size(size)
                                        .clip(uiRounded(6.dp))
                                        .border(1.5.dp, if (earned) ShelfTheme.accent else Color.White.copy(alpha = 0.25f), uiRounded(6.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.TopEnd).width(gamesW).fillMaxHeight()) {
            Box(Modifier.editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.TV, "sticker"), "Console sticker")) { ConsoleSticker(game?.platform) }
            Spacer(Modifier.height(6.dp))
            // (Update 202: the games, and the info strip below, are moved / resized / taken off in Edit Home too.)
            Box(Modifier.fillMaxWidth().weight(1f).categorySlide().editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.TV, "games"), "Games")) {
                // Update 156: Endless goes round in the shelf and the grid too (one more step, nothing reloads).
                if (mode == TvRoom.Games.SHELF) SpineShelf(games, focus, art, overrides, onSelect, onOpen, loop = loop, loopTick = loopTick, loopStep = loopStep)
                else if (mode == TvRoom.Games.CAROUSEL) GameCarousel(games, focus, art, overrides, onSelect, onOpen, cardArtType, physicalMedia, screenshots, onNeedPhysical, loop = loop, loopTick = loopTick, loopStep = loopStep)
                // Update 126: 3D cases turned to their spines only with the Spines look (R1 › Look); every other look as itself.
                else CaseGrid(games, focus, art, overrides, mode == TvRoom.Games.SPINES && spinesOn(cardArtType), onSelect, onOpen, cardArtType, physicalMedia, screenshots, onNeedPhysical, cols = if (gamesW < 240.dp) 2 else 3, loop = loop, loopTick = loopTick, loopStep = loopStep)
            }
            // The info strip stays here when there's no room under the set (or it's turned off there).
            if (game != null && !underOn) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(if (gamesW < 260.dp) 54.dp else 62.dp).editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.TV, "info"), "Game info").riseOnChange(game.stableKey)) {
                    TvInfoBar(
                        game, metadata[game.stableKey], playtime[game.stableKey], lastPlayed[game.stableKey],
                        cover = overrides[game.stableKey]?.cover ?: art[game.stableKey]?.gridUrl,
                        shots = screenshots[game.stableKey].orEmpty(),
                        logo = overrides[game.stableKey]?.logo ?: art[game.stableKey]?.logoUrl
                    )
                }
            }
        }
    }
}

/**
 * The TV room picture (res/drawable-nodpi/tv_room.webp, the user's own painting) and where its parts are, in the
 * picture's pixels: the glass of the TV's screen (the game is shown there), the set's right edge (the games column
 * starts after it) and the plush figure sitting in front of the screen's lower left corner (tv_room_front.png, drawn
 * again over the game so it stays in front).
 */
internal object TvRoomArt {
    const val W = 1672f
    const val H = 925f
    val screen = androidx.compose.ui.geometry.Rect(574f, 242f, 1044f, 562f)
    const val screenCorner = 46f
    /** The set's right edge, with the belt hanging over it. */
    const val tvRightX = 1335f
    /** Where tv_room_front.png sits in the picture. */
    val front = androidx.compose.ui.geometry.Rect(552f, 430f, 634f, 586f)

    /** The floor under the set, between its legs (update 107): the game's info goes there. */
    val underTv = androidx.compose.ui.geometry.Rect(574f, 766f, 1130f, 925f)

    /** The strip of wall between the lamp's pole and the set, under the shade and above the books and the plush
     *  figure (update 107): the recently unlocked badges go there. */
    val badgeWall = androidx.compose.ui.geometry.Rect(384f, 338f, 458f, 690f)

    /** A little left of the set and the plush figure (just the edge of the lamp's light shows): the room is lined up on this. */
    const val tvLeftX = 380f

    /**
     * How the picture is laid over the room: [scale], and its top-left [x], [y] — all px in the home box's
     * coordinates; [roomRight] / [roomTop] / [roomBottom] are the room's edges (past the box by the bleed).
     */
    class Place(val scale: Float, val x: Float, val y: Float, val roomRight: Float, val roomTop: Float, val roomBottom: Float) {
        fun map(r: androidx.compose.ui.geometry.Rect) = androidx.compose.ui.geometry.Rect(x + r.left * scale, y + r.top * scale, x + r.right * scale, y + r.bottom * scale)
        val tvRight get() = x + tvRightX * scale
        val right get() = x + W * scale
    }

    /**
     * The TV goes right to the left side of the screen (the lamp corner is cut off) so the games get the rest:
     * the picture fills the room's height, and where it ends on the right the plain dark wall and the floor run
     * on to the edge (its last column of pixels stretched).
     */
    fun place(boxW: Float, boxH: Float, bleedL: Float, bleedT: Float, bleedR: Float, bleedB: Float): Place {
        val rw = boxW + bleedL + bleedR
        val rh = boxH + bleedT + bleedB
        val s = rh / H
        // Never show past the picture's left edge.
        val x = (rw * 0.012f - tvLeftX * s).coerceAtMost(0f)
        return Place(s, x - bleedL, -bleedT, boxW + bleedR, -bleedT, boxH + bleedB)
    }
}

/** Just the room picture (no game focused). */
@Composable
private fun TvRoomPicture(place: TvRoomArt.Place) {
    val room = androidx.compose.ui.graphics.ImageBitmap.imageResource(com.shelf.launcher.R.drawable.tv_room)
    Canvas(Modifier.fillMaxSize()) {
        val h = (place.roomBottom - place.roomTop).toInt()
        drawImage(
            room, dstOffset = androidx.compose.ui.unit.IntOffset(place.x.toInt(), place.y.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize((TvRoomArt.W * place.scale).toInt(), (TvRoomArt.H * place.scale).toInt()),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High
        )
        // The wall and the floor carry on past the picture's right edge.
        val gap = place.roomRight - place.right
        if (gap > 0f) drawImage(
            room, srcOffset = androidx.compose.ui.unit.IntOffset(TvRoomArt.W.toInt() - 6, 0),
            srcSize = androidx.compose.ui.unit.IntSize(6, TvRoomArt.H.toInt()),
            dstOffset = androidx.compose.ui.unit.IntOffset(place.right.toInt() - 1, place.y.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(gap.toInt() + 2, h),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.Low
        )
    }
}

/** The console's banner: a small sticker card in the theme's colours with its logo and name. */
@Composable
private fun ConsoleSticker(platform: String?) {
    val accent = ShelfTheme.accent
    val second = ShelfTheme.colors.secondary
    val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
    Box(
        Modifier.fillMaxWidth().height(44.dp)
            .graphicsLayer { rotationZ = -1.2f }
            .clip(shape)
            .background(Brush.linearGradient(listOf(lerpColor(ShelfTheme.colors.surfaceRaised, accent, 0.25f), lerpColor(ShelfTheme.colors.surface, second, 0.3f))))
            .border(2.dp, Color.White.copy(alpha = 0.25f), shape)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // The peeled corner.
            val k = 18.dp.toPx()
            drawPath(Path().apply { moveTo(0f, k); lineTo(k, 0f); lineTo(0f, 0f); close() }, Color.Black.copy(alpha = 0.35f))
            drawPath(Path().apply { moveTo(0f, k); lineTo(k, 0f); lineTo(k * 0.8f, k * 0.8f); close() }, Color.White.copy(alpha = 0.55f))
        }
        Row(Modifier.align(Alignment.Center).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            com.shelf.launcher.data.ConsoleLogos.raIcon(platform)?.let { url ->
                AsyncImage(url, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                platform?.let { ConsoleNames.display(it) } ?: "All games", color = Color.White, fontSize = 16.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(16.sp), maxFontSize = 16.sp),
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The TV in the room picture: the game's clip / screenshots / art on its screen, with scanlines and glass. */
@Composable
private fun RetroTv(
    game: Game,
    art: Map<String, GameArt>,
    overrides: Map<String, ArtOverride>,
    shots: List<String>,
    video: String?,
    videoSound: Boolean,
    /** The game's place in the list: the channel number on screen. */
    channel: Int,
    /** Where the room picture (and so the TV's screen) sits. */
    place: TvRoomArt.Place
) {
    val k = game.stableKey
    val hero = overrides[k]?.hero ?: art[k]?.heroUrl ?: overrides[k]?.wide ?: art[k]?.wideGridUrl ?: overrides[k]?.cover ?: art[k]?.gridUrl
    val pictures = (shots + listOfNotNull(hero)).distinct()
    var slide by remember(k) { mutableIntStateOf(0) }
    val active = rememberIsActive()
    LaunchedEffect(k, pictures.size, video, active) {
        if (!active || video != null || pictures.size < 2) return@LaunchedEffect
        while (true) { kotlinx.coroutines.delay(5_000); slide = (slide + 1) % pictures.size }
    }
    val accent = ShelfTheme.accent
    // The tube's life: a faint flicker and a slow rolling bar (still with Lite effects / animations off).
    val crtLive = !LocalLiteEffects.current && rememberAnimationsEnabled() && active && !HomeIdle.calm
    val crt = androidx.compose.animation.core.rememberInfiniteTransition(label = "crt")
    val flicker by if (crtLive) crt.animateFloat(
        0.86f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.keyframes {
                durationMillis = 2600
                1f at 0; 0.9f at 180; 1f at 420; 0.95f at 1300; 0.88f at 1450; 1f at 1700; 0.97f at 2300
            }
        ), label = "flicker"
    ) else remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    val roll by if (crtLive) crt.animateFloat(
        -0.2f, 1.2f, androidx.compose.animation.core.infiniteRepeatable(tween(7000, easing = androidx.compose.animation.core.LinearEasing)), label = "roll"
    ) else remember { androidx.compose.runtime.mutableFloatStateOf(-1f) }
    Box(Modifier.fillMaxSize()) {
        TvRoomPicture(place)
        val density = androidx.compose.ui.platform.LocalDensity.current
        val scr = place.map(TvRoomArt.screen)
        val corner = with(density) { (TvRoomArt.screenCorner * place.scale).toDp() }
        val screen = RoundedCornerShape(corner)
        val tvView = androidx.compose.ui.platform.LocalView.current
        val tvDisplay = runCatching { tvView.display?.displayId }.getOrNull() ?: -1
        // The game on the TV's screen.
        with(density) {
            Box(
                Modifier.offset(scr.left.toDp(), scr.top.toDp()).size(scr.width.toDp(), scr.height.toDp()).clip(screen)
                    // Update 119: the game window opens right here, on the set.
                    .onGloballyPositioned { TvScreen.rect = it.boundsInWindow(); TvScreen.displayId = tvDisplay }
            ) {
                if (video != null) {
                    androidx.compose.runtime.key(video) { VideoBackdrop(video, withSound = videoSound, modifier = Modifier.fillMaxSize().background(Color.Black)) }
                } else {
                    Crossfade(targetState = pictures.getOrNull(slide % maxOf(1, pictures.size)), animationSpec = tween(500), label = "tvPicture") { url ->
                        if (url != null) {
                            val model: Any = if (url.startsWith("/")) java.io.File(url) else url
                            AsyncImage(model, game.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork())
                        } else if (game.sourceType == com.shelf.launcher.data.SourceType.APP) {
                            AppIconArt(game.packageName, wide = true, modifier = Modifier.fillMaxSize().background(Color.Black))
                        }
                    }
                }
                // Tube glass: scanlines, the bulge darkening to the corners, a curved reflection, the edge glow.
                Canvas(Modifier.fillMaxSize()) {
                    val gap = 3.dp.toPx()
                    var y = 0f
                    while (y < size.height) { drawRect(Color.Black.copy(alpha = 0.12f), Offset(0f, y), Size(size.width, 1f)); y += gap }
                    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.5f)), center = Offset(size.width / 2, size.height / 2), radius = maxOf(size.width, size.height) * 0.72f))
                    drawOval(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent), Offset(size.width * 0.28f, size.height * 0.14f), size.width * 0.45f),
                        Offset(-size.width * 0.08f, -size.height * 0.22f), Size(size.width * 0.75f, size.height * 0.62f)
                    )
                    drawRoundRect(Color(0xFFFFB27A).copy(alpha = 0.22f), style = Stroke(3.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner.toPx()))
                    if (roll > -0.5f) {
                        val by = roll * size.height
                        drawRect(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.05f), Color.Transparent), by - size.height * 0.08f, by + size.height * 0.08f),
                            Offset(0f, by - size.height * 0.08f), Size(size.width, size.height * 0.16f)
                        )
                    }
                    if (flicker < 1f) drawRect(Color.Black.copy(alpha = (1f - flicker) * 0.5f))
                }
                // The channel, top right, like an old set's on-screen number.
                Text(
                    "CH %02d".format((channel % 99) + 1), color = Color(0xFF7CFF8A).copy(alpha = 0.85f), fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 16.dp)
                )
            }
        }
        // The plush figure sits in front of the screen's corner: drawn again over the game.
        val front = androidx.compose.ui.graphics.ImageBitmap.imageResource(com.shelf.launcher.R.drawable.tv_room_front)
        val fr = place.map(TvRoomArt.front)
        Canvas(Modifier.fillMaxSize()) {
            drawImage(
                front, dstOffset = androidx.compose.ui.unit.IntOffset(fr.left.toInt(), fr.top.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(fr.width.toInt(), fr.height.toInt()),
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High
            )
        }
    }
}

@Composable
private fun CaseGrid(
    games: List<Game>, focus: Int, art: Map<String, GameArt>, overrides: Map<String, ArtOverride>, spines: Boolean,
    onSelect: (Int) -> Unit, onOpen: () -> Unit,
    cardArtType: com.shelf.launcher.data.CardArtType = com.shelf.launcher.data.CardArtType.COVER,
    physicalMedia: Map<String, List<Pair<String, String>>> = emptyMap(),
    screenshots: Map<String, List<String>> = emptyMap(),
    onNeedPhysical: (Game) -> Unit = {},
    cols: Int = 3,
    /** Update 156: Endless — the grid carries on with the first games again after the last ones. */
    loop: Boolean = false,
    loopTick: Int = 0,
    loopStep: Int = 0
) {
    // (Update 174: only with more games than fit — two games filled the list again and again.)
    // (Update 226, #105: "fit" counted from the screen — the rows above and below the one you're on as well; eight
    // games were fewer than three columns × three rows hold, so copies showed.)
    val looping = loop && games.size > maxOf(LAZY_LOOP_MIN, caseGridShown(cols))
    // Cartridge / disc / box art: the label scans and box scans of the games around the one you're on.
    LaunchedEffect(focus, cardArtType, games.size) {
        if (!cardArtType.wantsMedia()) return@LaunchedEffect
        kotlinx.coroutines.delay(120)
        for (i in (focus - cols)..(focus + cols * 2)) {
            // Update 156: going round, the games after the last one are the first ones again.
            val g = (if (looping) games.getOrNull(i.mod(games.size)) else games.getOrNull(i)) ?: continue
            if (!physicalMedia.containsKey(g.stableKey)) { onNeedPhysical(g); kotlinx.coroutines.delay(90) }
        }
    }
    GridHome.columns = cols
    GridHome.focus = focus
    // Update 156: going round, the games repeat as copies (the grid starts in the middle copy) and the selection is on
    // its endless virtual place: last -> first is one more step, the grid moves on a row at most.
    val n = games.size
    val loopTap = remember { LoopTap() }
    val vFocus = rememberLoopPlace(focus, n, looping, loopTick, loopStep, games, loopTap, rowStep = cols)
    val count = if (looping) n * LOOP_COPIES else n
    val focusItem = if (looping) ((LOOP_COPIES / 2) * n + vFocus).coerceIn(0, count - 1) else focus
    val plainState = rememberLazyGridState()
    val loopState = remember(looping) {
        if (looping) androidx.compose.foundation.lazy.grid.LazyGridState((focusItem / cols) * cols) else null
    }
    val state = loopState ?: plainState
    // The row you're on always sits in the middle: the grid scrolls up / down around it (the padding above and
    // below lets the first and last rows reach the middle too).
    LaunchedEffect(focusItem) {
        val target = (focusItem / cols) * cols
        // Update 156: going round, a long way (a new category) jumps instead of racing over the copies.
        runCatching {
            if (looping && kotlin.math.abs(state.firstVisibleItemIndex - target) > cols * 8) state.scrollToItem(target)
            else state.animateScrollToItem(target)
        }
    }
    val hiddenMedia = LocalHiddenMedia.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // One game per row: a big case in the middle with its neighbours above and below, never wider than the column
    // or so tall that only one fits.
    val gap = if (cols >= 3 && maxWidth < 360.dp) 6.dp else 12.dp
    val cellW = if (cols == 1) minOf(maxWidth - 16.dp, maxHeight * 0.58f * 0.78f) else (maxWidth - 16.dp - gap * (cols - 1)) / cols
    // Update 174: the Tile look — square tiles here too (the TV room always drew cases).
    val tiles = cardArtType == com.shelf.launcher.data.CardArtType.COVER && carouselTiles()
    val cellAspect = if (tiles) 1f else 0.78f
    val cellH = cellW / cellAspect
    val padV = ((maxHeight - cellH) / 2f).coerceAtLeast(8.dp)
    // The R1 Size tile (update 104): smaller games leave more room either side.
    val sizeK = GameSize.scale(com.shelf.launcher.data.HomeLayout.TV).coerceAtMost(1f)
    val padH = if (cols == 1) ((maxWidth - cellW * sizeK) / 2f).coerceAtLeast(8.dp)
        else ((maxWidth - (cellW * sizeK * cols + gap * (cols - 1))) / 2f).coerceAtLeast(8.dp)
    val select by rememberUpdatedState(onSelect)
    val open by rememberUpdatedState(onOpen)
    val look = LocalCardLook.current
    val pulse = rememberFocusPulse(animate = look.focusEffect != com.shelf.launcher.data.FocusEffect.NONE && !LocalLiteEffects.current)
    val keys = androidx.compose.runtime.remember(games) { gameKeys(games) }
    // Games from several consoles: "Box art" is the plain cover, all the same size.
    val mixed = androidx.compose.runtime.remember(games) { mixedConsoles(games) }
    val realRef = androidx.compose.runtime.remember(games, cardArtType) { realCaseRef(cardArtType, games) }
    LazyVerticalGrid(
        GridCells.Fixed(cols), state = state, modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = padH, vertical = padV)
    ) {
        // Update 156: going round, item [vi] is game vi mod n (each copy with its own key).
        items(count, key = { if (looping) "${keys[it.mod(n)]}#${it / n}" else keys[it] }) { vi ->
            val i = if (looping) vi.mod(n) else vi
            val g = games[i]
            val on = vi == focusItem
            val lift by animateFloatAsState(if (on) 1f else 0f, tween(if (on) 110 else 160), label = "caseLift")
            val free = isFreeMedia(artForGame(cardArtType, g.platform, hiddenMedia, mixed, gameKey = g.stableKey), g.platform)
            Column(
                Modifier.zIndex(if (on) 1f else 0f).fillMaxWidth().aspectRatio(cellAspect)
                    .pointerInput(vi) { detectTapGestures { if (i == GridHome.focus) open() else { if (looping) loopTap.virtual = vi - (LOOP_COPIES / 2) * n; select(i) } } },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (tiles) {
                    val url = overrides[g.stableKey]?.cover ?: art[g.stableKey]?.gridUrl
                    val tileShape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                    Box(
                        Modifier.fillMaxWidth().weight(1f)
                            .graphicsLayer { val sc = 1f + 0.06f * lift; scaleX = sc; scaleY = sc; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) }
                            .then(if (on) Modifier.focusedCard() else Modifier)
                            .clip(tileShape).background(placeholderBrush(g.hue))
                            .then(if (on && look.focusBorder) Modifier.border(2.5.dp, ShelfTheme.accent, tileShape) else Modifier)
                    ) {
                        // (Update 202: the cover with its file's flat strips trimmed — TrimMargins.kt.)
                        if (url != null) AsyncImage(rememberStyleArtRequest(url, trim = true), g.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork())
                        else if (g.sourceType == com.shelf.launcher.data.SourceType.APP) AppIconArt(g.packageName, wide = false, modifier = Modifier.fillMaxSize())
                        ConsoleBadge(g.platform, packageName = g.packageName)
                    }
                } else ShelfGameArt(
                    g, on, cardArtType, art, overrides, physicalMedia, screenshots, spines, mixed, realRef, pulse,
                    Modifier.fillMaxWidth().weight(1f)
                        .graphicsLayer { val sc = 1f + (if (free) 0.1f else 0.06f) * lift; scaleX = sc; scaleY = sc; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) } // grows from its bottom edge: never over its own name
                        .then(if (on) Modifier.focusedCard() else Modifier)
                )
                // The object with the game's name under it (Settings › Home layout › TV room: names on by default).
                if (free && Features.tvRoomNames.state.value) ScrollingName(
                    gameDisplayName(g.name), scrolling = on, color = if (on) ShelfTheme.accent else Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, lineHeight = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = nameTextStyle(com.shelf.launcher.data.NameBackdrop.SHADOW),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        }
    }
    }
}

/** The games as spines on a shelf; the one you're on is pulled out to show its cover. */
@Composable
private fun SpineShelf(
    games: List<Game>, focus: Int, art: Map<String, GameArt>, overrides: Map<String, ArtOverride>,
    onSelect: (Int) -> Unit, onOpen: () -> Unit,
    /** Update 156: Endless — the shelf goes round (the games repeat on both sides). */
    loop: Boolean = false,
    loopTick: Int = 0,
    loopStep: Int = 0
) {
    GridHome.columns = 1
    GridHome.focus = focus
    // Update 156: going round, the spines repeat as copies (the shelf starts in the middle copy) and the selection is on
    // its endless virtual place: last -> first slides one spine on, like any step.
    // (Update 174: only with more games than fit — two games filled the list again and again.)
    // (Update 226, #105: "fit" counted from the screen's width — about 30 spines stand side by side on a handheld, so
    // a shelf of 9 to 30 games showed each one two or three times.)
    val looping = loop && games.size > maxOf(LAZY_LOOP_MIN, (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp / 17) + 2)
    val n = games.size
    val loopTap = remember { LoopTap() }
    val vFocus = rememberLoopPlace(focus, n, looping, loopTick, loopStep, games, loopTap)
    val count = if (looping) n * LOOP_COPIES else n
    val focusItem = if (looping) ((LOOP_COPIES / 2) * n + vFocus).coerceIn(0, count - 1) else focus
    val plainState = rememberLazyListState()
    val loopState = remember(looping) {
        if (looping) androidx.compose.foundation.lazy.LazyListState((focusItem - 3).coerceAtLeast(0)) else null
    }
    val state = loopState ?: plainState
    LaunchedEffect(focusItem) {
        val target = (focusItem - 3).coerceAtLeast(0)
        // Update 156: going round, a long way (a new category) jumps instead of racing over the copies.
        runCatching {
            if (looping && kotlin.math.abs(state.firstVisibleItemIndex - target) > 24) state.scrollToItem(target)
            else state.animateScrollToItem(target)
        }
    }
    val select by rememberUpdatedState(onSelect)
    val open by rememberUpdatedState(onOpen)
    val look = LocalCardLook.current
    val frameOn = look.focusBorder
    // Games from several consoles: the one you pull out is the plain cover, the same size for every game.
    val mixed = androidx.compose.runtime.remember(games) { mixedConsoles(games) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight * 0.86f
        val maxPulledW = maxWidth * 0.62f
        val keys = androidx.compose.runtime.remember(games) { gameKeys(games) }
        LazyRow(
            Modifier.fillMaxSize(), state = state, verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Update 156: going round, item [vi] is game vi mod n (each copy with its own key).
            items(count, key = { if (looping) "${keys[it.mod(n)]}#${it / n}" else keys[it] }) { vi ->
                val i = if (looping) vi.mod(n) else vi
                val g = games[i]
                val on = vi == focusItem
                val pull by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "spinePull")
                val cov = MediaCovers.used(g.platform)
                // (Update 129: or the spine picked for all on R1 › Spine.)
                val spineTpl = if (Features.coverTemplates.state.value || LookStyles.spineForAll() || MediaCovers.own(g.platform) != null) CustomSpines.of(LookStyles.spinePlatform(g.platform)) else null
                // A real spine keeps its own proportions (the pack's spines run from slim to chunky; update 124: never wider
                // than a tenth of its height).
                // (Update 126: your own console spine at its own shape, so nothing of it is cut off.)
                val spineA = spineTpl?.aspect?.coerceAtMost(SPINE_MAX_ASPECT) ?: cov?.shelfSpineAspect()?.coerceAtMost(SPINE_MAX_ASPECT)
                val baseW = if (spineA != null) (h * spineA).coerceIn(14.dp, 48.dp) else 26.dp
                // Pulled out, the slot takes the front's own shape (the console's cover frame, or a plain cover when the
                // consoles are mixed), so the cover fills it edge to edge: no empty bands above or below it.
                // (Update 129: or the 3D box from the side, as set in "Pulled out, it shows".)
                // (Update 205: the same in a list mixing consoles — each game pulled out as its own case there too.)
                val sideBox = if (Features.shelfPulled.state.value == Features.ShelfPulled.SIDE) MediaTemplates.of(g.platform)?.box else null
                val frontAspect = if (sideBox != null) sideBox.aspect * 1.12f
                    else if (cov != null && Features.shelfPulled.state.value != Features.ShelfPulled.COVER) cov.frontAspect else 0.7f
                val fullW = minOf(h * frontAspect, maxPulledW)
                val fullH = fullW / frontAspect
                val w = baseW + (fullW - baseW) * pull
                val boxH = h + (fullH - h) * pull
                val cover = overrides[g.stableKey]?.cover ?: art[g.stableKey]?.gridUrl
                // (Update 117: its scans looked up once it stays on the shelf, for its own spine; update 125: always, as
                // the real spine scan is the default now.)
                if (!HomeScans.map.containsKey(g.stableKey)) {
                    LaunchedEffect(g.stableKey) { kotlinx.coroutines.delay(500); HomeScans.need?.invoke(g) }
                }
                Box(
                    Modifier.width(w).height(boxH).offset(y = (-14).dp * pull)
                        .then(if (on) Modifier.focusedCard() else Modifier)
                        .clip(RoundedCornerShape(3.dp))
                        .border(if (on && frameOn) 2.dp else 0.dp, if (on && frameOn) ShelfTheme.accent else Color.Transparent, RoundedCornerShape(3.dp))
                        .pointerInput(vi) { detectTapGestures { if (i == GridHome.focus) open() else { if (looping) loopTap.virtual = vi - (LOOP_COPIES / 2) * n; select(i) } } }
                ) {
                    if (pull > 0.4f) {
                        // Pulled out: the front alone, filling the slot (the spine is only for the shelf, seen from the side).
                        // Update 129: by default its real case when there's a scan of it, else Voltcap's (as on the Game shelf).
                        // (Update 205: in a list mixing consoles too — it was the plain cover there.)
                        PulledOutCase(g, cover, HomeScans.map[g.stableKey], sideBox != null, Modifier.fillMaxSize())
                    } else if (!LookStyles.spineForAll() && (HomeScans.map[g.stableKey]?.any { it.first == "Spine" } == true ||
                        (Features.originalCases.state.value && BoxTexture.spine(HomeScans.map[g.stableKey], g.platform) != null))) {
                        // Update 117: the game's own spine (its spine scan, or cut from its box scan), as on the Shelf.
                        // (Update 125: a real spine scan always, whatever Original cases says.)
                        GameSpine(g, null, Modifier.fillMaxSize())
                    } else if (spineTpl != null) {
                        // Your own console spine (or Steam's / Android's), the game's name in the box left for it.
                        SpinePicture(spineTpl, Modifier.fillMaxSize()) { ink ->
                            Box(Modifier.fillMaxSize().padding(horizontal = 1.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
                                // (Update 124: as big as fits, down to 6 sp, over two lines when it has to.)
                                Text(
                                    gameDisplayName(g.name), color = ink, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 4.5.sp, maxFontSize = 11.sp, stepSize = 0.5.sp), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 1.05.em,
                                    style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(if (ink == Color.White) Color.Black else Color.White, blurRadius = 4f)),
                                    modifier = Modifier.sideways()
                                )
                            }
                        }
                    } else {
                        // Spine: the console strip at the top, the game's name running up it.
                        val c = spineColor(g.platform)
                        Column(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(lerpColor(c, Color.White, 0.12f), c, lerpColor(c, Color.Black, 0.25f))))) {
                            Box(Modifier.fillMaxWidth().height(28.dp).background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                                com.shelf.launcher.data.ConsoleLogos.raIcon(g.platform)?.let { AsyncImage(it, null, modifier = Modifier.size(18.dp)) }
                            }
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                Text(gameDisplayName(g.name), color = spineInk(c), fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 4.5.sp, maxFontSize = 11.sp, stepSize = 0.5.sp), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 1.05.em, modifier = Modifier.sideways())
                            }
                        }
                    }
                }
            }
        }
        // The shelf board.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(8.dp).background(Brush.verticalGradient(listOf(lerpColor(ShelfTheme.colors.surfaceRaised, Color.White, 0.1f), ShelfTheme.colors.surface))))
    }
}

/** Under it all: the name and story, the score and who made it, and the facts. */
@Composable
private fun TvInfoBar(game: Game, metaAll: GameMetadata?, playtimeMs: Long?, lastPlayedAt: Long?, cover: String?, shots: List<String>, logo: String?) {
    val meta = metaAll.shownIn(com.shelf.launcher.data.Features.factsGrid.state.value)
    // A slim strip under the set: the TV stays what you look at.
    val facts = listOfNotNull(
        ConsoleNames.display(game.platform),
        meta?.releaseYear?.toString(),
        meta?.developer,
        meta?.publisher,
        playtimeMs?.takeIf { it > 0 }?.let { "Played " + com.shelf.launcher.data.PlaytimeStore.format(it) },
        lastPlayedAt?.let { "Last " + android.text.format.DateFormat.getMediumDateFormat(androidx.compose.ui.platform.LocalContext.current).format(java.util.Date(it)) }
    ).joinToString("  ·  ")
    // The pane's layers behind the strip (the TV already plays the clip, so no video layer here).
    DetailLayers(game.stableKey, cover, shots, null, logo, Modifier.fillMaxSize(), video = false) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // Small screens: a narrower column and a shorter strip - the logo gives way, the stars shrink to one, nothing wraps.
    val narrow = maxWidth < 260.dp
    val short = maxHeight < 58.dp
    val logoOn = Features.detailLayerLogo.state.value && logo != null && maxWidth >= 230.dp
    Column(
        Modifier.fillMaxSize().voltSurface(uiRounded(12.dp), base = ShelfTheme.colors.surface, alpha = 0.72f)
            .padding(start = if (logoOn) minOf(110.dp, maxWidth * 0.3f) else 14.dp, end = if (narrow) 8.dp else 14.dp, top = 6.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScrollingName(gameDisplayName(game.name), scrolling = true, color = ShelfTheme.colors.textPrimary, fontSize = if (narrow) 14.sp else 16.sp, fontWeight = FontWeight.Bold, lines = 1, modifier = Modifier.weight(1f, fill = false))
            val score = meta?.metacritic ?: meta?.criticScore
            if (score != null) {
                Spacer(Modifier.width(if (narrow) 4.dp else 8.dp))
                // Five stars when there's room, else one star and the number out of five: never broken over two lines.
                val stars = if (narrow) "★ " + "%.1f".format(score / 20f) else (1..5).joinToString("") { n -> if (score >= n * 20 - 10) "★" else "☆" }
                Text(stars, color = ShelfTheme.accent, fontSize = if (narrow) 11.sp else 12.sp, maxLines = 1, softWrap = false)
            }
        }
        Text(facts, color = ShelfTheme.textMuted, fontSize = if (narrow) 10.sp else 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(if (narrow) 10.sp else 11.sp), maxFontSize = if (narrow) 10.sp else 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!short) meta?.summary?.let { Text(it, color = ShelfTheme.textMuted.copy(alpha = 0.8f), fontSize = 10.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(10.sp), maxFontSize = 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    }
    }
}

/** Update 125: achievements still to get, in grey. */
private val TvLockedGrey = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) })


/**
 * Update 226 (#105): how many cases the TV room's grid can show at once (part-shown ones too) on this screen, so Endless
 * goes round only with more games than that: the row you're on sits in the middle with as many rows above and below as
 * the room leaves. Counted with the shortest cells (square tiles), so it never says fewer than are seen.
 */
@Composable
private fun caseGridShown(cols: Int): Int {
    val conf = androidx.compose.ui.platform.LocalConfiguration.current
    val w = conf.screenWidthDp.toFloat()
    val h = conf.screenHeightDp.toFloat()
    val c = cols.coerceAtLeast(1)
    val cell = if (c == 1) minOf(w - 16f, h * 0.58f * 0.78f) else (w - 16f - 6f * (c - 1)) / c
    if (cell <= 0f) return LAZY_LOOP_MIN
    val aside = kotlin.math.ceil(((h - cell) / 2f).coerceAtLeast(0f) / (cell + 6f)).toInt()
    return (1 + 2 * aside) * c
}
