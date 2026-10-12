package com.shelf.launcher.ui

import android.content.Context
import android.graphics.RuntimeShader
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.shelf.launcher.data.Features
import com.shelf.launcher.device.FrameWatch
import com.shelf.launcher.ui.theme.ThemeMotif
import java.io.File

/** One custom theme / visualizer that was added (a text file in Voltcap's own storage). */
internal class CustomPack(
    val id: String,
    val name: String,
    val author: String,
    val kind: PackKind,
    val text: String,
    val report: PackReport
) {
    /** The whole shader that is compiled for it (prelude + the pack + main). */
    val source: String get() = CustomPackRules.buildSource(kind, text)
}

internal val PackKind.motif: ThemeMotif
    get() = if (this == PackKind.THEME) ThemeMotif.CUSTOM_SCENE else ThemeMotif.CUSTOM_VIZ

internal fun ThemeMotif.packKind(): PackKind? = when (this) {
    ThemeMotif.CUSTOM_SCENE -> PackKind.THEME
    ThemeMotif.CUSTOM_VIZ -> PackKind.VISUALIZER
    else -> null
}

/**
 * Custom themes and visualizers: the files, the choice, the compile test and the safety net. The rules
 * themselves are in [CustomPackRules]; the picture is drawn by the normal scene code (ThemeScene /
 * NowPlayingScene), which reads [SceneSource] for a custom motif instead of the generated shaders.
 *
 * A pack only runs when all of these hold: it passes the rules, it compiles on this device (Android 13+),
 * it is the one chosen, and it hasn't been switched off by [PackGuard]. Otherwise the scene quietly falls
 * back to the Voltcap theme (themes) or Now playing (visualizers).
 */
internal object CustomPacks {
    val packs = mutableStateListOf<CustomPack>()

    /** Moves whenever the packs or the choice change, so a scene rebuilds its shader. */
    val revision = mutableIntStateOf(0)

    /** What the last import / scan said, one line per file (shown in the card). */
    var lastMessages by mutableStateOf<List<String>>(emptyList())

    private var loaded = false
    private val compiled = HashMap<String, String>() // text hash -> "" when it compiles, else the problem
    private val main = Handler(Looper.getMainLooper())

    class Outcome(val file: String, val ok: Boolean, val message: String)

    fun dir(context: Context): File = File(context.applicationContext.filesDir, "custom_packs").also { it.mkdirs() }

