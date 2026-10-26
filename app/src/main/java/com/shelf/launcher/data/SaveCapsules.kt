package com.shelf.launcher.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Which games get a Save Capsule: only games with real save evidence — an in-game save file named after
 * the ROM (on this device or synced from another one), a save inside a PS1 / PS2 memory card or a PSP
 * SAVEDATA folder that belongs to the game, or saves Google Drive really moved for it. Looking at a game
 * (resting on it, opening its details) never makes one.
 *
 * Memory-card systems get ONE capsule per game holding all its saves (every card, every slot), not one
 * per card entry. Settings › Sync › Save Capsules has the switches ([Options]).
 */
object SaveCapsules {

    data class Options(
        /** Saves found in memory cards (also for games never started from Voltcap). */
        val memoryCards: Boolean = true,
        /** Saves that came from elsewhere: games never played through Voltcap (imported / other device). */
        val external: Boolean = true,
        /** Only games played through Voltcap (overrides [external]). */
        val onlyPlayedHere: Boolean = false,
        /** Capsules hidden by hand (Game.stableKey). The saves themselves are never touched. */
        val hidden: Set<String> = emptySet(),
        /** 0.6.18: saves that don't say which game they are ("Unassigned 3DS save (title ID …)", a number, a code,
         *  "Other saves"): off by default, so the wall shows games only. The saves stay where they are. */
        val unnamed: Boolean = false
    )

    /** One individual save inside a capsule. */
    data class SaveItem(
        val title: String,
        /** Where it lives: "Mcd001.ps2 · BASLUS-20312", "RetroArch saves · Zelda.srm". */
        val where: String,
        val sizeBytes: Long,
        val modified: Long,
        val kind: Kind
    )

    enum class Kind { SAVE_FILE, SYNCED_FROM_OTHER_DEVICE, MEMORY_CARD, PSP_SAVEDATA, DRIVE }

    data class Capsule(
        val game: Game,
        val saves: List<SaveItem>,
        val status: GameSyncStatus?,
        val playedHere: Boolean,
        /** False for saves whose game isn't in this device's library (found on a memory card / synced). */
        val onDevice: Boolean = true,
        /** 0.6.18: a game you'd recognise — one of your games, or a save that names its game in words. False for
         *  "Unassigned 3DS / Wii save", "Other saves" and saves named only by a number or a code. */
        val named: Boolean = true
    ) {
        val lastSaved: Long get() = maxOf(saves.maxOfOrNull { it.modified } ?: 0L, status?.lastSuccessAt ?: 0L)
        val fromMemoryCard: Boolean get() = saves.isNotEmpty() && saves.all { it.kind == Kind.MEMORY_CARD || it.kind == Kind.PSP_SAVEDATA }
    }

    private val _all = MutableStateFlow<List<Capsule>>(emptyList())
    /** Every capsule with evidence (before [Options] filtering). */
    val all: StateFlow<List<Capsule>> = _all.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _options = MutableStateFlow(Options())
    val options: StateFlow<Options> = _options.asStateFlow()
    private val mutex = Mutex()
    @Volatile private var optionsLoaded = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("save_capsules", Context.MODE_PRIVATE)

    fun loadOptions(context: Context): Options {
        if (!optionsLoaded) {
            optionsLoaded = true
            val p = prefs(context)
            _options.value = Options(
                memoryCards = p.getBoolean("memory_cards", true),
                external = p.getBoolean("external", true),
                onlyPlayedHere = p.getBoolean("only_played_here", false),
                hidden = p.getStringSet("hidden", emptySet()).orEmpty().toSet(),
                unnamed = p.getBoolean("unnamed", false)
            )
        }
        return _options.value
    }

    fun setOptions(context: Context, transform: (Options) -> Options) {
        val o = transform(loadOptions(context))
        _options.value = o
        prefs(context).edit()
            .putBoolean("memory_cards", o.memoryCards).putBoolean("external", o.external)
            .putBoolean("only_played_here", o.onlyPlayedHere).putStringSet("hidden", o.hidden).putBoolean("unnamed", o.unnamed)
            .apply()
    }

    fun setHidden(context: Context, key: String, hidden: Boolean) =
        setOptions(context) { it.copy(hidden = if (hidden) it.hidden + key else it.hidden - key) }

    /** The capsules to show with [o]. */
    fun visible(list: List<Capsule>, o: Options): List<Capsule> = list.filter { c ->
        if (c.game.stableKey in o.hidden) return@filter false
        if (!o.unnamed && !c.named) return@filter false
        if (o.onlyPlayedHere && !c.playedHere) return@filter false
        if (!o.external && !c.playedHere) return@filter false
        if (!o.memoryCards && c.fromMemoryCard) return@filter false
        true
    }

    /** Save Vault's filter: all, games on this device, or saves of games that aren't here. */
    fun matches(c: Capsule, f: Features.SaveVaultFilter): Boolean = when (f) {
        Features.SaveVaultFilter.ALL -> true
        Features.SaveVaultFilter.ON_DEVICE -> c.onDevice
        Features.SaveVaultFilter.NOT_ON_DEVICE -> !c.onDevice
    }

    // ---- Building the list ----

    private data class CardKey(val path: String, val size: Long, val modified: Long)
    private val cardCache = HashMap<CardKey, List<MemoryCards.CardSave>>()

    /**
     * Looks for saves of every ROM in [games]. [lastPlayed]: games played through Voltcap (stableKey →
     * millis). Reads folders and cards (cached by size + date): runs on the IO dispatcher.
     */
    suspend fun refresh(context: Context, games: List<Game>, lastPlayed: Map<String, Long>) {
        lastArgs = games to lastPlayed // (Update 241, #76: for requestRefresh)
        // The last list shows at once (saved on disk), the fresh look-up replaces it when it's done.
        if (_all.value.isEmpty()) {
            val cached = withContext(Dispatchers.IO) { runCatching { readCache(context.applicationContext, games) }.getOrNull() }
            if (!cached.isNullOrEmpty() && _all.value.isEmpty()) _all.value = cached
        }
        mutex.withLock {
            _loading.value = true
            try {
                // (Update 241, #76 / #68: on a new device — or when Drive's folders have to be listed again — the vault
                // waited for Drive's listing and every memory card to download before showing a single save. A quick
                // look first, with what's on this device and already kept (no network), shows the real saves at once;
                // it's only shown when it doesn't hide anything the list already has. The full look replaces it.)
                val app = context.applicationContext
                val quickHelps = _all.value.isEmpty() || runCatching { DriveOnlySaves.needsFetch(app) }.getOrDefault(false)
                if (quickHelps) {
                    val quick = withContext(Dispatchers.IO) { runCatching { build(app, games, lastPlayed, net = false) }.getOrNull() }
                    val shown = _all.value.map { it.game.stableKey }.toSet()
                    if (!quick.isNullOrEmpty() && quick.map { it.game.stableKey }.toSet().containsAll(shown)) _all.value = quick
                }
                val result = withContext(Dispatchers.IO) { build(context.applicationContext, games, lastPlayed) }
                _all.value = result
                withContext(Dispatchers.IO) { runCatching { writeCache(context.applicationContext, result) } }
            } finally {
                _loading.value = false
            }
        }
    }

    @Volatile private var localDone = false

    /**
     * Update 330: the saves already on this device (no network, nothing written to a save) — for the Memory card layout's
     * save icons, once per run. The list kept on disk shows first; a quick local look then registers the memory cards'
     * icons (Ps2Icons). It never replaces a fuller list the Save Vault has already shown.
     */
    suspend fun refreshLocal(context: Context, games: List<Game>, lastPlayed: Map<String, Long>) {
        if (localDone || games.isEmpty()) return
        localDone = true
        val app = context.applicationContext
        if (_all.value.isEmpty()) {
            val cached = withContext(Dispatchers.IO) { runCatching { readCache(app, games) }.getOrNull() }
            if (!cached.isNullOrEmpty() && _all.value.isEmpty()) _all.value = cached
        }
        if (!mutex.tryLock()) return
        try {
            val quick = withContext(Dispatchers.IO) { runCatching { build(app, games, lastPlayed, net = false) }.getOrNull() }
            val shown = _all.value.map { it.game.stableKey }.toSet()
            if (!quick.isNullOrEmpty() && quick.map { it.game.stableKey }.toSet().containsAll(shown)) _all.value = quick
            Ps2Icons.bump()
        } finally {
            mutex.unlock()
        }
    }

