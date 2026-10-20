package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Features.SaveVaultStyle
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SyncState
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * (Update 236, #42/#43/#47: a colour of your own for each Save Vault object — the drawn memory card, SD card and floppy,
 * the 3D card and the capsule — for one save or for every save of a console. Original is today's look, exactly. The
 * presets are the Voltcap memory cards' own colours (the 3D models pack's memory_card_voltcap_* meta.json "own" shell /
 * badge pair), read from the pack on the device: no colour of theirs is written into the app. Only how a save is drawn:
 * the save itself is never touched.)
 */

/** (Update 236, #42: one object's colours: [shell] (null: its original), [trim] (screws / contacts / shutter / badge; null: its own), [preset] picked.) */
internal data class SaveLook(
    val shell: Int?, val trim: Int?, val preset: String?,
    /** Update 285: the 3D card's clear (true) or solid (false) version for this save; null: as picked for every save. */
    val clear: Boolean? = null
) {
    val isEmpty: Boolean get() = shell == null && trim == null && clear == null

    fun encode(): String? = listOfNotNull(
        preset?.let { "p=$it" }, shell?.let { "shell=" + hexOf(it) }, trim?.let { "trim=" + hexOf(it) },
        clear?.let { "clear=" + if (it) "1" else "0" }
    ).joinToString(";").ifEmpty { null }

    companion object {
        /** "p=black;shell=#RRGGBB;trim=#RRGGBB" (any of them) → the look; null when there's nothing in it. */
        fun parse(raw: String?): SaveLook? {
            if (raw.isNullOrBlank()) return null
            var p: String? = null
            var s: Int? = null
            var t: Int? = null
            var c: Boolean? = null
            for (f in raw.split(';')) {
                val i = f.indexOf('=')
                if (i <= 0) continue
                val k = f.substring(0, i).trim()
                val v = f.substring(i + 1).trim()
                if (k == "p") p = v.ifEmpty { null }
                else if (k == "shell") s = parseHex(v)
                else if (k == "trim") t = parseHex(v)
                else if (k == "clear") c = v == "1"
            }
            return SaveLook(s, t, p, c).takeUnless { it.isEmpty }
        }
    }
}

/**
 * (Update 236, #42: the looks, saved in Voltcap's settings (Features.saveObjectLooks, so they're backed up and survive a
 * restart) like CartShellStore. No state object of its own: reading the setting's state (made at start with Features)
 * is what redraws the vault when a look changes — no state is ever made in the middle of a screen's measuring.)
 */
internal object SaveLookStore {
    @Volatile private var map: Map<String, String> = emptyMap()
    @Volatile private var from: String? = null

    private fun sync() {
        // Read here (on a screen) → that screen redraws when a look changes.
        val raw = Features.saveObjectLooks.state.value
        if (raw == from) return
        synchronized(this) {
            if (raw == from) return
            val m = HashMap<String, String>()
            raw.split('\n').forEach { line ->
                val i = line.indexOf('\t')
                if (i > 0) m[line.substring(0, i)] = line.substring(i + 1)
            }
            map = m
            from = raw
        }
    }

    private fun gameKey(stableKey: String) = "g:" + stableKey.replace('\n', ' ').replace('\t', ' ')
    private fun consoleKey(platform: String) = "console:$platform"

    fun forGame(stableKey: String): SaveLook? { sync(); return SaveLook.parse(map[gameKey(stableKey)]) }
    fun forConsole(platform: String): SaveLook? { sync(); return SaveLook.parse(map[consoleKey(platform)]) }

    /** The save's own look, else its console's (the save's own choice wins); null: the original. */
    fun look(stableKey: String?, platform: String?): SaveLook? {
        sync()
        return stableKey?.let { SaveLook.parse(map[gameKey(it)]) } ?: platform?.let { SaveLook.parse(map[consoleKey(it)]) }
    }

    fun setGame(stableKey: String, look: SaveLook?) = put(gameKey(stableKey), look)
    fun setConsole(platform: String, look: SaveLook?) = put(consoleKey(platform), look)

    @Synchronized private fun put(key: String, look: SaveLook?) {
        sync()
        val m = HashMap(map)
        val v = look?.encode()
        if (v == null) m.remove(key) else m[key] = v
        val raw = m.entries.joinToString("\n") { "${it.key}\t${it.value}" }
        map = m
        from = raw
        Features.saveObjectLooks.value = raw
    }
}

/** (Update 236, #42: one of the Voltcap memory cards' colours — its shell and badge, as the 3D models pack has them.) */
internal class SaveLookPreset(val id: String, val label: String, val shell: Int, val badge: Int)

