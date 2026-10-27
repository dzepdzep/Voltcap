package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.CardArtType
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Disc station (update 147): one disc player. The game you're on is the disc in the tray of a big panel on the left —
 * its own disc (the disc consoles' printed discs, your disc pack), a plain CD with its label for cartridge games and
 * apps. Moving to another game ejects the disc (it drops out of the tray and fades) and the next one slides in from the
 * right, then spins slowly while it's "loaded". Only discs here, whatever the game look is.
 * On the right: "Now selected", the game's name, console and kind of disc, chips (time played, achievements, last
 * played), a few lines about the game, and the buttons ◀ Play ▶ and Details along the bottom.
 * ◀ ▶ the previous / next game (the home screen's own), ▼ the buttons (◀ ▶ between them, ▲ / B back), ▲ what lies above.
 * Touch: tap the disc to open the game's window, swipe it to move along.
 *
 * Update 151: every disc is cut to its circle (a square scan's black / white corners never show, turning or not), no
 * dark shadow under it; instead a faint glow ring in the game's colour, a soft rainbow sheen that turns with the disc,
 * the game's logo (or name) above it, and the text on the right on a soft see-through panel with shadowed letters
 * (DiscStationFx.kt).
 */

@Composable
internal fun StyleDiscStation(c: StyleCtx, modifier: Modifier = Modifier) {
    val g = c.game
    val infoOff = LayoutParts.off(HomeLayout.DISCS, "info")
    SideEffect {
        StyleNav.side = emptyList()
        StyleNav.top = emptyList()
        // ▼: ◀ previous, Play, ▶ next, Details.
        // Update 156: with Endless on, ◀ on the first game goes round to the last one and ▶ on the last to the first.
        val round = loopRowOn(c)
        StyleNav.bar = if (g == null || infoOff) emptyList() else listOf(
            { if (c.focus > 0) c.actions.select(c.focus - 1) else if (round) { DiscBarTurn.went(c.games.size - 1, -1); c.actions.select(c.games.size - 1) } },
            { c.actions.play(g) },
            { if (c.focus < c.games.size - 1) c.actions.select(c.focus + 1) else if (round) { DiscBarTurn.went(0, 1); c.actions.select(0) } },
            { c.actions.open() }
        )
    }
    // Plain if / else, never an early return out of an inline composable lambda.
    if (g == null) StyleEmpty(modifier) else DiscStation(c, g, infoOff, modifier)
}

/** ▼ goes to the buttons (on Play), ▲ to what lies above; ◀ ▶ are the home screen's own. */
internal fun discStationKey(ctx: StyleKeyCtx): Boolean = when (ctx.key) {
    Key.DirectionDown -> if (StyleNav.bar.isNotEmpty()) {
        StyleNav.forget(StyleNav.Zone.BAR)
        // (Onto Play, the second button.)
        ctx.enterZone(StyleNav.Zone.BAR).also { if (StyleNav.zone == StyleNav.Zone.BAR) StyleNav.index = 1.coerceAtMost(StyleNav.bar.size - 1) }
    } else ctx.reach(false)
    Key.DirectionUp -> ctx.reach(true)
    else -> false
}

@Composable
internal fun DiscStationSettings() {
    MutedNote("One disc player: the game you're on is the disc in the tray on the left, its name, console, time played, achievements and a few lines about it on the right. Moving to another game ejects the disc and loads the next one. Every game is a disc here — cartridge games and apps get a plain CD with their label or cover on it. ◀ ▶ move between games, ▼ goes to the buttons.")
    Spacer(Modifier.height(10.dp))
    SwitchRow(
        Icons.Filled.Album, "Spin the loaded disc", "The disc turns slowly once it's in the tray. Off: it stands still (a little lighter on the battery).",
        Features.discSpin.state.value, { Features.discSpin.value = it }
    )
    Spacer(Modifier.height(8.dp))
    // Update 154.
    StyleChoice("The disc sits in", Features.DiscPlayerLook.values(), Features.discPlayer.state.value, { it.label }) { Features.discPlayer.value = it }
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.Album, "Calm background", "No video clip and no moving background behind the player — only the disc turns. Off: the home screen's background as everywhere else.",
        Features.discCalm.state.value, { Features.discCalm.value = it }
    )
    Spacer(Modifier.height(8.dp))
    if (Features.discPlayer.state.value == Features.DiscPlayerLook.ART) PercentSlider(
        "Blur behind the disc", Features.discBlur.state.value, { Features.discBlur.value = it.coerceIn(0, 30) },
        min = 0, max = 30, valueText = { if (it == 0) "Sharp" else "$it %" }
    )
    if (Features.discPlayer.state.value == Features.DiscPlayerLook.ART) MutedNote("The game's art fills the player behind the disc, softly blurred.")
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.Description, "About the game", "A few lines about the game under its name, when its details are there.",
        Features.discAbout.state.value, { Features.discAbout.value = it }
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun DiscStation(c: StyleCtx, g: Game, infoOff: Boolean, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < 330.dp
        val pad = if (compact) 8.dp else 14.dp
        // The player: a square-ish panel as tall as the screen allows, at most a bit over half its width.
        val panelW = minOf(maxWidth * 0.52f, maxHeight - pad * 2)
        Row(Modifier.fillMaxSize().padding(pad), horizontalArrangement = Arrangement.spacedBy(pad)) {
            DiscPanel(c, g, compact, Modifier.width(panelW).fillMaxHeight())
            if (!infoOff) DiscInfo(c, g, compact, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** The panel with the tray: the disc that's in it, ejected and loaded as you move, and "Disc loaded" under it. */
@Composable
private fun DiscPanel(c: StyleCtx, g: Game, compact: Boolean, modifier: Modifier) {
    val open by rememberUpdatedState(c.actions.open)
    val shape = RoundedCornerShape(if (compact) 12.dp else 16.dp)
    // Update 156: with Endless on, which way the last move went, from the place counted on round the ends
    // (EndlessRow.kt): going round from the last game to the first loads the next disc from the right like any step
    // forward (comparing the places in the list, it came in from the left). A new game at the same place (another
    // category): from the right, as before.
    val loopOn = loopRowOn(c)
    val vf = rememberLoopFocus(c)
    // [last place, way (1 / -1), 1 = a move along the same list (else as before: by the places in the list)]
    val turn = remember { intArrayOf(vf, 1, 0) }
    val turnList = remember { arrayOf<Any?>(c.games) }
    // Update 156 (review): another list (a category change) slides as it did before Endless; the bar's ◀ ▶ going
    // round say which way they went (with two games the place alone can't tell).
    if (turnList[0] !== c.games) turn[2] = 0
    else if (vf != turn[0]) {
        turn[1] = if (DiscBarTurn.to == c.focus && DiscBarTurn.dir != 0) DiscBarTurn.dir else if (vf > turn[0]) 1 else -1
        turn[2] = 1
    }
    if (vf != turn[0] || turnList[0] !== c.games) DiscBarTurn.dir = 0
    turn[0] = vf
    turnList[0] = c.games
    // (Update 149: no dark panel behind the disc — the game's own art, softly blurred (Disc station › Blur behind the
    // disc, 5 % by default), fills the player instead.)
    BoxWithConstraints(modifier.clip(shape)) {
    // Update 154: the disc sits in a drawn portable CD player by default (Disc station › The disc sits in); the game's
    // art, blurred, is the other choice.
    val player = Features.discPlayer.state.value == Features.DiscPlayerLook.PLAYER
    val pct = Features.discBlur.state.value
    // Update 153: a game without a background picture (apps, games with only a cover) takes its cover instead.
    val backdropUrl = c.backdrop(g) ?: c.cover(g)
    // (The player still reads the picture once, small, for the game's colour.)
    val artNow = rememberSoftBitmap(backdropUrl, if (player) 96 else 480)
    // Update 151 (review): while the next game's picture loads, the last one stays behind the disc (it used to go empty
    // for a moment, the dark behind the player showing through at every step).
    val artKeep = remember { arrayOfNulls<android.graphics.Bitmap>(1) }
    if (artNow != null) artKeep[0] = artNow
    val art = artNow ?: if (backdropUrl != null) artKeep[0] else null
    val soft = if (player) null else if (pct > 0) rememberBakedBlur(art, maxWidth * (pct / 100f)) else art?.let { remember(it) { it.asImageBitmap() } }
    if (soft != null) androidx.compose.foundation.Image(soft, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
    // (A light shade, so the disc and the label under it stand out from the picture.)
    if (!player) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.18f)))
    // Update 151: the glow's colour, from the game's art (the theme's accent until it's there).
    val glowTarget = rememberDiscGlow(art, ShelfTheme.accent)
    val glow by androidx.compose.animation.animateColorAsState(glowTarget, tween(400), label = "discGlow")
    // Update 153: never a black player — a soft wash of the game's colour lights it from behind the disc, stronger when
    // there's no picture at all or the picture is very dark (the user saw black behind the disc on those games).
    val dark = remember(art) { art?.let { discIsDark(it) } ?: true }
    val wash = if (soft == null || dark) 0.55f else 0.22f
    if (player) Box(Modifier.matchParentSize().discPlayerBody(glow))
    else Box(
        Modifier.matchParentSize().drawBehind {
            drawRect(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    0f to glow.copy(alpha = wash),
                    0.6f to glow.copy(alpha = wash * 0.45f),
                    1f to glow.copy(alpha = wash * 0.18f),
                    center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.55f),
                    radius = size.maxDimension * 0.75f
                ),
                blendMode = androidx.compose.ui.graphics.BlendMode.Screen
            )
        }
    )
    Column(
        Modifier.fillMaxSize().padding(if (compact) 10.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Update 151: the game's logo (else its name in big letters) above the disc.
        AnimatedContent(
            targetState = g, contentKey = { it.stableKey }, label = "discLogo",
            transitionSpec = { fadeIn(tween(260, delayMillis = 120)) togetherWith fadeOut(tween(180)) }
        ) { shown ->
            DiscTitleArt(c, shown, compact, Modifier.fillMaxWidth(0.86f).height(if (compact) 40.dp else 60.dp))
        }
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().tapOnly { open() },
            contentAlignment = Alignment.Center
        ) {
            // Update 151: a little smaller, so the glow round it has room.
            val d = minOf(maxWidth, maxHeight) * 0.86f
            // Update 151: no dark shadow under the disc any more (it showed as black behind it): a faint glow ring in
            // the game's colour round it instead (drawn once, it doesn't turn).
            val glowPick = remember(g.platform) { stationPick(g.platform) }
            // Update 154: in the player, the round well it sits in instead.
            if (player) Box(Modifier.size(d).discWell(glow, stationDiscFrac(glowPick, Features.bigGcDiscs.state.value)))
            else Box(Modifier.size(d).discGlow(glow, stationDiscFrac(glowPick, Features.bigGcDiscs.state.value)))
            // Moving on ejects the old disc (down and out) and loads the next one (in from the right).
            AnimatedContent(
                targetState = g,
                contentKey = { it.stableKey },
                transitionSpec = {
                    // (Update 156: with Endless on, the way the move went, see [turn].)
                    val dir = if (loopOn && turn[2] == 1) turn[1]
                    else if (c.games.indexOfFirst { it.stableKey == targetState.stableKey } >= c.games.indexOfFirst { it.stableKey == initialState.stableKey }) 1 else -1
                    (slideInHorizontally(tween(420, delayMillis = 140, easing = FastOutSlowInEasing)) { w -> w * dir } + fadeIn(tween(260, delayMillis = 140))) togetherWith
                        (slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { h -> h / 2 } + fadeOut(tween(260)))
                },
                label = "discTray"
            ) { shown ->
                // (Update 154: its place on screen, for Starting a game's disc dropping into the tray.)
                Box(Modifier.size(d).gameOrigin(shown.stableKey, shown.stableKey == g.stableKey), contentAlignment = Alignment.Center) { LoadedDisc(c, shown, d) }
            }
        }
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
        // Update 154: the player's LCD and buttons along its front edge.
        if (player) DiscPlayerFront(glow, compact)
        else Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF3DDC84)))
            Spacer(Modifier.width(6.dp))
            Text("DISC LOADED", color = Color.White.copy(alpha = 0.85f), fontSize = if (compact) 10.sp else 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, style = discTextStyle())
        }
    }
    }
}

