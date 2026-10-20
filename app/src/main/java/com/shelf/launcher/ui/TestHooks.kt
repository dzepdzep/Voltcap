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
    /** A category to show (`--es voltcap_test_category ALL`, or a console's name): for the scroll-stutter runs. */
    val category = mutableStateOf<String?>(null)
    /** 0.6.15: open the Sort window (`--ez voltcap_test_sort true`). */
    val sort = mutableStateOf(false)
    /**
     * Update 274: `--es voltcap_test_inspect "Game_name|shellId|yaw|pitch"` — that game's window with its piece out on that
     * library shell (this time only, nothing saved), held at that angle: for checking the cartridge bottoms.
     */
    val inspect = mutableStateOf<String?>(null)

    fun fromIntent(context: android.content.Context, intent: android.content.Intent?) {
        val want = intent?.getStringExtra("voltcap_test_layout")
        val g = intent?.getStringExtra("voltcap_test_game")
        val st = intent?.getStringExtra("voltcap_test_settings")
        val lk = intent?.getStringExtra("voltcap_test_look")
        val cat = intent?.getStringExtra("voltcap_test_category")
        val inst = intent?.getStringExtra("voltcap_test_install")
        val mc = intent?.getStringExtra("voltcap_test_metacritic")
        val so = intent?.getBooleanExtra("voltcap_test_sort", false) == true
        val ins = intent?.getStringExtra("voltcap_test_inspect")
        // Update 277: `--ez voltcap_test_pspfit true` — the fitted PSP shell made from <external files>/psp_in.webp into
        // psp_out.png there (to check DiscShellFixes without the pack).
        val pf = intent?.getBooleanExtra("voltcap_test_pspfit", false) == true
        // `--ez voltcap_test_preview true`: layouts asked for afterwards are shown for this session only (nothing saved).
        val pv = intent?.getBooleanExtra("voltcap_test_preview", false) == true
        if (want == null && g == null && st == null && lk == null && cat == null && !pv && inst == null && mc == null && !so && ins == null && !pf) return
        val on = runCatching { java.io.File(context.getExternalFilesDir(null), "voltcap_test_hooks").exists() }.getOrDefault(false)
        if (!on) return
        if (pv) previewOnly = true
        if (so) sort.value = true
        if (pf) context.getExternalFilesDir(null)?.let { d ->
            Thread { runCatching { com.shelf.launcher.data.DiscShellFixes.test(java.io.File(d, "psp_in.webp"), java.io.File(d, "psp_out.png")) }
                .onFailure { android.util.Log.e("VoltcapTest", "pspfit: ${it.message}") } }.start()
        }
        if (ins != null) inspect.value = ins // (the shell ids have "_" — only the game's name part gets spaces, below)
        // 0.6.15: load a Metacritic scores file from a path (the Sort window's file picker, without the picker).
        if (mc != null) {
            val app = context.applicationContext
            Thread { runCatching { com.shelf.launcher.data.MetacriticFile.import(app, android.net.Uri.fromFile(java.io.File(mc))) }
                .onFailure { android.util.Log.e("VoltcapTest", "metacritic: ${it.message}") }
                .onSuccess { android.util.Log.e("VoltcapTest", "metacritic: $it scores") } }.start()
        }
        // 0.6.15: the updater's install path with a local voltcap.apk (+ voltcap.dm) — Android still asks first.
        if (inst != null) com.shelf.launcher.data.AppUpdates.installForTest(context, java.io.File(inst))
        if (cat != null) category.value = cat.replace('_', ' ')
        if (lk != null && com.shelf.launcher.data.CardArtType.values().any { it.name == lk }) look.value = lk
        if (st != null) settings.value = st.replace('_', ' ')
        if (g != null) game.value = g.replace('_', ' ') // (adb: "Contra_III")
        if (want != null && com.shelf.launcher.data.HomeLayout.values().any { it.name == want }) layout.value = want
    }
}
