package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * The "showcase" header (Appearance › Game popup › Layout, and Home › Game header): like a console store
 * page — the title, an accent "CONTINUE PLAYING · TODAY" line, small glass boxes with the numbers
 * (played, achievements, last played, time to beat…), then a wide Play pill with round icon buttons
 * beside it and the game's source as a small badge at the right. Used by the game popup and above the
 * home row, so both look the same. Controller selection uses the popup's actions (DetailsAction).
 */

/** One stat box: small caps label, value, optional progress line under it. [action]: selectable / tappable. */
internal data class HeaderStat(
    val label: String,
    val value: String,
    val progress: Float? = null,
    val valueColor: Color = Color.White,
    val action: DetailsAction? = null
)

/** One round icon button beside Play. */
internal data class HeaderButton(
    val action: DetailsAction?,
    val icon: ImageVector,
    val description: String,
    val tint: Color? = null,
    val onClick: () -> Unit
)

/**
 * Sizes for the header. The home screen uses the dense set (it sits over the hero, above the row, and must
 * stay small); the popup keeps the regular / compact sets.
 */
internal data class HeaderSizes(
    val title: Int, val continueSp: Float, val statLabel: Float, val statValue: Float, val statMinW: Int,
    val statPadH: Int, val statPadV: Int, val gap: Int, val playH: Int, val playMinW: Int, val playIcon: Int,
    val playText: Float, val round: Int, val roundGap: Int, val afterStats: Int
)

private val RegularSizes = HeaderSizes(30, 10f, 9f, 15f, 68, 10, 6, 6, 40, 180, 18, 14f, 34, 8, 10)
private val CompactSizes = HeaderSizes(24, 9f, 8f, 13f, 58, 8, 5, 6, 34, 140, 16, 13f, 30, 6, 8)
private val DenseSizes = HeaderSizes(20, 8.5f, 7f, 11f, 44, 6, 3, 4, 28, 100, 14, 12f, 26, 5, 6)

/** Set by [HomeShowcaseHeader]: everything inside uses [DenseSizes]. */
private val LocalHeaderDense = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
private fun headerSizes(compact: Boolean): HeaderSizes =
    if (LocalHeaderDense.current) DenseSizes else if (compact) CompactSizes else RegularSizes

/** Taps that survive redraws (a new lambda every frame used to cancel taps midway). */
@Composable
private fun Modifier.headerTap(onClick: (() -> Unit)?): Modifier {
    // No early return before remember (this project's Compose compiler mishandles them, see AppIcons.kt).
    val latest = rememberUpdatedState(onClick)
    return if (onClick != null) this.pointerInput(Unit) { detectTapGestures { latest.value?.invoke() } } else this
}

/** "CONTINUE PLAYING · TODAY", or "NEW · NOT PLAYED YET". */
@Composable
internal fun ContinueLine(lastPlayedAt: Long?, compact: Boolean = false) {
    Text(
        if (lastPlayedAt != null) "CONTINUE PLAYING  ·  " + lastPlayedLabel(lastPlayedAt).uppercase() else "NEW  ·  NOT PLAYED YET",
        color = ShelfTheme.accent, fontSize = headerSizes(compact).continueSp.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(headerSizes(compact).continueSp.sp), maxFontSize = headerSizes(compact).continueSp.sp), fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun HeaderStatBox(stat: HeaderStat, selected: Boolean, compact: Boolean, onClick: (() -> Unit)?) {
    val shape = uiRounded(6.dp)
    val z = headerSizes(compact)
    Column(
        Modifier
            .width(IntrinsicSize.Max)
            .widthIn(min = z.statMinW.dp)
            .revealWhen(selected)
            .then(if (onClick != null) Modifier.focusRing(selected, shape) else Modifier)
            .clip(shape)
            .background(if (selected) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.38f))
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.9f else 0.10f), shape)
            .headerTap(onClick)
            .padding(horizontal = z.statPadH.dp, vertical = z.statPadV.dp)
    ) {
        Text(stat.label, color = Color.White.copy(alpha = 0.62f), fontSize = z.statLabel.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(z.statLabel.sp), maxFontSize = z.statLabel.sp), fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false)
        Spacer(Modifier.height(if (z === DenseSizes) 1.dp else 2.dp))
        Text(stat.value, color = stat.valueColor, fontSize = z.statValue.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(z.statValue.sp), maxFontSize = z.statValue.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false)
        val p = stat.progress
        if (p != null) {
            Box(Modifier.padding(top = (z.statPadV + 1).dp).fillMaxWidth().height(2.dp).clip(uiRounded(1.dp)).background(Color.White.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).fillMaxHeight().background(ShelfTheme.accent))
            }
        }
    }
}

