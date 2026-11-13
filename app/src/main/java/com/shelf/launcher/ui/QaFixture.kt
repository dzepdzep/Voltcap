package com.shelf.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.mutableStateOf
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.GameArt
import java.io.File

/**
 * Update 474 — QA test content, display-only (like [DemoLibrary]): `--es voltcap_test_fixture "names=long,art=wide,meta=none"`
 * (only with the QA hooks file; `--es voltcap_test_fixture off` or a restart ends it). The same games stay in the same places
 * (keys, consoles, order, focus); only how they are DRAWN changes, so nothing is written: no ROM, artwork, category, metadata
 * or setting is touched, and the drawn pictures live in cache/qa_fixture_art (deleted when it goes off).
 *   names = real | long (very long, with Unicode) | short (three letters) — both keep the real names' alphabetical order
 *   art   = real | none | wide (16:9) | square | portrait (2:3) | ultrawide (3:1) | lowres (64 × 96, shown much bigger)
 *           | covers (Sprint 528: pictures QA pushed to the QA app's own files/qa_covers, matched by game name)
 *   meta  = real | none (no description, maker, genre, year, achievements, screenshots, clips)
 * Every drawn picture carries its own shape in words ("QA 16:9 …") so a screenshot shows which fixture is on.
 */
internal object QaFixture {
    data class Spec(val names: String = "real", val art: String = "real", val meta: String = "real") {
        val text get() = "names=$names,art=$art,meta=$meta"
    }

    val spec = mutableStateOf<Spec?>(null)
    val artDir = mutableStateOf<File?>(null)

    fun parse(s: String): Spec? {
        if (s.isBlank() || s == "off") return null
        val m = s.split(',').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
        return Spec(m["names"] ?: "real", m["art"] ?: "real", m["meta"] ?: "real")
    }

    private val LONG = listOf(
        "The Extraordinarily Long Chronicle of the Forgotten Lighthouse Keeper and the Seven Clockwork Islands — Director’s Cut",
        "Ünïcödé Ädventure: Reise durch die Überflüssig Langen Titelzeilen (Special Edition) 完全版",
        "SuperUltraMegaCompoundWordWithoutAnySpacesThatNeverEndsAndMustWrapSomehow",
        "Pocket Monsters: The Very Long Subtitle That Keeps Going Past Every Reasonable Width"
    )

    /** A three-letter code per alphabetical rank (Aaa, Aab, …): it sorts like the real names, so a layout or list that sorts
     *  by name keeps every game where it was (the dressed long names once opened another game's window: k474f). */
    fun code(rank: Int): String {
        val a = rank / 676 % 26; val b = rank / 26 % 26; val c = rank % 26
        return "${'A' + a}${'a' + b}${'a' + c}"
    }

    fun name(rank: Int, real: String, mode: String): String = when (mode) {
        "long" -> code(rank) + " · " + LONG[rank % LONG.size]
        "short" -> code(rank)
        else -> real
    }

