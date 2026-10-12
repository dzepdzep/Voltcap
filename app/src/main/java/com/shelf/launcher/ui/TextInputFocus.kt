package com.shelf.launcher.ui

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * How many text inputs are being edited on the main display right now. While
 * any is, the second-screen Presentation must not be focusable: a focused
 * Presentation window steals the keyboard (or sends it to the wrong display).
 */
object TextInputFocus {
    private val _active = MutableStateFlow(0)
    val active: StateFlow<Int> = _active

    fun enter() { _active.value = _active.value + 1 }
    fun leave() { _active.value = (_active.value - 1).coerceAtLeast(0) }
}

/**
 * Put inside a text field that is in editing mode. Marks input as active,
 * then, once the second screen has let go of focus, focuses the field and
 * shows the keyboard explicitly.
 */
@Composable
internal fun TextInputActiveEffect(focusRequester: FocusRequester) {
    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(Unit) {
        TextInputFocus.enter()
        onDispose { TextInputFocus.leave() }
    }
    LaunchedEffect(Unit) {
        // Give the Presentation a moment to become non-focusable.
        delay(150)
        runCatching { view.requestFocus() }
        runCatching { focusRequester.requestFocus() }
        runCatching { keyboard?.show() }
        runCatching {
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}
