package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.ControllerLights
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * "Controller lights" popup opened from the Lights tile in Quick settings:
 * on/off, colour swatches + hue bar, brightness, and an Advanced section.
 * Every change applies live through the same callbacks the panel always used.
 */
@Composable
fun LightsPopup(
    enabled: Boolean,
    color: Int,
    brightness: Int,
    offWithScreensaver: Boolean,
    flashOnUnlock: Boolean,
    foundLights: List<String>,
    onLightsChange: (enabled: Boolean, color: Int, brightness: Int) -> Unit,
    onAdvancedChange: (offWithScreensaver: Boolean, flashOnUnlock: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var advanced by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    val swatches = remember {
        listOf(0xFFFFFFFF.toInt()) + listOf(0f, 30f, 55f, 120f, 175f, 210f, 270f, 315f)
            .map { android.graphics.Color.HSVToColor(floatArrayOf(it, 1f, 1f)) }
    }
    val followsTheme = color == com.shelf.launcher.data.LIGHTS_THEME_COLOR
    val shown = com.shelf.launcher.data.LightsTheme.resolve(color)
    val solid = shown or 0xFF000000.toInt()

    ShelfMenuDialog(
        title = "Controller lights",
        subtitle = "Changes apply right away.",
        icon = Icons.Filled.Lightbulb,
        onDismiss = onDismiss
    ) {
        // Three clear states: Voltcap's colour, held off by Voltcap, or left to the handheld's own setting.
        // (One on/off switch used to mean "give them back to the system", which on the AYN Thor brought the
        // system's colour straight back, so the lights could not really be switched off from here.)
        val heldOff = com.shelf.launcher.data.Features.lightsHeldOff.state.value
        ShelfCard {
            SectionLabel("Lights")
            ChipFlow {
                Chip("Voltcap colour", accent = enabled && !heldOff, modifier = Modifier.focusRequester(firstFocus)) {
                    com.shelf.launcher.data.Features.lightsHeldOff.value = false
                    onLightsChange(true, color, brightness)
                }
                Chip("Off", accent = enabled && heldOff) {
                    com.shelf.launcher.data.Features.lightsHeldOff.value = true
                    onLightsChange(true, color, brightness)
                }
                Chip("System setting", accent = !enabled) {
                    com.shelf.launcher.data.Features.lightsHeldOff.value = false
                    onLightsChange(false, color, brightness)
                }
            }
            MutedNote(
                when {
                    !enabled -> "The handheld's own light setting is used. Voltcap doesn't touch the lights."
                    heldOff -> "All lights off, both sticks: the handheld's own RGB setting is switched off too (put back as it was when you pick a colour or System setting)."
                    else -> "Voltcap's colour while it's on screen; games get the handheld's own lights."
                }
            )
        }

        ShelfCard {
            SectionLabel("Colour")
            ChipFlow {
                // Follows the theme's accent colour (changes with the theme / game art).
                Chip((if (followsTheme) "✓ " else "") + "Theme colour", accent = followsTheme) {
                    com.shelf.launcher.data.Features.lightsHeldOff.value = false
                    onLightsChange(true, if (followsTheme) solid else com.shelf.launcher.data.LIGHTS_THEME_COLOR, brightness)
                }
                swatches.forEach { c ->
                    val selected = !followsTheme && solid == (c or 0xFF000000.toInt())
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(
                                if (selected) 3.dp else 1.dp,
                                if (selected) ShelfTheme.accent else Color.White.copy(alpha = 0.25f),
                                CircleShape
                            )
                            .shelfClickable(CircleShape) { com.shelf.launcher.data.Features.lightsHeldOff.value = false; onLightsChange(true, c, brightness) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // Left / right on the D-pad steps the hue.
            HueSlider(color = shown, onColor = { com.shelf.launcher.data.Features.lightsHeldOff.value = false; onLightsChange(true, it, brightness) })

            Spacer(Modifier.height(12.dp))
            SectionLabel("Brightness")
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundStep(Icons.Filled.Remove, "Dimmer lights") {
                    onLightsChange(true, color, (brightness - 10).coerceIn(0, 100))
                }
                Text(
                    "$brightness%", color = ShelfTheme.colors.textPrimary, fontSize = 14.sp,
                    modifier = Modifier.width(64.dp), textAlign = TextAlign.Center
                )
                RoundStep(Icons.Filled.Add, "Brighter lights") {
                    onLightsChange(true, color, (brightness + 10).coerceIn(0, 100))
                }
            }
        }

        ChipFlow {
            Chip(if (advanced) "Advanced ▲" else "Advanced ▼", accent = false) { advanced = !advanced }
            // Blinks the lights in the current colour, then restores them.
            Chip("Test", accent = false, enabled = enabled) {
                scope.launch(Dispatchers.IO) {
                    runCatching { ControllerLights.flash(context, solid, 2, shown, brightness) }
                }
            }
        }

        if (advanced) {
            SwitchRow(
                icon = Icons.Filled.Palette,
                title = "Off with the screensaver",
                subtitle = "Lights go dark when the screensaver fades to black.",
                checked = offWithScreensaver,
                onChange = { onAdvancedChange(it, flashOnUnlock) }
            )
            SwitchRow(
                icon = Icons.Filled.EmojiEvents,
                title = "Flash on achievement unlock",
                subtitle = "The stick lights blink yellow when you unlock something.",
                checked = flashOnUnlock,
                onChange = { onAdvancedChange(offWithScreensaver, it) }
            )
            ShelfCard {
                SectionLabel("Lights found")
                if (foundLights.isEmpty()) MutedNote("None.")
                foundLights.forEach { line ->
                    Text(
                        line, color = ShelfTheme.textMuted, fontSize = 12.sp,
                        modifier = Modifier.shelfFocusable().padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
