package com.shelf.launcher.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.CardArtType
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.FocusedCardStyle
import com.shelf.launcher.data.cardArtFor
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * "Game look": what every game is drawn as — one choice for every home layout (since update 82). The home layout only
 * decides where the games sit.
 *
 * Update 202: four main looks, each with a few plain choices (asked for: about twenty looks with hidden switches behind
 * them had become too confusing to know what was picked):
 *  - Covers: Cover · Wide · Square tile · Spine, and how the game you're on is drawn;
 *  - Cases: the game's real case scan or always the drawn case, all the same size or real sizes, and where its disc or
 *    cartridge is (inside, beside it, half out);
 *  - Cartridges & discs: each console's real shell, your pack's shapes or clear plastic; what disc games are; whether
 *    the game's own cartridge photo is used;
 *  - Art: hero art, screenshots or wide capsules.
 * The R1 panel's Look tile steps through the four; holding it (or Settings › Game look) shows the choices of the one in
 * use, each with a small picture of the game you're on drawn that way. The choices set the same switches as before
 * (Features.squareTiles, spineLook, originalCases, discCase, caseWithMedia, cartsOnly, cartSource, cartClear,
 * modelFinish, authenticCarts, mediaShells, discsForAll and the card artwork), so the layouts, presets and drawing code
 * that read them work as they did; only the confusing combinations are gone from the screens.
 */

/** Every card artwork with its short name (the presets' summaries). Update 202: named as the new looks call them. */
private val ALL_LOOKS: List<Pair<CardArtType, String>> = listOf(
    CardArtType.COVER to "Covers",
    CardArtType.CAPSULE to "Wide capsules",
    CardArtType.CASE to "Cases",
    CardArtType.CASE_REAL to "Cases · real sizes",
    CardArtType.CARTRIDGE_DISC to "Cartridges & discs",
    CardArtType.DISC to "Discs",
    CardArtType.HERO to "Hero art",
    CardArtType.SCREENSHOT to "Screenshots"
)

/** Short name of a look. */
internal fun gameLookLabel(type: CardArtType): String = ALL_LOOKS.firstOrNull { it.first == type }?.second ?: type.label

/** Update 202: the four main looks, in the order the R1 Look tile steps through them. */
internal enum class LookMain(val label: String, val short: String, val about: String) {
    // (Update 280, Toni: "Covers" always the covers — the SteamGridDB portrait art; the wide looks are a main look of
    // their own, and Layout standard follows the home layout.)
    COVERS("Covers", "Covers", "The game's box art as a flat picture: its cover, a square tile or its spine."),
    WIDE("Wide capsules", "Wide", "The game's wide capsule art: every game, the first game only, or the game you're on."),
    CASES("Cases", "Cases", "Each game in its console's case, from the front or turned to show its spine. Steam and Android games get Voltcap's own case when they have no scan."),
    CARTS("Cartridges & discs", "Cartridges", "The game itself: its cartridge, card, UMD or disc, drawn with real depth."),
    ART("Art", "Art", "A picture from the game instead of its box: its hero art or a screenshot."),
    LAYOUT("Layout standard", "Layout", "Each home layout in its own standard look (its first preset's): switching layout switches the look with it.")
}

/** Update 280: the wide-capsule cover styles (the Wide capsules main look); the others are Covers'. */
internal val LookCoverStyle.wide: Boolean
    get() = this == LookCoverStyle.WIDE || this == LookCoverStyle.WIDE_FIRST || this == LookCoverStyle.WIDE_FOCUS

/** Update 202: Covers › Cover style. */
internal enum class LookCoverStyle(val label: String, val about: String) {
    COVER("Cover", "The game's front cover."),
    // (Update 274, Toni: the three wide looks side by side, each one thing — they were split between Cover style, "The
    // game you're on" and Art, and Art › Wide capsules was the same as Wide.)
    WIDE("Wide capsules", "Every game a wide capsule, the ones beside the game you're on too."),
    WIDE_FIRST("Wide · first game only", "The first game is a fixed wide capsule (no animation); the others are covers."),
    WIDE_FOCUS("Wide · when you're on it", "Covers; the game you're on turns from its cover into its wide capsule."),
    TILE("Square tile", "Big rounded squares, the game you're on a little bigger."),
    SPINE("Spine", "Every game as its spine, like boxes on a shelf. The game you're on shows its cover.")
}

/** Update 202: Cases › Its disc or cartridge. */
internal enum class LookCaseMedia(val label: String, val about: String) {
    // (Update 208: named for what you see. Media only — the disc, UMD or cartridge without its case — is the Cartridges &
    // discs look.)
    INSIDE("Case only", "Just the case (the default). Its disc or cartridge is inside: open the game to take it out. For the media alone, pick Cartridges & discs."),
    BESIDE("Case + media", "The case standing, its disc, UMD or cartridge leaning against it."),
    HALF("Case + disc half out", "Disc games show their disc half out of the case, turning.")
}

/** Update 202: Cartridges & discs › Cartridge (each the 3D finish the drawn pieces use, PhysicalMedia.drawn3dPick). */
internal enum class LookCart(val label: String, val about: String, val finish: ModelFinish) {
    REAL("Real shell for each console", "Each console's own cartridge as the real one: its shape, its region, its back.", ModelFinish.REAL),
    PACK("Your pack's shapes", "Every cartridge in the shape picked from your cartridge pack (below).", ModelFinish.CUSTOM),
    CLEAR("Clear plastic", "Your pack's shape in clear plastic, the board showing inside.", ModelFinish.CLEAR)
}

/** Update 202: Cartridges & discs › Disc games. */
internal enum class LookDiscGames(val label: String, val about: String) {
    DISC("Their disc", "Disc games show their disc (PSP games their UMD)."),
    CASE("Their case", "Disc games show their case with the disc half out of it."),
    CART("A cartridge too", "Disc games go on a cartridge as well, their cover as the label."),
    ALL("Every game as a disc", "Every game is a disc: cartridge games get a plain CD with their label on it.")
}

/**
 * Update 204: Cartridges & discs › Show as — the three ways the look draws your games (also steps on the R1 Look tile).
 * Mix is each game's own medium; Cartridges / Discs put every game on one kind, Voltcap's own where a game has none.
 * They are the old Disc games choices underneath (Their disc or Their case = Mix, A cartridge too = Cartridges, Every
 * game as a disc = Discs), so nothing picked before changes.
 */
internal enum class LookShowAs(val label: String, val short: String, val about: String) {
    MIX("Mix", "Mix", "Every game its own: cartridge games their cartridge, disc games their disc. Android, Steam and PC games get Voltcap's own."),
    CARTS("Cartridges", "Cartridges", "Every game a cartridge: disc games, Android, Steam and PC games on a custom cartridge with their cover as the label."),
    DISCS("Discs", "Discs", "Every game a disc: cartridge games, Android, Steam and PC games on a custom CD with their cover printed on it.")
}

