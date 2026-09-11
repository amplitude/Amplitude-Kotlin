plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "com.amplitude.buildsrc"
version = "1.0.0"

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Task types for AGP 9 Android libraries (BCV does not register apiDump there).
    implementation("org.jetbrains.kotlinx:binary-compatibility-validator:${libs.versions.bcv.get()}")
}
