package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/*
 * Update 507 (owner review of batch 506, P1): the weakest families of the 73-page review get windows of their own home's
 * make instead of a recoloured rectangle — Game shelf and Case stacks bring the case itself forward out of the home and put it
 * back, Card binder turns its card over, Cube menu turns a real cube face, Console hub opens a console's nameplate, Timeline
 * hangs its entry from the year line, Tile home / Grid lift the tile out of the grid. Shared parts stay shared: the facts
 * (SceneFactsBody), the keys (KeyRow / SceneKey: Play first, wrapped lines reachable with the D-pad, never cut), the
 * reveal (0…1, GameWindow's grow) — the window's motion follows Home › Animations › Popup windows.
 */

/** Pure: where a stage of the reveal [k] runs from [a] to [b] (0…1). */
internal fun sceneStage(k: Float, a: Float, b: Float): Float = ((k - a) / (b - a)).coerceIn(0f, 1f)

/** The shape of the game's case as its home draws it (the console's cover frame / real case size), width / height. */
internal fun sceneCaseAspect(platform: String): Float {
    val cp = LookStyles.casePlatform(platform) ?: platform
    return (MediaCovers.used(cp)?.frontAspect ?: realCaseMm(cp)?.let { it.first / it.second } ?: 0.7f).coerceIn(0.5f, 1.6f)
}

/** The game's own case, exactly as the shelf shows it pulled out (its scan, the console's cover frame, or the cover). */
@Composable
internal fun SceneCase(ctx: WinSceneCtx, modifier: Modifier) =
    PulledOutCase(ctx.g, ctx.cover, ctx.state.physicalMedia[ctx.g.stableKey], false, modifier)

/**
 * The case flying out of the home into its place in the window — from the very picture it was on the home ([LocalSceneOrigin],
 * GameWindow's caseFlight), at that size, with a small lift toward you on the way — and back the same way on closing (the home's
 * own copy is hidden meanwhile: MediaInspect). [turnFrom]: it starts turned that far (a case pulled out of a wall of spines turns
 * to face you). Without an origin (from the other screen, Instant), it simply settles in place.
 */
@Composable
internal fun SceneFlyingCase(ctx: WinSceneCtx, modifier: Modifier, turnFrom: Float = 0f, until: Float = 0.86f) {
    val origin = LocalSceneOrigin.current
    var to by remember { mutableStateOf<Rect?>(null) }
    Box(modifier.onGloballyPositioned { to = it.boundsInWindow() }.graphicsLayer {
        // (Update 508, owner on the GT78: "arrives in about 0.1 s" — the reveal's own easing already slows its end, a second
        // ease-out on a short stage put the whole trip in its first frames. The trip now spans most of a longer reveal on the
        // reveal's own curve, and the case comes toward you on the way: a little larger mid-flight, lifted, its shadow longest.)
        val k = caseFlightCurve(sceneStage(ctx.reveal(), 0f, until))
        val t = to
        if (origin != null && origin.top != Float.MAX_VALUE && t != null && t.height > 0f) {
            val s0 = (origin.height / t.height).coerceIn(0.05f, 3f)
            val arc = kotlin.math.sin(k * Math.PI).toFloat()
            // (Device frames, GT78: from the shelf to its place in the window is a short way — a little left, a little smaller —
            // so moving alone read as "it barely moves". It comes forward on the way: up to a fifth larger, lifted off the
            // shelf, its shadow longest, then settles.)
            val s = (s0 + (1f - s0) * k) * (1f + CASE_FORWARD * arc)
            scaleX = s; scaleY = s
            translationX = (origin.center.x - t.center.x) * (1f - k)
            translationY = (origin.center.y - t.center.y) * (1f - k) - arc * t.height * 0.16f
            rotationY = turnFrom * (1f - k)
            cameraDistance = 14f * density
            // (D285: turned text drawn flat.)
            flatTextFor3d()
            shadowElevation = 6.dp.toPx() + 24.dp.toPx() * arc
        } else {
            val s = 0.9f + 0.1f * k
            scaleX = s; scaleY = s; alpha = k
            shadowElevation = 6.dp.toPx() * k
        }
    }) { SceneCase(ctx, Modifier.fillMaxSize()) }
}

/**
 * Update 508: the case's trip along the reveal. The reveal itself already eases in and out (FastOutSlowIn), so the trip
 * follows it as it is — a second ease on top either crammed the trip into its first frames (507's ease-out) or held the case
 * still for its first ~150 ms (an ease-in-out tried first, measured on the GT78). Pure.
 */
internal fun caseFlightCurve(k: Float): Float = k.coerceIn(0f, 1f)

