package com.shelf.launcher.ui

import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import kotlinx.coroutines.launch

/**
 * QA (pass 3): the controller EXPLORER. Drives the D-pad itself (key events into the window that has focus, the same
 * path a pad key takes after the system) and maps every page it can reach:
 *  - on each page it sweeps ▼ until focus stops moving, then ▶ and sweeps again (two-column pages), ▲ back;
 *  - a row whose words end in "›" leads to a sub-page: it is opened with A, explored, and closed with B;
 *  - A is pressed ONLY on such "›" rows, and never on rows naming something destructive (delete, sign out, restore…).
 * Everything it sees is logged in the walk format (MARK key / walk key= / walk screen / walk node), so walkcheck.py
 * reports reached / not reached / touch-only per window. Start: `--es voltcap_test_explore <start keys>` with the hooks
 * and the walk on (device/explore.sh does it all, with the settings snapshot around it).
 */
internal object QaExplore {
    @Volatile var running = false
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val deny = Regex("(?i)delete|remove|reset|sign ?out|log ?out|restore|clear|uninstall|format|erase|wipe|rescan|update|install|download|backup|import|export|forget|unlink|disconnect|factory|launch|play|open .*app|android settings|shut|reboot|restart|put everything back|edit home|move & resize|start editing|sign in|log in|pair|scan")

    private fun roots(): List<ViewRootForTest> = QaWalk.liveRoots()

    private fun focusRoot(): ViewRootForTest? = roots().let { r -> r.firstOrNull { it.view.hasWindowFocus() } ?: r.lastOrNull() }

