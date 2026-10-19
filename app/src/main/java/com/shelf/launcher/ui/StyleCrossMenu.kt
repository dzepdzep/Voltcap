package com.shelf.launcher.ui

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.NameBackdrop
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Home layout › Cross menu: the categories as icons across the screen, the games as a list going down from
 * the one you're on (the ones before it fade away above the icons), over a colour wash with slow ribbons.
 * ◀ ▶ change the category, ▲ ▼ the game; A opens it as usual. Options: Appearance › Game row › Cross menu options.
 */
@Composable
internal fun StyleCrossMenu(c: StyleCtx, modifier: Modifier) {
    SideEffect {
        StyleNav.side = emptyList()
        StyleNav.bar = emptyList()
        StyleNav.top = emptyList()
        // (Update 234, #14: Endless for the key handler — ▲ on the first game goes round to the last.)
        StyleLoop.on = loopRowOn(c)
    }
    val scale = Features.crossScale.state.value / 100f
    // The colour wash and ribbons are drawn across the screen by StyleBackLayer; the icons and list are kept
    // inside this area (clipped, so a game sliding up never runs into the status bar).
    Box(modifier.clipToBounds()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 380.dp
            val k = scale * if (compact) 0.8f else 1f
            val iconBig = 62.dp * k
            val iconSmall = 42.dp * k
            // Close together like a console's cross menu (it was 104 dp: big gaps, and half an icon cut at each edge).
            val spacing = iconSmall * 1.9f
            val baseX = (maxWidth * 0.2f).coerceAtLeast(iconBig)
            val iconTop = (maxHeight * 0.16f).coerceAtLeast(6.dp)
            val labels = Features.crossLabels.state.value
            val anchor = iconTop + iconBig + (if (labels) 26.dp else 10.dp) * k
            // ◀ ▶: the strip of icons slides the way you went (L2 / R2 and the D-pad both step CategorySlide).
            val slide = remember { Animatable(0f) }
            var seenTick by remember { mutableStateOf(CategorySlide.tick) }
            val lite = LocalLiteEffects.current
            LaunchedEffect(CategorySlide.tick) {
                if (CategorySlide.tick == seenTick) return@LaunchedEffect
                seenTick = CategorySlide.tick
                // Plays with Lite effects too (it only moves the drawing; the Retroid Pocket Classic runs Lite by default).
                @Suppress("UNUSED_EXPRESSION") lite
                slide.snapTo(CategorySlide.dir.toFloat())
                slide.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
            }
            // Update 166: in the very frame the category changes (before the effect above has snapped the slide), the
            // strip is drawn already shifted the way you went — it used to draw one frame with the new category in the
            // old places, so the next console's logos flashed on the folders before the strip slid ("random icons").
            val slideNow = if (CategorySlide.tick != seenTick) CategorySlide.dir.toFloat() else slide.value
            CrossIcons(c, baseX, iconTop, spacing, iconBig, iconSmall, slideNow, labels, k, maxWidth)
            if (c.game == null) {
                Text(
                    "No games match.", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp,
                    modifier = Modifier.offset(x = baseX - iconBig / 2, y = anchor)
                )
            } else {
                // The new category's games glide in from the side you went to and fade up (the icons move with them).
                val s = slideNow
                // Update 112: by default the Cross menu's games fade in (Home › Animations › this layout: Slide brings back
                // the glide from the side, Just change neither).
                MotionPrefs.observe()
                val kind = MotionPrefs.page(com.shelf.launcher.data.HomeLayout.CROSS)
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        translationX = if (kind == com.shelf.launcher.data.Features.PageAnim.SLIDE) s * spacing.toPx() * 1.4f else 0f
                        alpha = if (kind == com.shelf.launcher.data.Features.PageAnim.NONE) 1f else 1f - kotlin.math.abs(s) * (if (kind == com.shelf.launcher.data.Features.PageAnim.FADE) 1f else 0.85f)
                    }
                ) {
                    CrossList(c, baseX - iconBig / 2, iconTop, anchor, k, this@BoxWithConstraints.maxHeight, this@BoxWithConstraints.maxWidth)
                }
            }
        }
    }
}

