package com.shelf.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Update 462 (owner: "the generated carts look computer-drawn next to the real Silver cartridge photo"): a drawn cartridge
 * front made from a REAL cartridge photo of the same shell — taken from your own library's scans (ScreenScraper's
 * "Disc / cartridge" picture of an official game you have, e.g. Pokémon Silver's), never a bundled picture. The photo
 * keeps the real plastic, the moulded Nintendo GAME BOY lettering, the grip ridges, the light and the edges; the sticker
 * recess is found in it and the game's own label (your pick › its real label scan › your Labels folder › the printed label
 * from its artwork — exactly what the drawn shell would print, CartShells.front) is laid into the recess over the photo's
 * sticker, which is covered whole. The plastic is tinted to the shell colour you picked. The turned piece's sides come from
 * this front (sideArtOf), so they are the photo's plastic too; the back stays the drawn one in the same colour.
 *
 * No photo of that shell in the library (or it doesn't fit: another shape, an odd crop): the drawn shell, as before.
 */
internal object PhotoShells {
    /** A shell's template: the photo cropped to the shell and scaled to its shape, where its sticker is, its plastic. */
    class Template(val bmp: Bitmap, val label: RectF, val plastic: Int, val source: String)

    /** Which shells can be photo-made: the shell id → the ScreenScraper system ids whose cartridge photos show it. */
    private val SYSTEMS = mapOf("gb" to setOf("9", "10"), "gbc_dual" to setOf("10"), "gbc" to setOf("10"), "gba" to setOf("12"))

    fun supports(shellId: String): Boolean = shellId in SYSTEMS

    /** Official games' cartridge photos (game key → photo) by ScreenScraper system id (set by the view model with the scans). */
    @Volatile var photos: Map<String, List<Pair<String, String>>> = emptyMap()

    /** Bumped when a template is made, so the cartridges drawn before draw again. */
    val version = androidx.compose.runtime.mutableIntStateOf(0)

    private val templates = java.util.concurrent.ConcurrentHashMap<String, Template>()
    private val failed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val loading = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile var app: android.content.Context? = null

    private val SYS = Regex("""[?&]systemeid=(\d+)""")

    /** The cartridge photos of the official games in [raw] by system id (pure — unit-tested). */
    fun photosOf(raw: Map<String, List<Pair<String, String>>>, isHack: (String) -> Boolean): Map<String, List<Pair<String, String>>> {
        val out = HashMap<String, MutableList<Pair<String, String>>>()
        for ((key, list) in raw) {
            if (isHack(key)) continue
            val url = list.firstOrNull { it.first == "Disc / cartridge" }?.second ?: continue
            val sys = SYS.find(url)?.groupValues?.get(1) ?: continue
            if (SYSTEMS.values.none { sys in it }) continue
            out.getOrPut(sys) { mutableListOf() } += key to url
        }
        return out.mapValues { e -> e.value.distinctBy { it.second }.sortedBy { it.first } }
    }

    /** One analysed photo: where it's from, its plastic, whether it's see-through. */
    private class Meta(val url: String, val plastic: Int, val clear: Boolean)
    private val metas = java.util.concurrent.ConcurrentHashMap<String, List<Meta>>()

    /**
     * The template for [shell] in [colour] once made (null: none / still being made — the drawn shell is used meanwhile).
     * The photo whose plastic is nearest that colour (and see-through for a see-through one): a black dual-mode pak's
     * photo can't be tinted to Silver's plastic, Silver's own can.
     */
    fun template(shell: CartShell, colour: ShellColour = shell.colours.first()): Template? {
        if (!com.shelf.launcher.data.Features.photoShells.value || !supports(shell.id)) return null
        val list = metas[shell.id]
        if (list == null) { analyse(shell); return null }
        val clear = colour.opacity < 0.85f
        val pick = list.filter { it.clear == clear }.minByOrNull { colourDistance(it.plastic, colour.argb) }
            // (A see-through pak's plastic is mostly the board behind it: any clear photo will do, the nearest first.)
            ?.takeIf { clear || colourDistance(it.plastic, colour.argb) < 120f } ?: return null
        val key = shell.id + "|" + pick.url
        templates[key]?.let { return it }
        if (key in failed || !loading.add(key)) return null
        val ctx = app ?: return null
        Thread {
            try {
                val src = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { load(ctx, pick.url) }
                val t = src?.let { fromPhoto(it, shell, pick.url) }
                src?.recycle()
                if (t != null) { templates[key] = t; android.os.Handler(android.os.Looper.getMainLooper()).post { version.intValue++ } } else failed += key
                QaTrace.event("photoshell", "${shell.id} ${colour.key} ${if (t != null) "jeu " + JEU.find(pick.url)?.groupValues?.get(1) else "none"}")
            } finally { loading.remove(key) }
        }.apply { priority = Thread.MIN_PRIORITY; name = "photoshell" }.start()
        return null
    }

    /**
     * Where the sticker is on a real pak's photo when it isn't where the drawn shell has it (mm): the Color-only pak's sits
     * lower, under its taller GAME BOY COLOR panel (measured off two cartridge photos, Crystalis and Crystal: 14 % in from
     * either side, 34.5 % to 92 % down).
     */
    private val STICKER = mapOf("gbc" to floatArrayOf(8.0f, 22.6f, 42.4f, 37.7f))

    private val JEU = Regex("""[?&]jeuid=(\d+)""")

    /** Edition plastics people have (ShellMaterials): their games' photos are looked at first. */
    private val EDITION_WORDS = listOf("silver", "gold", "crystal", "yellow")

    /** Which pak [key]'s game came on (ScreenScraper system [sys]): the ROM header, else the file name's tag. */
    fun shellOfGame(key: String, sys: String): String? = when (sys) {
        "12" -> "gba"
        "9" -> "gb"
        "10" -> when (com.shelf.launcher.data.RomHeaders.gb(key, "GBC")) {
            com.shelf.launcher.data.RomHeaders.Gb.CGB_DUAL -> "gbc_dual"
            com.shelf.launcher.data.RomHeaders.Gb.CGB_ONLY -> "gbc"
            com.shelf.launcher.data.RomHeaders.Gb.DMG -> "gb"
            null -> if (key.contains("GB Compatible", ignoreCase = true)) "gbc_dual" else "gbc"
        }
        else -> null
    }

    /** Every photo of [shell]'s consoles looked at once (small): its plastic and see-through-ness, kept as [metas]. */
    private fun analyse(shell: CartShell) {
        val ctx = app ?: return
        // Only games that came on this very shell (the ROM header: grey Game Boy, black dual-mode or clear Color-only pak),
        // an edition's own game first (Pokémon Silver's photo for the silver pak).
        val urls = SYSTEMS[shell.id]!!.flatMap { sys -> photos[sys].orEmpty().filter { (k, _) -> shellOfGame(k, sys) == shell.id } }
            .sortedByDescending { (k, _) -> EDITION_WORDS.count { w -> k.contains(w, ignoreCase = true) } }
            .map { it.second }
        if (urls.isEmpty()) { metas[shell.id] = emptyList(); return }
        if (!loading.add("meta|" + shell.id)) return
        Thread {
            try {
                val out = ArrayList<Meta>()
                for (u in urls.take(24)) {
                    val src = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { load(ctx, u, 320) } ?: continue
                    val t = fromPhoto(src, shell, u, px = 240)
                    src.recycle()
                    if (t == null) continue
                    out += Meta(u, t.plastic, t.source.endsWith("#clear"))
                    t.bmp.recycle()
                }
                metas[shell.id] = out
                QaTrace.event("photoshell", "${shell.id} ${out.size} of ${urls.size} photos fit, ${out.count { it.clear }} see-through")
                android.os.Handler(android.os.Looper.getMainLooper()).post { version.intValue++ }
            } finally { loading.remove("meta|" + shell.id) }
        }.apply { priority = Thread.MIN_PRIORITY; name = "photoshell" }.start()
    }

    /** Forget the templates (a new library / its scans changed). */
    fun reset() { templates.clear(); metas.clear(); failed.clear(); version.intValue++ }

    // (Not kept in the picture memory: a shell's photos are looked at once — kept there, 96 photos held ~330 MB on the
    // KONKR. The disk cache still has them.)
    private suspend fun load(ctx: android.content.Context, url: String, side: Int = 900): Bitmap? = runCatching {
        val req = coil.request.ImageRequest.Builder(ctx).data(url).size(side, side).allowHardware(false)
            .memoryCachePolicy(coil.request.CachePolicy.DISABLED).build()
        val d = (coil.Coil.imageLoader(ctx).execute(req) as? coil.request.SuccessResult)?.drawable ?: return null
        (d as? android.graphics.drawable.BitmapDrawable)?.bitmap
    }.getOrNull()

    /** RGB distance (0…441). */
    fun colourDistance(a: Int, b: Int): Float {
        val dr = ((a shr 16) and 255) - ((b shr 16) and 255); val dg = ((a shr 8) and 255) - ((b shr 8) and 255); val db = (a and 255) - (b and 255)
        return kotlin.math.sqrt((dr * dr + dg * dg + db * db).toFloat())
    }

    /**
     * Pure apart from Bitmap: [src] cut to the cartridge (its see-through surroundings, or the flat colour round it),
     * checked against [shell]'s shape (±9 %), scaled to it, its sticker found near where the shell has it.
     */
    fun fromPhoto(src: Bitmap, shell: CartShell, source: String, px: Int = 720): Template? {
        val w0 = src.width; val h0 = src.height
        if (w0 < 60 || h0 < 60) return null
        val pix = IntArray(w0 * h0); src.getPixels(pix, 0, w0, 0, 0, w0, h0)
        val bg = pix[0]
        val alphaBg = (bg ushr 24) < 40
        fun isPiece(c: Int): Boolean = if (alphaBg) (c ushr 24) > 60 else colourDistance(c, bg) > 38f
        var l = w0; var t = h0; var r = -1; var b = -1
        for (y in 0 until h0) for (x in 0 until w0) if (isPiece(pix[y * w0 + x])) { if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y }
        if (r - l < w0 * 0.4f || b - t < h0 * 0.4f) return null
        val bw = r - l + 1; val bh = b - t + 1
        val aspect = shell.w / shell.h
        if (kotlin.math.abs(bw.toFloat() / bh / aspect - 1f) > 0.09f) return null
        val tw = if (aspect >= 1f) px else (px * aspect).toInt()
        val th = (tw / aspect).toInt()
        val out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        c.drawBitmap(src, android.graphics.Rect(l, t, r + 1, b + 1), android.graphics.Rect(0, 0, tw, th), paint)
        if (!alphaBg) {
            // A photo on a flat background: the background made see-through (the outline is the shell's).
            val o = IntArray(tw * th); out.getPixels(o, 0, tw, 0, 0, tw, th)
            for (i in o.indices) if (colourDistance(o[i], bg) <= 38f) o[i] = 0
            out.setPixels(o, 0, tw, 0, 0, tw, th)
        }
        val k = tw / shell.w
        val lb = STICKER[shell.id] ?: shell.label
        val est = RectF(lb[0] * k, lb[1] * k, (lb[0] + lb[2]) * k, (lb[1] + lb[3]) * k)
        val plastic = plasticOf(out, est)
        val clear = boardShows(out, est, plastic)
        // (Through a see-through pak the board is everywhere round the sticker: its measured place is used, not looked for.)
        val label = if (clear) est else findSticker(out, est, plastic)
        return Template(out, label, plastic, if (clear) "$source#clear" else source)
    }

    private fun lum(c: Int): Int = (((c shr 16) and 255) * 3 + ((c shr 8) and 255) * 6 + (c and 255)) / 10

    /**
     * The sticker's edges: from inside the sticker outwards, the first line (column / row) where most samples are the
     * plastic's colour — so a frame printed inside the label (its art's border, "THIS SIDE OUT") is passed over.
     */
    fun findSticker(b: Bitmap, est: RectF, plastic: Int): RectF {
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        fun plasticAt(x: Int, y: Int): Boolean {
            if (x !in 0 until w || y !in 0 until h) return true
            val c = px[y * w + x]
            return (c ushr 24) < 120 || colourDistance(c, plastic) < 30f
        }
        val reach = (maxOf(w, h) * 0.09f).toInt().coerceAtLeast(3)
        // A line counts as plastic when 70 % of its samples are (the recess's shadow and a sticker's worn edge aside).
        fun columnPlastic(x: Int): Boolean { var n = 0; var p = 0; var y = (est.top + est.height() * 0.15f).toInt(); while (y < est.bottom - est.height() * 0.15f) { n++; if (plasticAt(x, y)) p++; y += 3 }; return n > 0 && p >= n * 0.7f }
        fun rowPlastic(y: Int): Boolean { var n = 0; var p = 0; var x = (est.left + est.width() * 0.15f).toInt(); while (x < est.right - est.width() * 0.15f) { n++; if (plasticAt(x, y)) p++; x += 3 }; return n > 0 && p >= n * 0.7f }
        fun scan(from: Float, step: Int, test: (Int) -> Boolean): Float? {
            var v = from.toInt()
            repeat(reach * 2) { if (test(v)) return (v - step).toFloat(); v += step }
            return null
        }
        // From a little inside the expected edge (the sticker's own border may be plastic-coloured grey) outwards.
        val inset = minOf(est.width(), est.height()) * 0.12f
        val left = scan(est.left + inset, -1, ::columnPlastic) ?: est.left
        val right = scan(est.right - inset, 1, ::columnPlastic) ?: est.right
        val top = scan(est.top + inset, -1, ::rowPlastic) ?: est.top
        val bottom = scan(est.bottom - inset, 1, ::rowPlastic) ?: est.bottom
        val r = RectF(left, top, right, bottom)
        // An implausible find (the sticker more than 14 % off its expected size): the expected place.
        return if (r.width() < est.width() * 0.86f || r.width() > est.width() * 1.14f || r.height() < est.height() * 0.86f || r.height() > est.height() * 1.14f) est else r
    }

    /** The plastic's colour: the median of the bands either side of the sticker. */
    fun plasticOf(b: Bitmap, label: RectF): Int {
        val w = b.width; val h = b.height
        val rs = ArrayList<Int>(); val gs = ArrayList<Int>(); val bs = ArrayList<Int>()
        val y0 = (label.top + label.height() * 0.3f).toInt(); val y1 = (label.bottom - label.height() * 0.15f).toInt()
        // (The strips at the outer edges: the expected sticker may sit a little off in a photo.)
        val bands = listOf((w * 0.035f).toInt() to (minOf(label.left - w * 0.03f, w * 0.09f)).toInt(), (maxOf(label.right + w * 0.03f, w * 0.91f)).toInt() to (w * 0.965f).toInt())
        for ((x0, x1) in bands) for (y in y0 until y1 step 2) for (x in x0 until x1 step 2) {
            if (x !in 0 until w || y !in 0 until h) continue
            val c = b.getPixel(x, y)
            if ((c ushr 24) < 200) continue
            rs += (c shr 16) and 255; gs += (c shr 8) and 255; bs += c and 255
        }
        if (rs.isEmpty()) return 0xFF888888.toInt()
        rs.sort(); gs.sort(); bs.sort()
        return (0xFF shl 24) or (rs[rs.size / 2] shl 16) or (gs[gs.size / 2] shl 8) or bs[bs.size / 2]
    }

    /**
     * A see-through shell: the board shows round the sticker — many pixels far from the plastic's own colour below it and
     * beside it (a solid pak, even a gold or yellow one, is its one colour there).
     */
    private fun boardShows(b: Bitmap, label: RectF, plastic: Int): Boolean {
        var n = 0; var off = 0
        fun look(x0: Int, x1: Int, y0: Int, y1: Int) {
            for (y in y0 until y1 step 2) for (x in x0 until x1 step 2) {
                if (x !in 0 until b.width || y !in 0 until b.height) continue
                val c = b.getPixel(x, y); if ((c ushr 24) < 200) continue
                n++; if (colourDistance(c, plastic) > 45f) off++
            }
        }
        look((b.width * 0.12f).toInt(), (b.width * 0.88f).toInt(), (label.bottom + 3).toInt(), (b.height * 0.96f).toInt())
        look((b.width * 0.03f).toInt(), (label.left - 3).toInt(), (label.top + label.height() * 0.3f).toInt(), label.bottom.toInt())
        return n > 0 && off > n * 0.3f
    }

    /**
     * The photo front for [shell] wearing the label [drawn] has ([drawn] is CartShells.front's picture of the same shell at
     * the same size: its sticker is copied into the photo's recess), the plastic tinted to [colour]. Null: no template.
     */
    fun front(shell: CartShell, colour: ShellColour, drawn: Bitmap): Bitmap? {
        val t = template(shell, colour) ?: return null
        val w = drawn.width; val h = drawn.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        // The plastic in the colour picked: each channel scaled by want / photo (its texture and light kept).
        val want = colour.argb
        fun f(sh: Int) = (((want shr sh) and 255).toFloat() / ((t.plastic shr sh) and 255).coerceAtLeast(8)).coerceIn(0.35f, 2.2f)
        val tint = android.graphics.ColorMatrix(floatArrayOf(f(16), 0f, 0f, 0f, 0f, 0f, f(8), 0f, 0f, 0f, 0f, 0f, f(0), 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        // (A see-through pak isn't tinted: what shows through it is the board, not a plastic colour.)
        paint.colorFilter = if (colour.opacity >= 0.85f && colourDistance(want, t.plastic) > 10f) android.graphics.ColorMatrixColorFilter(tint) else null
        c.drawBitmap(t.bmp, null, android.graphics.Rect(0, 0, w, h), paint)
        paint.colorFilter = null
        // The sticker: the drawn one's, into the photo's recess (a little past its edge, so none of the photo's own shows).
        val sx = w.toFloat() / t.bmp.width; val sy = h.toFloat() / t.bmp.height
        val dst = RectF(t.label.left * sx, t.label.top * sy, t.label.right * sx, t.label.bottom * sy)
        val pad = w * 0.006f
        dst.inset(-pad, -pad)
        val k = w / shell.w
        val src = android.graphics.Rect((shell.label[0] * k).toInt(), (shell.label[1] * k).toInt(), ((shell.label[0] + shell.label[2]) * k).toInt(), ((shell.label[1] + shell.label[3]) * k).toInt())
        val rad = shell.labelRadius * k
        c.save()
        c.clipPath(android.graphics.Path().apply { addRoundRect(dst, rad, rad, android.graphics.Path.Direction.CW) })
        c.drawBitmap(drawn, src, dst, paint)
        // The paper sits in a recess: a soft shade under its top edge, the photo's light falling from above.
        val shade = Paint(Paint.ANTI_ALIAS_FLAG)
        shade.shader = android.graphics.LinearGradient(0f, dst.top, 0f, dst.top + 1.2f * k, 0x38000000, 0x00000000, android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(dst.left, dst.top, dst.right, dst.top + 1.2f * k, shade)
        c.restore()
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = maxOf(1f, 0.25f * k); color = 0x40000000 }
        c.drawRoundRect(dst, rad, rad, edge)
        return out
    }

    /** QA (test hooks): the templates as PNGs into <external files>/photoshells. */
    fun dump(ctx: android.content.Context) {
        val dir = java.io.File(ctx.getExternalFilesDir(null), "photoshells").apply { mkdirs() }
        for ((id0, t) in templates) runCatching {
            val id = id0.substringBefore('|') + "_" + (JEU.find(id0)?.groupValues?.get(1) ?: "x")
            java.io.File(dir, "$id.png").outputStream().use { t.bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            java.io.File(dir, "$id.txt").writeText("source=${t.source}\nlabel=${t.label}\nplastic=${Integer.toHexString(t.plastic)}\n")
        }
    }
}
