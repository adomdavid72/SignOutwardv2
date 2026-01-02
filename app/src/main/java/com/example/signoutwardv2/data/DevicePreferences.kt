package com.example.signoutwardv2.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ============================================================================
// DEVICE PREFERENCES
// ============================================================================
// Local storage for device state (screen ID, pairing status)
// ============================================================================

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "device_preferences")

class DevicePreferences(private val context: Context) {
    
    companion object {
        private val SCREEN_ID = stringPreferencesKey("screen_id")
        private val IS_PAIRED = booleanPreferencesKey("is_paired")
        private val GROUP_ID = stringPreferencesKey("group_id")
        private val LOCATION_ID = stringPreferencesKey("location_id")
        private val LAST_SYNC = longPreferencesKey("last_sync")
        // Additional pairing metadata (optional)
        private val PAIRING_CODE = stringPreferencesKey("pairing_code")
        private val PAIRED_AT = longPreferencesKey("paired_at")
    }
    
    val screenId: Flow<String?> = context.dataStore.data.map { it[SCREEN_ID] }
    val isPaired: Flow<Boolean> = context.dataStore.data.map { it[IS_PAIRED] ?: false }
    val groupId: Flow<String?> = context.dataStore.data.map { it[GROUP_ID] }
    val locationId: Flow<String?> = context.dataStore.data.map { it[LOCATION_ID] }
    
    suspend fun setScreenId(screenId: String) {
        context.dataStore.edit { it[SCREEN_ID] = screenId }
    }
    
    suspend fun setPaired(paired: Boolean) {
        context.dataStore.edit { it[IS_PAIRED] = paired }
    }
    
    suspend fun setGroupId(groupId: String?) {
        context.dataStore.edit {
            if (groupId != null) it[GROUP_ID] = groupId
            else it.remove(GROUP_ID)
        }
    }
    
    suspend fun setLocationId(locationId: String?) {
        context.dataStore.edit {
            if (locationId != null) it[LOCATION_ID] = locationId
            else it.remove(LOCATION_ID)
        }
    }
    
    suspend fun updateLastSync() {
        context.dataStore.edit { it[LAST_SYNC] = System.currentTimeMillis() }
    }
    
    /**
     * Save pairing code (optional, for reference)
     */
    suspend fun setPairingCode(code: String?) {
        context.dataStore.edit {
            if (code != null) {
                it[PAIRING_CODE] = code
            } else {
                it.remove(PAIRING_CODE)
            }
        }
    }
    
    /**
     * Get pairing code (if stored)
     */
    val pairingCode: Flow<String?> = context.dataStore.data.map { it[PAIRING_CODE] }
    
    /**
     * Save pairing timestamp (optional, for reference)
     */
    suspend fun setPairedAt(timestamp: Long?) {
        context.dataStore.edit {
            if (timestamp != null) {
                it[PAIRED_AT] = timestamp
            } else {
                it.remove(PAIRED_AT)
            }
        }
    }
    
    /**
     * Get pairing timestamp (if stored)
     */
    val pairedAt: Flow<Long?> = context.dataStore.data.map { it[PAIRED_AT] }
    
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}

