package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/*
 * Update 421 (popup audit, priority 1): the layouts that still opened the generic window get one that belongs to them.
 * Each is its own composition (shape, material, typography, entrance) built on the same WinSceneCtx as the 19 older
 * scenes: Play / Continue is the first control (ctx.playFocus is attached by SceneControl), every page's buttons and
 * every tool / chip stays reachable with the D-pad, B closes (GameWindow), the other pages draw ctx.pageBody inside the
 * scene. None of them owns the opening of a physical piece: on Game shelf / Case stacks / Record crate / Card binder /
 * Disc station the real case / sleeve / disc still comes out beside it (InspectLayout), the scene only replaces the
 * plain window next to it. Text never ends in "…": names wrap and shrink (StableFullText), key rows scroll sideways.
 */

/** The facts the scenes print, label to value, in one order everywhere. */
internal fun sceneFacts(ctx: WinSceneCtx): List<Pair<String, String>> = listOfNotNull(
    "Console" to ctx.console,
    ctx.year?.let { "Year" to it },
    ctx.developer?.let { "Made by" to it },
    ctx.genres?.let { "Genre" to it },
    "Played" to ctx.played,
    ctx.last?.let { "Last played" to it },
    if (ctx.total > 0) "Trophies" to "${ctx.earned} / ${ctx.total}" else null
)

/** Every control of the page: the page's buttons first (Play is the very first), then — on the first page — tools and chips. */
internal fun sceneKeys(ctx: WinSceneCtx): List<WinAction> =
    ctx.buttons + (if (ctx.page == WinPage.MAIN) ctx.tools + ctx.chips else emptyList())

/** A row of keys that wraps onto more lines when it doesn't fit (never squeezed, never cut). */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun KeyRow(keys: List<WinAction>, gap: Dp = 6.dp, key: @Composable (WinAction) -> Unit) {
    // Update 423: the keys wrap onto a second line instead of running off the window's edge (the 53-layout visual pass found
    // "Achievem" / "Opti" / a lone half key cut by the edge in 20 windows). D-pad ▼ reaches the second line (2D focus).
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap),
        itemVerticalAlignment = Alignment.CenterVertically) {
        keys.forEach { key(it) }
    }
}

