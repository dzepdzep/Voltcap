package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features

/*
 * Update 408: the Channel grid's own window, restored (update 104's Wii Channel Preview; 113 had replaced it by the shared
 * GameWindow). A large rounded preview of the channel on a clean white (or, in the dark variant, charcoal) surface with
 * a restrained blue focus outline, two big bottom keys, "Channels" (back to the channel tile; update 507) and "Start", and a compact
 * row of small keys for Details, Inspect, options and achievements. The banner is shown whole (Fit), never cropped.
 * Details / options / achievements / performance pages are GameWindow's own, drawn inside the preview area.
 */

/** Update 408: the Channel grid's window colours: light (default) or the dark variant, same geometry. */
internal class WiiPalette(val surface: Color, val well: Color, val edge: Color, val ink: Color, val muted: Color, val key: Color, val keyOn: Color, val light: Boolean)

internal fun wiiPalette(light: Boolean) = if (light)
    WiiPalette(Color(0xFFFAFBFC), Color(0xFFE9EDF2), Color(0xFFC9D2DD), Color(0xFF3B4450), Color(0xFF6C7787), Color.White, Color(0xFFEAF4FD), true)
else
    WiiPalette(Color(0xFF1B2028), Color(0xFF11151B), Color(0xFF3A4350), Color(0xFFE9EEF4), Color(0xFF9AA6B6), Color(0xFF2A313B), Color(0xFF26384C), false)

/**
 * Update 421: the preview window in the grid's palette (ChanPalette.kt) — Original Pale / OLED Black keep the exact colours
 * above; Light Blue, Light Yellow and Custom take their surfaces, rim and writing from the same palette as the tiles.
 */
internal fun wiiPaletteNow(): WiiPalette {
    val c = chanColors()
    return when {
        !Features.chanLight.state.value -> wiiPalette(false) // (the theme / art backdrop: the dark window, as before)
        c.palette == Features.ChanPalette.PALE -> wiiPalette(true)
        c.palette == Features.ChanPalette.OLED -> WiiPalette(Color(0xFF0E1012), Color(0xFF050607), Color(0xFF2A2F36), Color(0xFFE9EEF4), Color(0xFF9AA6B6), Color(0xFF17191D), Color(0xFF1E2A38), false)
        c.light -> WiiPalette(c.surface, c.surface2, c.edge, c.ink, c.ink.copy(alpha = 0.72f), c.surface, androidx.compose.ui.graphics.lerp(c.surface2, Color.White, 0.4f), true)
        else -> WiiPalette(c.surface, c.surface3, c.edge, Color.White, Color.White.copy(alpha = 0.7f), c.surface2, androidx.compose.ui.graphics.lerp(c.surface2, Color.White, 0.12f), false)
    }
}

private val WiiBlue = Color(0xFF3AA6E8)

/**
 * Update 506 (owner review of the 505 visual report): the preview's blue rim and lit keys stayed blue with Channel grid's own
 * colours set (the tiles' ring followed them). The rim is the shared scene rule's lit colour now (sceneLit: the Wii blue unless
 * this layout has its own colours, readable on the window's surface) — the window's shape and palette stay the Wii's.
 */
private val LocalWiiRim = androidx.compose.runtime.staticCompositionLocalOf { WiiBlue }

@Composable
internal fun WiiChannelPreview(ctx: WinSceneCtx) {
    // (Without colours of its own the Wii blue exactly as before.)
    val rim = if (com.shelf.launcher.data.Features.layoutColorsFor(com.shelf.launcher.data.HomeLayout.CHAN.name).acc == null) WiiBlue
        else sceneLit(com.shelf.launcher.data.HomeLayout.CHAN, WiiBlue, wiiPaletteNow().surface)
    androidx.compose.runtime.CompositionLocalProvider(LocalWiiRim provides rim) { WiiChannelPreviewBody(ctx) }
}

