package com.shelf.launcher.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.flow.first
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.GameSyncStatus
import com.shelf.launcher.data.SyncDirection
import com.shelf.launcher.data.SyncState
import com.shelf.launcher.data.SyncStatusStore
import com.shelf.launcher.ui.theme.ShelfTheme
import com.shelf.launcher.data.Features
import kotlinx.coroutines.launch

/** Lets screens outside the home screen (Settings › Sync) open the save browser. */
internal object SaveExplorerHook {
    @Volatile var open: (() -> Unit)? = null
    /**
     * Asks the home screen to open the browser. A counter it watches rather than a callback someone may
     * have cleared: the R1 tile sometimes did nothing because the home screen being rebuilt (dual screen
     * switch, theme change) had cleared the callback after the new one was set.
     */
    val requests = androidx.compose.runtime.mutableIntStateOf(0)
    fun request() { requests.intValue++ }
}

/**
 * The synced saves as a memory-card browser: a grid of game icons (RetroAchievements icon, else the
 * cover), the one you're on gently bobbing, and its details along the bottom — when it was last
 * saved to / fetched from Google Drive. Newest first by default; ◀ ▶ ▲ ▼ move, A opens the game's
 * save window, Y switches the order, B closes.
 */
@Composable
internal fun SaveExplorerDialog(
    games: List<Game>,
    iconFor: (Game) -> String?,
    onOpenGame: (Game) -> Unit,
    onDismiss: () -> Unit,
    /** When each game was last played (Game.stableKey → millis): the default order. */
    lastPlayed: Map<String, Long> = emptyMap(),
    /** A platform key as the user wants consoles named. */
    consoleName: (String) -> String = { it }
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { SyncStatusStore.load(context); com.shelf.launcher.data.SaveCapsules.loadOptions(context) }
    // Google Drive sync, right inside the vault: "Sync now" runs a saves sync, then looks for saves again.
    // (Update 151: read before the first frame, so a connected vault never flashes "Not connected".)
    remember { com.shelf.launcher.data.CloudSync.load(context) }
    val cloud by com.shelf.launcher.data.CloudSync.state.collectAsState()
    LaunchedEffect(Unit) { com.shelf.launcher.data.CloudSync.load(context) }
    // Update 151: without Google Drive the vault shows only that, with a button to connect it (as in Settings › Sync).
    val connected = cloud.settings.connected
    // Only games with real save evidence (save files, memory-card / SAVEDATA saves, saves Drive moved):
    // looking at a game never makes a capsule. Read again each time the browser opens.
    LaunchedEffect(games, lastPlayed, connected) { if (connected) com.shelf.launcher.data.SaveCapsules.refresh(context, games, lastPlayed) }
    val capsules by com.shelf.launcher.data.SaveCapsules.all.collectAsState()
    val capsuleOptions by com.shelf.launcher.data.SaveCapsules.options.collectAsState()
    val scanning by com.shelf.launcher.data.SaveCapsules.loading.collectAsState()
    val syncScope = androidx.compose.runtime.rememberCoroutineScope()
    val syncNow: () -> Unit = {
        if (cloud.settings.connected && !cloud.running) {
            syncScope.launch {
                runCatching { com.shelf.launcher.data.CloudSync.syncInBackground(context, savesOnly = true)?.await() }
                // Update 151: Drive's folders listed again too (saves from folders this device doesn't sync).
                com.shelf.launcher.data.DriveOnlySaves.invalidate()
                com.shelf.launcher.data.SaveCapsules.refresh(context, games, lastPlayed)
            }
        }
    }
    var connectProblem by remember { mutableStateOf<String?>(null) }
    var connecting by remember { mutableStateOf(false) }
    val driveConnect = rememberDriveConnect { connectProblem = it; connecting = false }
    val connectDrive: () -> Unit = {
        if (!connecting) {
            connecting = true
            connectProblem = null
            driveConnect {
                connecting = false
                syncScope.launch {
                    // Connecting starts a sync: look for saves again once it's done.
                    kotlinx.coroutines.delay(400)
                    kotlinx.coroutines.withTimeoutOrNull(10 * 60_000L) { com.shelf.launcher.data.CloudSync.state.first { !it.running } }
                    com.shelf.launcher.data.DriveOnlySaves.invalidate()
                    com.shelf.launcher.data.SaveCapsules.refresh(context, games, lastPlayed)
                }
            }
        }
    }
    val connectFocus = remember { FocusRequester() }
    LaunchedEffect(connected) { if (!connected) { kotlinx.coroutines.delay(80); runCatching { connectFocus.requestFocus() } } }
    // Update 151: on a small screen (Retroid Pocket Classic, 620 x 540 dp) the header's buttons go on a line of their own
    // under the title, and the save's details window gets a smaller icon and scrolls (labels were cut and overlapping).
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 760
    var showHidden by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("save_explorer", android.content.Context.MODE_PRIVATE) }
    var byName by remember { mutableStateOf(false) }
    // Saves grouped by console (on by default): the consoles across the top, their saves below.
    var byConsole by remember { mutableStateOf(prefs.getBoolean("by_console", true)) }
    val capsuleByKey = remember(capsules) { capsules.associateBy { it.game.stableKey } }
    // Newest = last played (then last saved) by default; Y switches to A–Z.
    fun recency(e: Pair<Game, GameSyncStatus>): Long =
        maxOf(lastPlayed[e.first.stableKey] ?: 0L, (capsuleByKey[e.first.stableKey]?.lastSaved ?: maxOf(e.second.lastSuccessAt, e.second.updatedAt)) / 2)
    val vaultFilter = Features.saveVaultFilter.state.value
    val all = remember(capsules, capsuleOptions, showHidden, byName, lastPlayed, vaultFilter) {
        val shown = (if (showHidden) capsules.filter { it.game.stableKey in capsuleOptions.hidden }
            else com.shelf.launcher.data.SaveCapsules.visible(capsules, capsuleOptions))
            .filter { com.shelf.launcher.data.SaveCapsules.matches(it, vaultFilter) }
        val list = shown.map { c ->
            c.game to (c.status ?: GameSyncStatus(state = SyncState.PENDING, updatedAt = c.lastSaved))
        }
        if (byName) list.sortedBy { stripRegionTags(it.first.name).lowercase() }
        else list.sortedByDescending { recency(it) }
    }
    // Consoles, the one played most recently first (A–Z with Y).
    val consoles = remember(all, byName) {
        val groups = all.groupBy { it.first.platform }
        val keys = groups.keys.toList()
        if (byName) keys.sortedBy { consoleName(it).lowercase() }
        else keys.sortedByDescending { k -> groups[k].orEmpty().maxOfOrNull { recency(it) } ?: 0L }
    }
    // Grouped: every console is a section (a plain header with a thin line), its saves right under it,
    // all in one scroll. The flat list keeps the sections' order, so `selected` is one index throughout.
    val sections = remember(all, byConsole, consoles) {
        if (!byConsole) listOf("" to all)
        else consoles.map { k -> k to all.filter { it.first.platform == k } }.filter { it.second.isNotEmpty() }
    }
    val entries = remember(sections) { sections.flatMap { it.second } }
    /** Flat index of each section's first save. */
    val sectionStart = remember(sections) {
        var n = 0
        sections.map { val s0 = n; n += it.second.size; s0 }
    }
    // Endless grid (Save Vault › Endless scrolling): the saves repeat, so scrolling (or the D-pad) never hits
    // an end; it starts in the middle copy. `selected` is a global index over all copies; [loc] gives the
    // save's own index. Only with enough saves to fill the screen (a few saves would repeat side by side).
    val n = entries.size
    val endless = Features.saveVaultEndless.state.value && n >= 12
    val loops = if (endless) 31 else 1
    val perCopy = n + (if (byConsole) sections.size else 0)
    fun loc(g: Int): Int = if (n > 0) ((g % n) + n) % n else 0
    fun copyOf(g: Int): Int = if (n > 0) (g / n).coerceIn(0, loops - 1) else 0
    fun home(): Int = if (endless) (loops / 2) * n else 0
    fun sectionOf(index: Int): Int = sectionStart.indexOfLast { it <= loc(index) }.coerceAtLeast(0)
    /** Where a section's header sits in the grid (each earlier section adds one header item); [copy] = which repeat. */
    fun headerGridIndex(section: Int, copy: Int = 0): Int = copy * perCopy + sectionStart.getOrElse(section) { 0 } + section
    /** The grid index of the save at global index [g] (headers counted). */
    fun gridIndexOf(g: Int): Int = copyOf(g) * perCopy + loc(g) + (if (byConsole) sectionOf(g) + 1 else 0)
    var selected by remember { mutableStateOf(0) }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    // L1 / R1: jump to the previous / next console's first save, its header scrolled to the top.
    var jumpTo by remember { mutableStateOf(-1) }
    val firstFocus = remember { FocusRequester() }
    // Endless: start (and restart after a re-sort) in the middle copy, so both directions go on.
    LaunchedEffect(endless, n, byConsole, byName) {
        selected = home()
        if (endless) runCatching { gridState.scrollToItem(headerGridIndex(0, loops / 2)) }
    }
    // Slots slide in once, staggered, when the browser opens (not again when scrolled back to).
    val entered = remember { HashSet<String>() }
    // Moving onto the first row scrolls all the way up, so its lifted icons stay inside the window.
    LaunchedEffect(selected) {
        val info = gridState.layoutInfo.visibleItemsInfo
        val cols = info.filter { it.key.toString().startsWith("h:").not() }
            .let { l -> l.firstOrNull()?.let { f -> l.count { it.row == f.row } } }?.coerceAtLeast(1) ?: 1
        val sec = sectionOf(selected)
        // On a section's first row: its header comes into view with it.
        if (loc(selected) - sectionStart.getOrElse(sec) { 0 } < cols) {
            val h = if (byConsole) headerGridIndex(sec, copyOf(selected)) else if (endless) gridIndexOf(selected) else 0
            if (info.none { it.index == h } || (h == 0 && gridState.firstVisibleItemScrollOffset > 0)) gridState.animateScrollToItem(h)
        }
    }
    LaunchedEffect(jumpTo) {
        if (jumpTo < 0) return@LaunchedEffect
        runCatching { gridState.animateScrollToItem(headerGridIndex(jumpTo, copyOf(selected))) }
        jumpTo = -1
        // The section's first save is composed now: focus it (the D-pad carries on from there).
        kotlinx.coroutines.delay(30)
        runCatching { firstFocus.requestFocus() }
    }
    // Focus lives on the selected slot: when the details window closes it goes straight back there
    // (it used to land nowhere in particular, so the highlight vanished and the D-pad jumped elsewhere).
    var refocus by remember { mutableStateOf(0) }
    LaunchedEffect(entries.isNotEmpty(), byName, byConsole, refocus) {
        // Also after Y re-sorts the grid (the selection goes back to the first slot).
        if (entries.isNotEmpty()) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } }
    }
    // A press: the save's icon lifts out of the grid and flies (in an arc, turning once) into the details
    // window, landing in its spot there, where it keeps turning slowly. Back flies it home again.
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var detail by remember { mutableStateOf<Pair<Game, GameSyncStatus>?>(null) }
    /** Where each slot's icon is on screen (root px), for the flight's start / end. Not state: read on press. */
    val slotRects = remember { HashMap<String, androidx.compose.ui.geometry.Rect>() }
    var hostOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    /** The icon's spot in the details window, once laid out. */
    var targetRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var flightFrom by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val flight = remember { androidx.compose.animation.core.Animatable(0f) }
    /** The save whose icon is in the air (out or back). */
    var flying by remember { mutableStateOf<Pair<Game, GameSyncStatus>?>(null) }
    var closing by remember { mutableStateOf(false) }
    /** The icon has arrived in the details window (a light sweeps across it from now on). */
    var landed by remember { mutableStateOf(false) }
    val sounds = LocalUiSounds.current
    /** The capsule cracking open in its slot (Capsule style with animations on), before its save comes out. */
    var crackFor by remember { mutableStateOf<String?>(null) }
    val crack = remember { androidx.compose.animation.core.Animatable(0f) }
    fun open(entry: Pair<Game, GameSyncStatus>) {
        if (flying != null || detail != null || crackFor != null) return
        sounds?.play(UiSound.OPEN)
        flightFrom = slotRects["g:$selected"] ?: slotRects[entry.first.stableKey]
        targetRect = null
        landed = false
        closing = false
        // (Update 141: the capsule cracking open before its save comes out is gone, at your request.)
        val crackIt = false
        scope.launch {
            if (crackIt) {
                // Right where the capsule sits: it shakes, light leaks from the seam, the halves twist apart —
                // then the save rises out of it into the details window.
                crackFor = entry.first.stableKey
                crack.snapTo(0f)
                crack.animateTo(1f, tween(780, easing = androidx.compose.animation.core.LinearEasing))
            }
            flying = entry
            detail = entry
            // The details window lays out first, so the flight knows where to land.
            kotlinx.coroutines.withTimeoutOrNull(600) {
                androidx.compose.runtime.snapshotFlow { targetRect }.first { it != null }
            }
            // The cracked capsule holds its save until the flight takes it.
            crackFor = null
            flight.snapTo(0f)
            if (flightFrom != null && targetRect != null) {
                flight.animateTo(1f, tween(720, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            } else flight.snapTo(1f)
            landed = true
            flying = null
        }
    }
    fun close() {
        val d = detail ?: return
        if (closing) return
        closing = true
        // The slot may have moved (scrolling) - fly back to where it is now.
        flightFrom = slotRects["g:$selected"] ?: slotRects[d.first.stableKey] ?: flightFrom
        landed = false
        flying = d
        scope.launch {
            flight.snapTo(1f)
            if (flightFrom != null && targetRect != null) {
                flight.animateTo(0f, tween(520, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            }
            flying = null
            detail = null
            // Back onto the save you had picked: scrolled into view, highlighted, D-pad from here.
            val idx = gridIndexOf(selected)
            if (gridState.layoutInfo.visibleItemsInfo.none { it.index == idx }) runCatching { gridState.scrollToItem(idx) }
            kotlinx.coroutines.delay(40)
            runCatching { firstFocus.requestFocus() }
            closing = false
        }
    }

    // Everything floats on one slow clock: idle capsules bob out of step, the picked one lifts.
    val clock = rememberInfiniteTransition(label = "memcard")
    // Read only while drawing (lambdas below): reading them here recomposed the whole browser every frame.
    val phaseState = clock.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(2600, easing = androidx.compose.animation.core.LinearEasing)), label = "phase")
    val spinState = if (Features.saveTurn.state.value) {
        clock.animateFloat(0f, 360f, infiniteRepeatable(tween(4200, easing = androidx.compose.animation.core.LinearEasing)), label = "spin")
    } else null
    val phase: () -> Float = { phaseState.value }
    val spin: () -> Float = { spinState?.value ?: 0f }

    Dialog(
        onDismissRequest = { if (detail != null) close() else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ImmersiveDialogWindow()
        // Update 151: the vault is always dark, so its buttons and notes keep light text with a light theme too (they
        // were dark on dark, hard to read).
        val baseColors = ShelfTheme.colors
        val vaultColors = remember(baseColors) {
            if (baseColors.textPrimary.luminance() < 0.5f) baseColors.copy(textPrimary = Color.White, textMuted = Color.White.copy(alpha = 0.62f)) else baseColors
        }
        androidx.compose.runtime.CompositionLocalProvider(com.shelf.launcher.ui.theme.LocalShelfColors provides vaultColors) {
        Box(
            Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .onGloballyPositioned { hostOffset = it.positionInRoot() }
        ) {
            // The background (Save Vault settings): the theme's (a glow and drifting specks of light),
            // plain, the picked game's art blurred, or floating save icons.
            val accentBg = ShelfTheme.accent.copy(alpha = 0.26f).compositeOverDark()
            SaveVaultBackground(
                phase, icons = remember(entries) { entries.take(24).mapNotNull { iconFor(it.first) } },
                artUrl = entries.getOrNull(loc(selected))?.let { iconFor(it.first) },
                modifier = Modifier.matchParentSize()
            ) {
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(accentBg, Color(0xFF05060B)))))
                MemoryCardSky(phase, ShelfTheme.accent, Modifier.matchParentSize())
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .onKeyEvent { e ->
                        when {
                            detail != null || e.type != KeyEventType.KeyUp -> false
                            e.key == Key.ButtonY -> { byName = !byName; selected = home(); true }
                            e.key == Key.ButtonX -> {
                                byConsole = !byConsole; selected = home()
                                prefs.edit().putBoolean("by_console", byConsole).apply(); true
                            }
                            // L1 / R1: previous / next console section (wraps round).
                            byConsole && (e.key == Key.ButtonL1 || e.key == Key.ButtonR1) && sections.size > 1 -> {
                                val cur = sectionOf(selected)
                                val next = if (e.key == Key.ButtonR1) (cur + 1) % sections.size else (cur - 1 + sections.size) % sections.size
                                selected = copyOf(selected) * n + sectionStart[next]; jumpTo = next; refocus++
                                sounds?.play(UiSound.MOVE); true
                            }
                            else -> false
                        }
                    }
                    .padding(if (compact) 12.dp else 18.dp)
            ) {
                // The vault's buttons (Update 151: one list, beside the title on a wide screen, on their own line under it on
                // a small one; without Google Drive only Close).
                val headerChips: @Composable () -> Unit = {
                    Chip(
                        when {
                            cloud.running -> "⟳ Syncing…"
                            else -> "⟳ Sync"
                        },
                        accent = cloud.running, onClick = syncNow
                    )
                    Chip(if (showFilters) "Filters & look ▴" else "Filters & look ▾", accent = showFilters) { showFilters = !showFilters }
                    Chip(if (byConsole) "By console (X)" else "All saves (X)", accent = false) {
                        byConsole = !byConsole; selected = home()
                        prefs.edit().putBoolean("by_console", byConsole).apply()
                    }
                    Chip(if (byName) "A–Z (Y)" else "Recent (Y)", accent = false) { byName = !byName; selected = home() }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            com.shelf.launcher.data.SAVES_NAME, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(20.sp), maxFontSize = 20.sp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (connected) Text(
                            "${all.size} game${if (all.size == 1) "" else "s"} with saves · " + (if (byName) "A–Z" else "last played first") +
                                if (byConsole && consoles.size > 1) " · L1 / R1 console" else "",
                            color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    VoltHelpButton(VoltTopic.SAVE_VAULT)
                    if (connected && !compact) {
                        Spacer(Modifier.width(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { headerChips() }
                    }
                    Spacer(Modifier.width(8.dp))
                    Chip("Close", accent = false, onClick = onDismiss)
                }
                if (connected && compact) {
                    Spacer(Modifier.height(8.dp))
                    ChipFlow { headerChips() }
                }
                // Filters and the look fold open under the header (scrolling on small screens).
                androidx.compose.animation.AnimatedVisibility(showFilters && connected) {
                    Column(Modifier.padding(top = 8.dp).heightIn(max = if (compact) 180.dp else 230.dp).verticalScroll(rememberScrollState())) {
                        CapsuleFilters(
                            options = capsuleOptions,
                            hiddenCount = capsules.count { it.game.stableKey in capsuleOptions.hidden },
                            showHidden = showHidden,
                            onShowHidden = { showHidden = it; selected = home() },
                            onSyncNow = syncNow,
                            onChange = { t -> com.shelf.launcher.data.SaveCapsules.setOptions(context, t); selected = home() }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (!connected) {
                    // Update 151: Google Drive isn't connected: only that, and the way to connect it.
                    Column(
                        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Filled.CloudOff, contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Not connected to Google Drive", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(17.sp), maxFontSize = 17.sp),
                            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        val problem = connectProblem
                        if (problem != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                problem, color = Color(0xFFFFB4A0), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 5, overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Chip(
                            if (connecting) "Connecting…" else "Connect Google Drive", accent = true,
                            modifier = Modifier.focusRequester(connectFocus), onClick = connectDrive
                        )
                    }
                } else if (entries.isEmpty()) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            when {
                                scanning -> "Looking for saves…"
                                showHidden -> "Nothing hidden."
                                else -> "No saves found yet. A game shows up here once it has a real save: an in-game " +
                                    "save file, a save on a PS1 / PS2 memory card or in PSP SAVEDATA, or saves synced with Google Drive."
                            },
                            color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(96.dp),
                        state = gridState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        // Each slot carries its own headroom for the lift / bob (see SaveSlot), so the
                        // top row is never cut off by the window edge, even after focus scrolling.
                        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (copy in 0 until loops) sections.forEachIndexed { si, (platform, list) ->
                          if (byConsole) {
                            item(key = "h:$copy:$platform", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                ConsoleHeader(consoleName(platform), platform, list.size, current = sectionOf(selected) == si && copyOf(selected) == copy, first = si == 0 && copy == 0)
                            }
                          }
                          val base = sectionStart[si]
                          itemsIndexed(list, key = { _, e -> "$copy:" + e.first.stableKey }) { li, entry ->
                            val i = copy * n + base + li
                            val (game, st) = entry
                            SaveSlot(
                                game = game, status = st, icon = iconFor(game),
                                appearDelayMs = if (entered.add(game.stableKey)) (i.coerceAtMost(14) * 45) else -1,
                                selected = i == selected,
                                // Each slot bobs a little out of step with its neighbours.
                                bob = { kotlin.math.sin(phase() + i * 0.9f) },
                                spin = spin,
                                // In the air or in the details window: not in its slot.
                                hidden = (detail?.first?.stableKey == game.stableKey || crackFor == game.stableKey) && (!endless || i == selected),
                                onIconPlaced = { slotRects["g:$i"] = it; if (!endless) slotRects[game.stableKey] = it },
                                modifier = if (i == selected) Modifier.focusRequester(firstFocus) else Modifier,
                                // While the details window is up, the grid behind it takes no focus
                                // (the D-pad used to wander into it and pick another save).
                                canFocus = detail == null,
                                onFocus = { if (selected != i) { selected = i; sounds?.play(UiSound.MOVE) } },
                                onOpen = { open(entry) }
                            )
                          }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    entries.getOrNull(loc(selected))?.let { (game, st) -> SaveInfoBar(game, st, capsuleByKey[game.stableKey]) }
                }
            }
            // The picked save, up close. Only fades (no zoom), so the icon's landing spot holds still.
            androidx.compose.animation.AnimatedVisibility(
                visible = detail != null && !closing,
                enter = androidx.compose.animation.fadeIn(tween(240)),
                exit = androidx.compose.animation.fadeOut(tween(220)),
                modifier = Modifier.matchParentSize()
            ) {
                val d = detail
                if (d != null) SaveDetail(
                    game = d.first, st = d.second, icon = iconFor(d.first), phase = phase,
                    capsule = capsuleByKey[d.first.stableKey],
                    hidden = d.first.stableKey in capsuleOptions.hidden,
                    onHide = { h -> com.shelf.launcher.data.SaveCapsules.setHidden(context, d.first.stableKey, h); close() },
                    landed = landed,
                    onIconSpot = { targetRect = it },
                    onOpenSave = { onOpenGame(d.first) },
                    onBack = { close() }
                )
            }
            // The capsule cracking open in its slot.
            val crackRect = flightFrom
            if (crackFor != null && crackRect != null) {
                CapsuleCrack(crackRect, hostOffset, { crack.value }, ShelfTheme.accent, detailOrNull(entries, crackFor)?.let { iconFor(it) }, Modifier.matchParentSize())
            }
            // The icon in flight: from its slot to the details window (or back), in an arc, turning once.
            val f = flying
            val from = flightFrom
            val to = targetRect
            if (f != null && from != null && to != null) {
                FlyingSaveIcon(f.first, iconFor(f.first), from, to, hostOffset, flight.value, outbound = !closing)
            }
            // (Update 141: the insert / loading moment after a save opened is gone, at your request — the save's
            // details show as soon as it lands.)
        }
        } // (Update 151: CompositionLocalProvider)
    }
}

/**
 * A console's section in the save browser: just its name (with a small picture of the console when
 * there is one) and the number of saves, over a thin line - no box. The section you're in gets the
 * accent colour on its line.
 */
@Composable
private fun ConsoleHeader(name: String, platform: String, count: Int, current: Boolean, first: Boolean) {
    val context = LocalContext.current
    val photo by androidx.compose.runtime.produceState<String?>(null, platform) {
        value = runCatching { com.shelf.launcher.data.ConsolePhotos.url(context, platform) }.getOrNull()
    }
    Column(Modifier.fillMaxWidth().padding(top = if (first) 2.dp else 14.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (photo != null) {
                AsyncImage(
                    model = photo, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 30.dp, height = 20.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                name, color = if (current) Color.White else Color.White.copy(alpha = 0.82f),
                fontSize = 16.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(16.sp), maxFontSize = 16.sp), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            Text("$count", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier.fillMaxWidth().height(1.dp).background(
                Brush.horizontalGradient(
                    listOf(
                        (if (current) ShelfTheme.accent else Color.White).copy(alpha = if (current) 0.9f else 0.28f),
                        Color.White.copy(alpha = 0.04f)
                    )
                )
            )
        )
    }
}

/** Specks of light drifting upward, a few dozen, drawn in one pass. */
@Composable
private fun MemoryCardSky(phaseOf: () -> Float, accent: Color, modifier: Modifier) {
    val specks = remember { List(36) { Triple(Math.random().toFloat(), Math.random().toFloat(), 0.4f + Math.random().toFloat()) } }
    // Its own long clock: each speck rises a whole number of screens per loop, so the loop never jumps.
    val drift = rememberInfiniteTransition(label = "specks").animateFloat(
        0f, 1f, infiniteRepeatable(tween(40_000, easing = androidx.compose.animation.core.LinearEasing)), label = "specksT"
    )
    androidx.compose.foundation.Canvas(modifier) {
        val phase = phaseOf()
        val t = drift.value
        specks.forEachIndexed { i, (x, y, s) ->
            val laps = if (s > 0.9f) 2 else 1
            val yy = ((y - t * laps) % 1f + 1f) % 1f
            val twinkle = 0.35f + 0.35f * kotlin.math.sin(phase * 2 + i).toFloat().coerceIn(-1f, 1f)
            drawCircle(
                color = (if (i % 3 == 0) accent else Color.White).copy(alpha = twinkle * 0.5f),
                radius = 1.2.dp.toPx() * s,
                center = androidx.compose.ui.geometry.Offset(x * size.width, yy * size.height)
            )
        }
    }
}

/** A tint over near-black, so the header glow follows the theme without washing out. */
private fun Color.compositeOverDark(): Color {
    val base = Color(0xFF0B0D16)
    val a = alpha
    return Color(red * a + base.red * (1 - a), green * a + base.green * (1 - a), blue * a + base.blue * (1 - a), 1f)
}

@Composable
private fun SaveSlot(
    game: Game,
    status: GameSyncStatus,
    icon: String?,
    /** Delay before sliding in (ms); -1 = already shown, no entry animation. */
    appearDelayMs: Int,
    selected: Boolean,
    /** How far it bobs right now (-1..1); read only while drawing. */
    bob: () -> Float,
    /** The picked one's slow turn, in degrees (only used with "3D turn" on); read only while drawing. */
    spin: () -> Float,
    /** Its icon is away (flying, or in the details window): the slot shows an empty socket. */
    hidden: Boolean,
    canFocus: Boolean = true,
    onIconPlaced: (androidx.compose.ui.geometry.Rect) -> Unit,
    modifier: Modifier,
    onFocus: () -> Unit,
    onOpen: () -> Unit
) {
    val lift by androidx.compose.animation.core.animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "lift")
    val latestFocus by androidx.compose.runtime.rememberUpdatedState(onFocus)
    val latestOpen by androidx.compose.runtime.rememberUpdatedState(onOpen)
    // Two looks (Filters › Look): squares (the default) or upright pills with the save icon in the clear
    // top half and the sync state tinting the bottom half.
    val shape = RoundedCornerShape(50)
    val appear = remember { androidx.compose.animation.core.Animatable(if (appearDelayMs < 0) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (appear.value < 1f) {
            kotlinx.coroutines.delay(appearDelayMs.toLong())
            appear.animateTo(1f, tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
    }
    val charge = stateColor(status.state)
    Column(
        modifier
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 26.dp.toPx()
            }
            .onFocusChanged { if (it.isFocused) onFocus() }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp && (e.key == Key.ButtonA || e.key == Key.Enter || e.key == Key.DirectionCenter)) { onOpen(); true } else false
            }
            .focusable(enabled = canFocus)
            // The slot keeps its key across a re-sort (its index changes): always the latest callbacks.
            .pointerInput(Unit) { detectTapGestures(onTap = { latestFocus(); latestOpen() }) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Headroom inside the slot for the lift (10 dp), the bigger scale and the bob: focus scrolling
        // brings the whole slot into view, so the lifted capsule is never clipped by the grid's edge.
        Spacer(Modifier.height(22.dp))
        val turnOn = Features.saveTurn.state.value
        val vaultStyle = Features.saveVaultStyle.state.value
        if (vaultStyle == Features.SaveVaultStyle.SD_CARD || vaultStyle == Features.SaveVaultStyle.MEMORY_CARD || vaultStyle == Features.SaveVaultStyle.FLOPPY || vaultStyle == Features.SaveVaultStyle.CARD_3D) {
            StyledSaveCard(
                vaultStyle, icon, gameDisplayName(game.name), status.state, selected, lift, bob, hidden, onIconPlaced, platform = game.platform,
                spin = spin
            )
        } else if (vaultStyle == Features.SaveVaultStyle.CAPSULE) {
            Box(
                Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(0.62f)
                    .graphicsLayer {
                        translationY = -(3.dp.toPx() * bob()) - 10.dp.toPx() * lift
                        val s = 1f + 0.10f * lift
                        scaleX = s; scaleY = s
                        alpha = if (hidden) 0.25f else 1f
                    }
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.07f + 0.05f * lift),
                            0.6f to Color.White.copy(alpha = 0.04f),
                            0.6f to charge.copy(alpha = 0.22f + 0.14f * lift),
                            1f to charge.copy(alpha = 0.42f + 0.2f * lift)
                        )
                    )
                    .border(1.dp + 1.5.dp * lift, if (selected) ShelfTheme.accent else Color.White.copy(alpha = 0.16f), shape),
            ) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.fillMaxHeight(0.07f))
                    // The icon's spot (measured for the flight into the details window).
                    Box(
                        Modifier
                            .fillMaxWidth(0.80f)
                            .aspectRatio(1f)
                            .onGloballyPositioned { onIconPlaced(it.boundsInRoot()) }
                            .graphicsLayer { alpha = if (hidden) 0f else 1f }
                            .clip(CapsuleIconShape)
                            .background(Color.Black.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (icon != null) {
                            AsyncImage(model = icon, contentDescription = game.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Text(
                                gameDisplayName(game.name).take(2).uppercase(),
                                color = Color.White.copy(alpha = 0.8f), fontSize = 20.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // The sync state in the charge half: its icon, not just a colour.
                    SyncBadge(status.state, 16.dp + 2.dp * lift)
                    Spacer(Modifier.fillMaxHeight(0.16f))
                }
                // The seam where the two halves of the capsule meet.
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(0.6f).align(Alignment.TopCenter)
                ) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.22f)))
                }
                // Glass highlight down the left side.
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 5.dp)
                        .width(4.dp)
                        .fillMaxHeight(0.62f)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.10f + 0.08f * lift))
                )
            }
        } else Box(
            // The square save icon, as it always was: idle it bobs, picked it lifts and grows (and,
            // with "3D turn", tilts while bobbing and turns slowly like a 3D save icon).
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .onGloballyPositioned { onIconPlaced(it.boundsInRoot()) }
                .graphicsLayer {
                    cameraDistance = 12f * density
                    val b = bob()
                    translationY = -(3.dp.toPx() * b) - 10.dp.toPx() * lift
                    if (turnOn) rotationY = if (selected) kotlin.math.sin(Math.toRadians(spin().toDouble())).toFloat() * 28f * lift else b * 4f
                    flatTextFor3d()
                    val s = 1f + 0.12f * lift
                    scaleX = s; scaleY = s
                    alpha = if (hidden) 0.12f else 1f
                }
                .clip(SquareIconShape)
                .background(Color.White.copy(alpha = 0.06f + 0.06f * lift))
                .border(1.dp + 1.5.dp * lift, if (selected) ShelfTheme.accent else Color.White.copy(alpha = 0.12f), SquareIconShape),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                AsyncImage(model = icon, contentDescription = game.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    gameDisplayName(game.name).take(2).uppercase(),
                    color = Color.White.copy(alpha = 0.8f), fontSize = 22.sp, fontWeight = FontWeight.Bold
                )
            }
            SyncBadge(status.state, 18.dp, Modifier.align(Alignment.TopEnd).padding(4.dp))
        }
        // A soft shadow under the floating icon, smaller when it floats higher.
        Box(
            Modifier
                .padding(top = 4.dp)
                .width(46.dp * (1f - 0.25f * lift))
                // The bob narrows it while drawing only (no relayout per frame).
                .graphicsLayer { scaleX = (1f - 0.25f * lift - 0.06f * bob()) / (1f - 0.25f * lift).coerceAtLeast(0.1f) }
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.35f))
        )
        Spacer(Modifier.height(4.dp))
        Text(
            gameDisplayName(game.name), color = if (selected) ShelfTheme.accent else Color.White,
            fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = androidx.compose.ui.unit.TextUnit(1.182f, androidx.compose.ui.unit.TextUnitType.Em)
        )
    }
}

