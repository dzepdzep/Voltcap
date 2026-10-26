package com.shelf.launcher.device.audio

import com.shelf.launcher.device.RootShell

/**
 * Update 348 (Toni: "controlling the sound per screen" on dual-screen handhelds): which app each screen is showing, for
 * the second screen's "Sound per screen" widget — a game on the top screen and a video on the bottom one, each turned
 * up or down on its own (through [AppMixer], the same per-app volume as the mixer).
 *
 * Android tells an app only about its own windows, so the screens' apps are read through the handheld's root service
 * ([RootShell], AYN / Retroid): the window manager's list of tasks per display. Without it: null (the widget says so).
 * Blocking (a dumpsys, tens of ms): call off the main thread, not too often.
 */
internal object ScreenApps {
    /** The app on display [displayId]: [packageName], and whether it's the one showing ([showing]) or the last one used there. */
    data class OnScreen(val displayId: Int, val packageName: String, val showing: Boolean)

    /** The system's own launchers and bars: never "the app on a screen". */
    private val SYSTEM = setOf("com.android.launcher3", "com.android.systemui", "com.android.settings.intelligence")

    /** Whether this handheld can tell (the root service is there). */
    val available: Boolean get() = RootShell.available

    /**
     * For each display, the app in front there — leaving out Voltcap itself ([ownPackage]: its home and second-screen
     * pages), so under Voltcap's own page the app last used on that screen. Null when it can't be read.
     */
    fun read(ownPackage: String): Map<Int, OnScreen>? {
        // (The root service returns one line: the lines joined with '~'.)
        val out = RootShell.execJoined("dumpsys activity activities | grep -E 'Display #|[*] Task[{]'", '~') ?: return null
        return parse(out.split('~'), ownPackage)
    }

    /** [lines]: "Display #N …" headers, each followed by its tasks top to bottom ("* Task{… type=standard A=uid:pkg … visible=true …"). */
    fun parse(lines: List<String>, ownPackage: String): Map<Int, OnScreen> {
        val found = LinkedHashMap<Int, OnScreen>()
        var display = -1
        var coveredByOwn = false
        // (A task is listed under its own display first; the dump repeats other displays' tasks deeper under a later
        // one — seen on the Thor: the top screen's under "Display #4". Each task counts once, where it's first seen.)
        val seen = HashSet<String>()
        for (raw in lines) {
            val line = raw.trim()
            val header = Regex("^Display #(\\d+)").find(line)
            if (header != null) { display = header.groupValues[1].toInt(); coveredByOwn = false; continue }
            if (display < 0 || !line.startsWith("* Task{")) continue
            val id = Regex("#(\\d+)").find(line)?.groupValues?.get(1)
            if (id != null && !seen.add(id)) continue
            if (display in found) continue
            val pkg = Regex("\\bA=\\d+:([A-Za-z0-9._]+)").find(line)?.groupValues?.get(1)
                ?: Regex("\\bI=([A-Za-z0-9._]+)/").find(line)?.groupValues?.get(1)
                ?: continue
            val visible = line.contains("visible=true")
            // Voltcap's own pages (its home too) cover the screen: the app found under them is the last one used there.
            if (pkg == ownPackage) { if (visible) coveredByOwn = true; continue }
            if (!line.contains("type=standard") || pkg in SYSTEM) continue
            found[display] = OnScreen(display, pkg, showing = visible && !coveredByOwn)
        }
        return found
    }
}