/** The wide Play pill: accent fill with a soft lighter rim; brighter rim and focus ring when selected. */
@Composable
internal fun PlayPill(label: String, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val shape = uiRounded(50)
    val accent = ShelfTheme.accent
    val z = headerSizes(compact)
    Row(
        Modifier
            .height(z.playH.dp)
            .widthIn(min = z.playMinW.dp)
            .revealWhen(selected)
            .focusRing(selected, shape)
            .clip(shape)
            .background(if (VoltUi.on) voltFill() else Brush.verticalGradient(listOf(lerp(accent, Color.White, 0.10f), lerp(accent, Color.Black, 0.22f))))
            .border(1.5.dp, lerp(accent, Color.White, 0.45f).copy(alpha = if (selected) 1f else 0.55f), shape)
            .headerTap(onClick)
            .padding(horizontal = if (z === DenseSizes) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = onAccentColor(), modifier = Modifier.size(z.playIcon.dp))
        Spacer(Modifier.width(if (z === DenseSizes) 6.dp else 8.dp))
        Text(label, color = onAccentColor(), fontSize = z.playText.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(z.playText.sp), maxFontSize = z.playText.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** A round glass icon button (controller, cog, info, cloud…): white when selected. */
@Composable
internal fun RoundIconButton(button: HeaderButton, selected: Boolean, compact: Boolean, sizeOverride: androidx.compose.ui.unit.Dp? = null) {
    val size = sizeOverride ?: headerSizes(compact).round.dp
    Box(
        Modifier
            .size(size)
            .revealWhen(selected)
            .focusRing(selected, CircleShape)
            .clip(CircleShape)
            .background(if (selected) Color.White else Color.Black.copy(alpha = 0.40f))
            .border(1.dp, Color.White.copy(alpha = if (selected) 1f else 0.25f), CircleShape)
            .headerTap(button.onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            button.icon, contentDescription = button.description,
            tint = if (selected) Color.Black else button.tint ?: Color.White,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/** Where the game comes from ("Steam", "PlayStation 2", "Android"), as a small pill at the right. */
@Composable
internal fun SourceBadge(text: String, icon: ImageVector? = null) {
    val shape = uiRounded(50)
    Row(
        Modifier.clip(shape).background(Color.Black.copy(alpha = 0.45f)).border(1.dp, Color.White.copy(alpha = 0.2f), shape)
            .padding(horizontal = if (LocalHeaderDense.current) 8.dp else 10.dp, vertical = if (LocalHeaderDense.current) 3.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, color = Color.White, fontSize = if (LocalHeaderDense.current) 9.5.sp else 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(if (LocalHeaderDense.current) 9.5.sp else 11.sp), maxFontSize = if (LocalHeaderDense.current) 9.5.sp else 11.sp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The whole block. [title] null = no title (the popup already has it on its banner). [selected] is the
 * controller's current action (popup) or null (home: the row keeps the controller, these are for touch).
 */
@Composable
internal fun ShowcaseHeader(
    title: String?,
    lastPlayedAt: Long?,
    stats: List<HeaderStat>,
    buttons: List<HeaderButton>,
    source: String?,
    selected: DetailsAction?,
    compact: Boolean,
    onPlay: () -> Unit,
    onStat: (DetailsAction) -> Unit,
    modifier: Modifier = Modifier,
    /** The "CONTINUE PLAYING · …" line (the home header can switch it off). */
    showContinue: Boolean = true,
    /** The Play / Continue pill (the home header can switch it off). */
    showPlay: Boolean = true,
    /** Home header: each part sits in a FlowSlot so Edit Home can move it on its own (update 90). */
    slots: Boolean = false,
    /** Draw only this part (HomeWidgets.H_*), for a part moved out of the header. */
    only: String? = null,
    /** Home header, tight layout: the category above the title, no big gaps, the score / save sync / game options small at the right. */
    tight: Boolean = false,
    /** The category being shown (the tight layout puts it above the title). */
    category: String? = null,
    /** Home header reached with the D-pad (HomeReach): Play, or [focusedButton], has the selection. */
    playFocused: Boolean = false,
    focusedButton: HeaderButton? = null
) {
    val z = headerSizes(compact)
    // Tight: the score and the two small buttons live in the title row of the whole header; a part drawn on its own (moved out in Edit Home) leaves them out too, or they would show twice.
    val tightSmall = if (tight) buttons.filter { it.description == "Save sync" || it.description == "Game options" } else emptyList()
    val tightScore = if (tight) stats.firstOrNull { it.label == "SCORE" } else null
    @Suppress("NAME_SHADOWING") val stats = if (tight) stats.filter { it.label != "SCORE" } else stats
    @Suppress("NAME_SHADOWING") val buttons = if (tightSmall.isEmpty()) buttons else buttons.filter { it !in tightSmall }
    @Composable
    fun part(id: String, m: Modifier = Modifier, body: @Composable () -> Unit) {
        if (slots) FlowSlot(id, m) { body() } else Box(m) { body() }
    }
    val titlePart: @Composable () -> Unit = {
        // One line that shrinks for long names (down to ~2/3), so the header keeps its height and the
        // row never jumps between games; only very long names take a second line.
        FitText(
            title!!, color = Color.White, maxSize = z.title.sp, minSize = (z.title * 0.66f).sp, maxLines = 1,
            fontWeight = FontWeight.ExtraBold, lineHeightRatio = 1.1f,
            style = uiFontStyle().merge(TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 8f))),
            modifier = Modifier.fillMaxWidth()
        )
    }
    val linePart: @Composable () -> Unit = { ContinueLine(lastPlayedAt, compact) }
    val statsPart: @Composable () -> Unit = {
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(z.gap.dp),
            verticalArrangement = Arrangement.spacedBy(z.gap.dp)
        ) {
            stats.forEach { s ->
                HeaderStatBox(s, selected != null && s.action == selected, compact, s.action?.let { a -> { onStat(a) } })
            }
        }
    }
    val actionsPart: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showPlay) {
                PlayPill(
                    if (lastPlayedAt != null) "Continue" else "Play",
                    selected = selected == DetailsAction.PLAY || playFocused, compact = compact, onClick = onPlay
                )
                if (buttons.isNotEmpty()) Spacer(Modifier.width((z.roundGap + 3).dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(z.roundGap.dp), verticalAlignment = Alignment.CenterVertically) {
                buttons.forEach { b -> RoundIconButton(b, (selected != null && b.action == selected) || b === focusedButton, compact) }
            }
        }
    }
    val badgePart: @Composable () -> Unit = { SourceBadge(source!!) }
    if (only != null) {
        Box(modifier) {
            when (only) {
                HomeWidgets.H_TITLE -> if (title != null) Column {
                    // The category above the title goes with it when it is moved.
                    if (tight && !category.isNullOrBlank()) Text(
                        category.uppercase(), color = ShelfTheme.accent, fontSize = 8.5.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = uiFontStyle().merge(TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 1f), 4f)))
                    )
                    titlePart()
                }
                HomeWidgets.H_LINE -> if (showContinue) linePart()
                HomeWidgets.H_STATS -> if (stats.isNotEmpty()) statsPart()
                HomeWidgets.H_ACTIONS -> if (showPlay || buttons.isNotEmpty()) actionsPart()
                HomeWidgets.H_BADGE -> if (source != null) badgePart()
                // Update 116: Play / Continue on its own, and the round buttons on their own.
                HomeWidgets.H_PLAY -> if (showPlay) PlayPill(
                    if (lastPlayedAt != null) "Continue" else "Play",
                    selected = selected == DetailsAction.PLAY || playFocused, compact = compact, onClick = onPlay
                )
                HomeWidgets.H_BUTTONS -> if (buttons.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(z.roundGap.dp), verticalAlignment = Alignment.CenterVertically) {
                    buttons.forEach { b -> RoundIconButton(b, (selected != null && b.action == selected) || b === focusedButton, compact) }
                }
            }
        }
        return
    }
    Column(modifier) {
        val gapTitle = if (tight) 0.dp else 2.dp
        val hasCluster = tightScore != null || tightSmall.isNotEmpty()
        if (title != null || (tight && hasCluster)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) {
                    if (title != null) part(HomeWidgets.H_TITLE, Modifier.fillMaxWidth()) {
                        Column {
                            if (tight && !category.isNullOrBlank()) Text(
                                category.uppercase(), color = ShelfTheme.accent, fontSize = 8.5.sp, fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = uiFontStyle().merge(TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 1f), 4f)))
                            )
                            titlePart()
                        }
                    }
                }
                if (tight && hasCluster) {
                    Spacer(Modifier.width(8.dp))
                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        tightScore?.let { sc ->
                            val shape = uiRounded(5.dp)
                            Text(
                                sc.value, color = sc.valueColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                                modifier = Modifier.clip(shape).background(Color.Black.copy(alpha = 0.42f)).border(1.dp, sc.valueColor.copy(alpha = 0.6f), shape)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        tightSmall.forEach { b -> RoundIconButton(b, b === focusedButton, compact, sizeOverride = 22.dp) }
                    }
                }
            }
            Spacer(Modifier.height(gapTitle))
        }
        if (showContinue) part(HomeWidgets.H_LINE) { linePart() }
        if (stats.isNotEmpty()) {
            Spacer(Modifier.height(if (tight) 2.dp else (z.gap + 2).dp))
            part(HomeWidgets.H_STATS) { statsPart() }
        }
        if (showPlay || buttons.isNotEmpty() || source != null) {
            Spacer(Modifier.height(if (tight) 4.dp else z.afterStats.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (showPlay || buttons.isNotEmpty()) part(HomeWidgets.H_ACTIONS) { actionsPart() }
                // The badge takes what's left at the right and shortens its text there (on a narrow popup it
                // used to be squeezed and cut mid-word next to the round buttons).
                Box(Modifier.weight(1f).padding(start = 8.dp), contentAlignment = Alignment.CenterEnd) {
                    if (source != null) part(HomeWidgets.H_BADGE) { badgePart() }
                }
            }
        }
    }
}