/** One save up close: its icon, the details, and a way into its sync window. */
@Composable
private fun SaveDetail(
    game: Game,
    st: GameSyncStatus,
    icon: String?,
    phase: () -> Float,
    /** What the capsule holds (null = only a Drive sync record). */
    capsule: com.shelf.launcher.data.SaveCapsules.Capsule?,
    hidden: Boolean,
    onHide: (Boolean) -> Unit,
    /** The flying icon has arrived: show it here and keep it turning. */
    landed: Boolean,
    /** Reports where the icon sits in this window (root px), for the flight to land on. */
    onIconSpot: (androidx.compose.ui.geometry.Rect) -> Unit,
    onOpenSave: () -> Unit,
    onBack: () -> Unit
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(80); runCatching { focus.requestFocus() } }
    val context = LocalContext.current
    val fmt = remember { android.text.format.DateFormat.getLongDateFormat(context) }
    val time = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val last = maxOf(st.lastSuccessAt, st.updatedAt)
    // After landing: one slow, endless turn (a full circle every 8 s) — picking up where the flight's turn
    // ended, facing front — the back showing the card's reverse side, never the cover mirrored.
    val turn = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(landed) {
        if (!landed) return@LaunchedEffect
        turn.snapTo(0f)
        while (true) {
            turn.animateTo(360f, tween(8000, easing = androidx.compose.animation.core.LinearEasing))
            turn.snapTo(0f)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { onBack() } },
        contentAlignment = Alignment.Center
    ) {
        // Update 151: on a small screen the window is wider, the icon smaller and the details scroll (they ran off the bottom).
        val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 760
        Row(
            Modifier
                .fillMaxWidth(if (compact) 0.94f else 0.8f)
                .padding(vertical = if (compact) 8.dp else 0.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(ShelfTheme.accent.copy(alpha = 0.3f).compositeOverDark(), Color(0xFF0A0B12))))
                .border(1.dp, ShelfTheme.accent.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(if (compact) 14.dp else 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The icon's spot: measured for the flight; the icon appears here once it has landed.
            Box(
                Modifier
                    .size(if (compact) 108.dp else 150.dp)
                    .onGloballyPositioned { onIconSpot(it.boundsInRoot()) },
                contentAlignment = Alignment.Center
            ) {
                // A soft glow on the "floor" under it.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp)
                        .width(if (compact) 80.dp else 110.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(ShelfTheme.accent.copy(alpha = if (landed) 0.25f else 0.1f))
                )
                if (landed) {
                    SaveIconCard(
                        game, icon, { turn.value },
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationY = -6.dp.toPx() * kotlin.math.sin(phase()) }
                    )
                }
            }
            Spacer(Modifier.width(if (compact) 14.dp else 24.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(gameDisplayName(game.name), color = Color.White, fontSize = if (compact) 18.sp else 22.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(if (compact) 18.sp else 22.sp), maxFontSize = if (compact) 18.sp else 22.sp), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(com.shelf.launcher.data.ConsoleNames.display(game.platform), color = ShelfTheme.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                val lastSave = maxOf(last, capsule?.lastSaved ?: 0L)
                DetailRow("Last saved", if (lastSave > 0) "${fmt.format(java.util.Date(lastSave))}, ${time.format(java.util.Date(lastSave))} (${syncAgo(lastSave)})" else "Unknown")
                if (capsule != null && !capsule.playedHere) DetailRow("Origin", "Not played through Voltcap (imported or from another device)")
                if (st.lastSuccessAt > 0 || st.state != SyncState.PENDING) {
                DetailRow("Last move", when (st.lastDirection) {
                    SyncDirection.UPLOAD -> "This device → Google Drive"
                    SyncDirection.DOWNLOAD -> "Google Drive → this device"
                    SyncDirection.NONE -> "Checked, nothing to move"
                })
                if (st.fileCount > 0) DetailRow("Files", "${st.fileCount}")
                DetailRow("Status", when (st.state) {
                    SyncState.SYNCED -> "Safe on Google Drive"
                    SyncState.CONFLICT -> "Different on two devices — choose which to keep"
                    SyncState.UPLOADING -> "Uploading…"
                    SyncState.DOWNLOADING -> "Downloading…"
                    SyncState.NEEDS_SIGN_IN -> "Google wants you to sign in again"
                    SyncState.OFFLINE -> "Offline"
                    SyncState.ERROR -> st.message ?: "Last sync failed"
                    SyncState.PENDING -> "Waiting to sync"
                })
                }
                // The saves inside (all slots of every memory card for this game, in one capsule).
                val saves = capsule?.saves.orEmpty()
                if (saves.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${saves.size} save${if (saves.size == 1) "" else "s"} inside", color = ShelfTheme.accent,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.height((saves.size.coerceAtMost(if (compact) 3 else 4) * 38).dp)) {
                        items(saves.size) { i -> CapsuleSaveRow(saves[i]) }
                    }
                }
                Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
                ChipFlow {
                    Box(Modifier.focusRequester(focus)) { Chip("Save options", accent = true, onClick = onOpenSave) }
                    // Hides the capsule only; the save files are never touched.
                    Chip(if (hidden) "Show in the vault" else "Hide from the vault", accent = false) { onHide(!hidden) }
                    Chip("Back", accent = false, onClick = onBack)
                }
            }
        }
    }
}