    /** (Update 241, #76) What the vault last asked for, so a sync can bring the list up to date by itself. */
    @Volatile private var lastArgs: Pair<List<Game>, Map<String, Long>>? = null
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var pending: Job? = null

    /**
     * Update 241 (#68 / #76): saves just arrived (a sync finished a folder with downloads). When the Save Vault has been
     * open in this run and someone still watches the list, it looks again — a couple of seconds later, once for a
     * burst of folders. Reads only (the same look-up as [refresh]); never touches a save.
     */
    fun requestRefresh(context: Context) {
        val args = lastArgs ?: return
        if (_all.subscriptionCount.value == 0) return
        if (pending?.isActive == true) return
        if (runCatching { PlaySession.isOpen(context) }.getOrDefault(false)) return
        val app = context.applicationContext
        pending = refreshScope.launch {
            delay(2_000)
            runCatching { refresh(app, args.first, args.second) }
        }
    }

    // ---- The list kept on disk, so the vault opens with it straight away ----

    /**
     * Update 112: a new name whenever what's looked for changes (the old list showed cards as "memory card" until the
     * fresh look-up replaced them with their games); the old file is removed.
     */
    private fun cacheFile(context: Context): java.io.File {
        java.io.File(context.filesDir, "save_capsules_cache.json").takeIf { it.exists() }?.delete()
        return java.io.File(context.filesDir, "save_capsules_cache_v2.json")
    }

    private fun writeCache(context: Context, list: List<Capsule>) {
        val arr = org.json.JSONArray()
        // A card that couldn't be opened yet is never kept: next time it may show as its games.
        list.filter { c -> c.game.romPath?.startsWith("save:card:") != true }.forEach { c ->
            val o = org.json.JSONObject()
            o.put("key", c.game.stableKey); o.put("name", c.game.name); o.put("platform", c.game.platform)
            o.put("hue", c.game.hue); o.put("rom", c.game.romPath ?: ""); o.put("here", c.playedHere); o.put("onDevice", c.onDevice)
            val saves = org.json.JSONArray()
            c.saves.forEach { sv ->
                saves.put(org.json.JSONObject().put("t", sv.title).put("w", sv.where).put("s", sv.sizeBytes).put("m", sv.modified).put("k", sv.kind.name))
            }
            o.put("saves", saves)
            arr.put(o)
        }
        val f = cacheFile(context)
        val tmp = java.io.File(f.path + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(f)
    }

    private fun readCache(context: Context, games: List<Game>): List<Capsule> {
        val f = cacheFile(context)
        if (!f.isFile) return emptyList()
        SyncStatusStore.load(context)
        val statuses = SyncStatusStore.all.value
        val byKey = games.associateBy { it.stableKey }
        val arr = org.json.JSONArray(f.readText())
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val key = o.getString("key")
            val onDevice = o.optBoolean("onDevice", true)
            // A game that's gone from the library since: left out (the fresh look-up decides).
            val game = byKey[key] ?: if (onDevice) return@mapNotNull null else Game(
                name = o.getString("name"), platform = o.getString("platform"), hue = o.optInt("hue"),
                sourceType = SourceType.ROM, romPath = o.optString("rom").ifBlank { null }
            )
            val sa = o.getJSONArray("saves")
            val saves = (0 until sa.length()).map { j ->
                val sv = sa.getJSONObject(j)
                SaveItem(sv.getString("t"), sv.getString("w"), sv.optLong("s"), sv.optLong("m"),
                    runCatching { Kind.valueOf(sv.getString("k")) }.getOrDefault(Kind.SAVE_FILE))
            }
            Capsule(game, saves, statuses[key], o.optBoolean("here"), onDevice)
        }
    }

