package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Features.PartBacking
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.NameBackdrop
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Update 104: the floating game info parts of every layout (Edit Home › + Add): the game's logo and description, and
 * the backing behind the parts — the layout's own colours by default, the theme's, or a Save Vault-like look
 * (capsule, memory card, SD card, floppy label), picked in Layout options › Game info on screen.
 */

/**
 * The game's logo on the art, no backing (its name, big, when it has no logo).
 * Update 325 (Toni: "the logo part in Edit Home — some logos are bigger, some smaller, hard to control where it lines up —
 * centre it"): the part is one fixed size (its width × 140 dp) whatever the logo, and the logo sits in its middle — it
 * was against the left, the part as tall as each logo, so every game's logo stood somewhere else. Where you put the part
 * is now the middle of every logo.
 */
@Composable
internal fun HomeLogoPart(name: String, logo: String?) {
    // (Update 325, Toni: "I can't get the logo lined up with the category — some logos are bigger, some smaller": many logo
    // files have an empty margin, wider on one side than the other, so a logo in the middle of its part still stood off
    // centre and smaller. Cut to what's drawn in it first — TrimToOpaque.)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val model = androidx.compose.runtime.remember(logo) {
        logo?.let { coil.request.ImageRequest.Builder(ctx).data(it).allowHardware(false).transformations(TrimToOpaque).build() }
    }
    Box(Modifier.fillMaxWidth().height(LOGO_PART_H), contentAlignment = Alignment.Center) {
        if (model != null) {
            FadeInImage(
                model, name, contentScale = ContentScale.Fit, alignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                gameDisplayName(name), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, maxLines = 2,
                overflow = TextOverflow.Ellipsis, style = nameTextStyle(NameBackdrop.SHADOW), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** Update 325: the logo parts' one height (see [HomeLogoPart]). */
private val LOGO_PART_H = 140.dp

/** Update 125: RetroAchievements' icon for the game (Edit Home › + Add › Logo (RetroAchievements)); its name until there is one. */
@Composable
internal fun HomeRaLogoPart(name: String, icon: String?) {
    // (Update 325: one fixed size, the icon in its middle — as HomeLogoPart.)
    Box(Modifier.fillMaxWidth().height(LOGO_PART_H), contentAlignment = Alignment.Center) {
        if (icon != null) {
            FadeInImage(
                icon, name, contentScale = ContentScale.Fit, alignment = Alignment.Center,
                modifier = Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
            )
        } else {
            Text(
                gameDisplayName(name), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                overflow = TextOverflow.Ellipsis, style = nameTextStyle(NameBackdrop.SHADOW), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** The game's description (its summary), a few lines. */
/**
 * Update 204: the Category name part on a layout that doesn't draw the category itself — just the name, in the
 * names' own style (Home › Name & logo). Every layout shows it by default (CATEGORY_PART_LAYOUTS); Edit Home moves,
 * sizes or removes it.
 */
@Composable
internal fun CategoryNamePart(label: String, backdrop: NameBackdrop) {
    // (Update 212: a long name ends in "…" instead of running over the screen.)
    Text(
        label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        overflow = TextOverflow.Ellipsis, style = nameTextStyle(backdrop), modifier = Modifier.widthIn(max = 420.dp)
    )
}

/**
 * Update 247 (#94, RP Classic): where the game's logo part (Edit Home › Logo) starts when you haven't moved it. The top-left
 * corner, except in Side menu, where that corner is the menu panel: the logo lay over its emblem and first entry. There it
 * starts at the top right, over the game's art.
 */
internal fun logoPartSpot(layout: HomeLayout?): Pair<Float, Float> = when (layout) {
    HomeLayout.SIDE -> 1f to 0.1f
    else -> 0f to 0.1f
}

/**
 * Update 204: where the Category name part starts on [layout] (fractions of the free room across and down, as
 * MovableWidget takes them) — a spot that layout leaves free, so it overlaps nothing until you move it.
 */
internal fun categoryPartSpot(layout: HomeLayout): Pair<Float, Float> = when (layout) {
    // TV room: over the TV's top-left corner (its games and console sticker are down the right).
    HomeLayout.TV -> 0f to 0f
    // Case stacks: above the stage on the left, under the tool strip.
    HomeLayout.STACKS -> 0f to 0.16f
    // Card binder, Hero tiles, Twin icons: the top-left corner (the status pill / clock is at the right).
    HomeLayout.CARDS, HomeLayout.HERO_TILES, HomeLayout.TWIN_ICONS, HomeLayout.TWIN_SHOWCASE -> 0f to 0f
    // Console hub: on the art at the left, under the profile / icons / clock row.
    HomeLayout.HUB -> 0f to 0.2f
    // Resume & discover: in the middle of the top row, between the profile and the clock.
    // (Update 247, #94, RP Classic: a little lower — on the narrower screen the status bar's date and clock reach the
    // middle of the top row, and the name sat on them.)
    HomeLayout.RESUME -> 0.5f to 0.07f
    // Hero carousel: along the top, right of the covers down the left and clear of the status bar at the right.
    // (Update 247, #94, RP Classic: a little lower — on the narrower screen the status bar's date reaches past the
    // middle of the top row, and the name sat on it.)
    HomeLayout.HERO -> 0.35f to 0.07f
    // Update 220 (#94): Spotlight — its own profile, icons and clock fill the top row, so the top-left corner put the
    // name over the profile picture. Left, in the free band: under the tiles' names (tiles on top) or above the tiles
    // (tiles at the bottom).
    HomeLayout.SPOT -> 0f to (if (com.shelf.launcher.data.Features.spotTilesAt.state.value == com.shelf.launcher.data.Features.SpotSide.BOTTOM) 0.25f else 0.75f)
    // Update 220 (#94, the RP Classic video): Icon bar with its shortcut tabs — the name sat in the top-left corner over
    // "All apps · Search · …" under the status bar. Left, between the row of tiles and the game's title.
    // (Update 235, #08: with the games at the bottom the free band is high up, under the tabs, above the title.)
    // (0.6.17, Odin 2 Portal: at 0.42 the game you're on — drawn bigger — reached down over the name; a little lower.)
    HomeLayout.BAR -> 0f to (if (com.shelf.launcher.data.Features.barGamesAt.state.value == com.shelf.launcher.data.Features.SpotSide.BOTTOM) 0.18f else 0.5f)
    // Update 247 (#94, RP Classic): Green Core — its own "GREEN CORE" title and line of counts fill the top-left corner;
    // under them, above the globe.
    HomeLayout.GREENCORE -> 0f to 0.19f
    // Anywhere else it's added by hand: the top-left corner.
    else -> 0f to 0f
}

@Composable
internal fun HomeDescriptionPart(game: com.shelf.launcher.data.Game, summary: String?) {
    Text(
        summary?.takeIf { it.isNotBlank() } ?: "No description for ${gameDisplayName(game.name)} yet.",
        color = Color.White.copy(alpha = if (summary.isNullOrBlank()) 0.6f else 0.92f), fontSize = 13.sp, lineHeight = 18.sp,
        maxLines = 6, overflow = TextOverflow.Ellipsis, style = nameTextStyle(NameBackdrop.SHADOW)
    )
}

/**
 * Update 116: the achievements card (Edit Home › + Add › Achievements): earned / total with a bar, the points, and
 * the badges of the next ones still to get (the rarest unlocked one when everything is done). Nothing for a game
 * without RetroAchievements.
 */
@Composable
internal fun HomeAchievementsPart(
    game: com.shelf.launcher.data.Game,
    list: List<com.shelf.launcher.data.RaAchievementDetail>,
    progress: com.shelf.launcher.data.RaGameProgress?
) {
    val total = if (list.isNotEmpty()) list.size else progress?.NumPossibleAchievements ?: 0
    val earned = if (list.isNotEmpty()) list.count { it.isUnlocked } else progress?.NumAchieved ?: 0
    androidx.compose.foundation.layout.Column {
        Text("ACHIEVEMENTS", color = ShelfTheme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        // Update 144: if / else instead of an early return out of the Column (that crashes this Compose compiler).
        if (game.raGameId == null || total <= 0) {
            Text("No achievements for this game.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, style = nameTextStyle(NameBackdrop.SHADOW))
        } else {
            val pts = list.filter { it.isUnlocked }.sumOf { it.points ?: 0 }
            val all = list.sumOf { it.points ?: 0 }
            Text(
                "$earned / $total" + (if (all > 0) "  ·  $pts / $all points" else ""),
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = nameTextStyle(NameBackdrop.SHADOW)
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 4.dp))
            Box(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f))) {
                // (Update 136: a real height: with only padding the fill had none, so the bar always looked empty.)
                Box(Modifier.fillMaxWidth((earned.toFloat() / total).coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(50)).background(ShelfTheme.accent))
            }
            val next = list.filter { !it.isUnlocked }.sortedBy { it.displayOrder ?: Int.MAX_VALUE }.take(6)
            val show = next.ifEmpty { list.filter { it.isUnlocked }.sortedBy { it.unlockPercent ?: 100f }.take(6) }
            if (show.isNotEmpty()) {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 4.dp))
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    show.forEach { a ->
                        val url = com.shelf.launcher.data.RaBadges.url(a.badgeName)
                        Box(Modifier.padding(0.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.35f))) {
                            if (url != null) AsyncImage(
                                url, a.title, contentScale = ContentScale.Crop,
                                // Still to get: shown grey and faded.
                                alpha = if (a.isUnlocked) 1f else 0.55f,
                                colorFilter = if (a.isUnlocked) null else GreyMatrix,
                                modifier = Modifier.padding(1.dp).size(36.dp)
                            )
                        }
                    }
                }
                next.firstOrNull()?.title?.let { t ->
                    Text("Next: $t", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = nameTextStyle(NameBackdrop.SHADOW))
                }
            }
        }
    }
}

/** Update 119 (Edit Home › + Add): the achievements progress bar on its own — earned / total and the bar. */
@Composable
internal fun HomeProgressPart(
    game: com.shelf.launcher.data.Game,
    list: List<com.shelf.launcher.data.RaAchievementDetail>,
    progress: com.shelf.launcher.data.RaGameProgress?
) {
    val total = if (list.isNotEmpty()) list.size else progress?.NumPossibleAchievements ?: 0
    val earned = if (list.isNotEmpty()) list.count { it.isUnlocked } else progress?.NumAchieved ?: 0
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.EmojiEvents, null, tint = ShelfTheme.accent, modifier = Modifier.size(16.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 6.dp))
        if (game.raGameId == null || total <= 0) {
            Text("No achievements", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, style = nameTextStyle(NameBackdrop.SHADOW))
        } else {
            Text("$earned / $total", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = nameTextStyle(NameBackdrop.SHADOW))
            androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 8.dp))
            Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f))) {
                // (Update 136: a real height: with only padding the fill had none, so the bar always looked empty.)
                Box(Modifier.fillMaxWidth((earned.toFloat() / total).coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(50)).background(ShelfTheme.accent))
            }
        }
    }
}

