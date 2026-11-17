# Per-item "Open on screen" shortcut (owner request 16 Nov 2026) - target v0.6.74, not v0.6.73

## Audit (16 Nov, code read only)
Settings that already exist - reuse, never duplicate:
- Apps: `data/AppDisplayStore.kt` - per app (`app:<pkg>`) > category (`cat:emulator`) > global; targets WHERE_LAUNCHED / MAIN /
  SECOND; `LauncherViewModel.effectiveAppDisplay(game)` returns (target, source) = inherited vs overridden; `setAppDisplay` /
  `resetAppDisplay`.
- Games: `data/ScreenLayouts.kt` `ScreenLayoutsStore` rules game > collection > console > emulator > default, per setup (DUAL / TV);
  `viewModel.screenPlanFor(game)` (resolved layout + scope), `setScreenRule(RuleScope.GAME, key, layoutId?)`. Built-ins:
  `info` (game on main + info), `flipped-info` (game on second + info), `native` (DS / 3DS draw both).
- UI that already shows it: `ui/GameProfileDialog.kt` Display section (apps: "Opens on" chips; games: "Screens…" > ScreenLayoutsUi).
Menus to extend (each keeps its own look):
- All Apps drawer `ui/AppDrawer.kt` `AppOptionsMenu` (long-press + START on a DrawerTile).
- Second-screen dock `ui/SecondScreenHome.kt` `DockAppMenu` (long-press / pad menu); `ui/SecondShowcase.kt` onMenu.
- Home layouts: START / Menu on a game -> `runDetailsAction(DetailsAction.OPTIONS)` (LauncherScreen 7285) - the game's options;
  Card binder long-press (`StyleCardBinder` onHold); per-layout START handlers (PocketRibbon, PocketQuest quickMenu, PlatformBrowser).
- Game details / popups: the options page (GameProfileDialog).

## Plan
1. One shared row `ScreenTargetRow(item)` (+ sheet `ScreenTargetPicker`) used by every menu above - one source of truth, no new prefs:
   - apps -> AppDisplayStore (Top / Bottom / Follow default = clear the app's own choice); subtitle "Set for this app: Top" or
     "Follows the default (Emulators: Where launched)".
   - games -> a GAME-scope rule: Top = `info`, Bottom = `flipped-info`, Follow default = remove the rule; "Screen layouts…" opens
     ScreenLayoutsUi for collection / console / emulator rules; a DS / 3DS game shows the native layout and doesn't offer Top / Bottom.
2. Screen names: Thor (built-in DUAL, two physical panels) "Top" / "Bottom"; other DUAL "Main" / "Second"; TV setup "Handheld" / "TV";
   one screen: the row reads "One screen - opens here" and is disabled-with-reason, never hidden silently.
3. Opening / changing never launches: the row consumes A; START / long-press open the menu with the item focused, never Play.
4. Tests: unit (mapping, labels per device, inherited vs overridden), static check that every listed menu calls ScreenTargetRow, QA app
   on the Nova (single-screen regression) and the Thor (Top / Bottom for an app, an emulator, an Android game, a ROM game) - L1 only.
