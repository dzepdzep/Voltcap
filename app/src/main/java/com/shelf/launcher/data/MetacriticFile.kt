package com.shelf.launcher.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.Normalizer

/**
 * 0.6.15: Metacritic scores from a file you load (Sort › Metacritic score › Load a scores file): a CSV with the columns
 * name, platform, metascore — such as the "Metacritic games" data set (games.csv, also zipped). Only name, console and
 * score are kept (`metacritic_scores.tsv` in Voltcap's own storage, nothing sent anywhere); the summaries and the rest
 * of the file are not. Used by the Metacritic and Critic sorts for games whose details have no score (RAWG).
 *
 * Matching: the game's name without its tags ("(USA)", "[!]"), "Title, The" turned round, accents, punctuation and a
 * leading "the" left out, on the same console (PS2 ↔ "PlayStation 2" …); Android games match iOS, then PC; DOS matches
 * PC. Consoles the file doesn't cover (NES, SNES, Genesis, Game Boy …) get no score.
 */
object MetacriticFile {
    private const val FILE = "metacritic_scores.tsv"

    /** Voltcap's console → the file's platform names to look in, in order. */
    private val PLATFORMS: Map<String, List<String>> = mapOf(
        "PS1" to listOf("PlayStation"), "PS2" to listOf("PlayStation 2"), "PS3" to listOf("PlayStation 3"),
        "PS4" to listOf("PlayStation 4"), "PS5" to listOf("PlayStation 5"), "PSP" to listOf("PSP"),
        "PSVita" to listOf("PlayStation Vita"), "GameCube" to listOf("GameCube"), "Wii" to listOf("Wii"),
        "WiiU" to listOf("Wii U"), "Switch" to listOf("Switch"), "N64" to listOf("Nintendo 64"),
        "GBA" to listOf("Game Boy Advance"), "NDS" to listOf("DS"), "3DS" to listOf("3DS"),
        "Dreamcast" to listOf("Dreamcast"), "Xbox" to listOf("Xbox"), "Xbox360" to listOf("Xbox 360"),
        "XboxOne" to listOf("Xbox One"), "DOS" to listOf("PC"), "ScummVM" to listOf("PC"),
        "Android" to listOf("iOS", "PC")
    )

    /** "platform|normalised name" → metascore. */
    @Volatile private var scores: Map<String, Int> = emptyMap()
    /** 0.6.15: the RAWG fill-in's progress (done, total) for the Sort window; null when it isn't running. */
    val rawgProgress = androidx.compose.runtime.mutableStateOf<Pair<Int, Int>?>(null)
    /** 0.6.15: whether a RAWG key is connected (set by the view model). */
    val rawgConnected = androidx.compose.runtime.mutableStateOf(false)

    /** How many scores are loaded (Compose state for the Sort window). */
    val count = mutableIntStateOf(0)
    /** Called after the scores change (the view model re-sorts). */
    @Volatile var onChange: (() -> Unit)? = null
    @Volatile private var loaded = false

