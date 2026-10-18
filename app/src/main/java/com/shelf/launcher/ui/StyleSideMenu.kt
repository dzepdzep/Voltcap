package com.shelf.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.FocusEffect
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.NameBackdrop
import com.shelf.launcher.ui.theme.ShelfTheme

/** The Side menu's trim colour: gold, or the theme's accent. */
@Composable
private fun sideTrim(): Color = if (Features.sideGold.state.value) Color(0xFFD9B45A) else ShelfTheme.accent

/**
 * Home layout › Side menu: a dark menu down the left (an emblem, your round shortcuts as a list, a glowing orb
 * with the game you're on under it), the game's art behind everything and the row of games along the bottom.
 * ◀ ▶ move along the row; ▲ (or ◀ twice on the first game, update 151) goes into the menu, ▲ ▼ move in it, ▶ / B come back.
 * Options: Appearance › Game row › Side menu options.
 */
@Composable
internal fun StyleSideMenu(c: StyleCtx, modifier: Modifier) {
    val menu = c.shortcuts
    val loops = loopRowOn(c)
    SideEffect {
        StyleNav.side = menu.map { it.onClick }
        StyleNav.bar = emptyList()
        StyleNav.top = emptyList()
        // Update 151: with Endless on, ◀ on the first game goes round to the last (▲ is the way into the menu).
        StyleNav.sideLoops = loops
    }
    val pulse = rememberFocusPulse(animate = !LocalLiteEffects.current && LocalCardLook.current.focusEffect != FocusEffect.NONE)
    BoxWithConstraints(modifier) {
        val compact = maxHeight < 380.dp
        val sideW = Features.sideWidth.state.value.dp.coerceAtMost(maxWidth * 0.36f)
        Row(Modifier.fillMaxSize()) {
            SidePanel(c, sideW, compact, this@BoxWithConstraints.maxHeight)
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp, end = 4.dp), verticalArrangement = Arrangement.Bottom) {
                // Plain if / else, never an early return@Column (it unbalances Compose's groups, see AppIcons.kt).
                if (c.game == null) {
                    StyleEmpty(Modifier.weight(1f))
                } else {
                    if (Features.sideRowTitle.state.value) {
                        CategoryTitle.Native() // (Update 212: the layout's own category title.)
                        Text(
                            c.categoryLabel.uppercase(), color = Color.White, fontSize = if (compact) 13.sp else 16.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(if (compact) 13.sp else 16.sp), maxFontSize = if (compact) 13.sp else 16.sp), fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = nameTextStyle(NameBackdrop.SHADOW),
                            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
                        )
                    }
                    if (HomeLayout.SIDE.name in Features.carouselLayouts.state.value) GameCarousel(
                        // Carousel look (update 104): the game you're on big in the middle of the row.
                        games = c.games, focus = c.focus, art = c.art, overrides = c.state.artOverrides,
                        onSelect = c.actions.select, onOpen = c.actions.open, cardArtType = c.type,
                        physicalMedia = c.state.physicalMedia, screenshots = c.state.screenshots, onNeedPhysical = c.actions.needPhysical,
                        loop = loopRowOn(c), loopTick = c.state.loopTick, loopStep = c.state.loopStep,
                        modifier = Modifier.fillMaxWidth().height(((this@BoxWithConstraints.maxHeight * 0.42f).coerceIn(120.dp, 270.dp) * GameSize.scale(HomeLayout.SIDE)).coerceAtMost(this@BoxWithConstraints.maxHeight * 0.6f))
                    ) else SideRow(c, pulse, Modifier.fillMaxWidth().height(((this@BoxWithConstraints.maxHeight * 0.36f).coerceIn(110.dp, 230.dp) * GameSize.scale(com.shelf.launcher.data.HomeLayout.SIDE)).coerceAtMost(this@BoxWithConstraints.maxHeight * 0.55f)))
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

/** The menu panel on the left. */
@Composable
private fun SidePanel(c: StyleCtx, width: androidx.compose.ui.unit.Dp, compact: Boolean, panelH: androidx.compose.ui.unit.Dp) {
    val trim = sideTrim()
    val caps = Features.sideCaps.state.value
    val scroll = rememberScrollState()
    // Update 151: the panel's dark takes the theme's colour.
    val panel = themeDark(Color(0xFF0A0D14))
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(Brush.horizontalGradient(0f to panel.copy(alpha = 0.95f), 0.85f to panel.copy(alpha = 0.85f), 1f to panel.copy(alpha = 0.70f)))
            .border(width = 1.dp, brush = Brush.verticalGradient(listOf(Color.Transparent, trim.copy(alpha = 0.55f), Color.Transparent)), shape = androidx.compose.ui.graphics.RectangleShape)
    ) {
        // The emblem and the category.
        when (Features.sideEmblem.state.value) {
            Features.SideEmblem.NONE -> Spacer(Modifier.height(8.dp))
            else -> Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val size = if (compact) 40.dp else 54.dp
                Box(
                    Modifier.size(size).clip(CircleShape)
                        // (Update 151: the dark round it from the trim colour too, not always a dark gold.)
                        .background(Brush.radialGradient(listOf(trim.copy(alpha = 0.55f), androidx.compose.ui.graphics.lerp(trim, Color.Black, 0.82f))))
                        .border(2.dp, trim, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val logo = if (Features.sideEmblem.state.value == Features.SideEmblem.CONSOLE) c.stopAt(0)?.logo else null
                    // Your RetroAchievements / Steam picture (as in the status bar), a person icon while there's none.
                    val avatar = if (Features.sideEmblem.state.value == Features.SideEmblem.PROFILE)
                        profilePictureUrl(com.shelf.launcher.data.RaBadges.mediaUrl(c.state.raProfile?.UserPic)) else null
                    // Update 151: tied to the category it stands for, so stepping categories never shows the last one's
                    // logo for a moment before the new one (or a folder's symbol) comes in.
                    // (Your picture stays the same whatever the category, so it isn't reloaded.)
                    androidx.compose.runtime.key(if (avatar != null) null else c.state.specialFilter, if (avatar != null) null else c.state.platformFilter) {
                    when {
                        avatar != null -> AsyncImage(avatar, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                        logo != null -> AsyncImage(logo, null, modifier = Modifier.size(size * 0.62f))
                        Features.sideEmblem.state.value == Features.SideEmblem.PROFILE ->
                            Icon(androidx.compose.material.icons.Icons.Filled.Person, null, tint = trim, modifier = Modifier.size(size * 0.56f))
                        else -> Icon(c.stopAt(0)?.icon ?: androidx.compose.material.icons.Icons.Filled.Apps, null, tint = trim, modifier = Modifier.size(size * 0.5f))
                    }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    CategoryTitle.Native()
                    FitText(
                        c.categoryLabel.let { if (caps) it.uppercase() else it }, color = trim, maxSize = 11.sp, minSize = 7.sp, maxLines = 2,
                        fontWeight = FontWeight.Bold
                    )
                    Text(gamesCount(c.games.size), color = trim.copy(alpha = 0.7f), fontSize = 9.sp, maxLines = 1, softWrap = false)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(trim.copy(alpha = 0.3f)))
        // The menu. Update 136: the buttons get shorter to fit the panel instead of scrolling (a scrolled menu showed
        // its last button cut in half at the bottom); only below 26 dp each does it scroll again.
        val fullItemH = if (compact) 34.dp else 44.dp
        val n = c.shortcuts.size.coerceAtLeast(1)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        val fitH = (maxHeight / n) - 1.dp
        val itemH = minOf(fullItemH, fitH).coerceAtLeast(26.dp)
        val scrolls = (itemH + 1.dp) * n > maxHeight + 0.5.dp
        // Keeps the button you're on in view (one above it showing).
        val itemPx = with(androidx.compose.ui.platform.LocalDensity.current) { (itemH + 1.dp).toPx() }
        LaunchedEffect(StyleNav.zone, StyleNav.index, scrolls) {
            if (scrolls && StyleNav.zone == StyleNav.Zone.SIDE) runCatching { scroll.animateScrollTo(((StyleNav.index - 1).coerceAtLeast(0) * itemPx).toInt()) }
        }
        Column(Modifier.fillMaxSize().then(if (scrolls) Modifier.verticalScroll(scroll) else Modifier)) {
            c.shortcuts.forEachIndexed { i, s ->
                val on = StyleNav.on(StyleNav.Zone.SIDE, i)
                val click by rememberUpdatedState(s.onClick)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(itemH)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .then(
                            if (on) Modifier
                                .background(Brush.horizontalGradient(listOf(trim.copy(alpha = 0.32f), trim.copy(alpha = 0.08f))), uiRounded(3.dp))
                                .border(1.5.dp, trim, uiRounded(3.dp))
                            else Modifier
                        )
                        .pointerInput(i) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.SIDE, i); click() } }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(s.icon, null, tint = if (on) trim else Color.White.copy(alpha = 0.82f), modifier = Modifier.size(if (compact) 16.dp else 19.dp))
                    Spacer(Modifier.width(12.dp))
                    // Shrinks to fit rather than cutting the name off with "…".
                    FitText(
                        if (caps) s.label.uppercase() else s.label, color = if (on) trim else Color.White.copy(alpha = 0.88f),
                        maxSize = if (compact) 10.sp else 12.sp, minSize = 7.sp, maxLines = 1, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 10.dp).background(Color.White.copy(alpha = 0.06f)))
            }
        }
        }
        // The charging capsule, with the game you're on under it. Fixed heights (the name always gets two lines'
        // room, shrinking to fit), so the capsule never jumps when a name is longer.
        val g = c.game
        // Only when every menu entry still fits above it: it used to cut the last entries in half (and sit on them).
        val emblemH = if (Features.sideEmblem.state.value == Features.SideEmblem.NONE) 9.dp else (if (compact) 61.dp else 75.dp)
        val capsuleFits = emblemH + ((if (compact) 34.dp else 44.dp) + 1.dp) * c.shortcuts.size + 160.dp <= panelH
        if (Features.sideOrb.state.value && !compact && g != null && capsuleFits) {
            Box(Modifier.fillMaxWidth().height(104.dp), contentAlignment = Alignment.Center) { SideCapsule(trim) }
            FitText(
                LocalTitleOptions.current.display(g.name).let { if (caps) it.uppercase() else it }, color = trim,
                maxSize = 10.sp, minSize = 7.sp, maxLines = 2, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().height(28.dp).riseOnChange(g.stableKey).padding(horizontal = 10.dp)
            )
            FitText(
                LocalTitleOptions.current.console(g.platform) + " · " + stylePlaytime(c.playtime(g), c.lastPlayed(g) != null), color = trim.copy(alpha = 0.65f),
                maxSize = 8.5.sp, minSize = 6.sp, maxLines = 1, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().height(16.dp).padding(horizontal = 10.dp)
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

/**
 * Voltcap's own emblem: a glass power capsule standing on a ring of light, its charge rising and falling with a
 * gentle wave, a lightning bolt in it and two sparks circling it (still with Lite effects or while idle).
 */
@Composable
private fun SideCapsule(trim: Color) {
    val still = LocalLiteEffects.current || HomeIdle.calm
    val turn = if (still) 0.15f else {
        val t = rememberInfiniteTransition(label = "capsule")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart), label = "capsuleTurn").value
    }
    // Update 151: with the theme's colour as trim (Gold trim off), the charge is the theme's accent too.
    val charge = if (Features.sideGold.state.value) Color(0xFF6FC8FF) else ShelfTheme.accent
    val chargeDeep = androidx.compose.ui.graphics.lerp(charge, Color.Black, 0.5f)
    Canvas(Modifier.size(96.dp)) {
        val u = size.minDimension
        val cx = size.width / 2f
        val bw = u * 0.34f; val bh = u * 0.62f
        val top = size.height * 0.12f
        val left = cx - bw / 2f
        val corner = androidx.compose.ui.geometry.CornerRadius(bw / 2f, bw / 2f)
        // Ring of light under it.
        drawOval(
            Brush.radialGradient(listOf(charge.copy(alpha = 0.45f), Color.Transparent), center = Offset(cx, top + bh + u * 0.06f), radius = u * 0.42f),
            topLeft = Offset(cx - u * 0.42f, top + bh - u * 0.02f), size = Size(u * 0.84f, u * 0.16f)
        )
        drawOval(trim.copy(alpha = 0.5f), topLeft = Offset(cx - u * 0.34f, top + bh + u * 0.005f), size = Size(u * 0.68f, u * 0.1f), style = Stroke(1.2f))
        // The cap (the terminal on top).
        drawRoundRect(trim, topLeft = Offset(cx - bw * 0.22f, top - u * 0.06f), size = Size(bw * 0.44f, u * 0.08f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * 0.02f))
        // Glass body.
        drawRoundRect(Color(0xFF0B1A2C).copy(alpha = 0.92f), topLeft = Offset(left, top), size = Size(bw, bh), cornerRadius = corner)
        // The charge, waving gently, clipped to the body.
        val level = 0.62f + 0.08f * kotlin.math.sin(turn * 2f * Math.PI.toFloat())
        val clip = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, left + bw, top + bh, corner))
        }
        clipPath(clip) {
            val surface = top + bh * (1f - level)
            val wave = androidx.compose.ui.graphics.Path().apply {
                moveTo(left, surface)
                val steps = 12
                for (k in 0..steps) {
                    val x = left + bw * k / steps
                    val y = surface + u * 0.012f * kotlin.math.sin((k / steps.toFloat() + turn) * 2f * Math.PI.toFloat() * 1.5f)
                    lineTo(x, y)
                }
                lineTo(left + bw, top + bh); lineTo(left, top + bh); close()
            }
            drawPath(wave, Brush.verticalGradient(listOf(charge.copy(alpha = 0.95f), chargeDeep), startY = surface, endY = top + bh))
            // Glass shine down the left side.
            drawRect(Color.White.copy(alpha = 0.16f), topLeft = Offset(left + bw * 0.14f, top + bh * 0.1f), size = Size(bw * 0.12f, bh * 0.8f))
        }
        drawRoundRect(trim, topLeft = Offset(left, top), size = Size(bw, bh), cornerRadius = corner, style = Stroke(u * 0.02f))
        // The bolt.
        val b = androidx.compose.ui.graphics.Path().apply {
            val bx = cx; val by = top + bh * 0.5f; val s = bw * 0.62f
            moveTo(bx + s * 0.12f, by - s * 0.62f)
            lineTo(bx - s * 0.32f, by + s * 0.06f)
            lineTo(bx - s * 0.02f, by + s * 0.06f)
            lineTo(bx - s * 0.14f, by + s * 0.62f)
            lineTo(bx + s * 0.32f, by - s * 0.08f)
            lineTo(bx + s * 0.02f, by - s * 0.08f)
            close()
        }
        drawPath(b, Color.White.copy(alpha = 0.95f))
        drawPath(b, trim, style = Stroke(u * 0.008f))
        // Two sparks circling the capsule.
        for (k in 0..1) {
            val a = (turn + k * 0.5f) * 2f * Math.PI.toFloat()
            val sx = cx + kotlin.math.cos(a) * u * 0.36f
            val sy = top + bh * 0.5f + kotlin.math.sin(a) * u * 0.1f
            val front = kotlin.math.sin(a) > 0f
            drawCircle(charge.copy(alpha = if (front) 0.9f else 0.35f), u * (if (front) 0.028f else 0.018f), Offset(sx, sy))
            drawCircle(Color.White.copy(alpha = if (front) 0.8f else 0.25f), u * 0.01f, Offset(sx, sy))
        }
    }
}

