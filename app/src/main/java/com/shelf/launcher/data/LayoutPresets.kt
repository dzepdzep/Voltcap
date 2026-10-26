package com.shelf.launcher.data

import com.shelf.launcher.ui.theme.AppTheme

/**
 * A named bundle of look / layout settings. Presets only ever touch the fields
 * listed in [PresetFields] (never library, accounts, sync, hotkeys, lights,
 * music, second-screen dock apps or other device-specific keys), and never lock
 * anything: every option stays editable after applying one.
 *
 * @param theme colour theme to switch to, or null to leave the theme alone.
 */
data class LayoutPreset(
    val id: String,
    val name: String,
    val description: String,
    val builtIn: Boolean,
    val theme: AppTheme?,
    val apply: (AppearanceSettings) -> AppearanceSettings,
    /** The home layout this preset is made for (built-ins); null = your own preset (it has whatever layout you saved). */
    val layout: HomeLayout? = null,
    /** The fields this preset sets (what "(modified)" is measured on); null = every preset field (your own presets). */
    val keys: Set<String>? = null,
    /** Update 125: your own preset with everything on the home screen saved: puts it all back and returns the settings. */
    val restoreAll: (() -> AppearanceSettings?)? = null,
    /**
     * Update 162: the game look's own switches (Tile, Spines, Cartridges, 3D models, Case + disc…) a built-in preset sets
     * with its card artwork, so the look is exactly the one it names; null = left as they are (your own presets, and the
     * layouts that draw the games their own way).
     */
    val look: PresetLook? = null,
    /** Update 162: the layout's own options and the carousel's view, as Features key -> value text (Features.restore). */
    val features: Map<String, String> = emptyMap(),
    /** Update 162: the carousel on (true) or off (false) for [layout]; null = left as it is. */
    val carousel: Boolean? = null
)

/**
 * Update 162: the switches that make one game look out of a card artwork (as Settings › Game look and the R1 Game look
 * tile set them, GameLookPicker.kt). All off = the plain look of the artwork (Covers, Real cases, Cartridges & discs…).
 */
data class PresetLook(
    val tiles: Boolean = false,
    val spines: Boolean = false,
    val carts: Boolean = false,
    val models: Boolean = false,
    val discInCase: Boolean = false,
    val disc3d: Boolean = false,
    val leaning: Boolean = false
) {
    /**
     * The look's name for a preset's summary ("games as …"); null = the card artwork's own name (Covers, Cases…).
     * Update 202: named as the game look's choices call it now (it was Tile, 3D cartridges, Case + its media…).
     */
    fun label(): String? = when {
        tiles -> "Square tiles"
        spines -> "Spines"
        carts -> "Cartridges & discs, disc games on a cartridge"
        models -> "Cartridges & discs"
        leaning -> "Cases with the disc or cartridge beside them"
        disc3d -> "Cases with the disc half out (3D case)"
        discInCase -> "Cases with the disc half out"
        else -> null
    }
}

/** The fields presets control, as name -> string so snapshots survive enum renames. */
object PresetFields {
    private class F(val key: String, val get: (AppearanceSettings) -> String, val set: (AppearanceSettings, String) -> AppearanceSettings?)

    private inline fun <reified E : Enum<E>> e(
        key: String, crossinline get: (AppearanceSettings) -> E, crossinline set: (AppearanceSettings, E) -> AppearanceSettings
    ) = F(key, { get(it).name }, { s, v -> enumValues<E>().firstOrNull { it.name == v }?.let { set(s, it) } })

    private fun i(key: String, get: (AppearanceSettings) -> Int, set: (AppearanceSettings, Int) -> AppearanceSettings) =
        F(key, { get(it).toString() }, { s, v -> v.toIntOrNull()?.let { set(s, it) } })

    private fun b(key: String, get: (AppearanceSettings) -> Boolean, set: (AppearanceSettings, Boolean) -> AppearanceSettings) =
        F(key, { get(it).toString() }, { s, v -> if (v == "true" || v == "false") set(s, v == "true") else null })

