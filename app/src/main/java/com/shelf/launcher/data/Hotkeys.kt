package com.shelf.launcher.data

/** What a hotkey does. */
enum class HotkeyAction(val label: String) {
    OFF("Nothing"),
    OPEN_APP("Open an app"),
    /** Plays / pauses Spotify in the background — without opening it. */
    PLAY_SPOTIFY("Play / pause Spotify"),
    /** Opens the Spotify library / search / player screen. */
    OPEN_SPOTIFY("Spotify library"),
    TOGGLE_MUSIC_PLAYER("Show / hide the music player"),
    LAST_GAME("Play the last game"),
    CATEGORIES("Category screen"),
    SEARCH("Search"),
    NOTIFICATIONS("Notifications"),
    SHORTCUTS("Shortcuts"),
    SCREENSAVER("Screensaver"),
    APP_DRAWER("All apps"),
    /** "Can't decide": something unplayed or unfinished from the category you're in. */
    RANDOM_GAME("Random game"),
    /** Three suggestions with reasons (PlaySuggest). */
    WHAT_TO_PLAY("What should I play?"),
    TOGGLE_LEGEND("Show / hide the legend"),
    SORT("Sort games"),
    /** Only works while VoltCap is in front (hotkeys are read by VoltCap itself). */
    TOGGLE_PERF_OVERLAY("Performance overlay on/off"),
    /** What L3 does — for devices without stick presses. */
    QUICK_JUMP("Jump by letter (L3)"),
    /** What R3 does — for devices without stick presses. */
    CONTROLS_HELP("All controls (R3)"),
    /** The quick menu (R1) and the settings menu (Select) from another button. */
    QUICK_MENU("Quick menu (R1)"),
    SETTINGS("Settings"),
    /** Each app's own volume (the card that also opens from Quick Settings and the in-game panel). */
    VOLUME_MIXER("Volume mixer"),
    /** The category before / after this one (what L2 / R2 do), on any other button. */
    PREVIOUS_CATEGORY("Previous category"),
    NEXT_CATEGORY("Next category"),
    TOGGLE_FAVORITE("Favourite / unfavourite the game"),
    GAME_OPTIONS("Game options"),
    GAME_DETAILS("Open the game's details"),
    MUSIC_PLAY_PAUSE("Play / pause the music"),
    MUSIC_NEXT("Next song"),
    MUSIC_PREVIOUS("Previous song"),
    /** Single-screen devices: the second screen as a panel over the main one (Appearance › Second screen tools). */
    VIRTUAL_SECOND_SCREEN("Show / hide the virtual second screen"),
    /** The second screen's next tab (Art, Video, Details, … Widgets, Wall). */
    SECOND_SCREEN_PAGE("Second screen: next page"),
    RESTART_APP("Restart Voltcap")
}

/** One hotkey: a button (or Select + button) and what it does. */
data class Hotkey(
    val button: MusicButton = MusicButton.NONE,
    val action: HotkeyAction = HotkeyAction.OFF,
    /** For [HotkeyAction.OPEN_APP]. */
    val packageName: String? = null,
    val appLabel: String? = null
)

/** How many hotkey slots there are. */
const val HOTKEY_SLOTS = 6

/** Four set up to start with; two more free slots. */
val DEFAULT_HOTKEYS: List<Hotkey> = listOf(
    Hotkey(MusicButton.SELECT_L1, HotkeyAction.PLAY_SPOTIFY),
    Hotkey(MusicButton.SELECT_R1, HotkeyAction.LAST_GAME),
    Hotkey(MusicButton.SELECT_Y, HotkeyAction.TOGGLE_MUSIC_PLAYER),
    Hotkey(MusicButton.SELECT_X, HotkeyAction.RANDOM_GAME),
    Hotkey(),
    Hotkey()
)

/** Suggestions shown under the hotkeys. */
val HOTKEY_TIPS: List<String> = listOf(
    "Select + L1 → Play / pause Spotify: starts your music in the background, the app never opens.",
    "Select + Y → Show / hide the music player: the corner player only when you want it.",
    "Select + R1 → Play the last game: back in with one press.",
    "M1 / M2 → Category screen or Search: the back buttons on AYN handhelds (Odin 2 Portal, Odin 2, Thor) work straight away. " +
        "On other devices map the paddles to \"Z\" (M1) and \"C\" (M2) in the device's own button settings.",
    "No stick presses (L3 / R3) on your device? Put \"Jump by letter (L3)\" and \"All controls (R3)\" on Select + L2 / R2 or M1 / M2.",
    "L3 → Screensaver: a quick way to dim the screen while something charges.",
    "Select + X → Random game: picks something unplayed or unfinished from the category you're in."
)

fun encodeHotkeys(list: List<Hotkey>): String = list.joinToString(";") {
    listOf(it.button.name, it.action.name, it.packageName.orEmpty(), it.appLabel.orEmpty())
        .joinToString("|") { part -> part.replace("|", "").replace(";", "") }
}

fun decodeHotkeys(saved: String?): List<Hotkey> {
    if (saved.isNullOrBlank()) return DEFAULT_HOTKEYS
    val parsed = saved.split(';').mapNotNull { part ->
        val bits = part.split('|')
        if (bits.size < 2) return@mapNotNull null
        Hotkey(
            button = runCatching { MusicButton.valueOf(bits[0]) }.getOrNull() ?: MusicButton.NONE,
            action = runCatching { HotkeyAction.valueOf(bits[1]) }.getOrNull() ?: HotkeyAction.OFF,
            packageName = bits.getOrNull(2)?.takeIf { it.isNotBlank() },
            appLabel = bits.getOrNull(3)?.takeIf { it.isNotBlank() }
        )
    }
    val padded = parsed + DEFAULT_HOTKEYS.drop(parsed.size).map { it.copy(button = MusicButton.NONE) }
    return (padded + List((HOTKEY_SLOTS - padded.size).coerceAtLeast(0)) { Hotkey() }).take(HOTKEY_SLOTS)
}

/** The hotkey bound to [keyCode] (with or without Select held), if any. */
fun List<Hotkey>.hotkeyFor(keyCode: Int, selectHeld: Boolean): Hotkey? =
    firstOrNull { it.button != MusicButton.NONE && it.action != HotkeyAction.OFF &&
        it.button.keyCode == keyCode && it.button.withSelect == selectHeld }
