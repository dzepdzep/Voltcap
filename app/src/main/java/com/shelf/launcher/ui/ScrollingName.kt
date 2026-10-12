package com.shelf.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A game's name that never gets cut off: it wraps onto up to two lines and, when that still isn't enough, the one you're
 * on ([scrolling]) slides along a single line so you can read all of it. Always takes room for [lines] lines, so rows stay even.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ScrollingName(
    text: String, scrolling: Boolean, color: Color, fontSize: TextUnit, modifier: Modifier = Modifier,
    lineHeight: TextUnit = (fontSize.value * 1.2f).sp, fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null, style: TextStyle = TextStyle.Default, lines: Int = 2
) {
    var overflows by remember(text, fontSize) { mutableStateOf(false) }
    if (scrolling && overflows && !LocalLiteEffects.current) {
        val h = with(LocalDensity.current) { lineHeight.toDp() } * lines
        Box(modifier.height(h), contentAlignment = Alignment.TopStart) {
            Text(
                text, color = color, fontSize = fontSize, lineHeight = lineHeight, fontWeight = fontWeight, textAlign = textAlign, style = style,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 700, repeatDelayMillis = 900, velocity = 36.dp)
            )
        }
    } else {
        Text(
            text, color = color, fontSize = fontSize, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(fontSize), maxFontSize = fontSize), lineHeight = lineHeight, fontWeight = fontWeight, textAlign = textAlign, style = style,
            maxLines = lines, minLines = lines, overflow = TextOverflow.Ellipsis,
            onTextLayout = { overflows = it.hasVisualOverflow },
            modifier = modifier
        )
    }
}
