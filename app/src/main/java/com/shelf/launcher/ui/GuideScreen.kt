package com.shelf.launcher.ui

import android.content.Intent
import android.net.Uri
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.guide.GuideCandidate
import com.shelf.launcher.data.guide.GuidePosition
import com.shelf.launcher.data.guide.GuideSection
import com.shelf.launcher.data.guide.GuideVisibility
import com.shelf.launcher.data.guide.MissableReport
import com.shelf.launcher.data.guide.SectionVisibility
import com.shelf.launcher.data.guide.SpoilerLevel
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.launch

/** Everything the walkthrough screens show for one game (owned by the view model). */
data class GuideUiState(
    val gameKey: String,
    val candidates: List<GuideCandidate> = emptyList(),
    val chosenId: String? = null,
    val position: GuidePosition? = null,
    val spoiler: SpoilerLevel = SpoilerLevel.NONE,
    val missablesMode: Boolean = false,
    val missableReport: MissableReport? = null,
    val loading: Boolean = false,
    val locating: Boolean = false,
    val error: String? = null,
    val localFolder: String? = null
)

internal const val GuideEmptyText =
    "No guide found for this game. Add a folder with .md/.txt guides or check the RetroAchievements guide link."

/** Opens a guide link inside Voltcap (controller-friendly web view), not in the browser. */
internal fun openGuideInBrowser(context: android.content.Context, url: String, gameKey: String? = null, gameTitle: String? = null) {
    GuideWebActivity.open(context, url, gameKey, gameTitle)
}

/** One line saying how sure the place is; null when there is nothing to say. */
internal fun guideConfidenceLine(pos: GuidePosition): String {
    val best = pos.located?.best
    return when {
        pos.keptPrevious -> "Unsure — kept your last place"
        best != null -> "${(best.confidence * 100).toInt().coerceIn(0, 100)}% sure · ${best.reason}" +
            (if (best.fromCorrection) " (your correction)" else "") + (if (best.isRevisit) " · revisiting" else "")
        else -> "Your saved place"
    }
}

/**
 * The walkthrough, full screen. D-pad moves between the buttons, the section list and the
 * reader; L1/R1 step through sections, Up/Down (or the right stick) scroll the text, B closes.
 */
