package com.shelf.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Update 501 (QA only, with the test hooks / in Voltcap QA): Save Vault 3D cards with no save behind them —
 * `--es voltcap_test_cards "0,40,89,90,140,180"` (each card held at those angles) | `spin` (turning all the way round, as the
 * vault's clock turns them) | `off`. The vault's own renderer ([SwayingModel], SolidPiece) with made-up inputs: a card id,
 * no icon, "QA card", PS1. Opaque and clear cards side by side; only cards whose 3D model is on the device.
 */
internal object QaCards {
    val spec = mutableStateOf<String?>(null)
    val CARDS = listOf("memory_card_voltcap_blue", "memory_card_voltcap_blue_translucent", "memory_card_voltcap_smoke_translucent",
        "memory_card_voltcap_red", "sd_card_hq")
    fun angles(s: String): List<Float> = s.split(',').mapNotNull { it.trim().toFloatOrNull() }
}

@Composable
internal fun QaCardSheetHost() {
    val sp = QaCards.spec.value ?: return
    val cards = QaCards.CARDS.filter { com.shelf.launcher.data.Packs.hasModel(it) }
    val spin = sp == "spin"
    val angles = if (spin) listOf(0f) else QaCards.angles(sp)
    val clock = if (spin) rememberInfiniteTransition(label = "qacards").animateFloat(0f, 360f,
        infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "qacardsClock") else null
    Dialog(onDismissRequest = { QaCards.spec.value = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF15171C)).padding(10.dp)) {
            Text("QA cards · no save · " + (if (spin) "turning" else angles.joinToString(" / ") { "${it.toInt()}°" }),
                color = Color(0xFF8A96A3), fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomEnd))
            if (cards.isEmpty()) Text("No 3D card models on this device (Settings › Game look › Extra art packs).", color = Color.White)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                for (c in cards) Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.removePrefix("memory_card_voltcap_"), color = Color.White, fontSize = 10.sp, modifier = Modifier.width(110.dp))
                    for (a in angles) Box(Modifier.weight(1f).fillMaxHeight(0.88f), contentAlignment = Alignment.Center) {
                        SwayingModel(c, null, "QA card", "PS1", { clock?.value ?: a }, Modifier.fillMaxHeight().fillMaxWidth(0.62f))
                    }
                }
            }
        }
    }
}
