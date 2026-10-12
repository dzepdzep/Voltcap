package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.RaAchievementDetail
import com.shelf.launcher.data.RaBadges
import com.shelf.launcher.ui.theme.Ds
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * The achievement views (update 84). Per game: "Power cells" (Voltcap's own, the default), "Trophy list" (in the
 * manner of a console's trophy list: big badge, tier, rarity line, date, the description beside it), and the older
 * Badges wall / plain List. Across games (the Achievements screen): "Power cells", "Scorecards" (tiles with two
 * progress rings and the percentage, summary cards on top) and the plain list. Settings › Achievements and the chips
 * on each screen switch them.
 */

/** RetroAchievements has no tiers: one is made from the points (Gold 25+, Silver 10+, Bronze below). */
internal enum class AchTier(val label: String, val color: Color) {
    BRONZE("Bronze", Color(0xFFC07A4A)), SILVER("Silver", Color(0xFFB9C2CF)), GOLD("Gold", Color(0xFFE8BE48)), PLATINUM("Platinum", Color(0xFF9FD8F2));
    companion object {
        fun of(points: Int?): AchTier = when {
            (points ?: 0) >= 25 -> GOLD
            (points ?: 0) >= 10 -> SILVER
            else -> BRONZE
        }
    }
}

/** How rare an achievement is, from the share of players who have it. */
internal enum class AchRarity(val label: String, val color: Color) {
    COMMON("Common", Color(0xFF9AA3AE)), UNCOMMON("Uncommon", Color(0xFF5FC27E)), RARE("Rare", Color(0xFF4FA3F0)),
    VERY_RARE("Very rare", Color(0xFFB07CF2)), ULTRA_RARE("Ultra rare", Color(0xFFF2C14E));
    companion object {
        fun of(pct: Float?): AchRarity? = when {
            pct == null -> null
            pct >= 50f -> COMMON
            pct >= 20f -> UNCOMMON
            pct >= 10f -> RARE
            pct >= 5f -> VERY_RARE
            else -> ULTRA_RARE
        }
    }
}

private fun pctText(p: Float): String = when {
    p <= 0f -> "0%"
    p < 1f -> "<1%"
    p < 10f -> String.format(java.util.Locale.US, "%.1f%%", p)
    else -> String.format(java.util.Locale.US, "%.1f%%", p)
}

/** "2026-05-18 10:11:00" → "18/5/2026  10:11". */
private fun earnedText(a: RaAchievementDetail): String? {
    val raw = a.dateEarnedHardcore ?: a.dateEarned ?: return null
    return runCatching {
        // Update 144: RetroAchievements dates are UTC; shown in the handheld's own time, like the detail view.
        val d = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(raw.trim()) ?: return raw
        java.text.SimpleDateFormat("d/M/yyyy  HH:mm", java.util.Locale.getDefault()).format(d)
    }.getOrDefault(raw)
}

/** Grey for achievements not unlocked yet — used everywhere a badge is drawn. */
internal val GreyMatrix = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

// ---------------------------------------------------------------- per game: headers

/** Power cells: the set as a battery charging up — the percentage, earned / total, points and how many rare ones. */
@Composable
internal fun PowerHeader(list: List<RaAchievementDetail>) {
    val total = list.size.coerceAtLeast(1)
    val unlocked = list.count { it.isUnlocked }
    val frac = unlocked.toFloat() / total
    val pts = list.filter { it.isUnlocked }.sumOf { it.points ?: 0 }
    val allPts = list.sumOf { it.points ?: 0 }
    val rare = list.count { it.isUnlocked && (it.unlockPercent ?: 100f) < 10f }
    val accent = ShelfTheme.accent
    val full = unlocked >= list.size && list.isNotEmpty()
    val charge = if (full) Ds.gold else accent
    Column(Modifier.fillMaxWidth().clip(uiRounded(16.dp)).background(Color.White.copy(alpha = 0.05f)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The battery: ten cells, the filled ones lit, a cap at the right.
            Canvas(Modifier.weight(1f).height(34.dp)) {
                val capW = size.height * 0.22f
                val bodyW = size.width - capW - 4f
                val r = CornerRadius(size.height * 0.22f)
                drawRoundRect(Color.White.copy(alpha = 0.08f), size = Size(bodyW, size.height), cornerRadius = r)
                drawRoundRect(Color.White.copy(alpha = 0.35f), size = Size(bodyW, size.height), cornerRadius = r, style = Stroke(2.5f))
                drawRoundRect(Color.White.copy(alpha = 0.35f), topLeft = Offset(bodyW + 2f, size.height * 0.3f), size = Size(capW, size.height * 0.4f), cornerRadius = CornerRadius(3f))
                val cells = 10
                val gap = 4f
                val cw = (bodyW - 10f - gap * (cells - 1)) / cells
                for (i in 0 until cells) {
                    val lit = (i + 1) <= (frac * cells + 0.0001f).toInt() || (i < frac * cells)
                    val x = 5f + i * (cw + gap)
                    val cellFrac = ((frac * cells) - i).coerceIn(0f, 1f)
                    drawRoundRect(Color.White.copy(alpha = 0.06f), topLeft = Offset(x, 5f), size = Size(cw, size.height - 10f), cornerRadius = CornerRadius(4f))
                    if (lit && cellFrac > 0f) drawRoundRect(
                        Brush.verticalGradient(listOf(charge, charge.copy(alpha = 0.65f))),
                        topLeft = Offset(x, 5f), size = Size(cw * cellFrac, size.height - 10f), cornerRadius = CornerRadius(4f)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text("${(frac * 100).toInt()}%", color = charge, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PowerStat("Unlocked", "$unlocked / ${list.size}", Modifier.weight(1f))
            PowerStat("Points", "$pts / $allPts", Modifier.weight(1f))
            PowerStat("Rare", rare.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun PowerStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(uiRounded(10.dp)).background(Color.Black.copy(alpha = 0.25f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(label.uppercase(), color = ShelfTheme.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, softWrap = false)
        FitText(value, color = ShelfTheme.colors.textPrimary, maxSize = 16.sp, minSize = 10.sp, maxLines = 1, fontWeight = FontWeight.Bold)
    }
}

/** Trophy list: the game's icon and name, then Progress, Earned and the tier counts at the right. */
@Composable
internal fun TrophyHeader(icon: String?, title: String, list: List<RaAchievementDetail>) {
    val unlocked = list.filter { it.isUnlocked }
    val frac = if (list.isEmpty()) 0f else unlocked.size.toFloat() / list.size
    val full = list.isNotEmpty() && unlocked.size == list.size
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < 520.dp
        val left: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) AsyncImage(icon, null, contentScale = ContentScale.Crop, modifier = Modifier.size(34.dp).clip(uiRounded(6.dp)))
                Spacer(Modifier.width(10.dp))
                FitText(title, color = ShelfTheme.colors.textPrimary, maxSize = 22.sp, minSize = 14.sp, maxLines = 2, fontWeight = FontWeight.Light)
            }
        }
        val right: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Filled.WorkspacePremium, null, tint = if (full) AchTier.PLATINUM.color else Color.White.copy(alpha = 0.25f), modifier = Modifier.size(30.dp))
                Column {
                    Text("Progress", color = ShelfTheme.textMuted, fontSize = 11.sp)
                    Text("${(frac * 100).toInt()}%", color = ShelfTheme.colors.textPrimary, fontSize = 18.sp)
                    Box(Modifier.width(54.dp).height(2.dp).background(Color.White.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Color.White))
                    }
                }
                Column {
                    Text("Earned", color = ShelfTheme.textMuted, fontSize = 11.sp)
                    Text("${unlocked.size}/${list.size}", color = ShelfTheme.colors.textPrimary, fontSize = 18.sp)
                }
                listOf(AchTier.GOLD, AchTier.SILVER, AchTier.BRONZE).forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EmojiEvents, null, tint = t.color, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(unlocked.count { AchTier.of(it.points) == t }.toString(), color = ShelfTheme.colors.textPrimary, fontSize = 16.sp)
                    }
                }
            }
        }
        if (narrow) Column { left(); Spacer(Modifier.height(10.dp)); right() }
        else Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f)) { left() }; right() }
    }
}

