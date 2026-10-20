package com.shelf.launcher.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shelf.launcher.LauncherViewModel

/**
 * The home screen's own UI state (which popup is open, what the D-pad is on, …) in one
 * remembered object. LauncherScreenContent and its parts read and write these through
 * `with(flags)`, so the parts capture one object instead of dozens of separate states —
 * that keeps every generated method small enough for Android to compile ahead of time.
 */
internal class LauncherUiFlags(viewModel: LauncherViewModel, context: Context, initialSearch: Boolean) {
    var showDetails by viewModel.detailsOpenState
    var categoryScreenOpen by viewModel.categoryScreenOpenState
    var showSettings by mutableStateOf(false)
    var showMenu by mutableStateOf(false)
    var showEmulatorSettings by mutableStateOf(false)
    var showIgdb by mutableStateOf(false)
    var showRawg by mutableStateOf(false)
    var showTgdb by mutableStateOf(false)
    var showScreenScraper by mutableStateOf(false)
    var showNotifications by mutableStateOf(false)
    /** The notifications only, without the R1 quick tiles (update 105: the bell buttons; R1 opens the full panel). */
    var notificationsOnly by mutableStateOf(false)
    var showSpotify by mutableStateOf(false)
    var showMusicPopup by mutableStateOf(false)
    var showSyncStatus by mutableStateOf(false)
    var showSaveExplorer by mutableStateOf(false)
    /** Achievements / Stats / Profile, each on its own screen (not inside Settings). */
    var hubScreen by mutableStateOf<HubScreen?>(null)
    /** Hero carousel: which info page (◀ ▶) is showing; kept when the game changes. */
    var heroPage by mutableStateOf(0)
    var showShortcuts by mutableStateOf(false)
    var showAppDrawer by mutableStateOf(false)
    var showAppsLibrary by mutableStateOf(false)
    var editingArtworkForGame by mutableStateOf<com.shelf.launcher.data.Game?>(null)
    /** Update 195: the game whose cartridge shell is being picked (CartShellPicker.kt). */
    var cartShellForGame by mutableStateOf<com.shelf.launcher.data.Game?>(null)
    /** Update 287: the DS game whose case is being picked (DsCasePicker). */
    var dsCaseForGame by mutableStateOf<com.shelf.launcher.data.Game?>(null)
    var optionsForGame by mutableStateOf<com.shelf.launcher.data.Game?>(null)
    var showLibraryDialog by mutableStateOf(false)
    var showAddSteamGameDialog by mutableStateOf(false)
    var showAchievementsDialog by mutableStateOf(false)
    var configuringPlatform by mutableStateOf<String?>(null)
    var configuringArtForGame by mutableStateOf<com.shelf.launcher.data.Game?>(null)
    var configuringRaForGame by mutableStateOf<com.shelf.launcher.data.Game?>(null)
    var viewingAchievementsForGame by mutableStateOf<com.shelf.launcher.data.Game?>(null)
    var showSearchBar by mutableStateOf(initialSearch)
    var detailsAction by mutableStateOf(DetailsAction.PLAY)
    var achievementsShowMissing by mutableStateOf(false)
    var collectionsForGame by mutableStateOf<com.shelf.launcher.data.Game?>(null)
    var screensForGame by mutableStateOf<com.shelf.launcher.data.Game?>(null)
    var profileForGame by mutableStateOf<com.shelf.launcher.data.Game?>(null)
    var statusForGame by mutableStateOf<com.shelf.launcher.data.Game?>(null)
    var playStatsForGame by mutableStateOf<com.shelf.launcher.data.Game?>(null)
    var tipsForGame by mutableStateOf<com.shelf.launcher.data.Game?>(null)
    var guideForGame by mutableStateOf<com.shelf.launcher.data.Game?>(null)
    var viewingShots by mutableStateOf<Pair<List<java.io.File>, Int>?>(null)
    var galleryItems by mutableStateOf<List<GalleryItem>>(emptyList())
    var galleryIndex by mutableStateOf(0)
    var artViewer by mutableStateOf<Pair<List<GalleryItem>, Int>?>(null)
    var summaryExpanded by mutableStateOf(false)
    var achInlineOpen by mutableStateOf(false)
    var achInlineFilter by mutableStateOf(0)
    /** D-pad row in the inline achievement list (-1 = on the progress bar). */
    var achInlineIndex by mutableStateOf(-1)
    /** Inline achievement opened with A (details + comments), or null. */
    var achInlineExpanded by mutableStateOf<Int?>(null)
    var showControlsHelp by mutableStateOf(false)
    var showSortDialog by mutableStateOf(false)
    var showWhatToPlay by mutableStateOf(false)
    var steamAchFor by mutableStateOf<com.shelf.launcher.data.Game?>(null)
    var leaderboardsFor by mutableStateOf<com.shelf.launcher.data.Game?>(null)
    var manualFor by mutableStateOf<Pair<com.shelf.launcher.data.Game, java.io.File>?>(null)
    var launchingGame by mutableStateOf<com.shelf.launcher.data.Game?>(null)
    var pickingMissingSourceFor by mutableStateOf<com.shelf.launcher.data.Game?>(null)
    var stickY by mutableStateOf(0f)
    var rightStickX by mutableStateOf(0f)
    var screensaverOn by mutableStateOf(false)
    var quickJump by mutableStateOf<Char?>(null)
    var miniFocus by mutableStateOf<Int?>(null)
    var heldRepeat by mutableStateOf(false)
    var tileBarFocus by mutableStateOf<Int?>(null)
    var tileBarEnteredAt by mutableStateOf(0L)
    var menuStartCategory by mutableStateOf<String?>(null)
    var lastInputAt by mutableStateOf(System.currentTimeMillis())
    var swallowKeyUp by mutableStateOf(false)
    var showFirstRun by mutableStateOf(!com.shelf.launcher.data.FirstRun.isDone(context))
    var saveSyncForGame by mutableStateOf<com.shelf.launcher.data.Game?>(null)
    var dialogClosedAt by mutableStateOf(0L)
    var selectHeld by mutableStateOf(false)
    var selectComboUsed by mutableStateOf(false)
    var categoryIndex by mutableStateOf(0)
    var categoryImages by mutableStateOf(com.shelf.launcher.data.CategoryImagesStore(context).loadAll())
    var pickingImageFor by mutableStateOf<String?>(null)
    var categoryTiles by mutableStateOf<List<CategoryTile>>(emptyList())
    var stickLetter by mutableStateOf<Char?>(null)
    /** The platform browser (Slides / Wall / Grid): grabbed card, slide editor, Platform Options. */
    val pbState = PlatformBrowserState()
    /** A random game was asked for on this category: picked once the home screen shows it. */
    var pbRandomFor by mutableStateOf<String?>(null)
    /** Music of its own being set: (override key, title). */
    var musicOverrideFor by mutableStateOf<Pair<String, String>?>(null)
    /** A platform intro video playing before the game starts: (game, video address). */
    var introVideo by mutableStateOf<Pair<com.shelf.launcher.data.Game, String>?>(null)
}