/** A key drawn as [shape] in [fill] (lit: [onFill]) with an outline; its word in full, never wrapped mid-word. */
@Composable
internal fun SceneKey(
    a: WinAction, fill: Color, onFill: Color, ink: Color, onInk: Color, edge: Color, onEdge: Color, shape: Shape,
    font: FontFamily? = null, size: TextUnit = 12.sp, pad: Dp = 10.dp, height: Dp = 30.dp, upper: Boolean = false
) {
    SceneControl(a, Modifier) { on ->
        Row(
            Modifier.heightIn(min = height).clip(shape).background(if (on) onFill else fill)
                .border(if (on) 2.dp else 1.dp, if (on) onEdge else edge, shape).padding(horizontal = pad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(a.icon, null, tint = if (on) onInk else ink, modifier = Modifier.size(if (a.primary) 16.dp else 13.dp))
            Spacer(Modifier.width(5.dp))
            Text(if (upper) sceneLabel(a.label).uppercase() else sceneLabel(a.label), color = if (on) onInk else if (a.urgent) Color(0xFFFF8A3D) else ink,
                fontSize = if (a.primary) size * 1.15f else size, fontWeight = FontWeight.Bold, fontFamily = font, maxLines = 2)
        }
    }
}

/**
 * Update 422: a key's word without a symbol the layout's look puts in front of it ("▶ continue" in Hero / Disc looks — the
 * key already draws its icon, so it read "▶ ▶ continue"), first letter capitalised.
 */
internal fun sceneLabel(l: String): String = l.trimStart { !it.isLetterOrDigit() }.ifEmpty { l }.replaceFirstChar { it.uppercase() }

/** The first page's facts, label over value in two columns, or the other page's body. */
@Composable
internal fun ColumnScope.SceneFactsBody(ctx: WinSceneCtx, label: Color, value: Color, font: FontFamily? = null, upperLabels: Boolean = true) {
    if (ctx.page == WinPage.MAIN) {
        // (Update 424: a fact cut at the window's edge fades out — it scrolls — instead of ending in half a line.)
        val sc = rememberScrollState()
        Column(Modifier.weight(1f, fill = false).sceneFadeEdges(sc).verticalScroll(sc)) {
            sceneFacts(ctx).chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    pair.forEach { (l, v) ->
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(if (upperLabels) l.uppercase() else l, color = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = font, softWrap = false)
                            Text(v, color = value, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = font)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            ctx.summary?.let { Text(it, color = value.copy(alpha = 0.78f), fontSize = 11.sp, lineHeight = 14.sp, fontFamily = font, modifier = Modifier.padding(top = 2.dp)) }
        }
    } else ctx.pageBody(Modifier.weight(1f).fillMaxWidth())
}

internal fun sceneEase(k: Float): Float { val c = k.coerceIn(0f, 1f); return 1f - (1f - c) * (1f - c) * (1f - c) }

// ───────────────────────────── Record crate: the sleeve's back, the record slides out of it ─────────────────────────────

@Composable
internal fun RecordSleeve(ctx: WinSceneCtx) {
    val paper = Color(0xFFEDE4D3); val ink = Color(0xFF2B231B); val red = Color(0xFFB8402E)
    val side = minOf(ctx.maxW, ctx.maxH, 460.dp)
    Box(Modifier.size(side).graphicsLayer {
        // The sleeve slides out of the crate sideways, the way a record is pulled from its neighbours.
        val e = sceneEase(ctx.reveal())
        translationX = (1f - e) * -size.width * 0.15f
        rotationZ = (1f - e) * -4f
        alpha = (ctx.reveal() * 2.2f).coerceIn(0f, 1f)
        shadowElevation = 18.dp.toPx() * e
    }) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(paper)
            // Ring wear from the record inside, and the worn corner.
            drawCircle(ink.copy(alpha = 0.06f), size.minDimension * 0.44f, center, style = Stroke(size.minDimension * 0.05f))
            drawRect(Brush.linearGradient(listOf(Color.Transparent, ink.copy(alpha = 0.10f)), Offset(size.width * 0.8f, size.height * 0.8f), Offset(size.width, size.height)))
        }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(side * 0.26f).background(ink.copy(alpha = 0.08f)).border(1.dp, ink.copy(alpha = 0.3f))) {
                    ctx.cover?.let { AsyncImage(rememberStyleArtRequest(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("SIDE A", color = red, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                    StableFullText(ctx.name, color = ink, fontSize = 19.sp, maxLines = 3, fontWeight = FontWeight.Black, lineHeight = 21.sp)
                    Text(ctx.console + (ctx.year?.let { " · $it" } ?: ""), color = ink.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = FontFamily.Serif)
                }
            }
            Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(2.dp).background(ink))
            if (ctx.page == WinPage.MAIN) {
                // The facts as the track list.
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    sceneFacts(ctx).forEachIndexed { i, (l, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("${i + 1}.", color = red, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                            Text(l, color = ink.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = FontFamily.Serif, modifier = Modifier.width(78.dp))
                            Text(v, color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else ctx.pageBody(Modifier.weight(1f).fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            KeyRow(sceneKeys(ctx)) { a ->
                SceneKey(a, if (a.primary) red else paper, ink, if (a.primary) Color.White else ink, paper, ink.copy(alpha = 0.5f), red, RoundedCornerShape(3.dp), upper = true, size = 11.sp)
            }
        }
    }
}

// ───────────────────────────── Card binder: the card flips to its back ─────────────────────────────

@Composable
internal fun CardBack(ctx: WinSceneCtx) {
    val h = minOf(ctx.maxH, 520.dp)
    // (Update 424: on a short screen the card is a little wider than a card, so its keys take fewer lines and its art keeps room.)
    val w = minOf(ctx.maxW, h * (if (h < 420.dp) 1.3f else 0.74f)).coerceAtLeast(minOf(ctx.maxW, 280.dp))
    // (Update 425: the card's own frame — Layout options › Frame colour (console / game's art, gold once mastered), as the card in
    // the binder wears it — not always gold foil.)
    val pal = rememberCardPalette(ctx.cover, if (ctx.total > 0) ctx.earned to ctx.total else null, ctx.g)
    val foil = pal.frame
    val face = Color(0xFFF7F3EA); val ink = Color(0xFF1E1A14)
    Box(Modifier.width(w).height(h).graphicsLayer {
        // A card turned over in the binder: a half turn about its upright axis (closing turns it back).
        val e = sceneEase(ctx.reveal())
        rotationY = (1f - e) * 90f
        cameraDistance = 14f * density
        alpha = if (ctx.reveal() < 0.02f) 0f else 1f
        shape = RoundedCornerShape(14.dp); clip = true
        shadowElevation = 16.dp.toPx() * e
    }.background(foil).padding(7.dp).clip(RoundedCornerShape(10.dp)).background(face)) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StableFullText(ctx.name, color = ink, fontSize = 17.sp, maxLines = 2, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                if (ctx.total > 0) Text("${ctx.earned}/${ctx.total}", color = Color(0xFFB0302A), fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
            // The art window, the shape of the card's picture box; the art whole inside it.
            // (Update 424, compact 1280×720 on the Thor: the art window at the card's full width pushed the keys off the card's foot.
            // It now takes the room the keys leave, its own shape kept.)
            Box(Modifier.align(Alignment.CenterHorizontally).weight(1f, fill = false).aspectRatio(1.55f).padding(vertical = 6.dp).border(3.dp, foil, RoundedCornerShape(4.dp)).background(Color(0xFF26221C))) {
                (ctx.wide ?: ctx.backdrop ?: ctx.cover)?.let { AsyncImage(rememberStyleArtRequest(it), ctx.name, Modifier.fillMaxSize().padding(3.dp), contentScale = ContentScale.Crop) }
            }
            Text(ctx.console.uppercase() + (ctx.year?.let { "  ·  $it" } ?: ""), color = ink.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Box(Modifier.padding(vertical = 5.dp).fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.3f)))
            SceneFactsBody(ctx, ink.copy(alpha = 0.55f), ink)
            Spacer(Modifier.weight(0.01f))
            Spacer(Modifier.height(6.dp))
            KeyRow(sceneKeys(ctx)) { a ->
                SceneKey(a, if (a.primary) Color(0xFF2A2620) else Color.White, Color(0xFF2A2620), if (a.primary) pal.star else ink, pal.star,
                    ink.copy(alpha = 0.35f), ink, RoundedCornerShape(50), size = 11.sp)
            }
        }
    }
}

// ───────────────────────────── Disc station: a disc player's front panel ─────────────────────────────

@Composable
internal fun DiscPlayerPanel(ctx: WinSceneCtx) {
    val w = minOf(ctx.maxW, 640.dp)
    val metal = Brush.verticalGradient(listOf(Color(0xFFD9DCE0), Color(0xFFB4B8BE), Color(0xFFC7CBD0)))
    val lcdBg = Color(0xFF0F2A2A); val lcd = Color(0xFF7BF2D6)
    Column(Modifier.width(w).heightIn(max = ctx.maxH).graphicsLayer {
        // The tray comes out of the deck: up from below, the panel already in place.
        val e = sceneEase(ctx.reveal())
        translationY = (1f - e) * size.height * 0.25f
        alpha = (ctx.reveal() * 2f).coerceIn(0f, 1f)
        shape = RoundedCornerShape(8.dp); clip = true
        shadowElevation = 14.dp.toPx() * e
    }.background(metal).border(1.dp, Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp)).padding(12.dp)) {
        // The tray slot line and the brand plate.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF2A2D31)))
            Spacer(Modifier.width(10.dp))
            // (Update 425: what the game really comes on — a cartridge game's window doesn't call itself a compact disc.)
            Text(discPanelLabels(ctx.g.platform).first, color = Color(0xFF3A3E44), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(10.dp))
        // The LCD: the title on the display, the facts as its readouts.
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).clip(RoundedCornerShape(6.dp)).background(lcdBg)
            .border(2.dp, Color(0xFF1B1E22), RoundedCornerShape(6.dp)).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(discPanelLabels(ctx.g.platform).second, color = lcd.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                Text(ctx.played.uppercase(), color = lcd, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            StableFullText(ctx.name.uppercase(), color = lcd, fontSize = 18.sp, maxLines = 2, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            SceneFactsBody(ctx, lcd.copy(alpha = 0.55f), lcd, FontFamily.Monospace)
        }
        Spacer(Modifier.height(10.dp))
        // The transport keys.
        KeyRow(sceneKeys(ctx), 8.dp) { a ->
            SceneKey(a, Color(0xFF2E3237), Color(0xFF15181B), Color(0xFFE6E8EB), lcd, Color.Black.copy(alpha = 0.5f), lcd, RoundedCornerShape(4.dp),
                font = FontFamily.Monospace, upper = true, size = 11.sp, height = 32.dp)
        }
    }
}

// ───────────────────────────── Green Core: a glowing system console ─────────────────────────────

@Composable
internal fun CorePanel(ctx: WinSceneCtx) {
    // (Update 425: the layout's own glow colour — Layout options › accent (greenCoreAccent) — not one fixed neon green.)
    val glow = greenCoreAccent()
    val dim = androidx.compose.ui.graphics.lerp(glow, Color.Black, 0.86f)
    val ink = androidx.compose.ui.graphics.lerp(glow, Color.White, 0.78f)
    val w = minOf(ctx.maxW, 600.dp)
    Column(Modifier.width(w).heightIn(max = ctx.maxH).graphicsLayer {
        // Opens from one glowing line, the console's own boot.
        val e = sceneEase(ctx.reveal())
        scaleY = 0.04f + 0.96f * e
        transformOrigin = TransformOrigin(0.5f, 0.5f)
        alpha = (ctx.reveal() * 3f).coerceIn(0f, 1f)
    }.clip(CutCornerShapeCompat).background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(glow, Color.Black, 0.93f).copy(alpha = 0.96f), androidx.compose.ui.graphics.lerp(glow, Color.Black, 0.97f).copy(alpha = 0.98f))))
        .border(1.5.dp, glow.copy(alpha = 0.8f), CutCornerShapeCompat).padding(14.dp)) {
        Text("// SYSTEM  ›  TITLE", color = glow.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        StableFullText(ctx.name, color = ink, fontSize = 22.sp, maxLines = 2, fontWeight = FontWeight.Bold)
        Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(glow, glow.copy(alpha = 0f)))))
        Row(Modifier.weight(1f, fill = false)) {
            // (Update 423: the cover follows the room left under the keys — sized by its width it grew past the row and covered
            // the title once the keys wrapped onto more lines.)
            Box(Modifier.widthIn(max = minOf(w * 0.34f, 180.dp)).aspectRatio(0.75f, matchHeightConstraintsFirst = true).border(1.dp, glow.copy(alpha = 0.5f)).background(dim)) {
                ctx.cover?.let { AsyncImage(rememberStyleArtRequest(it), null, Modifier.fillMaxSize().padding(2.dp), contentScale = ContentScale.Fit) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { SceneFactsBody(ctx, glow.copy(alpha = 0.6f), ink, FontFamily.Monospace) }
        }
        Spacer(Modifier.height(10.dp))
        KeyRow(sceneKeys(ctx)) { a ->
            SceneKey(a, dim, glow, glow, androidx.compose.ui.graphics.lerp(glow, Color.Black, 0.9f), glow.copy(alpha = 0.4f), glow, CutCornerShapeCompat, font = FontFamily.Monospace, upper = true, size = 11.sp)
        }
    }
}

/** Corners cut at 45°, the Green Core's frame. */
private val CutCornerShapeCompat = androidx.compose.foundation.shape.CutCornerShape(10.dp)

// ───────────────────────────── Cube menu: the info on a cube face turning toward you ─────────────────────────────

@Composable
internal fun CubeFace(ctx: WinSceneCtx) {
    val side = minOf(ctx.maxW, ctx.maxH, 470.dp)
    // (Update 425: the cube's own glass colour — Layout options › Cube colour (Features.cubeTint), the theme's accent for "Theme
    // accent" — not a fixed purple; its keys lit in the same colour.)
    val tintPick = com.shelf.launcher.data.Features.cubeTint.state.value
    val tint = if (tintPick == com.shelf.launcher.data.Features.CubeTint.THEME) ctx.accent else Color(tintPick.argb)
    val a = androidx.compose.ui.graphics.lerp(tint, Color.White, 0.25f)
    Box(Modifier.size(side).graphicsLayer {
        // The cube turns a quarter: the face swings in on its left edge (and back out the same way).
        val e = sceneEase(ctx.reveal())
        rotationY = (1f - e) * -90f
        transformOrigin = TransformOrigin(0f, 0.5f)
        cameraDistance = 10f * density
        alpha = if (ctx.reveal() < 0.02f) 0f else 1f
        shape = RoundedCornerShape(10.dp); clip = true
    }.background(Brush.linearGradient(listOf(androidx.compose.ui.graphics.lerp(tint, Color.Black, 0.35f), androidx.compose.ui.graphics.lerp(tint, Color.Black, 0.72f))))
        .border(3.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.4f))), RoundedCornerShape(10.dp))) {
        // The face's bevel and the art faint behind the text.
        (ctx.backdrop ?: ctx.wide)?.let { AsyncImage(rememberStyleArtRequest(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.22f) }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            StableFullText(ctx.name, color = Color.White, fontSize = 21.sp, maxLines = 3, fontWeight = FontWeight.Black)
            Text(ctx.console, color = a, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.weight(1f)) { SceneFactsBody(ctx, Color.White.copy(alpha = 0.6f), Color.White) }
            KeyRow(sceneKeys(ctx)) { k ->
                SceneKey(k, Color.Black.copy(alpha = 0.35f), a, Color.White, if (a.red + a.green + a.blue > 1.8f) Color.Black else Color.White,
                    Color.White.copy(alpha = 0.3f), Color.White, RoundedCornerShape(6.dp))
            }
        }
    }
}

