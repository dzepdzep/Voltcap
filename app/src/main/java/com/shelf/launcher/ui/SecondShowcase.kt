package com.shelf.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.NowPlaying
import com.shelf.launcher.data.PlaytimeStore
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Update 189: the second screen's "Game art" look (Settings › Screens › Second screen art & music › Look; the default).
 * Like a handheld's own now-playing screen: the game's art fills the whole screen, its logo big in the middle, two small
 * floating pills at the top — you (your picture, the page you're on) on the left; notifications, battery and the time
 * on the right — and a floating dock of round app icons with a home button along the bottom. The pages swipe as before;
 * small dots show where you are. Everything the classic look has is still there.
 */

/** Height kept free at the top for the pills, and at the bottom for the dock. */
internal val SHOWCASE_TOP: Dp = 52.dp
internal val SHOWCASE_BOTTOM: Dp = 78.dp

private val PillBg = Color(0xB3101114)
private val PillEdge = Color.White.copy(alpha = 0.10f)
private val ChargeGreen = Color(0xFF59E38A)

/** The full-screen art behind every page (it goes grey and darker while the second screen is dimmed). */
@Composable
internal fun ShowcaseBackdrop(info: SecondHomeGame, sceneBehind: (@Composable () -> Unit)?) {
    val art = info.heroUrl ?: info.wideUrl ?: info.coverUrl
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (art != null) {
            androidx.compose.animation.Crossfade(art, Modifier.fillMaxSize(), androidx.compose.animation.core.tween<Float>(420), label = "showcaseArt") { url ->
                AsyncImage(
                    model = url, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().pixelArtwork().secondDimArt()
                )
            }
        } else if (sceneBehind != null) sceneBehind()
        // Darker at the top and the bottom, so the pills and the dock always read (the middle stays clear).
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.45f), 0.18f to Color.Transparent,
                    0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f)
                )
            )
        )
    }
}

/** The Art page in this look: the game's logo big in the middle (its name when there's none), a line under it. */
@Composable
internal fun ShowcaseArtPage(info: SecondHomeGame, logoUrl: String?, nowPlayingCard: (@Composable () -> Unit)?) {
    val game = info.game
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (game == null) {
            Text("Pick a game", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        } else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            if (logoUrl != null) {
                AsyncImage(
                    model = logoUrl, contentDescription = game.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(0.62f).heightIn(max = 170.dp).secondDimArt()
                )
            } else {
                Text(
                    gameDisplayName(game.name), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), blurRadius = 14f)),
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            val bits = listOfNotNull(
                com.shelf.launcher.data.ConsoleNames.display(game.platform),
                info.playtimeMs?.takeIf { it > 0 }?.let { "Played ${PlaytimeStore.format(it)}" }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                bits.joinToString("  ·  "), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), blurRadius = 10f)), maxLines = 1
            )
            if (game.stableKey in com.shelf.launcher.data.RunningGames.keys) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (game.stableKey in com.shelf.launcher.data.RunningGames.confirmed) "● Still open · A resumes · X closes" else "● Probably still open · X closes",
                    color = RunningGlowColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        if (nowPlayingCard != null) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.9f)) { nowPlayingCard() }
    }
}

/** A page other than the art: on a soft dark glass so it reads over the art. */
@Composable
internal fun ShowcasePanel(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, PillEdge, RoundedCornerShape(22.dp)),
        content = content
    )
}

