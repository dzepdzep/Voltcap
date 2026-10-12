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

    fun enter(context: Context) {
        if (_playing.value) return
        _playing.value = true
        runCatching {
            com.shelf.launcher.device.MemoryBudget.release(context, android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        }
    }

    /** Back in Voltcap: background work picks up where it stopped. */
    fun exit() {
        _playing.value = false
    }

    /** Suspends while a game is in front (background loops call this between items). */
    suspend fun awaitNotPlaying() {
        if (_playing.value) _playing.first { !it }
    }
}
