package com.shelf.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.shelf.launcher.data.Features

/*
 * Update 195: the cartridge collection — every console's real cartridge / card shell, drawn in code from researched
 * measurements (CARTRIDGES.md says how sure each one is), in one library with your cartridge pack's shapes. Any game can
 * go on any shell: the shell keeps its own shape, thickness and plastic (or a colour / clear plastic you pick), and its
 * label shows the game's own art with its real console's name — a PS2 game on an N64 shell says PlayStation 2. The
 * game's console, emulator and file never change; only how it's drawn. Per game (the game's menu › Cartridge shell…)
 * or for all of a console's games; Settings › Game look › Authentic drawn cartridges puts every cartridge console on
 * its own real shell.
 *
 * A shell is its front outline (mm, clockwise from top-left, each corner with its radius), its thickness (the 3D piece
 * is that outline made solid, InspectSlab), its label area, grip ridges, screws and contacts on the back, and parts that
 * stand out (a camera, a solar window, a lock-on flap). The label never covers the shell outside its own area.
 */

/** The shell picked for [platform]'s games: what MediaPick.platform carries ("~sh:<choice>|<console>"). */
internal const val SHELL_PREFIX = "~sh:"

/** Update 195: your cartridge pack's own pieces and the drawn shells — both drawn by Voltcap, not a console template. */
internal fun isCustomPiece(platform: String): Boolean = platform.startsWith(CUSTOM_CART) || platform.startsWith(SHELL_PREFIX)

internal enum class ShellGroup(val label: String) {
    NINTENDO("Nintendo"), SEGA("Sega"), ATARI("Atari"), NEC_SNK("NEC, SNK, Bandai"), OTHER("Other"), SPECIAL("Special carts")
}

internal class ShellColour(
    val key: String, val name: String, val argb: Int, val opacity: Float,
    /** Update 210 (ShellMaterials): a metallic / pearlescent sheen's colour (0: none). */
    val highlight: Int = 0,
    /** Update 210: how strong that sheen is (0…1). */
    val metallic: Float = 0f,
    /** Update 210: fine glitter / pigment speckles (0…1). */
    val glitter: Float = 0f,
    /** Update 210: the back half's own plastic, for two-tone shells (0: as the front). */
    val backArgb: Int = 0,
    /** Update 210: a matte plastic (less gloss over the shell). */
    val matte: Boolean = false
)

/** A part of a shell that isn't the plain body: x, y, w, h in mm. */
internal class ShellExtra(val kind: Int, val x: Float, val y: Float, val w: Float, val h: Float, val argb: Int = 0) {
    companion object {
        const val ACCENT = 0   // a coloured plastic section (EA's yellow)
        const val SLOT = 1     // a dark opening (SNES lock slots, a card slot)
        const val LENS = 2     // a round glass (camera)
        const val WINDOW = 3   // a dark glass window (solar sensor, LCD)
        const val FLAP = 4     // a hinged cover (lock-on)
        const val KEYPAD = 5   // a printed keypad (Microvision)
    }
}

internal class CartShell(
    val id: String, val name: String, val group: ShellGroup,
    val w: Float, val h: Float, val t: Float,
    /** x, y, corner radius (mm), clockwise from the top-left. */
    val outline: FloatArray,
    /** x, y, w, h (mm). */
    val label: FloatArray,
    val ridges: List<FloatArray> = emptyList(),
    val screws: List<FloatArray> = emptyList(),
    val colours: List<ShellColour>,
    val extras: List<ShellExtra> = emptyList(),
    /** Gold contacts seen on the back (cards): x, y, w, h. */
    val contactsBack: FloatArray? = null,
    /** Gold contacts seen on the front (HuCard, N-Gage): x, y, w, h. */
    val contactsFront: FloatArray? = null,
    val check: CaseCheck,
    val note: String,
    /** Update 214: the label's corner radius (mm) — the N64's sticker has round corners. */
    val labelRadius: Float = 0.8f,
    /** Update 215: how strongly the moulded lines ([ridges]) show, 0…1 (1 = grip ridges; the N64's flush panel lines are faint). */
    val ridgeDepth: Float = 1f,
    // (Update 239, #40: the N64 sticker's own shape — ConsoleMods' label table gives it 4 mm top / 3 mm bottom corners
    // and a top edge arched on a 325 mm diameter, like the shell's arched top. Defaults keep every other label as it was.)
    /** The label's bottom corners' radius (mm); below 0: as [labelRadius]. */
    val labelRadiusBottom: Float = -1f,
    /** The label's top edge arched up on a circle of this diameter (mm), its middle at the label's top; 0: straight. */
    val labelTopArc: Float = 0f,
    // (Update 240, #90: a sticker that folds over the top edge — NES and SNES, 7 mm by ConsoleMods' Cart Labels table.
    // [label] is only the part on the front; a label scan of the whole sticker has this much cut off its top (it's on
    // the top face) instead of being squashed into the front window. 0: no fold.)
    /** How much of the sticker (mm) folds over the top edge, beyond the front part [label]. */
    val labelFold: Float = 0f
) {
    val aspect: Float get() = w / h
    /** Thickness against the longest side (the solid piece's depth). */
    val depth: Float get() = t / maxOf(w, h)
}

internal object CartShells {
    private fun c(name: String, hex: Long, op: Float = 1f) = ShellColour("v:$name", name, hex.toInt(), op)
    private fun o(vararg v: Float) = v
    private fun rect(w: Float, h: Float, rTop: Float, rBottom: Float) = floatArrayOf(0f, 0f, rTop, w, 0f, rTop, w, h, rBottom, 0f, h, rBottom)
    private fun lab(x: Float, y: Float, w: Float, h: Float) = floatArrayOf(x, y, w, h)
    /** Grip ridges: x, y, w, h, count, 1 = across (horizontal lines) / 0 = up and down. */
    private fun rid(x: Float, y: Float, w: Float, h: Float, n: Int, across: Boolean = true) = floatArrayOf(x, y, w, h, n.toFloat(), if (across) 1f else 0f)
    private fun sc(x: Float, y: Float) = floatArrayOf(x, y)

    private val GREY = 0xFF8A8B8EL
    private val BLACK = 0xFF1C1C1EL

