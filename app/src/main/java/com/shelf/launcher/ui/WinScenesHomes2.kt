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

/** A column of keys (menus that run down the screen: Cross menu, Side menu); scrolls when it doesn't fit. */
@Composable
private fun KeyColumn(keys: List<WinAction>, modifier: Modifier, scroll: Boolean = true, key: @Composable (WinAction) -> Unit) {
    Column(if (scroll) modifier.verticalScroll(rememberScrollState()) else modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) { keys.forEach { key(it) } }
}

/** A full-width list row key (icon + word), lit as a band. */
@Composable
private fun ListKey(a: WinAction, ink: Color, lit: Color, litInk: Color, height: androidx.compose.ui.unit.Dp = 38.dp, font: FontFamily? = null) {
    SceneControl(a, Modifier.fillMaxWidth()) { on ->
        Row(Modifier.fillMaxWidth().heightIn(min = height).background(if (on) lit else Color.Transparent).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                (ctx.cover ?: ctx.wide)?.let { AsyncImage(rememberStyleArtRequest(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
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
        Box(Modifier.fillMaxWidth().height(minOf(ctx.maxH * 0.3f, 150.dp))) {
            (ctx.wide ?: ctx.backdrop ?: ctx.cover)?.let { AsyncImage(rememberBackdropRequest(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, surface))))
        }
        // (Update 476, D255: the name was drawn over the art's lower part — where the game's own logo usually sits — and the
        // two titles fought. The name now sits just under the art, on the card; the art strip is unchanged.)
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp)) {
            StableFullText(ctx.name, color = Color.White, fontSize = 20.sp, maxLines = 2, fontWeight = FontWeight.Bold)
            Text(ctx.console, color = a, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).weight(1f, fill = false)) {
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
            } else ctx.pageBody(Modifier.fillMaxWidth().weight(1f))
        }
        Box(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
            KeyRow(sceneKeys(ctx)) { k ->
                SceneKeyStyled(k, if (k.primary) a else Color.White.copy(alpha = 0.06f), Color.White, if (k.primary) onColor(a) else Color.White, Color(0xFF111111),
                    Color.White.copy(alpha = 0.1f), a, RoundedCornerShape(8.dp), height = 34.dp)
            }
        }
    }
}

// ───────────── Side menu: a drawer sliding out from the menu's edge ─────────────

/** (Update 514) The drawer's name (two lines), its console line and the gap under them — what sits between picture and menu. */
private val SIDE_DRAWER_TEXT = 84.dp

