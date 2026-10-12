package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.NameBackdrop
import com.shelf.launcher.data.statusOn
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Update 158: Hero tiles. A clean handheld home: the top of the screen (about 45 %) is the game you're on's big
 * picture with rounded corners, its logo (or name) bottom left and a small status pill (Wi-Fi, battery, clock) top
 * right; under it one or two rows of square tiles in rounded frames on a pale backdrop, scrolling sideways, the one
 * you're on bigger with a glowing ring in the theme's colour. Along the bottom: a small pill with ⓘ (details) and ✕
 * (close the game when it's still open) on the left, a dock of app shortcuts in the middle and the menu (☰) on the right.
 * ◀ ▶ along the rows, ▲ ▼ between the rows, ▼ from the bottom row onto the dock (◀ ▶ along the buttons, ▲ / B back).
 * Endless goes round one column at a time (ContinuousGrid, StyleLoop). Options: Settings › Home › Layout options.
 */

/** Update 158: where the dock starts in the bottom buttons (StyleNav.bar), so ▼ lands on its first app. */
internal object HeroTilesNav {
    @Volatile var dockAt = 0
}

/** Update 158: one button of the dock: its name, its symbol (or an app's own icon) and what it does. */
private class HeroDockItem(val label: String, val icon: ImageVector?, val pkg: String?, val act: () -> Unit)

/** Update 158: the pale backdrop behind Hero tiles (drawn across the whole screen by StyleBackLayer). */
@Composable
internal fun HeroTilesBackdrop(modifier: Modifier = Modifier) {
    val accent = ShelfTheme.accent
    val top = remember(accent) { androidx.compose.ui.graphics.lerp(Color(0xFFF5F7FA), accent, 0.03f) }
    val bottom = remember(accent) { androidx.compose.ui.graphics.lerp(Color(0xFFE1E6ED), accent, 0.05f) }
    Box(modifier.background(Brush.verticalGradient(listOf(top, bottom))))
}

@Composable
internal fun StyleHeroTiles(c: StyleCtx, modifier: Modifier = Modifier) {
    val g = c.game
    val dockOn = Features.heroTilesDock.state.value && !LayoutParts.off(HomeLayout.HERO_TILES, "dock")
    val buttonsOn = !LayoutParts.off(HomeLayout.HERO_TILES, "buttons")
    val running = g != null && g.stableKey in com.shelf.launcher.data.RunningGames.keys
    val dockAll = rememberHeroDock(c)
    val dock = if (dockOn) dockAll else emptyList()
    // The bottom buttons, left to right: ⓘ, ✕ (only while the game is open), the dock, ☰.
    val left = if (buttonsOn && g != null) (if (running) 2 else 1) else 0
    SideEffect {
        StyleNav.side = emptyList()
        StyleNav.top = emptyList()
        StyleLoop.on = loopRowOn(c)
        val bar = ArrayList<() -> Unit>(dock.size + 3)
        if (left > 0 && g != null) {
            bar.add { c.actions.details() }
            if (left > 1) bar.add { c.actions.favorite(g) }
        }
        dock.forEach { bar.add(it.act) }
        if (buttonsOn) bar.add { c.actions.settings() }
        HeroTilesNav.dockAt = if (dock.isEmpty()) 0 else left
        StyleNav.bar = bar
    }
    val pale = Features.heroTilesPale.state.value
    val lightUi = pale || ShelfTheme.colors.surface.luminance() > 0.5f
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < 330.dp
        val rows = Features.heroTilesRows.state.value.coerceIn(1, 2)
        val heroH = maxHeight * (if (rows == 2 && maxHeight < 400.dp) 0.40f else 0.45f)
        val barH = if (compact) 34.dp else 42.dp
        val gap = if (compact) 6.dp else 10.dp
        Column(Modifier.fillMaxSize()) {
            HeroPicture(
                c, g, compact, showStatus = !c.state.appearance.statusOn,
                modifier = Modifier.fillMaxWidth().height(heroH).editablePart(LayoutParts.id(HomeLayout.HERO_TILES, "hero"), "Big picture")
            )
            Spacer(Modifier.height(gap))
            // Plain if / else, never an early return out of an inline composable lambda.
            // Update 158 (review): on the pale backdrop the usual white "No games match." can't be read: dark grey there.
            if (g == null && lightUi) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No games match.", color = Color(0xFF2A313B).copy(alpha = 0.75f), fontSize = 14.sp)
            } else if (g == null) StyleEmpty(Modifier.weight(1f).fillMaxWidth())
            else HeroTileRows(c, rows, lightUi, Modifier.weight(1f).fillMaxWidth())
            Spacer(Modifier.height(gap * 0.6f))
            HeroBottomBar(c, g, left, dock, buttonsOn, lightUi, compact, Modifier.fillMaxWidth().height(barH))
        }
    }
}