/**
 * (Update 236, #42: the Voltcap memory cards' colours, read from the 3D models pack's meta.json (its "own" pair) for
 * each card on the device — empty while the pack isn't here.)
 */
internal fun saveLookPresets(): List<SaveLookPreset> {
    com.shelf.launcher.data.Packs.version.intValue
    return ModelSpare.values().filter { it != ModelSpare.SD }.mapNotNull { s ->
        val own = (Models3D.meta(s.id) ?: Models3D.meta(s.id + "_translucent"))?.tint?.own ?: return@mapNotNull null
        SaveLookPreset(s.name.lowercase(), "Voltcap card · " + s.label.removeSuffix(" card"), own.first, own.second)
    }
}

// ---- (Update 236, #42: the drawn objects' tones from a look; the originals when there's none) ----

private fun mix(a: Color, b: Color, f: Float): Color = androidx.compose.ui.graphics.lerp(a, b, f)

internal class MemCardTones(
    val body: List<Color>, val screwOuter: Color, val screwInner: Color, val tri: List<Color>, val triEdge: Color,
    val recessEdge: Color, val recess: Color, val text: Color
)

private val MEM_ORIGINAL = MemCardTones(
    body = listOf(Color(0xFFC7C9CC), Color(0xFFA9ABAF)), screwOuter = Color(0xFF8F9195), screwInner = Color(0xFF6E7074),
    tri = listOf(Color(0xFFDADCDF), Color(0xFFB4B6BA)), triEdge = Color(0xFF8C8E92), recessEdge = Color(0xFF8C8E92),
    recess = Color(0xFFB6B8BC), text = Color(0xFF5F6165)
)

/** The drawn memory card: its body from the shell colour, the screws and triangle from the trim (else the shell). */
internal fun memCardTones(look: SaveLook?): MemCardTones {
    val s = look?.shell?.let { Color(it) }
    val t = look?.trim?.let { Color(it) } ?: s
    if (s == null && t == null) return MEM_ORIGINAL
    val o = MEM_ORIGINAL
    return MemCardTones(
        body = s?.let { listOf(mix(it, Color.White, 0.10f), mix(it, Color.Black, 0.14f)) } ?: o.body,
        screwOuter = t?.let { mix(it, Color.Black, 0.28f) } ?: o.screwOuter,
        screwInner = t?.let { mix(it, Color.Black, 0.45f) } ?: o.screwInner,
        tri = t?.let { listOf(mix(it, Color.White, 0.14f), mix(it, Color.Black, 0.08f)) } ?: o.tri,
        triEdge = t?.let { mix(it, Color.Black, 0.30f) } ?: o.triEdge,
        recessEdge = s?.let { mix(it, Color.Black, 0.30f) } ?: o.recessEdge,
        recess = s?.let { mix(it, Color.Black, 0.08f) } ?: o.recess,
        text = s?.let { if (it.luminance() > 0.4f) mix(it, Color.Black, 0.55f) else mix(it, Color.White, 0.55f) } ?: o.text
    )
}

/** The SD card's body (original: dark grey). */
internal fun sdBody(look: SaveLook?): List<Color> =
    look?.shell?.let { Color(it) }?.let { listOf(mix(it, Color.White, 0.06f), mix(it, Color.Black, 0.30f)) }
        ?: listOf(Color(0xFF2B2F3A), Color(0xFF16181F))

/** The SD card's contacts: gold unless a trim is picked. */
internal fun sdContacts(look: SaveLook?): Color = look?.trim?.let { Color(it) } ?: Color(0xFFD7B355)

/** The floppy's body (original: near black). */
internal fun floppyBody(look: SaveLook?): List<Color> =
    look?.shell?.let { Color(it) }?.let { listOf(mix(it, Color.White, 0.05f), mix(it, Color.Black, 0.30f)) }
        ?: listOf(Color(0xFF22252E), Color(0xFF111318))

/** The floppy's metal shutter: brushed metal unless a trim is picked. */
internal fun floppyShutter(look: SaveLook?): List<Color> =
    look?.trim?.let { Color(it) }?.let { listOf(mix(it, Color.Black, 0.15f), mix(it, Color.White, 0.35f), mix(it, Color.Black, 0.22f)) }
        ?: listOf(Color(0xFF9EA4AE), Color(0xFFD9DDE3), Color(0xFF8E949E))

/** A capsule's glass (and its halves when it opens): the look's shell, else its own clear white. */
internal fun capsuleGlass(look: SaveLook?): Color = look?.shell?.let { Color(it) } ?: Color.White

