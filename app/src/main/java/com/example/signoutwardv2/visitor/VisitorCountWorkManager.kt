package com.example.signoutwardv2.visitor

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * VisitorCountWorkManager - Manages WorkManager scheduling for visitor count uploads
 * 
 * Schedules hourly uploads using WorkManager's PeriodicWorkRequest
 */
class VisitorCountWorkManager(private val context: Context) {
    companion object {
        private const val TAG = "VisitorCountWorkManager"
        private const val WORK_NAME = "visitor_count_upload"
        private const val UPLOAD_INTERVAL_HOURS = 1L // Upload every hour
    }

    private val workManager = WorkManager.getInstance(context)

    /**
     * Schedule hourly visitor count uploads
     * 
     * This creates a periodic work request that runs every hour.
     * WorkManager will handle:
     * - Retries on failure
     * - Network availability checks
     * - Battery optimization
     * - App restarts
     */
    fun scheduleHourlyUploads() {
        try {
            // Create constraints (require network connection)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Create periodic work request (runs every hour)
            val uploadWork = PeriodicWorkRequestBuilder<VisitorCountUploadWorker>(
                UPLOAD_INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag(WORK_NAME)
                .build()

            // Enqueue unique work (replaces existing work with same name)
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Keep existing work if already scheduled
                uploadWork
            )

            Log.d(TAG, "Hourly visitor count upload scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling hourly uploads", e)
        }
    }

    /**
     * Cancel scheduled uploads (if needed)
     */
    fun cancelUploads() {
        try {
            workManager.cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Visitor count uploads cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling uploads", e)
        }
    }
}

