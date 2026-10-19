package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features

/*
 * Update 202: Settings › Home › Layout options (Green Core): the glow, the sphere's speed, the background's motion,
 * reduced motion, list or curved carousel for the games, the accent colour, and the layout's own synthesised sounds
 * (on / off, volume, the ambient hum). Every one a Features pref ("gc_…"), backed up and carried by presets.
 */
@Composable
internal fun GreenCoreSettings() {
    MutedNote(
        "A dark green machine dashboard: a living sphere of light on the left, angled menu panels on the right — Games, " +
            "Systems, Apps, Save Vault, Music and Settings. ▲ ▼ move, A opens, B goes back to the menu. On the games: A opens " +
            "the game, X favourite, Y achievements, L2 / R2 another category. Start: the quick menu; Select: sort & filter."
    )
    Spacer(Modifier.height(10.dp))
    PercentSlider(
        "Green glow intensity", Features.gcGlow.state.value, { Features.gcGlow.value = it.coerceIn(0, 100) },
        min = 0, max = 100, valueText = { if (it == 0) "Off" else "$it%" }
    )
    Spacer(Modifier.height(6.dp))
    PercentSlider(
        "Sphere animation speed", Features.gcSphereSpeed.state.value, { Features.gcSphereSpeed.value = it.coerceIn(0, 200) },
        min = 0, max = 200, valueText = { if (it == 0) "Still" else "$it%" }
    )
    Spacer(Modifier.height(6.dp))
    SwitchRow(
        Icons.Filled.BlurOn, "Background motion", "A slow drifting light behind the sphere.",
        Features.gcBgMotion.state.value, { Features.gcBgMotion.value = it }
    )
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.Speed, "Reduced motion",
        "The sphere stands still and the panels change without sliding. (Also when Android's animations are off.)",
        Features.gcReducedMotion.state.value, { Features.gcReducedMotion.value = it }
    )
    Spacer(Modifier.height(10.dp))
    StyleChoice("Game browsing style", Features.GcBrowse.values(), Features.gcBrowse.state.value, { it.label }) { Features.gcBrowse.value = it }
    StyleChoice("Accent colour", Features.GcAccent.values(), Features.gcAccent.state.value, { it.label }) { Features.gcAccent.value = it }
    SwitchRow(
        Icons.Filled.VolumeUp, "Interface sounds",
        "The layout's own sounds, made by the app: a click, a whoosh between pages, a confirm. Off: Voltcap's usual sounds.",
        Features.gcSounds.state.value, { Features.gcSounds.value = it }
    )
    Spacer(Modifier.height(8.dp))
    if (Features.gcSounds.state.value || Features.gcHum.state.value) {
        PercentSlider(
            "Sound volume", Features.gcVolume.state.value, { Features.gcVolume.value = it.coerceIn(0, 100) },
            min = 0, max = 100, valueText = { "$it%" }
        )
        Spacer(Modifier.height(6.dp))
    }
    SwitchRow(
        Icons.Filled.GraphicEq, "Ambient hum", "A low machine hum while Green Core is on screen.",
        Features.gcHum.state.value, { Features.gcHum.value = it }
    )
    Spacer(Modifier.height(8.dp))
    MutedNote("Voltcap's own sound switch (Settings › Audio) silences these too. The game window takes Green Core's look as well.")
}
