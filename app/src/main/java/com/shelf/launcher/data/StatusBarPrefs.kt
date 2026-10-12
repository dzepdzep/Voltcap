package com.shelf.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 12/24 h choice for the status-bar clock. */
enum class ClockHours(val label: String) {
    SYSTEM("Follow system"),
    H12("12 h"),
    H24("24 h")
}

/** What the home status bar shows besides its fixed icons. */
data class StatusBarSettings(
    val clock: Boolean = true,
    val hours: ClockHours = ClockHours.SYSTEM,
    /** Short weekday + day of month next to the time. */
    val showDate: Boolean = false,
    /** Connected controllers / headphones / etc. with their battery. */
    val accessories: Boolean = true,
    /** "73%" text next to the device battery icon. */
    val showPercent: Boolean = true
)

/** Tiny global store ("status_bar" prefs), observable so the bar reacts as soon as the card changes. */
object StatusBarPrefs {
    @Volatile private var flow: MutableStateFlow<StatusBarSettings>? = null

    fun state(context: Context): StateFlow<StatusBarSettings> = flowFor(context)

    fun update(context: Context, change: (StatusBarSettings) -> StatusBarSettings) {
        val f = flowFor(context)
        val next = change(f.value)
        f.value = next
        prefs(context).edit()
            .putBoolean("clock", next.clock)
            .putString("hours", next.hours.name)
            .putBoolean("show_date", next.showDate)
            .putBoolean("accessories", next.accessories)
            .putBoolean("show_percent", next.showPercent)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("status_bar", Context.MODE_PRIVATE)

    private fun flowFor(context: Context): MutableStateFlow<StatusBarSettings> =
        flow ?: synchronized(this) {
            flow ?: MutableStateFlow(load(context)).also { flow = it }
        }

    private fun load(context: Context): StatusBarSettings {
        val p = prefs(context)
        val d = StatusBarSettings()
        return StatusBarSettings(
            clock = p.getBoolean("clock", d.clock),
            hours = runCatching { ClockHours.valueOf(p.getString("hours", null) ?: d.hours.name) }.getOrDefault(d.hours),
            showDate = p.getBoolean("show_date", d.showDate),
            accessories = p.getBoolean("accessories", d.accessories),
            showPercent = p.getBoolean("show_percent", d.showPercent)
        )
    }
}
