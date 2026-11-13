package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.HomeLayout

/*
 * Update 425 (owner review of the 423 popups): layouts that shared one window now each get a window in their own home's look.
 * The scene enum stays the same (POCKET_LCD, INDEX_CARD); the window picks its face from the layout it opened in
 * ([LocalSceneLayout], provided by GameWindow). Functional parts stay shared: the facts (SceneFactsBody), the keys (KeyRow /
 * SceneKey, Play first, the wrapped lines reachable with the D-pad).
 *  - Pocket classic: a white handheld menu with a blue title bar and pixel type (lp_pocket_classic).
 *  - Pocket ribbon: a plain white information sheet, thin grey rules, the cartridge's art big (lp_pocket_ribbon).
 *  - Pocket Quest: a deep technical panel in the layout's own palette (pqPalette), cut corners (lp_pocket).
 *  - Game shelf: a dark wood shelf panel with the case brought forward on it (lp_shelf).
 *  - Case stacks: a compact slate panel beside the 3D case that flies out of its stack (lp_stacks).
 *  - Console stack: a console module — its bay, its LED, the layout's highlight rim (lp_consoles).
 *  - Cover details: the facts and keys beside the big case already on the screen, never over it (lp_coverdetails).
 */

/** Update 425: the home layout a scene window opened in (null outside GameWindow). */
internal val LocalSceneLayout = staticCompositionLocalOf<HomeLayout?> { null }

@Composable
internal fun PocketPopup(ctx: WinSceneCtx) = when (LocalSceneLayout.current) {
    HomeLayout.POCKET_CLASSIC -> PocketClassicMenu(ctx)
    HomeLayout.POCKET_RIBBON -> PocketRibbonSheet(ctx)
    HomeLayout.POCKET -> PocketQuestPanel(ctx)
    else -> PocketScreen(ctx)
}

@Composable
internal fun ShelfFamilyPopup(ctx: WinSceneCtx) = when (LocalSceneLayout.current) {
    HomeLayout.SHELF -> GameShelfWindow(ctx)
    HomeLayout.STACKS -> CaseStackWindow(ctx)
    HomeLayout.CONSOLES -> ConsoleModule(ctx)
    HomeLayout.COVERDETAILS -> CoverDetailPanel(ctx)
    else -> IndexCard(ctx)
}

/** Ink that reads on [c]. */
private fun onColour(c: Color) = if (c.luminance() > 0.45f) Color(0xFF15171C) else Color.White

/** A power-on / rise entrance shared by these windows (each passes its own motion). */
private fun Modifier.sceneEnter(ctx: WinSceneCtx, rise: Float = 0.18f, squash: Boolean = false): Modifier = graphicsLayer {
    val e = sceneEase(ctx.reveal())
    if (squash) { scaleY = 0.04f + 0.96f * e; scaleX = 0.94f + 0.06f * e } else translationY = (1f - e) * size.height * rise
    alpha = (ctx.reveal() * 2.5f).coerceIn(0f, 1f)
}

/** The game's picture whole in a framed well (never stretched). */
@Composable
private fun ArtWell(art: String?, modifier: Modifier, frame: Color, well: Color, shape: Shape, crop: Boolean = false) {
    Box(modifier.clip(shape).background(well).border(2.dp, frame, shape)) {
        // (Batch 523: Smart Fill instead of a whole picture between bars.)
        art?.let { if (crop) AsyncImage(rememberStyleArtRequest(it), null, Modifier.matchParentSize().padding(3.dp), contentScale = ContentScale.Crop)
            else SmartArt(it, null, Modifier.matchParentSize().padding(3.dp), well = well) }
    }
}

/** Title, facts and keys: the shared inside of these windows. */
@Composable
private fun ColumnScope.FactsAndKeys(ctx: WinSceneCtx, label: Color, value: Color, font: FontFamily?, key: @Composable (WinAction) -> Unit) {
    SceneFactsBody(ctx, label, value, font)
    Spacer(Modifier.height(8.dp))
    KeyRow(sceneKeys(ctx), 6.dp) { key(it) }
}

