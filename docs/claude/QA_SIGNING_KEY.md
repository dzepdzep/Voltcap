# QA signing key (owner decision 3A, 16 Nov 2026)

One stable key signs every build of the isolated QA app `com.shelf.launcher.qa`, so a new QA build always updates the installed
QA app in place (its settings, fixture library and test data kept) and a Google OAuth client registered for it keeps matching.
The key is **never committed** (`*.keystore` / `*.jks` are git-ignored) and **never printed** (tools/cloud/qa_key.sh writes it
with mode 600 and only ever shows the first 8 characters of the public certificate fingerprint).

| | |
|---|---|
| Certificate SHA-256 (pinned, public) | `4d133dde91d791392b59885d3d522fe906cfda3275f9e2cf9076331288150055` (tools/qa/qa_signing.json) |
| Certificate SHA-1 (for a Google OAuth Android client) | `16062d37e11160c3f70d1ebce3b43344f9c69b7b` |
| Alias / store password | `androiddebugkey` / Android's standard debug password (it is a debug key: protection = keeping the file private) |
| Valid | 16 Nov 2026 - 8 Nov 2056 |
| First used | QA build 8bd5e4a; installed on the RP Nova d3641c41 |

## Where it is kept (three places, all private)
1. **Owner's laptop (msi):** `C:\VoltcapQA\keys\voltcap-qa-debug.keystore` + `voltcap-qa-debug.keystore.b64.txt` (the same key as
   base64). Copy both to the home PC and a private backup (password manager / encrypted drive); they are the recovery copy.
2. **Claude cloud environment** (owner, once): the environment's settings > Environment variables >
   `VOLTCAP_QA_DEBUG_KEYSTORE_B64` = the whole contents of the .b64.txt file. `tools/cloud/setup_android.sh` then restores it
   in every new cloud session and checks the fingerprint.
3. **GitHub Actions repository secret** (owner, once - the session's proxy can't set secrets): Settings > Secrets and
   variables > Actions > New repository secret `VOLTCAP_QA_DEBUG_KEYSTORE_B64`. No workflow uses it today (the emulator
   trial installs a fresh QA app, any key works). A workflow that ever uses it must run only on `push` / `workflow_dispatch`
   (never `pull_request` / `pull_request_target`, so no fork can read it), write it with mode 600, and never echo it.

## Checks
- `bash tools/cloud/qa_key.sh check` - before building a QA APK meant for a device. "WRONG KEY" = do not install that APK.
- The PC tools' install_apk prints the installed signer; it must start `4d133dde`.

## Recovery / rotation
- Lost cloud copy: restore from (1) into (2). Lost everything: a new key means one uninstall of the QA app on every handheld
  (its test data only - never the owner's Voltcap), a new pin in tools/qa/qa_signing.json, and a new OAuth client.
- The PC's own debug key (Android Studio, `%USERPROFILE%\.android\debug.keystore`) is a different key: QA APKs built on the
  PC will not update a QA app installed from the cloud builds. Build QA APKs for devices with this key only.