@Composable
internal fun SideDrawer(ctx: WinSceneCtx) {
    val a = ctx.accent
    val w = minOf(ctx.maxW * 0.5f, 420.dp).coerceAtLeast(minOf(ctx.maxW, 300.dp))
    // (Update 514, RP Nova 4:3, 480 dp high: the picture at the top took its full 2.1 : 1 (152 dp) and the menu below scrolled —
    // Inspect sat under the drawer's edge. On a short screen the picture gives up height first, down to 64 dp, so every row
    // fits at its smallest readable height (30 dp); where there is room (the Thor) it keeps its whole 2.1 : 1.)
    val keys = sceneKeys(ctx)
    val rowsNeed = 30.dp * keys.size + 2.dp * (keys.size - 1).coerceAtLeast(0)
    val headerH = if (ctx.page != WinPage.MAIN) w / 2.1f else minOf(w / 2.1f, (ctx.maxH - rowsNeed - SIDE_DRAWER_TEXT).coerceAtLeast(64.dp))
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.CenterStart).width(w).fillMaxHeight().graphicsLayer {
            translationX = -(1f - sceneEase(ctx.reveal())) * size.width
            shadowElevation = 20.dp.toPx()
        }.background(Color(0xFF15171C)).border(1.dp, Color.White.copy(alpha = 0.06f))) {
            Box(Modifier.fillMaxWidth().height(headerH)) {
                (ctx.wide ?: ctx.backdrop ?: ctx.cover)?.let { AsyncImage(rememberBackdropRequest(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF15171C)))))
            }
            Column(Modifier.padding(horizontal = 14.dp)) {
                StableFullText(ctx.name, color = Color.White, fontSize = 19.sp, maxLines = 2, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(ctx.console, ctx.year, ctx.played).joinToString(" · "), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            if (ctx.page != WinPage.MAIN) ctx.pageBody(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp))
            // The menu's own rows, with the accent bar on the one you're on.
            // (Update 488, D284: the rows share the drawer's room — Back was cut 13 px under the Thor's 1080p screen edge; 40 dp
            // when there is room, down to 30 dp when not, scrolling only below that.)
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().then(if (ctx.page == WinPage.MAIN) Modifier.weight(1f) else Modifier.heightIn(max = 140.dp))) {
            val rowH = fitRowHeight(maxHeight, keys.size, 2.dp, 40.dp, 30.dp)
            // (Update 488b: with many rows (the Thor's 8) the list scrolls — then a whole number of rows fills the room, so
            // the last one shown is never cut, p488d: Back cut 13 px.)
            val snapH = if (rowH == null) snapRowHeight(maxHeight, keys.size, 2.dp, 40.dp, 30.dp) else null
            KeyColumn(keys, Modifier.fillMaxWidth(), scroll = rowH == null) { k ->
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
        (ctx.backdrop ?: ctx.wide)?.let { AsyncImage(rememberBackdropRequest(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.75f) }
        // The spotlight: dark round the edges, a pool of light on the title.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)), center = Offset(size.width * 0.5f, size.height * 0.3f), radius = size.maxDimension * 0.7f))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)), startY = size.height * 0.45f))
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // (Update 476, D261: a logo address that fails to load — gone, offline, not cached — left the poster with no name at
            // all. The name in text is the fallback for both: no logo, or a logo that didn't load.)
            val logoFailed = androidx.compose.runtime.remember(ctx.logo) { androidx.compose.runtime.mutableStateOf(false) }
            ctx.logo?.takeIf { !logoFailed.value }?.let { AsyncImage(rememberStyleArtRequest(it), ctx.name, Modifier.fillMaxWidth(0.55f).height(minOf(h * 0.18f, 90.dp)),
                contentScale = ContentScale.Fit, onError = { logoFailed.value = true }) }
                ?: StableFullText(ctx.name.uppercase(), color = Color.White, fontSize = 26.sp, maxLines = 2, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text(listOfNotNull(ctx.console, ctx.year, ctx.genres).joinToString("  ·  ").uppercase(), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, letterSpacing = 2.sp,
                modifier = Modifier.padding(vertical = 6.dp))
            if (ctx.page != WinPage.MAIN) ctx.pageBody(Modifier.fillMaxWidth().height(h * 0.4f))
            // (Update 505: as on the Spotlight home — its light Play pill, the other keys on its dark themed cards, the one you're on
            // lit in the theme's accent (was a fixed warm yellow), readable on the poster's dark.)
            val lit = sceneLit(com.shelf.launcher.data.HomeLayout.SPOT, com.shelf.launcher.ui.theme.ShelfTheme.accent, Color.Black)
            val card = themeDark(Color(0xFF1C1F25)).copy(alpha = 0.85f)
            KeyRow(sceneKeys(ctx), 8.dp) { k ->
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
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF20232A).copy(alpha = 0.96f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp)).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
                        (ctx.cover ?: ctx.wide)?.let { AsyncImage(rememberStyleArtRequest(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        StableFullText(ctx.name, color = Color.White, fontSize = 18.sp, maxLines = 2, fontWeight = FontWeight.Bold)
                        Text(ctx.console + " · " + ctx.played, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                }
                if (ctx.page != WinPage.MAIN) ctx.pageBody(Modifier.fillMaxWidth().heightIn(max = ctx.maxH * 0.5f).padding(top = 8.dp))
                Spacer(Modifier.height(12.dp))
                // The actions as round dock icons with their names under them.
                // (Update 476, D258: a status key's whole text — "Balanced · AutoTDP Balanced · Device default", "Saves: up to
                // date · 1 min ago" — wrapped to 3–4 lines under its 78 dp icon. The icon keeps the first part; the whole
                // text shows on one line under the dock while that key has the focus. The line's room is always kept, so
                // nothing moves when the focus does.)
                val keys = sceneKeys(ctx)
                val anyShort = keys.any { dockShortLabel(sceneLabel(it.label)) != sceneLabel(it.label) }
                val fullLine = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
                KeyRow(keys, 10.dp) { k ->
                    SceneControl(k, Modifier) { on ->
                        val whole = sceneLabel(k.label)
                        val short = dockShortLabel(whole)
                        androidx.compose.runtime.LaunchedEffect(on, whole) {
                            if (on) fullLine.value = if (short != whole) whole else null
                            else if (fullLine.value == whole) fullLine.value = null
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(78.dp)) {
                            Box(Modifier.size(46.dp).graphicsLayer { val s = if (on) 1.12f else 1f; scaleX = s; scaleY = s }.clip(CircleShape)
                                .background(if (k.primary) a else Color.White.copy(alpha = 0.1f)).border(if (on) 2.dp else 0.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center) {
                                Icon(k.icon, null, tint = if (k.primary) onColor(a) else Color.White, modifier = Modifier.size(22.dp))
                            }
                            Text(short, color = if (on) Color.White else Color.White.copy(alpha = 0.7f), fontSize = 10.sp, lineHeight = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
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
    Row(Modifier.width(w).height(h).graphicsLayer {
        val e = sceneEase(ctx.reveal())
        translationX = (1f - e) * -size.width * 0.12f
        alpha = (ctx.reveal() * 2f).coerceIn(0f, 1f)
    }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // The big square — (update 467, popup gate: as tall as the card, it left the discover card ~210 dp on the KONKR, the
        // keys one per line and the facts a 14 dp sliver showing half a line; it now leaves the card at least 290 dp).
        val side = minOf(h, (w - 10.dp - 290.dp).coerceAtLeast(140.dp))
        Box(Modifier.size(side).clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
            (ctx.cover ?: ctx.wide)?.let { AsyncImage(rememberStyleArtRequest(it), ctx.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Text(if (ctx.lastPlayed != null) "JUMP BACK IN" else "DISCOVER", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(50)).background(a.copy(alpha = 0.9f)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        // The discover card.
        Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1E24).copy(alpha = 0.97f)).padding(14.dp)) {
            StableFullText(ctx.name, color = Color.White, fontSize = 20.sp, maxLines = 2, fontWeight = FontWeight.Bold)
            Text(ctx.console, color = a, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            SceneFactsBody(ctx, Color.White.copy(alpha = 0.5f), Color.White)
            Spacer(Modifier.weight(0.01f))
            KeyRow(sceneKeys(ctx)) { k ->
                SceneKeyStyled(k, if (k.primary) a else Color.White.copy(alpha = 0.07f), Color.White, if (k.primary) onColor(a) else Color.White, Color.Black,
                    Color.Transparent, a, RoundedCornerShape(50), height = if (big) 30.dp else 36.dp)
            }
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
                Column(Modifier.heightIn(max = ctx.maxH * 0.45f).verticalScroll(rememberScrollState())) {
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
        Box(Modifier.fillMaxWidth().height(minOf(ctx.maxH * 0.36f, 200.dp)).background(Color.Black)) {
            (ctx.backdrop ?: ctx.wide)?.let { AsyncImage(rememberBackdropRequest(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, ground))))
            Box(Modifier.align(Alignment.BottomStart).padding(14.dp).fillMaxWidth(0.6f)) {
                ctx.logo?.let { AsyncImage(rememberStyleArtRequest(it), ctx.name, Modifier.fillMaxWidth().height(70.dp), contentScale = ContentScale.Fit, alignment = Alignment.BottomStart) }
                    ?: StableFullText(ctx.name, color = Color.White, fontSize = 24.sp, maxLines = 2, fontWeight = FontWeight.Bold)
            }
        }
        // The play bar: the big green Play, then the stats.
        Row(Modifier.fillMaxWidth().background(bar).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                KeyRow(sceneKeys(ctx)) { k ->
                    SceneKeyStyled(k, if (k.primary) green else Color.White.copy(alpha = 0.08f), if (k.primary) androidx.compose.ui.graphics.lerp(green, Color.White, 0.25f) else Color.White.copy(alpha = 0.2f),
                        Color.White, com.shelf.launcher.ui.theme.textOn(if (k.primary) androidx.compose.ui.graphics.lerp(green, Color.White, 0.25f) else Color.White.copy(alpha = 0.2f), Color.White), Color.Transparent, Color.White, RoundedCornerShape(3.dp), height = if (k.primary) 40.dp else 34.dp, upper = k.primary)
                }
            }
        }
        Column(Modifier.padding(14.dp).weight(1f, fill = false)) {
            if (ctx.page == WinPage.MAIN) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    listOf("LAST PLAYED" to (ctx.last ?: "Never"), "PLAY TIME" to ctx.played, "ACHIEVEMENTS" to (if (ctx.total > 0) "${ctx.earned}/${ctx.total}" else "—")).forEach { (l, v) ->
                        Column { Text(l, color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, letterSpacing = 1.sp); Text(v, color = Color.White, fontSize = 13.sp) }
                    }
                }
                if (ctx.total > 0) Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(track)) {
                    Box(Modifier.fillMaxWidth(ctx.earned.toFloat() / ctx.total).fillMaxHeight().background(if (done) com.shelf.launcher.ui.theme.Ds.gold else green))
                }
                ctx.summary?.let { Text(it, color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, lineHeight = 14.sp, maxLines = 4, modifier = Modifier.padding(top = 8.dp)) }
            } else ctx.pageBody(Modifier.fillMaxWidth().weight(1f))
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
        (ctx.backdrop ?: ctx.wide ?: ctx.cover)?.let { AsyncImage(rememberBackdropRequest(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
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
    Row(Modifier.width(w).heightIn(max = ctx.maxH).graphicsLayer {
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
            Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.Top) {
                Box(Modifier.width(minOf(w * 0.24f, 150.dp)).aspectRatio(0.72f).clip(RoundedCornerShape(6.dp)).background(Color.Black)) {
                    ctx.cover?.let { AsyncImage(rememberStyleArtRequest(it), ctx.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    StableFullText(ctx.name, color = Color.White, fontSize = 21.sp, maxLines = 3, fontWeight = FontWeight.Bold)
                    Text(ctx.console, color = a, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    SceneFactsBody(ctx, Color.White.copy(alpha = 0.5f), Color.White)
                }
            }
            Spacer(Modifier.height(10.dp))
            KeyRow(sceneKeys(ctx)) { k ->
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
