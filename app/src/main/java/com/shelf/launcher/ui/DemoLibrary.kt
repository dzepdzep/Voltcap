package com.shelf.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.runtime.mutableStateOf
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.GameArt
import java.io.File

/*
 * Update 423 (layout preview pictures): a display-only stand-in library for screenshots that go into the app and onto
 * GitHub. `adb shell am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_demo true|false` (only with the QA hooks
 * file). Nothing is written or changed: the same games stay in the same places (keys, consoles, order, focus), only how
 * they are DRAWN changes — made-up names, no artwork, scans, screenshots, clips, descriptions, achievements or account
 * picture — so no game's box art or anyone's account ends up in a committed picture. Off again at the next start.
 * RetroAchievements / Steam / SteamGridDB ids are dropped too (their icons are real covers), and every game wears a cover
 * drawn here ([DemoArt]: abstract shapes + the made-up name, Voltcap's own drawing) so the layouts look like a real library.
 */
internal object DemoLibrary {
    val on = mutableStateOf(false)

    private val first = listOf("Basalt", "Coral", "Dungeon", "Ember", "Glacier", "Hollow", "Crimson", "Velvet", "Copper", "Lantern",
        "Neon", "Moonlit", "Echo", "Harbor", "Ironleaf", "Frostline", "Glimmer", "Pixel", "Rocket", "Shadow", "Skyforge", "Thunder",
        "Turbo", "Clockwork", "Paper", "Pocket", "Starling", "Tidal", "Mint", "Nova")
    private val second = listOf("Brawlers", "Reef Racer", "Bloom", "Chronicle", "Rally", "Garden", "Tide Racer", "Circuit", "Fox",
        "Bay", "Drift 2049", "Kart", "Odyssey", "Hopper", "Tactics", "Quest", "Knight Saga", "Otter", "Legends", "Lagoon", "Gull",
        "Ruins", "Isle", "Tunnel", "Comet", "Meadow", "Arcade", "Orchard", "Sprint", "Academy")

    /**
     * Made-up names in A–Z order, as many as needed (each first word × second word, then " II", " III"…). They are handed
     * out in the order of the real names, so a list sorted by name keeps exactly the same order (the view model's focus and
     * the drawn list stay the same game).
     */
    private val pool: List<String> by lazy {
        val base = first.flatMap { a -> second.map { b -> "$a $b" } }
        (listOf("") + listOf(" II", " III", " IV", " V", " VI", " VII", " VIII")).flatMap { suf -> base.map { it + suf } }.sorted()
    }

    fun names(games: List<com.shelf.launcher.data.Game>): Map<String, String> {
        val order = games.indices.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { games[it].name })
        val out = HashMap<String, String>(games.size)
        order.forEachIndexed { rank, i -> out[games[i].stableKey] = pool[rank % pool.size] }
        return out
    }

    /** Folder of drawn covers once [DemoArt.prepare] finished (null while drawing / never). */
    val artDir = mutableStateOf<File?>(null)

    // The dressed games and art, kept while the real list is the same object: a new list on every state change (the clock,
    // a scan tick) made layouts that key on the list (the Cardboard box's sliced reveal, the shelf) start over every time and
    // never finish drawing.
    private var memoIn: List<com.shelf.launcher.data.Game>? = null
    private var memoDir: File? = null
    private var memoGames: List<com.shelf.launcher.data.Game> = emptyList()
    private var memoArt: Map<String, GameArt> = emptyMap()
    private var memoNoScans: Map<String, List<Pair<String, String>>> = emptyMap()

    fun dress(s: LauncherUiState): LauncherUiState {
        val dir = artDir.value
        if (memoIn !== s.games || memoDir != dir) {
            val m = names(s.games)
            memoGames = s.games.map { g -> g.copy(name = m[g.stableKey] ?: g.name, raGameId = null, sgdbGameId = null, steamAppId = null) }
            memoArt = if (dir == null) emptyMap() else s.games.associate { g ->
                val n = DemoArt.slot(g.stableKey)
                val wide = "file://" + File(dir, "w_${n % DemoArt.WIDE}.jpg").absolutePath
                g.stableKey to GameArt(gridUrl = "file://" + File(dir, "c2_$n.jpg").absolutePath, wideGridUrl = wide, heroUrl = wide)
            }
            // "Looked up, has no scans" (not "not looked up yet"): cases and spines are drawn from the console's own spine
            // at once instead of waiting forever for a lookup whose answer this stand-in hides (the Cardboard box stayed empty).
            memoNoScans = s.games.associate { it.stableKey to emptyList<Pair<String, String>>() }
            memoIn = s.games; memoDir = dir
        }
        return dressNames(s, memoGames, memoArt, memoNoScans)
    }

    private fun dressNames(s: LauncherUiState, games: List<com.shelf.launcher.data.Game>, art: Map<String, GameArt>, noScans: Map<String, List<Pair<String, String>>>): LauncherUiState = s.copy(
        games = games,
        art = art, artOverrides = emptyMap(), screenshots = emptyMap(), physicalMedia = noScans, videoUrls = emptyMap(),
        metadata = emptyMap(), sgdbLogos = emptyMap(), raProfile = null, raAchievementDetails = emptyMap(), raProgress = emptyMap(), raUsername = null,
        raIcons = emptyMap(), raCompletionById = emptyMap(), metadataNotes = emptyMap(), raGameTips = emptyMap(), raComments = emptyMap(),
        detailCandidates = emptyMap()
    )
}

