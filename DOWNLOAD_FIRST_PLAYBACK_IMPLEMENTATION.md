# Download-First Playback System Implementation

## Overview

This document describes the implementation of a robust, deterministic playback system with full local caching and background playlist syncing for Sign Outward v2.

## Components Created

### 1. LocalCacheManager (`app/src/main/java/com/example/signoutwardv2/cache/LocalCacheManager.kt`)

**Purpose**: Manages download-first playback by downloading all media files before playback starts.

**Key Features**:
- Downloads all media files (images and videos) before playback
- Tracks per-item download progress
- Ensures playback only starts once all files are downloaded
- Provides local file URIs only (never remote URLs)
- Removes cached files for playlist sync cleanup

**Key Methods**:
- `downloadAllMedia(videos, playlistId)`: Downloads all media files, blocks until complete
- `getLocalUri(playlistId, videoId, url)`: Returns local file URI if cached, null otherwise
- `isCached(playlistId, videoId, url)`: Checks if media is cached and ready
- `removeCachedFile(playlistId, videoId, url)`: Removes cached file (for sync cleanup)

**Progress Tracking**:
- `downloadProgress: StateFlow<Map<String, DownloadProgress>>`: Per-item progress (0.0 to 1.0)
- `allDownloadsComplete: StateFlow<Boolean>`: Overall completion status

### 2. LocalPlaybackEngine (`app/src/main/java/com/example/signoutwardv2/playback/LocalPlaybackEngine.kt`)

**Purpose**: Ensures playback is entirely from local storage with exact playlist order.

**Key Features**:
- Only uses local file URIs (never streaming)
- Maintains exact playlist order (videos + images)
- Supports mixed media (images and videos in same playlist)
- Verifies all items are cached before playback

**Key Methods**:
- `preparePlaybackItems(playlist)`: Prepares playback items from local storage only
- `verifyAllCached(playlist)`: Verifies all media items are cached
- `getPlaybackOrder(playlist)`: Returns playback order for verification

**Data Structure**:
```kotlin
data class PlaybackItem(
    val video: Video,
    val mediaType: MediaType,
    val localUri: String, // Always file:// URI
    val index: Int // Original playlist order
)
```

### 3. PlaylistSyncManager (`app/src/main/java/com/example/signoutwardv2/sync/PlaylistSyncManager.kt`)

**Purpose**: Background playlist synchronization every 2 minutes (configurable).

**Key Features**:
- Syncs playlist every 2 minutes (configurable via `SYNC_INTERVAL_MS`)
- Removes local files no longer in Supabase playlist
- Downloads new files in background
- Updates local playlist queue seamlessly

**Key Methods**:
- `startPeriodicSync(groupId, locationId)`: Starts background sync (2-minute interval)
- `stopPeriodicSync()`: Stops background sync
- `syncPlaylist(groupId, locationId)`: Performs one sync operation

**Sync Result**:
```kotlin
data class SyncResult(
    val filesAdded: Int,
    val filesRemoved: Int,
    val filesUpdated: Int,
    val syncTime: Long
)
```

### 4. Updated AdStreamingScreen (`app/src/main/java/com/example/signoutwardv2/screens/AdStreamingScreen.kt`)

**Changes**:
- Integrated `LocalCacheManager` for download-first playback
- Integrated `PlaylistSyncManager` for background sync
- Added `DownloadFirstMediaPlayer` composable
- Added `DownloadProgressScreen` composable for progress UI
- Added `LocalMediaPlayer` composable for local-only playback

**New Composables**:
- `DownloadFirstMediaPlayer`: Downloads all media before playback, shows progress
- `DownloadProgressScreen`: Shows per-item download progress with progress bars
- `LocalMediaPlayer`: Plays media from local storage only, maintains order

## Implementation Details

### Download-First Playback Flow

