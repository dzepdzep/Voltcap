package com.shelf.launcher.data

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Settings for the newer features (achievement look and themes, Living Library, density, the hero
 * carousel, transitions…), each a Compose state backed by the "voltcap_features" prefs file — so any
 * screen reads them directly and recomposes when they change, without threading them through
 * AppearanceSettings. Portable (backed up and exported like the other look settings).
 *
 * [init] once at start (MainActivity); reads before that see the defaults.
 */
object Features {
    private const val PREFS = "voltcap_features"
    private var prefs: android.content.SharedPreferences? = null
    private val all = mutableListOf<Pref<*>>()

    abstract class Pref<T>(val key: String, val default: T) {
        val state: MutableState<T> = mutableStateOf(default)
        var value: T
            get() = state.value
            set(v) { state.value = v; prefs?.edit()?.let { write(it, v); it.apply() } }
        /** The value as a Flow (for services and views outside Compose). */
        fun asFlow(): kotlinx.coroutines.flow.Flow<T> = androidx.compose.runtime.snapshotFlow { state.value }
        abstract fun read(p: android.content.SharedPreferences): T
        abstract fun write(e: android.content.SharedPreferences.Editor, v: T)
        init { all += this }
    }

    class BoolPref(key: String, default: Boolean) : Pref<Boolean>(key, default) {
        override fun read(p: android.content.SharedPreferences) = p.getBoolean(key, default)
        override fun write(e: android.content.SharedPreferences.Editor, v: Boolean) { e.putBoolean(key, v) }
        fun toggle() { value = !value }
    }

    class EnumPref<E : Enum<E>>(key: String, default: E, private val values: Array<E>) : Pref<E>(key, default) {
        override fun read(p: android.content.SharedPreferences) = p.getString(key, null)?.let { n -> values.firstOrNull { it.name == n } } ?: default
        override fun write(e: android.content.SharedPreferences.Editor, v: E) { e.putString(key, v.name) }
        /** Sets it by its name (a saved snapshot); unknown names are skipped. */
        fun setByName(n: String) { values.firstOrNull { it.name == n }?.let { value = it } }
    }

    class StringPref(key: String, default: String) : Pref<String>(key, default) {
        override fun read(p: android.content.SharedPreferences) = p.getString(key, default) ?: default
        override fun write(e: android.content.SharedPreferences.Editor, v: String) { e.putString(key, v) }
    }

    class IntPref(key: String, default: Int, private val range: IntRange? = null) : Pref<Int>(key, default) {
        override fun read(p: android.content.SharedPreferences) = p.getInt(key, default).let { v -> range?.let { v.coerceIn(it) } ?: v }
        override fun write(e: android.content.SharedPreferences.Editor, v: Int) { e.putInt(key, v) }
    }

    /** A set of names, stored comma separated. */
    class SetPref(key: String, default: Set<String> = emptySet()) : Pref<Set<String>>(key, default) {
        override fun read(p: android.content.SharedPreferences) =
            p.getString(key, null)?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: default
        override fun write(e: android.content.SharedPreferences.Editor, v: Set<String>) { e.putString(key, v.joinToString(",")) }
        fun toggle(name: String) { value = if (name in value) value - name else value + name }
    }

    /**
     * Update 125: every setting here as text (for your own layout presets: they carry everything on the home screen —
     * Edit Home, what each layout shows, the layout options…). [skip]: keys left out.
     */
    fun snapshot(skip: (String) -> Boolean = { false }): Map<String, String> = all.filter { !skip(it.key) }.associate { p ->
        p.key to when (val v = p.value) {
            is Set<*> -> v.joinToString(",")
            is Enum<*> -> v.name
            else -> v.toString()
        }
    }

    /** Puts back a [snapshot] (keys it doesn't have stay as they are; unknown ones are skipped). */
    fun restore(map: Map<String, String>) {
        all.forEach { p ->
            val s = map[p.key] ?: return@forEach
            when (p) {
                is BoolPref -> if (s == "true" || s == "false") p.value = s == "true"
                is IntPref -> s.toIntOrNull()?.let { p.value = it }
                is StringPref -> p.value = s
                is SetPref -> p.value = s.split(',').filter { it.isNotBlank() }.toSet()
                is EnumPref<*> -> p.setByName(s)
                else -> Unit
            }
        }
    }

