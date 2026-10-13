package com.shelf.launcher.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/*
 * Update 176: custom layouts — home screens anyone can make and share. A layout is a ".vclayout" file (a zip) holding a
 * layout.json and the pictures it uses: where each part sits (the moving games, the game's art, its name, text, the
 * clock, pictures, shapes, Play and Details), how they look and how they move (a loop that keeps going, and an
 * entrance played when you move to another game). Animated GIF / WebP pictures play. The format is in
 * docs\CUSTOM_LAYOUTS.md and in Settings › Home › Layout options while Custom layout is picked.
 * Imported layouts are kept in the app's own files (files/layouts/<id>/); nothing in them runs as code.
 */

/** One custom layout, read from its layout.json. */
data class CustomScene(
    val id: String,
    val name: String,
    val author: String?,
    val background: SceneBackground,
    val parts: List<ScenePart>,
    /** Where its pictures are (null: the built-in example, which has none). */
    val dir: File?
) {
    val games: ScenePart? get() = parts.firstOrNull { it.type == "games" }
    val hasButtons: Boolean get() = parts.any { it.type == "buttons" }
    /** Its picture file [name] (only files inside its own folder). */
    fun file(name: String?): File? {
        if (name.isNullOrBlank() || dir == null) return null
        val f = File(dir, File(name).name)
        return f.takeIf { it.isFile }
    }
}

data class SceneBackground(
    val color: String? = null,
    val gradient: List<String> = emptyList(),
    val image: String? = null,
    val fit: String = "crop",
    /** The game's own backdrop behind everything (under [image] and [shade]). */
    val gameArt: Boolean = false,
    /** Black over the game's art, 0–1. */
    val shade: Float = 0.4f,
    val loop: SceneLoop? = null
)

/** A motion that keeps going: float, sway, spin, rock, pulse, blink or drift. */
data class SceneLoop(val kind: String, val amount: Float?, val seconds: Float, val delay: Float = 0f)

/** A motion played when you move to another game ("move") or once when the layout appears ("start"). */
data class SceneEnter(val kind: String, val ms: Int, val delay: Int, val on: String)

/** One part of a layout. Positions and sizes are shares (0–1) of the layout's area. */
data class ScenePart(
    val type: String,
    val x: Float, val y: Float, val w: Float, val h: Float,
    val alpha: Float = 1f,
    val rotate: Float = 0f,
    val loop: SceneLoop? = null,
    val enter: SceneEnter? = null,
    // Pictures (art / image / games).
    val art: String = "cover",
    val fit: String = "crop",
    val corner: Float = 12f,
    val src: String? = null,
    // Text (title / info / text / clock / category).
    val text: String? = null,
    val size: Float = 0.06f,
    val color: String? = null,
    val align: String = "start",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val shadow: Boolean = true,
    val font: String = "sans",
    val maxLines: Int = 2,
    val show: List<String> = emptyList(),
    // Shapes.
    val shape: String = "round",
    val gradient: List<String> = emptyList(),
    val border: String? = null,
    val borderWidth: Float = 0f,
    // The moving games.
    val direction: String = "row",
    val cardSize: Float = 0.7f,
    val focusSize: Float = 1f,
    val gap: Float = 0.02f,
    val anchor: Float = 0.5f,
    val curve: Float = 0.15f,
    val names: Boolean = false,
    val glow: Boolean = true,
    val fadeEdges: Boolean = false,
    // Buttons.
    val textColor: String? = null,
    val labels: List<String> = emptyList()
)

object CustomLayouts {
    /** The built-in example: always there, and saved as a starting point by "Save the example". */
    const val EXAMPLE_ID = "example"

    const val FORMAT = 1
    private const val MAX_FILES = 80
    private const val MAX_BYTES = 48L * 1024 * 1024
    private val PICTURES = setOf("png", "jpg", "jpeg", "webp", "gif")

    /** The folder a layout can be dropped into on the handheld (Settings › … › Import from the folder). */
    const val DROP_FOLDER = "Voltcap/Layouts"