// ---------------------------------------------------------------- per game: rows

/**
 * Power cells: one achievement as a capsule-shaped cell. Unlocked: lit along its left edge in the accent (gold for
 * the rare ones), its badge in a charged ring, the date; locked: dark, the badge grey with a lock, the ring empty.
 * The rarity is a small voltage bar (fuller = rarer).
 */
@Composable
internal fun PowerCell(
    a: RaAchievementDetail, showRarity: Boolean, missable: Boolean, markedMissed: Boolean, expanded: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val accent = ShelfTheme.accent
    val rarity = if (showRarity) AchRarity.of(a.unlockPercent) else null
    val lit = if (a.isUnlocked) (if ((a.unlockPercent ?: 100f) < 10f) Ds.gold else accent) else Color.White.copy(alpha = 0.12f)
    val shape = uiRounded(40.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (a.isUnlocked) Brush.horizontalGradient(listOf(lit.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.03f)))
                else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.03f)))
            )
            .border(1.dp, if (expanded) accent else lit.copy(alpha = if (a.isUnlocked) 0.55f else 1f), shape)
            .shelfClickable(shape) { onClick() }
            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The badge in its charge ring.
        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.Black.copy(alpha = 0.35f))
                drawArc(
                    lit, -90f, if (a.isUnlocked) 360f else 0f, false,
                    style = Stroke(size.minDimension * 0.07f, cap = StrokeCap.Round)
                )
            }
            val url = RaBadges.url(a.badgeName)
            if (url != null) AsyncImage(
                url, null, contentScale = ContentScale.Crop,
                colorFilter = if (a.isUnlocked) null else GreyMatrix,
                modifier = Modifier.size(44.dp).clip(CircleShape).alpha(if (a.isUnlocked) 1f else 0.55f)
            )
            if (!a.isUnlocked) Icon(Icons.Filled.Lock, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp).align(Alignment.BottomEnd))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FitText(
                    a.title ?: "Achievement", color = if (a.isUnlocked) ShelfTheme.colors.textPrimary else ShelfTheme.colors.textPrimary.copy(alpha = 0.7f),
                    maxSize = 14.sp, minSize = 9.sp, maxLines = 2, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false)
                )
                if (missable) { Spacer(Modifier.width(6.dp)); MissableIcon(markedMissed) }
            }
            a.description?.let {
                Text(it, color = ShelfTheme.textMuted, fontSize = 11.5.sp, lineHeight = 15.sp)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, null, tint = lit.takeIf { a.isUnlocked } ?: ShelfTheme.textMuted, modifier = Modifier.size(13.dp))
                Text("${a.points ?: 0}", color = ShelfTheme.colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (rarity != null) {
                    Spacer(Modifier.width(10.dp))
                    VoltageBar(a.unlockPercent ?: 100f, rarity.color)
                    Spacer(Modifier.width(5.dp))
                    Text("${rarity.label} · ${pctText(a.unlockPercent ?: 0f)}", color = rarity.color, fontSize = 10.5.sp, maxLines = 1, softWrap = false)
                }
                Spacer(Modifier.weight(1f))
                earnedText(a)?.let { Text(it, color = ShelfTheme.textMuted, fontSize = 10.sp, maxLines = 1, softWrap = false) }
            }
        }
    }
}

