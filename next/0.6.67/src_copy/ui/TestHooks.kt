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
    /** Update 432: `--es voltcap_test_bg ANIMATED|ART|…` — the home background style for this session only (nothing saved). */
    val bg = mutableStateOf<String?>(null)
    /**
     * A game look shown for this session only (`--es voltcap_test_look CASE`); while it's on, the layout hook only shows
     * layouts too (nothing saved). A restart of Voltcap brings your own settings back.
     */
    val look = mutableStateOf<String?>(null)
    @Volatile var previewOnly = false

    /** Update 326: the flag file is there (read once): the test-only logging may run. */
    @Volatile private var enabledCache: Boolean? = null
    /**
     * Update 447 (D144): which screen is in front, for tools/qa/device/picker.sh — "PICKER <pid>" while a "Who's playing?"
     * screen is up, "HOME <pid>" after it closes. Written only with the hooks file; QA reads it instead of guessing the
     * screen from its colours (the picker has six looks now).
     */
    fun markScreen(context: android.content.Context, screen: String) {
        if (!enabled(context)) return
        runCatching {
            java.io.File(context.getExternalFilesDir(null), "voltcap_qa_screen").writeText("$screen ${android.os.Process.myPid()}\n")
        }
    }

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
    /** v0.6.66 (D123): `--ez voltcap_test_scenecover false` — the scene's colours from the cover off/on for this preview only. */
    val sceneCover = mutableStateOf<Boolean?>(null)
    /** v0.6.66 (D137): `--ez voltcap_test_novideo true|false` — the home screen's gameplay clips off for this session (frame-pacing A/B runs; nothing saved). */
    val noVideo = mutableStateOf(false)
    /** Update 456: `--ez voltcap_test_panelhold true|false` — the background held under the drawer / Settings (on by default; session only, for A/B runs). */
    val panelHold = mutableStateOf(true)
    /** Update 457: `--ez voltcap_test_settingsflat true|false` — the Settings window without its unseen drop-shadow (on by default; session, A/B). */
    val settingsFlat = mutableStateOf(true)
    /** Update 457: `--ez voltcap_test_lookahead true|false` — Settings keeps the next row in view while ▲ ▼ move focus (on by default; session, A/B). */
    val settingsLookAhead = mutableStateOf(true)
    /** Update 457 (QA measurement only): `--ei voltcap_test_surfaceprobe 0|1|2` — panels drawn plain (1) or plain without a clip (2). */
    val surfaceProbe = androidx.compose.runtime.mutableIntStateOf(0)
    /** Update 456: `--es voltcap_test_tabnames "GBA=A very long name|All=Ünïcødé 名前"` — Pocket Quest tab names for this session (key: console or label). */
    val tabNames = mutableStateOf<Map<String, String>>(emptyMap())
    /**
     * v0.6.67: `--es voltcap_test_pickerlook AURORA|CONSOLE|NEON|OLED|THEME|MIDNIGHT|off` — "Who's playing?" in that look for this
     * session (nothing saved).
     */
    val pickerLook = mutableStateOf<com.shelf.launcher.data.Features.PickerLook?>(null)
    /**
     * v0.6.67: `--es voltcap_test_profiles "N[:long][:nophoto][:locked][:active=K]"` — "Who's playing?" with N sample profiles
     * (1–8) instead of yours, as a preview: picking, editing, PIN and remove change nothing (ProfileSelectScreen demo).
     */
    val profilesDemo = mutableStateOf<List<com.shelf.launcher.data.Profiles.Profile>?>(null)
    val profilesDemoActive = mutableStateOf(0)

    /** The sample profiles for [profilesDemo] (pure, unit-tested): ids 900+, colours from the presets, real profiles untouched. */
    fun demoProfiles(spec: String): Pair<List<com.shelf.launcher.data.Profiles.Profile>, Int> {
        val parts = spec.split(":")
        val n = parts[0].toIntOrNull()?.coerceIn(1, com.shelf.launcher.data.Profiles.MAX) ?: 3
        val long = "long" in parts
        val locked = "locked" in parts
        val active = parts.firstOrNull { it.startsWith("active=") }?.substringAfter("=")?.toIntOrNull()?.coerceIn(0, n - 1) ?: 0
        val names = listOf("Toni", "Mia", "Alexander the Great Gamer", "Kai", "Speedrunner Supreme", "Lena", "Grandpa Joe", "Sam")
        val longNames = listOf("Maximilian-Alexander", "The Very Long Player Name", "Christopher Columbus", "Speedrunner Supreme 2000",
            "Anastasia-Katharina", "Grandmaster Flash Gordon", "Bartholomew", "Wolfgang Amadeus")
        val list = (0 until n).map { i ->
            com.shelf.launcher.data.Profiles.Profile(
                900 + i, (if (long) longNames else names)[i], com.shelf.launcher.data.Profiles.Picture.INITIAL, null,
                PickerGrid.PRESETS[(i * 5) % PickerGrid.PRESETS.size],
                if (locked && i == 1) "demo" else null
            )
        }
        return list to (900 + active)
    }
    /** v0.6.66 (D135): launches are recorded, not performed (`voltcap_test_nolaunch`). */
    @Volatile var noLaunch = false
    /** Logs who asked for a launch (QA builds with the hooks); true when it must not happen ([noLaunch]). */
    fun interceptLaunch(where: String, key: String): Boolean {
        if (!noLaunch && !QaTrace.on) return false
        val who = Throwable().stackTrace.drop(1).take(14).joinToString(" < ") { "${it.fileName}:${it.lineNumber}" }
        android.util.Log.e("VoltcapQA", "LAUNCH ${if (noLaunch) "blocked" else "asked"} $where $key popup=${GamePopup.game?.stableKey} by $who")
        return noLaunch
    }
    /** Update 330 (phase 4): the round shortcuts for this session only (`--es voltcap_test_shortcuts TOP_LEFT`, or off). */
    val shortcuts = mutableStateOf<String?>(null)
    /** Pass 5: `--es voltcap_test_theme LOBBY` — that theme (saved; the old one is logged "theme old=…" to put back). */
    val theme = mutableStateOf<String?>(null)
    /** Pass 5: `--es voltcap_test_perf LITE|FULL|AUTO` — performance mode for this session only (nothing saved). */
    val perf = mutableStateOf<String?>(null)
    /** Pass 5: counts previews ended (`--ez voltcap_test_preview false`); the launcher screen reloads your appearance. */
    val previewEnded = androidx.compose.runtime.mutableIntStateOf(0)
    /**
     * Update 274: `--es voltcap_test_inspect "Game_name|shellId|yaw|pitch"` — that game's window with its piece out on that
     * library shell (this time only, nothing saved), held at that angle: for checking the cartridge bottoms.
     */
    val inspect = mutableStateOf<String?>(null)
    /** Update 460b (D173): `--es voltcap_test_mediadump "part of a name"` — what each matching game's case is drawn from, into <external files>/media_dump.txt. */
    val mediaDump = mutableStateOf<String?>(null)
    /** Update 461 (D137 A/B): `--es voltcap_test_warm off|noprepare|on` — the case layouts' decoding ahead (WarmCaseNeighbours). */
    @Volatile var warmMode: String = "on"
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

    /** Update 431: the test tuner profile for [spec] (Result.success(null) = "off"), or null when it doesn't parse. */
    internal fun tuneProfile(spec: String): Result<com.shelf.launcher.device.tune.TuneProfile?>? {
        val T = com.shelf.launcher.device.tune.TuneProfile()
        val parts = spec.split(":")
        return when (parts[0]) {
            "off" -> Result.success(null)
            "stock" -> Result.success(T.copy(underclock = com.shelf.launcher.device.tune.Underclock.OFF))
            "uc" -> Result.success(T.copy(underclock = com.shelf.launcher.device.tune.Underclock.valueOf(parts[1])))
            "tier" -> com.shelf.launcher.device.tune.PowerTier.valueOf(parts[1]).let { Result.success(T.copy(tier = it, governor = it.governor)) }
            "auto" -> Result.success(T.copy(autoTdp = true, autoMode = com.shelf.launcher.device.tune.AutoTdpMode.valueOf(parts[1]),
                targetFps = parts.getOrNull(2)?.toIntOrNull() ?: 0))
            else -> null
        }
    }

    private fun com.shelf.launcher.device.tune.TuneProfile.summaryForQa() =
        "tier=$tier uc=$underclock auto=$autoTdp ${if (autoTdp) "$autoMode target=$targetFps" else ""}"

    /**
     * Update 450: what Voltcap's own state says about an app (set by the view model): game or app, in the library,
     * hidden, in Continue playing… For `voltcap_test_appinfo`.
     */
    @Volatile var appProbe: ((String) -> String)? = null
    /** Update 452: `--es voltcap_test_winmore on` — the open game window's More opened (as its button does), for the More QA. */
    val winMore = mutableStateOf<Boolean?>(null)

    /**
     * Update 451: the home screen's own state for QA snapshots — selected category (console or special list), the focused
     * game, the playing profile and the remembered last category (set by the view model). [restoreViewState] puts a saved
     * one back on the main thread and returns what it verified ("OK …" or "MISMATCH …").
     */
    @Volatile var viewState: (() -> org.json.JSONObject)? = null
    @Volatile var restoreViewState: ((org.json.JSONObject, (String) -> Unit) -> Unit)? = null

    /**
     * Update 450: `--es voltcap_test_appinfo pkg1,pkg2` — READ-ONLY: writes how Voltcap classifies each app (its own
     * state + Android's game flag) to `<external files>/voltcap_qa_appinfo.txt`. Changes nothing.
     */
    private fun appInfo(context: android.content.Context, pkgs: List<String>) {
        val pm = context.packageManager
        val lines = pkgs.map { pkg ->
            val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
            @Suppress("DEPRECATION")
            val androidGame = info != null && ((android.os.Build.VERSION.SDK_INT >= 26 && info.category == android.content.pm.ApplicationInfo.CATEGORY_GAME) ||
                (info.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0)
            val hidden = runCatching { pkg in com.shelf.launcher.data.HiddenAppsStore(context).loadAll() }.getOrDefault(false)
            "$pkg installed=${info != null} android_game_flag=$androidGame hidden=$hidden " + (appProbe?.invoke(pkg) ?: "voltcap_state=unavailable")
        }
        runCatching { java.io.File(context.getExternalFilesDir(null), "voltcap_qa_appinfo.txt").writeText(lines.joinToString("\n", postfix = "\n")) }
        lines.forEach { android.util.Log.e("VoltcapQA", "appinfo $it") }
    }

    fun fromIntent(context: android.content.Context, intent: android.content.Intent?) {
        intent?.getStringExtra("voltcap_test_winmore")?.let { v -> if (enabled(context)) winMore.value = v == "on" }
        intent?.getStringExtra("voltcap_test_appinfo")?.let { v ->
            if (enabled(context)) appInfo(context, v.split(',').map { it.trim() }.filter { it.isNotEmpty() })
        }
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
        intent?.getStringExtra("voltcap_test_mediadump")?.let { if (enabled(context)) mediaDump.value = it }
        intent?.getStringExtra("voltcap_test_warm")?.let { if (enabled(context)) warmMode = it }
        // Update 462: `--ez voltcap_test_photoshells true` — the photo-made shells' templates into <external files>/photoshells
        // (update 463: every supported shell looked at first, and the coverage into photoshells/inventory.tsv; send it again once
        // the photos are analysed).
        if (intent?.getBooleanExtra("voltcap_test_photoshells", false) == true && enabled(context)) { val c = context.applicationContext; Thread { PhotoShells.analyseAll(); PhotoShells.inventory(c); PhotoShells.dump(c) }.start() }
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
        val th = intent?.getStringExtra("voltcap_test_theme")
        val pm = intent?.getStringExtra("voltcap_test_perf")
        intent?.getStringExtra("voltcap_test_bg")?.let { if (enabled(context)) { previewOnly = true; bg.value = it } }
        // Update 431: `--es voltcap_test_tune stock|uc:MEDIUM|auto:EFFICIENT[:60]|tier:SAVER|off` — a tuner profile for this
        // process only, instead of every saved one (nothing saved, nothing learned; `off` puts yours back). Hardware comparisons.
        intent?.getStringExtra("voltcap_test_tune")?.let { t ->
            if (enabled(context)) runCatching { tuneProfile(t) }.getOrNull().let { r ->
                if (r != null || t == "off") com.shelf.launcher.device.tune.Tuner.setTestProfile(context.applicationContext, r?.getOrNull())
                android.util.Log.i("VoltcapTdp", "test profile $t -> ${r?.getOrNull()?.summaryForQa() ?: "saved profiles"}")
            }
        }
        // Update 280: `--es voltcap_test_snapshot save|restore` — every Features setting kept in <external files>/
        // voltcap_test_snapshot.json and put back afterwards, so a test walk leaves the settings as they were.
        val snap = intent?.getStringExtra("voltcap_test_snapshot")
        // QA: `--ez voltcap_test_churn true|false` — the state-churn log (QaTrace.watchChurn), only while asked for.
        // QA pass 3: `--ez voltcap_test_walk true|false` — the controller walk log (QaWalk.kt).
        // QA pass 3: `--es voltcap_test_explore "SELECT"` — the controller explorer (QaExplore.kt), with the hooks.
        intent?.getStringExtra("voltcap_test_explore")?.let { QaExplore.start(it) }
        if (intent?.hasExtra("voltcap_test_walk") == true) QaWalk.set(intent.getBooleanExtra("voltcap_test_walk", false))
        // QA update 370: `--ez voltcap_test_geom true|false` — the geometry trace (QaGeom.kt), only while asked for.
        if (intent?.hasExtra("voltcap_test_geom") == true) QaGeom.set(intent.getBooleanExtra("voltcap_test_geom", false))
        // Update 460: `--ez voltcap_test_genlabels true|false` — update 459's generated label designs back on for this session (comparison sheets only).
        if (intent?.hasExtra("voltcap_test_genlabels") == true && enabled(context)) GeneratedLabels.enabled = intent.getBooleanExtra("voltcap_test_genlabels", false)
        if (intent?.hasExtra("voltcap_test_churn") == true) QaTrace.watchChurn(intent.getBooleanExtra("voltcap_test_churn", false))
        // QA self-test: `--es voltcap_test_fault "counter,flash:20"` (QaFault.kt) — this session only, nothing saved.
        intent?.getStringExtra("voltcap_test_fault")?.let { f ->
            if (runCatching { java.io.File(context.getExternalFilesDir(null), "voltcap_test_hooks").exists() }.getOrDefault(false)) QaFault.set(f)
        }
        // Update 281: `--es voltcap_test_labelaudit <systemeid|all>` — every game's saved real-media list (ScreenScraper's
        // "support-texture" = Disc label, "support-2D" = Disc / cartridge): which it has, and those pictures downloaded
        // into <external files>/label_audit/ for checking the label pipeline. Only ids, never the account, are written.
        val la = intent?.getStringExtra("voltcap_test_labelaudit")
        // `--ez voltcap_test_preview true`: layouts asked for afterwards are shown for this session only (nothing saved).
        // v0.6.66 (D135): `--ez voltcap_test_nolaunch true|false` — a game Voltcap would start is only logged (VoltcapQA "LAUNCH
        // blocked", with who asked), never started: rapid-input popup runs without opening emulators or touching saves.
        if (intent?.hasExtra("voltcap_test_nolaunch") == true && enabled(context)) {
            noLaunch = intent.getBooleanExtra("voltcap_test_nolaunch", false)
            android.util.Log.e("VoltcapQA", "nolaunch=$noLaunch")
        }
        intent?.getStringExtra("voltcap_test_pickerlook")?.let { v ->
            if (enabled(context)) pickerLook.value = com.shelf.launcher.data.Features.PickerLook.values().firstOrNull { it.name == v }
        }
        intent?.getStringExtra("voltcap_test_profiles")?.let { v ->
            if (enabled(context)) {
                if (v == "off") profilesDemo.value = null
                else demoProfiles(v).let { (l, a) -> profilesDemoActive.value = a; profilesDemo.value = l }
                android.util.Log.e("VoltcapQA", "profiles demo $v")
            }
        }
        intent?.getStringExtra("voltcap_test_tabnames")?.let { v ->
            if (enabled(context)) tabNames.value = v.split('|').mapNotNull { e -> e.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1] } }.toMap()
        }
        if (intent?.hasExtra("voltcap_test_surfaceprobe") == true && enabled(context)) surfaceProbe.intValue = intent.getIntExtra("voltcap_test_surfaceprobe", 0)
        if (intent?.hasExtra("voltcap_test_lookahead") == true && enabled(context)) {
            settingsLookAhead.value = intent.getBooleanExtra("voltcap_test_lookahead", true)
            android.util.Log.e("VoltcapQA", "lookahead=${settingsLookAhead.value}")
        }
        if (intent?.hasExtra("voltcap_test_settingsflat") == true && enabled(context)) {
            settingsFlat.value = intent.getBooleanExtra("voltcap_test_settingsflat", true)
            android.util.Log.e("VoltcapQA", "settingsflat=${settingsFlat.value}")
        }
        if (intent?.hasExtra("voltcap_test_panelhold") == true && enabled(context)) {
            panelHold.value = intent.getBooleanExtra("voltcap_test_panelhold", true)
            android.util.Log.e("VoltcapQA", "panelhold=${panelHold.value}")
        }
        if (intent?.hasExtra("voltcap_test_novideo") == true && enabled(context)) {
            noVideo.value = intent.getBooleanExtra("voltcap_test_novideo", false)
            android.util.Log.e("VoltcapQA", "novideo=${noVideo.value}")
        }
        if (intent?.hasExtra("voltcap_test_scenecover") == true && enabled(context)) {
            previewOnly = true; sceneCover.value = intent.getBooleanExtra("voltcap_test_scenecover", false)
        }
        val pv = intent?.getBooleanExtra("voltcap_test_preview", false) == true
        // Pass 5: `--ez voltcap_test_preview false` ends a preview: your saved appearance back on screen, saving again.
        // (QA: a run's preview lasted until Voltcap restarted — the layout tried stayed, and nothing you changed was saved.)
        val pvEnd = intent?.hasExtra("voltcap_test_preview") == true && !pv
        // Update 336: `--ei voltcap_test_setup <step>` — the first-start setup opened on that step (0 = Welcome).
        val su = intent?.getIntExtra("voltcap_test_setup", -1) ?: -1
        if (su >= 0 && runCatching { java.io.File(context.getExternalFilesDir(null), "voltcap_test_hooks").exists() }.getOrDefault(false)) {
            SetupJump.step = su
            VoltHub.replay = true
        }
        // Update 423: `--ez voltcap_test_demo true|false` — the made-up stand-in library for preview pictures (DemoLibrary.kt).
        if (intent?.hasExtra("voltcap_test_demo") == true && runCatching { java.io.File(context.getExternalFilesDir(null), "voltcap_test_hooks").exists() }.getOrDefault(false))
        {
            val on = intent.getBooleanExtra("voltcap_test_demo", false)
            DemoLibrary.on.value = on
            val app = context.applicationContext
            if (on) DemoArt.prepare(app) { dir -> android.os.Handler(android.os.Looper.getMainLooper()).post { if (DemoLibrary.on.value) DemoLibrary.artDir.value = dir } }
            else { DemoLibrary.artDir.value = null; DemoArt.clear(app) }
        }
        val tw = intent?.getStringExtra("voltcap_test_tilewalk_all")?.let { "ALL:$it" } ?: intent?.getStringExtra("voltcap_test_tilewalk")
        if (tw != null && runCatching { java.io.File(context.getExternalFilesDir(null), "voltcap_test_hooks").exists() }.getOrDefault(false)) {
            // (A walk only ever previews: nothing it changes is saved.)
            previewOnly = true
            tileWalk.value = tw
        }
        // Update 447: `--es voltcap_test_twindash on|off` — a two-screen layout's "Second screen" dashboard opened / closed
        // (as its own button does; nothing saved), so QA can check the way back on every layout.
        intent?.getStringExtra("voltcap_test_twindash")?.let { v -> if (enabled(context)) TwinScreens.dashboard = v == "on" }
        if (want == null && g == null && st == null && lk == null && cat == null && !pv && inst == null && mc == null && !so && !wi && rs == null && ins == null && !pf && sh == null && pr == null && snap == null && la == null && po == null && aw == null && tf == null && tp == null && th == null && pm == null && !pvEnd) return
        val on = runCatching { java.io.File(context.getExternalFilesDir(null), "voltcap_test_hooks").exists() }.getOrDefault(false)
        if (!on) return
        if (pv) previewOnly = true
        if (pvEnd) { previewOnly = false; previewEnded.intValue = previewEnded.intValue + 1 }
        if (so) sort.value = true
        if (wi) window.value = true
        if (rs != null) { previewOnly = true; shortcuts.value = rs }
        if (snap != null) runCatching {
            val f = java.io.File(context.getExternalFilesDir(null), "voltcap_test_snapshot.json")
            val vf = java.io.File(context.getExternalFilesDir(null), "voltcap_test_snapshot_view.json")
            val verify = java.io.File(context.getExternalFilesDir(null), "voltcap_test_snapshot_verify.txt")
            if (snap == "save") {
                f.writeText(org.json.JSONObject(com.shelf.launcher.data.Features.snapshot() as Map<*, *>).toString())
                // Update 451: the selected category, focused game and profile too (a test once left the Thor on an empty category).
                viewState?.invoke()?.let { vf.writeText(it.toString()) }
                verify.delete()
                // QA pass 3: the category order / hidden list too (a walk once pressed A on a ▼ in Settings › Categories).
                val cp = context.getSharedPreferences("categories", android.content.Context.MODE_PRIVATE)
                java.io.File(context.getExternalFilesDir(null), "voltcap_test_snapshot_categories.txt")
                    .writeText((cp.getString("order", "") ?: "") + "\n" + (cp.getStringSet("hidden", null)?.joinToString(",") ?: "\u0000"))
                android.util.Log.e("VoltcapTest", "snapshot saved")
            } else if (snap == "restore" && f.exists()) {
                val j = org.json.JSONObject(f.readText())
                com.shelf.launcher.data.Features.restore(j.keys().asSequence().associateWith { j.getString(it) })
                val cf = java.io.File(context.getExternalFilesDir(null), "voltcap_test_snapshot_categories.txt")
                if (cf.exists()) {
                    val l = cf.readText().split('\n')
                    val e = context.getSharedPreferences("categories", android.content.Context.MODE_PRIVATE).edit()
                    if (l[0].isBlank()) e.remove("order") else e.putString("order", l[0])
                    val h = l.getOrNull(1)
                    if (h == null || h == "\u0000") e.remove("hidden") else e.putStringSet("hidden", h.split(',').filter { it.isNotBlank() }.toSet())
                    e.commit()   // (the walk then restarts Voltcap — vqa.sh hooks off — so the screen reads it again)
                }
                // Update 451: the selected category and focused game back, then checked (the result in voltcap_test_snapshot_verify.txt).
                if (vf.exists()) restoreViewState?.invoke(org.json.JSONObject(vf.readText())) { result ->
                    runCatching { verify.writeText(result + "\n") }
                    android.util.Log.e("VoltcapTest", "view state $result")
                }
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
        if (th != null && com.shelf.launcher.ui.theme.AppTheme.values().any { it.name == th }) theme.value = th
        if (pm != null && com.shelf.launcher.data.PerformanceMode.values().any { it.name == pm }) perf.value = pm
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
        // (Update 338, testing only: "cases" — no downloads: every game's saved real-media kinds, the console each scan is
        // from (systemeid) and whether a cover was picked for it — in label_audit/cases.tsv.)
        if (system == "cases") { cases(c); return }
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

    private fun cases(c: android.content.Context) {
        val prefs = c.getSharedPreferences("physical_media", android.content.Context.MODE_PRIVATE)
        val dir = java.io.File(c.getExternalFilesDir(null), "label_audit").apply { mkdirs() }
        UserCovers.ensure(c)
        val rows = StringBuilder("key\tbox_sys\tbox_region\tfull_sys\tpicked_cover\tkinds\n")
        for ((key, v) in prefs.all) {
            val text = v as? String ?: continue
            val list = text.split('\n').mapNotNull { l -> l.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            val box = list.firstOrNull { it.first == "Box" }?.second
            val full = list.firstOrNull { it.first == BoxTexture.LABEL }?.second
            rows.append("$key\t${box?.let { param(it, "systemeid") }.orEmpty()}\t${box?.let { param(it, "region") }.orEmpty()}\t" +
                "${full?.let { param(it, "systemeid") }.orEmpty()}\t${UserCovers.of(key) != null}\t${list.joinToString(",") { it.first }}\n")
        }
        java.io.File(dir, "cases.tsv").writeText(rows.toString())
        android.util.Log.e("VoltcapTest", "labelaudit cases: ${prefs.all.size}")
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
