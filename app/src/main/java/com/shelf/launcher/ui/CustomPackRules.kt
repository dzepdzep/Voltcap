package com.shelf.launcher.ui

/**
 * Custom themes and visualizers ("packs"): the rules, kept in plain Kotlin (no Android) so they can be
 * tested on their own.
 *
 * A pack is one small text file: a header, then ONE shader function.
 *
 *     // voltpack: 1
 *     // name: Tide lines
 *     // kind: theme            (or: visualizer)
 *     // author: you
 *     float3 voltScene(float2 uv, float asp, float t) { ... return colour; }      // theme
 *     float3 voltViz(float2 uv, float asp, float t)   { ... return colour; }      // visualizer
 *
 * Voltcap wraps that function (CustomPackShaders), so a pack cannot declare uniforms or pictures, cannot
 * write its own main(), and only ever sees the values listed in the tutorial. On top of that [validate]
 * enforces a budget so a pack can't overload a handheld: file size, loop shapes and a cost estimate.
 * The scene is then drawn like every built-in one (background quality, idle slow-down, still while
 * covered, Lite effects) with a lower resolution and frame-rate cap (see PackRules).
 */
internal enum class PackKind(val label: String, val entry: String, val keyword: String) {
    THEME("Theme", "voltScene", "theme"),
    VISUALIZER("Visualizer", "voltViz", "visualizer");
}

/** Every limit in one place (the tutorial prints them from here). */
internal object PackRules {
    const val HEADER_VERSION = "1"
    const val MAX_BYTES = 12_000
    const val MAX_LINES = 320
    const val MAX_PACKS = 12
    const val MAX_NAME = 32
    /** The biggest file that is even read (a bigger one is refused before looking inside). */
    const val READ_CAP = 64_000

    const val MAX_LOOP = 24            // iterations of one for loop
    const val MAX_LOOPS = 4            // for loops in the whole file
    const val MAX_NEST_PRODUCT = 64    // iterations of loops inside loops
    const val MAX_COST = 160f          // the budget meter's 100 %

    /** Drawing caps on top of the app's own quality settings (a pack never draws sharper / faster than this). */
    const val MAX_SCALE_THEME = 0.6f
    const val MAX_SCALE_VIZ = 0.75f
    const val MIN_FRAME_MS = 14        // at most ~60 frames a second (update 126; it was ~30)
    const val HEAVY_SCALE = 0.4f       // once the home screen has proved slow with it running
    const val HEAVY_FRAME_MS = 28      // ~30 frames a second then (it was ~15)
}

internal class PackReport(val problems: List<String>, val costPercent: Int) {
    val ok get() = problems.isEmpty()
}

internal class PackParsed(
    val name: String,
    val author: String,
    val kind: PackKind?,
    val hasMarker: Boolean
)

internal object CustomPackRules {
    // What each built-in helper costs (in "units"; the budget is PackRules.MAX_COST). Roughly the number of
    // hash lookups / noise taps, which is what makes a shader slow on a weak GPU.
    private val CALL_COST = mapOf(
        "fbm" to 9f, "fbm3" to 7f, "vnoise" to 2.2f, "dots" to 3f, "paletteField" to 15f,
        "hash11" to 0.2f, "hash12" to 0.3f, "hash22" to 0.4f,
        "rot" to 0.3f, "sdSegment" to 0.3f, "sdRoundBox" to 0.3f, "glow" to 0.1f,
        "eqAt" to 0.5f, "eqBand" to 0.2f, "vivid" to 0.1f, "room" to 0.02f, "waveX" to 0.02f,
        "sin" to 0.15f, "cos" to 0.15f, "tan" to 0.2f, "atan" to 0.3f, "exp" to 0.15f, "log" to 0.15f,
        "pow" to 0.3f, "sqrt" to 0.1f
    )
    private const val LOOP_STEP_COST = 0.3f
    private const val UNKNOWN_CALL_COST = 0.02f

    private val FORBIDDEN = listOf(
        Regex("#") to "No # lines (#include, #define): everything you need is already there.",
        Regex("\\buniform\\b") to "Don't declare uniforms: use the ones Voltcap gives you (see the tutorial).",
        Regex("\\b(shader|eval)\\b") to "Pictures aren't available to packs (no shader / eval).",
        Regex("\\b(while|do)\\b") to "No while / do loops: only for (int i = 0; i < 12; i++) { ... }.",
        Regex("\\bdiscard\\b") to "discard isn't allowed.",
        Regex("\\bmain\\b") to "Don't write main(): Voltcap calls your function."
    )

    private val LOOP_HEAD = Regex("\\bfor\\s*\\(\\s*int\\s+(\\w+)\\s*=\\s*0\\s*;\\s*\\1\\s*<\\s*(\\d+)\\s*;\\s*\\1\\s*\\+\\+\\s*\\)\\s*\\{")
    private val FUNC_HEAD = Regex("(?m)^[ \\t]*(?:float|float2|float3|float4|float2x2|half4|half|int|bool|void)\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*\\{")

