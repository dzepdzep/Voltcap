package com.shelf.launcher.data

import android.content.Context

/** What sits behind the dock. */
enum class BackgroundStyle(val label: String) {
    ART("Game art"),
    BLURRED("Blurred art"),
    BLACK("Black"),
    WALLPAPER("Your wallpaper"),
    /** The theme's own animated scene (sparks, bubbles, aurora…), drawn in code. */
    ANIMATED("Animated theme")
}

/** How the game's background (hero) art sits on the screen. */
enum class HeroFit(val label: String) {
    /** Fills the whole screen, top to bottom; the sides of very wide art are cut off. */
    FILL("Fill the screen"),
    /** The whole picture, full width at the top; a soft blurred copy fills the rest. */
    WHOLE("Whole picture")
}

/**
 * Update 125: the background picture's fit for each layout (its options in Settings). A layout you haven't set uses
 * Background picture's choice; with the carousel look it fills the screen unless you set it.
 */
/**
 * Update 125: where the game names go, for each layout on its own (its options in Settings): on the art, next to the
 * row (above / under it) or none. A layout you haven't set follows Game names in Settings.
 */
object NamePlan {
    private fun parse(raw: String): Map<String, Pair<TitlePlacement, RowSide>> = raw.split(';').mapNotNull { e ->
        val i = e.indexOf('='); if (i <= 0) return@mapNotNull null
        val parts = e.substring(i + 1).split(',')
        val p = TitlePlacement.values().firstOrNull { it.name == parts.getOrNull(0) } ?: return@mapNotNull null
        val side = RowSide.values().firstOrNull { it.name == parts.getOrNull(1) } ?: RowSide.AUTO
        e.substring(0, i) to (p to side)
    }.toMap()

    fun of(layout: HomeLayout, placement: TitlePlacement, side: RowSide): Pair<TitlePlacement, RowSide> =
        parse(Features.namesByLayout.value)[layout.name] ?: (placement to side)

    fun put(layout: HomeLayout, placement: TitlePlacement, side: RowSide) {
        val m = parse(Features.namesByLayout.value).toMutableMap()
        m[layout.name] = placement to side
        Features.namesByLayout.value = m.entries.joinToString(";") { "${it.key}=${it.value.first.name},${it.value.second.name}" }
    }

    /** 0.6.17: whether [layout] has a name place of its own (set in its Layout options before they moved). */
    fun has(layout: HomeLayout): Boolean = parse(Features.namesByLayout.value).containsKey(layout.name)

    /** 0.6.17: [layout] follows Settings › Home › Name & logo again. */
    fun clear(layout: HomeLayout) {
        val m = parse(Features.namesByLayout.value).toMutableMap()
        if (m.remove(layout.name) == null) return
        Features.namesByLayout.value = m.entries.joinToString(";") { "${it.key}=${it.value.first.name},${it.value.second.name}" }
    }
}

object HeroFitPlan {
    private fun parse(raw: String): Map<String, String> = raw.split(';').mapNotNull { e ->
        val i = e.indexOf('='); if (i <= 0) null else e.substring(0, i) to e.substring(i + 1)
    }.toMap()

    /** The fit set for [layout] in its options, or null. */
    fun set(layout: HomeLayout): HeroFit? =
        parse(Features.heroFitByLayout.value)[layout.name]?.let { n -> HeroFit.values().firstOrNull { it.name == n } }

    /**
     * Update 129: a layout you haven't set fills the screen — every layout but the Voltcap row, and the Voltcap row too
     * when its games are drawn as the real things (cases, cartridges, discs, spines), [look]; with covers and wide
     * capsules it keeps Background picture's choice.
     */
    fun of(layout: HomeLayout, global: HeroFit, look: CardArtType? = null): HeroFit {
        set(layout)?.let { return it }
        val carousel = layout.name in Features.carouselLayouts.value
        if (carousel || layout != HomeLayout.CLASSIC) return HeroFit.FILL
        val plain = look == null || ((look == CardArtType.COVER || look == CardArtType.CAPSULE) && !Features.spineLook.value)
        return if (plain) global else HeroFit.FILL
    }

    fun put(layout: HomeLayout, fit: HeroFit) {
        val m = parse(Features.heroFitByLayout.value).toMutableMap()
        m[layout.name] = fit.name
        Features.heroFitByLayout.value = m.entries.joinToString(";") { "${it.key}=${it.value}" }
    }

    /** 0.6.17: the same fit for every layout (Theme › Background › Background picture › Use it in every layout). */
    fun putAll(fit: HeroFit) {
        Features.heroFitByLayout.value = HomeLayout.values().joinToString(";") { "${it.name}=${fit.name}" }
    }
}

const val TILE_SCALE_MIN = 80
const val TILE_SCALE_MAX = 170

/** The whole home screen's layout. */
enum class HomeLayout(val label: String) {
    /** Covers in a row; the game you're on unfolds into a wide capsule. */
    CLASSIC("Voltcap row"),
    /** Square tiles in one row, name above the one you're on, round shortcut buttons under it. */
    TILES("Tile home"),
    /** The game's hero full screen, a curved column of covers on the left, ◀ ▶ pages of info. */
    HERO("Hero carousel"),
    /** The category as a grid filling the screen, in the card artwork you picked (GridHome). */
    GRID("Grid"),
    /** The game playing on an old TV, the games beside it as cases or spines, the story under it (TvRoomHome). */
    TV("TV room"),
    /** Glass panels: a round-button menu on the left, the game you're on big in a "Now playing" card, the row under it,
     *  quick buttons, a player card and system status on the right (StyleDashboard.kt). */
    DASH("Dashboard"),
    /** Flat live tiles: category tabs on top, one big tile and four small ones, coloured shortcut tiles under them (StyleLiveTiles.kt). */
    LIVE("Live tiles"),
    /** A dark menu down the left with an emblem and a glowing orb, the game's art behind, the row along the bottom (StyleSideMenu.kt). */
    SIDE("Side menu"),
    /** Categories as icons across the screen, the games as a list down from the one you're on (StyleCrossMenu.kt). */
    CROSS("Cross menu"),
    /** The game's art full screen, small square tiles on top, its big title, Play and activity cards (StyleSpotlight.kt). */
    SPOT("Spotlight"),
    /** Shortcut icons along the top, a row of square tiles with the one you're on big, its name and Start under it (StyleIconBar.kt). */
    BAR("Icon bar"),
    /** The category as a bookcase: spines on shelves, the game you're on pulled out to show its front (ShelfHome.kt). */
    SHELF("Game shelf"),
    /** The game's art full screen, profile / clock on top, a row of tiles with the game you're on big, three wide cards under it (StyleConsoleHomes.kt). */
    HUB("Console hub"),
    /** The game you're on as a big square with a 2x2 block of shortcut tiles, the next games in a row, "Discover" cards (StyleConsoleHomes.kt). */
    RESUME("Resume & discover"),
    /** Pages of wide tiles on a pale backdrop, a curved bar with the clock and a round button at each side (StyleConsoleHomes.kt). */
    CHAN("Channel grid"),
    /** Voltcap's own (update 106): the games as chips zig-zagging along a glowing power line on a circuit board (StyleCircuit.kt). */
    CIRCUIT("Circuit board"),
    /** Update 146: a panel on the left with the game you're on (cover, name, play time, achievements, Details / Play),
     *  a grid of the category's games on the right, category tabs along the top (StyleLibrary.kt). */
    LIBRARY("Library panel"),
    /** Update 146: the game you're on as a big case in the middle, the others as stacks of spines left and right, a button
     *  bar along the bottom (StyleCaseStacks.kt). */
    STACKS("Case stacks"),
    /** Update 146: the games along a line by when you last played them (or release year / when added), dates under them
     *  (StyleTimeline.kt). */
    TIMELINE("Timeline"),
    /** Update 146: the game you're on shown on a drawn arcade cabinet's screen with its marquee on top, the others as a
     *  row of small cabinets (StyleArcade.kt). */
    ARCADE("Arcade cabinets"),
    /** Update 146: your consoles as a pile of drawn consoles; the one you pick slides out its games (StyleConsoleStack.kt). */
    CONSOLES("Console stack"),
    /** Update 147: one disc player — the game you're on as the disc in the tray (ejected and loaded as you move), its
     *  details beside it (StyleDiscStation.kt). Always discs. */
    DISCS("Disc station"),
    /** Update 158: the game's big picture across the top, one or two rows of square tiles under it, an app dock and
     *  round buttons along the bottom (StyleHeroTiles.kt). */
    HERO_TILES("Hero tiles"),
    /** Update 158: two screens — a desk with a clock and calendar on top, the games as wide panels on the second screen
     *  (TwinDesk.kt; both halves stacked on a one-screen handheld). */
    TWIN_DESK("Twin desk"),
    /** Update 158: two screens — the game you're on as a big framed icon on top, a grid of square icons on the second
     *  screen (TwinIcons.kt; both halves stacked on a one-screen handheld). */
    TWIN_ICONS("Twin icons"),
    /** Update 307: two screens — the game you're on as its big picture on top (moving out and in with every move), square
     *  icons in 1–3 rows running on to the right on the second screen (TwinShowcase.kt). */
    TWIN_SHOWCASE("Twin showcase"),
    /** Update 158: your games as record sleeves in a wooden crate; the one you're on lifts out, its record slides out
     *  to the right and turns (StyleRecordCrate.kt). */
    CRATE("Record crate"),
    /** Update 158: your games as collectible cards — cover in a framed window, console as its type, stats at the
     *  bottom; the one you're on big in the middle, the others fanned out like a hand; Y turns it over (StyleCardBinder.kt). */
    CARDS("Card binder"),
    /** Update 176: a layout made by anyone — imported from a .vclayout file (CustomLayouts.kt, StyleCustom.kt). */
    // (Update 290, Toni: renamed from "Custom layout" — the layout creator will grow into it.)
    CUSTOM("Layout Studio"),
    /** Update 181: Voltcap's take on the 2004 two-screen pocket menu: clock and calendar on top, four panels below (PocketClassic.kt). */
    POCKET_CLASSIC("Pocket classic"),
    /** Update 181: Voltcap's take on the 2008 two-screen pocket menu: a picture on top, a ribbon of icons below (PocketRibbon.kt). */
    POCKET_RIBBON("Pocket ribbon"),
    /** Update 202: Green Core — a dark green machine dashboard: a living sphere of light on the left, angled menu panels
     *  on the right (GAMES, SYSTEMS, APPS, SAVE VAULT, MUSIC, SETTINGS), its own games and systems pages (StyleGreenCore.kt). */
    GREENCORE("Green Core (2001)"),
    /** Update 202: Pocket Quest — a full-screen game menu in the style of the great 32-bit handheld RPGs: a big showcase
     *  of the game you're on, layered blue panels with gold edges, a row of games along the bottom, pixel-art scenery
     *  behind (StylePocketQuest.kt, PocketQuestScenery.kt, PocketQuestSettings.kt). */
    POCKET("Pocket Quest"),
    /** (Update 239, #93: a cube home — a big translucent cube whose faces are your categories (▲ Apps, ▼ Settings);
     *  A opens a face and the cube docks to the side while the games fill the rest of the screen (StyleCube.kt).) */
    CUBE("Cube menu");

