# Releasing Voltcap on GitHub

## 1. One-time: a release key (do this before the first public APK)

Every APK you publish must be signed with the **same** key, or people can't update without
uninstalling. Your current builds use this PC's debug key, which is different on every PC.

```
keytool -genkeypair -v -keystore voltcap-release.jks -alias voltcap -keyalg RSA -keysize 4096 -validity 10000
```

Put `voltcap-release.jks` next to `settings.gradle.kts` and create `keystore.properties` there:

```
storeFile=voltcap-release.jks
storePassword=…
keyAlias=voltcap
keyPassword=…
```

Both are in `.gitignore`. **Back them up somewhere safe** (losing the key = no more updates).
`app/build.gradle.kts` uses them automatically for release builds.

## 2. Google Drive sync for everyone (not just your account)

In Google Cloud Console, in Voltcap's project:

1. **Google Auth Platform › Audience › Publish app** (status "In production"). In "Testing" only the
   listed test users can connect. Voltcap only asks for `drive.file`, a non-sensitive scope, so no
   security review is needed.
2. **Branding**: app name, support email, home page and privacy policy links (a GitHub page is fine).
3. **Clients**: one *Android* OAuth client per signing key, all with package `com.shelf.launcher`:
   - the release key from step 1 (the APKs on GitHub),
   - your debug key (your own test builds).
   Get each SHA-1 with `gradlew signingReport` (or `keytool -list -v -keystore voltcap-release.jks`).

If someone sees "This copy of Voltcap isn't registered with Google (error 10)" in Settings › Sync,
their APK was signed with a key that isn't in step 3.

## 3. Android developer verification

From **30 Sep 2026** (Brazil, Indonesia, Singapore, Thailand; worldwide in 2027) certified Android
devices only install apps from verified developers. Register yourself, the package name
`com.shelf.launcher` and the release key's certificate in the Android Developer Console (free
"limited distribution" accounts cover up to 20 devices for testers). Unregistered APKs can still be
installed with `adb` or the system's advanced install flow.

## 4. Each release

1. Raise `versionCode` (and `versionName`) in `app/build.gradle.kts`.
2. `gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.
3. Keep `app/build/outputs/mapping/release/mapping.txt` with the release (crash stack traces).
4. Install it on a real device over the previous version (checks the key) and run the
   pre-release checklist.
5. GitHub › Releases › Draft: tag `v0.x.y`, attach the APK, paste the release notes.
