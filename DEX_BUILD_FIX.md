# DEX Build Failure Fix - Step-by-Step Instructions

## Problem
The Android test build fails with DEX errors because Kotlin test function names with spaces (backtick syntax) create class names with spaces, which DEX format doesn't support until version 040.

**Error Message:**
```
ERROR: Space characters in SimpleName 'playlist sync fetches active playlist' are not allowed prior to DEX version 040
```

## Root Cause
- Kotlin test functions like `fun `test name`()` create class names with spaces
- DEX format doesn't allow spaces in class/method names until DEX version 040
- DEX 040 requires API level 40+ or explicit configuration

## Solution
Configure the test variant to use DEX version 040. Since AGP 8.x removed some APIs, we'll use a workaround by configuring the test variant's minSdk.

## Step-by-Step Fix (macOS)

### Step 1: Clean Build Cache
```bash
cd /Users/theshire2/Desktop/SignOutward/SignOutwardv2
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew clean
```

### Step 2: Update build.gradle.kts
Add the following configuration to enable DEX 040 for test builds:

```kotlin
android {
    // ... existing configuration ...
    
    // Configure test variant to use minSdk 40 for DEX 040 support
    // This enables spaces in class names from Kotlin backtick test functions
    testBuildType = "debug"
    
    // Configure test variant's minSdk to enable DEX 040
    // Note: This only affects test APKs, main app still uses minSdk 24
    applicationVariants.all {
        testVariant?.let { variant ->
            variant.mergedFlavor.minSdk = 40
        }
    }
}
```

**However**, if the above doesn't work due to API changes, use this alternative:

### Alternative Solution: Rename Test Functions (If DEX 040 configuration fails)

If configuring DEX 040 proves difficult, rename test functions to use underscores instead of spaces:

**Before:**
```kotlin
@Test
fun `playlist loops continuously without significant buffer lag`() = runBlocking {
    // ...
}
```

**After:**
```kotlin
@Test
fun playlistLoopsContinuouslyWithoutSignificantBufferLag() = runBlocking {
    // ...
}
```

### Step 3: Verify Dependencies
Ensure multidex is enabled (already added in build.gradle.kts):
```kotlin
defaultConfig {
    // ...
    multiDexEnabled = true
}
```

### Step 4: Rebuild and Test
```bash
# Clean build
./gradlew clean

# Build test APK
./gradlew :app:assembleDebugAndroidTest

# Verify DEX build succeeds
./gradlew :app:dexBuilderDebugAndroidTest

# Run tests (if emulator/device is connected)
./gradlew :app:connectedDebugAndroidTest
```

## Verification

### Check Build Success
```bash
./gradlew :app:dexBuilderDebugAndroidTest 2>&1 | grep -i "error\|successful\|BUILD"
```

Expected output: `BUILD SUCCESSFUL`

### Check Test APK
```bash
ls -lh app/build/outputs/apk/androidTest/debug/
```

Should show `app-debug-androidTest.apk` file.

## Alternative: Use JUnit Display Names

If DEX 040 configuration is not possible, use JUnit's `@DisplayName` annotation to keep readable test names:

```kotlin
@Test
@DisplayName("playlist loops continuously without significant buffer lag")
fun testPlaylistLoopPerformance() = runBlocking {
    // ...
}
```

## Troubleshooting

### Issue: "Unresolved reference: minSdk"
**Solution:** The API may have changed. Use the alternative solution (rename test functions) or check AGP version compatibility.

### Issue: "DEX version 040 not supported"
**Solution:** Ensure you're using AGP 8.0+ and the test device/emulator supports API 40+.

### Issue: Tests still fail after fix
**Solution:** 
1. Clean build: `./gradlew clean`
2. Invalidate caches in Android Studio
3. Rebuild: `./gradlew :app:assembleDebugAndroidTest`

## Prevention

To prevent similar issues in the future:
1. **Use underscores in test function names** instead of spaces (backticks)
2. **Use `@DisplayName`** for readable test names
3. **Keep AGP and dependencies updated**
4. **Test on multiple API levels** during development

## Current Status

- ✅ Multidex enabled
- ✅ Build configuration updated
- ⚠️ DEX 040 configuration may need adjustment based on AGP version
- ✅ Alternative solution (rename functions) available if needed

