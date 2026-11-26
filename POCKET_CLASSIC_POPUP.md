# Pocket Classic Game Popup (25 October 2026)

Staged source files are in `src_copy/ui/` (following CLAUDE.md).
Copy the three files into `app/src/main/java/com/shelf/launcher/ui/` using the standard src_copy robocopy workflow.

Changes:
- Pocket Classic's main game tile now calls `StyleActions.open()` instead of launching outright. That reuses the global Game window on/off preference and any configured classic-window override.
- `GameWindow.lookFor(HomeLayout.POCKET_CLASSIC)` now has its own paper-grey, pixel/monospace, sharp-border, accent-aware pocket UI. The shared popup still provides Play/Continue, Details, achievements, options, sync, Inspect, performance, controller navigation and touch.
- Pocket Classic settings help text updated to reflect the new A action.
- Existing Pocket Ribbon behaviour, other layouts and per-layout settings untouched.

IMPORTANT: The specialized look appears when Game window is enabled and its standard layout window is used. If the user explicitly chooses Banner/Shared-motion/Classic details window, their choice still takes precedence. For original pocket colours choose Game window > Colours > The layout's own.

Checks: static call wiring and exact source replacements verified. Android build, device rendering, controller and touch tests NOT run.

On local integration, prepend a SUMMARY.md update entry; this large historical log was intentionally left untouched in this staged GitHub branch.
