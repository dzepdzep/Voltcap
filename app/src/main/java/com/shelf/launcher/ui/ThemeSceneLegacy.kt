package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import com.shelf.launcher.ui.theme.ShelfColors
import com.shelf.launcher.ui.theme.ThemeMotif
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The simpler Canvas versions of the scenes, for Android 12 and older (no runtime shaders there)
 * and as a safety net if a GPU scene ever fails to build. Sizes are given for a 1080-pixel-tall
 * screen and scaled to the real one.
 */
@Composable
internal fun LegacyThemeScene(motif: ThemeMotif, colors: ShelfColors, time: State<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val t = time.value
        drawRect(colors.background)
        // (update 91) An empty area (e.g. a preview still expanding) made the scaled size infinite: endless loops.
        if (size.width < 1f || size.height < 1f) return@Canvas
        val k = size.height / 1080f
        scale(k) {
            when (motif) {
                ThemeMotif.SPARKS -> sparks(t, colors)
                ThemeMotif.LIQUID -> liquid(t, colors)
                ThemeMotif.FLAMES -> flames(t, colors)
                ThemeMotif.ORBIT -> orbit(t, colors)
                ThemeMotif.RETRO_GRID -> retroGrid(t, colors)
                ThemeMotif.LEAVES -> leaves(t, colors, petals = false)
                ThemeMotif.MINIMAL -> minimal(t, colors)
                ThemeMotif.BUBBLES -> bubbles(t, colors)
                ThemeMotif.PSY_WAVES -> psyWaves(t, colors)
                ThemeMotif.WISPS -> wisps(t, colors)
                ThemeMotif.LCD -> lcd(t, colors)
                ThemeMotif.LEAF_SWIRL -> leafSwirl(t, colors)
                ThemeMotif.AURA -> aura(t, colors)
                ThemeMotif.AURORA -> aurora(t, colors)
                ThemeMotif.NEBULA -> nebula(t, colors)
                ThemeMotif.PIXELS -> pixels(t, colors)
                ThemeMotif.GLASS -> glass(t, colors)
                ThemeMotif.RAIN -> rain(t, colors)
                ThemeMotif.STARS -> stars(t, colors)
                ThemeMotif.PETALS -> leaves(t, colors, petals = true)
                ThemeMotif.SCANLINES -> scanlines(t, colors)
                ThemeMotif.MUSIC, ThemeMotif.SPECTRUM, ThemeMotif.PULSE_RING, ThemeMotif.BEAT_TUNNEL,
                ThemeMotif.REACTOR, ThemeMotif.THUNDER, ThemeMotif.BLOOM,
                ThemeMotif.TAPE_DECK, ThemeMotif.CHLADNI, ThemeMotif.LASER_STAGE, ThemeMotif.KALEIDO,
                ThemeMotif.SHATTER, ThemeMotif.TERRAIN, ThemeMotif.FIREWORKS, ThemeMotif.NEON_PUDDLE,
                ThemeMotif.VINYL, ThemeMotif.CD_SPIN, ThemeMotif.LAVA, ThemeMotif.RIBBONS, ThemeMotif.DISCO, ThemeMotif.SPEAKER ->
                    minimal(t, colors) // drawn by NowPlayingScene instead
                ThemeMotif.CIRCUIT -> rain(t, colors)
                ThemeMotif.GEARWORKS -> orbit(t, colors)
                ThemeMotif.FROST -> stars(t, colors)
                ThemeMotif.TERRA -> flames(t, colors)
                ThemeMotif.VENOM -> bubbles(t, colors)
                ThemeMotif.CRT_TUNNEL -> scanlines(t, colors)
                ThemeMotif.MEMORY_CARD -> pixels(t, colors)
                ThemeMotif.ENERGY_CORE -> orbit(t, colors)
                ThemeMotif.RACING_32 -> retroGrid(t, colors)
                ThemeMotif.FIRE_32 -> minimal(t, colors)
                ThemeMotif.MEADOW -> leaves(t, colors, petals = false)
                ThemeMotif.DOJO -> flames(t, colors)
                ThemeMotif.GALE -> aurora(t, colors)
                ThemeMotif.THICKET -> stars(t, colors)
                ThemeMotif.BEDROCK -> flames(t, colors)
                ThemeMotif.WYRM -> nebula(t, colors)
                ThemeMotif.UMBRA -> stars(t, colors)
                ThemeMotif.ALLOY -> orbit(t, colors)
                ThemeMotif.GLIMMER -> leaves(t, colors, petals = true)
                ThemeMotif.ANIME_FLAMES -> flames(t, colors)
                ThemeMotif.CUSTOM_SCENE, ThemeMotif.CUSTOM_VIZ -> minimal(t, colors)
                ThemeMotif.CEL_SKY -> aurora(t, colors)
                ThemeMotif.MECHA_HUD -> orbit(t, colors)
                ThemeMotif.MOON_BLADE -> leaves(t, colors, petals = true)
                ThemeMotif.CAUSTICS -> liquid(t, colors)
                ThemeMotif.INK -> wisps(t, colors)
                ThemeMotif.PAPERCUT -> leaves(t, colors, petals = false)
                ThemeMotif.PARALLAX16 -> pixels(t, colors)
                ThemeMotif.HOLO -> glass(t, colors)
                ThemeMotif.CAMPFIRE -> flames(t, colors)
                ThemeMotif.ERA_8BIT, ThemeMotif.MODE7 -> pixels(t, colors)
                ThemeMotif.PS1_POLY, ThemeMotif.VECTOR -> retroGrid(t, colors)
                ThemeMotif.PS2_HAZE, ThemeMotif.WARP, ThemeMotif.ORBIT_STATION -> stars(t, colors)
                ThemeMotif.PRISM_ATRIUM -> retroGrid(t, colors)
                ThemeMotif.N64_PLAINS -> aurora(t, colors)
                ThemeMotif.BLUE_SKY -> blueSky(t, colors) // (v0.6.66, D123)
                ThemeMotif.XMB_WAVE -> liquid(t, colors)
                ThemeMotif.PLAZA -> bubbles(t, colors)
                ThemeMotif.ARENA -> stars(t, colors)
                ThemeMotif.ART_WALL, ThemeMotif.ART_TUNNEL, ThemeMotif.POP_ART, ThemeMotif.COVER_RIPPLE,
                ThemeMotif.BLAZE_BEAT -> minimal(t, colors) // drawn by NowPlayingScene instead
                ThemeMotif.SPECIMEN_LAB -> rain(t, colors)
                ThemeMotif.VOLT_BEAT -> minimal(t, colors) // drawn by NowPlayingScene instead
                ThemeMotif.VOLT_INTRO -> sparks(t, colors)
                ThemeMotif.VOLT_PIXELS, ThemeMotif.PIXEL_FALLS -> pixels(t, colors)
                ThemeMotif.RETRO_BOUNCE -> pixels(t, colors)
                ThemeMotif.LYRIC_CLOUD, ThemeMotif.LYRIC_KARAOKE, ThemeMotif.LYRIC_SHEET,
                ThemeMotif.LYRIC_NEON -> minimal(t, colors) // drawn by NowPlayingScene instead
                ThemeMotif.MAGICAL -> magical(t, colors) // (v0.6.66, D123)
                ThemeMotif.SPIRIT_FOREST -> leaves(t, colors, petals = false)
                ThemeMotif.FESTIVAL -> stars(t, colors)
                ThemeMotif.AURA_CLASH -> flames(t, colors)
                ThemeMotif.AURA_BEAT -> minimal(t, colors)
                ThemeMotif.MOUNTAINS -> minimal(t, colors)
                ThemeMotif.SPECIMEN_BEAT -> minimal(t, colors)
                ThemeMotif.TOWERS_128 -> minimal(t, colors)
                ThemeMotif.CARTRIDGE_16 -> minimal(t, colors)
                ThemeMotif.HANDHELD -> minimal(t, colors)
                ThemeMotif.PIXEL_PARADE -> minimal(t, colors)
                ThemeMotif.CAMPFIRE_BEAT -> minimal(t, colors)
                ThemeMotif.MANGA_BEAT -> mangaBeat(t, colors) // (v0.6.66, D123)
                ThemeMotif.COMIC_PANELS -> stars(t, colors)
                ThemeMotif.PIXEL_SKY -> pixelSky(t, colors) // (v0.6.66, D123)
                ThemeMotif.COVER_FLOW -> stars(t, colors)
                ThemeMotif.RECORD_SHELF -> stars(t, colors)
                ThemeMotif.COVER_CUBE -> stars(t, colors)
                ThemeMotif.COVER_FLOAT -> stars(t, colors)
                ThemeMotif.HOLO_COVER -> stars(t, colors)
                ThemeMotif.COVER_HALO -> stars(t, colors)
                ThemeMotif.BIG_TYPE -> stars(t, colors)
                ThemeMotif.PIXEL_COVER -> stars(t, colors)
                ThemeMotif.POLAROID -> stars(t, colors)
                ThemeMotif.RETRO_TV -> stars(t, colors)
                ThemeMotif.JUKEBOX -> stars(t, colors)
                ThemeMotif.NEON_FRAME -> stars(t, colors)
                ThemeMotif.MAGAZINE -> stars(t, colors)
                ThemeMotif.COVER_FOCUS -> stars(t, colors)
                ThemeMotif.SHOWCASE_PARALLAX, ThemeMotif.SHOWCASE_CINEMA, ThemeMotif.SHOWCASE_FILMSTRIP,
                ThemeMotif.SHOWCASE_MOSAIC, ThemeMotif.SHOWCASE_GLASS, ThemeMotif.SHOWCASE_CRT -> minimal(t, colors)
                ThemeMotif.DISC_SLEEVE, ThemeMotif.ALBUM_TIDE -> stars(t, colors)
                ThemeMotif.HANDHELD_BOOT, ThemeMotif.PIXEL_WORLD, ThemeMotif.PX_BEDROCK -> pixels(t, colors)
                ThemeMotif.POCKET_BEAT -> minimal(t, colors) // drawn by NowPlayingScene instead
                ThemeMotif.CONSOLE_DISC, ThemeMotif.PX_ALLOY -> orbit(t, colors)
                ThemeMotif.ARCADE_ATTRACT -> scanlines(t, colors)
                ThemeMotif.NEON_CARTRIDGE, ThemeMotif.DATA_STREAM -> retroGrid(t, colors)
                ThemeMotif.PORTAL_GALLERY, ThemeMotif.PX_WYRM -> nebula(t, colors)
                ThemeMotif.POSTER_WALL, ThemeMotif.GAMING_ROOM -> glass(t, colors)
                ThemeMotif.TROPHY_VAULT, ThemeMotif.BADGE_STARS, ThemeMotif.BADGE_BOUNCE, ThemeMotif.PX_FROST, ThemeMotif.PX_UMBRA -> stars(t, colors)
                ThemeMotif.PX_SPARK -> sparks(t, colors)
                ThemeMotif.PX_TORRENT, ThemeMotif.PX_VENOM -> bubbles(t, colors)
                ThemeMotif.PX_SPROUT, ThemeMotif.PX_MEADOW, ThemeMotif.PX_THICKET -> leaves(t, colors, petals = false)
                ThemeMotif.PX_GLIMMER -> leaves(t, colors, petals = true)
                ThemeMotif.PX_MIND -> psyWaves(t, colors)
                ThemeMotif.PX_BLAZE, ThemeMotif.PX_TERRA, ThemeMotif.PX_DOJO -> flames(t, colors)
                ThemeMotif.PX_PHANTOM -> wisps(t, colors)
                ThemeMotif.PX_GALE -> aurora(t, colors)
                ThemeMotif.STILL_DUSK, ThemeMotif.STILL_HORIZON, ThemeMotif.STILL_GLOW,
                ThemeMotif.CALM_RIBBON, ThemeMotif.CALM_DRIFT, ThemeMotif.CALM_HALO,
                ThemeMotif.BOLD_SPLIT, ThemeMotif.BOLD_DOTS, ThemeMotif.BOLD_BANDS -> minimal(t, colors)
                ThemeMotif.CHILL_BLADES -> glass(t, colors)
                ThemeMotif.CHILL_POLAR -> aurora(t, colors)
                ThemeMotif.CHILL_PLANET -> orbit(t, colors)
                ThemeMotif.CHILL_MOON, ThemeMotif.CHILL_CITY, ThemeMotif.CHILL_PEAKS -> stars(t, colors)
                ThemeMotif.CHILL_ORBS, ThemeMotif.CHILL_JELLY -> bubbles(t, colors)
                ThemeMotif.CHILL_SILK, ThemeMotif.CHILL_DUNES, ThemeMotif.CHILL_LIGHT, ThemeMotif.CHILL_HEX, ThemeMotif.CHILL_CLOUDS -> minimal(t, colors)
                ThemeMotif.CHILL_MESH -> nebula(t, colors)
                ThemeMotif.CHILL_SHADE -> leaves(t, colors, petals = false)
                ThemeMotif.CHILL_RAIN -> rain(t, colors)
                ThemeMotif.CHILL_GLADE -> wisps(t, colors)
                ThemeMotif.CHILL_KOI -> liquid(t, colors)
                ThemeMotif.LOBBY -> minimal(t, colors) // Update 158: drawn by LobbyScene instead
            }
        }
        drawSceneShade()
    }
}