    private val fields: List<F> = listOf(
        // The home layout (row, tiles, hero, grid, TV room, the style homes). Saved presets from before it was a
        // field simply don't have it, so applying them keeps the layout you're on.
        e("homeLayout", { it.homeLayout }, { s, v -> s.copy(homeLayout = v) }),
        e("dockPosition", { it.dockPosition }, { s, v -> s.copy(dockPosition = v) }),
        e("tileDockPosition", { it.tileDockPosition }, { s, v -> s.copy(tileDockPosition = v) }),
        e("focusPosition", { it.focusPosition }, { s, v -> s.copy(focusPosition = v) }),
        e("focusedCardStyle", { it.focusedCardStyle }, { s, v -> s.copy(focusedCardStyle = v) }),
        // The game look (covers, cases, cartridges / discs…), one for every layout since update 82.
        e("cardArtType", { it.cardArtType }, { s, v -> s.copy(cardArtType = v) }),
        e("cardSize", { it.cardSize }, { s, v -> s.copy(cardSize = v) }),
        i("tileScale", { it.tileScale }, { s, v -> s.copy(tileScale = v.coerceIn(TILE_SCALE_MIN, TILE_SCALE_MAX)) }),
        e("cardCorners", { it.cardCorners }, { s, v -> s.copy(cardCorners = v) }),
        e("otherGamesLook", { it.otherGamesLook }, { s, v -> s.copy(otherGamesLook = v) }),
        e("focusEffect", { it.focusEffect }, { s, v -> s.copy(focusEffect = v) }),
        b("focusBorder", { it.focusBorder }, { s, v -> s.copy(focusBorder = v) }),
        b("tiltOthers", { it.tiltOthers }, { s, v -> s.copy(tiltOthers = v) }),
        e("infoSpacing", { it.infoSpacing }, { s, v -> s.copy(infoSpacing = v) }),
        e("density", { it.density }, { s, v -> s.copy(density = v) }),
        e("categoryBar", { it.categoryBar }, { s, v -> s.copy(categoryBar = v) }),
        e("categorySide", { it.categorySide }, { s, v -> s.copy(categorySide = v) }),
        e("categoryScreen", { it.categoryScreen }, { s, v -> s.copy(categoryScreen = v) }),
        e("categoryTileArt", { it.categoryTileArt }, { s, v -> s.copy(categoryTileArt = v) }),
        e("categoryTileStyle", { it.categoryTileStyle }, { s, v -> s.copy(categoryTileStyle = v) }),
        e("titlePlacement", { it.titlePlacement }, { s, v -> s.copy(titlePlacement = v) }),
        e("nameSide", { it.nameSide }, { s, v -> s.copy(nameSide = v) }),
        e("nameBackdrop", { it.nameBackdrop }, { s, v -> s.copy(nameBackdrop = v) }),
        e("logoPlacement", { it.logoPlacement }, { s, v -> s.copy(logoPlacement = v) }),
        b("showGameLogos", { it.showGameLogos }, { s, v -> s.copy(showGameLogos = v) }),
        b("showLegend", { it.showLegend }, { s, v -> s.copy(showLegend = v) }),
        b("showStatusBar", { it.showStatusBar }, { s, v -> s.copy(showStatusBar = v) }),
        e("backgroundStyle", { it.backgroundStyle }, { s, v -> s.copy(backgroundStyle = v) }),
        e("heroFit", { it.heroFit }, { s, v -> s.copy(heroFit = v) }),
        b("sharpHeroes", { it.sharpHeroes }, { s, v -> s.copy(sharpHeroes = v) }),
        e("rowInfo", { it.rowInfo }, { s, v -> s.copy(rowInfo = v) }),
        b("videoPreviews", { it.videoPreviews }, { s, v -> s.copy(videoPreviews = v) }),
        b("playtimeOnHome", { it.playtimeOnHome }, { s, v -> s.copy(playtimeOnHome = v) }),
        b("bannerButtons", { it.bannerButtons }, { s, v -> s.copy(bannerButtons = v) }),
        b("trophyOnBanner", { it.trophyOnBanner }, { s, v -> s.copy(trophyOnBanner = v) }),
        b("progressOnBanner", { it.progressOnBanner }, { s, v -> s.copy(progressOnBanner = v) }),
        b("coverAchievements", { it.coverAchievements }, { s, v -> s.copy(coverAchievements = v) }),
        b("achievementsOnCards", { it.achievementsOnCards }, { s, v -> s.copy(achievementsOnCards = v) }),
        b("favoriteStar", { it.favoriteStar }, { s, v -> s.copy(favoriteStar = v) }),
        b("lastPlayedOnBanner", { it.lastPlayedOnBanner }, { s, v -> s.copy(lastPlayedOnBanner = v) }),
        b("useSecondScreen", { it.useSecondScreen }, { s, v -> s.copy(useSecondScreen = v) }),
        e("secondPage", { it.secondPage }, { s, v -> s.copy(secondPage = v) }),
        e("secondArtType", { it.secondArtType }, { s, v -> s.copy(secondArtType = v) }),
        e("introStyle", { it.introStyle }, { s, v -> s.copy(introStyle = v) })
    )

    val keys: Set<String> get() = fields.map { it.key }.toSet()

    /** Current values of every preset-controlled field. */
    fun read(s: AppearanceSettings): Map<String, String> = fields.associate { it.key to it.get(s) }

    /** Writes [values] onto [s]; unknown keys or values (renamed enums) are skipped. */
    fun write(s: AppearanceSettings, values: Map<String, String>): AppearanceSettings {
        var out = s
        for (f in fields) {
            val v = values[f.key] ?: continue
            out = f.set(out, v) ?: out
        }
        return out
    }
}

object LayoutPresets {
    /**
     * What a built-in preset sets: the home layout, the game look and where things sit — never the theme, the
     * background, the categories, the second screen or anything else. Each starts from Voltcap's standard values for
     * these (so every preset is the same whatever you had), then its own changes on top. Update 162: with the game
     * look's own switches ([LayoutPreset.look]), the layout's own options and its carousel ([LayoutPreset.features],
     * [LayoutPreset.carousel]) — the same set for every preset of one layout, so going from one to another always
     * lands on exactly what it says.
     */
    val BUILT_IN_KEYS: Set<String> = setOf(
        "homeLayout", "cardArtType", "dockPosition", "tileDockPosition", "focusPosition", "focusedCardStyle", "cardSize", "tileScale",
        "cardCorners", "otherGamesLook", "focusEffect", "tiltOthers", "infoSpacing", "titlePlacement", "showGameLogos", "rowInfo"
    )

    /** Update 162: what a preset sets on a layout that draws its games its own way (it keeps your game look). */
    private val KEEP_LOOK_KEYS: Set<String> = BUILT_IN_KEYS - "cardArtType"

    private fun preset(
        id: String, layout: HomeLayout, name: String, description: String,
        /** Update 162: the game look's switches (all off: the artwork's plain look); null keeps your game look as it is. */
        look: PresetLook? = PresetLook(),
        /** Update 162: the layout's own options (and the carousel's view). */
        options: List<Pair<String, String>> = emptyList(),
        /** Update 162: the carousel for this layout on / off (null: left as it is). */
        carousel: Boolean? = null,
        tweak: (AppearanceSettings) -> AppearanceSettings = { it }
    ): LayoutPreset {
        val keys = if (look == null) KEEP_LOOK_KEYS else BUILT_IN_KEYS
        return LayoutPreset(id, name, description, true, null, { cur ->
            val std = PresetFields.read(AppearanceSettings()).filterKeys { it in keys }
            tweak(PresetFields.write(cur, std).copy(homeLayout = layout))
        }, layout, keys, look = look, features = options.toMap(), carousel = carousel)
    }

