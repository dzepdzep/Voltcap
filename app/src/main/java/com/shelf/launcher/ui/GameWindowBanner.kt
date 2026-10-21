package com.shelf.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.GameArt
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.PlaytimeStore
import com.shelf.launcher.data.RaAchievementDetail
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Update 146: the banner page — the game window drawn as a store page, the same on every layout (Settings › Game
 * window › Style). The game's wide picture fills the top edge to edge, its logo (or its name) at the bottom left and its
 * console in the top right corner; under it a strip in the game's colours with a big Play / Continue (where the D-pad
 * starts), when you last played and for how long, and round buttons for the game's options, achievements and save
 * sync; then a short status line and tabs (L1 / R1 switch them from anywhere): Activity, Achievements, Media, Game info
 * (and Options when the game has its own list). The tabs' content scrolls; going down into it folds the picture to a
 * band so small screens get room, going back up to Play opens it again.
 */

/** Update 146: the banner page's tabs, left to right. OPTIONS only when the game's options come as a list. */
private enum class BannerTab(val label: String) {
    ACTIVITY("ACTIVITY"), ACHIEVEMENTS("ACHIEVEMENTS"), MEDIA("MEDIA"), INFO("GAME INFO"), PERFORMANCE("PERFORMANCE"), OPTIONS("OPTIONS")
}

/** Update 146: the banner page's colours, worked out once from the game's colour (or the theme's). */
private class BannerLook(
    /** Behind the tabs and their content. */
    val page: Color,
    /** The strip under the picture (Play, last played, play time, the round buttons). */
    val strip: Color,
    val ink: Color,
    val muted: Color,
    /** The light version of the colour: the focus ring, the tab marker, progress bars. */
    val glow: Color,
    val card: Color,
    val cardOn: Color,
    val btn: Color,
    val btnInk: Color,
    val btnOn: Color,
    val btnOnInk: Color
)

private fun bannerMix(a: Color, b: Color, t: Float) =
    Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

/**
 * The colours: from [tint] (the game's cover colour or the theme's accent) — a deep page, a richer strip, white words —
 * or, with "The layout's own" colours, the theme's own surfaces and accent (so a light theme stays light).
 */
@Composable
private fun bannerLook(colors: Features.WinColors, tint: Color): BannerLook {
    val t = ShelfTheme.colors
    val acc = ShelfTheme.accent
    return if (colors == Features.WinColors.LAYOUT) {
        // (Dark words on a light accent, as the other windows do — never white on white.)
        val onAcc = if (acc.luminance() > 0.5f) Color(0xFF111111) else Color.White
        BannerLook(
            page = t.background, strip = t.surfaceRaised, ink = t.textPrimary, muted = ShelfTheme.textMuted, glow = acc,
            card = t.surface, cardOn = bannerMix(t.surface, acc, 0.22f),
            btn = t.surface, btnInk = t.textPrimary, btnOn = acc, btnOnInk = onAcc
        )
    } else {
        val deep = bannerMix(tint, Color.Black, 0.55f)
        val glow = bannerMix(tint, Color.White, 0.55f)
        BannerLook(
            page = bannerMix(tint, Color.Black, 0.8f), strip = bannerMix(tint, Color.Black, 0.3f),
            ink = Color.White, muted = Color.White.copy(alpha = 0.72f), glow = glow,
            card = Color.White.copy(alpha = 0.06f), cardOn = Color.White.copy(alpha = 0.15f),
            btn = Color.White.copy(alpha = 0.14f), btnInk = Color.White, btnOn = glow, btnOnInk = deep
        )
    }
}

private val BannerGrey = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** One picture on the Media tab: its address and its width ÷ height. */
private class BannerMedia(val url: String, val aspect: Float, val shot: Boolean)