/** The colour wash and the slow ribbons. */
@Composable
internal fun CrossBackdrop() {
    val tint = Features.crossTint.state.value
    val accent = ShelfTheme.accent
    val (top, bottom) = when (tint) {
        Features.CrossTint.DUSK -> Color(0xFF3B2350) to Color(0xFFB59AD8)
        Features.CrossTint.OCEAN -> Color(0xFF0E2A48) to Color(0xFF5E9BD0)
        Features.CrossTint.EMBER -> Color(0xFF3A1014) to Color(0xFFC0704E)
        Features.CrossTint.THEME -> ShelfTheme.colors.background to accent
        Features.CrossTint.NONE -> Color.Transparent to Color.Transparent
    }
    if (tint != Features.CrossTint.NONE) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top.copy(alpha = 0.92f), bottom.copy(alpha = 0.88f)))))
    }
    if (!Features.crossWaves.state.value) return
    val still = LocalLiteEffects.current || HomeIdle.calm
    // The phase is read while drawing (not here), so the waves repaint each frame without recomposing the screen.
    val phaseState: androidx.compose.runtime.State<Float> = if (still) androidx.compose.runtime.rememberUpdatedState(0.3f) else {
        val t = rememberInfiniteTransition(label = "crossWaves")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart), label = "crossPhase")
    }
    Canvas(Modifier.fillMaxSize()) {
        if (size.width < 1f) return@Canvas // nothing to draw yet (and the loop below would never end)
        val phase = phaseState.value
        val two = (2 * Math.PI).toFloat()
        for (w in 0 until 3) {
            val path = Path()
            val amp = size.height * (0.035f + 0.015f * w)
            val mid = size.height * (0.62f + 0.035f * w)
            val freq = 1.3f + 0.35f * w
            val step = size.width / 48f
            var x = 0f
            path.moveTo(0f, mid)
            while (x <= size.width + step) {
                val y = mid + amp * kotlin.math.sin(x / size.width * two * freq + phase * two * (if (w % 2 == 0) 1f else -1f) + w)
                path.lineTo(x, y)
                x += step
            }
            drawPath(path, Color.White.copy(alpha = 0.22f - 0.05f * w), style = Stroke(width = 2.2f - 0.5f * w))
        }
        drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 0.75f to Color.White.copy(alpha = 0.07f), 1f to Color.Transparent))
    }
}

/** The category icons across the screen (the one you're in big, with its name). */
@Composable
private fun CrossIcons(c: StyleCtx, baseX: Dp, top: Dp, spacing: Dp, big: Dp, small: Dp, slide: Float, labels: Boolean, k: Float, width: Dp) {
    val move by rememberUpdatedState(c.actions.category)
    val n = c.stops.size
    // (Update 212: the layout's own category title — its name, or the icons' labels.)
    if (labels || n == 0 || c.stop < 0) CategoryTitle.Native()
    if (n == 0 || c.stop < 0) {
        // Not in the cycle (e.g. Rediscover): just its name.
        Text(
            c.categoryLabel, color = Color.White, fontSize = 15.sp * k, fontWeight = FontWeight.SemiBold, style = nameTextStyle(NameBackdrop.SHADOW),
            modifier = Modifier.offset(x = baseX - big / 2, y = top + big / 3)
        )
        return
    }
    // While Voltcap starts, the category list is still filling in (consoles as the library loads, the collections a
    // moment later), so the icon next to yours could show one category (e.g. Almost mastered) and then switch to another
    // (a collection's folder). The strip waits until the list has stayed the same for a moment, then fades in once;
    // after that (or coming back to this layout) it shows straight away.
    val sig = c.stops.joinToString("|") { "${it.special}:${it.platform}" }
    var ready by remember { mutableStateOf(CrossSettle.done) }
    // Update 130: the folders' logos decoded ahead, so a folder sliding in has its logo at once.
    val logoContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(sig, Features.crossFolders.state.value) {
        if (Features.crossFolders.value) PlatformLogos.prefetch(logoContext, c.stops.filter { crossFolderStop(it) }.mapNotNull { it.platform })
    }
    LaunchedEffect(sig) {
        if (ready) return@LaunchedEffect
        // Update 130: Last played and Apps are there at once, the consoles' folders only once the library has loaded —
        // so the strip showed those first and the folders popped in after. It now also waits for the first console
        // (at most 2.5 s, for a library with none).
        val consoles = c.stops.any { it.special == null && it.platform != null && !com.shelf.launcher.data.isCollectionKey(it.platform) }
        kotlinx.coroutines.delay(if (consoles) 450 else 2500)
        CrossSettle.done = true
        ready = true
    }
    // Update 104: nothing fades in any more. The icon you're on and its name are there straight away; only at
    // Voltcap's first start do the ones beside it wait (without a fade) for the list to settle.
    val stripAlpha = 1f
    // From one before to five after (fewer when there are fewer categories), never the same one twice.
    val range = (-minOf(2, (n - 1) / 2))..minOf(5, n - 1 - minOf(2, (n - 1) / 2))
    // (Update 246, #60: the stops picked before the loop — a `continue` in it gave each pass its own group, so the icons
    // weren't kept by their category as the strip slid but made again.)
    val stops = range.mapNotNull { d -> c.stopAt(d)?.let { d to it } }
    for ((d, stop) in stops) {
        // Each icon keeps its own state as the strip slides (keyed by its category, not its place), so a slot
        // never shows the previous category's logo or icon for a frame before the new one.
        androidx.compose.runtime.key(stop.special, stop.platform) {
        val pos = d + slide
        val near = (1f - kotlin.math.abs(pos)).coerceIn(0f, 1f)
        val size = lerp(small, big, near)
        val x = baseX + spacing * pos - size / 2
        val y = top + (big - size) / 2
        // Never half an icon at an edge: an icon fades out as it reaches the edge and isn't drawn past it.
        val edge = minOf(x, width - (x + size))
        if (edge > 0.dp) {
            val edgeFade = (edge / 18.dp).coerceIn(0f, 1f)
            Box(
                Modifier
                    .offset(x = x, y = y)
                    .size(size)
                    .alpha((if (pos < -0.5f) 0.45f else 0.55f + 0.45f * near) * edgeFade * (if (ready || d == 0) stripAlpha else 0f))
                    .pointerInput(d) { detectTapGestures { if (d != 0) move(d) } },
                contentAlignment = Alignment.Center
            ) {
                CrossIconTile(stop, size)
            }
        }
        }
        // Update 122: a folder shows only its console's logo, no name under it (a console without a logo has its
        // name on the folder itself).
        if (d == 0 && labels && !crossFolderStop(stop)) {
            Text(
                stop.label, color = Color.White, fontSize = 13.sp * k, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp * k), maxFontSize = 13.sp * k), fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = nameTextStyle(NameBackdrop.SHADOW),
                modifier = Modifier.offset(x = baseX + spacing * slide - 70.dp, y = top + big + 4.dp).width(140.dp).alpha(stripAlpha)
            )
        }
    }
}

