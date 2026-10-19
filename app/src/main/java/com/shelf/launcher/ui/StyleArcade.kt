package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.NameBackdrop
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.roundToInt

/*
 * Arcade cabinets (update 146): the game you're on stands as a big upright arcade cabinet on the left — drawn in plain
 * shapes (side panels with a coloured edge strip, a sloped control deck with a stick and buttons, a coin door and a
 * kick plate at the bottom). Its screen shows the game's backdrop (or screenshot / wide art) behind curved glass with
 * scan lines; the marquee on top is lit with the game's logo, or its name in bold letters. The cabinets take the colour
 * of the game's art (or the theme's).
 * To the right: the game's name, console, time played and achievements, then Play and Details as round arcade buttons;
 * along the bottom a row of small cabinets (their covers on the tiny screens) — the one you're on lit up — which slides
 * along as you move.
 * ◀ ▶ previous / next game (the home screen's own), ▼ Play and Details (◀ ▶ between them, ▲ / B back), ▲ what lies above.
 * Touch: tap a small cabinet to pick it, tap it again (or the big screen) to open the game; swipe the row to move along.
 */

/** Width / height of a cabinet (an upright cabinet is about twice as tall as it is wide). */
private const val CAB_ASPECT = 0.52f

/** Where the parts sit on a cabinet, as shares of its width (x / w) and height (y / h). */
private object Cab {
    // The lit marquee along the top.
    const val MX = 0.12f; const val MY = 0.035f; const val MW = 0.76f; const val MH = 0.115f
    // The screen (inside its black bezel).
    const val SX = 0.15f; const val SY = 0.2f; const val SW = 0.70f; const val SH = 0.30f
    // The control deck: its back edge, its front edge and the bottom of the lip.
    const val DECK_TOP = 0.555f; const val DECK_FRONT = 0.625f; const val LIP = 0.66f
    // Bottom of the front, the kick plate.
    const val BODY_END = 0.935f; const val KICK_END = 0.975f
}

@Composable
internal fun StyleArcade(c: StyleCtx, modifier: Modifier = Modifier) {
    val g = c.game
    // A part hidden in Edit Home is skipped by the D-pad (Play and Details live in the info part).
    val infoOff = LayoutParts.off(HomeLayout.ARCADE, "info")
    SideEffect {
        StyleNav.side = emptyList()
        StyleNav.top = emptyList()
        // ▼: Play, then Details (the game window / details).
        StyleNav.bar = if (g == null || infoOff) emptyList() else listOf({ c.actions.play(g) }, { c.actions.open() })
    }
    // Plain if / else, never an early return out of an inline composable lambda.
    if (g == null) StyleEmpty(modifier) else ArcadeHall(c, g, modifier)
}

/** ▼ goes to Play (always on Play, not where it was left), ▲ to what lies above; ◀ ▶ are the home screen's own. */
internal fun arcadeKey(ctx: StyleKeyCtx): Boolean = when (ctx.key) {
    Key.DirectionDown -> if (StyleNav.bar.isNotEmpty()) {
        StyleNav.forget(StyleNav.Zone.BAR)
        ctx.enterZone(StyleNav.Zone.BAR)
    } else ctx.reach(false)
    Key.DirectionUp -> ctx.reach(true)
    else -> false
}

@Composable
internal fun ArcadeSettings() {
    MutedNote("The game you're on stands as a big arcade cabinet: its picture on the screen, its logo (or name) on the lit marquee on top, its name, time played and achievements beside it. The other games stand along the bottom as a row of small cabinets. ◀ ▶ move along the row, ▼ goes to Play and Details. Tap a small cabinet to pick it; tap it again, or the big screen, to open the game. The R1 Size tile makes the cabinets bigger or smaller.")
    Spacer(Modifier.height(10.dp))
    SwitchRow(
        Icons.Filled.WbSunny, "Marquee glow", "The sign on top of the cabinet lit up, its light spilling over the top. Off: an unlit sign.",
        Features.arcadeGlow.state.value, { Features.arcadeGlow.value = it }
    )
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.Waves, "Scan lines", "Fine lines over the cabinet's screen, like an old tube screen. Off: a clean picture.",
        Features.arcadeLines.state.value, { Features.arcadeLines.value = it }
    )
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.Style, "Cabinets in the game's colours", "Each cabinet painted in its game's own colours (from its art). Off: your theme's colours.",
        Features.arcadeGameColors.state.value, { Features.arcadeGameColors.value = it }
    )
    Spacer(Modifier.height(8.dp))
}

/** The cabinet's paint: its body and the bright edge strip / marquee light. Animated, read only while drawing. */
private class ArcadeInk(val body: State<Color>, val trim: State<Color>, val trimNow: Color)

