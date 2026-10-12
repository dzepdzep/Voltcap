package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.AchievementLook
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One square on the badge wall (RetroAchievements or Steam). */
internal data class BadgeItem(
    val key: Any,
    val image: String?,
    val title: String,
    /** Share of players who have it (0-100), when known. */
    val percent: Float?,
    val unlocked: Boolean,
    val points: Int? = null,
    val missable: Boolean = false,
    /** Shown under the name when there's no percent ("12 Mar 2026"). */
    val note: String? = null,
    /** Unlocked since the last look: glows until the list has been viewed. */
    val isNew: Boolean = false
)

/** How many badges fly in at most (the rest are simply there — a 300-badge set would take too long). */
private const val FLY_MAX = 36

/**
 * Achievements as a wall of square "gym badges": the RA / Steam badge picture in a framed square (gold
 * edge when unlocked, greyed with a lock when not), its name on two lines and the unlock %. When
 * [AchievementLook.flyIn] is on, the badges swoop in from alternating sides and lock into place one after
 * another — a retro swoop and a star pop for each unlocked one (a softer glide for locked) when
 * [AchievementLook.sounds] is on. [animKey] restarts the animation (the game, the filter).
 * [below] draws under the row holding [selected] (the inline details).
 */
@Composable
internal fun BadgeWall(
    items: List<BadgeItem>,
    animKey: Any,
    selected: Any?,
    onClick: (BadgeItem) -> Unit,
    modifierFor: (BadgeItem) -> Modifier = { Modifier },
    below: @Composable (BadgeItem) -> Unit = {}
) {
    val context = LocalContext.current
    val sounds = LocalUiSounds.current
    // "Animate only new unlocks" (Appearance › Achievements) skips the entrance for the whole wall.
    val animate = AchievementLook.flyIn.value && !com.shelf.launcher.data.Features.animateOnlyNew.state.value
    val withSound = AchievementLook.sounds.value
    val anims = remember(animKey) { HashMap<Int, Animatable<Float, *>>() }
    // Once the fly-in is over, badges that turn up later (the list refreshed) are simply there.
    val flown = remember(animKey) { booleanArrayOf(false) }
    val currentItems by androidx.compose.runtime.rememberUpdatedState(items)
    fun anim(i: Int) = anims.getOrPut(i) { Animatable(if (animate && i < FLY_MAX && !flown[0]) 0f else 1f) }
    LaunchedEffect(animKey) {
        if (!animate) return@LaunchedEffect
        if (withSound) RetroFx.warm(context)
        delay(120)
        var i = 0
        while (i < minOf(currentItems.size, FLY_MAX)) {
            val a = anim(i)
            val unlocked = currentItems[i].unlocked
            launch {
                a.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = 320f))
            }
            launch {
                // Lands about 230 ms after it sets off.
                delay(230)
                if (withSound) RetroFx.play(context, sounds, if (unlocked) RetroSound.LOCK else RetroSound.GLIDE, pitch = 1f + (i % 5) * 0.05f)
            }
            if (withSound && i % 4 == 0) RetroFx.play(context, sounds, RetroSound.FLY, pitch = 0.9f + (i % 3) * 0.1f)
            delay(if (i < 6) 85 else 55)
            i++
        }
        delay(700)
        flown[0] = true
        // Anything still waiting (the list changed while they flew) just appears.
        anims.values.forEach { if (it.targetValue < 1f && !it.isRunning) it.snapTo(1f) }
    }
    val style = achievementStyle()
    val showName = com.shelf.launcher.data.Features.badgeName.state.value
    val showPct = com.shelf.launcher.data.Features.badgePercent.state.value
    val showCount = com.shelf.launcher.data.Features.badgeCount.state.value
    val textLines = (if (showName) 2 else 0) + (if (showPct || showCount) 1 else 0)
    // Hidden text: bigger badges in the room it leaves (fewer, larger columns; tighter rows).
    val minCell = when (textLines) { 0 -> 128.dp; 1 -> 116.dp; 2 -> 110.dp; else -> 104.dp }
    // Manual picks pop (separate from the automatic NEW animation).
    val pops = remember(animKey) { HashMap<Any, androidx.compose.runtime.MutableIntState>() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = (maxWidth / minCell).toInt().coerceIn(if (textLines == 0) 2 else 3, 8)
        Column(verticalArrangement = Arrangement.spacedBy(if (textLines == 0) 6.dp else 10.dp)) {
            items.chunked(cols).forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { colIndex, item ->
                        val index = rowIndex * cols + colIndex
                        val a = anim(index)
                        // Alternating directions: left, right, top, bottom.
                        val dir = (index * 7 + rowIndex) % 4
                        val pop = pops.getOrPut(item.key) { androidx.compose.runtime.mutableIntStateOf(0) }
                        BadgeTile(
                            item,
                            selected = item.key == selected,
                            style = style, showName = showName, showPct = showPct, showCount = showCount,
                            modifier = Modifier
                                .weight(1f)
                                .badgePop(pop.intValue, style.amount)
                                .graphicsLayer {
                                    val p = a.value
                                    val off = 1f - p
                                    translationX = when (dir) { 0 -> -900f * off; 1 -> 900f * off; else -> 0f }
                                    translationY = when (dir) { 2 -> -500f * off; 3 -> 500f * off; else -> 0f }
                                    rotationZ = off * if (dir % 2 == 0) -30f else 30f
                                    val s = 0.55f + 0.45f * p
                                    scaleX = s; scaleY = s
                                    alpha = (p * 2.5f).coerceIn(0f, 1f)
                                }
                                .then(modifierFor(item)),
                            onClick = { pop.intValue++; onClick(item) }
                        )
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
                row.firstOrNull { it.key == selected }?.let { below(it) }
            }
        }
    }
}

