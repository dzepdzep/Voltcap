package com.shelf.launcher.ui

import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game

/*
 * Update 191: the real game cases, console by console and region by region. Each console's packaging was looked up
 * (sizes, plastic colour and how see-through it is, where the disc / card sits, memory card and Game Boy Advance slots,
 * cardboard boxes) and the 3D cases (Case3D.kt) and the opened ones (CaseOpen.kt) are drawn from this table instead of
 * one DVD-style case with different art.
 *
 * Every entry says how sure it is ([CaseCheck]): sizes from a museum or a maker's measurement, sizes from a published
 * standard (the DVD / Blu-ray keep case, the CD jewel case), estimates (from the inside of protectors made for that box),
 * or placeholders where nothing was found — those draw the nearest known case and say so here and in CASES.md. Hex
 * colours are matched by eye from photos.
 *
 * Steam, Android, arcade and PICO-8 games never had retail cases: they get none from here (Voltcap's own case stays,
 * labelled as Voltcap's).
 */

/** Where a game's case is from: its file name's tags ("(USA)", "(Europe)", "(Japan)"), else Settings › Game look › Case region. */
internal enum class CaseRegion(val label: String) { US("North America"), EU("Europe / PAL"), JP("Japan") }

internal enum class CaseShell {
    /** A DVD / Blu-ray style keep case: living hinge, sleeve, hub or clips. */
    KEEP,
    /** A small keep case for a card (DS, 3DS, Vita, Switch). */
    CARDCASE,
    /** A CD jewel case: clear lid on pivots, separate tray, booklet in the lid. */
    JEWEL,
    /** A tall clear case for a CD (early Sega CD / Saturn in North America). */
    LONGBOX,
    /** A rigid plastic clamshell for a cartridge (Mega Drive, Master System, Neo Geo AES). */
    CLAMSHELL,
    /** A folded cardboard box: tuck flap and dust flaps on top, an inner tray, the cartridge in it. */
    CARDBOARD,
    /** A cardboard box whose front opens like a book onto a plastic tray (Intellivision). */
    BOOKBOX,
    /** A computer game's big box (cardboard). */
    BIGBOX,
    /** A compact cassette case. */
    CASSETTE
}

internal enum class CaseHeld(val round: Boolean) { DISC120(true), DISC80(true), UMD(false), CARD(false), CART(false), FLOPPY(false), TAPE(false) }

/** How sure an entry is. */
internal enum class CaseCheck(val label: String) {
    MEASURED("Measured"), SOURCED("From a published size"), ESTIMATED("Estimated"), PLACEHOLDER("Placeholder: no data found")
}

internal class CaseSpec(
    val platform: String,
    /** Null: every region. */
    val region: CaseRegion?,
    val shell: CaseShell,
    val wMm: Float, val hMm: Float, val tMm: Float,
    /** The plastic (or a box's print), and how opaque it is (1 = solid, 0.1 = clear). */
    val plastic: Int, val opacity: Float,
    /** Inside: a jewel case's tray, a box's inner tray. */
    val tray: Int,
    val media: CaseHeld,
    /** The disc / card / cartridge's width (mm), for its place inside. */
    val mediaWMm: Float,
    /** Xbox One / Series: the disc is on the left (inside the front cover). */
    val mediaLeft: Boolean = false,
    /** Its middle on its side's panel (0…1 across, 0…1 down). */
    val mediaU: Float = 0.5f, val mediaV: Float = 0.5f,
    val memCard: Boolean = false,
    val gbaSlot: Boolean = false,
    /** A thumb grip on the opening edge. */
    val grip: Boolean = shell == CaseShell.KEEP || shell == CaseShell.CARDCASE,
    val check: CaseCheck,
    val note: String
) {
    val aspect: Float get() = wMm / hMm
    val depth: Float get() = tMm / hMm
    val cardboard: Boolean get() = shell == CaseShell.CARDBOARD || shell == CaseShell.BIGBOX
}

internal object CaseCatalog {
    private const val BLACK = 0xFF0D0D0D.toInt()
    private const val CLEAR = 0xFFF2F4F5.toInt()
    private const val BLACK_TRAY = 0xFF111111.toInt()
    private const val CARD_WHITE = 0xFFEFECE4.toInt()
    private const val CARD_BEIGE = 0xFFC8B89A.toInt()
    private const val CARD_DARK = 0xFF1A1A1A.toInt()

