package com.shelf.launcher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/*
 * Update 101: everything on the home screen can be reached with the D-pad / left stick, on every layout.
 *
 * The games keep the controller as before. What sits beside them — the game header's Play and round buttons, the round
 * shortcuts folded into a corner ☰ or floating anywhere, the corner music player — is reached by going past the edge
 * of the games towards it: ▲ for what is above (or at the top of the screen), ▼ for what is below. Going on the same way
 * reaches the next thing further out; the other way (or B) comes back. ◀ ▶ move along the buttons, A presses one.
 * Any other button leaves and does its usual job (Start, Select, L1, R1…).
 *
 * The style homes' own menus and bars stay in StyleNav (StyleHomes.kt); when one of them runs out at the top or the
 * bottom it hands over to this (styleHomeKey's reach).
 */
internal object HomeReach {
    enum class Zone { HEADER, SHORTCUTS, MUSIC }

    var zone by mutableStateOf<Zone?>(null)
    var index by mutableIntStateOf(0)

    /** The game header's buttons as drawn (Play, ★, ⓘ, ⚙, ☁), set by HomeShowcaseHeader. */
    @Volatile var header: List<() -> Unit> = emptyList()

    /** The music theme's Now playing panel is up (it replaces the small player, whose buttons are then not there). */
    @Volatile var musicPanel = false

    /** That panel sits above the game row (else below it). */
    @Volatile var musicPanelUp = false

    /** What lies above / below the games, nearest first (set by the home screen as it draws). */
    @Volatile var up: List<Zone> = emptyList()
    @Volatile var down: List<Zone> = emptyList()

    /** How many buttons each zone has (set by the home screen). */
    @Volatile var counts: Map<Zone, Int> = emptyMap()

    internal var enteredAt = 0L
    internal val keysDown = HashSet<Int>()

    /** Which way the selection came in, so the same way goes further out and the other way back. */
    private var fromUp = true

    fun on(z: Zone, i: Int): Boolean = zone == z && index == i

    fun leave() {
        zone = null
        index = 0
    }

    private fun count(z: Zone): Int = if (z == Zone.HEADER) header.size else counts[z] ?: 0

    /** Goes past the games' edge: [goingUp] ▲, else ▼. False when there's nothing that way. */
    fun enter(goingUp: Boolean, at: Long): Boolean {
        val z = (if (goingUp) up else down).firstOrNull { count(it) > 0 } ?: return false
        zone = z
        index = if (z == Zone.MUSIC) MINI_PLAY.coerceAtMost(count(z) - 1) else 0
        fromUp = goingUp
        enteredAt = at
        return true
    }

    /**
     * The keys while a zone is on (call it before everything else of the home screen). True = handled.
     * [press] runs the zone's button; [sound] plays the move / open / back sounds.
     */
    fun key(event: KeyEvent, press: (Zone, Int) -> Unit, sound: (UiSound) -> Unit): Boolean {
        val z = zone ?: return false
        val n = count(z)
        if (n == 0) { leave(); return false }
        val code = event.nativeKeyEvent.keyCode
        val down = event.type == KeyEventType.KeyDown
        val up = event.type == KeyEventType.KeyUp
        if (up && keysDown.remove(code)) return true
        val k = event.key
        val first = event.nativeKeyEvent.repeatCount == 0
        when (k) {
            Key.DirectionLeft, Key.DirectionRight -> {
                if (down) {
                    val next = (index + if (k == Key.DirectionRight) 1 else -1).coerceIn(0, n - 1)
                    if (next != index) { index = next; sound(UiSound.MOVE) }
                    keysDown += code
                }
                return true
            }
            Key.DirectionUp, Key.DirectionDown -> {
                if (down && first) {
                    val outward = (k == Key.DirectionUp) == fromUp
                    val list = if (fromUp) this.up else this.down
                    val here = list.indexOf(z)
                    if (outward) {
                        // Further out the same way, if there's more there.
                        list.drop(here + 1).firstOrNull { count(it) > 0 }?.let { nz ->
                            zone = nz; index = if (nz == Zone.MUSIC) MINI_PLAY.coerceAtMost(count(nz) - 1) else 0; sound(UiSound.MOVE)
                        }
                    } else {
                        // Back towards the games (and onto them past the nearest).
                        val back = list.take(here.coerceAtLeast(0)).lastOrNull { count(it) > 0 }
                        if (back != null) { zone = back; index = 0 } else leave()
                        sound(UiSound.MOVE)
                    }
                }
                if (down) keysDown += code
                return true
            }
            Key.ButtonA, Key.Enter, Key.DirectionCenter -> {
                if (up && event.nativeKeyEvent.downTime >= enteredAt) {
                    sound(UiSound.OPEN)
                    press(z, index)
                }
                return true
            }
            Key.ButtonB, Key.Back -> {
                if (up) { leave(); sound(UiSound.BACK) }
                return true
            }
            else -> {
                // Any other button: back to the games, and it does its usual job.
                if (down) leave()
                return false
            }
        }
    }
}