/**
 * Update 423: the demo library's covers, drawn once into the app's cache (cache/demo_art; deleted when demo mode goes off).
 * A game wears the cover of its slot ([COVERS] designs, so two games can share one); every design is Voltcap's own
 * (gradients, hills, rings, stripes, stars, grids) and carries no words, like the wide pictures (heroes / banners).
 */
internal object DemoArt {
    const val COVERS = 160
    const val WIDE = 16

    fun slot(key: String): Int = Math.floorMod(key.hashCode() * 31 + 7, COVERS)

    fun prepare(context: Context, done: (File) -> Unit) {
        Thread {
            val dir = File(context.cacheDir, "demo_art").apply { mkdirs() }
            runCatching {
                for (i in 0 until COVERS) {
                    val f = File(dir, "c2_$i.jpg"); if (!f.exists()) save(cover(i), f)
                }
                for (i in 0 until WIDE) {
                    val f = File(dir, "w_$i.jpg"); if (!f.exists()) save(wide(i), f)
                }
            }
            done(dir)
        }.start()
    }

    fun clear(context: Context) { runCatching { File(context.cacheDir, "demo_art").deleteRecursively() } }

    private fun save(b: Bitmap, f: File) { f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 86, it) }; b.recycle() }

    private fun hsv(h: Float, s: Float, v: Float) = android.graphics.Color.HSVToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s, v))

    private fun scene(c: Canvas, w: Float, h: Float, i: Int) {
        val hue = (i * 47 % 360).toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h, hsv(hue, 0.55f, 0.85f), hsv(hue + 40f, 0.75f, 0.28f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
        val r = java.util.Random(i * 7919L)
        when (i % 6) {
            0 -> { // layered hills under a sun
                p.color = hsv(hue + 180f, 0.35f, 1f); c.drawCircle(w * 0.68f, h * 0.42f, w * 0.16f, p)
                for (k in 0 until 4) {
                    val path = Path(); val base = h * (0.55f + k * 0.11f)
                    path.moveTo(0f, h); path.lineTo(0f, base)
                    var x = 0f; while (x <= w) { path.lineTo(x, base - r.nextFloat() * h * 0.12f); x += w / 6f }
                    path.lineTo(w, h); path.close()
                    p.color = hsv(hue + 20f * k, 0.6f, 0.55f - k * 0.1f); c.drawPath(path, p)
                }
            }
            1 -> { // rings
                p.style = Paint.Style.STROKE
                for (k in 1..7) { p.strokeWidth = w * 0.03f; p.color = hsv(hue + k * 25f, 0.6f, 1f - k * 0.08f); c.drawCircle(w * 0.5f, h * 0.6f, w * 0.07f * k, p) }
                p.style = Paint.Style.FILL
            }
            2 -> { // diagonal stripes
                p.strokeWidth = w * 0.09f
                for (k in -4..8) { p.color = hsv(hue + k * 12f, 0.7f, if (k % 2 == 0) 0.95f else 0.6f); c.drawLine(k * w * 0.22f, h, k * w * 0.22f + h * 0.6f, h * 0.35f, p) }
            }
            3 -> { // stars and a planet
                p.color = android.graphics.Color.WHITE
                repeat(70) { c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, r.nextFloat() * w * 0.006f + 0.6f, p) }
                p.shader = RadialGradient(w * 0.42f, h * 0.55f, w * 0.3f, hsv(hue + 160f, 0.5f, 1f), hsv(hue + 200f, 0.8f, 0.35f), Shader.TileMode.CLAMP)
                c.drawCircle(w * 0.5f, h * 0.62f, w * 0.28f, p); p.shader = null
            }
            4 -> { // grid floor
                p.color = hsv(hue + 300f, 0.7f, 1f); p.strokeWidth = w * 0.006f
                val hz = h * 0.58f
                for (k in 0..10) { val y = hz + (h - hz) * (k / 10f) * (k / 10f); c.drawLine(0f, y, w, y, p) }
                for (k in -8..8) c.drawLine(w / 2f + k * w * 0.04f, hz, w / 2f + k * w * 0.25f, h, p)
                p.color = hsv(hue + 40f, 0.7f, 1f); c.drawCircle(w / 2f, hz, w * 0.18f, p)
                p.color = hsv(hue, 0.6f, 0.3f); c.drawRect(0f, hz, w, hz + h * 0.01f, p)
            }
            else -> { // shapes
                repeat(7) {
                    p.color = hsv(hue + r.nextInt(120), 0.6f, 0.6f + r.nextFloat() * 0.4f); p.alpha = 200
                    val cx = r.nextFloat() * w; val cy = h * 0.3f + r.nextFloat() * h * 0.7f; val s = w * (0.1f + r.nextFloat() * 0.25f)
                    if (it % 2 == 0) c.drawCircle(cx, cy, s, p) else c.drawRect(cx - s, cy - s, cx + s, cy + s, p)
                }
                p.alpha = 255
            }
        }
    }

    private fun cover(i: Int): Bitmap {
        val w = 300; val h = 420
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(b)
        scene(c, w.toFloat(), h.toFloat(), i)
        // (No words on the drawn covers: a made-up title on a cover read as a different game from the name under it.)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = 0x33FFFFFF })
        return b
    }

    private fun wide(i: Int): Bitmap {
        val w = 960; val h = 540
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        scene(Canvas(b), w.toFloat(), h.toFloat(), i * 3 + 1)
        return b
    }
}