    private val US = CaseRegion.US
    private val EU = CaseRegion.EU
    private val JP = CaseRegion.JP
    private val ALL: CaseRegion? = null

    private fun keep(p: String, r: CaseRegion?, w: Float, h: Float, t: Float, col: Int, op: Float, media: CaseHeld = CaseHeld.DISC120,
                     mediaW: Float = 120f, check: CaseCheck, note: String, mediaLeft: Boolean = false, mem: Boolean = false,
                     u: Float = 0.5f, v: Float = 0.5f, gba: Boolean = false, shell: CaseShell = CaseShell.KEEP) =
        CaseSpec(p, r, shell, w, h, t, col, op, col, media, mediaW, mediaLeft, u, v, mem, gba, check = check, note = note)

    private fun jewel(p: String, r: CaseRegion?, t: Float, tray: Int, check: CaseCheck, note: String, media: CaseHeld = CaseHeld.DISC120, mediaW: Float = 120f) =
        CaseSpec(p, r, CaseShell.JEWEL, 142f, 125f, t, CLEAR, 0.08f, tray, media, mediaW, check = check, note = note)

    private fun box(p: String, r: CaseRegion?, w: Float, h: Float, t: Float, tray: Int, check: CaseCheck, note: String, shell: CaseShell = CaseShell.CARDBOARD) =
        CaseSpec(p, r, shell, w, h, t, 0xFF3A3832.toInt(), 1f, tray, if (shell == CaseShell.BIGBOX) CaseHeld.FLOPPY else CaseHeld.CART, 0f, check = check, note = note)

    private fun clam(p: String, r: CaseRegion?, w: Float, h: Float, t: Float, mediaW: Float, check: CaseCheck, note: String, col: Int = 0xFF111111.toInt(), op: Float = 1f) =
        CaseSpec(p, r, CaseShell.CLAMSHELL, w, h, t, col, op, col, CaseHeld.CART, mediaW, check = check, note = note)

