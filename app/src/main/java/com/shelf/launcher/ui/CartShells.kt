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

internal class ShellColour(val key: String, val name: String, val argb: Int, val opacity: Float)

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
    val note: String
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
            note = "Size and label (55×97 mm with 7 mm over the top) measured (NESdev, ConsoleMods); ridges, screw places and radii estimated."),
        CartShell("famicom", "Famicom cassette", ShellGroup.NINTENDO, 108f, 71f, 17f, rect(108f, 71f, 3f, 1f), lab(9f, 5f, 90f, 45.5f),
            listOf(rid(3f, 52f, 102f, 14f, 5)), emptyList(),
            listOf(c("Grey", 0xFFC8C8C8), c("Black", BLACK), c("Red", 0xFFB3262E), c("Yellow", 0xFFE8C51E), c("Blue", 0xFF2D4FA0)),
            check = CaseCheck.ESTIMATED, note = "Size from ConsoleMods (landscape), sticker 90×45.5 mm measured; outline estimated. Shell colours vary by game."),
        CartShell("snes_na", "SNES Game Pak (North America)", ShellGroup.NINTENDO, 135.85f, 87.7f, 19.7f, rect(135.85f, 87.7f, 3f, 1.5f), lab(12f, 6f, 112f, 46f),
            listOf(rid(4f, 0.5f, 128f, 3f, 10, false)), listOf(sc(38f, 20f), sc(98f, 20f)),
            listOf(c("Grey", 0xFFA9A8AE), c("Black", 0xFF1E1E1F)),
            extras = listOf(ShellExtra(ShellExtra.SLOT, 11f, 62f, 8f, 14f), ShellExtra(ShellExtra.SLOT, 116.85f, 62f, 8f, 14f)),
            check = CaseCheck.ESTIMATED, note = "Size measured (135.85×87.7×19.7); label, ridges and the early lock slots' places estimated."),
        CartShell("sfc", "Super Famicom / SNES PAL cassette", ShellGroup.NINTENDO, 130f, 86f, 20f, rect(130f, 86f, 12f, 1.5f), lab(17f, 8f, 96f, 52f),
            listOf(rid(3f, 18f, 9f, 44f, 7), rid(118f, 18f, 9f, 44f, 7)), listOf(sc(32f, 24f), sc(98f, 24f)),
            listOf(c("Grey", 0xFF9E9DA3)), check = CaseCheck.ESTIMATED,
            note = "Size from Wikipedia (130×86×20; ConsoleMods 127 wide); the big round top corners and side grips estimated."),
        CartShell("n64", "Nintendo 64 Game Pak", ShellGroup.NINTENDO, 116f, 76.6f, 18.5f,
            o(0f, 0f, 6f, 116f, 0f, 6f, 116f, 50f, 1f, 113f, 53f, 1f, 113f, 76.6f, 1.5f, 3f, 76.6f, 1.5f, 3f, 53f, 1f, 0f, 50f, 1f), lab(12f, 4f, 92f, 48f),
            listOf(rid(4f, 0.5f, 108f, 2.5f, 20, false)), listOf(sc(28f, 22f), sc(88f, 22f)),
            listOf(c("Grey", 0xFF7C7D82), c("Black", 0xFF202022), c("Gold", 0xFFC9A84C), c("Red", 0xFFB3262E), c("Blue", 0xFF2D4FA0),
                c("Clear", 0xFFE6E8EA, 0.45f), c("Smoke", 0xFF3A3A40, 0.6f)),
            check = CaseCheck.ESTIMATED, note = "Size measured (116×76.6×18.5); the step-in for the lower part and the label estimated."),
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
            o(0f, 0f, 3f, 60f, 0f, 3f, 60f, 8f, 0.5f, 58.5f, 8f, 0.5f, 58.5f, 35f, 1f, 1.5f, 35f, 1f, 1.5f, 8f, 0.5f, 0f, 8f, 0.5f), lab(5f, 9f, 50f, 22f),
            listOf(rid(4f, 1f, 52f, 5f, 4)), listOf(sc(30f, 12f)),
            listOf(c("Dark grey", 0xFF4A4A50), c("Ruby red", 0xFFC0202A, 0.7f), c("Sapphire blue", 0xFF2850B0, 0.7f), c("Emerald green", 0xFF2E9A48, 0.7f)),
            contactsBack = floatArrayOf(5f, 30f, 50f, 4.5f), check = CaseCheck.ESTIMATED, note = "Width 60 / 57.5 and height 35 measured; the wide top's height estimated."),
        CartShell("vb", "Virtual Boy Game Pak", ShellGroup.NINTENDO, 75.8f, 68.36f, 6.61f, rect(75.8f, 68.36f, 3f, 1f), lab(11.9f, 6f, 52f, 42f),
            colours = listOf(c("Black", 0xFF232325)), check = CaseCheck.ESTIMATED,
            note = "Size measured (75.8×68.36×6.61; 73.74 tall with its dust cap), label about 52×42 measured; the dust cap isn't drawn."),
        CartShell("ds", "Nintendo DS Game Card", ShellGroup.NINTENDO, 33f, 35f, 3.8f, rect(33f, 35f, 1.5f, 0.8f), lab(2.5f, 5f, 28f, 28f),
            listOf(rid(1f, 0.5f, 31f, 2.5f, 1)), colours = listOf(c("Dark grey", 0xFF3C3C40), c("White", 0xFFF2F2F2)),
            contactsBack = floatArrayOf(2f, 29f, 29f, 5f), check = CaseCheck.ESTIMATED, note = "Size measured (33×35×3.8); layout estimated."),
        CartShell("3ds", "Nintendo 3DS Game Card", ShellGroup.NINTENDO, 34f, 35f, 3.8f,
            o(0f, 0f, 1.5f, 33f, 0f, 0.3f, 33f, 3f, 0.3f, 34f, 3.5f, 0.3f, 34f, 7f, 0.3f, 33f, 7.5f, 0.3f, 33f, 35f, 0.8f, 0f, 35f, 0.8f), lab(2.5f, 5f, 28f, 28f),
            listOf(rid(1f, 0.5f, 31f, 2.5f, 1)), colours = listOf(c("Light grey", 0xFFE6E6E8)),
            contactsBack = floatArrayOf(2f, 29f, 29f, 5f), check = CaseCheck.ESTIMATED, note = "Size measured; the side tab (it stops a 3DS card going into a DS) estimated."),
        CartShell("switch", "Nintendo Switch Game Card", ShellGroup.NINTENDO, 21f, 31f, 3f, rect(21f, 31f, 1.5f, 0.8f), lab(1.5f, 4f, 18f, 25f),
            listOf(rid(1f, 0.5f, 19f, 2.5f, 1)), colours = listOf(c("Dark grey", 0xFF2C2C30)),
            contactsBack = floatArrayOf(2f, 25f, 17f, 5f), check = CaseCheck.ESTIMATED, note = "Size 21×31×3 (Wikipedia; others say 3.3 thick); layout estimated."),
        CartShell("switch2", "Nintendo Switch 2 Game Card", ShellGroup.NINTENDO, 21f, 31f, 3f, rect(21f, 31f, 1.5f, 0.8f), lab(1.5f, 4f, 18f, 25f),
            listOf(rid(1f, 0.5f, 19f, 2.5f, 1)), colours = listOf(c("Red", 0xFFC41E2A)),
            contactsBack = floatArrayOf(2f, 25f, 17f, 5f), check = CaseCheck.ESTIMATED, note = "Same size as Switch, red; the rear notch isn't drawn."),
        CartShell("switch2_key", "Nintendo Switch 2 Game-Key Card", ShellGroup.NINTENDO, 21f, 31f, 3f, rect(21f, 31f, 1.5f, 0.8f), lab(1.5f, 4f, 18f, 25f),
            listOf(rid(1f, 0.5f, 19f, 2.5f, 1)), colours = listOf(c("Red", 0xFFC41E2A)),
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
            colours = listOf(c("Translucent", 0xFFD8D8E0, 0.6f)), check = CaseCheck.PLACEHOLDER, note = "A taller Game Boy Advance cart with gyro and rumble (estimated)."),
        CartShell("boktai", "Boktai (solar sensor)", ShellGroup.SPECIAL, 57.5f, 50f, 9f, rect(57.5f, 50f, 3f, 1f), lab(4f, 18f, 49.5f, 26f),
            colours = listOf(c("Clear", 0xFFE6EAEE, 0.5f)), extras = listOf(ShellExtra(ShellExtra.WINDOW, 23.75f, 4f, 10f, 6f, 0xFF1E3A6A.toInt())),
            check = CaseCheck.PLACEHOLDER, note = "A clear, slightly taller Game Boy Advance cart with the solar window on top (estimated)."),
        CartShell("drill_dozer", "Drill Dozer (rumble)", ShellGroup.SPECIAL, 57.5f, 48f, 11f, rect(57.5f, 48f, 3f, 1f), lab(4f, 16f, 49.5f, 26f),
            colours = listOf(c("Red", 0xFFC42030, 0.85f)), check = CaseCheck.PLACEHOLDER, note = "A taller Game Boy Advance cart with rumble (estimated)."),
        CartShell("yoshi_topsy", "Yoshi Topsy-Turvy (tilt)", ShellGroup.SPECIAL, 57.5f, 48f, 11f, rect(57.5f, 48f, 3f, 1f), lab(4f, 16f, 49.5f, 26f),
            colours = listOf(c("Green", 0xFF4FA84A, 0.7f)), check = CaseCheck.PLACEHOLDER, note = "A taller Game Boy Advance cart with a tilt sensor (estimated)."),
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
        (if (key.isNullOrEmpty()) null else shell.colours.firstOrNull { it.key == key } ?: presets.firstOrNull { it.key == key }) ?: shell.colours.first()

    /** Each console's own shell (by region where they differ): what "Authentic drawn cartridges" draws. */
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

    class Choice(val shell: CartShell?, val pack: CartLook?, val colourKey: String, val raw: String) {
        val aspect: Float get() = shell?.aspect ?: pack?.aspect ?: 1f
        val clear: Boolean get() = colourKey == "clear"
    }

    fun parse(raw: String?): Choice? {
        if (raw.isNullOrEmpty()) return null
        val id = raw.substringBefore('~')
        val colour = raw.substringAfter('~', "")
        return if (id.startsWith("pack:")) CartLook.values().firstOrNull { it.key == id.removePrefix("pack:") }?.let { Choice(null, it, colour, raw) }
        else of(id)?.let { Choice(it, null, colour, raw) }
    }

    /** The piece for a choice on [platform]'s game. */
    fun pickOf(raw: String, platform: String): MediaPick? = parse(raw)?.let { MediaPick(MediaShell.GB, "$SHELL_PREFIX$raw|$platform", it.aspect) }

    /** "~sh:<choice>|<console>" → its choice and console. */
    fun split(pickPlatform: String): Pair<Choice?, String?> {
        val rest = pickPlatform.removePrefix(SHELL_PREFIX)
        return parse(rest.substringBefore('|')) to rest.substringAfter('|', "").ifEmpty { null }
    }

    // ---- Drawing ----

    private val cache = android.util.LruCache<String, Bitmap>(24)

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

    private fun shade(argb: Int, k: Float): Int = CaseInside.mix(argb, if (k >= 0f) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(), kotlin.math.abs(k))
    private fun withAlpha(argb: Int, a: Float): Int = (argb and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255f).toInt() shl 24)

    /**
     * The shell's front at [maxPx] on its longest side: its plastic (lit from above), a see-through shell's board
     * inside, its rim, grip ridges, special parts, and the label in its recess — [art] (the game's cover, cut to the
     * label's shape, never stretched) under a band with [brand] (the game's real console).
     */
    fun front(s: CartShell, colour: ShellColour, art: Bitmap?, brand: String, title: String?, maxPx: Int): Bitmap {
        val key = "f|${s.id}|${colour.key}|${art?.let { System.identityHashCode(it) } ?: 0}|$brand|${title ?: ""}|$maxPx"
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
        for (r in s.ridges) {
            val n = r[4].toInt().coerceAtLeast(1)
            val across = r[5] > 0.5f
            for (i in 0 until n) {
                val f = (i + 0.5f) / n
                if (across) {
                    val y = (r[1] + r[3] * f) * k
                    p.color = withAlpha(shade(base, -0.35f), maxOf(op, 0.6f)); c.drawRect(r[0] * k, y - 0.3f * k, (r[0] + r[2]) * k, y + 0.15f * k, p)
                    p.color = withAlpha(shade(base, 0.2f), maxOf(op, 0.6f)); c.drawRect(r[0] * k, y + 0.15f * k, (r[0] + r[2]) * k, y + 0.45f * k, p)
                } else {
                    val x = (r[0] + r[2] * f) * k
                    p.color = withAlpha(shade(base, -0.35f), maxOf(op, 0.6f)); c.drawRect(x - 0.3f * k, r[1] * k, x + 0.15f * k, (r[1] + r[3]) * k, p)
                    p.color = withAlpha(shade(base, 0.2f), maxOf(op, 0.6f)); c.drawRect(x + 0.15f * k, r[1] * k, x + 0.45f * k, (r[1] + r[3]) * k, p)
                }
            }
        }
        for (e in s.extras) drawExtra(c, e, k, base, p)
        s.contactsFront?.let { ct -> contacts(c, ct, k, p) }
        // The label in its recess (a shadow round it), the cover cut to its shape, the console's band on top.
        val lr = RectF(s.label[0] * k, s.label[1] * k, (s.label[0] + s.label[2]) * k, (s.label[1] + s.label[3]) * k)
        val rr = 0.8f * k
        p.color = withAlpha(shade(base, -0.4f), maxOf(op, 0.7f))
        c.drawRoundRect(RectF(lr.left - 0.7f * k, lr.top - 0.7f * k, lr.right + 0.5f * k, lr.bottom + 0.5f * k), rr, rr, p)
        c.save()
        c.clipPath(Path().apply { addRoundRect(lr, rr, rr, Path.Direction.CW) })
        p.color = 0xFFE9E6DF.toInt(); c.drawRect(lr, p)
        val band = maxOf(lr.height() * 0.14f, 2.6f * k).coerceAtMost(lr.height() * 0.3f)
        val artRect = RectF(lr.left, lr.top + band, lr.right, lr.bottom)
        if (art != null) {
            val a = artRect.width() / artRect.height().coerceAtLeast(1f)
            val src = cropRect(art, a)
            c.drawBitmap(art, src, artRect, p)
        } else if (!title.isNullOrBlank()) {
            text(c, title, artRect, 0xFF2A2A2E.toInt(), p, bold = true, maxLines = 3)
        }
        p.color = 0xEE141418.toInt()
        c.drawRect(RectF(lr.left, lr.top, lr.right, lr.top + band), p)
        if (brand.isNotEmpty()) text(c, brand, RectF(lr.left + band * 0.3f, lr.top, lr.right - band * 0.3f, lr.top + band), 0xFFF2F2F2.toInt(), p, bold = true, maxLines = 1)
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
     */
    fun back(s: CartShell, colour: ShellColour, maxPx: Int): Bitmap {
        val key = "b|${s.id}|${colour.key}|$maxPx"
        cache.get(key)?.let { return it }
        val k = maxPx / maxOf(s.w, s.h)
        val bw = (s.w * k).toInt().coerceAtLeast(8); val bh = (s.h * k).toInt().coerceAtLeast(8)
        val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.scale(-1f, 1f, bw / 2f, 0f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = outlinePath(s, k)
        val base = colour.argb; val op = colour.opacity
        p.shader = LinearGradient(0f, 0f, 0f, bh.toFloat(), withAlpha(shade(base, 0.04f), op), withAlpha(shade(base, -0.16f), op), Shader.TileMode.CLAMP)
        c.drawPath(path, p); p.shader = null
        c.save(); c.clipPath(path)
        // The seam where the two halves meet, just inside the edge.
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.35f * k
        p.color = withAlpha(shade(base, -0.45f), maxOf(op, 0.6f))
        val inset = android.graphics.Matrix().apply { setScale(1f - 1.6f / s.w, 1f - 1.6f / s.h, bw / 2f, bh / 2f) }
        c.drawPath(Path(path).apply { transform(inset) }, p)
        p.style = Paint.Style.FILL
        for (sc in s.screws) {
            val x = sc[0] * k; val y = sc[1] * k; val r = 1.9f * k
            p.color = withAlpha(shade(base, -0.5f), 1f); c.drawCircle(x, y, r * 1.25f, p)
            p.color = 0xFF8C8E94.toInt(); c.drawCircle(x, y, r, p)
            p.color = 0xFF4A4C52.toInt(); c.drawRect(x - r * 0.65f, y - r * 0.12f, x + r * 0.65f, y + r * 0.12f, p)
        }
        s.contactsBack?.let { ct -> contacts(c, ct, k, p) }
        c.restore()
        cache.put(key, b)
        return b
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
    // (Read once from the settings, outside any screen's drawing: Snapshot.global.)
    private val map by lazy {
        androidx.compose.runtime.snapshots.Snapshot.global {
            androidx.compose.runtime.mutableStateMapOf<String, String>().apply {
                Features.cartShellChoices.value.split('\n').forEach { line ->
                    val i = line.indexOf('\t')
                    if (i > 0) put(line.substring(0, i), line.substring(i + 1))
                }
            }
        }
    }

    private fun load() { map.size }

    private fun save() { Features.cartShellChoices.value = map.entries.joinToString("\n") { "${it.key}\t${it.value}" } }

    fun forGame(key: String): String? { load(); return map[key] }
    fun forConsole(platform: String): String? { load(); return map["console:$platform"] }

    /** The game's own choice, else its console's. */
    fun choice(platform: String, gameKey: String?): String? { load(); return gameKey?.let { map[it] } ?: map["console:$platform"] }

    fun setGame(key: String, value: String?) { load(); if (value == null) map.remove(key) else map[key] = value; save() }
    fun setConsole(platform: String, value: String?) { load(); if (value == null) map.remove("console:$platform") else map["console:$platform"] = value; save() }
}

/** Update 195: a game drawn on a shell from the library ([pickPlatform] "~sh:<choice>|<console>"), its cover as the label. */
@Composable
internal fun CartShellArt(pickPlatform: String, art: Any?, modifier: Modifier, title: String? = null, maxPx: Int = 640) {
    val (choice, console) = remember(pickPlatform) { CartShells.split(pickPlatform) }
    val packLook = choice?.pack
    if (packLook != null) {
        PackCart(console ?: "", art, modifier, packLook, choice.clear)
    } else {
        val shell = choice?.shell
        val bmpArt = rememberSoftBitmap(art, 480)
        val brand = remember(console) { console?.let { com.shelf.launcher.data.ConsoleNames.display(it) } ?: "" }
        val front = remember(pickPlatform, bmpArt, brand, title, maxPx) {
            if (shell == null) null else runCatching { CartShells.front(shell, CartShells.colourOf(shell, choice.colourKey), bmpArt, brand, title, maxPx) }.getOrNull()
        }
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val a = shell?.aspect ?: 1f
            val w = minOf(maxWidth, maxHeight * a)
            val h = w / a
            if (front != null) Image(front.asImageBitmap(), shell?.name, Modifier.size(w, h))
        }
    }
}
