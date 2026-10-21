package com.shelf.launcher.ui

/**
 * Update 210: the plastics a drawn cartridge / card can be made of — the Master Physical Media Collection's material
 * registry. Every colour here is an approximate sRGB rendering preset (from the V3 spec, to be tuned against real
 * photographs), not a manufacturer's value. A material is drawn by CartShells: its base colour (lit from above), its
 * [highlight] as a sheen when it's metallic-looking, its [glitter] as fine sparkles, how much light it lets through
 * ([transmission]: the board inside shows), and a different back ([back]) for two-tone shells.
 *
 * A shell's choice names a material as "m:<id>" (CartShells.colourOf). Materials never touch a game's label art.
 */
internal enum class MaterialKind(val label: String) {
    OPAQUE("Opaque"), METALLIC("Metallic"), TRANSLUCENT("Translucent"), EDITION("Real editions")
}

internal class ShellMaterial(
    val id: String,
    val name: String,
    val kind: MaterialKind,
    /** Base colour (sRGB, approximate). */
    val base: Int,
    /** Highlight colour for metallic / pearlescent sheen and glitter (0: none). */
    val highlight: Int = 0,
    /** 0 = glossy … 1 = matte. */
    val roughness: Float = 0.45f,
    /** How strongly the metallic-pigment sheen shows (0…1). */
    val metallic: Float = 0f,
    /** How much light passes through (0 = opaque … 1 = clear glass). The PCB, contacts and label stay opaque. */
    val transmission: Float = 0f,
    /** Fine glitter / speckle amount (0…1). */
    val glitter: Float = 0f,
    /** A different back half (two-tone shells), 0: the same as the front. */
    val back: Int = 0,
    /** Edge tint for clear plastic (0: from the base). */
    val edge: Int = 0,
    /** How sure the colour is and what it was checked against. */
    val note: String = "Approximate sRGB preset (V3 spec); not a measured or official colour."
) {
    val opacity: Float get() = (1f - transmission).coerceIn(0.2f, 1f)

    /** As a shell colour (what the drawing uses). */
    fun colour(): ShellColour = ShellColour(
        "m:$id", name, base, opacity, highlight = highlight, metallic = metallic, glitter = glitter, backArgb = back,
        matte = roughness >= 0.7f
    )
}

internal object ShellMaterials {
    private fun hex(v: Long): Int = (0xFF000000L or v).toInt()

    private fun op(id: String, name: String, base: Long) = ShellMaterial(id, name, MaterialKind.OPAQUE, hex(base))
    private fun met(id: String, name: String, base: Long, hi: Long, glitter: Float = 0.15f) =
        ShellMaterial(id, name, MaterialKind.METALLIC, hex(base), hex(hi), roughness = 0.35f, metallic = 0.8f, glitter = glitter)
    private fun clr(id: String, name: String, tint: Long, t: Float = 0.45f) =
        ShellMaterial(id, name, MaterialKind.TRANSLUCENT, hex(tint), roughness = 0.3f, transmission = t)