/** Update 204: the Show as in use (read off the Disc games choice). */
internal fun showAsNow(a: AppearanceSettings): LookShowAs = when (discGamesNow(a)) {
    LookDiscGames.ALL -> LookShowAs.DISCS
    LookDiscGames.CART -> LookShowAs.CARTS
    LookDiscGames.DISC, LookDiscGames.CASE -> LookShowAs.MIX
}

/** Update 204: picks Show as [v]; Mix brings back its disc games' choice (their disc / their case). */
internal fun pickShowAs(v: LookShowAs, a: AppearanceSettings): AppearanceSettings {
    if (v != LookShowAs.DISCS && Features.modelFinish.value == ModelFinish.OFF) pickCart(cartLast())
    return when (v) {
        LookShowAs.MIX -> pickDiscGames(
            if (LookMemory.get("mixDiscs") == LookDiscGames.CASE.name) LookDiscGames.CASE else LookDiscGames.DISC, a
        )
        LookShowAs.CARTS -> pickDiscGames(LookDiscGames.CART, a)
        // (Picked here: cartridge games become discs too, Features.discsForAll.)
        LookShowAs.DISCS -> { Features.discsForAll.value = true; pickDiscGames(LookDiscGames.ALL, a) }
    }
}

/** Update 202: Art. */
internal enum class LookArt(val label: String, val about: String, val type: CardArtType) {
    HERO("Hero art", "The game's wide background art, cropped to the card.", CardArtType.HERO),
    SCREENSHOT("Screenshots", "A screenshot from the game.", CardArtType.SCREENSHOT)
    // (Update 274: Wide capsules moved to Covers › Cover style — here it was the same look under a second name.)
}

/**
 * Update 202: what each main look had (Features.lookMemory), so stepping through the four brings each back as you left
 * it — Cases' "Half out" and Cartridges' "Their case" share one switch (discCase), so each look puts its own back.
 */
private object LookMemory {
    private fun read(): Map<String, String> = Features.lookMemory.value.split(';').mapNotNull { p ->
        val i = p.indexOf('=')
        if (i <= 0) null else p.substring(0, i) to p.substring(i + 1)
    }.toMap()

    fun get(key: String): String? = read()[key]

    fun put(vararg kv: Pair<String, String>) {
        val m = read().toMutableMap()
        kv.forEach { (k, v) -> m[k] = v }
        Features.lookMemory.value = m.entries.joinToString(";") { "${it.key}=${it.value}" }
    }
}

private inline fun <reified E : Enum<E>> enumOr(name: String?, def: E): E = enumValues<E>().firstOrNull { it.name == name } ?: def

/** Update 202: the main look in use (wide capsules count as Covers unless they were picked under Art). */
internal fun lookMainOf(a: AppearanceSettings): LookMain = if (Features.lookFromLayout.value) LookMain.LAYOUT else when (a.cardArtFor()) {
    // (Update 280: a wide cover style is the Wide capsules look.)
    CardArtType.COVER -> if (coverStyleNow(a).wide) LookMain.WIDE else LookMain.COVERS
    CardArtType.CAPSULE -> LookMain.WIDE
    CardArtType.CASE, CardArtType.CASE_REAL -> LookMain.CASES
    CardArtType.CARTRIDGE_DISC, CardArtType.DISC -> LookMain.CARTS
    CardArtType.HERO, CardArtType.SCREENSHOT -> LookMain.ART
}

private fun coverStyleNow(a: AppearanceSettings): LookCoverStyle = when {
    Features.spineLook.value -> LookCoverStyle.SPINE
    Features.squareTiles.value -> LookCoverStyle.TILE
    a.cardArtFor() == CardArtType.CAPSULE -> LookCoverStyle.WIDE
    a.focusedCardStyle == FocusedCardStyle.FIRST_ONLY -> LookCoverStyle.WIDE_FIRST
    a.focusedCardStyle == FocusedCardStyle.WIDE -> LookCoverStyle.WIDE_FOCUS
    else -> LookCoverStyle.COVER
}

private fun caseMediaNow(): LookCaseMedia = when {
    Features.caseWithMedia.value -> LookCaseMedia.BESIDE
    Features.discCase.value -> LookCaseMedia.HALF
    else -> LookCaseMedia.INSIDE
}

/** Update 202: the cartridge choice in use (read off the 3D finish; a flat look from before off the Cartridges switches). */
internal fun cartNow(): LookCart = when (Features.modelFinish.value) {
    ModelFinish.REAL -> LookCart.REAL
    ModelFinish.CUSTOM -> LookCart.PACK
    ModelFinish.CLEAR -> LookCart.CLEAR
    ModelFinish.OFF -> if (Features.cartsOnly.value && Features.cartSource.value == CartSource.CUSTOM)
        (if (Features.cartClear.value) LookCart.CLEAR else LookCart.PACK) else LookCart.REAL
}

internal fun discGamesNow(a: AppearanceSettings): LookDiscGames = when {
    a.cardArtFor() == CardArtType.DISC -> LookDiscGames.ALL
    Features.cartsOnly.value -> LookDiscGames.CART
    Features.discCase.value -> LookDiscGames.CASE
    else -> LookDiscGames.DISC
}

/** Update 202: the cartridge finish kept for Cartridges & discs while another look is on (Features.modelFinishLast). */
private fun cartLast(): LookCart = when (Features.modelFinishLast.value) {
    ModelFinish.CUSTOM -> LookCart.PACK
    ModelFinish.CLEAR -> LookCart.CLEAR
    else -> LookCart.REAL
}

private fun pickCoverStyle(s: LookCoverStyle, a: AppearanceSettings): AppearanceSettings {
    val F = Features
    F.lookFromLayout.value = false
    // (Update 280: Covers and Wide capsules each remember their own style.)
    if (s.wide) LookMemory.put("main" to LookMain.WIDE.name, "wide" to s.name)
    else LookMemory.put("main" to LookMain.COVERS.name, "cover" to s.name)
    F.squareTiles.value = s == LookCoverStyle.TILE
    F.spineLook.value = s == LookCoverStyle.SPINE
    // (Update 119: the Grid's square tiles drew covers whatever the look was: wide capsules turn them off.)
    if (s == LookCoverStyle.WIDE) F.gridTiles.value = false
    // (Update 274: each wide look sets the game you're on itself — they were a second choice under Cover.)
    val focus = when (s) {
        LookCoverStyle.WIDE_FIRST -> FocusedCardStyle.FIRST_ONLY
        LookCoverStyle.WIDE_FOCUS -> FocusedCardStyle.WIDE
        else -> FocusedCardStyle.COVER
    }
    // (The first-game-only capsule stands still; the one you're on keeps its cover → wide animation, on if it was off.)
    if (s == LookCoverStyle.WIDE_FIRST) F.capsuleRevealFirst.value = Features.CapsuleReveal.NONE
    if (s == LookCoverStyle.WIDE_FOCUS && F.capsuleReveal.value == Features.CapsuleReveal.NONE) F.capsuleReveal.value = Features.CapsuleReveal.ROLL
    return pickFocus(focus, a.copy(cardArtType = if (s == LookCoverStyle.WIDE) CardArtType.CAPSULE else CardArtType.COVER))
}

