package com.shelf.launcher.ui

import androidx.compose.ui.zIndex

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import com.shelf.launcher.AchievementBadgeMode
import com.shelf.launcher.data.ArtOverride
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.GameArt
import com.shelf.launcher.data.RaGameProgress
import com.shelf.launcher.data.RaRecentAchievement
import com.shelf.launcher.data.RaAward

private val DockSpacing = 12.dp
private val DockStartPadding = 24.dp

// Positions use the same spring as GameCard's own width/height (dockMotion),
// so every card's position and size move in lockstep and never overlap or gap.

/**
 * The bottom dock.
 *
 * This used to be a LazyRow driven by animateScrollToItem. That scrolls a pixel
 * distance measured *before* the newly focused card widens into a banner, so
 * the target moved mid-animation and the row overshot past the left edge, then
 * snapped back when the scroll finished.
 *
 * Instead, every card's x position is computed directly: everything left of
 * the focused card is a cover of known width, so the focused card always lands
 * exactly at the start padding. Position and width animate with the same tween,
 * so nothing ever overshoots. Only a small window of cards around the focus is
 * composed, so large libraries stay cheap.
 */
@Composable
internal fun GameDock(
    games: List<Game>,
    art: Map<String, GameArt>,
    focusedIndex: Int,
    lastPlayedAt: Map<String, Long>,
    raProgress: Map<String, RaGameProgress>,
    onFocus: (Int) -> Unit,
    onLaunch: (Game) -> Unit,
    onOpenOptions: (Game) -> Unit,
    onViewAchievements: (Game) -> Unit,
    favorites: Set<String>,
    onToggleFavorite: (Game) -> Unit,
    artOverrides: Map<String, ArtOverride>,
    badgeMode: AchievementBadgeMode,
    badgeNextToTitle: Boolean,
    recentUnlocks: List<RaRecentAchievement>,
    onCycleBadgeMode: () -> Unit,
    awards: Map<String, RaAward> = emptyMap(),
    playtime: Map<String, Long> = emptyMap(),
    /** Reports where the focused card's art starts (px from the top), so
     * "Whole picture" backgrounds can end exactly there. */
    onArtTop: (Float) -> Unit = {},
    /** Left/right steps in a looping row (count, last direction). */
    loopTick: Int = 0,
    loopStep: Int = 0,
    /** Which artwork the cards show; box / disc / screenshot come from the maps below. */
    cardArtType: com.shelf.launcher.data.CardArtType = com.shelf.launcher.data.CardArtType.COVER,
    /** The category label sits under the row (else above it): the name line hugs it. */
    categoryBelow: Boolean = false,
    /** Update 153: the row sits at the bottom of the screen (its name line can then be only as tall as it needs). */
    rowAtBottom: Boolean = false,
    physicalMedia: Map<String, List<Pair<String, String>>> = emptyMap(),
    screenshots: Map<String, List<String>> = emptyMap(),
    /** Asks for a game's box / disc art (throttled here; only for games near the focus). */
    onNeedPhysical: (Game) -> Unit = {},
    onNeedScreenshots: (Game) -> Unit = {}
) {
    com.shelf.launcher.Breadcrumbs.mark("GameDock")
    // Unlocks not looked at yet, per game (Living Library's small gold mark).
    val seenAll by com.shelf.launcher.data.AchievementSeen.all.collectAsState()
    // Cartridges / discs this home layout leaves out (those games show their cover).
    val hiddenMedia = LocalHiddenMedia.current
    // Games from several consoles: box art is the plain cover, the same size on every card.
    val mixedRow = androidx.compose.runtime.remember(games) { mixedConsoles(games) }
    // "Box art · real sizes": every case against the biggest one in the row.
    val realRef = androidx.compose.runtime.remember(games, cardArtType) { realCaseRef(cardArtType, games) }
    // No early return here: drawing code must always run to the end (the
    // list is empty at startup, then fills — exactly when an early exit breaks).
    if (games.isNotEmpty()) {
        val focus = focusedIndex.coerceIn(0, games.lastIndex)

        val look = LocalCardLook.current
        val sizes = cardSizes()
        val coverWidth = sizes.cover.width
        // "Wide for the first game only" (left end, no looping): the first game is a fixed wide
        // capsule at the left that never moves or folds; the other games scroll as covers right of it.
        // "Carousel look" (Appearance › Game row): the game you're on big in the middle (a cover, never the wide
        // capsule), the next ones each side smaller, behind it and optionally turned away (Carousel).
        val carousel = com.shelf.launcher.data.HomeLayout.CLASSIC.name in com.shelf.launcher.data.Features.carouselLayouts.state.value
        MotionPrefs.observe()
        val popNow = MotionPrefs.pop(com.shelf.launcher.data.HomeLayout.CLASSIC)
        // (Update 141: whatever Endless and "Game you're on" say — always at the left end, never looping.)
        // Update 146: like a big-screen game store's home row: game 1 is wide, but it's part of the row — moving on, the
        // row slides along and the wide one slides off to the left like the others (it used to stay pinned there).
        val pinnedFirst = !carousel && LocalPinnedFirstWide.current
        // Update 136: wide capsules with the game you're on at the left: that place is a fixed wide capsule. It never
        // moves, folds or rolls out (only its game changes), and no game ever shows to its left; the covers right of it
        // slide along.
        // A free-standing cartridge / disc / box never opens into a wide capsule: its slot is a bigger cover.
        val bannerGame = games[if (pinnedFirst) 0 else focus]
        val bannerFree = isFreeMedia(artForGame(cardArtType, bannerGame.platform, hiddenMedia, mixedRow, gameKey = bannerGame.stableKey), bannerGame.platform)
        // Update 143: only while the game you're on really opens into a wide capsule. A case / cartridge / disc never
        // does: then the row moves like a plain cover row — the next game slides along into the place of the one you
        // were on, which slides off to the left. The fixed place made the next game vanish from the row and pop up at
        // the left ("no animation scrolling through games").
        val fixedSlot = !carousel && !pinnedFirst && look.focusedWide && !bannerFree &&
            look.focusPosition == com.shelf.launcher.data.FocusPosition.LEFT
        // (Update 129: the game you're on grows as much as "stands out" says; 15 % at 100 %.)
        val grow = sizes.bigCover.height / sizes.cover.height
        val bannerWidth = if ((bannerFree || carousel) && look.focusedWide) coverWidth * grow else sizes.focused.width
        // Update 144: room after the game you're on for its disc / cartridge sticking out of its case (Case + disc), so it
        // shows whole; the other games tuck theirs behind the next one. (Card padding is 4 dp each side.)
        val peek = if (carousel || pinnedFirst) 0f else casePeek(artForGame(cardArtType, bannerGame.platform, hiddenMedia, mixedRow, gameKey = bannerGame.stableKey), bannerGame.platform)
        val discRoom = if (peek > 0f) (sizes.bigCover.height - 8.dp) * peek else 0.dp
        // (The room the game you're on takes, gliding along with the row when it changes from one game to the next.)
        val glideBanner by animateDpAsState(bannerWidth, dockMotion(), label = "dockBanner")
        val glideDiscRoom by animateDpAsState(discRoom, dockMotion(), label = "dockDiscRoom")
        // Small, even gap between every card (about 1/27 of a cover's width —
        // tight, but never touching).
        val spacing = maxOf(DockSpacing * 0.36f, coverWidth / 27.5f)
        val stride = coverWidth + spacing

        // Where the game you're on sits: at the left end, or in the middle.
        val screenWidth = screenSizeDp().width
        // Left end: right at the screen's edge, with only the same small gap
        // as between the cards — no empty strip before the first one.
        // With no frame or effect around it, the card sits flush with the edge.
        val framed = look.focusBorder || look.focusEffect != com.shelf.launcher.data.FocusEffect.NONE
        val focusX = if (!pinnedFirst && (carousel || look.focusPosition == com.shelf.launcher.data.FocusPosition.CENTER)) {
            (screenWidth - bannerWidth) / 2
        } else if (framed) maxOf(spacing, 6.dp) else 0.dp
        // Enough covers to fill the screen on both sides of the focus, plus two
        // spare so cards enter and leave the window while fully off-screen.
        val bigCoverWidth = coverWidth * grow
        // (Update 137: the carousel packs them closer — as many as reach past the screen's edges.)
        val carouselReach = if (carousel) Carousel.flatReach(bannerWidth.value, screenWidth.value / 2f) else 0
        val coversAfter = maxOf((((screenWidth - focusX - bannerWidth) / stride).toInt() + 2).coerceAtLeast(2), carouselReach)
        val coversBefore = maxOf(((focusX / stride).toInt() + 2).coerceAtLeast(2), carouselReach)
        // Looping row (Appearance › Game row): the games repeat on both sides
        // instead of running out. Positions are tracked on an endless "virtual"
        // index, so stepping from the last game to the first still slides by
        // one card instead of jumping back through the whole library.
        val loop = look.loopRow && games.size >= 1 && !pinnedFirst
        // [last focus, virtual focus, last step count]
        val tracker: IntArray = androidx.compose.runtime.remember { intArrayOf(focus, focus, loopTick) }
        // A new list (re-sorted, art or details came in): carry on from the same place on the endless row
        // instead of starting it again at the game's index — the restart made the cards race across.
        val trackedList = androidx.compose.runtime.remember { arrayOfNulls<List<Game>>(1) }
        if (trackedList[0] !== games) {
            if (trackedList[0] != null && trackedList[0] != games) {
                val n0 = games.size.coerceAtLeast(1)
                tracker[1] = tracker[1] - tracker[1].mod(n0) + focus
                tracker[0] = focus
                tracker[2] = loopTick
            }
            trackedList[0] = games
        }
        if (loop && tracker[2] != loopTick) {
            // A D-pad step: slide exactly that way. With one or two games the
            // index alone can't tell (2 games: 0→1 and 1→0 look like back and
            // forth; 1 game: it never changes).
            tracker[2] = loopTick
            if (loopStep != 0) {
                tracker[1] += loopStep
                tracker[0] = (tracker[0] + loopStep).mod(games.size)
            }
        }
        if (tracker[0] != focus) {
            val n = games.size
            var delta: Int = focus - tracker[0]
            if (loop) {
                if (delta > n / 2) delta -= n
                if (delta < -n / 2) delta += n
            }
            tracker[1] += delta
            tracker[0] = focus
        }
        if (!loop) tracker[1] = focus
        val virtualFocus = tracker[1]
        // Update 144: where the row is, in games (virtual index when it loops), gliding as Settings › Home › Animations ›
        // Moving between games says. A new list (another category, re-sorted) or a long jump moves at once.
        val rowPos = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(virtualFocus.toFloat()) }
        val rowList = androidx.compose.runtime.remember { arrayOfNulls<List<Game>>(1) }
        val rowSpec = dockMotion<Float>()
        LaunchedEffect(virtualFocus, games) {
            val target = virtualFocus.toFloat()
            val newList = rowList[0] != null && rowList[0] != games
            rowList[0] = games
            if (newList || kotlin.math.abs(rowPos.value - target) > 4f) rowPos.snapTo(target)
            else rowPos.animateTo(target, rowSpec)
        }
        val firstIndex = if (loop) virtualFocus - coversBefore else (focus - coversBefore).coerceAtLeast(0)
        val lastIndex = if (loop) virtualFocus + coversAfter else (focus + coversAfter).coerceAtMost(games.lastIndex)
        // With the fixed first capsule the cover you're on is a bit taller than the capsule: room for both.
        val rowHeight = maxOf(sizes.focused.height, sizes.cover.height * (if (pinnedFirst) grow else 1f))

        // Warm the MEMORY cache for what the games around the selection will
        // actually show: covers for the ones beside you, the wide capsule only
        // for the next few you could land on. Same request shape as the cards
        // (ArtRequests), so a card finds its bitmap ready. Waits a moment and
        // cancels itself when the focus moves on, so fast scrolling doesn't
        // queue art for games it flew past.
        val context = LocalContext.current
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        val prefetchSizes = sizes
        val prefetchWide = look.focusedWide
        // Box / disc / screenshot art is looked up lazily: only for the few games
        // around the focus, after the selection has rested a moment, one at a
        // time (the lookups are idempotent and remembered by the view model).
        val needsLookup = cardArtType.wantsMedia() || cardArtType == com.shelf.launcher.data.CardArtType.SCREENSHOT
        LaunchedEffect(focus, cardArtType, games.size, loop) {
            if (!needsLookup) return@LaunchedEffect
            kotlinx.coroutines.delay(150)
            for (i in intArrayOf(focus, focus + 1, focus - 1, focus + 2)) {
                // Update 156: with the row going round, the games past the end are the first ones (and before game 1 the last).
                val game = games.getOrNull(if (loop) i.mod(games.size) else i) ?: continue
                val k = game.stableKey
                if (cardArtType == com.shelf.launcher.data.CardArtType.SCREENSHOT) {
                    if (!screenshots.containsKey(k)) { onNeedScreenshots(game); kotlinx.coroutines.delay(100) }
                } else if (!physicalMedia.containsKey(k)) {
                    onNeedPhysical(game); kotlinx.coroutines.delay(100)
                }
            }
        }
        LaunchedEffect(focus, games.size, loop, prefetchSizes, prefetchWide, density, cardArtType, if (needsLookup) physicalMedia else null, if (needsLookup) screenshots else null) {
            kotlinx.coroutines.delay(80)
            val loader = context.imageLoader
            val handles = ArrayList<coil.request.Disposable>()
            try {
                fun queue(url: String?, kind: ArtKind) {
                    // (Update 156: the carousel look warms its own covers in its own shape — GameCarousel.)
                    if (url == null || carousel) return
                    val px = cardArtPx(prefetchSizes, prefetchWide, kind, density)
                    handles += loader.enqueue(artRequest(context, url, kind, px, fadeMillis = 0))
                }
                // Fewer neighbours on small handhelds (each decoded cover stays in memory).
                val reach = if (com.shelf.launcher.device.MemoryBudget.small(context)) 2 else 3
                for (i in (focus - reach)..(focus + reach + 1)) {
                    // Update 156: going round, the games coming in past the last one (the first ones) are warmed too, so
                    // their art is there as they slide in.
                    val game = games.getOrNull(if (loop) i.mod(games.size) else i) ?: continue
                    val gArt = artForGame(cardArtType, game.platform, hiddenMedia, mixedRow, gameKey = game.stableKey)
                    val override = artOverrides[game.stableKey]
                    val gameArt = art[game.stableKey]
                    val cover = override?.cover ?: gameArt?.gridUrl
                    val wide = override?.wide ?: gameArt?.wideGridUrl
                    val hero = override?.hero ?: gameArt?.heroUrl
                    // What the card shows when it is not the focused one.
                    val typed = typedArtFor(gArt, physicalMedia[game.stableKey], screenshots[game.stableKey], game.platform, game.stableKey)
                    val shown = cardArtUrl(gArt, false, cover, wide, hero, typed)
                    // Update 157: a disc / cartridge / case look prints its picture in the style shape (mediaArtModel):
                    // that one warmed too (the discs came in blank for a moment).
                    if (shown != null && !carousel && isFreeMedia(gArt, game.platform)) handles += warmStyleArt(context, listOf(shown))
                    when {
                        shown == null -> Unit
                        shown == typed -> queue(shown, cardArtKind(gArt))
                        shown == cover -> queue(shown, ArtKind.COVER)
                        shown == wide -> queue(shown, ArtKind.WIDE)
                        else -> queue(shown, ArtKind.HERO)
                    }
                    // What it shows once focused (only the ones you can reach next).
                    if (prefetchWide && i in (focus - 1)..(focus + 1)) {
                        when {
                            wide != null -> queue(wide, ArtKind.WIDE)
                            hero != null -> queue(hero, ArtKind.HERO)
                            else -> Unit
                        }
                    }
                }
                kotlinx.coroutines.awaitCancellation()
            } finally {
                handles.forEach { it.dispose() }
            }
        }

        // Thumbnails for a wider window (tiny, 2 bytes a pixel): while you hold the D-pad and fly past games
        // whose sharp art isn't decoded yet, their thumbnail shows instead of an empty dark card.
        // Re-queued only every few steps, and the ones already in memory are skipped.
        LaunchedEffect(focus / 6, games.size, cardArtType, loop) {
            val reach = if (com.shelf.launcher.device.MemoryBudget.small(context)) 10 else 16
            val urls = ((focus - reach)..(focus + reach)).mapNotNull { i ->
                // Update 156: past the ends of a row going round, the games from the other end.
                val game = games.getOrNull(if (loop) i.mod(games.size) else i) ?: return@mapNotNull null
                val gArt = artForGame(cardArtType, game.platform, hiddenMedia, mixedRow, gameKey = game.stableKey)
                val override = artOverrides[game.stableKey]
                val gameArt = art[game.stableKey]
                val typed = typedArtFor(gArt, physicalMedia[game.stableKey], screenshots[game.stableKey], game.platform, game.stableKey)
                cardArtUrl(gArt, false, override?.cover ?: gameArt?.gridUrl, override?.wide ?: gameArt?.wideGridUrl, override?.hero ?: gameArt?.heroUrl, typed)
            }
            val handles = prefetchThumbs(context, urls)
            try { kotlinx.coroutines.awaitCancellation() } finally {
                // Let the ones already running finish a little longer: they are what fast scrolling lands on.
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { kotlinx.coroutines.delay(1500) }
                handles.forEach { it.dispose() }
            }
        }

        val currentFocus by rememberUpdatedState(focus)
        val currentLastIndex by rememberUpdatedState(games.lastIndex)
        val currentOnFocus by rememberUpdatedState(onFocus)

        val focusedPlaytime = playtime[games[focus].stableKey]?.takeIf { it > 0L && look.playtimeOnHome }
        // One line for the game you're on — its name and play time — above
        // or under the whole row, never on a card. Its space is always kept,
        // so moving between games with and without a play time never nudges
        // the row up or down.
        val placement = LocalTitleOptions.current.placement
        val nameOnLine = placement != com.shelf.launcher.data.TitlePlacement.HIDDEN &&
            (placement != com.shelf.launcher.data.TitlePlacement.INSIDE || !look.focusedWide || (bannerFree && !pinnedFirst) || (pinnedFirst && focus > 0))
        val lineShown = nameOnLine || look.playtimeOnHome
        // Right on top of the capsule — or under the row (Game names › Side).
        val lineAbove = !look.nameBelow
        val captionLine: @Composable () -> Unit = {
            val focusedKey = games[focus].stableKey
            FocusedLine(
                logoUrl = if (nameOnLine && LocalTitleOptions.current.showLogos) {
                    artOverrides[focusedKey]?.logo ?: art[focusedKey]?.logoUrl
                } else null,
                name = if (nameOnLine) LocalTitleOptions.current.display(games[focus].name) else null,
                playtimeMs = focusedPlaytime,
                // (Update 144: in the carousel too it starts at the left — centred, a long name ran under the game's big logo.)
                centered = !carousel && !pinnedFirst && look.focusPosition == com.shelf.launcher.data.FocusPosition.CENTER,
                // Lined up with the cards' left edge (same as the category label) — or, past the fixed
                // first capsule, with the cover you're on next to it.
                start = dockTextStart(),
                // The name hugs the category label when that's right next to it (above the name above the row, or
                // under the name under the row) — else it hugs the row — so there's never a big gap between them.
                // Update 153: also above a row at the bottom of the screen: the room kept for a second line (or a logo the
                // game doesn't have) showed as a big gap between the category and the name. A changing height there
                // only moves the category label above it, never the row.
                wrap = !lineAbove || rowAtBottom,
                vertical = if (lineAbove) {
                    if (categoryBelow) androidx.compose.ui.Alignment.Bottom else androidx.compose.ui.Alignment.Top
                } else {
                    if (categoryBelow) androidx.compose.ui.Alignment.Bottom else androidx.compose.ui.Alignment.Top
                }
            )
        }
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().categorySlide()) {
        if (lineShown && lineAbove) captionLine()
        // Update 141: the carousel look is the same carousel as the Grid's, TV room's and Side menu's (GameCarousel): every
        // view, every look (spines aside at their own size with the game you're on big in the middle, cartridges, discs,
        // 3D models turned to their side, cases), and moving between games glides. It used to be this row's own
        // approximation: spines aside blown up to many times their size, the middle game small, and the game you
        // moved to just swapping in.
        if (carousel) {
            // Its middle game as big as the row's game you're on (the carousel makes it 84 % of its height × Game size).
            val sizeK = (com.shelf.launcher.data.Features.carouselSize.state.value / 100f).coerceAtLeast(0.3f)
            val boxH = sizes.bigCover.height / (0.84f * sizeK)
            GameCarousel(
                games = games, focus = focus, art = art, overrides = artOverrides,
                onSelect = { onFocus(it) }, onOpen = { games.getOrNull(focus)?.let(onLaunch) },
                cardArtType = cardArtType, physicalMedia = physicalMedia, screenshots = screenshots,
                onNeedPhysical = onNeedPhysical,
                modifier = Modifier.fillMaxWidth().height(boxH),
                showName = false, loop = look.loopRow, loopTick = loopTick, loopStep = loopStep
            )
        } else Box(
            Modifier
                .fillMaxWidth()
                .height(rowHeight)
                // Swiping still works on touch: every card-width of drag moves the
                // focus by one, same as a D-pad press.
                // Keyed on the step so a new card size (Appearance) takes effect at once.
                .pointerInput(stride) {
                    val stepPx = stride.toPx().coerceAtLeast(1f)
                    var accumulated = 0f
                    var target = 0
                    detectHorizontalDragGestures(
                        onDragStart = {
                            accumulated = 0f
                            target = currentFocus
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            accumulated += dragAmount
                            while (accumulated <= -stepPx) {
                                accumulated += stepPx
                                target = (target + 1).coerceAtMost(currentLastIndex)
                                currentOnFocus(target)
                            }
                            while (accumulated >= stepPx) {
                                accumulated -= stepPx
                                target = (target - 1).coerceAtLeast(0)
                                currentOnFocus(target)
                            }
                        }
                    )
                }
        ) {
            // The fixed first capsule is always there, however far along the row you are.
            val slots = (firstIndex..lastIndex).toList()
            // Update 136: the fixed wide place (see [fixedSlot]) holds the game you're on; the row only draws the ones after it.
            if (fixedSlot) key("fixedSlot") {
                // Update 137: moving on, the next game slides into the wide place from the right (back: from the left)
                // while the one you were on slides out — it used to just appear (Settings › Home › Animations › Moving between games).
                val swap = com.shelf.launcher.data.Features.capsuleSwap.state.value
                androidx.compose.animation.AnimatedContent(
                    // Update 156: on a row going round, the place on the endless row: from the last game on to the first is
                    // a step forward (it slid in backwards when compared by the games' indexes).
                    // (A single game has nothing to go round to: it stays put.)
                    targetState = if (loop && games.size >= 2) virtualFocus else focus,
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.CenterStart)
                        .zIndex(1f)
                        .offset { IntOffset(focusX.roundToPx(), 0) },
                    transitionSpec = {
                        val dir = if (targetState >= initialState) 1 else -1
                        when (swap) {
                            com.shelf.launcher.data.Features.CapsuleSwap.NONE ->
                                androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                            com.shelf.launcher.data.Features.CapsuleSwap.FADE ->
                                androidx.compose.animation.fadeIn(tween(220)) togetherWith androidx.compose.animation.fadeOut(tween(180))
                            com.shelf.launcher.data.Features.CapsuleSwap.SLIDE ->
                                (androidx.compose.animation.slideInHorizontally(tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 3 * dir } +
                                    androidx.compose.animation.fadeIn(tween(220))) togetherWith
                                    (androidx.compose.animation.slideOutHorizontally(tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -it / 3 * dir } +
                                        androidx.compose.animation.fadeOut(tween(200)))
                        }.using(androidx.compose.animation.SizeTransform(clip = false))
                    },
                    label = "fixedSlot"
                ) { v ->
                // Update 156: [v] is the place on the endless row when it goes round; its game is [v] mod the count.
                val f = if (loop) v.mod(games.size) else v.coerceIn(0, games.lastIndex)
                val game = games[f.coerceIn(0, games.lastIndex)]
                GameCard(
                    game = game,
                    art = art[game.stableKey],
                    focused = true,
                    lastPlayedAt = lastPlayedAt[game.stableKey],
                    raProgress = raProgress[game.stableKey],
                    onFocused = { onFocus(f) },
                    onLaunch = { onLaunch(game) },
                    onOpenOptions = { onOpenOptions(game) },
                    onViewAchievements = if (game.raGameId != null) {
                        { onViewAchievements(game) }
                    } else null,
                    isFavorite = game.stableKey in favorites,
                    onToggleFavorite = { onToggleFavorite(game) },
                    artOverride = artOverrides[game.stableKey],
                    cardArtType = artForGame(cardArtType, game.platform, hiddenMedia, mixedRow, gameKey = game.stableKey),
                    typedArtUrl = typedArtFor(artForGame(cardArtType, game.platform, hiddenMedia, mixedRow, gameKey = game.stableKey), physicalMedia[game.stableKey], screenshots[game.stableKey], game.platform, game.stableKey),
                    realCase = realRef,
                    mixed = mixedRow,
                    asDisc = cardArtType == com.shelf.launcher.data.CardArtType.DISC,
                    fixedSlot = true,
                    badgeMode = badgeMode,
                    badgeNextToTitle = badgeNextToTitle,
                    recentUnlockTitle = recentUnlocks
                        .firstOrNull { it.GameID != null && it.GameID == game.raGameId }
                        ?.Title,
                    onCycleBadgeMode = onCycleBadgeMode,
                    award = awards[game.stableKey],
                    onArtTop = onArtTop,
                    living = livingLook(
                        lastPlayed = lastPlayedAt[game.stableKey], progress = raProgress[game.stableKey],
                        gameKey = game.stableKey,
                        freshUnlock = game.raGameId?.let { id -> seenAll[com.shelf.launcher.data.AchievementSeen.keyRa(id)]?.glow?.isNotEmpty() } == true ||
                            recentUnlocks.any { it.GameID != null && it.GameID == game.raGameId }
                    ),
                    modifier = Modifier
                        .focusedCard()
                        // Update 202: the game you're on (its cover, box, cartridge or disc) moved / resized in Edit Home.
                        .editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.CLASSIC, "media"), HomeWidgets.mediaLabel())
                        .then(Modifier.graphicsLayer {
                            val extra = ((popNow - 1f) * 0.07f).coerceIn(0f, 0.22f)
                            scaleX = 1f + extra; scaleY = 1f + extra
                        })
                )
                }
            }
            for (slot in slots) {
                // In a looping row [slot] runs past the ends and wraps around.
                val index = if (loop) slot.mod(games.size) else slot
                val game = games[index]
                val rel = slot - (if (loop) virtualFocus else focus)
                if (fixedSlot && rel <= 0) continue
                key(if (loop) "loop:$slot" else game.stableKey) {
                    val carouselSide = Carousel.side
                    // Update 144: where a card stands [r] places from the game you're on; the row glides between them with
                    // one shared position (rowPos, like the carousel) instead of a spring per card — the per-card springs
                    // never showed on the handhelds (measured on the Odin: the whole row jumped in one frame).
                    fun xAt(r: Int): Dp = when {
                        // Carousel: centred on the middle of the screen, the others tucked behind it.
                        carousel -> {
                            val w = if (r == 0) bannerWidth else coverWidth
                            // (Update 137: in the carousel view picked — Wheel, Deck, Flat row… — not only Tucked behind.)
                            // (Update 146: the Receding row's big game sits near the left edge, ~7 % in, the rest stepping back to its right.)
                            val mid = if (Carousel.flatView == CarouselView.RECEDE) (screenWidth * 0.07f).coerceAtLeast(12.dp) + bannerWidth / 2 else screenWidth / 2
                            mid + bannerWidth * Carousel.placeFlat(r.toFloat(), carouselSide).x - w / 2
                        }
                        // Left end: the games before sit fully off-screen — the frame/glow gap in front of the
                        // focused card used to show a sliver of the previous one.
                        r < 0 -> (if (look.focusPosition == com.shelf.launcher.data.FocusPosition.CENTER) focusX else 0.dp) + stride * r
                        r == 0 -> focusX
                        else -> focusX + glideBanner + spacing + glideDiscRoom + stride * (r - 1)
                    }
                    val slotF = (if (loop) slot else index).toFloat()
                    // Update 112: the game you're on growing out of the row (see below), eased in and out.
                    // Update 146, wide first game: where game [i] stands when the row is on game [at] — the game you're on
                    // at the row's start, the ones before it a cover's width each to its left (game 1 its wide width),
                    // the ones after past the bigger cover you're on.
                    fun pinX(i: Int, at: Int): Dp = when {
                        at <= 0 -> if (i == 0) focusX else focusX + bannerWidth + spacing + stride * (i - 1)
                        i == at -> focusX
                        i > at -> focusX + bigCoverWidth + spacing + stride * (i - at - 1)
                        i >= 1 -> focusX + stride * (i - at)
                        else -> focusX + stride * (1 - at) - spacing - bannerWidth
                    }
                    val popLift by androidx.compose.animation.core.animateFloatAsState(if (rel == 0) 1f else 0f, androidx.compose.animation.core.tween(160), label = "dockPop")
                    // Carousel: distance from the game you're on, gliding with the cards.
                    val relAnim by androidx.compose.animation.core.animateFloatAsState(rel.toFloat(), dockMotion(), label = "dockRel")
                    // Lean away from the game you're on (Game row › Tilt).
                    val tilt by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = when {
                            !look.tiltOthers || rel == 0 -> 0f
                            rel < 0 -> 24f
                            else -> -24f
                        },
                        animationSpec = dockMotion(),
                        label = "dockTilt"
                    )
                    // Update 203: a cartridge turns as a solid piece (its thickness showing) — the card hands it the turn
                    // instead of turning the flat picture (SolidPiece.kt).
                    val solidCart = (carousel || look.tiltOthers) &&
                        artForGame(cardArtType, game.platform, hiddenMedia, mixedRow, gameKey = game.stableKey) == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC
                    ProvidePieceTurn(if (solidCart) PieceTurn({ if (carousel) Carousel.placeFlat(relAnim, carouselSide).turnY else tilt }, 14f) else null) {
                    GameCard(
                        game = game,
                        art = art[game.stableKey],
                        focused = rel == 0,
                        lastPlayedAt = lastPlayedAt[game.stableKey],
                        raProgress = raProgress[game.stableKey],
                        onFocused = { onFocus(index) },
                        onLaunch = { onLaunch(game) },
                        onOpenOptions = { onOpenOptions(game) },
                        onViewAchievements = if (game.raGameId != null) {
                            { onViewAchievements(game) }
                        } else null,
                        isFavorite = game.stableKey in favorites,
                        onToggleFavorite = { onToggleFavorite(game) },
                        artOverride = artOverrides[game.stableKey],
                        cardArtType = artForGame(cardArtType, game.platform, hiddenMedia, mixedRow, gameKey = game.stableKey),
                        typedArtUrl = typedArtFor(artForGame(cardArtType, game.platform, hiddenMedia, mixedRow, gameKey = game.stableKey), physicalMedia[game.stableKey], screenshots[game.stableKey], game.platform, game.stableKey),
                        realCase = realRef,
                        mixed = mixedRow,
                        asDisc = cardArtType == com.shelf.launcher.data.CardArtType.DISC,
                        pinnedWide = pinnedFirst && index == 0,
                        allowWide = !carousel && (!pinnedFirst || index == 0),
                        badgeMode = badgeMode,
                        badgeNextToTitle = badgeNextToTitle,
                        recentUnlockTitle = recentUnlocks
                            .firstOrNull { it.GameID != null && it.GameID == game.raGameId }
                            ?.Title,
                        onCycleBadgeMode = onCycleBadgeMode,
                        award = awards[game.stableKey],
                        onArtTop = if (rel == 0) onArtTop else null,
                        living = livingLook(
                            lastPlayed = lastPlayedAt[game.stableKey], progress = raProgress[game.stableKey],
                            gameKey = game.stableKey,
                            freshUnlock = game.raGameId?.let { id -> seenAll[com.shelf.launcher.data.AchievementSeen.keyRa(id)]?.glow?.isNotEmpty() } == true ||
                                recentUnlocks.any { it.GameID != null && it.GameID == game.raGameId }
                        ),
                        // Covers sit centred on the game you're on, not hanging
                        // from its top edge.
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.CenterStart)
                            // The focused card's frame / bolts reach past its edge: keep it above its neighbours.
                            .zIndex(if (carousel) Carousel.placeFlat(relAnim, Carousel.side).z else if (rel == 0) 1f else 0f)
                            .offset {
                                // The card's place for where the row is now (between two games while it glides).
                                val xd = if (pinnedFirst) {
                                    val p = rowPos.value
                                    val a0 = kotlin.math.floor(p).toInt()
                                    lerp(pinX(index, a0), pinX(index, a0 + 1), p - a0)
                                } else {
                                    val rf = slotF - rowPos.value
                                    val f0 = kotlin.math.floor(rf).toInt()
                                    lerp(xAt(f0), xAt(f0 + 1), rf - f0)
                                }
                                IntOffset(xd.roundToPx(), 0)
                            }
                            // The game you're on reports where it is (cover light, game window; no layout change).
                            .then(if (rel == 0) Modifier.focusedCard() else Modifier)
                            // Update 202: the game you're on (its cover, box, cartridge or disc) moved / resized in Edit Home.
                            .then(if (rel == 0) Modifier.editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.CLASSIC, "media"), HomeWidgets.mediaLabel()) else Modifier)
                            // Update 112: "How far the game you're on stands out" (Home › Animations): above 100 % it grows
                            // a little out of the row (from its middle, so a wide capsule grows evenly too).
                            .then(if (!carousel) Modifier.graphicsLayer {
                                val extra = ((popNow - 1f) * 0.07f).coerceIn(0f, 0.22f) * popLift
                                scaleX = 1f + extra; scaleY = 1f + extra
                            } else Modifier)
                            .then(
                                if (carousel) Modifier.graphicsLayer {
                                    val pl = Carousel.placeFlat(relAnim, carouselSide)
                                    // Smaller the further out (against the middle cover), turned / leaning / lower as the view
                                    // places it, and only a few each side.
                                    val sc = if (rel == 0) pl.scale else bannerWidth.value * pl.scale / coverWidth.value
                                    scaleX = sc; scaleY = sc
                                    alpha = pl.alpha
                                    translationY = pl.y * size.height
                                    rotationY = if (solidCart) 0f else pl.turnY
                                    rotationZ = pl.turnZ
                                    if (rotationY != 0f) flatTextFor3d()
                                    cameraDistance = 14f * density
                                } else if (look.tiltOthers) Modifier.graphicsLayer {
                                    rotationY = if (solidCart) 0f else tilt
                                    flatTextFor3d()
                                    cameraDistance = 14f * density
                                    // Hinged on the side facing the game you're on.
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                                        if (rel < 0) 1f else 0f, 0.5f
                                    )
                                } else Modifier
                            )
                    )
                    }
                }
            }
        }
        // When the game you're on stays a cover, its name goes under the row.
        if (lineShown && !lineAbove) captionLine()
        }
    }
}