/**
 * The save icon as a card: square (or the pill's icon window), the cover on the front. With "3D turn"
 * on it turns [angle] degrees about its upright axis (the back: the same cover darkened, with the
 * memory-card mark, never mirrored); off, it stays upright and [angle] only sweeps a light across it.
 */
@Composable
private fun SaveIconCard(game: Game, icon: String?, angle: () -> Float, modifier: Modifier = Modifier) {
    val turnOn = Features.saveTurn.state.value
    // Update 141: with the 3D card style, the whole 3D card turns (it used to be its picture turning on its own).
    val card = if (Features.saveVaultStyle.state.value == Features.SaveVaultStyle.CARD_3D) Models3D.saveCardId() else null
    if (card != null) {
        if (turnOn) SwayingModel(card, icon, gameDisplayName(game.name), game.platform, angle, modifier)
        else Model3DArt(card, icon, 0f, modifier, name = gameDisplayName(game.name), alwaysName = true, platform = game.platform)
        return
    }
    val shape = saveIconShape()
    val latestAngle by androidx.compose.runtime.rememberUpdatedState(angle)
    fun norm(): Float = ((latestAngle() % 360f) + 360f) % 360f
    // The angle is read while drawing; only crossing to the back side recomposes (it used to be every frame).
    val latestTurnOn by androidx.compose.runtime.rememberUpdatedState(turnOn)
    val back by remember { androidx.compose.runtime.derivedStateOf { latestTurnOn && norm().let { it > 90f && it < 270f } } }
    Box(
        modifier
            .graphicsLayer {
                if (turnOn) { cameraDistance = 14f * density; rotationY = norm() }
                flatTextFor3d()
            }
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.5.dp, ShelfTheme.accent.copy(alpha = if (back) 0.9f else 0.5f), shape),
        contentAlignment = Alignment.Center
    ) {
        // Undo the mirror on the back side, so the picture reads the right way round.
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = if (back) -1f else 1f }, contentAlignment = Alignment.Center) {
            if (icon != null) AsyncImage(model = icon, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text(gameDisplayName(game.name).take(2).uppercase(), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            if (back) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Brush.linearGradient(listOf(ShelfTheme.accent.copy(alpha = 0.6f).compositeOverDark().copy(alpha = 0.82f), Color(0xD90A0B12)))),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.SdCard, null,
                        tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.fillMaxSize(0.38f)
                    )
                }
            }
        }
        // A light sweep across the face as it turns (drawn from the angle at draw time).
        Box(Modifier.matchParentSize().drawWithContent {
            val sheen = kotlin.math.abs(kotlin.math.sin(Math.toRadians(norm().toDouble()))).toFloat()
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.16f * sheen), Color.Transparent)))
        })
    }
}

