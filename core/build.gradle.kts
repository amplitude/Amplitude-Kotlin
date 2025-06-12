plugins {
    java
    kotlin("jvm")
}

extra.apply {
    set("PUBLISH_NAME", "Amplitude Kotlin Core")
    set("PUBLISH_DESCRIPTION", "Amplitude Kotlin Core library")
    set("PUBLISH_ARTIFACT_ID", "analytics-core")
}

apply(from = "${'$'}{rootDir}/gradle/publish-module.gradle.kts")

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation(libs.kotlin.stdlib)

    // MAIN DEPS
    compileOnly(libs.json)
    compileOnly(libs.okhttp)
    implementation(libs.coroutines.core)

    testImplementation(libs.json)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockwebserver)
}

tasks.dokkaHtmlPartial.configure {
    failOnWarning.set(true)
}