/** Update 508: how much larger the case is at the middle of its trip (it comes toward you). */
internal const val CASE_FORWARD = 0.2f

/** No picture: a purposeful plate — the console's initials big, the console's name under them — never an empty black box. */
@Composable
internal fun SceneNoArt(ctx: WinSceneCtx, modifier: Modifier, ink: Color = Color.White) {
    Box(modifier.background(placeholderBrush(ctx.g.hue)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
            Text(studioInitials(ctx.console), color = ink, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(ctx.console, color = ink.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 12.sp)
        }
    }
}

/** The game's picture filling its room (Smart Fill, Batch 523: a light crop, else whole over its soft copy — never bars), or [SceneNoArt]. */
@Composable
private fun SceneArt(ctx: WinSceneCtx, url: String?, modifier: Modifier, well: Color) {
    SmartArt(url, ctx.name, modifier, well = well, noArt = { m -> SceneNoArt(ctx, m) })
}

private fun inkOn(c: Color) = if (c.luminance() > 0.45f) Color(0xFF15171C) else Color.White

// ───────────────────────────── Game shelf: the case comes off the shelf ─────────────────────────────

/** Update 507: Game shelf / Case stacks lay the window out round the case: (case width dp, window width dp). Pure. */
internal fun caseWindowGeometry(maxW: Float, maxH: Float, aspect: Float): Pair<Float, Float> {
    val compact = maxH < 380f || maxW < 560f
    val caseH = minOf(maxH * (if (compact) 0.6f else 0.7f), 340f)
    val caseW = minOf(caseH * aspect, maxW * 0.3f).coerceAtLeast(64f)
    return caseW to minOf(maxW, 900f)
}

@Composable
internal fun GameShelfWindow(ctx: WinSceneCtx) {
    val a = ctx.accent; val cream = Color(0xFFF1E6D3); val label = Color(0xFFC9A57A)
    val aspect = sceneCaseAspect(ctx.g.platform)
    val (caseWv, totalWv) = caseWindowGeometry(ctx.maxW.value, ctx.maxH.value, aspect)
    val caseW = caseWv.dp
    Row(Modifier.width(totalWv.dp).heightIn(max = ctx.maxH), verticalAlignment = Alignment.CenterVertically) {
        // The case, taken off the shelf, standing on a ledge of the same wood. (Beside an inspected piece — Inspect — the piece
        // is the case: the card alone.)
        if (!ctx.inspecting) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SceneFlyingCase(ctx, Modifier.width(caseW).height(caseW / aspect))
            Box(Modifier.width(caseW + 26.dp).height(9.dp).graphicsLayer { alpha = sceneStage(ctx.reveal(), 0.3f, 0.7f) }
                .clip(RoundedCornerShape(2.dp)).background(Brush.verticalGradient(listOf(Color(0xFF8A6544), Color(0xFF4A3524)))))
            Box(Modifier.width(caseW + 16.dp).height(6.dp).graphicsLayer { alpha = sceneStage(ctx.reveal(), 0.3f, 0.7f) }
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))
        }
        if (!ctx.inspecting) Spacer(Modifier.width(18.dp))
        // The shelf's own card, sliding out from behind the case once it is off the shelf.
        Column(Modifier.weight(1f).heightIn(max = ctx.maxH).graphicsLayer {
            val e = sceneEase(sceneStage(ctx.reveal(), 0.32f, 1f))
            translationX = (1f - e) * -44.dp.toPx(); alpha = e
            shape = RoundedCornerShape(6.dp); clip = true
            shadowElevation = 14.dp.toPx() * e
        }.background(Brush.verticalGradient(listOf(Color(0xFF34281D), Color(0xFF1C1610)))).drawBehind {
            // The wood's grain: long faint lines, a little wavy.
            val step = 9.dp.toPx(); var y = 14.dp.toPx(); var i = 0
            while (y < size.height) {
                val p = Path().apply {
                    moveTo(0f, y)
                    var x = 0f
                    while (x < size.width) { x += size.width / 6f; lineTo(x, y + kotlin.math.sin((x / size.width + i * 0.37f) * 6.28f).toFloat() * 1.6f) }
                }
                drawPath(p, Color(0xFF5A4330).copy(alpha = if (i % 3 == 0) 0.32f else 0.16f), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                y += step; i++
            }
        }.border(1.dp, Color(0xFF6B5039).copy(alpha = 0.7f), RoundedCornerShape(6.dp))) {
            // The shelf's lip.
            Box(Modifier.fillMaxWidth().height(6.dp).background(Brush.verticalGradient(listOf(Color(0xFF7A5A3E), Color(0xFF3E2E21)))))
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp)) {
                // The console on the shelf's brass label holder.
                Text(ctx.console.uppercase(), color = Color(0xFF2A1E12), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                    modifier = Modifier.clip(RoundedCornerShape(2.dp)).background(Brush.verticalGradient(listOf(Color(0xFFE6C891), Color(0xFFB08A50))))
                        .padding(horizontal = 8.dp, vertical = 2.dp))
                Spacer(Modifier.height(6.dp))
                StableFullText(ctx.name, color = cream, fontSize = 21.sp, maxLines = 3, fontWeight = FontWeight.Bold, reserve = false)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.weight(1f, fill = false)) { SceneFactsBody(ctx, label, cream, null, omit = setOf("Console")) }
                Spacer(Modifier.height(8.dp))
                KeyRow(sceneKeys(ctx), 6.dp) { k ->
                    SceneKey(k, Color(0xFF3A2D21), a, cream, inkOn(a), Color(0xFF5A4532), a, RoundedCornerShape(4.dp), size = 12.sp)
                }
            }
        }
    }
}

