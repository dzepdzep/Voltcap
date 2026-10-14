package com.shelf.launcher.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shelf.launcher.AchievementBadgeMode
import com.shelf.launcher.ArtConnectionState
import com.shelf.launcher.data.ArtSource
import com.shelf.launcher.data.MetadataSource
import com.shelf.launcher.data.ScreensaverSettings
import com.shelf.launcher.data.ArtStorageSettings
import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.cardArtFor
import com.shelf.launcher.data.BackgroundStyle
import com.shelf.launcher.data.RaAward
import com.shelf.launcher.data.RaBadges
import com.shelf.launcher.data.RaCompletionEntry
import com.shelf.launcher.data.RaProfileStats
import com.shelf.launcher.data.RaRecentAchievement
import com.shelf.launcher.data.RaUserProfile
import com.shelf.launcher.data.UiDensity
import com.shelf.launcher.data.LibraryAppsSettings
import com.shelf.launcher.data.CategorySettings
import com.shelf.launcher.SpecialFilter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.shelf.launcher.RaConnectionState
import com.shelf.launcher.ui.theme.AppTheme
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.launch

private data class SettingsCategory(
    val label: String,
    val blurb: String,
    val icon: ImageVector
)

private val CATEGORIES = listOf(
    // Update 82: what used to be one Appearance page with 19 sub-pages, split by what it's about.
    SettingsCategory("Home", "The home layout and its presets, where the games, name and buttons sit, what's next to the row, the details window.", Icons.Filled.Home),
    SettingsCategory("Game look", "What every game is drawn as — covers, cases, cartridges, discs — its corners, effect, frame and artwork. The same in every layout.", Icons.Filled.Album),
    SettingsCategory("Theme", "Themes and colours, the background, the interface style, size and text, effects and lights.", Icons.Filled.Palette),
    // Update 137: the screensaver has its own place (it was a group inside Theme).
    SettingsCategory("Screensaver", "When it starts, what it shows with and without music, the second screen while it runs, fading to black, and the lights.", Icons.Filled.Bedtime),
    SettingsCategory("Categories", "Which categories and their order, the category name on the home screen, the category screen (Tiles, Slides, Wall, Grid) and each platform's own options.", Icons.Filled.Category),
    SettingsCategory("Achievements", "On the cards and pop-ups while playing, their look, and matching your games to RetroAchievements.", Icons.Filled.EmojiEvents),
    SettingsCategory("Audio", "Interface sounds, haptics, background music and video sound.", Icons.Filled.MusicNote),
    SettingsCategory("Controls", "Hotkeys, music buttons, the button legend and what each button does.", Icons.Filled.SportsEsports),
    SettingsCategory("Library", "ROM folders, installed apps, Steam games, shortcuts.", Icons.Filled.Folder),
    SettingsCategory("Accounts", "SteamGridDB, TheGamesDB, ScreenScraper, RetroAchievements, IGDB, RAWG and Discord.", Icons.Filled.Link),
    SettingsCategory("Emulators", "Which app runs each console.", Icons.Filled.VideogameAsset),
    SettingsCategory("Screens", "Second screen and TV: what goes on each screen when a game starts.", Icons.AutoMirrored.Filled.ScreenShare),
    SettingsCategory("Metadata", "Artwork source, scraping and saved data.", Icons.Filled.Refresh),
    SettingsCategory("Performance", "Power tiers, CPU and GPU limits, AutoTDP, fan control — for the whole device or one game.", Icons.Filled.Speed),
    SettingsCategory("Overlay", "On top of your games: performance readout (FPS, CPU, GPU, RAM, temperature) and the music player with lyrics.", Icons.Filled.Layers),
    SettingsCategory("Sync", "Google Drive: game saves, favourites and playtime across your devices.", Icons.Filled.CloudSync),
    SettingsCategory("Permissions", "Everything Voltcap asks Android for, in one list — allow them all in one go.", Icons.Filled.Security),
    SettingsCategory("Device", "Hardware, screens, battery, vendor profile, notifications and crash reports.", Icons.Filled.Memory),
    SettingsCategory("Diagnostics", "Checks everything Voltcap depends on and makes a report to share when something's wrong.", Icons.Filled.Speed),
    SettingsCategory("Backup", "Move settings, accounts and saved details to another device; first-start setup.", Icons.Filled.Backup),
    // Update 141: the version, the update checker and the extra art packs in one place.
    SettingsCategory("Updates & downloads", "Voltcap's version, checking GitHub for a new one and installing it, and the extra art packs (cartridges, spines, discs, 3D models).", Icons.Filled.SystemUpdate),
)

/**
 * Settings: a fixed sidebar of categories on the left, the selected one
 * filled with the accent colour, and that category's controls on the right
 * laid out as cards — icon, title/subtitle, and a trailing action or status.
 */
@Composable
fun SettingsDialog(
    currentTheme: AppTheme,
    badgeMode: AchievementBadgeMode,
    badgeNextToTitle: Boolean,
    steamGridDbState: ArtConnectionState,
    raState: RaConnectionState,
    raUsername: String?,
    igdbConnected: Boolean,
    tgdbConnected: Boolean,
    screenScraperConnected: Boolean,
    artSource: ArtSource,
    metadataSource: MetadataSource,
    scrapeDone: Int,
    scrapeTotal: Int,
    isMatchingAchievements: Boolean,
    onSelectTheme: (AppTheme) -> Unit,
    onSetBadgeMode: (AchievementBadgeMode) -> Unit,
    onToggleBadgePlacement: () -> Unit,
    screensaver: ScreensaverSettings,
    onScreensaverChange: (ScreensaverSettings) -> Unit,
    onPreviewScreensaver: () -> Unit,
    artStorage: ArtStorageSettings,
    artStorageUsedBytes: Long,
    artStorageMissing: Int,
    artSaveDone: Int,
    artSaveTotal: Int,
    onSaveAllArt: () -> Unit,
    onCancelArtSave: () -> Unit,
    onClearSavedArt: () -> Unit,
    onArtStorageChange: (ArtStorageSettings) -> Unit,
    onClearAchievementData: () -> Unit,
    appearance: AppearanceSettings,
    onAppearanceChange: (AppearanceSettings) -> Unit,
    hasSecondScreen: Boolean,
    raProfile: RaUserProfile?,
    raProfileStats: RaProfileStats,
    raCompletion: Collection<RaCompletionEntry>,
    raRecent: List<RaRecentAchievement>,
    onOpenProfile: () -> Unit,
    libraryApps: LibraryAppsSettings,
    onOpenAppsLibrary: () -> Unit,
    onAppsAlwaysUseIcons: (Boolean) -> Unit,
    categories: CategorySettings,
    onMoveCategory: (String, Int) -> Unit,
    onToggleCategoryHidden: (String) -> Unit,
    onResetCategories: () -> Unit,
    onSectionChange: (String) -> Unit = {},
    onOpenRomLibrary: () -> Unit,
    onOpenSteamGames: () -> Unit,
    onOpenShortcuts: () -> Unit,
    onOpenSteamGridDb: () -> Unit,
    onOpenRetroAchievements: () -> Unit,
    onOpenIgdb: () -> Unit,
    onOpenTgdb: () -> Unit,
    onOpenScreenScraper: () -> Unit,
    onSelectMetadataSource: (MetadataSource) -> Unit,
    onSelectArtSource: (ArtSource) -> Unit,
    onOpenEmulators: () -> Unit,
    onScrapeMissing: () -> Unit,
    onRescrapeAll: () -> Unit,
    onCancelScrape: () -> Unit,
    onAutoMatchAchievements: () -> Unit,
    onRehashAchievements: () -> Unit,
    metadataScrapeDone: Int = 0,
    metadataScrapeTotal: Int = 0,
    savedDetailsCount: Int = 0,
    onScrapeMetadata: () -> Unit = {},
    onCancelMetadataScrape: () -> Unit = {},
    onClearMetadata: () -> Unit = {},
    onRefreshAllMetadata: () -> Unit = {},
    onRunSetup: () -> Unit = {},
    /** Profile › Currently playing: open that game's missing achievements (RetroAchievements game ID). */
    onOpenRaGame: (Int) -> Unit = {},
    /** Settings › Stats / Profile: a week, month or year recap. */
    onOpenYearRecap: (com.shelf.launcher.data.YearRecap.Period) -> Unit = {},
    scrapeOptions: com.shelf.launcher.data.ScrapeOptions = com.shelf.launcher.data.ScrapeOptions(),
    onScrapeOptionsChange: (com.shelf.launcher.data.ScrapeOptions) -> Unit = {},
    rawgConnected: Boolean = false,
    onOpenRawg: () -> Unit = {},
    /** For Settings › Stats. */
    games: List<com.shelf.launcher.data.Game> = emptyList(),
    playtime: Map<String, Long> = emptyMap(),
    /** Settings › Library › Collections & status. */
    userCollections: Map<String, Set<String>> = emptyMap(),
    onRenameCollection: (String, String) -> Unit = { _, _ -> },
    onDeleteCollection: (String) -> Unit = {},
    playStatuses: Map<String, com.shelf.launcher.data.StatusEntry> = emptyMap(),
    /** Settings › Emulators › BIOS check. */
    libraryPlatforms: List<String> = emptyList(),
    romRoot: String = "",
    onDismiss: () -> Unit,
    /** Open straight into this section (e.g. "Profile" from the avatar). */
    initialCategory: String? = null,
    /** Settings › Screens. */
    screenLayoutsVersion: Int = 0,
    onScreenLayoutsChanged: () -> Unit = {}
) {
    com.shelf.launcher.Breadcrumbs.mark("SettingsDialog")
    // Old name for the Audio section, in case something still opens it by that.
    val wanted = when (initialCategory) {
        "Sounds" -> "Audio"
        "Appearance" -> "Home" // the page before update 82
        else -> initialCategory
    }
    var category by remember {
        mutableStateOf(
            CATEGORIES.firstOrNull { it.label == wanted }
                // Profile and Stats have their own screens now (avatar / Stats shortcut), not in Settings.
                ?: CATEGORIES.first { it.label == "Home" }
        )
    }
    // Settings search (the box in the header): while it has text, the results replace the page.
    var searchQuery by remember { mutableStateOf("") }
    // (Update 148: a search jump that finds nothing on the page gives up after a moment, so the groups close again.)
    LaunchedEffect(SettingsJump.pending) {
        if (SettingsJump.pending != null) { kotlinx.coroutines.delay(2_000); SettingsJump.pending = null }
    }
    LaunchedEffect(category) {
        onSectionChange(category.label)
        if (category.label == "Profile") onOpenProfile()
        // Each section opens where you left it last time (its sub-page, if one was open).
        currentSection = category.label
        openSubpage = subpageMemory[category.label]
        lastSubpage = openSubpage
    }

    val sounds = LocalUiSounds.current
    LaunchedEffect(Unit) { sounds?.play(UiSound.OPEN) }
    // A setting can close the window (e.g. "Open the library view now").
    val latestDismiss = androidx.compose.runtime.rememberUpdatedState(onDismiss)
    androidx.compose.runtime.DisposableEffect(Unit) {
        val close: () -> Unit = { latestDismiss.value() }
        SettingsCloser.close = close
        onDispose { if (SettingsCloser.close === close) SettingsCloser.close = null }
    }
    // Start with the current section focused: with nothing focused, the first press after opening
    // only moved focus into the menu, so every button seemed to need pressing twice.
    val openFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(50) // the dialog window needs a frame before it can take focus
        runCatching { openFocus.requestFocus() }
    }
    Dialog(
        // B / Back inside a sub-page goes back to the section's list first.
        onDismissRequest = {
            sounds?.play(UiSound.BACK)
            if (openSubpage != null) setSubpage(null) else onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ImmersiveDialogWindow()
        // Small screens (Retroid Pocket Classic and the like): the dialog fills
        // nearly the whole screen, the side list becomes a row of category pills
        // along the top, and paddings shrink — so every option stays reachable.
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
        val compact = maxWidth < 640.dp || maxHeight < 440.dp
        Box(
            Modifier
                .fillMaxWidth(if (compact) 0.98f else 0.94f)
                .fillMaxHeight(if (compact) 0.96f else 0.94f)
                .voltSurface(uiRounded(if (compact) 16.dp else 24.dp), base = ShelfTheme.colors.surface)
                .border(1.dp, Color.White.copy(alpha = 0.08f), uiRounded(if (compact) 16.dp else 24.dp))
        ) {
            Column(Modifier.fillMaxSize()) {
                // Header: icon badge + title + subtitle, close button at the end.
                // (Update 144: one slim bar — the description on one line, everything centred — so the settings themselves
                // get the room; it used to take a third of the window on the Odin.)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (compact) 14.dp else 20.dp, vertical = if (compact) 8.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(category.icon, size = if (compact) 32.dp else 38.dp)
                    Spacer(Modifier.width(if (compact) 10.dp else 14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            category.label,
                            color = ShelfTheme.colors.textPrimary,
                            fontSize = if (compact) 17.sp else 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!compact) {
                            Text(
                                category.blurb,
                                color = ShelfTheme.textMuted,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    if (!compact) {
                        ShelfTextField(
                            searchQuery, { searchQuery = it }, "Search settings",
                            modifier = Modifier.width(280.dp), leadingIcon = Icons.Filled.Search
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    CloseButton(onDismiss)
                }
                if (compact) {
                    ShelfTextField(
                        searchQuery, { searchQuery = it }, "Search settings",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), leadingIcon = Icons.Filled.Search
                    )
                }

                HeaderDivider()

                Row(Modifier.fillMaxSize()) {
                    if (!compact) {
                        // Sidebar.
                        Column(
                            Modifier
                                .width(220.dp)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            CATEGORIES.forEach { entry ->
                                Box(if (entry == category) Modifier.focusRequester(openFocus) else Modifier) {
                                    NavItem(entry, selected = entry == category) { category = entry }
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        }

                        if (VoltUi.on) VoltageLine(Modifier.fillMaxHeight(), vertical = true)
                        else Box(Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.08f)))
                    }

                    // Content pane.
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        if (compact) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CATEGORIES.forEach { entry ->
                                    Box(if (entry == category) Modifier.focusRequester(openFocus) else Modifier) {
                                        Chip(entry.label, accent = entry == category) { category = entry }
                                    }
                                }
                            }
                            ThinDivider()
                        }
                        // A fresh scroll position per category: sharing one kept
                        // the previous (long) page's position, so a new page
                        // opened scrolled down past its top.
                        val paneScroll = androidx.compose.runtime.key(category.label) { rememberScrollState() }
                        Column(
                            Modifier
                                .weight(1f)
                                // ▲ ▼ scroll text-only pages that have nothing to focus.
                                .dpadScroll(paneScroll)
                                .verticalScroll(paneScroll)
                                .padding(if (compact) 12.dp else 20.dp)
                        ) {
                            if (searchQuery.isNotBlank()) SettingsSearchResults(searchQuery) { hit ->
                                // Straight to it: its section, its sub-page, its group opened.
                                searchQuery = ""
                                subpageMemory[hit.section] = hit.subpage
                                hit.foldKey?.let { openFold(it) }
                                // (Update 148: and scrolled to the setting itself, lit for a moment.)
                                SettingsJump.pending = hit.label
                                val target = CATEGORIES.firstOrNull { it.label == hit.section }
                                if (target != null) {
                                    if (target == category) { currentSection = target.label; setSubpage(hit.subpage) }
                                    category = target
                                }
                            } else when (category.label) {
                                "Home" -> HomeContent(currentTheme, onSelectTheme, appearance, onAppearanceChange)
                                "Game look" -> GameLookContent(appearance, onAppearanceChange)
                                "Theme" -> ThemeContent(
                                    currentTheme, onSelectTheme, screensaver, onScreensaverChange, onPreviewScreensaver,
                                    appearance, onAppearanceChange, hasSecondScreen
                                )
                                "Screensaver" -> Column {
                                    ScreensaverCard(screensaver, onScreensaverChange, onPreviewScreensaver)
                                    Spacer(Modifier.height(8.dp))
                                    SettingsGrid {
                                        item { m ->
                                            SwitchRow(
                                                icon = Icons.Filled.Palette,
                                                title = "Lights off with the screensaver",
                                                subtitle = "When the screensaver fades to black, the controller lights go dark too.",
                                                checked = appearance.lightsOffWithScreensaver,
                                                onChange = { onAppearanceChange(appearance.copy(lightsOffWithScreensaver = it)) },
                                                modifier = m
                                            )
                                        }
                                    }
                                }
                                "Achievements" -> AchievementsContent(
                                    badgeMode, badgeNextToTitle, onSetBadgeMode, onToggleBadgePlacement,
                                    appearance, onAppearanceChange,
                                    isMatchingAchievements, onAutoMatchAchievements, onRehashAchievements
                                )
                                "Profile" -> Column {
                                    // The profile has nothing to select, so ▼ used to jump
                                    // straight to Close. This invisible stop takes the
                                    // D-pad and scrolls the page instead (see scrollStop).
                                    ScrollStop(paneScroll)
                                    ProfileContent(
                                        raState == RaConnectionState.ON, raProfile, raProfileStats, raCompletion, raRecent,
                                        onOpenGame = onOpenRaGame
                                    )
                                    if (raState == RaConnectionState.ON) RaWeeklyCard(onOpenRaGame)
                                    if (raState == RaConnectionState.ON) RaFriendsCard()
                                    Spacer(Modifier.height(12.dp))
                                    RecapsCard(onOpenYearRecap)
                                }
                                "Categories" -> CategoriesContent(
                                    appearance, onAppearanceChange, categories, onMoveCategory, onToggleCategoryHidden, onResetCategories,
                                    userCollections, onRenameCollection, onDeleteCollection
                                )
                                "Library" -> Column {
                                    // Sub-groups that fold open (the page used to be one long run of cards).
                                    FoldGroup("Your games", summary = "ROMs, Steam, apps, shortcuts", key = "lib_games", startOpen = true) {
                                    LibraryContent(
                                    onOpenRomLibrary, onOpenSteamGames, onOpenShortcuts,
                                    libraryApps, onOpenAppsLibrary, onAppsAlwaysUseIcons
                                )
                                    }
                                FoldGroup("Duplicates & regions", key = "lib_regions") {
                                RegionDuplicatesCard(appearance, onAppearanceChange)
                                }
                                FoldGroup("Running games & quick resume", key = "lib_running") {
                                QuickResumeRow(appearance, onAppearanceChange)
                                RunningGamesCard()
                                }
                                FoldGroup("Import from other frontends", summary = "ES-DE, Pegasus, Daijisho", key = "lib_import") {
                                FrontendImportCard(games, listOf(romRoot).filter { it.isNotBlank() } + games.mapNotNull { g -> g.romPath?.substringBeforeLast("/")?.substringBeforeLast("/") }.distinct().take(12))
                                }
                                FoldGroup("Screenshots", key = "lib_shots") {
                                ScreenshotSettings(appearance, onAppearanceChange)
                                }
                                FoldGroup("Save Vault", summary = "How it looks, which saves it shows", key = "lib_vault") {
                                SaveVaultCard()
                                }
                                FoldGroup("Sorting & category order", summary = appearance.sortMode.label, key = "lib_sort") {
                                MutedNote("Which categories there are, their order, collections and the one Voltcap starts in: Settings › Categories.")
                                Spacer(Modifier.height(12.dp))
                                ShelfCard {
                                    SectionLabel("Sort games by")
                                    MutedNote("Games without the information (no score yet, no achievements…) go last, A–Z. Recently played and Most played keep their own order. You can also sort each category on its own from the sort button above the games.")
                                    Spacer(Modifier.height(8.dp))
                                    ToggleChips(
                                        com.shelf.launcher.data.SortMode.values().toList(),
                                        setOf(appearance.sortMode),
                                        label = { it.label }
                                    ) { picked ->
                                        val mode = (picked - appearance.sortMode).firstOrNull() ?: appearance.sortMode
                                        onAppearanceChange(appearance.copy(sortMode = mode))
                                    }
                                }
                                }
                                }
                                "Accounts" -> Column {
                                    AccountsContent(
                                        steamGridDbState, raState, raUsername, tgdbConnected, screenScraperConnected,
                                        igdbConnected, onOpenSteamGridDb, onOpenRetroAchievements, onOpenTgdb,
                                        onOpenScreenScraper, onOpenIgdb, rawgConnected, onOpenRawg
                                    )
                                    FoldGroup("Discord", key = "acc_discord") { DiscordCard(appearance, onAppearanceChange) }
                                    FoldGroup("Spotify", key = "acc_spotify") { SpotifySettingsCard() }
                                    FoldGroup("Steam", key = "acc_steam") { SteamAccountCard() }
                                    FoldGroup("RomM", key = "acc_romm") { RommSettingsCard() }
                                }
                                "Emulators" -> Column {
                                    FoldGroup("Emulators", key = "emu_list", startOpen = true) { EmulatorsContent(onOpenEmulators) }
                                    FoldGroup("BIOS check", key = "emu_bios") { BiosCheckCard(libraryPlatforms, romRoot) }
                                }
                                "Sync" -> CloudSyncContent()
                                "Screens" -> Column {
                                    ScreensSettingsContent(
                                        appearance = appearance,
                                        onAppearanceChange = onAppearanceChange,
                                        games = games,
                                        version = screenLayoutsVersion,
                                        onChanged = onScreenLayoutsChanged
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    SecondScreenExtras(appearance, onAppearanceChange, hasSecondScreen)
                                    // Update 188.
                                    if (hasSecondScreen) {
                                        Spacer(Modifier.height(12.dp))
                                        FoldGroup("Dim the second screen", summary = "When nobody uses it, also during a game", key = "second_dim") { ShelfCard { SecondDimSettings() } }
                                    }
                                }
                                "Stats" -> Column {
                                    RecapsCard(onOpenYearRecap)
                                    Spacer(Modifier.height(12.dp))
                                    StatsContent(
                                        games, playtime, raCompletion,
                                        weeklyRecap = appearance.weeklyRecap,
                                        onWeeklyRecap = { onAppearanceChange(appearance.copy(weeklyRecap = it)) },
                                        statuses = if (appearance.playStatus) playStatuses else emptyMap()
                                    )
                                }
                                "Permissions" -> PermissionsSetup(showAll = true)
                                "Overlay" -> Column {
                                    PerfSettingsCard()
                                    Spacer(Modifier.height(12.dp))
                                    NotificationSwitch(VoltNote.OVERLAY)
                                }
                                "Performance" -> Column {
                                    TunerSettingsContent()
                                    if (com.shelf.launcher.device.tune.Tuner.supported) {
                                        Spacer(Modifier.height(12.dp))
                                        NotificationSwitch(VoltNote.POWER)
                                    }
                                }
                                "Device" -> Column {
                                    FoldGroup("This device", key = "dev_info", startOpen = true) { DeviceInfoContent(paneScroll) }
                                    // Update 187.
                                    FoldGroup("Battery saver", summary = "Voltcap rests at low battery and while you play", key = "dev_saver") { ShelfCard { BatterySaverSettings() } }
                                    FoldGroup("Battery watcher", summary = "How much battery Voltcap uses, and what to change", key = "dev_battery") { BatteryWatchCard() }
                                    FoldGroup("Works on this device", summary = "Which effects and features this handheld can run", key = "dev_compat") { DeviceCompatCard() }
                                    FoldGroup("Sleep mode", summary = "Wi-Fi / Bluetooth off while the screen is off", key = "dev_sleep") { SleepModeCard() }
                                    FoldGroup("Gesture hint", summary = "The white navigation pill", key = "dev_gesture") { GestureHintCard() }
                                    FoldGroup("Quick Settings tiles", summary = "Voltcap's tiles in Android's pull-down menu", key = "dev_tiles") { QsTilesCard() }
                                    FoldGroup("Notifications", summary = "Power tuner (TDP), overlays, volume mixer", key = "dev_notes") { NotificationSettings() }
                                    SwitchRow(
                                        Icons.Filled.MusicOff, "Pause music when closed",
                                        "Closing a dual-screen / clamshell handheld: Spotify, any music app and Voltcap's own music pause. Locking with the power button doesn't.",
                                        com.shelf.launcher.data.Features.pauseOnClose.state.value, { com.shelf.launcher.data.Features.pauseOnClose.value = it }
                                    )
                                    SwitchRow(
                                        Icons.Filled.MusicOff, "Also pause when locking",
                                        "The power button or the screen timing out pauses music too (off: music keeps playing with the screen off).",
                                        com.shelf.launcher.data.Features.pauseOnLock.state.value, { com.shelf.launcher.data.Features.pauseOnLock.value = it }
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    val restartCtx = androidx.compose.ui.platform.LocalContext.current
                                    ActionCard(
                                        Icons.Filled.RestartAlt, "Restart Voltcap",
                                        "Closes the app and opens it again, so every setting is read afresh.",
                                        { com.shelf.launcher.data.Backup.restart(restartCtx) }
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    SwitchRow(
                                        Icons.Filled.Info, "In-game Quick menu",
                                        "Achievements and saved Bluetooth headsets / controllers over a running game: a Quick Settings tile (\"Voltcap quick menu\") and a button in the in-game panel.",
                                        com.shelf.launcher.data.Features.quickMenuInGame.state.value, { com.shelf.launcher.data.Features.quickMenuInGame.value = it }
                                    )
                                }
                                "Diagnostics" -> Column {
                                    val diagCtx = androidx.compose.ui.platform.LocalContext.current
                                    val emuPlatforms = remember { com.shelf.launcher.data.EmulatorSettingsStore(diagCtx).loadAll().keys }
                                    DiagnosticsContent(games, emuPlatforms)
                                }
                                "Backup" -> BackupContent(onRunSetup)
                                "Updates & downloads" -> UpdatesContent()
                                "Audio" -> SoundsContent(appearance, currentTheme, onAppearanceChange)
                                "Controls" -> Column {
                                    ControlsContent(appearance, onAppearanceChange)
                                    FoldGroup("Phone remote", key = "ctl_remote") { WebRemoteCard() }
                                }
                                "Metadata" -> {
                                    MetadataContent(
                                        artSource, metadataSource, scrapeDone, scrapeTotal, isMatchingAchievements,
                                        onSelectArtSource, onSelectMetadataSource,
                                        onScrapeMissing, onRescrapeAll, onCancelScrape, onAutoMatchAchievements, onRehashAchievements,
                                        metadataScrapeDone, metadataScrapeTotal, savedDetailsCount,
                                        onScrapeMetadata, onCancelMetadataScrape, onClearMetadata,
                                        onRefreshAllMetadata, scrapeOptions, onScrapeOptionsChange, rawgConnected
                                    )
                                    CollapsibleSection("Saved on this device") {
                                    SavedArtCard(
                                        artStorage, artStorageUsedBytes, artStorageMissing, artSaveDone, artSaveTotal,
                                        onSaveAllArt, onCancelArtSave, onClearSavedArt, onArtStorageChange,
                                        onClearMetadata, onClearAchievementData
                                    )
                                    }
                                }
                            }
                        }
                        // (Update 144: no Close button floating under the settings any more — ✕ at the top and B close.)
                    }
                }
            }
        }
        }
    }
}

// ---- Category bodies ----

@Composable
private fun HomeContent(
    currentTheme: AppTheme,
    onSelectTheme: (AppTheme) -> Unit,
    appearance: AppearanceSettings,
    onAppearanceChange: (AppearanceSettings) -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("HomeContent")
    // Update 133: "Only for this layout" — what you change here can be kept for the layout on screen only.
    LayoutOnlyCard(appearance, onAppearanceChange)
    // Moving things by hand: one place to find it (it used to sit only under Music player and the home Options menu).
    CollapsibleSection("Move & resize (Edit Home)") {
        ShelfCard {
            MutedNote(
                "Edit Home lets you drag anything on the home screen and resize it by pulling its corner handle: the music player, " +
                    "the round shortcuts, the whole game header or each piece of it (title, continue line, stat boxes, Play & buttons, " +
                    "console badge), the category name, and the game info and news cards. Tap one to pick it. With a controller: D-pad moves, L1 / R1 pick the next one, " +
                    "L2 / R2 resize, X puts one back, Y resets everything, A or B is done. Settings closes while you edit."
            )
            Spacer(Modifier.height(8.dp))
            ChipFlow {
                Chip("Edit Home…") { HomeEditState.open() }
                Chip("Put everything back", accent = false) { HomeWidgets.resetAll() }
            }
            Spacer(Modifier.height(6.dp))
            MutedNote("Also on the home screen: Options menu › Edit Home. Moving the round shortcuts: Settings › Home › Round shortcuts.")
        }
    }
    CollapsibleSection("Layout & presets") {
        PresetsCard(appearance, currentTheme, onAppearanceChange, onSelectTheme)
    }
    CollapsibleSection("Layout options") {
        LayoutOptionsCard(appearance, onAppearanceChange)
    }
    // Everything about the round shortcut buttons in one place (they used to be spread over Layout options,
    // Move & resize and each style layout's options).
    CollapsibleSection("Round shortcuts") {
        RoundShortcutsContent(appearance, onAppearanceChange)
    }
    // Everything about the name line, in one place.
    CollapsibleSection("Name & logo") {
        NameAndCategoryContent(appearance, onAppearanceChange)
    }
    CollapsibleSection("Next to the row") {
        FoldGroup("Game header", summary = "Title, stat boxes, Play — parts, size, place", key = "home_header", startOpen = true) {
            ShelfCard { HomeHeaderSettings() }
        }
        Spacer(Modifier.height(10.dp))
        RowInfoContent(appearance, onAppearanceChange)
        Spacer(Modifier.height(10.dp))
        FoldGroup("Game details per view", summary = "Developer, genre, release date, publisher, description", key = "game_facts") {
            GameFactsSettings()
        }
    }
    CollapsibleSection("Extras on the home screen") {
        HomeExtrasCard(appearance, onAppearanceChange)
    }
    // Everything about the home screen's music player in one place (it used to be under Audio).
    CollapsibleSection("Music player") {
        MusicPlayerAppearance(appearance, onAppearanceChange)
        if (!currentTheme.motif.reactive) {
            Spacer(Modifier.height(8.dp))
            MutedNote("The music themes (Settings › Theme › Themes & colours) have their own player, equalizer and lyric placement: pick one to see their options.")
        }
    }
    CollapsibleSection("Details window") {
        ShelfCard {
            SectionLabel("What the details window shows")
            MutedNote("Pick what appears when you press A on a game. The game's options are the cog in its top-right corner.")
            Spacer(Modifier.height(8.dp))
            ToggleChips(
                com.shelf.launcher.data.DetailsSection.values().toList(),
                appearance.detailsSections,
                label = { it.label }
            ) { onAppearanceChange(appearance.copy(detailsSections = it)) }
        }
        Spacer(Modifier.height(12.dp))
        ShelfCard {
            SectionLabel("Background")
            MutedNote("What sits behind the details. If a game doesn't have it, colours from its art are draped over the window instead.")
            Spacer(Modifier.height(8.dp))
            ChipFlow {
                com.shelf.launcher.data.DetailsBackdrop.values().filter { it != com.shelf.launcher.data.DetailsBackdrop.BLURRED_HERO }.forEach { b ->
                    Chip(b.label, accent = appearance.detailsBackdrop == b) {
                        onAppearanceChange(appearance.copy(detailsBackdrop = b))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ShelfCard { GamePopupSettings() }
        Spacer(Modifier.height(12.dp))
        FoldGroup("Game details", summary = "Add missing info", key = "home_details") {
            SettingsGrid {
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.AutoAwesome,
                    title = "Offer to add missing info",
                    subtitle = "When a game's details window is missing something (video, score, time to beat, " +
                        "artwork…), an \"Add missing\" button there fetches it.",
                    checked = appearance.offerMissing,
                    onChange = { onAppearanceChange(appearance.copy(offerMissing = it)) },
                    modifier = m
                )
            }
            }
        }
    }
    CollapsibleSection("Animations") {
            // Update 137: moving between games, first and in one place (the wide place's change used to just appear).
            ShelfCard {
                SectionLabel("Moving between games")
                // Update 141: how fast the games slide along (rows and carousels).
                val glide = com.shelf.launcher.data.Features.rowGlide.state.value
                ChipFlow {
                    com.shelf.launcher.data.Features.RowGlide.values().forEach { v ->
                        Chip((if (glide == v) "✓ " else "") + "Slide: " + v.label, accent = glide == v) { com.shelf.launcher.data.Features.rowGlide.value = v }
                    }
                }
                MutedNote("How the games slide along when you move, in the rows and the carousels. Quick was the old one (so fast the next game seemed to just appear).")
                Spacer(Modifier.height(6.dp))
                MutedNote("How the next game comes into the wide place at the left of the row (wide capsules, the game you're on at the left).")
                val swap = com.shelf.launcher.data.Features.capsuleSwap.state.value
                ChipFlow {
                    com.shelf.launcher.data.Features.CapsuleSwap.values().forEach { v ->
                        Chip((if (swap == v) "✓ " else "") + v.label, accent = swap == v) { com.shelf.launcher.data.Features.capsuleSwap.value = v }
                    }
                }
                Spacer(Modifier.height(6.dp))
                MotionPrefs.observe()
                val lay = appearance.homeLayout
                val pop = (MotionPrefs.pop(lay) * 100).toInt()
                PercentSlider("How far the game you're on stands out (this layout)", pop, { MotionPrefs.setOverride(lay, "pop", it.toString()) }, max = 300, live = true)
                if (pop == 0) MutedNote("At 0 % the game you're on doesn't grow at all when you move onto it.")
            }
            Spacer(Modifier.height(8.dp))
            SettingsGrid {
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.AutoAwesome,
                    title = "Launch animation",
                    subtitle = "When a game starts, its cover rises, light runs round its edge and it flares — then straight into the game (under a second).",
                    checked = appearance.launchAnimation,
                    onChange = { onAppearanceChange(appearance.copy(launchAnimation = it)) },
                    modifier = m
                )
            }
            // Update 151: the case opens, the cartridge or disc is thrown in (LaunchThrow.kt).
            item { m ->
                Box(m) { ShelfCard {
                    val F = com.shelf.launcher.data.Features
                    RowChoice("Starting a game", com.shelf.launcher.data.Features.LaunchThrow.values(), F.launchThrow.state.value, { it.label }) { F.launchThrow.value = it }
                    MutedNote("With cases, cartridges and discs: the case opens and its disc flies out, the cartridge spins down into a slot under the screen, the disc drops into a tray. Under a second. Other looks keep the launch animation above.")
                } }
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.AutoAwesome,
                    title = "New game arrival",
                    subtitle = "When a scan finds a new game, its cover drops into a glowing capsule at the top of the home screen for a few seconds (several at once: they stack).",
                    checked = appearance.arrivalAnim,
                    onChange = { onAppearanceChange(appearance.copy(arrivalAnim = it)) },
                    modifier = m
                )
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.AutoAwesome,
                    title = "Details window grows out of the game",
                    subtitle = "A opens the details window from the card you're on, springing up to full size (off: it just fades in).",
                    checked = appearance.popupZoom,
                    onChange = { onAppearanceChange(appearance.copy(popupZoom = it)) },
                    modifier = m
                )
            }
            item { m ->
                Box(m) { ShelfCard {
                    RowChoice("Startup intro", com.shelf.launcher.data.IntroStyle.values(), appearance.introStyle, { it.label }) {
                        onAppearanceChange(appearance.copy(introStyle = it))
                    }
                    MutedNote("The Voltcap logo drawn in light, with its own sound, when the app starts. Full is about 2.6 seconds, Short under one. Any button skips it.")
                    var previewIntro by remember { mutableStateOf(false) }
                    Spacer(Modifier.height(6.dp))
                    Chip("Preview", accent = false) { previewIntro = true }
                    if (previewIntro) {
                        IntroPreviewDialog(
                            style = appearance.introStyle.takeIf { it != com.shelf.launcher.data.IntroStyle.OFF }
                                ?: com.shelf.launcher.data.IntroStyle.FULL,
                            onDone = { previewIntro = false }
                        )
                    }
                } }
            }
            item { m -> Box(m) { ShelfCard { CapsuleRevealChoice() } } }
            item { m -> Box(m) { ShelfCard { MediaMotionChoice() } } }
            // Update 109 / 112: how things come in — everywhere (and each layout its own, below), each with a preview.
            item { m ->
                Box(m) { ShelfCard {
                    val F = com.shelf.launcher.data.Features
                    val cur = if (!F.animPageSlide.state.value) com.shelf.launcher.data.Features.PageAnim.NONE else F.pageAnim.state.value
                    RowChoice("Pages and categories (everywhere)", com.shelf.launcher.data.Features.PageAnim.values(), cur, { it.label }) { F.animPageSlide.value = true; F.pageAnim.value = it }
                    MutedNote("A new page or category: it slides in from the side you went to, fades over, or just changes.")
                    Spacer(Modifier.height(6.dp))
                    PageSlidePreview(cur)
                } }
            }
            item { m ->
                Box(m) { ShelfCard {
                    val F = com.shelf.launcher.data.Features
                    val cur = if (!F.animRiseIn.state.value) com.shelf.launcher.data.Features.InfoAnim.NONE else F.infoAnim.state.value
                    RowChoice("The game's info (everywhere)", com.shelf.launcher.data.Features.InfoAnim.values(), cur, { it.label }) { F.animRiseIn.value = true; F.infoAnim.value = it }
                    MutedNote("When you move to another game: its name, info and panels rise softly into place, fade in, or just appear.")
                    Spacer(Modifier.height(6.dp))
                    RiseInPreview(cur)
                } }
            }
            item { m ->
                Box(m) { ShelfCard {
                    val F = com.shelf.launcher.data.Features
                    PercentSlider("How far the game you're on stands out", F.focusPop.state.value, { F.focusPop.value = (it / 10) * 10 }, min = 0, max = 300, valueText = { "$it %" })
                    MutedNote("100 % is each layout's own lift. Higher: it grows further out of the row (wide capsules too, evenly from their middle); 0: it stays level with the others.")
                    Spacer(Modifier.height(6.dp))
                    StandOutPreview(F.focusPop.state.value / 100f)
                } }
            }
            item { m ->
                SwitchRow(
                    Icons.Filled.AutoAwesome, "Only the game you're on moves",
                    "Spinning, bobbing and hopping discs, cartridges and cases: only the game you're on (off: every game on screen).",
                    com.shelf.launcher.data.Features.motionFocusedOnly.state.value, { com.shelf.launcher.data.Features.motionFocusedOnly.value = it },
                    modifier = m
                )
            }
            item { m -> Box(m) { ShelfCard {
                SectionLabel("Each layout its own")
                MutedNote("Everything here is the general choice. A layout's own animations (the game's picture coming in, its info, pages, how far the game you're on stands out) are in Home › Layout options, under that layout's other options.")
            } } }
            item { m ->
                Box(m) { ShelfCard {
                    SwitchRow(
                        Icons.Filled.AutoAwesome, "Pictures fade over each other",
                        "The next game's picture fades in over the last one, so there's never a flash of black (off: it replaces it at once).",
                        com.shelf.launcher.data.Features.animHeldPictures.state.value, { com.shelf.launcher.data.Features.animHeldPictures.value = it }
                    )
                    Spacer(Modifier.height(6.dp))
                    PicturesPreview(com.shelf.launcher.data.Features.animHeldPictures.state.value)
                } }
            }
            }
    }
}