/** The home game header's parts that can be switched off: id (as stored) to the name shown in Settings. */
internal val HOME_HEADER_PARTS: List<Pair<String, String>> = listOf(
    "title" to "Title", "continue" to "Continue playing line", "PLAYED" to "Played", "ACHIEVEMENTS" to "Achievements",
    "LAST PLAYED" to "Last played", "TIME TO BEAT" to "Time to beat", "SCORE" to "Score", "play" to "Play button",
    "fav" to "★ Favourite", "details" to "ⓘ Details", "options" to "⚙ Game options", "sync" to "☁ Save sync", "source" to "Console badge"
)

/**
 * Home › Game header: the showcase block for the game you're on, above the row (like a console's home).
 * The controller stays on the row (A opens the popup as always); Play and the round buttons are for touch:
 * ★ favourite, ⓘ details, ⚙ game options, ☁ save sync (only when the game has a sync status).
 */
@Composable
internal fun HomeShowcaseHeader(
    game: com.shelf.launcher.data.Game,
    title: String,
    lastPlayedAt: Long?,
    playtimeMs: Long?,
    metadata: com.shelf.launcher.data.GameMetadata?,
    raProgress: com.shelf.launcher.data.RaGameProgress?,
    isFavorite: Boolean,
    syncStatus: com.shelf.launcher.data.GameSyncStatus?,
    console: String,
    compact: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onDetails: () -> Unit,
    onOptions: () -> Unit,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
    /** Each part movable on its own in Edit Home (the home's own header). */
    slots: Boolean = false,
    /** Only this part (one moved out of the header). */
    only: String? = null,
    /** The category being shown (the tight header puts it above the title). */
    category: String? = null
) {
    val total = if (game.raGameId != null) raProgress?.NumPossibleAchievements ?: 0 else 0
    val stats = showcaseStats(
        playtimeMs = playtimeMs, lastPlayedAt = lastPlayedAt,
        earned = raProgress?.NumAchieved ?: 0, total = total,
        beatHours = metadata?.let { it.normallyHours ?: it.hastilyHours },
        score = metadata?.let { it.metacritic ?: it.criticScore },
        statsAction = null
    )
    val icons = Icons.Filled
    val synced = syncStatus?.state == com.shelf.launcher.data.SyncState.SYNCED
    // Settings › Home › Next to the row › Game header: the parts switched off.
    val hidden = com.shelf.launcher.data.Features.homeHeaderHidden.state.value
    val buttons = listOfNotNull(
        if ("fav" in hidden) null else HeaderButton(null, if (isFavorite) icons.Star else icons.StarBorder, if (isFavorite) "Favorited" else "Favorite",
            if (isFavorite) com.shelf.launcher.ui.theme.Ds.gold else null, onFavorite),
        if ("details" in hidden) null else HeaderButton(null, icons.Info, "Details", null, onDetails),
        if ("options" in hidden) null else HeaderButton(null, icons.Settings, "Game options", null, onOptions),
        if (syncStatus != null && "sync" !in hidden) HeaderButton(
            null, if (synced) icons.CloudDone else icons.CloudSync, "Save sync",
            if (synced) com.shelf.launcher.ui.theme.Ds.success else null, onSync
        ) else null
    )
    // Update 101: Play and the round buttons can be reached with the D-pad (▲ / ▼ past the row, HomeReach.kt).
    val showPlay = "play" !in hidden
    // (Update 116: Play on its own publishes them too, so the D-pad still reaches Play and the round buttons.)
    val publishes = only == null || only == HomeWidgets.H_ACTIONS || only == HomeWidgets.H_PLAY
    if (publishes) {
        val actions = (if (showPlay) listOf(onPlay) else emptyList()) + buttons.map { it.onClick }
        androidx.compose.runtime.SideEffect { HomeReach.header = actions }
        androidx.compose.runtime.DisposableEffect(Unit) { onDispose { HomeReach.header = emptyList() } }
    }
    val reachAt = if ((publishes || only == HomeWidgets.H_BUTTONS) && HomeReach.zone == HomeReach.Zone.HEADER) HomeReach.index else -1
    val playFocused = showPlay && reachAt == 0
    val focusedButton = if (reachAt < 0) null else buttons.getOrNull(reachAt - if (showPlay) 1 else 0)
    androidx.compose.runtime.CompositionLocalProvider(LocalHeaderDense provides true) { ShowcaseHeader(
        title = title.takeIf { "title" !in hidden }, lastPlayedAt = lastPlayedAt, stats = stats.filter { it.label !in hidden }, buttons = buttons,
        source = if ("source" in hidden) null else when (game.sourceType) {
            com.shelf.launcher.data.SourceType.STEAM -> "Steam"
            com.shelf.launcher.data.SourceType.APP -> "Android"
            else -> console
        },
        selected = null, compact = compact, onPlay = onPlay, onStat = {}, modifier = modifier,
        showContinue = "continue" !in hidden, showPlay = showPlay, slots = slots, only = only,
        tight = com.shelf.launcher.data.Features.homeHeaderTight.state.value, category = category,
        playFocused = playFocused, focusedButton = focusedButton
    ) }
}

