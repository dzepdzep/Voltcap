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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * a restrained blue focus outline, two big bottom keys, "Wii Menu" (back to the channel tile) and "Start", and a compact
 * row of small keys for Details, Inspect, options and achievements. The banner is shown whole (Fit), never cropped.
 * Details / options / achievements / performance pages are GameWindow's own, drawn inside the preview area.
 */

/** Update 408: the Channel grid's window colours: light (default) or the dark variant, same geometry. */
internal class WiiPalette(val surface: Color, val well: Color, val edge: Color, val ink: Color, val muted: Color, val key: Color, val keyOn: Color, val light: Boolean)

internal fun wiiPalette(light: Boolean) = if (light)
    WiiPalette(Color(0xFFFAFBFC), Color(0xFFE9EDF2), Color(0xFFC9D2DD), Color(0xFF3B4450), Color(0xFF6C7787), Color.White, Color(0xFFEAF4FD), true)
else
    WiiPalette(Color(0xFF1B2028), Color(0xFF11151B), Color(0xFF3A4350), Color(0xFFE9EEF4), Color(0xFF9AA6B6), Color(0xFF2A313B), Color(0xFF26384C), false)

private val WiiBlue = Color(0xFF3AA6E8)

@Composable
internal fun WiiChannelPreview(ctx: WinSceneCtx) {
    val pal = wiiPalette(Features.chanLight.state.value && !Features.chanDark.state.value)
    val w = minOf(ctx.maxW, 860.dp)
    val h = minOf(ctx.maxH, 540.dp)
    val compact = h < 380.dp
    val shape = RoundedCornerShape(26.dp)
    val play = ctx.buttons.firstOrNull { it.primary }
    val back = ctx.buttons.firstOrNull { it.back }
    val small = ctx.buttons.filter { !it.primary && !it.back } + ctx.tools
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
        }.background(pal.surface).border(1.dp, pal.edge, shape).padding(horizontal = 22.dp, vertical = if (compact) 10.dp else 16.dp)
    ) {
        // The big preview: the channel's banner whole, in a rounded well with a blue rim.
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(pal.well)
                .border(3.dp, WiiBlue.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
        ) {
            if (ctx.page == WinPage.MAIN) {
                val art = ctx.wide ?: ctx.backdrop ?: ctx.cover
                if (art != null) AsyncImage(
                    rememberStyleArtRequest(art), ctx.name, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit
                )
            } else {
                ctx.pageBody(Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        if (ctx.page == WinPage.MAIN) {
            // The channel's name under the picture (never over it), with its facts on one line.
            StableFullText(ctx.name, color = pal.ink, fontSize = if (compact) 15.sp else 19.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            FullText(listOfNotNull(ctx.console, ctx.year, ctx.genres?.substringBefore(","), "Played " + ctx.played).joinToString("  ·  "),
                color = pal.muted, fontSize = 12.sp)
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            // The compact secondary keys: one line that scrolls sideways, each key as wide as its whole name.
            if (small.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    small.forEach { a ->
                        SceneControl(a, Modifier) { on ->
                            Row(
                                Modifier.clip(RoundedCornerShape(50)).background(if (on) pal.keyOn else pal.key)
                                    .border(if (on) 2.dp else 1.dp, if (on) WiiBlue else if (a.urgent) WiiBlue.copy(alpha = 0.7f) else pal.edge, RoundedCornerShape(50))
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(a.icon, null, tint = pal.ink, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(a.label, color = pal.ink, fontSize = 12.sp, softWrap = false)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
            }
            // Two prominent keys along the bottom: Wii Menu (back to the tile) and Start.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (back != null) WiiBigKey(back, "Wii Menu", pal, Modifier.weight(1f), compact)
                if (play != null) WiiBigKey(play, if (play.label.equals("Continue", true)) "Continue" else "Start", pal, Modifier.weight(1f), compact)
            }
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ctx.buttons.forEach { a -> WiiBigKey(a, a.label, pal, Modifier.widthIn(min = 140.dp), compact) }
            }
        }
    }
}

@Composable
private fun WiiBigKey(a: WinAction, label: String, pal: WiiPalette, modifier: Modifier, compact: Boolean) {
    SceneControl(a, modifier) { on ->
        val s = RoundedCornerShape(50)
        Box(
            Modifier.fillMaxWidth().clip(s)
                .background(Brush.verticalGradient(if (on) listOf(if (pal.light) Color.White else pal.keyOn, pal.keyOn) else listOf(pal.key, pal.well)))
                .border(if (on) 3.dp else 1.5.dp, if (on) WiiBlue else pal.edge, s)
                .padding(vertical = if (compact) 8.dp else 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = pal.ink, fontSize = if (compact) 16.sp else 20.sp, fontWeight = FontWeight.Bold, softWrap = false)
        }
    }
}