/** Update 119 (Edit Home › + Add): the game's award on its own — its icon in a ring of the award's colour, and the award's name. */
@Composable
internal fun HomeAwardPart(game: com.shelf.launcher.data.Game, award: com.shelf.launcher.data.RaAward?, icon: String?) {
    val ring = when (award) {
        com.shelf.launcher.data.RaAward.MASTERED, com.shelf.launcher.data.RaAward.COMPLETED -> Color(0xFFFFC83D)
        com.shelf.launcher.data.RaAward.BEATEN_HARDCORE, com.shelf.launcher.data.RaAward.BEATEN -> Color(0xFFC9D2DE)
        null -> Color.White.copy(alpha = 0.3f)
    }
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(ring).padding(3.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.4f))) {
            if (icon != null) AsyncImage(icon, game.name, contentScale = ContentScale.Crop, alpha = if (award != null) 1f else 0.6f, modifier = Modifier.fillMaxSize())
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 8.dp))
        Text(
            award?.label ?: if (game.raGameId != null) "Not beaten yet" else "No achievements",
            color = if (award != null) ring else Color.White.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            style = nameTextStyle(NameBackdrop.SHADOW)
        )
    }
}

private val CutCorner: Shape = GenericShape { size, _ ->
    val c = size.minDimension * 0.18f
    moveTo(0f, 0f); lineTo(size.width - c, 0f); lineTo(size.width, c); lineTo(size.width, size.height); lineTo(0f, size.height); close()
}

