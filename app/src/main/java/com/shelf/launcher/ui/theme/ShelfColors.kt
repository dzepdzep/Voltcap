package com.shelf.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** The palette every screen reads from. Swapping themes swaps this object,
 * so nothing in the UI hardcodes a colour. */
data class ShelfColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val accent: Color,
    val accentGlow: Color,
    val secondary: Color,
    val textPrimary: Color,
    val textMuted: Color
)

/** The animated scene behind the home screen for a theme (Appearance › Behind the dock › Animated theme). */
enum class ThemeMotif(
    /** Moves with the music that is playing (NowPlayingScene), in the colours of its cover. */
    val reactive: Boolean = false,
    /** Reactive scenes: room the visualizer needs above / below its line (share of the screen height). */
    val reactiveHalf: Float = 0f,
    /** Game showcase: the focused game's hero art with its screenshots moving round it (ShowcaseScene), not a shader. */
    val showcase: Boolean = false,
    /** A still picture: drawn once, never animated (costs nothing while you rest on it). */
    val still: Boolean = false,
    /** Slow, quiet motion: drawn at a low frame rate. */
    val calm: Boolean = false,
    /** Music, lyrics: the theme shows the song's words itself (no lyric line in the player or on its own). */
    val lyrics: Boolean = false,
    /** A custom theme / visualizer pack the user added (CustomPacks): drawn from the pack's own shader. */
    val custom: Boolean = false
) {
    SPARKS, LIQUID, FLAMES, ORBIT, RETRO_GRID, LEAVES, MINIMAL,
    BUBBLES, PSY_WAVES, WISPS, LCD, LEAF_SWIRL, AURA,
    AURORA, NEBULA, PIXELS, GLASS, RAIN, STARS, PETALS, SCANLINES,
    /** Reacts to the music that is playing (NowPlayingScene), in the colours of its cover. */
    MUSIC(true, 0.26f),
    /** Anime-style fire: flat cel bands, halftone, diamond sparks, speed lines (Blaze). */
    ANIME_FLAMES,
    /** Anime summer sky: cel-shaded cumulus, lens flare, contrail, power lines. */
    CEL_SKY,
    /** Mecha cockpit HUD: hex grid, target rings, radar sweep, warning chevrons. */
    MECHA_HUD,
    /** Night field under a huge moon, petals, and a sword slash flashing across now and then. */
    MOON_BLADE,
    /** Music-reactive: LED bar spectrum over the blurred cover. */
    SPECTRUM(true, 0.11f),
    /** Music-reactive: the cover as a spinning record inside a ring of spectrum bars. */
    PULSE_RING(true, 0.26f),
    /** Music-reactive: a neon tunnel that rushes forward with the beat. */
    BEAT_TUNNEL(true, 0.12f),
    /** Circuit board: traces with racing pulses, chips with blinking LEDs. */
    CIRCUIT,
    /** Interlocking gears, pistons, steam and sparks. */
    GEARWORKS,
    /** Ice type: snowflakes, creeping frost, aurora. */
    FROST,
    /** Ground type: dunes, sandstorm, glowing cracks. */
    TERRA,
    /** Poison type: glowing swamp, bubbles, mist, drips. */
    VENOM,
    /** A tunnel of stacked TV sets, each on its own channel. */
    CRT_TUNNEL,
    /** A floating memory card and a grid of save blocks with pixel icons. */
    MEMORY_CARD,
    /** A plasma core in turning containment rings, arcs and surges. */
    ENERGY_CORE,
    /** A low-res arcade racer at sunset: polygon mountains, kerbs, a rival car. */
    RACING_32,
    /** Music-reactive: pixel demo fire played by the equalizer, the cover in a frame. */
    FIRE_32(true, 0.3f),
    /** Normal type: a windy meadow, a windmill, dandelion seeds. */
    MEADOW,
    /** Fighting type: a training hall at sunset, a strike every few seconds. */
    DOJO,
    /** Flying type: above a sea of clouds, wind streaks, a flock, feathers. */
    GALE,
    /** Bug type: undergrowth at dusk, backlit leaves, a dewy web, fireflies. */
    THICKET,
    /** Rock type: a layered sandstone canyon, an arch, falling pebbles. */
    BEDROCK,
    /** Dragon type: a scaled sky serpent weaving through a storm. */
    WYRM,
    /** Dark type: an eclipse over the rooftops, shadow smoke, eyes in the alleys. */
    UMBRA,
    /** Steel type: brushed steel panels, chrome spheres, a sweeping light. */
    ALLOY,
    /** Fairy type: a twilight glade, a glowing mushroom ring, swirling lights. */
    GLIMMER,
    /** Music-reactive: a plasma core with orbiting rings and arcs on the beat. */
    REACTOR(true, 0.24f),
    /** Music-reactive: storm, lightning on the beat, the equalizer as an electric arc. */
    THUNDER(true, 0.11f),
    /** Music-reactive: a flower of light whose petals follow the bands. */
    BLOOM(true, 0.24f),
    /** Music-reactive: a cassette with turning reels between two swinging VU meters. */
    TAPE_DECK(true, 0.2f),
    /** Music-reactive: sand gathering into vibration patterns that change with the music. */
    CHLADNI(true, 0.26f),
    /** Music-reactive: laser fans sweeping through haze, a strobe on the drops. */
    LASER_STAGE(true, 0.18f),
    /** Music-reactive: the cover turned into a slowly turning kaleidoscope. */
    KALEIDO(true, 0.26f),
    /** Music-reactive: the cover split into tiles that pop out with the beat. */
    SHATTER(true, 0.24f),
    /** Music-reactive: wireframe mountains built from the spectrum, rushing towards you. */
    TERRAIN(true, 0.16f),
    /** Music-reactive: fireworks bursting on the beat over a dark skyline. */
    FIREWORKS(true, 0.22f),
    /** Music-reactive: a rainy street, a neon sign and its reflection in a puddle. */
    NEON_PUDDLE(true, 0.2f),
    /** Clear shallow water: caustic light nets on the sand, fish shadows, sun rays. */
    CAUSTICS,
    /** Ink blooming in water on rice paper, brushed mountains in the mist. */
    INK,
    /** A landscape cut from layers of paper, drifting at their own depths. */
    PAPERCUT,
    /** A pixel-art valley with parallax layers, clouds and a waterfall. */
    PARALLAX16,
    /** Holographic foil: rainbow bands and glitter that move as you tilt the handheld. */
    HOLO,
    /** A campfire at night: flames, embers, pines and wheeling stars. */
    CAMPFIRE,
    /** Music-reactive: the cover as the label of a spinning record on a turntable, in the cover's colours. */
    VINYL(true, 0.38f),
    /** Music-reactive: the cover printed on a fast-spinning disc throwing rainbows and light spikes. */
    CD_SPIN(true, 0.28f),
    /** Music-reactive: glossy lava-lamp blobs of the cover's colours swelling with the bands. */
    LAVA(true, 0.17f),
    /** Music-reactive: layered ribbons of light weaving with the spectrum over a bright wash. */
    RIBBONS(true, 0.15f),
    /** Music-reactive: a light-up dance floor, a mirror ball and sweeping spotlights. */
    DISCO(true, 0.16f),
    /** Music-reactive: two loudspeakers pumping with the bass, tweeters and LED meters. */
    SPEAKER(true, 0.24f),
    /** 8-bit era: a chunky pixel world scrolling by, blocky clouds, twinkling coins. */
    ERA_8BIT,
    /** 16-bit era: a racetrack rushing under a mode-7 style floor, parallax mountains. */
    MODE7,
    /** 32-bit era: wobbly low-poly shapes, dithered gradients, affine texture warp and fog. */
    PS1_POLY,
    /** 128-bit era: pillars of light rising out of a deep blue haze, drifting particles. */
    PS2_HAZE,
    /** 64-bit era: smooth shaded hills in bright primary colours, foggy distance, floating shapes. */
    N64_PLAINS,
    /** Console menu waves: flowing bands of light across a gradient that follows the time of day. */
    XMB_WAVE,
    /** Motion-control era: a clean white plaza with soft bubbles and gently rotating rings. */
    PLAZA,
    /** Early arcade: glowing vector-line rocks, a wire tube and a starfield. */
    VECTOR,
    /** Late-90s arcade: a sunny coast road, blue sky, sea glitter. */
    BLUE_SKY,
    /** Stars stretching into a hyperspace tunnel. */
    WARP,
    /** Glass archways receding through a reflective atrium: perspective, occlusion, a slow camera drift. */
    PRISM_ATRIUM,
    /** A lit orbital habitat: tilted rings round a dark planet, shuttles riding the rings. */
    ORBIT_STATION,
    /** Arena entrance (music): the cover on a giant screen over the stage, spotlights, pyro and a crowd on the beat. */
    ARENA(true, 0.3f),
    /** Music-reactive: a wall of the album cover, tiles lit by the spectrum, the cover big in front. */
    ART_WALL(true, 0.26f),
    /** Music-reactive: the album cover inside itself, a picture-in-picture tunnel falling with the beat. */
    ART_TUNNEL(true, 0.2f),
    /** Music-reactive: the cover four times as bold two-colour pop-art prints with halftone dots. */
    POP_ART(true, 0.3f),
    /** Music-reactive: the cover under shallow water, rippling with every kick. */
    COVER_RIPPLE(true, 0.3f),
    /** Music-reactive: Blaze's anime fire following the spectrum and surging on the kick. */
    BLAZE_BEAT(true, 0.18f),
    SPECIMEN_LAB,
    /** Anime: pastel sky, shimmer ribbons, star glints, hearts and a transformation ring. */
    MAGICAL,
    /** Anime: a misty forest glade with sun shafts, spirit orbs and swaying grass. */
    SPIRIT_FOREST,
    /** Anime: summer-night festival lanterns, stall roofs and fireworks. */
    FESTIVAL,
    /** Anime: two power auras clashing at a lightning seam, shockwaves and lifting rocks. */
    AURA_CLASH,
    /** Voltcap: the start-up animation as a background - sparks round the capsule, the bolt snapping in. */
    VOLT_INTRO,
    /** Music-reactive: the Voltcap capsule as a visualizer (bars out of its edges, the bolt flashing on kicks). */
    VOLT_BEAT(true, 0.24f),
    /** Voltcap 32-bit: little pixel capsules and bolts hopping about. */
    VOLT_PIXELS,
    /** Pixel art: cliffs with waterfalls, an animated 8-frame picture played back crisply (FramesScene). */
    PIXEL_FALLS,
    /** 32-bit: D-pads, A / B buttons and console icons bouncing round a pixel stage (RetroBounceLayer on top of the shader). */
    RETRO_BOUNCE,
    /** Music: the song's lyrics scattered over the soft cover, words and lines fading in and out in its colours (LyricScatterLayer). */
    LYRIC_CLOUD(true, 0.1f, lyrics = true),
    /** Music, lyrics: a karaoke video - the line being sung fills with colour word by word, a bouncing ball, the next line below (LyricThemes.kt). */
    LYRIC_KARAOKE(true, 0.1f, lyrics = true),
    /** Music, lyrics: the whole lyric sheet scrolling gently, the line being sung bright in the middle (LyricThemes.kt). */
    LYRIC_SHEET(true, 0.1f, lyrics = true),
    /** Music, lyrics: the words lighting up as neon tubes on a brick wall as they're sung (LyricThemes.kt). */
    LYRIC_NEON(true, 0.1f, lyrics = true),
    /** Music: two anime auras clashing round the cover, pushed by the lows and the highs. */
    AURA_BEAT(true, 0.26f),
    /** Music: a mountain range rising out of the plain, every peak one band of the equalizer. */
    MOUNTAINS(true, 0.28f),
    /** Music: the pixel lab with the cover floating in the tank and monitors showing the song. */
    SPECIMEN_BEAT(true, 0.3f),
    /** Music: a boot-screen void of glowing cube towers, each one a band, the cover floating above. */
    TOWERS_128(true, 0.26f),
    /** Music: a pixel cartridge with the cover on its label, plugged into a console on the beat. */
    CARTRIDGE_16(true, 0.3f),
    /** Music: a chunky handheld console with the cover on its screen, buttons pressing on the beat. */
    HANDHELD(true, 0.3f),
    /** Music: 32-bit Voltcap capsules and bolts dancing on a lit floor under a pixel cover banner. */
    PIXEL_PARADE(true, 0.3f),
    /** Music: a campfire whose flames are the equalizer, sparks on the kick, the cover as the moon. */
    CAMPFIRE_BEAT(true, 0.3f),
    /** Music: a black-and-white manga page with the cover inked in the middle panel, impacts on the kick. */
    MANGA_BEAT(true, 0.28f),
    /** Comic pages: panels sliding in and flipping between halftone skies, speed lines and impact bursts. */
    COMIC_PANELS,
    /** Pixel sky: 32-bit clouds, balloons, birds and a capsule airship on a dithered sky. */
    PIXEL_SKY,
    /** Music: the cover-flow carousel - this song's cover in front, earlier songs angled away on a glossy floor. */
    COVER_FLOW(true, 0.28f),
    /** Music: a record-store crate of earlier songs' sleeves, the current one pulled out with its record spinning. */
    RECORD_SHELF(true, 0.28f),
    /** Music: a turning cube with this song's cover in front and earlier covers on its other faces. */
    COVER_CUBE(true, 0.28f),
    /** Music: the cover floating and tilting in 3D with a mirrored reflection and a gliding light. */
    COVER_FLOAT(true, 0.28f),
    /** Music: the cover as a holographic foil card - rainbow sheen, foil stars, sparkles on the highs. */
    HOLO_COVER(true, 0.28f),
    /** Music: the cover as a disc ringed by a waveform, a circular spectrum and kick-flashing dots. */
    COVER_HALO(true, 0.3f),
    /** Music: the song's title in giant type sliding past behind the cover, poster style. */
    BIG_TYPE(true, 0.28f),
    /** Music: the cover as chunky pixel art, pixels falling away on the kick, a pixel equalizer. */
    PIXEL_COVER(true, 0.28f),
    /** Music: polaroids on a cork board - this song's with its title handwritten, earlier ones pinned around. */
    POLAROID(true, 0.28f),
    /** Music: the cover on an old CRT TV - scanlines, colour fringes, rolling on the kick, a volume bar. */
    RETRO_TV(true, 0.3f),
    /** Music: a glowing retro jukebox with the cover in its window, bubble tubes and chasing lights. */
    JUKEBOX(true, 0.32f),
    /** Music: the cover on a brick wall framed by neon tubes that burn with the bass, a wet floor below. */
    NEON_FRAME(true, 0.28f),
    /** Music: the cover laid out as a music magazine front page with the title in big type. */
    MAGAZINE(true, 0.32f),
    /** Music: the cover fills the screen; the camera pans and zooms over it towards its subject, soft depth of field, kicks punch the zoom. */
    COVER_FOCUS(true, 0.28f),
    /** Game showcase: the hero art drifting in depth with screenshot cards floating round it. */
    SHOWCASE_PARALLAX(showcase = true),
    /** Game showcase: screenshots taking turns full screen (slow zoom and pan) behind the framed hero art. */
    SHOWCASE_CINEMA(showcase = true),
    /** Game showcase: a tilted strip of screenshots sliding past over the hero art. */
    SHOWCASE_FILMSTRIP(showcase = true),
    /** Game showcase: a wall of screenshots drifting in perspective, the hero art fading through it. */
    SHOWCASE_MOSAIC(showcase = true),
    /** Glass: frosted glass panes drifting over the game's key art, each holding one of its screenshots. */
    SHOWCASE_GLASS(showcase = true),
    /** CRT room: a cosy den at night, the game's hero art and screenshots on an old CRT TV, channel-hopping. */
    SHOWCASE_CRT(showcase = true),
    /** Square album cover with its disc slid halfway out, spinning slowly. */
    DISC_SLEEVE(true, 0.3f),
    /** Paper album card with an audio-driven wave edge and soft cover backdrop. */
    ALBUM_TIDE(true, 0.32f),
    STILL_DUSK(still = true),
    STILL_HORIZON(still = true),
    STILL_GLOW(still = true),
    CALM_RIBBON(calm = true),
    CALM_DRIFT(calm = true),
    CALM_HALO(calm = true),
    BOLD_SPLIT(still = true),
    BOLD_DOTS(still = true),
    BOLD_BANDS(still = true),
    /** Update 158: a calm light menu backdrop - near-white to light grey, faint thin lines drifting very slowly (LobbyScene, drawn in code). */
    LOBBY(calm = true),
    /** A handheld booting up: the logo drops, the shine sweeps, a menu fades in, then it starts again. */
    HANDHELD_BOOT,
    /** Music: a wide handheld (D-pad left, A / B right) with the cover on its screen, pads pressing on the beat. */
    POCKET_BEAT(true, 0.3f),
    /** A top-loading console with its lid up and a disc spinning under the laser. */
    CONSOLE_DISC,
    /** An arcade cabinet in attract mode: glowing marquee, a demo game playing itself, a blinking coin prompt. */
    ARCADE_ATTRACT,
    /** A cartridge drawn in neon tubes on a brick wall, sliding into a neon console that lights up. */
    NEON_CARTRIDGE,
    /** Lanes of data packets racing through a dark grid in perspective, bursting at the nodes. */
    DATA_STREAM,
    /** A gallery of glowing portal frames, each opening onto a different world, gliding past. */
    PORTAL_GALLERY,
    /** Game-style posters pinned at different depths on a lit brick wall, the camera drifting past. */
    POSTER_WALL,
    /** A gaming room at night that slowly turns to day: desk, glowing screen, shelves, LED strip. */
    GAMING_ROOM(showcase = true),
    /** A trophy vault: the round door swung open on rows of glowing badges on pedestals. */
    TROPHY_VAULT,
    /** Badges drawn star by star in the night sky, each lighting up as it's completed. */
    BADGE_STARS,
    /** A 32-bit overworld map scrolling by: fields, rivers, towns, a little capsule walking the road. */
    PIXEL_WORLD,
    /** Your earned badges (RetroAchievements / Steam) bouncing round the screen over a velvet backdrop (BadgeBounceLayer on top of the shader). */
    BADGE_BOUNCE,
    /** Chill home screens (update 70): quiet backgrounds with their detail in the corners, the middle left calm for the games. */
    CHILL_BLADES, CHILL_POLAR, CHILL_PLANET, CHILL_MOON, CHILL_ORBS, CHILL_SILK, CHILL_DUNES, CHILL_MESH, CHILL_LIGHT, CHILL_SHADE, CHILL_RAIN, CHILL_CITY, CHILL_JELLY, CHILL_GLADE, CHILL_HEX, CHILL_CLOUDS, CHILL_KOI, CHILL_PEAKS,
    /** 32-bit creature types: a pixel scene for each element (pylons in a storm, a sunken bay, a garden...). */
    PX_SPARK, PX_TORRENT, PX_SPROUT, PX_MIND, PX_BLAZE, PX_PHANTOM, PX_FROST, PX_TERRA, PX_VENOM,
    PX_MEADOW, PX_DOJO, PX_GALE, PX_THICKET, PX_BEDROCK, PX_WYRM, PX_UMBRA, PX_ALLOY, PX_GLIMMER,
    /** The chosen custom theme pack (Settings › Theme › Your own themes & visualizers). */
    CUSTOM_SCENE(custom = true),
    /** The chosen custom visualizer pack: music-reactive like Now playing. */
    CUSTOM_VIZ(reactive = true, reactiveHalf = 0.26f, custom = true)
}

