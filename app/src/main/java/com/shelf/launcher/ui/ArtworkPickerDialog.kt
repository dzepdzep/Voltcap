package com.shelf.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.ArtCandidates
import com.shelf.launcher.data.ArtOverride
import com.shelf.launcher.data.ArtSlot
import com.shelf.launcher.data.Game
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.launch

/**
 * Replace any of a game's four artwork slots — with a different alternative
 * from the scraper, or your own image from the device.
 *
 * Custom picks use OpenDocument (not GetContent) so the URI can be granted a
 * persistable read permission; otherwise the image would load once and then
 * break after a reboot.
 */
@Composable
fun ArtworkPickerDialog(
    game: Game,
    candidates: ArtCandidates?,
    isLoading: Boolean,
    currentOverride: ArtOverride?,
    /** What the scraper found (shown when there's no custom pick), so the
     * picture in use is highlighted either way. */
    scrapedArt: com.shelf.launcher.data.GameArt? = null,
    onSet: (ArtSlot, String?) -> Unit,
    onSearch: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
    metadata: com.shelf.launcher.data.GameMetadata? = null,
    metadataLoading: Boolean = false,
    metadataNote: String? = null,
    metadataSource: com.shelf.launcher.data.MetadataSource = com.shelf.launcher.data.MetadataSource.IGDB,
    igdbConnected: Boolean = false,
    screenScraperConnected: Boolean = false,
    rawgConnected: Boolean = false,
    onScrapeMetadata: (com.shelf.launcher.data.MetadataSource, String) -> Unit = { _, _ -> },
    /** Compare sources: each source's own result for this game. */
    detailCandidates: Map<com.shelf.launcher.data.MetadataSource, com.shelf.launcher.data.GameMetadata>? = null,
    detailCandidatesLoading: Boolean = false,
    onCompareSources: (String) -> Unit = {},
    onApplyDetailField: (com.shelf.launcher.data.DetailField, com.shelf.launcher.data.GameMetadata) -> Unit = { _, _ -> },
    onApplyBestOfEach: () -> Unit = {},
    /** This game's background upscaling: null = automatic, 1 = off, 2/4/6/8. */
    heroUpscale: Int? = null,
    onHeroUpscale: (Int?) -> Unit = {},
    /** Update 137: the game's real-media pictures (in use, your picks applied), the scans themselves, and picking one. */
    physical: List<Pair<String, String>> = emptyList(),
    physicalPicks: Map<String, String> = emptyMap(),
    onSetPhysical: (label: String, url: String?) -> Unit = { _, _ -> }
) {
    com.shelf.launcher.Breadcrumbs.mark("ArtworkPickerDialog")
    val context = LocalContext.current
    var pendingSlot by remember { mutableStateOf<ArtSlot?>(null) }
    var pendingPhysical by remember { mutableStateOf<String?>(null) }
    var searchText by remember { mutableStateOf(game.name) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Update 301: the full case scan whose cuts are being set (FullScanCutEditor).
    var cutting by remember { mutableStateOf<String?>(null) }
    // Update 305: a picture just added to a real-media slot — where it's from, and whether it fits (ArtProvenance, ArtFit).
    var provFor by remember { mutableStateOf<Triple<String, String, String?>?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val slot = pendingSlot
        val phys = pendingPhysical
        pendingSlot = null; pendingPhysical = null
        if (uri != null && (slot != null || phys != null)) {
            // Update 301: copied into Voltcap's storage on this device (UserArt) — it keeps working if the file moves. Only
            // if the copy fails is the file linked as before.
            scope.launch {
                val local = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.shelf.launcher.data.UserArt.importLocal(context, uri) }
                val address = local ?: run {
                    runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    uri.toString()
                }
                if (slot != null) onSet(slot, address) else if (phys != null) {
                    onSetPhysical(phys, address)
                    // (Update 305: its shape against the surface it's for — said now, not found out later — and where it's from.)
                    val warn = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { pictureSize(context, address) }
                        ?.let { (w, h) -> ArtFit.warning(phys, w, h, game) }
                    provFor = Triple(phys, address, warn)
                }
            }
        }
    }
    val pickCustom: (ArtSlot) -> Unit = { pendingSlot = it; pickImage.launch(arrayOf("image/*")) }

    ShelfDialog(
        icon = Icons.Filled.Image,
        title = "Artwork & details",
        subtitle = game.name,
        onDismiss = onDismiss,
        widthFraction = 0.92f,
        maxContentHeightFraction = 0.68f,
        footer = {
            Chip("Reset to scraped", accent = false, onClick = onClearAll)
            Chip("Done", onClick = onDismiss)
        }
    ) {
        // The scraper matches on the filename, which is often wrong for ROMs
        // with region tags or abbreviated names — so the search term stays editable.
        ShelfCard {
            SectionLabel("Search")
            ShelfTextField(
                searchText, { searchText = it }, "Search artwork and details for",
                leadingIcon = Icons.Filled.Search,
                onSubmit = { onSearch(searchText) }
            )
            Spacer(Modifier.height(10.dp))
            Chip(if (isLoading) "Searching…" else "Search again", accent = false, enabled = !isLoading) {
                onSearch(searchText)
            }
        }

        Spacer(Modifier.height(12.dp))
        DetailsCard(
            game = game,
            searchText = searchText,
            metadata = metadata,
            isLoading = metadataLoading,
            note = metadataNote,
            defaultSource = metadataSource,
            igdbConnected = igdbConnected,
            screenScraperConnected = screenScraperConnected,
            rawgConnected = rawgConnected,
            onScrape = onScrapeMetadata
        )
        if (igdbConnected || rawgConnected || screenScraperConnected) {
            Spacer(Modifier.height(12.dp))
            CompareSourcesCard(
                searchText = searchText,
                current = metadata,
                found = detailCandidates,
                isLoading = detailCandidatesLoading,
                onCompare = onCompareSources,
                onApply = onApplyDetailField,
                onApplyBest = onApplyBestOfEach
            )
        }

        // Artwork: all sources mixed, or just one of them.
        val sourceNames = candidates?.sources?.keys?.toList().orEmpty()
        var artFrom by remember { mutableStateOf<String?>(null) }
        val shown = artFrom?.let { candidates?.sources?.get(it) } ?: candidates
        if (sourceNames.size > 1) {
            Spacer(Modifier.height(12.dp))
            ShelfCard {
                SectionLabel("Artwork from")
                MutedNote("Show the pictures from every source, or just one.")
                Spacer(Modifier.height(8.dp))
                ChipFlow {
                    Chip("All sources", accent = artFrom == null) { artFrom = null }
                    sourceNames.forEach { name ->
                        val c = candidates?.sources?.get(name)
                        val count = c?.let { it.grids.size + it.wideGrids.size + it.heroes.size + it.logos.size } ?: 0
                        Chip("$name ($count)", accent = artFrom == name) { artFrom = name }
                    }
                }
            }
        }

        // "Animated only": just the moving covers, capsules and backgrounds (SteamGridDB's animated list,
        // plus GIF / animated WebP from anywhere). Logos are never animated, so they hide then.
        var animatedOnly by remember { mutableStateOf(false) }
        val isAnimated: (String) -> Boolean = { url -> candidates?.isAnimated(url) ?: com.shelf.launcher.data.AnimatedArt.looksAnimated(url) }
        val pick: (List<String>?) -> List<String> = { list -> list.orEmpty().let { l -> if (animatedOnly) l.filter(isAnimated) else l } }
        val animatedCount = shown?.let { c -> (c.grids + c.wideGrids + c.heroes).count(isAnimated) } ?: 0
        Spacer(Modifier.height(12.dp))
        ShelfCard {
            SectionLabel("Show")
            ChipFlow {
                Chip("All pictures", accent = !animatedOnly) { animatedOnly = false }
                Chip(if (isLoading) "▶ Animated only" else "▶ Animated only ($animatedCount)", accent = animatedOnly) { animatedOnly = true }
            }
            if (animatedOnly) {
                Spacer(Modifier.height(6.dp))
                MutedNote(
                    when {
                        isLoading -> "Looking for animated artwork…"
                        animatedCount == 0 -> "No animated artwork found for this game. SteamGridDB has the most (needs its key in Settings › Accounts); try another search name."
                        !com.shelf.launcher.data.AnimatedArt.enabled ->
                            "They play here. On the home screen they play once Animated artwork is on (Game look › Artwork), or for this game alone in Game settings › Look for this game."
                        else -> "Pick one to use it: it plays on the home screen, the hero carousel and the second screen."
                    }
                )
            }
        }

        // (Update 144: a hint shown on its own under the slot starts with a capital.)
        val specs = listOf(
            SlotSpec("Cover", "600×900", ArtSlot.COVER, pick(shown?.grids), currentOverride?.cover, scrapedArt?.gridUrl, 72.dp, 100.dp),
            SlotSpec("Wide capsule", "920×430", ArtSlot.WIDE, pick(shown?.wideGrids), currentOverride?.wide, scrapedArt?.wideGridUrl, 140.dp, 66.dp),
            SlotSpec("Background", "Wide background (hero)", ArtSlot.HERO, pick(shown?.heroes), currentOverride?.hero, scrapedArt?.heroUrl, 160.dp, 52.dp),
            SlotSpec("Logo", "Transparent logo", ArtSlot.LOGO, if (animatedOnly) emptyList() else shown?.logos.orEmpty(), currentOverride?.logo, scrapedArt?.logoUrl, 100.dp, 60.dp)
        ).filter { !animatedOnly || it.slot != ArtSlot.LOGO }
        // Change every slot in one go instead of picking four times.
        if (specs.any { it.urls.isNotEmpty() }) {
            Spacer(Modifier.height(12.dp))
            ShelfCard {
                SectionLabel("All artwork at once")
                MutedNote("Swap cover, wide capsule, background and logo together.")
                Spacer(Modifier.height(10.dp))
                ChipFlow {
                    Chip("Next set") {
                        specs.forEach { spec ->
                            if (spec.urls.isEmpty()) return@forEach
                            val at = spec.urls.indexOf(spec.inUse)
                            onSet(spec.slot, spec.urls[(at + 1).mod(spec.urls.size)])
                        }
                    }
                    Chip("Previous set", accent = false) {
                        specs.forEach { spec ->
                            if (spec.urls.isEmpty()) return@forEach
                            val at = spec.urls.indexOf(spec.inUse).let { if (it < 0) 0 else it }
                            onSet(spec.slot, spec.urls[(at - 1).mod(spec.urls.size)])
                        }
                    }
                    Chip("Top pick of each", accent = false) {
                        specs.forEach { spec -> spec.urls.firstOrNull()?.let { onSet(spec.slot, it) } }
                    }
                }
            }
        }
        specs.forEach { spec ->
            Spacer(Modifier.height(12.dp))
            ArtSlotSection(spec, isLoading, onSet, pickCustom, isAnimated, animatedOnly)
            if (spec.slot == ArtSlot.HERO) {
                Spacer(Modifier.height(12.dp))
                ShelfCard {
                    SectionLabel("Upscale background")
                    MutedNote(
                        "Makes a small, blurry background sharp. It keeps its shape and still fits the " +
                            "screen — it just gets more detail. Very big images are capped so they stay smooth."
                    )
                    Spacer(Modifier.height(8.dp))
                    ChipFlow {
                        Chip("Automatic", accent = heroUpscale == null) { onHeroUpscale(null) }
                        Chip("Off", accent = heroUpscale == 1) { onHeroUpscale(1) }
                        com.shelf.launcher.data.HeroUpscaleStore.FACTORS.forEach { f ->
                            Chip("${f}×", accent = heroUpscale == f) { onHeroUpscale(f) }
                        }
                    }
                }
            }
        }
        // Update 137: the box, disc, cartridge, label and spine too — any of the game's scans (or the cover / background),
        // or a picture of yours, for the looks that draw them.
        val pool = (physical.map { it.second } + listOfNotNull(currentOverride?.cover ?: scrapedArt?.gridUrl, currentOverride?.hero ?: scrapedArt?.heroUrl)).distinct()
        // Update 305: artwork from The Cover Project (or anywhere) — downloaded in your browser, then added here. Voltcap
        // doesn't fetch from the site itself: it has no API and no permission for automatic downloads was found.
        Spacer(Modifier.height(12.dp))
        ShelfCard {
            SectionLabel("Covers, wraps and labels from elsewhere")
            MutedNote("Download a cover, full wrap, label or box side in your browser — The Cover Project or another site — then add it with Upload… on the slot it's for. Voltcap asks where it's from and what it was made for, keeps the picture on this device only, and checks it fits this game's case or media (the model never changes: a picture made for another format is cut or fitted, never stretched).")
            Spacer(Modifier.height(8.dp))
            ChipFlow {
                Chip("Open The Cover Project", accent = false) {
                    runCatching {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.thecoverproject.net/"))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }
        com.shelf.launcher.data.PhysicalOverrides.SLOTS.forEach { (label, title) ->
            Spacer(Modifier.height(12.dp))
            val inUse = physical.firstOrNull { it.first == label }?.second
            val mine = physicalPicks[label]
            ShelfCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionLabel(title)
                        MutedNote(
                            when {
                                mine != null -> "Your pick (highlighted)"
                                inUse != null -> "The scan in use (highlighted)"
                                else -> "None found: pick one of the game's pictures, or upload one."
                            }
                        )
                    }
                    if (mine != null) {
                        Chip("Clear", accent = false, onClick = { onSetPhysical(label, null) })
                        Spacer(Modifier.width(8.dp))
                    }
                    Chip("Upload…", accent = false, onClick = { pendingPhysical = label; pickImage.launch(arrayOf("image/*")) })
                }
                // (Update 305: your picture's provenance and whether it fits this game's surface.)
                if (mine != null) {
                    val prov = com.shelf.launcher.data.ArtProvenance.of(mine)
                    val fitNote by androidx.compose.runtime.produceState<String?>(null, mine, label) {
                        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { pictureSize(context, mine) }
                            ?.let { (w, h) -> ArtFit.warning(label, w, h, game) }
                    }
                    prov?.summary?.takeIf { it.isNotBlank() }?.let { Spacer(Modifier.height(4.dp)); MutedNote("From: $it") }
                    fitNote?.let { Spacer(Modifier.height(4.dp)); MutedNote("⚠ $it") }
                    if (mine.startsWith("file:")) {
                        Spacer(Modifier.height(6.dp))
                        Chip(if (prov == null) "Where's it from?…" else "Edit where it's from…", accent = false) { provFor = Triple(label, mine, fitNote) }
                    }
                }
                // Update 301: a full case scan — where it's cut into back, spine and front can be set by hand.
                if (label == BoxTexture.LABEL) {
                    val scan = mine ?: inUse
                    if (scan != null && cutting != scan) {
                        Spacer(Modifier.height(8.dp))
                        Chip(if (com.shelf.launcher.data.FullScanCuts.of(scan) != null) "Adjust the cuts… (yours)" else "Adjust the cuts…", accent = false) { cutting = scan }
                    }
                    if (scan != null && cutting == scan) {
                        Spacer(Modifier.height(8.dp))
                        // (Your own scan: its front / back / spine addresses are made again with the new cut.)
                        FullScanCutEditor(scan, game.platform) { cutting = null; if (mine != null) onSetPhysical(label, mine) }
                    }
                    MutedNote("One picture with the back, spine and front side by side (as printed on the case's insert). Used for the case's front, back and spine unless you picked those yourself. Kept on this device only.")
                }
                Spacer(Modifier.height(10.dp))
                if (pool.isEmpty()) EmptyState("No scans for this game yet (ScreenScraper, Accounts).")
                else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pool) { url ->
                        val selected = url == inUse
                        androidx.compose.foundation.layout.Box(
                            Modifier
                                .size(if (label == "Spine") 30.dp else 72.dp, 100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(if (selected) 3.dp else 0.dp, if (selected) ShelfTheme.accent else Color.Transparent, RoundedCornerShape(8.dp))
                                .shelfClickable(RoundedCornerShape(8.dp)) { onSetPhysical(label, url) }
                        ) {
                            AsyncImage(
                                model = url, contentDescription = null,
                                contentScale = if (label == "Spine") ContentScale.Crop else ContentScale.Fit,
                                alpha = if (selected || inUse == null) 1f else 0.55f,
                                modifier = Modifier.matchParentSize()
                            )
                        }
                    }
                }
            }
        }
    }
    // (Update 305: where a picture just added is from, and whether it fits.)
    provFor?.let { (label, url, warn) ->
        ProvenanceDialog(url, warn, onRemove = { onSetPhysical(label, null) }) { provFor = null }
    }
}