    /** Reads the "// key: value" lines at the top. */
    fun parseHeader(text: String): PackParsed {
        var name = ""
        var author = ""
        var kind: PackKind? = null
        var marker = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (!line.startsWith("//")) break
            val body = line.removePrefix("//").trim()
            val i = body.indexOf(':')
            if (i <= 0) continue
            val key = body.substring(0, i).trim().lowercase()
            val value = body.substring(i + 1).trim()
            when (key) {
                "voltpack" -> marker = value.startsWith(PackRules.HEADER_VERSION)
                "name" -> name = value.take(PackRules.MAX_NAME)
                "author" -> author = value.take(32)
                "kind" -> kind = when (value.lowercase()) {
                    "theme", "scene", "background" -> PackKind.THEME
                    "visualizer", "visualiser", "music" -> PackKind.VISUALIZER
                    else -> null
                }
            }
        }
        return PackParsed(name, author, kind, marker)
    }

    fun stripComments(src: String): String =
        src.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)) { m -> m.value.replace(Regex("[^\n]"), " ") }
            .replace(Regex("//[^\n]*"), "")

    /** The text that is compiled: prelude (+ music helpers), the pack exactly as written, then main(). */
    fun buildPrefix(kind: PackKind): String =
        CustomPackShaders.PRELUDE + (if (kind == PackKind.VISUALIZER) "\n" + CustomPackShaders.VIZ_EXTRAS else "") + "\n"

    fun buildSource(kind: PackKind, text: String): String = buildPrefix(kind) + text + "\n" + wrapper(kind)

    /** How many lines come before the pack's own first line in [buildSource] (to report error lines in the pack's numbers). */
    fun linesBefore(kind: PackKind): Int = buildPrefix(kind).count { it == '\n' }

    /** Uniforms the compiled scene really has; the app sets these and nothing else (never a picture). */
    fun uniformsOf(kind: PackKind): Set<String> {
        val theme = setOf("uRes", "uTime", "uBg", "uSurf", "uAcc", "uSec", "uLod", "uDensity")
        return if (kind == PackKind.VISUALIZER) theme + CustomPackShaders.VIZ_UNIFORMS else theme
    }

    private fun wrapper(kind: PackKind): String {
        // The never-true test touches every uniform, so the shader compiler can't drop any of them: setting a
        // dropped uniform aborts the app on Android 13. It depends on live values, so it can't be folded away.
        val keep = if (kind == PackKind.VISUALIZER)
            "uRes.x + uTime + uLod + uDensity + uBg.r + uSurf.r + uAcc.r + uSec.r + uBass + uMid + uHigh + uBeatAge + uPlaying + uIntensity + uEqA.x + uEqB.x + uWaveY + uWaveH + uWaveX + uPlayT + uBassT + uMidT + uHighT"
        else
            "uRes.x + uTime + uLod + uDensity + uBg.r + uSurf.r + uAcc.r + uSec.r"
        return """
half4 main(float2 fc) {
    float2 uv = fc / uRes.y;
    float asp = uRes.x / uRes.y;
    float3 col = ${kind.entry}(uv, asp, uTime);
    float keep = $keep;
    if (keep < -1.0e20) { col = float3(1.0); }
    col = clamp(col, 0.0, 4.0);
    return half4(finish(col, fc), 1.0);
}
"""
    }

    private class LoopHead(val n: Int, val end: Int)
    private class Fn(val name: String, val start: Int, val end: Int)

    fun validate(text: String): PackReport {
        val problems = ArrayList<String>()
        val bytes = text.toByteArray(Charsets.UTF_8).size
        if (bytes > PackRules.MAX_BYTES) problems += "The file is ${bytes / 1000} KB; the limit is ${PackRules.MAX_BYTES / 1000} KB."
        val lines = text.count { it == '\n' } + 1
        if (lines > PackRules.MAX_LINES) problems += "$lines lines; the limit is ${PackRules.MAX_LINES}."
        val head = parseHeader(text)
        if (!head.hasMarker) problems += "The first line must be: // voltpack: ${PackRules.HEADER_VERSION}"
        if (head.name.isBlank()) problems += "Add a name line: // name: My pack"
        if (head.kind == null) problems += "Add a kind line: // kind: theme   (or: // kind: visualizer)"
        if (problems.isNotEmpty()) return PackReport(problems, 0)
        val kind = head.kind!!

        val code = stripComments(text)
        for ((re, why) in FORBIDDEN) if (re.containsMatchIn(code)) problems += why
        val entry = Regex("float3\\s+${kind.entry}\\s*\\(\\s*float2\\s+\\w+\\s*,\\s*float\\s+\\w+\\s*,\\s*float\\s+\\w+\\s*\\)\\s*\\{")
        if (!entry.containsMatchIn(code)) {
            problems += "Missing the function: float3 ${kind.entry}(float2 uv, float asp, float t) { ... return colour; }"
        }

        // Loops: only the strict shape, small, few, and not nested deeply.
        val loops = HashMap<Int, LoopHead>()
        LOOP_HEAD.findAll(code).forEach { m -> loops[m.range.first] = LoopHead(m.groupValues[2].toIntOrNull() ?: Int.MAX_VALUE, m.range.last) }
        val forWords = Regex("\\bfor\\b").findAll(code).count()
        if (forWords != loops.size) problems += "Every loop must look exactly like: for (int i = 0; i < 12; i++) { ... }  (a number, and braces)."
        if (loops.size > PackRules.MAX_LOOPS) problems += "${loops.size} loops; at most ${PackRules.MAX_LOOPS}."
        loops.values.filter { it.n > PackRules.MAX_LOOP }.forEach { problems += "A loop runs ${it.n} times; the limit is ${PackRules.MAX_LOOP}." }
        if (problems.isNotEmpty()) return PackReport(problems.distinct(), 0)

        // Functions, and what each one costs.
        val fns = FUNC_HEAD.findAll(code).map { m ->
            val open = code.indexOf('{', m.range.last - 1)
            Fn(m.groupValues[1], open, matchingBrace(code, open))
        }.toList()
        if (fns.any { it.end < 0 }) return PackReport(problems + "The { } don't match up.", 0)
        val byName = fns.associateBy { it.name }
        val direct = HashMap<String, Float>()
        val calls = HashMap<String, MutableList<Pair<String, Float>>>()
        var worstNest = 1
        for (fn in fns) {
            val list = ArrayList<Pair<String, Float>>()
            var cost = 0f
            var depth = 0
            val ns = ArrayList<Int>()
            val depths = ArrayList<Int>()
            var mult = 1f
            var i = fn.start
            while (i < fn.end) {
                val lh = loops[i]
                if (lh != null) {
                    cost += LOOP_STEP_COST * lh.n * mult
                    ns += lh.n; mult *= lh.n
                    depth++; depths += depth
                    worstNest = maxOf(worstNest, mult.toInt())
                    i = lh.end + 1
                    continue
                }
                val c = code[i]
                when {
                    c == '{' -> depth++
                    c == '}' -> {
                        if (depths.isNotEmpty() && depths.last() == depth) {
                            depths.removeAt(depths.size - 1); ns.removeAt(ns.size - 1)
                            mult = ns.fold(1f) { a, b -> a * b }
                        }
                        depth--
                    }
                    c.isLetter() || c == '_' -> {
                        var j = i
                        while (j < fn.end && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                        val id = code.substring(i, j)
                        var k = j
                        while (k < fn.end && code[k].isWhitespace()) k++
                        if (k < fn.end && code[k] == '(') {
                            when {
                                id in byName -> list += id to mult
                                id in CALL_COST -> cost += CALL_COST.getValue(id) * mult
                                id.startsWith("float") || id.startsWith("half") || id == "if" || id == "int" || id == "bool" -> Unit
                                else -> cost += UNKNOWN_CALL_COST * mult
                            }
                        }
                        i = j
                        continue
                    }
                }
                i++
            }
            direct[fn.name] = cost
            calls[fn.name] = list
        }
        if (worstNest > PackRules.MAX_NEST_PRODUCT) {
            problems += "Loops inside loops run $worstNest times in all; the limit is ${PackRules.MAX_NEST_PRODUCT}."
        }

        // Total from the entry function down. Recursion isn't possible in shaders; refuse it with a clear message.
        val memo = HashMap<String, Float>()
        val visiting = HashSet<String>()
        var recursion = false
        fun total(name: String): Float {
            memo[name]?.let { return it }
            if (!visiting.add(name)) { recursion = true; return 0f }
            var t = direct[name] ?: 0f
            for ((callee, m) in calls[name].orEmpty()) t += m * total(callee)
            visiting.remove(name)
            memo[name] = t
            return t
        }
        val cost = if (byName.containsKey(kind.entry)) total(kind.entry) else 0f
        if (recursion) problems += "A function calls itself (directly or not); shaders can't do that."
        val percent = Math.round(cost / PackRules.MAX_COST * 100f)
        if (percent > 100) problems += "Too heavy: $percent % of the budget. Use fewer loops or noise (fbm) calls."
        return PackReport(problems.distinct(), percent.coerceIn(0, 999))
    }

    private fun matchingBrace(s: String, open: Int): Int {
        var d = 0
        var i = open
        while (i < s.length) {
            if (s[i] == '{') d++ else if (s[i] == '}') { d--; if (d == 0) return i }
            i++
        }
        return -1
    }

    /** "my pack!" -> "my-pack" (for the file name). */
    fun slug(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(24).ifEmpty { "pack" }
}
