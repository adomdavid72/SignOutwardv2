#!/bin/bash
# Create Emulator with Optimized Configuration
# This creates a new emulator AVD with settings to reduce system errors

set -e

echo "=== Creating Optimized Emulator Configuration ==="
echo ""

# Check if emulator is available
if ! command -v emulator &> /dev/null; then
    echo "ERROR: Android emulator not found. Please install Android SDK."
    exit 1
fi

# AVD name
AVD_NAME="SignOutward_Optimized"

echo "Creating AVD: $AVD_NAME"
echo ""

# Create AVD with optimized settings
avdmanager create avd \
    -n "$AVD_NAME" \
    -k "system-images;android-34;google_apis;x86_64" \
    -d "pixel_7" \
    --force

echo ""
echo "=== AVD Created ==="
echo "AVD Name: $AVD_NAME"
echo ""
echo "To start the emulator with optimized settings:"
echo "  emulator -avd $AVD_NAME -no-audio -no-snapshot-load"
echo ""
echo "Or use the fix_emulator_issues.sh script after starting:"
echo "  ./scripts/fix_emulator_issues.sh"
echo ""
echo "Note: -no-audio flag suppresses audio completely"

