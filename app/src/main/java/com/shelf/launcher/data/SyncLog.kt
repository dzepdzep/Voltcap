package com.shelf.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The last few things Google Drive sync did ("what happened last"), newest first. Kept per device. */
object SyncLog {
    data class Entry(val at: Long, val text: String)

    private const val PREFS = "sync_log"
    private const val KEY = "entries"
    private const val MAX = 15
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> get() = _entries
    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
            _entries.value = raw.split('\n').mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) null else line.substring(0, tab).toLongOrNull()?.let { Entry(it, line.substring(tab + 1)) }
            }
        }
    }

    fun add(context: Context, text: String) {
        val clean = text.replace('\n', ' ').replace('\t', ' ').trim()
        if (clean.isEmpty()) return
        load(context)
        val list = _entries.value
        if (list.firstOrNull()?.text == clean && System.currentTimeMillis() - list.first().at < 60_000) return
        val next = (listOf(Entry(System.currentTimeMillis(), clean)) + list).take(MAX)
        _entries.value = next
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, next.joinToString("\n") { "${it.at}\t${it.text}" }).apply()
        }
    }
}