/** Update 426: which game's Wii window has its More page open (null: the preview). */
internal object WiiMore { var key by androidx.compose.runtime.mutableStateOf<String?>(null) }

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun WiiChannelPreviewBody(ctx: WinSceneCtx) {
    val pal = wiiPaletteNow()
    // Update 411: the real Wii Channel Preview is a compact, nearly 4:3 white card, not a full-screen dashboard.
    val w = minOf(ctx.maxW, 440.dp)
    val h = minOf(ctx.maxH * 0.74f, 330.dp)
    val compact = h < 300.dp
    val shape = RoundedCornerShape(22.dp)
    val play = ctx.buttons.firstOrNull { it.primary }
    val back = ctx.buttons.firstOrNull { it.back }
    val small = ctx.buttons.filter { !it.primary && !it.back } + ctx.tools
    val I = androidx.compose.material.icons.Icons.Filled
    // Update 426: the secondary actions' page (More); B / Channels come back to the preview first.
    // (Kept outside the scene: a page opened from More — Details, Options… — replaces the scene, and B from it comes back
    // to the More page on the key that opened it. A window opening afresh starts on the preview.)
    androidx.compose.runtime.remember { if (ctx.reveal() < 0.5f) WiiMore.key = null; 0 }
    val more = WiiMore.key == ctx.g.stableKey && ctx.page == WinPage.MAIN
    fun setMore(v: Boolean) { WiiMore.key = if (v) ctx.g.stableKey else null }
    androidx.activity.compose.BackHandler(enabled = more) { setMore(false) }
    val moreKey = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    var moreTouched by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(more) {
        if (!moreTouched) { moreTouched = true; return@LaunchedEffect }
        kotlinx.coroutines.delay(70)
        runCatching { if (more) small.firstOrNull { it.focus != null }?.focus?.requestFocus() else moreKey.requestFocus() }
    }
    Column(
        Modifier.width(w).height(h).graphicsLayer {
            // Opens out of the channel tile and closes back into it (B).
            val k = ctx.reveal().coerceIn(0f, 1f)
            val e = 1f - (1f - k) * (1f - k) * (1f - k)
            val s = 0.84f + 0.16f * e
            scaleX = s; scaleY = s
            transformOrigin = TransformOrigin(0.5f, 0.55f)
            alpha = (k * 2.4f).coerceIn(0f, 1f)
            this.shape = shape; clip = true
            shadowElevation = 20.dp.toPx() * e
        }.background(pal.surface).border(1.dp, pal.edge, shape).padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp)
    ) {
        // The big preview: the channel's banner whole, in a rounded well with a blue rim.
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.well)
                .border(2.dp, LocalWiiRim.current.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
        ) {
            if (ctx.page == WinPage.MAIN && more) {
                WiiMorePage(small, pal, compact, Modifier.fillMaxSize())
            } else if (ctx.page == WinPage.MAIN) {
                // Update 421: wide art fills the preview (cropped to its shape, never stretched); a portrait cover is shown
                // whole in front of a soft, filled copy of itself, so the well never shows bare bars either side.
                val wideArt = ctx.wide ?: ctx.backdrop
                if (wideArt != null) AsyncImage(
                    rememberBackdropRequest(wideArt), ctx.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                ) else if (ctx.cover != null) {
                    AsyncImage(rememberStyleArtRequest(ctx.cover), null, Modifier.fillMaxSize().graphicsLayer {
                        renderEffect = if (android.os.Build.VERSION.SDK_INT >= 31) androidx.compose.ui.graphics.BlurEffect(18.dp.toPx(), 18.dp.toPx()) else null
                        alpha = 0.55f
                    }, contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(pal.well.copy(alpha = 0.25f)))
                    AsyncImage(rememberStyleArtRequest(ctx.cover), ctx.name, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                }
            } else {
                ctx.pageBody(Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        if (ctx.page == WinPage.MAIN) {
            // The banner carries the title like the real preview; without banner art the name is the one line under the picture.
            if ((ctx.wide ?: ctx.backdrop ?: ctx.cover) == null) {
                StableFullText(ctx.name, color = pal.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Spacer(Modifier.height(4.dp))
            }
            // Update 426: no strip of tiny keys any more (an action was cut at the right edge on the user's Thor): one
            // readable "More" key opens the secondary actions as a page of big Wii keys in the preview well (above).
            if (small.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    WiiMidKey(WinAction(if (more) "Less" else "More", I.MoreHoriz, { setMore(!more) }, focus = moreKey, urgent = small.any { it.urgent }), pal, Modifier.widthIn(min = 150.dp), compact)
                }
                Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
            }
            // Two prominent keys along the bottom: Channels (back to the tile) and Start.
            // (Update 507: the key named the console maker's own menu; "Channels" says where B goes.)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (back != null) WiiBigKey(if (more) WinAction(back.label, back.icon, { setMore(false) }, back = true) else back, if (more) "Back" else "Channels", pal, Modifier.weight(1f), compact)
                if (play != null) WiiBigKey(play, if (play.label.equals("Continue", true)) "Continue" else "Start", pal, Modifier.weight(1f), compact)
            }
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ctx.buttons.forEach { a -> WiiBigKey(a, a.label, pal, Modifier.widthIn(min = 140.dp), compact) }
            }
        }
    }
}

