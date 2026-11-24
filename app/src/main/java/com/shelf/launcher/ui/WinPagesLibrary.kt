package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P2 batch 3 (682w): the Library window's own pages. The window is a game's page in a PC game library - the hero banner with
 * the logo, the play bar with the big Play, the stats under it, dark blue-grey panels in the theme's colours - so its pages are
 * that page's own sections under a tab strip (the current tab underlined in the Play colour):
 *  - Details      = ABOUT THIS GAME: the store page's description panel, its facts as the side column of label / value rows.
 *  - Achievements = ACHIEVEMENTS: each one a row with its icon, name, description and a bar of how many players have it (the
 *                   unlock rate), earned ones lit; the order as a "Sort by" link; A opens the row's full description.
 *  - Options      = MANAGE: the manage menu - rows on the panel; one that removes something is red and asks for A again.
 * Motion: the tab strip's underline is the page; the section slides in a little from the side of the tab it came from.
 * The play bar - and Play in it - stays where it is on every page.
 */

internal fun libraryTab(p: WinPage): String = when (p) {
    WinPage.MAIN -> "OVERVIEW"
    WinPage.DETAILS -> "ABOUT THIS GAME"
    WinPage.TROPHIES -> "ACHIEVEMENTS"
    WinPage.OPTIONS -> "MANAGE"
    WinPage.PERF -> "PERFORMANCE"
}

/** The unlock-rate bar's fill, 0..1, from a percentage that may be missing. Pure. */
internal fun libraryRate(percent: Float?): Float = ((percent ?: 0f) / 100f).coerceIn(0f, 1f)

internal fun libraryMove(dir: Int): ContentTransform {
    val d = if (dir < 0) -1 else 1
    // (682ag, KONKR x1.3 clip: the story and the list's rows were both on screen for ~40 ms - the old page fades out first)
    return (fadeIn(tween(NATIVE_PAGE_MS - NATIVE_OUT_MS, delayMillis = NATIVE_OUT_MS, easing = FastOutSlowInEasing)) + slideInHorizontally(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { w -> d * w / 14 }) togetherWith
        fadeOut(tween(NATIVE_OUT_MS))
}

private val LIB_MUTED = Color.White.copy(alpha = 0.6f)
private val LIB_RED = Color(0xFFE5484D)

/** The tab strip over the sections, the page's tab underlined in [lit]. */
@Composable
internal fun LibraryTabs(ctx: WinSceneCtx, lit: Color) {
    val shownPage = rememberHeadingPage(ctx.page) // (682ag: the tab moves when the old section is gone)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(WinPage.DETAILS, WinPage.TROPHIES, WinPage.OPTIONS).filter { it in ctx.pages }.forEach { p ->
            val on = p == shownPage
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(libraryTab(p), color = if (on) Color.White else LIB_MUTED, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, softWrap = false)
                Box(Modifier.padding(top = 3.dp).width(28.dp).height(2.dp).background(if (on) lit else Color.Transparent))
            }
        }
    }
}

@Composable
internal fun AnimatedVisibilityScope.LibraryPagesBody(ctx: WinSceneCtx, p: WinPage, live: Boolean, panel: Color, lit: Color, modifier: Modifier) {
    when (p) {
        WinPage.DETAILS -> LibraryAbout(ctx, live, panel, lit, modifier)
        WinPage.TROPHIES -> LibraryAchievements(ctx, live, panel, lit, modifier)
        WinPage.OPTIONS -> LibraryManage(ctx, live, panel, lit, modifier)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live)
    }
}

@Composable
private fun LibraryAbout(ctx: WinSceneCtx, live: Boolean, panel: Color, lit: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(Color.White)) {
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Text(ctx.summary ?: "No description for this game yet.", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(panel)
                    .then(if (on) Modifier.border(2.dp, lit, RoundedCornerShape(3.dp)) else Modifier).padding(10.dp))
        }
        Spacer(Modifier.height(6.dp))
        nativeFacts(ctx, setOf("Trophies")).forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(f.label.uppercase(), color = LIB_MUTED, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.width(96.dp))
                WholeWordsText(f.value, color = Color.White, fontSize = 12.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LibraryAchievements(ctx: WinSceneCtx, live: Boolean, panel: Color, lit: Color, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${t.earned} of ${t.total} achievements earned", color = LIB_MUTED, fontSize = 11.sp, modifier = Modifier.weight(1f))
            NativeItem(onClick = t.nextOrder, Modifier.weight(1f, fill = false), focus = focus, live = live) { on ->
                Text("Sort by: " + t.order.label, color = if (on) Color.White else lit, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(if (on) lit.copy(alpha = 0.35f) else Color.Transparent).padding(horizontal = 6.dp, vertical = 3.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        NativeRows(Color.White, Modifier.weight(1f).fillMaxWidth(), 3.dp) {
            nativeItems(t.all, { it.id }) { _, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(panel)
                            .then(if (on) Modifier.border(2.dp, lit, RoundedCornerShape(3.dp)) else Modifier).padding(6.dp)) {
                            NativeBadge(ach.badgeName, ach.title, earned, lit, Modifier.size(34.dp), shape = RoundedCornerShape(2.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                WholeWordsText(ach.title ?: "Untitled", color = if (earned) Color.White else LIB_MUTED, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
                                ach.description?.let { d ->
                                    if (opened) Text(d, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 15.sp)
                                    else WinFullText(d, color = LIB_MUTED, fontSize = 11.sp, maxLines = 1, lineHeight = 14.sp)
                                }
                                // How many players have it - the unlock rate.
                                ach.unlockPercent?.let { pc ->
                                    Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.1f))) {
                                            Box(Modifier.fillMaxWidth(libraryRate(pc)).fillMaxHeight().background(if (earned) lit else LIB_MUTED))
                                        }
                                        Text("  %.1f%%".format(pc), color = LIB_MUTED, fontSize = 10.sp, softWrap = false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (t.all.isEmpty()) item {
            Text("This game has no achievements.", color = LIB_MUTED, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LibraryManage(ctx: WinSceneCtx, live: Boolean, panel: Color, lit: Color, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(Color.White), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(if (on) lit.copy(alpha = 0.22f) else panel)
                    .then(if (on) Modifier.border(1.dp, if (o.destructive) LIB_RED else lit, RoundedCornerShape(3.dp)) else Modifier).padding(horizontal = 10.dp, vertical = 7.dp)) {
                    WinFullText(o.label, color = if (o.destructive) LIB_RED else Color.White, fontSize = 13.sp, maxLines = 2, lineHeight = 16.sp)
                    o.sub?.let { WinFullText(it, color = LIB_MUTED, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp) }
                    if (armed) Text("Press A again to confirm  ·  move away to cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(3.dp)).background(LIB_RED.copy(alpha = 0.85f)).padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("Nothing to manage for this game.", color = LIB_MUTED, fontSize = 12.sp)
    }
}