/** Five little bars: the rarer the achievement, the more are lit. */
@Composable
private fun VoltageBar(pct: Float, color: Color) {
    val lit = when { pct >= 50f -> 1; pct >= 20f -> 2; pct >= 10f -> 3; pct >= 5f -> 4; else -> 5 }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
        for (i in 1..5) Box(
            Modifier.width(3.dp).height((4 + i * 2).dp).clip(uiRounded(1.dp))
                .background(if (i <= lit) color else Color.White.copy(alpha = 0.15f))
        )
    }
}

/** Trophy list: a wide dark row — big badge, name, tier and rarity at the left with the date, the description at the right. */
@Composable
internal fun TrophyRow(
    a: RaAchievementDetail, showRarity: Boolean, missable: Boolean, markedMissed: Boolean, expanded: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val tier = AchTier.of(a.points)
    val rarity = if (showRarity) AchRarity.of(a.unlockPercent) else null
    val shape = uiRounded(14.dp)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth >= 560.dp
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color(0xFF101217).copy(alpha = 0.9f))
                .border(if (expanded) 1.5.dp else 0.dp, if (expanded) ShelfTheme.accent else Color.Transparent, shape)
                .shelfClickable(shape) { onClick() }
                // (No IntrinsicSize here: the fitted title is a SubcomposeLayout, and asking it for its intrinsic
                // height crashed the list on the Retroid Pocket Classic.)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val url = RaBadges.url(a.badgeName)
            Box(Modifier.size(if (wide) 70.dp else 56.dp).clip(uiRounded(14.dp)).background(Color.White.copy(alpha = 0.05f))) {
                if (url != null) AsyncImage(
                    url, null, contentScale = ContentScale.Crop, colorFilter = if (a.isUnlocked) null else GreyMatrix,
                    modifier = Modifier.fillMaxSize().alpha(if (a.isUnlocked) 1f else 0.45f)
                )
                if (!a.isUnlocked) Icon(Icons.Filled.Lock, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp).align(Alignment.Center))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FitText(a.title ?: "Achievement", color = Color.White, maxSize = 17.sp, minSize = 11.sp, maxLines = 2, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                    if (missable) { Spacer(Modifier.width(6.dp)); MissableIcon(markedMissed) }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EmojiEvents, null, tint = tier.color, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${tier.label} · ${a.points ?: 0} pts", color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
                if (rarity != null) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("▲", color = rarity.color, fontSize = 10.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${rarity.label}  |  ${pctText(a.unlockPercent ?: 0f)} of players earned", color = Color.White.copy(alpha = 0.72f),
                            fontSize = 12.sp, maxLines = 1, softWrap = false
                        )
                    }
                }
                if (!wide) a.description?.let { Spacer(Modifier.height(4.dp)); Text(it, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, lineHeight = 16.sp) }
            }
            earnedText(a)?.let { d ->
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(d, color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
            if (wide) {
                Spacer(Modifier.width(14.dp))
                Box(Modifier.width(1.dp).height(56.dp).background(Color.White.copy(alpha = 0.12f)))
                Spacer(Modifier.width(14.dp))
                Text(
                    a.description.orEmpty(), color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp, lineHeight = 17.sp,
                    modifier = Modifier.weight(0.9f)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- across games (the Achievements screen)

/** One game in the Achievements screen's views. */
internal class HubGame(
    val title: String, val icon: String?, val console: String?, val earned: Int, val total: Int, val hardcore: Int,
    val mastered: Boolean, val isNew: Boolean, val onClick: () -> Unit
)

/** Summary on top of Scorecards: mastered games, total unlocked, unlocks this month (and their points). */
@Composable
internal fun ScorecardSummary(mastered: Int, unlocked: Int, monthCount: Int, monthPoints: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard("100% Mastered", mastered.toString(), Icons.Filled.WorkspacePremium, Modifier.weight(1f))
        SummaryCard("Unlocked", unlocked.toString(), Icons.Filled.EmojiEvents, Modifier.weight(1f))
        SummaryCard("This month", "$monthCount  ·  $monthPoints pts", Icons.Filled.CalendarMonth, Modifier.weight(1.3f))
    }
}

@Composable
private fun SummaryCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Column(modifier.background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, color = ShelfTheme.colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = ShelfTheme.colors.textPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            FitText(value, color = ShelfTheme.colors.textPrimary, maxSize = 20.sp, minSize = 11.sp, maxLines = 1, fontWeight = FontWeight.Bold)
        }
    }
}

/** Scorecards: the game's icon at the left; its name, two rings (unlocked / hardcore) with their counts, the percentage. */
@Composable
internal fun ScorecardTile(g: HubGame, modifier: Modifier = Modifier) {
    val accent = Color(0xFFF07A2A)
    val frac = if (g.total > 0) g.earned.toFloat() / g.total else 0f
    val hc = if (g.total > 0) g.hardcore.toFloat() / g.total else 0f
    val shape = uiRounded(4.dp)
    Row(
        modifier.fillMaxWidth().height(96.dp).clip(shape).background(Color.White.copy(alpha = 0.06f)).shelfClickable(shape) { g.onClick() }
    ) {
        Box(Modifier.width(96.dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.3f))) {
            if (g.icon != null) AsyncImage(g.icon, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
            FitText(g.title, color = ShelfTheme.colors.textPrimary, maxSize = 14.sp, minSize = 9.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RingStat(frac, accent, Icons.Filled.EmojiEvents, "${g.earned}/${g.total}")
                Spacer(Modifier.width(8.dp))
                RingStat(hc, accent, Icons.Filled.Bolt, "HC ${g.hardcore}")
                Spacer(Modifier.weight(1f))
                FitText("${(frac * 100).toInt()}%", color = if (g.mastered) Ds.gold else ShelfTheme.colors.textPrimary, maxSize = 24.sp, minSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RingStat(frac: Float, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, caption: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.White.copy(alpha = 0.12f), style = Stroke(4f))
                drawArc(color, -90f, 360f * frac.coerceIn(0f, 1f), false, style = Stroke(4f, cap = StrokeCap.Round))
            }
            Icon(icon, null, tint = ShelfTheme.colors.textPrimary, modifier = Modifier.size(15.dp))
        }
        Text(caption, color = ShelfTheme.textMuted, fontSize = 9.sp, maxLines = 1, softWrap = false)
    }
}

/** Power cells (across games): each game a battery that charges with its achievements; full and gold when mastered. */
@Composable
internal fun PowerGameCell(g: HubGame, modifier: Modifier = Modifier) {
    val accent = ShelfTheme.accent
    val frac = if (g.total > 0) g.earned.toFloat() / g.total else 0f
    val charge = if (g.mastered) Ds.gold else accent
    val shape = uiRounded(18.dp)
    Row(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.horizontalGradient(listOf(charge.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))))
            .border(1.dp, charge.copy(alpha = 0.4f), shape)
            .shelfClickable(shape) { g.onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(52.dp).clip(uiRounded(12.dp)).background(Color.Black.copy(alpha = 0.3f))) {
            if (g.icon != null) AsyncImage(g.icon, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FitText(g.title, color = ShelfTheme.colors.textPrimary, maxSize = 14.sp, minSize = 9.sp, maxLines = 2, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                if (g.isNew) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.clip(uiRounded(50)).background(Ds.gold).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        Text("NEW", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            g.console?.let { Text(it, color = ShelfTheme.textMuted, fontSize = 10.sp, maxLines = 1, softWrap = false) }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A battery of 10 cells.
                Canvas(Modifier.weight(1f).height(14.dp)) {
                    val cells = 10; val gap = 3f
                    val capW = 4f
                    val bw = size.width - capW - 2f
                    val cw = (bw - gap * (cells - 1)) / cells
                    for (i in 0 until cells) {
                        val f = ((frac * cells) - i).coerceIn(0f, 1f)
                        val x = i * (cw + gap)
                        drawRoundRect(Color.White.copy(alpha = 0.1f), topLeft = Offset(x, 0f), size = Size(cw, size.height), cornerRadius = CornerRadius(3f))
                        if (f > 0f) drawRoundRect(charge, topLeft = Offset(x, 0f), size = Size(cw * f, size.height), cornerRadius = CornerRadius(3f))
                    }
                    drawRoundRect(Color.White.copy(alpha = 0.3f), topLeft = Offset(bw + 2f, size.height * 0.3f), size = Size(capW, size.height * 0.4f), cornerRadius = CornerRadius(2f))
                }
                Spacer(Modifier.width(10.dp))
                Text("${g.earned}/${g.total}", color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(8.dp))
                Text("${(frac * 100).toInt()}%", color = charge, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Two columns where there's room, one on narrow screens. */
@Composable
internal fun <T> TwoColumns(items: List<T>, minColumn: Dp = 300.dp, gap: Dp = 8.dp, cell: @Composable (T, Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = if (maxWidth >= minColumn * 2 + gap) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            items.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { cell(it, Modifier.weight(1f)) }
                    if (row.size < cols) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
