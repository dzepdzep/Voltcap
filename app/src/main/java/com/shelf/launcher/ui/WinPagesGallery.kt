package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 (741a, class C): the Art library's own pages. The window is a gallery wall in the theme's dark: the work whole in a dark
 * frame on a pale mat, the white museum placard under it (name, maker / year / console, play time), the keys as slim keys lit
 * in the layout's colour. Its pages hang on the same mat, in the placard's serif and ink:
 *  - Info         = The work: the picture as before.
 *  - Details      = Catalogue: each fact a catalogue line - the label in small grey capitals over the value in the serif,
 *                   hairlines between - then the story under "Notes" (a D-pad stop).
 *  - Achievements = Collection: one line - the cup, "N of M acquired", a hang of 10 small frames (one acquired always fills
 *                   one, all only when every one is acquired) and the order as a slim key; each achievement a small framed
 *                   badge (dark frame, white mat) beside its placard: the title in the serif, "Acquired <date>" /
 *                   "Not yet acquired", "+N"; the one you are on lit in the layout's colour; A shows the whole text and how
 *                   rare it is.
 *  - Options      = Curator: each option a small placard; one that removes something is in the museum's deep red and asks for
 *                   a second A ("Press A again to confirm", NativeConfirm).
 * Heading: the page's name in the serif italic and "Room N of M", after the old page has gone.
 * Motion: the next page comes up like the gallery's lights as you step up to the wall (fading in, a little closer); none with
 * Motion Off. On its pages the wall keeps its size and its keys keep the page slots, Play first, so Play stays put.
 * Shared: data, focus, B, Play-first, inert outgoing copy (NativePageKit).
 */

/** The gallery's name for a page. Pure. */
internal fun galleryPageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "The work"
    WinPage.DETAILS -> "Catalogue"
    WinPage.TROPHIES -> "Collection"
    WinPage.OPTIONS -> "Curator"
    WinPage.PERF -> "Performance"
}

/** "Room N of M": where [shown] hangs among the window's [tabs] (1-based); "" when it is not one of them. Pure. */
internal fun galleryRoom(tabs: List<WinPage>, shown: WinPage): String {
    val i = tabs.indexOf(shown)
    return if (i < 0 || tabs.isEmpty()) "" else "Room ${i + 1} of ${tabs.size}"
}

/** The count beside the cup: "N of M acquired" (acquired never above the total). Pure. */
internal fun galleryAwardCount(earned: Int, total: Int): String = "${earned.coerceIn(0, maxOf(total, 0))} of ${maxOf(total, 0)} acquired"

/** An achievement's line on its placard: "Acquired <date>" / "Acquired" when yours, "Not yet acquired" otherwise. Pure. */
internal fun galleryAwardLine(earned: Boolean, date: String?): String =
    if (!earned) "Not yet acquired" else date?.takeIf { it.isNotBlank() }?.let { "Acquired ${it.trim()}" } ?: "Acquired"

/** How many of the hang's [frames] are filled: one acquired always fills one, all only when every one is acquired. Pure. */
internal fun galleryHung(earned: Int, total: Int, frames: Int): Int {
    if (total <= 0 || frames <= 0) return 0
    val e = earned.coerceIn(0, total)
    if (e == 0) return 0
    if (e == total) return frames
    return (e * frames / total).coerceIn(1, frames - 1)
}

internal const val GALLERY_FRAMES = 10

/** The next page comes up like the gallery's lights as you step up to the wall: fading in, a little closer; the old goes at once. */
internal fun galleryMove(@Suppress("UNUSED_PARAMETER") dir: Int): ContentTransform =
    (fadeIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) +
        scaleIn(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing), initialScale = 0.96f)) togetherWith
        fadeOut(tween(NATIVE_OUT_MS))

private val GalleryRed = Color(0xFFA3221B)
private val GallerySlim = RoundedCornerShape(2.dp)