private fun pickFocus(f: FocusedCardStyle, a: AppearanceSettings): AppearanceSettings {
    // (Update 166: the carousels unfold the game you're on only with plain covers and "Wide capsule".)
    Features.carouselWide.value = f == FocusedCardStyle.WIDE && a.cardArtFor() == CardArtType.COVER
    // Update 204: "Only the first one wide" is the Voltcap row's fixed capsule at its left end — it only exists in the
    // plain row, so picking it takes the row out of the carousel and puts the game you're on at the left (it seemed not
    // to take: in the carousel nothing changed, or every game unfolded as you reached it).
    if (f == FocusedCardStyle.FIRST_ONLY) {
        val cl = Features.carouselLayouts.value
        if (HomeLayout.CLASSIC.name in cl) Features.carouselLayouts.value = cl - HomeLayout.CLASSIC.name
        return a.copy(focusedCardStyle = f, focusPosition = com.shelf.launcher.data.FocusPosition.LEFT)
    }
    return a.copy(focusedCardStyle = f)
}

private fun pickCaseSize(real: Boolean, a: AppearanceSettings): AppearanceSettings {
    LookMemory.put("main" to LookMain.CASES.name, "caseSize" to if (real) "REAL" else "SAME")
    Features.gridTiles.value = false
    return a.copy(cardArtType = if (real) CardArtType.CASE_REAL else CardArtType.CASE)
}

private fun pickCaseMedia(m: LookCaseMedia) {
    LookMemory.put("caseMedia" to m.name)
    Features.caseWithMedia.value = m == LookCaseMedia.BESIDE
    Features.discCase.value = m == LookCaseMedia.HALF
}

/** Update 202: sets the cartridge choice — its 3D finish now and for next time, and the switches that go with it. */
internal fun pickCart(c: LookCart) {
    val F = Features
    F.modelFinish.value = c.finish
    F.modelFinishLast.value = c.finish
    when (c) {
        // (The flat pieces too — a cartridge leaning against its case, a list mixing consoles — on the real shell.)
        LookCart.REAL -> { F.authenticCarts.value = true; F.cartSource.value = CartSource.ORIGINAL }
        LookCart.PACK -> { F.cartSource.value = CartSource.CUSTOM; F.cartClear.value = false }
        LookCart.CLEAR -> { F.cartSource.value = CartSource.CUSTOM; F.cartClear.value = true }
    }
}

private fun pickDiscGames(d: LookDiscGames, a: AppearanceSettings): AppearanceSettings {
    val F = Features
    LookMemory.put("main" to LookMain.CARTS.name, "discs" to d.name)
    // (Update 204: Mix's own disc games choice, for coming back to Mix from Cartridges / Discs.)
    if (d == LookDiscGames.DISC || d == LookDiscGames.CASE) LookMemory.put("mixDiscs" to d.name)
    F.cartsOnly.value = d == LookDiscGames.CART
    F.discCase.value = d == LookDiscGames.CASE
    F.caseWithMedia.value = false
    F.gridTiles.value = false
    return a.copy(cardArtType = if (d == LookDiscGames.ALL) CardArtType.DISC else CardArtType.CARTRIDGE_DISC)
}

private fun pickArt(k: LookArt, a: AppearanceSettings): AppearanceSettings {
    LookMemory.put("main" to LookMain.ART.name, "art" to k.name)
    Features.gridTiles.value = false
    return a.copy(cardArtType = k.type)
}

/**
 * Update 202: switches to main look [m] as you last had it: the switches only the other looks use go off, this look's
 * own come back from LookMemory (and the cartridge finish from modelFinishLast).
 */
internal fun applyMainLook(m: LookMain, a: AppearanceSettings): AppearanceSettings {
    val F = Features
    // (Update 280: Layout standard takes the layout's own look — layoutStandardLook — and stays on until another look is picked.)
    if (m == LookMain.LAYOUT) { F.lookFromLayout.value = true; return layoutStandardLook(a) }
    F.lookFromLayout.value = false
    if (m != LookMain.COVERS) { F.squareTiles.value = false; F.spineLook.value = false; F.carouselWide.value = false }
    // (Update 200: the 3D finish would put the drawn shells against cases or in the game window, so it's on only here.)
    if (m != LookMain.CARTS) { setModelsLook(false); F.cartsOnly.value = false }
    if (m != LookMain.CASES) F.caseWithMedia.value = false
    if (m == LookMain.COVERS || m == LookMain.ART) F.discCase.value = false
    return when (m) {
        // (Update 280: Covers is always covers — a wide style remembered from before 280 counts as Cover.)
        LookMain.COVERS -> pickCoverStyle(enumOr(LookMemory.get("cover"), LookCoverStyle.COVER).takeIf { !it.wide } ?: LookCoverStyle.COVER, a)
        LookMain.WIDE -> pickCoverStyle(enumOr(LookMemory.get("wide"), LookCoverStyle.WIDE).takeIf { it.wide } ?: LookCoverStyle.WIDE, a)
        LookMain.CASES -> {
            pickCaseView(LookMemory.get("caseView") == "TURNED")
            pickCaseMedia(enumOr(LookMemory.get("caseMedia"), LookCaseMedia.INSIDE))
            pickCaseSize(LookMemory.get("caseSize") == "REAL", a)
        }
        LookMain.CARTS -> {
            pickCart(cartLast())
            pickDiscGames(enumOr(LookMemory.get("discs"), LookDiscGames.DISC), a)
        }
        LookMain.ART -> pickArt(enumOr(LookMemory.get("art"), LookArt.HERO), a)
        LookMain.LAYOUT -> a
    }
}

/** Update 280: Cases › Case view — every case facing you, or every case turned to show its spine (Features.boxSpine). */
private fun pickCaseView(turned: Boolean) {
    LookMemory.put("caseView" to (if (turned) "TURNED" else "FRONT"))
    Features.boxSpine.value = turned
}

/**
 * Update 280 (Toni: "a new one called Layout standard that uses the set from the layout"): [a] in its home layout's own
 * standard look — the look of that layout's first built-in preset (LayoutPresets.builtIns: the card artwork, how the game
 * you're on is drawn, and the look's switches: tiles, spines, cartridges, case with its disc…). Only the look: sizes,
 * positions and the layout's options stay as you have them. A layout that draws its games its own way keeps them so.
 */