/** A bright, rich version of [c] for the edge strips and the marquee light. */
private fun brighten(c: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    hsv[1] = hsv[1].coerceIn(0.5f, 0.85f)
    hsv[2] = 0.96f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** The big cabinet's colours: from the game's art (a tiny decoded copy of its cover), else the theme's. */
@Composable
private fun rememberArcadeInk(c: StyleCtx, g: Game): ArcadeInk {
    val own = Features.arcadeGameColors.state.value
    val model = if (own) (c.cover(g) ?: c.wide(g)) else null
    val bmp = rememberSoftBitmap(model, 64)
    val art = rememberArtColor(bmp, ShelfTheme.accent)
    val themeBody = lerp(ShelfTheme.colors.surfaceRaised, ShelfTheme.accent, 0.28f)
    val themeTrim = ShelfTheme.accent
    // While the next game's art is still decoding the cabinet keeps the last game's paint (it used to flash the theme's
    // colour in between); a plain holder, not state, so writing it here costs nothing.
    // Update 146: a game with no art at all takes the theme's paint (it used to keep the previous game's for good).
    val held = remember { arrayOfNulls<Color>(1) }
    if (model == null) held[0] = null else if (bmp != null) held[0] = art
    val gameBody = held[0]
    val bodyTarget = if (own && gameBody != null) gameBody else themeBody
    val bright = remember(bodyTarget) { brighten(bodyTarget) }
    val trimTarget = if (own && gameBody != null) bright else themeTrim
    val ms = Features.rowGlide.state.value.carouselMs
    val body = animateColorAsState(bodyTarget, tween(ms * 2), label = "arcadeBody")
    val trim = animateColorAsState(trimTarget, tween(ms * 2), label = "arcadeTrim")
    return ArcadeInk(body, trim, trimTarget)
}

/** The whole layout: the big cabinet on the left, the game's info at the right, the row of small cabinets under it. */
@Composable
private fun ArcadeHall(c: StyleCtx, g: Game, modifier: Modifier) {
    val ink = rememberArcadeInk(c, g)
    BoxWithConstraints(modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
        val compact = maxHeight < 380.dp
        val k = GameSize.scale(HomeLayout.ARCADE)
        // The big cabinet: as tall as the screen allows (a little room on top for the marquee's light), never wider than
        // a bit under half the width, so the info and the row always have room (small, square-ish screens too).
        var cabH = maxHeight * 0.95f * k.coerceIn(0.7f, 1f)
        var cabW = cabH * CAB_ASPECT
        if (cabW > maxWidth * 0.42f) {
            cabW = maxWidth * 0.42f
            cabH = cabW / CAB_ASPECT
        }
        val gap = if (compact) 14.dp else 26.dp
        val rightX = cabW + gap
        val rightW = (maxWidth - rightX).coerceAtLeast(120.dp)
        val miniH = (maxHeight * 0.34f * k).coerceIn(64.dp, 190.dp)
        val miniW = miniH * CAB_ASPECT
        // Room for the focused small cabinet's lift and the floor under the row.
        val rowH = miniH * 1.12f + 10.dp
        val infoH = (maxHeight - rowH - 6.dp).coerceAtLeast(80.dp)

        ArcadeAmbience(ink, cabW, cabH, maxHeight, Modifier.fillMaxSize())
        Box(
            Modifier.offset(y = maxHeight - cabH).categorySlide()
                .editablePart(LayoutParts.id(HomeLayout.ARCADE, "cabinet"), "Big cabinet")
        ) { BigCabinet(c, g, ink, cabW, cabH, compact) }
        Column(
            Modifier.offset(x = rightX).width(rightW).height(infoH)
                .editablePart(LayoutParts.id(HomeLayout.ARCADE, "info"), "Name, Play & Details")
        ) {
            ArcadeCategory(c, compact)
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            AnimatedContent(
                targetState = g, label = "arcadeInfo", transitionSpec = { gameSwap() },
                contentAlignment = Alignment.TopStart,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { shown -> ArcadeInfo(c, shown, ink.trimNow, compact) }
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            ArcadeButtons(c, g, ink.trimNow, compact)
        }
        ArcadeRow(
            c, ink, miniW, miniH,
            Modifier.offset(x = rightX, y = maxHeight - rowH).width(rightW).height(rowH)
                .editablePart(LayoutParts.id(HomeLayout.ARCADE, "row"), "Row of cabinets")
        )
    }
}

/** The arcade's light: the big cabinet's colour glowing faintly on the wall behind it, and a darker floor. Static. */
@Composable
private fun ArcadeAmbience(ink: ArcadeInk, cabW: Dp, cabH: Dp, height: Dp, modifier: Modifier) {
    Canvas(modifier) {
        val trim = ink.trim.value
        val top = (height - cabH).toPx()
        drawRect(
            Brush.radialGradient(
                listOf(trim.copy(alpha = 0.2f), trim.copy(alpha = 0.06f), Color.Transparent),
                center = Offset(cabW.toPx() / 2f, top + cabH.toPx() * 0.3f), radius = cabW.toPx() * 1.25f
            )
        )
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f)), startY = size.height * 0.86f, endY = size.height),
            topLeft = Offset(0f, size.height * 0.86f), size = Size(size.width, size.height * 0.14f)
        )
    }
}

