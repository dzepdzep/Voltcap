# Rule 11 (no truncated text) — remaining TRUNCATE findings (static_checks.py, 10 Nov 2026, next/0.6.70 @ 523a)

Read-only inventory for the backlog (nothing changed here). 286 MED findings in 82 files: `maxLines` + `TextOverflow.Ellipsis` or `take(n) + "…"`.
Popup / game-window texts are covered by the popup gate (popclip, R26) — those that showed a cut on the RP Classic were fixed in 522 / 523;
the rest are mostly settings rows, lists and secondary screens. Fix per screen with FullText / WinFullText / wrapping and a device check.

| File | Findings | Lines |
|---|---|---|
| ui/SettingsDialog.kt | 17 | 2956, 3005, 3008, 3164, 3259, 3369, 3429, 3579, 3587, 3605, 5114, 5376, 5378, 5390, 5405, 5434, 5486 |
| ui/GameDetailsOverlay.kt | 13 | 416, 618, 627, 765, 970, 971, 1035, 1062, 1217, 1225, 1444, 1450, 1483 |
| ui/SaveExplorer.kt | 13 | 463, 469, 514, 520, 673, 1396, 1397, 1468, 1500, 1513, 1522, 1526, 1593 |
| ui/GameWindowBanner.kt | 12 | 464, 685, 709, 728, 730, 739, 741, 834, 971, 1084, 1089, 1126 |
| ui/YearRecapDialog.kt | 9 | 285, 312, 444, 445, 491, 492, 500, 532, 544 |
| ui/spotify/SpotifyScreen.kt | 9 | 244, 509, 510, 535, 539, 556, 557, 726, 727 |
| ui/GameCard.kt | 8 | 611, 684, 809, 896, 915, 926, 1267, 1286 |
| ui/GameWindow.kt | 8 | 1741, 2477, 2481, 2535, 2540, 2614, 2620, 2675 |
| ui/SecondScreenVolt.kt | 8 | 106, 147, 148, 184, 259, 260, 293, 365 |
| ui/StyleTerminal.kt | 8 | 323, 379, 507, 509, 511, 518, 535, 543 |
| ui/MixerActivity.kt | 7 | 215, 223, 341, 388, 396, 400, 518 |
| ui/PlatformBrowser.kt | 7 | 638, 644, 664, 827, 830, 998, 999 |
| ui/SyncPrompts.kt | 7 | 248, 256, 361, 386, 402, 405, 500 |
| ui/TwinIcons.kt | 7 | 411, 415, 527, 530, 580, 730, 737 |
| ui/NotificationsPanel.kt | 6 | 163, 342, 381, 394, 512, 526 |
| ui/PlayHeader.kt | 6 | 111, 131, 133, 164, 294, 325 |
| ui/SessionSummary.kt | 6 | 175, 180, 196, 205, 220, 240 |
| ui/GuideScreen.kt | 5 | 194, 199, 326, 417, 418 |
| ui/HubScreens.kt | 5 | 224, 239, 240, 257, 258 |
| ui/PlayerPages.kt | 5 | 134, 161, 222, 255, 256 |
| ui/QuickMenuActivity.kt | 5 | 139, 153, 174, 175, 235 |
| ui/RowInfoPanel.kt | 5 | 110, 131, 149, 219, 230 |
| ui/StatsContent.kt | 5 | 230, 257, 285, 295, 306 |
| ui/CrossTrophies.kt | 4 | 340, 347, 353, 483 |
| ui/HomeEditSession.kt | 4 | 411, 412, 522, 526 |
| ui/RaSocialUi.kt | 4 | 75, 118, 119, 122 |
| ui/TwinDesk.kt | 4 | 408, 503, 654, 702 |
| ui/spotify/SpotifyPlayer.kt | 4 | 135, 136, 200, 203 |
| ui/CompanionActivity.kt | 3 | 267, 269, 335 |
| ui/DialogChrome.kt | 3 | 340, 530, 913 |
| ui/GameJourneyUi.kt | 3 | 145, 149, 153 |
| ui/QuickSettings.kt | 3 | 1272, 1408, 1571 |
| ui/RetroAchievementsDialog.kt | 3 | 167, 174, 204 |
| ui/SecondGamesPage.kt | 3 | 422, 442, 448 |
| ui/CartShellPicker.kt | 2 | 181, 212 |
| ui/CollectorBacks.kt | 2 | 347, 359 |
| ui/FolderIcons.kt | 2 | 170, 238 |
| ui/GamePlayStats.kt | 2 | 142, 143 |
| ui/GamePopupStatus.kt | 2 | 241, 243 |
| ui/GlanceWindow.kt | 2 | 323, 424 |
| ui/HomeEditLegend.kt | 2 | 68, 70 |
| ui/LaunchAnimation.kt | 2 | 346, 352 |
| ui/NewArrival.kt | 2 | 164, 168 |
| ui/PerfPage.kt | 2 | 104, 105 |
| ui/QsTilesCard.kt | 2 | 96, 101 |
| ui/RommBrowser.kt | 2 | 227, 248 |
| ui/SaveInspect.kt | 2 | 363, 373 |
| ui/SaveSyncUi.kt | 2 | 91, 215 |
| ui/StacksArch.kt | 2 | 232, 238 |
| ui/SteamImportDialog.kt | 2 | 194, 203 |
| ui/TileHome.kt | 2 | 440, 882 |
| ui/TwinShowcase.kt | 2 | 421, 440 |
| ui/WidgetBoard.kt | 2 | 656, 760 |
| ui/AppsLibraryDialog.kt | 1 | 175 |
| ui/ArtworkGallery.kt | 1 | 174 |
| ui/BadgeWall.kt | 1 | 258 |
| ui/CubeHub.kt | 1 | 704 |
| ui/EmulatorPickerDialog.kt | 1 | 277 |
| ui/FirstRunDialog.kt | 1 | 474 |
| ui/FitText.kt | 1 | 110 |
| ui/FoldGroup.kt | 1 | 77 |
| ui/GameDock.kt | 1 | 1008 |
| ui/GameLookPicker.kt | 1 | 937 |
| ui/GameLookUi.kt | 1 | 84 |
| ui/GuideSecondScreen.kt | 1 | 50 |
| ui/HomeChrome.kt | 1 | 369 |
| ui/LauncherScreen.kt | 1 | 4032 |
| ui/ManualReader.kt | 1 | 294 |
| ui/PresetsCard.kt | 1 | 229 |
| ui/ScrollingName.kt | 1 | 56 |
| ui/SecondScreen.kt | 1 | 436 |
| ui/SecondScreenDetails.kt | 1 | 135 |
| ui/SecondShowcase.kt | 1 | 121 |
| ui/SettingsSlider.kt | 1 | 103 |
| ui/ShortcutsPanel.kt | 1 | 383 |
| ui/StyleCrossMenu.kt | 1 | 381 |
| ui/StyleCustom.kt | 1 | 353 |
| ui/StyleMagazine.kt | 1 | 406 |
| ui/StylePocketQuest.kt | 1 | 752 |
| ui/VoltUi.kt | 1 | 544 |
| ui/WhoIsPlaying.kt | 1 | 74 |
| ui/WholeRows.kt | 1 | 64 |