/** The stat boxes for a game, from what's known (shared by the popup and the home header). */
internal fun showcaseStats(
    playtimeMs: Long?,
    lastPlayedAt: Long?,
    earned: Int,
    total: Int,
    beatHours: Double?,
    score: Int?,
    statsAction: DetailsAction?
): List<HeaderStat> = listOfNotNull(
    HeaderStat("PLAYED", playtimeMs?.takeIf { it > 0L }?.let { com.shelf.launcher.data.PlaytimeStore.format(it) } ?: "New", action = statsAction),
    if (total > 0) HeaderStat("ACHIEVEMENTS", "$earned / $total", progress = earned.toFloat() / total) else null,
    HeaderStat("LAST PLAYED", lastPlayedAt?.let { lastPlayedLabel(it) } ?: "Never"),
    beatHours?.let { HeaderStat("TIME TO BEAT", formatHours(it)) },
    score?.let { HeaderStat("SCORE", it.toString(), valueColor = when { it >= 75 -> com.shelf.launcher.ui.theme.Ds.success; it >= 50 -> com.shelf.launcher.ui.theme.Ds.warning; else -> com.shelf.launcher.ui.theme.Ds.danger }) }
)

/**
 * Settings › Home › Next to the row › Game header: on / off, which parts it shows, its size, and moving it
 * (Edit Home: drag it anywhere, the corner handle or L2 / R2 resize it).
 */