/** "Wide for the first game only" is on (LauncherScreen provides it): see [GameDock]'s pinned first capsule. */
internal val LocalPinnedFirstWide = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * Where text above/under the row starts so it lines up with the left edge of
 * the game you're on — used by the name line here and by the category label
 * (LauncherScreen), so the two always sit exactly on top of each other.
 */
@Composable
internal fun dockTextStart(): Dp {
    val look = LocalCardLook.current
    val spacing = maxOf(DockSpacing * 0.36f, cardSizes().cover.width / 27.5f)
    val framed = look.focusBorder || look.focusEffect != com.shelf.launcher.data.FocusEffect.NONE
    val cardEdge = if (framed) maxOf(spacing, 6.dp) else 0.dp
    // Never glued to the screen edge.
    return maxOf(cardEdge, 8.dp)
}

/**
 * The style behind the focused name and the category label (Appearance ›
 * Name & category › Style): like the cards, a dark pill, a theme-coloured
 * pill, frosted glass, an accent bar at the side — or nothing.
 */
@Composable
internal fun Modifier.nameBackdrop(backdrop: com.shelf.launcher.data.NameBackdrop): Modifier {
    val accent = com.shelf.launcher.ui.theme.ShelfTheme.accent
    val look = LocalCardLook.current
    val pill = androidx.compose.foundation.shape.RoundedCornerShape(50)
    return when (backdrop) {
        // Same surface, corners and accent frame as the game cards.
        com.shelf.launcher.data.NameBackdrop.CARD -> {
            val shape = androidx.compose.foundation.shape.RoundedCornerShape((look.cornerDp * 0.6f).dp)
            this
                .clip(shape)
                .background(com.shelf.launcher.ui.theme.ShelfTheme.colors.surface.copy(alpha = 0.88f))
                .border(
                    if (look.focusBorder) 1.5.dp else 1.dp,
                    if (look.focusBorder) accent.copy(alpha = 0.85f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f),
                    shape
                )
                .padding(horizontal = 12.dp, vertical = 4.dp)
        }
        com.shelf.launcher.data.NameBackdrop.DARK -> this
            .clip(pill)
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 3.dp)
        com.shelf.launcher.data.NameBackdrop.THEME -> this
            .clip(pill)
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.22f))
                )
            )
            .border(1.dp, accent.copy(alpha = 0.8f), pill)
            .padding(horizontal = 12.dp, vertical = 3.dp)
        com.shelf.launcher.data.NameBackdrop.GLASS -> this
            .clip(pill)
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.16f))
            .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.28f), pill)
            .padding(horizontal = 12.dp, vertical = 3.dp)
        com.shelf.launcher.data.NameBackdrop.BAR -> this
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f), androidx.compose.ui.graphics.Color.Transparent)
                )
            )
            .drawBehind {
                drawRect(accent, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
            }
            .padding(start = 11.dp, end = 16.dp, top = 3.dp, bottom = 3.dp)
        com.shelf.launcher.data.NameBackdrop.RULE -> this
            .drawBehind {
                val h = 2.dp.toPx()
                drawRect(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.5f), androidx.compose.ui.graphics.Color.Transparent)),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - h),
                    size = androidx.compose.ui.geometry.Size(size.width * 1.3f, h)
                )
            }
            .padding(bottom = 6.dp)
        com.shelf.launcher.data.NameBackdrop.EDGE_FADE -> this
            .drawBehind {
                // Reaches past the text on both sides and fades out, so there's no edge to see.
                drawRect(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        0f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                        0.65f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f),
                        1f to androidx.compose.ui.graphics.Color.Transparent,
                        startX = -48.dp.toPx(), endX = size.width + 90.dp.toPx()
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(-48.dp.toPx(), -6.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width + 138.dp.toPx(), size.height + 12.dp.toPx())
                )
            }
            .padding(horizontal = 4.dp, vertical = 2.dp)
        com.shelf.launcher.data.NameBackdrop.TAG -> this
            .drawBehind {
                // A parallelogram: the left and right sides lean right by a third of the height.
                val lean = size.height * 0.35f
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(lean, 0f); lineTo(size.width + lean * 0.4f, 0f)
                    lineTo(size.width + lean * 0.4f - lean, size.height); lineTo(0f, size.height); close()
                }
                drawPath(path, androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.75f))))
                drawRect(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                    topLeft = androidx.compose.ui.geometry.Offset(lean * 0.2f, size.height - 2.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.35f, 2.dp.toPx()))
            }
            .padding(start = 14.dp, end = 16.dp, top = 3.dp, bottom = 3.dp)
        com.shelf.launcher.data.NameBackdrop.SHADOW, com.shelf.launcher.data.NameBackdrop.NONE,
        com.shelf.launcher.data.NameBackdrop.EMBOSS, com.shelf.launcher.data.NameBackdrop.GLOW,
        com.shelf.launcher.data.NameBackdrop.OUTLINE, com.shelf.launcher.data.NameBackdrop.NEON -> this
    }
}

