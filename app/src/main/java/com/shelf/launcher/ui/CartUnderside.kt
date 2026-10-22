package com.shelf.launcher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.sqrt

/*
 * Update 275 (Toni, "no more flat bottoms"): the cartridge's connector end in real 3D. It replaces update 240's flat
 * picture of the bottom (a dark strip with a green line and a gold hairline painted on the bottom plane) with geometry:
 *  - the opening in the bottom face — a hole you look into, with its inner walls and ceiling, as deep as the real one;
 *    where the real shell is cut away up the back (Game Boy family, DS / 3DS / Switch cards) the opening continues up
 *    the back face as a window too;
 *  - the circuit board inside it as a solid slab (its edge, both faces, its sides), recessed where the real board sits
 *    up inside the shell, its bevelled edge where it has one (Mega Drive, N64), split round its key slots (7800, PV-1000);
 *  - its gold contact fingers on the faces where the real ones are, each one placed at the published pitch and count
 *    (wider end fingers on Game Boy boards, the blank keying positions of the Jaguar and the 7800);
 *  - the plastic between contacts where the real card has it (DS / 3DS ribs, flush with the back face), the Atari
 *    2600 / 5200 dust plates with the slit the board goes through, the Channel F door, the GBA's and Switch 2's corner
 *    notches, a second board for the Neo Geo carts.
 * How: the piece is drawn as InspectSlab's slices (its outline extruded) — a solid with no hole. This draws on top of it,
 * with the same turn and tilt (PiecePose): first the bottom face around the holes ([drawRim], after the side slices),
 * then, after the front / back face, everything seen THROUGH each opening, clipped to the opening's outline on screen
 * ([drawInside]) — the far walls first, then the boards, contacts, plates and ribs, far to near. Each face is a flat-shaded
 * polygon lit from the upper left, culled when it faces away. Only drawn while the piece is turned to show its bottom or
 * back opening; only in the game window's inspect view (the one place a piece can be tilted under).
 *
 * The numbers: research of October 2026 (CARTRIDGES.md, "Bottoms" — every value and source per shell). Contact counts and
 * pitches are published (NESdev, SNESdev, ConsoleMods, replica KiCad footprints, patents); the openings' sizes, depths and
 * where each board sits through the thickness are ESTIMATED for every shell (no measured bottom photo could be read).
 * Shells with nothing to model keep their plain edge — [CartUndersides.none] says why for each.
 */

/**
 * A shell's connector end, mm. x across from the shell's left seen from the front (from the foot's left with [foot]),
 * y up from the bottom edge, z through the thickness from the front face (0) to the back (t).
 */
internal class CartBottom(
    val id: String,
    /** The opening: x, w, z0, z1, depth (y), back cut-out height (0: closed at the back), corner radius. */
    val open: FloatArray,
    /** The board: x, w, z (its front face), thickness, edge (y: 0 flush with the bottom, more: recessed). Null: none seen. */
    val pcb: FloatArray?,
    val pcbColour: Int,
    /** Contacts: sides (1 front, 2 back, 3 both), positions, pitch, finger width, finger height, first centre x (NaN: centred), gap above the edge. */
    val contacts: FloatArray,
    val contactColour: Int,
    /** Plastic ribs between the contacts (DS / 3DS): their width; 0 none. */
    val ribW: Float = 0f,
    /** A dust plate inside the opening: y, slit z0, slit z1 (z0 = z1: a closed door). */
    val plate: FloatArray? = null,
    /** The board edge's bevel (each side), mm. */
    val bevel: Float = 0f,
    /** Key slots cut up into the board: x, w, h each. */
    val slots: FloatArray = FloatArray(0),
    /** Corner notches cut out of the shell: x0, x1, y1, z0, z1 each (open to the bottom, and to the back / side they reach). */
    val notches: FloatArray = FloatArray(0),
    /** Contact positions left blank (1-based). */
    val skip: IntArray = IntArray(0),
    /** The two end fingers half as wide again (Game Boy family boards). */
    val wideEnds: Boolean = false,
    /** x counted from the foot's left (the narrower end of a GBA-shaped shell). */
    val foot: Boolean = false,
    /** Centred on the foot (borrowed from another shell of a slightly different width). */
    val centre: Boolean = false,
    /** A second board behind the first (Neo Geo): its front face z; NaN none. */
    val pcb2Z: Float = Float.NaN,
    /** What's verified and what's estimated, per part. */
    val check: String,
    /** Where it comes from. */
    val source: String
)

internal object CartUndersides {
    @Suppress("FunctionName")
    private fun B(
        id: String, open: FloatArray, pcb: FloatArray?, pcbColour: Int, contacts: FloatArray, contactColour: Int,
        ribW: Float, plate: FloatArray?, bevel: Float, slots: FloatArray, notches: FloatArray, skip: IntArray,
        wideEnds: Boolean, foot: Boolean, centre: Boolean, pcb2Z: Float, check: String, source: String
    ) = CartBottom(id, open, pcb, pcbColour, contacts, contactColour, ribW, plate, bevel, slots, notches, skip, wideEnds, foot, centre, pcb2Z, check, source)