    /** One of the style homes drawn by StyleHomes.kt (full-screen layouts with their own menus). */
    val styleHome: Boolean get() = this == DASH || this == LIVE || this == SIDE || this == CROSS || this == SPOT || this == BAR || this == SHELF || this == HUB || this == RESUME || this == CHAN || this == CIRCUIT ||
        this == LIBRARY || this == STACKS || this == TIMELINE || this == ARCADE || this == CONSOLES || this == DISCS ||
        this == HERO_TILES || // Update 158
        this == TWIN_DESK || this == TWIN_ICONS || this == TWIN_SHOWCASE || // Update 158: the two-screen layouts (307: Twin showcase)
        this == CRATE || // Update 158: Record crate
        this == CARDS || // Update 158: Card binder
        this == CUSTOM || // Update 176: Custom layout
        this == GREENCORE || // Update 202: Green Core
        this == POCKET || // Update 202: Pocket Quest
        this == CUBE || // (Update 239, #93: Cube menu)
        this == POCKET_CLASSIC || this == POCKET_RIBBON // Update 181

    companion object {
        /**
         * The layouts you can pick. (Update 112 took Tile home out and moved a saved one to the Voltcap row with the
         * square tile look.)
         */
        // (Update 205: Arcade cabinets removed — no longer offered; a home still set to it opens as the Voltcap row.)
        // (Update 235, #15/#16: Tile home is a layout again — and the Square tile look stays a look of its own.)
        val pickable: List<HomeLayout> get() = values().filter { it != ARCADE }
    }
}

/** How the background art changes when you move, and what it does while you rest on a game. */
enum class HeroMotion(val label: String) {
    CROSSFADE("Classic crossfade"),
    QUICK("Quick fade"),
    QUICK_ZOOM("Quick fade + slow zoom"),
    /** The new art slides in a little from the side as it fades in. */
    SLIDE("Slide"),
    /** The new art pushes in from the side, the old one sliding away more slowly (depth). */
    PARALLAX("Parallax push"),
    /** The new art comes forward out of a slight zoom while the old one zooms past. */
    ZOOM_THROUGH("Zoom through"),
    /** The new art opens from the middle in a growing circle. */
    IRIS("Portal"),
    /** Quick fade, then a slow zoom and pan across the picture while you rest on it. */
    KEN_BURNS("Ken Burns"),
    /** The new art swings in on a 3D turn while the old one turns away (like flicking through covers). */
    COVER_FLOW("Cover Flow"),
    /** The new art appears in blocks, scattered over the screen (a pixel dissolve). */
    PIXEL("Pixel dissolve"),
    /** Like an old TV changing channel: a bright line opens up into the new picture. */
    CRT("CRT switch"),
    /** The new art slides up into place like a memory card going in; the old one lifts away. */
    MEMORY_CARD("Memory card")
}

/** How much visual polish runs continuously (Settings › Appearance › Performance). */
enum class PerformanceMode(val label: String) {
    /** Lite on slower handhelds (Retroid Pocket Classic…), full effects elsewhere. */
    AUTO("Automatic"),
    FULL("Full effects"),
    /** No continuously animated glows, cheap blur, no hero upscaling: smoother on slow devices, lighter on the battery. */
    LITE("Lite")
}

/**
 * How hard the animated background works (Appearance › Behind the dock › Background quality).
 * [scale] = share of the screen resolution the scene is drawn at (then smoothly stretched — these
 * scenes are soft glows and gradients, so it hardly shows); [frameMs] = time between frames.
 */
enum class SceneQuality(val label: String, val scale: Float, val frameMs: Int) {
    /** Low on slower handhelds (Retroid Pocket Classic, Konkr…), Full elsewhere. */
    AUTO("Automatic", 1f, 28),
    /** Full resolution at ~60 frames a second: the smoothest, for strong handhelds (Automatic picks it on them; update 126: on 60 Hz screens too). */
    SMOOTH("Full · 60 fps", 1f, 14),
    HIGH("Full · 30 fps", 1f, 28),
    BALANCED("Balanced", 0.6f, 36),
    LOW("Low", 0.4f, 55),
    /** Weakest devices: a quarter of the pixels, ~12 frames a second. Still smooth-looking for slow scenes. */
    LOWEST("Lowest", 0.28f, 80)
}

/** How much of the start-up animation plays. */
enum class IntroStyle(val label: String) {
    FULL("Full"),
    SHORT("Short"),
    /** The 32-bit intro: a pixel capsule, Volt waking up inside it (VoltcapIntro.drawPixelIntro). */
    PIXEL("32-bit"),
    OFF("Off")
}

/** Where the row of games sits on the screen. */
enum class DockPosition(val label: String) {
    TOP("Top"),
    MIDDLE("Middle"),
    BOTTOM("Bottom")
}

/**
 * What is printed on the face buttons, for the button legend and the controls card: Xbox letters
 * (A bottom), Retro letters (A right) or PlayStation shapes (✕ bottom, like Xbox's A). Which
 * button sends which key comes from [ButtonLayoutMode]; together they give the right label.
 */
enum class ButtonGlyphs(val label: String) {
    XBOX("Xbox letters (A bottom)"),
    NINTENDO("Retro letters (A right)"),
    PLAYSTATION("PlayStation (✕ ○ □ △)")
}

/** The system's face-button layout (the handheld's Xbox / Retro switch): detected, or set by hand. */
enum class ButtonLayoutMode(val label: String) {
    AUTO("Follow the system"),
    XBOX("Xbox"),
    NINTENDO("Retro")
}

/** Where the game you're on sits in the row. */
enum class FocusPosition(val label: String) {
    LEFT("Left end"),
    CENTER("Middle")
}

/** Tile home: where the selected tile sits in the row. */
enum class TileFocus(val label: String) {
    EDGES("Moves to the edges"),
    LEFT("Left"),
    CENTER("Middle"),
    RIGHT("Right")
}

/** What the game you're on looks like. */
enum class FocusedCardStyle(val label: String) {
    WIDE("Wide capsule"),
    /** Only the first game of the row unfolds into a wide capsule; the others stay covers. Needs the
     * game you're on at the left end (with it in the middle this acts like [WIDE]). */
    FIRST_ONLY("Wide for the first game only"),
    COVER("Cover")
}

/** How big the cards in the row are, as a share of the screen's height
 * (Medium ≈ the Steam Deck home screen: covers about a third of the height). */
enum class CardSize(val label: String, val heightFraction: Float) {
    SMALL("Small", 0.24f),
    MEDIUM("Medium", 0.31f),
    LARGE("Large", 0.37f)
}

/** Corner rounding of game art — covers, wide capsules, the details window. */
enum class CardCorners(val label: String, val radiusDp: Int) {
    SQUARE("Square", 3),
    ROUNDED("Rounded", 10),
    ROUND("Extra round", 20),
    CAPSULE("Capsule", 28)
}

/** How the games you're not on look. */
enum class OtherGamesLook(val label: String) {
    NORMAL("Normal"),
    DIMMED("Dimmed"),
    GREY("Black & white")
}

/** Which artwork the library cards show (Settings › Appearance › Game row). */
enum class CardArtType(val label: String) {
    COVER("Cover"),
    CAPSULE("Capsule (wide)"),
    CASE("Box art"),
    /** Box art with every case at its real size next to the others (a DS case smaller than a PS2 one). */
    CASE_REAL("Box art · real sizes"),
    CARTRIDGE_DISC("Cartridge / disc"),
    /** Disc games as their disc; everything else keeps its cover. */
    DISC("Discs only"),
    HERO("Hero"),
    SCREENSHOT("Screenshot")
}

/** What the game's details window (A) shows. */
enum class DetailsSection(val label: String) {
    INFO("Year, developer, genres"),
    SCORES("Scores"),
    PLAYTIME("Your playtime"),
    ACHIEVEMENTS("Achievement progress"),
    SUMMARY("Summary"),
    TIME_TO_BEAT("How long to beat"),
    MEDIA("Video & screenshots"),
    PHYSICAL("Box & disc art"),
    ACHIEVEMENT_LIST("Achievements still to get")
}

/** The category row above the games. */
/**
 * Update 204: the layouts that don't show the category's name themselves (the Voltcap row, Tile home and Grid have the
 * category bar; Timeline, Pocket Quest, Library panel, Spotlight, Cross menu, Side menu… draw it in their design). They
 * get the floating Category name part by default, once (AppearanceStore.categoryPart204).
 */
val CATEGORY_PART_LAYOUTS: List<HomeLayout> = listOf(
    HomeLayout.TV, HomeLayout.STACKS, HomeLayout.CARDS, HomeLayout.HUB, HomeLayout.RESUME, HomeLayout.HERO_TILES,
    HomeLayout.TWIN_ICONS, HomeLayout.HERO
)

enum class CategoryBar(val label: String) {
    FULL("All categories"),
    CURRENT("Just the current one"),
    OFF("Off")
}

/** A corner of a card, for the trophy count. */
enum class CardCorner(val label: String) {
    TOP_RIGHT("Top right"),
    TOP_LEFT("Top left"),
    BOTTOM_LEFT("Bottom left"),
    BOTTOM_RIGHT("Bottom right")
}

/** What the game popup (A) shows behind its contents. */
enum class DetailsBackdrop(val label: String) {
    /** Colours drawn from the cover, draped softly over the Voltcap panel (no picture, no blur). Default. */
    COLORS("Colours from the art"),
    /** Old default. Kept so saved settings still load; it now draws the same colour drape as [COLORS]. */
    BLURRED_HERO("Blurred background art"),
    HERO("Background art"),
    SCREENSHOTS("Screenshots, changing"),
    VIDEO("Video clip (muted)"),
    COVER("Blurred cover"),
    PLAIN("Plain colour")
}

/** Where the corner music player sits. */
enum class MusicPlayerPosition(val label: String) {
    TOP_LEFT("Top left"),
    TOP_RIGHT("Top right"),
    BOTTOM_LEFT("Bottom left"),
    BOTTOM_RIGHT("Bottom right")
}

/** What a tile on the category screen shows. */
enum class CategoryTileArt(val label: String) {
    /** The art of every game in it, one after the other, slowly cross-fading. */
    SLIDESHOW("Slow fade through its games"),
    /** The art of the game played most recently in it (else its first game). */
    LAST_PLAYED("Last played game"),
    COVERS("A few covers"),
    /** A photo of the console (consoles; the others fall back to the slideshow). */
    CONSOLE("Photo of the console"),
    PLAIN("Just the name")
}

/** How big the tiles on the category screen are. */
enum class CategoryTileStyle(val label: String, val columns: Int) {
    /** Rounded square tiles like the Tiles home: the focused one grows with a ring, the name centred under it.
     *  (Update 125: first, and the default — the Tiles look showed wide cards.) */
    TILES("Tiles", 6),
    LARGE("Wide cards", 4),
    COMPACT("Small cards", 7)
}

/** Where the game's logo sits next to its name. */
enum class LogoPlacement(val label: String) {
    LEFT("Left of the name"),
    RIGHT("Right of the name"),
    ABOVE("Above the name"),
    BELOW("Under the name"),
    INSTEAD("Instead of the name")
}

