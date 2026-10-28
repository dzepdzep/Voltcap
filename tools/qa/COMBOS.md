# VoltCap QA - high-risk combination matrix

Bugs in VoltCap come from two systems meeting (a category list built in four places, an animation over a changing selection), not from a single feature.
This matrix lists those meetings, with an initial state, a realistic input sequence, an expected result, the runner and an honest status.
The table with every row and its current status is in `COVERAGE.md` ("High-risk combination matrix", generated from `combos.json`; CI fails when it is stale).

## How a row runs
| runner | where | what |
|---|---|---|
| `unit:File#test` | Gradle `:app:testDebugUnitTest` (local; not in CI) | deterministic JVM fixture; `coverage.py check` fails when the named test does not exist |
| `device:combos.sh LAYOUT SCENARIO` | Deep QA on the Thor, never in CI | input through the real gamepad node (`vqa.sh`), session-only layout, settings snapshot restored, hooks off afterwards |
| `none` | - | not automated; always NOT TESTED |

Quick, deterministic checks that run in GitHub CI (`qa.yml`): the self-tests in `selftest/test_qa.py` (`TestCombos`: `combocheck.py` rejects a stop the category screen does not list, a different stop total,
a restart / crash / lost focus, and missing evidence as INFRA; `coverage.py` rejects a PASS without evidence or without an existing test) and `coverage.py check`.
The Kotlin list tests (`CategoryStopsTest`) run in Gradle, not in CI (no Android SDK on the runner).

## The device verdict (`tools/combocheck.py`)
`combos.sh RUN LAYOUT SCENARIO` writes `out/RUN_LAYOUT_SCENARIO.txt` (the `VoltcapQA` log: the category screen's `tiles N: keys` line, every L2 / R2 `stop KEY i/N`, and a `COMBO pid_before pid_after crashes focus` line)
plus `_a.png` / `_b.png` stills. `combocheck.py LOG [--need-tiles] [--need-stops N]` exits 0 PASS / 1 FAIL / 2 INFRA:
the process survived and Voltcap is in front; every stop reached is a tile on the category screen and the stop total equals the tile count (one list, update 367); a run that did not navigate or finished without its tail is INFRA.
It does **not** judge visual quality (jumping text, stale artwork): that needs a recording and `frames.py` / a human look, and a row says so in its limits.
Scenarios: `parity`, `rapidcat` (30 x R2 L2 at 60 ms), `screenmash` (8 x open / close the category screen), `navcat` (selection movement while the category changes).
The app writes the two category lines only with the test hooks on (`QaTrace.event`, no cost otherwise).

## Priorities (risk x user impact)
1. Category navigation (group 3): the user-reported bug. Lists: unit-tested (7 configurations + the empty collection, update 368). Device: parity + mash on four layouts.
2. Cinema / Arctic (group 2) and Tile home (group 1): category switching while animations run - structural verdict only; visual verdict by recordings (done in 0.6.52 for the real library).
3. R1 / controller (group 4), second screen (5), artwork + popups (6), settings + profiles (7): NOT TESTED, see "next".

## Recommended next combinations
- A fixture library: games with no cover, no metadata, a 300-character title, a wide / tall / square cover, a collection with 0 / 1 / 500 games (Settings snapshot + a separate test profile; never the user's own).
- `combos.sh` scenario `popup`: open the game popup while the category changes; check `QaTrace` focus lines return to the card.
- R1 panel walk per layout family (`walk.sh` + `walkcheck.py --strict`), recording which options appear per layout.
- Second screen: read the bottom display with `screencap -d <id>` before / after R2 and compare the category label (needs the display id of the Thor's second screen).
- Settings: change layout in Settings, force-stop, restart, assert the same category / game via the `focus` log line.
- Per-console overrides (`mixcon.sh`) for 3+ consoles at once, with an assertion on the tile caption.