/**
 * A cabinet drawn in plain shapes, filling the draw area: side panels with the [trim] strip, the front in [body], the
 * marquee lit in [marquee] (with its light spilling over the top when [glow]), the black bezel, the control deck with a
 * stick and buttons, the coin door and the kick plate. [detail]: the big one's extra bits (more buttons, coin slots).
 */
private fun DrawScope.drawCabinet(body: Color, trim: Color, marquee: Color, lit: Boolean, glow: Boolean, detail: Boolean) {
    val w = size.width
    val h = size.height
    val dark = lerp(body, Color.Black, 0.45f)
    val darker = lerp(body, Color.Black, 0.72f)
    val light = lerp(body, Color.White, 0.16f)
    val side = w * 0.08f
    val t = maxOf(1f, w * 0.012f)

    // The shadow on the floor.
    drawOval(Color.Black.copy(alpha = 0.38f), topLeft = Offset(-w * 0.05f, h * 0.955f), size = Size(w * 1.1f, h * 0.05f))
    // The marquee's light spilling over the top of the cabinet.
    if (glow && lit) {
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, marquee.copy(alpha = 0.34f)), startY = -h * 0.06f, endY = h * 0.03f),
            topLeft = Offset(w * 0.06f, -h * 0.06f), size = Size(w * 0.88f, h * 0.09f)
        )
    }
    // The side panels, with the bright strip along their front edges.
    drawRoundRect(dark, topLeft = Offset(0f, 0f), size = Size(side, h * Cab.KICK_END), cornerRadius = CornerRadius(side * 0.4f))
    drawRoundRect(dark, topLeft = Offset(w - side, 0f), size = Size(side, h * Cab.KICK_END), cornerRadius = CornerRadius(side * 0.4f))
    drawRect(trim, topLeft = Offset(side - t, h * 0.01f), size = Size(t, h * (Cab.KICK_END - 0.02f)))
    drawRect(trim, topLeft = Offset(w - side, h * 0.01f), size = Size(t, h * (Cab.KICK_END - 0.02f)))
    // The front.
    drawRect(
        Brush.verticalGradient(listOf(light, body, dark), startY = 0f, endY = h),
        topLeft = Offset(side, h * 0.015f), size = Size(w - 2 * side, h * (Cab.BODY_END - 0.015f))
    )
    // The marquee's housing, then the sign itself: lit from behind (bright in the middle), or dark when it's off.
    drawRect(darker, topLeft = Offset(side, h * 0.015f), size = Size(w - 2 * side, h * 0.15f))
    val mTop = Offset(w * Cab.MX, h * Cab.MY)
    val mSize = Size(w * Cab.MW, h * Cab.MH)
    if (glow && lit) {
        // A soft halo round the sign.
        drawRoundRect(
            marquee.copy(alpha = 0.22f), topLeft = mTop - Offset(w * 0.03f, h * 0.012f),
            size = Size(mSize.width + w * 0.06f, mSize.height + h * 0.024f), cornerRadius = CornerRadius(w * 0.04f)
        )
    }
    if (lit) {
        drawRoundRect(
            Brush.radialGradient(
                listOf(lerp(marquee, Color.White, 0.32f), marquee, lerp(marquee, Color.Black, 0.5f)),
                center = mTop + Offset(mSize.width / 2f, mSize.height / 2f), radius = mSize.width * 0.62f
            ),
            topLeft = mTop, size = mSize, cornerRadius = CornerRadius(w * 0.015f)
        )
    } else {
        drawRoundRect(lerp(marquee, Color.Black, 0.62f), topLeft = mTop, size = mSize, cornerRadius = CornerRadius(w * 0.015f))
    }
    // The black bezel round the screen, its inner edge catching a little light.
    val bezelTop = Offset(w * 0.10f, h * 0.175f)
    val bezelSize = Size(w * 0.80f, h * 0.35f)
    drawRoundRect(Color(0xFF07080B), topLeft = bezelTop, size = bezelSize, cornerRadius = CornerRadius(w * 0.025f))
    drawRoundRect(
        Color.White.copy(alpha = 0.07f), topLeft = Offset(w * (Cab.SX - 0.018f), h * (Cab.SY - 0.012f)),
        size = Size(w * (Cab.SW + 0.036f), h * (Cab.SH + 0.024f)), cornerRadius = CornerRadius(w * 0.05f)
    )
    drawRoundRect(
        Color(0xFF020304), topLeft = Offset(w * Cab.SX, h * Cab.SY), size = Size(w * Cab.SW, h * Cab.SH),
        cornerRadius = CornerRadius(w * 0.05f)
    )
    // The control deck: sloping out towards you, wider than the cabinet, with its lip and the strip along its edge.
    val deckTop = h * Cab.DECK_TOP
    val deckFront = h * Cab.DECK_FRONT
    val lip = h * Cab.LIP
    val deck = Path().apply {
        moveTo(side * 0.6f, deckTop)
        lineTo(w - side * 0.6f, deckTop)
        lineTo(w * 1.03f, deckFront)
        lineTo(-w * 0.03f, deckFront)
        close()
    }
    drawPath(deck, Brush.verticalGradient(listOf(darker, lerp(body, Color.Black, 0.35f)), startY = deckTop, endY = deckFront))
    drawRect(lerp(body, Color.Black, 0.55f), topLeft = Offset(-w * 0.03f, deckFront), size = Size(w * 1.06f, lip - deckFront))
    drawRect(trim, topLeft = Offset(-w * 0.03f, deckFront - t / 2f), size = Size(w * 1.06f, t))
    // The stick: its base, the shaft and the ball on top.
    val sx = w * 0.27f
    val baseY = deckTop + (deckFront - deckTop) * 0.62f
    drawOval(Color.Black.copy(alpha = 0.55f), topLeft = Offset(sx - w * 0.055f, baseY - h * 0.008f), size = Size(w * 0.11f, h * 0.018f))
    drawLine(Color(0xFF2B2C31), Offset(sx, baseY), Offset(sx, deckTop - h * 0.012f), strokeWidth = w * 0.022f, cap = StrokeCap.Round)
    val ballR = w * 0.042f
    val ball = Offset(sx, deckTop - h * 0.016f)
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFF8A80), Color(0xFFE53935), Color(0xFF8E1C1A)), center = ball - Offset(ballR * 0.35f, ballR * 0.35f), radius = ballR * 1.3f),
        radius = ballR, center = ball
    )
    // The buttons: two rows of three on the big cabinet, two on the small ones.
    val colors = listOf(trim, Color(0xFFF2C14E), Color(0xFF4FC3F7), Color(0xFFEF5350), Color(0xFF66BB6A), Color(0xFFECEFF1))
    val br = w * (if (detail) 0.028f else 0.04f)
    val rows = if (detail) 2 else 1
    val cols = if (detail) 3 else 2
    for (r in 0 until rows) {
        for (col in 0 until cols) {
            val cx = w * (0.5f + col * (if (detail) 0.12f else 0.18f)) + r * w * 0.03f
            val cy = deckTop + (deckFront - deckTop) * (if (rows == 1) 0.45f else 0.3f + r * 0.42f)
            val bc = colors[(r * cols + col) % colors.size]
            drawCircle(Color.Black.copy(alpha = 0.5f), radius = br * 1.25f, center = Offset(cx, cy + br * 0.25f))
            drawCircle(bc, radius = br, center = Offset(cx, cy))
            drawCircle(Color.White.copy(alpha = 0.35f), radius = br * 0.35f, center = Offset(cx - br * 0.3f, cy - br * 0.3f))
        }
    }
    // The coin door: a metal plate with two lit coin slots (and a lock on the big cabinet).
    val doorTop = Offset(w * 0.34f, h * 0.70f)
    val doorSize = Size(w * 0.32f, h * 0.14f)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF55595F), Color(0xFF2E3136)), startY = doorTop.y, endY = doorTop.y + doorSize.height),
        topLeft = doorTop, size = doorSize, cornerRadius = CornerRadius(w * 0.012f)
    )
    for (n in 0 until 2) {
        val slotX = doorTop.x + doorSize.width * (0.22f + n * 0.4f)
        val slotY = doorTop.y + doorSize.height * 0.2f
        drawRoundRect(Color(0xFFFF8A3D), topLeft = Offset(slotX, slotY), size = Size(doorSize.width * 0.16f, doorSize.height * 0.24f), cornerRadius = CornerRadius(w * 0.006f))
        drawRect(Color(0xFF1A0E06), topLeft = Offset(slotX + doorSize.width * 0.07f, slotY + doorSize.height * 0.04f), size = Size(maxOf(1f, doorSize.width * 0.02f), doorSize.height * 0.16f))
    }
    if (detail) drawCircle(Color(0xFF1B1D21), radius = w * 0.012f, center = Offset(doorTop.x + doorSize.width / 2f, doorTop.y + doorSize.height * 0.72f))
    // The kick plate along the bottom.
    drawRect(Color(0xFF111215), topLeft = Offset(side * 0.6f, h * Cab.BODY_END), size = Size(w - side * 1.2f, h * (Cab.KICK_END - Cab.BODY_END)))
    drawRect(Color.White.copy(alpha = 0.08f), topLeft = Offset(side * 0.6f, h * Cab.BODY_END), size = Size(w - side * 1.2f, maxOf(1f, h * 0.004f)))
}

