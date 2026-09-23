pluginManagement {

    /**
     * The pluginManagement.repositories block configures the
     * repositories Gradle uses to search or download the Gradle plugins and
     * their transitive dependencies. Gradle pre-configures support for remote
     * repositories such as JCenter, Maven Central, and Ivy. You can also use
     * local repositories or define your own remote repositories. Here we
     * define the Gradle Plugin Portal, Google's Maven repository,
     * and the Maven Central Repository as the repositories Gradle should use to look for its
     * dependencies.
     */

    repositories {
        mavenLocal()
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {

    /**
     * The dependencyResolutionManagement.repositories
     * block is where you configure the repositories and dependencies used by
     * all modules in your project, such as libraries that you are using to
     * create your application. However, you should configure module-specific
     * dependencies in each module-level build.gradle file. For new projects,
     * Android Studio includes Google's Maven repository and the Maven Central
     * Repository by default, but it does not configure any dependencies (unless
     * you select a template that requires some).
     */

    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }

    versionCatalogs {
        create("unifiedLibs") {
            from(files("unified/gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "kotlin-sdk"
include("analytics-core")
project(":analytics-core").projectDir = file("analytics-core")
include("android")
project(":android").projectDir = file("android")
include("unified")
project(":unified").projectDir = file("unified")
include("streaming-analytics-android")
project(":streaming-analytics-android").projectDir = file("streaming-analytics-android")
include("samples:kotlin-android-app")
project(":samples:kotlin-android-app").projectDir = file("samples/kotlin-android-app")
include("samples:streaming-app")
project(":samples:streaming-app").projectDir = file("samples/streaming-analytics-android")

fun includePrototypeBuild(
    propertyName: String,
    buildName: String,
    projectPath: String,
    coordinate: String,
) {
    providers.gradleProperty(propertyName).orNull?.let { buildPath ->
        includeBuild(buildPath) {
            name = buildName
            dependencySubstitution {
                substitute(module(coordinate)).using(project(projectPath))
            }
        }
    }
}

includePrototypeBuild(
    propertyName = "amplitude.session.replay.sdk.path",
    buildName = "amplitude-session-replay-sdk",
    projectPath = ":plugin-session-replay",
    coordinate = "com.amplitude:plugin-session-replay-android",
)
