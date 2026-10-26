VoltCap — Matching Cases and Spines

62 coordinated platform sets: 62 spines, 62 transparent front frames, and 62 opaque back-cover bases. Includes Steam and Android.

The fronts and backs use the spine pack's colors, branding and restrained print details. Front windows are genuinely transparent so VoltCap can insert each game's own artwork. Backs reserve a large plain area for real screenshots and text. No game titles, fixed rating badges, fake barcodes, seals, publisher claims, sample art or mockups are embedded.

What is included:
- packs/v1/: the original set of 60 redesigned spine WebPs.
- app/src/main/res/drawable-nodpi/: two spine resources, 62 front WebPs and 62 back WebPs.
- png/: lossless editable PNG copies of all artwork.
- editable/cases/: 124 SVG frame/back masters. Shapes are vector; logos are embedded raster images.
- spine_manifest.json: spine title bounds, ink, aliases and dimensions.
- case_manifest.json: front artwork bounds, back content bands, colors, aliases and image dimensions.
- integration/: Kotlin metadata entries to merge into the current code.
- IMPLEMENTATION_PROMPT.txt: complete integration instructions.
- CREDITS.txt: existing logo attribution and provenance.

All 41 existing custom front resource names and canvas dimensions are retained. Another 21 fronts fill gaps in the matching platform set. New back bases share their companion front canvas; they require the new metadata. Physical case geometry was not changed. New texture dimensions are not claims about real retail packaging sizes.

These are universal packaging-inspired custom cases, not exact reproductions of regional retail releases. Genuine front/back/spine scans and user-selected artwork should continue to take priority. Digital and emulator platforms use deliberately custom companion cases.

This ZIP is an integration asset pack, not an APK or a verified in-app import format. Copying images alone is insufficient: merge the supplied window/band metadata, update Steam/Android runtime generation, preserve real-case geometry and clear affected caches.

Validation: all texture files decode; all 62 fronts have fully transparent declared art windows; backs are opaque; PNG and lossless WebP visible pixels agree; SVGs parse; all 41 original front filenames and dimensions are covered. Flat artwork was visually reviewed. An Android build and on-device 3D fitting have not been performed. No GitHub changes were made.
