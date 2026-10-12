package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * A new game arrives (Appearance › Home screen › New game arrival): a capsule in the theme's colours
 * slides down from the top edge, the game's cover drops into its round window with a bounce, a ring of
 * sparks bursts round it and the name types in — "NEW ARRIVAL". Several new games follow one another
 * (at most four, then "+N more"). [onDone] once they've all been shown.
 */
@Composable
internal fun NewArrivalShow(
    keys: List<String>,
    nameOf: (String) -> String,
    coverFor: (String) -> String?,
    consoleOf: (String) -> String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sounds = LocalUiSounds.current
    val done by rememberUpdatedState(onDone)
    val shown = keys.take(4)
    val extra = keys.size - shown.size
    var index by remember(keys.firstOrNull()) { mutableStateOf(0) }
    val slide = remember { Animatable(0f) }
    val drop = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val type = remember { Animatable(0f) }
    // Keyed on the first one: more arriving mid-show join the "+N more" count instead of restarting it.
    val shownNow by rememberUpdatedState(shown)
    LaunchedEffect(keys.firstOrNull()) {
        RetroFx.warm(context)
        var i = 0
        while (i < shownNow.size) {
            index = i
            drop.snapTo(0f); burst.snapTo(0f); type.snapTo(0f)
            if (i == 0) slide.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 260f))
            RetroFx.play(context, sounds, RetroSound.FLY)
            launch { drop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f)) }
            delay(260)
            RetroFx.play(context, sounds, RetroSound.LOCK)
            launch { burst.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
            type.animateTo(1f, tween(520))
            delay(if (i == shownNow.lastIndex) 2200 else 1300)
            i++
        }
        slide.animateTo(0f, tween(320))
        done()
    }
    val key = shown.getOrNull(index) ?: return
    val accent = ShelfTheme.accent
    val second = ShelfTheme.colors.secondary
    val capsule = RoundedCornerShape(50)
    Box(
        modifier.graphicsLayer {
            translationY = (1f - slide.value) * -220f
            alpha = slide.value.coerceIn(0f, 1f)
        }
    ) {
        Row(
            Modifier
                .widthIn(min = 260.dp, max = 460.dp)
                .clip(capsule)
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.92f), second.copy(alpha = 0.8f), Color(0xEE0B0C12))))
                .border(1.5.dp, Color.White.copy(alpha = 0.55f), capsule)
                .padding(start = 6.dp, end = 22.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The round window the cover drops into, with the spark ring.
            Box(
                Modifier
                    .size(58.dp)
                    .drawBehind {
                        val b = burst.value
                        if (b in 0.01f..0.99f) {
                            val r = size.minDimension / 2f * (1f + b * 0.9f)
                            drawCircle(Color.White.copy(alpha = (1f - b) * 0.9f), radius = r, style = Stroke(3.dp.toPx() * (1f - b) + 1f))
                            for (k in 0 until 10) {
                                val a = k / 10f * 6.2832f + b
                                val d = size.minDimension / 2f * (1.05f + b * 1.1f)
                                drawCircle(
                                    if (k % 2 == 0) Color.White.copy(alpha = 1f - b) else accent.copy(alpha = 1f - b),
                                    radius = 2.5.dp.toPx() * (1f - b * 0.6f),
                                    center = center + Offset(cos(a) * d, sin(a) * d)
                                )
                            }
                        }
                    }
                    .clip(CircleShape)
                    .background(Color(0xFF0B0C12))
                    .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = (1f - drop.value) * -140f
                            val s = 0.7f + 0.3f * drop.value
                            scaleX = s; scaleY = s
                        }
                ) {
                    val cover = coverFor(key)
                    if (cover != null) AsyncImage(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.5f)))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    "NEW ARRIVAL" + if (shown.size > 1) "  ${index + 1}/${shown.size}" else "",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp
                )
                val name = nameOf(key)
                // Types in, letter by letter.
                Text(
                    name.take((name.length * type.value).toInt().coerceAtLeast(if (type.value > 0f) 1 else 0)),
                    color = Color.White, fontSize = 16.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(16.sp), maxFontSize = 16.sp), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                val console = consoleOf(key)
                val tail = listOfNotNull(console, if (extra > 0 && index == shown.lastIndex) "+$extra more" else null).joinToString("  ·  ")
                if (tail.isNotBlank()) Text(tail, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}
