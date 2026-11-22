package com.shelf.launcher.ui

import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.getValue
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/*
 * Update 422 (popup audit, the last shared-window layouts): one composition each for the home layouts that still opened the
 * generic window. Same contract as WinScenesMore.kt: Play / Continue first (ctx.playFocus via SceneControl), every page's
 * buttons and every tool / chip reachable with the D-pad, other pages drawn with ctx.pageBody, B closes (GameWindow), no
 * text cut with "…" (StableFullText, sideways-scrolling key rows / scrolling key columns). Layout Studio (user-built) keeps
 * the standard window in the user's chosen look, and the TV room keeps its own on-the-TV window.
 */

private fun onColor(c: Color) = if (c.luminance() > 0.5f) Color(0xFF111111) else Color.White

/**
 * A column of keys (menus that run down the screen: Cross menu, Side menu); scrolls when it doesn't fit.
 * (673g, owner brief 15 Nov - "Vertical menus, WWE SvR 2011 style": every item its own key of the same width and the same height
 * - the tallest item's - edges in line, the gaps even; status lines (Save sync not set up) under the keys, not among them.)
 */
@Composable
private fun KeyColumn(keys: List<WinAction>, modifier: Modifier, scroll: Boolean = true, key: @Composable (WinAction) -> Unit) {
    val actions = keys.filterNot { it.status }
    val status = keys.filter { it.status }
    Column(if (scroll) modifier.verticalScroll(rememberScrollState()) else modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        UniformKeyColumn(actions, 4.dp, fill = true, key = key)
        if (status.isNotEmpty()) androidx.compose.runtime.CompositionLocalProvider(LocalKeyAsStatus provides true) { status.forEach { key(it) } }
    }
}

/**
 * A full-width list row key (icon + word), lit as a band.
 * (673g, owner brief 15 Nov: each item of a vertical menu is its own rectangular key - a faint fill and edge at rest, the band
 * when lit - at the size its column gives every item; a status line (WinAction.status) is words only.)
 */
@Composable
private fun ListKey(a: WinAction, ink: Color, lit: Color, litInk: Color, height: androidx.compose.ui.unit.Dp = 38.dp, font: FontFamily? = null) {
    if (LocalKeyAsStatus.current) {
        SceneControl(a, Modifier.fillMaxWidth()) { on ->
            val c = if (a.urgent) Color(0xFFFF8A3D) else ink.copy(alpha = ink.alpha * 0.8f)
            // (674a: a status line can be tapped (Save sync not set up opens its setup) - 24 dp at least, the touch gate's minimum; f673l Side.)
            Row(Modifier.fillMaxWidth().heightIn(min = 24.dp).background(if (on) lit.copy(alpha = 0.35f) else Color.Transparent).padding(horizontal = 12.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(a.icon, null, tint = c, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(8.dp))
                Text(sceneLabel(a.label), color = c, fontSize = 12.sp, fontFamily = font, maxLines = 3)
            }
        }
        return
    }
    val uniform = LocalUniformKey.current
    val box = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    SceneControl(a, Modifier.fillMaxWidth()) { on ->
        Row(Modifier.fillMaxWidth().then(if (uniform) Modifier.fillMaxHeight() else Modifier).heightIn(min = height).clip(box)
            .background(if (on) lit else ink.copy(alpha = 0.06f)).border(1.dp, if (on) lit else ink.copy(alpha = 0.16f), box)
            .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(a.icon, null, tint = if (on) litInk else ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text(sceneLabel(a.label), color = if (on) litInk else if (a.urgent) Color(0xFFFF8A3D) else ink, fontSize = if (a.primary) 16.sp else 14.sp,
                fontWeight = if (a.primary || on) FontWeight.SemiBold else FontWeight.Normal, fontFamily = font)
        }
    }
}

// ───────────── Cross menu: the options column sliding in at the right, over the wave ─────────────

@Composable
internal fun CrossOptions(ctx: WinSceneCtx) {
    val w = minOf(ctx.maxW * 0.46f, 380.dp).coerceAtLeast(minOf(ctx.maxW, 280.dp))
    Box(Modifier.fillMaxSize()) {
        // The title and facts stay on the left, as the column of the selected item would.
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(end = w + 16.dp).graphicsLayer { alpha = ctx.reveal().coerceIn(0f, 1f) }) {
            StableFullText(ctx.name, color = Color.White, fontSize = 22.sp, maxLines = 3, fontWeight = FontWeight.Light)
            Box(Modifier.padding(vertical = 6.dp).fillMaxWidth(0.8f).height(1.dp).background(Color.White.copy(alpha = 0.5f)))
            if (ctx.page == WinPage.MAIN) SceneFactsColumn(ctx, Color.White.copy(alpha = 0.55f), Color.White)
            else ctx.pageBody(Modifier.fillMaxWidth().heightIn(max = ctx.maxH * 0.75f))
        }
        // (Update 505: the column in the home's own wash — Dusk / Ocean / Ember, or the theme's — its lit row in the wash's light
        // colour (your accent when this layout has its own colours), readable on the column; was always black and white.)
        val wash = crossWash(com.shelf.launcher.data.Features.crossTint.state.value, com.shelf.launcher.ui.theme.ShelfTheme.colors.background,
            com.shelf.launcher.ui.theme.ShelfTheme.accent)
        val deep = sceneDark(wash?.first ?: Color.Black)
        val lit = sceneLit(com.shelf.launcher.data.HomeLayout.CROSS, wash?.second ?: com.shelf.launcher.ui.theme.ShelfTheme.accent, deep)
        val band = androidx.compose.ui.graphics.lerp(deep, lit, 0.34f)
        Column(Modifier.align(Alignment.CenterEnd).width(w).fillMaxHeight().graphicsLayer {
            // The options panel slides in from the screen's right edge (and back out).
            translationX = (1f - sceneEase(ctx.reveal())) * size.width
        }.background(Brush.horizontalGradient(listOf(deep.copy(alpha = 0.0f), deep.copy(alpha = 0.74f), deep.copy(alpha = 0.88f))))
            .padding(start = 28.dp, end = 12.dp), verticalArrangement = Arrangement.Center) {
            KeyColumn(sceneKeys(ctx), Modifier.fillMaxWidth()) { a ->
                ListKey(a, Color.White.copy(alpha = 0.85f), band, com.shelf.launcher.ui.theme.textOn(band, Color.White))
            }
        }
    }
}

/** The facts as one column (label, value) — for narrow scenes. */
@Composable
private fun SceneFactsColumn(ctx: WinSceneCtx, label: Color, value: Color, font: FontFamily? = null) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        sceneFacts(ctx).forEach { (l, v) ->
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                Text(l, color = label, fontSize = 11.sp, fontFamily = font, modifier = Modifier.width(92.dp))
                Text(v, color = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = font)
            }
        }
    }
}