    // ---- Update 162: each layout's own options as its presets set them (the defaults are Voltcap's own) ----

    /** One setting as a preset sets it: its key and its value as text (as Features.snapshot writes it). */
    private fun <T> o(p: Features.Pref<T>, v: T = p.default): Pair<String, String> = p.key to when (v) {
        is Enum<*> -> v.name
        is Set<*> -> v.joinToString(",")
        else -> v.toString()
    }

    /**
     * The carousel's view (shared by every carousel), with its games all the way to the screen's edges; the 3D-case
     * views come with their glossy floor and dark stage (as picking them in Settings does) and the case you're on facing you.
     */
    private fun view(v: com.shelf.launcher.ui.CarouselView): List<Pair<String, String>> = buildList {
        add(o(Features.carouselView, v))
        add(o(Features.carouselEdge, true))
        if (v.cases) {
            add(o(Features.carouselReflect, true))
            add(o(Features.carouselStage, true))
            add(o(Features.pickedFaces, true))
        }
    }

    private fun gridOpts(tiles: Boolean = false) = listOf(o(Features.gridTiles, tiles))
    private fun heroOpts(side: Features.HeroSideCovers = Features.HeroSideCovers.ALL) = listOf(o(Features.heroSideCovers, side))
    // (Update 241, #110: every TV room preset sets "the case you're on facing you" — it was only in the Cartridges one,
    // so going from it to another TV preset left it as that one had it.)
    private fun tvOpts(games: com.shelf.launcher.ui.TvRoom.Games = com.shelf.launcher.ui.TvRoom.Games.SPINES) =
        listOf(o(Features.tvRoomGames, games), o(Features.pickedFaces, true))
    // (Update 241, #110: the options the Live tiles, Spotlight and Icon bar presets differ by — those layouts draw their
    // tiles with fixed corners and (Live, Icon bar) no focus glow, so corners / glow alone never told their presets apart.)
    private fun liveOpts(art: Boolean = false) =
        listOf(o(Features.liveLight, !art), o(Features.liveArt, art), o(Features.livePage, Features.LivePage.BIG_FOUR))
    private fun spotOpts(shape: Features.TileShape = Features.TileShape.SQUARE) =
        listOf(o(Features.spotShape, shape), o(Features.spotTilesAt, Features.SpotSide.TOP))
    private fun barOpts(art: Boolean = false) =
        listOf(o(Features.barSquares, true), o(Features.barArt, art), o(Features.barGamesAt, Features.SpotSide.TOP))
    private fun shelfOpts(stand: Features.ShelfStand = Features.ShelfStand.SPINES, wood: Features.ShelfLook = Features.ShelfLook.DARK) =
        listOf(o(Features.shelfStand, stand), o(Features.shelfLook, wood))
    private fun hubOpts(art: Boolean = true) = listOf(o(Features.hubArt, art), o(Features.resNextAch))
    private fun resumeOpts(art: Boolean = true) = listOf(o(Features.resArt, art), o(Features.resNextAch))
    private fun chanOpts(cols: Int = Features.chanCols.default, pale: Boolean = true, art: Boolean = false) =
        listOf(o(Features.chanCols, cols), o(Features.chanLight, pale), o(Features.chanArt, art))
    private fun circuitOpts(colors: Features.CircuitColors = Features.CircuitColors.THEME) =
        listOf(o(Features.circuitColors, colors), o(Features.circuitCurrent), o(Features.circuitLabels), o(Features.circuitBackdrop))
    private fun libOpts(cols: Int = Features.libCols.default, panel: Boolean = true) =
        listOf(o(Features.libCols, cols), o(Features.libPanel, panel), o(Features.libTabs, true))
    private fun stacksOpts(depth: Boolean = true, toolbar: Boolean = true, mirror: Boolean = true, cases3d: Boolean = true) = listOf(
        o(Features.stacksDepth, depth), o(Features.stacksToolbar, toolbar), o(Features.stacksBar, true),
        o(Features.stacksMirror, mirror), o(Features.stacks3d, cases3d), o(Features.pickedFaces, true)
    )
    private fun timelineOpts(by: Features.TimelineBy) = listOf(o(Features.timelineBy, by), o(Features.timelineDates, true))
    private fun consoleOpts(photos: Boolean = true, games: Int = Features.consoleStackGames.default) =
        listOf(o(Features.consolePhotos, photos), o(Features.consoleStackGames, games))
    private fun discOpts(player: Features.DiscPlayerLook = Features.DiscPlayerLook.PLAYER, calm: Boolean = true, spin: Boolean = true, blur: Int = Features.discBlur.default) =
        listOf(o(Features.discPlayer, player), o(Features.discCalm, calm), o(Features.discSpin, spin), o(Features.discAbout, true), o(Features.discBlur, blur))
    private fun heroTilesOpts(rows: Int = Features.heroTilesRows.default, size: Int = Features.heroTilesSize.default, pale: Boolean = true) =
        listOf(o(Features.heroTilesRows, rows), o(Features.heroTilesSize, size), o(Features.heroTilesDock, true), o(Features.heroTilesPale, pale))
    private fun twinDeskOpts(clock: Features.TwinClock = Features.TwinClock.ANALOGUE) = listOf(o(Features.twinClock, clock), o(Features.twinCalendar, true))
    private fun twinIconsOpts(cols: Int = Features.twinCols.default) = listOf(o(Features.twinCols, cols))
    private fun crateOpts(wood: Features.CrateWood = Features.CrateWood.LIGHT) = listOf(o(Features.crateSpin, true), o(Features.crateWood, wood))
    private fun cardsOpts(fan: Int = Features.cardsFan.default, frame: Features.CardFrame = Features.CardFrame.CONSOLE) =
        listOf(o(Features.cardsFan, fan), o(Features.cardsSheen, true), o(Features.cardsFrame, frame))

