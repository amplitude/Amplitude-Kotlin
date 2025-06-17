import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial

/**
 * This is a legacy Gradle plugin for publishing Android and Java modules.
 * Usage:
 * ```kotlin
 * plugins {
 *     id("com.amplitude.legacy-publish-module")
 *}
 * Configure the publication extension:
 * extra["PUBLISH_NAME"] = "My Library"
 * extra["PUBLISH_DESCRIPTION"] = "A description of my library"
 * extra["PUBLISH_ARTIFACT_ID"] = "my-library-artifact-id"
 * ```
 */
plugins {
    `maven-publish`
    signing
    id("org.jetbrains.dokka")
}

val publishGroup: String = project.rootProject.property("PUBLISH_GROUP_ID") as String
val publishVersion: String = project.rootProject.property("PUBLISH_VERSION") as String

val publishName = providers.provider {
    project.findProperty("PUBLISH_NAME")?.toString() ?: project.name
}
val publishDescription = providers.provider {
    project.findProperty("PUBLISH_DESCRIPTION")?.toString() ?: (project.description ?: "")
}
val publishArtifactId = providers.provider {
    project.findProperty("PUBLISH_ARTIFACT_ID")?.toString() ?: project.name
}

// Use Android source jar if available, otherwise create one
if (project.plugins.findPlugin("com.android.library") == null) {
    tasks.register<Jar>("sourcesJar") {
        archiveClassifier.set("sources")
        from(project.extensions.getByType(org.gradle.api.plugins.JavaPluginExtension::class).sourceSets.getByName("main").allSource)
    }
}

tasks.withType<DokkaTaskPartial>().configureEach {
    pluginsMapConfiguration.set(mapOf("org.jetbrains.dokka.base.DokkaBase" to """{ \"separateInheritedMembers\": true }"""))
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    dependsOn(tasks.named("dokkaJavadoc"))
    archiveClassifier.set("javadoc")
    from(tasks.named("dokkaJavadoc").flatMap { (it as DokkaTask).outputDirectory })
}

group = publishGroup
version = publishVersion

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = publishGroup
                version = publishVersion

                artifactId = publishArtifactId.get()

                // print components for debugging
                println("Available components: ${project.components.joinToString { it.name }}")

                if (project.plugins.hasPlugin("com.android.library")) {
                    from(components["release"])
                } else {
                    from(components["java"])
                    artifact(tasks.named("sourcesJar"))
                }
                artifact(javadocJar.get())

                // Print debug information about the project
                println("Project: ${project.name}")
                println("Group: $group")
                println("Version: $version")
                println("Artifact ID: ${publishArtifactId.get()}")
                println("Name: ${publishName.get()}")
                println("Description: ${publishDescription.get()}")

                pom {
                    name.set(publishName)
                    description.set(publishDescription)
                    url.set(project.rootProject.property("POM_URL") as String)
                    licenses {
                        license {
                            name.set(project.rootProject.property("POM_LICENCE_NAME") as String)
                            url.set(project.rootProject.property("POM_LICENCE_URL") as String)
                            distribution.set(project.rootProject.property("POM_LICENCE_DIST") as String)
                        }
                    }
                    developers {
                        developer {
                            id.set(project.rootProject.property("POM_DEVELOPER_ID") as String)
                            name.set(project.rootProject.property("POM_DEVELOPER_NAME") as String)
                            email.set(project.rootProject.property("POM_DEVELOPER_EMAIL") as String)
                        }
                    }
                    scm {
                        connection.set(project.rootProject.property("POM_SCM_CONNECTION") as String)
                        developerConnection.set(project.rootProject.property("POM_SCM_DEV_CONNECTION") as String)
                        url.set(project.rootProject.property("POM_SCM_URL") as String)
                    }
                }
            }
        }
    }
}

extra["signing.keyId"] = project.rootProject.extra["signing.keyId"]
extra["signing.password"] = project.rootProject.extra["signing.password"]
extra["signing.secretKeyRingFile"] = project.rootProject.extra["signing.secretKeyRingFile"]

signing {
    sign(publishing.publications)
}