/** Which group a theme is listed under in the theme picker. */
enum class ThemeGroup(val label: String) {
    ESSENTIALS("Voltcap classics"),
    CALM("Still, calm & bold"),
    CHILL("Chill home screens"),
    ALBUM_ART("Music · with the album cover"),
    VISUALIZERS("Music · visualizers"),
    LYRICS("Music · lyrics"),
    ERA_MUSIC("Music · console eras"),
    SHOWCASE("Game showcase · your game's art"),
    ERAS("Gaming eras & retro"),
    ANIME("Anime"),
    NATURE("Nature & cozy"),
    SPACE("Space & tech"),
    TYPES("Creature types"),
    PIXEL_TYPES("Creature types · 32-bit"),
    TROPHIES("Achievements & trophies"),
    CUSTOM("Your own")
}

/** Theme palette in short: glow is the accent at ~45 %. */
private fun pal(bg: Long, surface: Long, raised: Long, accent: Long, secondary: Long, text: Long, muted: Long) = ShelfColors(
    background = Color(bg), surface = Color(surface), surfaceRaised = Color(raised),
    accent = Color(accent), accentGlow = Color(accent).copy(alpha = 0.45f), secondary = Color(secondary),
    textPrimary = Color(text), textMuted = Color(muted)
)

/**
 * The background-music loops Voltcap ships (res/raw/bgm_<name>.ogg, made with tools/audio). The name
 * is what Settings stores as the picked track ("music_track"): never rename one. The first seven used
 * to be theme names, so old saved picks still match.
 */
enum class MusicLoop(
    val label: String,
    /** The navigation-sound set that goes with it ("Sounds match the music"), or null = your pick. */
    val sounds: com.shelf.launcher.data.SoundStyle? = null
) {
    VOLT("Volt · bright synth arps"),
    TIDE("Tide · calm, watery"),
    EMBER("Ember · warm and glowing"),
    COBALT("Cobalt · cool, spacey"),
    SYNTHWAVE("Synthwave · retro night drive"),
    MATCHA("Matcha · soft and cosy"),
    MONO("Mono · minimal"),
    HOME("Home · calm console menu", com.shelf.launcher.data.SoundStyle.CHILL),
    HAZE("Haze · 128-bit dream", com.shelf.launcher.data.SoundStyle.CHILL),
    MEMORY("Memory card · 32-bit", com.shelf.launcher.data.SoundStyle.RETRO32),
    HOLLOW("Hollow · dark fantasy", com.shelf.launcher.data.SoundStyle.DARK);

    companion object {
        /** A saved track name: a loop, or (very old) any theme's name, which means that theme's loop. */
        fun named(name: String): MusicLoop? =
            if (name.isEmpty()) null
            else values().firstOrNull { it.name == name } ?: AppTheme.values().firstOrNull { it.name == name }?.musicLoop
    }
}

/**
 * Every theme. All names and looks are Voltcap's own: the "inspired" groups borrow a mood and a
 * colour scheme (creature-type colours, a classic pocket LCD, ninja / martial-arts anime palettes),
 * never a name, logo or character.
 */