    /** Everything known, in order: a region's own entry wins over an every-region one. */
    val all: List<CaseSpec> = listOf(
        // ── PlayStation ──
        jewel("PS1", US, 10.4f, BLACK_TRAY, CaseCheck.SOURCED, "Standard CD jewel case (142×125×10.4) with the black PlayStation tray. Early 1995 longboxes not drawn: no measurements found."),
        jewel("PS1", EU, 17.4f, BLACK_TRAY, CaseCheck.ESTIMATED, "PAL: deeper jewel case for the multi-language manual; 17.4 mm from a replacement-insert maker, not measured."),
        jewel("PS1", JP, 10.4f, BLACK_TRAY, CaseCheck.SOURCED, "Standard jewel case as North America. The paper obi on the spine isn't drawn."),
        keep("PS2", US, 135f, 190f, 14f, BLACK, 1f, mem = true, check = CaseCheck.SOURCED, note = "DVD keep case, black. Memory card holder in most; its place inside isn't confirmed. Greatest Hits red / Platinum silver not drawn."),
        keep("PS2", EU, 135f, 190f, 14f, 0xFF111A2E.toInt(), 1f, mem = true, check = CaseCheck.SOURCED, note = "PAL: black early on, then dark blue from about 2001 (Wikipedia). The dark blue is drawn. Platinum silver not drawn."),
        keep("PS2", JP, 135f, 190f, 14f, BLACK, 1f, check = CaseCheck.ESTIMATED, note = "Black assumed; Japanese differences not found."),
        keep("PS3", ALL, 135f, 171.5f, 12f, 0xFFE8ECEF.toInt(), 0.25f, check = CaseCheck.SOURCED, note = "Blu-ray keep case, clear. Greatest Hits red / Platinum silver not drawn."),
        keep("PS4", ALL, 135f, 171.5f, 12f, 0xFF1E5BB8.toInt(), 0.55f, check = CaseCheck.SOURCED, note = "Blu-ray keep case, see-through blue."),
        keep("PS5", ALL, 135f, 171.5f, 12f, 0xFF1F5FC4.toInt(), 0.55f, check = CaseCheck.SOURCED, note = "Blu-ray keep case, see-through blue."),
        keep("PSP", ALL, 104f, 177f, 14f, CLEAR, 0.15f, CaseHeld.UMD, 64f, check = CaseCheck.SOURCED, note = "Clear UMD case, the UMD in the middle of the right side. Sources differ on size (177×104 or 168×99); the UMD's holder isn't confirmed."),
        keep("PSVita", ALL, 105f, 135f, 12f, 0xFF2A5DB0.toInt(), 0.6f, CaseHeld.CARD, 15f, v = 0.3f, shell = CaseShell.CARDCASE, check = CaseCheck.SOURCED, note = "Blue card case 135×105×12. How see-through, and where the card sits, aren't confirmed."),
        // ── Xbox ──
        keep("Xbox", ALL, 135f, 190f, 14f, 0xFF3E8E2F.toInt(), 0.95f, check = CaseCheck.SOURCED, note = "Green, near opaque (not black). Platinum Hits silver not drawn."),
        keep("Xbox360", ALL, 135f, 190f, 14f, 0xFF5DBB3F.toInt(), 0.5f, check = CaseCheck.SOURCED, note = "See-through lime green. Platinum Hits grey / Kinect purple not drawn."),
        keep("XboxOne", ALL, 135f, 171.5f, 12f, 0xFF2E9E3E.toInt(), 0.5f, mediaLeft = true, check = CaseCheck.SOURCED, note = "See-through green Blu-ray case; the disc is on the left, inside the front cover."),
        keep("XboxSeries", ALL, 135f, 171.5f, 12f, 0xFF2E9E3E.toInt(), 0.5f, mediaLeft = true, check = CaseCheck.SOURCED, note = "As Xbox One: see-through green, disc on the left."),
        // ── Nintendo, plastic ──
        keep("GameCube", US, 135f, 190f, 14f, 0xFF111111.toInt(), 1f, CaseHeld.DISC80, 80f, mem = true, check = CaseCheck.SOURCED, note = "Black DVD-size case with an 8 cm hub; memory card holder in most (its place inside isn't confirmed)."),
        keep("GameCube", EU, 135f, 190f, 14f, 0xFF111111.toInt(), 1f, CaseHeld.DISC80, 80f, mem = true, check = CaseCheck.SOURCED, note = "As North America."),
        keep("GameCube", JP, 107f, 149f, 17f, 0xFFF2F2F2.toInt(), 0.25f, CaseHeld.DISC80, 80f, mem = true, check = CaseCheck.SOURCED, note = "Japan: compact clear case 149×107×17. An outer sleeve isn't confirmed, so not drawn."),
        keep("Wii", ALL, 135f, 190f, 14f, 0xFFF7F7F7.toInt(), 1f, check = CaseCheck.SOURCED, note = "White DVD-size case."),
        keep("WiiU", ALL, 135f, 190f, 14f, 0xFF1E9BD7.toInt(), 1f, check = CaseCheck.SOURCED, note = "Bright blue DVD-size case."),
        keep("NDS", US, 135f, 122f, 15f, 0xFF111111.toInt(), 1f, CaseHeld.CARD, 33f, v = 0.3f, gba = true, shell = CaseShell.CARDCASE, check = CaseCheck.SOURCED, note = "Black slim case (North America, Japan). Card at the top, Game Boy Advance slot under it (places not confirmed)."),
        keep("NDS", JP, 135f, 122f, 15f, 0xFF111111.toInt(), 1f, CaseHeld.CARD, 33f, v = 0.3f, gba = true, shell = CaseShell.CARDCASE, check = CaseCheck.SOURCED, note = "As North America."),
        keep("NDS", EU, 135f, 122f, 20f, 0xFFEEF2F5.toInt(), 0.2f, CaseHeld.CARD, 33f, v = 0.3f, gba = true, shell = CaseShell.CARDCASE, check = CaseCheck.SOURCED, note = "Europe: clear and thicker (20 mm)."),
        keep("3DS", ALL, 135f, 122f, 12f, 0xFFFAFAFA.toInt(), 1f, CaseHeld.CARD, 33f, v = 0.32f, shell = CaseShell.CARDCASE, check = CaseCheck.SOURCED, note = "White in every region. The moulded cut-outs inside aren't confirmed, so only the card slot is drawn."),
        keep("Switch", ALL, 104f, 170f, 10f, 0xFFF4F6F8.toInt(), 0.15f, CaseHeld.CARD, 21f, u = 0.66f, v = 0.8f, shell = CaseShell.CARDCASE, check = CaseCheck.SOURCED, note = "Clear (the red is the paper insert). Card holder low on the right, tabs above it for paper."),
        keep("Switch2", ALL, 104f, 170f, 10f, 0xFFC8102E.toInt(), 0.7f, CaseHeld.CARD, 21f, u = 0.66f, v = 0.8f, shell = CaseShell.CARDCASE, check = CaseCheck.SOURCED, note = "Red, red inside too; 170×104×10 like Switch (Wikipedia's keep-case table, update 206). How see-through isn't confirmed."),
        // ── Nintendo, cardboard ──
        box("NES", US, 127f, 180f, 24f, CARD_WHITE, CaseCheck.ESTIMATED, "From the inside of a protector made for it (the box is 1–2 mm smaller). Which end opens and the inner parts aren't confirmed; drawn with a top tuck flap."),
        box("NES", EU, 127f, 180f, 24f, CARD_WHITE, CaseCheck.ESTIMATED, "As North America; PAL boxes differ slightly (exact size not found)."),
        box("NES", JP, 127f, 180f, 24f, CARD_WHITE, CaseCheck.PLACEHOLDER, "Famicom boxes come in small, standard and large sizes; none measured. The NES box is drawn."),
        box("SNES", US, 181f, 127f, 32f, CARD_WHITE, CaseCheck.ESTIMATED, "Landscape box with a folded cardboard inner tray (confirmed). Size from protectors."),
        box("SNES", EU, 181f, 127f, 32f, CARD_WHITE, CaseCheck.ESTIMATED, "As North America (not confirmed)."),
        box("SNES", JP, 193f, 107f, 33f, CARD_WHITE, CaseCheck.ESTIMATED, "Super Famicom: smaller and a different shape; from a protector's inside, its orientation not confirmed."),
        box("N64", US, 181f, 127f, 32f, CARD_WHITE, CaseCheck.ESTIMATED, "Landscape box with an inner tray (confirmed). Size from protectors."),
        box("N64", EU, 181f, 127f, 32f, CARD_WHITE, CaseCheck.ESTIMATED, "As North America (not confirmed)."),
        box("N64", JP, 181f, 127f, 32f, CARD_WHITE, CaseCheck.PLACEHOLDER, "Japanese N64 boxes differ; size not found. The Western box is drawn."),
        box("GB", US, 125f, 125f, 23f, CARD_WHITE, CaseCheck.ESTIMATED, "Square box (one protector fits Game Boy, Color and Advance boxes)."),
        box("GB", EU, 125f, 125f, 23f, CARD_WHITE, CaseCheck.ESTIMATED, "As North America."),
        box("GB", JP, 90f, 105f, 20f, CARD_WHITE, CaseCheck.ESTIMATED, "Early Japanese boxes are very small (from a protector's inside)."),
        box("GBC", US, 125f, 125f, 23f, CARD_WHITE, CaseCheck.ESTIMATED, "As Game Boy."),
        box("GBC", EU, 125f, 125f, 23f, CARD_WHITE, CaseCheck.ESTIMATED, "As Game Boy."),
        box("GBC", JP, 98f, 124f, 22f, CARD_WHITE, CaseCheck.ESTIMATED, "From a protector's inside."),
        box("GBA", US, 125f, 125f, 23f, CARD_WHITE, CaseCheck.ESTIMATED, "Cardboard inner tray (confirmed)."),
        box("GBA", EU, 125f, 125f, 23f, CARD_WHITE, CaseCheck.ESTIMATED, "PAL has its own inner tray (confirmed); same box size assumed."),
        box("GBA", JP, 125f, 125f, 23f, CARD_WHITE, CaseCheck.PLACEHOLDER, "Japanese size not found; the Western box is drawn."),
        box("VirtualBoy", ALL, 125f, 125f, 23f, CARD_WHITE, CaseCheck.ESTIMATED, "A Game Boy box protector fits it (two sources); inside not found."),
        box("PokemonMini", ALL, 90f, 90f, 20f, CARD_WHITE, CaseCheck.PLACEHOLDER, "Nothing found about its packaging."),
        // ── Sega ──
        clam("Genesis", US, 145f, 210f, 23f, 108f, CaseCheck.ESTIMATED, "Black clamshell, from a protector's inside (an upper bound). Late first-party and EA games came in cardboard boxes (not drawn)."),
        clam("Genesis", EU, 145f, 210f, 23f, 108f, CaseCheck.ESTIMATED, "As North America (not measured)."),
        clam("Genesis", JP, 145f, 210f, 23f, 108f, CaseCheck.PLACEHOLDER, "Japanese clamshells are smaller; size not found. The Western one is drawn."),
        clam("MasterSystem", ALL, 145f, 210f, 23f, 95f, CaseCheck.PLACEHOLDER, "Black clamshell (confirmed), size assumed the Mega Drive's. Early cardboard boxes in North America not drawn."),
        clam("32X", ALL, 145f, 210f, 23f, 108f, CaseCheck.PLACEHOLDER, "Nothing found; the Mega Drive clamshell is drawn."),
        CaseSpec("SegaCD", US, CaseShell.LONGBOX, 146f, 208f, 23f, CLEAR, 0.15f, BLACK_TRAY, CaseHeld.DISC120, 120f, check = CaseCheck.ESTIMATED, note = "Tall clear longbox, from a protector's inside. Later North American games used jewel cases (not drawn)."),
        jewel("SegaCD", EU, 10.4f, BLACK_TRAY, CaseCheck.PLACEHOLDER, "PAL Mega-CD case not found (possibly a taller, thicker case); a jewel case is drawn."),
        jewel("SegaCD", JP, 10.4f, BLACK_TRAY, CaseCheck.ESTIMATED, "Jewel case assumed; may be thicker."),
        keep("Saturn", EU, 133f, 205f, 16f, BLACK, 1f, check = CaseCheck.ESTIMATED, note = "PAL: tall black case (205×133×16, not measured), card glued round it."),
        CaseSpec("Saturn", US, CaseShell.LONGBOX, 146f, 208f, 23f, CLEAR, 0.15f, BLACK_TRAY, CaseHeld.DISC120, 120f, check = CaseCheck.ESTIMATED, note = "Tall clear longbox as Sega CD. Late North American games used jewel cases (not drawn)."),
        jewel("Saturn", JP, 10.4f, BLACK_TRAY, CaseCheck.ESTIMATED, "Jewel case; tray colour and obi not confirmed."),
        jewel("Dreamcast", US, 10.2f, 0xFFE8EEF2.toInt(), CaseCheck.SOURCED, "Standard jewel case with a clear tray."),
        jewel("Dreamcast", JP, 10.4f, 0xFFF5F5F5.toInt(), CaseCheck.SOURCED, "White tray in about 95 % of games (Shenmue black)."),
        jewel("Dreamcast", EU, 20f, 0xFF2A2C31.toInt(), CaseCheck.PLACEHOLDER, "PAL cases are thicker (confirmed); 20 mm and the tray colour aren't."),
        box("GameGear", ALL, 111f, 152f, 24f, CARD_WHITE, CaseCheck.PLACEHOLDER, "Cardboard box with a hang tab (North America, confirmed); size not found."),
        // ── Neo Geo, NEC, Bandai, Panasonic, Philips ──
        clam("NeoGeo", ALL, 171f, 222f, 51f, 146f, CaseCheck.MEASURED, "Big hard black clamshell, 222×171×51 (measured by a museum). Early Japanese soft boxes not drawn."),
        jewel("NeoGeoCD", ALL, 10.4f, BLACK_TRAY, CaseCheck.ESTIMATED, "Jewel case assumed; some games came in double cases."),
        clam("NeoGeoPocket", EU, 128f, 128f, 26f, 40f, CaseCheck.SOURCED, "Western hard case 128×128×26 (a maker's size); colour and hinge not confirmed.", 0xFFDDE6EA.toInt(), 0.4f),
        clam("NeoGeoPocket", US, 128f, 128f, 26f, 40f, CaseCheck.ESTIMATED, "As Europe (not confirmed).", 0xFFDDE6EA.toInt(), 0.4f),
        box("NeoGeoPocket", JP, 90f, 120f, 25f, CARD_WHITE, CaseCheck.ESTIMATED, "Japanese cardboard box (exists, confirmed); size estimated."),
        jewel("PCEngine", JP, 10.4f, BLACK_TRAY, CaseCheck.ESTIMATED, "HuCard in a jewel-style case; size assumed a CD jewel case's.", CaseHeld.CARD, 54f),
        clam("PCEngine", US, 135f, 185f, 18f, 54f, CaseCheck.PLACEHOLDER, "TurboGrafx-16 case; size not found."),
        clam("PCEngine", EU, 135f, 185f, 18f, 54f, CaseCheck.PLACEHOLDER, "As the TurboGrafx-16 case; not found."),
        jewel("PCEngineCD", ALL, 10.4f, BLACK_TRAY, CaseCheck.ESTIMATED, "Jewel case. The TurboGrafx-CD's bigger case wasn't found, so a jewel case is drawn there too."),
        box("WonderSwan", ALL, 90f, 120f, 25f, CARD_WHITE, CaseCheck.ESTIMATED, "Japanese cardboard box; size estimated."),
        box("3DO", US, 152f, 305f, 30f, CARD_WHITE, CaseCheck.ESTIMATED, "Cardboard longbox round a jewel case (confirmed by listings); the 12×6 in music longbox size assumed."),
        jewel("3DO", EU, 10.4f, BLACK_TRAY, CaseCheck.ESTIMATED, "Plain jewel case (believed)."),
        jewel("3DO", JP, 10.4f, BLACK_TRAY, CaseCheck.ESTIMATED, "Plain jewel case (believed)."),
        jewel("CDi", ALL, 10.4f, BLACK_TRAY, CaseCheck.PLACEHOLDER, "Not researched in time; a jewel case is drawn."),
        // ── Atari, Coleco, Mattel, GCE ──
        box("Atari2600", ALL, 140f, 192f, 38f, CARD_BEIGE, CaseCheck.ESTIMATED, "Printed cardboard box; the cartridge in a cardboard insert. Size from protectors (one fits 2600 and 7800 boxes)."),
        box("Atari7800", ALL, 140f, 192f, 38f, CARD_BEIGE, CaseCheck.ESTIMATED, "As Atari 2600."),
        box("Lynx", ALL, 140f, 192f, 38f, CARD_DARK, CaseCheck.ESTIMATED, "Early big box (a 2600 protector fits it). Later smaller boxes not drawn: size not found."),
        box("Jaguar", ALL, 135f, 190f, 45f, CARD_DARK, CaseCheck.ESTIMATED, "Cardboard box with a plastic inner tray; size estimated."),
        box("ColecoVision", ALL, 140f, 215f, 25f, CARD_BEIGE, CaseCheck.ESTIMATED, "Size from a third-party box (not verified)."),
        CaseSpec("Intellivision", ALL, CaseShell.BOOKBOX, 130f, 190f, 30f, 0xFF2A2A2A.toInt(), 1f, CARD_DARK, CaseHeld.CART, 68f, grip = false, check = CaseCheck.ESTIMATED, note = "The front opens like a book (confirmed): the cartridge in a plastic tray, the manual and overlays in the cover. Size estimated."),
        box("Vectrex", ALL, 140f, 190f, 40f, CARD_BEIGE, CaseCheck.ESTIMATED, "Printed box with the screen overlay; size estimated."),
        // ── Computers ──
        CaseSpec("MSX", EU, CaseShell.CASSETTE, 69f, 109f, 17f, CLEAR, 0.3f, BLACK_TRAY, CaseHeld.TAPE, 63f, check = CaseCheck.SOURCED, note = "Compact cassette case (standard size)."),
        box("Amiga", ALL, 186f, 237f, 41f, CARD_BEIGE, CaseCheck.MEASURED, "Big box; sizes varied — this one measured (Mania Mansion).", CaseShell.BIGBOX),
        box("DOS", ALL, 150f, 200f, 50f, CARD_BEIGE, CaseCheck.SOURCED, "Big box; no standard size, most about 20×15×5 cm.", CaseShell.BIGBOX),
        box("C64", ALL, 150f, 200f, 50f, CARD_BEIGE, CaseCheck.SOURCED, "Disk games: a big box (no standard size).", CaseShell.BIGBOX),
    )

