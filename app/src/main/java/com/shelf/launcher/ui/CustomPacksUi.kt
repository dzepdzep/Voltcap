package com.shelf.launcher.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings › Theme › Your own themes & visualizers: add, look at, choose and delete custom packs, and the
 * how-to (the round "?" next to the title, and the "How to make one" chip).
 */
@Composable
internal fun CustomPacksCard() {
    com.shelf.launcher.Breadcrumbs.mark("CustomPacksCard")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Update 144: reading the pack files runs off the main thread; packs / revision are snapshot state,
    // so the card fills in when it's done.
    androidx.compose.runtime.LaunchedEffect(Unit) { withContext(Dispatchers.IO) { CustomPacks.ensureLoaded(context) } }
    var tutorial by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    // Checking and test-building a pack takes a moment: off the main thread, one job at a time.
    fun work(job: () -> List<CustomPacks.Outcome>) {
        if (busy) return
        busy = true
        scope.launch {
            val out = withContext(Dispatchers.Default) {
                try { job() } catch (e: Throwable) { listOf(CustomPacks.Outcome("", false, "Something went wrong: ${e.message}")) }
            }
            CustomPacks.lastMessages = out.map { o ->
                (if (o.ok) "✓ " else "✗ ") + (if (!o.ok && o.file.isNotBlank()) o.file + ": " else "") + o.message
            }
            busy = false
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) work { uris.take(PackRules.MAX_PACKS).map { importUri(context, it) } }
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionLabel("Your own themes & visualizers") }
            VoltHelpButton(VoltTopic.CUSTOM_PACKS, moreLabel = "How to make one", onMore = { tutorial = true })
        }
        MutedNote(
            "Add an animated theme or a music visualizer you made (or got from someone) as one small text file. " +
                "Voltcap checks it against its rules first, test-builds it on this device, and draws it smaller and slower " +
                "than the built-in ones, so it can't overload the app."
        )
        if (Build.VERSION.SDK_INT < 33) {
            Spacer(Modifier.height(6.dp))
            MutedNote("Custom packs are drawn on Android 13 or newer. On this device they are kept, but the normal theme shows instead.")
        }
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            Chip("Import files", enabled = !busy) { pick.launch(arrayOf("*/*")) }
            Chip("Look in Voltcap/Custom packs", accent = false, enabled = !busy) { work { CustomPacks.scanInbox(context) } }
            Chip("Add the examples", accent = false, enabled = !busy) { work { CustomPacks.addExamples(context) } }
            Chip("How to make one", accent = false) { tutorial = true }
        }
        if (busy) {
            Spacer(Modifier.height(6.dp))
            MutedNote("Checking…")
        }
        val messages = CustomPacks.lastMessages
        if (messages.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            messages.forEach { MutedNote(it); Spacer(Modifier.height(2.dp)) }
            Spacer(Modifier.height(4.dp))
            ChipFlow { Chip("Clear messages", accent = false) { CustomPacks.lastMessages = emptyList() } }
        }

        Spacer(Modifier.height(10.dp))
        SectionLabel("Added (${CustomPacks.packs.size} of ${PackRules.MAX_PACKS})")
        if (CustomPacks.packs.isEmpty()) {
            MutedNote("Nothing yet. \"Add the examples\" gives you two to start from.")
        } else {
            CustomPacks.packs.forEach { p ->
                val inUse = CustomPacks.selectedId(p.kind) == p.id
                val status = when {
                    !p.report.ok -> "doesn't follow the rules"
                    CustomPacks.isOff(p.id) -> "switched off: it was slow"
                    inUse -> "in use"
                    else -> "not in use"
                }
                val by = if (p.author.isBlank()) "" else " · by ${p.author}"
                MenuRow(
                    icon = if (p.kind == PackKind.THEME) Icons.Filled.Tune else Icons.Filled.GraphicEq,
                    title = p.name,
                    subtitle = "${p.kind.label}$by · $status · ${p.report.costPercent} % of the budget"
                ) { detail = p.id }
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        MutedNote(
            "To see one: pick \"Your theme\" or \"Your visualizer\" under Your own in the theme list " +
                "(Behind the dock set to Animated theme). Which pack they show is chosen here."
        )
        MutedNote("Pack files live only on this device (a backup keeps the choice, not the files).")
    }

    val open = detail?.let { id -> CustomPacks.packs.firstOrNull { it.id == id } }
    if (open != null) PackDetailDialog(open) { detail = null }
    if (tutorial) PackTutorialDialog { tutorial = false }
}

private fun importUri(context: Context, uri: Uri): CustomPacks.Outcome {
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: "file"
    val limit = "Too big (limit ${PackRules.MAX_BYTES / 1000} KB)."
    val text = try {
        val input = context.contentResolver.openInputStream(uri) ?: return CustomPacks.Outcome(name, false, "Couldn't open it.")
        input.use { stream ->
            val buf = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(4096)
            var total = 0
            while (true) {
                val n = stream.read(chunk)
                if (n <= 0) break
                total += n
                if (total > PackRules.READ_CAP) return CustomPacks.Outcome(name, false, limit)
                buf.write(chunk, 0, n)
            }
            buf.toString("UTF-8")
        }
    } catch (e: Throwable) {
        return CustomPacks.Outcome(name, false, "Couldn't read it.")
    }
    return CustomPacks.importText(context, name, text)
}

