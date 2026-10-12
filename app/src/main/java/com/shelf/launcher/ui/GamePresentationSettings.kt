package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features

/**
 * Appearance › Interface style › 32-bit: the font (normal, or the 32-bit type everywhere), pixel corners and
 * pixel artwork. It used to sit at the top of Appearance › Game row, and the 32-bit console style switched the
 * pixel type on for good; the font is its own choice now.
 */
@Composable
internal fun PixelStyleCard() {
    ShelfCard {
        SectionLabel("Font")
        ChipFlow {
            Features.UiFont.values().forEach { f ->
                Chip(f.label, accent = Features.uiFont.value == f) { Features.uiFont.value = f }
            }
        }
        MutedNote(
            "Normal: the usual font; with the 32-bit console style the headings use the 32-bit type. 32-bit everywhere: " +
                "every text in the 32-bit type — game names, categories and the status bar too — a size smaller so names fit."
        )
        Spacer(Modifier.height(10.dp))
        SectionLabel("32-bit details")
        ChipFlow {
            Chip("Pixel corners", accent = Features.pixelUi.value) { Features.pixelUi.toggle() }
            Chip("Pixel artwork", accent = Features.pixelArtwork.value) { Features.pixelArtwork.toggle() }
        }
        if (Features.pixelArtwork.value) ChipFlow {
            Features.PixelSize.values().forEach { p ->
                Chip(p.label, accent = Features.pixelSize.value == p) { Features.pixelSize.value = p }
            }
        }
        MutedNote(
            "Pixel corners: square, cut corners on panels and dialogs. Pixel artwork: chunky pixels on covers, tiles and " +
                "heroes (Android 13+). The 32-bit startup intro is under Startup intro."
        )
    }
}
