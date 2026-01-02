# Test Structure

This document describes the comprehensive test suite for the Android tablet ad streaming app.

## Test Organization

```
app/src/
├── test/                          # Unit tests (run on JVM, no device needed)
│   └── java/com/example/signoutwardv2/
│       ├── data/
│       │   ├── MediaTypeDetectorTest.kt      # File type detection
│       │   └── PlaylistProcessorTest.kt       # Playlist filtering
│       └── cache/
│           └── CacheSelectorTest.kt            # Cache selection logic
│
└── androidTest/                   # Instrumented tests (run on device/emulator)
    └── java/com/example/signoutwardv2/
        ├── integration/
        │   ├── SupabasePairingTest.kt          # Device pairing integration
        │   └── PlaylistSyncTest.kt             # Playlist sync integration
        └── playback/
            ├── LocalPlaybackTest.kt             # Local vs remote playback
            ├── LoopContinuityTest.kt            # Seamless looping
            └── AtomicDownloadTest.kt            # Atomic download enforcement
```

## Test Categories

### 1. Unit Tests (`/test`)

**Purpose**: Validate logic-level decisions without UI or network dependencies.

#### `MediaTypeDetectorTest.kt`
- Tests file type detection by extension and MIME type
- Validates supported image formats (jpg, png, webp, gif, bmp, tiff)
- Validates supported video formats (mp4, mov, avi, mkv, webm, flv, wmv)
- Tests unsupported file handling

#### `PlaylistProcessorTest.kt`
- Tests playlist filtering (supported vs unsupported files)
- Validates empty playlist handling
- Tests mixed media type playlists (images + videos)

#### `CacheSelectorTest.kt`
- **Critical**: Tests cache selection logic
- **Rule**: "If file exists locally AND download completed → return local path"
- Tests all cache status scenarios:
  - `NOT_STARTED` → returns null
  - `DOWNLOADING` → returns null (prevents partial playback)
  - `COMPLETED` → returns file:// URI
  - `FAILED` → returns null
- Tests file validation (exists, size > 0, extension match)

### 2. Integration Tests (`/androidTest/integration`)

**Purpose**: Ensure correct Supabase database mapping and API integration.

#### `SupabasePairingTest.kt`
- Tests device pairing flow
- Validates: `device_pairing_codes.is_used = true` after pairing
- Validates: `screens.is_paired = true` after pairing
- Tests invalid/used code rejection

#### `PlaylistSyncTest.kt`
- Tests playlist fetching from Supabase
- Validates playlist state updates
- Tests schedule-aware playlist resolution

### 3. Playback Tests (`/androidTest/playback`)

**Purpose**: Stop buffering, gaps, and partial playback issues.

#### `LocalPlaybackTest.kt`
- **Critical**: Tests local vs remote playback selection
- **Rule**: Cached files are always played via local file paths
- **Rule**: Partially downloaded files are never played
- Tests:
  - `getCachedUri()` returns `file://` URI when `COMPLETED`
  - `getCachedUri()` returns `null` when `DOWNLOADING`
  - Remote URL fallback when cache unavailable

#### `LoopContinuityTest.kt`
- Tests seamless playlist looping
- Validates mixed media type handling (videos + images)
- Tests empty playlist graceful handling
- Validates cache status prevents partial playback

#### `AtomicDownloadTest.kt`
- **Critical**: Enforces atomic download rules
- **Rule**: Download to temp file → validate → rename → mark COMPLETED
- Tests:
  - Single download enforcement (no duplicate downloads)
  - Download status prevents playback during download
  - Failed downloads can be retried
  - Cache status is COMPLETED only after validation

## Mandatory Test Guards

See `TEST_GUARDS.md` for complete list of non-negotiable rules.

### Key Rules Enforced:

1. ❌ **Never play from a file that is still downloading**
   - Test: `CacheSelectorTest.getCachedUri_returns_null_when_cache_status_is_DOWNLOADING`
   - Enforcement: `CacheStatus.DOWNLOADING` → `getCachedUri()` returns `null`

2. ❌ **Never re-download if file exists and is COMPLETED**
   - Test: `AtomicDownloadTest.single_download_enforcement_prevents_duplicate_downloads`
   - Enforcement: `DownloadManager.downloadVideo()` checks status before starting

3. ✅ **Always prefer local playback if file exists & complete**
   - Test: `LocalPlaybackTest.getCachedUri_returns_file_URI_when_cache_is_COMPLETED`
   - Enforcement: `CacheManager.getCachedUri()` validates all conditions

4. ✅ **Always validate file before marking COMPLETED**
   - Test: `AtomicDownloadTest.cache_status_is_COMPLETED_only_after_validation`
   - Enforcement: `DownloadManager.validateAndCompleteDownload()` performs all checks

## Running Tests

### Unit Tests (Fast, No Device Needed)
```bash
./gradlew test
```

### Instrumented Tests (Requires Device/Emulator)
```bash
./gradlew connectedAndroidTest
```

### Specific Test Class
```bash
./gradlew test --tests "com.example.signoutwardv2.data.MediaTypeDetectorTest"
./gradlew connectedAndroidTest --tests "com.example.signoutwardv2.playback.LocalPlaybackTest"
```

### With Coverage
```bash
./gradlew test jacocoTestReport
```

## Test Dependencies

Added to `build.gradle.kts`:
- `junit:4.13.2` - Unit testing framework
- `mockito-core:5.11.0` - Mocking framework
- `mockito-inline:5.2.0` - Inline mocking (for final classes)
- `kotlinx-coroutines-test:1.7.3` - Coroutine testing utilities

## CI/CD Integration

### Recommended Pipeline:

1. **Unit Tests**: Run on every commit
   - Fast execution (< 1 minute)
   - No external dependencies
   - Catches logic errors early

2. **Integration Tests**: Run on pull requests
   - Requires Supabase test environment
   - Validates database integration
   - May require test data setup

3. **Playback Tests**: Run on nightly builds
   - Requires device/emulator
   - Validates critical playback scenarios
   - Catches regressions in caching/playback logic

## Test Data Requirements

Some integration tests require test data in Supabase:

- **SupabasePairingTest**: Requires unused pairing code in `device_pairing_codes` table
- **PlaylistSyncTest**: Requires test screen ID in `screens` table
- **PlaylistSyncTest**: Requires test playlist with videos in `playlists` table

See individual test files for specific requirements.

## Coverage Goals

- **Unit Tests**: > 80% coverage for logic classes
  - `MediaTypeDetector`: 100%
  - `PlaylistProcessor`: 100%
  - `CacheManager`: > 80% (cache selection logic)

- **Integration Tests**: Critical paths only
  - Device pairing flow
  - Playlist sync flow

- **Playback Tests**: All critical scenarios
  - Local vs remote playback
  - Partial download prevention
  - Seamless looping

## Future Test Additions

Consider adding:

1. **UI Tests**: Compose UI testing for pairing/playback screens
2. **Performance Tests**: Cache performance, download speed
3. **Network Tests**: Offline handling, retry logic
4. **Schedule Tests**: Schedule evaluation logic (if extracted to separate class)

