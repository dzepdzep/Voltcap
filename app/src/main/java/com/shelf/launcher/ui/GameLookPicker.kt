package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.CardArtType
import com.shelf.launcher.data.cardArtFor
import com.shelf.launcher.data.HomeLayout

/*
 * "Game look": what every game is drawn as — the plain cover, the wide capsule, the real thing (the console's case at
 * one size or at real sizes, the cartridge / disc, discs) or a picture — one choice for every home layout (since
 * update 82), in Settings › Game look › Covers, cases & discs and from the R1 panel's "Game look" tile (steps through
 * the five main ones). The home layout only decides where the games sit.
 */

/** The looks the R1 tile steps through, in order. */
internal val GAME_LOOKS: List<Pair<CardArtType, String>> = listOf(
    CardArtType.COVER to "Covers",
    CardArtType.CASE to "Real cases",
    CardArtType.CASE_REAL to "Cases · real sizes",
    CardArtType.CARTRIDGE_DISC to "Cartridges & discs",
    CardArtType.DISC to "Discs only"
)

/** Every look, with its short name and what it is (the Settings picker). */
private val ALL_LOOKS: List<Triple<CardArtType, String, String>> = listOf(
    Triple(CardArtType.COVER, "Covers", "The game's front cover."),
    Triple(CardArtType.CAPSULE, "Wide capsules", "The wide capsule art (the long banner, like Steam's library)."),
    Triple(CardArtType.CASE, "Real cases", "The console's own case with the cover in it, every case the same size."),
    Triple(CardArtType.CASE_REAL, "Cases · real sizes", "Each case at its real size next to the others: a DS case smaller than a PS2 one, a Saturn case taller."),
    Triple(CardArtType.CARTRIDGE_DISC, "Cartridges & discs", "The real thing: cartridges, cards, discs and UMDs, each in its own shape."),
    Triple(CardArtType.DISC, "Discs only", "Every game as a disc. Cartridge games get a plain CD with their label (switch below)."),
    Triple(CardArtType.HERO, "Hero art", "The game's wide background art, cropped to the card."),
    Triple(CardArtType.SCREENSHOT, "Screenshots", "A screenshot from the game.")
)

/** Short name of a look. */
internal fun gameLookLabel(type: CardArtType): String = ALL_LOOKS.firstOrNull { it.first == type }?.second ?: type.label

/** The look after [type] for the R1 tile (any other artwork goes back to Covers). */
internal fun nextGameLook(type: CardArtType): CardArtType {
    val i = GAME_LOOKS.indexOfFirst { it.first == type }
    return GAME_LOOKS[(i + 1).mod(GAME_LOOKS.size)].first
}

/**
 * A step of the R1 tile: a look plus the case switches it needs (case + disc: the disc half out of the case). [focus]:
 * how the game you're on is drawn with covers (update 104: wide capsule, wide for the first game only, or a cover);
 * null leaves it as it is.
 */
private class LookStep(
    val type: CardArtType, val discInCase: Boolean, val leaning: Boolean, val label: String, val tile: Boolean = false,
    val focus: com.shelf.launcher.data.FocusedCardStyle? = null,
    /** Update 113: the Spines look. */
    val spine: Boolean = false,
    /** Update 125: Cartridges only (with the Cartridges & discs type). */
    val carts: Boolean = false,
    /** Update 141: the 3D models (your 3D shape library) with the Cartridges & discs type. */
    val models: Boolean = false,
    /** Update 151: 3D case + disc (the 3D box with the disc half out of it). */
    val disc3d: Boolean = false
)

