import org.jetbrains.dokka.gradle.DokkaMultiModuleTask
import java.io.FileInputStream
import java.util.Properties

buildscript {
    repositories {
        maven(url = "https://plugins.gradle.org/m2/")
        mavenCentral()
        google()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.10.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.25")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:2.0.0")
        classpath("org.jlleitschuh.gradle:ktlint-gradle:12.3.0")
        classpath("io.github.gradle-nexus:publish-plugin:2.0.0")
        classpath("de.mannodermaus.gradle.plugins:android-junit5:1.12.2.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://kotlin.bintray.com/kotlinx")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjvm-default=all")
            jvmTarget = "1.8"
        }
    }

    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "org.jetbrains.dokka")

    group = project.findProperty("PUBLISH_GROUP_ID") ?: ""
    version = project.findProperty("PUBLISH_VERSION") ?: "0.0.1-SNAPSHOT"
}

plugins {
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

// Load local properties for Sonatype credentials

// Load properties from local.properties
val localProperties = Properties()
val localPropertiesFile = project.rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    FileInputStream(localPropertiesFile).use { fis ->
        localProperties.load(fis)
    }
}

val sonatypeStagingProfileId: String? =
    project.findProperty("sonatypeStagingProfileId") as? String
        ?: localProperties.getProperty("sonatypeStagingProfileId")
        ?: System.getenv("SONATYPE_STAGING_PROFILE_ID")
val sonatypeUsername: String? =
    project.findProperty("ossrhUsername") as? String
        ?: localProperties.getProperty("ossrhUsername")
        ?: System.getenv("SONATYPE_USERNAME")
val sonatypePassword: String? =
    project.findProperty("ossrhPassword") as? String
        ?: localProperties.getProperty("ossrhPassword")
        ?: System.getenv("SONATYPE_PASSWORD")

nexusPublishing {
    repositories {
        sonatype {
            stagingProfileId.set(sonatypeStagingProfileId)
            username.set(sonatypeUsername)
            password.set(sonatypePassword)
        }
    }
}

tasks.named("dokkaHtmlMultiModule") {
    (this as DokkaMultiModuleTask).outputDirectory.set(file("$rootDir/docs"))
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}

tasks.register("printPublishCoordinates") {
    description = "Prints the GAV (GroupId:ArtifactId:Version) and project name for all publishable subprojects."
    group = "publishing" // Optional: assign to a task group

    doLast {
        val publishableProjects =
            subprojects.filter { proj ->
                // A project is considered publishable if it applies the 'maven-publish' plugin
                // and is not the 'buildSrc' project itself (as buildSrc usually has plugin publications not intended for this script).
                proj.plugins.hasPlugin("maven-publish") && proj.name != "buildSrc"
            }

        if (publishableProjects.isEmpty()) {
            println("INFO: No publishable subprojects found by the printPublishCoordinates task.")
        } else {
            publishableProjects.forEach { proj ->
                val publishing = proj.extensions.findByType(PublishingExtension::class.java)
                publishing?.publications?.filterIsInstance<MavenPublication>()?.forEach { publication ->
                    // Output format: groupId:artifactId:version:projectName
                    // This format is expected by the tools/scripts/verify_maven_local_publish.sh script.
                    // proj.name is the Gradle project name (e.g., "core", "android", etc...)
                    // publication.artifactId is the artifactId defined in the publication
                    if (publication.groupId != null && publication.artifactId != null && publication.version != null) {
                        println("${publication.groupId}:${publication.artifactId}:${publication.version}:${proj.name}")
                    } else {
                        println(
                            "WARN: Insufficient GAV information for publication '${publication.name}' in project '${proj.name}'. Skipping.",
                        )
                    }
                }
            }
        }
    }
}
