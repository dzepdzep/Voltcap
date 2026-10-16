package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Update 212: Edit Home as one edit session — pick a part, adjust it, preview, then Done (save) or Discard.
 *
 * Opening Edit Home takes a snapshot of everything it can change: where the parts are, their sizes and stretches, the
 * layouts' own parts' shifts (all staged in HomeEditState's live maps, as before), and what used to be saved at once —
 * parts taken off (hidden), parts added to a layout, the parts' styles, the backing of every part and Art position.
 * Every change before it is one step of Undo; Discard puts the snapshot back, Done keeps it all (the live maps written
 * as before, HomeEditState.done). An interruption (Home, a call, the screen off) leaves the session as it is; if
 * Voltcap is closed in the middle, the snapshot is put back when it starts again (nothing half-saved).
 */

/** The state of an edit session at one moment (equal when nothing differs). */
internal data class EditSnap(
    val live: Map<String, Pair<Float, Float>>,
    val shift: Map<String, Pair<Float, Float>>,
    val scale: Map<String, Float>,
    val stretch: Map<String, Pair<Float, Float>>,
    /** The settings Edit Home changes straight away, as stored text. */
    val prefs: Map<String, String>
)

internal object HomeEditSession {
    private var opening: EditSnap? = null
    private val steps = ArrayList<EditSnap>()
    /** How many steps Undo can take (Compose state: the Undo button). */
    var undoCount by mutableIntStateOf(0)
        private set
    private var lastKind = ""
    private var lastAt = 0L
    private const val MAX_STEPS = 80

    private fun prefsNow(): Map<String, String> = mapOf(
        "hidden" to Features.hiddenParts.value.sorted().joinToString(","),
        "overlays" to Features.overlayParts.value,
        "styles" to Features.partStyles.value,
        "backing" to Features.partBackingSolid.value.toString(),
        "art" to Features.artPosLooks.value,
        "artBox" to "${Features.artShiftCase.value},${Features.artDropCase.value},${Features.artZoomCase.value}"
    )

    private fun writePrefs(m: Map<String, String>) {
        m["hidden"]?.let { v -> val set = v.split(',').filter { it.isNotBlank() }.toSet(); if (Features.hiddenParts.value != set) Features.hiddenParts.value = set }
        m["overlays"]?.let { if (Features.overlayParts.value != it) Features.overlayParts.value = it }
        m["styles"]?.let { if (Features.partStyles.value != it) Features.partStyles.value = it }
        m["backing"]?.toIntOrNull()?.let { if (Features.partBackingSolid.value != it) Features.partBackingSolid.value = it }
        m["art"]?.let { if (Features.artPosLooks.value != it) Features.artPosLooks.value = it }
        m["artBox"]?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 3 }?.let { v ->
            if (Features.artShiftCase.value != v[0]) Features.artShiftCase.value = v[0]
            if (Features.artDropCase.value != v[1]) Features.artDropCase.value = v[1]
            if (Features.artZoomCase.value != v[2]) Features.artZoomCase.value = v[2]
        }
    }

    fun capture(): EditSnap = EditSnap(
        HomeEditState.live.toMap(), HomeEditState.liveShift.toMap(), HomeEditState.liveScale.toMap(),
        HomeEditState.liveStretch.toMap(), prefsNow()
    )

    private fun apply(s: EditSnap) {
        HomeEditState.live.clear(); HomeEditState.live.putAll(s.live)
        HomeEditState.liveShift.clear(); HomeEditState.liveShift.putAll(s.shift)
        HomeEditState.liveScale.clear(); HomeEditState.liveScale.putAll(s.scale)
        HomeEditState.liveStretch.clear(); HomeEditState.liveStretch.putAll(s.stretch)
        writePrefs(s.prefs)
    }

    private fun encode(m: Map<String, String>): String = m.entries.joinToString("\u0001") { it.key + "\u0002" + it.value }
    private fun decode(s: String): Map<String, String> = s.split('\u0001').mapNotNull { e ->
        val i = e.indexOf('\u0002'); if (i <= 0) null else e.substring(0, i) to e.substring(i + 1)
    }.toMap()

    /** Edit Home opened (HomeEditState.open): the snapshot Discard goes back to. */
    fun begin() {
        val s = capture()
        opening = s
        steps.clear(); undoCount = 0; lastKind = ""
        // (Kept until Done / Discard: put back if Voltcap closes in the middle — recover. In the "session" prefs, which
        // are never backed up: a moment's state.)
        draft(encode(s.prefs))
    }

    /**
     * Before a change: the state now becomes one step of Undo. The same [kind] again within a moment (D-pad presses held
     * down, a slider) is one step; [force] starts a new step whatever came before (a new drag).
     */
    fun push(kind: String, force: Boolean = false) {
        if (!HomeEditState.active) return
        val now = System.currentTimeMillis()
        if (!force && kind == lastKind && now - lastAt < 900) { lastAt = now; return }
        lastKind = kind; lastAt = now
        val s = capture()
        if (steps.lastOrNull() == s) return
        steps.add(s)
        while (steps.size > MAX_STEPS) steps.removeAt(0)
        undoCount = steps.size
    }

    /** Undo: one step back (moves, sizes, styles, adding, hiding, showing, resets, Art position). */
    fun undo() {
        val s = steps.removeLastOrNull() ?: return
        apply(s)
        undoCount = steps.size
        lastKind = ""
    }

    /** Something changed since Edit Home opened. */
    fun dirty(): Boolean = opening?.let { it != capture() } ?: false

    /** Done: everything kept. */
    fun save() {
        HomeEditState.done()
        finish()
    }

    /** Discard: everything back as it was when Edit Home opened, settings that were saved at once included. */
    fun discard() {
        opening?.let { apply(it) }
        HomeEditState.close()
        finish()
    }

    private fun finish() {
        opening = null; steps.clear(); undoCount = 0; lastKind = ""
        draft("")
    }

    private var store: android.content.SharedPreferences? = null
    private const val DRAFT = "home_edit_open_212"
    private fun draft(v: String) { runCatching { store?.edit()?.putString(DRAFT, v)?.apply() } }

    /** B / Back: one step back — Preview off, else leave (asked when there are changes). Panels close themselves. */
    fun back() {
        when {
            HomeEditState.preview -> HomeEditState.preview = false
            HomeEditState.styling != null -> HomeEditState.styling = null
            HomeEditState.elements -> HomeEditState.elements = false
            HomeEditState.adding -> HomeEditState.adding = false
            dirty() -> HomeEditState.leaving = true
            else -> save()
        }
    }

    /** At start: an edit session left unfinished when Voltcap closed is put back (nothing of it was kept). */
    fun recover(context: android.content.Context) {
        if (store == null) store = runCatching { context.applicationContext.getSharedPreferences("session", android.content.Context.MODE_PRIVATE) }.getOrNull()
        if (HomeEditState.active) return
        val saved = runCatching { store?.getString(DRAFT, "") }.getOrNull().orEmpty()
        if (saved.isBlank()) return
        runCatching { writePrefs(decode(saved)) }
        draft("")
    }

    // ---- What a part is, and what a change reaches ----

    /** The layout [id] belongs to only (a layout's own part, or a part added to that layout), else null (shared). */
    fun layoutOf(id: String): HomeLayout? {
        val n = if (id.startsWith(LayoutParts.PREFIX)) id.removePrefix(LayoutParts.PREFIX).substringBefore(':') else id.substringBefore(':', "")
        return HomeLayout.values().firstOrNull { it.name == n }
    }

    /** The layouts that draw the shared game header, game info, news and category bar (the row layouts). */
    private val rowLayouts: List<HomeLayout> get() = HomeLayout.values().filter { !it.styleHome }

    /** Where a change to [id]'s place, size or style shows. */
    fun scopeOf(id: String): String {
        layoutOf(id)?.let { return "This layout only (${it.label})" }
        val b = HomeWidgets.baseId(id)
        return if (b == HomeWidgets.MUSIC || b == HomeWidgets.SHORTCUTS) "Every layout"
        else "Shared: " + rowLayouts.joinToString(", ") { it.label }
    }

    /** [id] is part of [layout]'s own design: Reset this layout puts it back. */
    private fun belongs(id: String, layout: HomeLayout): Boolean {
        layoutOf(id)?.let { return it == layout }
        val b = HomeWidgets.baseId(id)
        if (b == HomeWidgets.MUSIC || b == HomeWidgets.SHORTCUTS) return false
        return !layout.styleHome
    }

    /** X / Reset this part: its place, size, stretch and appearance. */
    fun resetSelected(id: String) {
        push("reset:$id", force = true)
        HomeEditState.resetOne(id)
        PartStyles.set(id, PartStyle())
    }

    /**
     * Reset this layout: the places, sizes, stretches and appearances of this layout's parts (with the row layouts, the
     * header, game info, news and category bar they share). The other layouts, the music player and the round shortcuts
     * (on every layout) keep theirs. What's shown or taken off stays as it is.
     */
    fun resetLayout(layout: HomeLayout) {
        push("resetLayout", force = true)
        listOf(HomeEditState.live, HomeEditState.liveShift, HomeEditState.liveStretch).forEach { m -> m.keys.filter { belongs(it, layout) }.forEach { m.remove(it) } }
        HomeEditState.liveScale.keys.filter { belongs(it, layout) }.forEach { HomeEditState.liveScale.remove(it) }
        Features.partStyles.value = PartStyles.format(PartStyles.parse(Features.partStyles.value).filterKeys { !belongs(it, layout) })
    }

    /** [id] is shown on the home screen (not hidden, and — a part added to a layout — still added). */
    fun shown(id: String): Boolean {
        if (HomeWidgets.hidden(id)) return false
        val l = layoutOf(id)
        if (l != null && !id.startsWith(LayoutParts.PREFIX)) {
            val b = HomeWidgets.baseId(id)
            return b in Features.overlayPartsFor(l) || (b == HomeWidgets.CATEGORY && CategoryTitle.fallbackOn(l))
        }
        return true
    }

    /** Select / ✕ / Visibility: hides [id], or shows it again. One step of Undo. */
    fun toggleShown(id: String) {
        push("show:$id", force = true)
        if (shown(id)) {
            HomeWidgets.remove(id)
            HomeEditState.selected = id
        } else {
            HomeWidgets.restore(id)
            val l = layoutOf(id)
            if (l != null && !id.startsWith(LayoutParts.PREFIX)) {
                val b = HomeWidgets.baseId(id)
                if (b !in Features.overlayPartsFor(l) && !(b == HomeWidgets.CATEGORY && CategoryTitle.fallbackOn(l))) Features.toggleOverlayPart(l, b)
            }
        }
    }

    /** Every part of this home screen, for L1 / R1 and Elements: the ones shown, the layout's own and the ones taken off. */
    fun cycleList(): List<String> {
        val layout = HomeEditState.layout
        val out = LinkedHashSet<String>()
        HomeWidgets.all.filter { it in HomeWidgets.present }.forEach { out += it }
        HomeWidgets.present.filter { it !in HomeWidgets.all }.forEach { out += it }
        Features.overlayPartsFor(layout).forEach { out += HomeWidgets.overlayId(layout, it) }
        LayoutParts.registered.keys.filter { it.startsWith(LayoutParts.PREFIX + layout.name + ":") }.sorted().forEach { out += it }
        return out.toList().ifEmpty { HomeWidgets.all }
    }

    fun elementsList(): List<String> {
        val layout = HomeEditState.layout
        val out = LinkedHashSet(cycleList())
        if (CategoryTitle.fallbackOn(layout)) out += HomeWidgets.overlayId(layout, HomeWidgets.CATEGORY)
        Features.hiddenParts.value.filter { !it.startsWith(LayoutParts.PREFIX) || it.startsWith(LayoutParts.PREFIX + layout.name + ":") }
            .filter { layoutOf(it).let { l -> l == null || l == layout } }.forEach { out += it }
        return out.toList()
    }
}