/** How far [nameBackdrop] pushes the text in from its left edge (so a backed label can line up with a bare one). */
internal fun nameBackdropInset(backdrop: com.shelf.launcher.data.NameBackdrop): androidx.compose.ui.unit.Dp = when (backdrop) {
    com.shelf.launcher.data.NameBackdrop.CARD, com.shelf.launcher.data.NameBackdrop.DARK,
    com.shelf.launcher.data.NameBackdrop.THEME, com.shelf.launcher.data.NameBackdrop.GLASS -> 12.dp
    com.shelf.launcher.data.NameBackdrop.BAR -> 11.dp
    com.shelf.launcher.data.NameBackdrop.EDGE_FADE -> 4.dp
    com.shelf.launcher.data.NameBackdrop.TAG -> 14.dp
    else -> 0.dp
}

/** Text style for the name / category: a soft shadow under the letters with "Soft shadow". */
@Composable
internal fun nameTextStyle(backdrop: com.shelf.launcher.data.NameBackdrop): androidx.compose.ui.text.TextStyle =
    // On top of the interface font (32-bit everywhere included), not instead of it.
    uiFontStyle().merge(nameTextStyleOwn(backdrop).let { own ->
        // Settings › Dock › "Blur behind the names": scales the shadow / glow round the letters (0 = none).
        val k = com.shelf.launcher.data.Features.nameBlur.state.value / 100f
        val sh = own.shadow
        if (sh == null || k >= 1f) own
        else own.copy(shadow = if (k <= 0f) null else sh.copy(blurRadius = sh.blurRadius * k, color = sh.color.copy(alpha = sh.color.alpha * (0.4f + 0.6f * k))))
    })

