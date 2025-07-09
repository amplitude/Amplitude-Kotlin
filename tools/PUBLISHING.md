# Publishing Guide

## CI PUBLISHING

CI publishing relies on the following environment variables:

```bash
# Maven Central credentials
ORG_GRADLE_PROJECT_mavenCentralUsername=username
ORG_GRADLE_PROJECT_mavenCentralPassword=the_password

# Signing configuration (see below for how to obtain this)
ORG_GRADLE_PROJECT_signingInMemoryKey=exported_ascii_armored_key

# Optional signing configuration
ORG_GRADLE_PROJECT_signingInMemoryKeyId=12345678

# If key was created with a password
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=some_password
```

## Maven Local without signing

For local development and testing, you can publish to your local Maven repository without signing.
This is useful when you just need to test the library locally without the complexity of setting up GPG keys.

To publish without signing, you can either:

### Option 1: Skip signing tasks
```bash
./gradlew publishToMavenLocal -x signMavenPublication
```

### Option 2: Disable signing in gradle.properties
Add the following to your `gradle.properties` file:
```bash
# Disable signing for local development
signAllPublications=false
```

Then run:
```bash
./gradlew publishToMavenLocal
```

**Note:** Publications without signing should only be used for local development and testing.
They cannot be published to Maven Central or other public repositories.

## Maven Local with KEY RING

For local publishing using a GPG key ring, add the following parameters to your `gradle.properties` file:

```bash
# Signing configuration for key ring
signing.keyId=keyId
signing.password=password
signing.secretKeyRingFile=~/.gnupg/secring.gpg
```

Where:
- `signing.keyId`: The ID of your GPG key (last 8 characters of the key fingerprint)
- `signing.password`: The password for your GPG key
- `signing.secretKeyRingFile`: Path to your GPG secret key ring file (typically located at `~/.gnupg/secring.gpg`)

## Maven Local with inmemory keys

For local publishing using in-memory signing with GPG keys, you can configure the signing parameters 
directly in your `gradle.properties` file.

First, extract the private key from your GPG key ring using the following command:

```bash
gpg --export-secret-keys --armor ${key-id} ${path-to-secring.gpg} | grep -v '\-\-' | grep -v '^=.' | tr -d '\n'
```

This command will:
- Export the secret key in ASCII armor format
- Filter out the header/footer lines (lines starting with `--`)
- Remove lines starting with `=.` (checksum lines)
- Remove all newlines to create a single-line string

Once you have the extracted key, add the following configuration to your `gradle.properties` file:

```bash
# In-memory signing configuration
signingInMemoryKey=your_extracted_key_string_here
signingInMemoryKeyId=your_key_id_here
signingInMemoryKeyPassword=your_key_password_here
```

Where:
- `signingInMemoryKey`: The extracted ASCII-armored private key (single line string from the command above)
- `signingInMemoryKeyId`: The ID of your GPG key (last 8 characters of the key fingerprint)
- `signingInMemoryKeyPassword`: The password for your GPG key (if the key was created with a password)

Then run:
```bash
./gradlew publishToMavenLocal
```

**Note:** The in-memory signing configuration allows you to sign publications without needing the GPG key ring file 
on your system, making it useful for CI/CD environments or when you want to avoid managing key ring files locally.

## Publishing snapshots

Snapshots can be published by setting the version to something ending with -SNAPSHOT and then running the following Gradle task:

```bash
./gradlew publishToMavenCentral
```

The snapshot will be automatically available in the Central Portal snapshot repository directly after the task finished.

Signing is not required for snapshot builds, but if the configuration is present the build will still be signed.