/** The big cabinet: the game's picture on its screen behind curved glass, its logo or name on the lit marquee. */
@Composable
private fun BigCabinet(c: StyleCtx, g: Game, ink: ArcadeInk, w: Dp, h: Dp, compact: Boolean) {
    val glow = Features.arcadeGlow.state.value
    val lines = Features.arcadeLines.state.value
    val ms = Features.rowGlide.state.value.carouselMs
    Box(Modifier.size(w, h)) {
        // The colours are read while drawing only, so the cabinet's paint changing doesn't recompose anything.
        Canvas(Modifier.fillMaxSize()) { drawCabinet(ink.body.value, ink.trim.value, ink.trim.value, lit = glow, glow = glow, detail = true) }
        // The marquee's sign: the game's logo, or its name.
        Box(
            Modifier.offset(x = w * Cab.MX, y = h * Cab.MY).size(w * Cab.MW, h * Cab.MH)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = g, animationSpec = tween(ms), label = "arcadeMarquee") { shown -> ArcadeMarquee(c, shown, glow, compact) }
        }
        ArcadeBigScreen(c, g, ink, w, h, lines, ms)
    }
}

/**
 * The big cabinet's screen. Tapping it opens the game (it's always the one you're on); the game window grows out of it.
 * While the D-pad is on the games it glows with the layout's focus effect and has an outline in the cabinet's colour.
 */
