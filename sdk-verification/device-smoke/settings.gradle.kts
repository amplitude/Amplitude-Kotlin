// Standalone customer-shaped app. It resolves unified-android from Maven coordinates, not from
// the SDK build, so it sees the same POM, AAR, and consumer R8 rules a customer would.
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        // Override to check older consumer toolchains, e.g. -PkotlinVersion=2.0.21.
        id("org.jetbrains.kotlin.android") version providers.gradleProperty("kotlinVersion").getOrElse("2.2.20")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal {
            content { includeGroup("com.amplitude") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "unified-device-smoke"
include(":app")
