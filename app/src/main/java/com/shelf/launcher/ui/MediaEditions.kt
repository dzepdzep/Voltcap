package com.shelf.launcher.ui

import com.shelf.launcher.data.Features

/**
 * Update 210: the Master Physical Media Collection's release-aware editions — real cartridges / cards that came in their
 * own plastic or their own shell (the gold Ocarina of Time, the clear-cyan Pokémon Crystal, the infrared crimson DS
 * cards, Boktai's solar cartridge…), each with its source and how sure it is.
 *
 * Which piece a game is drawn on (PhysicalMedia.mediaFor / drawn3dPick), in order:
 *  1. your own choice (the game's menu › Cartridge shell…, for the game or for its console);
 *  2. a verified edition for exactly this game and region ([auto]) — only one with [EditionStatus.VERIFIED], allowed to
 *     be picked by itself (no other documented edition, e.g. a grey re-release), whose region the file name states;
 *  3. the console's standard shell.
 * Editions that need a choice (a grey and a coloured release both exist) or still need research are offered in the
 * Cartridge shell… picker, never picked by themselves. Matching goes by the ROM's file name (No-Intro style names:
 * "Pokemon - Crystal Version (USA, Europe) (Rev 1).gbc"), never by a guess from size or similar titles.
 * Only how the game is drawn changes — never its console, file, emulator, saves or achievements. Materials never replace
 * the game's label art. Records: docs/physical_media/MEDIA_EDITIONS.md (made from the same list).
 */
internal enum class EditionStatus(val label: String) {
    VERIFIED("Verified retail"), ALTERNATE("Verified alternate"), PENDING("Pending research")
}

internal class Edition(
    val id: String,
    val name: String,
    val platforms: Set<String>,
    /**
     * The file's title (normalised: [MediaEditions.titleOf]) is one of these; "…*" matches the start (a series),
     * "#tag" a tag in the file name ("(J-Cart)").
     */
    val titles: List<String>,
    /** The regions this edition was sold in; the file name's regions must all be among them. */
    val regions: Set<String>,
    /** The shell (CartShells id); null: the console's own. */
    val shell: String?,
    /** The plastic (ShellMaterials id); null: the shell's own colour. */
    val material: String?,
    val status: EditionStatus,
    /** Picked by itself when it matches (else only offered in the picker). */
    val auto: Boolean,
    val sources: List<String>,
    val note: String
) {
    /** The shell choice it stands for ("<shell>~m:<material>"), on [console]'s authentic shell when it names none. */
    fun raw(console: String, gameKey: String?): String? {
        val sh = shell ?: CartShells.authenticIdFor(console, CaseCatalog.regionOf(gameKey ?: ""), gameKey) ?: return null
        return sh + "~" + (material?.let { "m:$it" } ?: "")
    }
}

