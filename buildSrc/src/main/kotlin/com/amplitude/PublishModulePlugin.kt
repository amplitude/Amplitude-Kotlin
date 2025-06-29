package com.amplitude

import org.gradle.kotlin.dsl.*
import org.gradle.api.publish.PublishingExtension
import org.gradle.plugins.signing.SigningExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import java.util.Properties
import java.io.FileInputStream

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
 * to a Maven repository. It handles the configuration of the maven-publish, signing and dokka plugins,
 * creates source and Javadoc JARs, and sets up the necessary POM metadata.
 * Usage:
 * ```kotlin
 * plugins {
 *     id("com.amplitude.publish-module-plugin")
 * }
 * Configure the publication extension:
 * publication {
 *    name = "My Library"
 *    description = "A description of my library"
 *    artifactId = "my-library-artifact-id"
 * }
 * ```
 * Note: Signing properties such as `signing.keyId`, `signing.password`, and `signing.secretKeyRingFile`
 * are required to be set in the `local.properties` file or as environment variables in order to sign the publication.
 * If these properties are not set, the plugin will log an warning and skip signing configuration.
 */
class PublishModulePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.logger.quiet("Applying PublishModulePlugin to project: ${project.name}")
        val ext = project.extensions.create("publication", PublicationExtension::class.java)

        applyCorePlugins(project)
        registerJarTasks(project)

        project.afterEvaluate {
            val localProperties = loadLocalProperties(project)
            configurePublication(project, ext)
            configureSigning(project, localProperties)
        }
    }

    @VisibleForTesting
    internal fun applyCorePlugins(project: Project) {
        project.plugins.apply("maven-publish")
        project.plugins.apply("signing")
        project.plugins.apply("org.jetbrains.dokka")
    }

    /**
     * Registers the source and Javadoc JAR tasks for the project.
     * If the project is an Android library, it registers a Dokka task for generating Javadoc.
     * It also configures the Dokka task to separate inherited members.
     *
     * @param project The Gradle project to register tasks for.
     */
    @VisibleForTesting
    internal fun registerJarTasks(project: Project) {
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
    }

    /**
     * Loads local properties from the `local.properties` file in the root project directory.
     * If the file exists, it reads the properties and returns them as a Properties object.
     * If the file does not exist, it returns an empty Properties object.
     *
     * @param project The Gradle project to load local properties for.
     * @return A Properties object containing the local properties.
     */
    @VisibleForTesting
    internal fun loadLocalProperties(project: Project): Properties {
        val localProperties = Properties()
        val localPropertiesFile = project.rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            FileInputStream(localPropertiesFile).use { fis ->
                localProperties.load(fis)
            }
        }
        return localProperties
    }

    /**
     * Configures the Maven publication with the provided properties from the PublicationExtension.
     * It sets the groupId, version, artifactId, and POM metadata.
     * It also configures the sources and Javadoc JARs if applicable.
     *
     * @param project The Gradle project to configure.
     * @param ext The PublicationExtension containing publication properties.
     */
    @VisibleForTesting
    internal fun configurePublication(project: Project, ext: PublicationExtension) {
        project.extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("release") {
                    groupId = project.rootProject.property("PUBLISH_GROUP_ID") as String
                    version = project.rootProject.property("PUBLISH_VERSION") as String

                    // Log extension properties for debugging
                    if (ext.name.isNullOrEmpty()) {
                        throw org.gradle.api.GradleException("Publication name is required but was not provided in the 'publication' extension.")
                    } else {
                        project.logger.quiet("Publication name: ${ext.name}")
                    }
                    if (ext.description.isNullOrEmpty()) {
                        throw org.gradle.api.GradleException("Publication description is required but was not provided in the 'publication' extension.")
                    } else {
                        project.logger.quiet("Publication description: ${ext.description}")
                    }
                    if (ext.artifactId.isNullOrEmpty()) {
                        throw org.gradle.api.GradleException("Publication artifactId is required but was not provided in the 'publication' extension.")
                    } else {
                        project.logger.quiet("Publication artifactId: ${ext.artifactId}")
                    }

                    artifactId = ext.artifactId

                    if (project.plugins.hasPlugin("com.android.library")) {
                        from(project.components["release"])
                    } else {
                        from(project.components["java"])
                        val sourcesJarTask = project.tasks.named(SOURCES_JAR)
                        project.logger.quiet("Sources JAR task outputs: ${sourcesJarTask.get().outputs.files.files}")
                        artifact(sourcesJarTask)
                    }

                    val javadocJarTask = project.tasks.named(JAVADOC_JAR)
                    project.logger.quiet("Javadoc JAR task outputs: ${javadocJarTask.get().outputs.files.files}")
                    artifact(javadocJarTask)

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
    }

    /**
     * Configures signing for the Maven publication.
     * It reads signing properties from project properties, local.properties, or environment variables,
     * and sets them to project.extra for use in the SigningExtension.
     * If all required properties are available, it configures the SigningExtension to sign the publication.
     * @param project The Gradle project to configure.
     * @param localProperties The local properties loaded from local.properties file.
     */
    @VisibleForTesting
    internal fun configureSigning(project: Project, localProperties: Properties) {
        // Load signing properties from project properties or environment variables
        // and set them to project.extra. This makes them available for the SigningExtension.
        val keyId = project.findProperty("signing.keyId")?.toString()
            ?: localProperties.getProperty("signing.keyId")
            ?: System.getenv("SIGNING_KEY_ID")
        val password = project.findProperty("signing.password")?.toString()
            ?: localProperties.getProperty("signing.password")
            ?: System.getenv("SIGNING_PASSWORD")
        val secretKeyRingFile = project.findProperty("signing.secretKeyRingFile")?.toString()
            ?: localProperties.getProperty("signing.secretKeyRingFile")
            ?: System.getenv("SIGNING_SECRET_KEY_RING_FILE")

        // We place keyId, password, and secretKeyRingFile in project.extra for signing configuration.
        // TODO: In future migrate to use in memory PGP keys instead of secretKeyRingFile.
        if (keyId != null) {
            project.extra.set("signing.keyId", keyId)
        } else {
            project.logger.error("Signing keyId is not set. Please provide it in the 'signing.keyId' property or environment variable.")
        }
        if (password != null) {
            project.extra.set("signing.password", password)
        } else {
            project.logger.error("Signing password is not set. Please provide it in the 'signing.password' property or environment variable.")
        }
        if (secretKeyRingFile != null) {
            project.extra.set("signing.secretKeyRingFile", secretKeyRingFile)
        } else {
            project.logger.error("Signing secretKeyRingFile is not set. Please provide it in the 'signing.secretKeyRingFile' property or environment variable.")
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
                "Signing properties (signing.keyId, signing.password, signing.secretKeyRingFile) \\n" +
                "not fully available in project.extra (expected from rootProject.extra). \\n" +
                "Skipping signing for project ${project.name}."
            )
        }
    }
}