/** The game's disc, [d] across, turning slowly while it sits in the tray (Disc station › Spin the loaded disc). */
@Composable
private fun LoadedDisc(c: StyleCtx, g: Game, d: Dp) {
    val spin = Features.discSpin.state.value && !HomeIdle.still
    val angle = if (spin) {
        val t = rememberInfiniteTransition(label = "discSpin")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart), label = "discAngle")
    } else null
    val cover = c.cover(g)
    // Its own label scan when there is one, else the cover (printed on the disc).
    // Update 159: only a game that really came on a disc prints its own scan — a cartridge game's scan is the cartridge's
    // photo (it showed as a cartridge on the CD); those print their cover.
    val scan = if (mediaKind(g.platform) != "disc") null
        else typedArtFor(CardArtType.CARTRIDGE_DISC, c.state.physicalMedia[g.stableKey], c.state.screenshots[g.stableKey], g.platform, g.stableKey)
    val label: Any? = scan ?: cover
    val pick = remember(g.platform) { stationPick(g.platform) }
    // Update 151: cut to the disc's circle (a UMD keeps its own shape), so a square scan's black / white corners never
    // show, turning or not.
    // (Update 342, Toni: "the disc station looks weird with the PSP's UMD": the UMD shell stood in the round tray. Only the
    // disc from inside it now — round, smaller, with the game's art printed on it — the shell stays out of the player.)
    val umd = pick.shell == MediaShell.UMD
    val round = true
    val small = pick.shell == MediaShell.MINI_DISC && !Features.bigGcDiscs.state.value
    val frac = if (umd) UMD_DISC_FRAC else stationDiscFrac(pick, Features.bigGcDiscs.state.value)
    val labelIsScan = scan != null && label == scan
    // Update 151: with "Disc in its case" on, only the disc here (never its case half round it).
    val tpl = if (genericDiscConsole(pick.platform) == null) MediaTemplates.of(pick.platform) else null
    val circle = remember(frac) { DiscCircle(frac) }
    Box(
        Modifier.size(d).graphicsLayer {
            rotationZ = angle?.value ?: 0f
            if (round) {
                clip = true
                shape = circle
            }
        },
        contentAlignment = Alignment.Center
    ) {
        if (umd) {
            Box(Modifier.fillMaxSize(UMD_DISC_FRAC), contentAlignment = Alignment.Center) {
                PhysicalMedia(
                    MediaShell.DISC, cover ?: label, Modifier.fillMaxSize(), platform = null,
                    labelIsScan = cover == null && labelIsScan, title = g.name, noCase = true
                )
            }
        } else if (tpl != null && discCaseOn(tpl, pick.platform)) {
            TemplateMedia(
                MediaTpl(null, null, tpl.disc, null), labelScan = if (labelIsScan) label else null,
                cover = if (labelIsScan) null else label, modifier = Modifier.fillMaxSize(), smallDisc = small
            )
        } else {
            PhysicalMedia(
                pick.shell, label, Modifier.fillMaxSize(), platform = pick.platform,
                labelIsScan = labelIsScan, caseCover = cover, title = g.name
            )
        }
        // Update 151: a soft rainbow sheen, turning with the disc (cached; a spinning disc never redraws it).
        if (round) Box(Modifier.fillMaxSize().discSheen(frac))
    }
}

