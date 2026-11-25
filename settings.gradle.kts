pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Mozilla GeckoView releases
        maven { url = uri("https://maven.mozilla.org/maven2/") }
        // Guardian Project tor-android releases
        maven { url = uri("https://raw.githubusercontent.com/guardianproject/gpmaven/master") }
    }
}

rootProject.name = "VeilPrivacyBrowser"
include(":app")
