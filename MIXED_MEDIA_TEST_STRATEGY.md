# Mixed Media Playback Test Strategy

## Overview

This document describes the comprehensive test strategy for validating mixed media playback (images + videos) in the Android DOOH player. All tests are **read-only** and use **mocked data** to avoid Supabase writes.

## Test Structure

### 1. TestMediaRepository (`app/src/main/java/com/example/signoutwardv2/data/test/TestMediaRepository.kt`)

**Purpose**: Provides deterministic mixed media playlists for testing without network dependencies.

**Features**:
- Returns pre-configured playlists with real URLs (cached in-memory)
- No network calls after initial hydration
- Read-only - no Supabase writes
- Accessible from both unit tests and instrumented tests

**Available Playlists**:
- `getMixedMediaPlaylist()`: 2 videos (mp4) + 2 images (jpg/webp) in alternating order
- `getImageOnlyPlaylist()`: 3 images (jpg, png, webp)
- `getVideoOnlyPlaylist()`: 3 videos (mp4, webm, mp4)
- `getMixedWithUnsupportedPlaylist()`: Mixed playlist with unsupported files
- `getPlaylistWithEmptyUrls()`: Edge case with empty/null URLs

### 2. Unit Tests (`app/src/test`)

#### PlaylistProcessorTest (`app/src/test/java/com/example/signoutwardv2/data/PlaylistProcessorTest.kt`)

**Tests**:
- ✅ `processPlaylist separates supported and unsupported files`
- ✅ `processPlaylist returns empty supported list when all files are unsupported`
- ✅ `processPlaylist handles empty playlist`
- ✅ `processPlaylist includes both images and videos as supported`
- ✅ `processPlaylist preserves order for mixed media playlists` (NEW)
- ✅ `processPlaylist correctly separates supported and unsupported in mixed playlist` (NEW)
- ✅ `processPlaylist filters empty URLs to unsupported` (NEW)
- ✅ `processPlaylist handles image-only playlist correctly` (NEW)
- ✅ `processPlaylist handles video-only playlist correctly` (NEW)
- ✅ `processPlaylist maintains original order when unsupported items are filtered` (NEW)

**Validates**:
- Images and videos are correctly classified
- Supported vs unsupported formats are separated
- Playback order is preserved
- Empty/null URLs are handled gracefully

#### CacheSelectorTest (`app/src/test/java/com/example/signoutwardv2/cache/CacheSelectorTest.kt`)

**Tests**:
- ✅ `getCachedUri returns null when cache status is NOT_STARTED`
- ✅ `getCachedUri returns null when cache status is DOWNLOADING`
- ✅ `getCachedUri returns null when cache status is FAILED`
- ✅ `getCachedUri returns null when file does not exist`
- ✅ `getCachedUri returns null when file is empty`
- ✅ `getCachedUri returns file URI when file exists and is completed`
- ✅ `getCachedUri returns null when file size mismatch`
- ✅ `getCachedUri returns null when file extension mismatch`
- ✅ `isCached returns false when status is DOWNLOADING`
- ✅ `isCached returns true only when COMPLETED and file is valid`
- ✅ `getCachedUri returns null when localFilePath is null` (NEW)
- ✅ `getCachedUri returns null when metadata is null` (NEW)
- ✅ `getCachedUri returns file URI for image files when COMPLETED` (NEW)
- ✅ `getCachedUri returns file URI for webm video when COMPLETED` (NEW)
- ✅ `getCachedUri returns null when file size is zero` (NEW)
- ✅ `getCachedUri validates extension case-insensitively` (NEW)
- ✅ `isCached returns false when file does not exist` (NEW)
- ✅ `isCached returns false when file is empty` (NEW)
- ✅ `isCached returns false when metadata is null` (NEW)
- ✅ `isCached returns false when localFilePath is empty` (NEW)

**Validates**:
- COMPLETED status is required for playback
- File existence and size validation
- Extension matching (case-insensitive)
- Partial downloads are never used

### 3. Instrumented Tests (`app/src/androidTest`)

#### MixedMediaPlaybackDeterministicTest (`app/src/androidTest/java/com/example/signoutwardv2/playback/MixedMediaPlaybackDeterministicTest.kt`)

**Purpose**: Deterministic tests using TestMediaRepository (no Supabase writes)

**Tests**:
- ✅ `mixedPlaylistProcessesBothImagesAndVideos`
- ✅ `playbackOrderIsPreservedForMixedPlaylists`
- ✅ `imagesAreNotSkippedInMixedPlaylists`
- ✅ `unsupportedFilesAreFilteredWithoutBreakingPlaylist`
- ✅ `emptyUrlsAreFilteredToUnsupported`

