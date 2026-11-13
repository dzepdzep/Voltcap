package com.shelf.launcher.ui

import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/** Update 471: the facts a window lists, without those it already shows elsewhere ([omit], by label). */
internal fun shownFacts(ctx: WinSceneCtx, omit: Set<String>): List<Pair<String, String>> = shownFacts(sceneFacts(ctx), omit)
internal fun shownFacts(facts: List<Pair<String, String>>, omit: Set<String>): List<Pair<String, String>> = facts.filter { it.first !in omit }

/** Every control of the page: the page's buttons first (Play is the very first), then — on the first page — tools and chips.
 *  (Update 529b, owner: "the last thing that's shown from the options should be the 3 dots, not anything else beside it" - the
 *  round ••• More / Less goes after the chips too: Game shelf's flowing row put "Saves: …" beside it, Flip 2 mlast529b.) */
internal fun sceneKeys(ctx: WinSceneCtx): List<WinAction> =
    (ctx.buttons + (if (ctx.page == WinPage.MAIN) ctx.tools + ctx.chips else emptyList())).let { all ->
        all.filterNot(PopupLab::isMore) + all.filter(PopupLab::isMore)
    }

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
    font: FontFamily? = null, size: TextUnit = 12.sp, pad: Dp = 10.dp, height: Dp = 30.dp, upper: Boolean = false,
    /** The caller already draws the primary key (Play) its own way; else Play gets the lit colour as a tint (update 487). */
    primaryStyled: Boolean = false,
    /** 529l: the word's line height (a tight window at large text: the theme's 24 sp line made each key 54 px tall on the
     *  KONKR at x1.3); unspecified = the theme's, as before. */
    lineHeight: TextUnit = TextUnit.Unspecified,
    /** 529m (owner, #15 6865249152): a full-width bar - the window's width, its icon in a fixed column, the word after it,
     *  growing only when the whole word needs a second line. */
    wide: Boolean = false
) {
    // Update 487 (E7 popup hierarchy, owner-approved): Play is the clear first action in every window. A window that drew
    // every key alike gets Play tinted with its lit colour (and a firm edge) while another key is lit; a lit key's word is
    // always readable on it (≥ 4.5 : 1: Tiles wrote white on lilac at 2.9 : 1), and a lit Play keeps a ring that shows.
    val k = sceneKeyColors(a.primary, primaryStyled, fill, onFill, ink, onInk, edge, onEdge)
    val lab = PopupLab.active.actions // (523t: approved - everyone)
    if (lab && PopupLab.isMore(a)) {
        // (Batch 523 design lab: More as a small round "•••" key beside Details; open (Less) it stays lit-edged.)
        SceneControl(a, Modifier) { on ->
            androidx.compose.foundation.layout.Box(
                Modifier.size(height).clip(androidx.compose.foundation.shape.CircleShape).background(if (on) k.onFill else k.fill.copy(alpha = k.fill.alpha * 0.6f))
                    .border(if (on || a.label == "Less") 2.dp else 1.dp, if (on) k.onEdge else if (a.label == "Less") onFill.copy(alpha = 1f) else k.edge,
                        androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("•••", color = if (on) k.onInk else k.ink, fontSize = size, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
        }
        return
    }
    // (Update 487, E7 tiny controls: no taller layout box — a 48 dp box grew the key rows and cut the keys of the tight Cover
    // details window on the KONKR (popclip k487). Compose already widens a small clickable's touch area to 48 dp
    // (ViewConfiguration.minimumTouchTargetSize) where nothing else is under the finger.)
    SceneControl(a, if (wide) Modifier.fillMaxWidth() else Modifier) { on ->
        Row(
            Modifier.then(if (wide) Modifier.fillMaxWidth() else Modifier).heightIn(min = height).clip(shape).background(if (on) k.onFill else if (lab && PopupLab.isDetails(a)) Color.Transparent else k.fill)
                .border(if (on) 2.dp else if (k.firmEdge) 1.5.dp else 1.dp, if (on) k.onEdge else k.edge, shape).padding(horizontal = pad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (wide) Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
                Icon(a.icon, null, tint = if (on) k.onInk else k.ink, modifier = Modifier.size(if (a.primary) 16.dp else 14.dp))
            } else Icon(a.icon, null, tint = if (on) k.onInk else k.ink, modifier = Modifier.size(if (a.primary) 16.dp else 13.dp))
            Spacer(Modifier.width(if (wide) 10.dp else 5.dp))
            Text(if (upper) sceneLabel(a.label).uppercase() else sceneLabel(a.label), color = if (on) k.onInk else if (a.urgent) Color(0xFFFF8A3D) else k.ink,
                fontSize = if (a.primary) size * 1.15f else size, fontWeight = FontWeight.Bold, fontFamily = font, maxLines = 2,
                modifier = if (wide) Modifier.weight(1f) else Modifier,
                lineHeight = if (lineHeight == TextUnit.Unspecified) TextUnit.Unspecified else if (a.primary) lineHeight * 1.15f else lineHeight)
        }
    }
}

/** [SceneKey] for the windows that already draw Play their own way (a filled Play key). */
@Composable
internal fun SceneKeyStyled(
    a: WinAction, fill: Color, onFill: Color, ink: Color, onInk: Color, edge: Color, onEdge: Color, shape: Shape,
    font: FontFamily? = null, size: TextUnit = 12.sp, pad: Dp = 10.dp, height: Dp = 30.dp, upper: Boolean = false
) = SceneKey(a, fill, onFill, ink, onInk, edge, onEdge, shape, font, size, pad, height, upper, primaryStyled = true)

internal class SceneKeyColors(val fill: Color, val onFill: Color, val ink: Color, val onInk: Color, val edge: Color, val onEdge: Color, val firmEdge: Boolean)

/** Update 487: the colours a scene key is drawn in (pure; SceneKey487Test). */
internal fun sceneKeyColors(
    primary: Boolean, primaryStyled: Boolean, fill: Color, onFill: Color, ink: Color, onInk: Color, edge: Color, onEdge: Color
): SceneKeyColors {
    // A lit word must read on the lit colour (only judged where that colour is solid; a see-through one depends on the scene).
    val onInkR = if (onFill.alpha >= 0.9f) com.shelf.launcher.ui.theme.textOn(onFill, onInk) else onInk
    val inkR = if (fill.alpha >= 0.9f) com.shelf.launcher.ui.theme.textOn(fill, ink) else ink
    // The ring of a lit key must show against the key itself (Twin: the accent ring on the accent key vanished).
    val ringR = if (onFill.alpha >= 0.9f && onEdge.alpha >= 0.5f && com.shelf.launcher.ui.theme.contrastRatio(onEdge, onFill) < 1.4f) onInkR else onEdge
    if (!primary || primaryStyled) return SceneKeyColors(fill, onFill, inkR, onInkR, edge, ringR, false)
    // Play in a window that draws all keys alike: tinted with the lit colour, its edge in that colour.
    val tintFill = if (fill.alpha >= 0.9f) androidx.compose.ui.graphics.lerp(fill, onFill.copy(alpha = 1f), 0.28f)
        else onFill.copy(alpha = (fill.alpha + 0.24f).coerceAtMost(0.5f))
    val tintInk = if (tintFill.alpha >= 0.9f) com.shelf.launcher.ui.theme.textOn(tintFill, ink) else ink
    return SceneKeyColors(tintFill, onFill, tintInk, onInkR, onFill.copy(alpha = 1f), ringR, true)
}

/**
 * Update 422: a key's word without a symbol the layout's look puts in front of it ("▶ continue" in Hero / Disc looks — the
 * key already draws its icon, so it read "▶ ▶ continue"), first letter capitalised.
 */
internal fun sceneLabel(l: String): String = l.trimStart { !it.isLetterOrDigit() }.ifEmpty { l }.replaceFirstChar { it.uppercase() }

/** The first page's facts, label over value in two columns, or the other page's body. */
@Composable
internal fun ColumnScope.SceneFactsBody(ctx: WinSceneCtx, label: Color, value: Color, font: FontFamily? = null, upperLabels: Boolean = true,
    omit: Set<String> = emptySet(), inline: Boolean = false,
    /** Update 508: a larger type for a window that has room to spare on a small screen (Tile home on the GT78); 1 = as before. */
    scale: Float = 1f) {
    // (529m: the copy of a page change draws its own page - LocalShownPage.)
    if ((LocalShownPage.current ?: ctx.page) == WinPage.MAIN) {
        // (Update 424: a fact cut at the window's edge fades out — it scrolls — instead of ending in half a line.)
        val sc = rememberScrollState()
        // (Update 467, popup gate: with large text the keys can leave the facts a sliver that shows the tops of one row
        // — "CONSOLE YEAR" cut in half on the KONKR at 1.3×. Under one whole row's room the box gives its space away
        // instead; the facts stay on the Details page.)
        val minRow = with(androidx.compose.ui.platform.LocalDensity.current) { (9.sp.toDp() * scale + 14.sp.toDp() * scale + 8.dp).roundToPx() }
        // (Update 471, owner review of the 470 PDF: with More open the facts box showed "MADE BY" / "GENRE" and their values
        // were scrolled away under the keys — the actions fitted, the information didn't. [omit] leaves out facts the window
        // already shows (Cover details' header line has the console and year); [inline] puts label and value on one line.
        // A window that doesn't ask for it gets it too when its two-column facts don't fit the room they have: measured once,
        // switched for the next frame, again when the window's keys change (More / Less).)
        var viewH by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
        var factsH by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
        var tight by androidx.compose.runtime.remember(ctx.g.stableKey, ctx.tools.size, ctx.chips.size) { androidx.compose.runtime.mutableStateOf(false) }
        val asLines = inline || tight
        androidx.compose.runtime.LaunchedEffect(viewH, factsH, asLines) { if (!asLines && viewH > 0 && factsH > viewH) tight = true }
        // (Update 476, D260: a story cut by the room faded out mid-sentence with nothing saying there was more. While the box
        // can scroll further, a small cue at its bottom edge says where the whole story is.)
        // (529m, owner #15: the cue lay over the facts it cut - "Full story on Details ›" across "Action" and "PLAYED" on the
        // KONKR at x1.3. It has its own line under the box now; the box gives it the room.)
        Column(Modifier.weight(1f, fill = false)) {
        Column(Modifier.weight(1f, fill = false).layout { m, c ->
            if (c.hasBoundedHeight && c.maxHeight < minRow) layout(0, 0) {} else { val p = m.measure(c); layout(p.width, p.height) { p.place(0, 0) } }
        }.onSizeChanged { viewH = it.height }.sceneFadeEdges(sc).verticalScroll(sc)) {
            val facts = shownFacts(ctx, omit)
            Column(Modifier.onSizeChanged { factsH = it.height }) {
            if (asLines) facts.forEach { (l, v) ->
                Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.Top) {
                    // (Its own line height — the theme's tall one made every row half as tall again as its value: 470's KONKR
                    // run showed three facts of four.)
                    Text(if (upperLabels) l.uppercase() else l, color = label, fontSize = 9.sp * scale, lineHeight = 11.sp * scale, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = font,
                        softWrap = false, modifier = Modifier.width(78.dp).padding(top = 3.dp))
                    Text(v, color = value, fontSize = 12.sp * scale, lineHeight = 14.sp * scale, fontWeight = FontWeight.SemiBold, fontFamily = font, modifier = Modifier.weight(1f))
                }
            } else facts.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    pair.forEach { (l, v) ->
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(if (upperLabels) l.uppercase() else l, color = label, fontSize = 9.sp * scale, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = font, softWrap = false)
                            Text(v, color = value, fontSize = 12.sp * scale, lineHeight = 14.sp * scale, fontWeight = FontWeight.SemiBold, fontFamily = font)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            }
            ctx.summary?.let { Text(it, color = value.copy(alpha = 0.78f), fontSize = 11.sp * scale, lineHeight = 14.sp * scale, fontFamily = font, modifier = Modifier.padding(top = 2.dp)) }
        }
        if (ctx.summary != null && viewH > 0 && sc.canScrollForward) FullText(STORY_CUE, color = label, fontSize = 9.sp,
            fontWeight = FontWeight.Bold, fontFamily = font,
            modifier = Modifier.align(Alignment.End).padding(top = 2.dp).background(sceneCueBack(value)).padding(horizontal = 4.dp))
        }
    } else ctx.pageBody(Modifier.weight(1f).fillMaxWidth())
}

/** Update 476: the cue's backing — the window's ground behind light ink (dark windows), a pale one behind dark ink. */
internal fun sceneCueBack(ink: Color): Color =
    if (ink.red * 0.3f + ink.green * 0.59f + ink.blue * 0.11f > 0.5f) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.7f)

internal fun sceneEase(k: Float): Float { val c = k.coerceIn(0f, 1f); return 1f - (1f - c) * (1f - c) * (1f - c) }

// ───────────────────────────── Record crate: the sleeve's back, the record slides out of it ─────────────────────────────

@Composable
internal fun RecordSleeve(ctx: WinSceneCtx) {
    val paper = Color(0xFFEDE4D3); val ink = Color(0xFF2B231B)
    // (Update 505: the sleeve's print — SIDE A, the track numbers, Play and the key you're on — in the Crate home's accent (the
    // theme's, the game's or this layout's own colours, as its turntable and rings), made readable on the paper; was a fixed red.)
    val red = sceneLit(com.shelf.launcher.data.HomeLayout.CRATE, com.shelf.launcher.ui.theme.ShelfTheme.accent, paper)
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
                    ctx.cover?.let { SmartArt(it, null, Modifier.fillMaxSize(), maxCrop = SmartFill.maxCropFor(it, ctx.cover)) }
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
                SceneKey(a, if (a.primary) red else paper, ink, if (a.primary) com.shelf.launcher.ui.theme.textOn(red, Color.White) else ink, paper, ink.copy(alpha = 0.5f), red, RoundedCornerShape(3.dp), upper = true, size = 11.sp)
            }
        }
    }
}