/** The row of games along the bottom, the one you're on lifted with a trim-coloured frame. */
@Composable
private fun SideRow(c: StyleCtx, pulse: Float, modifier: Modifier) {
    val trim = sideTrim()
    // Update 125: the row goes round with Endless on (EndlessRow.kt) — the last game slides on to the first.
    val fits = remember { RowFits() }
    val row = rememberLoopRow(c, fits.value)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (row.focusItem - 1).coerceAtLeast(0))
    FollowFocus(list, row)
    val keys = remember(c.games) { gameKeys(c.games) }
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    BoxWithConstraints(modifier) {
        val h = maxHeight - 14.dp
        val w = h * styleAspect(c.type)
        SideEffect { fits.report(maxWidth, w, 10.dp) } // (Update 226, #105)
        val corner = LocalCardLook.current.cornerDp.coerceAtMost(8).dp
        LazyRow(
            state = list, horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
            modifier = Modifier.fillMaxSize().categorySlide()
        ) {
            items(row.count, key = { row.key(it, keys) }) { v ->
                val i = row.game(v)
                val g = c.games[i]
                val here = v == row.focusItem
                val on = here && StyleNav.zone == StyleNav.Zone.GAMES
                val lift = styleLift(on)
                StyleArt(
                    c, g,
                    Modifier
                        .width(w).height(h)
                        .graphicsLayer { val k = 0.92f + 0.08f * lift; scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) }
                        .then(if (here) Modifier.focusedCard() else Modifier)
                        .pointerInput(i) { detectTapGestures { if (i == GridHome.focus) open() else select(i) } },
                    corner = corner, focused = on, fx = pulse, ring = trim, outline = 3.dp
                )
            }
        }
    }
}
