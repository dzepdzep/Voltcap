package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shelf.launcher.device.VoltcapRate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 0.6.15: Settings › Device › Screen rate — Voltcap at the screen's fastest refresh rate (device/VoltcapRate.kt). */
@Composable
internal fun ScreenRateCard() {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    LaunchedEffect(Unit) { VoltcapRate.load(context) }
    val on = VoltcapRate.on.value
    var status by remember { mutableStateOf("…") }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(on, refresh) {
        if (refresh > 0) kotlinx.coroutines.delay(1_500) // let the screen switch first
        status = withContext(Dispatchers.IO) { runCatching { VoltcapRate.status(context) }.getOrDefault("") }
    }
    Spacer(Modifier.height(12.dp))
    ShelfCard {
        SectionLabel("Screen rate")
        MutedNote("Smoother scrolling through your games: Voltcap's menus at the screen's fastest refresh rate. Games and other apps keep their own rate.")
        Spacer(Modifier.height(10.dp))
        SwitchRow(
            Icons.Filled.Speed, "Fastest rate in Voltcap",
            "Uses a little more battery while Voltcap is on screen.",
            checked = on,
            onChange = { VoltcapRate.set(activity, context, it); refresh++ }
        )
        if (status.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            MutedNote(status)
        }
    }
}
