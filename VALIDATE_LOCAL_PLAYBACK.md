# Validating Local/Cached Playback

This guide explains how to validate that the app is using locally cached files instead of fetching from Supabase after the initial download.

## Quick Validation Commands

### 1. Check Source Summary (Recommended)
When playback starts, the app logs a summary of all items and their sources:

```bash
adb logcat -v time '*:V' | grep --line-buffered "MediaSource" | grep --line-buffered "PLAYBACK SOURCE SUMMARY"
```

**Expected Output:**
```
MediaSource: === PLAYBACK SOURCE SUMMARY ===
MediaSource: Total items: 5
MediaSource:   CACHE: 5 items
MediaSource:   REMOTE: 0 items
MediaSource: Local/Cached: 5 | Remote: 0
MediaSource: ✓ All items are using local/cached files
MediaSource: === END SOURCE SUMMARY ===
```

### 2. Check Individual Playback Start Logs
Each time a media item starts playing, the source is logged:

```bash
adb logcat -v time '*:V' | grep --line-buffered "MediaSource" | grep --line-buffered "=== PLAYBACK START ==="
```

**Expected Output:**
```
MediaSource: === PLAYBACK START ===
MediaSource: Media ID: abc-123
MediaSource: Media Name: My Video
MediaSource: Media Type: VIDEO
MediaSource: Source Type: CACHE    # <-- Should be CACHE or LOCAL, NOT REMOTE
MediaSource: Source URI: file:///data/user/0/.../cache/...
```

### 3. Check MixedMediaPlayer Source Logs
The player also logs source information for each item:

```bash
adb logcat -v time '*:V' | grep --line-buffered "MixedMediaPlayer" | grep --line-buffered -E "(Source:|Playback URI:)"
```

**Expected Output:**
```
MixedMediaPlayer: Source: CACHE
MixedMediaPlayer: Is Cached: true
MixedMediaPlayer: Playback URI: file:///data/user/0/.../cache/...
```

### 4. Comprehensive Source Validation
Check all source-related logs at once:

```bash
adb logcat -v time '*:V' | grep --line-buffered -E "(MediaSource|MixedMediaPlayer)" | grep --line-buffered -E "(Source Type:|Source:|PLAYBACK SOURCE SUMMARY|Playback URI:)"
```

## What to Look For

### ✅ **Valid (Using Local/Cached Files)**
- `Source Type: CACHE` or `Source Type: LOCAL`
- `Source: Cache Directory` or `Source: Local Storage`
- `Is Cached: true`
- `Playback URI: file:///...` (starts with `file://`)
- URI contains `/cache/` or `/Android/data/`

### ❌ **Invalid (Still Fetching from Supabase)**
- `Source Type: REMOTE`
- `Source: Remote (supabase.co)` or similar
- `Is Cached: false`
- `Playback URI: https://...` or `http://...` (starts with `http`)
- URI contains a Supabase domain

## Understanding Source Types

1. **LOCAL**: Files stored in regular app storage (not cache directory)
2. **CACHE**: Files stored in the app's cache directory (`/cache/` or `/Android/data/`)
3. **REMOTE**: Files being streamed from Supabase (HTTP/HTTPS URLs)

Both `LOCAL` and `CACHE` indicate the file is stored locally and not being fetched from Supabase.

## Troubleshooting

### If You See REMOTE Sources

1. **Check if downloads completed:**
   ```bash
   adb logcat -v time '*:V' | grep --line-buffered "DownloadFirstMediaPlayer" | grep --line-buffered -E "(All downloads complete|downloads failed)"
   ```

2. **Check cache status:**
   ```bash
   adb logcat -v time '*:V' | grep --line-buffered "MediaSource" | grep --line-buffered "Cache Status:"
   ```

3. **Verify files exist locally:**
   - Connect to device via ADB
   - Navigate to app's cache directory
   - Check if media files are present

### Expected Behavior After Initial Download

After the initial download completes:
- ✅ All items should show `Source Type: CACHE` or `LOCAL`
- ✅ All `Playback URI` values should start with `file://`
- ✅ `Is Cached: true` for all items
- ✅ No network requests should be made for media files
- ✅ Playback should work even if device is offline

## Real-Time Monitoring

To monitor source usage in real-time while the app is running:

```bash
adb logcat -v time '*:V' | grep --line-buffered -E "(MediaSource|MixedMediaPlayer)" | grep --line-buffered -E "(PLAYBACK START|Source Type:|Source:)"
```

This will show you the source type for each media item as it starts playing, allowing you to verify in real-time that local files are being used.