private val SquareIconShape = RoundedCornerShape(16.dp)

@Composable
private fun saveIconShape(): androidx.compose.ui.graphics.Shape = when (Features.saveVaultStyle.state.value) {
    Features.SaveVaultStyle.CAPSULE -> CapsuleIconShape
    Features.SaveVaultStyle.CLASSIC -> SquareIconShape
    else -> RoundedCornerShape(6.dp)
}

/** The icon window of a capsule: round on top like the capsule's cap, gently rounded below. */
private val CapsuleIconShape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 18, bottomEndPercent = 18)

/**
 * The icon on its way between its slot ([from]) and the details window ([to]): [p] 0 = in the slot,
 * 1 = landed. It rises in an arc, grows to its new size and turns one full circle, so it arrives
 * facing front - exactly how the details window then carries on turning it.
 */
private fun detailOrNull(entries: List<Pair<Game, GameSyncStatus>>, key: String?): Game? =
    // Null when the save left the list (update 91: it used to take the first save, or crash on an empty list).
    entries.firstOrNull { it.first.stableKey == key }?.first

/**
 * The capsule cracking open where it sits, before its save comes out: it shivers, light leaks along the seam and
 * a burst grows behind it, then the two halves twist apart (the glass top up and to the left, the charge half
 * down and to the right) and fade, leaving the save icon free to fly. [p] runs 0..1 (read while drawing).
 */