/** Draws [block] as if the screen were 1080 px tall, so the hand-tuned sizes look the same everywhere. */
private inline fun DrawScope.scale(k: Float, block: ScaledScope.() -> Unit) {
    ScaledScope(this, k).block()
}

/** A DrawScope view whose `size` is the screen in 1080p units; drawing is scaled back up. */
private class ScaledScope(val scope: DrawScope, val k: Float) {
    val size: Size = Size(scope.size.width / k, scope.size.height / k)
}

private inline fun ScaledScope.draw(crossinline block: DrawScope.() -> Unit) {
    val s = k
    with(scope) {
        drawContext.transform.scale(s, s, Offset.Zero)
        try { block() } finally { drawContext.transform.scale(1f / s, 1f / s, Offset.Zero) }
    }
}

// ---- helpers ----

/** Deterministic 0..1 noise per index (stable positions, no allocation per frame). */
private fun h(i: Int, salt: Int = 0): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFFFF) / 16777215f
}

private fun DrawScope.glowDot(c: Color, at: Offset, r: Float, alpha: Float) {
    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = alpha), Color.Transparent), at, r), r, at)
}

// ---- motifs ----

private fun ScaledScope.sparks(t: Float, c: ShelfColors) = draw {
    val size = this@sparks.size
    glowDot(c.accent, Offset(size.width * 0.8f, size.height * 0.2f), size.minDimension * 0.6f, 0.18f)
    for (i in 0 until 9) {
        val phase = (t * 1.3f + h(i) * 10f) % 3f
        if (phase > 0.35f) continue
        val x0 = h(i, 1) * size.width
        val y0 = h(i, 2) * size.height * 0.7f
        val p = Path().apply {
            moveTo(x0, y0)
            var x = x0; var y = y0
            repeat(5) { k ->
                x += (h(i * 7 + k, 3) - 0.5f) * 60f
                y += 18f + h(i * 7 + k, 4) * 24f
                lineTo(x, y)
            }
        }
        val a = 1f - phase / 0.35f
        drawPath(p, c.accent.copy(alpha = 0.35f * a), style = Stroke(8f))
        drawPath(p, Color.White.copy(alpha = 0.8f * a), style = Stroke(2f))
    }
}