private val LOOK_STEPS = listOf(
    LookStep(CardArtType.COVER, false, false, "Covers · wide capsule", focus = com.shelf.launcher.data.FocusedCardStyle.WIDE),
    LookStep(CardArtType.COVER, false, false, "Covers · first one wide", focus = com.shelf.launcher.data.FocusedCardStyle.FIRST_ONLY),
    LookStep(CardArtType.COVER, false, false, "Covers", focus = com.shelf.launcher.data.FocusedCardStyle.COVER),
    LookStep(CardArtType.COVER, false, false, "Tile (square tiles)", tile = true),
    LookStep(CardArtType.COVER, false, false, "Spines", spine = true),
    // Update 115: every game a real wide capsule.
    LookStep(CardArtType.CAPSULE, false, false, "Wide capsules"),
    LookStep(CardArtType.CASE, false, false, "Real cases"),
    LookStep(CardArtType.CASE_REAL, false, false, "Cases · real sizes"),
    LookStep(CardArtType.CASE, true, false, "Case + disc"),
    // (Update 151: the 3D case with its disc.)
    LookStep(CardArtType.CASE, true, false, "3D case + disc", disc3d = true),
    LookStep(CardArtType.CASE, false, true, "Case + its media"),
    LookStep(CardArtType.CARTRIDGE_DISC, false, false, "Cartridges & discs"),
    // (Update 128: "Cartridges" — with your own cartridge pack, R1 › Cartridges.)
    LookStep(CardArtType.CARTRIDGE_DISC, false, false, "Cartridges", carts = true),
    // (Update 141: the 3D models as a look of their own, so they're easy to find — R1 › Game look too.)
    LookStep(CardArtType.CARTRIDGE_DISC, false, false, "3D models", models = true),
    LookStep(CardArtType.DISC, false, false, "Discs only")
)

private fun currentStep(a: AppearanceSettings): LookStep? {
    val t = a.cardArtFor()
    val disc = com.shelf.launcher.data.Features.discCase.value
    val lean = com.shelf.launcher.data.Features.caseWithMedia.value
    val isCase = t == CardArtType.CASE || t == CardArtType.CASE_REAL
    val tile = com.shelf.launcher.data.Features.squareTiles.value
    val spine = com.shelf.launcher.data.Features.spineLook.value
    val carts = com.shelf.launcher.data.Features.cartsOnly.value
    val models = com.shelf.launcher.data.Features.modelFinish.value != ModelFinish.OFF
    // Update 151: 3D case + disc tells itself from Case + disc.
    val d3 = com.shelf.launcher.data.Features.discCase3d.value
    return LOOK_STEPS.firstOrNull {
        it.type == t && (!isCase || (it.leaning == lean && (lean || (it.discInCase == disc && (!disc || it.disc3d == d3))))) &&
            (t != CardArtType.CARTRIDGE_DISC || (if (models) it.models else (!it.models && it.carts == carts))) &&
            (t != CardArtType.COVER || (it.spine == spine && (spine || (it.tile == tile && (tile || it.focus == null || it.focus == a.focusedCardStyle)))))
    }
}

/** Name of the look in use for the R1 tile (the case variants included). */
internal fun currentLookName(a: AppearanceSettings): String = currentStep(a)?.label ?: gameLookLabel(a.cardArtFor())

/** The R1 tile's next look; sets the case switches too (Settings › Game look has them as switches as well). */
internal fun stepGameLook(a: AppearanceSettings): AppearanceSettings {
    val i = LOOK_STEPS.indexOf(currentStep(a))
    val next = LOOK_STEPS[(i + 1).mod(LOOK_STEPS.size)]
    com.shelf.launcher.data.Features.discCase.value = next.discInCase
    com.shelf.launcher.data.Features.caseWithMedia.value = next.leaning
    com.shelf.launcher.data.Features.discCase3d.value = next.disc3d // Update 151
    com.shelf.launcher.data.Features.squareTiles.value = next.tile
    com.shelf.launcher.data.Features.spineLook.value = next.spine
    com.shelf.launcher.data.Features.cartsOnly.value = next.carts
    setModelsLook(next.models)
    // (Update 119: the Grid's "Games as square tiles" drew covers whatever the look was, so the cartridges, discs and
    // cases never showed there: picking one of those turns it off.)
    if (next.type != CardArtType.COVER) com.shelf.launcher.data.Features.gridTiles.value = false
    // Tile never unfolds wide on its own (LauncherScreen.tileLookOn): the player's wide-capsule choice is left as it was.
    return a.copy(cardArtType = next.type, focusedCardStyle = next.focus ?: a.focusedCardStyle)
}

/**
 * Update 141: the "3D models" look on (back to the finish you had, Custom the first time) or off (the finish is kept for
 * next time).
 */