@Composable
private fun nameTextStyleOwn(backdrop: com.shelf.launcher.data.NameBackdrop): androidx.compose.ui.text.TextStyle = when (backdrop) {
    // Update 112: "Names over pictures › Dark edge": a tight, solid dark edge round the letters (readable on any picture).
    com.shelf.launcher.data.NameBackdrop.SHADOW, com.shelf.launcher.data.NameBackdrop.EDGE_FADE, com.shelf.launcher.data.NameBackdrop.RULE ->
        if (com.shelf.launcher.data.Features.nameContrast.state.value != com.shelf.launcher.data.Features.NameContrast.SOFT) androidx.compose.ui.text.TextStyle(
            shadow = androidx.compose.ui.graphics.Shadow(
                color = androidx.compose.ui.graphics.Color.Black,
                offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                blurRadius = 4f
            )
        ) else androidx.compose.ui.text.TextStyle(
            shadow = androidx.compose.ui.graphics.Shadow(
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.9f),
                offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                blurRadius = 10f
            )
        )
    // Pressed into the picture: slightly see-through letters with a dark lower edge.
    com.shelf.launcher.data.NameBackdrop.EMBOSS -> androidx.compose.ui.text.TextStyle(
        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.88f),
        shadow = androidx.compose.ui.graphics.Shadow(
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f),
            offset = androidx.compose.ui.geometry.Offset(0f, 3f),
            blurRadius = 2f
        ),
        letterSpacing = androidx.compose.ui.unit.TextUnit(0.6f, androidx.compose.ui.unit.TextUnitType.Sp)
    )
    // Light: the accent glowing round the letters, into the art.
    com.shelf.launcher.data.NameBackdrop.GLOW -> androidx.compose.ui.text.TextStyle(
        shadow = androidx.compose.ui.graphics.Shadow(
            color = com.shelf.launcher.ui.theme.ShelfTheme.accent.copy(alpha = 0.95f),
            offset = androidx.compose.ui.geometry.Offset.Zero,
            blurRadius = 26f
        )
    )
    // Hollow letters: only their outline is drawn, with a dark halo so they read on bright art.
    com.shelf.launcher.data.NameBackdrop.OUTLINE -> androidx.compose.ui.text.TextStyle(
        drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        shadow = androidx.compose.ui.graphics.Shadow(
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.95f),
            offset = androidx.compose.ui.geometry.Offset(0f, 1f),
            blurRadius = 6f
        ),
        letterSpacing = androidx.compose.ui.unit.TextUnit(0.8f, androidx.compose.ui.unit.TextUnitType.Sp)
    )
    // A neon tube: white-hot letters in a wide accent halo.
    com.shelf.launcher.data.NameBackdrop.NEON -> androidx.compose.ui.text.TextStyle(
        color = androidx.compose.ui.graphics.Color(0xFFFFF8F0),
        shadow = androidx.compose.ui.graphics.Shadow(
            color = com.shelf.launcher.ui.theme.ShelfTheme.accent,
            offset = androidx.compose.ui.geometry.Offset.Zero,
            blurRadius = 18f
        ),
        letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp)
    )
    else -> androidx.compose.ui.text.TextStyle.Default
}