    private val cache = HashMap<String, CustomScene>()

    private fun root(context: Context) = File(context.filesDir, "layouts")

    /** Every layout there is: the example first, then the imported ones by name. */
    fun list(context: Context): List<CustomScene> {
        val mine = root(context).listFiles()?.filter { it.isDirectory }?.mapNotNull { d -> runCatching { load(context, d.name) }.getOrNull() }
            ?.sortedBy { it.name.lowercase() }.orEmpty()
        val presets = PRESETS.keys.mapNotNull { id -> runCatching { load(context, id) }.getOrNull() }
        return listOf(example()) + presets + mine
    }

    /** The layout [id] (null / unknown: the example). */
    fun load(context: Context, id: String?): CustomScene {
        if (id.isNullOrBlank() || id == EXAMPLE_ID) return example()
        synchronized(cache) { cache[id]?.let { return it } }
        PRESETS[id]?.let { text ->
            val scene = parse(id, text, null)
            synchronized(cache) { cache[id] = scene }
            return scene
        }
        val dir = File(root(context), id)
        val json = File(dir, "layout.json")
        if (!json.isFile) return example()
        val scene = parse(id, json.readText(), dir)
        synchronized(cache) { cache[id] = scene }
        return scene
    }

    fun example(): CustomScene = synchronized(cache) {
        cache.getOrPut(EXAMPLE_ID) { parse(EXAMPLE_ID, EXAMPLE_JSON, null) }
    }

    /** Built in (the example and the presets): can't be deleted. */
    fun builtIn(id: String): Boolean = id == EXAMPLE_ID || id in PRESETS

    fun delete(context: Context, id: String) {
        if (builtIn(id)) return
        File(root(context), File(id).name).deleteRecursively()
        synchronized(cache) { cache.remove(id) }
    }

    /** What an import did: the layout's id, or a message saying what's wrong. */
    class Result(val id: String?, val message: String)