    val all: List<CartShell> = listOf(
        // ── Nintendo ──
        CartShell("nes", "NES Game Pak", ShellGroup.NINTENDO, 120f, 134f, 17f, rect(120f, 134f, 2f, 1f), lab(11.5f, 1f, 97f, 48f),
            listOf(rid(8f, 70f, 104f, 58f, 12)), listOf(sc(20f, 22f), sc(100f, 22f), sc(60f, 62f), sc(20f, 112f), sc(100f, 112f)),
            listOf(c("Grey", GREY), c("Gold", 0xFFC9A548)), check = CaseCheck.ESTIMATED,
            // (Update 240, #90: checked, not an axis bug — the 55 × 97 sticker is 97 wide and 55 tall, 7 mm of it over the
            // top edge, so 97 × 48 on the front, centred ((120 − 97) / 2 = 11.5). Only the fold is new: a scan of the whole
            // sticker loses its top 7 mm to the top face instead of being squashed. ConsoleMods doesn't mark it test-fitted.)
            labelFold = 7f,
            note = "Size and label (55×97 mm with 7 mm over the top) measured (NESdev, ConsoleMods); ridges, screw places and radii estimated."),
        CartShell("famicom", "Famicom cassette", ShellGroup.NINTENDO, 108f, 71f, 17f, rect(108f, 71f, 3f, 1f), lab(9f, 5f, 90f, 45.5f),
            listOf(rid(3f, 52f, 102f, 14f, 5)), emptyList(),
            listOf(c("Grey", 0xFFC8C8C8), c("Black", BLACK), c("Red", 0xFFB3262E), c("Yellow", 0xFFE8C51E), c("Blue", 0xFF2D4FA0)),
            check = CaseCheck.ESTIMATED, note = "Size from ConsoleMods (landscape), sticker 90×45.5 mm measured; outline estimated. Shell colours vary by game."),
        CartShell("snes_na", "SNES Game Pak (North America)", ShellGroup.NINTENDO, 135.85f, 87.7f, 19.7f, rect(135.85f, 87.7f, 3f, 1.5f),
            // (Update 240, #90: the sticker as ConsoleMods' Cart Labels table gives it, test-fitted on an OEM cartridge —
            // 82.5 × 44 mm with 7 mm folded over the top and 3 mm corners: 82.5 × 37 on the front from the top edge,
            // centred ((135.85 − 82.5) / 2), square along the fold, round at the bottom. It was 112 × 46 from 6 mm down,
            // nearly the whole front. Read like the NES row (the fold inside the 44, as the NES's 7 is inside its 55);
            // whether the 44 includes the fold isn't stated on the page.)
            lab(26.675f, 0f, 82.5f, 37f),
            listOf(rid(4f, 0.5f, 128f, 3f, 10, false)), listOf(sc(38f, 20f), sc(98f, 20f)),
            listOf(c("Grey", 0xFFA9A8AE), c("Black", 0xFF1E1E1F)),
            extras = listOf(ShellExtra(ShellExtra.SLOT, 11f, 62f, 8f, 14f), ShellExtra(ShellExtra.SLOT, 116.85f, 62f, 8f, 14f)),
            check = CaseCheck.ESTIMATED, labelRadius = 0f, labelRadiusBottom = 3f, labelFold = 7f,
            note = "Size measured (135.85×87.7×19.7); sticker 82.5×44 with 7 mm over the top (ConsoleMods, test-fitted); ridges and the early lock slots' places estimated."),
        CartShell("sfc", "Super Famicom / SNES PAL cassette", ShellGroup.NINTENDO, 130f, 86f, 20f, rect(130f, 86f, 12f, 1.5f), lab(17f, 8f, 96f, 52f),
            listOf(rid(3f, 18f, 9f, 44f, 7), rid(118f, 18f, 9f, 44f, 7)), listOf(sc(32f, 24f), sc(98f, 24f)),
            listOf(c("Grey", 0xFF9E9DA3)), check = CaseCheck.ESTIMATED,
            note = "Size from Wikipedia (130×86×20; ConsoleMods 127 wide); the big round top corners and side grips estimated."),
        // (Update 214: drawn as the real Game Pak looks straight on — measured off a straight-on photo of a PAL cartridge:
        // the top arched about 6.5 mm higher in the middle than at its ends, the raised middle panel between two grooves
        // about 22.7 mm in from each side, the sides stepping in about 1.6 mm some 56.5 mm down with the guide slot's
        // ledge there, and the label 57 × 63.5 mm with round corners (about 3 mm) 7 mm below the top. It was a flat-topped
        // block with grip ridges along its top, which the real front doesn't have. Same outline for every colour.)
        CartShell("n64", "Nintendo 64 Game Pak", ShellGroup.NINTENDO, 116f, 76.6f, 18.5f,
            o(0f, 6.5f, 3f, 14.5f, 3.656f, 8f, 29f, 1.625f, 8f, 43.5f, 0.406f, 8f, 58f, 0f, 8f, 72.5f, 0.406f, 8f, 87f, 1.625f, 8f,
                101.5f, 3.656f, 8f, 116f, 6.5f, 3f, 116f, 56.5f, 0.8f, 114.4f, 57.5f, 0.5f, 114.4f, 76.6f, 1.5f, 1.6f, 76.6f, 1.5f,
                1.6f, 57.5f, 0.5f, 0f, 56.5f, 0.8f),
            lab(29.5f, 7f, 57f, 63.5f),
            // (The middle panel's grooves, and the guide slots' ledge and edge low on each side.)
            listOf(rid(22.2f, 0f, 1f, 76.6f, 1, false), rid(92.8f, 0f, 1f, 76.6f, 1, false),
                rid(1.6f, 56.2f, 5.6f, 0.6f, 1), rid(108.8f, 56.2f, 5.6f, 0.6f, 1),
                rid(6.7f, 56.5f, 1f, 20.1f, 1, false), rid(108.3f, 56.5f, 1f, 20.1f, 1, false)),
            listOf(sc(28f, 22f), sc(88f, 22f)),
            listOf(c("Grey", 0xFF7C7D82), c("Black", 0xFF202022), c("Gold", 0xFFC9A84C), c("Red", 0xFFB3262E), c("Blue", 0xFF2D4FA0),
                c("Clear", 0xFFE6E8EA, 0.45f), c("Smoke", 0xFF3A3A40, 0.6f)),
            // (Update 215: the panel lines faint — the front is flush, they're only hairline seams.)
            // (Update 239, #40: the sticker's shape from ConsoleMods' Cart Labels table — 4 mm top / 3 mm bottom corners, the
            // top edge on a 325 mm circle (about 2.5 mm lower at its ends than in the middle). It was a plain 3 mm round
            // rectangle. Its size stays 57 × 63.5: the sources disagree (ConsoleMods 54.5 × 62.5, not test-fitted; the
            // GoldenEye photo 50.8 × 60.3; the Super Mario 64 photo 57 × 63.5), so it isn't changed until it's measured.)
            check = CaseCheck.ESTIMATED, labelRadius = 4f, ridgeDepth = 0.3f, labelRadiusBottom = 3f, labelTopArc = 325f,
            note = "Size measured (116×76.6×18.5, ConsoleMods / Wikipedia); arched top, panel grooves, side step and the 57×63.5 label measured off a straight-on photo (approximate; ConsoleMods' label table says 54.5×62.5); label corners 4 / 3 mm and its 325 mm top arc from ConsoleMods."),
        CartShell("gb", "Game Boy Game Pak", ShellGroup.NINTENDO, 57f, 65f, 7.8f, rect(57f, 65f, 1f, 0.8f), lab(7.5f, 14f, 42f, 37f),
            listOf(rid(4f, 3f, 49f, 9f, 4)), listOf(sc(28.5f, 22f)), listOf(c("Grey", 0xFFA3A2A8), c("Yellow", 0xFFE8C51E)),
            contactsBack = floatArrayOf(4f, 58f, 49f, 6f), check = CaseCheck.ESTIMATED,
            note = "Size (57×65×7.8) and sticker (42×37) measured; places estimated. The power-lock notch isn't drawn (its place unconfirmed)."),
        CartShell("gbc_dual", "Game Boy Color Game Pak (black)", ShellGroup.NINTENDO, 57f, 65.5f, 9f, rect(57f, 65.5f, 1f, 0.8f), lab(7.5f, 14f, 42f, 37f),
            listOf(rid(4f, 3f, 49f, 9f, 4)), listOf(sc(28.5f, 22f)), listOf(c("Black", 0xFF1C1C1E)),
            contactsBack = floatArrayOf(4f, 58.5f, 49f, 6f), check = CaseCheck.ESTIMATED, note = "Size measured; layout as Game Boy (estimated)."),
        CartShell("gbc", "Game Boy Color Game Pak (clear)", ShellGroup.NINTENDO, 57f, 65.5f, 9f, rect(57f, 65.5f, 1f, 0.8f), lab(7.5f, 14f, 42f, 37f),
            listOf(rid(4f, 3f, 49f, 9f, 4)), listOf(sc(28.5f, 22f)), listOf(c("Clear", 0xFFE6E8EA, 0.45f)),
            contactsBack = floatArrayOf(4f, 58.5f, 49f, 6f), check = CaseCheck.ESTIMATED, note = "Clear shell, the board shows through; size measured, layout estimated."),
        CartShell("gba", "Game Boy Advance Game Pak", ShellGroup.NINTENDO, 60f, 35f, 8f,
            o(0f, 0f, 3f, 60f, 0f, 3f, 60f, 8f, 0.5f, 58.5f, 8f, 0.5f, 58.5f, 35f, 1f, 1.5f, 35f, 1f, 1.5f, 8f, 0.5f, 0f, 8f, 0.5f),
            // (Update 240, #90: the sticker 43 × 22 mm with 1 mm corners — ConsoleMods' Cart Labels table, test-fitted on an
            // OEM cartridge — centred on the shell ((60 − 43) / 2 = 8.5); it was 50 wide, past the sticker. Its 9 mm from
            // the top stays (estimated). The 8 mm thickness stays too: ConsoleMods gives 9, Wikipedia's Game Boy Game Pak
            // 6–9 (body vs top lip); the piece has one thickness, so neither is "the" value until it's measured.)
            lab(8.5f, 9f, 43f, 22f),
            listOf(rid(4f, 1f, 52f, 5f, 4)), listOf(sc(30f, 12f)),
            listOf(c("Dark grey", 0xFF4A4A50), c("Ruby red", 0xFFC0202A, 0.7f), c("Sapphire blue", 0xFF2850B0, 0.7f), c("Emerald green", 0xFF2E9A48, 0.7f)),
            contactsBack = floatArrayOf(5f, 30f, 50f, 4.5f), check = CaseCheck.ESTIMATED, labelRadius = 1f,
            note = "Width 60 / 57.5 and height 35 measured; sticker 43×22 (ConsoleMods, test-fitted); the wide top's height and the 8 mm thickness estimated (ConsoleMods 9, Wikipedia 6–9)."),
        CartShell("vb", "Virtual Boy Game Pak", ShellGroup.NINTENDO, 75.8f, 73.74f, 6.61f, rect(75.8f, 73.74f, 3f, 1f), lab(11.9f, 6f, 52f, 42f),
            colours = listOf(c("Black", 0xFF232325)), extras = listOf(ShellExtra(ShellExtra.FLAP, 1.5f, 68.36f, 72.8f, 5.38f, 0xFF3A3A3D.toInt())),
            check = CaseCheck.ESTIMATED,
            note = "Size measured (75.8×68.36×6.61, 73.74 tall with its removable dust cap, drawn at the bottom); label about 52×42 measured."),
        // (Update 208: measured off a straight-on DS card photo (Advance Wars: Dual Strike, ScreenScraper, 517 px across the
        // 33 mm card): the sticker is 27 × 29 mm from 2.9 mm in and 1.6 mm down — it carries the printed NINTENDO DS
        // logo itself — with plain charcoal plastic below it and the lower left corner cut off. It was a 28 × 28 window
        // under a moulded ridge, the cover under a console band across it: it read as a little box.)
        CartShell("ds", "Nintendo DS Game Card", ShellGroup.NINTENDO, 33f, 35f, 3.8f,
            o(0f, 0f, 1.2f, 33f, 0f, 1.2f, 33f, 35f, 0.8f, 2.6f, 35f, 0.3f, 0f, 32.4f, 0.3f), lab(2.9f, 1.6f, 27f, 29f),
            emptyList(), colours = listOf(c("Dark grey", 0xFF45464B), c("White", 0xFFF2F2F2)), // (Update 210: V3's DS dark grey preset)
            contactsBack = floatArrayOf(2f, 29f, 29f, 5f), check = CaseCheck.ESTIMATED, note = "Size measured (33×35×3.8); sticker 27×29 and the cut corner measured off a photo (approximate)."),
        CartShell("3ds", "Nintendo 3DS Game Card", ShellGroup.NINTENDO, 34f, 35f, 3.8f,
            // (Update 208: the sticker as on the DS card — the 3DS card is the same form with its side tab.)
            o(0f, 0f, 1.5f, 33f, 0f, 0.3f, 33f, 3f, 0.3f, 34f, 3.5f, 0.3f, 34f, 7f, 0.3f, 33f, 7.5f, 0.3f, 33f, 35f, 0.8f, 2.6f, 35f, 0.3f, 0f, 32.4f, 0.3f), lab(2.9f, 1.6f, 27f, 29f),
            emptyList(), colours = listOf(c("Pale grey", 0xFFCFD0CC)), // (Update 210: pale grey / off-white, not paper white — V3)
            contactsBack = floatArrayOf(2f, 29f, 29f, 5f), check = CaseCheck.ESTIMATED, note = "Size measured; the side tab (it stops a 3DS card going into a DS) estimated."),
        CartShell("switch", "Nintendo Switch Game Card", ShellGroup.NINTENDO, 21f, 31f, 3f, rect(21f, 31f, 1.5f, 0.8f), lab(1.5f, 4f, 18f, 25f),
            listOf(rid(1f, 0.5f, 19f, 2.5f, 1)), colours = listOf(c("Dark grey", 0xFF2C2C30)),
            contactsBack = floatArrayOf(2f, 25f, 17f, 5f), check = CaseCheck.ESTIMATED, note = "Size 21×31×3 (Wikipedia; others say 3.3 thick); layout estimated."),
        CartShell("switch2", "Nintendo Switch 2 Game Card", ShellGroup.NINTENDO, 21f, 31f, 3f, rect(21f, 31f, 1.5f, 0.8f), lab(1.5f, 4f, 18f, 25f),
            listOf(rid(1f, 0.5f, 19f, 2.5f, 1)), colours = listOf(c("Red", 0xFFC53B3D)),
            contactsBack = floatArrayOf(2f, 25f, 17f, 5f), check = CaseCheck.ESTIMATED, note = "Same size as Switch, red (Nintendo confirms red cards; colour approximate); the rear notch isn't drawn."),
        CartShell("switch2_key", "Nintendo Switch 2 Game-Key Card", ShellGroup.NINTENDO, 21f, 31f, 3f, rect(21f, 31f, 1.5f, 0.8f), lab(1.5f, 4f, 18f, 25f),
            listOf(rid(1f, 0.5f, 19f, 2.5f, 1)), colours = listOf(c("Red", 0xFFC53B3D)),
            extras = listOf(ShellExtra(ShellExtra.ACCENT, 15.5f, 4.5f, 3.5f, 2.6f, 0xFFF2C200.toInt())),
            contactsBack = floatArrayOf(2f, 25f, 17f, 5f), check = CaseCheck.ESTIMATED, note = "As Switch 2; the label's key mark drawn top right (licence only, no game on the card)."),
        CartShell("pokemini", "Pokémon mini Game Pak", ShellGroup.NINTENDO, 26f, 25f, 6f, rect(26f, 25f, 2f, 1f), lab(2f, 2f, 22f, 17f),
            colours = listOf(c("Dark grey", 0xFF3A3A3E)), check = CaseCheck.PLACEHOLDER, note = "No measurements found: placeholder."),
        CartShell("fds", "Famicom Disk System disk", ShellGroup.NINTENDO, 71f, 76f, 3f, rect(71f, 76f, 1f, 1f), lab(5f, 5f, 61f, 40f),
            colours = listOf(c("Yellow", 0xFFF2C200), c("Blue", 0xFF2F5DA8)),
            extras = listOf(ShellExtra(ShellExtra.WINDOW, 22f, 52f, 27f, 20f, 0xFF4A3A2A.toInt())), check = CaseCheck.ESTIMATED,
            note = "71×76 from Wikipedia (worth re-checking); thickness and the media window estimated."),
        CartShell("64dd", "64DD disk", ShellGroup.NINTENDO, 101f, 103f, 10f, rect(101f, 103f, 2f, 2f), lab(10f, 8f, 81f, 45f),
            colours = listOf(c("Grey", 0xFF6E6E73)), extras = listOf(ShellExtra(ShellExtra.FLAP, 30f, 62f, 41f, 36f, 0xFFB9BCC2.toInt())),
            check = CaseCheck.PLACEHOLDER, note = "No measurements found: placeholder with a metal shutter."),
        // ── Sega ──
        CartShell("sg1000", "SG-1000 cartridge", ShellGroup.SEGA, 84.3f, 107.1f, 21.1f, rect(84.3f, 107.1f, 3f, 2f), lab(8f, 8f, 68f, 70f),
            colours = listOf(c("Black", BLACK)), check = CaseCheck.ESTIMATED, note = "Same form as the Mark III cart (forum); not measured itself."),
        CartShell("sega_card", "Sega Card / My Card", ShellGroup.SEGA, 54f, 85.5f, 2f, rect(54f, 85.5f, 3f, 3f), lab(2f, 2f, 50f, 72f),
            colours = listOf(c("Black", 0xFF1A1A1A)), contactsFront = floatArrayOf(4f, 77.5f, 46f, 8f), check = CaseCheck.ESTIMATED,
            note = "85.5×54×2 measured; label and contact area estimated."),
        CartShell("sms", "Master System cartridge", ShellGroup.SEGA, 109.1f, 69.3f, 16.9f, rect(109.1f, 69.3f, 3f, 2f), lab(14f, 4f, 81f, 52f),
            colours = listOf(c("Black", BLACK)), check = CaseCheck.ESTIMATED, note = "69.3×109.1×16.9 measured with calipers (SMS Power); label estimated."),
        CartShell("sms_jp", "Mark III / Master System (Japan) cartridge", ShellGroup.SEGA, 84.3f, 107.1f, 21.1f, rect(84.3f, 107.1f, 3f, 2f), lab(8f, 8f, 68f, 70f),
            colours = listOf(c("Black", BLACK)), check = CaseCheck.ESTIMATED, note = "107.1×84.3×21.1 measured with calipers; label estimated."),
        CartShell("genesis", "Mega Drive / Genesis cartridge", ShellGroup.SEGA, 109f, 70f, 17f,
            o(0f, 0f, 4f, 109f, 0f, 4f, 109f, 46f, 1f, 102f, 50f, 1f, 102f, 70f, 1f, 7f, 70f, 1f, 7f, 50f, 1f, 0f, 46f, 1f), lab(17.5f, 2f, 74f, 58f),
            screws = listOf(sc(54.5f, 20f)), colours = listOf(c("Black", BLACK)), check = CaseCheck.ESTIMATED,
            note = "109 / 95 wide, 70 tall, 17 thick and the label (74×67, 7 mm over the top) measured; the shoulders' place estimated."),
        CartShell("megadrive_jp", "Mega Drive cartridge (Japan)", ShellGroup.SEGA, 93f, 67f, 17f,
            o(0f, 0f, 6f, 93f, 0f, 6f, 93f, 67f, 3f, 0f, 67f, 3f, 0f, 52f, 0.5f, 3f, 52f, 0.5f, 3f, 42f, 0.5f, 0f, 42f, 0.5f), lab(8f, 5f, 77f, 50f),
            colours = listOf(c("Black", BLACK)), check = CaseCheck.ESTIMATED, note = "93×67×17 measured, with the cart-lock notch on the left (place estimated)."),
        CartShell("genesis_ea", "Electronic Arts Genesis cartridge", ShellGroup.SPECIAL, 95f, 88f, 17f, rect(95f, 88f, 3f, 1f), lab(10.5f, 24f, 74f, 56f),
            colours = listOf(c("Black", BLACK)), extras = listOf(ShellExtra(ShellExtra.ACCENT, 20f, 3f, 55f, 16f, 0xFFF2C200.toInt())),
            check = CaseCheck.ESTIMATED, note = "88×95×17 measured; the yellow EA section's size and place estimated."),
        CartShell("32x", "Sega 32X cartridge", ShellGroup.SEGA, 111f, 72f, 16f,
            o(0f, 0f, 4f, 111f, 0f, 4f, 111f, 50f, 1f, 103f, 54f, 1f, 103f, 72f, 1f, 8f, 72f, 1f, 8f, 54f, 1f, 0f, 50f, 1f), lab(18f, 6f, 75f, 43f),
            colours = listOf(c("Black", BLACK)), check = CaseCheck.ESTIMATED, note = "111×72×16 and the 75×43 label measured (width and height read as landscape); shoulders estimated."),
        CartShell("virtua_racing", "Virtua Racing (SVP) cartridge", ShellGroup.SPECIAL, 109f, 108f, 17f,
            o(0f, 0f, 5f, 109f, 0f, 5f, 109f, 84f, 1f, 102f, 88f, 1f, 102f, 108f, 1f, 7f, 108f, 1f, 7f, 88f, 1f, 0f, 84f, 1f), lab(17.5f, 2f, 74f, 78f),
            colours = listOf(c("Black", BLACK)), check = CaseCheck.ESTIMATED, note = "108×109×17 measured; the rest assumed from the Genesis base."),
        CartShell("sonic_knuckles", "Sonic & Knuckles lock-on cartridge", ShellGroup.SPECIAL, 121.65f, 84f, 22f,
            o(2.8f, 0f, 3f, 118.8f, 0f, 3f, 118.8f, 8f, 0f, 121.65f, 9f, 2f, 121.65f, 19f, 2f, 118.8f, 20f, 0f, 115.3f, 22f, 1f, 115.3f, 60f, 1f,
                108.3f, 64f, 1f, 108.3f, 84f, 1f, 13.3f, 84f, 1f, 13.3f, 64f, 1f, 6.3f, 60f, 1f, 6.3f, 22f, 1f, 2.8f, 20f, 0f, 0f, 19f, 2f, 0f, 9f, 2f, 2.8f, 8f, 0f),
            lab(23.8f, 26f, 74f, 34f), colours = listOf(c("Black", BLACK)),
            extras = listOf(ShellExtra(ShellExtra.FLAP, 4f, 1f, 113.6f, 19f, 0xFF2A2A2D.toInt())), check = CaseCheck.ESTIMATED,
            note = "84 tall, 121.65 at the hinges, 22 thick at the cover measured; the hinged lock-on cover's height estimated."),
        CartShell("codemasters", "Codemasters Mega Drive cartridge", ShellGroup.SPECIAL, 98f, 75f, 17f,
            o(16.5f, 0f, 4f, 81.5f, 0f, 4f, 98f, 58f, 2f, 98f, 75f, 1f, 0f, 75f, 1f, 0f, 58f, 2f), lab(21f, 14f, 56f, 46f),
            colours = listOf(c("Black", BLACK)), check = CaseCheck.ESTIMATED, note = "98 wide tapering to 65, 75 tall, 17 thick measured; where the taper starts estimated."),
        CartShell("jcart", "Codemasters J-Cart", ShellGroup.SPECIAL, 98f, 75f, 17f,
            o(16.5f, 0f, 4f, 81.5f, 0f, 4f, 98f, 58f, 2f, 98f, 75f, 1f, 0f, 75f, 1f, 0f, 58f, 2f), lab(21f, 18f, 56f, 42f),
            colours = listOf(c("Black", BLACK)),
            extras = listOf(ShellExtra(ShellExtra.SLOT, 18f, 2f, 28f, 10f), ShellExtra(ShellExtra.SLOT, 52f, 2f, 28f, 10f)),
            check = CaseCheck.ESTIMATED, note = "Codemasters shell plus two controller ports on top (their size estimated; drawn at the top of the front)."),
        CartShell("game_gear", "Game Gear cartridge", ShellGroup.SEGA, 66f, 68f, 10f,
            o(0f, 0f, 3f, 66f, 0f, 3f, 66f, 54f, 1f, 61f, 56f, 1f, 61f, 68f, 1f, 5f, 68f, 1f, 5f, 56f, 1f, 0f, 54f, 1f), lab(5.5f, 5f, 55f, 40f),
            screws = listOf(sc(33f, 30f)), colours = listOf(c("Black", BLACK)), check = CaseCheck.ESTIMATED,
            note = "68×66×10 and a ~55×40 label measured (one collector); shoulders estimated."),
        // ── Atari ──
        CartShell("atari2600", "Atari 2600 cartridge", ShellGroup.ATARI, 82f, 98f, 20f, rect(82f, 98f, 4f, 3f), lab(6f, 6f, 70f, 58f),
            listOf(rid(8f, 70f, 66f, 20f, 6)), colours = listOf(c("Black", BLACK)), check = CaseCheck.ESTIMATED,
            note = "98×82×20 measured (Atari / Parker Bros); label and grips estimated."),
        CartShell("atari2600_activision", "Atari 2600 cartridge (Activision)", ShellGroup.ATARI, 81f, 97f, 20f, rect(81f, 97f, 2f, 2f), lab(4f, 4f, 73f, 80f),
            colours = listOf(c("Black", BLACK)), check = CaseCheck.ESTIMATED, note = "97×81×20 measured; label estimated."),
        CartShell("atari5200", "Atari 5200 cartridge", ShellGroup.ATARI, 104f, 112f, 20f, rect(104f, 112f, 4f, 3f), lab(8f, 8f, 88f, 70f),
            colours = listOf(c("Black", BLACK)), check = CaseCheck.ESTIMATED, note = "112×104×20 measured (one collector); the rest estimated."),
        CartShell("atari7800", "Atari 7800 cartridge", ShellGroup.ATARI, 81f, 87f, 19f, rect(81f, 87f, 4f, 3f), lab(6f, 6f, 69f, 60f),
            colours = listOf(c("Black", BLACK)), check = CaseCheck.ESTIMATED, note = "87×81×19 measured; the rest estimated."),
        CartShell("lynx", "Atari Lynx game card", ShellGroup.ATARI, 58f, 61f, 2.4f, rect(58f, 61f, 4f, 1.5f), lab(3f, 8f, 52f, 46f),
            colours = listOf(c("Dark grey", 0xFF2A2A2A)), contactsBack = floatArrayOf(6f, 55f, 46f, 5f), check = CaseCheck.ESTIMATED,
            note = "61×58 measured, 2.4 thick (early cards' curved lip not drawn)."),
        CartShell("jaguar", "Atari Jaguar cartridge", ShellGroup.ATARI, 108f, 88.4f, 19.5f, rect(108f, 88.4f, 6f, 2f), lab(9f, 14f, 90f, 60f),
            listOf(rid(10f, 2f, 88f, 8f, 3)), colours = listOf(c("Black", BLACK)), check = CaseCheck.ESTIMATED,
            note = "88.4×108×19.5 measured (with its handle); the handle and label estimated."),
        // ── NEC, SNK, Bandai ──
        CartShell("hucard", "PC Engine HuCard / TurboChip", ShellGroup.NEC_SNK, 53f, 84f, 2f, rect(53f, 84f, 3f, 3f), lab(2f, 12f, 49f, 70f),
            colours = listOf(c("Black", 0xFF1E1E1E)), contactsFront = floatArrayOf(5f, 1.5f, 43f, 8f), check = CaseCheck.ESTIMATED,
            note = "84×53×2 measured; the contact strip's size estimated."),
        CartShell("neogeo_aes", "Neo Geo AES cartridge", ShellGroup.NEC_SNK, 190.5f, 146f, 31.75f, rect(190.5f, 146f, 6f, 3f), lab(12f, 12f, 166f, 118f),
            listOf(rid(20f, 2f, 150f, 8f, 6)), listOf(sc(20f, 20f), sc(170f, 20f), sc(20f, 126f), sc(170f, 126f)),
            listOf(c("Black", 0xFF141414)), check = CaseCheck.ESTIMATED, note = "146×190.5×31.75 measured; label, grips and screws estimated."),
        CartShell("neogeo_mvs", "Neo Geo MVS cartridge", ShellGroup.NEC_SNK, 185f, 135f, 26f, rect(185f, 135f, 8f, 3f), lab(15f, 15f, 155f, 100f),
            screws = listOf(sc(12f, 12f), sc(173f, 12f), sc(12f, 123f), sc(173f, 123f)), colours = listOf(c("Black", 0xFF161616)),
            check = CaseCheck.PLACEHOLDER, note = "No measurements found; size estimated from replica shells' bolts."),
        CartShell("ngp", "Neo Geo Pocket cartridge", ShellGroup.NEC_SNK, 47f, 40f, 6.5f, rect(47f, 40f, 3f, 1f), lab(4f, 8f, 39f, 28f),
            listOf(rid(6f, 1.5f, 35f, 4f, 4)), listOf(sc(23.5f, 20f)), listOf(c("Black", 0xFF181818), c("Clear", 0xFFD8DCE0, 0.55f)),
            check = CaseCheck.PLACEHOLDER, note = "No measurements found; estimated."),
        CartShell("wonderswan", "WonderSwan cartridge", ShellGroup.NEC_SNK, 52f, 45f, 7f, rect(52f, 45f, 2f, 1f), lab(4f, 6f, 44f, 34f),
            colours = listOf(c("Black", 0xFF1A1A1A), c("Clear", 0xFFD0D4DA, 0.55f)), check = CaseCheck.PLACEHOLDER, note = "No measurements found; estimated."),
        // ── Other ──
        CartShell("colecovision", "ColecoVision cartridge", ShellGroup.OTHER, 81f, 87f, 19f, rect(81f, 87f, 2f, 1f), lab(5f, 5f, 71f, 50f),
            listOf(rid(8f, 62f, 65f, 18f, 6)), listOf(sc(40.5f, 20f)), listOf(c("Black", 0xFF151515)), check = CaseCheck.ESTIMATED,
            note = "87×81×19 measured; label, grips and screw estimated."),
        CartShell("intellivision", "Intellivision cartridge", ShellGroup.OTHER, 68f, 88f, 16f,
            o(0f, 0f, 2f, 68f, 0f, 2f, 68f, 60f, 0f, 64f, 66f, 0f, 64f, 88f, 1f, 4f, 88f, 1f, 4f, 66f, 0f, 0f, 60f, 0f), lab(5f, 4f, 58f, 52f),
            colours = listOf(c("Black", 0xFF141414)), check = CaseCheck.ESTIMATED, note = "88×68×16 measured; the narrowing and label estimated."),
        CartShell("vectrex", "Vectrex cartridge", ShellGroup.OTHER, 74.5f, 71.5f, 22.8f, rect(74.5f, 71.5f, 3f, 1f), lab(6f, 6f, 62.5f, 45f),
            listOf(rid(10f, 55f, 54.5f, 12f, 5)), colours = listOf(c("Black", 0xFF151515)), check = CaseCheck.ESTIMATED, note = "71.5×74.5×22.8 measured; the rest estimated."),
        CartShell("odyssey2", "Odyssey² / Videopac cartridge", ShellGroup.OTHER, 80f, 127f, 21f,
            o(0f, 0f, 4f, 80f, 0f, 4f, 80f, 32f, 2f, 72f, 36f, 0f, 72f, 127f, 1f, 8f, 127f, 1f, 8f, 36f, 0f, 0f, 32f, 2f), lab(12f, 44f, 56f, 70f),
            listOf(rid(6f, 6f, 68f, 20f, 7)), colours = listOf(c("Black", 0xFF141414)), check = CaseCheck.ESTIMATED,
            note = "127×80×21 measured; the handle's proportions estimated."),
        CartShell("channelf", "Channel F Videocart", ShellGroup.OTHER, 102f, 133f, 22f, rect(102f, 133f, 5f, 2f), lab(8f, 8f, 86f, 60f),
            colours = listOf(c("Yellow", 0xFFE8B92A)), check = CaseCheck.PLACEHOLDER, note = "8-track-like size from the box (no cart measurement)."),
        CartShell("gx4000", "Amstrad GX4000 cartridge", ShellGroup.OTHER, 100f, 72f, 14f, rect(100f, 72f, 4f, 1f), lab(8f, 6f, 84f, 44f),
            colours = listOf(c("Dark grey", 0xFF2A2A2C)), check = CaseCheck.PLACEHOLDER, note = "No measurements found."),
        CartShell("scv", "Super Cassette Vision cartridge", ShellGroup.OTHER, 100f, 80f, 16f, rect(100f, 80f, 3f, 1f), lab(6f, 6f, 88f, 55f),
            colours = listOf(c("Dark grey", 0xFF3A3A3A)), check = CaseCheck.PLACEHOLDER, note = "No measurements found."),
        CartShell("pv1000", "Casio PV-1000 cartridge", ShellGroup.OTHER, 90f, 80f, 16f, rect(90f, 80f, 3f, 1f), lab(6f, 6f, 78f, 55f),
            colours = listOf(c("Dark grey", 0xFF3A3A3A)), check = CaseCheck.PLACEHOLDER, note = "No measurements found."),
        CartShell("loopy", "Casio Loopy cartridge", ShellGroup.OTHER, 110f, 75f, 18f, rect(110f, 75f, 5f, 2f), lab(8f, 6f, 94f, 50f),
            colours = listOf(c("Light", 0xFFE8E4EC)), check = CaseCheck.PLACEHOLDER, note = "No measurements found."),
        CartShell("supervision", "Watara Supervision cartridge", ShellGroup.OTHER, 52f, 60f, 8f, rect(52f, 60f, 2f, 1f), lab(4f, 6f, 44f, 44f),
            colours = listOf(c("Black", 0xFF1A1A1A)), check = CaseCheck.PLACEHOLDER, note = "No measurements found."),
        CartShell("megaduck", "Mega Duck / Cougar Boy cartridge", ShellGroup.OTHER, 62f, 56f, 8f, rect(62f, 56f, 2f, 1f), lab(4f, 6f, 54f, 40f),
            colours = listOf(c("Black", 0xFF1A1A1A)), check = CaseCheck.PLACEHOLDER, note = "No measurements found."),
        CartShell("microvision", "Microvision game module", ShellGroup.OTHER, 95f, 140f, 12f, rect(95f, 140f, 6f, 3f), lab(10f, 98f, 75f, 36f),
            colours = listOf(c("Dark grey", 0xFF2B2B2B)),
            extras = listOf(ShellExtra(ShellExtra.WINDOW, 31f, 15f, 33f, 33f, 0xFF7E8A6A.toInt()), ShellExtra(ShellExtra.KEYPAD, 22f, 56f, 51f, 38f, 0xFFC0392B.toInt())),
            check = CaseCheck.PLACEHOLDER, note = "The module is the console's whole faceplate (LCD window, keypad); all sizes estimated."),
        CartShell("gamecom", "Tiger game.com cartridge", ShellGroup.OTHER, 38f, 38f, 12.7f, rect(38f, 38f, 2f, 1f), lab(3f, 6f, 32f, 28f),
            colours = listOf(c("Black", 0xFF1A1A1A)), check = CaseCheck.ESTIMATED, note = "1.5×1.5×0.5 in from a museum (rounded); the rest estimated."),
        CartShell("ngage", "N-Gage game card (MMC)", ShellGroup.OTHER, 24f, 32f, 1.4f, o(0f, 0f, 0.5f, 20f, 0f, 0f, 24f, 4f, 0f, 24f, 32f, 0.5f, 0f, 32f, 0.5f),
            lab(1f, 5f, 22f, 25f), colours = listOf(c("Dark grey", 0xFF2A2A2A)), contactsBack = floatArrayOf(1f, 0.5f, 19f, 4f),
            check = CaseCheck.SOURCED, note = "MultiMediaCard standard size (24×32×1.4) with its cut corner."),
        CartShell("evercade", "Evercade cartridge", ShellGroup.OTHER, 60f, 88f, 11f, rect(60f, 88f, 4f, 1.5f), lab(4f, 6f, 52f, 66f),
            colours = listOf(c("Red", 0xFFC8202B), c("Purple", 0xFF5B2D8E)), check = CaseCheck.PLACEHOLDER, note = "No measurements found; estimated."),
        // ── Special Nintendo carts ──
        CartShell("gb_camera", "Game Boy Camera", ShellGroup.SPECIAL, 57f, 110f, 38f,
            o(0f, 45f, 2f, 13f, 45f, 0f, 13f, 0f, 15f, 44f, 0f, 15f, 44f, 45f, 0f, 57f, 45f, 2f, 57f, 110f, 1f, 0f, 110f, 1f), lab(5f, 52f, 47f, 30f),
            colours = listOf(c("Blue", 0xFF2F5DB0), c("Red", 0xFFC8282F), c("Yellow", 0xFFE8C22A), c("Green", 0xFF3E9A4A)),
            extras = listOf(ShellExtra(ShellExtra.LENS, 20f, 8f, 17f, 17f)), check = CaseCheck.PLACEHOLDER,
            note = "A Game Boy cart with the round camera head on top; the head's size and the colours from memory."),
        CartShell("pokemon_pinball", "Pokémon Pinball (rumble)", ShellGroup.SPECIAL, 57f, 84f, 18f, rect(57f, 84f, 4f, 1f), lab(5f, 28f, 47f, 40f),
            colours = listOf(c("Black", 0xFF1A1A1A)), extras = listOf(ShellExtra(ShellExtra.ACCENT, 3f, 3f, 51f, 16f, 0xFF2A2A2E.toInt())),
            check = CaseCheck.PLACEHOLDER, note = "A Game Boy Color cart with the rumble and battery housing on top (estimated)."),
        CartShell("kirby_tilt", "Kirby Tilt 'n' Tumble", ShellGroup.SPECIAL, 57f, 78f, 14f, rect(57f, 78f, 4f, 1f), lab(5f, 20f, 47f, 40f),
            colours = listOf(c("Clear pink", 0xFFF29AC0, 0.6f)), check = CaseCheck.PLACEHOLDER, note = "A clear Game Boy Color cart with the tilt sensor on top (estimated)."),
        CartShell("warioware_twisted", "WarioWare: Twisted!", ShellGroup.SPECIAL, 57.5f, 48f, 12f, rect(57.5f, 48f, 3f, 1f), lab(4f, 16f, 49.5f, 26f),
            colours = listOf(c("Transparent grey", 0xFF9A9DA4, 0.55f), c("Translucent white (Japan)", 0xFFE0E9EB, 0.65f)), check = CaseCheck.PLACEHOLDER,
            note = "A taller Game Boy Advance cart with gyro and rumble (AGB-019A transparent grey; Japan AGB-019 translucent white — Game Verifying; shape estimated)."),
        CartShell("boktai", "Boktai (solar sensor)", ShellGroup.SPECIAL, 57.5f, 50f, 9f, rect(57.5f, 50f, 3f, 1f), lab(4f, 18f, 49.5f, 26f),
            colours = listOf(c("Clear", 0xFFE6EAEE, 0.5f)), extras = listOf(ShellExtra(ShellExtra.WINDOW, 23.75f, 4f, 10f, 6f, 0xFF1E3A6A.toInt())),
            check = CaseCheck.PLACEHOLDER, note = "A clear, slightly taller Game Boy Advance cart with the solar window on top (estimated)."),
        CartShell("drill_dozer", "Drill Dozer (rumble)", ShellGroup.SPECIAL, 57.5f, 48f, 11f, rect(57.5f, 48f, 3f, 1f), lab(4f, 16f, 49.5f, 26f),
            colours = listOf(c("Brown", 0xFF795440)), check = CaseCheck.PLACEHOLDER, note = "A taller Game Boy Advance cart with rumble (AGB-021, brown — Game Verifying; shape estimated)."),
        CartShell("yoshi_topsy", "Yoshi Topsy-Turvy (tilt)", ShellGroup.SPECIAL, 57.5f, 48f, 11f, rect(57.5f, 48f, 3f, 1f), lab(4f, 16f, 49.5f, 26f),
            colours = listOf(c("Green", 0xFF4FA84A)), check = CaseCheck.PLACEHOLDER,
            note = "A taller Game Boy Advance cart with a tilt sensor (AGB-013, green and white — Game Verifying; shape estimated)."),
        CartShell("ereader", "Nintendo e-Reader", ShellGroup.SPECIAL, 120f, 72f, 26f,
            o(0f, 0f, 6f, 120f, 0f, 6f, 120f, 40f, 4f, 89f, 40f, 0f, 89f, 72f, 1f, 31f, 72f, 1f, 31f, 40f, 0f, 0f, 40f, 4f), lab(10f, 6f, 100f, 20f),
            colours = listOf(c("Grey", 0xFF8E9096)), extras = listOf(ShellExtra(ShellExtra.SLOT, 6f, 30f, 108f, 2.5f)), check = CaseCheck.PLACEHOLDER,
            note = "The head with its card slot over a Game Boy Advance plug (estimated)."),
        CartShell("np_sfc", "Nintendo Power SF Memory", ShellGroup.SPECIAL, 127f, 86f, 20f, rect(127f, 86f, 12f, 2f), lab(12f, 8f, 103f, 52f),
            colours = listOf(c("Grey", 0xFFA9A8AD)), check = CaseCheck.ESTIMATED, note = "The Super Famicom shell; its own colour unconfirmed."),
        CartShell("np_gb", "Nintendo Power GB Memory", ShellGroup.SPECIAL, 57f, 65.5f, 9f, rect(57f, 65.5f, 2f, 1f), lab(5f, 8f, 47f, 42f),
            colours = listOf(c("Dark grey", 0xFF3C3C42)), check = CaseCheck.PLACEHOLDER, note = "Assumed the Game Boy Color size; colour unconfirmed.")
    )

