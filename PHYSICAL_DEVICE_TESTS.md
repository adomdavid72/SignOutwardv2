# Physical Device Tests for Playlist Playback

## Overview

Comprehensive instrumented tests for validating playlist playback behavior on physical Android devices. These tests verify observable behaviors including loop performance, dynamic updates, mixed media support, edge case handling, and metrics logging.

## Test Files

### 1. `PlaylistLoopPerformanceTest.kt`
**Purpose**: Validate seamless playlist looping without buffer lag

**Tests**:
- `playlist loops continuously without significant buffer lag` - Measures buffer delays between loop iterations (max 500ms threshold)
- `measure buffer time between media items` - Tracks transitions between individual media items

**Key Metrics**:
- Loop completion times
- Buffer delay measurements
- Maximum acceptable delay: 500ms

**Usage**:
```bash
./gradlew connectedAndroidTest --tests "com.example.signoutwardv2.playback.PlaylistLoopPerformanceTest"
```

### 2. `DynamicPlaylistUpdateTest.kt`
**Purpose**: Verify dynamic playlist updates from web app

**Tests**:
- `new videos appear in rotation after playlist update` - Detects new videos added via web app within sync interval (30s)
- `new images appear and display correctly after playlist update` - Verifies images are added and displayed correctly
- `playlist continues playing without crashing after update` - Validates crash resilience during updates

**Requirements**:
- Manual playlist updates via web app during test execution
- Tests wait for sync interval (30s) to detect changes

**Usage**:
```bash
./gradlew connectedAndroidTest --tests "com.example.signoutwardv2.playback.DynamicPlaylistUpdateTest"
```

**Test Procedure**:
1. Start test
2. When prompted, update playlist via web app to add new videos/images
3. Test waits 35 seconds for sync interval
4. Test verifies new media appears in playlist

### 3. `MixedMediaPlaybackTest.kt`
**Purpose**: Validate mixed media (videos + images) playback

**Tests**:
- `mixed playlist displays both videos and images` - Verifies both media types are detected and processed
- `images are not skipped in mixed playlists` - Ensures images are included, not filtered out
- `different media order sequences work correctly` - Tests various ordering (video→image→video→image)

**Key Validations**:
- Media type detection (VIDEO vs IMAGE)
- Processing order preservation
- No image skipping

**Usage**:
```bash
./gradlew connectedAndroidTest --tests "com.example.signoutwardv2.playback.MixedMediaPlaybackTest"
```

### 4. `PlaybackEdgeCasesTest.kt`
**Purpose**: Test edge cases and failure handling

**Tests**:
- `partially downloaded files are not played` - Verifies DOWNLOADING status prevents playback
- `unsupported media types are ignored` - Ensures unsupported formats are filtered correctly
- `empty playlist is handled gracefully` - Validates empty playlist handling
- `playlist with missing media entries does not crash` - Tests null/empty URL handling
- `failed cache status prevents playback` - Verifies FAILED status prevents playback

**Key Validations**:
- Cache status enforcement (DOWNLOADING, FAILED → no playback)
- Unsupported file filtering
- Graceful error handling (no crashes)

**Usage**:
```bash
./gradlew connectedAndroidTest --tests "com.example.signoutwardv2.playback.PlaybackEdgeCasesTest"
```

### 5. `PlaybackMetricsTest.kt`
**Purpose**: Verify metrics logging and observability

**Tests**:
- `playback start and end times are logged` - Verifies timing logs for each media item
- `buffer delays are logged` - Tracks buffer delay events
- `skipped items are logged` - Logs unsupported/skipped files
- `failed media loads are logged` - Records failed playback attempts
- `device metadata is recorded in logs` - Logs app version, playlist ID, media IDs

**Key Metrics Logged**:
- Start/end times per media item
- Duration calculations
- Device app version
- Playlist ID
- Media IDs
- Buffer delays
- Skipped/failed items

**Usage**:
```bash
./gradlew connectedAndroidTest --tests "com.example.signoutwardv2.playback.PlaybackMetricsTest"
```

## Test Configuration

### Prerequisites

1. **Physical Device or Emulator**: Tests require Android device/emulator
2. **Test Screen ID**: Configure test screen ID in Supabase or use test fixtures
3. **Network Access**: Device must have internet access to fetch playlists from Supabase
4. **Test Data**: Some tests require manual playlist updates via web app

### Test Screen Setup

Before running tests, configure a test screen in Supabase:

1. Create a test screen with ID matching test expectations
2. Assign a test playlist with known media files
3. Ensure playlist contains both videos and images for mixed media tests

### Test Data Requirements

