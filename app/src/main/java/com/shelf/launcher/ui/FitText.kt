package com.shelf.launcher.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.semantics

/**
 * Text that shrinks (from [maxSize] down to [minSize]) until it fits the room it's given in at most
 * [maxLines] lines — wrapping first, getting smaller second, and only cutting off with "…" when even
 * the smallest size plus one extra line can't hold it. Used for lyrics and song titles, which used to be
 * cut off at the screen edge or after a fixed two lines on small screens (the lower screen of a
 * dual-screen handheld especially).
 *
 * The height it may use comes from the incoming constraints (bounded) or [maxHeight] in px.
 */
@Composable
internal fun FitText(
    text: String,
    color: Color,
    maxSize: TextUnit,
    minSize: TextUnit,
    maxLines: Int,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    // The surrounding text style (the interface font: plain, 32-bit…), so a fitted title or lyric uses
    // the same font as the text next to it (it used to fall back to the default font).
    style: TextStyle = uiFontStyle(),
    lineHeightRatio: Float = 1.18f,
    /** Extra limit on the height in px (0 = only what the parent allows). */
    maxHeight: Int = 0
) {
    // Centred / end-aligned text fills the width, so it's placed the same way while it fades in and out
    // (it used to start at the left edge and then jump to the middle once the old line was gone).
    val wide = textAlign == TextAlign.Center || textAlign == TextAlign.End
    BoxWithConstraints(modifier, contentAlignment = if (wide) androidx.compose.ui.Alignment.TopCenter else androidx.compose.ui.Alignment.TopStart) {
        val measurer = rememberTextMeasurer(cacheSize = 8)
        val w = constraints.maxWidth
        val parentH = if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity
        val h = if (maxHeight > 0) minOf(maxHeight, parentH) else parentH
        val fit = remember(text, w, h, maxSize, minSize, maxLines, fontWeight, style, lineHeightRatio) {
            fun styled(s: Float) = style.copy(fontSize = s.sp, lineHeight = (s * lineHeightRatio).sp, fontWeight = fontWeight ?: style.fontWeight)
            fun fits(s: Float, lines: Int): Boolean {
                if (w <= 0 || w == Constraints.Infinity) return true
                val r = measurer.measure(
                    text, styled(s), overflow = TextOverflow.Clip, maxLines = lines,
                    constraints = Constraints(maxWidth = w, maxHeight = h)
                )
                return !r.hasVisualOverflow
            }
            val top = maxSize.value
            val bottom = minOf(minSize.value, top)
            var s = top
            var lines = maxLines
            // Step down ~6 % at a time: a handful of measurements at most, and only when the text changes.
            while (s > bottom && !fits(s, lines)) s = maxOf(bottom, s * 0.94f - 0.1f)
            if (!fits(s, lines)) lines = maxLines + 1
            s to lines
        }
        Text(
            text, color = color,
            fontSize = fit.first.sp, lineHeight = (fit.first * lineHeightRatio).sp,
            fontWeight = fontWeight, textAlign = textAlign, style = style,
            // qa-ok: TRUNCATE (lyrics / song titles in a fixed box: only after the smallest readable size AND an extra line)
            maxLines = fit.second, overflow = TextOverflow.Ellipsis,
            modifier = if (wide) Modifier.fillMaxWidth() else Modifier
        )
    }
}

/**
 * Only the interface font (Normal / 32-bit, Appearance › Interface style) as a style to build on: texts that set a
 * style of their own (a shadow, centred lines) keep their sizes and spacing and just take the font.
 */
@Composable
internal fun uiFontStyle(): TextStyle = TextStyle(fontFamily = androidx.compose.material3.LocalTextStyle.current.fontFamily)

/**
 * Update 136: the smallest size a text that shrinks to fit may reach — 60 % of [size], but never under 10 sp (or
 * [size] itself, when that's smaller). Text that shrank to 6–7 sp was barely readable on a handheld's screen; past
 * this it ends in "…" instead.
 */
internal fun fitMin(size: TextUnit): TextUnit =
    if (size.isSp) kotlin.math.max(size.value * 0.6f, kotlin.math.min(size.value, 10f)).sp else size * 0.6f


/**
 * Permanent rule (CLAUDE.md #11, QA R18): a label shown whole, never cut with "…". It wraps onto up to [maxLines] lines,
 * shrinks to a size that stays readable ([fitMin], when [autoFit]), and if it still doesn't fit it slides along one line
 * (a slow marquee) so all of it can be read. Use it instead of `maxLines = n, overflow = TextOverflow.Ellipsis`.
 */
