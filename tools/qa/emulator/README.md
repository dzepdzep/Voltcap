# Emulator QA trial (draft)

`.github/workflows/emulator-qa-trial.yml` + `run_trial.sh` + `grade_trial.py` — the first hosted Android-emulator smoke test of
the isolated QA app, requested in #16. **Emulator evidence only**: it never replaces the real-handheld checks (GPU / frame pacing,
OEM behaviour, physical controls, two-screen devices).

- Starts only by hand: `workflow_dispatch` (once this file is on the default branch) or a push to `claude/qa-0672-repair`
  that changes these files with `[emulator-qa]` in the head commit message.
- `contents: read`, checkout without stored credentials, no secrets, actions pinned to commit SHAs, 55-minute timeout,
  artifact kept 7 days.
- Builds `:app:assembleDebug` with `-PvoltcapCiWithoutDiscord=true` (the QA app `com.shelf.launcher.qa`, Discord SDK stub, debug
  key). The APK carries ARM native libraries only (`abiFilters` arm64-v8a / armeabi-v7a); the API 34 x86_64 Google APIs image
  runs them through its ARM translation — the run records whether install and launch work.
- Profile: `wm size 960x640`, `wm density 240`, system font scale 1.0; ×1.3 is the app's QA scale (`font=1.3,density=1.15`) as on
  the handhelds. The run records Android version, ABI list, native bridge, size, density, font and animator scale.
- Runs the existing handheld scripts unchanged (`tools/qa/device/pop529.sh`, `shelfpages529.sh` with `TOUCH=1`) on
  Coverflow details and Game shelf: ×1.3 and ×1.0 popup gate (open, More open / closed, Details page, touch, R1 / L1, rapid
  reopen), then the ×1.3 page walk (Details / Achievements / Options, recordings, Motion Off, touch then controller).
- Artifact: `summary.md`, `profile.txt`, launch screenshot and logs, popup-gate reports, contact sheets, timestamped frame
  strips, raw screenshots and layout dumps. A step that did not finish or a missing capture is NOT TESTED.