/**
 * Update 212: while editing, a touch on the home screen that isn't on a part (or on Edit Home's bars) goes nowhere — it
 * can't start a game, open a menu or scroll the row. In Preview a tap only ends Preview. On the home screen's root.
 */
internal fun Modifier.homeEditShield(): Modifier = this.pointerInput(Unit) {
    val blocked = HashSet<PointerId>()
    val margin = 18.dp.toPx()
    awaitPointerEventScope {
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            if (!HomeEditState.active) { blocked.clear(); continue }
            for (c in ev.changes) {
                if (c.pressed && !c.previousPressed) {
                    val p = c.position
                    if (HomeEditState.preview) { HomeEditState.preview = false; blocked += c.id; continue }
                    fun inside(r: androidx.compose.ui.geometry.Rect) = p.x >= r.left - margin && p.x <= r.right + margin && p.y >= r.top - margin && p.y <= r.bottom + margin
                    val onPart = HomeEditState.bounds.entries.any { (id, r) -> (id in HomeWidgets.present || id.startsWith(LayoutParts.PREFIX) || id.contains(':')) && inside(r) }
                    val onChrome = HomeEditState.chrome.values.any { r -> p.x >= r.left && p.x <= r.right && p.y >= r.top && p.y <= r.bottom }
                    if (!onPart && !onChrome) blocked += c.id
                }
                if (c.id in blocked) {
                    c.consume()
                    if (!c.pressed) blocked -= c.id
                }
            }
        }
    }
}