/** Update 305: a picture's pixel size from its address (a local copy or a content:// link), without decoding it; null if unreadable. */
internal fun pictureSize(context: android.content.Context, address: String): Pair<Int, Int>? = runCatching {
    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(android.net.Uri.parse(address))?.use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
    if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
}.getOrNull()

/**
 * Update 305: where a picture you've added is from — its source (The Cover Project, your own scan…), whether it's a scan
 * of real packaging or a custom design, who made it, and what it was made for — plus whether it fits ([warning]).
 * Saved with the picture (ArtProvenance); nothing leaves the device.
 */
@Composable
internal fun ProvenanceDialog(url: String, warning: String?, onRemove: (() -> Unit)?, onDone: () -> Unit) {
    val now = remember(url) { com.shelf.launcher.data.ArtProvenance.of(url) ?: com.shelf.launcher.data.ArtProvenance.Info() }
    var source by remember(url) { mutableStateOf(now.source) }
    var kind by remember(url) { mutableStateOf(now.kind) }
    var credit by remember(url) { mutableStateOf(now.credit) }
    var madeFor by remember(url) { mutableStateOf(now.madeFor) }
    ShelfDialog(
        icon = Icons.Filled.Image,
        title = "Where's this picture from?",
        subtitle = "Kept with it on this device — never uploaded",
        onDismiss = onDone,
        footer = {
            if (onRemove != null) Chip("Remove it", accent = false, onClick = { onRemove(); onDone() })
            Chip("Skip", accent = false, onClick = onDone)
            Chip("Save", onClick = {
                com.shelf.launcher.data.ArtProvenance.set(url, com.shelf.launcher.data.ArtProvenance.Info(source.trim(), kind, credit.trim(), madeFor))
                onDone()
            })
        }
    ) {
        warning?.let { MutedNote("⚠ $it"); Spacer(Modifier.height(10.dp)) }
        SectionLabel("Source")
        ChipFlow { com.shelf.launcher.data.ArtProvenance.SOURCES.forEach { s -> Chip((if (source == s) "✓ " else "") + s, accent = source == s) { source = s } } }
        ShelfTextField(source, { source = it }, "Or type it (a site, a person, a book…)")
        Spacer(Modifier.height(10.dp))
        SectionLabel("What it is")
        ChipFlow { com.shelf.launcher.data.ArtProvenance.Kind.values().forEach { k -> Chip((if (kind == k) "✓ " else "") + k.label, accent = kind == k) { kind = k } } }
        Spacer(Modifier.height(10.dp))
        SectionLabel("Made for")
        ChipFlow { ArtFit.FORMATS.forEach { f -> Chip((if (madeFor == f) "✓ " else "") + f, accent = madeFor == f) { madeFor = f } } }
        Spacer(Modifier.height(10.dp))
        ShelfTextField(credit, { credit = it }, "Made by (credit, optional)")
        MutedNote("\"Scan of real packaging\" is what Box sides › Authentic only shows. A design made for another case keeps that case's look — Voltcap fits it onto this game's own model without stretching it.")
    }
}