    private val byId: Map<String, CartShell> = all.associateBy { it.id }
    fun of(id: String?): CartShell? = id?.let { byId[it] }

    /** Colours any shell can take (besides its own). */
    val presets: List<ShellColour> = listOf(
        ShellColour("black", "Black", 0xFF1C1C1E.toInt(), 1f), ShellColour("grey", "Grey", 0xFF9A9AA0.toInt(), 1f),
        ShellColour("white", "White", 0xFFF2F2F2.toInt(), 1f), ShellColour("gold", "Gold", 0xFFC9A548.toInt(), 1f),
        ShellColour("clear", "Clear", 0xFFE6E8EA.toInt(), 0.4f), ShellColour("smoke", "Smoke", 0xFF3A3A40.toInt(), 0.6f),
        ShellColour("red", "Clear red", 0xFFC0202A.toInt(), 0.65f), ShellColour("blue", "Clear blue", 0xFF2850B0.toInt(), 0.65f),
        ShellColour("green", "Clear green", 0xFF2E9A48.toInt(), 0.65f), ShellColour("purple", "Clear purple", 0xFF7A3FB0.toInt(), 0.6f),
        ShellColour("yellow", "Clear yellow", 0xFFE8C51E.toInt(), 0.65f)
    )

    fun colourOf(shell: CartShell, key: String?): ShellColour =
        (if (key.isNullOrEmpty()) null else shell.colours.firstOrNull { it.key == key } ?: presets.firstOrNull { it.key == key }
            // (Update 210: a material from the registry — "m:<id>", ShellMaterials.)
            ?: ShellMaterials.colourFor(key)) ?: shell.colours.first()

