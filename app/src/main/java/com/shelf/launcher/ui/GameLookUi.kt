package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.GameLooks
import com.shelf.launcher.data.GameState
import com.shelf.launcher.ui.theme.AppTheme
import com.shelf.launcher.ui.theme.Ds
import com.shelf.launcher.ui.theme.ShelfTheme
import com.shelf.launcher.ui.theme.ThemeGroup
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Game states (Settings › Library › Game states)
// ---------------------------------------------------------------------------------------------

/**
 * Every game's state mark (Game.stableKey → state), worked out by the home screen; empty while Game states
 * is off. Compose state: the cover, tile and carousel cards that read it update on their own.
 */
object GameStates {
    var byKey by androidx.compose.runtime.mutableStateOf<Map<String, GameState>>(emptyMap())
}

/** The colour that means this state (status colours only where they carry meaning). */
@Composable
internal fun GameState.tint(): Color = when (this) {
    GameState.NEW -> ShelfTheme.accent
    GameState.PLAYING -> Ds.success
    GameState.ON_HOLD -> Ds.warning
    GameState.COMPLETED -> Color(0xFF8FB8FF)
    GameState.MASTERED, GameState.PERFECT -> Ds.gold
}

/** The small state mark drawn on a cover's corner: dark plate, coloured dot and short label. */
@Composable
internal fun GameStateTag(state: GameState, modifier: Modifier = Modifier, compact: Boolean = false) {
    val tint = state.tint()
    androidx.compose.foundation.layout.Row(
        modifier
            .clip(RoundedCornerShape(Ds.rSmall))
            .background(Color.Black.copy(alpha = 0.66f))
            .padding(horizontal = if (compact) 5.dp else 6.dp, vertical = 2.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Spacer(Modifier.size(6.dp).drawBehind { drawCircle(tint) })
        Spacer(Modifier.width(4.dp))
        Text(
            state.label,
            color = if (state == GameState.MASTERED || state == GameState.PERFECT) tint else Color.White,
            fontSize = if (compact) 8.sp else 9.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(if (compact) 8.sp else 9.sp), maxFontSize = if (compact) 8.sp else 9.sp),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Per-game ambience and CRT treatment (Game settings › Look for this game)
// ---------------------------------------------------------------------------------------------

/**
 * A light mood layer over the background while a game with an ambience is focused: particles, rain,
 * snow, embers, CRT noise or a slow sweep of light in the art's colours. Pure drawing on a ~30 fps clock
 * (15 while idle), stopped while covered, deeply idle or with animations off; half the particles with
 * Lite effects. Never plays sound.
 */
@Composable
internal fun GameAmbienceLayer(
    ambience: GameLooks.Ambience,
    tint: Color,
    tint2: Color,
    covered: Boolean,
    modifier: Modifier = Modifier
) {
    val lite = LocalLiteEffects.current
    val active = !covered && rememberIsActive() && rememberAnimationsEnabled() && !HomeIdle.still
    val time = remember { mutableFloatStateOf(0f) }
    val step = if (HomeIdle.idle) 66_000_000L else 33_000_000L
    LaunchedEffect(active, step) {
        if (!active) return@LaunchedEffect
        val base = time.floatValue
        val start = withFrameNanos { it }
        // Sleep between steps instead of waking on every display frame.
        val stepMs = step / 1_000_000L
        while (true) {
            kotlinx.coroutines.delay((stepMs - 4L).coerceAtLeast(1L))
            withFrameNanos { now -> time.floatValue = base + (now - start) / 1e9f }
        }
    }
    val k = if (lite) 0.5f else 1f
    Canvas(modifier.fillMaxSize()) {
        val t = time.floatValue
        val w = size.width
        val h = size.height
        fun rnd(i: Int, salt: Int) = (((i * 73856093) xor (salt * 19349663)) and 0xFFFF) / 65535f
        when (ambience) {
            GameLooks.Ambience.MOTES -> {
                val n = (28 * k).toInt()
                for (i in 0 until n) {
                    val rise = 0.012f + 0.02f * rnd(i, 3)
                    val y = ((rnd(i, 1) - t * rise) % 1f + 1f) % 1f
                    val x = rnd(i, 2) + 0.02f * sin(t * 0.6f + i)
                    val tw = 0.5f + 0.5f * sin(t * (0.8f + rnd(i, 4)) + i * 1.7f)
                    val r = (1.4f + 2.2f * rnd(i, 5)) * density
                    val c = Offset(x * w, y * h)
                    drawCircle((if (i % 3 == 0) tint2 else tint).copy(alpha = 0.10f + 0.16f * tw), r * 3.2f, c)
                    drawCircle(Color.White.copy(alpha = 0.18f + 0.3f * tw), r * 0.8f, c)
                }
            }
            GameLooks.Ambience.RAIN -> {
                drawRect(Color(0xFF0A1830).copy(alpha = 0.16f))
                val n = (90 * k).toInt()
                val slant = 0.18f
                for (i in 0 until n) {
                    val depth = rnd(i, 7)
                    val fall = 0.9f + 1.3f * depth
                    val len = (10f + 26f * depth) * density
                    val y = ((rnd(i, 1) + t * fall) % 1.1f) * h - len
                    val x = (rnd(i, 2) * 1.2f - 0.1f) * w + y * slant
                    drawLine(
                        Color(0xFFCFE3FF).copy(alpha = 0.10f + 0.22f * depth),
                        Offset(x, y), Offset(x + len * slant, y + len),
                        strokeWidth = (0.6f + 1.1f * depth) * density, cap = StrokeCap.Round
                    )
                }
            }
            GameLooks.Ambience.SNOW -> {
                val n = (70 * k).toInt()
                for (i in 0 until n) {
                    val depth = rnd(i, 7)
                    val fall = 0.025f + 0.06f * depth
                    val y = ((rnd(i, 1) + t * fall) % 1f)
                    val x = rnd(i, 2) + 0.025f * sin(t * (0.5f + depth) + i)
                    drawCircle(Color.White.copy(alpha = 0.25f + 0.5f * depth), (1f + 2.6f * depth) * density, Offset(x * w, y * h))
                }
            }
            GameLooks.Ambience.EMBERS -> {
                drawRect(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color(0xFFFF5A1F).copy(alpha = 0.16f)))
                val n = (36 * k).toInt()
                for (i in 0 until n) {
                    val life = 3f + 3f * rnd(i, 3)
                    val ph = ((t + rnd(i, 1) * life) % life) / life
                    val x = rnd(i, 2) * w + sin(t * 1.3f + i) * 14f * density * ph
                    val y = h * (1.02f - ph * (0.45f + 0.4f * rnd(i, 4)))
                    val a = (1f - ph) * (0.35f + 0.5f * rnd(i, 5))
                    val c = Offset(x, y)
                    val col = if (i % 4 == 0) Color(0xFFFFD27A) else Color(0xFFFF7A2E)
                    drawCircle(col.copy(alpha = a * 0.35f), 5f * density, c)
                    drawCircle(col.copy(alpha = a), 1.4f * density, c)
                }
            }
            GameLooks.Ambience.CRT_NOISE -> {
                val gap = 3f * density
                var y = 0f
                while (y < h) { drawRect(Color.Black.copy(alpha = 0.16f), Offset(0f, y), Size(w, gap * 0.45f)); y += gap }
                // A soft bar rolling down every 6 s.
                val barY = ((t / 6f) % 1f) * (h + 120f * density) - 60f * density
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.05f), 1f to Color.Transparent, startY = barY, endY = barY + 120f * density),
                    Offset(0f, barY), Size(w, 120f * density)
                )
                // A little static, changing ~12 times a second.
                val frame = (t * 12f).toInt()
                val n = (60 * k).toInt()
                for (i in 0 until n) {
                    val sx = rnd(i, frame) * w
                    val sy = rnd(i + 911, frame) * h
                    drawRect(Color.White.copy(alpha = 0.05f + 0.08f * rnd(i, frame + 5)), Offset(sx, sy), Size(2f * density, 1f * density))
                }
            }
            GameLooks.Ambience.LIGHT -> {
                val a = t * 2f * PI.toFloat() / 24f
                val c = Offset(w * (0.5f + 0.38f * cos(a)), h * (0.42f + 0.22f * sin(a * 1.3f)))
                drawRect(Brush.radialGradient(listOf(tint.copy(alpha = 0.20f), Color.Transparent), c, w * 0.55f))
                val c2 = Offset(w * (0.5f - 0.33f * cos(a * 0.8f)), h * (0.6f - 0.2f * sin(a)))
                drawRect(Brush.radialGradient(listOf(tint2.copy(alpha = 0.14f), Color.Transparent), c2, w * 0.45f))
            }
        }
    }
}

