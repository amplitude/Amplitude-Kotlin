import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.mavenPublish) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.bcv)
}

apiValidation {
    ignoredProjects += listOf("kotlin-android-app", "streaming-app")
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    group = project.findProperty("GROUP") ?: ""
    version = project.findProperty("VERSION_NAME") ?: "0.0.1-SNAPSHOT"

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(KotlinConfig.JVM_TARGET))

            // Keeps the published artifacts consumable by older Kotlin compilers.
            //
            // We compile with Kotlin 2.2 (forced by AGP 9), which by default stamps
            // classes with `@Metadata` version 2.2.0. A Kotlin compiler only reads
            // metadata up to roughly one minor ahead of itself, so consumers below
            // Kotlin 2.1 fail with "was compiled with an incompatible version of
            // Kotlin" the moment they touch an SDK type. Measured against 2.2.0
            // metadata: 1.9.x breaks, 2.0.x breaks, 2.1+ is fine.
            //
            // Pinning the language/API version back to 1.9 lowers the emitted
            // metadata version so Kotlin 1.9, 2.0, 2.1 and 2.2 consumers all
            // compile. This is compile-time only — runtime was never affected.
            //
            // Cost: SDK source cannot use Kotlin 2.x language features, and the
            // compiler emits "Language version 1.9 is deprecated".
            //
            // To remove: raising this is a breaking change for consumers, so it
            // needs a major/minor release and a release note, not a chore bump.
            // Before removing, confirm downstreams compile with Kotlin >= 2.1 —
            // notably amplitude/session-replay-android, which depends on
            // com.amplitude:analytics-android via the open range [1.29.0,2.0.0)
            // and so picks up our releases automatically. Then drop both lines
            // and let the metadata version follow the compiler.
            languageVersion.set(KotlinVersion.KOTLIN_1_9)
            apiVersion.set(KotlinVersion.KOTLIN_1_9)

            freeCompilerArgs.set(listOf("-Xjvm-default=all"))
        }
    }
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")
}