/** Update 212: "Preview" said for a moment while it's on (any button or a tap ends it). */
@Composable
internal fun androidx.compose.foundation.layout.BoxScope.PreviewNote() {
    var show by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(2200); show = false }
    if (show) Text(
        "Preview · any button or a tap goes back to editing", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

/** A full-screen, undimmed popup window for Edit Home's panels (the home screen stays in sight behind it). */
@Composable
private fun EditPanelWindow(onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        ImmersiveDialogWindow()
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f)
        }
        // A tap beside the panel closes it (as B does).
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }, content = content)
    }
}

/**
 * Update 212: the selected part's inspector — a side panel on a wide screen (on the side away from the part), a sheet
 * on a small one (top or bottom, away from the part; it folds to its title). Grouped: Position & size, Appearance,
 * Visibility and More (the settings that reach further: every part's backing, Art position, Reset this layout); each
 * says what it changes. B closes it (back to the home screen, the same part selected).
 */
@Composable
internal fun HomeInspector(id: String, onDismiss: () -> Unit) {
    EditPanelWindow(onDismiss) {
        val conf = LocalConfiguration.current
        val dens = LocalDensity.current.density
        val wide = conf.screenWidthDp >= 840
        val b = HomeEditState.bounds[id]
        val cx = b?.center?.x?.div(dens) ?: 0f
        val cy = b?.center?.y?.div(dens) ?: (conf.screenHeightDp * 0.25f)
        var folded by remember { mutableStateOf(false) }
        val panel = Modifier.clip(RoundedCornerShape(18.dp)).background(ShelfTheme.colors.surfaceRaised.copy(alpha = 0.97f))
            .pointerInput(Unit) { detectTapGestures { } }.padding(14.dp)
        if (wide) {
            val left = cx > conf.screenWidthDp / 2f
            Box(Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd).padding(12.dp).width(360.dp).fillMaxHeight().then(panel)) {
                InspectorBody(id, folded = false, onFold = null, onDismiss = onDismiss)
            }
        } else {
            val top = cy > conf.screenHeightDp * 0.5f
            Box(
                Modifier.align(if (top) Alignment.TopCenter else Alignment.BottomCenter).padding(8.dp).fillMaxWidth()
                    .heightIn(max = (conf.screenHeightDp * 0.48f).dp).then(panel)
            ) {
                InspectorBody(id, folded = folded, onFold = { folded = !folded }, onDismiss = onDismiss)
            }
        }
    }
}