/**
 * The CRT treatment for a game's background picture: fine scanlines, darker rounded edges like a curved
 * tube, and a faint glow of the picture's colour round the screen. Drawn once (no clock).
 */
@Composable
internal fun CrtArtOverlay(glow: Color, modifier: Modifier = Modifier) {
    Spacer(
        modifier.fillMaxSize().drawBehind {
            val gap = 3f * density
            var y = 0f
            while (y < size.height) { drawRect(Color.Black.copy(alpha = 0.22f), Offset(0f, y), Size(size.width, gap * 0.5f)); y += gap }
            // Curvature: the corners fall off into darkness.
            drawRect(
                Brush.radialGradient(
                    0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f),
                    center = center, radius = size.maxDimension * 0.62f
                )
            )
            // Phosphor glow hugging the screen edge in the art's colour.
            val edge = 18f * density
            drawRect(Brush.verticalGradient(0f to glow.copy(alpha = 0.16f), 1f to Color.Transparent, endY = edge * 3), size = Size(size.width, edge * 3))
            drawRect(
                Brush.verticalGradient(0f to Color.Transparent, 1f to glow.copy(alpha = 0.16f), startY = size.height - edge * 3, endY = size.height),
                topLeft = Offset(0f, size.height - edge * 3), size = Size(size.width, edge * 3)
            )
        }
    )
}

