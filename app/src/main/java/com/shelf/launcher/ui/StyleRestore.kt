package com.shelf.launcher.ui

/**
 * Update 330 (phase 4): where the controller was in the layouts that have pages of their own — the Cube menu (big cube or
 * its games, the face it's turned to; the Hub's part and zone), Green Core, Pocket Quest and Pocket classic — kept when
 * Voltcap goes to the background (a game starting) and put back when it comes back from a game in a new process (Android
 * closed it for memory while the game ran). The game you're on and the category come back on their own (LaunchContext);
 * without this the Cube menu came back as the big cube, Green Core on its menu, and so on.
 *
 * Plain JSON in the "session" prefs (LauncherViewModel): written on every pause, read once at start when it's a quick
 * return (within 30 min, as the start category's "resuming" rule) or a return from Voltcap's own launch.
 */
internal object StyleRestore {
    fun snapshot(): String = runCatching {
        org.json.JSONObject().apply {
            put("cube_page", CubeNav.page.name); put("cube_ring", CubeNav.ring); put("cube_tip", CubeNav.tip)
            put("hub_section", CubeHub.section); put("hub_zone", CubeHub.zone); put("hub_sys", CubeHub.sysFocus)
            put("gc_page", GreenCoreNav.page.name); put("gc_menu", GreenCoreNav.menu); put("gc_system", GreenCoreNav.system)
            put("gc_from_systems", GreenCoreNav.gamesFromSystems)
            put("pq_page", PqNav.page.name); put("pq_zone", PqNav.zone.name); put("pq_sys", PqNav.sysCursor)
            put("pc_page", PocketNav.page.name); put("pc_spot", PocketNav.spot); put("pc_cursor", PocketNav.cursor)
            put("pc_from_all", PocketNav.gamesFromAll)
            // Update 344: Blades.
            put("bl_blade", BladesNav.blade); put("bl_spot", BladesNav.spotRow); put("bl_sys", BladesNav.sysRow)
            put("bl_media", BladesNav.mediaRow); put("bl_set", BladesNav.setRow); put("bl_from_systems", BladesNav.gamesFromSystems)
            put("bv_view", BubbleNav.view.name); put("bv_folder", BubbleNav.folder); put("bv_from_folders", BubbleNav.fromFolders)
            put("pc_source", CameraNav.source)
            put("tm_dir", TermNav.dir.name); put("tm_root", TermNav.rootSel); put("tm_sys", TermNav.sysSel)
            put("pd_channel", PanelNav.channel); put("pd_spot", PanelNav.spotCol); put("pd_sys", PanelNav.sysCol)
            put("pd_media", PanelNav.mediaCol); put("pd_set", PanelNav.setCol); put("pd_from_systems", PanelNav.gamesFromSystems)
        }.toString()
    }.getOrDefault("")

    fun apply(json: String?) {
        if (json.isNullOrBlank()) return
        runCatching {
            val j = org.json.JSONObject(json)
            fun <E : Enum<E>> enumOf(values: Array<E>, key: String): E? = j.optString(key).let { n -> values.firstOrNull { it.name == n } }
            enumOf(CubeNav.Page.values(), "cube_page")?.let { CubeNav.page = it }
            if (j.has("cube_ring")) CubeNav.ring = j.optInt("cube_ring")
            if (j.has("cube_tip")) CubeNav.tip = j.optInt("cube_tip").coerceIn(-1, 1)
            if (j.has("hub_section")) CubeHub.section = j.optInt("hub_section").coerceIn(0, 3)
            if (j.has("hub_zone")) CubeHub.zone = j.optInt("hub_zone")
            if (j.has("hub_sys")) CubeHub.sysFocus = j.optInt("hub_sys").coerceAtLeast(0)
            enumOf(GreenCoreNav.Page.values(), "gc_page")?.let { GreenCoreNav.page = it }
            if (j.has("gc_menu")) GreenCoreNav.menu = j.optInt("gc_menu").coerceAtLeast(0)
            if (j.has("gc_system")) GreenCoreNav.system = j.optInt("gc_system").coerceAtLeast(0)
            GreenCoreNav.gamesFromSystems = j.optBoolean("gc_from_systems")
            enumOf(PqNav.Page.values(), "pq_page")?.let { PqNav.page = it }
            enumOf(PqNav.Zone.values(), "pq_zone")?.let { PqNav.zone = it }
            if (j.has("pq_sys")) PqNav.sysCursor = j.optInt("pq_sys").coerceAtLeast(0)
            enumOf(PocketNav.Page.values(), "pc_page")?.let { PocketNav.page = it }
            if (j.has("pc_spot")) PocketNav.spot = j.optInt("pc_spot").coerceAtLeast(0)
            if (j.has("pc_cursor")) PocketNav.cursor = j.optInt("pc_cursor").coerceAtLeast(0)
            PocketNav.gamesFromAll = j.optBoolean("pc_from_all")
            if (j.has("bl_blade")) { BladesNav.blade = j.optInt("bl_blade").coerceIn(0, BladesNav.COUNT - 1); BladesNav.started = true }
            if (j.has("bl_spot")) BladesNav.spotRow = j.optInt("bl_spot").coerceAtLeast(0)
            if (j.has("bl_sys")) BladesNav.sysRow = j.optInt("bl_sys").coerceAtLeast(0)
            if (j.has("bl_media")) BladesNav.mediaRow = j.optInt("bl_media").coerceAtLeast(0)
            if (j.has("bl_set")) BladesNav.setRow = j.optInt("bl_set").coerceAtLeast(0)
            BladesNav.gamesFromSystems = j.optBoolean("bl_from_systems")
            enumOf(BubbleNav.View.values(), "bv_view")?.let { BubbleNav.view = it; BubbleNav.started = true }
            if (j.has("bv_folder")) BubbleNav.folder = j.optInt("bv_folder").coerceAtLeast(0)
            BubbleNav.fromFolders = j.optBoolean("bv_from_folders")
            if (j.has("pc_source")) CameraNav.source = j.optInt("pc_source").coerceIn(0, 2)
            enumOf(TermNav.Dir.values(), "tm_dir")?.let { TermNav.dir = it; TermNav.booted = true }
            if (j.has("tm_root")) TermNav.rootSel = j.optInt("tm_root").coerceAtLeast(0)
            if (j.has("tm_sys")) TermNav.sysSel = j.optInt("tm_sys").coerceAtLeast(0)
            if (j.has("pd_channel")) { PanelNav.channel = j.optInt("pd_channel").coerceIn(0, PanelNav.COUNT - 1); PanelNav.started = true }
            if (j.has("pd_spot")) PanelNav.spotCol = j.optInt("pd_spot").coerceAtLeast(0)
            if (j.has("pd_sys")) PanelNav.sysCol = j.optInt("pd_sys").coerceAtLeast(0)
            if (j.has("pd_media")) PanelNav.mediaCol = j.optInt("pd_media").coerceAtLeast(0)
            if (j.has("pd_set")) PanelNav.setCol = j.optInt("pd_set").coerceAtLeast(0)
            PanelNav.gamesFromSystems = j.optBoolean("pd_from_systems")
        }
    }
}
