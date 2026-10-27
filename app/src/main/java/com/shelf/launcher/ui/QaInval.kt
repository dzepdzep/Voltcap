@file:OptIn(androidx.compose.runtime.ExperimentalComposeRuntimeApi::class)

package com.shelf.launcher.ui

import androidx.compose.runtime.Composition
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.ObservableComposition

/**
 * QA (pass 2): WHY a part re-composes. A part registers its recompose scope ([watch]); every time Compose re-runs it,
 * the state objects that invalidated it are counted and logged once a second as
 * `inval <part>: 95x <state(value)> | …` (logcat VoltcapQA). Only with the test hooks AND `--ez voltcap_test_churn true`;
 * off, [watch] returns at once and nothing is observed.
 */
internal object QaInval {
    @Volatile var on = false
        set(v) { if (v && !field) readSites.clear(); field = v }
    private val scopes = java.util.WeakHashMap<RecomposeScope, String>()
    private val observed = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Composition, Boolean>())
    private val counts = HashMap<String, Int>()
    private val readSites = java.util.Collections.synchronizedSet(HashSet<Int>())
    /** The states that hold the theme colours (seen changing by QaTrace's churn observer, which may read them). */
    val themeStates: MutableSet<Int> = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Int, Boolean>())
    private var since = 0L

    fun watch(name: String, scope: RecomposeScope, composition: Composition) {
        if (!on) return
        synchronized(this) {
            scopes[scope] = name
            if (observed.add(composition)) (composition as? ObservableComposition)?.setObserver(observer)
        }
    }

    private fun describe(o: Any): String {
        val v = runCatching { (o as? androidx.compose.runtime.State<*>)?.value }.getOrNull()
        val vs = when (v) {
            null -> if (o is androidx.compose.runtime.State<*>) "null" else o.javaClass.simpleName
            is Number, is Boolean, is Enum<*> -> v.toString()
            is String -> v.take(24)
            else -> v.javaClass.name.substringAfterLast('.')
        }
        return "${o.javaClass.simpleName}($vs)@${System.identityHashCode(o)}"
    }

    private fun tick() {
        val now = android.os.SystemClock.uptimeMillis()
        if (since == 0L) since = now
        if (now - since >= 1000) {
            val top = counts.entries.filter { it.value >= 5 }.sortedByDescending { it.value }.take(10)
            if (top.isNotEmpty()) QaTrace.event("inval", top.joinToString(" | ") { "${it.value}x ${it.key}" })
            counts.clear(); since = now
        }
    }

    private val observer = object : CompositionObserver {
        override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) {
            if (!on) return
            synchronized(this@QaInval) {
                val name = scopes[scope] ?: return
                // (Per-item parts — the carousel's games — are counted by kind of state, not one line per game.)
                val what = value?.let { if (name.startsWith("Car")) describe(it).substringBefore('@') else describe(it) } ?: "(forced)"
                counts.merge("$name: $what", 1, Int::plus)
                tick()
            }
        }
        override fun onBeginComposition(composition: ObservableComposition) {}
        override fun onEndComposition(composition: ObservableComposition) {}
        override fun onScopeEnter(scope: RecomposeScope) {}
        // Who reads the theme colours in a watched part's own scope (the read that makes the colour glide re-compose it,
        // QA D22): the stack of each distinct read site, once (`themeread <part>: frames…`).
        override fun onReadInScope(scope: RecomposeScope, value: Any) {
            if (!on) return
            // (Never read a state here: that read comes back to this observer — pass 3, a first try made 5 s frames.)
            if (System.identityHashCode(value) !in themeStates) return
            val name = synchronized(this@QaInval) { scopes[scope] } ?: return
            val st = Thread.currentThread().stackTrace.drop(3).filter { !it.className.startsWith("androidx.compose.runtime") }.take(14)
            val key = name + st.take(8).joinToString { it.className + it.methodName + it.lineNumber }
            if (!readSites.add(key.hashCode())) return
            QaTrace.event("themeread", "$name: " + st.joinToString(" < ") { "${it.className}.${it.methodName}:${it.lineNumber}" })
        }
        override fun onScopeExit(scope: RecomposeScope) {}
        override fun onScopeDisposed(scope: RecomposeScope) {}
    }
}
