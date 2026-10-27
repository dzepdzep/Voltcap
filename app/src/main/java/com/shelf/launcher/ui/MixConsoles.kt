package com.shelf.launcher.ui

import com.shelf.launcher.data.CardArtType
import com.shelf.launcher.data.Features
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pass 4: Settings › Game look › Cartridges & discs › Mix › Customize consoles — what each console's games are drawn as
 * in the Mix look, on its own. Automatic (the default) is what Mix always did: the console's own original medium — a PS2
 * game its DVD case or disc as the Disc games choice says, an N64 game its cartridge, a PSP game its UMD, a Switch game
 * its game card. A console can instead show:
 *  - MEDIUM: always its cartridge / disc / UMD / game card (a disc console's disc even with "Their case" on);
 *  - CASE: its retail case / box (the console's case template, real box scans when there are some);
 *  - CASE_MEDIA: its case with the medium (a disc half out for disc consoles; the cartridge leaning against the box);
 *  - COVER: the plain cover art.
 * Only the choices the console's templates support are offered ([choicesFor]); a console with no original medium
 * template keeps its cover, never an invented cartridge or case. Resolved by each game's own platform, so a PS2 game in
 * Favorites or a mixed collection is drawn as its PS2 choice. Separate from a category's own game look and from the
 * category home layouts; a home layout change never touches it.
 */
internal object MixConsoles {
    enum class Medium(val label: String) {
        AUTO("Automatic (original)"), MEDIUM("Cartridge / disc"), CASE("Retail case / box"),
        CASE_MEDIA("Case with its media"), COVER("Cover art")
    }

    private fun parse(raw: String): Map<String, Medium> = runCatching {
        val o = org.json.JSONObject(raw.ifBlank { "{}" })
        o.keys().asSequence().mapNotNull { k -> Medium.values().firstOrNull { it.name == o.optString(k) }?.let { k to it } }.toMap()
    }.getOrDefault(emptyMap())

    /** Every console's choice (state-backed: reading it in composition follows changes). */
    fun all(): Map<String, Medium> = parse(Features.mixConsoles.state.value)

    fun of(platform: String?): Medium = platform?.let { all()[it] } ?: Medium.AUTO

    fun set(platform: String, m: Medium) {
        val now = all().toMutableMap()
        if (m == Medium.AUTO) now.remove(platform) else now[platform] = m
        val o = org.json.JSONObject(); now.forEach { (k, v) -> o.put(k, v.name) }
        Features.mixConsoles.value = if (now.isEmpty()) "" else o.toString()
    }

    fun resetAll() { Features.mixConsoles.value = "" }

    /** What [platform]'s templates can draw (Automatic and Cover always). */
    fun choicesFor(platform: String): List<Medium> {
        val tpl = MediaTemplates.of(platform)
        val shell = MediaShell.of(platform)
        val out = mutableListOf(Medium.AUTO)
        if (shell != null) out += Medium.MEDIUM
        if (tpl?.box != null) {
            out += Medium.CASE
            // A disc half out needs the disc and box templates; a leaning piece needs the console's shell.
            if ((tpl.disc != null) || shell != null) out += Medium.CASE_MEDIA
        }
        out += Medium.COVER
        return out
    }

    /** Pass 5: a cover of one of each console's games (set by the launcher screen), for the previews in Customize consoles. */
    val samples = androidx.compose.runtime.mutableStateOf<Map<String, Pair<String, String>>>(emptyMap())

    /** The consoles in your library (set by the launcher screen), for Customize consoles. */
    val platforms = androidx.compose.runtime.mutableStateOf<List<String>>(emptyList())

    /** The game look in force is Cartridges & discs (set by the launcher screen). */
    val lookIsCarts = androidx.compose.runtime.mutableStateOf(false)

    /** Only in the Mix look of Cartridges & discs (not Cartridges / Discs for every game, not the other looks). */
    // (Show as is read the way GameLookPicker.showAsNow does: Discs is its own game look (CardArtType.DISC), Cartridges is
    // cartsOnly; Features.discsForAll only matters inside the Discs look — QA: it was on here and turned this off.)
    fun active(): Boolean = lookIsCarts.value && !Features.cartsOnly.state.value

    /** The card art for a game of [platform] when its console has a choice of its own; null = as the look draws it. */
    fun artFor(type: CardArtType, platform: String): CardArtType? {
        if (type != CardArtType.CARTRIDGE_DISC || !active()) return null
        return when (of(platform)) {
            Medium.AUTO, Medium.MEDIUM -> null
            Medium.CASE -> CardArtType.CASE
            Medium.CASE_MEDIA -> if (MediaTemplates.of(platform)?.disc != null) null else CardArtType.CASE
            Medium.COVER -> CardArtType.COVER
        }
    }

    /** A disc console's case with the disc half out: the console's choice, else the Disc games setting. */
    fun discCase(platform: String?): Boolean = when (if (active()) of(platform) else Medium.AUTO) {
        Medium.MEDIUM -> false
        Medium.CASE_MEDIA -> true
        else -> Features.discCase.state.value
    }

    /** A cartridge leaning against its box: the console's choice, else Case with its media. */
    fun caseWithMedia(platform: String?): Boolean = when (if (active()) of(platform) else Medium.AUTO) {
        Medium.CASE_MEDIA -> true
        Medium.CASE -> false
        else -> Features.caseWithMedia.state.value
    }
}

/**
 * Pass 4: Mix › Customize consoles — every console in your library with a medium of its own: its picture, its name and
 * the choices its templates support; each with Reset, and Reset all. Changes show on the home screen at once.
 */
@androidx.compose.runtime.Composable
internal fun MixConsolesCard() {
    val platforms = MixConsoles.platforms.value.filter { MediaShell.of(it) != null || MediaTemplates.of(it)?.box != null }
    val all = MixConsoles.all()
    SectionLabel("Customize consoles")
    MutedNote("Automatic is each console's original medium (a PS2 game its DVD case or disc, an N64 game its cartridge, a PSP game its UMD). Pick another for a console here; it follows its games into Favorites, Continue playing and mixed collections.")
    androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(6.dp))
    platforms.forEach { p ->
        val now = all[p] ?: MixConsoles.Medium.AUTO
        androidx.compose.foundation.layout.Row(
            androidx.compose.ui.Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            val hw = com.shelf.launcher.data.ConsoleArt.model(p)
            if (hw != null) coil.compose.AsyncImage(hw, null, contentScale = androidx.compose.ui.layout.ContentScale.Fit, modifier = androidx.compose.ui.Modifier.size(54.dp, 36.dp))
            else androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.size(54.dp, 36.dp))
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(10.dp))
            androidx.compose.material3.Text(
                com.shelf.launcher.data.ConsoleNames.label(p), color = com.shelf.launcher.ui.theme.ShelfTheme.colors.textPrimary, fontSize = 13.sp,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = androidx.compose.ui.Modifier.width(150.dp)
            )
            // Pass 5: one of its games as the choice draws it (its cover, case, case with its media, cartridge / disc).
            MixPreview(p, now)
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(10.dp))
            ChipFlow(androidx.compose.ui.Modifier.weight(1f)) {
                MixConsoles.choicesFor(p).forEach { m -> Chip(m.label, accent = m == now) { MixConsoles.set(p, m) } }
            }
        }
    }
    if (all.isNotEmpty()) {
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(6.dp))
        Chip("Reset all consoles to Automatic", accent = false) { MixConsoles.resetAll() }
    }
}


/** Pass 5: Customize consoles' preview — one of [platform]'s games drawn as [m] picks. */
@androidx.compose.runtime.Composable
private fun MixPreview(platform: String, m: MixConsoles.Medium) {
    val sample = MixConsoles.samples.value[platform]
    val cover = sample?.second
    val title = sample?.first ?: ""
    val shell = MediaShell.of(platform)
    val tplDisc = MediaTemplates.of(platform)?.disc != null
    val mod = androidx.compose.ui.Modifier.fillMaxSize()
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(58.dp, 58.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
        when {
            m == MixConsoles.Medium.COVER || (shell == null && cover != null && m != MixConsoles.Medium.CASE) ->
                if (cover != null) coil.compose.AsyncImage(cover, null, contentScale = androidx.compose.ui.layout.ContentScale.Fit, modifier = mod)
            m == MixConsoles.Medium.CASE || (m == MixConsoles.Medium.CASE_MEDIA && !tplDisc) || shell == null ->
                CaseArt(platform, cover, null, mod, title = title)
            else -> PhysicalMedia(shell, cover, mod, platform = platform, caseCover = cover, title = title)
        }
    }
}