    /** Imports a .vclayout (zip) or a bare layout.json from [uri]. */
    fun import(context: Context, uri: Uri): Result = try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytesCapped(MAX_BYTES) }
        if (bytes == null) Result(null, "Couldn't open that file.")
        else importBytes(context, bytes, uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Layout")
    } catch (e: Exception) {
        Result(null, "Couldn't import it: ${e.message ?: e::class.java.simpleName}")
    }

    /** Imports every layout in the [DROP_FOLDER] folder (a .vclayout, a folder with a layout.json, or a .json file). */
    fun importFolder(context: Context): Result {
        val base = File(android.os.Environment.getExternalStorageDirectory(), DROP_FOLDER)
        if (!base.isDirectory) {
            return if (AppStorage.canUseRootFolder() && base.mkdirs()) Result(null, "Made the $DROP_FOLDER folder on the handheld's storage — put your layouts in it and tap this again.")
            else Result(null, "Make a folder named $DROP_FOLDER on the handheld's storage and put your layouts in it (Voltcap needs All files access to read it).")
        }
        val found = base.listFiles().orEmpty().sortedBy { it.name.lowercase() }
        if (found.isEmpty()) return Result(null, "The $DROP_FOLDER folder is empty.")
        var ok = 0
        var lastId: String? = null
        val problems = ArrayList<String>()
        for (f in found) {
            val r = try {
                when {
                    f.isDirectory && File(f, "layout.json").isFile -> importDir(context, f)
                    f.isFile && (f.extension.equals("vclayout", true) || f.extension.equals("zip", true) || f.extension.equals("json", true)) ->
                        importBytes(context, f.inputStream().use { it.readBytesCapped(MAX_BYTES) }, f.nameWithoutExtension)
                    else -> null
                }
            } catch (e: Exception) { Result(null, e.message ?: "unreadable") }
            if (r == null) continue
            if (r.id != null) { ok++; lastId = r.id } else problems += "${f.name}: ${r.message}"
        }
        val msg = buildString {
            append(if (ok == 1) "1 layout imported." else "$ok layouts imported.")
            if (problems.isNotEmpty()) append(" Not imported — ").append(problems.take(3).joinToString("; "))
        }
        return Result(lastId, msg)
    }

    private fun importDir(context: Context, dir: File): Result {
        val files = dir.listFiles().orEmpty().filter { it.isFile }
        val map = LinkedHashMap<String, ByteArray>()
        var total = 0L
        for (f in files.take(MAX_FILES)) {
            val ext = f.extension.lowercase()
            if (f.name != "layout.json" && ext !in PICTURES) continue
            total += f.length()
            if (total > MAX_BYTES) return Result(null, "Too big (the limit is ${MAX_BYTES / 1024 / 1024} MB).")
            map[f.name] = f.readBytes()
        }
        return save(context, map, dir.name)
    }

    private class Refused(message: String) : Exception(message)

    private fun importBytes(context: Context, bytes: ByteArray, fallbackName: String): Result {
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (!isZip) return save(context, mapOf("layout.json" to bytes), fallbackName)
        val map = try { unzip(bytes) } catch (e: Refused) { return Result(null, e.message ?: "Not imported.") }
        return save(context, map, fallbackName)
    }

    /** The layout.json and pictures in a zip. Only each file's own name is kept: a layout's files sit side by side
     *  (folders inside the zip are flattened), so nothing can be written outside its folder. */
    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        var total = 0L
        val zip = ZipInputStream(bytes.inputStream())
        try {
            var e: ZipEntry? = zip.nextEntry
            while (e != null) {
                val name = e.name.substringAfterLast('/').substringAfterLast('\\')
                val ext = name.substringAfterLast('.', "").lowercase()
                if (!e.isDirectory && (name == "layout.json" || ext in PICTURES)) {
                    if (map.size >= MAX_FILES) throw Refused("Too many files (the limit is $MAX_FILES).")
                    val data = zip.readBytesCapped(MAX_BYTES - total)
                    total += data.size
                    map[name] = data
                }
                e = zip.nextEntry
            }
        } catch (x: IllegalStateException) {
            throw Refused("Too big (the limit is ${MAX_BYTES / 1024 / 1024} MB).")
        } finally {
            zip.close()
        }
        return map
    }

    private fun save(context: Context, files: Map<String, ByteArray>, fallbackName: String): Result {
        val json = files["layout.json"] ?: return Result(null, "There's no layout.json in it.")
        val text = String(json, Charsets.UTF_8)
        // Checked before anything is saved: a broken file says where it's broken.
        val probe = try { parse("probe", text, null) } catch (e: Exception) { return Result(null, "layout.json: ${e.message}") }
        val id = slug(probe.name.takeIf { it != "Untitled layout" } ?: fallbackName)
        val dir = File(root(context), id)
        dir.deleteRecursively()
        dir.mkdirs()
        files.forEach { (name, data) -> File(dir, File(name).name).writeBytes(data) }
        synchronized(cache) { cache.remove(id) }
        val missing = probe.parts.mapNotNull { it.src } .plus(listOfNotNull(probe.background.image)).filter { it !in files.keys }
        val note = if (missing.isEmpty()) "" else " Missing pictures: ${missing.distinct().take(4).joinToString(", ")}."
        return Result(id, "“${probe.name}” imported.$note")
    }

    /** Writes the example layout as a .vclayout (zip) to [out]. */
    fun writeExample(out: java.io.OutputStream) = writeLayout(null, EXAMPLE_ID, out)

    /** Update 178: writes layout [id] (its layout.json and pictures) as a .vclayout (zip) to [out], to change or share. */
    fun writeLayout(context: Context?, id: String, out: java.io.OutputStream) {
        val text = when {
            id == EXAMPLE_ID -> EXAMPLE_JSON
            PRESETS.containsKey(id) -> PRESETS.getValue(id)
            context != null -> File(File(root(context), id), "layout.json").readText()
            else -> EXAMPLE_JSON
        }
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("layout.json"))
            z.write(text.toByteArray(Charsets.UTF_8))
            z.closeEntry()
            if (context != null && !builtIn(id)) {
                File(root(context), id).listFiles()?.filter { it.isFile && it.name != "layout.json" }?.forEach { f ->
                    z.putNextEntry(ZipEntry(f.name))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
    }

    private fun slug(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifBlank { "layout" }

    private fun java.io.InputStream.readBytesCapped(max: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw IllegalStateException("too big (the limit is ${MAX_BYTES / 1024 / 1024} MB)")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    // ---- Reading layout.json ----

    /** Reads a layout.json; throws with a readable message when something is wrong. */
    fun parse(id: String, text: String, dir: File?): CustomScene {
        val o = try { JSONObject(text) } catch (e: org.json.JSONException) {
            throw IllegalArgumentException("not valid JSON (${e.message})")
        }
        val format = o.optInt("format", FORMAT)
        if (format > FORMAT) throw IllegalArgumentException("made for a newer Voltcap (format $format) — update Voltcap")
        val partsJson = o.optJSONArray("parts") ?: throw IllegalArgumentException("it has no \"parts\" list")
        val parts = ArrayList<ScenePart>()
        for (i in 0 until partsJson.length()) {
            val p = partsJson.optJSONObject(i) ?: throw IllegalArgumentException("part ${i + 1} isn't an object { … }")
            parts += part(p, i)
        }
        if (parts.count { it.type == "games" } > 1) throw IllegalArgumentException("only one \"games\" part is allowed")
        val bg = o.optJSONObject("background")?.let { b ->
            SceneBackground(
                color = b.str("color"),
                gradient = b.strings("gradient"),
                image = b.str("image"),
                fit = b.optString("fit", "crop"),
                gameArt = b.optBoolean("gameArt", false),
                shade = b.f("shade", 0.4f).coerceIn(0f, 1f),
                loop = b.optJSONObject("loop")?.let { loop(it) }
            )
        } ?: SceneBackground(gameArt = true)
        return CustomScene(id, o.optString("name").ifBlank { "Untitled layout" }, o.str("author"), bg, parts, dir)
    }

    private val TYPES = setOf("games", "art", "title", "info", "text", "clock", "category", "image", "shape", "buttons")

    private fun part(p: JSONObject, i: Int): ScenePart {
        val type = p.optString("type").lowercase()
        if (type !in TYPES) throw IllegalArgumentException("part ${i + 1}: unknown type \"$type\" (one of ${TYPES.joinToString(", ")})")
        val x = p.f("x", 0f); val y = p.f("y", 0f)
        val w = p.f("w", 1f - x); val h = p.f("h", 0.1f)
        if (w <= 0f || h <= 0f) throw IllegalArgumentException("part ${i + 1} ($type): \"w\" and \"h\" must be above 0")
        return ScenePart(
            type = type, x = x, y = y, w = w, h = h,
            alpha = p.f("alpha", 1f).coerceIn(0f, 1f),
            rotate = p.f("rotate", 0f),
            loop = p.optJSONObject("loop")?.let { loop(it) },
            enter = p.optJSONObject("enter")?.let { e ->
                SceneEnter(
                    e.optString("kind", "fade").lowercase(),
                    e.optInt("ms", 350).coerceIn(0, 5000),
                    e.optInt("delay", 0).coerceIn(0, 5000),
                    e.optString("on", if (type in setOf("games", "image", "shape", "clock", "buttons")) "start" else "move").lowercase()
                )
            },
            art = p.optString("art", if (type == "art") "backdrop" else "cover").lowercase(),
            fit = p.optString("fit", if (type == "art" && p.optString("art") == "logo") "fit" else "crop").lowercase(),
            corner = p.f("corner", if (type == "shape") 16f else 12f),
            src = p.str("src"),
            text = p.str("text"),
            size = p.f("size", if (type == "title") 0.07f else 0.045f).coerceIn(0.005f, 0.5f),
            color = p.str("color"),
            align = p.optString("align", "start").lowercase(),
            bold = p.optBoolean("bold", type == "title"),
            italic = p.optBoolean("italic", false),
            shadow = p.optBoolean("shadow", true),
            font = p.optString("font", "sans").lowercase(),
            maxLines = p.optInt("maxLines", if (type == "title") 2 else 3).coerceIn(1, 20),
            show = p.strings("show").ifEmpty { listOf("console", "playtime", "achievements") },
            shape = p.optString("shape", "round").lowercase(),
            gradient = p.strings("gradient"),
            border = p.str("border"),
            borderWidth = p.f("borderWidth", 0f).coerceIn(0f, 40f),
            direction = p.optString("direction", "row").lowercase(),
            cardSize = p.f("cardSize", 0.7f).coerceIn(0.1f, 1f),
            focusSize = p.f("focusSize", 1f).coerceIn(0.1f, 1.5f),
            gap = p.f("gap", 0.02f).coerceIn(0f, 0.5f),
            anchor = p.f("anchor", 0.5f).coerceIn(0f, 1f),
            curve = p.f("curve", 0.15f).coerceIn(-1f, 1f),
            names = p.optBoolean("names", false),
            glow = p.optBoolean("glow", true),
            fadeEdges = p.optBoolean("fadeEdges", false),
            textColor = p.str("textColor"),
            labels = p.strings("labels")
        )
    }

    private fun loop(o: JSONObject) = SceneLoop(
        o.optString("kind", "float").lowercase(),
        if (o.has("amount")) o.f("amount", 0f) else null,
        o.f("seconds", 4f).coerceIn(0.2f, 120f),
        o.f("delay", 0f).coerceIn(0f, 1f)
    )

    private fun JSONObject.f(key: String, def: Float): Float = if (has(key)) optDouble(key, def.toDouble()).toFloat() else def
    private fun JSONObject.str(key: String): String? = if (has(key) && !isNull(key)) optString(key).ifBlank { null } else null
    private fun JSONObject.strings(key: String): List<String> {
        val a: JSONArray = optJSONArray(key) ?: return str(key)?.let { listOf(it) }.orEmpty()
        return (0 until a.length()).mapNotNull { a.optString(it).ifBlank { null } }
    }


    /**
     * Update 178: the other built-in layouts (the same presets as the Voltcap Layout Maker page): always there, like the
     * example, and a starting point to change.
     */
    val PRESETS: Map<String, String> = linkedMapOf(
        "preset-side-list" to """{"format":1,"name":"Side list","author":"Voltcap","background":{"color":"#101318","gameArt":true,"shade":0.35},"parts":[{"type":"shape","x":0,"y":0,"w":0.34,"h":1,"shape":"rect","gradient":["#E6101318","#B3101318"]},{"type":"category","x":0.03,"y":0.03,"w":0.28,"h":0.06,"size":0.04,"color":"accent","bold":true},{"type":"games","x":0.03,"y":0.1,"w":0.28,"h":0.88,"direction":"column","art":"wide","cardSize":0.82,"focusSize":0.98,"gap":0.02,"anchor":0.35,"fadeEdges":true},{"type":"art","x":0.42,"y":0.08,"w":0.5,"h":0.28,"art":"logo","fit":"fit","enter":{"kind":"pop","ms":420},"loop":{"kind":"float","amount":0.008,"seconds":6}},{"type":"info","x":0.42,"y":0.4,"w":0.5,"h":0.06,"size":0.035,"align":"center","show":["console","playtime","lastPlayed"],"enter":{"kind":"fade","ms":400,"delay":150}},{"type":"buttons","x":0.5,"y":0.5,"w":0.34,"h":0.09},{"type":"text","x":0.42,"y":0.92,"w":0.54,"h":0.05,"size":0.03,"align":"end","color":"#99FFFFFF","text":"{position}   {time}"}]}""",
        "preset-big-picture" to """{"format":1,"name":"Big picture","author":"Voltcap","background":{"color":"#000000","gameArt":true,"shade":0.2},"parts":[{"type":"shape","x":0,"y":0.5,"w":1,"h":0.5,"shape":"rect","gradient":["#00000000","#E6000000"]},{"type":"art","x":0.05,"y":0.12,"w":0.2,"h":0.5,"art":"cover","corner":10,"enter":{"kind":"slide-up","ms":420},"loop":{"kind":"float","amount":0.008,"seconds":6}},{"type":"title","x":0.28,"y":0.36,"w":0.6,"h":0.16,"size":0.08,"enter":{"kind":"fade","ms":400,"delay":80}},{"type":"info","x":0.28,"y":0.52,"w":0.6,"h":0.06,"size":0.034,"color":"#CCFFFFFF","show":["console","playtime","achievements"]},{"type":"games","x":0.02,"y":0.72,"w":0.96,"h":0.24,"direction":"row","art":"wide","cardSize":0.62,"focusSize":0.8,"gap":0.012,"anchor":0.15,"fadeEdges":true},{"type":"clock","x":0.78,"y":0.04,"w":0.18,"h":0.07,"size":0.045,"align":"end"}]}""",
        "preset-marquee" to """{"format":1,"name":"Marquee","author":"Voltcap","background":{"gradient":["#14051F","#030208"]},"parts":[{"type":"shape","x":0.1,"y":0.03,"w":0.8,"h":0.16,"shape":"round","corner":12,"gradient":["#FF2E6B","#B8135A"],"border":"#FFD0E1","borderWidth":2,"loop":{"kind":"blink","amount":0.75,"seconds":3}},{"type":"title","x":0.12,"y":0.055,"w":0.76,"h":0.12,"size":0.07,"align":"center","font":"mono","maxLines":1,"enter":{"kind":"flip","ms":420}},{"type":"shape","x":0.27,"y":0.22,"w":0.46,"h":0.44,"shape":"round","corner":14,"color":"#000000","border":"accent","borderWidth":3},{"type":"art","x":0.285,"y":0.235,"w":0.43,"h":0.41,"art":"screenshot","corner":8,"enter":{"kind":"zoom","ms":380}},{"type":"info","x":0.27,"y":0.67,"w":0.46,"h":0.05,"size":0.03,"align":"center","font":"mono","color":"#B3FFFFFF","show":["console","position"]},{"type":"games","x":0,"y":0.74,"w":1,"h":0.25,"direction":"row","art":"square","cardSize":0.7,"focusSize":0.92,"gap":0.02,"anchor":0.5,"glow":true}]}""",
        "preset-quiet-clock" to """{"format":1,"name":"Quiet clock","author":"Voltcap","background":{"color":"#0E0F12","gameArt":true,"shade":0.75},"parts":[{"type":"clock","x":0.05,"y":0.06,"w":0.6,"h":0.22,"size":0.17,"bold":true,"shadow":false,"loop":{"kind":"pulse","amount":0.01,"seconds":8}},{"type":"text","x":0.05,"y":0.29,"w":0.6,"h":0.05,"size":0.032,"color":"#99FFFFFF","text":"{date}"},{"type":"title","x":0.05,"y":0.48,"w":0.9,"h":0.1,"size":0.05,"maxLines":1,"enter":{"kind":"slide-up","ms":320}},{"type":"games","x":0.05,"y":0.6,"w":0.9,"h":0.32,"direction":"row","art":"card","cardSize":0.75,"focusSize":0.9,"gap":0.025,"anchor":0,"names":false,"glow":false}]}""",
        "preset-card-fan" to """{"format":1,"name":"Card fan","author":"Voltcap","background":{"gradient":["#0D2A22","#06110E"],"gameArt":true,"shade":0.65},"parts":[{"type":"category","x":0.05,"y":0.04,"w":0.9,"h":0.06,"size":0.04,"align":"center","color":"accent","bold":true},{"type":"games","x":0,"y":0.1,"w":1,"h":0.62,"direction":"arc","art":"card","cardSize":0.6,"focusSize":0.92,"gap":-0.0,"anchor":0.5,"curve":0.3,"glow":true},{"type":"title","x":0.15,"y":0.74,"w":0.7,"h":0.1,"size":0.06,"align":"center","maxLines":1,"enter":{"kind":"pop","ms":360}},{"type":"info","x":0.15,"y":0.85,"w":0.7,"h":0.06,"size":0.032,"align":"center","color":"#CCFFFFFF","show":["playtime","achievements"]}]}""",
        "preset-stage-lights" to """{"format":1,"name":"Stage lights","author":"Voltcap","background":{"gradient":["#1A1033","#05030A"]},"parts":[{"type":"shape","x":0.25,"y":-0.2,"w":0.5,"h":1.1,"shape":"circle","gradient":["#33FFFFFF","#00FFFFFF"],"loop":{"kind":"sway","amount":0.06,"seconds":9}},{"type":"shape","x":0,"y":0.78,"w":1,"h":0.22,"shape":"rect","gradient":["#402A1B4D","#E6000000"]},{"type":"games","x":0,"y":0.3,"w":1,"h":0.5,"direction":"row","art":"card","cardSize":0.6,"focusSize":0.95,"gap":0.02,"anchor":0.5,"glow":true,"names":true},{"type":"title","x":0.1,"y":0.05,"w":0.8,"h":0.12,"size":0.07,"align":"center","maxLines":1,"enter":{"kind":"slide-down","ms":360}},{"type":"info","x":0.1,"y":0.17,"w":0.8,"h":0.06,"size":0.032,"align":"center","color":"#CCFFFFFF","show":["console","playtime"]},{"type":"buttons","x":0.36,"y":0.86,"w":0.28,"h":0.08}]}"""
    )

    /** The built-in example: a neon row over the game's art (docs\CUSTOM_LAYOUTS.md walks through it). */
    val EXAMPLE_JSON = """
{
  "format": 1,
  "name": "Neon drift (example)",
  "author": "Voltcap",
  "background": {
    "gradient": ["#0B0F1A", "#26123F"],
    "gameArt": true,
    "shade": 0.55
  },
  "parts": [
    { "type": "shape", "x": 0.0, "y": 0.6, "w": 1.0, "h": 0.4, "shape": "rect",
      "gradient": ["#00000000", "#CC05060C"] },
    { "type": "shape", "x": 0.04, "y": 0.06, "w": 0.006, "h": 0.3, "shape": "rect", "color": "accent",
      "loop": { "kind": "blink", "seconds": 2.4 } },
    { "type": "category", "x": 0.06, "y": 0.06, "w": 0.5, "h": 0.06, "size": 0.04, "color": "accent", "bold": true },
    { "type": "title", "x": 0.06, "y": 0.12, "w": 0.55, "h": 0.2, "size": 0.085,
      "enter": { "kind": "slide-right", "ms": 380 } },
    { "type": "info", "x": 0.06, "y": 0.32, "w": 0.55, "h": 0.06, "size": 0.035, "color": "#CCFFFFFF",
      "show": ["console", "playtime", "achievements"],
      "enter": { "kind": "fade", "ms": 420, "delay": 120 } },
    { "type": "art", "x": 0.64, "y": 0.05, "w": 0.32, "h": 0.32, "art": "logo", "fit": "fit",
      "enter": { "kind": "zoom", "ms": 450 },
      "loop": { "kind": "float", "amount": 0.012, "seconds": 5 } },
    { "type": "buttons", "x": 0.06, "y": 0.41, "w": 0.4, "h": 0.09, "labels": ["Play", "Details"],
      "color": "accent", "textColor": "#FFFFFF" },
    { "type": "games", "x": 0.0, "y": 0.56, "w": 1.0, "h": 0.4, "direction": "arc", "art": "card",
      "cardSize": 0.62, "focusSize": 0.95, "gap": 0.018, "anchor": 0.22, "curve": 0.12,
      "glow": true, "fadeEdges": true },
    { "type": "clock", "x": 0.75, "y": 0.42, "w": 0.21, "h": 0.07, "size": 0.05, "align": "end", "bold": true }
  ]
}
""".trimIndent()
}
