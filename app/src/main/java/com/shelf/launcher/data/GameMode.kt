package com.shelf.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * A game started from Voltcap is in front. While it is, Voltcap keeps its own power use as low as
 * it can, so the battery and the chip's heat go to the game:
 *  - background work waits (artwork / details / manuals scraping, saving art, RetroAchievements
 *    hashing and icon lookups) and carries on when you come back ([awaitNotPlaying]);
 *  - decoded pictures are dropped from memory (less memory pressure on the emulator);
 *  - the performance tuner's loop, the charge limit, the unlock watch and Discord status all wake
 *    up far less often;
 *  - the home screen's animations already stop on their own while Voltcap isn't on screen.
 * Nothing here slows the game itself down.
 */
object GameMode {
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    /** True while a game started from Voltcap is in front. */
    val active: Boolean get() = _playing.value

    /** Update 182: when the game was started (see [userIsHere]). */
    @Volatile private var enteredAt = 0L

    fun enter(context: Context) {
        if (_playing.value) return
        enteredAt = System.currentTimeMillis()
        _playing.value = true
        BatterySaver.gameRunning = true // (Update 188)
        runCatching {
            com.shelf.launcher.device.MemoryBudget.release(context, android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        }
    }

    /** Back in Voltcap: background work picks up where it stopped. */
    fun exit() {
        _playing.value = false
        BatterySaver.gameRunning = false // (Update 188)
    }

    /**
     * Update 182: a key or touch reached Voltcap's home screen, so no game has the controller: game mode ends (after the
     * first seconds of a launch). On a two-screen handheld Voltcap can stay resumed the whole time a game runs on the
     * other screen, so the resume check never saw the game end — scraping ("Getting art 1/215"), saving art and syncing
     * then waited forever.
     */
    fun userIsHere() {
        if (_playing.value && System.currentTimeMillis() - enteredAt > 6_000L) { _playing.value = false; BatterySaver.gameRunning = false }
    }

    /** Suspends while a game is in front (background loops call this between items). */
    suspend fun awaitNotPlaying() {
        if (_playing.value) _playing.first { !it }
    }
}
