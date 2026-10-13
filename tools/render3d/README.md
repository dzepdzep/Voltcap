# render3d — makes the "3D models" pack from the 3D shape library

Update 136, redone in 137 for the library's v6.8. Renders every `.glb` of the VoltCap 3D Shape Library from seven angles
and writes the pack Voltcap downloads (`model_<id>.zip` and `model_logos.zip`, see `data/Packs.kt` and `ui/Models3D.kt`).

- `render.html` + `render.js`: a small WebGL2 glTF renderer (no libraries): PBR-style light, the clear plastic
  (KHR_materials_transmission) as tinted see-through, 4× MSAA. Per pass it can set the shell (`molded_polymer`,
  `recess_polymer` = 0.48 × it, `translucent_tinted_polycarbonate`) and badge (`console_badge_polymer`) colours, hide
  nodes (`console_logo`, `console_name`) and draw the logo plate plain white.
- `drive.js`: serves the page and the library to a headless Chromium (Playwright). Paths at the top.
- `batch2.js` (137): for each model and angle (−60 … 60, every 20°, pitch 6°), all cropped to one box, longest side 600 px:
  - every model: `<yaw>.webp` (as the library has it, front art dark) and `<yaw>_g.webp` (the art window's light);
  - paintable ones (custom cartridges / cards, clear ones, the Save Vault's cards; not the real ones): the shell and badge
    in grey levels with no logo — `<yaw>_t0` / `_t50` (solid: the app undoes the tone curve and paints any colour exactly)
    or `_t0 / _t25 / _t50 / _t75 / _t100` (clear: blended between the nearest), `<yaw>_b` (where the badge is),
    `<yaw>_gw` (clear: the art window's light through white plastic), and in `meta.json` the logo plate's corners and light
    per angle, the model's own console, and for the Save Vault cards their own colours (`own`).
  Run two at once on different ports: `PORT=8771 node batch2.js <ids…>`.
- `logos.js`: the logos piece — each console's ES-DE logo (webp, ≤ 640 × 320) and `systems.json` (shell / badge colours).
- `comp.py` / `comp2.py`: put a cover in (and paint a piece, print its logo) the same way the app does, to check renders;
  `truth.js` renders a piece in a given colour directly, to compare.

Then zip each `pack68/<id>` folder as `model_<id>.zip` (the folder inside the zip) and `logos68/logos` as
`model_logos.zip`, into `packs/v1/`.

## Update 166: all the way round (`batch3.js`)

`batch3.js` renders the library's v6.11 (`all/VoltCap_v6_Refined/v6`) and, where there is one, its blank-branding
variant (`all/VoltCap_Blank_Branding_Variants`, no native lettering; `glbplan.json` says which file each id is made from)
from 18 angles, −160 … 180 every 20° (180 = the back), same files and crop as `batch2.js`:
- `<yaw>_g` / `<yaw>_gw` and the art window's corners (`angles` in `meta.json`) only where the front faces you (±80°);
  the logo plate's corners and light (`tint.logo` / `tint.ls`) likewise;
- `meta.json` v3 adds `yaws` (the angles);
- every piece with a card slot is paintable: the custom ones as before; the real ones and the Save Vault cards keep their
  own colours (`tint.own`), only the blank badge gets the console's logo;
- the logo plate's corners come from the `console_logo` node's own bounds (`render.js` `logoNode`); the blank badge
  (`blank_branding_label`) takes the badge colour like `console_badge_polymer`.
Run two at once: `PORT=8771 node batch3.js <ids…>` and `PORT=8772 …` (≈ 3–6 min a piece in software GL). Output in
`pack611/<id>`; zip each as `model_<id>.zip` (folder inside) and `logos68/logos` as `model_logos.zip` into `packs/v1/`.
The app reads them with the marker `<id>.v611` (data/Packs.kt).
