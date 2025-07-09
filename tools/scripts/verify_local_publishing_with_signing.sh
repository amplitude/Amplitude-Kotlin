#!/bin/bash

# Script to verify that proper signing keys are configured for local publishing
# Based on the signing methods described in PUBLISHING.md

set -e

echo "🔍 Verifying signing configuration for local publishing..."
echo

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Flags to track what we find
found_env_vars=false
found_keyring=false
found_inmemory=false

# Function to check if gradle.properties exists and read it
check_gradle_properties() {
    local gradle_props="gradle.properties"
    if [[ -f "$gradle_props" ]]; then
        echo -e "${BLUE}📄 Found gradle.properties file${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠️  No gradle.properties file found${NC}"
        return 1
    fi
}

# Function to check environment variables (CI method)
check_env_variables() {
    echo -e "${BLUE}🌍 Checking environment variables (CI method)...${NC}"

    if [[ -n "$ORG_GRADLE_PROJECT_signingInMemoryKey" ]]; then
        echo -e "${GREEN}✅ ORG_GRADLE_PROJECT_signingInMemoryKey is set${NC}"
        found_env_vars=true

        if [[ -n "$ORG_GRADLE_PROJECT_signingInMemoryKeyId" ]]; then
            echo -e "${GREEN}✅ ORG_GRADLE_PROJECT_signingInMemoryKeyId is set${NC}"
        else
            echo -e "${YELLOW}⚠️  ORG_GRADLE_PROJECT_signingInMemoryKeyId is not set (optional)${NC}"
        fi

        if [[ -n "$ORG_GRADLE_PROJECT_signingInMemoryKeyPassword" ]]; then
            echo -e "${GREEN}✅ ORG_GRADLE_PROJECT_signingInMemoryKeyPassword is set${NC}"
        else
            echo -e "${YELLOW}⚠️  ORG_GRADLE_PROJECT_signingInMemoryKeyPassword is not set (optional if key has no password)${NC}"
        fi
    else
        echo -e "${RED}❌ ORG_GRADLE_PROJECT_signingInMemoryKey is not set${NC}"
    fi
    echo
}

# Function to check key ring configuration in gradle.properties
check_keyring_config() {
    echo -e "${BLUE}🔑 Checking key ring configuration in gradle.properties...${NC}"

    if check_gradle_properties; then
        local has_keyid=$(grep -q "^signing.keyId=" gradle.properties && echo "true" || echo "false")
        local has_password=$(grep -q "^signing.password=" gradle.properties && echo "true" || echo "false")
        local has_keyring=$(grep -q "^signing.secretKeyRingFile=" gradle.properties && echo "true" || echo "false")

        if [[ "$has_keyid" == "true" && "$has_password" == "true" && "$has_keyring" == "true" ]]; then
            echo -e "${GREEN}✅ Key ring configuration found:${NC}"
            echo -e "${GREEN}  - signing.keyId${NC}"
            echo -e "${GREEN}  - signing.password${NC}"
            echo -e "${GREEN}  - signing.secretKeyRingFile${NC}"
            found_keyring=true

            # Check if the key ring file actually exists
            local keyring_path=$(grep "^signing.secretKeyRingFile=" gradle.properties | cut -d'=' -f2- | sed 's/~/$HOME/')
            if [[ -f "$keyring_path" ]]; then
                echo -e "${GREEN}✅ Key ring file exists: $keyring_path${NC}"
            else
                echo -e "${RED}❌ Key ring file not found: $keyring_path${NC}"
                found_keyring=false
            fi
        else
            echo -e "${RED}❌ Incomplete key ring configuration:${NC}"
            [[ "$has_keyid" == "false" ]] && echo -e "${RED}  - Missing: signing.keyId${NC}"
            [[ "$has_password" == "false" ]] && echo -e "${RED}  - Missing: signing.password${NC}"
            [[ "$has_keyring" == "false" ]] && echo -e "${RED}  - Missing: signing.secretKeyRingFile${NC}"
        fi
    fi
    echo
}