// ───────────────────────────── Pocket layouts: a handheld's small LCD ─────────────────────────────

@Composable
internal fun PocketScreen(ctx: WinSceneCtx) {
    // Four greens, the classic handheld's screen.
    val c0 = Color(0xFF0F380F); val c1 = Color(0xFF306230); val c2 = Color(0xFF8BAC0F); val c3 = Color(0xFF9BBC0F)
    val w = minOf(ctx.maxW, 420.dp)
    val h = minOf(ctx.maxH, 320.dp)
    Box(Modifier.width(w).height(h).graphicsLayer {
        // Power on: a line across the screen opens to the picture.
        val e = sceneEase(ctx.reveal())
        scaleY = 0.03f + 0.97f * e
        scaleX = 0.92f + 0.08f * e
        alpha = (ctx.reveal() * 3f).coerceIn(0f, 1f)
    }.clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 34.dp)).background(Color(0xFF4A4E5A)).padding(10.dp)) {
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(c3).border(3.dp, c0.copy(alpha = 0.8f), RoundedCornerShape(4.dp)).padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(minOf(h * 0.28f, 72.dp)).background(c2).border(2.dp, c0)) {
                    ctx.cover?.let { AsyncImage(rememberStyleArtRequest(it), null, Modifier.fillMaxSize().padding(2.dp), contentScale = ContentScale.Fit, alpha = 0.92f) }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    StableFullText(ctx.name.uppercase(), color = c0, fontSize = 14.sp, maxLines = 2, fontWeight = FontWeight.Bold)
                    Text(ctx.console.uppercase(), color = c1, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.height(4.dp))
            SceneFactsBody(ctx, c1, c0, FontFamily.Monospace)
            Spacer(Modifier.height(4.dp))
            KeyRow(sceneKeys(ctx), 4.dp) { a ->
                SceneKey(a, c2, c0, c0, c3, c1, c0, RoundedCornerShape(2.dp), font = FontFamily.Monospace, upper = true, size = 10.sp, pad = 6.dp, height = 24.dp)
            }
        }
    }
}

