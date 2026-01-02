# Automated Test Function Renaming Script

## Overview

This script automatically renames Kotlin test functions with spaces (backtick syntax) to camelCase, fixing DEX build failures while preserving readability through comments.

## Problem

Kotlin test functions like `fun `test name`()` create class names with spaces, which DEX format doesn't support until version 040. This causes build failures:

```
ERROR: Space characters in SimpleName 'playlist sync fetches active playlist' are not allowed prior to DEX version 040
```

## Solution

The script:
1. **Finds** all test functions with backticks (spaces in names)
2. **Converts** them to camelCase (e.g., `playlistSyncFetchesActivePlaylist`)
3. **Preserves** readability by adding comments with original names
4. **Creates backups** (optional) before modifying files

## Usage

### Step 1: Preview Changes (Dry Run)

```bash
cd /Users/theshire2/Desktop/SignOutward/SignOutwardv2
python3 rename_test_functions.py --dry-run
```

This shows what would be changed without modifying any files.

### Step 2: Apply Changes

```bash
# With backup files
python3 rename_test_functions.py --backup

# Without backups (faster)
python3 rename_test_functions.py
```

### Step 3: Verify Build

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew clean
./gradlew :app:dexBuilderDebugAndroidTest
```

Expected output: `BUILD SUCCESSFUL`

## Example Transformation

**Before:**
```kotlin
@Test
fun `playlist sync fetches active playlist`() = runBlocking {
    // test code...
}
```

**After:**
```kotlin
    // Test: playlist sync fetches active playlist
    @Test
    fun playlistSyncFetchesActivePlaylist() = runBlocking {
        // test code...
    }
```

## Features

- ✅ **Safe**: Creates backups before modifying (with `--backup` flag)
- ✅ **Preview**: Dry-run mode shows changes without applying them
- ✅ **Readable**: Adds comments with original test names
- ✅ **Comprehensive**: Processes all test files in `app/src/androidTest`
- ✅ **Reversible**: Backup files can be restored if needed

## Command Options

```bash
python3 rename_test_functions.py [OPTIONS]

Options:
    --dry-run    Show what would be changed without modifying files
    --backup     Create .bak backup files before modifying
```

## Files Processed

The script processes all `.kt` files in:
- `app/src/androidTest/java/com/example/signoutwardv2/integration/`
- `app/src/androidTest/java/com/example/signoutwardv2/playback/`

## Verification Checklist

After running the script:

- [ ] All test functions renamed to camelCase
- [ ] Comments added with original readable names
- [ ] Build succeeds: `./gradlew :app:dexBuilderDebugAndroidTest`
- [ ] Tests still run: `./gradlew :app:connectedDebugAndroidTest` (if device connected)
- [ ] No compilation errors

## Troubleshooting

### Issue: "No module named 'pathlib'"
**Solution:** Python 3.4+ is required. Check version: `python3 --version`

### Issue: "Permission denied"
**Solution:** Make script executable: `chmod +x rename_test_functions.py`

### Issue: Build still fails after renaming
**Solution:**
1. Clean build: `./gradlew clean`
2. Check for any remaining backtick functions: `grep -r "fun \`" app/src/androidTest`
3. Verify Java version: `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`

### Issue: Need to restore from backup
**Solution:**
```bash
# Restore a single file
cp app/src/androidTest/.../TestFile.kt.bak app/src/androidTest/.../TestFile.kt

# Restore all backups
find app/src/androidTest -name "*.bak" -exec sh -c 'mv "$1" "${1%.bak}"' _ {} \;
```

## Future Prevention

To prevent this issue in future tests:

1. **Use camelCase** for test function names:
   ```kotlin
   @Test
   fun playlistSyncFetchesActivePlaylist() = runBlocking {
       // ...
   }
   ```

2. **Add comments** for readability:
   ```kotlin
   // Test: playlist sync fetches active playlist
   @Test
   fun playlistSyncFetchesActivePlaylist() = runBlocking {
       // ...
   }
   ```

3. **Run the script** periodically to catch any new backtick functions:
   ```bash
   python3 rename_test_functions.py --dry-run
   ```

## Summary

- **38 test functions** will be renamed across **11 test files**
- **Readability preserved** through comments
- **DEX build will succeed** after renaming
- **No test logic changes** - only function names