/** One floating pill (the top bar's halves, the dock). */
@Composable
private fun Pill(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier.shadow(10.dp, RoundedCornerShape(50), clip = false).clip(RoundedCornerShape(50)).background(PillBg)
            .border(1.dp, PillEdge, RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * The two pills at the top: you and the page on the left (a tap: the next page), notifications, battery and the time on
 * the right ([endInset] keeps room for something drawn over the right end, the in-game timer).
 */
@Composable
internal fun ShowcaseTopBar(
    avatarUrl: String?, points: Int?, pageLabel: String, onPageTap: () -> Unit, endInset: Dp, modifier: Modifier = Modifier
) {
    val battery = rememberBatteryInfo()
    val context = LocalContext.current
    val bar by com.shelf.launcher.data.StatusBarPrefs.state(context).collectAsState()
    val clock = rememberClockText(true, bar.hours, false)
    val resumed = rememberIsResumed()
    val none = remember { kotlinx.coroutines.flow.MutableStateFlow<List<com.shelf.launcher.data.ShelfNotification>>(emptyList()) }
    val notes by (if (resumed) com.shelf.launcher.data.ShelfNotificationListener.notifications else none).collectAsState()
    val noteCount = notes.count { !it.ongoing }
    Row(modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp + endInset, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Pill(Modifier.tapOnly(onPageTap)) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(ShelfTheme.accent), contentAlignment = Alignment.Center) {
                if (avatarUrl != null) AsyncImage(avatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text("P1", color = onAccentColor(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (points != null) {
                Spacer(Modifier.width(7.dp))
                Text("$points", color = ShelfTheme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Text(pageLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.width(6.dp))
        }
        Spacer(Modifier.weight(1f))
        Pill {
            Spacer(Modifier.width(4.dp))
            if (noteCount > 0) {
                Icon(Icons.Filled.Notifications, "Notifications", tint = Color.White, modifier = Modifier.size(15.dp))
                Text("$noteCount", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                if (battery.charging) Icons.Filled.BatteryChargingFull else batteryIconFor(battery.percent, false), null,
                tint = when {
                    battery.charging -> ChargeGreen
                    battery.percent < 15 -> LowBatteryColor
                    else -> Color.White
                },
                modifier = Modifier.size(16.dp)
            )
            Text(
                "${battery.percent}%", color = if (battery.charging) ChargeGreen else Color.White,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
            )
            if (clock != null) {
                Spacer(Modifier.width(8.dp))
                Text(clock, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.width(6.dp))
        }
    }
}

/** Small dots for the pages (the one you're on long and lit). */
@Composable
internal fun ShowcaseDots(count: Int, index: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (count >= 2) for (i in 0 until count) {
            val on = i == index
            Box(
                Modifier.height(5.dp).width(if (on) 16.dp else 5.dp).clip(RoundedCornerShape(50))
                    .background(if (on) Color.White else Color.White.copy(alpha = 0.4f))
            )
        }
    }
}

/**
 * The floating dock: home (back to the first page), your apps as round icons (hold one: move or remove it; + adds one),
 * all apps and the settings.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ShowcaseDock(
    apps: List<Game>, maxApps: Int, onHome: () -> Unit, onLaunch: (Game) -> Unit, onAllApps: () -> Unit, onSettings: () -> Unit,
    onAdd: () -> Unit, onMenu: (Game) -> Unit, modifier: Modifier = Modifier
) {
    Pill(modifier.widthIn(max = 640.dp)) {
        DockRound(rememberPadId("sc-home"), onHome, bg = ShelfTheme.accent) {
            Icon(Icons.Filled.Home, "Home", tint = onAccentColor(), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(alpha = 0.14f)))
        Spacer(Modifier.width(6.dp))
        Row(Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            apps.forEach { app ->
                val id = rememberPadId("sc-app")
                Box(
                    Modifier.size(40.dp).padTarget(id) { onLaunch(app) }.clip(CircleShape).background(Color.White.copy(alpha = 0.95f))
                        .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onMenu(app) }),
                    contentAlignment = Alignment.Center
                ) { AppIconArt(app.packageName, wide = false, modifier = Modifier.fillMaxSize().padding(5.dp)) }
            }
            if (apps.size < maxApps) DockRound(rememberPadId("sc-add"), onAdd, bg = Color.White.copy(alpha = 0.10f)) {
                Icon(Icons.Filled.Add, "Add an app to the dock", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(alpha = 0.14f)))
        Spacer(Modifier.width(6.dp))
        DockRound(rememberPadId("sc-all"), onAllApps, bg = Color.White.copy(alpha = 0.10f)) {
            Icon(Icons.Filled.Apps, "All apps", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(6.dp))
        DockRound(rememberPadId("sc-set"), onSettings, bg = Color.White.copy(alpha = 0.10f)) {
            Icon(Icons.Filled.Settings, "Settings", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DockRound(id: String, onClick: () -> Unit, bg: Color, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp).padTarget(id) { onClick() }.clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
