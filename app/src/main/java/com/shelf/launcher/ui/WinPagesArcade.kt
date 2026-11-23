package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/*
 * P2 batch 2 (682p): the Arcade cabinets window's own pages. The window is the machine's screen in its attract / service
 * mode (the tube switching on, INSERT COIN, a ▶ cursor menu in the pixel lettering), so its pages are the cabinet's own
 * screens, in the same pixel font, yellow / cyan / pink on the tube's dark:
 *  - Details      = INSTRUCTIONS: the instruction card - each fact a NAME ..... VALUE line with a dot leader, the story as the
 *                   attract text (a D-pad stop that scrolls).
 *  - Achievements = HIGH SCORES: the high-score table - RANK, the achievement as the entry, its points as the SCORE; one you
 *                   have glows yellow, one to get is dim with "-----" for its score; A opens its text; the order as a
 *                   "SORT" line on the table's head.
 *  - Options      = SERVICE MODE: the operator's menu - "▶" on the line you're on; one that removes something is pink and asks
 *                   "PRESS A AGAIN TO CONFIRM" (NativeConfirm).
 * Motion: the tube rolls to the next screen - the new page is drawn from the top down, a bright scan line riding its edge.
 */

private val ArcPgInk = Color(0xFFF4F1FF)
private val ArcPgYellow = Color(0xFFFFE23D)
private val ArcPgCyan = Color(0xFF3DE7FF)
private val ArcPgPink = Color(0xFFFF3DB4)
private val ArcPgDim = Color(0xFF8E89A8)

/** The page's name on the screen's top line. Pure. */
internal fun arcadeNativePageName(p: WinPage): String = when (p) {
    WinPage.MAIN -> "ATTRACT"
    WinPage.DETAILS -> "INSTRUCTIONS"
    WinPage.TROPHIES -> "HIGH SCORES"
    WinPage.OPTIONS -> "SERVICE MODE"
    WinPage.PERF -> "PERFORMANCE"
}

/** The high-score table's rank column: "1ST", "2ND", "3RD", "4TH" ... "11TH", "12TH", "13TH", "21ST". Pure. */
internal fun arcadeRank(i: Int): String {
    val n = i + 1
    val suf = if (n % 100 in 11..13) "TH" else when (n % 10) { 1 -> "ST"; 2 -> "ND"; 3 -> "RD"; else -> "TH" }
    return "$n$suf"
}

/** A score as the table prints it: six digits, zero-padded ("000050"); "-----" for one still to get. Pure. */
internal fun arcadeScore(points: Int?, earned: Boolean): String =
    if (!earned) "-----" else (points ?: 0).coerceIn(0, 999_999).toString().padStart(6, '0')

internal fun arcadeMove(@Suppress("UNUSED_PARAMETER") dir: Int): ContentTransform = fadeIn(tween(60)) togetherWith fadeOut(tween(60))

@Composable
internal fun AnimatedVisibilityScope.ArcadePage(ctx: WinSceneCtx, p: WinPage, live: Boolean, font: FontFamily, line: TextUnit, small: TextUnit, modifier: Modifier) {
    val anim = rememberAnimationsEnabled()
    // The tube rolling: the new screen drawn from the top down, a bright scan line on its edge.
    val roll by transition.animateFloat(transitionSpec = { if (anim) tween(NATIVE_PAGE_MS, easing = LinearEasing) else snap() }, label = "arcRoll") {
        if (it == EnterExitState.Visible) 1f else 0f
    }
    val glow = TextStyle(fontFamily = font, shadow = Shadow(ArcPgCyan.copy(alpha = 0.55f), Offset.Zero, 10f))
    val m = modifier.drawWithContent {
        val y = size.height * roll
        drawContext.canvas.save()
        drawContext.canvas.clipRect(0f, 0f, size.width, y)
        drawContent()
        drawContext.canvas.restore()
        if (roll in 0.01f..0.99f) drawRect(Color.White.copy(alpha = 0.8f), Offset(0f, y - 1.5.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 1.5.dp.toPx()))
    }
    when (p) {
        WinPage.DETAILS -> ArcadeInstructions(ctx, live, glow, line, small, m)
        WinPage.TROPHIES -> ArcadeHighScores(ctx, live, glow, line, small, m)
        WinPage.OPTIONS -> ArcadeService(ctx, live, glow, line, small, m)
        WinPage.MAIN -> {}
        else -> ctx.pageFor(p, modifier, live) // (Performance: the shared page - not a screen of the cabinet yet)
    }
}