// ───────────────────────────── Case stacks: the case comes out of its stack ─────────────────────────────

@Composable
internal fun CaseStackWindow(ctx: WinSceneCtx) {
    val a = ctx.accent; val ink = Color(0xFFEDEAF3); val muted = Color(0xFFA9A2B8)
    val aspect = sceneCaseAspect(ctx.g.platform)
    val (caseWv, totalWv) = caseWindowGeometry(ctx.maxW.value, ctx.maxH.value, aspect)
    val caseW = caseWv.dp
    val stackShape = RoundedCornerShape(16.dp)
    Row(Modifier.width(totalWv.dp).heightIn(max = ctx.maxH), verticalAlignment = Alignment.CenterVertically) {
        // Out of the wall of spines: it slides out spine-first and turns to show its front, over its own glow on the floor.
        if (!ctx.inspecting) Box(contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.width(caseW * 1.3f).height(18.dp).offset(y = 10.dp).graphicsLayer { alpha = sceneStage(ctx.reveal(), 0.35f, 0.8f) }
                .background(Brush.radialGradient(listOf(a.copy(alpha = 0.45f), Color.Transparent))))
            SceneFlyingCase(ctx, Modifier.width(caseW).height(caseW / aspect), turnFrom = 68f)
        }
        if (!ctx.inspecting) Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f).heightIn(max = ctx.maxH).graphicsLayer {
            val e = sceneEase(sceneStage(ctx.reveal(), 0.3f, 1f))
            translationY = (1f - e) * 26.dp.toPx(); alpha = e
            shape = RoundedCornerShape(16.dp); clip = true
        }.background(Brush.verticalGradient(listOf(Color(0xFF2C2438), Color(0xFF1A1522))))
            // The case's spine down the card's edge, in the stack's lit colour.
            .drawBehind { drawRect(Brush.verticalGradient(listOf(a, a.copy(alpha = 0.25f))), size = androidx.compose.ui.geometry.Size(5.dp.toPx(), size.height)) }
            .border(1.dp, Color.White.copy(alpha = 0.08f), stackShape).padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            Text(listOfNotNull(ctx.console, ctx.year).joinToString("  ·  ").uppercase(), color = a, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(2.dp))
            StableFullText(ctx.name, color = ink, fontSize = 21.sp, maxLines = 3, fontWeight = FontWeight.Bold, reserve = false)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.weight(1f, fill = false)) { SceneFactsBody(ctx, muted, ink, null, omit = setOf("Console", "Year")) }
            Spacer(Modifier.height(8.dp))
            KeyRow(sceneKeys(ctx), 6.dp) { k ->
                SceneKey(k, Color.White.copy(alpha = 0.06f), a, ink, inkOn(a), Color.White.copy(alpha = 0.14f), a, RoundedCornerShape(50), size = 12.sp)
            }
        }
    }
}

// ───────────────────────────── Tile home / Grid: the tile lifted out of the grid ─────────────────────────────

/**
 * Batch 523: the lifted tile's (width, height) dp for a [tile] dp square and the picture's shape [art] (w / h, null =
 * unknown: the square): a portrait picture makes a tile 0.86 as wide and as tall as its shape asks, up to [maxH] and 1.5
 * tiles; a wide one a lower tile, never under 0.62 tiles. Pure.
 */
internal fun tileLiftSize(tile: Float, art: Float?, maxH: Float): Pair<Float, Float> {
    if (art == null || !art.isFinite() || art <= 0f) return tile to tile
    val a = art.coerceIn(0.55f, 2.2f)
    val w = if (a < 0.95f) tile * 0.86f else tile
    val h = (w / a).coerceIn(tile * 0.62f, minOf(tile * 1.5f, maxOf(maxH, tile)))
    return w to h
}

