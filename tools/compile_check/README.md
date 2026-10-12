# Compile check (for Claude's cloud session)

A real Kotlin 2.2.10 + Compose compiler type-check of `src_copy`, run in Claude's cloud workspace before shipping an update.

- `paths.txt`: the library jars / AARs used, relative to `C:\Users\User\.gradle\caches\modules-2\files-2.1\` (same versions the app builds with).
  Plus `Sdk\platforms\android-36\android.jar`, `app\libs\discord\lib\release\discord_partner_sdk.aar`, and
  `app\build\intermediates\compile_app_classes_jar\debug\bundleDebugClassesToCompileJar\classes.jar` (only the compiled
  `ThemeShaders` classes and `META-INF/app.kotlin_module` are taken from it, so ThemeShaders.kt is never read).
- `genR.py`: makes a stub `R` class from the `R.x.y` names the code uses.
- `check.sh`: runs kotlinc with the Compose plugin; prints the error count, errors go to `log.txt`.

Not covered: missing resource files, the manifest, lint, R8. Gradle on the PC is still the final word.
