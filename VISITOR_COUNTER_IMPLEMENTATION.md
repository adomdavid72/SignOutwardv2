# Visitor Counter Feature Implementation

## Overview

The visitor counter feature has been successfully implemented in the SignOutwardv2 Android app. This feature uses the tablet camera to detect and count people entering a defined area using ML Kit Object Detection.

## Implementation Details

### Components Created

1. **PersonDetectionProcessor.kt**
   - Uses ML Kit Object Detection for real-time person detection
   - Processes camera frames from CameraX
   - Tracks detected persons for line crossing detection
   - Does NOT store images/video (in-memory processing only)

2. **LineCrossingDetector.kt**
   - Detects when a person crosses a virtual entry line
   - Prevents duplicate counts with debouncing
   - Tracks crossings in-memory only

3. **CameraPreview.kt**
   - CameraX preview integration
   - Processes frames through PersonDetectionProcessor
   - Optimized for performance (processes every 3rd frame)

4. **VisitorCounterScreen.kt**
   - Main UI screen for visitor counting
   - Displays live visitor count
   - Camera preview with overlay
   - Reset count functionality
   - Permission handling

### Dependencies Added

- CameraX (camera-core, camera-camera2, camera-lifecycle, camera-view)
- ML Kit Object Detection (com.google.mlkit:object-detection)
- Accompanist Permissions (for camera permission handling)

### Permissions

- `CAMERA` permission added to AndroidManifest.xml
- Camera hardware feature declared

### Navigation Integration

- New route: `Screen.VisitorCounter` ("visitor_counter")
- Integrated into AppNavigation.kt
- Accessible via navigation: `navController.navigate(Screen.VisitorCounter.route)`

## Features

✅ Real-time person detection using ML Kit
✅ Line crossing detection (virtual entry line)
✅ Live visitor count display
✅ In-memory processing only (no storage)
✅ Optimized for tablet hardware (processes every 3rd frame)
✅ Camera permission handling
✅ Reset count functionality
✅ Clean UI with camera preview overlay

## Usage

To access the visitor counter screen:
```kotlin
navController.navigate(Screen.VisitorCounter.route)
```

Or from any screen in the app, you can navigate to:
```kotlin
navController.navigate("visitor_counter")
```

## Technical Notes

### Performance Optimization
- Processes every 3rd frame (33% of frames) to reduce CPU usage
- Uses ML Kit Object Detection (optimized for Android)
- Stream mode for real-time processing
- In-memory processing only

### Line Crossing Detection
- Entry line set to middle of frame (Y = 0.5)
- Detects crossings from top to bottom
- Debouncing prevents duplicate counts (1 second)
- Valid entry area: middle 60% of frame width

### ML Kit vs MediaPipe
- Initially planned to use MediaPipe, but switched to ML Kit for:
  - Better Android integration
  - No need for external model files
  - Optimized for mobile devices
  - Easier setup and maintenance

## Future Enhancements (Optional)

- Configurable entry line position
- Direction detection (entry vs exit)
- Maximum capacity alerts
- Data persistence (optional)
- Multi-device synchronization (optional)
- Analytics integration

## Testing

To test the visitor counter:
1. Ensure camera permission is granted
2. Navigate to visitor counter screen
3. Position camera to view entry area
4. Watch count increment as people cross the entry line
5. Test reset functionality

## Code Location

All visitor counter code is in:
- `app/src/main/java/com/example/signoutwardv2/visitor/`
- `app/src/main/java/com/example/signoutwardv2/screens/VisitorCounterScreen.kt`

## Branch Status

✅ Code is ready to commit to `develop` branch
✅ All dependencies added
✅ All permissions configured
✅ Navigation integrated
✅ No breaking changes to existing features

