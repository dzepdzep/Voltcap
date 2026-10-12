package com.shelf.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import com.shelf.launcher.ui.theme.AppTheme
import com.shelf.launcher.ui.theme.ThemeGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Still pictures of every theme's scene for the theme browser, so each tile shows what the theme
 * really looks like without running ninety live shaders at once.
 *
 * Each thumbnail is rendered once (Android 13+: the scene's shader at its still-frame moment, drawn by
 * the GPU into a small bitmap), then kept in memory and on disk (cache/theme_thumbs) - the file name
 * carries a hash of the scene's code and colours, so an updated scene gets a fresh picture. One at a
 * time, off the main thread.
 */
internal object ThemeThumbs {
    private const val W = 192
    private const val H = 120
    private val mem = android.util.LruCache<String, ImageBitmap>(64)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val one = Dispatchers.Default.limitedParallelism(1)

    private val keys = java.util.concurrent.ConcurrentHashMap<AppTheme, String>()

    // Worked out once per theme (the scene's code is long; tiles ask for this while composing).
    // (Your own themes change with the pack that is chosen: never remembered.)
    private fun key(theme: AppTheme): String = if (theme.motif.custom) makeKey(theme) else keys.getOrPut(theme) { makeKey(theme) }

    private fun makeKey(theme: AppTheme): String {
        val src = runCatching { SceneSource.source(theme.motif).hashCode() }.getOrDefault(0)
        val c = theme.colors
        val col = (c.background.toArgb() * 31 + c.accent.toArgb()) * 31 + c.secondary.toArgb()
        return "v1_${theme.name}_${Integer.toHexString(src)}_${Integer.toHexString(col)}"
    }

    fun cached(theme: AppTheme): ImageBitmap? = mem.get(key(theme))