1. **Playlist Loaded**: When playlist is fetched from Supabase
2. **Download Started**: `LocalCacheManager.downloadAllMedia()` is called
3. **Progress Tracking**: Per-item progress is tracked and displayed in UI
4. **Completion Check**: System waits until `allDownloadsComplete == true`
5. **Playback Preparation**: `LocalPlaybackEngine.preparePlaybackItems()` prepares local files
6. **Playback Start**: Only starts when all files are cached locally

### Background Sync Flow

1. **Periodic Sync**: Every 2 minutes, `PlaylistSyncManager` syncs playlist
2. **Compare**: Compares remote playlist with local cached files
3. **Remove**: Deletes local files no longer in remote playlist
4. **Download**: Queues new files for background download
5. **Update**: Updates repository playlist state seamlessly

### Playback Order Preservation

- Playback order matches exact playlist order
- Images and videos are played in the order they appear in the playlist
- `LocalPlaybackEngine.getPlaybackOrder()` verifies order matches

## Test Implementation

### DownloadFirstPlaybackTest (`app/src/androidTest/java/com/example/signoutwardv2/playback/DownloadFirstPlaybackTest.kt`)

**Test Cases**:
1. `videoThenImagePlaybackOrder()`: Tests Video → Image order
2. `imageThenVideoPlaybackOrder()`: Tests Image → Video order
3. `downloadProgressTracking()`: Tests download progress tracking
4. `playbackOnlyWhenAllCached()`: Tests playback blocked when not all cached
5. `playbackOrderPreservation()`: Tests order matches playlist exactly

**Features**:
- Uses `TestMediaRepository` for deterministic testing
- Mocks cached files (simulates completed downloads)
- Verifies local-only playback
- Verifies playback order matches playlist
- No live Supabase DB required

## Logging

### Download Progress Logging
- `MEDIA_ID: {id} | CACHE_STATUS: {status} | DOWNLOAD_STARTED: {true/false}`
- `Download progress for {id}: {percentage}%`
- `All downloads complete - ready for playback`

### Playback Order Logging
- `=== Playback Order ===`
- `[{index}] {videoId} | Type: {VIDEO/IMAGE} | Local URI: {file://...}`

### Sync Logging
- `=== Starting playlist sync ===`
- `Files added: {count}`
- `Files removed: {count}`
- `=== Sync complete ===`

## Configuration

### Sync Interval
- Default: 2 minutes (`SYNC_INTERVAL_MS = 120_000L`)
- Configurable in `PlaylistSyncManager`

### Download Timeout
- Default: 5 minutes (`300_000L`)
- Configurable in `LocalCacheManager.downloadAllMedia()`

## Constraints Preserved

✅ **Existing video playback logic**: Not changed  
✅ **Caching logic**: Preserved and enhanced  
✅ **Playlist priority**: Preserved  
✅ **Recurring schedules**: Preserved  
✅ **Analytics**: Preserved  
✅ **Role-based access**: Preserved  
✅ **Offline playback**: Enhanced (all media cached locally)

## Usage

### Starting Download-First Playback

```kotlin
val localCacheManager = LocalCacheManager(context, screenId)
val playbackEngine = LocalPlaybackEngine(context, localCacheManager, playlistId)

// Download all media
val allDownloaded = localCacheManager.downloadAllMedia(playlist.videos, playlistId)

// Prepare playback items (only if all downloaded)
val playbackItems = playbackEngine.preparePlaybackItems(playlist)
```

### Starting Background Sync

```kotlin
val syncManager = PlaylistSyncManager(context, screenId, localCacheManager, repository)
syncManager.startPeriodicSync(groupId, locationId)
```

## Benefits

1. **Deterministic Playback**: All media downloaded before playback starts
2. **Offline Capable**: All playback from local storage
3. **No Buffering**: No network delays during playback
4. **Order Preserved**: Exact playlist order maintained
5. **Mixed Media Support**: Images and videos in any order
6. **Background Updates**: Playlist syncs automatically
7. **Progress Visibility**: Per-item download progress shown

## Next Steps

1. Test on physical device
2. Verify download progress UI displays correctly
3. Verify background sync removes/adds files correctly
4. Monitor performance and adjust sync interval if needed
5. Add error handling for download failures