/** The screen with every category as a tile. */
enum class CategoryScreenMode(val label: String) {
    OFF("Off"),
    ON_B("Open it with B"),
    AT_START("At start, and with B")
}

/** Which set of built-in interface sounds plays. */
enum class SoundStyle(val label: String) {
    /** Soft, rounded, airy — the calm feel of a modern console menu. */
    CONSOLE("Console"),
    ELECTRIC("Electric"),
    CLASSIC("Soft chimes"),
    /** Even softer, with a big airy room: a calm home screen (res/raw/ui_chill_*). */
    CHILL("Chill"),
    /** Late-90s console blips, 'sampled' and crunchy, a short metallic room (res/raw/ui_retro32_*). */
    RETRO32("32-bit"),
    /** Harp, crystal and a distant bell (res/raw/ui_dark_*). */
    DARK("Dark fantasy"),
    /** Update 181: tiny square-wave chirps, like a pocket handheld's menu (made from scratch; the Pocket layouts use them). */
    POCKET("Pocket chirps")
}

/** Where a game's name shows on its card. */
enum class TitlePlacement(val label: String) {
    HIDDEN("No name"),
    INSIDE("On the art"),
    ABOVE("Next to the row"),
    /** Old setting — same as [ABOVE]; which side is its own setting now. */
    BELOW("Next to the row")
}

/** Stored as the lights colour when the lights follow the theme's accent colour. */
const val LIGHTS_THEME_COLOR: Int = 0x00FEFEFE

/** The theme accent the lights use while "Theme colour" is picked (kept current by the home screen). */
object LightsTheme {
    @Volatile var accent: Int = 0xFFFF6A00.toInt()
    fun resolve(color: Int): Int = if (color == LIGHTS_THEME_COLOR) accent else color
}

/** How the round shortcut button you're on is marked. */
enum class ShortcutHighlight(val label: String) {
    /** A ring in the accent colour around the button (it grows a little). */
    RING("Ring"),
    /** The button fills with its own colour, the icon turns white, a soft glow round it. */
    FILL("Filled"),
    /** The button lifts up, with a glow under it and a dot below. */
    LIFT("Lift & dot")
}

/** Where the round shortcut buttons go: by the tile row, a small ☰ button in a corner, or anywhere (any home style). */
enum class ShortcutSpot(val label: String) {
    ROW("By the row"),
    TOP_LEFT("Top left corner"),
    TOP_RIGHT("Top right corner"),
    BOTTOM_LEFT("Bottom left corner"),
    BOTTOM_RIGHT("Bottom right corner"),
    /** A free-floating bar, anywhere on the screen (moved in Edit Home: drag it, or the D-pad), in any home style. */
    FREE("Anywhere (move in Edit Home)")
}

/** What the light sweeping over mastered / completed games looks like. */
enum class ShimmerStyle(val label: String) {
    GOLD("Flashing gold"),
    WHITE("Soft white"),
    THEME("Theme colour")
}

/** Order of games in the dock. */
enum class SortMode(val label: String, val short: String = label) {
    NAME("Name (A–Z)", "Name A–Z"),
    NAME_DESC("Name (Z–A)", "Name Z–A"),
    RECENT("Last played", "Last played"),
    PLAYTIME("Most played", "Most played"),
    NEWLY_ADDED("Recently added", "Recently added"),
    FAVORITES_FIRST("Favorites first, then A–Z", "Favorites first"),
    PROGRESS("Achievement progress", "Achievements"),
    MASTERED("Mastered first", "Mastered"),
    METACRITIC("Metacritic score", "Metascore"),
    CRITIC("Critic score", "Critic"),
    NEWEST("Release date (newest)", "Newest release"),
    OLDEST("Release date (oldest)", "Oldest release"),
    SHORTEST("Shortest to beat", "Shortest to beat"),
    PLATFORM("Console, then name", "By console")
}

/** How a mastered / completed / beaten game is marked on its card. */
enum class MasteredStyle(val label: String, val blurb: String) {
    BADGE("Badge with text", "\"Mastered\" pill on the selected card, crown on covers."),
    ICON("Small icon", "Just the crown, next to the game's name."),
    BORDER("Golden border", "A gold (or silver) frame around the art — no badge."),
    NONE("Nothing", "No mark for mastered games at all.")
}

/** An extra effect on the game you're on. */
enum class FocusEffect(val label: String) {
    NONE("None"),
    GLOW("Soft glow"),
    ELECTRIC("Electric")
}

/** Space between the category bar, the game's name and the row. */
enum class InfoSpacing(val label: String, val dp: Int) {
    TIGHT("Tight", 0),
    NORMAL("Normal", 6),
    ROOMY("Roomy", 14)
}

/** Above or under the row of games. Automatic: above, but under when the games sit at the top. */
/** What sits behind the focused game's name so it reads on any background. */
enum class NameBackdrop(val label: String) {
    NONE("Nothing"),
    /** The same look as the game cards: their colour, corners and frame. */
    CARD("Like the cards"),
    DARK("Dark pill"),
    THEME("Theme colour"),
    GLASS("Frosted glass"),
    BAR("Accent bar"),
    SHADOW("Soft shadow"),
    /** Part of the art: letters pressed into the picture (light edge above, dark below), no box. */
    EMBOSS("Pressed into the art"),
    /** Part of the art: the letters glow in the accent colour, the light spilling into the picture. */
    GLOW("Glowing in the art"),
    /** Magazine style: no box, a thin accent rule under the name fading into the art. */
    RULE("Editorial rule"),
    /** A dark wash from the screen edge that dissolves into the art behind the name (no pill). */
    EDGE_FADE("Fade from the edge"),
    /** Hollow letters: just their outline, with a dark halo — no box. */
    OUTLINE("Outlined letters"),
    /** The letters glow like a neon tube in the accent colour (a bright core, a wide soft halo). */
    NEON("Neon sign"),
    /** A slanted accent tag behind the name, like a sports broadcast caption. */
    TAG("Slanted tag")
}

/** How the category name comes in when L2 / R2 switches category (Appearance › Categories). */
enum class CategoryAnim(val label: String) {
    NONE("Just swaps"),
    FADE("Fades"),
    SLIDE("Slides in from the side"),
    DROP("Drops in from above")
}

/** What shows next to the row: under it when the games sit at the top or
 * in the middle, above it (over the background art) when they're at the bottom. */
/** What the second screen shows above its dock while you're in Voltcap. */
enum class SecondPage(val label: String) {
    /** Update 122: every console as a folder in the theme's colour; a tap opens its games as a grid (SecondGamesPage.kt).
     *  (The name is from the old Games page, removed in update 42, so a choice saved back then still reads.) */
    LIBRARY("Games"),
    ART("Art"),
    VIDEO("Video"),
    DETAILS("Details"),
    ACHIEVEMENTS("Achievements"),
    MUSIC("Music"),
    /** Filled by the walkthrough feature; hidden until that is available. */
    WALKTHROUGH("Guide"),
    /** Filled by the performance feature; hidden until that is available. */
    PERFORMANCE("Performance"),
    /** Widget Board: Now Playing, System Stats, Screen Mirror and widgets from other apps (Features.secondWidgets). */
    WIDGETS("Widgets"),
    /** The Wall: slanted slices of the platforms' (or the category's games') art (Features.secondWall). */
    WALL("Wall"),
    /** The main screen's picture enlarged on the second screen (Features.secondMagnifier). */
    MAGNIFIER("Magnifier");

    /** Whether the page is switched on (Settings › Appearance › Second screen tools). */
    fun available(): Boolean = when (this) {
        WIDGETS -> Features.secondWidgets.value
        WALL -> Features.secondWall.value
        MAGNIFIER -> Features.secondMagnifier.value
        else -> true
    }
}

/** Quiet music behind the launcher. */
enum class BackgroundMusicMode(val label: String) {
    OFF("Off"),
    THEME("Theme music"),
    CUSTOM("My own music")
}

/** Which picture the second screen's Art page shows. */
enum class SecondArtType(val label: String) {
    HERO("Hero"),
    BACKGROUND("Background (wide)"),
    COVER("Cover"),
    SCREENSHOTS("My screenshots"),
    SLIDESHOW("Slideshow of all"),
    CASE("Box / case"),
    DISC("Cartridge / disc"),
    /** The real cartridge / disc (or box) beside the game's name, facts and story, over its blurred art. */
    SHOWCASE("Cartridge + details")
}

/** How the second screen's Music page looks. */
enum class SecondMusicLayout(val label: String) {
    /**
     * Update 200: the user's Wallpaper Engine lyrics dashboard rebuilt for the second screen (ui/LyricsStage.kt): the
     * cover blurred behind everything, a clock, the cover with a circle visualizer, synced lyrics, effects and stats.
     * First in the list because it is the default now.
     */
    STAGE("Lyrics stage"),
    FULL("Full screen"),
    /** Full screen over a music-reactive scene (the one used on the home screen, or Bloom). */
    VISUALIZER("Visualizer"),
    CLASSIC("Classic card")
}

/** "cat=THEME;cat2=THEME2" ⇄ map, for [AppearanceSettings.categoryScenes]. */
object CategoryScenes {
    fun parse(s: String): Map<String, String> = s.split(';').mapNotNull { part ->
        val i = part.indexOf('=')
        if (i <= 0) null else part.substring(0, i) to part.substring(i + 1)
    }.toMap()

    fun format(m: Map<String, String>): String =
        m.entries.filter { it.key.isNotBlank() && it.value.isNotBlank() && ';' !in it.key && '=' !in it.key }
            .joinToString(";") { "${it.key}=${it.value}" }
}

enum class RowInfo(val label: String) {
    OFF("Nothing"),
    GAME("Game info"),
    NEWS("News"),
    BOTH("Game info + news")
}

enum class RowSide(val label: String) {
    AUTO("Automatic"),
    ABOVE("Above the row"),
    BELOW("Under the row");

    fun below(dock: DockPosition): Boolean = when (this) {
        AUTO -> dock == DockPosition.TOP
        ABOVE -> false
        BELOW -> true
    }
}

/** Where the song's lyrics show. */
enum class LyricsPosition(val label: String) {
    WITH_PLAYER("Under the player"),
    TOP("Top of the screen"),
    BOTTOM("Bottom of the screen"),
    /** Top or bottom, whichever part of the cover is calmer — never over the cover's subject. */
    AWAY("Away from the cover's subject")
}

/** Scales every size in the app — cards, text, popups — together. */
enum class UiDensity(val label: String, val scale: Float) {
    COMPACT("Compact", 0.88f),
    DEFAULT("Default", 1f),
    LARGE("Large", 1.12f)
}

/**
 * Everything about how Shelf looks and sounds that isn't the base theme.
 * Colours are stored as ARGB ints; null means "use the theme's own".
 */
