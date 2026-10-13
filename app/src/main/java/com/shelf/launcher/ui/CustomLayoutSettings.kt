package com.shelf.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.CustomLayouts
import com.shelf.launcher.data.CustomScene
import com.shelf.launcher.data.Features
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Update 176: Settings › Home › Layout options while Custom layout is picked — the layouts you have (pick / delete),
 * importing (a file, or everything in the Voltcap/Layouts folder), saving the example to start from, and the guide.
 */

@Composable
internal fun CustomLayoutSettings() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var layouts by remember { mutableStateOf<List<CustomScene>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var guide by remember { mutableStateOf(false) }
    val version = CustomSceneNow.version
    LaunchedEffect(version) { layouts = withContext(Dispatchers.IO) { CustomLayouts.list(context) } }
    val picked = Features.customLayout.state.value.ifBlank { CustomLayouts.EXAMPLE_ID }

    fun done(r: CustomLayouts.Result) {
        status = r.message
        if (r.id != null) Features.customLayout.value = r.id
        CustomSceneNow.version++
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { done(withContext(Dispatchers.IO) { CustomLayouts.import(context, uri) }) }
    }
    val saveExample = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri, "w")?.use { CustomLayouts.writeExample(it) } != null }.getOrDefault(false)
            }
            status = if (ok) "Example saved. Open it as a zip, change layout.json, add your pictures and import it." else "Couldn't save the example."
        }
    }

    MutedNote(
        "Home screens anyone can make and share: a .vclayout file places the parts (the moving games, the game's art, its name, " +
            "text, the clock, pictures, shapes, Play and Details) and makes them move. Animated GIF and WebP pictures play."
    )
    Spacer(Modifier.height(10.dp))
    SectionLabel("Layouts")
    ChipFlow {
        layouts.forEach { l ->
            Chip(l.name, accent = l.id == picked) { Features.customLayout.value = if (l.id == CustomLayouts.EXAMPLE_ID) "" else l.id }
        }
    }
    layouts.firstOrNull { it.id == picked }?.let { l ->
        Spacer(Modifier.height(6.dp))
        MutedNote(listOfNotNull(l.author?.let { "By $it" }, "${l.parts.size} parts").joinToString(" · "))
        if (l.id != CustomLayouts.EXAMPLE_ID) {
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                Chip("Delete “${l.name}”", accent = false) {
                    scope.launch {
                        withContext(Dispatchers.IO) { CustomLayouts.delete(context, l.id) }
                        Features.customLayout.value = ""
                        status = "“${l.name}” deleted."
                        CustomSceneNow.version++
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    SectionLabel("Add layouts")
    ChipFlow {
        Chip("Import a file") { runCatching { importFile.launch(arrayOf("*/*")) }.onFailure { status = "No file picker on this device." } }
        Chip("Import from ${CustomLayouts.DROP_FOLDER}", accent = false) {
            scope.launch { done(withContext(Dispatchers.IO) { CustomLayouts.importFolder(context) }) }
        }
        Chip("Save the example", accent = false) { runCatching { saveExample.launch("neon-drift.vclayout") }.onFailure { status = "No file picker on this device." } }
        Chip(if (guide) "Hide the guide" else "How to make one", accent = false) { guide = !guide }
    }
    status?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, color = ShelfTheme.colors.textPrimary, fontSize = 12.sp)
    }
    MutedNote("Importing from the folder again replaces layouts with the same name — the quick way to try your changes.")
    if (guide) {
        Spacer(Modifier.height(10.dp))
        CustomLayoutGuide()
    }
}

/** The step-by-step guide (the full one: docs\CUSTOM_LAYOUTS.md in the project). */
@Composable
private fun CustomLayoutGuide() {
    Column {
        step(
            "1. Start from the example",
            "Tap Save the example and save neon-drift.vclayout on the handheld (or your PC). A .vclayout is a zip: rename it to " +
                ".zip to open it. Inside is layout.json — a text file you can edit with any text editor."
        )
        step(
            "2. Place the parts",
            "Every part has a type and a box: x and y are where its top-left corner is, w and h its width and height — all as " +
                "shares of the screen from 0 to 1 (0.5 is the middle). Parts are drawn in order: later ones on top."
        )
        code("{ \"type\": \"title\", \"x\": 0.06, \"y\": 0.12, \"w\": 0.55, \"h\": 0.2, \"size\": 0.08 }")
        step(
            "3. The part types",
            "games — the moving games (direction: row, column or arc; art: card, cover, square or wide; cardSize, focusSize, gap, " +
                "anchor, curve, names, glow, fadeEdges). art — the game you're on (art: backdrop, cover, wide, logo, screenshot or card). " +
                "title, info (show: console, playtime, lastPlayed, achievements, category, position), text, clock, category — words " +
                "(size is a share of the screen's height; color, align, bold, italic, font, shadow). image — a picture from the zip " +
                "(src). shape — rect, round or circle with color or gradient and border. buttons — Play and Details."
        )
        step(
            "4. Words that change",
            "In text parts, these are filled in: {name} {console} {playtime} {lastPlayed} {achievements} {category} {position} " +
                "{count} {time} {date}."
        )
        code("{ \"type\": \"text\", \"text\": \"{position} · {category}\", \"x\": 0.06, \"y\": 0.9, \"w\": 0.5, \"h\": 0.05 }")
        step(
            "5. Make it move",
            "loop keeps going: kind float, sway, drift, spin, rock, pulse, blink or turn, with amount and seconds (and delay, 0–1, " +
                "to set parts apart). enter plays when you move to another game: kind fade, slide-left, slide-right, slide-up, " +
                "slide-down, zoom, pop, flip or spin, with ms and delay (on: start plays it only when the layout appears)."
        )
        code("\"loop\": { \"kind\": \"float\", \"amount\": 0.01, \"seconds\": 5 },\n\"enter\": { \"kind\": \"zoom\", \"ms\": 400 }")
        step(
            "6. Background and colours",
            "background: color or gradient (two or more colours, top to bottom), gameArt (the game's art behind, with shade from 0 " +
                "to 1) and image (a picture from the zip on top — an animated GIF / WebP plays, with its own loop). Colours are " +
                "#RRGGBB or #AARRGGBB (AA is how solid: 00 clear, FF solid), or accent (your theme's colour), text, white, black."
        )
        step(
            "7. Pack it and import it",
            "Put layout.json and your pictures (PNG, JPG, WebP, GIF) side by side in a zip and name it something.vclayout. " +
                "Import it here with Import a file, or put it (or a folder with layout.json inside) in ${CustomLayouts.DROP_FOLDER} " +
                "on the handheld and tap Import from ${CustomLayouts.DROP_FOLDER}. A mistake in layout.json is shown here with " +
                "what's wrong. Up to 80 files and 48 MB."
        )
        step(
            "Controls",
            "◀ ▶ move along a row or arc (▲ ▼ in a column), ▼ (▶ in a column) goes to the buttons, A opens the game, tap a game " +
                "to pick it and again to open it. Share a layout by sending its .vclayout file."
        )
    }
}

@Composable
private fun step(title: String, body: String) {
    Text(title, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(2.dp))
    MutedNote(body)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun code(text: String) {
    Text(
        text, color = ShelfTheme.colors.textPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
    )
}