    val all: Map<String, CartBottom> = listOf(
        B("nes", floatArrayOf(9.75f, 100.5f, 3f, 14f, 22f, 0f, 1f), floatArrayOf(13.25f, 93.5f, 7.9f, 1.2f, 8f), 0xFF2F6B3A.toInt(), floatArrayOf(3f, 36f, 2.5f, 2f, 12f, 16.25f, 1f), 0xFFD4A941.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb verified; contacts verified; ribs estimated",
            source = "https://www.nesdev.org/wiki/Cartridge_connector https://www.nesdev.org/wiki/NES_cartrige_dimensions https://nesdev.nes.science/f9/t5095.xhtml https://nesdev.nes.science/f9/t9261.xhtml"),
        B("famicom", floatArrayOf(13f, 82f, 4f, 13f, 11f, 0f, 1f), floatArrayOf(14.8f, 78.4f, 7.9f, 1.2f, 1f), 0xFF3B6B3A.toInt(), floatArrayOf(3f, 30f, 2.54f, 1.6f, 5.7f, 17.17f, 2f), 0xFFD4A941.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb verified; contacts verified; ribs estimated",
            source = "https://nesdev.org/wiki/Famicom_cartridge_dimensions https://www.nesdev.org/wiki/Cartridge_connector https://ConsoleMods.org/wiki/Dimensions_for_Game_Cartridges"),
        B("snes_na", floatArrayOf(16f, 104f, 3.5f, 16.2f, 12f, 0f, 1f), floatArrayOf(38.4f, 59f, 9.9f, 1.2f, 3f), 0xFF2D6A3E.toInt(), floatArrayOf(3f, 23f, 2.5f, 1.7f, 7f, 40.43f, 1f), 0xFFD4A941.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb verified; contacts verified; ribs estimated",
            source = "https://snes.nesdev.org/wiki/Cartridge_connector https://consolemods.org/wiki/SNES:Connector_Pinouts https://allpinouts.org/pinouts/connectors/videogame/snes-cartridge/ https://nesdev.nes.science/f12/t9236.xhtml"),
        B("sfc", floatArrayOf(14f, 102f, 3.5f, 16.5f, 10f, 0f, 1.5f), floatArrayOf(35.5f, 59f, 10f, 1.2f, 1f), 0xFF2D6A3E.toInt(), floatArrayOf(3f, 23f, 2.5f, 1.7f, 7f, 37.5f, 1f), 0xFFD4A941.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb verified; contacts verified; ribs estimated",
            source = "https://ConsoleMods.org/wiki/Dimensions_for_Game_Cartridges https://consolemods.org/wiki/SNES:Region_Information http://www.classicplastic.net/dvgi/features-MODSSNESmod.htm https://patents.justia.com/patent/5192082"),
        B("np_sfc", floatArrayOf(14f, 102f, 3.5f, 16.5f, 10f, 0f, 1.5f), floatArrayOf(35.5f, 59f, 10f, 1.2f, 1f), 0xFF2D6A3E.toInt(), floatArrayOf(3f, 23f, 2.5f, 1.7f, 7f, 37.5f, 1f), 0xFFD4A941.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = true,
            pcb2Z = Float.NaN, check = "opening estimated; pcb verified; contacts verified; ribs estimated",
            source = "https://ConsoleMods.org/wiki/Dimensions_for_Game_Cartridges https://consolemods.org/wiki/SNES:Region_Information http://www.classicplastic.net/dvgi/features-MODSSNESmod.htm https://patents.justia.com/patent/5192082"),
        B("n64", floatArrayOf(22f, 72f, 5f, 13.5f, 10f, 0f, 1f), floatArrayOf(26f, 64f, 8.65f, 1.2f, 3f), 0xFF2F6B3A.toInt(), floatArrayOf(3f, 25f, 2.5f, 1.6f, 6f, 28f, 0.8f), 0xFFD4A941.toInt(),
            ribW = 0f, plate = null, bevel = 0.5f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb verified; contacts verified; ribs estimated",
            source = "https://www.ifixit.com/Guide/Disassembling+Nintendo+64+Cartridge/3583 https://n64brew.dev/wiki/Game_Pak?oldid=4389 https://n64brew.dev/wiki/NUS-006 https://www.pcbway.com/project/shareproject/SC64_an_open_source_Nintendo_64_flashcart_14b9688a.html"),
        B("gb", floatArrayOf(3.6f, 49.8f, 1.2f, 7.8f, 9f, 9f, 0.5f), floatArrayOf(3.85f, 49.3f, 2.4f, 0.8f, 1f), 0xFF2F6B3F.toInt(), floatArrayOf(2f, 32f, 1.5f, 1f, 6.3f, 5f, 0.7f), 0xFFC9A24A.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = true, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://raw.githubusercontent.com/djedditt/kicad-gamepaks/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_DMG-09_P1.50mm_Edge.kicad_mod https://github.com/djedditt/kicad-gamepaks https://github.com/Gekkio/gb-hardware https://github.com/MouseBiteLabs/Game-Boy-MBC5-Cartridge"),
        B("gbc_dual", floatArrayOf(3.6f, 49.8f, 1.2f, 9f, 9f, 9f, 0.5f), floatArrayOf(3.85f, 49.3f, 2.4f, 0.8f, 1f), 0xFF2F6B3F.toInt(), floatArrayOf(2f, 32f, 1.5f, 1f, 7f, 5f, 1f), 0xFFC9A24A.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = true, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://raw.githubusercontent.com/djedditt/kicad-gamepaks/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_CGB-002_P1.50mm_Edge.kicad_mod https://raw.githubusercontent.com/djedditt/kicad-gamepaks/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_DMG-09_P1.50mm_Edge.kicad_mod https://github.com/djedditt/kicad-gamepaks https://en.wikipedia.org/wiki/Game_Boy_Game_Pak"),
        B("gbc", floatArrayOf(3.6f, 49.8f, 1.2f, 9f, 9f, 9f, 0.5f), floatArrayOf(3.85f, 49.3f, 2.4f, 0.8f, 1f), 0xFF2F6B3F.toInt(), floatArrayOf(2f, 32f, 1.5f, 1f, 7f, 5f, 1f), 0xFFC9A24A.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = true, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://raw.githubusercontent.com/djedditt/kicad-gamepaks/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_CGB-002_P1.50mm_Edge.kicad_mod https://raw.githubusercontent.com/djedditt/kicad-gamepaks/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_DMG-09_P1.50mm_Edge.kicad_mod https://github.com/djedditt/kicad-gamepaks https://en.wikipedia.org/wiki/Game_Boy_Game_Pak"),
        B("gb_camera", floatArrayOf(3.6f, 49.8f, 1.2f, 7.8f, 9f, 9f, 0.5f), floatArrayOf(3.85f, 49.3f, 2.4f, 0.8f, 1f), 0xFF2F6B3F.toInt(), floatArrayOf(2f, 32f, 1.5f, 1f, 6.3f, 5f, 0.7f), 0xFFC9A24A.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = true, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://en.wikipedia.org/wiki/Game_Boy_Camera https://en.wikipedia.org/wiki/Game_Boy_Game_Pak https://raw.githubusercontent.com/djedditt/kicad-gamepaks/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_DMG-09_P1.50mm_Edge.kicad_mod https://github.com/djedditt/kicad-gamepaks"),
        B("pokemon_pinball", floatArrayOf(3.6f, 49.8f, 1.2f, 9f, 9f, 9f, 0.5f), floatArrayOf(3.85f, 49.3f, 2.4f, 0.8f, 1f), 0xFF2F6B3F.toInt(), floatArrayOf(2f, 32f, 1.5f, 1f, 7f, 5f, 1f), 0xFFC9A24A.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = true, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://en.wikipedia.org/wiki/Game_Boy_Game_Pak https://raw.githubusercontent.com/djedditt/kicad-gamepaks/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_CGB-002_P1.50mm_Edge.kicad_mod https://raw.githubusercontent.com/djedditt/kicad-gamepaks/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_DMG-09_P1.50mm_Edge.kicad_mod"),
        B("kirby_tilt", floatArrayOf(3.6f, 49.8f, 1.2f, 9f, 9f, 9f, 0.5f), floatArrayOf(3.85f, 49.3f, 2.4f, 0.8f, 1f), 0xFF2F6B3F.toInt(), floatArrayOf(2f, 32f, 1.5f, 1f, 7f, 5f, 1f), 0xFFC9A24A.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = true, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://en.wikipedia.org/wiki/Game_Boy_Game_Pak https://raw.githubusercontent.com/djedditt/kicad-gamepaks/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_CGB-002_P1.50mm_Edge.kicad_mod https://raw.githubusercontent.com/djedditt/kicad-gamepaks/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_DMG-09_P1.50mm_Edge.kicad_mod"),
        B("np_gb", floatArrayOf(3.6f, 49.8f, 1.2f, 9f, 9f, 9f, 0.5f), floatArrayOf(3.85f, 49.3f, 2.4f, 0.8f, 1f), 0xFF2F6B3F.toInt(), floatArrayOf(2f, 32f, 1.5f, 1f, 7f, 5f, 1f), 0xFFC9A24A.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = true, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://en.wikipedia.org/wiki/Nintendo_Power_(cartridge) https://raw.githubusercontent.com/djedditt/kicad-gamepaks/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_CGB-002_P1.50mm_Edge.kicad_mod https://raw.githubusercontent.com/djedditt/kicad-gamepaks/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_DMG-09_P1.50mm_Edge.kicad_mod"),
        B("gba", floatArrayOf(3.75f, 50f, 1f, 8f, 7.5f, 6.5f, 0.5f), floatArrayOf(4.1f, 49.3f, 3.2f, 0.8f, 1f), 0xFF6FA287.toInt(), floatArrayOf(2f, 32f, 1.5f, 1f, 5.4f, 5.25f, 1f), 0xFFC9A24A.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(0f, 2f, 7f, 4f, 8f, 55.5f, 57.5f, 7f, 4f, 8f), skip = intArrayOf(), wideEnds = true, foot = true, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://raw.githubusercontent.com/djedditt/kicad-gamepaks/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_AGB-002_P1.50mm_Edge.kicad_mod https://github.com/djedditt/kicad-gamepaks/blob/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_AGB-002_P1.50mm_Edge_Bootleg.kicad_mod https://github.com/djedditt/kicad-gamepaks https://www.pcbway.com/project/shareproject/AGB_E02_20_Game_Boy_Advance_Replacement_PCB_V2_a29fa89d.html"),
        B("warioware_twisted", floatArrayOf(3.75f, 50f, 1f, 8f, 7.5f, 6.5f, 0.5f), floatArrayOf(4.1f, 49.3f, 3.2f, 0.8f, 1f), 0xFF6FA287.toInt(), floatArrayOf(2f, 32f, 1.5f, 1f, 5.4f, 5.25f, 1f), 0xFFC9A24A.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(0f, 2f, 7f, 4f, 8f, 55.5f, 57.5f, 7f, 4f, 8f), skip = intArrayOf(), wideEnds = true, foot = true, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://gbhwdb.gekkio.fi/cartridges/AGB-RZWE-0/ikk5-1.html https://raw.githubusercontent.com/djedditt/kicad-gamepaks/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_AGB-002_P1.50mm_Edge.kicad_mod https://github.com/djedditt/kicad-gamepaks/blob/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_AGB-002_P1.50mm_Edge_Bootleg.kicad_mod"),
        B("boktai", floatArrayOf(3.75f, 50f, 1f, 8f, 7.5f, 6.5f, 0.5f), floatArrayOf(4.1f, 49.3f, 3.2f, 0.8f, 1f), 0xFF6FA287.toInt(), floatArrayOf(2f, 32f, 1.5f, 1f, 5.4f, 5.25f, 1f), 0xFFC9A24A.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(0f, 2f, 7f, 4f, 8f, 55.5f, 57.5f, 7f, 4f, 8f), skip = intArrayOf(), wideEnds = true, foot = true, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://en.wikipedia.org/wiki/Game_Boy_Game_Pak https://raw.githubusercontent.com/djedditt/kicad-gamepaks/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_AGB-002_P1.50mm_Edge.kicad_mod https://github.com/djedditt/kicad-gamepaks/blob/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_AGB-002_P1.50mm_Edge_Bootleg.kicad_mod"),
        B("drill_dozer", floatArrayOf(3.75f, 50f, 1f, 8f, 7.5f, 6.5f, 0.5f), floatArrayOf(4.1f, 49.3f, 3.2f, 0.8f, 1f), 0xFF6FA287.toInt(), floatArrayOf(2f, 32f, 1.5f, 1f, 5.4f, 5.25f, 1f), 0xFFC9A24A.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(0f, 2f, 7f, 4f, 8f, 55.5f, 57.5f, 7f, 4f, 8f), skip = intArrayOf(), wideEnds = true, foot = true, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://en.wikipedia.org/wiki/Game_Boy_Game_Pak https://raw.githubusercontent.com/djedditt/kicad-gamepaks/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_AGB-002_P1.50mm_Edge.kicad_mod https://github.com/djedditt/kicad-gamepaks/blob/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_AGB-002_P1.50mm_Edge_Bootleg.kicad_mod"),
        B("yoshi_topsy", floatArrayOf(3.75f, 50f, 1f, 8f, 7.5f, 6.5f, 0.5f), floatArrayOf(4.1f, 49.3f, 3.2f, 0.8f, 1f), 0xFF6FA287.toInt(), floatArrayOf(2f, 32f, 1.5f, 1f, 5.4f, 5.25f, 1f), 0xFFC9A24A.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(0f, 2f, 7f, 4f, 8f, 55.5f, 57.5f, 7f, 4f, 8f), skip = intArrayOf(), wideEnds = true, foot = true, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://en.wikipedia.org/wiki/Game_Boy_Game_Pak https://raw.githubusercontent.com/djedditt/kicad-gamepaks/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_AGB-002_P1.50mm_Edge.kicad_mod https://github.com/djedditt/kicad-gamepaks/blob/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_AGB-002_P1.50mm_Edge_Bootleg.kicad_mod"),
        B("ereader", floatArrayOf(3.75f, 50f, 1f, 8f, 7.5f, 6.5f, 0.5f), floatArrayOf(4.1f, 49.3f, 3.2f, 0.8f, 1f), 0xFF6FA287.toInt(), floatArrayOf(2f, 32f, 1.5f, 1f, 5.4f, 5.25f, 1f), 0xFFC9A24A.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(0f, 2f, 7f, 4f, 8f, 55.5f, 57.5f, 7f, 4f, 8f), skip = intArrayOf(), wideEnds = true, foot = true, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://en.wikipedia.org/wiki/Nintendo_e-Reader https://raw.githubusercontent.com/djedditt/kicad-gamepaks/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_AGB-002_P1.50mm_Edge.kicad_mod https://github.com/djedditt/kicad-gamepaks/blob/master/modules/Connector_GameBoy.pretty/GameBoy_GamePak_AGB-002_P1.50mm_Edge_Bootleg.kicad_mod"),
        B("ds", floatArrayOf(2.5f, 28f, 3.1f, 3.8f, 10f, 10f, 0.3f), floatArrayOf(1.5f, 30f, 2.3f, 0.8f, 0.5f), 0xFF2E6B3C.toInt(), floatArrayOf(2f, 17f, 1.5f, 1f, 4.5f, Float.NaN, 0.5f), 0xFFC9A24A.toInt(),
            ribW = 0.5f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts estimated; ribs verified",
            source = "https://patents.google.com/patent/US8016681B2/en https://www.3dbrew.org/wiki/Cards https://en.wikipedia.org/wiki/Nintendo_Game_Card https://consolemods.org/wiki/Dimensions_for_Game_Cartridges"),
        B("3ds", floatArrayOf(2.5f, 28f, 3.1f, 3.8f, 10f, 10f, 0.3f), floatArrayOf(1.5f, 30f, 2.3f, 0.8f, 0.5f), 0xFF2E6B3C.toInt(), floatArrayOf(2f, 17f, 1.5f, 1f, 4.5f, Float.NaN, 0.5f), 0xFFC9A24A.toInt(),
            ribW = 0.5f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts estimated; ribs verified",
            source = "https://www.3dbrew.org/wiki/Cards https://www.ifixit.com/Teardown/Nintendo+3DS+Game+Card+Teardown-How+to+Open/143740 https://en.wikipedia.org/wiki/Nintendo_Game_Card https://patents.google.com/patent/US8016681B2/en"),
        B("switch", floatArrayOf(1.8f, 17.4f, 2.5f, 3f, 10f, 10f, 0.5f), floatArrayOf(1f, 19f, 1.8f, 0.7f, 0.5f), 0xFF1F4A33.toInt(), floatArrayOf(2f, 17f, 1f, 0.75f, 3f, 2.5f, 0.5f), 0xFFC9A24A.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts estimated; ribs estimated",
            source = "https://switchbrew.org/wiki/Gamecard https://en.wikipedia.org/wiki/Nintendo_Game_Card https://www.nintendo.com/au/support/articles/how-to-insert-remove-game-cards-on-nintendo-switch/ https://commons.wikimedia.org/wiki/File:Nintendo_Switch_Gamecard_Rear.jpg"),
        B("switch2", floatArrayOf(1.8f, 17.4f, 2.5f, 3f, 10f, 10f, 0.5f), floatArrayOf(1f, 19f, 1.8f, 0.7f, 0.5f), 0xFF1F4A33.toInt(), floatArrayOf(2f, 17f, 1f, 0.75f, 3f, 2.5f, 0.5f), 0xFFC9A24A.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(0f, 1.5f, 1.5f, 2f, 3f), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts estimated; ribs estimated",
            source = "https://en.wikipedia.org/wiki/Nintendo_Game_Card https://en.wikipedia.org/wiki/Nintendo_Switch_2 https://press-start.com.au/news/nintendo/2025/05/20/heres-a-look-at-nintendo-switch-2s-game-cartridges-and-its-official-carry-case/ https://press-start.com.au/news/2025/04/15/the-nintendo-switch-2-game-carts-are-shaped-differently-to-the-og-carts/"),
        B("switch2_key", floatArrayOf(1.8f, 17.4f, 2.5f, 3f, 10f, 10f, 0.5f), floatArrayOf(1f, 19f, 1.8f, 0.7f, 0.5f), 0xFF1F4A33.toInt(), floatArrayOf(2f, 17f, 1f, 0.75f, 3f, 2.5f, 0.5f), 0xFFC9A24A.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(0f, 1.5f, 1.5f, 2f, 3f), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts estimated; ribs estimated",
            source = "https://en.wikipedia.org/wiki/Nintendo_Game_Card https://en.wikipedia.org/wiki/Nintendo_Switch_2 https://press-start.com.au/news/nintendo/2025/05/20/heres-a-look-at-nintendo-switch-2s-game-cartridges-and-its-official-carry-case/ https://press-start.com.au/news/2025/04/15/the-nintendo-switch-2-game-carts-are-shaped-differently-to-the-og-carts/"),
        B("genesis", floatArrayOf(11f, 87f, 4.3f, 12.7f, 10f, 0f, 1f), floatArrayOf(12.59f, 83.82f, 7.7f, 1.6f, 3f), 0xFF2E6B3C.toInt(), floatArrayOf(3f, 32f, 2.54f, 1.905f, 6.86f, 15.11f, 1f), 0xFFD4A94A.toInt(),
            ribW = 0f, plate = null, bevel = 0.5f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb verified; contacts verified; ribs estimated",
            source = "https://raw.githubusercontent.com/windsorschmidt/md-flashcart/main/pcb/md-flashcart.kicad_pcb https://www.pcbway.com/project/shareproject/Genesis_Mega_Drive_Advanced_Flash_Cartridge_83ab3d2d.html https://www.pcbway.com/project/shareproject/W53454ASI19_md_gerber.html https://forums.atariage.com/topic/271671-genesis-homebrew-pcbs-too-thick/"),
        B("megadrive_jp", floatArrayOf(3.5f, 86f, 4.3f, 12.7f, 10f, 0f, 1f), floatArrayOf(4.59f, 83.82f, 7.7f, 1.6f, 3f), 0xFF2E6B3C.toInt(), floatArrayOf(3f, 32f, 2.54f, 1.905f, 6.86f, 7.11f, 1f), 0xFFD4A94A.toInt(),
            ribW = 0f, plate = null, bevel = 0.5f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb verified; contacts verified; ribs estimated",
            source = "https://raw.githubusercontent.com/windsorschmidt/md-flashcart/main/pcb/md-flashcart.kicad_pcb https://www.pcbway.com/project/shareproject/Genesis_Mega_Drive_Advanced_Flash_Cartridge_83ab3d2d.html https://www.pcbway.com/project/shareproject/W53454ASI19_md_gerber.html https://forums.atariage.com/topic/271671-genesis-homebrew-pcbs-too-thick/"),
        B("genesis_ea", floatArrayOf(4f, 87f, 4.3f, 12.7f, 10f, 0f, 1f), floatArrayOf(5.59f, 83.82f, 7.7f, 1.6f, 3f), 0xFF2E6B3C.toInt(), floatArrayOf(3f, 32f, 2.54f, 1.905f, 6.86f, 8.11f, 1f), 0xFFD4A94A.toInt(),
            ribW = 0f, plate = null, bevel = 0.5f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb verified; contacts verified; ribs estimated",
            source = "https://raw.githubusercontent.com/windsorschmidt/md-flashcart/main/pcb/md-flashcart.kicad_pcb https://www.pcbway.com/project/shareproject/Genesis_Mega_Drive_Advanced_Flash_Cartridge_83ab3d2d.html https://www.pcbway.com/project/shareproject/W53454ASI19_md_gerber.html https://forums.atariage.com/topic/271671-genesis-homebrew-pcbs-too-thick/"),
        B("32x", floatArrayOf(12f, 87f, 3.8f, 12.2f, 10f, 0f, 1f), floatArrayOf(13.59f, 83.82f, 7.2f, 1.6f, 3f), 0xFF2E6B3C.toInt(), floatArrayOf(3f, 32f, 2.54f, 1.905f, 6.86f, 16.11f, 1f), 0xFFD4A94A.toInt(),
            ribW = 0f, plate = null, bevel = 0.5f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb verified; contacts estimated; ribs estimated",
            source = "https://raw.githubusercontent.com/windsorschmidt/md-flashcart/main/pcb/md-flashcart.kicad_pcb https://www.pcbway.com/project/shareproject/Genesis_Mega_Drive_Advanced_Flash_Cartridge_83ab3d2d.html https://www.pcbway.com/project/shareproject/W53454ASI19_md_gerber.html https://forums.atariage.com/topic/271671-genesis-homebrew-pcbs-too-thick/"),
        B("virtua_racing", floatArrayOf(11f, 87f, 4.3f, 12.7f, 10f, 0f, 1f), floatArrayOf(12.59f, 83.82f, 7.7f, 1.6f, 3f), 0xFF2E6B3C.toInt(), floatArrayOf(3f, 32f, 2.54f, 1.905f, 6.86f, 15.11f, 1f), 0xFFD4A94A.toInt(),
            ribW = 0f, plate = null, bevel = 0.5f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb verified; contacts verified; ribs estimated",
            source = "https://raw.githubusercontent.com/windsorschmidt/md-flashcart/main/pcb/md-flashcart.kicad_pcb https://www.pcbway.com/project/shareproject/Genesis_Mega_Drive_Advanced_Flash_Cartridge_83ab3d2d.html https://www.pcbway.com/project/shareproject/W53454ASI19_md_gerber.html https://forums.atariage.com/topic/271671-genesis-homebrew-pcbs-too-thick/"),
        B("sonic_knuckles", floatArrayOf(17.32f, 87f, 6.8f, 15.2f, 10f, 0f, 1f), floatArrayOf(18.92f, 83.82f, 10.2f, 1.6f, 3f), 0xFF2E6B3C.toInt(), floatArrayOf(3f, 32f, 2.54f, 1.905f, 6.86f, 21.44f, 1f), 0xFFD4A94A.toInt(),
            ribW = 0f, plate = null, bevel = 0.5f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb verified; contacts verified; ribs estimated",
            source = "https://raw.githubusercontent.com/windsorschmidt/md-flashcart/main/pcb/md-flashcart.kicad_pcb https://www.pcbway.com/project/shareproject/Genesis_Mega_Drive_Advanced_Flash_Cartridge_83ab3d2d.html https://www.pcbway.com/project/shareproject/W53454ASI19_md_gerber.html https://forums.atariage.com/topic/271671-genesis-homebrew-pcbs-too-thick/"),
        B("codemasters", floatArrayOf(5.5f, 87f, 4.3f, 12.7f, 10f, 0f, 1f), floatArrayOf(7.09f, 83.82f, 7.7f, 1.6f, 3f), 0xFF2E6B3C.toInt(), floatArrayOf(3f, 32f, 2.54f, 1.905f, 6.86f, 9.61f, 1f), 0xFFD4A94A.toInt(),
            ribW = 0f, plate = null, bevel = 0.5f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb verified; contacts verified; ribs estimated",
            source = "https://raw.githubusercontent.com/windsorschmidt/md-flashcart/main/pcb/md-flashcart.kicad_pcb https://www.pcbway.com/project/shareproject/Genesis_Mega_Drive_Advanced_Flash_Cartridge_83ab3d2d.html https://www.pcbway.com/project/shareproject/W53454ASI19_md_gerber.html https://forums.atariage.com/topic/271671-genesis-homebrew-pcbs-too-thick/"),
        B("jcart", floatArrayOf(5.5f, 87f, 4.3f, 12.7f, 10f, 0f, 1f), floatArrayOf(7.09f, 83.82f, 7.7f, 1.6f, 3f), 0xFF2E6B3C.toInt(), floatArrayOf(3f, 32f, 2.54f, 1.905f, 6.86f, 9.61f, 1f), 0xFFD4A94A.toInt(),
            ribW = 0f, plate = null, bevel = 0.5f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb verified; contacts verified; ribs estimated",
            source = "https://raw.githubusercontent.com/windsorschmidt/md-flashcart/main/pcb/md-flashcart.kicad_pcb https://www.pcbway.com/project/shareproject/Genesis_Mega_Drive_Advanced_Flash_Cartridge_83ab3d2d.html https://www.pcbway.com/project/shareproject/W53454ASI19_md_gerber.html https://forums.atariage.com/topic/271671-genesis-homebrew-pcbs-too-thick/"),
        B("sms", floatArrayOf(19.55f, 70f, 4.65f, 12.25f, 10f, 0f, 1f), floatArrayOf(21.65f, 65.8f, 7.65f, 1.6f, 3f), 0xFF2E6B3C.toInt(), floatArrayOf(3f, 25f, 2.54f, 1.7f, 8.5f, 24.07f, 1.25f), 0xFFD4A94A.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb verified; contacts verified; ribs estimated",
            source = "https://raw.githubusercontent.com/barbeque/sms-u-simple-cartridge/main/sms-cartridge-basic.kicad_pcb https://consolemods.org/wiki/Master_System:Connector_Pinouts https://www.hardwarebook.info/Master_System_Cartridge https://www.chrismcovell.com/segahacking/segaadaptor.html"),
        B("sms_jp", floatArrayOf(4.15f, 76f, 6.25f, 14.85f, 10f, 0f, 1f), floatArrayOf(6.15f, 72f, 9.75f, 1.6f, 2f), 0xFF2E6B3C.toInt(), floatArrayOf(3f, 22f, 3.175f, 2.2f, 8f, 8.81f, 1f), 0xFFD4A94A.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://consolemods.org/wiki/Master_System:Connector_Pinouts https://consolemods.org/wiki/SG-1000:Connector_Pinouts https://consolemods.org/wiki/Master_System:171-5304_Board_Info https://www.raphnet.net/electronique/markIII_to_smd/index_en.php"),
        B("sg1000", floatArrayOf(4.15f, 76f, 6.25f, 14.85f, 10f, 0f, 1f), floatArrayOf(6.15f, 72f, 9.75f, 1.6f, 2f), 0xFF2E6B3C.toInt(), floatArrayOf(3f, 22f, 3.175f, 2.2f, 8f, 8.81f, 1f), 0xFFD4A94A.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://consolemods.org/wiki/Master_System:Connector_Pinouts https://consolemods.org/wiki/SG-1000:Connector_Pinouts https://consolemods.org/wiki/Master_System:171-5304_Board_Info https://www.raphnet.net/electronique/markIII_to_smd/index_en.php"),
        B("game_gear", floatArrayOf(3f, 60f, 2.4f, 7.6f, 7f, 0f, 0.8f), floatArrayOf(2f, 62f, 4.4f, 1.2f, 2f), 0xFF2E6B3C.toInt(), floatArrayOf(2f, 45f, 1.27f, 0.8f, 5f, 5.06f, 0.8f), 0xFFD4A94A.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://consolemods.org/wiki/images/2/24/Game_Gear_VA1_Service_Manual.pdf https://www.hardwarebook.info/Game_Gear_Cartridge https://www.smspower.org/forums/18337-WhereToFindGameGearCartridgeConnectors https://community.element14.com/challenges-projects/element14-presents/f/forum/11046/master-gear-game-gear-pinout"),
        B("atari2600", floatArrayOf(20f, 42f, 4.5f, 15.5f, 14f, 0f, 1f), floatArrayOf(20.2f, 41.6f, 9.25f, 1.5f, 3f), 0xFF2E6B3F.toInt(), floatArrayOf(3f, 12f, 2.54f, 1.8f, 7f, 27.03f, 0.5f), 0xFFD4AF37.toInt(),
            ribW = 0f, plate = floatArrayOf(1.5f, 8.5f, 11.5f), bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://ConsoleMods.org/wiki/Atari_2600:Connector_Pinouts https://forums.atariage.com/topic/281211-replacement-2600-connector/ https://forums.atariage.com/topic/216297-7800-asteroids-cart-w2600-style-spring-cover/ https://atariage.com/forums/topic/291443-internal-dust-sleeve-in-atari-carts-really-needed/"),
        B("atari2600_activision", floatArrayOf(19f, 43f, 5f, 15f, 14f, 0f, 1f), floatArrayOf(19.85f, 41.3f, 9.25f, 1.5f, 2f), 0xFF2E6B3F.toInt(), floatArrayOf(3f, 12f, 2.54f, 1.8f, 7f, 26.53f, 0.5f), 0xFFD4AF37.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://oshpark.com/shared_projects/fqTcNFkn https://atariage.com/forums/topic/291443-internal-dust-sleeve-in-atari-carts-really-needed/ https://forums.atariage.com/topic/281211-replacement-2600-connector/"),
        B("atari5200", floatArrayOf(24f, 56f, 4.5f, 15.5f, 15f, 0f, 1f), floatArrayOf(25.5f, 53f, 9.25f, 1.6f, 3f), 0xFF2E6B3F.toInt(), floatArrayOf(3f, 18f, 2.54f, 1.8f, 7.5f, 30.41f, 0.5f), 0xFFD4AF37.toInt(),
            ribW = 0f, plate = floatArrayOf(1.5f, 8.9f, 11.2f), bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://consolemods.org/wiki/Atari_5200:Connector_Pinouts https://de.ifixit.com/Anleitung/Atari+5200+Game+Cartridge+Disassembly/30264?lang=en https://ConsoleMods.org/wiki/Dimensions_for_Game_Cartridges"),
        B("atari7800", floatArrayOf(16.5f, 48f, 4.5f, 14.5f, 13f, 0f, 1f), floatArrayOf(17.57f, 45.85f, 8.75f, 1.5f, 2f), 0xFF2E6B3F.toInt(), floatArrayOf(3f, 18f, 2.54f, 2f, 7f, 18.91f, 0.5f), 0xFFD4AF37.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(22.79f, 2.4f, 9.7f, 55.81f, 2.4f, 9.7f), notches = floatArrayOf(), skip = intArrayOf(3, 16), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb verified; contacts verified; ribs estimated",
            source = "https://forums.atariage.com/topic/348171-atari-7800-edge-connector-spacing-pad-geometry-pcb-thickness-place-for-cuts/ https://consolemods.org/wiki/Atari_7800:Connector_Pinouts https://forums.atariage.com/topic/216297-7800-asteroids-cart-w2600-style-spring-cover/ https://ConsoleMods.org/wiki/Dimensions_for_Game_Cartridges"),
        B("jaguar", floatArrayOf(4f, 100f, 5f, 14.5f, 12f, 0f, 1f), floatArrayOf(5f, 98f, 9f, 1.6f, 1f), 0xFF2E6B3F.toInt(), floatArrayOf(3f, 54f, 1.8f, 1.2f, 6f, 6.3f, 0.5f), 0xFFD4AF37.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(13, 14), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts estimated; ribs estimated",
            source = "https://consolemods.org/wiki/Jaguar:Connector_Pinouts https://forums.atariage.com/topic/194422-cartridge-slot/ https://forums.atariage.com/topic/347610-6mb-cartridge-diy-options/ https://atariage.com/forums/topic/274099-jaguar-cartridge-port-riser/"),
        B("neogeo_aes", floatArrayOf(25f, 140.5f, 5f, 26.75f, 14f, 0f, 1.5f), floatArrayOf(27f, 136.5f, 9f, 1.6f, 2f), 0xFF2E6B3F.toInt(), floatArrayOf(3f, 50f, 2.54f, 1.8f, 7f, Float.NaN, 0.5f), 0xFFD4AF37.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = 20.5f, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://wiki.neogeodev.org/index.php/AES_cartridge_pinout https://wiki.neogeodev.org/index.php/AES_cartridge https://wiki.neogeodev.org/index.php/Cartridges https://ConsoleMods.org/wiki/Dimensions_for_Game_Cartridges"),
        B("neogeo_mvs", floatArrayOf(13f, 159f, 4f, 22f, 12f, 0f, 1f), floatArrayOf(14.5f, 156f, 7f, 1.6f, 1f), 0xFF2E6B3F.toInt(), floatArrayOf(3f, 60f, 2.54f, 1.8f, 7f, Float.NaN, 0.5f), 0xFFD4AF37.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = 17.4f, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://wiki.neogeodev.org/index.php/MVS_cartridge_pinout https://wiki.neogeodev.org/index.php/MVS_cartridge"),
        B("ngp", floatArrayOf(10f, 27f, 1.5f, 5f, 9f, 0f, 0.5f), floatArrayOf(10.5f, 26f, 2.5f, 1.2f, 0.5f), 0xFF2E6B3F.toInt(), floatArrayOf(3f, 18f, 1.27f, 1.016f, 7.62f, Float.NaN, 0.5f), 0xFFD4AF37.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts estimated; ribs estimated",
            source = "https://github.com/Xyl2k/Neo-Geo-Pocket-flash-cartridge/wiki/connector-dims https://www.flashmasta.com/how-to-make-a-videogame-cartridge-connector-for-neo-geo-pocket-or-gameboy-nes-snes-genesis-etc/ https://dumping.guide/_export/raw/todo/carts/snk/neo-geo-pocket"),
        B("colecovision", floatArrayOf(17.5f, 46f, 5f, 14f, 12f, 0f, 1f), floatArrayOf(19f, 43f, 8.75f, 1.6f, 1f), 0xFF2E6B3F.toInt(), floatArrayOf(3f, 15f, 2.54f, 1.8f, 7f, Float.NaN, 0.5f), 0xFFD4AF37.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://consolemods.org/wiki/ColecoVision:Connector_Pinouts https://ConsoleMods.org/wiki/Dimensions_for_Game_Cartridges"),
        B("intellivision", floatArrayOf(4f, 60f, 4f, 12f, 10f, 0f, 0.8f), floatArrayOf(5f, 58f, 7.2f, 1.6f, 0f), 0xFF2E6B3F.toInt(), floatArrayOf(3f, 22f, 2.54f, 1.8f, 7f, Float.NaN, 0.5f), 0xFFD4AF37.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts verified; ribs estimated",
            source = "https://wiki.intellivision.us/index.php/Cartridge_Port https://intvfunhouse.com/tech/cartport/ https://www.atariage.com/forums/topic/185361-how-do-you-open-an-intellivision-cart-shell/ https://www.printables.com/model/499445-intellivision-cartridge-shell"),
        B("vectrex", floatArrayOf(12f, 50.5f, 8f, 15f, 10f, 0f, 0.8f), floatArrayOf(13f, 48.5f, 10.6f, 1.6f, 0f), 0xFF2E6B3F.toInt(), floatArrayOf(3f, 18f, 2.54f, 1.8f, 7f, Float.NaN, 0.5f), 0xFFD4AF37.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts estimated; ribs estimated",
            source = "https://harmlesslion.com/text/vectrex_multicart.htm https://ConsoleMods.org/wiki/Dimensions_for_Game_Cartridges"),
        B("odyssey2", floatArrayOf(8f, 64f, 5f, 16f, 12f, 0f, 1f), floatArrayOf(9f, 62f, 9.7f, 1.57f, 1f), 0xFF2E6B3F.toInt(), floatArrayOf(3f, 15f, 3.96f, 2.6f, 9f, Float.NaN, 0.5f), 0xFFC8C8C0.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts estimated; ribs estimated",
            source = "https://www.odyssey2.info/articles/cartread/ https://www.chipdip.ru/product0/8004980822 https://odyssey2.info/jgmunchkin/make.php https://git.redump.net/mame/tree/src/devices/bus/odyssey2/slot.cpp"),
        B("channelf", floatArrayOf(30f, 42f, 5f, 17f, 12f, 0f, 1f), floatArrayOf(31f, 40f, 10.2f, 1.6f, 4f), 0xFFB87333.toInt(), floatArrayOf(3f, 11f, 3.96f, 2.5f, 8f, Float.NaN, 0.5f), 0xFFC8C8C0.toInt(),
            ribW = 0f, plate = floatArrayOf(1f, 0f, 0f), bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts estimated; ribs estimated",
            source = "https://channelf.se/veswiki/index.php?title=Pinouts https://3dgo.app/models/printables/1507542 https://nicole.express/2021/the-most-unfair-child.html https://en.wikipedia.org/wiki/Fairchild_Channel_F_Videocarts"),
        B("gx4000", floatArrayOf(24f, 52f, 3.5f, 10.5f, 10f, 0f, 0.8f), floatArrayOf(25f, 50f, 6.2f, 1.6f, 1f), 0xFF2E6B3F.toInt(), floatArrayOf(3f, 18f, 2.54f, 1.8f, 7f, Float.NaN, 0.5f), 0xFFD4AF37.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts estimated; ribs estimated",
            source = "https://www.cpcwiki.eu/index.php/Connector:Cartridge_Slot_(CPC_Plus_only) https://cpcwiki.eu/index.php/Cartridge"),
        B("pv1000", floatArrayOf(20f, 50f, 4.5f, 11.5f, 12f, 0f, 0.8f), floatArrayOf(20.87f, 48.26f, 7.2f, 1.6f, 1f), 0xFF2E6B3F.toInt(), floatArrayOf(3f, 18f, 2.54f, 1.27f, 10.16f, 23.41f, 0.5f), 0xFFD4AF37.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(47.04f, 1f, 8f), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb verified; contacts verified; ribs estimated",
            source = "https://obscure.nesdev.org/wiki/Casio_PV-1000/Cartridge_connector"),
        B("supervision", floatArrayOf(3f, 46f, 4f, 8f, 5f, 4f, 0.5f), floatArrayOf(3.5f, 45f, 5.5f, 1.2f, 0.5f), 0xFF2E6B3F.toInt(), floatArrayOf(2f, 40f, 1.1f, 0.7f, 4f, Float.NaN, 0.5f), 0xFFD4AF37.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts estimated; ribs estimated",
            source = "https://www.vincenzoscarpa.it/biblioteca/retrofaq/megaduck.html https://retrohandhelds.gg/game-over-no-supervision/ https://www.pcbway.com/project/shareproject/Watara_Supervision_flash_cart_Superpico_5c2a9747.html"),
        B("megaduck", floatArrayOf(4f, 54f, 3.5f, 8f, 5f, 4f, 0.5f), floatArrayOf(4.5f, 53f, 5.2f, 1.2f, 0.5f), 0xFF2E6B3F.toInt(), floatArrayOf(2f, 36f, 1.4f, 0.9f, 4f, Float.NaN, 0.5f), 0xFFD4AF37.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb estimated; contacts estimated; ribs estimated",
            source = "https://www.vincenzoscarpa.it/biblioteca/retrofaq/megaduck.html https://github.com/zwenergy/picoDuck https://github.com/bbbbbr/megaduck-info https://github.com/bbbbbr/megaduck_cartridge_shell"),
        B("gamecom", floatArrayOf(5.5f, 27f, 5f, 8f, 6f, 0f, 0.5f), floatArrayOf(6f, 26f, 6f, 1f, 0.5f), 0xFF2E6B3F.toInt(), floatArrayOf(3f, 20f, 1.27f, 0.8f, 4f, Float.NaN, 0.5f), 0xFFD4AF37.toInt(),
            ribW = 0f, plate = null, bevel = 0f, slots = floatArrayOf(), notches = floatArrayOf(), skip = intArrayOf(), wideEnds = false, foot = false, centre = false,
            pcb2Z = Float.NaN, check = "opening estimated; pcb verified; contacts estimated; ribs estimated",
            source = "https://github.com/zwenergy/game.flash https://www.digikey.com/en/products/detail/sullins-connector-solutions/RBB25DHHN/927332 https://en.wikipedia.org/wiki/Tiger_Game.com")
    ).associateBy { it.id }

    /** Shells left with their plain edge on purpose, and why (CARTRIDGES.md lists them too). */
    val none: Map<String, String> = mapOf(
        "vb" to "the dust cap over its connector is part of the drawn shell (73.74 mm with the cap): its plain bottom is the cap",
        "fds" to "a disk: no connector",
        "64dd" to "a disk: no connector",
        "microvision" to "contacts under a door on the back near the top; the bottom is plain plastic",
        "sega_card" to "flat pads flush with the card face: nothing to model in 3D",
        "hucard" to "flat pads flush with the card: nothing to model in 3D",
        "lynx" to "flat fingers flush with the card: nothing to model in 3D",
        "ngage" to "MMC: flat pads flush with the card",
        "pokemini" to "only the pin count was found",
        "scv" to "no description of its connector end found",
        "loopy" to "no description of its connector end found",
        "evercade" to "no description of its connector end found",
        "wonderswan" to "its 48 × 1.25 mm edge needs ~61 mm but the drawn shell is a 52 mm placeholder"
    )

    /** [s]'s bottom, or null: nothing researched to model — the piece keeps its plain edge. */
    fun of(s: CartShell?): CartBottom? = s?.let { all[it.id] }

    /** The x range (mm) of [s]'s lowest edge (the outline's lowest points): an N64's or a Genesis's narrower foot. */
    fun span(s: CartShell): FloatArray {
        val v = s.outline
        var maxY = -1f
        var i = 0
        while (i + 2 < v.size) { maxY = maxOf(maxY, v[i + 1]); i += 3 }
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
        i = 0
        while (i + 2 < v.size) {
            if (v[i + 1] >= maxY - 0.01f) { lo = minOf(lo, v[i]); hi = maxOf(hi, v[i]) }
            i += 3
        }
        return if (hi > lo) floatArrayOf(lo, hi) else floatArrayOf(0f, s.w)
    }

    // ---- The geometry, worked out once per shell (mm) ----

    /** A box cut into the shell from its bottom: x0..x1, 0..y1, z0..z1; which other faces it opens through. */
    internal class Hole(
        val x0: Float, val x1: Float, val y1: Float, val z0: Float, val z1: Float,
        val back: Boolean, val backH: Float, val front: Boolean, val left: Boolean, val right: Boolean
    )
    /** A solid box inside the opening (a board, a plate, a rib); [bevel] on its bottom edge (boards). */
    internal class Solid(val x0: Float, val x1: Float, val y0: Float, val y1: Float, val z0: Float, val z1: Float, val argb: Int, val bevel: Float = 0f)
    /** A contact finger lying on a board's face at z ([back]: facing the back, else the front). */
    internal class Finger(val x0: Float, val x1: Float, val y0: Float, val y1: Float, val z: Float, val back: Boolean)

    internal class Geo(val main: Hole, val notches: List<Hole>, val boards: List<Solid>, val fingers: List<Finger>, val fingerArgb: Int, val after: List<Solid>, val footL: Float, val footR: Float)

    private val geos = HashMap<String, Geo?>()

    /** [s]'s connector end as boxes and fingers (cached), clamped inside its foot and thickness; null: plain edge. */
    /** Update 276: which real shell's connector end a piece of your pack gets, and where that shell's frame sits on it. */
    class PackFit(val shellId: String, val frame: FloatArray)

    /**
     * Update 276 (Toni: "add them to custom cartridges as well"): your pack's cartridge shapes are drawn after the real
     * cartridges, so each gets that cartridge's connector end. [frame]: the shell's box in the pack picture (left, top,
     * right, bottom as shares of the picture) — measured from the pack's pictures (the same for every console and both
     * finishes: a clear margin of 1.5–5 % round each). The Genesis shape is narrower than the real cartridge: its frame
     * is set so the real bottom tab (6.4–93.6 % of its width) lands on the pack's tab (12.3–87.7 %).
     * The PS Vita and HuCard shapes have flat contacts (nothing to show in 3D): none.
     */
    fun packFit(look: CartLook): PackFit? = when (look) {
        CartLook.GB -> PackFit("gb", floatArrayOf(0.033f, 0.029f, 0.967f, 0.971f))
        CartLook.N64 -> PackFit("n64", floatArrayOf(0.017f, 0.022f, 0.983f, 0.975f))
        CartLook.SNES -> PackFit("snes_na", floatArrayOf(0.014f, 0.023f, 0.986f, 0.978f))
        CartLook.GBA -> PackFit("gba", floatArrayOf(0.031f, 0.051f, 0.969f, 0.949f))
        CartLook.NES -> PackFit("nes", floatArrayOf(0.016f, 0.015f, 0.984f, 0.985f))
        CartLook.FAMICOM -> PackFit("famicom", floatArrayOf(0.018f, 0.027f, 0.982f, 0.973f))
        // (Update 307: the Genesis shape now draws the real cartridge's front, at its own proportions — wider than the
        // pack picture's shell — so its box is the real shell fitted across the picture, standing on the same bottom
        // edge; the connector end follows it.)
        CartLook.GENESIS -> PackFit("genesis", floatArrayOf(0.015f, 0.1033f, 0.985f, 0.973f))
        CartLook.GAME_GEAR -> PackFit("game_gear", floatArrayOf(0.029f, 0.028f, 0.971f, 0.972f))
        CartLook.NDS -> PackFit("ds", floatArrayOf(0.029f, 0.027f, 0.971f, 0.973f))
        CartLook.N3DS -> PackFit("3ds", floatArrayOf(0.028f, 0.027f, 0.973f, 0.973f))
        CartLook.SWITCH -> PackFit("switch", floatArrayOf(0.044f, 0.030f, 0.956f, 0.970f))
        CartLook.ATARI -> PackFit("atari2600", floatArrayOf(0.023f, 0.020f, 0.977f, 0.980f))
        CartLook.VITA, CartLook.HUCARD -> null
    }

    /**
     * Update 276: your optical-system pack's shells (a disc console's game as a cartridge: PS1, GameCube, Steam…) are
     * made up — no real cartridge to copy — so they get a plain cartridge-style edge connector: the Mega Drive's
     * (an opening across ~80 % of the bottom, the board inside, gold fingers both sides). The pictures are cut to the shell.
     */
    val opticalFit = PackFit("genesis", floatArrayOf(0f, 0f, 1f, 1f))

    fun geo(s: CartShell): Geo? = synchronized(geos) { geos.getOrPut(s.id) { runCatching { build(s) }.getOrNull() } }

    private fun build(s: CartShell): Geo? {
        val b = of(s) ?: return null
        val t = s.t
        val sp = span(s)
        val footL = sp[0]; val footR = sp[1]
        val o = b.open
        val shift = when {
            b.centre -> (footL + footR) / 2f - (o[0] + o[1] / 2f)
            b.foot -> footL
            else -> 0f
        }
        val wall = 0.6f
        val x0 = (o[0] + shift).coerceAtLeast(footL + wall)
        val x1 = (o[0] + o[1] + shift).coerceAtMost(footR - wall)
        if (x1 - x0 < 1f) return null
        val backH = o[5]
        val openBack = backH > 0f && o[3] >= t - 0.05f
        val z0 = o[2].coerceIn(0.3f, t - 0.4f)
        val z1 = if (openBack) t else o[3].coerceIn(z0 + 0.3f, t - 0.3f)
        val y1 = o[4].coerceIn(0.5f, s.h * 0.45f)
        val main = Hole(x0, x1, y1, z0, z1, openBack, minOf(backH, y1), false, false, false)
        val notches = ArrayList<Hole>()
        var i = 0
        while (i + 4 < b.notches.size) {
            val nx0 = (b.notches[i] + shift).coerceAtLeast(footL); val nx1 = (b.notches[i + 1] + shift).coerceAtMost(footR)
            val nz0 = b.notches[i + 3].coerceIn(0f, t); val nz1 = b.notches[i + 4].coerceIn(0f, t)
            val ny1 = b.notches[i + 2].coerceIn(0.3f, s.h * 0.4f)
            if (nx1 - nx0 > 0.2f && nz1 - nz0 > 0.2f) notches += Hole(
                nx0, nx1, ny1, nz0, nz1, back = nz1 >= t - 0.05f, backH = ny1, front = nz0 <= 0.05f,
                left = nx0 <= footL + 0.05f, right = nx1 >= footR - 0.05f
            )
            i += 5
        }
        val boards = ArrayList<Solid>()
        val fingers = ArrayList<Finger>()
        val after = ArrayList<Solid>()
        val p = b.pcb
        // The plastic bits inside: a little lighter than the walls (they catch more light), in the shell's own colour.
        if (p != null) {
            val px0 = maxOf(p[0] + shift, x0); val px1 = minOf(p[0] + p[1] + shift, x1)
            val pt = p[3].coerceIn(0.3f, 3f)
            val ey = p[4].coerceIn(0f, y1 - 0.5f)
            val bev = b.bevel.coerceAtMost(pt / 2f - 0.05f).coerceAtLeast(0f)
            val boardZs = listOf(p[2]) + (if (b.pcb2Z.isNaN()) emptyList() else listOf(b.pcb2Z))
            for (bz in boardZs) {
                // (Cut to the opening: a card's board is the pocket's floor — only its back face shows.)
                var pz0 = maxOf(bz, z0); var pz1 = minOf(bz + pt, z1)
                if (pz1 < pz0) { val zz = if (bz + pt <= z0) z0 else z1; pz0 = zz; pz1 = zz }
                if (px1 - px0 < 0.5f) continue
                // Split round the key slots: the lower band in pieces between them, the rest whole above.
                val slotTop = run { var m = 0f; var k = 0; while (k + 2 < b.slots.size) { m = maxOf(m, b.slots[k + 2]); k += 3 }; m }
                if (slotTop > 0f) {
                    val cuts = ArrayList<FloatArray>()
                    var k = 0
                    while (k + 2 < b.slots.size) { cuts += floatArrayOf(b.slots[k] + shift, b.slots[k] + b.slots[k + 1] + shift); k += 3 }
                    cuts.sortBy { it[0] }
                    var from = px0
                    for (c in cuts) { if (c[0] > from) boards += Solid(from, minOf(c[0], px1), ey, ey + slotTop, pz0, pz1, b.pcbColour, bev); from = maxOf(from, c[1]) }
                    if (from < px1) boards += Solid(from, px1, ey, ey + slotTop, pz0, pz1, b.pcbColour, bev)
                    boards += Solid(px0, px1, ey + slotTop, y1, pz0, pz1, b.pcbColour)
                } else boards += Solid(px0, px1, ey, y1, pz0, pz1, b.pcbColour, bev)
                // The fingers.
                val c = b.contacts
                val sides = c[0].toInt(); val n = c[1].toInt(); val pitch = c[2]
                if (sides != 0 && n > 0 && pitch > 0f) {
                    val fw = c[3].coerceAtMost(pitch * 0.92f)
                    val first = if (c[5].isNaN()) (p[0] + shift) + (p[1] - (n - 1) * pitch) / 2f else c[5] + shift
                    val fy0 = ey + c[6].coerceAtLeast(0f) + (if (bev > 0f) bev else 0f)
                    val fy1 = (fy0 + c[4]).coerceAtMost(y1)
                    for (j in 0 until n) {
                        if ((j + 1) in b.skip) continue
                        val cx = first + j * pitch
                        val w = if (b.wideEnds && (j == 0 || j == n - 1)) fw * 1.5f else fw
                        val l = cx - w / 2f; val r = cx + w / 2f
                        if (l < px0 || r > px1 || fy1 <= fy0) continue
                        if (sides and 1 != 0) fingers += Finger(l, r, fy0, fy1, pz0, back = false)
                        if (sides and 2 != 0) fingers += Finger(l, r, fy0, fy1, pz1, back = true)
                    }
                    // Ribs between the contacts, from the floor (the board's back face) up to the back face.
                    if (b.ribW > 0f && openBack) {
                        for (j in 0 until n - 1) {
                            val rx = first + (j + 0.5f) * pitch
                            val l = rx - b.ribW / 2f; val r = rx + b.ribW / 2f
                            if (l < x0 || r > x1) continue
                            after += Solid(l, r, 0f, y1, pz1, t, 0, 0f)
                        }
                    }
                }
            }
        }
        // A dust plate / door just inside the opening (with the board's slit through it).
        b.plate?.let { pl ->
            val py = pl[0].coerceIn(0f, y1 - 0.9f)
            val sz0 = pl[1]; val sz1 = pl[2]
            if (sz1 > sz0) {
                if (sz0 > z0) after += Solid(x0 + 0.3f, x1 - 0.3f, py, py + 0.8f, z0, sz0, 0)
                if (sz1 < z1) after += Solid(x0 + 0.3f, x1 - 0.3f, py, py + 0.8f, sz1, z1, 0)
            } else after += Solid(x0 + 0.3f, x1 - 0.3f, py, py + 0.8f, z0, z1, 0)
        }
        return Geo(main, notches, boards, fingers, b.contactColour, after, footL, footR)
    }

    // ---- Drawing ----

    /** The view: where a point (mm) lands on screen, how a face (its outward normal, mm axes) faces you and how it's lit. */
    internal class View(
        s: CartShell, nodeW: Float, nodeH: Float, depthPx: Float,
        private val cy: Float, private val sy: Float, private val cp: Float, private val sp: Float,
        /** The shell's frame stretched over the whole node (a photo / template at its own, slightly different shape). */
        stretch: Boolean = false,
        /**
         * Update 276: the shell's frame stretched over this part of the node instead (left, top, right, bottom as shares
         * of the node) — your pack's cartridge pictures have a clear margin round the shell ([packFit]).
         */
        frame: FloatArray? = null
    ) {
        private val pw = if (frame != null) nodeW * (frame[2] - frame[0]) else if (stretch) nodeW else minOf(nodeW, nodeH * s.aspect)
        private val ph = if (frame != null) nodeH * (frame[3] - frame[1]) else if (stretch) nodeH else pw / s.aspect
        private val left = if (frame != null) nodeW * frame[0] else (nodeW - pw) / 2f
        private val bottom = if (frame != null) nodeH * frame[3] else (nodeH + ph) / 2f
        private val q = pw / s.w
        private val qy = ph / s.h
        private val zk = depthPx / s.t.coerceAtLeast(0.01f)
        private val half = depthPx / 2f
        private val px = nodeW / 2f
        private val py = nodeH / 2f
        val t = s.t
        fun x(xm: Float, ym: Float, zm: Float): Float {
            val xn = left + xm * q - px; val zn = half - zm * zk
            return px + xn * cy + zn * sy
        }
        fun y(xm: Float, ym: Float, zm: Float): Float {
            val xn = left + xm * q - px; val yn = bottom - ym * qy - py; val zn = half - zm * zk
            return py + yn * cp - xn * sy * sp + zn * cy * sp
        }
        /** How much a face with outward normal (nx, ny up, nz towards the back) faces you (> 0: it does). */
        fun facing(nx: Float, ny: Float, nz: Float): Float = -nx * sy * cp + ny * sp - nz * cy * cp
        /** Its light: 0.42 in shadow … 1 facing the light (from the upper left, in front). */
        fun light(nx: Float, ny: Float, nz: Float): Float {
            // In node axes (y down, z towards you): (nx, −ny, −nz); turned like the piece.
            val ax = nx; val ay = -ny; val az = -nz
            val vx = cy * ax + sy * az
            val vy = -sy * sp * ax + cp * ay + cy * sp * az
            val vz = -sy * cp * ax - sp * ay + cy * cp * az
            val len = sqrt(vx * vx + vy * vy + vz * vz).coerceAtLeast(1e-4f)
            val d = (vx * -0.35f + vy * -0.55f + vz * 0.76f) / len
            return 0.42f + 0.58f * d.coerceAtLeast(0f)
        }
        /** Whether the bottom face shows. */
        val bottomShows: Boolean get() = facing(0f, -1f, 0f) > 0.01f
    }

    private fun shade(argb: Int, k: Float, alpha: Float): Color {
        val r = ((argb shr 16) and 0xFF) * k; val g = ((argb shr 8) and 0xFF) * k; val bl = (argb and 0xFF) * k
        return Color(r.coerceIn(0f, 255f) / 255f, g.coerceIn(0f, 255f) / 255f, bl.coerceIn(0f, 255f) / 255f, alpha.coerceIn(0f, 1f))
    }

    private fun quad(path: Path, v: View, a: FloatArray) {
        // a: 4 points × (x, y, z) mm.
        path.reset()
        path.moveTo(v.x(a[0], a[1], a[2]), v.y(a[0], a[1], a[2]))
        path.lineTo(v.x(a[3], a[4], a[5]), v.y(a[3], a[4], a[5]))
        path.lineTo(v.x(a[6], a[7], a[8]), v.y(a[6], a[7], a[8]))
        path.lineTo(v.x(a[9], a[10], a[11]), v.y(a[9], a[10], a[11]))
        path.close()
    }

    private fun addQuad(path: Path, v: View, a: FloatArray) {
        path.moveTo(v.x(a[0], a[1], a[2]), v.y(a[0], a[1], a[2]))
        path.lineTo(v.x(a[3], a[4], a[5]), v.y(a[3], a[4], a[5]))
        path.lineTo(v.x(a[6], a[7], a[8]), v.y(a[6], a[7], a[8]))
        path.lineTo(v.x(a[9], a[10], a[11]), v.y(a[9], a[10], a[11]))
        path.close()
    }

    /** Scratch, reused frame to frame (drawing is on the main thread only). */
    private val path = Path()
    private val clip = Path().apply { fillType = PathFillType.NonZero }
    private val rim = Path().apply { fillType = PathFillType.EvenOdd }
    private val pts = FloatArray(12)

    private fun pts(vararg v: Float): FloatArray { System.arraycopy(v, 0, pts, 0, 12); return pts }

    /**
     * Step 1, after the side slices and before the front / back face: the bottom face around its openings, in the shell's
     * plastic, lit as it faces. Only when the bottom shows and [full] (a drawn shell — a photo's outline isn't the shell's).
     */
    fun drawRim(ds: DrawScope, g: Geo, v: View, plastic: Int, opacity: Float) {
        if (!v.bottomShows) return
        val t = v.t
        rim.reset()
        addQuad(rim, v, pts(g.footL, 0f, 0f, g.footR, 0f, 0f, g.footR, 0f, t, g.footL, 0f, t))
        for (h in listOf(g.main) + g.notches) addQuad(rim, v, pts(h.x0, 0f, h.z0, h.x1, 0f, h.z0, h.x1, 0f, h.z1, h.x0, 0f, h.z1))
        ds.drawPath(rim, shade(plastic, 0.82f * v.light(0f, -1f, 0f), opacity))
    }

    /** Step 2, after the front / back face: what's seen through each opening, clipped to it. */
    fun drawInside(ds: DrawScope, g: Geo, v: View, plastic: Int, opacity: Float) {
        for (h in g.notches) hole(ds, h, g, v, plastic, opacity, contents = false)
        hole(ds, g.main, g, v, plastic, opacity, contents = true)
    }

    private fun hole(ds: DrawScope, h: Hole, g: Geo, v: View, plastic: Int, opacity: Float, contents: Boolean) {
        val t = v.t
        // The openings it shows through, on screen.
        clip.reset()
        var any = false
        if (v.bottomShows) { addQuad(clip, v, pts(h.x0, 0f, h.z0, h.x1, 0f, h.z0, h.x1, 0f, h.z1, h.x0, 0f, h.z1)); any = true }
        if (h.back && h.backH > 0f && v.facing(0f, 0f, 1f) > 0.01f) { addQuad(clip, v, pts(h.x0, 0f, t, h.x1, 0f, t, h.x1, h.backH, t, h.x0, h.backH, t)); any = true }
        if (h.front && v.facing(0f, 0f, -1f) > 0.01f) { addQuad(clip, v, pts(h.x0, 0f, 0f, h.x1, 0f, 0f, h.x1, h.y1, 0f, h.x0, h.y1, 0f)); any = true }
        if (h.left && v.facing(-1f, 0f, 0f) > 0.01f) { addQuad(clip, v, pts(h.x0, 0f, h.z0, h.x0, 0f, h.z1, h.x0, h.y1, h.z1, h.x0, h.y1, h.z0)); any = true }
        if (h.right && v.facing(1f, 0f, 0f) > 0.01f) { addQuad(clip, v, pts(h.x1, 0f, h.z0, h.x1, 0f, h.z1, h.x1, h.y1, h.z1, h.x1, h.y1, h.z0)); any = true }
        if (!any) return
        val inner = maxOf(opacity, 0.9f)
        ds.clipPath(clip) {
            // The walls you see from inside (their inner side faces you), darker than the outside.
            fun wall(nx: Float, ny: Float, nz: Float, a: FloatArray) {
                if (v.facing(nx, ny, nz) <= 0.01f) return
                quad(path, v, a)
                drawPath(path, shade(plastic, 0.5f * v.light(nx, ny, nz), inner))
            }
            wall(0f, -1f, 0f, pts(h.x0, h.y1, h.z0, h.x1, h.y1, h.z0, h.x1, h.y1, h.z1, h.x0, h.y1, h.z1))          // its ceiling
            if (!h.left) wall(1f, 0f, 0f, pts(h.x0, 0f, h.z0, h.x0, 0f, h.z1, h.x0, h.y1, h.z1, h.x0, h.y1, h.z0))
            if (!h.right) wall(-1f, 0f, 0f, pts(h.x1, 0f, h.z0, h.x1, 0f, h.z1, h.x1, h.y1, h.z1, h.x1, h.y1, h.z0))
            if (!h.front) wall(0f, 0f, 1f, pts(h.x0, 0f, h.z0, h.x1, 0f, h.z0, h.x1, h.y1, h.z0, h.x0, h.y1, h.z0))
            // Its back wall — up from the cut-out where it opens to the back face, all of it where it doesn't.
            val by0 = if (h.back) h.backH else 0f
            if (by0 < h.y1) wall(0f, 0f, -1f, pts(h.x0, by0, h.z1, h.x1, by0, h.z1, h.x1, h.y1, h.z1, h.x0, h.y1, h.z1))
            if (!contents) return@clipPath
            // The boards, far to near, then their fingers, then the plates and ribs, far to near.
            for (s in sorted(g.boards, v)) solid(this, s, v, plastic, inner)
            val gold = g.fingerArgb
            for (f in g.fingers) {
                val nz = if (f.back) 1f else -1f
                if (v.facing(0f, 0f, nz) <= 0.01f) continue
                val z = f.z + nz * 0.02f
                quad(path, v, pts(f.x0, f.y0, z, f.x1, f.y0, z, f.x1, f.y1, z, f.x0, f.y1, z))
                drawPath(path, shade(gold, 0.55f + 0.5f * v.light(0f, 0f, nz), 1f))
            }
            for (s in sorted(g.after, v)) solid(this, s, v, plastic, inner)
        }
    }

    /** [list] far to near (the depth of each box's middle along the view). */
    private fun sorted(list: List<Solid>, v: View): List<Solid> {
        if (list.size < 2) return list
        return list.sortedBy { s ->
            val cx = (s.x0 + s.x1) / 2f; val cyy = (s.y0 + s.y1) / 2f; val cz = (s.z0 + s.z1) / 2f
            // Nearer to you = more of the view's depth: the facing of the vector from the middle.
            v.facing(cx, cyy, cz)
        }
    }

    /** A box: its faces that face you (outward normals), lit; [Solid.argb] 0 = the shell's plastic. */
    private fun solid(ds: DrawScope, s: Solid, v: View, plastic: Int, alpha: Float) {
        val c = if (s.argb == 0) plastic else s.argb
        val k = if (s.argb == 0) 0.78f else 1f
        fun face(nx: Float, ny: Float, nz: Float, a: FloatArray) {
            if (v.facing(nx, ny, nz) <= 0.01f) return
            quad(path, v, a)
            ds.drawPath(path, shade(c, k * v.light(nx, ny, nz), alpha))
        }
        val b = s.bevel
        // Bottom (its edge) — bevelled into two slopes and a narrow flat where it has one.
        if (b > 0f) {
            face(0f, -1f, 0f, pts(s.x0, s.y0, s.z0 + b, s.x1, s.y0, s.z0 + b, s.x1, s.y0, s.z1 - b, s.x0, s.y0, s.z1 - b))
            face(0f, -0.7071f, -0.7071f, pts(s.x0, s.y0 + b, s.z0, s.x1, s.y0 + b, s.z0, s.x1, s.y0, s.z0 + b, s.x0, s.y0, s.z0 + b))
            face(0f, -0.7071f, 0.7071f, pts(s.x0, s.y0, s.z1 - b, s.x1, s.y0, s.z1 - b, s.x1, s.y0 + b, s.z1, s.x0, s.y0 + b, s.z1))
        } else face(0f, -1f, 0f, pts(s.x0, s.y0, s.z0, s.x1, s.y0, s.z0, s.x1, s.y0, s.z1, s.x0, s.y0, s.z1))
        face(0f, 1f, 0f, pts(s.x0, s.y1, s.z0, s.x1, s.y1, s.z0, s.x1, s.y1, s.z1, s.x0, s.y1, s.z1))
        face(0f, 0f, -1f, pts(s.x0, s.y0 + b, s.z0, s.x1, s.y0 + b, s.z0, s.x1, s.y1, s.z0, s.x0, s.y1, s.z0))
        face(0f, 0f, 1f, pts(s.x0, s.y0 + b, s.z1, s.x1, s.y0 + b, s.z1, s.x1, s.y1, s.z1, s.x0, s.y1, s.z1))
        face(-1f, 0f, 0f, pts(s.x0, s.y0, s.z0, s.x0, s.y0, s.z1, s.x0, s.y1, s.z1, s.x0, s.y1, s.z0))
        face(1f, 0f, 0f, pts(s.x1, s.y0, s.z0, s.x1, s.y0, s.z1, s.x1, s.y1, s.z1, s.x1, s.y1, s.z0))
    }
}
