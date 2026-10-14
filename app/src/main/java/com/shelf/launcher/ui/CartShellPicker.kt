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

/** Update 197: a shape-library piece's id as a name ("cart_gbc_atomic_purple_translucent" → "Gbc atomic purple, clear"). */
private fun pieceName(id: String): String {
    val clear = id.endsWith("_translucent")
    val base = id.removeSuffix("_translucent").removePrefix("cart_").removePrefix("card_").replace("_real", " (real)").replace('_', ' ')
    return base.replaceFirstChar { it.uppercase() } + if (clear) ", clear" else ""
}

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
    val currentId = current?.substringBefore('~')
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    var group by remember { mutableStateOf<ShellGroup?>(null) }
    var colour by remember { mutableStateOf(currentChoice?.colourKey ?: "") }
    val region = remember(game.stableKey) { CaseCatalog.regionOf(game.romPath ?: game.name) }
    val original = remember(platform, region) { CartShells.authenticId(platform, region) }
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
        ShelfTextField(query, { query = it }, "Search shells (NES, Genesis, card…)", leadingIcon = Icons.Filled.Search)
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            Chip("Authentic", accent = tab == 0) { tab = 0 }
            Chip("Your custom shells", accent = tab == 1) { tab = 1 }
            Chip("Your 3D pieces", accent = tab == 2) { tab = 2 }
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
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            Chip("Shell's own colour", accent = colour.isEmpty()) { colour = ""; currentId?.let { pick(it) } }
            CartShells.presets.forEach { c -> Chip(c.name, accent = colour == c.key) { colour = c.key; currentId?.let { pick(it) } } }
        }
        Spacer(Modifier.height(8.dp))
        val q = query.trim().lowercase()
        val ids: List<Pair<String, String>> = if (tab == 0) {
            CartShells.all.filter { s -> (group == null || s.group == group) && (q.isEmpty() || s.name.lowercase().contains(q) || s.id.contains(q)) }
                .sortedBy { if (it.id == original) 0 else 1 }
                .map { it.id to it.name }
        } else if (tab == 2) {
            // (Update 197: your shape library's own cartridges and cards, as the 3D models look draws them.)
            CartShells.libraryPieces().filter { q.isEmpty() || it.contains(q) }.map { "m3d:$it" to pieceName(it) }
        } else if (packHere) {
            CartLook.values().filter { q.isEmpty() || it.label.lowercase().contains(q) }.map { "pack:${it.key}" to "${it.label} (your pack)" }
        } else emptyList()
        if (tab == 1 && !packHere) MutedNote("Your cartridge pack isn't on this device for $brand: Settings › Game look › Extra art packs. Its shapes show here once it is.")
        if (tab == 2 && ids.isEmpty() && q.isEmpty()) MutedNote("No 3D pieces on this device yet: Settings › Game look › Extra art packs › 3D models.")
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