internal fun layoutStandardLook(a: AppearanceSettings): AppearanceSettings {
    val p = com.shelf.launcher.data.LayoutPresets.builtIns.firstOrNull { it.layout == a.homeLayout }
    val std = p?.apply(a) ?: a
    val out = a.copy(cardArtType = std.cardArtType, focusedCardStyle = std.focusedCardStyle)
    val F = Features
    // (Update 322, Toni: "Layout standard isn't changing, just the old one" — Tile home's preset has no look of its own,
    // so the look you had stayed. Its standard is the Switch-style home's: covers as square tiles.)
    // (Update 325, Toni: "Layout standard still uses the last used look there": every layout whose presets keep your look
    // — the two-screen and Pocket layouts, Record crate, Card binder, Cube… — or that has no preset kept it. They get a
    // standard of their own: Pocket Quest its real cartridges, Tile home square tiles, the others plain covers.)
    if (p?.look == null) {
        val layout = a.homeLayout
        val carts = layout == com.shelf.launcher.data.HomeLayout.POCKET
        F.squareTiles.value = layout == com.shelf.launcher.data.HomeLayout.TILES
        F.spineLook.value = false; F.carouselWide.value = false
        F.cartsOnly.value = false; F.caseWithMedia.value = false; F.discCase.value = false
        setModelsLook(carts)
        if (carts) F.gridTiles.value = false
        return out.copy(cardArtType = if (carts) CardArtType.CARTRIDGE_DISC else CardArtType.COVER)
    }
    val l = p?.look ?: return out
    val type = out.cardArtType
    F.squareTiles.value = l.tiles
    F.spineLook.value = l.spines
    F.cartsOnly.value = l.carts
    val cartLook = type == CardArtType.CARTRIDGE_DISC || type == CardArtType.DISC
    setModelsLook(cartLook)
    if (cartLook) { F.discCase.value = false; F.caseWithMedia.value = false }
    if (type == CardArtType.CASE || type == CardArtType.CASE_REAL) {
        F.discCase.value = l.discInCase
        F.discCase3d.value = l.disc3d
        F.caseWithMedia.value = l.leaning
    } else F.caseWithMedia.value = false
    if (type != CardArtType.COVER) F.gridTiles.value = false
    // (The wide capsule of the game you're on only with plain covers, as Cover style sets it.)
    F.carouselWide.value = out.focusedCardStyle == FocusedCardStyle.WIDE && type == CardArtType.COVER && !l.tiles && !l.spines
    return out
}

/** Update 280: the layout's standard look, if Layout standard is on and [a] isn't in it yet (a layout was switched); else null. */
internal fun layoutStandardIfDue(a: AppearanceSettings): AppearanceSettings? {
    if (!Features.lookFromLayout.value) return null
    val t = layoutStandardLook(a)
    return t.takeIf { it != a }
}

/**
 * Update 202, once (AppearanceStore.lookRework202): the look in use, read off its switches, written as the new choices
 * — each main look's memory and the cartridge finish — so nothing picked before is lost. Cartridges start on each
 * console's real shell unless your pack's shapes or clear ones were in use (asked for: the researched real backs). A 3D
 * cartridges look never showed the game's own photos, so it keeps them off.
 */
internal fun migrateGameLook202(type: CardArtType) {
    val F = Features
    val wasModels = F.modelFinish.value != ModelFinish.OFF
    val cart = cartNow()
    val main = when (type) {
        CardArtType.COVER, CardArtType.CAPSULE -> LookMain.COVERS
        CardArtType.CASE, CardArtType.CASE_REAL -> LookMain.CASES
        CardArtType.CARTRIDGE_DISC, CardArtType.DISC -> LookMain.CARTS
        CardArtType.HERO, CardArtType.SCREENSHOT -> LookMain.ART
    }
    val cover = when {
        F.spineLook.value -> LookCoverStyle.SPINE
        F.squareTiles.value -> LookCoverStyle.TILE
        type == CardArtType.CAPSULE -> LookCoverStyle.WIDE
        else -> LookCoverStyle.COVER
    }
    val caseMedia = if (main == LookMain.CASES) caseMediaNow() else LookCaseMedia.INSIDE
    val discs = when {
        type == CardArtType.DISC -> LookDiscGames.ALL
        main == LookMain.CARTS && F.cartsOnly.value -> LookDiscGames.CART
        main == LookMain.CARTS && F.discCase.value -> LookDiscGames.CASE
        else -> LookDiscGames.DISC
    }
    val art = LookArt.values().firstOrNull { it.type == type && type != CardArtType.CAPSULE } ?: LookArt.HERO
    LookMemory.put(
        "main" to main.name, "cover" to cover.name, "caseSize" to (if (type == CardArtType.CASE_REAL) "REAL" else "SAME"),
        "caseMedia" to caseMedia.name, "discs" to discs.name, "art" to art.name
    )
    F.modelFinishLast.value = cart.finish
    if (main == LookMain.CARTS) {
        pickCart(cart)
    } else setModelsLook(false)
}

/** Update 202: the game you're on (set by the home screen), for the small pictures of the game look's choices. */
internal object LookPreviewGame {
    var game by mutableStateOf<com.shelf.launcher.data.Game?>(null)
    var cover by mutableStateOf<String?>(null)
    var wide by mutableStateOf<String?>(null)
    var hero by mutableStateOf<String?>(null)
    var shot by mutableStateOf<String?>(null)
    var physical by mutableStateOf<List<Pair<String, String>>?>(null)

    fun set(g: com.shelf.launcher.data.Game?, cover: String?, wide: String?, hero: String?, shot: String?, physical: List<Pair<String, String>>?) {
        if (g == null) return
        this.game = g; this.cover = cover; this.wide = wide; this.hero = hero; this.shot = shot; this.physical = physical
    }
}

// (Update 202: LookStep / LOOK_STEPS — the twenty R1 steps (Covers · wide capsule, Tile, Spines, Real cases, Case + disc,
// 3D case + disc, Case + its media, Cartridges, Custom / Clear cartridges, the three 3D cartridges, Discs only…) and the
// hidden switches each set — are gone: the four main looks above with their choices replace them.)

/**
 * Update 177: what [layout] draws its games as when that's its own (the game looks change nothing there), else null.
 * The R1 Look tile is greyed out there and Settings › Game look's chips can't be picked.
 */
internal fun layoutOwnLook(layout: com.shelf.launcher.data.HomeLayout): String? = when (layout) {
    com.shelf.launcher.data.HomeLayout.HUB, com.shelf.launcher.data.HomeLayout.RESUME, com.shelf.launcher.data.HomeLayout.CHAN,
    com.shelf.launcher.data.HomeLayout.HERO_TILES, com.shelf.launcher.data.HomeLayout.TWIN_ICONS,
    com.shelf.launcher.data.HomeLayout.TWIN_SHOWCASE -> "square and wide tiles"
    com.shelf.launcher.data.HomeLayout.LIVE, com.shelf.launcher.data.HomeLayout.TWIN_DESK -> "wide tiles"
    com.shelf.launcher.data.HomeLayout.SHELF -> "spines and cases on shelves (Layout options › Shelves)"
    com.shelf.launcher.data.HomeLayout.DISCS -> "discs"
    com.shelf.launcher.data.HomeLayout.CRATE -> "record sleeves"
    com.shelf.launcher.data.HomeLayout.CARDS -> "collectible cards"
    com.shelf.launcher.data.HomeLayout.ARCADE -> "arcade cabinets"
    // (Update 183: the Pocket layouts follow your look — cartridges, cases and discs in their frames.)
    else -> null
}