data class AppearanceSettings(
    /** Accent colour follows the focused game's cover art. */
    val dynamicAccent: Boolean = false,
    /** The whole app (background, surfaces, accent, second colour) follows the focused game's cover. */
    val dynamicTheme: Boolean = false,
    /** GIF / animated WebP / APNG covers and heroes play (AnimatedArt). */
    val animatedArt: Boolean = true,
    /** Scraping asks SteamGridDB for animated covers and heroes first. */
    val preferAnimatedArt: Boolean = false,
    val customAccent: Int? = null,
    val customBackground: Int? = null,
    val backgroundStyle: BackgroundStyle = BackgroundStyle.ART,
    val wallpaperUri: String? = null,
    val density: UiDensity = UiDensity.DEFAULT,
    /** Text size on its own (85-140 %), on top of [density]: bigger text without bigger cards. */
    val textScale: Int = 100,
    val soundsEnabled: Boolean = true,
    /** 0-100. */
    val soundVolume: Int = 50,
    val haptics: Boolean = true,
    /** 0-100: how hard the haptic ticks are. */
    val hapticStrength: Int = 60,
    /** Play a short clip behind the dock after resting on a game (ScreenScraper). */
    val videoPreviews: Boolean = true,
    val videoSound: Boolean = false,
    /** Put popups on a second display when there is one (AYN Thor etc.). */
    val useSecondScreen: Boolean = true,
    /** Small music player in the top-left corner of the launcher. */
    val miniPlayer: Boolean = false,
    /** Current song line under the mini player. */
    val miniPlayerLyrics: Boolean = false,
    val titlePlacement: TitlePlacement = TitlePlacement.ABOVE,
    /** Leave (USA), (Europe), (En,Fr,De)… out of game names. */
    val hideRegionTags: Boolean = true,
    /** Achievement count pill on the small cover art. */
    val coverAchievements: Boolean = true,
    /** Mark covers of games with a recent unlock ("NEW" trophy). */
    val coverRecentUnlocks: Boolean = false,
    /** Shelf sets the controller / stick RGB lights (when the device allows it). */
    val lightsEnabled: Boolean = false,
    val lightsColor: Int = 0xFFFF6A00.toInt(),
    /** 0-100. */
    val lightsBrightness: Int = 70,
    /** Lights go dark when the screensaver fades to black. */
    val lightsOffWithScreensaver: Boolean = true,
    val masteredStyle: MasteredStyle = MasteredStyle.BADGE,
    /** Buttons that control the music player from the home screen. */
    val musicButtons: Map<MusicAction, MusicButton> = DEFAULT_MUSIC_BUTTONS,
    val sortMode: SortMode = SortMode.NAME,
    /** Blink the controller lights when an achievement unlocks. */
    val flashOnUnlock: Boolean = true,
    /** The short Voltcap animation when the app starts. */
    val introStyle: IntroStyle = IntroStyle.FULL,
    /** A light that runs around the golden border of mastered games. */
    val masteredShimmer: Boolean = true,
    val shimmerStyle: ShimmerStyle = ShimmerStyle.GOLD,
    /** Show what you're playing on Discord (Rich Presence). */
    val discordPresence: Boolean = true,
    /** Blow small background (hero) images up crisply instead of smearing them. */
    val sharpHeroes: Boolean = true,
    /** The game's logo next to its name on the focused banner / details window. */
    val showGameLogos: Boolean = true,
    /** "Add missing…" in the details window when a video, score, art… is missing. */
    val offerMissing: Boolean = true,
    val heroFit: HeroFit = HeroFit.FILL,
    /** The console's name under the game's name on the focused wide card. */
    val showConsoleName: Boolean = false,
    val dockPosition: DockPosition = DockPosition.BOTTOM,
    val focusPosition: FocusPosition = FocusPosition.LEFT,
    val focusedCardStyle: FocusedCardStyle = FocusedCardStyle.WIDE,
    val cardSize: CardSize = CardSize.MEDIUM,
    val cardCorners: CardCorners = CardCorners.ROUNDED,
    val otherGamesLook: OtherGamesLook = OtherGamesLook.NORMAL,
    /** Which artwork the library cards show; missing types fall back to cover → capsule → hero. */
    val cardArtType: CardArtType = CardArtType.COVER,
    /** Media kinds left out with "Cartridge / disc" ("cartridge", "disc"): those games show their cover instead. One for every layout. */
    val hiddenMedia: Set<String> = emptySet(),
    /** Every other home layout's own placement (card size, spacing, tilt, tile size), swapped in when you switch layout. */
    val lookByLayout: Map<HomeLayout, LayoutLook> = emptyMap(),
    /** The button legend along the bottom (R3 shows / hides it). */
    val showLegend: Boolean = false,
    /** Names / shapes of the face buttons in the legend (Xbox, Retro or PlayStation). */
    val buttonGlyphs: ButtonGlyphs = ButtonGlyphs.XBOX,
    /** The system's button layout: followed automatically (default) or forced Xbox / Retro. */
    val buttonLayout: ButtonLayoutMode = ButtonLayoutMode.AUTO,
    /** What the details window shows (Appearance › Details window). */
    val detailsSections: Set<DetailsSection> = DetailsSection.values().toSet(),
    /** Time played for the game you're on, under the row on the home screen. */
    val playtimeOnHome: Boolean = true,
    val soundStyle: SoundStyle = SoundStyle.CONSOLE,
    /** The console-menu, 32-bit and dark-fantasy music loops also bring their navigation sounds. */
    val themeSounds: Boolean = true,
    /** Category Voltcap opens on: "LAST" (where you left off), "ALL", or a
     * special category's name (RECENT, FAVORITES…). */
    val startCategory: String = "RECENT",
    val categoryBar: CategoryBar = CategoryBar.FULL,
    /** Where the trophy count sits on covers and the wide capsule. */
    val trophyCorner: CardCorner = CardCorner.TOP_RIGHT,
    /** The trophy count on the wide capsule of the game you're on. */
    val trophyOnBanner: Boolean = true,
    /** The achievement progress bar on the wide capsule. */
    val progressOnBanner: Boolean = true,
    /** The accent-coloured frame around the game you're on. */
    val focusBorder: Boolean = true,
    val detailsBackdrop: DetailsBackdrop = DetailsBackdrop.COLORS,
    val logoPlacement: LogoPlacement = LogoPlacement.LEFT,
    /** When the row runs out of games, start again from the first. */
    val loopRow: Boolean = true,
    val categoryScreen: CategoryScreenMode = CategoryScreenMode.ON_B,
    /** Full console names ("PlayStation 2") instead of short ones ("PS2"). */
    val fullConsoleNames: Boolean = true,
    val hotkeys: List<Hotkey> = DEFAULT_HOTKEYS,
    /** The category bar: above the row or under it. */
    /** Default: the categories sit above the game names. */
    val categorySide: RowSide = RowSide.ABOVE,
    /** The focused game's name: above the row or under it. */
    val nameSide: RowSide = RowSide.AUTO,
    /** The other games lean away from the one you're on. */
    val tiltOthers: Boolean = false,
    /** The focused name sits on a dark pill, readable on bright backgrounds.
     * Old setting, kept so it carries over — [nameBackdrop] is what's used. */
    val nameCapsule: Boolean = false,
    val nameBackdrop: NameBackdrop = NameBackdrop.NONE,
    /** How the category name changes (L2 / R2). */
    val categoryAnim: CategoryAnim = CategoryAnim.FADE,
    /** Console logos (RetroAchievements' system icons) next to the category names. */
    val categoryLogos: Boolean = true,
    /** Game info / news next to the row. */
    val rowInfo: RowInfo = RowInfo.OFF,
    /** Which news feeds (NewsFeed.SOURCES ids). */
    val newsSources: Set<String> = DEFAULT_NEWS_SOURCES,
    /** Hide everything in brackets in game names — "(QOL Improvements)", "(Rev 1)", "(Hack)"… */
    val hideAllBrackets: Boolean = false,
    /** A notification for each achievement unlocked while playing (shows over the emulator). */
    val unlockPopups: Boolean = true,
    /** The game's RetroAchievements status ("50 Pokémon caught") on Discord too. */
    val discordRichPresence: Boolean = true,
    /** "Unlocked by 4 % of players" on achievements, and the easiest one to go for next. */
    val showRarity: Boolean = true,
    /** Tips in the details window: the official guide and comments from the game's page. */
    val showTips: Boolean = true,
    /** Achievements as a wall of square "gym badges" (name on two lines + unlock %) instead of a list. */
    val achievementBadges: Boolean = true,
    /** Badges fly in from the sides and lock into place when the list opens. */
    val achievementFlyIn: Boolean = true,
    /** The retro "fly" and "star pop" sounds with that animation (follows the UI sounds' volume). */
    val achievementFlySounds: Boolean = true,
    /** Your own collections as categories. */
    val userCollections: Boolean = true,
    /** Franchises found automatically ("Final Fantasy", "Pokémon") as categories. Off until switched on. */
    val franchiseCollections: Boolean = false,
    /** Genres from the scraped details ("RPG", "Platform") as categories. Off until switched on. */
    val genreCollections: Boolean = false,
    /** Second screen: the app dock along its bottom. */
    val secondDock: Boolean = true,
    /** The dock's apps (package names), up to five; "" = empty slot. */
    val secondDockApps: List<String> = emptyList(),
    /** Dock apps open on the second screen (true) or the main one. */
    val secondDockOnSecond: Boolean = true,
    /** Second screen: what shows above the dock. */
    val secondPage: SecondPage = SecondPage.ART,
    /** Second screen: only the current page's name at the top, with subtle arrows to the others (not every tab). */
    val secondPageTitleOnly: Boolean = true,
    /** A on a game with the second screen up: its details window opens there as a "Game" page (else on the main screen). */
    val secondDetails: Boolean = true,
    /** Second screen › Visualizer music layout: the album cover card over the visualizer too (off: just the visualizer). */
    val secondVisualizerArt: Boolean = false,
    /** With a second screen up: the music-reactive scene and the corner visualizers move there (main keeps a still frame). */
    val musicFxSecond: Boolean = true,
    /** Second screen › Details: the case first (tap to open it) or the cartridge / disc first. */
    val secondDetailsCase: Boolean = true,
    /** Second screen › Art page: which picture of the game it shows. */
    val secondArtType: SecondArtType = SecondArtType.HERO,
    /** Second screen: blur of the artwork behind everything, 0–100 %. */
    val secondArtBlur: Int = 50,
    /** Second screen: also show the small music player over the artwork. Off = the player only lives on the Music page. */
    val secondPlayerOverArt: Boolean = false,
    /** Second screen › Music page: full screen player or the old card. */
    // Update 200: the Lyrics stage is the default now.
    val secondMusicLayout: SecondMusicLayout = SecondMusicLayout.STAGE,
    /** Second screen and screensaver: blurred album art as the background while music plays. */
    val secondMusicBlurArt: Boolean = true,
    /** How strong that blur is, 0–100 %. */
    val secondMusicBlur: Int = 3,
    /** Lyrics in the full second-screen player. */
    val secondMusicLyrics: Boolean = false,
    /** Background music: off, the theme's own loop, or your own file. */
    val musicMode: BackgroundMusicMode = BackgroundMusicMode.OFF,
    /** Its own volume, 0–100 %, separate from the interface sounds. */
    val musicVolume: Int = 25,
    /** Built-in track: "" follows the theme, otherwise a [com.shelf.launcher.ui.theme.MusicLoop] name (any loop with any look). */
    val musicTrack: String = "",
    /** File name of your own track, and when it was picked (to reload it). */
    val musicCustomName: String = "",
    val musicCustomStamp: Long = 0L,
    /** Playing / Finished / Dropped / Wishlist per game, as categories too. */
    val playStatus: Boolean = true,
    /** Your emulator screenshots in the details window. */
    val showScreenshots: Boolean = true,
    /** Extra folders to look for screenshots in (full paths). */
    val screenshotFolders: Set<String> = emptySet(),
    /** Sunday-evening notification with the week's play. */
    val weeklyRecap: Boolean = true,
    /** By the game's name: RetroAchievements' icon instead of a logo (update 113: on by default). */
    val raLogos: Boolean = true,
    val musicPlayerPosition: MusicPlayerPosition = MusicPlayerPosition.TOP_LEFT,
    val categoryTileArt: CategoryTileArt = CategoryTileArt.CONSOLE,
    val categoryTileStyle: CategoryTileStyle = CategoryTileStyle.TILES,
    /** The achievement progress bar sits above the title on the wide capsule. */
    val progressAbove: Boolean = false,
    val focusEffect: FocusEffect = FocusEffect.ELECTRIC,
    val infoSpacing: InfoSpacing = InfoSpacing.TIGHT,
    val lyricsPosition: LyricsPosition = LyricsPosition.WITH_PLAYER,
    /** Mastered frame in the theme's accent colour instead of gold / silver. */
    val masteredThemeColor: Boolean = false,
    /** Master switch: every achievement mark on the cards (pills, bars, NEW). */
    val achievementsOnCards: Boolean = true,
    /** Clock, battery, Wi-Fi… along the top. */
    val showStatusBar: Boolean = true,
    /** The star on covers of favourites. */
    val favoriteStar: Boolean = true,
    /** Play / favourite / more buttons on the wide capsule. */
    val bannerButtons: Boolean = true,
    /** "Played 2d ago" on the wide capsule. */
    val lastPlayedOnBanner: Boolean = true,
    /** The "Ignition" animation when a game starts (LaunchAnimation). */
    val launchAnimation: Boolean = true,
    /** A new game in the library arrives with a little show on the home screen. */
    val arrivalAnim: Boolean = true,
    /** The details window grows out of the game you're on (instead of just fading in). */
    val popupZoom: Boolean = true,
    /** Voltcap's own row, or square Tile-style tiles (wide capsules are off then). */
    val homeLayout: HomeLayout = HomeLayout.CLASSIC,
    /** Tile home: the round shortcut buttons under the tiles. */
    val tileShortcuts: Boolean = true,
    /** Round shortcut buttons in the theme colour instead of their own colours. */
    /** Round buttons in the theme colour (default, one consistent look) or each in its own colour. */
    val tileShortcutTheme: Boolean = true,
    val tileFocus: TileFocus = TileFocus.EDGES,
    /** Tile home: tile size, % of the Card size default (larger for tiny screens). Name, buttons and pills follow. */
    val tileScale: Int = 100,
    /** One copy per game when several regions / revisions are in the library. */
    val hideDuplicates: Boolean = false,
    /** Starting the game you just left brings its still-running emulator back instead of rebooting it. */
    val quickResume: Boolean = true,
    /** Preferred regions, best first (comma separated). */
    val regionOrder: String = "USA,Europe,World,Japan",
    val shortcutSpot: ShortcutSpot = ShortcutSpot.ROW,
    /** How the focused round shortcut is highlighted. */
    val shortcutHighlight: ShortcutHighlight = ShortcutHighlight.RING,
    /** Background art: fades in quickly once loaded, then slowly zooms while you rest on a game. */
    val heroMotion: HeroMotion = HeroMotion.QUICK_ZOOM,
    /** How far the slow zoom goes, 5–25 %. */
    val heroZoomPercent: Int = 15,
    val performanceMode: PerformanceMode = PerformanceMode.AUTO,
    /** Keeps the animated background moving even when Lite effects are on (Lite otherwise shows a still frame). */
    val animateOnLite: Boolean = false,
    /** Resolution and frame rate of the animated background (see [SceneQuality]). */
    val sceneQuality: SceneQuality = SceneQuality.AUTO,
    /** Slow the background and hold the card effects still after a while without input (see HomeIdle). */
    val idleSaver: Boolean = true,
    /** The animated background's colours follow the time of day (dawn, day, dusk, night). */
    val sceneTimeOfDay: Boolean = false,
    /** The animated background takes its colours from the cover of the game you're on. */
    val sceneFromCover: Boolean = false,
    /** A scene per category: "categoryKey=THEME_NAME" pairs joined by ';' (empty = the same everywhere). */
    val categoryScenes: String = "",
    /** Cross-fade between scenes when the theme or the category changes. */
    val sceneFade: Boolean = true,
    /** Dual screens: the animated scene plays only on the second screen; the main screen holds a still frame. */
    val sceneSecondOnly: Boolean = false,
    /** Scene editor: speed (25-200 %), extra darkening (0-60 %), colour shift (-180..180°), particles (0-200 %). */
    val sceneSpeed: Int = 100,
    val sceneDim: Int = 0,
    val sceneHue: Int = 0,
    val sceneParticles: Int = 100,
    /** The controller / stick lights pulse with the music while it plays. */
    val lightsMusic: Boolean = false,
    /** Name of the AppTheme whose animated scene and colours are used behind the dock; null = follow the current theme. */
    val sceneTheme: String? = null,
    /** Name of the AppTheme whose animated scene plays on the second screen (with the main screen's colours); null = the same as the main screen. */
    val secondSceneTheme: String? = null
) {
    /** Kept for older callers: any intro at all. Follows [introStyle]. */
    val introAnimation: Boolean get() = introStyle != IntroStyle.OFF
}

class AppearanceStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    /** Once: franchise categories became opt-in (they were on for everyone
     * before). Only the start categories — Recent, All, the consoles — are on
     * by default now; everything else is switched on by hand. */
    private fun applyDefaultsV2() {
        if (prefs.getBoolean("defaults_v2", false)) return
        prefs.edit().putBoolean("franchise_collections", false).putBoolean("defaults_v2", true).apply()
    }

    /**
     * Update 58: album-art blur on every music player is 3 % by default (it was 60 %). Once: a stored 60 (the old
     * default, saved again by every settings change) becomes 3; any other value was picked by hand and stays.
     */
    private fun applyDefaultsV3() {
        if (prefs.getBoolean("defaults_v3", false)) return
        val e = prefs.edit().putBoolean("defaults_v3", true)
        if (prefs.getInt("second_music_blur", 60) == 60) e.putInt("second_music_blur", 3)
        e.apply()
    }

    /** Update 98: real scraped cartridges / discs by default; our drawn shells only when chosen (asked once, so a choice made afterwards stays). */
    private fun applyDefaultsV4() {
        if (prefs.getBoolean("defaults_v4", false)) return
        // Only marked done once the setting really was written (Features must be started: else it's tried again next time).
        runCatching { Features.init(appContext); Features.mediaShells.value = false; prefs.edit().putBoolean("defaults_v4", true).apply() }
    }

    /**
     * Update 151: video previews on for everyone, once (the scraped clip plays over the game's big background art). A
     * new install has them on already; turning them off afterwards stays off.
     */
    private fun applyVideoOn151() {
        if (prefs.getBoolean("video_on_151", false)) return
        prefs.edit().putBoolean("video_previews", true).putBoolean("video_on_151", true).apply()
    }

    /**
     * Update 200: the second screen's Music page switches to the new Lyrics stage once for everyone (asked for). The
     * flag is written in the same step, so picking another layout afterwards sticks.
     */
    private fun applyStage200() {
        if (prefs.getBoolean("stage_default_200", false)) return
        prefs.edit().putString("second_music_layout", SecondMusicLayout.STAGE.name).putBoolean("stage_default_200", true).apply()
    }

    /**
     * Update 202, once: the game look became four main looks with a few choices each (ui/GameLookPicker.kt). The look you
     * had is read off its switches and written as those choices, so nothing you picked is lost; cartridges start on each
     * console's real shell (asked for: the researched real backs). Only marked done once Features could be started (as
     * [applyDefaultsV4]), else it's tried again next time.
     */
    private fun lookRework202(s: AppearanceSettings): AppearanceSettings {
        if (prefs.getBoolean("look_rework_202", false)) return s
        return runCatching {
            Features.init(appContext)
            com.shelf.launcher.ui.migrateGameLook202(s.cardArtType)
            prefs.edit().putBoolean("look_rework_202", true).apply()
            s
        }.getOrDefault(s)
    }

    /**
     * Update 204, once: every layout shows the category's name by default (asked for). The layouts that don't draw it
     * themselves get the Category name part (Edit Home › + Add) added, at a free spot (LauncherScreen,
     * categoryPartSpot); its ✕ in Edit Home, or Layout options › On the screen, takes it off again for good. Only marked
     * done once Features could be started (as [lookRework202]).
     */
    private fun categoryPart204() {
        if (prefs.getBoolean("category_part_204", false)) return
        runCatching {
            Features.init(appContext)
            for (l in CATEGORY_PART_LAYOUTS) {
                if (com.shelf.launcher.ui.HomeWidgets.CATEGORY !in Features.overlayPartsFor(l)) {
                    Features.toggleOverlayPart(l, com.shelf.launcher.ui.HomeWidgets.CATEGORY)
                }
            }
            prefs.edit().putBoolean("category_part_204", true).apply()
        }
    }

    /**
     * Update 208, once: cases stand on their own (Case only) — the disc half out of every disc game's case had come on
     * with a preset or the Mix look, and was asked to be a choice, off by default. Settings › Game look › Cases ›
     * With its media puts it back (Case + media beside it / Case + disc half out). Only marked done once Features could
     * be started.
     */
    private fun caseOnly208() {
        if (prefs.getBoolean("case_only_208", false)) return
        runCatching {
            Features.init(appContext)
            Features.discCase.value = false
            Features.caseWithMedia.value = false
            val mem = Features.lookMemory.value
            if ("caseMedia=" in mem) Features.lookMemory.value = mem.replace(Regex("caseMedia=[A-Z]+"), "caseMedia=INSIDE")
            prefs.edit().putBoolean("case_only_208", true).apply()
        }
    }

    fun load(): AppearanceSettings = applyDefaultsV2().let { applyDefaultsV3(); applyDefaultsV4(); applyVideoOn151(); applyStage200(); categoryPart204(); caseOnly208(); lookRework202(oneGameLook(loadSettings())) }.also {
        // Scraping in the background reads these too, without the home screen open.
        AnimatedArt.enabled = it.animatedArt
        AnimatedArt.preferAnimatedScraping = it.animatedArt && it.preferAnimatedArt
        AchievementLook.apply(it)
        ConsoleLogos.enabled.value = it.categoryLogos
        com.shelf.launcher.ui.TitleStrip.allBrackets.value = it.hideAllBrackets
    }

    /**
     * Update 82: the game look (covers, cases, cartridges / discs…) is one choice for every home layout. Once, the
     * layout you were on hands its own card artwork and its shown media to all of them; the old per-layout keys go.
     */
    private fun oneGameLook(s: AppearanceSettings): AppearanceSettings {
        if (prefs.getBoolean("one_game_look", false)) return s
        fun forLayout(key: String): String? = (prefs.getString(key, null) ?: "").split(';')
            .firstOrNull { it.substringBefore(':') == s.homeLayout.name }?.substringAfter(':')
        val art = forLayout("card_art_by_layout")?.let { a -> CardArtType.values().firstOrNull { it.name == a } } ?: s.cardArtType
        val hidden = forLayout("hidden_media_by_layout")?.split('|')?.filter { it == "cartridge" || it == "disc" }?.toSet() ?: s.hiddenMedia
        prefs.edit()
            .putString("card_art_type", art.name)
            .putString("hidden_media", hidden.joinToString("|"))
            .remove("card_art_by_layout").remove("hidden_media_by_layout")
            .putBoolean("one_game_look", true)
            .apply()
        return s.copy(cardArtType = art, hiddenMedia = hidden)
    }

    private fun loadSettings(): AppearanceSettings = AppearanceSettings(
        dynamicAccent = prefs.getBoolean("dynamic_accent", false),
        dynamicTheme = prefs.getBoolean("dynamic_theme", false),
        // (Update 148: on by default.)
        animatedArt = prefs.getBoolean("animated_art", true),
        preferAnimatedArt = prefs.getBoolean("prefer_animated_art", false),
        customAccent = if (prefs.contains("custom_accent")) prefs.getInt("custom_accent", 0) else null,
        customBackground = if (prefs.contains("custom_background")) prefs.getInt("custom_background", 0) else null,
        backgroundStyle = runCatching { BackgroundStyle.valueOf(prefs.getString("background_style", null) ?: "") }
            .getOrDefault(BackgroundStyle.ART),
        wallpaperUri = prefs.getString("wallpaper_uri", null),
        density = runCatching { UiDensity.valueOf(prefs.getString("density", null) ?: "") }
            .getOrDefault(UiDensity.DEFAULT),
        textScale = prefs.getInt("text_scale", 100).coerceIn(85, 140),
        soundsEnabled = prefs.getBoolean("sounds", true),
        soundVolume = prefs.getInt("sound_volume", 50),
        haptics = prefs.getBoolean("haptics", true),
        hapticStrength = prefs.getInt("haptic_strength", 60),
        videoPreviews = prefs.getBoolean("video_previews", true),
        videoSound = prefs.getBoolean("video_sound", false),
        useSecondScreen = prefs.getBoolean("second_screen", true),
        miniPlayer = prefs.getBoolean("mini_player", false),
        miniPlayerLyrics = prefs.getBoolean("mini_player_lyrics", false),
        // Update 125: the name next to the row (above the capsule) by default; a new key, so the old default "on the art"
        // that was saved with everything else gives way (a name you had switched off stays off).
        titlePlacement = runCatching { TitlePlacement.valueOf(prefs.getString("title_placement2", null) ?: "") }
            .getOrElse { if (prefs.getString("title_placement", null) == "HIDDEN") TitlePlacement.HIDDEN else TitlePlacement.ABOVE },
        hideRegionTags = prefs.getBoolean("hide_region_tags", true),
        coverAchievements = prefs.getBoolean("cover_achievements", true),
        coverRecentUnlocks = prefs.getBoolean("cover_recent_unlocks", false),
        lightsEnabled = prefs.getBoolean("lights_enabled", false),
        lightsColor = prefs.getInt("lights_color", 0xFFFF6A00.toInt()),
        lightsBrightness = prefs.getInt("lights_brightness", 70),
        lightsOffWithScreensaver = prefs.getBoolean("lights_off_screensaver", true),
        masteredStyle = runCatching { MasteredStyle.valueOf(prefs.getString("mastered_style", null) ?: "") }
            .getOrDefault(MasteredStyle.BADGE),
        musicButtons = decodeMusicButtons(prefs.getString("music_buttons", null)),
        flashOnUnlock = prefs.getBoolean("flash_on_unlock", true),
        // Older installs only have the on/off switch: on becomes Full, off stays off.
        introStyle = runCatching { IntroStyle.valueOf(prefs.getString("intro_style", null) ?: "") }
            .getOrDefault(if (prefs.getBoolean("intro_animation", true)) IntroStyle.FULL else IntroStyle.OFF),
        masteredShimmer = prefs.getBoolean("mastered_shimmer", true),
        shimmerStyle = enumPref("shimmer_style", ShimmerStyle.GOLD),
        discordPresence = prefs.getBoolean("discord_presence", true),
        sharpHeroes = prefs.getBoolean("sharp_heroes", true),
        showGameLogos = prefs.getBoolean("show_game_logos_v2", true),
        offerMissing = prefs.getBoolean("offer_missing", true),
        showConsoleName = prefs.getBoolean("show_console_name", false),
        heroFit = runCatching { HeroFit.valueOf(prefs.getString("hero_fit_v2", null) ?: "") }.getOrDefault(HeroFit.FILL),
        dockPosition = enumPref("dock_position", DockPosition.BOTTOM),
        focusPosition = enumPref("focus_position", FocusPosition.LEFT),
        focusedCardStyle = enumPref("focused_card_style", FocusedCardStyle.WIDE),
        cardSize = enumPref("card_size", CardSize.MEDIUM),
        cardCorners = enumPref("card_corners", CardCorners.ROUNDED),
        otherGamesLook = enumPref("other_games_look", OtherGamesLook.NORMAL),
        cardArtType = enumPref("card_art_type", CardArtType.COVER),
        lookByLayout = LayoutLook.parse(prefs.getString("look_by_layout", null)),
        hiddenMedia = (prefs.getString("hidden_media", null) ?: "").split('|').filter { it == "cartridge" || it == "disc" }.toSet(),
        showLegend = prefs.getBoolean("show_legend", false),
        buttonGlyphs = enumPref("button_glyphs", ButtonGlyphs.XBOX),
        buttonLayout = enumPref("button_layout", ButtonLayoutMode.AUTO),
        playtimeOnHome = prefs.getBoolean("playtime_on_home", true),
        soundStyle = enumPref("sound_style_v2", SoundStyle.CONSOLE),
        themeSounds = prefs.getBoolean("theme_sounds", true),
        startCategory = prefs.getString("start_category", null) ?: "RECENT",
        categoryBar = enumPref("category_bar", CategoryBar.FULL),
        trophyCorner = enumPref("trophy_corner", CardCorner.TOP_RIGHT),
        trophyOnBanner = prefs.getBoolean("trophy_on_banner", true),
        progressOnBanner = prefs.getBoolean("progress_on_banner", true),
        focusBorder = prefs.getBoolean("focus_border", true),
        detailsBackdrop = enumPref("details_backdrop", DetailsBackdrop.COLORS)
            .let { if (it == DetailsBackdrop.BLURRED_HERO) DetailsBackdrop.COLORS else it },
        logoPlacement = enumPref("logo_placement", LogoPlacement.LEFT),
        // "Endless" (R1 tile): on by default. A new key, so an old saved "off" from before it was forced on is ignored.
        loopRow = prefs.getBoolean("loop_row3", true), // (Update 182: on again for everyone once — the sides of a carousel with few games stood empty)
        categoryScreen = enumPref("category_screen", CategoryScreenMode.ON_B),
        fullConsoleNames = prefs.getBoolean("full_console_names", true),
        hotkeys = decodeHotkeys(prefs.getString("hotkeys_v2", null)),
        categorySide = enumPref(
            "category_side",
            if (prefs.getBoolean("info_below_row", false)) RowSide.BELOW else RowSide.ABOVE
        ),
        nameSide = enumPref(
            "name_side",
            if (prefs.getBoolean("info_below_row", false) || prefs.getString("title_placement", null) == "BELOW") RowSide.BELOW
            else RowSide.AUTO
        ),
        tiltOthers = prefs.getBoolean("tilt_others", false),
        nameCapsule = prefs.getBoolean("name_capsule", false),
        unlockPopups = prefs.getBoolean("unlock_popups", true),
        discordRichPresence = prefs.getBoolean("discord_rich_presence", true),
        showRarity = prefs.getBoolean("show_rarity", true),
        showTips = prefs.getBoolean("show_tips", true),
        achievementBadges = prefs.getBoolean("achievement_badges", true),
        achievementFlyIn = prefs.getBoolean("achievement_fly_in", true),
        achievementFlySounds = prefs.getBoolean("achievement_fly_sounds", true),
        userCollections = prefs.getBoolean("user_collections", true),
        franchiseCollections = prefs.getBoolean("franchise_collections", false),
        genreCollections = prefs.getBoolean("genre_collections", false),
        secondDock = prefs.getBoolean("second_dock", true),
        secondDockApps = prefs.getString("second_dock_apps", null)?.split(',')?.map { it.trim() }?.take(8) ?: emptyList(),
        secondDockOnSecond = prefs.getBoolean("second_dock_on_second", true),
        secondPage = enumPref("second_page", SecondPage.ART),
        // New key (update 42): the arrows are on for everyone once (they weren't easy to find); switch off in Settings.
        secondPageTitleOnly = prefs.getBoolean("second_page_arrows", true),
        secondDetails = prefs.getBoolean("second_details", true),
        secondVisualizerArt = prefs.getBoolean("second_visualizer_art", false),
        musicFxSecond = prefs.getBoolean("music_fx_second", true),
        secondDetailsCase = prefs.getBoolean("second_details_case", true),
        secondArtType = enumPref("second_art_type", SecondArtType.HERO),
        secondArtBlur = prefs.getInt("second_art_blur", 50).coerceIn(0, 100),
        secondPlayerOverArt = prefs.getBoolean("second_player_over_art", false),
        secondMusicLayout = enumPref("second_music_layout", SecondMusicLayout.STAGE),
        secondMusicBlurArt = prefs.getBoolean("second_music_blur_art", true),
        secondMusicBlur = prefs.getInt("second_music_blur", 3).coerceIn(0, 100),
        secondMusicLyrics = prefs.getBoolean("second_music_lyrics", false),
        musicMode = enumPref("music_mode", BackgroundMusicMode.OFF),
        musicVolume = prefs.getInt("music_volume", 25).coerceIn(0, 100),
        musicTrack = prefs.getString("music_track", "").orEmpty(),
        musicCustomName = prefs.getString("music_custom_name", "") ?: "",
        musicCustomStamp = prefs.getLong("music_custom_stamp", 0L),
        playStatus = prefs.getBoolean("play_status", true),
        showScreenshots = prefs.getBoolean("show_screenshots", true),
        screenshotFolders = prefs.getStringSet("screenshot_folders", null)?.toSet() ?: emptySet(),
        weeklyRecap = prefs.getBoolean("weekly_recap", true),
        rowInfo = enumPref("row_info", RowInfo.OFF),
        newsSources = prefs.getString("news_sources", null)
            ?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: DEFAULT_NEWS_SOURCES,
        nameBackdrop = enumPref(
            "name_backdrop",
            if (prefs.getBoolean("name_capsule", false)) NameBackdrop.DARK else NameBackdrop.NONE
        ),
        categoryAnim = enumPref("category_anim", CategoryAnim.FADE),
        categoryLogos = prefs.getBoolean("category_logos", true),
        hideAllBrackets = prefs.getBoolean("hide_all_brackets", false),
        raLogos = prefs.getBoolean("ra_logos", true),
        musicPlayerPosition = enumPref("music_player_position", MusicPlayerPosition.TOP_LEFT),
        categoryTileArt = enumPref("category_tile_art", CategoryTileArt.CONSOLE),
        // Update 125: a new key, so the Tiles look really starts with tiles (the old saved "wide" came from the old default).
        categoryTileStyle = enumPref("category_tile_style2", CategoryTileStyle.TILES),
        progressAbove = prefs.getBoolean("progress_above", false),
        focusEffect = enumPref("focus_effect", FocusEffect.ELECTRIC),
        infoSpacing = enumPref("info_spacing", InfoSpacing.TIGHT),
        lyricsPosition = enumPref("lyrics_position", LyricsPosition.WITH_PLAYER),
        masteredThemeColor = prefs.getBoolean("mastered_theme_color", false),
        achievementsOnCards = prefs.getBoolean("achievements_on_cards", true),
        showStatusBar = prefs.getBoolean("show_status_bar", true),
        favoriteStar = prefs.getBoolean("favorite_star", true),
        bannerButtons = prefs.getBoolean("banner_buttons", true),
        lastPlayedOnBanner = prefs.getBoolean("last_played_on_banner", true),
        launchAnimation = prefs.getBoolean("launch_animation", true),
        arrivalAnim = prefs.getBoolean("arrival_anim", true),
        popupZoom = prefs.getBoolean("popup_zoom", true),
        homeLayout = prefs.getString("home_layout", null).let { if (it == "SWITCH") HomeLayout.TILES else enumPref("home_layout", HomeLayout.CLASSIC) }.let { if (it == HomeLayout.ARCADE) HomeLayout.CLASSIC else it },
        tileShortcuts = prefs.getBoolean("tile_shortcuts", true),
        // New key: everyone starts on the consistent theme colour once (design clean-up); their own colours stay a choice.
        tileShortcutTheme = prefs.getBoolean("tile_shortcut_theme2", true),
        tileFocus = enumPref("tile_focus", TileFocus.EDGES),
        tileScale = prefs.getInt("tile_scale", 100).coerceIn(TILE_SCALE_MIN, TILE_SCALE_MAX),
        hideDuplicates = prefs.getBoolean("hide_duplicates", false),
        quickResume = prefs.getBoolean("quick_resume", true),
        regionOrder = prefs.getString("region_order", null) ?: "USA,Europe,World,Japan",
        shortcutSpot = enumPref("shortcut_spot", ShortcutSpot.ROW),
        shortcutHighlight = enumPref("shortcut_highlight", ShortcutHighlight.RING),
        heroMotion = enumPref("hero_motion", HeroMotion.QUICK_ZOOM),
        heroZoomPercent = prefs.getInt("hero_zoom_percent", 15).coerceIn(5, 25),
        performanceMode = enumPref("performance_mode", PerformanceMode.AUTO),
        animateOnLite = prefs.getBoolean("animate_on_lite", false),
        sceneQuality = enumPref("scene_quality", SceneQuality.AUTO),
        idleSaver = prefs.getBoolean("idle_saver", true),
        sceneTimeOfDay = prefs.getBoolean("scene_time_of_day", false),
        sceneFromCover = prefs.getBoolean("scene_from_cover", false),
        categoryScenes = prefs.getString("category_scenes", "").orEmpty(),
        sceneFade = prefs.getBoolean("scene_fade", true),
        sceneSecondOnly = prefs.getBoolean("scene_second_only", false),
        sceneSpeed = prefs.getInt("scene_speed", 100).coerceIn(25, 200),
        sceneDim = prefs.getInt("scene_dim", 0).coerceIn(0, 60),
        sceneHue = prefs.getInt("scene_hue", 0).coerceIn(-180, 180),
        sceneParticles = prefs.getInt("scene_particles", 100).coerceIn(0, 200),
        lightsMusic = prefs.getBoolean("lights_music", false),
        sceneTheme = prefs.getString("scene_theme", null),
        secondSceneTheme = prefs.getString("second_scene_theme", null),
        detailsSections = prefs.getString("details_sections", null)
            ?.split(',')
            ?.mapNotNull { name -> runCatching { DetailsSection.valueOf(name) }.getOrNull() }
            ?.toSet()
            // Box & disc art is new: switched on once for existing setups.
            ?.let { if (prefs.getBoolean("details_physical_seen", false)) it else it + DetailsSection.PHYSICAL }
            ?: DetailsSection.values().toSet(),
        sortMode = runCatching { SortMode.valueOf(prefs.getString("sort_mode", null) ?: "") }.getOrDefault(SortMode.NAME)
    )

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T =
        runCatching { enumValueOf<T>(prefs.getString(key, null) ?: "") }.getOrDefault(default)

    fun save(s: AppearanceSettings) {
        AchievementLook.apply(s)
        ConsoleLogos.enabled.value = s.categoryLogos
        com.shelf.launcher.ui.TitleStrip.allBrackets.value = s.hideAllBrackets
        prefs.edit().apply {
            putBoolean("dynamic_accent", s.dynamicAccent)
            putBoolean("dynamic_theme", s.dynamicTheme)
            putBoolean("animated_art", s.animatedArt)
            putBoolean("prefer_animated_art", s.preferAnimatedArt)
            if (s.customAccent != null) putInt("custom_accent", s.customAccent) else remove("custom_accent")
            if (s.customBackground != null) putInt("custom_background", s.customBackground) else remove("custom_background")
            putString("background_style", s.backgroundStyle.name)
            putString("wallpaper_uri", s.wallpaperUri)
            putString("density", s.density.name)
            putInt("text_scale", s.textScale)
            putBoolean("sounds", s.soundsEnabled)
            putInt("sound_volume", s.soundVolume)
            putBoolean("haptics", s.haptics)
            putInt("haptic_strength", s.hapticStrength)
            putBoolean("video_previews", s.videoPreviews)
            putBoolean("video_sound", s.videoSound)
            putBoolean("second_screen", s.useSecondScreen)
            putBoolean("mini_player", s.miniPlayer)
            putBoolean("mini_player_lyrics", s.miniPlayerLyrics)
            putString("title_placement2", s.titlePlacement.name)
            putBoolean("hide_region_tags", s.hideRegionTags)
            putBoolean("cover_achievements", s.coverAchievements)
            putBoolean("cover_recent_unlocks", s.coverRecentUnlocks)
            putBoolean("lights_enabled", s.lightsEnabled)
            putInt("lights_color", s.lightsColor)
            putInt("lights_brightness", s.lightsBrightness)
            putBoolean("lights_off_screensaver", s.lightsOffWithScreensaver)
            putString("mastered_style", s.masteredStyle.name)
            putString("music_buttons", encodeMusicButtons(s.musicButtons))
            putString("sort_mode", s.sortMode.name)
            putBoolean("flash_on_unlock", s.flashOnUnlock)
            putString("intro_style", s.introStyle.name)
            putBoolean("intro_animation", s.introAnimation)
            putBoolean("mastered_shimmer", s.masteredShimmer)
            putString("shimmer_style", s.shimmerStyle.name)
            putBoolean("discord_presence", s.discordPresence)
            putBoolean("sharp_heroes", s.sharpHeroes)
            putBoolean("show_game_logos_v2", s.showGameLogos)
            putBoolean("offer_missing", s.offerMissing)
            putString("hero_fit_v2", s.heroFit.name)
            putBoolean("show_console_name", s.showConsoleName)
            putString("dock_position", s.dockPosition.name)
            putString("focus_position", s.focusPosition.name)
            putString("focused_card_style", s.focusedCardStyle.name)
            putString("card_size", s.cardSize.name)
            putString("card_corners", s.cardCorners.name)
            putString("other_games_look", s.otherGamesLook.name)
            putString("card_art_type", s.cardArtType.name)
            putString("look_by_layout", LayoutLook.serialize(s.lookByLayout))
            putString("hidden_media", s.hiddenMedia.joinToString("|"))
            putBoolean("show_legend", s.showLegend)
            putString("button_glyphs", s.buttonGlyphs.name)
            putString("button_layout", s.buttonLayout.name)
            putBoolean("playtime_on_home", s.playtimeOnHome)
            putString("sound_style_v2", s.soundStyle.name)
            putBoolean("theme_sounds", s.themeSounds)
            putString("start_category", s.startCategory)
            putString("category_bar", s.categoryBar.name)
            putString("trophy_corner", s.trophyCorner.name)
            putBoolean("trophy_on_banner", s.trophyOnBanner)
            putBoolean("progress_on_banner", s.progressOnBanner)
            putBoolean("focus_border", s.focusBorder)
            putString("details_backdrop", s.detailsBackdrop.name)
            putString("logo_placement", s.logoPlacement.name)
            putBoolean("loop_row3", s.loopRow)
            putString("category_screen", s.categoryScreen.name)
            putBoolean("full_console_names", s.fullConsoleNames)
            putString("hotkeys_v2", encodeHotkeys(s.hotkeys))
            putString("category_side", s.categorySide.name)
            putString("name_side", s.nameSide.name)
            putBoolean("tilt_others", s.tiltOthers)
            putBoolean("name_capsule", s.nameCapsule)
            putBoolean("unlock_popups", s.unlockPopups)
            putBoolean("discord_rich_presence", s.discordRichPresence)
            putBoolean("show_rarity", s.showRarity)
            putBoolean("show_tips", s.showTips)
            putBoolean("achievement_badges", s.achievementBadges)
            putBoolean("achievement_fly_in", s.achievementFlyIn)
            putBoolean("achievement_fly_sounds", s.achievementFlySounds)
            putBoolean("user_collections", s.userCollections)
            putBoolean("franchise_collections", s.franchiseCollections)
            putBoolean("genre_collections", s.genreCollections)
            putBoolean("second_dock", s.secondDock)
            putString("second_dock_apps", s.secondDockApps.take(8).joinToString(","))
            putBoolean("second_dock_on_second", s.secondDockOnSecond)
            putString("second_page", s.secondPage.name)
            putBoolean("second_page_arrows", s.secondPageTitleOnly)
            putBoolean("second_details", s.secondDetails)
            putBoolean("second_visualizer_art", s.secondVisualizerArt)
            putBoolean("music_fx_second", s.musicFxSecond)
            putBoolean("second_details_case", s.secondDetailsCase)
            putString("second_art_type", s.secondArtType.name)
            putInt("second_art_blur", s.secondArtBlur.coerceIn(0, 100))
            putBoolean("second_player_over_art", s.secondPlayerOverArt)
            putString("second_music_layout", s.secondMusicLayout.name)
            putBoolean("second_music_blur_art", s.secondMusicBlurArt)
            putInt("second_music_blur", s.secondMusicBlur.coerceIn(0, 100))
            putBoolean("second_music_lyrics", s.secondMusicLyrics)
            putString("music_mode", s.musicMode.name)
            putInt("music_volume", s.musicVolume.coerceIn(0, 100))
            putString("music_track", s.musicTrack)
            putString("music_custom_name", s.musicCustomName)
            putLong("music_custom_stamp", s.musicCustomStamp)
            putBoolean("play_status", s.playStatus)
            putBoolean("show_screenshots", s.showScreenshots)
            putStringSet("screenshot_folders", s.screenshotFolders)
            putBoolean("weekly_recap", s.weeklyRecap)
            putString("name_backdrop", s.nameBackdrop.name)
            putString("category_anim", s.categoryAnim.name)
            putBoolean("category_logos", s.categoryLogos)
            putString("row_info", s.rowInfo.name)
            putString("news_sources", s.newsSources.joinToString(","))
            putBoolean("hide_all_brackets", s.hideAllBrackets)
            putBoolean("ra_logos", s.raLogos)
            putString("music_player_position", s.musicPlayerPosition.name)
            putString("category_tile_art", s.categoryTileArt.name)
            putString("category_tile_style2", s.categoryTileStyle.name)
            putBoolean("progress_above", s.progressAbove)
            putString("focus_effect", s.focusEffect.name)
            putString("info_spacing", s.infoSpacing.name)
            putString("lyrics_position", s.lyricsPosition.name)
            putBoolean("mastered_theme_color", s.masteredThemeColor)
            putBoolean("achievements_on_cards", s.achievementsOnCards)
            putBoolean("show_status_bar", s.showStatusBar)
            putBoolean("favorite_star", s.favoriteStar)
            putBoolean("banner_buttons", s.bannerButtons)
            putBoolean("last_played_on_banner", s.lastPlayedOnBanner)
            putBoolean("launch_animation", s.launchAnimation)
            putBoolean("arrival_anim", s.arrivalAnim)
            putBoolean("popup_zoom", s.popupZoom)
            putString("home_layout", s.homeLayout.name)
            putBoolean("tile_shortcuts", s.tileShortcuts)
            putBoolean("tile_shortcut_theme2", s.tileShortcutTheme)
            putString("tile_focus", s.tileFocus.name)
            putInt("tile_scale", s.tileScale)
            putBoolean("hide_duplicates", s.hideDuplicates)
            putBoolean("quick_resume", s.quickResume)
            putString("region_order", s.regionOrder)
            putString("shortcut_spot", s.shortcutSpot.name)
            putString("shortcut_highlight", s.shortcutHighlight.name)
            putString("hero_motion", s.heroMotion.name)
            putInt("hero_zoom_percent", s.heroZoomPercent.coerceIn(5, 25))
            putString("performance_mode", s.performanceMode.name)
            putBoolean("animate_on_lite", s.animateOnLite)
            putString("scene_quality", s.sceneQuality.name)
            putBoolean("idle_saver", s.idleSaver)
            putBoolean("scene_time_of_day", s.sceneTimeOfDay)
            putBoolean("scene_from_cover", s.sceneFromCover)
            putString("category_scenes", s.categoryScenes)
            putBoolean("scene_fade", s.sceneFade)
            putBoolean("scene_second_only", s.sceneSecondOnly)
            putInt("scene_speed", s.sceneSpeed.coerceIn(25, 200))
            putInt("scene_dim", s.sceneDim.coerceIn(0, 60))
            putInt("scene_hue", s.sceneHue.coerceIn(-180, 180))
            putInt("scene_particles", s.sceneParticles.coerceIn(0, 200))
            putBoolean("lights_music", s.lightsMusic)
            if (s.sceneTheme != null) putString("scene_theme", s.sceneTheme) else remove("scene_theme")
            if (s.secondSceneTheme != null) putString("second_scene_theme", s.secondSceneTheme) else remove("second_scene_theme")
            putString("details_sections", s.detailsSections.joinToString(",") { it.name })
            putBoolean("details_physical_seen", true)
        }.apply()
    }
}

