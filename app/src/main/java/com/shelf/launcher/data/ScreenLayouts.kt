package com.shelf.launcher.data

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/*
 * Screen layouts: what goes on each screen when a game starts.
 *
 * A device can have a second screen in two ways — built in (AYN Thor, Ayaneo
 * Flip DS…) or plugged in (a TV or monitor on the Odin 2 Portal's video out).
 * Each of those is a "setup" with its own rules, so the same game can run
 * "game on top, info below" on the Thor and "game on the TV, handheld dark"
 * when docked.
 *
 * A layout says which screen the game goes on and what the other screen gets:
 * Voltcap's own info page, another app, a black screen, or nothing (left as
 * is). DS/3DS emulators draw both screens themselves; the "emulator uses both
 * screens" layout keeps Voltcap out of the way for those.
 *
 * Which layout a game gets is decided by rules, most specific first:
 *   this game › a collection it's in › its console › its emulator › default.
 */

enum class ScreenSide { MAIN, SECOND }

enum class OtherScreen(val label: String, val blurb: String) {
    INFO("Voltcap info", "The game's logo, then your second-screen pages (art, achievements, guide, music…) and a card for every unlock"),
    APP("An app", "Discord, YouTube, a guide in the browser…"),
    BLACK("Black screen", "Close to off on OLED screens"),
    KEEP("Leave as is", "Nothing new is opened there")
}

/** Built into the device, or a TV/monitor plugged into it. */
enum class ScreenSetup(val key: String, val label: String) {
    DUAL("dual", "Built-in second screen"),
    TV("tv", "TV or monitor");

    /** What each screen is called in this setup. */
    fun sideName(side: ScreenSide): String = when (this) {
        DUAL -> if (side == ScreenSide.MAIN) "main screen" else "second screen"
        TV -> if (side == ScreenSide.MAIN) "handheld" else "TV"
    }

    companion object {
        fun of(key: String?): ScreenSetup = values().firstOrNull { it.key == key } ?: DUAL
    }
}

data class ScreenLayout(
    val id: String,
    val name: String,
    val gameOn: ScreenSide = ScreenSide.MAIN,
    val other: OtherScreen = OtherScreen.INFO,
    /** For [OtherScreen.APP]. */
    val appPackage: String? = null,
    val appLabel: String? = null,
    /** DS/3DS emulators that draw to both screens themselves. */
    val emulatorUsesBoth: Boolean = false,
    val builtIn: Boolean = false
) {
    /** "Game on main screen · Voltcap info on second screen". */
    fun summary(setup: ScreenSetup): String {
        if (emulatorUsesBoth) return "The emulator draws both screens itself"
        val gameSide = setup.sideName(gameOn)
        val otherSide = setup.sideName(if (gameOn == ScreenSide.MAIN) ScreenSide.SECOND else ScreenSide.MAIN)
        val what = when (other) {
            OtherScreen.INFO -> "Voltcap info"
            OtherScreen.APP -> appLabel ?: appPackage ?: "an app"
            OtherScreen.BLACK -> "black"
            OtherScreen.KEEP -> "left as is"
        }
        return "Game on $gameSide · $otherSide: $what"
    }

    /** Gson skips constructors, so fields missing from older saves come back
     * null even though Kotlin says they can't be. */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    internal fun sanitized(): ScreenLayout? {
        if (id == null || name == null) return null
        var out = copy(
            gameOn = gameOn ?: ScreenSide.MAIN,
            other = other ?: OtherScreen.INFO
        )
        // Voltcap staying on the main screen while the game runs on the other
        // one would never notice the game ending (play time, sync), so the
        // main screen always gets something when the game is on the second.
        if (out.gameOn == ScreenSide.SECOND && out.other == OtherScreen.KEEP) out = out.copy(other = OtherScreen.INFO)
        if (out.other == OtherScreen.APP && out.appPackage.isNullOrBlank()) out = out.copy(other = OtherScreen.INFO)
        return out
    }
}

/** Where a rule applies. Keys are stored as "<scope>:<value>". */
enum class RuleScope(val prefix: String, val label: String) {
    GAME("game", "This game"),
    COLLECTION("coll", "Collection"),
    CONSOLE("console", "Console"),
    EMULATOR("emu", "Emulator"),
    DEFAULT("default", "Everything else");

    fun key(value: String = ""): String = if (this == DEFAULT) prefix else "$prefix:$value"
}

data class ScreenRule(val scope: RuleScope, val value: String, val layoutId: String) {
    val key: String get() = scope.key(value)
}