private fun ScaledScope.liquid(t: Float, c: ShelfColors) = draw {
    val size = this@liquid.size
    for (layer in 0 until 3) {
        val p = Path()
        val base = size.height * (0.45f + layer * 0.14f)
        p.moveTo(0f, size.height)
        var x = 0f
        while (x <= size.width + 20f) {
            val y = base + sin(x / (160f + layer * 50f) + t * (0.5f + layer * 0.2f)) * (18f + layer * 8f)
            p.lineTo(x, y); x += 20f
        }
        p.lineTo(size.width, size.height); p.close()
        drawPath(p, lerp(c.accent, c.secondary, layer / 2f).copy(alpha = 0.10f + layer * 0.05f))
    }
}

private fun ScaledScope.flames(t: Float, c: ShelfColors) = draw {
    val size = this@flames.size
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, c.accent.copy(alpha = 0.25f)), startY = size.height * 0.4f, endY = size.height), size = size)
    for (i in 0 until 40) {
        val speed = 0.03f + h(i) * 0.05f
        val y = size.height * (1f - ((t * speed + h(i, 1)) % 1f))
        val x = h(i, 2) * size.width + sin(t + i) * 14f
        val a = (y / size.height).coerceIn(0f, 1f)
        glowDot(if (i % 3 == 0) c.secondary else c.accent, Offset(x, y), 6f + h(i, 3) * 8f, 0.7f * a)
    }
}

