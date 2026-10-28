package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.FocusEffect
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.NameBackdrop
import com.shelf.launcher.data.RaBadges
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Home layout › Dashboard: glass panels over the theme. A menu of round buttons down the left (your round
 * shortcuts) under a title, the game you're on big in a "Now playing" card with Play / Continue, the row
 * under it, a Quick access strip, and a player card and the system's status on the right.
 * ◀ ▶ move along the row; ▲ (or ◀ on the first game) goes to the menu, ▼ to Quick access; ▶ / ▲ / B come back.
 * Options: Appearance › Game row › Dashboard options.
 */
@Composable
internal fun StyleDashboard(c: StyleCtx, modifier: Modifier) {
    val panel = Features.dashPanel.state.value
    val op = Features.dashOpacity.state.value / 100f
    // Update 130: the middle (Now playing, the row, Quick access) is half way stronger than the side panels, so the games
    // read clearly over a busy background; the Panel opacity setting still moves both.
    val midOp = op + (1f - op) * 0.5f
    val shape: Shape = if (Features.dashCut.state.value) CutCornerShape(topEnd = 14.dp, bottomStart = 14.dp) else uiRounded(14.dp)
    val menuOn = Features.dashMenu.state.value
    val menu = if (menuOn) c.shortcuts.take(Features.dashMenuCount.state.value) else emptyList()
    // Update 144: Quick access shows the shortcuts after the menu's, not the same ones again (back to the start
    // when the menu already shows them all).
    val rest = if (menuOn) c.shortcuts.drop(menu.size) else c.shortcuts
    val quick = rest.ifEmpty { c.shortcuts }.take(Features.dashQuick.state.value)
    // (Update 221, #60 / #96: the focus pulse is read in the highlighted card only — DashRow. Read here, its 20 ticks a
    // second rebuilt the whole Dashboard — menu, Now playing, the row, Quick access — measured on the RP Classic as
    // ~20 frames a second at ~30 ms each while nothing moved.)
    val pulseOn = !LocalLiteEffects.current && LocalCardLook.current.focusEffect != FocusEffect.NONE
    BoxWithConstraints(modifier) {
        val compact = maxHeight < 360.dp
        val music = styleMusicShown(c)
        val showRight = (Features.dashProfile.state.value || Features.dashStatus.state.value || music) && maxWidth >= 640.dp
        // The D-pad's buttons are the ones on screen (update 101): Play in Now playing (▲), the menu (◀ / ▲ without
        // Now playing), Quick access and then the player's ⏮ ⏯ ⏭ (▼).
        val heroPlay = Features.dashHero.state.value && c.game != null
        val musicHere = music && showRight
        SideEffect {
            StyleNav.side = menu.map { it.onClick }
            StyleNav.bar = quick.map { it.onClick } + (if (musicHere) styleMusicActions(c) else emptyList())
            StyleNav.top = if (heroPlay) listOf({ c.game?.let { g -> c.actions.play(g) } ?: Unit }) else emptyList()
        }
        val title = Features.dashTitle.state.value
        val fullW = maxWidth
        Row(Modifier.fillMaxSize()) {
            if (menuOn) {
                DashMenu(
                    menu, title, Features.dashSubtitle.state.value, Features.dashMenuLabels.state.value,
                    width = if (Features.dashMenuLabels.state.value) (fullW * 0.22f).coerceIn(150.dp, 240.dp) else 72.dp,
                    compact = compact
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 8.dp)) {
                if (!menuOn && title.isNotBlank()) DashTitle(title, Features.dashSubtitle.state.value)
                val g = c.game
                // Plain if / else, never an early return@Column (it unbalances Compose's groups, see AppIcons.kt).
                if (g == null) {
                    StyleEmpty(Modifier.weight(1f))
                } else {
                    if (Features.dashHero.state.value) {
                        DashHero(c, g, panel, midOp, shape, Modifier.fillMaxWidth().weight(1f))
                        Spacer(Modifier.height(8.dp))
                    }
                    val rowH = if (Features.dashHero.state.value) ((this@BoxWithConstraints.maxHeight * 0.4f).coerceIn(110.dp, 260.dp) * GameSize.scale(com.shelf.launcher.data.HomeLayout.DASH).coerceAtLeast(1f)).coerceAtMost(this@BoxWithConstraints.maxHeight * 0.6f) else 0.dp
                    DashRow(
                        c, panel, midOp, shape, pulseOn,
                        (if (Features.dashHero.state.value) Modifier.height(rowH) else Modifier.weight(1f)).fillMaxWidth()
                    )
                    if (quick.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        DashQuick(quick, panel, midOp, shape, compact)
                    }
                }
            }
            if (showRight) {
                Column(
                    Modifier.width((fullW * 0.24f).coerceIn(180.dp, 290.dp)).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Both cards need ~310 dp; on shorter screens the status card tightens, and goes if there's no room at all.
                    val h = this@BoxWithConstraints.maxHeight
                    val both = Features.dashProfile.state.value && Features.dashStatus.state.value
                    // The player takes the bottom of the column; with it the status card needs a taller screen.
                    val statusRoom = if (music) h >= 520.dp else (!both || h >= 270.dp)
                    if (Features.dashProfile.state.value) DashProfile(c, panel, op, shape)
                    if (Features.dashStatus.state.value && statusRoom) DashStatus(c, panel, op, shape, tight = h < 330.dp)
                    if (music) {
                        Spacer(Modifier.weight(1f))
                        val colW = (fullW * 0.24f).coerceIn(180.dp, 290.dp)
                        Box(Modifier.fillMaxWidth().stylePanel(panel, op, shape).padding(6.dp), contentAlignment = Alignment.Center) {
                            // Update 125: the Dashboard's own player — the album cover (Dashboard options › Album cover),
                            // the song in the panel's ink and the controls, instead of the small corner player.
                            DashPlayer(c, panelInk(panel), focusedSlot = styleMusicFocus(quick.size), room = h)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashTitle(title: String, subtitle: String) {
    Column(Modifier.editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.DASH, "title"), "Title").padding(start = 4.dp, bottom = 6.dp)) {
        if (title.isNotBlank()) FullText(
            title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, style = nameTextStyle(NameBackdrop.SHADOW)
        )
        if (subtitle.isNotBlank()) FullText(
            // A dark theme accent (e.g. deep green) disappears on a picture; lifted toward white so it still reads.
            subtitle, color = ShelfTheme.accent.let { a -> if (a.luminance() < 0.3f) androidx.compose.ui.graphics.lerp(a, Color.White, 0.55f) else a }, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, style = nameTextStyle(NameBackdrop.SHADOW)
        )
    }
}

/** The title and the round buttons down the left. */
@Composable
private fun DashMenu(menu: List<HomeShortcut>, title: String, subtitle: String, labels: Boolean, width: Dp, compact: Boolean) {
    val accent = ShelfTheme.accent
    // Update 136: a soft dark shade down the left, behind the title and the menu's white labels, so they read over a
    // bright game picture (a yellow cover made the white words vanish). It fades out to the right.
    Column(
        Modifier.width(width).fillMaxHeight().background(
            Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.58f), 0.7f to Color.Black.copy(alpha = 0.34f), 1f to Color.Transparent)
        )
    ) {
        if (labels && (title.isNotBlank() || subtitle.isNotBlank())) DashTitle(title, subtitle) else Spacer(Modifier.height(4.dp))
        // Update 119: every button always fits, whole — they get smaller to fit the height instead of scrolling (a
        // scrolled menu showed a button cut in half at the bottom).
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val gap = if (compact) 2.dp else 6.dp
        val n = menu.size.coerceAtLeast(1)
        val fullDisc = if (compact) 34.dp else 44.dp
        val rowH = minOf(fullDisc + 8.dp, (maxHeight - gap * (n - 1)) / n).coerceAtLeast(18.dp)
        val disc = (rowH - 8.dp).coerceIn(14.dp, fullDisc)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            menu.forEachIndexed { i, s ->
                val on = StyleNav.on(StyleNav.Zone.SIDE, i)
                val lift = styleLift(on)
                val click by rememberUpdatedState(s.onClick)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(rowH)
                        .background(
                            Brush.horizontalGradient(
                                0f to accent.copy(alpha = 0.85f * lift), 0.75f to accent.copy(alpha = 0.35f * lift), 1f to Color.Transparent
                            )
                        )
                        .pointerInput(i) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.SIDE, i); click() } }
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(disc)
                            .graphicsLayer { val k = 1f + 0.08f * lift; scaleX = k; scaleY = k }
                            .clip(CircleShape)
                            .background(if (on) accent else Color.White.copy(alpha = 0.85f))
                            .border(2.dp, if (on) Color.White else Color.White.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(s.icon, s.label, tint = if (on) Color.White else Color(0xFF3B4B5E), modifier = Modifier.size(disc * 0.5f))
                    }
                    if (labels) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            s.label, color = Color.White, fontSize = if (compact) 13.sp else 16.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(if (compact) 13.sp else 16.sp), maxFontSize = if (compact) 13.sp else 16.sp), fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Clip, softWrap = false, style = nameTextStyle(NameBackdrop.SHADOW)
                        )
                    }
                }
            }
        }
        }
    }
}

