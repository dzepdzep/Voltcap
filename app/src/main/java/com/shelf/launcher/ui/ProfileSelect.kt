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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
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
    lookStrip: Boolean = false,
    /**
     * Connected Ignition: drawn in the activity's own window as the intro's continuation (IgnitionStage) instead of a Dialog —
     * one window, one clock, no swap. [stage] carries the intro clock while it runs; [onStageSkip] jumps to the settled picker.
     */
    inStage: Boolean = false,
    stage: IgnitionReveal? = null,
    onStageSkip: () -> Unit = {},
    /**
     * Sprint 527 (owner: Confirm -> Home "looked frozen" for ~450 ms, then hitched): called the moment a choice is accepted, so the
     * home can be built under the start-up stage while the chosen picture's light runs; [onPick] follows as the stage begins
     * to fade (stage) / when the picker has gone (elsewhere).
     */
    onAccept: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val lite = LocalLiteEffects.current
    // Update 447 (D144): tell QA's picker.sh that "Who's playing?" is up (only with the test hooks file).
    val markCtx = LocalContext.current
    DisposableEffect(Unit) {
        TestHooks.markScreen(markCtx, "PICKER")
        onDispose { TestHooks.markScreen(markCtx, "HOME") }
    }
    val canAdd = profiles.size < Profiles.MAX && !lookStrip
    val count = profiles.size + if (canAdd) 1 else 0
    var cursor by remember { mutableIntStateOf(profiles.indexOfFirst { it.id == activeId }.coerceAtLeast(0)) }
    // (Sprint 526: profiles can change while the picker is up - a sync, a restore: the cursor stays on the row.)
    LaunchedEffect(count) { if (cursor > count - 1) cursor = profiles.indexOfFirst { it.id == activeId }.takeIf { it >= 0 } ?: (count - 1).coerceAtLeast(0) }
    var editing by remember { mutableStateOf<Profiles.Profile?>(null) }
    var leaving by remember { mutableStateOf(false) }
    // (Update 334: a locked profile asks its PIN before it plays or is edited — then [afterPin] goes ahead.)
    var pinFor by remember { mutableStateOf<Profiles.Profile?>(null) }
    var afterPin by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun unlockThen(p: Profiles.Profile, go: () -> Unit) { if (p.locked) { pinFor = p; afterPin = go } else go() }
    val scope = rememberCoroutineScope()
    val enter = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    LaunchedEffect(Unit) { if (inStage) enter.snapTo(1f) else enter.animateTo(1f, tween(if (lite) 180 else 520, easing = FastOutSlowInEasing)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(editing) { if (editing == null) { kotlinx.coroutines.delay(60); runCatching { focus.requestFocus() } } }
    // v0.6.67: the look (ProfileLooks.kt) — QA can show one for this session only (TestHooks.pickerLook), nothing saved.
    val look = TestHooks.pickerLook.value ?: Features.pickerLook.state.value
    val style = pickerStyle(look)
    val looks = Features.PickerLook.values()
    val planPerRow = remember { intArrayOf(1) }
    // Connected Ignition (Ignition look, rev 2): the cursor moves at once; the drawn focus glides to it from wherever it is drawn
    // (held / rapid D-pad retargets, never snaps or lags) and each press sends one short spark along the wire.
    val ignition = look == Features.PickerLook.IGNITION
    val wireOn = ignition || stage != null
    val focusPos = remember { Animatable(cursor.toFloat()) }
    val lastMove = remember { longArrayOf(-1000L) }
    val impulses = remember { androidx.compose.runtime.mutableStateListOf<WireImpulse>() }
    val centers = remember { androidx.compose.runtime.mutableStateListOf<androidx.compose.ui.geometry.Offset>() }
    LaunchedEffect(cursor) {
        val dist = kotlin.math.abs(cursor - focusPos.value)
        if (dist < 0.001f) return@LaunchedEffect
        val now = android.os.SystemClock.uptimeMillis()
        val dur = if (lite) 120 else IgnK.glideMs(dist, now - lastMove[0])
        lastMove[0] = now
        focusPos.animateTo(cursor.toFloat(), tween(dur, easing = androidx.compose.animation.core.CubicBezierEasing(0.33f, 1f, 0.68f, 1f)))
    }
    fun moveTo(n: Int) {
        if (n == cursor) return
        if (wireOn && !lite) impulses.add(WireImpulse(focusPos.value, n, android.os.SystemClock.uptimeMillis(), (IgnK.glideMs(kotlin.math.abs(n - focusPos.value), android.os.SystemClock.uptimeMillis() - lastMove[0]) + 60).coerceIn(160, 340)))
        cursor = n
    }
    val stageClock = stage?.clock
    fun stageT(): Float = stageClock?.value ?: Float.MAX_VALUE
    // (Sprint 527: read through derivedStateOf - reading the clock here recomposed the whole picker on every intro frame.)
    val stageSettled by remember(stage) { androidx.compose.runtime.derivedStateOf { stage == null || stage.clock.value >= stage.tm.settled } }
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
            onAccept(id)
            scope.launch {
                // (Sprint 527: on the start-up stage the hand-over starts at 70 % of the chosen picture's light - the stage fades
                // into the home built under it meanwhile - instead of after it, with a dim stage in between.)
                var handed = false
                exit.animateTo(1f, tween(if (lite) 120 else 460, easing = FastOutSlowInEasing)) {
                    if (stage != null && !handed && value >= 0.7f) { handed = true; onPick(id) }
                }
                if (!handed) onPick(id)
            }
        }
    }

    // Update 514 (owner, RP Classic: "Who's playing?" left the bottom of the screen uncovered, every time): a Dialog's window is
    // laid out inside the bars Android counts as shown, and on a Retroid whose own hide_nav_bar hides the bar Voltcap's windows
    // leave it shown (GestureHint.systemOwnsNav) — so the window stopped 48 px above the bottom (frame [0,0][1240,1032]) and the
    // home showed through there. Edge to edge like the game windows (decorFitsSystemWindows = false): the whole display.
    val body: @Composable () -> Unit = {
        val accentNow = profiles.getOrNull(cursor)?.let { style.accent(it.hue) } ?: style.accent(210f)
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = (enter.value * (1f - exit.value * 0.9f)).coerceIn(0f, 1f) }
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    // (Update 513: this window's keys never reach the activity, so they count as input here — the backdrop's
                    // settled drift comes back on the first press, as the home's effects do.)
                    if (e.type == KeyEventType.KeyDown) { HomeIdle.poke(); PickerCalm.poke() }
                    if (editing != null || pinFor != null || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    // (Connected Ignition: a press before the picker has settled jumps to the settled picker — never to the home.)
                    if (stage != null && stageT() < stage.tm.settled) { onStageSkip(); return@onPreviewKeyEvent true }
                    // (Sprint 526: once a choice is made the picker is leaving - later presses (a fast double A, a D-pad press
                    // right after A) no longer move the ring away from the one that was chosen.)
                    if (leaving) return@onPreviewKeyEvent true
                    val per = planPerRow[0]
                    when (e.key) {
                        Key.DirectionLeft -> { moveTo(PickerGrid.step(cursor, count, per, -1, 0)); true }
                        Key.DirectionRight -> { moveTo(PickerGrid.step(cursor, count, per, 1, 0)); true }
                        Key.DirectionUp -> { moveTo(PickerGrid.step(cursor, count, per, 0, -1)); true }
                        Key.DirectionDown -> { moveTo(PickerGrid.step(cursor, count, per, 0, 1)); true }
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
            // (Connected Ignition: at start-up the look's own backdrop rises in under the wire while the title arrives — a dissolve
            // on the intro's stage, never a swap; the intro itself is drawn by IgnitionStage behind this.)
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (stage == null) 1f else IgnK.eInOut(IgnK.ph(stageT(), stage.tm.titleFrom, stage.tm.settled)) }) {
                PickerBackdrop(look, accentNow, animate = !lite, hold = stage != null && !stageSettled)
            }
            val rootPos = remember { floatArrayOf(0f, 0f) }
            Box(Modifier.matchParentSize().onPlaced { c -> val q = c.positionInRoot(); rootPos[0] = q.x; rootPos[1] = q.y })
            val compact = maxHeight < 360.dp || maxWidth < 520.dp
            val plan = PickerGrid.plan(count, maxWidth.value, maxHeight.value, compact)
            planPerRow[0] = plan.perRow
            val viewport = (maxWidth - (PickerGrid.SIDE * 2).dp)
            val avatar = plan.avatar.dp
            while (centers.size < count) centers.add(androidx.compose.ui.geometry.Offset(Float.NaN, Float.NaN))
            while (centers.size > count) centers.removeAt(centers.size - 1)
            val density0 = LocalDensity.current
            if (wireOn) IgnitionWireLayer(
                WireInputs(
                    centers, List(count) { i -> style.accent(profiles.getOrNull(i)?.hue ?: 210f) },
                    rho = with(density0) { (avatar / 2 + 6.dp).toPx() }, rise = with(density0) { (avatar * 0.08f).toPx() },
                    cursor = cursor, focusPos = { focusPos.value }, exit = { exit.value }, impulses = impulses, keep = ignition, lite = lite
                ),
                stage, Modifier.fillMaxSize()
            )
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
                    modifier = Modifier.graphicsLayer {
                        val ta = if (stage == null) 1f else IgnK.eOut(IgnK.ph(stageT(), stage.tm.titleFrom, stage.tm.titleTo))
                        alpha = (1f - exit.value) * ta; translationY = (1f - ta) * 10f * density
                    }
                )
                Spacer(Modifier.height(if (compact) 16.dp else 30.dp))
                // One row; when the pictures can't all fit at a readable size it scrolls, keeping the one you're on in view.
                val rowScroll = androidx.compose.foundation.rememberScrollState()
                val density = LocalDensity.current
                // Room at both ends of a scrolling row, so the one you're on (drawn a little bigger) is never cut at the edge.
                val edge = plan.slot * 0.1f
                // (Sprint 527: the first placement - the start-up's, with the light already on its way - is at once; later moves glide.)
                val placed = remember { booleanArrayOf(false) }
                LaunchedEffect(cursor, plan) {
                    if (!plan.scroll) return@LaunchedEffect
                    val target = with(density) { (edge + (plan.slot + plan.gap) * cursor - (viewport.value - plan.slot) / 2f).dp.roundToPx() }
                    if (lite || !placed[0]) rowScroll.scrollTo(target.coerceIn(0, rowScroll.maxValue)) else rowScroll.animateScrollTo(target.coerceIn(0, rowScroll.maxValue))
                    placed[0] = true
                }
                // Sprint 527 (owner: the selected glow was cut into a rectangle once the row scrolls): the scrolling row no longer
                // draws through one offscreen layer the size of the row (which cut every glow at its edges). It spans the whole
                // width with the same resting place for every picture (padding SIDE + edge), has room above and below for the glow
                // (taken back from the layout, so nothing moves), and each picture fades by itself near the ends instead.
                val glowRoom = avatar * 0.8f
                val sidePx = with(density) { PickerGrid.SIDE.dp.toPx() }
                val bandW = with(density) { viewport.toPx() }
                val itemPitch = with(density) { (plan.slot + plan.gap).dp.toPx() }
                val itemFirst = with(density) { (PickerGrid.SIDE + edge + plan.slot / 2f).dp.toPx() }
                val slotPx = with(density) { plan.slot.dp.toPx() }
                fun edgeOf(i: Int): Float = if (!plan.scroll) 1f else PickerGrid.edgeFade(itemFirst + itemPitch * i - rowScroll.value, sidePx, sidePx + bandW, slotPx)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(plan.gap.dp), verticalAlignment = Alignment.Top,
                    modifier = Modifier.then(if (plan.scroll) Modifier.glowRoom(glowRoom).width(maxWidth).horizontalScroll(rowScroll, enabled = !leaving)
                        .padding(horizontal = (PickerGrid.SIDE + edge).dp).padding(vertical = glowRoom) else Modifier)
                        .padding(vertical = avatar * 0.22f)
                ) {
                    for (i in 0 until count) {
                        val p = profiles.getOrNull(i)
                        val report: (androidx.compose.ui.geometry.Offset) -> Unit = { c -> if (i < centers.size) centers[i] = androidx.compose.ui.geometry.Offset(c.x - rootPos[0], c.y - rootPos[1]) }
                        // When this profile appears as the start-up's trace passes it (immediately otherwise).
                        val appearOf: () -> Float = {
                            if (stage == null) 1f else {
                                val cs = centers; val f = cursor.coerceIn(0, (cs.size - 1).coerceAtLeast(0))
                                val rho = with(density) { (avatar / 2 + 6.dp).toPx() }
                                if (i >= cs.size || cs[i].x.isNaN() || cs[f].x.isNaN() || cs.first().x.isNaN() || cs.last().x.isNaN()) 0f else { // (Sprint 526: the row can shrink under a frame still drawing it)
                                    // (Sprint 527: the focused one emerges as the light reaches it; the others nearest first as it spreads.)
                                    val ends = WireGeo.ends(cs.first().x, cs.last().x, rho, with(density) { 30.dp.toPx() })
                                    val reach = WireGeo.reach(cs[f].x - rho, cs[f].x + rho, ends[0], ends[1], with(density) { maxWidth.toPx() })
                                    val at = WireGeo.appearAt(cs[i].x, cs[f].x, rho, rho, reach, stage.tm)
                                    IgnK.eOut(IgnK.ph(stageT(), at, at + 260f))
                                }
                            }
                        }
                        if (ignition || stage != null) IgnitionBubble(
                            p, style = style, on = i == cursor, size = avatar, slot = plan.slot.dp, compact = compact, lite = lite,
                            leaving = leaving, exitOf = { exit.value },
                            liftOf = { IgnK.liftAt(if (ignition) focusPos.value else cursor.toFloat(), i) * (if (stage == null) 1f else IgnK.eOut(IgnK.ph(stageT(), stage.tm.arrive, stage.tm.liftTo))) },
                            appearOf = appearOf, edgeOf = { edgeOf(i) }, onCenter = report,
                            model = p?.let { Profiles.pictureModel(context, it, raNames, steamAvatar) },
                            playing = p?.id == activeId, ra = p?.raSlot?.let { raNames.getOrNull(it) },
                            onTap = { if (stageSettled) { moveTo(i); pick(i) } else onStageSkip() },
                            onLong = { if (p != null && !lookStrip && stageSettled) { moveTo(i); unlockThen(p) { editing = p } } }
                        ) else ProfileBubble(
                            p, style = style, on = i == cursor, size = avatar, slot = plan.slot.dp, compact = compact, lite = lite,
                            leaving = leaving, exit = exit.value, edgeOf = { edgeOf(i) },
                            model = p?.let { Profiles.pictureModel(context, it, raNames, steamAvatar) },
                            playing = p?.id == activeId,
                            ra = p?.raSlot?.let { raNames.getOrNull(it) },
                            onTap = { cursor = i; pick(i) },
                            onLong = { if (p != null && !lookStrip) { cursor = i; unlockThen(p) { editing = p } } }
                        )
                    }
                }
                Spacer(Modifier.height(if (compact) 14.dp else 28.dp))
                // Sprint 527 (owner: at x1.3 "Keep <long name>" wrapped and broke the row): the B hint names the profile only while the
                // whole row fits on one line at the current text size; otherwise just "Keep" (the name is right there on its picture).
                val hintMeasurer = androidx.compose.ui.text.rememberTextMeasurer(cacheSize = 8)
                val keepName = profiles.firstOrNull { it.id == activeId }?.name ?: "last"
                val keepHint = if (!atStart) "Keep" else {
                    val hs = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    fun w(t: String) = hintMeasurer.measure(t, hs, softWrap = false).size.width
                    val keyPx = with(density) { (22.dp + 6.dp).toPx() }
                    val full = w("Keep $keepName")
                    val total = 3 * keyPx + with(density) { 36.dp.toPx() } + w("Play") + w("Edit") + full
                    if (PickerGrid.hintFits(full.toFloat(), total, with(density) { 220.dp.toPx() }, with(density) { viewport.toPx() })) "Keep $keepName" else "Keep"
                }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer { alpha = (1f - exit.value) * (if (stage == null) 1f else IgnK.ph(stageT(), stage.tm.hintsFrom, stage.tm.hintsTo)) }) {
                    PickerHint(style, "A", if (cursor < profiles.size) "Play" else "Add")
                    if (cursor < profiles.size && !lookStrip) PickerHint(style, "Y", "Edit")
                    PickerHint(style, "B", if (atStart) keepHint else "Close")
                    if (lookStrip) PickerHint(style, "L1 R1", "Look")
                }
            }
        }
    }
    // The start-up's continuation lives in the activity's window (IgnitionStage); everywhere else the full-screen Dialog as before.
    if (inStage) body() else Dialog(onDismissRequest = { if (!atStart) onSkip() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)) {
        ImmersiveDialogWindow()
        body()
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
        ) { InkGlyph(key, style.hint, 11.dp) } // (Batch 521: the letter centred by its ink)
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
    edgeOf: () -> Float, model: Any?, playing: Boolean, ra: String?, onTap: () -> Unit, onLong: () -> Unit
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
            alpha = (style.dimOthers + (1f - style.dimOthers) * lift) * (1f - pickedOut) * edgeOf()
            // (Sprint 527: alpha on each drawing, no offscreen layer the size of the column - that cut the glow into a box.)
            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
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
/**
 * Sprint 527: draw room around a node without taking it from the layout - the node is measured [room] taller (its content
 * padded by [room] above and below) and placed [room] higher, so its parent sees the old size and place. The scrolling row
 * uses it so the selected glow has room inside the row's clip.
 */