/**
 * Update 158: the keys on the games. The games run down each column (one or two rows): ◀ ▶ a column over, ▲ ▼ between
 * the rows, ▼ from the bottom row onto the dock, ▲ from the top row to what lies above. Endless on: past the last column
 * round to the first (and back), one more step (StyleLoop tells the strip which way).
 */
internal fun heroTilesKey(ctx: StyleKeyCtx): Boolean {
    val rows = Features.heroTilesRows.value.coerceIn(1, 2)
    val count = ctx.count
    fun toBar(): Boolean = if (StyleNav.bar.isNotEmpty()) {
        ctx.enterZone(StyleNav.Zone.BAR).also {
            if (StyleNav.zone == StyleNav.Zone.BAR) StyleNav.index = HeroTilesNav.dockAt.coerceIn(0, StyleNav.bar.size - 1)
        }
    } else ctx.reach(false)
    if (count == 0) return when (ctx.key) {
        Key.DirectionDown -> toBar()
        Key.DirectionUp -> ctx.reach(true)
        else -> ctx.take()
    }
    val focus = ctx.focus.coerceIn(0, count - 1)
    val r = focus % rows
    val loop = StyleLoop.on && count >= 2
    var round = 0
    val target: Int? = when (ctx.key) {
        Key.DirectionUp -> if (r > 0) focus - 1 else null
        Key.DirectionDown -> if (r < rows - 1 && focus + 1 < count) focus + 1 else null
        Key.DirectionLeft -> when {
            focus - rows >= 0 -> focus - rows
            loop -> { round = -1; ((count - 1) / rows * rows + r).coerceAtMost(count - 1) }
            else -> null
        }
        Key.DirectionRight -> when {
            focus + rows < count -> focus + rows
            // The last column is shorter: its last game.
            (focus / rows + 1) * rows < count -> count - 1
            loop -> { round = 1; r.coerceAtMost(count - 1) }
            else -> null
        }
        else -> return false
    }
    if (target != null && target != focus) {
        if (round != 0) StyleLoop.wrapped(round)
        ctx.setFocus(target)
        ctx.sound(UiSound.MOVE)
        return ctx.take()
    }
    return when (ctx.key) {
        Key.DirectionDown -> toBar()
        Key.DirectionUp -> ctx.reach(true)
        else -> {
            if (ctx.first) EdgeBump.hit(if (ctx.key == Key.DirectionRight) 1 else -1)
            ctx.take()
        }
    }
}

/** The big picture: the game's art, its logo (or name) bottom left, the status pill top right. Tap: the game window. */
@Composable
private fun HeroPicture(c: StyleCtx, g: Game?, compact: Boolean, showStatus: Boolean, modifier: Modifier) {
    val open by rememberUpdatedState(c.actions.open)
    val shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 26.dp, bottomEnd = 26.dp)
    val fill = if (g != null) placeholderBrush(g.hue) else SolidColor(themeDark(Color(0xFF1D222B)))
    Box(modifier.clip(shape).background(fill).tapOnly { open() }) {
        val url = g?.let { c.backdrop(it) ?: c.wide(it) }
        HeldImage(url, Modifier.fillMaxSize(), imageModifier = Modifier.pixelArtwork())
        // A soft shade along the bottom, so the logo reads on any picture.
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
        if (g != null) AnimatedContent(
            targetState = g, contentKey = { it.stableKey }, transitionSpec = { gameSwap() }, label = "heroTilesTitle",
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(0.6f).padding(start = if (compact) 12.dp else 18.dp, bottom = if (compact) 10.dp else 16.dp)
        ) { shown -> HeroTitle(c, shown, compact) }
        if (showStatus) HeroStatusPill(compact, Modifier.align(Alignment.TopEnd).padding(if (compact) 8.dp else 12.dp))
    }
}

