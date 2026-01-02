package com.example.signoutwardv2.visitor

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DeviceIdManager - Manages unique device ID for visitor counting
 * 
 * Generates and stores a persistent device ID using Android ID.
 * The device ID is used to identify the device when uploading visitor counts to Supabase.
 */
private val Context.visitorDataStore: DataStore<Preferences> by preferencesDataStore(name = "visitor_preferences")

class DeviceIdManager(private val context: Context) {
    companion object {
        private const val TAG = "DeviceIdManager"
        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
    }

    /**
     * Get or generate device ID
     * 
     * @return Device ID (persistent across app restarts)
     */
    suspend fun getDeviceId(): String {
        return try {
            // Try to get existing device ID from preferences
            val existingId = context.visitorDataStore.data.map { it[DEVICE_ID_KEY] }.first()
            
            if (existingId != null && existingId.isNotBlank()) {
                Log.d(TAG, "Using existing device ID: $existingId")
                existingId
            } else {
                // Generate new device ID using Android ID
                val androidId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                )
                
                // Use Android ID as device ID (it's unique per device)
                val deviceId = androidId ?: generateFallbackId()
                
                // Store device ID persistently
                context.visitorDataStore.edit { preferences ->
                    preferences[DEVICE_ID_KEY] = deviceId
                }
                
                Log.d(TAG, "Generated and stored new device ID: $deviceId")
                deviceId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device ID", e)
            // Fallback to generated ID
            generateFallbackId()
        }
    }

    /**
     * Generate fallback device ID if Android ID is not available
     */
    private fun generateFallbackId(): String {
        // Generate a unique ID based on timestamp and random
        val timestamp = System.currentTimeMillis()
        val random = (Math.random() * 1000000).toInt()
        return "device_${timestamp}_$random"
    }

    /**
     * Get device ID synchronously (for use in non-suspend contexts)
     * Note: This may return a cached value or generate a new one
     */
    suspend fun getDeviceIdSync(): String = getDeviceId()
}