// ───────────── Live tiles: flat tiles turning in like a turnstile ─────────────

@Composable
internal fun LiveTilesPanel(ctx: WinSceneCtx) {
    val a = ctx.accent
    val w = minOf(ctx.maxW, 620.dp)
    Column(Modifier.width(w).heightIn(max = ctx.maxH).graphicsLayer {
        val e = sceneEase(ctx.reveal())
        rotationY = (1f - e) * 70f
        transformOrigin = TransformOrigin(0f, 0.5f)
        cameraDistance = 10f * density
        alpha = if (ctx.reveal() < 0.02f) 0f else 1f
        // (Update 489, D285: turned text drawn flat — see flatTextFor3d.)
        flatTextFor3d()
    }.background(Color(0xFF111111).copy(alpha = 0.94f)).padding(16.dp)) {
        Text(ctx.console.lowercase(), color = a, fontSize = 13.sp, fontWeight = FontWeight.Light)
        StableFullText(ctx.name.lowercase(), color = Color.White, fontSize = 30.sp, maxLines = 2, fontWeight = FontWeight.Light, lineHeight = 32.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.weight(1f, fill = false)) {
            Box(Modifier.width(minOf(w * 0.3f, 160.dp)).aspectRatio(1f).background(a)) {
                (ctx.cover ?: ctx.wide)?.let { SmartArt(it, null, Modifier.fillMaxSize(), maxCrop = SmartFill.maxCropFor(it, ctx.cover)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { SceneFactsBody(ctx, Color.White.copy(alpha = 0.5f), Color.White, upperLabels = false) }
        }
        Spacer(Modifier.height(10.dp))
        // The keys as small flat tiles in the accent.
        KeyRow(sceneKeys(ctx), 4.dp) { k ->
            SceneKeyStyled(k, if (k.primary) a else Color(0xFF2A2A2A), Color.White, if (k.primary) onColor(a) else Color.White, Color(0xFF111111),
                Color.Transparent, Color.White, RectangleShape, height = 40.dp)
        }
    }
}

// ───────────── Dashboard: a widget card rising from the row ─────────────

@Composable
internal fun DashCard(ctx: WinSceneCtx) {
    val a = ctx.accent
    val w = minOf(ctx.maxW, 640.dp)
    val surface = Color(0xFF1C1F26)
    Column(Modifier.width(w).heightIn(max = ctx.maxH).graphicsLayer {
        val e = sceneEase(ctx.reveal())
        translationY = (1f - e) * size.height * 0.18f
        alpha = (ctx.reveal() * 2f).coerceIn(0f, 1f)
        shape = RoundedCornerShape(12.dp); clip = true
        shadowElevation = 16.dp.toPx() * e
    }.background(surface).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))) {
        // Header strip with the art.
        // (674h, D332 x1.3: on a short window (KONKR; Flip 2 with More open) the art took 30 % first and the three stat widgets
        // were laid out 0-1 px high. There the strip is a band of 16 %; the stats keep their room.)
        // (674j, KONKR x1.3 k674i with More open: still short of the stats' values - under 400 dp the band goes.)
        if (ctx.maxH >= 400.dp) Box(Modifier.fillMaxWidth().height(minOf(ctx.maxH * (if (ctx.maxH < 440.dp) 0.16f else 0.3f), 150.dp))) {
            (ctx.wide ?: ctx.backdrop ?: ctx.cover)?.let { SmartArt(it, null, Modifier.fillMaxSize(), request = rememberBackdropRequest(it), maxCrop = SmartFill.maxCropFor(it, ctx.cover)) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, surface))))
        }
        // (Update 476, D255: the name was drawn over the art's lower part — where the game's own logo usually sits — and the
        // two titles fought. The name now sits just under the art, on the card; the art strip is unchanged.)
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp)) {
            StableFullText(ctx.name, color = Color.White, fontSize = 20.sp, maxLines = 2, fontWeight = FontWeight.Bold)
            Text(if (ctx.page == WinPage.MAIN) ctx.console else ctx.console + "  ·  " + dashPageName(ctx.page), color = a, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        // (682w, P2 batch 3: the dashboard's other tabs - Overview, Progress, Settings - WinPagesDash.kt, in the card's room above
        // the keys; the tab's name beside the console line.)
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).weight(1f, fill = ctx.page != WinPage.MAIN)) {
            if (ctx.page == WinPage.MAIN) {
                // Three stat widgets.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Played" to ctx.played, "Last played" to (ctx.last ?: "Never"), "Trophies" to (if (ctx.total > 0) "${ctx.earned} / ${ctx.total}" else "None")).forEach { (l, v) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.05f)).padding(8.dp)) {
                            Text(l, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                            Text(v, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                // (Update 476, D255: the story ended after 3 lines, cut silently mid-sentence. Whole lines, and a cue to Details
                // when there's more.)
                ctx.summary?.let { StoryWithCue(it, Color.White.copy(alpha = 0.7f), a, 11.sp, 14.sp, 3, Modifier.padding(top = 8.dp).fillMaxWidth()) }
            } else NativePages(ctx, Modifier.fillMaxWidth().weight(1f), label = "dashPage", move = ::dashMove) { pg, pageLive ->
                DashPage(ctx, pg, pageLive, Modifier.fillMaxSize())
            }
        }
        Box(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
            KeyRow(sceneKeys(ctx), slots = sceneKeySlots(ctx)) { k ->
                SceneKeyStyled(k, if (k.primary) a else Color.White.copy(alpha = 0.06f), Color.White, if (k.primary) onColor(a) else Color.White, Color(0xFF111111),
                    Color.White.copy(alpha = 0.1f), a, RoundedCornerShape(8.dp), height = 34.dp)
            }
        }
    }
}

