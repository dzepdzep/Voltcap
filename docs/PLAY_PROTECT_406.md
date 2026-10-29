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