    // (Update 239, #93: the Cube menu's own options.)
    private fun cubeOpts(
        dock: Features.CubeDock = Features.CubeDock.LEFT, home: Boolean = true,
        tint: Features.CubeTint = Features.CubeTint.INDIGO, face: Features.CubeFace = Features.CubeFace.LOGO
    ) = listOf(
        o(Features.cubeHome, home), o(Features.cubeDock, dock), o(Features.cubeFace, face), o(Features.cubeTint, tint),
        o(Features.cubeExtraFaces), o(Features.cubeIdle), o(Features.cubeSize), o(Features.cubeBackdrop), o(Features.cubeReducedMotion)
    )

    /** Update 330: Memory card's options. */
    private fun memCardOpts(back: Features.McBack = Features.McBack.GREY, size: Features.McSize = Features.McSize.MEDIUM) = listOf(
        o(Features.mcSize, size), o(Features.mcSpacing), o(Features.mcArrange), o(Features.mcDepth), o(Features.mcSelected),
        o(Features.mcBack, back), o(Features.mcSaveIcons), o(Features.mcTitle), o(Features.mcHints), o(Features.mcFloat)
    )

    /** Update 330: Case flow's options. */
    private fun caseFlowOpts(side: Features.CfSide = Features.CfSide.FRONTS, curve: Int = 45, reflect: Boolean = true) = listOf(
        o(Features.cfSide, side), o(Features.cfCurve, curve), o(Features.cfSpacing), o(Features.cfSize), o(Features.cfAngle),
        o(Features.cfPitch), o(Features.cfSpeed), o(Features.cfReflect, reflect), o(Features.cfStage), o(Features.cfTitle)
    )

    private val CASES = PresetLook()

