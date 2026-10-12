package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.shownIn
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.NowPlaying
import com.shelf.launcher.data.RaAchievementDetail
import com.shelf.launcher.data.RaBadges
import com.shelf.launcher.data.SecondPage
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * The second screen in the Volt look: a header with the Voltcap mark, underlined page tabs and the clock
 * over a voltage line; soft accent light in the corners; and an achievements board (filters
 * with counts · the list · the picked achievement in detail) on screens wide enough for it.
 */

/** Soft accent light from two corners behind the second screen's pages (static: no cost per frame). */
internal fun Modifier.voltAmbient(accent: Color, second: Color = accent): Modifier = this.drawBehind {
    val r = maxOf(size.width, size.height)
    drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.16f), Color.Transparent), center = Offset(0f, 0f), radius = r * 0.7f))
    drawRect(Brush.radialGradient(listOf(second.copy(alpha = 0.11f), Color.Transparent), center = Offset(size.width, size.height), radius = r * 0.6f))
}

/** The header: ⚡ VOLTCAP, the page tabs, the clock, then the voltage line. */
@Composable
internal fun VoltSecondHeader(
    page: SecondPage,
    pages: List<SecondPage>,
    onPage: (SecondPage) -> Unit,
    nowPlaying: NowPlaying?,
    /** Only the current page's name, with arrows to the others ([PageSwitcher]). */
    titleOnly: Boolean = false,
    onStep: (Int) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(60_000L - System.currentTimeMillis() % 60_000L + 50L) }
    }
    val time = remember(now) { android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(now)) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            VoltNode(Modifier.size(width = 16.dp, height = 26.dp))
            Spacer(Modifier.width(8.dp))
            Text("VOLTCAP", color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.width(10.dp))
            if (titleOnly) {
                // The current page, underlined like the tabs, between two quiet arrows.
                PageSwitcher(page, pages.size, onStep, Modifier.weight(1f)) {
                    VoltTabs(labels = listOf(page.label), selected = 0) { }
                }
            } else VoltTabs(
                labels = pages.map { it.label },
                selected = pages.indexOf(page),
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())
            ) { i -> pages.getOrNull(i)?.let(onPage) }
            Text(time, color = ShelfTheme.textMuted, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().height(5.dp)) {
            VoltageLine(Modifier.fillMaxWidth().align(Alignment.Center))
        }
    }
}

private enum class BoardFilter(val label: String) { ALL("All"), UNLOCKED("Unlocked"), LOCKED("Locked"), MISSABLE("Missable"), RARE("Rare") }

private fun BoardFilter.pick(list: List<RaAchievementDetail>) = when (this) {
    BoardFilter.ALL -> list
    BoardFilter.UNLOCKED -> list.filter { it.isUnlocked }
    BoardFilter.LOCKED -> list.filter { !it.isUnlocked }
    BoardFilter.MISSABLE -> list.filter { it.isMissable }
    BoardFilter.RARE -> list.filter { (it.unlockPercent ?: 100f) < 10f }
}

/**
 * The achievements board: progress on top, then filters with counts, the list (the picked one outlined in
 * the accent) and the picked achievement large — badge, what it asks, points, when you got it and how many
 * players have. Narrow screens stack the detail under the list. Touch only, like the rest of this screen.
 */
