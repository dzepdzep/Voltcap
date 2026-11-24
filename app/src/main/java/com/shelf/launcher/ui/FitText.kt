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
import androidx.compose.ui.unit.em
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
    maxHeight: Int = 0,
    /**
     * 676r (D340, rule 11): where even the smallest size and an extra line don't fit, slide the whole text along one line
     * (FullText's slow marquee) instead of ending it in "…". Off keeps the old last resort for the music players' lyrics.
     */
    slideWhenCut: Boolean = false,
    /**
     * 678r (D350): false = never take the extra line - where the smallest size doesn't fit [maxLines], slide (needs
     * [slideWhenCut]). For a plate of fixed height over art (the Channel grid's name), where a second line rose over the badges.
     */
    extraLine: Boolean = true,
    /**
     * 682as (owner 24 Nov, Arcade): a size fits only when the longest word fits a line too - words wrap whole, never split.
     */
    wholeWords: Boolean = false,
    /**
     * 682as (owner 24 Nov, Arcade - "prefer static text for unfocused machines"): where nothing fits and [slideWhenCut] is off, the
     * text stays still at the smallest size - whole lines only, no "…" - instead of the lyrics' ellipsis. For text whose whole
     * wording is reachable elsewhere (the machine you move onto shows it in full, and the game's name is beside it).
     */
    stillWhenCut: Boolean = false
) {
    // Centred / end-aligned text fills the width, so it's placed the same way while it fades in and out
    // (it used to start at the left edge and then jump to the middle once the old line was gone).
    val wide = textAlign == TextAlign.Center || textAlign == TextAlign.End
    BoxWithConstraints(modifier, contentAlignment = if (wide) androidx.compose.ui.Alignment.TopCenter else androidx.compose.ui.Alignment.TopStart) {
        val measurer = rememberTextMeasurer(cacheSize = 8)
        val w = constraints.maxWidth
        val parentH = if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity
        val h = if (maxHeight > 0) minOf(maxHeight, parentH) else parentH
        val fit = remember(text, w, h, maxSize, minSize, maxLines, fontWeight, style, lineHeightRatio, extraLine, wholeWords, stillWhenCut) {
            fun styled(s: Float) = style.copy(fontSize = s.sp, lineHeight = (s * lineHeightRatio).sp, fontWeight = fontWeight ?: style.fontWeight)
            val longest = if (wholeWords) text.split(' ', '\n').maxByOrNull { it.length }.orEmpty() else ""
            fun fits(s: Float, lines: Int): Boolean {
                if (w <= 0 || w == Constraints.Infinity) return true
                if (longest.isNotEmpty() && measurer.measure(longest, styled(s), softWrap = false, maxLines = 1).size.width > w) return false
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
            // (682au, owner 24 Nov - Arcade B: on the RP Classic the step-down cost ~1.5 ms per text, ~11 ms of each ~100 ms game
            // change. The same steps, searched by halves: the first step that fits, in ~4 measurements instead of up to ~20.
            // FitSearch.legacy: the old walk, QA A/B only.)
            s = fitStepSize(top, bottom, FitSearch.legacy.value) { fits(it, lines) }
            if (extraLine && !fits(s, lines)) lines = maxLines + 1
            // (682as: a still text that fits nowhere at its smallest size is laid out wider and drawn condensed - the least squeeze,
            // never under half, that holds the whole text in its lines and the longest word on one line.)
            var layoutW = 0
            if (stillWhenCut && !slideWhenCut && w > 0 && w != Constraints.Infinity && !fits(s, lines)) {
                fun fitsAt(lw: Int): Boolean {
                    if (longest.isNotEmpty() && measurer.measure(longest, styled(s), softWrap = false, maxLines = 1).size.width > lw) return false
                    return !measurer.measure(text, styled(s), overflow = TextOverflow.Clip, maxLines = lines,
                        constraints = Constraints(maxWidth = lw, maxHeight = h)).hasVisualOverflow
                }
                var k = 0.95f
                while (k >= 0.5f && !fitsAt((w / k).toInt())) k -= 0.05f
                layoutW = (w / k.coerceAtLeast(0.5f)).toInt()
            }
            FitPick(s, lines, slideWhenCut && !fits(s, lines), layoutW == 0, layoutW)
        }
        @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
        // (676y: it slides at the size it shrank to - a far arcade machine's sign is a few px tall; at the full size its letters
        // were cut in height.)
        if (fit.third) FullTextMarquee(
            text, color, fit.first.sp, if (wide) Modifier.fillMaxWidth() else Modifier, fontWeight, null, TextUnit.Unspecified,
            (fit.first * lineHeightRatio).sp, textAlign, style
        ) else Text(
            text, color = color,
            fontSize = fit.first.sp, lineHeight = (fit.first * lineHeightRatio).sp,
            fontWeight = fontWeight, textAlign = textAlign, style = style,
            // qa-ok: TRUNCATE (lyrics / song titles in a fixed box: only after the smallest readable size AND an extra line)
            maxLines = fit.second, overflow = if (stillWhenCut) TextOverflow.Clip else TextOverflow.Ellipsis,
            // (682as: a word wider than the line even at the smallest size is never split: the lines are laid out as wide as that
            // word and drawn narrower - condensed letters, as an arcade screen's lettering is - at the smallest size's height.)
            modifier = (if (wide) Modifier.fillMaxWidth() else Modifier).then(
                if (!fit.fourth) Modifier.condensedTo(fit.wordW) else Modifier)
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
    reserve: Boolean = true,
    /** 682aq (D386): the fit worked out inside the text's own measure ([StableFitAutoSize]) - no subcomposition per text. The
     *  same steps and result; the reserved height keeps the old way (its height follows the lines the fit chose). */
    inMeasure: Boolean = !reserve
) {
    if (inMeasure) { StableFitText(text, color, fontSize, modifier, maxLines, fontWeight, lineHeight, letterSpacing, fontFamily, textAlign, style); return }
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
        val fit = remember(text, w, h, top, bottom, maxLines, fontWeight, base, ratio, letterSpacing) { vcTrace("VC:stableFit") {
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
        } }
        FullText(
            text, color = color, fontSize = fit.first.sp, maxLines = fit.second, fontWeight = fontWeight,
            lineHeight = (fit.first * ratio).sp, letterSpacing = letterSpacing, autoFit = false,
            fontFamily = fontFamily, textAlign = textAlign, style = style, reserve = reserve
        )
    }
}

/**
 * 682aq (D386, owner 22:43: "continue investigating StableFullText ... don't change text appearance just to improve measurements"):
 * [StableFullText]'s fit without its BoxWithConstraints - the size steps down 1 sp at a time from the full size to [fitMin] until
 * the text fits [maxLines] lines (one more line, for 2-3 lines, where the room allows and it still doesn't fit), measured by the
 * Text itself in its layout pass. Past the minimum it slides, at the minimum, as before.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun StableFitText(
    text: String, color: Color, fontSize: TextUnit, modifier: Modifier, maxLines: Int, fontWeight: FontWeight?, lineHeight: TextUnit,
    letterSpacing: TextUnit, fontFamily: androidx.compose.ui.text.font.FontFamily?, textAlign: TextAlign?, style: TextStyle
) {
    val top = fontSize.value
    val bottom = fitMin(fontSize).value
    val ratio = if (lineHeight.isSp && top > 0f) lineHeight.value / top else 1.2f
    val extra = maxLines in 2..3
    var slideAt by androidx.compose.runtime.remember(text, fontSize, maxLines) { androidx.compose.runtime.mutableIntStateOf(-1) }
    val cw = androidx.compose.runtime.remember { IntArray(1) }
    val widthMod = Modifier.layout { m, c ->
        cw[0] = c.maxWidth
        if (slideAt >= 0 && c.maxWidth > slideAt) slideAt = -1
        val pl = m.measure(c); layout(pl.width, pl.height) { pl.place(0, 0) }
    }
    if (slideAt >= 0) FullTextMarquee(text, color, bottom.sp, modifier.then(widthMod), fontWeight, fontFamily, letterSpacing, (bottom * ratio).sp, textAlign, style)
    else Text(
        text, color = color, fontSize = fontSize, fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing,
        lineHeight = ratio.em, textAlign = textAlign, style = style, maxLines = if (extra) maxLines + 1 else maxLines, overflow = TextOverflow.Clip,
        autoSize = StableFitAutoSize(top, bottom, maxLines),
        onTextLayout = { if (it.hasVisualOverflow) slideAt = cw[0].coerceAtLeast(0) },
        modifier = modifier.then(widthMod)
    )
}

/** 682aq: [StableFullText]'s steps as a [androidx.compose.foundation.text.TextAutoSize]: the largest size, 1 sp apart from [max]
 *  down to [min], whose text has no overflow in [lines] lines. The text's own maxLines may allow one line more (used only at
 *  [min]). The last layout is always the size returned (the scope keeps the last one it made). */
internal data class StableFitAutoSize(val max: Float, val min: Float, val lines: Int) : androidx.compose.foundation.text.TextAutoSize {
    override fun androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope.getFontSize(
        constraints: Constraints, text: androidx.compose.ui.text.AnnotatedString
    ): TextUnit {
        val w = constraints.maxWidth
        var s = max
        if (w > 0 && w != Constraints.Infinity) {
            while (s > min) {
                val r = performLayout(constraints, text, s.sp)
                if (!r.hasVisualOverflow && r.lineCount <= lines) break
                s = maxOf(min, s - 1f)
            }
        }
        performLayout(constraints, text, s.sp)
        return s.sp
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
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    /** Batch 523: the lines' alignment (a centred plate); default as before. */
    textAlign: TextAlign? = null
) {
    // (682al, D386 - owner 23 Nov 18:23 "the launcher must feel smooth": a native page change spent 50-140 ms composing and
    // laying out its new page on the KONKR, most of it these texts - each one a BoxWithConstraints (a composition of its own at
    // layout time) plus its own text measurer. The same step-down now runs inside the text's own layout ([WholeWordsAutoSize]):
    // same sizes, same whole words, no subcomposition.)
    val ratio = if (lineHeight.isSpecified && lineHeight.isSp && fontSize.value > 0f) lineHeight.value / fontSize.value else 1.25f
    val longest = remember(text) { text.split(' ', '\n').maxByOrNull { it.length } ?: "" }
    val auto = remember(fontSize, longest) { WholeWordsAutoSize(fontSize.value, fitMin(fontSize).value, longest) }
    // (682ag: an aligned text takes the line's width - laid out at its content's width a centred line measured past its own
    // right edge, a false "cut" in the text checks.)
    Text(text, color = color, fontSize = fontSize, lineHeight = ratio.em, fontWeight = fontWeight, fontFamily = fontFamily, textAlign = textAlign,
        autoSize = auto, modifier = if (textAlign != null) modifier.fillMaxWidth() else modifier)
}

/**
 * 682al: [WholeWordsText]'s size - from [max] down 1 sp at a time while the longest word is wider than the line, never under [min]
 * (its readable minimum). Runs in the text's own layout pass. The last layout it asks for is always the whole text at the chosen
 * size: Compose keeps the search's last layout when its size matches, and that must be the text, never the lone word.
 */
internal data class WholeWordsAutoSize(val max: Float, val min: Float, val longest: String) : androidx.compose.foundation.text.TextAutoSize {
    override fun androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope.getFontSize(
        constraints: Constraints, text: androidx.compose.ui.text.AnnotatedString
    ): TextUnit {
        val w = constraints.maxWidth
        var s = max
        if (w > 0 && w != Constraints.Infinity && longest.isNotEmpty()) {
            val word = androidx.compose.ui.text.AnnotatedString(longest)
            while (s > min && performLayout(Constraints(), word, s.sp).size.width > w) s = maxOf(min, s - 1f)
        }
        performLayout(constraints, text, s.sp)
        return s.sp
    }
}

/**
 * 677h (owner, RP Classic: Storage Boxes "6 / 1 0", "Neve / r play / ed"): a line break inside a word (677l: detection only on
 * the v0.6.73 line - QaClip "brokenWord"; the fitters keeping words whole moved to v0.6.74, branch claude/whole-words-v0674) - the two characters
 * either side of a line end are letters or digits of a script that separates its words with spaces. Chinese, Japanese,
 * Korean, Thai and their neighbours break between characters by design and never count.
 */
internal fun brokenWord(r: androidx.compose.ui.text.TextLayoutResult): Boolean =
    brokenWordAt(r.layoutInput.text.text, IntArray((r.lineCount - 1).coerceAtLeast(0)) { r.getLineEnd(it) })

/** 677h: [brokenWord] on the text and the offsets its lines end at (all lines but the last). */
internal fun brokenWordAt(t: String, lineEnds: IntArray): Boolean =
    lineEnds.any { e -> e in 1 until t.length && wordChar(t[e - 1]) && wordChar(t[e]) }

private fun wordChar(c: Char): Boolean {
    if (!c.isLetterOrDigit()) return false
    return when (Character.UnicodeScript.of(c.code)) {
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL, Character.UnicodeScript.THAI, Character.UnicodeScript.LAO,
        Character.UnicodeScript.KHMER, Character.UnicodeScript.MYANMAR, Character.UnicodeScript.TIBETAN -> false
        else -> true
    }
}

/** 682as: [FitText]'s choice - size, lines, slide, whether it fits as it is, and else the width it is laid out at (drawn condensed). */
private data class FitPick(val first: Float, val second: Int, val third: Boolean, val fourth: Boolean, val wordW: Int)

/** 682as: lay the content out [wide] px wide (its longest word's width) and draw it squeezed into the width it was given -
 *  horizontally only, never below half; centred in that width. */
private fun Modifier.condensedTo(wide: Int): Modifier = layout { m, c ->
    val room = c.maxWidth
    val k = (room.toFloat() / wide).coerceIn(0.5f, 1f)
    val inner = (room / k).toInt()
    val p = m.measure(c.copy(minWidth = minOf(c.minWidth, inner), maxWidth = inner))
    val drawn = (p.width * k).toInt()
    layout(room, p.height) {
        p.placeWithLayer((room - drawn) / 2, 0) {
            scaleX = k
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
        }
    }
}

/** 682au: QA-only same-build A/B for [FitText]'s size search (`--es voltcap_test_fitsearch legacy|new`, isolated QA app, session). */
internal object FitSearch { val legacy = androidx.compose.runtime.mutableStateOf(false) }

/** 682au: [FitText]'s size - the first of the ~6 % steps down from [top] to [bottom] that [fits], else [bottom]. [legacy]: tried one
 *  by one (the old walk); else galloping then halves (the same steps, the same answer where a smaller size never fits worse). Pure. */
internal fun fitStepSize(top: Float, bottom: Float, legacy: Boolean, fits: (Float) -> Boolean): Float {
    if (legacy) { var s = top; while (s > bottom && !fits(s)) s = maxOf(bottom, s * 0.94f - 0.1f); return s }
    if (top <= bottom || fits(top)) return top
    val steps = ArrayList<Float>(24).apply { var t = top; while (t > bottom) { t = maxOf(bottom, t * 0.94f - 0.1f); add(t) } }
    // (galloping: steps 1, 2, 4, 8 ... down until one fits, then halves between - a fit just under the top costs what the walk
    // did, one near the bottom ~log2 of the walk)
    val last = steps.size - 1
    var prev = -1; var probe = 0; var stride = 1
    while (true) {
        if (fits(steps[probe])) break
        prev = probe
        if (probe == last) return steps[last]
        probe = minOf(last, probe + stride); stride *= 2
    }
    var lo = prev + 1; var hi = probe
    while (lo < hi) { val mid = (lo + hi) / 2; if (fits(steps[mid])) hi = mid else lo = mid + 1 }
    return steps[lo]
}