# Function to check in-memory key configuration in gradle.properties
check_inmemory_config() {
    echo -e "${BLUE}💾 Checking in-memory key configuration in gradle.properties...${NC}"

    if check_gradle_properties; then
        local has_inmemory_key=$(grep -q "^signingInMemoryKey=" gradle.properties && echo "true" || echo "false")
        local has_inmemory_keyid=$(grep -q "^signingInMemoryKeyId=" gradle.properties && echo "true" || echo "false")
        local has_inmemory_password=$(grep -q "^signingInMemoryKeyPassword=" gradle.properties && echo "true" || echo "false")

        if [[ "$has_inmemory_key" == "true" ]]; then
            echo -e "${GREEN}✅ In-memory key configuration found:${NC}"
            echo -e "${GREEN}  - signingInMemoryKey${NC}"
            found_inmemory=true

            if [[ "$has_inmemory_keyid" == "true" ]]; then
                echo -e "${GREEN}  - signingInMemoryKeyId${NC}"
            else
                echo -e "${YELLOW}  ⚠️  signingInMemoryKeyId not set (optional)${NC}"
            fi

            if [[ "$has_inmemory_password" == "true" ]]; then
                echo -e "${GREEN}  - signingInMemoryKeyPassword${NC}"
            else
                echo -e "${YELLOW}  ⚠️  signingInMemoryKeyPassword not set (optional if key has no password)${NC}"
            fi
        else
            echo -e "${RED}❌ signingInMemoryKey not found in gradle.properties${NC}"
        fi
    fi
    echo
}

# Function to check if signing is disabled
check_signing_disabled() {
    echo -e "${BLUE}🚫 Checking if signing is disabled...${NC}"

    if check_gradle_properties; then
        if grep -q "^signAllPublications=false" gradle.properties; then
            echo -e "${YELLOW}⚠️  Signing is disabled (signAllPublications=false)${NC}"
            echo -e "${YELLOW}   This is only suitable for local development without signing${NC}"
            return 0
        fi
    fi
    return 1
}

# Main verification logic
main() {
    echo "Starting verification of signing configuration..."
    echo "=============================================="
    echo

    # Check all possible configurations
    check_env_variables
    check_keyring_config
    check_inmemory_config

    # Check if signing is intentionally disabled
    signing_disabled=false
    if check_signing_disabled; then
        signing_disabled=true
    fi

    echo "=============================================="
    echo -e "${BLUE}📋 Summary:${NC}"
    echo

    # Determine the result
    if [[ "$found_env_vars" == "true" ]]; then
        echo -e "${GREEN}✅ Environment variables signing configuration is ready${NC}"
        echo -e "${GREEN}   You can publish with: ./gradlew publishToMavenLocal${NC}"
        exit 0
    elif [[ "$found_keyring" == "true" ]]; then
        echo -e "${GREEN}✅ Key ring signing configuration is ready${NC}"
        echo -e "${GREEN}   You can publish with: ./gradlew publishToMavenLocal${NC}"
        exit 0
    elif [[ "$found_inmemory" == "true" ]]; then
        echo -e "${GREEN}✅ In-memory signing configuration is ready${NC}"
        echo -e "${GREEN}   You can publish with: ./gradlew publishToMavenLocal${NC}"
        exit 0
    elif [[ "$signing_disabled" == "true" ]]; then
        echo -e "${YELLOW}⚠️  Signing is disabled - only suitable for local development${NC}"
        echo -e "${YELLOW}   You can publish with: ./gradlew publishToMavenLocal${NC}"
        exit 0
    else
        echo -e "${RED}❌ No valid signing configuration found!${NC}"
        echo
        echo -e "${YELLOW}💡 To fix this, choose one of the following options:${NC}"
        echo
        echo -e "${YELLOW}Option 1: Environment variables (recommended for CI)${NC}"
        echo "  export ORG_GRADLE_PROJECT_signingInMemoryKey=your_key"
        echo "  export ORG_GRADLE_PROJECT_signingInMemoryKeyId=your_key_id"
        echo "  export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=your_password"
        echo
        echo -e "${YELLOW}Option 2: Key ring in gradle.properties${NC}"
        echo "  signing.keyId=your_key_id"
        echo "  signing.password=your_password"
        echo "  signing.secretKeyRingFile=~/.gnupg/secring.gpg"
        echo
        echo -e "${YELLOW}Option 3: In-memory keys in gradle.properties${NC}"
        echo "  signingInMemoryKey=your_exported_key"
        echo "  signingInMemoryKeyId=your_key_id"
        echo "  signingInMemoryKeyPassword=your_password"
        echo
        echo -e "${YELLOW}Option 4: Disable signing for local development${NC}"
        echo "  signAllPublications=false"
        echo
        echo -e "${BLUE}📖 See PUBLISHING.md for detailed instructions${NC}"
        exit 1
    fi
}

# Run the main function
main
