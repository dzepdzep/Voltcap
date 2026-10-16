package com.shelf.launcher.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Game
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Update 195: the game's menu › Cartridge shell… — the whole cartridge library in one place: every console's real shell
 * (Authentic, by maker, searchable) and your cartridge pack's shapes (Your custom shells). The game stays what it is
 * (its console, emulator and file don't change): the shell only changes how it's drawn, its label showing the game's
 * own art and its real console's name. For this game or for every game of its console; any colour or clear plastic.
 */
@Composable
internal fun CartShellPicker(game: Game, cover: Any?, onDismiss: () -> Unit) {
    val platform = game.platform
    val brand = remember(platform) { com.shelf.launcher.data.ConsoleNames.display(platform) }
    var forConsole by remember { mutableStateOf(CartShellStore.forGame(game.stableKey) == null && CartShellStore.forConsole(platform) != null) }
    val current = if (forConsole) CartShellStore.forConsole(platform) else CartShellStore.forGame(game.stableKey)
    val currentChoice = CartShells.parse(current)
    val currentId0 = current?.substringBefore('~')
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    var group by remember { mutableStateOf<ShellGroup?>(null) }
    var colour by remember { mutableStateOf(currentChoice?.colourKey ?: "") }
    val region = remember(game.stableKey) { CaseCatalog.regionOf(game.romPath ?: game.name) }
    val original = remember(platform, region) { CartShells.authenticIdFor(platform, region, game.stableKey) }
    // Update 200: a 3D piece saved before ("m3d:<id>", the retired "Your 3D pieces" tab) is drawn as this console's own
    // shell now (CartShells.pickOf), so that's the one shown as picked — and a colour picked goes onto it.
    val currentId = currentId0?.let { if (it.startsWith("m3d:")) original ?: it else it }
    val packId = CustomCarts.idFor(platform)
    val packHere = packId != null && com.shelf.launcher.data.Packs.hasCart(packId)

    fun store(value: String?) {
        if (forConsole) CartShellStore.setConsole(platform, value) else CartShellStore.setGame(game.stableKey, value)
    }
    fun pick(id: String) = store("$id~$colour")

    ShelfDialog(
        icon = Icons.Filled.Memory,
        title = "Cartridge shell",
        subtitle = "${gameDisplayName(game.name)} · stays a $brand game",
        onDismiss = onDismiss,
        widthFraction = 0.94f,
        scrollable = false,
        footer = {
            Chip("Its own look (no shell)", accent = false) { store(null) }
            Chip("Done", onClick = onDismiss)
        }
    ) {
        // Update 212: an Android game — a disc or a cartridge first (AndroidMedia.kt); the shells below are for the cartridge.
        if (AndroidMedia.isAndroid(platform)) AndroidMediaGameChoice(platform, game.stableKey)
        ShelfTextField(query, { query = it }, "Search shells (NES, Genesis, card…)", leadingIcon = Icons.Filled.Search)
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            Chip("Authentic", accent = tab == 0) { tab = 0 }
            Chip("Your custom shells", accent = tab == 1) { tab = 1 }
            // (Update 200: "Your 3D pieces" is gone — the pre-rendered 3D models are retired; the drawn shells here turn
            // in real 3D themselves.)
            Chip("For every $brand game", accent = forConsole) {
                // (Moves the current choice over: from this game to the console, or back.)
                val v = current
                store(null)
                forConsole = !forConsole
                if (v != null) { if (forConsole) CartShellStore.setConsole(platform, v) else CartShellStore.setGame(game.stableKey, v) }
            }
        }
        if (tab == 0) {
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                Chip("All", accent = group == null) { group = null }
                ShellGroup.values().forEach { g -> Chip(g.label, accent = group == g) { group = g } }
            }
        }
        // Update 210: this game's real editions (MediaEditions) — its own plastic or special shell, with how sure each is.
        val editionsOn = com.shelf.launcher.data.Features.realEditions.state.value
        val editions = remember(platform, game.stableKey) { MediaEditions.forGame(platform, game.stableKey) }
        val autoEdition = remember(platform, game.stableKey, editionsOn) { MediaEditions.auto(platform, game.stableKey) }
        if (editions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Real editions of this game", color = ShelfTheme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                editions.forEach { e ->
                    val raw = e.raw(platform, game.stableKey)
                    if (raw != null) EditionCard(e, raw, platform, cover, game, selected = current == raw, picked = autoEdition?.id == e.id && current == null) {
                        colour = raw.substringAfter('~', ""); store(raw)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // Update 210: the plastics by kind (ShellMaterials) — your own pick is always a custom appearance.
        var kind by remember { mutableStateOf<MaterialKind?>(ShellMaterials.of(colour.removePrefix("m:"))?.kind) }
        ChipFlow {
            Chip("Shell's own colour", accent = colour.isEmpty()) { colour = ""; currentId?.let { pick(it) } }
            MaterialKind.values().forEach { k -> Chip(k.label, accent = kind == k) { kind = if (kind == k) null else k } }
        }
        kind?.let { k ->
            Spacer(Modifier.height(4.dp))
            ChipFlow {
                ShellMaterials.ofKind(k).forEach { m -> Chip(m.name, accent = colour == "m:${m.id}") { colour = "m:${m.id}"; currentId?.let { pick(it) } } }
            }
        }
        ShellMaterials.of(colour.removePrefix("m:"))?.takeIf { colour.startsWith("m:") }?.let { m ->
            val fromEdition = editions.any { it.material == m.id && current == it.raw(platform, game.stableKey) }
            MutedNote(
                "${m.name} · ${ShellMaterials.hexOf(m.base)}" + (if (m.back != 0) " front / ${ShellMaterials.hexOf(m.back)} back" else "") +
                    (if (m.transmission > 0f) " · see-through ${(m.transmission * 100).toInt()} %" else "") +
                    (if (fromEdition) " · real edition" else " · custom appearance") + ". " + m.note
            )
        }
        Spacer(Modifier.height(8.dp))
        val q = query.trim().lowercase()
        val ids: List<Pair<String, String>> = if (tab == 0) {
            CartShells.all.filter { s -> (group == null || s.group == group) && (q.isEmpty() || s.name.lowercase().contains(q) || s.id.contains(q)) }
                .sortedBy { if (it.id == original) 0 else 1 }
                .map { it.id to it.name }
        } else if (packHere) {
            CartLook.values().filter { q.isEmpty() || it.label.lowercase().contains(q) }.map { "pack:${it.key}" to "${it.label} (your pack)" }
        } else emptyList()
        if (tab == 1 && !packHere) MutedNote("Your cartridge pack isn't on this device for $brand: Settings › Game look › Extra art packs. Its shapes show here once it is.")
        if (ids.isEmpty() && q.isNotEmpty()) MutedNote("Nothing matches \"$query\".")
        LazyVerticalGrid(
            GridCells.Adaptive(118.dp), Modifier.fillMaxWidth().heightIn(max = 360.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            items(ids, key = { it.first }) { (id, name) ->
                val selected = id == currentId
                val shell = CartShells.of(id)
                Column(
                    Modifier.clip(uiRounded(12.dp))
                        .border(if (selected) 2.dp else 1.dp, if (selected) ShelfTheme.accent else Color.White.copy(alpha = 0.08f), uiRounded(12.dp))
                        .padTarget("shell-$id") { pick(id) }
                        .clickable { pick(id) }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                        CartShellArt("$SHELL_PREFIX$id~$colour|$platform", cover, Modifier.fillMaxSize(), title = gameDisplayName(game.name), maxPx = 240)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        (if (id == original) "★ " else "") + name, color = ShelfTheme.colors.textPrimary, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                    )
                    if (shell != null) Text(shell.check.label, color = ShelfTheme.textMuted, fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        MutedNote("★ is this game's own console's shell. Sizes and shapes come from measurements where they were found; \"Estimated\" and \"Placeholder\" ones are drawn from photos and memory (CARTRIDGES.md lists them).")
    }
}

/**
 * Update 210: one real edition of the game in Cartridge shell…: its piece drawn, its name, how sure it is and where it
 * was sold, its plastic's approximate colour and its source.
 */
@Composable
private fun EditionCard(e: Edition, raw: String, platform: String, cover: Any?, game: Game, selected: Boolean, picked: Boolean, onPick: () -> Unit) {
    val m = ShellMaterials.of(e.material)
    Column(
        Modifier.width(150.dp).clip(uiRounded(12.dp))
            .border(if (selected || picked) 2.dp else 1.dp, if (selected || picked) ShelfTheme.accent else Color.White.copy(alpha = 0.08f), uiRounded(12.dp))
            .padTarget("edition-${e.id}") { onPick() }
            .clickable { onPick() }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.Center) {
            CartShellArt("$SHELL_PREFIX$raw|$platform", cover, Modifier.fillMaxSize(), title = gameDisplayName(game.name), maxPx = 240)
        }
        Spacer(Modifier.height(4.dp))
        Text(e.name, color = ShelfTheme.colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(
            e.status.label + " · " + e.regions.sorted().joinToString("/") + (if (picked) " · Picked by itself" else ""),
            color = if (e.status == EditionStatus.PENDING) Color(0xFFE8A33D) else ShelfTheme.textMuted, fontSize = 9.sp, maxLines = 2, textAlign = TextAlign.Center
        )
        if (m != null) Text(
            ShellMaterials.hexOf(m.base) + (if (m.back != 0) " / " + ShellMaterials.hexOf(m.back) else "") + " (approx.)",
            color = ShelfTheme.textMuted, fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center
        )
        e.sources.firstOrNull()?.let { src ->
            Text(src.substringAfter("://").substringBefore('/').removePrefix("www."), color = ShelfTheme.textMuted, fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}