@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
private fun FullTextMarquee(
    text: String, color: Color, fontSize: TextUnit, modifier: Modifier, fontWeight: FontWeight?, fontFamily: androidx.compose.ui.text.font.FontFamily?,
    letterSpacing: TextUnit, lineHeight: TextUnit, textAlign: TextAlign?, style: TextStyle
) {
    Text(
        text, color = color, fontSize = fontSize, fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing,
        lineHeight = lineHeight, textAlign = textAlign, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
        // (RP Classic QA: a slide never stops, so a home screen with long names redrew forever. It rests with the home
        // screen — 30 s without input, HomeIdle.calm — and starts again with the first press.)
        // (Update 477: marked as sliding for the QA gate — QaClip.Slides — whether it moves now or rests.)
        modifier = (if (calmHere()) modifier else modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1200, repeatDelayMillis = 1500, velocity = 28.dp))
            .semantics { this[QaClip.Slides] = true }
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun FullText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    fontWeight: FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current,
    /** Shrink (to [fitMin]) before sliding; off where a size that changes while the screen moves would flicker. */
    autoFit: Boolean = true,
    /**
     * Update 460 (owner: "the cube layout jumps up and down depending on how big the text under the games is — never do
     * this"): the text always takes the height of [maxLines] lines at its full size, whether the name needs one line or
     * two, shrinks or slides — so whatever sits above or below it never moves with the name. Off only where the text is
     * centred over a picture by itself (a missing-cover placeholder), which nothing else is laid out around.
     */
    reserve: Boolean = true
) {
    // (Update 470, long-name run: the slide was decided once and kept — a window's first layout pass, narrower while it opens
    // beside an inspected piece, made a name overflow and it then slid for good on one line although its full width had room
    // for the wrapped lines (Pocket Quest, Cover details, Resume). The width it slid at is kept; a wider one tries again.)
    var slideAt by androidx.compose.runtime.remember(text, fontSize, maxLines) { androidx.compose.runtime.mutableIntStateOf(-1) }
    val cw = androidx.compose.runtime.remember { IntArray(1) }
    val widthMod = Modifier.layout { m, c ->
        cw[0] = c.maxWidth
        if (slideAt >= 0 && c.maxWidth > slideAt) slideAt = -1
        val pl = m.measure(c); layout(pl.width, pl.height) { pl.place(0, 0) }
    }
    val reserveMod = if (reserve) Modifier.heightIn(min = reservedLinesHeight(fontSize, lineHeight, fontWeight, fontFamily, letterSpacing, style, maxLines)) else Modifier
    if (slideAt >= 0) FullTextMarquee(text, color, fontSize, modifier.then(widthMod).then(reserveMod), fontWeight, fontFamily, letterSpacing, lineHeight, textAlign, style)
    else Text(
        text, color = color, fontSize = fontSize, fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing,
        lineHeight = lineHeight, textAlign = textAlign, style = style, maxLines = maxLines, overflow = TextOverflow.Clip,
        autoSize = if (autoFit) androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = fitMin(fontSize), maxFontSize = fontSize) else null,
        onTextLayout = {
            if (it.hasVisualOverflow) {
                slideAt = cw[0].coerceAtLeast(0)
                if (QaTrace.on) android.util.Log.e("VoltcapQA", "slide '${text.take(24)}' w=${cw[0]} lines=${it.lineCount}/$maxLines size=${it.size} ow=${it.didOverflowWidth} oh=${it.didOverflowHeight} c=${it.layoutInput.constraints}")
            }
        },
        modifier = modifier.then(widthMod).then(reserveMod)
    )
}

/**
 * Update 460: the height [lines] lines of text take at [fontSize] (measured once per size and style, not per frame) — what
 * [FullText] keeps for a name however many lines it really needs, and what a plain `Text` under a game row can be given
 * (`Modifier.heightIn(min = reservedLinesHeight(…))`) for the same reason.
 */
@Composable
internal fun reservedLinesHeight(
    fontSize: TextUnit, lineHeight: TextUnit = TextUnit.Unspecified, fontWeight: FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null, letterSpacing: TextUnit = TextUnit.Unspecified,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current, lines: Int = 1
): androidx.compose.ui.unit.Dp {
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val density = androidx.compose.ui.platform.LocalDensity.current
    return remember(fontSize, lineHeight, fontWeight, fontFamily, letterSpacing, style, lines, density) {
        val probe = List(lines.coerceAtLeast(1)) { "Ag" }.joinToString("\n")
        val r = measurer.measure(
            probe, style.merge(TextStyle(fontSize = fontSize, lineHeight = lineHeight, fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing)),
            maxLines = lines.coerceAtLeast(1), softWrap = false
        )
        with(density) { r.size.height.toDp() }
    }
}

/**
 * Update 363 (Cinema / Arctic hub, Thor): [FullText] whose size that fits is worked out in the same layout pass as the room it
 * gets — [FullText]'s own auto-size draws nothing for a few frames after each change while it fits itself, so a name in a
 * fixed slot popped in late. Same wrapping, same readable minimum ([fitMin]), same slide as the last resort.
 */
