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

    /** Update 326: the flag file is there (read once): the test-only logging may run. */
    @Volatile private var enabledCache: Boolean? = null
    fun enabled(context: android.content.Context): Boolean = enabledCache ?: runCatching {
        java.io.File(context.getExternalFilesDir(null), "voltcap_test_hooks").exists()
    }.getOrDefault(false).also { enabledCache = it }
    /**
     * Update 329: `--es voltcap_test_tilewalk all` (or a comma list of tile ids) — every R1 tile shown here pressed and
     * checked on both screens (TileCheck), this session only. [lastTiles]: the tiles the R1 panel showed last.
     */
    val tileWalk = mutableStateOf<String?>(null)
    @Volatile var lastTiles: String? = null
    /** Update 329: during the tile walk, a song "playing" (nothing plays) so the music player and its tile show. */
    @Volatile var fakeNowPlaying: com.shelf.launcher.data.NowPlaying? = null
    /** A category to show (`--es voltcap_test_category ALL`, or a console's name): for the scroll-stutter runs. */
    val category = mutableStateOf<String?>(null)
    /** 0.6.15: open the Sort window (`--ez voltcap_test_sort true`). */
    val sort = mutableStateOf(false)
    /** Update 330 (phase 4): open the game window of the game you're on (`--ez voltcap_test_window true`) — never starts a game. */
    val window = mutableStateOf(false)
    /** Update 330 (phase 4): the round shortcuts for this session only (`--es voltcap_test_shortcuts TOP_LEFT`, or off). */
    val shortcuts = mutableStateOf<String?>(null)
    /**
     * Update 274: `--es voltcap_test_inspect "Game_name|shellId|yaw|pitch"` — that game's window with its piece out on that
     * library shell (this time only, nothing saved), held at that angle: for checking the cartridge bottoms.
     */
    val inspect = mutableStateOf<String?>(null)
    /** Update 301: `--es voltcap_test_artwork "Game name"` — that game's Artwork & details (for checking the full-scan slot). */
    val artwork = mutableStateOf<String?>(null)
    /**
     * Update 305: `--es voltcap_test_facts "Game name|USK 16|PEGI 12"` — that game's packaging facts replaced by these TEST
     * values for this session (nothing saved), to check where a generated back puts a rating badge and its details.
     */
    val facts = mutableStateOf<String?>(null)
    /**
     * Update 305: `--es voltcap_test_pick "Game name|Label|/sdcard/file.png"` — that file added as the game's picture for
     * that real-media slot, exactly as Artwork & details › Upload… does (copied into Voltcap, saved); "…|Label|clear"
     * removes it again. For checking how imported covers, wraps, labels and box sides fit without the file picker.
     */
    val pick = mutableStateOf<String?>(null)
    /**
     * Update 279: `--es voltcap_test_shell pack:<shape>` (your cartridge pack's shape, e.g. pack:gb) or `opt:<file>` (an
     * optical-system shell, e.g. opt:playstation) — every cartridge / disc game drawn on that custom shell, this session
     * only, nothing saved; `off` ends it. For the custom-shell walk. Also logs which packs are on the device.
     */
    val shell = mutableStateOf<String?>(null)
    /**
     * Update 294: `--es voltcap_test_pose "60"` — every solid cartridge on the home screen held at that turn (degrees; the
     * view's own turn and the sway left out), `"60|old"` the same drawn the old way (no smear between the side slices),
     * for before / after pictures of the sides; `off` ends it. This session only, nothing saved.
     */
    val pose = mutableStateOf<Pair<Float, Boolean>?>(null)

    fun fromIntent(context: android.content.Context, intent: android.content.Intent?) {
        val want = intent?.getStringExtra("voltcap_test_layout")
        val g = intent?.getStringExtra("voltcap_test_game")
        val st = intent?.getStringExtra("voltcap_test_settings")
        val lk = intent?.getStringExtra("voltcap_test_look")
        val cat = intent?.getStringExtra("voltcap_test_category")
        val inst = intent?.getStringExtra("voltcap_test_install")
        val mc = intent?.getStringExtra("voltcap_test_metacritic")
        val so = intent?.getBooleanExtra("voltcap_test_sort", false) == true
        val wi = intent?.getBooleanExtra("voltcap_test_window", false) == true
        val rs = intent?.getStringExtra("voltcap_test_shortcuts")
        val ins = intent?.getStringExtra("voltcap_test_inspect")
        val aw = intent?.getStringExtra("voltcap_test_artwork")
        val tf = intent?.getStringExtra("voltcap_test_facts")
        val tp = intent?.getStringExtra("voltcap_test_pick")
        // Update 277: `--ez voltcap_test_pspfit true` — the fitted PSP shell made from <external files>/psp_in.webp into
        // psp_out.png there (to check DiscShellFixes without the pack).
        val pf = intent?.getBooleanExtra("voltcap_test_pspfit", false) == true
        val sh = intent?.getStringExtra("voltcap_test_shell")
        val po = intent?.getStringExtra("voltcap_test_pose")
        // Update 280: `--es voltcap_test_pref "key=value"` — one setting (saved, as Settings would; logged with its old value).
        val pr = intent?.getStringExtra("voltcap_test_pref")
        // Update 280: `--es voltcap_test_snapshot save|restore` — every Features setting kept in <external files>/
        // voltcap_test_snapshot.json and put back afterwards, so a test walk leaves the settings as they were.
        val snap = intent?.getStringExtra("voltcap_test_snapshot")
        // Update 281: `--es voltcap_test_labelaudit <systemeid|all>` — every game's saved real-media list (ScreenScraper's
        // "support-texture" = Disc label, "support-2D" = Disc / cartridge): which it has, and those pictures downloaded
        // into <external files>/label_audit/ for checking the label pipeline. Only ids, never the account, are written.
        val la = intent?.getStringExtra("voltcap_test_labelaudit")
        // `--ez voltcap_test_preview true`: layouts asked for afterwards are shown for this session only (nothing saved).
        val pv = intent?.getBooleanExtra("voltcap_test_preview", false) == true
        val tw = intent?.getStringExtra("voltcap_test_tilewalk_all")?.let { "ALL:$it" } ?: intent?.getStringExtra("voltcap_test_tilewalk")
        if (tw != null && runCatching { java.io.File(context.getExternalFilesDir(null), "voltcap_test_hooks").exists() }.getOrDefault(false)) {
            // (A walk only ever previews: nothing it changes is saved.)
            previewOnly = true
            tileWalk.value = tw
        }
        if (want == null && g == null && st == null && lk == null && cat == null && !pv && inst == null && mc == null && !so && !wi && rs == null && ins == null && !pf && sh == null && pr == null && snap == null && la == null && po == null && aw == null && tf == null && tp == null) return
        val on = runCatching { java.io.File(context.getExternalFilesDir(null), "voltcap_test_hooks").exists() }.getOrDefault(false)
        if (!on) return
        if (pv) previewOnly = true
        if (so) sort.value = true
        if (wi) window.value = true
        if (rs != null) { previewOnly = true; shortcuts.value = rs }
        if (snap != null) runCatching {
            val f = java.io.File(context.getExternalFilesDir(null), "voltcap_test_snapshot.json")
            if (snap == "save") {
                f.writeText(org.json.JSONObject(com.shelf.launcher.data.Features.snapshot() as Map<*, *>).toString())
                android.util.Log.e("VoltcapTest", "snapshot saved")
            } else if (snap == "restore" && f.exists()) {
                val j = org.json.JSONObject(f.readText())
                com.shelf.launcher.data.Features.restore(j.keys().asSequence().associateWith { j.getString(it) })
                android.util.Log.e("VoltcapTest", "snapshot restored")
            }
        }.onFailure { android.util.Log.e("VoltcapTest", "snapshot: ${it.message}") }
        if (la != null) {
            val app = context.applicationContext
            Thread { runCatching { LabelAudit.run(app, la) }.onFailure { android.util.Log.e("VoltcapTest", "labelaudit: ${it.message}") } }.start()
        }
        if (pr != null && '=' in pr) {
            val k = pr.substringBefore('='); val v = pr.substringAfter('=')
            android.util.Log.e("VoltcapTest", "pref $k was ${com.shelf.launcher.data.Features.snapshot()[k]} -> $v")
            com.shelf.launcher.data.Features.restore(mapOf(k to v))
        }
        if (po != null) {
            pose.value = if (po == "off") null else po.substringBefore('|').toFloatOrNull()?.let { it to po.endsWith("|old") }
            android.util.Log.e("VoltcapTest", "pose=${pose.value}")
        }
        if (sh != null) {
            shell.value = sh.takeIf { it != "off" }
            android.util.Log.e("VoltcapTest", "shell=${shell.value} packs=${com.shelf.launcher.data.Packs.debugHave()}")
        }
        if (pf) context.getExternalFilesDir(null)?.let { d ->
            Thread { runCatching {
                if (java.io.File(d, "psp_in.webp").exists()) com.shelf.launcher.data.DiscShellFixes.test(java.io.File(d, "psp_in.webp"), java.io.File(d, "psp_out.png"))
                if (java.io.File(d, "segacd_in.webp").exists()) com.shelf.launcher.data.DiscShellFixes.testSegaCd(context.applicationContext, java.io.File(d, "segacd_in.webp"), java.io.File(d, "segacd_out.png"))
            }
                .onFailure { android.util.Log.e("VoltcapTest", "pspfit: ${it.message}") } }.start()
        }
        if (aw != null) artwork.value = aw
        if (tf != null) facts.value = tf
        if (tp != null) pick.value = tp
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

/** Update 281, testing only: see TestHooks `voltcap_test_labelaudit`. */
internal object LabelAudit {
    private fun param(url: String, name: String): String =
        Regex("[?&]$name=([^&]*)").find(url)?.groupValues?.get(1).orEmpty()

    fun run(c: android.content.Context, system: String) {
        // (Update 298, testing only: "scans:<part of a file name>" — every saved scan of those games (front, back, spine,
        // full scan…) downloaded into label_audit/, and their addresses (without any login) in scans.tsv: for checking
        // which edition's back a game really has.)
        if (system.startsWith("scans:")) { scans(c, system.removePrefix("scans:")); return }
        val prefs = c.getSharedPreferences("physical_media", android.content.Context.MODE_PRIVATE)
        val dir = java.io.File(c.getExternalFilesDir(null), "label_audit").apply { mkdirs() }
        val client = okhttp3.OkHttpClient.Builder().readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
        val rows = StringBuilder("key\tsystem\tjeu\thas_label\thas_cart\tlabel_media\tcart_media\ttypes\n")
        var n = 0
        for ((key, v) in prefs.all) {
            val text = v as? String ?: continue
            val list = text.split('\n').mapNotNull { l -> l.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            val label = list.firstOrNull { it.first == "Disc label" }?.second
            val cart = list.firstOrNull { it.first == "Disc / cartridge" }?.second
            val any = label ?: cart ?: list.firstOrNull()?.second ?: continue
            val sys = param(any, "systemeid")
            if (system != "all" && sys != system) continue
            val jeu = param(any, "jeuid")
            rows.append("$key\t$sys\t$jeu\t${label != null}\t${cart != null}\t${label?.let { param(it, "media") }.orEmpty()}\t${cart?.let { param(it, "media") }.orEmpty()}\t${list.joinToString(",") { it.first }}\n")
            val safe = key.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
            for ((tag, url) in listOf("label" to label, "cart" to cart)) {
                if (url == null) continue
                val f = java.io.File(dir, "${safe}_$tag.png")
                if (f.exists()) continue
                runCatching {
                    client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { r ->
                        if (r.isSuccessful) r.body?.byteStream()?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                    }
                }
                Thread.sleep(400)
            }
            n++
        }
        java.io.File(dir, "index.tsv").writeText(rows.toString())
        android.util.Log.e("VoltcapTest", "labelaudit: $n games")
    }

    private fun scans(c: android.content.Context, part: String) {
        val prefs = c.getSharedPreferences("physical_media", android.content.Context.MODE_PRIVATE)
        val dir = java.io.File(c.getExternalFilesDir(null), "label_audit").apply { mkdirs() }
        val client = okhttp3.OkHttpClient.Builder().readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
        val rows = StringBuilder("key\tkind\tregion\tmedia\tjeu\tsystem\n")
        for ((key, v) in prefs.all) {
            if (!key.contains(part, ignoreCase = true)) continue
            val text = v as? String ?: continue
            val safe = key.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
            for (l in text.split('\n')) {
                val kv = l.split('\t').takeIf { it.size == 2 } ?: continue
                val (kind, url) = kv[0] to kv[1]
                rows.append("$key\t$kind\t${param(url, "region")}\t${param(url, "media")}\t${param(url, "jeuid")}\t${param(url, "systemeid")}\n")
                val f = java.io.File(dir, "${safe}_${kind.replace(Regex("[^A-Za-z0-9]"), "")}.png")
                if (f.exists() || !url.startsWith("http")) continue
                runCatching {
                    client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { r ->
                        if (r.isSuccessful) r.body?.byteStream()?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                    }
                }
                Thread.sleep(400)
            }
        }
        java.io.File(dir, "scans.tsv").writeText(rows.toString())
        android.util.Log.e("VoltcapTest", "labelaudit scans: done")
    }
}