@Composable
private fun ArcadeBigScreen(c: StyleCtx, g: Game, ink: ArcadeInk, w: Dp, h: Dp, lines: Boolean, ms: Int) {
    val sel = StyleNav.zone == StyleNav.Zone.GAMES
    val open by rememberUpdatedState(c.actions.open)
    val screenCorner = (w * Cab.SW * 0.07f).coerceIn(4.dp, 18.dp)
    Box(Modifier.offset(x = w * Cab.SX, y = h * Cab.SY).size(w * Cab.SW, h * Cab.SH)) {
        if (sel) ArcadeFocusGlow(screenCorner, ink.trimNow, 2.dp, Modifier.matchParentSize())
        Box(
            Modifier.matchParentSize()
                .focusedCard()
                .gameOrigin(g.stableKey, true)
                .clip(uiRounded(screenCorner))
                .background(placeholderBrush(g.hue))
                .tapOnly { open() }
        ) {
            Crossfade(targetState = g, animationSpec = tween(ms), label = "arcadeScreen") { shown -> ArcadeScreenPicture(c, shown) }
            ArcadeGlass(lines, Modifier.fillMaxSize())
        }
        if (sel) Box(Modifier.matchParentSize().border(2.dp, ink.trimNow, uiRounded(screenCorner)))
    }
}

/**
 * The focus effect (soft glow / electric frame, breathing) behind a cabinet's screen. Its own small composable, so the
 * breathing (about 20 steps a second, stopped when idle) only redraws this, never the screen's picture or the cabinet.
 */
@Composable
private fun ArcadeFocusGlow(corner: Dp, color: Color, outline: Dp, modifier: Modifier) {
    val pulse = rememberFocusPulse(animate = !LocalLiteEffects.current && LocalCardLook.current.focusEffect != com.shelf.launcher.data.FocusEffect.NONE)
    Box(modifier.styleFocus(true, corner, pulse, color, outline))
}

/** The big cabinet's screen: the game's backdrop (screenshot, wide art, else its cover), cropped to fill. */
@Composable
private fun ArcadeScreenPicture(c: StyleCtx, g: Game) {
    val titles = LocalTitleOptions.current
    val url = c.backdrop(g) ?: c.wide(g)
    Box(Modifier.fillMaxSize().background(placeholderBrush(g.hue)), contentAlignment = Alignment.Center) {
        if (url != null) {
            AsyncImage(url, titles.display(g.name), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork())
        } else if (g.sourceType == com.shelf.launcher.data.SourceType.APP) {
            AppIconArt(g.packageName, wide = true, modifier = Modifier.fillMaxSize())
        } else {
            FitText(
                titles.display(g.name).uppercase(), Color.White, maxSize = 18.sp, minSize = 9.sp, maxLines = 3,
                modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                style = uiFontStyle().copy(fontFamily = FontFamily.Monospace)
            )
        }
    }
}