/** Update 342: how much of the tray a UMD's own disc fills (60 mm against a CD's 120, drawn as big as a GameCube disc here). */
private const val UMD_DISC_FRAC = 0.7f

/** Update 151: the disc a game is shown as here — its own, else the plain CD with its console's branding. */
private fun stationPick(platform: String): MediaPick =
    mediaFor(platform, asDisc = true)?.takeIf { it.shell.isDisc || it.shell == MediaShell.UMD || it.platform.startsWith(GENERIC_DISC) }
        ?: MediaPick(MediaShell.DISC, genericDiscKey(if (platform == "App") "Android" else platform))

/** Update 151: how much of its square the disc fills (a GameCube disc is drawn at its real, smaller size). */
private fun stationDiscFrac(pick: MediaPick, bigGc: Boolean): Float =
    if (pick.shell == MediaShell.MINI_DISC && !bigGc) 0.7f else 1f

/**
 * Update 156 (review): the bar's ◀ / ▶ going round with Endless on (first game -> last, last -> first) say which way
 * they went, so the next disc comes in from the right side even with two games (used once, by DiscPanel).
 */
private object DiscBarTurn {
    @Volatile var to = -1
    @Volatile var dir = 0

    fun went(to: Int, dir: Int) {
        this.to = to
        this.dir = dir
    }
}

