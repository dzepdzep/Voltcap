package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HotkeyAction
import com.shelf.launcher.SpecialFilter
import kotlin.math.sin

/*
 * Update 344: Terminal — Voltcap's own old-computer operating system ("VOLTCAP OS", original; nobody's system copied).
 * The screen is a monitor: a directory listing on the left, a preview window on the right, a command line along the
 * bottom typing out what A would do.
 * - C:\ (the root): GAMES\, SYSTEMS\, FAVORITE\, RECENT\ and the programs — MUSIC.EXE, SAVES.EXE, APPS.EXE, SEARCH.EXE,
 *   SORT.EXE, SETTINGS.EXE.
 * - GAMES\<CATEGORY>: the category's games as files (name, console as its extension, time played, last played), the one
 *   you're on in reverse video; the preview shows it dithered in the monitor's phosphor with its facts and achievements.
 * - SYSTEMS\: your consoles as directories; A goes in.
 * ▲ ▼ the cursor (Endless: round), ◀ ▶ a screen at a time, A runs / goes in (a game: its window, a DOS-style dialog),
 * B goes up a directory (at C:\ it's the home screen's own). Monitors: green, amber, white phosphor, a blue DOS shell,
 * four-colour; CRT effects (scanlines, glow, the tube's curve and a slight flicker) light or full; a short boot.
 */

/** Update 344: where the controller is in the Terminal. */
internal object TermNav {
    enum class Dir { ROOT, GAMES, SYSTEMS }
    var dir by mutableStateOf(Dir.GAMES)
    var rootSel by mutableIntStateOf(0)
    var sysSel by mutableIntStateOf(0)
    var booted = false
    @Volatile var ctx: StyleCtx? = null
    @Volatile var root: List<TermEntry> = emptyList()
    @Volatile var systems: List<Int> = emptyList()
    @Volatile var rows: Int = 12
    /** Update 346: the listing's 8.3 names (stableKey → alias), for the RUN window too. */
    @Volatile var aliases: Map<String, String> = emptyMap()
    fun alias(g: Game): String = aliases[g.stableKey] ?: termFileName(g.name)
    internal val keysDown = HashSet<Int>()
    fun signature(): Any = listOf(dir, rootSel, sysSel)
}

/** Update 344: a line of C:\ — a directory or a program. */
internal class TermEntry(val name: String, val dir: Boolean, val note: String, val act: () -> Unit)

/** Update 344: a monitor's colours. */
internal class TermColors(val bg: Color, val fg: Color, val dim: Color, val hi: Color, val hiInk: Color, val accent: Color)

internal fun termColors(t: Features.TmTheme): TermColors = when (t) {
    Features.TmTheme.GREEN -> TermColors(Color(0xFF020A04), Color(0xFF39FF7A), Color(0xFF1C8A43), Color(0xFF39FF7A), Color(0xFF021006), Color(0xFFB6FFCB))
    Features.TmTheme.AMBER -> TermColors(Color(0xFF0D0700), Color(0xFFFFB000), Color(0xFF8A5E00), Color(0xFFFFB000), Color(0xFF140B00), Color(0xFFFFE0A0))
    Features.TmTheme.WHITE -> TermColors(Color(0xFF07080A), Color(0xFFE6ECF0), Color(0xFF7D868D), Color(0xFFE6ECF0), Color(0xFF07080A), Color(0xFFFFFFFF))
    Features.TmTheme.DOS -> TermColors(Color(0xFF0000A8), Color(0xFFFFFFFF), Color(0xFFA8A8A8), Color(0xFF00A8A8), Color(0xFF000000), Color(0xFFFFFF55))
    Features.TmTheme.CGA -> TermColors(Color(0xFF000000), Color(0xFF55FFFF), Color(0xFF00A8A8), Color(0xFFFF55FF), Color(0xFF000000), Color(0xFFFFFFFF))
}

private fun tmSound(s: KitSound, fallback: UiSound?) = LayoutKitSounds.play(LayoutKit.TERMINAL, s, fallback)

private fun tmHotkey(a: HotkeyAction): Boolean {
    val h = GreenCoreHooks.hotkey ?: return false
    h(a)
    return true
}

/** The stop for a special list (Favourites, Recently played) in the category cycle, or -1. */
private fun termStop(c: StyleCtx, f: SpecialFilter): Int = c.stops.indexOfFirst { it.special == f }