private fun Modifier.glowRoom(room: Dp): Modifier = layout { m, c ->
    val e = room.roundToPx()
    val pl = m.measure(c.copy(maxHeight = if (c.hasBoundedHeight) c.maxHeight + 2 * e else c.maxHeight))
    layout(pl.width, (pl.height - 2 * e).coerceAtLeast(0)) { pl.place(0, -e) }
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
        if (!armed) placedAny = false
        armed = false; startedAt = System.nanoTime(); clockMs.floatValue = 0f; active = true
    }

    /**
     * Sprint 527: the home is being built under the start-up stage before the hand-over - the games wait at the line's start
     * (hidden) until [start] at the hand-over, so the roll-out still plays where it can be seen.
     */
    private var armed = false
    fun arm() {
        if (!Features.rolloutOn.value) return
        placedAny = false; armed = true; startedAt = -1L; clockMs.floatValue = 0f; active = true
    }

    /** How long the line of games takes to start, end to end (ms). */
    fun spreadMs(): Float = Features.rolloutSpeed.value.stepMs * 11f

    /** Moves the clock while it runs; stops when the last piece is in. */
    @Composable
    fun Run() {
        if (!active) return
        LaunchedEffect(startedAt) {
            if (startedAt < 0L) return@LaunchedEffect // (armed: held at the start until the hand-over)
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
            // (Update 489, D285: turned text drawn flat — see flatTextFor3d.)
            flatTextFor3d()
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
        // (Update 489, D285: turned text drawn flat — see flatTextFor3d.)
        flatTextFor3d()
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
    // (Update 514: the PIN pad over "Who's playing?" covers the whole display too — see ProfileSelectScreen.)
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
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

/**
 * Connected Ignition (rev 2): one profile on the Ignition look (and on every look while the start-up continuation runs). Resting:
 * the picture shaded toward the stage (never faded — the wire must not show through it), a fine neutral edge. Focused ([liftOf]
 * 0..1, read only while drawing): lifted, a solid ring in the profile's own colour with a cream hairline inside, a soft halo, and
 * the name in a small capsule nameplate of that colour. "Playing" is a neutral pill so the colour belongs to the selection.
 * [onCenter]: the picture's centre at rest (before the lift), for the wire.
 */
@Composable
private fun IgnitionBubble(
    p: Profiles.Profile?, style: PickerStyle, on: Boolean, size: Dp, slot: Dp, compact: Boolean, lite: Boolean, leaving: Boolean,
    exitOf: () -> Float, liftOf: () -> Float, appearOf: () -> Float, edgeOf: () -> Float, onCenter: (androidx.compose.ui.geometry.Offset) -> Unit,
    model: Any?, playing: Boolean, ra: String?, onTap: () -> Unit, onLong: () -> Unit
) {
    val hue = p?.hue ?: 210f
    val accent = style.accent(hue)
    val cream = Color(0xFFFFF6D6)
    val stage = Color(0xFF08070B)
    val stamp = Profiles.pictureStamp.value
    val pad = size * 0.06f
    val density = LocalDensity.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(slot)
            .onGloballyPositioned { c -> val pos = c.positionInRoot(); with(density) { onCenter(androidx.compose.ui.geometry.Offset(pos.x + c.size.width / 2f, pos.y + (pad + size / 2).toPx())) } }
            .graphicsLayer {
                val l = liftOf(); val ap = appearOf(); val ex = exitOf()
                val out = if (leaving && !on) ex else 0f
                val k = (1f + 0.16f * l + (if (leaving && on) 0.22f * ex else 0f)) * (0.86f + 0.14f * ap)
                scaleX = k; scaleY = k
                translationY = -size.toPx() * 0.08f * l + out * size.toPx() * 0.25f
                alpha = (1f - out) * ap * edgeOf()
                // (Sprint 527: alpha on each drawing - an offscreen layer the size of the column cut the arriving glow into a box.)
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
            }
            .pointerInput(p?.id) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLong() }) }
    ) {
        Box(Modifier.padding(pad).drawBehind {
            val l = liftOf()
            if (l > 0.01f && style.glow > 0f) {
                val r = this.size.minDimension * 1.0f
                drawCircle(Brush.radialGradient(0.35f to accent.copy(alpha = style.glow * l), 1f to Color.Transparent, radius = r), radius = r, blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
            }
        }, contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(size).clip(CircleShape).background(if (p == null) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.03f))) else style.initialFill(hue))
                    .drawWithContent {
                        drawContent()
                        val l = liftOf()
                        drawRect(stage.copy(alpha = 0.34f * (1f - l)))
                        val fl = if (on && leaving) kotlin.math.sin(Math.PI.toFloat() * IgnK.ph(exitOf(), 0.4f, 0.9f)) else 0f
                        if (fl > 0f) drawRect(Brush.radialGradient(listOf(cream.copy(alpha = 0.55f * fl), accent.copy(alpha = 0.25f * fl))), blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    p == null -> Icon(Icons.Filled.Add, "Add a profile", tint = cream.copy(alpha = 0.8f), modifier = Modifier.size(size * 0.36f))
                    model != null -> AsyncImage(
                        coil.request.ImageRequest.Builder(LocalContext.current).data(model).memoryCacheKey("profile:${p.id}:$stamp:$model")
                            .diskCacheKey("profile:${p.id}:$stamp:$model").build(),
                        p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                    else -> Text(p.name.take(1).uppercase(), color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
                }
            }
            // The rings, drawn over the picture's edge.
            Box(Modifier.size(size).drawBehind {
                val l = liftOf()
                val r = this.size.minDimension / 2f
                drawCircle(cream.copy(alpha = 0.16f * (1f - l)), r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                if (l > 0.01f) {
                    drawCircle(accent.copy(alpha = l), r + 1.5.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(3.5.dp.toPx() * l))
                    drawCircle(cream.copy(alpha = 0.55f * l), r - 0.6.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                }
            })
            if (p?.locked == true) Box(
                Modifier.align(Alignment.BottomEnd).size((size * 0.26f).coerceAtLeast(20.dp)).clip(CircleShape).background(Color.Black.copy(alpha = 0.72f))
                    .border(1.dp, accent, CircleShape), contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Lock, "Locked", tint = Color.White, modifier = Modifier.size((size * 0.14f).coerceAtLeast(11.dp))) }
        }
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
        // The nameplate: a capsule of the profile's own colour behind the focused name (whole names, wrapping, never "…").
        Box(Modifier.drawBehind {
            val l = liftOf()
            if (l > 0.02f) {
                val cr = androidx.compose.ui.geometry.CornerRadius(minOf(this.size.height / 2f, 14.dp.toPx()))
                drawRoundRect(accent.copy(alpha = 0.16f * l), cornerRadius = cr)
                drawRoundRect(accent.copy(alpha = 0.75f * l), cornerRadius = cr, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
            }
        }.padding(horizontal = 11.dp, vertical = 3.dp)) {
            ProfileName(p?.name ?: "Add", color = if (on) style.nameOn else style.nameOff, maxSize = if (compact) 14f else 16f, bold = on)
        }
        if (ra != null && on) Text("RetroAchievements · $ra", color = style.nameOff.copy(alpha = 0.8f), fontSize = 10.sp, textAlign = TextAlign.Center)
        if (playing) Row(
            Modifier.padding(top = 6.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(cream.copy(alpha = 0.08f))
                .border(1.dp, cream.copy(alpha = 0.28f), androidx.compose.foundation.shape.RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(5.dp))
            Text("Playing", color = cream.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
