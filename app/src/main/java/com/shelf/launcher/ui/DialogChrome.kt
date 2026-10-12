package com.shelf.launcher.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.launch

/**
 * Shared building blocks for every popup in the app, so a Settings card,
 * a connection row inside the RetroAchievements dialog, and a menu item in
 * the game options sheet all read as the same design language rather than
 * three different eras of the app.
 */

// ---- Interaction ----

/**
 * The real screen size in dp *as the app currently lays out*. Differs from
 * Configuration.screenWidthDp/HeightDp once the size setting (Compact /
 * Large) scales density, so anything sizing itself to the screen uses this.
 */
@Composable
internal fun screenSizeDp(): DpSize {
    com.shelf.launcher.Breadcrumbs.mark("screenSizeDp")
    // The window's real size: displayMetrics leaves out the navigation bar's room on many handhelds even
    // though Voltcap draws edge to edge (immersive) — things "centred" on it sat left of the middle.
    val window = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val metrics = LocalContext.current.resources.displayMetrics
    return with(LocalDensity.current) {
        if (window.width > 0 && window.height > 0) DpSize(window.width.toDp(), window.height.toDp())
        else DpSize(metrics.widthPixels.toDp(), metrics.heightPixels.toDp())
    }
}

/**
 * Text/icon colour for anything sitting on the accent colour. Picks black or
 * white by the accent's brightness, so labels stay readable on every theme —
 * including Mono, whose accent is pure white.
 */
@Composable
internal fun onAccentColor(): Color =
    if (ShelfTheme.accent.luminance() > 0.5f) Color.Black else Color.White

/**
 * The D-pad focus outline: a light ring with a dark inner edge, so it shows
 * up on dark cards and on light (e.g. white accent) buttons alike. Drawn on
 * top of the content, so it never changes an element's size.
 */