    /** A C64 game on tape (.tap / .t64) comes in a cassette case. */
    private val C64_TAPE = CaseSpec("C64", ALL, CaseShell.CASSETTE, 69f, 109f, 17f, CLEAR, 0.3f, BLACK_TRAY, CaseHeld.TAPE, 63f, check = CaseCheck.SOURCED, note = "Tape games: a compact cassette case (standard size).")

    /** Consoles whose games never had a retail case. */
    val noRetailCase = setOf("Steam", "Android", "App", "Arcade", "Naomi", "Atomiswave", "PICO8", "Pico8", "ScummVM", "Disc")

    /** [platform]'s case in [region] (a region's own entry, else an every-region one, else another region's). */
    fun of(platform: String?, region: CaseRegion): CaseSpec? {
        if (platform == null || platform in noRetailCase) return null
        var any: CaseSpec? = null
        var allRegions: CaseSpec? = null
        for (s in all) {
            if (s.platform != platform) continue
            if (s.region == region) return s
            if (s.region == null) allRegions = s
            if (any == null) any = s
        }
        return allRegions ?: any
    }

    private val cache = android.util.LruCache<String, Any>(600)
    private val NONE = Any()

    /** [g]'s case: its console's (R1 › Case's when one is picked for all) in its region. */
    fun forGame(g: Game): CaseSpec? {
        val platform = LookStyles.casePlatform(g.platform)
        val pref = Features.caseRegion.state.value
        val file = g.romPath ?: g.name
        val key = "$platform|${pref.ordinal}|$file"
        cache.get(key)?.let { return it as? CaseSpec }
        val region = regionOf(file, pref)
        val tape = platform == "C64" && (file.endsWith(".tap", true) || file.endsWith(".t64", true))
        val spec = if (tape) C64_TAPE else of(platform, region)
        cache.put(key, spec ?: NONE)
        return spec
    }