// ───────────────────────────── Shelf / stacks / coverflow: a library catalogue card ─────────────────────────────

@Composable
internal fun IndexCard(ctx: WinSceneCtx) {
    val paper = Color(0xFFFBF7EC); val rule = Color(0xFFB9D4EA); val red = Color(0xFFC94A3F); val ink = Color(0xFF2A2A30)
    val w = minOf(ctx.maxW, 470.dp)
    // (Update 424: the card grows with its keys — they wrap onto more lines now — up to the screen; at a fixed 360 dp the facts were
    // squeezed to a sliver cut mid-line on the Thor.)
    Box(Modifier.width(w).heightIn(min = minOf(ctx.maxH, 300.dp), max = ctx.maxH).graphicsLayer {
        // Drawn up out of the catalogue drawer, beside the case taken off the shelf.
        val e = sceneEase(ctx.reveal())
        translationY = (1f - e) * size.height * 0.2f
        alpha = (ctx.reveal() * 2f).coerceIn(0f, 1f)
        shadowElevation = 10.dp.toPx() * e
    }.clip(RoundedCornerShape(3.dp)).background(paper)) {
        Canvas(Modifier.matchParentSize()) {
            // The ruled lines, the red header line and the drawer rod's hole.
            val step = 22.dp.toPx(); var y = 58.dp.toPx()
            while (y < size.height) { drawLine(rule, Offset(0f, y), Offset(size.width, y), 1f); y += step }
            drawLine(red, Offset(0f, 50.dp.toPx()), Offset(size.width, 50.dp.toPx()), 2f)
            drawLine(red.copy(alpha = 0.5f), Offset(44.dp.toPx(), 0f), Offset(44.dp.toPx(), size.height), 1f)
            drawCircle(Color(0xFFD8D2C2), 7.dp.toPx(), Offset(size.width / 2f, size.height - 12.dp.toPx()))
        }
        Column(Modifier.fillMaxWidth().padding(start = 52.dp, end = 14.dp, top = 10.dp, bottom = 24.dp)) {
            StableFullText(ctx.name, color = ink, fontSize = 18.sp, maxLines = 2, fontWeight = FontWeight.Bold)
            Text(ctx.console + (ctx.year?.let { " · $it" } ?: ""), color = ink.copy(alpha = 0.65f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(10.dp))
            SceneFactsBody(ctx, red.copy(alpha = 0.8f), ink, FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            KeyRow(sceneKeys(ctx)) { a ->
                SceneKey(a, if (a.primary) ink else paper, red, if (a.primary) paper else ink, Color.White, ink.copy(alpha = 0.4f), red, RoundedCornerShape(2.dp),
                    font = FontFamily.Monospace, size = 11.sp)
            }
        }
    }
}

// ───────────────────────────── Twin layouts: one window split over the hinge ─────────────────────────────

@Composable
internal fun TwinHinge(ctx: WinSceneCtx) {
    val w = minOf(ctx.maxW, 560.dp)
    val h = minOf(ctx.maxH, 520.dp)
    val a = ctx.accent
    Column(Modifier.width(w).height(h).graphicsLayer {
        // The two halves fold open from the hinge.
        val e = sceneEase(ctx.reveal())
        rotationX = (1f - e) * 40f
        cameraDistance = 12f * density
        alpha = (ctx.reveal() * 2f).coerceIn(0f, 1f)
    }) {
        // Upper half: what the top screen shows (the art, whole).
        Box(Modifier.weight(if (h < 420.dp) 0.26f else 0.36f).fillMaxWidth().clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)).background(Color(0xFF101318))) {
            val art = ctx.wide ?: ctx.backdrop
            if (art != null) {
                AsyncImage(rememberStyleArtRequest(art), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.35f)
                AsyncImage(rememberStyleArtRequest(art), ctx.name, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
            } else ctx.cover?.let { AsyncImage(rememberStyleArtRequest(it), ctx.name, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit) }
        }
        // The hinge.
        Box(Modifier.fillMaxWidth().height(10.dp).background(Brush.verticalGradient(listOf(Color(0xFF2A2F37), Color(0xFF555C67), Color(0xFF2A2F37)))))
        // Lower half: the touch screen's controls.
        // (Update 423: the keys wrap onto three lines now, so the controls half is the bigger one — at 0.54 the facts were
        // squeezed to their labels only.)
        Column(Modifier.weight(if (h < 420.dp) 0.74f else 0.64f).fillMaxWidth().clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)).background(Color(0xFF1A1E25)).padding(12.dp)) {
            StableFullText(ctx.name, color = Color.White, fontSize = 18.sp, maxLines = 2, fontWeight = FontWeight.Bold)
            Text(ctx.console, color = a, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            SceneFactsBody(ctx, Color.White.copy(alpha = 0.55f), Color.White)
            Spacer(Modifier.height(6.dp))
            KeyRow(sceneKeys(ctx)) { k ->
                SceneKey(k, Color.White.copy(alpha = 0.08f), a, Color.White, if (a.red + a.green + a.blue > 1.8f) Color.Black else Color.White,
                    Color.White.copy(alpha = 0.18f), Color.White, RoundedCornerShape(12.dp), height = 30.dp)
            }
        }
    }
}