    /** A few presets for each home layout, made for it (Settings › Home › Layout & presets; update 162: every layout has its own). */
    val builtIns: List<LayoutPreset> = listOf(
        // Voltcap row (update 162: the carousel off in the row presets, on in the carousel ones).
        preset("row_std", HomeLayout.CLASSIC, "Voltcap standard",
            "The row along the bottom, the game you're on unfolded into a wide capsule at the left, covers after it.", carousel = false),
        preset("row_shelf", HomeLayout.CLASSIC, "Cover shelf",
            "Big covers with the one you're on in the middle; the others dimmed and tilting away as you scroll.", carousel = false) { s ->
            s.copy(focusedCardStyle = FocusedCardStyle.COVER, focusPosition = FocusPosition.CENTER, cardSize = CardSize.LARGE,
                otherGamesLook = OtherGamesLook.DIMMED, tiltOthers = true, focusEffect = FocusEffect.GLOW)
        },
        // (Update 162: "Disc collection" is the Disc station's own preset now; this one is the row of discs.)
        preset("row_discs", HomeLayout.CLASSIC, "Disc row",
            "Every game as its disc in a centred row (cartridge games on a plain CD with their label), a soft glow on the one you're on.", carousel = false) { s ->
            s.copy(cardArtType = CardArtType.DISC, focusedCardStyle = FocusedCardStyle.COVER, focusPosition = FocusPosition.CENTER,
                focusEffect = FocusEffect.GLOW)
        },
        // Update 162: Tile home is no longer a layout (update 112): its presets are the Voltcap row with the Tile look.
        // (Update 235, #15/#16: Tile home is a layout again — its own presets are further down; these stay the row
        // with the Square tile look.)
        preset("l_tiles", HomeLayout.CLASSIC, "Tile row",
            "Big rounded square covers in one row, the name above the one you're on.", look = PresetLook(tiles = true), carousel = false) { s ->
            s.copy(cardCorners = CardCorners.ROUND, focusEffect = FocusEffect.GLOW)
        },
        preset("tiles_big", HomeLayout.CLASSIC, "Big tiles",
            "The same tiles a good deal bigger, in the middle of the screen — easy to read on a small handheld.", look = PresetLook(tiles = true), carousel = false) { s ->
            s.copy(cardCorners = CardCorners.ROUND, focusEffect = FocusEffect.GLOW, cardSize = CardSize.LARGE, dockPosition = DockPosition.MIDDLE)
        },
        preset("tiles_media", HomeLayout.CLASSIC, "Cartridges & discs",
            "Each game as the real thing — its cartridge, card or disc — standing in the row, the one you're on in the middle.", carousel = false) { s ->
            s.copy(cardArtType = CardArtType.CARTRIDGE_DISC, focusedCardStyle = FocusedCardStyle.COVER, focusPosition = FocusPosition.CENTER,
                focusEffect = FocusEffect.GLOW)
        },
        // Update 162: the carousel views on the row.
        preset("row_arch", HomeLayout.CLASSIC, "Arch row",
            "The games fanned in an arch across the screen: the one you're on raised and bigger in front, each step out lower, smaller and leaning outward.",
            options = view(com.shelf.launcher.ui.CarouselView.ARCH), carousel = true) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        preset("row_recede", HomeLayout.CLASSIC, "Receding row",
            "The game you're on big near the left edge, the next ones smaller and turned a little away, stepping back into the distance.",
            options = view(com.shelf.launcher.ui.CarouselView.RECEDE), carousel = true) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        // (Update 235, #15/#16: Tile home's own presets. It draws its games as square tiles itself, so your game look
        // and the Square tile look stay as they are; the carousel off.)
        preset("tile_home", HomeLayout.TILES, "Tile home",
            "Big square tiles in one row, the name above the one you're on with a ring round it, round shortcut buttons under the row.",
            look = null, carousel = false),
        preset("tile_home_big", HomeLayout.TILES, "Big tile home",
            "The same tiles a good deal bigger, in the middle of the screen — easy to read on a small handheld.",
            look = null, carousel = false) { s ->
            s.copy(tileScale = 130, tileDockPosition = DockPosition.MIDDLE)
        },
        // Hero carousel (update 162: the whole column of covers, the column standing still).
        preset("l_hero", HomeLayout.HERO, "Hero carousel",
            "The game's art full screen with its logo, a curved column of covers beside it.", options = heroOpts(), carousel = false) { s ->
            s.copy(showGameLogos = true, focusEffect = FocusEffect.GLOW)
        },
        preset("hero_boxes", HomeLayout.HERO, "Hero with boxes",
            "The same, with the console's real cases in the column instead of plain covers.", options = heroOpts(), carousel = false) { s ->
            s.copy(cardArtType = CardArtType.CASE, showGameLogos = true, focusEffect = FocusEffect.GLOW)
        },
        preset("hero_discs", HomeLayout.HERO, "Hero with discs",
            "Discs down the column beside the full-screen art.", options = heroOpts(), carousel = false) { s ->
            s.copy(cardArtType = CardArtType.DISC, showGameLogos = true, focusEffect = FocusEffect.GLOW)
        },
        // Grid (update 162: the game look's own shapes, not square tiles; the carousel off but in Box flow).
        preset("l_grid", HomeLayout.GRID, "Cover wall",
            "The whole category as covers filling the screen, a glow on the one you're on.", options = gridOpts(), carousel = false) { s ->
            s.copy(cardSize = CardSize.MEDIUM, focusEffect = FocusEffect.GLOW)
        },
        preset("grid_capsules", HomeLayout.GRID, "Capsule grid",
            "Fewer, bigger cards: every game as its wide capsule art.", options = gridOpts(), carousel = false) { s ->
            s.copy(cardArtType = CardArtType.CAPSULE, cardSize = CardSize.LARGE, focusEffect = FocusEffect.GLOW)
        },
        preset("grid_boxes", HomeLayout.GRID, "Box collection",
            "Lots of small cases at their real sizes side by side, like a shelf seen from the front.", options = gridOpts(), carousel = false) { s ->
            s.copy(cardArtType = CardArtType.CASE_REAL, cardSize = CardSize.SMALL, cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.GLOW)
        },
        // Update 162.
        preset("grid_carts", HomeLayout.GRID, "Cartridge wall",
            "Every game as a cartridge on the wall: its own for cartridge consoles, your cartridge pack for discs and the rest.",
            look = PresetLook(carts = true), options = gridOpts(), carousel = false) { s ->
            s.copy(cardArtType = CardArtType.CARTRIDGE_DISC, cardSize = CardSize.MEDIUM, focusEffect = FocusEffect.GLOW)
        },
        preset("grid_boxflow", HomeLayout.GRID, "Box flow",
            "Real 3D cases packed tight either side of the one you're on, turned to their spines; moving, the next one slides out and turns to face you.",
            look = CASES, options = gridOpts() + view(com.shelf.launcher.ui.CarouselView.BOX_FLOW), carousel = true) { s ->
            s.copy(cardArtType = CardArtType.CASE, focusEffect = FocusEffect.GLOW)
        },
        // TV room (update 162: the games beside the TV as a grid in the game look, or the carousel).
        preset("l_tv", HomeLayout.TV, "TV room",
            "The game plays on an old TV, its cartridges and discs beside it, the story underneath.", options = tvOpts()) { s ->
            s.copy(cardArtType = CardArtType.CARTRIDGE_DISC, focusEffect = FocusEffect.GLOW)
        },
        preset("tv_boxes", HomeLayout.TV, "TV room with boxes",
            "The same room with the games' cases beside the TV.", options = tvOpts()) { s ->
            s.copy(cardArtType = CardArtType.CASE, focusEffect = FocusEffect.GLOW)
        },
        // Update 162.
        preset("tv_spines", HomeLayout.TV, "TV room · spine wall",
            "Beside the TV a carousel of 3D cases: the one you're on facing you, the others turned to their spines and packed tight.",
            look = CASES, options = tvOpts(com.shelf.launcher.ui.TvRoom.Games.CAROUSEL) + view(com.shelf.launcher.ui.CarouselView.SPINE_WALL)) { s ->
            s.copy(cardArtType = CardArtType.CASE, focusEffect = FocusEffect.GLOW)
        },
        // (Update 200: renamed from "TV room · 3D models" — the 3D cartridges look draws the drawn shells now; its id stays.)
        // (Update 202: "Cartridges", as Game look calls it now — it uses your Cartridge choice, real shells by default.)
        // (Update 241, #110: it set the same look as "TV room" — every Cartridges & discs look is drawn on the 3D finish since
        // update 202, so models = true changed nothing and the two were the same room. Now every game is a cartridge, disc
        // games too, as its name says; "TV room" keeps the discs as discs.)
        preset("tv_models", HomeLayout.TV, "TV room · Cartridges",
            "Every game as a 3D cartridge beside the TV — each console's real shell, disc games on your cartridge pack — the one you're on facing you.",
            look = PresetLook(carts = true, models = true), options = tvOpts()) { s ->
            s.copy(cardArtType = CardArtType.CARTRIDGE_DISC, focusEffect = FocusEffect.GLOW)
        },
        // Dashboard
        preset("l_dash", HomeLayout.DASH, "Dashboard",
            "Glass panels: a round-button menu, the game big in Now playing, Quick access as covers.") { s ->
            s.copy(cardCorners = CardCorners.ROUNDED, focusEffect = FocusEffect.GLOW)
        },
        preset("dash_wide", HomeLayout.DASH, "Dashboard · wide art",
            "The same panels with every game as its wide capsule art.") { s ->
            s.copy(cardArtType = CardArtType.CAPSULE, cardCorners = CardCorners.ROUNDED, focusEffect = FocusEffect.GLOW)
        },
        // Live tiles
        preset("l_live", HomeLayout.LIVE, "Live tiles",
            "Flat square tiles on a pale backdrop, one big and four small, no glow — clean and calm.", options = liveOpts()) { s ->
            s.copy(cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.NONE)
        },
        // (Update 241, #110: was "Soft tiles" — rounded corners and a glow, but Live tiles draws its tiles square and
        // without a glow whatever those say, so it looked exactly like "Live tiles". Its id stays.)
        preset("live_soft", HomeLayout.LIVE, "Live tiles on art",
            "The same flat tiles over the game's own art instead of the pale backdrop.", options = liveOpts(art = true)) { s ->
            s.copy(cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.NONE)
        },
        // Side menu (update 162: the row along the bottom, or the turned row of cases).
        preset("l_side", HomeLayout.SIDE, "Side menu",
            "A dark menu with an emblem, the game's art behind it, square covers along the bottom.", carousel = false) { s ->
            s.copy(cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.GLOW)
        },
        preset("side_boxes", HomeLayout.SIDE, "Side menu with boxes",
            "The same with the games' real cases along the bottom.", carousel = false) { s ->
            s.copy(cardArtType = CardArtType.CASE, cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.GLOW)
        },
        preset("side_turned", HomeLayout.SIDE, "Side menu · turned row",
            "The same menu with the games as a row of 3D cases along the bottom, each turned a little so its spine shows.",
            look = CASES, options = view(com.shelf.launcher.ui.CarouselView.TURNED), carousel = true) { s ->
            s.copy(cardArtType = CardArtType.CASE, cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.GLOW)
        },
        // Cross menu
        preset("l_cross", HomeLayout.CROSS, "Cross menu",
            "Categories as icons across the screen, the games as a list going down, with their covers.") { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        // (Update 241, #110: was "Cross menu with discs" — the Cross menu's list always shows wide art (or square tiles
        // with the Tile look), never discs, so it looked exactly like "Cross menu". Now the list's square tiles; id kept.)
        preset("cross_discs", HomeLayout.CROSS, "Cross menu · square tiles",
            "The same list with each game as a rounded square tile instead of its wide art.", look = PresetLook(tiles = true)) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        // Spotlight
        preset("l_spot", HomeLayout.SPOT, "Spotlight",
            "The game's art full screen, small square tiles on top, its logo big, Play and activity cards.", options = spotOpts()) { s ->
            s.copy(cardCorners = CardCorners.ROUNDED, showGameLogos = true, focusEffect = FocusEffect.GLOW)
        },
        // (Update 241, #110: the tiles' shape is Spotlight's own option (squares by default), so the capsule artwork alone
        // never showed — it set the tiles to wide now, and both presets set the shape and the tiles on top.)
        preset("spot_wide", HomeLayout.SPOT, "Spotlight · wide art",
            "The same with wide capsule art in the tiles on top.", options = spotOpts(Features.TileShape.WIDE)) { s ->
            s.copy(cardArtType = CardArtType.CAPSULE, cardCorners = CardCorners.ROUNDED, showGameLogos = true, focusEffect = FocusEffect.GLOW)
        },
        // Icon bar
        preset("l_bar", HomeLayout.BAR, "Icon bar",
            "Shortcut icons along the top, a row of square tiles with the selected one big, Start under it, on your theme.", options = barOpts()) { s ->
            s.copy(cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.NONE)
        },
        // (Update 241, #110: the Icon bar draws no focus glow, and its corners stop at 8 dp, so "rounded tiles and a soft
        // glow" was only a slightly rounder corner. Now rounder tiles over the game's art, as the description says.)
        preset("bar_round", HomeLayout.BAR, "Rounded icon bar",
            "The same with rounder tiles, the game's art fading in behind the top of the screen.", options = barOpts(art = true)) { s ->
            s.copy(cardCorners = CardCorners.ROUND, focusEffect = FocusEffect.NONE)
        },
        // Game shelf (update 90; update 162: it draws its own spines and cases, so it keeps your game look).
        preset("l_shelf", HomeLayout.SHELF, "Game shelf",
            "Your games as spines on a bookcase; the one you're on pulled out to show its front.", look = null, options = shelfOpts()) { s ->
            s.copy(cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.NONE)
        },
        preset("shelf_glow", HomeLayout.SHELF, "Turned cases",
            "Every case turned a little on wooden shelves, so its spine and its front both show; a soft glow round the one you pulled out.",
            look = null, options = shelfOpts(Features.ShelfStand.ANGLED, Features.ShelfLook.WOOD)) { s ->
            s.copy(cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.GLOW)
        },
        // Update 162: Console hub, Resume & discover, Channel grid.
        preset("hub_std", HomeLayout.HUB, "Console hub",
            "The game's art full screen, a row of tiles with the one you're on big, Play, Achievements and Details cards under them.", options = hubOpts()) { s ->
            s.copy(cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.GLOW)
        },
        // (Update 241, #110: was "Hub with boxes" — the hub's row always crops every game to a square cover, so the cases
        // never showed and it looked exactly like "Console hub". Now the hub on your theme; id kept.)
        preset("hub_boxes", HomeLayout.HUB, "Hub on your theme",
            "The same hub on your theme's background instead of the game's art — calmer as you move.", options = hubOpts(art = false)) { s ->
            s.copy(cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.GLOW)
        },
        preset("res_std", HomeLayout.RESUME, "Resume & discover",
            "The game you're on as a big square with shortcut tiles under it, the next games in a row, Discover cards.", options = resumeOpts()) { s ->
            s.copy(cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.GLOW)
        },
        preset("res_theme", HomeLayout.RESUME, "Resume on your theme",
            "The same, on your theme's background instead of the game's art — calmer as you move.", options = resumeOpts(art = false)) { s ->
            s.copy(cardCorners = CardCorners.ROUNDED, focusEffect = FocusEffect.GLOW)
        },
        preset("chan_std", HomeLayout.CHAN, "Channel grid",
            "Pages of wide tiles on a pale backdrop, the name on the one you're on, a curved bar with the clock.", options = chanOpts()) { s ->
            s.copy(cardCorners = CardCorners.ROUNDED, focusEffect = FocusEffect.GLOW)
        },
        preset("chan_wide", HomeLayout.CHAN, "Wide channels",
            "Three big channels to a row, every game as its wide capsule art, the game's art behind.",
            options = chanOpts(cols = 3, pale = false, art = true)) { s ->
            s.copy(cardArtType = CardArtType.CAPSULE, cardCorners = CardCorners.ROUNDED, focusEffect = FocusEffect.GLOW)
        },
        // Circuit board (update 106)
        preset("l_circuit", HomeLayout.CIRCUIT, "Circuit board",
            "Voltcap's own: your games as chips along a glowing power line, the one you're on powered up.", options = circuitOpts()) { s ->
            s.copy(cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.GLOW)
        },
        // Update 162.
        preset("circuit_green", HomeLayout.CIRCUIT, "Green board",
            "The same chips on a classic green board with its labels printed beside them, current flowing along the line.",
            options = circuitOpts(Features.CircuitColors.GREEN)) { s ->
            s.copy(cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.GLOW)
        },
        // Update 202: Green Core (2001). Keeps your game look (cases, cartridges, discs show by the sphere).
        preset("gc_std", HomeLayout.GREENCORE, "Green Core",
            "A dark green machine dashboard: a living sphere of light on the left, angled menu panels on the right, your games as a list beside the sphere.",
            look = null, options = listOf(
                o(Features.gcBrowse, Features.GcBrowse.LIST), o(Features.gcAccent, Features.GcAccent.GREEN), o(Features.gcGlow),
                o(Features.gcSphereSpeed), o(Features.gcBgMotion), o(Features.gcReducedMotion)
            )) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        preset("gc_carousel", HomeLayout.GREENCORE, "Green Core carousel",
            "The same machine dashboard with your games on a curved carousel in front of the sphere, a softer glow.",
            look = null, options = listOf(
                o(Features.gcBrowse, Features.GcBrowse.CAROUSEL), o(Features.gcAccent, Features.GcAccent.GREEN), o(Features.gcGlow, 55),
                o(Features.gcSphereSpeed), o(Features.gcBgMotion), o(Features.gcReducedMotion)
            )) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        // Update 162: Library panel.
        preset("lib_std", HomeLayout.LIBRARY, "Library panel",
            "The game you're on in a panel on the left with Play and Details, the category's covers as a grid on the right, tabs on top.", options = libOpts()) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        preset("lib_boxes", HomeLayout.LIBRARY, "Box library",
            "The same panel with the games' real cases in the grid, six to a row.", options = libOpts(cols = 6)) { s ->
            s.copy(cardArtType = CardArtType.CASE, focusEffect = FocusEffect.GLOW)
        },
        preset("lib_wall", HomeLayout.LIBRARY, "Full library",
            "No panel: the grid fills the width, seven covers to a row, the tabs on top.", options = libOpts(cols = 7, panel = false)) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        // Update 162: Case stacks.
        preset("stacks_std", HomeLayout.STACKS, "Case stacks",
            "The game you're on as a big 3D case facing you, stacks of spines left and right, the tool strip on top and the panel along the bottom.",
            look = CASES, options = stacksOpts(), carousel = false) { s ->
            s.copy(cardArtType = CardArtType.CASE, focusEffect = FocusEffect.GLOW)
        },
        preset("stacks_loader", HomeLayout.STACKS, "Loader",
            "Like a disc loader: the cases in a Box flow, packed tight and turned to their spines either side of the one facing you, the tool strip and the panel round them.",
            look = CASES, options = stacksOpts() + view(com.shelf.launcher.ui.CarouselView.BOX_FLOW), carousel = true) { s ->
            s.copy(cardArtType = CardArtType.CASE, focusEffect = FocusEffect.GLOW)
        },
        preset("stacks_flat", HomeLayout.STACKS, "Flat stacks",
            "The stacks straight on, no mirror floor or tool strip, the case in the middle flat — plain and light.",
            look = CASES, options = stacksOpts(depth = false, toolbar = false, mirror = false, cases3d = false), carousel = false) { s ->
            s.copy(cardArtType = CardArtType.CASE, focusEffect = FocusEffect.GLOW)
        },
        // Update 162: Timeline.
        preset("tl_played", HomeLayout.TIMELINE, "Timeline",
            "Your games as a history by when you last played them, a row for each month, the dates under them.",
            options = timelineOpts(Features.TimelineBy.LAST_PLAYED)) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        preset("tl_released", HomeLayout.TIMELINE, "Release history",
            "From the oldest game to the newest, a row of cases for each year it came out.",
            options = timelineOpts(Features.TimelineBy.RELEASED)) { s ->
            s.copy(cardArtType = CardArtType.CASE, focusEffect = FocusEffect.GLOW)
        },
        preset("tl_added", HomeLayout.TIMELINE, "New arrivals",
            "The games you added last at the top, a row for each month they came in.",
            options = timelineOpts(Features.TimelineBy.ADDED)) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        // Update 162: Console stack.
        preset("con_std", HomeLayout.CONSOLES, "Console stack",
            "Your consoles piled on a shelf, the one you're in pulled out and lit, six of its games fanned out of it.", options = consoleOpts()) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        preset("con_drawn", HomeLayout.CONSOLES, "Drawn consoles",
            "Each console drawn as a box in its colour with its logo, more games sliding out — no pictures to look up.",
            options = consoleOpts(photos = false, games = 8)) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        // Update 162: Disc station (always discs: your game look stays).
        preset("disc_collection", HomeLayout.DISCS, "Disc collection",
            "One portable CD player: the game you're on is the disc turning in the tray, its story beside it, the screen behind kept calm.",
            look = null, options = discOpts()) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        preset("disc_art", HomeLayout.DISCS, "Disc on its art",
            "The disc sits on the game's own art, softly blurred, with the home screen's background behind as everywhere else.",
            look = null, options = discOpts(player = Features.DiscPlayerLook.ART, calm = false, blur = 8)) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        preset("disc_still", HomeLayout.DISCS, "Still player",
            "The CD player with the disc standing still and a calm screen — the lightest on the battery.",
            look = null, options = discOpts(spin = false)) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        // Update 162: Hero tiles (square tiles of their own: your game look stays).
        preset("ht_std", HomeLayout.HERO_TILES, "Hero tiles",
            "The game's big picture across the top, two rows of square tiles on a pale backdrop, the app dock at the bottom.",
            look = null, options = heroTilesOpts()) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        preset("ht_big", HomeLayout.HERO_TILES, "One big row",
            "One row of big tiles under the picture, on your theme's background.",
            look = null, options = heroTilesOpts(rows = 1, size = 100, pale = false)) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        // Update 162: the two-screen layouts.
        preset("twin_desk", HomeLayout.TWIN_DESK, "Twin desk",
            "A round clock and this month's calendar on squared paper on top, your games as wide panels on the second screen.",
            look = null, options = twinDeskOpts()) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        preset("twin_desk_digital", HomeLayout.TWIN_DESK, "Digital desk",
            "The same desk with a big digital clock beside the calendar.",
            look = null, options = twinDeskOpts(Features.TwinClock.DIGITAL)) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        preset("twin_icons", HomeLayout.TWIN_ICONS, "Twin icons",
            "The game you're on as a big framed icon on top, five square icons to a row on the second screen.",
            look = null, options = twinIconsOpts()) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        preset("twin_icons_big", HomeLayout.TWIN_ICONS, "Big icons",
            "The same with four bigger icons to a row — easier to hit with a thumb.",
            look = null, options = twinIconsOpts(4)) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        // Update 307: Twin showcase.
        preset("twin_showcase", HomeLayout.TWIN_SHOWCASE, "Twin showcase",
            "The game you're on as its big picture on top, moving with you; two rows of square icons on the second screen, running on to the right.",
            look = null, options = listOf(o(Features.twinShowRows, 2))) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        preset("twin_showcase_one", HomeLayout.TWIN_SHOWCASE, "Showcase · one row",
            "The same with one row of big icons — easier to hit with a thumb.",
            look = null, options = listOf(o(Features.twinShowRows, 1))) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        // Update 162: Record crate.
        preset("crate_std", HomeLayout.CRATE, "Record crate",
            "Your games as sleeves in a light wooden crate; the one you're on lifts out and its record slides out, turning.",
            look = null, options = crateOpts()) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        preset("crate_shop", HomeLayout.CRATE, "Record shop",
            "A dark wooden crate like a shop's back room, the record turning as you flip through.",
            look = null, options = crateOpts(Features.CrateWood.DARK)) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        // Update 162: Card binder.
        preset("cards_std", HomeLayout.CARDS, "Card binder",
            "Your games as collectible cards framed in their console's colour, fanned out like a hand, a shine on the one you land on.",
            look = null, options = cardsOpts()) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        preset("cards_collector", HomeLayout.CARDS, "Card collector",
            "A wider fan of cards framed in each game's own colours, mastered games in gold.",
            look = null, options = cardsOpts(fan = 10, frame = Features.CardFrame.ART)) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        // (Update 239, #93: Cube menu. Keeps your game look — the games in the browser are drawn in it.)
        preset("cube_std", HomeLayout.CUBE, "Cube menu",
            "A translucent cube in a dark indigo room, its sides your categories, its top Apps and its bottom Settings; open a face and the cube docks to the left while the games fill the screen.",
            look = null, options = cubeOpts()) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        // Update 330: Memory card. Keeps your game look (the icons are their own drawing).
        preset("memcard_std", HomeLayout.MEMCARD, "Memory card",
            "Your games as small save icons floating in loose rows on a grey memory-card screen, the game you're on lit from behind, its title in yellow at the top right.",
            look = null, options = memCardOpts()) { s -> s.copy(focusEffect = FocusEffect.GLOW) },
        preset("memcard_blue", HomeLayout.MEMCARD, "Memory card, grey-blue",
            "The same card on a cooler grey-blue screen with bigger icons.",
            look = null, options = memCardOpts(Features.McBack.BLUE, Features.McSize.LARGE)) { s -> s.copy(focusEffect = FocusEffect.GLOW) },
        // Update 330: Case flow. Keeps your game look (the cases are drawn as cases whatever it is).
        preset("caseflow_std", HomeLayout.CASEFLOW, "Case flow",
            "Real 3D cases on a curved carousel on a dark stage, the one you're on facing you, the others turned toward it, reflections on the floor.",
            look = null, options = caseFlowOpts()) { s -> s.copy(focusEffect = FocusEffect.GLOW) },
        preset("caseflow_spines", HomeLayout.CASEFLOW, "Case flow, spines",
            "The cases either side show their spines, on a flatter curve.",
            look = null, options = caseFlowOpts(Features.CfSide.SPINES, 20)) { s -> s.copy(focusEffect = FocusEffect.GLOW) },
        preset("cube_band", HomeLayout.CUBE, "Cube band",
            "The same cube docked in a band along the top while you browse, lilac glass and the faces' names only — more room across for the games.",
            look = null, options = cubeOpts(dock = Features.CubeDock.TOP, tint = Features.CubeTint.LILAC, face = Features.CubeFace.NAME)) { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        }
    )

    /** The built-in presets made for [layout]. */
    fun forLayout(layout: HomeLayout): List<LayoutPreset> = builtIns.filter { it.layout == layout }
}