- **PlaylistLoopPerformanceTest**: Requires playlist with 2+ videos
- **DynamicPlaylistUpdateTest**: Requires manual playlist updates during test
- **MixedMediaPlaybackTest**: Requires playlist with both videos and images
- **PlaybackEdgeCasesTest**: Uses synthetic test data, no external requirements
- **PlaybackMetricsTest**: Requires any active playlist

## Running Tests

### Run All Playback Tests
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew connectedAndroidTest --tests "com.example.signoutwardv2.playback.*"
```

### Run Specific Test Class
```bash
./gradlew connectedAndroidTest --tests "com.example.signoutwardv2.playback.PlaylistLoopPerformanceTest"
```

### Run Specific Test Method
```bash
./gradlew connectedAndroidTest --tests "com.example.signoutwardv2.playback.PlaylistLoopPerformanceTest.playlist loops continuously without significant buffer lag"
```

### View Test Results

Test reports are generated at:
```
app/build/reports/androidTests/connected/index.html
```

## Test Output and Logging

All tests use Android Log with specific tags:

- `PlaylistLoopPerfTest` - Loop performance metrics
- `DynamicPlaylistUpdateTest` - Dynamic update detection
- `MixedMediaPlaybackTest` - Media type detection
- `PlaybackEdgeCasesTest` - Edge case handling
- `PlaybackMetricsTest` - Metrics and logging

View logs during test execution:
```bash
adb logcat -s PlaylistLoopPerfTest:D DynamicPlaylistUpdateTest:D MixedMediaPlaybackTest:D PlaybackEdgeCasesTest:D PlaybackMetricsTest:D
```

## Key Test Assertions

### Loop Performance
- Maximum buffer delay ≤ 500ms between loops
- At least one loop completes during observation period
- No significant buffering detected

### Dynamic Updates
- New videos appear within sync interval (30s + 5s buffer)
- New images appear and are in supported formats
- Playlist remains in Ready state after updates
- No crashes during update cycles

### Mixed Media
- Both videos and images are detected and processed
- Images are not skipped or filtered incorrectly
- Media order is preserved from original playlist
- All supported formats are included

### Edge Cases
- DOWNLOADING status → no playback (null URI)
- FAILED status → no playback (null URI)
- Unsupported files → filtered to unsupported list
- Empty playlists → handled gracefully (no crash)
- Null/empty URLs → filtered to unsupported

### Metrics
- Start/end times logged for each media item
- Buffer delays detected and logged
- Skipped items logged with details
- Failed loads logged with error messages
- Device metadata (version, IDs) recorded

## Troubleshooting

### Tests Skip with "Screen ID must be set"
**Solution**: Configure test screen ID in `DevicePreferences` or update test to use actual screen ID

### Tests Fail with "Playlist must be ready"
**Solution**: Ensure test playlist exists in Supabase and is assigned to test screen

### Dynamic Update Tests Don't Detect Changes
**Solution**: 
- Verify manual playlist update was performed via web app
- Increase sync wait time if network is slow
- Check Supabase connection and RLS policies

### Loop Performance Test Reports High Buffer Delays
**Solution**: 
- Check device performance and network conditions
- Verify cache is working (local files should reduce buffering)
- Review ExoPlayer configuration for optimal buffering

## Test Maintenance

### Adding New Tests

1. Create new test class in `app/src/androidTest/java/com/example/signoutwardv2/playback/`
2. Follow existing test structure:
   - `@RunWith(AndroidJUnit4::class)`
   - `@Before setup()` method
   - `@Test` methods with descriptive names
   - Comprehensive logging
   - Clear assertions

### Updating Test Data

- Test screen IDs can be configured in `@Before setup()`
- For tests requiring specific playlists, document requirements in test comments
- Use `org.junit.Assume` to skip tests when prerequisites aren't met

## Integration with CI/CD

These tests can be integrated into CI/CD pipelines:

```yaml
# Example GitHub Actions workflow
- name: Run Physical Device Tests
  run: |
    ./gradlew connectedAndroidTest --tests "com.example.signoutwardv2.playback.*"
  # Note: Requires Android emulator or physical device
```

For CI environments without devices, focus on unit tests and use these tests for manual QA validation.

## Success Criteria

Tests pass when:

✅ **Loop Performance**: No buffer delays > 500ms between loops  
✅ **Dynamic Updates**: New media appears within sync interval  
✅ **Mixed Media**: All supported media types play correctly  
✅ **Edge Cases**: Invalid states handled gracefully (no crashes)  
✅ **Metrics**: All events logged with required metadata  

---

**Test Coverage**: 5 test classes, 15+ test methods  
**Execution Time**: ~5-10 minutes (depending on sync intervals)  
**Requirements**: Physical device/emulator with network access

