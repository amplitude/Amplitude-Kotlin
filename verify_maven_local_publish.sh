#!/bin/bash
set -e # Exit immediately if a command exits with a non-zero status.

LOCAL_PROPERTIES_FILE="local.properties"
MAVEN_LOCAL_REPO="$HOME/.m2/repository"
# Assuming the script is in the workspace root.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$SCRIPT_DIR"

# ANSI color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo_info() {
    echo -e "${GREEN}[INFO] $1${NC}"
}

echo_warn() {
    echo -e "${YELLOW}[WARN] $1${NC}"
}

echo_error() {
    echo -e "${RED}[ERROR] $1${NC}" >&2
}

check_local_properties() {
    echo_info "Checking for signing credentials in $WORKSPACE_ROOT/$LOCAL_PROPERTIES_FILE..."
    if [ ! -f "$WORKSPACE_ROOT/$LOCAL_PROPERTIES_FILE" ]; then
        echo_error "$LOCAL_PROPERTIES_FILE not found in $WORKSPACE_ROOT."
        echo_error "Please ensure $LOCAL_PROPERTIES_FILE exists and contains signing.keyId, signing.password, and signing.secretKeyRingFile."
        exit 1
    fi

    missing_props=0
    if ! grep -q "^signing.keyId=" "$WORKSPACE_ROOT/$LOCAL_PROPERTIES_FILE"; then
        echo_error "signing.keyId not found in $LOCAL_PROPERTIES_FILE."
        missing_props=1
    fi
    if ! grep -q "^signing.password=" "$WORKSPACE_ROOT/$LOCAL_PROPERTIES_FILE"; then
        echo_error "signing.password not found in $LOCAL_PROPERTIES_FILE."
        missing_props=1
    fi
    if ! grep -q "^signing.secretKeyRingFile=" "$WORKSPACE_ROOT/$LOCAL_PROPERTIES_FILE"; then
        echo_error "signing.secretKeyRingFile not found in $LOCAL_PROPERTIES_FILE."
        missing_props=1
    fi

    if [ "$missing_props" -eq 1 ]; then
        echo_error "One or more signing credentials missing. Please check $LOCAL_PROPERTIES_FILE."
        exit 1
    fi
    echo_info "Signing credentials found in $LOCAL_PROPERTIES_FILE."
}

get_publish_coordinates() {
    echo_info "Retrieving publish coordinates from Gradle... " >&2
    if [ ! -x "$WORKSPACE_ROOT/gradlew" ]; then
        echo_warn "$WORKSPACE_ROOT/gradlew is not executable. Attempting to make it executable..."
        chmod +x "$WORKSPACE_ROOT/gradlew"
        if [ ! -x "$WORKSPACE_ROOT/gradlew" ]; then
            echo_error "Failed to make $WORKSPACE_ROOT/gradlew executable. Please do it manually."
            exit 1
        fi
    fi

    # Run the Gradle task. Use -q for quiet output.
    # Redirect stderr to stdout to capture any warnings from the task itself
    local coordinates_output
    coordinates_output=$("$WORKSPACE_ROOT/gradlew" -p "$WORKSPACE_ROOT" printPublishCoordinates -q 2>&1)

    if [ $? -ne 0 ]; then
        echo_error "Gradle task printPublishCoordinates failed."
        echo_error "Output from Gradle:"
        echo "$coordinates_output" >&2
        exit 1
    fi

    # Filter for lines that look like coordinates (G:A:V:ProjectName)
    # AND filter out lines that start with "[INFO]" to avoid malformed entries
    echo "$coordinates_output" | grep -E "^[^:]+:[^:]+:[^:]+:[^:]+" | grep -E -v "^\\[INFO\\]"
}

publish_to_maven_local() {
    echo_info "Publishing to Maven Local using root project task './gradlew publishToMavenLocal'..."
    if ! "$WORKSPACE_ROOT/gradlew" -p "$WORKSPACE_ROOT" publishReleasePublicationToMavenLocal; then
        echo_error "Gradle task publishToMavenLocal failed."
        exit 1
    fi
    echo_info "Successfully published to Maven Local."
}