    suspend fun get(context: Context, theme: AppTheme): ImageBitmap? {
        val k = key(theme)
        mem.get(k)?.let { return it }
        if (Build.VERSION.SDK_INT < 33) return null
        return withContext(one) {
            mem.get(k)?.let { return@withContext it }
            val dir = File(context.cacheDir, "theme_thumbs").apply { mkdirs() }
            val file = File(dir, "$k.webp")
            val bmp = runCatching { if (file.exists()) android.graphics.BitmapFactory.decodeFile(file.path) else null }.getOrNull()
                ?: runCatching { render(context, theme) }.getOrNull()?.also { b ->
                    runCatching {
                        // Old pictures of this theme go (a scene or its colours changed).
                        dir.listFiles()?.filter { it.name.startsWith("v1_${theme.name}_") && it.name != file.name }?.forEach { it.delete() }
                        file.outputStream().use { b.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, it) }
                    }
                }
            bmp?.asImageBitmap()?.also { mem.put(k, it) }
        }
    }

    @androidx.annotation.RequiresApi(33)
    private fun render(context: Context, theme: AppTheme): Bitmap? {
        val motif = theme.motif
        // Update 158: Lobby has no shader; its still picture is drawn in code.
        if (motif == com.shelf.launcher.ui.theme.ThemeMotif.LOBBY) return lobbyThumb(W, H, theme.colors)
        // Your own themes: the chosen pack, or nothing (no picture) when none is chosen / it doesn't run here.
        val source = SceneSource.source(motif)
        if (source.isEmpty()) return null
        val shader = RuntimeShader(source)
        // Only uniforms the scene really reads (the others abort the app on Android 13, see ThemeShaders.uniforms).
        val used = SceneSource.uniforms(motif)
        fun f(name: String, vararg v: Float) = if (name !in used) Unit else runCatching {
            when (v.size) {
                1 -> shader.setFloatUniform(name, v[0])
                2 -> shader.setFloatUniform(name, v[0], v[1])
                3 -> shader.setFloatUniform(name, v[0], v[1], v[2])
                else -> shader.setFloatUniform(name, v[0], v[1], v[2], v[3])
            }
        }
        val c = theme.colors
        f("uRes", W.toFloat(), H.toFloat()); f("uTime", STILL_FRAME_TIME); f("uLod", 1f); f("uDensity", 0f)
        f("uBg", c.background.red, c.background.green, c.background.blue, 1f)
        f("uSurf", c.surface.red, c.surface.green, c.surface.blue, 1f)
        f("uAcc", c.accent.red, c.accent.green, c.accent.blue, 1f)
        f("uSec", c.secondary.red, c.secondary.green, c.secondary.blue, 1f)
        if (motif.reactive) {
            f("uWaveY", 0.5f); f("uWaveH", 0.22f); f("uBass", 0.45f); f("uMid", 0.4f); f("uHigh", 0.35f)
            f("uBeatAge", 0.35f); f("uPlaying", 1f); f("uIntensity", 0.7f); f("uHasArt", 0f)
            f("uEqA", 0.6f, 0.7f, 0.55f, 0.45f); f("uEqB", 0.4f, 0.35f, 0.3f, 0.25f)
            f("uArtSize", 2f, 2f); f("uCoverSize", 2f, 2f); f("uLabelSize", 2f, 2f)
            f("uPlayT", STILL_FRAME_TIME); f("uBassT", STILL_FRAME_TIME * 0.45f); f("uMidT", STILL_FRAME_TIME * 0.4f); f("uHighT", STILL_FRAME_TIME * 0.35f)
        }
        // Only the pictures the scene really uses (see ThemeShaders.children: anything else aborts on Android 13).
        val kids = SceneSource.children(motif)
        if (kids.isNotEmpty()) {
            val px = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(c.accent.toArgb()) }
            // The song's label (Tape deck, Holo card) stays blank: no song in a thumbnail.
            val blank = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            kids.forEach { name -> shader.setInputShader(name, BitmapShader(if (name == "uLabel") blank else px, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)) }
            // Gaming room: its painting (the monitor shows the painting's own screen: no game in a thumbnail).
            // A painted scene: its painting.
            if ("uPaint" in kids) {
                val paint = ScenePainting.decode(context, motif) ?: return null
                shader.setInputShader("uPaint", paint.shader)
                f("uPaintSize", paint.w.toFloat(), paint.h.toFloat()); f("uHasPaint", 1f)
            }
            if ("uRoom" in kids) {
                val room = RoomPicture.decode(context) ?: return null
                shader.setInputShader("uRoom", room.shader)
                f("uRoomSize", room.w.toFloat(), room.h.toFloat()); f("uFocusY", 0.5f)
            }
        }
        val pic = Picture()
        val canvas = pic.beginRecording(W, H)
        canvas.drawPaint(Paint().apply { this.shader = shader })
        pic.endRecording()
        // Drawn by the GPU (a hardware bitmap); copied to a normal one so it can be saved.
        val hw = Bitmap.createBitmap(pic)
        return hw.copy(Bitmap.Config.ARGB_8888, false)
    }
}

/**
 * A small icon that says what each theme is: a record for Vinyl spin, a castle for Hero's quest,
 * lanterns for the festival… (shown in its tile and the preview, ringed with the theme's colours by
 * [ThemeBadge]).
 */