/** A panel's small heading. */
@Composable
private fun PanelLabel(text: String, ink: Color) {
    FullText(text, color = ink.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
}

/** The game you're on, big: its art, its name and Play / Continue. */
@Composable
private fun DashHero(c: StyleCtx, g: com.shelf.launcher.data.Game, panel: Features.PanelLook, op: Float, shape: Shape, modifier: Modifier) {
    val ink = panelInk(panel)
    val titles = LocalTitleOptions.current
    val open by rememberUpdatedState(c.actions.open)
    val play by rememberUpdatedState(c.actions.play)
    val last = c.lastPlayed(g)
    Column(modifier.stylePanel(panel, op, shape).padding(8.dp)) {
        PanelLabel("Now playing", ink)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(uiRounded(6.dp))
                .background(placeholderBrush(g.hue))
                .pointerInput(g.stableKey) { detectTapGestures { open() } }
        ) {
            // Held (update 104): the next game's picture fades in over the last one, no black gap.
            HeldImage(c.backdrop(g) ?: c.wide(g), Modifier.fillMaxSize(), imageModifier = Modifier.pixelArtwork(), zoom = true)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.78f), 0.6f to Color.Transparent)))
            // Update 136: and a shade along the bottom, so the name's end, the button and "Last played" on the right
            // still read over a bright picture.
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
            // Smaller name and button when the card is short (small screens), so nothing is cut off at its top.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val small = maxHeight < 150.dp
                val tiny = maxHeight < 96.dp
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().riseOnChange(g.stableKey).padding(if (small) 8.dp else 14.dp)) {
                    if (!tiny) FullText(
                        titles.display(g.name), color = Color.White, fontSize = if (small) 16.sp else 22.sp, lineHeight = androidx.compose.ui.unit.TextUnit(1.18f, androidx.compose.ui.unit.TextUnitType.Em),
                        fontWeight = FontWeight.Bold, maxLines = if (small) 1 else 2, style = nameTextStyle(NameBackdrop.SHADOW)
                    )
                    Spacer(Modifier.height(if (small) 6.dp else 10.dp))
                    // Play on the left, last played on the right, on one line (they never overlap).
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        val playOn = StyleNav.on(StyleNav.Zone.TOP, 0)
                        Row(
                            Modifier
                                .graphicsLayer { val k = if (playOn) 1.08f else 1f; scaleX = k; scaleY = k }
                                .clip(uiRounded(4.dp))
                                .background(ShelfTheme.accent)
                                .border(if (playOn) 2.dp else 0.dp, if (playOn) Color.White else Color.Transparent, uiRounded(4.dp))
                                .pointerInput(g.stableKey) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.TOP, 0); play(g) } }
                                .padding(horizontal = if (small) 12.dp else 18.dp, vertical = if (small) 5.dp else 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, tint = onAccentColor(), modifier = Modifier.size(if (small) 16.dp else 20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (last != null) "Continue" else "Play", color = onAccentColor(), fontSize = if (small) 13.sp else 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Spacer(Modifier.weight(1f).widthIn(min = 8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (last != null) "LAST PLAYED" else "TIME PLAYED", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, style = nameTextStyle(NameBackdrop.SHADOW))
                            Text(
                                if (last != null) lastPlayedLabel(last) else stylePlaytime(c.playtime(g), c.lastPlayed(g) != null),
                                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, style = nameTextStyle(NameBackdrop.SHADOW)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The row of games under the big card. */
@Composable
private fun DashRow(c: StyleCtx, panel: Features.PanelLook, op: Float, shape: Shape, pulseOn: Boolean, modifier: Modifier) {
    val ink = panelInk(panel)
    val custom = Features.dashRowTitle.state.value
    // Update 125: the row goes round with Endless on (EndlessRow.kt).
    val fits = remember { RowFits() }
    val row = rememberLoopRow(c, fits.value)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (row.focusItem - 1).coerceAtLeast(0))
    FollowFocus(list, row)
    val keys = remember(c.games) { gameKeys(c.games) }
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    Column(modifier.stylePanel(panel, op, shape).padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)) {
        // (Update 315: off for this layout — Categories tile / Settings — no caption.)
        val catOn = custom.isNotBlank() || CategoryTitle.captionOn(c.state.appearance)
        if (custom.isBlank() && catOn) CategoryTitle.Native() // (Update 212: the layout's own category title.)
        if (catOn) PanelLabel(custom.ifBlank { c.categoryLabel }, ink)
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            // Room round the cards for the glow / electric frame (it spreads ~14 dp).
            // The R1 Size tile (update 104): smaller cards in the same panel (larger with the hero panel, which gives room).
            val h = ((maxHeight - 16.dp) * GameSize.scale(com.shelf.launcher.data.HomeLayout.DASH).coerceAtMost(1f)).coerceAtLeast(24.dp)
            val w = h * styleAspect(c.type)
            SideEffect { fits.report(maxWidth, w, 12.dp) } // (Update 226, #105)
            val corner = LocalCardLook.current.cornerDp.dp
            LazyRow(
                state = list, horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxSize().categorySlide()
            ) {
                items(row.count, key = { row.key(it, keys) }) { v ->
                    val i = row.game(v)
                    val g = c.games[i]
                    val on = v == row.focusItem
                    // While a menu has the D-pad, the game keeps its place but loses the highlight.
                    val sel = on && StyleNav.zone == StyleNav.Zone.GAMES
                    val lift = styleLift(sel)
                    // (Update 221: only the highlighted card pulses, so only it is redrawn by the pulse.)
                    val pulse = if (sel) rememberFocusPulse(animate = pulseOn) else 0.75f
                    StyleArt(
                        c, g,
                        Modifier
                            .width(w).height(h)
                            .graphicsLayer { val k = 0.94f + 0.06f * lift; scaleX = k; scaleY = k }
                            .then(if (on) Modifier.focusedCard() else Modifier)
                            .pointerInput(i) { detectTapGestures { if (i == GridHome.focus) open() else select(i) } },
                        corner = corner, focused = sel, fx = pulse, outline = 3.dp
                    )
                }
            }
        }
    }
}

/** The Quick access strip: more of your round shortcuts as wide buttons. */
@Composable
private fun DashQuick(quick: List<HomeShortcut>, panel: Features.PanelLook, op: Float, shape: Shape, compact: Boolean) {
    val ink = panelInk(panel)
    val accent = ShelfTheme.accent
    Column(Modifier.fillMaxWidth().stylePanel(panel, op, shape).padding(8.dp)) {
        if (!compact) {
            PanelLabel("Quick access", ink)
            Spacer(Modifier.height(6.dp))
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Too narrow for names (many buttons, small screen): icons only, the name of the one you're on beside it.
        val roomy = (maxWidth - 6.dp * (quick.size - 1)) / quick.size >= 104.dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            quick.forEachIndexed { i, s ->
                val on = StyleNav.on(StyleNav.Zone.BAR, i)
                val click by rememberUpdatedState(s.onClick)
                Row(
                    Modifier
                        .weight(1f)
                        .height(if (compact) 32.dp else 40.dp)
                        .clip(uiRounded(3.dp))
                        .background(if (on) accent else ink.copy(alpha = if (panel == Features.PanelLook.LIGHT) 0.07f else 0.12f))
                        .pointerInput(i) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.BAR, i); click() } }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(s.icon, s.label, tint = if (on) Color.White else ink, modifier = Modifier.size(18.dp))
                    if (roomy || on) {
                        Spacer(Modifier.width(6.dp))
                        FullText(
                            s.label, color = if (on) Color.White else ink, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        }
    }
}

/** A thin labelled bar (the player card). */
@Composable
private fun StatBar(label: String, fraction: Float, color: Color, right: String, ink: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ink.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
        Box(Modifier.weight(1f).height(7.dp).clip(uiRounded(2.dp)).background(ink.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))))
        }
        Spacer(Modifier.width(6.dp))
        Text(right, color = ink.copy(alpha = 0.75f), fontSize = 9.sp, maxLines = 1)
    }
}