/** The game's logo (Game names › Logos), else its name; the console and time played under it. */
@Composable
private fun HeroTitle(c: StyleCtx, g: Game, compact: Boolean) {
    val titles = LocalTitleOptions.current
    // Update 159: the big logo (SteamGridDB's when there is one), never the RetroAchievements game icon — a small square
    // picture showed there instead of the logo; without a logo, the name.
    val logo = if (titles.showLogos) bigLogo(g, c.state)?.takeIf { !it.contains("retroachievements", ignoreCase = true) } else null
    Column {
        if (logo != null) AsyncImage(
            logo, titles.display(g.name), contentScale = ContentScale.Fit, alignment = Alignment.BottomStart,
            modifier = Modifier.height(if (compact) 42.dp else 64.dp).fillMaxWidth()
        ) else FitText(
            titles.display(g.name), color = Color.White, maxSize = if (compact) 20.sp else 28.sp, minSize = 12.sp,
            maxLines = 2, fontWeight = FontWeight.Bold, style = nameTextStyle(NameBackdrop.SHADOW)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            titles.console(g.platform) + " · " + stylePlaytime(c.playtime(g), c.lastPlayed(g) != null),
            color = Color.White.copy(alpha = 0.88f), fontSize = if (compact) 10.sp else 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, style = nameTextStyle(NameBackdrop.SHADOW)
        )
    }
}

/** Wi-Fi, battery and the time in a small dark pill. */
@Composable
private fun HeroStatusPill(compact: Boolean, modifier: Modifier) {
    val (time, _) = rememberConsoleClock()
    val battery = rememberBatteryInfo()
    val wifi = rememberWifiConnected()
    val icon = if (compact) 13.dp else 15.dp
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 3.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (wifi) Icons.Filled.Wifi else Icons.Filled.WifiOff, if (wifi) "Wi-Fi on" else "Wi-Fi off", tint = Color.White, modifier = Modifier.size(icon))
        Spacer(Modifier.width(6.dp))
        Icon(batteryIconFor(battery.percent, battery.charging), null, tint = Color.White, modifier = Modifier.size(icon))
        Text("${battery.percent}%", color = Color.White, fontSize = if (compact) 10.sp else 12.sp, maxLines = 1, softWrap = false)
        Spacer(Modifier.width(8.dp))
        Text(time, color = Color.White, fontSize = if (compact) 11.sp else 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

/** One or two rows of square tiles going sideways (ContinuousGrid), sized by Hero tiles › Tile size. */
@Composable
private fun HeroTileRows(c: StyleCtx, rows: Int, lightUi: Boolean, modifier: Modifier) {
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    // Update 158 (review): one tap action that always reads the latest select / open (the tiles' pointerInput keeps the
    // lambda it started with); a tap also brings the D-pad back to the games from the buttons, as on Case stacks.
    val tap: (Int) -> Unit = remember {
        { i: Int ->
            if (StyleNav.zone != StyleNav.Zone.GAMES) StyleNav.leave()
            if (i == GridHome.focus) open() else select(i)
        }
    }
    BoxWithConstraints(modifier) {
        val gap = if (maxHeight < 160.dp) 8.dp else 12.dp
        val cellH = (maxHeight - gap * (rows - 1)) / rows
        val k = (Features.heroTilesSize.state.value / 100f) * GameSize.scale(HomeLayout.HERO_TILES)
        val side = (cellH * k.coerceIn(0.4f, 1f)).coerceAtLeast(40.dp)
        val cols = ((maxWidth + gap) / (side + gap)).toInt().coerceAtLeast(2)
        ContinuousGrid(
            count = c.games.size, focus = c.focus, rows = rows, cols = cols, gap = gap,
            modifier = Modifier.fillMaxSize().categorySlide(), square = true,
            // Endless on: the strip carries on with the first columns again after the last.
            loop = loopRowOn(c), loopTick = StyleLoop.tick, loopStep = StyleLoop.step, loopList = c.games
        ) { i, m -> HeroTile(c, i, lightUi, m, tap) }
    }
}

/** One square tile in its rounded frame; the one you're on full size with the glowing ring, the others a little smaller. */
@Composable
private fun HeroTile(c: StyleCtx, i: Int, lightUi: Boolean, modifier: Modifier, tap: (Int) -> Unit) {
    val g = c.games.getOrNull(i)
    if (g == null) {
        Box(modifier)
    } else {
        val on = i == c.focus
        val sel = on && StyleNav.zone == StyleNav.Zone.GAMES
        val lift = styleLift(on).coerceIn(0f, 1f)
        val lite = LocalLiteEffects.current
        val corner = 16.dp
        val shape = RoundedCornerShape(corner)
        val frame = if (lightUi) Color.White else themeDark(Color(0xFF1D222B))
        // (Only the selected tile reads the pulse, so only it redraws while it glows; still when idle.)
        val pulse = if (sel) rememberFocusPulse(animate = !lite && LocalCardLook.current.focusEffect != com.shelf.launcher.data.FocusEffect.NONE) else 0f
        Box(
            modifier.pointerInput(i) { detectTapGestures { tap(i) } },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { val s = 0.84f + 0.16f * lift; scaleX = s; scaleY = s }
                    .then(if (sel) Modifier.styleFocus(true, corner, pulse, ShelfTheme.accent, 3.dp) else Modifier)
                    .then(if (!lite) Modifier.shadow(if (on) 8.dp else 3.dp, shape, clip = false) else Modifier)
                    .clip(shape)
                    .background(frame)
                    .then(if (!sel && !lightUi) Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), shape) else Modifier)
                    .padding(if (on) 4.dp else 3.dp)
            ) {
                StyleArt(c, g, Modifier.fillMaxSize(), square = true, corner = corner - 3.dp)
            }
        }
    }
}

