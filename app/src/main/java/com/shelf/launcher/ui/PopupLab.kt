package com.shelf.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Batch 523 (owner: "not fully happy with the typography, button placement and visual balance of the six popup styles -
 * explore, don't redesign until I've reviewed the options"): a QA-only design lab. Nothing changes for anyone unless the
 * isolated QA app is started with `--es voltcap_test_popuplab "font=current|inter|manrope,weights=on,actions=on,storage=on"`
 * (`off` ends it; never saved). The options it shows:
 *  - font: the current system font (Roboto), Inter or Manrope (both SIL OFL 1.1; bundled in the QA build's assets only,
 *    src/qa/assets/qa_fonts - not in the release APK);
 *  - weights: one step lighter everywhere (Bold drawn SemiBold, SemiBold drawn Medium, Black drawn Bold) - the "too bold"
 *    part of the owner's note, without touching any screen's code;
 *  - actions: Play the only filled action, Details secondary (outlined), More a small round "•••" key beside Details,
 *    Back out of the action row - a subtle "B Back" hint (still tappable) in the window's corner;
 *  - storage: Storage boxes' popup with more air, Play + Details + ••• on one line and the console tag on the picture.
 */
internal object PopupLab {
    data class Spec(val font: String = "current", val weights: Boolean = false, val actions: Boolean = false, val storage: Boolean = false) {
        val text get() = "font=$font,weights=${if (weights) "on" else "off"},actions=${if (actions) "on" else "off"},storage=${if (storage) "on" else "off"}"
    }

    val spec = mutableStateOf<Spec?>(null)

    /** "font=inter,weights=on,actions=on" → a spec; "off" / blank → none. Pure. */
    fun parse(s: String): Spec? {
        if (s.isBlank() || s == "off") return null
        val m = s.split(',').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
        fun on(k: String) = m[k] == "on" || m[k] == "true"
        return Spec(m["font"]?.takeIf { it in setOf("current", "inter", "manrope") } ?: "current", on("weights"), on("actions"), on("storage"))
    }

    /** The weight a lighter step draws a requested weight with. Pure. */
    fun lighter(w: FontWeight): FontWeight = when {
        w.weight >= 900 -> FontWeight.Bold
        w.weight >= 700 -> FontWeight.SemiBold
        w.weight >= 600 -> FontWeight.Medium
        else -> w
    }

    /** The family for [s] - each requested weight mapped to the file / device face that draws it. */
    fun family(context: android.content.Context, s: Spec): FontFamily? {
        val asks = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black)
        fun drawn(w: FontWeight) = if (s.weights) lighter(w) else w
        return when (s.font) {
            "inter", "manrope" -> runCatching {
                val ext = if (s.font == "inter") "otf" else "ttf"
                fun file(w: FontWeight) = "qa_fonts/${s.font}_${drawn(w).weight.coerceIn(400, 700) / 100 * 100}.$ext"
                context.assets.open(file(FontWeight.Normal)).close() // the QA build only: none in a release build
                FontFamily(asks.map { w -> Font(file(w), context.assets, w) })
            }.getOrNull()
            // (The device's own faces, Roboto Regular and Medium only: a Bold / SemiBold / Black request is matched to the
            // nearest face there is - Medium - and Theme turns weight synthesis off while the lab is on, so it isn't
            // thickened again.)
            else -> if (!s.weights) null else FontFamily(
                Font(DeviceFontFamilyName("sans-serif"), FontWeight.Normal),
                Font(DeviceFontFamilyName("sans-serif-medium"), FontWeight.Medium)
            )
        }
    }

    fun isMore(a: WinAction) = a.label == "More" || a.label == "Less"
    fun isDetails(a: WinAction) = a.icon == androidx.compose.material.icons.Icons.Filled.Info
}

/** The lab's Back: a small controller hint in the window's corner (still a touch target). */
@Composable
internal fun LabBackHint(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clickable(onClick = onBack).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        InkGlyph("B", Color.White.copy(alpha = 0.75f), 18.dp)
        Spacer(Modifier.width(6.dp))
        Text("Back", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
