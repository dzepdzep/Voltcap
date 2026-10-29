# Play Protect "App blocked to protect your device" (update 406)

## Evidence
- Public v0.6.58 APK (GitHub) SHA-256 `84e2c31116838212f8152651e579e004e6df2f6628aa748dd901eb9a015d086f` is byte-identical to the local
  `assembleRelease` output. Package `com.shelf.launcher`, versionCode 65, minSdk 24, targetSdk 36, APK Signature Scheme v2 verifies (owner key).
- Final merged manifest: NO READ_SMS / RECEIVE_SMS, NO accessibility service (aapt permissions / xmltree: count 0).
- It DID declare `ShelfNotificationListener` with `BIND_NOTIFICATION_LISTENER_SERVICE`. Google's sideload fraud protection (Play Protect
  "enhanced fraud protection") blocks sideloaded installs of apps that can request SMS, notification-listener or accessibility access;
  the dialog text ("can request access to sensitive data ... identity theft or financial fraud") matches that block. This is the only item of that class in the APK.
- Other sensitive-but-not-blocking items stay (each has a visible feature): MANAGE_EXTERNAL_STORAGE (ROM scan), REQUEST_INSTALL_PACKAGES (in-app updater),
  SYSTEM_ALERT_WINDOW (FPS overlay), PACKAGE_USAGE_STATS (running games), RECORD_AUDIO (opt-in beat-reactive theme, Discord SDK), WRITE_SECURE_SETTINGS / DUMP (granted over the shell only).
- Not verified: the official guidance page could not be fetched in this session (tool permission denied); the cause is established from the manifest plus Google's published behaviour.

## Fix (update 406)
- Standard `release` / `debug` manifests no longer declare the listener. It moved to `app/src/notify/AndroidManifest.xml`, merged only into the
  optional build type `notify` (`gradlew assembleNotify` gives `Voltcap-<ver>-notify.apk`, same id and key, installs over the standard one).
- Standard build: notification / now-playing features stay off (`ShelfNotificationListener.isBuiltIn` false, `hasAccess` false), no permission prompt,
  the notifications panel explains the optional build. Nothing hidden, no signature or package change.
- Regression: `PlayProtect406Test` (main manifest has no listener / accessibility / SMS entries; notify manifest has the listener).

## Status: EXTERNAL REVIEW / NOT VERIFIED on a blocking device
Thor (Android 13): a shell install of the new standard APK succeeded and the app runs. That is NOT proof (shell installs skip the sideload check).

## Manual retest (affected handheld)
1. Copy `Voltcap-<ver>-release.apk` (standard) to the device and open it from the file manager or browser download (normal install).
2. Note: Android version, Play Protect on/off (Play Store > profile > Play Protect), installer app, exact dialog text.
3. PASS = install proceeds (an "unknown app" or "scan" prompt is not a block). FAIL = the same "App blocked" dialog: send a screenshot.
4. The `-notify.apk` is expected to be blocked wherever fraud protection is active; install it only where the standard one installs.

## Appeal (only if the standard APK is still blocked)
Play Protect appeal form (Google Play Console help, "Play Protect appeals"): package `com.shelf.launcher`, SHA-256 of the exact APK,
GitHub release URL, merged manifest (no SMS / accessibility / listener), `PRIVACY.md`, source link, device and dialog screenshot.

## Update 407: notification features, feature comparison and decision

| Feature | Standard APK (0.6.59) | Optional notify APK | Why |
|---|---|---|---|
| VoltCap's own notifications (save sync, achievements, recap) | kept | kept | `POST_NOTIFICATIONS`, normal API |
| VoltCap's own music player, background music, UI sounds | kept | kept | own audio |
| Spotify now-playing / control | kept where logged in via `spotify/` (Web API) | kept | no listener needed |
| Background music waits while another app plays | kept | kept | `BackgroundMusic.otherMusicActive()` (AudioManager) + quiet window; the listener check only adds precision |
| Notifications list from OTHER apps (R1) | LOST | kept | only `NotificationListenerService` can read them |
| Now-playing title / art / progress / controls of OTHER players | LOST | kept | `MediaSessionManager.getActiveSessions` requires an enabled listener (or a signature permission) |
| Lyrics / second-screen / over-game mini player for other apps' songs | LOST (own / Spotify-API songs stay) | kept | same reason |
| Lid pause for music apps | partial (no per-app session) | kept | `LidPause` uses the sessions |

Normal-API alternatives checked: `AudioManager.isMusicActive()` / playback callbacks only say that audio plays (no title, art or controls), legacy `metachanged` broadcasts are not sent by modern players, `MEDIA_CONTENT_CONTROL` is signature-only. So there is no supported way to restore the lost rows without the listener.

Companion app: NOT built. A separate listener APK is the same capability class (Play Protect's fraud protection treats it the same way, so the companion itself is likely blocked where the setting is on), needs its own install, a signature-protected IPC to hand other apps' notification text to Voltcap (a larger privacy surface), and gives nothing the existing optional `notify` build type does not (same package and key, installs over the standard one). Decision: keep the `notify` build as the opt-in route, standard APK unchanged. Play Protect acceptance of the notify APK is NOT verified.

UI (update 407): every screen that said "Needs notification access" (Music player toggle, Music over games, Lyrics status line) now says, in the standard build, that other apps' music needs the optional notifications build (`ShelfNotificationListener.requirementNote`); the notifications panel already explained it. Test: `PlayProtect406Test` (bare sentence banned, `anyBusy` stays behind the access check).