    val all: List<ShellMaterial> = listOf(
        // ── Standard opaque plastics ──
        op("nes_grey", "NES light grey", 0xB8B8B0),
        op("snes_grey", "SNES grey", 0xB5B3AC),
        op("n64_grey", "N64 medium grey", 0x777779),
        op("gb_grey", "Game Boy grey", 0xB4B4AC),
        op("gba_dark", "GBA dark grey", 0x54565A),
        op("ds_dark", "DS dark grey", 0x45464B),
        ShellMaterial("3ds_offwhite", "3DS pale grey", MaterialKind.OPAQUE, hex(0xCFD0CC), hex(0xE5E6E3), roughness = 0.75f,
            note = "Approximate: Game Verifying calls the CTR-005 card white; photographed plastic reads pale grey / off-white."),
        op("switch_dark", "Switch dark grey", 0x45464B),
        op("black", "Black", 0x202125),
        op("white", "White", 0xECECE9),
        op("cream", "Cream", 0xD7CBB2),
        op("red", "Red", 0xC7363D),
        op("orange", "Orange", 0xEE7D31),
        op("yellow", "Yellow", 0xE7C42F),
        op("green", "Green", 0x308450),
        op("dark_green", "Dark green", 0x225B42),
        op("blue", "Blue", 0x3267AA),
        op("dark_blue", "Dark blue", 0x293F73),
        op("purple", "Purple", 0x7854A5),
        op("pink", "Pink", 0xDA88AA),
        op("brown", "Brown", 0x795440),
        op("teal", "Teal", 0x218C87),
        // ── Metallic-looking plastic (a pigment finish, not metal) ──
        met("zelda_gold", "Zelda gold", 0xB99D56, 0xE6D18B),
        met("bright_gold", "Bright gold", 0xC5A354, 0xF0D58D),
        met("silver", "Silver", 0xA9AEB6, 0xE8EBEF),
        met("bronze", "Bronze", 0x936C45, 0xC4A077),
        met("copper", "Copper", 0xAE7350, 0xE0B18A),
        met("gunmetal", "Gunmetal", 0x555D67, 0x9CA5AF),
        ShellMaterial("pearl_white", "Pearlescent white", MaterialKind.METALLIC, hex(0xE6E4DF), hex(0xFFFFFF), roughness = 0.3f, metallic = 0.5f),
        // ── Transparent / translucent plastic ──
        clr("clear", "Crystal clear", 0xEBF5FA, 0.6f),
        clr("smoke", "Smoke", 0x565A64, 0.4f),
        clr("ruby", "Ruby red", 0xBE3547),
        clr("sapphire", "Sapphire blue", 0x326AB5),
        clr("emerald", "Emerald green", 0x26995D),
        clr("cyan_crystal", "Cyan crystal", 0x86C7E4),
        clr("atomic_purple", "Atomic purple", 0x8564B9),
        clr("amber", "Amber", 0xE6A04A),
        clr("clear_orange", "Clear orange", 0xE88C4E),
        clr("clear_yellow", "Clear yellow", 0xEECF62),
        clr("clear_pink", "Clear pink", 0xE99ECA),
        ShellMaterial("frosted_white", "Frosted white", MaterialKind.TRANSLUCENT, hex(0xE0E9EB), roughness = 0.85f, transmission = 0.35f),
        // ── Real editions' plastics (approximate presets for the verified releases in MediaEditions) ──
        ShellMaterial("pkmn_red", "Pokémon Red", MaterialKind.EDITION, hex(0xC34D54)),
        ShellMaterial("pkmn_blue", "Pokémon Blue", MaterialKind.EDITION, hex(0x386AAF)),
        ShellMaterial("pkmn_yellow", "Pokémon Yellow", MaterialKind.EDITION, hex(0xE1C33D)),
        ShellMaterial("pkmn_gold", "Pokémon Gold", MaterialKind.EDITION, hex(0xB99D56), hex(0xE6D18B), roughness = 0.35f, metallic = 0.7f, glitter = 0.55f,
            note = "Approximate; the real plastic has a fine sparkling pigment."),
        ShellMaterial("pkmn_silver", "Pokémon Silver", MaterialKind.EDITION, hex(0xAEB4BB), hex(0xE8EBEF), roughness = 0.35f, metallic = 0.7f, glitter = 0.55f,
            note = "Approximate; the real plastic has a fine sparkling pigment."),
        ShellMaterial("pkmn_gold_jp", "Pocket Monsters Gold (Japan, two-tone)", MaterialKind.EDITION, hex(0x303D66), back = hex(0x666870),
            note = "Dark blue front, dark grey back (Game Verifying, DMG guide); colour values approximate."),
        ShellMaterial("pkmn_silver_jp", "Pocket Monsters Silver (Japan, two-tone)", MaterialKind.EDITION, hex(0x666870), back = hex(0x303D66),
            note = "Dark grey front, dark blue back (Game Verifying: the opposite of Gold); colour values approximate."),
        ShellMaterial("pkmn_crystal", "Pokémon Crystal clear cyan", MaterialKind.EDITION, hex(0x86C7E4), hex(0xD5F0FB), roughness = 0.25f,
            transmission = 0.5f, glitter = 0.6f, edge = hex(0x65A9CB), note = "Approximate: clear cyan with micro-glitter (Game Verifying: \"Clear Cyan\")."),
        ShellMaterial("gba_ruby", "Pokémon Ruby transparent red", MaterialKind.EDITION, hex(0xBE3547), roughness = 0.3f, transmission = 0.5f),
        ShellMaterial("gba_sapphire", "Pokémon Sapphire transparent blue", MaterialKind.EDITION, hex(0x326AB5), roughness = 0.3f, transmission = 0.5f),
        ShellMaterial("gba_emerald", "Pokémon Emerald transparent green", MaterialKind.EDITION, hex(0x279A60), roughness = 0.3f, transmission = 0.5f),
        ShellMaterial("gba_firered", "Pokémon FireRed red-orange", MaterialKind.EDITION, hex(0xDA5848), roughness = 0.35f, transmission = 0.18f,
            note = "Provisional: Game Verifying's casing page says orange, its GBA depository translucent red. Less see-through than Ruby."),
        ShellMaterial("gba_leafgreen", "Pokémon LeafGreen green", MaterialKind.EDITION, hex(0x77A95B), roughness = 0.35f, transmission = 0.18f,
            note = "Approximate; translucent green, less see-through than Emerald."),
        ShellMaterial("ntr031", "DS infrared crimson (NTR-031)", MaterialKind.EDITION, hex(0x241C22), roughness = 0.35f, transmission = 0.12f, edge = hex(0x653540),
            note = "Approximate: very dark crimson, near black, slightly translucent in strong light (Game Verifying)."),
        ShellMaterial("dsi_white", "DSi-exclusive white", MaterialKind.EDITION, hex(0xE6E6E2),
            note = "Approximate; used for DSi-only games by their DS header (MediaEditions.DSI_WHITE, Update 300)."),
        ShellMaterial("switch2_red", "Switch 2 red", MaterialKind.EDITION, hex(0xC53B3D), hex(0xE46B68), roughness = 0.5f),
        ShellMaterial("stadium2", "Pokémon Stadium 2 gold / silver", MaterialKind.EDITION, hex(0xB99D56), hex(0xE6D18B), roughness = 0.35f,
            metallic = 0.7f, glitter = 0.45f, back = hex(0xA9AEB6), note = "Approximate: gold front, silver back, fine pigment speckles (two plastics)."),
        ShellMaterial("classic_nes_grey", "Classic NES Series medium grey", MaterialKind.EDITION, hex(0x8C8D90),
            note = "Approximate: \"Medium Grey, not the standard shade\" (Game Verifying)."),
        ShellMaterial("gba_video_grey", "GBA Video light grey", MaterialKind.EDITION, hex(0xC9CACC),
            note = "Approximate: \"Light Grey, not the standard shade\" (Game Verifying)."),
        ShellMaterial("twisted_grey", "WarioWare: Twisted! transparent grey", MaterialKind.EDITION, hex(0x9A9DA4), roughness = 0.3f, transmission = 0.45f),
        ShellMaterial("mawaru_white", "Mawaru Made in Wario translucent white", MaterialKind.EDITION, hex(0xE0E9EB), roughness = 0.5f, transmission = 0.35f),
        ShellMaterial("yoshi_green_white", "Yoshi Topsy-Turvy green and white", MaterialKind.EDITION, hex(0x4FA84A), back = hex(0xECECE9),
            note = "Approximate: \"Green and White\" (Game Verifying); which half is which not confirmed. PAL copies have a different front casing."),
        ShellMaterial("command_teal", "Command Master clear teal", MaterialKind.EDITION, hex(0x218C87), roughness = 0.3f, transmission = 0.45f),
        ShellMaterial("mobile_blue", "Mobile Series clear blue", MaterialKind.EDITION, hex(0x326AB5), roughness = 0.3f, transmission = 0.45f),
        ShellMaterial("kirby_pink", "Kirby Tilt 'n' Tumble clear pink", MaterialKind.EDITION, hex(0xE99ECA), roughness = 0.3f, transmission = 0.45f)
    )

    private val byId = all.associateBy { it.id }

    fun of(id: String?): ShellMaterial? = id?.let { byId[it] }

    /** The shell colour for a choice's colour key "m:<id>", or null. */
    fun colourFor(key: String?): ShellColour? = key?.takeIf { it.startsWith("m:") }?.let { of(it.removePrefix("m:"))?.colour() }

    fun ofKind(kind: MaterialKind): List<ShellMaterial> = all.filter { it.kind == kind }

    /** "#RRGGBB" for the picker. */
    fun hexOf(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)
}