internal fun termRootEntries(c: StyleCtx): List<TermEntry> = buildList {
    add(TermEntry("GAMES", true, c.categoryLabel.uppercase()) { TermNav.dir = TermNav.Dir.GAMES })
    add(TermEntry("SYSTEMS", true, "${TermNav.systems.size} DIRS") {
        TermNav.sysSel = TermNav.systems.indexOf(c.stop).coerceAtLeast(0); TermNav.dir = TermNav.Dir.SYSTEMS
    })
    termStop(c, SpecialFilter.FAVORITES).takeIf { it >= 0 }?.let { s -> add(TermEntry("FAVORITE", true, "YOUR FAVOURITES") { termGoStop(c, s) }) }
    termStop(c, SpecialFilter.RECENT).takeIf { it >= 0 }?.let { s -> add(TermEntry("RECENT", true, "PLAYED LATELY") { termGoStop(c, s) }) }
    add(TermEntry("MUSIC.EXE", false, "MUSIC PLAYER") { if (!tmHotkey(HotkeyAction.OPEN_SPOTIFY)) MusicSwipe.handler?.invoke() })
    add(TermEntry("SAVES.EXE", false, "SAVE VAULT") { SaveExplorerHook.request() })
    add(TermEntry("APPS.EXE", false, "ALL APPS") { if (!tmHotkey(HotkeyAction.APP_DRAWER)) c.shortcuts.firstOrNull { s -> s.label == "All apps" }?.onClick?.invoke() })
    add(TermEntry("SEARCH.EXE", false, "FIND A GAME") { c.actions.search() })
    add(TermEntry("SORT.EXE", false, "SORT & FILTER") { c.actions.sort() })
    add(TermEntry("SETTINGS.EXE", false, "SETUP") { c.actions.settings() })
}

private fun termGoStop(c: StyleCtx, stop: Int) {
    val steps = pocketStepsTo(c, stop)
    if (steps != 0) c.actions.category(steps)
    TermNav.dir = TermNav.Dir.GAMES
}

/** B / the back gesture: up a directory. False at C:\. */
internal fun termBack(): Boolean {
    if (TermNav.dir == TermNav.Dir.ROOT) return false
    TermNav.rootSel = if (TermNav.dir == TermNav.Dir.SYSTEMS) 1 else 0
    TermNav.dir = TermNav.Dir.ROOT
    tmSound(KitSound.BACK, UiSound.BACK)
    return true
}

/** Update 344: the Terminal's keys (all of them while it's on). True = handled. */
internal fun terminalKey(event: KeyEvent, reach: (Boolean) -> Boolean): Boolean {
    val c = TermNav.ctx ?: return false
    val code = event.nativeKeyEvent.keyCode
    val down = event.type == KeyEventType.KeyDown
    val up = event.type == KeyEventType.KeyUp
    if (up && TermNav.keysDown.remove(code)) return true
    val k = event.key
    if (k == Key.ButtonStart || k == Key.Menu) { if (up) c.actions.quickMenu(); return up }
    if (k == Key.ButtonSelect) return up && tmHotkey(HotkeyAction.SORT)
    val dir = TermNav.dir
    val isA = k == Key.ButtonA || k == Key.Enter || k == Key.DirectionCenter
    if (isA && dir != TermNav.Dir.GAMES) {
        if (up && !event.nativeKeyEvent.isCanceled) {
            if (dir == TermNav.Dir.ROOT) TermNav.root.getOrNull(TermNav.rootSel)?.let { tmSound(KitSound.SELECT, UiSound.OPEN); it.act() }
            else TermNav.systems.getOrNull(TermNav.sysSel)?.let { s -> tmSound(KitSound.SELECT, UiSound.SELECT); termGoStop(c, s) }
        }
        return true
    }
    if (!down) return false
    val first = event.nativeKeyEvent.repeatCount == 0
    fun take(): Boolean { TermNav.keysDown += code; return true }
    val isB = k == Key.ButtonB || k == Key.Back
    if (isB) return if (first && termBack()) take() else if (dir == TermNav.Dir.GAMES) take() else false
    val page = (TermNav.rows - 1).coerceAtLeast(1)
    val d = when (k) { Key.DirectionUp -> -1; Key.DirectionDown -> 1; Key.DirectionLeft -> -page; Key.DirectionRight -> page; else -> 0 }
    if (d == 0) return if (dir == TermNav.Dir.GAMES && c.games.isNotEmpty()) false else if (isA || k == Key.ButtonX || k == Key.ButtonY) take() else false
    when (dir) {
        TermNav.Dir.GAMES -> {
            val n = c.games.size
            if (n == 0) return take()
            val next = if (abs1(d) && StyleLoop.on && n > 1) (c.focus + d).mod(n) else (c.focus + d).coerceIn(0, n - 1)
            if (next != c.focus) { c.actions.select(next); tmSound(KitSound.MOVE, UiSound.MOVE) } else if (first && abs1(d)) reach(d < 0)
        }
        TermNav.Dir.ROOT -> {
            val n = TermNav.root.size
            val next = (TermNav.rootSel + d).coerceIn(0, (n - 1).coerceAtLeast(0))
            if (next != TermNav.rootSel) { TermNav.rootSel = next; tmSound(KitSound.MOVE, UiSound.MOVE) } else if (first && abs1(d)) reach(d < 0)
        }
        TermNav.Dir.SYSTEMS -> {
            val n = TermNav.systems.size
            val next = (TermNav.sysSel + d).coerceIn(0, (n - 1).coerceAtLeast(0))
            if (next != TermNav.sysSel) { TermNav.sysSel = next; tmSound(KitSound.MOVE, UiSound.MOVE) } else if (first && abs1(d)) reach(d < 0)
        }
    }
    return take()
}

