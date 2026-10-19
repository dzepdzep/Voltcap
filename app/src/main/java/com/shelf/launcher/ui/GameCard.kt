package com.shelf.launcher.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.CardArtType
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.GameArt
import com.shelf.launcher.data.RaGameProgress
import com.shelf.launcher.data.SourceType
import com.shelf.launcher.data.RaAward
import com.shelf.launcher.ui.theme.ShelfTheme

// Sized conservatively for handheld landscape screens, which can be as
// short as ~380-400dp tall — shrunk twice now based on real-device testing,
// since the dock cluster (platform row + dock + control bar) needs to leave
// clear room below the status bar rather than crowding the whole screen.
// Internal (not private) because GameDock positions cards from these widths.
internal val CoverSize = 72.dp to 100.dp
internal val BannerSize = 220.dp to 116.dp

/** Update 115: a wide capsule's width / height (SteamGridDB's 920 × 430), and its height against a cover's in the row. */
internal const val CAPSULE_ASPECT = 920f / 430f
internal const val CAPSULE_HEIGHT = 0.66f

/** Card sizes for the current look: a cover, and the game you're on. */
internal data class CardSizes(
    val cover: androidx.compose.ui.unit.DpSize, val focused: androidx.compose.ui.unit.DpSize,
    /** The game you're on kept a cover (a bit bigger than a cover; update 113: with the Spines look, a cover among spines). */
    val bigCover: androidx.compose.ui.unit.DpSize = androidx.compose.ui.unit.DpSize(cover.width * 1.15f, cover.height * 1.15f)
)

@Composable
internal fun cardSizes(): CardSizes {
    val look = LocalCardLook.current
    // Sized from the screen, like the Steam Deck home screen: 2:3 covers, and
    // a wide capsule (920×430) that's just as tall as them — no step between
    // the two, only a small even gap.
    val screen = screenSizeDp()
    var coverHeight = screen.height * look.heightFraction
    // Update 129: how much bigger the game you're on is follows "How far the game you're on stands out" (Home ›
    // Animations, per layout too): 100 % and more = 15 % bigger, 0 % = the same size as the others.
    MotionPrefs.observe()
    val grow = 1f + 0.15f * MotionPrefs.pop().coerceIn(0f, 1f)
    // Layout options › Games across the screen: the size that fits that many (the wide game you're on counts as
    // one), never tinier than ~14 % or taller than half the screen.
    // Update 115, "Wide capsules" look: every game a real wide capsule (920 × 430), a bit lower than a cover so the
    // row still fits; the game you're on a bigger one.
    val capH = CAPSULE_HEIGHT
    if (look.rowGames > 0) {
        val aspect = if (look.squareTiles) 1f else if (look.spines) SPINE_ASPECT else if (look.capsules) capH * CAPSULE_ASPECT else 2f / 3f
        val focusK = if (look.capsules) capH * (if (look.focusedWide) 1.2f else 1.15f) * CAPSULE_ASPECT
            else if (look.focusedWide) 1.03f * 920f / 430f else (if (look.squareTiles) 1f else 2f / 3f) * 1.15f
        val perCover = aspect * (1f + 1f / 27.5f)
        coverHeight = (screen.width / (focusK + (look.rowGames - 1) * perCover + 0.04f))
            .coerceIn(screen.height * 0.14f, screen.height * 0.5f)
    }
    if (look.capsules) {
        val h = coverHeight * capH
        val cover = androidx.compose.ui.unit.DpSize(h * CAPSULE_ASPECT, h)
        val big = androidx.compose.ui.unit.DpSize(h * grow * CAPSULE_ASPECT, h * grow)
        val focused = if (look.focusedWide) androidx.compose.ui.unit.DpSize(h * 1.2f * CAPSULE_ASPECT, h * 1.2f) else big
        return CardSizes(cover, focused, big)
    }
    val cover = androidx.compose.ui.unit.DpSize(coverHeight * (if (look.squareTiles) 1f else if (look.spines) SPINE_ASPECT else 2f / 3f), coverHeight)
    // A cover, a little bigger than its neighbours (with spines: a real cover's width, not a spine's).
    val bigCover = androidx.compose.ui.unit.DpSize(coverHeight * (if (look.squareTiles) 1f else 2f / 3f) * grow, coverHeight * grow)
    val focused = if (look.focusedWide) {
        val h = coverHeight * 1.03f
        androidx.compose.ui.unit.DpSize(h * (920f / 430f), h)
    } else bigCover
    return CardSizes(cover, focused, bigCover)
}