@Composable
private fun ArcadeInstructions(ctx: WinSceneCtx, live: Boolean, glow: TextStyle, line: TextUnit, small: TextUnit, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    Column(modifier.nativeScroll(ArcPgCyan), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("INSTRUCTIONS", color = ArcPgPink, fontSize = line, style = glow, lineHeight = line * ARCADE_LINE)
        val facts = nativeFacts(ctx, setOf("Trophies"))
        // (682ag, owner review 23 Nov, RP Classic x1.3: "PLAYST / ATION / 2" beside the label and its dot leader - where the card
        // is narrow the value goes under its NAME ....., in whole words.)
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 300.dp
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                facts.forEach { f ->
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides glow) {
                        if (narrow) Column(Modifier.fillMaxWidth()) {
                            Text(f.label.uppercase() + " ....", color = ArcPgCyan, fontSize = small, style = glow, lineHeight = small * ARCADE_LINE)
                            WholeWordsText(f.value.uppercase(), color = ArcPgInk, fontSize = small, fontFamily = glow.fontFamily, lineHeight = small * ARCADE_LINE,
                                modifier = Modifier.padding(start = 12.dp, top = 3.dp))
                        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            // NAME ..... VALUE: the label, a dot leader, the value (wraps under itself).
                            Text(f.label.uppercase(), color = ArcPgCyan, fontSize = small, style = glow, lineHeight = small * ARCADE_LINE)
                            Text(" ....", color = ArcPgDim, fontSize = small, style = glow, maxLines = 1, lineHeight = small * ARCADE_LINE)
                            Spacer(Modifier.width(4.dp))
                            WholeWordsText(f.value.uppercase(), color = ArcPgInk, fontSize = small, fontFamily = glow.fontFamily, lineHeight = small * ARCADE_LINE, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        NativeItem(onClick = {}, Modifier.fillMaxWidth(), focus = focus, live = live) { on ->
            Text((ctx.summary ?: "NO STORY ON THIS MACHINE YET.").uppercase(), color = if (on) ArcPgYellow else ArcPgInk, fontSize = small,
                lineHeight = small * ARCADE_LINE, style = glow,
                modifier = Modifier.fillMaxWidth().border(if (on) 2.dp else 1.dp, if (on) ArcPgYellow else ArcPgCyan.copy(alpha = 0.35f)).padding(5.dp))
        }
    }
}

@Composable
private fun ArcadeHighScores(ctx: WinSceneCtx, live: Boolean, glow: TextStyle, line: TextUnit, small: TextUnit, modifier: Modifier) {
    val t = rememberNativeTrophies(ctx)
    val focus = nativeListFocus(ctx, live)
    var openId by rememberOpenTrophy(ctx)
    Column(modifier) {
        // (682ag, owner review 23 Nov - "rework High Scores": the head, the count and the order key each on a line of their own, in
        // whole words with the pixel font's line height - beside each other at x1.3 they broke mid-word and ran into one another.)
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides glow) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                WholeWordsText("HIGH SCORES", color = ArcPgPink, fontSize = line, modifier = Modifier.weight(1f), fontFamily = glow.fontFamily, lineHeight = line * ARCADE_LINE)
                Text("${t.earned}/${t.total}", color = ArcPgDim, fontSize = small, style = glow, softWrap = false, lineHeight = small * ARCADE_LINE,
                    modifier = Modifier.padding(start = 6.dp).semantics { contentDescription = "${t.earned} of ${t.total} cleared" })
            }
        }
        NativeItem(onClick = t.nextOrder, Modifier.fillMaxWidth().padding(top = 2.dp), focus = focus, live = live) { on ->
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides glow) {
                WholeWordsText((if (on) "▶ " else "  ") + "SORT: " + t.order.label.uppercase(), color = if (on) ArcPgYellow else ArcPgCyan, fontSize = small,
                    fontFamily = glow.fontFamily, lineHeight = small * ARCADE_LINE,
                    modifier = Modifier.fillMaxWidth().background(if (on) ArcPgPink.copy(alpha = 0.16f) else Color.Transparent))
            }
        }
        Spacer(Modifier.height(4.dp))
        Column(Modifier.weight(1f).fillMaxWidth().nativeScroll(ArcPgCyan), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            t.list.forEachIndexed { i, ach ->
                val earned = ach.isUnlocked
                androidx.compose.runtime.key(ach.id) {
                    val opened = openId == ach.id
                    NativeItem(onClick = { openId = if (opened) null else ach.id }, Modifier.fillMaxWidth(), live = live) { on ->
                        val ink = if (on) ArcPgYellow else if (earned) ArcPgYellow.copy(alpha = 0.92f) else ArcPgDim
                        Column(Modifier.fillMaxWidth().background(if (on) ArcPgPink.copy(alpha = 0.16f) else Color.Transparent).padding(horizontal = 2.dp, vertical = 2.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                // (682r, RP Classic x1.3: "1S / T" in a fixed 40 dp and the title one word a line beside the score - the rank
                                // as wide as it is, the score under the title)
                                Text(arcadeRank(i), color = if (earned) ArcPgCyan else ArcPgDim, fontSize = small, style = glow, softWrap = false,
                                    modifier = Modifier.widthIn(min = 40.dp).padding(end = 4.dp))
                                Column(Modifier.weight(1f)) {
                                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides glow) {
                                        WholeWordsText((ach.title ?: "UNTITLED").uppercase(), color = ink, fontSize = small, fontFamily = glow.fontFamily, lineHeight = small * ARCADE_LINE)
                                    }
                                    Text(arcadeScore(ach.points, earned), color = ink, fontSize = small, style = glow, softWrap = false, lineHeight = small * ARCADE_LINE,
                                        modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                            ach.description?.let { d ->
                                // (682ag, owner review 23 Nov: one sliding line cut the description mid-word - it is shown whole, in whole
                                // words, on the entry you're on (or opened with A); the table keeps one entry's room otherwise, so the
                                // first entry stays whole on the RP Classic at x1.3.)
                                if (on || opened) androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides glow) {
                                    WholeWordsText(d.uppercase(), color = if (opened) ArcPgInk else ArcPgDim, fontSize = small, fontFamily = glow.fontFamily,
                                        lineHeight = small * ARCADE_LINE, modifier = Modifier.padding(start = 44.dp, top = 2.dp))
                                }
                            }
                            if (opened) {
                                val facts = listOfNotNull(
                                    (ach.dateEarnedHardcore ?: ach.dateEarned)?.let { "CLEARED " + it.take(10) },
                                    ach.unlockPercent?.let { "%.1f%% OF PLAYERS".format(it) },
                                    ach.type?.takeIf { it.isNotBlank() }?.uppercase()
                                )
                                if (facts.isNotEmpty()) Text(facts.joinToString("  "), color = ArcPgCyan, fontSize = small, style = glow, lineHeight = small * ARCADE_LINE,
                                    modifier = Modifier.padding(start = 44.dp, top = 1.dp))
                            }
                        }
                    }
                }
            }
            if (t.list.isEmpty()) Text("NO HIGH SCORES ON THIS MACHINE.", color = ArcPgDim, fontSize = small, style = glow)
        }
    }
}

