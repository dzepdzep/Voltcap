pluginManagement {
    repositories {
        google()
        System.getenv("VOLTCAP_MAVEN_MIRROR")?.trim()?.takeIf { it.startsWith("https://") }?.let { maven(it) }
        mavenCentral()
        gradlePluginPortal()
    }
}
// (706a: CI / cloud only - Maven Central answered 429 to every attempt of both Verify runners on 503d679. When
// VOLTCAP_MAVEN_MIRROR is set (the workflows and the cloud runs set it to Google's Maven Central mirror) that mirror is asked
// first; an artifact it lacks (404) still comes from mavenCentral(). Unset (Android Studio on the owner's PC): unchanged.)
val voltcapMavenMirror: String? = System.getenv("VOLTCAP_MAVEN_MIRROR")?.trim()?.takeIf { it.startsWith("https://") }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        voltcapMavenMirror?.let { maven(it) }
        mavenCentral()
    }
}

rootProject.name = "Voltcap"
include(":app")