/** Settings › Game look: how every game is drawn, in every home layout. */
@Composable
private fun GameLookContent(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("GameLookContent")
    CollapsibleSection("Covers, cases & discs") {
        GameMediaCard(appearance, onChange)
    }
    // Update 131: your cartridge, spine and disc-console packs, downloaded (data/Packs.kt).
    CollapsibleSection("Extra art packs") {
        PacksCard()
    }
    CollapsibleSection("Cards") {
        CardShapeCard(appearance, onChange)
    }
    CollapsibleSection("Frames & mastered games") {
        FoldGroup("Frame & shimmer", summary = "The frame round the game you're on", key = "cards_frame", startOpen = true) {
        SettingsGrid {
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.Palette,
                    title = "Coloured frame",
                    subtitle = "The accent-coloured frame around the game you're on, in every home layout. The glow is Game look › Cards › Effect.",
                    checked = appearance.focusBorder,
                    onChange = { onChange(appearance.copy(focusBorder = it)) },
                    modifier = m
                )
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.AutoAwesome,
                    title = "Shimmer on mastered games",
                    subtitle = "The light running along the golden border of mastered games.",
                    checked = appearance.masteredShimmer,
                    onChange = { onChange(appearance.copy(masteredShimmer = it)) },
                    modifier = m
                )
            }
        }
        }
        FoldGroup("Mastered games", summary = appearance.masteredStyle.label, key = "cards_mastered") {
        ShelfCard {
            SectionLabel("Mastered frame colour")
            ChipFlow {
                Chip("Gold / silver", accent = !appearance.masteredThemeColor) { onChange(appearance.copy(masteredThemeColor = false)) }
                Chip("Theme colour", accent = appearance.masteredThemeColor) { onChange(appearance.copy(masteredThemeColor = true)) }
            }
            Spacer(Modifier.height(12.dp))
            SectionLabel("Mastered games")
            com.shelf.launcher.data.MasteredStyle.values().forEach { style ->
                RadioLine(style.label, appearance.masteredStyle == style, style.blurb) {
                    onChange(appearance.copy(masteredStyle = style))
                }
            }
        }
        }
    }
    CollapsibleSection("Artwork") {
        FoldGroup("Animated artwork", summary = if (appearance.animatedArt) "On" else "Off", key = "art_animated", startOpen = true) {
            SettingsGrid {
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.Gif,
                    title = "Animated artwork",
                    subtitle = "Covers, heroes and logos that are GIFs, animated WebPs or animated PNGs play instead of " +
                        "standing still - your own picks, or SteamGridDB's animated ones. Android 9 and newer. " +
                        "Uses a little more battery.",
                    checked = appearance.animatedArt,
                    onChange = { onChange(appearance.copy(animatedArt = it)) },
                    modifier = m
                )
            }
            // Update 148: every game's animated cover moving, or only the one you're on (lighter on small handhelds).
            if (appearance.animatedArt) item { m ->
                SwitchRow(
                    icon = Icons.Filled.Gif,
                    title = "Only the game you're on moves",
                    subtitle = "On (default): a game's animated cover, banner or background plays only while you're on it — " +
                        "the other games show their plain cover (the animation's first frame), in every layout and on both " +
                        "screens. Off: every animated picture plays.",
                    checked = com.shelf.launcher.data.Features.animatedFocusOnly.state.value,
                    onChange = { com.shelf.launcher.data.Features.animatedFocusOnly.value = it },
                    modifier = m
                )
            }
            if (appearance.animatedArt) item { m ->
                SwitchRow(
                    icon = Icons.Filled.CloudDownload,
                    title = "Fetch animated art when scraping",
                    subtitle = "SteamGridDB's animated covers and heroes are used first, the still ones where a game has " +
                        "none. Applies to newly scraped games (or Re-scrape artwork). The artwork picker always lists both.",
                    checked = appearance.preferAnimatedArt,
                    onChange = { onChange(appearance.copy(preferAnimatedArt = it)) },
                    modifier = m
                )
            }
            }
        }
        FoldGroup("Artwork priority", summary = com.shelf.launcher.data.Features.artPriority.state.value.label, key = "art_priority") {
        ShelfCard {
            SectionLabel("Artwork priority")
            MutedNote(
                "Which picture fills the background on the home screen, the hero carousel, the game popup and the " +
                    "second screen. A background you picked yourself (Artwork & details) always wins, and each game can " +
                    "have its own priority (Game settings › Look for this game)."
            )
            Spacer(Modifier.height(4.dp))
            val prio = com.shelf.launcher.data.Features.artPriority.state.value
            com.shelf.launcher.data.Features.ArtPriority.values().forEach { option ->
                RadioLine(option.label, prio == option, option.blurb) { com.shelf.launcher.data.Features.artPriority.value = option }
            }
            if (prio == com.shelf.launcher.data.Features.ArtPriority.SMART && !appearance.animatedArt) {
                Spacer(Modifier.height(4.dp))
                MutedNote("Animated heroes are skipped while Animated artwork is off (above).")
            }
            if (prio == com.shelf.launcher.data.Features.ArtPriority.SMART) {
                MutedNote("Games scraped before this update learn about their animated hero with Re-scrape artwork.")
            }
        }
        }
        FoldGroup("Sharpness", key = "art_sharp") {
            SettingsGrid {
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.AutoAwesome,
                    title = "Upscale small backgrounds",
                    subtitle = "Low-resolution background art (old screenshots, small heroes) is blown up crisply " +
                        "instead of looking blurry. Big images are left alone.",
                    checked = appearance.sharpHeroes,
                    onChange = { onChange(appearance.copy(sharpHeroes = it)) },
                    modifier = m
                )
            }
            }
        }
    }
}

