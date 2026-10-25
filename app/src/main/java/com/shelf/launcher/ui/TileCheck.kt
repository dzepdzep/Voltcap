package com.shelf.launcher.ui

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.Window
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Update 329: the R1 tile walk (test only — TestHooks, off without its flag file). For the layout and look on screen it
 * presses every R1 tile the panel shows, the way an L1 shortcut does (the panel opens and presses it), closes the panel
 * and compares both screens before and after; a tile that changed nothing on either screen is pressed once more (its
 * next value) before it is called "no effect". Everything a tile changed is put back after each tile (the previewed
 * appearance, the Features switches, the badge placement), and nothing of the appearance is saved (preview).
 *
 * Logged as `VoltcapTest tile layout=… look=… id=… main=… second=… noise=… verdict=EFFECT|NONE|…`; small pictures of
 * every NONE (before / after) go to `<external files>/tilecheck/` for a look by eye.
 */
internal object TileCheck {
    /** The tiles that belong to the home screen (layout / look); device and app tiles do the same everywhere. */
    val HOME_TILES = listOf(
        "gamelook", "badges", "outline", "cartlook", "discstyle", "casestyle", "spinestyle", "popupstyle", "twingap",
        "shelflook", "spotlight", "bartabs", "crosscut", "headertight", "pqtheme", "magnifier", "musicspot", "endless",
        "roundshort", "progressbar", "gamesize", "artdrop", "corners", "categories", "rowpos", "carousel", "background",
        "herofit", "sort", "edithome"
    )

    private const val W = 192
    private const val H = 108
    private const val PIXEL = 28
    /** Share of a screen's pixels that must change (outside the moving parts) to count as an effect: 0.15 %. */
    private const val EFFECT = 0.0015f

    class Shot(val main: IntArray?, val second: IntArray?)

    class Hooks(
        val activity: Activity,
        val vm: com.shelf.launcher.LauncherViewModel,
        /** Opens the R1 panel; with an id the panel presses that tile (R1Shortcut). */
        val openPanel: (String?) -> Unit,
        /** Closes the panel and every dialog / window / edit mode. */
        val closeAll: () -> Unit,
        /** Opens the game window of the game you're on. */
        val openDetails: () -> Unit,
        /** Sends a key to the home screen (down + up). */
        val key: (Int) -> Unit
    )

    private suspend fun grab(window: Window?): IntArray? {
        val w = window ?: return null
        val view = w.peekDecorView() ?: return null
        if (view.width <= 0 || view.height <= 0) return null
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val ok = suspendCancellableCoroutine { cont ->
            runCatching {
                PixelCopy.request(w, full, { r -> if (cont.isActive) cont.resume(r == PixelCopy.SUCCESS) }, Handler(Looper.getMainLooper()))
            }.onFailure { if (cont.isActive) cont.resume(false) }
        }
        if (!ok) { full.recycle(); return null }
        val small = Bitmap.createScaledBitmap(full, W, H, true)
        full.recycle()
        val px = IntArray(W * H)
        small.getPixels(px, 0, W, 0, 0, W, H)
        small.recycle()
        return px
    }

    suspend fun capture(h: Hooks): Shot = Shot(
        grab(h.activity.window),
        grab(com.shelf.launcher.data.perf.PerfRecorder.secondWindow?.takeIf { SecondScreenShown.value })
    )

    private fun differs(a: Int, b: Int): Boolean {
        val dr = kotlin.math.abs(((a shr 16) and 255) - ((b shr 16) and 255))
        val dg = kotlin.math.abs(((a shr 8) and 255) - ((b shr 8) and 255))
        val db = kotlin.math.abs((a and 255) - (b and 255))
        return maxOf(dr, dg, db) > PIXEL
    }