private data class SlotSpec(
    val label: String,
    val sizeHint: String,
    val slot: ArtSlot,
    val urls: List<String>,
    val current: String?,
    val scraped: String?,
    val itemWidth: Dp,
    val itemHeight: Dp
) {
    /** The picture showing now: your pick, else what the scraper found. */
    val inUse: String? get() = current ?: scraped
}

@Composable
private fun ArtSlotSection(
    spec: SlotSpec,
    isLoading: Boolean,
    onSet: (ArtSlot, String?) -> Unit,
    onPickCustom: (ArtSlot) -> Unit,
    isAnimated: (String) -> Boolean = { com.shelf.launcher.data.AnimatedArt.looksAnimated(it) },
    animatedOnly: Boolean = false
) {
    com.shelf.launcher.Breadcrumbs.mark("ArtSlotSection")
    ShelfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel(spec.label)
                MutedNote(
                    when {
                        spec.current != null -> "Your pick (highlighted)"
                        spec.scraped != null -> "Scraped art in use (highlighted when it's in the list)"
                        else -> spec.sizeHint
                    }
                )
            }
            if (spec.current != null) {
                Chip("Clear", accent = false, onClick = { onSet(spec.slot, null) })
                Spacer(Modifier.width(8.dp))
            }
            Chip("Upload…", accent = false, onClick = { onPickCustom(spec.slot) })
        }
        Spacer(Modifier.height(10.dp))

        when {
            spec.urls.isEmpty() && isLoading -> EmptyState("Loading alternatives…")
            spec.urls.isEmpty() && animatedOnly -> EmptyState("No animated ${spec.label.lowercase()} for this game.")
            spec.urls.isEmpty() -> EmptyState("No alternatives from the scraper for this slot.")
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(spec.urls) { url ->
                    val selected = url == spec.inUse
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .size(spec.itemWidth, spec.itemHeight)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(
                                if (selected) 3.dp else 0.dp,
                                if (selected) ShelfTheme.accent else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .shelfClickable(RoundedCornerShape(8.dp)) { onSet(spec.slot, url) }
                    ) {
                        // Animated pictures (GIF / animated WebP) play right here in the picker, whatever
                        // the Animated artwork setting, and carry a small "Animated" tag.
                        val animated = isAnimated(url)
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        val request = androidx.compose.runtime.remember(url) {
                            coil.request.ImageRequest.Builder(ctx).data(url)
                                .setParameter(com.shelf.launcher.data.AnimatedArt.PARAM_ANIMATE, true, memoryCacheKey = "anim")
                                .build()
                        }
                        AsyncImage(
                            model = request,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            // The ones not in use are dimmed, so the chosen one stands out.
                            alpha = if (selected || spec.inUse == null) 1f else 0.55f,
                            modifier = Modifier.matchParentSize()
                        )
                        if (animated) {
                            Text(
                                "▶ Animated",
                                color = Color.White, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                                    .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                        if (selected) {
                            androidx.compose.foundation.layout.Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(18.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(ShelfTheme.accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✓", color = onAccentColor(), fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The game's details (summary, score, playtimes) and where they come from:
 * pick a source and look it up again with the search name above — handy when
 * the current source found nothing or matched the wrong game.
 */
@Composable
private fun DetailsCard(
    game: Game,
    searchText: String,
    metadata: com.shelf.launcher.data.GameMetadata?,
    isLoading: Boolean,
    note: String?,
    defaultSource: com.shelf.launcher.data.MetadataSource,
    igdbConnected: Boolean,
    screenScraperConnected: Boolean,
    rawgConnected: Boolean,
    onScrape: (com.shelf.launcher.data.MetadataSource, String) -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("DetailsCard")
    val sources = listOf(
        com.shelf.launcher.data.MetadataSource.IGDB to igdbConnected,
        com.shelf.launcher.data.MetadataSource.SCREENSCRAPER to
            (screenScraperConnected && game.sourceType == com.shelf.launcher.data.SourceType.ROM),
        com.shelf.launcher.data.MetadataSource.RAWG to rawgConnected,
        com.shelf.launcher.data.MetadataSource.COMBINED to (igdbConnected || rawgConnected || screenScraperConnected)
    )
    var source by remember {
        mutableStateOf(
            sources.firstOrNull { it.first == defaultSource && it.second }?.first
                ?: sources.firstOrNull { it.second }?.first
                ?: defaultSource
        )
    }
    ShelfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel("Details")
                MutedNote(
                    when {
                        metadata == null -> "Nothing saved yet for this game."
                        else -> listOfNotNull(
                            metadata.title,
                            metadata.releaseYear?.toString(),
                            metadata.developer,
                            metadata.criticScore?.let { "score $it" },
                            if (metadata.summary.isNullOrBlank()) null else "summary",
                            metadata.normallyHours?.let { "≈${it.toInt()} h to beat" }
                        ).joinToString("  ·  ")
                    }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Look up with", color = ShelfTheme.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            sources.forEach { (s, available) ->
                Chip(s.label, accent = source == s && available, enabled = available) { source = s }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Chip(
                if (isLoading) "Looking up…" else "Scrape details & art",
                enabled = !isLoading && sources.any { it.first == source && it.second }
            ) { onScrape(source, searchText) }
        }
        if (sources.none { it.second }) {
            Spacer(Modifier.height(8.dp))
            MutedNote("Connect IGDB, ScreenScraper or RAWG under Settings › Accounts to fetch details.")
        } else if (!screenScraperConnected || game.sourceType != com.shelf.launcher.data.SourceType.ROM) {
            Spacer(Modifier.height(8.dp))
            MutedNote(
                if (game.sourceType != com.shelf.launcher.data.SourceType.ROM) "ScreenScraper only covers ROMs."
                else "Connect ScreenScraper to use it here too."
            )
        }
        note?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = ShelfTheme.accent, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
    }
}

/**
 * Each source's own answer for this game, side by side, one row per detail:
 * pick which source a detail comes from (the one in use is highlighted), or
 * take the best of each in one go.
 */
@Composable
private fun CompareSourcesCard(
    searchText: String,
    current: com.shelf.launcher.data.GameMetadata?,
    found: Map<com.shelf.launcher.data.MetadataSource, com.shelf.launcher.data.GameMetadata>?,
    isLoading: Boolean,
    onCompare: (String) -> Unit,
    onApply: (com.shelf.launcher.data.DetailField, com.shelf.launcher.data.GameMetadata) -> Unit,
    onApplyBest: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("CompareSourcesCard")
    ShelfCard {
        SectionLabel("Compare sources")
        MutedNote(
            "Look the game up in every connected source and choose each detail yourself — for example " +
                "the time to beat from IGDB and the Metacritic score from RAWG."
        )
        Spacer(Modifier.height(10.dp))
        ChipFlow {
            Chip(
                when {
                    isLoading -> "Looking up…"
                    found == null -> "Compare sources"
                    else -> "Look up again"
                },
                accent = found == null,
                enabled = !isLoading
            ) { onCompare(searchText) }
            if (!found.isNullOrEmpty()) {
                Chip("Use the best of each", accent = true, enabled = !isLoading, onClick = onApplyBest)
            }
        }
        if (found != null && found.isEmpty() && !isLoading) {
            Spacer(Modifier.height(8.dp))
            MutedNote("No source found this game — edit the search name above and try again.")
        }
        if (!found.isNullOrEmpty()) {
            com.shelf.launcher.data.DetailField.values().forEach { field ->
                val options = found.mapNotNull { (source, meta) ->
                    detailValue(field, meta)?.let { Triple(source, meta, it) }
                }
                if (options.isNotEmpty()) {
                    val inUse = current?.let { detailValue(field, it) }
                    Spacer(Modifier.height(12.dp))
                    Text(field.label, color = ShelfTheme.textMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    ChipFlow {
                        options.forEach { (source, meta, value) ->
                            Chip(
                                "${source.label}: $value",
                                accent = value == inUse
                            ) { onApply(field, meta) }
                        }
                    }
                }
            }
        }
    }
}

/** A short, comparable text for one detail of [meta] (null when it lacks it). */
private fun detailValue(field: com.shelf.launcher.data.DetailField, meta: com.shelf.launcher.data.GameMetadata): String? =
    when (field) {
        com.shelf.launcher.data.DetailField.SUMMARY ->
            meta.summary?.takeIf { it.isNotBlank() }?.let { if (it.length > 60) it.take(57).trimEnd() + "…" else it }
        com.shelf.launcher.data.DetailField.INFO ->
            listOfNotNull(meta.releaseYear?.toString(), meta.developer, meta.genres.firstOrNull())
                .takeIf { it.isNotEmpty() }?.joinToString(" · ")
        com.shelf.launcher.data.DetailField.SCORE -> meta.criticScore?.toString()
        com.shelf.launcher.data.DetailField.METACRITIC -> meta.metacritic?.toString()
        com.shelf.launcher.data.DetailField.PLAYTIME ->
            listOfNotNull(meta.hastilyHours, meta.normallyHours, meta.completelyHours)
                .takeIf { it.isNotEmpty() }
                ?.let { hours -> (meta.normallyHours ?: hours.first()).let { "≈${it.roundToIntSafe()} h" } }
    }

private fun Double.roundToIntSafe(): Int = if (isNaN()) 0 else kotlin.math.round(this).toInt()
