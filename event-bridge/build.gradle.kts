plugins {
    kotlin("multiplatform")
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
        val jvmMain by getting {
            dependencies {
                implementation(project(":common"))
                implementation(kotlin("stdlib"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.junit.jupiter:junit-jupiter")
            }
        }
    }
}