// ───────────── Side menu: a drawer sliding out from the menu's edge ─────────────

/** (Update 514) The drawer's name (two lines), its console line and the gap under them — what sits between picture and menu. */
private val SIDE_DRAWER_TEXT = 84.dp
/** (674n) What KeyColumn draws between keys, and the room one status line under them takes (24 dp touch size, one 12 sp line). */
private val SIDE_KEY_GAP = 4.dp
private val SIDE_STATUS_H = 24.dp
private val SIDE_ROUNDING = 2.dp
/** (674n, KONKR x1.0 window 360 dp: 6 keys + a status line need 228 dp - with the picture held at 64 dp they were 12 dp short and
 *  the menu scrolled. The picture now gives up height down to 44 dp before any key scrolls.) */
internal val SIDE_ART_MIN = 44.dp

@Composable
internal fun SideDrawer(ctx: WinSceneCtx) {
    val a = ctx.accent
    val w = minOf(ctx.maxW * 0.5f, 420.dp).coerceAtLeast(minOf(ctx.maxW, 300.dp))
    // (Update 514, RP Nova 4:3, 480 dp high: the picture at the top took its full 2.1 : 1 (152 dp) and the menu below scrolled —
    // Inspect sat under the drawer's edge. On a short screen the picture gives up height first, down to 64 dp, so every row
    // fits at its smallest readable height (30 dp); where there is room (the Thor) it keeps its whole 2.1 : 1.)
    val keys = sceneKeys(ctx)
    // (674n, KONKR x1.0 k674m: Save sync cut 13 px at the drawer's bottom - the fit counted the status line as a 30 dp row and
    // the gaps as 2 dp, but KeyColumn draws 4 dp gaps and the status line under the keys; the list fell 1 dp short of fitting,
    // scrolled, and its "whole rows" were whole only by the wrong sums. The sums now match what KeyColumn draws.)
    val nAct = keys.count { !it.status }
    val nStat = keys.size - nAct
    val statusNeed = (SIDE_STATUS_H + SIDE_KEY_GAP) * nStat
    val rowsNeed = 30.dp * nAct + SIDE_KEY_GAP * (nAct - 1).coerceAtLeast(0) + statusNeed + SIDE_ROUNDING
    // (682y: on the drawer's submenus the picture steps back to a strip - the submenu gets the room; on a short screen
    // (RP Classic x1.3: the first achievement was cut in half under the drawer's rows) only a top margin stays.)
    val headerH = if (ctx.page != WinPage.MAIN) (if (ctx.maxH < 460.dp) 14.dp else maxOf(w / 5f, SIDE_ART_MIN)) else minOf(w / 2.1f, (ctx.maxH - rowsNeed - SIDE_DRAWER_TEXT).coerceAtLeast(SIDE_ART_MIN))
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.CenterStart).width(w).fillMaxHeight().graphicsLayer {
            translationX = -(1f - sceneEase(ctx.reveal())) * size.width
            shadowElevation = 20.dp.toPx()
        }.background(Color(0xFF15171C)).border(1.dp, Color.White.copy(alpha = 0.06f))) {
            Box(Modifier.fillMaxWidth().height(headerH)) {
                (ctx.wide ?: ctx.backdrop ?: ctx.cover)?.let { SmartArt(it, null, Modifier.fillMaxSize(), request = rememberBackdropRequest(it), maxCrop = SmartFill.maxCropFor(it, ctx.cover)) }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF15171C)))))
            }
            Column(Modifier.padding(horizontal = 14.dp)) {
                StableFullText(ctx.name, color = Color.White, fontSize = 19.sp, maxLines = 2, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(ctx.console, ctx.year, ctx.played).joinToString(" · "), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            // (682y, P2 batch 4: the drawer's submenus - ABOUT, ACHIEVEMENTS, OPTIONS - WinPagesSide.kt.)
            if (ctx.page != WinPage.MAIN) NativePages(ctx, Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp), label = "sidePage", move = ::sideMove) { pg, pageLive ->
                SidePage(ctx, pg, pageLive, Modifier.fillMaxSize())
            }
            // The menu's own rows, with the accent bar on the one you're on.
            // (Update 488, D284: the rows share the drawer's room — Back was cut 13 px under the Thor's 1080p screen edge; 40 dp
            // when there is room, down to 30 dp when not, scrolling only below that.)
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().then(if (ctx.page == WinPage.MAIN) Modifier.weight(1f) else Modifier.heightIn(min = if (nAct < 2) 40.dp * 2 + SIDE_KEY_GAP else 0.dp, max = 140.dp))) {
            // (682y, KONKR / RPC pk14 / pr14: on Options - Play alone - the rows' box was one row shorter and Play sat a row lower
            // than on Details / Achievements. A page keeps two rows' room, Play at the top of it.)
            // (674o, Flip 2 x1.0 f674o: the fit filled the room to the last dp and pixel rounding left the status line 51 px -
            // under its 24 dp (54 px) touch size. 2 dp stay free for rounding.)
            val rowRoom = maxHeight - statusNeed - SIDE_ROUNDING
            val rowH = fitRowHeight(rowRoom, nAct, SIDE_KEY_GAP, 40.dp, 30.dp)
            // (Update 488b: with many rows (the Thor's 8) the list scrolls — then a whole number of rows fills the room, so
            // the last one shown is never cut, p488d: Back cut 13 px.)
            val snapH = if (rowH == null) snapRowHeight(maxHeight, nAct, SIDE_KEY_GAP, 40.dp, 30.dp) else null
            KeyColumn(keys, Modifier.fillMaxWidth(), scroll = rowH == null) { k ->
                // (674k, Flip 2 x1.0 f674j: the drawer drew its status line as one of its rows, at the fitted height - 38 px, under
                // the 24 dp touch size. A status line is words under the menu, at least 24 dp.)
                if (LocalKeyAsStatus.current) SceneControl(k, Modifier.fillMaxWidth()) { on ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 24.dp).background(if (on) a.copy(alpha = 0.18f) else Color.Transparent).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(k.icon, null, tint = if (k.urgent) Color(0xFFFF8A3D) else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(sceneLabel(k.label), color = if (k.urgent) Color(0xFFFF8A3D) else Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 3)
                    }
                } else
                SceneControl(k, Modifier.fillMaxWidth()) { on ->
                    Row(Modifier.fillMaxWidth().height(rowH ?: snapH ?: 40.dp).background(if (on) a.copy(alpha = 0.18f) else Color.Transparent), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(4.dp).fillMaxHeight().background(if (on) a else Color.Transparent))
                        Spacer(Modifier.width(12.dp))
                        Icon(k.icon, null, tint = if (on) a else Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(sceneLabel(k.label), color = if (on) Color.White else Color.White.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            }
        }
    }
}

