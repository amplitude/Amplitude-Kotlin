plugins {
    kotlin("multiplatform")
}

group = "com.amplitude"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(8)
    jvm {
        withJava()
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":common"))
                api(project(":event-bridge"))
                api(project(":id"))

                implementation("org.json:json:20211205")
                implementation("org.jetbrains.kotlin:kotlin-stdlib")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation("io.mockk:mockk:1.12.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")

                implementation(platform("org.junit:junit-bom:5.7.2"))
                implementation("org.junit.jupiter:junit-jupiter")
                implementation("com.squareup.okhttp3:mockwebserver:4.10.0")
            }
        }
    }
}

tasks.dokkaHtmlPartial.configure {
    failOnWarning.set(true)
}
