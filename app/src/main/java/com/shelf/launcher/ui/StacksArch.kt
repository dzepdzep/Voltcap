package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * Update 159: Case stacks restyled after a disc loader's home (at the user's request, in Voltcap's own drawing and the
 * theme's colours): a strip of small square tool buttons along the top (your round shortcuts) and a panel along the
 * bottom with the game's name, a big clock, the free storage and how many games there are, a big round button at each
 * end (Options, Play) and two small ones (Favourite, Details). The games in between: the stacks, or the carousel (its
 * Arch and Box flow views come from the loader pictures).
 */

/** The theme's colours for the toolbar and the panel: a light panel on a light theme, a dark one on a dark theme. */
private class ArchInk(val panel: Color, val panelEdge: Color, val ink: Color, val muted: Color, val accent: Color, val light: Boolean)

@Composable
private fun rememberArchInk(): ArchInk {
    val colors = ShelfTheme.colors
    val accent = ShelfTheme.accent
    return remember(colors, accent) {
        val light = colors.background.luminance() > 0.5f
        val base = if (light) Color(0xFFF4F6F9) else colors.surfaceRaised
        ArchInk(
            panel = lerp(base, accent, 0.06f).copy(alpha = 0.94f),
            panelEdge = accent.copy(alpha = 0.55f),
            ink = colors.textPrimary,
            muted = colors.textMuted,
            accent = accent,
            light = light
        )
    }
}

// ---- Top: the tool strip ----

/** Your round shortcuts as small square buttons in one strip (▲ from the games reaches them). */
@Composable
internal fun StacksToolbar(c: StyleCtx, modifier: Modifier) {
    val ink = rememberArchInk()
    val tools = c.shortcuts
    if (tools.isEmpty()) return
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.clip(shape).background(ink.panel).border(1.dp, ink.panelEdge.copy(alpha = 0.35f), shape).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        tools.forEachIndexed { i, t ->
            val on = StyleNav.on(StyleNav.Zone.TOP, i)
            val click by rememberUpdatedState(t.onClick)
            val s = RoundedCornerShape(6.dp)
            Box(
                Modifier.size(30.dp)
                    .clip(s)
                    .background(if (on) ink.accent.copy(alpha = 0.3f) else ink.ink.copy(alpha = 0.06f))
                    .border(if (on) 2.dp else 1.dp, if (on) ink.accent else ink.ink.copy(alpha = 0.12f), s)
                    .pointerInput(i) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.TOP, i); click() } },
                contentAlignment = Alignment.Center
            ) { Icon(t.icon, t.label, tint = if (on) ink.accent else ink.ink.copy(alpha = 0.75f), modifier = Modifier.size(17.dp)) }
        }
    }
}

// (Update 159: the arch of cases is a carousel view now — Carousel › Arch, Carousel.kt.)

// ---- Bottom: the panel ----

/** The panel's top edge rises gently into an arch in the middle. */
private class ArchTopShape(private val rise: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = rise * size.height
        val p = Path().apply {
            moveTo(0f, r)
            quadraticTo(size.width / 2f, -r, size.width, r)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(p)
    }
}

/** Free and total space on the storage the games are on, refreshed each minute: "8.20 GB of 114.58 GB free". */
@Composable
private fun rememberStorageLine(): String? {
    val line by produceState<String?>(null) {
        while (true) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val st = android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path)
                    val gb = 1024.0 * 1024.0 * 1024.0
                    "%.2f GB of %.2f GB free".format(java.util.Locale.US, st.availableBytes / gb, st.totalBytes / gb)
                }.getOrNull()
            }
            kotlinx.coroutines.delay(60_000)
        }
    }
    return line
}

