package com.shelf.launcher.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
        modifier = modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1200, repeatDelayMillis = 1500, velocity = 28.dp)
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
    autoFit: Boolean = true
) {
    var slide by androidx.compose.runtime.remember(text, fontSize, maxLines) { androidx.compose.runtime.mutableStateOf(false) }
    if (slide) FullTextMarquee(text, color, fontSize, modifier, fontWeight, fontFamily, letterSpacing, lineHeight, textAlign, style)
    else Text(
        text, color = color, fontSize = fontSize, fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing,
        lineHeight = lineHeight, textAlign = textAlign, style = style, maxLines = maxLines, overflow = TextOverflow.Clip,
        autoSize = if (autoFit) androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = fitMin(fontSize), maxFontSize = fontSize) else null,
        onTextLayout = { if (it.hasVisualOverflow) slide = true },
        modifier = modifier
    )
}
