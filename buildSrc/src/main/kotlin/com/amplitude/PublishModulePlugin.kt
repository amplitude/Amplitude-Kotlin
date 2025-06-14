package com.amplitude

import org.gradle.kotlin.dsl.*
import org.gradle.api.publish.PublishingExtension
import org.gradle.plugins.signing.SigningExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication

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

        // Configure the maven-publish extension
        project.extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("release") {
                    groupId = project.rootProject.property("PUBLISH_GROUP_ID") as String
                    version = project.rootProject.property("PUBLISH_VERSION") as String
                    artifactId = ext.artifactId

                    // print components for debugging
                    project.afterEvaluate {
                        println("Available components: ${project.components.joinToString { it.name }}")
                        println("Release components: ${project.components["release"]}")

                        if (project.plugins.hasPlugin("com.android.library")) {
                            from(project.components["release"])
                        } else {
                            from(project.components["java"])
                            artifact(project.tasks.named("sourcesJar"))
                        }
                    }

                    // Configure POM metadata
                    pom {
                        name.set(ext.name)
                        description.set(ext.description)
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

        // Configure signing to sign the Maven publication
        project.extensions.configure<SigningExtension> {
            val publishing = project.extensions.getByType(PublishingExtension::class)
            sign(publishing.publications)
        }
    }
}