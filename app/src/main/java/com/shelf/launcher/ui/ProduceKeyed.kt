package com.shelf.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.CoroutineScope

/**
 * QA rule STALE-PRODUCE (tools/qa/RULES.md): `produceState` keeps its last value when its keys change — only the producer
 * restarts, and it can't run before the next frame. A slot that moved on to another game, console or file therefore
 * showed the PREVIOUS one's picture (or text) for a frame or more: the "old cover flashing" class of glitches.
 *
 * [produceKeyed] is produceState whose value belongs to [identity] (the game / file / console it is about): while the
 * stored value was made for another identity, [initialValue] (this identity's cached value, or empty) is returned
 * instead — also to the producer itself, so `if (value == null) value = load()` loads again for the new identity.
 * [keys] restart the producer as before without hiding the value (a look or version change keeps the old picture of the
 * SAME thing until the new one is ready — no blank frame).
 */
@Composable
internal fun <T> produceKeyed(
    initialValue: T,
    identity: Any?,
    vararg keys: Any?,
    producer: suspend ProduceStateScope<T>.() -> Unit
): State<T> {
    val inner = produceState(identity to initialValue, identity, *keys) {
        KeyedProduceScope(identity, initialValue, this).producer()
    }
    val cur = inner.value
    return KeyedValue(if (cur.first == identity) cur.second else initialValue)
}

private class KeyedValue<T>(override val value: T) : State<T>

private class KeyedProduceScope<T>(
    private val identity: Any?,
    private val initial: T,
    private val outer: ProduceStateScope<Pair<Any?, T>>
) : ProduceStateScope<T>, CoroutineScope by outer {
    override var value: T
        get() = outer.value.let { if (it.first == identity) it.second else initial }
        set(v) { outer.value = identity to v }
    override fun component1(): T = value
    override fun component2(): (T) -> Unit = { value = it }
    override suspend fun awaitDispose(onDispose: () -> Unit): Nothing = outer.awaitDispose(onDispose)
}