/** The glass over the screen: scan lines (option), darker curved edges and a soft reflection. Drawn once, no animation. */
@Composable
private fun ArcadeGlass(lines: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        if (lines) {
            val pitch = 3.dp.toPx()
            val thick = maxOf(1f, pitch * 0.42f)
            var y = 0f
            while (y < size.height) {
                drawRect(Color.Black.copy(alpha = 0.22f), topLeft = Offset(0f, y), size = Size(size.width, thick))
                y += pitch
            }
        }
        // The tube's curve: the corners and edges fall off into shadow.
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f),
                center = center, radius = size.maxDimension * 0.66f
            )
        )
        // The glass catching the light: a soft streak from the top left, and a faint bright band near the top.
        drawRect(
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                start = Offset.Zero, end = Offset(size.width * 0.55f, size.height * 0.65f)
            )
        )
        drawOval(
            Color.White.copy(alpha = 0.05f), topLeft = Offset(size.width * 0.08f, size.height * 0.03f),
            size = Size(size.width * 0.84f, size.height * 0.2f)
        )
    }
}

/** The marquee's sign: the game's logo when it has one (and Game names › Logos is on), else its name in bold letters. */
@Composable
private fun ArcadeMarquee(c: StyleCtx, g: Game, glow: Boolean, compact: Boolean) {
    val titles = LocalTitleOptions.current
    val logo = if (titles.showLogos) c.logo(g) else null
    var failed by remember(logo) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (logo != null && !failed) {
            AsyncImage(
                logo, titles.display(g.name), contentScale = ContentScale.Fit, onError = { failed = true },
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (glow) 1f else 0.8f }
            )
        } else {
            FitText(
                titles.display(g.name).uppercase(), if (glow) Color.White else Color.White.copy(alpha = 0.78f),
                maxSize = if (compact) 16.sp else 22.sp, minSize = 7.sp, maxLines = 2,
                fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                style = uiFontStyle().copy(
                    fontStyle = FontStyle.Italic,
                    shadow = Shadow(Color.Black.copy(alpha = 0.75f), Offset(0f, 2f), 6f)
                ),
                lineHeightRatio = 1.05f
            )
        }
    }
}

/** The category you're in and where you are in it, like a high-score line; ◀ ▶ tap through the categories. */
@Composable
private fun ArcadeCategory(c: StyleCtx, compact: Boolean) {
    val step by rememberUpdatedState(c.actions.category)
    val accent = ShelfTheme.accent
    val size = if (compact) 11.sp else 13.sp
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (c.stops.size > 1) {
            // (The padding sits inside the tap area, so the small arrows are easy to hit with a finger.)
            Text("◀", color = Color.White.copy(alpha = 0.6f), fontSize = size, modifier = Modifier.tapOnly { step(-1) }.padding(end = 8.dp, top = 6.dp, bottom = 6.dp))
        }
        CategoryTitle.Native() // (Update 212: the layout's own category title.)
        Text(
            c.categoryLabel.uppercase(), color = accent, fontSize = size, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            maxLines = 1, softWrap = false, modifier = Modifier.weight(1f, fill = false), style = nameTextStyle(NameBackdrop.SHADOW)
        )
        Text(
            "  ${c.focus + 1} / ${c.games.size}", color = Color.White.copy(alpha = 0.7f), fontSize = size, fontFamily = FontFamily.Monospace,
            maxLines = 1, softWrap = false, style = nameTextStyle(NameBackdrop.SHADOW)
        )
        if (c.stops.size > 1) {
            Text("▶", color = Color.White.copy(alpha = 0.6f), fontSize = size, modifier = Modifier.tapOnly { step(1) }.padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 4.dp))
        }
    }
}

/** The game's name, console, time played (and last played) and its achievements. */
@Composable
private fun ArcadeInfo(c: StyleCtx, g: Game, trim: Color, compact: Boolean) {
    val titles = LocalTitleOptions.current
    Column(Modifier.fillMaxWidth()) {
        FitText(
            titles.display(g.name), Color.White, maxSize = if (compact) 22.sp else 30.sp, minSize = 13.sp, maxLines = 2,
            fontWeight = FontWeight.Bold, style = nameTextStyle(NameBackdrop.SHADOW)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            titles.console(g.platform), color = lerp(trim, Color.White, 0.35f), fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, style = nameTextStyle(NameBackdrop.SHADOW)
        )
        Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
        val last = c.lastPlayed(g)
        Text(
            stylePlaytime(c.playtime(g), last != null) + (last?.let { "  ·  Last played: " + lastPlayedLabel(it) } ?: ""),
            color = Color.White.copy(alpha = 0.82f), fontSize = if (compact) 11.sp else 13.sp, maxLines = 1, softWrap = false,
            style = nameTextStyle(NameBackdrop.SHADOW)
        )
        val p = c.progress(g)
        if (p != null) {
            Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
            // (The chip has its own padding: shifted back so it lines up with the text.)
            GameProgressChip(p.first, p.second, Modifier.offset(x = (-5).dp))
        }
    }
}