@Composable
private fun ArcadeService(ctx: WinSceneCtx, live: Boolean, glow: TextStyle, line: TextUnit, small: TextUnit, modifier: Modifier) {
    val focus = nativeListFocus(ctx, live)
    val confirm = rememberNativeConfirm(ctx)
    Column(modifier.nativeScroll(ArcPgCyan), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("SERVICE MODE", color = ArcPgPink, fontSize = line, style = glow, lineHeight = line * ARCADE_LINE)
        ctx.options.forEachIndexed { i, o ->
            NativeItem(onClick = { if (confirm.press(i, o.destructive)) o.onClick() }, Modifier.fillMaxWidth(), focus = if (i == 0) focus else null, live = live) { on ->
                androidx.compose.runtime.LaunchedEffect(on) { if (!on) confirm.leave(i) }
                val armed = confirm.armed == i
                val ink = if (o.destructive) ArcPgPink else if (on) ArcPgYellow else ArcPgInk
                // (682ag, owner review 23 Nov: the lines in whole words with the pixel font's line height and a gap between items -
                // "ADD TO / FAVORITES" and a description ran into the next line at x1.3.)
                Column(Modifier.fillMaxWidth().background(if (on) ArcPgPink.copy(alpha = 0.16f) else Color.Transparent).padding(vertical = 3.dp)) {
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides glow) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text("▶", color = if (on) ArcPgYellow else Color.Transparent, fontSize = line, style = if (on) glow else glow.copy(shadow = null), lineHeight = line * ARCADE_LINE,
                                modifier = Modifier.clearAndSetSemantics {})
                            Spacer(Modifier.width(6.dp))
                            WholeWordsText(o.label.uppercase(), color = ink, fontSize = line, fontFamily = glow.fontFamily, lineHeight = line * ARCADE_LINE, modifier = Modifier.weight(1f))
                        }
                        o.sub?.let { WholeWordsText(it.uppercase(), color = ArcPgDim, fontSize = small, fontFamily = glow.fontFamily, lineHeight = small * ARCADE_LINE,
                            modifier = Modifier.padding(start = 18.dp, top = 2.dp)) }
                    }
                    if (armed) Text("PRESS A AGAIN TO CONFIRM", color = ArcPgPink, fontSize = small, style = glow, lineHeight = small * ARCADE_LINE,
                        modifier = Modifier.padding(start = 18.dp, top = 2.dp).border(1.dp, ArcPgPink).padding(horizontal = 4.dp, vertical = 1.dp))
                    if (armed) Text("MOVE AWAY TO CANCEL", color = ArcPgCyan, fontSize = small, style = glow, lineHeight = small * ARCADE_LINE,
                        modifier = Modifier.padding(start = 18.dp, top = 1.dp))
                }
            }
        }
        if (ctx.options.isEmpty()) Text("NO SERVICE ITEMS.", color = ArcPgDim, fontSize = small, style = glow)
    }
}
