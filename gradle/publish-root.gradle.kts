// Create variables with empty default values
extra["ossrhUsername"] = ""
extra["ossrhPassword"] = ""
extra["sonatypeStagingProfileId"] = ""
extra["signing.keyId"] = ""
extra["signing.password"] = ""
extra["signing.secretKeyRingFile"] = ""

val secretPropsFile = project.rootProject.file("local.properties")
if (secretPropsFile.exists()) {
    val props = java.util.Properties()
    java.io.FileInputStream(secretPropsFile).use { props.load(it) }
    props.forEach { name, value -> extra[name.toString()] = value }
} else {
    extra["ossrhUsername"] = System.getenv("OSSRH_USERNAME") ?: ""
    extra["ossrhPassword"] = System.getenv("OSSRH_PASSWORD") ?: ""
    extra["sonatypeStagingProfileId"] = System.getenv("SONATYPE_STAGING_PROFILE_ID") ?: ""
    extra["signing.keyId"] = System.getenv("SIGNING_KEY_ID") ?: ""
    extra["signing.password"] = System.getenv("SIGNING_PASSWORD") ?: ""
    extra["signing.secretKeyRingFile"] = System.getenv("SIGNING_SECRET_KEY_RING_FILE") ?: ""
}

nexusPublishing {
    repositories {
        sonatype {
            stagingProfileId.set(extra["sonatypeStagingProfileId"] as String)
            username.set(extra["ossrhUsername"] as String)
            password.set(extra["ossrhPassword"] as String)
        }
    }
}
