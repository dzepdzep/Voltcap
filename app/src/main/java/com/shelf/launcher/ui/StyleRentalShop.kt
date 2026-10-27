package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HotkeyAction
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Update 344: Rental shop — Voltcap's own 1990s game rental store (original signs, names and drawings; nobody's shop
 * copied). You stand in front of a wall of shelves under a buzzing neon "GAME RENTALS" sign; each console is a section
 * with its own backlit sign. The category's games stand on three shelves as real 3D cases (or in your game look), front
 * out; the camera glides along the wall to the case you're on, which tips forward off the shelf, its shelf tag lit.
 * ◀ ▶ along the shelves, ▲ ▼ between them; ▲ on the top shelf opens the STORE MAP (the sections: ▲ ▼ ◀ ▶, A walks there).
 * A picks the case up — the game window: the case comes off the shelf to be turned in your hand and its rental tag beside
 * it (WinScenesConsole.kt). L2 / R2 walk to the next section. Touch: tap a case, swipe along the wall.
 */

/** Update 344: where the controller is in the Rental shop. */
internal object ShopNav {
    var map by mutableStateOf(false)
    var mapSel by mutableIntStateOf(0)
    @Volatile var ctx: StyleCtx? = null
    @Volatile var systems: List<Int> = emptyList()
    @Volatile var rows: Int = 3
    internal val keysDown = HashSet<Int>()
    fun signature(): Any = listOf(map, mapSel)
}

private fun rsSound(s: KitSound, fallback: UiSound?) = LayoutKitSounds.play(LayoutKit.SHOP, s, fallback)

private fun rsHotkey(a: HotkeyAction): Boolean {
    val h = GreenCoreHooks.hotkey ?: return false
    h(a)
    return true
}

private fun rsWalkTo(c: StyleCtx, pos: Int) {
    val idx = ShopNav.systems.getOrNull(pos) ?: return
    ShopNav.map = false
    val steps = pocketStepsTo(c, idx)
    if (steps != 0) c.actions.category(steps)
    rsSound(KitSound.PAGE, if (steps == 0) UiSound.SELECT else null)
}

/** Update 344: the Rental shop's keys (all of them while it's on). True = handled. */
internal fun rentalShopKey(event: KeyEvent, reach: (Boolean) -> Boolean): Boolean {
    val c = ShopNav.ctx ?: return false
    val code = event.nativeKeyEvent.keyCode
    val down = event.type == KeyEventType.KeyDown
    val up = event.type == KeyEventType.KeyUp
    if (up && ShopNav.keysDown.remove(code)) return true
    val k = event.key
    if (k == Key.ButtonStart || k == Key.Menu) { if (up) c.actions.quickMenu(); return up }
    if (k == Key.ButtonSelect) return up && rsHotkey(HotkeyAction.SORT)
    val isA = k == Key.ButtonA || k == Key.Enter || k == Key.DirectionCenter
    if (ShopNav.map) {
        val n = ShopNav.systems.size
        if (isA) { if (up) rsWalkTo(c, ShopNav.mapSel.coerceIn(0, (n - 1).coerceAtLeast(0))); return true }
        if (!down) return true
        val cols = 4
        when (k) {
            Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> {
                val d = when (k) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; Key.DirectionUp -> -cols; else -> cols }
                val nx = (ShopNav.mapSel + d).coerceIn(0, (n - 1).coerceAtLeast(0))
                if (nx != ShopNav.mapSel) { ShopNav.mapSel = nx; rsSound(KitSound.MOVE, UiSound.MOVE) }
            }
            Key.ButtonB, Key.Back -> if (event.nativeKeyEvent.repeatCount == 0) { ShopNav.map = false; rsSound(KitSound.BACK, UiSound.BACK) }
            else -> Unit
        }
        ShopNav.keysDown += code
        return true
    }
    if (!down) return false
    val first = event.nativeKeyEvent.repeatCount == 0
    fun take(): Boolean { ShopNav.keysDown += code; return true }
    val n = c.games.size
    val rows = ShopNav.rows
    when (k) {
        Key.DirectionLeft, Key.DirectionRight -> {
            if (n == 0) return take()
            val d = if (k == Key.DirectionRight) rows else -rows
            var next = c.focus + d
            next = when {
                next in 0 until n -> next
                StyleLoop.on && n > rows -> if (d > 0) c.focus % rows else ((n - 1) / rows) * rows + (c.focus % rows)
                d > 0 && (c.focus / rows) < (n - 1) / rows -> n - 1
                else -> c.focus
            }.coerceIn(0, n - 1)
            if (next != c.focus) { c.actions.select(next); rsSound(KitSound.MOVE, UiSound.MOVE) } else if (first) EdgeBump.hit(if (d > 0) 1 else -1)
            return take()
        }
        Key.DirectionUp -> {
            if (n > 0 && c.focus % rows > 0) { c.actions.select(c.focus - 1); rsSound(KitSound.MOVE, UiSound.MOVE) }
            else if (first) {
                val at = ShopNav.systems.indexOf(c.stop)
                if (ShopNav.systems.isNotEmpty()) { ShopNav.mapSel = at.coerceAtLeast(0); ShopNav.map = true; rsSound(KitSound.OPEN, UiSound.OPEN) } else reach(true)
            }
            return take()
        }
        Key.DirectionDown -> {
            if (n > 0 && c.focus % rows < rows - 1 && c.focus + 1 < n) { c.actions.select(c.focus + 1); rsSound(KitSound.MOVE, UiSound.MOVE) }
            else if (first) reach(false)
            return take()
        }
        else -> return if (n == 0 && (isA || k == Key.ButtonX || k == Key.ButtonY)) take() else false
    }
}