/** The gallery's ink set for its pages: the placard's ink on the mat, [lit] = the layout's colour. */
internal class GalleryInk(val lit: Color, val ink: Color) {
    val label = ink.copy(alpha = 0.55f)
    val muted = ink.copy(alpha = 0.5f)
    val hair = ink.copy(alpha = 0.15f)
    val card = Color.White
    val frame = Color(0xFF2A2724)
    val onLit = com.shelf.launcher.ui.theme.textOn(lit, Color.White)
}

/** The heading: the page's name in the serif italic, "Room N of M" beside it, a hairline under both. */
@Composable
internal fun GalleryPageHeading(tabs: List<WinPage>, shown: WinPage, k: GalleryInk, short: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(bottom = if (short) 3.dp else 5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(galleryPageName(shown), color = k.ink, fontSize = if (short) 15.sp else 18.sp, fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, lineHeight = if (short) 18.sp else 22.sp, maxLines = 2,
                modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(10.dp))
            Text(galleryRoom(tabs, shown).uppercase(), color = k.label, fontSize = 10.sp, fontFamily = FontFamily.Serif,
                letterSpacing = 1.sp, lineHeight = 13.sp, softWrap = false, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(k.hair))
    }
}

/** One of the gallery's pages (The work is the picture itself, drawn by GalleryPlacard). */
@Composable
internal fun AnimatedVisibilityScope.GalleryPage(ctx: WinSceneCtx, p: WinPage, live: Boolean, k: GalleryInk, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> GalleryCatalogue(ctx, live, k, modifier)
        WinPage.TROPHIES -> GalleryCollection(ctx, live, k, modifier)
        WinPage.OPTIONS -> GalleryCurator(ctx, live, k, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not on the wall yet)
    }
}

@Composable
private fun GalleryCatalogue(ctx: WinSceneCtx, live: Boolean, k: GalleryInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(k.lit)) {
        nativeFacts(ctx).forEach { f ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                WholeWordsText(f.label.uppercase(), color = k.label, fontSize = 10.sp, lineHeight = 13.sp, fontFamily = FontFamily.Serif)
                WholeWordsText(f.value, color = k.ink, fontSize = 14.sp, lineHeight = 18.sp, fontFamily = FontFamily.Serif)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(k.hair))
        }
        Spacer(Modifier.height(8.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Column(Modifier.fillMaxWidth().background(k.card, GallerySlim).border(if (on) 2.dp else 1.dp, if (on) k.lit else k.hair, GallerySlim)
                .padding(horizontal = 10.dp, vertical = 7.dp)) {
                Text("Notes", color = if (on) k.lit else k.label, fontSize = 12.sp, fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, lineHeight = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text(ctx.summary ?: "No description for this game yet.", color = k.ink, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = FontFamily.Serif)
            }
        }
    }
}

