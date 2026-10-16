package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Features.AndroidMediaMode

/*
 * Update 212: what Android games (and apps) are drawn as, wherever games are drawn as a cartridge or a disc — the cards,
 * the closed and opened cases and what lies in them, the game window, the inspection view and the launch animation all
 * ask mediaFor (PhysicalMedia.kt), which asks [AndroidMedia.disc]. Only how it's drawn: the game's platform, category
 * and launch settings never change.
 *
 * Three separate choices: the media (this file: disc or cartridge), how it's presented (Settings › Game look › Cases ›
 * With its media: case only, beside it, half out) and its art (the game's cover / icon as its label, as before).
 */
internal object AndroidMedia {
    fun isAndroid(platform: String): Boolean = platform == "App" || platform == "Android"

    private fun games(): Map<String, AndroidMediaMode> = Features.androidMediaGames.state.value.split(';').mapNotNull { e ->
        val i = e.lastIndexOf('=')
        if (i <= 0) null else AndroidMediaMode.values().firstOrNull { it.name == e.substring(i + 1) }?.let { e.substring(0, i) to it }
    }.toMap()

    /** This game's own choice (the game's menu › Disc or cartridge…), or null: the setting. Reads state. */
    fun gameChoice(gameKey: String?): AndroidMediaMode? = gameKey?.let { games()[it] }?.takeIf { it != AndroidMediaMode.FOLLOW }

    fun setGame(gameKey: String, mode: AndroidMediaMode?) {
        val m = games().toMutableMap()
        if (mode == null || mode == AndroidMediaMode.FOLLOW) m.remove(gameKey) else m[gameKey] = mode
        Features.androidMediaGames.value = m.entries.joinToString(";") { "${it.key}=${it.value.name}" }
    }

    /** A shell or your cartridge pack is saved for this game or for Android: a cartridge someone picked. */
    private fun savedCartridge(platform: String, gameKey: String?): Boolean =
        CartShellStore.choice(platform, gameKey) != null || customCartFor(platform)

    /**
     * True: drawn as a disc (Voltcap's plain CD with Android's band); false: a cartridge (the shell or pack you picked,
     * else Voltcap's green Android cartridge). Highest first:
     *  1. a layout where every game is a disc (Disc station) — its callers keep the disc whatever this says;
     *  2. this game's own choice;
     *  3. a shell or cartridge pack picked for it or for Android stays a cartridge (never replaced without asking);
     *  4. Settings › Game look › Android games (Disc / Cartridge);
     *  5. Follow the current look: [lookDisc] — Discs only, "All CDs" in lists mixing consoles (as before update 212).
     */
    fun disc(platform: String, gameKey: String?, lookDisc: Boolean): Boolean {
        when (gameChoice(gameKey)) {
            AndroidMediaMode.DISC -> return true
            AndroidMediaMode.CARTRIDGE -> return false
            else -> {}
        }
        return when (Features.androidMedia.state.value) {
            AndroidMediaMode.DISC -> !savedCartridge(platform, gameKey)
            AndroidMediaMode.CARTRIDGE -> false
            AndroidMediaMode.FOLLOW -> lookDisc
        }
    }

    /** A disc picked on purpose (this game, or the setting) — not just the look: a look of covers shows it as its disc. */
    fun discPicked(platform: String, gameKey: String?): Boolean =
        isAndroid(platform) && when (gameChoice(gameKey)) {
            AndroidMediaMode.DISC -> true
            AndroidMediaMode.CARTRIDGE -> false
            else -> Features.androidMedia.state.value == AndroidMediaMode.DISC && !savedCartridge(platform, gameKey)
        }

    /** What [gameKey] is drawn as and why ("Disc · This game", "Cartridge · Your cartridge shell"…). Reads state. */
    fun effective(platform: String, gameKey: String?, lookDisc: Boolean): String {
        val d = disc(platform, gameKey, lookDisc)
        val why = when {
            gameChoice(gameKey) != null -> "This game"
            Features.androidMedia.state.value == AndroidMediaMode.DISC && savedCartridge(platform, gameKey) -> "Your cartridge shell / pack"
            Features.androidMedia.state.value != AndroidMediaMode.FOLLOW -> "Android games setting"
            else -> "The current look"
        }
        return (if (d) "Disc" else "Cartridge") + " · " + why
    }
}

/** Update 212: Settings › Game look › Android games — Follow the current look / Disc (suggested) / Cartridge. */
@Composable
internal fun AndroidMediaChoice() {
    val now = Features.androidMedia.state.value
    SectionLabel("Android games")
    ChipFlow {
        AndroidMediaMode.values().forEach { m ->
            val label = if (m == AndroidMediaMode.DISC) "Disc (Suggested)" else m.label
            Chip((if (m == now) "✓ " else "") + label, accent = m == now) { Features.androidMedia.value = m }
        }
    }
    MutedNote(
        "Android games have no cartridge or disc of their own: a disc suits them best. A shell or cartridge pack you picked " +
            "stays as it is; one game can be set on its own (its menu › Disc or cartridge…). Only how it's drawn — never its " +
            "console, category or how it starts."
    )
    Spacer(Modifier.height(8.dp))
}

/** Update 212: one Android game's own choice (the game's menu › Disc or cartridge…, at the top of Cartridge shell). */
@Composable
internal fun AndroidMediaGameChoice(platform: String, gameKey: String) {
    val own = AndroidMedia.gameChoice(gameKey)
    SectionLabel("Disc or cartridge · for this game")
    ChipFlow {
        Chip((if (own == null) "✓ " else "") + "As the setting", accent = own == null) { AndroidMedia.setGame(gameKey, null) }
        Chip((if (own == AndroidMediaMode.DISC) "✓ " else "") + "Disc (Suggested)", accent = own == AndroidMediaMode.DISC) { AndroidMedia.setGame(gameKey, AndroidMediaMode.DISC) }
        Chip((if (own == AndroidMediaMode.CARTRIDGE) "✓ " else "") + "Cartridge", accent = own == AndroidMediaMode.CARTRIDGE) { AndroidMedia.setGame(gameKey, AndroidMediaMode.CARTRIDGE) }
    }
    MutedNote(
        "Now: " + AndroidMedia.effective(platform, gameKey, DiscLook.on) + ". " +
            (if (own == AndroidMediaMode.DISC && CartShellStore.choice(platform, gameKey) != null) "The shell below stays saved for when it's a cartridge again. " else "") +
            "The setting for every Android game: Settings › Game look › Android games."
    )
    Spacer(Modifier.height(8.dp))
}
