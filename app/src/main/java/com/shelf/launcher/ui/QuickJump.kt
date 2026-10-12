package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.ui.theme.ShelfTheme

/** # then A-Z — every stop on the quick-jump strip. */
val QUICK_JUMP_LETTERS: List<Char> = listOf('#') + ('A'..'Z').toList()

/**
 * Letter strip for jumping through a long library: opened with a stick click,
 * ◀ ▶ to pick, A to jump, B to close. Letters with no games are dimmed and
 * skipped by the D-pad.
 */
@Composable
fun QuickJumpBar(
    available: Set<Char>,
    selected: Char,
    onPick: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    com.shelf.launcher.Breadcrumbs.mark("QuickJumpBar")
    val listState = rememberLazyListState()
    val index = QUICK_JUMP_LETTERS.indexOf(selected).coerceAtLeast(0)
    LaunchedEffect(index) { listState.animateScrollToItem((index - 4).coerceAtLeast(0)) }

    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ShelfTheme.colors.surface.copy(alpha = 0.96f))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Jump to  ·  ◀ ▶ choose  ·  A jump  ·  B close",
            color = ShelfTheme.textMuted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(QUICK_JUMP_LETTERS) { letter ->
                val isSelected = letter == selected
                val enabled = letter in available
                Box(
                    Modifier
                        .size(if (isSelected) 46.dp else 38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) ShelfTheme.accent else Color.White.copy(alpha = 0.06f))
                        .pointerInput(letter, enabled) { detectTapGestures { if (enabled) onPick(letter) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        letter.toString(),
                        color = when {
                            isSelected -> onAccentColor()
                            enabled -> ShelfTheme.colors.textPrimary
                            else -> ShelfTheme.textMuted.copy(alpha = 0.35f)
                        },
                        fontSize = if (isSelected) 20.sp else 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** Next letter in [direction] that has games, or [from] if there is none. */
fun nextQuickJumpLetter(from: Char, direction: Int, available: Set<Char>): Char {
    var i = QUICK_JUMP_LETTERS.indexOf(from)
    while (true) {
        i += direction
        if (i !in QUICK_JUMP_LETTERS.indices) return from
        if (QUICK_JUMP_LETTERS[i] in available) return QUICK_JUMP_LETTERS[i]
    }
}