// ---------------------------------------------------------------------------------------------
// Game settings › Look for this game
// ---------------------------------------------------------------------------------------------

/**
 * Per-game look: background theme, artwork priority, animated artwork, CRT, theme intensity, ambience and
 * the game's state. Every row has "Inherit"/"Automatic", which follows the global setting.
 * [autoState] is what Automatic works out for this game now (shown so the choice is clear).
 */
@Composable
internal fun GameLookSection(game: Game, autoState: GameState?) {
    val key = game.stableKey
    val look = GameLooks.of(key)
    fun set(change: (GameLooks.Look) -> GameLooks.Look) = GameLooks.update(key, change)

    ShelfCard {
        SectionLabel("Look for this game")
        MutedNote(
            if (look.isEmpty()) "Everything follows your global settings." else "This game has its own look. Reset puts it back to the global settings."
        )
        Spacer(Modifier.height(10.dp))

        // Background theme
        val themeName = look.theme?.let { n -> AppTheme.values().firstOrNull { it.name == n }?.label }
        Text("Background theme" + (themeName?.let { " · $it" } ?: " · Inherit"), color = ShelfTheme.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        ChipFlow { Chip("Inherit", accent = look.theme == null) { set { it.copy(theme = null) } } }
        MutedNote("Plays as the animated background whenever this game is the one you're on (its own colours).")
        val groups = remember { ThemeGroup.values().map { g -> g to AppTheme.values().filter { it.group == g } }.filter { it.second.isNotEmpty() } }
        groups.forEach { (g, themes) ->
            val inGroup = themes.any { it.name == look.theme }
            FoldGroup(g.label, summary = if (inGroup) themeName else "${themes.size}", key = "gamelook_${g.name}") {
                ChipFlow {
                    themes.forEach { th -> Chip(th.label, accent = look.theme == th.name) { set { it.copy(theme = th.name) } } }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Artwork priority", color = ShelfTheme.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            Chip("Inherit (${Features.artPriority.state.value.label})", accent = look.artPriority == null) { set { it.copy(artPriority = null) } }
            Features.ArtPriority.values().forEach { p -> Chip(p.label, accent = look.artPriority == p) { set { it.copy(artPriority = p) } } }
        }

        Spacer(Modifier.height(12.dp))
        Text("Transition onto this game", color = ShelfTheme.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            Chip("Inherit", accent = look.transition == null) { set { it.copy(transition = null) } }
            com.shelf.launcher.data.HeroMotion.values().forEach { m -> Chip(m.label, accent = look.transition == m) { set { it.copy(transition = m) } } }
        }
        look.transition?.let { Spacer(Modifier.height(8.dp)); HeroMotionPreview(it) }

        Spacer(Modifier.height(12.dp))
        Text("Animated artwork", color = ShelfTheme.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            Chip("Inherit", accent = look.animateArt == null) { set { it.copy(animateArt = null) } }
            Chip("Play", accent = look.animateArt == true) { set { it.copy(animateArt = true) } }
            Chip("Still", accent = look.animateArt == false) { set { it.copy(animateArt = false) } }
        }

        Spacer(Modifier.height(12.dp))
        Text("CRT effect on the background", color = ShelfTheme.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            Chip("Off", accent = look.crt != true) { set { it.copy(crt = null) } }
            Chip("On", accent = look.crt == true) { set { it.copy(crt = true) } }
        }

        Spacer(Modifier.height(12.dp))
        Text("Theme intensity", color = ShelfTheme.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            Chip("Inherit (${Features.themeIntensity.state.value.label})", accent = look.intensity == null) { set { it.copy(intensity = null) } }
            Features.Intensity.values().forEach { i -> Chip(i.label, accent = look.intensity == i) { set { it.copy(intensity = i) } } }
        }

        Spacer(Modifier.height(12.dp))
        Text("Ambience", color = ShelfTheme.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            Chip("None", accent = look.ambience == null) { set { it.copy(ambience = null) } }
            GameLooks.Ambience.values().forEach { a -> Chip(a.label, accent = look.ambience == a) { set { it.copy(ambience = a) } } }
        }
        look.ambience?.let { MutedNote(it.blurb + " No sound.") }
        if (look.ambience != null && !Features.gameAmbience.state.value) {
            Spacer(Modifier.height(4.dp))
            MutedNote("Ambience is switched off for all games.")
            Chip("Switch ambience on", accent = false) { Features.gameAmbience.value = true }
        }

        Spacer(Modifier.height(12.dp))
        Text("State", color = ShelfTheme.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            Chip("Automatic" + (autoState?.let { " (${it.label})" } ?: ""), accent = look.state == null) { set { it.copy(state = null) } }
            GameState.values().forEach { st -> Chip(st.label, accent = look.state == st) { set { it.copy(state = st) } } }
        }
        if (!Features.gameStates.state.value) {
            Spacer(Modifier.height(4.dp))
            MutedNote("State marks on covers are off.")
            Chip("Show state marks", accent = false) { Features.gameStates.value = true }
        }

        if (!look.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Chip("Reset this game's look", accent = false) { GameLooks.reset(key) }
        }
    }
}
