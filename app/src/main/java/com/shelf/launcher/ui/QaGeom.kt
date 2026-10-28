package com.shelf.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * QA geometry trace (update 370, tools/qa/tools/geomcheck.py). Opt-in twice: the test-hooks flag file AND
 * `--ez voltcap_test_geom true` (false stops it). Off, [qaGeom] is a plain `Modifier` (no state, no callback) and
 * nothing runs. On, each marked element keeps its LayoutCoordinates and one Choreographer callback per frame logs the
 * elements whose on-screen rectangle changed as `geom <id>=x,y,w,h …` (VoltcapQA) — read from the layer transform too,
 * so a scroll done with graphicsLayer / offset is seen (onGloballyPositioned alone is not called for it).
 * Also `geomscreen W H`, `geomfocus <id>`, `geomgone <id>` (left composition) and `geomkey <name>` (MainActivity.dispatchKeyEvent). Stops by itself after
 * [MAX_MS] so a lost adb connection cannot leave it running. Reads nothing, saves nothing.
 */
internal object QaGeom {
    private const val MAX_MS = 120_000L
    @Volatile var on = false
        private set
    private val coords = HashMap<String, LayoutCoordinates>()   // main thread only
    private val last = HashMap<String, String>()
    private var running = false
    private var startedAt = 0L
    private var focusId: String? = null
    private val cb = object : android.view.Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!on) { running = false; return }
            if (android.os.SystemClock.uptimeMillis() - startedAt > MAX_MS) { set(false); return }
            flush()
            android.view.Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun set(want: Boolean) {
        val ok = want && QaTrace.on
        if (ok == on) return
        on = ok
        if (ok) {
            startedAt = android.os.SystemClock.uptimeMillis()
            val dm = android.content.res.Resources.getSystem().displayMetrics
            QaTrace.event("geomscreen", "${dm.widthPixels} ${dm.heightPixels}")
            if (!running) { running = true; android.view.Choreographer.getInstance().postFrameCallback(cb) }
        } else {
            coords.clear(); last.clear(); focusId = null
            QaTrace.event("geom", "off")
        }
    }

    /** A hardware / injected key reached the activity (MainActivity.dispatchKeyEvent). */
    fun key(code: Int, down: Boolean) {
        if (on && down) QaTrace.event("geomkey", android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_"))
    }

    fun attach(id: String, c: LayoutCoordinates, focused: Boolean) {
        coords[id] = c
        if (focused && focusId != id) { focusId = id; QaTrace.event("geomfocus", id) }
    }

    fun detach(id: String) { coords.remove(id); last.remove(id); if (on) QaTrace.event("geomgone", id) }

    private fun flush() {
        val sb = StringBuilder()
        for ((id, c) in coords) {
            if (!c.isAttached) continue
            val r = c.boundsInRoot()
            val s = "${r.left.toInt()},${r.top.toInt()},${r.width.toInt()},${r.height.toInt()}"
            if (last[id] != s) { last[id] = s; sb.append(id).append('=').append(s).append(' ') }
        }
        if (sb.isNotEmpty()) QaTrace.event("geom", sb.toString().trim())
    }
}

/** Marks an element for the geometry trace; a plain [Modifier] unless the trace is on (see [QaGeom]). */
@Composable
internal fun Modifier.qaGeom(rawId: String, focused: Boolean = false): Modifier {
    if (!QaGeom.on) return this
    val id = rawId.map { if (it.isWhitespace()) '_' else it }.joinToString("") // a log line is split on spaces (game paths have them)
    DisposableEffect(id) { onDispose { QaGeom.detach(id) } }
    return this.onGloballyPositioned { QaGeom.attach(id, it, focused) }
}