/** Update 508: Tile home's type size — a size up on a small screen (GT78 960×640), as before on a large one. Pure. */
internal fun tileLiftType(compact: Boolean): Float = if (compact) 1.18f else 1f

@Composable
internal fun TileLiftCard(ctx: WinSceneCtx) {
    val a = ctx.accent
    // The home's own ground (the theme's surface), not a white card pasted on a dark grid.
    val surface = themeDark(com.shelf.launcher.ui.theme.ShelfTheme.colors.surfaceRaised.copy(alpha = 1f), 0.35f)
    val ink = com.shelf.launcher.ui.theme.textOn(surface, Color.White)
    val muted = ink.copy(alpha = 0.6f)
    val w = minOf(ctx.maxW, 700.dp)
    val compact = ctx.maxH < 400.dp || ctx.maxW < 560.dp
    // (Update 508, owner on the GT78: "text and action labels are too small". On a small screen the tile gives up some width
    // to the writing, and the writing — title, console, facts, keys — is set a size up; the window itself does not grow.)
    val tile = minOf(w * (if (compact) 0.24f else 0.34f), ctx.maxH * (if (compact) 0.42f else 0.55f), 230.dp)
    val type = tileLiftType(compact)
    val lift = 14.dp
    // (Batch 523, owner — WWE SmackDown: a portrait cover sat between grey bars in the square tile, with empty card under
    // it. The lifted tile takes the picture's own shape: a portrait cover makes a taller, slightly narrower tile that runs
    // down the card's side; a wide picture a lower one. The card is never shorter than the tile beside it.)
    val (tileW, tileH) = tileLiftSize(tile.value, CoverAspects.rawOf(ctx.cover ?: ctx.wide), ctx.maxH.value * (if (compact) 0.66f else 0.72f))
        .let { it.first.dp to it.second.dp }
    Box(Modifier.width(w).heightIn(max = ctx.maxH)) {
        // The card behind: it unrolls downward from the tile's row.
        Row(Modifier.padding(top = lift).fillMaxWidth().heightIn(min = tileH - lift + 14.dp).graphicsLayer {
            val e = sceneEase(sceneStage(ctx.reveal(), 0.25f, 1f))
            scaleY = 0.2f + 0.8f * e; transformOrigin = TransformOrigin(0.5f, 0f); alpha = e
            shape = RoundedCornerShape(18.dp); clip = true
            shadowElevation = 12.dp.toPx() * e
        }.background(surface).drawBehind {
            // The grid it came from: faint tile outlines in the card's corner.
            val c = 22.dp.toPx(); val gap = 5.dp.toPx()
            for (r in 0 until 3) for (q in 0 until 4) {
                val x = size.width - (q + 1) * (c + gap); val y = gap + r * (c + gap)
                drawRoundRect(a.copy(alpha = 0.07f + 0.03f * ((r + q) % 2)), Offset(x, y), androidx.compose.ui.geometry.Size(c, c), androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
            }
        }.border(1.dp, ink.copy(alpha = 0.1f), RoundedCornerShape(18.dp)).padding(start = tileW + 28.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            Column(Modifier.weight(1f)) {
                StableFullText(ctx.name, color = ink, fontSize = (20f * type).sp, maxLines = 3, fontWeight = FontWeight.Bold, reserve = false)
                Text(ctx.console, color = a, fontSize = (12f * type).sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.weight(1f, fill = false)) { SceneFactsBody(ctx, muted, ink, upperLabels = false, omit = setOf("Console"), scale = type) }
                Spacer(Modifier.height(10.dp))
                KeyRow(sceneKeys(ctx), 6.dp) { k ->
                    SceneKeyStyled(k, if (k.primary) a else ink.copy(alpha = 0.08f), a, if (k.primary) inkOn(a) else ink, inkOn(a),
                        ink.copy(alpha = 0.16f), a, RoundedCornerShape(50), size = (12f * type).sp, pad = if (compact) 11.dp else 10.dp, height = 36.dp)
                }
            }
        }
        // The tile itself, lifted above the card's edge, its focus ring on: it pops forward first, then the card unrolls under it.
        Box(Modifier.padding(start = 14.dp).size(tileW, tileH).graphicsLayer {
            val k = sceneStage(ctx.reveal(), 0f, 0.55f)
            val pop = if (ctx.closing()) 0f else 0.06f * kotlin.math.sin(k * Math.PI.toFloat())
            val s = 0.7f + 0.3f * sceneEase(k) + pop
            scaleX = s; scaleY = s; alpha = (k * 3f).coerceIn(0f, 1f)
            shape = RoundedCornerShape(14.dp); clip = true
            shadowElevation = 16.dp.toPx() * k
        }.border(3.dp, a, RoundedCornerShape(14.dp)).padding(3.dp).clip(RoundedCornerShape(11.dp))) {
            SceneArt(ctx, ctx.cover ?: ctx.wide, Modifier.fillMaxSize(), lerp(surface, Color.Black, 0.3f))
        }
    }
}

// ───────────────────────────── Card binder: the card turns over ─────────────────────────────

/** Update 508: the card front's bottom line — the console, and the year when it is known (no stray dash). Pure. */
internal fun cardFrontLine(console: String, year: String?): String = console.uppercase() + (year?.takeIf { it.isNotBlank() }?.let { "  ·  $it" } ?: "")

@Composable
internal fun CardTurnOver(ctx: WinSceneCtx) {
    val h = minOf(ctx.maxH, 520.dp)
    val short = h < 420.dp
    val w = minOf(ctx.maxW, if (short) 620.dp else 560.dp)
    val pal = rememberCardPalette(ctx.cover, if (ctx.total > 0) ctx.earned to ctx.total else null, ctx.g)
    val face = Color(0xFFF7F3EA); val ink = Color(0xFF1E1A14)
    val cardShape = RoundedCornerShape(14.dp)
    Box(Modifier.width(w).heightIn(max = h), contentAlignment = Alignment.Center) {
        // The front, as it sits in the binder, turning away (first half of the turn)…
        Box(Modifier.matchParentSize().graphicsLayer {
            val k = sceneStage(ctx.reveal(), 0f, 0.5f)
            rotationY = k * 90f
            cameraDistance = 14f * density
            flatTextFor3d()
            alpha = if (k >= 0.999f || ctx.reveal() < 0.02f) 0f else 1f
            shape = cardShape; clip = true
        }.background(pal.frame).padding(7.dp).clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            // (Update 508, owner: "the front face shows almost only the cover". The binder card's front as a collectable card
            // is printed: its name bar, the picture whole in its window, and its line of console, year and trophies.)
            Column(Modifier.fillMaxSize().background(face)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StableFullText(ctx.name, color = ink, fontSize = 15.sp, maxLines = 2, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), reserve = false)
                    Box(Modifier.size(10.dp).clip(CircleShape).background(pal.star))
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp).border(3.dp, pal.frame, RoundedCornerShape(4.dp)).padding(3.dp)) {
                    SceneArt(ctx, ctx.cover ?: ctx.wide, Modifier.fillMaxSize(), Color(0xFF26221C))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(cardFrontLine(ctx.console, ctx.year), color = ink.copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                    if (ctx.total > 0) Text("★ ${ctx.earned}/${ctx.total}", color = Color(0xFFB0302A), fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        // …and its back, turning in (second half): the stats side of the card.
        Column(Modifier.fillMaxWidth().heightIn(max = h).graphicsLayer {
            val k = sceneStage(ctx.reveal(), 0.5f, 1f)
            rotationY = (1f - sceneEase(k)) * -90f
            cameraDistance = 14f * density
            alpha = if (k <= 0.001f) 0f else 1f
            shape = cardShape; clip = true
            shadowElevation = 16.dp.toPx() * k
            flatTextFor3d()
        }.background(pal.frame).padding(7.dp).clip(RoundedCornerShape(10.dp)).background(face).padding(10.dp)) {
            // The card's title bar.
            Row(verticalAlignment = Alignment.CenterVertically) {
                StableFullText(ctx.name, color = ink, fontSize = 18.sp, maxLines = 2, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), reserve = false)
                if (ctx.total > 0) Text("${ctx.earned}/${ctx.total}", color = Color(0xFFB0302A), fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
            Box(Modifier.padding(vertical = 5.dp).fillMaxWidth().height(2.dp).background(pal.frame))
            Row(Modifier.weight(1f, fill = false)) {
                // The picture window: the cover whole, at its own shape (no strip).
                Box(Modifier.width(minOf(w * (if (short) 0.24f else 0.3f), 150.dp)).aspectRatio(0.72f).border(3.dp, pal.frame, RoundedCornerShape(4.dp)).padding(3.dp)) {
                    SceneArt(ctx, ctx.cover ?: ctx.wide, Modifier.fillMaxSize(), Color(0xFF26221C))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(ctx.console.uppercase() + (ctx.year?.let { "  ·  $it" } ?: ""), color = ink.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(4.dp))
                    SceneFactsBody(ctx, ink.copy(alpha = 0.55f), ink, omit = setOf("Console", "Year"))
                }
            }
            Spacer(Modifier.height(8.dp))
            KeyRow(sceneKeys(ctx)) { k ->
                SceneKey(k, if (k.primary) Color(0xFF2A2620) else Color.White, Color(0xFF2A2620), if (k.primary) pal.star else ink, pal.star,
                    ink.copy(alpha = 0.35f), ink, RoundedCornerShape(50), size = 11.sp)
            }
        }
    }
}

// ───────────────────────────── Cube menu: a face of the cube turns toward you ─────────────────────────────

/** Update 508: how the cube's faces take the light (lerp amounts toward white / black). */
internal class CubeShade(val topLight: Float, val topFar: Float, val sideTop: Float, val sideFoot: Float)
internal val cubeShade = CubeShade(topLight = 0.38f, topFar = 0.12f, sideTop = 0.52f, sideFoot = 0.84f)

@Composable
internal fun CubeTurnFace(ctx: WinSceneCtx) {
    val side = minOf(ctx.maxW - 40.dp, ctx.maxH - 30.dp, 470.dp)
    val tintPick = com.shelf.launcher.data.Features.cubeTint.state.value
    val tint = if (tintPick == com.shelf.launcher.data.Features.CubeTint.THEME) ctx.accent else Color(tintPick.argb)
    val a = lerp(tint, Color.White, 0.25f)
    val faceTop = lerp(tint, Color.Black, 0.38f); val faceBottom = lerp(tint, Color.Black, 0.74f)
    val depth = 22.dp
    Box(Modifier.size(side + depth, side + depth)) {
        // The cube's top and right faces (its depth), drawn behind the face: it reads as a solid cube, not a card.
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = sceneStage(ctx.reveal(), 0.2f, 0.7f) }) {
            val d = depth.toPx(); val s = side.toPx()
            // (Update 508, owner: "subtle light and shading on the top and side faces". One light, up and to the left: the top
            // catches it — brightest at its back-left corner — the side is in shade, darkest toward the floor; the edges facing
            // the light carry a fine highlight, the far ones a dark line. Drawn once with the face, no per-frame light.)
            val top = Path().apply { moveTo(0f, d); lineTo(d, 0f); lineTo(d + s, 0f); lineTo(s, d); close() }
            drawPath(top, Brush.linearGradient(listOf(lerp(tint, Color.White, cubeShade.topLight), lerp(tint, Color.Black, cubeShade.topFar)),
                start = Offset(d, 0f), end = Offset(s, d)))
            val right = Path().apply { moveTo(s, d); lineTo(d + s, 0f); lineTo(d + s, s); lineTo(s, s + d); close() }
            drawPath(right, Brush.verticalGradient(listOf(lerp(tint, Color.Black, cubeShade.sideTop), lerp(tint, Color.Black, cubeShade.sideFoot)), startY = 0f, endY = s + d))
            // A faint band of shade along the side's front edge (where it turns away from the light).
            drawPath(right, Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent), startX = s, endX = s + d * 0.6f))
            val hi = Color.White.copy(alpha = 0.55f); val lo = Color.Black.copy(alpha = 0.45f); val px = 1.2f.dp.toPx()
            drawLine(hi, Offset(0f, d), Offset(s, d), px)                 // top face's front edge, toward the light
            drawLine(hi.copy(alpha = 0.35f), Offset(0f, d), Offset(d, 0f), px) // its left edge
            drawLine(hi.copy(alpha = 0.25f), Offset(s, d), Offset(d + s, 0f), px) // the corner where top meets side
            drawLine(lo, Offset(d + s, 0f), Offset(d + s, s), px)         // the side's far edge
            drawLine(lo.copy(alpha = 0.3f), Offset(d, 0f), Offset(d + s, 0f), px) // the top's back edge
        }
        // The front face: opaque glass (no picture showing through the writing), turning a quarter in on its left edge.
        Column(Modifier.offset(y = depth).size(side).graphicsLayer {
            val e = sceneEase(ctx.reveal())
            rotationY = (1f - e) * -90f
            transformOrigin = TransformOrigin(0f, 0.5f)
            cameraDistance = 10f * density
            alpha = if (ctx.reveal() < 0.02f) 0f else 1f
            shape = RoundedCornerShape(6.dp); clip = true
            flatTextFor3d()
        }.background(Brush.linearGradient(listOf(faceTop, faceBottom)))
            // (Update 508: the same light on the front — a soft sheen from its top-left corner.)
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), start = Offset.Zero, end = Offset(260f, 260f)))
            .border(2.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.4f))), RoundedCornerShape(6.dp)).padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // The game on the face as a square inset, whole.
                Box(Modifier.size(minOf(side * 0.3f, 120.dp)).clip(RoundedCornerShape(4.dp)).border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(4.dp))) {
                    SceneArt(ctx, ctx.cover ?: ctx.wide, Modifier.fillMaxSize(), faceBottom)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ctx.console.uppercase(), color = a, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    StableFullText(ctx.name, color = Color.White, fontSize = 20.sp, maxLines = 3, fontWeight = FontWeight.Black, reserve = false)
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.weight(1f, fill = false)) { SceneFactsBody(ctx, Color.White.copy(alpha = 0.6f), Color.White, omit = setOf("Console")) }
            Spacer(Modifier.height(8.dp))
            KeyRow(sceneKeys(ctx)) { k ->
                SceneKey(k, Color.Black.copy(alpha = 0.35f), a, Color.White, inkOn(a), Color.White.copy(alpha = 0.3f), Color.White, RoundedCornerShape(6.dp))
            }
        }
    }
}