/** Where a style's colour starts when you go to Custom (its original shell). (Update 241, #51: the Inspect viewer's Original swatch too.) */
internal fun originalShell(style: SaveVaultStyle): Int = when (style) {
    SaveVaultStyle.SD_CARD -> 0xFF2B2F3A.toInt()
    SaveVaultStyle.FLOPPY -> 0xFF22252E.toInt()
    SaveVaultStyle.CAPSULE -> 0xFFFFFFFF.toInt()
    SaveVaultStyle.CARD_3D -> Models3D.saveCardId()?.let { Models3D.meta(it)?.tint?.own?.first } ?: 0xFFC7C9CC.toInt()
    else -> 0xFFC7C9CC.toInt()
}

private fun originalTrim(style: SaveVaultStyle): Int = when (style) {
    SaveVaultStyle.SD_CARD -> 0xFFD7B355.toInt()
    SaveVaultStyle.FLOPPY -> 0xFFD9DDE3.toInt()
    SaveVaultStyle.CARD_3D -> Models3D.saveCardId()?.let { Models3D.meta(it)?.tint?.own?.second } ?: 0xFFDADCDF.toInt()
    else -> 0xFFDADCDF.toInt()
}

// ---- (Update 236, #47: the save's Appearance… window) ----

/**
 * (Update 236, #47: Save Vault › a save › Appearance: this save's object (or every save of its console) in Original, a
 * Voltcap card's colours or your own, shell and trim apart where the drawing has both, with a live preview. Changes show
 * at once and stay; Reset to original takes the choice away. The save itself is never touched.)
 */