@Composable
internal fun Modifier.focusRing(focused: Boolean, shape: Shape): Modifier {
    com.shelf.launcher.Breadcrumbs.mark("focusRing")
    // Colours are read every time and there's a single return: drawing code
    // must never leave early (see the note in AppIcons.kt).
    val outer = ShelfTheme.colors.textPrimary
    val inner = ShelfTheme.colors.background
    // The Volt look: an accent outline with a soft glow (VoltUi.kt).
    val volt = VoltUi.on
    val accent = ShelfTheme.accent
    val glow = VoltUi.glow && !LocalLiteEffects.current
    val pixelFocus = VoltUi.pixel
    val crtFocus = com.shelf.launcher.data.Features.uiStyle.state.value == com.shelf.launcher.data.Features.UiStyle.CRT
    return if (!focused) this else if (volt) this.voltFocus(true, shape, accent, glow && !pixelFocus, pixelFocus, crtFocus) else this.drawWithContent {
        drawContent()
        val outerWidth = 2.5.dp.toPx()
        // Something can be focused while it's still (or again) zero-sized —
        // mid-animation, or collapsed. Insetting that gives a negative size,
        // which crashes, so the rings are only drawn when there's room.
        if (size.width > outerWidth * 2 && size.height > outerWidth * 2) {
            drawOutline(shape.createOutline(size, layoutDirection, this), outer, style = Stroke(outerWidth))
            inset(outerWidth) {
                drawOutline(shape.createOutline(size, layoutDirection, this), inner, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

/**
 * clickable + a visible focus ring. With a gamepad, Material's focus state
 * layer is nearly invisible on a small screen, so the element being moved to
 * with the D-pad gets a solid outline instead. Touch taps are unaffected.
 */
@Composable
internal fun Modifier.shelfClickable(
    shape: Shape = uiRounded(12.dp),
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    com.shelf.launcher.Breadcrumbs.mark("shelfClickable")
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    LaunchedEffect(focused) { if (focused) sounds?.play(UiSound.MOVE) }
    return this
        .focusRing(focused, shape)
        .clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = {
                sounds?.play(UiSound.SELECT)
                onClick()
            }
        )
}

/** [shelfClickable] with a second action on hold (long press with a finger or the A button). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.shelfClickableLong(
    shape: Shape = uiRounded(12.dp),
    onLongClick: () -> Unit,
    onClick: () -> Unit
): Modifier {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    LaunchedEffect(focused) { if (focused) sounds?.play(UiSound.MOVE) }
    return this
        .focusRing(focused, shape)
        .combinedClickable(
            interactionSource = source,
            indication = LocalIndication.current,
            onLongClick = { sounds?.play(UiSound.OPEN); onLongClick() },
            onClick = { sounds?.play(UiSound.SELECT); onClick() }
        )
}

/**
 * Makes a read-only row reachable with the D-pad (with the same focus ring),
 * so long lists like achievements can be walked through and auto-scroll.
 */
@Composable
internal fun Modifier.shelfFocusable(shape: Shape = uiRounded(12.dp)): Modifier {
    com.shelf.launcher.Breadcrumbs.mark("shelfFocusable")
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val sounds = LocalUiSounds.current
    LaunchedEffect(focused) { if (focused) sounds?.play(UiSound.MOVE) }
    return this
        .focusRing(focused, shape)
        .focusable(interactionSource = source)
}

/**
 * D-pad handling for a scrolling popup body: Up/Down moves to the next
 * selectable item when there is one, and simply scrolls when there isn't —
 * so text-only content below the last button is still reachable. Held keys
 * repeat.
 */
@Composable
internal fun Modifier.dpadScroll(scrollState: androidx.compose.foundation.ScrollState): Modifier {
    com.shelf.launcher.Breadcrumbs.mark("dpadScroll")
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    return this.onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val direction = when (event.key) {
            Key.DirectionDown -> FocusDirection.Down
            Key.DirectionUp -> FocusDirection.Up
            else -> return@onKeyEvent false
        }
        if (!focusManager.moveFocus(direction)) {
            scope.launch {
                scrollState.animateScrollBy(if (direction == FocusDirection.Down) 220f else -220f)
            }
        }
        true
    }
}

/**
 * An invisible focus stop for pages with nothing to select (like Profile):
 * while it has focus, ▲ ▼ scroll [scrollState]; at the very end it lets the
 * press through, so focus can still move on (to Close, or back up).
 */
@Composable
internal fun ScrollStop(scrollState: androidx.compose.foundation.ScrollState) {
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> if (scrollState.value < scrollState.maxValue) {
                        scope.launch { scrollState.animateScrollBy(220f) }
                        true
                    } else false
                    Key.DirectionUp -> if (scrollState.value > 0) {
                        scope.launch { scrollState.animateScrollBy(-220f) }
                        true
                    } else false
                    else -> false
                }
            }
            .focusable()
    )
}

/**
 * Text input that doesn't grab focus by itself. Shown as a box until you
 * press A (or tap) on it; only then does it become a real text field and
 * open the keyboard. Without this, opening a popup whose first control is a
 * text field — or just moving past one with the D-pad — pops the keyboard.
 */
