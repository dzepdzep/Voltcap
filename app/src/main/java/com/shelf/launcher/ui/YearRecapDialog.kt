package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import com.shelf.launcher.data.PlaytimeStore
import com.shelf.launcher.data.YearRecap
import com.shelf.launcher.ui.theme.Ds
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The recap (see [YearRecap]) for a week, a month or a year: total time, days played, the top three on
 * a podium (their covers drop in, 3rd → 2nd → 1st), the longest streak and session, the most-played
 * list, a bar for every day (week / month) or month (year) and the consoles you played most.
 * ◀ ▶ step back / forward a week, month or year · L1 / R1 switch between week, month and year ·
 * X removes apps (not games) that shouldn't count · ▲ ▼ scroll · B or A closes.
 */
@Composable
internal fun YearRecapDialog(
    period: YearRecap.Period,
    games: List<Game>,
    coverForLib: (String) -> String?,
    /** Update 117: a game from another device that isn't on this one: its cover looked up by name (null: not yet). */
    foreignCover: (String) -> String? = { null },
    /** Update 117: asks for those covers (games made from the other devices' names and consoles). */
    onNeedForeignCovers: (List<Game>) -> Unit = {},
    /** The game's hero picture, for the background when there are no badges to tile. */
    heroFor: (String) -> String? = { null },
    /** RetroAchievements progress per game ID (count + % and masteries). */
    completion: Map<Int, com.shelf.launcher.data.RaCompletionEntry> = emptyMap(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sounds = LocalUiSounds.current
    val byKey = remember(games) { games.associateBy { it.stableKey } }
    val names = LocalTitleOptions.current
    var current by remember(period) { mutableStateOf(period) }
    // Bumped when an app is removed from / put back into recaps, so the recap is added up again.
    var hiddenVersion by remember { mutableStateOf(0) }
    var pickApps by remember { mutableStateOf(false) }
    val recap by produceState<YearRecap.Recap?>(null, current, hiddenVersion) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching { YearRecap.build(context, current) { k -> byKey[k]?.platform } }.getOrNull()
        }
    }
    // Achievements of the period: unlocks, count + %, completion changes, milestones.
    val ach by produceState<com.shelf.launcher.data.RecapAchievements.Result?>(null, current) {
        value = null
        value = runCatching { com.shelf.launcher.data.RecapAchievements.build(context, current, games, completion) }.getOrNull()
    }
    LaunchedEffect(period) { YearRecap.markShown(context, period) }
    // Update 117: games played on your other devices that aren't on this one — their name, and their cover (looked up
    // by name) or a disc of their console (Stats › "Recaps include your other devices").
    val others = recap?.otherInfo.orEmpty()
    // (Update 317as, Toni: "Stats don't show pictures of games that aren't on my device": a game played here and since
    // removed was neither in the library nor another device's — no picture at all. Its cover is looked up by the name in
    // its file's address, as the other devices' games are; the console guessed from its folder.)
    val gone = remember(recap) {
        val r = recap ?: return@remember emptyList<String>()
        (r.topGames.map { it.key } + listOfNotNull(r.longestSessionKey, r.firstKey)).distinct()
            .filter { k -> byKey[k] == null && k !in others && k.contains('/') }
    }
    LaunchedEffect(others, gone) {
        val wanted = (if (com.shelf.launcher.data.Features.recapForeignArt.value == com.shelf.launcher.data.Features.RecapForeignArt.COVER)
            others.map { (k, i) -> Game(i.name.ifBlank { k.substringAfterLast('/') }, i.platform, 0, com.shelf.launcher.data.SourceType.ROM, romPath = k) }
        else emptyList()) + gone.map { k -> com.shelf.launcher.data.goneGame(k) }
        if (wanted.isNotEmpty()) onNeedForeignCovers(wanted)
    }
    val coverFor: (String) -> String? = { k ->
        coverForLib(k) ?: (if (byKey[k] == null && k !in others) foreignCover(k) else null) ?: others[k]?.let { info ->
            val disc = com.shelf.launcher.data.SystemLogos.discAsset(info.platform)?.let { "file:///android_asset/$it" }
            if (com.shelf.launcher.data.Features.recapForeignArt.value == com.shelf.launcher.data.Features.RecapForeignArt.DISC) disc
            else foreignCover(k) ?: disc
        }
    }
    fun nameOf(key: String?): String = key?.let { k ->
        byKey[k]?.name?.let { gameDisplayName(it) } ?: others[k]?.name?.takeIf { it.isNotBlank() }?.let { gameDisplayName(it) }
            ?: k.substringAfterLast('/').substringBeforeLast('.')
    } ?: "—"
    fun switchTo(p: YearRecap.Period) { if (p.id != current.id) { current = p; sounds?.play(UiSound.MOVE) } }
    fun step(delta: Int) {
        val next = (current.offset + delta).coerceAtMost(0)
        if (next != current.offset || current.id.startsWith("anniv")) switchTo(YearRecap.period(current.kind, next))
    }
    fun cycleKind(delta: Int) {
        val all = YearRecap.Kind.values()
        switchTo(YearRecap.period(all[(current.kind.ordinal + delta + all.size) % all.size]))
    }

    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { focus.requestFocus() } }
    LaunchedEffect(current.id) { scroll.scrollTo(0) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveDialogWindow()
        Box(
            Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(Ds.rXLarge))
                .background(
                    Brush.linearGradient(
                        listOf(
                            ShelfTheme.accent.copy(alpha = 0.55f).compositeOverBlack(),
                            Color(0xFF07080D),
                            ShelfTheme.colors.secondary.copy(alpha = 0.35f).compositeOverBlack()
                        )
                    )
                )
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> { scope.launch { scroll.animateScrollTo(scroll.value + 220) }; true }
                        Key.DirectionUp -> { scope.launch { scroll.animateScrollTo((scroll.value - 220).coerceAtLeast(0)) }; true }
                        Key.DirectionLeft -> { step(-1); true }
                        Key.DirectionRight -> { step(1); true }
                        // L2 / R2 (and L1 / R1, as before): week → month → year.
                        Key.ButtonL2, Key.ButtonL1, Key.PageUp -> { cycleKind(-1); true }
                        Key.ButtonR2, Key.ButtonR1, Key.PageDown -> { cycleKind(1); true }
                        Key.ButtonX -> { pickApps = true; true }
                        Key.ButtonB, Key.Back, Key.ButtonA, Key.Escape -> { onDismiss(); true }
                        else -> false
                    }
                }
        ) {
            val r = recap
            // The background: the period's badges tiled into a slowly drifting wall, or (no badges) the
            // heroes of its most-played games taking turns.
            RecapBackdrop(
                badges = ach?.badges.orEmpty(),
                heroes = r?.topGames.orEmpty().mapNotNull { heroFor(it.key) ?: coverFor(it.key) }.distinct().take(5)
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(Ds.s5),
                verticalArrangement = Arrangement.spacedBy(Ds.s4)
            ) {
                // Week / Month / Year, and ◀ ▶ through earlier ones.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Ds.s2)) {
                    YearRecap.Kind.values().forEach { k ->
                        RecapPill(k.label, selected = current.kind == k && !current.id.startsWith("anniv")) { switchTo(YearRecap.period(k)) }
                    }
                    Spacer(Modifier.weight(1f))
                    RecapPill("◀", selected = false) { step(-1) }
                    RecapPill("▶", selected = false, enabled = current.offset < 0 || current.id.startsWith("anniv")) { step(1) }
                }
                Text(current.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                if (r == null) {
                    Text("Adding it all up…", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                } else {
                if (r.totalMs <= 0L) {
                    Text("Nothing played in this ${current.kind.label.lowercase()} yet.", color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
                    Text("◀ ▶ earlier / later · L2 R2 week, month, year · B closes", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                } else {
                // The big number.
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(PlaytimeStore.format(r.totalMs), color = ShelfTheme.accent, fontSize = 44.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(Ds.s3))
                    Text("played over ${r.days} day${if (r.days == 1) "" else "s"}", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp,
                        modifier = Modifier.padding(bottom = 8.dp))
                }
                // The podium: the top three, covers dropping in.
                if (r.topGames.isNotEmpty()) {
                    key(current.id, hiddenVersion) {
                        RecapPodium(r.topGames.take(3), coverFor, ::nameOf)
                    }
                }
                // Stat tiles.
                Row(horizontalArrangement = Arrangement.spacedBy(Ds.s2), modifier = Modifier.fillMaxWidth()) {
                    RecapTile("Games", r.gamesPlayed.toString(), Modifier.weight(1f))
                    RecapTile("Sessions", r.sessions.toString(), Modifier.weight(1f))
                    RecapTile("Best streak", "${r.longestStreakDays} day${if (r.longestStreakDays == 1) "" else "s"}", Modifier.weight(1f))
                    RecapTile("Finished", r.finished.toString(), Modifier.weight(1f))
                }
                // Top games.
                if (r.topGames.isNotEmpty()) {
                    RecapHeading("Most played")
                    val max = r.topGames.first().ms.coerceAtLeast(1L)
                    r.topGames.forEachIndexed { i, g ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", color = if (i == 0) ShelfTheme.accent else Color.White.copy(alpha = 0.6f),
                                fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(26.dp))
                            val cover = coverFor(g.key)
                            if (cover != null) {
                                AsyncImage(cover, null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(width = 40.dp, height = 54.dp).clip(RoundedCornerShape(Ds.rSmall)))
                            } else {
                                Box(Modifier.size(width = 40.dp, height = 54.dp).clip(RoundedCornerShape(Ds.rSmall)).background(ShelfTheme.accent.copy(alpha = 0.25f)))
                            }
                            Spacer(Modifier.width(Ds.s3))
                            Column(Modifier.weight(1f)) {
                                Text(nameOf(g.key), color = Color.White, fontSize = 14.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(14.sp), maxFontSize = 14.sp), fontWeight = FontWeight.Bold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.1f))) {
                                    Box(Modifier.fillMaxWidth((g.ms.toFloat() / max).coerceIn(0.03f, 1f)).fillMaxHeight().background(ShelfTheme.accent))
                                }
                            }
                            Spacer(Modifier.width(Ds.s3))
                            Text(PlaytimeStore.format(g.ms), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                // Achievements: unlocks, per game count + %, completion changes, milestones.
                ach?.takeIf { !it.isEmpty }?.let { a -> RecapAchievementsSection(a, coverFor) }
                // Day by day (week, month) or month by month (year).
                RecapHeading(
                    (if (current.kind == YearRecap.Kind.YEAR) "Month by month" else "Day by day") + " · busiest: ${r.busiestLabel}"
                )
                val maxBar = r.buckets.maxOrNull()?.coerceAtLeast(1L) ?: 1L
                Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(if (r.buckets.size > 12) 2.dp else 6.dp), verticalAlignment = Alignment.Bottom) {
                    r.buckets.forEachIndexed { m, ms ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.fillMaxWidth().height((72f * ms / maxBar).coerceAtLeast(2f).dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (m == r.busiest) ShelfTheme.accent else ShelfTheme.accent.copy(alpha = 0.4f))
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(r.bucketLabels.getOrElse(m) { "" }, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(10.sp), maxFontSize = 10.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false)
                        }
                    }
                }
                // Consoles.
                if (r.topPlatforms.isNotEmpty()) {
                    RecapHeading("Consoles")
                    Row(horizontalArrangement = Arrangement.spacedBy(Ds.s2), modifier = Modifier.fillMaxWidth()) {
                        r.topPlatforms.forEach { (p, ms) ->
                            RecapTile(names.console(p), PlaytimeStore.format(ms), Modifier.weight(1f))
                        }
                        repeat(4 - r.topPlatforms.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                // Little facts.
                RecapHeading("And also")
                Fact("Longest session", "${PlaytimeStore.format(r.longestSessionMs)} of ${nameOf(r.longestSessionKey)}")
                Fact("First game of the ${current.kind.label.lowercase()}", nameOf(r.firstKey))
                Spacer(Modifier.height(Ds.s2))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("◀ ▶ earlier / later · L2 R2 week, month, year · B closes", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.weight(1f))
                    RecapPill("X  Remove apps", selected = false) { pickApps = true }
                }
                    }
                }
            }
        }
    }

    if (pickApps) {
        RecapAppsDialog(current, byKey, ::nameOf, onChanged = { hiddenVersion++ }, onDismiss = {
            pickApps = false
            scope.launch { kotlinx.coroutines.delay(80); runCatching { focus.requestFocus() } }
        })
    }
}

