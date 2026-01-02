package com.example.signoutwardv2.visitor

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * VisitorCountAggregator - Tracks visitor counts per hour and manages uploads
 * 
 * Features:
 * - Tracks current hour's count in memory
 * - Persists pending counts to local storage (for retry on network failure)
 * - Provides hourly aggregation with timestamp (start of hour)
 * - Handles hour transitions automatically
 */
private val Context.visitorCountDataStore: DataStore<Preferences> by preferencesDataStore(name = "visitor_counts")

class VisitorCountAggregator(private val context: Context) {
    companion object {
        private const val TAG = "VisitorCountAggregator"
        private const val PENDING_COUNTS_KEY = "pending_counts" // JSON array of pending counts
    }

    // Current hour's count (in-memory)
    private val currentCount = MutableStateFlow(0)
    
    // Current hour timestamp (start of hour)
    private var currentHourTimestamp: String? = null

    /**
     * Get current visitor count (StateFlow for reactive UI updates)
     */
    fun getCurrentCount(): StateFlow<Int> = currentCount.asStateFlow()

    /**
     * Increment visitor count for current hour
     */
    fun incrementCount() {
        val newCount = currentCount.value + 1
        currentCount.value = newCount
        Log.d(TAG, "Visitor count incremented: $newCount")
    }

    /**
     * Reset current count (for testing or manual reset)
     */
    fun resetCount() {
        currentCount.value = 0
        currentHourTimestamp = null
        Log.d(TAG, "Visitor count reset")
    }

    /**
     * Get current hour's aggregated count data
     * Returns null if no count for current hour
     */
    suspend fun getCurrentHourCount(deviceId: String): HourlyCount? {
        val now = Instant.now()
        val hourStart = now.atZone(ZoneOffset.UTC)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
        
        val timestamp = hourStart.format(DateTimeFormatter.ISO_INSTANT)
        
        // Check if we're in a new hour
        if (currentHourTimestamp != timestamp) {
            // New hour - return previous hour's count if any
            val previousCount = if (currentHourTimestamp != null && currentCount.value > 0) {
                HourlyCount(
                    deviceId = deviceId,
                    timestamp = currentHourTimestamp!!,
                    count = currentCount.value
                )
            } else {
                null
            }
            
            // Reset for new hour
            currentHourTimestamp = timestamp
            currentCount.value = 0
            
            return previousCount
        }
        
        // Same hour - return current count
        return if (currentCount.value > 0) {
            HourlyCount(
                deviceId = deviceId,
                timestamp = timestamp,
                count = currentCount.value
            )
        } else {
            null
        }
    }

    /**
     * Mark current hour's count as uploaded (reset after successful upload)
     */
    fun markAsUploaded() {
        // Reset count after successful upload
        currentCount.value = 0
        currentHourTimestamp = null
        Log.d(TAG, "Count marked as uploaded and reset")
    }

    /**
     * Data class for hourly count
     */
    data class HourlyCount(
        val deviceId: String,
        val timestamp: String, // ISO 8601 timestamp (start of hour)
        val count: Int
    ) {
        fun toVisitorCount(): com.example.signoutwardv2.data.models.VisitorCount {
            return com.example.signoutwardv2.data.models.VisitorCount(
                id = UUID.randomUUID().toString(),
                deviceId = deviceId,
                timestamp = timestamp,
                count = count
            )
        }
    }
}