private fun ScaledScope.orbit(t: Float, c: ShelfColors) = draw {
    val size = this@orbit.size
    val center = Offset(size.width * 0.72f, size.height * 0.32f)
    glowDot(c.accent, center, size.minDimension * 0.18f, 0.5f)
    for (ring in 1..4) {
        val r = size.minDimension * 0.12f * ring
        drawCircle(c.secondary.copy(alpha = 0.10f), r, center, style = Stroke(1.5f))
        val a = t * (0.6f / ring) + ring
        drawCircle(lerp(c.accent, c.secondary, ring / 4f), 3f + ring, center + Offset(cos(a) * r, sin(a) * r))
    }
}

private fun ScaledScope.retroGrid(t: Float, c: ShelfColors) = draw {
    val size = this@retroGrid.size
    val horizon = size.height * 0.55f
    val sun = Offset(size.width / 2f, horizon - size.height * 0.08f)
    val r = size.minDimension * 0.22f
    drawCircle(Brush.verticalGradient(listOf(c.secondary, c.accent), sun.y - r, sun.y + r), r, sun)
    for (k in 0 until 5) {
        val y = sun.y + r * (0.15f + k * 0.17f)
        drawRect(c.background, Offset(sun.x - r, y), Size(r * 2, 3f + k * 1.6f))
    }
    drawRect(c.background, Offset(0f, horizon), Size(size.width, size.height - horizon))
    val line = c.accent.copy(alpha = 0.5f)
    for (k in -12..12) {
        drawLine(line, Offset(size.width / 2f + k * 30f, horizon), Offset(size.width / 2f + k * 180f, size.height), 1.5f)
    }
    val scroll = (t * 0.35f) % 1f
    for (k in 0 until 10) {
        val f = ((k + scroll) / 10f)
        val y = horizon + (size.height - horizon) * f * f
        drawLine(line, Offset(0f, y), Offset(size.width, y), 1.5f)
    }
}

private fun ScaledScope.leaves(t: Float, c: ShelfColors, petals: Boolean) = draw {
    val size = this@leaves.size
    for (i in 0 until 26) {
        val fall = 0.025f + h(i) * 0.03f
        val y = size.height * ((t * fall + h(i, 1)) % 1f) * 1.1f - 20f
        val x = (h(i, 2) * size.width + sin(t * 0.8f + i) * 40f + t * 8f) % size.width
        val col = if (i % 2 == 0) c.accent else c.secondary
        rotate(t * 40f * (if (i % 2 == 0) 1 else -1) + i * 30f, Offset(x, y)) {
            if (petals) {
                drawOval(col.copy(alpha = 0.55f), Offset(x - 7f, y - 4f), Size(14f, 8f))
            } else {
                val leaf = Path().apply {
                    moveTo(x - 10f, y); quadraticBezierTo(x, y - 8f, x + 10f, y); quadraticBezierTo(x, y + 8f, x - 10f, y); close()
                }
                drawPath(leaf, col.copy(alpha = 0.45f))
            }
        }
    }
}

private fun ScaledScope.minimal(t: Float, c: ShelfColors) = draw {
    val size = this@minimal.size
    val x = size.width * (0.5f + 0.35f * sin(t * 0.08f))
    glowDot(c.accent, Offset(x, size.height * 0.3f), size.minDimension * 0.7f, 0.10f)
}