// ───────────────────────────── Pocket classic ─────────────────────────────

@Composable
private fun PocketClassicMenu(ctx: WinSceneCtx) {
    val paper = Color(0xFFFCFCFD); val ink = Color(0xFF2B2D33)
    // (Update 505: the title bar, frame and the key you're on in Pocket classic's own accent (Layout options › Accent, or the
    // theme's; your accent when this layout has its own colours) — as the home's top bar, clock hand and today's date — was a
    // fixed blue; the rule a pale tint of it.)
    val own = pocketAccent(runCatching { PocketAccent.valueOf(com.shelf.launcher.data.Features.classicAccent.state.value) }.getOrDefault(PocketAccent.BLUE))
    val blue = sceneLit(HomeLayout.POCKET_CLASSIC, own, paper)
    val rule = androidx.compose.ui.graphics.lerp(blue, paper, 0.72f)
    val font = pocketFont()
    val w = minOf(ctx.maxW, 500.dp)
    Column(Modifier.width(w).heightIn(max = ctx.maxH).sceneEnter(ctx, squash = true)
        .clip(RoundedCornerShape(4.dp)).background(paper).border(2.dp, rule, RoundedCornerShape(4.dp))) {
        // The title bar of the handheld's own menu.
        Row(Modifier.fillMaxWidth().background(blue).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(ctx.console.uppercase(), color = onColour(blue), fontSize = 13.sp, fontFamily = font, modifier = Modifier.weight(1f))
            ctx.year?.let { Text(it, color = onColour(blue), fontSize = 13.sp, fontFamily = font) }
        }
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtWell(ctx.cover, Modifier.size(64.dp), blue, androidx.compose.ui.graphics.lerp(blue, paper, 0.88f), RoundedCornerShape(2.dp))
                Spacer(Modifier.width(10.dp))
                StableFullText(ctx.name, color = ink, fontSize = 15.sp, maxLines = 3, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            Box(Modifier.padding(vertical = 7.dp).fillMaxWidth().height(2.dp).background(rule))
            FactsAndKeys(ctx, blue, ink, font) { a ->
                SceneKey(a, paper, blue, ink, onColour(blue), rule, blue, RoundedCornerShape(2.dp), font = font, size = 13.sp, pad = 8.dp, height = 28.dp)
            }
        }
    }
}

// ───────────────────────────── Pocket ribbon ─────────────────────────────

