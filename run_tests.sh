#!/bin/bash
# Script to run instrumented tests on Android emulator/device
# Usage: ./run_tests.sh

set -e

# Set Java 17
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

echo "=========================================="
echo "Instrumented Test Runner"
echo "=========================================="
echo ""

# Step 1: Clean build
echo "Step 1: Cleaning build..."
./gradlew clean
echo "✓ Clean complete"
echo ""

# Step 2: Build test APK
echo "Step 2: Building test APK..."
./gradlew :app:assembleDebugAndroidTest
echo "✓ Test APK built"
echo ""

# Step 3: Check for connected devices
echo "Step 3: Checking for connected devices..."
DEVICES=$(adb devices | grep -v "List" | grep "device" | wc -l | tr -d ' ')
if [ "$DEVICES" -eq "0" ]; then
    echo "⚠ No devices connected!"
    echo "Please start an emulator or connect a physical device."
    echo ""
    echo "To start emulator:"
    echo "  emulator -avd Medium_Tablet2 &"
    echo ""
    echo "To check devices:"
    echo "  adb devices"
    exit 1
fi
echo "✓ Found $DEVICES device(s)"
echo ""

# Step 4: Wait for device to be ready
echo "Step 4: Waiting for device to be ready..."
adb wait-for-device
BOOT_COMPLETE=$(adb shell getprop sys.boot_completed)
if [ "$BOOT_COMPLETE" != "1" ]; then
    echo "⚠ Device not fully booted. Waiting..."
    sleep 5
    BOOT_COMPLETE=$(adb shell getprop sys.boot_completed)
    if [ "$BOOT_COMPLETE" != "1" ]; then
        echo "⚠ Device still not ready. Please wait and try again."
        exit 1
    fi
fi
echo "✓ Device ready"
echo ""

# Step 5: Test network connectivity
echo "Step 5: Testing network connectivity..."
NETWORK_TEST=$(adb shell ping -c 1 8.8.8.8 2>&1 | grep -c "1 packets transmitted" || echo "0")
if [ "$NETWORK_TEST" -eq "0" ]; then
    echo "⚠ Network connectivity test failed"
    echo "Tests requiring network may fail or be skipped"
else
    echo "✓ Network connectivity OK"
fi
echo ""

# Step 6: Run tests
echo "Step 6: Running instrumented tests..."
echo "This may take several minutes..."
echo ""
./gradlew :app:connectedDebugAndroidTest
echo ""

# Step 7: Show results
echo "=========================================="
echo "Test Results"
echo "=========================================="
echo ""
echo "HTML Report:"
echo "  open app/build/reports/androidTests/connected/debug/index.html"
echo ""
echo "Test Summary:"
cat app/build/reports/androidTests/connected/debug/index.html 2>/dev/null | grep -o "tests=\"[0-9]*\"" | head -1 || echo "  (Report not available)"
echo ""