@Composable
internal fun SaveLookDialog(game: Game, icon: String?, state: SyncState, onDismiss: () -> Unit) {
    val platform = game.platform
    val brand = remember(platform) { com.shelf.launcher.data.ConsoleNames.display(platform) }
    val name = gameDisplayName(game.name)
    val style = Features.saveVaultStyle.state.value
    var forConsole by remember { mutableStateOf(SaveLookStore.forGame(game.stableKey) == null && SaveLookStore.forConsole(platform) != null) }
    /** The choice being edited, read fresh (also inside the sliders' callbacks). */
    fun now(): SaveLook? = if (forConsole) SaveLookStore.forConsole(platform) else SaveLookStore.forGame(game.stableKey)
    fun store(l: SaveLook?) {
        val v = l?.takeUnless { it.isEmpty }
        if (forConsole) SaveLookStore.setConsole(platform, v) else SaveLookStore.setGame(game.stableKey, v)
    }
    val current = now()
    val presets = remember(com.shelf.launcher.data.Packs.version.intValue) { saveLookPresets() }
    val card3d = if (style == SaveVaultStyle.CARD_3D) Models3D.saveCardId() else null
    // A 3D card without its own colours in the pack (the SD card) can't be painted; the drawn ones take any colour.
    val paintable = card3d == null || Models3D.meta(card3d)?.tint?.own != null
    val hasTrim = style == SaveVaultStyle.MEMORY_CARD || style == SaveVaultStyle.SD_CARD || style == SaveVaultStyle.FLOPPY || style == SaveVaultStyle.CARD_3D
    val trimLabel = when {
        card3d != null -> "Badge"
        style == SaveVaultStyle.SD_CARD -> "Trim · contacts"
        style == SaveVaultStyle.FLOPPY -> "Trim · metal shutter"
        else -> "Trim · screws and triangle"
    }
    var customShell by remember { mutableStateOf(false) }
    var customTrim by remember { mutableStateOf(false) }
    ShelfDialog(
        icon = Icons.Filled.Palette,
        title = "Appearance",
        subtitle = "$name · only how it's drawn, the save is never touched",
        onDismiss = onDismiss,
        widthFraction = 0.9f,
        footer = {
            Chip("Reset to original", accent = false) { store(null); customShell = false; customTrim = false }
            Chip("Done", onClick = onDismiss)
        }
    ) {
        if (style == SaveVaultStyle.CLASSIC) {
            MutedNote("The Classic squares have no object to colour: pick another style (Filters & look › Style).")
        } else {
        ChipFlow {
            Chip("This save", accent = !forConsole) { forConsole = false }
            Chip("Every $brand save", accent = forConsole) { forConsole = true }
        }
        if (forConsole && SaveLookStore.forGame(game.stableKey) != null) {
            Spacer(Modifier.height(6.dp))
            MutedNote("This save has a colour of its own, which wins over the console's.")
            Spacer(Modifier.height(4.dp))
            ChipFlow { Chip("Use the console's for this save", accent = false) { SaveLookStore.setGame(game.stableKey, null) } }
        }
        Spacer(Modifier.height(10.dp))
        // The live preview: the save as the vault draws it, in the colours being picked.
        Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(if (style == SaveVaultStyle.CAPSULE) 104.dp else 140.dp), contentAlignment = Alignment.Center) {
                if (style == SaveVaultStyle.CAPSULE) CapsuleSaveCard(icon, name, state, selected = false, lift = 0f, bob = { 0f }, hidden = false, onIconPlaced = {}, look = current)
                else StyledSaveCard(style, icon, name, state, false, 0f, { 0f }, false, {}, platform = platform, look = current)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (!paintable) {
            MutedNote("This 3D card can't be recoloured. Pick one of the Voltcap memory cards (Filters & look › Style › 3D card) or a drawn style.")
        } else {
        // Update 285 (Toni: "a bit confusing on what's what: a section for translucent colours and a section for the
        // normal ones, and more examples for each colour"): Solid and (for a 3D card that comes clear) Translucent, each
        // the Voltcap cards' own colours and then every colour in four shades; the badge follows the shell unless picked.
        val canClear = card3d != null && Models3D.saveCardId()?.let { id ->
            val base = Features.saveVaultCard.state.value.id
            com.shelf.launcher.data.Packs.hasModel(base + "_translucent") && com.shelf.launcher.data.Packs.hasModel(base)
        } == true
        val clearNow = current?.clear ?: Features.saveVaultClear.state.value
        fun pick(shell: Int?, clear: Boolean?, preset: String?, badge: Int? = null) {
            customShell = false
            val l = now()
            // The badge: a Voltcap card's own, the one you picked, else one that goes with the shell.
            val trim = when {
                !hasTrim -> null
                badge != null -> badge
                customTrim || (l?.trim != null && l.trim != shell?.let { matchingBadge(it) } && presets.none { it.badge == l.trim }) -> l?.trim
                else -> shell?.let { matchingBadge(it) }
            }
            store(SaveLook(shell, trim, preset, clear))
        }
        @Composable
        fun ColourSection(title: String, clear: Boolean?) {
            Text(title, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val inSection = if (clear == null) true else clearNow == clear
            if (presets.isNotEmpty()) SwatchRow("Voltcap") {
                presets.forEach { p ->
                    SwatchDot(p.shell, p.label.removePrefix("Voltcap card · "), selected = inSection && current?.preset == p.id && current?.shell == p.shell) {
                        pick(p.shell, clear, p.id, if (hasTrim) p.badge else null)
                    }
                }
            }
            SAVE_PALETTE.forEach { (family, shades) ->
                SwatchRow(family) {
                    shades.forEach { (label, c) ->
                        SwatchDot(c, label, selected = inSection && current?.preset == null && current?.shell == c) { pick(c, clear, null) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        ChipFlow {
            SwatchChip("Original", originalShell(style), selected = current?.shell == null && !customShell) {
                customShell = false; store(SaveLook(null, null, null, now()?.clear))
            }
            SwatchChip("Custom…", current?.shell?.takeIf { current?.preset == null && SAVE_PALETTE.none { f -> f.second.any { it.second == current?.shell } } }, selected = customShell) { customShell = !customShell }
        }
        if (customShell) {
            Spacer(Modifier.height(8.dp))
            val c = current?.shell ?: originalShell(style)
            HueSlider(c) { h -> now().let { l -> store(SaveLook(h, l?.trim, null, l?.clear)) } }
            Spacer(Modifier.height(6.dp))
            ColorFineTune("Shell", c) { v -> now().let { l -> store(SaveLook(v, l?.trim, null, l?.clear)) } }
        }
        Spacer(Modifier.height(10.dp))
        ColourSection(if (canClear) "Solid colours" else "Colours", if (canClear) false else null)
        if (canClear) ColourSection("Translucent colours · see-through plastic", true)
        if (presets.isEmpty()) {
            MutedNote("The Voltcap cards' own colours come from the 3D models pack (Settings › Game look › Extra art packs).")
            Spacer(Modifier.height(6.dp))
        }
        if (hasTrim) {
            Text(trimLabel, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val auto = current?.shell?.let { matchingBadge(it) }
            ChipFlow {
                SwatchChip("Goes with the shell", auto, selected = !customTrim && current?.trim != null && current?.trim == auto) {
                    customTrim = false; now().let { l -> store(SaveLook(l?.shell, l?.shell?.let { matchingBadge(it) }, l?.preset, l?.clear)) }
                }
                SwatchChip("Original", null, selected = current?.trim == null && !customTrim) {
                    customTrim = false; now().let { l -> store(SaveLook(l?.shell, null, l?.preset, l?.clear)) }
                }
                SwatchChip("Custom…", current?.trim?.takeIf { it != auto }, selected = customTrim) { customTrim = !customTrim }
            }
            Spacer(Modifier.height(6.dp))
            SwatchRow("Badge") {
                SAVE_PALETTE.forEach { (_, shades) ->
                    val (label, c) = shades[1]
                    SwatchDot(c, label, selected = !customTrim && current?.trim == c && c != auto) {
                        customTrim = false; now().let { l -> store(SaveLook(l?.shell, c, l?.preset, l?.clear)) }
                    }
                }
            }
            if (customTrim) {
                Spacer(Modifier.height(8.dp))
                val c = current?.trim ?: originalTrim(style)
                HueSlider(c) { h -> now().let { l -> store(SaveLook(l?.shell, h, l?.preset, l?.clear)) } }
                Spacer(Modifier.height(6.dp))
                ColorFineTune("Trim", c) { v -> now().let { l -> store(SaveLook(l?.shell, v, l?.preset, l?.clear)) } }
            }
        }
        Spacer(Modifier.height(10.dp))
        MutedNote("The Voltcap card colours are Voltcap's own memory cards (3D models pack), not any console maker's.")
        } // (paintable)
        } // (not Classic)
    }
}

/** A chip with a colour swatch (D-pad and touch, like [Chip]). (Update 241, #51: the Inspect viewer's colour strip too.) */
@Composable
internal fun SwatchChip(label: String, swatch: Int?, selected: Boolean, onClick: () -> Unit) {
    val shape = uiRounded(50)
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) ShelfTheme.accent.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f))
            .border(if (selected) 2.dp else 1.dp, if (selected) ShelfTheme.accent else Color.White.copy(alpha = 0.10f), shape)
            .shelfClickable(shape) { onClick() }
            .padding(start = 8.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(18.dp).clip(CircleShape)
                .background(swatch?.let { Color(it) } ?: Color.Transparent)
                .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/** Update 285: the colours offered for a save's object, four shades of each (darkest to lightest). */
internal val SAVE_PALETTE: List<Pair<String, List<Pair<String, Int>>>> = listOf(
    "Black / white" to listOf("Black" to 0xFF1E1F23, "Charcoal" to 0xFF4A4D55, "Grey" to 0xFF9EA2A8, "White" to 0xFFE8E9EC),
    "Red" to listOf("Wine" to 0xFF7A1418, "Red" to 0xFFC62828, "Coral" to 0xFFE8524A, "Rose" to 0xFFF4A3A0),
    "Orange / yellow" to listOf("Orange" to 0xFFE65100, "Tangerine" to 0xFFFF9F2E, "Yellow" to 0xFFF2C230, "Lemon" to 0xFFF6E27A),
    "Green" to listOf("Forest" to 0xFF1B5E20, "Green" to 0xFF2E9E4F, "Lime" to 0xFF8BC34A, "Mint" to 0xFF9EE6B8),
    "Blue" to listOf("Navy" to 0xFF13265C, "Blue" to 0xFF2F6FD6, "Sky" to 0xFF47B5E6, "Teal" to 0xFF2FA7A0),
    "Purple / pink" to listOf("Indigo" to 0xFF4A2A7A, "Purple" to 0xFF8E4FC2, "Lavender" to 0xFFC08BE8, "Pink" to 0xFFE46FA8)
).map { (f, l) -> f to l.map { (n, c) -> n to c.toInt() } }

/** Update 285: a badge that goes with [shell] — a deeper shade on a light shell, a lighter one on a dark shell. */
internal fun matchingBadge(shell: Int): Int {
    val c = Color(shell)
    return (if (c.luminance() > 0.45f) mix(c, Color.Black, 0.45f) else mix(c, Color.White, 0.45f)).let {
        android.graphics.Color.argb(255, (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt())
    }
}

/** Update 285: one row of a colour section — its family's name, then its swatches. */
@Composable
internal fun SwatchRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ShelfTheme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(96.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/** Update 285: a round colour swatch with its name under it (D-pad and touch); a ring when it's the one picked. */
@Composable
internal fun SwatchDot(colour: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        Modifier.width(58.dp).clip(uiRounded(10)).shelfClickable(uiRounded(10)) { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(28.dp)
                .border(if (selected) 3.dp else 1.dp, if (selected) ShelfTheme.accent else Color.White.copy(alpha = 0.35f), CircleShape)
                .padding(if (selected) 4.dp else 1.dp)
                .clip(CircleShape)
                .background(Color(colour))
        )
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (selected) ShelfTheme.accent else ShelfTheme.colors.textPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}