    /** Each console's own shell (by region where they differ): what "Authentic drawn cartridges" draws. */
    /**
     * Update 200: [shellId] is a shell of [console]'s own (any region's, or one of its special cartridges) — its back then
     * shows the maker's markings by default.
     */
    fun belongsTo(shellId: String, console: String): Boolean {
        if (CaseRegion.values().any { authenticId(console, it) == shellId }) return true
        val also = when (shellId) {
            "gbc_dual", "np_gb" -> setOf("GB", "GBC")
            "gb_camera" -> setOf("GB")
            "pokemon_pinball", "kirby_tilt" -> setOf("GBC")
            "warioware_twisted", "boktai", "drill_dozer", "yoshi_topsy", "ereader" -> setOf("GBA")
            "genesis_ea", "virtua_racing", "sonic_knuckles", "codemasters", "jcart", "genesis", "megadrive_jp" -> setOf("Genesis", "MegaDrive")
            "np_sfc", "sfc", "snes_na" -> setOf("SNES", "SFC")
            "nes", "famicom" -> setOf("NES", "Famicom")
            "sms", "sms_jp", "sega_card" -> setOf("MasterSystem")
            "switch2_key" -> setOf("Switch2")
            else -> emptySet()
        }
        return console in also
    }

    /**
     * Update 205: [authenticId] for one game ([gameKey], its file): a Game Boy Color game that also runs on the Game Boy
     * ("(GB Compatible)" in its name) came on the black dual-mode Game Pak — only the Color-only games were clear. Every
     * Color game was drawn clear, so most of them looked like a custom see-through piece.
     */
    fun authenticIdFor(platform: String, region: CaseRegion, gameKey: String?): String? {
        val id = authenticId(platform, region) ?: return null
        return if (id == "gbc" && gameKey != null && gameKey.contains("GB Compatible", ignoreCase = true)) "gbc_dual" else id
    }

