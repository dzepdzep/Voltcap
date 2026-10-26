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
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val lite = LocalLiteEffects.current
    val canAdd = profiles.size < Profiles.MAX
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

    fun pick(i: Int) {
        if (leaving) return
        if (i >= profiles.size) {
            // The Add circle: a new profile, straight into its editor.
            Profiles.add(context, "Player ${profiles.size + 1}")?.let { editing = it; cursor = Profiles.list.value.indexOfFirst { p -> p.id == it.id } }
            return
        }
        val id = profiles[i].id
        unlockThen(profiles[i]) {
            leaving = true
            scope.launch {
                exit.animateTo(1f, tween(if (lite) 120 else 430, easing = FastOutSlowInEasing))
                onPick(id)
            }
        }
    }

    Dialog(onDismissRequest = { if (!atStart) onSkip() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        ImmersiveDialogWindow()
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = (enter.value * (1f - exit.value * 0.9f)).coerceIn(0f, 1f) }
                .background(Brush.radialGradient(listOf(Color(0xFF16305E), Color(0xFF0A1734), Color(0xFF03060F)), radius = 1600f))
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (editing != null || pinFor != null || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft -> { cursor = (cursor - 1).coerceAtLeast(0); true }
                        Key.DirectionRight -> { cursor = (cursor + 1).coerceAtMost(count - 1); true }
                        Key.ButtonA, Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> { pick(cursor); true }
                        Key.ButtonY, Key.ButtonX -> { profiles.getOrNull(cursor)?.let { p -> unlockThen(p) { editing = p } }; true }
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
            if (!lite) Sparks(Modifier.fillMaxSize())
            // A soft floor of light under the row.
            Box(Modifier.fillMaxSize().drawBehind {
                drawOval(
                    Brush.radialGradient(listOf(Color(0x3363A8FF), Color.Transparent), center = Offset(size.width / 2, size.height * 0.62f), radius = size.width * 0.4f),
                    topLeft = Offset(size.width * 0.1f, size.height * 0.5f), size = androidx.compose.ui.geometry.Size(size.width * 0.8f, size.height * 0.24f)
                )
            })
            val compact = maxHeight < 360.dp || maxWidth < 520.dp
            val avatar = (if (compact) 84.dp else 118.dp).let { a -> minOf(a, (maxWidth - 48.dp) / (count.coerceAtLeast(1) * 1.45f)) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer {
                translationY = (1f - enter.value) * 40f
            }) {
                Text(
                    "Who's playing?", color = Color.White, fontSize = if (compact) 22.sp else 30.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.graphicsLayer { alpha = 1f - exit.value }
                )
                Spacer(Modifier.height(if (compact) 18.dp else 34.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(avatar * 0.42f), verticalAlignment = Alignment.Top) {
                    for (i in 0 until count) {
                        val p = profiles.getOrNull(i)
                        ProfileBubble(
                            p, on = i == cursor, size = avatar, leaving = leaving, exit = exit.value,
                            model = p?.let { Profiles.pictureModel(context, it, raNames, steamAvatar) },
                            playing = p?.id == activeId,
                            ra = p?.raSlot?.let { raNames.getOrNull(it) },
                            onTap = { cursor = i; pick(i) },
                            onLong = { if (p != null) { cursor = i; unlockThen(p) { editing = p } } }
                        )
                    }
                }
                Spacer(Modifier.height(if (compact) 18.dp else 30.dp))
                Text(
                    buildString {
                        append("Ⓐ Play"); if (cursor < profiles.size) append("   Ⓨ Edit")
                        append(if (atStart) "   Ⓑ Keep ${profiles.firstOrNull { it.id == activeId }?.name ?: "last"}" else "   Ⓑ Close")
                    },
                    color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp,
                    modifier = Modifier.graphicsLayer { alpha = 1f - exit.value }
                )
            }
        }
    }
    pinFor?.let { p ->
        PinPad("${p.name}'s PIN", check = { Profiles.pinOpens(p, it) }, onDone = {
            val go = afterPin; pinFor = null; afterPin = null; go?.invoke()
        }, onCancel = { pinFor = null; afterPin = null })
    }
    editing?.let { p ->
        ProfileEditDialog(p, raNames, steamAvatar, onDone = { editing = null }, onRemoved = {
            editing = null; cursor = cursor.coerceAtMost((Profiles.list.value.size - 1).coerceAtLeast(0))
        })
    }
}