enum class AppTheme(
    val label: String, val motif: ThemeMotif = ThemeMotif.MINIMAL, val group: ThemeGroup = ThemeGroup.ESSENTIALS
) {
    // Voltcap's own: the intro as a scene, a music version, and 32-bit sprites.
    VOLTCAP("Voltcap", ThemeMotif.VOLT_INTRO, ThemeGroup.ESSENTIALS),
    VOLTCAP_BEAT("Voltcap beat", ThemeMotif.VOLT_BEAT, ThemeGroup.ESSENTIALS),
    VOLTCAP_PIXELS("Voltcap 32-bit", ThemeMotif.VOLT_PIXELS, ThemeGroup.ESSENTIALS),
    WATERFALL("Waterfall cliffs", ThemeMotif.PIXEL_FALLS, ThemeGroup.NATURE),
    RETRO_BOUNCE("Retro bounce", ThemeMotif.RETRO_BOUNCE, ThemeGroup.ERAS),
    LYRIC_CLOUD("Lyric cloud", ThemeMotif.LYRIC_CLOUD, ThemeGroup.LYRICS),
    LYRIC_KARAOKE("Karaoke", ThemeMotif.LYRIC_KARAOKE, ThemeGroup.LYRICS),
    LYRIC_SHEET("Lyric sheet", ThemeMotif.LYRIC_SHEET, ThemeGroup.LYRICS),
    LYRIC_NEON("Neon lyrics", ThemeMotif.LYRIC_NEON, ThemeGroup.LYRICS),
    VOLT("Volt", ThemeMotif.SPARKS, ThemeGroup.ESSENTIALS),
    TIDE("Tide", ThemeMotif.LIQUID, ThemeGroup.ESSENTIALS),
    EMBER("Ember", ThemeMotif.FLAMES, ThemeGroup.ESSENTIALS),
    COBALT("Cobalt", ThemeMotif.ORBIT, ThemeGroup.ESSENTIALS),
    SYNTHWAVE("Synthwave", ThemeMotif.RETRO_GRID, ThemeGroup.ESSENTIALS),
    MATCHA("Matcha", ThemeMotif.LEAVES, ThemeGroup.ESSENTIALS),
    MONO("Mono (OLED)", ThemeMotif.MINIMAL, ThemeGroup.ESSENTIALS),

    // Takes its colours from the cover of whatever is playing (MusicTheme); these are the fallback.
    NOW_PLAYING("Now playing", ThemeMotif.MUSIC, ThemeGroup.ALBUM_ART),
    SPECTRUM("Spectrum", ThemeMotif.SPECTRUM, ThemeGroup.VISUALIZERS),
    PULSE_RING("Pulse ring", ThemeMotif.PULSE_RING, ThemeGroup.VISUALIZERS),
    BEAT_TUNNEL("Beat tunnel", ThemeMotif.BEAT_TUNNEL, ThemeGroup.VISUALIZERS),
    REACTOR("Reactor core", ThemeMotif.REACTOR, ThemeGroup.VISUALIZERS),
    THUNDER("Thunderstorm", ThemeMotif.THUNDER, ThemeGroup.VISUALIZERS),
    BLOOM("Bloom", ThemeMotif.BLOOM, ThemeGroup.VISUALIZERS),
    TAPE_DECK("Tape deck", ThemeMotif.TAPE_DECK, ThemeGroup.ALBUM_ART),
    CHLADNI("Sand patterns", ThemeMotif.CHLADNI, ThemeGroup.VISUALIZERS),
    LASER_STAGE("Laser stage", ThemeMotif.LASER_STAGE, ThemeGroup.VISUALIZERS),
    KALEIDO("Kaleido cover", ThemeMotif.KALEIDO, ThemeGroup.ALBUM_ART),
    SHATTER("Cover shatter", ThemeMotif.SHATTER, ThemeGroup.ALBUM_ART),
    TERRAIN("Wire terrain", ThemeMotif.TERRAIN, ThemeGroup.VISUALIZERS),
    FIREWORKS("Fireworks", ThemeMotif.FIREWORKS, ThemeGroup.VISUALIZERS),
    NEON_PUDDLE("Neon puddle", ThemeMotif.NEON_PUDDLE, ThemeGroup.VISUALIZERS),
    VINYL("Vinyl spin", ThemeMotif.VINYL, ThemeGroup.ALBUM_ART),
    CD_SPIN("Disc spin", ThemeMotif.CD_SPIN, ThemeGroup.ALBUM_ART),
    LAVA("Album lava", ThemeMotif.LAVA, ThemeGroup.ALBUM_ART),
    RIBBONS("Sound ribbons", ThemeMotif.RIBBONS, ThemeGroup.VISUALIZERS),
    DISCO("Disco floor", ThemeMotif.DISCO, ThemeGroup.VISUALIZERS),
    SPEAKER("Bass cones", ThemeMotif.SPEAKER, ThemeGroup.VISUALIZERS),

    // Creature-type colours: one theme per element.
    SPARK_TYPE("Spark", ThemeMotif.SPARKS, ThemeGroup.TYPES),
    TORRENT_TYPE("Torrent", ThemeMotif.BUBBLES, ThemeGroup.TYPES),
    SPROUT_TYPE("Sprout", ThemeMotif.LEAVES, ThemeGroup.TYPES),
    MIND_TYPE("Mind", ThemeMotif.PSY_WAVES, ThemeGroup.TYPES),
    BLAZE_TYPE("Blaze", ThemeMotif.ANIME_FLAMES, ThemeGroup.TYPES),
    PHANTOM_TYPE("Phantom", ThemeMotif.WISPS, ThemeGroup.TYPES),
    FROST_TYPE("Frost", ThemeMotif.FROST, ThemeGroup.TYPES),
    TERRA_TYPE("Terra", ThemeMotif.TERRA, ThemeGroup.TYPES),
    VENOM_TYPE("Venom", ThemeMotif.VENOM, ThemeGroup.TYPES),
    MEADOW_TYPE("Meadow", ThemeMotif.MEADOW, ThemeGroup.TYPES),
    DOJO_TYPE("Dojo", ThemeMotif.DOJO, ThemeGroup.TYPES),
    GALE_TYPE("Gale", ThemeMotif.GALE, ThemeGroup.TYPES),
    THICKET_TYPE("Thicket", ThemeMotif.THICKET, ThemeGroup.TYPES),
    BEDROCK_TYPE("Bedrock", ThemeMotif.BEDROCK, ThemeGroup.TYPES),
    WYRM_TYPE("Wyrm", ThemeMotif.WYRM, ThemeGroup.TYPES),
    UMBRA_TYPE("Umbra", ThemeMotif.UMBRA, ThemeGroup.TYPES),
    ALLOY_TYPE("Alloy", ThemeMotif.ALLOY, ThemeGroup.TYPES),
    GLIMMER_TYPE("Glimmer", ThemeMotif.GLIMMER, ThemeGroup.TYPES),

    // Handheld looks.
    POCKET_LCD("Pocket LCD", ThemeMotif.LCD, ThemeGroup.ERAS),
    POCKET_SHELL("Pocket shell", ThemeMotif.LCD, ThemeGroup.ERAS),

    // Anime moods.
    SHINOBI("Shinobi", ThemeMotif.LEAF_SWIRL, ThemeGroup.ANIME),
    POWER_AURA("Power aura", ThemeMotif.AURA, ThemeGroup.ANIME),
    SUMMER_SKY("Summer sky", ThemeMotif.CEL_SKY, ThemeGroup.ANIME),
    MECHA_HUD("Mecha HUD", ThemeMotif.MECHA_HUD, ThemeGroup.ANIME),
    MOON_BLADE("Moon blade", ThemeMotif.MOON_BLADE, ThemeGroup.ANIME),

    // Tech & mech.
    CIRCUIT("Circuit board", ThemeMotif.CIRCUIT, ThemeGroup.SPACE),
    GEARWORKS("Gearworks", ThemeMotif.GEARWORKS, ThemeGroup.SPACE),
    WARP("Warp drive", ThemeMotif.WARP, ThemeGroup.SPACE),
    PRISM_ATRIUM("Prism atrium", ThemeMotif.PRISM_ATRIUM, ThemeGroup.SPACE),
    ORBIT_STATION("Orbit station", ThemeMotif.ORBIT_STATION, ThemeGroup.SPACE),

    // Scenes.
    AURORA("Aurora", ThemeMotif.AURORA, ThemeGroup.NATURE),
    NEBULA("Nebula", ThemeMotif.NEBULA, ThemeGroup.SPACE),
    PIXEL_FLOW("Pixel flow", ThemeMotif.PIXELS, ThemeGroup.ERAS),
    // Frosted glass over the focused game's own art (the shader glass shows while it loads).
    GLASS("Glass", ThemeMotif.SHOWCASE_GLASS, ThemeGroup.SHOWCASE),
    RETRO_WAVE("Retro wave", ThemeMotif.RETRO_GRID, ThemeGroup.ERAS),
    ORBIT("Orbit", ThemeMotif.ORBIT, ThemeGroup.SPACE),
    DIGITAL_RAIN("Digital rain", ThemeMotif.RAIN, ThemeGroup.SPACE),
    CONSTELLATION("Constellation", ThemeMotif.STARS, ThemeGroup.SPACE),
    LIQUID("Liquid", ThemeMotif.LIQUID, ThemeGroup.ESSENTIALS),
    MINIMAL_CONSOLE("Minimal console", ThemeMotif.MINIMAL, ThemeGroup.ESSENTIALS),
    SHALLOWS("Sunlit shallows", ThemeMotif.CAUSTICS, ThemeGroup.NATURE),

    // Crafted & cozy.
    INK_WASH("Ink wash", ThemeMotif.INK, ThemeGroup.NATURE),
    PAPER_LAYERS("Paper layers", ThemeMotif.PAPERCUT, ThemeGroup.NATURE),
    PIXEL_VALLEY("16-bit valley", ThemeMotif.PARALLAX16, ThemeGroup.ERAS),
    HOLO_FOIL("Holo foil", ThemeMotif.HOLO, ThemeGroup.SPACE),
    CAMPFIRE("Campfire", ThemeMotif.CAMPFIRE, ThemeGroup.NATURE),

    // Gaming eras: the look of a console generation (moods and techniques only, no names or logos).
    ERA_8BIT("8-bit quest", ThemeMotif.ERA_8BIT, ThemeGroup.ERAS),
    MODE7("16-bit mode 7", ThemeMotif.MODE7, ThemeGroup.ERAS),
    PS1_POLY("32-bit polygons", ThemeMotif.PS1_POLY, ThemeGroup.ERAS),
    N64_PLAINS("64-bit plains", ThemeMotif.N64_PLAINS, ThemeGroup.ERAS),
    PS2_HAZE("128-bit haze", ThemeMotif.PS2_HAZE, ThemeGroup.ERAS),
    BLUE_SKY("Blue sky cruise", ThemeMotif.BLUE_SKY, ThemeGroup.ERAS),
    XMB_WAVE("Wave menu", ThemeMotif.XMB_WAVE, ThemeGroup.ERAS),
    PLAZA("Motion plaza", ThemeMotif.PLAZA, ThemeGroup.ERAS),
    VECTOR("Vector arcade", ThemeMotif.VECTOR, ThemeGroup.ERAS),
    CRT_TUNNEL("CRT tunnel", ThemeMotif.CRT_TUNNEL, ThemeGroup.ERAS),
    MEMORY_CARD("Memory card", ThemeMotif.MEMORY_CARD, ThemeGroup.ERAS),
    ENERGY_CORE("Energy core", ThemeMotif.ENERGY_CORE, ThemeGroup.SPACE),
    RACING_32("32-bit racing", ThemeMotif.RACING_32, ThemeGroup.ERAS),
    FIRE_32("32-bit fire", ThemeMotif.FIRE_32, ThemeGroup.ERA_MUSIC),

    // Game showcase: the focused game's own hero art and screenshots, animated (colours from its art).
    SHOWCASE_PARALLAX("Hero parallax", ThemeMotif.SHOWCASE_PARALLAX, ThemeGroup.SHOWCASE),
    SHOWCASE_CINEMA("Cinematic", ThemeMotif.SHOWCASE_CINEMA, ThemeGroup.SHOWCASE),
    SHOWCASE_FILMSTRIP("Filmstrip", ThemeMotif.SHOWCASE_FILMSTRIP, ThemeGroup.SHOWCASE),
    SHOWCASE_MOSAIC("Screenshot wall", ThemeMotif.SHOWCASE_MOSAIC, ThemeGroup.SHOWCASE),

    // Arena entrance: an original wrestling-show mood (no names, logos, characters or real people).
    // The four Arena & adventure themes (Hero's quest, Coral reef, Jungle canopy, Sewer & skyline) were removed in update 20.
    ARENA("Arena entrance", ThemeMotif.ARENA, ThemeGroup.ALBUM_ART),
    SPECIMEN_LAB("Specimen lab", ThemeMotif.SPECIMEN_LAB, ThemeGroup.ERAS),
    MAGICAL("Magical sparkle", ThemeMotif.MAGICAL, ThemeGroup.ANIME),
    SPIRIT_FOREST("Spirit forest", ThemeMotif.SPIRIT_FOREST, ThemeGroup.ANIME),
    FESTIVAL("Summer festival", ThemeMotif.FESTIVAL, ThemeGroup.ANIME),
    AURA_CLASH("Aura clash", ThemeMotif.AURA_CLASH, ThemeGroup.ANIME),
    COVER_WALL("Cover wall", ThemeMotif.ART_WALL, ThemeGroup.ALBUM_ART),
    COVER_TUNNEL("Cover tunnel", ThemeMotif.ART_TUNNEL, ThemeGroup.ALBUM_ART),
    POP_ART("Pop art cover", ThemeMotif.POP_ART, ThemeGroup.ALBUM_ART),
    COVER_RIPPLE("Cover ripple", ThemeMotif.COVER_RIPPLE, ThemeGroup.ALBUM_ART),
    BLAZE_BEAT("Blaze beat", ThemeMotif.BLAZE_BEAT, ThemeGroup.VISUALIZERS),

    AURA_BEAT("Aura clash · music", ThemeMotif.AURA_BEAT, ThemeGroup.ALBUM_ART),
    MOUNTAINS("Mountains forming", ThemeMotif.MOUNTAINS, ThemeGroup.VISUALIZERS),
    SPECIMEN_BEAT("Specimen lab · music", ThemeMotif.SPECIMEN_BEAT, ThemeGroup.ALBUM_ART),
    TOWERS_128("128-bit towers", ThemeMotif.TOWERS_128, ThemeGroup.ERA_MUSIC),
    CARTRIDGE_16("16-bit cartridge", ThemeMotif.CARTRIDGE_16, ThemeGroup.ERA_MUSIC),
    HANDHELD("Handheld", ThemeMotif.HANDHELD, ThemeGroup.ERA_MUSIC),
    PIXEL_PARADE("Pixel parade", ThemeMotif.PIXEL_PARADE, ThemeGroup.ESSENTIALS),
    CAMPFIRE_BEAT("Campfire beat", ThemeMotif.CAMPFIRE_BEAT, ThemeGroup.VISUALIZERS),
    MANGA_BEAT("Manga beat", ThemeMotif.MANGA_BEAT, ThemeGroup.ANIME),
    COMIC_PANELS("Comic pages", ThemeMotif.COMIC_PANELS, ThemeGroup.ANIME),
    PIXEL_SKY("Pixel sky", ThemeMotif.PIXEL_SKY, ThemeGroup.ESSENTIALS),
    CRT_ROOM("CRT room", ThemeMotif.SHOWCASE_CRT, ThemeGroup.SHOWCASE),
    COVER_FLOW("Cover flow", ThemeMotif.COVER_FLOW, ThemeGroup.ALBUM_ART),
    RECORD_SHELF("Record shelf", ThemeMotif.RECORD_SHELF, ThemeGroup.ALBUM_ART),
    COVER_CUBE("Cover cube", ThemeMotif.COVER_CUBE, ThemeGroup.ALBUM_ART),
    COVER_FLOAT("Floating cover", ThemeMotif.COVER_FLOAT, ThemeGroup.ALBUM_ART),
    HOLO_COVER("Holo card", ThemeMotif.HOLO_COVER, ThemeGroup.ALBUM_ART),
    COVER_HALO("Cover halo", ThemeMotif.COVER_HALO, ThemeGroup.ALBUM_ART),
    BIG_TYPE("Big type", ThemeMotif.BIG_TYPE, ThemeGroup.ALBUM_ART),
    PIXEL_COVER("Pixel cover", ThemeMotif.PIXEL_COVER, ThemeGroup.ALBUM_ART),
    POLAROID("Polaroids", ThemeMotif.POLAROID, ThemeGroup.ALBUM_ART),
    RETRO_TV("Retro TV", ThemeMotif.RETRO_TV, ThemeGroup.ALBUM_ART),
    JUKEBOX("Jukebox", ThemeMotif.JUKEBOX, ThemeGroup.ALBUM_ART),
    NEON_FRAME("Neon frame", ThemeMotif.NEON_FRAME, ThemeGroup.ALBUM_ART),
    MAGAZINE("Magazine", ThemeMotif.MAGAZINE, ThemeGroup.ALBUM_ART),
    COVER_FOCUS("Pan & zoom", ThemeMotif.COVER_FOCUS, ThemeGroup.ALBUM_ART),
    DISC_SLEEVE("Disc & sleeve", ThemeMotif.DISC_SLEEVE, ThemeGroup.ALBUM_ART),
    ALBUM_TIDE("Album tide", ThemeMotif.ALBUM_TIDE, ThemeGroup.ALBUM_ART),
    // Still & calm: a quiet picture (drawn once) or a slow ambient scene.
    STILL_DUSK("Still dusk", ThemeMotif.STILL_DUSK, ThemeGroup.CALM),
    STILL_HORIZON("Still horizon", ThemeMotif.STILL_HORIZON, ThemeGroup.CALM),
    STILL_GLOW("Still glow", ThemeMotif.STILL_GLOW, ThemeGroup.CALM),
    CALM_RIBBON("Light ribbon", ThemeMotif.CALM_RIBBON, ThemeGroup.CALM),
    CALM_DRIFT("Night drift", ThemeMotif.CALM_DRIFT, ThemeGroup.CALM),
    CALM_HALO("Soft halo", ThemeMotif.CALM_HALO, ThemeGroup.CALM),
    // Bold & flat: crisp console-style home screens in solid colours (static).
    BOLD_SPLIT("Bold split", ThemeMotif.BOLD_SPLIT, ThemeGroup.CALM),
    BOLD_DOTS("Bold dots", ThemeMotif.BOLD_DOTS, ThemeGroup.CALM),
    BOLD_BANDS("Bold bands", ThemeMotif.BOLD_BANDS, ThemeGroup.CALM),
    // Update 158: Lobby, the first light theme (near-white backdrop, light-grey panels, dark grey writing).
    LOBBY("Lobby", ThemeMotif.LOBBY, ThemeGroup.CALM),
    // Picks.
    SAKURA("Sakura", ThemeMotif.PETALS, ThemeGroup.NATURE),
    // The game you're on in the CRT room's showcase (red / cyan arcade lighting).
    ARCADE_CRT("Arcade CRT", ThemeMotif.SHOWCASE_CRT, ThemeGroup.ERAS),
    DEEP_SEA("Deep sea", ThemeMotif.BUBBLES, ThemeGroup.NATURE),
    // Leftovers from the brief (update 56).
    HANDHELD_BOOT("Handheld boot", ThemeMotif.HANDHELD_BOOT, ThemeGroup.ERAS),
    POCKET_BEAT("Pocket beat", ThemeMotif.POCKET_BEAT, ThemeGroup.ERA_MUSIC),
    CONSOLE_DISC("Console disc", ThemeMotif.CONSOLE_DISC, ThemeGroup.ERAS),
    ARCADE_ATTRACT("Arcade attract", ThemeMotif.ARCADE_ATTRACT, ThemeGroup.ERAS),
    NEON_CARTRIDGE("Neon cartridge", ThemeMotif.NEON_CARTRIDGE, ThemeGroup.ERAS),
    DATA_STREAM("Data stream", ThemeMotif.DATA_STREAM, ThemeGroup.SPACE),
    PORTAL_GALLERY("Portal gallery", ThemeMotif.PORTAL_GALLERY, ThemeGroup.SPACE),
    POSTER_WALL("Poster wall", ThemeMotif.POSTER_WALL, ThemeGroup.ERAS),
    GAMING_ROOM("Gaming room", ThemeMotif.GAMING_ROOM, ThemeGroup.SHOWCASE),
    TROPHY_VAULT("Trophy vault", ThemeMotif.TROPHY_VAULT, ThemeGroup.TROPHIES),
    BADGE_BOUNCE("Badge bounce", ThemeMotif.BADGE_BOUNCE, ThemeGroup.TROPHIES),
    // Chill home screens: quiet backgrounds, detail in the corners.
    CHILL_BLADES("Glass blades", ThemeMotif.CHILL_BLADES, ThemeGroup.CHILL),
    CHILL_POLAR("Polar veil", ThemeMotif.CHILL_POLAR, ThemeGroup.CHILL),
    CHILL_PLANET("Planet rise", ThemeMotif.CHILL_PLANET, ThemeGroup.CHILL),
    CHILL_MOON("Moon tide", ThemeMotif.CHILL_MOON, ThemeGroup.CHILL),
    CHILL_ORBS("Glass orbs", ThemeMotif.CHILL_ORBS, ThemeGroup.CHILL),
    CHILL_SILK("Silk folds", ThemeMotif.CHILL_SILK, ThemeGroup.CHILL),
    CHILL_DUNES("Dune hour", ThemeMotif.CHILL_DUNES, ThemeGroup.CHILL),
    CHILL_MESH("Mesh glow", ThemeMotif.CHILL_MESH, ThemeGroup.CHILL),
    CHILL_LIGHT("Morning light", ThemeMotif.CHILL_LIGHT, ThemeGroup.CHILL),
    CHILL_SHADE("Leaf shade", ThemeMotif.CHILL_SHADE, ThemeGroup.CHILL),
    CHILL_RAIN("Rainy window", ThemeMotif.CHILL_RAIN, ThemeGroup.CHILL),
    CHILL_CITY("Rooftop dusk", ThemeMotif.CHILL_CITY, ThemeGroup.CHILL),
    CHILL_JELLY("Jellyfish drift", ThemeMotif.CHILL_JELLY, ThemeGroup.CHILL),
    CHILL_GLADE("Firefly glade", ThemeMotif.CHILL_GLADE, ThemeGroup.CHILL),
    CHILL_HEX("Hex lattice", ThemeMotif.CHILL_HEX, ThemeGroup.CHILL),
    CHILL_CLOUDS("Above the clouds", ThemeMotif.CHILL_CLOUDS, ThemeGroup.CHILL),
    CHILL_KOI("Koi pond", ThemeMotif.CHILL_KOI, ThemeGroup.CHILL),
    CHILL_PEAKS("Winter peaks", ThemeMotif.CHILL_PEAKS, ThemeGroup.CHILL),
    PIXEL_WORLD("Pixel world", ThemeMotif.PIXEL_WORLD, ThemeGroup.ERAS),
    // 32-bit creature types: their own pixel scenes (not the full scenes pixelated).
    SPARK_PX("Spark · 32-bit", ThemeMotif.PX_SPARK, ThemeGroup.PIXEL_TYPES),
    TORRENT_PX("Torrent · 32-bit", ThemeMotif.PX_TORRENT, ThemeGroup.PIXEL_TYPES),
    SPROUT_PX("Sprout · 32-bit", ThemeMotif.PX_SPROUT, ThemeGroup.PIXEL_TYPES),
    MIND_PX("Mind · 32-bit", ThemeMotif.PX_MIND, ThemeGroup.PIXEL_TYPES),
    BLAZE_PX("Blaze · 32-bit", ThemeMotif.PX_BLAZE, ThemeGroup.PIXEL_TYPES),
    PHANTOM_PX("Phantom · 32-bit", ThemeMotif.PX_PHANTOM, ThemeGroup.PIXEL_TYPES),
    FROST_PX("Frost · 32-bit", ThemeMotif.PX_FROST, ThemeGroup.PIXEL_TYPES),
    TERRA_PX("Terra · 32-bit", ThemeMotif.PX_TERRA, ThemeGroup.PIXEL_TYPES),
    VENOM_PX("Venom · 32-bit", ThemeMotif.PX_VENOM, ThemeGroup.PIXEL_TYPES),
    MEADOW_PX("Meadow · 32-bit", ThemeMotif.PX_MEADOW, ThemeGroup.PIXEL_TYPES),
    DOJO_PX("Dojo · 32-bit", ThemeMotif.PX_DOJO, ThemeGroup.PIXEL_TYPES),
    GALE_PX("Gale · 32-bit", ThemeMotif.PX_GALE, ThemeGroup.PIXEL_TYPES),
    THICKET_PX("Thicket · 32-bit", ThemeMotif.PX_THICKET, ThemeGroup.PIXEL_TYPES),
    BEDROCK_PX("Bedrock · 32-bit", ThemeMotif.PX_BEDROCK, ThemeGroup.PIXEL_TYPES),
    WYRM_PX("Wyrm · 32-bit", ThemeMotif.PX_WYRM, ThemeGroup.PIXEL_TYPES),
    UMBRA_PX("Umbra · 32-bit", ThemeMotif.PX_UMBRA, ThemeGroup.PIXEL_TYPES),
    ALLOY_PX("Alloy · 32-bit", ThemeMotif.PX_ALLOY, ThemeGroup.PIXEL_TYPES),
    GLIMMER_PX("Glimmer · 32-bit", ThemeMotif.PX_GLIMMER, ThemeGroup.PIXEL_TYPES),
    // Your own packs (Settings › Theme › Your own themes & visualizers); they show the chosen pack, or fall back.
    CUSTOM_THEME("Your theme", ThemeMotif.CUSTOM_SCENE, ThemeGroup.CUSTOM),
    CUSTOM_VISUALIZER("Your visualizer", ThemeMotif.CUSTOM_VIZ, ThemeGroup.CUSTOM);

    /**
     * The background-music loop this theme plays. The four newer loops go to the themes they were made
     * for (console-menu looks, 128-bit dreams, 32-bit pixels, dark fantasy); everything else keeps the
     * loop it had ([musicBase]).
     */
    val musicLoop: MusicLoop
        get() = when (this) {
            XMB_WAVE, MINIMAL_CONSOLE, GLASS, COVER_FLOW, COVER_FLOAT, CALM_RIBBON, CALM_DRIFT, CALM_HALO,
            STILL_DUSK, STILL_HORIZON, STILL_GLOW, BOLD_SPLIT, BOLD_DOTS, BOLD_BANDS -> MusicLoop.HOME
            LOBBY -> MusicLoop.HOME // Update 158
            PS2_HAZE, TOWERS_128, MIND_TYPE, NEBULA, AURORA -> MusicLoop.HAZE
            CHILL_POLAR, CHILL_PLANET, CHILL_JELLY -> MusicLoop.HAZE
            CHILL_BLADES, CHILL_MOON, CHILL_ORBS, CHILL_SILK, CHILL_DUNES, CHILL_MESH, CHILL_LIGHT, CHILL_SHADE, CHILL_RAIN, CHILL_CITY, CHILL_GLADE, CHILL_HEX, CHILL_CLOUDS, CHILL_KOI, CHILL_PEAKS -> MusicLoop.HOME
            VOLTCAP_PIXELS, PS1_POLY, PIXEL_PARADE, MEMORY_CARD, RACING_32, FIRE_32, CRT_TUNNEL, PIXEL_SKY, PIXEL_COVER, PIXEL_FLOW, RETRO_BOUNCE,
            SPECIMEN_LAB, SPECIMEN_BEAT, CRT_ROOM, RETRO_TV, HANDHELD -> MusicLoop.MEMORY
            HANDHELD_BOOT, POCKET_BEAT, CONSOLE_DISC, PIXEL_WORLD, SPARK_PX, TORRENT_PX, SPROUT_PX, MIND_PX, BLAZE_PX,
            FROST_PX, TERRA_PX, VENOM_PX, MEADOW_PX, DOJO_PX, GALE_PX, THICKET_PX, BEDROCK_PX, WYRM_PX, ALLOY_PX, GLIMMER_PX -> MusicLoop.MEMORY
            TROPHY_VAULT, BADGE_BOUNCE, PORTAL_GALLERY, DATA_STREAM -> MusicLoop.HAZE
            PHANTOM_PX, UMBRA_PX -> MusicLoop.HOLLOW
            PHANTOM_TYPE, VENOM_TYPE, UMBRA_TYPE, MOON_BLADE, SPIRIT_FOREST -> MusicLoop.HOLLOW
            else -> when (musicBase) {
                TIDE -> MusicLoop.TIDE
                EMBER -> MusicLoop.EMBER
                COBALT -> MusicLoop.COBALT
                SYNTHWAVE -> MusicLoop.SYNTHWAVE
                MATCHA -> MusicLoop.MATCHA
                MONO -> MusicLoop.MONO
                else -> MusicLoop.VOLT
            }
        }

    /**
     * Static (a still picture), calm (slow, quiet motion) or animated: the theme browser's filter and
     * favourite-theme rotation use it. Static / calm themes also cost almost nothing while idle.
     */
    val kind: com.shelf.launcher.data.Features.ThemeKind
        get() = when (this) {
            MONO, MINIMAL_CONSOLE, STILL_DUSK, STILL_HORIZON, STILL_GLOW, BOLD_SPLIT, BOLD_DOTS, BOLD_BANDS ->
                com.shelf.launcher.data.Features.ThemeKind.STATIC
            CALM_RIBBON, CALM_DRIFT, CALM_HALO -> com.shelf.launcher.data.Features.ThemeKind.CALM
            LOBBY -> com.shelf.launcher.data.Features.ThemeKind.CALM // Update 158
            CHILL_BLADES, CHILL_POLAR, CHILL_PLANET, CHILL_MOON, CHILL_ORBS, CHILL_SILK, CHILL_DUNES, CHILL_MESH, CHILL_LIGHT, CHILL_SHADE, CHILL_RAIN, CHILL_CITY, CHILL_JELLY, CHILL_GLADE, CHILL_HEX, CHILL_CLOUDS, CHILL_KOI, CHILL_PEAKS -> com.shelf.launcher.data.Features.ThemeKind.CALM
            XMB_WAVE, PS2_HAZE, AURORA, NEBULA, INK_WASH, PAPER_LAYERS, SHALLOWS, MATCHA, TIDE, LIQUID,
            GLASS, SHOWCASE_PARALLAX, SHOWCASE_CINEMA, DEEP_SEA, SAKURA, CONSTELLATION, PIXEL_VALLEY,
            BLUE_SKY, SUMMER_SKY, SPIRIT_FOREST, MEADOW_TYPE, GALE_TYPE, POSTER_WALL,
            MEADOW_PX, SPROUT_PX -> com.shelf.launcher.data.Features.ThemeKind.CALM
            else -> com.shelf.launcher.data.Features.ThemeKind.ANIMATED
        }

    /**
     * True when the theme's scene draws itself in the colours it's given, so Theme look › Custom /
     * Current game colours visibly apply. Music themes take theirs from the album cover and the game
     * showcases from the game's art: those keep their original look.
     */
    val recolorable: Boolean
        get() = !(motif.reactive || motif.showcase || motif == ThemeMotif.PIXEL_FALLS || motif.name in PAINTED_MOTIFS ||
            // Update 158: Lobby is light with dark writing; a game's or a custom dark palette would leave its dark text unreadable.
            motif == ThemeMotif.LOBBY)

    /**
     * Recolourable, but the scene paints mostly in its own fixed colours (sky, grass, stone...), so Custom /
     * Current game colours only tint its accents. Measured, not guessed: tools/themes/color_check.py renders
     * each scene with its colours and with very different ones; these change less than 4 of 255 per channel.
     */
    val mostlyOwnColors: Boolean
        get() = recolorable && motif.name in OWN_COLOR_MOTIFS

    /** Keeps its colours whatever Theme look says (album cover, game art, a picture) or mostly does. */
    val ownColors: Boolean
        get() = !recolorable || mostlyOwnColors

    companion object {
        /** The user's paintings with motion on top (ScenePainting): their own colours, always. */
        private val PAINTED_MOTIFS = setOf(
            "AURORA", "MEADOW", "PX_DOJO",
            "CHILL_PEAKS", "CHILL_SILK", "CHILL_POLAR", "CHILL_MOON", "CHILL_DUNES", "CHILL_GLADE", "CHILL_CLOUDS", "CHILL_KOI", "CHILL_CITY"
        )

        /** From tools/themes/color_check.py (update 56). Re-run it after changing a scene's colours. */
        private val OWN_COLOR_MOTIFS = setOf(
            "BEDROCK", "BLUE_SKY", "DOJO", "MEADOW", "MODE7", "N64_PLAINS", "PIXEL_SKY", "PIXEL_WORLD", "PLAZA",
            "PX_BEDROCK", "PX_MEADOW", "PX_SPROUT", "VECTOR"
        )
    }

    /** The first seven loops' mapping (one of VOLT, TIDE, EMBER, COBALT, SYNTHWAVE, MATCHA, MONO). */
    val musicBase: AppTheme
        get() = when (this) {
            SPARK_TYPE, POWER_AURA, PIXEL_FLOW, ARCADE_CRT, MECHA_HUD, GEARWORKS, CRT_TUNNEL, RACING_32 -> VOLT
            TORRENT_TYPE, LIQUID, DEEP_SEA, GLASS, SUMMER_SKY, FROST_TYPE, GALE_TYPE -> TIDE
            BLAZE_TYPE, SHINOBI, TERRA_TYPE, DOJO_TYPE, BEDROCK_TYPE, FIRE_32 -> EMBER
            ORBIT, CONSTELLATION, AURORA, MOON_BLADE, CIRCUIT, ORBIT_STATION, ALLOY_TYPE, MEMORY_CARD, ENERGY_CORE -> COBALT
            MIND_TYPE, NEBULA, RETRO_WAVE, WARP, VENOM_TYPE, PRISM_ATRIUM, WYRM_TYPE, GLIMMER_TYPE -> SYNTHWAVE
            SPROUT_TYPE, POCKET_LCD, POCKET_SHELL, SAKURA, MEADOW_TYPE, THICKET_TYPE -> MATCHA
            PHANTOM_TYPE, DIGITAL_RAIN, MINIMAL_CONSOLE, UMBRA_TYPE -> MONO
            NOW_PLAYING, SPECTRUM, PULSE_RING, BEAT_TUNNEL, REACTOR, THUNDER, BLOOM,
            TAPE_DECK, CHLADNI, LASER_STAGE, KALEIDO, SHATTER, TERRAIN, FIREWORKS, NEON_PUDDLE,
            VINYL, CD_SPIN, LAVA, RIBBONS, DISCO, SPEAKER -> SYNTHWAVE
            ERA_8BIT, MODE7, VECTOR -> VOLT
            PS1_POLY, PS2_HAZE, XMB_WAVE -> COBALT
            N64_PLAINS, BLUE_SKY -> TIDE
            PLAZA -> MATCHA
            SHOWCASE_PARALLAX, SHOWCASE_CINEMA, SHOWCASE_FILMSTRIP, SHOWCASE_MOSAIC -> EMBER
            SHALLOWS -> TIDE
            INK_WASH, PAPER_LAYERS -> MATCHA
            PIXEL_VALLEY -> VOLT
            HOLO_FOIL -> SYNTHWAVE
            CAMPFIRE -> EMBER
            ARENA -> SYNTHWAVE
            SPECIMEN_LAB -> COBALT
            VOLTCAP, VOLTCAP_BEAT, VOLTCAP_PIXELS -> EMBER
            WATERFALL -> TIDE
            RETRO_BOUNCE -> VOLT
            LYRIC_CLOUD, LYRIC_KARAOKE, LYRIC_SHEET, LYRIC_NEON -> SYNTHWAVE
            MAGICAL, FESTIVAL -> MATCHA
            SPIRIT_FOREST -> TIDE
            AURA_CLASH, BLAZE_BEAT -> EMBER
            COVER_WALL, COVER_TUNNEL, POP_ART, COVER_RIPPLE -> SYNTHWAVE
            DISC_SLEEVE, ALBUM_TIDE -> SYNTHWAVE
            STILL_DUSK, CALM_HALO -> COBALT
            STILL_HORIZON, STILL_GLOW, CALM_DRIFT -> TIDE
            CALM_RIBBON -> COBALT
            BOLD_SPLIT, BOLD_DOTS, BOLD_BANDS -> MATCHA
            LOBBY -> MONO // Update 158
            AURA_BEAT -> EMBER
            MOUNTAINS -> COBALT
            SPECIMEN_BEAT -> COBALT
            TOWERS_128 -> COBALT
            CARTRIDGE_16 -> VOLT
            HANDHELD -> MATCHA
            PIXEL_PARADE -> EMBER
            CAMPFIRE_BEAT -> EMBER
            MANGA_BEAT -> MONO
            COMIC_PANELS -> VOLT
            PIXEL_SKY -> TIDE
            CRT_ROOM -> EMBER
            COVER_FLOW -> COBALT
            RECORD_SHELF -> EMBER
            COVER_CUBE -> SYNTHWAVE
            COVER_FLOAT -> COBALT
            HOLO_COVER -> SYNTHWAVE
            COVER_HALO -> SYNTHWAVE
            BIG_TYPE -> MONO
            PIXEL_COVER -> VOLT
            POLAROID -> MATCHA
            RETRO_TV -> EMBER
            JUKEBOX -> SYNTHWAVE
            NEON_FRAME -> SYNTHWAVE
            MAGAZINE -> MONO
            COVER_FOCUS -> EMBER
            ARCADE_ATTRACT, NEON_CARTRIDGE, POSTER_WALL, GAMING_ROOM -> SYNTHWAVE
            PHANTOM_PX, UMBRA_PX -> MONO
            else -> this
        }

    val colors: ShelfColors
        get() = when (this) {
            HANDHELD_BOOT -> pal(0xFF0D1110, 0xFF1B2220, 0xFF2A3431, 0xFF9BD86A, 0xFF7FC4F0, 0xFFF1F7EE, 0xFFB3C0B6)
            POCKET_BEAT -> pal(0xFF0F0D16, 0xFF1F1B2B, 0xFF2F2A40, 0xFFF8775A, 0xFF59C9EE, 0xFFFFF4EF, 0xFFBDB6C6)
            CONSOLE_DISC -> pal(0xFF0A0B10, 0xFF181A22, 0xFF272A35, 0xFF8FA6FB, 0xFF6EE0C8, 0xFFF2F4FA, 0xFFB3B7C3)
            ARCADE_ATTRACT -> pal(0xFF0B0612, 0xFF1B1226, 0xFF2B1D3C, 0xFFF94F8E, 0xFFF9D24A, 0xFFFFF1F6, 0xFFC3B3C9)
            NEON_CARTRIDGE -> pal(0xFF0C0909, 0xFF1D1616, 0xFF2E2323, 0xFF43E2F9, 0xFFF94BA0, 0xFFF1FBFF, 0xFFB9B2B4)
            DATA_STREAM -> pal(0xFF04080D, 0xFF0F1822, 0xFF1A2836, 0xFF35E0C2, 0xFF5A8CFA, 0xFFEDFBF8, 0xFFA6B6B6)
            PORTAL_GALLERY -> pal(0xFF0A0812, 0xFF181425, 0xFF272139, 0xFFC08BFC, 0xFFFBB15C, 0xFFF7F2FF, 0xFFBBB3C8)
            POSTER_WALL -> pal(0xFF120D0C, 0xFF241B19, 0xFF372A26, 0xFFF9A548, 0xFF5CB8F9, 0xFFFFF5EC, 0xFFC5B8AF)
            GAMING_ROOM -> pal(0xFF0B0A12, 0xFF1A1825, 0xFF2A2639, 0xFF8C6CFB, 0xFF3FE0D8, 0xFFF4F1FF, 0xFFB8B3C9)
            TROPHY_VAULT -> pal(0xFF0D0B08, 0xFF1E1A14, 0xFF2F2920, 0xFFF5C54A, 0xFF7FB3F7, 0xFFFFF7E6, 0xFFC4BBA7)
            CHILL_BLADES -> pal(0xFF04100C, 0xFF14211E, 0xFF23312D, 0xFF5EE06C, 0xFFD8F8E6, 0xFFFDFFFE, 0xFFB9BCBB)
            CHILL_POLAR -> pal(0xFF050A14, 0xFF111324, 0xFF1E1C34, 0xFF5CF2B5, 0xFFB58CFA, 0xFFFBF8FF, 0xFFB4AFBC)
            CHILL_PLANET -> pal(0xFF04060E, 0xFF151113, 0xFF261C19, 0xFF62B6FA, 0xFFF9A35C, 0xFFFFF9F5, 0xFFBCB2A9)
            CHILL_MOON -> pal(0xFF060A17, 0xFF0D1325, 0xFF141C32, 0xFFE6ECFA, 0xFF6C8BD8, 0xFFF6F8FD, 0xFFABAFB8)
            CHILL_ORBS -> pal(0xFF09091A, 0xFF191226, 0xFF291B32, 0xFF8AA8FF, 0xFFF08CC8, 0xFFFEF8FC, 0xFFBBAFB6)
            CHILL_SILK -> pal(0xFF0E0A14, 0xFF171224, 0xFF201A34, 0xFFF59BB5, 0xFF8F7CF5, 0xFFF8F7FE, 0xFFB0ADBC)
            CHILL_DUNES -> pal(0xFF140D10, 0xFF231417, 0xFF321B1F, 0xFFF4A261, 0xFFE76F7A, 0xFFFEF6F7, 0xFFBAACAD)
            CHILL_MESH -> pal(0xFF0B0B14, 0xFF1C131F, 0xFF2D1B2A, 0xFF6E8BFF, 0xFFFF7EB3, 0xFFFFF7FA, 0xFFBDAEB4)
            CHILL_LIGHT -> pal(0xFF110F0B, 0xFF1B1C1A, 0xFF25292A, 0xFFF7D59A, 0xFF9EC8E8, 0xFFF9FCFE, 0xFFB1B6BA)
            CHILL_SHADE -> pal(0xFF16110D, 0xFF1D1C15, 0xFF25261D, 0xFFF2C48D, 0xFF7FA77F, 0xFFF7FAF7, 0xFFAEB2AE)
            CHILL_RAIN -> pal(0xFF070A10, 0xFF0D1620, 0xFF132230, 0xFFF5A35A, 0xFF5AB4F5, 0xFFF5FAFE, 0xFFA9B4BC)
            CHILL_CITY -> pal(0xFF0C0A16, 0xFF151225, 0xFF1E1A35, 0xFFF7B267, 0xFF8C7CF2, 0xFFF8F7FE, 0xFFAFADBB)
            CHILL_JELLY -> pal(0xFF030A15, 0xFF131324, 0xFF231C34, 0xFF6FE7F5, 0xFFE58CF2, 0xFFFDF8FE, 0xFFBAAFBB)
            CHILL_GLADE -> pal(0xFF040B09, 0xFF0A1813, 0xFF11241D, 0xFFE8F27A, 0xFF5FBF9A, 0xFFF5FBF9, 0xFFAAB5B1)
            CHILL_HEX -> pal(0xFF05080F, 0xFF0D0F1F, 0xFF16162F, 0xFF4FD8F5, 0xFF7B6CF6, 0xFFF7F6FE, 0xFFADABBC)
            CHILL_CLOUDS -> pal(0xFF0C1022, 0xFF151A31, 0xFF1E2540, 0xFFFFB27A, 0xFF8EA6F5, 0xFFF8FAFE, 0xFFAFB2BC)
            CHILL_KOI -> pal(0xFF04100E, 0xFF15201D, 0xFF26302D, 0xFFF7884A, 0xFFF4F1E8, 0xFFFEFEFE, 0xFFBCBBBA)
            CHILL_PEAKS -> pal(0xFF08101C, 0xFF101A2A, 0xFF192437, 0xFFBFE3FA, 0xFF7FA1E0, 0xFFF7F9FD, 0xFFAEB2B9)
            BADGE_BOUNCE -> pal(0xFF0B0816, 0xFF1A1430, 0xFF292047, 0xFFF7C948, 0xFFB78CFB, 0xFFF7F3FF, 0xFFB9B2CC)
            PIXEL_WORLD -> pal(0xFF0F1A14, 0xFF1C2D24, 0xFF2B4236, 0xFF7ED957, 0xFFF4C542, 0xFFF3FAF1, 0xFFB5C6BA)
            SPARK_PX -> pal(0xFF0C0D1A, 0xFF1A1C30, 0xFF282B47, 0xFFF9D645, 0xFF6FA8FA, 0xFFFFFBEA, 0xFFBBBACB)
            TORRENT_PX -> pal(0xFF06111E, 0xFF112236, 0xFF1C334F, 0xFF4CB4F9, 0xFF7FF0E0, 0xFFEEF8FF, 0xFFA9BAC9)
            SPROUT_PX -> pal(0xFF0B140C, 0xFF18261A, 0xFF253828, 0xFF73D35A, 0xFFF7D861, 0xFFF1F9EE, 0xFFB1C2B1)
            MIND_PX -> pal(0xFF110A18, 0xFF22162E, 0xFF332244, 0xFFF06BC6, 0xFF9C8BFB, 0xFFFDF0FA, 0xFFC7B4C9)
            BLAZE_PX -> pal(0xFF140806, 0xFF27130F, 0xFF3B1F18, 0xFFF96A2E, 0xFFF9C443, 0xFFFFF2EA, 0xFFCDB4AA)
            PHANTOM_PX -> pal(0xFF0B0914, 0xFF181528, 0xFF26213C, 0xFF9C83F2, 0xFF6FE3C4, 0xFFF3F0FF, 0xFFB5B0C8)
            FROST_PX -> pal(0xFF08101A, 0xFF13202F, 0xFF1F3045, 0xFF8FDCF9, 0xFFD6EEFD, 0xFFF1FAFF, 0xFFAEC0CB)
            TERRA_PX -> pal(0xFF140F08, 0xFF271E12, 0xFF3A2D1C, 0xFFE9B35A, 0xFFF48A52, 0xFFFFF6E8, 0xFFCABBA5)
            VENOM_PX -> pal(0xFF0E0A12, 0xFF1D1524, 0xFF2D2137, 0xFFB46CF3, 0xFF8BEA5A, 0xFFF7F0FC, 0xFFBFB2C6)
            MEADOW_PX -> pal(0xFF0F1410, 0xFF1D261F, 0xFF2C3830, 0xFFE7C77F, 0xFF8FCB8A, 0xFFF6F7F0, 0xFFBDC1B5)
            DOJO_PX -> pal(0xFF140B09, 0xFF261713, 0xFF39241E, 0xFFF0703F, 0xFFF4C27A, 0xFFFFF3EC, 0xFFC9B6AD)
            GALE_PX -> pal(0xFF0B1220, 0xFF172238, 0xFF243350, 0xFF8EC5FA, 0xFFF4E1A6, 0xFFF1F6FF, 0xFFB0BACD)
            THICKET_PX -> pal(0xFF0C120A, 0xFF192315, 0xFF263421, 0xFFB5D648, 0xFFF29E4C, 0xFFF4F9EC, 0xFFB7C2AD)
            BEDROCK_PX -> pal(0xFF120F0C, 0xFF231E19, 0xFF352D26, 0xFFD39B65, 0xFF8FD0E0, 0xFFFAF4EC, 0xFFC1B7AC)
            WYRM_PX -> pal(0xFF0A0A16, 0xFF17172B, 0xFF242442, 0xFF6B7CF9, 0xFFF26A6A, 0xFFF0F1FF, 0xFFB2B3CB)
            UMBRA_PX -> pal(0xFF070608, 0xFF141216, 0xFF221F25, 0xFFE24B5F, 0xFF9C8E78, 0xFFF4F0F2, 0xFFB0AAAE)
            ALLOY_PX -> pal(0xFF0B0D10, 0xFF191D22, 0xFF282E36, 0xFFA9BCD3, 0xFFF4A23A, 0xFFF2F5F9, 0xFFB4BAC3)
            GLIMMER_PX -> pal(0xFF120B16, 0xFF24172B, 0xFF372340, 0xFFF7A1D8, 0xFFB8F0E6, 0xFFFFF2FB, 0xFFCBB7C6)
            ALBUM_TIDE -> pal(0xFF100D1B, 0xFF21192F, 0xFF332741, 0xFFC58EED, 0xFF72D6DB, 0xFFFAF8FE, 0xFFC4B8D0)
            DISC_SLEEVE -> pal(0xFF0B0A10, 0xFF1A1823, 0xFF292636, 0xFFBA8FFC, 0xFF64D2FA, 0xFFF6F3FF, 0xFFB7B3C5)
            STILL_DUSK -> pal(0xFF0C0D1A, 0xFF1B1D30, 0xFF2A2D47, 0xFFFBA17E, 0xFF8F7FFB, 0xFFF7F4FF, 0xFFB9B8CB)
            STILL_HORIZON -> pal(0xFF0F151C, 0xFF1D2730, 0xFF2C3947, 0xFF81C6B7, 0xFFB9D3EE, 0xFFF2F8FA, 0xFFB0BCC4)
            STILL_GLOW -> pal(0xFF090B10, 0xFF181B25, 0xFF262B39, 0xFF708EFB, 0xFFDE80FB, 0xFFF3F4FA, 0xFFB1B4C1)
            CALM_RIBBON -> pal(0xFF071024, 0xFF13203B, 0xFF203054, 0xFF83B8FB, 0xFFC1D9FD, 0xFFF1F6FF, 0xFFB3BFD3)
            CALM_DRIFT -> pal(0xFF090D16, 0xFF171D2A, 0xFF242D3E, 0xFF9DD0FC, 0xFFFDC4E5, 0xFFF3F6FB, 0xFFB4BAC6)
            CALM_HALO -> pal(0xFF0B0913, 0xFF1A1827, 0xFF29263C, 0xFFA68FFC, 0xFF72E0D3, 0xFFF5F3FF, 0xFFB9B6C8)
            BOLD_SPLIT -> pal(0xFF26292F, 0xFF383B44, 0xFF4A4E5A, 0xFFF93444, 0xFF4FBDED, 0xFFFFFFFF, 0xFFCCD0D6)
            BOLD_DOTS -> pal(0xFF1B2532, 0xFF2B3747, 0xFF3A4A5B, 0xFFF9C642, 0xFFFA6481, 0xFFFFFFFF, 0xFFC9CFD8)
            BOLD_BANDS -> pal(0xFF17171A, 0xFF28282D, 0xFF393940, 0xFF34F9DA, 0xFFF97C43, 0xFFFFFFFF, 0xFFC3C3C9)
            // Update 158, Lobby (light): near-white backdrop, light-grey panels, a soft pale blue, dark grey writing.
            // (The accent a shade deeper than pale, so the focus ring and white writing on it still read on near-white.)
            LOBBY -> pal(0xFFF2F4F6, 0xFFE5E8EC, 0xFFD8DDE3, 0xFF4592CC, 0xFF9CC9EA, 0xFF3A3F46, 0xFF757C85)
            AURA_BEAT -> pal(0xFF0A0911, 0xFF191826, 0xFF29263C, 0xFFF9B534, 0xFF5FB7FA, 0xFFFFF8EC, 0xFFBDB9CA)
            MOUNTAINS -> pal(0xFF0A0E1B, 0xFF171E33, 0xFF252D4A, 0xFF83A8FB, 0xFFFBB36F, 0xFFF2F5FF, 0xFFB5BBCC)
            SPECIMEN_BEAT -> pal(0xFF0B1130, 0xFF182048, 0xFF263063, 0xFF7FDD7D, 0xFF5883E5, 0xFFEFF6FF, 0xFFBAC2DB)
            TOWERS_128 -> pal(0xFF050713, 0xFF101530, 0xFF1C244A, 0xFF618FFA, 0xFFAAC5FC, 0xFFEEF3FF, 0xFFADB6CC)
            CARTRIDGE_16 -> pal(0xFF0F0F18, 0xFF1F1F2F, 0xFF2F2F47, 0xFFE3564F, 0xFF63B7E2, 0xFFF6F3EA, 0xFFB8B6C2)
            HANDHELD -> pal(0xFF101018, 0xFF20202D, 0xFF313142, 0xFF8E6FFB, 0xFF69D4BA, 0xFFF5F3FF, 0xFFB9B7C8)
            PIXEL_PARADE -> pal(0xFF0C0A11, 0xFF1A1721, 0xFF2A2632, 0xFFF09F3D, 0xFFFAD54F, 0xFFFFF6D6, 0xFFBAB1A2)
            CAMPFIRE_BEAT -> pal(0xFF0A090C, 0xFF19171D, 0xFF29262E, 0xFFF98D34, 0xFFFBD88E, 0xFFFFF5EA, 0xFFBDB5AC)
            MANGA_BEAT -> pal(0xFF0E0E10, 0xFF1E1E22, 0xFF2F2F34, 0xFFF2F2F2, 0xFFFA4F6E, 0xFFFFFFFF, 0xFFBEBEC3)
            COMIC_PANELS -> pal(0xFF131117, 0xFF24212A, 0xFF35313E, 0xFFF95E42, 0xFF43B6F9, 0xFFFFF8EE, 0xFFC0BCC8)
            PIXEL_SKY -> pal(0xFF0E172C, 0xFF1B2945, 0xFF2B3D64, 0xFFF09F3D, 0xFFFAD54F, 0xFFF4F8FF, 0xFFB7C1D1)
            CRT_ROOM -> pal(0xFF100C0B, 0xFF211917, 0xFF312724, 0xFFE3A04F, 0xFF83B3FB, 0xFFFFF4EA, 0xFFC0B6AE)
            COVER_FLOW -> pal(0xFF07070A, 0xFF16161B, 0xFF25252D, 0xFF92B8FC, 0xFFFC9DC8, 0xFFF5F6FF, 0xFFB3B5C1)
            RECORD_SHELF -> pal(0xFF130C09, 0xFF251A15, 0xFF382921, 0xFFFA9F4F, 0xFFFBD17E, 0xFFFFF4E6, 0xFFC5B5A8)
            COVER_CUBE -> pal(0xFF0B0913, 0xFF1A1827, 0xFF2A263C, 0xFFAB8EFC, 0xFF60DDE2, 0xFFF6F2FF, 0xFFB7AFC7)
            COVER_FLOAT -> pal(0xFF0A0C11, 0xFF191C25, 0xFF272C39, 0xFF83D3FB, 0xFFFDB2D0, 0xFFF3F7FF, 0xFFB2B7C3)
            HOLO_COVER -> pal(0xFF0C0A13, 0xFF1B1929, 0xFF2B263E, 0xFFC59EFC, 0xFF7FECD6, 0xFFF8F4FF, 0xFFBBB5CB)
            COVER_HALO -> pal(0xFF090A10, 0xFF171921, 0xFF262933, 0xFFFB7EB6, 0xFF7ED5FB, 0xFFF6F7FF, 0xFFB3B6C0)
            BIG_TYPE -> pal(0xFF0C0C0E, 0xFF1D1D21, 0xFF2D2D32, 0xFFF9CF45, 0xFFFA5973, 0xFFFFFFFF, 0xFFC1C1C6)
            PIXEL_COVER -> pal(0xFF0C0A11, 0xFF1A1721, 0xFF2A2632, 0xFFF09F3D, 0xFF63B7E2, 0xFFFFF6D6, 0xFFBAB1A2)
            POLAROID -> pal(0xFF16110B, 0xFF2B2118, 0xFF403225, 0xFFE3A04F, 0xFF92C5E5, 0xFFFFF6EA, 0xFFC7BAAA)
            RETRO_TV -> pal(0xFF0C0B09, 0xFF1D1916, 0xFF2E2823, 0xFFFA9F5F, 0xFF83B3FB, 0xFFFFF4EA, 0xFFC0B6AE)
            JUKEBOX -> pal(0xFF10090E, 0xFF22151E, 0xFF32212F, 0xFFFA618C, 0xFFFAC752, 0xFFFFF2F6, 0xFFC9B3BF)
            NEON_FRAME -> pal(0xFF0C0808, 0xFF1E1414, 0xFF2F2121, 0xFFF94381, 0xFF43E2F9, 0xFFFFF0F4, 0xFFC6B2B7)
            MAGAZINE -> pal(0xFF0E0C0C, 0xFF201C1C, 0xFF312C2C, 0xFFFA5454, 0xFFF9CF45, 0xFFFFF8F2, 0xFFC7BEBC)
            COVER_FOCUS -> pal(0xFF09090C, 0xFF17171D, 0xFF27272F, 0xFFFBB46F, 0xFF83C7FB, 0xFFF7F5F2, 0xFFB6B4BB)
            NOW_PLAYING -> pal(0xFF0C0913, 0xFF1C1727, 0xFF2B233B, 0xFFBA8FFC, 0xFF60DDE2, 0xFFF7F2FF, 0xFFB7AFC7)
            // Music-reactive: the cover decides the colours; these show before anything has played.
            SPECTRUM -> pal(0xFF06080D, 0xFF151825, 0xFF222839, 0xFF52DB9F, 0xFFF9CF45, 0xFFF1F7F4, 0xFFA8B7B0)
            PULSE_RING -> pal(0xFF0D080E, 0xFF1F1521, 0xFF302133, 0xFFFB6F9C, 0xFF7ED5FB, 0xFFFFF1F6, 0xFFBFADB7)
            BEAT_TUNNEL -> pal(0xFF06060D, 0xFF131321, 0xFF202034, 0xFF7E61FA, 0xFF43EEF9, 0xFFF0F0FF, 0xFFAFAFC7)
            REACTOR -> pal(0xFF06090E, 0xFF121823, 0xFF1E2839, 0xFF34C4F9, 0xFFF98B43, 0xFFEFF7FF, 0xFFA7B3C2)
            THUNDER -> pal(0xFF090A11, 0xFF181A27, 0xFF26293C, 0xFFF9D141, 0xFF92B9FC, 0xFFFFFBEA, 0xFFBAB9C6)
            BLOOM -> pal(0xFF0D070F, 0xFF1F1522, 0xFF302134, 0xFFFB82B9, 0xFFFBD17E, 0xFFFFF2F8, 0xFFC8B4C0)
            TAPE_DECK -> pal(0xFF0E0B08, 0xFF201A16, 0xFF312922, 0xFFF99A42, 0xFFFCE1A9, 0xFFFFF5EA, 0xFFC0B5A9)
            CHLADNI -> pal(0xFF0A0907, 0xFF1C1915, 0xFF2E2923, 0xFFE5C17D, 0xFF83D3FB, 0xFFFFF8EC, 0xFFBEB6A9)
            LASER_STAGE -> pal(0xFF05050E, 0xFF121221, 0xFF1F1F36, 0xFF34F9DE, 0xFFF94391, 0xFFEFFFFC, 0xFFA9B3BC)
            KALEIDO -> pal(0xFF0B0711, 0xFF1A1427, 0xFF2A203E, 0xFFF9AF40, 0xFF53D5FA, 0xFFFFF6EA, 0xFFBFB5C9)
            SHATTER -> pal(0xFF08080A, 0xFF17181E, 0xFF26272F, 0xFFFA6360, 0xFFFACF6B, 0xFFFFF4F2, 0xFFBAB5B6)
            TERRAIN -> pal(0xFF070516, 0xFF15112C, 0xFF231E46, 0xFFF945CD, 0xFF45E4F9, 0xFFFBEFFF, 0xFFB7B0CB)
            FIREWORKS -> pal(0xFF050711, 0xFF121727, 0xFF1E253E, 0xFFF9C045, 0xFFFA547E, 0xFFFFF8EC, 0xFFAFB4C5)
            NEON_PUDDLE -> pal(0xFF06060D, 0xFF131521, 0xFF202334, 0xFFF94381, 0xFF43E1F9, 0xFFFFF0F5, 0xFFACB0C0)
            VINYL -> pal(0xFF10090E, 0xFF231720, 0xFF362331, 0xFFFA7D5E, 0xFFFAC752, 0xFFFFF4EE, 0xFFCAB9BD)
            CD_SPIN -> pal(0xFF080B13, 0xFF151C2B, 0xFF222C46, 0xFF5FC6FA, 0xFFFB73CE, 0xFFF0F7FF, 0xFFB3BDCF)
            LAVA -> pal(0xFF110816, 0xFF23142C, 0xFF362043, 0xFFFA54A3, 0xFFF9B143, 0xFFFFF0F8, 0xFFCBB5C9)
            RIBBONS -> pal(0xFF0A0A1A, 0xFF181B34, 0xFF272B4C, 0xFF54D5FA, 0xFFB470FB, 0xFFF1F4FF, 0xFFB8BBD2)
            DISCO -> pal(0xFF0E0716, 0xFF1F122C, 0xFF311E45, 0xFFF943B6, 0xFF43EBF9, 0xFFFFF0FB, 0xFFC4B4CE)
            SPEAKER -> pal(0xFF0B090C, 0xFF1B181F, 0xFF2C2731, 0xFFF95E3C, 0xFF4CD6F9, 0xFFFFF3EE, 0xFFBDB6BA)
            ERA_8BIT -> pal(0xFF0C0F29, 0xFF1A2046, 0xFF2A3162, 0xFFF9603F, 0xFFF9D14D, 0xFFF8F8F8, 0xFFBEC3DC)
            MODE7 -> pal(0xFF110C27, 0xFF211944, 0xFF332864, 0xFFFB6E9D, 0xFF64D5FA, 0xFFFFF5FA, 0xFFC4BDD8)
            PS1_POLY -> pal(0xFF090911, 0xFF181826, 0xFF27273C, 0xFFC9C9D9, 0xFFE0655C, 0xFFF2F2F6, 0xFFB0B0BF)
            N64_PLAINS -> pal(0xFF0B172B, 0xFF182948, 0xFF253D65, 0xFF5AD380, 0xFFF9CE43, 0xFFF4FAFF, 0xFFB7C4D4)
            PS2_HAZE -> pal(0xFF030714, 0xFF0B162C, 0xFF162445, 0xFF407DF9, 0xFFABD1FC, 0xFFEEF4FF, 0xFFA5B2CB)
            BLUE_SKY -> pal(0xFF082243, 0xFF143661, 0xFF214C80, 0xFFF99334, 0xFF43C8F9, 0xFFF7FBFF, 0xFFBCCBDB)
            XMB_WAVE -> pal(0xFF0A1129, 0xFF182042, 0xFF27315E, 0xFF83B3FB, 0xFFC4D6FD, 0xFFF4F7FF, 0xFFB6BED0)
            PLAZA -> pal(0xFF171A1F, 0xFF282C33, 0xFF39404A, 0xFF48B8E5, 0xFFD0E6F1, 0xFFF6F9FB, 0xFFBAC1C7)
            VECTOR -> pal(0xFF020303, 0xFF0D1213, 0xFF192122, 0xFF81FBD9, 0xFFFA63DE, 0xFFEAFFF9, 0xFF96ABA5)
            VOLTCAP -> pal(0xFF07060A, 0xFF16141A, 0xFF25222B, 0xFFF09F3D, 0xFFFAD54F, 0xFFFFF6D6, 0xFFBAB1A2)
            VOLTCAP_BEAT -> pal(0xFF09070A, 0xFF18151C, 0xFF28232D, 0xFFF09F3D, 0xFFFAD54F, 0xFFFFF6D6, 0xFFBAB1A2)
            VOLTCAP_PIXELS -> pal(0xFF0C0A11, 0xFF1A1721, 0xFF2A2632, 0xFFF09F3D, 0xFFFAD54F, 0xFFFFF6D6, 0xFFBAB1A2)
            WATERFALL -> pal(0xFF0D171D, 0xFF19262F, 0xFF273846, 0xFF53B4DD, 0xFF7BDD89, 0xFFF2FBF5, 0xFFB0BDC1)
            RETRO_BOUNCE -> pal(0xFF0A0918, 0xFF19172D, 0xFF282642, 0xFFFA547E, 0xFF54D5FA, 0xFFF4F1FF, 0xFFBAB6CE)
            LYRIC_CLOUD -> pal(0xFF09060E, 0xFF171320, 0xFF261F31, 0xFFE38FFC, 0xFFFBB86F, 0xFFF8F1FF, 0xFFBEB5C9)
            LYRIC_KARAOKE -> pal(0xFF0A0813, 0xFF191527, 0xFF29223C, 0xFF61C6FA, 0xFFFB73B5, 0xFFF4F7FF, 0xFFB9BDCD)
            LYRIC_SHEET -> pal(0xFF0B0A0E, 0xFF191720, 0xFF292731, 0xFFFBC46F, 0xFF9E8FFC, 0xFFFBF7EE, 0xFFBDB9AF)
            LYRIC_NEON -> pal(0xFF0B0707, 0xFF1C1414, 0xFF2D2121, 0xFFFA54A3, 0xFF54ECFA, 0xFFFFF2F6, 0xFFC8B7BD)
            SPECIMEN_LAB -> pal(0xFF0B1130, 0xFF182048, 0xFF263063, 0xFF7FDD7D, 0xFF5883E5, 0xFFEFF6FF, 0xFFBAC2DB)
            MAGICAL -> pal(0xFF191329, 0xFF2C2344, 0xFF40335E, 0xFFFC92CF, 0xFFA2D7FC, 0xFFFFF5FC, 0xFFD2C6DC)
            SPIRIT_FOREST -> pal(0xFF07100B, 0xFF12221A, 0xFF1E362A, 0xFF92EDC7, 0xFFE5D07D, 0xFFF2FBF5, 0xFFAFC3B7)
            FESTIVAL -> pal(0xFF0A0C1F, 0xFF181B36, 0xFF262B4E, 0xFFF95E42, 0xFFFAC65F, 0xFFFFF6EE, 0xFFC5C0D0)
            AURA_CLASH -> pal(0xFF0A0911, 0xFF191826, 0xFF29263C, 0xFFF9B534, 0xFF5FB7FA, 0xFFFFF8EC, 0xFFBDB9CA)
            COVER_WALL -> pal(0xFF0A0910, 0xFF1A1721, 0xFF2A2633, 0xFFFB73AF, 0xFF73D1FB, 0xFFFFF4FA, 0xFFBFB7C7)
            COVER_TUNNEL -> pal(0xFF05050A, 0xFF131320, 0xFF212133, 0xFF9C7FFB, 0xFFFA8C61, 0xFFF4F0FF, 0xFFB8B4C9)
            POP_ART -> pal(0xFF100B0E, 0xFF231820, 0xFF362731, 0xFFF94381, 0xFFF9DE43, 0xFFFFF7FA, 0xFFCCBBC3)
            COVER_RIPPLE -> pal(0xFF06121B, 0xFF112330, 0xFF1C3646, 0xFF4BD3F9, 0xFFFBB86F, 0xFFEFFBFF, 0xFFAFC2CD)
            BLAZE_BEAT -> pal(0xFF0F0706, 0xFF23140F, 0xFF392219, 0xFFF97734, 0xFFF9CF45, 0xFFFFF4EA, 0xFFCAB6A9)
            ARENA -> pal(0xFF06060C, 0xFF151420, 0xFF242132, 0xFFF94153, 0xFF45B5F9, 0xFFF8F6FF, 0xFFB9B5C6)
            WARP -> pal(0xFF04030D, 0xFF100E23, 0xFF1D1A3A, 0xFF8D7FFB, 0xFF43E3F9, 0xFFF2F0FF, 0xFFB2B0CA)
            PRISM_ATRIUM -> pal(0xFF090913, 0xFF18182B, 0xFF282844, 0xFF92D7FC, 0xFFFB8ED7, 0xFFF4F6FF, 0xFFB9BBCF)
            ORBIT_STATION -> pal(0xFF03050C, 0xFF0E1423, 0xFF1B2439, 0xFF61C6FA, 0xFFFAB461, 0xFFEFF6FF, 0xFFABB5C6)
            SHOWCASE_PARALLAX -> pal(0xFF090B0E, 0xFF181C22, 0xFF272C35, 0xFFF09F3D, 0xFF73D1FB, 0xFFF6F8FB, 0xFFB3B8C0)
            SHOWCASE_CINEMA -> pal(0xFF070709, 0xFF16161A, 0xFF25252B, 0xFFE4C15E, 0xFFDCDCE5, 0xFFF7F7F9, 0xFFB4B4BB)
            SHOWCASE_FILMSTRIP -> pal(0xFF0A0907, 0xFF1C1814, 0xFF2D2721, 0xFFF98043, 0xFFFBD48E, 0xFFFFF6EC, 0xFFBDB4AA)
            SHOWCASE_MOSAIC -> pal(0xFF070A0F, 0xFF161C27, 0xFF232D3E, 0xFF549EFA, 0xFFB2EC60, 0xFFF1F5FB, 0xFFAFB6C2)
            SHALLOWS -> pal(0xFF041116, 0xFF0E232A, 0xFF18363F, 0xFF55D8CC, 0xFFFCE5A9, 0xFFEFFCFA, 0xFFA2BAB9)
            INK_WASH -> pal(0xFF11100D, 0xFF23211C, 0xFF35322B, 0xFFD56458, 0xFFE7DCC5, 0xFFF7F2E8, 0xFFBCB6AB)
            PAPER_LAYERS -> pal(0xFF0F1217, 0xFF1F242C, 0xFF303741, 0xFFFA8C6A, 0xFF81C6A9, 0xFFF6F8FA, 0xFFB7BDC5)
            PIXEL_VALLEY -> pal(0xFF0C0F1B, 0xFF1B1F33, 0xFF2A2F4A, 0xFF708EFB, 0xFFFBB86F, 0xFFF2F4FF, 0xFFB5B9CC)
            HOLO_FOIL -> pal(0xFF0B0A10, 0xFF1A1821, 0xFF2A2733, 0xFFBA8FFC, 0xFF80F1E5, 0xFFF7F4FF, 0xFFB9B5C5)
            CAMPFIRE -> pal(0xFF0A090C, 0xFF19171D, 0xFF29262E, 0xFFF98D34, 0xFFFBD88E, 0xFFFFF5EA, 0xFFBDB5AC)
            FROST_TYPE -> pal(0xFF060E18, 0xFF121D2C, 0xFF1E2D41, 0xFF83D6FB, 0xFFC4EEFD, 0xFFF0FAFF, 0xFFACBCC9)
            TERRA_TYPE -> pal(0xFF110B07, 0xFF251A13, 0xFF39291E, 0xFFDEA55E, 0xFFF1D69D, 0xFFFFF6EA, 0xFFC6B6A3)
            MEADOW_TYPE -> pal(0xFF0D110E, 0xFF1C241E, 0xFF2C362E, 0xFF9CC86E, 0xFFF1E2A8, 0xFFF6F8F2, 0xFFB7BFB4)
            DOJO_TYPE -> pal(0xFF110B07, 0xFF231812, 0xFF36261C, 0xFFDE674F, 0xFFEEB36F, 0xFFFFF4EA, 0xFFC5B5A8)
            GALE_TYPE -> pal(0xFF0A1020, 0xFF182139, 0xFF253251, 0xFF92B9FC, 0xFFFCD69D, 0xFFF2F6FF, 0xFFB6BED0)
            THICKET_TYPE -> pal(0xFF0A1009, 0xFF182217, 0xFF263423, 0xFFB5CE5F, 0xFFE4D06E, 0xFFF3F8EA, 0xFFB0BBA8)
            BEDROCK_TYPE -> pal(0xFF120C08, 0xFF251A14, 0xFF392A20, 0xFFD69657, 0xFFBAA482, 0xFFFFF5EA, 0xFFC5B7AA)
            WYRM_TYPE -> pal(0xFF0A0918, 0xFF19172E, 0xFF282546, 0xFF7360FA, 0xFFFA8C51, 0xFFF3F1FF, 0xFFB8B5CE)
            UMBRA_TYPE -> pal(0xFF06060A, 0xFF13131A, 0xFF21212B, 0xFFC06D7F, 0xFFE5C26E, 0xFFF1EFF4, 0xFFACA9B2)
            ALLOY_TYPE -> pal(0xFF0B0C0F, 0xFF191C21, 0xFF292D33, 0xFF90A6BE, 0xFFD0D8E2, 0xFFF3F6FA, 0xFFB3B8C0)
            GLIMMER_TYPE -> pal(0xFF120B14, 0xFF241727, 0xFF37243B, 0xFFF192C4, 0xFFCAB4FD, 0xFFFFF2FA, 0xFFCBB8C8)
            CRT_TUNNEL -> pal(0xFF09080A, 0xFF18161C, 0xFF28252E, 0xFFFA6E4F, 0xFF60CFFA, 0xFFF6F3F8, 0xFFB6B2BC)
            MEMORY_CARD -> pal(0xFF090B11, 0xFF181B28, 0xFF262C3E, 0xFF808FFB, 0xFFFB7EB6, 0xFFF2F4FF, 0xFFB6B9CB)
            ENERGY_CORE -> pal(0xFF06090E, 0xFF121821, 0xFF1E2834, 0xFF3BDCF9, 0xFFB470FB, 0xFFEFFBFF, 0xFFA6B4BF)
            RACING_32 -> pal(0xFF10090E, 0xFF22161E, 0xFF33222F, 0xFFF97C43, 0xFFFAC65C, 0xFFFFF4EC, 0xFFC8B7BE)
            FIRE_32 -> pal(0xFF0A0605, 0xFF1A110F, 0xFF2B1D18, 0xFFF97634, 0xFFF9CD46, 0xFFFFF4EA, 0xFFC7B5A9)
            VENOM_TYPE -> pal(0xFF0B070F, 0xFF1C1323, 0xFF2D1E37, 0xFFA243EB, 0xFF7FFB6F, 0xFFF6EFFF, 0xFFBAAEC6)
            CIRCUIT -> pal(0xFF050909, 0xFF101A18, 0xFF1B2B28, 0xFF52DBB4, 0xFFFAC752, 0xFFEAFBF6, 0xFF9EB6B0)
            GEARWORKS -> pal(0xFF0D0A07, 0xFF1F1915, 0xFF302921, 0xFFE49F49, 0xFFB9C4CE, 0xFFFBF5EC, 0xFFBDB5A8)
            SUMMER_SKY -> pal(0xFF0C2650, 0xFF1B3968, 0xFF294D81, 0xFF44A6F0, 0xFFFCE8AB, 0xFFF4F9FF, 0xFFB9C7D9)
            MECHA_HUD -> pal(0xFF060A0C, 0xFF131A1F, 0xFF1E2931, 0xFFF9A634, 0xFF4EC9EE, 0xFFF0F6F8, 0xFFA2AEB5)
            MOON_BLADE -> pal(0xFF070915, 0xFF141930, 0xFF222747, 0xFFE34F72, 0xFFC4D4FD, 0xFFF4F5FF, 0xFFB5B8CB)
            SPARK_TYPE -> pal(0xFF0D0C07, 0xFF1F1D13, 0xFF312E1D, 0xFFF2CF3B, 0xFFFCF1A3, 0xFFFFFBEA, 0xFFC2BDA3)
            TORRENT_TYPE -> pal(0xFF060E1A, 0xFF131F33, 0xFF1F3049, 0xFF6792EC, 0xFFA1E2FC, 0xFFEFF4FF, 0xFFACB7CB)
            SPROUT_TYPE -> pal(0xFF070F09, 0xFF162218, 0xFF233427, 0xFF89CA63, 0xFFE0DE8D, 0xFFF1F8EE, 0xFFAFBDAB)
            MIND_TYPE -> pal(0xFF110711, 0xFF241527, 0xFF37213B, 0xFFF45A89, 0xFFCAA9FC, 0xFFFFF0F6, 0xFFC6AEBC)
            BLAZE_TYPE -> pal(0xFF160606, 0xFF2B120F, 0xFF401E19, 0xFFF96834, 0xFFF9D043, 0xFFFFF4EA, 0xFFCCB2A6)
            PHANTOM_TYPE -> pal(0xFF0A070F, 0xFF191524, 0xFF292139, 0xFF9C82D1, 0xFF62E2C6, 0xFFF4F0FF, 0xFFB5AEC6)
            POCKET_LCD -> pal(0xFF0F300F, 0xFF224822, 0xFF396639, 0xFFCAEA43, 0xFFCAEB52, 0xFFE0F8D0, 0xFFABBE9E)
            POCKET_SHELL -> pal(0xFF17171C, 0xFF2C2C34, 0xFF3F3F4A, 0xFFC86490, 0xFF9191AB, 0xFFF1F0F4, 0xFFB2B2BD)
            SHINOBI -> pal(0xFF0A0F1B, 0xFF192236, 0xFF27334D, 0xFFF98734, 0xFFEDBF53, 0xFFFFF5EC, 0xFFBBBFCE)
            POWER_AURA -> pal(0xFF0B0D24, 0xFF191D3F, 0xFF282E58, 0xFFF9B534, 0xFF4CACF0, 0xFFFFF8E8, 0xFFBBC0D4)
            AURORA -> pal(0xFF050A13, 0xFF111C2B, 0xFF1D2D40, 0xFF50DEAE, 0xFF9F80FB, 0xFFEFFAF7, 0xFFA4B7B8)
            NEBULA -> pal(0xFF090611, 0xFF191227, 0xFF291E3D, 0xFFC681FB, 0xFFFB82B7, 0xFFF7EFFF, 0xFFBBB0CA)
            PIXEL_FLOW -> pal(0xFF0A0C14, 0xFF191C2C, 0xFF282D43, 0xFFFA618C, 0xFF60DDE2, 0xFFF4F6FF, 0xFFB3B7C9)
            GLASS -> pal(0xFF0D1015, 0xFF1D2430, 0xFF2C3749, 0xFFA2D7FC, 0xFFFFFFFF, 0xFFF4F8FC, 0xFFB7C0CB)
            RETRO_WAVE -> pal(0xFF0E0A1B, 0xFF201533, 0xFF32234C, 0xFFF98F42, 0xFFF943AC, 0xFFFFF2EA, 0xFFC9B6CE)
            ORBIT -> pal(0xFF07090F, 0xFF151927, 0xFF22293C, 0xFFFAC65C, 0xFF72C0E0, 0xFFF5F7FB, 0xFFADB3C0)
            DIGITAL_RAIN -> pal(0xFF020704, 0xFF0D1912, 0xFF16291F, 0xFF43F97C, 0xFFB8FDCA, 0xFFE9FFEF, 0xFF93B39C)
            CONSTELLATION -> pal(0xFF060811, 0xFF131829, 0xFF1F273F, 0xFF8EB5FB, 0xFFFBDE8E, 0xFFF0F4FF, 0xFFABB2C4)
            LIQUID -> pal(0xFF060C11, 0xFF121F28, 0xFF1D303D, 0xFF55D8CD, 0xFF7F8FFB, 0xFFEDFAF9, 0xFFA4B7BA)
            MINIMAL_CONSOLE -> pal(0xFF101214, 0xFF1E2125, 0xFF2D3137, 0xFF54A3FA, 0xFFC8CDD3, 0xFFF4F6F8, 0xFFAAAEB5)
            SAKURA -> pal(0xFF110A0E, 0xFF23171D, 0xFF36232E, 0xFFFCA1C5, 0xFFFDC4D6, 0xFFFFF4F8, 0xFFC9B6BF)
            ARCADE_CRT -> pal(0xFF060609, 0xFF15151A, 0xFF24242C, 0xFFF94141, 0xFF43DEF9, 0xFFF6F6FA, 0xFFB1B1BA)
            DEEP_SEA -> pal(0xFF02090F, 0xFF0D1C27, 0xFF172D3C, 0xFF34EBF9, 0xFF70ECA8, 0xFFEAFBFC, 0xFF9CB2BA)
            // Unique on purpose: NowPlayingScene treats a reactive theme's exact palette as "no theme colours, use the cover's".
            CUSTOM_THEME -> pal(0xFF0B1115, 0xFF182229, 0xFF263540, 0xFF5CE0C2, 0xFFF2A65A, 0xFFF1F7F6, 0xFFAEBDBB)
            CUSTOM_VISUALIZER -> pal(0xFF0C0F16, 0xFF1A1F2C, 0xFF283043, 0xFFE06CF2, 0xFF5CC8F2, 0xFFF6F2FB, 0xFFB8B4C6)
            else -> base
        }

    private val base: ShelfColors
        get() = when (this) {
            // Voltcap's own theme: electric orange and spark yellow on a warm,
            // almost-black brown.
            VOLT -> ShelfColors(
                background = Color(0xFF0F0B07),
                surface = Color(0xFF211A15),
                surfaceRaised = Color(0xFF312820),
                accent = Color(0xFFF09F3D),
                accentGlow = Color(0x73F59A2C),
                secondary = Color(0xFFFAD54F),
                textPrimary = Color(0xFFFBF5EC),
                textMuted = Color(0xFFBFB5A7)
            )
            // Water blue with a warm shell-cream second colour.
            TIDE -> ShelfColors(
                background = Color(0xFF071014),
                surface = Color(0xFF13222A),
                surfaceRaised = Color(0xFF1E323E),
                accent = Color(0xFF67C3EC),
                accentGlow = Color(0x7363C5F0),
                secondary = Color(0xFFE6CE9C),
                textPrimary = Color(0xFFEFF7FA),
                textMuted = Color(0xFFA7B7BF)
            )
            EMBER -> ShelfColors(
                background = Color(0xFF0B0C0F),
                surface = Color(0xFF1D2028),
                surfaceRaised = Color(0xFF2B2F3C),
                accent = Color(0xFFE4A549),
                accentGlow = Color(0x73E8A33D),
                secondary = Color(0xFF6AD3C7),
                textPrimary = Color(0xFFF3F4F7),
                textMuted = Color(0xFFB7B9C2)
            )
            COBALT -> ShelfColors(
                background = Color(0xFF070A11),
                surface = Color(0xFF171C29),
                surfaceRaised = Color(0xFF242D3D),
                accent = Color(0xFF518EFA),
                accentGlow = Color(0x734C8DFF),
                secondary = Color(0xFF83D1F1),
                textPrimary = Color(0xFFF1F4FA),
                textMuted = Color(0xFFADB4C3)
            )
            SYNTHWAVE -> ShelfColors(
                background = Color(0xFF100A1A),
                surface = Color(0xFF201530),
                surfaceRaised = Color(0xFF312247),
                accent = Color(0xFFFA54A3),
                accentGlow = Color(0x73FF4FA3),
                secondary = Color(0xFF43E7F9),
                textPrimary = Color(0xFFF7F0FF),
                textMuted = Color(0xFFC2AFCF)
            )
            MATCHA -> ShelfColors(
                background = Color(0xFF0A0D0B),
                surface = Color(0xFF18221C),
                surfaceRaised = Color(0xFF25322A),
                accent = Color(0xFF81CF81),
                accentGlow = Color(0x737FD17F),
                secondary = Color(0xFFD6C77D),
                textPrimary = Color(0xFFF0F5F1),
                textMuted = Color(0xFFAFBAB2)
            )
            MONO -> ShelfColors(
                // True black background so OLED handhelds actually save power
                // and the art is the only colour on screen.
                background = Color(0xFF000000),
                surface = Color(0xFF141414),
                surfaceRaised = Color(0xFF232323),
                accent = Color(0xFFFFFFFF),
                accentGlow = Color(0x59FFFFFF),
                secondary = Color(0xFFBDBDBD),
                textPrimary = Color(0xFFFFFFFF),
                textMuted = Color(0xFF979797)
            )
            else -> VOLT.base
        }
}