/** Update 151: a circle [frac] of the box across, in its middle (the disc's outline). */
private class DiscCircle(private val frac: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val r = minOf(size.width, size.height) / 2f * frac
        val cx = size.width / 2f
        val cy = size.height / 2f
        return androidx.compose.ui.graphics.Outline.Generic(
            androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r)) }
        )
    }

    override fun equals(other: Any?): Boolean = other is DiscCircle && other.frac == frac
    override fun hashCode(): Int = frac.hashCode()
}

/** The right-hand side: what's in the tray, its details, and the buttons. */
@Composable
private fun DiscInfo(c: StyleCtx, g: Game, compact: Boolean, modifier: Modifier) {
    val titles = LocalTitleOptions.current
    val accent = ShelfTheme.accent
    // (Update 149: on a dark panel like the player's, so the text reads over any background picture.)
    // Update 151: a soft, rounded, see-through panel instead (not a black box): a cool dark tint that deepens towards
    // the bottom like frosted glass, a touch of the theme's accent, a faint light edge; the letters get a soft shadow.
    val panel = RoundedCornerShape(if (compact) 14.dp else 20.dp)
    val ink = discTextStyle()
    Column(
        modifier
            .clip(panel)
            .background(Brush.verticalGradient(listOf(Color(0xFF141A2A).copy(alpha = 0.42f), Color(0xFF0C111C).copy(alpha = 0.62f))))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.10f), Color.Transparent)))
            .border(1.dp, Color.White.copy(alpha = 0.14f), panel)
            .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = if (compact) 8.dp else 14.dp)
    ) {
        Text(
            "NOW SELECTED", color = Color.White.copy(alpha = 0.7f), fontSize = if (compact) 10.sp else 12.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, style = ink
        )
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        FitText(
            titles.display(g.name), color = Color.White, maxSize = if (compact) 20.sp else 30.sp, minSize = 13.sp,
            maxLines = 2, fontWeight = FontWeight.Bold, style = ink
        )
        Spacer(Modifier.height(4.dp))
        Text(
            titles.console(g.platform) + " · " + discKind(g.platform), color = Color.White.copy(alpha = 0.8f),
            fontSize = if (compact) 11.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ink
        )
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            c.playtime(g)?.let { DiscChip(com.shelf.launcher.data.PlaytimeStore.format(it), Color.White, compact) }
            c.progress(g)?.let { (a, b) -> DiscChip("$a / $b", Color(0xFFF2C14E), compact) }
            DiscChip(if (c.lastPlayed(g) != null) "READY" else "NEW", Color(0xFF3DDC84), compact, tint = true)
        }
        // A few lines about the game, where there's room.
        val summary = if (Features.discAbout.state.value) c.state.metadata[g.stableKey]?.summary?.trim()?.takeIf { it.isNotEmpty() } else null
        if (summary != null) {
            Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
            // (Update 199: as many whole lines as the panel has room for — seven lines pushed the panel's last line, the
            // category and count, off its foot, cut in half.)
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val lh = if (compact) 13.sp else 16.sp
                val fits = with(androidx.compose.ui.platform.LocalDensity.current) { (maxHeight.toPx() / lh.toPx()).toInt() }
                val lines = minOf(fits, if (compact) 4 else 7)
                if (lines > 0) Text(
                    summary, color = Color.White.copy(alpha = 0.82f), fontSize = if (compact) 10.sp else 12.sp,
                    lineHeight = lh, maxLines = lines, overflow = TextOverflow.Ellipsis, style = ink
                )
            }
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        } else Spacer(Modifier.weight(1f))
        // The buttons: ◀ Play ▶ and Details (▼ reaches them; ◀ ▶ between them).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
            val bar = StyleNav.bar
            DiscRound(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous", StyleNav.on(StyleNav.Zone.BAR, 0), compact) { bar.getOrNull(0)?.invoke() }
            DiscPill(Icons.Filled.PlayArrow, if (c.lastPlayed(g) != null) "Continue" else "Play", StyleNav.on(StyleNav.Zone.BAR, 1), accent, compact, Modifier.weight(1f)) { bar.getOrNull(1)?.invoke() }
            DiscRound(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next", StyleNav.on(StyleNav.Zone.BAR, 2), compact) { bar.getOrNull(2)?.invoke() }
            DiscRound(Icons.Filled.Info, "Details", StyleNav.on(StyleNav.Zone.BAR, 3), compact) { bar.getOrNull(3)?.invoke() }
        }
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        // (Update 315: off for this layout — Categories tile / Settings — no caption.)
        val catOn = CategoryTitle.captionOn(c.state.appearance)
        if (catOn) CategoryTitle.Native() // (Update 212: the layout's own category title.)
        Text(
            (if (catOn) "${c.categoryLabel} · " else "") + "${c.focus + 1} / ${c.games.size}", color = Color.White.copy(alpha = 0.6f),
            fontSize = if (compact) 9.sp else 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ink
        )
    }
}

