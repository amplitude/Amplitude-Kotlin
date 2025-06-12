plugins {
    `maven-publish`
    signing
    id("org.jetbrains.dokka")
}

// Use Android's built-in source jar for libraries, create custom one for non-Android modules
if (project.plugins.findPlugin("com.android.library") == null) {
    tasks.register<Jar>("sourcesJar") {
        archiveClassifier.set("sources")
        from(sourceSets.main.get().java.srcDirs)
        from(sourceSets.main.get().kotlin.srcDirs)
    }
}

tasks.withType<org.jetbrains.dokka.gradle.DokkaTaskPartial>().configureEach {
    pluginsMapConfiguration.set(mapOf("org.jetbrains.dokka.base.DokkaBase" to "{ \"separateInheritedMembers\": true}"))
}

tasks.register<Jar>("javadocJar") {
    dependsOn(tasks.named("dokkaJavadoc"))
    archiveClassifier.set("javadoc")
    from(tasks.named("dokkaJavadoc").get().outputs.files)
}

val PUBLISH_GROUP_ID: String by project
val PUBLISH_VERSION: String by project

group = PUBLISH_GROUP_ID
version = PUBLISH_VERSION

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = PUBLISH_GROUP_ID
                artifactId = project.extra["PUBLISH_ARTIFACT_ID"] as String
                version = PUBLISH_VERSION
                if (project.plugins.findPlugin("com.android.library") != null) {
                    from(components.findByName("release"))
                } else {
                    from(components.findByName("java"))
                    artifact(tasks.named("sourcesJar"))
                }

                artifact(tasks.named("javadocJar"))

                pom {
                    name.set(project.extra["PUBLISH_NAME"] as String)
                    description.set(project.extra["PUBLISH_DESCRIPTION"] as String)
                    url.set(project.property("POM_URL") as String)
                    licenses {
                        license {
                            name.set(project.property("POM_LICENCE_NAME") as String)
                            url.set(project.property("POM_LICENCE_URL") as String)
                            distribution.set(project.property("POM_LICENCE_DIST") as String)
                        }
                    }
                    developers {
                        developer {
                            id.set(project.property("POM_DEVELOPER_ID") as String)
                            name.set(project.property("POM_DEVELOPER_NAME") as String)
                            email.set(project.property("POM_DEVELOPER_EMAIL") as String)
                        }
                    }
                    scm {
                        connection.set(project.property("POM_SCM_CONNECTION") as String)
                        developerConnection.set(project.property("POM_SCM_DEV_CONNECTION") as String)
                        url.set(project.property("POM_SCM_URL") as String)
                    }
                }
            }
        }
    }
}

extra["signing.keyId"] = rootProject.extra["signing.keyId"]
extra["signing.password"] = rootProject.extra["signing.password"]
extra["signing.secretKeyRingFile"] = rootProject.extra["signing.secretKeyRingFile"]

signing {
    sign(publishing.publications)
}
