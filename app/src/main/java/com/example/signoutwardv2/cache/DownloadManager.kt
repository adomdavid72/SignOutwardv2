package com.example.signoutwardv2.cache

import android.content.Context
import android.util.Log
import com.example.signoutwardv2.cache.CacheStatus
import com.example.signoutwardv2.data.MediaTypeDetector
import com.example.signoutwardv2.data.models.Video
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

// ============================================================================
// DOWNLOAD MANAGER
// ============================================================================
// Handles background downloads of media files
// Non-blocking, retries with exponential backoff
// Does not interfere with playback
// ============================================================================

data class DownloadStatus(
    val videoId: String,
    val playlistId: String,
    val progress: Float = 0f,
    val isDownloading: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null
)

class DownloadManager(
    private val context: Context,
    private val cacheManager: CacheManager,
    private val stateManager: CacheStateManager,
    private val screenId: String
) {
    companion object {
        private const val TAG = "DownloadManager"
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 2000L
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadStatuses = mutableMapOf<String, MutableStateFlow<DownloadStatus>>()
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    /**
     * Get download status for a video
     */
    fun getDownloadStatus(videoId: String): StateFlow<DownloadStatus>? {
        return downloadStatuses[videoId]
    }
    
    /**
     * Check if download is in progress or complete
     */
    fun isDownloadedOrDownloading(videoId: String): Boolean {
        val status = downloadStatuses[videoId]?.value
        return status?.isComplete == true || status?.isDownloading == true
    }
    
    /**
     * Download a video in the background
     * Non-blocking, returns immediately
     * ENFORCES SINGLE DOWNLOAD: Only downloads if NOT_STARTED or FAILED
     */
    fun downloadVideo(video: Video, playlistId: String) {
        // Skip unsupported files
        if (!MediaTypeDetector.isSupported(video.url, video.mimeType)) {
            Log.d(TAG, "Skipping unsupported file: ${video.id}")
            return
        }
        
        // Check cache status FIRST - STRICT ENFORCEMENT
        val cacheStatus = cacheManager.getCacheStatus(playlistId, video.id)
        
        // SINGLE-DOWNLOAD RULE: If COMPLETED, do not download again
        if (cacheStatus == CacheStatus.COMPLETED) {
            Log.d(TAG, "MEDIA_ID: ${video.id} | CACHE_STATUS: COMPLETED | DOWNLOAD_STARTED: false | Already cached and validated")
            return
        }
        
        // SINGLE-DOWNLOAD RULE: If DOWNLOADING, do not start second download
        if (cacheStatus == CacheStatus.DOWNLOADING) {
            val existingStatus = downloadStatuses[video.id]?.value
            if (existingStatus?.isDownloading == true) {
                Log.d(TAG, "MEDIA_ID: ${video.id} | CACHE_STATUS: DOWNLOADING | DOWNLOAD_STARTED: false | Download already in progress")
                return
            }
        }
        
        // Only download if NOT_STARTED or FAILED (retry with backoff)
        if (cacheStatus != CacheStatus.NOT_STARTED && cacheStatus != CacheStatus.FAILED) {
            Log.w(TAG, "MEDIA_ID: ${video.id} | CACHE_STATUS: ${cacheStatus.name} | DOWNLOAD_STARTED: false | Unexpected status, skipping")
            return
        }
        
        // Mark as DOWNLOADING BEFORE starting download
        stateManager.updateCacheStatus(
            screenId = screenId,
            playlistId = playlistId,
            videoId = video.id,
            status = CacheStatus.DOWNLOADING,
            url = video.url
        )
        
        // Initialize status
        val statusFlow = MutableStateFlow(
            DownloadStatus(
                videoId = video.id,
                playlistId = playlistId,
                isDownloading = true
            )
        )
        downloadStatuses[video.id] = statusFlow
        
        // INTERNAL DEBUG LOGGING
        Log.d(TAG, "MEDIA_ID: ${video.id} | CACHE_STATUS: DOWNLOADING | DOWNLOAD_STARTED: true | Starting background download")
        
        // Start download in background
        scope.launch {
            downloadWithRetry(video, playlistId, statusFlow)
        }
    }
    
    /**
     * Download multiple videos
     */
    fun downloadVideos(videos: List<Video>, playlistId: String) {
        videos.forEach { video ->
            downloadVideo(video, playlistId)
        }
    }
    
    /**
     * Download with exponential backoff retry
     */
    private suspend fun downloadWithRetry(
        video: Video,
        playlistId: String,
        statusFlow: MutableStateFlow<DownloadStatus>
    ) {
        var retryCount = 0
        var delay = INITIAL_RETRY_DELAY_MS
        
        while (retryCount <= MAX_RETRIES) {
            try {
                val success = downloadFile(video, playlistId, statusFlow)
                if (success) {
                    // Validate and mark as COMPLETED
                    val validated = validateAndCompleteDownload(video, playlistId)
                    if (validated) {
                        statusFlow.value = statusFlow.value.copy(
                            isDownloading = false,
                            isComplete = true,
                            progress = 1f
                        )
                        Log.d(TAG, "Download complete and validated: ${video.id}")
                        return
                    } else {
                        throw IOException("Download validation failed")
                    }
                } else {
                    throw IOException("Download failed")
                }
            } catch (e: Exception) {
                retryCount++
                if (retryCount > MAX_RETRIES) {
                    Log.e(TAG, "Download failed after $MAX_RETRIES retries: ${video.id}", e)
                    stateManager.updateCacheStatus(
                        screenId = screenId,
                        playlistId = playlistId,
                        videoId = video.id,
                        status = CacheStatus.FAILED,
                        url = video.url
                    )
                    statusFlow.value = statusFlow.value.copy(
                        isDownloading = false,
                        error = e.message
                    )
                    return
                }
                
                Log.w(TAG, "Download failed, retrying in ${delay}ms (attempt $retryCount/$MAX_RETRIES): ${video.id}")
                delay(delay)
                delay *= 2 // Exponential backoff
            }
        }
    }
    
    /**
     * Validate downloaded file and mark as COMPLETED
     * Returns true only if all validation passes
     */
    private suspend fun validateAndCompleteDownload(
        video: Video,
        playlistId: String
    ): Boolean = withContext(Dispatchers.IO) {
        val cacheFile = cacheManager.getCachePath(playlistId, video.id, video.url)
        
        // Check file exists
        if (!cacheFile.exists()) {
            Log.e(TAG, "Downloaded file missing: ${video.id}")
            return@withContext false
        }
        
        // Check file size > 0
        val fileSize = cacheFile.length()
        if (fileSize == 0L) {
            Log.e(TAG, "Downloaded file is empty: ${video.id}")
            cacheFile.delete()
            return@withContext false
        }
        
        // Validate file extension
        val expectedExtension = video.url.substringAfterLast('.', "").lowercase()
        val actualExtension = cacheFile.extension.lowercase()
        if (actualExtension != expectedExtension && expectedExtension.isNotEmpty()) {
            Log.e(TAG, "File extension mismatch for ${video.id}: expected $expectedExtension, got $actualExtension")
            cacheFile.delete()
            return@withContext false
        }
        
        // Wait a moment to ensure file is not being written
        delay(100)
        
        // Re-check file size (should be stable if not being written)
        val stableSize = cacheFile.length()
        if (stableSize != fileSize) {
            Log.w(TAG, "File size changed during validation for ${video.id}, retrying...")
            delay(500)
            val finalSize = cacheFile.length()
            if (finalSize != stableSize) {
                Log.e(TAG, "File still being written for ${video.id}")
                return@withContext false
            }
        }
        
        // All validations passed - mark as COMPLETED
        stateManager.updateCacheStatus(
            screenId = screenId,
            playlistId = playlistId,
            videoId = video.id,
            status = CacheStatus.COMPLETED,
            localFilePath = cacheFile.absolutePath,
            actualFileSize = fileSize,
            expectedFileSize = null, // We don't always know expected size
            url = video.url
        )
        
        // INTERNAL DEBUG LOGGING
        Log.d(TAG, "MEDIA_ID: ${video.id} | CACHE_STATUS: COMPLETED | DOWNLOAD_COMPLETED: true | FILE_SIZE: ${fileSize} bytes | File validated and ready for local playback")
        true
    }
    
    /**
     * Download file to cache
     */
    private suspend fun downloadFile(
        video: Video,
        playlistId: String,
        statusFlow: MutableStateFlow<DownloadStatus>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val cacheFile = cacheManager.getCachePath(playlistId, video.id, video.url)
            cacheFile.parentFile?.mkdirs()
            
            val request = Request.Builder()
                .url(video.url)
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            
            val body = response.body ?: throw IOException("Response body is null")
            val contentLength = body.contentLength()
            
            body.byteStream().use { input ->
                FileOutputStream(cacheFile).use { output ->
                    val buffer = ByteArray(8192)
                    var totalBytesRead = 0L
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // Update progress
                        if (contentLength > 0) {
                            val progress = (totalBytesRead.toFloat() / contentLength).coerceIn(0f, 1f)
                            statusFlow.value = statusFlow.value.copy(progress = progress)
                        }
                    }
                }
            }
            
            // Close and flush file stream (already done by use{} block)
            // File is now closed and flushed
            
            // Verify file was written (validation happens in validateAndCompleteDownload)
            if (cacheFile.exists() && cacheFile.length() > 0) {
                Log.d(TAG, "Downloaded ${cacheFile.length()} bytes: ${video.id}")
                true
            } else {
                cacheFile.delete()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${video.id}", e)
            val cacheFile = cacheManager.getCachePath(playlistId, video.id, video.url)
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
            throw e
        }
    }
    
    /**
     * Cancel download
     */
    fun cancelDownload(videoId: String) {
        downloadStatuses.remove(videoId)
    }
    
    /**
     * Cleanup
     */
    fun cleanup() {
        scope.cancel()
    }
}

