package com.amplitude

import org.gradle.kotlin.dsl.*
import org.gradle.api.publish.PublishingExtension
import org.gradle.plugins.signing.SigningExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial

open class PublicationExtension {
    var name: String? = null
    var description: String? = null
    var artifactId: String? = null
}

const val SOURCES_JAR = "sourcesJar"
const val JAVADOC_JAR = "javadocJar"
const val DOKKA_JAVADOC = "dokkaJavadoc"

/**
 * PublishModulePlugin is a Gradle plugin that simplifies the process of publishing Android libraries and modules
 * to a Maven repository. It handles the configuration of the maven-publish and signing plugins,
 * creates source and Javadoc JARs, and sets up the necessary POM metadata.
 * Usage:
 * ```kotlin
 * plugins {
 *     id("com.amplitude.publish-module")
 * }
 * Configure the publication extension:
 * publication {
 *    name = "My Library"
 *    description = "A description of my library"
 *    artifactId = "my-library-artifact-id"
 * }
 * ```
 */
class PublishModulePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.logger.quiet("Applying PublishModulePlugin to project: ${project.name}")
        // Create extension for module-specific overrides
        val ext = project.extensions.create("publication", PublicationExtension::class.java)

        // Apply core plugins
        project.plugins.apply("maven-publish")
        project.plugins.apply("signing")
        project.plugins.apply("org.jetbrains.dokka")

        // Register source and javadoc jar tasks used in publication
        if (project.plugins.findPlugin("com.android.library") == null) {
            project.tasks.register(SOURCES_JAR, Jar::class.java) {
                archiveClassifier.set("sources")
                val javaExtension = project.extensions.getByType(org.gradle.api.plugins.JavaPluginExtension::class.java)
                from(javaExtension.sourceSets.getByName("main").allSource)
            }
        }

        project.tasks.withType(DokkaTaskPartial::class.java).configureEach {
            pluginsMapConfiguration.set(mapOf("org.jetbrains.dokka.base.DokkaBase" to """{ \"separateInheritedMembers\": true }"""))
        }

        project.tasks.register(JAVADOC_JAR, Jar::class.java) {
            dependsOn(project.tasks.named(DOKKA_JAVADOC))
            archiveClassifier.set("javadoc")
            from(project.tasks.named(DOKKA_JAVADOC).flatMap { (it as DokkaTask).outputDirectory })
        }

        project.afterEvaluate {
            // Configure the maven-publish extension
            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("release") {
                        groupId = rootProject.property("PUBLISH_GROUP_ID") as String
                        version = rootProject.property("PUBLISH_VERSION") as String

                        // Log extension properties for debugging
                        project.logger.quiet("Publication name: ${ext.name}")
                        project.logger.quiet("Publication description: ${ext.description}")
                        project.logger.quiet("Publication artifactId: ${ext.artifactId}")

                        artifactId = ext.artifactId

                        if (plugins.hasPlugin("com.android.library")) {
                            from(components["release"])
                        } else {
                            from(components["java"])
                            artifact(tasks.named(SOURCES_JAR))
                        }

                        artifact(tasks.named(JAVADOC_JAR))

                        // Configure POM metadata
                        pom {
                            name.set(ext.name)
                            description.set(ext.description)
                            url.set(rootProject.property("POM_URL") as String)

                            licenses {
                                license {
                                    name.set(rootProject.property("POM_LICENCE_NAME") as String)
                                    url.set(rootProject.property("POM_LICENCE_URL") as String)
                                    distribution.set(rootProject.property("POM_LICENCE_DIST") as String)
                                }
                            }

                            developers {
                                developer {
                                    id.set(rootProject.property("POM_DEVELOPER_ID") as String)
                                    name.set(rootProject.property("POM_DEVELOPER_NAME") as String)
                                    email.set(rootProject.property("POM_DEVELOPER_EMAIL") as String)
                                }
                            }

                            scm {
                                connection.set(rootProject.property("POM_SCM_CONNECTION") as String)
                                developerConnection.set(rootProject.property("POM_SCM_DEV_CONNECTION") as String)
                                url.set(rootProject.property("POM_SCM_URL") as String)
                            }
                        }
                    }
                }
            }

            // Load signing properties from project properties or environment variables
            // and set them to project.extra. This makes them available for the SigningExtension.
            val keyId = project.findProperty("signing.keyId")?.toString() ?: System.getenv("SIGNING_KEY_ID")
            val password = project.findProperty("signing.password")?.toString() ?: System.getenv("SIGNING_PASSWORD")
            val secretKeyRingFile = project.findProperty("signing.secretKeyRingFile")?.toString() ?: System.getenv("SIGNING_SECRET_KEY_RING_FILE")

            if (keyId != null) {
                project.extra.set("signing.keyId", keyId)
            }
            if (password != null) {
                project.extra.set("signing.password", password)
            }
            if (secretKeyRingFile != null) {
                project.extra.set("signing.secretKeyRingFile", secretKeyRingFile)
            }

            // Configure signing to sign the Maven publication, only if all properties are available
            if (project.extra.has("signing.keyId") &&
                project.extra.has("signing.password") &&
                project.extra.has("signing.secretKeyRingFile")) {
                project.extensions.configure<SigningExtension> {
                    val publishing = project.extensions.findByType<PublishingExtension>()
                    sign(publishing?.publications)
                }
            } else {
                project.logger.error(
                    "Signing properties (signing.keyId, signing.password, signing.secretKeyRingFile) \n" +
                    "not fully available in project.extra (expected from rootProject.extra). \n" +
                    "Skipping signing for project ${project.name}."
                )
            }
        }
    }
}