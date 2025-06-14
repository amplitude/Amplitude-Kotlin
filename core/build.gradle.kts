plugins {
    id("java")
    id("kotlin")
    id("org.jetbrains.kotlin.jvm")
}

extra.set("PUBLISH_NAME", "Amplitude Kotlin Core")
extra.set("PUBLISH_DESCRIPTION", "Amplitude Kotlin Core library")
extra.set("PUBLISH_ARTIFACT_ID", "analytics-core")

//apply(from = rootDir.resolve("gradle/publish-module.gradle"))

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
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

tasks.test {
    useJUnitPlatform()
}

tasks.dokkaHtmlPartial.configure {
    failOnWarning.set(true)
}