    /** Reads the kept scores (once; blocking, call off the main thread). */
    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val f = File(c.applicationContext.filesDir, FILE)
        if (!f.isFile) return
        val m = HashMap<String, Int>(40_000)
        runCatching {
            f.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val t = line.split('\t')
                    if (t.size >= 3) t[2].toIntOrNull()?.let { m[t[0] + "|" + t[1]] = it }
                }
            }
        }
        scores = m
        count.intValue = m.size
        if (m.isNotEmpty()) onChange?.invoke()
    }

    /** A game's score from the file, or null. */
    fun score(g: Game): Int? {
        val s = scores
        if (s.isEmpty()) return null
        val key = when {
            g.sourceType == SourceType.APP -> "Android"
            else -> g.platform
        }
        val plats = PLATFORMS[key] ?: return null
        val n = norm(g.name)
        if (n.isEmpty()) return null
        for (p in plats) s["$p|$n"]?.let { return it }
        return null
    }

    /**
     * Loads [uri] (a .csv, or a .zip with one) and keeps its scores in place of any earlier file. Blocking; returns how
     * many games have a score.
     */
    fun import(c: Context, uri: Uri): Int {
        val app = c.applicationContext
        val out = HashMap<String, Int>(40_000)
        val input = app.contentResolver.openInputStream(uri) ?: error("Couldn't open the file")
        input.use { raw ->
            val buffered = java.io.BufferedInputStream(raw)
            buffered.mark(4)
            val sig = ByteArray(2).also { buffered.read(it) }
            buffered.reset()
            if (sig[0] == 'P'.code.toByte() && sig[1] == 'K'.code.toByte()) {
                val zip = java.util.zip.ZipInputStream(buffered)
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (!e.isDirectory && e.name.endsWith(".csv", ignoreCase = true)) { readCsv(BufferedReader(InputStreamReader(zip, Charsets.UTF_8)), out); break }
                }
            } else readCsv(BufferedReader(InputStreamReader(buffered, Charsets.UTF_8)), out)
        }
        if (out.isEmpty()) error("No scores found — the file needs the columns name, platform and metascore")
        val f = File(app.filesDir, FILE)
        val tmp = File(app.filesDir, "$FILE.tmp")
        tmp.bufferedWriter().use { w -> out.forEach { (k, v) -> w.append(k.replaceFirst('|', '\t')).append('\t').append(v.toString()).append('\n') } }
        tmp.renameTo(f)
        scores = out
        loaded = true
        count.intValue = out.size
        onChange?.invoke()
        return out.size
    }

    /** Forgets the loaded scores (Sort window › Remove). */
    fun clear(c: Context) {
        File(c.applicationContext.filesDir, FILE).delete()
        scores = emptyMap()
        count.intValue = 0
        onChange?.invoke()
    }

    /** CSV (quoted fields may hold commas, "" and line breaks) → [out]. */
    private fun readCsv(r: BufferedReader, out: MutableMap<String, Int>) {
        var header: List<String>? = null
        var iName = -1; var iPlat = -1; var iScore = -1
        val field = StringBuilder()
        val row = ArrayList<String>(8)
        var quoted = false
        fun endRow() {
            row.add(field.toString()); field.setLength(0)
            val h = header
            if (h == null) {
                header = row.map { it.trim().lowercase() }
                iName = header!!.indexOf("name"); iPlat = header!!.indexOf("platform"); iScore = header!!.indexOf("metascore")
                if (iName < 0 || iPlat < 0 || iScore < 0) error("No name / platform / metascore columns")
            } else if (row.size > maxOf(iName, iPlat, iScore)) {
                val score = row[iScore].trim().toIntOrNull()
                val n = norm(row[iName])
                val p = row[iPlat].trim()
                if (score != null && score in 1..100 && n.isNotEmpty() && p.isNotEmpty()) {
                    val k = "$p|$n"
                    // The same name twice on a console (re-releases): the higher score.
                    if ((out[k] ?: -1) < score) out[k] = score
                }
            }
            row.clear()
        }
        while (true) {
            val ch = r.read()
            if (ch < 0) { if (field.isNotEmpty() || row.isNotEmpty()) endRow(); break }
            val c = ch.toChar()
            if (quoted) {
                if (c == '"') {
                    r.mark(1)
                    val next = r.read()
                    if (next == '"'.code) field.append('"') else { quoted = false; if (next >= 0) r.reset() }
                } else field.append(c)
            } else when (c) {
                '"' -> quoted = true
                ',' -> { row.add(field.toString()); field.setLength(0) }
                '\n' -> endRow()
                '\r' -> Unit
                else -> field.append(c)
            }
        }
    }

    private val TAGS = Regex("""\s*[\(\[][^)\]]*[\)\]]""")
    private val THE_END = Regex("""^(.*?),\s*the\b(.*)$""", RegexOption.IGNORE_CASE)
    private val NON_WORD = Regex("""[^a-z0-9]+""")

    /** "Legend of Zelda, The - Ocarina of Time (USA)" and "The Legend of Zelda: Ocarina of Time" → "legend of zelda ocarina of time". */
    fun norm(name: String): String {
        var s = TAGS.replace(name, "")
        THE_END.matchEntire(s.trim())?.let { m -> s = "The " + m.groupValues[1] + m.groupValues[2] }
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        s = s.lowercase().replace("&", " and ")
        s = NON_WORD.replace(s, " ").trim()
        if (s.startsWith("the ")) s = s.removePrefix("the ")
        return s
    }
}