/** Settings › Theme: the theme and its colours, the background, the interface style, size and text, effects. */
@Composable
private fun ThemeContent(
    currentTheme: AppTheme,
    onSelectTheme: (AppTheme) -> Unit,
    screensaver: ScreensaverSettings,
    onScreensaverChange: (ScreensaverSettings) -> Unit,
    onPreviewScreensaver: () -> Unit,
    appearance: AppearanceSettings,
    onAppearanceChange: (AppearanceSettings) -> Unit,
    hasSecondScreen: Boolean
) {
    val onChange = onAppearanceChange
    com.shelf.launcher.Breadcrumbs.mark("ThemeContent")
    val context = LocalContext.current
    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onChange(appearance.copy(wallpaperUri = uri.toString(), backgroundStyle = BackgroundStyle.WALLPAPER))
        }
    }

    CollapsibleSection("Themes & colours") {
        FoldGroup("Themes", summary = currentTheme.label, key = "theme_browser", startOpen = true) {
            ShelfCard {
                ColorsFollowRow(currentTheme)
                Spacer(Modifier.height(10.dp))
                ThemeSwatches(currentTheme, onSelectTheme)
            }
        }
        // Colours, blur, dim and motion of this theme (and where artwork colours come from).
        FoldGroup("Colours & look", summary = "Colours, blur, dim, motion", key = "theme_look") {
            ThemeLookCard(currentTheme)
        }
        FoldGroup("Favourites & rotation", key = "theme_rotation") {
            ThemeRotationCard()
        }
        // Your own animated themes and music visualizers (a checked, size-capped text file each), and how to make one.
        CustomPacks.ensureLoaded(androidx.compose.ui.platform.LocalContext.current)
        FoldGroup("Your own themes & visualizers", summary = CustomPacks.summary(), key = "theme_custom_packs") {
            ShelfCard { CustomPacksCard() }
        }
        if (currentTheme.motif.reactive) {
            FoldGroup("Music theme: player, equalizer & lyrics", key = "theme_music", startOpen = true) {
                ShelfCard { NowPlayingThemeCard(appearance, onAppearanceChange) }
            }
        }
        // Extras for the theme in use (or picked as the animated scene).
        val sceneTheme = appearance.sceneTheme?.let { n -> AppTheme.values().firstOrNull { it.name == n } }
        if (currentTheme == AppTheme.RETRO_BOUNCE || sceneTheme == AppTheme.RETRO_BOUNCE) {
            Spacer(Modifier.height(10.dp))
            ShelfCard { RetroBounceSettings() }
        }
        if (currentTheme == AppTheme.LYRIC_CLOUD || sceneTheme == AppTheme.LYRIC_CLOUD) {
            Spacer(Modifier.height(10.dp))
            ShelfCard { LyricCloudSettings() }
        }
        val lyricTheme = listOf(currentTheme, sceneTheme).firstOrNull { it != null && it.motif.lyrics && it != AppTheme.LYRIC_CLOUD }
        if (lyricTheme != null) {
            Spacer(Modifier.height(10.dp))
            ShelfCard { LyricThemeSettings(lyricTheme) }
        }
        // Update 119: the Lyric cloud over the album cover too; the trophy themes' mastered-only switch.
        if (currentTheme == AppTheme.LYRIC_CLOUD || sceneTheme == AppTheme.LYRIC_CLOUD) {
            Spacer(Modifier.height(10.dp))
            SwitchRow(
                Icons.Filled.Album, "Album cover behind the words",
                "The song's cover fills the screen, softened and dimmed. Off: the theme's own music scene.",
                com.shelf.launcher.data.Features.lyricCoverBg.state.value, { com.shelf.launcher.data.Features.lyricCoverBg.value = it }
            )
        }
        val trophyTheme = listOf(currentTheme, sceneTheme).firstOrNull { it == AppTheme.TROPHY_VAULT || it == AppTheme.BADGE_BOUNCE }
        if (trophyTheme != null) {
            Spacer(Modifier.height(10.dp))
            ShelfCard {
                SectionLabel(trophyTheme.label)
                MutedNote("Your games you mastered most recently, then your unlocked achievements — the last 30 days' and the ones in the lists of the games you played last.")
                Spacer(Modifier.height(6.dp))
                SwitchRow(
                    Icons.Filled.EmojiEvents, "Only mastered games",
                    "Just the games you mastered (and Steam games at 100 %), newest first.",
                    com.shelf.launcher.data.Features.trophyMasteredOnly.state.value, { com.shelf.launcher.data.Features.trophyMasteredOnly.value = it }
                )
            }
        }
    }
    // Update 24: the Volt look (VoltSettings.kt).
    CollapsibleSection("Interface style") { InterfaceStyleSettings() }
    CollapsibleSection("Background") {
        FoldGroup("Behind the dock", summary = appearance.backgroundStyle.label, key = "bg_dock", startOpen = true) {
            ShelfCard {
            Text("Behind the dock", color = ShelfTheme.textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                BackgroundStyle.values().forEach { style ->
                    Chip(style.label, accent = appearance.backgroundStyle == style) {
                        if (style == BackgroundStyle.WALLPAPER && appearance.wallpaperUri == null) {
                            pickWallpaper.launch(arrayOf("image/*", "video/*"))
                        } else {
                            onChange(appearance.copy(backgroundStyle = style))
                        }
                    }
                }
                if (appearance.backgroundStyle == BackgroundStyle.WALLPAPER) {
                    Chip("Choose another picture or video…", accent = false) { pickWallpaper.launch(arrayOf("image/*", "video/*")) }
                }
            }
            if (appearance.backgroundStyle == BackgroundStyle.WALLPAPER) {
                MutedNote(
                    "Your own picture - or something that moves: a GIF, an animated WebP or a short video (MP4, WebM), " +
                        "played muted and looping. It pauses whenever the home screen isn't in front, while the screen " +
                        "is off and after a few idle minutes."
                )
            }
            Spacer(Modifier.height(12.dp))
            DockBackingSettings()
            }
        }
        FoldGroup("Shade on the game's art", summary = com.shelf.launcher.data.Features.heroShade.state.value.label, key = "bg_shade", startOpen = true) {
            ShelfCard {
                MutedNote(
                    "Whenever the game's art is the background (Game art, Blurred art, the art behind the Side menu, Spotlight and Icon bar). " +
                        "Console style is how a PS5-like home does it: darker from the left edge where the text is, along the top and the bottom, " +
                        "and slightly in the corners, so the middle of the picture shines and the text reads on any art."
                )
                Spacer(Modifier.height(8.dp))
                ChipFlow {
                    com.shelf.launcher.data.Features.HeroShade.values().forEach { m ->
                        Chip(m.label, accent = com.shelf.launcher.data.Features.heroShade.state.value == m) { com.shelf.launcher.data.Features.heroShade.value = m }
                    }
                }
                if (com.shelf.launcher.data.Features.heroShade.state.value != com.shelf.launcher.data.Features.HeroShade.OFF) {
                    Spacer(Modifier.height(8.dp))
                    PercentSlider(
                        "Strength", com.shelf.launcher.data.Features.heroShadeStrength.state.value,
                        { com.shelf.launcher.data.Features.heroShadeStrength.value = it }, min = 0, max = 100, live = true,
                        valueText = { "$it %" }
                    )
                }
                Spacer(Modifier.height(6.dp))
                MutedNote("For the full console feel, also try Motion & transitions › \"Quick fade + slow zoom\": the art drifts in slowly, like the PS5's.")
            }
        }
        FoldGroup("Background picture", summary = appearance.heroFit.label, key = "bg_picture") {
            ShelfCard {
                MutedNote("Colours, blur and dim are per theme: Theme › Theme look.")
                Spacer(Modifier.height(6.dp))
            Text("Background picture", color = ShelfTheme.textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                com.shelf.launcher.data.HeroFit.values().forEach { fit ->
                    Chip(fit.label, accent = appearance.heroFit == fit) { onChange(appearance.copy(heroFit = fit)) }
                }
            }
            }
        }
        FoldGroup("Motion & transitions", summary = appearance.heroMotion.label, key = "bg_motion") {
            ShelfCard {
            Text("Background art motion", color = ShelfTheme.textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                com.shelf.launcher.data.HeroMotion.values().forEach { m ->
                    Chip(m.label, accent = appearance.heroMotion == m) { onChange(appearance.copy(heroMotion = m)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            HeroMotionPreview(appearance.heroMotion)
            Spacer(Modifier.height(8.dp))
            Text("Transition speed", color = ShelfTheme.textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                com.shelf.launcher.data.Features.Speed.values().forEach { speed ->
                    Chip(speed.label, accent = com.shelf.launcher.data.Features.transitionSpeed.state.value == speed) {
                        com.shelf.launcher.data.Features.transitionSpeed.value = speed
                    }
                }
            }
            if (appearance.heroMotion == com.shelf.launcher.data.HeroMotion.QUICK_ZOOM || appearance.heroMotion == com.shelf.launcher.data.HeroMotion.KEN_BURNS) {
                Spacer(Modifier.height(8.dp))
                Text("Zoom while you rest on a game", color = ShelfTheme.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                ChipFlow {
                    listOf(5, 10, 15, 20, 25).forEach { pct ->
                        Chip("$pct %", accent = appearance.heroZoomPercent == pct) { onChange(appearance.copy(heroZoomPercent = pct)) }
                    }
                }
            }
            MutedNote(
                "Quick fade keeps the old picture until the new one has loaded, then fades it in fast — no blank flash. " +
                    "Slide, Parallax push, Zoom through, Portal, Cover Flow, Pixel dissolve, CRT switch and Memory card move the new " +
                    "picture in instead; Ken Burns pans slowly while you rest. Speed applies to all of them (and the hero carousel). " +
                    "The zoom drifts slowly closer (about 20 s) and then stays; it pauses while a game runs."
            )
            }
        }
        if (appearance.backgroundStyle == BackgroundStyle.ANIMATED) {
            FoldGroup("Animated scene", key = "bg_scene") {
                ShelfCard {
                Text("Animated scene", color = ShelfTheme.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                // Everywhere, or only in the category the home screen is showing right now.
                var perCategory by remember { mutableStateOf(false) }
                val catKey = HomeContext.categoryKey
                val catScenes = remember(appearance.categoryScenes) { com.shelf.launcher.data.CategoryScenes.parse(appearance.categoryScenes) }
                ChipFlow {
                    Chip("Everywhere", accent = !perCategory) { perCategory = false }
                    Chip("Only in ${HomeContext.categoryLabel}", accent = perCategory) { perCategory = true }
                }
                Spacer(Modifier.height(6.dp))
                if (perCategory) {
                    ScenePicker(catScenes[catKey]) { pick ->
                        val next = if (pick == null) catScenes - catKey else catScenes + (catKey to pick)
                        onChange(appearance.copy(categoryScenes = com.shelf.launcher.data.CategoryScenes.format(next)))
                    }
                } else {
                    ScenePicker(appearance.sceneTheme) { onChange(appearance.copy(sceneTheme = it)) }
                }
                MutedNote(
                    if (perCategory) "This scene plays only while ${HomeContext.categoryLabel} is shown; \"Follow theme\" " +
                        "removes it there. Switch category on the home screen to set another one."
                    else "Pick any scene, whatever theme you use. Each plays in its own colours; " +
                        "\"Follow theme\" uses the active theme's scene and colours."
                )
                if (catScenes.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Scenes per category", color = ShelfTheme.textMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    ChipFlow {
                        catScenes.forEach { (key, theme) ->
                            val label = AppTheme.values().firstOrNull { it.name == theme }?.label ?: theme
                            Chip("${categoryKeyLabel(key)}: $label  ✕", accent = false) {
                                onChange(appearance.copy(categoryScenes = com.shelf.launcher.data.CategoryScenes.format(catScenes - key)))
                            }
                        }
                    }
                }

                }
            }
            FoldGroup("Scene style", key = "bg_scene_style") {
                ShelfCard {
                // ---- Scene style ----
                Spacer(Modifier.height(12.dp))
                Text("Scene style", color = ShelfTheme.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                SwitchRow(
                    icon = Icons.Filled.Palette,
                    title = "Colours from the game you're on",
                    subtitle = "The scene takes its colours from the cover of the game you're resting on, gliding " +
                        "to the next game's after a moment. (Music scenes always use the song's cover.)",
                    checked = appearance.sceneFromCover,
                    onChange = { onChange(appearance.copy(sceneFromCover = it)) }
                )
                SwitchRow(
                    icon = Icons.Filled.Schedule,
                    title = "Follow the time of day",
                    subtitle = "Pink-gold at dawn, a little lighter by day, orange and violet at dusk, deep and cool at night.",
                    checked = appearance.sceneTimeOfDay,
                    onChange = { onChange(appearance.copy(sceneTimeOfDay = it)) }
                )
                SwitchRow(
                    icon = Icons.Filled.AutoAwesome,
                    title = "Fade between scenes",
                    subtitle = "When the scene changes (another theme, or a category with its own scene) the new one dissolves in.",
                    checked = appearance.sceneFade,
                    onChange = { onChange(appearance.copy(sceneFade = it)) }
                )
                if (hasSecondScreen) {
                    SwitchRow(
                        icon = Icons.AutoMirrored.Filled.ScreenShare,
                        title = "Animate only on the second screen",
                        subtitle = "The scene moves behind the second screen's page; the main screen keeps a still frame of it (calmer, and easier on the battery).",
                        checked = appearance.sceneSecondOnly,
                        onChange = { onChange(appearance.copy(sceneSecondOnly = it)) }
                    )
                }
                if (hasSecondScreen) {
                    Spacer(Modifier.height(6.dp))
                    SectionLabel("Scene on the second screen")
                    ScenePicker(appearance.secondSceneTheme) { onChange(appearance.copy(secondSceneTheme = it)) }
                    MutedNote("Pick the scene for the second screen; \"Follow theme\" plays the same one as the main screen. The colours are always the main screen's, so the two screens never clash.")
                }
                MutedNote("Speed, particles, colour shift and darkening are part of each theme's look now: Theme › Theme look.")
                }
            }
            FoldGroup("Quality & battery", summary = appearance.sceneQuality.label, key = "bg_quality") {
                ShelfCard {
                Spacer(Modifier.height(10.dp))
                SwitchRow(
                    icon = Icons.Filled.AutoAwesome,
                    title = "Animate on Lite",
                    subtitle = "Keeps the animated background moving even when Lite effects are on (or chosen " +
                        "automatically for this device). Lite otherwise shows a still frame. Uses a little more " +
                        "battery and GPU; it still stops while the screen is off or a game is running.",
                    checked = appearance.animateOnLite,
                    onChange = { onChange(appearance.copy(animateOnLite = it)) }
                )
                Spacer(Modifier.height(10.dp))
                Text("Background quality", color = ShelfTheme.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                ChipFlow {
                    com.shelf.launcher.data.SceneQuality.values().forEach { q ->
                        Chip(q.label, accent = appearance.sceneQuality == q) { onChange(appearance.copy(sceneQuality = q)) }
                    }
                }
                // Update 125: the frame rate for the whole app.
                Spacer(Modifier.height(10.dp))
                RowChoice("Background frame rate", com.shelf.launcher.data.Features.SceneFps.values(), com.shelf.launcher.data.Features.sceneFps.state.value, { it.label }) {
                    com.shelf.launcher.data.Features.sceneFps.value = it
                }
                MutedNote(
                    if (com.shelf.launcher.data.Features.sceneFps.state.value == com.shelf.launcher.data.Features.SceneFps.AUTO)
                        "Automatic saves battery: the background runs calmer when nobody touches the screen and under popups."
                    else "The animated backgrounds run at this rate all the time, everywhere in Voltcap (never lowered to save battery). They still stop while the screen is off or a game is running."
                )
                if (appearance.sceneQuality == com.shelf.launcher.data.SceneQuality.AUTO && com.shelf.launcher.device.FrameWatch.steps.intValue > 0) {
                    Spacer(Modifier.height(4.dp))
                    MutedNote("Automatic lowered it ${com.shelf.launcher.device.FrameWatch.steps.intValue} step(s) this session because the home screen kept missing frames on this device. Pick a quality yourself to keep it fixed.")
                }
                Spacer(Modifier.height(10.dp))
                SwitchRow(
                    icon = Icons.Filled.AutoAwesome,
                    title = "Half-resolution scenes",
                    subtitle = "The animated scenes are drawn at half the screen's resolution (a quarter of the pixels " +
                        "each frame) and smoothly stretched. They're soft light, noise and glows, so it hardly shows, " +
                        "and the graphics chip does far less work. Turn it off if a scene looks too soft to you.",
                    checked = com.shelf.launcher.data.Features.sceneHalfRes.state.value,
                    onChange = { com.shelf.launcher.data.Features.sceneHalfRes.value = it }
                )
                Spacer(Modifier.height(10.dp))
                SwitchRow(
                    icon = Icons.Filled.Bedtime,
                    title = "Second screen slower when unused",
                    subtitle = "When nobody has touched the second screen for 20 seconds, its animated background runs " +
                        "at about 15 frames a second. A touch there brings it back to full speed at once.",
                    checked = com.shelf.launcher.data.Features.secondSlowWhenUnused.state.value,
                    onChange = { com.shelf.launcher.data.Features.secondSlowWhenUnused.value = it }
                )
                Spacer(Modifier.height(10.dp))
                SwitchRow(
                    icon = Icons.Filled.Bedtime,
                    title = "Save power when idle",
                    subtitle = "After 30 seconds without a button press the background slows to about 30 frames a " +
                        "second (15 after three minutes) and the card effects hold still; the next press brings them " +
                        "back at once. A music visualizer keeps its speed while music plays. The background holds a " +
                        "still frame only while something covers the whole screen (the screensaver, a category page, " +
                        "a guide or a picture viewer); under a popup that leaves it showing, it runs at about 15.",
                    checked = appearance.idleSaver,
                    onChange = { onChange(appearance.copy(idleSaver = it)) }
                )
                MutedNote(
                    "If the home screen stutters with an animated background (Retroid Pocket Classic, Konkr and " +
                        "other lighter handhelds), pick Low or Lowest: the scene is drawn at a fraction of the " +
                        "screen's resolution and fewer times a second, then smoothly stretched — the soft glows " +
                        "look almost the same. Automatic picks Low on slower devices."
                )
                }
            }
        }
    }
    CollapsibleSection("Size & text") {
        ShelfCard {
                    SectionLabel("Size")
                    MutedNote("Scales cards, text and popups together.")
                    Spacer(Modifier.height(8.dp))
                    ChipFlow {
                        UiDensity.values().forEach { d ->
                            Chip(d.label, accent = appearance.density == d) { onChange(appearance.copy(density = d)) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    SectionLabel("Spacing")
                    MutedNote("Comfortable or Compact: how much room between cards, rows and settings. Sizes stay the same.")
                    Spacer(Modifier.height(8.dp))
                    ChipFlow {
                        com.shelf.launcher.data.Features.Density.values().forEach { d ->
                            Chip(d.label, accent = com.shelf.launcher.data.Features.density.state.value == d) {
                                com.shelf.launcher.data.Features.density.value = d
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    SectionLabel("Text size")
                    MutedNote("Only the text: bigger letters, same layout.")
                    Spacer(Modifier.height(8.dp))
                    ChipFlow {
                        listOf(90 to "Small", 100 to "Normal", 115 to "Large", 130 to "Larger").forEach { (v, label) ->
                            Chip(label, accent = appearance.textScale == v) { onChange(appearance.copy(textScale = v)) }
                        }
                    }
        }
    }
    CollapsibleSection("Effects & battery") {
        FoldGroup("Performance", summary = appearance.performanceMode.label, key = "home_perf") {
            SettingsGrid {
            item { m ->
                Box(m) { ShelfCard {
                    RowChoice("Performance", com.shelf.launcher.data.PerformanceMode.values(), appearance.performanceMode, { it.label }) {
                        onChange(appearance.copy(performanceMode = it))
                    }
                    val perfContext = LocalContext.current
                    val slow = remember { com.shelf.launcher.device.DevicePerformance.isLowEnd(perfContext) }
                    MutedNote(
                        "Lite keeps the glow on the game you're on still (nothing redraws while you just look), " +
                            "blurs backgrounds the cheap way and skips upscaling — smoother on slower handhelds and " +
                            "lighter on the battery. Automatic picks " + (if (slow) "Lite on this device." else "full effects on this device.")
                    )
                } }
            }
            // Update 159: the decoded art stays in memory (a bigger share, kept while a game runs).
            item { m ->
                Box(m) {
                    SwitchRow(
                        icon = Icons.Filled.Memory,
                        title = "Keep art in memory",
                        subtitle = "Every picture is read from storage once and kept: switching layouts, looks and categories, or coming back from a game, shows the art at once. It uses more memory; Android can still take it back when a game really needs it. Takes effect after Voltcap restarts.",
                        checked = com.shelf.launcher.data.Features.keepArt.state.value,
                        onChange = { com.shelf.launcher.data.Features.keepArt.value = it }
                    )
                }
            }
            // Update 162: the carousel and the open / inspect animation in full on Lite too.
            item { m ->
                Box(m) {
                    SwitchRow(
                        icon = Icons.Filled.AutoAwesome,
                        title = "Full animations on Lite",
                        subtitle = "The carousel's category slide and opening a game (its box, cartridge or disc flying out and turning) stay in full even with Lite effects. Off: Lite shows a quick fade instead.",
                        checked = com.shelf.launcher.data.Features.motionOnLite.state.value,
                        onChange = { com.shelf.launcher.data.Features.motionOnLite.value = it }
                    )
                }
            }
            }
        }
    }
    CollapsibleSection("Controller lights") {
        SettingsGrid {
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.Palette,
                    title = "Controller lights",
                    subtitle = "Voltcap sets the stick / controller RGB lights. Colour, brightness and Off / System setting are under R1 › Lights.",
                    checked = appearance.lightsEnabled,
                    onChange = { onAppearanceChange(appearance.copy(lightsEnabled = it)) },
                    modifier = m
                )
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.Palette,
                    title = "Lights off with the screensaver",
                    subtitle = "When the screensaver fades to black, the controller lights go dark too.",
                    checked = appearance.lightsOffWithScreensaver,
                    onChange = { onAppearanceChange(appearance.copy(lightsOffWithScreensaver = it)) },
                    modifier = m
                )
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.Palette,
                    title = "Lights pulse with the music",
                    subtitle = "While music plays and Voltcap is on screen, the lights beat in the colour of the song's " +
                        "cover (to the real sound with \"React to the real beat\" on, otherwise to a steady beat).",
                    checked = appearance.lightsMusic,
                    onChange = { onAppearanceChange(appearance.copy(lightsMusic = it)) },
                    modifier = m
                )
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.Palette,
                    title = "Lights in the theme colour",
                    subtitle = "The lights follow the theme's accent colour (and change with it). Off: the colour picked under R1.",
                    checked = appearance.lightsColor == com.shelf.launcher.data.LIGHTS_THEME_COLOR,
                    onChange = {
                        onAppearanceChange(appearance.copy(lightsColor = if (it) com.shelf.launcher.data.LIGHTS_THEME_COLOR else 0xFFFF6A00.toInt()))
                    },
                    modifier = m
                )
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.EmojiEvents,
                    title = "Flash on achievement unlock",
                    subtitle = "The stick lights blink yellow when you unlock something — on AYN handhelds even while you play.",
                    checked = appearance.flashOnUnlock,
                    onChange = { onAppearanceChange(appearance.copy(flashOnUnlock = it)) },
                    modifier = m
                )
            }
        }
    }
}

/** Settings › Achievements: on the cards and pop-ups, their look, and matching games to RetroAchievements. */
@Composable
private fun AchievementsContent(
    badgeMode: AchievementBadgeMode,
    badgeNextToTitle: Boolean,
    onSetBadgeMode: (AchievementBadgeMode) -> Unit,
    onToggleBadgePlacement: () -> Unit,
    appearance: AppearanceSettings,
    onAppearanceChange: (AppearanceSettings) -> Unit,
    isMatchingAchievements: Boolean,
    onAutoMatchAchievements: () -> Unit,
    onRehashAchievements: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("AchievementsContent")
    CollapsibleSection("Achievement views") {
        ShelfCard {
            RowChoice("A game's achievements", com.shelf.launcher.data.Features.AchView.values(), com.shelf.launcher.data.Features.achView.state.value, { it.label }) {
                com.shelf.launcher.data.Features.achView.value = it
            }
            MutedNote(
                when (com.shelf.launcher.data.Features.achView.state.value) {
                    com.shelf.launcher.data.Features.AchView.POWER -> "Voltcap's own: the set as a battery charging up, each achievement a capsule cell — lit when unlocked (gold when rare), its points and a rarity bar."
                    com.shelf.launcher.data.Features.AchView.TROPHY -> "Like a console's trophy list: big badges, a tier from the points (bronze, silver, gold), \"Common | 84% of players earned\", the date, the description beside it."
                    com.shelf.launcher.data.Features.AchView.BADGES -> "The badge wall: square badges with their names."
                    com.shelf.launcher.data.Features.AchView.LIST -> "The plain list."
                }
            )
            // Update 151: the comments of the achievement you rest on open by themselves (on by default).
            Spacer(Modifier.height(10.dp))
            SwitchRow(
                icon = Icons.Filled.EmojiEvents,
                title = "Show comments",
                subtitle = "In a game's achievements, the one you rest on opens its details and comments by itself. Off: A opens them.",
                checked = com.shelf.launcher.data.Features.achCommentsAuto.state.value,
                onChange = { com.shelf.launcher.data.Features.achCommentsAuto.value = it }
            )
            Spacer(Modifier.height(10.dp))
            RowChoice("The Achievements screen", com.shelf.launcher.data.Features.AchHubView.values(), com.shelf.launcher.data.Features.achHubView.state.value, { it.label }) {
                com.shelf.launcher.data.Features.achHubView.value = it
            }
            MutedNote(
                when (com.shelf.launcher.data.Features.achHubView.state.value) {
                    com.shelf.launcher.data.Features.AchHubView.POWER -> "Every game a battery that charges with its achievements; full and gold once mastered."
                    com.shelf.launcher.data.Features.AchHubView.SCORECARD -> "Tiles with the game's picture, two rings (unlocked and hardcore) and the percentage, with your mastered games, unlocks and this month on top."
                    com.shelf.launcher.data.Features.AchHubView.LIST -> "The plain list with a progress bar per game."
                }
            )
        }
    }
    CollapsibleSection("Achievements on cards & pop-ups") {
        FoldGroup("On the cards", summary = "Shimmer, badge & progress bar, trophy count", startOpen = true) {
        // One switch for all of it.
        SettingsGrid {
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.AutoAwesome,
                    title = "Shimmer on mastered games",
                    subtitle = "A light sweeps across every mastered / completed game (brighter on the one you're on). Same switch as in Game look › Frames & mastered games.",
                    checked = appearance.masteredShimmer,
                    onChange = { onAppearanceChange(appearance.copy(masteredShimmer = it)) },
                    modifier = m
                )
            }
            if (appearance.masteredShimmer) item { m ->
                ShelfCard {
                    SectionLabel("Shimmer look")
                    ChipFlow {
                        com.shelf.launcher.data.ShimmerStyle.values().forEach { st ->
                            Chip(st.label, accent = appearance.shimmerStyle == st) { onAppearanceChange(appearance.copy(shimmerStyle = st)) }
                        }
                    }
                }
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.EmojiEvents,
                    title = "Achievements on the cards",
                    subtitle = "Every trophy pill, progress bar and NEW mark on covers and the wide capsule. " +
                        "Off hides them all; the options below pick which ones show.",
                    checked = appearance.achievementsOnCards,
                    onChange = { onAppearanceChange(appearance.copy(achievementsOnCards = it)) },
                    modifier = m
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // Where the badge / progress bar goes and what it shows — first, since
        // it's the setting people look for here.
        ShelfCard {
            Text("Badge & progress bar", color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            MutedNote("The achievement badge of the game you're on.")
            Spacer(Modifier.height(10.dp))
            Text("Where it shows", color = ShelfTheme.textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                Chip("Under the card", accent = appearance.progressOnBanner && !appearance.progressAbove && !badgeNextToTitle) {
                    if (badgeNextToTitle) onToggleBadgePlacement()
                    onAppearanceChange(appearance.copy(progressOnBanner = true, progressAbove = false))
                }
                Chip("Next to the title", accent = appearance.progressOnBanner && !appearance.progressAbove && badgeNextToTitle) {
                    if (!badgeNextToTitle) onToggleBadgePlacement()
                    onAppearanceChange(appearance.copy(progressOnBanner = true, progressAbove = false))
                }
                Chip("Above the title", accent = appearance.progressOnBanner && appearance.progressAbove) {
                    onAppearanceChange(appearance.copy(progressOnBanner = true, progressAbove = true))
                }
                Chip("Off", accent = !appearance.progressOnBanner) {
                    onAppearanceChange(appearance.copy(progressOnBanner = false))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("What it shows", color = ShelfTheme.textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                Chip("Progress bar", accent = badgeMode == AchievementBadgeMode.PROGRESS) {
                    onSetBadgeMode(AchievementBadgeMode.PROGRESS)
                }
                Chip("Latest unlock", accent = badgeMode == AchievementBadgeMode.RECENT_UNLOCK) {
                    onSetBadgeMode(AchievementBadgeMode.RECENT_UNLOCK)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ShelfCard {
            Text("Trophy count", color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            MutedNote("The small \"🏆 12/40\" pill on the art.")
            Spacer(Modifier.height(10.dp))
            // Update 129: the same choice as the R1 Achievements tile (covers, cartridges, discs, every layout).
            RowChoice("Where on the game", com.shelf.launcher.data.Features.AchPlace.values(), com.shelf.launcher.data.Features.achPlace.state.value, { it.label }) {
                com.shelf.launcher.data.Features.achPlace.value = it
            }
            RowChoice("Corner", com.shelf.launcher.data.CardCorner.values(), appearance.trophyCorner, { it.label }) {
                // Update 151: picking a corner also puts the count in the corner (it did nothing while "Where on the
                // game" was Above / Next to the name / Under, so the right-hand corners seemed out of reach).
                com.shelf.launcher.data.Features.achPlace.value = com.shelf.launcher.data.Features.AchPlace.CORNER
                onAppearanceChange(appearance.copy(trophyCorner = it, achievementsOnCards = true, coverAchievements = true))
            }
            ChipFlow {
                Chip(if (appearance.coverAchievements) "✓ On covers" else "On covers", accent = appearance.coverAchievements) {
                    onAppearanceChange(appearance.copy(coverAchievements = !appearance.coverAchievements))
                }
                Chip(if (appearance.trophyOnBanner) "✓ On the wide capsule" else "On the wide capsule", accent = appearance.trophyOnBanner) {
                    onAppearanceChange(appearance.copy(trophyOnBanner = !appearance.trophyOnBanner))
                }
            }
        }
        }
        FoldGroup("Lists, pop-ups & rarity", summary = "Badge wall, fly-in, pop-ups while playing, rarity, tips") {
        Spacer(Modifier.height(12.dp))
        SettingsGrid {
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.EmojiEvents,
                    title = "Recent unlocks on covers",
                    subtitle = "A \"NEW\" trophy on covers of games where you recently unlocked something.",
                    checked = appearance.coverRecentUnlocks,
                    onChange = { onAppearanceChange(appearance.copy(coverRecentUnlocks = it)) },
                    modifier = m
                )
            }
            // Unlock pop-ups, rarity and tips live with the other achievement options (they used to sit
            // under Controller lights).
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.EmojiEvents,
                    title = "Unlock pop-ups while playing",
                    subtitle = "A notification with the badge, description and points slides in over the game for every unlock " +
                        "(Voltcap checks every 45 s). Android asks once to allow notifications.",
                    checked = appearance.unlockPopups,
                    onChange = { onAppearanceChange(appearance.copy(unlockPopups = it)) },
                    modifier = m
                )
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.EmojiEvents,
                    title = "Rarity",
                    subtitle = "\"Unlocked by 12 % of players\" under each achievement, Rare under 5 %, and an Easiest next mark on the locked one most players have.",
                    checked = appearance.showRarity,
                    onChange = { onAppearanceChange(appearance.copy(showRarity = it)) },
                    modifier = m
                )
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.EmojiEvents,
                    title = "Badge wall",
                    subtitle = "Steam achievements as square badges with the name on two lines and how many players have it, instead of a list. RetroAchievements lists: Achievement views (Power cells, Trophy list, Badges, List).",
                    checked = appearance.achievementBadges,
                    onChange = { onAppearanceChange(appearance.copy(achievementBadges = it)) },
                    modifier = m
                )
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.EmojiEvents,
                    title = "Badges fly in",
                    subtitle = "When an achievement list opens, the badges swoop in from the sides and lock into place one after another.",
                    checked = appearance.achievementFlyIn,
                    onChange = { onAppearanceChange(appearance.copy(achievementFlyIn = it)) },
                    modifier = m
                )
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.EmojiEvents,
                    title = "Fly-in sounds",
                    subtitle = "A retro swoop as they fly and a star pop as each one locks in. Uses the UI sounds' volume.",
                    checked = appearance.achievementFlySounds,
                    onChange = { onAppearanceChange(appearance.copy(achievementFlySounds = it)) },
                    modifier = m
                )
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.EmojiEvents,
                    title = "Tips",
                    subtitle = "The set's official guide and what people wrote on the game's RetroAchievements page — in the details window and behind its Tips button.",
                    checked = appearance.showTips,
                    onChange = { onAppearanceChange(appearance.copy(showTips = it)) },
                    modifier = m
                )
            }
        }
        }
    }
    AchievementLookSettings()
    CollapsibleSection("Matching achievements") {
        ShelfCard {
            Text("Achievements", color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Games are matched automatically in the background by hashing each file, the same " +
                    "way the emulator does, and the results are saved on the device. Files that can't " +
                    "be hashed (like .cso or .7z) fall back to their title. Manual IDs always win. " +
                    "A file is only hashed again when it changes; \"Re-check everything\" downloads fresh " +
                    "game lists and re-matches every game using the saved hashes.",
                color = ShelfTheme.textMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            if (isMatchingAchievements) {
                Text("Matching in the background…", color = ShelfTheme.textMuted, fontSize = 12.sp)
            } else {
                ChipFlow {
                    Chip("Check new games", onClick = onAutoMatchAchievements)
                    Chip("Re-check everything", accent = false, onClick = onRehashAchievements)
                }
            }
        }
    }
}

/** Settings › Screens: the second screen's own look and tools (below the per-game screen layouts). */
@Composable
private fun SecondScreenExtras(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit, hasSecondScreen: Boolean) {
        FoldGroup("Screens & home app", summary = if (appearance.useSecondScreen) "Second screen on" else "Second screen off", key = "home_screens") {
            SettingsGrid {
            item { m ->
                SwitchRow(
                    icon = Icons.AutoMirrored.Filled.ScreenShare,
                    title = "Use the second screen",
                    subtitle = if (hasSecondScreen) {
                        "Found — game info, the details window and music show there."
                    } else {
                        "No second screen found on this device right now."
                    },
                    checked = appearance.useSecondScreen,
                    onChange = { onChange(appearance.copy(useSecondScreen = it)) },
                    modifier = m
                )
            }
            }
            Spacer(Modifier.height(12.dp))
        HomeLauncherCard()
        }
        FoldGroup("Second screen art & music", key = "second_look") {
            // Update 189: the look of the whole second screen.
            ShelfCard {
                StyleChoice("Look", com.shelf.launcher.data.Features.SecondLook.values(), com.shelf.launcher.data.Features.secondLook.state.value, { it.label }) {
                    com.shelf.launcher.data.Features.secondLook.value = it
                }
                MutedNote(
                    if (com.shelf.launcher.data.Features.secondLook.state.value == com.shelf.launcher.data.Features.SecondLook.SHOWCASE)
                        "The game's art fills the screen with its logo in the middle; small floating bars at the top (you, the page — tap it for the next one — notifications, battery, time) and a floating dock of round app icons with a home button. Swipe for the other pages."
                    else "The page names along the top, the page in a card and the dock as a bar along the bottom."
                )
            }
            Spacer(Modifier.height(10.dp))
            SecondScreenLookCard(appearance, onChange)
        }
        // Update 177: the Games page's console folders — their look, a folder pack, a picture per console.
        FoldGroup("Second screen Games page", summary = "Folders, tiles, cards or logos; folder packs; the controller there", key = "second_games") {
            SecondGamesSettings()
        }
        FoldGroup("Second screen tools", summary = "Widget Board, Screen Mirror, Wall, wallpaper, virtual second screen", key = "second_tools") {
            SecondScreenToolsSettings(hasSecondScreen)
        }
}

/** Settings › Library › Save Vault: how it looks and which saves it shows. */
@Composable
private fun SaveVaultCard() {
        ShelfCard {
            MutedNote("How the Save Vault (your saves on memory cards, SD cards, floppies or capsules) looks and which saves it shows.")
            Spacer(Modifier.height(8.dp))
            SaveVaultSettings()
        }
}

/** Appearance › Second screen art & music: which picture, the blur sliders, the full-screen player. */
@Composable
private fun SecondScreenLookCard(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("SecondScreenLookCard")
    ShelfCard {
        RowChoice(
            "Art page shows",
            arrayOf(com.shelf.launcher.data.SecondArtType.HERO, com.shelf.launcher.data.SecondArtType.SCREENSHOTS),
            if (appearance.secondArtType == com.shelf.launcher.data.SecondArtType.SCREENSHOTS || appearance.secondArtType == com.shelf.launcher.data.SecondArtType.SLIDESHOW)
                com.shelf.launcher.data.SecondArtType.SCREENSHOTS else com.shelf.launcher.data.SecondArtType.HERO,
            { if (it == com.shelf.launcher.data.SecondArtType.SCREENSHOTS) "Screenshots" else it.label }
        ) { onChange(appearance.copy(secondArtType = it)) }
        RowChoice("Details page shows first", arrayOf(true, false), appearance.secondDetailsCase, { if (it) "The case (tap opens it)" else "The cartridge / disc" }) {
            onChange(appearance.copy(secondDetailsCase = it))
        }
        Spacer(Modifier.height(10.dp))
        // Also in Settings › Screens (it wasn't easy to find there alone).
        SwitchRow(
            icon = androidx.compose.material.icons.Icons.Filled.ViewCarousel,
            title = "Arrows between pages",
            subtitle = "Only the current page's name at the top of the second screen, with an arrow on each side to the others.",
            checked = appearance.secondPageTitleOnly,
            onChange = { onChange(appearance.copy(secondPageTitleOnly = it)) }
        )
        Spacer(Modifier.height(10.dp))
        PercentSlider(
            "Blur of the artwork behind the second screen",
            appearance.secondArtBlur,
            { onChange(appearance.copy(secondArtBlur = it)) }
        )
        Spacer(Modifier.height(14.dp))
        RowChoice(
            "Music page layout",
            com.shelf.launcher.data.SecondMusicLayout.values(),
            appearance.secondMusicLayout,
            { it.label }
        ) { onChange(appearance.copy(secondMusicLayout = it)) }
        MutedNote("Full screen = the player takes over the whole second screen. Classic card = the older layout.")
        if (appearance.secondMusicLayout == com.shelf.launcher.data.SecondMusicLayout.VISUALIZER) {
            Spacer(Modifier.height(6.dp))
            SwitchRow(
                icon = Icons.Filled.MusicNote,
                title = "Album cover on the visualizer",
                subtitle = "Off: just the visualizer, the song's name and controls below it. Themes that show the cover themselves never get a second one.",
                checked = appearance.secondVisualizerArt,
                onChange = { onChange(appearance.copy(secondVisualizerArt = it)) }
            )
        }
        Spacer(Modifier.height(4.dp))
        MutedNote("The blurred album art behind the music players is under Home › Music player.")
    }
    Spacer(Modifier.height(10.dp))
    SettingsGrid {
        item { m -> com.shelf.launcher.ui.spotify.LyricsOptInRow(m) }
        item { m ->
            SwitchRow(
                icon = Icons.Filled.MusicNote,
                title = "Lyrics in the full player",
                subtitle = "Shows the song's lyrics under the controls (synced when available, from LRCLIB).",
                checked = appearance.secondMusicLyrics,
                onChange = { onChange(appearance.copy(secondMusicLyrics = it)) },
                modifier = m
            )
        }
        item { m ->
            SwitchRow(
                icon = Icons.AutoMirrored.Filled.ScreenShare,
                title = "Player over the artwork",
                subtitle = "Off: the small player stays off the Art page and only shows on the Music page.",
                checked = appearance.secondPlayerOverArt,
                onChange = { onChange(appearance.copy(secondPlayerOverArt = it)) },
                modifier = m
            )
        }
    }
}

@Composable
private fun ScreensaverCard(
    settings: ScreensaverSettings,
    onChange: (ScreensaverSettings) -> Unit,
    onPreview: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("ScreensaverCard")
    SettingsGrid {
        item { m ->
            SwitchRow(
                icon = Icons.Filled.Palette,
                title = "Screensaver",
                subtitle = "Your library's art with a clock. Drifts and dims to protect OLED screens.",
                checked = settings.enabled,
                onChange = { onChange(settings.copy(enabled = it)) },
                modifier = m
            )
        }
        if (settings.enabled) item { m ->
            SwitchRow(
                icon = Icons.Filled.MusicNote,
                title = "Show music while playing",
                subtitle = "Shows the song — cover, title, progress — instead of the slideshow.",
                checked = settings.showMusic,
                onChange = { onChange(settings.copy(showMusic = it)) },
                modifier = m
            )
        }
    }
    if (settings.enabled) {
        Spacer(Modifier.height(10.dp))
        ShelfCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("Start after")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 3, 5, 10).forEach { minutes ->
                            Chip("$minutes min", accent = settings.idleMinutes == minutes) {
                                onChange(settings.copy(idleMinutes = minutes))
                            }
                        }
                    }
                }
                Chip("Preview now", accent = false, onClick = onPreview)
            }
            Spacer(Modifier.height(14.dp))
            // What it shows: the library slideshow or an animated theme; with music, the song card or a music theme.
            var pickFor by remember { mutableStateOf<String?>(null) }
            SectionLabel("Without music")
            ChipFlow {
                com.shelf.launcher.data.SaverStyle.values().forEach { st ->
                // (Update 125: "Animated theme" covers every theme, the music visualizers too.)
                    Chip(st.label, accent = settings.style == st) { onChange(settings.copy(style = st)) }
                }
                if (settings.style == com.shelf.launcher.data.SaverStyle.THEME) {
                    val name = settings.theme?.let { n -> runCatching { AppTheme.valueOf(n).label }.getOrNull() } ?: "Your current theme"
                    Chip("Theme: $name…", accent = false) { pickFor = "theme" }
                }
            }
            Spacer(Modifier.height(8.dp))
            SectionLabel("With music")
            ChipFlow {
                com.shelf.launcher.data.SaverMusicStyle.values().forEach { st ->
                    Chip(st.label, accent = settings.musicStyle == st) { onChange(settings.copy(musicStyle = st)) }
                }
                if (settings.musicStyle == com.shelf.launcher.data.SaverMusicStyle.THEME) {
                    val name = settings.musicTheme?.let { n -> runCatching { AppTheme.valueOf(n).label }.getOrNull() } ?: "Now playing"
                    Chip("Theme: $name…", accent = false) { pickFor = "music" }
                }
                Chip(if (settings.stayWithMusic) "✓ Stay on while music plays" else "Stay on while music plays", accent = settings.stayWithMusic) {
                    onChange(settings.copy(stayWithMusic = !settings.stayWithMusic))
                }
            }
            MutedNote("Stay on while music plays: no fade to black and the screen doesn't switch off while a song is playing.")
            pickFor?.let { which ->
                SaverThemePicker(
                    music = which == "music",
                    current = if (which == "music") settings.musicTheme else settings.theme,
                    onPick = { picked ->
                        onChange(if (which == "music") settings.copy(musicTheme = picked) else settings.copy(theme = picked))
                        pickFor = null
                    },
                    onDismiss = { pickFor = null }
                )
            }
            Spacer(Modifier.height(14.dp))
            RowChoice(
                "Second screen while it runs",
                com.shelf.launcher.data.SecondScreenSaver.values(),
                settings.secondScreen,
                { it.label }
            ) { onChange(settings.copy(secondScreen = it)) }
            SectionLabel("Fade to black after")
            MutedNote("True black turns OLED pixels off completely — no burn-in, least battery.")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5 to "5 min", 10 to "10 min", 30 to "30 min", 0 to "Never").forEach { (minutes, label) ->
                    Chip(label, accent = settings.blackoutMinutes == minutes) {
                        onChange(settings.copy(blackoutMinutes = minutes))
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryContent(
    onOpenRomLibrary: () -> Unit,
    onOpenSteamGames: () -> Unit,
    onOpenShortcuts: () -> Unit,
    libraryApps: LibraryAppsSettings,
    onOpenAppsLibrary: () -> Unit,
    onAppsAlwaysUseIcons: (Boolean) -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("LibraryContent")
    LivingLibrarySettings()
    SettingsHeading("Games")
    SettingsGrid {
        item { m -> ActionCard(Icons.Filled.Folder, "ROM folders", "Choose a folder and scan for games", onOpenRomLibrary, m) }
        item { m -> ActionCard(Icons.Filled.VideogameAsset, "Steam games", "Games launched through GameNative", onOpenSteamGames, m) }
    }
    SettingsHeading("Apps")
    SettingsGrid {
        item { m ->
            ActionCard(
                Icons.Filled.Apps,
                "Apps in your library",
                "${libraryApps.mode.label} — choose apps, mark emulators",
                onOpenAppsLibrary,
                m
            )
        }
        item { m ->
            ActionCard(
                Icons.Filled.VideogameAsset,
                "Android games or apps?",
                "Choose which installed apps count as Android games",
                onOpenAppsLibrary,
                m
            )
        }
        item { m -> ActionCard(Icons.Filled.Apps, "Shortcuts", "Pinned apps, reachable from L1", onOpenShortcuts, m) }
        item { m ->
            SwitchRow(
                icon = Icons.Filled.Apps,
                title = "Apps always use their own icon",
                subtitle = "Icon on a blurred backdrop instead of scraped art. Per app: Start › Use app icon as art.",
                checked = libraryApps.alwaysUseIcons,
                onChange = onAppsAlwaysUseIcons,
                modifier = m
            )
        }
    }
}

@Composable
private fun AccountsContent(
    steamGridDbState: ArtConnectionState,
    raState: RaConnectionState,
    raUsername: String?,
    tgdbConnected: Boolean,
    screenScraperConnected: Boolean,
    igdbConnected: Boolean,
    onOpenSteamGridDb: () -> Unit,
    onOpenRetroAchievements: () -> Unit,
    onOpenTgdb: () -> Unit,
    onOpenScreenScraper: () -> Unit,
    onOpenIgdb: () -> Unit,
    rawgConnected: Boolean,
    onOpenRawg: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("AccountsContent")
    SettingsHeading("Artwork")
    SettingsGrid {
        item { m ->
            ConnectionCard(
                "SteamGridDB", "Covers, backgrounds, and logos",
                connected = steamGridDbState == ArtConnectionState.ON, onClick = onOpenSteamGridDb, modifier = m
            )
        }
        item { m ->
            ConnectionCard(
                "TheGamesDB", "Alternative art source, strong retro coverage",
                connected = tgdbConnected, onClick = onOpenTgdb, modifier = m
            )
        }
    }
    SettingsHeading("Details & achievements")
    SettingsGrid {
        item { m ->
            ConnectionCard(
                "ScreenScraper", "ROM art, details and clips by checksum",
                connected = screenScraperConnected, onClick = onOpenScreenScraper, modifier = m
            )
        }
        item { m ->
            ConnectionCard(
                "IGDB", "Summaries, scores, screenshots, how long to beat",
                connected = igdbConnected, onClick = onOpenIgdb, modifier = m
            )
        }
        item { m ->
            ConnectionCard(
                "RetroAchievements",
                raUsername?.let { "Signed in as $it" } ?: "Achievement progress and unlocks",
                connected = raState == RaConnectionState.ON, onClick = onOpenRetroAchievements, modifier = m
            )
        }
        item { m ->
            ConnectionCard(
                "RAWG", "Metacritic scores and average playtime",
                connected = rawgConnected, onClick = onOpenRawg, modifier = m
            )
        }
    }
}

@Composable
private fun EmulatorsContent(onOpenEmulators: () -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("EmulatorsContent")
    ActionCard(
        Icons.Filled.VideogameAsset,
        "Per-console emulators",
        "Assign an app (and core, where needed) to each system",
        onOpenEmulators
    )
}

/**
 * Update 147: one button for everything a game can have (it was spread over Scraping, Game details, Game look and the
 * spines, and the video clips were only ever looked up when a game was shown).
 * Update 151: its own card at the top of Settings › Metadata › Scraping (it used to be a section of its own).
 */
@Composable
private fun EverythingAtOnceCard() {
    ShelfCard {
        val E = com.shelf.launcher.data.EverythingScrape
        val running = E.stage != null
        var parts by remember { mutableStateOf(com.shelf.launcher.data.EverythingScrape.Part.values().toSet()) }
        Text("Fetch everything that's missing", color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        MutedNote(
            "For every game, whatever it doesn't have yet: artwork (the kinds ticked under Scraping), details (ticked under " +
                "Game details), its real case, disc, cartridge and spine scans, screenshots and a video clip for Video previews. " +
                "One after another in the background; it pauses while you play. Games that have something are left as they are."
        )
        Spacer(Modifier.height(8.dp))
        if (running) {
            val prog = E.progress
            Text(
                (E.stage ?: "") + (prog?.let { "  ·  ${it.first} / ${it.second}" } ?: "…"),
                color = ShelfTheme.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            if (prog != null) TaskProgressLine(prog.first, prog.second)
            Spacer(Modifier.height(6.dp))
            Chip("Stop", accent = false) { E.stop?.invoke() }
        } else {
            ChipFlow {
                com.shelf.launcher.data.EverythingScrape.Part.values().forEach { part ->
                    val on = part in parts
                    Chip((if (on) "✓ " else "") + part.label, accent = on) { parts = if (on) parts - part else parts + part }
                }
            }
            Spacer(Modifier.height(8.dp))
            Chip("Fetch everything missing", enabled = parts.isNotEmpty()) { E.run?.invoke(parts) }
            Spacer(Modifier.height(4.dp))
            MutedNote("Real cases, discs and spines and the video clips come from ScreenScraper (Settings › Accounts).")
        }
        // Update 148: the clips kept on the device, to play without Wi-Fi and start at once.
        Spacer(Modifier.height(10.dp))
        val F = com.shelf.launcher.data.Features
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var clipStats by remember { mutableStateOf(0 to 0L) }
        LaunchedEffect(F.clipsOffline.state.value, E.stage) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.shelf.launcher.data.ClipStore.init(ctx)
                clipStats = com.shelf.launcher.data.ClipStore.count() to com.shelf.launcher.data.ClipStore.usedBytes()
            }
        }
        SwitchRow(
            Icons.Filled.CloudDownload, "Save clips on the device",
            "The preview clips are kept on this handheld, so they play without Wi-Fi and start at once (a few MB each). " +
                "Off: they stream each time. Saved now: ${clipStats.first} clips, ${clipStats.second / (1024 * 1024)} MB.",
            F.clipsOffline.state.value, { on -> F.clipsOffline.value = on; if (on) E.saveClips?.invoke() }
        )
        if (F.clipsOffline.state.value) {
            Spacer(Modifier.height(6.dp))
            RowChoice("Up to", arrayOf(500, 1024, 2048, 4096), F.clipsMaxMb.state.value, { if (it >= 1024) "${it / 1024} GB" else "$it MB" }) { F.clipsMaxMb.value = it }
            Chip("Delete the saved clips", accent = false) {
                com.shelf.launcher.data.ClipStore.clear(); clipStats = 0 to 0L
            }
        }
    }
}

@Composable
private fun MetadataContent(
    artSource: ArtSource,
    metadataSource: MetadataSource,
    scrapeDone: Int,
    scrapeTotal: Int,
    isMatchingAchievements: Boolean,
    onSelectArtSource: (ArtSource) -> Unit,
    onSelectMetadataSource: (MetadataSource) -> Unit,
    onScrapeMissing: () -> Unit,
    onRescrapeAll: () -> Unit,
    onCancelScrape: () -> Unit,
    onAutoMatchAchievements: () -> Unit,
    onRehashAchievements: () -> Unit,
    metadataScrapeDone: Int,
    metadataScrapeTotal: Int,
    savedDetailsCount: Int,
    onScrapeMetadata: () -> Unit,
    onCancelMetadataScrape: () -> Unit,
    onClearMetadata: () -> Unit,
    onRefreshAllMetadata: () -> Unit,
    scrapeOptions: com.shelf.launcher.data.ScrapeOptions,
    onScrapeOptionsChange: (com.shelf.launcher.data.ScrapeOptions) -> Unit,
    rawgConnected: Boolean
) {
    com.shelf.launcher.Breadcrumbs.mark("MetadataContent")
    // Update 151: "Everything at once" is no longer its own section at the top of the page (it felt out of place
    // there): its card is the first thing under Scraping (EverythingAtOnceCard).
    CollapsibleSection("Artwork source", startExpanded = false) {
        ShelfCard {
            Text("Artwork source", color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tried first. If it has nothing for a game, the other one fills in.",
                color = ShelfTheme.textMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            ArtSource.values().forEach { source ->
                RadioLine(source.label, source == artSource) { onSelectArtSource(source) }
            }
        }
    }
    // Update 137: each look's pictures from their own source.
    CollapsibleSection("Artwork source per look", startExpanded = false) {
        ShelfCard {
            SectionLabel("Artwork source per look")
            MutedNote("Where each kind of picture is fetched from first; the main artwork source and then the others fill in what it doesn't have. Takes effect for new games, and for all of them with \"Fetch all artwork again\" below.")
            val F = com.shelf.launcher.data.Features
            listOf(
                Triple("Covers (Covers look)", F.artSourceCover, "cover"),
                Triple("Wide capsules (Capsule look)", F.artSourceWide, "wide"),
                Triple("Backgrounds (Hero look, behind the games)", F.artSourceHero, "hero"),
                Triple("Logos", F.artSourceLogo, "logo")
            ).forEach { (label, pref, _) ->
                Spacer(Modifier.height(8.dp))
                Text(label, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                val now = pref.state.value
                ChipFlow {
                    Chip((if (now.isBlank()) "✓ " else "") + "Same as main (${artSource.label.substringBefore(" (")})", accent = now.isBlank()) { pref.value = "" }
                    ArtSource.values().forEach { src ->
                        val on = now == src.name
                        Chip((if (on) "✓ " else "") + src.label.substringBefore(" ("), accent = on) { pref.value = src.name }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Real cases, discs, cartridges, labels and spines", color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            MutedNote("Box art, Cartridge / disc, Discs only and Spines looks: only ScreenScraper has these scans (and RetroAchievements a box front). Connect ScreenScraper under Accounts; they're fetched as you reach each game.")
            Spacer(Modifier.height(8.dp))
            ChipFlow { Chip("Fetch all artwork again") { onRescrapeAll() } }
        }
    }
    CollapsibleSection("Details source", startExpanded = false) {
        ShelfCard {
            Text("Details source", color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Fills the window you open with A on a game.",
                color = ShelfTheme.textMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            MetadataSource.values().forEach { source ->
                RadioLine(source.label, source == metadataSource, source.blurb) { onSelectMetadataSource(source) }
            }
        }
    }
    CollapsibleSection("Game details", startExpanded = true) {
        ShelfCard {
            Text("Game details", color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (metadataScrapeTotal > 0) "Downloading: $metadataScrapeDone / $metadataScrapeTotal"
                else "$savedDetailsCount games saved on this device. Saved details open instantly and work offline.",
                color = ShelfTheme.textMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            SectionLabel("What to download")
            Spacer(Modifier.height(6.dp))
            ToggleChips(
                com.shelf.launcher.data.DetailField.values().toList(),
                scrapeOptions.details,
                label = { it.label }
            ) { onScrapeOptionsChange(scrapeOptions.copy(details = it)) }
            if (com.shelf.launcher.data.DetailField.METACRITIC in scrapeOptions.details && !rawgConnected) {
                Spacer(Modifier.height(6.dp))
                MutedNote("Metacritic scores come from RAWG — connect it under Accounts to get them.")
            }
            Spacer(Modifier.height(12.dp))
            ChipFlow {
                if (metadataScrapeTotal > 0) {
                    Chip("Stop", onClick = onCancelMetadataScrape)
                } else {
                    Chip("Download missing", onClick = onScrapeMetadata)
                    Chip("Refresh all", accent = false, onClick = onRefreshAllMetadata)
                    Chip("Clear saved", accent = false, onClick = onClearMetadata)
                }
            }
            if (metadataScrapeTotal == 0) {
                Spacer(Modifier.height(6.dp))
                MutedNote(
                    "Download missing visits games without one of the ticked details (like a Metacritic score). " +
                        "Refresh all looks every game up again. Only ticked details are changed."
                )
            }
        }
    }
    CollapsibleSection("Scraping", startExpanded = true) {
        // Update 151: "Fetch everything that's missing" (and the saved clips) on top of Scraping.
        EverythingAtOnceCard()
        Spacer(Modifier.height(10.dp))
        ShelfCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Scraping", color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (scrapeTotal > 0) "In progress: $scrapeDone / $scrapeTotal games (each one's pictures saved on the device as it's found)"
                        else "Runs in the background and saves as it goes.",
                        color = ShelfTheme.textMuted, fontSize = 12.sp
                    )
                }
                if (scrapeTotal > 0) {
                    Chip("Stop", onClick = onCancelScrape)
                }
            }
            if (scrapeTotal > 0) TaskProgressLine(scrapeDone, scrapeTotal)
            if (scrapeTotal == 0) {
                Spacer(Modifier.height(10.dp))
                SectionLabel("What to download")
                Spacer(Modifier.height(6.dp))
                ToggleChips(
                    com.shelf.launcher.data.ArtField.values().toList(),
                    scrapeOptions.art,
                    label = { it.label }
                ) { onScrapeOptionsChange(scrapeOptions.copy(art = it)) }
                Spacer(Modifier.height(8.dp))
                Chip(
                    if (scrapeOptions.manuals) "✓ Manuals" else "Manuals",
                    accent = scrapeOptions.manuals
                ) { onScrapeOptionsChange(scrapeOptions.copy(manuals = !scrapeOptions.manuals)) }
                MutedNote(
                    "Scanned game manuals (PDF) from ScreenScraper, saved on the device so they open instantly. " +
                        "Fetched with the artwork, the first time a game is scraped. A manual next to the ROM always wins."
                )
                Spacer(Modifier.height(12.dp))
                ChipFlow {
                    Chip("Scrape missing", onClick = onScrapeMissing)
                    Chip("Re-scrape all", accent = false, onClick = onRescrapeAll)
                }
                Spacer(Modifier.height(6.dp))
                MutedNote("Only the ticked kinds of artwork are fetched or replaced; the rest stays as it is.")
            }
        }
    }
}

/** Discord "Playing …" status. */
@Composable
private fun DiscordCard(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
    val context = LocalContext.current
    val installed = remember {
        runCatching { context.packageManager.getPackageInfo("com.discord", 0); true }.getOrDefault(false)
    }
    fun activity(): android.app.Activity? {
        // Dialogs wrap the context; find the activity underneath.
        var c: android.content.Context? = context
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        return c as? android.app.Activity
    }
    // The link's state changes in the background (sign-in happens in Discord) — check it often.
    var link by remember { mutableStateOf(com.shelf.launcher.data.DiscordPresence.linkState()) }
    var status by remember { mutableStateOf(com.shelf.launcher.data.DiscordPresence.status()) }
    LaunchedWhileActive {
        while (true) {
            link = com.shelf.launcher.data.DiscordPresence.linkState()
            status = com.shelf.launcher.data.DiscordPresence.status()
            kotlinx.coroutines.delay(700)
        }
    }
    SettingsHeading("Discord")
    ShelfCard {
        SwitchRow(
            icon = Icons.Filled.VideogameAsset,
            title = "Show what you're playing on Discord",
            subtitle = "\"Playing GoldenEye 007 · Nintendo 64\" with a timer on your profile, cleared when you're back in Voltcap.",
            checked = appearance.discordPresence,
            onChange = { on ->
                onChange(appearance.copy(discordPresence = on))
                if (on) activity()?.let { com.shelf.launcher.data.DiscordPresence.attach(it) }
                else { com.shelf.launcher.data.DiscordPresence.setEnabled(false); com.shelf.launcher.data.DiscordPresence.clear() }
            }
        )
        if (appearance.discordPresence) {
            Spacer(Modifier.height(10.dp))
            SwitchRow(
                icon = Icons.Filled.EmojiEvents,
                title = "What you're doing in the game",
                subtitle = "For games on RetroAchievements: the game's own status under its name — \"Route 3 · 50 Pokémon caught\" — updated about every minute.",
                checked = appearance.discordRichPresence,
                onChange = { onChange(appearance.copy(discordRichPresence = it)) }
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(active = link == 2 && status == 3)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        link == 2 && status == 3 -> "Connected — your status shows while you play."
                        link == 2 || link == 1 -> "Signing in to Discord…"
                        link == 3 -> "Couldn't connect: " + com.shelf.launcher.data.DiscordPresence.linkError()
                        else -> "Not connected yet."
                    },
                    color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))
            ChipFlow {
                if (link != 2) {
                    Chip(if (link == 1) "Waiting for Discord…" else "Connect Discord", enabled = link != 1) {
                        activity()?.let { com.shelf.launcher.data.DiscordPresence.attach(it) }
                        com.shelf.launcher.data.DiscordPresence.connect()
                    }
                } else {
                    Chip("Disconnect", accent = false) { com.shelf.launcher.data.DiscordPresence.disconnect() }
                }
            }
            Spacer(Modifier.height(8.dp))
            MutedNote(
                "Connect once: Discord opens (the app, or a browser tab), you tap Authorize, and you're sent back " +
                    "here. Voltcap then signs in by itself every time. " +
                    (if (!installed) "Installing the Discord app makes signing in quicker. " else "") +
                    "Still not showing? Discord › Settings › Content & Social › turn on sharing your activity."
            )
        }
    }
}

/** Export / import everything, and the first-start setup again. */
@Composable
private fun BackupContent(onRunSetup: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    /** A backup being looked at in the Import Preview (from a file, Drive or a nearby device). */
    var incoming by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var includeGameData by remember { mutableStateOf(false) }
    var includeDeviceSetup by remember { mutableStateOf(false) }
    var includeAccounts by remember { mutableStateOf(true) }
    // Writing / reading a backup (with game data it can be large) runs off the main thread.
    val backupScope = androidx.compose.runtime.rememberCoroutineScope()
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) backupScope.launch {
            message = "Saving…"
            message = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { com.shelf.launcher.data.Backup.export(context, uri, includeGameData, includeDeviceSetup, includeAccounts) }
            }.fold({ "Saved ($it settings files). Copy it to the other device and import it there." }, { "Couldn't save: ${it.message}" })
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }
    ShelfCard {
        SectionLabel("Move to another device")
        MutedNote(
            "One file with your settings — game data only if you tick it below. " +
                "Importing replaces the settings on the other device; its own game data stays unless the file has some. " +
                "Logins and API keys go along only with \"Include accounts\" below."
        )
        Spacer(Modifier.height(10.dp))
        Chip(
            if (includeGameData) "✓ Include game data" else "Include game data",
            accent = includeGameData
        ) { includeGameData = !includeGameData }
        Spacer(Modifier.height(4.dp))
        MutedNote(
            if (includeGameData) "Game details, artwork choices, favourites, playtime and achievement matches go along — " +
                "only useful if the other device has the same games in the same folders."
            else "Settings and accounts only. Each device keeps its own game details, favourites and playtime."
        )
        Spacer(Modifier.height(10.dp))
        Chip(
            if (includeDeviceSetup) "✓ Include this device's setup (folders, emulators, screens)"
            else "Include this device's setup (folders, emulators, screens)",
            accent = includeDeviceSetup
        ) { includeDeviceSetup = !includeDeviceSetup }
        Spacer(Modifier.height(4.dp))
        MutedNote("Only useful for restoring on this same device; importing on another device skips it.")
        Spacer(Modifier.height(10.dp))
        Chip(
            if (includeAccounts) "✓ Include accounts (logins & API keys)" else "Include accounts (logins & API keys)",
            accent = includeAccounts
        ) { includeAccounts = !includeAccounts }
        Spacer(Modifier.height(4.dp))
        MutedNote(
            if (includeAccounts) "RetroAchievements, SteamGridDB, TheGamesDB, ScreenScraper, IGDB, RAWG and Steam go along, so the other device is signed in right away. " +
                "The file then contains your keys: keep it private. Google Drive, Spotify and Discord sign in on each device."
            else "No logins in the file: sign in again on the other device."
        )
        Spacer(Modifier.height(10.dp))
        // Default place: Internal storage › Voltcap › Voltcap settings sync › voltcap-backup.json - one file,
        // replaced on every export, so a sync app (or a USB copy) always has the latest.
        val canFolder = com.shelf.launcher.data.AppStorage.canUseRootFolder()
        val driveConnect = rememberDriveConnect { message = it }
        var syncExists by remember { mutableStateOf(canFolder && com.shelf.launcher.data.Backup.syncFile().isFile) }
        ChipFlow {
            Chip("Export") {
                if (canFolder) backupScope.launch {
                    message = "Saving…"
                    message = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { com.shelf.launcher.data.Backup.exportToSyncFolder(context, includeGameData, includeDeviceSetup, includeAccounts) }
                    }.fold({ syncExists = true; "Saved ($it settings files) to Internal storage › Voltcap › Voltcap settings sync › voltcap-backup.json." },
                        { "Couldn't save: ${it.message}" })
                } else {
                    message = "To save into Internal storage › Voltcap › Voltcap settings sync, allow \"All files access\" for Voltcap " +
                        "(below), or pick a place with Export to…"
                }
            }
            // Update 144: the same backup to Google Drive, so a new install takes it back in its first-start setup
            // (Welcome › Yes — from Google Drive). Signs in to Drive first when needed.
            Chip("Export to Google Drive", accent = false) {
                message = "Connecting to Google Drive…"
                driveConnect {
                    backupScope.launch {
                        message = "Saving to Google Drive…"
                        message = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching { com.shelf.launcher.data.Backup.exportToDrive(context, includeGameData, includeDeviceSetup, includeAccounts) }
                        }.fold({ "Saved ($it settings files) to Google Drive › Voltcap › voltcap-backup.json. A new install takes it back in its first-start setup." },
                            { "Couldn't save to Google Drive: ${it.message}" })
                    }
                }
            }
            if (syncExists) Chip("Import from Voltcap settings sync", accent = false) {
                pendingImport = android.net.Uri.fromFile(com.shelf.launcher.data.Backup.syncFile())
            }
            Chip("Export to…", accent = false) { exportFile.launch("voltcap-backup.json") }
            Chip("Import…", accent = false) { importFile.launch(arrayOf("application/json", "text/plain", "*/*")) }
            if (!canFolder) Chip("Allow all files access", accent = false) {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            .setData(android.net.Uri.parse("package:" + context.packageName))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
        message?.let {
            Spacer(Modifier.height(8.dp))
            MutedNote(it)
        }
        // Update 149: the same Drive backup made by itself, every day or week.
        Spacer(Modifier.height(12.dp))
        run {
            val F = com.shelf.launcher.data.Features
            val every = F.autoBackup.state.value
            RowChoice("Automatic backup to Google Drive", com.shelf.launcher.data.AutoBackupWorker.Every.values(), every, { it.label }) { e ->
                F.autoBackup.value = e
                runCatching { com.shelf.launcher.data.AutoBackupWorker.schedule(context) }
                // (Signs in to Drive now, so the first one can run.)
                if (e != com.shelf.launcher.data.AutoBackupWorker.Every.OFF && !com.shelf.launcher.data.CloudSync.state.value.settings.connected) {
                    message = "Connecting to Google Drive…"
                    driveConnect { message = "Google Drive connected: the backup runs by itself from now on." }
                }
            }
            if (every != com.shelf.launcher.data.AutoBackupWorker.Every.OFF) {
                SwitchRow(
                    Icons.Filled.CloudDownload, "With accounts",
                    "Logins and API keys go into the automatic backup too, so a new device is signed in right away. The file is in your own Google Drive.",
                    F.autoBackupAccounts.state.value, { F.autoBackupAccounts.value = it }
                )
                val last = F.autoBackupAt.state.value.toLongOrNull()
                MutedNote(
                    "Settings, game data" + (if (F.autoBackupAccounts.state.value) " and accounts" else "") +
                        " to Google Drive › Voltcap › voltcap-backup.json, with Wi-Fi and enough battery, never during a game. " +
                        (if (last != null) "Last one: " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(last)) + "."
                        else "None yet.")
                )
            }
        }
        // A picked file goes through the Import Preview first (nothing is replaced until confirmed).
        androidx.compose.runtime.LaunchedEffect(pendingImport) {
            val uri = pendingImport ?: return@LaunchedEffect
            pendingImport = null
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { com.shelf.launcher.data.Backup.readDocument(context, uri) } }
                .onSuccess { incoming = it }
                .onFailure { message = "Couldn't read it: ${it.message}" }
        }
    }
    Spacer(Modifier.height(12.dp))
    SettingsShareCard(onReceived = { incoming = it })
    incoming?.let { root ->
        val preview = remember(root) { runCatching { com.shelf.launcher.data.SettingsTransfer.preview(context, root) }.getOrNull() }
        Spacer(Modifier.height(12.dp))
        if (preview == null) MutedNote("That backup couldn't be read.")
        else ImportPreviewCard(
            preview,
            onApply = { excluded ->
                incoming = null
                // Writing the settings runs off the main thread; the restart follows on it (update 91).
                backupScope.launch {
                    message = "Importing…"
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { com.shelf.launcher.data.SettingsTransfer.apply(context, preview, excluded) }
                    }
                        .onSuccess { com.shelf.launcher.data.Backup.restart(context) }
                        .onFailure { message = "Couldn't import: ${it.message}" }
                }
            },
            onCancel = { incoming = null }
        )
    }
    AppStorageCard()
    Spacer(Modifier.height(12.dp))
    BackupProfilesCard()
    Spacer(Modifier.height(12.dp))
    ShelfCard {
        SectionLabel("First-start setup")
        MutedNote("The guided setup from the first start: ROM folders, accounts, home screen.")
        Spacer(Modifier.height(10.dp))
        Chip("Run setup again", accent = false, onClick = onRunSetup)
    }
    Spacer(Modifier.height(12.dp))
    ResetAppCard()
}

/**
 * A settings group that folds open and shut, so a long page is a short list
 * of headings. Which groups are open is remembered while the app runs.
 */
@Composable
internal fun CollapsibleSection(title: String, startExpanded: Boolean = false, content: @Composable () -> Unit) {
    // Sub-pages: a section's page first lists its groups; picking one shows only that group
    // (‹ or B goes back). startExpanded is kept for callers but no longer used.
    @Suppress("UNUSED_VARIABLE") val unused = startExpanded
    val shape = uiRounded(14.dp)
    val open = openSubpage
    when (open) {
        null -> {
            val focus = remember { androidx.compose.ui.focus.FocusRequester() }
            // Coming back from this sub-page: its row gets the focus again.
            LaunchedEffect(Unit) {
                if (lastSubpage == title) { kotlinx.coroutines.delay(30); runCatching { focus.requestFocus() } }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .voltSurface(shape, alpha = 0.75f)
                    .shelfClickable(shape) { lastSubpage = title; setSubpage(title) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val groupIcon = groupIconFor(title)
                if (groupIcon != null) {
                    IconBadge(groupIcon, size = 30.dp)
                    Spacer(Modifier.width(12.dp))
                } else if (VoltUi.on) {
                    Box(Modifier.size(width = 3.dp, height = 18.dp).clip(uiRounded(50)).background(ShelfTheme.accent.copy(alpha = 0.8f)))
                    Spacer(Modifier.width(12.dp))
                }
                Text(title, color = ShelfTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("›", color = if (VoltUi.on) ShelfTheme.accent else ShelfTheme.textMuted, fontSize = 20.sp)
            }
        }
        title -> {
            val focus = remember { androidx.compose.ui.focus.FocusRequester() }
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(30); runCatching { focus.requestFocus() } }
            Row(
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .clip(shape)
                    .shelfClickable(shape) { setSubpage(null) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("‹", color = ShelfTheme.accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(title, color = ShelfTheme.colors.textPrimary, fontSize = 17.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(17.sp), maxFontSize = 17.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (VoltUi.on) VoltageLine(Modifier.fillMaxWidth().padding(horizontal = 6.dp))
            Column(Modifier.fillMaxWidth().padding(top = 6.dp)) { content() }
        }
        else -> Unit
    }
}

/**
 * Settings search: every setting whose name (or its group / page / section) has all the words typed, best matches
 * first, each with where it lives. Picking one jumps there (see [SettingsIndex], made by tools/settings_index/gen.py).
 */
@Composable
private fun SettingsSearchResults(query: String, onPick: (SettingsIndex.E) -> Unit) {
    val sections = remember { CATEGORIES.map { it.label }.toSet() }
    val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    val hits = remember(query) {
        SettingsIndex.all.asSequence()
            .filter { it.section in sections }
            .mapNotNull { e ->
                val label = e.label.lowercase()
                val all = listOfNotNull(e.label, e.foldTitle, e.subpage, e.section).joinToString(" ").lowercase()
                if (!words.all { it in all }) return@mapNotNull null
                val score = when {
                    label.startsWith(words.first()) -> 0
                    words.all { it in label } -> 1
                    else -> 2
                }
                score to e
            }
            .sortedBy { it.first }
            .map { it.second }
            .take(60)
            .toList()
    }
    if (hits.isEmpty()) {
        MutedNote("Nothing called that. Try another word (e.g. \"blur\", \"lyrics\", \"mixer\").")
        return
    }
    MutedNote("${hits.size} setting${if (hits.size == 1) "" else "s"} — pick one to go there.")
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        hits.forEach { e ->
            val shape = uiRounded(12.dp)
            Column(
                Modifier.fillMaxWidth().voltSurface(shape, alpha = 0.7f).shelfClickable(shape) { onPick(e) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(e.label, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(14.sp), maxFontSize = 14.sp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(e.section, e.subpage, e.foldTitle?.takeIf { it != e.label && it != e.subpage }).distinct().joinToString(" › "),
                    color = ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Open / closed settings groups, kept for as long as the app runs. */
private val openSections = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()

/** The settings sub-page that is open (null = the section's list of sub-pages). */
private var openSubpage by androidx.compose.runtime.mutableStateOf<String?>(null)
/** The sub-page last opened, so its row is focused again on the way back. */
private var lastSubpage: String? = null
/** Which sub-page each section had open, so coming back into Settings continues where you were. */
private val subpageMemory = mutableMapOf<String, String?>()
private var currentSection: String = ""

/**
 * Opens Settings straight at a setting (R1 › hold a tile): [section]'s [subpage] and its [foldKey] group open.
 * The caller then opens Settings with [section] as the start category.
 */
internal fun prepareSettingsJump(section: String, subpage: String?, foldKey: String?, label: String? = null) {
    subpageMemory[section] = subpage
    foldKey?.let { openFold(it) }
    SettingsJump.pending = label
}

private fun setSubpage(title: String?) {
    openSubpage = title
    subpageMemory[currentSection] = title
}

/** The theme browser's place: which group is open and the theme you were on (kept while the app runs). */
private object ThemeBrowserMemory {
    var group by androidx.compose.runtime.mutableStateOf<com.shelf.launcher.ui.theme.ThemeGroup?>(null)
    var lastGroup: com.shelf.launcher.ui.theme.ThemeGroup? = null
    var focused: AppTheme? = null
    /** Theme browser filter: ALL, FAV, or a ThemeKind name. */
    var filter by androidx.compose.runtime.mutableStateOf("ALL")
}

// ---- Shared pieces ----

/** Tick-box chips, a few per row: ticked ones are filled with the accent. */
@Composable
private fun <T> ToggleChips(
    all: List<T>,
    selected: Set<T>,
    label: (T) -> String,
    onChange: (Set<T>) -> Unit
) {
    ChipFlow {
        all.forEach { item ->
            val on = item in selected
            Chip((if (on) "✓ " else "") + label(item), accent = on) {
                onChange(if (on) selected - item else selected + item)
            }
        }
    }
}

@Composable
private fun NavItem(entry: SettingsCategory, selected: Boolean, onClick: () -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("NavItem")
    val volt = VoltUi.on
    val accent = ShelfTheme.accent
    Row(
        Modifier
            .fillMaxWidth()
            // Volt: the picked section glows (a soft accent halo behind the gold fill).
            .then(if (volt && selected) Modifier.drawBehind {
                drawRoundRect(accent.copy(alpha = 0.22f), topLeft = androidx.compose.ui.geometry.Offset(-3.dp.toPx(), -3.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(15.dp.toPx()))
            } else Modifier)
            .clip(uiRounded(12.dp))
            .background(
                if (selected && volt) voltFill()
                else androidx.compose.ui.graphics.SolidColor(if (selected) accent else Color.Transparent)
            )
            .shelfClickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            entry.icon, null,
            tint = if (selected) onAccentColor() else ShelfTheme.accent.copy(alpha = 0.85f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            entry.label,
            color = if (selected) onAccentColor() else ShelfTheme.colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}


@Composable
private fun ThemeSwatches(current: AppTheme, onSelect: (AppTheme) -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("ThemeSwatches")
    // The theme the D-pad (or a finger) is on: previewed live above the list without applying it.
    var hovered by remember { mutableStateOf<AppTheme?>(null) }
    val shown = hovered ?: current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The live preview sits right above the choices (the list scrolls under it, inside its own box),
        // so it's always in view while browsing.
        Box(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(uiRounded(com.shelf.launcher.ui.theme.Ds.rMedium))
                .border(
                    2.dp,
                    Brush.linearGradient(listOf(shown.colors.accent, shown.colors.secondary)),
                    uiRounded(com.shelf.launcher.ui.theme.Ds.rMedium)
                )
        ) {
            androidx.compose.animation.Crossfade(targetState = shown, animationSpec = androidx.compose.animation.core.tween(250), label = "themePreview") { th ->
                ThemeScene(th.motif, th.colors, Modifier.fillMaxSize())
            }
            Row(
                Modifier.align(Alignment.BottomStart).padding(8.dp)
                    .clip(uiRounded(50)).background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThemeBadge(shown, 20.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    shown.label + " · " + shown.group.label +
                        (if (!shown.recolorable) " · ⊘ own colours" else if (shown.mostlyOwnColors) " · ◐ mostly own colours" else "") +
                        if (shown != current) "   A: use it" else "",
                    color = Color.White, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), fontWeight = FontWeight.SemiBold, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        // Favourite (★, used by theme rotation) for the theme being previewed, and filters.
        val favs = com.shelf.launcher.data.Features.favoriteThemes.state.value
        ChipFlow {
            val fav = shown.name in favs
            Chip(if (fav) "★ ${shown.label} is a favorite" else "☆ Add ${shown.label} to favorites", accent = fav) {
                com.shelf.launcher.data.Features.favoriteThemes.toggle(shown.name)
            }
            Chip("▶ Full preview", accent = false) { ScenePreview.theme = shown }
        }
        ScenePreview.theme?.let { th -> FullThemePreview(th) { ScenePreview.theme = null } }
        // Update 109: which devices the theme is best on (every theme), and for a video loop how much of the device it uses.
        ThemeDeviceNote(shown)
        ChipFlow {
            listOf("ALL" to "All", "FAV" to "★ Favorites", "STATIC" to "Static", "CALM" to "Calm", "ANIMATED" to "Animated").forEach { (k, l) ->
                Chip(l, accent = ThemeBrowserMemory.filter == k) { ThemeBrowserMemory.filter = k }
            }
        }
        val filtered: List<AppTheme>? = when (val f = ThemeBrowserMemory.filter) {
            "ALL" -> null
            "FAV" -> AppTheme.values().filter { it.name in favs }
            else -> AppTheme.values().filter { it.kind.name == f }
        }
        if (filtered != null) {
            if (filtered.isEmpty()) MutedNote(if (ThemeBrowserMemory.filter == "FAV") "No favorites yet: focus a theme and pick ☆ above." else "Nothing here yet.")
            else androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(ThemeTileWidth),
                modifier = Modifier.fillMaxWidth().height(340.dp),
                horizontalArrangement = Arrangement.spacedBy(ThemeTileGap),
                verticalArrangement = Arrangement.spacedBy(ThemeTileGap)
            ) {
                items(filtered.size, key = { filtered[it].name }) { i ->
                    val theme = filtered[i]
                    ThemeTile(theme, theme == current, onFocus = { hovered = it }, focusFirst = i == 0, favorite = theme.name in favs) { onSelect(theme); hovered = null }
                }
            }
        }
        // Every theme by what it is: first the groups (like the Appearance pages), then the group's
        // themes, each with a picture of its own scene, its icon and its colours. The open group and the
        // theme you were on are remembered, so coming back continues there.
        val sections = remember { com.shelf.launcher.ui.theme.ThemeGroup.values().map { g -> g to AppTheme.values().filter { it.group == g } }.filter { it.second.isNotEmpty() } }
        val openGroup = ThemeBrowserMemory.group
        if (filtered != null) {
            // The filtered grid above replaces the groups.
        } else if (openGroup == null) {
            Column(Modifier.fillMaxWidth().height(380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sections.forEach { (group, themes) ->
                    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                    LaunchedEffect(Unit) {
                        if (ThemeBrowserMemory.lastGroup == group || (ThemeBrowserMemory.lastGroup == null && current.group == group)) {
                            kotlinx.coroutines.delay(40); runCatching { focus.requestFocus() }
                        }
                    }
                    val shape = uiRounded(com.shelf.launcher.ui.theme.Ds.rMedium)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .clip(shape)
                            .background(ShelfTheme.colors.surfaceRaised.copy(alpha = 0.75f))
                            .shelfClickable(shape) { ThemeBrowserMemory.lastGroup = group; ThemeBrowserMemory.group = group }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // A few of the group's badges as its icon.
                        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                            themes.take(3).forEach { ThemeBadge(it, 24.dp) }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(group.label, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (current.group == group) Text("● in use  ", color = ShelfTheme.accent, fontSize = 11.sp)
                        Text("${themes.size}", color = ShelfTheme.textMuted, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("›", color = ShelfTheme.textMuted, fontSize = 20.sp)
                    }
                }
            }
        } else {
            val themes = sections.firstOrNull { it.first == openGroup }?.second.orEmpty()
            val back = remember { androidx.compose.ui.focus.FocusRequester() }
            val shape = uiRounded(com.shelf.launcher.ui.theme.Ds.rSmall)
            Row(
                Modifier
                    .fillMaxWidth()
                    .focusRequester(back)
                    .clip(shape)
                    .shelfClickable(shape) { ThemeBrowserMemory.group = null }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("‹", color = ShelfTheme.accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(openGroup.label, color = ShelfTheme.colors.textPrimary, fontSize = 15.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(15.sp), maxFontSize = 15.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // B goes back to the groups first.
            androidx.activity.compose.BackHandler { ThemeBrowserMemory.group = null }
            val resume = ThemeBrowserMemory.focused?.takeIf { it.group == openGroup } ?: current.takeIf { it.group == openGroup } ?: themes.firstOrNull()
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(ThemeTileWidth),
                state = androidx.compose.foundation.lazy.grid.rememberLazyGridState(initialFirstVisibleItemIndex = themes.indexOf(resume).coerceAtLeast(0)),
                modifier = Modifier.fillMaxWidth().height(340.dp),
                horizontalArrangement = Arrangement.spacedBy(ThemeTileGap),
                verticalArrangement = Arrangement.spacedBy(ThemeTileGap)
            ) {
                items(themes.size, key = { themes[it].name }) { i ->
                    val theme = themes[i]
                    ThemeTile(
                        theme, theme == current,
                        onFocus = { hovered = it; if (it != null) ThemeBrowserMemory.focused = it },
                        focusFirst = theme == resume,
                        favorite = theme.name in favs
                    ) { onSelect(theme); hovered = null }
                }
            }
        }
        MutedNote("Every theme has its own scene: pick \"Animated theme\" under Behind the dock to use it as the home background. Its colours, blur and dim are in Theme look below. ⊘ keeps its own colours (album cover, game art or a picture); ◐ mostly its own: custom colours only tint it.")
    }
}

/** Under the theme preview (update 109): "Best on …" for every theme. (Update 119: the video loop themes are gone.) */
@Composable
private fun ThemeDeviceNote(theme: AppTheme) {
    Column(
        Modifier.fillMaxWidth().clip(uiRounded(10.dp)).background(ShelfTheme.colors.surfaceRaised.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text("Best on: " + themeBestOn(theme), color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

/** Which devices a theme is best on (Theme browser, update 109): every theme says so. */
internal fun themeBestOn(theme: AppTheme): String {
    val m = theme.motif
    return when {
        m.custom -> "Depends on your pack: Voltcap slows it to 30 fps if it proves heavy."
        m.still || theme.kind == com.shelf.launcher.data.Features.ThemeKind.STATIC -> "Any device: a still picture costs nothing, even on budget handhelds."
        m.showcase -> "Any device: it's your game's own art, gently moving."
        m.reactive -> "Mid-range and up (Retroid Pocket 4 Pro / 5, AYN Odin 2 family) for smooth music visuals; on budget handhelds pick Lite effects."
        theme.kind == com.shelf.launcher.data.Features.ThemeKind.CALM -> "Any device: slow, quiet motion, light on battery."
        else -> "Most handhelds (Retroid Pocket 4 Pro / 5, AYN Odin 2 family); on budget ones (Pocket Classic, Anbernic RG) Lite effects keeps it smooth."
    } + if (android.os.Build.VERSION.SDK_INT < 33 && !m.still) " (Android 12 and older draw a simpler version.)" else ""
}

/** One theme in the browser: a still of its scene, framed in its own colours, its icon and its name. */
@Composable
private fun ThemeTile(theme: AppTheme, selected: Boolean, onFocus: (AppTheme?) -> Unit, focusFirst: Boolean = false, favorite: Boolean = false, onClick: () -> Unit) {
    val requester = remember { androidx.compose.ui.focus.FocusRequester() }
    if (focusFirst) LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { requester.requestFocus() } }
    val context = androidx.compose.ui.platform.LocalContext.current
    val c = theme.colors
    val thumb by androidx.compose.runtime.produceState(ThemeThumbs.cached(theme), theme) {
        if (value == null) value = ThemeThumbs.get(context, theme)
    }
    val shape = uiRounded(10.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(ThemeTileWidth)
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onFocus(theme) }
            .shelfClickable(shape) { onClick() }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ThemeTileWidth * 0.62f)
                .clip(shape)
                .background(Brush.linearGradient(listOf(c.background, c.accent.copy(alpha = 0.6f), c.secondary.copy(alpha = 0.5f))))
                .border(
                    width = if (selected) 2.5.dp else 1.5.dp,
                    brush = Brush.linearGradient(
                        if (selected) listOf(c.accent, c.secondary) else listOf(c.accent.copy(alpha = 0.7f), c.secondary.copy(alpha = 0.45f))
                    ),
                    shape = shape
                )
        ) {
            thumb?.let {
                androidx.compose.foundation.Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            // What it is (its icon), ringed with the colours it uses.
            ThemeBadge(theme, 24.dp, Modifier.align(Alignment.BottomStart).padding(4.dp))
            if (favorite) Text("★", color = Color(0xFFFFD54A), fontSize = 14.sp, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
            // Keeps its own colours (⊘) or mostly does (◐): Theme look's colour choices won't (fully) change it.
            if (theme.ownColors) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(4.dp).size(16.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) { Text(if (theme.recolorable) "◐" else "⊘", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp).clip(CircleShape).background(c.accent),
                    contentAlignment = Alignment.Center
                ) { Text("✓", color = c.background, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            theme.label,
            color = if (selected) ShelfTheme.accent else ShelfTheme.textMuted,
            fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), lineHeight = androidx.compose.ui.unit.TextUnit(1.182f, androidx.compose.ui.unit.TextUnitType.Em), maxLines = 2, minLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/**
 * Row of still previews, one per theme scene, plus "Follow theme". The previews never animate
 * (Lite on, animate-on-Lite off inside), so a long list costs one draw each.
 */
@Composable
private fun ScenePicker(selected: String?, onPick: (String?) -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("ScenePicker")
    val themes = remember { AppTheme.values().toList() }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(ThemeTileGap)) {
        item {
            ScenePickerTile("Follow theme", selected == null, null, onClick = { onPick(null) })
        }
        items(themes.size) { i ->
            val theme = themes[i]
            ScenePickerTile(theme.label, selected == theme.name, theme, onClick = { onPick(theme.name) })
        }
    }
}

@Composable
private fun ScenePickerTile(label: String, selected: Boolean, theme: AppTheme?, onClick: () -> Unit) {
    val shape = uiRounded(10.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp).shelfClickable(shape) { onClick() }
    ) {
        Box(
            Modifier
                .size(width = 96.dp, height = 56.dp)
                .clip(shape)
                .background(ShelfTheme.colors.surfaceRaised)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) ShelfTheme.accent else Color.White.copy(alpha = 0.15f),
                    shape = shape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (theme != null) {
                // The same still pictures as the theme browser (no live shader per tile).
                val context = androidx.compose.ui.platform.LocalContext.current
                val thumb by androidx.compose.runtime.produceState(ThemeThumbs.cached(theme), theme) {
                    if (value == null) value = ThemeThumbs.get(context, theme)
                }
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(theme.colors.background, theme.colors.accent.copy(alpha = 0.6f)))))
                thumb?.let { androidx.compose.foundation.Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                ThemeBadge(theme, 20.dp, Modifier.align(Alignment.BottomStart).padding(3.dp))
            } else {
                Icon(Icons.Filled.Palette, null, tint = ShelfTheme.textMuted, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (selected) ShelfTheme.accent else ShelfTheme.textMuted,
            fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** One width and one gap for every theme / scene tile, so the lists line up the same everywhere. */
private val ThemeTileWidth = 96.dp
private val ThemeTileGap = 10.dp

@Composable
private fun SavedArtCard(
    settings: ArtStorageSettings,
    usedBytes: Long,
    missing: Int,
    saveDone: Int,
    saveTotal: Int,
    onSaveAll: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onChange: (ArtStorageSettings) -> Unit,
    onClearDetails: () -> Unit,
    onClearAchievements: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("SavedArtCard")
    ShelfCard {
        SectionLabel("Saved on this device")
        MutedNote(
            "Covers, wide art, backgrounds, logos, box / disc / spine scans and screenshots are kept on the device as files, " +
                "so they load instantly and work offline. Each one is saved the first time it loads and stays until you clear it — " +
                "Android, updates and restarts never remove it."
        )
        Spacer(Modifier.height(10.dp))
        val usedMb = usedBytes / (1024 * 1024)
        Text(
            "Using " + formatMb(usedMb.toInt()) + " of " + (if (settings.unlimited) "unlimited" else formatMb(settings.maxMegabytes)) + when {
                saveTotal > 0 -> " · saving $saveDone / $saveTotal"
                missing > 0 && com.shelf.launcher.data.ArtStorage.nearlyFull(usedBytes, settings.maxBytes) ->
                    " · full, $missing images not saved (raise the size below)"
                missing > 0 -> " · $missing images not saved yet"
                missing == 0 -> " · everything saved"
                else -> ""
            },
            color = ShelfTheme.colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        if (saveTotal > 0) TaskProgressLine(saveDone, saveTotal)
        Spacer(Modifier.height(12.dp))
        ChipFlow {
            if (saveTotal > 0) {
                Chip("Stop", accent = false, onClick = onCancel)
            } else {
                Chip("Save all now", onClick = onSaveAll)
            }
            Chip("Clear saved art", accent = false, onClick = onClear)
        }
        Spacer(Modifier.height(10.dp))
        MutedNote(
            "Game details (summaries, scores, playtimes) and RetroAchievements data (profile, progress, " +
                "awards, achievement lists) are saved too, and refreshed whenever you're online."
        )
        Spacer(Modifier.height(10.dp))
        ChipFlow {
            Chip("Clear saved details", accent = false, onClick = onClearDetails)
            Chip("Clear achievement data", accent = false, onClick = onClearAchievements)
        }
        Spacer(Modifier.height(14.dp))
        SectionLabel("Space it may use")
        ChipFlow {
            listOf(
                256 to "250 MB", 512 to "500 MB", 1024 to "1 GB", 2048 to "2 GB", 4096 to "4 GB",
                8192 to "8 GB", 16384 to "16 GB", 32768 to "32 GB",
                com.shelf.launcher.data.ArtStorageSettings.UNLIMITED to "Unlimited"
            ).forEach { (mb, label) ->
                Chip(label, accent = settings.maxMegabytes == mb) { onChange(settings.copy(maxMegabytes = mb)) }
            }
        }
        MutedNote(
            if (settings.unlimited) "No limit: everything stays until you clear it (only the device's free space limits it)."
            else "When full, new images aren't saved (the saved ones stay); raise the size to save the rest."
        )
    }
    Spacer(Modifier.height(12.dp))
    SwitchRow(
        icon = Icons.Filled.Refresh,
        title = "Save new artwork automatically",
        subtitle = "After scraping and at startup, only images not saved yet. Images the server refuses are tried again after a few days; it stops when the store is full.",
        checked = settings.autoSave,
        onChange = { onChange(settings.copy(autoSave = it)) }
    )
    // Update 190.
    Spacer(Modifier.height(12.dp))
    SwitchRow(
        icon = Icons.Filled.Refresh,
        title = "Keep getting artwork in the background",
        subtitle = "Artwork keeps downloading while you play, use another app or the screen is off — a quiet notification shows the count while it runs. Off: it waits until you're back in Voltcap.",
        checked = com.shelf.launcher.data.Features.artInBackground.state.value,
        onChange = { com.shelf.launcher.data.Features.artInBackground.value = it }
    )
    Spacer(Modifier.height(12.dp))
    SwitchRow(
        icon = Icons.Filled.Refresh,
        title = "Save game details automatically",
        subtitle = "Fetches every game's summary, score and playtimes in the background once a details source is connected.",
        checked = settings.autoSaveDetails,
        onChange = { onChange(settings.copy(autoSaveDetails = it)) }
    )
    // Update 114: every game's spine (its box scans) from ScreenScraper, not only as the shelf comes to it.
    Spacer(Modifier.height(12.dp))
    SwitchRow(
        icon = Icons.Filled.Refresh,
        title = "Scrape spines for every game",
        subtitle = "Looks up each game's box scans on ScreenScraper in the background (its spine, full box and disc), so the Shelf, " +
            "TV room and the Spines look show the game's own spine everywhere. Off: looked up only as a game comes on screen.",
        checked = com.shelf.launcher.data.Features.spineScrape.state.value,
        onChange = { com.shelf.launcher.data.Features.spineScrape.value = it; if (it) com.shelf.launcher.data.SpineScrape.run?.invoke(false) }
    )
    val spineProgress = com.shelf.launcher.data.SpineScrape.progress
    ChipFlow {
        if (spineProgress != null) {
            Chip("Spines ${spineProgress.first} / ${spineProgress.second} · Stop", accent = false) { com.shelf.launcher.data.SpineScrape.stop?.invoke() }
        } else {
            Chip("Scrape spines now", accent = false) { com.shelf.launcher.data.SpineScrape.run?.invoke(true) }
            // Update 141.
            Chip("Only the missing real covers", accent = false) { com.shelf.launcher.data.SpineScrape.runMissingReal?.invoke() }
        }
    }
    MutedNote("Scrape spines now: every game without its own spine is asked again, even ones asked recently. Only the missing real covers: just the games with no box scan, disc / cartridge or full box yet (cases, discs, cartridges, spines) — the others aren't touched. Both need ScreenScraper (Settings › Accounts).")
}

/** Update 119: a background job's progress as a bar that fills up (scraping, saving art). */
@Composable
internal fun TaskProgressLine(done: Int, total: Int) {
    val f by androidx.compose.animation.core.animateFloatAsState(
        if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f, androidx.compose.animation.core.tween(400), label = "task"
    )
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(ShelfTheme.accent.copy(alpha = 0.2f))) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(ShelfTheme.accent))
    }
}

// ---- Appearance extras ----

internal val ACCENT_CHOICES = listOf(
    0xFFE8A33D, 0xFFFF6B6B, 0xFFFF4FA3, 0xFFC77DFF, 0xFF7C83FF, 0xFF4C8DFF,
    0xFF3DD6F5, 0xFF2EC4B6, 0xFF7FD17F, 0xFFC6E377, 0xFFFFD166, 0xFFFFFFFF
).map { it.toInt() }

internal val BACKGROUND_CHOICES = listOf(
    0xFF000000, 0xFF0C0D11, 0xFF101418, 0xFF0B1220, 0xFF120A1E, 0xFF0B0F0C, 0xFF1A0F0B, 0xFF121212
).map { it.toInt() }


/**
 * Settings › Home › Layout options: everything about where the games sit in the home layout in use — the row's
 * place, the game you're on, the carousel, each layout's own options, card size and spacing. How the games are drawn
 * is Settings › Game look; picking the layout is Home › Layout & presets.
 */
@Composable
private fun LayoutOptionsCard(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
    val tileLayout = appearance.homeLayout == com.shelf.launcher.data.HomeLayout.TILES
    ShelfCard {
        SectionLabel(appearance.homeLayout.label)
        MutedNote("The options of the layout in use. Another layout: Home › Layout & presets. Each layout keeps its own card size and spacing.")
        Spacer(Modifier.height(8.dp))
        // Update 148: the page in folds (it was one long scroll): the game window, background & names, the carousel.
        FoldGroup(
            "Game window", key = "lo_window",
            summary = if (com.shelf.launcher.data.Features.gameWindow.state.value) "On" + (if (com.shelf.launcher.data.Features.winBanner.state.value) " · Banner page" else "") + " · what it shows" else "Off: A starts the game"
        ) {
        // Update 113: the game window, every layout (its look follows the layout).
        SwitchRow(
            Icons.Filled.OpenInNew, "Game window",
            "A or a tap on the game you're on opens a window in this layout's own style on the main screen: Play, Details, Back. " +
                "Off: A starts the game straight away (the details stay on the details hotkey and the game's options).",
            com.shelf.launcher.data.Features.gameWindow.state.value, { com.shelf.launcher.data.Features.gameWindow.value = it }
        )
        // Update 119: the classic details window instead (this layout only), the window's colours and what it shows.
        if (com.shelf.launcher.data.Features.gameWindow.state.value) {
            val F = com.shelf.launcher.data.Features
            val classic = appearance.homeLayout.name in F.classicWindowLayouts.state.value
            RowChoice("In ${appearance.homeLayout.label}, A opens", arrayOf(false, true), classic, { if (it) "The classic details window" else "This layout's own window" }) {
                if (it != classic) F.classicWindowLayouts.toggle(appearance.homeLayout.name)
            }
            // Update 146: the banner page — one look for every layout (art banner, Play strip, tabs).
            RowChoice("Its style (every layout)", arrayOf(false, true), F.winBanner.state.value, { if (it) "Banner page" else "The layout's own" }) { F.winBanner.value = it }
            RowChoice("Its colours (every layout)", com.shelf.launcher.data.Features.WinColors.values(), F.gameWindowColors.state.value, { it.label }) { F.gameWindowColors.value = it }
            MutedNote("The game's colours come from its cover; the layout's own keeps each layout's look as it was.")
            // Update 125: what the window shows and where, for this layout on its own.
            SectionLabel("What it shows and where (${appearance.homeLayout.label})")
            MutedNote("On the picture, by the game's name, under the buttons, or off. Play / Continue is always the first button. The ⚙ and 🏆 buttons turn the window to the game's options and achievements.")
            val plan = WinPlan.of(appearance.homeLayout)
            WinPart.values().forEach { p ->
                RowChoice(p.label, WinSlot.values(), plan[p] ?: WinSlot.OFF, { it.label }) { WinPlan.set(appearance.homeLayout, p, it) }
            }
            // Update 122: disc games as their case with the disc half out of it (the default), or the disc alone.
            if ((plan[WinPart.MEDIA] ?: WinSlot.OFF) != WinSlot.OFF) SwitchRow(
                Icons.Filled.Album, "Case with the disc",
                "Disc games show their case with the disc half out of it, spinning. Off: the disc alone. Cartridges stay cartridges.",
                F.gameWindowCase.state.value, { F.gameWindowCase.value = it }
            )
        }
        }
        FoldGroup("Background & game names", key = "lo_bg_names", summary = "How the picture sits, where the names go") {
        // Update 125: how the background picture sits in this layout.
        run {
            val F = com.shelf.launcher.data.Features
            F.heroFitByLayout.state.value; F.carouselLayouts.state.value
            F.spineLook.state.value
            val fit = com.shelf.launcher.data.HeroFitPlan.of(appearance.homeLayout, appearance.heroFit, appearance.cardArtType)
            RowChoice("Background picture", com.shelf.launcher.data.HeroFit.values(), fit, { it.label }) {
                com.shelf.launcher.data.HeroFitPlan.put(appearance.homeLayout, it)
            }
            MutedNote("Fill the screen: top to bottom, the sides of wide art cut off. Whole picture: all of it across the top, a soft blurred copy fills the rest. Until you pick here, it fills the screen — on the Voltcap row only with cases, cartridges, discs or spines (covers and wide capsules follow Background picture).")
        }
        // Update 125: where the game names go in this layout.
        run {
            com.shelf.launcher.data.Features.namesByLayout.state.value
            val (place, side) = com.shelf.launcher.data.NamePlan.of(appearance.homeLayout, appearance.titlePlacement, appearance.nameSide)
            val places = arrayOf(com.shelf.launcher.data.TitlePlacement.ABOVE, com.shelf.launcher.data.TitlePlacement.INSIDE, com.shelf.launcher.data.TitlePlacement.HIDDEN)
            RowChoice("Game names", places, if (place == com.shelf.launcher.data.TitlePlacement.BELOW) com.shelf.launcher.data.TitlePlacement.ABOVE else place, { it.label }) {
                com.shelf.launcher.data.NamePlan.put(appearance.homeLayout, it, side)
            }
            if (place == com.shelf.launcher.data.TitlePlacement.ABOVE || place == com.shelf.launcher.data.TitlePlacement.BELOW) {
                RowChoice("They sit", com.shelf.launcher.data.RowSide.values(), side, { it.label }) {
                    com.shelf.launcher.data.NamePlan.put(appearance.homeLayout, place, it)
                }
            }
            MutedNote("Layouts with their own name options (below) use those as well.")
        }
        }
        Spacer(Modifier.height(8.dp))
        if (appearance.homeLayout == com.shelf.launcher.data.HomeLayout.TV) {
            MutedNote(
                "The game you're on plays on an old TV: its video clip (ScreenScraper), else its screenshots in turn, else its art — " +
                    "in a lit room. The console's banner above, Volt on the TV, the story and facts under it."
            )
            Spacer(Modifier.height(8.dp))
            RowChoice("Games beside the TV", TvRoom.Games.values(), com.shelf.launcher.data.Features.tvRoomGames.state.value, { it.label }) {
                com.shelf.launcher.data.Features.tvRoomGames.value = it
            }
            if (com.shelf.launcher.data.Features.tvRoomGames.state.value == TvRoom.Games.SPINES) {
                MutedNote("The games show as R1 › Look picks: covers, wide capsules, cases, cartridges and discs. The Spines look shows the cases turned to their spines.")
            }
            // Update 129: what the game you're on shows once pulled out of the shelf (shared with the Game shelf).
            if (com.shelf.launcher.data.Features.tvRoomGames.state.value == TvRoom.Games.SHELF) {
                RowChoice("Pulled out, it shows", com.shelf.launcher.data.Features.ShelfPulled.values(), com.shelf.launcher.data.Features.shelfPulled.state.value, { it.label }) {
                    com.shelf.launcher.data.Features.shelfPulled.value = it
                }
            }
            if (com.shelf.launcher.data.Features.tvRoomGames.state.value == TvRoom.Games.CAROUSEL) {
                MutedNote("The game you're on big in the middle and lit up, the next ones each side smaller and behind it. ◀ ▶ move.")
                RowChoice(
                    "Games each side", arrayOf(1, 2, 3, 4, 5, 0),
                    if (com.shelf.launcher.data.Features.carouselEdge.state.value) 0 else com.shelf.launcher.data.Features.tvCarouselSide.state.value,
                    { if (it == 0) "To the edge" else it.toString() }
                ) {
                    // (Update 119: "To the edge" — games all the way to the screen's edges.)
                    com.shelf.launcher.data.Features.carouselEdge.value = it == 0
                    if (it > 0) com.shelf.launcher.data.Features.tvCarouselSide.value = it
                }
                // Update 129: how big the carousel's games are (every carousel).
                PercentSlider(
                    "Game size", com.shelf.launcher.data.Features.carouselSize.state.value,
                    { com.shelf.launcher.data.Features.carouselSize.value = (it / 5) * 5 }, min = 60, max = 200, valueText = { "$it %" }
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    Icons.Filled.ViewInAr, "Tilt the other games",
                    "The games aside turn, like pages. Off: they face you.",
                    com.shelf.launcher.data.Features.tvCarouselTilt.state.value, { com.shelf.launcher.data.Features.tvCarouselTilt.value = it }
                )
                CarouselTiltChoices()
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                Icons.Filled.Title, "Names under the games",
                "Each cartridge / disc / box beside the TV with the game's name under it.",
                com.shelf.launcher.data.Features.tvRoomNames.state.value, { com.shelf.launcher.data.Features.tvRoomNames.value = it }
            )
            // Update 107.
            SwitchRow(
                Icons.Filled.Title, "Game info under the TV",
                "The game's info strip on the floor under the set, so the games beside it get the whole height. Off: under the games.",
                com.shelf.launcher.data.Features.tvInfoUnder.state.value, { com.shelf.launcher.data.Features.tvInfoUnder.value = it }
            )
            SwitchRow(
                Icons.Filled.Title, "Recent badges on the wall",
                "Your recently unlocked achievement badges on the wall between the lamp and the TV, newest at the top.",
                com.shelf.launcher.data.Features.tvRecentBadges.state.value, { com.shelf.launcher.data.Features.tvRecentBadges.value = it }
            )
        }
        if (appearance.homeLayout == com.shelf.launcher.data.HomeLayout.GRID) MutedNote(
            "The category as a grid filling the screen. What the cards show (covers, wide capsules, boxes, cartridges / discs…) is Settings › Game look. ◀ ▶ ▲ ▼ move, L2 / R2 change the category."
        )
        if (appearance.homeLayout == com.shelf.launcher.data.HomeLayout.GRID) SwitchRow(
            Icons.Filled.Apps, "Games as square tiles",
            "Every game is its cover as a rounded square tile (what used to be the Tile home), whatever the game look is set to (the focused one grows with a ring).",
            com.shelf.launcher.data.Features.gridTiles.state.value, { com.shelf.launcher.data.Features.gridTiles.value = it }
        )
        if (appearance.homeLayout == com.shelf.launcher.data.HomeLayout.GRID) {
            RowChoice("The game you're on", com.shelf.launcher.data.Features.GridFocus.values(), com.shelf.launcher.data.Features.gridFocus.state.value, { it.label }) {
                com.shelf.launcher.data.Features.gridFocus.value = it
            }
            MutedNote(
                when (com.shelf.launcher.data.Features.gridFocus.state.value) {
                    com.shelf.launcher.data.Features.GridFocus.LIFT -> "It rises a little and turns slowly left and right."
                    com.shelf.launcher.data.Features.GridFocus.OUTLINE -> "A ring in the theme colour round it, nothing else."
                    com.shelf.launcher.data.Features.GridFocus.GLOW -> "The glow / electric effect from Game look › Cards › Effect, and it grows a little."
                }
            )
        }
        // Update 105: the game size of this layout in %, for every layout (also R1 › Size, hold).
        if (!tileLayout) {
            GameSizeSlider(appearance, onChange)
            Spacer(Modifier.height(8.dp))
        }
        // The style homes (Dashboard, Live tiles, Side menu, Cross menu, Spotlight, Icon bar): each its own options (StyleHomeSettings.kt).
        if (appearance.homeLayout.styleHome) FoldGroup(
            "${appearance.homeLayout.label} options", summary = styleHomeSummary(appearance.homeLayout),
            key = "style_" + appearance.homeLayout.name, startOpen = true
        ) { StyleHomeSettings(appearance.homeLayout) }
        // (Update 104: the Side menu can be a carousel too.)
        if (appearance.homeLayout != com.shelf.launcher.data.HomeLayout.TV && (!appearance.homeLayout.styleHome ||
                appearance.homeLayout == com.shelf.launcher.data.HomeLayout.SIDE ||
                appearance.homeLayout == com.shelf.launcher.data.HomeLayout.STACKS)) { // (Update 159: Case stacks too)
            // Carousel look for this layout (TV room: Games beside the TV › Carousel).
            val layoutName = appearance.homeLayout.name
            val carouselOn = layoutName in com.shelf.launcher.data.Features.carouselLayouts.state.value
            // (Update 148: in a fold of its own.)
            FoldGroup(
                "Carousel", key = "lo_carousel",
                summary = if (carouselOn) "On · " + com.shelf.launcher.data.Features.carouselView.state.value.label else "Off"
            ) {
            SwitchRow(
                Icons.Filled.ViewInAr, "Carousel look",
                if (appearance.homeLayout == com.shelf.launcher.data.HomeLayout.HERO) "The covers above and below the selected one turn away from it, like pages."
                else "The game you're on big in the middle, the next ones each side smaller and tucked behind it.",
                carouselOn, { com.shelf.launcher.data.Features.carouselLayouts.toggle(layoutName) }
            )
            if (carouselOn && appearance.homeLayout != com.shelf.launcher.data.HomeLayout.HERO) {
                RowChoice(
                    "Games each side", arrayOf(1, 2, 3, 4, 5, 0),
                    if (com.shelf.launcher.data.Features.carouselEdge.state.value) 0 else com.shelf.launcher.data.Features.tvCarouselSide.state.value,
                    { if (it == 0) "To the edge" else it.toString() }
                ) {
                    // (Update 119: "To the edge" — games all the way to the screen's edges.)
                    com.shelf.launcher.data.Features.carouselEdge.value = it == 0
                    if (it > 0) com.shelf.launcher.data.Features.tvCarouselSide.value = it
                }
                // Update 129: how big the carousel's games are (every carousel).
                PercentSlider(
                    "Game size", com.shelf.launcher.data.Features.carouselSize.state.value,
                    { com.shelf.launcher.data.Features.carouselSize.value = (it / 5) * 5 }, min = 60, max = 200, valueText = { "$it %" }
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    Icons.Filled.ViewInAr, "Tilt the other games",
                    "The games aside turn, like pages. Off: they face you. (Shared by every carousel.)",
                    com.shelf.launcher.data.Features.tvCarouselTilt.state.value, { com.shelf.launcher.data.Features.tvCarouselTilt.value = it }
                )
                CarouselTiltChoices()
            }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (appearance.homeLayout == com.shelf.launcher.data.HomeLayout.HERO) FoldGroup("Hero carousel options", summary = "Covers side, card style, transition, dim / blur", startOpen = true) { HeroCarouselSettings() }
        // Update 104: the game info parts (logo, description…) on the other layouts too.
        if (!appearance.homeLayout.styleHome && appearance.homeLayout != com.shelf.launcher.data.HomeLayout.HERO) {
            OverlayPartsChoice(appearance.homeLayout)
            Spacer(Modifier.height(8.dp))
        }
        if (tileLayout) {
            MutedNote(
                "Big square tiles in one row, the name above the one you're on and round buttons under it " +
                    "(▼ to reach them; with the tiles at the bottom the buttons sit above them, ▲). Card size sets the tile size; wide capsules are off in this layout."
            )
            RowChoice("Where the tiles sit", com.shelf.launcher.data.DockPosition.values(), appearance.dockPosition, { it.label }) {
                onChange(appearance.copy(dockPosition = it))
            }
            RowChoice("Game you're on", com.shelf.launcher.data.TileFocus.values(), appearance.tileFocus, { it.label }) {
                onChange(appearance.copy(tileFocus = it))
            }
            Spacer(Modifier.height(6.dp))
            PercentSlider(
                "Tile size",
                ((appearance.tileScale - com.shelf.launcher.data.TILE_SCALE_MIN) * 100) /
                    (com.shelf.launcher.data.TILE_SCALE_MAX - com.shelf.launcher.data.TILE_SCALE_MIN),
                { v ->
                    val pct = com.shelf.launcher.data.TILE_SCALE_MIN +
                        v * (com.shelf.launcher.data.TILE_SCALE_MAX - com.shelf.launcher.data.TILE_SCALE_MIN) / 100
                    onChange(appearance.copy(tileScale = (pct / 5) * 5))
                },
                // The slider runs 0–100 over the size range; show the real size.
                valueText = { v ->
                    "${((com.shelf.launcher.data.TILE_SCALE_MIN + v * (com.shelf.launcher.data.TILE_SCALE_MAX - com.shelf.launcher.data.TILE_SCALE_MIN) / 100) / 5) * 5}%"
                }
            )
            MutedNote("Bigger tiles for small screens. The name, round buttons and category pills grow with them.")
            MutedNote("The round buttons (shown or not, where, colours, size, which ones): Settings › Home › Round shortcuts.")
            MutedNote("The category's name above the tiles: Settings › Categories › On the home screen.")
        }
        if (!tileLayout && !appearance.homeLayout.styleHome) {
            RowChoice("Where the row sits", com.shelf.launcher.data.DockPosition.values(), appearance.dockPosition, { it.label }) {
                onChange(appearance.copy(dockPosition = it))
            }
            RowChoice("Game you're on", com.shelf.launcher.data.FocusPosition.values(), appearance.focusPosition, { it.label }) {
                onChange(appearance.copy(focusPosition = it))
            }
            // "Wide for the first game only" makes sense only with the game you're on at the left end.
            val leftEnd = appearance.focusPosition == com.shelf.launcher.data.FocusPosition.LEFT
            RowChoice(
                "It shows as",
                com.shelf.launcher.data.FocusedCardStyle.values()
                    .filter { leftEnd || it != com.shelf.launcher.data.FocusedCardStyle.FIRST_ONLY }.toTypedArray(),
                appearance.focusedCardStyle, { it.label }
            ) {
                onChange(appearance.copy(focusedCardStyle = it))
            }
            // (Update 141: "first one wide" is a fixed capsule that never rolls out — nothing to choose there.)
            if (appearance.focusedCardStyle == com.shelf.launcher.data.FocusedCardStyle.WIDE) CapsuleRevealChoice()
            if (appearance.focusedCardStyle == com.shelf.launcher.data.FocusedCardStyle.FIRST_ONLY) {
                MutedNote(
                    if (leftEnd) "The first game of the row (the one you played last, with the usual sort) is a fixed wide " +
                        "capsule at the left that stays put; the others scroll as covers next to it. (In a looping row it unfolds only while you're on it.)"
                    else "Only works with the game you're on at the left end — in the middle every game unfolds."
                )
            }
        }
        if (appearance.homeLayout != com.shelf.launcher.data.HomeLayout.HERO && appearance.homeLayout != com.shelf.launcher.data.HomeLayout.TV && !appearance.homeLayout.styleHome) {
            RowChoice(
                if (appearance.homeLayout == com.shelf.launcher.data.HomeLayout.GRID) "Card size (how many fit across)" else "Card size",
                com.shelf.launcher.data.CardSize.values(), appearance.cardSize, { it.label }
            ) {
                onChange(appearance.copy(cardSize = it))
            }
            if (appearance.homeLayout == com.shelf.launcher.data.HomeLayout.CLASSIC || appearance.homeLayout == com.shelf.launcher.data.HomeLayout.TILES ||
                appearance.homeLayout == com.shelf.launcher.data.HomeLayout.GRID) {
                val layout = appearance.homeLayout
                val now = com.shelf.launcher.data.Features.rowGamesFor(layout)
                val choices = if (layout == com.shelf.launcher.data.HomeLayout.GRID) arrayOf(0, 3, 4, 5, 6, 7, 8, 10) else arrayOf(0, 3, 4, 5, 6, 7, 8, 10, 12)
                RowChoice(if (layout == com.shelf.launcher.data.HomeLayout.GRID) "Games per row" else "Games across the screen", choices, now, { if (it == 0) "Auto" else it.toString() }) {
                    com.shelf.launcher.data.Features.setRowGames(layout, it)
                }
                MutedNote(
                    if (now == 0) "Auto: as many as fit at the card size above."
                    else "$now games across: the card size follows from it" + (if (layout == com.shelf.launcher.data.HomeLayout.CLASSIC) " (the wide game you're on counts as one)." else ".")
                )
            }
        }
        RowChoice("Space between categories, name and row", com.shelf.launcher.data.InfoSpacing.values(), appearance.infoSpacing, { it.label }) {
            onChange(appearance.copy(infoSpacing = it))
        }
        RowChoice("Other games tilt away while scrolling", arrayOf(false, true), appearance.tiltOthers, { if (it) "On" else "Off" }) {
            onChange(appearance.copy(tiltOthers = it))
        }
    }
    // Update 119: this layout's own animations, with its other options (they were under Home › Animations).
    Spacer(Modifier.height(12.dp))
    ShelfCard { LayoutAnimChoices(appearance.homeLayout, appearance.heroMotion) }
}

/**
 * Settings › Game look › Covers, cases & discs: what every game is drawn as, the same in every home layout, and the
 * switches that go with it (discs for all, cases, templates, games from several consoles).
 */
@Composable
private fun GameMediaCard(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
    ShelfCard {
        GameLookPicker(appearance, onChange)
        Spacer(Modifier.height(12.dp))
        val artNow = appearance.cardArtType
        // Lists that mix consoles: one size for every card by default.
        RowChoice("Categories with several consoles", com.shelf.launcher.ui.MixedArt.values(), com.shelf.launcher.data.Features.mixedArt.state.value, { it.label }) {
            com.shelf.launcher.data.Features.mixedArt.value = it
        }
        MutedNote(
            when (com.shelf.launcher.data.Features.mixedArt.state.value) {
                com.shelf.launcher.ui.MixedArt.BASIC -> "Where the games come from several consoles (Recently played, Favourites, a mixed category…) box art, cartridges and discs stay as picked, each in its own shape, fitted to the same slot."
                com.shelf.launcher.ui.MixedArt.AS_CHOSEN -> "Mixed lists use the game look above as it is: each console's own cartridge / disc shape, so sizes can differ."
                com.shelf.launcher.ui.MixedArt.COVER -> "Every game in a mixed list as its cover."
                com.shelf.launcher.ui.MixedArt.CAPSULE -> "Every game in a mixed list as its wide capsule."
                com.shelf.launcher.ui.MixedArt.DISC -> "Every game in a mixed list as a CD (the PS2 CD's size). Cartridge games get a plain CD with their cartridge label on it."
                com.shelf.launcher.ui.MixedArt.CARTRIDGE -> "Every game in a mixed list as a cartridge. Disc games get a plain generic cartridge (the Game Boy shell without its branding) with their disc label on it."
            }
        )
        // Update 147: one style for every spine (Spines look, Game shelf, the carousels' spines).
        Spacer(Modifier.height(10.dp))
        SwitchRow(
            icon = Icons.Filled.ViewWeek,
            title = "Matching spines",
            subtitle = "On: every game's spine is Voltcap's spine of its console with the game's name, so they all look alike. Off (default): a game's real spine scan when it has one.",
            checked = com.shelf.launcher.data.Features.spinesOwn.state.value,
            onChange = { com.shelf.launcher.data.Features.spinesOwn.value = it }
        )
        // Update 169: the cases take the cover's shape.
        Spacer(Modifier.height(10.dp))
        SwitchRow(
            icon = Icons.Filled.ViewWeek,
            title = "Steam / Android cases fit the cover",
            subtitle = "On (default): Voltcap's Steam and Android cases take the shape of the game's cover — the band on top stays, the window grows or shrinks to the cover, so it fills it with no bars beside it. Off: one case shape for all, the cover fitted into it.",
            checked = com.shelf.launcher.data.Features.ownCaseFit.state.value,
            onChange = { com.shelf.launcher.data.Features.ownCaseFit.value = it }
        )
        SwitchRow(
            icon = Icons.Filled.ViewWeek,
            title = "Console cases fit the cover too",
            subtitle = "On: the consoles' case templates (Settings › Cover templates) take the cover's shape as well. Off (default): they keep the real case's shape and size.",
            checked = com.shelf.launcher.data.Features.allCaseFit.state.value,
            onChange = { com.shelf.launcher.data.Features.allCaseFit.value = it }
        )
        // Update 170.
        SwitchRow(
            icon = Icons.Filled.ViewWeek,
            title = "Printed case backs",
            subtitle = "On (default): turning an opened game's 3D case round shows a back printed like a real one — its console's back cover (barcode, seals, rating box) over the game's art, with its screenshots and description. A game with its back scanned shows the scan. Off: plain plastic.",
            checked = com.shelf.launcher.data.Features.printedBacks.state.value,
            onChange = { com.shelf.launcher.data.Features.printedBacks.value = it }
        )
        // Update 195: every cartridge console on its own real shell (CartShells.kt).
        SwitchRow(
            icon = Icons.Filled.ViewWeek,
            title = "Authentic drawn cartridges",
            subtitle = "On: in the cartridge looks, every cartridge console's games are drawn on that console's real shell, by region (an NES or Famicom cart, a US or PAL SNES cart, a Japanese Mega Drive cart), turning in 3D at its real thickness. Off (default): your template pack's cartridges, as before. Any game can also go on any shell: its menu › Cartridge shell…",
            checked = com.shelf.launcher.data.Features.authenticCarts.state.value,
            onChange = { com.shelf.launcher.data.Features.authenticCarts.value = it }
        )
        // Update 198: opening cases.
        SwitchRow(
            icon = Icons.Filled.ViewWeek,
            title = "Open cases side by side",
            subtitle = "On: a game's case opens by itself once it's out in the game window (and on the second screen's Details page): the case to the left, its disc or cartridge out next to it. Off (default): tap the case (or click the right stick) to open it.",
            checked = com.shelf.launcher.data.Features.inspectOpenAtStart.state.value,
            onChange = { com.shelf.launcher.data.Features.inspectOpenAtStart.value = it }
        )
        SwitchRow(
            icon = Icons.Filled.ViewWeek,
            title = "Cases that open on the second screen",
            subtitle = "On (default): the second screen's Details page shows the game's case in 3D — tap it to open it, its disc or cartridge comes out beside it, drag to turn either. Off: the flat case and its media, flipped with a tap, as before.",
            checked = com.shelf.launcher.data.Features.secondOpenCase.state.value,
            onChange = { com.shelf.launcher.data.Features.secondOpenCase.value = it }
        )
        // Update 191: real cases per region (CaseCatalog.kt).
        Spacer(Modifier.height(8.dp))
        StyleChoice(
            "Case region", com.shelf.launcher.data.Features.CaseRegionChoice.values(),
            com.shelf.launcher.data.Features.caseRegion.state.value, { it.label }
        ) { com.shelf.launcher.data.Features.caseRegion.value = it }
        MutedNote("The 3D cases are drawn as sold in each region: a PAL PlayStation case is thicker, a European DS case clear, a Japanese GameCube case small and clear, a Super Famicom box a different shape. A game's file name says its region — (USA), (Europe), (Japan); this picks one for games whose name doesn't.")
        when (artNow) {
            com.shelf.launcher.data.CardArtType.CASE -> MutedNote("Every case the same size, in lists mixing consoles too (Continue playing, Favourites…).")
            com.shelf.launcher.data.CardArtType.CASE_REAL -> MutedNote("Every case at its real size next to the others: a DS case smaller than a PS2 one, a Saturn case taller. Also in lists that mix consoles.")
            com.shelf.launcher.data.CardArtType.DISC -> {
                SwitchRow(
                    icon = Icons.Filled.Album, title = "Cartridge games as discs too",
                    subtitle = "On: every console game is a disc — cartridge games get a plain CD with their label (or cover) on it. Off: disc and UMD games show their disc, the others their cover.",
                    checked = com.shelf.launcher.data.Features.discsForAll.state.value,
                    onChange = { com.shelf.launcher.data.Features.discsForAll.value = it }
                )
                Spacer(Modifier.height(8.dp))
            }
            else -> {}
        }
        if (artNow == com.shelf.launcher.data.CardArtType.CASE || artNow == com.shelf.launcher.data.CardArtType.CASE_REAL) {
            SwitchRow(
                icon = Icons.Filled.Album,
                title = "Original cases",
                subtitle = "On (default): each game's own scanned case from ScreenScraper — its front scan (or the front cut from the full box scan) is the case, and its real spine scan shows on the turned box and the Game shelf. Off: Voltcap's case templates with the cover in them.",
                checked = com.shelf.launcher.data.Features.originalCases.state.value,
                onChange = { com.shelf.launcher.data.Features.originalCases.value = it }
            )
            // Update 123: spines cut out of other scans are off unless asked for.
            Spacer(Modifier.height(10.dp))
            SwitchRow(
                icon = Icons.Filled.Album,
                title = "Spines cut from box scans",
                subtitle = "Off (default): only ScreenScraper's real spine scans; a game without one gets its console's spine with its name. On: a game without a spine scan uses a strip cut out of its full box scan (or its 3D box), which can be cut in the wrong place.",
                checked = com.shelf.launcher.data.Features.spineCuts.state.value,
                onChange = { com.shelf.launcher.data.Features.spineCuts.value = it }
            )
            Spacer(Modifier.height(10.dp))
            SwitchRow(
                icon = Icons.Filled.ViewInAr,
                title = "3D glass boxes (older look)",
                subtitle = "Off (default): the official flat case, the console's printed cover frame with the game's cover in it. On: the older 3D box under glass.",
                checked = com.shelf.launcher.data.Features.box3d.state.value,
                onChange = { com.shelf.launcher.data.Features.box3d.value = it }
            )
            Spacer(Modifier.height(10.dp))
            SwitchRow(
                icon = Icons.Filled.ViewInAr,
                title = "Spines on boxes",
                subtitle = "The 3D box, turned a little so its real spine shows at the side. Off (default): the case facing you, front alone.",
                checked = com.shelf.launcher.data.Features.boxSpine.state.value,
                onChange = { com.shelf.launcher.data.Features.boxSpine.value = it }
            )
            Spacer(Modifier.height(10.dp))
        }
        SwitchRow(
            icon = Icons.Filled.Layers,
            title = "Cover templates",
            subtitle = "Covers go into each console's own cover template (its printed border, real proportions); the TV room's shelf of spines uses the console's own spines. Games from several consoles together show plain covers, all one size. Off: plain covers.",
            checked = com.shelf.launcher.data.Features.coverTemplates.state.value,
            onChange = { com.shelf.launcher.data.Features.coverTemplates.value = it }
        )
        Spacer(Modifier.height(10.dp))
        if (artNow == com.shelf.launcher.data.CardArtType.CASE || artNow == com.shelf.launcher.data.CardArtType.CASE_REAL ||
            artNow == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC || artNow == com.shelf.launcher.data.CardArtType.DISC) {
            SwitchRow(
                icon = Icons.Filled.Album,
                title = "Disc in its case",
                subtitle = "Disc games (PlayStation, Saturn, Dreamcast, GameCube, Wii…) show their case with the disc half out of it, turning like it's playing.",
                checked = com.shelf.launcher.data.Features.discCase.state.value,
                onChange = { com.shelf.launcher.data.Features.discCase.value = it }
            )
            Spacer(Modifier.height(10.dp))
            // Update 151: the 3D box (or your 3D case) with its disc.
            if (com.shelf.launcher.data.Features.discCase.state.value) {
                SwitchRow(
                    icon = Icons.Filled.ViewInAr,
                    title = "3D case with the disc",
                    subtitle = "Off (default): the flat case. On: the 3D box with the disc half out of it.",
                    checked = com.shelf.launcher.data.Features.discCase3d.state.value,
                    onChange = { com.shelf.launcher.data.Features.discCase3d.value = it }
                )
                Spacer(Modifier.height(10.dp))
            }
        }
        if (artNow == com.shelf.launcher.data.CardArtType.CASE || artNow == com.shelf.launcher.data.CardArtType.CASE_REAL) {
            SwitchRow(
                icon = Icons.Filled.Album,
                title = "Case with its cartridge / disc",
                subtitle = "The original case standing, its cartridge or disc leaning against its lower right. Only the cartridge / disc moves (Moving discs, cartridges & cases). Used instead of \"Disc in its case\" when both are on.",
                checked = com.shelf.launcher.data.Features.caseWithMedia.state.value,
                onChange = { com.shelf.launcher.data.Features.caseWithMedia.value = it }
            )
            Spacer(Modifier.height(10.dp))
        }
        MediaMotionChoice()
        Spacer(Modifier.height(10.dp))
        if (artNow == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC) {
            val hidden = appearance.hiddenMedia
            SectionLabel("Shown")
            ChipFlow {
                listOf("cartridge" to "Cartridges & cards", "disc" to "Discs & UMDs").forEach { (kind, label) ->
                    val on = kind !in hidden
                    Chip((if (on) "✓ " else "") + label, accent = on) {
                        val next = if (on) hidden + kind else hidden - kind
                        onChange(appearance.copy(hiddenMedia = next))
                    }
                }
            }
            MutedNote("Switched off: those games show their cover instead.")
            MediaShellSwitch()
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** Settings › Game look › Cards: corners, the other games, the effect on the game you're on, the favourite star. */
@Composable
private fun CardShapeCard(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
    ShelfCard {
        RowChoice("Corners", com.shelf.launcher.data.CardCorners.values(), appearance.cardCorners, { it.label }) {
            onChange(appearance.copy(cardCorners = it))
        }
        RowChoice("Other games", com.shelf.launcher.data.OtherGamesLook.values(), appearance.otherGamesLook, { it.label }) {
            onChange(appearance.copy(otherGamesLook = it))
        }
        RowChoice("Effect on the game you're on", com.shelf.launcher.data.FocusEffect.values(), appearance.focusEffect, { it.label }) {
            onChange(appearance.copy(focusEffect = it))
        }
        CapsuleRevealChoice()
        MutedNote("Corners apply to covers, wide capsules and the details window — the golden border of mastered games follows them.")
    }
    Spacer(Modifier.height(12.dp))
    SettingsGrid {
        item { m ->
            SwitchRow(
                icon = Icons.Filled.Star,
                title = "Favourite star",
                subtitle = "The small star on covers of your favourites.",
                checked = appearance.favoriteStar,
                onChange = { onChange(appearance.copy(favoriteStar = it)) },
                modifier = m
            )
        }
    }
}

/** Settings › Home › Extras on the home screen: the status bar, what the wide capsule carries, play time, video previews. */
@Composable
private fun HomeExtrasCard(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
            SettingsGrid {
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.VideogameAsset,
                    title = "Play time on the home screen",
                    subtitle = "How long you've played the game you're on, just under the row (never on the cards).",
                    checked = appearance.playtimeOnHome,
                    onChange = { onChange(appearance.copy(playtimeOnHome = it)) },
                    modifier = m
                )
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.Movie,
                    title = "Video previews",
                    // (Update 151: on by default; on the Hero carousel it plays over the hero picture.)
                    subtitle = "Rest on a game for two seconds and its clip plays behind the dock or over the hero picture (ScreenScraper, ROMs).",
                    checked = appearance.videoPreviews,
                    onChange = { onChange(appearance.copy(videoPreviews = it)) },
                    modifier = m
                )
            }
            }
    SettingsGrid {
        item { m ->
            SwitchRow(
                icon = Icons.Filled.VideogameAsset,
                title = "Buttons on the wide capsule",
                subtitle = "Play, favourite and ••• on the game you're on.",
                checked = appearance.bannerButtons,
                onChange = { onChange(appearance.copy(bannerButtons = it)) },
                modifier = m
            )
        }
        item { m ->
            SwitchRow(
                icon = Icons.Filled.VideogameAsset,
                title = "\"Played … ago\" on the wide capsule",
                subtitle = "When you last played the game you're on.",
                checked = appearance.lastPlayedOnBanner,
                onChange = { onChange(appearance.copy(lastPlayedOnBanner = it)) },
                modifier = m
            )
        }
        item { m ->
            SwitchRow(
                icon = Icons.Filled.Palette,
                title = "Status bar",
                subtitle = "Clock, battery, Wi-Fi, search and your profile along the top.",
                checked = appearance.showStatusBar,
                onChange = { onChange(appearance.copy(showStatusBar = it)) },
                modifier = m
            )
        }
    }
    StatusBarSettingsCard()
}

/** Appearance › Name & category: where the name and the category label sit,
 * how they look (the same style for both), and how names are written. */
@Composable
private fun NameAndCategoryContent(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
    ShelfCard {
        MutedNote("The category's own name, logos and animation: Settings › Categories › On the home screen.")
        Spacer(Modifier.height(8.dp))
        RowChoice("Game's name sits", com.shelf.launcher.data.RowSide.values(), appearance.nameSide, { it.label }) {
            onChange(appearance.copy(nameSide = it))
        }
        MutedNote("Automatic: above the row — or under it when the games are at the top (then the name moves off the wide capsule too).")
        Spacer(Modifier.height(10.dp))
        RowChoice("Style (name and category)", com.shelf.launcher.data.NameBackdrop.values(), appearance.nameBackdrop, { it.label }) {
            onChange(appearance.copy(nameBackdrop = it, nameCapsule = it == com.shelf.launcher.data.NameBackdrop.DARK))
        }
        MutedNote(
            "Like the cards: the cards' colour, corners and frame. Theme colour, Accent bar, Neon sign and Slanted tag follow your theme. " +
                "Keeps both readable on bright backgrounds."
        )
        Spacer(Modifier.height(14.dp))
    }
    Spacer(Modifier.height(10.dp))
        SettingsGrid {
            item { m ->
                Column(
                    m
                        .clip(uiRounded(16.dp))
                        .background(ShelfTheme.colors.surfaceRaised)
                        .padding(16.dp)
                ) {
                    SectionLabel("Where the name shows")
                    MutedNote("On the wide capsule, or on its own line right next to the row (above or under — see \"Name sits\" above). Only the game you're on shows its name.")
                    Spacer(Modifier.height(8.dp))
                    ChipFlow {
                        // BELOW is the old "under" choice — the side is its own setting now.
                    com.shelf.launcher.data.TitlePlacement.values().filter { it != com.shelf.launcher.data.TitlePlacement.BELOW }.forEach { p ->
                            Chip(p.label, accent = appearance.titlePlacement == p ||
                                (p == com.shelf.launcher.data.TitlePlacement.ABOVE && appearance.titlePlacement == com.shelf.launcher.data.TitlePlacement.BELOW)) {
                                onChange(appearance.copy(titlePlacement = p))
                            }
                        }
                    }
                }
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.AutoAwesome,
                    title = "Clean up game names",
                    subtitle = "\"Zelda (USA) (En,Fr) (60 FPS)\" shows as \"Zelda\" — regions, languages and patch tags like widescreen hacks go. Disc and revision numbers stay.",
                    checked = appearance.hideRegionTags,
                    onChange = { onChange(appearance.copy(hideRegionTags = it)) },
                    modifier = m
                )
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.AutoAwesome,
                    title = "Hide everything in brackets",
                    subtitle = "Drops every ( ) and [ ] part of a name — \"(QOL Improvements)\", \"(Rev 1)\", \"[v1.2]\", \"(Disc 2)\" — plus loose \"v1.2\" / \"Patch\" endings.",
                    checked = appearance.hideAllBrackets,
                    onChange = { onChange(appearance.copy(hideAllBrackets = it)) },
                    modifier = m
                )
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.Image,
                    title = "Logos from RetroAchievements",
                    subtitle = "By the game's name: its icon from RetroAchievements instead of a logo (on by default; games linked to RetroAchievements; a logo you picked yourself still wins).",
                    checked = appearance.raLogos,
                    onChange = { onChange(appearance.copy(raLogos = it)) },
                    modifier = m
                )
            }
            // Update 113.
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.Image,
                    title = "Big logos from SteamGridDB",
                    subtitle = "The big logos — Edit Home's logo part, the Hero carousel, Spotlight's title, the second screen's showcase — " +
                        "use SteamGridDB's logo (looked up once per game). Off: whichever source the artwork's logo came from. A logo you picked yourself always wins.",
                    checked = com.shelf.launcher.data.Features.bigLogoSgdb.state.value,
                    onChange = { com.shelf.launcher.data.Features.bigLogoSgdb.value = it },
                    modifier = m
                )
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.Image,
                    title = "Game logos",
                    subtitle = "The game's logo next to its name on the focused game and in the details window " +
                        "(SteamGridDB / ScreenScraper logos; pick one under Artwork & details).",
                    checked = appearance.showGameLogos,
                    onChange = { onChange(appearance.copy(showGameLogos = it)) },
                    modifier = m
                )
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.VideogameAsset,
                    title = "Full console names",
                    subtitle = "\"PlayStation 2\", \"Nintendo GameCube\" instead of \"PS2\", \"GameCube\" — in the category bar and on cards.",
                    checked = appearance.fullConsoleNames,
                    onChange = { onChange(appearance.copy(fullConsoleNames = it)) },
                    modifier = m
                )
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.VideogameAsset,
                    title = "Console name on the wide card",
                    subtitle = "The console's name under the game's name on the game you're on.",
                    checked = appearance.showConsoleName,
                    onChange = { onChange(appearance.copy(showConsoleName = it)) },
                    modifier = m
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        ShelfCard {
            SectionLabel("Where the logo goes")
            MutedNote("Next to the game's name on the row, the wide capsule and the details window.")
            Spacer(Modifier.height(8.dp))
            ChipFlow {
                com.shelf.launcher.data.LogoPlacement.values().forEach { p ->
                    Chip(p.label, accent = appearance.logoPlacement == p && appearance.showGameLogos) {
                        onChange(appearance.copy(logoPlacement = p, showGameLogos = true))
                    }
                }
            }
        }
    }

/** Appearance › Next to the row: game info and/or news, under the row (games
 * at the top or middle) or above it (games at the bottom). */
@Composable
private fun RowInfoContent(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
    ShelfCard {
        RowChoice("Show", com.shelf.launcher.data.RowInfo.values(), appearance.rowInfo, { it.label }) {
            onChange(appearance.copy(rowInfo = it))
        }
        MutedNote(
            "Game info: year, genre, developer, Metacritic, time to beat, achievements and a line of the story " +
                "(from the details you've downloaded). News: headlines that change every few seconds — tap one to read it."
        )
        if (appearance.rowInfo == com.shelf.launcher.data.RowInfo.NEWS || appearance.rowInfo == com.shelf.launcher.data.RowInfo.BOTH) {
            Spacer(Modifier.height(12.dp))
            listOf(true to "Retro & handhelds", false to "Games in general").forEach { (retro, heading) ->
                SectionLabel(heading)
                ChipFlow {
                    com.shelf.launcher.data.NewsFeed.SOURCES.filter { it.retro == retro }.forEach { src ->
                        val on = src.id in appearance.newsSources
                        Chip((if (on) "✓ " else "") + src.name, accent = on) {
                            val next = if (on) appearance.newsSources - src.id else appearance.newsSources + src.id
                            onChange(appearance.copy(newsSources = next))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/** Settings › Controls: hotkeys, music buttons, the legend, and a cheat sheet. */
@Composable
private fun ControlsContent(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("ControlsContent")
    CollapsibleSection("Hotkeys", startExpanded = true) {
        HotkeysCard(appearance, onChange)
    }
    CollapsibleSection("Music buttons", startExpanded = true) {
        MusicControlsCard(appearance, onChange)
    }
    CollapsibleSection("Button legend", startExpanded = true) {
        SettingsGrid {
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.VideogameAsset,
                    title = "Button legend",
                    subtitle = "A slim strip with the main buttons along the bottom. R3 (right stick press) shows every control on a small card.",
                    checked = appearance.showLegend,
                    onChange = { onChange(appearance.copy(showLegend = it)) },
                    modifier = m
                )
            }
        }
        ShelfCard {
            SectionLabel("Printed on the buttons")
            MutedNote("What your controller's face buttons say. PlayStation shapes sit like Xbox letters: ✕ bottom, ○ right, □ left, △ top.")
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                com.shelf.launcher.data.ButtonGlyphs.values().forEach { g ->
                    Chip(g.label, accent = appearance.buttonGlyphs == g) { onChange(appearance.copy(buttonGlyphs = g)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionLabel("System button layout")
            val detected = com.shelf.launcher.data.SystemButtonLayout.abSwapped
            MutedNote(
                when (appearance.buttonLayout) {
                    com.shelf.launcher.data.ButtonLayoutMode.AUTO -> "Follows the handheld's own Xbox / Retro setting, read from your button presses. " + when (detected) {
                        null -> "Not seen yet: press A or B once."
                        true -> "Right now: Retro layout (A and B swapped)."
                        false -> "Right now: Xbox layout."
                    } + " If the legend stays wrong after switching, this device swaps the buttons where apps can't see it: pick Xbox or Retro by hand."
                    com.shelf.launcher.data.ButtonLayoutMode.XBOX -> "Always treated as Xbox layout (the bottom button is A to the system)."
                    com.shelf.launcher.data.ButtonLayoutMode.NINTENDO -> "Always treated as Retro layout (A on the right; the right button is A to the system)."
                }
            )
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                com.shelf.launcher.data.ButtonLayoutMode.values().forEach { m ->
                    Chip(m.label, accent = appearance.buttonLayout == m) { onChange(appearance.copy(buttonLayout = m)) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MutedNote("Legend preview: ")
                listOf("A", "B", "X", "Y").forEach { k ->
                    Spacer(Modifier.width(6.dp))
                    GlyphPill(k)
                }
            }
        }
    }
    CollapsibleSection("What the buttons do") {
        ShelfCard {
            MutedNote(
                "Home: ◀ ▶ move · A details · X favorite · Y achievements · Start options · Select menu · " +
                    "L1 shortcuts · R1 quick menu · L2 / R2 category · B category screen · L3 jump by letter · " +
                    "R3 all controls · right stick ◀ ▶ letters (no sticks? give L3 / R3's jobs to a hotkey above).\n" +
                    "Details window: D-pad or left stick move between the buttons · right stick scrolls · A select · B close."
            )
        }
    }
}

/** Six hotkeys: a button (or Select + button) and what it does. */
@Composable
private fun HotkeysCard(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
    var pickingAppFor by remember { mutableStateOf<Int?>(null) }
    val buttons = com.shelf.launcher.data.MusicButton.values()
    val actions = com.shelf.launcher.data.HotkeyAction.values()
    fun update(index: Int, hk: com.shelf.launcher.data.Hotkey) =
        onChange(appearance.copy(hotkeys = appearance.hotkeys.toMutableList().also { it[index] = hk }))
    ShelfCard {
        MutedNote("Press a chip to change it. \"Select + …\" = hold Select and press the other button. M1 / M2 are the back buttons (AYN Odin 2 Portal and other AYN handhelds). No stick presses? \"Jump by letter (L3)\" and \"All controls (R3)\" can go on any button here.")
        appearance.hotkeys.forEachIndexed { index, hk ->
            Spacer(Modifier.height(12.dp))
            Text("Hotkey ${index + 1}", color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                Chip(hk.button.label, accent = hk.button != com.shelf.launcher.data.MusicButton.NONE) {
                    // Next button nothing else uses (music buttons and the other hotkeys).
                    val taken = appearance.musicButtons.values.toSet() +
                        appearance.hotkeys.filterIndexed { i, _ -> i != index }.map { it.button }
                    var next = hk.button
                    do {
                        next = buttons[(next.ordinal + 1) % buttons.size]
                    } while (next != com.shelf.launcher.data.MusicButton.NONE && next in taken)
                    update(index, hk.copy(button = next))
                }
                Chip(hk.action.label, accent = false) {
                    val next = actions[(hk.action.ordinal + 1) % actions.size]
                    update(index, hk.copy(action = next))
                }
                if (hk.action == com.shelf.launcher.data.HotkeyAction.OPEN_APP) {
                    Chip(hk.appLabel ?: "Choose app…", accent = false) { pickingAppFor = index }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionLabel("Ideas")
        com.shelf.launcher.data.HOTKEY_TIPS.forEach { MutedNote("• $it") }
    }
    pickingAppFor?.let { index ->
        val installed by com.shelf.launcher.data.InstalledAppsHub.apps.collectAsState()
        val apps = remember(installed) { installed.map { it.name to it.packageName.orEmpty() } }
        ShelfMenuDialog(title = "Open which app?", onDismiss = { pickingAppFor = null }, icon = Icons.Filled.Apps) {
            apps.forEach { (label, pkg) ->
                MenuRow(Icons.Filled.Apps, label, pkg) {
                    appearance.hotkeys.getOrNull(index)?.let { hk ->
                        update(index, hk.copy(action = com.shelf.launcher.data.HotkeyAction.OPEN_APP, packageName = pkg, appLabel = label))
                    }
                    pickingAppFor = null
                }
            }
        }
    }
}

/**
 * Settings › Home › Round shortcuts: the only place for the round shortcut buttons: shown or not and where (by the
 * tiles, a corner ☰, anywhere), their colours and highlight, size, which buttons, moving them, and how many of them
 * the current layout's own menus show.
 */
@Composable
private fun RoundShortcutsContent(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
    val layout = appearance.homeLayout
    val tile = layout == com.shelf.launcher.data.HomeLayout.TILES
    val ownMenu = layout.styleHome && styleHasShortcuts(layout)
    val spots = com.shelf.launcher.data.ShortcutSpot.values()
    ShelfCard {
        MutedNote(
            "Your round shortcut buttons (Shortcuts, Saves, What should I play?, Edit Home, Volt, Settings…). " +
                if (tile) "In the Tile home they sit by the tiles (▼ reaches them; with the tiles at the bottom the buttons sit above them, ▲)."
                else "In this layout they can fold into a small ☰ in a corner or float anywhere."
        )
        Spacer(Modifier.height(10.dp))
        // One choice: hidden, or where. (In the Tile home "By the row" is under the tiles; elsewhere it means off,
        // and picking it never switches the Tile home's buttons off.)
        val none = "none"
        val values: Array<Any> = (if (tile) listOf<Any>(none) else emptyList<Any>()).plus(spots.toList()).toTypedArray()
        val current: Any = when {
            !appearance.tileShortcuts -> if (tile) none else com.shelf.launcher.data.ShortcutSpot.ROW
            else -> appearance.shortcutSpot
        }
        RowChoice("Round shortcut buttons", values, current, { v ->
            when {
                v == none -> "Hidden"
                v == com.shelf.launcher.data.ShortcutSpot.ROW -> if (tile) "Under the tiles" else "Off"
                else -> (v as com.shelf.launcher.data.ShortcutSpot).label
            }
        }) { v ->
            when {
                v == none -> onChange(appearance.copy(tileShortcuts = false))
                v == com.shelf.launcher.data.ShortcutSpot.ROW && !tile -> onChange(appearance.copy(shortcutSpot = com.shelf.launcher.data.ShortcutSpot.ROW))
                else -> onChange(appearance.copy(tileShortcuts = true, shortcutSpot = v as com.shelf.launcher.data.ShortcutSpot))
            }
        }
        if (ownMenu) MutedNote("This layout already has them in its own menu, so the corner ☰ stays away here (it shows on the other layouts).")
        val shown = appearance.tileShortcuts && (tile || appearance.shortcutSpot != com.shelf.launcher.data.ShortcutSpot.ROW)
        if (shown || ownMenu) {
            RowChoice("Button colours", arrayOf(false, true), appearance.tileShortcutTheme, { if (it) "Theme colour" else "Their own colours" }) {
                onChange(appearance.copy(tileShortcutTheme = it))
            }
            RowChoice("Highlight", com.shelf.launcher.data.ShortcutHighlight.values(), appearance.shortcutHighlight, { it.label }) {
                onChange(appearance.copy(shortcutHighlight = it))
            }
        }
        val f = com.shelf.launcher.data.Features
        PercentSlider("Button size", f.shortcutScale.state.value, { f.shortcutScale.value = it }, min = 60, max = 160, live = true)
        Spacer(Modifier.height(10.dp))
        Text("Buttons (also used by the layouts' menus)", color = ShelfTheme.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        val hidden = f.shortcutHidden.state.value
        ChipFlow {
            HomeShortcutNames.forEach { name ->
                val on = name !in hidden
                Chip((if (on) "✓ " else "") + name, accent = on) { f.shortcutHidden.toggle(name) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("App icons in the L1 shortcuts", color = ShelfTheme.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        ChipFlow {
            Chip("Their own colours", accent = !f.shortcutThemedIcons.state.value) { f.shortcutThemedIcons.value = false }
            Chip("Theme colours", accent = f.shortcutThemedIcons.state.value) { f.shortcutThemedIcons.value = true }
        }
        Spacer(Modifier.height(10.dp))
        // Moving them: Edit Home (they float where you put them from then on).
        if (!ownMenu) ChipFlow {
            Chip("Move them (Edit Home)…", accent = false) {
                if (!shown) onChange(appearance.copy(tileShortcuts = true, shortcutSpot = com.shelf.launcher.data.ShortcutSpot.FREE))
                HomeEditState.open(); HomeEditState.selected = HomeWidgets.SHORTCUTS
            }
        }
    }
    // The current layout's own menus made of these buttons (Dashboard, Live tiles, Console hub).
    if (layout.styleHome && styleHasShortcutSettings(layout)) {
        Spacer(Modifier.height(10.dp))
        ShelfCard {
            SectionLabel("In ${layout.label}")
            Spacer(Modifier.height(6.dp))
            StyleShortcutSettings(layout)
        }
    }
}

/** One labelled row of choices, as wrapping chips. */
@Composable
private fun <T> RowChoice(label: String, values: Array<T>, current: T, name: (T) -> String, onPick: (T) -> Unit) {
    Text(label, color = ShelfTheme.textMuted, fontSize = 12.sp, modifier = jumpAnchor(label))
    Spacer(Modifier.height(6.dp))
    // Visual choices (layouts, artwork, …) get a little drawing per option (OptionIcons.kt).
    if (values.all { pictogramFor(it) != null }) PictoChoice(values, current, name, onPick)
    else ChipFlow {
        values.forEach { v -> Chip(name(v), accent = v == current) { onPick(v) } }
    }
    Spacer(Modifier.height(14.dp))
}

/** Settings › Audio: interface sounds, haptics, your own sounds, music player,
 * lyrics, music buttons and the preview clips' sound. */
@Composable
private fun SoundsContent(appearance: AppearanceSettings, currentTheme: AppTheme, onChange: (AppearanceSettings) -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("SoundsContent")
    CollapsibleSection("Sound & feel", startExpanded = true) {
        SettingsGrid {
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.MusicNote,
                    title = "Navigation sounds",
                    subtitle = "Clicks and chimes when moving, selecting, going back, opening menus and starting games.",
                    checked = appearance.soundsEnabled,
                    onChange = { onChange(appearance.copy(soundsEnabled = it)) },
                    modifier = m
                )
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.Vibration,
                    title = "Haptics",
                    subtitle = "A tick when moving and a tap when selecting — through the device's motor, or the controller's rumble.",
                    checked = appearance.haptics,
                    onChange = { onChange(appearance.copy(haptics = it)) },
                    modifier = m
                )
            }
        }
        if (appearance.haptics) {
            Spacer(Modifier.height(10.dp))
            ShelfCard {
                SectionLabel("Haptic strength")
                Spacer(Modifier.height(8.dp))
                ChipFlow {
                    listOf(30 to "Light", 60 to "Medium", 100 to "Strong").forEach { (v, label) ->
                        Chip(label, accent = appearance.hapticStrength == v) {
                            onChange(appearance.copy(hapticStrength = v))
                        }
                    }
                }
            }
        }
        if (appearance.soundsEnabled) {
            Spacer(Modifier.height(10.dp))
            ShelfCard {
                SectionLabel("Sound style")
                MutedNote(
                    "Console is soft and airy, like a PlayStation menu. Chill is softer still, with a big airy room. " +
                        "Electric is synth blips and sparks. 32-bit is crunchy late-90s console blips. Dark fantasy is " +
                        "harp, crystal and a far bell. Soft chimes is the original set."
                )
                Spacer(Modifier.height(8.dp))
                val preview = LocalUiSounds.current
                ChipFlow {
                    com.shelf.launcher.data.SoundStyle.values().forEach { style ->
                        Chip(style.label, accent = appearance.soundStyle == style) {
                            onChange(appearance.copy(soundStyle = style))
                            // Heard straight away, even while the theme's music brings its own set.
                            preview?.preview(style, UiSound.SELECT)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                val loopNow = appearance.musicLoopFor(currentTheme)
                val ownSet = loopNow.sounds
                SwitchRow(
                    icon = Icons.Filled.MusicNote,
                    title = "Sounds match the music",
                    subtitle = when {
                        !appearance.themeSounds -> "Off: your sound style everywhere."
                        ownSet != null -> "Now: ${ownSet.label} (goes with ${loopNow.label}). Console-menu, 32-bit and " +
                            "dark fantasy music bring their own sounds; the rest use your style."
                        else -> "Console-menu, 32-bit and dark fantasy music bring their own sounds; this theme uses your style."
                    },
                    checked = appearance.themeSounds,
                    onChange = { onChange(appearance.copy(themeSounds = it)) }
                )
            }
            Spacer(Modifier.height(10.dp))
            ShelfCard {
                SectionLabel("Volume")
                MutedNote("Follows your media volume.")
                Spacer(Modifier.height(8.dp))
                ChipFlow {
                    listOf(25, 50, 75, 100).forEach { v ->
                        Chip("$v%", accent = appearance.soundVolume == v) { onChange(appearance.copy(soundVolume = v)) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            CustomSoundsCard()
        }
    }
    CollapsibleSection("Background music", startExpanded = true) {
        BackgroundMusicCard(appearance, onChange)
    }
    CollapsibleSection("Video previews", startExpanded = true) {
        SettingsGrid {
            // Update 151: the on / off switch here too (on by default): a game's scraped clip plays over its big
            // background or hero picture once you rest on it.
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.Movie,
                    title = "Video previews",
                    subtitle = "Rest on a game and its scraped clip plays over its background or hero picture.",
                    checked = appearance.videoPreviews,
                    onChange = { onChange(appearance.copy(videoPreviews = it)) },
                    modifier = m
                )
            }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.Movie,
                    title = "Video sound",
                    subtitle = if (appearance.videoPreviews) {
                        "Play the preview clips with their sound."
                    } else {
                        "Video previews are off (Home › Extras on the home screen) — turning this on switches them on too."
                    },
                    checked = appearance.videoSound && appearance.videoPreviews,
                    onChange = { onChange(appearance.copy(videoSound = it, videoPreviews = appearance.videoPreviews || it)) },
                    modifier = m
                )
            }
        }
    }
}

/**
 * Which buttons control the music from the home screen. Each chip cycles
 * through the choices; "Select + …" means hold Select and press the button.
 */
@Composable
private fun MusicControlsCard(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
    val buttons = com.shelf.launcher.data.MusicButton.values()
    ShelfCard {
        SectionLabel("Music controls")
        MutedNote(
            "Control Spotify (or whatever is playing) without opening anything. Press a chip to change its " +
                "button. \"Select + …\" = hold Select, press the other button; Select alone still opens the menu."
        )
        com.shelf.launcher.data.MusicAction.values().forEach { action ->
            val current = appearance.musicButtons[action] ?: com.shelf.launcher.data.MusicButton.NONE
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    action.label,
                    color = ShelfTheme.colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Chip(current.label, accent = current != com.shelf.launcher.data.MusicButton.NONE) {
                    // Next button nobody else uses (Off can always be picked).
                    val taken = appearance.musicButtons.filterKeys { it != action }.values.toSet() +
                        appearance.hotkeys.map { it.button }
                    var next = current
                    do {
                        next = buttons[(next.ordinal + 1) % buttons.size]
                    } while (next != com.shelf.launcher.data.MusicButton.NONE && next in taken)
                    onChange(appearance.copy(musicButtons = appearance.musicButtons + (action to next)))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Chip("Reset to defaults", accent = false) {
            onChange(appearance.copy(musicButtons = com.shelf.launcher.data.DEFAULT_MUSIC_BUTTONS))
        }
    }
}

/** Shelf as the device's home screen. */
@Composable
private fun HomeLauncherCard() {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val isDefault = remember(refresh) { com.shelf.launcher.data.HomeLauncher.isDefault(context) }
    val offered = remember(refresh) { com.shelf.launcher.data.HomeLauncher.isOffered(context) }
    ShelfCard {
        SectionLabel("Home app")
        if (isDefault) {
            Text("✓ Voltcap is your home app", color = ShelfTheme.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
        }
        MutedNote(
            when {
                isDefault -> "The home button and starting the device open Voltcap."
                offered -> "Voltcap can be picked as the home app — choose it in Android's list."
                else -> "Make Voltcap open on the home button and when the device starts, like a console."
            }
        )
        Spacer(Modifier.height(10.dp))
        ChipFlow {
            if (!isDefault) {
                Chip("Use Voltcap as home app") {
                    com.shelf.launcher.data.HomeLauncher.setOffered(context, true)
                    refresh++
                }
            } else {
                Chip("Choose another home app", accent = false) {
                    com.shelf.launcher.data.HomeLauncher.openHomePicker(context)
                }
            }
            if (offered && !isDefault) {
                Chip("Stop offering Voltcap", accent = false) {
                    com.shelf.launcher.data.HomeLauncher.setOffered(context, false)
                    refresh++
                }
            }
        }
    }
}

/** Lets each interface sound be swapped for an audio file of your own. */
/** Settings › Audio › Background music: the theme's own loop, your own file, or off — with its own volume. */
@Composable
private fun BackgroundMusicCard(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("BackgroundMusicCard")
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    // Copying the file (up to 30 MB) runs off the main thread.
    val importScope = androidx.compose.runtime.rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { BackgroundMusicFiles.import(context, uri) }
            if (result.error != null) {
                error = result.error
            } else {
                error = null
                onChange(
                    appearance.copy(
                        musicMode = com.shelf.launcher.data.BackgroundMusicMode.CUSTOM,
                        musicCustomName = result.name,
                        musicCustomStamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }
    ShelfCard {
        RowChoice(
            "Background music",
            com.shelf.launcher.data.BackgroundMusicMode.values(),
            appearance.musicMode,
            { it.label }
        ) { onChange(appearance.copy(musicMode = it)) }
        MutedNote(
            "Theme music is a calm, endless loop composed for Voltcap — built in, nothing to download. " +
                "It pauses while a game or another app is in front, and while a music app is playing."
        )
        if (appearance.musicMode == com.shelf.launcher.data.BackgroundMusicMode.THEME) {
            Spacer(Modifier.height(10.dp))
            // The loops Voltcap has (many themes share one): pick one, or let the theme decide. Under each:
            // which themes use it.
            SectionLabel("Track")
            val loops = remember {
                com.shelf.launcher.ui.theme.MusicLoop.values().map { loop ->
                    loop to com.shelf.launcher.ui.theme.AppTheme.values().filter { it.musicLoop == loop }
                }
            }
            val picked = com.shelf.launcher.ui.theme.MusicLoop.named(appearance.musicTrack)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Follow the theme", accent = picked == null) { onChange(appearance.copy(musicTrack = "")) }
                MutedNote("Each theme plays the loop that fits it (listed under each loop below).")
                loops.forEach { (loop, users) ->
                    Chip(loop.label, accent = picked == loop) { onChange(appearance.copy(musicTrack = loop.name)) }
                    MutedNote("Used by: " + users.take(6).joinToString(", ") { it.label } + if (users.size > 6) " and ${users.size - 6} more" else "")
                }
            }
        }
        if (appearance.musicMode == com.shelf.launcher.data.BackgroundMusicMode.CUSTOM) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your track", color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        appearance.musicCustomName.ifBlank { "None picked yet" },
                        color = ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Chip("Choose…", accent = false) { pick.launch(arrayOf("audio/*")) }
                if (appearance.musicCustomName.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Chip("Remove", accent = false) {
                        BackgroundMusicFiles.clear(context)
                        onChange(appearance.copy(musicCustomName = "", musicCustomStamp = System.currentTimeMillis()))
                    }
                }
            }
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = com.shelf.launcher.ui.theme.Ds.danger, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        PercentSlider(
            "Music volume (separate from the interface sounds)",
            appearance.musicVolume,
            { onChange(appearance.copy(musicVolume = it)) },
            enabled = appearance.musicMode != com.shelf.launcher.data.BackgroundMusicMode.OFF
        )
    }
}

@Composable
private fun CustomSoundsCard() {
    com.shelf.launcher.Breadcrumbs.mark("CustomSoundsCard")
    val context = LocalContext.current
    val sounds = LocalUiSounds.current
    // Bumped after every change so the "Custom" labels re-read.
    var version by remember { mutableStateOf(0) }
    var picking by remember { mutableStateOf<UiSound?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Reading the picked file (up to 3 MB, maybe from a cloud provider) runs off the main thread (update 91).
    val importScope = androidx.compose.runtime.rememberCoroutineScope()
    val pickSound = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val sound = picking
        picking = null
        if (uri != null && sound != null) importScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CustomSounds.import(context, sound, uri) }
            error = result
            sounds?.reload(sound)
            version++
            if (result == null) sounds?.play(sound)
        }
    }
    ShelfCard {
        SectionLabel("Your own sounds")
        MutedNote("Pick a short audio file (MP3, OGG, WAV…) for any of these. Reset brings back Voltcap's own sound.")
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = com.shelf.launcher.ui.theme.Ds.danger, fontSize = 12.sp)
        }
        UiSound.values().forEach { sound ->
            val custom = remember(version) { CustomSounds.has(context, sound) }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(sound.label, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (custom) "Your sound" else "Voltcap's sound", color = ShelfTheme.textMuted, fontSize = 11.sp)
                }
                Chip("Play", accent = false) { sounds?.play(sound) }
                Spacer(Modifier.width(8.dp))
                Chip("Choose…", accent = false) {
                    picking = sound
                    pickSound.launch(arrayOf("audio/*"))
                }
                if (custom) {
                    Spacer(Modifier.width(8.dp))
                    Chip("Reset", accent = false) {
                        CustomSounds.clear(context, sound)
                        sounds?.reload(sound)
                        version++
                    }
                }
            }
        }
    }
}

/** A row of colour dots; the first (⌀) means "theme default". */
@Composable
internal fun ColorChoices(choices: List<Int>, selected: Int?, onPick: (Int?) -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("ColorChoices")
    ChipFlow {
        ColorDot(null, selected == null) { onPick(null) }
        choices.forEach { argb -> ColorDot(argb, selected == argb) { onPick(argb) } }
    }
}

@Composable
private fun ColorDot(argb: Int?, selected: Boolean, onClick: () -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("ColorDot")
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(argb?.let { Color(it) } ?: Color.White.copy(alpha = 0.08f))
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) ShelfTheme.colors.textPrimary else Color.White.copy(alpha = 0.2f),
                CircleShape
            )
            .shelfClickable(CircleShape) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (argb == null) Text("⌀", color = ShelfTheme.textMuted, fontSize = 14.sp)
    }
}

// ---- Profile ----

/** Week / month / year recaps, one press each (Settings › Stats and › Profile). */
@Composable
internal fun RecapsCard(onOpen: (com.shelf.launcher.data.YearRecap.Period) -> Unit) {
    val Y = com.shelf.launcher.data.YearRecap
    ShelfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EmojiEvents, null, tint = ShelfTheme.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            SectionLabel("Recaps")
        }
        MutedNote("Total time, a podium of your top three, streaks and a day-by-day chart. Inside one, ◀ ▶ go to earlier weeks, months or years and L1 / R1 switch between them. The year's recap also opens by itself every December and on your Voltcap anniversary.")
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            Chip("This week") { onOpen(Y.period(com.shelf.launcher.data.YearRecap.Kind.WEEK)) }
            Chip("Last week", accent = false) { onOpen(Y.period(com.shelf.launcher.data.YearRecap.Kind.WEEK, -1)) }
            Chip("This month") { onOpen(Y.period(com.shelf.launcher.data.YearRecap.Kind.MONTH)) }
            Chip("Last month", accent = false) { onOpen(Y.period(com.shelf.launcher.data.YearRecap.Kind.MONTH, -1)) }
            Chip("This year") { onOpen(Y.period(com.shelf.launcher.data.YearRecap.Kind.YEAR)) }
        }
    }
}

@Composable
internal fun ProfileContent(
    connected: Boolean,
    profile: RaUserProfile?,
    stats: RaProfileStats,
    completion: Collection<RaCompletionEntry>,
    recent: List<RaRecentAchievement>,
    onOpenGame: (Int) -> Unit = {}
) {
    com.shelf.launcher.Breadcrumbs.mark("ProfileContent")
    if (!connected && profile == null) {
        ShelfCard {
            SectionLabel("Not connected")
            MutedNote("Connect RetroAchievements under Accounts to see your profile, awards and unlocks here.")
        }
    } else {

    // Who you are
    ShelfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val avatar = profilePictureUrl(RaBadges.mediaUrl(profile?.UserPic))
            if (avatar != null) {
                AsyncImage(
                    model = avatar, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(uiRounded(16.dp))
                )
            } else {
                Box(Modifier.size(64.dp).clip(uiRounded(16.dp)).background(ShelfTheme.accent.copy(alpha = 0.3f)))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(profile?.User ?: "—", color = ShelfTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(
                        profile?.TotalPoints?.let { "$it points" },
                        profile?.TotalTruePoints?.let { "$it RetroPoints" },
                        profile?.Rank?.let { "rank #$it" }
                    ).joinToString("  ·  "),
                    color = ShelfTheme.textMuted,
                    fontSize = 13.sp
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    // Totals
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile("Played", stats.gamesPlayed.toString(), Modifier.weight(1f))
        StatTile("Mastered", stats.mastered.toString(), Modifier.weight(1f))
        StatTile("Beaten", stats.beaten.toString(), Modifier.weight(1f))
        val pct = if (stats.achievementsPossible > 0) stats.achievementsEarned * 100 / stats.achievementsPossible else 0
        StatTile("Unlocks", "${stats.achievementsEarned} ($pct%)", Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))

    // Games you've been playing lately, with how far along you are.
    val lately = completion
        .withIndex()
        .sortedWith(compareByDescending<IndexedValue<RaCompletionEntry>> { it.value.MostRecentAwardedDate ?: "" }
            .thenBy { it.index })
        .map { it.value }
        .filter { (it.MaxPossible ?: 0) > 0 }
        .take(5)
    ShelfCard {
        SectionLabel("Currently playing")
        if (lately.isEmpty()) {
            EmptyState("No games with progress yet.")
        } else {
            // A (or a tap) on a game: its achievements, opened on the ones still missing.
            lately.forEach { entry -> ProgressRow(entry, onClick = entry.GameID?.let { id -> { onOpenGame(id) } }) }
        }
    }
    Spacer(Modifier.height(12.dp))

    // Recent awards
    val awarded = completion
        .filter { RaAward.from(it.HighestAwardKind) != null }
        .sortedByDescending { it.HighestAwardDate ?: "" }
        .take(8)
    ShelfCard {
        SectionLabel("Recent awards")
        if (awarded.isEmpty()) {
            EmptyState("No mastered or beaten games yet.")
        } else {
            awarded.forEach { entry -> AwardRow(entry, onClick = entry.GameID?.let { id -> { onOpenGame(id) } }) }
        }
    }
    Spacer(Modifier.height(12.dp))

    // Recent unlocks, one row per game (its unlocks under it); A on a game opens its achievements.
    ShelfCard {
        SectionLabel("Unlocked this week")
        if (recent.isEmpty()) {
            EmptyState("Nothing unlocked in the last 7 days.")
        } else {
            val byGame = recent.groupBy { it.GameID ?: -(it.GameTitle?.hashCode() ?: 0) }.values.toList()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                byGame.take(8).forEach { list -> UnlockGameRow(list, onClick = list.first().GameID?.let { id -> { onOpenGame(id) } }) }
            }
        }
    }
    }
}

/** One game in "Unlocked this week": its icon, name, console and points, then its unlocked badges in a row. */
@Composable
private fun UnlockGameRow(unlocks: List<RaRecentAchievement>, onClick: (() -> Unit)?) {
    val first = unlocks.first()
    val shape = uiRounded(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ShelfTheme.colors.surfaceRaised.copy(alpha = 0.6f))
            .then(if (onClick != null) Modifier.shelfClickable(shape) { onClick() } else Modifier)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = RaBadges.mediaUrl(first.GameIcon)
            if (icon != null) AsyncImage(model = icon, contentDescription = null, modifier = Modifier.size(38.dp).clip(uiRounded(8.dp)))
            else Box(Modifier.size(38.dp).clip(uiRounded(8.dp)).background(ShelfTheme.accent.copy(alpha = 0.2f)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(first.GameTitle ?: "Game", color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp),
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(first.ConsoleName, "${unlocks.size} unlocked").joinToString("  ·  "), color = ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("${unlocks.sumOf { it.Points ?: 0 }} pts", color = ShelfTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            unlocks.take(8).forEach { a ->
                val badge = RaBadges.url(a.BadgeName)
                if (badge != null) AsyncImage(model = badge, contentDescription = a.Title, modifier = Modifier.size(30.dp).clip(uiRounded(6.dp)))
            }
            if (unlocks.size > 8) Text("+${unlocks.size - 8}", color = ShelfTheme.textMuted, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(unlocks.first().Title ?: "", color = ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(2f, fill = false))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    com.shelf.launcher.Breadcrumbs.mark("StatTile")
    Column(
        modifier
            .clip(uiRounded(14.dp))
            .background(ShelfTheme.colors.surfaceRaised)
            .padding(12.dp)
    ) {
        Text(value, color = ShelfTheme.colors.textPrimary, fontSize = 18.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(18.sp), maxFontSize = 18.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(label, color = ShelfTheme.textMuted, fontSize = 11.sp)
    }
}

/** A game with its icon, console and a progress bar of unlocks. */
@Composable
private fun ProgressRow(entry: RaCompletionEntry, onClick: (() -> Unit)? = null) {
    val possible = entry.MaxPossible ?: 0
    val earned = maxOf(entry.NumAwarded ?: 0, entry.NumAwardedHardcore ?: 0)
    val fraction = if (possible > 0) (earned.toFloat() / possible).coerceIn(0f, 1f) else 0f
    val award = RaAward.from(entry.HighestAwardKind)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(uiRounded(12.dp)).shelfClickable(uiRounded(12.dp)) { onClick() } else Modifier)
            .padding(vertical = 7.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = RaBadges.mediaUrl(entry.ImageIcon)
        if (icon != null) {
            AsyncImage(model = icon, contentDescription = null, modifier = Modifier.size(42.dp).clip(uiRounded(8.dp)))
        } else {
            Box(Modifier.size(42.dp).clip(uiRounded(8.dp)).background(ShelfTheme.accent.copy(alpha = 0.2f)))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.Title ?: "Game", color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp),
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (award != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(award.label.uppercase(), color = ShelfTheme.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(entry.ConsoleName ?: "", color = ShelfTheme.textMuted, fontSize = 11.sp)
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(uiRounded(3.dp))
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(ShelfTheme.accent))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("${(fraction * 100).toInt()}%", color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("$earned/$possible", color = ShelfTheme.textMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AwardRow(entry: RaCompletionEntry, onClick: (() -> Unit)? = null) {
    com.shelf.launcher.Breadcrumbs.mark("AwardRow")
    val award = RaAward.from(entry.HighestAwardKind)
    if (award != null) {
    val tint = when (award) {
        RaAward.MASTERED -> Color(0xFFF5C542)
        RaAward.COMPLETED -> Color(0xFFD5DAE1)
        RaAward.BEATEN_HARDCORE -> Color(0xFF7FD3F5)
        RaAward.BEATEN -> Color(0xFFB0BEC5)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(uiRounded(12.dp)).shelfClickable(uiRounded(12.dp)) { onClick() } else Modifier)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = RaBadges.mediaUrl(entry.ImageIcon)
        if (icon != null) {
            AsyncImage(model = icon, contentDescription = null, modifier = Modifier.size(38.dp).clip(uiRounded(8.dp)))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.Title ?: "Game", color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp),
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(entry.ConsoleName ?: "", color = ShelfTheme.textMuted, fontSize = 11.sp)
        }
        Box(
            Modifier
                .clip(uiRounded(50))
                .background(tint.copy(alpha = 0.18f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(award.label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
    }
}

// ---- Category order ----

private fun categoryLabel(key: String): String = when (key) {
    CategorySettings.ALL -> "All"
    CategorySettings.CONSOLES -> "Consoles (each console)"
    CategorySettings.COLLECTIONS -> "Collections, franchises & status"
    // Update 132: only shown on the Cross menu.
    SpecialFilter.TROPHIES.name -> "Achievements (Cross menu only)"
    else -> runCatching { SpecialFilter.valueOf(key).label }.getOrDefault(key)
}

@Composable
private fun CategoryOrderCard(
    categories: CategorySettings,
    onMove: (String, Int) -> Unit,
    onToggleHidden: (String) -> Unit,
    onReset: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("CategoryOrderCard")
    val known = CategorySettings.known(SpecialFilter.values().map { it.name })
    val order = categories.normalized(known)
    ShelfCard {
        SectionLabel("Category order")
        MutedNote("The order of the category pills above the dock, and of L2 / R2. Hidden ones are skipped.")
        Spacer(Modifier.height(8.dp))
        order.forEachIndexed { index, key ->
            val hidden = key in categories.hidden
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${index + 1}.  ${categoryLabel(key)}",
                    color = if (hidden) ShelfTheme.textMuted else ShelfTheme.colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Chip("▲", accent = false, enabled = index > 0) { onMove(key, -1) }
                Spacer(Modifier.width(6.dp))
                Chip("▼", accent = false, enabled = index < order.lastIndex) { onMove(key, 1) }
                Spacer(Modifier.width(6.dp))
                Chip(if (hidden) "Hidden" else "Shown", accent = !hidden) { onToggleHidden(key) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Chip("Reset order", accent = false, onClick = onReset)
    }
}

/**
 * Update 133: Settings › Home › Save changes for — All layouts / Only <the layout on screen> (data/LayoutOnly.kt). What
 * changes in Settings › Home while "Only" is picked is kept for that layout; the others keep the shared settings. Back to
 * "All layouts" each time you leave Settings › Home.
 */
@Composable
private fun LayoutOnlyCard(appearance: AppearanceSettings, onAppearanceChange: (AppearanceSettings) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val layout = appearance.homeLayout
    // Noting what changes starts here and is kept when Settings › Home closes (or another tab opens).
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.shelf.launcher.data.LayoutOnly.begin(context, layout)
        onDispose { com.shelf.launcher.data.LayoutOnly.end(context) }
    }
    val only = com.shelf.launcher.data.LayoutOnly.onlyThis.value
    val own = com.shelf.launcher.data.LayoutOnly.count(context, layout)
    ShelfCard {
        SectionLabel("Save changes for")
        ChipFlow {
            Chip("All layouts", accent = !only) { com.shelf.launcher.data.LayoutOnly.setOnlyThis(context, false) }
            Chip("Only " + layout.label, accent = only) { com.shelf.launcher.data.LayoutOnly.setOnlyThis(context, true) }
        }
        Spacer(Modifier.height(4.dp))
        MutedNote(
            if (only) "What you change in Settings › Home now is kept for ${layout.label} only; the other layouts keep theirs. Back to All layouts when you leave Settings › Home."
            else "What you change in Settings › Home is for every layout. Pick Only ${layout.label} to keep changes for this layout alone."
        )
        if (own > 0) {
            Spacer(Modifier.height(6.dp))
            MutedNote("${layout.label} has $own setting${if (own == 1) "" else "s"} of its own.")
            Spacer(Modifier.height(4.dp))
            ChipFlow {
                Chip("Use the shared settings here again", accent = false) {
                    if (com.shelf.launcher.data.LayoutOnly.clear(context, layout)) onAppearanceChange(com.shelf.launcher.data.AppearanceStore(context).load())
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

/** Settings › Startup intro › Preview: plays the intro full screen; any button or tap ends it early. */
@Composable
private fun IntroPreviewDialog(style: com.shelf.launcher.data.IntroStyle, onDone: () -> Unit) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveDialogWindow()
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { e -> if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyUp) onDone(); true }
                .pointerInput(Unit) { detectTapGestures { onDone() } }
        ) {
            VoltcapIntro(onDone = onDone, style = style)
        }
    }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(50); runCatching { focus.requestFocus() } }
}

/** Settings › Backup: keep Voltcap's files in its private folder or in a visible "Voltcap" folder. */
@Composable
private fun AppStorageCard() {
    val context = LocalContext.current
    var root by remember { mutableStateOf(com.shelf.launcher.data.AppStorage.useRootFolder(context)) }
    var refresh by remember { mutableStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(o)
        onDispose { lifecycleOwner.lifecycle.removeObserver(o) }
    }
    val allowed = remember(refresh) { com.shelf.launcher.data.AppStorage.canUseRootFolder() }
    ShelfCard {
        SectionLabel("Where Voltcap keeps its files")
        MutedNote("Saved artwork, guides, your music and sounds, ROM list, RetroAchievements data, crash reports, settings snapshots and a full settings copy (Settings/voltcap-settings.json), and Drive sync backups / conflicts. Only Android's own settings database and your sign-ins have to stay private.")
        Spacer(Modifier.height(6.dp))
        RadioLine("Voltcap's private folder", !root, "Android/data — hidden, removed with the app.") {
            root = false; com.shelf.launcher.data.AppStorage.setUseRootFolder(context, false)
        }
        RadioLine("A \"Voltcap\" folder on the storage", root, "Internal storage / Voltcap — you can see, copy and back it up; it stays if the app is removed.") {
            root = true; com.shelf.launcher.data.AppStorage.setUseRootFolder(context, true)
        }
        if (root && !allowed) {
            Spacer(Modifier.height(6.dp))
            MutedNote("Needs \"All files access\" for Voltcap. Until then it keeps using the private folder.")
            ChipFlow {
                Chip("Allow all files access") {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                .setData(android.net.Uri.parse("package:" + context.packageName))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        }
        if (root) {
            Spacer(Modifier.height(4.dp))
            MutedNote("Takes effect the next time Voltcap starts; everything is copied over then (saved art is fetched again instead).")
        }
    }
}


/** "750 MB" / "2.5 GB". */
private fun formatMb(mb: Int): String = if (mb >= 1024) {
    val gb = mb / 1024f
    if (gb >= 10f || gb == gb.toInt().toFloat()) "${gb.toInt()} GB" else "%.1f GB".format(java.util.Locale.US, gb)
} else "$mb MB"


/** [base]'s strength and brightness with [hueFrom]'s hue (at least [minSat] / [minVal]), as ARGB. */
internal fun withHueOf(hueFrom: Int, base: Int, minSat: Float, minVal: Float): Int {
    val h = FloatArray(3).also { android.graphics.Color.colorToHSV(hueFrom, it) }
    val b = FloatArray(3).also { android.graphics.Color.colorToHSV(base, it) }
    return android.graphics.Color.HSVToColor(floatArrayOf(h[0], maxOf(b[1], minSat), maxOf(b[2], minVal)))
}

/** Appearance › Music player: the home screen's music player and its lyrics. */
@Composable
private fun MusicPlayerAppearance(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
        // Edit Home: move the player anywhere on the home screen (touch or controller), not only a corner.
        ShelfCard {
            SectionLabel("Position")
            MutedNote("Pick a corner below, or put it anywhere with Edit Home (also in the home Options menu). Settings closes while you move it.")
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                Chip("Edit Home…") { HomeEditState.open() }
                if (HomeWidgets.position(HomeWidgets.MUSIC) != null) Chip("Back to its corner", accent = false) { HomeWidgets.reset(HomeWidgets.MUSIC) }
            }
        }
        Spacer(Modifier.height(10.dp))
        // The cover's blur behind every music player (corner, R1, full player, second screen, screensaver).
        ShelfCard {
            SectionLabel("Album art behind the players")
            ChipFlow {
                Chip(if (appearance.secondMusicBlurArt) "✓ Blurred album art" else "Blurred album art", accent = appearance.secondMusicBlurArt) {
                    onChange(appearance.copy(secondMusicBlurArt = !appearance.secondMusicBlurArt))
                }
            }
            Spacer(Modifier.height(8.dp))
            PercentSlider(
                "Blur strength",
                appearance.secondMusicBlur,
                { onChange(appearance.copy(secondMusicBlur = it)) },
                enabled = appearance.secondMusicBlurArt
            )
            MutedNote("Behind every music player: the corner and R1 players, the full player, the second screen, over games and the screensaver.")
        }
        Spacer(Modifier.height(10.dp))
        ShelfCard {
            RowChoice("Where the player sits", com.shelf.launcher.data.MusicPlayerPosition.values(), appearance.musicPlayerPosition, { it.label }) {
                onChange(appearance.copy(musicPlayerPosition = it, miniPlayer = true))
            }
            val lyricsContext = androidx.compose.ui.platform.LocalContext.current
            RowChoice("Where the lyrics show", com.shelf.launcher.data.LyricsPosition.values(), appearance.lyricsPosition, { it.label }) {
                onChange(appearance.copy(lyricsPosition = it, miniPlayerLyrics = true, miniPlayer = true))
                // The music themes place their own lyrics: move those too, so the change shows right away
                // whichever background is on ("Away from the cover" keeps the theme's own place).
                when (it) {
                    com.shelf.launcher.data.LyricsPosition.WITH_PLAYER -> com.shelf.launcher.data.MusicLyricsSpot.IN_PLAYER
                    com.shelf.launcher.data.LyricsPosition.TOP -> com.shelf.launcher.data.MusicLyricsSpot.TOP
                    com.shelf.launcher.data.LyricsPosition.BOTTOM -> com.shelf.launcher.data.MusicLyricsSpot.BOTTOM
                    com.shelf.launcher.data.LyricsPosition.AWAY -> null
                }?.let { spot -> com.shelf.launcher.data.MusicThemeSettings.setLyricsSpot(lyricsContext, spot) }
            }
            LyricsLayoutSettings()
            MutedNote("A hotkey can show / hide it too (Settings › Controls).")
        }
        Spacer(Modifier.height(10.dp))
        ShelfCard { CornerVisualizerSettings() }
        Spacer(Modifier.height(10.dp))
        SettingsGrid {
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.MusicNote,
                    title = "Music player",
                    subtitle = "A small player in the top-left corner while Spotify or another music app plays. " +
                        "Needs notification access.",
                    checked = appearance.miniPlayer,
                    onChange = { onChange(appearance.copy(miniPlayer = it)) },
                    modifier = m
                )
            }
            item { m -> com.shelf.launcher.ui.spotify.LyricsOptInRow(m) }
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.MusicNote,
                    title = "Lyrics under the player",
                    subtitle = if (appearance.miniPlayer) {
                        "The current line of the song, synced when available (from LRCLIB)."
                    } else {
                        "Turn the music player on to see lyrics."
                    },
                    checked = appearance.miniPlayerLyrics && appearance.miniPlayer,
                    onChange = { onChange(appearance.copy(miniPlayerLyrics = it, miniPlayer = appearance.miniPlayer || it)) },
                    modifier = m
                )
            }
            // Update 117.
            item { m ->
                SwitchRow(
                    icon = Icons.Filled.MusicNote,
                    title = "Player in the layout's style",
                    subtitle = "The small player takes each layout's own look: an LCD on the Circuit board, a white pill on the Channel grid, " +
                        "a flat tile on Live tiles, a wooden label on the Shelf… Off: the album art's colour everywhere.",
                    checked = com.shelf.launcher.data.Features.musicSkins.state.value,
                    onChange = { com.shelf.launcher.data.Features.musicSkins.value = it },
                    modifier = m
                )
            }
        }
}


/** Pick the screensaver's theme: every animated theme (music themes only for [music]), with its badge. */
@Composable
private fun SaverThemePicker(music: Boolean, current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    // Update 125: every theme can be the screensaver, the music visualizers too (they move with the music; without
    // music they idle along). The fitting ones come first.
    val themes = remember(music) { AppTheme.values().sortedBy { if (it.motif.reactive == music) 0 else 1 } }
    Dialog(onDismissRequest = onDismiss) {
        ImmersiveDialogWindow()
        ShelfCard {
            SectionLabel(if (music) "Music theme for the screensaver" else "Theme for the screensaver")
            androidx.compose.foundation.lazy.LazyColumn(Modifier.height(420.dp)) {
                item {
                    Chip(if (music) "Now playing (default)" else "Your current theme", accent = current == null) { onPick(null) }
                    Spacer(Modifier.height(6.dp))
                }
                items(themes.size) { i ->
                    val t = themes[i]
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        ThemeBadge(t, 28.dp)
                        Spacer(Modifier.width(8.dp))
                        Chip(t.label, accent = current == t.name) { onPick(t.name) }
                    }
                }
            }
        }
    }
}

/** Appearance › Startup categories: the category Voltcap opens on, and whether the category screen comes first. */
@Composable
private fun StartupCategoriesCard(appearance: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
    ShelfCard {
        SectionLabel("Start in")
        MutedNote("The category Voltcap opens on. Continue playing opens on All until you've played something.")
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            // Update 125: more to start in — the smart lists and the Android games.
            buildList {
                addAll(listOf(
                    "RECENT" to "Continue playing", "LAST" to "Where I left off", "ALL" to "All",
                    "FAVORITES" to "Favorites", "MOST_PLAYED" to "Most played",
                    "IN_PROGRESS" to "In progress", "ALMOST_MASTERED" to "Almost mastered", "MASTERED" to "Mastered",
                    "NEVER_PLAYED" to "Never played", "FORGOTTEN" to "Not played lately",
                    "P:" + com.shelf.launcher.ANDROID_GAMES_PLATFORM to "Android games"
                ))
                if (com.shelf.launcher.data.Features.smartContinue.state.value) add("SMART_CONTINUE" to "Smart Continue")
            }.forEach { (key, label) ->
                Chip(label, accent = appearance.startCategory == key) { onChange(appearance.copy(startCategory = key)) }
            }
        }
    }
}

/** Carousel tilt: outward / inward, and both sides or only one (shown while "Tilt the other games" is on). */
@Composable
private fun CarouselTiltChoices() {
    val f = com.shelf.launcher.data.Features
    // Update 104: more carousel views and options (shared by every carousel).
    RowChoice("View", com.shelf.launcher.ui.CarouselView.values(), f.carouselView.state.value, { it.label }) {
        f.carouselView.value = it
        // (Update 134: Arc and Turned row stand on a glossy floor — the reflection comes on with them; switch it off below.
        // Update 147: the Spine wall too, and all three on the dark stage, like a disc loader's case views.)
        if (it.cases) { f.carouselReflect.value = true; f.carouselStage.value = true }
    }
    CarouselViewPreview(f.carouselView.state.value)
    // Update 137: where each view works, said right here (it used to depend on the layout without saying so).
    val v = f.carouselView.state.value
    MutedNote(
        when {
            // (Update 149: the Voltcap row has used the full carousel since update 141.)
            v.cases -> "${v.label}: real 3D cases in the Voltcap row, Grid, Side menu and TV room carousels. In the Tiles carousel the covers stand as in ${com.shelf.launcher.ui.Carousel.flatView.label}."
            v == com.shelf.launcher.ui.CarouselView.TUCKED || v == com.shelf.launcher.ui.CarouselView.FLIP || v == com.shelf.launcher.ui.CarouselView.FLAT || v == com.shelf.launcher.ui.CarouselView.POP ->
                "${v.label}: every carousel (Voltcap row, Tiles, Grid, Side menu, TV room). With Spines aside (Covers look) the games beside the middle one are slim spines in Grid, Side menu and TV room."
            else -> "${v.label}: every carousel (Voltcap row, Tiles, Grid, Side menu, TV room)."
        } + " Hero has its own carousel (Hero carousel options)."
    )
    // Update 187: Box flow's box you're on sways as it moves.
    if (v == com.shelf.launcher.ui.CarouselView.BOX_FLOW) SwitchRow(
        Icons.Filled.ViewInAr, "Box sways as you move",
        "The box you're on leans a little into each move and settles facing you when you stop.",
        f.boxFlowTilt.state.value, { f.boxFlowTilt.value = it }
    )
    if (v == com.shelf.launcher.ui.CarouselView.TUCKED || v == com.shelf.launcher.ui.CarouselView.FLIP || v == com.shelf.launcher.ui.CarouselView.FLAT || v == com.shelf.launcher.ui.CarouselView.POP) {
        val sp = f.spineLook.state.value
        ChipFlow { Chip((if (sp) "✓ " else "") + "Spines aside", accent = sp) { f.spineLook.value = !sp } }
    }
    Spacer(Modifier.height(6.dp))
    PercentSlider("Spacing", f.carouselGap.state.value, { f.carouselGap.value = (it / 5) * 5 }, min = 60, max = 160, valueText = { "$it%" })
    // Update 185: the gap set by hand alone, or wider for a short list.
    SwitchRow(
        Icons.Filled.ViewInAr, "Spread out a short list",
        "With only a few games they stand further apart, so the sides aren't empty. Off: Spacing alone sets the gap.",
        f.carouselSpread.state.value, { f.carouselSpread.value = it }
    )
    SwitchRow(
        Icons.Filled.ViewInAr, "Reflection", "A faint mirror image of each game under it, as on a glossy floor.",
        f.carouselReflect.state.value, { f.carouselReflect.value = it }
    )
    // Update 147: the black stage of a disc loader's case views (on with Arc, Spine wall and Turned row).
    SwitchRow(
        Icons.Filled.ViewInAr, "Dark stage", "The carousel stands on a dark floor that fades into black, the background dimmed around it.",
        f.carouselStage.state.value, { f.carouselStage.value = it }
    )
    // Update 187.
    if (f.carouselStage.state.value) SwitchRow(
        Icons.Filled.ViewInAr, "Stage in the game's colours",
        "The stage's light and floor take the colours of the cover you're on, gliding to the next game's. Off: your colour.",
        f.carouselStageGameColor.state.value, { f.carouselStageGameColor.value = it }
    )
    // Update 134: real 3D cases in any view.
    SwitchRow(
        Icons.Filled.ViewInAr, "3D cases",
        if (f.carouselView.state.value.cases) "Always on in ${f.carouselView.state.value.label}: each game a real case, cover on the front, its spine on the side."
        // (Update 162: the case looks only — cartridges, cards and discs show as themselves.)
        else "Each game a real case: cover on the front, its spine on the side, turned in true 3D. Cartridges, cards and discs show as themselves.",
        f.carousel3d.state.value || f.carouselView.state.value.cases, { f.carousel3d.value = it }
    )
    // Update 158: the 3D case you're on faces you (default) or stands turned like the others.
    if (f.carousel3d.state.value || f.carouselView.state.value.cases) {
        Spacer(Modifier.height(6.dp))
        com.shelf.launcher.ui.PickedFacingChoice(note = false)
    }
    if (!f.tvCarouselTilt.state.value) return
    RowChoice("Tilt direction", com.shelf.launcher.ui.TiltDir.values(), f.tvCarouselTiltDir.state.value, { it.label }) { f.tvCarouselTiltDir.value = it }
    RowChoice("Tilt which games", com.shelf.launcher.ui.TiltSides.values(), f.tvCarouselTiltSides.state.value, { it.label }) { f.tvCarouselTiltSides.value = it }
}


/**
 * Settings › Categories: everything about categories in one place (it used to be spread over Library, Appearance ›
 * Name & category, Appearance › Category screen › Platform browser and Startup categories).
 */
@Composable
private fun CategoriesContent(
    appearance: AppearanceSettings,
    onChange: (AppearanceSettings) -> Unit,
    categories: CategorySettings,
    onMoveCategory: (String, Int) -> Unit,
    onToggleCategoryHidden: (String) -> Unit,
    onResetCategories: () -> Unit,
    userCollections: Map<String, Set<String>>,
    onRenameCollection: (String, String) -> Unit,
    onDeleteCollection: (String) -> Unit
) {
    val F = com.shelf.launcher.data.Features
    Column {
        MutedNote("A category is a console, a collection or a smart list (Favorites, Continue playing…). L2 / R2 step through them on the home screen; the category screen shows them all at once.")
        Spacer(Modifier.height(10.dp))
        FoldGroup("Which categories", summary = "Order, hidden ones, collections, where Voltcap starts", key = "cat_which", startOpen = true) {
            CategoryOrderCard(categories, onMoveCategory, onToggleCategoryHidden, onResetCategories)
            Spacer(Modifier.height(12.dp))
            StartupCategoriesCard(appearance, onChange)
            Spacer(Modifier.height(12.dp))
            CollectionsSettings(appearance, onChange, userCollections, onRenameCollection, onDeleteCollection)
        }
        FoldGroup("On the home screen", summary = "The category's name above the games, logos, L2 / R2", key = "cat_home") {
            ShelfCard {
                RowChoice("Category name", com.shelf.launcher.data.CategoryBar.values(), appearance.categoryBar, { it.label }) {
                    onChange(appearance.copy(categoryBar = it))
                }
                RowChoice("It sits", com.shelf.launcher.data.RowSide.values(), appearance.categorySide, { it.label }) {
                    onChange(appearance.copy(categorySide = it))
                }
                // Console / platform categories only: keep the name in one fixed place (or hide it).
                Text("Console categories", color = ShelfTheme.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                ChipFlow {
                    com.shelf.launcher.data.Features.ConsoleNamePin.values().forEach { p ->
                        Chip(p.label, accent = F.consoleNamePin.state.value == p) {
                            F.consoleNamePin.value = p
                            // Pinned / hidden works on the single category name.
                            if (p != com.shelf.launcher.data.Features.ConsoleNamePin.FLOW && appearance.categoryBar == com.shelf.launcher.data.CategoryBar.FULL) {
                                onChange(appearance.copy(categoryBar = com.shelf.launcher.data.CategoryBar.CURRENT))
                            }
                        }
                    }
                }
                MutedNote("Pinned, a console's name stays put above the games (centred or at the left) while its look still changes with each category.")
                Spacer(Modifier.height(14.dp))
                RowChoice("When the category changes", com.shelf.launcher.data.CategoryAnim.values(), appearance.categoryAnim, { it.label }) {
                    onChange(appearance.copy(categoryAnim = it))
                }
                SwitchRow(
                    Icons.Filled.SwapHoriz, "Games slide in", "With L2 / R2 the games slide in from that side.",
                    F.categorySlide.state.value, { F.categorySlide.value = it }
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    icon = Icons.Filled.VideogameAsset,
                    title = "Console logos next to the name",
                    subtitle = "RetroAchievements' little system icon beside a console's name on the home screen (needs a RetroAchievements account). Never on the category screen.",
                    checked = appearance.categoryLogos,
                    onChange = { onChange(appearance.copy(categoryLogos = it)) }
                )
                Spacer(Modifier.height(6.dp))
                MutedNote("The name's style (backdrop, glow…) is shared with the game's name: Home › Name & logo. A scene of its own per category: Theme › Background › Animated scene.")
            }
        }
        FoldGroup("Category screen", summary = F.platformView.state.value.label, key = "cat_screen", startOpen = true) {
            ShelfCard {
                RowChoice("When it opens", com.shelf.launcher.data.CategoryScreenMode.values(), appearance.categoryScreen, { it.label }) {
                    onChange(appearance.copy(categoryScreen = it))
                }
                Text("Look", color = ShelfTheme.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                ChipFlow {
                    com.shelf.launcher.data.Features.PlatformView.values().forEach { v -> Chip(v.label, accent = F.platformView.state.value == v) { F.platformView.value = v } }
                }
                Spacer(Modifier.height(6.dp))
                MutedNote(categoryLookNote(F.platformView.state.value))
                Spacer(Modifier.height(14.dp))
                Text("Cards show", color = ShelfTheme.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                ChipFlow {
                    com.shelf.launcher.data.CategoryTileArt.values().forEach { a -> Chip(a.label, accent = F.categoryArt.state.value == a) { F.categoryArt.value = a } }
                }
                Spacer(Modifier.height(6.dp))
                MutedNote(
                    when (F.categoryArt.state.value) {
                        com.shelf.launcher.data.CategoryTileArt.SLIDESHOW -> "Each card slowly fades through the art of the games in it (only the cards on screen move; still with Lite effects)."
                        com.shelf.launcher.data.CategoryTileArt.LAST_PLAYED -> "Each card shows the game you played last in it — its first game when you haven't played any yet."
                        com.shelf.launcher.data.CategoryTileArt.COVERS -> "A few of its covers, fanned out."
                        com.shelf.launcher.data.CategoryTileArt.CONSOLE -> "A photo of the console (consoles; the others fade through their games)."
                        com.shelf.launcher.data.CategoryTileArt.PLAIN -> "Just its colour and its name."
                    } + " The same in all four looks. Your own picture for a card (Y on it) always wins; X removes it."
                )
                Spacer(Modifier.height(14.dp))
                if (F.platformView.state.value == com.shelf.launcher.data.Features.PlatformView.TILES) {
                    RowChoice("Cards", com.shelf.launcher.data.CategoryTileStyle.values(), appearance.categoryTileStyle, { it.label }) {
                        onChange(appearance.copy(categoryTileStyle = it))
                    }
                }
                CategoryLookOptions(F.platformView.state.value)
            }
        }
        FoldGroup("Per platform", summary = "Intro videos, music, scraped logo / photo / fan art", key = "cat_platform") {
            ShelfCard { PerPlatformSettings() }
        }
    }
}


/**
 * Settings › Theme › Themes: right where you pick a theme, what its colours follow — its own colours, the game you're
 * on, or the music playing (the album cover). Sets this theme's colour source (Colours & look › Colours) and where
 * artwork colours come from; "Every theme" sets it for all themes that can be recoloured.
 */
@Composable
private fun ColorsFollowRow(theme: AppTheme) {
    val f = com.shelf.launcher.data.Features
    val look = f.lookFor(theme.name)
    val now = when {
        !theme.recolorable -> null
        look.colorSource != com.shelf.launcher.data.Features.ColorSource.GAME -> "theme"
        f.artColorSource.state.value == com.shelf.launcher.data.Features.ArtColorSource.ALBUM -> "music"
        else -> "game"
    }
    SectionLabel("Colours follow")
    if (!theme.recolorable) {
        MutedNote("${theme.label} keeps its own colours (it takes them from its own art or the album cover).")
        return
    }
    Spacer(Modifier.height(6.dp))
    var allThemes by remember { mutableStateOf(false) }
    fun apply(choice: String) {
        val targets = if (allThemes) AppTheme.values().filter { it.recolorable } else listOf(theme)
        targets.forEach { t ->
            val l = f.lookFor(t.name)
            val src = if (choice == "theme") com.shelf.launcher.data.Features.ColorSource.THEME else com.shelf.launcher.data.Features.ColorSource.GAME
            if (l.colorSource != com.shelf.launcher.data.Features.ColorSource.CUSTOM || choice != "theme") f.setLook(t.name, l.copy(colorSource = src))
        }
        when (choice) {
            "game" -> f.artColorSource.value = com.shelf.launcher.data.Features.ArtColorSource.GAME
            "music" -> f.artColorSource.value = com.shelf.launcher.data.Features.ArtColorSource.ALBUM
        }
    }
    ChipFlow {
        Chip((if (now == "theme") "✓ " else "") + "Its own colours", accent = now == "theme") { apply("theme") }
        Chip((if (now == "game") "✓ " else "") + "The game you're on", accent = now == "game") { apply("game") }
        Chip((if (now == "music") "✓ " else "") + "The music playing", accent = now == "music") { apply("music") }
        Chip(if (allThemes) "✓ Every theme" else "Only this theme", accent = false) { allThemes = !allThemes }
    }
    Spacer(Modifier.height(4.dp))
    MutedNote(
        when (now) {
            "game" -> "Background, panels and accent take the colours of the game you're on, gliding as you move."
            "music" -> "The colours of the album cover while music plays; the game's when nothing plays."
            else -> "The theme as it was made."
        }
    )
}


/** Settings › Game look › Covers, cases & discs: how discs, cartridges and cases move — spin, bounce, both, or still. */
@Composable
private fun MediaMotionChoice() {
    val f = com.shelf.launcher.data.Features
    SectionLabel("Moving discs, cartridges & cases")
    Spacer(Modifier.height(6.dp))
    RowChoice("Discs", com.shelf.launcher.data.Features.MediaMotion.values(), f.discMotion.state.value, { it.label }) { f.discMotion.value = it }
    Spacer(Modifier.height(6.dp))
    RowChoice("Cartridges", com.shelf.launcher.data.Features.MediaMotion.values(), f.cartMotion.state.value, { it.label }) { f.cartMotion.value = it }
    Spacer(Modifier.height(6.dp))
    RowChoice("Cases", com.shelf.launcher.data.Features.MediaMotion.values(), f.caseMotion.state.value, { it.label }) { f.caseMotion.value = it }
    Spacer(Modifier.height(4.dp))
    MutedNote(
        "Discs: spin = turning like it's playing, bounce = a slow soft bob. Cartridges: spin = turning slowly left and right, " +
            "bounce = a little hop. Cases (box art) move like cartridges. Both moves together, or still. Off with Lite effects or animations off."
    )
    Spacer(Modifier.height(6.dp))
    MediaMotionPreview(f.discMotion.state.value, f.cartMotion.state.value, f.caseMotion.state.value)
}


/** Cover → wide capsule (Home › Animations, Home › Layout options, Game look › Cards): how the game you're on opens wide. */
@Composable
private fun CapsuleRevealChoice(firstOnly: Boolean = false) {
    val f = com.shelf.launcher.data.Features
    // Update 129: "Wide for the first game only" keeps its own (off by default).
    val pref = if (firstOnly) f.capsuleRevealFirst else f.capsuleReveal
    RowChoice(if (firstOnly) "First game's wide capsule" else "Cover → wide capsule", com.shelf.launcher.data.Features.CapsuleReveal.values(), pref.state.value, { it.label }) {
        pref.value = it
    }
    MutedNote(
        when (pref.state.value) {
            com.shelf.launcher.data.Features.CapsuleReveal.ROLL -> "Quick and smooth: the wide art rolls out from the left over the cover, with a soft edge."
            com.shelf.launcher.data.Features.CapsuleReveal.SLIDE -> "The cover slides out to the left while the wide art slides in from the right."
            com.shelf.launcher.data.Features.CapsuleReveal.FLIP -> "The card turns over like a playing card: the cover on the front, the wide art on the back."
            com.shelf.launcher.data.Features.CapsuleReveal.SWEEP -> "A band of light runs across while the wide art fades in."
            com.shelf.launcher.data.Features.CapsuleReveal.WIPE -> "The older hard-edged wipe from the left."
            com.shelf.launcher.data.Features.CapsuleReveal.NONE -> "No animation: the wide art is simply there."
        }
    )
    Spacer(Modifier.height(6.dp))
    CapsuleRevealPreview(pref.state.value)
}


/**
 * The game size of [a]'s layout in % (update 105): its own size where it has one (Tile home, Cross menu, Icon bar),
 * else a per-layout size, on top of Small / Medium / Large on the Voltcap row, the Grid and the paged layouts.
 * Also on the R1 Size tile (hold).
 */
@Composable
internal fun GameSizeSlider(a: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
    val range = GameSize.sliderRange(a.homeLayout)
    PercentSlider(
        if (GameSize.stepsOnly(a.homeLayout)) "Game size (fine)" else "Game size",
        GameSize.sliderPct(a).coerceIn(range),
        { GameSize.setSlider(a, (it / 5) * 5, onChange) },
        min = range.first, max = range.last, valueText = { "$it%" }
    )
}

/** Layout options › Game info on screen (update 93): which of the Voltcap row's info parts this layout shows too. */
@Composable
internal fun OverlayPartsChoice(layout: com.shelf.launcher.data.HomeLayout) {
    val f = com.shelf.launcher.data.Features
    val on = f.overlayPartsFor(layout)
    SectionLabel("Game info on screen")
    Spacer(Modifier.height(6.dp))
    ChipFlow {
        // Update 104: the game's logo and description too, on every layout.
        overlayChoices(layout).forEach { (id, label) ->
            val sel = id in on
            Chip((if (sel) "✓ " else "") + label, accent = sel) { f.toggleOverlayPart(layout, id) }
        }
    }
    Spacer(Modifier.height(6.dp))
    RowChoice("Behind them", com.shelf.launcher.data.Features.PartBacking.values(), f.partBacking.state.value, { it.label }) { f.partBacking.value = it }
    // Update 133: how solid that backing is (the parts had become too see-through).
    if (f.partBacking.state.value != com.shelf.launcher.data.Features.PartBacking.NONE) {
        Spacer(Modifier.height(4.dp))
        PercentSlider("Backing", f.partBackingSolid.state.value, { f.partBackingSolid.value = ((it / 5) * 5).coerceIn(0, 100) }, min = 0, max = 100, valueText = { if (it == 0) "Light" else "$it %" })
    }
    Spacer(Modifier.height(4.dp))
    MutedNote(
        "The same parts as the Voltcap row's game header, info and news, floating on this layout (stacked on the right until " +
            "you move them). Which stat boxes and buttons show: Settings › Home › Next to the row › Game header. Drag and resize them in Edit Home."
    )
    if (on.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Chip("Edit Home…") { HomeEditState.open() }
    }
    MutedNote("In Edit Home: + Add (Start) picks what to show, a part's red ✕ (Select) takes it off again.")
}
