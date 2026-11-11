# Connected Ignition — implementation and QA log

Branch `feature/connected-ignition` (from tag v0.6.70; local only). FW1 untouched.

## PC tooling change (uncommitted on the PC's main tree, owner-approved scope)
`tools/adb_mcp/server.js` changed in one place only: the `WORKTREES` allow-list gains
`ci: { dir: <project>\Claude outputs\worktrees\ci, branch: 'feature/connected-ignition' }` next to `fw1`
(diff checked 11 Nov against v0.6.70's file: no other line differs).

It cannot weaken the production guards:
- Every install, from the main tree or any worktree, goes through `qaInstall()`, which runs `guard.targetProblems` (USB, not a TV, listed in `qa_devices.json`) and `guard.qaApkProblems` (manifest package must be `com.shelf.launcher.qa`; a release-key signer is refused). It also compares the real Voltcap's state before and after (`productionState`).
- `install_production` still accepts only pinned published APKs.
- `launch_app` still refuses the production package, and the `shell` / `push` filters are unchanged.
- The worktree entry only selects which folder Gradle builds in and where the APK is read from.

The change needs a restart of the voltcap-adb tool. It must not be committed and pushed to main without the owner's approval; it is kept as a patch here and in the backups.
