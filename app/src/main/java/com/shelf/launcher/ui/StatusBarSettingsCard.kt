package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.ClockHours
import com.shelf.launcher.data.StatusBarPrefs

/** Clock, date, accessories and battery-percentage options for the home status bar. */
@Composable
internal fun StatusBarSettingsCard() {
    val context = LocalContext.current
    val s by StatusBarPrefs.state(context).collectAsState()
    fun change(f: (com.shelf.launcher.data.StatusBarSettings) -> com.shelf.launcher.data.StatusBarSettings) =
        StatusBarPrefs.update(context, f)

    SettingsHeading("Status bar details")
    SettingsGrid {
        item { m ->
            SwitchRow(Icons.Filled.Schedule, "Clock", "Time in the status bar.", s.clock, { v -> change { it.copy(clock = v) } }, m)
        }
        item { m ->
            SwitchRow(Icons.Filled.CalendarToday, "Date next to the clock", "Short weekday and day, like \"Wed 30\".", s.showDate, { v -> change { it.copy(showDate = v) } }, m)
        }
        item { m ->
            SwitchRow(Icons.Filled.SportsEsports, "Connected devices", "Controllers and headphones with their battery.", s.accessories, { v -> change { it.copy(accessories = v) } }, m)
        }
        item { m ->
            SwitchRow(Icons.Filled.BatteryFull, "Battery percentage", "The number next to the battery icon.", s.showPercent, { v -> change { it.copy(showPercent = v) } }, m)
        }
    }
    Spacer(Modifier.height(8.dp))
    SectionLabel("Clock format")
    ChipFlow {
        ClockHours.values().forEach { h ->
            Chip(h.label, accent = s.hours == h) { change { it.copy(hours = h) } }
        }
    }
    Spacer(Modifier.height(8.dp))
}
