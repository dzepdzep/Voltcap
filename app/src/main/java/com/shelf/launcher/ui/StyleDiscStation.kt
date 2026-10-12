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
 */

@Composable
internal fun StyleDiscStation(c: StyleCtx, modifier: Modifier = Modifier) {
    val g = c.game
    val infoOff = LayoutParts.off(HomeLayout.DISCS, "info")
    SideEffect {
        StyleNav.side = emptyList()
        StyleNav.top = emptyList()
        // ▼: ◀ previous, Play, ▶ next, Details.
        StyleNav.bar = if (g == null || infoOff) emptyList() else listOf(
            { if (c.focus > 0) c.actions.select(c.focus - 1) },
            { c.actions.play(g) },
            { if (c.focus < c.games.size - 1) c.actions.select(c.focus + 1) },
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
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF2A2C31), Color(0xFF1E2024))))
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .padding(if (compact) 10.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().tapOnly { open() },
            contentAlignment = Alignment.Center
        ) {
            val d = minOf(maxWidth, maxHeight) * 0.96f
            // The tray's well: a faint ring the disc sits in.
            Canvas(Modifier.size(d * 1.02f)) {
                drawCircle(Color.Black.copy(alpha = 0.35f), radius = size.minDimension / 2f)
                drawCircle(Color.White.copy(alpha = 0.05f), radius = size.minDimension / 2f, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            }
            // Moving on ejects the old disc (down and out) and loads the next one (in from the right).
            AnimatedContent(
                targetState = g,
                contentKey = { it.stableKey },
                transitionSpec = {
                    val dir = if (c.games.indexOfFirst { it.stableKey == targetState.stableKey } >= c.games.indexOfFirst { it.stableKey == initialState.stableKey }) 1 else -1
                    (slideInHorizontally(tween(420, delayMillis = 140, easing = FastOutSlowInEasing)) { w -> w * dir } + fadeIn(tween(260, delayMillis = 140))) togetherWith
                        (slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { h -> h / 2 } + fadeOut(tween(260)))
                },
                label = "discTray"
            ) { shown ->
                Box(Modifier.size(d), contentAlignment = Alignment.Center) { LoadedDisc(c, shown, d) }
            }
        }
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF3DDC84)))
            Spacer(Modifier.width(6.dp))
            Text("DISC LOADED", color = Color.White.copy(alpha = 0.7f), fontSize = if (compact) 10.sp else 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        }
    }
}

/** The game's disc, [d] across, turning slowly while it sits in the tray (Disc station › Spin the loaded disc). */
@Composable
private fun LoadedDisc(c: StyleCtx, g: Game, d: Dp) {
    val spin = Features.discSpin.state.value && !HomeIdle.deep
    val angle = if (spin) {
        val t = rememberInfiniteTransition(label = "discSpin")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart), label = "discAngle")
    } else null
    val cover = c.cover(g)
    // Its own label scan when there is one, else the cover (printed on the disc).
    val scan = typedArtFor(CardArtType.CARTRIDGE_DISC, c.state.physicalMedia[g.stableKey], c.state.screenshots[g.stableKey], g.platform)
    val label: Any? = scan ?: cover
    val pick = remember(g.platform) {
        mediaFor(g.platform, asDisc = true)?.takeIf { it.shell.isDisc || it.shell == MediaShell.UMD || it.platform.startsWith(GENERIC_DISC) }
            ?: MediaPick(MediaShell.DISC, genericDiscKey(if (g.platform == "App") "Android" else g.platform))
    }
    Box(
        Modifier.size(d).graphicsLayer { rotationZ = angle?.value ?: 0f },
        contentAlignment = Alignment.Center
    ) {
        PhysicalMedia(
            pick.shell, label, Modifier.fillMaxSize(), platform = pick.platform,
            labelIsScan = scan != null && label == scan, caseCover = cover, title = g.name
        )
    }
}

/** The right-hand side: what's in the tray, its details, and the buttons. */
@Composable
private fun DiscInfo(c: StyleCtx, g: Game, compact: Boolean, modifier: Modifier) {
    val titles = LocalTitleOptions.current
    val accent = ShelfTheme.accent
    Column(modifier.padding(top = if (compact) 2.dp else 6.dp)) {
        Text(
            "NOW SELECTED", color = Color.White.copy(alpha = 0.6f), fontSize = if (compact) 10.sp else 12.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp
        )
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        FitText(
            titles.display(g.name), color = Color.White, maxSize = if (compact) 20.sp else 30.sp, minSize = 13.sp,
            maxLines = 2, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            titles.console(g.platform) + " · " + discKind(g.platform), color = Color.White.copy(alpha = 0.72f),
            fontSize = if (compact) 11.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
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
            Text(
                summary, color = Color.White.copy(alpha = 0.7f), fontSize = if (compact) 10.sp else 12.sp,
                lineHeight = if (compact) 13.sp else 16.sp, maxLines = if (compact) 4 else 7, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.weight(1f))
        // The buttons: ◀ Play ▶ and Details (▼ reaches them; ◀ ▶ between them).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
            val bar = StyleNav.bar
            DiscRound(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous", StyleNav.on(StyleNav.Zone.BAR, 0), compact) { bar.getOrNull(0)?.invoke() }
            DiscPill(Icons.Filled.PlayArrow, if (c.lastPlayed(g) != null) "Continue" else "Play", StyleNav.on(StyleNav.Zone.BAR, 1), accent, compact) { bar.getOrNull(1)?.invoke() }
            DiscRound(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next", StyleNav.on(StyleNav.Zone.BAR, 2), compact) { bar.getOrNull(2)?.invoke() }
            DiscRound(Icons.Filled.Info, "Details", StyleNav.on(StyleNav.Zone.BAR, 3), compact) { bar.getOrNull(3)?.invoke() }
        }
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        Text(
            "${c.categoryLabel} · ${c.focus + 1} / ${c.games.size}", color = Color.White.copy(alpha = 0.45f),
            fontSize = if (compact) 9.sp else 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
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
private fun DiscPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, accent: Color, compact: Boolean, onTap: () -> Unit) {
    val pill = RoundedCornerShape(50)
    Row(
        Modifier
            .graphicsLayer { val k = if (on) 1.06f else 1f; scaleX = k; scaleY = k }
            .clip(pill)
            .background(Color.White)
            .border(if (on) 3.dp else 0.dp, if (on) accent else Color.Transparent, pill)
            .tapOnly(onTap)
            .padding(horizontal = if (compact) 14.dp else 20.dp, vertical = if (compact) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color(0xFF16171B), modifier = Modifier.size(if (compact) 18.dp else 22.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color(0xFF16171B), fontSize = if (compact) 13.sp else 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