/** ⓘ / ✕ bottom left, the dock in the middle, ☰ bottom right; each one a D-pad stop (StyleNav.bar). */
@Composable
private fun HeroBottomBar(c: StyleCtx, g: Game?, left: Int, dock: List<HeroDockItem>, buttonsOn: Boolean, lightUi: Boolean, compact: Boolean, modifier: Modifier) {
    val fill = if (lightUi) Color.White else themeDark(Color(0xFF1D222B))
    val ink = if (lightUi) Color(0xFF2A313B) else Color.White
    val button = if (compact) 28.dp else 34.dp
    Box(modifier) {
        if (buttonsOn && left > 0) HeroPill(fill, lightUi, Modifier.align(Alignment.CenterStart).editablePart(LayoutParts.id(HomeLayout.HERO_TILES, "buttons"), "Buttons")) {
            HeroRound(Icons.Filled.Info, "Details", null, StyleNav.on(StyleNav.Zone.BAR, 0), ink, button) { StyleNav.tapped(StyleNav.Zone.BAR, 0); StyleNav.bar.getOrNull(0)?.invoke() }
            if (left > 1 && g != null) HeroRound(Icons.Filled.Close, "Close the game", null, StyleNav.on(StyleNav.Zone.BAR, 1), ink, button) { StyleNav.tapped(StyleNav.Zone.BAR, 1); StyleNav.bar.getOrNull(1)?.invoke() }
        }
        if (dock.isNotEmpty()) HeroPill(fill, lightUi, Modifier.align(Alignment.Center).editablePart(LayoutParts.id(HomeLayout.HERO_TILES, "dock"), "App dock")) {
            dock.forEachIndexed { n, item ->
                val at = left + n
                HeroRound(item.icon, item.label, item.pkg, StyleNav.on(StyleNav.Zone.BAR, at), ink, button) { StyleNav.tapped(StyleNav.Zone.BAR, at); StyleNav.bar.getOrNull(at)?.invoke() }
            }
        }
        if (buttonsOn) {
            val at = left + dock.size
            HeroPill(fill, lightUi, Modifier.align(Alignment.CenterEnd)) {
                HeroRound(Icons.Filled.Menu, "Menu", null, StyleNav.on(StyleNav.Zone.BAR, at), ink, button) { StyleNav.tapped(StyleNav.Zone.BAR, at); StyleNav.bar.getOrNull(at)?.invoke() }
            }
        }
    }
}

@Composable
private fun HeroPill(fill: Color, lightUi: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .then(if (!LocalLiteEffects.current) Modifier.shadow(3.dp, shape, clip = false) else Modifier)
            .clip(shape).background(fill)
            .then(if (!lightUi) Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), shape) else Modifier)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) { content() }
}