    fun authenticId(platform: String, region: CaseRegion): String? = when (platform) {
        "NES" -> if (region == CaseRegion.JP) "famicom" else "nes"
        "Famicom" -> "famicom"
        "FDS" -> "fds"
        "SNES" -> if (region == CaseRegion.US) "snes_na" else "sfc"
        "SFC" -> "sfc"
        "N64" -> "n64"
        "GB" -> "gb"
        "GBC" -> "gbc"
        "GBA" -> "gba"
        "VirtualBoy" -> "vb"
        "NDS" -> "ds"
        "3DS" -> "3ds"
        "Switch" -> "switch"
        "Switch2" -> "switch2"
        "PokemonMini" -> "pokemini"
        "Genesis", "MegaDrive" -> if (region == CaseRegion.JP) "megadrive_jp" else "genesis"
        "MasterSystem" -> if (region == CaseRegion.JP) "sms_jp" else "sms"
        "SG1000" -> "sg1000"
        "32X" -> "32x"
        "GameGear" -> "game_gear"
        "Atari2600" -> "atari2600"
        "Atari5200" -> "atari5200"
        "Atari7800" -> "atari7800"
        "Lynx" -> "lynx"
        "Jaguar" -> "jaguar"
        "PCEngine", "TG16" -> "hucard"
        "NeoGeo" -> "neogeo_aes"
        "NeoGeoPocket", "NeoGeoPocketColor" -> "ngp"
        "WonderSwan", "WonderSwanColor" -> "wonderswan"
        "ColecoVision" -> "colecovision"
        "Intellivision" -> "intellivision"
        "Vectrex" -> "vectrex"
        "Odyssey2", "Videopac" -> "odyssey2"
        "ChannelF" -> "channelf"
        "GX4000" -> "gx4000"
        "SCV" -> "scv"
        "Supervision" -> "supervision"
        "MegaDuck" -> "megaduck"
        "GameCom" -> "gamecom"
        "NGage" -> "ngage"
        "Evercade" -> "evercade"
        else -> null
    }

    // ---- Choices: "<shell>~<colour>" or "pack:<look key>~<clear|opaque>" ----

    /**
     * [model]: Update 200 — a piece of the retired 3D shape library ("m3d:<id>", saved by update 197's "Your 3D pieces"),
     * kept only so [retired] can put the game on its own console's drawn shell instead.
     */
    class Choice(val shell: CartShell?, val pack: CartLook?, val colourKey: String, val raw: String, val model: String? = null) {
        val aspect: Float get() = shell?.aspect ?: pack?.aspect ?: 1f
        val clear: Boolean get() = colourKey == "clear"
    }

    fun parse(raw: String?): Choice? {
        if (raw.isNullOrEmpty()) return null
        val id = raw.substringBefore('~')
        val colour = raw.substringAfter('~', "")
        return if (id.startsWith("pack:")) CartLook.values().firstOrNull { it.key == id.removePrefix("pack:") }?.let { Choice(null, it, colour, raw) }
        // (Update 200: no longer checked against the 3D pack's list — the piece itself isn't drawn any more, see [retired].)
        else if (id.startsWith("m3d:")) Choice(null, null, colour, raw, id.removePrefix("m3d:"))
        else of(id)?.let { Choice(it, null, colour, raw) }
    }

    /**
     * Update 200: the pre-rendered 3D pieces are retired (they turned in 20° steps, like a slide show, and the drawn shells
     * look better). A saved "m3d:<id>" choice becomes the game's own console's authentic drawn shell — in clear plastic
     * when the piece was a clear one — so nothing disappears; null (its usual look) for a console without a shell.
     */
    private fun retired(ch: Choice, platform: String?, region: CaseRegion): Choice? {
        val m = ch.model ?: return ch
        val id = platform?.let { authenticId(it, region) } ?: return null
        val colour = if (m.endsWith("_translucent")) "clear" else ch.colourKey
        return parse("$id~$colour")
    }

    /** The piece for a choice on [platform]'s game ([gameKey]: its region, for a retired 3D piece's authentic shell). */
    fun pickOf(raw: String, platform: String, gameKey: String? = null): MediaPick? =
        parse(raw)?.let { retired(it, platform, CaseCatalog.regionOf(gameKey ?: "")) }?.let { ch ->
            // (Update 200: [Choice.raw] — a retired 3D piece's is its authentic shell's.)
            MediaPick(MediaShell.GB, "$SHELL_PREFIX${ch.raw}|$platform", ch.aspect)
        }

    /** "~sh:<choice>|<console>" → its choice and console. */
    fun split(pickPlatform: String): Pair<Choice?, String?> {
        val rest = pickPlatform.removePrefix(SHELL_PREFIX)
        val console = rest.substringAfter('|', "").ifEmpty { null }
        // (Update 200: a retired 3D piece as its console's authentic shell, here too.)
        return parse(rest.substringBefore('|'))?.let { retired(it, console, CaseCatalog.regionOf("")) } to console
    }

    // ---- Drawing ----

    /** Update 208: the console whose box covers go with each shell (for [labelArt]). */
    private val COVER_OF = mapOf(
        "ds" to "NDS", "3ds" to "3DS", "gb" to "GB", "gbc" to "GBC", "gbc_dual" to "GBC", "gba" to "GBA", "n64" to "N64",
        "snes_na" to "SNES", "sfc" to "SNES", "nes" to "NES", "genesis" to "Genesis", "switch" to "Switch", "switch2" to "Switch2",
        "vb" to "VirtualBoy", "game_gear" to "GameGear", "sms" to "MasterSystem"
    )

