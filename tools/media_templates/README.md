# Media templates (update 43)

Builds Voltcap's real cartridges / discs / 3D boxes from the template pack in `C:\Users\User\Downloads\Templates`.

1. `mapping.py`: which file of the pack each console uses (cart, cart with label frame, disc, 3D box).
2. `analyze2.py`: finds each cart's label window from its alpha (largest see-through area inside the shell) → `windows.json` + `masks/`.
3. `box3d.py`: finds each 3D box's outline corners → `box_raw.json`.
4. `build.py`: writes `res/drawable-nodpi/tpl_*.webp` (shells, window masks, disc overlays, box overlays, spine strips and front
   frames cut from the cover textures) and `table.json`.
5. `gen_kt.py`: writes `ui/MediaTemplates.kt` from `table.json`.
6. `preview.py <consoles> box|cart|label`: a contact sheet of the results with a test cover.

Paths at the top of the scripts point at the pack (B=) and the output folders; change them to run on Windows.