private fun ScaledScope.bubbles(t: Float, c: ShelfColors) = draw {
    val size = this@bubbles.size
    drawRect(Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.10f), Color.Transparent), 0f, size.height), size = size)
    for (i in 0 until 30) {
        val speed = 0.02f + h(i) * 0.04f
        val y = size.height * (1.05f - ((t * speed + h(i, 1)) % 1.1f))
        val x = h(i, 2) * size.width + sin(t * 1.4f + i) * 10f
        val r = 4f + h(i, 3) * 14f
        drawCircle(c.secondary.copy(alpha = 0.25f), r, Offset(x, y), style = Stroke(1.5f))
        drawCircle(Color.White.copy(alpha = 0.25f), r * 0.25f, Offset(x - r * 0.35f, y - r * 0.35f))
    }
}

private fun ScaledScope.psyWaves(t: Float, c: ShelfColors) = draw {
    val size = this@psyWaves.size
    val center = Offset(size.width * 0.5f, size.height * 0.35f)
    for (k in 0 until 7) {
        val r = ((t * 40f + k * 70f) % 490f)
        val a = (1f - r / 490f)
        drawCircle(lerp(c.accent, c.secondary, k / 6f).copy(alpha = 0.25f * a), r, center, style = Stroke(3f))
    }
}

private fun ScaledScope.wisps(t: Float, c: ShelfColors) = draw {
    val size = this@wisps.size
    for (i in 0 until 7) {
        val x = size.width * h(i) + sin(t * 0.3f + i) * 60f
        val y = size.height * (0.2f + 0.5f * h(i, 1)) + cos(t * 0.25f + i * 2) * 30f
        val pulse = 0.5f + 0.5f * sin(t * 0.9f + i)
        glowDot(if (i % 2 == 0) c.accent else c.secondary, Offset(x, y), 40f + 30f * pulse, 0.22f + 0.15f * pulse)
    }
}

private fun ScaledScope.lcd(t: Float, c: ShelfColors) = draw {
    val size = this@lcd.size
    val cell = maxOf(14f, size.minDimension / 48f)
    val cols = (size.width / cell).toInt() + 1
    val rows = (size.height / cell).toInt() + 1
    val shades = listOf(c.background, c.surface, c.surfaceRaised, c.accent.copy(alpha = 0.35f))
    val shift = (t * 2f).toInt()
    for (y in 0 until rows) for (x in 0 until cols) {
        val v = ((x + shift) / 6 + y / 6) % 7
        if (v > 3) continue
        drawRect(shades[v], Offset(x * cell, y * cell), Size(cell - 1.5f, cell - 1.5f))
    }
}

private fun ScaledScope.leafSwirl(t: Float, c: ShelfColors) {
    draw {
        val size = this@leafSwirl.size
        glowDot(c.accent, Offset(size.width * 0.2f, size.height * 0.15f), size.minDimension * 0.7f, 0.20f)
        // (No spiral any more: the swirl read as a tornado.)
    }
    leaves(t, c, petals = false)
}

private fun ScaledScope.aura(t: Float, c: ShelfColors) = draw {
    val size = this@aura.size
    val base = size.height
    for (i in 0 until 14) {
        val x = size.width * (i + 0.5f) / 14f
        val hgt = size.height * (0.25f + 0.25f * abs(sin(t * 3f + i * 1.7f)))
        val p = Path().apply {
            moveTo(x - 40f, base); quadraticBezierTo(x - 10f, base - hgt * 0.6f, x, base - hgt)
            quadraticBezierTo(x + 10f, base - hgt * 0.6f, x + 40f, base); close()
        }
        drawPath(p, Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.0f), c.accent.copy(alpha = 0.35f)), base - hgt, base))
    }
    for (i in 0 until 16) {
        val y = size.height * h(i)
        val len = 80f + h(i, 1) * 200f
        val x = ((t * (600f + h(i, 2) * 600f) + h(i, 3) * size.width) % (size.width + len)) - len
        drawLine(c.secondary.copy(alpha = 0.25f), Offset(x, y), Offset(x + len, y), 2f)
    }
}

private fun ScaledScope.aurora(t: Float, c: ShelfColors) {
    draw {
        val size = this@aurora.size
        for (band in 0 until 3) {
            val p = Path()
            val top = size.height * (0.12f + band * 0.08f)
            p.moveTo(0f, top)
            var x = 0f
            while (x <= size.width + 20f) {
                p.lineTo(x, top + sin(x / 220f + t * 0.3f + band) * 40f); x += 20f
            }
            p.lineTo(size.width, top + 220f); p.lineTo(0f, top + 220f); p.close()
            val col = lerp(c.accent, c.secondary, band / 2f)
            drawPath(p, Brush.verticalGradient(listOf(col.copy(alpha = 0.28f), Color.Transparent), top - 40f, top + 220f))
        }
    }
    stars(t, c, count = 40)
}

private fun ScaledScope.nebula(t: Float, c: ShelfColors) {
    draw {
        val size = this@nebula.size
        for (i in 0 until 5) {
            val x = size.width * (0.2f + 0.6f * h(i)) + sin(t * 0.05f + i) * 50f
            val y = size.height * (0.15f + 0.5f * h(i, 1)) + cos(t * 0.04f + i) * 40f
            glowDot(if (i % 2 == 0) c.accent else c.secondary, Offset(x, y), size.minDimension * (0.3f + 0.2f * h(i, 2)), 0.18f)
        }
    }
    stars(t, c, count = 60)
}