/** News feeds switched on until you pick your own (Appearance › Next to the row). */
val DEFAULT_NEWS_SOURCES: Set<String> = setOf("timeextension", "retrohandhelds", "retrododo")


/** How achievement lists look (Appearance › Achievements); read by the RA and Steam lists. */
object AchievementLook {
    val badges = androidx.compose.runtime.mutableStateOf(true)
    val flyIn = androidx.compose.runtime.mutableStateOf(true)
    val sounds = androidx.compose.runtime.mutableStateOf(true)
    fun apply(s: AppearanceSettings) {
        badges.value = s.achievementBadges; flyIn.value = s.achievementFlyIn; sounds.value = s.achievementFlySounds
    }
}

/**
 * A home layout's own values. Since update 82 only the placement part is swapped per layout (card size, spacing,
 * tilt, tile size): the game look (corners, other games, effect, media switches) is one for every layout. The other
 * fields are still stored so older saved data reads back. The active layout's values live in the normal settings.
 */
data class LayoutLook(
    val cardSize: CardSize,
    val cardCorners: CardCorners,
    val otherGamesLook: OtherGamesLook,
    val focusEffect: FocusEffect,
    val infoSpacing: InfoSpacing,
    val tiltOthers: Boolean,
    val tileScale: Int,
    val coverTemplates: Boolean,
    val boxSpine: Boolean,
    val box3d: Boolean,
    val discCase: Boolean,
    val mediaShells: Boolean,
    val bigGcDiscs: Boolean
) {
    companion object {
        /** Written after the fields; its presence says boxSpine means "3D box turned to show its spine" (update 63). */
        private const val LOOK_VERSION = "v63"

        fun serialize(m: Map<HomeLayout, LayoutLook>): String = m.entries.joinToString(";") { (l, k) ->
            "${l.name}:" + listOf(
                k.cardSize.name, k.cardCorners.name, k.otherGamesLook.name, k.focusEffect.name, k.infoSpacing.name,
                k.tiltOthers, k.tileScale, k.coverTemplates, k.boxSpine, k.box3d, k.discCase, k.mediaShells, k.bigGcDiscs,
                LOOK_VERSION
            ).joinToString(",")
        }

        fun parse(raw: String?): Map<HomeLayout, LayoutLook> {
            if (raw.isNullOrBlank()) return emptyMap()
            val out = LinkedHashMap<HomeLayout, LayoutLook>()
            for (part in raw.split(';')) {
                runCatching {
                    val layout = HomeLayout.valueOf(part.substringBefore(':'))
                    val f = part.substringAfter(':').split(',')
                    out[layout] = LayoutLook(
                        CardSize.valueOf(f[0]), CardCorners.valueOf(f[1]), OtherGamesLook.valueOf(f[2]),
                        FocusEffect.valueOf(f[3]), InfoSpacing.valueOf(f[4]), f[5].toBoolean(),
                        f[6].toInt().coerceIn(TILE_SCALE_MIN, TILE_SCALE_MAX),
                        // Parked before update 63, "spines" meant the spine beside the flat front: start those on the front alone.
                        f[7].toBoolean(), f.size > 13 && f[8].toBoolean(), f[9].toBoolean(), f[10].toBoolean(), f[11].toBoolean(), f[12].toBoolean()
                    )
                }
            }
            return out
        }
    }
}

