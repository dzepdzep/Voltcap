package com.shelf.launcher.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.semantics

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
    /** Batch 521b: a controller glyph drawn by InkGlyph (its text); dumped under "glyphs" for tools/qa/tools/glyphcheck.py. */
    val Glyph = androidx.compose.ui.semantics.SemanticsPropertyKey<String>("QaGlyph") { a, _ -> a }
    /** Update 467: a scene drawn tilted (the rental tag): its children's boxes overlap by the tilt. */
    val Tilted = androidx.compose.ui.semantics.SemanticsPropertyKey<Boolean>("QaTilted") { a, b -> a == true || b == true }

    /** Update 467: a box that shows its rows a page at a time (SnapRows) — its rows are out of view by design, like a scroll's. */
    val Paged = androidx.compose.ui.semantics.SemanticsPropertyKey<Boolean>("QaPaged") { a, b -> a == true || b == true }

    /** Update 477: a text sliding sideways to show all of itself (FullText's marquee) — cut at rest by design, reachable as
     *  it slides (rule 11), like a scrolled one; geomcheck read it as cut (Airy's long name, k477i). */
    val Slides = androidx.compose.ui.semantics.SemanticsPropertyKey<Boolean>("QaSlides") { a, b -> a == true || b == true }

    /** Sprint 525: on the game that wears the layout's selection highlight (styleFocus, focusedCard, the lit Night /
     *  Airy / Art library row) - set only while [litMarks] is on (unit tests), so Endless › Fill the row can be checked
     *  for exactly one lit copy. Nothing changes on screen. */
    val Lit = androidx.compose.ui.semantics.SemanticsPropertyKey<Boolean>("QaLit") { a, b -> a == true || b == true }
    @Volatile var litMarks = false

    /**
     * Update 467: a dump asked for by a file, not an intent — an `am start` reaches the activity and closes the popups that
     * live inside the home screen (Classic, Row reveal, Shared), so the dump would show the home. popclip.sh writes the tag
     * into <external files>/qa_clip_req; this polls it (test hooks only) and dumps at once.
     */
    @Volatile private var nlFile: java.io.File? = null
    fun noLaunchFile(): Boolean = nlFile?.exists() == true
    @Volatile private var allowFile: java.io.File? = null
    /** Update 489b (D287): launching allowed during QA only while <external files>/qa_allow_launch exists. */
    fun allowLaunchFile(): Boolean = allowFile?.exists() == true

    @Volatile private var watching = false
    fun watch(context: android.content.Context) {
        if (!QaTrace.on || watching) return
        watching = true
        val app = context.applicationContext
        val req = java.io.File(app.getExternalFilesDir(null), "qa_clip_req")
        // (The same for More: `echo on > qa_winmore_req` — voltcap_test_winmore without an intent.)
        val more = java.io.File(app.getExternalFilesDir(null), "qa_winmore_req")
        nlFile = java.io.File(app.getExternalFilesDir(null), "qa_nolaunch")
        allowFile = java.io.File(app.getExternalFilesDir(null), "qa_allow_launch")
        // (Update 489b, D287: a QA script asks whether a tap on Play would start a game — answered in qa_launch_state.)
        val launchCheck = java.io.File(app.getExternalFilesDir(null), "qa_launch_check")
        val launchState = java.io.File(app.getExternalFilesDir(null), "qa_launch_state")
        val poll = object : Runnable {
            override fun run() {
                runCatching {
                    if (req.exists()) {
                        val tag = req.readText().trim().ifEmpty { "clip" }
                        req.delete()
                        request(app, tag, 0)
                    }
                    if (launchCheck.exists()) {
                        launchCheck.delete()
                        launchState.writeText(TestHooks.launchState() + "\n")
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
        if (n.config.getOrNull(Slides) == true) return true
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
            sb.append("],")
            // (Update 474: every text as laid out — unmerged, so the words inside a button count too — with what its own
            // TextLayoutResult says: lines, did it need more height / width than it was given (cut inside its own box, or
            // stopped by maxLines), an ellipsis at its end. geomcheck's "text" check reads this; boxes alone missed D244.)
            sb.append("\"texts\":[")
            var firstT = true
            for (n in root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false)) {
                val c = n.config
                val words = c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: continue
                if (words.isBlank()) continue
                val tl = runCatching {
                    c.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { act ->
                        val l = ArrayList<androidx.compose.ui.text.TextLayoutResult>(); if (act(l)) l.firstOrNull() else null
                    }
                }.getOrNull()
                val pos = n.positionInWindow; val sz = n.size; val b = n.boundsInWindow
                if (!firstT) sb.append(','); firstT = false
                sb.append("{\"text\":\"${esc(words.take(80))}\",\"len\":${words.length},")
                sb.append("\"full\":[${pos.x.toInt()},${pos.y.toInt()},${sz.width},${sz.height}],")
                sb.append("\"shown\":[${b.left.toInt()},${b.top.toInt()},${(b.right - b.left).toInt()},${(b.bottom - b.top).toInt()}],")
                if (tl != null) {
                    val lines = tl.lineCount
                    val ell = lines > 0 && runCatching { tl.isLineEllipsized(lines - 1) }.getOrDefault(false)
                    sb.append("\"lines\":$lines,\"maxLines\":${tl.layoutInput.maxLines.let { if (it == Int.MAX_VALUE) -1 else it }},")
                    sb.append("\"ovH\":${tl.didOverflowHeight},\"ovW\":${tl.didOverflowWidth},\"ellipsis\":$ell,")
                    sb.append("\"textH\":${tl.multiParagraph.height.toInt()},\"boxH\":${tl.size.height},")
                }
                // (Update 477, D266: a text a full FlowRow / Column dropped is measured but never placed — its box is the last
                // one it had, so 'full' and 'shown' look fine while nothing is drawn (Airy's chips with More open, k477e).)
                sb.append("\"placed\":${placed(n)},")
                sb.append("\"scroll\":${scrolls(n)},\"giveway\":${givesWay(n)},\"id\":${n.id}}")
            }
            sb.append("],")
            // (Batch 521b: every InkGlyph - the letter's ink box - for glyphcheck.py, which finds its circle / pill in the screenshot.)
            sb.append("\"glyphs\":[")
            var firstG = true
            for (n in root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false)) {
                val g = n.config.getOrNull(Glyph) ?: continue
                val b = n.boundsInWindow
                if (!firstG) sb.append(','); firstG = false
                sb.append("{\"text\":\"${esc(g)}\",\"shown\":[${b.left.toInt()},${b.top.toInt()},${(b.right - b.left).toInt()},${(b.bottom - b.top).toInt()}],\"placed\":${placed(n)}}")
            }
            sb.append("]}")
        }
        sb.append("],")
        // (Update 474: which game the home screen is on and which one the open window shows — the "wrong game" check.)
        val vmFocus = runCatching { TestHooks.viewState?.invoke()?.optString("focus") }.getOrNull()?.takeIf { it.isNotEmpty() }
        sb.append("\"focusedGame\":${vmFocus?.let { "\"" + esc(it.takeLast(120)) + "\"" } ?: "null"},")
        sb.append("\"originKey\":${GameOrigin.key?.takeIf { it != GameOrigin.FOCUSED }?.let { "\"" + esc(it.takeLast(120)) + "\"" } ?: "null"},")
        sb.append("\"origin\":${GameOrigin.rect?.let { "[${it.left.toInt()},${it.top.toInt()},${it.width.toInt()},${it.height.toInt()}]" } ?: "null"},")
        sb.append("\"popupGame\":${GamePopup.game?.let { "\"" + esc(it.stableKey.takeLast(120)) + "\"" } ?: "null"},")
        sb.append("\"popupName\":${GamePopup.game?.let { "\"" + esc(it.name.take(120)) + "\"" } ?: "null"},")
        sb.append("\"images\":${QaArt.json()}}")
        return sb.toString()
    }

    /** Update 477: the node and every layout above it placed (drawn) in the last layout pass. */
    private fun placed(n: SemanticsNode): Boolean {
        var li: androidx.compose.ui.layout.LayoutInfo? = n.layoutInfo
        while (li != null) { if (!li.isPlaced) return false; li = li.parentInfo }
        return true
    }

    /** Update 474: inside a part marked as giving way by design (it may shrink to nothing when the room is short). */
    val GivesWay = androidx.compose.ui.semantics.SemanticsPropertyKey<Boolean>("QaGivesWay") { a, b -> a == true || b == true }
    private fun givesWay(n: SemanticsNode): Boolean {
        var p: SemanticsNode? = n
        while (p != null) { if (p.config.getOrNull(GivesWay) == true) return true; p = p.parent }
        return false
    }
}

/** Sprint 525: marks the lit game for the tests ([QaClip.Lit]); nothing while [QaClip.litMarks] is off. */
internal fun androidx.compose.ui.Modifier.qaLit(on: Boolean): androidx.compose.ui.Modifier =
    if (on && QaClip.litMarks) this.then(androidx.compose.ui.Modifier.semantics { this[QaClip.Lit] = true }) else this
