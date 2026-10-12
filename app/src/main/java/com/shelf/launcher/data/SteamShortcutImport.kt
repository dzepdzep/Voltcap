package com.shelf.launcher.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/** One Steam game found in a folder the user picked. */
data class ImportCandidate(
    val name: String,
    /** Steam AppID; 0 when the entry can't be imported (non-Steam shortcut). */
    val appId: Int,
    /** File name plus what kind of file it came from, e.g. "Portal 2.url (Steam shortcut)". */
    val source: String,
    val alreadyInLibrary: Boolean,
    val importable: Boolean,
    val reason: String? = null
)

/** Remembers the last folder scanned so it can be rescanned in one tap. */
class SteamImportStore(context: Context) {
    private val prefs = context.getSharedPreferences("steam_import", Context.MODE_PRIVATE)
    fun lastFolder(): String? = prefs.getString("last_folder", null)
    fun setLastFolder(uri: String?) { prefs.edit().putString("last_folder", uri).apply() }
}

/**
 * Finds Steam games in a folder: desktop shortcuts (.url / .desktop), library
 * manifests (appmanifest_*.acf), Steam's shortcuts.vdf and exported text/JSON
 * shortcut files. Read-only, bounded (3 levels, ~2000 entries) and tolerant of
 * malformed files — a bad file is skipped, never fatal.
 */
object SteamShortcutImport {
    private const val MAX_DEPTH = 3
    private const val MAX_ENTRIES = 2000
    private const val MAX_TEXT_BYTES = 256 * 1024
    private const val MAX_VDF_BYTES = 4 * 1024 * 1024

    private val RUN_ID = Regex("steam://(?:rungameid|run)/(\\d+)", RegexOption.IGNORE_CASE)
    private val SKIP_NAMES = listOf("Steamworks Common Redistributables", "Proton", "Steam Linux Runtime")
    private val SKIP_IDS = setOf(228980, 1070560, 1391110, 1628350, 1493710, 2180100, 1113280, 1245040, 1826330)

    // Higher wins when the same AppID turns up in several files.
    private const val P_ACF = 4
    private const val P_URL = 3
    private const val P_DESKTOP = 2
    private const val P_OTHER = 1
    private const val P_VDF = 0

    private class Node(
        val name: String,
        val isDir: Boolean,
        val size: Long,
        val open: () -> InputStream?,
        val children: () -> List<Node>
    )

    internal class Found(val name: String, val appId: Int, val source: String, val prio: Int)

    /** Scan a folder chosen with the system folder picker (a SAF tree Uri). */
    suspend fun scan(context: Context, tree: Uri, existingAppIds: Set<Int>): List<ImportCandidate> =
        withContext(Dispatchers.IO) { scanNode(safRoot(context, tree), existingAppIds) }

    /** Scan a plain directory path (used when the folder is directly readable). */
    suspend fun scan(dir: File, existingAppIds: Set<Int>): List<ImportCandidate> =
        withContext(Dispatchers.IO) { scanNode(fileNode(dir), existingAppIds) }

    // ---- folder walking --------------------------------------------------------

    private fun fileNode(f: File): Node = Node(
        f.name, f.isDirectory, runCatching { f.length() }.getOrDefault(0L),
        { runCatching { f.inputStream() }.getOrNull() },
        { runCatching { f.listFiles()?.map(::fileNode) }.getOrNull().orEmpty() }
    )

