package com.shelf.launcher.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Update 177: what the last ROM scan found worth tidying: multi-disc games without a .m3u playlist (each listed as one
 * game, started from disc 1 — emulators need the playlist to change discs), and games found more than once. Shown in
 * Settings › Library › ROM folders › Library check, with "Make the .m3u files".
 */
object LibraryCheck {
    var missingM3u by mutableStateOf<List<RomScanner.MultiDiscSet>>(emptyList())
    var duplicates by mutableStateOf<List<RomScanner.Duplicate>>(emptyList())
    /** Whether a scan has run since Voltcap started (else the check has nothing to say yet). */
    var scanned by mutableStateOf(false)
}
