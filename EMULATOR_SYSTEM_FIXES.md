# Emulator System Fixes - Binder IPC and Audio Issues

## Overview

This document provides solutions for Android emulator system-level issues:
- **Binder IPC failures** (-22 errors) from system services
- **Audio PCM write failures** in the audio HAL

**Important**: These are **emulator/system issues**, not app code issues. The fixes provided are **workarounds** that can be applied via emulator configuration and app-level mitigations.

## Solutions Provided

### A. Emulator Configuration Scripts

#### 1. `scripts/fix_emulator_issues.sh`
**Purpose**: Configures running emulator to reduce system errors.

**What it does**:
- Increases Binder transaction buffer size
- Disables Binder error dumps (reduces log spam)
- Sets Binder transaction limits
- Disables audio output (suppresses PCM writes)
- Reduces audio service logging
- Reduces system service logging

**Usage**:
```bash
# Make executable
chmod +x scripts/fix_emulator_issues.sh

# Run (requires emulator to be running)
./scripts/fix_emulator_issues.sh

# Restart emulator for all changes
adb reboot
```

#### 2. `scripts/create_emulator_config.sh`
**Purpose**: Creates a new emulator AVD with optimized settings.

**Usage**:
```bash
chmod +x scripts/create_emulator_config.sh
./scripts/create_emulator_config.sh

# Start emulator with no audio
emulator -avd SignOutward_Optimized -no-audio -no-snapshot-load
```

### B. App-Level Workarounds

#### 1. `EmulatorWorkarounds.kt`
**Purpose**: App-level workarounds to reduce IPC calls and suppress audio.

**Features**:
- Detects if running on emulator
- Suppresses audio output on emulator
- Attempts to optimize Binder transactions (may require root)

**Integration**: Already integrated into `MainActivity.onCreate()`

### C. Manual ADB Commands

If scripts don't work, apply fixes manually:

```bash
# Get device ID
DEVICE=$(adb devices | grep "emulator" | head -1 | cut -f1)

# Binder IPC Fixes
adb -s $DEVICE shell "setprop debug.binder.transaction_buffer_size 1024"
adb -s $DEVICE shell "setprop debug.binder.max_transaction_size 1048576"
adb -s $DEVICE shell "setprop debug.binder.dump_on_error 0"

# Audio HAL Fixes
adb -s $DEVICE shell "setprop ro.audio.silent 1"
adb -s $DEVICE shell "setprop ro.audio.offload.disable 1"
adb -s $DEVICE shell "setprop ro.audio.effects.disable 1"

# Reduce Logging
adb -s $DEVICE shell "setprop log.tag.AudioService WARN"
adb -s $DEVICE shell "setprop log.tag.audio_hw WARN"
adb -s $DEVICE shell "setprop debug.gms.persistent.log_level WARN"

# Restart emulator
adb -s $DEVICE reboot
```

## Verification Steps

### 1. Check Binder Settings
```bash
adb shell "getprop debug.binder.transaction_buffer_size"
adb shell "getprop debug.binder.max_transaction_size"
```
Expected: `1024` and `1048576` (or similar)

### 2. Check Audio Settings
```bash
adb shell "getprop ro.audio.silent"
adb shell "getprop ro.audio.offload.disable"
```
Expected: `1` for both

### 3. Monitor Logs
```bash
# Check for Binder errors (should be reduced)
adb logcat | grep -E "Binder.*-22"

# Check for PCM write failures (should be eliminated)
adb logcat | grep -E "pcmWrite.*failure"

# Check audio service logs (should be minimal)
adb logcat | grep -E "AudioService|audio_hw"
```

**Expected Result**: 
- Reduced or no Binder -22 errors
- No pcmWrite failures
- Minimal audio service logging

## Limitations

### What CANNOT Be Fixed in App Code

1. **Binder IPC Errors**: These occur in system services (GMS, system_server) and cannot be patched from app code
2. **Audio HAL Failures**: These occur in the hardware abstraction layer and cannot be fixed from app code
3. **System Service Errors**: These require system-level modifications

### What CAN Be Done

1. **Emulator Configuration**: Set system properties via ADB
2. **App-Level Workarounds**: Suppress audio, reduce IPC calls
3. **Log Filtering**: Reduce log spam by setting log levels
4. **Emulator Settings**: Use `-no-audio` flag when starting emulator

## Rollback Steps

If fixes cause issues:

```bash
# Reset Binder settings
adb shell "setprop debug.binder.transaction_buffer_size \"\""
adb shell "setprop debug.binder.max_transaction_size \"\""

# Reset audio settings
adb shell "setprop ro.audio.silent 0"
adb shell "setprop ro.audio.offload.disable 0"

# Restart emulator
adb reboot
```

## Alternative: Use Physical Device

For production testing, use a physical Android device instead of emulator. Physical devices don't have these emulator-specific issues.

## Summary

**Applied Fixes**:
- ✅ Emulator configuration scripts created
- ✅ App-level workarounds implemented
- ✅ Audio suppression on emulator
- ✅ Binder transaction optimization attempts
- ✅ Logging reduction

**Expected Results**:
- Reduced Binder -22 errors
- Eliminated pcmWrite failures
- Reduced log spam
- App functionality preserved

**Note**: These are workarounds, not true fixes. The underlying issues are in the emulator/system and would require Android framework modifications to truly fix.

