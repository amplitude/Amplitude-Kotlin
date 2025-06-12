buildscript {
    val kotlinVersion by extra("1.9.25")
    val dokkaVersion by extra("1.9.20")
    repositories {
        maven(url = "https://plugins.gradle.org/m2/")
        mavenCentral()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.10.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:$dokkaVersion")
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
}

tasks.named<org.jetbrains.dokka.gradle.DokkaMultiModuleTask>("dokkaHtmlMultiModule") {
    outputDirectory.set(file("${'$'}rootDir/docs"))
}

apply(plugin = "io.github.gradle-nexus.publish-plugin")

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

apply(from = "${rootDir}/gradle/publish-root.gradle")

tasks.register("printProjectGroup") {
    doLast {
        println("Project group is: ${project.group}")
    }
}