    /**
     * Update 208: [art] (a game's cover, used when it has no label scan) for [s]'s sticker: a scan of the whole box front —
     * its shape within 6 % of the console's box template — is cut to the template's art window, leaving the box's printed
     * console banner (the DS / Game Boy strip down its side, the N64 band) off the cartridge. Other pictures as they are.
     */
    /**
     * Update 215: the average colour along [b]'s top and bottom edges ([topBottom]) or its left and right edges — what
     * fills the label round a cover fitted inside it. The label's paper colour when the pixels can't be read.
     */
    private fun edgeColour(b: Bitmap, topBottom: Boolean): Int = runCatching {
        val soft = if (b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b
        var r = 0L; var g = 0L; var bl = 0L; var n = 0
        val steps = 24
        for (i in 0 until steps) {
            val f = (i + 0.5f) / steps
            val pts = if (topBottom) listOf((f * (soft.width - 1)).toInt() to 1, (f * (soft.width - 1)).toInt() to soft.height - 2)
                else listOf(1 to (f * (soft.height - 1)).toInt(), soft.width - 2 to (f * (soft.height - 1)).toInt())
            for ((x, y) in pts) {
                val px = soft.getPixel(x.coerceIn(0, soft.width - 1), y.coerceIn(0, soft.height - 1))
                if ((px ushr 24) < 200) continue
                r += (px shr 16) and 0xFF; g += (px shr 8) and 0xFF; bl += px and 0xFF; n++
            }
        }
        if (n == 0) 0xFFE9E6DF.toInt() else (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt()
    }.getOrDefault(0xFFE9E6DF.toInt())

    private fun labelArt(s: CartShell, art: Bitmap): Bitmap = runCatching {
        val tpl = MediaCovers.of(COVER_OF[s.id] ?: return art) ?: return art
        val a = art.width.toFloat() / art.height.coerceAtLeast(1)
        if (kotlin.math.abs(a / tpl.frontAspect - 1f) > 0.06f) return art
        val w = tpl.win
        val l = (w[0] * art.width).toInt(); val t = (w[1] * art.height).toInt()
        val r = (w[2] * art.width).toInt(); val b = (w[3] * art.height).toInt()
        if (r - l < 8 || b - t < 8 || (l == 0 && t == 0 && r >= art.width && b >= art.height)) art
        else Bitmap.createBitmap(art, l, t, r - l, b - t)
    }.getOrDefault(art)

    /**
     * Update 239 (#34): [art] (a [platform] game's cover) as the sticker in your pack's cartridge / disc-console shell window,
     * [winAspect] wide for 1 tall: a whole box-front scan has its printed console banner cut off, as [labelArt] does for the
     * drawn shells (a PS2 cover's black "PlayStation 2" header sat under the pack's own PS2 plaque), then the cover keeps
     * its own shape, as big as fits, and the rest of the window is the colour along the cover's edge — one printed
     * sticker, never stretched. (The disc-console shells cut a portrait PS2 cover to their near-square window, losing its
     * top and bottom — the title among them; your cartridge pack left bare label paper round it.) Off the main thread
     * (a Coil transformation, [PackSticker]).
     */
    internal fun packSticker(platform: String, art: Bitmap, winAspect: Float): Bitmap = runCatching {
        val tpl = MediaCovers.of(platform)?.takeIf { !it.isOwn() }
        val a = art.width.toFloat() / art.height.coerceAtLeast(1)
        val pic = if (tpl != null && kotlin.math.abs(a / tpl.frontAspect - 1f) <= 0.06f) {
            val w = tpl.win
            val l = (w[0] * art.width).toInt(); val t = (w[1] * art.height).toInt()
            val r = (w[2] * art.width).toInt().coerceAtMost(art.width); val b = (w[3] * art.height).toInt().coerceAtMost(art.height)
            if (r - l < 8 || b - t < 8 || (l == 0 && t == 0 && r >= art.width && b >= art.height)) art
            else Bitmap.createBitmap(art, l, t, r - l, b - t)
        } else art
        val pa = pic.width.toFloat() / pic.height.coerceAtLeast(1)
        if (winAspect <= 0f || kotlin.math.abs(pa / winAspect - 1f) <= 0.02f) pic
        else {
            val wide = pa > winAspect
            val ow = (if (wide) pic.width else (pic.height * winAspect).toInt()).coerceAtLeast(1)
            val oh = (if (wide) (pic.width / winAspect).toInt() else pic.height).coerceAtLeast(1)
            val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            c.drawColor(edgeColour(pic, wide))
            val dst = if (wide) RectF(0f, (oh - pic.height) / 2f, ow.toFloat(), (oh + pic.height) / 2f)
                else RectF((ow - pic.width) / 2f, 0f, (ow + pic.width) / 2f, oh.toFloat())
            c.drawBitmap(pic, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            out
        }
    }.getOrDefault(art)

    private val cache = android.util.LruCache<String, Bitmap>(24)

    /**
     * Update 239 (#40): [s]'s label outline over [r] (px, [k] px per mm): [CartShell.labelRadius] at the top corners,
     * [CartShell.labelRadiusBottom] at the bottom ones, and the top edge arched on [CartShell.labelTopArc]'s circle (its
     * middle at [r]'s top, its ends lower) — the N64 sticker's shape. A plain round rectangle for every other label.
     */
    private fun labelPath(s: CartShell, r: RectF, k: Float): Path {
        val rt = s.labelRadius * k
        val rb = (if (s.labelRadiusBottom >= 0f) s.labelRadiusBottom else s.labelRadius) * k
        val p = Path()
        val arcR = s.labelTopArc * k / 2f
        val half = r.width() / 2f
        if (arcR <= half + 0.5f) {
            // A straight top (or no arc that spans the label).
            if (rt == rb) p.addRoundRect(r, rt, rt, Path.Direction.CW)
            else p.addRoundRect(r, floatArrayOf(rt, rt, rt, rt, rb, rb, rb, rb), Path.Direction.CW)
            return p
        }
        val cx = r.centerX()
        fun arcY(x: Float): Float { val dx = x - cx; return r.top + arcR - kotlin.math.sqrt((arcR * arcR - dx * dx).coerceAtLeast(0f)) }
        val ends = arcY(r.left)
        val rtc = minOf(rt, (r.bottom - ends) / 2f, half)
        val rbc = minOf(rb, (r.bottom - ends) / 2f, half)
        p.moveTo(r.left, ends + rtc)
        p.quadTo(r.left, ends, r.left + rtc, arcY(r.left + rtc))
        val n = 24
        for (i in 1..n) {
            val x = r.left + rtc + (r.width() - 2f * rtc) * i / n
            p.lineTo(x, arcY(x))
        }
        p.quadTo(r.right, ends, r.right, ends + rtc)
        p.lineTo(r.right, r.bottom - rbc)
        p.quadTo(r.right, r.bottom, r.right - rbc, r.bottom)
        p.lineTo(r.left + rbc, r.bottom)
        p.quadTo(r.left, r.bottom, r.left, r.bottom - rbc)
        p.close()
        return p
    }

    private fun outlinePath(s: CartShell, k: Float): Path {
        val p = Path()
        val v = s.outline
        val n = v.size / 3
        for (i in 0 until n) {
            val x = v[i * 3] * k; val y = v[i * 3 + 1] * k; val r0 = v[i * 3 + 2] * k
            val pi = (i - 1 + n) % n; val ni = (i + 1) % n
            val px = v[pi * 3] * k; val py = v[pi * 3 + 1] * k
            val nx = v[ni * 3] * k; val ny = v[ni * 3 + 1] * k
            val dp = kotlin.math.hypot(px - x, py - y).coerceAtLeast(0.001f)
            val dn = kotlin.math.hypot(nx - x, ny - y).coerceAtLeast(0.001f)
            val r = minOf(r0, dp / 2f, dn / 2f)
            val ax = x + (px - x) / dp * r; val ay = y + (py - y) / dp * r
            val bx = x + (nx - x) / dn * r; val by = y + (ny - y) / dn * r
            if (i == 0) p.moveTo(ax, ay) else p.lineTo(ax, ay)
            p.quadTo(x, y, bx, by)
        }
        p.close()
        return p
    }

    /**
     * Update 210: a material's finish over the plastic (clipped to the shell already): a metallic / pearlescent sheen
     * across it in its highlight colour, and fine glitter speckles (the same pattern every time for a shell).
     */
    private fun finish(c: Canvas, s: CartShell, colour: ShellColour, k: Float, bw: Int, bh: Int, p: Paint, front: Boolean) {
        val base = if (!front && colour.backArgb != 0) colour.backArgb else colour.argb
        val hi = if (colour.highlight != 0) colour.highlight else shade(base, 0.55f)
        if (colour.metallic > 0f) {
            val m = colour.metallic.coerceIn(0f, 1f)
            p.shader = LinearGradient(
                0f, 0f, bw.toFloat(), bh.toFloat(),
                intArrayOf(withAlpha(hi, 0f), withAlpha(hi, 0.5f * m), withAlpha(hi, 0f), withAlpha(hi, 0.28f * m), withAlpha(hi, 0f)),
                floatArrayOf(0f, 0.28f, 0.48f, 0.7f, 1f), Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), p)
            p.shader = null
        }
        if (colour.glitter > 0f) {
            val rnd = java.util.Random((s.id.hashCode() * 31 + if (front) 1 else 2).toLong())
            val n = (s.w * s.h * colour.glitter * 0.9f).toInt().coerceIn(0, 4000)
            for (i in 0 until n) {
                val x = rnd.nextFloat() * bw; val y = rnd.nextFloat() * bh
                val r = ((0.1f + rnd.nextFloat() * 0.2f) * k).coerceAtLeast(0.6f)
                p.color = withAlpha(if (rnd.nextInt(6) == 0) 0xFFFFFFFF.toInt() else hi, 0.3f + rnd.nextFloat() * 0.6f)
                c.drawCircle(x, y, r, p)
            }
        }
    }

    private fun shade(argb: Int, k: Float): Int = CaseInside.mix(argb, if (k >= 0f) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(), kotlin.math.abs(k))
    private fun withAlpha(argb: Int, a: Float): Int = (argb and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255f).toInt() shl 24)

    /**
     * The shell's front at [maxPx] on its longest side: its plastic (lit from above), a see-through shell's board
     * inside, its rim, grip ridges, special parts, and the label in its recess — [art] (the game's cover, cut to the
     * label's shape, never stretched) under a band with [brand] (the game's real console).
     */
    fun front(s: CartShell, colour: ShellColour, art: Bitmap?, brand: String, title: String?, maxPx: Int, scan: Boolean = false): Bitmap {
        val key = "f4|${s.id}|${colour.key}|${art?.let { System.identityHashCode(it) } ?: 0}|$brand|${title ?: ""}|$maxPx|$scan"
        cache.get(key)?.let { return it }
        val k = maxPx / maxOf(s.w, s.h)
        val bw = (s.w * k).toInt().coerceAtLeast(8); val bh = (s.h * k).toInt().coerceAtLeast(8)
        val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val path = outlinePath(s, k)
        val base = colour.argb
        val op = colour.opacity
        p.shader = LinearGradient(0f, 0f, 0f, bh.toFloat(), withAlpha(shade(base, 0.08f), op), withAlpha(shade(base, -0.12f), op), Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        p.shader = null
        c.save(); c.clipPath(path)
        // A see-through shell shows its board (green PCB, a chip) inside.
        if (op < 0.85f) {
            val board = RectF(bw * 0.1f, bh * 0.22f, bw * 0.9f, bh * 0.97f)
            p.color = withAlpha(0xFF1E5F3A.toInt(), 0.55f * (1f - op))
            c.drawRect(board, p)
            p.color = withAlpha(0xFF111111.toInt(), 0.6f * (1f - op))
            c.drawRect(RectF(board.left + board.width() * 0.3f, board.top + board.height() * 0.3f, board.left + board.width() * 0.62f, board.top + board.height() * 0.55f), p)
            p.color = withAlpha(0xFFC9A646.toInt(), 0.7f * (1f - op))
            c.drawRect(RectF(board.left + board.width() * 0.08f, board.bottom - bh * 0.05f, board.right - board.width() * 0.08f, board.bottom), p)
        }
        // (Update 210: a metallic-looking plastic's sheen and a glittering one's speckles — ShellMaterials.)
        finish(c, s, colour, k, bw, bh, p, front = true)
        // The rim: the moulded edge, a touch darker, lit along the top.
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.4f * k
        p.color = withAlpha(shade(base, -0.3f), maxOf(op, 0.5f))
        c.drawPath(path, p)
        p.strokeWidth = 0.4f * k
        p.color = withAlpha(shade(base, 0.25f), maxOf(op, 0.5f) * 0.8f)
        c.save(); c.translate(0f, 0.6f * k); c.drawPath(path, p); c.restore()
        p.style = Paint.Style.FILL
        // Grip ridges: a shadow line and a lit line each.
        val rd = s.ridgeDepth.coerceIn(0f, 1f)
        // (Update 215: lines that are only seams — ridgeDepth under 1 — are thinner too.)
        val rw = if (rd < 1f) 0.5f else 1f
        for (r in s.ridges) {
            val n = r[4].toInt().coerceAtLeast(1)
            val across = r[5] > 0.5f
            for (i in 0 until n) {
                val f = (i + 0.5f) / n
                if (across) {
                    val y = (r[1] + r[3] * f) * k
                    p.color = withAlpha(shade(base, -0.35f * rd), maxOf(op, 0.6f)); c.drawRect(r[0] * k, y - 0.3f * rw * k, (r[0] + r[2]) * k, y + 0.15f * rw * k, p)
                    p.color = withAlpha(shade(base, 0.2f * rd), maxOf(op, 0.6f)); c.drawRect(r[0] * k, y + 0.15f * rw * k, (r[0] + r[2]) * k, y + 0.45f * rw * k, p)
                } else {
                    val x = (r[0] + r[2] * f) * k
                    p.color = withAlpha(shade(base, -0.35f * rd), maxOf(op, 0.6f)); c.drawRect(x - 0.3f * rw * k, r[1] * k, x + 0.15f * rw * k, (r[1] + r[3]) * k, p)
                    p.color = withAlpha(shade(base, 0.2f * rd), maxOf(op, 0.6f)); c.drawRect(x + 0.15f * rw * k, r[1] * k, x + 0.45f * rw * k, (r[1] + r[3]) * k, p)
                }
            }
        }
        for (e in s.extras) drawExtra(c, e, k, base, p)
        s.contactsFront?.let { ct -> contacts(c, ct, k, p) }
        // The label in its recess (a shadow round it), the cover cut to its shape, the console's band on top.
        val lr = RectF(s.label[0] * k, s.label[1] * k, (s.label[0] + s.label[2]) * k, (s.label[1] + s.label[3]) * k)
        // (Update 239, #40: the label's own outline — labelPath: its top and bottom corners and an arched top where the
        // real sticker has one, the N64's. Other shells' labels are the same round rectangles as before.)
        p.color = withAlpha(shade(base, -0.4f), maxOf(op, 0.7f))
        c.drawPath(labelPath(s, RectF(lr.left - 0.7f * k, lr.top - 0.7f * k, lr.right + 0.5f * k, lr.bottom + 0.5f * k), k), p)
        c.save()
        c.clipPath(labelPath(s, lr, k))
        p.color = 0xFFE9E6DF.toInt(); c.drawRect(lr, p)
        // (Update 205: a real label scan is the whole printed sticker — no console band drawn over its top.)
        // (Update 208: never a band — a separate console header over the sticker isn't on any real cartridge; the
        // console's own branding comes from its sticker. A box scan used instead has its printed console banner cut
        // off — labelArt — so a DS card no longer wore the box's NINTENDO DS strip like a little box.)
        val band = 0f
        val artRect = RectF(lr.left, lr.top + band, lr.right, lr.bottom)
        if (art != null) {
            // (Update 206: the picture fills the label exactly — stretched to it, never cut — the label keeps the shell's
            // real size and place, the art adapts to it; a label scan is the sticker itself.)
            // (Update 240, #90: a scan of the whole sticker of a label that folds over the top has its fold cut off its
            // top — that part is on the top face — rather than the whole sticker squashed into the front: foldCrop.)
            if (scan) c.drawBitmap(art, foldCrop(s, art), artRect, p)
            else {
                // (Update 215: a cover keeps its own shape — fitted inside the label, never stretched to it — and the
                // rest of the label is the colour along the cover's own edge, so it reads as one printed sticker. A real
                // label scan is the sticker itself and still fills it.)
                val pic = labelArt(s, art)
                val pa = pic.width.toFloat() / pic.height.coerceAtLeast(1)
                val la = artRect.width() / artRect.height().coerceAtLeast(1f)
                val fit = if (pa > la) RectF(artRect.left, artRect.centerY() - artRect.width() / pa / 2f, artRect.right, artRect.centerY() + artRect.width() / pa / 2f)
                    else RectF(artRect.centerX() - artRect.height() * pa / 2f, artRect.top, artRect.centerX() + artRect.height() * pa / 2f, artRect.bottom)
                if (kotlin.math.abs(pa - la) > 0.02f) { p.color = edgeColour(pic, pa > la); c.drawRect(artRect, p) }
                c.drawBitmap(pic, null, fit, p)
            }
        } else if (!title.isNullOrBlank()) {
            text(c, title, artRect, 0xFF2A2A2E.toInt(), p, bold = true, maxLines = 3)
        }
        p.color = 0xEE141418.toInt()
        if (band > 0f) c.drawRect(RectF(lr.left, lr.top, lr.right, lr.top + band), p)
        if (band > 0f && brand.isNotEmpty()) text(c, brand, RectF(lr.left + band * 0.3f, lr.top, lr.right - band * 0.3f, lr.top + band), 0xFFF2F2F2.toInt(), p, bold = true, maxLines = 1)
        // A little gloss over the sticker.
        p.shader = LinearGradient(lr.left, lr.top, lr.right, lr.bottom, intArrayOf(0x22FFFFFF, 0x00FFFFFF, 0x10FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(lr, p); p.shader = null
        c.restore()
        c.restore()
        cache.put(key, b)
        return b
    }

    /**
     * The shell's back (as seen from the front through it, so it reads right once turned round): the plastic, the seam
     * just inside its edge, its screws and contacts.
     * Update 200: each shell's own back from the research (CartBacks): its screws (cross, tri-wing, security, slot) in
     * their recesses, moulded text raised or sunken, sunken and raised panels, the warning / info stickers, the seams,
     * the connector (open with its pads where it shows) and parts like notches, grips, windows and doors. [neutral]:
     * the same shape, screws, panels and contacts without the maker's text and stickers (a game on another console's
     * shell). Shells without researched backs keep the plain one. The front is never touched.
     */
    fun back(s: CartShell, colour: ShellColour, maxPx: Int, neutral: Boolean = false): Bitmap {
        val data = CartBacks.of(s.id)
        val plainText = neutral && data?.neutralOk != false
        val key = "b|${s.id}|${colour.key}|$maxPx|$plainText"
        cache.get(key)?.let { return it }
        val k = maxPx / maxOf(s.w, s.h)
        val bw = (s.w * k).toInt().coerceAtLeast(8); val bh = (s.h * k).toInt().coerceAtLeast(8)
        val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        // (Drawn as seen from behind — text reads right — then mirrored, since the piece flips it back when turned.)
        c.scale(-1f, 1f, bw / 2f, 0f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // The outline as seen from behind (the front's, mirrored).
        val path = outlinePath(s, k).apply { transform(android.graphics.Matrix().apply { setScale(-1f, 1f, bw / 2f, 0f) }) }
        // (Update 210: a two-tone shell's back half in its own plastic.)
        val base = if (colour.backArgb != 0) colour.backArgb else colour.argb; val op = colour.opacity
        // A see-through shell shows its board from behind too.
        // (Update 201: what's inside first and solid — the board, dark round it — then the clear plastic over it. The
        // back was see-through, and the turned piece's face behind it — its label, mirrored — showed through it.)
        if (op < 0.85f) {
            c.save(); c.clipPath(path)
            p.color = withAlpha(shade(base, -0.62f), 1f); c.drawPath(path, p)
            val board = RectF(bw * 0.1f, bh * 0.22f, bw * 0.9f, bh * 0.97f)
            p.color = withAlpha(0xFF1E5F3A.toInt(), 0.92f); c.drawRect(board, p)
            p.color = withAlpha(0xFF111111.toInt(), 0.92f)
            c.drawRect(RectF(board.left + board.width() * 0.4f, board.top + board.height() * 0.25f, board.left + board.width() * 0.75f, board.top + board.height() * 0.5f), p)
            c.restore()
        }
        p.shader = LinearGradient(0f, 0f, 0f, bh.toFloat(), withAlpha(shade(base, 0.04f), op), withAlpha(shade(base, -0.16f), op), Shader.TileMode.CLAMP)
        c.drawPath(path, p); p.shader = null
        c.save(); c.clipPath(path)
        finish(c, s, colour, k, bw, bh, p, front = false)
        // The seam where the two halves meet, just inside the edge.
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.35f * k
        p.color = withAlpha(shade(base, -0.45f), maxOf(op, 0.6f))
        val inset = android.graphics.Matrix().apply { setScale(1f - 1.6f / s.w, 1f - 1.6f / s.h, bw / 2f, bh / 2f) }
        c.drawPath(Path(path).apply { transform(inset) }, p)
        p.style = Paint.Style.FILL
        if (data == null) {
            for (sc in s.screws) screw(c, (s.w - sc[0]) * k, sc[1] * k, 3.8f * k, 4.8f * k, 0, base, p)
            s.contactsBack?.let { ct -> contacts(c, floatArrayOf(s.w - ct[0] - ct[2], ct[1], ct[2], ct[3]), k, p) }
        } else drawBack(c, data, k, base, op, plainText, p)
        c.restore()
        cache.put(key, b)
        return b
    }

    /** Update 200: [d] drawn on the back (seen from behind, mm × [k]). */
    private fun drawBack(c: Canvas, d: CartBack, k: Float, base: Int, op: Float, neutral: Boolean, p: Paint) {
        val solid = maxOf(op, 0.6f)
        fun rr(x: Float, y: Float, w: Float, h: Float) = RectF(x * k, y * k, (x + w) * k, (y + h) * k)
        // Sunken panels: darker, shadowed along the top and left (the light comes from above), lit along the bottom.
        var i = 0
        while (i + 5 < d.recesses.size) {
            val r = rr(d.recesses[i], d.recesses[i + 1], d.recesses[i + 2], d.recesses[i + 3]); val rad = d.recesses[i + 4] * k
            val deep = d.recesses[i + 5].coerceIn(0.1f, 4f)
            p.color = withAlpha(shade(base, -0.06f - 0.05f * deep), solid); c.drawRoundRect(r, rad, rad, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = (0.25f + 0.15f * deep) * k
            p.color = withAlpha(shade(base, -0.45f), solid * 0.9f)
            c.drawLine(r.left + rad, r.top, r.right - rad, r.top, p); c.drawLine(r.left, r.top + rad, r.left, r.bottom - rad, p)
            p.color = withAlpha(shade(base, 0.25f), solid * 0.7f)
            c.drawLine(r.left + rad, r.bottom, r.right - rad, r.bottom, p); c.drawLine(r.right, r.top + rad, r.right, r.bottom - rad, p)
            p.style = Paint.Style.FILL
            i += 6
        }
        // Raised panels: a little lighter, lit along the top, a shadow under them.
        i = 0
        while (i + 4 < d.raised.size) {
            val r = rr(d.raised[i], d.raised[i + 1], d.raised[i + 2], d.raised[i + 3]); val rad = d.raised[i + 4] * k
            p.color = withAlpha(0xFF000000.toInt(), 0.18f * solid); c.drawRoundRect(RectF(r.left, r.top + 0.5f * k, r.right, r.bottom + 0.6f * k), rad, rad, p)
            p.color = withAlpha(shade(base, 0.07f), solid); c.drawRoundRect(r, rad, rad, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 0.3f * k
            p.color = withAlpha(shade(base, 0.35f), solid * 0.8f); c.drawLine(r.left + rad, r.top, r.right - rad, r.top, p)
            p.style = Paint.Style.FILL
            i += 5
        }
        // Other parts.
        i = 0
        while (i + 4 < d.extras.size) {
            val kind = d.extras[i].toInt(); val r = rr(d.extras[i + 1], d.extras[i + 2], d.extras[i + 3], d.extras[i + 4])
            when (kind) {
                0 -> { // a notch / step in the shell
                    // (Update 201: a step down cut into the edge — its floor a shade darker, its walls (the sides not on
                    // the shell's edge) a shadow line above / left and a lit one below / right. It was a flat dark
                    // see-through block that read as a stray plate on the back, e.g. the Game Boy's power-lock notch.)
                    p.color = withAlpha(shade(base, -0.12f), solid); c.drawRect(r, p)
                    val edge = 0.6f * k
                    val wPx = c.width.toFloat(); val hPx = c.height.toFloat()
                    p.style = Paint.Style.STROKE; p.strokeWidth = 0.3f * k
                    p.color = withAlpha(shade(base, -0.5f), solid)
                    if (r.top > edge) c.drawLine(r.left, r.top, r.right, r.top, p)
                    if (r.left > edge) c.drawLine(r.left, r.top, r.left, r.bottom, p)
                    p.color = withAlpha(shade(base, 0.3f), solid * 0.8f)
                    if (r.bottom < hPx - edge) c.drawLine(r.left, r.bottom, r.right, r.bottom, p)
                    if (r.right < wPx - edge) c.drawLine(r.right, r.top, r.right, r.bottom, p)
                    p.style = Paint.Style.FILL
                }
                1 -> { // grip ridges
                    val n = (minOf(r.width(), r.height()) / (1.4f * k)).toInt().coerceIn(2, 14)
                    val across = r.width() >= r.height()
                    for (g in 0 until n) {
                        val f = (g + 0.5f) / n
                        p.color = withAlpha(shade(base, -0.35f), solid)
                        if (across) c.drawRect(r.left, r.top + r.height() * f - 0.25f * k, r.right, r.top + r.height() * f + 0.1f * k, p)
                        else c.drawRect(r.left + r.width() * f - 0.25f * k, r.top, r.left + r.width() * f + 0.1f * k, r.bottom, p)
                    }
                }
                3 -> { p.color = 0xFF0C0C0E.toInt(); c.drawRoundRect(r, 0.8f * k, 0.8f * k, p) } // a hole
                4, 9 -> { // a dark glass window / lens
                    p.color = 0xFF101318.toInt(); if (kind == 9) c.drawOval(r, p) else c.drawRoundRect(r, 0.8f * k, 0.8f * k, p)
                    p.shader = LinearGradient(r.left, r.top, r.right, r.bottom, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
                    if (kind == 9) c.drawOval(r, p) else c.drawRoundRect(r, 0.8f * k, 0.8f * k, p); p.shader = null
                }
                5, 8 -> { // a compartment / battery door: a deep recess with its lid's seam and a finger catch
                    p.color = withAlpha(shade(base, -0.18f), solid); c.drawRoundRect(r, 1f * k, 1f * k, p)
                    p.style = Paint.Style.STROKE; p.strokeWidth = 0.4f * k; p.color = withAlpha(shade(base, -0.55f), solid)
                    c.drawRoundRect(r, 1f * k, 1f * k, p); p.style = Paint.Style.FILL
                    p.color = withAlpha(shade(base, -0.4f), solid)
                    c.drawRoundRect(RectF(r.centerX() - 3f * k, r.bottom - 2.2f * k, r.centerX() + 3f * k, r.bottom - 1.2f * k), 0.5f * k, 0.5f * k, p)
                }
                6 -> { // a hinged flap: raised, its hinge line along the top
                    p.color = withAlpha(shade(base, 0.06f), solid); c.drawRoundRect(r, 1.2f * k, 1.2f * k, p)
                    p.color = withAlpha(shade(base, -0.5f), solid); c.drawRect(r.left, r.top + 0.8f * k, r.right, r.top + 1.2f * k, p)
                }
                7 -> { // a port: dark opening with pins
                    p.color = 0xFF0B0B0D.toInt(); c.drawRoundRect(r, 0.6f * k, 0.6f * k, p)
                    p.color = 0xFF8D8F94.toInt()
                    val n = (r.width() / (1.6f * k)).toInt().coerceIn(3, 12)
                    for (g in 0 until n) { val x = r.left + r.width() * (g + 0.5f) / n; c.drawRect(x - 0.25f * k, r.top + r.height() * 0.3f, x + 0.25f * k, r.bottom - r.height() * 0.3f, p) }
                }
                else -> { p.color = withAlpha(shade(base, 0.05f), solid); c.drawRoundRect(r, 0.8f * k, 0.8f * k, p) } // a tab
            }
            i += 5
        }
        // Seams: a dark line with a lit one beside it.
        i = 0
        p.strokeCap = Paint.Cap.ROUND
        while (i + 3 < d.seams.size) {
            val x1 = d.seams[i] * k; val y1 = d.seams[i + 1] * k; val x2 = d.seams[i + 2] * k; val y2 = d.seams[i + 3] * k
            p.style = Paint.Style.STROKE
            p.strokeWidth = 0.3f * k; p.color = withAlpha(shade(base, -0.5f), solid); c.drawLine(x1, y1, x2, y2, p)
            p.strokeWidth = 0.2f * k; p.color = withAlpha(shade(base, 0.25f), solid * 0.6f); c.drawLine(x1, y1 + 0.35f * k, x2, y2 + 0.35f * k, p)
            p.style = Paint.Style.FILL
            i += 4
        }
        // The connector: open (its pads showing, with plastic ribs between them where it has them) or the shell's slot.
        val cn = d.connector
        if (cn.size >= 7) {
            val r = rr(cn[0], cn[1], cn[2], cn[3])
            val open = cn[4] > 0.5f; val n = cn[5].toInt(); val ribs = cn[6] > 0.5f
            if (open && n > 0) {
                p.color = 0xFF1E1F22.toInt(); c.drawRect(r, p)
                val step = r.width() / n
                for (g in 0 until n) {
                    val x = r.left + g * step
                    p.color = 0xFFC9A646.toInt(); c.drawRect(x + step * (if (ribs) 0.22f else 0.12f), r.top + 0.35f * k, x + step * (if (ribs) 0.78f else 0.88f), r.bottom - 0.2f * k, p)
                    if (ribs && g > 0) { p.color = withAlpha(shade(base, -0.2f), 1f); c.drawRect(x - step * 0.1f, r.top, x + step * 0.1f, r.bottom, p) }
                }
            } else {
                p.color = withAlpha(shade(base, -0.4f), solid * 0.8f)
                c.drawRect(r.left, r.top, r.right, r.top + 0.35f * k, p)
                p.color = withAlpha(shade(base, -0.15f), solid * 0.6f); c.drawRect(r.left, r.top + 0.35f * k, r.right, r.bottom, p)
            }
        }
        // Stickers (not on the neutral back): the paper, a heading band and lines of small print.
        if (!neutral) {
            i = 0; var ci = 0
            while (i + 4 < d.stickers.size) {
                val r = rr(d.stickers[i], d.stickers[i + 1], d.stickers[i + 2], d.stickers[i + 3]); val kind = d.stickers[i + 4].toInt()
                val bg = d.stickerColours.getOrElse(ci) { 0xFFEDEDED.toInt() }; val ink = d.stickerColours.getOrElse(ci + 1) { 0xFF222222.toInt() }
                p.color = 0x22000000; c.drawRoundRect(RectF(r.left, r.top + 0.3f * k, r.right, r.bottom + 0.4f * k), 0.6f * k, 0.6f * k, p)
                p.color = bg; c.drawRoundRect(r, 0.6f * k, 0.6f * k, p)
                val pad = minOf(r.width(), r.height()) * 0.08f
                p.color = withAlpha(ink, 0.85f)
                val lineH = (0.9f * k).coerceAtMost(r.height() / 6f)
                var y = r.top + pad
                if (kind == 0 && r.height() > 6f * lineH) { c.drawRect(r.left + pad, y, r.left + r.width() * 0.45f, y + lineH * 1.6f, p); y += lineH * 3f }
                p.color = withAlpha(ink, 0.55f)
                var row = 0
                while (y + lineH < r.bottom - pad) {
                    val end = if (row % 4 == 3) 0.6f else 0.92f
                    c.drawRect(r.left + pad, y, r.left + pad + (r.width() - 2 * pad) * end, y + lineH * 0.55f, p)
                    y += lineH * 1.5f; row++
                }
                i += 5; ci += 2
            }
        }
        // Moulded text (not on the neutral back): raised (lit above, shadow below) or sunken (the other way round).
        if (!neutral) {
            val tp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true; typeface = android.graphics.Typeface.DEFAULT_BOLD }
            i = 0
            var ti = 0
            while (i + 4 < d.textPos.size) {
                val t = d.texts.getOrElse(ti) { "" }
                val x = d.textPos[i] * k; val y = d.textPos[i + 1] * k; val cap = d.textPos[i + 2] * k; val rot = d.textPos[i + 3]
                val raisedText = d.textPos[i + 4] > 0.5f
                if (t.isNotBlank() && cap > 1.5f) {
                    tp.textSize = cap * 1.42f
                    c.save(); c.rotate(rot, x, y)
                    val by = y - (tp.descent() + tp.ascent()) / 2f
                    val off = maxOf(0.18f * k, 0.6f)
                    tp.color = withAlpha(shade(base, if (raisedText) 0.35f else -0.55f), solid); c.drawText(t, x, by - off, tp)
                    tp.color = withAlpha(shade(base, if (raisedText) -0.55f else 0.35f), solid); c.drawText(t, x, by + off, tp)
                    tp.color = withAlpha(shade(base, if (raisedText) 0.06f else -0.12f), solid); c.drawText(t, x, by, tp)
                    c.restore()
                }
                i += 5; ti++
            }
        }
        // Screws last, in their recesses.
        i = 0
        while (i + 4 < d.screws.size) {
            screw(c, d.screws[i] * k, d.screws[i + 1] * k, d.screws[i + 2] * k, d.screws[i + 3] * k, d.screws[i + 4].toInt(), base, p)
            i += 5
        }
    }

    /** Update 200: a screw head ([head] Ø) in its recess ([recess] Ø), its drive drawn by [type]. */
    private fun screw(c: Canvas, x: Float, y: Float, head: Float, recess: Float, type: Int, base: Int, p: Paint) {
        val rr = maxOf(recess, head) / 2f; val hr = head / 2f
        p.shader = android.graphics.RadialGradient(x, y - rr * 0.2f, rr, shade(base, -0.65f), shade(base, -0.35f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, rr, p); p.shader = null
        if (type == 4) return // a clip: just the hole
        p.shader = android.graphics.RadialGradient(x - hr * 0.3f, y - hr * 0.3f, hr * 1.3f, 0xFFBFC1C6.toInt(), 0xFF6D6F75.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, hr, p); p.shader = null
        p.color = 0xFF2E3034.toInt()
        val w = maxOf(hr * 0.16f, 0.6f)
        when (type) {
            0 -> { c.drawRect(x - hr * 0.62f, y - w, x + hr * 0.62f, y + w, p); c.drawRect(x - w, y - hr * 0.62f, x + w, y + hr * 0.62f, p) }
            1 -> for (a in 0 until 3) { // tri-wing: three slots at 120°
                c.save(); c.rotate(a * 120f - 90f, x, y); c.drawRect(x, y - w, x + hr * 0.7f, y + w, p); c.restore()
            }
            2 -> { // security / game bit: a round drive with a pin in it
                p.color = 0xFF3A3C41.toInt(); c.drawCircle(x, y, hr * 0.55f, p)
                p.color = 0xFF9A9CA2.toInt(); c.drawCircle(x, y, hr * 0.2f, p)
            }
            3 -> c.drawRect(x - hr * 0.7f, y - w, x + hr * 0.7f, y + w, p)
            5 -> { // torx: a star
                val star = Path()
                for (s in 0 until 12) {
                    val ang = Math.toRadians((s * 30f - 90f).toDouble()); val rad = if (s % 2 == 0) hr * 0.6f else hr * 0.38f
                    val px = x + rad * kotlin.math.cos(ang).toFloat(); val py = y + rad * kotlin.math.sin(ang).toFloat()
                    if (s == 0) star.moveTo(px, py) else star.lineTo(px, py)
                }
                star.close(); c.drawPath(star, p)
            }
        }
    }

    private fun contacts(c: Canvas, ct: FloatArray, k: Float, p: Paint) {
        val n = (ct[2] / 1.6f).toInt().coerceIn(4, 40)
        val step = ct[2] / n
        p.color = 0xFF2A2A2A.toInt()
        c.drawRect(ct[0] * k, ct[1] * k, (ct[0] + ct[2]) * k, (ct[1] + ct[3]) * k, p)
        p.color = 0xFFC9A646.toInt()
        for (i in 0 until n) {
            val x = (ct[0] + i * step + step * 0.18f) * k
            c.drawRect(x, (ct[1] + 0.4f) * k, x + step * 0.64f * k, (ct[1] + ct[3] - 0.4f) * k, p)
        }
    }

    private fun drawExtra(c: Canvas, e: ShellExtra, k: Float, base: Int, p: Paint) {
        val r = RectF(e.x * k, e.y * k, (e.x + e.w) * k, (e.y + e.h) * k)
        when (e.kind) {
            ShellExtra.ACCENT -> { p.color = e.argb; c.drawRoundRect(r, 1.2f * k, 1.2f * k, p) }
            ShellExtra.SLOT -> {
                p.color = 0xFF050506.toInt(); c.drawRoundRect(r, 0.8f * k, 0.8f * k, p)
                p.color = shade(base, 0.2f); c.drawRect(r.left, r.bottom - 0.4f * k, r.right, r.bottom, p)
            }
            ShellExtra.LENS -> {
                p.color = 0xFF0A0A0C.toInt(); c.drawOval(r, p)
                p.color = 0xFF27364A.toInt(); c.drawOval(RectF(r.left + r.width() * 0.2f, r.top + r.height() * 0.2f, r.right - r.width() * 0.2f, r.bottom - r.height() * 0.2f), p)
                p.color = 0x88FFFFFF.toInt(); c.drawOval(RectF(r.left + r.width() * 0.32f, r.top + r.height() * 0.28f, r.left + r.width() * 0.48f, r.top + r.height() * 0.42f), p)
            }
            ShellExtra.WINDOW -> {
                p.color = e.argb; c.drawRoundRect(r, 0.8f * k, 0.8f * k, p)
                p.shader = LinearGradient(r.left, r.top, r.right, r.bottom, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP); c.drawRoundRect(r, 0.8f * k, 0.8f * k, p); p.shader = null
            }
            ShellExtra.FLAP -> {
                p.color = e.argb; c.drawRoundRect(r, 1.5f * k, 1.5f * k, p)
                p.color = shade(e.argb, -0.4f); c.drawRect(r.left, r.bottom - 0.5f * k, r.right, r.bottom, p)
            }
            ShellExtra.KEYPAD -> {
                p.color = e.argb; c.drawRoundRect(r, 1f * k, 1f * k, p)
                p.color = 0x66FFFFFF
                for (row in 0 until 4) for (col in 0 until 3) {
                    val cw = r.width() / 3f; val ch = r.height() / 4f
                    c.drawRoundRect(RectF(r.left + col * cw + cw * 0.15f, r.top + row * ch + ch * 0.15f, r.left + (col + 1) * cw - cw * 0.15f, r.top + (row + 1) * ch - ch * 0.15f), 0.6f * k, 0.6f * k, p)
                }
            }
        }
    }

    /**
     * Update 240 (#90): the part of a label scan [b] that's on [s]'s front, for a sticker that folds over the top
     * ([CartShell.labelFold]): when [b]'s shape is nearer the whole sticker's (front + fold) than the front window's, its
     * top fold / (front + fold) is left off. Null (all of it) otherwise — a scan of just the front, or no fold.
     */
    private fun foldCrop(s: CartShell, b: Bitmap): android.graphics.Rect? {
        val fold = s.labelFold
        val lw = s.label[2]; val lh = s.label[3]
        if (fold <= 0f || lw <= 0f || lh <= 0f || b.width < 8 || b.height < 8) return null
        val a = b.width.toFloat() / b.height
        val front = lw / lh
        val whole = lw / (lh + fold)
        if (kotlin.math.abs(a / whole - 1f) >= kotlin.math.abs(a / front - 1f)) return null
        val cut = (b.height * fold / (lh + fold)).toInt().coerceIn(0, b.height - 4)
        return android.graphics.Rect(0, cut, b.width, b.height)
    }

    /** The middle of [b] at [aspect] (width / height). */
    private fun cropRect(b: Bitmap, aspect: Float): android.graphics.Rect {
        val cur = b.width.toFloat() / b.height.coerceAtLeast(1)
        return if (cur > aspect) {
            val w = (b.height * aspect).toInt().coerceAtLeast(1); android.graphics.Rect((b.width - w) / 2, 0, (b.width + w) / 2, b.height)
        } else {
            val h = (b.width / aspect).toInt().coerceAtLeast(1); android.graphics.Rect(0, (b.height - h) / 2, b.width, (b.height + h) / 2)
        }
    }

    private fun text(c: Canvas, s: String, r: RectF, argb: Int, p: Paint, bold: Boolean, maxLines: Int) {
        val tp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = argb; isFakeBoldText = bold; textAlign = Paint.Align.CENTER }
        var size = r.height() * (if (maxLines == 1) 0.62f else 0.22f)
        tp.textSize = size
        if (maxLines == 1) {
            while (tp.measureText(s) > r.width() && size > 4f) { size -= 1f; tp.textSize = size }
            c.drawText(s, r.centerX(), r.centerY() - (tp.descent() + tp.ascent()) / 2f, tp)
        } else {
            val words = s.split(' ')
            val lines = ArrayList<String>()
            var cur = ""
            for (w in words) { val nx = if (cur.isEmpty()) w else "$cur $w"; if (tp.measureText(nx) > r.width() * 0.9f && cur.isNotEmpty()) { lines.add(cur); cur = w } else cur = nx }
            if (cur.isNotEmpty()) lines.add(cur)
            val shown = lines.take(maxLines)
            val lh = tp.textSize * 1.15f
            var y = r.centerY() - lh * (shown.size - 1) / 2f - (tp.descent() + tp.ascent()) / 2f
            for (l in shown) { c.drawText(l, r.centerX(), y, tp); y += lh }
        }
    }
}

/** Update 195: which shell each game (or every game of a console) is drawn on — saved in Voltcap's settings. */
internal object CartShellStore {
    // Update 200: a plain map plus a version number made when the app starts (Features.init → warmUp). The map used
    // to be a state map made the first time a game was drawn — in the middle of a screen's measuring, made "outside"
    // it (Snapshot.global), so reading it there crashed the Odin 2 Portal ("Reading a state that was created after the
    // snapshot was taken").
    private val map = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val version = androidx.compose.runtime.mutableIntStateOf(0)
    @Volatile private var loaded = false

    /** Called once at start (Features.init), before any screen draws. */
    fun warmUp() { load() }

    @Synchronized private fun fill() {
        if (loaded) return
        Features.cartShellChoices.value.split('\n').forEach { line ->
            val i = line.indexOf('\t')
            if (i > 0) map[line.substring(0, i)] = line.substring(i + 1)
        }
        loaded = true
    }

    /** Loads once, and (read from a screen) makes it redraw when a choice changes. */
    private fun load() { if (!loaded) fill(); runCatching { version.intValue } }

    private fun save() {
        Features.cartShellChoices.value = map.entries.joinToString("\n") { "${it.key}\t${it.value}" }
        version.intValue++
    }

    fun forGame(key: String): String? { load(); return map[key] }
    fun forConsole(platform: String): String? { load(); return map["console:$platform"] }

    /** The game's own choice, else its console's. */
    fun choice(platform: String, gameKey: String?): String? { load(); return gameKey?.let { map[it] } ?: map["console:$platform"] }

    fun setGame(key: String, value: String?) { load(); if (value == null) map.remove(key) else map[key] = value; save() }
    fun setConsole(platform: String, value: String?) { load(); if (value == null) map.remove("console:$platform") else map["console:$platform"] = value; save() }
}

/** Update 195: a game drawn on a shell from the library ([pickPlatform] "~sh:<choice>|<console>"), its cover as the label. */
@Composable
internal fun CartShellArt(
    pickPlatform: String, art: Any?, modifier: Modifier, title: String? = null, maxPx: Int = 640,
    /** Update 205: [art] is the game's real label scan: it fills the label, no console band over it. */
    labelIsScan: Boolean = false
) {
    val (choice, console) = remember(pickPlatform) { CartShells.split(pickPlatform) }
    val packLook = choice?.pack
    // (Update 200: a retired 3D piece comes here as its console's authentic shell — CartShells.split — never the
    // pre-rendered pictures.)
    if (packLook != null) {
        PackCart(console ?: "", art, modifier, packLook, choice.clear)
    } else {
        val shell = choice?.shell
        val bmpArt = rememberSoftBitmap(art, 480)
        val brand = remember(console) { console?.let { com.shelf.launcher.data.ConsoleNames.display(it) } ?: "" }
        val front = remember(pickPlatform, bmpArt, brand, title, maxPx, labelIsScan) {
            if (shell == null) null else runCatching { CartShells.front(shell, CartShells.colourOf(shell, choice.colourKey), bmpArt, brand, title, maxPx, labelIsScan) }.getOrNull()
        }
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val a = shell?.aspect ?: 1f
            val w = minOf(maxWidth, maxHeight * a)
            val h = w / a
            if (front != null) Image(front.asImageBitmap(), shell?.name, Modifier.size(w, h))
        }
    }
}