// ---- The shop ----

internal class ShopColors(val wallTop: Color, val wallBottom: Color, val neon: Color, val neon2: Color, val shelf: Color, val floorA: Color, val floorB: Color)

internal fun shopColors(w: Features.RsWall): ShopColors = when (w) {
    Features.RsWall.PURPLE -> ShopColors(Color(0xFF2B1846), Color(0xFF120A1F), Color(0xFFFF4FD8), Color(0xFF46E6FF), Color(0xFF6B4A2E), Color(0xFF2A2236), Color(0xFF1A1424))
    Features.RsWall.TEAL -> ShopColors(Color(0xFF0E3A40), Color(0xFF061A1D), Color(0xFFFFD84A), Color(0xFFFF5A5A), Color(0xFF7A5A3A), Color(0xFF1E3236), Color(0xFF112023))
    Features.RsWall.BLUE -> ShopColors(Color(0xFF14204F), Color(0xFF070B1E), Color(0xFF5BFF8F), Color(0xFFFF8A3D), Color(0xFF5E5E66), Color(0xFF1E2238), Color(0xFF121524))
}

/** Update 344: the shop's back wall, its neon and the tiled floor (the shelves and cases are the layout's own). */
@Composable
internal fun RentalShopBackdrop(modifier: Modifier) {
    val sc = shopColors(Features.rsWall.state.value)
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(sc.wallTop, sc.wallBottom), endY = size.height * 0.86f))
        // Wall panelling.
        var x = 0f
        while (x < size.width) { drawLine(Color.Black.copy(alpha = 0.18f), Offset(x, 0f), Offset(x, size.height * 0.86f), 2f); x += size.width / 14f }
        // The floor: tiles running away from you.
        val fy = size.height * 0.86f
        drawRect(sc.floorB, Offset(0f, fy), Size(size.width, size.height - fy))
        val rows = 4
        for (r in 0 until rows) {
            val y0 = fy + (size.height - fy) * (r / rows.toFloat()).let { it * it }
            val y1 = fy + (size.height - fy) * ((r + 1) / rows.toFloat()).let { it * it }
            val cols = 16
            for (cI in 0 until cols) if ((r + cI) % 2 == 0) {
                val sx0 = size.width * cI / cols; val sx1 = size.width * (cI + 1) / cols
                val spread = 0.15f * (r + 1) / rows
                val p = Path().apply {
                    moveTo(sx0 + (sx0 - size.width / 2f) * spread * (r / (r + 1f)), y0); lineTo(sx1 + (sx1 - size.width / 2f) * spread * (r / (r + 1f)), y0)
                    lineTo(sx1 + (sx1 - size.width / 2f) * spread, y1); lineTo(sx0 + (sx0 - size.width / 2f) * spread, y1); close()
                }
                drawPath(p, sc.floorA)
            }
        }
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), startY = fy, endY = fy + 30f), Offset(0f, fy), Size(size.width, 30f))
    }
}