@Composable
private fun CapsuleCrack(
    iconRect: androidx.compose.ui.geometry.Rect,
    host: androidx.compose.ui.geometry.Offset,
    p: () -> Float,
    accent: Color,
    icon: String?,
    modifier: Modifier
) {
    // The whole capsule around the icon's spot (the icon sits 80 % wide, near the top, of a 0.62 pill).
    val cw = iconRect.width / 0.80f
    val ch = cw / 0.62f
    val left = iconRect.center.x - cw / 2f - host.x
    val top = iconRect.top - ch * 0.07f - host.y
    val seamY = top + ch * 0.6f
    Box(modifier) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val t = p()
            val shake = if (t < 0.35f) kotlin.math.sin(t * 90f) * (1f - t / 0.35f) * 3.dp.toPx() * (t / 0.35f + 0.3f) else 0f
            val open = ((t - 0.4f) / 0.6f).coerceIn(0f, 1f)
            val ease = 1f - (1f - open) * (1f - open)
            val cx = left + cw / 2f
            // The burst behind: grows with the leak, fades as the halves leave.
            val leak = ((t - 0.15f) / 0.35f).coerceIn(0f, 1f)
            val burstA = leak * (1f - open * 0.85f)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.75f * burstA), accent.copy(alpha = 0.45f * burstA), Color.Transparent), Offset(cx, seamY), cw * (0.6f + 1.6f * leak)), cw * (0.6f + 1.6f * leak), Offset(cx, seamY))
            // Rays fanning out of the seam.
            if (leak > 0f && open < 1f) {
                for (k in 0 until 9) {
                    val ang = Math.toRadians((k * 40.0) + t * 60.0)
                    val len = cw * (0.4f + 0.9f * leak)
                    drawLine(accent.copy(alpha = 0.35f * burstA), Offset(cx, seamY), Offset(cx + (kotlin.math.cos(ang) * len).toFloat(), seamY + (kotlin.math.sin(ang) * len * 0.6f).toFloat()), 2.dp.toPx())
                }
            }
            val r = androidx.compose.ui.geometry.CornerRadius(cw / 2f)
            val fade = 1f - (open - 0.6f).coerceAtLeast(0f) / 0.4f
            // Top half (glass): rises and tips left.
            withTransform({
                translate(shake - cw * 0.25f * ease, -ch * 0.35f * ease)
                rotate(-28f * ease, Offset(cx, seamY))
            }) {
                clipRect(left - cw, top - ch, left + cw * 2f, seamY) {
                    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f * fade), Color.White.copy(alpha = 0.12f * fade)), top, seamY), Offset(left, top), Size(cw, ch), r)
                    drawRoundRect(Color.White.copy(alpha = 0.55f * fade), Offset(left, top), Size(cw, ch), r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                }
            }
            // Bottom half (the charge colour): drops and tips right.
            withTransform({
                translate(shake + cw * 0.22f * ease, ch * 0.3f * ease)
                rotate(24f * ease, Offset(cx, seamY))
            }) {
                clipRect(left - cw, seamY, left + cw * 2f, top + ch * 2f) {
                    drawRoundRect(Brush.verticalGradient(listOf(accent.copy(alpha = 0.75f * fade), accent.copy(alpha = 0.45f * fade)), seamY, top + ch), Offset(left, top), Size(cw, ch), r)
                    drawRoundRect(Color.White.copy(alpha = 0.35f * fade), Offset(left, top), Size(cw, ch), r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                }
            }
            // The seam glowing as it splits.
            drawLine(Color.White.copy(alpha = (leak * (1f - open)).coerceIn(0f, 1f)), Offset(left + cw * 0.08f + shake, seamY), Offset(left + cw * 0.92f + shake, seamY), (1.5f + 3f * leak).dp.toPx())
        }
        // The save itself, in its place inside the capsule until the flight takes it from there.
        val density = androidx.compose.ui.platform.LocalDensity.current
        if (icon != null) AsyncImage(
            icon, null, contentScale = ContentScale.Crop,
            modifier = Modifier
                .graphicsLayer {
                    translationX = iconRect.left - host.x
                    translationY = iconRect.top - host.y
                }
                .size(with(density) { iconRect.width.toDp() }, with(density) { iconRect.height.toDp() })
                .clip(RoundedCornerShape(8.dp))
        )
    }
}