private fun abs1(d: Int) = d == 1 || d == -1

// ---- The monitor ----

/** Update 344: the monitor's black (or the DOS shell's blue) glass and, with full CRT effects, the bezel round it. */
@Composable
internal fun TerminalBackdrop(modifier: Modifier) {
    val tc = termColors(Features.tmTheme.state.value)
    Canvas(modifier) {
        drawRect(if (Features.tmCrt.value == Features.TmCrt.FULL) Color(0xFF15161A) else tc.bg)
    }
}

/** A short file name from a game's name: capitals and digits, eight of them, as an old system would keep it. */
internal fun termFileName(name: String): String {
    val joined = termStem(name)
    return if (joined.length <= 8) joined else joined.take(6) + "~1"
}

/** The game's name as an old system's file name before it's cut: capitals and digits only, tags in brackets dropped. */
internal fun termStem(name: String): String {
    val clean = name.uppercase().replace(Regex("\\(.*?\\)|\\[.*?]"), "").replace(Regex("[^A-Z0-9 ]"), "").trim()
    return clean.split(' ').filter { it.isNotBlank() }.joinToString("").ifEmpty { "GAME" }
}

/**
 * Update 346: every game of a listing its own 8.3 name, as the old systems made them — the name itself when it fits and is
 * free, else its first letters and ~1, ~2, … (~10 on: one letter fewer). Unique per name + extension; worked out in a
 * fixed order (the names, then the keys), so a game keeps its alias whatever the list's sort. Two "Asterix & Obelix
 * XXL" games were both ASTERI~1.PS2. [ext] gives each game's extension.
 */
internal fun <T> termAliases(items: List<T>, key: (T) -> String, name: (T) -> String, ext: (T) -> String): Map<String, String> {
    val used = HashSet<String>()
    val out = HashMap<String, String>(items.size * 2)
    val order = items.map { Triple(termStem(name(it)), key(it), ext(it)) }.sortedWith(compareBy({ it.first }, { it.second }))
    for ((stem, k, e) in order) {
        if (k in out) continue
        var alias = stem.takeIf { it.length <= 8 && (it + "." + e) !in used }
        var n = 1
        while (alias == null) {
            val tail = "~$n"
            val cand = stem.take(8 - tail.length) + tail
            if ((cand + "." + e) !in used) alias = cand
            n++
        }
        used += alias + "." + e
        out[k] = alias
    }
    return out
}

internal fun termExt(g: Game): String = if (g.sourceType == com.shelf.launcher.data.SourceType.APP) "APK" else g.platform.uppercase().replace(Regex("[^A-Z0-9]"), "").take(3).ifEmpty { "ROM" }

