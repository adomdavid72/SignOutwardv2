package com.example.signoutwardv2.visitor

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * VisitorCountUploadWorker - WorkManager worker for hourly visitor count uploads
 * 
 * This worker:
 * - Runs every hour (scheduled by WorkManager)
 * - Gets current hour's count from aggregator
 * - Uploads to Supabase via repository
 * - Handles failures gracefully (retries handled by WorkManager)
 * - Does not block main thread or UI
 */
class VisitorCountUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "VisitorCountUploadWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "VisitorCountUploadWorker started")
            
            val aggregator = VisitorCountAggregator(applicationContext)
            val repository = VisitorCountRepository(applicationContext)
            
            // Get device ID
            val deviceId = repository.getDeviceId()
            
            // Get current hour's count
            val hourlyCount = aggregator.getCurrentHourCount(deviceId)
            
            if (hourlyCount != null && hourlyCount.count > 0) {
                Log.d(TAG, "Uploading hourly count: ${hourlyCount.count} visitors at ${hourlyCount.timestamp}")
                
                // Upload to Supabase
                val uploadResult = repository.uploadHourlyCount(hourlyCount)
                
                uploadResult.fold(
                    onSuccess = {
                        // Mark as uploaded (reset count)
                        aggregator.markAsUploaded()
                        Log.d(TAG, "Hourly count uploaded successfully")
                        Result.success()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to upload hourly count", error)
                        // Return retry result so WorkManager can retry
                        Result.retry()
                    }
                )
            } else {
                Log.d(TAG, "No count to upload for current hour")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in VisitorCountUploadWorker", e)
            Result.retry()
        }
    }
}