/** The player: RetroAchievements picture and name (else "Player"), battery and library bars. */
@Composable
private fun DashProfile(c: StyleCtx, panel: Features.PanelLook, op: Float, shape: Shape) {
    val ink = panelInk(panel)
    val profile = c.state.raProfile
    val name = profile?.User ?: c.state.raUsername ?: "Player"
    val avatar = profilePictureUrl(RaBadges.mediaUrl(profile?.UserPic))
    val battery = rememberBatteryInfo()
    Column(Modifier.fillMaxWidth().editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.DASH, "profile"), "Profile card").stylePanel(panel, op, shape).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(ink.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                if (avatar != null) AsyncImage(avatar, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Filled.Person, null, tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                FullText(name, color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val online = profile != null
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (online) Color(0xFF4CC46B) else ink.copy(alpha = 0.4f)))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (online) profile?.TotalPoints?.let { "Online · $it pts" } ?: "Online" else "On this device",
                        color = ink.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        StatBar("PWR", battery.percent / 100f, Color(0xFF6CCB3E), "${battery.percent} / 100", ink)
        Spacer(Modifier.height(5.dp))
        val total = c.state.games.size
        // (Update 151: the library bar in the theme's accent; the battery bar stays green.)
        StatBar("LIB", if (total > 0) c.games.size / total.toFloat() else 0f, ShelfTheme.accent, gamesCount(c.games.size), ink)
    }
}