// ───────────── Spotlight: a poster under a spotlight ─────────────

@Composable
internal fun SpotPoster(ctx: WinSceneCtx) {
    val h = minOf(ctx.maxH, 560.dp)
    val w = minOf(ctx.maxW, 720.dp)
    Box(Modifier.width(w).height(h).graphicsLayer {
        val e = sceneEase(ctx.reveal())
        alpha = e
        val s = 0.94f + 0.06f * e
        scaleX = s; scaleY = s
        shape = RoundedCornerShape(6.dp); clip = true
    }.background(Color.Black)) {
        // (Update 476, D256: the poster's picture was decoded at the CARDS' size — 360 px on the KONKR, 512 on the Odin — and
        // drawn across the whole window: 2.7–3.8× upscaled, soft. Window-wide pictures in the scenes now use the backdrop
        // request (the screen's long side), as the classic window already did; the picture itself is the owner's choice.)
        (ctx.backdrop ?: ctx.wide)?.let { SmartArt(it, null, Modifier.fillMaxSize().graphicsLayer { alpha = 0.75f }, request = rememberBackdropRequest(it), maxCrop = SmartFill.maxCropFor(it, ctx.cover)) }
        // (682t, P2 batch 2: Spotlight's own pages - THE PLOT, THE CREDITS, BACKSTAGE - WinPagesSpot.kt. On them the pool of light
        // moves down from the title onto the page, the title steps up small, and the Play pill row stays where it is.)
        val onPage = ctx.page != WinPage.MAIN
        val anim = rememberAnimationsEnabled()
        val pool by androidx.compose.animation.core.animateFloatAsState(if (onPage) 0.62f else 0.3f,
            if (anim) androidx.compose.animation.core.tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing) else androidx.compose.animation.core.snap(),
            label = "spotPool")
        // The spotlight: dark round the edges, a pool of light on the title.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)), center = Offset(size.width * 0.5f, size.height * pool), radius = size.maxDimension * 0.7f))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)), startY = size.height * (if (onPage) 0.2f else 0.45f)))
        }
        // (Update 505: as on the Spotlight home — its light Play pill, the other keys on its dark themed cards, the one you're on
        // lit in the theme's accent (was a fixed warm yellow), readable on the poster's dark.)
        val lit = sceneLit(com.shelf.launcher.data.HomeLayout.SPOT, com.shelf.launcher.ui.theme.ShelfTheme.accent, Color.Black)
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().then(if (onPage) Modifier.fillMaxSize() else Modifier).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (onPage) Text(spotPageName(ctx.page), color = lit, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            // (Update 476, D261: a logo address that fails to load — gone, offline, not cached — left the poster with no name at
            // all. The name in text is the fallback for both: no logo, or a logo that didn't load.)
            val logoFailed = androidx.compose.runtime.remember(ctx.logo) { androidx.compose.runtime.mutableStateOf(false) }
            ctx.logo?.takeIf { !logoFailed.value }?.let { AsyncImage(rememberStyleArtRequest(it), ctx.name,
                Modifier.fillMaxWidth(if (onPage) 0.4f else 0.55f).height(if (onPage) minOf(h * 0.10f, 44.dp) else minOf(h * 0.18f, 90.dp)),
                contentScale = ContentScale.Fit, onError = { logoFailed.value = true }) }
                ?: StableFullText(ctx.name.uppercase(), color = Color.White, fontSize = if (onPage) 17.sp else 26.sp, maxLines = 2, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            if (!onPage) Text(listOfNotNull(ctx.console, ctx.year, ctx.genres).joinToString("  ·  ").uppercase(), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, letterSpacing = 2.sp,
                modifier = Modifier.padding(vertical = 6.dp))
            else NativePages(ctx, Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), label = "spotPage", move = ::spotMove) { pg, pageLive ->
                SpotPage(ctx, pg, pageLive, lit, Modifier.fillMaxSize())
            }
            val card = themeDark(Color(0xFF1C1F25)).copy(alpha = 0.85f)
            // (682t, owner 18 Nov: Play the same size in the same place on every page - the pages keep the main page's slots)
            val keys = sceneKeys(ctx)
            KeyRow(keys, 8.dp, slots = rememberKeySlots(ctx.page, keys)) { k ->
                SceneKeyStyled(k, if (k.primary) Color(0xFFE9E9EC) else card, lit, if (k.primary) Color(0xFF16181C) else Color.White,
                    com.shelf.launcher.ui.theme.textOn(lit, Color.Black), Color.White.copy(alpha = 0.35f), Color.White, RoundedCornerShape(50), height = 36.dp, upper = true, size = 11.sp)
            }
        }
    }
}

// ───────────── Icon bar: a flyout rising from the dock ─────────────

