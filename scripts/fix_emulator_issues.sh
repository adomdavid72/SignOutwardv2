#!/bin/bash
# Emulator Configuration Fixes for Binder IPC and Audio Issues
# Run this script to configure the emulator to reduce system errors

set -e

echo "=== Emulator System Fixes ==="
echo "This script configures the emulator to reduce Binder IPC and audio errors"
echo ""

# Get the running emulator device
DEVICE=$(adb devices | grep "emulator" | head -1 | cut -f1)

if [ -z "$DEVICE" ]; then
    echo "ERROR: No emulator found. Please start an emulator first."
    exit 1
fi

echo "Found emulator: $DEVICE"
echo ""

# A. Binder IPC Fixes
echo "=== A. Applying Binder IPC Fixes ==="

# Increase Binder transaction buffer size
adb -s $DEVICE shell "setprop debug.binder.transaction_buffer_size 1024"
echo "✓ Increased Binder transaction buffer size"

# Disable Binder debugging (reduces log spam)
adb -s $DEVICE shell "setprop debug.binder.dump_on_error 0"
echo "✓ Disabled Binder error dumps"

# Set Binder transaction limit
adb -s $DEVICE shell "setprop debug.binder.max_transaction_size 1048576"
echo "✓ Set Binder max transaction size"

# Reduce Binder transaction failures by increasing buffer
adb -s $DEVICE shell "setprop persist.vendor.binder.max_transaction_size 1048576"
echo "✓ Set persistent Binder transaction size"

echo ""

# B. Audio HAL Fixes
echo "=== B. Applying Audio HAL Fixes ==="

# Disable audio output (suppress PCM writes)
adb -s $DEVICE shell "setprop ro.audio.silent 1"
echo "✓ Set audio to silent mode"

# Disable audio service logging
adb -s $DEVICE shell "setprop log.tag.AudioService WARN"
echo "✓ Reduced audio service logging"

# Disable audio HAL logging
adb -s $DEVICE shell "setprop log.tag.audio_hw WARN"
echo "✓ Reduced audio HAL logging"

# Set audio policy to suppress output
adb -s $DEVICE shell "setprop ro.audio.offload.disable 1"
echo "✓ Disabled audio offload"

# Disable audio effects (reduces HAL calls)
adb -s $DEVICE shell "setprop ro.audio.effects.disable 1"
echo "✓ Disabled audio effects"

echo ""

# C. System Service Fixes
echo "=== C. Applying System Service Fixes ==="

# Reduce Google Play Services errors
adb -s $DEVICE shell "setprop debug.gms.persistent.log_level WARN"
echo "✓ Reduced GMS persistent logging"

# Reduce system_server errors
adb -s $DEVICE shell "setprop debug.system_server.log_level WARN"
echo "✓ Reduced system_server logging"

# Reduce Settings intelligence errors
adb -s $DEVICE shell "setprop debug.settings.intelligence.log_level WARN"
echo "✓ Reduced Settings intelligence logging"

echo ""

# D. Verification
echo "=== D. Verification ==="
echo "Checking current settings..."

echo ""
echo "Binder settings:"
adb -s $DEVICE shell "getprop debug.binder.transaction_buffer_size"
adb -s $DEVICE shell "getprop debug.binder.max_transaction_size"

echo ""
echo "Audio settings:"
adb -s $DEVICE shell "getprop ro.audio.silent"
adb -s $DEVICE shell "getprop ro.audio.offload.disable"

echo ""
echo "=== Fixes Applied ==="
echo "Restart the emulator for all changes to take effect:"
echo "  adb -s $DEVICE reboot"
echo ""
echo "Or restart the app to see immediate improvements."
echo ""
echo "To verify fixes are working, check logs:"
echo "  adb -s $DEVICE logcat | grep -E 'Binder|pcmWrite|audio'"
echo ""
echo "Expected: Reduced or no Binder -22 errors and pcmWrite failures"