@Composable
internal fun StableFullText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    fontWeight: FontWeight? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    // (Update 478, D271: the window scenes' own type — a mono / pixel face, italic, an outline or glow — so their names could
    // move from a plain Text cut at maxLines to this one without changing how they look.)
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    textAlign: TextAlign? = null,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current,
    /** Update 478: keep the height of [maxLines] lines whatever the text needs (home rows); a window's own text: false. */
    reserve: Boolean = true
) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer(cacheSize = 8)
        val base = if (fontFamily != null) style.copy(fontFamily = fontFamily) else style
        val w = constraints.maxWidth
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity
        val top = fontSize.value
        val bottom = fitMin(fontSize).value
        val ratio = if (lineHeight.isSp && top > 0f) lineHeight.value / top else 1.2f
        // (Update 470, long-name run: "From TV Animation One Piece Yume No Luffy Kaizokudan Tanjou!" still overflowed three lines at
        // the smallest readable size and slid sideways in Pocket Quest, Cover details, Resume and Bubbles although the room
        // under it was free. Where the height allows, one more line is used before the text slides.)
        val fit = remember(text, w, h, top, bottom, maxLines, fontWeight, base, ratio, letterSpacing) {
            fun over(s: Float, lines: Int) = measurer.measure(
                // (Update 470: the theme's own letter spacing when none is given — copy(letterSpacing = Unspecified) measured
                // the name without it, narrower than the Text draws it: "fits in 3 lines" here, 4 on screen, and it slid.)
                text, base.copy(fontSize = s.sp, lineHeight = (s * ratio).sp, fontWeight = fontWeight ?: base.fontWeight,
                    letterSpacing = if (letterSpacing.isSpecified) letterSpacing else base.letterSpacing),
                overflow = TextOverflow.Clip, maxLines = lines, constraints = Constraints(maxWidth = w, maxHeight = h)
            ).hasVisualOverflow
            var s = top
            while (s > bottom && w > 0 && w != Constraints.Infinity) {
                if (!over(s, maxLines)) break
                s = maxOf(bottom, s - 1f)
            }
            val lines = if (maxLines in 2..3 && w > 0 && w != Constraints.Infinity && over(s, maxLines) && !over(s, maxLines + 1)) maxLines + 1 else maxLines
            s to lines
        }
        FullText(
            text, color = color, fontSize = fit.first.sp, maxLines = fit.second, fontWeight = fontWeight,
            lineHeight = (fit.first * ratio).sp, letterSpacing = letterSpacing, autoFit = false,
            fontFamily = fontFamily, textAlign = textAlign, style = style, reserve = reserve
        )
    }
}

/**
 * Update 478 (D271): a window's name or line — [StableFullText] without the reserved height, so a short name takes one line
 * and the window's other parts keep their room (the Blades guide's Back was pushed under its edge by a reserved 2nd line).
 */
@Composable
internal fun WinFullText(
    text: String, color: Color, fontSize: TextUnit, modifier: Modifier = Modifier, maxLines: Int = 1, fontWeight: FontWeight? = null,
    lineHeight: TextUnit = TextUnit.Unspecified, letterSpacing: TextUnit = TextUnit.Unspecified,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null, textAlign: TextAlign? = null,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current
) = StableFullText(text, color, fontSize, modifier, maxLines, fontWeight, lineHeight, letterSpacing, fontFamily, textAlign, style, reserve = false)

/**
 * Update 471 (owner review, Box Details page: "(Parmentat / ion)"): a wrapping text whose size steps down until its longest
 * word fits a line — so normal words are never split across lines — down to the readable minimum ([fitMin]); only a word
 * longer than a line at that size still breaks. Wraps freely otherwise (no line limit, nothing hidden).
 */
@Composable
internal fun WholeWordsText(
    text: String, color: Color, fontSize: TextUnit, modifier: Modifier = Modifier, fontWeight: FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null, lineHeight: TextUnit = TextUnit.Unspecified
) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer(cacheSize = 8)
        val base = androidx.compose.material3.LocalTextStyle.current
        val w = constraints.maxWidth
        val size = remember(text, w, fontSize, fontWeight, fontFamily, base) {
            val longest = text.split(' ', '\n').maxByOrNull { it.length } ?: ""
            var s = fontSize.value
            val bottom = fitMin(fontSize).value
            fun width(v: Float) = measurer.measure(longest, base.copy(fontSize = v.sp, fontWeight = fontWeight ?: base.fontWeight,
                fontFamily = fontFamily ?: base.fontFamily), softWrap = false, maxLines = 1).size.width
            if (w > 0 && w != Constraints.Infinity && longest.isNotEmpty()) while (s > bottom && width(s) > w) s = maxOf(bottom, s - 1f)
            s
        }
        val ratio = if (lineHeight.isSpecified && lineHeight.isSp && fontSize.value > 0f) lineHeight.value / fontSize.value else 1.25f
        Text(text, color = color, fontSize = size.sp, lineHeight = (size * ratio).sp, fontWeight = fontWeight, fontFamily = fontFamily)
    }
}