@Composable
private fun PocketRibbonSheet(ctx: WinSceneCtx) {
    val a = ctx.accent; val paper = Color(0xFFF8F9FA); val ink = Color(0xFF2E3136); val grey = Color(0xFFD3D6DB); val muted = Color(0xFF8A8F98)
    val font = pocketFont()
    val w = minOf(ctx.maxW, 620.dp)
    Column(Modifier.width(w).heightIn(max = ctx.maxH).sceneEnter(ctx, rise = 0.06f).background(paper).border(1.dp, grey)) {
        // The thin information bar, as along the ribbon's top.
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(ctx.console, color = a, fontSize = 13.sp, fontFamily = font, modifier = Modifier.weight(1f))
            Text(ctx.played, color = muted, fontSize = 13.sp, fontFamily = font)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(grey))
        Row(Modifier.padding(10.dp)) {
            // The cartridge's art, big, in the ribbon's pale frame.
            ArtWell(ctx.cover ?: ctx.wide, Modifier.width(minOf(w * 0.34f, 190.dp)).aspectRatio(0.82f), grey, Color(0xFFE4E7EB), RoundedCornerShape(0.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                StableFullText(ctx.name, color = ink, fontSize = 17.sp, maxLines = 3, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                FactsAndKeys(ctx, muted, ink, font) { k ->
                    SceneKey(k, Color.White, a, ink, onColour(a), grey, a, RoundedCornerShape(0.dp), font = font, size = 12.sp, pad = 8.dp, height = 26.dp)
                }
            }
        }
    }
}

// ───────────────────────────── Pocket Quest ─────────────────────────────

@Composable
private fun PocketQuestPanel(ctx: WinSceneCtx) {
    val p = pqPalette()
    val w = minOf(ctx.maxW, 640.dp)
    val shape = CutCornerShape(14.dp)
    // (Update 469 recheck, 1.3× text on the KONKR: beside the inspected cartridge the keys wrapped one or two a line in the
    // column right of the art — seven lines — and the window's height cut the last ones (Inspect, the Saves line). With
    // larger text the keys take the window's whole width under the art.)
    val under = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.1f
    val key: @Composable (WinAction) -> Unit = { k ->
        SceneKey(k, p.deep, p.accent, p.ink, p.onAccent, p.accent.copy(alpha = 0.45f), p.accentLight, CutCornerShape(5.dp), size = 12.sp, height = 30.dp)
    }
    Column(Modifier.width(w).heightIn(max = ctx.maxH).sceneEnter(ctx, rise = 0.1f)
        .clip(shape).background(Brush.verticalGradient(listOf(p.top, p.bottom))).border(2.dp, p.accent, shape).padding(14.dp)) {
        if (!under) Row {
            ArtWell(ctx.cover ?: ctx.wide, Modifier.width(minOf(w * 0.28f, 150.dp)).aspectRatio(0.75f), p.accent, p.deep, CutCornerShape(6.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ctx.console.uppercase(), color = p.accentLight, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                StableFullText(ctx.name, color = p.ink, fontSize = 19.sp, maxLines = 3, fontWeight = FontWeight.Bold)
                Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(p.accent, p.accent.copy(alpha = 0f)))))
                FactsAndKeys(ctx, p.muted, p.ink, null) { k -> key(k) }
            }
        } else {
            // (Update 469, owner review of the 469 PDF: the facts in the narrow column right of the art ended in a faded
            // "Played / Trophies" row just above the keys. With larger text: a smaller art beside the name, the facts across the
            // whole width under it (fewer, whole rows), then the keys — measured first, the facts give way.)
            Column(Modifier.weight(1f, fill = false)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArtWell(ctx.cover ?: ctx.wide, Modifier.width(minOf(w * 0.2f, 96.dp)).aspectRatio(0.75f), p.accent, p.deep, CutCornerShape(6.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ctx.console.uppercase(), color = p.accentLight, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        StableFullText(ctx.name, color = p.ink, fontSize = 19.sp, maxLines = 3, fontWeight = FontWeight.Bold)
                    }
                }
                Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(p.accent, p.accent.copy(alpha = 0f)))))
                SceneFactsBody(ctx, p.muted, p.ink, null)
            }
            Spacer(Modifier.height(8.dp)); KeyRow(sceneKeys(ctx), 6.dp) { key(it) }
        }
    }
}

// ───────────────────────────── Game shelf ─────────────────────────────

// (Update 507: ShelfPanel replaced — WinSceneFamilies.kt.)

// ───────────────────────────── Case stacks ─────────────────────────────

// (Update 507: StackPanel replaced — WinSceneFamilies.kt.)

// ───────────────────────────── Console stack ─────────────────────────────

