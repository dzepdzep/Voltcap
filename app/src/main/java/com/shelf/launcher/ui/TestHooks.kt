package com.shelf.launcher.ui

import androidx.compose.runtime.mutableStateOf

/**
 * Update 261 (#120): a way for a layout walk over adb to pick the home layout without tapping through R1 — the Layout
 * tile sits in a different place on every layout. Off unless the file `voltcap_test_hooks` exists in Voltcap's own
 * external files folder (Android/data/com.shelf.launcher/files), which only adb or Voltcap itself can create; then
 *   am start -n com.shelf.launcher/.MainActivity --es voltcap_test_layout SPOT
 * switches the layout, as R1 › Layout would (each layout keeps its own options). Nothing else is reachable through it.
 */
internal object TestHooks {
    /** The layout asked for (by its enum name), taken by the home screen. */
    val layout = mutableStateOf<String?>(null)
    /** A game to start from (part of its name): its console's category, the game focused — `--es voltcap_test_game …`. */
    val game = mutableStateOf<String?>(null)
    /** A Settings page to open (its name, "_" for spaces) — `--es voltcap_test_settings Game_look`. */
    val settings = mutableStateOf<String?>(null)
    /**
     * A game look shown for this session only (`--es voltcap_test_look CASE`); while it's on, the layout hook only shows
     * layouts too (nothing saved). A restart of Voltcap brings your own settings back.
     */
    val look = mutableStateOf<String?>(null)
    @Volatile var previewOnly = false

    fun fromIntent(context: android.content.Context, intent: android.content.Intent?) {
        val want = intent?.getStringExtra("voltcap_test_layout")
        val g = intent?.getStringExtra("voltcap_test_game")
        val st = intent?.getStringExtra("voltcap_test_settings")
        val lk = intent?.getStringExtra("voltcap_test_look")
        if (want == null && g == null && st == null && lk == null) return
        val on = runCatching { java.io.File(context.getExternalFilesDir(null), "voltcap_test_hooks").exists() }.getOrDefault(false)
        if (!on) return
        if (lk != null && com.shelf.launcher.data.CardArtType.values().any { it.name == lk }) look.value = lk
        if (st != null) settings.value = st.replace('_', ' ')
        if (g != null) game.value = g.replace('_', ' ') // (adb: "Contra_III")
        if (want != null && com.shelf.launcher.data.HomeLayout.values().any { it.name == want }) layout.value = want
    }
}