private fun ScaledScope.pixels(t: Float, c: ShelfColors) = draw {
    val size = this@pixels.size
    val cell = 18f
    for (i in 0 until 60) {
        val col = (h(i) * size.width / cell).toInt()
        val speed = 20f + h(i, 1) * 40f
        val y = ((t * speed + h(i, 2) * size.height) % (size.height + cell * 6)) - cell * 6
        repeat(4) { k ->
            val a = (1f - k / 4f) * 0.45f
            drawRect((if (i % 2 == 0) c.accent else c.secondary).copy(alpha = a), Offset(col * cell, y - k * cell), Size(cell - 3f, cell - 3f))
        }
    }
}

private fun ScaledScope.glass(t: Float, c: ShelfColors) = draw {
    val size = this@glass.size
    for (i in 0 until 5) {
        val w = size.width * (0.25f + 0.2f * h(i))
        val hh = size.height * (0.2f + 0.2f * h(i, 1))
        val x = size.width * h(i, 2) - w / 2 + sin(t * 0.1f + i) * 30f
        val y = size.height * h(i, 3) * 0.7f + cos(t * 0.08f + i) * 20f
        drawRoundRect(c.secondary.copy(alpha = 0.05f), Offset(x, y), Size(w, hh), CornerRadius(28f))
        drawRoundRect(c.accent.copy(alpha = 0.18f), Offset(x, y), Size(w, hh), CornerRadius(28f), style = Stroke(1.5f))
    }
}

private fun ScaledScope.rain(t: Float, c: ShelfColors) = draw {
    val size = this@rain.size
    val col = 16f
    val n = (size.width / col).toInt()
    for (i in 0 until n) {
        val speed = 60f + h(i) * 120f
        val head = (t * speed + h(i, 1) * size.height * 2) % (size.height * 1.5f)
        for (k in 0 until 14) {
            val y = head - k * col
            if (y < 0 || y > size.height) continue
            val a = (1f - k / 14f) * 0.5f
            drawRect((if (k == 0) c.secondary else c.accent).copy(alpha = a), Offset(i * col + 4f, y), Size(6f, 10f))
        }
    }
}

private fun ScaledScope.stars(t: Float, c: ShelfColors, count: Int = 80) = draw {
    val size = this@stars.size
    for (i in 0 until count) {
        val x = h(i, 5) * size.width
        val y = h(i, 6) * size.height * 0.8f
        val tw = 0.4f + 0.6f * abs(sin(t * (0.5f + h(i, 7)) + i))
        drawCircle(Color.White.copy(alpha = 0.6f * tw), 1f + h(i, 8) * 1.6f, Offset(x, y))
    }
    if (count >= 80) {
        val pts = (0 until 6).map { Offset(size.width * (0.55f + 0.35f * h(it, 9)), size.height * (0.1f + 0.35f * h(it, 10))) }
        val a = 0.15f + 0.15f * sin(t * 0.4f)
        for (k in 0 until pts.size - 1) drawLine(c.accent.copy(alpha = a), pts[k], pts[k + 1], 1.2f)
        pts.forEach { glowDot(c.secondary, it, 8f, 0.7f) }
    }
}

private fun ScaledScope.scanlines(t: Float, c: ShelfColors) = draw {
    val size = this@scanlines.size
    glowDot(c.accent, Offset(size.width * 0.3f, size.height * 0.3f), size.minDimension * 0.6f, 0.14f)
    glowDot(c.secondary, Offset(size.width * 0.8f, size.height * 0.5f), size.minDimension * 0.5f, 0.10f)
    var y = 0f
    while (y < size.height) { drawRect(Color.Black.copy(alpha = 0.25f), Offset(0f, y), Size(size.width, 1.5f)); y += 4f }
    val band = (t * 60f) % (size.height + 120f) - 60f
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.05f), Color.Transparent), band - 60f, band + 60f), size = size)
}

// ---- v0.6.66 (D123): Android 12 versions of four themes that showed a near-black frame there ----
// (No painting exists for them — ScenePainting — so the Canvas scene draws the theme's own subject in its own colours:
// a manga page, a magical-girl sky, a 90s racer's sky and sea, a pixel sky. Plain shapes, no blur, no tint, cheap.)