// ───────────────────────────── Console hub: the console's nameplate ─────────────────────────────

@Composable
internal fun HubNameplate(ctx: WinSceneCtx) {
    val a = ctx.accent
    val w = minOf(ctx.maxW, 680.dp)
    val compact = ctx.maxH < 400.dp
    val metal = Brush.linearGradient(listOf(Color(0xFF3E434C), Color(0xFF262A30), Color(0xFF3A3F47)))
    val plate = RoundedCornerShape(10.dp)
    Column(Modifier.width(w).heightIn(max = ctx.maxH).graphicsLayer {
        // Out of the console list on the left: it slides in from that side, the nameplate first.
        val e = sceneEase(ctx.reveal())
        translationX = (1f - e) * -size.width * 0.18f
        alpha = (ctx.reveal() * 2f).coerceIn(0f, 1f)
        shape = plate; clip = true
        shadowElevation = 14.dp.toPx() * e
    }.background(metal).drawBehind {
        // Brushed metal and the plate's four screws.
        var y = 0f; val step = 3.dp.toPx()
        while (y < size.height) { drawLine(Color.White.copy(alpha = 0.025f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
        val r = 3.dp.toPx(); val m = 9.dp.toPx()
        listOf(Offset(m, m), Offset(size.width - m, m), Offset(m, size.height - m), Offset(size.width - m, size.height - m)).forEach { c ->
            drawCircle(Color(0xFF15171B), r, c); drawLine(Color(0xFF6A707A), c - Offset(r * 0.7f, 0f), c + Offset(r * 0.7f, 0f), 1.2f)
        }
    }.border(2.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Black.copy(alpha = 0.45f))), plate)) {
        // The console's engraved nameplate across the top, its power light in the hub's colour.
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 12.dp).graphicsLayer { alpha = sceneStage(ctx.reveal(), 0f, 0.5f) },
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(a).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(ctx.console.uppercase(), color = Color(0xFFD7DEE8), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, modifier = Modifier.weight(1f))
            ctx.year?.let { Text(it, color = Color(0xFFD7DEE8).copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
        }
        Box(Modifier.padding(horizontal = 22.dp, vertical = 8.dp).fillMaxWidth().height(2.dp)
            .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.White.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.5f)))))
        Row(Modifier.padding(start = 22.dp, end = 22.dp, bottom = 18.dp).weight(1f, fill = false)) {
            // The game seated in the console: its own case.
            val aspect = sceneCaseAspect(ctx.g.platform)
            val cw = minOf(w * (if (compact) 0.2f else 0.26f), 170.dp)
            SceneCase(ctx, Modifier.width(cw).height(cw / aspect))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                StableFullText(ctx.name, color = Color.White, fontSize = 21.sp, maxLines = 3, fontWeight = FontWeight.Bold, reserve = false)
                Spacer(Modifier.height(6.dp))
                Column(Modifier.weight(1f, fill = false)) { SceneFactsBody(ctx, Color.White.copy(alpha = 0.5f), Color.White, omit = setOf("Console", "Year")) }
                Spacer(Modifier.height(8.dp))
                KeyRow(sceneKeys(ctx)) { k ->
                    SceneKeyStyled(k, if (k.primary) a else Color.Black.copy(alpha = 0.3f), Color.White, if (k.primary) inkOn(a) else Color.White, Color.Black,
                        Color.White.copy(alpha = 0.2f), Color.White, RoundedCornerShape(4.dp))
                }
            }
        }
    }
}