    private val TAG = Regex("""[(\[]([^)\]]*)[)\]]""")
    private val US_WORDS = setOf("usa", "us", "u", "america", "canada", "ntsc-u", "brazil")
    private val EU_WORDS = setOf("europe", "eu", "eur", "e", "pal", "pal-e", "uk", "germany", "france", "spain", "italy", "netherlands",
        "sweden", "scandinavia", "australia", "a")
    private val JP_WORDS = setOf("japan", "jp", "jpn", "j", "ntsc-j", "korea", "asia", "k")

    /** The region a file name says ("Game (Europe).zip"); none or several (World): the default. */
    fun regionOf(file: String, pref: Features.CaseRegionChoice = Features.caseRegion.state.value): CaseRegion {
        val fallback = when (pref.code) { "EU" -> CaseRegion.EU; "JP" -> CaseRegion.JP; else -> CaseRegion.US }
        if (pref.fixed) return fallback
        val name = file.substringAfterLast('/')
        val found = LinkedHashSet<CaseRegion>()
        for (m in TAG.findAll(name)) {
            for (w in m.groupValues[1].split(',', '/', '+')) {
                when (w.trim().lowercase()) {
                    in US_WORDS -> found.add(CaseRegion.US)
                    in EU_WORDS -> found.add(CaseRegion.EU)
                    in JP_WORDS -> found.add(CaseRegion.JP)
                }
            }
            // (Only the first tag that names a region: "(Europe) (En,Fr,De)" mustn't read "De" as a region again.)
            if (found.isNotEmpty()) break
        }
        return when {
            found.size == 1 -> found.first()
            fallback in found -> fallback
            found.isNotEmpty() -> found.first()
            else -> fallback
        }
    }
}