/** Side padding for centred text above/under the row. */
internal val DockCenteredPadding = 24.dp

/** The focused game's name (with its logo) and play time, at a fixed height. */
@Composable
private fun FocusedLine(
    name: String?,
    playtimeMs: Long?,
    centered: Boolean,
    logoUrl: String? = null,
    start: Dp = DockStartPadding,
    /** Where the name sits in its fixed-height line: against the category label (or the row) next to it. */
    vertical: androidx.compose.ui.Alignment.Vertical = androidx.compose.ui.Alignment.CenterVertically,
    /**
     * Only as tall as what it shows (no room kept for a second line or a logo the game doesn't have). Used under
     * the row: there a changing height moves nothing but the category label, which then sits right under the name.
     */
    wrap: Boolean = false
) {
    val titles = LocalTitleOptions.current
    // Taller when the logo sits above / under the name — decided by the
    // setting, not by whether this game has a logo, so the row never jumps.
    val stacked = titles.showLogos && (titles.logoPlacement == com.shelf.launcher.data.LogoPlacement.ABOVE ||
        titles.logoPlacement == com.shelf.launcher.data.LogoPlacement.BELOW)
    // Logo on its own: noticeably bigger.
    val instead = titles.showLogos && titles.logoPlacement == com.shelf.launcher.data.LogoPlacement.INSTEAD
    val backdrop = LocalCardLook.current.nameBackdrop
    val capsule = backdrop != com.shelf.launcher.data.NameBackdrop.NONE &&
        backdrop != com.shelf.launcher.data.NameBackdrop.SHADOW &&
        backdrop != com.shelf.launcher.data.NameBackdrop.EMBOSS &&
        backdrop != com.shelf.launcher.data.NameBackdrop.GLOW
    val shadowStyle = nameTextStyle(backdrop)
    // Room for two lines of the name at the system's font size too (a bigger font used to push the
    // second line out over the cards); still a fixed height, so the row never jumps between games.
    val twoLines = with(androidx.compose.ui.platform.LocalDensity.current) { 18.sp.toDp() } * 2 + 8.dp
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (wrap) Modifier.heightIn(min = 24.dp).padding(vertical = 2.dp)
                else Modifier.height(
                    (if (stacked) maxOf(78.dp, twoLines + 30.dp) else if (instead) 50.dp else maxOf(44.dp, twoLines)) + (if (capsule) 6.dp else 0.dp)
                )
            )
            .padding(start = if (centered) DockCenteredPadding else start, end = DockCenteredPadding),
        horizontalArrangement = if (centered) androidx.compose.foundation.layout.Arrangement.Center
        else androidx.compose.foundation.layout.Arrangement.Start,
        verticalAlignment = vertical
    ) {
    // Optional backdrop behind name + play time, so it reads on white heroes
    // (Appearance › Name & category › Style) — shared with the category label.
    val hasText = name != null || playtimeMs != null
    androidx.compose.foundation.layout.Row(
        Modifier
            .weight(1f, fill = false)
            .then(if (hasText) Modifier.nameBackdrop(backdrop) else Modifier),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        if (name != null) {
            LogoAndName(
                logoUrl = logoUrl,
                placement = titles.logoPlacement,
                logoHeight = if (instead) 44.dp else 26.dp,
                logoMaxWidth = if (instead) 200.dp else 120.dp,
                centered = centered,
                modifier = Modifier.weight(1f, fill = false)
            ) { m ->
                androidx.compose.material3.Text(
                    name,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    // Long names wrap onto a second line instead of ending in "…".
                    maxLines = 2,
                    lineHeight = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp),
                    textAlign = if (centered) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = shadowStyle,
                    modifier = m
                )
            }
        }
        if (playtimeMs != null) {
            androidx.compose.material3.Text(
                (if (name != null) "   ⏱ " else "⏱ ") + com.shelf.launcher.data.PlaytimeStore.format(playtimeMs),
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp),
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                style = shadowStyle
            )
        }
    }
    }
}
