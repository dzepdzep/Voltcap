// Plugin versions match what Android Studio's current project template uses
// (gradle/libs.versions.toml): AGP 9.3.3 and Kotlin 2.2.10.
//
// AGP 9 compiles Kotlin by itself ("built-in Kotlin"), so the old
// org.jetbrains.kotlin.android plugin is gone. The Compose compiler is now the
// Kotlin compose plugin; its version must equal the Kotlin version.
plugins {
    id("com.android.application") version "9.3.3" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