@Composable
fun GameCard(
    game: Game,
    art: GameArt?,
    focused: Boolean,
    lastPlayedAt: Long?,
    raProgress: RaGameProgress?,
    onFocused: () -> Unit,
    onLaunch: () -> Unit,
    onOpenOptions: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    artOverride: com.shelf.launcher.data.ArtOverride? = null,
    onViewAchievements: (() -> Unit)? = null,
    badgeMode: com.shelf.launcher.AchievementBadgeMode =
        com.shelf.launcher.AchievementBadgeMode.PROGRESS,
    badgeNextToTitle: Boolean = false,
    recentUnlockTitle: String? = null,
    onCycleBadgeMode: (() -> Unit)? = null,
    award: RaAward? = null,
    /** Top edge of this card's art on screen (px), for lining the background up. */
    onArtTop: ((Float) -> Unit)? = null,
    /** Which artwork the card shows (Settings › Appearance › Game row). */
    cardArtType: CardArtType = CardArtType.COVER,
    /** Box / disc / screenshot address for [cardArtType] when it is one of those (null = not loaded / none). */
    typedArtUrl: String? = null,
    /** "Wide for the first game only": this card stays a wide capsule even when you move on, and
     * never animates its size (it's the row's fixed first slot). */
    pinnedWide: Boolean = false,
    /** False: this card stays a (slightly bigger) cover when focused, even if wide capsules are on. */
    allowWide: Boolean = true,
    /** Update 136: the Voltcap row's fixed wide place (game you're on at the left): only its game changes — no size
     * animation, no roll-out of the wide art. */
    fixedSlot: Boolean = false,
    /** Living Library marks (Settings › Library); plain = nothing drawn. */
    living: LivingLook = LivingLook(),
    /** "Box art · real sizes": the biggest case in the row, so this one is drawn at its real size next to it. */
    realCase: RealCaseRef? = null,
    /** The row mixes consoles (Mixed categories: "All CDs" / "All cartridges" use the generic one where a game has none). */
    mixed: Boolean = false,
    /** The layout's card artwork is "Discs only": a cartridge game is drawn as the generic CD. */
    asDisc: Boolean = false,
    modifier: Modifier = Modifier
) {
    com.shelf.launcher.Breadcrumbs.mark("GameCard " + game.name)
    EnsureUserCovers() // (Update 235, #24: the covers you picked, for typedArtFor / CaseArt)
    val look = LocalCardLook.current
    val sizes = cardSizes()
    // The wide banner layout (name, badges on the art) only when the game
    // you're on turns into a wide capsule; as a cover it stays plain.
    // Cartridge / disc / box art drawn as the real object: it stands free (no card behind it, no frame) and the
    // game you're on stays that object — it never opens into a wide capsule.
    val freeMedia = isFreeMedia(cardArtType, game.platform)
    val banner = (focused || pinnedWide) && look.focusedWide && allowWide && !freeMedia
    val cardShape = RoundedCornerShape(look.cornerDp.dp)
    // Focused but kept a cover: a little bigger than its neighbours.
    val bigCover = if (look.focusedWide) sizes.bigCover else sizes.focused
    val sizeSpec: androidx.compose.animation.core.AnimationSpec<androidx.compose.ui.unit.Dp> =
        if (pinnedWide || fixedSlot) androidx.compose.animation.core.snap() else dockMotion()
    val width by animateDpAsState(
        targetValue = when { banner -> sizes.focused.width; focused -> bigCover.width; else -> sizes.cover.width },
        animationSpec = sizeSpec, label = "cardWidth"
    )
    val height by animateDpAsState(
        targetValue = when { banner -> sizes.focused.height; focused -> bigCover.height; else -> sizes.cover.height },
        animationSpec = sizeSpec, label = "cardHeight"
    )
    val titles = LocalTitleOptions.current
    val title = titles.display(game.name)
    val nameInside = titles.placement == com.shelf.launcher.data.TitlePlacement.INSIDE
    val masteredStyle = titles.masteredStyle
    // Golden-border style: the award is the frame itself.
    // Its colour: gold / silver, or the theme's accent (Colours & background).
    // Independent of the coloured frame — switching that off never hides it.
    val accentForAward = ShelfTheme.accent
    val awardFrame = award?.takeIf { masteredStyle == com.shelf.launcher.data.MasteredStyle.BORDER }
        ?.let { if (look.masteredThemeColor) accentForAward else awardTint(it) }
    // Electric / glow effect on the game you're on (Game row › Effect). Lite effects: a still glow
    // instead of crackling arcs, and no pulse (nothing redraws while you just look at the screen).
    val lite = LocalLiteEffects.current
    // (Update 146: the wide first capsule moves with the row now, so it gets the effect like any game you're on.)
    val effect = if (!focused) com.shelf.launcher.data.FocusEffect.NONE
    else if (lite && look.focusEffect == com.shelf.launcher.data.FocusEffect.ELECTRIC) com.shelf.launcher.data.FocusEffect.GLOW
    else look.focusEffect
    // ~20 updates a second (not every display frame), and still while the handheld sits idle.
    val pulse = if (effect == com.shelf.launcher.data.FocusEffect.NONE) 0f
    else rememberFocusPulse(animate = !lite)

    Column(modifier.width(width)) {
    // Latest values for the tap handler, so it never has to restart (a restart mid-tap loses the tap).
    val latestFocused = androidx.compose.runtime.rememberUpdatedState(focused)
    val latestLaunch = androidx.compose.runtime.rememberUpdatedState(onLaunch)
    val latestOnFocused = androidx.compose.runtime.rememberUpdatedState(onFocused)
    // Names of the other games aren't written anywhere — only the game you're
    // on gets its name, once, next to the row (GameDock).
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            // (Update 113: the game window grows out of the game you're on.)
            .gameOrigin(game.stableKey, focused)
            // pointerInput rather than clickable: clickable would register a
            // focus target and put us back in a fight with the root's key
            // handling. Touch selects; tapping the selected card opens it.
            .pointerInput(Unit) {
                detectTapGestures { if (latestFocused.value) latestLaunch.value() else latestOnFocused.value() }
            }
            .then(
                if (onArtTop != null) {
                    Modifier.onGloballyPositioned { onArtTop(it.positionInRoot().y) }
                } else Modifier
            )
            // Still open in the background (RunningGames): a pulsing green glow round the card.
            .runningGlow(game.stableKey in com.shelf.launcher.data.RunningGames.keys, look.cornerDp.dp)
            // Living Library: calmer / alive / fresh unlock / progress, drawn over the art (never moves anything).
            .livingLibrary(living, focused, ShelfTheme.accent, look.cornerDp.toFloat())
            // The free-standing cartridge / disc / box: no card frame, glow or clip — the one you're on gets a
            // light pool under it instead (and is a bit bigger, see bigCover).
            .then(if (freeMedia) Modifier.mediaFloorGlow(focused, ShelfTheme.accent, pulse) else Modifier
            // Soft glow: a real blurred halo round the card (a lighter one under the electric bolts).
            .then(
                when (effect) {
                    com.shelf.launcher.data.FocusEffect.GLOW -> Modifier.softGlow(ShelfTheme.accent, look.cornerDp.dp, 0.55f + 0.6f * pulse)
                    com.shelf.launcher.data.FocusEffect.ELECTRIC -> Modifier.softGlow(ShelfTheme.accent, look.cornerDp.dp, 0.3f + 0.2f * pulse)
                    else -> Modifier
                }
            )
            .shadow(
                // A fixed elevation: animating it re-rendered the shadow every frame (costly on
                // slower GPUs); the pulse lives in the frame's brightness instead.
                elevation = when {
                    effect != com.shelf.launcher.data.FocusEffect.NONE -> 18.dp
                    focused && look.focusBorder -> 16.dp
                    else -> 0.dp
                },
                shape = cardShape,
                ambientColor = ShelfTheme.accentGlow,
                spotColor = ShelfTheme.accentGlow
            )
            .then(
                if (awardFrame != null) {
                    // Metallic frame: dark edges keep it visible on light
                    // themes, the bright middle on dark ones; optionally a
                    // light sweeps across it.
                    Modifier.border(
                        width = if (focused) 3.5.dp else 2.5.dp,
                        brush = awardBrush(awardFrame, titles.masteredShimmer && focused, width, height),
                        shape = cardShape
                    )
                } else if (effect == com.shelf.launcher.data.FocusEffect.ELECTRIC) {
                    // Crackling arcs that jump around the frame (drawn below) —
                    // on purpose nothing like the mastered frame's smooth sweep.
                    Modifier
                        .then(if (look.focusBorder) Modifier.border(1.5.dp, ShelfTheme.accent.copy(alpha = 0.35f + 0.4f * pulse), cardShape) else Modifier)
                        .electricFrame(ShelfTheme.accent, look.cornerDp.dp, pulse = pulse)
                } else {
                    // The accent frame only with Coloured frame on (the soft glow itself is Game row › Effect).
                    val rim = focused && look.focusBorder
                    Modifier.border(
                        width = if (rim) 2.dp else 0.dp,
                        color = if (rim) ShelfTheme.accent.copy(
                            alpha = if (effect == com.shelf.launcher.data.FocusEffect.GLOW) 0.55f + 0.45f * pulse else 1f
                        ) else Color.Transparent,
                        shape = cardShape
                    )
                }
            )
            .clip(cardShape)
            .background(placeholderBrush(game.hue)))
    ) {
        // Unfocused cards show the portrait 600x900 grid; the focused card
        // swaps to the wide ~920x430 capsule so the banner isn't just the
        // portrait cover stretched sideways. Falls back to whichever one
        // exists if SteamGridDB only has one of the two.
        // A per-game override always wins over whatever the scraper returned.
        val coverUrl = artOverride?.cover ?: art?.gridUrl
        val wideUrl = artOverride?.wide ?: art?.wideGridUrl
        val heroUrl = artOverride?.hero ?: art?.heroUrl
        // Wide 920x430 capsules mostly exist for Steam titles — most retro
        // entries on SteamGridDB only have a 600x900 cover. Heroes are wide and
        // far more commonly available, so they're the fallback before giving up
        // and stretching the portrait cover.
        val artUrl = cardArtUrl(cardArtType, banner, coverUrl, wideUrl, heroUrl, typedArtUrl)
        val greyOut = !focused && look.otherGames == com.shelf.launcher.data.OtherGamesLook.GREY
        fun kindOf(url: String) = when (url) {
            typedArtUrl -> cardArtKind(cardArtType)
            coverUrl -> ArtKind.COVER
            wideUrl -> ArtKind.WIDE
            else -> ArtKind.HERO
        }
        // The plain (cover) art is always the bottom layer; when the card opens into a wide capsule,
        // the wide art unrolls over it from the left once it has loaded — instead of popping in.
        // (Update 136: the fixed wide place shows its wide art straight away: no cover under it to roll over.)
        val baseUrl = if (fixedSlot && banner) artUrl else cardArtUrl(cardArtType, false, coverUrl, wideUrl, heroUrl, typedArtUrl)
        val wideLayer = artUrl?.takeIf { banner && it != baseUrl }
        // How the cover turns into the wide capsule (Game look › Cards): a card flip (default), a light sweep, or the wipe.
        // (Update 129: "Wide for the first game only" has its own, off by default.)
        // (Update 141: nor does the fixed first capsule of "Covers · first one wide": it never rolls out.)
        val revealStyle = if (fixedSlot || pinnedWide) com.shelf.launcher.data.Features.CapsuleReveal.NONE
            else (if (LocalPinnedFirstWide.current) com.shelf.launcher.data.Features.capsuleRevealFirst
            else com.shelf.launcher.data.Features.capsuleReveal).state.value
        val flipping = revealStyle == com.shelf.launcher.data.Features.CapsuleReveal.FLIP
        val reveal = remember { androidx.compose.animation.core.Animatable(0f) }
        var wideLoaded by remember(wideLayer) { mutableStateOf(false) }
        LaunchedEffect(wideLayer, wideLoaded) {
            if (wideLayer == null) reveal.animateTo(0f, androidx.compose.animation.core.tween(110))
            else if (wideLoaded && revealStyle == com.shelf.launcher.data.Features.CapsuleReveal.NONE) reveal.snapTo(1f)
            else if (wideLoaded) reveal.animateTo(
                1f, androidx.compose.animation.core.tween(
                    when (revealStyle) {
                        com.shelf.launcher.data.Features.CapsuleReveal.FLIP -> 460
                        com.shelf.launcher.data.Features.CapsuleReveal.ROLL -> 300
                        com.shelf.launcher.data.Features.CapsuleReveal.SLIDE -> 280
                        else -> 320
                    },
                    // Quick start, long soft landing: smooth rather than snappy.
                    easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
                )
            )
            else reveal.snapTo(0f)
        }
        // Cartridge / disc art, drawn: the console's cartridge or a disc with the game's label on it.
        val pick = if (drawnMediaWanted(cardArtType, typedArtUrl)) mediaFor(game.platform, mixed, asDisc, gameKey = game.stableKey) else null
        // (Update 240, #32/#57: a round disc — drawn (its console's, the plain CD, an Android game as a disc) or the game's
        // own disc photo — not a cartridge, a UMD, a pack shell or a disc in its case. On these the trophy count and the
        // game state go on a chip strip under the disc (Features.discTileChips), so they're never drawn over its curve.)
        val roundDisc = freeMedia && !banner && !(look.spines && !focused && !pinnedWide) && (
            if (pick != null) pick.shell.isDisc && !isCustomPiece(pick.platform) && !discCaseOn(MediaTemplates.of(pick.platform))
            else realScanOf(cardArtType, typedArtUrl) != null && mediaRound(game.platform))
        val discChips = roundDisc && com.shelf.launcher.data.Features.discTileChips.state.value
        // The strip: room for the chip (it sits 2 dp in) under the disc. On a 2:3 tile the space under the disc (and its
        // 6 dp margin) already is that big, so nothing changes there; on a square tile the disc is made just
        // small enough, with the same room above, so it stays centred next to cartridges and covers.
        val discChipNeed = if (focused) 26.dp else 20.dp
        // (A wide tile — Wide capsules — has room for the chip beside the disc: nothing changes there either.)
        val discChipRoom = if (!discChips || width - height >= 80.dp) 0.dp
            else (discChipNeed - (height - width).coerceAtLeast(0.dp) / 2 - 6.dp).coerceAtLeast(0.dp)
        // Update 144: a case, cartridge or disc fills its place now — room is kept at its top for the trophy count when
        // that sits at the top (it used to be drawn over the case's banner). The same room on every such card, with
        // or without a count, so they all stay one size.
        val pillTopRoom = if (freeMedia && !banner && look.achievementsOnCards && titles.coverAchievements) {
            val place = com.shelf.launcher.data.Features.achPlace.state.value
            val atTop = place == com.shelf.launcher.data.Features.AchPlace.ABOVE ||
                (!discChips && place == com.shelf.launcher.data.Features.AchPlace.CORNER &&
                    (look.trophyCorner == com.shelf.launcher.data.CardCorner.TOP_LEFT || look.trophyCorner == com.shelf.launcher.data.CardCorner.TOP_RIGHT))
            if (atTop) (if (focused) 28.dp else 18.dp) else 0.dp
        } else 0.dp
        val mediaTopRoom = maxOf(pillTopRoom, discChipRoom)
        val box3d = if (cardArtType == CardArtType.CASE && !banner) caseMarker(game.platform) else null
        // Update 147: an app without art of its own wears its icon on its cartridge's label, disc or case front (the
        // icon used to be laid over the whole card, hiding the cartridge / case under it).
        val appIcon = if (artUrl == null && game.sourceType == SourceType.APP && (pick != null || box3d != null)) {
            rememberAppIcon(game.packageName)?.let { androidx.compose.runtime.remember(it) { it.asAndroidBitmap() } }
        } else null
        // Mastered / completed: the light sweep follows the object's own shape (cartridge, disc, case), not a square round it.
        val shineOn = titles.masteredShimmer && (award == RaAward.MASTERED || award == RaAward.COMPLETED)
        // Update 113, Spines look: the games you're not on as their spines (the one you're on keeps its cover).
        if (look.spines && !focused && !banner && !pinnedWide) {
            GameSpine(game, null, Modifier.matchParentSize().graphicsLayer { alpha = if (greyOut) 0.55f else 1f })
        } else if (pick != null) {
            Box(
                Modifier.matchParentSize()
                    .then(if (shineOn) Modifier.masteredShineOverShape(if (focused) 0.55f else 0.32f) else Modifier)
                    .graphicsLayer { alpha = if (greyOut || (!focused && look.otherGames != com.shelf.launcher.data.OtherGamesLook.NORMAL)) 0.55f else 1f }
                    // Update 202: Art position for the cartridges / discs (Edit Home › R3), each look its own.
                    .artPosition(if (pick.shell.isDisc) ArtPlace.Look.DISC else ArtPlace.Look.CART)
            ) {
                // (Update 205: its real label scan on the drawn piece when it has one — HomeScans holds every game's scans.)
                val labelScan = if (typedArtUrl == null) drawnLabelScan(HomeScans.map[game.stableKey], game.platform) else null
                MediaFocus(focused) { PhysicalMedia(
                    pick.shell, labelScan ?: baseUrl ?: artUrl ?: appIcon, Modifier.fillMaxSize().padding(top = if (roundDisc) mediaTopRoom else pillTopRoom, bottom = discChipRoom).padding(if (pick.shell.isDisc) 6.dp else 10.dp),
                    platform = pick.platform, labelIsScan = labelScan != null || (typedArtUrl != null && (baseUrl ?: artUrl) == typedArtUrl),
                    caseCover = coverUrl ?: appIcon, title = game.name
                ) }
            }
        } else if (box3d != null) {
            // Box art as the real 3D box (template pack), turning gently while it's the game you're on.
            // Box art as the real box from the template pack, straight from the front (never turned to its side).
            Box(
                Modifier.matchParentSize()
                    .then(if (shineOn) Modifier.masteredShineOverShape(if (focused) 0.55f else 0.32f) else Modifier)
                    .graphicsLayer { alpha = if (greyOut || (!focused && look.otherGames != com.shelf.launcher.data.OtherGamesLook.NORMAL)) 0.55f else 1f }
            ) {
                MediaFocus(focused) { CaseArt(
                    game.platform, coverUrl ?: appIcon, typedArtUrl, Modifier.fillMaxSize().padding(top = pillTopRoom).padding(4.dp), title = gameDisplayName(game.name), real = realCase,
                    // The coloured frame hugs the case itself (Settings › Cards & frames › Coloured frame).
                    caseModifier = if (focused && look.focusBorder) Modifier.border(2.dp, ShelfTheme.accent, RoundedCornerShape(2.dp)) else Modifier,
                    // (Update 240, #27/#58: the game and its scans, so a box whose template lies on its back (N64 / SNES) stands as its real 3D case when turned to its spine.)
                    spine = BoxTexture.realSpine(HomeScans.map[game.stableKey], game.platform), game = game, physical = HomeScans.map[game.stableKey]
                ) }
            }
        } else if (realScanOf(cardArtType, typedArtUrl) != null) {
            // Update 129: "Real discs": the game's own cartridge / disc scan, standing free (no dark card behind it).
            Box(
                Modifier.matchParentSize()
                    .then(if (shineOn) Modifier.masteredShineOverShape(if (focused) 0.55f else 0.32f) else Modifier)
                    .graphicsLayer { alpha = if (greyOut || (!focused && look.otherGames != com.shelf.launcher.data.OtherGamesLook.NORMAL)) 0.55f else 1f }
                    // Update 202: Art position for the cartridges / discs (the game's own scan too).
                    .artPosition(if (mediaRound(game.platform)) ArtPlace.Look.DISC else ArtPlace.Look.CART)
            ) {
                MediaFocus(focused) {
                    RealMediaPicture(typedArtUrl!!, mediaRound(game.platform), Modifier.fillMaxSize().padding(top = if (roundDisc) mediaTopRoom else pillTopRoom, bottom = discChipRoom).padding(if (mediaRound(game.platform)) 6.dp else 10.dp), platform = game.platform, gameKey = game.stableKey)
                }
            }
        } else (baseUrl ?: artUrl)?.let { url ->
            AsyncImage(
                model = rememberCardArtRequest(url, kindOf(url), still = !focused && com.shelf.launcher.data.Features.animatedFocusOnly.state.value && com.shelf.launcher.data.AnimatedArt.looksAnimated(url)),
                contentDescription = game.name,
                contentScale = ContentScale.Crop,
                colorFilter = if (greyOut) GreyFilter else null,
                modifier = Modifier.matchParentSize().pixelArtwork()
                    // Update 202: Art position for the covers (moved and sized in the card, which cuts it off), not the wide capsule.
                    .artPosition(if (!banner && cardArtType == CardArtType.COVER) ArtPlace.Look.COVER else null)
                    // Flip: the cover turns away (first half of the turn), the wide art comes round on the other side.
                    .then(if (flipping && wideLayer != null) Modifier.graphicsLayer {
                        val r = reveal.value
                        rotationY = (r * 180f).coerceAtMost(90f)
                        alpha = if (r >= 0.5f) 0f else 1f
                        cameraDistance = 14f * density
                        flatTextFor3d() // Update 177: drawn flat, then turned (Android 13 text cache)
                    } else if (revealStyle == com.shelf.launcher.data.Features.CapsuleReveal.SLIDE && wideLayer != null) Modifier.graphicsLayer {
                        // Slide: the cover moves out to the left as the wide art pushes in.
                        translationX = -size.width * reveal.value
                    } else Modifier)
            )
        }
        (wideLayer ?: artUrl?.takeIf { reveal.value > 0f && it != baseUrl })?.let { url ->
            AsyncImage(
                model = rememberCardArtRequest(url, kindOf(url), still = !focused && com.shelf.launcher.data.Features.animatedFocusOnly.state.value && com.shelf.launcher.data.AnimatedArt.looksAnimated(url)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { wideLoaded = true },
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val r = reveal.value
                        when (revealStyle) {
                            com.shelf.launcher.data.Features.CapsuleReveal.FLIP -> {
                                // The back of the card: comes round from edge-on to facing you.
                                alpha = if (r > 0.5f) 1f else 0f
                                rotationY = (r - 1f) * 180f
                                cameraDistance = 14f * density
                            }
                            com.shelf.launcher.data.Features.CapsuleReveal.SWEEP -> alpha = r.coerceIn(0f, 1f)
                            // Roll out: unrolls from the left with a soft edge, drifting a little into place.
                            com.shelf.launcher.data.Features.CapsuleReveal.ROLL -> {
                                alpha = if (r <= 0f) 0f else 1f
                                translationX = -size.width * 0.05f * (1f - r)
                                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                            }
                            // Slide: in from the right, pushing the cover out.
                            com.shelf.launcher.data.Features.CapsuleReveal.SLIDE -> {
                                alpha = if (r <= 0f) 0f else 1f
                                translationX = size.width * (1f - r)
                            }
                            com.shelf.launcher.data.Features.CapsuleReveal.WIPE ->
                                alpha = (0.35f + 0.65f * r).coerceIn(0f, 1f) * if (r <= 0f) 0f else 1f
                            // Off: just there once it has loaded.
                            com.shelf.launcher.data.Features.CapsuleReveal.NONE -> alpha = if (r <= 0f) 0f else 1f
                        }
                    }
                    .drawWithContent {
                        val r = reveal.value
                        when (revealStyle) {
                            // A soft-edged wipe: clipped at the reveal line.
                            com.shelf.launcher.data.Features.CapsuleReveal.WIPE ->
                                clipRect(right = size.width * r) { this@drawWithContent.drawContent() }
                            // A band of light runs across while the wide art fades in under it.
                            com.shelf.launcher.data.Features.CapsuleReveal.SWEEP -> {
                                drawContent()
                                if (r in 0.01f..0.99f) {
                                    val x = size.width * (r * 1.3f - 0.15f)
                                    val band = size.width * 0.18f
                                    drawRect(
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            listOf(Color.Transparent, Color.White.copy(alpha = 0.55f * (1f - r)), Color.Transparent),
                                            startX = x - band, endX = x + band
                                        )
                                    )
                                }
                            }
                            com.shelf.launcher.data.Features.CapsuleReveal.ROLL -> {
                                drawContent()
                                if (r < 1f) {
                                    // Keep what's left of the soft edge, fade out what's to its right.
                                    val feather = size.width * 0.22f
                                    val edge = r * (size.width + feather)
                                    drawRect(
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            listOf(Color.Black, Color.Transparent), startX = edge - feather, endX = edge
                                        ),
                                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                                    )
                                }
                            }
                            else -> drawContent()
                        }
                    }
            )
        }
        // Most Android apps aren't on SteamGridDB — use the app's own icon
        // (or its TV banner on the wide card) rather than an empty gradient.
        if (artUrl == null && game.sourceType == SourceType.APP && pick == null && box3d == null) {
            AppIconArt(game.packageName, wide = banner, modifier = Modifier.matchParentSize())
        }
        // The games you're not on, dimmed or greyed (Appearance › Game row).
        if (!focused && !freeMedia && look.otherGames != com.shelf.launcher.data.OtherGamesLook.NORMAL) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Color.Black.copy(
                            alpha = if (look.otherGames == com.shelf.launcher.data.OtherGamesLook.DIMMED) 0.45f else 0.2f
                        )
                    )
            )
        }

        // Update 115: the console's badge (Game look › Console badges), clear of the trophy's corner.
        // (Update 141: on every look — cases, cartridges, discs, 3D models and spines too; a slot too narrow for it,
        // like a slim spine, skips it on its own.)
        // Update 144: the corner it takes (null when Console badges is off), so the star, the Open tag and the game
        // state mark below keep out of it instead of being drawn over the badge.
        val badgeCorner: Alignment? = run {
            // (Update 129: also clear of the count when it sits above the name.)
            val coverPlace = com.shelf.launcher.data.Features.achPlace.state.value
            // (Update 240, #32/#57: on a disc with the chip strip the count is at the bottom right, so the badge keeps the top
            // left — over the free space above the disc — unless the count sits above.)
            val badgeLow = if (discChips) coverPlace == com.shelf.launcher.data.Features.AchPlace.ABOVE
            else if (!banner && coverPlace != com.shelf.launcher.data.Features.AchPlace.CORNER) coverPlace == com.shelf.launcher.data.Features.AchPlace.ABOVE
            else look.trophyCorner == com.shelf.launcher.data.CardCorner.TOP_LEFT
            val corner = if (badgeLow) Alignment.BottomStart else Alignment.TopStart
            // (Update 202: not over a case — its printed band shows the console already; the corner stays free then.)
            val onCase = pick == null && box3d != null && !(look.spines && !focused && !banner && !pinnedWide)
            ConsoleBadge(game.platform, corner, game.packageName, onCase = onCase)
            if (com.shelf.launcher.data.Features.consoleBadges.state.value && !(onCase && caseShowsConsole(game.platform))) corner else null
        }
        val badgeTop = badgeCorner == Alignment.TopStart
        // Just under a top-left badge (its pill is about 1.6 × its height down, as ConsoleBadge sizes it).
        val belowBadge = (width * 0.13f).coerceIn(11.dp, 20.dp) * 1.6f + 2.dp

        // Mastered / completed: a soft light glides across the game you're on (Achievements › Shimmer).
        // With the golden-frame style the frame itself carries the sweep instead.
        // Every mastered / completed card shimmers (stronger on the one you're on).
        if (!freeMedia && titles.masteredShimmer && (award == RaAward.MASTERED || award == RaAward.COMPLETED)) {
            MasteredShine(Modifier.matchParentSize(), if (focused) 0.55f else 0.32f)
        }

        // Achievement progress badge, visible in both focused and unfocused
        // states — same corner-badge idea most library UIs use for this.
        // A recent unlock can take the cover's corner instead (Appearance).
        // NEW only for unlocks detected since the last check and not looked at yet (AchievementSeen).
        val seenAll by com.shelf.launcher.data.AchievementSeen.all.collectAsState()
        val glowing = game.raGameId?.let { seenAll[com.shelf.launcher.data.AchievementSeen.keyRa(it)]?.glow?.isNotEmpty() } == true
        val showNewUnlock = !focused && look.achievementsOnCards && titles.coverRecentUnlocks && recentUnlockTitle != null && glowing
        if (game.stableKey in com.shelf.launcher.data.RunningGames.keys) {
            RunningTag(Modifier.align(Alignment.TopStart).then(if (badgeTop) Modifier.padding(start = 6.dp, top = belowBadge) else Modifier.padding(6.dp)))
        }
        if (showNewUnlock) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(ShelfTheme.accent)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.EmojiEvents, null, tint = onAccentColor(), modifier = Modifier.size(9.dp))
                Spacer(Modifier.width(2.dp))
                Text("NEW", color = onAccentColor(), fontSize = 8.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(8.sp), maxFontSize = 8.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false)
            }
        }
        // On the wide capsule the count is already in its progress line; a second pill in the corner
        // only doubled it (and sat under the ☆ / ••• buttons).
        val bannerShowsProgress = banner && look.progressOnBanner
        // Update 129: where it sits on a cover (R1 Achievements tile / Appearance › Achievements › Trophy count).
        val achPlace = com.shelf.launcher.data.Features.achPlace.state.value
        val trophyShown = look.achievementsOnCards &&
            (if (banner) look.trophyOnBanner && !bannerShowsProgress
            else titles.coverAchievements && !showNewUnlock && achPlace != com.shelf.launcher.data.Features.AchPlace.OFF)
        val trophyAlign = if (!banner && achPlace == com.shelf.launcher.data.Features.AchPlace.ABOVE) Alignment.TopCenter
        // (Update 240, #32/#57: a disc's count on the strip under it, at the tile's bottom right — never over its curve.)
        else if (discChips) Alignment.BottomEnd
        else if (!banner && achPlace == com.shelf.launcher.data.Features.AchPlace.NAME) Alignment.BottomStart
        else if (!banner && achPlace == com.shelf.launcher.data.Features.AchPlace.UNDER) Alignment.BottomCenter
        else when (look.trophyCorner) {
            com.shelf.launcher.data.CardCorner.TOP_RIGHT -> Alignment.TopEnd
            com.shelf.launcher.data.CardCorner.TOP_LEFT -> Alignment.TopStart
            // The capsule's bottom row holds Play and the round buttons: the pill goes to the top instead.
            com.shelf.launcher.data.CardCorner.BOTTOM_LEFT -> if (banner && look.bannerButtons) Alignment.TopStart else Alignment.BottomStart
            com.shelf.launcher.data.CardCorner.BOTTOM_RIGHT -> if (banner && look.bannerButtons) Alignment.TopEnd else Alignment.BottomEnd
        }
        // Corners are shared by the trophy pill, the favourite star, the NEW pill and the award
        // (mastered / beaten). Rather than stacking two badges in one corner, the award joins the
        // trophy pill when that's on the card ("👑 100%"), and the star moves out of the pill's way.
        val pillPossible = raProgress?.NumPossibleAchievements?.takeIf { trophyShown && it > 0 }
        val cornerAward = award?.takeIf { !banner && awardFrame == null && masteredStyle != com.shelf.launcher.data.MasteredStyle.NONE }
        val awardInPill = cornerAward != null && pillPossible != null
        raProgress?.takeIf { trophyShown }?.let { progress ->
            val possible = progress.NumPossibleAchievements
            if (possible != null && possible > 0) {
                // Covers are only 72dp wide: there the pill is compact and can
                // never wrap onto a second line ("0/15" used to split in two).
                Box(
                    Modifier
                        .align(trophyAlign)
                        // (Update 240, #32/#57: on a disc's strip, 2 dp in, and at most half the tile when the game's state
                        // shares the strip.)
                        .padding(if (discChips && trophyAlign == Alignment.BottomEnd) 2.dp else if (focused) 8.dp else 4.dp)
                        // (Update 211: never wider than the card — the text shrinks / ends with … instead of being cut off.)
                        .widthIn(max = (if (discChips && GameStates.byKey[game.stableKey] != null) width / 2 - 4.dp else width - 12.dp).coerceAtLeast(24.dp))
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = if (focused) 8.dp else 5.dp, vertical = if (focused) 4.dp else 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (awardInPill && cornerAward != null) {
                            Icon(
                                awardIcon(cornerAward), cornerAward.label,
                                tint = awardTint(cornerAward), modifier = Modifier.size(if (focused) 12.dp else 9.dp)
                            )
                        } else {
                            Icon(
                                Icons.Filled.EmojiEvents, null,
                                tint = ShelfTheme.accent, modifier = Modifier.size(if (focused) 12.dp else 9.dp)
                            )
                        }
                        // Update 151: a small upright tick between the trophy and "0/69", so they read as two parts.
                        ProgressTick(height = if (focused) 10.dp else 7.dp, gap = if (focused) 4.dp else 2.dp)
                        val earned = progress.NumAchieved ?: 0
                        Text(
                            // Always the count, the same on every card ("0/53", "12/120"); with room
                            // (the game you're on) the percentage joins it: "12/120 · 10%".
                            // (Update 211: the percentage only where the card has room for it — on the RP Classic's
                            // small Continue playing tiles "38/75 · 51%" ran off the card's edge.)
                            // (0.6.17: on the smallest tiles — Twin icons, Twin desk's list, Pocket Quest's strip — "107/155"
                            // was cut to "107/1": there the percentage instead, which always fits.)
                            if (focused && width >= 132.dp) com.shelf.launcher.data.achievementProgress(earned, possible)
                            else if ("$earned/$possible".length * 4.6f > (width - if (focused) 52.dp else 40.dp).value)
                                "${(earned * 100 / possible).coerceIn(0, 100)}%"
                            else "$earned/$possible",
                            color = Color.White,
                            fontSize = if (focused) 11.sp else 9.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(if (focused) 11.sp else 9.sp), maxFontSize = if (focused) 11.sp else 9.sp),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                }
                // "Under the cover": the game you're on also gets the bar along its bottom edge.
                if (!banner && focused && achPlace == com.shelf.launcher.data.Features.AchPlace.UNDER) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)
                            .background(Color.Black.copy(alpha = 0.55f))
                    ) {
                        Box(
                            Modifier.fillMaxWidth(((progress.NumAchieved ?: 0).toFloat() / possible).coerceIn(0f, 1f))
                                .fillMaxHeight().background(ShelfTheme.accent)
                        )
                    }
                }
            }
        }

        val pillAlign = trophyAlign.takeIf { pillPossible != null }
        // Top-left normally; top-right when the trophy pill or the console badge sits top-left (the NEW pill
        // never shows together with a top-left trophy pill on a cover). Update 144: when the top-right is taken
        // too (NEW pill, trophy pill), it stays top-left just under the badge.
        // (Update 144: the award also takes the top-right on a card with no art, as placed below.)
        val awardTopRight = cornerAward != null && !awardInPill && !focused && artUrl == null && game.sourceType != SourceType.APP
        val starAlign = if ((pillAlign == Alignment.TopStart || badgeTop) && !showNewUnlock && pillAlign != Alignment.TopEnd && !awardTopRight) Alignment.TopEnd
        else Alignment.TopStart
        if (isFavorite && look.favoriteStar) {
            Icon(
                Icons.Filled.Star, contentDescription = "Favorite", tint = ShelfTheme.accent,
                modifier = Modifier.align(starAlign)
                    .then(if (starAlign == Alignment.TopStart && badgeTop) Modifier.padding(start = 6.dp, top = belowBadge) else Modifier.padding(6.dp))
                    .size(12.dp)
            )
        }
        // On the focused card the award sits in the text column instead (see
        // below), so it can't land on top of the achievement line. With the
        // trophy pill on the card it's inside the pill (above), never a second badge.
        if (cornerAward != null && !awardInPill) {
            // Bottom-right, unless the no-art name strip covers the bottom edge.
            val nameStrip = !focused && artUrl == null && game.sourceType != SourceType.APP
            val awardAlign = when {
                !nameStrip -> Alignment.BottomEnd
                showNewUnlock -> if (isFavorite && look.favoriteStar) Alignment.BottomEnd else Alignment.TopStart
                else -> Alignment.TopEnd
            }
            AwardBadge(
                cornerAward,
                expanded = false,
                modifier = Modifier.align(awardAlign).padding(5.dp)
            )
        }
        // Game state mark (Settings › Library › Game states): bottom-left, or top-left when that corner
        // is taken (trophy pill, the no-art name strip, the capsule's buttons); left out when both are.
        GameStates.byKey[game.stableKey]?.let { gs ->
            val nameStrip = !focused && artUrl == null && game.sourceType != SourceType.APP
            val starTopLeft = isFavorite && look.favoriteStar && starAlign == Alignment.TopStart
            // (Update 144: the console badge's corner counts as taken too.)
            val bottomBusy = pillAlign == Alignment.BottomStart || pillAlign == Alignment.BottomCenter || nameStrip || (banner && look.bannerButtons) ||
                badgeCorner == Alignment.BottomStart
            val topBusy = pillAlign == Alignment.TopStart || pillAlign == Alignment.TopCenter || starTopLeft || badgeTop ||
                game.stableKey in com.shelf.launcher.data.RunningGames.keys
            val stateAlign = if (discChips) {
                // (Update 240, #32/#57: on a disc, on the strip under it — the bottom right, or the bottom left beside the
                // trophy count / award there.)
                val endTaken = pillAlign == Alignment.BottomEnd || (cornerAward != null && !awardInPill)
                when {
                    !endTaken -> Alignment.BottomEnd
                    badgeCorner != Alignment.BottomStart -> Alignment.BottomStart
                    !topBusy -> Alignment.TopStart
                    else -> null
                }
            } else when {
                !bottomBusy -> Alignment.BottomStart
                !topBusy -> Alignment.TopStart
                else -> null
            }
            val onStrip = discChips && (stateAlign == Alignment.BottomEnd || stateAlign == Alignment.BottomStart)
            if (stateAlign != null) GameStateTag(
                gs, Modifier.align(stateAlign).padding(if (onStrip) 2.dp else if (focused) 8.dp else 4.dp)
                    .then(if (onStrip) Modifier.widthIn(max = (width / 2 - 4.dp).coerceAtLeast(24.dp)) else Modifier),
                compact = !focused
            )
        }

        // Other games show no name — except when there's no art at all yet,
        // so a blank card still says which game it is.
        androidx.compose.animation.AnimatedVisibility(
            visible = !focused && artUrl == null && game.sourceType != SourceType.APP,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            // The game's own name, not the console — the console is already
            // implied by the category you're browsing, and the title is what
            // you actually need when there's no cover art yet.
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                        )
                    )
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(10.sp), maxFontSize = 10.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    lineHeight = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Em),
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        // Full banner content shown only when focused as a wide capsule.
        androidx.compose.animation.AnimatedVisibility(
            visible = banner,
            enter = fadeIn(tween(130, delayMillis = 40)),
            exit = fadeOut(tween(90)),
            modifier = Modifier.matchParentSize()
        ) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                // How much height the text column has above the Play row: on a short capsule (small screen,
                // small cards) the extra lines go, so the name never runs into the buttons or the corners.
                val room = maxHeight - (if (look.bannerButtons) 50.dp else 0.dp)
                val tight = room < 84.dp
                val veryTight = room < 60.dp
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, ShelfTheme.background.copy(alpha = 0.75f))
                            )
                        )
                )
                // The default progress bar ("under") sits in the button row,
                // next to Play, so the two can never overlap.
                val progressInButtons = look.bannerButtons && look.achievementsOnCards && !badgeNextToTitle &&
                    look.progressOnBanner && !look.progressAbove
                Column(
                    Modifier
                        .align(Alignment.CenterStart)
                        // Centred in the space above the Play button row.
                        .padding(start = 20.dp, end = 20.dp, bottom = if (look.bannerButtons) 50.dp else 0.dp)
                ) {
                    // Progress bar above everything else on the capsule (Achievements setting).
                    if (look.achievementsOnCards && look.progressOnBanner && look.progressAbove) {
                        AchievementBadge(
                            progress = raProgress,
                            mode = badgeMode,
                            recentUnlockTitle = recentUnlockTitle,
                            onCycleMode = onCycleBadgeMode,
                            onOpen = onViewAchievements
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    if (award != null && masteredStyle == com.shelf.launcher.data.MasteredStyle.BADGE) {
                        AwardBadge(award, expanded = !tight)
                        Spacer(Modifier.height(6.dp))
                    }
                    val iconAward = award?.takeIf { masteredStyle == com.shelf.launcher.data.MasteredStyle.ICON }
                    if (iconAward != null && !nameInside) {
                        AwardBadge(iconAward, expanded = false)
                        Spacer(Modifier.height(4.dp))
                    }
                    if (look.achievementsOnCards && badgeNextToTitle && look.progressOnBanner && !look.progressAbove) {
                        AchievementBadge(
                            progress = raProgress,
                            mode = badgeMode,
                            recentUnlockTitle = recentUnlockTitle,
                            onCycleMode = onCycleBadgeMode,
                            onOpen = onViewAchievements
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    // Always the title. This previously showed a logo image
                    // instead, which renders as empty space whenever the logo
                    // fails to load — leaving only the console name visible.
                    if (nameInside) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val instead = titles.logoPlacement == com.shelf.launcher.data.LogoPlacement.INSTEAD
                            LogoAndName(
                                logoUrl = if (titles.showLogos) artOverride?.logo ?: art?.logoUrl else null,
                                placement = titles.logoPlacement,
                                // Logo on its own: noticeably bigger (smaller on a short capsule).
                                logoHeight = if (instead) (if (tight) 40.dp else 52.dp) else (if (tight) 24.dp else 34.dp),
                                logoMaxWidth = if (instead) 220.dp else 120.dp,
                                modifier = Modifier.weight(1f, fill = false)
                            ) { m ->
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = if (veryTight) 16.sp else 18.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(if (veryTight) 16.sp else 18.sp), maxFontSize = if (veryTight) 16.sp else 18.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = if (tight) 1 else 2,
                                    lineHeight = androidx.compose.ui.unit.TextUnit(1.15f, androidx.compose.ui.unit.TextUnitType.Em),
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = m
                                )
                            }
                            if (iconAward != null) {
                                Spacer(Modifier.width(6.dp))
                                AwardBadge(iconAward, expanded = false)
                            }
                        }
                    }
                    // Off by default (Appearance › Game names): the category
                    // you're browsing already says which console it is.
                    if (titles.showConsoleName && !veryTight) {
                        Text(
                            text = game.platform,
                            color = ShelfTheme.secondary,
                            fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    // The play timer isn't shown on the card — it sits under the
                    // row instead (Appearance › Home screen).
                    lastPlayedAt?.takeIf { look.lastPlayedOnBanner && !tight }?.let {
                        Text(
                            text = "Played ${relativeTimeLabel(it)}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp),
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (!progressInButtons && look.achievementsOnCards && !badgeNextToTitle && look.progressOnBanner && !look.progressAbove) {
                        AchievementBadge(
                            progress = raProgress,
                            mode = badgeMode,
                            recentUnlockTitle = recentUnlockTitle,
                            onCycleMode = onCycleBadgeMode,
                            onOpen = onViewAchievements
                        )
                    }
                }
                if (look.bannerButtons) Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(50))
                            .background(ShelfTheme.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = onAccentColor())
                    }
                    if (progressInButtons) {
                        Box(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                            AchievementBadge(
                                progress = raProgress,
                                mode = badgeMode,
                                recentUnlockTitle = recentUnlockTitle,
                                onCycleMode = onCycleBadgeMode,
                                onOpen = onViewAchievements
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onToggleFavorite != null) {
                            MiniIcon(
                                if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                onClick = onToggleFavorite,
                                tint = if (isFavorite) ShelfTheme.accent else Color.White
                            )
                        }
                        // Everything else — artwork, details, emulator,
                        // achievements — lives behind this one control now.
                        if (onOpenOptions != null) {
                            MiniIcon(Icons.Filled.MoreHoriz, onClick = onOpenOptions)
                        }
                    }
                }
            }
        }
    }
    }
}