/**
 * Update 133: how dark the layer under a part's backing is, from the Backing slider (0–100 → 0–0.6). Reads state. The
 * capsule, memory card, SD card and floppy are solid already.
 */
@Composable
internal fun backingUnder(): Float = Features.partBackingSolid.state.value.coerceIn(0, 100) / 100f * 0.6f

/** [content] on the backing picked for the floating parts, in [layout]'s colours where it follows the layout. */
@Composable
internal fun PartBackingBox(layout: HomeLayout, content: @Composable () -> Unit) {
    // Update 125: a backing picked in the part's own Style replaces this one (None: see-through).
    val own = LocalPartLook.current
    if (own != null && own.noBacking) { Box(Modifier.padding(4.dp)) { content() }; return }
    val style = Features.partBacking.state.value
    val accent = ShelfTheme.accent
    // Update 133: the backings had become too see-through — a dark layer under each one, as strong as the Backing
    // slider says (Layout options › Game info on screen; 0 = as before).
    val under = Color.Black.copy(alpha = backingUnder())
    when (style) {
        PartBacking.NONE -> Box(Modifier.padding(4.dp)) { content() }
        // (Update 125: the shape picked in the part's Style, when there is one.)
        PartBacking.GLASS -> Box(Modifier.clip(own?.shape ?: RoundedCornerShape(14.dp)).background(under).background(Color.Black.copy(alpha = 0.35f)).padding(8.dp)) { content() }
        PartBacking.THEME -> {
            val shape = own?.shape ?: RoundedCornerShape(14.dp)
            Box(
                Modifier.clip(shape).background(under).background(ShelfTheme.colors.surface.copy(alpha = 0.86f))
                    .border(1.dp, accent.copy(alpha = 0.6f), shape).padding(8.dp)
            ) { content() }
        }
        PartBacking.CAPSULE -> {
            val shape = RoundedCornerShape(50)
            Box(
                Modifier.clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF2A2E38), Color(0xFF15181E))))
                    .border(1.5.dp, accent.copy(alpha = 0.7f), shape).padding(horizontal = 18.dp, vertical = 8.dp)
            ) { content() }
        }
        PartBacking.MEMORY_CARD -> {
            val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
            Box(Modifier.clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF66696F), Color(0xFF45484D))))) {
                // Screw holes, the raised triangle and the side ridges of a grey memory card.
                Canvas(Modifier.matchParentSize()) {
                    val w = size.width; val h = size.height
                    for (x in floatArrayOf(10.dp.toPx(), w - 10.dp.toPx())) drawCircle(Color(0xFF35373B), 3.dp.toPx(), Offset(x, 8.dp.toPx()))
                    val tri = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w / 2, 3.dp.toPx()); lineTo(w / 2 + 9.dp.toPx(), 10.dp.toPx()); lineTo(w / 2 - 9.dp.toPx(), 10.dp.toPx()); close()
                    }
                    drawPath(tri, Color(0xFF7C7F85))
                    for (i in 0 until 5) {
                        val y = h * 0.35f + i * 5.dp.toPx()
                        drawLine(Color.Black.copy(alpha = 0.25f), Offset(2.dp.toPx(), y), Offset(5.dp.toPx(), y), 1.dp.toPx())
                        drawLine(Color.Black.copy(alpha = 0.25f), Offset(w - 5.dp.toPx(), y), Offset(w - 2.dp.toPx(), y), 1.dp.toPx())
                    }
                }
                Box(Modifier.padding(start = 10.dp, end = 10.dp, top = 16.dp, bottom = 10.dp)) { content() }
            }
        }
        PartBacking.SD_CARD -> {
            Box(Modifier.clip(CutCorner).background(Brush.verticalGradient(listOf(Color(0xFF2B2F3A), Color(0xFF16181F))))) {
                // The gold contacts along the top.
                Canvas(Modifier.matchParentSize()) {
                    val n = 8
                    val gap = 3.dp.toPx()
                    val cw = ((size.width * 0.55f) - gap * (n - 1)) / n
                    for (i in 0 until n) drawRoundRect(
                        Color(0xFFC9A74A), Offset(8.dp.toPx() + i * (cw + gap), 3.dp.toPx()), Size(cw, 7.dp.toPx()), CornerRadius(1.dp.toPx())
                    )
                }
                Box(Modifier.padding(start = 10.dp, end = 10.dp, top = 16.dp, bottom = 10.dp)) { content() }
            }
        }
        PartBacking.FLOPPY -> {
            Box(Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFF1E2433))) {
                // The metal shutter at the top and the paper label under it.
                Canvas(Modifier.matchParentSize()) {
                    val w = size.width
                    drawRect(
                        Brush.horizontalGradient(listOf(Color(0xFF9EA4AE), Color(0xFFD9DDE3), Color(0xFF8E949E)), w * 0.3f, w * 0.7f),
                        Offset(w * 0.3f, 0f), Size(w * 0.4f, 10.dp.toPx())
                    )
                    drawRect(Color.White.copy(alpha = 0.06f), Offset(6.dp.toPx(), 14.dp.toPx()), Size(w - 12.dp.toPx(), size.height - 20.dp.toPx()))
                }
                Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 10.dp)) { content() }
            }
        }
        PartBacking.LAYOUT -> {
            // The layout's own look.
            val (shape, fill, edge) = when (layout) {
                HomeLayout.CHAN -> Triple(RoundedCornerShape(22.dp), Brush.verticalGradient(listOf(Color(0xE62E3540), Color(0xE61B2028))), Color(0xFF8FB4D8))
                HomeLayout.LIVE -> Triple(RoundedCornerShape(0.dp), Brush.verticalGradient(listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.7f))), Color.Transparent)
                HomeLayout.CROSS -> Triple(RoundedCornerShape(10.dp), Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.05f))), Color.White.copy(alpha = 0.25f))
                HomeLayout.SIDE -> Triple(RoundedCornerShape(6.dp), Brush.verticalGradient(listOf(Color(0xE6141820), Color(0xE60B0E13))),
                    if (Features.sideGold.state.value) Color(0xFFD9B45A) else accent)
                HomeLayout.SHELF -> Triple(RoundedCornerShape(6.dp), Brush.verticalGradient(listOf(Color(0xE65A3A22), Color(0xE63A2414))), Color(0xFF8A6440))
                HomeLayout.HUB, HomeLayout.RESUME -> Triple(RoundedCornerShape(6.dp), Brush.verticalGradient(listOf(Color(0xCC14181F), Color(0xCC14181F))), Color.White.copy(alpha = 0.18f))
                HomeLayout.BAR -> Triple(RoundedCornerShape(8.dp), Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.4f))), Color.Transparent)
                HomeLayout.CIRCUIT -> Triple(RoundedCornerShape(8.dp), Brush.verticalGradient(listOf(Color(0xF00D1410), Color(0xF00D1410))), Color(0xFF2C3A31))
                HomeLayout.DASH -> Triple(RoundedCornerShape(12.dp), Brush.verticalGradient(listOf(ShelfTheme.colors.surface.copy(alpha = 0.82f), ShelfTheme.colors.surface.copy(alpha = 0.7f))), accent.copy(alpha = 0.35f))
                else -> Triple(RoundedCornerShape(14.dp), Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.35f))), Color.Transparent)
            }
            Box(
                Modifier.clip(shape).background(under).background(fill)
                    .then(if (edge != Color.Transparent) Modifier.border(1.5.dp, edge, shape) else Modifier)
                    .padding(if (layout == HomeLayout.CHAN) 12.dp else 8.dp)
            ) { content() }
        }
    }
}
