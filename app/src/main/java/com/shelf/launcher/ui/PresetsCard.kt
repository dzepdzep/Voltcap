package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.LayoutPresetStore
import com.shelf.launcher.ui.theme.AppTheme
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Settings › Home › Layout & presets: pick the home layout, with two or three presets made for each one (a drawn
 * preview, what it is and what it changes, then Apply), and your own saved presets. Built-in presets set only the
 * layout, the game look and where things sit — never the theme. Applying never locks anything; the active preset
 * shows "(modified)" once any field it sets changes.
 */
@Composable
internal fun PresetsCard(
    appearance: AppearanceSettings,
    theme: AppTheme,
    onAppearanceChange: (AppearanceSettings) -> Unit,
    onSelectTheme: (AppTheme) -> Unit
) {
    val context = LocalContext.current
    val store = remember { LayoutPresetStore(context.applicationContext) }
    // Bumped after every store change so lists and buttons re-read it.
    var rev by remember { mutableIntStateOf(0) }
    var naming by remember { mutableStateOf<String?>(null) }   // "" = new, else id being renamed
    var managing by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    @Suppress("UNUSED_EXPRESSION") rev
    val presets = store.all()
    val active = store.find(store.activeId())
    val modified = active != null && store.isModified(appearance, theme)
    val customs = store.customs()

    fun push(r: Pair<AppearanceSettings, AppTheme?>) {
        r.second?.let { if (it != theme) onSelectTheme(it) }
        onAppearanceChange(r.first)
        rev++
    }

    // A preset that changes the home layout is applied in two steps: first the layout alone (so the layout you leave
    // keeps its own card look and the new one brings in its own), then — once that has landed — the preset's values
    // on top. Undo goes back to [pendingBefore], the settings from before the first step.
    var pending by remember { mutableStateOf<com.shelf.launcher.data.LayoutPreset?>(null) }
    var pendingBefore by remember { mutableStateOf<AppearanceSettings?>(null) }
    fun applyPreset(p: com.shelf.launcher.data.LayoutPreset) {
        notice = null
        val target = p.apply(appearance).homeLayout
        if (target != appearance.homeLayout) {
            pending = p
            pendingBefore = appearance
            onAppearanceChange(appearance.copy(homeLayout = target))
        } else {
            // Update 162: the game look's switches, the layout's options and its carousel, after the undo point is taken.
            val r = store.apply(p, appearance, theme)
            applyPresetExtras(p, r.first)
            push(r)
        }
    }
    androidx.compose.runtime.LaunchedEffect(appearance.homeLayout, pending) {
        val p = pending ?: return@LaunchedEffect
        if (p.apply(appearance).homeLayout != appearance.homeLayout) return@LaunchedEffect
        val before = pendingBefore ?: appearance
        pending = null
        pendingBefore = null
        // Update 162: as above, the preset's game look switches, layout options and carousel too.
        val r = store.apply(p, appearance, theme, before)
        applyPresetExtras(p, r.first)
        push(r)
    }

    // The layout whose presets are shown (starts on the one in use; picking another doesn't switch to it yet).
    var browse by remember { mutableStateOf(appearance.homeLayout) }
    // Your own preset picked in its list: shown (preview + what it does) until you press Apply.
    var chosenId by remember { mutableStateOf(active?.id?.takeIf { id -> customs.any { it.id == id } }) }
    val chosen = presets.firstOrNull { it.id == chosenId && !it.builtIn }

    ShelfCard {
        SectionLabel("Layout")
        MutedNote(
            "Where your games show on the home screen. Pick a layout to see its presets, made for it; " +
                "how each game is drawn (covers, cases, cartridges, discs) is Settings › Game look."
        )
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            com.shelf.launcher.data.HomeLayout.pickable.forEach { l ->
                val inUse = l == appearance.homeLayout
                Chip((if (inUse) "✓ " else "") + l.label, accent = l == browse) { browse = l; notice = null }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(layoutBlurb(browse), color = ShelfTheme.colors.textPrimary.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(8.dp))
        if (browse != appearance.homeLayout) {
            Chip("Use ${browse.label} as it is", accent = false, enabled = pending == null) {
                notice = null
                onAppearanceChange(appearance.copy(homeLayout = browse))
            }
            MutedNote("Switches the layout and keeps your game look. Or pick one of its presets below.")
            Spacer(Modifier.height(10.dp))
        }
        SectionLabel("Presets for ${browse.label}")
        Spacer(Modifier.height(6.dp))
        com.shelf.launcher.data.LayoutPresets.forLayout(browse).forEach { p ->
            PresetDetails(p, appearance, theme, isActive = p.id == active?.id, modified = modified, applying = pending != null,
                onApply = { applyPreset(p) })
            Spacer(Modifier.height(8.dp))
        }
        notice?.let { MutedNote(it); Spacer(Modifier.height(6.dp)) }
        Spacer(Modifier.height(6.dp))
        SectionLabel("Your presets")
        MutedNote("Your whole look saved under a name — layout, game look, where things sit, and your theme. Share one as a code.")
        Spacer(Modifier.height(6.dp))
        if (customs.isNotEmpty()) {
            ChipFlow {
                presets.filter { !it.builtIn }.forEach { p ->
                    val isActive = p.id == active?.id
                    val label = (if (isActive) "✓ " else "") + p.name + if (isActive && modified) " (modified)" else ""
                    Chip(label, accent = p.id == chosenId) { chosenId = if (chosenId == p.id) null else p.id; notice = null }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (chosen != null) {
            PresetDetails(chosen, appearance, theme, isActive = chosen.id == active?.id, modified = modified, applying = pending != null,
                onApply = { applyPreset(chosen) })
            Spacer(Modifier.height(8.dp))
        }
        ChipFlow {
            Chip("Undo", accent = false, enabled = store.hasUndo()) {
                store.undo(appearance)?.let { push(it) }
            }
            Chip("Save as my preset…", accent = true, enabled = customs.size < LayoutPresetStore.MAX_CUSTOM) { naming = "" }
            Chip("Manage", accent = false, enabled = customs.isNotEmpty()) { managing = true }
            Chip("Paste a shared look", accent = false, enabled = customs.size < LayoutPresetStore.MAX_CUSTOM) {
                val clip = runCatching {
                    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .primaryClip?.getItemAt(0)?.text?.toString()
                }.getOrNull().orEmpty()
                val added = store.importCode(clip)
                notice = if (added != null) "Added \"${added.name}\" — it's under Your presets." else "Copy a Voltcap look code first (it starts with ${LayoutPresetStore.CODE_PREFIX})."
                if (added != null) chosenId = added.id
                rev++
            }
        }
        if (customs.size >= LayoutPresetStore.MAX_CUSTOM) {
            Spacer(Modifier.height(6.dp))
            MutedNote("You can keep up to ${LayoutPresetStore.MAX_CUSTOM} of your own presets. Delete one to save another.")
        }
    }

    naming?.let { target ->
        var name by remember(target) { mutableStateOf(customs.firstOrNull { it.id == target }?.name ?: "My look ${customs.size + 1}") }
        val done = {
            if (target.isEmpty()) {
                val saved = store.saveCurrent(name, appearance, theme)
                notice = if (saved != null) "Saved \"${saved.name}\"." else "Preset limit reached."
            } else store.rename(target, name)
            naming = null
            rev++
        }
        ShelfDialog(
            icon = Icons.Filled.Edit,
            title = if (target.isEmpty()) "Save current look" else "Rename preset",
            subtitle = "Give it a name — it shows up with the presets above. Saves the look and layout options and the theme.",
            onDismiss = { naming = null },
            maxContentHeightFraction = 0.5f
        ) {
            ShelfTextField(name, { name = it.take(40) }, "Preset name", onSubmit = { done() })
            Spacer(Modifier.height(12.dp))
            Chip(if (target.isEmpty()) "Save" else "Rename", enabled = name.isNotBlank()) { done() }
        }
    }

    if (managing) {
        ShelfMenuDialog(title = "Your presets", subtitle = "Rename or delete.", onDismiss = { managing = false }) {
            if (customs.isEmpty()) MutedNote("No saved presets.")
            customs.forEach { c ->
                Row(
                    Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(c.name, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(14.sp), maxFontSize = 14.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    Chip("Rename", accent = false) { managing = false; naming = c.id }
                    Chip("Share", accent = false) {
                        store.exportCode(c.id)?.let { code ->
                            runCatching {
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(android.content.Intent.EXTRA_SUBJECT, "Voltcap look: ${c.name}")
                                    .putExtra(android.content.Intent.EXTRA_TEXT, code)
                                context.startActivity(android.content.Intent.createChooser(send, "Share ${c.name}").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                            runCatching {
                                (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                    .setPrimaryClip(android.content.ClipData.newPlainText("Voltcap look", code))
                            }
                        }
                    }
                    Chip("Delete", accent = false) { store.delete(c.id); rev++ }
                }
            }
            Spacer(Modifier.height(8.dp))
            Chip("Done") { managing = false }
        }
    }
}

/** The chosen preset: a drawn preview, its explanation, what it changes, and Apply. */
@Composable
private fun PresetDetails(
    p: com.shelf.launcher.data.LayoutPreset,
    appearance: AppearanceSettings,
    theme: AppTheme,
    isActive: Boolean,
    modified: Boolean,
    applying: Boolean,
    onApply: () -> Unit
) {
    // What the screen would look like: the preset applied to your settings (nothing is changed yet).
    val after = remember(p.id, appearance) { p.apply(appearance) }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Side by side when there's room, else the preview on top.
        val wide = maxWidth > 460.dp
        val preview: @Composable (Modifier) -> Unit = { m ->
            // Update 162: the preset's own game look switches, carousel and layout options (else as they are now).
            val f = com.shelf.launcher.data.Features
            PresetPreview(
                layout = after.homeLayout,
                background = after.backgroundStyle,
                focusEffect = after.focusEffect,
                cardScale = when (after.cardSize) {
                    com.shelf.launcher.data.CardSize.SMALL -> 0.85f
                    com.shelf.launcher.data.CardSize.MEDIUM -> 1f
                    com.shelf.launcher.data.CardSize.LARGE -> 1.15f
                },
                showLegend = after.showLegend,
                modifier = m,
                art = after.cardArtType,
                corners = after.cardCorners,
                tiles = p.look?.tiles ?: f.squareTiles.state.value,
                spines = p.look?.spines ?: f.spineLook.state.value,
                carts = p.look?.carts ?: f.cartsOnly.state.value,
                models = p.look?.models ?: (f.modelFinish.state.value != ModelFinish.OFF),
                carousel = presetCarousel(p, after.homeLayout),
                options = p.features
            )
        }
        // Lists with games from several consoles (Recently played, Favourites…): the same look, or plain covers.
        val media = after.cardArtType.wantsMedia()
        var mixToo by remember(p.id) { mutableStateOf(true) }
        val text: @Composable () -> Unit = {
            Text(p.name, color = ShelfTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(p.description, color = ShelfTheme.colors.textPrimary.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(6.dp))
            MutedNote(presetChanges(p, appearance, after, theme))
            if (media) {
                Spacer(Modifier.height(6.dp))
                Chip(
                    if (mixToo) "✓ Also in mixed lists" else "Mixed lists: plain covers", accent = mixToo
                ) { mixToo = !mixToo }
                MutedNote(
                    if (mixToo) "Recently played, Favourites and other lists with several consoles show the same look, each game in its own shape."
                    else "Lists with several consoles show plain covers, all one size."
                )
            }
            Spacer(Modifier.height(8.dp))
            val apply = {
                if (media) com.shelf.launcher.data.Features.mixedArt.value =
                    if (mixToo) com.shelf.launcher.ui.MixedArt.AS_CHOSEN else com.shelf.launcher.ui.MixedArt.COVER
                onApply()
            }
            when {
                applying -> Chip("Applying…", enabled = false) {}
                isActive && !modified -> Chip("✓ In use", accent = false, enabled = false) {}
                isActive -> Chip("Reset to preset") { apply() }
                else -> Chip("Apply") { apply() }
            }
        }
        val box = Modifier.fillMaxWidth().voltSurface(uiRounded(14.dp), alpha = 0.85f).padding(12.dp)
        if (wide) Row(box, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            preview(Modifier.width(220.dp))
            Column(Modifier.weight(1f)) { text() }
        } else Column(box) {
            preview(Modifier.widthIn(max = 420.dp))
            Spacer(Modifier.height(10.dp))
            text()
        }
    }
}

/** What a home layout is, in a sentence (Settings › Home › Layout & presets). */
internal fun layoutBlurb(l: com.shelf.launcher.data.HomeLayout): String = when (l) {
    com.shelf.launcher.data.HomeLayout.CLASSIC -> "Voltcap row: one row of games along the screen, the game you're on bigger, its name and info next to the row."
    com.shelf.launcher.data.HomeLayout.TILES -> "Tile home: big square tiles in one row, the name above the one you're on, round shortcut buttons under them."
    com.shelf.launcher.data.HomeLayout.HERO -> "Hero carousel: the game's art full screen, a curved column of games beside it, pages of info with ◀ ▶."
    com.shelf.launcher.data.HomeLayout.GRID -> "Grid: the whole category as a grid that fills the screen. ◀ ▶ ▲ ▼ move."
    com.shelf.launcher.data.HomeLayout.TV -> "TV room: the game plays on an old TV in a lit room, the games beside it, its story underneath."
    com.shelf.launcher.data.HomeLayout.DASH -> "Dashboard: glass panels — a round-button menu, the game big in Now playing, Quick access, player and status cards."
    com.shelf.launcher.data.HomeLayout.LIVE -> "Live tiles: flat tiles on a pale backdrop, category words on top, one big tile and four small, coloured shortcut tiles."
    com.shelf.launcher.data.HomeLayout.SIDE -> "Side menu: a dark menu with an emblem on the left, the game's art behind, the games along the bottom."
    com.shelf.launcher.data.HomeLayout.CROSS -> "Cross menu: categories as icons across the screen, the games as a list going down. ◀ ▶ category, ▲ ▼ game."
    com.shelf.launcher.data.HomeLayout.SPOT -> "Spotlight: the game's art full screen, small tiles on top, a big title, Play and activity cards."
    com.shelf.launcher.data.HomeLayout.BAR -> "Icon bar: shortcut icons along the top, a row of square tiles with the selected one big, Start under it."
    com.shelf.launcher.data.HomeLayout.HUB -> "Console hub: the game's art full screen, tiles with the one you're on big, and Play / Achievements / Details cards under them."
    com.shelf.launcher.data.HomeLayout.RESUME -> "Resume & discover: the game you're on as a big square with shortcut tiles under it, the next games in a row, Discover cards."
    com.shelf.launcher.data.HomeLayout.CHAN -> "Channel grid: pages of wide tiles on a pale backdrop, a curved bar with the clock, round buttons at the sides."
    com.shelf.launcher.data.HomeLayout.CIRCUIT -> "Circuit board: Voltcap's own — your games as chips zig-zagging along a glowing power line, the one you're on powered up, a readout and a power switch to play."
    // Update 146.
    com.shelf.launcher.data.HomeLayout.LIBRARY -> "Library panel: the game you're on in a panel on the left — cover, play time, achievements, Details and Play — and a grid of the category's games on the right, tabs on top."
    // (Update 162: the tool strip, the bottom panel and the carousel views it has now.)
    com.shelf.launcher.data.HomeLayout.STACKS -> "Case stacks: the game you're on as a big case in the middle, the others as stacks of spines left and right (or the carousel, like its Box flow), a tool strip on top and a panel along the bottom."
    // Update 151: the history look.
    com.shelf.launcher.data.HomeLayout.TIMELINE -> "Timeline: your games as a history, a row for each month or year by when you last played them (or release year, or when added)."
    com.shelf.launcher.data.HomeLayout.ARCADE -> "Arcade cabinets: the game you're on on an arcade cabinet's screen with its marquee lit on top, the others as a row of small cabinets."
    com.shelf.launcher.data.HomeLayout.CONSOLES -> "Console stack: your consoles as a pile of consoles; the one you're on slides out its games."
    com.shelf.launcher.data.HomeLayout.DISCS -> "Disc station: one disc player — the game you're on is the disc in the tray, ejected and swapped as you move, its name, play time, achievements and a few lines about it beside it."
    // Update 158.
    com.shelf.launcher.data.HomeLayout.HERO_TILES -> "Hero tiles: the game you're on as a big picture across the top, one or two rows of square tiles under it, an app dock and round buttons along the bottom."
    // Update 158: the two-screen layouts.
    com.shelf.launcher.data.HomeLayout.TWIN_DESK -> "Twin desk: for two screens — a clock and this month's calendar on squared paper on top, your games as wide panels with round buttons on the second screen. With one screen, both halves show together."
    com.shelf.launcher.data.HomeLayout.TWIN_ICONS -> "Twin icons: for two screens — the game you're on as a big framed icon on top, a grid of square icons with tool buttons and an Open bar on the second screen. With one screen, both halves show together."
    // Update 158: Record crate.
    com.shelf.launcher.data.HomeLayout.CRATE -> "Record crate: your games as record sleeves in a wooden crate. Flip through them; the one you're on lifts out and its record slides out and turns. Its name, play time, achievements and Play beside it."
    // Update 158: Card binder.
    com.shelf.launcher.data.HomeLayout.CARDS -> "Card binder: your games as collectible cards — the cover in a framed window, the console as its type, time played, achievements and rarity stars at the bottom. The one you're on stands big in the middle, the others fan out like a hand; Y turns it over."
    // Update 176.
    // Update 181.
    com.shelf.launcher.data.HomeLayout.POCKET_CLASSIC -> "Pocket classic: a 2004-style two-screen pocket menu — a clock and this month's calendar on top; the game you're on, Achievements, your category and All categories as flat panels below, three tiny icons for Settings, Statistics and Sleep."
    com.shelf.launcher.data.HomeLayout.POCKET_RIBBON -> "Pocket ribbon: a 2008-style two-screen pocket menu — a framed picture on top; a row of square icons below with the one you're on in the middle, its name in a speech bubble and a START tab, your categories first and their games inside."
    com.shelf.launcher.data.HomeLayout.CUSTOM -> "Custom layout: a home screen made by anyone — import a .vclayout file, or make your own (Layout options › How to make one). Starts with the Neon drift example."
    com.shelf.launcher.data.HomeLayout.SHELF -> "Game shelf: your games as spines on a bookcase, the one you're on pulled out to show its front. ◀ ▶ along, ▲ ▼ to the shelf above / below."
}

/**
 * Update 162: what a built-in preset sets besides the look settings — the game look's switches (as Settings › Game look
 * sets them), the layout's own options and its carousel. Called right after the store took its undo point, so Undo
 * puts all of it back too.
 */
private fun applyPresetExtras(p: com.shelf.launcher.data.LayoutPreset, after: AppearanceSettings) {
    val f = com.shelf.launcher.data.Features
    p.look?.let { l ->
        val type = after.cardArtType
        f.squareTiles.value = l.tiles
        f.spineLook.value = l.spines
        f.cartsOnly.value = l.carts
        // (As the Game look picker does: the 3D models are switched only with the cartridges & discs — the finish also
        // dresses the carousels' 3D cases — and the case switches only with a case look.)
        if (type == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC) setModelsLook(l.models)
        if (type == com.shelf.launcher.data.CardArtType.CASE || type == com.shelf.launcher.data.CardArtType.CASE_REAL) {
            f.discCase.value = l.discInCase
            f.discCase3d.value = l.disc3d
            f.caseWithMedia.value = l.leaning
        }
        // (The Grid's square tiles would hide every look but the covers.)
        if (type != com.shelf.launcher.data.CardArtType.COVER) f.gridTiles.value = false
    }
    if (p.features.isNotEmpty()) f.restore(p.features)
    val layout = p.layout
    val on = p.carousel
    if (layout != null && on != null) {
        val set = f.carouselLayouts.value
        if (on != (layout.name in set)) f.carouselLayouts.value = if (on) set + layout.name else set - layout.name
    }
}

/** Update 162: the game look's switches already as [l] says, for the card artwork [type]. */
private fun lookIsNow(type: com.shelf.launcher.data.CardArtType, l: com.shelf.launcher.data.PresetLook): Boolean {
    val f = com.shelf.launcher.data.Features
    return when (type) {
        com.shelf.launcher.data.CardArtType.COVER -> f.squareTiles.state.value == l.tiles && f.spineLook.state.value == l.spines
        com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC -> f.cartsOnly.state.value == l.carts && (f.modelFinish.state.value != ModelFinish.OFF) == l.models
        com.shelf.launcher.data.CardArtType.CASE, com.shelf.launcher.data.CardArtType.CASE_REAL ->
            f.discCase.state.value == l.discInCase && f.discCase3d.state.value == l.disc3d && f.caseWithMedia.state.value == l.leaning
        else -> true
    }
}

/**
 * Update 162: the carousel view [p] leaves [layout] showing its games in, or null when it shows its own row / grid
 * (the preset's own carousel switch and view, else as they are now; the TV room's carousel is its Games choice).
 */
private fun presetCarousel(p: com.shelf.launcher.data.LayoutPreset, layout: com.shelf.launcher.data.HomeLayout): CarouselView? {
    val f = com.shelf.launcher.data.Features
    val on = if (layout == com.shelf.launcher.data.HomeLayout.TV)
        (p.features[f.tvRoomGames.key] ?: f.tvRoomGames.state.value.name) == TvRoom.Games.CAROUSEL.name
    else p.carousel ?: (layout.name in f.carouselLayouts.state.value)
    if (!on) return null
    val name = p.features[f.carouselView.key] ?: f.carouselView.state.value.name
    return CarouselView.values().firstOrNull { it.name == name }
}

/** One line saying what applying [p] changes, compared with your settings now. */
private fun presetChanges(
    p: com.shelf.launcher.data.LayoutPreset, now: AppearanceSettings, after: AppearanceSettings, theme: AppTheme
): String {
    val parts = mutableListOf<String>()
    parts += if (after.homeLayout != now.homeLayout) "Layout: ${after.homeLayout.label}" else "Keeps your layout (${now.homeLayout.label})"
    // Update 162: the game look by its full name (Tile, Cartridges, 3D models…), or that it stays as it is.
    val look = p.look
    when {
        look != null -> {
            if (after.cardArtType != now.cardArtType || !lookIsNow(after.cardArtType, look))
                // (Update 162 (review): "games as tiles", not "games as tile".)
                parts += "games as ${(look.label()?.let { if (it == "Tile") "Tiles" else it } ?: gameLookLabel(after.cardArtType)).replaceFirstChar { it.lowercase() }}"
        }
        after.cardArtType != now.cardArtType -> { parts += "games as ${gameLookLabel(after.cardArtType).lowercase()}" }
        p.builtIn -> { parts += "keeps your game look" }
    }
    if (after.cardSize != now.cardSize) parts += "${after.cardSize.label} cards"
    if (after.cardCorners != now.cardCorners) parts += "${after.cardCorners.label.lowercase()} corners"
    if (after.focusEffect != now.focusEffect) parts += "focus: ${after.focusEffect.label.lowercase()}"
    if (after.backgroundStyle != now.backgroundStyle) parts += "background: ${after.backgroundStyle.label.lowercase()}"
    if (after.density != now.density) parts += "${after.density.label.lowercase()} spacing"
    if (after.otherGamesLook != now.otherGamesLook) parts += "other games ${after.otherGamesLook.label.lowercase()}"
    if (after.focusedCardStyle != now.focusedCardStyle) parts += "game you're on: ${after.focusedCardStyle.label.lowercase()}"
    if (after.showLegend != now.showLegend) parts += if (after.showLegend) "button legend on" else "button legend off"
    if (after.showStatusBar != now.showStatusBar) parts += if (after.showStatusBar) "status bar on" else "status bar off"
    if (after.useSecondScreen != now.useSecondScreen) parts += if (after.useSecondScreen) "second screen on" else "second screen off"
    // Update 162: the carousel and the layout's own options.
    val f = com.shelf.launcher.data.Features
    val view = presetCarousel(p, after.homeLayout)
    if (view != null && (p.carousel == true || p.features.containsKey(f.tvRoomGames.key))) {
        parts += "carousel: ${view.label.lowercase()}"
    } else if (p.carousel == false && after.homeLayout.name in f.carouselLayouts.state.value) {
        parts += "carousel off"
    }
    val carouselKeys = setOf(f.carouselView.key, f.carouselEdge.key, f.carouselReflect.key, f.carouselStage.key)
    val opts = p.features.filterKeys { it !in carouselKeys }
    if (opts.isNotEmpty()) {
        val cur = f.snapshot { it !in opts }
        if (opts.any { (k, v) -> cur[k] != null && cur[k] != v }) parts += "${after.homeLayout.label} options"
    }
    parts += p.theme?.let { if (it != theme) "theme: ${it.label}" else null } ?: "keeps your theme"
    return parts.joinToString(" · ")
}
