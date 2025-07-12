buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.10.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
        classpath("org.jlleitschuh.gradle:ktlint-gradle:12.3.0")
        classpath("io.github.gradle-nexus:publish-plugin:2.0.0")
        classpath("de.mannodermaus.gradle.plugins:android-junit5:1.13.1.0")
    }
}

plugins {
    alias(libs.plugins.mavenPublish) apply false
    id("org.jetbrains.dokka") version "2.0.0"
}

allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjvm-default=all")
            jvmTarget = KotlinConfig.JVM_TARGET
        }
    }

    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    group = project.findProperty("GROUP") ?: ""
    version = project.findProperty("VERSION_NAME") ?: "0.0.1-SNAPSHOT"
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")
}