@Composable
private fun ConsoleModule(ctx: WinSceneCtx) {
    val a = ctx.accent; val ink = Color(0xFFE9EBEF); val muted = Color(0xFF9CA2AD)
    val w = minOf(ctx.maxW, 600.dp)
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.width(w).heightIn(max = ctx.maxH).sceneEnter(ctx, squash = true).clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xFF41454D), Color(0xFF2A2D33), Color(0xFF202227))))
        .border(2.dp, Brush.horizontalGradient(listOf(a, a.copy(alpha = 0.35f))), shape).padding(12.dp)) {
        // The module's front: its bay slot, the LED and the vents, as on the console blocks.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(a))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF121317)))
            Spacer(Modifier.width(10.dp))
            Canvas(Modifier.width(54.dp).height(12.dp)) {
                for (i in 0 until 6) drawLine(Color.Black.copy(alpha = 0.45f), Offset(i * size.width / 6f + 3f, 1f), Offset(i * size.width / 6f + 3f, size.height - 1f), 2f)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            // The game sitting in the bay — (update 507) its own case as the home draws it, not an empty well when it has no cover.
            val aspect = sceneCaseAspect(ctx.g.platform)
            val cw = minOf(w * 0.25f, 130.dp)
            SceneCase(ctx, Modifier.width(cw).height(cw / aspect))
            Spacer(Modifier.width(12.dp))
            // (529k: Details / Achievements / Options show on the module's own screen beside the bay - ShelfFamilyPages.)
            val moduleKey: @Composable (WinAction) -> Unit = { k ->
                SceneKey(k, Color(0xFF2E3137), a, ink, onColour(a), Color.Black.copy(alpha = 0.5f), a, RoundedCornerShape(6.dp), size = 12.sp)
            }
            ShelfFamilyPages(ctx, Modifier.weight(1f), a, ink, Color(0xFF121317), moduleKey) { live ->
                StableFullText(ctx.name, color = ink, fontSize = 18.sp, maxLines = 3, fontWeight = FontWeight.Bold)
                Text(ctx.console.uppercase(), color = a, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(6.dp))
                // (Update 507: the console is on the module's label already — not twice.)
                SceneFactsBody(ctx, muted, ink, null, omit = setOf("Console"))
                Spacer(Modifier.height(8.dp))
                KeyRow(sceneKeys(ctx).map { if (live) it else it.inert() }, 6.dp) { k -> moduleKey(k) }
            }
        }
    }
}

// ───────────────────────────── Cover details ─────────────────────────────

@Composable
private fun CoverDetailPanel(ctx: WinSceneCtx) {
    // The big case stays where the layout shows it (left); the facts and keys open beside it, never over it.
    val a = ctx.accent; val ink = Color.White; val muted = Color.White.copy(alpha = 0.62f)
    val panelW = minOf(ctx.maxW * 0.5f, 560.dp)
    Box(Modifier.width(ctx.maxW), contentAlignment = Alignment.CenterEnd) {
        Column(Modifier.width(panelW).heightIn(max = ctx.maxH).sceneEnter(ctx, rise = 0.08f).clip(RoundedCornerShape(12.dp))
            // (Update 471: fully opaque — at 97 % the home screen's big title still showed through faintly behind the name.)
            .background(Color(0xFF0E0F13)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            // (Update 507: a lit edge toward the case it belongs to.)
            .drawBehind { drawRect(Brush.verticalGradient(listOf(a, a.copy(alpha = 0.2f))), size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) }
            .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
            // (Update 469, D215: the panel was 86 % opaque — the home screen's own title and facts, right under it, showed through
            // as a second faded title behind the keys (worst with More open at 1.3×). It hides what it covers now.)
            // (Update 469, display size larger — KONKR at 280 dpi: with More open the keys ran past the panel's foot. A weighted name
            // fixed that but took the whole free height — a big gap under the title, the facts (Year) squeezed out, and the title's
            // re-fit drew a faded second copy. Now the name is simply smaller on a short panel and the facts give way first.)
            // The keys are measured first (the block above them is the weighted one); inside it the name and console keep their
            // natural height and the facts give way.
            val shortPanel = ctx.maxH < 420.dp
            val compact = shortPanel && ctx.tools.any { it.label == "Less" }
            val coverKey: @Composable (WinAction) -> Unit = { k ->
                SceneKey(k, Color.White.copy(alpha = 0.07f), a, ink, onColour(a), Color.White.copy(alpha = 0.16f), a, RoundedCornerShape(8.dp),
                    size = if (compact) 11.sp else 12.sp, pad = if (compact) 8.dp else 10.dp, height = if (compact) 26.dp else 30.dp)
            }
            // (529k: Details / Achievements / Options are further panels beside the big case - ShelfFamilyPages; the case stays.)
            ShelfFamilyPages(ctx, Modifier, a, ink, Color.White.copy(alpha = 0.05f), coverKey) { live ->
            Column(Modifier.weight(1f, fill = false)) {
                StableFullText(ctx.name, color = ink, fontSize = if (shortPanel) 20.sp else 24.sp, maxLines = if (shortPanel) 2 else 3, fontWeight = FontWeight.Bold)
                // (The year beside the console: with More open the facts give way to the keys and the year went with them.)
                // (Update 471: console and year are on this line — not repeated in the facts. With More open every fact moves up
                // into two plain lines — console · year · maker · genre, then what you've played — so all of them stay in view over
                // the extra keys, also with large text and display size larger (KONKR at 280 dpi left the label rows no room).)
                val moreOpen = ctx.tools.any { it.label == "Less" }
                Text(if (moreOpen) coverInfoLine(ctx) else ctx.console + (ctx.year?.let { " · $it" } ?: ""), color = a, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (moreOpen) Text(coverPlayedLine(ctx), color = muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(if (moreOpen) 6.dp else 10.dp))
                // (Update 471: the facts / story under the lines are what gives way — never the lines themselves.)
                Column(Modifier.weight(1f, fill = false)) {
                    SceneFactsBody(ctx, muted, ink, null, omit = if (moreOpen) COVER_DETAIL_OMIT_MORE else COVER_DETAIL_OMIT, inline = moreOpen)
                }
            }
            Spacer(Modifier.height(8.dp))
            // (Update 471, D244: KONKR at 280 dpi with More open — nine keys in six rows left the two lines above them 40 px short:
            // the second line was cut and "Never played · 0 / 109 trophies" got no height. On a short panel with More open the
            // keys are a little more compact — 26 dp tall (touch stays >= 24 dp), 4 dp apart — so both lines stay whole.)
            KeyRow(sceneKeys(ctx).map { if (live) it else it.inert() }, if (compact) 4.dp else 6.dp) { k -> coverKey(k) }
            }
        }
    }
}

/**
 * Update 507 (owner review: Cover details' window "has no artwork although the home is all about the cover"): the big case the
 * home shows on the left stays in full view — the dim is light there and deep only under the panel on the right.
 */
internal val coverDetailsVeil: Brush = Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.12f), 0.45f to Color.Black.copy(alpha = 0.2f), 0.55f to Color.Black.copy(alpha = 0.62f), 1f to Color.Black.copy(alpha = 0.78f))

