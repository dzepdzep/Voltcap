package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Profiles

/*
 * Update 358 (Toni: "upgrade" Tile home, Switch home as the example — not started over). Tile home › Switch style, on by
 * default; off is Tile home exactly as before. Each part has its own switch:
 * - Plain background: a calm grey like the Switch's dark theme, over the art and the clip (the clip isn't even looked up
 *   then). Off: the background you picked (game art, a clip, your wallpaper, a theme) shows again, the tiles over it.
 * - Profiles top-left: every profile's picture in the top-left corner, the one playing ringed; a tap opens Who's playing.
 * - Thin focus frame: a thin bright frame that gently pulses round the tile you're on, no glow or bolts (TileHome.kt).
 * - Hint bar: a thin line across the bottom over the button legend (only while the legend is shown).
 */
internal object TileSwitch {
    val on: Boolean get() = Features.tileSwitch.state.value
    val plain: Boolean get() = on && Features.tileSwitchPlain.state.value
    val profiles: Boolean get() = on && Features.tileSwitchProfiles.state.value
    val frame: Boolean get() = on && Features.tileSwitchFrame.state.value
    val hints: Boolean get() = on && Features.tileSwitchHints.state.value
    /** Set by the home screen: Tile home with the profiles top-left, so the status bar leaves its own picture out. */
    var hideBarAvatar by androidx.compose.runtime.mutableStateOf(false)
}

/** The plain background: a soft dark grey, a touch lighter at the top. */
@Composable
internal fun TileSwitchBackground(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF3A3C40), Color(0xFF2D2F33), Color(0xFF26282B)))))
}

/** Every profile's picture in a row (the one playing ringed and a little bigger). A tap opens Who's playing. */
@Composable
internal fun TileSwitchProfiles(raNames: List<String?>, steamAvatar: String?, modifier: Modifier = Modifier) {
    val all = Profiles.list.value
    if (all.isEmpty()) return
    val active = Profiles.activeId.value
    val context = LocalContext.current
    Row(
        modifier.pointerInput(Unit) { detectTapGestures { ProfilePicker.request = true } },
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        all.take(Profiles.MAX).forEach { p ->
            val on = p.id == active
            val d = if (on) 40.dp else 32.dp
            Box(
                Modifier.size(d).clip(CircleShape)
                    .background(Color.hsv(p.hue.coerceIn(0f, 360f), 0.45f, 0.62f))
                    .then(if (on) Modifier.border(2.5.dp, Color.White, CircleShape) else Modifier.border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)),
                contentAlignment = Alignment.Center
            ) {
                val model = Profiles.pictureModel(context, p, raNames, steamAvatar)
                if (model != null) AsyncImage(model, p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text(p.name.take(1).uppercase(), color = Color.White, fontSize = if (on) 18.sp else 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