    /** [net] false (update 241, #76): nothing from the network — only this device and what's already kept from Drive. */
    private fun build(context: Context, games: List<Game>, lastPlayed: Map<String, Long>, net: Boolean = true): List<Capsule> {
        loadOptions(context)
        SyncStatusStore.load(context)
        val statuses = SyncStatusStore.all.value
        val roms = games.filter { it.sourceType == SourceType.ROM && it.romPath != null }
        val items = HashMap<String, MutableList<SaveItem>>()
        fun add(game: Game, item: SaveItem) { items.getOrPut(game.stableKey) { mutableListOf() } += item }
        // Update 151: saves on Google Drive in folders this device doesn't sync (set up on another device only) — they
        // never showed. Their memory cards, PSP saves and named saves join the steps below like synced ones.
        val driveOnly = runCatching { DriveOnlySaves.loose(context, allowFetch = net) }.getOrDefault(emptyList())
        // Update 151: the library's names worked out once, for matching saves by file name and title (step 4).
        val romIndex = SaveMatch.index(roms)

        // 1. Save files named after the ROM (the per-game kinds: RetroArch, generic folders, DraStic): every
        //    folder listed once for the whole library.
        val perGame = CloudSync.perGameSaveFilesAll(context, roms)
        val tied = HashSet<String>() // files step 1 tied to a ROM: step 4 must not list them again
        roms.forEach { g ->
            perGame[g.stableKey].orEmpty().forEach { f ->
                tied += "${f.folder}|${f.path}"
                add(g, SaveItem(
                    title = f.path.substringAfterLast('/'),
                    where = f.folder + if (!f.onDevice) " · synced from ${f.fromDevice ?: "another device"}" else "",
                    sizeBytes = f.size, modified = f.modified,
                    kind = if (f.onDevice) Kind.SAVE_FILE else Kind.SYNCED_FROM_OTHER_DEVICE
                ))
            }
        }

        // 2. Memory cards / PSP SAVEDATA: matched by product code, else by title.
        //    Cards are read even without a PS1 / PS2 / PSP game in the library: their saves then show as
        //    games "not on this device" (one capsule per game, holding all of its saves).
        val cardGames = roms.filter { platformKind(it.platform) != null }
        val strayGames = HashMap<String, Game>()
        // Update 151: a save that matches no game goes under "Other saves" (named after its file), never left out.
        fun other(name: String, item: SaveItem) {
            val id = name.lowercase()
            val g = strayGames.getOrPut("OTHER:$id") {
                Game(name = name, platform = "other", hue = (id.hashCode() and 0x7FFFFFFF) % 360, sourceType = SourceType.ROM, romPath = "save:other:$id")
            }
            add(g, item)
        }
        fun itemOf(f: CloudSync.LooseSave): SaveItem = SaveItem(
            title = f.path.substringAfterLast('/'),
            where = f.folder + if (!f.onDevice) " · synced from ${f.fromDevice ?: "another device"}" else "",
            sizeBytes = f.size, modified = f.modified,
            kind = if (f.onDevice) Kind.SAVE_FILE else Kind.SYNCED_FROM_OTHER_DEVICE
        )
        // (Update 108: plus the cards only synced from another device, fetched from Drive and opened the same way.)
        val openedCards = HashSet<String>()
        // (Update 109: and files that look like a card by name but aren't one, by their content — plain saves, step 4.)
        val notCards = HashSet<String>()
        // (Listed once: the memory-card folders' files only on Drive, for step 2 and step 4.)
        val remoteCardList = runCatching { CloudSync.remoteCardFiles(context) }.getOrDefault(emptyList()) +
            driveOnly.filter { it.kind == SaveKind.PS1_MEMCARDS || it.kind == SaveKind.PS2_MEMCARDS } // (Update 151)
        val saves = readCards(context) + remoteCards(context, remoteCardList, openedCards, notCards, net) +
            // Update 151: PSP saves only on Drive (a PPSSPP folder this device doesn't sync), named from their PARAM.SFO.
            runCatching { DriveOnlySaves.pspSaves(context, driveOnly.filter { it.kind == SaveKind.PSP_SAVEDATA }, allowNetwork = net) }
                .getOrDefault(emptyList()).map { Platform.PSP to it }
        if (saves.isNotEmpty()) {
            val byCode = HashMap<String, Game>()
            cardGames.forEach { g ->
                MemoryCards.serialFor(context, g.romPath!!, g.platform)?.let { code -> byCode.putIfAbsent(code, g) }
            }
            saves.forEach { (kind, s) ->
                // Per-game cards are named after the game (DuckStation "Crash Bandicoot (USA)_1.mcd",
                // "SLUS-00067_1.mcr"): the card's own name is a second clue.
                val cardStem = s.card.substringBeforeLast('.').replace(Regex("_\\d+$"), "")
                val cardCode = MemoryCards.normaliseCode(cardStem)
                val target = s.code?.let { byCode[it] }
                    ?: cardCode?.let { byCode[it] }
                    ?: cardGames.firstOrNull { g -> platformKind(g.platform) == kind && MemoryCards.titleMatches(s.title, com.shelf.launcher.ui.stripRegionTags(g.name)) }
                    ?: cardGames.firstOrNull { g -> platformKind(g.platform) == kind && MemoryCards.titleMatches(cardStem, com.shelf.launcher.ui.stripRegionTags(g.name)) }
                    ?: run {
                        // Not in this library: its own capsule, keyed by product code (or title).
                        val id = s.code ?: s.title.lowercase()
                        strayGames.getOrPut("${kind.name}:$id") {
                            Game(
                                name = s.title.ifBlank { s.code ?: "Save" },
                                platform = kind.name,
                                hue = (id.hashCode() and 0x7FFFFFFF) % 360,
                                sourceType = SourceType.ROM,
                                romPath = "save:${kind.name}:$id"
                            )
                        }
                    }
                add(target, SaveItem(
                    title = s.title, where = "${s.card} · ${s.folder}", sizeBytes = s.sizeBytes, modified = s.modified,
                    kind = if (kind == Platform.PSP) Kind.PSP_SAVEDATA else Kind.MEMORY_CARD
                ))
                // (Update 239, #112: where this PS2 save's browser icon comes from, for Save Vault's PS2 browser style.)
                if (kind == Platform.PS2) s.ps2Icon?.let { Ps2Icons.register(Ps2Icons.idOf("${s.card} · ${s.folder}", s.sizeBytes, s.modified), it) }
            }
            Ps2Icons.bump() // (Update 239, #112)
        }

        // 2b. GameCube (update 112): Dolphin's .gci saves and .raw memory cards are opened and each save goes to its game —
        //     by the game code (read from the disc's header), else by the save's own title; Dolphin's SRAM file (system
        //     settings, not a save) stays out. On this device they're read directly, from another device fetched once.
        val loose = runCatching { CloudSync.looseSaveFiles(context) }.getOrDefault(emptyList()) + remoteCardList +
            driveOnly.filter { it.kind in DRIVE_LOOSE_KINDS } // (Update 151)
        val gcDone = HashSet<String>()
        val gcFiles = loose.filter { f ->
            val e = f.path.substringAfterLast('.', "").lowercase()
            (e == "gci" || (e == "raw" && f.kind == SaveKind.DOLPHIN_GC)) && "${f.folder}|${f.path}" !in tied
        }
        if (gcFiles.isNotEmpty()) {
            val gcLib = roms.filter { isGameCube(it.platform) }
            val byGcId = HashMap<String, Game>()
            gcLib.forEach { g -> MemoryCards.gcGameId(g.romPath!!)?.let { byGcId.putIfAbsent(it.take(4), g) } }
            // Update 151: also by name (Dolphin's file name holds one: "01-GC6E-pokemon_colosseum.gci"), when the disc's
            // header can't be read and the save's own title differs ("POKéMON COLOSSEUM").
            val gcIndex = SaveMatch.index(gcLib)
            gcFiles.forEach { f ->
                val key = "${f.folder}|${f.path}"
                val name = f.path.substringAfterLast('/')
                val ext = name.substringAfterLast('.', "").lowercase()
                if (name.substringBeforeLast('.').equals("SRAM", true)) { gcDone += key; return@forEach }
                val cap = if (ext == "raw") 17_000_000L else 2_000_000L
                val bytes = if (f.onDevice) CloudSync.localFileBytes(context, f.folder, f.path, cap)
                    else runCatching { CloudSync.remoteFileBytes(context, f.folder, f.path, maxBytes = cap.toInt(), allowNetwork = net) }.getOrNull()
                        // Update 151: or from a Drive folder this device doesn't sync.
                        ?: DriveOnlySaves.bytes(context, f.folder, f.path, cap, allowNetwork = net)
                val from = if (!f.onDevice) " · synced from ${f.fromDevice ?: "another device"}" else ""
                val gcSaves = when {
                    bytes == null -> null
                    ext == "gci" -> MemoryCards.readGci(name, bytes, f.modified)?.let { listOf(it) }
                    else -> MemoryCards.readGcCard(name, bytes, f.modified)
                }
                // Update 119: every .gci / Dolphin .raw is handled here and never again as a plain save (step 4 turned an
                // unreadable "00000012.gci" into a game of its own, named after the number).
                gcDone += key
                if (gcSaves == null) {
                    // Not readable now (offline): a .gci still names its game in Dolphin's file name ("01-GZLE-…").
                    // Update 151: by the code, else by the name after it; still unknown: under "Other saves", not left out.
                    val code = if (ext == "gci") Regex("^[0-9A-Za-z]{2}-([A-Za-z0-9]{4})-").find(name)?.groupValues?.get(1)?.uppercase() else null
                    val g = code?.let { byGcId[it] } ?: (if (ext == "gci") SaveMatch.best(f.title, "gc", gcIndex) else null)
                    val item = SaveItem(name, f.folder + from, f.size, f.modified, if (f.onDevice) Kind.SAVE_FILE else Kind.SYNCED_FROM_OTHER_DEVICE)
                    if (g != null) add(g, item) else other(name, item)
                    return@forEach
                }
                gcSaves.forEach { sv ->
                    val code4 = sv.code?.take(4)
                    val known = code4?.let { byGcId[it] }
                        ?: gcLib.firstOrNull { g -> MemoryCards.titleMatches(sv.title, com.shelf.launcher.ui.stripRegionTags(g.name)) }
                        // Update 151: a single .gci also by Dolphin's file name ("pokemon_colosseum").
                        ?: (if (ext == "gci") SaveMatch.best(f.title, "gc", gcIndex) else null)
                    // A save that isn't one of your games shows as a game "not on this device" only when it says which game
                    // it is in words (a title with letters); a bare number or code goes under "Other saves" (update 151:
                    // it used to be left out).
                    if (known == null && !looksLikeTitle(sv.title, sv.code)) {
                        other(if (ext == "gci") name else sv.title.ifBlank { name }, SaveItem(
                            title = sv.title.ifBlank { name }, where = (if (ext == "raw") "$name · " else "") + f.folder + from,
                            sizeBytes = sv.sizeBytes, modified = sv.modified,
                            kind = if (ext == "raw") Kind.MEMORY_CARD else if (f.onDevice) Kind.SAVE_FILE else Kind.SYNCED_FROM_OTHER_DEVICE
                        ))
                        return@forEach
                    }
                    val target = known
                        ?: run {
                            val id = (code4 ?: sv.title).lowercase()
                            strayGames.getOrPut("GC:$id") {
                                Game(name = sv.title.ifBlank { sv.code ?: "GameCube save" }, platform = "GameCube",
                                    hue = (id.hashCode() and 0x7FFFFFFF) % 360, sourceType = SourceType.ROM, romPath = "save:gc:$id")
                            }
                        }
                    add(target, SaveItem(
                        title = sv.title, where = (if (ext == "raw") "$name · " else "") + f.folder + from, sizeBytes = sv.sizeBytes, modified = sv.modified,
                        kind = if (ext == "raw") Kind.MEMORY_CARD else if (f.onDevice) Kind.SAVE_FILE else Kind.SYNCED_FROM_OTHER_DEVICE
                    ))
                }
            }
        }

        // 2c. Wii (update 151): Dolphin keeps each game's save in "<type>/<title id>/data/…"; the title id's last part is
        //     the game code ("524d4345" = "RMCE"), matched to the code in a Wii disc's header (or its file name). One
        //     save per game folder; one that isn't in the library shows as that code's Wii save.
        // (Update 241, #53: also Wii title folders in a folder you picked yourself, found by their path
        //  (SaveLayouts.wii: "00010000/<low>/data/…", "title/…" or "Wii/title/…" in front); every file of a title folder —
        //  banner.bin and the game's own files — is one save. One that isn't in the library is "Unassigned Wii save
        //  (CODE)", with its title id in where it's kept.)
        val wiiFiles = loose.mapNotNull { f ->
            val u = SaveLayouts.wii(f.path) ?: if (f.kind == SaveKind.DOLPHIN_WII) legacyWiiUnit(f.path) else null
            u?.let { f to it }
        }
        if (wiiFiles.isNotEmpty()) {
            val wiiLib = roms.filter { it.platform.equals("wii", true) }
            val byWiiId = HashMap<String, Game>()
            wiiLib.forEach { g ->
                val path = g.romPath!!
                val id = MemoryCards.gcGameId(path) ?: Regex("\\[([A-Z0-9]{6})]").find(path.substringAfterLast('/'))?.groupValues?.get(1)
                id?.let { byWiiId.putIfAbsent(it.take(4).uppercase(), g) }
            }
            wiiFiles.groupBy { (f, u) -> f.folder to u.prefix }.forEach { (key, list) ->
                val files = list.map { it.first }
                val unit = list.first().second
                val code = wiiCode(unit.low)
                val first = files.first()
                val item = SaveItem(
                    title = "Save data (${files.size} file${if (files.size == 1) "" else "s"})",
                    where = key.first + " · title ${unit.high}/${unit.low}" + if (!first.onDevice) " · synced from ${first.fromDevice ?: "another device"}" else "",
                    sizeBytes = files.sumOf { it.size }, modified = files.maxOf { it.modified },
                    kind = if (files.all { it.onDevice }) Kind.SAVE_FILE else Kind.SYNCED_FROM_OTHER_DEVICE
                )
                val g = byWiiId[code] ?: strayGames.getOrPut("WII:${code.lowercase()}") {
                    Game(name = "Unassigned Wii save ($code)", platform = "Wii", hue = (code.hashCode() and 0x7FFFFFFF) % 360,
                        sourceType = SourceType.ROM, romPath = "save:wii:${code.lowercase()}")
                }
                add(g, item)
            }
        }

        // 2d. Nintendo 3DS (update 223, #67 / #72): Citra / Azahar keep each game's save in
        //     ".../title/<high>/<low>/data/<id>/…" (files often called "main", or savedata0.dat…). One save per title
        //     folder, matched by the title id in the game's ROM header (MemoryCards.n3dsTitleLow); one that isn't in the
        //     library shows as that title id's 3DS save. Extra data (extdata) isn't a game save and isn't listed.
        //     (Update 241, #72 / #73: also from a folder you picked yourself or a Drive-only folder (SaveLayouts.n3ds), every
        //     file of the title folder — "main" with no extension, "00000001.metadata" — in one save; separate 3DS profiles
        //     (id0/id1) stay separate saves; one without its game here is "Unassigned 3DS save (title ID …)".)
        val n3dsFiles = loose.mapNotNull { f ->
            val u = SaveLayouts.n3ds(f.path) ?: if (f.kind == SaveKind.CITRA) legacyN3dsUnit(f.path) else null
            u?.let { f to it }
        }
        if (n3dsFiles.isNotEmpty()) {
            val n3dsLib = roms.filter { it.platform.lowercase().let { p -> p == "3ds" || p == "n3ds" || p.contains("3ds") } }
            val byTitle = HashMap<String, Game>()
            n3dsLib.forEach { g -> MemoryCards.n3dsTitleLow(g.romPath!!)?.let { byTitle.putIfAbsent(it, g) } }
            n3dsFiles.groupBy { (f, u) -> Triple(f.folder, u.profile.lowercase(), u.high + "/" + u.low) }.forEach { (key, list) ->
                val files = list.map { it.first }
                val unit = list.first().second
                val low = unit.low
                val first = files.first()
                val item = SaveItem(
                    title = "Save data (${files.size} file${if (files.size == 1) "" else "s"})",
                    where = key.first + " · title ID ${unit.titleId}" + if (!first.onDevice) " · synced from ${first.fromDevice ?: "another device"}" else "",
                    sizeBytes = files.sumOf { it.size }, modified = files.maxOf { it.modified },
                    kind = if (files.all { it.onDevice }) Kind.SAVE_FILE else Kind.SYNCED_FROM_OTHER_DEVICE
                )
                val g = byTitle[low] ?: strayGames.getOrPut("N3DS:$low") {
                    Game(name = "Unassigned 3DS save (title ID ${unit.titleId})", platform = "3DS", hue = (low.hashCode() and 0x7FFFFFFF) % 360,
                        sourceType = SourceType.ROM, romPath = "save:3ds:$low")
                }
                add(g, item)
            }
        }

        // 3. Google Drive moved saves for it (other shared kinds: GameCube, Wii, 3DS, Dreamcast, N64) —
        //    only games played through Voltcap, since those folders hold every game's saves.
        roms.forEach { g ->
            if (items.containsKey(g.stableKey)) return@forEach
            val st = statuses[g.stableKey] ?: return@forEach
            if (st.lastSuccessAt > 0 && st.fileCount > 0 && st.lastDirection != SyncDirection.NONE && lastPlayed.containsKey(g.stableKey)) {
                add(g, SaveItem("Synced saves", "Google Drive", 0L, st.lastSuccessAt, Kind.DRIVE))
            }
        }

        // 4. Save files named after their game that step 1 didn't tie to a ROM (N64, GameCube, RetroArch / generic
        //    folders…), on this device or synced from another one: matched to a library game of the right system
        //    by title; the rest are games that aren't on this device (one capsule each).
        // RetroAchievements' game lists already on the device (Game Boy, Game Boy Color, Game Boy Advance): which
        // console a game is on, for saves not tied to one of your games.
        val raLists = HandheldLists(context)
        loose.forEach { f ->
            if ("${f.folder}|${f.path}" in tied || "${f.folder}|${f.path}" in gcDone) return@forEach
            // (Update 112: Dolphin's SRAM file is the GameCube's system settings, never a game's save.)
            if (f.kind == SaveKind.DOLPHIN_GC && f.path.substringAfterLast('/').substringBeforeLast('.').equals("SRAM", true)) return@forEach
            // (Update 109: the files of a PS2 folder card were read in step 2; from the memory-card folders only the cards.)
            if (f.path.split('/').dropLast(1).any { it.lowercase().endsWith(".ps2") }) return@forEach
            if ((f.kind == SaveKind.PS1_MEMCARDS || f.kind == SaveKind.PS2_MEMCARDS) && !looksLikeCard(f)) return@forEach
            // A memory card (update 104: "Mcd001.ps2", "epsxe000.mcr"…): on this device its saves were listed game by
            // game in step 2; one only synced from another device shows as that console's card.
            val ext = f.path.substringAfterLast('.', "").lowercase()
            val stem = f.path.substringAfterLast('/').substringBeforeLast('.')
            val card = looksLikeCard(f) && "${f.folder}|${f.path}" !in notCards
            if (card) {
                // On this device, or fetched from Drive and opened: its saves are listed game by game (step 2).
                if (f.onDevice || "${f.folder}|${f.path}" in openedCards) return@forEach
                // Update 109: a card that couldn't be opened (offline, Drive not reachable) is still listed by game where
                // its name says which (per-game cards: "Crash Bandicoot (USA)_1.mcd", "SLUS-00067_1.mcr"); only a
                // shared card ("Mcd001.ps2") shows as the card itself until it can be opened.
                val platform = if (ext == "ps2" || f.kind == SaveKind.PS2_MEMCARDS) "ps2" else "psx"
                val gameStem = stem.replace(Regex("_\\d+$"), "")
                val shared = SHARED_CARD.matches(gameStem)
                val item = SaveItem(
                    title = f.path.substringAfterLast('/'),
                    where = f.folder + " · synced from ${f.fromDevice ?: "another device"}" + if (shared) " (its games show once it can be opened)" else "",
                    sizeBytes = f.size, modified = f.modified, kind = Kind.SYNCED_FROM_OTHER_DEVICE
                )
                if (!shared) {
                    val wanted = if (platform == "ps2") Platform.PS2 else Platform.PS1
                    val title = com.shelf.launcher.ui.stripRegionTags(gameStem)
                    val code = MemoryCards.normaliseCode(gameStem)
                    val mine = cardGames.firstOrNull { g ->
                        platformKind(g.platform) == wanted && (MemoryCards.titleMatches(title, com.shelf.launcher.ui.stripRegionTags(g.name)) ||
                            (code != null && g.romPath?.let { r -> MemoryCards.normaliseCode(r.substringAfterLast('/').substringBeforeLast('.')) } == code))
                    }
                    if (mine != null) { add(mine, item); return@forEach }
                    if (title.length >= 3) {
                        val id = (code ?: title).lowercase()
                        val stray = strayGames.getOrPut("${wanted.name}:$id") {
                            Game(name = if (code != null && title.equals(gameStem, true) && title.length <= 11) code else title, platform = wanted.name,
                                hue = (id.hashCode() and 0x7FFFFFFF) % 360, sourceType = SourceType.ROM, romPath = "save:${wanted.name}:$id")
                        }
                        add(stray, item)
                        return@forEach
                    }
                }
                val stray = strayGames.getOrPut("CARD:$platform:${stem.lowercase()}") {
                    Game(
                        name = (if (platform == "ps2") "PS2 memory card" else "PS1 memory card") + " · $stem", platform = platform,
                        hue = (stem.hashCode() and 0x7FFFFFFF) % 360, sourceType = SourceType.ROM, romPath = "save:card:$platform:${stem.lowercase()}"
                    )
                }
                add(stray, item)
                return@forEach
            }
            // Update 119: GameCube saves are only ever read in step 2b (by their content); update 151: Wii saves in 2c;
            // update 223: 3DS saves in 2d.
            if (ext == "gci" || f.kind == SaveKind.DOLPHIN_GC || f.kind == SaveKind.DOLPHIN_WII || f.kind == SaveKind.CITRA) return@forEach
            // (Update 241: and 3DS / Wii title folders in any other folder, in 2c / 2d — never matched by a file name like "main".)
            if (SaveLayouts.titleUnit(f.path) != null) return@forEach
            // Update 151: the emulator core in front of the name ("Gambatte_Pokemon - Red…") is not part of the game's name.
            val (core, bare) = SaveMatch.splitCore(f.title)
            val stripped = com.shelf.launcher.ui.stripRegionTags(bare)
            val item = itemOf(f)
            if (stripped.length < 3) { other(f.path.substringAfterLast('/'), item); return@forEach }
            // Update 151: matched by the same file name as the ROM first, then by the name with its tags, then by title
            // ("… [patched]", "(Subset - …)"), a game of the save's own console first (it used to take the first title
            // match of any console).
            val hint = SaveMatch.platformOfCore(core) ?: loosePlatform(f)
            val found = SaveMatch.match(bare, hint, romIndex) { g ->
                f.kind == SaveKind.RETROARCH || f.kind == SaveKind.GENERIC || f.kind in CloudSync.kindsForPlatform(g.platform)
            }
            val mine = found.game
            if (mine != null) { add(mine, item); return@forEach }
            // (Update 241, #74: its name fits several of your games equally well — other regions, another console: it's
            // left unassigned under "Other saves" and says so, instead of going to the first of them.)
            if (found.ambiguous > 1) {
                other(f.path.substringAfterLast('/'), item.copy(where = item.where + " · fits ${found.ambiguous} games here — not assigned"))
                return@forEach
            }
            // Without a recognisable name (a number, a code) it isn't a game of its own (update 119): "Other saves" (update 151).
            if (!looksLikeTitle(stripped)) { other(f.path.substringAfterLast('/'), item); return@forEach }
            // Its console (update 104): from the folder's kind, the file's type, or the emulator / system folder it's in;
            // Game Boy or Game Boy Color by the game itself (update 119: the emulators keep both in one folder).
            val platform = handheldOf(hint, bare, raLists)
            // (0.6.18: by the title's key, so "Pokemon - Red Version" from one handheld and "Pokemon Red Version" from
            // another are one game, not two cards; the name with the " - " — the ROM sets' spelling, which finds art — wins.)
            val id = MemoryCards.titleKey(stripped).ifBlank { stripped.lowercase() }
            val slot = "LOOSE:$platform:$id"
            val stray = strayGames.getOrPut(slot) {
                Game(
                    name = stripped, platform = platform, hue = (id.hashCode() and 0x7FFFFFFF) % 360,
                    sourceType = SourceType.ROM, romPath = "save:$platform:$id"
                )
            }.let { g ->
                if (" - " in stripped && " - " !in g.name) g.copy(name = stripped).also { strayGames[slot] = it } else g
            }
            add(stray, item)
        }
        // Update 151: Drive-only folders of other kinds (3DS, Dreamcast…): one "Other saves" entry per folder.
        driveOnly.filter { it.kind != SaveKind.PSP_SAVEDATA && it.kind != SaveKind.PS1_MEMCARDS && it.kind != SaveKind.PS2_MEMCARDS && it.kind !in DRIVE_LOOSE_KINDS }
            .forEach { f -> other(f.folder, itemOf(f)) }

        val byKey = roms.associateBy { it.stableKey }
        val strays = strayGames.values.associateBy { it.stableKey }
        return items.mapNotNull { (key, list) ->
            // Update 112: a game that isn't here is filed under its console's full name, with the library's own consoles
            // (GameCube, not "gc"; Game Boy, not "gb").
            // Update 119: a Game Boy Color game kept in a Game Boy folder (a .gbc file) is filed under Game Boy Color.
            val g = byKey[key]?.let { lib ->
                if (lib.platform.equals("GB", true) && lib.romPath?.endsWith(".gbc", true) == true) lib.copy(platform = "GBC") else lib
            } ?: strays[key]?.let { it.copy(platform = canonicalPlatform(it.platform)) } ?: return@mapNotNull null
            // The same save seen twice (on the card and synced, two card copies): one row.
            val unique = list.distinctBy { Triple(it.title.lowercase(), it.where.substringBefore(" · synced").lowercase(), it.kind == Kind.MEMORY_CARD) }
                .sortedByDescending { it.modified }
            Capsule(g, unique, statuses[key], lastPlayed.containsKey(key), onDevice = key in byKey, named = key in byKey || isNamedStray(g))
        }
    }

