package com.shelf.launcher.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.getAllSemanticsNodes

/**
 * Update 467 (owner: "I don't want to discover clipped controls manually anymore"): QA only — every control and text on
 * screen as Compose laid it out, for tools/qa/tools/geomcheck.py `--clip`. For each window (the main one, popups, dialogs)
 * and each node that can be pressed / focused, or shows text:
 *   `full` = where the node itself is (positionInWindow + size — not cut by anything),
 *   `shown` = the part of it inside every ancestor's bounds (boundsInWindow — Compose cuts a child to its parents),
 *   `scroll` = whether an ancestor scrolls (a control cut by a scrolling list can be scrolled to; one cut by a fixed box
 *   can't), plus focus, label, role and the window's place on the screen, the screen size, density and font scale.
 * Written to <external files>/qa_clip/<tag>.json with `--es voltcap_test_clip <tag>` (test hooks only). Layout bounds can
 * miss what drawing cuts (a clip() on a parent of the same size, a translucent cover) — geomcheck also reads the screenshot.
 */
internal object QaClip {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Update 467: set by focusRing on the element showing the controller focus (test hooks only) — merged up to its control. */
    val Ring = androidx.compose.ui.semantics.SemanticsPropertyKey<Boolean>("QaRing") { a, b -> a == true || b == true }
    /** Update 467: a scene drawn tilted (the rental tag): its children's boxes overlap by the tilt. */
    val Tilted = androidx.compose.ui.semantics.SemanticsPropertyKey<Boolean>("QaTilted") { a, b -> a == true || b == true }

    /** Update 467: a box that shows its rows a page at a time (SnapRows) — its rows are out of view by design, like a scroll's. */
    val Paged = androidx.compose.ui.semantics.SemanticsPropertyKey<Boolean>("QaPaged") { a, b -> a == true || b == true }

    /**
     * Update 467: a dump asked for by a file, not an intent — an `am start` reaches the activity and closes the popups that
     * live inside the home screen (Classic, Row reveal, Shared), so the dump would show the home. popclip.sh writes the tag
     * into <external files>/qa_clip_req; this polls it (test hooks only) and dumps at once.
     */
    @Volatile private var nlFile: java.io.File? = null
    fun noLaunchFile(): Boolean = nlFile?.exists() == true

    @Volatile private var watching = false
    fun watch(context: android.content.Context) {
        if (!QaTrace.on || watching) return
        watching = true
        val app = context.applicationContext
        val req = java.io.File(app.getExternalFilesDir(null), "qa_clip_req")
        // (The same for More: `echo on > qa_winmore_req` — voltcap_test_winmore without an intent.)
        val more = java.io.File(app.getExternalFilesDir(null), "qa_winmore_req")
        nlFile = java.io.File(app.getExternalFilesDir(null), "qa_nolaunch")
        val poll = object : Runnable {
            override fun run() {
                runCatching {
                    if (req.exists()) {
                        val tag = req.readText().trim().ifEmpty { "clip" }
                        req.delete()
                        request(app, tag, 0)
                    }
                    if (more.exists()) {
                        TestHooks.winMore.value = more.readText().trim() != "off"
                        more.delete()
                    }
                }
                main.postDelayed(this, 250)
            }
        }
        main.postDelayed(poll, 1000)
    }

