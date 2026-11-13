# Settings inventory (read-only, generated)

Generated from `data/Features.kt` (734 prefs) by `tools/settings_audit/inventory.py`. Nothing was changed: no setting removed, renamed or migrated.

Flags are mechanical leads for the owner review, not verdicts: NEVER-USED = nothing references it; SETTINGS-ONLY = only a settings screen touches it (shown and saved, but no other code reads it - a strong candidate for an option that does nothing, to confirm by hand); NO-SETTINGS-SCREEN = no settings screen file touches it (hidden, set elsewhere, or set by a layout's own menu); LAYOUT-ONLY = only layout files read it; similar = keys with the same stem under another prefix (possible duplicates across layouts).

## Checked by hand (12 Nov)

- `box_sides_chosen` (SETTINGS-ONLY): written when you pick Box sides, read nowhere - an internal marker left over, no effect you can see. Candidate to retire (owner review).
- `pb_follow_home`, `platform_media_scrape`, `pq_sounds`, `pq_sound_volume` (SETTINGS-ONLY): read by working code that lives in files named *Settings / *Options (PlatformBrowserSettings.kt, PlatformOptions.kt, PocketQuestSettings.kt) - in use; the flag is the file-name heuristic.
- `art_source_cover / _wide / _hero / _logo`: read by LauncherViewModel (in use).
- Not attempted here (needs the owner review): conflicts between settings, layout-by-layout compatibility, wording vs real effect.

| Flag | Count |
|---|---|
| NO-SETTINGS-SCREEN | 361 |
| LAYOUT-ONLY | 77 |
| SETTINGS-ONLY | 5 |

| Pref (Features.…) | Key | Type | Default | Read in (top 3) | Flags | Similar keys | Its own description |
|---|---|---|---|---|---|---|---|
| density | `density` | Enum | `Density.COMFORTABLE` | HeroCarousel.kt, SettingsDialog.kt, Ds.kt | — | — |  |
| themeIntensity | `theme_intensity` | Enum | `Intensity.NORMAL` | ThemeLookSettings.kt, GameLookUi.kt, LauncherScreen.kt | — | — | Theme intensity: Low also works as a performance mode (fewer particles, slower motion). */ |
| themeColorsOnly | `theme_colors_only` | Bool | `false` | GameDetailsOverlay.kt, LauncherScreen.kt, DetailLayers.kt | NO-SETTINGS-SCREEN | — | Use the theme's own colours only (ignore game / album / custom colours). */ |
| badgeName | `badge_name` | Bool | `true` | HubScreens.kt, BadgeWall.kt | NO-SETTINGS-SCREEN | model_name, pocket_name | ---- Achievements ---- Badge view: which text shows under each badge (independent). Hidden text = bigger badges. */ |
| badgePercent | `badge_percent` | Bool | `true` | HubScreens.kt, BadgeWall.kt | NO-SETTINGS-SCREEN | — |  |
| badgeCount | `badge_count` | Bool | `true` | HubScreens.kt, BadgeWall.kt | NO-SETTINGS-SCREEN | — |  |
| unlockAnimation | `unlock_animation` | Bool | `true` | HubScreens.kt, AchievementListDialog.kt | NO-SETTINGS-SCREEN | — | The automatic unlock animation for new unlocks (plays once per unlock). */ |
| animateOnlyNew | `animate_only_new` | Bool | `true` | HubScreens.kt, BadgeWall.kt | NO-SETTINGS-SCREEN | — | Only new unlocks animate (the entrance fly-in for the whole wall is skipped). */ |
| completionCelebration | `completion_celebration` | Bool | `true` | HubScreens.kt, AchievementListDialog.kt | NO-SETTINGS-SCREEN | — |  |
| achievementIntensity | `achievement_intensity` | Enum | `Intensity.NORMAL` | HubScreens.kt, AchievementFx.kt | NO-SETTINGS-SCREEN | — |  |
| gameJourney | `game_journey` | Bool | `true` | GameJourneyUi.kt, HeroCarousel.kt, GameDetailsOverlay.kt | NO-SETTINGS-SCREEN | — | ---- Post-session, journey, states ---- |
| contextMilestone | `context_milestone` | Bool | `true` | GameJourneyUi.kt | NO-SETTINGS-SCREEN | — | One contextual milestone line in the game's info ("Last session: 1h 42m"). */ |
| gameStates | `game_states` | Bool | `false` | GameLookUi.kt, LivingLibrary.kt, LauncherScreen.kt | NO-SETTINGS-SCREEN | — | Small state marks on covers: NEW / PLAYING / ON HOLD / COMPLETED / MASTERED / 100%. */ |
| smartContinue | `smart_continue` | Bool | `false` | LivingLibrary.kt, LauncherUiState.kt, SettingsDialog.kt | — | — | ---- Smart Continue / Living Library ---- |
| livingLibrary | `living_library` | Bool | `false` | LivingLibrary.kt | NO-SETTINGS-SCREEN | — | Off by default. Never hides or reorders anything by itself. */ |
| llActivityVisuals | `ll_activity` | Bool | `true` | LivingLibrary.kt | NO-SETTINGS-SCREEN | — |  |
| llHighlightRecent | `ll_recent` | Bool | `true` | LivingLibrary.kt | NO-SETTINGS-SCREEN | — |  |
| llHighlightUnfinished | `ll_unfinished` | Bool | `false` | LivingLibrary.kt | NO-SETTINGS-SCREEN | — |  |
| llHighlightNear | `ll_near` | Bool | `true` | LivingLibrary.kt | NO-SETTINGS-SCREEN | — |  |
| llSubdueInactive | `ll_subdue` | Bool | `false` | LivingLibrary.kt | NO-SETTINGS-SCREEN | — |  |
| heroBlur | `hero_blur` | Bool | `false` | HeroCarousel.kt | NO-SETTINGS-SCREEN | row_blur, name_blur, disc_blur | ---- Hero carousel ---- |
| heroDim | `hero_dim` | Bool | `true` | HeroCarousel.kt | NO-SETTINGS-SCREEN | — |  |
| idleFocusMode | `idle_focus` | Bool | `false` | HeroCarousel.kt | NO-SETTINGS-SCREEN | grid_focus | Hides most of the UI after a while without input; any input brings it back. */ |
| artPriority | `art_priority` | Enum | `ArtPriority.SMART` | SettingsDialog.kt, GameLookUi.kt, GameLooks.kt | — | — |  |
| transitionSpeed | `transition_speed` | Enum | `Speed.NORMAL` | SettingsDialog.kt, HeroCarousel.kt, AnimationPreviews.kt | — | — |  |
| animPageSlide | `anim_page_slide` | Bool | `true` | SettingsDialog.kt, Motion.kt, QuickSettings.kt | — | — | Update 109: the newer animations can each be turned off (Home › Animations, each with a preview). */ |
| animRiseIn | `anim_rise_in` | Bool | `true` | SettingsDialog.kt, Motion.kt | — | — |  |
| animHeldPictures | `anim_held_pictures` | Bool | `true` | SettingsDialog.kt, Motion.kt | — | — |  |
| infoAnim | `anim_info` | Enum | `InfoAnim.NONE` | SettingsDialog.kt, Motion.kt | — | cross_info | (Update 393: text / panels / rows appear in place by default — no rise, fade or slide-in. A value already saved is kept.) |
| popupAnim | `anim_popup` | Enum | `PopupAnim.LAYOUT` | SettingsDialog.kt, Motion.kt | — | — |  |
| motion | `motion_level` | Enum | `Motion.NORMAL` | SettingsDialog.kt, MediaInspectView.kt, MotionLevel.kt | — | — |  |
| pageAnim | `anim_page` | Enum | `PageAnim.NONE` | StyleCube.kt, SettingsDialog.kt, Motion.kt | — | live_page, fs_page |  |
| focusPop | `focus_pop` | Int | `100` | SettingsDialog.kt, Motion.kt | — | — |  |
| motionFocusedOnly | `motion_focused_only` | Bool | `false` | SettingsDialog.kt, Motion.kt | — | — |  |
| resumeSwap | `resume_swap` | Enum | `ResumeSwap.SLIDE` | StyleConsoleHomes.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | — |  |
| layoutAnims | `layout_anims` | String | `""` | Motion.kt, AnimationPreviews.kt | NO-SETTINGS-SCREEN | — | Per-layout choices over the ones above: "CROSS.info=FADE;CHAN.page=FADE;DUAL.pop=200;SHELF.motion=1". */ |
| capsuleSwap | `capsule_swap` | Enum | `CapsuleSwap.SLIDE` | SettingsDialog.kt, GameDock.kt | — | — |  |
| rowGlide | `row_glide` | Enum | `RowGlide.SMOOTH` | GameWindowBanner.kt, SettingsDialog.kt, StyleArcade.kt | — | — |  |
| artSourceCover | `art_source_cover` | String | `""` | LauncherViewModel.kt, SettingsDialog.kt, GameLookPicker.kt | — | — | Update 137, artwork source per look (Settings › Metadata › Artwork source per look): where each kind of picture is fetched from first — the covers (Covers look) |
| artSourceWide | `art_source_wide` | String | `""` | LauncherViewModel.kt, SettingsDialog.kt, GameLookPicker.kt | — | — |  |
| artSourceHero | `art_source_hero` | String | `""` | LauncherViewModel.kt, SettingsDialog.kt, GameLookPicker.kt | — | — |  |
| artSourceLogo | `art_source_logo` | String | `""` | LauncherViewModel.kt, SettingsDialog.kt | — | — |  |
| gameLooks | `game_looks` | String | `""` | GameLooks.kt, LauncherScreen.kt | NO-SETTINGS-SCREEN | theme_looks | ---- Per game ---- Every game's own look ([GameLooks]) as JSON {"stableKey": {...}}. */ |
| playStyle | `play_style` | Enum | `PlayStyle.BLOCK` | GameJourneyUi.kt, GameDetailsOverlay.kt | NO-SETTINGS-SCREEN | header_style, disc_style, case_style |  |
| headerStyle | `header_style` | Enum | `HeaderStyle.SHOWCASE` | GameJourneyUi.kt, GameDetailsOverlay.kt | NO-SETTINGS-SCREEN | play_style, disc_style, case_style |  |
| homeHeader | `home_header` | Bool | `true` | LauncherScreen.kt, PlayHeader.kt, GameJourneyUi.kt | NO-SETTINGS-SCREEN | — | The same showcase block for the game you're on, above the home row (classic row layout). */ |
| homeHeaderTight | `home_header_tight` | Bool | `true` | QuickSettings.kt, PlayHeader.kt | — | — | Home game header, tight layout: the category above the title, small gaps, score / save sync / game options small at the right. */ |
| squareTiles | `look_square_tiles` | Bool | `false` | GameLookPicker.kt, PresetsCard.kt, LauncherScreen.kt | NO-SETTINGS-SCREEN | — | Game look "Tile": the Voltcap row's covers as big squares (the Tile home's tiles), the one you're on a bit bigger. */ |
| tileHomeButtonsSet | `tile_home_buttons_set` | Bool | `false` | LauncherViewModel.kt | NO-SETTINGS-SCREEN | — | Update 317ah: Tile home's round buttons were put under its tiles the first time it was picked (once only). */ |
| carouselWide | `carousel_wide` | Bool | `false` | GameLookPicker.kt, Carousel.kt | NO-SETTINGS-SCREEN | — | Update 166: in the carousels, the game you're on unfolds into its wide capsule as you scroll (the look "Covers · wide capsule"; the Voltcap row has it from the  |
| spineLook | `look_spines` | Bool | `false` | GameLookPicker.kt, PresetsCard.kt, SettingsDialog.kt | — | — | Update 113: game look "Spines": every game as its spine (its own spine scan, else the console's spine with the name on it) — in the Voltcap row, the grid, the c |
| bigLogoSgdb | `big_logo_sgdb` | Bool | `true` | SettingsDialog.kt, GameWindow.kt, LauncherScreen.kt | — | — | Update 113: the big logos (Edit Home's logo part, Hero carousel, Spotlight's title, the second screen's showcase) from SteamGridDB only (on, the default), else  |
| metacriticChip | `metacritic_chip` | Bool | `false` | LauncherViewModel.kt, SettingsDialog.kt, SortDialog.kt | — | — | 0.6.17: the Metacritic score on the game you're on (Settings › Game look › Cards, and the Sort window). */ |
| consoleBadges | `console_badges` | Bool | `true` | GameLookPicker.kt, QuickSettings.kt, ConsoleBadge.kt | — | — | Update 115: a small console badge in the corner of every game's tile (the console's logo in white on its own colour), and whether that pill takes your theme's c |
| consoleBadgeTheme | `console_badge_theme` | Bool | `false` | GameLookPicker.kt, ConsoleBadge.kt | NO-SETTINGS-SCREEN | — |  |
| consoleBadgeLogos | `console_badge_logos` | Bool | `false` | — | NO-SETTINGS-SCREEN | — | Update 125: badges with the console's logo from Dan Patrick's white set (the R1 Console badges tile's second step). */ |
| consoleBadgeStyle | `console_badge_style` | Enum | `BadgeStyle.LOGO` | QuickSettings.kt, GameLookPicker.kt, ConsoleBadge.kt | — | — |  |
| consoleBadgeCorner | `console_badge_corner` | Enum | `BadgeCorner.AUTO` | GameLookPicker.kt, ConsoleBadge.kt | NO-SETTINGS-SCREEN | — |  |
| cartsOnly | `carts_only` | Bool | `false` | GameLookPicker.kt, PresetsCard.kt, CustomCarts.kt | — | — | Update 125: with the Cartridges & discs look, every game as a cartridge — its own cartridge for cartridge consoles (the real scan, or the drawn one), a plain ca |
| shortConsoleNames | `short_console_names` | Bool | `false` | SettingsDialog.kt, ConsoleNames.kt | — | — | Update 329: short console names on the screens (PS2, GBA…) instead of the official ones (ConsoleNames.label). */ |
| cartSource | `cart_source` | Enum | `com.shelf.launcher.ui.Ca` | GameLookPicker.kt, CustomCarts.kt, GameCard.kt | NO-SETTINGS-SCREEN | avatar_source | Update 128, the Cartridges look (cartsOnly): Original keeps each console's own cartridge and puts your cartridge pack on the rest (discs, UMDs, Steam, Android,  |
| cartLook | `cart_look` | Enum | `com.shelf.launcher.ui.Ca` | QuickSettings.kt, GameLookPicker.kt, CustomCarts.kt | — | second_look, picker_look, shelf_look |  |
| cartClear | `cart_clear` | Bool | `false` | GameLookPicker.kt, CustomCarts.kt, PhysicalMedia.kt | NO-SETTINGS-SCREEN | — |  |
| modelFinish | `model_finish` | Enum | `com.shelf.launcher.ui.Mo` | GameLookPicker.kt, PhysicalMedia.kt, PresetsCard.kt | NO-SETTINGS-SCREEN | — | Update 136: your 3D shape library (the "3D models" pack): which pieces the Cartridges & discs look and the 3D cases of the carousels draw (Off = as before) and  |
| modelFinishLast | `model_finish_last` | Enum | `com.shelf.launcher.ui.Mo` | GameLookPicker.kt | NO-SETTINGS-SCREEN | — | Update 141: the finish to come back to when the "3D models" look is picked again (it turns 3D models off when left). Update 202: Cartridges & discs' Cartridge c |
| modelAngle | `model_angle` | Enum | `com.shelf.launcher.ui.Mo` | PickedFacing.kt, Models3D.kt | NO-SETTINGS-SCREEN | cf_angle |  |
| modelName | `model_name` | Bool | `false` | Models3DLife.kt, Models3D.kt | NO-SETTINGS-SCREEN | badge_name, pocket_name | Update 136: the game's name on a strip along the bottom of the 3D model's label (Settings › Game look › 3D models). */ |
| modelTurn | `model_turn` | Bool | `false` | Models3DLife.kt | NO-SETTINGS-SCREEN | save_turn | Update 158: off by default (the one you're on stands still, facing you); switched off once for existing installs too ([migrate158]), and switching it back on st |
| pickedFaces | `picked_faces` | Bool | `true` | PickedFacing.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — | Update 158, "The one you're on" (Game look › 3D models, Carousel options, Case stacks): the 3D piece or case you're on faces you straight on (true) or stands tu |
| modelReflect | `model_reflect` | Bool | `false` | Models3DLife.kt | NO-SETTINGS-SCREEN | cf_reflect |  |
| artDropCase | `art_drop_case` | Int | `0` | HomeEditSession.kt, TileCheck.kt, HomeAdjust.kt | NO-SETTINGS-SCREEN | — | Update 136, Art drop (R1 › Art drop, Settings › Game look): how far the cover drops below the top of the window, 0–100, each look its own — the case template's  |
| artDropModel | `art_drop_model` | Int | `0` | HomeAdjust.kt | NO-SETTINGS-SCREEN | — |  |
| artShiftCase | `art_shift_case` | Int | `0` | HomeEditSession.kt, HomeAdjust.kt | NO-SETTINGS-SCREEN | — | Update 137, Art position (R1 › Art position: drag the picture, pinch or L1 / R1 to size it): with the drop above (now also upwards, to −50), how far the cover m |
| artShiftModel | `art_shift_model` | Int | `0` | HomeAdjust.kt | NO-SETTINGS-SCREEN | — |  |
| artZoomCase | `art_zoom_case` | Int | `100` | HomeEditSession.kt, TileCheck.kt, HomeAdjust.kt | NO-SETTINGS-SCREEN | — |  |
| artPosLooks | `art_pos_looks` | String | `""` | HomeEditSession.kt, HomeAdjust.kt | NO-SETTINGS-SCREEN | — | Update 202: Art position for the other looks (Edit Home › R3 / Art position): "COVER=x,y,zoom;CART=…;DISC=…", the same steps as the Box art's three above (left  |
| artZoomModel | `art_zoom_model` | Int | `100` | HomeAdjust.kt | NO-SETTINGS-SCREEN | — |  |
| discStyle | `disc_style` | String | `""` | LookStyles.kt, QuickSettings.kt | — | play_style, header_style, case_style | Update 129: one console's disc, case or spine for every game ("" = each game's own), picked on the R1 panel's Disc / Case / Spine tile, which shows with the Dis |
| caseStyle | `case_style` | String | `""` | LookStyles.kt, ShelfHome.kt, QuickSettings.kt | — | play_style, header_style, disc_style |  |
| spineStyle | `spine_style` | String | `""` | LookStyles.kt, ShelfHome.kt, QuickSettings.kt | — | play_style, header_style, disc_style |  |
| lookMemory | `look_memory` | String | `""` | GameLookPicker.kt, AppearanceStore.kt | NO-SETTINGS-SCREEN | — | Update 202: the game look is four main looks (Covers, Cases, Cartridges & discs, Art) with a few plain choices each (ui/GameLookPicker.kt). The choices still se |
| pixelUi | `pixel_ui` | Bool | `false` | DialogChrome.kt, Ds.kt, PixelStyle.kt | — | — | ---- 32-bit pixel style ---- Square / cut pixel corners on panels, chips and dialogs (the type is [uiFont] now). */ |
| windowsFollowLayout | `windows_follow_layout` | Bool | `false` | VoltSettings.kt, WindowLook.kt | — | — | Update 493 (Layout-aware windows, phase 1): Settings and Achievements take a window frame matching the home layout where one exists (Channel grid, Cross menu);  |
| uiFont | `ui_font` | Enum | `UiFont.NORMAL` | GamePresentationSettings.kt, Theme.kt | — | — |  |
| pixelArtwork | `pixel_artwork` | Bool | `false` | GamePresentationSettings.kt, PixelStyle.kt | — | — | Also pixelate artwork (covers, heroes, screenshots). Off = artwork stays sharp. */ |
| pixelSize | `pixel_size` | Enum | `PixelSize.CLASSIC` | GamePresentationSettings.kt, PixelStyle.kt | — | cube_size, mc_size, cf_size |  |
| dockBacking | `dock_backing` | Enum | `DockBack.COLOR` | DockBacking.kt | NO-SETTINGS-SCREEN | part_backing |  |
| dockColor | `dock_color` | String | `"#05070B"` | DockBacking.kt | NO-SETTINGS-SCREEN | live_color, od_color, nm_color |  |
| dockOpacity | `dock_opacity` | String | `"62"` | DockBacking.kt | NO-SETTINGS-SCREEN | dash_opacity |  |
| dockImage | `dock_image` | String | `""` | DockBacking.kt | NO-SETTINGS-SCREEN | — |  |
| gameAmbience | `game_ambience` | Bool | `true` | GameLookUi.kt, LivingLibrary.kt, LauncherScreen.kt | NO-SETTINGS-SCREEN | — | ---- Ambience ---- Per-game ambience layers (Game settings › Look for this game) are drawn only while this is on. */ |
| lyricsAlign | `lyrics_align` | Enum | `LyricsAlign.CENTER` | LyricScatter.kt, LauncherScreen.kt, ScreenSaver.kt | NO-SETTINGS-SCREEN | — |  |
| lyricsMotion | `lyrics_motion` | Enum | `LyricsMotion.STILL` | LyricScatter.kt, NowPlayingPanel.kt | NO-SETTINGS-SCREEN | disc_motion, cart_motion, case_motion |  |
| saveTurn | `save_turn` | Bool | `false` | SaveVaultStyles.kt, SaveExplorer.kt, Ps2Browser.kt | NO-SETTINGS-SCREEN | model_turn | The picked save turns slowly in 3D and idle ones tilt as they bob (off: they stay upright). */ |
| retroIcons | `retro_icons` | Enum | `RetroIcons.CURRENT` | RetroBounce.kt | NO-SETTINGS-SCREEN | bar_icons, hub_icons |  |
| lyricScatter | `lyric_scatter` | Enum | `LyricScatter.MIX` | LyricScatter.kt | NO-SETTINGS-SCREEN | — |  |
| lyricNextLine | `lyric_next_line` | Bool | `true` | LyricThemes.kt | NO-SETTINGS-SCREEN | — | Lyric themes (Karaoke, Neon lyrics): the next line shows, dimmer, under the one being sung. */ |
| karaokeBall | `karaoke_ball` | Bool | `true` | LyricThemes.kt | NO-SETTINGS-SCREEN | — | Karaoke: a ball hops from word to word over the line being sung. */ |
| trophyMasteredOnly | `trophy_mastered_only` | Bool | `false` | SettingsDialog.kt, LauncherScreen.kt | — | — | Update 119: the trophy themes (Trophy vault, Badge bounce) show only your mastered games. */ |
| lyricsOffsetMs | `lyrics_offset_ms` | Int | `0` | LyricThemes.kt, LyricScatter.kt | NO-SETTINGS-SCREEN | — | Update 119: the lyric themes' words a little earlier (−) or later (+) than the player says, in ms. */ |
| lyricCoverBg | `lyric_cover_bg` | Bool | `true` | SettingsDialog.kt, LyricThemes.kt, ThemeScene.kt | — | — | Update 119: the lyric themes over the song's own cover (blurred and dimmed) instead of their scene. */ |
| layoutColors | `layout_colors` | String | `""` | — | NO-SETTINGS-SCREEN | fs_colors |  |
| themeLooks | `theme_looks` | String | `""` | — | NO-SETTINGS-SCREEN | game_looks | Every theme's own look, as JSON {"THEME_NAME": {...}}. Themes not in it use [themeLookDefault]. */ |
| themeLookDefault | `theme_look_default` | String | `""` | — | NO-SETTINGS-SCREEN | — | The look themes start with: what the old global colour settings were (migrated once), else plain. */ |
| favoriteThemes | `favorite_themes` | Set | `` | SettingsDialog.kt, LauncherScreen.kt, ThemeLookSettings.kt | — | — | AppTheme names marked as favourites (the ★ in the theme browser). */ |
| themeRotation | `theme_rotation` | Enum | `ThemeRotation.OFF` | ThemeRotator.kt, ThemeLookSettings.kt | — | — |  |
| themeRotationMinutes | `theme_rotation_minutes` | Int | `60` | ThemeLookSettings.kt, ThemeRotator.kt | — | — |  |
| themeRotationLaunches | `theme_rotation_launches` | Int | `3` | ThemeLookSettings.kt, ThemeRotator.kt | — | — |  |
| themeRotationKinds | `theme_rotation_kinds` | Set | `ThemeKind.values(` | ThemeLookSettings.kt, ThemeRotator.kt | — | — | Which kinds of favourite themes rotation may pick (names of [ThemeKind]); empty = all. */ |
| customScenePack | `custom_scene_pack` | String | `""` | CustomPacks.kt | NO-SETTINGS-SCREEN | — | ---- Custom themes & visualizers (ui/CustomPacks.kt; the files themselves stay on this device) ---- Id of the custom theme pack that "Your theme" shows ("" = no |
| customVizPack | `custom_viz_pack` | String | `""` | CustomPacks.kt | NO-SETTINGS-SCREEN | — | Id of the custom visualizer pack that "Your visualizer" shows ("" = none chosen). */ |
| customPackOff | `custom_pack_off` | Set | `` | CustomPacks.kt | NO-SETTINGS-SCREEN | — | Packs Voltcap switched off because the home screen ran slowly with them (open the pack to try again). */ |
| safeZoneExtra | `safe_zone_extra` | Int | `0` | SafeZones.kt | NO-SETTINGS-SCREEN | — | ---- Safe zones ---- Extra margin on top of the automatic safe zone, 0..8 % of the short screen side (TVs with overscan, odd bezels). */ |
| consoleNamePin | `console_name_pin` | Enum | `ConsoleNamePin.FLOW` | SettingsDialog.kt, LauncherScreen.kt | — | — | Only console / platform categories use this; other categories always keep the normal behaviour. */ |
| widgetPositions | `widget_positions` | String | `""` | HomeEdit.kt | NO-SETTINGS-SCREEN | — | ---- Edit Home: moved widgets ---- Home widgets the person moved in Edit Home: "id=x,y;id2=x,y", each the widget's top-left as a fraction (0..1) of the free roo |
| mixConsoles | `mix_consoles` | String | `""` | MixConsoles.kt | NO-SETTINGS-SCREEN | — | Pass 4: Game look › Cartridges & discs › Mix › Customize consoles — each console's medium (ui/MixConsoles.kt), JSON. */ |
| catLayouts | `cat_layouts` | String | `""` | CategoryLayouts.kt, SettingsDialog.kt | — | — | Pass 4: each category's own home layout (data/CategoryLayouts.kt), as JSON {category key: layout name}. */ |
| catLayoutDefault | `cat_layout_default` | String | `""` | CategoryLayouts.kt, SettingsDialog.kt | — | — | Pass 4: the home layout of every category without its own (set the first time a category gets one). */ |
| catGameLooks | `cat_game_looks` | String | `""` | CategoryGameLooks.kt, SettingsDialog.kt | — | — | Pass 5: each category's own game look (data/CategoryGameLooks.kt), as JSON {category key: CardArtType name}. */ |
| widgetScales | `widget_scales` | String | `""` | PlayHeader.kt, HomeEdit.kt | NO-SETTINGS-SCREEN | — | Home widgets' sizes set in Edit Home ("id=1.2;…", 1 = normal; the corner handle or L2 / R2). */ |
| widgetWidths | `widget_widths` | String | `""` | HomeEdit.kt | NO-SETTINGS-SCREEN | — | Widths (dp) that moved home parts keep (update 90: \"id=320.0;…\"). */ |
| widgetStretch | `widget_stretch` | String | `""` | HomeEdit.kt | NO-SETTINGS-SCREEN | — | Update 133: how far each part is stretched in Edit Home, wider and taller on its own (its side and bottom handles): "id=x,y;…" (1 = as it is). What's inside str |
| capsuleReveal | `capsule_reveal_v2` | Enum | `CapsuleReveal.ROLL` | GameLookPicker.kt, GameCard.kt | NO-SETTINGS-SCREEN | — |  |
| capsuleRevealFirst | `capsule_reveal_first` | Enum | `CapsuleReveal.NONE` | GameLookPicker.kt, GameCard.kt | NO-SETTINGS-SCREEN | — | Update 129: the same for "Wide for the first game only" (its fixed capsule); off by default, the wide art just shows. */ |
| achPlace | `ach_place` | Enum | `AchPlace.CORNER` | SettingsDialog.kt, GameCard.kt, StyleHomes.kt | — | — |  |
| discTileChips | `disc_tile_chips` | Bool | `true` | GameLookPicker.kt, GameCard.kt | NO-SETTINGS-SCREEN | — | (Update 240, #32/#57) On a disc in the game row (Discs, Continue playing…): the trophy count and the game's state on a chip strip below the disc, at the tile's  |
| discMotion | `disc_motion` | Enum | `MediaMotion.SPIN` | SettingsDialog.kt, PhysicalMedia.kt | — | lyrics_motion, cart_motion, case_motion |  |
| cartMotion | `cart_motion` | Enum | `MediaMotion.BOUNCE` | SettingsDialog.kt, PhysicalMedia.kt, SolidPiece.kt | — | lyrics_motion, disc_motion, case_motion |  |
| caseMotion | `case_motion` | Enum | `MediaMotion.OFF` | SettingsDialog.kt, PhysicalMedia.kt | — | lyrics_motion, disc_motion, cart_motion | Box art cases (update 88): spin = turning slowly left and right, bounce = a little hop. Still by default. */ |
| spotifyQuietWake | `spotify_quiet_wake` | Bool | `true` | SpotifySettingsCard.kt, SpotifyScreen.kt | — | — | Spotify (update 90): with nothing playing anywhere, a picked song wakes Spotify on this handheld in the background and plays there, without opening the app. */ |
| overlayParts | `overlay_parts` | String | `""` | HomeEditSession.kt, LauncherScreen.kt | NO-SETTINGS-SCREEN | — | Extra game info on layouts that don't have it built in (update 93: Hero carousel, Spotlight, Icon bar), per layout: "HERO=h_title,h_stats,info;SPOT=news". Each  |
| hiddenParts | `hidden_parts` | Set | `` | HomeEdit.kt, HomeEditSession.kt, R1TileRules.kt | NO-SETTINGS-SCREEN | — | Parts taken off the home screen with their ✕ in Edit Home (update 104). */ |
| partShifts | `part_shifts` | String | `""` | HomeEdit.kt | NO-SETTINGS-SCREEN | — | How far the layouts' own parts were moved in Edit Home (update 108): "part:LAYOUT:name=dx,dy;…" in dp. */ |
| partBacking | `part_backing` | Enum | `PartBacking.LAYOUT` | SettingsDialog.kt, HomeParts.kt | — | dock_backing |  |
| partBackingSolid | `part_backing_solid` | Int | `70` | HomeEditSession.kt, SettingsDialog.kt, HomeParts.kt | — | — | Update 133: how solid the backing behind the floating parts is, 0–100 (0 = as see-through as the backings were, 100 = nearly solid). Settings › Home › Layout op |
| categoryTitle | `category_title_212` | Enum | `CategoryTitleMode.SUBTLE` | SettingsDialog.kt, CategoryTitle.kt, LauncherScreen.kt | — | — |  |
| androidMedia | `android_media_212` | Enum | `AndroidMediaMode.FOLLOW` | AndroidMedia.kt | NO-SETTINGS-SCREEN | — |  |
| androidMediaGames | `android_media_games_212` | String | `""` | AndroidMedia.kt | NO-SETTINGS-SCREEN | — | Update 212: one Android game's own choice: "gameKey=DISC;gameKey=CARTRIDGE" (none: the setting above). */ |
| rowGames | `row_games` | String | `""` | — | NO-SETTINGS-SCREEN | — | Games across the screen per home layout (update 90): "CLASSIC=6;TILES=5;GRID=7". Missing / 0 = automatic (from Card size). Only the Voltcap row, Tile home and G |
| heroShade | `hero_shade` | Enum | `HeroShade.CONSOLE` | SettingsDialog.kt, HeroShade.kt | — | spot_shade |  |
| heroShadeStrength | `hero_shade_strength` | Int | `60` | SettingsDialog.kt, HeroShade.kt | — | — |  |
| homeHeaderHidden | `home_header_hidden` | Set | `emptySet(` | PlayHeader.kt, LauncherScreen.kt | NO-SETTINGS-SCREEN | — | The home game header's parts switched off (title, continue, the stat boxes, Play, the round buttons, source). */ |
| heroCardStyle | `hero_card_style` | Enum | `HeroCardStyle.ROUNDED` | HeroCarousel.kt | NO-SETTINGS-SCREEN | — |  |
| heroCarouselMotion | `hero_carousel_motion` | Enum | `HeroCarouselMotion.GLIDE` | HeroCarousel.kt | NO-SETTINGS-SCREEN | — |  |
| heroCoversRight | `hero_covers_right` | Bool | `false` | HeroCarousel.kt, QuickSettings.kt, LauncherScreen.kt | — | — | Hero carousel: the column of covers on the right (the info then goes left). */ |
| heroSideCovers | `hero_side_covers` | Enum | `HeroSideCovers.ALL` | HeroCarousel.kt, QuickSettings.kt, LayoutPresets.kt | — | — |  |
| dockOtherGames | `dock_other_games` | Enum | `DockOtherGames.ALL` | SettingsDialog.kt, GameDock.kt, LauncherScreen.kt | — | — |  |
| rowFocus | `style_row_focus` | Enum | `RowFocus.DESIGN` | StyleHomeSettings.kt, StyleSideMenu.kt, StyleDashboard.kt | LAYOUT-ONLY | — |  |
| spineDepth | `spine_depth` | Enum | `SpineDepth.SUBTLE` | SettingsDialog.kt, SpineDepth.kt, StyleCoverDetails.kt | — | stacks_depth, mc_depth |  |
| saveVaultStyle | `save_vault_style` | Enum | `SaveVaultStyle.CLASSIC` | SaveVaultStyles.kt, SaveExplorer.kt, SaveInspect.kt | NO-SETTINGS-SCREEN | — |  |
| saveVaultCard | `save_vault_card` | Enum | `com.shelf.launcher.ui.Mo` | SaveVaultStyles.kt, Models3D.kt, SaveVaultLooks.kt | NO-SETTINGS-SCREEN | — | Update 136: the 3D card's model (SD card or a memory card colour) and whether it's the clear one. */ |
| saveVaultClear | `save_vault_clear` | Bool | `false` | SaveVaultStyles.kt, SaveVaultLooks.kt, Models3D.kt | NO-SETTINGS-SCREEN | — |  |
| saveVaultBackdrop | `save_vault_backdrop` | Enum | `SaveVaultBackdrop.THEME` | SaveVaultStyles.kt | NO-SETTINGS-SCREEN | — |  |
| saveVaultFilter | `save_vault_filter` | Enum | `SaveVaultFilter.ALL` | SaveVaultStyles.kt, SaveExplorer.kt | NO-SETTINGS-SCREEN | — |  |
| raPinned | `ra_pinned` | String | `""` | RaPin.kt, PerfOverlayService.kt | NO-SETTINGS-SCREEN | — | ---- RetroAchievements overlay (in-game) ---- The achievement you're working on, pinned to the in-game overlay: "gameId:achievementId" (empty = none). */ |
| raShowPinned | `ra_show_pinned` | Bool | `true` | PerfSettingsCard.kt, RaOverlayView.kt, PerfOverlayService.kt | — | — | The pinned achievement shows as a compact card in the in-game overlay (the other contents are in PerfConfig). */ |
| backdropAchievements | `backdrop_achievements` | Set | `` | HubScreens.kt, LauncherScreen.kt, BackdropAchievements.kt | NO-SETTINGS-SCREEN | — | Achievement touches drawn into the home backdrop (names of [BackdropAchievement]); empty = none. */ |
| voltHelp | `volt_help` | Bool | `true` | VoltHelp.kt | NO-SETTINGS-SCREEN | — | ---- Volt help ---- Small "?" Volt help on complex pages (Save Vault, theme colours, RetroAchievements, TDP). */ |
| lightsHeldOff | `lights_held_off` | Bool | `false` | LightsPopup.kt, LauncherScreen.kt, QuickSettings.kt | — | — | ---- Controller lights ---- Voltcap holds the lights dark (Lights › Off). Different from "System" (lightsEnabled off), which leaves them to the handheld's own s |
| artColorSource | `art_color_source` | Enum | `ArtColorSource.AUTO` | SettingsDialog.kt, QuickSettings.kt, ThemeLookSettings.kt | — | — |  |
| artKeepAccent | `art_keep_accent` | Bool | `false` | SettingsDialog.kt, Theme.kt | — | — | (Update 240, #23) While a theme's colours follow the game / the music: keep the accent you picked yourself (Colours & look › Custom › Accent; the theme's own ac |
| musicShowControls | `music_show_controls` | Bool | `true` | NowPlayingThemeCard.kt, NowPlayingPanel.kt, LauncherScreen.kt | NO-SETTINGS-SCREEN | — | ---- Music themes: what sits under the visualizer ---- |
| musicShowTitle | `music_show_title` | Bool | `true` | NowPlayingThemeCard.kt, NowPlayingPanel.kt | NO-SETTINGS-SCREEN | — |  |
| musicShowLyrics | `music_show_lyrics` | Bool | `true` | NowPlayingThemeCard.kt, LauncherScreen.kt | NO-SETTINGS-SCREEN | — |  |
| cornerViz | `corner_viz` | Enum | `CornerViz.OFF` | CornerVisualizer.kt, LauncherScreen.kt | NO-SETTINGS-SCREEN | — |  |
| cornerVizCorner | `corner_viz_corner` | Enum | `VizCorner.TOP_RIGHT` | CornerVisualizer.kt | NO-SETTINGS-SCREEN | — |  |
| cornerVizSize | `corner_viz_size` | Int | `120` | CornerVisualizer.kt | NO-SETTINGS-SCREEN | — | Size in dp of each corner visualizer. */ |
| cornerVizOnlyMusic | `corner_viz_only_music_v2` | Bool | `false` | CornerVisualizer.kt | NO-SETTINGS-SCREEN | — | Only while music plays (else always, idling slowly). */ |
| eraArt | `era_art` | Enum | `EraArt.AUTO` | ThemeLookSettings.kt, NowPlayingScene.kt, LauncherScreen.kt | — | mixed_art, live_art, side_art |  |
| shortcutScale | `shortcut_scale` | Int | `100` | SettingsDialog.kt, TileHome.kt | — | — | ---- Round shortcuts (the tile layout's shortcut bar) ---- Size of the round shortcuts, % of normal. */ |
| shortcutHidden | `shortcut_hidden` | Set | `` | SettingsDialog.kt, LauncherScreen.kt | — | — | Shortcut ids hidden from the round bar (still in L1). */ |
| shortcutThemedIcons | `shortcut_themed_icons` | Bool | `true` | SettingsDialog.kt, AppIcons.kt | — | — | App icons in the round shortcuts drawn in the theme's colours (monochrome, tinted). */ |
| secondDockMono | `second_dock_mono` | Bool | `true` | ScreenLayoutsUi.kt, AppIcons.kt, SecondScreenHome.kt | NO-SETTINGS-SCREEN | — | Update 319 (Toni: "an icon pack in one colour so it follows the theme's colours", no square plate with a blur behind): the second screen's dock shows each app a |
| endlessFill | `endless_fill` | Bool | `true` | QuickSettings.kt, StyleNightMenu.kt, EndlessRow.kt | — | — | Update 324 (Toni: "with Endless on the games never reach the edges when there aren't many"): Endless fills the row past both edges with the games again (on), or |
| monoIconStyle | `mono_icon_style` | Enum | `MonoIconStyle.DETAIL` | ScreenLayoutsUi.kt, IconStyles.kt | NO-SETTINGS-SCREEN | — |  |
| syncLastPlayed | `sync_last_played` | Bool | `true` | CloudSyncContent.kt, LastPlayedSync.kt | NO-SETTINGS-SCREEN | — | ---- Last played on every device ---- Share "last played" through Google Drive so a game played on another device rises to the top here too. */ |
| overlayPositions | `overlay_positions` | String | `""` | PerfOverlayService.kt | NO-SETTINGS-SCREEN | — | ---- Overlays over games: free positions ---- Overlays dragged to a free spot (not a corner): "stats=x,y;music=x,y;ra=x,y", each the window's top-left as a frac |
| overlayFolded | `overlay_folded` | Set | `` | PerfOverlayService.kt | NO-SETTINGS-SCREEN | — | Overlays folded away to a small arrow tab (update 121): "stats", "music", "ra". */ |
| overlaySizes | `overlay_sizes` | String | `""` | PerfOverlayService.kt | NO-SETTINGS-SCREEN | — | Each overlay's own size from a corner drag, times Overlay size: "stats=1.2;music=0.8". Missing = 1. */ |
| overlayArrows | `overlay_arrows` | Bool | `true` | PerfOverlayService.kt, QuickSettings.kt, OverlayControlActivity.kt | — | — | The small fold arrow under each overlay (the folded tab always shows). */ |
| sceneHalfRes | `scene_half_res` | Bool | `true` | SettingsDialog.kt, ThemeScene.kt | — | — | ---- Lighter backgrounds (update 121) ---- The shader scenes drawn at most at half the screen's resolution. */ |
| sceneFps | `scene_fps` | Enum | `SceneFps.AUTO` | SettingsDialog.kt, ThemeScene.kt | — | stage_fps |  |
| secondSlowWhenUnused | `second_slow_unused` | Bool | `true` | SettingsDialog.kt, SecondScreen.kt | — | — |  |
| secondLook | `second_look` | Enum | `SecondLook.SHOWCASE` | SettingsDialog.kt, SecondScreenHome.kt | — | cart_look, picker_look, shelf_look |  |
| secondDimAfter | `second_dim_after` | Int | `60` | BatterySaverUi.kt, SecondDim.kt | NO-SETTINGS-SCREEN | — | Update 188: the second screen dims after this many seconds unused (0 = never; ui/SecondDim.kt). */ |
| secondDimLevel | `second_dim_level2` | Int | `45` | BatterySaverUi.kt, SecondDim.kt | NO-SETTINGS-SCREEN | — | Update 188: how dark it goes, % black (100 = black). */ (Update 189: a new key — part-way dark by default, the screen stays usable; 30 / 45 / 60.) */ |
| secondDimBacklight | `second_dim_backlight` | Bool | `true` | BatterySaverUi.kt, SecondDim.kt | NO-SETTINGS-SCREEN | — | Update 188: its backlight turned down too while dimmed. */ |
| secondBrightness | `second_brightness` | Int | `0` | BatterySaverUi.kt, SecondDim.kt | NO-SETTINGS-SCREEN | — | D3 (update 485): the second screen's own brightness, % (0 = the same as the main screen). Android lets an app set it only for its own window, so it holds while  |
| saveVaultEndless | `save_vault_endless` | Bool | `true` | SaveVaultStyles.kt, SaveExplorer.kt | NO-SETTINGS-SCREEN | — | ---- Save Vault ---- The save grid wraps round: scrolling past the end starts again at the beginning. */ |
| uiStyle | `ui_style` | Enum | `UiStyle.VOLT` | VoltUi.kt, VoltSettings.kt, DialogChrome.kt | — | play_style, header_style, disc_style |  |
| voltLine | `volt_line` | Bool | `true` | VoltSettings.kt, VoltUi.kt | — | — | The spark that runs along the voltage lines (off: still lines). */ |
| uiGlow | `ui_glow` | Bool | `true` | VoltSettings.kt, VoltUi.kt | — | ribbon_glow, arcade_glow, gc_glow | The soft glow inside the focus outline. */ |
| coverLight | `cover_light` | Bool | `true` | VoltSettings.kt, FocusedCard.kt, LauncherScreen.kt | — | live_light, chan_light | A soft light in the colours of the game you're on, behind its card. */ |
| voltBattery | `volt_battery` | Bool | `true` | VoltSettings.kt, HomeChrome.kt | — | — | Status bar battery as a little charge gauge in the theme's colours (Volt looks). */ |
| rowBlur | `row_blur` | Int | `35` | VoltSettings.kt, DockBacking.kt, LauncherScreen.kt | — | hero_blur, name_blur, disc_blur | A light blur (and soft shade) behind just the games, their names and the categories, 0 = off. */ |
| nameBlur | `name_blur` | Int | `100` | DockBacking.kt, GameDock.kt | NO-SETTINGS-SCREEN | hero_blur, row_blur, disc_blur | How soft the shadow / glow round the game's name and the category is, % of the style's own (0 = crisp). */ |
| nameContrast | `name_contrast` | Enum | `NameContrast.PILL` | DockBacking.kt, GameDock.kt, StyleIconBar.kt | NO-SETTINGS-SCREEN | — |  |
| tvRoomGames | `tv_room_games` | Enum | `com.shelf.launcher.ui.Tv` | SettingsDialog.kt, QuickSettings.kt, R1TileRules.kt | — | — | TV room layout: how the games beside the TV look. */ |
| carouselLayouts | `carousel_layouts` | Set | `` | QuickSettings.kt, R1TileRules.kt, PresetsCard.kt | — | — | Home layouts with the "Carousel look" (HomeLayout names: row, tiles, grid, Hero carousel). */ |
| carouselView | `carousel_view` | Enum | `com.shelf.launcher.ui.Ca` | SettingsDialog.kt, QuickSettings.kt, PresetsCard.kt | — | — | How every carousel lays its games out (update 104). */ |
| carouselGap | `carousel_gap` | Int | `100` | SettingsDialog.kt, QuickSettings.kt, Carousel.kt | — | — | Space between the carousel's games, 60–160 % (update 104). */ |
| carouselSpread | `carousel_spread` | Bool | `true` | QuickSettings.kt, SettingsDialog.kt, Carousel.kt | — | — | Update 185: a short list spread out wider (Carousel.spread). Off: the Spacing alone decides the gap. */ |
| carouselReflect | `carousel_reflect` | Bool | `false` | SettingsDialog.kt, Carousel.kt, QuickSettings.kt | — | — | A faint reflection under each game of the carousel (update 104). */ |
| carouselStage | `carousel_stage` | Bool | `false` | SettingsDialog.kt, QuickSettings.kt, Carousel.kt | — | — | Update 147: the carousel on a dark floor fading into black (on with the 3D case views). */ |
| carouselStageGameColor | `carousel_stage_game_color` | Bool | `true` | SettingsDialog.kt, Carousel.kt | — | — | Update 187: the dark stage tinted by the colours of the game you're on (off: your colour). */ |
| boxFlowTilt | `box_flow_tilt` | Bool | `true` | SettingsDialog.kt, Carousel.kt | — | — | Update 187: Box flow — the box you're on sways a little as the carousel moves. */ |
| animatedFocusOnly | `animated_focus_only2` | Bool | `true` | SettingsDialog.kt, GameCard.kt, LauncherScreen.kt | — | — | Update 148: animated covers move only on the game you're on (the others show their first frame). Off: every game. Update 181: on by default (a new key, so it st |
| clipsOffline | `clips_offline` | Bool | `false` | SettingsDialog.kt, LauncherViewModel.kt | — | — | Update 148: the preview clips saved on the device (ClipStore), up to [clipsMaxMb]. */ |
| clipsMaxMb | `clips_max_mb` | Int | `1024` | SettingsDialog.kt, ClipStore.kt | — | — |  |
| autoBackup | `auto_backup` | Enum | `AutoBackupWorker.Every.W` | SettingsDialog.kt, TestHooks.kt, AutoBackupWorker.kt | — | — | Update 149: the automatic Drive backup (AutoBackupWorker): how often, with accounts or not, when the last one ran. */ |
| autoBackupAccounts | `auto_backup_accounts` | Bool | `true` | SettingsDialog.kt, AutoBackupWorker.kt | — | — |  |
| autoBackupAt | `auto_backup_at` | String | `""` | AutoBackupWorker.kt, SettingsDialog.kt | — | — |  |
| carousel3d | `carousel_3d` | Bool | `false` | SettingsDialog.kt, Carousel.kt, CategoryWarmup.kt | — | — | Update 134: every carousel game drawn as a real 3D case (cover, spine, edge, lid), whatever the view (always in Arc, Spine wall and Turned row). */ |
| tvCarouselSide | `tv_carousel_side` | Int | `2` | SettingsDialog.kt, QuickSettings.kt, Carousel.kt | — | — | Carousel (TV room and every "Carousel look"): how many games show each side of the one in the middle (1–3; update 113: up to 5, also on the R1 panel's Carousel  |
| carouselEdge | `carousel_edge` | Bool | `true` | SettingsDialog.kt, QuickSettings.kt, Carousel.kt | — | — | Update 119: the carousel shows games all the way to the screen's edges (instead of [tvCarouselSide] each side). */ (Update 129: on by default — the games reach  |
| carouselSize | `carousel_size` | Int | `130` | SettingsDialog.kt, QuickSettings.kt, GameDock.kt | — | — | Update 129: how big every carousel's games are, % (the Voltcap row's carousel 100 % = the row's own size). */ |
| tvCarouselTilt | `tv_carousel_tilt` | Bool | `true` | SettingsDialog.kt, Carousel.kt | — | — | Carousel: the games aside turned away from the middle one. */ |
| tvInfoUnder | `tv_info_under` | Bool | `true` | SettingsDialog.kt, TvRoomHome.kt | — | — | TV room (update 107): the game's info on the floor under the set instead of under the games. */ |
| tvRecentBadges | `tv_recent_badges` | Bool | `true` | SettingsDialog.kt, TvRoomHome.kt, LauncherScreen.kt | — | — | TV room (update 107): recently unlocked achievement badges on the wall left of the set. */ |
| tvCarouselTiltDir | `tv_carousel_tilt_dir` | Enum | `com.shelf.launcher.ui.Ti` | SettingsDialog.kt, Carousel.kt | — | — | Carousel tilt: turned away from the middle one (outward, like pages) or towards it (inward, like a wall). */ |
| tvCarouselTiltSides | `tv_carousel_tilt_sides` | Enum | `com.shelf.launcher.ui.Ti` | SettingsDialog.kt, Carousel.kt | — | — | Carousel tilt: both sides, or only the games on the left / on the right of the middle one. */ |
| mixedArt | `mixed_art` | Enum | `com.shelf.launcher.ui.Mi` | PhysicalMedia.kt, SettingsDialog.kt, Carousel.kt | — | era_art, live_art, side_art | Lists mixing consoles (Recently played, favourites…): what every game is drawn as - basic by default, so no card is bigger or smaller. */ |
| categorySlide | `category_slide` | Bool | `true` | SettingsDialog.kt, HomeExtras.kt, Carousel.kt | — | — | The games slide in from the side when the category changes. */ |
| box3d | `box_3d` | Bool | `false` | SettingsDialog.kt, AppearanceStore.kt, LaunchThrow.kt | — | stacks_3d | Box art as the older 3D glass box from the template pack (off: the official flat case with its cover frame and spine). */ |
| boxSpine | `box_spine_side` | Bool | `false` | GameLookPicker.kt, SettingsDialog.kt, AppearanceStore.kt | — | — | Box cards show the pack's 3D box turned a little to the side, so its real spine shows. Off (default): the front alone, facing you — a spine is never drawn besid |
| lookFromLayout | `look_from_layout` | Bool | `false` | GameLookPicker.kt, LauncherScreen.kt | NO-SETTINGS-SCREEN | — | Update 280 (Toni): Game look › Layout standard — each home layout draws its games in its own standard look (the look of its first preset, e.g. the Voltcap row's |
| coverTemplates | `cover_templates` | Bool | `true` | Case3D.kt, ShelfHome.kt, SettingsDialog.kt | — | — | Covers go into each system's own cover template (printed border, real proportions) and its spine; off: plain covers. */ |
| discCase | `disc_case` | Bool | `false` | GameLookPicker.kt, PresetsCard.kt, AppearanceStore.kt | — | — | Disc games: the case with the disc half out of it, spinning. */ |
| discCase3d | `disc_case_3d` | Bool | `false` | SettingsDialog.kt, PresetsCard.kt, DiscCase3D.kt | — | — | Update 151: "3D case + disc" (Settings › Game look › Cases with a disc): with Disc in its case, the case is the 3D box (every console alike) with the disc half  |
| caseWithMedia | `case_with_media` | Bool | `false` | GameLookPicker.kt, PresetsCard.kt, LaunchThrow.kt | NO-SETTINGS-SCREEN | — | Box art (update 93): the case with its cartridge / disc leaning against it; only the cartridge / disc moves. */ |
| originalCases | `original_cases` | Bool | `true` | RealMedia.kt, GameLookPicker.kt, MediaInspectView.kt | — | — | Box art and the Game shelf (update 101): the game's own scanned case — its front scan (or the front cut from the full box scan) as the case, and its real spine  |
| spinesOwn | `spines_own` | Bool | `false` | Case3D.kt, SettingsDialog.kt, StyleCardboardBox.kt | — | — | Update 147: every game's spine is Voltcap's own spine of its console with the game's name (your spine pack), so they all match — the real spine scans (each in i |
| ownCaseFit | `own_case_fit` | Bool | `true` | Case3D.kt, SettingsDialog.kt, CoverCases.kt | — | all_case_fit | Update 169: Voltcap's own Steam / Android cases take the shape of the game's cover (SteamGridDB's tall 2:3 grid, a square icon-like cover…): the band stays as i |
| allCaseFit | `all_case_fit` | Bool | `false` | SettingsDialog.kt, CoverCases.kt | — | own_case_fit | Update 169: the consoles' cover templates take the cover's shape too (off: they keep the real case's shape). */ |
| printedBacks | `printed_backs` | Bool | `true` | MediaInspectView.kt, SettingsDialog.kt, Case3D.kt | — | — | Update 170: the 3D cases' backs printed like real ones when the game has no back scan: the console's back cover from the template pack (barcode, seals, rating b |
| backRating | `back_rating` | Enum | `BackRating.USK` | SettingsDialog.kt, MediaInspectView.kt | — | — |  |
| backRatingOthers | `back_rating_others` | Bool | `true` | SettingsDialog.kt, MediaInspectView.kt | — | — |  |
| boxSides | `box_sides` | Enum | `BoxSides.COLOUR` | SettingsDialog.kt, BoxSides.kt, LauncherScreen.kt | — | — | Update 466 (owner: "make Plain colour from the box art the new default … preserve any explicitly saved preference"). */ |
| boxSidesChosen | `box_sides_chosen` | Bool | `false` | SettingsDialog.kt | SETTINGS-ONLY | — | Update 466: true once a box-sides style was picked in Settings (kept for later decisions; since update 467 no saved value is ever changed because of it — [migra |
| manualKeep | `manual_keep` | Enum | `ManualKeep.PREVIEW` | LauncherViewModel.kt, SettingsDialog.kt | — | — |  |
| boxSideColors | `box_side_colors` | Set | `setOf("RIGHT"` | SettingsDialog.kt, BoxSides.kt | — | — | Update 319 (Toni): with the printed sides off (Authentic only / Plain cardboard), the sides with no picture of their own named here ("RIGHT", "TOP", "BOTTOM" —  |
| caseRegion | `case_region` | Enum | `CaseRegionChoice.AUTO_US` | CaseCatalog.kt, SettingsDialog.kt, Case3D.kt | — | — |  |
| cartShellChoices | `cart_shell_choices` | String | `""` | CartShells.kt | NO-SETTINGS-SCREEN | — | Update 195: the cartridge shell picked per game / per console ("key\tchoice" lines; ui/CartShells.kt). */ |
| dsCaseChoices | `ds_case_choices` | String | `""` | DsCases.kt | NO-SETTINGS-SCREEN | — | Update 287: the DS case picked for a game (DsCases): "key\tVARIANT" lines. */ |
| saveObjectLooks | `save_object_looks` | String | `""` | SaveVaultLooks.kt | NO-SETTINGS-SCREEN | — | (Update 236, #42/#43/#47: each Save Vault object's colour, per save or per console — "key\tvalue" lines, keys "g:<stableKey>" / "console:<platform>", values "p= |
| saveEngraving | `save_engraving` | Bool | `true` | SaveVaultStyles.kt, Engraving.kt, SaveInspect.kt | NO-SETTINGS-SCREEN | — | (Update 241, #44/#46: "SaveVault" and Voltcap's cloud moulded into every Save Vault object's plastic — the front's blank area where it has one, always its back; |
| saveInspectAutoRotate | `save_inspect_auto_rotate` | Bool | `false` | SaveInspect.kt | NO-SETTINGS-SCREEN | — | (Update 241, #49: Save Vault › a save › Inspect turns the object by itself — off by default; ui/SaveInspect.kt.) */ |
| realEditions | `real_editions` | Bool | `true` | SettingsDialog.kt, MediaEditions.kt, CartShellPicker.kt | — | — | Update 210: games whose real cartridge came in its own plastic or shell (the gold Zelda, Pokémon Crystal's clear cyan, the DS infrared cards…) are drawn on it b |
| cartBackStyle | `cart_back_style` | Enum | `CartBackStyle.AUTO` | SettingsDialog.kt, SolidPiece.kt, CustomCarts.kt | — | — |  |
| secondOpenCase | `second_open_case` | Bool | `true` | SettingsDialog.kt, LauncherScreen.kt | — | — | Update 198: the second screen's Details page shows the game's case in 3D, opening on a tap (MediaInspectView.kt). */ |
| inspectOpenAtStart | `inspect_open_at_start` | Bool | `false` | MediaInspectView.kt, SettingsDialog.kt | — | — | Update 198: an opening case starts opened — the case aside, its disc / cartridge out next to it. */ |
| inspectAutoReturn | `inspect_auto_return` | Bool | `true` | SettingsDialog.kt, SaveInspect.kt, MediaInspectView.kt | — | — | (Update 238, #116/#117: a piece turned in the game window stays at its angle for a while after you let go, then turns back slowly to face you — or stays where y |
| inspectHold | `inspect_hold` | Enum | `InspectHold.S3` | SettingsDialog.kt, MediaInspectView.kt, SaveInspect.kt | — | — | Update 238 (#116): how long it stays at its angle before turning back. */ (Update 504, owner: settle back about 3 s after you let go — the default for anyone wh |
| inspectReturnSpeed | `inspect_return_speed` | Enum | `InspectReturnSpeed.NORMA` | SettingsDialog.kt, MediaInspectView.kt, SaveInspect.kt | — | — | Update 238 (#116): how long its turn back takes. */ |
| inspectMode | `inspect_mode` | Enum | `InspectMode.INSPECT` | SettingsDialog.kt, GameWindow.kt, InspectChoice.kt | — | — |  |
| inspectSway | `inspect_sway` | Bool | `true` | SettingsDialog.kt, MediaInspectView.kt, SaveInspect.kt | — | — | Update 274 (Toni): a piece left alone (after its turn back) sways gently, as on the home rows. On by default. */ |
| inspectModeAsked | `inspect_mode_asked_274` | Bool | `false` | InspectChoice.kt, SettingsDialog.kt | — | — | Update 274: the Inspect / Wobble question has been answered (asked once, after this update). */ |
| discSpin | `disc_spin` | Bool | `true` | StyleDiscStation.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | od_spin, crate_spin | Update 147, Disc station: the loaded disc turns slowly. */ |
| discAbout | `disc_about` | Bool | `true` | StyleDiscStation.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — | Update 147, Disc station: a few lines about the game under its name. */ |
| discBlur | `disc_blur` | Int | `5` | StyleDiscStation.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | hero_blur, row_blur, name_blur | Update 149, Disc station: how much the game's art behind the disc is blurred (% of the player's width). */ |
| discPlayer | `disc_player` | Enum | `DiscPlayerLook.PLAYER` | StyleDiscStation.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| twinClock | `twin_clock` | Enum | `TwinClock.ANALOGUE` | TwinDesk.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | ar_clock |  |
| twinCalendar | `twin_calendar` | Bool | `true` | TwinDesk.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| twinDeskClock | `twin_desk_clock` | Bool | `true` | TwinDesk.kt | NO-SETTINGS-SCREEN | — | Update 180: what Twin desk's top shows besides the calendar: the clock, the game's picture, the game's name. */ |
| twinDeskArt | `twin_desk_art` | Bool | `false` | TwinDesk.kt | NO-SETTINGS-SCREEN | — |  |
| twinDeskGame | `twin_desk_game` | Bool | `true` | TwinDesk.kt | NO-SETTINGS-SCREEN | — |  |
| twinDeskPlayIcon | `twin_desk_play_icon` | Bool | `false` | TwinDesk.kt | NO-SETTINGS-SCREEN | — | Update 243 (#55): Twin desk's optional extras on the game you're on — the ▶ icon at the panel's end and the four corner handles round it. Off for new setups; an |
| twinDeskHandles | `twin_desk_handles` | Bool | `false` | TwinDesk.kt | NO-SETTINGS-SCREEN | — |  |
| twinFullTop | `twin_full_top` | Bool | `true` | TwinScreens.kt | NO-SETTINGS-SCREEN | — | Update 180: on two screens, the two-screen layouts fill the whole top screen with their own background (theme off). */ |
| twinDashboardAccess | `twin_dashboard_access` | Bool | `true` | TwinScreens.kt, TwinDesk.kt, PocketShared.kt | NO-SETTINGS-SCREEN | — | Update 310: allow navigation to the regular second-screen dashboard from twin layouts. */ |
| twinSharedCategories | `twin_shared_categories` | Bool | `false` | TwinScreens.kt, R1TileRules.kt, AppearanceStore.kt | NO-SETTINGS-SCREEN | — | Update 310: optionally show standard selectable categories above the twin layout\'s lower screen. */ |
| pocketName | `pocket_name` | String | `""` | PocketSettings.kt, PocketShared.kt | — | badge_name, model_name | The user name on the top screens ("" = the RetroAchievements name, else "Player"). */ |
| pocketAnim | `pocket_anim` | Enum | `PocketAnim.NORMAL` | PocketSettings.kt, PocketShared.kt | — | — |  |
| pocketArrange | `pocket_arrange` | Enum | `PocketArrange.AUTO` | PocketSettings.kt, PocketClassic.kt, TwinScreens.kt | — | mc_arrange, box_arrange |  |
| pocketPixel | `pocket_pixel2` | Bool | `false` | PocketClassic.kt, PocketRibbon.kt, PocketSettings.kt | — | — | Pixel-perfect (whole pixels per dot, 4:3) or fit the screen (as big as fits, wider when there's room). */ (Update 182: off by default — as big as fits; whole pi |
| pocketStrip | `pocket_strip` | Bool | `true` | PocketSettings.kt, PocketShared.kt | — | — | Update 182: the home strip under both Pocket menus (your shortcuts, the music, the time and battery). */ |
| pocketSounds | `pocket_sounds` | Bool | `true` | PocketSettings.kt, LauncherScreen.kt | — | ar_sounds, gc_sounds, fs_sounds |  |
| pocketStartCategory | `pocket_start_category` | String | `""` | PocketSettings.kt, PocketClassic.kt | — | — | The category a Pocket layout opens on ("" = where you were). */ |
| classicAccent | `classic_accent` | String | `"BLUE"` | PocketClassic.kt, TwinScreens.kt, GameWindow.kt | — | — | Pocket classic. |
| classicAnalogue | `classic_analogue` | Bool | `true` | PocketSettings.kt, PocketClassic.kt | — | — |  |
| classicCalendar | `classic_calendar` | Bool | `true` | PocketSettings.kt, PocketClassic.kt | — | — |  |
| classicToolbar | `classic_toolbar` | Bool | `true` | PocketClassic.kt, PocketSettings.kt, TwinScreens.kt | — | — |  |
| classicArt | `classic_art` | Enum | `PocketArt.COVER` | PocketClassic.kt, PocketSettings.kt | — | — |  |
| classicTopArt | `classic_top_art` | Bool | `true` | PocketSettings.kt, PocketClassic.kt | — | — | Update 187: while you browse a page of games or categories, the top screen shows it instead of the clock. */ |
| ribbonAccent | `ribbon_accent` | String | `"SKY"` | PocketRibbon.kt, TwinScreens.kt, PocketSettings.kt | — | gc_accent, ah_accent, cn_accent | Pocket ribbon. |
| ribbonTop | `ribbon_top` | Enum | `RibbonTop.GAME` | PocketSettings.kt, PocketRibbon.kt, LauncherScreen.kt | — | — |  |
| ribbonStatus | `ribbon_status` | Bool | `true` | PocketSettings.kt, PocketRibbon.kt | — | dash_status, stage_status |  |
| ribbonSlides | `ribbon_slides` | Bool | `true` | PocketSettings.kt, PocketRibbon.kt | — | — |  |
| ribbonNames | `ribbon_names` | Bool | `true` | PocketSettings.kt, PocketRibbon.kt | — | live_names, chan_names |  |
| ribbonGlow | `ribbon_glow` | Bool | `true` | PocketSettings.kt, PocketRibbon.kt | — | ui_glow, arcade_glow, gc_glow | Update 187: a soft glow round the icon you're on. */ |
| ribbonCart | `ribbon_cart` | Bool | `true` | R1TileRules.kt, PocketSettings.kt, PocketRibbon.kt | — | gbc_cart | Update 325 (Toni: "in Pocket Ribbon, in front of the hero make the cartridge appear by default with its animation, and instead of the hero the cover art from St |
| ribbonIcon | `ribbon_icon2` | Int | `44` | PocketSettings.kt, PocketRibbon.kt | — | — | Icon size (dots, 24–40) and the gap between icons (dots). */ (Update 182: bigger by default, up to 56.) */ |
| ribbonGap | `ribbon_gap` | Int | `10` | PocketSettings.kt, PocketRibbon.kt | — | twin_gap |  |
| ribbonSplit | `ribbon_split` | Int | `50` | PocketSettings.kt, PocketRibbon.kt | — | — | The top screen's share when both screens are on one display, % (30–70). */ |
| ribbonStartOnCategories | `ribbon_start_categories` | Bool | `false` | PocketSettings.kt, PocketRibbon.kt | — | — | Open on the categories (true) or on the games of the category you're in. */ |
| twinCols | `twin_cols` | Int | `5` | TwinIcons.kt, LayoutPresets.kt, PresetPreview.kt | NO-SETTINGS-SCREEN | chan_cols, lib_cols |  |
| twinIconsTone | `twin_icons_tone` | Enum | `TwinTone.MIX` | TwinIcons.kt, StatusLooks.kt | NO-SETTINGS-SCREEN | — |  |
| twinShowRows | `twin_show_rows` | Int | `2` | TwinShowcase.kt, LayoutPresets.kt, PresetPreview.kt | NO-SETTINGS-SCREEN | — | Update 307: Twin showcase's rows of icons (1–3); more games run on to the right. */ |
| twinShowStatusBar | `twin_show_status_bar` | Bool | `false` | TwinShowcase.kt, AppearanceStore.kt | NO-SETTINGS-SCREEN | — | Update 308: Twin showcase with Voltcap's status bar too (off: the picture's own status capsule). */ |
| twinGap | `twin_gap` | Int | `8` | TwinIcons.kt, TwinShowcase.kt, QuickSettings.kt | — | ribbon_gap | Update 179: the space between the tiles of the two-screen layouts, dp (Twin icons' grid, Twin desk's panels). */ |
| twinDeskGap | `twin_desk_gap` | Int | `7` | TwinDesk.kt, QuickSettings.kt | — | — |  |
| discCalm | `disc_calm_bg` | Bool | `true` | StyleDiscStation.kt, LauncherScreen.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — | Update 154: Disc station keeps the screen behind it still: no video clip, the animated background holds a frame. */ |
| spineCuts | `spine_cuts` | Bool | `false` | SettingsDialog.kt, Case3D.kt, BoxTexture.kt | — | — | Update 123: a game without ScreenScraper's spine scan may use a spine cut out of its full box scan (or the side of its 3D box picture). Off (default): only real |
| mediaShells | `media_shells` | Bool | `false` | AppearanceStore.kt, VoltSettings.kt, PhysicalMedia.kt | — | photo_shells | Cartridge / disc art drawn by Voltcap (its own shell + the game's label) instead of the real cartridge / disc picture scraped for the game. Off by default (upda |
| bigGcDiscs | `big_gc_discs` | Bool | `false` | PhysicalMedia.kt, StyleDiscStation.kt, MediaInspectView.kt | — | — | GameCube discs drawn as big as the 12 cm ones (off: their real, smaller 8 cm size). */ |
| gbcCart | `gbc_cart` | Enum | `GbcCart.BLACK` | SettingsDialog.kt, VoltSettings.kt, MediaVariants.kt | — | ribbon_cart |  |
| pauseOnClose | `pause_on_close` | Bool | `true` | SettingsDialog.kt, LidPause.kt | — | — | Closing the handheld (or its screen going off): Spotify / any music app and Voltcap's own music pause. */ |
| pauseOnLock | `pause_on_lock` | Bool | `false` | SettingsDialog.kt, LidPause.kt | — | — | Also pause music when the screen goes off by the power button / timeout (off: only when the lid closes). */ |
| mixerNotification | `mixer_notification` | Bool | `true` | MixerActivity.kt, NotificationSettings.kt, MixerNotification.kt | — | — | A notification in Android's pull-down with the volume mixer: − / + per app (a real slider can't live in a notification). */ |
| gridTiles | `grid_tiles` | Bool | `false` | GameLookPicker.kt, GridHome.kt, SettingsDialog.kt | — | cn_tiles | Grid home: every game as a square, rounded tile (like the Tile home) instead of the card artwork's own shape. */ |
| gridFocus | `grid_focus` | Enum | `GridFocus.LIFT` | SettingsDialog.kt, R1TileRules.kt, GridHome.kt | — | idle_focus |  |
| discsForAll | `discs_for_all` | Bool | `true` | SettingsDialog.kt, PhysicalMedia.kt, GameLookPicker.kt | — | — | Card artwork "Discs only": games from cartridge consoles become a plain CD with their label / cover on it too (off: they keep their cover, the old behaviour). * |
| tvRoomNames | `tv_room_names` | Bool | `true` | SettingsDialog.kt, TvRoomHome.kt | — | — | TV room: the game's name under each cartridge / disc / box in the grid. */ |
| categoryArt | `category_art` | Enum | `CategoryTileArt.SLIDESHO` | SettingsDialog.kt, CategoryArt.kt, LauncherScreen.kt | — | — | ---- The category screen (CategoryScreen.kt Tiles, PlatformBrowser.kt Slides / Wall / Grid) ---- What every category / platform card shows, in all four looks (S |
| dashTitle | `dash_title` | String | `"Voltcap"` | StyleHomeSettings.kt, StyleDashboard.kt | LAYOUT-ONLY | mc_title, cf_title | Dashboard: the big title top left (empty = hidden) and the small line under it. */ |
| dashSubtitle | `dash_subtitle` | String | `"Ready to play"` | StyleDashboard.kt, StyleHomeSettings.kt | LAYOUT-ONLY | — |  |
| dashMenu | `dash_menu` | Bool | `true` | StyleHomeSettings.kt, StyleHomes.kt, StyleDashboard.kt | LAYOUT-ONLY | — | Dashboard: the round-button menu on the left, and its names. */ |
| dashMenuLabels | `dash_menu_labels` | Bool | `true` | StyleDashboard.kt, StyleHomeSettings.kt | LAYOUT-ONLY | — |  |
| dashMenuCount | `dash_menu_count` | Int | `6` | StyleDashboard.kt, StyleHomeSettings.kt | LAYOUT-ONLY | — | Dashboard: how many of the round shortcuts the menu shows (Settings › Round shortcuts picks which / their order). */ |
| dashHero | `dash_hero` | Bool | `true` | StyleDashboard.kt, StyleHomeSettings.kt | LAYOUT-ONLY | cn_hero | Dashboard: the "Now playing" card with the game you're on big. */ |
| dashRowTitle | `dash_row_title` | String | `""` | StyleHomeSettings.kt, StyleDashboard.kt | LAYOUT-ONLY | side_row_title | Dashboard: the row's title ("" = the category's name). */ |
| dashQuick | `dash_quick` | Int | `5` | StyleHomes.kt, StyleDashboard.kt, StyleHomeSettings.kt | LAYOUT-ONLY | — | Dashboard: buttons in the Quick access strip (0 = no strip). */ |
| dashProfile | `dash_profile` | Bool | `true` | StyleDashboard.kt, StyleHomeSettings.kt | LAYOUT-ONLY | pd_profile | Dashboard: the player card and the system-status card on the right. */ |
| styleMusic | `style_music` | Bool | `true` | StyleMusicCard.kt, StyleHomeSettings.kt, StyleDashboard.kt | LAYOUT-ONLY | — | Style homes (Dashboard, Live tiles): the music player built into the layout, bottom right. */ |
| dashPlayerArt | `dash_player_art` | Bool | `true` | StyleDashboard.kt, StyleHomeSettings.kt | LAYOUT-ONLY | — | Dashboard (update 125): the album cover in its player (on by default). */ |
| heroFitByLayout | `hero_fit_by_layout` | String | `""` | AppearanceStore.kt, HomeChrome.kt, SettingsDialog.kt | — | — | Update 125: how the background picture sits, per layout ("LAYOUT=FILL/WHOLE" entries; Settings › the layout's options). */ |
| namesByLayout | `names_by_layout` | String | `""` | AppearanceStore.kt, SettingsDialog.kt, LauncherScreen.kt | — | — | Update 125: where the game names go, per layout ("LAYOUT=PLACEMENT,SIDE" entries; the layout's options in Settings). */ |
| batteryWatch | `battery_watch_on` | Bool | `true` | BatteryWatchUi.kt, BatteryWatch.kt, QuickSettings.kt | — | — | Battery watcher: Voltcap measures its own share of the battery use (a light check once a minute). */ |
| artInBackground | `art_in_background` | Bool | `true` | LauncherViewModel.kt, SettingsDialog.kt, GameMode.kt | — | — | Update 190: artwork downloads go on while Voltcap isn't on screen (behind a game too; ArtDownloadService). */ |
| saverMode | `saver_mode` | Enum | `BatterySaver.Mode.AUTO` | BatterySaverUi.kt, BatterySaver.kt, LauncherScreen.kt | — | status_mode, ar_mode, mirror_mode | Update 187: the battery saver (BatterySaver): when the low-battery rest is on (when low / always / off). */ |
| saverLowAt | `saver_low_at` | Int | `20` | BatterySaverUi.kt, BatterySaver.kt | NO-SETTINGS-SCREEN | — | Update 187: below this level (%), not plugged in, the low-battery rest starts. */ |
| idleRestMin | `idle_rest_min` | Int | `10` | BatterySaverUi.kt, PowerSaver.kt | NO-SETTINGS-SCREEN | — | Update 321 (Toni: Voltcap drained 30 % overnight): after this many minutes without a press or a touch, Voltcap rests (HomeIdle.rest): the background clip stops, |
| clipIdlePause | `clip_idle_pause` | Bool | `false` | BatterySaverUi.kt, LauncherScreen.kt, StyleCinema.kt | NO-SETTINGS-SCREEN | — | Next version (Toni, after the RP Classic pass, D64): the background gameplay clip holds its frame after 30 s without input (HomeIdle.calm) and plays on at the f |
| tileSwitch | `tile_switch` | Bool | `true` | SettingsDialog.kt, TileSwitch.kt | — | box_switch | Update 358 (Toni): Tile home › Switch style — on by default; off is Tile home as before. Each part its own switch (TileSwitch.kt). */ |
| tileSwitchPlain | `tile_switch_plain` | Bool | `true` | SettingsDialog.kt, TileSwitch.kt | — | — |  |
| tileSwitchProfiles | `tile_switch_profiles` | Bool | `true` | SettingsDialog.kt, TileSwitch.kt | — | — |  |
| tileSwitchFrame | `tile_switch_frame` | Bool | `true` | SettingsDialog.kt, TileSwitch.kt | — | — |  |
| tileSwitchHints | `tile_switch_hints` | Bool | `true` | SettingsDialog.kt, TileSwitch.kt | — | — |  |
| saverFollowSystem | `saver_follow_system` | Bool | `true` | BatterySaverUi.kt, BatterySaver.kt | NO-SETTINGS-SCREEN | — | Update 187: Android's own Battery Saver on counts as low battery too. */ |
| saverAway | `saver_away` | Bool | `true` | BatterySaverUi.kt, BatterySaver.kt | NO-SETTINGS-SCREEN | — | Update 187: Voltcap rests while a game or another app has the controller (two-screen handhelds). */ |
| whoPlays | `who_plays` | Bool | `false` | RetroAchievementsDialog.kt, LauncherScreen.kt | NO-SETTINGS-SCREEN | — | Ask "Who is playing?" at start-up when two RetroAchievements accounts are saved. */ |
| startupMode | `startup_mode` | Enum | `StartupMode.PROFILES_LAY` | LauncherScreen.kt, FirstRunDialog.kt, SettingsDialog.kt | — | — |  |
| rolloutOn | `rollout_on` | Bool | `true` | SettingsDialog.kt, ProfileSelect.kt | — | — | Update 332: after "Who's playing?", the games come in one after another (the layout you have). */ |
| rolloutSpeed | `rollout_speed` | Enum | `RolloutSpeed.NORMAL` | ProfileSelect.kt, SettingsDialog.kt | — | — |  |
| pickerLook | `picker_look` | Enum | `PickerLook.MIDNIGHT` | ProfileLooks.kt, ProfileSelect.kt, TestHooks.kt | NO-SETTINGS-SCREEN | cart_look, second_look, shelf_look |  |
| statusMatchesLayout | `status_matches_layout` | Bool | `true` | FirstRunDialog.kt, SettingsDialog.kt, StatusLooks.kt | — | — | Update 333: the status bar dressed for the layout it's on (StatusLooks.kt); off: the plain shade everywhere. */ |
| tileInfoStrip | `tile_info_strip` | Bool | `true` | SettingsDialog.kt, LauncherScreen.kt | — | — | Update 480 (D257): Tile home — the focused game's facts under the tiles when no round buttons are there (Layout options). */ |
| statusMode | `status_mode` | Enum | `StatusMode.FOLLOW` | SettingsDialog.kt, AppearanceStore.kt | — | saver_mode, ar_mode, mirror_mode |  |
| dashStatus | `dash_status` | Bool | `true` | StyleDashboard.kt, StyleHomeSettings.kt | LAYOUT-ONLY | ribbon_status, stage_status |  |
| dashPanel | `dash_panel` | Enum | `PanelLook.LIGHT` | StyleHomeSettings.kt, DrawerLook.kt, StatusLooks.kt | — | lib_panel | Dashboard: panel look, opacity (%), and cut (angled) corners instead of round ones. */ |
| dashOpacity | `dash_opacity` | Int | `62` | StyleDashboard.kt, StyleHomeSettings.kt, DrawerLook.kt | — | dock_opacity |  |
| dashCut | `dash_cut` | Bool | `true` | StyleDashboard.kt, StyleHomeSettings.kt, DrawerLook.kt | — | — |  |
| liveTabs | `live_tabs` | Bool | `true` | StyleLiveTiles.kt, StyleHomeSettings.kt | LAYOUT-ONLY | spot_tabs, lib_tabs | Live tiles: the category names across the top. */ |
| livePage | `live_page` | Enum | `LivePage.BIG_FOUR` | GameSize.kt, StyleHomeSettings.kt, StyleLiveTiles.kt | — | anim_page, fs_page |  |
| liveBar | `live_bar` | Int | `4` | StyleHomes.kt, StyleLiveTiles.kt, StyleHomeSettings.kt | LAYOUT-ONLY | stacks_bar | Live tiles: coloured shortcut tiles under the games (0 = none). */ |
| liveColor | `live_color` | Enum | `LiveColor.THEME` | StyleHomeSettings.kt, StatusLooks.kt, StyleLiveTiles.kt | — | dock_color, od_color, nm_color |  |
| liveLight | `live_light` | Bool | `true` | StyleHomes.kt, StyleLiveTiles.kt, PresetPreview.kt | — | cover_light, chan_light | Live tiles: a pale backdrop behind everything (off: the theme shows through). */ |
| liveArt | `live_art` | Bool | `false` | StyleHomes.kt, PresetPreview.kt, StyleHomeSettings.kt | — | era_art, mixed_art, side_art | Update 146, Live tiles: the game's art fills the background (then the pale backdrop is off). */ |
| liveNames | `live_names` | Bool | `true` | StyleLiveTiles.kt, StyleHomeSettings.kt | LAYOUT-ONLY | ribbon_names, chan_names | Live tiles: the game's name on its tile. */ |
| sideEmblem | `side_emblem_v2` | Enum | `SideEmblem.PROFILE` | StyleSideMenu.kt, StyleHomeSettings.kt | LAYOUT-ONLY | — | Side menu: the round picture top left — your RetroAchievements / Steam picture by default (update 84, new key). */ |
| sideOrb | `side_orb` | Bool | `true` | StyleSideMenu.kt, StyleHomeSettings.kt | LAYOUT-ONLY | — | Side menu: the glowing orb, with the game you're on under it. */ |
| sideCaps | `side_caps` | Bool | `true` | StyleSideMenu.kt, StyleHomeSettings.kt | LAYOUT-ONLY | — | Side menu: menu names in capitals. */ |
| sideWidth | `side_width` | Int | `200` | StyleSideMenu.kt, StyleHomeSettings.kt | LAYOUT-ONLY | — | Side menu: its width (dp). */ |
| sideGold | `side_gold` | Bool | `false` | StyleSideMenu.kt, StatusLooks.kt, StyleHomeSettings.kt | — | — | Side menu: gold trim instead of the theme's accent. */ |
| sideArt | `side_art` | Bool | `true` | StyleHomes.kt, PresetPreview.kt, StyleHomeSettings.kt | — | era_art, mixed_art, live_art | Side menu: the game's art behind everything. */ |
| sideRowTitle | `side_row_title` | Bool | `true` | StyleSideMenu.kt, StyleHomeSettings.kt | LAYOUT-ONLY | dash_row_title | Side menu: the category's name above the row. */ |
| crossWaves | `cross_waves` | Bool | `true` | StyleCrossMenu.kt, StyleHomeSettings.kt | LAYOUT-ONLY | — | Cross menu: flowing ribbons across the background. */ |
| crossTint | `cross_tint` | Enum | `CrossTint.THEME` | StyleHomeSettings.kt, WinScenesHomes2.kt, StyleCrossMenu.kt | — | cube_tint |  |
| crossInfo | `cross_info` | Bool | `true` | StyleCrossMenu.kt, StyleHomeSettings.kt | LAYOUT-ONLY | anim_info | Cross menu: "Last played" / time played under the game you're on. */ |
| crossTrophies | `cross_trophies` | Bool | `true` | CrossTrophies.kt, StyleHomeSettings.kt | — | — | Update 127: on the Cross menu a game's achievements open as the menu's own column (CrossTrophies.kt), not the list popup. */ |
| crossTrophyCat | `cross_trophy_cat` | Bool | `true` | LauncherUiState.kt, StyleHomeSettings.kt, LauncherScreen.kt | — | — | Update 132: a Trophies category in the Cross menu's strip (SpecialFilter.TROPHIES): every game with achievements, last played first, each with how far you are;  |
| crossCats | `cross_cats` | Set | `` | StyleHomeSettings.kt, LauncherUiState.kt | — | — | Update 168: the lists the Cross menu shows beside the consoles even when they're hidden in Settings › Categories (Continue playing, Favourites, Most played, the |
| crossScale | `cross_scale` | Int | `100` | GameSize.kt, StyleCrossMenu.kt, CrossTrophies.kt | — | layout_scale | Cross menu: size of the category icons and the list (%). */ |
| layoutScale | `layout_scale` | String | `""` | GameSize.kt, ShelfHome.kt | NO-SETTINGS-SCREEN | cross_scale | Game size of the layouts without a size of their own (update 104): "LAYOUT=pct;…" (70–140, 100 = normal), set by the R1 Size tile. */ |
| shortcutsOpen | `shortcuts_open` | Bool | `false` | QuickSettings.kt, TileHome.kt | — | — | The round shortcuts in a corner stay open (else folded behind their ☰ until pressed) — update 104. */ |
| crossLabels | `cross_labels` | Bool | `true` | StyleCrossMenu.kt, StyleHomeSettings.kt | LAYOUT-ONLY | — | Cross menu: the category's name under its icon. */ |
| crossLogoTint | `cross_logo_tint` | Bool | `false` | StyleCrossMenu.kt, StyleHomeSettings.kt | LAYOUT-ONLY | — | Cross menu (update 90/91): the tile behind each console logo in the accent colour (the game's or the theme's); logos keep their colours. */ |
| crossLogoCut | `cross_logo_cut` | Bool | `false` | QuickSettings.kt, StyleCrossMenu.kt, StyleHomeSettings.kt | — | — | Cross menu: the console logos as one-colour cut-outs (no tile behind them), white or the accent colour. */ |
| crossFolders | `cross_folders` | Bool | `true` | QuickSettings.kt, StyleCrossMenu.kt, StyleHomeSettings.kt | — | bv_folders | Cross menu (update 104): each console as a plain white folder with its name, the special categories keep their icons. Off: the console logos (on tiles, or cut o |
| shelfRealSizes | `shelf_real_sizes_348` | Bool | `true` | ShelfHome.kt, StyleHomeSettings.kt | LAYOUT-ONLY | — | Game shelf (update 90): every spine at its console's real case size (else all the same). */ (Update 348, Toni: "in real sizes as well": on by default — a new ke |
| shelfBob | `shelf_bob` | Bool | `true` | ShelfHome.kt, StyleHomeSettings.kt | LAYOUT-ONLY | — | Game shelf: the game you're on bobs and turns gently once it's pulled out. */ |
| shelfPulled | `shelf_pulled` | Enum | `ShelfPulled.FRONT` | ShelfHome.kt, SettingsDialog.kt, TvRoomHome.kt | — | — | Game shelf and TV room shelf (update 129): how the game you're on shows once pulled out. */ |
| spineRecheckAt | `spine_recheck_at` | String | `""` | LauncherViewModel.kt, StyleHomeSettings.kt | — | — | Game shelf › "Look for spines again" (update 108): when it was pressed (ms); games looked up before then are asked again. */ |
| spineScrape | `spine_scrape` | Bool | `false` | SettingsDialog.kt, LauncherViewModel.kt | — | — | Update 114: every game's box scans (its spine) looked up in the background (Settings › Metadata › Saved on this device). */ |
| shelfStand | `shelf_stand` | Enum | `ShelfStand.SPINES` | StyleHomeSettings.kt, PresetPreview.kt, ShelfHome.kt | — | — |  |
| tilesSpacing | `tiles_spacing` | Int | `100` | SettingsDialog.kt, R1Extras.kt, TileHome.kt | — | stacks_spacing, row_spacing, grid_spacing | Update 488 (K1): Tile home — room between the tiles, % of the standard (100 = as before). */ |
| stacksSpacing | `stacks_spacing` | Int | `100` | StyleCaseStacks.kt, StyleHomeSettings.kt, R1Extras.kt | — | tiles_spacing, row_spacing, grid_spacing | Update 488 (K1): Case stacks — room between the stacked spines, % (100 = as before). */ |
| rowSpacing | `row_spacing` | Int | `100` | SettingsDialog.kt, R1Extras.kt, GameDock.kt | — | tiles_spacing, stacks_spacing, grid_spacing | Update 488 (K1): the Voltcap row — room between the games, % of the standard gap (100 = as before). */ |
| gridSpacing | `grid_spacing` | Int | `100` | SettingsDialog.kt, GridHome.kt, R1Extras.kt | — | tiles_spacing, stacks_spacing, row_spacing | Batch 520 (K1): the Game grid (main screen) — room between the cards, % of the standard gaps (100 = as before). */ |
| shelfSpacing | `shelf_spacing` | Int | `100` | ShelfHome.kt, StyleHomeSettings.kt, R1Extras.kt | — | tiles_spacing, stacks_spacing, row_spacing | Update 487 (K1): Game shelf — room between the games, % of the standard gap (100 = as before). */ |
| shelfLook | `shelf_look` | Enum | `ShelfLook.DARK` | QuickSettings.kt, ShelfHome.kt, StyleHomeSettings.kt | — | cart_look, second_look, picker_look |  |
| shelfBackdrop | `shelf_backdrop` | Bool | `true` | StyleHomes.kt, QuickSettings.kt, StyleHomeSettings.kt | — | cube_backdrop | Game shelf: the whole screen behind the shelves is the bookcase (back panel, side uprights, top crown). */ |
| spotShape | `spot_shape` | Enum | `TileShape.SQUARE` | StyleSpotlight.kt, StyleHomeSettings.kt, PresetPreview.kt | — | — |  |
| spotLook | `spot_look` | Enum | `SpotLook.HOME_MENU` | StyleHomeSettings.kt, StyleHomes.kt, AppearanceStore.kt | — | cart_look, second_look, picker_look |  |
| spotStatusBar | `spot_status_bar` | Bool | `false` | StyleSpotlight.kt, StyleHomeSettings.kt, AppearanceStore.kt | — | chan_status_bar | Spotlight's Home menu (update 132): the status bar too (off: the menu's own profile, battery and clock on top). */ |
| spotHints | `spot_hints` | Bool | `true` | StyleSpotlight.kt, StyleHomeSettings.kt, AppearanceStore.kt | — | mc_hints | Spotlight's Home menu (update 132): what the buttons do, along the bottom. */ |
| spotTilesAt | `spot_tiles_at` | Enum | `SpotSide.TOP` | QuickSettings.kt, StyleSpotlight.kt, StyleHomes.kt | — | — |  |
| spotNameAt | `spot_name_at` | Enum | `SpotName.RIGHT` | QuickSettings.kt, StyleHomeSettings.kt, StyleSpotlight.kt | — | — |  |
| spotTitleAt | `spot_title_at` | Enum | `SpotAcross.LEFT` | StyleHomeSettings.kt, QuickSettings.kt, StyleSpotlight.kt | — | — |  |
| spotTitleRow | `spot_title_row` | Enum | `SpotAlong.FAR` | StyleHomeSettings.kt, QuickSettings.kt, StyleSpotlight.kt | — | — |  |
| spotLogo | `spot_logo` | Bool | `true` | StyleSpotlight.kt, StyleHomeSettings.kt | LAYOUT-ONLY | cn_logo | Spotlight: the game's logo as its title when there is one. */ |
| spotCards | `spot_cards` | Bool | `true` | StyleSpotlight.kt, StyleHomeSettings.kt | LAYOUT-ONLY | — | Spotlight: the activity cards (achievements, time played, last played). */ |
| spotTabs | `spot_tabs` | Bool | `true` | StyleSpotlight.kt, StyleHomeSettings.kt | LAYOUT-ONLY | live_tabs, lib_tabs | Spotlight: the category name (and the next one) top left. */ |
| spotArt | `spot_art` | Bool | `true` | StyleHomes.kt, PresetPreview.kt, StyleHomeSettings.kt | — | era_art, mixed_art, live_art | Spotlight: the game's art behind everything, and how dark the shade over it is (%). */ |
| spotShade | `spot_shade` | Int | `55` | StyleHomes.kt, StyleHomeSettings.kt | LAYOUT-ONLY | hero_shade |  |
| barIcons | `bar_icons` | Bool | `true` | StyleHomes.kt, R1TileRules.kt, StyleHomeSettings.kt | — | retro_icons, hub_icons | Icon bar: the shortcut icons along the top. */ |
| barTile | `bar_tile` | Int | `100` | GameSize.kt, StyleHomeSettings.kt, StyleIconBar.kt | — | — | Icon bar: tile size (%), and how much bigger the one you're on is (%). */ |
| barGrow | `bar_grow_v3` | Int | `155` | StyleHomeSettings.kt, StyleIconBar.kt | LAYOUT-ONLY | — | Icon bar: how much bigger the game you're on pops out (update 86: like the PS5's, the others stay put; new key). */ |
| barNames | `bar_names_v2` | Bool | `false` | StyleHomeSettings.kt, StyleIconBar.kt | LAYOUT-ONLY | — | Icon bar: the game's name on its tile. */ |
| barStart | `bar_start` | Bool | `true` | StyleIconBar.kt, StyleHomeSettings.kt | LAYOUT-ONLY | bl_start, pd_start | Icon bar: the Start button and time played under the game you're on. */ |
| barArt | `bar_art` | Bool | `false` | StyleHomeSettings.kt, StyleHomes.kt, PresetPreview.kt | — | era_art, mixed_art, live_art | Icon bar: the game's art behind everything. */ |
| barArtCut | `bar_art_cut` | Bool | `true` | QuickSettings.kt, StyleHomes.kt, PresetPreview.kt | — | — | Icon bar: the game's art behind is cut at the middle of the screen (it fades out there) instead of covering all of it. */ |
| barTabsCurrent | `bar_tabs_current` | Bool | `true` | QuickSettings.kt, StyleHomes.kt, StyleHomeSettings.kt | — | — | Icon bar: the tabs along the top show only the category you're in (◀ ▶ step through the categories) instead of your shortcuts. */ |
| barGamesAt | `bar_games_at` | Enum | `SpotSide.TOP` | StyleHomes.kt, StyleHomeSettings.kt, QuickSettings.kt | — | — | (Update 235, #08: Icon bar — the row of tiles right under the tabs (TOP, as before) or along the bottom with the title, Start and time played just above it (BOT |
| hubIcons | `hub_icons` | Bool | `true` | StyleHomes.kt, StyleConsoleHomes.kt, StyleHomeSettings.kt | LAYOUT-ONLY | retro_icons, bar_icons | Console hub: shortcut icons along the top, and the game's art behind. */ |
| hubArt | `hub_art` | Bool | `true` | StyleHomes.kt, PresetPreview.kt, StyleHomeSettings.kt | — | era_art, mixed_art, live_art |  |
| resArt | `res_art` | Bool | `true` | StyleHomes.kt, PresetPreview.kt, StyleHomeSettings.kt | — | era_art, mixed_art, live_art | Resume & discover: the game's art behind. */ |
| chanCols | `chan_cols` | Int | `4` | GameSize.kt, StyleHomeSettings.kt, LayoutPresets.kt | — | twin_cols, lib_cols | Channel grid: tiles per row, pale backdrop, the name on the tile you're on. */ |
| chanLight | `chan_light` | Bool | `true` | StyleHomes.kt, StyleConsoleHomes.kt, StyleHomeSettings.kt | — | cover_light, live_light |  |
| chanDark | `chan_dark` | Bool | `false` | ChanPalette.kt | NO-SETTINGS-SCREEN | — | Update 329 (Toni: "a dark Wii Channel-inspired theme as a toggle for the current one, because of OLED"): the pale Channel grid in black and dark grey — true bla |
| chanPalette | `chan_palette` | String | `""` | ChanPalette.kt | NO-SETTINGS-SCREEN | pc_palette | Update 421: the Channel grid's colours — "" = follow the older switches (Dark on = OLED Black, else Original Pale), so a saved setup looks exactly as before unt |
| chanCustom | `chan_custom` | Int | `0xFFDCE8F4.toInt(` | StyleHomeSettings.kt, ChanPalette.kt | — | — | Update 421: the Channel grid's Custom palette base colour (ARGB), edited with the shared HEX / saturation / brightness tool. */ |
| chanArt | `chan_art` | Bool | `false` | StyleHomes.kt, PresetPreview.kt, StyleHomeSettings.kt | — | era_art, mixed_art, live_art | Update 146, Channel grid: the game's art fills the background (then the pale backdrop is off). */ |
| chanNames | `chan_names` | Bool | `true` | StyleConsoleHomes.kt, StyleHomeSettings.kt | LAYOUT-ONLY | ribbon_names, live_names |  |
| gridsContinuous | `grids_continuous` | Bool | `true` | StyleConsoleHomes.kt, StyleHomes.kt, StyleHomeSettings.kt | LAYOUT-ONLY | — | Update 112: the Channel grid scrolls on continuously, a column at a time (◀ ▶), instead of turning pages. |
| chanStatusBar | `chan_status_bar` | Bool | `false` | StyleConsoleHomes.kt, StyleHomeSettings.kt, AppearanceStore.kt | — | spot_status_bar | Channel grid (update 104): the status bar along the top; off by default, the grid has its own corner buttons and clock. */ |
| chanSymbols | `chan_symbols` | Bool | `true` | StyleConsoleHomes.kt, StyleHomeSettings.kt | LAYOUT-ONLY | — | Update 115: the Channel grid's corner circle shows the category as a symbol (on), or the console's logo in colour / the category's name. */ |
| partStyles | `part_styles` | String | `""` | HomeEditSession.kt, HomePartStyle.kt | NO-SETTINGS-SCREEN | — | Update 116: each Edit Home part's own style ("id=backing,alpha,outline,shape,still;…", HomePartStyle.kt). */ |
| recapOtherDevices | `recap_other_devices` | Bool | `true` | StatsContent.kt, YearRecap.kt | NO-SETTINGS-SCREEN | — | Update 117: the recaps count the games played on your other devices too (synced with Google Drive), and a game that isn't on this device shows as its cover (loo |
| recapForeignArt | `recap_foreign_art` | Enum | `RecapForeignArt.COVER` | StatsContent.kt, YearRecapDialog.kt | NO-SETTINGS-SCREEN | — |  |
| musicSkins | `music_skins` | Bool | `true` | SettingsDialog.kt, PlayerSkins.kt | — | — | Update 117: the small music player in each layout's own style (off: the album art's colour everywhere). */ |
| resNextAch | `res_next_ach` | Bool | `true` | StyleHomeSettings.kt, LayoutPresets.kt, StyleConsoleHomes.kt | — | — | Resume & discover / Console hub (update 104): the next achievements to earn slide past, faint, behind the Achievements card. */ |
| gameWindow | `game_window` | Bool | `true` | SettingsDialog.kt, LauncherScreen.kt, QuickSettings.kt | — | — | Update 113: A or a tap on the game you're on opens the layout's own game window (back · Details · Play), on the main screen. Off: straight into the game. (Repla |
| classicWindowLayouts | `classic_window_layouts` | Set | `emptySet(` | QuickSettings.kt, SettingsDialog.kt, StyleSpotlight.kt | — | — | Update 119: the layouts whose A opens the classic details window instead of their own game window. */ |
| winTrophyOrder | `win_trophy_order` | Enum | `TrophyOrder.SET` | GameWindow.kt | NO-SETTINGS-SCREEN | — |  |
| gameWindowColors | `game_window_colors` | Enum | `WinColors.GAME` | SettingsDialog.kt, GameWindow.kt, GameWindowBanner.kt | — | — |  |
| gameWindowShows | `game_window_shows` | Set | `setOf("MEDIA"` | GameWindow.kt | NO-SETTINGS-SCREEN | — | Update 119: what the game window shows where it has room (names of [com.shelf.launcher.ui.WinPart]). */ |
| gameWindowPlan | `game_window_plan` | String | `""` | GameWindow.kt | NO-SETTINGS-SCREEN | — | Update 125: where each part of the game window goes, per layout ("LAYOUT.PART=SLOT", GameWindow.kt WinPlan). */ |
| gameWindowCase | `game_window_case` | Bool | `true` | GameWindow.kt, SettingsDialog.kt | — | — | Update 122: the game window's disc games as their case with the disc half out of it (the default); off: the disc alone. */ |
| libCols | `lib_cols` | Int | `5` | StyleLibrary.kt, LayoutPresets.kt, PresetPreview.kt | NO-SETTINGS-SCREEN | twin_cols, chan_cols | ---- Update 146: the newer layouts and the banner game window ---- Library panel: games per row in the grid (3–8). */ |
| libPanel | `lib_panel` | Bool | `true` | StyleLibrary.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | dash_panel | Library panel: the game panel on the left (off: the grid alone, full width). */ |
| libTabs | `lib_tabs` | Bool | `true` | StyleLibrary.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | live_tabs, spot_tabs | Library panel: category tabs along the top. */ |
| stacksDepth | `stacks_depth` | Bool | `true` | StyleCaseStacks.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | spine_depth, mc_depth | Case stacks: the stacks drawn in perspective, going back into the room (off: flat, straight on). */ |
| stacksBar | `stacks_bar` | Bool | `true` | StyleCaseStacks.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | live_bar | Case stacks: the button bar along the bottom. */ |
| stacksMirror | `stacks_mirror` | Bool | `true` | StyleCaseStacks.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — | Case stacks: a mirror floor under the cases. */ |
| stacks3d | `stacks_3d` | Bool | `true` | StyleCaseStacks.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | box_3d | Update 151: Case stacks: the game in the middle as a real 3D case, turned a little so its spine shows (off: flat). */ |
| caseModels3d | `case_models_3d2` | Bool | `false` | RealMedia.kt | NO-SETTINGS-SCREEN | — | Update 159: the case looks (Real cases, Cases · real sizes) in your 3D shape library's cases (R1 › 3D). */ (Update 182: off for good — the R1 3D tile is greyed  |
| stacksToolbar | `stacks_toolbar` | Bool | `true` | StyleCaseStacks.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — | Update 159: Case stacks: the tool strip along the top (your round shortcuts as small square buttons). */ |
| keepArt | `keep_art` | Bool | `true` | SettingsDialog.kt, MemoryBudget.kt | — | era_art, mixed_art, live_art | Update 159: the decoded art stays in memory — a bigger share of a larger heap, not dropped when a game starts (only when Android is really short), so every pict |
| motionOnLite | `motion_on_lite` | Bool | `true` | SettingsDialog.kt, GameWindow.kt, Carousel.kt | — | — | Update 162: the carousel's motion, its category slide and the open / inspect animation (the piece flying out and turning) run in full even with Lite effects on  |
| timelineBy | `timeline_by` | Enum | `TimelineBy.LAST_PLAYED` | StyleTimeline.kt, StyleTimelineHistory.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| timelineDates | `timeline_dates` | Bool | `true` | StyleTimeline.kt, StyleTimelineHistory.kt, PresetPreview.kt | NO-SETTINGS-SCREEN | — | Timeline: the date / year under each game. */ |
| arcadeGlow | `arcade_glow` | Bool | `true` | StyleArcade.kt, PresetPreview.kt | NO-SETTINGS-SCREEN | ui_glow, ribbon_glow, gc_glow | Arcade cabinets: the marquee lit and glowing. */ |
| autoM3u | `auto_m3u` | Bool | `false` | LibraryDialog.kt, RomScanner.kt | NO-SETTINGS-SCREEN | — | Update 177: a multi-disc game without a .m3u playlist gets one written by the scan (off: listed under Library check). */ |
| secondFolderLook | `second_folder_look` | Enum | `FolderLook.FOLDERS` | SecondGamesSettings.kt, SecondGamesPage.kt | — | — | (Update 185: back to your own choice — Folders by default; it's the games inside that are Tiles now.) */ |
| secondGamesLook | `second_games_look` | Enum | `FolderGamesLook.TILES` | SecondGamesPage.kt | NO-SETTINGS-SCREEN | — |  |
| secondFolderLogo | `second_folder_logo` | Bool | `true` | SecondGamesSettings.kt, SecondGamesPage.kt | — | — | Update 177: the console's logo over a folder picture (off: the picture alone — many packs have the name in it). */ |
| secondFolderPlain | `second_folder_plain` | Bool | `true` | SecondGamesSettings.kt, SecondGamesPage.kt | — | — | Update 286 (Toni): the consoles' logos on the Games page as plain icons in the theme's colours (off: their own colours). */ |
| secondTopFollows | `second_top_follows` | Bool | `true` | SecondGamesSettings.kt, SecondGamesPage.kt, LauncherScreen.kt | — | — | Update 200: the top screen follows the second screen's Games page: a folder lit / touched there shows that console big up top, and opening it switches the top s |
| customLayout | `custom_layout` | String | `""` | CustomLayoutSettings.kt, StyleCustom.kt | — | — | Update 176: the custom layout shown by Custom layout (CustomLayouts.kt; "" = the built-in example). */ |
| arcadeLines | `arcade_lines` | Bool | `true` | StyleArcade.kt, PresetPreview.kt | NO-SETTINGS-SCREEN | stage_lines | Arcade cabinets: scan lines over the screen. */ |
| arcadeGameColors | `arcade_game_colors` | Bool | `true` | StyleArcade.kt, PresetPreview.kt | NO-SETTINGS-SCREEN | — | Arcade cabinets: the cabinets in the game's own colours (off: the theme's). */ |
| arMode | `ar_mode` | Enum | `ArMode.SHOWCASE` | StyleArcade.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | saver_mode, status_mode, mirror_mode |  |
| arCab | `ar_cab` | Enum | `ArCab.UPRIGHT` | StyleArcade.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | — |  |
| arView | `ar_view` | Enum | `ArView.SIDE` | StyleArcade.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | ach_view, pb_view |  |
| arSpacing | `ar_spacing` | Enum | `ArSpacing.NORMAL` | StyleArcade.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | tiles_spacing, stacks_spacing, row_spacing |  |
| arCrt | `ar_crt` | Enum | `ArCrt.LIGHT` | StyleArcade.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | tm_crt |  |
| arScreen | `ar_screen` | Enum | `ArScreen.SHOTS` | StyleArcade.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | — |  |
| arVideo | `ar_video` | Bool | `true` | StyleArcade.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | — | The gameplay clip on the machine you're on, after a moment (where the game has one). */ |
| arSideArt | `ar_side_art` | Bool | `true` | StyleArcade.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | — | The game's art printed on the cabinets' side panels (off: plain paint). */ |
| arRoom | `ar_room` | Enum | `ArRoom.CARPET` | StyleArcade.kt, StyleHomes.kt, StatusLooks.kt | NO-SETTINGS-SCREEN | — | (Update 348: the 1990s arcade by default — realism first; the neon stage stays one pick away.) |
| arMotion | `ar_motion` | Bool | `true` | StyleArcade.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | lyrics_motion, disc_motion, cart_motion | Camera glides and the machine you're on coming forward (off: straight there, still). */ |
| arSpeed | `ar_speed` | Enum | `ArSpeed.NORMAL` | StyleArcade.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | cf_speed, box_speed, pq_speed |  |
| arSounds | `ar_sounds` | Bool | `false` | StyleArcade.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | pocket_sounds, gc_sounds, fs_sounds | The arcade's own sounds: joystick clicks, a coin, a CRT switching on (off: Voltcap's usual sounds). */ |
| consolePhotos | `console_stack_photos` | Bool | `true` | StyleConsoleStack.kt, StyleBlades.kt, PresetPreview.kt | NO-SETTINGS-SCREEN | — | Console stack: the consoles as your console photos where there are (Settings › Library), else drawn. */ |
| consoleHwPick | `console_hw_pick` | Set | `` | CategoryArt.kt, SaveExplorer.kt, StyleConsoleStack.kt | NO-SETTINGS-SCREEN | — | Update 330: the console picture picked per console, "KEY=name" (ConsoleArt). */ |
| consoleStackGames | `console_stack_games` | Int | `6` | StyleConsoleStack.kt, LayoutPresets.kt, PresetPreview.kt | NO-SETTINGS-SCREEN | — | Console stack: how many games slide out of the console you're on (3–9). */ |
| winBanner | `win_banner` | Bool | `false` | SettingsDialog.kt, QuickSettings.kt | — | — | Banner game window (update 146): the game window as one page — a wide art banner on top, a coloured strip with Play, last played and play time, then tabs (L1 /  |
| winScenes | `win_scenes` | Bool | `true` | SettingsDialog.kt, StyleFileSelect.kt, GameWindow.kt | — | — | Update 330 (Toni: "each popup a natural extension of its layout"): the layouts that have a window of their own making (the Cardboard box's insert sheet beside t |
| winMotion | `win_motion` | Enum | `WinMotion.NONE` | SettingsDialog.kt, QuickSettings.kt | — | lyrics_motion, disc_motion, cart_motion |  |
| popupStyleByLayout | `popup_style_by_layout` | Set | `` | QuickSettings.kt, SettingsDialog.kt, StyleSpotlight.kt | — | — | Update 409 (D105): the game popup chosen PER home layout, as "LAYOUT=STYLE" entries (STYLE = a `PopupStyle` name). A layout without an entry follows the old set |
| chanCorner | `chan_corner` | Bool | `true` | StyleConsoleHomes.kt, StyleHomeSettings.kt | LAYOUT-ONLY | — | Channel grid (update 104): profile, Settings, notifications and sync buttons in the top right corner. */ |
| circuitColors | `circuit_colors` | Enum | `CircuitColors.THEME` | StyleCircuit.kt, PlayerSkins.kt, GameWindow.kt | — | — |  |
| circuitCurrent | `circuit_current` | Bool | `true` | StyleCircuit.kt, StyleHomeSettings.kt, LayoutPresets.kt | — | — | Current flowing along the power line while you rest on a game. */ |
| circuitLabels | `circuit_labels` | Bool | `true` | StyleCircuit.kt, PresetPreview.kt, StyleHomeSettings.kt | — | — | The chips' silkscreen labels (U1, U2…). */ |
| circuitBackdrop | `circuit_backdrop` | Bool | `true` | StyleHomes.kt, StyleHomeSettings.kt, PresetPreview.kt | — | — | The faint traces of the board behind everything (off: your theme's background). */ |
| gcGlow | `gc_glow` | Int | `70` | GreenCoreSettings.kt, GreenCoreSphere.kt, LayoutPresets.kt | — | ui_glow, ribbon_glow, arcade_glow | Update 202: how strongly the sphere, the panels and the selection glow (0–100). */ |
| gcSphereSpeed | `gc_sphere_speed` | Int | `100` | GreenCoreSettings.kt, LayoutPresets.kt, StyleGreenCore.kt | — | — | Update 202: the sphere's turning and pulsing speed, in % (0 = still). */ |
| gcBgMotion | `gc_bg_motion` | Bool | `true` | GreenCoreSettings.kt, LayoutPresets.kt, StyleGreenCore.kt | — | — | Update 202: the slow drifting light in the background. */ |
| gcSounds | `gc_sounds` | Bool | `true` | GreenCoreSettings.kt, StyleGreenCore.kt, GreenCoreSounds.kt | — | pocket_sounds, ar_sounds, fs_sounds | Update 202: the layout's own synthesised interface sounds (click, whoosh, confirm); off = Voltcap's usual sounds. */ |
| gcVolume | `gc_volume` | Int | `60` | GreenCoreSettings.kt, StyleGreenCore.kt, GreenCoreSounds.kt | — | kit_volume, stage_volume | Update 202: their volume, in %. */ |
| gcHum | `gc_hum` | Bool | `false` | GreenCoreSettings.kt, StyleGreenCore.kt | — | — | Update 202: a low machine hum while Green Core is on screen (off by default). */ |
| gcReducedMotion | `gc_reduced_motion` | Bool | `false` | GreenCoreSettings.kt, LayoutPresets.kt, StyleGreenCore.kt | — | cube_reduced_motion, pq_reduced_motion | Update 202: reduced motion — the sphere stands still and the panels change without sliding. */ |
| gcBrowse | `gc_browse` | Enum | `GcBrowse.LIST` | GreenCoreSettings.kt, StyleGreenCore.kt, LayoutPresets.kt | — | — |  |
| gcAccent | `gc_accent` | Enum | `GcAccent.GREEN` | GreenCoreSettings.kt, LayoutPresets.kt, GreenCoreSphere.kt | — | ribbon_accent, ah_accent, cn_accent |  |
| cubeHome | `cube_home` | Bool | `true` | PresetPreview.kt, StyleCube.kt, StyleHomeSettings.kt | — | — | (Update 239, #93: start on the big cube — A opens a face's games, B comes back to it. Off: straight to the games with the cube docked.) */ |
| cubeDock | `cube_dock` | Enum | `CubeDock.LEFT` | PresetPreview.kt, StyleHomeSettings.kt, StyleCube.kt | — | — |  |
| cubeFace | `cube_face` | Enum | `CubeFace.LOGO` | StyleHomeSettings.kt, StyleCube.kt, LayoutPresets.kt | — | — |  |
| cubeTint | `cube_tint` | Enum | `CubeTint.INDIGO` | StyleHomeSettings.kt, WinSceneFamilies.kt, StatusLooks.kt | — | cross_tint |  |
| cubeIdle | `cube_idle` | Bool | `true` | CubeHub.kt, StyleCube.kt, StyleHomeSettings.kt | — | — | (Update 239, #93: a gentle float and sway while you rest on the big cube; it stops when the home screen idles.) */ |
| cubeExtraFaces | `cube_extra_faces` | Bool | `true` | StyleCube.kt, StyleHomeSettings.kt, LayoutPresets.kt | — | — | (Update 239, #93: the cube's top and bottom faces — ▲ Apps, ▼ Settings. Off: ▲ ▼ go past the cube.) */ |
| cubeSize | `cube_size` | Int | `100` | StyleHomeSettings.kt, CubeHub.kt, StyleCube.kt | — | pixel_size, mc_size, cf_size | (Update 239, #93: the big cube's size, in %.) */ |
| cubeBackdrop | `cube_backdrop` | Bool | `true` | StyleHomes.kt, PresetPreview.kt, StyleHomeSettings.kt | — | shelf_backdrop | (Update 239, #93: the dark indigo backdrop with a faint floor grid; off: your theme's background.) */ |
| cubeReducedMotion | `cube_reduced_motion` | Bool | `false` | CubeHub.kt, StyleCube.kt, StyleHomeSettings.kt | — | gc_reduced_motion, pq_reduced_motion | (Update 239, #93: reduced motion — the cube turns and docks without animating.) */ |
| cubeLook | `cube_look` | Enum | `CubeLook.HUB` | StyleHomeSettings.kt, StyleCube.kt | LAYOUT-ONLY | cart_look, second_look, picker_look |  |
| mcSize | `mc_size` | Enum | `McSize.MEDIUM` | StyleMemoryCard.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | pixel_size, cube_size, cf_size |  |
| mcSpacing | `mc_spacing` | Enum | `McSpacing.NORMAL` | StyleMemoryCard.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | tiles_spacing, stacks_spacing, row_spacing |  |
| mcArrange | `mc_arrange` | Enum | `McArrange.LOOSE` | StyleMemoryCard.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | pocket_arrange, box_arrange |  |
| mcDepth | `mc_depth` | Enum | `McDepth.SHALLOW` | StyleMemoryCard.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | spine_depth, stacks_depth |  |
| mcSelected | `mc_selected` | Enum | `McSelected.TURN` | StyleMemoryCard.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| mcBack | `mc_back` | Enum | `McBack.GREY` | StyleMemoryCard.kt, StatusLooks.kt, PresetPreview.kt | NO-SETTINGS-SCREEN | — |  |
| mcSaveIcons | `mc_save_icons` | Bool | `true` | StyleMemoryCard.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — | PS2 games show the 3D icon of their own save (read only from your memory cards) when there is one. */ |
| mcDbIcons | `mc_db_icons` | Bool | `true` | StyleMemoryCard.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | — | Update 330: PS2 games without a save here: their save icon from PS2IODB (fetched once, kept). */ |
| mcTitle | `mc_title` | Bool | `true` | StyleMemoryCard.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | dash_title, cf_title | The game's title at the top right (the save's own two-line title where it has one). */ |
| mcHints | `mc_hints` | Bool | `true` | StyleMemoryCard.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | spot_hints | The button hints along the bottom. */ |
| mcFloat | `mc_float` | Bool | `true` | StyleMemoryCard.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | bv_float | The icons float up and down a little (rests when the home screen goes quiet). */ |
| cfCurve | `cf_curve` | Int | `45` | StyleCaseFlow.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | ah_curve | ---- Update 330: Case flow — the games as real 3D cases on a curved carousel (StyleCaseFlow.kt) ---- How much the row of cases curves away (0 = straight). */ |
| cfSpacing | `cf_spacing` | Int | `100` | StyleCaseFlow.kt, R1Extras.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | tiles_spacing, stacks_spacing, row_spacing | Room between the cases, in % of the standard. */ |
| cdSpacing | `cd_spacing` | Int | `100` | StyleCoverDetails.kt, R1Extras.kt | NO-SETTINGS-SCREEN | tiles_spacing, stacks_spacing, row_spacing | Update 487 (K1): Cover details — room between the spines on its shelf, % (100 = packed as before). */ |
| cfSize | `cf_size` | Int | `100` | StyleCaseFlow.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | pixel_size, cube_size, mc_size | The cases' size, in % of the standard. */ |
| cfAngle | `cf_angle` | Int | `60` | StyleCaseFlow.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | model_angle | How far the cases either side turn, degrees. */ |
| cfPitch | `cf_pitch` | Int | `5` | StyleCaseFlow.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — | How much you look down on the cases (the camera's height), degrees. */ |
| cfSide | `cf_side` | Enum | `CfSide.FRONTS` | StyleCaseFlow.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| cfSpeed | `cf_speed` | Enum | `CfSpeed.NORMAL` | StyleCaseFlow.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | ar_speed, box_speed, pq_speed |  |
| cfReflect | `cf_reflect` | Bool | `true` | StyleCaseFlow.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | model_reflect | The cases' mirror images on a glossy floor. */ |
| cfStage | `cf_stage` | Bool | `true` | StyleCaseFlow.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — | A dark stage with a floor behind the cases (off: your theme's background). */ |
| cfTitle | `cf_title` | Bool | `true` | StyleCaseFlow.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | dash_title, mc_title | The game's name, console and time played under the cases. */ |
| boxArrange | `box_arrange` | Enum | `BoxArrange.NEAT` | StyleCardboardBox.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | pocket_arrange, mc_arrange |  |
| boxSpacing | `box_spacing` | Int | `100` | StyleCardboardBox.kt, R1Extras.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | tiles_spacing, stacks_spacing, row_spacing | Room between the games, in % of the standard. */ |
| boxLift | `box_lift` | Int | `100` | StyleCardboardBox.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — | How high the game you're on rises out of the box, in % of the standard. */ |
| boxTilt | `box_tilt` | Int | `8` | StyleCardboardBox.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | od_tilt, pq_tilt | Update 330 (top-down box): the camera's angle — 0 straight down into the box, more: tilted toward its back wall. */ |
| boxCardboard | `box_cardboard` | Enum | `BoxCardboard.KRAFT` | StyleCardboardBox.kt, StatusLooks.kt, PresetPreview.kt | NO-SETTINGS-SCREEN | — |  |
| boxSpeed | `box_speed` | Enum | `BoxSpeed.NORMAL` | StyleCardboardBox.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | ar_speed, cf_speed, pq_speed |  |
| boxCaseSize | `box_case_size` | Enum | `BoxCaseSize.MEDIUM` | StyleCardboardBox.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| boxLabel | `box_label` | Enum | `BoxLabel.STICKER` | StyleCardboardBox.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| boxWear | `box_wear` | Bool | `false` | StyleCardboardBox.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | — | Update 330: a strip of tape and a few scuffs on the box (off: a clean new box). */ |
| boxOrder | `box_order` | Enum | `BoxOrder.ORIGINAL` | StyleCardboardBox.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | — |  |
| boxDividers | `box_dividers` | Bool | `true` | StyleCardboardBox.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | — | Update 415: a thin cardboard divider between size groups that share a row (Grouped by size only). */ |
| boxSwitch | `box_switch` | Enum | `BoxSwitch.EDGE` | StyleCardboardBox.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | tile_switch |  |
| boxRows | `box_rows` | Enum | `BoxRowsPref.AUTO` | StyleCardboardBox.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | nm_rows, rs_rows |  |
| stSize | `st_size` | Enum | `StSize.COMPACT` | StyleStorageBoxes.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | pixel_size, cube_size, mc_size |  |
| stSpacing | `st_spacing` | Int | `100` | StyleStorageBoxes.kt, R1Extras.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | tiles_spacing, stacks_spacing, row_spacing | Room round each icon in its slot, in %. */ |
| stWallpaper | `st_wallpaper` | Enum | `StWallpaper.PER_BOX` | StyleStorageBoxes.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| stPreview | `st_preview` | Enum | `StPreview.LEFT` | StyleStorageBoxes.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | cn_preview |  |
| stCursor | `st_cursor` | Bool | `true` | StyleStorageBoxes.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — | The pointer over the icon you're on, and the icons' little hop. */ |
| stMotion | `st_motion` | Enum | `StMotion.FULL` | StyleStorageBoxes.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | lyrics_motion, disc_motion, cart_motion |  |
| fsPage | `fs_page` | Enum | `FsPage.FOUR` | StyleFileSelect.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | anim_page, live_page |  |
| fsSpacing | `fs_spacing` | Int | `100` | StyleFileSelect.kt, R1Extras.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | tiles_spacing, stacks_spacing, row_spacing | Room between the blocks, in %. */ |
| fsBounce | `fs_bounce` | Int | `100` | StyleFileSelect.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — | How much the block you're on bounces and tilts, in %. */ |
| fsColors | `fs_colors` | Enum | `FsColors.BRIGHT` | StyleFileSelect.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | layout_colors |  |
| fsSounds | `fs_sounds` | Bool | `false` | StyleFileSelect.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | pocket_sounds, ar_sounds, gc_sounds | Console-era blips when you move and pick. */ |
| fsPattern | `fs_pattern` | Bool | `true` | StyleFileSelect.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — | The moving pattern behind the blocks. */ |
| odRing | `od_ring` | Enum | `OdRing.NORMAL` | StyleOrbitDeck.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| odTilt | `od_tilt` | Int | `14` | StyleOrbitDeck.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | box_tilt, pq_tilt | How far the orbit is tipped toward you, degrees. */ |
| odSpin | `od_spin` | Bool | `true` | StyleOrbitDeck.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | disc_spin, crate_spin | The disc you're on spins slowly. */ |
| odStars | `od_stars` | Bool | `true` | StyleOrbitDeck.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | stage_stars | Twinkling stars behind (off: still). */ |
| odReal | `od_real` | Bool | `true` | StyleOrbitDeck.kt, StyleHomes.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | — | The games' own discs (ScreenScraper's photo of the disc, else its label scan) on the orbit, the cover only where there's none; a cartridge game shows its cartri |
| odColor | `od_color` | Enum | `OdColor.BLUE` | StyleOrbitDeck.kt, WinScenes.kt, StatusLooks.kt | NO-SETTINGS-SCREEN | dock_color, live_color, nm_color |  |
| ahSize | `ah_size` | Enum | `AhSize.MEDIUM` | StyleAiryHome.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | pixel_size, cube_size, mc_size |  |
| ahBubbles | `ah_bubbles` | Bool | `true` | StyleAiryHome.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| ahAccent | `ah_accent` | Enum | `AhAccent.ORANGE` | StyleAiryHome.kt, StatusLooks.kt, WinScenes.kt | NO-SETTINGS-SCREEN | ribbon_accent, gc_accent, cn_accent |  |
| ahCurve | `ah_curve` | Int | `30` | StyleAiryHome.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | cf_curve | How much the row bows (0 straight). */ |
| ahLcd | `ah_lcd` | Bool | `true` | StyleAiryHome.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — | The small pixel display top right showing the game you're on. */ |
| nmRows | `nm_rows` | Enum | `NmRows.SEVEN` | StyleNightMenu.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | box_rows, rs_rows |  |
| nmMoon | `nm_moon` | Bool | `true` | StyleNightMenu.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| nmWater | `nm_water` | Bool | `true` | StyleNightMenu.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| nmColor | `nm_color` | Enum | `NmColor.NIGHT` | StyleNightMenu.kt, StatusLooks.kt, WinScenes.kt | NO-SETTINGS-SCREEN | dock_color, live_color, od_color |  |
| kitVolume | `kit_volume` | Int | `70` | StyleBlades.kt, StyleMagazine.kt, StylePanels.kt | NO-SETTINGS-SCREEN | gc_volume, stage_volume | ---- Update 344: the console-inspired layouts' shared sound volume (LayoutKitSounds.kt) ---- |
| blColor | `bl_color` | Enum | `BlColor.GREEN` | StyleBlades.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | dock_color, live_color, od_color |  |
| blStart | `bl_start` | Enum | `BlStart.GAMES` | StyleBlades.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | bar_start, pd_start |  |
| blBadges | `bl_badges` | Bool | `true` | StyleBlades.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| blMotion | `bl_motion` | Bool | `true` | StyleBlades.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | lyrics_motion, disc_motion, cart_motion |  |
| blSounds | `bl_sounds` | Bool | `true` | StyleBlades.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | pocket_sounds, ar_sounds, gc_sounds |  |
| bvOpens | `bv_opens` | Enum | `BvOpens.BUBBLES` | StyleBubbles.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| bvWall | `bv_wall` | Enum | `BvWall.SKY` | StyleBubbles.kt, StyleHomes.kt, SceneOwnColors.kt | NO-SETTINGS-SCREEN | rs_wall, second_wall |  |
| bvFolders | `bv_folders` | Bool | `true` | StyleBubbles.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | cross_folders |  |
| bvFloat | `bv_float` | Bool | `true` | StyleBubbles.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | mc_float |  |
| bvSounds | `bv_sounds` | Bool | `true` | StyleBubbles.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | pocket_sounds, ar_sounds, gc_sounds |  |
| pcPalette | `pc_palette` | Enum | `PcPalette.GREEN` | StylePocketCamera.kt, WinScenesConsole.kt, StatusLooks.kt | NO-SETTINGS-SCREEN | chan_palette |  |
| pcShift | `pc_shift` | Enum | `PcShift.SHUTTER` | StylePocketCamera.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| pcGrid | `pc_grid` | Bool | `true` | StylePocketCamera.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | facts_grid |  |
| pcSounds | `pc_sounds` | Bool | `true` | StylePocketCamera.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | pocket_sounds, ar_sounds, gc_sounds |  |
| pdColor | `pd_color` | Enum | `PdColor.GREEN` | StylePanels.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | dock_color, live_color, od_color |  |
| pdStart | `pd_start` | Enum | `BlStart.GAMES` | StylePanels.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | bar_start, bl_start |  |
| pdProfile | `pd_profile` | Bool | `true` | StylePanels.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | dash_profile |  |
| pdSounds | `pd_sounds` | Bool | `true` | StylePanels.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | pocket_sounds, ar_sounds, gc_sounds |  |
| mgStyle | `mg_style` | Enum | `MgStyle.BOLD` | StyleMagazine.kt, WinScenesConsole.kt, StatusLooks.kt | NO-SETTINGS-SCREEN | play_style, header_style, disc_style |  |
| mgShot | `mg_shot` | Bool | `true` | StyleMagazine.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| mgDesk | `mg_desk` | Bool | `true` | StyleMagazine.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| mgSounds | `mg_sounds` | Bool | `true` | StyleMagazine.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | pocket_sounds, ar_sounds, gc_sounds |  |
| cnHero | `cn_hero` | Enum | `CnHero.BIG` | StyleCinema.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | dash_hero |  |
| cnSpacing | `cn_spacing` | Int | `100` | StyleCinema.kt, R1Extras.kt | NO-SETTINGS-SCREEN | tiles_spacing, stacks_spacing, row_spacing | Batch 520 (K1): Cinema — room between the tiles of a row, % of the standard 8 dp (100 = as before). */ |
| cnTiles | `cn_tiles` | Enum | `CnTiles.WIDE` | StyleCinema.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | grid_tiles |  |
| cnPreview | `cn_preview` | Bool | `true` | StyleCinema.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | st_preview | The gameplay clip plays in the billboard after you rest on a game (with Video previews on). */ |
| cnLogo | `cn_logo` | Bool | `true` | StyleCinema.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | spot_logo | The game's logo on the billboard instead of its name (when it has one). */ |
| cnRoll | `cn_roll` | Bool | `true` | StyleCinema.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | — | The billboard's story rolls up slowly when it's longer than its three lines. */ |
| cnAccent | `cn_accent` | Enum | `CnAccent.CINEMA` | StyleCinema.kt, WindowLook.kt, CategoryLooks462.kt | NO-SETTINGS-SCREEN | ribbon_accent, gc_accent, ah_accent |  |
| cnRowContinue | `cn_row_continue` | Bool | `true` | StyleCinema.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | ar_row_continue |  |
| cnRowFav | `cn_row_fav` | Bool | `true` | StyleCinema.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | ar_row_fav |  |
| cnRowMost | `cn_row_most` | Bool | `true` | StyleCinema.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | ar_row_most |  |
| cnRowNew | `cn_row_new` | Bool | `true` | StyleCinema.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | ar_row_new |  |
| arNav | `ar_nav` | Enum | `ArNav.FOLD` | StyleArctic.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | — |  |
| arArt | `ar_art` | Enum | `ArArt.FANART` | StyleArctic.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | era_art, mixed_art, live_art |  |
| arFirst | `ar_first` | Enum | `ArFirst.SPOTLIGHT` | StyleArctic.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | — |  |
| arClock | `ar_clock` | Bool | `true` | StyleArctic.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | twin_clock |  |
| arRowContinue | `ar_row_continue` | Bool | `true` | StyleArctic.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | cn_row_continue |  |
| arRowFav | `ar_row_fav` | Bool | `true` | StyleArctic.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | cn_row_fav |  |
| arRowMost | `ar_row_most` | Bool | `false` | StyleArctic.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | cn_row_most |  |
| arRowNew | `ar_row_new` | Bool | `true` | StyleArctic.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | cn_row_new |  |
| rsCases | `rs_cases` | Enum | `RsCases.CASES` | StyleRentalShop.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| rsRows | `rs_rows` | Enum | `RsRows.THREE` | StyleRentalShop.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | box_rows, nm_rows |  |
| rsWall | `rs_wall` | Enum | `RsWall.PURPLE` | StyleRentalShop.kt, SceneOwnColors.kt, WinScenesConsole.kt | NO-SETTINGS-SCREEN | bv_wall, second_wall |  |
| rsNeon | `rs_neon` | Bool | `true` | StyleRentalShop.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| rsSounds | `rs_sounds` | Bool | `true` | StyleRentalShop.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | pocket_sounds, ar_sounds, gc_sounds |  |
| tmTheme | `tm_theme` | Enum | `TmTheme.GREEN` | StyleTerminal.kt, WinScenesConsole.kt, StatusLooks.kt | NO-SETTINGS-SCREEN | pq_theme |  |
| tmCrt | `tm_crt` | Enum | `TmCrt.LIGHT` | StyleTerminal.kt, WinScenesConsole.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | ar_crt |  |
| tmPixel | `tm_pixel` | Bool | `false` | StyleTerminal.kt, WinScenesConsole.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| tmBoot | `tm_boot` | Bool | `true` | StyleTerminal.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| tmSounds | `tm_sounds` | Bool | `true` | StyleTerminal.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | pocket_sounds, ar_sounds, gc_sounds |  |
| barSquares | `bar_squares` | Bool | `true` | PresetPreview.kt, StyleHomeSettings.kt, GameLookPicker.kt | — | — | Icon bar: the tiles as squares (off: the card artwork's own shape). */ |
| avatarSource | `avatar_source` | Enum | `Avatar.RA` | SteamAchievementsUi.kt, LauncherViewModel.kt | NO-SETTINGS-SCREEN | cart_source |  |
| achView | `ach_view` | Enum | `AchView.POWER` | SettingsDialog.kt, AchievementListDialog.kt | — | ar_view, pb_view |  |
| achCommentsAuto | `ach_comments_auto` | Bool | `true` | AchievementListDialog.kt, SettingsDialog.kt | — | — | Update 151: a game's achievement list opens the details and comments of the achievement you rest on by itself (on by default; the Comments chip in the list or S |
| achHubView | `ach_hub_view` | Enum | `AchHubView.POWER` | SettingsDialog.kt, HubScreens.kt | — | — |  |
| platformView | `pb_view` | Enum | `PlatformView.TILES` | SettingsDialog.kt, LauncherScreen.kt, PlatformBrowserSettings.kt | — | ar_view, ach_view |  |
| pbFollowHome | `pb_follow_home` | Bool | `false` | SettingsDialog.kt, PlatformBrowserSettings.kt | SETTINGS-ONLY | — | Pass 4: the category screen's look follows the home layout of the category you open it from (followLookFor). */ |
| pbThemed | `pb_themed_pickers` | Bool | `true` | SettingsDialog.kt, PlatformBrowserSettings.kt, InputContract.kt | — | — | Update 462: Pocket Quest, Green Core, Pocket classic, Channel grid, Cinema and Arctic hub open their own themed category picker. */ |
| photoShells | `photo_shells` | Bool | `true` | SettingsDialog.kt, PhotoShells.kt | — | media_shells | Update 462: drawn Game Boy family paks made from a real cartridge photo in your library (ui/PhotoShells.kt). */ |
| pbBands | `pb_bands` | Bool | `true` | PlatformBrowserSettings.kt, PlatformBrowser.kt | — | — | Slides view: the grey skewed bands behind the cover. */ |
| pbShowLogo | `pb_show_logo` | Bool | `true` | PlatformBrowser.kt, PlatformBrowserSettings.kt | — | — | Every element of the platform slide can be switched off. */ |
| pbShowIcon | `pb_show_icon` | Bool | `false` | PlatformBrowser.kt | NO-SETTINGS-SCREEN | — |  |
| pbShowName | `pb_show_name` | Bool | `true` | PlatformBrowser.kt, PlatformBrowserSettings.kt | — | — |  |
| pbShowCovers | `pb_show_covers` | Bool | `true` | PlatformBrowser.kt, PlatformBrowserSettings.kt | — | — |  |
| pbShowLastPlayed | `pb_show_last_played` | Bool | `false` | PlatformBrowser.kt, PlatformBrowserSettings.kt | — | — |  |
| pbShowShadow | `pb_show_shadow` | Bool | `true` | PlatformBrowser.kt, PlatformBrowserSettings.kt | — | — |  |
| pbShowCount | `pb_show_count` | Bool | `true` | PlatformBrowser.kt, PlatformBrowserSettings.kt | — | — |  |
| pbShowMaker | `pb_show_maker` | Bool | `true` | PlatformBrowser.kt, PlatformBrowserSettings.kt | — | — |  |
| pbShowLegend | `pb_show_legend` | Bool | `true` | PlatformBrowserSettings.kt, PlatformBrowser.kt | — | — |  |
| pbTallCards | `pb_tall_cards` | Bool | `true` | PlatformBrowser.kt, PlatformBrowserSettings.kt | — | — | Grid view: tall 2:3 cards (off: wide cards). */ |
| pbAnimate | `pb_animate` | Bool | `true` | PlatformBrowser.kt, PlatformBrowserSettings.kt, SecondScreenTools.kt | — | — | Slides view animates when switching platform; Wall view animates in the direction you browse. */ |
| pbBarCount | `pb_bar_count` | Bool | `true` | PlatformBrowserSettings.kt, PlatformBrowser.kt | — | — | The game count and the random-game button in the platform views' action bar. */ |
| pbBarRandom | `pb_bar_random` | Bool | `true` | PlatformBrowser.kt, PlatformBrowserSettings.kt | — | — |  |
| pbSlideLayout | `pb_slide_layout` | String | `""` | PlatformBrowser.kt, PlatformBrowserSettings.kt, LauncherScreen.kt | — | — | The slide editor's layout: "ELEMENT=x,y,w,h;…" as fractions of the slide (empty = the built-in layout). */ |
| pbWallPlatformsLayout | `pb_wall_platforms` | String | `""` | SecondScreenTools.kt | NO-SETTINGS-SCREEN | — | The Wall view on the second screen: its own layouts for platforms and for games. */ |
| pbWallGamesLayout | `pb_wall_games` | String | `""` | SecondScreenTools.kt | NO-SETTINGS-SCREEN | — |  |
| platformMediaScrape | `platform_media_scrape` | Bool | `true` | PlatformBrowserSettings.kt, PlatformOptions.kt | SETTINGS-ONLY | — | ---- Platform media (data/PlatformInfo.kt) ---- Platform artwork (logo, photo, fan art) scraped from ScreenScraper through the Platform Options menu. */ |
| musicCrossfade | `music_crossfade` | Bool | `true` | BackgroundMusic.kt, PlatformBrowserSettings.kt, MusicOverrides.kt | — | — | ---- Music overrides (ui/BackgroundMusic.kt) ---- Platform and game music overrides fade into each other instead of cutting or overlapping. */ |
| musicRestartOnReturn | `music_restart_on_return` | Set | `emptySet(` | PlatformOptions.kt, LauncherScreen.kt | — | — | Per platform / per game: restart the override from the beginning when you come back to it. Keys: "p:<platform>" / "g:<stableKey>". */ |
| keepMusicScreenOff | `music_keep_screen_off` | Bool | `false` | SettingsDialog.kt, BackgroundMusic.kt | — | — | Update 202: Voltcap's background music plays on when the screen goes off or the screensaver fades to black (Settings › Music & sound › Background music). Off: i |
| platformIntroVideos | `platform_intro_videos` | Bool | `true` | PlatformBrowserSettings.kt, LauncherScreen.kt | — | — | ---- Platform intro videos (after the launch animation, before the game starts) ---- |
| introVideoOff | `intro_video_off` | Set | `emptySet(` | PlatformOptions.kt, LauncherScreen.kt | — | — | Platforms whose intro video is switched off. */ |
| detailLayerCover | `detail_layer_cover` | Bool | `true` | PlatformBrowserSettings.kt, HeroCarousel.kt, DetailLayers.kt | — | — | ---- Detail pane layers (list / grid detail panes) ---- |
| detailLayerScreenshots | `detail_layer_screenshots` | Bool | `true` | DetailLayers.kt, PlatformBrowserSettings.kt, HeroCarousel.kt | — | — |  |
| detailLayerVideo | `detail_layer_video` | Bool | `false` | PlatformBrowserSettings.kt, HeroCarousel.kt, LauncherScreen.kt | — | — |  |
| detailLayerLogo | `detail_layer_logo` | Bool | `true` | PlatformBrowserSettings.kt, HeroCarousel.kt, TvRoomHome.kt | — | — |  |
| factsRow | `facts_row` | Set | `GameFact.values(` | PlatformBrowserSettings.kt, RowInfoPanel.kt | — | — |  |
| factsList | `facts_list` | Set | `GameFact.values(` | PlatformBrowserSettings.kt, LauncherScreen.kt | — | — |  |
| factsGrid | `facts_grid` | Set | `GameFact.values(` | PlatformBrowserSettings.kt, TvRoomHome.kt | — | pc_grid |  |
| factsDetails | `facts_details` | Set | `GameFact.values(` | PlatformBrowserSettings.kt, GameDetailsOverlay.kt | — | — |  |
| factsSecond | `facts_second` | Set | `GameFact.values(` | PlatformBrowserSettings.kt, SecondScreenDetails.kt, SecondScreenVolt.kt | — | — |  |
| secondWidgets | `second_widgets` | Bool | `true` | AppearanceStore.kt, SecondScreenTools.kt | NO-SETTINGS-SCREEN | — | ---- Second screen: widget board, mirror, virtual second screen, wallpapers ---- The second screen's Widgets page and Wall page appear among its tabs. */ |
| secondWall | `second_wall` | Bool | `true` | AppearanceStore.kt, SecondScreenTools.kt | NO-SETTINGS-SCREEN | bv_wall, rs_wall |  |
| secondMagnifier | `second_magnifier` | Bool | `true` | AppearanceStore.kt, SecondScreenTools.kt, R1TileRules.kt | NO-SETTINGS-SCREEN | — | The second screen's Magnifier page: the main screen's picture enlarged (dual-screen devices). */ |
| magnifierZoom | `magnifier_zoom` | Int | `200` | SecondScreenMagnifier.kt | NO-SETTINGS-SCREEN | — | The Magnifier's zoom in percent (kept for the next time the page opens). */ |
| wallOfPlatforms | `wall_of_platforms` | Bool | `true` | SecondScreenTools.kt | NO-SETTINGS-SCREEN | — | The second screen's Wall shows platforms (off: the games of the category you are in). */ |
| widgetBoardItems | `widget_board_items` | String | `"builtin:NOW_PLAYING` | WidgetBoard.kt, SecondScreenTools.kt | NO-SETTINGS-SCREEN | — | The widgets on the second screen's Widget Board: "builtin:NOW_PLAYING", "builtin:SYSTEM_STATS", "app:<appWidgetId>". */ |
| widgetBoardSizes | `widget_board_sizes` | String | `""` | WidgetBoard.kt | NO-SETTINGS-SCREEN | — | Update 299: each Widget Board widget's size, "<item>=COMPACT/NORMAL/EXPANDED;…" (an item not listed is NORMAL), so the boards saved before keep their look and o |
| mirrorMode | `mirror_mode` | Enum | `MirrorMode.ZOOM` | ScreenMirror.kt, SecondScreenTools.kt | NO-SETTINGS-SCREEN | saver_mode, status_mode, ar_mode |  |
| virtualSecondScreen | `virtual_second_screen` | Bool | `false` | LauncherScreen.kt, SecondScreenTools.kt | NO-SETTINGS-SCREEN | — | Single-screen devices: a second-screen layout shown as a panel of its own (swipe in from the edge / hotkey). */ |
| virtualSecondScreenOpen | `virtual_second_screen_open` | Bool | `false` | SecondScreenTools.kt, LauncherScreen.kt | NO-SETTINGS-SCREEN | — | The virtual second screen is shown now. */ |
| secondScreenWallpaper | `second_screen_wallpaper` | String | `""` | SecondScreenTools.kt, LauncherScreen.kt | NO-SETTINGS-SCREEN | — | Separate wallpapers: "" = the same as the main screen, else the picture's file name. */ |
| heroTilesRows | `hero_tiles_rows` | Int | `2` | StyleHeroTiles.kt, LayoutPresets.kt, PresetPreview.kt | NO-SETTINGS-SCREEN | — | ---- Update 158: Hero tiles (StyleHeroTiles.kt, Settings › Home › Layout options) ---- Update 158, Hero tiles: rows of tiles under the big picture (1 or 2). */ |
| heroTilesSize | `hero_tiles_size` | Int | `90` | StyleHeroTiles.kt, LayoutPresets.kt, PresetPreview.kt | NO-SETTINGS-SCREEN | — | Update 158, Hero tiles: tile size, % of a row's height (smaller: more tiles across). */ |
| heroTilesSpacing | `hero_tiles_spacing` | Int | `100` | StyleHeroTiles.kt, R1Extras.kt | NO-SETTINGS-SCREEN | — | Batch 520 (K1): Hero tiles — room between the tiles, % of the standard gap (100 = as before; tile size stays its own). */ |
| heroTilesDock | `hero_tiles_dock` | Bool | `true` | StyleHeroTiles.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — | Update 158, Hero tiles: the app dock at the bottom in the middle. */ |
| heroTilesDockApps | `hero_tiles_dock_apps` | String | `""` | StyleHeroTiles.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | — | Update 158, Hero tiles: the dock's own apps (package names, comma separated); empty = Files, Browser, Store, System settings. */ |
| heroTilesPale | `hero_tiles_pale` | Bool | `true` | StyleHeroTiles.kt, PresetPreview.kt, StyleHomes.kt | NO-SETTINGS-SCREEN | — | Update 158, Hero tiles: a pale backdrop with white tile frames (off: the theme's background). */ |
| heroTilesStatusBar | `hero_tiles_status_bar` | Bool | `false` | StyleHeroTiles.kt, AppearanceStore.kt | NO-SETTINGS-SCREEN | — | Update 158, Hero tiles: the status bar along the top too (off: the picture's own status pill). */ |
| crateSpin | `crate_spin` | Bool | `true` | StyleRecordCrate.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | disc_spin, od_spin | ---- Update 158: Record crate (StyleRecordCrate.kt, Settings › Home › Layout options) ---- Update 158, Record crate: the record half out of the sleeve turns slo |
| crateWood | `crate_wood` | Enum | `CrateWood.LIGHT` | StyleRecordCrate.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| cardsFan | `cards_fan` | Int | `5` | StyleCardBinder.kt, LayoutPresets.kt, PresetPreview.kt | NO-SETTINGS-SCREEN | — | ---- Update 158: Card binder (StyleCardBinder.kt, Settings › Home › Layout options) ---- Update 158, Card binder: how far each card of the hand turns from the n |
| cardsSheen | `cards_sheen` | Bool | `true` | StyleCardBinder.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — | Update 158, Card binder: a rainbow sheen sweeps once across the card you land on (never with Lite effects). */ |
| cardsFrame | `cards_frame` | Enum | `CardFrame.CONSOLE` | StyleCardBinder.kt, PresetPreview.kt, LayoutPresets.kt | NO-SETTINGS-SCREEN | — |  |
| cardsAPlays | `cards_a_plays` | Bool | `false` | StyleCardBinder.kt | NO-SETTINGS-SCREEN LAYOUT-ONLY | pq_a_plays | Update 158, Card binder: A starts the game straight away (off: A opens the game's window, as on other layouts). Popup-first (owner, 12 Nov): off by default - A  |
| pqTheme | `pq_theme` | Enum | `PqTheme.ISLAND` | QuickSettings.kt, PocketQuestScenery.kt, PocketQuestSettings.kt | — | tm_theme |  |
| pqBackground | `pq_background` | Enum | `PqBackground.SCENERY` | PocketQuestSettings.kt, PocketQuestScenery.kt | — | — |  |
| pqBgAnim | `pq_bg_anim` | Bool | `true` | PocketQuestScenery.kt, PocketQuestSettings.kt | — | — | Update 202, Pocket Quest: the scenery moves (drifting clouds, motes, bubbles); off: a still picture. */ |
| pqParallax | `pq_parallax` | Int | `50` | PocketQuestSettings.kt, PocketQuestScenery.kt | — | — | Update 202, Pocket Quest: how far the scenery's layers shift as you move along the games, % (0 = none). */ |
| pqAccent | `pq_accent` | Enum | `PqAccent.THEME` | PocketQuestScenery.kt, PocketQuestSettings.kt | — | ribbon_accent, gc_accent, ah_accent |  |
| pqMedia | `pq_media2` | Enum | `PqMedia.CARTS` | R1TileRules.kt, PocketQuestSettings.kt, StylePocketQuest.kt | — | — | (Update 295, Toni: "use the cartridge look as default on Pocket Quest": Cartridges & discs, whatever look the other layouts use — a new key, so everyone gets it |
| pqDescription | `pq_description` | Bool | `true` | StylePocketQuest.kt, PocketQuestSettings.kt | — | — |  |
| pqScreenshots | `pq_screenshots` | Bool | `true` | StylePocketQuest.kt, PocketQuestSettings.kt | — | — |  |
| pqSize | `pq_size` | Enum | `PqSize.MEDIUM` | PocketQuestSettings.kt, StylePocketQuest.kt | — | pixel_size, cube_size, mc_size |  |
| pqSpeed | `pq_speed` | Enum | `PqSpeed.NORMAL` | PocketQuestSettings.kt, PocketQuestScenery.kt, StylePocketQuest.kt | — | ar_speed, cf_speed, box_speed |  |
| pqSounds | `pq_sounds` | Bool | `false` | PocketQuestSettings.kt | SETTINGS-ONLY | pocket_sounds, ar_sounds, gc_sounds | Update 202, Pocket Quest: its own synthesized square-wave blips for moving around the menu (off: Voltcap's sounds). */ |
| pqSoundVolume | `pq_sound_volume` | Int | `60` | PocketQuestSettings.kt | SETTINGS-ONLY | — |  |
| pqReducedMotion | `pq_reduced_motion` | Bool | `false` | PocketQuestScenery.kt, StylePocketQuest.kt, PocketQuestSettings.kt | — | gc_reduced_motion, cube_reduced_motion | Update 202, Pocket Quest: reduced motion — no tilt, parallax, drifting or launch flash; changes are quick fades. */ |
| pqTilt | `pq_tilt` | Bool | `true` | StylePocketQuest.kt, PocketQuestSettings.kt | — | box_tilt, od_tilt | Update 202, Pocket Quest: the big picture of the game you're on leans gently in 3D. */ |
| pqBumpers | `pq_bumpers2` | Bool | `false` | StylePocketQuest.kt, PocketQuestSettings.kt, InputContract.kt | — | — | Update 202, Pocket Quest: L1 / R1 switch the console (off: they do what they do everywhere else). Update 204: off by default — on, R1 couldn't open the quick se |
| pqAPlays | `pq_a_plays` | Bool | `false` | PocketQuestSettings.kt, StylePocketQuest.kt | — | cards_a_plays | Update 202, Pocket Quest: A starts the game (off: A opens the game window, as on the other layouts). Popup-first (owner, 12 Nov): off by default; same key, so a |
| quickMenuInGame | `quick_menu_in_game` | Bool | `true` | SettingsDialog.kt, OverlayControlActivity.kt, QuickMenuTileService.kt | — | — | ---- Quick Menu while in a game ---- |
| launchThrow | `launch_throw2` | Enum | `LaunchThrow.THROW` | SettingsDialog.kt, LaunchThrow.kt, LauncherScreen.kt | — | — |  |
| stageLowPower | `stage_low_power` | Bool | `false` | LyricsStage.kt | NO-SETTINGS-SCREEN | — |  |
| stageParticles | `stage_particles` | Bool | `true` | LyricsStage.kt | NO-SETTINGS-SCREEN | — |  |
| stageCircle | `stage_circle` | Bool | `true` | LyricsStage.kt | NO-SETTINGS-SCREEN | — |  |
| stageSpectrum | `stage_spectrum` | Bool | `false` | LyricsStage.kt | NO-SETTINGS-SCREEN | — |  |
| stageRings | `stage_rings` | Bool | `false` | LyricsStage.kt | NO-SETTINGS-SCREEN | — |  |
| stageAurora | `stage_aurora` | Bool | `false` | LyricsStage.kt | NO-SETTINGS-SCREEN | — |  |
| stageStars | `stage_stars` | Bool | `false` | LyricsStage.kt | NO-SETTINGS-SCREEN | od_stars |  |
| stageBokeh | `stage_bokeh` | Bool | `false` | LyricsStage.kt | NO-SETTINGS-SCREEN | — |  |
| stageLines | `stage_lines` | Bool | `false` | LyricsStage.kt | NO-SETTINGS-SCREEN | arcade_lines |  |
| stageFxReact | `stage_fx_react` | Bool | `true` | LyricsStage.kt | NO-SETTINGS-SCREEN | — |  |
| stageBgReact | `stage_bg_react` | Bool | `true` | LyricsStage.kt | NO-SETTINGS-SCREEN | — |  |
| stageCoverColors | `stage_cover_colors` | Bool | `true` | LyricsStage.kt | NO-SETTINGS-SCREEN | — |  |
| stageReaction | `stage_reaction` | Int | `150` | LyricsStage.kt | NO-SETTINGS-SCREEN | — | Update 200, Lyrics stage: how hard the effects and the background answer the bass, in %. */ |
| stageBgBlur | `stage_bg_blur` | Int | `12` | LyricsStage.kt | NO-SETTINGS-SCREEN | — | Update 200, Lyrics stage: blur of the cover behind everything (0 = sharp). */ |
| stageBgBright | `stage_bg_bright` | Int | `55` | LyricsStage.kt | NO-SETTINGS-SCREEN | — | Update 200, Lyrics stage: brightness of that background, in %. */ |
| stageParticleAmount | `stage_particle_amount` | Int | `90` | LyricsStage.kt | NO-SETTINGS-SCREEN | — |  |
| stageFps | `stage_fps` | Int | `30` | LyricsStage.kt | NO-SETTINGS-SCREEN | scene_fps | Update 200, Lyrics stage: the effects' frame rate cap. */ |
| stageIdleFx | `stage_idle_fx` | Enum | `StageIdleFx.OFF` | LyricsStage.kt | NO-SETTINGS-SCREEN | — |  |
| stageIdleAfter | `stage_idle_after` | Int | `30` | LyricsStage.kt | NO-SETTINGS-SCREEN | — | Update 200, Lyrics stage: seconds after a pause before the stage goes idle. */ |
| stageIdlePicture | `stage_idle_picture` | String | `""` | LyricsStage.kt | NO-SETTINGS-SCREEN | — | Update 200, Lyrics stage: the picture shown while idle (a content:// address), "" = none. */ |
| stageClock24 | `stage_clock_24` | Bool | `true` | LyricsStage.kt | NO-SETTINGS-SCREEN | — |  |
| stageSeconds | `stage_seconds` | Bool | `true` | LyricsStage.kt | NO-SETTINGS-SCREEN | — |  |
| stageUiScale | `stage_ui_scale` | Int | `100` | LyricsStage.kt | NO-SETTINGS-SCREEN | — | Update 200, Lyrics stage: interface size, in %. */ |
| stageCpu | `stage_cpu` | Bool | `true` | LyricsStage.kt | NO-SETTINGS-SCREEN | — |  |
| stageGpu | `stage_gpu` | Bool | `true` | LyricsStage.kt | NO-SETTINGS-SCREEN | — |  |
| stageRam | `stage_ram` | Bool | `true` | LyricsStage.kt | NO-SETTINGS-SCREEN | — |  |
| stageDrives | `stage_drives` | Bool | `true` | LyricsStage.kt | NO-SETTINGS-SCREEN | — |  |
| stageNet | `stage_net` | Bool | `true` | LyricsStage.kt | NO-SETTINGS-SCREEN | — |  |
| stageLyrics | `stage_lyrics` | Bool | `true` | LyricsStage.kt | NO-SETTINGS-SCREEN | — |  |
| stageVolume | `stage_volume` | Bool | `true` | LyricsStage.kt | NO-SETTINGS-SCREEN | gc_volume, kit_volume |  |
| stageLyricOffsetMs | `stage_lyric_offset_ms` | Int | `0` | LyricsStage.kt | NO-SETTINGS-SCREEN | — | Update 200, Lyrics stage: lyric timing for every song, in ms (+ = the words come earlier). */ |
| stageStatus | `stage_status` | Enum | `StageStatus.AUTO` | LyricsStage.kt | NO-SETTINGS-SCREEN | ribbon_status, dash_status |  |