/**
 * Name of the look in use for the R1 tile. Update 202: one of the four main looks. Update 204: Cartridges & discs by
 * its Show as (Mix, Cartridges, Discs).
 */
internal fun currentLookName(a: AppearanceSettings): String = when (val m = lookMainOf(a)) {
    LookMain.CARTS -> showAsNow(a).short
    // (Update 280: the two Cases looks.)
    // (Update 325: "Case + media" named on the tile too.)
    LookMain.CASES -> if (Features.caseWithMedia.value) "Case + media" else if (Features.boxSpine.value) "Cases · turned" else "Cases"
    // (Update 322: Square tiles named as a look of its own on the tile.)
    LookMain.COVERS -> if (Features.squareTiles.value && !Features.spineLook.value) "Tiles" else m.short
    else -> m.short
}

/**
 * The R1 tile's next look. Update 202: the next of the four main looks, as you last had it (its choices: hold the tile,
 * or Settings › Game look).
 */
internal fun stepGameLook(a: AppearanceSettings): AppearanceSettings {
    // Update 204: Covers → Cases → Mix → Cartridges → Discs → Art (Cartridges & discs' three Show as each a step).
    // Update 280: Covers → Wide capsules → Cases → Cases · turned → Mix → Cartridges → Discs → Art → Layout standard.
    return when (lookMainOf(a)) {
        // (Update 322, Toni: "the Tile look is nowhere to be found": Covers → Square tiles → Wide…, a step of its own.)
        LookMain.COVERS -> if (coverStyleNow(a) != LookCoverStyle.TILE) pickCoverStyle(LookCoverStyle.TILE, a) else applyMainLook(LookMain.WIDE, a)
        LookMain.WIDE -> applyMainLook(LookMain.CASES, a).also { pickCaseView(false) }
        // (Update 325, Toni: "the look for the boxes and cases with their physical media is gone, can't find it": a step
        // of its own after Cases · turned — Case + media, the case facing you with its disc / cartridge leaning on it.)
        LookMain.CASES -> when {
            Features.caseWithMedia.value -> { pickCaseMedia(LookCaseMedia.INSIDE); pickShowAs(LookShowAs.MIX, applyMainLook(LookMain.CARTS, a)) }
            !Features.boxSpine.value -> { pickCaseView(true); a }
            else -> { pickCaseView(false); pickCaseMedia(LookCaseMedia.BESIDE); a }
        }
        LookMain.CARTS -> when (showAsNow(a)) {
            LookShowAs.MIX -> pickShowAs(LookShowAs.CARTS, a)
            LookShowAs.CARTS -> pickShowAs(LookShowAs.DISCS, a)
            LookShowAs.DISCS -> applyMainLook(LookMain.ART, a)
        }
        LookMain.ART -> applyMainLook(LookMain.LAYOUT, a)
        LookMain.LAYOUT -> applyMainLook(LookMain.COVERS, a)
    }
}

/**
 * Update 141: the "3D models" look on (back to the finish you had) or off (the finish is kept for next time).
 * Update 202: the Cartridges & discs look's finish; Real (each console's real shell) when none was kept — it was Custom.
 */
internal fun setModelsLook(on: Boolean) {
    val F = com.shelf.launcher.data.Features
    val now = F.modelFinish.value
    if (on && now == ModelFinish.OFF) F.modelFinish.value = F.modelFinishLast.value.takeIf { it != ModelFinish.OFF } ?: ModelFinish.REAL
    if (!on && now != ModelFinish.OFF) { F.modelFinishLast.value = now; F.modelFinish.value = ModelFinish.OFF }
}

/**
 * The game look picker (Settings › Game look): update 202 — the four main looks and the choices of the one in use, each
 * with a small picture of the game you're on drawn that way; then where its art comes from and the console badges. The
 * same for every layout.
 */
@Composable
internal fun GameLookPicker(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
    MutedNote(
        "One look for every home layout: switching layout only changes where the games sit. " +
            "Also on the R1 panel: tap the Look tile for the next look, hold it for these choices."
    )
    // Update 177: a layout that draws its games its own way: the looks are greyed out (they'd change nothing there).
    val ownLook = layoutOwnLook(appearance.homeLayout)
    // Update 204: where the layout draws its games its own way, the looks are left out (they were greyed out): only
    // what applies here is shown. They come back on a layout that draws your look.
    val pqMedia = com.shelf.launcher.data.Features.pqMedia.state.value
    val pqCovers = appearance.homeLayout == HomeLayout.POCKET && pqMedia != com.shelf.launcher.data.Features.PqMedia.LOOK
    if (ownLook != null || pqCovers) {
        Spacer(Modifier.height(4.dp))
        MutedNote(
            if (ownLook != null) "${appearance.homeLayout.label} draws its games as $ownLook, so the game looks don't apply here. They show again on a layout that draws your look."
            else "Pocket Quest is set to show ${if (pqMedia == com.shelf.launcher.data.Features.PqMedia.COVERS) "plain covers" else "cartridges & discs"} (Settings › Home › Layout options › Games shown as). Pick Your game look there to choose a look here."
        )
        return
    }
    Spacer(Modifier.height(8.dp))
    LookChooser(appearance, onChange, enabled = true)
    val now = appearance.cardArtType
    // Update 202: your pack's shape matters with Your pack's shapes / Clear plastic, or with disc games on a cartridge.
    val packNow = (now == CardArtType.CARTRIDGE_DISC) &&
        (cartNow() != LookCart.REAL || com.shelf.launcher.data.Features.cartsOnly.state.value)
    // (Update 202: the look chips — every card artwork, Tile, Spines, the carousels' wide capsule, the Cartridges and the
    // Cases with a disc rows — and the long notes under them stood here; LookChooser's choices replace them.)
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
    // Update 128: your pack's cartridge shape (also the R1 panel's Cartridge tile).
    // (Update 202: with Your pack's shapes, Clear plastic, or disc games on a cartridge.)
    if (packNow) CartridgeOptions()
    // (Update 136: your 3D shape library's options — finish, stand, turn, reflection, name on the label, art position —
    // stood here. Update 200: gone with the retired pre-rendered 3D models; the 3D cartridges looks above replace them.)
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
    // (Update 317ao: where the badge sits — Auto keeps each layout's own place, clear of the trophy count.)
    ChipFlow {
        val at = F.consoleBadgeCorner.state.value
        com.shelf.launcher.data.Features.BadgeCorner.values().forEach { c: com.shelf.launcher.data.Features.BadgeCorner -> Chip((if (at == c) "✓ " else "") + c.label, accent = at == c) { F.consoleBadgeCorner.value = c } }
    }
    MutedNote("A small badge in the corner of each game's tile: the console's logo in white on the console's own colour (or your theme's). On every look: covers, cases, cartridges (3D ones too) and discs (not on a spine too slim for it).")
    // (Update 240, #32/#57: the strip under round discs that carries the trophy count and game status.)
    if (now == CardArtType.DISC || now == CardArtType.CARTRIDGE_DISC) {
        Spacer(Modifier.height(8.dp))
        SwitchRow(Icons.Filled.Album, "Counts under the disc", "On a round disc's tile the trophy count and the game's status sit on a strip under the disc instead of over its edge.", F.discTileChips.state.value, { F.discTileChips.value = it })
    }
}