@Composable
internal fun ShelfTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    leadingIcon: ImageVector = Icons.Filled.Edit,
    onSubmit: (() -> Unit)? = null
) {
    com.shelf.launcher.Breadcrumbs.mark("ShelfTextField")
    var editing by remember { mutableStateOf(false) }
    // Finishing with the keyboard's Done / B puts the focus back on the box;
    // otherwise the box is a brand-new node with no focus and the next D-pad
    // press would restart from the top of the dialog.
    var restoreFocus by remember { mutableStateOf(false) }
    val boxFocus = remember { FocusRequester() }

    if (!editing) {
        LaunchedEffect(Unit) {
            if (restoreFocus) {
                restoreFocus = false
                runCatching { boxFocus.requestFocus() }
            }
        }
        Row(
            modifier
                .fillMaxWidth()
                .clip(uiRounded(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), uiRounded(12.dp))
                .focusRequester(boxFocus)
                .shelfClickable { editing = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(leadingIcon, null, tint = ShelfTheme.textMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = ShelfTheme.textMuted, fontSize = 11.sp)
                Text(
                    when {
                        value.isBlank() -> "Press A to type"
                        password -> "•".repeat(value.length.coerceAtMost(16))
                        else -> value
                    },
                    color = if (value.isBlank()) ShelfTheme.textMuted else ShelfTheme.colors.textPrimary,
                    fontSize = 14.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(14.sp), maxFontSize = 14.sp),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    } else {
        val focusRequester = remember { FocusRequester() }
        var hadFocus by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
        TextInputActiveEffect(focusRequester)

        val finish = {
            restoreFocus = true
            editing = false
            onSubmit?.invoke()
            Unit
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = if (onSubmit != null) ImeAction.Search else ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { finish() }, onSearch = { finish() }),
            shape = uiRounded(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ShelfTheme.colors.textPrimary,
                unfocusedTextColor = ShelfTheme.colors.textPrimary,
                focusedBorderColor = ShelfTheme.accent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedLabelColor = ShelfTheme.accent,
                unfocusedLabelColor = ShelfTheme.textMuted,
                cursorColor = ShelfTheme.accent
            ),
            modifier = modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                // B / Back on a controller folds the field back into its box.
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.ButtonB || event.key == Key.Back) {
                        if (event.type == KeyEventType.KeyDown) { restoreFocus = true; editing = false }
                        true
                    } else false
                }
                // Leaving the field (D-pad away, Back) folds it back into a box.
                .onFocusChanged {
                    if (it.isFocused) hadFocus = true
                    else if (hadFocus) {
                        hadFocus = false
                        editing = false
                    }
                }
        )
    }
}

// ---- Scaffolds ----

/**
 * The standard single-pane popup: icon badge + title + subtitle header, a
 * scrollable card-based content area, and a footer row (defaults to a single
 * accent "Close" pill). Used for every dialog except Settings itself, which
 * needs a second (sidebar) column, and the compact game-options menu, which
 * uses [ShelfMenuDialog] instead.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ShelfDialog(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    widthFraction: Float = 0.88f,
    maxContentHeightFraction: Float = 0.6f,
    footer: (@Composable RowScope.() -> Unit)? = null,
    // false → the body is a plain height-bounded Column (no verticalScroll) so it can host a lazy list.
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("ShelfDialog")
    val screen = screenSizeDp()
    val sounds = LocalUiSounds.current
    LaunchedEffect(Unit) { sounds?.play(UiSound.OPEN) }
    Dialog(
        onDismissRequest = {
            sounds?.play(UiSound.BACK)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ImmersiveDialogWindow()
        // The popup is capped to the screen, and the body — not the header or
        // the buttons — is what shrinks and scrolls. Before, a tall popup on a
        // short landscape screen pushed the bottom-right buttons out of view.
        Box(
            Modifier
                .fillMaxWidth(widthFraction)
                .heightIn(max = screen.height * 0.94f)
                .voltSurface(uiRounded(if (com.shelf.launcher.data.Features.pixelUi.value) 0.dp else 24.dp), base = ShelfTheme.colors.surface)
                .border(1.dp, Color.White.copy(alpha = 0.08f), uiRounded(if (com.shelf.launcher.data.Features.pixelUi.value) 0.dp else 24.dp))
        ) {
            Column(Modifier.fillMaxWidth()) {
                DialogHeader(icon = icon, title = title, subtitle = subtitle, onDismiss = onDismiss)
                HeaderDivider()
                val bodyScroll = rememberScrollState()
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .heightIn(max = screen.height * maxContentHeightFraction.coerceAtLeast(0.7f))
                        .then(if (scrollable) Modifier.dpadScroll(bodyScroll).verticalScroll(bodyScroll) else Modifier)
                        .padding(20.dp),
                    content = content
                )
                // Wraps onto a second line (right-aligned) when the buttons don't fit side by side: on a narrow
                // screen the last ones used to be squeezed to nothing.
                androidx.compose.foundation.layout.FlowRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (footer != null) footer() else Chip("Close", onClick = onDismiss)
                }
            }
        }
    }
}

/**
 * A narrower, title-only popup for compact menus (the game options sheet):
 * no icon badge, just a bold title / muted subtitle and a vertical list of
 * [MenuRow]s. Width hugs its content instead of taking a screen fraction.
 */
