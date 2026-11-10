# Batch 522: logo artwork for PS5, Xbox One, Xbox Series X|S, Switch 2 — sources for the owner's review

Nothing below is in the app. Until reviewed artwork is added, these four consoles show their short name (Text / Symbol /
Logo badges) in their family colour, and their folders show the name. No logo was drawn, traced or invented.

## Requirements for an asset to go in
- Authentic: the console maker's own mark (from official brand material / packaging), not a fan redraw that changes it.
- Same family as the shipped badge set (assets/badge_logos + badge_icons): Dan Patrick's "v2 Platform Logos
  (professionally redrawn official versions)", Light › Just White, SVG — so the new badges match the 47 others.
- Licence noted in assets/badge_logos/CREDITS.txt (the set's terms: credit the author, never sold; the marks belong to
  their owners).
- One-colour safe: readable as white on the console colour (badge_audit.py's fill-ratio check < 0.62, no plate behind it).

## Candidate sources (checked 10 Nov 2026)
| Console | Source | Status |
|---|---|---|
| PlayStation 5 | Dan Patrick v2.1 set (LaunchBox forum file 3402, v2.1.0, 6 Feb 2023): its list says "Sony PS1, 2, 3, 4, 5, VR, Vita, PSP (from vector manual)", vector art "ripped from official sources" | **Best source** — same family as the shipped set. The Just White SVG still has to be confirmed inside the download |
| Xbox Series X\|S | Same set: "Microsoft Xbox (one color icon), Game Pass, Series Logos (from Xbox website)" | Likely there (as "Series" logos); confirm the exact file in the download |
| Xbox One | Same set: no separate "Xbox One" entry in the published list (the list says it is not exhaustive) | Unconfirmed — check the download; otherwise BLOCKED-REF |
| Nintendo Switch 2 | Same set (2023) predates Switch 2 (2025): not in it | BLOCKED-REF — needs Nintendo's own brand / packaging artwork |
| any | Simple Icons (CC0 SVG paths of official marks): PlayStation 5 / Xbox / Switch entries not confirmed in this session | Unverified |
| any | Wikimedia Commons logo files | Not reachable from this session — unverified |

## Owner steps (the download is behind a LaunchBox forum login, ~9 GB)
1. Download Dan Patrick's v2.1 platform logos from the LaunchBox forums (file 3402).
2. From `SVG/Light/Just White/` copy the PlayStation 5, Xbox Series (and Xbox One if present) SVGs to
   `Claude outputs\b522\artwork_review\` on the PC — no renaming needed.
3. Claude then: checks they are the white set (no plate), measures each logo's width ÷ height, adds them as
   `badge_logos/PS5.svg`, `XboxSeries.svg` (+ `XboxOne.svg`) with ASPECT entries and CREDITS lines, renders them in the
   QA badge gallery (96 / 200 dp, Logo and Symbol looks) and shows you a before / after page before anything ships.
4. Switch 2: point to an official source you accept (Nintendo press kit / packaging photo) — nothing is made up meanwhile.