    /** Pixels that move by themselves (clock, clips, effects): changed between two shots of the same state, grown by 1. */
    private fun addNoise(a: IntArray?, b: IntArray?, mask: BooleanArray) {
        if (a == null || b == null) return
        for (y in 0 until H) for (x in 0 until W) {
            val i = y * W + x
            if (differs(a[i], b[i])) {
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx; val yy = y + dy
                    if (xx in 0 until W && yy in 0 until H) mask[yy * W + xx] = true
                }
            }
        }
    }

    private fun score(a: IntArray?, b: IntArray?, mask: BooleanArray): Float {
        if (a == null || b == null) return -1f
        var n = 0
        for (i in a.indices) if (!mask[i] && differs(a[i], b[i])) n++
        return n.toFloat() / a.size
    }

    private fun save(ctx: android.content.Context, name: String, px: IntArray?) {
        px ?: return
        runCatching {
            val dir = java.io.File(ctx.getExternalFilesDir(null), "tilecheck").apply { mkdirs() }
            val bmp = Bitmap.createBitmap(px, W, H, Bitmap.Config.ARGB_8888)
            java.io.FileOutputStream(java.io.File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
        }
    }

    private class Pair2(val a: Shot, val b: Shot)

    /** Two shots 400 ms apart (the second only for the noise mask). */
    private suspend fun twoShots(h: Hooks): Pair2 {
        val a = capture(h); delay(400); val b = capture(h)
        return Pair2(a, b)
    }

    /** Waits until the panel took the tile (or it was refused), at most [ms]. */
    private suspend fun pressed(ms: Long): Boolean {
        var t = 0L
        while (R1Shortcut.pending != null && t < ms) { delay(50); t += 50 }
        return R1Shortcut.pending == null
    }

    /**
     * Presses [id] through the panel ([steps] times, closing it after each), settles, and returns the shots. Custom
     * actions for the tiles whose effect isn't on the home screen as it stands (game window, Endless's wrap, Sort).
     */
    private suspend fun act(h: Hooks, id: String, steps: Int, a0: com.shelf.launcher.data.AppearanceSettings): Pair2 {
        when (id) {
            "artdrop" -> {
                // The tile opens the Art position adjuster; what it changes is where the cover sits in the case — a
                // step down and smaller (step 2: further), as moving it there would.
                val F = com.shelf.launcher.data.Features
                F.artDropCase.value = (F.artDropCase.value + 20 * steps).coerceIn(-50, 100)
                F.artZoomCase.value = (F.artZoomCase.value - 15 * steps).coerceIn(50, 200)
            }
            "sort" -> {
                // The tile opens the Sort window; what it changes is the order — the next sort, as a preview.
                val all = com.shelf.launcher.data.SortMode.values()
                var m = a0.sortMode
                repeat(steps) { m = all[(m.ordinal + 1) % all.size] }
                h.vm.previewAppearance(a0.copy(sortMode = m))
            }
            "endless" -> {
                // On (or off) — then the first game and Left: the wrap to the last game, or the bump.
                h.vm.previewAppearance(a0.copy(loopRow = if (steps == 1) !a0.loopRow else a0.loopRow))
                delay(600)
                h.vm.setFocus(0); delay(500)
                h.key(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
            }
            // The control: the panel opened and closed with nothing pressed — what closing it alone changes.
            "none" -> { h.openPanel(null); delay(1200); h.closeAll(); delay(250) }
            else -> repeat(steps) {
                h.openPanel(id)
                pressed(2500)
                delay(350)
                h.closeAll()
                delay(250)
            }
        }
        if (id == "popupstyle") { delay(700); h.openDetails(); delay(1600) } else delay(1300)
        return twoShots(h)
    }

    suspend fun walk(h: Hooks, only: Set<String>?) {
        val a = h.vm.uiState.value.appearance
        val tag = "layout=${a.homeLayout} look=${a.cardArtType}"
        TestHooks.fakeNowPlaying = com.shelf.launcher.data.NowPlaying("Tile walk", "Voltcap test", "com.shelf.launcher", isPlaying = true)
        h.vm.refreshNowPlaying()
        try { walkInner(h, only) } catch (e: Throwable) {
            Log.e("VoltcapTest", "tilewalk error $tag ${e.javaClass.simpleName}: ${e.message}")
            if (e is kotlinx.coroutines.CancellationException) throw e
        } finally {
            TestHooks.fakeNowPlaying = null
            h.vm.refreshNowPlaying()
            HomeIdle.holdForTest(false)
            Log.e("VoltcapTest", "tilewalk end $tag")
        }
    }

    private suspend fun walkInner(h: Hooks, only: Set<String>?) {
        val ctx = h.activity
        val a0 = h.vm.uiState.value.appearance
        val tag = "layout=${a0.homeLayout} look=${a0.cardArtType}"
        h.closeAll(); delay(600)
        // The tiles the panel shows here (QuickSettings logs them; greyed ones end in "~" and only explain themselves).
        TestHooks.lastTiles = null
        h.openPanel(null); delay(1600)
        val shown = TestHooks.lastTiles.orEmpty().split(',').filter { it.isNotBlank() && !it.endsWith("~") }
        h.closeAll(); delay(800)
        val tiles = listOf("none") + HOME_TILES.filter { it in shown && (only == null || it in only) }
        Log.e("VoltcapTest", "tilewalk start $tag tiles=${tiles.joinToString(",")}")
        val features0 = com.shelf.launcher.data.Features.snapshot()
        val badge0 = h.vm.uiState.value.achievementBadgeNextToTitle
        val theme0 = h.vm.uiState.value.appTheme
        HomeIdle.holdForTest(true)
        delay(1500)
        for (id in tiles) {
            HomeIdle.holdForTest(true)
            if (id == "edithome") {
                Log.e("VoltcapTest", "tile $tag id=edithome parts=${HomeWidgets.present.size} verdict=OPENS")
                continue
            }
            if (id == "musicspot" && HomeWidgets.position(HomeWidgets.MUSIC) != null) {
                // (A tap would put your moved player back in its corner: not pressed.)
                Log.e("VoltcapTest", "tile $tag id=musicspot verdict=SKIP_MOVED")
                continue
            }
            var verdict = "NONE"
            var detail = ""
            val base = a0
            for (step in 1..2) {
                if (base != a0) { h.vm.previewAppearance(base); delay(500) }
                // Before: the same state the tile starts from (the game window open for its style).
                if (id == "endless") {
                    h.vm.previewAppearance(a0.copy(loopRow = if (step == 1) a0.loopRow else !a0.loopRow)); delay(600)
                    h.vm.setFocus(0); delay(500); h.key(android.view.KeyEvent.KEYCODE_DPAD_LEFT); delay(1300)
                } else if (id == "popupstyle") { h.openDetails(); delay(1600) }
                val before = twoShots(h)
                if (id == "popupstyle") { h.closeAll(); delay(600) }
                val focusBefore = h.vm.uiState.value.focusedIndex
                if (id == "endless") { h.vm.previewAppearance(a0); delay(400) }
                val after = act(h, id, if (id == "endless") 1 else step, base)
                val focusAfter = h.vm.uiState.value.focusedIndex
                val maskM = BooleanArray(W * H); val maskS = BooleanArray(W * H)
                addNoise(before.a.main, before.b.main, maskM); addNoise(after.a.main, after.b.main, maskM)
                addNoise(before.a.second, before.b.second, maskS); addNoise(after.a.second, after.b.second, maskS)
                val m = score(before.a.main, after.a.main, maskM)
                val s = score(before.a.second, after.a.second, maskS)
                val noise = maskM.count { it }.toFloat() / maskM.size
                val themeMoved = h.vm.uiState.value.appTheme != theme0
                detail = (if (id == "endless") "focus=$focusBefore>$focusAfter " else "") + "step=$step main=${"%.4f".format(m)} second=${"%.4f".format(s)} noise=${"%.3f".format(noise)}"
                // Put everything back before deciding.
                h.closeAll(); delay(200)
                restore(h, a0, features0, badge0)
                delay(900)
                if (themeMoved) { verdict = "THEME_CHANGED"; break }
                if (m >= EFFECT || s >= EFFECT) { verdict = "EFFECT"; break }
                if (id == "none") break
                if (id == "endless") {
                    // Endless was tried both ways in one go (off→on step 1, on→off step 2 is the same pair): done.
                    save(ctx, "${a0.homeLayout}_${a0.cardArtType}_${id}_a", before.a.main)
                    save(ctx, "${a0.homeLayout}_${a0.cardArtType}_${id}_b", after.a.main)
                    break
                }
                if (step == 2) {
                    val name = "${a0.homeLayout}_${a0.cardArtType}_$id"
                    save(ctx, name + "_a", before.a.main); save(ctx, name + "_b", after.a.main)
                    if (before.a.second != null) { save(ctx, name + "_a2", before.a.second); save(ctx, name + "_b2", after.a.second) }
                    if (noise > 0.5f) verdict = "NOISY"
                }
            }
            Log.e("VoltcapTest", "tile $tag id=$id $detail verdict=$verdict")
        }
        restore(h, a0, features0, badge0)
    }

    private fun restore(h: Hooks, a0: com.shelf.launcher.data.AppearanceSettings, f0: Map<String, String>, badge0: Boolean) {
        val now = com.shelf.launcher.data.Features.snapshot()
        val changed = f0.filter { (k, v) -> now[k] != v }
        if (changed.isNotEmpty()) com.shelf.launcher.data.Features.restore(changed)
        if (h.vm.uiState.value.achievementBadgeNextToTitle != badge0) h.vm.toggleAchievementBadgePlacement()
        h.vm.previewAppearance(a0)
    }
}
