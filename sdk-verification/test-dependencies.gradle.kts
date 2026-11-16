import org.gradle.api.artifacts.VersionCatalogsExtension

val testLibraries = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    listOf("coroutines-test", "mockk", "mockwebserver", "test-core", "robolectric", "junit-jupiter", "junit-jupiter-api", "junit4")
        .forEach { add("testImplementation", testLibraries.findLibrary(it).get()) }
    add("testImplementation", platform(testLibraries.findLibrary("junit-bom").get()))
    listOf("junit-jupiter-engine", "junit-vintage-engine", "junit-platform-launcher")
        .forEach { add("testRuntimeOnly", testLibraries.findLibrary(it).get()) }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("sdkVerificationCustomEvents", providers.gradleProperty("sdkVerificationCustomEvents").orElse("false").get())
    testLogging {
        events("passed", "skipped", "failed")
    }
}