/** Play / Continue and Details as round arcade push buttons with their names (▼ reaches them). */
@Composable
private fun ArcadeButtons(c: StyleCtx, g: Game, trim: Color, compact: Boolean) {
    val play by rememberUpdatedState(c.actions.play)
    val open by rememberUpdatedState(c.actions.open)
    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
        ArcadePush(if (c.lastPlayed(g) != null) "Continue" else "Play", trim, StyleNav.on(StyleNav.Zone.BAR, 0), compact) {
            StyleNav.tapped(StyleNav.Zone.BAR, 0); play(g)
        }
        ArcadePush("Details", Color(0xFFF2C14E), StyleNav.on(StyleNav.Zone.BAR, 1), compact) {
            StyleNav.tapped(StyleNav.Zone.BAR, 1); open()
        }
    }
}

/** One arcade push button: a round coloured button with a shine, its name beside it; picked = ringed and a bit bigger. */
@Composable
private fun ArcadePush(label: String, color: Color, on: Boolean, compact: Boolean, onTap: () -> Unit) {
    val d = if (compact) 26.dp else 34.dp
    val pill = RoundedCornerShape(50)
    Row(
        Modifier
            .graphicsLayer { val s = if (on) 1.06f else 1f; scaleX = s; scaleY = s }
            .clip(pill)
            .background(Color.Black.copy(alpha = if (on) 0.62f else 0.42f))
            .border(if (on) 2.dp else 1.dp, if (on) color else Color.White.copy(alpha = 0.22f), pill)
            .tapOnly(onTap)
            .padding(start = 4.dp, end = if (compact) 12.dp else 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(d)) {
            val r = size.minDimension / 2f
            drawCircle(Color(0xFF16171B), radius = r)
            drawCircle(
                Brush.radialGradient(
                    listOf(lerp(color, Color.White, 0.45f), color, lerp(color, Color.Black, 0.35f)),
                    center = center - Offset(r * 0.25f, r * 0.25f), radius = r * 0.95f
                ),
                radius = r * 0.76f
            )
        }
        Spacer(Modifier.width(if (compact) 6.dp else 9.dp))
        Text(
            label.uppercase(), color = Color.White, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false
        )
    }
}

/**
 * The row of small cabinets: the one you're on (lit up, a little bigger) about a third of the way along, the games
 * before it to its left and the next ones to its right. The row glides along as you move (Home › Animations ›
 * Moving between games); only the cabinets on screen (and one or two past each edge) are composed, so big libraries
 * stay light. Tap one to pick it, tap the one you're on to open it; swipe to move along.
 */
