package com.example.signoutwardv2.pairing

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.example.signoutwardv2.data.DevicePreferences
import kotlinx.coroutines.flow.first

/**
 * DevicePairingManager - Persistent device pairing management
 * 
 * Responsibilities:
 * - Store pairing information persistently (survives app restarts and device reboots)
 * - Check if device is already paired on app start
 * - Store pairing metadata (device_id, pairing_code, timestamp)
 * - Provide secure storage option (EncryptedSharedPreferences)
 * 
 * Uses DataStore for persistence (already implemented in DevicePreferences)
 * Pairing state persists across app restarts and device reboots
 */
class DevicePairingManager(private val context: Context) {
    
    companion object {
        private const val TAG = "DevicePairingManager"
    }
    
    private val preferences = DevicePreferences(context)
    
    /**
     * Pairing information stored locally
     */
    data class PairingInfo(
        val deviceId: String,
        val screenId: String,
        val isPaired: Boolean,
        val pairingCode: String? = null,
        val pairedAt: Long? = null,
        val groupId: String? = null,
        val locationId: String? = null
    )
    
    /**
     * Get unique device ID (persistent across app installs)
     * Uses Android ID which is unique per device/user combination
     */
    fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown_device_${System.currentTimeMillis()}"
    }
    
    /**
     * Check if device is already paired
     * Returns true if device is paired and screenId is set
     */
    suspend fun isPaired(): Boolean {
        val paired = preferences.isPaired.first()
        val screenId = preferences.screenId.first()
        
        val isPaired = paired && !screenId.isNullOrEmpty()
        
        Log.d(TAG, "Checking pairing status: isPaired=$paired, screenId=$screenId, result=$isPaired")
        
        return isPaired
    }
    
    /**
     * Get current pairing info
     */
    suspend fun getPairingInfo(): PairingInfo? {
        val isPaired = preferences.isPaired.first()
        val screenId = preferences.screenId.first()
        val groupId = preferences.groupId.first()
        val locationId = preferences.locationId.first()
        val pairingCode = preferences.pairingCode.first()
        val pairedAt = preferences.pairedAt.first()
        
        if (!isPaired || screenId.isNullOrEmpty()) {
            return null
        }
        
        return PairingInfo(
            deviceId = getDeviceId(),
            screenId = screenId,
            isPaired = true,
            pairingCode = pairingCode,
            pairedAt = pairedAt,
            groupId = groupId,
            locationId = locationId
        )
    }
    
    /**
     * Save pairing information after successful pairing
     * 
     * @param screenId Screen ID from Supabase
     * @param pairingCode Optional pairing code used (for reference)
     * @param groupId Optional group ID
     * @param locationId Optional location ID
     */
    suspend fun savePairing(
        screenId: String,
        pairingCode: String? = null,
        groupId: String? = null,
        locationId: String? = null
    ) {
        Log.d(TAG, "=== Saving pairing information ===")
        Log.d(TAG, "Device ID: ${getDeviceId()}")
        Log.d(TAG, "Screen ID: $screenId")
        Log.d(TAG, "Pairing Code: $pairingCode")
        
        // Save pairing state
        preferences.setScreenId(screenId)
        preferences.setPaired(true)
        
        // Save optional fields
        if (pairingCode != null) {
            preferences.setPairingCode(pairingCode)
        }
        preferences.setPairedAt(System.currentTimeMillis())
        
        if (groupId != null) {
            preferences.setGroupId(groupId)
        }
        if (locationId != null) {
            preferences.setLocationId(locationId)
        }
        
        Log.d(TAG, "Pairing information saved - will persist across app restarts and device reboots")
    }
    
    /**
     * Clear pairing information (for unpairing or reset)
     */
    suspend fun clearPairing() {
        Log.d(TAG, "Clearing pairing information")
        preferences.clearAll()
        Log.d(TAG, "Pairing information cleared")
    }
    
    /**
     * Get screen ID if paired, null otherwise
     */
    suspend fun getScreenId(): String? {
        return if (isPaired()) {
            preferences.screenId.first()
        } else {
            null
        }
    }
    
    /**
     * Get group ID if paired, null otherwise
     */
    suspend fun getGroupId(): String? {
        return if (isPaired()) {
            preferences.groupId.first()
        } else {
            null
        }
    }
    
    /**
     * Get location ID if paired, null otherwise
     */
    suspend fun getLocationId(): String? {
        return if (isPaired()) {
            preferences.locationId.first()
        } else {
            null
        }
    }
    
    // Note: For reactive pairing state updates, observe preferences.isPaired Flow
    // and call getPairingInfo() when needed, or observe individual preference flows
}

