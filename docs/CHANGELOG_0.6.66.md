# Voltcap 0.6.66 (release candidate — not published)

Changes since 0.6.65 (updates 438–444). Only items verified on a device (AYN Odin 2 Portal, ARBOR GT78) or by tests are listed as fixed.

## Game windows (popups)
- A quick A / B / A / B can no longer start a game from a window that is opening or closing (Channel grid, Case stacks, Cross menu,
  Shared move). A quick A while a window closes opens it again; a B right after it closes it, ending on the same game.
- Twin desk / Twin icons / Twin showcase: the window over the hinge was rebuilt — the picture and the game's name on top, whole lines of
  facts and every button below; Twin desk uses its own light desk look. Nothing is cut in half on small screens.
- Channel grid with Row reveal: the name and buttons sit together in a panel under the game instead of over the other channels.
- Row opens up: wide cards (Cinema, Channel grid) keep the cover, text and buttons on screen.
- Row reveal / Shared move on whole-screen layouts: the layout's own title no longer shows through; long stories scroll instead of ending in "…".
- Banner: the selected round button shows its name; Cinema: bigger button captions on small screens.
- Hero carousel texts no longer end in "…"; a few games stand in the middle of the Cardboard box.

## Home screen
- Round shortcuts on whole-screen layouts (Disc station, Arctic, …) stay in the top band on every device, so they no longer cover the disc panel.
- Android 12 versions of the Manga beat, Magical, Blue sky and Pixel sky scenes in their own colours.
- Less work while the 3D game rows glide (focus glow drawn without rebuilding the row; animated scenes hold their frame during a glide).

## Known limitations
- GT78 (and similar low-end devices): about one dropped frame per step in Case stacks when moving between games (the home screen rebuilds when
  the selected game changes; D137, low). The gameplay-video background does not add to it.
- AutoTDP in real gameplay (D124) and battery savings: not validated on hardware.
- AYN Thor (dual screen): not tested for this release.
- Twin window for a game without any artwork: not seen on a device (code path only).