internal object MediaEditions {
    val all: List<Edition> = listOf(
        Edition("n64_007_world_is_not_enough", "Blue cartridge (North America)", setOf("N64"), listOf("007 world is not enough"), setOf("US"), null, "blue", EditionStatus.VERIFIED, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "A grey release is documented too (micro-64), so neither is picked by itself — choose in the game's Cartridge shell…"),
        Edition("n64_007_world_is_not_enough_grey", "Grey cartridge (North America)", setOf("N64"), listOf("007 world is not enough"), setOf("US"), null, "n64_grey", EditionStatus.ALTERNATE, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "The documented grey release."),
        Edition("n64_aidyn_chronicles_first_mage", "Black cartridge (North America)", setOf("N64"), listOf("aidyn chronicles first mage"), setOf("US"), null, "black", EditionStatus.VERIFIED, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "A grey release is documented too (micro-64), so neither is picked by itself — choose in the game's Cartridge shell…"),
        Edition("n64_aidyn_chronicles_first_mage_grey", "Grey cartridge (North America)", setOf("N64"), listOf("aidyn chronicles first mage"), setOf("US"), null, "n64_grey", EditionStatus.ALTERNATE, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "The documented grey release."),
        Edition("n64_all_star_baseball_2001", "Red cartridge (North America)", setOf("N64"), listOf("all star baseball 2001"), setOf("US"), null, "red", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_armorines_project_swarm", "Black cartridge (North America)", setOf("N64"), listOf("armorines project swarm"), setOf("US"), null, "black", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_army_men_air_combat", "Green cartridge (North America)", setOf("N64"), listOf("army men air combat"), setOf("US"), null, "green", EditionStatus.VERIFIED, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "A grey release is documented too (micro-64), so neither is picked by itself — choose in the game's Cartridge shell…"),
        Edition("n64_army_men_air_combat_grey", "Grey cartridge (North America)", setOf("N64"), listOf("army men air combat"), setOf("US"), null, "n64_grey", EditionStatus.ALTERNATE, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "The documented grey release."),
        Edition("n64_army_men_sarges_heroes_2", "Green cartridge (North America)", setOf("N64"), listOf("army men sarges heroes 2"), setOf("US"), null, "green", EditionStatus.VERIFIED, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "A grey release is documented too (micro-64), so neither is picked by itself — choose in the game's Cartridge shell…"),
        Edition("n64_army_men_sarges_heroes_2_grey", "Grey cartridge (North America)", setOf("N64"), listOf("army men sarges heroes 2"), setOf("US"), null, "n64_grey", EditionStatus.ALTERNATE, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "The documented grey release."),
        Edition("n64_bass_masters_2000", "Blue cartridge (North America)", setOf("N64"), listOf("bass masters 2000"), setOf("US"), null, "blue", EditionStatus.VERIFIED, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "A grey release is documented too (micro-64), so neither is picked by itself — choose in the game's Cartridge shell…"),
        Edition("n64_bass_masters_2000_grey", "Grey cartridge (North America)", setOf("N64"), listOf("bass masters 2000"), setOf("US"), null, "n64_grey", EditionStatus.ALTERNATE, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "The documented grey release."),
        Edition("n64_batman_beyond_return_of_joker", "Black cartridge (North America)", setOf("N64"), listOf("batman beyond return of joker"), setOf("US"), null, "black", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_battletanx_global_assault", "Green cartridge (North America)", setOf("N64"), listOf("battletanx global assault"), setOf("US"), null, "green", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_battlezone_rise_of_black_dogs", "Black cartridge (North America)", setOf("N64"), listOf("battlezone rise of black dogs"), setOf("US"), null, "black", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_donkey_kong_64", "Yellow cartridge (North America)", setOf("N64"), listOf("donkey kong 64"), setOf("US"), null, "yellow", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_earthworm_jim_3d", "Yellow cartridge (North America)", setOf("N64"), listOf("earthworm jim 3d"), setOf("US"), null, "yellow", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_ecw_hardcore_revolution", "Black cartridge (North America)", setOf("N64"), listOf("ecw hardcore revolution"), setOf("US"), null, "black", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_fighter_destiny_2", "Black cartridge (North America)", setOf("N64"), listOf("fighter destiny 2"), setOf("US"), null, "black", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_hydro_thunder", "Blue cartridge (North America)", setOf("N64"), listOf("hydro thunder"), setOf("US"), null, "blue", EditionStatus.VERIFIED, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "A grey release is documented too (micro-64), so neither is picked by itself — choose in the game's Cartridge shell…"),
        Edition("n64_hydro_thunder_grey", "Grey cartridge (North America)", setOf("N64"), listOf("hydro thunder"), setOf("US"), null, "n64_grey", EditionStatus.ALTERNATE, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "The documented grey release."),
        Edition("n64_jeremy_mcgrath_supercross_2000", "Red cartridge (North America)", setOf("N64"), listOf("jeremy mcgrath supercross 2000"), setOf("US"), null, "red", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_legend_of_zelda_majoras_mask", "Gold cartridge (North America)", setOf("N64"), listOf("legend of zelda majoras mask"), setOf("US"), null, "zelda_gold", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), "Label variants (incl. the lenticular collector's label) aren't drawn — the game's own label art is used."),
        Edition("n64_legend_of_zelda_ocarina_of_time", "Gold cartridge (North America)", setOf("N64"), listOf("legend of zelda ocarina of time"), setOf("US"), null, "zelda_gold", EditionStatus.VERIFIED, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "A grey release is documented too (micro-64), so neither is picked by itself — choose in the game's Cartridge shell…"),
        Edition("n64_legend_of_zelda_ocarina_of_time_grey", "Grey cartridge (North America)", setOf("N64"), listOf("legend of zelda ocarina of time"), setOf("US"), null, "n64_grey", EditionStatus.ALTERNATE, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "The documented grey release."),
        Edition("n64_madden_nfl_2001", "Blue cartridge (North America)", setOf("N64"), listOf("madden nfl 2001"), setOf("US"), null, "blue", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_madden_nfl_2002", "Black cartridge (North America)", setOf("N64"), listOf("madden nfl 2002"), setOf("US"), null, "black", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_nba_jam_2000", "Red cartridge (North America)", setOf("N64"), listOf("nba jam 2000"), setOf("US"), null, "red", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_nfl_quarterback_club_2001", "Red cartridge (North America)", setOf("N64"), listOf("nfl quarterback club 2001", "nfl qb club 2001"), setOf("US"), null, "red", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_nuclear_strike_64", "Black cartridge (North America)", setOf("N64"), listOf("nuclear strike 64"), setOf("US"), null, "black", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_pokemon_stadium_2", "Gold front / silver back cartridge (North America)", setOf("N64"), listOf("pokemon stadium 2"), setOf("US"), null, "stadium2", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_power_rangers_lightspeed_rescue", "Black cartridge (North America)", setOf("N64"), listOf("power rangers lightspeed rescue"), setOf("US"), null, "black", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_rally_challenge_2000", "Black cartridge (North America)", setOf("N64"), listOf("rally challenge 2000"), setOf("US"), null, "black", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_rayman_2_great_escape", "Green cartridge (North America)", setOf("N64"), listOf("rayman 2 great escape", "rayman 2"), setOf("US"), null, "green", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_road_rash_64", "Black cartridge (North America)", setOf("N64"), listOf("road rash 64", "road rash"), setOf("US"), null, "black", EditionStatus.VERIFIED, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "A grey release is documented too (micro-64), so neither is picked by itself — choose in the game's Cartridge shell…"),
        Edition("n64_road_rash_64_grey", "Grey cartridge (North America)", setOf("N64"), listOf("road rash 64", "road rash"), setOf("US"), null, "n64_grey", EditionStatus.ALTERNATE, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "The documented grey release."),
        Edition("n64_rocket_robot_on_wheels", "Red cartridge (North America)", setOf("N64"), listOf("rocket robot on wheels"), setOf("US"), null, "red", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_rugrats_in_paris_movie", "Black cartridge (North America)", setOf("N64"), listOf("rugrats in paris movie"), setOf("US"), null, "black", EditionStatus.VERIFIED, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "A grey release is documented too (micro-64), so neither is picked by itself — choose in the game's Cartridge shell…"),
        Edition("n64_rugrats_in_paris_movie_grey", "Grey cartridge (North America)", setOf("N64"), listOf("rugrats in paris movie"), setOf("US"), null, "n64_grey", EditionStatus.ALTERNATE, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "The documented grey release."),
        Edition("n64_scooby_doo_classic_creep_capers", "Black cartridge (North America)", setOf("N64"), listOf("scooby doo classic creep capers"), setOf("US"), null, "black", EditionStatus.VERIFIED, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "A grey release is documented too (micro-64), so neither is picked by itself — choose in the game's Cartridge shell…"),
        Edition("n64_scooby_doo_classic_creep_capers_grey", "Grey cartridge (North America)", setOf("N64"), listOf("scooby doo classic creep capers"), setOf("US"), null, "n64_grey", EditionStatus.ALTERNATE, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "The documented grey release."),
        Edition("n64_spider_man", "Red cartridge (North America)", setOf("N64"), listOf("spider man"), setOf("US"), null, "red", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_tom_clancys_rainbow_six", "Black cartridge (North America)", setOf("N64"), listOf("tom clancys rainbow six"), setOf("US"), null, "black", EditionStatus.VERIFIED, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "A grey release is documented too (micro-64), so neither is picked by itself — choose in the game's Cartridge shell…"),
        Edition("n64_tom_clancys_rainbow_six_grey", "Grey cartridge (North America)", setOf("N64"), listOf("tom clancys rainbow six"), setOf("US"), null, "n64_grey", EditionStatus.ALTERNATE, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "The documented grey release."),
        Edition("n64_tony_hawks_pro_skater", "Blue cartridge (North America)", setOf("N64"), listOf("tony hawks pro skater"), setOf("US"), null, "blue", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_tony_hawks_pro_skater_2", "Yellow cartridge (North America)", setOf("N64"), listOf("tony hawks pro skater 2"), setOf("US"), null, "yellow", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_tony_hawks_pro_skater_3", "Black cartridge (North America)", setOf("N64"), listOf("tony hawks pro skater 3"), setOf("US"), null, "black", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_turok_2_seeds_of_evil", "Black cartridge (North America)", setOf("N64"), listOf("turok 2 seeds of evil"), setOf("US"), null, "black", EditionStatus.VERIFIED, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "A grey release is documented too (micro-64), so neither is picked by itself — choose in the game's Cartridge shell…"),
        Edition("n64_turok_2_seeds_of_evil_grey", "Grey cartridge (North America)", setOf("N64"), listOf("turok 2 seeds of evil"), setOf("US"), null, "n64_grey", EditionStatus.ALTERNATE, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "The documented grey release."),
        Edition("n64_turok_3_shadow_of_oblivion", "Black cartridge (North America)", setOf("N64"), listOf("turok 3 shadow of oblivion"), setOf("US"), null, "black", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_turok_rage_wars", "Black cartridge (North America)", setOf("N64"), listOf("turok rage wars"), setOf("US"), null, "black", EditionStatus.VERIFIED, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "A grey release is documented too (micro-64), so neither is picked by itself — choose in the game's Cartridge shell…"),
        Edition("n64_turok_rage_wars_grey", "Grey cartridge (North America)", setOf("N64"), listOf("turok rage wars"), setOf("US"), null, "n64_grey", EditionStatus.ALTERNATE, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "The documented grey release."),
        Edition("n64_wcw_backstage_assault", "Blue cartridge (North America)", setOf("N64"), listOf("wcw backstage assault"), setOf("US"), null, "blue", EditionStatus.VERIFIED, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "Grey variant listed by micro-64 but disputed as a US release (its region markings) — choose in Cartridge shell…"),
        Edition("n64_wcw_backstage_assault_grey", "Grey cartridge (North America)", setOf("N64"), listOf("wcw backstage assault"), setOf("US"), null, "n64_grey", EditionStatus.PENDING, false, listOf("https://micro-64.com/database/colouredcarts.shtml"), "The documented grey release."),
        Edition("n64_wwf_no_mercy", "Black cartridge (North America)", setOf("N64"), listOf("wwf no mercy"), setOf("US"), null, "black", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_wwf_wrestlemania_2000", "Black cartridge (North America)", setOf("N64"), listOf("wwf wrestlemania 2000"), setOf("US"), null, "black", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), ""),
        Edition("n64_majora_pal", "Gold cartridge (PAL)", setOf("N64"), listOf("legend of zelda majoras mask"), setOf("EU"), null, "zelda_gold", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), "micro-64 lists Majora's Mask among the PAL coloured releases."),
        Edition("n64_stadium2_pal", "Gold front / silver back (PAL)", setOf("N64"), listOf("pokemon stadium 2"), setOf("EU"), null, "stadium2", EditionStatus.VERIFIED, true, listOf("https://micro-64.com/database/colouredcarts.shtml"), "PAL coloured release (micro-64). The Japanese counterparts use standard grey shells."),
        Edition("n64_turok2_pal", "Black cartridge (PAL)", setOf("N64"), listOf("turok 2 seeds of evil"), setOf("EU"), null, "black", EditionStatus.PENDING, false, listOf("V3 spec (not re-checked)", "https://micro-64.com/database/colouredcarts.shtml"), "Listed as a PAL coloured release in the V3 spec (citing micro-64); grey PAL copies not ruled out."),
        Edition("n64_dk64_au", "Yellow cartridge (Australia)", setOf("N64"), listOf("donkey kong 64"), setOf("AU"), null, "yellow", EditionStatus.PENDING, false, listOf("V3 spec (not re-checked)"), "Australian example from the V3 spec."),
        Edition("n64_oot_au", "Gold cartridge (Australia)", setOf("N64"), listOf("legend of zelda ocarina of time"), setOf("AU"), null, "zelda_gold", EditionStatus.PENDING, false, listOf("V3 spec (not re-checked)"), "Australian example from the V3 spec."),
        Edition("gb_pkmn_red", "Red cartridge", setOf("GB"), listOf("pokemon red version", "pokemon red"), setOf("US", "EU", "AU"), null, "pkmn_red", EditionStatus.VERIFIED, true, listOf("https://gameverifying.com/wiki/cart-based-systems/gameboy/casing"), ""),
        Edition("gb_pkmn_blue", "Blue cartridge", setOf("GB"), listOf("pokemon blue version", "pokemon blue"), setOf("US", "EU", "AU"), null, "pkmn_blue", EditionStatus.VERIFIED, true, listOf("https://gameverifying.com/wiki/cart-based-systems/gameboy/casing"), ""),
        Edition("gb_pkmn_yellow", "Yellow cartridge", setOf("GB", "GBC"), listOf("pokemon yellow version special pikachu edition", "pokemon yellow version", "pokemon yellow"), setOf("US", "EU", "AU"), "gb", "pkmn_yellow", EditionStatus.VERIFIED, true, listOf("https://gameverifying.com/wiki/cart-based-systems/gameboy/casing"), "A Game Boy (DMG-shaped) cartridge in yellow."),
        Edition("gb_pkmn_gold", "Gold cartridge", setOf("GB", "GBC"), listOf("pokemon gold version", "pokemon gold"), setOf("US", "EU", "AU"), "gbc_dual", "pkmn_gold", EditionStatus.VERIFIED, true, listOf("https://gameverifying.com/wiki/cart-based-systems/gameboy/casing"), "Dual-mode (black-style) Game Pak shape in sparkling gold."),
        Edition("gb_pkmn_silver", "Silver cartridge", setOf("GB", "GBC"), listOf("pokemon silver version", "pokemon silver"), setOf("US", "EU", "AU"), "gbc_dual", "pkmn_silver", EditionStatus.VERIFIED, true, listOf("https://gameverifying.com/wiki/cart-based-systems/gameboy/casing", "V3 spec (not re-checked)"), "Silver isn't in the part of Game Verifying's casing list that loaded; the western silver cartridge is well known. Colour approximate."),
        Edition("gb_pkmn_gold_jp", "Two-tone cartridge (Japan)", setOf("GB", "GBC"), listOf("pocket monsters kin", "pocket monsters gold"), setOf("JP"), "gbc_dual", "pkmn_gold_jp", EditionStatus.VERIFIED, true, listOf("https://gameverifying.com/wiki/cart-based-systems/gameboy/dmg"), "Update 300 — Game Verifying: \"The front half of Gold should be dark blue and the back half should be a dark grey.\" Colours approximate."),
        Edition("gb_pkmn_silver_jp", "Two-tone cartridge (Japan)", setOf("GB", "GBC"), listOf("pocket monsters gin", "pocket monsters silver"), setOf("JP"), "gbc_dual", "pkmn_silver_jp", EditionStatus.VERIFIED, true, listOf("https://gameverifying.com/wiki/cart-based-systems/gameboy/dmg"), "Update 300 — Game Verifying: \"The Japanese Silver is the exact opposite\" of Gold (dark grey front, dark blue back). Colours approximate."),
        Edition("gb_dkland", "Yellow cartridge", setOf("GB"), listOf("donkey kong land"), setOf("US", "EU", "AU"), null, "yellow", EditionStatus.VERIFIED, true, listOf("https://gameverifying.com/wiki/cart-based-systems/gameboy/casing"), "Game Verifying lists Donkey Kong Land in yellow (DMG-09)."),
        Edition("gb_dkland23", "Yellow cartridge", setOf("GB"), listOf("donkey kong land 2", "donkey kong land iii", "donkey kong land 3"), setOf("US", "EU", "AU"), null, "yellow", EditionStatus.PENDING, false, listOf("V3 spec (not re-checked)"), "\"Donkey Kong Land series — yellow\" (V3 spec); the sequels not confirmed separately."),
        Edition("gb_tamagotchi", "Pink cartridge", setOf("GB"), listOf("tamagotchi"), setOf("US", "EU", "AU"), null, "pink", EditionStatus.VERIFIED, false, listOf("https://gameverifying.com/wiki/cart-based-systems/gameboy/casing"), "Game Verifying lists Tamagotchi in pink; other Tamagotchi releases differ, so it isn't picked by itself."),
        Edition("gbc_crystal", "Clear cyan, glitter", setOf("GBC"), listOf("pokemon crystal version", "pokemon crystal", "pocket monsters crystal version", "pocket monsters crystal"), setOf("US", "EU", "AU", "JP"), "gbc", "pkmn_crystal", EditionStatus.VERIFIED, true, listOf("https://gameverifying.com/wiki/cart-based-systems/gameboy/color"), "\"Clear Cyan for Pokemon Crystal\". The Japanese crystal board art isn't drawn."),
        Edition("gbc_kirby_tilt", "Kirby Tilt 'n' Tumble shell, clear pink", setOf("GBC"), listOf("kirby tilt n tumble", "koro koro kirby"), setOf("US", "EU", "AU", "JP"), "kirby_tilt", "kirby_pink", EditionStatus.VERIFIED, true, listOf("https://gameverifying.com/wiki/cart-based-systems/gameboy/color"), "Clear pink (Game Verifying); the shell's shape is estimated."),
        Edition("gbc_command_master", "Clear teal", setOf("GBC"), listOf("command master"), setOf("JP"), "gbc", "command_teal", EditionStatus.VERIFIED, true, listOf("https://gameverifying.com/wiki/cart-based-systems/gameboy/color"), ""),
        Edition("gbc_pinball", "Pokémon Pinball rumble shell", setOf("GBC"), listOf("pokemon pinball"), setOf("US", "EU", "AU", "JP"), "pokemon_pinball", null, EditionStatus.VERIFIED, true, listOf("https://bulbapedia.bulbagarden.net/wiki/Game_Pak"), "Special rumble cartridge (Bulbapedia). Colours by region (western black, Japanese yellow / black) not yet confirmed — drawn in the shell's own colour."),
        Edition("gbc_mobile", "Clear blue (Mobile Series)", setOf("GBC"), listOf("mobile golf", "mobile trainer", "net de get minigame 100"), setOf("JP"), "gbc", "mobile_blue", EditionStatus.PENDING, false, listOf("https://gameverifying.com/wiki/cart-based-systems/gameboy/color"), "Game Verifying: \"Clear Blue for the Mobile Series\" — which titles belong to it not confirmed."),
        Edition("gba_ruby", "Transparent red", setOf("GBA"), listOf("pokemon ruby version", "pokemon ruby", "pocket monsters ruby"), setOf("US", "EU", "AU", "JP"), "gba", "gba_ruby", EditionStatus.VERIFIED, true, listOf("https://www.gameverifying.com/wiki/depository/gba"), ""),
        Edition("gba_sapphire", "Transparent blue", setOf("GBA"), listOf("pokemon sapphire version", "pokemon sapphire", "pocket monsters sapphire"), setOf("US", "EU", "AU", "JP"), "gba", "gba_sapphire", EditionStatus.VERIFIED, true, listOf("https://www.gameverifying.com/wiki/depository/gba"), ""),
        Edition("gba_emerald", "Transparent green", setOf("GBA"), listOf("pokemon emerald version", "pokemon emerald", "pocket monsters emerald"), setOf("US", "EU", "AU", "JP"), "gba", "gba_emerald", EditionStatus.VERIFIED, true, listOf("https://www.gameverifying.com/wiki/depository/gba"), ""),
        Edition("gba_firered", "Red-orange, less see-through", setOf("GBA"), listOf("pokemon firered version", "pokemon firered", "pocket monsters firered"), setOf("US", "EU", "AU", "JP"), "gba", "gba_firered", EditionStatus.VERIFIED, true, listOf("https://www.gameverifying.com/wiki/depository/gba"), "Sources disagree on the shade (orange / translucent red): the colour is a provisional compromise."),
        Edition("gba_leafgreen", "Green, less see-through", setOf("GBA"), listOf("pokemon leafgreen version", "pokemon leafgreen", "pocket monsters leafgreen"), setOf("US", "EU", "AU", "JP"), "gba", "gba_leafgreen", EditionStatus.VERIFIED, true, listOf("https://www.gameverifying.com/wiki/depository/gba"), ""),
        Edition("gba_boktai", "Boktai solar shell, clear", setOf("GBA"), listOf("boktai*", "bokura no taiyou*", "zoku bokura no taiyou*", "shin bokura no taiyou*"), setOf("US", "EU", "AU", "JP"), "boktai", "clear", EditionStatus.VERIFIED, true, listOf("https://www.gameverifying.com/wiki/depository/gba"), "AGB-013 clear casing with the solar sensor; shape estimated."),
        Edition("gba_drill_dozer", "Drill Dozer rumble shell, brown", setOf("GBA"), listOf("drill dozer", "screw breaker*"), setOf("US", "EU", "AU", "JP"), "drill_dozer", "brown", EditionStatus.VERIFIED, true, listOf("https://www.gameverifying.com/wiki/depository/gba"), "AGB-021A (AGB-021 in Japan), brown; shape estimated."),
        Edition("gba_twisted", "WarioWare: Twisted! shell, transparent grey", setOf("GBA"), listOf("warioware twisted"), setOf("US", "EU", "AU"), "warioware_twisted", "twisted_grey", EditionStatus.VERIFIED, true, listOf("https://www.gameverifying.com/wiki/depository/gba"), "AGB-019A; shape estimated."),
        Edition("gba_mawaru", "Mawaru Made in Wario shell, translucent white", setOf("GBA"), listOf("mawaru made in wario"), setOf("JP"), "warioware_twisted", "mawaru_white", EditionStatus.VERIFIED, true, listOf("https://www.gameverifying.com/wiki/depository/gba"), "AGB-019; shape estimated."),
        Edition("gba_yoshi_topsy", "Yoshi Topsy-Turvy tilt shell, green and white", setOf("GBA"), listOf("yoshi topsy turvy", "yoshis universal gravitation", "yoshi no banyuu inryoku"), setOf("US", "JP"), "yoshi_topsy", "yoshi_green_white", EditionStatus.VERIFIED, true, listOf("https://www.gameverifying.com/wiki/depository/gba"), "AGB-013; PAL copies have a different front casing (not drawn — Update 300: so Europe and Australia no longer get this one by themselves). Which half is green not confirmed."),
        Edition("gba_classic_nes", "Classic NES Series medium grey", setOf("GBA"), listOf("classic nes series*"), setOf("US", "EU", "AU"), "gba", "classic_nes_grey", EditionStatus.VERIFIED, true, listOf("https://www.gameverifying.com/wiki/depository/gba"), ""),
        Edition("gba_video", "GBA Video light grey", setOf("GBA"), listOf("game boy advance video*", "gba video*"), setOf("US", "EU", "AU"), "gba", "gba_video_grey", EditionStatus.VERIFIED, true, listOf("https://www.gameverifying.com/wiki/depository/gba"), ""),
        Edition("gba_bitgen", "bit Generations black", setOf("GBA"), listOf("bit generations*"), setOf("JP"), "gba", "black", EditionStatus.VERIFIED, true, listOf("https://www.gameverifying.com/wiki/depository/gba"), ""),
        Edition("gba_exe4_blue", "Blue cartridge (Japan)", setOf("GBA"), listOf("rockman exe 4 tournament blue moon", "rockman exe 4 blue moon"), setOf("JP"), "gba", "blue", EditionStatus.VERIFIED, false, listOf("https://www.gameverifying.com/wiki/depository/gba"), "Also released in a standard grey cartridge — choose in Cartridge shell…"),
        Edition("gba_exe4_red", "Red cartridge (Japan)", setOf("GBA"), listOf("rockman exe 4 tournament red sun", "rockman exe 4 red sun"), setOf("JP"), "gba", "red", EditionStatus.VERIFIED, false, listOf("https://www.gameverifying.com/wiki/depository/gba"), "Also released in a standard grey cartridge — choose in Cartridge shell…"),
        Edition("gba_exe5_colonel", "Teal cartridge (Japan)", setOf("GBA"), listOf("rockman exe 5 team of colonel"), setOf("JP"), "gba", "teal", EditionStatus.VERIFIED, true, listOf("https://www.gameverifying.com/wiki/depository/gba"), ""),
        Edition("ds_ntr031", "Infrared crimson card (NTR-031)", setOf("NDS"), listOf("pokemon heartgold version", "pokemon soulsilver version", "pokemon black version", "pokemon white version", "pokemon black version 2", "pokemon white version 2", "pokemon heartgold", "pokemon soulsilver", "pokemon black", "pokemon white", "pokemon black 2", "pokemon white 2", "pocket monsters heartgold", "pocket monsters soulsilver", "pocket monsters black", "pocket monsters white", "pocket monsters black 2", "pocket monsters white 2", "personal trainer walking", "walk with me*", "active health with carol vorderman"), setOf("US", "EU", "AU", "JP", "KOREA"), "ds", "ntr031", EditionStatus.VERIFIED, true, listOf("https://www.gameverifying.com/wiki/cart-based-systems/nds031", "https://bulbapedia.bulbagarden.net/wiki/Game_Pak"), "Eight known NTR-031 games (Game Verifying). The crimson-light trick isn't proof of authenticity."),
        Edition("nes_zelda_gold", "Gold cartridge", setOf("NES"), listOf("legend of zelda"), setOf("US"), "nes", "zelda_gold", EditionStatus.VERIFIED, false, listOf("https://www.thelegendofnes.com/game-specific-variants"), "Original release (NES-ZL-USA). A grey Classic Series re-issue exists — choose in Cartridge shell…"),
        Edition("nes_zelda_grey", "Grey cartridge (Classic Series)", setOf("NES"), listOf("legend of zelda"), setOf("US"), "nes", "nes_grey", EditionStatus.ALTERNATE, false, listOf("https://www.thelegendofnes.com/game-specific-variants"), "NES-ZL-USA-1."),
        Edition("nes_zelda2_gold", "Gold cartridge", setOf("NES"), listOf("zelda ii adventure of link"), setOf("US"), "nes", "zelda_gold", EditionStatus.VERIFIED, false, listOf("https://www.thelegendofnes.com/game-specific-variants"), "Original release (NES-AL-USA). A grey Classic Series re-issue exists — choose in Cartridge shell…"),
        Edition("nes_zelda2_grey", "Grey cartridge (Classic Series)", setOf("NES"), listOf("zelda ii adventure of link"), setOf("US"), "nes", "nes_grey", EditionStatus.ALTERNATE, false, listOf("https://www.thelegendofnes.com/game-specific-variants"), "NES-AL-USA-1."),
        Edition("snes_doom", "Red cartridge (North America)", setOf("SNES"), listOf("doom"), setOf("US"), "snes_na", "red", EditionStatus.VERIFIED, true, listOf("https://www.svg.com/1072450/only-these-three-snes-games-got-the-color-cartridge-treatment/"), "European / Japanese DOOM stays grey."),
        Edition("snes_ki", "Black cartridge (North America)", setOf("SNES"), listOf("killer instinct"), setOf("US"), "snes_na", "black", EditionStatus.VERIFIED, true, listOf("https://www.svg.com/1072450/only-these-three-snes-games-got-the-color-cartridge-treatment/"), ""),
        Edition("snes_carnage", "Red cartridge (North America)", setOf("SNES"), listOf("spider man and venom maximum carnage"), setOf("US"), "snes_na", "red", EditionStatus.VERIFIED, false, listOf("https://www.svg.com/1072450/only-these-three-snes-games-got-the-color-cartridge-treatment/"), "Later runs were grey — choose in Cartridge shell…"),
        Edition("snes_carnage_grey", "Grey cartridge (later run)", setOf("SNES"), listOf("spider man and venom maximum carnage"), setOf("US"), "snes_na", "snes_grey", EditionStatus.ALTERNATE, false, listOf("https://www.svg.com/1072450/only-these-three-snes-games-got-the-color-cartridge-treatment/"), ""),
        Edition("gen_carnage", "Red cartridge", setOf("Genesis", "MegaDrive"), listOf("spider man and venom maximum carnage"), setOf("US", "EU"), "genesis", "red", EditionStatus.VERIFIED, false, listOf("https://www.svg.com/1072450/only-these-three-snes-games-got-the-color-cartridge-treatment/"), "\"The only red variant cartridge on the Sega Genesis\" (SVG); standard black copies too — choose in Cartridge shell…"),
        Edition("gen_sk", "Sonic & Knuckles lock-on cartridge", setOf("Genesis", "MegaDrive"), listOf("sonic and knuckles*"), setOf("US", "EU", "AU", "JP"), "sonic_knuckles", null, EditionStatus.VERIFIED, true, listOf("https://commons.wikimedia.org/wiki/File:SegaGenesis-Sonic%2BKnuckles-lock-on-open.jpg"), "Its own lock-on shell with the hinged flap (proportions partly estimated)."),
        Edition("gen_jcart", "Codemasters J-Cart (two controller ports)", setOf("Genesis", "MegaDrive"), listOf("#j-cart"), setOf("US", "EU", "AU", "JP"), "jcart", null, EditionStatus.VERIFIED, true, listOf("https://de.wikipedia.org/wiki/J-Cart"), "Picked only for files tagged \"(J-Cart)\" (a dump of the J-Cart release)."),
        Edition("gen_jcart_titles", "Codemasters J-Cart", setOf("Genesis", "MegaDrive"), listOf("pete sampras tennis", "micro machines 2 turbo tournament", "micro machines turbo tournament 96", "micro machines military", "super skidmarks"), setOf("US", "EU", "AU", "JP"), "jcart", null, EditionStatus.PENDING, false, listOf("https://de.wikipedia.org/wiki/J-Cart"), "These game families had J-Cart releases; not every release was one, so it's offered, not picked.")
    )

    // (Update 325, Thor recording switching categories: 0.27 s compiling these for each game coming in — compiled once.)
    private val TAGS = Regex("""\s*[(\[][^)\]]*[)\]]""")
    private val MARKS = Regex("\\p{M}+")
    private val PUNCT = Regex("[.'’!]")
    private val NON_WORD = Regex("[^a-z0-9]+")
    private val PARENS = Regex("""\(([^)]*)\)""")

    /** "Legend of Zelda, The - Ocarina of Time (USA) (Rev 1).z64" → "legend of zelda ocarina of time". */
    fun titleOf(fileName: String): String {
        val noTags = fileName.replace(TAGS, " ")
        val ascii = java.text.Normalizer.normalize(noTags, java.text.Normalizer.Form.NFD).replace(MARKS, "")
        return ascii.lowercase().replace("&", " and ").replace(PUNCT, "")
            .replace(NON_WORD, " ").split(' ').filter { it.isNotEmpty() && it != "the" }.joinToString(" ")
    }

    /** The regions the file name states ("(USA, Europe)" → US, EU; "(World)" → all). Empty: none stated. */
    fun regionsOf(fileName: String): Set<String> {
        val out = HashSet<String>()
        PARENS.findAll(fileName).forEach { m ->
            m.groupValues[1].split(',').map { it.trim().lowercase() }.forEach { w ->
                when (w) {
                    "usa", "us", "america", "canada" -> out += "US"
                    "europe", "eur", "uk", "germany", "france", "spain", "italy", "netherlands", "sweden", "scandinavia", "denmark", "norway", "finland" -> out += "EU"
                    "australia" -> out += "AU"
                    "japan", "jpn" -> out += "JP"
                    "world" -> { out += "US"; out += "EU"; out += "AU"; out += "JP" }
                    "korea", "asia", "china", "brazil" -> out += w.uppercase()
                }
            }
        }
        return out
    }

    /** The file name of a game key (a path, or a content address with its slashes escaped). */
    private val INNER = setOf("gb", "gbc", "sgb", "gba", "nds", "dsi", "3ds", "nes", "fds", "sfc", "smc", "n64", "z64", "v64",
        "md", "gen", "smd", "sms", "gg", "32x", "pce", "ws", "wsc", "ngp", "ngc", "a26", "a52", "a78", "lnx", "j64", "col", "int", "vec", "min", "vb")

    fun fileNameOf(gameKey: String): String {
        val name = java.net.URLDecoder.decode(gameKey.replace("+", "%2B"), "UTF-8").substringAfterLast('/').substringAfterLast(':')
        // (Update 300: an archive named after its ROM — "… [patched].gbc.zip" — loses both endings; the ROM's ".gbc" was
        // read as part of the title and no edition matched.)
        val ext = name.substringAfterLast('.', "").lowercase()
        var base = name.substringBeforeLast('.')
        if ((ext == "zip" || ext == "7z") && base.substringAfterLast('.', "").lowercase() in INNER) base = base.substringBeforeLast('.')
        return base.trim()
    }

    private fun matches(e: Edition, fileName: String, title: String): Boolean = e.titles.any { t ->
        when {
            t.startsWith("#") -> fileName.contains("(" + t.removePrefix("#") + ")", ignoreCase = true)
            t.endsWith("*") -> title.startsWith(t.removeSuffix("*").trim())
            else -> title == t
        }
    }

    private val cache = java.util.concurrent.ConcurrentHashMap<String, List<Edition>>()

    /** Every edition for this game (auto or offered), for the picker and the report. */
    fun forGame(platform: String, gameKey: String?): List<Edition> {
        if (gameKey.isNullOrEmpty()) return emptyList()
        return cache.getOrPut("$platform|$gameKey") {
            val file = runCatching { fileNameOf(gameKey) }.getOrDefault(gameKey)
            val title = titleOf(file)
            if (title.isEmpty()) emptyList() else all.filter { platform in it.platforms && matches(it, file, title) }
        }
    }

    private val NONE = Edition("", "", emptySet(), emptyList(), emptySet(), null, null, EditionStatus.PENDING, false, emptyList(), "")

    /**
     * Update 300: the white DSi-exclusive card (NTR-005 white) — chosen by the game's own DS header (unit code 03h =
     * DSi only, RomHeaders), not by its title. Game Verifying: "This variant of the NTR-005 is used for DSi Exclusive
     * games"; Wikipedia (Nintendo Game Card): "These titles have game cards with white-colored casings."
     */
    val DSI_WHITE = Edition("ds_dsi_white", "White card (DSi exclusive)", setOf("NDS"), emptyList(), emptySet(), "ds", "dsi_white",
        EditionStatus.VERIFIED, true, listOf("https://gameverifying.com/wiki/cart-based-systems/nds", "https://en.wikipedia.org/wiki/Nintendo_Game_Card"),
        "Picked from the game's DS header (DSi only); colour approximate.")
    private val autoCache = java.util.concurrent.ConcurrentHashMap<String, Edition>()

    /** The edition picked by itself for this game, or null. (Worked out once per game: mediaFor asks on every draw.) */
    fun auto(platform: String, gameKey: String?): Edition? {
        if (!Features.realEditions.state.value || gameKey.isNullOrEmpty()) return null
        autoCache["$platform|$gameKey"]?.let { return it.takeIf { e -> e !== NONE } }
        val found = autoNow(platform, gameKey)
        // (Update 300: not kept while the ROM's header is still being read — the answer may change when it arrives.)
        if (found != null || !(com.shelf.launcher.data.RomHeaders.reads(platform) && com.shelf.launcher.data.RomHeaders.pending(gameKey)))
            autoCache["$platform|$gameKey"] = found ?: NONE
        return found
    }

    private fun autoNow(platform: String, gameKey: String): Edition? {
        val list = forGame(platform, gameKey).filter { it.auto && it.status == EditionStatus.VERIFIED }
        if (list.isEmpty()) {
            // (Update 300: no title edition — a DSi-only DS game is on the white card.)
            if (platform == "NDS" && com.shelf.launcher.data.RomHeaders.nds(gameKey, platform) == com.shelf.launcher.data.RomHeaders.Nds.DSI_ONLY) return DSI_WHITE
            return null
        }
        val regions = regionsOf(runCatching { fileNameOf(gameKey) }.getOrDefault(gameKey))
        // (The file has to say where it's from, and every place it names must have had this edition.)
        val fit = list.filter { e -> regions.isNotEmpty() && e.regions.containsAll(regions) }
        // (One answer only: two that fit would be a guess.)
        return fit.singleOrNull()
    }

    /** [auto]'s shell choice, or null. */
    fun autoRaw(platform: String, gameKey: String?): String? = auto(platform, gameKey)?.raw(platform, gameKey)
}
