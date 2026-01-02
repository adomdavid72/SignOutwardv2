# Persistent Device Pairing Implementation

## Overview

This document describes the implementation of persistent device pairing for Sign Outward v2, ensuring that pairing information survives app restarts and device reboots.

## Components Created

### 1. DevicePairingManager (`app/src/main/java/com/example/signoutwardv2/pairing/DevicePairingManager.kt`)

**Purpose**: Centralized management of persistent device pairing state.

**Key Features**:
- Checks if device is already paired on app start
- Stores pairing information persistently using DataStore
- Provides device ID (Android ID)
- Stores pairing metadata (screenId, pairingCode, timestamp, groupId, locationId)
- Clears pairing information when needed

**Key Methods**:
- `isPaired(): Boolean` - Check if device is currently paired
- `getPairingInfo(): PairingInfo?` - Get complete pairing information
- `savePairing(screenId, pairingCode?, groupId?, locationId?)` - Save pairing after successful pairing
- `clearPairing()` - Clear all pairing information
- `getScreenId(): String?` - Get screen ID if paired
- `getDeviceId(): String` - Get unique device ID (Android ID)

**PairingInfo Data Class**:
```kotlin
data class PairingInfo(
    val deviceId: String,
    val screenId: String,
    val isPaired: Boolean,
    val pairingCode: String? = null,
    val pairedAt: Long? = null,
    val groupId: String? = null,
    val locationId: String? = null
)
```

### 2. Enhanced DevicePreferences (`app/src/main/java/com/example/signoutwardv2/data/DevicePreferences.kt`)

**New Fields Added**:
- `pairingCode: Flow<String?>` - Stores the pairing code used (optional)
- `pairedAt: Flow<Long?>` - Stores timestamp of pairing (optional)

**New Methods**:
- `setPairingCode(code: String?)` - Save pairing code
- `setPairedAt(timestamp: Long?)` - Save pairing timestamp

### 3. Updated AppNavigation (`app/src/main/java/com/example/signoutwardv2/navigation/AppNavigation.kt`)

**Changes**:
- Checks pairing state on app startup using `DevicePairingManager`
- If device is already paired, navigates directly to `AdStreamingScreen` (skips pairing workflow)
- If device is not paired, shows `PairingScreen` as before
- Clears back stack when navigating to playback (prevents going back to pairing)

**Flow**:
1. App starts → `AppNavigation` composable
2. `LaunchedEffect` checks `pairingManager.isPaired()`
3. If paired → Navigate to `AdStreamingScreen` with saved `screenId`
4. If not paired → Show `PairingScreen`

### 4. Updated PairingScreen (`app/src/main/java/com/example/signoutwardv2/screens/PairingScreen.kt`)

**Changes**:
- Uses `DevicePairingManager` to save pairing information
- Saves pairing code, timestamp, groupId, and locationId
- Ensures pairing persists across app restarts and device reboots

**Pairing Flow**:
1. User enters pairing code
2. `SupabaseClient.validatePairingCode()` validates code
3. On success → `pairingManager.savePairing()` saves all pairing info
4. Navigation proceeds to `PairingSuccessScreen` → `AdStreamingScreen`

## Persistence Details

### Storage Mechanism

- **DataStore**: Uses Android DataStore Preferences (already implemented in `DevicePreferences`)
- **Persistence**: DataStore persists data to disk, surviving:
  - App restarts
  - Device reboots
  - App updates (unless app is uninstalled)

### Stored Data

1. **Required Fields**:
   - `screen_id` (String) - Screen ID from Supabase
   - `is_paired` (Boolean) - Pairing status

2. **Optional Fields**:
   - `pairing_code` (String?) - Pairing code used (for reference)
   - `paired_at` (Long?) - Timestamp of pairing (milliseconds since epoch)
   - `group_id` (String?) - Group ID from Supabase
   - `location_id` (String?) - Location ID from Supabase

3. **Device ID**:
   - Retrieved dynamically using `Settings.Secure.ANDROID_ID`
   - Not stored (retrieved on-demand)
   - Unique per device/user combination

## Usage

### Checking Pairing Status

```kotlin
val pairingManager = DevicePairingManager(context)
val isPaired = pairingManager.isPaired() // suspend function
```