/**
 * A game's transparent logo, sized to sit next to its name. Takes no space
 * at all when there's no logo or it fails to load — so the name is never
 * pushed around by an empty box.
 */
@Composable
internal fun GameLogo(url: String?, height: androidx.compose.ui.unit.Dp, maxWidth: androidx.compose.ui.unit.Dp, endGap: androidx.compose.ui.unit.Dp = 0.dp) {
    if (url == null) return
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
        onError = { failed = true },
        modifier = Modifier
            .height(height)
            .widthIn(max = maxWidth)
            .padding(end = endGap)
    )
}

/**
 * The game's name with its logo left, right, above, under or instead of it
 * (Appearance › Game names). No logo (or one that fails to load): just the name.
 * [name] gets the modifier to put on the text.
 */
@Composable
internal fun LogoAndName(
    logoUrl: String?,
    placement: com.shelf.launcher.data.LogoPlacement,
    logoHeight: androidx.compose.ui.unit.Dp,
    logoMaxWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    name: @Composable (Modifier) -> Unit
) {
    var failed by remember(logoUrl) { mutableStateOf(false) }
    if (logoUrl == null || failed) {
        name(modifier)
        return
    }
    val logo: @Composable () -> Unit = {
        AsyncImage(
            model = logoUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alignment = if (centered) Alignment.Center else Alignment.CenterStart,
            onError = { failed = true },
            modifier = Modifier.height(logoHeight).widthIn(max = logoMaxWidth)
        )
    }
    when (placement) {
        com.shelf.launcher.data.LogoPlacement.LEFT -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            logo(); Spacer(Modifier.width(8.dp)); name(Modifier.weight(1f, fill = false))
        }
        com.shelf.launcher.data.LogoPlacement.RIGHT -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            name(Modifier.weight(1f, fill = false)); Spacer(Modifier.width(8.dp)); logo()
        }
        com.shelf.launcher.data.LogoPlacement.ABOVE -> Column(
            modifier, horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
        ) { logo(); Spacer(Modifier.height(4.dp)); name(Modifier) }
        com.shelf.launcher.data.LogoPlacement.BELOW -> Column(
            modifier, horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
        ) { name(Modifier); Spacer(Modifier.height(4.dp)); logo() }
        com.shelf.launcher.data.LogoPlacement.INSTEAD -> Box(modifier) { logo() }
    }
}