@Composable
internal fun GuideScreen(
    game: Game,
    state: GuideUiState?,
    onDismiss: () -> Unit,
    onChoose: (String?) -> Unit,
    onContinue: () -> Unit,
    onLocate: () -> Unit,
    onCorrect: (String) -> Unit,
    onSpoiler: (SpoilerLevel) -> Unit,
    onMissablesMode: (Boolean) -> Unit,
    onPickFolder: (Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onPickFolder(uri)
    }
    val pos = state?.position
    val guide = pos?.guide
    val spoiler = state?.spoiler ?: SpoilerLevel.NONE
    val curId = pos?.current?.id
    val farId = pos?.furthest?.id

    var viewId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(guide?.id, curId) { viewId = curId }
    var expanded by remember(guide?.id) { mutableStateOf(setOf<String>()) }
    var picking by remember { mutableStateOf(0) } // 0 none, 1 guides, 2 sections
    val scroll = rememberScrollState()
    val listState = rememberLazyListState()
    val readerFocus = remember { FocusRequester() }

    val visible = remember(guide, spoiler, curId, farId) {
        if (guide == null) emptyList() else GuideVisibility.visibleSections(spoiler, guide, curId, farId)
    }
    val viewIndex = visible.indexOfFirst { it.first.id == viewId }.let { if (it < 0) visible.indexOfFirst { p -> p.first.id == curId } else it }
    val viewing = visible.getOrNull(viewIndex)

    LaunchedEffect(viewing?.first?.id) {
        scroll.scrollTo(0)
        if (viewIndex >= 0) runCatching { listState.animateScrollToItem((viewIndex - 2).coerceAtLeast(0)) }
    }


    fun step(delta: Int) {
        val i = (viewIndex + delta).coerceIn(0, (visible.size - 1).coerceAtLeast(0))
        visible.getOrNull(i)?.let { viewId = it.first.id }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ImmersiveDialogWindow()
        // Right stick scrolls the reader. Set on the dialog's own view: the stick goes to the focused window
        // (this dialog), so a listener on the home screen's window never saw it.
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            dialogView.setOnGenericMotionListener { _, ev ->
                if (ev.source and InputDevice.SOURCE_JOYSTICK != 0 && ev.action == MotionEvent.ACTION_MOVE) {
                    val y = ev.getAxisValue(MotionEvent.AXIS_RZ)
                    if (kotlin.math.abs(y) > 0.25f) { scroll.dispatchRawDelta(y * 36f); true } else false
                } else false
            }
            onDispose { dialogView.setOnGenericMotionListener(null) }
        }
        // The reader takes the controller once a guide is showing (inside the dialog, so its window exists).
        LaunchedEffect(guide?.id) {
            if (guide != null) { kotlinx.coroutines.delay(50); runCatching { readerFocus.requestFocus() } }
        }
        Column(
            Modifier
                .fillMaxSize()
                .background(ShelfTheme.colors.background)
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.ButtonB, Key.Back -> { onDismiss(); true }
                        Key.ButtonL1 -> { step(-1); true }
                        Key.ButtonR1 -> { step(1); true }
                        else -> false
                    }
                }
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // ---- header ----
            Text(
                guide?.title ?: ("Guide · " + gameDisplayName(game.name)),
                color = ShelfTheme.colors.textPrimary, fontSize = 20.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(20.sp), maxFontSize = 20.sp), fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (guide != null) {
                Text(
                    listOfNotNull(guide.attribution.takeIf { it.isNotBlank() }, guide.sourceUrl).joinToString(" · "),
                    color = ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            ChipFlow {
                if (pos != null) {
                    Chip("Continue guide") { onContinue(); viewId = curId }
                    if (game.raGameId != null) Chip(if (state?.locating == true) "Locating…" else "Locate now", accent = false, enabled = state?.locating != true) { onLocate() }
                    Chip("This isn't where I am", accent = false) { picking = 2 }
                }
                if ((state?.candidates?.size ?: 0) > 1) Chip("Guides (${state?.candidates?.size})", accent = false) { picking = 1 }
                state?.candidates?.filter { it.kind == GuideCandidate.Kind.EXTERNAL_LINK }?.forEach { c ->
                    val url = c.sourceUrl ?: c.ref
                    Chip(if ((state.candidates.count { it.kind == GuideCandidate.Kind.EXTERNAL_LINK }) > 1) "Open “${c.title}”" else "Open guide", accent = false) { // Update 144: it opens in Voltcap's guide reader, not the browser.
                        openGuideInBrowser(context, url, game.stableKey, stripRegionTags(game.name))
                    }
                }
                guide?.sourceUrl?.takeIf { it.startsWith("http") }?.let { url ->
                    Chip("Source page", accent = false) { openGuideInBrowser(context, url, game.stableKey, stripRegionTags(game.name)) }
                }
                Chip(if (state?.localFolder != null) "Guides folder ✓" else "Add guides folder", accent = false) { folderPicker.launch(null) }
            }
            // Guide pages saved for offline (Y in the guide reader), read without a connection.
            var savedTick by remember { mutableStateOf(0) }
            val resumed = rememberIsResumed()
            // Pages you added to this game (X in the guide reader).
            val added = remember(game.stableKey, savedTick, resumed) { com.shelf.launcher.data.guide.GameGuideLinks.list(context, game.stableKey) }
            if (added.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Added by you", color = ShelfTheme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                ChipFlow {
                    added.forEach { l ->
                        Chip("🔖 " + l.title.take(36), accent = false) {
                            openGuideInBrowser(context, l.url, game.stableKey, stripRegionTags(game.name))
                        }
                    }
                    Chip("Remove added", accent = false) {
                        added.forEach { com.shelf.launcher.data.guide.GameGuideLinks.remove(context, game.stableKey, it.url) }
                        savedTick++
                    }
                }
            }
            val saved = remember(game.stableKey, savedTick, resumed) { com.shelf.launcher.data.guide.OfflineGuides.list(context, game.stableKey) }
            if (saved.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Saved for offline", color = ShelfTheme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                ChipFlow {
                    saved.forEach { sv ->
                        Chip("📥 " + sv.title.take(36), accent = false) {
                            GuideWebActivity.open(context, "file://" + sv.file, game.stableKey, stripRegionTags(game.name))
                        }
                    }
                    Chip("Remove saved", accent = false) {
                        saved.forEach { com.shelf.launcher.data.guide.OfflineGuides.remove(context, game.stableKey, it) }
                        savedTick++
                    }
                }
            } else if (state?.candidates?.any { it.kind == GuideCandidate.Kind.EXTERNAL_LINK } == true) {
                MutedNote("Tip: in a guide page, press Y to save it for reading offline, or X to add it to this game's guides.")
            }
            if (pos != null) {
                Spacer(Modifier.height(6.dp))
                ChipFlow {
                    SpoilerLevel.values().forEach { lvl ->
                        Chip(lvl.label, accent = lvl == spoiler) { onSpoiler(lvl) }
                    }
                    if (game.raGameId != null) {
                        Chip(if (state?.missablesMode == true) "Missables: on" else "Missables: off", accent = state?.missablesMode == true) {
                            onMissablesMode(state?.missablesMode != true)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // ---- body ----
            when {
                state == null || (state.loading && pos == null) -> MutedNote("Loading guide…")
                pos == null -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    state.error?.let { MutedNote(it); Spacer(Modifier.height(8.dp)) }
                    val texts = state.candidates.filter { it.kind == GuideCandidate.Kind.TEXT }
                    if (texts.isEmpty() && state.candidates.none { it.kind == GuideCandidate.Kind.EXTERNAL_LINK }) {
                        EmptyState(GuideEmptyText)
                    } else if (texts.isNotEmpty()) {
                        SectionLabel("Pick a guide")
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            texts.forEach { c -> GuideRow(c.title, c.subtitle) { onChoose(c.id) } }
                        }
                    } else {
                        MutedNote("This game only has a link to its guide. It opens in Voltcap's guide reader; add a folder with .md/.txt guides to read one here.")
                    }
                }
                else -> {
                    state.error?.let { MutedNote(it); Spacer(Modifier.height(4.dp)) }
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    // On small screens the section list takes a third of the width (it was a fixed 260 dp,
                    // which left the guide text a narrow strip on the lower screen of a dual-screen handheld).
                    val listW = minOf(260.dp, maxWidth * 0.34f)
                    Row(Modifier.fillMaxSize()) {
                        // Section list
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.width(listW).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            // Keyed on the section's own index: ids can repeat ("boss-2" twice) and two equal keys crash the list (update 91).
                            itemsIndexed(visible, key = { _, p -> p.first.order }) { _, (s, vis) ->
                                val isCur = s.id == curId
                                val isView = s.id == viewing?.first?.id
                                val shape = RoundedCornerShape(10.dp)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(shape)
                                        .background(if (isView) ShelfTheme.accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                                        .shelfClickable(shape) {
                                            viewId = s.id
                                            if (vis == SectionVisibility.COLLAPSED) expanded = expanded + s.id
                                        }
                                        .padding(start = (8 + 8 * (s.level - 1).coerceIn(0, 4)).dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        (if (isCur) "● " else if (vis == SectionVisibility.COLLAPSED) "▸ " else "") + s.title.ifBlank { "Introduction" },
                                        color = if (isCur) ShelfTheme.accent else ShelfTheme.colors.textPrimary,
                                        fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp),
                                        fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.alpha(if (vis == SectionVisibility.TITLE_ONLY) 0.6f else 1f)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        // Reader
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .onKeyEvent { e ->
                                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                                        when (e.key) {
                                            Key.DirectionDown, Key.PageDown ->
                                                if (scroll.value < scroll.maxValue) { scope.launch { scroll.animateScrollBy(260f) }; true } else false
                                            Key.DirectionUp, Key.PageUp ->
                                                if (scroll.value > 0) { scope.launch { scroll.animateScrollBy(-260f) }; true } else false
                                            else -> false
                                        }
                                    }
                                    .focusRequester(readerFocus)
                                    .shelfFocusable(RoundedCornerShape(14.dp))
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(ShelfTheme.colors.surface)
                            ) {
                                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(14.dp)) {
                                    if (viewing != null) {
                                        GuideReader(
                                            section = viewing.first,
                                            vis = viewing.second,
                                            isCurrent = viewing.first.id == curId,
                                            forceShown = viewing.first.id in expanded,
                                            pos = pos,
                                            state = state,
                                            onExpand = { expanded = expanded + viewing.first.id }
                                        )
                                    }
                                }
                                // Touch scroll helpers; the D-pad and stick do the same.
                                Column(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ScrollNub("▲") { scope.launch { scroll.animateScrollBy(-360f) } }
                                    ScrollNub("▼") { scope.launch { scroll.animateScrollBy(360f) } }
                                }
                            }
                            Text(
                                "L1 / R1 previous · next section     ▲▼ scroll     B close",
                                color = ShelfTheme.textMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    }
                }
            }
        }
    }

    if (picking == 1 && state != null) {
        GuidePickDialog(
            title = "Choose a guide",
            entries = state.candidates.map { Triple(it.id, it.title, it.subtitle) },
            currentId = state.chosenId,
            onDismiss = { picking = 0 }
        ) { id ->
            picking = 0
            val c = state.candidates.firstOrNull { it.id == id }
            if (c?.kind == GuideCandidate.Kind.EXTERNAL_LINK) openGuideInBrowser(context, c.sourceUrl ?: c.ref, game.stableKey, stripRegionTags(game.name)) else onChoose(id)
        }
    }
    if (picking == 2 && guide != null) {
        GuidePickDialog(
            title = "Where are you?",
            entries = guide.sections.map { Triple(it.id, it.title.ifBlank { "Introduction" }, it.path.dropLast(1).joinToString(" › ").ifBlank { null }) },
            currentId = curId,
            onDismiss = { picking = 0 }
        ) { id ->
            picking = 0
            onCorrect(id)
        }
    }
}