### Saving Pairing

```kotlin
pairingManager.savePairing(
    screenId = "screen-123",
    pairingCode = "ABC123",
    groupId = "group-456",
    locationId = "location-789"
)
```

### Getting Pairing Info

```kotlin
val pairingInfo = pairingManager.getPairingInfo()
if (pairingInfo != null) {
    println("Device ID: ${pairingInfo.deviceId}")
    println("Screen ID: ${pairingInfo.screenId}")
    println("Paired at: ${pairingInfo.pairedAt}")
}
```

### Clearing Pairing

```kotlin
pairingManager.clearPairing() // Clears all pairing data
```

## App Startup Flow

1. **App Launches** → `MainActivity.onCreate()`
2. **Navigation Initializes** → `AppNavigation()` composable
3. **Pairing Check** → `LaunchedEffect` calls `pairingManager.isPaired()`
4. **Decision**:
   - **If Paired**: Navigate directly to `AdStreamingScreen` with saved `screenId`
   - **If Not Paired**: Show `PairingScreen`
5. **User Experience**:
   - Paired devices skip pairing workflow entirely
   - Unpaired devices see pairing screen as before

## Benefits

1. **User Experience**: Paired devices skip pairing workflow on every app start
2. **Persistence**: Pairing survives app restarts and device reboots
3. **Offline Capable**: Pairing check works offline (uses local storage)
4. **Metadata Storage**: Stores pairing code, timestamp, and related IDs for reference
5. **Device Identification**: Uses Android ID for unique device identification

## Security Considerations

### Current Implementation

- Uses standard DataStore (not encrypted)
- Suitable for non-sensitive pairing data
- Pairing codes are already validated server-side

### Future Enhancement: EncryptedSharedPreferences

If enhanced security is needed, `EncryptedSharedPreferences` can be integrated:

```kotlin
// Example (not implemented):
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val encryptedPrefs = EncryptedSharedPreferences.create(
    context,
    "pairing_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

**Note**: This is optional and not currently implemented, as pairing data is not highly sensitive (pairing codes are validated server-side).

## Testing

### Manual Testing Steps

1. **First Pairing**:
   - Launch app → Should show pairing screen
   - Enter pairing code → Should pair successfully
   - Restart app → Should skip pairing and go directly to playback

2. **Device Reboot**:
   - Pair device
   - Reboot device
   - Launch app → Should skip pairing and go directly to playback

3. **Unpairing**:
   - Call `pairingManager.clearPairing()`
   - Restart app → Should show pairing screen again

## Logging

The implementation includes comprehensive logging:

- `DevicePairingManager`: Logs pairing checks, saves, and clears
- `AppNavigation`: Logs pairing state check and navigation decisions
- `PairingScreen`: Logs pairing save confirmation

Example logs:
```
DevicePairingManager: Checking pairing status: isPaired=true, screenId=screen-123, result=true
AppNavigation: Device already paired - screenId: screen-123
AppNavigation: Skipping pairing workflow - navigating directly to playback
PairingScreen: Pairing saved - will persist across app restarts and device reboots
```

## Constraints Preserved

✅ **Existing pairing logic**: Not broken  
✅ **Supabase integration**: Preserved  
✅ **Pairing validation**: Still validates against Supabase  
✅ **Cache clearing**: Still clears cache on pairing (as before)  
✅ **Navigation flow**: Preserved (just skips pairing if already paired)

## Future Enhancements

1. **Encrypted Storage**: Add `EncryptedSharedPreferences` for enhanced security
2. **Pairing Expiration**: Add optional pairing expiration logic
3. **Multiple Pairings**: Support multiple screen pairings per device (if needed)
4. **Pairing History**: Store pairing history for debugging

## Summary

The persistent pairing implementation ensures that:
- ✅ Pairing information persists across app restarts
- ✅ Pairing information persists across device reboots
- ✅ Paired devices skip pairing workflow on app start
- ✅ All pairing metadata is stored locally
- ✅ Existing pairing logic and Supabase integration remain intact
- ✅ Offline pairing check works (uses local storage)

The implementation uses Android DataStore for persistence, which is the recommended modern approach for storing key-value pairs in Android apps.