/** Black & white, for the games you're not on. */
private val GreyFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
    androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
)

/** How long a cover takes to turn into the wide capsule (and back). */
internal const val CardAnimMillis = 150

/**
 * How the row moves: a critically damped spring, the same for every card's
 * position and size so they stay in lockstep (a linear system: equal springs
 * started together keep the same relations as their targets). Unlike a tween,
 * a spring keeps its speed when the target changes mid-move, so holding the
 * D-pad glides instead of stuttering, and a single step settles in ~120 ms.
 */
internal const val DockStiffness = 1100f
internal fun <T> dockMotion(): androidx.compose.animation.core.SpringSpec<T> =
    // (Update 141: as quick as Settings › Home › Animations › Moving between games says; Quick is the old one.)
    androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = com.shelf.launcher.data.Features.rowGlide.value.stiffness)

@Composable
private fun MiniIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (() -> Unit)? = null,
    tint: Color = Color.White
) {
    com.shelf.launcher.Breadcrumbs.mark("MiniIcon")
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
            .then(
                if (onClick != null) {
                    // Keyed on Unit + latest lambda: keying on onClick restarted the detector on every
                    // recomposition (a new lambda each time), swallowing taps — the "press twice" bug.
                    val latest = androidx.compose.runtime.rememberUpdatedState(onClick)
                    Modifier.pointerInput(Unit) { detectTapGestures { latest.value() } }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
    }
}

/**
 * Achievement readout on a focused card. Tapping swaps between a completion
 * bar and the most recent unlock — they occupy the same spot because a
 * handheld card has no room for both.
 */
@Composable
private fun AchievementBadge(
    progress: RaGameProgress?,
    mode: com.shelf.launcher.AchievementBadgeMode,
    recentUnlockTitle: String?,
    onCycleMode: (() -> Unit)?,
    onOpen: (() -> Unit)?
) {
    com.shelf.launcher.Breadcrumbs.mark("AchievementBadge")
    val possible = progress?.NumPossibleAchievements ?: 0
    if (progress != null && possible > 0) {
    val earned = progress.NumAchieved ?: 0
    val fraction = (earned.toFloat() / possible).coerceIn(0f, 1f)
    val percent = (fraction * 100).toInt()
    val latestCycle = androidx.compose.runtime.rememberUpdatedState(onCycleMode)
    val latestOpen = androidx.compose.runtime.rememberUpdatedState(onOpen)

    Column(
        Modifier
            .padding(top = 4.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { latestCycle.value?.invoke() },
                    // Long-press jumps straight to the full list — tap is
                    // already taken by swapping progress / recent unlock.
                    onLongPress = { latestOpen.value?.invoke() }
                )
            }
    ) {
        // Nothing unlocked lately: show the progress bar rather than an empty line.
        val shown = if (mode == com.shelf.launcher.AchievementBadgeMode.RECENT_UNLOCK && recentUnlockTitle == null)
            com.shelf.launcher.AchievementBadgeMode.PROGRESS else mode
        when (shown) {
            com.shelf.launcher.AchievementBadgeMode.RECENT_UNLOCK -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.EmojiEvents, null,
                        tint = ShelfTheme.accent, modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        recentUnlockTitle ?: "No recent unlocks",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            com.shelf.launcher.AchievementBadgeMode.PROGRESS -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.EmojiEvents, null,
                        tint = ShelfTheme.accent, modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        com.shelf.launcher.data.achievementProgress(earned, possible) +
                            (com.shelf.launcher.data.remainingNote(earned, possible)?.let { "  ·  $it" } ?: ""),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .width(120.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(ShelfTheme.accent)
                    )
                }
            }
        }
    }
    }
}