/** The look the settings (and [Features]) hold right now. */
fun AppearanceSettings.currentLook(): LayoutLook = LayoutLook(
    cardSize, cardCorners, otherGamesLook, focusEffect, infoSpacing, tiltOthers, tileScale,
    Features.coverTemplates.value, Features.boxSpine.value, Features.box3d.value,
    Features.discCase.value, Features.mediaShells.value, Features.bigGcDiscs.value
)

/** These settings with [look] applied (the media-template switches are written to [Features]). */
fun AppearanceSettings.withLook(look: LayoutLook): AppearanceSettings {
    Features.coverTemplates.value = look.coverTemplates
    Features.boxSpine.value = look.boxSpine
    Features.box3d.value = look.box3d
    Features.discCase.value = look.discCase
    Features.mediaShells.value = look.mediaShells
    Features.bigGcDiscs.value = look.bigGcDiscs
    return copy(
        cardSize = look.cardSize, cardCorners = look.cardCorners, otherGamesLook = look.otherGamesLook,
        focusEffect = look.focusEffect, infoSpacing = look.infoSpacing, tiltOthers = look.tiltOthers, tileScale = look.tileScale
    )
}

/**
 * Switching [old]'s layout to [new]'s: parks the layout you leave and brings in the one you enter
 * (a layout you've never been on starts from what you had). Call before saving.
 */