@Composable
private fun FlyingSaveIcon(
    game: Game,
    icon: String?,
    from: androidx.compose.ui.geometry.Rect,
    to: androidx.compose.ui.geometry.Rect,
    host: androidx.compose.ui.geometry.Offset,
    p: Float,
    outbound: Boolean
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val w = to.width.coerceAtLeast(1f)
    val sizeDp = with(density) { w.toDp() }
    val cx = from.center.x + (to.center.x - from.center.x) * p - host.x
    // The arc: up and over, highest halfway.
    val cy = from.center.y + (to.center.y - from.center.y) * p - host.y - kotlin.math.sin(Math.PI * p).toFloat() * w * 0.55f
    val scale = (from.width / w) + (1f - from.width / w) * p
    // With "3D turn": one turn forwards out, one turn back the other way, ending square in the slot.
    // Without: upright all the way, the angle only sweeps a light across the face.
    val angle = if (outbound) 360f * p else -360f * (1f - p)
    val flightShape = saveIconShape()
    SaveIconCard(
        game, icon, { angle },
        Modifier
            .size(sizeDp)
            .graphicsLayer {
                translationX = cx - w / 2f
                translationY = cy - w / 2f
                scaleX = scale; scaleY = scale
                shadowElevation = 18.dp.toPx() * kotlin.math.sin(Math.PI * p).toFloat()
                shape = flightShape
            }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.width(84.dp))
        Text(value, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun stateColor(state: SyncState): Color = when (state) {
    SyncState.SYNCED -> Color(0xFF6FD08C)
    SyncState.CONFLICT, SyncState.ERROR, SyncState.NEEDS_SIGN_IN -> Color(0xFFE8A33D)
    SyncState.UPLOADING, SyncState.DOWNLOADING -> ShelfTheme.accent
    else -> Color.White.copy(alpha = 0.4f)
}

/** One save inside a capsule: its own title, where it lives, when. */
@Composable
private fun CapsuleSaveRow(item: com.shelf.launcher.data.SaveCapsules.SaveItem) {
    val context = LocalContext.current
    val fmt = remember { android.text.format.DateFormat.getMediumDateFormat(context) }
    Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(
                if (item.kind == com.shelf.launcher.data.SaveCapsules.Kind.MEMORY_CARD) ShelfTheme.accent else Color.White.copy(alpha = 0.5f)
            )
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = Color.White, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) // (Update 151: one line each: two of each never fit the 38 dp row and were cut through)
            Text(item.where, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.modified > 0) Text(fmt.format(java.util.Date(item.modified)), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(start = 6.dp)) // (Update 151: one line)
    }
}