// ───────────────────────────── Card binder: the card flips to its back ─────────────────────────────

// (Update 507: CardBack replaced — WinSceneFamilies.kt.)

// ───────────────────────────── Disc station: a disc player's front panel ─────────────────────────────

/**
 * Update 506 (owner review of the 505 visual report, P0): the deck's panel opens beside the player instead of a dark slab over
 * it — the player and its turning disc stay in view on the left (the dim is light there, sceneVeil), the panel slides out of
 * the deck's right edge where the home's info panel stands, joined to it by the tray, and goes back in the same way on B.
 * Its words are the deck's: the disc kind the home shows (never "CARTRIDGE" on a disc player), DISC 1 · TRACK 01.
 * [discStationDeckRightDp] mirrors the home's own sizing (StyleDiscStation.DiscStation).
 */
@Composable
internal fun DiscPlayerPanel(ctx: WinSceneCtx) {
    // (Update 504, popup identity: the deck in Disc Station's own look — the home's smoked glass panels and the accent its
    // Play pill and focus rings use (the owner's layout / theme accent) — instead of a silver deck with a fixed teal display.)
    val accent = com.shelf.launcher.ui.theme.ShelfTheme.accent
    val metal = Brush.verticalGradient(listOf(Color(0xFF1E2535), Color(0xFF141A2A), Color(0xFF0C111C)))
    val lcdBg = Color(0xFF070B12)
    val lcd = androidx.compose.runtime.remember(accent) { com.shelf.launcher.ui.theme.legibleArtAccent(accent, lcdBg, lcdBg) }
    val labels = discPanelLabels(ctx.g.platform)
    // (Beside an inspected piece the room is the window's part only: the panel fills it, as before.)
    val geo = if (ctx.inspecting) 0f to minOf(ctx.maxW.value, 640f) else discPanelGeometry(ctx.maxW.value, ctx.maxH.value)
    val left = geo.first.dp; val w = geo.second.dp
    val gap = 12.dp
    val rim = androidx.compose.ui.graphics.lerp(Color.White.copy(alpha = 0.10f), accent, 0.35f)
    Box(Modifier.width(ctx.maxW).height(ctx.maxH)) {
        // The tray: the panel's link to the deck, out of the deck's edge first and back in last.
        Box(Modifier.align(Alignment.CenterStart).padding(start = (left - gap).coerceAtLeast(0.dp)).width(gap + 10.dp).height(14.dp).graphicsLayer {
            val e = sceneEase((ctx.reveal() / 0.45f).coerceIn(0f, 1f))
            scaleX = e; transformOrigin = TransformOrigin(0f, 0.5f); alpha = e
        }.clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(Color(0xFF2A3346), Color(0xFF0B0F18))))
            .border(1.dp, rim, RoundedCornerShape(3.dp)))
        Column(Modifier.align(Alignment.CenterStart).padding(start = left).width(w).heightIn(max = ctx.maxH).graphicsLayer {
            // Out of the deck's edge: the panel slides right from behind the tray as the tray reaches it; back in on close.
            val e = sceneEase(((ctx.reveal() - 0.15f) / 0.85f).coerceIn(0f, 1f))
            translationX = (1f - e) * -48.dp.toPx()
            alpha = e
            shape = RoundedCornerShape(8.dp); clip = true
            shadowElevation = 14.dp.toPx() * e
        }.background(metal).border(1.dp, rim, RoundedCornerShape(8.dp)).padding(12.dp)) {
            // The tray slot line and the disc-kind plate.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NOW LOADED", color = lcd.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF05080D)))
                Spacer(Modifier.width(10.dp))
                Text(labels.first, color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }
            Spacer(Modifier.height(10.dp))
            // The LCD: the title on the display, the facts as its readouts.
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).clip(RoundedCornerShape(6.dp)).background(lcdBg)
                .border(2.dp, Color(0xFF232B3B), RoundedCornerShape(6.dp)).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(labels.second, color = lcd.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.weight(1f))
                    Text(ctx.played.uppercase(), color = lcd, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                StableFullText(ctx.name.uppercase(), color = lcd, fontSize = 18.sp, maxLines = 3, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                SceneFactsBody(ctx, lcd.copy(alpha = 0.55f), lcd, FontFamily.Monospace)
            }
            Spacer(Modifier.height(10.dp))
            // The transport keys.
            KeyRow(sceneKeys(ctx), 8.dp) { a ->
                SceneKey(a, Color(0xFF263044), Color(0xFF121826), Color(0xFFE6E8EB), lcd, Color.Black.copy(alpha = 0.5f), lcd, RoundedCornerShape(4.dp),
                    font = FontFamily.Monospace, upper = true, size = 11.sp, height = 32.dp)
            }
        }
    }
}