internal fun setModelsLook(on: Boolean) {
    val F = com.shelf.launcher.data.Features
    val now = F.modelFinish.value
    if (on && now == ModelFinish.OFF) F.modelFinish.value = F.modelFinishLast.value.takeIf { it != ModelFinish.OFF } ?: ModelFinish.CUSTOM
    if (!on && now != ModelFinish.OFF) { F.modelFinishLast.value = now; F.modelFinish.value = ModelFinish.OFF }
}

/** The game look picker: every look as a chip, what the picked one is underneath. The same for every layout. */
@Composable
internal fun GameLookPicker(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
    SectionLabel("Games are drawn as")
    MutedNote(
        "One look for every home layout: switching layout only changes where the games sit. " +
            "Also on the R1 panel: the Game look tile."
    )
    Spacer(Modifier.height(8.dp))
    val now = appearance.cardArtType
    // (Update 141: the 3D models are a look of their own: Cartridges & discs with your 3D shape library.)
    val modelsNow = now == CardArtType.CARTRIDGE_DISC && com.shelf.launcher.data.Features.modelFinish.state.value != ModelFinish.OFF &&
        !com.shelf.launcher.data.Features.squareTiles.state.value && !com.shelf.launcher.data.Features.spineLook.state.value
    val cartsNow = now == CardArtType.CARTRIDGE_DISC && com.shelf.launcher.data.Features.cartsOnly.state.value &&
        !com.shelf.launcher.data.Features.squareTiles.state.value && !com.shelf.launcher.data.Features.spineLook.state.value && !modelsNow
    ChipFlow {
        ALL_LOOKS.forEach { (type, label, _) ->
            val on = now == type && !com.shelf.launcher.data.Features.squareTiles.state.value && !com.shelf.launcher.data.Features.spineLook.state.value
            val onNow = on && !(type == CardArtType.CARTRIDGE_DISC && (com.shelf.launcher.data.Features.cartsOnly.state.value || modelsNow))
            Chip((if (onNow) "✓ " else "") + label, accent = onNow) {
                com.shelf.launcher.data.Features.squareTiles.value = false
                com.shelf.launcher.data.Features.spineLook.value = false
                com.shelf.launcher.data.Features.cartsOnly.value = false
                if (type == CardArtType.CARTRIDGE_DISC) setModelsLook(false)
                if (type != CardArtType.COVER) com.shelf.launcher.data.Features.gridTiles.value = false
                onChange(appearance.copy(cardArtType = type))
            }
        }
        // Update 141: every game as its piece from your 3D shape library (3D cartridges, cards and cases).
        Chip((if (modelsNow) "✓ " else "") + "3D models", accent = modelsNow) {
            com.shelf.launcher.data.Features.squareTiles.value = false
            com.shelf.launcher.data.Features.spineLook.value = false
            com.shelf.launcher.data.Features.cartsOnly.value = false
            com.shelf.launcher.data.Features.gridTiles.value = false
            setModelsLook(true)
            onChange(appearance.copy(cardArtType = CardArtType.CARTRIDGE_DISC))
        }
        Chip((if (com.shelf.launcher.data.Features.squareTiles.state.value) "✓ " else "") + "Tile", accent = com.shelf.launcher.data.Features.squareTiles.state.value) {
            com.shelf.launcher.data.Features.cartsOnly.value = false
            com.shelf.launcher.data.Features.squareTiles.value = true
            com.shelf.launcher.data.Features.spineLook.value = false
            onChange(appearance.copy(cardArtType = CardArtType.COVER))
        }
        // Update 125: every game as a cartridge (update 128: on your cartridge pack where it has to be, or for all).
        Chip((if (cartsNow) "✓ " else "") + "Cartridges", accent = cartsNow) {
            com.shelf.launcher.data.Features.squareTiles.value = false
            com.shelf.launcher.data.Features.spineLook.value = false
            setModelsLook(false)
            com.shelf.launcher.data.Features.cartsOnly.value = true
            com.shelf.launcher.data.Features.gridTiles.value = false
            onChange(appearance.copy(cardArtType = CardArtType.CARTRIDGE_DISC))
        }
        // Update 113: every game as its spine.
        val spines = com.shelf.launcher.data.Features.spineLook.state.value
        Chip((if (spines) "✓ " else "") + "Spines", accent = spines) {
            com.shelf.launcher.data.Features.cartsOnly.value = false
            com.shelf.launcher.data.Features.spineLook.value = true
            com.shelf.launcher.data.Features.squareTiles.value = false
            onChange(appearance.copy(cardArtType = CardArtType.COVER))
        }
    }
    Spacer(Modifier.height(8.dp))
    SectionLabel("Cases with a disc")
    ChipFlow {
        LOOK_STEPS.filter { it.discInCase || it.leaning }.forEach { st ->
            val on = currentStep(appearance) === st
            Chip((if (on) "✓ " else "") + st.label, accent = on) {
                com.shelf.launcher.data.Features.cartsOnly.value = false
                com.shelf.launcher.data.Features.discCase.value = st.discInCase
                com.shelf.launcher.data.Features.caseWithMedia.value = st.leaning
                com.shelf.launcher.data.Features.discCase3d.value = st.disc3d // Update 151
                com.shelf.launcher.data.Features.squareTiles.value = false
                com.shelf.launcher.data.Features.spineLook.value = false
                onChange(appearance.copy(cardArtType = st.type))
            }
        }
    }
    MutedNote("Case + disc: disc games show their case with the disc half out of it, turning. 3D case + disc: the same with the 3D box. Case + its media: the case standing, its cartridge or disc leaning against it.")
    Spacer(Modifier.height(6.dp))
    MutedNote(
        if (com.shelf.launcher.data.Features.spineLook.state.value)
            "Spines: every game as its real spine — its own spine scan when ScreenScraper has one (or cut from its box scan), else the console's spine from the template pack; a game with neither shows a strip of its cover with its name. In the Voltcap row and the carousels the game you're on shows its cover."
        else if (com.shelf.launcher.data.Features.squareTiles.state.value)
            "Tile: the Tile home's big square tiles in the Voltcap row, the game you're on a little bigger and kept a square (no wide capsule). Size and shape are in Home › Layout options."
        else if (modelsNow)
            "3D models: every game as its console's piece from your 3D shape library — real cartridges, cards and cases, or your custom cartridges (plain or clear) — with its cover in the window. Real / Custom / Clear and how they stand are under 3D models below. Also on the R1 panel: the Game look tile."
        else if (now == CardArtType.CARTRIDGE_DISC && com.shelf.launcher.data.Features.cartsOnly.state.value)
            "Cartridges: every game as a cartridge. Original: cartridge games keep their own (the real scan, or the drawn one); disc, UMD, Steam, Android, PC and arcade games get a cartridge from your pack with their cover in its window. Custom: every game on your pack. Also on the R1 panel: the Cartridges tile."
        else ALL_LOOKS.firstOrNull { it.first == now }?.third ?: now.label
    )
    // Update 137: where this look's pictures come from (Settings › Metadata › Artwork source per look).
    run {
        val F = com.shelf.launcher.data.Features
        val physical = F.spineLook.state.value || now == CardArtType.CASE || now == CardArtType.CASE_REAL ||
            now == CardArtType.CARTRIDGE_DISC || now == CardArtType.DISC
        val pref = when {
            physical -> null
            now == CardArtType.CAPSULE -> F.artSourceWide
            now == CardArtType.HERO -> F.artSourceHero
            else -> F.artSourceCover
        }
        Spacer(Modifier.height(6.dp))
        if (pref == null) {
            MutedNote("Art for this look: real scans from ScreenScraper (Accounts › ScreenScraper), the cover where there's none.")
            // Update 141: fetch just the games still missing them.
            val prog = com.shelf.launcher.data.SpineScrape.progress
            ChipFlow {
                if (prog != null) Chip("Looking up ${prog.first} / ${prog.second} · Stop", accent = false) { com.shelf.launcher.data.SpineScrape.stop?.invoke() }
                else Chip("Scrape only the missing real covers", accent = false) { com.shelf.launcher.data.SpineScrape.runMissingReal?.invoke() }
            }
        }
        else {
            val cur = pref.state.value
            ChipFlow {
                Chip((if (cur.isBlank()) "✓ " else "") + "Art from: main source", accent = cur.isBlank()) { pref.value = "" }
                com.shelf.launcher.data.ArtSource.values().forEach { src ->
                    val on = cur == src.name
                    Chip((if (on) "✓ " else "") + src.label.substringBefore(" ("), accent = on) { pref.value = src.name }
                }
            }
            MutedNote("Where this look's pictures are fetched from first (new games; Settings › Metadata › Artwork source per look fetches them all again).")
        }
    }
    // Update 136: Art drop for the box art looks (the cover under the case template's top band).
    if (now == CardArtType.CASE || now == CardArtType.CASE_REAL) {
        Spacer(Modifier.height(6.dp))
        ArtPositionSliders(model = false)
        MutedNote("Art position: where the cover sits under the case's frame (also R1 › Art position, by dragging). Each look keeps its own.")
    }
    // Update 128: the Cartridges look's options (also the R1 panel's Cartridges tile).
    if (cartsNow) CartridgeOptions()
    // Update 136: your 3D shape library.
    ModelOptions()
    if (appearance.homeLayout == HomeLayout.GRID && com.shelf.launcher.data.Features.gridTiles.state.value) {
        MutedNote("The Grid is set to square tiles (Home › Layout options), which shows covers whatever is picked here.")
    }
    // Update 115: the console's badge on every game's tile.
    Spacer(Modifier.height(10.dp))
    SectionLabel("Console badges")
    val F = com.shelf.launcher.data.Features
    ChipFlow {
        Chip((if (!F.consoleBadges.state.value) "✓ " else "") + "Off", accent = !F.consoleBadges.state.value) { F.consoleBadges.value = false }
        Chip((if (F.consoleBadges.state.value && !F.consoleBadgeTheme.state.value) "✓ " else "") + "Console's colour", accent = F.consoleBadges.state.value && !F.consoleBadgeTheme.state.value) {
            F.consoleBadges.value = true; F.consoleBadgeTheme.value = false
        }
        Chip((if (F.consoleBadges.state.value && F.consoleBadgeTheme.state.value) "✓ " else "") + "Theme's colour", accent = F.consoleBadges.state.value && F.consoleBadgeTheme.state.value) {
            F.consoleBadges.value = true; F.consoleBadgeTheme.value = true
        }
    }
    // Update 125: the console's own logo (Dan Patrick's white set) instead of the small one.
    ChipFlow {
        val logos = F.consoleBadgeLogos.state.value
        Chip((if (!logos) "✓ " else "") + "Small logo", accent = !logos) { F.consoleBadgeLogos.value = false }
        Chip((if (logos) "✓ " else "") + "Big logo", accent = logos) { F.consoleBadges.value = true; F.consoleBadgeLogos.value = true }
    }
    MutedNote("A small badge in the corner of each game's tile: the console's logo in white on the console's own colour (or your theme's). On every look: covers, cases, cartridges, discs and 3D models (not on a spine too slim for it).")
}

