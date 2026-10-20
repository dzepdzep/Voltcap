package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.shelf.launcher.data.Features
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Update 274 (Toni): asked once, after this update — what a cartridge / disc / case does once it's out in the game
 * window: Inspect (turn it by hand) or Wobble (it just sways). Settings › Game look › Covers, cases & discs › More look
 * options › "In the game window" changes it later.
 */
internal object InspectChoice {
    /** Not answered yet (a fresh install is asked too). */
    fun due(): Boolean = !Features.inspectModeAsked.value
}

@Composable
internal fun InspectChoiceDialog(onDone: () -> Unit) {
    val maxH = screenSizeDp().height * 0.92f
    val first = remember { FocusRequester() }
    var lit by remember { mutableStateOf(false) }
    fun pick(m: Features.InspectMode) {
        Features.inspectMode.value = m
        Features.inspectModeAsked.value = true
        onDone()
    }
    // (Back / outside: asked again next time — only a pick counts as an answer.)
    Dialog(onDismissRequest = onDone, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveDialogWindow()
        val view = androidx.compose.ui.platform.LocalView.current
        // (Lit at once, so the first press picks / moves: out of touch mode first — in the dialog's own window.)
        // (And again the moment the dialog's own window gets the key focus.)
        val winFocused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(winFocused) {
            if (winFocused && !lit) { kotlinx.coroutines.delay(30); KeyInput.leaveTouchMode(view); runCatching { first.requestFocus() } }
        }
        LaunchedEffect(Unit) {
            // (The dialog's window takes the key focus a moment after it opens: asked until a choice is lit, up to 3 s.)
            repeat(15) {
                kotlinx.coroutines.delay(200)
                if (lit) return@LaunchedEffect
                KeyInput.leaveTouchMode(view)
                runCatching { first.requestFocus() }
            }
        }
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(0.88f).heightIn(max = maxH)) { ShelfCard {
            SectionLabel("Cartridges, discs and cases in the game window")
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text(
                    "When you open a game drawn as its cartridge, disc or case, it comes out of its card. What should it do there?",
                    color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceCard(
                        "Inspect", "Drag it (or use the right stick) to turn it and look at every side, under it too. Left alone for 2 s it turns back, then sways gently.",
                        Modifier.weight(1f).focusRequester(first), onFocus = { if (it) lit = true }
                    ) { pick(Features.InspectMode.INSPECT) }
                    ChoiceCard(
                        "Wobble", "No turning by hand: it just sways gently left and right, as on the home rows.",
                        Modifier.weight(1f), onFocus = { if (it) lit = true }
                    ) { pick(Features.InspectMode.WOBBLE) }
                }
                Spacer(Modifier.height(8.dp))
                MutedNote("Change it any time: Settings › Game look › Covers, cases & discs › More look options › In the game window.")
            }
        } }
    }
}

@Composable
private fun ChoiceCard(title: String, text: String, modifier: Modifier, onFocus: (Boolean) -> Unit = {}, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val c = ShelfTheme.colors
    Column(
        modifier
            .heightIn(min = 120.dp)
            .clip(uiRounded(14))
            .background(if (focused) c.accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
            .border(if (focused) 2.dp else 1.dp, if (focused) c.accent else Color.White.copy(alpha = 0.14f), uiRounded(14))
            .onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) }
            .clickable { onClick() }
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text, color = c.textMuted, fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
    }
}