@Composable
internal fun DockFlyout(ctx: WinSceneCtx) {
    val a = ctx.accent
    val w = minOf(ctx.maxW, 600.dp)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.width(w).heightIn(max = ctx.maxH).graphicsLayer {
            val e = sceneEase(ctx.reveal())
            translationY = (1f - e) * size.height * 0.6f
            alpha = (ctx.reveal() * 2f).coerceIn(0f, 1f)
        }, horizontalAlignment = Alignment.CenterHorizontally) {
            // (682y: the card takes the room the pointer leaves - its page shares it with the header and the dock keys)
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).clip(RoundedCornerShape(18.dp)).background(Color(0xFF20232A).copy(alpha = 0.96f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp)).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
                        // (682y, owner 18 Nov "avoid empty black areas when artwork is unavailable": the console's plate instead)
                        (ctx.cover ?: ctx.wide)?.let { SmartArt(it, null, Modifier.fillMaxSize(), maxCrop = SmartFill.maxCropFor(it, ctx.cover)) }
                            ?: SceneNoArt(ctx, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        StableFullText(ctx.name, color = Color.White, fontSize = 18.sp, maxLines = 2, fontWeight = FontWeight.Bold)
                        Text(ctx.console + " · " + ctx.played, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                }
                // (682y, P2 batch 4: the flyout's own pages - grouped cards, round icons, a grouped list - WinPagesDock.kt.)
                if (ctx.page != WinPage.MAIN) NativePages(ctx, Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = ctx.maxH * 0.5f).padding(top = 8.dp), label = "dockPage", move = { d -> dockMove(d) }) { pg, pageLive ->
                    DockPage(ctx, pg, pageLive, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(12.dp))
                // The actions as round dock icons with their names under them.
                // (Update 476, D258: a status key's whole text — "Balanced · AutoTDP Balanced · Device default", "Saves: up to
                // date · 1 min ago" — wrapped to 3–4 lines under its 78 dp icon. The icon keeps the first part; the whole
                // text shows on one line under the dock while that key has the focus. The line's room is always kept, so
                // nothing moves when the focus does.)
                val keys = sceneKeys(ctx)
                val anyShort = keys.any { dockShortLabel(sceneLabel(it.label)) != sceneLabel(it.label) }
                val fullLine = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
                // (681w, H16 - owner review of the KONKR x1.3 sheet: the Bar window's key names were cramped - in the old fixed
                // 78 dp a long name such as "Achievements" or "Performance" broke inside the word at x1.3. Every key takes the
                // width of the widest single word among the keys' names (78 dp at least), so names break only between words;
                // the uniform row scrolls by whole keys where they no longer all fit.)
                val labelStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = DOCK_LABEL_SP.sp)
                val measurer = androidx.compose.ui.text.rememberTextMeasurer(cacheSize = 16)
                val density = androidx.compose.ui.platform.LocalDensity.current
                val words = keys.map { dockShortLabel(sceneLabel(it.label)) }
                val keyW = androidx.compose.runtime.remember(words, labelStyle, density) {
                    val widest = dockLabelWords(words).maxOfOrNull { measurer.measure(it, labelStyle, softWrap = false, maxLines = 1).size.width } ?: 0
                    dockKeyWidthDp(with(density) { widest.toDp() }.value).dp
                }
                // (682y: on the flyout's pages Play keeps its place - the first half - with or without the page's own key)
                KeyRow(keys, 10.dp, slots = rememberKeySlots(ctx.page, keys)) { k ->
                    SceneControl(k, Modifier) { on ->
                        val whole = sceneLabel(k.label)
                        val short = dockShortLabel(whole)
                        androidx.compose.runtime.LaunchedEffect(on, whole) {
                            if (on) fullLine.value = if (short != whole) whole else null
                            else if (fullLine.value == whole) fullLine.value = null
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(keyW)) {
                            Box(Modifier.size(46.dp).graphicsLayer { val s = if (on) 1.12f else 1f; scaleX = s; scaleY = s }.clip(CircleShape)
                                .background(if (k.primary) a else Color.White.copy(alpha = 0.1f)).border(if (on) 2.dp else 0.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center) {
                                Icon(k.icon, null, tint = if (k.primary) onColor(a) else Color.White, modifier = Modifier.size(22.dp))
                            }
                            Text(short, color = if (on) Color.White else Color.White.copy(alpha = 0.7f), fontSize = DOCK_LABEL_SP.sp, lineHeight = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 3.dp))
                        }
                    }
                }
                if (anyShort) Box(Modifier.fillMaxWidth().height(18.dp).padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    fullLine.value?.let { FullText(it, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1) }
                }
            }
            // The pointer down to the dock.
            Canvas(Modifier.size(22.dp, 10.dp)) {
                drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height); close() }, Color(0xFF20232A).copy(alpha = 0.96f))
            }
        }
    }
}

// ───────────── Console hub: the console's nameplate with the game on it ─────────────

// (Update 507: HubPlate replaced — WinSceneFamilies.kt.)

// ───────────── Resume & discover: the big square with its discover cards ─────────────

@Composable
internal fun DiscoverCard(ctx: WinSceneCtx) {
    val a = ctx.accent
    val w = minOf(ctx.maxW, 700.dp)
    // (Update 469, Odin at 1.3× text with its Performance key: the status line under the keys was cut at the card's foot —
    // with larger text the card may take more of the screen and the keys are a little lower.)
    val big = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.1f
    val h = minOf(ctx.maxH, if (big) 460.dp else 380.dp)
    Row(Modifier.width(w).height(h).nativeBumpers(ctx).graphicsLayer {
        val e = sceneEase(ctx.reveal())
        translationX = (1f - e) * -size.width * 0.12f
        alpha = (ctx.reveal() * 2f).coerceIn(0f, 1f)
    }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // The big square — (update 467, popup gate: as tall as the card, it left the discover card ~210 dp on the KONKR, the
        // keys one per line and the facts a 14 dp sliver showing half a line; it now leaves the card at least 290 dp).
        val side = minOf(h, (w - 10.dp - 290.dp).coerceAtLeast(140.dp))
        Box(Modifier.size(side).clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
            // (682v, owner 18 Nov: "Resume & Discover: avoid large empty black areas when artwork is unavailable" - with no
            // picture, or one that does not load, the square is the game's own discover tile: its colour, console and name.)
            val noArt: @Composable (Modifier) -> Unit = { m -> DiscoverNoArt(ctx, a, m) }
            (ctx.cover ?: ctx.wide)?.let { SmartArt(it, ctx.name, Modifier.fillMaxSize(), maxCrop = SmartFill.maxCropFor(it, ctx.cover), noArt = noArt) }
                ?: noArt(Modifier.fillMaxSize())
            Text(if (ctx.lastPlayed != null) "JUMP BACK IN" else "DISCOVER", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(50)).background(a.copy(alpha = 0.9f)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        // The discover card.
        // (P2 / 681e: the card's own pages - the discover feed, WinPagesDiscover.kt - in the room above the pills.)
        Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1E24).copy(alpha = 0.97f)).padding(14.dp)) {
            NativePages(ctx, Modifier.weight(1f).fillMaxWidth(), label = "discoverPage", move = ::discoverMove) { pg, pageLive ->
                if (pg == WinPage.MAIN) Column {
                    StableFullText(ctx.name, color = Color.White, fontSize = 20.sp, maxLines = 2, fontWeight = FontWeight.Bold)
                    Text(ctx.console, color = a, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    SceneFactsBody(ctx, Color.White.copy(alpha = 0.5f), Color.White)
                } else DiscoverPage(ctx, pg, pageLive, Modifier.fillMaxSize())
            }
            // (529p, owner #15 pattern: the keys as pills that span the card in whole-word rows - PageBars - Play still the filled one.)
            PageBars(sceneKeys(ctx), 6.dp) { k ->
                SceneKey(k, if (k.primary) a else Color.White.copy(alpha = 0.07f), Color.White, if (k.primary) onColor(a) else Color.White, Color.Black,
                    Color.Transparent, a, RoundedCornerShape(50), height = if (big) 30.dp else 36.dp, primaryStyled = true, lineHeight = 15.sp, wide = true)
            }
        }
    }
}