/** One round picture with its name; the one you're on lifts, grows and glows in its colour. */
@Composable
private fun ProfileBubble(
    p: Profiles.Profile?, on: Boolean, size: Dp, leaving: Boolean, exit: Float, model: Any?, playing: Boolean, ra: String?,
    onTap: () -> Unit, onLong: () -> Unit
) {
    val lift by animateFloatAsState(if (on) 1f else 0f, tween(260, easing = FastOutSlowInEasing), label = "bubbleLift")
    val hue = p?.hue ?: 210f
    val glow = Color.hsv(hue, 0.55f, 1f)
    val stamp = Profiles.pictureStamp.value
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(size * 1.3f).graphicsLayer {
            val pickedOut = if (leaving && !on) exit else 0f
            val k = 1f + 0.16f * lift + (if (leaving && on) 0.22f * exit else 0f)
            scaleX = k; scaleY = k
            translationY = -size.toPx() * 0.08f * lift
            alpha = (0.62f + 0.38f * lift) * (1f - pickedOut)
        }.pointerInput(p?.id) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLong() }) }
    ) {
        Box(
            Modifier.size(size).drawBehind {
                if (lift > 0.01f) drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.55f * lift), Color.Transparent), radius = this.size.minDimension * 0.85f), radius = this.size.minDimension * 0.85f)
            },
            contentAlignment = Alignment.Center
        ) {
            val ring = if (on) 3.dp else 1.5.dp
            Box(
                Modifier.size(size).clip(CircleShape)
                    .background(if (p == null) Color.White.copy(alpha = 0.06f) else Color.hsv(hue, 0.5f, 0.42f))
                    .border(ring, if (on) Color.White else Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when {
                    p == null -> Icon(Icons.Filled.Add, "Add a profile", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(size * 0.36f))
                    model != null -> AsyncImage(
                        coil.request.ImageRequest.Builder(LocalContext.current).data(model).memoryCacheKey("profile:${p.id}:$stamp:$model")
                            .diskCacheKey("profile:${p.id}:$stamp:$model").build(),
                        p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                    else -> Text(p.name.take(1).uppercase(), color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            p?.name ?: "Add", color = Color.White.copy(alpha = if (on) 1f else 0.7f), fontSize = if (on) 16.sp else 14.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
        val sub = when {
            p == null -> null
            ra != null -> "RetroAchievements · $ra"
            else -> null
        }
        if (sub != null) Text(sub, color = Color.White.copy(alpha = 0.45f * (0.5f + 0.5f * lift)), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (p?.locked == true) Icon(Icons.Filled.Lock, "Locked", tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(top = 3.dp).size(12.dp))
        if (playing) Box(Modifier.padding(top = 4.dp).size(6.dp).clip(CircleShape).background(glow))
    }
}

/** Slow sparks drifting up through the dark (the PS-style user screen's), drawn on one canvas. */
@Composable
private fun Sparks(modifier: Modifier) {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { now -> t.floatValue = (now - start) / 1e9f }
    }
    val seeds = remember { List(46) { i -> floatArrayOf(((i * 7919) % 1000) / 1000f, ((i * 104729) % 1000) / 1000f, 0.4f + ((i * 31) % 10) / 16f, ((i * 53) % 100) / 100f) } }
    Canvas(modifier) {
        val time = t.floatValue
        for (s in seeds) {
            val speed = 0.012f + 0.02f * s[3]
            val y = (s[1] - time * speed).let { it - kotlin.math.floor(it) }
            val x = s[0] + 0.012f * kotlin.math.sin(time * 0.6f + s[3] * 6f)
            val a = (0.18f + 0.35f * s[3]) * kotlin.math.sin(y * Math.PI.toFloat()).coerceAtLeast(0f)
            drawCircle(Color(0xFFBFD8FF).copy(alpha = a), radius = s[2] * 2.2f, center = Offset(x * size.width, y * size.height))
        }
    }
}

/** A profile's name, picture and RetroAchievements account; remove (never the first one). */
@Composable
internal fun ProfileEditDialog(p0: Profiles.Profile, raNames: List<String?>, steamAvatar: String?, onDone: () -> Unit, onRemoved: () -> Unit) {
    val context = LocalContext.current
    var p by remember(p0.id) { mutableStateOf(Profiles.list.value.firstOrNull { it.id == p0.id } ?: p0) }
    var name by remember(p0.id) { mutableStateOf(p.name) }
    fun save(np: Profiles.Profile) { p = np; Profiles.update(context, np) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && Profiles.setPicture(context, p.id, uri)) p = Profiles.list.value.firstOrNull { it.id == p.id } ?: p
    }
    ShelfDialog(icon = Icons.Filled.Person, title = "Profile", subtitle = "Its own favorites, Continue playing, play time and achievements account.", onDismiss = {
        if (name.isNotBlank() && name != p.name) save(p.copy(name = name.trim()))
        onDone()
    }) {
        ShelfTextField(name, { name = it.take(24) }, "Name", modifier = Modifier.fillMaxWidth(), onSubmit = {
            if (name.isNotBlank()) save(p.copy(name = name.trim()))
        })
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
            if (p.locked) Chip("Remove the PIN", accent = false) { Profiles.setPin(context, p.id, null); p = Profiles.list.value.firstOrNull { it.id == p.id } ?: p }
        }
        MutedNote(if (p.locked) "Locked: its PIN is asked before it plays or is edited." else "Optional: 4 digits asked before this profile plays or is edited.")
        if (settingPin) PinPad("A new PIN for ${p.name}", check = null, onDone = { pin ->
            Profiles.setPin(context, p.id, pin); p = Profiles.list.value.firstOrNull { it.id == p.id } ?: p; settingPin = false
        }, onCancel = { settingPin = false })
        if (p.id != 0) {
            Spacer(Modifier.height(10.dp))
            Chip("Remove this profile", accent = false) { Profiles.remove(context, p.id); onRemoved() }
            MutedNote("Its favorites, Continue playing and play time go with it; games, saves and settings stay.")
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
internal fun PinPad(title: String, check: ((String) -> Boolean)?, onDone: (String) -> Unit, onCancel: () -> Unit) {
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
            Modifier.fillMaxSize().background(Color(0xE6030812))
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
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.graphicsLayer { translationX = kotlin.math.sin(shake.value * 18f) * 14f * shake.value * density }
                ) {
                    for (i in 0 until 4) Box(
                        Modifier.size(14.dp).clip(CircleShape)
                            .background(if (i < digits.length) Color.White else Color.White.copy(alpha = 0.18f))
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
                                Modifier.size(52.dp).clip(CircleShape)
                                    .background(if (on) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.07f))
                                    .border(if (on) 2.dp else 1.dp, if (on) Color.White else Color.White.copy(alpha = 0.18f), CircleShape)
                                    .pointerInput(i) { detectTapGestures { cursor = i; press(keys[i]) } },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(keys[i], color = Color.White, fontSize = if (keys[i].length > 1) 14.sp else 20.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Ⓑ Cancel   Ⓧ Delete", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
    }
}
