# Visitor Counter Supabase Integration

## Overview

The visitor counter feature has been enhanced to store hourly counts in Supabase per device. This document describes the device ID handling, Supabase integration, and hourly aggregation system.

## Components

### 1. Device ID Management

**File**: `DeviceIdManager.kt`

The `DeviceIdManager` handles unique device identification:

- **Generation**: Uses `Settings.Secure.ANDROID_ID` to generate a unique device ID
- **Storage**: Persists device ID in DataStore preferences (`visitor_preferences`)
- **Persistence**: Device ID is stored permanently and reused across app restarts
- **Fallback**: If Android ID is unavailable, generates a fallback ID based on timestamp

**Usage**:
```kotlin
val deviceIdManager = DeviceIdManager(context)
val deviceId = deviceIdManager.getDeviceId() // Returns persistent device ID
```

### 2. Supabase Table Structure

**Table**: `visitor_counts`

```sql
CREATE TABLE visitor_counts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL,  -- Start of hour (ISO 8601)
    count INTEGER NOT NULL,         -- Visitor count for that hour
    created_at TIMESTAMP DEFAULT now()
);

-- Index for efficient queries
CREATE INDEX idx_visitor_counts_device_timestamp ON visitor_counts(device_id, timestamp);
```

**Columns**:
- `id`: UUID primary key (auto-generated)
- `device_id`: Unique device identifier (from Android ID)
- `timestamp`: ISO 8601 timestamp representing the start of the hour
- `count`: Integer count of visitors for that hour

### 3. Visitor Count Aggregator

**File**: `VisitorCountAggregator.kt`

The aggregator tracks visitor counts per hour:

- **In-Memory Tracking**: Maintains current hour's count in memory
- **Hour Transitions**: Automatically detects hour changes and prepares data for upload
- **Timestamp Generation**: Creates ISO 8601 timestamps for the start of each hour
- **Reset on Upload**: Resets count after successful upload

**Key Methods**:
- `incrementCount()`: Increments current hour's count
- `getCurrentHourCount(deviceId)`: Returns hourly count data for upload
- `markAsUploaded()`: Resets count after successful upload
- `resetCount()`: Manual reset (for testing)

### 4. Visitor Count Repository

**File**: `VisitorCountRepository.kt`

Handles Supabase uploads with retry logic:

- **Upload with Retry**: Attempts upload up to 3 times with exponential backoff
- **Network Handling**: Gracefully handles network errors
- **Error Logging**: Comprehensive error logging for debugging

**Key Methods**:
- `uploadHourlyCount(hourlyCount)`: Uploads hourly count to Supabase
- `getDeviceId()`: Gets device ID for use in aggregator

### 5. WorkManager Integration

**Files**: 
- `VisitorCountUploadWorker.kt`: Worker that performs hourly uploads
- `VisitorCountWorkManager.kt`: Manages WorkManager scheduling

**Features**:
- **Hourly Scheduling**: Runs every hour automatically
- **Network Constraints**: Only runs when network is available
- **Retry Logic**: WorkManager handles retries on failure
- **Battery Optimization**: Respects Android battery optimization
- **App Restarts**: Continues working after app restarts

**Scheduling**:
```kotlin
val workManager = VisitorCountWorkManager(context)
workManager.scheduleHourlyUploads() // Called once on app start
```

## Data Flow

1. **Visitor Detection**: Line crossing detector detects visitors and increments count
2. **Aggregation**: Count aggregator tracks count per hour
3. **Hour Transition**: When hour changes, aggregator prepares data for upload
4. **WorkManager Trigger**: WorkManager worker runs every hour
5. **Upload**: Worker gets current hour's count and uploads to Supabase
6. **Reset**: After successful upload, count is reset for new hour

## Network & Reliability

### Retry Strategy

- **Upload Retries**: Up to 3 attempts with 5-second delay between retries
- **WorkManager Retries**: WorkManager handles additional retries with exponential backoff
- **Network Constraints**: Uploads only occur when network is available

### Data Persistence

- **Device ID**: Stored in DataStore (persists across app restarts)
- **In-Memory Counts**: Current hour's count is in memory (fast access)
- **Pending Uploads**: Failed uploads are retried by WorkManager automatically

### No Data Loss

- **Hour Transitions**: Count is captured before hour change
- **Upload Before Reset**: Count is only reset after successful upload
- **WorkManager Persistence**: WorkManager persists work requests across app restarts

## Integration Points

### VisitorCounterScreen

The screen integrates with the aggregator:

- **Count Display**: Shows current visitor count from line crossing detector
- **Aggregator Sync**: Syncs line crossing count to aggregator for hourly tracking
- **WorkManager Init**: Schedules hourly uploads on screen initialization
- **Reset Handling**: Resets both line crossing detector and aggregator

### SupabaseClient

Added method for visitor count uploads:

```kotlin
suspend fun uploadVisitorCount(visitorCount: VisitorCount): Result<Unit>
```

## Scheduling

### WorkManager Configuration

- **Interval**: 1 hour (PeriodicWorkRequest)
- **Constraints**: Requires network connection
- **Backoff Policy**: Exponential backoff on failure
- **Unique Work**: Uses unique work name to prevent duplicates

### Initialization

WorkManager is initialized in `VisitorCounterScreen`:

```kotlin
LaunchedEffect(Unit) {
    workManager.scheduleHourlyUploads()
}
```

This ensures uploads are scheduled when the visitor counter screen is first accessed.

## Testing

### Manual Testing

1. **Device ID**: Verify device ID is generated and persisted
2. **Count Tracking**: Verify counts increment correctly
3. **Hour Transitions**: Test hour change detection
4. **Upload**: Verify uploads to Supabase (check network logs)
5. **Retry**: Test retry logic by disabling network

### Supabase Verification

Query visitor counts:
```sql
SELECT * FROM visitor_counts 
WHERE device_id = 'your_device_id' 
ORDER BY timestamp DESC;
```

## Best Practices

1. **No UI Blocking**: All uploads happen in background (WorkManager)
2. **Battery Efficient**: WorkManager respects battery optimization
3. **Network Aware**: Only uploads when network is available
4. **Error Handling**: Comprehensive error logging and retry logic
5. **Data Integrity**: No counts lost due to network errors or app restarts

## Future Enhancements

- **Batch Uploads**: Upload multiple hours at once if network was unavailable
- **Local Storage**: Store pending counts in local database for offline scenarios
- **Analytics Dashboard**: Create dashboard to view visitor counts per device
- **Alerts**: Notify when counts exceed thresholds

## Code Location

All visitor count Supabase integration code is in:
- `app/src/main/java/com/example/signoutwardv2/visitor/`
  - `DeviceIdManager.kt`
  - `VisitorCountAggregator.kt`
  - `VisitorCountRepository.kt`
  - `VisitorCountUploadWorker.kt`
  - `VisitorCountWorkManager.kt`

## Dependencies

- **WorkManager**: `androidx.work:work-runtime-ktx:2.9.0`
- **DataStore**: Already included (for device preferences)
- **Supabase Client**: Existing SupabaseClient extended

## Notes

- Device ID is generated once and reused (persistent)
- Counts are aggregated per hour (timestamp = start of hour)
- Uploads happen in background (non-blocking)
- WorkManager ensures reliability across app restarts
- No changes to existing camera, line-crossing, or UI logic