@Composable
internal fun VoltAchievementBoard(
    game: Game,
    earned: Int,
    possible: Int,
    awardLabel: String?,
    list: List<RaAchievementDetail>,
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableStateOf(BoardFilter.ALL) }
    val shown = remember(list, filter) { filter.pick(list) }
    var pickedId by remember(game.stableKey) { mutableStateOf<Int?>(null) }
    val pickedOnly = shown.firstOrNull { it.id == pickedId }
    val picked = pickedOnly ?: shown.firstOrNull()
    Column(modifier.fillMaxSize()) {
        // Progress.
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(gameDisplayName(game.name), color = ShelfTheme.colors.textPrimary, fontSize = 16.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(16.sp), maxFontSize = 16.sp), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(game.platform, color = ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            if (possible > 0) {
                Text("$earned", color = ShelfTheme.accent, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text(" / $possible", color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 5.dp))
            }
            if (awardLabel != null) {
                Spacer(Modifier.width(10.dp))
                Text(
                    awardLabel.uppercase(), color = onAccentColor(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 6.dp).clip(uiRounded(50)).background(voltFill()).padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        if (possible > 0) {
            Spacer(Modifier.height(6.dp))
            VoltProgressBar(earned.toFloat() / possible)
        }
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val wide = maxWidth >= 520.dp
            val detailW = (maxWidth * 0.32f).coerceAtMost(260.dp)
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Filters with counts.
                    Column(Modifier.width(132.dp).fillMaxHeight().voltSurface(uiRounded(14.dp)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BoardFilter.values().forEach { f ->
                            val count = f.pick(list).size
                            val on = f == filter
                            Row(
                                Modifier.fillMaxWidth().clip(uiRounded(10.dp))
                                    .background(if (on) voltFill() else androidx.compose.ui.graphics.SolidColor(Color.Transparent))
                                    .clickable { filter = f; pickedId = null }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(f.label, color = if (on) onAccentColor() else ShelfTheme.colors.textPrimary, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text("$count", color = if (on) onAccentColor() else ShelfTheme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    BoardList(shown, picked?.id, { pickedId = it }, Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.width(detailW).fillMaxHeight().voltSurface(uiRounded(14.dp))) {
                        if (picked != null) AchievementDetail(picked, Modifier.fillMaxSize())
                        else Placeholder2("Nothing in this filter.")
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BoardFilter.values().forEach { f ->
                            Chip("${f.label} ${f.pick(list).size}", accent = f == filter) { filter = f; pickedId = null }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Narrow screens: the whole height is the list; tap one to open its details right under it
                    // (tap again to close) — a fixed panel at the bottom used to hide most of the list.
                    BoardList(
                        shown, pickedOnly?.id, { id -> pickedId = if (pickedId == id) null else id },
                        Modifier.fillMaxWidth().weight(1f), inlineDetail = true
                    )
                }
            }
        }
    }
}

@Composable
private fun Placeholder2(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = ShelfTheme.textMuted, fontSize = 12.sp) }
}

/** A thicker progress bar in the accent with a bright tip. */
@Composable
internal fun VoltProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val fill = voltFill()
    val f = fraction.coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f))) {
        Box(
            Modifier.fillMaxWidth(f).fillMaxHeight().clip(CircleShape)
                .background(fill)
                .drawBehind { if (f > 0f) drawCircle(Color.White.copy(alpha = 0.85f), radius = size.height * 0.28f, center = Offset(size.width - size.height / 2, size.height / 2)) }
        )
    }
}

@Composable
private fun BoardList(shown: List<RaAchievementDetail>, pickedId: Int?, onPick: (Int) -> Unit, modifier: Modifier, inlineDetail: Boolean = false) {
    if (shown.isEmpty()) { Box(modifier) { Placeholder2("Nothing in this filter.") }; return }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(shown, key = { it.id }) { a ->
            val on = a.id == pickedId
            val shape = uiRounded(12.dp)
            Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth()
                    .voltSurface(shape, alpha = if (a.isUnlocked) 1f else 0.7f)
                    .border(if (on) 1.5.dp else 0.dp, if (on) ShelfTheme.accent else Color.Transparent, shape)
                    .clickable { onPick(a.id) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = a.badgeName?.let { if (a.isUnlocked) RaBadges.url(it) else RaBadges.url(it + "_lock") },
                    contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(uiRounded(8.dp)).alpha(if (a.isUnlocked) 1f else 0.7f)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title.orEmpty(), color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(a.description.orEmpty(), color = ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (a.isMissable) Icon(Icons.Filled.Warning, "Missable", tint = com.shelf.launcher.ui.theme.Ds.warning, modifier = Modifier.padding(horizontal = 4.dp).size(14.dp))
                a.points?.let { Text("$it", color = ShelfTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
            if (inlineDetail && on) {
                Spacer(Modifier.height(4.dp))
                // Bounded height: the detail scrolls inside it (a list item can't hold an endless scroller).
                Box(Modifier.fillMaxWidth().heightIn(max = 240.dp).voltSurface(uiRounded(12.dp))) {
                    AchievementDetail(a, Modifier.fillMaxWidth(), compact = true)
                }
            }
            }
        }
    }
}

/** The picked achievement, large. */
@Composable
private fun AchievementDetail(a: RaAchievementDetail, modifier: Modifier, compact: Boolean = false) {
    val unlocked = a.isUnlocked
    val badge = @Composable { size: androidx.compose.ui.unit.Dp ->
        Box(
            Modifier.size(size).clip(uiRounded(14.dp))
                .border(if (unlocked) 2.dp else 1.dp, if (unlocked) ShelfTheme.accent else Color.White.copy(alpha = 0.15f), uiRounded(14.dp))
        ) {
            AsyncImage(
                model = a.badgeName?.let { if (unlocked) RaBadges.url(it) else RaBadges.url(it + "_lock") },
                contentDescription = null, modifier = Modifier.fillMaxSize()
            )
        }
    }
    val facts = @Composable {
        Text(a.title.orEmpty(), color = ShelfTheme.colors.textPrimary, fontSize = 15.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(15.sp), maxFontSize = 15.sp), fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(a.description.orEmpty(), color = ShelfTheme.textMuted, fontSize = 12.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            a.points?.let { Text("$it pts", color = ShelfTheme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp)) }
            if (a.isMissable) Text("MISSABLE", color = com.shelf.launcher.ui.theme.Ds.warning, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(4.dp))
        val date = (a.dateEarnedHardcore ?: a.dateEarned)?.take(10)
        Text(
            if (date != null) "Unlocked $date" + if (a.dateEarnedHardcore != null) " · hardcore" else "" else "Locked",
            color = if (date != null) com.shelf.launcher.ui.theme.Ds.success else ShelfTheme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
        )
        a.unlockPercent?.let { pct ->
            Spacer(Modifier.height(6.dp))
            Text("${"%.1f".format(pct)} % of players have it", color = ShelfTheme.colors.textPrimary, fontSize = 11.sp)
            Spacer(Modifier.height(3.dp))
            VoltProgressBar(pct / 100f)
        }
    }
    if (compact) {
        Row(modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
            badge(64.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { facts() }
        }
    } else {
        Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            badge(96.dp)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth()) { facts() }
        }
    }
}

/**
 * Art page › "Cartridge + details": the real cartridge / disc (else the box, else the cover) on the left, and
 * on the right the console, the name, year · developer · genres, and the game's story — over its blurred art.
 */
@Composable
internal fun ShowcaseArt(info: SecondHomeGame, modifier: Modifier = Modifier) {
    val game = info.game ?: return
    val media = info.discUrl ?: info.caseUrl ?: info.coverUrl
    // Drawn cartridge / disc wearing the game's label (else its cover), when the console has one.
    val shell = if (mediaShellsOn) MediaShell.of(game.platform) else null
    val meta = info.metadata.shownIn(com.shelf.launcher.data.Features.factsSecond.state.value)
    Box(modifier.fillMaxSize().clip(uiRounded(18.dp))) {
        (info.heroUrl ?: info.wideUrl)?.let { url ->
            // Blurred once (update 121), not on every redraw of the page over it.
            BakedBlurImage(url, 18.dp, Modifier.fillMaxSize(), alpha = 0.55f)
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.72f)))))
        BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
            val mediaW = (maxWidth * 0.36f).coerceAtMost(maxHeight * 0.9f)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(mediaW).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (shell != null) PhysicalMedia(shell, info.labelUrl ?: info.coverUrl, Modifier.fillMaxSize(), platform = game.platform, labelIsScan = info.labelUrl != null)
                    else if (media != null) AsyncImage(
                        model = media, contentDescription = game.name,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().pixelArtwork()
                    ) else if (game.sourceType == com.shelf.launcher.data.SourceType.APP) {
                        AppIconArt(game.packageName, wide = false, modifier = Modifier.size(mediaW * 0.7f))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                    Text(
                        com.shelf.launcher.data.ConsoleNames.display(game.platform).uppercase(),
                        color = ShelfTheme.accent, fontSize = 10.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(10.sp), maxFontSize = 10.sp), fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(gameDisplayName(game.name), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
                    val facts = listOfNotNull(
                        meta?.releaseYear?.toString(), meta?.developer, meta?.publisher, meta?.genres?.take(2)?.joinToString(", ")?.takeIf { it.isNotBlank() },
                        info.playtimeMs?.takeIf { it > 0 }?.let { "Played ${com.shelf.launcher.data.PlaytimeStore.format(it)}" }
                    )
                    if (facts.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(facts.joinToString("  ·  "), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                    if (VoltUi.on) { Spacer(Modifier.height(8.dp)); VoltageLine(Modifier.width(120.dp)) }
                    Spacer(Modifier.height(8.dp))
                    when {
                        meta?.summary != null -> Text(meta.summary, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, lineHeight = 17.sp)
                        info.metadataLoading -> Text("Loading the story…", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        else -> Text("No description yet (details come from your details sources, Settings › Accounts).", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