@Composable
internal fun StyleTerminal(c: StyleCtx, modifier: Modifier = Modifier) {
    val systems = remember(c.stops) { gcSystems(c) }
    // Update 346: every file its own 8.3 name (two games could share ASTERI~1).
    val aliases = remember(c.games) { termAliases(c.games, { it.stableKey }, { it.name }, ::termExt) }
    SideEffect {
        TermNav.ctx = c
        TermNav.systems = systems
        TermNav.root = termRootEntries(c)
        TermNav.aliases = aliases
        StyleNav.side = emptyList(); StyleNav.top = emptyList(); StyleNav.bar = emptyList()
        StyleLoop.on = loopRowOn(c)
    }
    rememberLayoutKit(LayoutKit.TERMINAL, Features.tmSounds.state.value, c.state.appearance.soundsEnabled)
    DisposableEffect(Unit) { onDispose { TermNav.keysDown.clear() } }
    val windowOpen = GamePopup.game != null
    androidx.activity.compose.BackHandler(enabled = !windowOpen && TermNav.dir != TermNav.Dir.ROOT) { termBack() }
    val tc = termColors(Features.tmTheme.state.value)
    val crt = Features.tmCrt.state.value
    val font: FontFamily = if (Features.tmPixel.state.value) pocketFont() else FontFamily.Monospace
    val reduced = !rememberAnimationsEnabled()
    val lite = LocalLiteEffects.current
    val active = rememberIsActive()
    // The boot: a few lines of the machine starting, once a session.
    val boot = remember { Animatable(if (TermNav.booted || !Features.tmBoot.value || reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (boot.value >= 1f) { TermNav.booted = true; return@LaunchedEffect }
        tmSound(KitSound.SPECIAL, null)
        boot.animateTo(1f, tween(1500, easing = LinearEasing))
        TermNav.booted = true
    }
    val clock = rememberGcClock(active && !reduced && !lite, 1f, 12)
    if (TermNav.dir == TermNav.Dir.GAMES && CategoryTitle.captionOn(c.state.appearance)) CategoryTitle.Native(null)
    val g = c.game
    LaunchedEffect(g?.stableKey) {
        val gg = g ?: return@LaunchedEffect
        kotlinx.coroutines.delay(400)
        c.actions.needMetadata(gg)
    }
    val density = LocalDensity.current
    val glow = crt != Features.TmCrt.OFF
    BoxWithConstraints(modifier.fillMaxSize().padding(if (crt == Features.TmCrt.FULL) 10.dp else 0.dp)) {
        // (Update 346: sized to the screen — small handhelds got 12 sp text and a preview squeezed to a strip.)
        val compact = maxHeight < 380.dp
        val narrow = maxWidth < 600.dp
        val fsz: TextUnit = when {
            compact && narrow -> 13.sp
            compact || narrow -> 14.sp
            else -> 15.sp
        }
        val lineH = with(density) { (fsz.toPx() * 1.32f).toDp() }
        val style = TextStyle(fontFamily = font, fontSize = fsz, lineHeight = fsz * 1.32f, color = tc.fg, shadow = if (glow) Shadow(tc.fg.copy(alpha = 0.75f), Offset.Zero, 10f) else null)
        val screenShape = RoundedCornerShape(if (crt == Features.TmCrt.FULL) 22.dp else 0.dp)
        Box(Modifier.fillMaxSize().clip(screenShape).background(tc.bg).graphicsLayer {
            // A faint flicker of the tube (full effects only, never in Lite).
            alpha = if (crt == Features.TmCrt.FULL && !lite) 0.97f + 0.03f * sin(clock.floatValue * 40f) else 1f
        }) {
            if (boot.value < 1f) TermBoot(boot.value, style, tc)
            else Column(Modifier.fillMaxSize().padding(horizontal = if (narrow) 8.dp else 14.dp, vertical = 6.dp)) {
                // The title line, in reverse.
                Row(Modifier.fillMaxWidth().background(tc.hi).padding(horizontal = 6.dp)) {
                    Text(if (narrow) " VOLTCAP OS" else " VOLTCAP OS  v1.0", style = style.copy(color = tc.hiInk, shadow = null), maxLines = 1, modifier = Modifier.weight(1f))
                    Text(termPath(c) + " ", style = style.copy(color = tc.hiInk, shadow = null), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    // (The listing gets the room the names need; the preview keeps at least its picture and facts.)
                    val listW = if (narrow) 0.6f else 0.56f
                    TermListing(c, tc, style, lineH, Modifier.weight(listW).fillMaxHeight())
                    Spacer(Modifier.width(if (narrow) 8.dp else 12.dp))
                    TermPreview(c, tc, style, compact, Modifier.weight(1f - listW).fillMaxHeight())
                }
                Spacer(Modifier.height(4.dp))
                TermPrompt(c, tc, style, clock)
                TermHints(tc, style.copy(fontSize = fsz * 0.8f, shadow = null), narrow)
            }
            if (crt != Features.TmCrt.OFF) TermCrt(crt == Features.TmCrt.FULL)
        }
    }
}

/** Update 346: the controller's keys for what's on screen, as key caps in reverse video — the handheld's buttons, not a keyboard's. */
@Composable
private fun TermHints(tc: TermColors, style: TextStyle, narrow: Boolean) {
    val keys = when (TermNav.dir) {
        TermNav.Dir.GAMES -> listOf("A" to "RUN", "B" to "UP", "X" to "FAV", "Y" to "TROPHIES", "L2/R2" to "DRIVE") + if (narrow) emptyList() else listOf("◀▶" to "PAGE")
        else -> listOf("A" to "OPEN", "B" to "UP", "▲▼" to "MOVE") + if (narrow) emptyList() else listOf("◀▶" to "PAGE")
    }
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        keys.forEach { (k, v) ->
            Text(" $k ", style = style.copy(color = tc.bg), maxLines = 1, modifier = Modifier.background(tc.dim))
            Text(" $v  ", style = style.copy(color = tc.dim), maxLines = 1)
        }
    }
}

private fun termPath(c: StyleCtx): String = when (TermNav.dir) {
    TermNav.Dir.ROOT -> "C:\\"
    TermNav.Dir.SYSTEMS -> "C:\\SYSTEMS"
    TermNav.Dir.GAMES -> "C:\\GAMES\\" + termFileName(c.categoryLabel)
}

/** The boot screen: the machine's lines appearing one after another. */
@Composable
private fun TermBoot(k: Float, style: TextStyle, tc: TermColors) {
    val lines = listOf("VOLTCAP BIOS v1.0", "MEMORY TEST ... 640K OK", "DETECTING CONTROLLER ... OK", "MOUNTING C:\\GAMES ... OK", "", "C:\\> CD GAMES")
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        val shown = (k * (lines.size + 1)).toInt().coerceAtMost(lines.size)
        lines.take(shown).forEach { Text(it, style = style) }
        Text("_", style = style.copy(color = tc.accent))
    }
}

/** Update 346: [s] cut to [n] characters, a "…" where it was cut (never mid-way without saying so). */
internal fun termFit(s: String, n: Int): String = when {
    n <= 0 -> ""
    s.length <= n -> s.padEnd(n)
    n == 1 -> "…"
    else -> s.take(n - 1) + "…"
}

/**
 * Update 346: one listing line for a game, fitted to [cols] characters: its 8.3 name and extension, its title (as it is
 * — the 8.3 name is only the file's), the time played at the right. Narrow listings drop the 8.3 name before the title.
 */
internal fun termGameLine(alias: String, ext: String, title: String, played: String, cols: Int, full: Boolean = false): String {
    val pt = played.padStart(6)
    val withFile = cols >= 44
    val head = if (withFile) alias.padEnd(8) + " " + ext.padEnd(3) + "  " else ""
    val room = (cols - head.length - pt.length - 1).coerceAtLeast(4)
    return head + (if (full) title.padEnd(room) else termFit(title, room)) + " " + pt
}

/** The directory listing: the lines near the cursor, the one it's on in reverse video. */
@Composable
private fun TermListing(c: StyleCtx, tc: TermColors, style: TextStyle, lineH: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    val titles = LocalTitleOptions.current
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val latest by rememberUpdatedState(c)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val charW = remember(style.fontFamily, style.fontSize) { measurer.measure("MMMMMMMMMM", style.copy(shadow = null)).size.width / 10f }
    val marquee = rememberAnimationsEnabled()
    BoxWithConstraints(modifier.clipToBounds().pointerInput(Unit) {
        var acc = 0f
        detectVerticalDragGestures(onDragStart = { acc = 0f }) { ch, dy ->
            ch.consume(); acc += dy
            val step = lineH.toPx()
            val cc = latest
            fun move(d: Int) {
                when (TermNav.dir) {
                    TermNav.Dir.GAMES -> { val nx = (cc.focus + d).coerceIn(0, (cc.games.size - 1).coerceAtLeast(0)); if (nx != cc.focus) select(nx) }
                    TermNav.Dir.ROOT -> TermNav.rootSel = (TermNav.rootSel + d).coerceIn(0, (TermNav.root.size - 1).coerceAtLeast(0))
                    TermNav.Dir.SYSTEMS -> TermNav.sysSel = (TermNav.sysSel + d).coerceIn(0, (TermNav.systems.size - 1).coerceAtLeast(0))
                }
            }
            while (acc <= -step) { acc += step; move(1) }
            while (acc >= step) { acc -= step; move(-1) }
        }
    }) {
        val rows = ((maxHeight / lineH).toInt() - 2).coerceAtLeast(3)
        // Characters a line holds (less the cursor mark and the row's padding).
        val cols = with(LocalDensity.current) { ((maxWidth - 8.dp).toPx() / charW.coerceAtLeast(1f)).toInt() - 2 }.coerceAtLeast(12)
        SideEffect { TermNav.rows = rows }
        val (count, sel) = when (TermNav.dir) {
            TermNav.Dir.GAMES -> c.games.size to c.focus
            TermNav.Dir.ROOT -> TermNav.root.size to TermNav.rootSel
            TermNav.Dir.SYSTEMS -> TermNav.systems.size to TermNav.sysSel
        }
        val first = (sel - rows / 2).coerceIn(0, (count - rows).coerceAtLeast(0))
        Column {
            Text(termFit(" Directory of " + termPath(c), cols + 2), style = style.copy(color = tc.dim), maxLines = 1, softWrap = false)
            for (i in first until (first + rows).coerceAtMost(count)) {
                val on = i == sel
                var isDir = false
                var cut = false
                val line: String = when (TermNav.dir) {
                    TermNav.Dir.GAMES -> c.games[i].let { g ->
                        val title = titles.display(g.name).uppercase()
                        val pt = c.playtime(g)?.let { ms -> val m = (ms / 60_000L).toInt(); if (m >= 60) "${m / 60}h${(m % 60).toString().padStart(2, '0')}" else "${m}m" } ?: "--"
                        val ln = termGameLine(TermNav.alias(g), termExt(g), title, pt, cols)
                        // (The one you're on scrolls its whole title when it doesn't fit.)
                        if (on && marquee && ln.contains('…')) { cut = true; termGameLine(TermNav.alias(g), termExt(g), title, pt, cols, full = true) } else ln
                    }
                    TermNav.Dir.ROOT -> TermNav.root[i].let { e -> isDir = e.dir; termFit(e.name.padEnd(13) + (if (e.dir) "<DIR>" else "     ") + "  " + e.note, cols) }
                    TermNav.Dir.SYSTEMS -> c.stops.getOrNull(TermNav.systems[i])?.let { st ->
                        isDir = true
                        val n = c.state.games.count { it.platform == st.platform }
                        val head = termFileName(st.label).padEnd(9) + "<DIR>  "
                        head + termFit(st.label.uppercase(), (cols - head.length - 6).coerceAtLeast(4)) + "$n".padStart(6)
                    } ?: ""
                }
                key(i) {
                    Row(
                        Modifier.fillMaxWidth().background(if (on) tc.hi else Color.Transparent).padding(horizontal = 4.dp)
                            .pointerInput(i) {
                                detectTapGestures {
                                    when (TermNav.dir) {
                                        TermNav.Dir.GAMES -> if (latest.focus == i) open() else { select(i); tmSound(KitSound.MOVE, UiSound.MOVE) }
                                        TermNav.Dir.ROOT -> if (TermNav.rootSel == i) TermNav.root.getOrNull(i)?.let { tmSound(KitSound.SELECT, UiSound.OPEN); it.act() } else TermNav.rootSel = i
                                        TermNav.Dir.SYSTEMS -> if (TermNav.sysSel == i) TermNav.systems.getOrNull(i)?.let { termGoStop(latest, it) } else TermNav.sysSel = i
                                    }
                                }
                            }
                    ) {
                        // (Update 346: a ► on the line you're on as well as the reverse video — it reads on the white and four-colour monitors too.)
                        Text(if (on) "►" else " ", style = if (on) style.copy(color = tc.hiInk, shadow = null) else style, maxLines = 1)
                        Text(
                            line, style = if (on) style.copy(color = tc.hiInk, shadow = null) else style.copy(color = if (isDir) tc.accent else tc.fg),
                            maxLines = 1, overflow = TextOverflow.Clip, softWrap = false,
                            modifier = Modifier.weight(1f).then(
                                if (cut) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1200, repeatDelayMillis = 1500) else Modifier
                            )
                        )
                    }
                }
            }
            val pos = if (count > 0) "  [${sel + 1}/$count]" else ""
            Text(" " + count + (if (TermNav.dir == TermNav.Dir.GAMES) " file(s)" else " item(s)") + pos, style = style.copy(color = tc.dim), maxLines = 1)
        }
    }
}