    private fun safRoot(context: Context, tree: Uri): Node {
        val resolver = context.contentResolver
        fun node(docId: String, name: String, isDir: Boolean, size: Long): Node = Node(
            name, isDir, size,
            { runCatching { resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, docId)) }.getOrNull() },
            {
                val out = ArrayList<Node>()
                runCatching {
                    val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
                    resolver.query(
                        kids,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_SIZE
                        ), null, null, null
                    )?.use { c ->
                        while (c.moveToNext() && out.size < MAX_ENTRIES) {
                            val dir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                            out += node(c.getString(0), c.getString(1) ?: "", dir, if (c.isNull(3)) 0L else c.getLong(3))
                        }
                    }
                }
                out
            }
        )
        return node(DocumentsContract.getTreeDocumentId(tree), "", true, 0L)
    }

    private suspend fun scanNode(root: Node, existing: Set<Int>): List<ImportCandidate> {
        val found = ArrayList<Found>()
        val nonSteam = ArrayList<ImportCandidate>()
        var visited = 0

        suspend fun walk(dir: Node, depth: Int) {
            coroutineContext.ensureActive()
            val kids = dir.children()
            for (k in kids) {
                if (visited >= MAX_ENTRIES) return
                visited++
                if (k.isDir) {
                    if (depth < MAX_DEPTH) walk(k, depth + 1)
                    continue
                }
                coroutineContext.ensureActive()
                runCatching { handleFile(k, found, nonSteam) }
            }
        }
        walk(root, 0)

        // One entry per AppID, keeping the most trustworthy name (acf > url > desktop > other > vdf).
        val best = LinkedHashMap<Int, Found>()
        for (f in found) {
            val cur = best[f.appId]
            if (cur == null || f.prio > cur.prio || (cur.name.isBlank() && f.name.isNotBlank())) best[f.appId] = f
        }
        val good = best.values.map {
            ImportCandidate(it.name.ifBlank { "Steam game ${it.appId}" }, it.appId, it.source, it.appId in existing, true)
        }.sortedBy { it.name.lowercase() }
        val bad = nonSteam.distinctBy { it.name.lowercase() }.sortedBy { it.name.lowercase() }
        return good + bad
    }

    private fun handleFile(n: Node, found: MutableList<Found>, nonSteam: MutableList<ImportCandidate>) {
        val lower = n.name.lowercase()
        val base = n.name.substringBeforeLast('.', n.name)
        when {
            lower.startsWith("appmanifest_") && lower.endsWith(".acf") ->
                readText(n, MAX_TEXT_BYTES)?.let { parseAcf(it, n.name)?.let(found::add) }
            lower == "shortcuts.vdf" -> {
                val bytes = readBytes(n, MAX_VDF_BYTES) ?: return
                for (e in parseShortcutsVdf(bytes)) {
                    val hay = e.exe + "\n" + e.launchOptions
                    val id = RUN_ID.find(hay)?.groupValues?.get(1)?.toLongOrNull()
                    val src = "${n.name} (Steam shortcuts.vdf)"
                    if (id != null && id in 1..Int.MAX_VALUE) found += Found(e.name, id.toInt(), src, P_VDF)
                    else nonSteam += ImportCandidate(
                        e.name.ifBlank { "Unnamed shortcut" }, 0, src, false, false,
                        "Not importable (non-Steam game)"
                    )
                }
            }
            lower.endsWith(".url") -> readText(n, MAX_TEXT_BYTES)?.let { t ->
                val line = t.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("URL=", true) } ?: return
                addRunId(line, base, "${n.name} (Steam shortcut)", P_URL, found, nonSteam)
            }
            lower.endsWith(".desktop") -> readText(n, MAX_TEXT_BYTES)?.let { t ->
                var name: String? = null
                var target: String? = null
                for (raw in t.lineSequence()) {
                    val l = raw.trim()
                    if (name == null && l.startsWith("Name=")) name = l.substring(5).trim()
                    else if (target == null && (l.startsWith("Exec=") || l.startsWith("URL=")) && RUN_ID.containsMatchIn(l)) target = l
                }
                if (target != null) addRunId(target, name?.takeIf { it.isNotBlank() } ?: base, "${n.name} (desktop entry)", P_DESKTOP, found, nonSteam)
            }
            lower.endsWith(".json") || lower.endsWith(".txt") -> {
                if (n.size > 64 * 1024) return
                val t = readText(n, 64 * 1024) ?: return
                if (!RUN_ID.containsMatchIn(t)) return
                val src = "${n.name} (shortcut export)"
                var any = false
                if (lower.endsWith(".json")) {
                    val root = runCatching { JsonParser.parseString(t) }.getOrNull()
                    if (root != null) any = collectJson(root, base, src, found)
                }
                if (!any) {
                    val ids = RUN_ID.findAll(t).mapNotNull { it.groupValues[1].toLongOrNull() }
                        .filter { it in 1..Int.MAX_VALUE }.distinct().toList()
                    ids.forEach { found += Found(if (ids.size == 1) base else "$base (${it})", it.toInt(), src, P_OTHER) }
                }
            }
        }
    }

    private fun addRunId(
        line: String, name: String, source: String, prio: Int,
        found: MutableList<Found>, nonSteam: MutableList<ImportCandidate>
    ) {
        val id = RUN_ID.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: return
        if (id in 1..Int.MAX_VALUE) found += Found(name, id.toInt(), source, prio)
        else nonSteam += ImportCandidate(name, 0, source, false, false, "Not a valid Steam AppID")
    }

    /** Walks JSON looking for objects that hold a steam://rungameid link; name comes from "name"/"title". */
    private fun collectJson(e: JsonElement, fallback: String, src: String, out: MutableList<Found>): Boolean {
        var any = false
        fun visit(el: JsonElement, depth: Int) {
            if (depth > 6) return
            if (el.isJsonArray) el.asJsonArray.forEach { visit(it, depth + 1) }
            else if (el.isJsonObject) {
                val o = el.asJsonObject
                var id: Long? = null
                var name: String? = null
                for ((k, v) in o.entrySet()) {
                    if (!v.isJsonPrimitive || !v.asJsonPrimitive.isString) continue
                    val s = v.asString
                    if (id == null) RUN_ID.find(s)?.groupValues?.get(1)?.toLongOrNull()?.let { id = it }
                    if (name == null && (k.equals("name", true) || k.equals("title", true)) && s.isNotBlank()) name = s.trim()
                }
                val i = id
                if (i != null && i in 1..Int.MAX_VALUE) { out += Found(name ?: fallback, i.toInt(), src, P_OTHER); any = true }
                else o.entrySet().forEach { visit(it.value, depth + 1) }
            }
        }
        visit(e, 0)
        return any
    }

    // ---- parsers (internal for testing) -----------------------------------------

    private val ACF_ID = Regex("\"appid\"\\s+\"(\\d+)\"", RegexOption.IGNORE_CASE)
    private val ACF_NAME = Regex("\"name\"\\s+\"((?:[^\"\\\\]|\\\\.)*)\"", RegexOption.IGNORE_CASE)

    internal fun parseAcf(text: String, fileName: String): Found? {
        val id = ACF_ID.find(text)?.groupValues?.get(1)?.toLongOrNull()
            ?: Regex("appmanifest_(\\d+)", RegexOption.IGNORE_CASE).find(fileName)?.groupValues?.get(1)?.toLongOrNull()
            ?: return null
        if (id !in 1..Int.MAX_VALUE) return null
        val name = ACF_NAME.find(text)?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\\\", "\\")?.trim().orEmpty()
        if (id.toInt() in SKIP_IDS || SKIP_NAMES.any { name.contains(it, ignoreCase = true) }) return null
        return Found(name, id.toInt(), "$fileName (Steam library)", P_ACF)
    }

    internal class VdfShortcut(val name: String, val exe: String, val launchOptions: String)

    /** Binary VDF: 0x00 object, 0x01 string, 0x02 int32, 0x08 end. Stops quietly at anything malformed. */
    internal fun parseShortcutsVdf(b: ByteArray): List<VdfShortcut> {
        var p = 0
        fun cstr(): String? {
            val s = p
            while (p < b.size && b[p] != 0.toByte()) p++
            if (p >= b.size) return null
            val r = String(b, s, p - s, Charsets.UTF_8)
            p++
            return r
        }
        // Returns key->value (String, Int or nested Map) until the 0x08 terminator.
        fun obj(depth: Int): Map<String, Any>? {
            if (depth > 6) return null
            val m = LinkedHashMap<String, Any>()
            while (p < b.size) {
                val t = b[p++].toInt()
                if (t == 8) return m
                val key = cstr() ?: return null
                when (t) {
                    0 -> m[key.lowercase()] = obj(depth + 1) ?: return null
                    1 -> m[key.lowercase()] = cstr() ?: return null
                    2 -> {
                        if (p + 4 > b.size) return null
                        m[key.lowercase()] = (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8) or
                            ((b[p + 2].toInt() and 255) shl 16) or ((b[p + 3].toInt() and 255) shl 24)
                        p += 4
                    }
                    else -> return null
                }
            }
            return m
        }
        val out = ArrayList<VdfShortcut>()
        runCatching {
            val top = obj(0) ?: return@runCatching
            @Suppress("UNCHECKED_CAST")
            val list = top["shortcuts"] as? Map<String, Any> ?: return@runCatching
            for (v in list.values) {
                @Suppress("UNCHECKED_CAST")
                val e = v as? Map<String, Any> ?: continue
                out += VdfShortcut(
                    (e["appname"] as? String).orEmpty(), (e["exe"] as? String).orEmpty(),
                    (e["launchoptions"] as? String).orEmpty()
                )
            }
        }
        return out
    }

    // ---- reading ------------------------------------------------------------------

    private fun readBytes(n: Node, max: Int): ByteArray? = runCatching {
        n.open()?.use { ins ->
            val bo = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (bo.size() < max) {
                val r = ins.read(buf)
                if (r < 0) break
                bo.write(buf, 0, r)
            }
            bo.toByteArray()
        }
    }.getOrNull()

    private fun readText(n: Node, max: Int): String? = readBytes(n, max)?.let(::decode)

    /** UTF-8 / UTF-16 with or without a BOM; anything else is read as UTF-8. */
    private fun decode(b: ByteArray): String {
        fun bom(vararg v: Int) = b.size >= v.size && v.indices.all { (b[it].toInt() and 255) == v[it] }
        return when {
            bom(0xEF, 0xBB, 0xBF) -> String(b, 3, b.size - 3, Charsets.UTF_8)
            bom(0xFF, 0xFE) -> String(b, 2, b.size - 2, Charsets.UTF_16LE)
            bom(0xFE, 0xFF) -> String(b, 2, b.size - 2, Charsets.UTF_16BE)
            else -> String(b, Charsets.UTF_8)
        }
    }
}