/** One pack: how heavy it is, whether it runs here, use / switch back on / copy / delete. */
@Composable
private fun PackDetailDialog(pack: CustomPack, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var confirmDelete by remember { mutableStateOf(false) }
    val inUse = CustomPacks.selectedId(pack.kind) == pack.id
    val off = CustomPacks.isOff(pack.id)
    // Update 144: the test build runs off the main thread (it used to hitch the D-pad on the first open).
    // null = still checking, "" = builds fine, else the reason it doesn't.
    val buildState by androidx.compose.runtime.produceState<String?>(if (pack.report.ok) null else "", pack.id, pack.text) {
        value = if (pack.report.ok) withContext(Dispatchers.Default) { CustomPacks.compileProblem(pack).orEmpty() } else ""
    }
    val buildProblem = buildState
    val checking = buildProblem == null
    val usable = pack.report.ok && buildProblem == ""
    ShelfDialog(
        icon = if (pack.kind == PackKind.THEME) Icons.Filled.Tune else Icons.Filled.GraphicEq,
        title = pack.name,
        subtitle = pack.kind.label + if (pack.author.isBlank()) "" else " by ${pack.author}",
        onDismiss = onDismiss
    ) {
        Column {
            SectionLabel("How heavy it is: ${pack.report.costPercent} % of the budget")
            BudgetMeter(pack.report.costPercent)
            Spacer(Modifier.height(4.dp))
            MutedNote("Over 100 % isn't allowed. Fewer loops and fewer fbm / noise calls make it lighter.")
            if (!pack.report.ok) {
                Spacer(Modifier.height(8.dp))
                SectionLabel("Why it can't be used")
                pack.report.problems.forEach { MutedNote("• $it"); Spacer(Modifier.height(2.dp)) }
            } else if (!buildProblem.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                SectionLabel("It doesn't build on this device")
                MutedNote(buildProblem)
            } else if (checking) {
                Spacer(Modifier.height(8.dp))
                MutedNote("Checking it builds on this device…")
            }
            if (off) {
                Spacer(Modifier.height(8.dp))
                MutedNote("Voltcap switched it off because the home screen ran slowly with it. You can turn it back on, or make it lighter and import it again.")
            }
            Spacer(Modifier.height(10.dp))
            ChipFlow {
                if (inUse && !off) Chip("✓ In use: stop using") { CustomPacks.select(pack.kind, "") }
                else if (usable) Chip(if (off) "Turn back on and use it" else "Use it") { CustomPacks.select(pack.kind, pack.id) }
                Chip("Copy its text", accent = false) { clipboard.setText(AnnotatedString(pack.text)) }
                Chip(if (confirmDelete) "Tap again to delete" else "Delete", accent = false) {
                    if (confirmDelete) { CustomPacks.delete(context, pack); onDismiss() } else confirmDelete = true
                }
            }
            Spacer(Modifier.height(6.dp))
            MutedNote(
                if (pack.kind == PackKind.THEME) "It shows when \"Your theme\" is the theme (or the animated scene)."
                else "It shows when \"Your visualizer\" is the theme, with the song's cover colours while music plays."
            )
        }
    }
}

@Composable
private fun BudgetMeter(percent: Int) {
    val shape = RoundedCornerShape(50)
    val over = percent > 100
    val fill = (percent / 100f).coerceIn(0.02f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.10f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fill)
                .height(8.dp)
                .clip(shape)
                .background(if (over) Color(0xFFB5583C) else ShelfTheme.accent)
        )
    }
}

@Composable
private fun CodeBlock(text: String) {
    Text(
        text,
        color = ShelfTheme.colors.textPrimary,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.30f))
            .padding(10.dp)
    )
}

@Composable
private fun TutorialPart(title: String, vararg lines: String) {
    Spacer(Modifier.height(12.dp))
    SectionLabel(title)
    lines.forEach { MutedNote(it); Spacer(Modifier.height(3.dp)) }
}