val LocalShelfColors = compositionLocalOf { AppTheme.EMBER.colors }

/**
 * (QA pass 2, D22) The same colours as one State that never changes identity: read `.value` while DRAWING
 * (drawBehind, graphicsLayer, a draw lambda) and the colour glide only redraws, instead of re-composing every reader
 * ~100 times a second while the colours follow the game. Not overridden by nested LocalShelfColors providers — use it
 * only where the screen's own theme colours are meant.
 */
val LocalShelfColorsLive = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.State<ShelfColors>> {
    androidx.compose.runtime.mutableStateOf(AppTheme.EMBER.colors)
}

/** Shorthand so screens can write ShelfTheme.accent instead of reaching for
 * the CompositionLocal every time. */
object ShelfTheme {
    val colors: ShelfColors
        @Composable @ReadOnlyComposable get() = LocalShelfColors.current

    val accent: Color
        @Composable @ReadOnlyComposable get() = colors.accent

    /** The theme colours as a State, for reading while drawing (see [LocalShelfColorsLive]). */
    val live: androidx.compose.runtime.State<ShelfColors>
        @Composable @ReadOnlyComposable get() = LocalShelfColorsLive.current

    val accentGlow: Color
        @Composable @ReadOnlyComposable get() = colors.accentGlow

    val background: Color
        @Composable @ReadOnlyComposable get() = colors.background

    val secondary: Color
        @Composable @ReadOnlyComposable get() = colors.secondary

    val textMuted: Color
        @Composable @ReadOnlyComposable get() = colors.textMuted
}