/** Update 425: the Disc station window's labels for what the game really comes on. */
internal fun discPanelLabels(platform: String): Pair<String, String> =
    // (Update 506, owner review of the 505 visual report: Disc station's window said "CARTRIDGE / SLOT A" on a disc player whose
    // home says "On CD" — the deck plays everything as a disc. Its own terms now: the disc kind the home shows, read as a disc.)
    discKindPlate(discKind(platform)) to "DISC 1 · TRACK 01"

/** Update 506: the home's disc kind as the deck's plate ("On CD", a cartridge game pressed on CD here: COMPACT DISC). */
internal fun discKindPlate(kind: String): String = if (kind == "On CD") "COMPACT DISC" else kind.uppercase()

/** Update 471: Cover details' header line already shows these. */
internal val COVER_DETAIL_OMIT = setOf("Console", "Year")
/** With More open: what you've played is on its own line under the console (coverPlayedLine). */
internal val COVER_DETAIL_OMIT_MORE = COVER_DETAIL_OMIT + setOf("Made by", "Genre", "Played", "Last played", "Trophies")
internal fun coverInfoLine(ctx: WinSceneCtx): String = coverInfoLine(ctx.console, ctx.year, ctx.developer, ctx.genres)
internal fun coverInfoLine(console: String, year: String?, developer: String?, genres: String?): String =
    listOfNotNull(console, year, developer, genres).filter { it.isNotBlank() }.joinToString(" · ")
internal fun coverPlayedLine(ctx: WinSceneCtx): String = coverPlayedLine(ctx.played, ctx.last, ctx.earned, ctx.total)
internal fun coverPlayedLine(played: String, last: String?, earned: Int, total: Int): String =
    listOfNotNull(if (played.startsWith("Never")) played else "Played $played", last?.let { "last $it" }, if (total > 0) "$earned / $total trophies" else null).joinToString("  ·  ")