@Composable
private fun GalleryCollection(ctx: WinSceneCtx, live: Boolean, k: GalleryInk, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        // One line: the cup and the count, the hang in the room between, the order key at most its share (its words slide on one
        // line, rule 11) - so the first achievement stays whole at x1.3 (AchFirstRow).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EmojiEvents, null, tint = k.lit, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(galleryAwardCount(t.earned, t.total), color = k.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp,
                fontFamily = FontFamily.Serif, maxLines = 1, softWrap = false)
            val hung = galleryHung(t.earned, t.total, GALLERY_FRAMES)
            Row(Modifier.weight(1f).padding(horizontal = 8.dp).height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(GALLERY_FRAMES) { i ->
                    Box(Modifier.weight(1f).fillMaxHeight().border(1.dp, k.frame.copy(alpha = if (i < hung) 1f else 0.3f)).padding(2.dp)
                        .background(if (i < hung) k.lit else Color.Transparent))
                }
            }
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Box(Modifier.background(if (on) k.lit else k.card, GallerySlim).border(1.dp, if (on) k.lit else k.hair, GallerySlim)
                    .padding(horizontal = 10.dp, vertical = 4.dp)) {
                    WinFullText("Order: " + t.order.label, color = if (on) k.onLit else k.ink, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp,
                        fontFamily = FontFamily.Serif)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        NativeRows(k.lit, Modifier.weight(1f).fillMaxWidth(), spacing = 3.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        val text = if (on) k.onLit else if (earned) k.ink else k.muted
                        val sub = if (on) k.onLit.copy(alpha = 0.85f) else k.muted
                        Column(Modifier.fillMaxWidth().background(if (on) k.lit else k.card, GallerySlim)
                            .border(1.dp, if (on) k.lit else k.hair, GallerySlim).padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // The badge hangs framed: a dark frame round a white mat.
                                Box(Modifier.size(34.dp).background(k.frame).padding(2.dp).background(Color.White).padding(2.dp)) {
                                    NativeBadge(ach.badgeName, ach.title, earned, k.lit, Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    WholeWordsText(ach.title ?: "Untitled", color = text, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        lineHeight = 17.sp, fontFamily = FontFamily.Serif)
                                    Text(galleryAwardLine(earned, ach.dateEarned), color = if (on) sub else if (earned) k.ink.copy(alpha = 0.75f) else k.muted,
                                        fontSize = 10.sp, lineHeight = 13.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
                                }
                                ach.points?.let { pts ->
                                    Text("+$pts", color = if (on) k.onLit else if (earned) k.ink else k.muted, fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, softWrap = false, modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                            ach.description?.let { d ->
                                if (opened) Text(d, color = text, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = FontFamily.Serif, modifier = Modifier.padding(start = 42.dp, top = 3.dp))
                                else WinFullText(d, color = sub, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp, modifier = Modifier.padding(start = 42.dp, top = 1.dp))
                            }
                            if (opened) ach.unlockPercent?.let { pc ->
                                Text("%.1f %% of players have it".format(pc), color = if (on) k.onLit else k.ink.copy(alpha = 0.75f), fontSize = 11.sp,
                                    fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 42.dp, top = 1.dp, bottom = 2.dp))
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
                Text("No achievements for this game yet.", color = k.muted, fontSize = 13.sp, fontFamily = FontFamily.Serif,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun GalleryCurator(ctx: WinSceneCtx, live: Boolean, k: GalleryInk, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(k.lit), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(),
                focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val hi = if (o.destructive) GalleryRed else k.lit
                val onHi = if (o.destructive) Color.White else k.onLit
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().background(if (on) hi else k.card, GallerySlim)
                        .border(1.dp, if (on) hi else if (o.destructive) GalleryRed.copy(alpha = 0.5f) else k.hair, GallerySlim)
                        .padding(horizontal = 10.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        WinFullText(o.label, color = if (on) onHi else if (o.destructive) GalleryRed else k.ink, fontSize = 14.sp, maxLines = 2,
                            lineHeight = 18.sp, fontFamily = FontFamily.Serif, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                        o.sub?.let { WinFullText(it, color = if (on) onHi.copy(alpha = 0.85f) else k.muted, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp,
                            fontFamily = FontFamily.Serif) }
                    }
                    if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, lineHeight = 16.sp,
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp).background(GalleryRed, GallerySlim).padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("Nothing for the curator on this game.", color = k.muted, fontSize = 13.sp, fontFamily = FontFamily.Serif)
    }
}

/** The gallery's page area on the mat: the heading, then the page, lit up by [galleryMove]. [short]: a short wall - a smaller
 *  heading, so the first achievement stays whole (AchFirstRow). */
@Composable
internal fun GalleryPageArea(ctx: WinSceneCtx, k: GalleryInk, modifier: Modifier, short: Boolean = false) {
    val heading = rememberHeadingPage(ctx.page)
    Column(modifier) {
        GalleryPageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, k, short)
        NativePages(ctx, Modifier.fillMaxWidth().weight(1f), label = "galleryPage", move = ::galleryMove) { pg, pageLive ->
            GalleryPage(ctx, pg, pageLive, k, Modifier.fillMaxSize())
        }
    }
}