/**
 * X in a recap: the apps with play in it (only apps — a recorded browser or video player that isn't
 * a game; games always count), each one switched in or out of every recap.
 */
@Composable
private fun RecapAppsDialog(
    period: YearRecap.Period,
    byKey: Map<String, Game>,
    nameOf: (String?) -> String,
    onChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val keys by produceState<List<YearRecap.GameTime>?>(null, period) {
        value = withContext(Dispatchers.IO) { YearRecap.keysIn(context, period) }
    }
    var hidden by remember { mutableStateOf(YearRecap.hiddenKeys(context)) }
    ShelfDialog(icon = androidx.compose.material.icons.Icons.Filled.Apps, title = "Remove apps from recaps",
        subtitle = "Apps that were recorded but aren't games. Games always count.", onDismiss = onDismiss, widthFraction = 0.7f) {
        val k = keys
        // Apps: an app entry in the library, or a package name that isn't in the library at all.
        val apps = k?.filter { g -> byKey[g.key]?.sourceType == com.shelf.launcher.data.SourceType.APP || (byKey[g.key] == null && !g.key.contains('/') && g.key.contains('.') && !g.key.startsWith("steam:")) }
        when {
            apps == null -> MutedNote("Loading…")
            apps.isEmpty() -> MutedNote("No apps were played in this stretch — only games, and those always count.")
            else -> apps.forEach { g ->
                val off = g.key in hidden
                MenuRow(
                    if (off) androidx.compose.material.icons.Icons.Filled.VisibilityOff else androidx.compose.material.icons.Icons.Filled.Visibility,
                    nameOf(g.key),
                    (if (off) "Left out of recaps" else "Counted") + " · " + PlaytimeStore.format(g.ms)
                ) {
                    YearRecap.setHidden(context, g.key, !off)
                    hidden = YearRecap.hiddenKeys(context)
                    onChanged()
                }
            }
        }
    }
}