/**
 * A console's category as a plain white folder (update 104): a back with its tab, a slightly lighter front, a soft
 * shadow under it. Drawn, not loaded, so it is there from the first frame.
 */
@Composable
private fun CrossFolder(size: Dp) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val w = this.size.width
        val h = this.size.height
        val left = w * 0.08f
        val right = w * 0.92f
        val top = h * 0.2f
        val bottom = h * 0.8f
        val r = androidx.compose.ui.geometry.CornerRadius(w * 0.05f)
        // Shadow.
        drawRoundRect(Color.Black.copy(alpha = 0.28f), androidx.compose.ui.geometry.Offset(left + w * 0.02f, top + h * 0.05f),
            androidx.compose.ui.geometry.Size(right - left, bottom - top), r)
        // The back with its tab.
        val back = androidx.compose.ui.graphics.Path().apply {
            moveTo(left, top + h * 0.04f)
            lineTo(left, bottom)
            lineTo(right, bottom)
            lineTo(right, top + h * 0.1f)
            lineTo(left + w * 0.4f, top + h * 0.1f)
            lineTo(left + w * 0.33f, top)
            lineTo(left + w * 0.04f, top)
            close()
        }
        drawPath(back, Color(0xFFD9DDE3))
        // The front.
        drawRoundRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White, Color(0xFFE9ECF0)), top + h * 0.18f, bottom),
            androidx.compose.ui.geometry.Offset(left, top + h * 0.18f), androidx.compose.ui.geometry.Size(right - left, bottom - top - h * 0.18f), r
        )
    }
}

/** Whether [stop] is drawn as a folder (Consoles as folders: a console, never a list or a collection). */
private fun crossFolderStop(stop: StyleStop): Boolean =
    Features.crossFolders.state.value && stop.special == null && stop.platform != null && !com.shelf.launcher.data.isCollectionKey(stop.platform)

/** Whether the category strip has settled once since Voltcap started (see CrossIcons). */
private object CrossSettle {
    @Volatile var done = false
    @Volatile var faded = false
}