@Composable
private fun GuideRow(title: String, subtitle: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(ShelfTheme.colors.surfaceRaised).shelfClickable(shape) { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(title, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        subtitle?.let { Text(it, color = ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun ScrollNub(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f))
            .clickableNoFocus(onClick).padding(horizontal = 10.dp, vertical = 6.dp)
    ) { Text(glyph, color = Color.White, fontSize = 12.sp) }
}

/** Touch-only tap target: the D-pad scrolls with Up/Down instead, so it must not take focus. */
private fun Modifier.clickableNoFocus(onClick: () -> Unit): Modifier =
    pointerInput(Unit) { detectTapGestures { onClick() } }

/** The current section's text (or why it is hidden), with the place-finding banner and missables. */
@Composable
private fun GuideReader(
    section: GuideSection,
    vis: SectionVisibility,
    isCurrent: Boolean,
    forceShown: Boolean,
    pos: GuidePosition,
    state: GuideUiState,
    onExpand: () -> Unit
) {
    if (isCurrent) {
        val unsure = pos.keptPrevious
        Text(
            "📍 You are here  ·  " + guideConfidenceLine(pos),
            color = if (unsure) Color(0xFFE0A030) else ShelfTheme.accent,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
    }
    Text(section.title.ifBlank { "Introduction" }, color = ShelfTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    if (section.path.size > 1) {
        Text(section.path.dropLast(1).joinToString(" › "), color = ShelfTheme.textMuted, fontSize = 11.sp)
    }
    if (state.missablesMode && isCurrent) {
        Spacer(Modifier.height(8.dp))
        MissablesPanel(state.missableReport, state.spoiler)
    }
    Spacer(Modifier.height(10.dp))
    when {
        vis == SectionVisibility.FULL || forceShown -> Text(
            section.text.ifBlank { "(This section has no text.)" },
            color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, lineHeight = 21.sp
        )
        vis == SectionVisibility.COLLAPSED -> {
            MutedNote("This part is ahead of where you are, so it's folded away.")
            Spacer(Modifier.height(8.dp))
            Chip("Show anyway", accent = false) { onExpand() }
        }
        else -> MutedNote("Text hidden by your spoiler setting (you're past this section). Change the level above to read it.")
    }
}

@Composable
private fun MissablesPanel(report: MissableReport?, spoiler: SpoilerLevel) {
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Color.White.copy(alpha = 0.06f)).padding(10.dp)) {
        // if / else instead of an early `return@Column` (that unbalances Compose's groups).
        if (report == null) {
            MutedNote("No achievement info for this spot. Connect RetroAchievements and open the game's achievements once.")
        } else {
            MissablesBody(report, spoiler)
        }
    }
}

@Composable
private fun MissablesBody(report: MissableReport, spoiler: SpoilerLevel) {
    Column {
        val sectionsShown = spoiler != SpoilerLevel.STRICT
        @Composable
        fun list(label: String, hints: List<com.shelf.launcher.data.guide.AchievementHint>, withSection: Boolean) {
            if (hints.isNotEmpty()) {
                SectionLabel(label)
                hints.forEach { h ->
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (h.achievement.isMissable) { MissableIcon(); Spacer(Modifier.width(6.dp)) }
                        Text(
                            (h.achievement.title ?: "Achievement") + if (withSection && sectionsShown) "  ·  in ${h.section.title}" else "",
                            color = ShelfTheme.colors.textPrimary, fontSize = 12.sp
                        )
                    }
                    // A locked achievement's description can be a spoiler; STRICT hides it.
                    if (h.achievement.description != null && com.shelf.launcher.data.guide.GuideVisibility.achievementDescriptionVisible(spoiler, h.achievement.isUnlocked)) {
                        Text(h.achievement.description, color = ShelfTheme.textMuted, fontSize = 11.sp, modifier = Modifier.padding(start = if (h.achievement.isMissable) 21.dp else 0.dp))
                    }
                }
            }
        }
        list("Missables here", report.missablesCurrent, false)
        list("Missables coming up", report.missablesUpcoming, true)
        val missIds = (report.missablesCurrent + report.missablesUpcoming).map { it.achievement.id }.toSet()
        list("Locked achievements mentioned here", report.lockedCurrent.filter { it.achievement.id !in missIds }, false)
        list("Locked achievements coming up", report.lockedUpcoming.filter { it.achievement.id !in missIds }, true)
        if (report.missablesCurrent.isEmpty() && report.missablesUpcoming.isEmpty() && report.lockedCurrent.isEmpty() && report.lockedUpcoming.isEmpty()) {
            MutedNote("No missable or locked achievements are mentioned near here.")
        }
    }
}

/** A tall, scrolling pick list (guides or sections) that a controller can walk through. */
@Composable
private fun GuidePickDialog(
    title: String,
    entries: List<Triple<String, String, String?>>,
    currentId: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val start = entries.indexOfFirst { it.first == currentId }.coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (start - 2).coerceAtLeast(0))
    val sounds = LocalUiSounds.current
    Dialog(onDismissRequest = { sounds?.play(UiSound.BACK); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveDialogWindow()
        Column(
            Modifier
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(20.dp))
                .background(ShelfTheme.colors.surface)
                .onKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Back)) { onDismiss(); true } else false
                }
                .padding(16.dp)
        ) {
            Text(title, color = ShelfTheme.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(entries, key = { i, e -> e.first + "#" + i }) { _, (id, label, sub) ->
                    GuideRow((if (id == currentId) "● " else "") + label, sub) { onPick(id) }
                }
            }
        }
    }
}