@Composable
fun ShelfMenuDialog(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("ShelfMenuDialog")
    val screen = screenSizeDp()
    val sounds = LocalUiSounds.current
    LaunchedEffect(Unit) { sounds?.play(UiSound.OPEN) }
    Dialog(
        onDismissRequest = {
            sounds?.play(UiSound.BACK)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ImmersiveDialogWindow()
        // Same look as the Select menu: icon badge header, rows as cards.
        Box(
            Modifier
                .fillMaxWidth(0.62f)
                .widthIn(min = 320.dp, max = 720.dp)
                .voltSurface(uiRounded(if (com.shelf.launcher.data.Features.pixelUi.value) 0.dp else 24.dp), base = ShelfTheme.colors.surface)
                .border(1.dp, Color.White.copy(alpha = 0.08f), uiRounded(if (com.shelf.launcher.data.Features.pixelUi.value) 0.dp else 24.dp))
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        IconBadge(icon, size = 44.dp)
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            title, color = ShelfTheme.colors.textPrimary, fontSize = 20.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(20.sp), maxFontSize = 20.sp), fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (subtitle != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(subtitle, color = ShelfTheme.textMuted, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    CloseButton(onDismiss)
                }
                HeaderDivider()
                val menuScroll = rememberScrollState()
                Column(
                    Modifier
                        .heightIn(max = screen.height * 0.66f)
                        .dpadScroll(menuScroll)
                        .verticalScroll(menuScroll)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun DialogHeader(icon: ImageVector, title: String, subtitle: String?, onDismiss: () -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("DialogHeader")
    // Short landscape screens need the room for content, not header padding.
    val compact = screenSizeDp().height < 480.dp
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = if (compact) 12.dp else 20.dp),
        verticalAlignment = Alignment.Top
    ) {
        IconBadge(icon, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ShelfTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = headingFont())
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = ShelfTheme.textMuted, fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        CloseButton(onDismiss)
    }
}

@Composable
internal fun CloseButton(onClick: () -> Unit, size: Dp = 36.dp) {
    com.shelf.launcher.Breadcrumbs.mark("CloseButton")
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .shelfClickable(CircleShape) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Close, null, tint = ShelfTheme.textMuted, modifier = Modifier.size(size * 0.5f))
    }
}

/** Under a popup's header: the voltage line in the Volt look, a hairline in the classic one. */
@Composable
internal fun HeaderDivider() {
    if (VoltUi.on) VoltageLine(Modifier.fillMaxWidth()) else ThinDivider()
}

@Composable
internal fun ThinDivider() {
    com.shelf.launcher.Breadcrumbs.mark("ThinDivider")
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
}

// ---- Cards & rows ----

@Composable
internal fun IconBadge(icon: ImageVector, size: Dp = 38.dp, tint: Color? = null) {
    com.shelf.launcher.Breadcrumbs.mark("IconBadge")
    // One look everywhere (Ds): the accent on a faint accent plate; red only for destructive actions.
    val c = tint ?: iconAccent(icon) ?: ShelfTheme.accent
    val shape = uiRounded(com.shelf.launcher.ui.theme.Ds.rMedium)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(c.copy(alpha = com.shelf.launcher.ui.theme.Ds.accentFill))
            .border(1.dp, c.copy(alpha = 0.28f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = c, modifier = Modifier.size(size / 2))
    }
}

/**
 * The colour for an action's icon: null (= the theme accent) for everything except destructive actions.
 */
internal fun iconAccent(icon: ImageVector): Color? {
    // Design rule (Ds): icons follow the theme accent everywhere. Only destructive actions get their
    // own (red) colour, because there the colour carries meaning.
    val n = icon.name.substringAfter('.').lowercase()
    return if (listOf("delete", "removecircle", "block", "deleteforever").any { n.contains(it) }) com.shelf.launcher.ui.theme.Ds.danger else null
}

@Composable
internal fun StatusDot(active: Boolean, size: Dp = 8.dp) {
    com.shelf.launcher.Breadcrumbs.mark("StatusDot")
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) com.shelf.launcher.ui.theme.Ds.success else com.shelf.launcher.ui.theme.Ds.danger)
    )
}