    private fun focusedLabel(): String {
        val root = focusRoot() ?: return "-"
        val n = root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
            .firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true } ?: return "-"
        return QaWalk.labelOf(n)
    }

    /** The focused control's box on screen (left, top, right, bottom), or null. */
    private fun focusedBox(): IntArray? {
        val root = focusRoot() ?: return null
        val n = root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
            .firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true } ?: return null
        val b = n.boundsInWindow
        return intArrayOf(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
    }

    /** Label + left edge: the same words twice on a page (two "Large" chips) are still told apart. */
    private fun focusedId(): String = focusedLabel() + "@" + (focusedBox()?.get(0) ?: -1)

    private fun windowId(): String = focusRoot()?.let { System.identityHashCode(it.view).toString(16) } ?: "-"

    private suspend fun key(name: String) {
        val code = when (name) {
            "U" -> android.view.KeyEvent.KEYCODE_DPAD_UP; "D" -> android.view.KeyEvent.KEYCODE_DPAD_DOWN
            "L" -> android.view.KeyEvent.KEYCODE_DPAD_LEFT; "R" -> android.view.KeyEvent.KEYCODE_DPAD_RIGHT
            "A" -> android.view.KeyEvent.KEYCODE_BUTTON_A; "B" -> android.view.KeyEvent.KEYCODE_BUTTON_B
            "SELECT" -> android.view.KeyEvent.KEYCODE_BUTTON_SELECT; "START" -> android.view.KeyEvent.KEYCODE_BUTTON_START
            "R1" -> android.view.KeyEvent.KEYCODE_BUTTON_R1; "L1" -> android.view.KeyEvent.KEYCODE_BUTTON_L1
            "Y" -> android.view.KeyEvent.KEYCODE_BUTTON_Y; "X" -> android.view.KeyEvent.KEYCODE_BUTTON_X
            else -> return
        }
        // Stopped (`--es voltcap_test_explore stop`, or the walk switched off): no further key is pressed.
        if (stopAsked || !QaWalk.on) throw IllegalStateException("stopped")
        android.util.Log.e("VoltcapQA", "MARK key $name")
        // A real key event through the input system (Instrumentation, as UI tests do): allowed into the app's own
        // focused window, and it takes exactly the path of a pad press — first-press focus, dialogs, back included.
        val sent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val ins = android.app.Instrumentation()
                ins.sendKeySync(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
                ins.sendKeySync(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
            }.onFailure { QaTrace.event("explore", "inject failed ${it.javaClass.simpleName}: ${it.message}") }.isSuccess
        }
        if (!sent) throw IllegalStateException("key injection refused")
        kotlinx.coroutines.delay(320)
        // Until a scroll that brings the new focus into view has settled (the focused box stays put), at most ~0.5 s more.
        var last = focusedBox()?.joinToString(); var n = 0
        while (n++ < 6) { kotlinx.coroutines.delay(80); val now = focusedBox()?.joinToString(); if (now == last) break; last = now }
    }

    /** Presses [dir] until focus stops moving (or [max] presses); returns the labels met, in order. */
    private suspend fun sweep(dir: String, max: Int = 60): List<String> {
        val seen = ArrayList<String>()
        var last = focusedLabel(); var same = 0
        repeat(max) {
            key(dir)
            val f = focusedLabel()
            if (f == last) { if (++same >= 2) return seen } else { same = 0; seen += f; last = f }
        }
        return seen
    }

    private val visited = HashSet<String>()

    private suspend fun explorePage(depth: Int) {
        val win = windowId()
        val labels = LinkedHashSet<String>()
        labels += focusedLabel()
        labels += sweep("U")
        // ▼ one step at a time, and at each row ◀ and ▶ along it (chips, tiles) while focus stays on that row, then back to
        // where the row was entered. (Pass 3: a chip row is entered on its picked chip — "Large" — so the chips left of it
        // were never seen, and ▶ off the row's end jumps to another row or to Close, so "◀ as many times" got lost.)
        // (The page's left edge: from its first row that isn't the search field or Close at the top — E6: worked out from
        // the search field, every row's ◀ sweep counted the page's own left column as "off the page", and the page was lost.)
        var pageLeft = -1
        var lastRow = focusedLabel(); var same = 0; var rows = 0
        // (A row it couldn't find its way back to once: the rest of that page is walked ▼ only, logged "row lost".)
        var rowWalk = true
        while (rows++ < 80) {
            val home = focusedId(); val top = focusedBox()?.get(1) ?: 0
            val header = Regex("^(Search settings|Close)").containsMatchIn(focusedLabel())
            if (!header && pageLeft < 0) pageLeft = (focusedBox()?.get(0) ?: 0) - 24
            fun onRow() = focusedBox()?.let { kotlin.math.abs(it[1] - top) < 40 && it[0] >= pageLeft } ?: false
            for ((dir, back) in (if (rowWalk && !header && pageLeft >= 0) listOf("R" to "L", "L" to "R") else emptyList())) {
                var g = 0
                while (g++ < 10) {
                    val before = focusedId(); key(dir)
                    if (focusedId() == before) break
                    if (!onRow()) { key(back); break }
                    labels += focusedLabel()
                }
                var h = 0
                while (focusedId() != home && h++ < 12) key(back)
                if (focusedId() != home) {
                    // Off the page (◀ into the category list, ▶ to Close): from the top of the page ▼ (and ▶) to it again.
                    if (dir == "L" && windowId() == win) key("R")
                    sweep("U")
                    var k = 0
                    while (focusedId() != home && k++ < 60) {
                        val b = focusedId(); key("D")
                        if (focusedId() == b) { key("R"); if (focusedId() == b) break }
                    }
                    if (focusedId() != home) { QaTrace.event("explore", "row lost at $home"); rowWalk = false; break }
                }
            }
            key("D")
            val f = focusedLabel()
            if (f == lastRow) { if (++same >= 2) break } else { same = 0; labels += f; lastRow = f }
        }
        labels += sweep("U")
        // (A settings page: ◀ leads back into the category list, which changes the page — only deeper pages go left.)
        if (depth >= 2) labels += sweep("L", 8)
        QaTrace.event("explore", "page depth=$depth win=$win labels=${labels.size}")
        if (depth >= 3) return
        // Sub-pages: rows ending in › (navigation), safe by name. Reach each again from the top of the page.
        for (target in labels.filter { it.trimEnd().endsWith("›") && !deny.containsMatchIn(it) }) {
            val key = target.substringBefore("/").trim()
            if (!visited.add("$depth|$key")) continue
            sweep("U"); if (depth >= 2) sweep("L", 8)
            var found = focusedLabel() == target
            var guard = 0
            while (!found && guard++ < 60) {
                val before = focusedLabel(); key("D"); if (focusedLabel() == target) found = true
                if (!found && focusedLabel() == before) { key("R"); if (focusedLabel() == target) found = true }
            }
            if (!found) { QaTrace.event("explore", "lost $key"); continue }
            QaTrace.event("explore", "enter $key")
            key("A"); kotlinx.coroutines.delay(400)
            explorePage(depth + 1)
            key("B"); kotlinx.coroutines.delay(400)
            QaTrace.event("explore", "back from $key win=${windowId()} focus=${focusedLabel()}")
        }
    }

    /** [start]: keys that open what to explore from the home screen, e.g. "SELECT" (settings) or "R1". Then each entry of
     *  its left list ([listDepth] ≥ 1: also A on each entry and its page explored). */
    @Volatile private var stopAsked = false
    private var homeWin = "-"

    /** Settings: ▲ up the category list until the next ▲ would leave it (to the search field, further right). */
    private suspend fun toFirstCategory() {
        val listLeft = focusedBox()?.get(0) ?: return
        var g = 0
        while (g++ < 30) {
            val before = focusedId(); key("U")
            val b = focusedBox()
            // (From the search field ◀ leads to the first category — ▼ would go into the page.)
            if (b == null || b[0] > listLeft + 40) { key("L"); return }
            if (focusedId() == before) return
        }
    }

    fun start(start: String) {
        if (start == "stop") { stopAsked = true; return }
        if (running || !QaTrace.on) return
        stopAsked = false
        running = true; visited.clear()
        QaWalk.set(true)
        val scope = kotlinx.coroutines.MainScope()
        scope.launch {
            try {
                QaTrace.event("explore", "start $start")
                homeWin = windowId()
                // "from=N": start at the N-th settings category (a long walk in two runs).
                val from = Regex("from=(\\d+)").find(start)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val to = Regex("to=(\\d+)").find(start)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
                for (k in start.split(' ').filter { it.isNotBlank() && !it.startsWith("from=") && !it.startsWith("to=") }) key(k)
                kotlinx.coroutines.delay(600)
                // Nothing focused yet (a menu opens with no focus until the first press): one ▼ takes it in.
                if (focusedLabel() == "-") key("D")
                if (start.contains("SETTINGS")) {
                    // RP Classic pass: on a narrow screen Settings has its categories as tabs across the top, not a list
                    // down the left, so the ▲ / ▼ category walk below found a page's buttons instead. Each category is
                    // opened directly by name (the voltcap_test_settings path), then its page explored.
                    val cats = SETTINGS_CATEGORY_NAMES
                    QaTrace.event("explore", "categories ${cats.size}: ${cats.joinToString(" | ")}")
                    for ((i, c) in cats.withIndex()) {
                        if (i < from || i >= to) continue
                        if (deny.containsMatchIn(c)) { QaTrace.event("explore", "skip category $c"); continue }
                        var g = 0
                        while (windowId() != homeWin && g++ < 6) key("B")
                        TestHooks.settings.value = c
                        kotlinx.coroutines.delay(1300)
                        if (focusedLabel() == "-") key("D")
                        QaTrace.event("explore", "category $c")
                        explorePage(1)
                    }
                } else if (start.contains("SELECT")) {
                    // Settings: every category in the left list, its page explored.
                    // The list starts at the first category (the first ▼ lands there); ▲ from it goes to the search field.
                    // (Settings opens on the category last used, so first ▲ to the top of the list — one ▲ more is the
                    // search field, right of the list — and ▼ back.)
                    toFirstCategory()
                    val cats = ArrayList<String>(); cats += focusedLabel(); cats += sweep("D")
                    QaTrace.event("explore", "categories ${cats.size}: ${cats.joinToString(" | ")}")
                    for ((i, c) in cats.withIndex()) {
                        if (i < from || i >= to) continue
                        if (deny.containsMatchIn(c)) { QaTrace.event("explore", "skip category $c"); continue }
                        // To the i-th category, the sure way: Settings closed (B until the home screen has the controller),
                        // opened again (focus starts on the first category), ▼ i times. (◀ out of a page didn't always
                        // reach the list: Categories / Accounts were missed in run E4.)
                        var g = 0
                        while (windowId() != homeWin && g++ < 6) key("B")
                        for (k in start.split(' ').filter { it.isNotBlank() && !it.startsWith("from=") && !it.startsWith("to=") }) key(k)
                        kotlinx.coroutines.delay(500)
                        if (focusedLabel() == "-") key("D")
                        toFirstCategory()
                        repeat(i) { key("D") }
                        if (focusedLabel() != c) { QaTrace.event("explore", "lost category $c (at ${focusedLabel()})"); continue }
                        QaTrace.event("explore", "category $c")
                        key("A"); kotlinx.coroutines.delay(400)
                        key("R")
                        explorePage(1)
                        sweep("L", 4)
                    }
                } else explorePage(0)
                QaTrace.event("explore", "done")
            } catch (t: Throwable) {
                QaTrace.event("explore", "error ${t.javaClass.simpleName} ${t.message}")
            } finally { running = false }
        }
    }

}
