package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** "#FF8A1E" for an ARGB colour (alpha left out). */
internal fun hexOf(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

/** "#ff8a1e", "ff8a1e", "#f81" (short form) → opaque ARGB; null when it isn't a colour. */
internal fun parseHex(text: String): Int? {
    var t = text.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
    if (t.length == 3) t = t.map { "$it$it" }.joinToString("")
    if (t.length == 8) t = t.substring(2) // AARRGGBB: the alpha is ignored
    if (t.length != 6 || t.any { it.digitToIntOrNull(16) == null }) return null
    return (0xFF000000.toInt() or t.toInt(16))
}

private fun hsv(c: Int) = FloatArray(3).also { android.graphics.Color.colorToHSV(c, it) }

/**
 * Fine control for one of "Your colours": a HEX box (type any colour), and Saturation and Brightness
 * sliders that keep the hue. [color] is what's in use now (custom or the theme's own).
 */
@Composable
internal fun ColorFineTune(label: String, color: Int, onChange: (Int) -> Unit) {
    val h = remember(color) { hsv(color) }
    // (update 91) The hue is remembered across grey / black: at saturation or brightness 0 the colour has no hue
    // of its own, and raising the slider again used to turn it red instead of back to its own colour.
    // A plain holder (not state): updated while composing without triggering another composition.
    // [0] hue, [1] saturation (black has neither).
    val hueHold = remember { floatArrayOf(h[0], h[1]) }
    if (h[1] > 0.01f && h[2] > 0.01f) hueHold[0] = h[0]
    if (h[2] > 0.01f) hueHold[1] = h[1]
    var hexText by remember(color) { mutableStateOf(hexOf(color)) }
    var bad by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(color))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(10.dp))
        ShelfTextField(
            value = hexText,
            onValueChange = { t ->
                hexText = t
                // Applied as you type only once all six digits are there: the short form ("#f81") is a
                // prefix of longer colours, and applying it rewrote the box to "#FF8811" mid-typing.
                val digits = t.trim().removePrefix("#").removePrefix("0x").removePrefix("0X").length
                val c = if (digits == 6 || digits == 8) parseHex(t) else null
                bad = parseHex(t) == null && digits > 6
                if (c != null) onChange(c)
            },
            label = "$label HEX",
            modifier = Modifier.weight(1f),
            onSubmit = { parseHex(hexText)?.let(onChange) ?: run { bad = hexText.isNotBlank() } }
        )
    }
    if (bad) MutedNote("Use six hex digits, like #FF8A1E.")
    Spacer(Modifier.height(6.dp))
    PercentSlider("Saturation", (h[1] * 100).toInt(), { v ->
        onChange(android.graphics.Color.HSVToColor(floatArrayOf(hueHold[0], v / 100f, h[2])))
    })
    PercentSlider("Brightness", (h[2] * 100).toInt(), { v ->
        onChange(android.graphics.Color.HSVToColor(floatArrayOf(hueHold[0], hueHold[1], v / 100f)))
    })
}
