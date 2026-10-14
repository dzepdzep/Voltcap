import java.util.Properties

plugins {
    id("com.android.application")
    // Compose compiler (Kotlin itself is built into AGP 9, no kotlin-android plugin).
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing. Put a keystore.properties next to settings.gradle.kts (keep it and the
// .jks out of git!) with:
//   storeFile=voltcap-release.jks
//   storePassword=…
//   keyAlias=voltcap
//   keyPassword=…
// Without it, release builds are signed with this PC's debug key as before. Every APK you put
// on GitHub must be signed with the SAME key, or people can't update without uninstalling —
// and the debug key is different on every PC, so make the release key before the first release.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.shelf.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shelf.launcher"
        minSdk = 24
        // Android 16. Play needs 36 for new apps since 31 Aug 2026; GitHub builds just benefit.
        targetSdk = 36
        versionCode = 6
        versionName = "0.6.0"

        // Handheld CPUs only — also keeps Discord's native library small.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Small native bridge to the Discord Social SDK (Rich Presence), see src/main/cpp.
    // AGP 9.3 builds it with NDK 28 (Android Studio offers to install it), which lays the
    // library out for 16 KB memory pages. Discord's own library is already 16 KB aligned.
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    signingConfigs {
        val storeFile = keystoreProps.getProperty("storeFile")?.trim()?.trim('"')
        // (Update 144: only when the key file is really there — a wrong path in keystore.properties made every build
        // stop at validateSigningDebug; then this PC's debug key signs, with a warning.)
        val keyFile = storeFile?.let { rootProject.file(it) }
        if (keyFile != null && !keyFile.isFile) logger.warn("Voltcap: keystore.properties points to $keyFile, which doesn't exist - signing with the debug key.")
        if (keyFile != null && keyFile.isFile) {
            create("release") {
                this.storeFile = keyFile
                storePassword = keystoreProps.getProperty("storePassword")?.trim()
                keyAlias = keystoreProps.getProperty("keyAlias")?.trim()
                keyPassword = keystoreProps.getProperty("keyPassword")?.trim()
            }
        }
    }

    buildTypes {
        // Update 144: one key for every build. With keystore.properties (your release key) the debug builds Android
        // Studio installs are signed with it too, so they, the GitHub releases and the in-app updater all install over
        // each other. Without it, everything stays on this PC's debug key as before.
        getByName("debug") {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        release {
            // R8: strips the unused parts of the libraries (Material icons alone is tens of MB of
            // classes), optimises Compose and the rest, and drops unused resources. Voltcap's own
            // classes are kept whole (see src/main/keepRules/rules.keep) because settings are
            // saved with Gson, which reads field names at run time.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "src/main/keepRules/rules.keep"
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

composeCompiler {
    // Lists, maps and Voltcap's own data classes are never changed in place (state is copied),
    // so Compose may skip redrawing parts of the screen whose inputs didn't change.
    // (Kotlin 2's Compose compiler also has "strong skipping" switched on by default.)
    stabilityConfigurationFiles.add(project.layout.projectDirectory.file("compose_stability.conf"))
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.activity:activity-compose:1.10.1")
    // Installs the baseline profile (src/main/baseline-prof.txt + the Compose libraries' own) on
    // sideloaded installs too, so the UI code is compiled ahead of time instead of interpreted.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // No longer updated (last release 1.7.8) — pinned so newer BOMs can't drop it.
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // Image loading (grid covers + hero backgrounds from SteamGridDB)
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Console logos (ES-DE system-logos, SVG) on the blank CDs: Coil's SVG decoder (update 84).
    implementation("io.coil-kt:coil-svg:2.7.0")

    // Encrypts the saved API keys at rest (see SgdbKeyStore.kt / SecureStore.kt).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Discord Social SDK (Rich Presence: "Playing … on Voltcap"), downloaded
    // from discord.com/developers into app/libs/discord. Custom Tabs is what
    // it opens Discord's sign-in with.
    implementation(files("libs/discord/lib/release/discord_partner_sdk.aar"))
    implementation("androidx.browser:browser:1.8.0")

    // Networking. Retrofit 2.11 ships the R8 rules its suspend functions need.
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Google Drive sync (Settings › Sync): Play Services hands out the Drive
    // access token; the Drive calls themselves go through OkHttp above.
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    // Settings transfer by QR code: Play services' own scanner screen (no camera permission, no
    // camera code in Voltcap). Devices without it type the short code instead.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    // Background Google Drive sync every few hours (data/CloudSyncWorker.kt).
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // LZMA decoder for reading CHD disc images (RetroAchievements hashing).
    implementation("org.tukaani:xz:1.9")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
