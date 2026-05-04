plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.mavenPublish) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.bcv)
}

apiValidation {
    ignoredProjects += listOf("kotlin-android-app")
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    group = project.findProperty("GROUP") ?: ""
    version = project.findProperty("VERSION_NAME") ?: "0.0.1-SNAPSHOT"

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(KotlinConfig.JVM_TARGET))
        }
    }
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")
}