    /** Where people drop pack files (needs All files access to be read, like the settings sync folder). */
    fun inbox(): File = File(android.os.Environment.getExternalStorageDirectory(), "Voltcap/Custom packs")

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        reload(context)
    }

    fun reload(context: Context) {
        val files = dir(context).listFiles { f -> f.isFile && f.name.endsWith(".agsl") }.orEmpty().sortedBy { it.name }
        val list = files.take(PackRules.MAX_PACKS * 2).mapNotNull { f ->
            runCatching { if (f.length() > PackRules.READ_CAP) null else make(f.nameWithoutExtension, f.readText()) }.getOrNull()
        }
        packs.clear(); packs.addAll(list)
        revision.intValue++
    }

    private fun make(id: String, text: String): CustomPack {
        val head = CustomPackRules.parseHeader(text)
        return CustomPack(id, head.name.ifBlank { id }, head.author, head.kind ?: PackKind.THEME, text, CustomPackRules.validate(text))
    }

    // --- the choice ------------------------------------------------------------------------------

    fun selectedId(kind: PackKind): String =
        if (kind == PackKind.THEME) Features.customScenePack.state.value else Features.customVizPack.state.value

    fun select(kind: PackKind, id: String) {
        if (kind == PackKind.THEME) Features.customScenePack.value = id else Features.customVizPack.value = id
        Features.customPackOff.value = Features.customPackOff.value - id
        revision.intValue++
    }

    fun selected(kind: PackKind): CustomPack? = selectedId(kind).let { id -> packs.firstOrNull { it.id == id && it.kind == kind } }

    fun isOff(id: String) = id in Features.customPackOff.state.value

    /** The pack to draw for this motif, or null (fall back): it passes the rules, compiles here and isn't switched off. */
    fun active(motif: ThemeMotif): CustomPack? {
        val kind = motif.packKind() ?: return null
        if (Build.VERSION.SDK_INT < 33) return null
        val pack = selected(kind) ?: return null
        if (!pack.report.ok || isOff(pack.id)) return null
        return if (compileProblem(pack).isNullOrEmpty()) pack else null
    }

    fun summary(): String {
        val t = selected(PackKind.THEME)?.name
        val v = selected(PackKind.VISUALIZER)?.name
        return when {
            packs.isEmpty() -> "None yet"
            t == null && v == null -> "${packs.size} added"
            else -> listOfNotNull(t?.let { "Theme: $it" }, v?.let { "Visualizer: $it" }).joinToString(" · ")
        }
    }

    // --- adding, removing ---------------------------------------------------------------------------

    /** Checks, test-compiles and saves one pack. Call it off the main thread (it compiles a shader). */
    fun importText(context: Context, fileName: String, text: String): Outcome {
        if (text.length > PackRules.READ_CAP) return Outcome(fileName, false, "Too big (limit ${PackRules.MAX_BYTES / 1000} KB).")
        val report = CustomPackRules.validate(text)
        if (!report.ok) return Outcome(fileName, false, report.problems.joinToString(" "))
        val head = CustomPackRules.parseHeader(text)
        val kind = head.kind ?: return Outcome(fileName, false, "No kind line.")
        val id = CustomPackRules.slug(head.name) + if (kind == PackKind.THEME) "-theme" else "-viz"
        val replaces = packs.any { it.id == id }
        if (!replaces && packs.size >= PackRules.MAX_PACKS) {
            return Outcome(fileName, false, "You already have ${PackRules.MAX_PACKS} packs: delete one first.")
        }
        val pack = make(id, text)
        val problem = compileProblem(pack)
        if (!problem.isNullOrEmpty()) return Outcome(fileName, false, problem)
        val ok = runCatching {
            val dir = dir(context)
            val tmp = File(dir, "$id.tmp")
            tmp.writeText(text)
            val dest = File(dir, "$id.agsl")
            if (!tmp.renameTo(dest)) { dest.delete(); tmp.renameTo(dest) }
        }.isSuccess
        if (!ok) return Outcome(fileName, false, "Couldn't save it (no space?).")
        main.post {
            val i = packs.indexOfFirst { it.id == id }
            if (i >= 0) packs[i] = pack else packs.add(pack)
            Features.customPackOff.value = Features.customPackOff.value - id
            revision.intValue++
        }
        return Outcome(fileName, true, "Added “${pack.name}” (${kind.label.lowercase()}, ${report.costPercent} % of the budget).")
    }

    fun delete(context: Context, pack: CustomPack) {
        runCatching { File(dir(context), "${pack.id}.agsl").delete() }
        packs.removeAll { it.id == pack.id }
        if (selectedId(pack.kind) == pack.id) {
            if (pack.kind == PackKind.THEME) Features.customScenePack.value = "" else Features.customVizPack.value = ""
        }
        Features.customPackOff.value = Features.customPackOff.value - pack.id
        revision.intValue++
    }

    fun switchOff(id: String, why: String) {
        if (id.isEmpty() || isOff(id)) return
        Features.customPackOff.value = Features.customPackOff.value + id
        val name = packs.firstOrNull { it.id == id }?.name ?: id
        lastMessages = listOf("“$name” was switched off: $why")
        revision.intValue++
    }

    fun switchOn(id: String) {
        Features.customPackOff.value = Features.customPackOff.value - id
        revision.intValue++
    }

    /** Reads every pack file in Voltcap/Custom packs (Internal storage). */
    fun scanInbox(context: Context): List<Outcome> {
        val dir = inbox()
        val files = runCatching { dir.listFiles { f -> f.isFile && f.name.substringAfterLast('.').lowercase() in setOf("agsl", "txt", "voltpack") } }.getOrNull()
            ?: return listOf(Outcome("Voltcap/Custom packs", false, "Voltcap can't read that folder. It needs All files access (Settings › Backup), or use Import files."))
        if (files.isEmpty()) return listOf(Outcome("Voltcap/Custom packs", false, "No .agsl or .txt files in that folder yet."))
        return files.sortedBy { it.name }.take(PackRules.MAX_PACKS).map { f ->
            if (f.length() > PackRules.READ_CAP) Outcome(f.name, false, "Too big (limit ${PackRules.MAX_BYTES / 1000} KB).")
            else runCatching { importText(context, f.name, f.readText()) }.getOrElse { Outcome(f.name, false, "Couldn't read it.") }
        }
    }

    fun addExamples(context: Context): List<Outcome> = listOf(
        importText(context, "Tide lines", CustomPackExamples.THEME),
        importText(context, "Soft bars", CustomPackExamples.VISUALIZER)
    )

    // --- compile test ---------------------------------------------------------------------------------

    /** "" / null when the pack compiles on this device, else a short reason (error lines are the pack's own line numbers). */
    @Synchronized
    fun compileProblem(pack: CustomPack): String? {
        if (Build.VERSION.SDK_INT < 33) return "Custom packs need Android 13 or newer."
        val key = pack.kind.name + ":" + pack.text.length + ":" + pack.text.hashCode()
        return compiled.getOrPut(key) { tryCompile(pack) }.ifEmpty { null }
    }

    @androidx.annotation.RequiresApi(33)
    private fun compileNow(pack: CustomPack): String = try {
        RuntimeShader(pack.source)
        ""
    } catch (e: Throwable) {
        describe(e.message.orEmpty(), pack.kind, pack.text.count { it == '\n' } + 1)
    }

    private fun tryCompile(pack: CustomPack): String =
        if (Build.VERSION.SDK_INT >= 33) compileNow(pack) else "Custom packs need Android 13 or newer."

    private fun describe(raw: String, kind: PackKind, userLines: Int): String {
        val m = Regex("(?i)error:?\\s*(\\d+):?\\s*([^\\n]+)").find(raw)
        val msg = (m?.groupValues?.get(2) ?: raw.lineSequence().firstOrNull { it.isNotBlank() } ?: "It didn't compile.").trim().take(140)
        val line = m?.groupValues?.get(1)?.toIntOrNull()?.minus(CustomPackRules.linesBefore(kind))
        return if (line != null && line in 1..userLines) "Line $line: $msg" else msg
    }
}

