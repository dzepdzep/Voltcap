package com.shelf.launcher.ui

import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.getAllSemanticsNodes

/**
 * QA (pass 3): the controller walk. After every key press, what has focus and what can be pressed on the screen that has
 * the controller — read from Compose's own semantics tree (uiautomator can't read Voltcap: it never goes idle).
 *
 *  `walk key=<code> win=<window> focus=<label> @x,y,w,h`        after each key (a moment later, once the UI moved)
 *  `walk screen <hash> n=<count>` + `walk node <label> @… f=<focusable> c=<clickable>`   when the set of controls changes
 *
 * Only with the test hooks AND `--ez voltcap_test_walk true` (tools/qa/device/walk.sh, tools/walkcheck.py). Off: one
 * boolean check per key.
 */
internal object QaWalk {
    @Volatile var on = false
    private val roots = java.util.Collections.newSetFromMap(java.util.WeakHashMap<ViewRootForTest, Boolean>())
    private var lastScreen = 0
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Called once at start (with the hooks): every Compose root made from now on (the main one, dialogs, popups) is known. */
    fun install() {
        val prev = ViewRootForTest.onViewCreatedCallback
        ViewRootForTest.onViewCreatedCallback = { r -> synchronized(roots) { roots.add(r) }; prev?.invoke(r) }
    }

    private var lastFocus = ""
    private val poll = object : Runnable {
        override fun run() {
            if (!on) return
            runCatching { dump(0) }.onFailure { QaTrace.event("walk", "error ${it.javaClass.simpleName}") }
            main.postDelayed(this, 150)
        }
    }

    // Dialogs and popups are their own windows: their keys never pass the activity, so the walk polls (every 150 ms,
    // logging only changes) instead of listening for keys. walk.sh marks each key press in the log.
    fun set(want: Boolean) {
        on = want && QaTrace.on; lastScreen = 0; lastFocus = ""
        QaTrace.event("walk", "on=$on")
        main.removeCallbacks(poll)
        if (on) main.post(poll)
    }

    @Suppress("UNUSED_PARAMETER")
    fun afterKey(event: android.view.KeyEvent) {}

    fun liveRoots(): List<ViewRootForTest> = synchronized(roots) { roots.filter { it.view.isAttachedToWindow && it.view.isShown } }
    fun labelOf(n: SemanticsNode): String = label(n)

    private fun label(n: SemanticsNode): String {
        val c = n.config
        val parts = ArrayList<String>()
        c.getOrNull(SemanticsProperties.TestTag)?.let { parts += "#$it" }
        c.getOrNull(SemanticsProperties.ContentDescription)?.let { parts += it.joinToString("/") }
        c.getOrNull(SemanticsProperties.Text)?.let { parts += it.joinToString("/") { a -> a.text } }
        c.getOrNull(SemanticsProperties.EditableText)?.let { parts += "edit:" + it.text }
        if (parts.isEmpty()) {
            // no own words: the first words inside it
            val sub = n.children.asSequence().map { label(it) }.firstOrNull { !it.startsWith("@") }
            if (sub != null) parts += sub
        }
        return parts.joinToString(" ").replace(Regex("\\s+"), " ").take(60).ifEmpty { "@" + box(n) }
    }

    private fun box(n: SemanticsNode): String {
        val b = n.boundsInWindow
        return "${b.left.toInt()},${b.top.toInt()},${b.width.toInt()},${b.height.toInt()}"
    }

    private fun dump(code: Int) {
        val live = synchronized(roots) { roots.filter { it.view.isAttachedToWindow && it.view.isShown } }
        // The window with the controller: the one with window focus (a dialog over the home screen), else the last made.
        val root = live.firstOrNull { it.view.hasWindowFocus() } ?: live.lastOrNull() ?: return
        val nodes = root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
        val win = root.view.javaClass.simpleName + "@" + System.identityHashCode(root.view).toString(16)
        val focused = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
        val f = "win=$win focus=${focused?.let { label(it) + " @" + box(it) } ?: "-"}"
        if (f != lastFocus) { lastFocus = f; QaTrace.event("walk", "key=$code $f") }
        val act = nodes.filter {
            val c = it.config
            c.contains(SemanticsActions.OnClick) || c.contains(SemanticsProperties.Focused) || c.contains(SemanticsActions.SetProgress)
        }.filter { val b = it.boundsInWindow; b.width > 1f && b.height > 1f }
        val sig = act.joinToString("|") { label(it) }.hashCode() xor win.hashCode()
        if (sig != lastScreen) {
            lastScreen = sig
            QaTrace.event("walk", "screen ${Integer.toHexString(sig)} win=$win n=${act.size}")
            for (n in act) {
                val c = n.config
                QaTrace.event("walk", "node ${label(n)} @${box(n)} f=${if (c.contains(SemanticsProperties.Focused)) 1 else 0} " +
                    "c=${if (c.contains(SemanticsActions.OnClick)) 1 else 0} s=${if (c.contains(SemanticsActions.SetProgress)) 1 else 0}")
            }
        }
    }
}