/** Battery, library, time played and network. */
@Composable
private fun DashStatus(c: StyleCtx, panel: Features.PanelLook, op: Float, shape: Shape, tight: Boolean = false) {
    val ink = panelInk(panel)
    val battery = rememberBatteryInfo()
    val wifi = rememberWifiConnected()
    val totalMs = remember(c.state.playtime) { c.state.playtime.values.sum() }
    Column(Modifier.fillMaxWidth().editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.DASH, "status"), "Status card").stylePanel(panel, op, shape).padding(horizontal = 12.dp, vertical = 8.dp)) {
        PanelLabel("System status", ink)
        Spacer(Modifier.height(4.dp))
        StatusLine(Icons.Filled.BatteryFull, "Battery", "${battery.percent}%" + if (battery.charging) " ⚡" else "", ink, tight = tight)
        StatusLine(Icons.Filled.VideogameAsset, "Library", gamesCount(c.state.games.size), ink, tight = tight)
        if (!tight) StatusLine(Icons.Filled.Schedule, "Played", if (totalMs > 0) stylePlaytime(totalMs) else "—", ink)
        StatusLine(Icons.Filled.Wifi, "Network", if (wifi) "Connected" else "Offline", ink, last = true, tight = tight)
    }
}

@Composable
private fun StatusLine(icon: ImageVector, label: String, value: String, ink: Color, last: Boolean = false, tight: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = if (tight) 3.dp else 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = ink.copy(alpha = 0.75f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = ink, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
        Text(value, color = ink.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
    }
    if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.12f)))
}