/** The preview window: a framed box with the game dithered in the phosphor's shades, its facts and achievements. */
@Composable
private fun TermPreview(c: StyleCtx, tc: TermColors, style: TextStyle, compact: Boolean, modifier: Modifier) {
    val titles = LocalTitleOptions.current
    val pal = intArrayOf(
        tc.bg.toArgb(), lerp(tc.bg, tc.fg, 0.33f).toArgb(), lerp(tc.bg, tc.fg, 0.66f).toArgb(), tc.fg.toArgb()
    )
    val f = style.fontSize * 0.88f
    val small = style.copy(fontSize = f, lineHeight = f * 1.25f)
    Column(modifier.border(2.dp, tc.fg).padding(if (compact) 6.dp else 8.dp)) {
        Text("[ PREVIEW ]", style = style.copy(color = tc.accent), maxLines = 1)
        Spacer(Modifier.height(4.dp))
        when (TermNav.dir) {
            TermNav.Dir.GAMES -> c.game?.let { g ->
                val bmp = rememberDithered(c.cover(g), 96, 128, pal)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxHeight().aspectRatio(96f / 128f, matchHeightConstraintsFirst = true).border(1.dp, tc.dim)) {
                        if (bmp != null) Image(bmp, g.name, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None)
                    }
                }
                Spacer(Modifier.height(4.dp))
                val meta = c.state.metadata[g.stableKey]
                // (Update 346: the whole title — two lines — and the file's 8.3 name under it; the listing may have cut it.)
                Text(titles.display(g.name).uppercase(), style = small, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                Text(TermNav.alias(g) + "." + termExt(g), style = small.copy(color = tc.dim), maxLines = 1)
                Text("SYS  " + (if (g.sourceType == com.shelf.launcher.data.SourceType.APP) "ANDROID" else titles.console(g.platform).uppercase()), style = small.copy(color = tc.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compact) meta?.releaseYear?.let { Text("YEAR $it", style = small.copy(color = tc.dim), maxLines = 1) }
                Text("TIME " + stylePlaytime(c.playtime(g), c.lastPlayed(g) != null).uppercase(), style = small.copy(color = tc.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                c.progress(g)?.let { (e, t) ->
                    val filled = ((e.toFloat() / t) * 10).toInt().coerceIn(0, 10)
                    Text("[" + "#".repeat(filled) + ".".repeat(10 - filled) + "] $e/$t", style = small, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false)
                }
            }
            TermNav.Dir.ROOT -> TermNav.root.getOrNull(TermNav.rootSel)?.let { e ->
                Text(e.name, style = style.copy(fontSize = style.fontSize * 1.3f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(e.note, style = small.copy(color = tc.dim), maxLines = 2)
                Spacer(Modifier.height(6.dp))
                // (Update 346: what's in the directory — the empty preview said only "a directory".)
                val inside: List<String> = when (e.name) {
                    "GAMES" -> c.games.take(12).map { TermNav.alias(it) + "." + termExt(it) }
                    "SYSTEMS" -> TermNav.systems.take(12).mapNotNull { c.stops.getOrNull(it)?.label?.let { l -> termFileName(l) + "  <DIR>" } }
                    else -> emptyList()
                }
                if (inside.isNotEmpty()) {
                    Text("CONTENTS:", style = small.copy(color = tc.accent), maxLines = 1)
                    Column(Modifier.weight(1f).clipToBounds()) { inside.forEach { Text("  $it", style = small, maxLines = 1, softWrap = false) } }
                } else {
                    Text(if (e.dir) "DIRECTORY — PRESS A TO OPEN IT." else "PROGRAM — PRESS A TO RUN IT.", style = small.copy(color = tc.dim))
                }
            }
            TermNav.Dir.SYSTEMS -> c.stops.getOrNull(TermNav.systems.getOrNull(TermNav.sysSel) ?: -1)?.let { st ->
                Text(st.label.uppercase(), style = style.copy(fontSize = style.fontSize * 1.15f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                val games = c.state.games.filter { it.platform == st.platform }
                Text("${games.size} FILE(S)", style = small.copy(color = tc.dim), maxLines = 1)
                Spacer(Modifier.height(6.dp))
                // (Update 346: its games played last, else its first ones, so the preview isn't an empty box.)
                val shown = games.sortedByDescending { c.lastPlayed(it) ?: 0L }.take(10)
                if (shown.isNotEmpty()) {
                    Text(if (shown.any { c.lastPlayed(it) != null }) "PLAYED LAST:" else "CONTENTS:", style = small.copy(color = tc.accent), maxLines = 1)
                    Column(Modifier.weight(1f).clipToBounds()) { shown.forEach { Text("  " + titles.display(it.name).uppercase(), style = small, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                }
            }
        }
    }
}

/** The command line: what A would do, typed out key by key, and the blinking block cursor. */
@Composable
private fun TermPrompt(c: StyleCtx, tc: TermColors, style: TextStyle, clock: androidx.compose.runtime.FloatState) {
    val cmd = when (TermNav.dir) {
        TermNav.Dir.GAMES -> c.game?.let { "RUN " + TermNav.alias(it) + "." + termExt(it) } ?: ""
        TermNav.Dir.ROOT -> TermNav.root.getOrNull(TermNav.rootSel)?.let { if (it.dir) "CD " + it.name else it.name } ?: ""
        TermNav.Dir.SYSTEMS -> c.stops.getOrNull(TermNav.systems.getOrNull(TermNav.sysSel) ?: -1)?.let { "CD " + termFileName(it.label) } ?: ""
    }
    val typed = remember { Animatable(1f) }
    val reduced = !rememberAnimationsEnabled()
    LaunchedEffect(cmd) {
        if (reduced) { typed.snapTo(1f); return@LaunchedEffect }
        typed.snapTo(0f)
        typed.animateTo(1f, tween((cmd.length * 18).coerceIn(80, 360), easing = LinearEasing))
    }
    val shown = cmd.take((cmd.length * typed.value).toInt())
    val blink = (clock.floatValue * 2f).toInt() % 2 == 0 || reduced
    Text(termPath(c) + "> " + shown + (if (blink) "█" else " "), style = style, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false)
}

/** The tube: scanlines, a soft vignette, (full) the glass's curve shading and a faint sheen. */
@Composable
private fun TermCrt(full: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        var y = 0f
        val step = 3f
        while (y < size.height) { drawRect(Color.Black.copy(alpha = 0.18f), Offset(0f, y), Size(size.width, 1f)); y += step }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (full) 0.55f else 0.3f)), center = Offset(size.width / 2f, size.height / 2f),
            radius = maxOf(size.width, size.height) * 0.72f))
        if (full) drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent), start = Offset.Zero, end = Offset(size.width * 0.4f, size.height * 0.5f)))
    }
}

// ---- Options ----

/** Update 344: Settings › Home › Layout options (Terminal). Each its own "tm_…" Features pref. */
@Composable
internal fun TerminalSettings() {
    MutedNote(
        "An old computer's screen: a directory listing of your games, a preview window, a command line. ▲ ▼ the cursor, " +
            "◀ ▶ a screen at a time, A runs or goes in, B goes up a directory. C:\\ has your systems, favourites and programs."
    )
    Spacer(Modifier.height(10.dp))
    StyleChoice("Monitor", Features.TmTheme.values(), Features.tmTheme.state.value, { it.label }) { Features.tmTheme.value = it }
    StyleChoice("CRT effects", Features.TmCrt.values(), Features.tmCrt.state.value, { it.label }) { Features.tmCrt.value = it }
    Toggle(Icons.Filled.Terminal, "Pixel type", "The blocky pixel font instead of the plain fixed-width one.", Features.tmPixel)
    Toggle(Icons.Filled.Terminal, "Boot screen", "A few lines of the machine starting the first time it's shown.", Features.tmBoot)
    Toggle(Icons.Filled.QueueMusic, "Interface sounds", "Key clicks and a speaker beep. Off: Voltcap's usual sounds.", Features.tmSounds)
    if (Features.tmSounds.state.value) {
        PercentSlider("Sound volume", Features.kitVolume.state.value, { Features.kitVolume.value = it.coerceIn(0, 100) })
        Spacer(Modifier.height(6.dp))
    }
}