verify_publications() {
    local coordinates_string="$1"
    local all_checks_passed=true

    echo_info "Verifying publications in $MAVEN_LOCAL_REPO..."

    if [ -z "$coordinates_string" ]; then
        echo_warn "No module coordinates found to verify. Check output of printPublishCoordinates task."
        return 1
    fi

    IFS=$'\n'
    for line in $coordinates_string; do
        local group_id artifact_id version project_name
        group_id=$(echo "$line" | cut -d: -f1)
        artifact_id=$(echo "$line" | cut -d: -f2)
        version=$(echo "$line" | cut -d: -f3)
        project_name=$(echo "$line" | cut -d: -f4)

        if [ -z "$project_name" ] || [ -z "$group_id" ] || [ -z "$artifact_id" ] || [ -z "$version" ]; then
            echo_warn "  Skipping verification for line: '$line' due to missing GAV or project name."
            continue
        fi

        echo_info "Verifying module: $project_name ($group_id:$artifact_id:$version)"

        local module_path_base
        module_path_base="$MAVEN_LOCAL_REPO/$(echo "$group_id" | tr '.' '/')/$artifact_id/$version"

        local main_artifact_filename
        # Check if the project is the Android project.
        # This assumes the Android module's project name as reported by printPublishCoordinates is 'android'.
        # Adjust this condition if the project name is different (e.g., derived from artifact_id or a different naming convention).
        if [ "$project_name" == "android" ]; then
            main_artifact_filename="$artifact_id-$version.aar"
        else
            main_artifact_filename="$artifact_id-$version.jar"
        fi

        local files_to_check_basenames=(
            "$artifact_id-$version.pom"
            "$main_artifact_filename" # Use the dynamically determined filename
            "$artifact_id-$version-sources.jar"
            "$artifact_id-$version-javadoc.jar"
            "$artifact_id-$version.module"
        )

        if [ ! -d "$module_path_base" ]; then
            echo_error "  Directory not found for module: $module_path_base"
            all_checks_passed=false
            continue
        fi

        for file_basename in "${files_to_check_basenames[@]}"; do
            local file_full_path="$module_path_base/$file_basename"
            local sig_file_full_path="$file_full_path.asc"

            if [ -f "$file_full_path" ]; then
                echo_info "  Found: $file_basename"
            else
                if [[ "$file_basename" == *".module" ]]; then
                    echo_warn "  Optional file not found: $file_basename at $file_full_path"
                else
                    echo_error "  File not found: $file_basename at $file_full_path"
                    all_checks_passed=false
                fi
            fi

            if [[ "$file_basename" != *".module" ]]; then # .module files are typically not signed
                if [ -f "$sig_file_full_path" ]; then
                    echo_info "  Found signature: $file_basename.asc"
                else
                    if [ -f "$file_full_path" ]; then
                        echo_error "  Signature not found: $file_basename.asc at $sig_file_full_path"
                        all_checks_passed=false
                    elif [[ "$file_basename" != *".module" ]]; then
                         echo_warn "  Signature also not found (as main file $file_basename is missing): $file_basename.asc"
                    fi
                fi
            fi
        done
    done
    IFS=$' \t\n'

    if [ "$all_checks_passed" = true ]; then
        echo_info "All publication verifications passed."
        return 0
    else
        echo_error "Some publication verifications failed."
        return 1
    fi
}

# Main script execution
main() {
    cd "$WORKSPACE_ROOT"

    check_local_properties

    module_coordinates=$(get_publish_coordinates)
    if [ -z "$module_coordinates" ]; then
        echo_error "Failed to retrieve module coordinates or no modules are configured for publishing. Exiting."
        exit 1
    fi
    echo_info "Found the following module coordinates to check:"
    echo "$module_coordinates"

    publish_to_maven_local

    verify_publications "$module_coordinates"
    local verify_status=$?

    if [ $verify_status -eq 0 ]; then
        echo_info "Script completed successfully. All checks passed."
        exit 0
    else
        echo_error "Script completed with errors. Some checks failed."
        exit 1
    fi
}

main