/**
 * Update 125: the Dashboard's music player — fits the glass column: the album cover (on by default; Dashboard options
 * › Album cover), the song and artist in the panel's ink, ⏮ ⏯ ⏭ and the song's progress. The D-pad reaches the three
 * buttons from Quick access (as before).
 */
@Composable
private fun DashPlayer(c: StyleCtx, ink: Color, focusedSlot: Int?, room: Dp) {
    val playing = c.state.nowPlaying ?: return
    if (!Features.styleMusic.state.value) return
    val accent = ShelfTheme.accent
    val showArt = Features.dashPlayerArt.state.value
    Column(Modifier.fillMaxWidth().padding(4.dp)) {
        if (showArt) {
            // As big as the column allows on this screen (a short screen keeps it small beside the song).
            val big = room >= 560.dp
            if (big) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val side = minOf(maxWidth, room * 0.26f)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AlbumArt(playing.art, side) }
                }
                Spacer(Modifier.height(8.dp))
                DashSongText(playing, ink)
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(playing.art, 54.dp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) { DashSongText(playing, ink) }
            }
        } else DashSongText(playing, ink)
        Spacer(Modifier.height(6.dp))
        // The song's progress.
        if (playing.durationMs > 0) {
            val pos = rememberPlaybackPosition(playing)
            val frac = (pos.toFloat() / playing.durationMs).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(3.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(ink.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(accent))
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            @Composable
            fun Btn(icon: ImageVector, label: String, on: Boolean, big: Boolean, act: () -> Unit) {
                val latest by rememberUpdatedState(act)
                Box(
                    Modifier.size(if (big) 40.dp else 32.dp).focusRing(on, CircleShape).clip(CircleShape)
                        .background(if (big) accent else ink.copy(alpha = 0.1f))
                        .pointerInput(Unit) { detectTapGestures { latest() } },
                    contentAlignment = Alignment.Center
                ) { Icon(icon, label, tint = if (big) Color.White else ink, modifier = Modifier.size(if (big) 24.dp else 20.dp)) }
            }
            Btn(androidx.compose.material.icons.Icons.Filled.SkipPrevious, "Previous", focusedSlot == MINI_PREVIOUS, false, c.actions.mediaPrevious)
            Btn(
                if (playing.isPlaying) androidx.compose.material.icons.Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (playing.isPlaying) "Pause" else "Play", focusedSlot == MINI_PLAY, true, c.actions.mediaPlayPause
            )
            Btn(androidx.compose.material.icons.Icons.Filled.SkipNext, "Next", focusedSlot == MINI_NEXT, false, c.actions.mediaNext)
        }
    }
}

@Composable
private fun DashSongText(playing: com.shelf.launcher.data.NowPlaying, ink: Color) {
    Column(Modifier.fillMaxWidth()) {
        FullText(playing.title ?: "Unknown track", color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        playing.artist?.let { FullText(it, color = ink.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1) }
    }
}