/**
 * Pure (Update 506): where Disc station's panel stands in the window's room ([maxW] x [maxH] dp, the screen less 22 / 14 dp
 * margins) — (left, width) in dp. It starts just right of the home's deck (StyleDiscStation: the deck is min(52 % of the
 * width, the height less its margins) wide, 14 dp in, 8 dp on short screens); never narrower than 300 dp (it then overlaps the
 * deck's edge rather than squeeze its keys).
 */
internal fun discPanelGeometry(maxW: Float, maxH: Float): Pair<Float, Float> {
    val screenW = maxW + 44f; val screenH = maxH + 28f
    val pad = if (screenH < 330f) 8f else 14f
    val deckRight = pad + minOf(screenW * 0.52f, screenH - pad * 2) - 22f
    val minW = minOf(300f, maxW)
    val left = (deckRight + 12f).coerceIn(0f, maxW - minW)
    return left to (maxW - left).coerceAtMost(620f)
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
                ctx.cover?.let { SmartArt(it, null, Modifier.fillMaxSize().padding(2.dp), well = dim) }
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

// (Update 507: CubeFace replaced — WinSceneFamilies.kt.)

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
                    ctx.cover?.let { SmartArt(it, null, Modifier.fillMaxSize().padding(2.dp).graphicsLayer { alpha = 0.92f }, well = c2) }
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

// (Twin layouts: TwinHinge moved to WinSceneTwin.kt — v0.6.66, D142.)

// ───────────────────────────── Artwork library: the artwork grows into a gallery wall with its placard ─────────────────────────────

@Composable
internal fun GalleryPlacard(ctx: WinSceneCtx) {
    val w = minOf(ctx.maxW, 720.dp)
    val h = minOf(ctx.maxH, 520.dp)
    // (Update 505: the Art library's own dark, themed gallery — the wall its glass panels are, the frames darker, the key you're on
    // in the theme's accent — instead of a fixed pale museum wall. The mat and the white placard stay: the work is shown whole.)
    val wall = themeDark(Color(0xFF141B27), 0.6f); val ink = Color(0xFF22201C)
    val lit = sceneLit(com.shelf.launcher.data.HomeLayout.ARTLIB, com.shelf.launcher.ui.theme.ShelfTheme.accent, wall)
    Column(Modifier.width(w).height(h).graphicsLayer {
        // The picture grows out of its place in the library, the wall fading in round it.
        val e = sceneEase(ctx.reveal())
        val s = 0.86f + 0.14f * e
        scaleX = s; scaleY = s
        alpha = (ctx.reveal() * 2f).coerceIn(0f, 1f)
        shape = RoundedCornerShape(4.dp); clip = true
    }.background(wall).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // The work itself, whole, in a dark frame with a mat — never stretched.
        Box(Modifier.weight(1f).fillMaxWidth().background(sceneDark(wall, 0.02f)).border(1.dp, lit.copy(alpha = 0.35f)).padding(6.dp).background(Color(0xFFF7F5F0)).padding(10.dp)) {
            if (ctx.page == WinPage.MAIN) {
                val art = ctx.backdrop ?: ctx.wide ?: ctx.cover
                // (Batch 523: the work in a frame of ITS shape - the mat hugs it instead of a whole picture floating in a wide mat.)
                if (art != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    var shape by remember(art) { mutableStateOf(CoverAspects.rawOf(art)) }
                    AsyncImage(rememberStyleArtRequest(art), ctx.name,
                        Modifier.then(shape?.let { Modifier.aspectRatio(it.coerceIn(0.4f, 3f), matchHeightConstraintsFirst = true) } ?: Modifier.fillMaxSize()),
                        contentScale = ContentScale.Fit,
                        onSuccess = { st -> val sz = st.painter.intrinsicSize; if (sz.width > 0f && sz.height > 0f && sz.width.isFinite()) { CoverAspects.note(art, sz.width, sz.height); shape = sz.width / sz.height } })
                }
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
            SceneKey(a, if (a.primary) lit else Color.White, lit, if (a.primary) com.shelf.launcher.ui.theme.textOn(lit, Color.White) else ink, com.shelf.launcher.ui.theme.textOn(lit, Color.White),
                Color.White.copy(alpha = 0.3f), Color.White, RoundedCornerShape(2.dp))
        }
    }
}