/** 682v: the discover square with no picture - a tile in the game's colour washed with the theme's, its console and its name. */
@Composable
private fun DiscoverNoArt(ctx: WinSceneCtx, accent: Color, modifier: Modifier) {
    Box(modifier.background(placeholderBrush(ctx.g.hue)).background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.55f))))
        .padding(horizontal = 14.dp, vertical = 30.dp), contentAlignment = Alignment.BottomStart) {
        Column {
            Text(ctx.console.uppercase(), color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.height(4.dp))
            WholeWordsText(ctx.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, lineHeight = 26.sp)
        }
    }
}

// ───────────── Circuit board: the chip's datasheet ─────────────

@Composable
internal fun ChipSheet(ctx: WinSceneCtx) {
    // (Update 504, popup identity: the board the home layout shows — Layout options › Board colours. Only the green board
    // was ever drawn here, so the default "Your theme's colours" Circuit opened a green board with gold traces.)
    val (pcb, trace, silk) = circuitSheetColors()
    val w = minOf(ctx.maxW, 620.dp)
    Box(Modifier.width(w).heightIn(max = ctx.maxH).graphicsLayer {
        // Powers up from the trace: a line that opens into the board.
        val e = sceneEase(ctx.reveal())
        scaleY = 0.05f + 0.95f * e
        alpha = (ctx.reveal() * 3f).coerceIn(0f, 1f)
    }.clip(CutCornerShape(8.dp)).background(pcb).border(2.dp, trace.copy(alpha = 0.7f), CutCornerShape(8.dp))) {
        // (Update 424: the traces follow the sheet's size instead of setting it — fillMaxSize made the sheet as tall as the screen
        // allows, its lower half empty board with a few vias.)
        Canvas(Modifier.matchParentSize()) {
            // Traces along the edges with their vias.
            val s = Stroke(1.5.dp.toPx())
            for (i in 0 until 6) {
                val y = size.height * (0.1f + i * 0.16f)
                drawLine(trace.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width * 0.12f, y), s.width)
                drawCircle(trace.copy(alpha = 0.4f), 3.dp.toPx(), Offset(size.width * 0.12f, y), style = s)
            }
        }
        Column(Modifier.padding(start = 36.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
            Text("DATASHEET  ·  ${ctx.console.uppercase()}", color = trace, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            StableFullText(ctx.name, color = silk, fontSize = 20.sp, maxLines = 2, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (ctx.page == WinPage.MAIN) {
                // Pins: the facts numbered like a chip's pinout.
                // (674g, x1.3 f674f / k674f with More open: the pinout took up to 45 % of the window first and the status line under
                // the keys was cut (Flip 2) or 0 px (KONKR). The pinout gives way - it scrolls - and the keys and that line keep theirs.)
                Column(Modifier.weight(1f, fill = false).heightIn(max = ctx.maxH * 0.45f).verticalScroll(rememberScrollState())) {
                    sceneFacts(ctx).forEachIndexed { i, (l, v) ->
                        Row(Modifier.padding(vertical = 1.dp)) {
                            Text("P%02d".format(i + 1), color = trace, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(36.dp))
                            Text(l.uppercase(), color = silk.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(96.dp))
                            Text(v, color = silk, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            } else ctx.pageBody(Modifier.fillMaxWidth().heightIn(max = ctx.maxH * 0.6f))
            Spacer(Modifier.height(10.dp))
            KeyRow(sceneKeys(ctx)) { k ->
                SceneKey(k, Color(0xFF07261A), trace, silk, Color(0xFF07261A), trace.copy(alpha = 0.4f), trace, CutCornerShape(4.dp), font = FontFamily.Monospace, upper = true, size = 11.sp)
            }
        }
    }
}


// ───────────── Library panel: the game's library page ─────────────

@Composable
internal fun LibraryPage(ctx: WinSceneCtx) {
    // (Update 505: the Library home's own look — its dark themed panels and the theme's accent (gold once every achievement is
    // earned, as its progress bar) — instead of a fixed blue store page with a green Play.)
    val ground = themeDark(Color(0xFF1B2838)); val bar = themeDark(Color(0xFF16202D)); val track = themeDark(Color(0xFF2A475E))
    val green = sceneLit(com.shelf.launcher.data.HomeLayout.LIBRARY, com.shelf.launcher.ui.theme.ShelfTheme.accent, bar)
    val done = ctx.total > 0 && ctx.earned >= ctx.total
    val w = minOf(ctx.maxW, 760.dp)
    Column(Modifier.width(w).heightIn(max = ctx.maxH).graphicsLayer {
        val e = sceneEase(ctx.reveal())
        translationY = (1f - e) * size.height * 0.1f
        alpha = (ctx.reveal() * 2f).coerceIn(0f, 1f)
        shape = RoundedCornerShape(4.dp); clip = true
    }.background(ground)) {
        // The hero banner across the top, logo on it.
        // (682w, P2 batch 3: on the page's own sections the banner steps back to a strip - the sections get the room.)
        Box(Modifier.fillMaxWidth().height(minOf(ctx.maxH * (if (ctx.page == WinPage.MAIN) 0.36f else 0.2f), 200.dp)).background(Color.Black)) {
            (ctx.backdrop ?: ctx.wide)?.let { SmartArt(it, null, Modifier.fillMaxSize(), request = rememberBackdropRequest(it), maxCrop = SmartFill.maxCropFor(it, ctx.cover)) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, ground))))
            Box(Modifier.align(Alignment.BottomStart).padding(14.dp).fillMaxWidth(0.6f)) {
                ctx.logo?.let { AsyncImage(rememberStyleArtRequest(it), ctx.name, Modifier.fillMaxWidth().height(70.dp), contentScale = ContentScale.Fit, alignment = Alignment.BottomStart) }
                    ?: StableFullText(ctx.name, color = Color.White, fontSize = 24.sp, maxLines = 2, fontWeight = FontWeight.Bold)
            }
        }
        // The play bar: the big green Play, then the stats.
        Row(Modifier.fillMaxWidth().background(bar).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                KeyRow(sceneKeys(ctx), slots = sceneKeySlots(ctx)) { k ->
                    SceneKeyStyled(k, if (k.primary) green else Color.White.copy(alpha = 0.08f), if (k.primary) androidx.compose.ui.graphics.lerp(green, Color.White, 0.25f) else Color.White.copy(alpha = 0.2f),
                        Color.White, com.shelf.launcher.ui.theme.textOn(if (k.primary) androidx.compose.ui.graphics.lerp(green, Color.White, 0.25f) else Color.White.copy(alpha = 0.2f), Color.White), Color.Transparent, Color.White, RoundedCornerShape(3.dp), height = if (k.primary) 40.dp else 34.dp, upper = k.primary)
                }
            }
        }
        Column(Modifier.padding(14.dp).weight(1f, fill = ctx.page != WinPage.MAIN)) {
            if (ctx.page == WinPage.MAIN) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    listOf("LAST PLAYED" to (ctx.last ?: "Never"), "PLAY TIME" to ctx.played, "ACHIEVEMENTS" to (if (ctx.total > 0) "${ctx.earned}/${ctx.total}" else "—")).forEach { (l, v) ->
                        Column { Text(l, color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, letterSpacing = 1.sp); Text(v, color = Color.White, fontSize = 13.sp) }
                    }
                }
                if (ctx.total > 0) Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(track)) {
                    Box(Modifier.fillMaxWidth(ctx.earned.toFloat() / ctx.total).fillMaxHeight().background(if (done) com.shelf.launcher.ui.theme.Ds.gold else green))
                }
                // (674g, KONKR x1.3 k674f with More open: no room left for the story - it gives way by design (Details has it whole).)
                ctx.summary?.let { Text(it, color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, lineHeight = 14.sp, maxLines = 4, modifier = Modifier.semantics { this[QaClip.GivesWay] = true }.padding(top = 8.dp)) }
            } else {
                // (682w: the page's sections under a tab strip - ABOUT THIS GAME, ACHIEVEMENTS, MANAGE - WinPagesLibrary.kt.)
                LibraryTabs(ctx, green)
                Spacer(Modifier.height(6.dp))
                NativePages(ctx, Modifier.fillMaxWidth().weight(1f), label = "libraryPage", move = ::libraryMove) { pg, pageLive ->
                    LibraryPagesBody(ctx, pg, pageLive, bar, green, Modifier.fillMaxSize())
                }
            }
        }
    }
}