/** Update 128: Original / Custom, Opaque / Clear and the 14 cartridge shapes of your pack (Settings › Game look). */
@Composable
private fun CartridgeOptions() {
    val F = com.shelf.launcher.data.Features
    Spacer(Modifier.height(8.dp))
    // (Update 202: Original / Custom and Opaque / Clear are the Cartridge choices of Cartridges & discs now.)
    SectionLabel("Cartridge shape")
    ChipFlow {
        CartLook.values().forEach { look ->
            val on = F.cartLook.state.value == look
            Chip((if (on) "✓ " else "") + look.label, accent = on) { F.cartLook.value = look }
        }
    }
    MutedNote("Your cartridge pack: the shape and finish for every game drawn on it. Clear shows the board inside the shell.")
}

// (Update 200: ModelOptions — Settings › Game look › 3D models: Off / Real / Custom / Clear, Stand, Turn the one you're
// on, Floor reflection, Name on the label, Art position — is gone: those only applied to the retired pre-rendered 3D
// models. The finish is picked by the 3D cartridges looks; the one you're on facing you stays in Carousel options.)

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

/** Update 202: what a choice's small picture shows (LookSample). */
private enum class Sample {
    COVER, WIDE, TILE, SPINE, FOCUS_SAME, FOCUS_WIDE, FOCUS_FIRST,
    CASE_SCAN, CASE_DRAWN, CASE_SAME, CASE_REAL, MEDIA_INSIDE, MEDIA_BESIDE, MEDIA_HALF,
    CART_REAL, CART_PACK, CART_CLEAR, DISC_OWN, DISC_CASE, DISC_CART, DISC_ALL, PHOTO_ON, PHOTO_OFF, MIX,
    HERO, SHOT, ART_WIDE
}

/**
 * Update 202: the four main looks and the choices of the one in use, each a card with a small picture of the game you're
 * on drawn that way, its name and one plain line about it (Settings › Game look, and holding the R1 Look tile). Each
 * choice sets the switches the drawing code reads (see the top of this file). [enabled]: false in a layout that draws
 * its games its own way.
 */
@Composable
internal fun LookChooser(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit, enabled: Boolean = true) {
    val F = Features
    val main = lookMainOf(appearance)
    SectionLabel("Main look")
    ChoiceRow {
        LookMain.values().forEach { m ->
            val sample = when (m) {
                LookMain.COVERS -> Sample.COVER
                LookMain.WIDE -> Sample.WIDE
                LookMain.CASES -> Sample.CASE_DRAWN
                LookMain.CARTS -> Sample.CART_REAL
                LookMain.ART -> Sample.HERO
                LookMain.LAYOUT -> Sample.FOCUS_WIDE
            }
            // (Update 323, Toni: "Layout standard did nothing on all layouts": once on, a look changed another way left it
            // marked as the one in use, so picking it again was ignored. It's applied every time it's picked.)
            LookChoiceCard(m.label, m.about, m == main, enabled, sample) { if (m != main || m == LookMain.LAYOUT) onChange(applyMainLook(m, appearance)) }
        }
    }
    when (main) {
        LookMain.COVERS, LookMain.WIDE -> {
            val style = coverStyleNow(appearance)
            SectionLabel(if (main == LookMain.WIDE) "Wide capsules" else "Cover style")
            ChoiceRow {
                // (Update 280: Covers' styles, or the three wide looks.)
                LookCoverStyle.values().filter { it.wide == (main == LookMain.WIDE) }.forEach { s ->
                    val sample = when (s) {
                        LookCoverStyle.COVER -> Sample.COVER
                        LookCoverStyle.WIDE -> Sample.WIDE
                        LookCoverStyle.WIDE_FIRST -> Sample.FOCUS_FIRST
                        LookCoverStyle.WIDE_FOCUS -> Sample.FOCUS_WIDE
                        LookCoverStyle.TILE -> Sample.TILE
                        LookCoverStyle.SPINE -> Sample.SPINE
                    }
                    LookChoiceCard(s.label, s.about, s == style, enabled, sample) { onChange(pickCoverStyle(s, appearance)) }
                }
            }
            // (Update 274: "The game you're on" is gone — its wide choices are Cover styles of their own now.)
        }
        LookMain.CASES -> {
            // (Update 280, Toni: every case from the front, or every case turned to its spine — never some of each.)
            val turned = F.boxSpine.value
            SectionLabel("Case view")
            ChoiceRow {
                LookChoiceCard("From the front", "Every case facing you.", !turned, enabled, Sample.CASE_DRAWN) { pickCaseView(false); onChange(appearance) }
                LookChoiceCard("Turned", "Every case turned to show its spine, as on a shelf.", turned, enabled, Sample.SPINE) { pickCaseView(true); onChange(appearance) }
            }
            val scan = F.originalCases.value
            SectionLabel("Case")
            ChoiceRow {
                LookChoiceCard("Your game's real case scan", "Its own scanned case from ScreenScraper; the drawn case when there's none.", scan, enabled, Sample.CASE_SCAN) {
                    F.originalCases.value = true
                }
                LookChoiceCard("Always the drawn case", "Voltcap's case of its console with the game's cover in it, for every game.", !scan, enabled, Sample.CASE_DRAWN) {
                    F.originalCases.value = false
                }
            }
            val real = appearance.cardArtFor() == CardArtType.CASE_REAL
            SectionLabel("Case size")
            ChoiceRow {
                LookChoiceCard("All the same size", "Every case fills the same space, in lists mixing consoles too.", !real, enabled, Sample.CASE_SAME) {
                    onChange(pickCaseSize(false, appearance))
                }
                LookChoiceCard("Real sizes", "Each case at its real size next to the others: a DS case smaller than a PS2 one.", real, enabled, Sample.CASE_REAL) {
                    onChange(pickCaseSize(true, appearance))
                }
            }
            val media = caseMediaNow()
            SectionLabel("With its media") // (Update 208)
            ChoiceRow {
                LookCaseMedia.values().forEach { m ->
                    val sample = when (m) {
                        LookCaseMedia.INSIDE -> Sample.MEDIA_INSIDE
                        LookCaseMedia.BESIDE -> Sample.MEDIA_BESIDE
                        LookCaseMedia.HALF -> Sample.MEDIA_HALF
                    }
                    LookChoiceCard(m.label, m.about, m == media, enabled, sample) { pickCaseMedia(m) }
                }
            }
            // Update 212: what Android games hold — a disc or a cartridge (AndroidMedia.kt).
            AndroidMediaChoice()
        }
        LookMain.CARTS -> {
            // Update 204: Show as first — Mix, Cartridges or Discs (the old Disc games choices, named for what you see).
            val showAs = showAsNow(appearance)
            SectionLabel("Show as")
            ChoiceRow {
                LookShowAs.values().forEach { v ->
                    val sample = when (v) {
                        LookShowAs.MIX -> Sample.MIX
                        LookShowAs.CARTS -> Sample.DISC_CART
                        LookShowAs.DISCS -> Sample.DISC_ALL
                    }
                    LookChoiceCard(v.label, v.about, v == showAs, enabled, sample) { onChange(pickShowAs(v, appearance)) }
                }
            }
            // The cartridge's shell: Mix and Cartridges (Discs draws none).
            if (showAs != LookShowAs.DISCS) {
                val cart = cartNow()
                SectionLabel("Cartridge")
                ChoiceRow {
                    LookCart.values().forEach { c ->
                        val sample = when (c) {
                            LookCart.REAL -> Sample.CART_REAL
                            LookCart.PACK -> Sample.CART_PACK
                            LookCart.CLEAR -> Sample.CART_CLEAR
                        }
                        LookChoiceCard(c.label, c.about, c == cart, enabled, sample) { pickCart(c) }
                    }
                }
            }
            // Mix: a disc game as its disc, or its case with the disc half out.
            if (showAs == LookShowAs.MIX) {
                val discs = discGamesNow(appearance)
                SectionLabel("Disc games")
                ChoiceRow {
                    listOf(LookDiscGames.DISC, LookDiscGames.CASE).forEach { d ->
                        val sample = if (d == LookDiscGames.DISC) Sample.DISC_OWN else Sample.DISC_CASE
                        LookChoiceCard(d.label, d.about, d == discs, enabled, sample) { onChange(pickDiscGames(d, appearance)) }
                    }
                }
            }
            // Update 212: Android games as a disc or a cartridge (AndroidMedia.kt).
            AndroidMediaChoice()
            // (Update 325, Toni: "some layouts picked the drawn cartridges only — delete them, they keep breaking stuff":
            // "Always the drawn one" is gone. A game's own photo always shows when one is found, else its real shell.)
        }
        LookMain.LAYOUT -> {
            // (Update 280.)
            val p = com.shelf.launcher.data.LayoutPresets.builtIns.firstOrNull { it.layout == appearance.homeLayout }
            SectionLabel("Layout standard")
            MutedNote(
                if (p?.look != null) "This layout is in its standard look: \"${p?.name}\" — ${gameLookLabel(appearance.cardArtFor())}. Switch layout and the look switches with it. Pick another look above to choose your own."
                // (Update 325: a layout without a preset look of its own has a standard too — layoutStandardLook.)
                else "This layout is in its standard look — ${gameLookLabel(appearance.cardArtFor())}. Switch layout and the look switches with it. Pick another look above to choose your own."
            )
        }
        LookMain.ART -> {
            val now = LookArt.values().firstOrNull { it.type == appearance.cardArtFor() }
            SectionLabel("Art")
            ChoiceRow {
                LookArt.values().forEach { k ->
                    val sample = when (k) {
                        LookArt.HERO -> Sample.HERO
                        LookArt.SCREENSHOT -> Sample.SHOT
                    }
                    LookChoiceCard(k.label, k.about, k == now, enabled, sample) { onChange(pickArt(k, appearance)) }
                }
            }
        }
    }
}