/** 1st in the middle (tallest), 2nd on the left, 3rd on the right. The blocks rise, then the covers drop in and bounce, 3rd first. */
@Composable
private fun RecapPodium(top: List<YearRecap.GameTime>, coverFor: (String) -> String?, nameOf: (String?) -> String) {
    val context = LocalContext.current
    val sounds = LocalUiSounds.current
    // Slot order on screen: 2nd, 1st, 3rd.
    val slots = listOf(1, 0, 2).filter { it < top.size }
    val rise = remember { Animatable(0f) }
    val drops = remember { List(3) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        RetroFx.warm(context)
        rise.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        for (place in (top.size - 1) downTo 0) {
            launch {
                drops[place].animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 260f))
            }
            kotlinx.coroutines.delay(170)
            RetroFx.play(context, sounds, RetroSound.LAND, pitch = 1f + (2 - place) * 0.08f)
            kotlinx.coroutines.delay(if (place == 1) 260 else 200)
        }
        RetroFx.play(context, sounds, RetroSound.LOCK)
    }
    val medal = listOf(Color(0xFFF5C542), Color(0xFFCBD3DC), Color(0xFFD08A4E))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        slots.forEach { place ->
            val g = top[place]
            val coverW = when (place) { 0 -> 92.dp; 1 -> 76.dp; else -> 70.dp }
            val blockH = when (place) { 0 -> 74.dp; 1 -> 52.dp; else -> 38.dp }
            val drop = drops[place].value
            Column(
                Modifier.width(coverW + 34.dp).padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .graphicsLayer {
                            translationY = (1f - drop) * -420f
                            alpha = (drop * 3f).coerceIn(0f, 1f)
                            val squash = 1f + (drop - 1f).coerceAtLeast(0f) * 0.4f
                            scaleY = 1f / squash; scaleX = squash
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        }
                        .size(width = coverW, height = coverW * 1.35f)
                        .clip(RoundedCornerShape(Ds.rSmall))
                        .border(2.dp, medal[place].copy(alpha = if (place == 0) 0.9f else 0.6f), RoundedCornerShape(Ds.rSmall))
                ) {
                    val cover = coverFor(g.key)
                    if (cover != null) AsyncImage(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Box(Modifier.fillMaxSize().background(ShelfTheme.accent.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                        Text(nameOf(g.key).take(2), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(nameOf(g.key), color = Color.White.copy(alpha = 0.9f * drop.coerceIn(0f, 1f)), fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(PlaytimeStore.format(g.ms), color = Color.White.copy(alpha = 0.6f * drop.coerceIn(0f, 1f)), fontSize = 10.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(10.sp), maxFontSize = 10.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(blockH * rise.value)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(Brush.verticalGradient(listOf(medal[place].copy(alpha = 0.85f), medal[place].copy(alpha = 0.35f)))),
                    contentAlignment = Alignment.Center
                ) {
                    if (rise.value > 0.6f) Text("${place + 1}", color = Color(0xFF15161C), fontSize = if (place == 0) 26.sp else 20.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun RecapPill(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) ShelfTheme.accent else Color.White.copy(alpha = if (enabled) 0.1f else 0.04f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(label, color = if (selected) Color(0xFF101114) else Color.White.copy(alpha = if (enabled) 0.85f else 0.3f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecapHeading(text: String) {
    Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun RecapTile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(Ds.rMedium))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(Ds.rMedium))
            .padding(horizontal = Ds.s3, vertical = Ds.s2)
    ) {
        Text(value, color = Color.White, fontSize = 18.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(18.sp), maxFontSize = 18.sp), fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.width(170.dp))
        Text(value, color = Color.White, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun Color.compositeOverBlack(): Color = Color(red * alpha, green * alpha, blue * alpha, 1f)

/** The recap's achievements: the total, the newest badges, each game's count + %, completion changes and milestones. */
@Composable
private fun RecapAchievementsSection(a: com.shelf.launcher.data.RecapAchievements.Result, coverFor: (String) -> String?) {
    RecapHeading("Achievements")
    Row(verticalAlignment = Alignment.Bottom) {
        Text("${a.count}", color = Color(0xFFFFC94D), fontSize = 34.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(Ds.s2))
        Text(
            "unlocked" + if (a.points > 0) " · ${a.points} points" else "",
            color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp)
        )
    }
    if (a.badges.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            a.badges.take(12).forEach { b ->
                AsyncImage(model = b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)))
            }
        }
    }
    a.games.take(6).forEach { g ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            val pic = g.key?.let(coverFor) ?: g.icon
            if (pic != null) AsyncImage(model = pic, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 30.dp, height = 40.dp).clip(RoundedCornerShape(Ds.rSmall)))
            else Box(Modifier.size(width = 30.dp, height = 40.dp).clip(RoundedCornerShape(Ds.rSmall)).background(ShelfTheme.accent.copy(alpha = 0.25f)))
            Spacer(Modifier.width(Ds.s3))
            Text(gameDisplayName(g.name), color = Color.White, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.SemiBold, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("+${g.count}", color = Color(0xFFFFC94D), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (g.total > 0) {
                Spacer(Modifier.width(Ds.s3))
                Text(com.shelf.launcher.data.achievementProgress(g.unlocked, g.total), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
            }
        }
    }
    if (a.changes.isNotEmpty()) {
        RecapHeading("Completion")
        a.changes.take(5).forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(gameDisplayName(c.name), color = Color.White, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${c.before}% → ${c.after}%", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(Ds.s2))
                Box(Modifier.width(90.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.1f))) {
                    Box(Modifier.fillMaxWidth(c.after / 100f).fillMaxHeight().background(Color(0xFFFFC94D)))
                    Box(Modifier.fillMaxWidth(c.before / 100f).fillMaxHeight().background(ShelfTheme.accent))
                }
            }
        }
    }
    if (a.milestones.isNotEmpty()) {
        RecapHeading("Milestones")
        a.milestones.forEach { (game, text) -> Fact(text, gameDisplayName(game)) }
    }
}

/**
 * Behind the recap: its badges tiled into a wall that drifts very slowly (dimmed so the numbers read),
 * or — no badges — the heroes of its top games crossfading every few seconds. One layer moves; it stops
 * with the dialog.
 */
@Composable
private fun RecapBackdrop(badges: List<String>, heroes: List<String>) {
    val liteStill = LocalLiteEffects.current
    if (badges.isNotEmpty()) {
        val drift = if (liteStill) null else androidx.compose.animation.core.rememberInfiniteTransition(label = "recapWall").animateFloat(
            0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(60_000, easing = androidx.compose.animation.core.LinearEasing)), label = "recapDrift"
        )
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(Ds.rXLarge))) {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = drift?.value ?: 0f
                        translationX = -p * 72.dp.toPx()
                        translationY = -p * 36.dp.toPx()
                        rotationZ = -8f
                        scaleX = 1.35f; scaleY = 1.35f
                        alpha = 0.16f
                    },
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(10) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = if (row % 2 == 0) 0.dp else 36.dp)) {
                        repeat(18) { col ->
                            AsyncImage(model = badges[(row * 7 + col) % badges.size], contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(62.dp).clip(RoundedCornerShape(8.dp)))
                        }
                    }
                }
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x99000000), Color(0x33000000), Color(0xCC000000)))))
        }
    } else if (heroes.isNotEmpty()) {
        var i by androidx.compose.runtime.remember(heroes) { mutableStateOf(0) }
        LaunchedEffect(heroes, liteStill) {
            if (liteStill || heroes.size < 2) return@LaunchedEffect
            while (true) { kotlinx.coroutines.delay(7000); i = (i + 1) % heroes.size }
        }
        androidx.compose.animation.Crossfade(targetState = heroes[i % heroes.size], animationSpec = tween(1600), label = "recapHero",
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(Ds.rXLarge))) { url ->
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.22f })
        }
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(Ds.rXLarge)).background(Brush.verticalGradient(listOf(Color(0x66000000), Color(0xCC000000)))))
    }
}