@Composable
internal fun BannerGameWindow(
    layout: HomeLayout,
    g: Game,
    state: LauncherUiState,
    art: Map<String, GameArt>,
    onClose: () -> Unit,
    onPlay: () -> Unit,
    onFullDetails: () -> Unit,
    onNeedMedia: (Game) -> Unit,
    onOptions: () -> Unit,
    onAchievements: () -> Unit,
    onSync: () -> Unit,
    options: List<WinOption>,
    onLoadComments: (Int) -> Unit,
    /** Update 213: the game's performance (null: no tuner here) and save sync (GamePopupStatus.kt). */
    perf: GamePerfState? = null,
    sync: GameSyncView? = null,
    hasFan: Boolean? = null,
    onPerfAdvanced: () -> Unit = {},
    onSyncSetUp: () -> Unit = {},
    /** Update 285: the game window with the game's piece out (null: an app — no Inspect button). */
    onInspect: (() -> Unit)? = null
) {
    val k = g.stableKey
    val o = state.artOverrides[k]
    val a = art[k]
    val cover = o?.cover ?: a?.gridUrl
    val wide = o?.wide ?: a?.wideGridUrl ?: o?.hero ?: a?.heroUrl ?: cover
    val backdrop = com.shelf.launcher.data.ArtPick.backdrop(g, state.artOverrides, art, state.screenshots) ?: wide
    val resumed = state.lastPlayedAt[k] != null
    // The game's colours (its cover's), the theme's, or the theme's own surfaces — as the other game windows.
    val colors = Features.gameWindowColors.state.value
    val coverBmp = rememberSoftBitmap(if (colors == Features.WinColors.GAME) cover else null, 64)
    val gameTint = rememberArtColor(coverBmp, ShelfTheme.accent)
    val rawTint = if (colors == Features.WinColors.GAME && coverBmp != null) gameTint else ShelfTheme.accent
    // (The cover's colour arrives a moment after opening: the page eases into it instead of jumping.)
    val tint by animateColorAsState(rawTint, tween(420), label = "bannerTint")
    val look = bannerLook(colors, tint)
    val anim = MotionPrefs.info() != Features.InfoAnim.NONE
    val glide = Features.rowGlide.state.value
    val titles = LocalTitleOptions.current
    val context = LocalContext.current

    // What the tabs show, gathered once here (the tabs' lists are built from plain values).
    val playMs = state.playtime[k]
    val lastAt = state.lastPlayedAt[k]
    val sessions by produceState(0 to emptyList<PlaytimeStore.Session>(), k, playMs) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val mine = PlaytimeStore(context).loadSessions().filter { it.key == k }
                mine.size to mine.sortedByDescending { it.start }.take(8)
            }.getOrDefault(0 to emptyList())
        }
    }
    val trophies = state.raAchievementDetails[k].orEmpty()
    val prog = state.raProgress[k]
    val total = if (trophies.isNotEmpty()) trophies.size else prog?.NumPossibleAchievements ?: 0
    val earned = if (trophies.isNotEmpty()) trophies.count { it.isUnlocked } else prog?.NumAchieved ?: 0
    // Earned ones first (the latest first), then the rest in the set's own order.
    val sortedTrophies = remember(trophies) {
        val set = trophies.sortedWith(com.shelf.launcher.data.RA_DISPLAY_ORDER)
        set.filter { it.isUnlocked }.sortedByDescending { it.dateEarnedHardcore ?: it.dateEarned ?: "" } + set.filter { !it.isUnlocked }
    }
    val trophiesLoading = k in state.raAchievementDetailsLoading
    val meta = state.metadata[k]
    val shots = state.screenshots[k].orEmpty()
    val media = remember(shots, backdrop, wide, cover, o?.hero, a?.heroUrl) {
        val s = shots.distinct().map { BannerMedia(it, 1.78f, true) }
        val artOnes = listOfNotNull(o?.hero ?: a?.heroUrl, backdrop, wide).distinct().filter { u -> u != cover && shots.none { it == u } }
            .map { BannerMedia(it, 1.78f, false) } + listOfNotNull(cover).filter { u -> shots.none { it == u } }.map { BannerMedia(it, 0.72f, false) }
        s + artOnes
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        ImmersiveDialogWindow()
        val scope = rememberCoroutineScope()
        val playFocus = remember { FocusRequester() }
        // (Update 233, #120: opened with a button after a touch — leave Android's touch mode first, so Play is lit at once
        // and the first ◀ / ▶ moves; it only lit Play, and A, ▶, A started the game.)
        val keyView = androidx.compose.ui.platform.LocalView.current
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(20); KeyInput.leaveTouchMode(keyView) }
        // Always opens on Play / Continue: the window can take a moment to get the focus (from a touch, or picked on
        // the second screen), so ask again until something in it has it (as the other game windows do).
        var focusInside by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            for ((n, wait) in longArrayOf(60, 140, 250, 400, 700).withIndex()) {
                delay(wait)
                if (focusInside && n >= 3) break
                runCatching { playFocus.requestFocus() }
            }
        }
        val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
        var askedOnFocus by remember { mutableStateOf(false) }
        LaunchedEffect(windowInfo.isWindowFocused) {
            if (windowInfo.isWindowFocused && !askedOnFocus) {
                askedOnFocus = true
                KeyInput.leaveTouchMode(keyView) // (Update 233, #120)
                delay(30)
                runCatching { playFocus.requestFocus() }
            }
        }
        LaunchedEffect(k) { onNeedMedia(g) }

        // (Update 213: the Performance tab where the handheld can be tuned.)
        val perfOn = perf != null
        val tabs = remember(options.isNotEmpty(), perfOn) {
            BannerTab.values().filter { (it != BannerTab.OPTIONS || options.isNotEmpty()) && (it != BannerTab.PERFORMANCE || perfOn) }
        }
        var tab by remember(k) { mutableStateOf(BannerTab.ACTIVITY) }
        // (The options list can go away while the page is open: then back to the first tab.)
        LaunchedEffect(tabs) { if (tab !in tabs) tab = BannerTab.ACTIVITY }
        // Which way the content slides in when the tab changes (+1: from the right).
        var tabDir by remember { mutableStateOf(1) }
        val tabFocus = remember { BannerTab.values().associateWith { FocusRequester() } }
        val contentFocus = remember { FocusRequester() }
        // Each tab keeps its own place in its list (back on a tab, you're where you were).
        val listStates = remember(k) { BannerTab.values().associateWith { LazyListState() } }
        val mediaRow = remember(k) { LazyListState() }
        val thumbFocus = remember(media) { List(media.size) { FocusRequester() } }
        var stripHas by remember { mutableStateOf(false) }
        var contentHas by remember { mutableStateOf(false) }
        var viewer by remember(k) { mutableStateOf<Int?>(null) }
        var openTrophy by remember(k) { mutableStateOf<Int?>(null) }
        var storyOpen by remember(k) { mutableStateOf(false) }

        fun setTab(t: BannerTab) {
            if (t == tab) return
            tabDir = if (tabs.indexOf(t) >= tabs.indexOf(tab)) 1 else -1
            tab = t
        }
        // The first thing in the tab's content gets the D-pad (scrolled back to the top first, so it's there).
        fun focusContent() {
            scope.launch {
                delay(60)
                val ls = listStates.getValue(tab)
                if (ls.firstVisibleItemIndex > 0 || ls.firstVisibleItemScrollOffset > 0) { ls.scrollToItem(0); delay(40) }
                if (tab == BannerTab.MEDIA && mediaRow.firstVisibleItemIndex > 0) { mediaRow.scrollToItem(0); delay(40) }
                runCatching { contentFocus.requestFocus() }
            }
        }
        // An achievement picked under Activity › Latest unlocked: the Achievements tab with it opened, scrolled to and
        // with the D-pad on it (not on the summary at the top, which could leave the opened one off screen).
        val trophyFocus = remember { FocusRequester() }
        fun jumpToTrophy(id: Int) {
            openTrophy = id
            onLoadComments(id)
            setTab(BannerTab.ACHIEVEMENTS)
            // (Its place in the list: the summary card comes first, then the achievements in [sortedTrophies]' order.)
            val at = sortedTrophies.indexOfFirst { it.id == id }
            if (at < 0) focusContent()
            else scope.launch {
                delay(60)
                runCatching { listStates.getValue(BannerTab.ACHIEVEMENTS).scrollToItem(at + 1) }
                delay(40)
                // (Back to the top of the list if it couldn't be reached.)
                val got = runCatching { trophyFocus.requestFocus(androidx.compose.ui.focus.FocusDirection.Enter) }.getOrDefault(false)
                if (!got) runCatching { contentFocus.requestFocus() }
            }
        }
        // L1 / R1: the tab before / after (round the ends). The D-pad's place follows to the new tab's name, unless it
        // was on the strip (then it stays on Play / the round button it was on).
        fun stepTab(step: Int) {
            val i = tabs.indexOf(tab).coerceAtLeast(0)
            val n = tabs[(i + step + tabs.size) % tabs.size]
            tabDir = if (step > 0) 1 else -1
            tab = n
            if (!stripHas) scope.launch { delay(30); runCatching { tabFocus.getValue(n).requestFocus() } }
        }

        val sounds = LocalUiSounds.current
        // Opening: a short fade and grow (just appears with Settings › Animations › info set to none).
        val open = remember { Animatable(if (anim) 0f else 1f) }
        LaunchedEffect(Unit) { if (anim) open.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
        // The tab's content glides in from the side it came from.
        val tabIn = remember { Animatable(1f) }
        var firstTab by remember { mutableStateOf(true) }
        LaunchedEffect(tab) {
            if (firstTab) firstTab = false
            else if (anim) {
                tabIn.snapTo(0f)
                tabIn.animateTo(1f, tween(glide.carouselMs, easing = FastOutSlowInEasing))
            }
        }

        BoxWithConstraints(
            Modifier.fillMaxSize()
                .graphicsLayer { val v = open.value; alpha = v; val s = 0.95f + 0.05f * v; scaleX = s; scaleY = s }
                .background(look.page)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) false
                    else when (e.key) {
                        // (Not while a picture is open full size: there they'd change the tab behind it.)
                        Key.ButtonL1 -> { if (viewer == null) { sounds?.play(UiSound.MOVE); stepTab(-1) }; true }
                        Key.ButtonR1 -> { if (viewer == null) { sounds?.play(UiSound.MOVE); stepTab(1) }; true }
                        else -> false
                    }
                }
                .onFocusChanged { if (it.hasFocus) focusInside = true }
        ) {
            val narrow = maxWidth < 600.dp
            val short = maxHeight < 440.dp
            // About 40 % of the screen for the picture; a third on a short screen; folded to a band while you're in the
            // tab's content, so the list gets the room.
            val heroFull = (maxHeight * (if (short) 0.32f else 0.4f)).coerceIn(96.dp, 460.dp)
            val heroSmall = (heroFull * 0.42f).coerceAtLeast(60.dp)
            val heroSpec: androidx.compose.animation.core.AnimationSpec<Dp> =
                if (anim) tween(300, easing = FastOutSlowInEasing) else snap()
            // (Kept folded while a picture from the Media tab is open over it, so it doesn't open and fold again behind it.)
            // (Read as a State and only while laying out the picture: folding doesn't rebuild the whole page each frame.)
            val heroH = animateDpAsState(if (contentHas || viewer != null) heroSmall else heroFull, heroSpec, label = "bannerHero")
            val pad = if (narrow) 14.dp else 24.dp
            val downToTab = tabFocus.getValue(tab)
            Column(Modifier.fillMaxSize()) {
                BannerHero(look, g, state, backdrop, { heroH.value }, pad, narrow, onClose)
                // ---- the strip: Play, last played, play time, the round buttons ----
                Column(Modifier.fillMaxWidth().background(look.strip)) {
                    Row(
                        Modifier.fillMaxWidth()
                            .onFocusChanged { stripHas = it.hasFocus }
                            .padding(horizontal = pad, vertical = if (narrow || short) 8.dp else 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BannerPlay(
                            look, if (resumed) "Continue" else "Play", narrow || short,
                            Modifier.focusRequester(playFocus).focusProperties { down = downToTab },
                            onPlay
                        )
                        Spacer(Modifier.width(if (narrow) 12.dp else 24.dp))
                        BannerStat(look, "LAST PLAYED", lastAt?.let { lastPlayedLabel(it) } ?: "Never", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        BannerStat(look, "PLAY TIME", stylePlaytime(playMs, played = lastAt != null), Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        val round = if (narrow || short) 38.dp else 46.dp
                        val roundMod = Modifier.focusProperties { down = downToTab }
                        Row(horizontalArrangement = Arrangement.spacedBy(if (narrow) 6.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            BannerRound(look, Icons.Filled.Settings, "Game options", round, roundMod) {
                                if (options.isNotEmpty()) { setTab(BannerTab.OPTIONS); focusContent() } else onOptions()
                            }
                            BannerRound(look, Icons.Filled.EmojiEvents, "Achievements", round, roundMod) {
                                if (trophies.isNotEmpty()) { setTab(BannerTab.ACHIEVEMENTS); focusContent() } else onAchievements()
                            }
                            BannerRound(look, Icons.Filled.CloudSync, "Save sync", round, roundMod, onSync)
                            // (Update 285: Inspect — half a case, half a cartridge.)
                            if (onInspect != null) BannerRound(look, InspectIcon.vector, "Inspect", round, roundMod, onInspect)
                            // Update 213: Performance — its tab, one press away.
                            if (perfOn) BannerRound(look, Icons.Filled.Speed, "Performance", round, roundMod) {
                                setTab(BannerTab.PERFORMANCE); focusContent()
                            }
                        }
                    }
                    // ---- a thin line and the short status ----
                    Box(Modifier.fillMaxWidth().height(1.dp).background(look.ink.copy(alpha = 0.12f)))
                    Row(Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (total > 0) {
                            Icon(Icons.Filled.EmojiEvents, null, tint = look.glow, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "$earned / $total achievements" + if (earned == total) "  ·  Mastered" else "",
                                color = look.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                            )
                            // Update 151: a small upright tick between "0 / 69 achievements" and its bar.
                            ProgressTick(height = 10.dp, gap = 6.dp, color = look.ink.copy(alpha = 0.35f))
                            BannerBar(earned.toFloat() / total, look, Modifier.width(if (narrow) 50.dp else 90.dp))
                        } else Text(
                            if (lastAt == null) "Not played yet" else "Time played: " + stylePlaytime(playMs, played = true),
                            color = look.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                        )
                        Spacer(Modifier.weight(1f))
                        // Update 213: the game's save sync, always in sight (a tap opens it; stronger only when it needs you).
                        if (sync != null) {
                            BannerSyncChip(look, sync, onClick = if (sync.inert) ({}) else if (sync.setUp) onSyncSetUp else onSync, modifier = Modifier.padding(start = 10.dp))
                        } else Text(
                            listOfNotNull(titles.console(g.platform), meta?.releaseYear?.toString()).joinToString("  ·  "),
                            color = look.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
                // ---- the tabs, with the L1 / R1 hints at the ends ----
                val tabPos = remember { mutableStateMapOf<BannerTab, Pair<Float, Float>>() }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = pad, vertical = if (short) 4.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BannerHint(look, "L1") { sounds?.play(UiSound.MOVE); stepTab(-1) }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        // (Update 294: whole tabs only, "+N ›" for the rest — WholeChipRow; the marker is placed from
                        // each tab's place in the row, read against the row's own.)
                        val rowX = remember { floatArrayOf(0f) }
                        Box(Modifier.fillMaxWidth().onGloballyPositioned { rowX[0] = it.positionInRoot().x }) {
                            WholeChipRow(tabs.size, tabs.indexOf(tab), Modifier.fillMaxWidth(), spacing = 4.dp, hintColor = look.muted) { ti ->
                                    val t = tabs[ti]
                                    BannerTabChip(
                                        look, t.label, selected = t == tab,
                                        modifier = Modifier
                                            .onGloballyPositioned { c ->
                                                val v = (c.positionInRoot().x - rowX[0]) to c.size.width.toFloat()
                                                if (tabPos[t] != v) tabPos[t] = v
                                            }
                                            .focusRequester(tabFocus.getValue(t))
                                            .focusProperties { up = playFocus }
                                            // Moving onto a tab shows it, as a console's store page does.
                                            .onFocusChanged { if (it.isFocused) setTab(t) },
                                        onClick = { setTab(t) }
                                    )
                            }
                            // The marker under the shown tab glides to the next one (drawn only: no new layout).
                            val target = tabPos[tab]
                            val mx by animateFloatAsState(target?.first ?: 0f, spring(stiffness = glide.stiffness), label = "bannerTabX")
                            val mw by animateFloatAsState(target?.second ?: 0f, spring(stiffness = glide.stiffness), label = "bannerTabW")
                            val markColor = look.glow
                            Spacer(
                                Modifier.matchParentSize().drawBehind {
                                    if (mw > 0f && mx >= -1f && mx + mw <= size.width + 1f) {
                                        val h = 3.dp.toPx()
                                        drawRoundRect(
                                            markColor, topLeft = Offset(mx + mw * 0.18f, size.height - h),
                                            size = Size(mw * 0.64f, h), cornerRadius = CornerRadius(h / 2f)
                                        )
                                    }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    BannerHint(look, "R1") { sounds?.play(UiSound.MOVE); stepTab(1) }
                }
                // ---- the tab's content (scrolls) ----
                val firstUp = Modifier.focusProperties { up = downToTab }
                val firstMod = Modifier.focusRequester(contentFocus).then(firstUp)
                Box(
                    Modifier.fillMaxWidth().weight(1f)
                        .onFocusChanged { contentHas = it.hasFocus }
                        .graphicsLayer {
                            val v = tabIn.value
                            alpha = 0.25f + 0.75f * v
                            translationX = (1f - v) * tabDir * 40.dp.toPx()
                        }
                ) {
                    key(tab) {
                        LazyColumn(
                            state = listStates.getValue(tab),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = pad, end = pad, top = 6.dp, bottom = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (tab) {
                                BannerTab.ACTIVITY -> {
                                    item(key = "overview") {
                                        BannerCard(look, firstMod) {
                                            val pct = if (total > 0) "${earned * 100 / total} %" else "None"
                                            val cells = listOf(
                                                "LAST PLAYED" to (lastAt?.let { lastPlayedLabel(it) } ?: "Never"),
                                                "PLAY TIME" to stylePlaytime(playMs, played = lastAt != null),
                                                "SESSIONS" to sessions.first.toString(),
                                                "ACHIEVEMENTS" to pct
                                            )
                                            cells.chunked(if (narrow) 2 else 4).forEachIndexed { r, row ->
                                                if (r > 0) Spacer(Modifier.height(8.dp))
                                                Row(Modifier.fillMaxWidth()) {
                                                    row.forEach { (l, v) -> BannerStat(look, l, v, Modifier.weight(1f)) }
                                                }
                                            }
                                            if (lastAt == null) {
                                                Spacer(Modifier.height(8.dp))
                                                Text("Not played yet. Press Play to start your first session.", color = look.muted, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                    val recent = sessions.second
                                    if (recent.isNotEmpty()) {
                                        item(key = "sessionsHead") { BannerHead(look, "RECENT SESSIONS") }
                                        val longest = recent.maxOf { it.ms }.coerceAtLeast(1L)
                                        // (Keyed by place: two saved sessions with the same start would crash a list keyed by it.)
                                        itemsIndexed(recent, key = { i, _ -> "s$i" }) { _, s ->
                                            BannerCard(look) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        bannerDate(s.start), color = look.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1, modifier = Modifier.weight(1f)
                                                    )
                                                    BannerBar(s.ms.toFloat() / longest, look, Modifier.width(if (narrow) 60.dp else 120.dp))
                                                    Spacer(Modifier.width(10.dp))
                                                    Text(
                                                        PlaytimeStore.format(s.ms), color = look.muted, fontSize = 12.sp, maxLines = 1,
                                                        modifier = Modifier.widthIn(min = 56.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    val latest = sortedTrophies.filter { it.isUnlocked }.take(4)
                                    if (latest.isNotEmpty()) {
                                        item(key = "latestHead") { BannerHead(look, "LATEST UNLOCKED") }
                                        items(latest, key = { "t" + it.id }) { t ->
                                            BannerCard(look, onClick = { jumpToTrophy(t.id) }) { BannerTrophyLine(look, t, opened = false) }
                                        }
                                    }
                                }
                                BannerTab.ACHIEVEMENTS -> {
                                    item(key = "trophySum") {
                                        // The summary; A / a tap opens the big achievement list.
                                        BannerCard(look, firstMod, onClick = onAchievements) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.EmojiEvents, null, tint = look.glow, modifier = Modifier.size(22.dp))
                                                Spacer(Modifier.width(10.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        if (total > 0) "$earned / $total achievements" else "Achievements",
                                                        color = look.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1
                                                    )
                                                    val pts = trophies.filter { it.isUnlocked }.sumOf { it.points ?: 0 }
                                                    val allPts = trophies.sumOf { it.points ?: 0 }
                                                    if (allPts > 0) Text("$pts / $allPts points", color = look.muted, fontSize = 12.sp, maxLines = 1)
                                                }
                                                BannerPill(look, "Full list")
                                            }
                                            if (total > 0) {
                                                Spacer(Modifier.height(8.dp))
                                                BannerBar(earned.toFloat() / total, look, Modifier.fillMaxWidth())
                                            }
                                        }
                                    }
                                    if (sortedTrophies.isEmpty()) item(key = "trophyNone") {
                                        BannerCard(look) {
                                            Text(
                                                when {
                                                    trophiesLoading -> "Loading achievements…"
                                                    total > 0 -> "Open the full list to see them all."
                                                    else -> "No achievements found for this game."
                                                },
                                                color = look.muted, fontSize = 13.sp
                                            )
                                        }
                                    }
                                    items(sortedTrophies, key = { "a" + it.id }) { t ->
                                        val opened = openTrophy == t.id
                                        BannerCard(look, if (opened) Modifier.focusRequester(trophyFocus) else Modifier, onClick = {
                                            openTrophy = if (opened) null else t.id
                                            if (!opened) onLoadComments(t.id)
                                        }) {
                                            BannerTrophyLine(look, t, opened)
                                            if (opened) BannerTrophyMore(look, t, state.raComments[t.id], t.id in state.raCommentsLoading)
                                        }
                                    }
                                }
                                BannerTab.MEDIA -> {
                                    if (media.isEmpty()) item(key = "mediaNone") {
                                        BannerCard(look, firstMod) {
                                            Text("No screenshots or artwork for this game yet.", color = look.muted, fontSize = 13.sp)
                                        }
                                    } else {
                                        val thumbH = if (narrow || short) 104.dp else 140.dp
                                        val shotIdx = media.indices.filter { media[it].shot }
                                        val artIdx = media.indices.filter { !media[it].shot }
                                        val groups = listOfNotNull(
                                            shotIdx.takeIf { it.isNotEmpty() }?.let { "SCREENSHOTS" to it },
                                            artIdx.takeIf { it.isNotEmpty() }?.let { "ARTWORK" to it }
                                        )
                                        groups.forEachIndexed { gi, (head, idx) ->
                                            item(key = "media$gi") {
                                                Column {
                                                    BannerHead(look, head)
                                                    Spacer(Modifier.height(6.dp))
                                                    LazyRow(
                                                        state = if (gi == 0) mediaRow else androidx.compose.foundation.lazy.rememberLazyListState(),
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)
                                                    ) {
                                                        itemsIndexed(idx, key = { _, i -> media[i].url }) { n, i ->
                                                            val m = media[i]
                                                            BannerThumb(
                                                                look, m.url, m.aspect, thumbH,
                                                                Modifier.focusRequester(thumbFocus[i])
                                                                    .then(if (gi == 0) firstUp else Modifier)
                                                                    .then(if (gi == 0 && n == 0) Modifier.focusRequester(contentFocus) else Modifier)
                                                            ) { viewer = i }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                BannerTab.INFO -> {
                                    item(key = "story") {
                                        val summary = meta?.summary?.takeIf { it.isNotBlank() }
                                        // A / a tap shows the whole story (it starts at a few lines).
                                        BannerCard(look, firstMod, onClick = if (summary != null) ({ storyOpen = !storyOpen }) else null) {
                                            Text(titles.display(g.name), color = look.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                summary ?: "No description yet.", color = look.ink.copy(alpha = 0.88f), fontSize = 13.sp, lineHeight = 19.sp,
                                                maxLines = if (storyOpen) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis
                                            )
                                            if (summary != null && summary.length > 260) {
                                                Spacer(Modifier.height(6.dp))
                                                BannerPill(look, if (storyOpen) "Less" else "More")
                                            }
                                        }
                                    }
                                    val facts = bannerFacts(meta, titles.console(g.platform))
                                    if (facts.isNotEmpty()) item(key = "facts") {
                                        BannerCard(look) {
                                            facts.chunked(if (narrow) 2 else 3).forEachIndexed { r, row ->
                                                if (r > 0) Spacer(Modifier.height(10.dp))
                                                Row(Modifier.fillMaxWidth()) {
                                                    row.forEach { (l, v) -> BannerStat(look, l, v, Modifier.weight(1f), lines = 2) }
                                                    repeat((if (narrow) 2 else 3) - row.size) { Spacer(Modifier.weight(1f)) }
                                                }
                                            }
                                        }
                                    }
                                    item(key = "full") {
                                        BannerCard(look, onClick = onFullDetails) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Full details", color = look.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                                Text("Everything about this game", color = look.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                                BannerTab.PERFORMANCE -> if (perf != null) {
                                    item(key = "perfHead") {
                                        BannerCard(look, firstMod) {
                                            Text("FOR THIS GAME", color = look.muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
                                            Text(perf.perf.summary, color = look.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                            Spacer(Modifier.height(4.dp))
                                            Text(perfNote(perf), color = look.muted, fontSize = 12.sp)
                                        }
                                    }
                                    val rows = perfRows(perf, hasFan, onPerfAdvanced)
                                    itemsIndexed(rows, key = { i, r -> "p$i${r.label}" }) { _, r ->
                                        BannerCard(look, onClick = r.onClick) {
                                            Text(
                                                r.label, color = if (r.destructive) Color(0xFFFF8A80) else look.ink, fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                                            )
                                            Text(r.value, color = look.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                                BannerTab.OPTIONS -> {
                                    itemsIndexed(options, key = { i, _ -> "o$i" }) { i, opt ->
                                        BannerCard(look, if (i == 0) firstMod else Modifier, onClick = opt.onClick) {
                                            Text(
                                                opt.label, color = if (opt.destructive) Color(0xFFFF8A80) else look.ink, fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                                            )
                                            opt.sub?.let { Text(it, color = look.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // A picture from the Media tab, full size over everything.
            val v = viewer
            if (v != null && v in media.indices) BannerViewer(
                media.map { it.url }, v, onIndex = { viewer = it },
                onClose = {
                    val back = viewer
                    viewer = null
                    scope.launch {
                        delay(40)
                        back?.let { i -> thumbFocus.getOrNull(i)?.let { runCatching { it.requestFocus() } } }
                    }
                }
            )
        }
    }
}

/** "Mon 3 Oct · 14:20" (in the phone's language). */
private fun bannerDate(at: Long): String =
    runCatching { java.text.SimpleDateFormat("EEE d MMM · HH:mm", java.util.Locale.getDefault()).format(java.util.Date(at)) }.getOrDefault("")

/** The Game info tab's facts, label over value. */
private fun bannerFacts(meta: com.shelf.launcher.data.GameMetadata?, console: String): List<Pair<String, String>> {
    fun hrs(d: Double?): String? = d?.takeIf { it > 0.0 }?.let { if (it >= 10.0) "${Math.round(it)} h" else "%.1f h".format(it) }
    val beat = listOfNotNull(
        hrs(meta?.hastilyHours)?.let { "Story $it" }, hrs(meta?.normallyHours)?.let { "Extras $it" },
        hrs(meta?.completelyHours)?.let { "All $it" }
    ).joinToString(" · ")
    return listOfNotNull(
        "CONSOLE" to console,
        meta?.releaseYear?.let { "RELEASED" to it.toString() },
        meta?.developer?.takeIf { it.isNotBlank() }?.let { "DEVELOPER" to it },
        meta?.publisher?.takeIf { it.isNotBlank() }?.let { "PUBLISHER" to it },
        meta?.genres?.takeIf { it.isNotEmpty() }?.let { "GENRES" to it.take(3).joinToString(", ") },
        meta?.criticScore?.let { "CRITICS" to "$it / 100" + (meta?.criticCount?.let { n -> " ($n)" } ?: "") },
        meta?.metacritic?.let { "METACRITIC" to it.toString() },
        beat.takeIf { it.isNotBlank() }?.let { "TIME TO BEAT" to it }
    )
}

/**
 * The picture across the top: the game's wide art (its preview video once found), fading into the strip; its logo — or
 * its name in big type — at the bottom left; its console in the top right; a close button for touch in the top left.
 */
@Composable
private fun BannerHero(
    look: BannerLook, g: Game, state: LauncherUiState, backdrop: String?, heightOf: () -> Dp, pad: Dp, narrow: Boolean, onClose: () -> Unit
) {
    val titles = LocalTitleOptions.current
    val logo = bigLogo(g, state)
    var logoFailed by remember(logo) { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth()
            // (The height is read here, while laying out, so its fold animation only re-lays out the picture.)
            .layout { m, c ->
                val h = heightOf().roundToPx().coerceIn(c.minHeight, c.maxHeight)
                val p = m.measure(c.copy(minHeight = h, maxHeight = h))
                layout(p.width, h) { p.place(0, 0) }
            }
            .clipToBounds().background(placeholderBrush(g.hue))
    ) {
        if (backdrop != null) AsyncImage(backdrop, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork())
        val video = state.videoUrls[g.stableKey]
        if (video != null) key(video) { VideoBackdrop(video, state.appearance.videoSound, Modifier.fillMaxSize()) }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.3f), 0.3f to Color.Transparent,
                    0.62f to look.strip.copy(alpha = 0.35f), 1f to look.strip
                )
            )
        )
        Box(
            Modifier.align(Alignment.BottomStart).padding(start = pad, end = pad, bottom = 8.dp)
                .fillMaxWidth(if (narrow) 0.72f else 0.46f).fillMaxHeight(0.46f),
            contentAlignment = Alignment.BottomStart
        ) {
            if (logo != null && !logoFailed) AsyncImage(
                logo, titles.display(g.name), contentScale = ContentScale.Fit, alignment = Alignment.BottomStart,
                onError = { logoFailed = true }, modifier = Modifier.fillMaxSize()
            ) else Text(
                titles.display(g.name), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2,
                overflow = TextOverflow.Ellipsis, lineHeight = 34.sp,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = 32.sp, stepSize = 1.sp),
                style = nameTextStyle(com.shelf.launcher.data.NameBackdrop.SHADOW)
            )
        }
        BannerConsoleChip(g, Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = pad))
        // (Touch only: B closes with the controller.)
        Box(
            Modifier.align(Alignment.TopStart).padding(top = 12.dp, start = pad).size(34.dp)
                .clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                .focusProperties { canFocus = false }
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Close, "Close", tint = Color.White, modifier = Modifier.size(18.dp)) }
    }
}

/** The console in the picture's corner: its symbol and name, its logo, or its name, on a dark glass pill. */
@Composable
private fun BannerConsoleChip(g: Game, modifier: Modifier) {
    val titles = LocalTitleOptions.current
    val context = LocalContext.current
    val logo = BadgeLogos.of(g.platform)
    val symbol = logo?.first?.endsWith(".webp") == true
    val shape = RoundedCornerShape(50)
    Row(
        modifier.height(28.dp).clip(shape).background(Color.Black.copy(alpha = 0.5f)).border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (logo != null) {
            val req = remember(logo.first) {
                coil.request.ImageRequest.Builder(context).data(logo.first)
                    .apply { if (!symbol) decoderFactory(coil.decode.SvgDecoder.Factory()) }.size(256).build()
            }
            val h = if (symbol) 16.dp else 13.dp
            AsyncImage(
                req, g.platform, contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.height(h).width(h * logo.second.coerceIn(0.6f, 8f))
            )
        }
        // (A wordmark already says the name; a symbol gets it beside it.)
        if (logo == null || symbol) {
            if (logo != null) Spacer(Modifier.width(8.dp))
            Text(titles.console(g.platform).uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, letterSpacing = 1.sp)
        }
    }
}

/** Play / Continue: big and round-ended; in the game's light colour (and its focus ring) while the D-pad is on it. */
@Composable
private fun BannerPlay(look: BannerLook, label: String, compact: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    LaunchedEffect(focused) { if (focused) sounds?.play(UiSound.MOVE) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, spring(stiffness = Features.rowGlide.state.value.stiffness), label = "bannerPlay")
    val h = if (compact) 44.dp else 54.dp
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .styleFocus(focused, h / 2, 0.7f, look.glow)
            .clip(RoundedCornerShape(50))
            .background(if (focused) look.btnOn else look.btn)
            .clickable(interactionSource = source, indication = null) { sounds?.play(UiSound.SELECT); onClick() }
            .height(h).widthIn(min = if (compact) 120.dp else 180.dp)
            .padding(horizontal = if (compact) 16.dp else 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val ink = if (focused) look.btnOnInk else look.btnInk
        Icon(Icons.Filled.PlayArrow, null, tint = ink, modifier = Modifier.size(if (compact) 22.dp else 28.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = ink, fontSize = if (compact) 15.sp else 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** A small round button on the strip (options, achievements, save sync). */
@Composable
private fun BannerRound(look: BannerLook, icon: ImageVector, label: String, size: Dp, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    LaunchedEffect(focused) { if (focused) sounds?.play(UiSound.MOVE) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, spring(stiffness = Features.rowGlide.state.value.stiffness), label = "bannerRound")
    Box(
        modifier.size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .styleFocus(focused, size / 2, 0.7f, look.glow)
            .clip(CircleShape)
            .background(if (focused) look.btnOn else look.btn)
            .clickable(interactionSource = source, indication = null) { sounds?.play(UiSound.SELECT); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = if (focused) look.btnOnInk else look.btnInk, modifier = Modifier.size(size * 0.5f))
    }
}

/** Update 213: the game's save sync in the status line — an icon and short words, a tap / A opens it. */
@Composable
private fun BannerSyncChip(look: BannerLook, sync: GameSyncView, onClick: () -> Unit, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    LaunchedEffect(focused) { if (focused) sounds?.play(UiSound.MOVE) }
    val warn = Color(0xFFFFB74D)
    val shape = RoundedCornerShape(50)
    Row(
        modifier.styleFocus(focused, 12.dp, 0.7f, look.glow).clip(shape)
            .background(if (focused) look.cardOn else if (sync.urgent) warn.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(interactionSource = source, indication = null) { sounds?.play(UiSound.SELECT); onClick() }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val ink = if (sync.urgent) warn else look.muted
        Icon(sync.icon, null, tint = ink, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            "Saves: " + sync.text, color = if (focused) look.ink else ink, fontSize = 12.sp,
            fontWeight = if (sync.urgent) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/** A small-caps label over its value ("LAST PLAYED" / "Yesterday"). */
@Composable
private fun BannerStat(look: BannerLook, label: String, value: String, modifier: Modifier, lines: Int = 1) {
    Column(modifier) {
        Text(label, color = look.muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = look.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = lines, overflow = TextOverflow.Ellipsis)
    }
}

/** A thin progress bar in the game's light colour. */
@Composable
private fun BannerBar(fraction: Float, look: BannerLook, modifier: Modifier) {
    Box(modifier.height(5.dp).clip(RoundedCornerShape(3.dp)).background(look.ink.copy(alpha = 0.16f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(look.glow))
    }
}

/** A section's heading in the tab's content. */
@Composable
private fun BannerHead(look: BannerLook, text: String) {
    Text(
        text, color = look.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, maxLines = 1,
        modifier = Modifier.padding(top = 6.dp, start = 2.dp)
    )
}

/** A small rounded word on a card ("Full list", "More"). */
@Composable
private fun BannerPill(look: BannerLook, text: String) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(look.glow.copy(alpha = 0.22f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = look.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** "L1" / "R1" at the ends of the tabs (a tap does the same as the shoulder button). */
@Composable
private fun BannerHint(look: BannerLook, text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.clip(shape).border(1.dp, look.muted.copy(alpha = 0.6f), shape)
            .focusProperties { canFocus = false }
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) { Text(text, color = look.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
}

/** One tab's name; the shown one is bright with the marker under it, the D-pad's one gets the focus ring. */
@Composable
private fun BannerTabChip(look: BannerLook, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    LaunchedEffect(focused) { if (focused) sounds?.play(UiSound.MOVE) }
    val shape = uiRounded(10.dp)
    Box(
        modifier
            .styleFocus(focused, 10.dp, 0.7f, look.glow)
            .clip(shape)
            .background(if (focused) look.cardOn else Color.Transparent)
            .clickable(interactionSource = source, indication = null) { sounds?.play(UiSound.SELECT); onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            label, color = if (selected || focused) look.ink else look.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp, maxLines = 1, softWrap = false
        )
    }
}

/**
 * A card in the tab's content. With [onClick] A / a tap does it; without, the card still takes the D-pad (so the list
 * can be read to its end with it).
 */
@Composable
private fun BannerCard(look: BannerLook, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    LaunchedEffect(focused) { if (focused) sounds?.play(UiSound.MOVE) }
    val shape = uiRounded(12.dp)
    Column(
        modifier.fillMaxWidth()
            .styleFocus(focused, 12.dp, 0.7f, look.glow)
            .clip(shape)
            .background(if (focused) look.cardOn else look.card)
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = source, indication = null) { sounds?.play(UiSound.SELECT); onClick() }
                else Modifier.focusable(interactionSource = source)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        content = content
    )
}

/** An achievement: its badge (grey while still to get), name, what it asks and its points. */
@Composable
private fun BannerTrophyLine(look: BannerLook, t: RaAchievementDetail, opened: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            com.shelf.launcher.data.RaBadges.url(t.badgeName), t.title, contentScale = ContentScale.Crop,
            colorFilter = if (t.isUnlocked) null else BannerGrey, alpha = if (t.isUnlocked) 1f else 0.55f,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.title ?: "Achievement", color = look.ink.copy(alpha = if (t.isUnlocked) 1f else 0.65f), fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            t.description?.let {
                Text(
                    it, color = look.muted.copy(alpha = if (t.isUnlocked) 1f else 0.7f), fontSize = 12.sp,
                    maxLines = if (opened) 6 else 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            t.points?.let { Text("$it", color = if (t.isUnlocked) look.glow else look.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            (t.dateEarnedHardcore ?: t.dateEarned)?.let { Text(it.take(10), color = look.muted, fontSize = 10.sp, maxLines = 1) }
        }
    }
}

/** Under an opened achievement: when you earned it, how rare it is, and what people wrote about it. */
@Composable
private fun BannerTrophyMore(look: BannerLook, a: RaAchievementDetail, comments: List<com.shelf.launcher.data.RaComment>?, loading: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).background(look.ink.copy(alpha = 0.07f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val facts = buildList {
            (a.dateEarnedHardcore ?: a.dateEarned)?.let { add("Earned " + it.take(10) + if (a.dateEarnedHardcore != null) " (hardcore)" else "") }
            a.unlockPercent?.let { add("%.1f %% of players have it".format(it)) }
            if (a.isMissable) add("Missable")
        }
        if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), color = look.glow, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text("COMMENTS", color = look.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        when {
            comments == null && loading -> Text("Loading comments…", color = look.muted, fontSize = 11.sp)
            comments == null -> Text("Couldn't load the comments (no connection?). Close it and open it again to try again.", color = look.muted, fontSize = 11.sp)
            comments.isEmpty() -> Text("No comments yet.", color = look.muted, fontSize = 11.sp)
            else -> comments.take(8).forEach { cm ->
                Column {
                    Text(
                        (cm.User ?: "Player") + (cm.Submitted?.let { "  ·  " + it.take(10) } ?: ""),
                        color = look.muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                    Text(cm.CommentText.orEmpty().trim(), color = look.ink, fontSize = 11.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A picture on the Media tab; A / a tap opens it full size. */
@Composable
private fun BannerThumb(look: BannerLook, url: String, aspect: Float, height: Dp, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    LaunchedEffect(focused) { if (focused) sounds?.play(UiSound.MOVE) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, spring(stiffness = Features.rowGlide.state.value.stiffness), label = "bannerThumb")
    val shape = uiRounded(10.dp)
    Box(
        modifier.height(height).width(height * aspect)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .styleFocus(focused, 10.dp, 0.7f, look.glow)
            .clip(shape)
            .background(look.card)
            .clickable(interactionSource = source, indication = null) { sounds?.play(UiSound.SELECT); onClick() }
    ) {
        AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork())
    }
}

/**
 * A picture full size: ◀ ▶ (or a tap on the left / right edge) to the one before / after, A, B or a tap in the middle
 * closes it.
 */
@Composable
private fun BannerViewer(urls: List<String>, index: Int, onIndex: (Int) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(30); runCatching { focus.requestFocus() } }
    androidx.activity.compose.BackHandler { onClose() }
    val step by rememberUpdatedState { d: Int -> onIndex((index + d + urls.size) % urls.size) }
    val close by rememberUpdatedState(onClose)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f))
            .focusRequester(focus)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) false
                else when (e.key) {
                    Key.DirectionLeft -> { step(-1); true }
                    Key.DirectionRight -> { step(1); true }
                    Key.ButtonA, Key.Enter, Key.DirectionCenter -> { close(); true }
                    Key.DirectionUp, Key.DirectionDown -> true
                    else -> false
                }
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    val w = size.width.toFloat()
                    when {
                        p.x < w * 0.25f -> step(-1)
                        p.x > w * 0.75f -> step(1)
                        else -> close()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        key(urls[index]) {
            AsyncImage(urls[index], null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(24.dp))
        }
        if (urls.size > 1) Text(
            "${index + 1} / ${urls.size}", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
        )
    }
}
