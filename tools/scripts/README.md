# Verification Script for Local Maven Publications

This directory contains scripts used for verifying the local Maven publication process for the Amplitude-Kotlin SDK.

## `verify_maven_local_publish.sh`

### Purpose

The `verify_maven_local_publish.sh` script is designed to automate the verification of artifacts published to your local Maven repository (`~/.m2/repository`). It checks for the presence of essential files for each published module, including:

*   POM files (`.pom`)
*   Main artifacts (JARs or AARs)
*   Sources JARs (`-sources.jar`)
*   Javadoc JARs (`-javadoc.jar`)
*   Gradle Module Metadata files (`.module`)
*   Digital signature files (`.asc`) for all the above (except typically for `.module` files).

This script is particularly useful for:

*   **Pre-release checks**: Ensuring that the build and publication process generates all necessary artifacts correctly before attempting a public release.
*   **CI/CD validation**: Integrating into continuous integration pipelines to catch publication issues early.
*   **Development testing**: Allowing developers to quickly confirm that their changes haven't broken the local publishing mechanism.

### Prerequisites

1.  **Signing Configuration**: The script checks for signing credentials (`signing.keyId`, `signing.password`, `signing.secretKeyRingFile`) in the `local.properties` file at the root of the repository. Ensure this file is present and correctly configured if you intend to verify signed artifacts.
2.  **Gradle Task `printPublishCoordinates`**: The script relies on a Gradle task named `printPublishCoordinates`. This task must be defined in your root Gradle build configuration (e.g., `build.gradle.kts`). Its purpose is to print the publishing coordinates (in the format `groupId:artifactId:version:projectName`) for each module that should be published.

#### Creating and Configuring Local Signing Credentials

To generate and use local signing keys for artifact verification, follow these steps:

1.  **Generate a GPG Key**: If you don't have one, create a GPG key. You can use a command like:
    ```bash
    gpg --full-generate-key
    ```
    Follow the prompts. It's recommended to use an RSA key type.

2.  **List Your Keys**: To find your `signing.keyId`, list your GPG keys:
    ```bash
    gpg --list-secret-keys --keyid-format LONG
    ```
    Your key ID is the long string after `sec` and before the date (e.g., `rsa4096/XXXXXXXXXXXXXXXX`). You'll need the last 8 or 16 characters of this ID.

3.  **Export the Secret Keyring**: Export your secret keyring file.
    ```bash
    gpg --export-secret-keys -o ~/.gnupg/secring.gpg
    ```
    This command saves the keyring to `~/.gnupg/secring.gpg`. You can choose a different location if preferred.

4.  **Configure `local.properties`**: Create or update the `local.properties` file in the root directory of the Amplitude-Kotlin repository with the following content, replacing the placeholder values with your actual credentials:
    ```properties
    signing.keyId=YOUR_KEY_ID 
    signing.password=YOUR_GPG_KEY_PASSPHRASE
    signing.secretKeyRingFile=/path/to/your/secring.gpg 
    ```
    *   `signing.keyId`: The last 8 or 16 characters of your GPG key ID.
    *   `signing.password`: The passphrase for your GPG key.
    *   `signing.secretKeyRingFile`: The absolute path to your exported secret keyring file (e.g., `/Users/yourusername/.gnupg/secring.gpg` or the path you chose in step 3).

    **Important Security Note**: The `local.properties` file should **never** be committed to version control, as it contains sensitive credentials. Ensure it is listed in your project's `.gitignore` file.

### How to Use

1.  **Make the script executable**:
    Navigate to the `tools/scripts` directory in your terminal and run:
    ```bash
    chmod +x verify_maven_local_publish.sh
    ```

2.  **Run the script**:
    From the `tools/scripts` directory, execute the script:
    ```bash
    ./verify_maven_local_publish.sh
    ```
    The script will then:
    *   Check for signing credentials.
    *   Invoke the `printPublishCoordinates` Gradle task to get the list of modules and their coordinates.
    *   Invoke the `publishReleasePublicationToMavenLocal` Gradle task (or a similarly named task responsible for publishing all release artifacts to Maven Local).
    *   Iterate through the modules and verify the presence of the expected artifacts and their signature files in your `~/.m2/repository`.

### Connection to `printPublishCoordinates` Gradle Task

The `verify_maven_local_publish.sh` script is tightly coupled with a Gradle task, typically named `printPublishCoordinates`. Here's how they interact:

1.  **Script Invocation**: The bash script executes `./gradlew printPublishCoordinates` (adjusting for the workspace root).
2.  **Gradle Task Execution**: This Gradle task runs and should be configured to iterate through all subprojects (or specific projects intended for publication) and print their Maven coordinates (Group ID, Artifact ID, Version) along with a project identifier. The expected output format for each module is a line like: `com.example.group:my-artifact:1.0.0:myProjectName`.
3.  **Output Parsing**: The bash script captures the standard output of the `printPublishCoordinates` task.
4.  **Verification Logic**: It then parses each line of this output to determine the `groupId`, `artifactId`, `version`, and `projectName`. These details are used to construct the expected file paths within the local Maven repository (`~/.m2/repository/group/id/path/artifact-id/version/`) and check for the existence of the artifact files and their corresponding `.asc` signature files.

Therefore, the accuracy and completeness of the `printPublishCoordinates` task's output are crucial for the verification script to function correctly. If new modules are added or publishing details change, this Gradle task might need to be updated accordingly.
