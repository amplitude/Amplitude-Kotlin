buildscript {
    extra["kotlin_version"] = "1.9.25"
    extra["dokka_version"] = "1.9.20"
    repositories {
        maven { url = uri("https://plugins.gradle.org/m2/") }
        mavenCentral()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.10.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${extra["kotlin_version"]}")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:${extra["dokka_version"]}")
        classpath("org.jlleitschuh.gradle:ktlint-gradle:10.2.1")
        classpath("io.github.gradle-nexus:publish-plugin:2.0.0")
        classpath("de.mannodermaus.gradle.plugins:android-junit5:1.12.2.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://kotlin.bintray.com/kotlinx") }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjvm-default=all")
            jvmTarget = "1.8"
        }
    }

    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "org.jetbrains.dokka")

    group = project.property("PUBLISH_GROUP_ID") as String
}

tasks.dokkaHtmlMultiModule.configure {
    outputDirectory.set(File(rootDir, "docs"))
}

apply(plugin = "io.github.gradle-nexus.publish-plugin")

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}

apply(from = "${'$'}{rootDir}/gradle/publish-root.gradle.kts")