fun swapLayoutLook(old: AppearanceSettings, new: AppearanceSettings): AppearanceSettings {
    if (new.homeLayout == old.homeLayout) return new
    val parked = new.lookByLayout + (old.homeLayout to old.currentLook())
    val next = parked[new.homeLayout]
    val base = new.copy(lookByLayout = parked)
    // Only where / how big the games sit comes back with the layout; how they look stays as it is.
    return if (next != null) base.copy(cardSize = next.cardSize, infoSpacing = next.infoSpacing, tiltOthers = next.tiltOthers, tileScale = next.tileScale)
    else base
}

/**
 * The card artwork (the game look): one for every home layout since update 82.
 * Update 295 (Toni): Pocket Quest draws Cartridges & discs by default (Layout options › Games shown as), whatever the
 * look — so its game window, Inspect and the start-up animation show the same cartridge / disc as its rows.
 */
fun AppearanceSettings.cardArtFor(layout: HomeLayout = homeLayout): CardArtType =
    if (layout == HomeLayout.POCKET && Features.pqMedia.state.value == Features.PqMedia.CARTS) CardArtType.CARTRIDGE_DISC else cardArtType

/**
 * The status bar is drawn: the setting, except on the Channel grid unless it is turned on there (update 104), and on
 * Spotlight's Home menu unless it is turned on there (update 132: the menu has its own profile, battery and clock on top).
 */
val AppearanceSettings.statusOn: Boolean
    get() = showStatusBar && (homeLayout != HomeLayout.CHAN || Features.chanStatusBar.state.value) && !spotOwnTop &&
        // Update 158: Hero tiles has its own status pill on the picture (unless its Status bar is turned on).
        (homeLayout != HomeLayout.HERO_TILES || Features.heroTilesStatusBar.state.value) &&
        // Update 308: Twin showcase has its own status capsule on the picture (unless its Status bar is turned on).
        (homeLayout != HomeLayout.TWIN_SHOWCASE || Features.twinShowStatusBar.state.value) &&
        // Update 182: the Pocket layouts have their own bar on top (name, date, time, battery); Voltcap's sat over it.
        homeLayout != HomeLayout.POCKET_CLASSIC && homeLayout != HomeLayout.POCKET_RIBBON &&
        // Update 202: Pocket Quest has its own top bar too (brand, consoles, search, settings, battery and clock).
        homeLayout != HomeLayout.POCKET

/** Update 132: Spotlight's Home menu draws its own top row (and its own button hints), so the status bar steps aside. */
val AppearanceSettings.spotOwnTop: Boolean
    get() = homeLayout == HomeLayout.SPOT && Features.spotLook.state.value == Features.SpotLook.HOME_MENU && !Features.spotStatusBar.state.value

/** Update 132: the legend along the bottom: the setting, except on Spotlight's Home menu with its own button hints. */
val AppearanceSettings.legendOn: Boolean
    get() = showLegend && !(homeLayout == HomeLayout.SPOT && Features.spotLook.state.value == Features.SpotLook.HOME_MENU && Features.spotHints.state.value)