/** Save Vault switches: which kinds of saves show, and the look. Changing them never touches a save. */
@Composable
private fun CapsuleFilters(
    options: com.shelf.launcher.data.SaveCapsules.Options,
    hiddenCount: Int,
    showHidden: Boolean,
    onShowHidden: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onChange: ((com.shelf.launcher.data.SaveCapsules.Options) -> com.shelf.launcher.data.SaveCapsules.Options) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScrollCompat(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Chip("Memory cards", accent = options.memoryCards) { onChange { it.copy(memoryCards = !it.memoryCards) } }
        Chip("External / imported", accent = options.external && !options.onlyPlayedHere) { onChange { it.copy(external = !it.external) } }
        Chip("Only played in Voltcap", accent = options.onlyPlayedHere) { onChange { it.copy(onlyPlayedHere = !it.onlyPlayedHere) } }
        Chip(if (showHidden) "Hidden ($hiddenCount) ✓" else "Hidden ($hiddenCount)", accent = showHidden) { onShowHidden(!showHidden) }
    }
    // The look, filter and background (the same settings as Settings › Appearance › Save Vault), and
    // what the sync badges mean.
    // Google Drive: what's connected, when it last synced, and the switch for syncing around games.
    Spacer(Modifier.height(8.dp))
    SaveVaultSyncRow(onSyncNow)
    Spacer(Modifier.height(8.dp))
    SaveVaultSettings(onDark = true)
    Spacer(Modifier.height(6.dp))
    SyncLegend()
}