/** Manga beat: screentone dots, speed lines rushing to the middle, panel borders and an impact burst now and then. */
private fun ScaledScope.mangaBeat(t: Float, c: ShelfColors) = draw {
    val size = this@mangaBeat.size
    val cx = size.width / 2f; val cy = size.height * 0.45f
    val maxD = kotlin.math.hypot(size.width, size.height) / 2f
    // Screentone: a dot grid, darker (bigger dots) towards the edges, as a manga page's shading.
    val step = 28f
    var y = step / 2f
    while (y < size.height) {
        var x = step / 2f + if (((y / step).toInt() % 2) == 1) step / 2f else 0f
        while (x < size.width) {
            val d = kotlin.math.hypot(x - cx, y - cy) / maxD
            if (d > 0.35f) drawCircle(Color.White.copy(alpha = 0.10f), 1.5f + 5f * (d - 0.35f), Offset(x, y))
            x += step
        }
        y += step
    }
    // Speed lines, pulsing in and out like a beat.
    val pulse = 0.5f + 0.5f * sin(t * 2.6f)
    for (i in 0 until 56) {
        val a = (i / 56f) * 2f * PI.toFloat() + h(i) * 0.05f
        val inner = maxD * (0.42f + 0.12f * h(i, 1) - 0.06f * pulse)
        val dx = cos(a); val dy = sin(a)
        drawLine(Color.White.copy(alpha = 0.09f + 0.07f * h(i, 2)), Offset(cx + dx * inner, cy + dy * inner),
            Offset(cx + dx * maxD * 1.2f, cy + dy * maxD * 1.2f), 2f + 5f * h(i, 3))
    }
    // An impact burst in the accent, every few seconds, fading out.
    val ph = (t / 3.2f) % 1f
    if (ph < 0.3f) {
        val k = ph / 0.3f
        val bx = size.width * (0.2f + 0.6f * h((t / 3.2f).toInt(), 4)); val by = size.height * (0.2f + 0.5f * h((t / 3.2f).toInt(), 5))
        val r = 60f + 90f * k
        val p = Path()
        for (j in 0 until 24) {
            val a = j / 24f * 2f * PI.toFloat(); val rr = if (j % 2 == 0) r else r * 0.55f
            if (j == 0) p.moveTo(bx + cos(a) * rr, by + sin(a) * rr) else p.lineTo(bx + cos(a) * rr, by + sin(a) * rr)
        }
        p.close()
        drawPath(p, c.accent.copy(alpha = 0.35f * (1f - k)))
        drawPath(p, Color.White.copy(alpha = 0.5f * (1f - k)), style = Stroke(3f))
    }
    // Panel borders: the page's frames.
    val ink = Color.White.copy(alpha = 0.16f)
    drawRect(ink, Offset(24f, 24f), Size(size.width - 48f, size.height - 48f), style = Stroke(6f))
    drawLine(ink, Offset(size.width * 0.62f, 24f), Offset(size.width * 0.56f, size.height * 0.34f), 6f)
    drawLine(ink, Offset(24f, size.height * 0.34f), Offset(size.width - 24f, size.height * 0.30f), 6f)
}

/** Magical sparkle: soft pastel light, four-point star glints, falling sparkle dust and a ring of light now and then. */
private fun ScaledScope.magical(t: Float, c: ShelfColors) = draw {
    val size = this@magical.size
    glowDot(c.accent, Offset(size.width * 0.25f, size.height * 0.25f), size.minDimension * 0.7f, 0.16f)
    glowDot(c.secondary, Offset(size.width * 0.8f, size.height * 0.7f), size.minDimension * 0.6f, 0.14f)
    // Sparkle dust drifting down.
    for (i in 0 until 50) {
        val x = h(i, 11) * size.width + sin(t * 0.7f + i) * 14f
        val yy = (h(i, 12) * size.height + t * (18f + 26f * h(i, 13))) % size.height
        drawCircle((if (i % 3 == 0) c.secondary else Color.White).copy(alpha = 0.35f + 0.35f * abs(sin(t * 2f + i))), 1.5f + 2f * h(i, 14), Offset(x, yy))
    }
    // Four-point star glints.
    for (i in 0 until 12) {
        val x = h(i, 15) * size.width; val yy = h(i, 16) * size.height
        val tw = abs(sin(t * (0.6f + 0.5f * h(i, 17)) + i * 1.7f))
        val r = (14f + 26f * h(i, 18)) * (0.4f + 0.6f * tw)
        val col = (if (i % 2 == 0) c.accent else c.secondary)
        val p = Path().apply {
            moveTo(x, yy - r); quadraticTo(x, yy, x + r, yy); quadraticTo(x, yy, x, yy + r)
            quadraticTo(x, yy, x - r, yy); quadraticTo(x, yy, x, yy - r); close()
        }
        drawPath(p, col.copy(alpha = 0.55f * tw))
        drawCircle(Color.White.copy(alpha = 0.8f * tw), r * 0.12f, Offset(x, yy))
    }
    // A ring of light bursting from the middle every few seconds.
    val ph = (t / 5f) % 1f
    if (ph < 0.45f) {
        val k = ph / 0.45f
        drawCircle(c.accent.copy(alpha = 0.3f * (1f - k)), size.minDimension * (0.1f + 0.6f * k), Offset(size.width / 2f, size.height / 2f), style = Stroke(6f))
    }
}

