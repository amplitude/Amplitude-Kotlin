plugins {
    id("org.jetbrains.compose")
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(project(":common"))
    compileOnly(compose.runtime)
    compileOnly(compose.ui)
}