@Composable
internal fun StyleRentalShop(c: StyleCtx, modifier: Modifier = Modifier) {
    val systems = remember(c.stops) { gcSystems(c) }
    val rowsWanted = Features.rsRows.state.value
    SideEffect {
        ShopNav.ctx = c
        ShopNav.systems = systems
        StyleNav.side = emptyList(); StyleNav.top = emptyList(); StyleNav.bar = emptyList()
        StyleLoop.on = loopRowOn(c)
    }
    rememberLayoutKit(LayoutKit.SHOP, Features.rsSounds.state.value, c.state.appearance.soundsEnabled)
    DisposableEffect(Unit) { onDispose { ShopNav.keysDown.clear(); ShopNav.map = false } }
    androidx.activity.compose.BackHandler(enabled = ShopNav.map && GamePopup.game == null) { ShopNav.map = false }
    if (CategoryTitle.captionOn(c.state.appearance)) CategoryTitle.Native(null)
    val sc = shopColors(Features.rsWall.state.value)
    val density = LocalDensity.current
    val reduced = !rememberAnimationsEnabled()
    val motion = !reduced && !LocalLiteEffects.current && rememberIsActive() && !HomeIdle.calm
    val clock = rememberGcClock(motion && Features.rsNeon.state.value, 1f, 20)
    // Walking to another section: the wall slides in from the side you walked to.
    val walk = remember { Animatable(0f) }
    val lastStop = remember { intArrayOf(c.stop) }
    LaunchedEffect(c.stop) {
        if (lastStop[0] == c.stop) return@LaunchedEffect
        val dir = if (c.stop > lastStop[0]) 1f else -1f
        lastStop[0] = c.stop
        if (reduced) return@LaunchedEffect
        walk.snapTo(dir)
        walk.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
    }
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val latestFocus by rememberUpdatedState(c.focus)
    val latestCount by rememberUpdatedState(c.games.size)
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val rows = if (maxHeight < 380.dp) 2 else rowsWanted.n
        SideEffect { ShopNav.rows = rows }
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        // The neon over everything, the section's sign under it.
        val flick = clock.floatValue
        val on = if (Features.rsNeon.state.value) (if (sin(flick * 7.3f) > 0.97f) 0.45f else 1f) else 1f
        Text(
            "GAME RENTALS", color = lerp(Color.White, sc.neon, 0.35f).copy(alpha = on), fontSize = if (maxHeight < 380.dp) 22.sp else 30.sp,
            fontWeight = FontWeight.Black, letterSpacing = 4.sp,
            style = TextStyle(shadow = Shadow(sc.neon.copy(alpha = 0.95f * on), Offset.Zero, 28f)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp)
        )
        val topY = hPx * 0.17f
        val shelfArea = hPx * 0.66f
        val rowH = shelfArea / rows
        val caseH = rowH * 0.8f
        val asCases = Features.rsCases.state.value == Features.RsCases.CASES
        val caseW = if (asCases) caseH * 0.72f else caseH * lookSlotAspect(c.type).coerceAtMost(1.2f)
        val colW = caseW * 1.12f
        val n = c.games.size
        val focus = c.focus.coerceIn(0, (n - 1).coerceAtLeast(0))
        val col = focus / rows
        // The camera keeps the wall's first cases at the left edge (no bare wall before them), then follows the case you're on.
        val halfVisible = ((wPx / colW) / 2f - 0.6f).coerceAtLeast(0f)
        val camTarget = maxOf(col.toFloat(), halfVisible)
        val cam = remember { Animatable(camTarget) }
        LaunchedEffect(camTarget) {
            if (reduced || abs(cam.value - camTarget) > 8f) cam.snapTo(camTarget)
            else cam.animateTo(camTarget, spring(dampingRatio = 0.9f, stiffness = 180f))
        }
        // The section's sign, hanging over the shelves.
        val st = c.stops.getOrNull(c.stop)
        // (Update 346: a backlit lightbox — dark plate, the console's logo lit white, the neon round its rim — instead of a white
        // card the logo's own white lines vanished into.)
        Box(Modifier.align(Alignment.TopCenter).zIndex(5f).offset { IntOffset(0, (hPx * 0.085f).roundToInt()) }.graphicsLayer { translationX = walk.value * wPx * 0.6f; alpha = 1f - abs(walk.value) }
            .shadow(14.dp, RoundedCornerShape(4.dp), ambientColor = sc.neon2, spotColor = sc.neon2)
            .clip(RoundedCornerShape(4.dp)).background(Brush.verticalGradient(listOf(Color(0xFF2A2A30), Color(0xFF111114))))
            .border(2.dp, lerp(sc.neon2, Color.White, 0.25f).copy(alpha = 0.6f + 0.4f * on), RoundedCornerShape(4.dp))
            .drawBehind {
                // The lamp behind the panel, brightest in the middle.
                drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), center = center, radius = size.width * 0.55f))
            }
            .padding(horizontal = 18.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center) {
            val plat = st?.platform?.takeIf { st.special == null && !com.shelf.launcher.data.isCollectionKey(it) }
            if (plat != null) PlatformLogo(plat, st.label, Modifier.height(24.dp).width(200.dp), textColor = Color.White, textSize = 15.sp, blackToWhite = true)
            else Text((st?.label ?: c.categoryLabel).uppercase(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                style = TextStyle(shadow = Shadow(Color.White.copy(alpha = 0.5f), Offset.Zero, 10f)))
        }
        // The shelves and their cases, the camera gliding along.
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = walk.value * wPx * 0.6f; alpha = 1f - abs(walk.value) * 0.7f }
            .pointerInput(Unit) {
                var acc = 0f
                detectHorizontalDragGestures(onDragStart = { acc = 0f }) { ch, dx ->
                    ch.consume(); acc += dx
                    val step = colW * 0.7f
                    val r = ShopNav.rows
                    if (acc <= -step) { acc = 0f; val nx = latestFocus + r; if (nx < latestCount) { select(nx); rsSound(KitSound.MOVE, UiSound.MOVE) } }
                    if (acc >= step) { acc = 0f; val nx = latestFocus - r; if (nx >= 0) { select(nx); rsSound(KitSound.MOVE, UiSound.MOVE) } }
                }
            }
        ) {
            // The boards.
            // (Update 346: shelf depth — each row a recess with a back panel, shadowed under the board above and lit by the
            // lamps in the ceiling; each board shows its top and a lit front lip; the far ends fall into shadow.)
            Canvas(Modifier.fillMaxSize()) {
                val recessTop = topY - rowH * 0.05f
                val unitTop = recessTop
                val unitBot = topY + rowH * rows + rowH * 0.03f
                // The back panel of the unit, darker than the wall.
                drawRect(Brush.verticalGradient(listOf(lerp(sc.wallTop, Color.Black, 0.3f), lerp(sc.wallBottom, Color.Black, 0.2f)), startY = unitTop, endY = unitBot),
                    Offset(0f, unitTop), Size(size.width, unitBot - unitTop))
                // The ceiling lamps: soft pools of light down the back panel.
                val lamps = 6
                for (l in 0 until lamps) {
                    val cx = size.width * (l + 0.5f) / lamps
                    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), center = Offset(cx, unitTop), radius = size.width / lamps * 0.9f),
                        Offset(cx - size.width / lamps, unitTop), Size(size.width * 2f / lamps, unitBot - unitTop))
                }
                for (r in 0 until rows) {
                    val rowTop = topY + rowH * r
                    val y = topY + rowH * (r + 1) - rowH * 0.06f
                    // The shadow the board above throws into the recess.
                    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), startY = rowTop - rowH * 0.02f, endY = rowTop + rowH * 0.22f),
                        Offset(0f, rowTop - rowH * 0.02f), Size(size.width, rowH * 0.24f))
                    // Under the cases, where they meet the board.
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), startY = y - rowH * 0.1f, endY = y),
                        Offset(0f, y - rowH * 0.1f), Size(size.width, rowH * 0.1f))
                    // The board: its top seen from just above, its front lip, its shadow on the panel below.
                    val topFace = rowH * 0.035f
                    val lip = rowH * 0.06f
                    drawRect(lerp(sc.shelf, Color.White, 0.12f), Offset(0f, y - topFace), Size(size.width, topFace))
                    drawRect(Brush.verticalGradient(listOf(lerp(sc.shelf, Color.White, 0.3f), sc.shelf, lerp(sc.shelf, Color.Black, 0.45f)), startY = y, endY = y + lip),
                        Offset(0f, y), Size(size.width, lip))
                    drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, y + 1f), Offset(size.width, y + 1f), 1.5f)
                    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), startY = y + lip, endY = y + lip + rowH * 0.08f),
                        Offset(0f, y + lip), Size(size.width, rowH * 0.08f))
                }
                // The ends of the wall in shadow.
                drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.45f), 0.12f to Color.Transparent, 0.88f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f)),
                    Offset(0f, unitTop), Size(size.width, unitBot - unitTop))
            }
            if (n == 0) {
                Text(EmptyShelf.text, color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, modifier = Modifier.align(Alignment.Center))
            } else {
                val camX = cam.value
                val half = (wPx / colW / 2f).toInt() + 2
                for (cc in (camX.toInt() - half).coerceAtLeast(0)..(camX.toInt() + half)) {
                    for (r in 0 until rows) {
                        val i = cc * rows + r
                        if (i >= n) break
                        val g = c.games[i]
                        key(i) {
                            val isOn = i == focus
                            val lift by animateFloatAsState(if (isOn) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 420f), label = "rsLift")
                            // Each case at its own real shape, standing on the board (a wide box is lower, never wider than its slot).
                            val a = if (asCases) case3dAspect(g, false) else caseW / caseH
                            val bh = minOf(caseH, colW * 0.92f / a)
                            val bw = bh * a
                            // (Update 346: picked up — its shadow left on the board as it comes forward.)
                            if (lift > 0.01f) Box(
                                Modifier.size(with(density) { (bw * 1.15f).toDp() }, with(density) { (rowH * 0.08f).toDp() }).zIndex(2f)
                                    .graphicsLayer {
                                        translationX = wPx / 2f + (cc - cam.value) * colW - bw * 0.575f
                                        translationY = topY + rowH * (r + 1) - rowH * 0.06f - rowH * 0.05f
                                        alpha = lift
                                    }
                                    .background(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                            )
                            Box(
                                Modifier.size(with(density) { bw.toDp() }, with(density) { bh.toDp() }).zIndex(if (isOn) 3f else 1f)
                                    .graphicsLayer {
                                        val x = wPx / 2f + (cc - cam.value) * colW - bw / 2f
                                        translationX = x
                                        translationY = topY + rowH * (r + 1) - rowH * 0.06f - bh - lift * caseH * 0.13f
                                        val s = 1f + 0.15f * lift
                                        scaleX = s; scaleY = s
                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                                        rotationX = -8f * lift
                                        cameraDistance = 10f * density.density
                                        // The far ends of the wall go dimmer.
                                        val d = abs((cc - cam.value) * colW) / (wPx / 2f)
                                        alpha = (1.25f - d * 0.5f).coerceIn(0.35f, 1f)
                                    }
                                    .pointerInput(i) { detectTapGestures { if (latestFocus == i) open() else { select(i); rsSound(KitSound.MOVE, UiSound.MOVE) } } }
                            ) {
                                if (asCases) {
                                    Case3D(g, c.cover(g), c.state.physicalMedia[g.stableKey], 0f, a, Modifier.fillMaxSize().gameOrigin(g.stableKey, isOn),
                                        pitch = 2f, realFront = c.state.artOverrides[g.stableKey]?.cover == null, fill = 1f)
                                } else StyleArt(c, g, Modifier.fillMaxSize(), focused = isOn, fx = if (isOn) 0f else null, ring = sc.neon2)
                            }
                            // The shelf tag under the case you're on.
                            if (isOn) Text(
                                if (c.lastPlayed(g) == null) "NEW!" else if (g.stableKey in c.state.favorites) "★ FAVE" else "PLAYED", color = Color(0xFF1C1C1C), fontSize = 10.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.zIndex(4f).graphicsLayer {
                                    translationX = wPx / 2f + (cc - cam.value) * colW - 22.dp.toPx()
                                    translationY = topY + rowH * (r + 1) - rowH * 0.04f
                                    alpha = lift
                                }.background(sc.neon2).padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
        // The shelf talker: the case you're on, in words.
        c.game?.let { g -> ShopTalker(c, g, Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)) }
        if (ShopNav.map) ShopMap(c, sc, systems, Modifier.fillMaxSize().zIndex(10f))
    }
}

/** A card clipped to the shelf edge: the title, the console, time played and achievements. */
@Composable
private fun ShopTalker(c: StyleCtx, g: Game, modifier: Modifier) {
    val titles = LocalTitleOptions.current
    Row(
        modifier.widthIn(max = 640.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFFFE14A)).border(2.dp, Color(0xFF1C1C1C), RoundedCornerShape(2.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FullText(titles.display(g.name).uppercase(), color = Color(0xFF1C1C1C), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1,
            modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(10.dp))
        Text(stylePlaytime(c.playtime(g), c.lastPlayed(g) != null), color = Color(0xFF3A3A3A), fontSize = 12.sp, maxLines = 1)
        c.progress(g)?.let { (e, t) ->
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Filled.EmojiEvents, null, tint = Color(0xFF1C1C1C), modifier = Modifier.size(14.dp))
            Text(" $e/$t", color = Color(0xFF1C1C1C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (g.stableKey in c.state.favorites) { Spacer(Modifier.width(8.dp)); Text("★ STAFF PICK", color = Color(0xFFC0161A), fontSize = 11.sp, fontWeight = FontWeight.Black) }
    }
}

/** The STORE MAP: the sections as aisles on a floor plan; A walks there. */
@Composable
private fun ShopMap(c: StyleCtx, sc: ShopColors, systems: List<Int>, modifier: Modifier) {
    val sel = ShopNav.mapSel
    val counts = remember(c.state.games) { c.state.games.groupingBy { it.platform }.eachCount() }
    Box(modifier.background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(0.86f).clip(RoundedCornerShape(4.dp)).background(Color(0xFFF7F3E6)).border(3.dp, Color(0xFF1C1C1C), RoundedCornerShape(4.dp)).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Storefront, null, tint = Color(0xFF1C1C1C), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("STORE MAP", color = Color(0xFF1C1C1C), fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Text("YOU ARE HERE: " + c.categoryLabel.uppercase(), color = Color(0xFFC0161A), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.height(10.dp))
            val cols = 4
            val rowsN = (systems.size + cols - 1) / cols
            val first = ((sel / cols) - 2).coerceIn(0, (rowsN - 4).coerceAtLeast(0))
            for (r in first until (first + 4).coerceAtMost(rowsN)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (cc in 0 until cols) {
                        val i = r * cols + cc
                        val st = c.stops.getOrNull(systems.getOrNull(i) ?: -1)
                        Box(Modifier.weight(1f).height(48.dp)) {
                            if (st != null) {
                                val on = i == sel
                                Column(
                                    Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)).background(if (on) sc.neon else Color(0xFFE2DCC8))
                                        .border(if (on) 3.dp else 1.dp, Color(0xFF1C1C1C), RoundedCornerShape(2.dp))
                                        .pointerInput(i) { detectTapGestures { if (ShopNav.mapSel == i) rsWalkTo(c, i) else ShopNav.mapSel = i } }
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text("AISLE ${i + 1}", color = Color(0xFF1C1C1C).copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    FullText(st.label.uppercase(), color = Color(0xFF1C1C1C), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
                                    st.platform?.let { p -> counts[p]?.let { Text("$it GAMES", color = Color(0xFF1C1C1C).copy(alpha = 0.7f), fontSize = 9.sp) } }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("▲ ▼ ◀ ▶ PICK AN AISLE   A WALK THERE   B BACK", color = Color(0xFF5A5446), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

// ---- Options ----

/** Update 344: Settings › Home › Layout options (Rental shop). Each its own "rs_…" Features pref. */
@Composable
internal fun RentalShopSettings() {
    MutedNote(
        "A 1990s rental store: each console a section of shelves under a neon sign. ◀ ▶ along the shelves, ▲ ▼ between " +
            "them, ▲ on the top shelf the store map, A picks the case up, L2 / R2 walk to the next section. Tap a case, " +
            "swipe along the wall."
    )
    Spacer(Modifier.height(10.dp))
    StyleChoice("The games as", Features.RsCases.values(), Features.rsCases.state.value, { it.label }) { Features.rsCases.value = it }
    StyleChoice("Shelves", Features.RsRows.values(), Features.rsRows.state.value, { it.label }) { Features.rsRows.value = it }
    StyleChoice("Walls", Features.RsWall.values(), Features.rsWall.state.value, { it.label }) { Features.rsWall.value = it }
    Toggle(Icons.Filled.Storefront, "Buzzing neon", "The sign flickers now and then. Off: steady.", Features.rsNeon)
    Toggle(Icons.Filled.QueueMusic, "Interface sounds", "Plastic case clacks, a shelf slide and the door's bell. Off: Voltcap's usual sounds.", Features.rsSounds)
    if (Features.rsSounds.state.value) {
        PercentSlider("Sound volume", Features.kitVolume.state.value, { Features.kitVolume.value = it.coerceIn(0, 100) })
        Spacer(Modifier.height(6.dp))
    }
}