@Composable
private fun ArcadeRow(c: StyleCtx, ink: ArcadeInk, miniW: Dp, miniH: Dp, modifier: Modifier) {
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val latestFocus by rememberUpdatedState(c.focus)
    val count = c.games.size
    val latestCount by rememberUpdatedState(count)
    // With Endless on the row goes round (the place on the endless row is [vf], EndlessRow.kt).
    val loop = loopRowOn(c)
    val latestLoop by rememberUpdatedState(loop)
    val vf = rememberLoopFocus(c)
    val glide = Features.rowGlide.state.value
    val pos = remember { Animatable(vf.toFloat()) }
    LaunchedEffect(vf) {
        // A long jump (a new category, the first / last game): straight there instead of racing past dozens of cabinets.
        if (kotlin.math.abs(pos.value - vf) > 6f) pos.snapTo(vf.toFloat())
        else pos.animateTo(vf.toFloat(), spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = glide.stiffness))
    }
    // Which cabinets are composed changes only when the glide passes a whole game; their places move in the layout
    // step (offset { }), so the glide itself never recomposes the row.
    val whole by remember { derivedStateOf { kotlin.math.floor(pos.value).toInt() } }
    val own = Features.arcadeGameColors.state.value
    val glow = Features.arcadeGlow.state.value
    val themeBody = lerp(ShelfTheme.colors.surfaceRaised, ShelfTheme.accent, 0.28f)
    val themeTrim = ShelfTheme.accent
    val inGames = StyleNav.zone == StyleNav.Zone.GAMES
    val latestMiniW by rememberUpdatedState(miniW)
    BoxWithConstraints(
        modifier.pointerInput(Unit) {
            var acc = 0f
            // Update 146: the game the swipe has reached, kept for the whole swipe (the focus handed back by the home
            // screen only catches up after the next redraw, so a quick swipe could pick the same game twice and lose a step).
            var target = -1
            detectHorizontalDragGestures(
                onDragStart = { acc = 0f; target = latestFocus },
                onDragEnd = { acc = 0f },
                onDragCancel = { acc = 0f }
            ) { change, dx ->
                change.consume()
                acc += dx
                val stepPx = latestMiniW.toPx() * 1.1f
                val n = latestCount
                if (n > 0) {
                    if (target !in 0 until n) target = latestFocus.coerceIn(0, n - 1)
                    val was = target
                    while (acc <= -stepPx) { acc += stepPx; target = if (latestLoop) (target + 1).mod(n) else (target + 1).coerceAtMost(n - 1) }
                    while (acc >= stepPx) { acc -= stepPx; target = if (latestLoop) (target - 1).mod(n) else (target - 1).coerceAtLeast(0) }
                    if (target != was) select(target)
                }
            }
        }.categorySlide()
            // The cabinets past the row's left end would otherwise stand over the big cabinet.
            .clipToBounds()
    ) {
        val step = miniW * 1.3f
        val anchorX = maxWidth * 0.3f
        val floorY = maxHeight - 6.dp
        val before = (anchorX / step).toInt() + 2
        val after = ((maxWidth - anchorX) / step).toInt() + 2
        val first = (whole - before).let { if (loop) it else it.coerceAtLeast(0) }
        val last = (whole + 1 + after).let { if (loop) it else it.coerceAtMost(count - 1) }
        // The floor the cabinets stand on.
        Canvas(Modifier.fillMaxSize()) {
            val y = floorY.toPx()
            drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            drawRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent), startY = y, endY = size.height),
                topLeft = Offset(0f, y), size = Size(size.width, size.height - y)
            )
        }
        // (Update 246, #60: the places picked before the loop — a `continue` in it made Compose give each pass its
        // own group, so a step that moved every game one place along threw them all away and built them again.)
        for (i in (first..last).filter { i -> !(loop && !drawnOnce(i - whole, count, minOf(before, (count - 1) / 2))) }) {
            // (Update 218, #105: going round a short list, each game once — no copies of the same game at the sides.)
            val real = if (loop) i.mod(count) else i
            val g = c.games[real]
            val on = i == vf
            val sel = on && inGames
            androidx.compose.runtime.key(if (loop) "${g.stableKey}#${Math.floorDiv(i, count)}" else g.stableKey) {
                val lift = styleLift(sel)
                // The one you're on in the big cabinet's paint (read while drawing, so its fade never recomposes the row);
                // the others in a colour of their own (from the game's hue, nothing to decode) or the theme's.
                val fixed = if (own) Color.hsl(g.hue.toFloat(), 0.42f, 0.26f) else themeBody
                val bodyOf: () -> Color = if (on) ({ ink.body.value }) else ({ fixed })
                val trim = if (on) ink.trimNow else if (own) Color.hsl(g.hue.toFloat(), 0.7f, 0.62f) else themeTrim
                MiniCabinet(
                    c, g, miniW, miniH, bodyOf, trim, on = on, sel = sel, glow = glow,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (anchorX.toPx() + step.toPx() * (i - pos.value) - miniW.toPx() / 2f).roundToInt(),
                                (floorY - miniH).roundToPx()
                            )
                        }
                        .zIndex(if (on) 2f else 1f)
                        .graphicsLayer {
                            val s = 1f + 0.1f * lift
                            scaleX = s; scaleY = s
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            alpha = if (on) 1f else 0.86f
                        }
                        .pointerInput(real) { detectTapGestures { if (real == GridHome.focus) open() else select(real) } }
                )
            }
        }
    }
}

/** A small cabinet: the same cabinet drawn small, its marquee lit when it's the one you're on, the game's cover on its screen. */
@Composable
private fun MiniCabinet(
    c: StyleCtx, g: Game, w: Dp, h: Dp, bodyOf: () -> Color, trim: Color, on: Boolean, sel: Boolean, glow: Boolean,
    modifier: Modifier
) {
    val corner = (w * Cab.SW * 0.08f).coerceIn(2.dp, 6.dp)
    Box(modifier.size(w, h)) {
        Canvas(Modifier.fillMaxSize()) {
            val body = bodyOf()
            val marquee = if (on) trim else lerp(body, Color.White, 0.3f)
            drawCabinet(body, trim, marquee, lit = on || glow, glow = on && glow, detail = false)
        }
        Box(Modifier.offset(x = w * Cab.SX, y = h * Cab.SY).size(w * Cab.SW, h * Cab.SH)) {
            // Only the one you're on breathes with the focus effect (the others never start the pulse's ticking).
            if (sel) ArcadeFocusGlow(corner, trim, 1.5.dp, Modifier.matchParentSize())
            Box(Modifier.matchParentSize().clip(uiRounded(corner)).background(placeholderBrush(g.hue))) {
                val url = c.cover(g) ?: c.wide(g)
                if (url != null) {
                    AsyncImage(url, LocalTitleOptions.current.display(g.name), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork())
                } else if (g.sourceType == com.shelf.launcher.data.SourceType.APP) {
                    AppIconArt(g.packageName, wide = false, modifier = Modifier.fillMaxSize())
                }
                // The screens of the cabinets you're not on are a little dimmer.
                if (!on) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
            }
            if (sel) Box(Modifier.matchParentSize().border(1.5.dp, trim, uiRounded(corner)))
        }
    }
}