/** The layout a game ends up with, and why — shown in the game's options. */
data class ResolvedLayout(val layout: ScreenLayout, val scope: RuleScope?, val value: String?) {
    /** "set for this game", "set for PS2", "default". */
    fun reason(consoleName: (String) -> String): String = when (scope) {
        RuleScope.GAME -> "set for this game"
        RuleScope.COLLECTION -> "set for ${value?.let { collectionLabel(it) } ?: "a collection"}"
        RuleScope.CONSOLE -> "set for ${value?.let(consoleName) ?: "this console"}"
        RuleScope.EMULATOR -> "set for this emulator"
        RuleScope.DEFAULT -> "your default"
        null -> "Voltcap's default"
    }
}

object ScreenLayouts {
    const val INFO = "info"
    const val GAME_ONLY = "game-only"
    const val NATIVE = "native"
    const val FLIPPED = "flipped-info"
    const val KEEP = "keep"
    const val TV = "tv"

    val builtIns = listOf(
        ScreenLayout(INFO, "Game + info", ScreenSide.MAIN, OtherScreen.INFO, builtIn = true),
        ScreenLayout(GAME_ONLY, "Game only", ScreenSide.MAIN, OtherScreen.BLACK, builtIn = true),
        ScreenLayout(NATIVE, "Emulator uses both screens", emulatorUsesBoth = true, builtIn = true),
        ScreenLayout(FLIPPED, "Game on the other screen + info", ScreenSide.SECOND, OtherScreen.INFO, builtIn = true),
        ScreenLayout(KEEP, "Game, leave the other screen", ScreenSide.MAIN, OtherScreen.KEEP, builtIn = true),
        ScreenLayout(TV, "Game on the second screen, main dark", ScreenSide.SECOND, OtherScreen.BLACK, builtIn = true)
    )

    /** Consoles whose emulators draw both screens (DS, 3DS). */
    private val DUAL_SCREEN_CONSOLES = setOf("NDS", "3DS")

    /**
     * Update 326: emulators that use the second screen themselves whatever the console — Eden Duo (Switch) puts a live
     * companion for the running game there (maps, menus, status). Voltcap keeps out of that screen for them.
     */
    val DUAL_SCREEN_EMULATORS = setOf(EmulatorCatalog.EDEN_DUO, EmulatorCatalog.EDEN_DUO + ".dev")

    /** Voltcap's own pick when no rule matches. */
    fun fallback(setup: ScreenSetup, game: Game, emulatorPackage: String? = null): String = when {
        game.platform in DUAL_SCREEN_CONSOLES -> NATIVE
        setup == ScreenSetup.DUAL && emulatorPackage != null && emulatorPackage in DUAL_SCREEN_EMULATORS -> NATIVE
        setup == ScreenSetup.TV -> TV
        else -> INFO
    }
}

