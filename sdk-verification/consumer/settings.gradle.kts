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
        providers.gradleProperty("candidateRepository").orNull?.let { repository ->
            exclusiveContent {
                forRepository { maven { url = uri(repository) } }
                filter {
                    includeModule("com.amplitude", "unified-android")
                    includeModule("com.amplitude", "analytics-android")
                    includeModule("com.amplitude", "analytics-core")
                }
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "unified-consumer"
include(":app")