/** Update 202: a row of choice cards, scrolling sideways when they don't fit (a phone, the R1 panel). */
@Composable
private fun ChoiceRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Max).horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

/** Update 202: one choice — its picture, its name (ticked when it's the one in use) and a line about it. */
@Composable
private fun LookChoiceCard(label: String, about: String, on: Boolean, enabled: Boolean, sample: Sample, onPick: () -> Unit) {
    val shape = uiRounded(12.dp)
    Column(
        // (Update 274, Toni: "text written with …" — the choices' lines were cut to four with "…"; now the whole text
        // shows, every card in the row as tall as the tallest.)
        Modifier.width(164.dp).fillMaxHeight()
            .clip(shape)
            .background(if (on) ShelfTheme.accent.copy(alpha = 0.16f) else ShelfTheme.colors.surfaceRaised)
            .border(if (on) 2.dp else 1.dp, if (on) ShelfTheme.accent else ShelfTheme.textMuted.copy(alpha = 0.25f), shape)
            .shelfClickable(shape, enabled = enabled) { onPick() }
            .padding(8.dp)
    ) {
        LookSample(sample, Modifier.fillMaxWidth().height(84.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            (if (on) "✓ " else "") + label, color = ShelfTheme.colors.textPrimary.copy(alpha = if (enabled) 1f else 0.5f),
            fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Text(about, color = ShelfTheme.textMuted, fontSize = 11.sp, lineHeight = 14.sp)
    }
}

/** Update 202: a picture filling [modifier], cropped (a grey box while it loads or when there's none). */
@Composable
private fun LookPic(model: Any?, modifier: Modifier, round: Dp = 3.dp, alpha: Float = 1f, fit: Boolean = false) {
    Box(modifier.clip(RoundedCornerShape(round)).background(Color(0xFF2A2D38))) {
        if (model != null) AsyncImage(
            model, null, contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier.fillMaxSize(), alpha = alpha
        )
    }
}

/**
 * Update 202: the cartridge [c] puts the game you're on on (as PhysicalMedia.drawn3dPick picks it): its console's real
 * shell, or your pack's shape where that console's part of the pack is on the device; a console without a shell of its
 * own shows a Super Nintendo one.
 */
private fun samplePick(c: LookCart): MediaPick? {
    val g = LookPreviewGame.game
    val region = CaseCatalog.regionOf(g?.stableKey ?: "")
    val p = g?.platform?.takeIf { CartShells.authenticId(it, region) != null } ?: "SNES"
    val authentic = CartShells.authenticId(p, region) ?: return null
    val packHere = CustomCarts.idFor(p)?.let { com.shelf.launcher.data.Packs.hasCart(it) } == true
    val pack = "pack:" + Features.cartLook.value.key + "~" + (if (c == LookCart.CLEAR) "clear" else "opaque")
    val raw = when (c) {
        LookCart.REAL -> "$authentic~"
        LookCart.PACK -> if (packHere) pack else "$authentic~"
        LookCart.CLEAR -> if (packHere) pack else "$authentic~clear"
    }
    return CartShells.pickOf(raw, p, g?.stableKey)
}

/** Update 202: the game's cover in its console's drawn case ([tpl]: its cover template), for the choices' pictures. */
@Composable
private fun LookDrawnCase(tpl: CoverTpl?, cover: Any?, m: Modifier) {
    if (tpl != null) CoverCase(tpl, cover, CaseArtPlaceholder, m) else LookPic(cover, m.aspectRatio(0.7f))
}

/** Update 202: the game on the cartridge [c] gives it (samplePick), its cover as the label. */
@Composable
private fun LookShell(c: LookCart, cover: Any?, name: String?, m: Modifier) {
    val pick = samplePick(c)
    if (pick != null) CartShellArt(pick.platform, cover, m, title = name, maxPx = 320)
    else PhysicalMedia(MediaShell.GB, cover, m)
}

/** Update 202: the small picture of a choice — the game you're on drawn that way (else a sample shape). */
@Composable
private fun LookSample(s: Sample, modifier: Modifier) {
    val g = LookPreviewGame
    val cover: Any? = g.cover
    val platform = g.game?.platform
    val wideArt: Any? = g.wide ?: g.hero ?: cover
    // A disc console's own disc / UMD, else a plain CD; its case's cover template, else a PS2 one.
    val discShell = platform?.let { MediaShell.of(it) }?.takeIf { it.disc || it == MediaShell.UMD } ?: MediaShell.DISC
    val coverTpl = MediaCovers.used(platform) ?: MediaCovers.of("PS2")
    val drawnCase: @Composable (Modifier) -> Unit = { m -> LookDrawnCase(coverTpl, cover, m) }
    val shell: @Composable (LookCart, Modifier) -> Unit = { c, m -> LookShell(c, cover, g.game?.name, m) }
    Box(modifier.clip(uiRounded(8.dp)).background(Color(0x22000000)).padding(6.dp), contentAlignment = Alignment.Center) {
        when (s) {
            Sample.COVER -> LookPic(cover, Modifier.fillMaxHeight().aspectRatio(2f / 3f))
            Sample.WIDE, Sample.ART_WIDE -> LookPic(wideArt, Modifier.fillMaxWidth().aspectRatio(92f / 43f))
            Sample.TILE -> LookPic(cover, Modifier.fillMaxHeight().aspectRatio(1f), round = 10.dp)
            Sample.SPINE -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                repeat(5) { i -> LookPic(cover, Modifier.fillMaxHeight(if (i == 2) 1f else 0.9f).aspectRatio(SPINE_ASPECT * 1.6f), alpha = if (i == 2) 1f else 0.6f) }
            }
            Sample.FOCUS_SAME, Sample.FOCUS_WIDE, Sample.FOCUS_FIRST -> Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                // A short row: the game you're on bigger — a cover, or its wide capsule in the middle, or the first game
                // wide at the left end.
                val other: @Composable () -> Unit = { LookPic(cover, Modifier.fillMaxHeight(0.42f).aspectRatio(2f / 3f), alpha = 0.6f) }
                val focused: @Composable () -> Unit = {
                    if (s == Sample.FOCUS_SAME) LookPic(cover, Modifier.fillMaxHeight(0.7f).aspectRatio(2f / 3f))
                    else LookPic(wideArt, Modifier.fillMaxHeight(0.5f).aspectRatio(92f / 43f))
                }
                if (s == Sample.FOCUS_FIRST) { focused(); other(); other() } else { other(); focused(); other() }
            }
            Sample.CASE_SCAN -> {
                val scan = typedArtFor(CardArtType.CASE, g.physical, null, platform)
                // (Update 202: the scan fills the case-front-shaped sample, cut in the middle only as much as it's off —
                // fitted, it sat between grey strips of the sample's placeholder.)
                if (scan != null) LookPic(scan, Modifier.fillMaxHeight().aspectRatio(coverTpl?.frontAspect ?: 0.7f))
                else drawnCase(Modifier.fillMaxSize())
            }
            Sample.CASE_DRAWN, Sample.MEDIA_INSIDE -> drawnCase(Modifier.fillMaxSize())
            Sample.CASE_SAME -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                LookPic(cover, Modifier.fillMaxHeight().aspectRatio(0.7f))
                LookPic(cover, Modifier.fillMaxHeight().aspectRatio(0.7f), alpha = 0.7f)
            }
            Sample.CASE_REAL -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                LookPic(cover, Modifier.fillMaxHeight().aspectRatio(0.7f))
                LookPic(cover, Modifier.fillMaxHeight(0.62f).aspectRatio(0.88f), alpha = 0.7f)
            }
            Sample.MEDIA_BESIDE -> Box(Modifier.fillMaxSize()) {
                drawnCase(Modifier.fillMaxHeight().fillMaxWidth(0.62f).align(Alignment.CenterStart))
                PhysicalMedia(platform?.let { MediaShell.of(it) } ?: MediaShell.DISC, cover, Modifier.fillMaxHeight(0.55f).aspectRatio(1f).align(Alignment.BottomEnd))
            }
            Sample.MEDIA_HALF, Sample.DISC_CASE -> Box(Modifier.fillMaxSize()) {
                PhysicalMedia(discShell, cover, Modifier.fillMaxHeight(0.8f).aspectRatio(1f).align(Alignment.CenterEnd))
                drawnCase(Modifier.fillMaxHeight().fillMaxWidth(0.62f).align(Alignment.CenterStart))
            }
            Sample.CART_REAL, Sample.PHOTO_OFF -> shell(if (s == Sample.PHOTO_OFF) cartNow() else LookCart.REAL, Modifier.fillMaxSize())
            Sample.CART_PACK -> shell(LookCart.PACK, Modifier.fillMaxSize())
            Sample.CART_CLEAR -> shell(LookCart.CLEAR, Modifier.fillMaxSize())
            Sample.DISC_OWN -> PhysicalMedia(discShell, cover, Modifier.fillMaxSize())
            Sample.DISC_CART -> PhysicalMedia(MediaShell.GB, cover, Modifier.fillMaxSize())
            Sample.DISC_ALL -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                PhysicalMedia(MediaShell.DISC, cover, Modifier.fillMaxHeight(0.85f).aspectRatio(1f))
                PhysicalMedia(MediaShell.DISC, cover, Modifier.fillMaxHeight(0.65f).aspectRatio(1f))
            }
            // Update 204: Mix — a cartridge and a disc side by side.
            Sample.MIX -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                PhysicalMedia(MediaShell.GB, cover, Modifier.fillMaxHeight(0.8f).aspectRatio(MediaShell.GB.aspect))
                PhysicalMedia(discShell, cover, Modifier.fillMaxHeight(0.7f).aspectRatio(1f))
            }
            Sample.PHOTO_ON -> {
                val photo = g.physical?.firstOrNull { it.first == "Disc / cartridge" }?.second
                if (photo != null) LookPic(photo, Modifier.fillMaxSize(), fit = true) else shell(cartNow(), Modifier.fillMaxSize())
            }
            Sample.HERO -> LookPic(g.hero ?: wideArt, Modifier.fillMaxWidth().aspectRatio(92f / 43f))
            Sample.SHOT -> LookPic(g.shot ?: g.hero ?: wideArt, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        }
    }
}

/** Update 202: holding the R1 Look tile — the choices of the main look in use, in a popup. */
@Composable
internal fun GameLookPopup(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        ImmersiveDialogWindow()
        val maxH = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.9f).dp
        Box(Modifier.heightIn(max = maxH)) {
            ShelfCard {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LookChooser(appearance, onChange, enabled = layoutOwnLook(appearance.homeLayout) == null)
                    Chip("Close", onClick = onDismiss)
                }
            }
        }
    }
}