@Composable
private fun InspectorBody(id: String, folded: Boolean, onFold: (() -> Unit)?, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(id, folded) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(HomeWidgets.label(id), color = ShelfTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(HomeEditSession.scopeOf(id), color = ShelfTheme.textMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (onFold != null) Chip(if (folded) "▴ Open" else "▾ Fold", accent = false) { onFold() }
            Spacer(Modifier.width(6.dp))
            Chip("Close", accent = false) { onDismiss() }
        }
        if (folded) return@Column
        Spacer(Modifier.height(8.dp))
        val tab = HomeEditState.inspectorTab
        ChipFlow {
            listOf("Position & size", "Appearance", "Visibility", "More").forEachIndexed { i, l ->
                Box(if (i == tab) Modifier.focusRequester(first) else Modifier) { Chip(l, accent = i == tab) { HomeEditState.inspectorTab = i } }
            }
        }
        Spacer(Modifier.height(8.dp))
        val scroll = rememberScrollState()
        Column(Modifier.weight(1f, fill = false).verticalScroll(scroll).dpadScroll(scroll)) {
            when (tab) {
                0 -> {
                    PartSizeControls(id)
                    Spacer(Modifier.height(6.dp))
                    Chip("Reset this part", accent = false) { HomeEditSession.resetSelected(id) }
                }
                1 -> {
                    MutedNote("Applies to: " + HomeEditSession.scopeOf(id))
                    PartLookControls(id)
                }
                2 -> {
                    val on = HomeEditSession.shown(id)
                    MutedNote(
                        when {
                            id.startsWith(LayoutParts.PREFIX) -> "Part of this layout's design: hidden, its place stays (nothing else moves)."
                            HomeEditSession.layoutOf(id) != null -> "Added to this layout: hidden, it's taken off (+ Add or Elements brings it back)."
                            else -> "Hidden wherever it's shared (" + HomeEditSession.scopeOf(id) + ")."
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    ChipFlow {
                        Chip((if (on) "✓ " else "") + "Shown", accent = on) { if (!on) HomeEditSession.toggleShown(id) }
                        Chip((if (!on) "✓ " else "") + "Hidden", accent = !on) { if (on) HomeEditSession.toggleShown(id) }
                    }
                }
                else -> {
                    PercentSlider(
                        "Backing of every part", Features.partBackingSolid.state.value,
                        { HomeEditSession.push("backing"); Features.partBackingSolid.value = ((it / 5) * 5).coerceIn(0, 100) },
                        min = 0, max = 100, valueText = { if (it == 0) "Light" else "$it %" }
                    )
                    MutedNote("Applies to: every part on every layout.")
                    HomeEditState.artLook?.let { look ->
                        Spacer(Modifier.height(8.dp))
                        Chip("Art position · ${look.label}…", accent = false) { onDismiss(); HomeEditState.openArtPosition() }
                        MutedNote("Moves the art inside each ${look.label.lowercase()} frame, not this part. Applies to: every game shown in this look.")
                    }
                    Spacer(Modifier.height(8.dp))
                    ChipFlow {
                        Chip("Elements…", accent = false) { onDismiss(); HomeEditState.elements = true }
                        Chip("+ Add…", accent = false) { onDismiss(); HomeEditState.adding = true }
                        Chip("Preview", accent = false) { onDismiss(); HomeEditState.preview = true }
                    }
                    Spacer(Modifier.height(8.dp))
                    Chip("Reset this layout", accent = false) { HomeEditSession.resetLayout(HomeEditState.layout) }
                    MutedNote("Places, sizes and appearances of ${HomeEditState.layout.label}'s parts. The other layouts, the music player and the round shortcuts keep theirs; Undo brings it back.")
                }
            }
        }
    }
}

/**
 * Update 212: Elements — every part of this home screen, shown, hidden, under another or off the screen: pick one to
 * select it (the inspector opens), or show / hide it here.
 */
@Composable
internal fun HomeElementsSheet(onDismiss: () -> Unit) {
    val list = HomeEditSession.elementsList()
    val conf = LocalConfiguration.current
    val dens = LocalDensity.current.density
    val sw = conf.screenWidthDp * dens
    val sh = conf.screenHeightDp * dens
    ShelfDialog(
        icon = Icons.Filled.ViewList, title = "Elements", subtitle = HomeEditState.layout.label + " · ${list.size} parts", onDismiss = onDismiss,
        footer = { Chip("Close", onClick = onDismiss) }
    ) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
        list.forEachIndexed { i, id ->
            val on = HomeEditSession.shown(id)
            val r = HomeEditState.bounds[id]
            val off = on && r != null && (r.right < 0f || r.bottom < 0f || r.left > sw || r.top > sh)
            val sel = id == HomeEditState.selected
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f).then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                        .shelfClickable { HomeEditState.selected = id; onDismiss(); HomeEditState.inspectorTab = 0; HomeEditState.styling = id }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        (if (sel) "● " else "") + HomeWidgets.label(id), color = ShelfTheme.colors.textPrimary, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(if (!on) "Hidden" else null, if (off) "Off the screen" else null, HomeEditSession.scopeOf(id)).joinToString(" · "),
                        color = ShelfTheme.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Chip(if (on) "Hide" else "Show", accent = !on) { HomeEditSession.toggleShown(id) }
            }
        }
    }
}

/** Update 212: leaving Edit Home with changes — Save, Discard or Keep editing. */
@Composable
internal fun HomeLeavePrompt() {
    ShelfMenuDialog(title = "Leave Edit Home?", subtitle = "Your changes aren't saved yet", onDismiss = { HomeEditState.leaving = false }, icon = Icons.Filled.Edit) {
        MenuRow(Icons.Filled.Check, "Save", "Keep every change") { HomeEditState.leaving = false; HomeEditSession.save() }
        MenuRow(Icons.Filled.Delete, "Discard", "Put everything back as it was when Edit Home opened", destructive = true) { HomeEditState.leaving = false; HomeEditSession.discard() }
        MenuRow(Icons.Filled.Edit, "Keep editing") { HomeEditState.leaving = false }
    }
}
