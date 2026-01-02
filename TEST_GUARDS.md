# Test Guards - Mandatory Rules

This document defines the **non-negotiable** test guards that must be enforced in all tests.

## ❌ NEVER ALLOW

### 1. Never play from a file that is still downloading
**Rule**: `CacheStatus.DOWNLOADING` → `getCachedUri()` must return `null`
**Test**: `CacheSelectorTest.getCachedUri_returns_null_when_cache_status_is_DOWNLOADING`
**Enforcement**: All playback code must check `cacheStatus != DOWNLOADING` before using local file

### 2. Never re-download if file exists and is COMPLETED
**Rule**: `CacheStatus.COMPLETED` → `downloadVideo()` must skip download
**Test**: `AtomicDownloadTest.single_download_enforcement_prevents_duplicate_downloads`
**Enforcement**: `DownloadManager.downloadVideo()` checks status before starting

### 3. Never delete cached files without schedule validation
**Rule**: Files referenced by active or upcoming schedules must not be deleted
**Test**: `CacheValidator.validateCache()` ensures files are only deleted if not referenced
**Enforcement**: `CacheValidator.cleanupCache()` only deletes files from `filesToDelete` set

### 4. Never play from remote URL when local file exists and is COMPLETED
**Rule**: If `getCachedUri()` returns non-null → must use local file
**Test**: `LocalPlaybackTest.getCachedUri_returns_file_URI_when_cache_is_COMPLETED`
**Enforcement**: Playback code must prefer `cachedUri` over `remoteUrl`

## ✅ ALWAYS ENFORCE

### 1. Always prefer local playback if file exists & complete
**Rule**: `CacheStatus.COMPLETED` + file exists + file size > 0 → use `file://` URI
**Test**: `LocalPlaybackTest.getCachedUri_returns_file_URI_when_cache_is_COMPLETED`
**Enforcement**: `CacheManager.getCachedUri()` validates all conditions

### 2. Always preload next asset before transition
**Rule**: Media items must be preloaded into ExoPlayer queue before playback starts
**Test**: `LoopContinuityTest.playlist_with_mixed_media_types_processes_correctly`
**Enforcement**: `AdStreamingScreen.MediaPlayer` preloads all MediaItems in `LaunchedEffect`

### 3. Always validate file before marking COMPLETED
**Rule**: File must exist, size > 0, extension matches, not being written
**Test**: `AtomicDownloadTest.cache_status_is_COMPLETED_only_after_validation`
**Enforcement**: `DownloadManager.validateAndCompleteDownload()` performs all checks

### 4. Always use atomic downloads
**Rule**: Download to temp file → validate → rename → mark COMPLETED
**Test**: `AtomicDownloadTest.download_status_prevents_playback_during_download`
**Enforcement**: `DownloadManager.downloadFile()` writes to final location only after validation

## Test Failure Conditions

If any of these tests fail, the build **MUST** fail:

1. **Partial Playback Prevention**: If `getCachedUri()` returns non-null when `status == DOWNLOADING`
2. **Duplicate Download Prevention**: If download starts when `status == COMPLETED`
3. **Local Playback Preference**: If remote URL is used when local file exists and is COMPLETED
4. **Cache Validation**: If file is marked COMPLETED without validation
5. **Schedule-Aware Deletion**: If cached file is deleted while referenced by active schedule

## Running Tests

```bash
# Run all unit tests
./gradlew test

# Run all instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest

# Run specific test class
./gradlew test --tests "com.example.signoutwardv2.data.MediaTypeDetectorTest"

# Run with coverage
./gradlew test jacocoTestReport
```

## CI/CD Integration

These tests should be run in CI/CD pipeline:

1. **Unit Tests**: Run on every commit (fast, no device needed)
2. **Integration Tests**: Run on pull requests (requires Supabase test environment)
3. **Playback Tests**: Run on nightly builds (requires device/emulator)

## Test Data Requirements

Some integration tests require test data in Supabase:

- `device_pairing_codes` table: At least one unused test code
- `screens` table: At least one test screen ID
- `playlists` table: At least one test playlist with videos
- `schedules` table: Optional test schedules

See individual test files for specific test data requirements.