/** The time as a big LCD clock (24 h or 12 h as the handheld is set), the "88:88" segments faintly behind it. */
@Composable
private fun ArchClock(ink: ArchInk, size: androidx.compose.ui.unit.TextUnit) {
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(10_000) }
    }
    val minute = now / 60_000L
    val text = remember(minute) {
        val p = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm"
        java.text.SimpleDateFormat(p, java.util.Locale.US).format(java.util.Date(minute * 60_000L))
    }
    Box(contentAlignment = Alignment.Center) {
        Text("88:88", color = ink.ink.copy(alpha = 0.07f), fontSize = size, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
        Text(text, color = ink.ink.copy(alpha = 0.85f), fontSize = size, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

/** The bottom panel: big round Options at the left, Favourite and Details, the name / clock / storage, big round Play. */
@Composable
internal fun StacksPanel(c: StyleCtx, g: Game, compact: Boolean, modifier: Modifier) {
    val ink = rememberArchInk()
    val titles = LocalTitleOptions.current
    val storage = rememberStorageLine()
    val fav = g.stableKey in c.state.favorites
    val played = c.lastPlayed(g) != null
    val favorite by rememberUpdatedState(c.actions.favorite)
    val options by rememberUpdatedState(c.actions.options)
    val open by rememberUpdatedState(c.actions.open)
    val play by rememberUpdatedState(c.actions.play)
    val game by rememberUpdatedState(g)
    val shape = remember { ArchTopShape(0.1f) }
    val big = if (compact) 46.dp else 58.dp
    val small = if (compact) 30.dp else 36.dp
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(ink.panel, lerp(ink.panel, ink.accent, 0.08f))))
            .drawArchEdge(ink.panelEdge),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = if (compact) 10.dp else 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArchRound(Icons.Filled.Tune, "Options", 0, big, ink, main = false) { options(game) }
            Spacer(Modifier.width(8.dp))
            ArchRound(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, "Favourite", 1, small, ink, tint = if (fav) Color(0xFFE8BE48) else null) { favorite(game) }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.animation.AnimatedContent(
                    targetState = g, label = "archName", transitionSpec = { gameSwap() }, contentAlignment = Alignment.Center
                ) { shownGame ->
                    Text(
                        titles.display(shownGame.name), color = ink.ink, fontSize = if (compact) 13.sp else 16.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
                }
                ArchClock(ink, if (compact) 26.sp else 34.sp)
                Text(
                    listOfNotNull(storage, "Games: ${c.games.size}").joinToString("  ·  "),
                    color = ink.muted, fontSize = if (compact) 9.sp else 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            ArchRound(Icons.Filled.Info, "Details", 2, small, ink) { open() }
            Spacer(Modifier.width(8.dp))
            ArchRound(Icons.Filled.PlayArrow, if (played) "Continue" else "Play", 3, big, ink, main = true) { play(game) }
        }
    }
}

/** A thin lit line along the panel's arched top edge, in the theme's accent. */
private fun Modifier.drawArchEdge(color: Color): Modifier = this.drawWithCache {
    val r = 0.1f * size.height
    val path = Path().apply { moveTo(0f, r); quadraticTo(size.width / 2f, -r, size.width, r) }
    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
    onDrawWithContent {
        drawContent()
        drawPath(path, color, style = stroke)
    }
}

/** A round button of the panel; [index] is its place in the D-pad's bar (▼ from the games, ◀ ▶ along it). */
@Composable
private fun ArchRound(
    icon: ImageVector, label: String, index: Int, size: Dp, ink: ArchInk, main: Boolean = false, tint: Color? = null, onClick: () -> Unit
) {
    val click by rememberUpdatedState(onClick)
    val on = StyleNav.on(StyleNav.Zone.BAR, index)
    val grow by animateFloatAsState(if (on) 1.1f else 1f, spring(stiffness = Features.rowGlide.state.value.stiffness), label = "archRound")
    Box(
        Modifier.size(size)
            .graphicsLayer { scaleX = grow; scaleY = grow }
            .clip(CircleShape)
            .background(
                if (main) Brush.verticalGradient(listOf(lerp(ink.accent, Color.White, 0.25f), ink.accent))
                else Brush.verticalGradient(listOf(lerp(ink.panel, Color.White, if (ink.light) 0.6f else 0.12f), ink.panel))
            )
            .border(if (on) 2.5.dp else 1.5.dp, if (on) ink.accent else ink.panelEdge.copy(alpha = 0.6f), CircleShape)
            .pointerInput(index) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.BAR, index); click() } },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = tint ?: if (main) Color.White else ink.ink.copy(alpha = 0.7f), modifier = Modifier.size(size * 0.48f))
    }
}