/** The base rounded card everything in a content pane sits in. */
@Composable
internal fun ShelfCard(content: @Composable ColumnScope.() -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("ShelfCard")
    Column(
        Modifier
            .fillMaxWidth()
            .voltSurface(uiRounded(16.dp))
            .padding(com.shelf.launcher.ui.theme.Ds.gap(16f)),
        content = content
    )
}

@Composable
internal fun ActionCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    com.shelf.launcher.Breadcrumbs.mark("ActionCard")
    Row(
        modifier
            .fillMaxWidth()
            .voltSurface(uiRounded(16.dp))
            .shelfClickable(uiRounded(16.dp)) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = ShelfTheme.textMuted, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun ConnectionCard(name: String, subtitle: String, connected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    com.shelf.launcher.Breadcrumbs.mark("ConnectionCard")
    Row(
        modifier
            .fillMaxWidth()
            .voltSurface(uiRounded(16.dp))
            .shelfClickable(uiRounded(16.dp)) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(connected)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = ShelfTheme.textMuted, fontSize = 12.sp)
        }
        Chip(if (connected) "Connected" else "Set up", accent = !connected, onClick = onClick)
    }
}

/** A compact, full-bleed row for menus (game options, category lists) —
 * icon, title, optional subtitle. [destructive] tints it toward red for
 * things like "Remove" or "Hide". */
