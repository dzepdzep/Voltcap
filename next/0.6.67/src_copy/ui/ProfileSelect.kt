package com.shelf.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Profiles
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.launch

/*
 * Update 332: "Who's playing?" — Voltcap's profile selector (data/Profiles.kt). Big round pictures in a row on a deep blue
 * night with slow drifting sparks (a console's user screen), the one you're on lifted, lit in its own colour and named
 * under it; picking one lifts it and the screen opens on your layout, its games rolling in one after another (Rollout).
 * Shown on a fresh start (Settings › Look & feel › Start-up) and any time from R1 › Who is playing or Settings.
 */

/** Update 332: asked from elsewhere (R1, Settings): LauncherScreen opens the selector. */
internal object ProfilePicker {
    var request by mutableStateOf(false)
    /** Update 334: an intent extra (true) that opens Voltcap on "Who's playing?" (the in-game Quick menu). */
    const val EXTRA = "voltcap_who_is_playing"
    /** v0.6.67: Settings' live preview of the looks (nothing is switched). */
    var preview by mutableStateOf(false)
}

/** One choice on the selector: a profile, or the "Add" circle (null). */
@Composable
internal fun ProfileSelectScreen(
    profiles: List<Profiles.Profile>,
    activeId: Int,
    raNames: List<String?>,
    steamAvatar: String?,
    /** At start-up: B keeps the profile that played last. Else B closes. */
    atStart: Boolean,
    onPick: (Int) -> Unit,
    onSkip: () -> Unit,
    /**
     * v0.6.67: a preview (Settings' look preview, QA's sample profiles): nothing is saved or switched — Add does nothing, the
     * editor and PIN only show. [onPick] / [onSkip] just close it.
     */
    demo: Boolean = false,
    /** v0.6.67: L1 / R1 (or the arrows on the look strip) change the look live — Settings' preview. */
    lookStrip: Boolean = false
) {
    val context = LocalContext.current
    val lite = LocalLiteEffects.current
    val canAdd = profiles.size < Profiles.MAX && !lookStrip
    val count = profiles.size + if (canAdd) 1 else 0
    var cursor by remember { mutableIntStateOf(profiles.indexOfFirst { it.id == activeId }.coerceAtLeast(0)) }
    var editing by remember { mutableStateOf<Profiles.Profile?>(null) }
    var leaving by remember { mutableStateOf(false) }
    // (Update 334: a locked profile asks its PIN before it plays or is edited — then [afterPin] goes ahead.)
    var pinFor by remember { mutableStateOf<Profiles.Profile?>(null) }
    var afterPin by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun unlockThen(p: Profiles.Profile, go: () -> Unit) { if (p.locked) { pinFor = p; afterPin = go } else go() }
    val scope = rememberCoroutineScope()
    val enter = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(if (lite) 180 else 520, easing = FastOutSlowInEasing)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(editing) { if (editing == null) { kotlinx.coroutines.delay(60); runCatching { focus.requestFocus() } } }
    // v0.6.67: the look (ProfileLooks.kt) — QA can show one for this session only (TestHooks.pickerLook), nothing saved.
    val look = TestHooks.pickerLook.value ?: Features.pickerLook.state.value
    val style = pickerStyle(look)
    val looks = Features.PickerLook.values()
    val planPerRow = remember { intArrayOf(1) }
    fun stepLook(d: Int) { Features.pickerLook.value = looks[(looks.indexOf(look) + d).mod(looks.size)] }

    fun pick(i: Int) {
        if (leaving) return
        if (i >= profiles.size) {
            // The Add circle: a new profile, straight into its editor.
            if (demo) return
            Profiles.add(context, "Player ${profiles.size + 1}")?.let { editing = it; cursor = Profiles.list.value.indexOfFirst { p -> p.id == it.id } }
            return
        }
        val id = profiles[i].id
        unlockThen(profiles[i]) {
            leaving = true
            scope.launch {
                exit.animateTo(1f, tween(if (lite) 120 else 460, easing = FastOutSlowInEasing))
                onPick(id)
            }
        }
    }

    Dialog(onDismissRequest = { if (!atStart) onSkip() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        ImmersiveDialogWindow()
        val accentNow = profiles.getOrNull(cursor)?.let { style.accent(it.hue) } ?: style.accent(210f)
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = (enter.value * (1f - exit.value * 0.9f)).coerceIn(0f, 1f) }
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (editing != null || pinFor != null || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val per = planPerRow[0]
                    when (e.key) {
                        Key.DirectionLeft -> { cursor = PickerGrid.step(cursor, count, per, -1, 0); true }
                        Key.DirectionRight -> { cursor = PickerGrid.step(cursor, count, per, 1, 0); true }
                        Key.DirectionUp -> { cursor = PickerGrid.step(cursor, count, per, 0, -1); true }
                        Key.DirectionDown -> { cursor = PickerGrid.step(cursor, count, per, 0, 1); true }
                        Key.ButtonL1 -> { if (lookStrip) stepLook(-1); lookStrip }
                        Key.ButtonR1 -> { if (lookStrip) stepLook(1); lookStrip }
                        Key.ButtonA, Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> { pick(cursor); true }
                        Key.ButtonY, Key.ButtonX -> { if (!lookStrip) profiles.getOrNull(cursor)?.let { p -> unlockThen(p) { editing = p } }; true }
                        // (Update 334: at start-up a locked profile that played last isn't kept without its PIN.)
                        Key.ButtonB, Key.Back, Key.Escape -> {
                            if (!leaving) {
                                val last = profiles.firstOrNull { it.id == activeId }
                                if (atStart && last != null && last.locked) pick(profiles.indexOf(last)) else onSkip()
                            }
                            true
                        }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            PickerBackdrop(look, accentNow, animate = !lite)
            val compact = maxHeight < 360.dp || maxWidth < 520.dp
            val plan = PickerGrid.plan(count, maxWidth.value, maxHeight.value, compact)
            planPerRow[0] = plan.perRow
            val viewport = (maxWidth - (PickerGrid.SIDE * 2).dp)
            val avatar = plan.avatar.dp
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer {
                translationY = (1f - enter.value) * 40f
            }) {
                if (lookStrip) {
                    LookStrip(style, look, onStep = { stepLook(it) })
                    Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
                }
                Text(
                    "Who's playing?", color = style.title, fontSize = if (compact) 22.sp else 30.sp, fontWeight = style.titleWeight,
                    letterSpacing = style.titleSpacing.sp, fontFamily = style.titleFont,
                    modifier = Modifier.graphicsLayer { alpha = 1f - exit.value }
                )
                Spacer(Modifier.height(if (compact) 16.dp else 30.dp))
                // One row; when the pictures can't all fit at a readable size it scrolls, keeping the one you're on in view.
                val rowScroll = androidx.compose.foundation.rememberScrollState()
                val density = LocalDensity.current
                // Room at both ends of a scrolling row, so the one you're on (drawn a little bigger) is never cut at the edge.
                val edge = plan.slot * 0.1f
                LaunchedEffect(cursor, plan) {
                    if (!plan.scroll) return@LaunchedEffect
                    val target = with(density) { (edge + (plan.slot + plan.gap) * cursor - (viewport.value - plan.slot) / 2f).dp.roundToPx() }
                    if (lite) rowScroll.scrollTo(target.coerceIn(0, rowScroll.maxValue)) else rowScroll.animateScrollTo(target.coerceIn(0, rowScroll.maxValue))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(plan.gap.dp), verticalAlignment = Alignment.Top,
                    modifier = Modifier.then(if (plan.scroll) Modifier.width(viewport).scrollFade(rowScroll).horizontalScroll(rowScroll, enabled = !leaving).padding(horizontal = edge.dp) else Modifier)
                        .padding(vertical = avatar * 0.22f)
                ) {
                    for (i in 0 until count) {
                        val p = profiles.getOrNull(i)
                        ProfileBubble(
                            p, style = style, on = i == cursor, size = avatar, slot = plan.slot.dp, compact = compact, lite = lite,
                            leaving = leaving, exit = exit.value,
                            model = p?.let { Profiles.pictureModel(context, it, raNames, steamAvatar) },
                            playing = p?.id == activeId,
                            ra = p?.raSlot?.let { raNames.getOrNull(it) },
                            onTap = { cursor = i; pick(i) },
                            onLong = { if (p != null && !lookStrip) { cursor = i; unlockThen(p) { editing = p } } }
                        )
                    }
                }
                Spacer(Modifier.height(if (compact) 14.dp else 28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer { alpha = 1f - exit.value }) {
                    PickerHint(style, "A", if (cursor < profiles.size) "Play" else "Add")
                    if (cursor < profiles.size && !lookStrip) PickerHint(style, "Y", "Edit")
                    PickerHint(style, "B", if (atStart) "Keep ${profiles.firstOrNull { it.id == activeId }?.name ?: "last"}" else "Close")
                    if (lookStrip) PickerHint(style, "L1 R1", "Look")
                }
            }
        }
    }
    pinFor?.let { p ->
        PinPad("${p.name}'s PIN", check = { if (demo) true else Profiles.pinOpens(p, it) }, onDone = {
            val go = afterPin; pinFor = null; afterPin = null; go?.invoke()
        }, onCancel = { pinFor = null; afterPin = null }, accent = style.accent(p.hue))
    }
    editing?.let { p ->
        ProfileEditDialog(p, raNames, steamAvatar, onDone = { editing = null }, onRemoved = {
            editing = null; cursor = cursor.coerceAtMost((Profiles.list.value.size - 1).coerceAtLeast(0))
        }, demo = demo)
    }
}

/** v0.6.67: Settings' live preview — the look's name between ◀ L1 and R1 ▶ (touch: tap the arrows). */
@Composable
private fun LookStrip(style: PickerStyle, look: Features.PickerLook, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, style.title.copy(alpha = 0.25f), androidx.compose.foundation.shape.RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text("◀ L1", color = style.hint, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.pointerInput(Unit) { detectTapGestures { onStep(-1) } })
        Text(look.label, color = style.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("R1 ▶", color = style.hint, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.pointerInput(Unit) { detectTapGestures { onStep(1) } })
    }
}

/** v0.6.67: a button hint — the button in a small round key, then what it does. */
@Composable
private fun PickerHint(style: PickerStyle, key: String, what: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.height(22.dp).widthIn(min = 22.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(style.hintKey)
                .padding(horizontal = if (key.length > 1) 7.dp else 0.dp),
            contentAlignment = Alignment.Center
        ) { Text(key, color = style.hint, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(6.dp))
        // (Whole name of the profile kept: it wraps, never "…".)
        Text(what, color = style.hint, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.widthIn(max = 220.dp))
    }
}

/**
 * One picture with its name, in the look's style; the one you're on lifts, grows, gets a ring and light in its own colour
 * (Profiles.Profile.hue). The profile playing now carries a "Playing" pill; a locked one a lock badge.
 */
@Composable
private fun ProfileBubble(
    p: Profiles.Profile?, style: PickerStyle, on: Boolean, size: Dp, slot: Dp, compact: Boolean, lite: Boolean, leaving: Boolean, exit: Float,
    model: Any?, playing: Boolean, ra: String?, onTap: () -> Unit, onLong: () -> Unit
) {
    val lift by animateFloatAsState(if (on) 1f else 0f, tween(if (lite) 120 else 260, easing = FastOutSlowInEasing), label = "bubbleLift")
    val hue = p?.hue ?: 210f
    val accent = style.accent(hue)
    val stamp = Profiles.pictureStamp.value
    val shape = style.avatarShape
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(slot).graphicsLayer {
            // Picked: it grows toward you while the others drop back and fade.
            val pickedOut = if (leaving && !on) exit else 0f
            val k = 1f + (if (lite) 0.08f else 0.14f) * lift + (if (leaving && on) 0.24f * exit else 0f)
            scaleX = k; scaleY = k
            translationY = -size.toPx() * 0.07f * lift + pickedOut * size.toPx() * 0.25f
            alpha = (style.dimOthers + (1f - style.dimOthers) * lift) * (1f - pickedOut)
        }.pointerInput(p?.id) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLong() }) }
    ) {
        Box(
            Modifier.then(if (style.card != null) Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
                .background(if (on) style.card.copy(alpha = style.card.alpha * 2.2f) else style.card)
                .border(1.dp, if (on) accent.copy(alpha = 0.55f) else (style.cardEdge ?: Color.Transparent), androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
                .padding(size * 0.09f) else Modifier.padding(size * 0.06f))
                .drawBehind {
                    if (style.glow > 0f && lift > 0.01f) {
                        val r = this.size.minDimension * 0.82f
                        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = style.glow * lift), Color.Transparent), radius = r), radius = r)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                // The ring in the profile's own colour (two outlines for Neon arcade).
                val ring = if (on) style.ringOn else 1.5.dp
                Box(
                    Modifier.size(size).clip(shape)
                        .background(if (p == null) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.03f))) else style.initialFill(hue))
                        .border(ring, if (on) accent else style.ringOff, shape)
                        .then(if (on && style.doubleRing) Modifier.padding(ring + 3.dp).border(1.dp, accent.copy(alpha = 0.6f), shape) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        p == null -> Icon(Icons.Filled.Add, "Add a profile", tint = style.nameOff, modifier = Modifier.size(size * 0.36f))
                        model != null -> AsyncImage(
                            coil.request.ImageRequest.Builder(LocalContext.current).data(model).memoryCacheKey("profile:${p.id}:$stamp:$model")
                                .diskCacheKey("profile:${p.id}:$stamp:$model").build(),
                            p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(ring).clip(shape)
                        )
                        else -> Text(p.name.take(1).uppercase(), color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold, fontFamily = style.titleFont)
                    }
                }
                if (p?.locked == true) Box(
                    Modifier.align(Alignment.BottomEnd).size((size * 0.26f).coerceAtLeast(20.dp)).clip(CircleShape).background(Color.Black.copy(alpha = 0.72f))
                        .border(1.dp, accent, CircleShape), contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Lock, "Locked", tint = Color.White, modifier = Modifier.size((size * 0.14f).coerceAtLeast(11.dp))) }
            }
        }
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
        // (Long names wrap onto a second line and shrink a little — never "…", never over the next name: the slot is the limit.)
        ProfileName(
            p?.name ?: "Add", color = if (on) style.nameOn else style.nameOff, maxSize = if (compact) 14f else 16f,
            bold = on, modifier = Modifier.fillMaxWidth()
        )
        if (ra != null && on) Text("RetroAchievements · $ra", color = style.nameOff.copy(alpha = 0.8f), fontSize = 10.sp, textAlign = TextAlign.Center)
        if (playing) Row(
            Modifier.padding(top = 5.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(accent.copy(alpha = if (style.look == Features.PickerLook.OLED) 0.25f else 0.2f))
                .border(1.dp, accent.copy(alpha = 0.7f), androidx.compose.foundation.shape.RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(5.dp))
            Text("Playing", color = style.nameOn, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A scrolling row fades out at a side that has more profiles beyond it, so a half-shown one reads as "more this way". */
private fun Modifier.scrollFade(state: androidx.compose.foundation.ScrollState): Modifier =
    graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val f = size.width * 0.08f
        val l = if (state.value > 0) 0f else 1f
        val r = if (state.value < state.maxValue) 0f else 1f
        drawRect(
            androidx.compose.ui.graphics.Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = l), f / size.width to Color.Black, 1f - f / size.width to Color.Black, 1f to Color.Black.copy(alpha = r)
            ), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
        )
    }