/** A round button in a pill: a symbol, or an app's own icon. Selected: a ring in the theme's colour. */
@Composable
private fun HeroRound(icon: ImageVector?, label: String, pkg: String?, on: Boolean, ink: Color, size: Dp, onTap: () -> Unit) {
    val accent = ShelfTheme.accent
    Box(
        Modifier
            .graphicsLayer { val k = if (on) 1.1f else 1f; scaleX = k; scaleY = k }
            .size(size)
            .clip(CircleShape)
            .background(if (on) accent.copy(alpha = 0.18f) else Color.Transparent)
            .border(if (on) 2.dp else 0.dp, if (on) accent else Color.Transparent, CircleShape)
            .tapOnly(onTap),
        contentAlignment = Alignment.Center
    ) {
        if (pkg != null) AppIconArt(pkg, wide = false, modifier = Modifier.size(size * 0.72f).clip(CircleShape))
        else if (icon != null) Icon(icon, label, tint = if (on) accent else ink, modifier = Modifier.size(size * 0.58f))
    }
}

/**
 * The dock's buttons: All apps (your round shortcut) first, then the apps you picked (Hero tiles › Apps in the dock),
 * else Files, Browser, Store and System settings (where the device has them).
 */
@Composable
private fun rememberHeroDock(c: StyleCtx): List<HeroDockItem> {
    val context = LocalContext.current
    val shortcutsNow = rememberUpdatedState(c.shortcuts)
    val hasAllApps = c.shortcuts.any { it.label == "All apps" }
    val picked = Features.heroTilesDockApps.state.value
    val apps = c.state.installedApps
    // The system apps are looked up once, off the main thread.
    val system by produceState<List<Triple<String, ImageVector, android.content.Intent>>>(emptyList(), context) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { heroSystemApps(context) }
    }
    return remember(hasAllApps, picked, apps, system, context) {
        val out = ArrayList<HeroDockItem>()
        if (hasAllApps) out += HeroDockItem("All apps", Icons.Filled.Apps, null) {
            shortcutsNow.value.firstOrNull { it.label == "All apps" }?.onClick?.invoke()
        }
        val pkgs = picked.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val mine = pkgs.mapNotNull { p -> apps.firstOrNull { it.packageName == p } }
        if (mine.isNotEmpty()) mine.forEach { app ->
            val pkg = app.packageName.orEmpty()
            out += HeroDockItem(app.name, null, pkg) { heroLaunch(context, context.packageManager.getLaunchIntentForPackage(pkg)) }
        } else system.forEach { (label, icon, intent) ->
            out += HeroDockItem(label, icon, null) { heroLaunch(context, intent) }
        }
        out
    }
}