@Composable
internal fun MenuRow(icon: ImageVector, title: String, subtitle: String? = null, destructive: Boolean = false, onClick: () -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("MenuRow")
    val red = Color(0xFFB5583C)
    val shape = uiRounded(16.dp)
    // A card, like the rows in the Select menu.
    Row(
        Modifier
            .fillMaxWidth()
            .voltSurface(shape)
            .shelfClickable(shape) { onClick() }
            .padding(horizontal = 14.dp, vertical = com.shelf.launcher.ui.theme.Ds.gap(12f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (destructive) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(uiRounded(12.dp))
                    .background(red.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = red, modifier = Modifier.size(19.dp)) }
        } else {
            IconBadge(icon)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (destructive) red else ShelfTheme.colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(subtitle, color = ShelfTheme.textMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
internal fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    com.shelf.launcher.Breadcrumbs.mark("SwitchRow")
    // The whole row is the button (A toggles it), with the usual focus ring —
    // the switch itself is just the indicator.
    val shape = uiRounded(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .voltSurface(shape)
            .shelfClickable(shape) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = com.shelf.launcher.ui.theme.Ds.gap(14f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = ShelfTheme.textMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        if (VoltUi.on) VoltSwitch(checked)
        else androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = null,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = onAccentColor(),
                checkedTrackColor = ShelfTheme.accent
            )
        )
    }
}

/** Small accent heading that groups related settings. */
@Composable
internal fun SettingsHeading(text: String) {
    if (VoltUi.on) {
        VoltHeading(text, Modifier.padding(start = 4.dp, top = com.shelf.launcher.ui.theme.Ds.gap(18f), bottom = com.shelf.launcher.ui.theme.Ds.gap(8f)))
        return
    }
    Text(
        text.uppercase(),
        color = ShelfTheme.accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        fontFamily = headingFont(),
        modifier = Modifier.padding(start = 4.dp, top = com.shelf.launcher.ui.theme.Ds.gap(18f), bottom = com.shelf.launcher.ui.theme.Ds.gap(8f))
    )
}

/** Collects the cards for [SettingsGrid]. */
internal class SettingsGridScope {
    val items = mutableListOf<@Composable (Modifier) -> Unit>()
    fun item(content: @Composable (Modifier) -> Unit) {
        items += content
    }
}

/**
 * Lays settings cards out two per row when there's room (landscape), one per
 * row otherwise. Cards in a row share the same height. Each item gets the
 * modifier to apply to its card.
 */
@Composable
internal fun SettingsGrid(build: SettingsGridScope.() -> Unit) {
    val items = SettingsGridScope().apply(build).items
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 520.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(com.shelf.launcher.ui.theme.Ds.gap(10f))) {
            items.chunked(columns).forEach { row ->
                // Cards side by side get the same height: each is at least as tall as the tallest one
                // asks for (intrinsic height), but never shorter than it really needs — wrapping chip
                // rows can under-report, and cutting a card off is worse than a small mismatch.
                androidx.compose.ui.layout.Layout(
                    content = { row.forEach { item -> item(Modifier) } },
                    modifier = Modifier.fillMaxWidth()
                ) { measurables, constraints ->
                    val gap = 10.dp.roundToPx()
                    val w = ((constraints.maxWidth - gap * (columns - 1)) / columns).coerceAtLeast(0)
                    val wanted = measurables.maxOfOrNull { m -> runCatching { m.maxIntrinsicHeight(w) }.getOrDefault(0) } ?: 0
                    val placeables = measurables.map { m ->
                        m.measure(androidx.compose.ui.unit.Constraints(minWidth = w, maxWidth = w, minHeight = wanted))
                    }
                    val h = placeables.maxOfOrNull { it.height } ?: 0
                    layout(constraints.maxWidth, h) {
                        placeables.forEachIndexed { i, p -> p.place(i * (w + gap), 0) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RadioLine(label: String, selected: Boolean, blurb: String? = null, onClick: () -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("RadioLine")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(uiRounded(10.dp))
            .shelfClickable(uiRounded(10.dp)) { onClick() }
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (selected) ShelfTheme.accent else ShelfTheme.textMuted, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(ShelfTheme.accent))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (blurb != null) {
                Text(blurb, color = ShelfTheme.textMuted, fontSize = 11.sp)
            }
        }
    }
}

/** A pill button. [accent] fills it with the theme accent (the default,
 * for primary actions); otherwise it's a quiet translucent chip. */
@Composable
internal fun Chip(label: String, accent: Boolean = true, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("Chip")
    val volt = VoltUi.on
    Box(
        modifier
            .clip(uiRounded(50))
            .background(
                when {
                    !enabled -> androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.05f))
                    accent && volt -> voltFill()
                    accent -> androidx.compose.ui.graphics.SolidColor(ShelfTheme.accent)
                    else -> androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = if (volt) 0.06f else 0.08f))
                }
            )
            .then(if (volt && !accent && enabled) Modifier.border(1.dp, VoltUi.hairline, uiRounded(50)) else Modifier)
            .shelfClickable(uiRounded(50), enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            color = when {
                !enabled -> ShelfTheme.textMuted
                accent -> onAccentColor()
                else -> ShelfTheme.colors.textPrimary
            },
            fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp),
            fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            softWrap = false
        )
    }
}

/**
 * A row of chips that wraps onto a second (third…) line instead of scrolling
 * sideways, so every option is visible and one D-pad press away.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun ChipFlow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

@Composable
internal fun SectionLabel(text: String) {
    com.shelf.launcher.Breadcrumbs.mark("SectionLabel")
    Text(
        text,
        color = ShelfTheme.colors.textPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = headingFont(),
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

/**
 * The font for headings (dialog titles, section labels, settings headings): the 32-bit type with the 32-bit
 * console style even when the rest stays normal ("where it fits"), else whatever the interface uses.
 */
@Composable
internal fun headingFont(): androidx.compose.ui.text.font.FontFamily? =
    androidx.compose.material3.MaterialTheme.typography.titleMedium.fontFamily

@Composable
internal fun MutedNote(text: String) {
    com.shelf.launcher.Breadcrumbs.mark("MutedNote")
    Text(text, color = ShelfTheme.textMuted, fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
internal fun EmptyState(text: String) {
    com.shelf.launcher.Breadcrumbs.mark("EmptyState")
    Text(
        text,
        color = ShelfTheme.textMuted,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(vertical = 10.dp)
    )
}