    /** stableKey → dressed name, by the real names' case-insensitive order (as DemoLibrary.names). */
    fun names(games: List<com.shelf.launcher.data.Game>, mode: String): Map<String, String> {
        val order = games.indices.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { games[it].name })
        val out = HashMap<String, String>(games.size)
        order.forEachIndexed { rank, i -> out[games[i].stableKey] = name(rank, games[i].name, mode) }
        return out
    }

    fun shape(mode: String): Pair<Int, Int>? = when (mode) {
        "wide" -> 960 to 540; "square" -> 720 to 720; "portrait" -> 600 to 900
        "ultrawide" -> 1200 to 400; "lowres" -> 64 to 96; else -> null
    }

    const val SLOTS = 24

    /** Draws the pictures for [mode] once ([SLOTS] of them, numbered, hues far apart) into cache/qa_fixture_art/<mode>. */
    fun prepare(context: Context, mode: String, done: (File?) -> Unit) {
        if (mode == "covers") return prepareCovers(context, done)
        val wh = shape(mode) ?: return done(null)
        Thread {
            val dir = File(context.cacheDir, "qa_fixture_art/$mode").apply { mkdirs() }
            runCatching {
                for (i in 0 until SLOTS) {
                    val f = File(dir, "a_$i.png")
                    if (!f.exists()) {
                        val b = Bitmap.createBitmap(wh.first, wh.second, Bitmap.Config.ARGB_8888)
                        val c = Canvas(b)
                        c.drawColor(Color.HSVToColor(floatArrayOf((i * 137.5f) % 360f, 0.55f, if (i % 2 == 0) 0.75f else 0.55f)))
                        val p = Paint(Paint.ANTI_ALIAS_FLAG)
                        // A frame 4 % in from every edge: a crop shows as a missing frame side, a stretch as uneven circles.
                        p.style = Paint.Style.STROKE; p.strokeWidth = maxOf(2f, wh.first * 0.02f); p.color = Color.WHITE
                        val m = wh.first * 0.04f
                        c.drawRect(m, m, wh.first - m, wh.second - m, p)
                        val r = minOf(wh.first, wh.second) * 0.22f
                        c.drawCircle(wh.first / 2f, wh.second / 2f, r, p)
                        p.style = Paint.Style.FILL; p.textSize = maxOf(10f, wh.second * 0.08f); p.textAlign = Paint.Align.CENTER
                        c.drawText("QA ${mode.uppercase()} #$i", wh.first / 2f, wh.second * 0.88f, p)
                        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
                    }
                }
            }
            done(dir)
        }.start()
    }

    /**
     * Sprint 528 (owner: verify with representative real covers): `art=covers` - pictures pushed by QA into the QA app's OWN
     * external files/qa_covers/ (never read from the real Voltcap, never in the repository), matched to a game by its name
     * (letters and digits, any case). A game without one gets no picture, so the fallbacks show as they really would.
     */
    private fun prepareCovers(context: Context, done: (File?) -> Unit) {
        Thread {
            val src = context.getExternalFilesDir("qa_covers")
            val dir = File(context.cacheDir, "qa_fixture_art/covers").apply { deleteRecursively(); mkdirs() }
            runCatching {
                src?.listFiles()?.filter { it.isFile && it.length() > 0 }?.forEach { f -> f.copyTo(File(dir, "n_" + coverKey(f.nameWithoutExtension) + ".img"), overwrite = true) }
            }
            done(dir)
        }.start()
    }

    /** A game's or a cover file's name as the match key (letters and digits, lower case). Pure. */
    fun coverKey(name: String): String = name.substringBefore(" (").lowercase().filter { it.isLetterOrDigit() }

    fun clear(context: Context) { runCatching { File(context.cacheDir, "qa_fixture_art").deleteRecursively() } }

    // (The renamed list and the drawn art are kept while the real list is the same object — a new list on every state change
    // made layouts that key on it start over, as DemoLibrary found; everything else is taken from the live state each time.)
    private var memoIn: List<com.shelf.launcher.data.Game>? = null
    private var memoSpec: Spec? = null
    private var memoDir: File? = null
    private var memoGames: List<com.shelf.launcher.data.Game> = emptyList()
    private var memoArt: Map<String, GameArt>? = null
    private var memoNoScans: Map<String, List<Pair<String, String>>> = emptyMap()

    fun dress(s: LauncherUiState): LauncherUiState {
        val sp = spec.value ?: return s
        val dir = artDir.value
        if (memoIn !== s.games || memoSpec != sp || memoDir != dir) {
            val keys = s.games.map { it.stableKey }
            memoGames = if (sp.names == "real") s.games else names(s.games, sp.names).let { m -> s.games.map { g -> g.copy(name = m[g.stableKey] ?: g.name) } }
            memoArt = when {
                sp.art == "real" -> null
                sp.art == "none" -> emptyMap()
                dir == null -> null
                sp.art == "covers" -> s.games.mapNotNull { g ->
                    val f = File(dir, "n_" + coverKey(g.name) + ".img")
                    if (f.exists()) g.stableKey to "file://${f.absolutePath}".let { u -> GameArt(gridUrl = u, wideGridUrl = null, heroUrl = null) } else g.stableKey to GameArt(gridUrl = null, heroUrl = null)
                }.toMap()
                // (Numbered by the games' alphabetical rank, over SLOTS pictures: neighbours in any name-sorted list — a console
                // or All — get different pictures. Numbered by list position over 8, games 8 apart sat side by side, k474p.)
                else -> run {
                    val rank = s.games.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }).withIndex().associate { (r, g) -> g.stableKey to r }
                    s.games.associate { g ->
                        val u = "file://" + File(dir, "a_${(rank[g.stableKey] ?: 0) % SLOTS}.png").absolutePath
                        g.stableKey to GameArt(gridUrl = u, wideGridUrl = u, heroUrl = u)
                    }
                }
            }
            memoNoScans = keys.associateWith { emptyList<Pair<String, String>>() }
            memoIn = s.games; memoSpec = sp; memoDir = dir
        }
        var out = s.copy(games = memoGames)
        memoArt?.let { a ->
            out = out.copy(art = a, artOverrides = emptyMap(), screenshots = emptyMap(), videoUrls = emptyMap(),
                physicalMedia = memoNoScans, sgdbLogos = emptyMap())
        }
        if (sp.meta == "none") out = out.copy(metadata = emptyMap(), screenshots = emptyMap(), raProgress = emptyMap(),
            raAchievementDetails = emptyMap(), raCompletionById = emptyMap(), metadataNotes = emptyMap(), detailCandidates = emptyMap(),
            videoUrls = emptyMap())
        return out
    }
}
