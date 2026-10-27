package com.shelf.launcher.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.roundToInt

/**
 * A 0–100 % slider for settings (blur strength, dimming…). Drag with touch;
 * the value is saved when you let go, so dragging doesn't rewrite settings
 * on every frame.
 */
@Composable
internal fun PercentSlider(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    min: Int = 0,
    max: Int = 100,
    /** Report every step while dragging (live preview), not only when let go. */
    live: Boolean = false,
    /** What the value reads as on the right (default "42%"); the label shouldn't repeat it. */
    valueText: ((Int) -> String)? = null
) {
    com.shelf.launcher.Breadcrumbs.mark("PercentSlider")
    var drag by remember(value) { mutableStateOf(value.toFloat()) }
    // D-pad: the whole control is one focus stop (with the shared focus ring)
    // and ◀ ▶ step it by 5 %. The Slider itself is taken out of focus
    // traversal so it isn't a second, invisible stop; touch is unaffected.
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .then(jumpAnchor(label))
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val dir = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> 0
                }
                if (!enabled || dir == 0) return@onPreviewKeyEvent false
                // QA (C2): small ranges (a count of buttons, an angle) step by 1 — by 5 only 0, 5 and 8 of "0..8" could be
                // picked with the D-pad. QA (C9): at the end of the range ◀ ▶ are let through, so focus can leave the
                // slider sideways (two-column settings, the R1 panel) instead of being stuck on it.
                val step = if (max - min <= 30) 1 else 5
                val cur = drag.roundToInt()
                if ((dir < 0 && cur <= min) || (dir > 0 && cur >= max)) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) {
                    val next = if (step == 1) cur + dir else ((drag / 5f).roundToInt() + dir) * 5
                    val clamped = next.coerceIn(min, max)
                    if (clamped != cur) {
                        drag = clamped.toFloat()
                        onChange(clamped)
                    }
                }
                true
            }
            .focusable(enabled)
            .focusRing(focused, RoundedCornerShape(10.dp))
            .padding(horizontal = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = ShelfTheme.textMuted,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                valueText?.invoke(drag.roundToInt()) ?: "${drag.roundToInt()}%",
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = ShelfTheme.colors.textPrimary,
                fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp),
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            modifier = Modifier.focusProperties { canFocus = false },
            value = drag,
            onValueChange = { drag = it; if (live) onChange(it.roundToInt().coerceIn(min, max)) },
            onValueChangeFinished = { onChange(drag.roundToInt().coerceIn(min, max)) },
            valueRange = min.toFloat()..max.toFloat(),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = ShelfTheme.accent,
                activeTrackColor = ShelfTheme.accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
            )
        )
    }
}