    /** Reads every setting again (after an import replaced the prefs file). */
    fun reload(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        all.forEach { pref -> @Suppress("UNCHECKED_CAST") (pref as Pref<Any?>).state.value = pref.read(p) }
    }

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        all.forEach { pref -> @Suppress("UNCHECKED_CAST") (pref as Pref<Any?>).state.value = pref.read(p) }
        migrateLegacyLook(context.applicationContext, p)
        // The old Save Vault look switch (squares / pills) became one of the styles.
        if (!p.contains("save_vault_style") && p.getString("save_shape", null) == SaveShape.PILL.name) {
            saveVaultStyle.value = SaveVaultStyle.CAPSULE
        }
        migrate42(p)
        // Update 200: the cartridge shell choices, made now — before any screen draws (see CartShellStore).
        runCatching { com.shelf.launcher.ui.CartShellStore.warmUp() }
        runCatching { com.shelf.launcher.ui.DsCases.warmUp() }
        migrate158(p) // Update 158
        migrate166(p) // Update 166
        // Update 243 (#55): Twin desk's extras start off on a new setup; an install that already had them keeps them.
        run {
            val existing = FirstRun.isDone(context.applicationContext)
            if (!p.contains("twin_desk_play_icon")) twinDeskPlayIcon.value = existing
            if (!p.contains("twin_desk_handles")) twinDeskHandles.value = existing
        }
        // "Category tiles show" moved here from the appearance settings (update 81). The console photo was the old
        // default; it now starts on the games' own art (still pickable).
        if (!p.contains("category_art")) {
            val old = context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("category_tile_art", null)
            categoryArt.value = when (old) {
                CategoryTileArt.COVERS.name -> CategoryTileArt.COVERS
                CategoryTileArt.PLAIN.name -> CategoryTileArt.PLAIN
                else -> CategoryTileArt.SLIDESHOW
            }
        }
    }

    /**
     * Update 42's new defaults, applied once to existing installs too (asked for): Volt off on the home screen
     * (he can be switched back on in Appearance › Volt).
     */
    private fun migrate42(p: android.content.SharedPreferences) {
        // Update 43: the 32-bit console style used to switch the pixel corners on for good (and the font with
        // them); they stayed after picking another style. Off again unless that style is still the one in use.
        if (!p.getBoolean("m43_done", false)) {
            if (uiStyle.value != UiStyle.PIXEL && pixelUi.value) pixelUi.value = false
            p.edit().putBoolean("m43_done", true).apply()
        }
        if (p.getBoolean("m42_done", false)) return
        p.edit().putBoolean("m42_done", true).apply()
    }

    /**
     * Update 158, once (asked for): the 3D piece you're on stops its slow turn (Turn the one you're on off), so it faces
     * you; switching it back on afterwards sticks. Someone who had picked the "Turned" stand keeps the one they're on turned.
     */
    private fun migrate158(p: android.content.SharedPreferences) {
        if (p.getBoolean("m158_face_done", false)) return
        p.edit().putBoolean("m158_face_done", true).apply()
        if (modelTurn.value) modelTurn.value = false
        if (modelAngle.value == com.shelf.launcher.ui.ModelAngle.TURNED && !p.contains("picked_faces")) pickedFaces.value = false
    }

    /**
     * Update 166, once (asked for): the 3D box or piece you're on faces you again while you scroll ("The one you're on" ›
     * Faces you); "Turned like the others" stays a choice (Game look › 3D models, Carousel options, Case stacks options).
     */
    private fun migrate166(p: android.content.SharedPreferences) {
        if (p.getBoolean("m166_face_done", false)) return
        p.edit().putBoolean("m166_face_done", true).apply()
        if (!pickedFaces.value) com.shelf.launcher.ui.PickedFacing.set(true)
    }

    // ---- Look & feel ----
    enum class Density(val label: String, val scale: Float) { COMFORTABLE("Roomy", 1f), COMPACT("Tight", 0.82f) } // (0.6.15: "Compact" was also a Size choice)
    val density = EnumPref("density", Density.COMFORTABLE, Density.values())

    enum class Intensity(val label: String, val speed: Float, val amount: Float) {
        LOW("Low · performance", 0.6f, 0.45f), NORMAL("Normal", 1f, 1f), HIGH("High", 1.25f, 1.45f)
    }
    /** Theme intensity: Low also works as a performance mode (fewer particles, slower motion). */
    val themeIntensity = EnumPref("theme_intensity", Intensity.NORMAL, Intensity.values())
    /** Use the theme's own colours only (ignore game / album / custom colours). */
    val themeColorsOnly = BoolPref("theme_colors_only", false)

    // ---- Overlays over other apps ----
    /** Voltcap's overlays (stats, music, trophies) only appear over games when this is on. */
    val overlaysOverApps = BoolPref("overlays_over_apps", false)

    // ---- Achievements ----
    /** Badge view: which text shows under each badge (independent). Hidden text = bigger badges. */
    val badgeName = BoolPref("badge_name", true)
    val badgePercent = BoolPref("badge_percent", true)
    val badgeCount = BoolPref("badge_count", true)
    /** The automatic unlock animation for new unlocks (plays once per unlock). */
    val unlockAnimation = BoolPref("unlock_animation", true)
    /** Only new unlocks animate (the entrance fly-in for the whole wall is skipped). */
    val animateOnlyNew = BoolPref("animate_only_new", true)
    val completionCelebration = BoolPref("completion_celebration", true)

    enum class AchievementTheme(val label: String, val blurb: String) {
        INHERIT("Follow the app theme", "Achievements use the launcher theme's colours."),
        TROPHY_SHELF("Trophy Shelf", "Badges stand on lit glass shelves."),
        VAULT("Achievement Vault", "A dark vault; unlocked badges glow behind steel."),
        CAPSULE_POP("Capsule Pop", "Badges sit in capsules that pop open."),
        POSTER_WALL("Poster Wall", "Badges pinned up like posters, with depth."),
        ARCADE("Arcade Scoreboard", "High-score table colours and pixel type."),
        CRT("CRT Unlock", "Scanlines and phosphor glow on every badge."),
        CONSTELLATION("Constellation", "Badges as stars, linked as you unlock them."),
        ENERGY_GRID("Energy Grid", "A neon grid that lights up per unlock."),
        RELIC("Treasure / Relic", "Gold and stone, like found relics."),
        PARADE("Badge Parade", "Badges march in with a bounce.")
    }
    val achievementTheme = EnumPref("achievement_theme", AchievementTheme.INHERIT, AchievementTheme.values())
    val achievementIntensity = EnumPref("achievement_intensity", Intensity.NORMAL, Intensity.values())

    // ---- Post-session, journey, states ----
    val gameJourney = BoolPref("game_journey", true)
    /** One contextual milestone line in the game's info ("Last session: 1h 42m"). */
    val contextMilestone = BoolPref("context_milestone", true)
    /** Small state marks on covers: NEW / PLAYING / ON HOLD / COMPLETED / MASTERED / 100%. */
    val gameStates = BoolPref("game_states", false)

    // ---- Smart Continue / Living Library ----
    val smartContinue = BoolPref("smart_continue", false)
    /** Off by default. Never hides or reorders anything by itself. */
    val livingLibrary = BoolPref("living_library", false)
    val llActivityVisuals = BoolPref("ll_activity", true)
    val llHighlightRecent = BoolPref("ll_recent", true)
    val llHighlightUnfinished = BoolPref("ll_unfinished", false)
    val llHighlightNear = BoolPref("ll_near", true)
    val llSubdueInactive = BoolPref("ll_subdue", false)
    /** Sorting never follows activity (always on unless switched off on purpose). */
    val llNeverReorder = BoolPref("ll_never_reorder", true)
    /** Explore: everything shown equally for now (not saved: resets on restart). */
    val exploreMode = mutableStateOf(false)

    // ---- Hero carousel ----
    val heroBlur = BoolPref("hero_blur", false)
    val heroDim = BoolPref("hero_dim", true)
    /** Hides most of the UI after a while without input; any input brings it back. */
    val idleFocusMode = BoolPref("idle_focus", false)
    val heroParallax = BoolPref("hero_parallax", true)

    // ---- Artwork ----
    /**
     * Which picture fills the background (home, hero carousel, game popup, second screen) — see [ArtPick].
     * A background you picked yourself (Artwork & details) always wins.
     */
    enum class ArtPriority(val label: String, val blurb: String) {
        SMART("Smart", "Animated hero → Hero → Screenshot → Cover"),
        HERO("Hero first", "Hero → Wide capsule → Screenshot → Cover"),
        SCREENSHOT("Screenshot first", "Your screenshot → Hero → Cover"),
        COVER("Cover first", "Cover → Hero → Screenshot")
    }
    val artPriority = EnumPref("art_priority", ArtPriority.SMART, ArtPriority.values())

    // ---- Transitions (background art between games: HeroMotion; this is their speed) ----
    enum class Speed(val label: String, val ms: Int) { FAST("Fast", 160), NORMAL("Normal", 240), CINEMATIC("Cinematic", 520) }
    val transitionSpeed = EnumPref("transition_speed", Speed.NORMAL, Speed.values())
    /** Update 109: the newer animations can each be turned off (Home › Animations, each with a preview). */
    val animPageSlide = BoolPref("anim_page_slide", true)
    val animRiseIn = BoolPref("anim_rise_in", true)
    val animHeldPictures = BoolPref("anim_held_pictures", true)
    /**
     * Update 112: how things come in — everywhere, and per layout (Home › Animations › For this layout):
     *  - a game's info / name / panels when you move ([InfoAnim]): rise in, fade in, or just appear
     *  - pages and categories ([PageAnim]): slide, fade, or just change
     *  - how far the game you're on stands out from the others ([focusPop], % of the layout's own lift)
     *  - spinning / bobbing discs, cartridges and cases only on the game you're on ([motionFocusedOnly]).
     * [animRiseIn] / [animPageSlide] (update 109) off still mean "just appear / just change".
     */
    enum class InfoAnim(val label: String) { RISE("Rise in"), FADE("Fade in"), NONE("Just appear") }
    enum class PageAnim(val label: String) { SLIDE("Slide"), FADE("Fade"), NONE("Just change") }
    val infoAnim = EnumPref("anim_info", InfoAnim.RISE, InfoAnim.values())
    val pageAnim = EnumPref("anim_page", PageAnim.SLIDE, PageAnim.values())
    val focusPop = IntPref("focus_pop", 100, 0..400)
    val motionFocusedOnly = BoolPref("motion_focused_only", false)
    /** Update 112: Resume & discover — how the next game comes into the big spot. */
    enum class ResumeSwap(val label: String) { SLIDE("Slides in"), JUMP("Jumps in") }
    val resumeSwap = EnumPref("resume_swap", ResumeSwap.SLIDE, ResumeSwap.values())
    /** Per-layout choices over the ones above: "CROSS.info=FADE;CHAN.page=FADE;DUAL.pop=200;SHELF.motion=1". */
    val layoutAnims = StringPref("layout_anims", "")
    /**
     * Update 137: how the next game comes into the wide place at the left of the Voltcap row (wide capsules, the game
     * you're on at the left): slides in, fades in, or just appears (Home › Animations).
     */
    enum class CapsuleSwap(val label: String) { SLIDE("Slides in"), FADE("Fades in"), NONE("Just appears") }
    val capsuleSwap = EnumPref("capsule_swap", CapsuleSwap.SLIDE, CapsuleSwap.values())
    /**
     * Update 141: how the games slide along when you move (the rows, the carousels): Quick is the old snap-like glide
     * (about a tenth of a second, it read as the next game just appearing), Smooth and Slow let you see them slide.
     */
    enum class RowGlide(val label: String, val stiffness: Float, val carouselMs: Int) {
        QUICK("Quick", 1100f, 160), SMOOTH("Smooth", 380f, 300), SLOW("Slow", 170f, 460)
    }
    val rowGlide = EnumPref("row_glide", RowGlide.SMOOTH, RowGlide.values())
    /**
     * Update 137, artwork source per look (Settings › Metadata › Artwork source per look): where each kind of picture
     * is fetched from first — the covers (Covers look), the wide capsules (Capsule look), the backgrounds (Hero look),
     * the logos. "" = the main artwork source. ArtSource names. (The real cases, discs, cartridges, labels and spines
     * — Box art, Cartridge / disc, Discs only, Spines — only ScreenScraper has, with RetroAchievements' box art.)
     */
    val artSourceCover = StringPref("art_source_cover", "")
    val artSourceWide = StringPref("art_source_wide", "")
    val artSourceHero = StringPref("art_source_hero", "")
    val artSourceLogo = StringPref("art_source_logo", "")

    // ---- Per game ----
    /** Every game's own look ([GameLooks]) as JSON {"stableKey": {...}}. */
    val gameLooks = StringPref("game_looks", "")

    // ---- Game popup ----
    enum class PlayStyle(val label: String) { PILL("Pill"), BLOCK("Block"), RING("Ring"), CAPSULE("Capsule"), TEXT("Text only") }
    val playStyle = EnumPref("play_style", PlayStyle.BLOCK, PlayStyle.values())
    /**
     * How Play and the game's numbers / buttons are laid out (game popup): Showcase = a "continue playing"
     * line and stat boxes above, a Play pill with round icon buttons beside it; Classic = the earlier row.
     */
    enum class HeaderStyle(val label: String) { SHOWCASE("Showcase"), CLASSIC("Classic") }
    val headerStyle = EnumPref("header_style", HeaderStyle.SHOWCASE, HeaderStyle.values())
    /** The same showcase block for the game you're on, above the home row (classic row layout). */
    val homeHeader = BoolPref("home_header", true)
    /** Home game header, tight layout: the category above the title, small gaps, score / save sync / game options small at the right. */
    val homeHeaderTight = BoolPref("home_header_tight", true)
    /** Game look "Tile": the Voltcap row's covers as big squares (the Tile home's tiles), the one you're on a bit bigger. */
    val squareTiles = BoolPref("look_square_tiles", false)
    /** Update 317ah: Tile home's round buttons were put under its tiles the first time it was picked (once only). */
    val tileHomeButtonsSet = BoolPref("tile_home_buttons_set", false)
    /**
     * Update 166: in the carousels, the game you're on unfolds into its wide capsule as you scroll (the look "Covers ·
     * wide capsule"; the Voltcap row has it from the row's own "It shows as"). Off: it stays a cover.
     */
    val carouselWide = BoolPref("carousel_wide", false)
    /**
     * Update 113: game look "Spines": every game as its spine (its own spine scan, else the console's spine with the
     * name on it) — in the Voltcap row, the grid, the carousels and the style layouts (not the Hero carousel's
     * column or the Tile look); in the row and the carousels the game you're on shows its cover, like a game taken
     * off the shelf. Over the cover look ([squareTiles] off).
     */
    val spineLook = BoolPref("look_spines", false)
    /**
     * Update 113: the big logos (Edit Home's logo part, Hero carousel, Spotlight's title, the second screen's
     * showcase) from SteamGridDB only (on, the default), else whichever source the artwork's logo came from. A logo
     * you picked yourself always wins. The small logo by the game's name is RetroAchievements' icon by default
     * (Game names › Logos from RetroAchievements).
     */
    val bigLogoSgdb = BoolPref("big_logo_sgdb", true)
    /** 0.6.17: the Metacritic score on the game you're on (Settings › Game look › Cards, and the Sort window). */
    val metacriticChip = BoolPref("metacritic_chip", false)
    /**
     * Update 115: a small console badge in the corner of every game's tile (the console's logo in white on its own
     * colour), and whether that pill takes your theme's colour instead.
     */
    val consoleBadges = BoolPref("console_badges", true)
    val consoleBadgeTheme = BoolPref("console_badge_theme", false)
    /** Update 125: badges with the console's logo from Dan Patrick's white set (the R1 Console badges tile's second step). */
    val consoleBadgeLogos = BoolPref("console_badge_logos", false)
    /** Update 317ao: which corner of a game's tile the console badge takes (Auto: where each layout puts it, clear of the rest). */
    enum class BadgeCorner(val label: String) { AUTO("Auto"), TOP_LEFT("Top left"), TOP_RIGHT("Top right"), BOTTOM_LEFT("Bottom left"), BOTTOM_RIGHT("Bottom right") }
    val consoleBadgeCorner = EnumPref("console_badge_corner", BadgeCorner.AUTO, BadgeCorner.values())
    /**
     * Update 125: with the Cartridges & discs look, every game as a cartridge — its own cartridge for cartridge consoles
     * (the real scan, or the drawn one), a plain cartridge with its cover as the label for disc and UMD games. The R1
     * Look tile's "Cartridges only" step.
     */
    val cartsOnly = BoolPref("carts_only", false)
    /** Update 329: short console names on the screens (PS2, GBA…) instead of the official ones (ConsoleNames.label). */
    val shortConsoleNames = BoolPref("short_console_names", false)
    /**
     * Update 128, the Cartridges look (cartsOnly): Original keeps each console's own cartridge and puts your cartridge
     * pack on the rest (discs, UMDs, Steam, Android, PC, arcade…); Custom puts every console on your pack. Also the
     * pack's shape (one of 14) and whether it's the clear shell. R1 › Cartridges, Settings › Game look.
     */
    val cartSource = EnumPref("cart_source", com.shelf.launcher.ui.CartSource.ORIGINAL, com.shelf.launcher.ui.CartSource.values())
    val cartLook = EnumPref("cart_look", com.shelf.launcher.ui.CartLook.GB, com.shelf.launcher.ui.CartLook.values())
    val cartClear = BoolPref("cart_clear", false)
    /**
     * Update 136: your 3D shape library (the "3D models" pack): which pieces the Cartridges & discs look and the 3D cases
     * of the carousels draw (Off = as before) and how they stand.
     * Settings › Game look › 3D models.
     * Update 200: the 3D cartridges looks (Real = 3D cartridges, Custom = 3D custom, Clear = 3D clear; Off = none) — the
     * same saved setting, now drawing the drawn shells with real depth (ui/PhysicalMedia.kt drawn3dPick); the
     * pre-rendered 3D models are retired.
     */
    val modelFinish = EnumPref("model_finish", com.shelf.launcher.ui.ModelFinish.OFF, com.shelf.launcher.ui.ModelFinish.values())
    /**
     * Update 141: the finish to come back to when the "3D models" look is picked again (it turns 3D models off when left).
     * Update 202: Cartridges & discs' Cartridge choice while another look is on; Real by default (each console's real
     * shell, so the researched real backs show — asked for), it was Custom.
     */
    val modelFinishLast = EnumPref("model_finish_last", com.shelf.launcher.ui.ModelFinish.REAL, com.shelf.launcher.ui.ModelFinish.values())
    val modelAngle = EnumPref("model_angle", com.shelf.launcher.ui.ModelAngle.FACE_PICKED, com.shelf.launcher.ui.ModelAngle.values())
    /** Update 136: the game's name on a strip along the bottom of the 3D model's label (Settings › Game look › 3D models). */
    val modelName = BoolPref("model_name", false)
    /**
     * Update 151 (Settings › Game look › 3D models): disc consoles stand as their 3D case (off) or, with Custom / Clear,
     * as your custom cartridge (on); the piece you're on turns slowly to and fro; a faint reflection on the floor under
     * each piece.
     */
    val modelDiscAsCart = BoolPref("model_disc_as_cart", false)
    // Update 158: off by default (the one you're on stands still, facing you); switched off once for existing installs too
    // ([migrate158]), and switching it back on sticks.
    val modelTurn = BoolPref("model_turn", false)
    /**
     * Update 158, "The one you're on" (Game look › 3D models, Carousel options, Case stacks): the 3D piece or case you're on
     * faces you straight on (true) or stands turned like the others (PickedFacing.kt).
     */
    val pickedFaces = BoolPref("picked_faces", true)
    val modelReflect = BoolPref("model_reflect", false)
    /**
     * Update 136, Art drop (R1 › Art drop, Settings › Game look): how far the cover drops below the top of the window, 0–100,
     * each look its own — the case template's front (Box art looks) and the 3D models' window.
     */
    val artDropCase = IntPref("art_drop_case", 0, -50..100)
    val artDropModel = IntPref("art_drop_model", 0, -50..100)
    /**
     * Update 137, Art position (R1 › Art position: drag the picture, pinch or L1 / R1 to size it): with the drop above
     * (now also upwards, to −50), how far the cover moves left / right (−50…50) and its size (50–200 %), each look its own.
     */
    val artShiftCase = IntPref("art_shift_case", 0, -50..50)
    val artShiftModel = IntPref("art_shift_model", 0, -50..50)
    val artZoomCase = IntPref("art_zoom_case", 100, 50..200)
    /**
     * Update 202: Art position for the other looks (Edit Home › R3 / Art position): "COVER=x,y,zoom;CART=…;DISC=…", the
     * same steps as the Box art's three above (left / right −50…50, up / down −50…100, size 50–200 %). Missing = centred.
     */
    val artPosLooks = StringPref("art_pos_looks", "")
    val artZoomModel = IntPref("art_zoom_model", 100, 50..200)
    /**
     * Update 129: one console's disc, case or spine for every game ("" = each game's own), picked on the R1 panel's
     * Disc / Case / Spine tile, which shows with the Discs only, Real cases and Spines looks (LookStyles.kt).
     */
    val discStyle = StringPref("disc_style", "")
    val caseStyle = StringPref("case_style", "")
    val spineStyle = StringPref("spine_style", "")
    /**
     * Update 202: the game look is four main looks (Covers, Cases, Cartridges & discs, Art) with a few plain choices each
     * (ui/GameLookPicker.kt). The choices still set the switches above, which the drawing code reads; this remembers what
     * each main look had ("key=value;…": main, cover, caseSize, caseMedia, discs, art), so stepping through the four on
     * the R1 Look tile brings each one back as you left it — two looks share switches (a disc half out of its case).
     */
    val lookMemory = StringPref("look_memory", "")

    // ---- 32-bit pixel style ----
    /** Square / cut pixel corners on panels, chips and dialogs (the type is [uiFont] now). */
    val pixelUi = BoolPref("pixel_ui", false)
    /**
     * The interface font (Appearance › Interface style › Font). Normal: the 32-bit font only where it fits — the
     * headings, with the 32-bit console style. 32-bit everywhere: every text in it, game names, categories and
     * the status bar included, a size smaller so names don't get cut or wrap.
     */
    enum class UiFont(val label: String) { NORMAL("Normal"), PIXEL("32-bit everywhere") }
    val uiFont = EnumPref("ui_font", UiFont.NORMAL, UiFont.values())
    /** Also pixelate artwork (covers, heroes, screenshots). Off = artwork stays sharp. */
    val pixelArtwork = BoolPref("pixel_artwork", false)
    enum class PixelSize(val label: String, val px: Float) { FINE("Fine", 2f), CLASSIC("Classic", 3f), CHUNKY("Chunky", 4f) }
    val pixelSize = EnumPref("pixel_size", PixelSize.CLASSIC, PixelSize.values())
    enum class IntroStyle(val label: String) { CLASSIC("Voltcap"), PIXEL("32-bit capsule"), OFF("Off") }
    val introStyle = EnumPref("intro_style", IntroStyle.CLASSIC, IntroStyle.values())

    // ---- Dock backing (behind the row of games) ----
    enum class DockBack(val label: String) { OFF("Nothing"), COLOR("Solid colour"), IMAGE("Animated picture") }
    val dockBacking = EnumPref("dock_backing", DockBack.COLOR, DockBack.values())
    val dockColor = StringPref("dock_color", "#05070B")
    val dockOpacity = StringPref("dock_opacity", "62")
    val dockImage = StringPref("dock_image", "")

    // ---- Ambience ----
    /** Per-game ambience layers (Game settings › Look for this game) are drawn only while this is on. */
    val gameAmbience = BoolPref("game_ambience", true)

    // ---- Lyrics layout (every place a lyric line shows: home, the music themes, second screen, screensaver) ----
    enum class LyricsAlign(val label: String) { START("Left"), CENTER("Centre"), END("Right") }
    val lyricsAlign = EnumPref("lyrics_align", LyricsAlign.CENTER, LyricsAlign.values())
    enum class LyricsMotion(val label: String) { STILL("Still"), FLOAT("Gentle float") }
    val lyricsMotion = EnumPref("lyrics_motion", LyricsMotion.STILL, LyricsMotion.values())

    // ---- Save Vault look ----
    /** Squares (the classic save icons) or upright pills. */
    enum class SaveShape(val label: String) { SQUARE("Squares"), PILL("Pills") }
    val saveShape = EnumPref("save_shape", SaveShape.SQUARE, SaveShape.values())
    /** The picked save turns slowly in 3D and idle ones tilt as they bob (off: they stay upright). */
    val saveTurn = BoolPref("save_turn", false)

    // ---- Theme extras ----
    /** Retro bounce: which console icons bounce round (the category you're on, or a mix from your library). */
    enum class RetroIcons(val label: String) { CURRENT("This console"), MIXED("Mix of systems") }
    val retroIcons = EnumPref("retro_icons", RetroIcons.CURRENT, RetroIcons.values())
    /** Lyric cloud: whole lines, single words, or both. */
    enum class LyricScatter(val label: String) { LINES("Lines"), WORDS("Word by word"), MIX("Both") }
    val lyricScatter = EnumPref("lyric_scatter", LyricScatter.MIX, LyricScatter.values())
    /** Lyric themes (Karaoke, Neon lyrics): the next line shows, dimmer, under the one being sung. */
    val lyricNextLine = BoolPref("lyric_next_line", true)
    /** Karaoke: a ball hops from word to word over the line being sung. */
    val karaokeBall = BoolPref("karaoke_ball", true)
    /** Update 119: the trophy themes (Trophy vault, Badge bounce) show only your mastered games. */
    val trophyMasteredOnly = BoolPref("trophy_mastered_only", false)
    /** Update 119: the lyric themes' words a little earlier (−) or later (+) than the player says, in ms. */
    val lyricsOffsetMs = IntPref("lyrics_offset_ms", 0, -2000..2000)
    /** Update 119: the lyric themes over the song's own cover (blurred and dimmed) instead of their scene. */
    val lyricCoverBg = BoolPref("lyric_cover_bg", true)

    // ---- Theme look: colour source, blur, dim (one per theme; Settings › Appearance › Theme look) ----
    /** Where the app's colours come from. One setting instead of the old accent / whole-app switches. */
    enum class ColorSource(val label: String, val blurb: String) {
        THEME("Theme original", "The theme's own colours, untouched."),
        GAME("Current game", "Background, panels and accent follow the cover of the game you're on."),
        CUSTOM("Custom", "Your own accent and background colours, saturation and brightness.")
    }
    /** What the blur and dim cover: the whole background, or only the strip behind the games, their names and the categories. */
    enum class BlurArea(val label: String) { FULL("Full background"), GAME_UI("Game UI area only") }

    /**
     * One theme's own look. Every theme keeps its own; Reset This Theme puts it back to [ThemeLook()]
     * (the theme's original colours, no blur, no dim, normal motion). [accent] / [background] are ARGB
     * (null = the theme's own); saturation and brightness are percentages (100 = unchanged).
     */
    data class ThemeLook(
        val colorSource: ColorSource = ColorSource.THEME,
        val accent: Int? = null,
        val background: Int? = null,
        /** 0..200 % */
        val saturation: Int = 100,
        /** 50..150 % */
        val brightness: Int = 100,
        val blurArea: BlurArea = BlurArea.FULL,
        /** 0..100 % */
        val blur: Int = 0,
        /** 0..100 % */
        val dim: Int = 0,
        /** Animated scene: speed 25..200 %, colour shift -180..180°, particles 0..200 %. */
        val sceneSpeed: Int = 100,
        val sceneHue: Int = 0,
        val sceneParticles: Int = 100,
        /** Scenes with a subject in the middle (Voltcap, Trophy vault, Specimen lab...): moved down by this % of the height (-15..30). */
        val sceneShift: Int = 0
    ) {
        fun encode(): org.json.JSONObject = org.json.JSONObject().apply {
            put("src", colorSource.name)
            accent?.let { put("acc", it) }
            background?.let { put("bg", it) }
            put("sat", saturation); put("bri", brightness)
            put("ba", blurArea.name); put("blur", blur); put("dim", dim)
            put("spd", sceneSpeed); put("hue", sceneHue); put("prt", sceneParticles)
            if (sceneShift != 0) put("sft", sceneShift)
        }

        companion object {
            fun decode(o: org.json.JSONObject): ThemeLook = ThemeLook(
                colorSource = ColorSource.values().firstOrNull { it.name == o.optString("src") } ?: ColorSource.THEME,
                accent = if (o.has("acc")) o.optInt("acc") else null,
                background = if (o.has("bg")) o.optInt("bg") else null,
                saturation = o.optInt("sat", 100).coerceIn(0, 200),
                brightness = o.optInt("bri", 100).coerceIn(50, 150),
                blurArea = BlurArea.values().firstOrNull { it.name == o.optString("ba") } ?: BlurArea.FULL,
                blur = o.optInt("blur", 0).coerceIn(0, 100),
                dim = o.optInt("dim", 0).coerceIn(0, 100),
                sceneSpeed = o.optInt("spd", 100).coerceIn(25, 200),
                sceneHue = o.optInt("hue", 0).coerceIn(-180, 180),
                sceneParticles = o.optInt("prt", 100).coerceIn(0, 200),
                sceneShift = o.optInt("sft", 0).coerceIn(-15, 30)
            )
        }
    }

    /** Every theme's own look, as JSON {"THEME_NAME": {...}}. Themes not in it use [themeLookDefault]. */
    val themeLooks = StringPref("theme_looks", "")
    /** The look themes start with: what the old global colour settings were (migrated once), else plain. */
    val themeLookDefault = StringPref("theme_look_default", "")

    private var lookCacheKey: String? = null
    private var lookCache: Map<String, ThemeLook> = emptyMap()
    private var defaultCacheKey: String? = null
    private var defaultCache: ThemeLook = ThemeLook()

    private fun looks(): Map<String, ThemeLook> {
        val raw = themeLooks.state.value
        if (raw != lookCacheKey) {
            lookCache = runCatching {
                val o = org.json.JSONObject(raw)
                o.keys().asSequence().associateWith { k -> ThemeLook.decode(o.getJSONObject(k)) }
            }.getOrDefault(emptyMap())
            lookCacheKey = raw
        }
        return lookCache
    }

    private fun defaultLook(): ThemeLook {
        val raw = themeLookDefault.state.value
        if (raw != defaultCacheKey) {
            defaultCache = runCatching { ThemeLook.decode(org.json.JSONObject(raw)) }.getOrDefault(ThemeLook())
            defaultCacheKey = raw
        }
        return defaultCache
    }

    /** The look of [theme] (an AppTheme name). Reads Compose state: callers recompose when it changes. */
    fun lookFor(theme: String): ThemeLook = looks()[theme] ?: defaultLook()

    fun setLook(theme: String, look: ThemeLook) {
        val o = runCatching { org.json.JSONObject(themeLooks.value) }.getOrDefault(org.json.JSONObject())
        o.put(theme, look.encode())
        themeLooks.value = o.toString()
    }

    /** Reset This Theme: only this theme's look goes back to the original; nothing else changes. */
    fun resetLook(theme: String) = setLook(theme, ThemeLook())

    /** True when [theme] has been changed from its original look. */
    fun isLookChanged(theme: String): Boolean = lookFor(theme) != ThemeLook()

    /** Once: the old accent / whole-app / custom colour / scene editor settings become every theme's starting look. */
    private fun migrateLegacyLook(context: Context, p: android.content.SharedPreferences) {
        if (p.contains("theme_look_default")) return
        val a = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val accent = if (a.contains("custom_accent")) a.getInt("custom_accent", 0) else null
        val bg = if (a.contains("custom_background")) a.getInt("custom_background", 0) else null
        val source = when {
            a.getBoolean("dynamic_theme", false) || a.getBoolean("dynamic_accent", false) -> ColorSource.GAME
            accent != null || bg != null -> ColorSource.CUSTOM
            else -> ColorSource.THEME
        }
        val look = ThemeLook(
            colorSource = source, accent = accent, background = bg,
            dim = (a.getInt("scene_dim", 0)).coerceIn(0, 100),
            sceneSpeed = a.getInt("scene_speed", 100).coerceIn(25, 200),
            sceneHue = a.getInt("scene_hue", 0).coerceIn(-180, 180),
            sceneParticles = a.getInt("scene_particles", 100).coerceIn(0, 200)
        )
        themeLookDefault.value = look.encode().toString()
    }

    // ---- Favourite themes & rotation ----
    /** How a theme moves: a still picture, a slow quiet scene, or a fully animated one. */
    enum class ThemeKind(val label: String) { STATIC("Static"), CALM("Calm"), ANIMATED("Animated") }
    enum class ThemeRotation(val label: String) {
        OFF("Off"),
        ON_START("Random favorite on app start"),
        TIMED("Random favorite after a set time"),
        LAUNCHES("Random favorite after game launches")
    }
    /** AppTheme names marked as favourites (the ★ in the theme browser). */
    val favoriteThemes = SetPref("favorite_themes")
    val themeRotation = EnumPref("theme_rotation", ThemeRotation.OFF, ThemeRotation.values())
    val themeRotationMinutes = IntPref("theme_rotation_minutes", 60, 5..1440)
    val themeRotationLaunches = IntPref("theme_rotation_launches", 3, 1..50)
    /** Which kinds of favourite themes rotation may pick (names of [ThemeKind]); empty = all. */
    val themeRotationKinds = SetPref("theme_rotation_kinds", ThemeKind.values().map { it.name }.toSet())

    // ---- Custom themes & visualizers (ui/CustomPacks.kt; the files themselves stay on this device) ----
    /** Id of the custom theme pack that "Your theme" shows ("" = none chosen). */
    val customScenePack = StringPref("custom_scene_pack", "")
    /** Id of the custom visualizer pack that "Your visualizer" shows ("" = none chosen). */
    val customVizPack = StringPref("custom_viz_pack", "")
    /** Packs Voltcap switched off because the home screen ran slowly with them (open the pack to try again). */
    val customPackOff = SetPref("custom_pack_off")

    // ---- Safe zones ----
    /** Extra margin on top of the automatic safe zone, 0..8 % of the short screen side (TVs with overscan, odd bezels). */
    val safeZoneExtra = IntPref("safe_zone_extra", 0, 0..8)

    // ---- Category name (console / platform categories) ----
    enum class ConsoleNamePin(val label: String) {
        FLOW("With the row (as before)"),
        CENTER("Pinned · centred above the games"),
        LEFT("Pinned · left above the games"),
        HIDDEN("Hidden")
    }
    /** Only console / platform categories use this; other categories always keep the normal behaviour. */
    val consoleNamePin = EnumPref("console_name_pin", ConsoleNamePin.FLOW, ConsoleNamePin.values())

    // ---- Edit Home: moved widgets ----
    /**
     * Home widgets the person moved in Edit Home: "id=x,y;id2=x,y", each the widget's top-left as a
     * fraction (0..1) of the free room inside the safe zone. Missing id = the widget's normal spot.
     */
    val widgetPositions = StringPref("widget_positions", "")
    /** Home widgets' sizes set in Edit Home ("id=1.2;…", 1 = normal; the corner handle or L2 / R2). */
    val widgetScales = StringPref("widget_scales", "")
    /** Widths (dp) that moved home parts keep (update 90: \"id=320.0;…\"). */
    val widgetWidths = StringPref("widget_widths", "")
    /**
     * Update 133: how far each part is stretched in Edit Home, wider and taller on its own (its side and bottom handles):
     * "id=x,y;…" (1 = as it is). What's inside stretches with it.
     */
    val widgetStretch = StringPref("widget_stretch", "")
    /** How the cover turns into the wide capsule on the game you're on (update 86): a card flip (default), a light sweep, the old wipe. */
    // Update 90: Roll out (default, a quick soft-edged unroll from the left) and Slide (the cover slides out, the wide
    // art slides in) added; new key so everyone gets the new default.
    enum class CapsuleReveal(val label: String) { ROLL("Roll out"), SLIDE("Slide"), FLIP("Card flip"), SWEEP("Light sweep"), WIPE("Wipe (old)"), NONE("Off") }
    val capsuleReveal = EnumPref("capsule_reveal_v2", CapsuleReveal.ROLL, CapsuleReveal.values())
    /** Update 129: the same for "Wide for the first game only" (its fixed capsule); off by default, the wide art just shows. */
    val capsuleRevealFirst = EnumPref("capsule_reveal_first", CapsuleReveal.NONE, CapsuleReveal.values())
    /**
     * Update 129: where the achievement count / progress sits on the game you're on, on covers and every other
     * picture (the R1 Achievements tile, Appearance › Achievements › Trophy count). The wide capsule follows the
     * same choice through its own Badge & progress bar settings.
     */
    enum class AchPlace(val label: String, val short: String) {
        CORNER("In the corner", "Corner"), ABOVE("Above the name", "Above"), NAME("Next to the name", "By name"),
        UNDER("Under the cover", "Under"), OFF("Off", "Off")
    }
    val achPlace = EnumPref("ach_place", AchPlace.CORNER, AchPlace.values())
    /**
     * (Update 240, #32/#57) On a disc in the game row (Discs, Continue playing…): the trophy count and the game's state on
     * a chip strip below the disc, at the tile's bottom right, instead of over the disc's curve. Off: where Trophy count
     * puts them, as on covers.
     */
    val discTileChips = BoolPref("disc_tile_chips", true)
    /**
     * How discs and cartridges move (update 87). Discs: spin = turning like it's playing, bounce = a slow soft bob.
     * Cartridges: spin = turning slowly left and right, bounce = a little hop and settle. Either, both, or still.
     */
    enum class MediaMotion(val label: String) {
        SPIN("Spin"), BOUNCE("Bounce"), BOTH("Both"), OFF("Still");
        val spins get() = this == SPIN || this == BOTH
        val bounces get() = this == BOUNCE || this == BOTH
    }
    val discMotion = EnumPref("disc_motion", MediaMotion.SPIN, MediaMotion.values())
    val cartMotion = EnumPref("cart_motion", MediaMotion.BOUNCE, MediaMotion.values())
    /** Box art cases (update 88): spin = turning slowly left and right, bounce = a little hop. Still by default. */
    val caseMotion = EnumPref("case_motion", MediaMotion.OFF, MediaMotion.values())
    /** Spotify (update 90): with nothing playing anywhere, a picked song wakes Spotify on this handheld in the background and plays there, without opening the app. */
    val spotifyQuietWake = BoolPref("spotify_quiet_wake", true)
    /**
     * Extra game info on layouts that don't have it built in (update 93: Hero carousel, Spotlight, Icon bar), per layout:
     * "HERO=h_title,h_stats,info;SPOT=news". Each part floats on its own and is moved / resized in Edit Home.
     */
    val overlayParts = StringPref("overlay_parts", "")
    /** Update 104: every layout (the Voltcap row: the logo and description only, it has the rest built in). */
    val overlayLayouts: Set<com.shelf.launcher.data.HomeLayout> = com.shelf.launcher.data.HomeLayout.values().toSet()
    /** Parts taken off the home screen with their ✕ in Edit Home (update 104). */
    val hiddenParts = SetPref("hidden_parts")
    /** How far the layouts' own parts were moved in Edit Home (update 108): "part:LAYOUT:name=dx,dy;…" in dp. */
    val partShifts = StringPref("part_shifts", "")
    /** The backing behind the floating game info parts (update 104). */
    enum class PartBacking(val label: String) {
        LAYOUT("Layout's colours"), THEME("Theme colours"), GLASS("Dark glass"), CAPSULE("Capsule"),
        MEMORY_CARD("Memory card"), SD_CARD("SD card"), FLOPPY("Floppy label"), NONE("None")
    }
    val partBacking = EnumPref("part_backing", PartBacking.LAYOUT, PartBacking.values())
    /**
     * Update 133: how solid the backing behind the floating parts is, 0–100 (0 = as see-through as the backings were,
     * 100 = nearly solid). Settings › Home › Layout options › Game info on screen › Backing, and Edit Home › ✎ Style.
     */
    val partBackingSolid = IntPref("part_backing_solid", 70, 0..100)
    /** Update 212: the category title on layouts that don't show the category themselves (CategoryTitle.kt). */
    enum class CategoryTitleMode(val label: String) { SUBTLE("Show, then dim"), ALWAYS("Always clear"), OFF("Off") }
    val categoryTitle = EnumPref("category_title_212", CategoryTitleMode.SUBTLE, CategoryTitleMode.values())
    /** Update 212: what Android games are drawn as where games are drawn as cartridges or discs (AndroidMedia.kt). */
    enum class AndroidMediaMode(val label: String) { FOLLOW("Follow the current look"), DISC("Disc"), CARTRIDGE("Cartridge") }
    val androidMedia = EnumPref("android_media_212", AndroidMediaMode.FOLLOW, AndroidMediaMode.values())
    /** Update 212: one Android game's own choice: "gameKey=DISC;gameKey=CARTRIDGE" (none: the setting above). */
    val androidMediaGames = StringPref("android_media_games_212", "")
    fun overlayPartsFor(layout: com.shelf.launcher.data.HomeLayout): List<String> =
        overlayParts.state.value.split(';').firstNotNullOfOrNull { p ->
            val i = p.indexOf('='); if (i > 0 && p.substring(0, i) == layout.name) p.substring(i + 1).split(',').filter { it.isNotBlank() } else null
        }.orEmpty()
    fun toggleOverlayPart(layout: com.shelf.launcher.data.HomeLayout, id: String) {
        val m = overlayParts.value.split(';').mapNotNull { p -> val i = p.indexOf('='); if (i > 0) p.substring(0, i) to p.substring(i + 1) else null }
            .toMap().toMutableMap()
        val now = m[layout.name].orEmpty().split(',').filter { it.isNotBlank() }
        val next = if (id in now) now - id else now + id
        if (next.isEmpty()) m.remove(layout.name) else m[layout.name] = next.joinToString(",")
        overlayParts.value = m.entries.joinToString(";") { "${it.key}=${it.value}" }
    }
    /**
     * Games across the screen per home layout (update 90): "CLASSIC=6;TILES=5;GRID=7". Missing / 0 = automatic (from
     * Card size). Only the Voltcap row, Tile home and Grid use it; nothing else is sized from it.
     */
    val rowGames = StringPref("row_games", "")
    fun rowGamesFor(layout: com.shelf.launcher.data.HomeLayout): Int =
        rowGames.state.value.split(';').firstNotNullOfOrNull { p ->
            val i = p.indexOf('='); if (i > 0 && p.substring(0, i) == layout.name) p.substring(i + 1).toIntOrNull() else null
        }?.coerceIn(0, 12) ?: 0
    fun setRowGames(layout: com.shelf.launcher.data.HomeLayout, n: Int) {
        val m = rowGames.value.split(';').mapNotNull { p -> val i = p.indexOf('='); if (i > 0) p.substring(0, i) to p.substring(i + 1) else null }
            .toMap().toMutableMap()
        if (n <= 0) m.remove(layout.name) else m[layout.name] = n.toString()
        rowGames.value = m.entries.joinToString(";") { "${it.key}=${it.value}" }
    }
    /** Over the game's art when it's the background (update 85): console-style shading (default), dark corners, light corners, off. */
    enum class HeroShade(val label: String) { CONSOLE("Console style"), CORNERS("Dark corners"), LIGHT("Light corners"), OFF("Off") }
    val heroShade = EnumPref("hero_shade", HeroShade.CONSOLE, HeroShade.values())
    val heroShadeStrength = IntPref("hero_shade_strength", 60, 0..100)
    /** The home game header's parts switched off (title, continue, the stat boxes, Play, the round buttons, source). */
    val homeHeaderHidden = SetPref("home_header_hidden", emptySet())

    // ---- Hero carousel ----
    enum class HeroCardStyle(val label: String) {
        ROUNDED("Rounded covers"),
        COVER("Covers"),
        WIDE("Wide capsules"),
        FLAT("Flat cards / list")
    }
    val heroCardStyle = EnumPref("hero_card_style", HeroCardStyle.ROUNDED, HeroCardStyle.values())
    /** How the carousel moves between games (the current glide stays the default). */
    enum class HeroCarouselMotion(val label: String) {
        GLIDE("Glide (default)"),
        SLIDE("Straight slide"),
        FADE("Cross-fade"),
        ZOOM("Zoom"),
        FLIP("Flip")
    }
    val heroCarouselMotion = EnumPref("hero_carousel_motion", HeroCarouselMotion.GLIDE, HeroCarouselMotion.values())
    /** Hero carousel: the column of covers on the right (the info then goes left). */
    val heroCoversRight = BoolPref("hero_covers_right", false)
    /** Update 112: the covers beside the hero — the whole arc, only the game you're on, or none (just the hero and its info). */
    enum class HeroSideCovers(val label: String) { ALL("The whole column"), FOCUSED("Only the game you're on"), NONE("None") }
    val heroSideCovers = EnumPref("hero_side_covers", HeroSideCovers.ALL, HeroSideCovers.values())
    /**
     * (Update 237, #30: the Voltcap row's "no tiles" look, like [heroSideCovers]: the other games in the row — all of
     * them (the row as it always was), only the game you're on, or none (just its name, info and background art; ◀ ▶
     * and swiping still move through the games). GameDock.kt.)
     */
    enum class DockOtherGames(val label: String) { ALL("All"), FOCUSED("Only the game you're on"), NONE("None") }
    val dockOtherGames = EnumPref("dock_other_games", DockOtherGames.ALL, DockOtherGames.values())

    // ---- Save Vault ----
    enum class SaveVaultStyle(val label: String) {
        CLASSIC("Classic squares"),
        CAPSULE("Capsule / pill"),
        SD_CARD("SD card"),
        MEMORY_CARD("Memory card"),
        FLOPPY("Floppy disk"),
        /** Update 136: your 3D shape library's memory card or SD card, the game's cover on its label ("3D models" pack). */
        CARD_3D("3D card"),
        /**
         * Update 285 (Toni: "the logos without the cards"): no object — each save as its game's icon on its own: a PS2
         * save's own 3D icon from its memory card, else the RetroAchievements icon, else the SteamGridDB logo.
         */
        ICONS("Icons only")
    }
    val saveVaultStyle = EnumPref("save_vault_style", SaveVaultStyle.CLASSIC, SaveVaultStyle.values())
    /** Update 136: the 3D card's model (SD card or a memory card colour) and whether it's the clear one. */
    val saveVaultCard = EnumPref("save_vault_card", com.shelf.launcher.ui.ModelSpare.SD, com.shelf.launcher.ui.ModelSpare.values())
    val saveVaultClear = BoolPref("save_vault_clear", false)
    /** Insert / load / eject / crack-open animations. */
    val saveVaultAnimations = BoolPref("save_vault_animations", true)
    enum class SaveVaultBackdrop(val label: String) {
        THEME("Theme background"),
        PLAIN("Plain dark"),
        GAME_ART("Blurred game art"),
        FLOATING("Floating save icons")
    }
    val saveVaultBackdrop = EnumPref("save_vault_backdrop", SaveVaultBackdrop.THEME, SaveVaultBackdrop.values())
    enum class SaveVaultFilter(val label: String) { ALL("All"), ON_DEVICE("Games on this device"), NOT_ON_DEVICE("Games not on this device") }
    val saveVaultFilter = EnumPref("save_vault_filter", SaveVaultFilter.ALL, SaveVaultFilter.values())

    // ---- RetroAchievements overlay (in-game) ----
    /** The achievement you're working on, pinned to the in-game overlay: "gameId:achievementId" (empty = none). */
    val raPinned = StringPref("ra_pinned", "")
    /** The pinned achievement shows as a compact card in the in-game overlay (the other contents are in PerfConfig). */
    val raShowPinned = BoolPref("ra_show_pinned", true)

    // ---- Achievements inside the normal backdrop (no separate achievement theme) ----
    enum class BackdropAchievement(val label: String) {
        BADGES("Recent unlocked badges"),
        PROGRESS("Progress & percentage"),
        PARTICLES("Subtle trophy particles")
    }
    /** Achievement touches drawn into the home backdrop (names of [BackdropAchievement]); empty = none. */
    val backdropAchievements = SetPref("backdrop_achievements")

    // ---- Volt help ----
    /** Small "?" Volt help on complex pages (Save Vault, theme colours, RetroAchievements, TDP). */
    val voltHelp = BoolPref("volt_help", true)

    // ---- Controller lights ----
    /**
     * Voltcap holds the lights dark (Lights › Off). Different from "System" (lightsEnabled off), which
     * leaves them to the handheld's own setting: on the AYN Thor that one comes back on its own.
     */
    val lightsHeldOff = BoolPref("lights_held_off", false)

    // ---- Where "follow the art" colours come from ----
    /**
     * When something takes its colours from artwork (the game-coloured app, scene colours, controller lights
     * on Theme colour): the game you're on, the album cover playing, or automatic (album while music plays
     * and the theme is a music one, else the game).
     */
    enum class ArtColorSource(val label: String) { AUTO("Automatic"), GAME("Game art"), ALBUM("Album cover") }
    val artColorSource = EnumPref("art_color_source", ArtColorSource.AUTO, ArtColorSource.values())
    /**
     * (Update 240, #23) While a theme's colours follow the game / the music: keep the accent you picked yourself (Colours &
     * look › Custom › Accent; the theme's own accent when you never picked one) — only the background and panels follow
     * the art. Off: the art's accent, as before.
     */
    val artKeepAccent = BoolPref("art_keep_accent", false)

    // ---- Music themes: what sits under the visualizer ----
    val musicShowControls = BoolPref("music_show_controls", true)
    val musicShowTitle = BoolPref("music_show_title", true)
    val musicShowLyrics = BoolPref("music_show_lyrics", true)

    // ---- Corner visualizers (small space scenes that move with the music) ----
    enum class CornerViz(val label: String) {
        OFF("Off"), PLANET("Ringed planet"), ORBITS("Orbiting moons"), PULSAR("Pulsar"), BLACK_HOLE("Black hole"), NEBULA("Nebula bloom")
    }
    val cornerViz = EnumPref("corner_viz", CornerViz.OFF, CornerViz.values())
    enum class VizCorner(val label: String) { TOP_LEFT("Top left"), TOP_RIGHT("Top right"), BOTTOM_LEFT("Bottom left"), BOTTOM_RIGHT("Bottom right"), BOTH_TOP("Both top corners") }
    val cornerVizCorner = EnumPref("corner_viz_corner", VizCorner.TOP_RIGHT, VizCorner.values())
    /** Size in dp of each corner visualizer. */
    val cornerVizSize = IntPref("corner_viz_size", 120, 70..220)
    /** Only while music plays (else always, idling slowly). */
    val cornerVizOnlyMusic = BoolPref("corner_viz_only_music_v2", false)

    // ---- Console-era music scenes (Theme › Music · console eras) ----
    /** What their screen / cartridge / banner shows: the album cover, the game you're on, or the game's art when no music plays. */
    enum class EraArt(val label: String) { AUTO("Game art when no music plays"), GAME("Always the game's art"), ALBUM("Album cover only") }
    val eraArt = EnumPref("era_art", EraArt.AUTO, EraArt.values())

    // ---- Round shortcuts (the tile layout's shortcut bar) ----
    /** Size of the round shortcuts, % of normal. */
    val shortcutScale = IntPref("shortcut_scale", 100, 60..160)
    /** Shortcut ids hidden from the round bar (still in L1). */
    val shortcutHidden = SetPref("shortcut_hidden")
    /** App icons in the round shortcuts drawn in the theme's colours (monochrome, tinted). */
    val shortcutThemedIcons = BoolPref("shortcut_themed_icons", false)
    /**
     * Update 319 (Toni: "an icon pack in one colour so it follows the theme's colours", no square plate with a blur behind):
     * the second screen's dock shows each app as its one-colour symbol in the theme's accent — the app's own monochrome
     * icon (Android 13+) where it has one, else its icon's foreground drawn in the accent's shades — on nothing. Off: the
     * apps' own colour icons, still without the plate.
     */
    val secondDockMono = BoolPref("second_dock_mono", true)
    /**
     * Update 324 (Toni: "with Endless on the games never reach the edges when there aren't many"): Endless fills the row
     * past both edges with the games again (on), or shows each game once (off, update 226's way — the row stops short when
     * the category has few games). Hold the R1 Endless tile.
     */
    val endlessFill = BoolPref("endless_fill", true)
    /** Update 321: how the one-colour icons are drawn — the app's drawing kept see-through (DETAIL), or one solid shape. */
    enum class MonoIconStyle(val label: String) { DETAIL("Detail"), SOLID("Solid"), ORIGINAL("Own colours") }
    val monoIconStyle = EnumPref("mono_icon_style", MonoIconStyle.DETAIL, arrayOf(MonoIconStyle.DETAIL, MonoIconStyle.SOLID))

    // ---- Last played on every device ----
    /** Share "last played" through Google Drive so a game played on another device rises to the top here too. */
    val syncLastPlayed = BoolPref("sync_last_played", true)

    // ---- Overlays over games: free positions ----
    /**
     * Overlays dragged to a free spot (not a corner): "stats=x,y;music=x,y;ra=x,y", each the window's top-left
     * as a fraction (0..1) of the room left on screen. Missing = the overlay sits in its corner.
     */
    val overlayPositions = StringPref("overlay_positions", "")
    /** Overlays folded away to a small arrow tab (update 121): "stats", "music", "ra". */
    val overlayFolded = SetPref("overlay_folded")
    /** Each overlay's own size from a corner drag, times Overlay size: "stats=1.2;music=0.8". Missing = 1. */
    val overlaySizes = StringPref("overlay_sizes", "")
    /** The small fold arrow under each overlay (the folded tab always shows). */
    val overlayArrows = BoolPref("overlay_arrows", true)

    // ---- Lighter backgrounds (update 121) ----
    /** The shader scenes drawn at most at half the screen's resolution. */
    val sceneHalfRes = BoolPref("scene_half_res", true)
    /** The second screen's background at ~15 fps while nobody touches it. */
    /**
     * Update 125: the frame rate of the animated backgrounds, for the whole app. Automatic: the battery-saving steps
     * (calmer when nobody touches the home screen, slower under a popup, Background quality's own rate). 60 / 30: that
     * rate all the time, never lowered by the battery-saving steps.
     */
    enum class SceneFps(val label: String, val frameMs: Int) { AUTO("Automatic", 0), FPS60("60 fps", 14), FPS30("30 fps", 28) }
    val sceneFps = EnumPref("scene_fps", SceneFps.AUTO, SceneFps.values())
    val secondSlowWhenUnused = BoolPref("second_slow_unused", true)
    /** Update 189: the second screen's look — the game's art with floating pills and dock (SecondShowcase.kt), or classic. */
    enum class SecondLook(val label: String) { SHOWCASE("Game art, floating bars"), CLASSIC("Classic: tabs and bar") }
    val secondLook = EnumPref("second_look", SecondLook.SHOWCASE, SecondLook.values())
    /** Update 188: the second screen dims after this many seconds unused (0 = never; ui/SecondDim.kt). */
    val secondDimAfter = IntPref("second_dim_after", 60, 0..900)
    /** Update 188: how dark it goes, % black (100 = black). */
    /** (Update 189: a new key — part-way dark by default, the screen stays usable; 30 / 45 / 60.) */
    val secondDimLevel = IntPref("second_dim_level2", 45, 10..90)
    /** Update 188: its backlight turned down too while dimmed. */
    val secondDimBacklight = BoolPref("second_dim_backlight", true)

    // ---- Save Vault ----
    /** The save grid wraps round: scrolling past the end starts again at the beginning. */
    val saveVaultEndless = BoolPref("save_vault_endless", true)

    // ---- Interface style (update 24) ----
    /** Volt: Voltcap's own look (glass panels, accent focus glow, voltage lines). Classic: the look before. */
    enum class UiStyle(val label: String) {
        VOLT("Glass"), CAPSULE("Energy capsule"), PIXEL("32-bit console"), MEMORY_CARD("Memory card"),
        CRT("CRT"), NEON("Neon sign"), CARTRIDGE("Cartridge"), CLASSIC("Classic")
    }
    val uiStyle = EnumPref("ui_style", UiStyle.VOLT, UiStyle.values())
    /** The spark that runs along the voltage lines (off: still lines). */
    val voltLine = BoolPref("volt_line", true)
    /** The soft glow inside the focus outline. */
    val uiGlow = BoolPref("ui_glow", true)
    /** A soft light in the colours of the game you're on, behind its card. */
    val coverLight = BoolPref("cover_light", true)
    /** Status bar battery as a little charge gauge in the theme's colours (Volt looks). */
    val voltBattery = BoolPref("volt_battery", true)
    /** A light blur (and soft shade) behind just the games, their names and the categories, 0 = off. */
    val rowBlur = IntPref("row_blur", 35, 0..100)
    /** How soft the shadow / glow round the game's name and the category is, % of the style's own (0 = crisp). */
    val nameBlur = IntPref("name_blur", 100, 0..100)
    /**
     * Update 112: names over busy pictures — the soft shadow, a stronger dark edge round the letters, or a dark pill
     * behind the Icon bar's small name.
     */
    enum class NameContrast(val label: String) { SOFT("Soft shadow"), STRONG("Dark edge"), PILL("Dark pill") }
    val nameContrast = EnumPref("name_contrast", NameContrast.PILL, NameContrast.values())
    /** TV room layout: how the games beside the TV look. */
    val tvRoomGames = EnumPref("tv_room_games", com.shelf.launcher.ui.TvRoom.Games.SPINES, com.shelf.launcher.ui.TvRoom.Games.values())
    /** Home layouts with the "Carousel look" (HomeLayout names: row, tiles, grid, Hero carousel). */
    val carouselLayouts = SetPref("carousel_layouts")
    /** How every carousel lays its games out (update 104). */
    val carouselView = EnumPref("carousel_view", com.shelf.launcher.ui.CarouselView.TUCKED, com.shelf.launcher.ui.CarouselView.values())
    /** Space between the carousel's games, 60–160 % (update 104). */
    val carouselGap = IntPref("carousel_gap", 100, 60..160)
    /** Update 185: a short list spread out wider (Carousel.spread). Off: the Spacing alone decides the gap. */
    val carouselSpread = BoolPref("carousel_spread", true)
    /** A faint reflection under each game of the carousel (update 104). */
    val carouselReflect = BoolPref("carousel_reflect", false)
    /** Update 147: the carousel on a dark floor fading into black (on with the 3D case views). */
    val carouselStage = BoolPref("carousel_stage", false)
    /** Update 187: the dark stage tinted by the colours of the game you're on (off: your colour). */
    val carouselStageGameColor = BoolPref("carousel_stage_game_color", true)
    /** Update 187: Box flow — the box you're on sways a little as the carousel moves. */
    val boxFlowTilt = BoolPref("box_flow_tilt", true)
    /** Update 148: animated covers move only on the game you're on (the others show their first frame). Off: every game.
     *  Update 181: on by default (a new key, so it starts on for everyone once), and followed by every layout (AnimatedArt.Gate). */
    val animatedFocusOnly = BoolPref("animated_focus_only2", true)
    /** Update 148: the preview clips saved on the device (ClipStore), up to [clipsMaxMb]. */
    val clipsOffline = BoolPref("clips_offline", false)
    val clipsMaxMb = IntPref("clips_max_mb", 1024, 100..8000)
    /** Update 149: the automatic Drive backup (AutoBackupWorker): how often, with accounts or not, when the last one ran. */
    val autoBackup = EnumPref("auto_backup", AutoBackupWorker.Every.WEEKLY, AutoBackupWorker.Every.values())
    val autoBackupAccounts = BoolPref("auto_backup_accounts", true)
    val autoBackupAt = StringPref("auto_backup_at", "")
    /** Update 134: every carousel game drawn as a real 3D case (cover, spine, edge, lid), whatever the view (always in Arc, Spine wall and Turned row). */
    val carousel3d = BoolPref("carousel_3d", false)
    /**
     * Carousel (TV room and every "Carousel look"): how many games show each side of the one in the middle (1–3;
     * update 113: up to 5, also on the R1 panel's Carousel tile — hold it).
     */
    val tvCarouselSide = IntPref("tv_carousel_side", 2, 1..5)
    /** Update 119: the carousel shows games all the way to the screen's edges (instead of [tvCarouselSide] each side). */
    // (Update 129: on by default — the games reach the screen's edges.)
    val carouselEdge = BoolPref("carousel_edge", true)
    /** Update 129: how big every carousel's games are, % (the Voltcap row's carousel 100 % = the row's own size). */
    val carouselSize = IntPref("carousel_size", 130, 60..200)
    /** Carousel: the games aside turned away from the middle one. */
    val tvCarouselTilt = BoolPref("tv_carousel_tilt", true)
    /** TV room (update 107): the game's info on the floor under the set instead of under the games. */
    val tvInfoUnder = BoolPref("tv_info_under", true)
    /** TV room (update 107): recently unlocked achievement badges on the wall left of the set. */
    val tvRecentBadges = BoolPref("tv_recent_badges", true)
    /** Carousel tilt: turned away from the middle one (outward, like pages) or towards it (inward, like a wall). */
    val tvCarouselTiltDir = EnumPref("tv_carousel_tilt_dir", com.shelf.launcher.ui.TiltDir.OUT, com.shelf.launcher.ui.TiltDir.values())
    /** Carousel tilt: both sides, or only the games on the left / on the right of the middle one. */
    val tvCarouselTiltSides = EnumPref("tv_carousel_tilt_sides", com.shelf.launcher.ui.TiltSides.BOTH, com.shelf.launcher.ui.TiltSides.values())
    /** Lists mixing consoles (Recently played, favourites…): what every game is drawn as - basic by default, so no card is bigger or smaller. */
    val mixedArt = EnumPref("mixed_art", com.shelf.launcher.ui.MixedArt.BASIC, com.shelf.launcher.ui.MixedArt.values())
    /** The games slide in from the side when the category changes. */
    val categorySlide = BoolPref("category_slide", true)

    /** Box art as the older 3D glass box from the template pack (off: the official flat case with its cover frame and spine). */
    val box3d = BoolPref("box_3d", false)
    /**
     * Box cards show the pack's 3D box turned a little to the side, so its real spine shows. Off (default): the front
     * alone, facing you — a spine is never drawn beside a flat front. (New key: the old "box_spine" put the spine next
     * to the flat front, which is gone.)
     */
    val boxSpine = BoolPref("box_spine_side", false)
    /**
     * Update 280 (Toni): Game look › Layout standard — each home layout draws its games in its own standard look (the
     * look of its first preset, e.g. the Voltcap row's covers with the wide capsule, the TV room's cases); switching
     * layout switches the look with it. Picking any other look turns it off.
     */
    val lookFromLayout = BoolPref("look_from_layout", false)
    /** Covers go into each system's own cover template (printed border, real proportions) and its spine; off: plain covers. */
    val coverTemplates = BoolPref("cover_templates", true)
    /** Disc games: the case with the disc half out of it, spinning. */
    val discCase = BoolPref("disc_case", false)
    /**
     * Update 151: "3D case + disc" (Settings › Game look › Cases with a disc): with Disc in its case, the case is the 3D box
     * (every console alike) with the disc half out of it. Off by default.
     */
    val discCase3d = BoolPref("disc_case_3d", false)
    /** Box art (update 93): the case with its cartridge / disc leaning against it; only the cartridge / disc moves. */
    val caseWithMedia = BoolPref("case_with_media", false)
    /**
     * Box art and the Game shelf (update 101): the game's own scanned case — its front scan (or the front cut from the
     * full box scan) as the case, and its real spine (ScreenScraper's spine scan; the strip cut from a full scan only with
     * [spineCuts]) on the shelf and the turned box. Off: Voltcap's case templates with the cover in them, the console's spine.
     */
    val originalCases = BoolPref("original_cases", true)
    /**
     * Update 147: every game's spine is Voltcap's own spine of its console with the game's name (your spine pack), so
     * they all match — the real spine scans (each in its own style and size) are left out.
     */
    val spinesOwn = BoolPref("spines_own", false)
    /**
     * Update 169: Voltcap's own Steam / Android cases take the shape of the game's cover (SteamGridDB's tall 2:3 grid, a
     * square icon-like cover…): the band stays as it is, the window grows to the cover, so it fills it — no bars beside it.
     */
    val ownCaseFit = BoolPref("own_case_fit", true)
    /** Update 169: the consoles' cover templates take the cover's shape too (off: they keep the real case's shape). */
    val allCaseFit = BoolPref("all_case_fit", false)
    /**
     * Update 170: the 3D cases' backs printed like real ones when the game has no back scan: the console's back cover from
     * the template pack (barcode, seals, rating box) over the game's art, with its screenshots and description.
     */
    val printedBacks = BoolPref("printed_backs", true)
    /**
     * Update 305: the age rating a generated case back prints — only a rating body's rating found for that very game
     * (Steam's store record for its app id, its ScreenScraper record; PackagingFacts), as plain text ("USK 16"), never
     * guessed. USK by default (Germany); with [backRatingOthers] another body's is printed when the chosen one has none.
     */
    enum class BackRating(val label: String, val system: String?) {
        USK("USK (Germany)", "USK"), PEGI("PEGI (Europe)", "PEGI"), ESRB("ESRB (North America)", "ESRB"), OFF("None", null)
    }
    val backRating = EnumPref("back_rating", BackRating.USK, BackRating.values())
    val backRatingOthers = BoolPref("back_rating_others", true)
    /**
     * Update 305: a cardboard box's right side, top and bottom (Case3D). FILL: a real scan of that side, else your own
     * picture of it, else one printed from the game's own title, logo and colours. AUTHENTIC: only pictures marked as a
     * real packaging scan (no scraper has those sides, so mostly your own scans), else plain card. PLAIN: plain card.
     */
    enum class BoxSides(val label: String) {
        FILL("Fill missing sides"), AUTHENTIC("Authentic only"), PLAIN("Plain cardboard")
    }
    val boxSides = EnumPref("box_sides", BoxSides.FILL, BoxSides.values())
    /**
     * Update 320 (Toni: "the manual downloads only when I press it on the case — can they all be downloaded ahead if I
     * choose so, and if not just a preview, viewed that time without downloading"). PREVIEW: a manual is fetched only to
     * read it, into a temporary file deleted when it's closed — nothing kept. AHEAD: every manual found is downloaded onto
     * the device in the background (and one opened is kept), for reading offline.
     */
    enum class ManualKeep(val label: String) { PREVIEW("Preview only — nothing kept"), AHEAD("Download all ahead") }
    val manualKeep = EnumPref("manual_keep", ManualKeep.PREVIEW, ManualKeep.values())
    /**
     * Update 319 (Toni): with the printed sides off (Authentic only / Plain cardboard), the sides with no picture of their
     * own named here ("RIGHT", "TOP", "BOTTOM" — BoxSide) take the colour of the cover's edge that meets them, as the
     * printed card of a real box carries on round its corners; the others stay plain card.
     */
    val boxSideColors = SetPref("box_side_colors", setOf("RIGHT", "TOP", "BOTTOM"))
    /**
     * Update 191: which region's case a game gets (ui/CaseCatalog.kt) when its file name doesn't say ("(USA)", "(Europe)",
     * "(Japan)"), or one region for every game.
     */
    enum class CaseRegionChoice(val label: String, val code: String, val fixed: Boolean) {
        AUTO_US("From the file name, else North America", "US", false),
        AUTO_EU("From the file name, else Europe", "EU", false),
        AUTO_JP("From the file name, else Japan", "JP", false),
        US("North America for all", "US", true),
        EU("Europe / PAL for all", "EU", true),
        JP("Japan for all", "JP", true)
    }
    val caseRegion = EnumPref("case_region", CaseRegionChoice.AUTO_US, CaseRegionChoice.values())
    /** Update 195: the cartridge shell picked per game / per console ("key\tchoice" lines; ui/CartShells.kt). */
    val cartShellChoices = StringPref("cart_shell_choices", "")
    /** Update 287: the DS case picked for a game (DsCases): "key\tVARIANT" lines. */
    val dsCaseChoices = StringPref("ds_case_choices", "")
    /**
     * (Update 236, #42/#43/#47: each Save Vault object's colour, per save or per console — "key\tvalue" lines, keys
     * "g:<stableKey>" / "console:<platform>", values "p=<preset>;shell=#RRGGBB;trim=#RRGGBB"; ui/SaveVaultLooks.kt.)
     */
    val saveObjectLooks = StringPref("save_object_looks", "")
    /**
     * (Update 241, #44/#46: "SaveVault" and Voltcap's cloud moulded into every Save Vault object's plastic — the front's
     * blank area where it has one, always its back; ui/Engraving.kt. On by default.)
     */
    val saveEngraving = BoolPref("save_engraving", true)
    /** (Update 241, #49: Save Vault › a save › Inspect turns the object by itself — off by default; ui/SaveInspect.kt.) */
    val saveInspectAutoRotate = BoolPref("save_inspect_auto_rotate", false)
    /** Update 195: every cartridge console on its own drawn authentic shell (Settings › Game look). */
    val authenticCarts = BoolPref("authentic_carts", false)
    /**
     * Update 210: games whose real cartridge came in its own plastic or shell (the gold Zelda, Pokémon Crystal's clear
     * cyan, the DS infrared cards…) are drawn on it by themselves when the file name says which release it is
     * (ui/MediaEditions.kt). Off: every game on its console's standard shell unless you pick one.
     */
    val realEditions = BoolPref("real_editions", true)
    /**
     * Update 200: what a drawn cartridge's back shows when it's turned round: its maker's moulded text and stickers, or
     * the same shell without them (no console branding) — by default the markings only on a game's own console's shell.
     */
    enum class CartBackStyle(val label: String) { AUTO("Markings on its own console's shell"), AUTHENTIC("Always the maker's markings"), NEUTRAL("Plain, no markings") }
    val cartBackStyle = EnumPref("cart_back_style", CartBackStyle.AUTO, CartBackStyle.values())
    /** Update 198: the second screen's Details page shows the game's case in 3D, opening on a tap (MediaInspectView.kt). */
    val secondOpenCase = BoolPref("second_open_case", true)
    /** Update 198: an opening case starts opened — the case aside, its disc / cartridge out next to it. */
    val inspectOpenAtStart = BoolPref("inspect_open_at_start", false)
    // (Update 238, #116/#117: a piece turned in the game window stays at its angle for a while after you let go, then
    // turns back slowly to face you — or stays where you left it. ui/MediaInspectView.kt, InspectMotion.release.)
    /** Update 238 (#116): a turned piece turns back to face you by itself once you let go. Off: it stays as you left it. */
    val inspectAutoReturn = BoolPref("inspect_auto_return", true)
    enum class InspectHold(val label: String, val ms: Long) { S1("1 s", 1_000), S2("2 s", 2_000), S3("3 s", 3_000), S5("5 s", 5_000), S10("10 s", 10_000) }
    /** Update 238 (#116): how long it stays at its angle before turning back. */
    val inspectHold = EnumPref("inspect_hold", InspectHold.S2, InspectHold.values())
    enum class InspectReturnSpeed(val label: String, val ms: Int) { SLOW("Slow", 2_500), NORMAL("Normal", 1_200), QUICK("Quick", 600) }
    /** Update 238 (#116): how long its turn back takes. */
    val inspectReturnSpeed = EnumPref("inspect_return_speed", InspectReturnSpeed.NORMAL, InspectReturnSpeed.values())
    /**
     * Update 274 (Toni): what the cartridge / disc / case does once it's out in the game window. Inspect (default): turn
     * and tilt it by hand (drag, the right stick), it turns back after the hold. Wobble: no turning by hand — it just
     * sways gently left and right, as the pieces on the home rows do. Asked once after this update (InspectChoice.kt).
     */
    enum class InspectMode(val label: String) { INSPECT("Inspect (turn it by hand)"), WOBBLE("Wobble (it just sways)") }
    val inspectMode = EnumPref("inspect_mode", InspectMode.INSPECT, InspectMode.values())
    /** Update 274 (Toni): a piece left alone (after its turn back) sways gently, as on the home rows. On by default. */
    val inspectSway = BoolPref("inspect_sway", true)
    /** Update 274: the Inspect / Wobble question has been answered (asked once, after this update). */
    val inspectModeAsked = BoolPref("inspect_mode_asked_274", false)
    /** Update 147, Disc station: the loaded disc turns slowly. */
    val discSpin = BoolPref("disc_spin", true)
    /** Update 147, Disc station: a few lines about the game under its name. */
    val discAbout = BoolPref("disc_about", true)
    /** Update 149, Disc station: how much the game's art behind the disc is blurred (% of the player's width). */
    val discBlur = IntPref("disc_blur", 5, 0..30)
    /** Update 154: what the disc sits in — a drawn portable CD player (default) or the game's art, blurred. */
    enum class DiscPlayerLook(val label: String) { PLAYER("Portable CD player"), ART("The game's art") }
    val discPlayer = EnumPref("disc_player", DiscPlayerLook.PLAYER, DiscPlayerLook.values())
    // Update 158: the two-screen layouts (TwinDesk.kt / TwinIcons.kt): Twin desk's clock and calendar, Twin icons' icons per row.
    enum class TwinClock(val label: String) { ANALOGUE("Analogue"), DIGITAL("Digital") }
    val twinClock = EnumPref("twin_clock", TwinClock.ANALOGUE, TwinClock.values())
    val twinCalendar = BoolPref("twin_calendar", true)
    /** Update 180: what Twin desk's top shows besides the calendar: the clock, the game's picture, the game's name. */
    val twinDeskClock = BoolPref("twin_desk_clock", true)
    val twinDeskArt = BoolPref("twin_desk_art", false)
    val twinDeskGame = BoolPref("twin_desk_game", true)
    /**
     * Update 243 (#55): Twin desk's optional extras on the game you're on — the ▶ icon at the panel's end and the four
     * corner handles round it. Off for new setups; an existing install keeps them on (migrate243) until switched off.
     */
    val twinDeskPlayIcon = BoolPref("twin_desk_play_icon", false)
    val twinDeskHandles = BoolPref("twin_desk_handles", false)
    /** Update 180: on two screens, the two-screen layouts fill the whole top screen with their own background (theme off). */
    val twinFullTop = BoolPref("twin_full_top", true)
    /** Update 310: allow navigation to the regular second-screen dashboard from twin layouts. */
    val twinDashboardAccess = BoolPref("twin_dashboard_access", true)
    /** Update 310: optionally show standard selectable categories above the twin layout\'s lower screen. */
    val twinSharedCategories = BoolPref("twin_shared_categories", false)
    // ---- Update 181: the two Pocket layouts (PocketClassic.kt, PocketRibbon.kt) ----
    enum class PocketAnim(val label: String) { NORMAL("Normal"), FAST("Fast"), OFF("Off") }
    enum class PocketArrange(val label: String) { AUTO("Two screens when there are"), STACKED("One above the other"), SIDE("Side by side") }
    enum class PocketArt(val label: String) { COVER("Game look (cover, case, cartridge…)"), LOGO("Logo"), SHOT("Screenshot") }
    enum class RibbonTop(val label: String) { GAME("Game cover"), ALBUM("Album art"), PHOTO("Your photo"), SLIDES("Screenshots slideshow") }
    /** The user name on the top screens ("" = the RetroAchievements name, else "Player"). */
    val pocketName = StringPref("pocket_name", "")
    val pocketAnim = EnumPref("pocket_anim", PocketAnim.NORMAL, PocketAnim.values())
    val pocketArrange = EnumPref("pocket_arrange", PocketArrange.AUTO, PocketArrange.values())
    /** Pixel-perfect (whole pixels per dot, 4:3) or fit the screen (as big as fits, wider when there's room). */
    /** (Update 182: off by default — as big as fits; whole pixels per dot left the Thor's screens half empty.) */
    val pocketPixel = BoolPref("pocket_pixel2", false)
    /** Update 182: the home strip under both Pocket menus (your shortcuts, the music, the time and battery). */
    val pocketStrip = BoolPref("pocket_strip", true)
    val pocketSounds = BoolPref("pocket_sounds", true)
    /** The category a Pocket layout opens on ("" = where you were). */
    val pocketStartCategory = StringPref("pocket_start_category", "")
    // Pocket classic.
    val classicAccent = StringPref("classic_accent", "BLUE")
    val classicAnalogue = BoolPref("classic_analogue", true)
    val classicCalendar = BoolPref("classic_calendar", true)
    val classicToolbar = BoolPref("classic_toolbar", true)
    val classicArt = EnumPref("classic_art", PocketArt.COVER, PocketArt.values())
    /** Update 187: while you browse a page of games or categories, the top screen shows it instead of the clock. */
    val classicTopArt = BoolPref("classic_top_art", true)
    // Pocket ribbon.
    val ribbonAccent = StringPref("ribbon_accent", "SKY")
    val ribbonTop = EnumPref("ribbon_top", RibbonTop.GAME, RibbonTop.values())
    val ribbonStatus = BoolPref("ribbon_status", true)
    val ribbonSlides = BoolPref("ribbon_slides", true)
    val ribbonNames = BoolPref("ribbon_names", true)
    /** Update 187: a soft glow round the icon you're on. */
    val ribbonGlow = BoolPref("ribbon_glow", true)
    /**
     * Update 325 (Toni: "in Pocket Ribbon, in front of the hero make the cartridge appear by default with its animation,
     * and instead of the hero the cover art from SteamGridDB, as in the tiles"): the game's cartridge (or disc) standing
     * in front of its cover on the top screen.
     */
    val ribbonCart = BoolPref("ribbon_cart", true)
    /** Icon size (dots, 24–40) and the gap between icons (dots). */
    /** (Update 182: bigger by default, up to 56.) */
    val ribbonIcon = IntPref("ribbon_icon2", 44, 24..56)
    val ribbonGap = IntPref("ribbon_gap", 10, 2..24)
    /** The top screen's share when both screens are on one display, % (30–70). */
    val ribbonSplit = IntPref("ribbon_split", 50, 30..70)
    /** Open on the categories (true) or on the games of the category you're in. */
    val ribbonStartOnCategories = BoolPref("ribbon_start_categories", false)
    val twinCols = IntPref("twin_cols", 5, 4..7)
    /** Update 309: Twin icons' colours — dark top / light bottom as before, or both screens dark, or both light. */
    enum class TwinTone(val label: String) { MIX("Mixed (dark top, light bottom)"), DARK("Dark"), LIGHT("Light") }
    val twinIconsTone = EnumPref("twin_icons_tone", TwinTone.MIX, TwinTone.values())
    /** Update 307: Twin showcase's rows of icons (1–3); more games run on to the right. */
    val twinShowRows = IntPref("twin_show_rows", 2, 1..3)
    /** Update 308: Twin showcase with Voltcap's status bar too (off: the picture's own status capsule). */
    val twinShowStatusBar = BoolPref("twin_show_status_bar", false)
    /** Update 179: the space between the tiles of the two-screen layouts, dp (Twin icons' grid, Twin desk's panels). */
    val twinGap = IntPref("twin_gap", 8, 0..32)
    val twinDeskGap = IntPref("twin_desk_gap", 7, 0..24)
    /** Update 154: Disc station keeps the screen behind it still: no video clip, the animated background holds a frame. */
    val discCalm = BoolPref("disc_calm_bg", true)
    /**
     * Update 123: a game without ScreenScraper's spine scan may use a spine cut out of its full box scan (or the side of
     * its 3D box picture). Off (default): only real spine scans; the others get the console's template spine with the name.
     */
    val spineCuts = BoolPref("spine_cuts", false)
    /**
     * Cartridge / disc art drawn by Voltcap (its own shell + the game's label) instead of the real cartridge / disc picture
     * scraped for the game. Off by default (update 98): the real ones are used, ours only when you choose.
     */
    val mediaShells = BoolPref("media_shells", false)
    /** GameCube discs drawn as big as the 12 cm ones (off: their real, smaller 8 cm size). */
    val bigGcDiscs = BoolPref("big_gc_discs", false)
    /** Game Boy Color cartridges: the clear ones, the black GBC-only ones or the grey dual-mode ones. */
    enum class GbcCart(val label: String) { BLACK("Black"), GREY("Grey"), CLEAR("Clear") }
    val gbcCart = EnumPref("gbc_cart", GbcCart.BLACK, GbcCart.values())

    /** Closing the handheld (or its screen going off): Spotify / any music app and Voltcap's own music pause. */
    val pauseOnClose = BoolPref("pause_on_close", true)
    /** Also pause music when the screen goes off by the power button / timeout (off: only when the lid closes). */
    val pauseOnLock = BoolPref("pause_on_lock", false)
    /** A notification in Android's pull-down with the volume mixer: − / + per app (a real slider can't live in a notification). */
    val mixerNotification = BoolPref("mixer_notification", true)
    /** Grid home: every game as a square, rounded tile (like the Tile home) instead of the card artwork's own shape. */
    val gridTiles = BoolPref("grid_tiles", false)
    /** Grid: how the game you're on stands out — raised a little and turning gently (default), an outline, or the glow. */
    enum class GridFocus(val label: String) { LIFT("Raise & gentle spin"), OUTLINE("Outline"), GLOW("Glow (the focus effect)") }
    val gridFocus = EnumPref("grid_focus", GridFocus.LIFT, GridFocus.values())
    /** Card artwork "Discs only": games from cartridge consoles become a plain CD with their label / cover on it too
     *  (off: they keep their cover, the old behaviour). */
    val discsForAll = BoolPref("discs_for_all", true)
    /** TV room: the game's name under each cartridge / disc / box in the grid. */
    val tvRoomNames = BoolPref("tv_room_names", true)

    // ---- The category screen (CategoryScreen.kt Tiles, PlatformBrowser.kt Slides / Wall / Grid) ----
    /** What every category / platform card shows, in all four looks (Settings › Categories › Category screen).
     *  Replaces Appearance's "Category tiles show" (migrated once in [init]); default: a slow fade through its games. */
    val categoryArt = EnumPref("category_art", CategoryTileArt.SLIDESHOW, CategoryTileArt.values())

    // ---- The style homes (StyleHomes.kt): each layout's own options (Appearance › Game row › <layout> options) ----
    /** How the style homes' panels look: light glass, dark glass, or the theme's own surface. */
    enum class PanelLook(val label: String) { LIGHT("Light glass"), DARK("Dark glass"), THEME("Theme colour") }
    /** Dashboard: the big title top left (empty = hidden) and the small line under it. */
    val dashTitle = StringPref("dash_title", "Voltcap")
    val dashSubtitle = StringPref("dash_subtitle", "Ready to play")
    /** Dashboard: the round-button menu on the left, and its names. */
    val dashMenu = BoolPref("dash_menu", true)
    val dashMenuLabels = BoolPref("dash_menu_labels", true)
    /** Dashboard: how many of the round shortcuts the menu shows (Settings › Round shortcuts picks which / their order). */
    val dashMenuCount = IntPref("dash_menu_count", 6, 2..12)
    /** Dashboard: the "Now playing" card with the game you're on big. */
    val dashHero = BoolPref("dash_hero", true)
    /** Dashboard: the row's title ("" = the category's name). */
    val dashRowTitle = StringPref("dash_row_title", "")
    /** Dashboard: buttons in the Quick access strip (0 = no strip). */
    val dashQuick = IntPref("dash_quick", 5, 0..8)
    /** Dashboard: the player card and the system-status card on the right. */
    val dashProfile = BoolPref("dash_profile", true)
    /** Style homes (Dashboard, Live tiles): the music player built into the layout, bottom right. */
    val styleMusic = BoolPref("style_music", true)
    /** Dashboard (update 125): the album cover in its player (on by default). */
    val dashPlayerArt = BoolPref("dash_player_art", true)
    /** Update 125: how the background picture sits, per layout ("LAYOUT=FILL|WHOLE" entries; Settings › the layout's options). */
    val heroFitByLayout = StringPref("hero_fit_by_layout", "")
    /** Update 125: where the game names go, per layout ("LAYOUT=PLACEMENT,SIDE" entries; the layout's options in Settings). */
    val namesByLayout = StringPref("names_by_layout", "")
    /** Battery watcher: Voltcap measures its own share of the battery use (a light check once a minute). */
    val batteryWatch = BoolPref("battery_watch_on", true)
    /** Update 190: artwork downloads go on while Voltcap isn't on screen (behind a game too; ArtDownloadService). */
    val artInBackground = BoolPref("art_in_background", true)
    /** Update 187: the battery saver (BatterySaver): when the low-battery rest is on (when low / always / off). */
    val saverMode = EnumPref("saver_mode", BatterySaver.Mode.AUTO, BatterySaver.Mode.values())
    /** Update 187: below this level (%), not plugged in, the low-battery rest starts. */
    val saverLowAt = IntPref("saver_low_at", 20, 5..50)
    /**
     * Update 321 (Toni: Voltcap drained 30 % overnight): after this many minutes without a press or a touch, Voltcap rests
     * (HomeIdle.rest): the background clip stops, the theme's animation holds a still frame, on both screens. 0 = never.
     */
    val idleRestMin = IntPref("idle_rest_min", 10, 0..120)
    /** Update 187: Android's own Battery Saver on counts as low battery too. */
    val saverFollowSystem = BoolPref("saver_follow_system", true)
    /** Update 187: Voltcap rests while a game or another app has the controller (two-screen handhelds). */
    val saverAway = BoolPref("saver_away", true)
    /** Ask "Who is playing?" at start-up when two RetroAchievements accounts are saved. */
    val whoPlays = BoolPref("who_plays", false)
    val dashStatus = BoolPref("dash_status", true)
    /** Dashboard: panel look, opacity (%), and cut (angled) corners instead of round ones. */
    val dashPanel = EnumPref("dash_panel", PanelLook.LIGHT, PanelLook.values())
    val dashOpacity = IntPref("dash_opacity", 62, 15..100)
    val dashCut = BoolPref("dash_cut", true)

    /** Live tiles: the category names across the top. */
    val liveTabs = BoolPref("live_tabs", true)
    /** Live tiles: the games, a page at a time: one big tile and four, one big and two, or six even tiles. */
    enum class LivePage(val label: String, val size: Int) { BIG_FOUR("1 big + 4", 5), BIG_TWO("1 big + 2", 3), SIX("6 even", 6) }
    val livePage = EnumPref("live_page", LivePage.BIG_FOUR, LivePage.values())
    /** Live tiles: coloured shortcut tiles under the games (0 = none). */
    val liveBar = IntPref("live_bar", 4, 0..6)
    /** Live tiles: the colour of the first shortcut tile and the selection. */
    enum class LiveColor(val label: String) { THEME("Theme colour"), GREEN("Green"), GAME("The game's colour") }
    val liveColor = EnumPref("live_color", LiveColor.THEME, LiveColor.values()) // Update 151: the theme's colour by default (was green).
    /** Live tiles: a pale backdrop behind everything (off: the theme shows through). */
    val liveLight = BoolPref("live_light", true)
    /** Update 146, Live tiles: the game's art fills the background (then the pale backdrop is off). */
    val liveArt = BoolPref("live_art", false)
    /** Live tiles: the game's name on its tile. */
    val liveNames = BoolPref("live_names", true)

    /** Side menu: what sits at the top of the menu. */
    enum class SideEmblem(val label: String) { PROFILE("Your picture"), CONSOLE("Console logo"), NONE("Nothing") }
    /** Side menu: the round picture top left — your RetroAchievements / Steam picture by default (update 84, new key). */
    val sideEmblem = EnumPref("side_emblem_v2", SideEmblem.PROFILE, SideEmblem.values())
    /** Side menu: the glowing orb, with the game you're on under it. */
    val sideOrb = BoolPref("side_orb", true)
    /** Side menu: menu names in capitals. */
    val sideCaps = BoolPref("side_caps", true)
    /** Side menu: its width (dp). */
    val sideWidth = IntPref("side_width", 200, 140..280)
    /** Side menu: gold trim instead of the theme's accent. */
    val sideGold = BoolPref("side_gold", false) // Update 151: the theme's colour by default (was gold).
    /** Side menu: the game's art behind everything. */
    val sideArt = BoolPref("side_art", true)
    /** Side menu: the category's name above the row. */
    val sideRowTitle = BoolPref("side_row_title", true)

    /** Cross menu: flowing ribbons across the background. */
    val crossWaves = BoolPref("cross_waves", true)
    /** Cross menu: a colour wash over the background. */
    enum class CrossTint(val label: String) { DUSK("Dusk purple"), OCEAN("Ocean blue"), EMBER("Ember red"), THEME("Theme colour"), NONE("None") }
    val crossTint = EnumPref("cross_tint", CrossTint.THEME, CrossTint.values()) // Update 151: the theme's colours by default (was Dusk purple).
    /** Cross menu: "Last played" / time played under the game you're on. */
    val crossInfo = BoolPref("cross_info", true)
    /** Update 127: on the Cross menu a game's achievements open as the menu's own column (CrossTrophies.kt), not the list popup. */
    val crossTrophies = BoolPref("cross_trophies", true)
    /**
     * Update 132: a Trophies category in the Cross menu's strip (SpecialFilter.TROPHIES): every game with achievements,
     * last played first, each with how far you are; A opens its achievements. Only on the Cross menu.
     */
    val crossTrophyCat = BoolPref("cross_trophy_cat", true)
    /**
     * Update 168: the lists the Cross menu shows beside the consoles even when they're hidden in Settings › Categories
     * (Continue playing, Favourites, Most played, the collections…): category keys, as in the category order.
     */
    val crossCats = SetPref("cross_cats")
    /** Cross menu: size of the category icons and the list (%). */
    val crossScale = IntPref("cross_scale", 100, 70..140)
    /** Game size of the layouts without a size of their own (update 104): "LAYOUT=pct;…" (70–140, 100 = normal), set by the R1 Size tile. */
    val layoutScale = StringPref("layout_scale", "")
    /** The round shortcuts in a corner stay open (else folded behind their ☰ until pressed) — update 104. */
    val shortcutsOpen = BoolPref("shortcuts_open", false)
    /** Cross menu: the category's name under its icon. */
    val crossLabels = BoolPref("cross_labels", true)
    /** Cross menu (update 90/91): the tile behind each console logo in the accent colour (the game's or the theme's); logos keep their colours. */
    val crossLogoTint = BoolPref("cross_logo_tint", false)
    /** Cross menu: the console logos as one-colour cut-outs (no tile behind them), white or the accent colour. */
    val crossLogoCut = BoolPref("cross_logo_cut", false)
    /** Cross menu (update 104): each console as a plain white folder with its name, the special categories keep their
     *  icons. Off: the console logos (on tiles, or cut out). */
    val crossFolders = BoolPref("cross_folders", true)
    /** Game shelf (update 90): every spine at its console's real case size (else all the same). */
    val shelfRealSizes = BoolPref("shelf_real_sizes", false)
    /** Game shelf: the game you're on bobs and turns gently once it's pulled out. */
    val shelfBob = BoolPref("shelf_bob", true)
    /** Update 129: Real case (its own scan, else Voltcap's) is the default; Voltcap's case and the plain cover added. */
    enum class ShelfPulled(val label: String) {
        FRONT("Real case, else Voltcap's"), SIDE("3D box from the side"), OURS("Voltcap's case"), COVER("Plain cover")
    }
    /** Game shelf and TV room shelf (update 129): how the game you're on shows once pulled out. */
    val shelfPulled = EnumPref("shelf_pulled", ShelfPulled.FRONT, ShelfPulled.values())
    /** How the games stand on the Game shelf (update 103): spines out (a bookcase), fronts facing you, or turned so front and spine both show. */
    enum class ShelfStand(val label: String) { SPINES("Spines"), FRONTS("Facing forward"), ANGLED("Turned to the side") }
    /** Game shelf › "Look for spines again" (update 108): when it was pressed (ms); games looked up before then are asked again. */
    val spineRecheckAt = StringPref("spine_recheck_at", "")
    /** Update 114: every game's box scans (its spine) looked up in the background (Settings › Metadata › Saved on this device). */
    val spineScrape = BoolPref("spine_scrape", false)
    val shelfStand = EnumPref("shelf_stand", ShelfStand.SPINES, ShelfStand.values())
    enum class ShelfLook(val label: String) { DARK("Dark"), WOOD("Wood"), GLASS("Glass") }
    val shelfLook = EnumPref("shelf_look", ShelfLook.DARK, ShelfLook.values())
    /** Game shelf: the whole screen behind the shelves is the bookcase (back panel, side uprights, top crown). */
    val shelfBackdrop = BoolPref("shelf_backdrop", true)

    /** Spotlight: the small tiles along the top as squares (off: the card artwork's own shape). */
    val spotSquares = BoolPref("spot_squares", true)
    /** Spotlight: the tiles' shape — squares, wide capsules, or the game look's own shape (update 84; replaces [spotSquares]). */
    enum class TileShape(val label: String) { SQUARE("Squares"), WIDE("Wide capsules"), LOOK("The game look's shape") }
    val spotShape = EnumPref("spot_shape", TileShape.SQUARE, TileShape.values())
    /**
     * Spotlight's look (update 125). Row & menu (the default): a bar of round buttons with your profile, points and the
     * time on top, the row of square tiles under it with the one you're on big, its name beside it and Start under it,
     * then Overview · Recent activity · Related and what that shows. Big picture: the earlier look (big title, Play, cards).
     */
    // Update 132: Home menu (the new default): a console's home menu — your profile, your shortcuts as icons and the time
    // along the very top (the name of the one you're on under it), a row of big tiles with the game's name under the one
    // you're on, and what the buttons do along the bottom.
    enum class SpotLook(val label: String) { HOME_MENU("Home menu"), ROW_MENU("Row & menu"), CINEMA("Big picture") }
    val spotLook = EnumPref("spot_look", SpotLook.HOME_MENU, SpotLook.values())
    /** Spotlight's Home menu (update 132): the status bar too (off: the menu's own profile, battery and clock on top). */
    val spotStatusBar = BoolPref("spot_status_bar", false)
    /** Spotlight's Home menu (update 132): what the buttons do, along the bottom. */
    val spotHints = BoolPref("spot_hints", true)
    /** Spotlight: tiles along the top (title and Play under them) or along the bottom (title and Play above them). */
    enum class SpotSide(val label: String) { TOP("Top"), BOTTOM("Bottom") }
    val spotTilesAt = EnumPref("spot_tiles_at", SpotSide.TOP, SpotSide.values())
    /** Spotlight: where the name of the game you're on sits. */
    enum class SpotName(val label: String) { RIGHT("Beside the tile"), BELOW("Under the tile"), ABOVE("Above the tile"), NONE("Hidden (the big title only)") }
    val spotNameAt = EnumPref("spot_name_at", SpotName.RIGHT, SpotName.values())
    /** Spotlight (update 104): where the big title, Play and ⋯ sit across the screen… */
    enum class SpotAcross(val label: String) { LEFT("Left"), CENTER("Centre"), RIGHT("Right") }
    val spotTitleAt = EnumPref("spot_title_at", SpotAcross.LEFT, SpotAcross.values())
    /** …and how far from the tiles: right under (over) them, in the middle of the screen, or at the far edge (as before). */
    enum class SpotAlong(val label: String) { NEAR("By the tiles"), MIDDLE("Middle"), FAR("Far edge") }
    val spotTitleRow = EnumPref("spot_title_row", SpotAlong.FAR, SpotAlong.values())
    /** Spotlight: the game's logo as its title when there is one. */
    val spotLogo = BoolPref("spot_logo", true)
    /** Spotlight: the activity cards (achievements, time played, last played). */
    val spotCards = BoolPref("spot_cards", true)
    /** Spotlight: the category name (and the next one) top left. */
    val spotTabs = BoolPref("spot_tabs", true)
    /** Spotlight: the game's art behind everything, and how dark the shade over it is (%). */
    val spotArt = BoolPref("spot_art", true)
    val spotShade = IntPref("spot_shade", 55, 0..90)

    /** Icon bar: the shortcut icons along the top. */
    val barIcons = BoolPref("bar_icons", true)
    /** Icon bar: tile size (%), and how much bigger the one you're on is (%). */
    val barTile = IntPref("bar_tile", 100, 70..150)
    /** Icon bar: how much bigger the game you're on pops out (update 86: like the PS5's, the others stay put; new key). */
    val barGrow = IntPref("bar_grow_v3", 155, 100..200)
    /** Icon bar: the game's name on its tile. */
    val barNames = BoolPref("bar_names_v2", false)
    /** Icon bar: the Start button and time played under the game you're on. */
    val barStart = BoolPref("bar_start", true)
    /** Icon bar: the game's art behind everything. */
    val barArt = BoolPref("bar_art", false)
    /** Icon bar: the game's art behind is cut at the middle of the screen (it fades out there) instead of covering all of it. */
    val barArtCut = BoolPref("bar_art_cut", true)
    /** Icon bar: the tabs along the top show only the category you're in (◀ ▶ step through the categories) instead of your shortcuts. */
    val barTabsCurrent = BoolPref("bar_tabs_current", true)
    /** (Update 235, #08: Icon bar — the row of tiles right under the tabs (TOP, as before) or along the bottom with the
     *  title, Start and time played just above it (BOTTOM). The tabs stay along the top either way.) */
    val barGamesAt = EnumPref("bar_games_at", SpotSide.TOP, SpotSide.values())
    /** Console hub: shortcut icons along the top, and the game's art behind. */
    val hubIcons = BoolPref("hub_icons", true)
    val hubArt = BoolPref("hub_art", true)
    /** Resume & discover: the game's art behind. */
    val resArt = BoolPref("res_art", true)
    /** Channel grid: tiles per row, pale backdrop, the name on the tile you're on. */
    val chanCols = IntPref("chan_cols", 4, 2..6) // Update 151: 2 to 6 (was 3 to 6).
    val chanLight = BoolPref("chan_light", true)
    /**
     * Update 329 (Toni: "a dark Wii Channel-inspired theme as a toggle for the current one, because of OLED"): the pale
     * Channel grid in black and dark grey — true black behind, dark tiles, bar and buttons, light writing. Only with the
     * pale backdrop (Layout options › Background: Pale).
     */
    val chanDark = BoolPref("chan_dark", false)
    /** Update 146, Channel grid: the game's art fills the background (then the pale backdrop is off). */
    val chanArt = BoolPref("chan_art", false)
    val chanNames = BoolPref("chan_names", true)
    /**
     * Update 112: the Channel grid scrolls on continuously, a column at a time (◀ ▶), instead
     * of turning pages.
     */
    val gridsContinuous = BoolPref("grids_continuous", true)
    /** Channel grid (update 104): the status bar along the top; off by default, the grid has its own corner buttons and clock. */
    val chanStatusBar = BoolPref("chan_status_bar", false)
    /** Update 115: the Channel grid's corner circle shows the category as a symbol (on), or the console's logo in colour / the category's name. */
    val chanSymbols = BoolPref("chan_symbols", true)
    /** Update 116: each Edit Home part's own style ("id=backing,alpha,outline,shape,still;…", HomePartStyle.kt). */
    val partStyles = StringPref("part_styles", "")
    /**
     * Update 117: the recaps count the games played on your other devices too (synced with Google Drive), and a game
     * that isn't on this device shows as its cover (looked up by name) or as a disc.
     */
    val recapOtherDevices = BoolPref("recap_other_devices", true)
    enum class RecapForeignArt(val label: String) { COVER("Its cover"), DISC("A disc") }
    val recapForeignArt = EnumPref("recap_foreign_art", RecapForeignArt.COVER, RecapForeignArt.values())
    /** Update 117: the small music player in each layout's own style (off: the album art's colour everywhere). */
    val musicSkins = BoolPref("music_skins", true)
    /** Resume & discover / Console hub (update 104): the next achievements to earn slide past, faint, behind the Achievements card. */
    val resNextAch = BoolPref("res_next_ach", true)
    /**
     * Update 113: A or a tap on the game you're on opens the layout's own game window (back · Details · Play), on the
     * main screen. Off: straight into the game. (Replaces the Channel grid's own switch, update 104.)
     */
    val gameWindow = BoolPref("game_window", true)
    /** Update 119: the layouts whose A opens the classic details window instead of their own game window. */
    val classicWindowLayouts = SetPref("classic_window_layouts", emptySet())
    /**
     * Update 132: the order of the achievements on the game window's achievements page (you pick it there, at the top
     * of the list; it stays for every game).
     */
    enum class TrophyOrder(val label: String) {
        SET("Set order"), TO_GET("Still to get first"), EARNED("Earned first"), LATEST("Latest earned first"),
        POINTS("Most points first"), RARE("Rarest first")
    }
    val winTrophyOrder = EnumPref("win_trophy_order", TrophyOrder.SET, TrophyOrder.values())
    /** Update 119: the game window's colours — the game's own (from its cover), the theme's, or the layout's own look. */
    enum class WinColors(val label: String) { GAME("The game's colours"), THEME("Theme colours"), LAYOUT("The layout's own") }
    val gameWindowColors = EnumPref("game_window_colors", WinColors.GAME, WinColors.values())
    /** Update 119: what the game window shows where it has room (names of [com.shelf.launcher.ui.WinPart]). */
    val gameWindowShows = SetPref("game_window_shows", setOf("MEDIA", "DESCRIPTION", "FACTS", "ACHIEVEMENTS", "ICONS"))
    /** Update 125: where each part of the game window goes, per layout ("LAYOUT.PART=SLOT", GameWindow.kt WinPlan). */
    val gameWindowPlan = StringPref("game_window_plan", "")
    /** Update 122: the game window's disc games as their case with the disc half out of it (the default); off: the disc alone. */
    val gameWindowCase = BoolPref("game_window_case", true)
    // ---- Update 146: the newer layouts and the banner game window ----
    /** Library panel: games per row in the grid (3–8). */
    val libCols = IntPref("lib_cols", 5, 3..8)
    /** Library panel: the game panel on the left (off: the grid alone, full width). */
    val libPanel = BoolPref("lib_panel", true)
    /** Library panel: category tabs along the top. */
    val libTabs = BoolPref("lib_tabs", true)
    /** Case stacks: the stacks drawn in perspective, going back into the room (off: flat, straight on). */
    val stacksDepth = BoolPref("stacks_depth", true)
    /** Case stacks: the button bar along the bottom. */
    val stacksBar = BoolPref("stacks_bar", true)
    /** Case stacks: a mirror floor under the cases. */
    val stacksMirror = BoolPref("stacks_mirror", true)
    /** Update 151: Case stacks: the game in the middle as a real 3D case, turned a little so its spine shows (off: flat). */
    val stacks3d = BoolPref("stacks_3d", true)
    /** Update 159: the case looks (Real cases, Cases · real sizes) in your 3D shape library's cases (R1 › 3D). */
    /** (Update 182: off for good — the R1 3D tile is greyed out in the case looks; your real case scans look better.) */
    /** (Update 200: read by nothing — the R1 3D tile is gone and the pre-rendered 3D cases are retired; a saved true is ignored.) */
    val caseModels3d = BoolPref("case_models_3d2", false)
    /** Update 159: Case stacks: the tool strip along the top (your round shortcuts as small square buttons). */
    val stacksToolbar = BoolPref("stacks_toolbar", true)
    /**
     * Update 159: the decoded art stays in memory — a bigger share of a larger heap, not dropped when a game starts
     * (only when Android is really short), so every picture is read from storage once (MemoryBudget).
     */
    val keepArt = BoolPref("keep_art", true)
    /**
     * Update 162: the carousel's motion, its category slide and the open / inspect animation (the piece flying out and
     * turning) run in full even with Lite effects on (on by default).
     */
    val motionOnLite = BoolPref("motion_on_lite", true)
    /** Timeline: what puts the games in order along the line. */
    enum class TimelineBy(val label: String) { LAST_PLAYED("Last played"), RELEASED("Release year"), ADDED("Added to Voltcap") }
    val timelineBy = EnumPref("timeline_by", TimelineBy.LAST_PLAYED, TimelineBy.values())
    /** Timeline: the date / year under each game. */
    val timelineDates = BoolPref("timeline_dates", true)
    /** Arcade cabinets: the marquee lit and glowing. */
    val arcadeGlow = BoolPref("arcade_glow", true)
    /** Update 177: a multi-disc game without a .m3u playlist gets one written by the scan (off: listed under Library check). */
    val autoM3u = BoolPref("auto_m3u", false)
    /** Update 177: how the consoles look on the second screen's Games page (SecondGamesPage.kt). */
    enum class FolderLook(val label: String) { FOLDERS("Folders"), TILES("Tiles"), CARDS("Wide cards"), LOGOS("Logos") }
    /** (Update 185: back to your own choice — Folders by default; it's the games inside that are Tiles now.) */
    val secondFolderLook = EnumPref("second_folder_look", FolderLook.FOLDERS, FolderLook.values())
    /** Update 185: the games inside a console on the second screen's Games page: Tiles (default), Covers, Wide, List. */
    enum class FolderGamesLook(val label: String) { TILES("Tiles"), COVERS("Covers"), WIDE("Wide"), LIST("List") }
    val secondGamesLook = EnumPref("second_games_look", FolderGamesLook.TILES, FolderGamesLook.values())
    /** Update 177: the console's logo over a folder picture (off: the picture alone — many packs have the name in it). */
    val secondFolderLogo = BoolPref("second_folder_logo", true)
    /** Update 286 (Toni): the consoles' logos on the Games page as plain icons in the theme's colours (off: their own colours). */
    val secondFolderPlain = BoolPref("second_folder_plain", true)
    /**
     * Update 200: the top screen follows the second screen's Games page: a folder lit / touched there shows that console
     * big up top, and opening it switches the top screen to that console's games (off: the top screen stays as it is).
     */
    val secondTopFollows = BoolPref("second_top_follows", true)
    /** Update 176: the custom layout shown by Custom layout (CustomLayouts.kt; "" = the built-in example). */
    val customLayout = StringPref("custom_layout", "")
    /** Arcade cabinets: scan lines over the screen. */
    val arcadeLines = BoolPref("arcade_lines", true)
    /** Arcade cabinets: the cabinets in the game's own colours (off: the theme's). */
    val arcadeGameColors = BoolPref("arcade_game_colors", true)
    /** Console stack: the consoles as your console photos where there are (Settings › Library), else drawn. */
    val consolePhotos = BoolPref("console_stack_photos", true)
    /** Update 330: the console picture picked per console, "KEY=name" (ConsoleArt). */
    val consoleHwPick = SetPref("console_hw_pick")
    /** Console stack: how many games slide out of the console you're on (3–9). */
    val consoleStackGames = IntPref("console_stack_games", 6, 3..9)
    /**
     * Banner game window (update 146): the game window as one page — a wide art banner on top, a coloured strip with
     * Play, last played and play time, then tabs (L1 / R1): Activity, Achievements, Media, Game info. Off: each layout's own window.
     */
    val winBanner = BoolPref("win_banner", false)
    /**
     * Update 330 (Toni: "each popup a natural extension of its layout"): the layouts that have a window of their own making
     * (the Cardboard box's insert sheet beside the case, File select's flipped-over file, Storage boxes' entry record…,
     * WinScenes.kt) show it; off: those layouts use the standard window in their colours. The banner page and the
     * motions above override both.
     */
    val winScenes = BoolPref("win_scenes", true)
    /**
     * Update 249 (#119, Toni's two earlier designs kept as styles of their own): the game window as a move of the home
     * screen itself instead of a window over it — [WinMotion.ROW_OPENS]: the row with the game you're on opens up into its
     * details; [WinMotion.ROW_REVEAL]: the art stays, the other games fade, the name rises and Play / Continue and the
     * buttons slide down while the details appear. NONE: the window as before (each layout's own, or the banner page).
     */
    enum class WinMotion(val label: String, val short: String) {
        NONE("Off", "Off"), ROW_OPENS("The row opens up", "Row opens"), ROW_REVEAL("Row reveal", "Reveal"),
        // (Update 250, #119: Game Glance's move — GlanceWindow.kt.)
        SHARED("Shared move", "Shared")
    }
    val winMotion = EnumPref("win_motion", WinMotion.NONE, WinMotion.values())

    /** Channel grid (update 104): profile, Settings, notifications and sync buttons in the top right corner. */
    val chanCorner = BoolPref("chan_corner", true)
    /** Circuit board (update 106): the board's colour — green board and traces, else near-black with the theme's colour. */
    val circuitGreen = BoolPref("circuit_green", true)
    /**
     * Update 112: the board's colours — your theme's (its background and accent, the default), the green board, or
     * near-black with the theme's accent. Replaces [circuitGreen] (no longer read; the theme's colours are the new default).
     */
    enum class CircuitColors(val label: String) { THEME("Your theme's colours"), GREEN("Green board"), DARK("Black board") }
    val circuitColors = EnumPref("circuit_colors", CircuitColors.THEME, CircuitColors.values())
    /** Current flowing along the power line while you rest on a game. */
    val circuitCurrent = BoolPref("circuit_current", true)
    /** The chips' silkscreen labels (U1, U2…). */
    val circuitLabels = BoolPref("circuit_labels", true)
    /** The faint traces of the board behind everything (off: your theme's background). */
    val circuitBackdrop = BoolPref("circuit_backdrop", true)

    // ---- Update 202: Green Core (2001) — the machine-dashboard layout (StyleGreenCore.kt, GreenCoreSphere.kt) ----
    /** Update 202: how the GAMES page shows the category: a vertical list, or a curved carousel in front of the sphere. */
    enum class GcBrowse(val label: String) { LIST("List"), CAROUSEL("Curved carousel") }
    /** Update 202: Green Core's light colour (the original look is the core green). */
    enum class GcAccent(val label: String, val argb: Long) {
        GREEN("Core green", 0xFF59E36BL), LIME("Lime", 0xFFB6F24AL), CYAN("Ice blue", 0xFF5FD8F0L),
        AMBER("Amber", 0xFFF2B640L), RED("Red alert", 0xFFF0605AL), WHITE("White", 0xFFE8F2EAL)
    }
    /** Update 202: how strongly the sphere, the panels and the selection glow (0–100). */
    val gcGlow = IntPref("gc_glow", 70, 0..100)
    /** Update 202: the sphere's turning and pulsing speed, in % (0 = still). */
    val gcSphereSpeed = IntPref("gc_sphere_speed", 100, 0..200)
    /** Update 202: the slow drifting light in the background. */
    val gcBgMotion = BoolPref("gc_bg_motion", true)
    /** Update 202: the layout's own synthesised interface sounds (click, whoosh, confirm); off = Voltcap's usual sounds. */
    val gcSounds = BoolPref("gc_sounds", true)
    /** Update 202: their volume, in %. */
    val gcVolume = IntPref("gc_volume", 60, 0..100)
    /** Update 202: a low machine hum while Green Core is on screen (off by default). */
    val gcHum = BoolPref("gc_hum", false)
    /** Update 202: reduced motion — the sphere stands still and the panels change without sliding. */
    val gcReducedMotion = BoolPref("gc_reduced_motion", false)
    val gcBrowse = EnumPref("gc_browse", GcBrowse.LIST, GcBrowse.values())
    val gcAccent = EnumPref("gc_accent", GcAccent.GREEN, GcAccent.values())

    // ---- (Update 239, #93: Cube menu — the optional cube-navigator home layout, StyleCube.kt) ----
    /** (Update 239, #93: where the cube waits while you browse the games — a column on the left, a band on top, or
     *  hidden behind a small category chip. A screen too small for the one you picked docks it where it fits.) */
    enum class CubeDock(val label: String) { LEFT("Left column"), TOP("Top band"), HIDDEN("Hidden (category chip)") }
    /** (Update 239, #93: what a cube face shows for its category.) */
    enum class CubeFace(val label: String) { LOGO("Logo or icon + name"), NAME("Name only") }
    /** (Update 239, #93: the cube's glass colour.) */
    enum class CubeTint(val label: String, val argb: Long) {
        INDIGO("Indigo", 0xFF5B4FC8L), LILAC("Lilac", 0xFF9A7FE0L), SMOKE("Smoke", 0xFF6F7486L), THEME("Theme accent", 0L)
    }
    /** (Update 239, #93: start on the big cube — A opens a face's games, B comes back to it. Off: straight to the games
     *  with the cube docked.) */
    val cubeHome = BoolPref("cube_home", true)
    val cubeDock = EnumPref("cube_dock", CubeDock.LEFT, CubeDock.values())
    val cubeFace = EnumPref("cube_face", CubeFace.LOGO, CubeFace.values())
    val cubeTint = EnumPref("cube_tint", CubeTint.INDIGO, CubeTint.values())
    /** (Update 239, #93: a gentle float and sway while you rest on the big cube; it stops when the home screen idles.) */
    val cubeIdle = BoolPref("cube_idle", true)
    /** (Update 239, #93: the cube's top and bottom faces — ▲ Apps, ▼ Settings. Off: ▲ ▼ go past the cube.) */
    val cubeExtraFaces = BoolPref("cube_extra_faces", true)
    /** (Update 239, #93: the big cube's size, in %.) */
    val cubeSize = IntPref("cube_size", 100, 70..115)
    /** (Update 239, #93: the dark indigo backdrop with a faint floor grid; off: your theme's background.) */
    val cubeBackdrop = BoolPref("cube_backdrop", true)
    /** (Update 239, #93: reduced motion — the cube turns and docks without animating.) */
    val cubeReducedMotion = BoolPref("cube_reduced_motion", false)
    /** 0.6.17: the Cube menu's look — Hub: the glass cube with Games / Systems / Apps / Settings faces beside a menu and the
     *  game library (on wide screens); Classic: the cube of categories (update 239). */
    enum class CubeLook(val label: String) { HUB("Hub (cube + library)"), CLASSIC("Classic (cube of categories)") }
    val cubeLook = EnumPref("cube_look", CubeLook.HUB, CubeLook.values())

    // ---- Update 330: Memory card (2000) — the games as save icons floating on a grey memory-card screen (StyleMemoryCard.kt) ----
    enum class McSize(val label: String, val cell: Int) { SMALL("Small", 84), MEDIUM("Medium", 104), LARGE("Large", 132) }
    val mcSize = EnumPref("mc_size", McSize.MEDIUM, McSize.values())
    enum class McSpacing(val label: String, val icon: Float) { TIGHT("Tight", 0.64f), NORMAL("Normal", 0.54f), ROOMY("Roomy", 0.45f) }
    val mcSpacing = EnumPref("mc_spacing", McSpacing.NORMAL, McSpacing.values())
    /** Rows: straight rows; Loose: each icon a little off the line, as a real card's icons float. */
    enum class McArrange(val label: String) { LOOSE("Loose rows"), ROWS("Straight rows") }
    val mcArrange = EnumPref("mc_arrange", McArrange.LOOSE, McArrange.values())
    /** How deep the drawn icons look: no edge, a shallow edge, a deeper one with a tilt. */
    enum class McDepth(val label: String, val edge: Float, val tilt: Float) { FLAT("Flat", 0f, 0f), SHALLOW("Shallow", 2f, 10f), DEEP("Deep", 4f, 18f) }
    val mcDepth = EnumPref("mc_depth", McDepth.SHALLOW, McDepth.values())
    /** What the icon you're on does: turns round, sways, or only lifts and brightens. */
    enum class McSelected(val label: String) { TURN("Turns"), SWAY("Sways"), LIFT("Lifts only") }
    val mcSelected = EnumPref("mc_selected", McSelected.TURN, McSelected.values())
    /** The screen behind: neutral grey, grey-blue, or your theme's background. */
    enum class McBack(val label: String) { GREY("Grey"), BLUE("Grey-blue"), THEME("Your theme") }
    val mcBack = EnumPref("mc_back", McBack.GREY, McBack.values())
    /** PS2 games show the 3D icon of their own save (read only from your memory cards) when there is one. */
    val mcSaveIcons = BoolPref("mc_save_icons", true)
    /** Update 330: PS2 games without a save here: their save icon from PS2IODB (fetched once, kept). */
    val mcDbIcons = BoolPref("mc_db_icons", true)
    /** The game's title at the top right (the save's own two-line title where it has one). */
    val mcTitle = BoolPref("mc_title", true)
    /** The button hints along the bottom. */
    val mcHints = BoolPref("mc_hints", true)
    /** The icons float up and down a little (rests when the home screen goes quiet). */
    val mcFloat = BoolPref("mc_float", true)

    // ---- Update 330: Case flow — the games as real 3D cases on a curved carousel (StyleCaseFlow.kt) ----
    /** How much the row of cases curves away (0 = straight). */
    val cfCurve = IntPref("cf_curve", 45, 0..100)
    /** Room between the cases, in % of the standard. */
    val cfSpacing = IntPref("cf_spacing", 100, 60..160)
    /** The cases' size, in % of the standard. */
    val cfSize = IntPref("cf_size", 100, 70..120)
    /** How far the cases either side turn, degrees. */
    val cfAngle = IntPref("cf_angle", 60, 25..80)
    /** How much you look down on the cases (the camera's height), degrees. */
    val cfPitch = IntPref("cf_pitch", 5, 0..14)
    /** The cases either side: their fronts turned toward the middle, or their spines toward you. */
    enum class CfSide(val label: String) { FRONTS("Fronts"), SPINES("Spines") }
    val cfSide = EnumPref("cf_side", CfSide.FRONTS, CfSide.values())
    enum class CfSpeed(val label: String, val k: Float) { CALM("Calm", 0.55f), NORMAL("Normal", 1f), QUICK("Quick", 1.8f) }
    val cfSpeed = EnumPref("cf_speed", CfSpeed.NORMAL, CfSpeed.values())
    /** The cases' mirror images on a glossy floor. */
    val cfReflect = BoolPref("cf_reflect", true)
    /** A dark stage with a floor behind the cases (off: your theme's background). */
    val cfStage = BoolPref("cf_stage", true)
    /** The game's name, console and time played under the cases. */
    val cfTitle = BoolPref("cf_title", true)

    // ---- Update 330: Cardboard box — the games filed spine-up in rows in an open cardboard box (StyleCardboardBox.kt) ----
    val boxLanes = IntPref("box_lanes", 2, 2..3)
    enum class BoxArrange(val label: String) { NEAT("Neat"), MESSY("Messy") }
    val boxArrange = EnumPref("box_arrange", BoxArrange.NEAT, BoxArrange.values())
    /** Room between the games, in % of the standard. */
    val boxSpacing = IntPref("box_spacing", 100, 70..150)
    /** How high the game you're on rises out of the box, in % of the standard. */
    val boxLift = IntPref("box_lift", 100, 40..160)
    /** Update 330 (top-down box): the camera's angle — 0 straight down into the box, more: tilted toward its back wall. */
    val boxTilt = IntPref("box_tilt", 8, 0..32)
    /** How deep the box looks (its walls), in %. */
    val boxDepth = IntPref("box_depth", 100, 60..140)
    enum class BoxCardboard(val label: String, val top: Long, val deep: Long) {
        KRAFT("Brown cardboard", 0xFFC79A62, 0xFF8A6235), WHITE("White box", 0xFFE9E4DA, 0xFFB9B1A2), DARK("Dark box", 0xFF4A4038, 0xFF241E19)
    }
    val boxCardboard = EnumPref("box_cardboard", BoxCardboard.KRAFT, BoxCardboard.values())
    enum class BoxSpeed(val label: String, val k: Float) { CALM("Calm", 0.6f), NORMAL("Normal", 1f), QUICK("Quick", 1.7f) }
    val boxSpeed = EnumPref("box_speed", BoxSpeed.NORMAL, BoxSpeed.values())
    /** Update 330 (top-down box): how big the cases are — bigger cases, fewer to a row (and to a box). */
    enum class BoxCaseSize(val label: String, val k: Float) { LARGE("Large (fewer to a row)", 1.18f), MEDIUM("Medium", 1f), SMALL("Small (more to a row)", 0.84f) }
    val boxCaseSize = EnumPref("box_case_size", BoxCaseSize.MEDIUM, BoxCaseSize.values())
    /** Update 330: what the box's name is written on. */
    enum class BoxLabel(val label: String) { STICKER("Printed label"), MARKER("Marker on the cardboard"), NONE("No label") }
    val boxLabel = EnumPref("box_label", BoxLabel.STICKER, BoxLabel.values())
    /** Update 330: a strip of tape and a few scuffs on the box (off: a clean new box). */
    val boxWear = BoolPref("box_wear", false)
    /** Update 330: ◀ ▶ past a box's edge: on into the next box at once, or after a knock against its wall. */
    enum class BoxSwitch(val label: String) { EDGE("At once"), KNOCK("After a knock") }
    val boxSwitch = EnumPref("box_switch", BoxSwitch.EDGE, BoxSwitch.values())
    /** (Update 330 first draft: what stood in the box — no longer read; always the games' cases by their spines.) */
    enum class BoxMedia(val label: String) { LOOK("Your game look"), CASES("Cases and boxes") }
    val boxMedia = EnumPref("box_media", BoxMedia.LOOK, BoxMedia.values())

    // ---- Update 330: Storage boxes — categories as storage boxes of small icons (StyleStorageBoxes.kt) ----
    enum class StSize(val label: String, val cols: Int, val rows: Int) { COMPACT("Compact (6 × 5)", 6, 5), ROOMY("Roomy (5 × 4)", 5, 4) }
    val stSize = EnumPref("st_size", StSize.COMPACT, StSize.values())
    /** Room round each icon in its slot, in %. */
    val stSpacing = IntPref("st_spacing", 100, 60..150)
    enum class StWallpaper(val label: String) { PER_BOX("A different one per box"), ONE("The same for every box"), PLAIN("Plain") }
    val stWallpaper = EnumPref("st_wallpaper", StWallpaper.PER_BOX, StWallpaper.values())
    enum class StPreview(val label: String) { LEFT("Left"), RIGHT("Right") }
    val stPreview = EnumPref("st_preview", StPreview.LEFT, StPreview.values())
    /** The pointer over the icon you're on, and the icons' little hop. */
    val stCursor = BoolPref("st_cursor", true)
    enum class StMotion(val label: String) { FULL("Full"), REDUCED("Reduced"), OFF("Off") }
    val stMotion = EnumPref("st_motion", StMotion.FULL, StMotion.values())

    // ---- Update 330: File select (1996) — chunky 3D save-file blocks, pages of them (StyleFileSelect.kt) ----
    enum class FsPage(val label: String, val cols: Int, val rows: Int) { FOUR("4 files (2 × 2)", 2, 2), SIX("6 files (3 × 2)", 3, 2), EIGHT("8 files (4 × 2)", 4, 2) }
    val fsPage = EnumPref("fs_page", FsPage.FOUR, FsPage.values())
    /** Room between the blocks, in %. */
    val fsSpacing = IntPref("fs_spacing", 100, 60..150)
    /** How much the block you're on bounces and tilts, in %. */
    val fsBounce = IntPref("fs_bounce", 100, 0..160)
    enum class FsColors(val label: String) { BRIGHT("Bright"), GAME("The game's colours"), SOFT("Soft") }
    val fsColors = EnumPref("fs_colors", FsColors.BRIGHT, FsColors.values())
    /** Console-era blips when you move and pick. */
    val fsSounds = BoolPref("fs_sounds", false)
    /** The moving pattern behind the blocks. */
    val fsPattern = BoolPref("fs_pattern", true)

    // ---- Update 330, phase 3: Orbit deck (1995) — the games as discs on an orbit round a glowing core (StyleOrbitDeck.kt) ----
    enum class OdRing(val label: String, val n: Int) { SMALL("7 discs", 7), NORMAL("9 discs", 9), LARGE("11 discs", 11) }
    val odRing = EnumPref("od_ring", OdRing.NORMAL, OdRing.values())
    /** How far the orbit is tipped toward you, degrees. */
    val odTilt = IntPref("od_tilt", 14, 4..30)
    /** The disc you're on spins slowly. */
    val odSpin = BoolPref("od_spin", true)
    /** Twinkling stars behind (off: still). */
    val odStars = BoolPref("od_stars", true)
    /** The games' own discs (ScreenScraper's photo of the disc, else its label scan) on the orbit, the cover only where
     *  there's none; a cartridge game shows its cartridge. Off: every disc printed with its cover. */
    val odReal = BoolPref("od_real", true)
    enum class OdColor(val label: String, val top: Long, val bottom: Long, val glow: Long) {
        BLUE("Deep blue", 0xFF0B1638, 0xFF02040C, 0xFF5FA8FF), VIOLET("Violet", 0xFF1E0E3A, 0xFF05020C, 0xFFB98CFF),
        GRAPHITE("Graphite", 0xFF1A1D23, 0xFF050607, 0xFFC9D3E0)
    }
    val odColor = EnumPref("od_color", OdColor.BLUE, OdColor.values())

    // ---- Update 330, phase 3: Airy home (1999) — a light, airy row of rounded tiles under drifting bubbles (StyleAiryHome.kt) ----
    enum class AhSize(val label: String, val k: Float) { SMALL("Small", 0.82f), MEDIUM("Medium", 1f), LARGE("Large", 1.18f) }
    val ahSize = EnumPref("ah_size", AhSize.MEDIUM, AhSize.values())
    val ahBubbles = BoolPref("ah_bubbles", true)
    enum class AhAccent(val label: String, val argb: Long) { ORANGE("Orange", 0xFFF08A24), BLUE("Blue", 0xFF2F86E0), GREEN("Green", 0xFF35B37A) }
    val ahAccent = EnumPref("ah_accent", AhAccent.ORANGE, AhAccent.values())
    /** How much the row bows (0 straight). */
    val ahCurve = IntPref("ah_curve", 30, 0..60)
    /** The small pixel display top right showing the game you're on. */
    val ahLcd = BoolPref("ah_lcd", true)

    // ---- Update 330, phase 3: Night menu (2006) — slanted bars under a full moon (StyleNightMenu.kt) ----
    enum class NmRows(val label: String, val n: Int) { FIVE("5 to a screen", 5), SEVEN("7 to a screen", 7), NINE("9 to a screen", 9) }
    val nmRows = EnumPref("nm_rows", NmRows.SEVEN, NmRows.values())
    val nmMoon = BoolPref("nm_moon", true)
    val nmWater = BoolPref("nm_water", true)
    enum class NmColor(val label: String, val top: Long, val bottom: Long, val hi: Long) {
        NIGHT("Midnight blue", 0xFF0A1C3F, 0xFF020611, 0xFF7FE3FF), CRIMSON("Crimson", 0xFF3A0A14, 0xFF0C0204, 0xFFFF7A8C),
        EMERALD("Emerald", 0xFF06291F, 0xFF010A07, 0xFF7DFFC6)
    }
    val nmColor = EnumPref("nm_color", NmColor.NIGHT, NmColor.values())

    /** Icon bar: the tiles as squares (off: the card artwork's own shape). */
    val barSquares = BoolPref("bar_squares", true)

    /** Your picture in the status bar and on the Profile page: RetroAchievements' or Steam's (Settings › Accounts › Steam). */
    enum class Avatar(val label: String) { RA("RetroAchievements"), STEAM("Steam") }
    val avatarSource = EnumPref("avatar_source", Avatar.RA, Avatar.values())

    /** A game's achievement list (update 84): Voltcap's power cells (default), a trophy list, the badge wall or the plain list. */
    enum class AchView(val label: String) { POWER("Power cells"), TROPHY("Trophy list"), BADGES("Badges"), LIST("List") }
    val achView = EnumPref("ach_view", AchView.POWER, AchView.values())
    /**
     * Update 151: a game's achievement list opens the details and comments of the achievement you rest on by itself
     * (on by default; the Comments chip in the list or Settings › Achievements › Achievement views turns it off,
     * then A opens them as before).
     */
    val achCommentsAuto = BoolPref("ach_comments_auto", true)
    /** The Achievements screen (every game): power cells (default), scorecards, or the plain list. */
    enum class AchHubView(val label: String) { POWER("Power cells"), SCORECARD("Scorecards"), LIST("List") }
    val achHubView = EnumPref("ach_hub_view", AchHubView.POWER, AchHubView.values())

    // ---- Platform browser (ui/PlatformBrowser.kt): Slides, Wall and Grid views of the platforms ----
    /** How the category screen looks: the classic tiles, or the platform browser's Slides / Wall / Grid. */
    enum class PlatformView(val label: String) {
        TILES("Tiles"), SLIDES("Slides — one platform at a time"), WALL("Wall — slanted art slices"), GRID("Grid — horizontal cards")
    }
    val platformView = EnumPref("pb_view", PlatformView.TILES, PlatformView.values())
    /** Slides view: the grey skewed bands behind the cover. */
    val pbBands = BoolPref("pb_bands", true)
    /** Every element of the platform slide can be switched off. */
    val pbShowLogo = BoolPref("pb_show_logo", true)
    val pbShowIcon = BoolPref("pb_show_icon", false)
    val pbShowName = BoolPref("pb_show_name", true)
    val pbShowCovers = BoolPref("pb_show_covers", true)
    val pbShowLastPlayed = BoolPref("pb_show_last_played", false)
    val pbShowShadow = BoolPref("pb_show_shadow", true)
    val pbShowCount = BoolPref("pb_show_count", true)
    val pbShowMaker = BoolPref("pb_show_maker", true)
    val pbShowLegend = BoolPref("pb_show_legend", true)
    /** Grid view: tall 2:3 cards (off: wide cards). */
    val pbTallCards = BoolPref("pb_tall_cards", true)
    /** Grid view: A picks the platform up, ◀ ▶ (or a drag) moves it, A drops it. */
    val pbReorder = BoolPref("pb_reorder", false)
    /** Slides view animates when switching platform; Wall view animates in the direction you browse. */
    val pbAnimate = BoolPref("pb_animate", true)
    /** The game count and the random-game button in the platform views' action bar. */
    val pbBarCount = BoolPref("pb_bar_count", true)
    val pbBarRandom = BoolPref("pb_bar_random", true)
    /** The slide editor's layout: "ELEMENT=x,y,w,h;…" as fractions of the slide (empty = the built-in layout). */
    val pbSlideLayout = StringPref("pb_slide_layout", "")
    /** The Wall view on the second screen: its own layouts for platforms and for games. */
    val pbWallPlatformsLayout = StringPref("pb_wall_platforms", "")
    val pbWallGamesLayout = StringPref("pb_wall_games", "")

    // ---- Platform media (data/PlatformInfo.kt) ----
    /** Platform artwork (logo, photo, fan art) scraped from ScreenScraper through the Platform Options menu. */
    val platformMediaScrape = BoolPref("platform_media_scrape", true)

    // ---- Music overrides (ui/BackgroundMusic.kt) ----
    /** Platform and game music overrides fade into each other instead of cutting or overlapping. */
    val musicCrossfade = BoolPref("music_crossfade", true)
    /** Per platform / per game: restart the override from the beginning when you come back to it. Keys: "p:<platform>" / "g:<stableKey>". */
    val musicRestartOnReturn = SetPref("music_restart_on_return", emptySet())
    /**
     * Update 202: Voltcap's background music plays on when the screen goes off or the screensaver fades to black
     * (Settings › Music & sound › Background music). Off: it fades out there and back in when you return (MusicBlackout).
     */
    val keepMusicScreenOff = BoolPref("music_keep_screen_off", false)

    // ---- Platform intro videos (after the launch animation, before the game starts) ----
    val platformIntroVideos = BoolPref("platform_intro_videos", true)
    /** Platforms whose intro video is switched off. */
    val introVideoOff = SetPref("intro_video_off", emptySet())

    // ---- Detail pane layers (list / grid detail panes) ----
    val detailLayerCover = BoolPref("detail_layer_cover", true)
    val detailLayerScreenshots = BoolPref("detail_layer_screenshots", true)
    val detailLayerVideo = BoolPref("detail_layer_video", false)
    val detailLayerLogo = BoolPref("detail_layer_logo", true)

    // ---- Game details per view: which facts each view shows. Values are names of [GameFact]. ----
    enum class GameFact(val label: String) { DEVELOPER("Developer"), GENRE("Genre"), RELEASED("Release date"), PUBLISHER("Publisher"), DESCRIPTION("Description") }
    val factsRow = SetPref("facts_row", GameFact.values().map { it.name }.toSet())
    val factsList = SetPref("facts_list", GameFact.values().map { it.name }.toSet())
    val factsGrid = SetPref("facts_grid", GameFact.values().map { it.name }.toSet())
    val factsDetails = SetPref("facts_details", GameFact.values().map { it.name }.toSet())
    val factsSecond = SetPref("facts_second", GameFact.values().map { it.name }.toSet())

    // ---- Second screen: widget board, mirror, virtual second screen, wallpapers ----
    /** The second screen's Widgets page and Wall page appear among its tabs. */
    val secondWidgets = BoolPref("second_widgets", true)
    val secondWall = BoolPref("second_wall", true)
    /** The second screen's Magnifier page: the main screen's picture enlarged (dual-screen devices). */
    val secondMagnifier = BoolPref("second_magnifier", true)
    /** The Magnifier's zoom in percent (kept for the next time the page opens). */
    val magnifierZoom = IntPref("magnifier_zoom", 200, 100..800)
    /** The second screen's Wall shows platforms (off: the games of the category you are in). */
    val wallOfPlatforms = BoolPref("wall_of_platforms", true)
    /** The widgets on the second screen's Widget Board: "builtin:NOW_PLAYING", "builtin:SYSTEM_STATS", "app:<appWidgetId>". */
    val widgetBoardItems = StringPref("widget_board_items", "builtin:NOW_PLAYING,builtin:SYSTEM_STATS")
    /**
     * Update 299: each Widget Board widget's size, "<item>=COMPACT|NORMAL|EXPANDED;…" (an item not listed is NORMAL), so
     * the boards saved before keep their look and order.
     */
    val widgetBoardSizes = StringPref("widget_board_sizes", "")
    /** Screen Mirror: pinch-zoom, or a trackpad that moves a pointer on the primary screen. */
    enum class MirrorMode(val label: String) { ZOOM("Zoom"), TRACKPAD("Trackpad") }
    val mirrorMode = EnumPref("mirror_mode", MirrorMode.ZOOM, MirrorMode.values())
    /** Single-screen devices: a second-screen layout shown as a panel of its own (swipe in from the edge / hotkey). */
    val virtualSecondScreen = BoolPref("virtual_second_screen", false)
    /** The virtual second screen is shown now. */
    val virtualSecondScreenOpen = BoolPref("virtual_second_screen_open", false)
    /** Separate wallpapers: "" = the same as the main screen, else the picture's file name. */
    val secondScreenWallpaper = StringPref("second_screen_wallpaper", "")

    // ---- Update 158: Hero tiles (StyleHeroTiles.kt, Settings › Home › Layout options) ----
    /** Update 158, Hero tiles: rows of tiles under the big picture (1 or 2). */
    val heroTilesRows = IntPref("hero_tiles_rows", 2, 1..2)
    /** Update 158, Hero tiles: tile size, % of a row's height (smaller: more tiles across). */
    val heroTilesSize = IntPref("hero_tiles_size", 90, 50..100)
    /** Update 158, Hero tiles: the app dock at the bottom in the middle. */
    val heroTilesDock = BoolPref("hero_tiles_dock", true)
    /** Update 158, Hero tiles: the dock's own apps (package names, comma separated); empty = Files, Browser, Store, System settings. */
    val heroTilesDockApps = StringPref("hero_tiles_dock_apps", "")
    /** Update 158, Hero tiles: a pale backdrop with white tile frames (off: the theme's background). */
    val heroTilesPale = BoolPref("hero_tiles_pale", true)
    /** Update 158, Hero tiles: the status bar along the top too (off: the picture's own status pill). */
    val heroTilesStatusBar = BoolPref("hero_tiles_status_bar", false)

    // ---- Update 158: Record crate (StyleRecordCrate.kt, Settings › Home › Layout options) ----
    /** Update 158, Record crate: the record half out of the sleeve turns slowly (never while the home screen rests). */
    val crateSpin = BoolPref("crate_spin", true)
    /** Update 158, Record crate: the crate's wood. */
    enum class CrateWood(val label: String) { LIGHT("Light"), DARK("Dark") }
    val crateWood = EnumPref("crate_wood", CrateWood.LIGHT, CrateWood.values())

    // ---- Update 158: Card binder (StyleCardBinder.kt, Settings › Home › Layout options) ----
    /** Update 158, Card binder: how far each card of the hand turns from the next (degrees; 0 = a flat row). */
    // Update 158: default 5, a step the slider's D-pad (steps of 5: 0, 5, 10, 15) can come back to.
    val cardsFan = IntPref("cards_fan", 5, 0..15)
    /** Update 158, Card binder: a rainbow sheen sweeps once across the card you land on (never with Lite effects). */
    val cardsSheen = BoolPref("cards_sheen", true)
    /** Update 158, Card binder: where a card's frame colour comes from (mastered games are always gold). */
    enum class CardFrame(val label: String) { CONSOLE("Console"), ART("Game's art") }
    val cardsFrame = EnumPref("cards_frame", CardFrame.CONSOLE, CardFrame.values())
    /** Update 158, Card binder: A starts the game straight away (off: A opens the game's window, as on other layouts). */
    val cardsAPlays = BoolPref("cards_a_plays", true)

    // ---- Update 202: Pocket Quest (StylePocketQuest.kt, PocketQuestScenery.kt, PocketQuestSettings.kt) ----
    // Update 202: declared here (not in the layout's own files) because only prefs declared in Features are read by init().
    /** Update 202, Pocket Quest: the scenery and colours behind the menu (all drawn in code, original pixel art). */
    enum class PqTheme(val label: String) { ISLAND("Island Sky"), SUNSET("Sunset Ruins"), LAB("Deep Lab"), OCEAN("Ocean Depths") }
    /** Update 202, Pocket Quest: the theme's scenery behind the menu, or Voltcap's own background (Theme › Background). */
    enum class PqBackground(val label: String) { SCENERY("Theme scenery"), VOLTCAP("Voltcap background") }
    /** Update 202, Pocket Quest: the colour of the panel edges, the cursor and Play (Theme = the theme's own). */
    enum class PqAccent(val label: String) { THEME("Theme's own"), GOLD("Gold"), CYAN("Cyan"), ROSE("Rose"), EMERALD("Emerald"), SILVER("Silver"), VOLTCAP("Voltcap accent") }
    /** Update 202, Pocket Quest: the games drawn as your game look (cartridges, discs, cases…) or as plain cover art. */
    enum class PqMedia(val label: String) { CARTS("Cartridges & discs"), LOOK("Your game look"), COVERS("Cover art") }
    /** Update 202, Pocket Quest: how tall the collection along the bottom is. */
    enum class PqSize(val label: String) { SMALL("Small"), MEDIUM("Medium"), LARGE("Large") }
    /** Update 202, Pocket Quest: how quickly things glide. */
    enum class PqSpeed(val label: String) { RELAXED("Relaxed"), NORMAL("Normal"), SNAPPY("Snappy") }
    val pqTheme = EnumPref("pq_theme", PqTheme.ISLAND, PqTheme.values())
    val pqBackground = EnumPref("pq_background", PqBackground.SCENERY, PqBackground.values())
    /** Update 202, Pocket Quest: the scenery moves (drifting clouds, motes, bubbles); off: a still picture. */
    val pqBgAnim = BoolPref("pq_bg_anim", true)
    /** Update 202, Pocket Quest: how far the scenery's layers shift as you move along the games, % (0 = none). */
    val pqParallax = IntPref("pq_parallax", 50, 0..100)
    val pqAccent = EnumPref("pq_accent", PqAccent.THEME, PqAccent.values())
    // (Update 295, Toni: "use the cartridge look as default on Pocket Quest": Cartridges & discs, whatever look the other
    // layouts use — a new key, so everyone gets it once; Your game look and Cover art stay as choices.)
    val pqMedia = EnumPref("pq_media2", PqMedia.CARTS, PqMedia.values())
    val pqDescription = BoolPref("pq_description", true)
    val pqScreenshots = BoolPref("pq_screenshots", true)
    val pqSize = EnumPref("pq_size", PqSize.MEDIUM, PqSize.values())
    val pqSpeed = EnumPref("pq_speed", PqSpeed.NORMAL, PqSpeed.values())
    /** Update 202, Pocket Quest: its own synthesized square-wave blips for moving around the menu (off: Voltcap's sounds). */
    val pqSounds = BoolPref("pq_sounds", false)
    val pqSoundVolume = IntPref("pq_sound_volume", 60, 0..100)
    /** Update 202, Pocket Quest: reduced motion — no tilt, parallax, drifting or launch flash; changes are quick fades. */
    val pqReducedMotion = BoolPref("pq_reduced_motion", false)
    /** Update 202, Pocket Quest: the big picture of the game you're on leans gently in 3D. */
    val pqTilt = BoolPref("pq_tilt", true)
    /**
     * Update 202, Pocket Quest: L1 / R1 switch the console (off: they do what they do everywhere else).
     * Update 204: off by default — on, R1 couldn't open the quick settings (the R1 panel) on this layout. A new key, so
     * everyone gets the new default once; turning it on again is kept.
     */
    val pqBumpers = BoolPref("pq_bumpers2", false)
    /** Update 202, Pocket Quest: A starts the game (off: A opens the game window, as on the other layouts). */
    val pqAPlays = BoolPref("pq_a_plays", true)

    // ---- Quick Menu while in a game ----
    val quickMenuInGame = BoolPref("quick_menu_in_game", true)

    /**
     * Update 151: what plays when a game drawn as a case, cartridge or disc starts (Settings › Home › Animations ›
     * Starting a game): its case opens and the disc flies out, the cartridge is thrown into a slot below the screen,
     * the disc drops into a tray. Off: the usual launch animation (or none).
     */
    // (Update 295, Toni: "always play the physical media plug-in animation as the default and the other one as an
    // alternative": one choice — Plug in the media (every look: a case opens, a cartridge / disc is plugged in, and
    // with covers / capsules it's pulled out from behind the card first), Cover rises (the launch animation), or Off.
    // A new key, so everyone gets the new default once.)
    enum class LaunchThrow(val label: String) { THROW("Plug in the media"), COVER("Cover rises"), OFF("Off") }
    val launchThrow = EnumPref("launch_throw2", LaunchThrow.THROW, LaunchThrow.values())

    // ---- Update 200: Lyrics stage (ui/LyricsStage.kt; the second screen's Music page, layout "Lyrics stage") ----
    // Update 200: the same switches and sliders, with the same defaults, as the user's Wallpaper Engine lyrics dashboard
    // (its project.json), so the stage behaves the way they already set it up on the PC. They live here, not in an
    // object of their own, because only prefs declared in Features are read from disk by init().
    /** Update 200, Lyrics stage: what the effects do once nothing has played for a while. */
    enum class StageIdleFx(val label: String) { OFF("Switch off"), SLOW("Slow (12 fps)"), KEEP("Keep running") }
    /** Update 200, Lyrics stage: when the status line in the bottom-left corner shows. */
    enum class StageStatus(val label: String) { AUTO("Only when something is wrong"), ALWAYS("Always"), NEVER("Never") }
    val stageLowPower = BoolPref("stage_low_power", false)
    val stageParticles = BoolPref("stage_particles", true)
    val stageCircle = BoolPref("stage_circle", true)
    val stageSpectrum = BoolPref("stage_spectrum", false)
    val stageRings = BoolPref("stage_rings", false)
    val stageAurora = BoolPref("stage_aurora", false)
    val stageStars = BoolPref("stage_stars", false)
    val stageBokeh = BoolPref("stage_bokeh", false)
    val stageLines = BoolPref("stage_lines", false)
    val stageFxReact = BoolPref("stage_fx_react", true)
    val stageBgReact = BoolPref("stage_bg_react", true)
    val stageCoverColors = BoolPref("stage_cover_colors", true)
    /** Update 200, Lyrics stage: how hard the effects and the background answer the bass, in %. */
    val stageReaction = IntPref("stage_reaction", 150, 50..300)
    /** Update 200, Lyrics stage: blur of the cover behind everything (0 = sharp). */
    val stageBgBlur = IntPref("stage_bg_blur", 12, 0..60)
    /** Update 200, Lyrics stage: brightness of that background, in %. */
    val stageBgBright = IntPref("stage_bg_bright", 55, 20..100)
    val stageParticleAmount = IntPref("stage_particle_amount", 90, 20..300)
    /** Update 200, Lyrics stage: the effects' frame rate cap. */
    val stageFps = IntPref("stage_fps", 30, 15..60)
    val stageIdleFx = EnumPref("stage_idle_fx", StageIdleFx.OFF, StageIdleFx.values())
    /** Update 200, Lyrics stage: seconds after a pause before the stage goes idle. */
    val stageIdleAfter = IntPref("stage_idle_after", 30, 5..300)
    /** Update 200, Lyrics stage: the picture shown while idle (a content:// address), "" = none. */
    val stageIdlePicture = StringPref("stage_idle_picture", "")
    val stageClock24 = BoolPref("stage_clock_24", true)
    val stageSeconds = BoolPref("stage_seconds", true)
    /** Update 200, Lyrics stage: interface size, in %. */
    val stageUiScale = IntPref("stage_ui_scale", 100, 70..130)
    val stageStats = BoolPref("stage_stats", true)
    val stageCpu = BoolPref("stage_cpu", true)
    val stageGpu = BoolPref("stage_gpu", true)
    val stageRam = BoolPref("stage_ram", true)
    val stageDrives = BoolPref("stage_drives", true)
    val stageNet = BoolPref("stage_net", true)
    val stageLyrics = BoolPref("stage_lyrics", true)
    val stageVolume = BoolPref("stage_volume", true)
    /** Update 200, Lyrics stage: lyric timing for every song, in ms (+ = the words come earlier). */
    val stageLyricOffsetMs = IntPref("stage_lyric_offset_ms", 0, -5000..5000)
    val stageStatus = EnumPref("stage_status", StageStatus.AUTO, StageStatus.values())
}
