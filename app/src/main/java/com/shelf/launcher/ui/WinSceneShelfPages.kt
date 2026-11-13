package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 529k (owner, 13 Nov: every layout's own Details / Stats / Options / Achievements pages, "literally all popups" - not the shared
 * expanded page in the layout's colours): the shelf family - Game shelf, Case stacks, Console stack, Coverflow details (one window
 * scene, INDEX_CARD). Their pages are further cards of the same make: the next index card is drawn up out of the shelf's slot
 * (from below) over the one it replaces, which sinks back a little and fades; going back runs it the other way. The case, the
 * module, the big cover and the card's own wood / slate / metal stay put - the window never turns into another design.
 */

/** The shelf family's pages in key order (Info, Details, Achievements, Options; L1 / R1 walk them). */
internal val SHELF_TABS = listOf(WinPage.MAIN, WinPage.DETAILS, WinPage.TROPHIES, WinPage.OPTIONS)

/** The room a shelf-family page asks for: most of the window's height, so a list has whole rows (Pure). */
internal fun shelfPageHeight(maxH: Dp): Dp = (maxH * 0.86f).coerceAtLeast(200.dp)

/**
 * The inside of a shelf-family card: [main] (Info, as each window draws it) or one of its pages - the page's name on the card's
 * label, the game's name, the page in a well of the card's own ground ([well]) and the card's keys ([key], each window's own
 * style). A page change draws the next card up from the slot; with animations off it is immediate. [main] gets `live` = false
 * for the copy on its way out: its keys must not keep focus requesters (WinAction.inert).
 */
@Composable
internal fun ShelfFamilyPages(
    ctx: WinSceneCtx,
    modifier: Modifier,
    label: Color,
    ink: Color,
    well: Color,
    key: @Composable (WinAction) -> Unit,
    main: @Composable ColumnScope.(live: Boolean) -> Unit
) {
    val anim = rememberAnimationsEnabled()
    val tabs = SHELF_TABS.filter { it in ctx.pages }
    AnimatedContent(
        targetState = ctx.page,
        transitionSpec = {
            val dir = pageDirection(tabs, initialState, targetState)
            if (!anim) EnterTransition.None togetherWith ExitTransition.None
            // (Forward: the next card rises out of the slot; back: the card drops back and the one before comes down to it.)
            else (slideInVertically(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { h -> h * dir / 3 } + fadeIn(tween(110))) togetherWith
                (slideOutVertically(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { h -> -h * dir / 10 } + fadeOut(tween(NATIVE_PAGE_MS)))
        },
        contentKey = { it },
        modifier = modifier.onPreviewKeyEvent { e ->
            // (L1 / R1 walk Info, Details, Achievements, Options - as their keys open them, as on Tile home and Storage boxes.)
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val step = when (e.key) { Key.ButtonL1 -> -1; Key.ButtonR1 -> 1; else -> return@onPreviewKeyEvent false }
            val to = pageStep(tabs, ctx.page, step)
            if (to != ctx.page) ctx.open(to)
            true
        },
        label = "shelfPage"
    ) { p ->
        val live = p == ctx.page
        if (p == WinPage.MAIN) Column { main(live) }
        else Column(Modifier.height(shelfPageHeight(ctx.maxH))) {
            Text(winPageTitle(p).uppercase(), color = label, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            StableFullText(ctx.name, color = ink, fontSize = 17.sp, maxLines = 2, fontWeight = FontWeight.Bold, reserve = false)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(well).padding(8.dp)) {
                // (The page draws on this card's well, without the case and name the card already shows.)
                androidx.compose.runtime.CompositionLocalProvider(LocalNativePageHost provides NativePageHost(Color.Transparent, dark = true, showsCover = true, showsName = true)) {
                    ctx.pageFor(p, Modifier.fillMaxSize(), live)
                }
            }
            Spacer(Modifier.height(8.dp))
            KeyRow(sceneKeys(ctx).map { if (live) it else it.inert() }, 6.dp) { key(it) }
        }
    }
}
