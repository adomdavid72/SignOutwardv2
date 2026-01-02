package com.example.signoutwardv2.visitor

import android.content.Context
import android.util.Log
import com.example.signoutwardv2.data.SupabaseClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * VisitorCountRepository - Manages visitor count uploads to Supabase
 * 
 * Features:
 * - Uploads hourly counts to Supabase
 * - Retries failed uploads
 * - Handles network errors gracefully
 * - Ensures no counts are lost
 */
class VisitorCountRepository(private val context: Context) {
    companion object {
        private const val TAG = "VisitorCountRepository"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 5000L // 5 seconds
    }

    private val deviceIdManager = DeviceIdManager(context)

    /**
     * Upload hourly count to Supabase
     * 
     * @param hourlyCount HourlyCount to upload
     * @return Result indicating success or failure
     */
    suspend fun uploadHourlyCount(hourlyCount: VisitorCountAggregator.HourlyCount): Result<Unit> {
        val deviceId = deviceIdManager.getDeviceId()
        val visitorCount = hourlyCount.toVisitorCount()
        
        return uploadWithRetry(visitorCount, MAX_RETRIES)
    }

    /**
     * Upload visitor count with retry logic
     */
    private suspend fun uploadWithRetry(
        visitorCount: com.example.signoutwardv2.data.models.VisitorCount,
        maxRetries: Int
    ): Result<Unit> {
        var lastError: Throwable? = null
        
        repeat(maxRetries) { attempt ->
            try {
                Log.d(TAG, "Upload attempt ${attempt + 1}/$maxRetries for visitor count")
                
                val result = SupabaseClient.uploadVisitorCount(visitorCount)
                
                result.fold(
                    onSuccess = {
                        Log.d(TAG, "Visitor count uploaded successfully")
                        return Result.success(Unit)
                    },
                    onFailure = { error: Throwable ->
                        lastError = error
                        Log.w(TAG, "Upload attempt ${attempt + 1} failed: ${error.message}")
                        
                        // Wait before retry (except on last attempt)
                        if (attempt < maxRetries - 1) {
                            kotlinx.coroutines.delay(RETRY_DELAY_MS)
                        }
                    }
                )
            } catch (e: Exception) {
                lastError = e
                Log.e(TAG, "Error during upload attempt ${attempt + 1}", e)
                
                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay(RETRY_DELAY_MS)
                }
            }
        }
        
        // All retries failed
        Log.e(TAG, "Failed to upload visitor count after $maxRetries attempts")
        return Result.failure(lastError ?: Exception("Upload failed after $maxRetries attempts"))
    }

    /**
     * Get device ID (for use in aggregator)
     */
    suspend fun getDeviceId(): String = deviceIdManager.getDeviceId()
}