/** Files, Browser, Store and System settings, the ones this device can open. */
private fun heroSystemApps(context: android.content.Context): List<Triple<String, ImageVector, android.content.Intent>> {
    val pm = context.packageManager
    fun found(i: android.content.Intent): Boolean = runCatching { pm.resolveActivity(i, 0) != null }.getOrDefault(false)
    val out = ArrayList<Triple<String, ImageVector, android.content.Intent>>()
    if (android.os.Build.VERSION.SDK_INT >= 29) {
        val files = android.content.Intent.makeMainSelectorActivity(android.content.Intent.ACTION_MAIN, android.content.Intent.CATEGORY_APP_FILES)
        if (found(files)) out += Triple("Files", Icons.Filled.Folder, files)
    }
    val browser = android.content.Intent.makeMainSelectorActivity(android.content.Intent.ACTION_MAIN, android.content.Intent.CATEGORY_APP_BROWSER)
    if (found(browser)) out += Triple("Browser", Icons.Filled.Public, browser)
    val store = android.content.Intent.makeMainSelectorActivity(android.content.Intent.ACTION_MAIN, android.content.Intent.CATEGORY_APP_MARKET)
    if (found(store)) out += Triple("Store", Icons.Filled.Storefront, store)
    out += Triple("System settings", Icons.Filled.Settings, android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
    return out
}

private fun heroLaunch(context: android.content.Context, intent: android.content.Intent?) {
    val ok = intent != null && runCatching {
        context.startActivity(android.content.Intent(intent).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!ok) runCatching { android.widget.Toast.makeText(context, "That app can't be opened.", android.widget.Toast.LENGTH_SHORT).show() }
}

/** Update 158: Settings › Home › Layout options › Hero tiles options. */
@Composable
internal fun HeroTilesSettings() {
    MutedNote("The game you're on as a big picture across the top, its logo on it; one or two rows of square tiles under it, the one you're on bigger with a glowing ring. Along the bottom: ⓘ details and ✕ close (while the game is open), a dock of apps and the menu (☰). ◀ ▶ along the rows, ▲ ▼ between them, ▼ from the bottom row onto the dock.")
    Spacer(Modifier.height(10.dp))
    StyleChoice("Rows of tiles", arrayOf(1, 2), Features.heroTilesRows.state.value, { if (it == 1) "One row" else "Two rows" }) { Features.heroTilesRows.value = it }
    PercentSlider(
        "Tile size", Features.heroTilesSize.state.value, { Features.heroTilesSize.value = it.coerceIn(50, 100) },
        min = 50, max = 100, valueText = { "$it%" }
    )
    Spacer(Modifier.height(6.dp))
    SwitchRow(
        Icons.Filled.Apps, "App dock", "A pill of app shortcuts at the bottom in the middle: All apps, then your apps (or Files, Browser, Store and System settings).",
        Features.heroTilesDock.state.value, { Features.heroTilesDock.value = it }
    )
    Spacer(Modifier.height(8.dp))
    if (Features.heroTilesDock.state.value) HeroDockPicker()
    SwitchRow(
        Icons.Filled.Image, "Pale background", "A light backdrop with white tile frames. Off: your theme's background (dark themes get dark frames).",
        Features.heroTilesPale.state.value, { Features.heroTilesPale.value = it }
    )
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.Label, "Status bar", "The status bar along the top too. Off: the picture's own small pill with Wi-Fi, battery and the time.",
        Features.heroTilesStatusBar.state.value, { Features.heroTilesStatusBar.value = it }
    )
    Spacer(Modifier.height(8.dp))
}

/** Up to six apps of your own in the dock (empty: Files, Browser, Store and System settings). */
@Composable
private fun HeroDockPicker() {
    val apps by com.shelf.launcher.data.InstalledAppsHub.apps.collectAsState()
    val names = remember(apps) { apps.associate { (it.packageName ?: "") to it.name } }
    val docked = Features.heroTilesDockApps.state.value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    var picking by remember { mutableStateOf<Int?>(null) }
    fun save(list: List<String>) { Features.heroTilesDockApps.value = list.distinct().take(HERO_DOCK_MAX).joinToString(",") }
    ShelfCard {
        SectionLabel("Apps in the dock")
        MutedNote("All apps always comes first. With none picked, the dock has Files, Browser, Store and System settings. Tap an app to change or remove it.")
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            docked.forEachIndexed { i, pkg -> Chip("${i + 1}: " + (names[pkg] ?: pkg.substringAfterLast('.')), accent = true) { picking = i } }
            if (docked.size < HERO_DOCK_MAX) Chip("+ Add an app", accent = false) { picking = docked.size }
            if (docked.isNotEmpty()) Chip("Back to the defaults", accent = false) { save(emptyList()) }
        }
    }
    Spacer(Modifier.height(8.dp))
    picking?.let { slot ->
        var showAll by remember { mutableStateOf(false) }
        val current = docked.getOrNull(slot)
        ShelfDialog(
            icon = Icons.Filled.Add, title = "Dock app ${slot + 1}",
            subtitle = current?.let { names[it] ?: it } ?: "Empty",
            onDismiss = { picking = null }
        ) {
            fun set(pkg: String) {
                val list = docked.toMutableList()
                if (pkg.isEmpty()) { if (slot < list.size) list.removeAt(slot) }
                else if (slot < list.size) list[slot] = pkg
                else list += pkg
                save(list)
                picking = null
            }
            if (current != null) RadioLine("Remove from the dock", selected = false) { set("") }
            val shown = if (showAll) apps else apps.take(30)
            shown.forEach { app -> RadioLine(app.name, selected = current == app.packageName) { set(app.packageName.orEmpty()) } }
            if (!showAll && apps.size > shown.size) {
                Spacer(Modifier.height(6.dp))
                Chip("Show all ${apps.size} apps", accent = false) { showAll = true }
            }
        }
    }
}

/** Most apps of your own the Hero tiles dock holds. */
private const val HERO_DOCK_MAX = 6
