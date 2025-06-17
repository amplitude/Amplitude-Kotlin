plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "com.amplitude.buildsrc"
version = "1.0.0"

gradlePlugin {
    plugins {
        create("publishModulePlugin") {
            id = "com.amplitude.publish-module-plugin"
            displayName = "Amplitude Publish Module Plugin"
            description = "A Gradle plugin to simplify publishing Android libraries and modules to a Maven repository."
            implementationClass = "com.amplitude.PublishModulePlugin"
        }
    }
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:1.9.20")
    testImplementation(kotlin("test-junit5"))
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