/**
 * A profile's name under its picture: whole words only. It breaks between words or after a hyphen ("Anastasia-" /
 * "Katharina"), never inside a word, and shrinks (down to 10 sp) until every word fits on a line and the name fits in
 * 2 lines; a name that still doesn't fit takes a third line. Measured bold, so the selected name never grows past its slot.
 */
@Composable
private fun ProfileName(name: String, color: Color, maxSize: Float, bold: Boolean, modifier: Modifier = Modifier) {
    val text = PickerGrid.breakable(name)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val measurer = androidx.compose.ui.text.rememberTextMeasurer(cacheSize = 8)
        val font = uiFontStyle()
        val w = constraints.maxWidth
        val fit = remember(text, w, maxSize, font) {
            fun st(s: Float) = font.copy(fontSize = s.sp, lineHeight = (s * 1.18f).sp, fontWeight = FontWeight.Bold)
            fun ok(s: Float, lines: Int): Boolean {
                if (w <= 0 || w == androidx.compose.ui.unit.Constraints.Infinity) return true
                val words = PickerGrid.words(name)
                if (words.any { measurer.measure(it, st(s), softWrap = false).size.width > w }) return false
                return measurer.measure(text, st(s), constraints = androidx.compose.ui.unit.Constraints(maxWidth = w)).lineCount <= lines
            }
            var s = maxSize
            while (s > 10f && !ok(s, 2)) s = maxOf(10f, s - 0.5f)
            s to (if (ok(s, 2)) 2 else 3)
        }
        Text(
            text, color = color, fontSize = fit.first.sp, lineHeight = (fit.first * 1.18f).sp, style = font,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center, maxLines = fit.second,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** A profile's name, picture and RetroAchievements account; remove (never the first one). */
@Composable
internal fun ProfileEditDialog(
    p0: Profiles.Profile, raNames: List<String?>, steamAvatar: String?, onDone: () -> Unit, onRemoved: () -> Unit,
    /** v0.6.67: a preview's sample profile — changes show here only, nothing is saved (QA, Settings' preview). */
    demo: Boolean = false
) {
    val context = LocalContext.current
    var p by remember(p0.id) { mutableStateOf(if (demo) p0 else Profiles.list.value.firstOrNull { it.id == p0.id } ?: p0) }
    var name by remember(p0.id) { mutableStateOf(p.name) }
    fun save(np: Profiles.Profile) { p = np; if (!demo) Profiles.update(context, np) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !demo && Profiles.setPicture(context, p.id, uri)) p = Profiles.list.value.firstOrNull { it.id == p.id } ?: p
    }
    ShelfDialog(icon = Icons.Filled.Person, title = "Profile", subtitle = "Its own favorites, Continue playing, play time and achievements account.", onDismiss = {
        if (name.isNotBlank() && name != p.name) save(p.copy(name = name.trim()))
        onDone()
    }) {
        ShelfTextField(name, { name = it.take(24) }, "Name", modifier = Modifier.fillMaxWidth(), onSubmit = {
            if (name.isNotBlank()) save(p.copy(name = name.trim()))
        })
        Spacer(Modifier.height(10.dp))
        // v0.6.67: the profile's own colour — its ring, its light and its "Playing" pill on Who's playing? (never the app's theme).
        SettingsHeading("Colour")
        ProfileColourRow(p.hue) { h -> save(p.copy(hue = h)) }
        Spacer(Modifier.height(10.dp))
        SettingsHeading("Picture")
        ChipFlow {
            Chip("Initial", accent = p.picture == Profiles.Picture.INITIAL) { save(p.copy(picture = Profiles.Picture.INITIAL)) }
            if (p.raSlot?.let { raNames.getOrNull(it) } != null)
                Chip("RetroAchievements", accent = p.picture == Profiles.Picture.RA) { save(p.copy(picture = Profiles.Picture.RA)) }
            if (steamAvatar != null) Chip("Steam", accent = p.picture == Profiles.Picture.STEAM) { save(p.copy(picture = Profiles.Picture.STEAM)) }
            Chip("Your own picture…", accent = p.picture == Profiles.Picture.OWN) { runCatching { pick.launch(arrayOf("image/*")) } }
        }
        Spacer(Modifier.height(10.dp))
        SettingsHeading("RetroAchievements")
        val mine = p.raSlot?.let { raNames.getOrNull(it) }
        val taken = Profiles.list.value.filter { it.id != p.id }.mapNotNull { it.raSlot }.toSet()
        ChipFlow {
            Chip("None", accent = mine == null) { save(p.copy(raSlot = null, picture = if (p.picture == Profiles.Picture.RA) Profiles.Picture.INITIAL else p.picture)) }
            raNames.forEachIndexed { slot, n ->
                if (n != null) Chip(n + if (slot in taken) " (another profile)" else "", accent = p.raSlot == slot) {
                    // One account, one profile: the other profile lets it go.
                    Profiles.list.value.filter { it.id != p.id && it.raSlot == slot }.forEach { Profiles.update(context, it.copy(raSlot = null)) }
                    save(p.copy(raSlot = slot))
                }
            }
        }
        MutedNote("A new account: pick this profile, then sign in under Settings › Accounts › RetroAchievements — it becomes this profile's.")
        // Update 334: a PIN to play or edit this profile.
        Spacer(Modifier.height(10.dp))
        SettingsHeading("PIN")
        var settingPin by remember { mutableStateOf(false) }
        ChipFlow {
            Chip(if (p.locked) "Change PIN…" else "Set a PIN…", accent = false) { settingPin = true }
            if (p.locked) Chip("Remove the PIN", accent = false) { if (demo) p = p.copy(pinHash = null) else { Profiles.setPin(context, p.id, null); p = Profiles.list.value.firstOrNull { it.id == p.id } ?: p } }
        }
        MutedNote(if (p.locked) "Locked: its PIN is asked before it plays or is edited." else "Optional: 4 digits asked before this profile plays or is edited.")
        if (settingPin) PinPad("A new PIN for ${p.name}", check = null, onDone = { pin ->
            if (demo) p = p.copy(pinHash = Profiles.hashPin(p.id, pin)) else { Profiles.setPin(context, p.id, pin); p = Profiles.list.value.firstOrNull { it.id == p.id } ?: p }; settingPin = false
        }, onCancel = { settingPin = false })
        if (p.id != 0) {
            Spacer(Modifier.height(10.dp))
            Chip("Remove this profile", accent = false) { if (!demo) Profiles.remove(context, p.id); onRemoved() }
            MutedNote("Its favorites, Continue playing and play time go with it; games, saves and settings stay.")
        }
    }
}

/**
 * v0.6.67: a profile's colour — twelve round swatches (PickerGrid.PRESETS) and the colour it has now when that is none of them
 * (colours saved before stay exactly as they were). ◀ ▶ move, A picks; touch taps.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
internal fun ProfileColourRow(hue: Float, onPick: (Float) -> Unit) {
    val choices = remember(hue) { (if (PickerGrid.PRESETS.any { kotlin.math.abs(it - hue) < 0.5f }) PickerGrid.PRESETS else listOf(hue) + PickerGrid.PRESETS) }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { h ->
            val on = kotlin.math.abs(h - hue) < 0.5f
            Box(
                Modifier.size(34.dp).shelfClickable(CircleShape) { onPick(h) }.clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color.hsv(h.mod(360f), 0.45f, 0.95f), Color.hsv(h.mod(360f), 0.6f, 0.6f))))
                    .border(if (on) 3.dp else 1.dp, if (on) Color.White else Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) { if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White)) }
        }
    }
}

/**
 * Update 332: the games coming in after "Who's playing?" — each game's art (GameCard, StyleArt, Case stacks' case) slides
 * into its place with a little depth, left to right and top to bottom across the screen (by where it sits), in the layout
 * you have. One clock for all ([clockMs], moved by [Run]); a piece reads it only while drawing, and only while it runs.
 * A layout whose games are drawn otherwise comes in as a whole ([rolloutLayer]).
 */
internal object Rollout {
    private var startedAt = 0L
    var active by mutableStateOf(false)
        private set
    val clockMs = mutableFloatStateOf(0f)
    /** A game's art was placed while it runs (else the whole layer comes in). */
    @Volatile var placedAny = false

    fun start() {
        if (!Features.rolloutOn.value) return
        placedAny = false; startedAt = System.nanoTime(); clockMs.floatValue = 0f; active = true
    }

    /** How long the line of games takes to start, end to end (ms). */
    fun spreadMs(): Float = Features.rolloutSpeed.value.stepMs * 11f

    /** Moves the clock while it runs; stops when the last piece is in. */
    @Composable
    fun Run() {
        if (!active) return
        LaunchedEffect(startedAt) {
            val sp = Features.rolloutSpeed.value
            val end = sp.durMs + spreadMs() * 1.5f + 150f
            while (true) {
                withFrameNanos { now -> clockMs.floatValue = (now - startedAt) / 1e6f }
                if (clockMs.floatValue > end) break
            }
            active = false
        }
    }
}

/** Update 332: a game's art taking part in the rollout (nothing at all when it isn't running). */
internal fun Modifier.rolloutItem(): Modifier = this.composed {
    if (!Rollout.active) return@composed Modifier
    val sp = Features.rolloutSpeed.value
    val density = LocalDensity.current
    val dist = with(density) { 56.dp.toPx() }
    // Where it sits on the screen (0–1 across, 0–1 down): its turn in the line. Unknown until placed: not drawn yet.
    val at = remember { floatArrayOf(-1f, 0f) }
    val screen = androidx.compose.ui.platform.LocalConfiguration.current.let { with(density) { it.screenWidthDp.dp.toPx() to it.screenHeightDp.dp.toPx() } }
    Modifier
        .onPlaced { c ->
            val p = c.positionInRoot()
            at[0] = (p.x / screen.first.coerceAtLeast(1f)).coerceIn(0f, 1f); at[1] = (p.y / screen.second.coerceAtLeast(1f)).coerceIn(0f, 1f)
            Rollout.placedAny = true
        }
        .graphicsLayer {
            if (at[0] < 0f) { alpha = 0f; return@graphicsLayer }
            val delay = (at[0] * 0.85f + at[1] * 0.15f) * Rollout.spreadMs()
            val t = ((Rollout.clockMs.floatValue - delay) / sp.durMs).coerceIn(0f, 1f)
            if (t >= 1f) return@graphicsLayer
            val e = 1f - (1f - t) * (1f - t) * (1f - t)
            alpha = e
            translationX = (1f - e) * dist
            translationY = (1f - e) * dist * 0.35f
            rotationY = (1f - e) * -28f
            val k = 0.9f + 0.1f * e
            scaleX = k; scaleY = k
            cameraDistance = 14f * this.density
        }
}

/** Update 332: the home layer coming in as a whole after "Who's playing?", for a layout without art pieces that roll. */
internal fun Modifier.rolloutLayer(): Modifier = this.composed {
    if (!Rollout.active) return@composed Modifier
    val sp = Features.rolloutSpeed.value
    val dist = with(LocalDensity.current) { 40.dp.toPx() }
    Modifier.graphicsLayer {
        if (Rollout.placedAny) return@graphicsLayer
        val t = (Rollout.clockMs.floatValue / (sp.durMs * 1.3f)).coerceIn(0f, 1f)
        if (t >= 1f) return@graphicsLayer
        val e = 1f - (1f - t) * (1f - t) * (1f - t)
        alpha = 0.15f + 0.85f * e
        translationY = (1f - e) * dist
        rotationX = (1f - e) * 6f
        val k = 0.96f + 0.04f * e
        scaleX = k; scaleY = k
        cameraDistance = 14f * density
    }
}

/**
 * Update 334: a profile's 4-digit PIN — entered ([check] given: true when it opens) or set ([check] null: typed twice, then
 * [onDone] with it). A round keypad on the selector's night blue: ◀ ▲ ▼ ▶ and A, the number keys, or touch; ⌫ takes the
 * last digit back, B cancels. A wrong PIN shakes the dots and clears them.
 */
@Composable
internal fun PinPad(title: String, check: ((String) -> Boolean)?, onDone: (String) -> Unit, onCancel: () -> Unit, accent: Color = Color(0xFF8FB8FF)) {
    var digits by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }
    var cursor by remember { mutableIntStateOf(4) }
    var wrong by remember { mutableIntStateOf(0) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(wrong) {
        if (wrong > 0) { shake.snapTo(1f); shake.animateTo(0f, tween(380)) }
    }
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
    fun submit() {
        if (digits.length < 4) return
        when {
            check != null -> if (check(digits)) onDone(digits) else { wrong++; digits = "" }
            first == null -> { first = digits; digits = "" }
            first == digits -> onDone(digits)
            else -> { wrong++; first = null; digits = "" }
        }
    }
    fun press(k: String) {
        when (k) {
            "⌫" -> digits = digits.dropLast(1)
            "OK" -> submit()
            else -> if (digits.length < 4) { digits += k; if (digits.length == 4) submit() }
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveDialogWindow()
        Box(
            Modifier.fillMaxSize().background(Color(0xFA030812))
                .focusRequester(focus).focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val n = e.nativeKeyEvent.keyCode - android.view.KeyEvent.KEYCODE_0
                    when {
                        n in 0..9 -> { press("$n"); true }
                        e.key == Key.Backspace || e.key == Key.Delete -> { press("⌫"); true }
                        e.key == Key.DirectionLeft -> { if (cursor % 3 > 0) cursor--; true }
                        e.key == Key.DirectionRight -> { if (cursor % 3 < 2) cursor++; true }
                        e.key == Key.DirectionUp -> { if (cursor >= 3) cursor -= 3; true }
                        e.key == Key.DirectionDown -> { if (cursor < 9) cursor += 3; true }
                        e.key == Key.ButtonA || e.key == Key.Enter || e.key == Key.DirectionCenter -> { press(keys[cursor]); true }
                        e.key == Key.ButtonX -> { press("⌫"); true }
                        e.key == Key.ButtonB || e.key == Key.Back || e.key == Key.Escape -> { onCancel(); true }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (check == null && first != null) "Enter it again" else title,
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.graphicsLayer { translationX = kotlin.math.sin(shake.value * 18f) * 14f * shake.value * density }
                ) {
                    for (i in 0 until 4) Box(
                        Modifier.size(16.dp).clip(CircleShape)
                            .background(if (i < digits.length) accent else Color.White.copy(alpha = 0.18f))
                            .border(1.dp, if (shake.value > 0.01f) Color(0xFFFF7A7A) else Color.White.copy(alpha = 0.5f), CircleShape)
                    )
                }
                Spacer(Modifier.height(18.dp))
                for (r in 0 until 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                        for (c in 0 until 3) {
                            val i = r * 3 + c
                            val on = i == cursor
                            Box(
                                Modifier.size(56.dp).clip(CircleShape)
                                    .background(if (on) accent.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.08f))
                                    .border(if (on) 2.dp else 1.dp, if (on) accent else Color.White.copy(alpha = 0.22f), CircleShape)
                                    .pointerInput(i) { detectTapGestures { cursor = i; press(keys[i]) } },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(keys[i], color = Color.White, fontSize = if (keys[i].length > 1) 15.sp else 22.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Ⓑ Cancel   Ⓧ Delete", color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp)
            }
        }
    }
}