/** Gradient placeholder used until SteamGridDB art has loaded (or if it's off). */
internal fun placeholderBrush(hue: Int): Brush {
    val base = Color.hsl(hue.toFloat(), 0.55f, 0.22f)
    val edge = Color.hsl(hue.toFloat(), 0.45f, 0.09f)
    return Brush.radialGradient(colors = listOf(base, edge))
}

private fun relativeTimeLabel(timestampMillis: Long): String {
    val diffMinutes = (System.currentTimeMillis() - timestampMillis) / 60000
    return when {
        diffMinutes < 1 -> "just now"
        diffMinutes < 60 -> "${diffMinutes}m ago"
        diffMinutes < 60 * 24 -> "${diffMinutes / 60}h ago"
        diffMinutes < 60 * 24 * 30 -> "${diffMinutes / (60 * 24)}d ago"
        else -> "${diffMinutes / (60 * 24 * 30)}mo ago"
    }
}

/**
 * The mastered frame's paint: the award colour with darker edges, and — when
 * [shimmer] is on — a white highlight gliding diagonally across every ~2.4 s.
 */
@Composable
private fun awardBrush(tint: Color, shimmer: Boolean, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp): Brush {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val w = with(density) { width.toPx() }
    val h = with(density) { height.toPx() }
    val dark = androidx.compose.ui.graphics.lerp(tint, Color.Black, 0.45f)
    if (!shimmer) return Brush.linearGradient(listOf(dark, tint, dark), start = Offset.Zero, end = Offset(w, h))
    // The shared slow clock (EffectClock): ~24 updates a second, still while idle.
    val sweep = -1f + 3f * ((EffectClock.shimmer.floatValue / 2.4f) % 1f)
    val span = (w + h) * 0.35f
    val x = sweep * (w + h)
    return Brush.linearGradient(
        listOf(dark, tint, Color.White, tint, dark),
        start = Offset(x - span, x - span - w),
        end = Offset(x + span, x + span - w)
    )
}