    private enum class Platform { PS1, PS2, PSP }

    /** 0.6.18: a save that isn't one of your games still says which game it is (not "Unassigned …", "Other saves",
     *  a number or a code). */
    private fun isNamedStray(g: Game): Boolean {
        val rp = g.romPath.orEmpty()
        if (rp.startsWith("save:other:") || rp.startsWith("save:3ds:") || rp.startsWith("save:wii:")) return false
        if (g.name.startsWith("Unassigned ", ignoreCase = true)) return false
        return looksLikeTitle(com.shelf.launcher.ui.stripRegionTags(g.name))
    }

    /** A save's own title says which game it is (letters, not only a number or a code like "00000012" or "GZLE"). */
    private fun looksLikeTitle(t: String, code: String? = null): Boolean {
        val compact = t.replace(Regex("[\\s_.-]"), "")
        // A short year-like number is a real title ("1942"); longer numbers are file numbers.
        if (compact.all { it.isDigit() }) return compact.length in 3..4 && compact[0] != '0'
        if (compact.count { it.isLetter() } < 3) return false
        // Hex-like names ("8a3f00c1") and the save's own game code ("GZLE01") aren't titles.
        if (compact.length >= 8 && compact.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return false
        if (code != null && (compact.equals(code, true) || compact.equals(code.take(4), true))) return false
        return true
    }

    /** Update 119: RetroAchievements' cached game lists of the Game Boy family (read once per vault build, only when needed). */
    private class HandheldLists(private val context: Context) {
        private val lists: Map<String, RaTitleMatcher.Index> by lazy {
            val cache = RaGameListCache(context)
            listOf("gb" to 4, "gbc" to 6, "gba" to 5).mapNotNull { (k, id) ->
                cache.get(id, Long.MAX_VALUE)?.let { k to RaTitleMatcher.buildIndex(it) }
            }.toMap()
        }
        /** The consoles (of [among]) whose list has [title]; empty when none of their lists is here. */
        // (Update 238, #111: allowHacks — only the console is asked here, never an achievements link.)
        fun onWhich(title: String, among: List<String>): List<String> = among.filter { k -> lists[k]?.let { RaTitleMatcher.match(title, it, allowHacks = true) } != null }
    }

    /**
     * Update 119: Game Boy or Game Boy Color (or Advance, for mGBA's folder) for a save not tied to one of your games.
     * Emulators keep these consoles' saves in one folder (Gambatte, SameBoy, mGBA…), so the folder alone said "Game
     * Boy" for every Color game. Now, in order: the file name's tags ("(GB Compatible)" is a Color cartridge that also
     * runs on a Game Boy, "(SGB Enhanced)" alone a Game Boy one), the title ("… DX", "… Color"), then
     * RetroAchievements' game lists of those consoles already on the device. Other consoles are left as they are.
     */
    private fun handheldOf(platform: String, fileTitle: String, lists: HandheldLists): String {
        val among = when (platform) {
            "gb", "gbc" -> listOf("gb", "gbc")
            "gba" -> listOf("gba", "gb", "gbc")
            else -> return platform
        }
        val n = fileTitle.lowercase()
        if ("(gb compatible)" in n || "(gbc)" in n || "(cgb)" in n || "game boy color" in n || "gameboy color" in n) return "gbc"
        val bare = com.shelf.launcher.ui.stripRegionTags(fileTitle).lowercase()
        if (platform != "gba" && (Regex("\\bdx\\b").containsMatchIn(bare) || bare.endsWith(" color"))) return "gbc"
        if ("(sgb enhanced)" in n && platform != "gba") return "gb"
        val on = lists.onWhich(fileTitle, among)
        return if (on.size == 1) on.first() else platform
    }

    private fun isGameCube(platform: String): Boolean = platform.lowercase().let { it == "gamecube" || it == "gc" || it == "ngc" || it.contains("gamecube") }

    /**
     * Update 112: the console key the library uses (ConsoleNames) for a save's own console id ("gb", "gc", "psx",
     * "PS2"…), so the vault groups it with that console and shows its full name. Unknown ids stay as they are.
     */
    internal fun canonicalPlatform(id: String): String = when (id.lowercase()) {
        "gb" -> "GB"; "gbc" -> "GBC"; "gba" -> "GBA"
        "nes", "fc" -> "NES"; "snes", "sfc" -> "SNES"; "n64" -> "N64"
        "nds" -> "NDS"; "n3ds", "3ds" -> "3DS"; "gc", "ngc", "gamecube" -> "GameCube"; "wii" -> "Wii"
        "megadrive", "genesis", "md" -> "Genesis"; "mastersystem", "sms" -> "MasterSystem"; "gamegear", "gg" -> "GameGear"
        "segacd" -> "SegaCD"; "sega32x", "32x" -> "32X"; "saturn" -> "Saturn"; "dreamcast", "dc" -> "Dreamcast"
        "psx", "ps1" -> "PS1"; "ps2" -> "PS2"; "psp" -> "PSP"
        "pcengine", "tg16" -> "PCEngine"; "neogeo" -> "NeoGeo"; "ngp", "ngpc" -> "NeoGeoPocket"
        "wonderswan", "wonderswancolor" -> "WonderSwan"; "atari2600" -> "Atari2600"; "atari7800" -> "Atari7800"
        "atarilynx" -> "Lynx"; "atarijaguar" -> "Jaguar"; "arcade" -> "Arcade"; "virtualboy" -> "VirtualBoy"
        "other" -> "Other saves"
        else -> id
    }

    /** The console a loose save belongs to (update 104: it used to be "other" for every RetroArch / generic save). */
    private fun loosePlatform(f: CloudSync.LooseSave): String {
        when (f.kind) {
            SaveKind.N64 -> return "n64"
            SaveKind.DOLPHIN_GC -> return "gc"
            SaveKind.NDS -> return "nds"
            SaveKind.PS1_MEMCARDS -> return "psx"
            SaveKind.PS2_MEMCARDS -> return "ps2"
            SaveKind.PSP_SAVEDATA -> return "psp"
            SaveKind.DREAMCAST_VMU -> return "dreamcast"
            SaveKind.DOLPHIN_WII -> return "wii"
            SaveKind.CITRA -> return "n3ds"
            else -> Unit
        }
        val ext = f.path.substringAfterLast('.', "").lowercase()
        when (ext) {
            "eep", "sra", "fla", "mpk" -> return "n64"
            "dsv" -> return "nds"
            "gci" -> return "gc"
            "vmu", "vms" -> return "dreamcast"
            "ps2" -> return "ps2"
            "mcr", "mcd", "gme", "vgs", "psm", "ddf", "vm1" -> return "psx"
        }
        // The folders it sits in: RetroArch keeps saves per core ("saves/Snes9x/…") or per system ("saves/snes/…").
        f.path.split('/').dropLast(1).reversed().forEach { part ->
            val p = part.lowercase().trim()
            CORE_PLATFORMS.entries.firstOrNull { (k, _) -> p == k || p.startsWith("$k ") || p.startsWith("$k-") }?.let { return it.value }
            if (p in SYSTEM_FOLDERS) return p
        }
        return "other"
    }

    private val CORE_PLATFORMS = linkedMapOf(
        "snes9x" to "snes", "bsnes" to "snes", "mesen-s" to "snes", "beetle supafaust" to "snes",
        "mgba" to "gba", "vba-m" to "gba", "vbam" to "gba", "vba next" to "gba", "gpsp" to "gba", "meteor" to "gba",
        "gambatte" to "gb", "sameboy" to "gb", "gearboy" to "gb", "tgbdual" to "gb",
        "genesis plus gx" to "megadrive", "picodrive" to "megadrive", "blastem" to "megadrive",
        "mupen64plus" to "n64", "parallel n64" to "n64", "parallel-n64" to "n64",
        "fceumm" to "nes", "nestopia" to "nes", "mesen" to "nes", "quicknes" to "nes",
        "beetle psx" to "psx", "swanstation" to "psx", "duckstation" to "psx", "pcsx-rearmed" to "psx", "pcsx rearmed" to "psx",
        "flycast" to "dreamcast", "redream" to "dreamcast",
        "melonds" to "nds", "desmume" to "nds", "drastic" to "nds",
        "ppsspp" to "psp",
        "beetle pce" to "pcengine", "mednafen pce" to "pcengine",
        "beetle saturn" to "saturn", "mednafen saturn" to "saturn", "yabause" to "saturn", "kronos" to "saturn", "yabasanshiro" to "saturn",
        "pcsx2" to "ps2", "lrps2" to "ps2", "dolphin" to "gc", "citra" to "n3ds",
        "beetle wonderswan" to "wonderswan", "handy" to "atarilynx", "stella" to "atari2600", "prosystem" to "atari7800",
        "fbneo" to "arcade", "mame" to "arcade", "beetle ngp" to "ngp", "virtual jaguar" to "atarijaguar"
    )

    private val SYSTEM_FOLDERS = setOf(
        "nes", "snes", "sfc", "n64", "gb", "gbc", "gba", "nds", "n3ds", "gc", "wii", "megadrive", "genesis", "mastersystem",
        "gamegear", "segacd", "sega32x", "saturn", "dreamcast", "psx", "ps1", "ps2", "psp", "pcengine", "tg16", "neogeo",
        "atari2600", "atari7800", "atarilynx", "atarijaguar", "wonderswan", "wonderswancolor", "ngp", "ngpc", "arcade", "virtualboy"
    )

    private fun platformKind(platform: String): Platform? {
        val p = platform.lowercase()
        return when {
            p == "ps2" || p.contains("playstation 2") -> Platform.PS2
            p == "psp" || p.contains("portable") -> Platform.PSP
            p == "ps1" || p == "psx" || p == "playstation" -> Platform.PS1
            else -> null
        }
    }

    /** Public folders emulators keep cards in, looked at even without Google Drive set up. */
    private val KNOWN_CARD_DIRS = listOf(
        "RetroArch/saves", "duckstation/memcards", "DuckStation/memcards", "ePSXe/memcards", "epsxe/memcards",
        "SwanStation/memcards", "swanstation/memcards", "PCSX2/memcards", "pcsx2/memcards",
        "ARMSX2/memcards", "NetherSX2/memcards", "AetherSX2/memcards", "PSP/SAVEDATA",
        // App folders: readable on older Android versions and on handhelds that allow it.
        "Android/data/com.github.stenzek.duckstation/files/memcards",
        "Android/data/xyz.aethersx2.android/files/memcards",
        "Android/data/com.retroarch/files/saves", "Android/data/com.retroarch.aarch64/files/saves",
        "Android/data/com.epsxe.ePSXe/files/epsxe/memcards"
    )

    private fun readCards(context: Context): List<Pair<Platform, MemoryCards.CardSave>> {
        val out = mutableListOf<Pair<Platform, MemoryCards.CardSave>>()
        val seenRoots = HashSet<String>()
        // Synced folders first (they may be shared trees), then the known public folders.
        CloudSync.memoryCardTrees(context).forEach { (name, kind, tree) ->
            seenRoots += name
            runCatching { readTree(name, kind, tree, out) }
        }
        val ext = android.os.Environment.getExternalStorageDirectory()
        KNOWN_CARD_DIRS.forEach { rel ->
            val dir = java.io.File(ext, rel)
            if (!dir.isDirectory) return@forEach
            val kind = when {
                rel.startsWith("PSP") -> SaveKind.PSP_SAVEDATA
                rel.contains("SX2", ignoreCase = true) || rel.contains("pcsx2", ignoreCase = true) -> SaveKind.PS2_MEMCARDS
                rel.contains("retroarch", ignoreCase = true) -> SaveKind.RETROARCH
                else -> SaveKind.PS1_MEMCARDS
            }
            runCatching { readTree(rel, kind, FileTree(dir), out) }
        }
        // The same card reached twice (a synced folder that is also a known folder): one copy.
        return out.distinctBy { (_, s) -> Triple(s.folder, s.title, s.sizeBytes) }
    }

    private fun readTree(name: String, kind: SaveKind, tree: SyncTree, out: MutableList<Pair<Platform, MemoryCards.CardSave>>) {
        val files = tree.list()
        // PS2 folder cards: "Mcd001.ps2/BASLUS-20312/icon.sys"
        val folderCards = HashMap<String, HashMap<String, Triple<ByteArray?, Long, Long>>>()
        // (Update 239, #112: each save folder's own files — lower-case name → (path, size) — so its browser icon can be read
        // when the PS2 browser style shows it.)
        val folderFiles = HashMap<String, HashMap<String, HashMap<String, Pair<String, Long>>>>()
        // PSP: "ULUS10041DATA00/PARAM.SFO"
        val psp = HashMap<String, Triple<ByteArray?, Long, Long>>()
        files.forEach { (rel, e) ->
            val parts = rel.split('/')
            val fileName = parts.last()
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val cardDirIndex = parts.dropLast(1).indexOfFirst { it.lowercase().endsWith(".ps2") }
            when {
                cardDirIndex >= 0 && parts.size >= cardDirIndex + 3 -> {
                    val card = parts[cardDirIndex]
                    val save = parts[cardDirIndex + 1]
                    val m = folderCards.getOrPut(card) { HashMap() }
                    val old = m[save] ?: Triple(null, 0L, 0L)
                    val icon = if (fileName.equals("icon.sys", true) && parts.size == cardDirIndex + 3)
                        runCatching { tree.open(rel).use { it.readBytes() } }.getOrNull() else old.first
                    m[save] = Triple(icon ?: old.first, old.second + e.size, maxOf(old.third, e.modified))
                    if (parts.size == cardDirIndex + 3) // (Update 239, #112)
                        folderFiles.getOrPut(card) { HashMap() }.getOrPut(save) { HashMap() }[fileName.lowercase()] = rel to e.size
                }
                kind == SaveKind.PSP_SAVEDATA || parts.any { it.equals("SAVEDATA", true) } -> {
                    if (parts.size < 2) return@forEach
                    val save = parts[parts.size - 2]
                    val old = psp[save] ?: Triple(null, 0L, 0L)
                    val sfo = if (fileName.equals("PARAM.SFO", true)) runCatching { tree.open(rel).use { it.readBytes() } }.getOrNull() else old.first
                    psp[save] = Triple(sfo ?: old.first, old.second + e.size, maxOf(old.third, e.modified))
                }
                ext == "ps2" && e.size >= 8_000_000 -> {
                    val key = CardKey("$name/$rel", e.size, e.modified)
                    val saves = synchronized(cardCache) { cardCache[key] } ?: run {
                        val bytes = tree.open(rel).use { it.readBytes() }
                        MemoryCards.readPs2File(fileName, bytes, e.modified).also { synchronized(cardCache) { cardCache[key] = it } }
                    }
                    saves.forEach { out += Platform.PS2 to it }
                }
                ext in PS1_CARD_EXTS && e.size in 131_072L..140_000L -> {
                    val key = CardKey("$name/$rel", e.size, e.modified)
                    val saves = synchronized(cardCache) { cardCache[key] } ?: run {
                        val bytes = tree.open(rel).use { it.readBytes() }
                        MemoryCards.readPs1(fileName, bytes, e.modified).also { synchronized(cardCache) { cardCache[key] = it } }
                    }
                    saves.forEach { out += Platform.PS1 to it }
                }
                // Update 109: a card kept under another name in a memory-card folder, known by its size and content.
                (kind == SaveKind.PS1_MEMCARDS || kind == SaveKind.PS2_MEMCARDS) && ext != "ps2" && ext !in PS1_CARD_EXTS &&
                    (e.size in 131_072L..140_000L || e.size in PS2_SIZES) -> {
                    val key = CardKey("$name/$rel", e.size, e.modified)
                    val cached = synchronized(remoteCache) { remoteCache[key] }
                    val read = cached ?: run {
                        val bytes = tree.open(rel).use { it.readBytes() }
                        val r = runCatching { MemoryCards.readAny(fileName, bytes, e.modified) }.getOrNull()
                        RemoteCard(r?.first ?: 0, r?.second.orEmpty()).also { synchronized(remoteCache) { remoteCache[key] = it } }
                    }
                    read.saves.forEach { out += (if (read.type == 2) Platform.PS2 else Platform.PS1) to it }
                }
            }
        }
        folderCards.forEach { (card, dirs) ->
            // (Update 239, #112: a reader per save folder, for its icon file — only called when the icon is shown.)
            val readers = folderFiles[card].orEmpty().mapValues { (_, own) ->
                { wanted: String ->
                    own[wanted.lowercase()]?.takeIf { it.second in 20L..Ps2Icons.MAX_ICON_BYTES.toLong() }
                        ?.let { (path, _) -> runCatching { tree.open(path).use { it.readBytes() } }.getOrNull() }
                }
            }
            MemoryCards.readPs2Folder(card, dirs, readers).forEach { out += Platform.PS2 to it }
        }
        psp.forEach { (dir, info) ->
            val title = info.first?.let { sfoTitle(it) } ?: dir
            out += Platform.PSP to MemoryCards.CardSave("SAVEDATA", dir, MemoryCards.normaliseCode(dir), title, info.second, info.third)
        }
    }

    /** Update 151: the kinds of Drive-only saves that join the loose saves (named after their game, GameCube, Wii). */
    // (Update 241, #72 / #76: and 3DS — a Drive-only Citra / Azahar folder went to "Other saves" as one lump per folder,
    // never under its games.)
    private val DRIVE_LOOSE_KINDS = setOf(SaveKind.RETROARCH, SaveKind.GENERIC, SaveKind.NDS, SaveKind.N64, SaveKind.DOLPHIN_GC, SaveKind.DOLPHIN_WII, SaveKind.CITRA)

    /** (Update 241) The Wii folder grouping of update 151 ("<type>/<id>/data/…"), for files SaveLayouts.wii doesn't take. */
    private fun legacyWiiUnit(rel: String): SaveLayouts.TitleUnit? {
        val parts = rel.split('/')
        if (parts.size < 2) return null
        return SaveLayouts.TitleUnit("wii", parts.take(2).joinToString("/"), parts[0].lowercase(), parts[1].lowercase(), "")
    }

    /** (Update 241) The 3DS folder grouping of update 223 (any "title/<high>/<low>/data/…"), for CITRA files SaveLayouts.n3ds doesn't take. */
    private fun legacyN3dsUnit(rel: String): SaveLayouts.TitleUnit? {
        val parts = rel.split('/')
        val t = parts.indexOf("title")
        if (t < 0 || parts.size < t + 4 || parts[t + 3] != "data") return null
        return SaveLayouts.TitleUnit("3ds", parts.take(t + 4).joinToString("/"), parts[t + 1].lowercase(), parts[t + 2].lowercase(), parts.take(t).joinToString("/"))
    }

    /** Update 151: a Wii title id's game part ("524d4345") as its code ("RMCE"); the hex itself when it isn't letters. */
    private fun wiiCode(hex: String): String {
        if (hex.length != 8) return hex.uppercase().ifBlank { "?" }
        val chars = (0 until 4).map { i -> hex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: 0 }
        return if (chars.all { it in 0x30..0x39 || it in 0x41..0x5A }) chars.joinToString("") { it.toChar().toString() } else hex.uppercase()
    }

    /** A shared card's name ("Mcd001", "epsxe000", "shared_card", "card1"…), not a game's. */
    private val SHARED_CARD = Regex("^(mcd|mc|card|memcard|memorycard|shared_card|epsxe|slot|memory ?card)[ _-]?\\d*$", RegexOption.IGNORE_CASE)

    /** A loose save that is (probably) a memory card, by its name, type, folder kind and size (update 109: wider). */
    private fun looksLikeCard(f: CloudSync.LooseSave): Boolean {
        val name = f.path.substringAfterLast('/')
        val ext = name.substringAfterLast('.', "").lowercase()
        val stem = name.substringBeforeLast('.')
        return ext == "ps2" || (ext in PS1_CARD_EXTS && ext != "srm") ||
            (ext == "srm" && SHARED_CARD.matches(stem)) ||
            // A card kept under another name in a memory-card folder: its size gives it away (read to be sure).
            ((f.kind == SaveKind.PS1_MEMCARDS || f.kind == SaveKind.PS2_MEMCARDS) && (f.size in 131_072L..140_000L || f.size in PS2_SIZES))
    }

    private val PS2_SIZES = setOf(8_388_608L, 8_650_752L, 16_777_216L, 17_301_504L, 33_554_432L, 34_603_008L, 67_108_864L, 69_206_016L)

    private data class RemoteCard(val type: Int, val saves: List<MemoryCards.CardSave>)
    private val remoteCache = HashMap<CardKey, RemoteCard>()

    /**
     * Memory cards that are only on Drive (synced from another device, update 108): downloaded once (cached) and read
     * like the cards on this device. [opened] gets "folder|path" of each card that could be read (update 109: even an
     * empty one — it then has no capsule, rather than a card with nothing in it), [notCards] each file that turned out
     * not to be a card at all (a plain save named like one).
     */
    private fun remoteCards(
        context: Context, cardFolderFiles: List<CloudSync.LooseSave>, opened: MutableSet<String>, notCards: MutableSet<String>,
        net: Boolean = true
    ): List<Pair<Platform, MemoryCards.CardSave>> {
        val out = mutableListOf<Pair<Platform, MemoryCards.CardSave>>()
        val files = runCatching { CloudSync.looseSaveFiles(context) }.getOrDefault(emptyList()).filter { !it.onDevice } + cardFolderFiles
        // PS2 folder cards from another device ("Mcd001.ps2/BASLUS-20312/…", update 109): one save per folder, named
        // from its icon.sys (a small download) or else its product code.
        val folderCards = HashMap<String, HashMap<String, Triple<ByteArray?, Long, Long>>>()
        val folderFrom = HashMap<String, String?>()
        // (Update 239, #112: each save folder's files on Drive — lower-case name → (Drive folder, path, size) — for its icon.)
        val folderFiles = HashMap<String, HashMap<String, HashMap<String, Triple<String, String, Long>>>>()
        files.forEach { f ->
            val parts = f.path.split('/')
            val at = parts.dropLast(1).indexOfFirst { it.lowercase().endsWith(".ps2") }
            if (at < 0 || parts.size < at + 3) return@forEach
            val card = parts[at]; val save = parts[at + 1]
            val m = folderCards.getOrPut("${f.folder}|$card") { HashMap() }
            folderFrom.putIfAbsent("${f.folder}|$card", f.fromDevice)
            val old = m[save] ?: Triple(null, 0L, 0L)
            val icon = if (old.first == null && parts.last().equals("icon.sys", true) && parts.size == at + 3)
                (runCatching { CloudSync.remoteFileBytes(context, f.folder, f.path, maxBytes = 64_000, allowNetwork = net) }.getOrNull()
                    ?: DriveOnlySaves.bytes(context, f.folder, f.path, 64_000L, allowNetwork = net)) else old.first // (Update 151: or a Drive-only folder)
            // (Update 144: the save's size adds up its files, like the local twin above - it used to stay 0 B.)
            m[save] = Triple(icon, old.second + f.size, maxOf(old.third, f.modified))
            if (parts.size == at + 3) // (Update 239, #112)
                folderFiles.getOrPut("${f.folder}|$card") { HashMap() }.getOrPut(save) { HashMap() }[parts.last().lowercase()] = Triple(f.folder, f.path, f.size)
        }
        folderCards.forEach { (key, dirs) ->
            val from = " · synced from ${folderFrom[key] ?: "another device"}"
            // (Update 239, #112: its icon file fetched from Drive only when the PS2 browser style shows it — then kept in
            // Ps2Icons' small disk cache.)
            val readers = folderFiles[key].orEmpty().mapValues { (_, own) ->
                { wanted: String ->
                    own[wanted.lowercase()]?.takeIf { it.third in 20L..Ps2Icons.MAX_ICON_BYTES.toLong() }?.let { (folder, path, _) ->
                        runCatching { CloudSync.remoteFileBytes(context, folder, path, maxBytes = Ps2Icons.MAX_ICON_BYTES) }.getOrNull()
                            ?: runCatching { DriveOnlySaves.bytes(context, folder, path, Ps2Icons.MAX_ICON_BYTES.toLong()) }.getOrNull()
                    }
                }
            }
            MemoryCards.readPs2Folder(key.substringAfter('|'), dirs, readers).forEach { sv -> out += Platform.PS2 to sv.copy(folder = sv.folder + from) }
        }
        files.forEach { f ->
            if (!looksLikeCard(f)) return@forEach
            // (A file inside a folder card, handled above.)
            if (f.path.split('/').dropLast(1).any { it.lowercase().endsWith(".ps2") }) return@forEach
            val name = f.path.substringAfterLast('/')
            val key = CardKey("remote:${f.folder}/${f.path}", f.size, f.modified)
            val read = synchronized(remoteCache) { remoteCache[key] } ?: run {
                // (PS2 cards can be 8 to 64 MB; on a handheld with little memory only up to 16 MB ones are opened.)
                val cap = if (com.shelf.launcher.device.MemoryBudget.small(context)) 17_400_000 else 70_000_000
                // (Update 151: a card in a Drive folder this device doesn't sync is fetched by DriveOnlySaves.)
                val bytes = CloudSync.remoteFileBytes(context, f.folder, f.path, maxBytes = cap, allowNetwork = net)
                    ?: DriveOnlySaves.bytes(context, f.folder, f.path, cap.toLong(), allowNetwork = net) ?: return@forEach
                val r = runCatching { MemoryCards.readAny(name, bytes, f.modified) }.getOrNull()
                    .let { RemoteCard(it?.first ?: 0, it?.second.orEmpty()) }
                synchronized(remoteCache) { remoteCache[key] = r }
                r
            }
            if (read.type == 0) { notCards += "${f.folder}|${f.path}"; return@forEach }
            opened += "${f.folder}|${f.path}"
            val from = " · synced from ${f.fromDevice ?: "another device"}"
            read.saves.forEach { s -> out += (if (read.type == 2) Platform.PS2 else Platform.PS1) to s.copy(folder = s.folder + from) }
        }
        return out
    }

    private val PS1_CARD_EXTS = setOf("mcr", "mcd", "mc", "srm", "gme", "vgs", "mem", "psm", "ddf", "vm1")

    /** TITLE from a PSP PARAM.SFO. */
    internal fun sfoTitle(b: ByteArray): String? = sfoValue(b, "TITLE")

    /** One text value from a PSP PARAM.SFO ("TITLE", "DISC_ID"). */
    internal fun sfoValue(b: ByteArray, wanted: String): String? = runCatching {
        if (b.size < 20 || b[1] != 'P'.code.toByte() || b[2] != 'S'.code.toByte() || b[3] != 'F'.code.toByte()) return null
        fun u32(o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
        fun u16(o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
        val keyTable = u32(8); val dataTable = u32(12); val count = u32(16)
        for (i in 0 until count) {
            val e = 20 + i * 16
            val keyOff = u16(e); val len = u32(e + 4); val dataOff = u32(e + 12)
            val keyStart = keyTable + keyOff
            var keyEnd = keyStart
            while (keyEnd < b.size && b[keyEnd] != 0.toByte()) keyEnd++
            if (String(b, keyStart, keyEnd - keyStart, Charsets.US_ASCII) == wanted) {
                val start = dataTable + dataOff
                return String(b, start, len.coerceAtMost(b.size - start), Charsets.UTF_8).trimEnd('\u0000').trim()
            }
        }
        null
    }.getOrNull()
}
