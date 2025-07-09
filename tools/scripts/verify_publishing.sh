#!/bin/bash

# Script to verify signing configuration and publish artifacts to Maven Local
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

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source the signing verification script
source "$SCRIPT_DIR/verify_signing_config.sh"

# Function to publish to Maven local
publish_to_maven_local() {
    echo "=============================================="
    echo -e "${BLUE}🚀 Publishing to Maven Local...${NC}"
    echo

    if ./gradlew publishToMavenLocal; then
        echo
        echo -e "${GREEN}✅ Successfully published to Maven Local!${NC}"
        echo -e "${GREEN}   Artifacts are now available in your local Maven repository${NC}"
        return 0
    else
        echo
        echo -e "${RED}❌ Publishing to Maven Local failed!${NC}"
        return 1
    fi
}

# Main function
main() {
    # Verify signing configuration using the dedicated script
    if verify_signing_config; then
        echo -e "${GREEN}   You can publish with: ./gradlew publishToMavenLocal${NC}"
        publish_to_maven_local
        exit 0
    else
        exit 1
    fi
}

# Run the main function
main