/** The games under the icons: the one you're on big with its details, the next ones under it, the ones before above. */
@Composable
private fun CrossList(c: StyleCtx, left: Dp, iconTop: Dp, anchor: Dp, k: Float, height: Dp, width: Dp) {
    // Update 125: with Endless on the list goes round — from the last game it carries on down to the first (EndlessRow.kt).
    val loop = loopRowOn(c)
    val vf = rememberLoopFocus(c)
    val pos by animateFloatAsState(vf.toFloat(), tween(160), label = "crossFocus")
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val titles = LocalTitleOptions.current
    val thumbBig = 150.dp * k
    val thumbSmall = 96.dp * k
    val focusedH = thumbBig * 0.58f + 18.dp * k
    val itemH = thumbSmall * 0.58f + 14.dp * k
    val aboveStart = iconTop - 10.dp - itemH
    val info = Features.crossInfo.state.value
    // Update 132: the Trophies category shows how far you are instead.
    val trophies = c.stopAt(0)?.special == com.shelf.launcher.SpecialFilter.TROPHIES
    val needAchievements by rememberUpdatedState(c.actions.needAchievements)
    val focusedGame = c.game
    LaunchedEffect(trophies, focusedGame?.stableKey) {
        val fg = focusedGame ?: return@LaunchedEffect
        if (trophies) { kotlinx.coroutines.delay(350); needAchievements(fg) }
    }
    val from = if (loop) vf - 4 else (c.focus - 4).coerceAtLeast(0)
    val to = if (loop) vf + 9 else (c.focus + 9).coerceAtMost(c.games.lastIndex)
    // (Update 174: each game once in a short list — the ones before above only when there are enough.)
    // (Update 226, #105: the games above yours aren't drawn once it has settled — rel > -0.7 below — so only the one
    // just left counts as "before"; with four, up to four games of a short list were missing from the list below.)
    for (v in from..to) if (!loop || drawnOnce(v - vf, c.games.size, minOf(1, (c.games.size - 1) / 2))) androidx.compose.runtime.key(v) {
        val i = if (loop) v.mod(c.games.size) else v
        val g = c.games[i]
        val rel = v - pos
        val y = when {
            rel >= 1f -> anchor + focusedH + itemH * (rel - 1f)
            rel >= 0f -> anchor + focusedH * rel
            rel >= -1f -> lerp(anchor, aboveStart, -rel)
            else -> aboveStart - itemH * (-rel - 1f)
        }
        // (Update 127: drawn only when on screen. A "return@key" out of the inline key { } broke the Android build:
        // D8 "Method name '<anonymous>' in class '$$$$$NON_LOCAL_RETURN$$$$$' cannot be represented in dex format".)
        // (Update 166: no games above the icons any more — the one you leave going down fades out on its way up.)
        if (y <= height && y >= -itemH && rel > -0.7f) {
            val near = (1f - kotlin.math.abs(rel)).coerceIn(0f, 1f)
            val w = lerp(thumbSmall, thumbBig, near)
            val on = v == vf
            val x = left + (thumbBig - w) * 0.5f
            val gapX = 16.dp * k + (thumbBig - w) * 0.5f
            // The name never runs off the right edge (it ends in "…" there instead).
            val textMax = (width - x - w - gapX - 12.dp).coerceIn(40.dp, 420.dp)
            Row(
                Modifier
                    .offset(x = x, y = y)
                    .alpha(if (rel < 0f) (1f + rel * 1.45f).coerceIn(0f, 1f) else 1f)
                    .pointerInput(i) { detectTapGestures { if (i == GridHome.focus) open() else select(i) } },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // (Update 174: the Tile look as square tiles of the same height — the list always showed wide art.)
                val tile = c.type == com.shelf.launcher.data.CardArtType.COVER && carouselTiles()
                StyleArt(
                    c, g,
                    Modifier.width(if (tile) w * 0.58f else w).height(w * 0.58f).then(if (on) Modifier.focusedCard() else Modifier),
                    square = tile, wide = !tile, corner = if (tile) 8.dp else 4.dp, focused = on, fx = 0.4f, ring = Color.White, outline = 1.5.dp
                )
                Spacer(Modifier.width(gapX))
                Column(Modifier.widthIn(max = textMax)) {
                    Text(
                        titles.display(g.name), color = Color.White.copy(alpha = if (on) 1f else 0.82f),
                        fontSize = (if (on) 19.sp else 15.sp) * k, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin((if (on) 19.sp else 15.sp) * k), maxFontSize = (if (on) 19.sp else 15.sp) * k), fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = nameTextStyle(NameBackdrop.SHADOW)
                    )
                    // Update 132, Trophies category: how far you are, with a bar, under the game you're on.
                    val trophyRow = on && trophies
                    if (trophyRow) Column(Modifier.riseOnEnter()) {
                        val p = c.progress(g)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            p?.let { (e, t) -> "$e / $t achievements · ${(e * 100) / t} %" } ?: "Achievements loading…",
                            color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp * k, maxLines = 1, overflow = TextOverflow.Ellipsis // (Update 234, #14: ends in "…")
                        )
                        if (p != null) {
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.width(140.dp * k).height(3.dp).clip(uiRounded(2.dp)).background(Color.White.copy(alpha = 0.2f))) {
                                Box(Modifier.fillMaxWidth(p.first.toFloat() / p.second).height(3.dp).background(ShelfTheme.accent))
                            }
                        }
                        Text("A: its achievements", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp * k, maxLines = 1, overflow = TextOverflow.Ellipsis) // (Update 234, #14: ends in "…")
                    }
                    if (on && info && !trophyRow) Column(Modifier.riseOnEnter()) {
                        Spacer(Modifier.height(2.dp))
                        Text(stylePlaytime(c.playtime(g), c.lastPlayed(g) != null), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp * k, maxLines = 1, overflow = TextOverflow.Ellipsis) // (Update 234, #14: ends in "…")
                        Text(
                            titles.console(g.platform) + (c.lastPlayed(g)?.let { " · Last played: " + lastPlayedLabel(it) } ?: ""),
                            color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp * k, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp * k), maxFontSize = 11.sp * k), maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}


/**
 * One category icon (update 90): a rounded glass tile with the console's logo fitted inside it (wide logos never reach
 * past it), or the category's symbol; consoles as plain white folders by default (update 104). Nothing fades in.
 * "Tiles in the accent colour" colours the tile, never the logo.
 */
@Composable
internal fun CrossIconTile(stop: StyleStop, size: Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(size * 0.22f)
    // "Tiles in the accent colour": the tile behind the logo takes the accent colour; the logo keeps its own colours.
    val tileTint = if (Features.crossLogoTint.state.value) ShelfTheme.accent else null
    // Cut-out logos: no tile, the logo's own shape in one colour (white, or the accent colour).
    val folder = crossFolderStop(stop)
    val cut = Features.crossLogoCut.state.value || folder
    Box(
        (if (cut) Modifier.fillMaxSize().padding(size * 0.06f)
        else Modifier.fillMaxSize().clip(shape).background(tileTint?.copy(alpha = 0.55f) ?: Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape).padding(size * 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        // Consoles as plain white folders (update 104, the default), drawn at once (no picture to load).
        // (Plain if / else, never an early return@Box: it unbalances Compose's groups.)
        if (folder) {
            CrossFolder(size)
            // Update 112: the console's logo on the folder's front (its own colours; the folder alone until it loads).
            // Update 122: the redrawn logos — the emblem alone where the console has one, no extra text (PlatformLogos.kt).
            PlatformLogo(
                stop.platform!!, stop.label, blackToWhite = false, textColor = Color(0xFF2E3640), textSize = 11.sp,
                // (Update 125: about 30 % smaller.)
                modifier = Modifier.fillMaxWidth(0.44f).fillMaxHeight(0.3f).offset(y = size * 0.09f)
            )
        } else {
        // A logo that doesn't load falls back to the icon (it used to stay blank).
        var failed by androidx.compose.runtime.remember(stop.logo) { androidx.compose.runtime.mutableStateOf(false) }
        val logo = stop.logo.takeIf { !failed }
        // Update 166: the console logos baked into the app, drawn at once (no SVG decoding, no popping in).
        val baked = androidx.compose.runtime.remember(logo) { logo?.let { PlatformLogos.bakedForUrl(context, it) } }
        if (baked != null) {
            androidx.compose.foundation.Image(
                baked, stop.label, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                colorFilter = if (cut) androidx.compose.ui.graphics.ColorFilter.tint(tileTint ?: Color.White) else null,
                modifier = Modifier.fillMaxSize(0.7f)
            )
        } else if (logo != null) {
            val request = androidx.compose.runtime.remember(logo) {
                coil.request.ImageRequest.Builder(context).data(logo)
                    .apply { if (logo.endsWith(".svg")) decoderFactory(coil.decode.SvgDecoder.Factory()) }
                    .size(256).build()
            }
            AsyncImage(
                request, stop.label, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                onError = { failed = true },
                colorFilter = if (cut) androidx.compose.ui.graphics.ColorFilter.tint(tileTint ?: Color.White) else null,
                // (Update 125: the logos about 30 % smaller in their tile.)
                modifier = Modifier.fillMaxSize(0.7f)
            )
        } else Icon(stop.icon, stop.label, tint = if (cut) tileTint ?: Color.White else Color.White, modifier = Modifier.fillMaxSize())
        }
    }
}