    fun request(context: android.content.Context, tag: String, delayMs: Long = 700) {
        if (!QaTrace.on) return
        val dir = java.io.File(context.getExternalFilesDir(null), "qa_clip").apply { mkdirs() }
        main.postDelayed({
            runCatching { java.io.File(dir, tag.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json").writeText(dump(context)) }
                .onFailure { QaTrace.event("clip", "error ${it.javaClass.simpleName} ${it.message}") }
            QaTrace.event("clip", "done $tag")
        }, delayMs)
    }

    private fun paged(n: SemanticsNode): Boolean {
        var p = n.parent
        while (p != null) { if (p.config.getOrNull(Paged) == true) return true; p = p.parent }
        return false
    }

    private fun tilted(n: SemanticsNode): Boolean {
        var p: SemanticsNode? = n
        while (p != null) { if (p.config.getOrNull(Tilted) == true) return true; p = p.parent }
        return false
    }

    private fun scrolls(n: SemanticsNode): Boolean {
        var p = n.parent
        while (p != null) {
            val c = p.config
            if (c.getOrNull(Paged) == true) return true
            if (c.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.let { it.maxValue() > 0f } == true ||
                c.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)?.let { it.maxValue() > 0f } == true) return true
            p = p.parent
        }
        return false
    }

    private fun esc(s: String) = buildString { for (ch in s) when (ch) { '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append(' '); else -> if (ch < ' ') append(' ') else append(ch) } }

    fun dump(context: android.content.Context): String {
        val dm = context.resources.displayMetrics
        val fs = context.resources.configuration.fontScale
        // (The row styles — Opens / Reveal / Shared — need the game drawn on this screen; without it they are not offered.)
        val row = GameOrigin.rect?.let { it.width >= 4f && it.height >= 4f } == true
        val sb = StringBuilder()
        sb.append("{\"screen\":[${dm.widthPixels},${dm.heightPixels}],\"density\":${dm.density},\"fontScale\":$fs,\"hasRow\":$row,\"windows\":[")
        var firstW = true
        for (root in QaWalk.liveRoots()) {
            val loc = IntArray(2); root.view.getLocationOnScreen(loc)
            if (!firstW) sb.append(','); firstW = false
            sb.append("{\"id\":\"${System.identityHashCode(root.view).toString(16)}\",\"at\":[${loc[0]},${loc[1]},${root.view.width},${root.view.height}],")
            sb.append("\"focus\":${root.view.hasWindowFocus()},\"nodes\":[")
            // (Collected first: a round icon key with its word under it — Airy's ▶ over "Play" — has no words of its own,
            // so it takes the word right under it, as you read it.)
            class Row(val n: androidx.compose.ui.semantics.SemanticsNode, var label: String, val control: Boolean, val ring: Boolean)
            val rows = ArrayList<Row>()
            for (n in root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)) {
                val c = n.config
                val click = c.getOrNull(SemanticsActions.OnClick) != null
                val ring = c.getOrNull(Ring) == true
                val focusable = c.getOrNull(SemanticsProperties.Focused) != null || ring
                val text = c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
                if (!click && !focusable && text.isNullOrBlank()) continue
                rows += Row(n, QaWalk.labelOf(n), click || focusable, ring)
            }
            for (r in rows) if (r.control && r.label.startsWith("@")) {
                val b = r.n.boundsInWindow
                rows.firstOrNull { t ->
                    val tb = t.n.boundsInWindow
                    !t.control && tb.top >= b.bottom - 4f && tb.top <= b.bottom + b.height * 0.6f &&
                        (tb.left + tb.right) / 2f in b.left..b.right
                }?.let { r.label = it.label }
            }
            var firstN = true
            for (r in rows) {
                val n = r.n; val c = n.config
                val pos = n.positionInWindow; val sz = n.size; val b = n.boundsInWindow
                if (!firstN) sb.append(','); firstN = false
                sb.append("{\"label\":\"${esc(r.label.take(80))}\",\"kind\":\"${if (r.control) "control" else "text"}\",")
                sb.append("\"full\":[${pos.x.toInt()},${pos.y.toInt()},${sz.width},${sz.height}],")
                sb.append("\"shown\":[${b.left.toInt()},${b.top.toInt()},${(b.right - b.left).toInt()},${(b.bottom - b.top).toInt()}],")
                sb.append("\"focused\":${c.getOrNull(SemanticsProperties.Focused) == true || r.ring},\"scroll\":${scrolls(n)},\"tilt\":${tilted(n)},\"paged\":${paged(n)},\"id\":${n.id}}")
            }
            sb.append("]}")
        }
        sb.append("]}")
        return sb.toString()
    }
}
