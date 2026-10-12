package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.WebRemote
import com.shelf.launcher.ui.theme.ShelfTheme

/** Settings › Controls: the phone web remote. */
@Composable
internal fun WebRemoteCard() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(WebRemote.enabled(context)) }
    var pin by remember { mutableStateOf(WebRemote.pin(context)) }
    SettingsHeading("Phone remote")
    SwitchRow(
        Icons.Filled.PhoneAndroid, "Control Voltcap from your phone",
        "A D-pad, buttons and your game list in your phone's browser, over the same Wi-Fi. Works while Voltcap is on screen.",
        on, onChange = { on = it; WebRemote.setEnabled(context, it) }
    )
    if (on) {
        Spacer(Modifier.height(8.dp))
        ShelfCard {
            val addr = remember(on) { WebRemote.address() }
            Text(addr ?: "Connect to Wi-Fi first", color = ShelfTheme.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Code: $pin", color = ShelfTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            MutedNote(
                if (WebRemote.running) "Open the address in your phone's browser and enter the code."
                else "Couldn't open the port — another app may be using it. Switch off and on again."
            )
            Spacer(Modifier.height(6.dp))
            ChipFlow { Chip("New code", accent = false) { pin = WebRemote.newPin(context); WebRemote.stop(); WebRemote.start(context) } }
        }
    }
}