class ScreenLayoutsStore(context: Context) {
    private val prefs = context.getSharedPreferences("screen_layouts", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val layoutsType = object : TypeToken<List<ScreenLayout>>() {}.type
    private val rulesType = object : TypeToken<Map<String, String>>() {}.type
    private val kindsType = object : TypeToken<Map<String, String>>() {}.type

    /** Built-in layouts first, then your own. */
    fun layouts(): List<ScreenLayout> = ScreenLayouts.builtIns + customLayouts()

    fun customLayouts(): List<ScreenLayout> =
        prefs.getString("custom_json", null)
            ?.let { runCatching { gson.fromJson<List<ScreenLayout>>(it, layoutsType) }.getOrNull() }
            .orEmpty()
            .mapNotNull { it?.sanitized() }

    fun layout(id: String?): ScreenLayout? = id?.let { wanted -> layouts().firstOrNull { it.id == wanted } }

    fun saveLayout(layout: ScreenLayout) {
        val clean = layout.copy(builtIn = false).sanitized() ?: return
        val list = customLayouts().filterNot { it.id == clean.id } + clean
        prefs.edit().putString("custom_json", gson.toJson(list)).apply()
    }

    /** Removes one of your layouts, and every rule that pointed at it. */
    fun deleteLayout(id: String) {
        val list = customLayouts().filterNot { it.id == id }
        val edit = prefs.edit().putString("custom_json", gson.toJson(list))
        ScreenSetup.values().forEach { setup ->
            val rules = rulesMap(setup).filterValues { it != id }
            edit.putString(rulesKey(setup), gson.toJson(rules))
        }
        edit.apply()
    }

    fun newLayoutId(): String = "custom-" + System.currentTimeMillis().toString(36)

    // ---- Rules ----

    private fun rulesKey(setup: ScreenSetup) = "rules_${setup.key}"

    private fun rulesMap(setup: ScreenSetup): Map<String, String> =
        prefs.getString(rulesKey(setup), null)
            ?.let { runCatching { gson.fromJson<Map<String, String>>(it, rulesType) }.getOrNull() }
            .orEmpty()

    fun rules(setup: ScreenSetup): List<ScreenRule> = rulesMap(setup).mapNotNull { (key, layoutId) ->
        val prefix = key.substringBefore(':')
        val scope = RuleScope.values().firstOrNull { it.prefix == prefix } ?: return@mapNotNull null
        ScreenRule(scope, if (scope == RuleScope.DEFAULT) "" else key.substringAfter(':'), layoutId)
    }.sortedWith(compareBy({ it.scope.ordinal }, { it.value.lowercase() }))

    /** [layoutId] null removes the rule. */
    fun setRule(setup: ScreenSetup, scope: RuleScope, value: String, layoutId: String?) {
        val map = rulesMap(setup).toMutableMap()
        val key = scope.key(value)
        if (layoutId == null) map.remove(key) else map[key] = layoutId
        prefs.edit().putString(rulesKey(setup), gson.toJson(map)).apply()
    }

    /**
     * The layout for [game]: this game › a collection it's in › its console ›
     * its emulator › your default › Voltcap's default. Rules pointing at a
     * layout that no longer exists are skipped.
     */
    fun resolve(
        setup: ScreenSetup,
        game: Game,
        collectionKeys: List<String>,
        emulatorPackage: String?
    ): ResolvedLayout {
        val rules = rulesMap(setup)
        val all = layouts().associateBy { it.id }
        val candidates = buildList {
            add(RuleScope.GAME to game.stableKey)
            collectionKeys.forEach { add(RuleScope.COLLECTION to it) }
            add(RuleScope.CONSOLE to game.platform)
            if (emulatorPackage != null) add(RuleScope.EMULATOR to emulatorPackage)
            add(RuleScope.DEFAULT to "")
        }
        for ((scope, value) in candidates) {
            val layout = rules[scope.key(value)]?.let { all[it] } ?: continue
            return ResolvedLayout(layout, scope, value.ifEmpty { null })
        }
        val fallback = all[ScreenLayouts.fallback(setup, game, emulatorPackage)] ?: ScreenLayouts.builtIns.first()
        return ResolvedLayout(fallback, null, null)
    }

    // ---- Which setup a connected screen is ----

    private fun kinds(): Map<String, String> =
        prefs.getString("kinds_json", null)
            ?.let { runCatching { gson.fromJson<Map<String, String>>(it, kindsType) }.getOrNull() }
            .orEmpty()

    /** Your choice for this screen, or a guess from its name. */
    fun setupFor(screen: SecondScreenInfo): ScreenSetup =
        kinds()[screen.fingerprint]?.let { ScreenSetup.of(it) } ?: screen.guessedSetup

    fun setSetupFor(screen: SecondScreenInfo, setup: ScreenSetup) {
        val map = kinds() + (screen.fingerprint to setup.key)
        prefs.edit().putString("kinds_json", gson.toJson(map)).apply()
    }
}

/** A second screen as Voltcap sees it. Remembered by name and resolution,
 * because display numbers change between boots and plug-ins. */
data class SecondScreenInfo(
    val displayId: Int,
    val name: String,
    val width: Int,
    val height: Int
) {
    val fingerprint: String get() = "$name|${width}x$height"

    /** Screens reached through a cable or casting are TVs/monitors; anything
     * else is taken as built in. Can be corrected in Settings › Screens. */
    val guessedSetup: ScreenSetup
        get() {
            val n = name.lowercase()
            val external = listOf("hdmi", "displayport", " dp", "usb", "external", "wifi", "wireless", "cast", "miracast")
            return if (external.any { it in n }) ScreenSetup.TV else ScreenSetup.DUAL
        }
}

/**
 * Finding the second screen and starting things on it.
 *
 * Order matters: Android sends the controller to whatever started last, so
 * the other screen is filled first and the game is started after it.
 */
object ScreenPlacement {

    fun secondScreen(context: Context): SecondScreenInfo? = runCatching {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return@runCatching null
        fun usable(d: Display) = d.displayId != Display.DEFAULT_DISPLAY && d.isValid &&
            (d.flags and Display.FLAG_PRIVATE) == 0
        val display = manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull { usable(it) }
            ?: manager.displays.firstOrNull { usable(it) }
            ?: return@runCatching null
        val mode = display.mode
        SecondScreenInfo(display.displayId, display.name ?: "Display ${display.displayId}", mode.physicalWidth, mode.physicalHeight)
    }.getOrNull()

    /** Options that put an activity on [displayId] (null: Android decides). */
    fun optionsFor(displayId: Int?): android.os.Bundle? {
        if (displayId == null || Build.VERSION.SDK_INT < 26) return null
        return ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
    }

    /** Starts an installed app on [displayId]. False if Android refused. */
    fun startApp(context: Context, packageName: String, displayId: Int): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent, optionsFor(displayId)); true }.getOrDefault(false)
    }
}