private fun awardTint(award: RaAward): Color = when (award) {
    RaAward.MASTERED -> Color(0xFFF5C542)
    RaAward.COMPLETED -> Color(0xFFD5DAE1)
    RaAward.BEATEN_HARDCORE -> Color(0xFF7FD3F5)
    RaAward.BEATEN -> Color(0xFFB0BEC5)
}

private fun awardIcon(award: RaAward) = when (award) {
    RaAward.MASTERED, RaAward.COMPLETED -> Icons.Filled.WorkspacePremium
    RaAward.BEATEN_HARDCORE, RaAward.BEATEN -> Icons.Filled.Flag
}

/** Gold crown = mastered, silver crown = completed (softcore), flag = beaten. */
@Composable
private fun AwardBadge(award: RaAward, expanded: Boolean, modifier: Modifier = Modifier) {
    com.shelf.launcher.Breadcrumbs.mark("AwardBadge")
    val icon = awardIcon(award)
    val tint = awardTint(award)
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = if (expanded) 8.dp else 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, award.label, tint = tint, modifier = Modifier.size(if (expanded) 13.dp else 11.dp))
        if (expanded) {
            Spacer(Modifier.width(4.dp))
            Text(award.label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * The address a card shows for the chosen art type. Requested type → cover →
 * wide → hero, so a game that lacks that art still gets a picture. The
 * default (cover) and the focused banner keep their original behaviour;
 * only Capsule / Hero re-order the banner.
 */
internal fun cardArtUrl(
    type: CardArtType,
    banner: Boolean,
    cover: String?, wide: String?, hero: String?, typed: String?
): String? {
    if (type == CardArtType.COVER) return if (banner) (wide ?: hero ?: cover) else (cover ?: wide)
    if (banner && type != CardArtType.HERO && type != CardArtType.CAPSULE) return wide ?: hero ?: cover
    val requested = when (type) {
        CardArtType.CAPSULE -> wide
        CardArtType.HERO -> hero
        else -> typed
    }
    // (Update 229, #31/#24: with no cover yet, a case / disc look no longer prints the wide capsule or hero on the disc label or crops it into the case.)
    if (!banner && type.wantsMedia()) return requested ?: cover
    return requested ?: if (banner) (wide ?: hero ?: cover) else (cover ?: wide ?: hero)
}

/** Decode-size slot for box / disc / screenshot art. */
internal fun cardArtKind(type: CardArtType): ArtKind =
    if (type == CardArtType.SCREENSHOT) ArtKind.WIDE else ArtKind.COVER

/** Box / disc / screenshot address for [type] from what is already loaded — never triggers loading. */
internal fun typedArtFor(
    type: CardArtType,
    physical: List<Pair<String, String>>?,
    screenshots: List<String>?,
    /** The game's console: with 3D boxes on and a template for it, only the flat front scan counts (the 3D box is drawn). */
    platform: String? = null,
    /** Update 205: the game's key, for a shell picked for this game (the game's menu › Cartridge shell…). */
    gameKey: String? = null
): String? = when (type) {
    // The case's own scanned front (or the front cut from the full box scan, BoxTexture.kt).
    CardArtType.CASE ->
        // (Update 235, #24: a cover you picked for this game (UserCovers: SteamGridDB, an alternative, your own picture) is
        // its case front in every case look — Cases, Cases · real sizes — so its box scan isn't handed on: the case template
        // gets the cover. Games without a picked cover keep their scan as before.)
        if (UserCovers.of(gameKey) != null) null
        else if (MediaTemplates.of(platform)?.box != null) BoxTexture.front(physical, platform)
        else BoxTexture.caseScan(physical, platform)
    // Drawn cartridge / disc: its label (else the cover, as the card's fallback); otherwise the scraped picture.
    CardArtType.CARTRIDGE_DISC ->
        // (Update 125, Cartridges only: a disc game's own disc picture doesn't fit on a cartridge; its cover is the label.)
        if (platform != null && cartsOnlyFor(platform)) null
        // Update 205: a shell picked for this game or its console (Cartridge shell…) is what it shows — everywhere, the
        // game window too; its own photo showed on the home screen instead, and the picked shell only when opened.
        else if (platform != null && CartShellStore.choice(platform, gameKey) != null) null
        // Update 204: Android, Steam, PC and arcade games have no cartridge or disc of their own: a "disc / cartridge"
        // picture found for one is another release's (WWE All Stars on Android showed its console box), so they're
        // always drawn on Voltcap's own cartridge / CD.
        else if (platform != null && noOwnMedia(platform)) null
        // Update 159, Discs only: a cartridge game's own scan / label is its cartridge — on its CD its cover is printed.
        else if (platform != null && DiscLook.on && mediaKind(platform) != "disc") null
        else if (com.shelf.launcher.data.Features.mediaShells.value) physical?.firstOrNull { it.first == "Disc label" }?.second
        else physical?.firstOrNull { it.first == "Disc / cartridge" }?.second
    CardArtType.SCREENSHOT -> screenshots?.firstOrNull()
    else -> null
}

/**
 * Update 205: the game's printed label scan (ScreenScraper's "Disc label") for its drawn cartridge / disc, when the
 * drawn one shows (no photo of its own, or the drawn pieces picked): the real sticker, not its cover under a console
 * band — a drawn N64 or Game Boy cartridge with the cover on it looked like a custom one. Null where its label can't be
 * its own (Cartridges only, Discs only on a cartridge game, games without media of their own).
 */
internal fun drawnLabelScan(physical: List<Pair<String, String>>?, platform: String?): String? {
    if (platform == null || cartsOnlyFor(platform) || noOwnMedia(platform)) return null
    if (DiscLook.on && mediaKind(platform) != "disc") return null
    return physical?.firstOrNull { it.first == "Disc label" }?.second
}

/** Update 204: games of [platform] have no cartridge or disc of their own (apps, Steam, PC, arcade…). */
internal fun noOwnMedia(platform: String): Boolean =
    platform == "App" || platform == "Android" || platform == "Steam" || MediaShell.of(platform) == null

/** A diagonal band of light sweeping over the card every ~2.6 s (one gradient layer, cheap). */
@Composable
internal fun MasteredShine(modifier: Modifier, strength: Float = 0.4f) {
    val style = LocalTitleOptions.current.shimmerStyle
    val accent = ShelfTheme.accent
    // One shared slow clock for every shining card (EffectClock) instead of an animation per card
    // redrawing at the display's full rate; read while drawing only, and still while idle.
    val clock = EffectClock.shimmer
    Box(modifier.drawBehind { drawMasteredShine(style, accent, clock.floatValue, strength, BlendMode.SrcOver) })
}

/**
 * The same light sweep, but only over what the content actually draws: a cartridge's outline, a disc's ring, a
 * case - not the empty square round it. The content is drawn into its own layer and the band painted with
 * "source atop", so it lands only where the picture is opaque. Put it on a box around the media.
 */
/**
 * The mastered / completed games by key (set by the home screen), so every layout that draws a cartridge, disc or
 * case can give it the shimmer without being handed the awards (Carousel, Grid, Hero, TV room, the style homes).
 */
internal object AwardShine {
    var keys by androidx.compose.runtime.mutableStateOf<Set<String>>(emptySet())
}

/** The mastered shimmer over the drawn object's own shape when game [key] is mastered / completed (and the switch is on). */
@Composable
internal fun Modifier.awardShine(key: String, focused: Boolean): Modifier =
    if (LocalTitleOptions.current.masteredShimmer && key in AwardShine.keys) this.masteredShineOverShape(if (focused) 0.55f else 0.32f) else this

@Composable
internal fun Modifier.masteredShineOverShape(strength: Float = 0.4f): Modifier {
    val style = LocalTitleOptions.current.shimmerStyle
    val accent = ShelfTheme.accent
    val clock = EffectClock.shimmer
    return this
        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawMasteredShine(style, accent, clock.floatValue, strength, BlendMode.SrcAtop)
        }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMasteredShine(
    style: com.shelf.launcher.data.ShimmerStyle, accent: Color, seconds: Float, strength: Float, blend: BlendMode
) {
    // 0 = band at the top-left corner, 1 = bottom-right; a little either side so it slides in and out.
    val t = -0.25f + 1.5f * ((seconds / 2.2f) % 1f)
    val w = size.width
    val h = size.height
    // The band's centre is the diagonal line x + y = d; it crosses the card for most of the cycle
    // (it used to be on the card for under a quarter of it, so it was easy to miss).
    val d = t * (w + h)
    val band = (w + h) * 0.16f
    val a = strength
    val colors = when (style) {
        // Gold: an amber edge, a bright gold body and a near-white glint in the middle,
        // plus a faint gold wash that flares up as the band passes.
        com.shelf.launcher.data.ShimmerStyle.GOLD -> listOf(
            Color.Transparent, Color(0xFFB8860B).copy(alpha = a * 0.5f), Color(0xFFFFD24A).copy(alpha = a),
            Color(0xFFFFF4C2).copy(alpha = a), Color(0xFFFFD24A).copy(alpha = a), Color(0xFFB8860B).copy(alpha = a * 0.5f),
            Color.Transparent
        )
        com.shelf.launcher.data.ShimmerStyle.THEME -> listOf(
            Color.Transparent, accent.copy(alpha = a), Color.White.copy(alpha = a * 0.8f), accent.copy(alpha = a), Color.Transparent
        )
        com.shelf.launcher.data.ShimmerStyle.WHITE -> listOf(Color.Transparent, Color.White.copy(alpha = a), Color.Transparent)
    }
    if (style == com.shelf.launcher.data.ShimmerStyle.GOLD) {
        // How close the band is to the middle of the card: 1 there, 0 off the card.
        val mid = 1f - (kotlin.math.abs(t - 0.5f) * 2f).coerceIn(0f, 1f)
        drawRect(Color(0xFFFFC83D).copy(alpha = 0.10f * mid * (a / 0.55f)), blendMode = blend)
    }
    drawRect(
        Brush.linearGradient(
            colors,
            start = Offset((d - band) / 2f, (d - band) / 2f),
            end = Offset((d + band) / 2f, (d + band) / 2f)
        ),
        blendMode = blend
    )
}