**Validates**:
- Mixed playlists process both images and videos correctly
- Playback order is preserved
- Images are not skipped
- Unsupported files are filtered without breaking playlist

#### PlaybackGuardEnforcementTest (`app/src/androidTest/java/com/example/signoutwardv2/playback/PlaybackGuardEnforcementTest.kt`)

**Purpose**: Validates playback guard enforcement (no partial playback)

**Tests**:
- ✅ `playbackWaitsForFileReadiness`
- ✅ `cachedFilesArePreferredOnceFullyDownloaded`
- ✅ `partialDownloadsAreNeverUsedForPlayback`
- ✅ `nextPlaybackUsesLocalFileAfterCompletion`
- ✅ `emptyFilesAreNeverUsedForPlayback`
- ✅ `fileSizeMismatchPreventsPlayback`

**Validates**:
- Playback waits for COMPLETED status
- Cached files are preferred once fully downloaded
- Partial downloads are never used
- Empty files are rejected
- File size mismatches prevent playback

### 4. Network-Dependent Tests (Marked as @Ignore)

#### SupabasePairingTest (`app/src/androidTest/java/com/example/signoutwardv2/integration/SupabasePairingTest.kt`)

**Status**: All tests marked with `@Ignore` to avoid production table writes

**Tests**:
- ⏸️ `validatepairingcodeUpdatesScreensIsPairedWhenSuccessful` (@Ignore)
- ⏸️ `validatepairingcodeFailsForInvalidCode` (@Ignore)
- ⏸️ `validatepairingcodeFailsForAlreadyUsedCode` (@Ignore)

**Reason**: These tests require Supabase writes to production tables, which violates the read-only constraint.

## Test Execution

### Unit Tests
```bash
./gradlew test
```

### Instrumented Tests
```bash
./gradlew connectedAndroidTest
```

### Specific Test Classes
```bash
# PlaylistProcessor tests
./gradlew test --tests "com.example.signoutwardv2.data.PlaylistProcessorTest"

# CacheSelector tests
./gradlew test --tests "com.example.signoutwardv2.cache.CacheSelectorTest"

# Mixed media playback tests
./gradlew connectedAndroidTest --tests "com.example.signoutwardv2.playback.MixedMediaPlaybackDeterministicTest"

# Playback guard tests
./gradlew connectedAndroidTest --tests "com.example.signoutwardv2.playback.PlaybackGuardEnforcementTest"
```

## Success Criteria

✅ **Mixed playlists process correctly**: Both images and videos are classified and included  
✅ **Playback order preserved**: Original order is maintained after processing  
✅ **Images not skipped**: All images in original playlist appear in processed list  
✅ **Cached content preferred**: Local files are used once COMPLETED  
✅ **Partial playback prevented**: DOWNLOADING status never returns cached URI  
✅ **Empty files rejected**: Zero-byte files are never used for playback  
✅ **Size validation**: File size mismatches prevent playback  
✅ **Extension validation**: Extension mismatches prevent playback  
✅ **No Supabase writes**: All tests are read-only  

## Constraints Met

✅ **No Supabase test DB**: Uses TestMediaRepository with mocked data  
✅ **No production writes**: All tests are read-only  
✅ **No schema changes**: No modifications to Supabase schema  
✅ **Mocked repositories**: TestMediaRepository provides deterministic data  
✅ **Existing URLs only**: Uses real Supabase URLs but cached in-memory  
✅ **Network-dependent tests skipped**: SupabasePairingTest marked @Ignore  

## Coverage

### Playlist Processing
- ✅ Mixed media (images + videos)
- ✅ Image-only playlists
- ✅ Video-only playlists
- ✅ Unsupported files filtering
- ✅ Empty/null URL handling
- ✅ Order preservation

### Cache Selection
- ✅ COMPLETED status requirement
- ✅ File existence validation
- ✅ File size validation (> 0, matches expected)
- ✅ Extension matching (case-insensitive)
- ✅ Partial download prevention
- ✅ Empty file rejection

### Playback Guards
- ✅ DOWNLOADING → null (prevents partial playback)
- ✅ COMPLETED → local URI (prefers cached)
- ✅ Empty files → null (prevents playback)
- ✅ Size mismatch → null (prevents playback)
- ✅ Extension mismatch → null (prevents playback)

## Notes

- All tests use deterministic data from `TestMediaRepository`
- No network calls occur during test execution
- Tests can run offline
- Tests are reproducible and deterministic
- No production data is modified