/** Update 426: the secondary actions as big, readable Wii keys in the preview well, wrapping onto as many rows as needed. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun WiiMorePage(items: List<WinAction>, pal: WiiPalette, compact: Boolean, modifier: Modifier) {
    val scroll = rememberScrollState()
    androidx.compose.foundation.layout.FlowRow(
        modifier.sceneFadeEdges(scroll).verticalScroll(scroll).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        // (Four to a row from seven actions on — two rows of whole keys, never a third row cut by the well's edge.)
        maxItemsInEachRow = if (items.size > 6) 4 else 3
    ) {
        items.forEach { a -> WiiMidKey(WinAction(wiiSmallLabel(a.label), a.icon, a.onClick, a.focus, urgent = a.urgent), pal, Modifier.weight(1f), compact, tall = true) }
    }
}

/** A medium Wii key: icon over (tall) or beside its whole name, the palette's key colours, the blue rim when lit. */
@Composable
private fun WiiMidKey(a: WinAction, pal: WiiPalette, modifier: Modifier, compact: Boolean, tall: Boolean = false) {
    SceneControl(a, modifier) { on ->
        val s = RoundedCornerShape(if (tall) 16.dp else 50.dp)
        val body: @Composable () -> Unit = {
            Icon(a.icon, null, tint = if (on) LocalWiiRim.current else pal.ink, modifier = Modifier.size(if (compact) 16.dp else 20.dp))
            Text(a.label, color = pal.ink, fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        val m = Modifier.fillMaxWidth().clip(s)
            .background(Brush.verticalGradient(if (on) listOf(if (pal.light) Color.White else pal.keyOn, pal.keyOn) else listOf(pal.key, pal.well)))
            .border(if (on) 3.dp else 1.5.dp, if (on) LocalWiiRim.current else if (a.urgent) LocalWiiRim.current.copy(alpha = 0.7f) else pal.edge, s)
            .padding(horizontal = 12.dp, vertical = if (tall) (if (compact) 6.dp else 10.dp) else (if (compact) 4.dp else 6.dp))
        if (tall) Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) { body() }
        else Row(m, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) { body() }
    }
}

@Composable
private fun WiiBigKey(a: WinAction, label: String, pal: WiiPalette, modifier: Modifier, compact: Boolean) {
    SceneControl(a, modifier) { on ->
        val s = RoundedCornerShape(50)
        Box(
            Modifier.fillMaxWidth().clip(s)
                .background(Brush.verticalGradient(if (on) listOf(if (pal.light) Color.White else pal.keyOn, pal.keyOn) else listOf(pal.key, pal.well)))
                .border(if (on) 3.dp else 1.5.dp, if (on) LocalWiiRim.current else pal.edge, s)
                .padding(vertical = if (compact) 6.dp else 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = pal.ink, fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.Bold, softWrap = false)
        }
    }
}

/** Update 411: the compact VoltCap keys under the Wii preview use the short names (Config, Saves); the handlers are the existing ones. */
private fun wiiSmallLabel(l: String) = when (l) { "Options" -> "Config"; "Save sync" -> "Saves"; else -> l }