/** What kind of disc the game came on (cartridge games: what they'd be pressed on here, a CD). */
private fun discKind(platform: String): String = when (platform) {
    "PS2", "Xbox", "Wii", "PS3" -> if (platform == "PS3") "Blu-ray" else "DVD-ROM"
    "PS4", "PS5", "WiiU", "XboxOne", "XboxSeries", "Switch" -> if (platform == "Switch") "Game card" else "Blu-ray"
    "GameCube" -> "Mini disc"
    "PSP" -> "UMD"
    else -> if (mediaKind(platform) == "disc") "CD-ROM" else "On CD"
}

@Composable
private fun DiscChip(text: String, color: Color, compact: Boolean, tint: Boolean = false) {
    Text(
        text, color = color, fontSize = if (compact) 10.sp else 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (tint) color.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.1f))
            .padding(horizontal = if (compact) 8.dp else 11.dp, vertical = if (compact) 3.dp else 5.dp)
    )
}

@Composable
private fun DiscRound(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, compact: Boolean, onTap: () -> Unit) {
    val s = if (compact) 34.dp else 44.dp
    Box(
        Modifier
            .graphicsLayer { val k = if (on) 1.08f else 1f; scaleX = k; scaleY = k }
            .size(s)
            .clip(CircleShape)
            .background(if (on) Color.White.copy(alpha = 0.18f) else Color.Transparent)
            .border(if (on) 2.dp else 1.dp, if (on) ShelfTheme.accent else Color.White.copy(alpha = 0.25f), CircleShape)
            .tapOnly(onTap),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(if (compact) 20.dp else 24.dp)) }
}

@Composable
private fun DiscPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, accent: Color, compact: Boolean, modifier: Modifier = Modifier, onTap: () -> Unit) {
    val pill = RoundedCornerShape(50)
    // (Update 149: it takes the room the round buttons leave, so Details always fits beside it.)
    Row(
        modifier
            .graphicsLayer { val k = if (on) 1.06f else 1f; scaleX = k; scaleY = k }
            .clip(pill)
            .background(Color.White)
            .border(if (on) 3.dp else 0.dp, if (on) accent else Color.Transparent, pill)
            .tapOnly(onTap)
            .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = if (compact) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Color(0xFF16171B), modifier = Modifier.size(if (compact) 18.dp else 22.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color(0xFF16171B), fontSize = if (compact) 13.sp else 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