internal fun themeIcon(theme: AppTheme): ImageVector = when (theme) {
    // Voltcap's own
    AppTheme.VOLTCAP -> Icons.Filled.Bolt
    AppTheme.VOLTCAP_BEAT -> Icons.Filled.FlashOn
    AppTheme.VOLTCAP_PIXELS -> Icons.Filled.GridOn
    AppTheme.RETRO_BOUNCE -> Icons.Filled.SportsEsports
    AppTheme.LYRIC_CLOUD -> Icons.Filled.Lyrics
    AppTheme.LYRIC_KARAOKE -> Icons.Filled.Mic
    AppTheme.LYRIC_SHEET -> Icons.Filled.ViewHeadline
    AppTheme.LYRIC_NEON -> Icons.Filled.WbIncandescent
    // Classics
    AppTheme.VOLT -> Icons.Filled.Bolt
    AppTheme.TIDE -> Icons.Filled.Waves
    AppTheme.EMBER -> Icons.Filled.LocalFireDepartment
    AppTheme.COBALT, AppTheme.ORBIT -> Icons.Filled.Public
    AppTheme.SYNTHWAVE, AppTheme.RETRO_WAVE -> Icons.Filled.Grid4x4
    AppTheme.MATCHA, AppTheme.SPROUT_TYPE -> Icons.Filled.Eco
    AppTheme.MONO -> Icons.Filled.Contrast
    AppTheme.MINIMAL_CONSOLE -> Icons.Filled.Gamepad
    AppTheme.LIQUID, AppTheme.TORRENT_TYPE -> Icons.Filled.WaterDrop
    // Music with the album cover
    AppTheme.NOW_PLAYING -> Icons.Filled.MusicNote
    AppTheme.TAPE_DECK -> Icons.Filled.Radio
    AppTheme.KALEIDO -> Icons.Filled.FilterVintage
    AppTheme.SHATTER -> Icons.Filled.BrokenImage
    AppTheme.VINYL, AppTheme.CD_SPIN -> Icons.Filled.Album
    AppTheme.LAVA -> Icons.Filled.BubbleChart
    AppTheme.COVER_WALL -> Icons.Filled.GridView
    AppTheme.COVER_TUNNEL -> Icons.Filled.Layers
    AppTheme.POP_ART -> Icons.Filled.Brush
    AppTheme.COVER_RIPPLE -> Icons.Filled.Water
    // Visualizers
    AppTheme.SPECTRUM -> Icons.Filled.Equalizer
    AppTheme.PULSE_RING -> Icons.Filled.RadioButtonChecked
    AppTheme.BEAT_TUNNEL -> Icons.Filled.Speed
    AppTheme.REACTOR -> Icons.Filled.FlashOn
    AppTheme.THUNDER -> Icons.Filled.Thunderstorm
    AppTheme.BLOOM, AppTheme.SAKURA -> Icons.Filled.LocalFlorist
    AppTheme.CHLADNI -> Icons.Filled.Grain
    AppTheme.LASER_STAGE -> Icons.Filled.Flare
    AppTheme.TERRAIN, AppTheme.TERRA_TYPE -> Icons.Filled.Terrain
    AppTheme.FIREWORKS -> Icons.Filled.Celebration
    AppTheme.NEON_PUDDLE -> Icons.Filled.Opacity
    AppTheme.RIBBONS -> Icons.Filled.Gesture
    AppTheme.DISCO -> Icons.Filled.Nightlife
    AppTheme.SPEAKER -> Icons.Filled.Speaker
    AppTheme.BLAZE_BEAT, AppTheme.POWER_AURA -> Icons.Filled.Whatshot
    // Game showcase
    AppTheme.SHOWCASE_PARALLAX -> Icons.Filled.Layers
    AppTheme.SHOWCASE_CINEMA -> Icons.Filled.Movie
    AppTheme.SHOWCASE_FILMSTRIP -> Icons.Filled.VideoLibrary
    AppTheme.SHOWCASE_MOSAIC -> Icons.Filled.Collections
    AppTheme.GLASS -> Icons.Filled.CropSquare
    // Eras & retro
    AppTheme.ERA_8BIT -> Icons.Filled.VideogameAsset
    AppTheme.MODE7 -> Icons.Filled.DirectionsCar
    AppTheme.PS1_POLY, AppTheme.VECTOR -> Icons.Filled.ChangeHistory
    AppTheme.N64_PLAINS, AppTheme.PIXEL_VALLEY -> Icons.Filled.Landscape
    AppTheme.PS2_HAZE -> Icons.Filled.BlurOn
    AppTheme.BLUE_SKY -> Icons.Filled.WbSunny
    AppTheme.XMB_WAVE -> Icons.Filled.Waves
    AppTheme.PLAZA -> Icons.Filled.PanTool
    AppTheme.PIXEL_FLOW -> Icons.Filled.Apps
    AppTheme.ARCADE_CRT -> Icons.Filled.Tv
    AppTheme.POCKET_LCD, AppTheme.POCKET_SHELL -> Icons.Filled.SportsEsports
    // Anime
    AppTheme.SHINOBI -> Icons.Filled.Air
    AppTheme.SUMMER_SKY, AppTheme.NEBULA -> Icons.Filled.Cloud
    AppTheme.MECHA_HUD -> Icons.Filled.GpsFixed
    AppTheme.MOON_BLADE, AppTheme.AURORA -> Icons.Filled.NightsStay
    AppTheme.MAGICAL -> Icons.Filled.AutoAwesome
    AppTheme.SPIRIT_FOREST -> Icons.Filled.Forest
    AppTheme.FESTIVAL -> Icons.Filled.Festival
    AppTheme.AURA_CLASH -> Icons.Filled.FlashOn
    // Arena
    AppTheme.ARENA -> Icons.Filled.SportsMma
    AppTheme.SPECIMEN_LAB -> Icons.Filled.Science
    // Nature, space & tech
    AppTheme.SHALLOWS -> Icons.Filled.BeachAccess
    AppTheme.DEEP_SEA -> Icons.Filled.Water
    AppTheme.CUSTOM_THEME -> Icons.Filled.Tune
    AppTheme.CUSTOM_VISUALIZER -> Icons.Filled.GraphicEq
    AppTheme.CAMPFIRE, AppTheme.BLAZE_TYPE -> Icons.Filled.LocalFireDepartment
    AppTheme.INK_WASH -> Icons.Filled.Brush
    AppTheme.PAPER_LAYERS -> Icons.Filled.Layers
    AppTheme.CONSTELLATION -> Icons.Filled.Stars
    AppTheme.WARP -> Icons.Filled.RocketLaunch
    AppTheme.PRISM_ATRIUM -> Icons.Filled.ViewInAr
    AppTheme.ORBIT_STATION -> Icons.Filled.Public
    AppTheme.CIRCUIT -> Icons.Filled.Memory
    AppTheme.GEARWORKS -> Icons.Filled.Settings
    AppTheme.DIGITAL_RAIN -> Icons.Filled.Code
    AppTheme.HOLO_FOIL -> Icons.Filled.Diamond
    AppTheme.AURA_BEAT -> Icons.Filled.FlashOn
    AppTheme.MOUNTAINS -> Icons.Filled.Landscape
    AppTheme.SPECIMEN_BEAT -> Icons.Filled.Biotech
    AppTheme.TOWERS_128 -> Icons.Filled.ViewInAr
    AppTheme.CARTRIDGE_16 -> Icons.Filled.VideogameAsset
    AppTheme.HANDHELD -> Icons.Filled.Gamepad
    AppTheme.PIXEL_PARADE -> Icons.Filled.Celebration
    AppTheme.CAMPFIRE_BEAT -> Icons.Filled.Fireplace
    AppTheme.MANGA_BEAT -> Icons.Filled.AutoStories
    AppTheme.COMIC_PANELS -> Icons.Filled.MenuBook
    AppTheme.PIXEL_SKY -> Icons.Filled.Cloud
    AppTheme.CRT_ROOM -> Icons.Filled.Tv
    AppTheme.COVER_FLOW -> Icons.Filled.ViewCarousel
    AppTheme.RECORD_SHELF -> Icons.Filled.LibraryMusic
    AppTheme.COVER_CUBE -> Icons.Filled.ViewInAr
    AppTheme.COVER_FLOAT -> Icons.Filled.Photo
    AppTheme.HOLO_COVER -> Icons.Filled.AutoAwesome
    AppTheme.COVER_HALO -> Icons.Filled.TrackChanges
    AppTheme.BIG_TYPE -> Icons.Filled.TextFields
    AppTheme.PIXEL_COVER -> Icons.Filled.GridOn
    AppTheme.POLAROID -> Icons.Filled.PhotoCamera
    AppTheme.RETRO_TV -> Icons.Filled.LiveTv
    AppTheme.JUKEBOX -> Icons.Filled.QueueMusic
    AppTheme.NEON_FRAME -> Icons.Filled.Lightbulb
    AppTheme.MAGAZINE -> Icons.Filled.Newspaper
    AppTheme.COVER_FOCUS -> Icons.Filled.CenterFocusStrong
    // Creature types
    AppTheme.SPARK_TYPE -> Icons.Filled.Bolt
    AppTheme.MIND_TYPE -> Icons.Filled.Psychology
    AppTheme.PHANTOM_TYPE -> Icons.Filled.DarkMode
    AppTheme.FROST_TYPE -> Icons.Filled.AcUnit
    AppTheme.VENOM_TYPE -> Icons.Filled.Science
    AppTheme.CRT_TUNNEL -> Icons.Filled.Tv
    AppTheme.MEMORY_CARD -> Icons.Filled.SdCard
    AppTheme.ENERGY_CORE -> Icons.Filled.Bolt
    AppTheme.RACING_32 -> Icons.Filled.DirectionsCar
    AppTheme.FIRE_32 -> Icons.Filled.Whatshot
    AppTheme.MEADOW_TYPE -> Icons.Filled.Grass
    AppTheme.DOJO_TYPE -> Icons.Filled.SportsMma
    AppTheme.GALE_TYPE -> Icons.Filled.Air
    AppTheme.THICKET_TYPE -> Icons.Filled.BugReport
    AppTheme.BEDROCK_TYPE -> Icons.Filled.Landscape
    AppTheme.WYRM_TYPE -> Icons.Filled.Gesture
    AppTheme.UMBRA_TYPE -> Icons.Filled.NightsStay
    AppTheme.ALLOY_TYPE -> Icons.Filled.Hardware
    AppTheme.GLIMMER_TYPE -> Icons.Filled.AutoAwesome
    AppTheme.DISC_SLEEVE -> Icons.Filled.Album
    AppTheme.STILL_DUSK -> Icons.Filled.WbTwilight
    AppTheme.STILL_HORIZON -> Icons.Filled.Landscape
    AppTheme.STILL_GLOW -> Icons.Filled.Lens
    AppTheme.CALM_RIBBON -> Icons.Filled.Waves
    AppTheme.CALM_DRIFT -> Icons.Filled.BubbleChart
    AppTheme.CALM_HALO -> Icons.Filled.BlurCircular
    AppTheme.BOLD_SPLIT -> Icons.Filled.Dashboard
    AppTheme.BOLD_DOTS -> Icons.Filled.Apps
    AppTheme.BOLD_BANDS -> Icons.Filled.ViewStream
    AppTheme.LOBBY -> Icons.Filled.ViewHeadline // Update 158
    // Update 56: the brief's leftovers and the 32-bit creature types.
    AppTheme.HANDHELD_BOOT -> Icons.Filled.PhoneAndroid
    AppTheme.POCKET_BEAT -> Icons.Filled.SportsEsports
    AppTheme.CONSOLE_DISC -> Icons.Filled.Album
    AppTheme.ARCADE_ATTRACT -> Icons.Filled.VideogameAsset
    AppTheme.NEON_CARTRIDGE -> Icons.Filled.Lightbulb
    AppTheme.DATA_STREAM -> Icons.Filled.Storage
    AppTheme.PORTAL_GALLERY -> Icons.Filled.DonutLarge
    AppTheme.POSTER_WALL -> Icons.Filled.Wallpaper
    AppTheme.GAMING_ROOM -> Icons.Filled.Weekend
    AppTheme.TROPHY_VAULT -> Icons.Filled.EmojiEvents
    AppTheme.BADGE_BOUNCE -> Icons.Filled.WorkspacePremium
    AppTheme.CHILL_BLADES -> Icons.Filled.Gesture
    AppTheme.CHILL_POLAR -> Icons.Filled.AcUnit
    AppTheme.CHILL_PLANET -> Icons.Filled.Public
    AppTheme.CHILL_MOON -> Icons.Filled.NightsStay
    AppTheme.CHILL_ORBS -> Icons.Filled.BubbleChart
    AppTheme.CHILL_SILK -> Icons.Filled.Waves
    AppTheme.CHILL_DUNES -> Icons.Filled.Landscape
    AppTheme.CHILL_MESH -> Icons.Filled.BlurOn
    AppTheme.CHILL_LIGHT -> Icons.Filled.WbSunny
    AppTheme.CHILL_SHADE -> Icons.Filled.Spa
    AppTheme.CHILL_RAIN -> Icons.Filled.Umbrella
    AppTheme.CHILL_CITY -> Icons.Filled.LocationCity
    AppTheme.CHILL_JELLY -> Icons.Filled.WaterDrop
    AppTheme.CHILL_GLADE -> Icons.Filled.Forest
    AppTheme.CHILL_HEX -> Icons.Filled.Hexagon
    AppTheme.CHILL_CLOUDS -> Icons.Filled.Cloud
    AppTheme.CHILL_KOI -> Icons.Filled.SetMeal
    AppTheme.CHILL_PEAKS -> Icons.Filled.Terrain
    AppTheme.PIXEL_WORLD -> Icons.Filled.Map
    AppTheme.SPARK_PX -> Icons.Filled.Bolt
    AppTheme.TORRENT_PX -> Icons.Filled.WaterDrop
    AppTheme.SPROUT_PX -> Icons.Filled.Eco
    AppTheme.MIND_PX -> Icons.Filled.Psychology
    AppTheme.BLAZE_PX -> Icons.Filled.LocalFireDepartment
    AppTheme.PHANTOM_PX -> Icons.Filled.DarkMode
    AppTheme.FROST_PX -> Icons.Filled.AcUnit
    AppTheme.TERRA_PX -> Icons.Filled.Terrain
    AppTheme.VENOM_PX -> Icons.Filled.Science
    AppTheme.MEADOW_PX -> Icons.Filled.Grass
    AppTheme.DOJO_PX -> Icons.Filled.SportsMma
    AppTheme.GALE_PX -> Icons.Filled.Air
    AppTheme.THICKET_PX -> Icons.Filled.BugReport
    AppTheme.BEDROCK_PX -> Icons.Filled.Landscape
    AppTheme.WYRM_PX -> Icons.Filled.Gesture
    AppTheme.UMBRA_PX -> Icons.Filled.NightsStay
    AppTheme.ALLOY_PX -> Icons.Filled.Hardware
    AppTheme.GLIMMER_PX -> Icons.Filled.AutoAwesome
    // Update 109: the video loops, by pack.
    else -> when (theme.group) {
        else -> Icons.Filled.Palette
    }
}

/**
 * A theme's badge: its icon in the accent on the theme's own background, inside a ring made of the
 * theme's colours (accent, second colour, surface) - so each badge says what the theme is and which
 * colours it brings, at a glance.
 */
@androidx.compose.runtime.Composable
internal fun ThemeBadge(theme: AppTheme, size: androidx.compose.ui.unit.Dp, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val c = theme.colors
    androidx.compose.foundation.layout.Box(
        modifier
            .size(size)
            .drawBehind {
                val ring = size.toPx() * 0.13f
                // The colour ring: accent → second colour → surface → accent all the way round.
                drawCircle(
                    androidx.compose.ui.graphics.Brush.sweepGradient(listOf(c.accent, c.secondary, c.surfaceRaised, c.accent)),
                    radius = this.size.minDimension / 2f
                )
                drawCircle(c.background, radius = this.size.minDimension / 2f - ring)
            },
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Icon(themeIcon(theme), null, tint = c.accent, modifier = androidx.compose.ui.Modifier.size(size * 0.52f))
    }
}