// ───────────── Timeline: an entry pinned to the year line ─────────────

// (Update 507: TimelineEntry replaced — WinSceneFamilies.kt.)

// ───────────── Hero carousel / Hero tiles: the hero banner opening up ─────────────

@Composable
internal fun HeroBanner(ctx: WinSceneCtx) {
    val a = ctx.accent
    val w = minOf(ctx.maxW, 860.dp)
    val h = minOf(ctx.maxH, 500.dp)
    Box(Modifier.width(w).height(h).graphicsLayer {
        // The banner grows open from its middle band (the hero strip widening into the page).
        val e = sceneEase(ctx.reveal())
        scaleY = 0.35f + 0.65f * e
        alpha = (ctx.reveal() * 2.5f).coerceIn(0f, 1f)
        shape = RoundedCornerShape(16.dp); clip = true
    }.background(Color.Black)) {
        (ctx.backdrop ?: ctx.wide ?: ctx.cover)?.let { SmartArt(it, null, Modifier.fillMaxSize(), request = rememberBackdropRequest(it), maxCrop = SmartFill.maxCropFor(it, ctx.cover)) }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.88f), Color.Black.copy(alpha = 0.55f), Color.Transparent))))
        Column(Modifier.fillMaxHeight().fillMaxWidth(0.62f).padding(20.dp), verticalArrangement = Arrangement.Bottom) {
            ctx.logo?.let { AsyncImage(rememberStyleArtRequest(it), ctx.name, Modifier.fillMaxWidth(0.8f).height(minOf(h * 0.2f, 96.dp)), contentScale = ContentScale.Fit, alignment = Alignment.BottomStart) }
                ?: StableFullText(ctx.name, color = Color.White, fontSize = 28.sp, maxLines = 3, fontWeight = FontWeight.Black)
            Text(listOfNotNull(ctx.console, ctx.year, ctx.played).joinToString("  ·  "), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            // (Update 426: the summary gives way first — it scrolls — so the title is never squeezed to one cut line when the
            // keys wrap; it was "999 Nine Hours, Nine Persons, Nin" on the Thor.)
            if (ctx.page == WinPage.MAIN) ctx.summary?.let {
                val sc = androidx.compose.foundation.rememberScrollState()
                Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 15.sp,
                    modifier = Modifier.weight(1f, fill = false).heightIn(max = 15.dp * 4).sceneFadeEdges(sc).verticalScroll(sc))
            }
            else ctx.pageBody(Modifier.fillMaxWidth().weight(1f, fill = false))
            Spacer(Modifier.height(10.dp))
            KeyRow(sceneKeys(ctx), 8.dp) { k ->
                SceneKeyStyled(k, if (k.primary) Color.White else Color.White.copy(alpha = 0.16f), a, if (k.primary) Color.Black else Color.White, onColor(a),
                    Color.Transparent, Color.White, RoundedCornerShape(50), height = 38.dp)
            }
        }
    }
}