private val greyscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
private fun BadgeTile(
    item: BadgeItem,
    selected: Boolean,
    style: AchievementStyle,
    showName: Boolean,
    showPct: Boolean,
    showCount: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) ShelfTheme.accent.copy(alpha = 0.18f) else Color.Transparent)
            .shelfClickable(shape) { onClick() }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val frame = style.shape
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                // NEW: a soft glow round the frame until the list has been looked at.
                .then(
                    if (item.isNew) Modifier.border(3.dp, style.glow.copy(alpha = 0.55f), frame).padding(2.dp) else Modifier
                )
                .clip(frame)
                .background(Brush.linearGradient(if (item.unlocked) style.plateOn else style.plateOff))
                .border(if (item.unlocked) 2.dp else 1.dp, if (item.unlocked) style.frameOn else style.frameOff, frame)
                .padding(7.dp),
            contentAlignment = Alignment.Center
        ) {
            if (item.image != null) {
                AsyncImage(
                    model = item.image, contentDescription = item.title, contentScale = ContentScale.Crop,
                    colorFilter = if (item.unlocked) null else greyscale,
                    alpha = if (item.unlocked) 1f else 0.45f,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(style.radius * 0.6f))
                )
            } else {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f)))
            }
            // A shine across the top-left of unlocked badges.
            if (item.unlocked) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.22f), 0.35f to Color.Transparent, 1f to Color.Transparent
                        )
                    )
                )
            } else {
                Icon(Icons.Filled.Lock, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(18.dp).align(Alignment.BottomEnd))
            }
            if (item.missable) {
                Box(
                    Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(50)).background(Color(0xFFE0533D)).padding(horizontal = 4.dp, vertical = 1.dp)
                ) { Text("!", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black) }
            }
            if (item.isNew) {
                Box(
                    Modifier.align(Alignment.TopStart).clip(RoundedCornerShape(50)).background(style.glow).padding(horizontal = 5.dp, vertical = 1.dp)
                ) { Text("NEW", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Black) }
            }
        }
        if (showName) {
            Spacer(Modifier.height(5.dp))
            // Long names wrap to three lines and shrink a little instead of being cut off.
            FitText(
                item.title, color = if (item.unlocked) style.text else ShelfTheme.textMuted,
                maxSize = 11.sp, minSize = 8.sp, maxLines = 3, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().heightIn(min = 26.dp), style = uiFontStyle().merge(androidx.compose.ui.text.TextStyle(fontFamily = style.font))
            )
        }
        // Percentage (share of players) and the count / points line, each switchable.
        val pct = if (showPct) item.percent?.let { p -> if (p < 1f && p > 0f) "<1 %" else "${p.toInt()} %" } else null
        val count = if (showCount) item.points?.let { "$it pts" } ?: item.note else null
        val sub = listOfNotNull(pct, count).joinToString(" · ").ifEmpty { null }
        if (sub != null) {
            if (!showName) Spacer(Modifier.height(4.dp))
            Text(
                sub,
                color = if (item.percent != null && item.percent < 5f && pct != null) GOLD else ShelfTheme.textMuted,
                fontSize = 10.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(10.sp), maxFontSize = 10.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontFamily = style.font
            )
        }
    }
}

private val GOLD = Color(0xFFF2C14E)
