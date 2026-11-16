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
        providers.gradleProperty("unifiedCandidateRepository").orNull?.let { repository ->
            exclusiveContent {
                forRepository { maven { url = uri(repository) } }
                filter { includeModule("com.amplitude", "unified-android") }
            }
        }
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") { from(files("../../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "unified-published-regression"