/** Update 128: Original / Custom, Opaque / Clear and the 14 cartridge shapes of your pack (Settings › Game look). */
@Composable
private fun CartridgeOptions() {
    val F = com.shelf.launcher.data.Features
    Spacer(Modifier.height(8.dp))
    SectionLabel("Cartridges")
    ChipFlow {
        CartSource.values().forEach { src ->
            val on = F.cartSource.state.value == src
            Chip((if (on) "✓ " else "") + src.label, accent = on) { F.cartSource.value = src }
        }
    }
    ChipFlow {
        val clear = F.cartClear.state.value
        Chip((if (!clear) "✓ " else "") + "Opaque", accent = !clear) { F.cartClear.value = false }
        Chip((if (clear) "✓ " else "") + "Clear", accent = clear) { F.cartClear.value = true }
    }
    Spacer(Modifier.height(4.dp))
    SectionLabel("Cartridge shape")
    ChipFlow {
        CartLook.values().forEach { look ->
            val on = F.cartLook.state.value == look
            Chip((if (on) "✓ " else "") + look.label, accent = on) { F.cartLook.value = look }
        }
    }
    MutedNote("Your cartridge pack: the shape and finish for every game drawn on it. Clear shows the board inside the shell.")
}

/** Update 136: Off / Real / Custom / Clear, the piece for consoles without one, and how they stand (Settings › Game look). */
@Composable
private fun ModelOptions() {
    val F = com.shelf.launcher.data.Features
    Spacer(Modifier.height(8.dp))
    SectionLabel("3D models")
    ChipFlow {
        ModelFinish.values().forEach { f ->
            val on = F.modelFinish.state.value == f
            Chip((if (on) "✓ " else "") + f.label, accent = on) { F.modelFinish.value = f }
        }
    }
    if (F.modelFinish.state.value != ModelFinish.OFF) {
        SectionLabel("Stand")
        ChipFlow {
            ModelAngle.values().forEach { a ->
                val on = F.modelAngle.state.value == a
                Chip((if (on) "✓ " else "") + a.label, accent = on) { F.modelAngle.value = a }
            }
        }
        // Update 151: the one you're on turning slowly to and fro, and a faint reflection on the floor.
        ChipFlow {
            val tn = F.modelTurn.state.value
            Chip((if (tn) "✓ " else "") + "Turn the one you're on", accent = tn) { F.modelTurn.value = !tn }
            val rf = F.modelReflect.state.value
            Chip((if (rf) "✓ " else "") + "Floor reflection", accent = rf) { F.modelReflect.value = !rf }
        }
        MutedNote("Every piece stands on a soft shadow and catches a sweep of light when you reach it. Turn the one you're on: it turns slowly from side to side, and stays put with Lite effects or while the screen rests.")
        ChipFlow {
            val nm = F.modelName.state.value
            Chip((if (nm) "✓ " else "") + "Name on the label", accent = nm) { F.modelName.value = !nm }
        }
        // Update 151: disc consoles stand as their 3D case; with Custom / Clear your custom cartridge instead, if you like.
        if (F.modelFinish.state.value != ModelFinish.REAL) {
            SectionLabel("Disc consoles as")
            ChipFlow {
                val cart = F.modelDiscAsCart.state.value
                Chip((if (!cart) "✓ " else "") + "Their 3D case", accent = !cart) { F.modelDiscAsCart.value = false }
                Chip((if (cart) "✓ " else "") + "Your custom cartridge", accent = cart) { F.modelDiscAsCart.value = true }
            }
        }
        ArtPositionSliders(model = true)
        MutedNote("Art position: where the cover sits in the window (also R1 › Art position, by dragging).")
        val have = com.shelf.launcher.data.Packs.count(com.shelf.launcher.data.Packs.Pack.MODELS).first
        if (have == 0) MutedNote("The 3D models aren't on this device yet: Settings › Game look › Extra art packs › 3D models downloads them.")
    }
    MutedNote("Your 3D shape library: with the 3D models look (or Cartridges & discs) every game is its console's piece with its cover in the window: cartridge consoles their 3D cartridge, the disc consoles, DS, 3DS and Switch their 3D case, and the carousels' 3D cases become these cases, turned to the side they face. Real cases stay flat for every console. Real: each console's own cartridge, card or case. Custom: your custom cartridges. Clear: the custom ones in tinted clear plastic. Consoles without a piece keep their usual look; the SD and memory cards are for the Save Vault (its Style › 3D card).")
}

/** Width / height of a game drawn as its spine (Spines look, update 113): a little wider than a real one, so the name reads. */
// (Update 129: 0.13, close to a real chunky spine; it was 0.2, too wide.)
internal const val SPINE_ASPECT = 0.13f

/** Update 113: the Spines look is on for games drawn as [type] (it sits over the cover look). */
@androidx.compose.runtime.Composable
internal fun spinesOn(type: CardArtType): Boolean = com.shelf.launcher.data.Features.spineLook.state.value && type == CardArtType.COVER

/** Update 137: Art position as sliders (Settings › Game look): down / up, left / right, size. */
@Composable
private fun ArtPositionSliders(model: Boolean) {
    val y = ArtPlace.drop(model); val x = ArtPlace.shift(model); val z = ArtPlace.zoom(model)
    PercentSlider("Art drop (down / up)", y.state.value, { y.value = it }, min = -50, max = 100, live = true, valueText = { "$it" })
    PercentSlider("Art left / right", x.state.value, { x.value = it }, min = -50, max = 50, live = true, valueText = { "$it" })
    PercentSlider("Art size", z.state.value, { z.value = it }, min = 50, max = 200, live = true)
}
