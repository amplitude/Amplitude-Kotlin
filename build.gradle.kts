import org.jetbrains.dokka.gradle.DokkaMultiModuleTask

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
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:1.9.20")
        classpath("org.jlleitschuh.gradle:ktlint-gradle:10.2.1")
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
val sonatypeStagingProfileId: String? =
    project.findProperty("SONATYPE_STAGING_PROFILE_ID") as? String
        ?: System.getenv("SONATYPE_STAGING_PROFILE_ID")
val sonatypeUsername: String? =
    project.findProperty("SONATYPE_USERNAME") as? String ?: System.getenv("SONATYPE_USERNAME")
val sonatypePassword: String? =
    project.findProperty("SONATYPE_PASSWORD") as? String ?: System.getenv("SONATYPE_PASSWORD")

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