// ───────────── Tile home / Grid: a clean game card that pops up from the tile ─────────────

// (Update 507: TileCard replaced — WinSceneFamilies.kt.)

// ───────────── Voltcap row: the house window, a charged panel with the bolt stripe ─────────────

@Composable
internal fun VoltPanel(ctx: WinSceneCtx) {
    val a = ctx.accent
    val w = minOf(ctx.maxW, 720.dp)
    // (P2 / 681c: the panel's own pages - WinPagesVolt.kt. On them the panel takes a steady height so the cells list has room
    // to scroll; L1 / R1 turn the pages wherever the D-pad is.)
    Row(Modifier.width(w).heightIn(max = if (ctx.page != WinPage.MAIN) minOf(ctx.maxH, 480.dp) else ctx.maxH).nativeBumpers(ctx).graphicsLayer {
        val e = sceneEase(ctx.reveal())
        translationY = (1f - e) * size.height * 0.15f
        alpha = (ctx.reveal() * 2f).coerceIn(0f, 1f)
        shape = RoundedCornerShape(14.dp); clip = true
    }.background(Color(0xFF12151B).copy(alpha = 0.97f)).border(1.dp, a.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
        // The charge stripe down the left edge. (Update 453: drawn behind, no longer a fillMaxHeight box — that box made the
        // window take its whole maximum height, so a game with few facts left half of it empty; now it fits what it shows.)
        .drawBehind { drawRect(Brush.verticalGradient(listOf(a, a.copy(alpha = 0.2f))), size = androidx.compose.ui.geometry.Size(6.dp.toPx(), size.height)) }) {
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f).padding(14.dp)) {
            // (Update 452, D156: the picture and facts give way to the keys — with More open the keys' extra lines were pushed out
            // of the window's maximum height and cut off on the RP Classic, Achievements to Max unreachable.)
            NativePages(ctx, Modifier.weight(1f, fill = ctx.page != WinPage.MAIN).fillMaxWidth(), label = "voltPage", move = ::voltMove) { pg, pageLive ->
            if (pg == WinPage.MAIN) Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.width(minOf(w * 0.24f, 150.dp)).aspectRatio(0.72f).clip(RoundedCornerShape(6.dp)).background(Color.Black)) {
                    SmartArt(ctx.cover, ctx.name, Modifier.fillMaxSize(), noArt = { m -> SceneNoArt(ctx, m) })
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    StableFullText(ctx.name, color = Color.White, fontSize = 21.sp, maxLines = 3, fontWeight = FontWeight.Bold)
                    Text(ctx.console, color = a, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    SceneFactsBody(ctx, Color.White.copy(alpha = 0.5f), Color.White)
                }
            } else VoltPage(ctx, pg, pageLive, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(10.dp))
            KeyRow(sceneKeys(ctx), slots = sceneKeySlots(ctx)) { k ->
                SceneKeyStyled(k, if (k.primary) a else Color.White.copy(alpha = 0.06f), Color.White, if (k.primary) onColor(a) else Color.White, Color.Black,
                    Color.White.copy(alpha = 0.12f), a, RoundedCornerShape(8.dp), height = 36.dp)
            }
        }
    }
}


/**
 * Update 476 (D258): the dock's label for a key — a status text ("Balanced · AutoTDP Balanced · Device default",
 * "Saves: up to date · 1 min ago") keeps its first part under the icon; the whole text is shown while the key has the focus.
 */
/** 681w: the Bar window's key names' size (sp) and a dock key's least width / the air beside its widest word (dp). */
internal const val DOCK_LABEL_SP = 10f
internal const val DOCK_KEY_MIN_DP = 78f
internal const val DOCK_KEY_AIR_DP = 6f

/** 681w: the single words of the dock keys' names - a name breaks only between them. Pure. */
internal fun dockLabelWords(names: List<String>): List<String> = names.flatMap { it.split(' ', '\n') }.filter { it.isNotBlank() }

/** 681w: a dock key's width (dp) for the widest word of the keys' names: the old 78 dp, or that word plus a little air. Pure. */
internal fun dockKeyWidthDp(widestWordDp: Float): Float = maxOf(DOCK_KEY_MIN_DP, kotlin.math.ceil(widestWordDp + DOCK_KEY_AIR_DP))

internal fun dockShortLabel(label: String): String = label.substringBefore(" · ").trim().ifEmpty { label }

/** Update 504: ChipSheet's board, traces and print in the Circuit layout's own colours (circuitColors), legible. */
@Composable
internal fun circuitSheetColors(): Triple<Color, Color, Color> {
    val mode = com.shelf.launcher.data.Features.circuitColors.state.value
    val surface = com.shelf.launcher.ui.theme.ShelfTheme.colors.surface
    val accent = com.shelf.launcher.ui.theme.ShelfTheme.accent
    return androidx.compose.runtime.remember(mode, surface, accent) { circuitSheetColorsFor(mode, surface, accent) }
}

internal fun circuitSheetColorsFor(mode: com.shelf.launcher.data.Features.CircuitColors, surface: Color, accent: Color): Triple<Color, Color, Color> {
    val pcb = when (mode) {
        com.shelf.launcher.data.Features.CircuitColors.GREEN -> return Triple(Color(0xFF0D3B26), Color(0xFFE2B44B), Color(0xFFEAF2EC))
        com.shelf.launcher.data.Features.CircuitColors.DARK -> Color(0xFF0E1013)
        com.shelf.launcher.data.Features.CircuitColors.THEME -> surface.copy(alpha = 1f)
    }
    val silk = if (pcb.luminance() > 0.45f) Color(0xFF15171C) else Color(0xFFEAF2EC)
    val trace = com.shelf.launcher.ui.theme.legibleArtAccent(accent, pcb, pcb)
    return Triple(pcb, trace, silk)
}
