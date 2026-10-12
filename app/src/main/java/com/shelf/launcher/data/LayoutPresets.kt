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
    val restoreAll: (() -> AppearanceSettings?)? = null
)

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
     * these (so every preset is the same whatever you had), then its own changes on top.
     */
    val BUILT_IN_KEYS: Set<String> = setOf(
        "homeLayout", "cardArtType", "dockPosition", "focusPosition", "focusedCardStyle", "cardSize", "tileScale",
        "cardCorners", "otherGamesLook", "focusEffect", "tiltOthers", "infoSpacing", "titlePlacement", "showGameLogos", "rowInfo"
    )

    private fun preset(
        id: String, layout: HomeLayout, name: String, description: String,
        tweak: (AppearanceSettings) -> AppearanceSettings = { it }
    ) = LayoutPreset(id, name, description, true, null, { cur ->
        val std = PresetFields.read(AppearanceSettings()).filterKeys { it in BUILT_IN_KEYS }
        tweak(PresetFields.write(cur, std).copy(homeLayout = layout))
    }, layout, BUILT_IN_KEYS)

    /** Two or three presets for each home layout, made for it (Settings › Home › Layout & presets). */
    val builtIns: List<LayoutPreset> = listOf(
        // Voltcap row
        preset("row_std", HomeLayout.CLASSIC, "Voltcap standard",
            "The row along the bottom, the game you're on unfolded into a wide capsule at the left, covers after it."),
        preset("row_shelf", HomeLayout.CLASSIC, "Cover shelf",
            "Big covers with the one you're on in the middle; the others dimmed and tilting away as you scroll.") { s ->
            s.copy(focusedCardStyle = FocusedCardStyle.COVER, focusPosition = FocusPosition.CENTER, cardSize = CardSize.LARGE,
                otherGamesLook = OtherGamesLook.DIMMED, tiltOthers = true, focusEffect = FocusEffect.GLOW)
        },
        preset("row_discs", HomeLayout.CLASSIC, "Disc collection",
            "Every game as its disc in a centred row (cartridge games on a plain CD with their label), a soft glow on the one you're on.") { s ->
            s.copy(cardArtType = CardArtType.DISC, focusedCardStyle = FocusedCardStyle.COVER, focusPosition = FocusPosition.CENTER,
                focusEffect = FocusEffect.GLOW)
        },
        // Tile home
        preset("l_tiles", HomeLayout.TILES, "Tile home",
            "Big rounded square covers in one row, the name above the one you're on, round buttons under them.") { s ->
            s.copy(cardCorners = CardCorners.ROUND, focusEffect = FocusEffect.GLOW)
        },
        preset("tiles_big", HomeLayout.TILES, "Big tiles",
            "The same tiles a good deal bigger, in the middle of the screen — easy to read on a small handheld.") { s ->
            s.copy(cardCorners = CardCorners.ROUND, focusEffect = FocusEffect.GLOW, tileScale = 130, dockPosition = DockPosition.MIDDLE)
        },
        preset("tiles_media", HomeLayout.TILES, "Cartridges & discs",
            "Each game as the real thing — its cartridge, card or disc — standing in the tile row.") { s ->
            s.copy(cardArtType = CardArtType.CARTRIDGE_DISC, focusEffect = FocusEffect.GLOW)
        },
        // Hero carousel
        preset("l_hero", HomeLayout.HERO, "Hero carousel",
            "The game's art full screen with its logo, a curved column of covers beside it.") { s ->
            s.copy(showGameLogos = true, focusEffect = FocusEffect.GLOW)
        },
        preset("hero_boxes", HomeLayout.HERO, "Hero with boxes",
            "The same, with the console's real cases in the column instead of plain covers.") { s ->
            s.copy(cardArtType = CardArtType.CASE, showGameLogos = true, focusEffect = FocusEffect.GLOW)
        },
        preset("hero_discs", HomeLayout.HERO, "Hero with discs",
            "Discs spinning down the column beside the full-screen art.") { s ->
            s.copy(cardArtType = CardArtType.DISC, showGameLogos = true, focusEffect = FocusEffect.GLOW)
        },
        // Grid
        preset("l_grid", HomeLayout.GRID, "Cover wall",
            "The whole category as covers filling the screen, a glow on the one you're on.") { s ->
            s.copy(cardSize = CardSize.MEDIUM, focusEffect = FocusEffect.GLOW)
        },
        preset("grid_capsules", HomeLayout.GRID, "Capsule grid",
            "Fewer, bigger cards: every game as its wide capsule art.") { s ->
            s.copy(cardArtType = CardArtType.CAPSULE, cardSize = CardSize.LARGE, focusEffect = FocusEffect.GLOW)
        },
        preset("grid_boxes", HomeLayout.GRID, "Box collection",
            "Lots of small cases at their real sizes side by side, like a shelf seen from the front.") { s ->
            s.copy(cardArtType = CardArtType.CASE_REAL, cardSize = CardSize.SMALL, cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.GLOW)
        },
        // TV room
        preset("l_tv", HomeLayout.TV, "TV room",
            "The game plays on an old TV, its cartridges and discs beside it, the story underneath.") { s ->
            s.copy(cardArtType = CardArtType.CARTRIDGE_DISC, focusEffect = FocusEffect.GLOW)
        },
        preset("tv_boxes", HomeLayout.TV, "TV room with boxes",
            "The same room with the games' cases beside the TV.") { s ->
            s.copy(cardArtType = CardArtType.CASE, focusEffect = FocusEffect.GLOW)
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
            "Flat square tiles on a pale backdrop, no glow — clean and calm.") { s ->
            s.copy(cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.NONE)
        },
        preset("live_soft", HomeLayout.LIVE, "Soft tiles",
            "The same tiles with rounded corners and a soft glow on the one you're on.") { s ->
            s.copy(cardCorners = CardCorners.ROUNDED, focusEffect = FocusEffect.GLOW)
        },
        // Side menu
        preset("l_side", HomeLayout.SIDE, "Side menu",
            "A dark menu with an emblem, the game's art behind it, square covers along the bottom.") { s ->
            s.copy(cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.GLOW)
        },
        preset("side_boxes", HomeLayout.SIDE, "Side menu with boxes",
            "The same with the games' real cases along the bottom.") { s ->
            s.copy(cardArtType = CardArtType.CASE, cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.GLOW)
        },
        // Cross menu
        preset("l_cross", HomeLayout.CROSS, "Cross menu",
            "Categories as icons across the screen, the games as a list going down, with their covers.") { s ->
            s.copy(focusEffect = FocusEffect.GLOW)
        },
        preset("cross_discs", HomeLayout.CROSS, "Cross menu with discs",
            "The same list with each game as its disc.") { s ->
            s.copy(cardArtType = CardArtType.DISC, focusEffect = FocusEffect.GLOW)
        },
        // Spotlight
        preset("l_spot", HomeLayout.SPOT, "Spotlight",
            "The game's art full screen, small tiles on top, its logo big, Play and activity cards.") { s ->
            s.copy(cardCorners = CardCorners.ROUNDED, showGameLogos = true, focusEffect = FocusEffect.GLOW)
        },
        preset("spot_wide", HomeLayout.SPOT, "Spotlight · wide art",
            "The same with wide capsule art in the tiles on top.") { s ->
            s.copy(cardArtType = CardArtType.CAPSULE, cardCorners = CardCorners.ROUNDED, showGameLogos = true, focusEffect = FocusEffect.GLOW)
        },
        // Icon bar
        preset("l_bar", HomeLayout.BAR, "Icon bar",
            "Shortcut icons along the top, a row of square tiles with the selected one big, Start under it.") { s ->
            s.copy(cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.NONE)
        },
        preset("bar_round", HomeLayout.BAR, "Rounded icon bar",
            "The same with rounded tiles and a soft glow.") { s ->
            s.copy(cardCorners = CardCorners.ROUND, focusEffect = FocusEffect.GLOW)
        },
        // Game shelf (update 90)
        preset("l_shelf", HomeLayout.SHELF, "Game shelf",
            "Your games as spines on a bookcase; the one you're on pulled out to show its front.") { s ->
            s.copy(cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.NONE)
        },
        preset("shelf_glow", HomeLayout.SHELF, "Game shelf · glow",
            "The same with a soft glow round the game you pulled out.") { s ->
            s.copy(cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.GLOW)
        },
        // Circuit board (update 106)
        preset("l_circuit", HomeLayout.CIRCUIT, "Circuit board",
            "Voltcap's own: your games as chips along a glowing power line, the one you're on powered up.") { s ->
            s.copy(cardCorners = CardCorners.SQUARE, focusEffect = FocusEffect.GLOW)
        }
    )

    /** The built-in presets made for [layout]. */
    fun forLayout(layout: HomeLayout): List<LayoutPreset> = builtIns.filter { it.layout == layout }
}