// ───────────────────────────── Artwork library: the artwork grows into a gallery wall with its placard ─────────────────────────────

@Composable
internal fun GalleryPlacard(ctx: WinSceneCtx) {
    val w = minOf(ctx.maxW, 720.dp)
    val h = minOf(ctx.maxH, 520.dp)
    val wall = Color(0xFFEDEBE6); val ink = Color(0xFF22201C)
    Column(Modifier.width(w).height(h).graphicsLayer {
        // The picture grows out of its place in the library, the wall fading in round it.
        val e = sceneEase(ctx.reveal())
        val s = 0.86f + 0.14f * e
        scaleX = s; scaleY = s
        alpha = (ctx.reveal() * 2f).coerceIn(0f, 1f)
        shape = RoundedCornerShape(4.dp); clip = true
    }.background(wall).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // The work itself, whole, in a dark frame with a mat — never stretched.
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF2B2722)).padding(6.dp).background(Color(0xFFF7F5F0)).padding(10.dp)) {
            if (ctx.page == WinPage.MAIN) {
                val art = ctx.backdrop ?: ctx.wide ?: ctx.cover
                if (art != null) AsyncImage(rememberStyleArtRequest(art), ctx.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else ctx.pageBody(Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(10.dp))
        // The placard.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).background(Color.White).border(1.dp, ink.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                StableFullText(ctx.name, color = ink, fontSize = 15.sp, maxLines = 2, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(ctx.developer, ctx.year, ctx.console).joinToString(", "), color = ink.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = FontFamily.Serif)
                Text(ctx.played + (if (ctx.total > 0) "  ·  ${ctx.earned} / ${ctx.total} trophies" else ""), color = ink.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = FontFamily.Serif)
            }
        }
        Spacer(Modifier.height(8.dp))
        KeyRow(sceneKeys(ctx)) { a ->
            SceneKey(a, if (a.primary) ink else Color.White, Color(0xFF8C6D3F), if (a.primary) Color.White else ink, Color.White, ink.copy(alpha = 0.3f), Color(0xFF8C6D3F), RoundedCornerShape(2.dp))
        }
    }
}