/** Save Vault's Google Drive row: state, last sync, "Sync now" and "sync around games". */
@Composable
private fun SaveVaultSyncRow(onSyncNow: () -> Unit) {
    val context = LocalContext.current
    val cloud by com.shelf.launcher.data.CloudSync.state.collectAsState()
    val st = cloud.settings
    Text("Sync", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
    // Update 144: not connected is a note, not a button that did nothing when pressed.
    // (Light text like the rest of this dark panel; MutedNote follows the theme and can be dark.)
    if (!st.connected) Text("Google Drive isn't connected (Settings › Sync).", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, lineHeight = 16.sp)
    ChipFlow {
        if (st.connected) {
            Chip(if (cloud.running) (cloud.progress ?: "Syncing…") else "Sync now", accent = cloud.running, onClick = onSyncNow)
            Chip(if (st.syncAroundGames) "✓ Sync around games" else "Sync around games", accent = st.syncAroundGames) {
                com.shelf.launcher.data.CloudSync.updateSettings(context) { it.copy(syncAroundGames = !it.syncAroundGames) }
            }
            Chip(if (st.syncSaves) "✓ Sync saves" else "Sync saves", accent = st.syncSaves) {
                com.shelf.launcher.data.CloudSync.updateSettings(context) { it.copy(syncSaves = !it.syncSaves) }
            }
        }
    }
    if (st.connected && st.lastSyncAt > 0) {
        val ago = android.text.format.DateUtils.getRelativeTimeSpanString(st.lastSyncAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)
        Text("Last sync $ago" + (st.lastResult?.let { " · $it" } ?: ""), color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier =
    this.then(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()))

@Composable
private fun SaveInfoBar(game: Game, st: GameSyncStatus, capsule: com.shelf.launcher.data.SaveCapsules.Capsule?) {
    val context = LocalContext.current
    val fmt = remember { android.text.format.DateFormat.getMediumDateFormat(context) }
    val time = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val last = maxOf(st.lastSuccessAt, capsule?.lastSaved ?: st.updatedAt)
    val synced = st.lastSuccessAt > 0 || st.state != SyncState.PENDING
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(gameDisplayName(game.name), color = Color.White, fontSize = 15.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(15.sp), maxFontSize = 15.sp), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                com.shelf.launcher.data.ConsoleNames.display(game.platform) + " · " +
                    (capsule?.saves?.size?.takeIf { it > 0 }?.let { n -> "$n save${if (n == 1) "" else "s"} · " } ?: "") + if (!synced) "on this device" else when (st.state) {
                    SyncState.SYNCED -> "safe on Google Drive"
                    SyncState.CONFLICT -> "differs on two devices — choose which to keep"
                    SyncState.UPLOADING -> "uploading…"
                    SyncState.DOWNLOADING -> "downloading…"
                    SyncState.NEEDS_SIGN_IN -> "Google wants you to sign in again"
                    SyncState.OFFLINE -> "offline"
                    SyncState.ERROR -> st.message ?: "last sync failed"
                    SyncState.PENDING -> "waiting to sync"
                },
                color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        // Update 151: kept to one line each and narrow enough to leave the game's name room on a small screen.
        Column(Modifier.padding(start = 10.dp).widthIn(max = 200.dp), horizontalAlignment = Alignment.End) {
            Text(
                if (last > 0) "${fmt.format(java.util.Date(last))} ${time.format(java.util.Date(last))}" else "Never",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                if (!synced) "Last saved" else when (st.lastDirection) {
                    SyncDirection.UPLOAD -> "Saved to Drive"
                    SyncDirection.DOWNLOAD -> "Fetched from Drive"
                    SyncDirection.NONE -> "Checked"
                } + if (st.fileCount > 0) " · ${st.fileCount} file${if (st.fileCount == 1) "" else "s"}" else "",
                color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * "Browse saves" as its own button: a memory-card icon in the accent colour that breathes gently
 * and catches a light sweep every few seconds, so it stands out from the plain chips around it.
 * [compact] drops the label (icon only).
 */
@Composable
internal fun BrowseSavesButton(onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val accent = ShelfTheme.accent
    val idle = rememberInfiniteTransition(label = "browseSaves")
    val pulse by idle.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1800, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    // Shine: crosses once, then rests (most of the 3.6 s cycle is the rest).
    val shine by idle.animateFloat(
        -0.6f, 2.4f,
        infiniteRepeatable(tween(3600, easing = androidx.compose.animation.core.LinearEasing)),
        label = "shine"
    )
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val sounds = LocalUiSounds.current
    Row(
        modifier
            .graphicsLayer { val s = 1f + 0.035f * pulse + (if (focused) 0.05f else 0f); scaleX = s; scaleY = s }
            .clip(shape)
            .background(
                Brush.horizontalGradient(listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.72f)))
            )
            // A steady rim: reading the pulse here recomposed the button every frame (the pulse lives in the scale).
            .border(if (focused) 2.dp else 1.dp, Color.White.copy(alpha = if (focused) 0.9f else 0.45f), shape)
            .drawWithContent {
                drawContent()
                // The light sweep: a soft diagonal band moving left to right.
                val x = size.width * shine
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.32f), Color.Transparent),
                        start = androidx.compose.ui.geometry.Offset(x - size.height, 0f),
                        end = androidx.compose.ui.geometry.Offset(x + size.height * 0.6f, size.height)
                    )
                )
            }
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp && (e.key == Key.ButtonA || e.key == Key.Enter || e.key == Key.DirectionCenter)) {
                    sounds?.play(UiSound.OPEN); onClick(); true
                } else false
            }
            .focusable()
            .pointerInput(Unit) { detectTapGestures { sounds?.play(UiSound.OPEN); onClick() } }
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Filled.SdCard, contentDescription = "Browse saves",
            tint = onAccentColor(), modifier = Modifier.size(18.dp)
        )
        if (!compact) {
            Spacer(Modifier.width(8.dp))
            Text("Browse saves", color = onAccentColor(), fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}