// ───────────────────────────── Timeline: the entry hangs from the year line ─────────────────────────────

internal const val TIMELINE_UNDATED = "YEAR UNKNOWN"

/** Update 508: whether the entry has a year to hang on its node. Pure. */
internal fun timelineDated(year: String?): Boolean = !year.isNullOrBlank() && year.trim() != "—"

@Composable
internal fun TimelineHang(ctx: WinSceneCtx) {
    val a = ctx.accent
    val w = minOf(ctx.maxW, 680.dp)
    val node = 46.dp
    val dated = timelineDated(ctx.year)
    val compact = ctx.maxH < 400.dp
    Column(Modifier.width(w).heightIn(max = ctx.maxH)) {
        // The year line: drawn across from the left, its node (the year) on it — inside the window, never over its edge.
        Box(Modifier.fillMaxWidth().height(node), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(3.dp).graphicsLayer {
                scaleX = sceneEase(sceneStage(ctx.reveal(), 0f, 0.45f)); transformOrigin = TransformOrigin(0f, 0.5f)
            }.background(Brush.horizontalGradient(listOf(a.copy(alpha = 0.25f), a, a.copy(alpha = 0.25f)))))
            Box(Modifier.padding(start = 28.dp).size(node).graphicsLayer {
                val k = sceneStage(ctx.reveal(), 0.2f, 0.55f)
                val s = sceneEase(k) + 0.12f * kotlin.math.sin(k * Math.PI.toFloat())
                scaleX = s; scaleY = s
            }.clip(CircleShape).then(if (dated) Modifier.background(a) else Modifier.background(themeDark(Color(0xFF1A1D23))))
                .border(3.dp, if (dated) Color.White.copy(alpha = 0.85f) else a, CircleShape), contentAlignment = Alignment.Center) {
                // (Update 508, owner: "no unexplained — for a missing year". An undated game's node is open — a ring in the
                // line's colour with a dot at its heart, its place on the line kept — and the line says why beside it.)
                if (dated) Text(ctx.year!!, color = inkOn(a), fontSize = 13.sp, fontWeight = FontWeight.Black)
                else Box(Modifier.size(10.dp).clip(CircleShape).background(a))
            }
            if (!dated) Text(TIMELINE_UNDATED, color = a, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp,
                modifier = Modifier.padding(start = 28.dp + node + 8.dp).graphicsLayer { alpha = sceneStage(ctx.reveal(), 0.35f, 0.6f) }
                    .clip(RoundedCornerShape(50)).background(themeDark(Color(0xFF1A1D23))).border(1.dp, a.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 3.dp))
        }
        // The stem from the node down to the entry.
        Box(Modifier.padding(start = 28.dp + node / 2 - 1.dp).width(2.dp).height(12.dp).graphicsLayer { alpha = sceneStage(ctx.reveal(), 0.4f, 0.6f) }.background(a))
        // The entry, unrolling down from the stem.
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).graphicsLayer {
            val e = sceneEase(sceneStage(ctx.reveal(), 0.45f, 1f))
            scaleY = 0.3f + 0.7f * e; transformOrigin = TransformOrigin(0.08f, 0f); alpha = e
            shape = RoundedCornerShape(10.dp); clip = true
            shadowElevation = 12.dp.toPx() * e
        }.background(themeDark(Color(0xFF1A1D23))).border(1.dp, a.copy(alpha = 0.45f), RoundedCornerShape(10.dp)).padding(14.dp)) {
            Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.Top) {
                val aw = if (compact) 64.dp else 86.dp
                Box(Modifier.width(aw).aspectRatio(0.72f).clip(RoundedCornerShape(4.dp))) { SceneArt(ctx, ctx.cover ?: ctx.wide, Modifier.fillMaxSize(), Color.Black) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    StableFullText(ctx.name, color = Color.White, fontSize = 19.sp, maxLines = 3, fontWeight = FontWeight.Bold, reserve = false)
                    Text(listOfNotNull(ctx.console, ctx.developer).joinToString(" · "), color = a, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Column(Modifier.weight(1f, fill = false)) { SceneFactsBody(ctx, Color.White.copy(alpha = 0.5f), Color.White, omit = setOf("Console", "Year", "Made by")) }
                }
            }
            Spacer(Modifier.height(10.dp))
            KeyRow(sceneKeys(ctx)) { k ->
                SceneKeyStyled(k, if (k.primary) a else Color.White.copy(alpha = 0.06f), Color.White, if (k.primary) inkOn(a) else Color.White, Color.Black,
                    Color.White.copy(alpha = 0.12f), a, RoundedCornerShape(6.dp))
            }
        }
    }
}