/** What the scene code reads for a motif: the generated shaders, or the chosen custom pack's. */
internal object SceneSource {
    fun source(motif: ThemeMotif): String =
        if (motif.custom) CustomPacks.active(motif)?.source.orEmpty() else ThemeShaders.source(motif)

    fun uniforms(motif: ThemeMotif): Set<String> =
        if (motif.custom) motif.packKind()?.let { CustomPackRules.uniformsOf(it) }.orEmpty() else ThemeShaders.uniforms(motif)

    fun children(motif: ThemeMotif): Set<String> =
        if (motif.custom) emptySet() else ThemeShaders.children(motif)
}

/**
 * The motif that is really drawn: a custom one only while a valid, compiled, switched-on pack is chosen
 * (and Android 13+); otherwise the Voltcap theme (themes) or Now playing (visualizers).
 */
@Composable
internal fun rememberEffectiveMotif(requested: ThemeMotif): ThemeMotif {
    val fallback = if (requested == ThemeMotif.CUSTOM_VIZ) ThemeMotif.MUSIC else ThemeMotif.VOLT_INTRO
    if (!requested.custom) return requested
    val context = LocalContext.current
    val rev = CustomPacks.revision.intValue // recompose when packs / the choice change
    // Reading the pack files and test-compiling the shader run off the main thread; the fallback shows
    // meanwhile (update 91). The result is cached, so later checks are instant.
    val ok by produceState(false, requested, rev) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                CustomPacks.ensureLoaded(context)
                CustomPacks.active(requested) != null
            }.getOrDefault(false)
        }
    }
    return if (ok) requested else fallback
}

/**
 * The safety net while a pack is on screen: Voltcap already watches the frames it really draws
 * ([FrameWatch]). If it has to lower the background quality after a pack started, the pack draws even
 * smaller and slower ([heavy]); if it has to do it again, the pack is switched off and the card says so.
 */
internal object PackGuard {
    var heavy by mutableStateOf(false)
        private set
    private var baseline = 0

    @Composable
    fun Watch(motif: ThemeMotif) {
        val kind = motif.packKind() ?: return
        val id = CustomPacks.selectedId(kind)
        val rev = CustomPacks.revision.intValue
        val steps = FrameWatch.steps.intValue
        LaunchedEffect(id, rev) { baseline = FrameWatch.steps.intValue; heavy = false }
        LaunchedEffect(steps) {
            val extra = steps - baseline
            if (extra >= 1) heavy = true
            if (extra >= 2) CustomPacks.switchOff(id, "the home screen was running slowly with it. Open the pack to try it again, or make it lighter.")
        }
    }

    /** Resolution share a pack may use: the app's own quality, capped, and lower still once it proved heavy. */
    fun scale(quality: Float, kind: PackKind): Float {
        val cap = if (kind == PackKind.THEME) PackRules.MAX_SCALE_THEME else PackRules.MAX_SCALE_VIZ
        return if (heavy) minOf(quality, PackRules.HEAVY_SCALE).coerceAtLeast(0.3f) else minOf(quality.coerceAtLeast(0.45f), cap)
    }

    /** The shortest frame interval a pack may use (about 60 fps, 30 once it proved heavy; update 126). */
    fun frameMs(): Int = if (heavy) PackRules.HEAVY_FRAME_MS else PackRules.MIN_FRAME_MS
}
