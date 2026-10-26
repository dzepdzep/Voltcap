package com.shelf.launcher.data

import android.content.Context

/*
 * Update 330: PS2 save icons from PS2IODB (the PlayStation 2 Icon Open Database, ps2iodb.com — a community archive of the
 * 3D save icons extracted from real memory cards, github.com/Issung/PS2IODB) for PS2 games with no save of their own on
 * this device.
 *
 * - Which title is which icon: a list shipped with Voltcap (res/raw/ps2iodb_index.tsv: the database's title, tab, its
 *   icon's folder — made from its titles list), matched to the game's name with the region / version tags and
 *   punctuation left out.
 * - The icon: one zip per title (icon.zip: iconsys.json, the model as .obj, its texture as .png, its animation as .anim)
 *   fetched once, only when the game's icon is about to be shown, and kept in Voltcap's files; a title the database
 *   doesn't have is asked again after a week, a failed download after an hour.
 * - Turned into the same icon the memory-card reader makes (Ps2Icons.Icon): the .obj's positions back into the console's
 *   own axes (x and y turned round), its texture back the right way up, the .anim's shapes and timelines as its
 *   animation. Never written to a save; always labelled as the database's, never as the game's own save.
 */
object Ps2IconDb {
    private const val BASE = "https://ps2iodb.com/icons/"
    private const val MAX_ZIP = 3_000_000
    private const val RETRY_MISSING_MS = 7L * 24 * 3600_000
    private const val RETRY_FAILED_MS = 3600_000L

    @Volatile private var index: Map<String, String>? = null
    private val parsed = android.util.LruCache<String, Ps2Icons.Parsed>(64)
    private val failedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    /** The database's titles and names normalised the same way: lower case letters and digits only, "and" for "&". */
    fun norm(s: String): String {
        val t = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return t.lowercase().replace("&", "and").replace(Regex("[^a-z0-9]"), "")
    }

    /** A game name as the database writes titles: region / version tags and "The" left out. */
    private fun keysFor(name: String): List<String> {
        val bare = name.replace(Regex("[(\\[][^)\\]]*[)\\]]"), " ").trim()
        val n = norm(bare)
        val out = linkedSetOf(n)
        if (n.startsWith("the")) out += n.removePrefix("the")
        // "Title, The" (sorted names).
        if (n.endsWith("the")) out += "the" + n.removeSuffix("the")
        // Without a subtitle after a colon or a dash.
        bare.split(Regex("\\s*[:–-]\\s+"), limit = 2).firstOrNull()?.let { out += norm(it) }
        return out.filter { it.length >= 3 }
    }