/** The how-to behind the round "?": what a pack is, the file, what you can use, the rules, two examples to copy. */
@Composable
internal fun PackTutorialDialog(onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    ShelfDialog(
        icon = Icons.Filled.Tune,
        title = "Make your own",
        subtitle = "Themes & visualizers",
        onDismiss = onDismiss,
        widthFraction = 0.94f,
        maxContentHeightFraction = 0.78f
    ) {
        Column {
            MutedNote("A pack is one small text file. It holds one function that gives a colour for every pixel; Voltcap does everything else (the picture, the colours, the music levels, the speed limits).")

            TutorialPart(
                "Quick start",
                "1. Tap \"Add the examples\", then tap one in the list and \"Use it\".",
                "2. Pick \"Your theme\" or \"Your visualizer\" under Your own in the theme list (Behind the dock: Animated theme).",
                "3. Copy an example from below, change the numbers, save it as a .txt or .agsl file and bring it in with \"Import files\" (or put it in Internal storage › Voltcap › Custom packs and tap \"Look in Voltcap/Custom packs\").",
                "Importing a file with the same name replaces the old one. If something is wrong you get a message with the line number."
            )

            TutorialPart(
                "The file",
                "It starts with four comment lines. kind is theme (a background) or visualizer (reacts to the music). Then one function: " +
                    "voltScene for a theme, voltViz for a visualizer. Both take uv, asp and t and return a colour (float3)."
            )
            CodeBlock(
                "// voltpack: ${PackRules.HEADER_VERSION}\n// name: My pack\n// kind: theme\n// author: me\n\n" +
                    "float3 voltScene(float2 uv, float asp, float t) {\n    return uBg.rgb;\n}"
            )

            TutorialPart(
                "Where things are",
                "uv.x runs from 0 to asp (the screen's width divided by its height), uv.y from 0 at the top to 1 at the bottom. t is seconds. " +
                    "Colours are 0 to 1; going over 1 glows (it is clamped at the end). Write numbers with a dot: 1.0, not 1."
            )

            TutorialPart(
                "Colours you get (theme and visualizer)",
                "uBg background, uSurf surface, uAcc accent, uSec secondary (each a float4: use .rgb). They follow Theme look, so your pack recolours like the built-in themes.",
                "Visualizers also get the music: uBass, uMid, uHigh (0 to 1), uBeatAge (seconds since the last kick), uPlaying (0 to 1), uIntensity, and the spectrum with eqAt(0.0 to 1.0) (low notes first). " +
                    "uWaveY, room(0.11) and waveX(asp) say where the visualizer may draw so it stays clear of the player and the game row."
            )

            TutorialPart(
                "Helpers that are ready to use",
                "glow(d, r) soft falloff from a distance; dots(uv, scale, drift, density, size, seed) drifting specks; vnoise(p) and fbm(p) smooth noise (fbm is the expensive one); " +
                    "hash12(p) random per cell; rot(a) a rotation; sdSegment and sdRoundBox distances to a line and a box; sat(x) clamp 0 to 1; sqf(x) squared. " +
                    "Plus the usual: mix, smoothstep, step, clamp, sin, cos, pow, abs, fract, floor, length…",
                "Visualizers: paletteField(uv, asp, t, uBass, uBg.rgb, uAcc.rgb, uSec.rgb) gives a cover-coloured backdrop; vivid(colour, amount) makes a colour punchier; eqBand(0 to 7) is one band."
            )

            TutorialPart(
                "The rules (what keeps the app light)",
                "• At most ${PackRules.MAX_BYTES / 1000} KB and ${PackRules.MAX_LINES} lines; ${PackRules.MAX_PACKS} packs; names up to ${PackRules.MAX_NAME} letters.",
                "• Loops only like this: for (int i = 0; i < 12; i++) { ... } with a plain number, braces, at most ${PackRules.MAX_LOOP} turns each, ${PackRules.MAX_LOOPS} loops, and nested loops at most ${PackRules.MAX_NEST_PRODUCT} turns in all. No while, no do.",
                "• Everything has a cost; the total must stay under 100 % (the meter in the pack's page). Noise (fbm) inside a loop is the quickest way to run out.",
                "• No # lines, no uniforms of your own, no pictures, no main(), no discard, no function that calls itself.",
                "• It is drawn at most at ${(PackRules.MAX_SCALE_THEME * 100).toInt()} % (themes) or ${(PackRules.MAX_SCALE_VIZ * 100).toInt()} % (visualizers) of the screen's resolution and at most about 60 frames a second (never faster than the background quality allows).",
                "• It is test-built on your device before it is saved. And it is watched: if the home screen runs slowly with it, it is drawn even smaller and slower, and the second time Voltcap switches it off (you can turn it back on in its page)."
            )

            TutorialPart("Example theme: Tide lines", "Soft bands drifting across, in the theme's colours.")
            CodeBlock(CustomPackExamples.THEME)
            Spacer(Modifier.height(6.dp))
            ChipFlow { Chip("Copy the theme example", accent = false) { clipboard.setText(AnnotatedString(CustomPackExamples.THEME)) } }

            TutorialPart("Example visualizer: Soft bars", "24 bars that follow the spectrum.")
            CodeBlock(CustomPackExamples.VISUALIZER)
            Spacer(Modifier.height(6.dp))
            ChipFlow { Chip("Copy the visualizer example", accent = false) { clipboard.setText(AnnotatedString(CustomPackExamples.VISUALIZER)) } }

            TutorialPart(
                "When it says no",
                "\"Line 7: …\" is the line in your file. \"Too heavy\" means over the budget: use fewer loop turns or noise calls. " +
                    "\"Doesn't build\" is a typo or something the shader language can't do (use float3 for colours, dots in numbers, and finish every line with ;).",
                "Original work only: share packs you made, or have permission to use."
            )
            Spacer(Modifier.height(10.dp))
            Chip("Got it") { onDismiss() }
        }
    }
}