/** Blue sky cruise: a deep blue sky with drifting clouds and the sun's flare over a glittering sea. */
private fun ScaledScope.blueSky(t: Float, c: ShelfColors) = draw {
    val size = this@blueSky.size
    val horizon = size.height * 0.64f
    // The sky in the theme's own blues (deeper at the top), the sea below it.
    drawRect(Brush.verticalGradient(listOf(c.background, c.surfaceRaised), 0f, horizon), Offset.Zero, Size(size.width, horizon))
    drawRect(Brush.verticalGradient(listOf(lerp(c.surfaceRaised, c.secondary, 0.25f), c.background), horizon, size.height),
        Offset(0f, horizon), Size(size.width, size.height - horizon))
    // The sun and its flare.
    val sun = Offset(size.width * 0.78f, size.height * 0.2f)
    glowDot(c.accent, sun, 260f, 0.35f)
    drawCircle(lerp(c.accent, Color.White, 0.6f).copy(alpha = 0.85f), 38f, sun)
    // Clouds drifting by (two layers).
    for (i in 0 until 7) {
        val far = i >= 4
        val speed = if (far) 10f else 22f
        val w = (if (far) 160f else 260f) * (0.8f + 0.4f * h(i, 21))
        val x = ((h(i, 22) * (size.width + w * 2) - t * speed) % (size.width + w * 2) + size.width + w * 2) % (size.width + w * 2) - w
        val yy = size.height * (if (far) 0.36f + 0.16f * h(i, 23) else 0.1f + 0.3f * h(i, 23))
        val a = if (far) 0.28f else 0.42f
        drawOval(Color.White.copy(alpha = a), Offset(x, yy), Size(w, w * 0.3f))
        drawOval(Color.White.copy(alpha = a), Offset(x + w * 0.2f, yy - w * 0.12f), Size(w * 0.5f, w * 0.3f))
    }
    // The sea glittering towards the sun.
    for (i in 0 until 40) {
        val yy = horizon + 8f + (size.height - horizon) * h(i, 24) * h(i, 24)
        val x = sun.x + (h(i, 25) - 0.5f) * (60f + (yy - horizon) * 1.6f)
        val tw = abs(sin(t * 3f + i * 2.3f))
        drawLine(Color.White.copy(alpha = 0.5f * tw), Offset(x - 10f, yy), Offset(x + 10f, yy), 2.5f)
    }
    drawLine(lerp(c.secondary, Color.White, 0.4f).copy(alpha = 0.5f), Offset(0f, horizon), Offset(size.width, horizon), 2f)
}

/** Pixel sky: chunky pixel clouds drifting in two layers, the sun's turning rays and pixel birds, on a coarse grid. */
private fun ScaledScope.pixelSky(t: Float, c: ShelfColors) = draw {
    val size = this@pixelSky.size
    val px = 12f
    fun snap(v: Float) = (v / px).toInt() * px
    // A dithered sky: a few bands of the theme's own colours, stepped like a 32-bit palette.
    val bands = 6
    for (b in 0 until bands) {
        drawRect(lerp(c.background, c.surfaceRaised, b / (bands - 1f)), Offset(0f, snap(size.height * b / bands)), Size(size.width, snap(size.height / bands) + px))
    }
    // The sun and its slowly turning pixel rays.
    val sun = Offset(snap(size.width * 0.18f), snap(size.height * 0.2f))
    for (r in 0 until 12) {
        val a = r / 12f * 2f * PI.toFloat() + t * 0.15f
        for (k in 5 until 9) drawRect(c.accent.copy(alpha = 0.45f), Offset(snap(sun.x + cos(a) * k * px * 1.6f), snap(sun.y + sin(a) * k * px * 1.6f)), Size(px, px))
    }
    for (yy in -4..4) for (xx in -4..4) if (xx * xx + yy * yy <= 17) drawRect(lerp(c.accent, Color.White, 0.35f), Offset(sun.x + xx * px, sun.y + yy * px), Size(px, px))
    // Pixel clouds: blocky rows of cells, two parallax layers.
    for (i in 0 until 8) {
        val far = i >= 5
        val speed = if (far) 8f else 20f
        val cw = (if (far) 9 else 14) + (h(i, 31) * 6).toInt()
        val span = size.width + cw * px * 2
        val x0 = snap(((h(i, 32) * span - t * speed) % span + span) % span - cw * px)
        val y0 = snap(size.height * (if (far) 0.42f + 0.15f * h(i, 33) else 0.12f + 0.3f * h(i, 33)))
        val col = Color.White.copy(alpha = if (far) 0.3f else 0.5f)
        for (row in 0 until 4) {
            val inset = when (row) { 0 -> cw / 3; 1 -> cw / 6; else -> 0 }
            val len = cw - inset * 2 - (if (row == 3) 2 else 0)
            drawRect(col, Offset(x0 + (inset + if (row == 3) 1 else 0) * px, y0 + row * px), Size(len * px, px))
        }
    }
    // Pixel birds flapping by.
    for (i in 0 until 4) {
        val span = size.width + 200f
        val x = snap(((h(i, 34) * span + t * (40f + 20f * h(i, 35))) % span) - 100f)
        val yy = snap(size.height * (0.25f + 0.3f * h(i, 36)))
        val up = ((t * 4f + i) % 2f) < 1f
        val col = c.background.copy(alpha = 0.85f)
        drawRect(col, Offset(x, yy), Size(px, px))
        drawRect(col, Offset(x - px, yy + if (up) -px else 0f), Size(px, px)); drawRect(col, Offset(x + px, yy + if (up) -px else 0f), Size(px, px))
    }
}