    private fun loadIndex(context: Context): Map<String, String> {
        index?.let { return it }
        val m = HashMap<String, String>(8192)
        runCatching {
            context.resources.openRawResource(com.shelf.launcher.R.raw.ps2iodb_index).bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    val title = line.substring(0, tab)
                    val slug = line.substring(tab + 1).trim()
                    if (slug.isEmpty()) continue
                    val k = norm(title)
                    if (k.isNotEmpty()) m.putIfAbsent(k, slug)
                    // Also without its subtitle, when that's not taken by another title.
                    title.split(Regex("\\s*[:–]\\s+"), limit = 2).takeIf { it.size == 2 }?.let { m.putIfAbsent(norm(it[0]) + "#short", slug) }
                }
            }
        }
        index = m
        return m
    }

    /** The database's icon folder for [game] (PS2 games only), or null. */
    fun slugFor(context: Context, game: Game): String? {
        if (!Ps2BrowserStyle.isPs2(game.platform)) return null
        val idx = loadIndex(context)
        val keys = keysFor(game.name)
        for (k in keys) idx[k]?.let { return it }
        // A shortened title ("Ace Combat 5" for "Ace Combat 5: The Unsung War").
        for (k in keys) idx["$k#short"]?.let { return it }
        return null
    }

    /** What's known already, without any reading. */
    fun cached(slug: String): Ps2Icons.Parsed? = parsed.get(slug)

    /**
     * The icon for [slug]: memory, the zip kept in Voltcap's files, else downloaded (blocking — off the main thread).
     * Null = not available now (offline, the database has none).
     */
    fun load(context: Context, slug: String): Ps2Icons.Parsed? {
        parsed.get(slug)?.let { return it }
        if (!slug.matches(Regex("[A-Za-z0-9_.-]{1,96}"))) return null
        val lock = locks.getOrPut(slug) { Any() }
        synchronized(lock) {
            parsed.get(slug)?.let { return it }
            val dir = java.io.File(context.filesDir, "ps2iodb").apply { mkdirs() }
            val zip = java.io.File(dir, "$slug.zip")
            val missing = java.io.File(dir, "$slug.none")
            if (!zip.isFile) {
                if (missing.isFile && System.currentTimeMillis() - missing.lastModified() < RETRY_MISSING_MS) return null
                failedAt[slug]?.let { if (System.currentTimeMillis() - it < RETRY_FAILED_MS) return null }
                when (download(BASE + slug + "/icon.zip", zip)) {
                    404 -> { runCatching { missing.writeText("") }; return null }
                    200 -> Unit
                    else -> { failedAt[slug] = System.currentTimeMillis(); return null }
                }
            }
            val p = runCatching { parseZip(zip.readBytes()) }.getOrNull()
            if (p?.icon == null) { runCatching { zip.delete() }; failedAt[slug] = System.currentTimeMillis(); return null }
            parsed.put(slug, p)
            return p
        }
    }

    /** HTTP status of the download into [to] (200 = saved). */
    private fun download(url: String, to: java.io.File): Int = runCatching {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 12000
        c.setRequestProperty("User-Agent", "Voltcap")
        try {
            val code = c.responseCode
            if (code != 200) return@runCatching code
            val len = c.contentLengthLong
            if (len > MAX_ZIP) return@runCatching 413
            val tmp = java.io.File(to.path + ".tmp")
            c.inputStream.use { inp ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024); var total = 0L
                    while (true) {
                        val n = inp.read(buf); if (n < 0) break
                        total += n; if (total > MAX_ZIP) throw java.io.IOException("too big")
                        out.write(buf, 0, n)
                    }
                }
            }
            if (!tmp.renameTo(to)) { tmp.delete(); return@runCatching -1 }
            200
        } finally { c.disconnect() }
    }.getOrDefault(-1)

    /** The zip's files → the reader's icon (and what its iconsys.json says). */
    internal fun parseZip(bytes: ByteArray): Ps2Icons.Parsed? {
        val files = HashMap<String, ByteArray>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { zin ->
            var total = 0L
            while (true) {
                val e = zin.nextEntry ?: break
                if (e.isDirectory) continue
                val name = e.name.substringAfterLast('/')
                val data = zin.readBytes()
                total += data.size
                if (total > 12_000_000) break
                files[name] = data
            }
        }
        val sysJson = files["iconsys.json"]?.let { org.json.JSONObject(String(it, Charsets.UTF_8)) } ?: return null
        val normal = clean(sysJson.optString("normal"))
        val obj = files["$normal.obj"] ?: files.entries.firstOrNull { it.key.endsWith(".obj") }?.value ?: return null
        val png = files["$normal.png"] ?: files.entries.firstOrNull { it.key.endsWith(".png") }?.value
        val anim = files["$normal.anim"]
        val sys = iconSys(sysJson)
        return Ps2Icons.Parsed(sys, icon(String(obj, Charsets.UTF_8), png, anim))
    }

    /** As the database's exporter names files: backslashes to hyphens, slashes to underscores. */
    private fun clean(n: String) = n.replace('\\', '-').replace('/', '_')

    private fun iconSys(j: org.json.JSONObject): Ps2Icons.IconSys {
        fun col(k: String): Int = runCatching { android.graphics.Color.parseColor(j.optString(k, "#000000")) }.getOrDefault(0xFF000000.toInt()) or (0xFF shl 24)
        val title = j.optString("title").trim()
        return Ps2Icons.IconSys(
            title, "", intArrayOf(col("bgColTL"), col("bgColTR"), col("bgColBL"), col("bgColBR")),
            (j.optInt("bgOpacity", 64).coerceIn(0, 128) / 128f), j.optString("normal"), j.optString("copy"), j.optString("delete")
        )
    }

    private fun icon(obj: String, png: ByteArray?, animJson: ByteArray?): Ps2Icons.Icon? {
        val px = ArrayList<Float>(6000); val col = ArrayList<Int>(2000); val uvs = ArrayList<Float>(4000)
        var maxC = 0f
        obj.lineSequence().forEach { line ->
            when {
                line.startsWith("v ") -> {
                    val f = line.substring(2).substringBefore('#').trim().split(Regex("\\s+"))
                    if (f.size >= 3) {
                        // Back into the console's axes: the exporter turned x and y round.
                        px += -(f[0].toFloatOrNull() ?: 0f); px += -(f[1].toFloatOrNull() ?: 0f); px += (f[2].toFloatOrNull() ?: 0f)
                        val r = f.getOrNull(3)?.toFloatOrNull() ?: 1f; val g = f.getOrNull(4)?.toFloatOrNull() ?: 1f; val b = f.getOrNull(5)?.toFloatOrNull() ?: 1f
                        maxC = maxOf(maxC, r, g, b)
                        col += (0xFF shl 24) or ((r * 255).toInt().coerceIn(0, 255) shl 16) or ((g * 255).toInt().coerceIn(0, 255) shl 8) or (b * 255).toInt().coerceIn(0, 255)
                    }
                }
                line.startsWith("vt ") -> {
                    val f = line.substring(3).trim().split(Regex("\\s+"))
                    uvs += f.getOrNull(0)?.toFloatOrNull() ?: 0f; uvs += f.getOrNull(1)?.toFloatOrNull() ?: 0f
                }
            }
        }
        var n = px.size / 3
        n -= n % 3
        if (n < 3 || n > 30_000) return null
        val positions = FloatArray(n * 3) { px[it] }
        val uv = FloatArray(n * 2) { uvs.getOrElse(it) { 0f } }
        // PS2 colours use 0x80 as full brightness: doubled when none goes above it (as the reader does).
        val dbl = maxC in 0.01f..0.51f
        val colors = IntArray(n) { i ->
            val c = col.getOrElse(i) { -1 }
            if (!dbl) c else {
                val r = minOf(255, ((c shr 16) and 0xFF) * 2); val g = minOf(255, ((c shr 8) and 0xFF) * 2); val b = minOf(255, (c and 0xFF) * 2)
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        // The texture: the exporter saved it upside down (row 0 at the bottom).
        val tex = png?.let { bytes ->
            runCatching {
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
                val s = if (bmp.width != 128 || bmp.height != 128) android.graphics.Bitmap.createScaledBitmap(bmp, 128, 128, true) else bmp
                val out = IntArray(128 * 128)
                val row = IntArray(128)
                for (y in 0 until 128) {
                    s.getPixels(row, 0, 128, 0, 127 - y, 128, 1)
                    for (x in 0 until 128) out[y * 128 + x] = row[x] or (0xFF shl 24)
                }
                out
            }.getOrNull()
        }
        val anim = animJson?.let { runCatching { anim(String(it, Charsets.UTF_8), n * 3) }.getOrNull() }
        return Ps2Icons.Icon(1, if (tex != null) 7 else 3, n, positions, uv, colors, tex, anim?.frameShapes?.size ?: 1, anim)
    }

    /** The .anim (JSON): frameLength, animSpeed, frames of keys and vertex positions (the console's own axes). */
    private fun anim(json: String, len: Int): Ps2Icons.Anim? {
        val j = org.json.JSONObject(json)
        // (Version 1 files used another timing — those icons stay still rather than play wrongly.)
        if (j.optInt("version", 0) < 2) return null
        val frames = j.optJSONArray("frames") ?: return null
        if (frames.length() < 2) return null
        val shapes = ArrayList<FloatArray>(); val keys = ArrayList<FloatArray>()
        for (f in 0 until minOf(frames.length(), 64)) {
            val fr = frames.getJSONObject(f)
            val vd = fr.optJSONArray("vertexData") ?: continue
            val v = FloatArray(len) { i -> if (i < vd.length()) vd.optDouble(i, 0.0).toFloat() else 0f }
            val ks = fr.optJSONArray("keys") ?: continue
            val k = FloatArray(ks.length() * 2)
            for (i in 0 until ks.length()) { val o = ks.getJSONObject(i); k[i * 2] = o.optDouble("time", 0.0).toFloat(); k[i * 2 + 1] = o.optDouble("value", 0.0).toFloat() }
            if (k.isEmpty()) continue
            shapes += v; keys += k
        }
        if (shapes.size < 2) return null
        return Ps2Icons.Anim(j.optInt("frameLength", 60).coerceAtLeast(1), j.optDouble("animSpeed", 1.0).toFloat().takeIf { it > 0f } ?: 1f,
            shapes.toTypedArray(), keys.toTypedArray())
    }
}
