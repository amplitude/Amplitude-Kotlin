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

open class PublicationExtension(
    project: Project,
) {
    var name: String? = null
    var description: String? = null
    var artifactId: String? = null
}

class PublishModulePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        println("Applying PublishModulePlugin to project: ${project.name}")
        // Create extension for module-specific overrides
        val ext = project.extensions.create("publication", PublicationExtension::class.java, project)

        // Apply core plugins
        project.plugins.apply("maven-publish")
        project.plugins.apply("signing")
        project.plugins.apply("org.jetbrains.dokka")

        // Register source and javadoc jar tasks used in publication
        if (project.plugins.findPlugin("com.android.library") == null) {
            project.tasks.register("sourcesJar", Jar::class.java) {
                archiveClassifier.set("sources")
                val javaExtension = project.extensions.getByType(org.gradle.api.plugins.JavaPluginExtension::class.java)
                from(javaExtension.sourceSets.getByName("main").allSource)
            }
        }

        project.tasks.withType(DokkaTaskPartial::class.java).configureEach {
            pluginsMapConfiguration.set(mapOf("org.jetbrains.dokka.base.DokkaBase" to """{ \"separateInheritedMembers\": true }"""))
        }

        val javadocJar = project.tasks.register("javadocJar", Jar::class.java) {
            dependsOn(project.tasks.named("dokkaJavadoc"))
            archiveClassifier.set("javadoc")
            from(project.tasks.named("dokkaJavadoc").flatMap { (it as DokkaTask).outputDirectory })
        }
        project.afterEvaluate {
            // Configure the maven-publish extension
            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("release") {
                        groupId = rootProject.property("PUBLISH_GROUP_ID") as String
                        version = rootProject.property("PUBLISH_VERSION") as String

                        // Log extension properties for debugging
                        println("Publication name: ${ext.name}")
                        println("Publication description: ${ext.description}")
                        println("Publication artifactId: ${ext.artifactId}")

                        artifactId = ext.artifactId

                        if (plugins.hasPlugin("com.android.library")) {
                            from(components["release"])
                        } else {
                            from(components["java"])
                            artifact(tasks.named("sourcesJar"))
                        }

                        artifact(javadocJar.get())

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

            // Configure signing to sign the Maven publication
            extensions.configure<SigningExtension> {
                val publishing = extensions.getByType(PublishingExtension::class)
                sign(publishing.publications)
            }
        }
    }
}