@Composable
internal fun HomeHeaderSettings() {
    val f = com.shelf.launcher.data.Features
    SwitchRow(
        Icons.Filled.PlayArrow, "Game header",
        "The game you're on as a big title with stat boxes and a Play pill with round buttons, above the row (Voltcap row layout). Off: just the name line.",
        f.homeHeader.state.value, onChange = { f.homeHeader.value = it }
    )
    if (!f.homeHeader.state.value) return
    Spacer(Modifier.height(4.dp))
    SwitchRow(
        Icons.Filled.PlayArrow, "Tight layout",
        "The category above the game's title, small gaps, and the score, save sync and game options small at the right of the title. Off: the roomier layout.",
        f.homeHeaderTight.state.value, onChange = { f.homeHeaderTight.value = it }
    )
    Spacer(Modifier.height(8.dp))
    SectionLabel("What it shows")
    MutedNote("Tap to switch a part on or off.")
    Spacer(Modifier.height(6.dp))
    val hidden = f.homeHeaderHidden.state.value
    ChipFlow {
        HOME_HEADER_PARTS.forEach { (id, label) ->
            val on = id !in hidden
            Chip((if (on) "✓ " else "") + label, accent = on) { f.homeHeaderHidden.toggle(id) }
        }
    }
    Spacer(Modifier.height(10.dp))
    val scales = HomeWidgets.parseScales(f.widgetScales.state.value)
    val pct = ((scales[HomeWidgets.HEADER] ?: 1f) * 100).toInt()
    PercentSlider(
        "Size", pct, { v ->
            val m = HomeWidgets.parseScales(f.widgetScales.value) + (HomeWidgets.HEADER to v / 100f)
            f.widgetScales.value = HomeWidgets.formatScales(m)
        },
        min = (HomeWidgets.SCALE_MIN * 100).toInt(), max = (HomeWidgets.SCALE_MAX * 100).toInt(), valueText = { "$it %" }
    )
    Spacer(Modifier.height(6.dp))
    MutedNote("To move it anywhere on the screen or size it by hand: Edit Home — drag it, pull its corner handle (or L2 / R2).")
    Spacer(Modifier.height(6.dp))
    ChipFlow {
        Chip("Edit Home…") { HomeEditState.open(); HomeEditState.selected = HomeWidgets.HEADER }
        Chip("Back to its place", accent = false, enabled = HomeWidgets.position(HomeWidgets.HEADER) != null || pct != 100) {
            HomeWidgets.reset(HomeWidgets.HEADER)
            f.widgetScales.value = HomeWidgets.formatScales(HomeWidgets.parseScales(f.widgetScales.value) - HomeWidgets.HEADER)
        }
    }
}
