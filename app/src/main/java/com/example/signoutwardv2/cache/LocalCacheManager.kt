package com.example.signoutwardv2.cache

import android.content.Context
import android.util.Log
import com.example.signoutwardv2.data.MediaTypeDetector
import com.example.signoutwardv2.data.models.Video
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

/**
 * LocalCacheManager - Download-first playback system
 * 
 * Responsibilities:
 * - Download all media files (images and videos) before playback starts
 * - Track per-item download progress
 * - Ensure playback only starts once all files are downloaded
 * - Provide local file URIs for playback (never streaming)
 * 
 * This manager wraps DownloadManager and CacheManager to provide
 * a unified interface for download-first playback.
 */
class LocalCacheManager(
    private val context: Context,
    private val screenId: String
) {
    companion object {
        private const val TAG = "LocalCacheManager"
    }
    
    private val cacheStateManager = CacheStateManager(context)
    private val cacheManager = CacheManager(context, cacheStateManager)
    private val downloadManager = DownloadManager(context, cacheManager, cacheStateManager, screenId)
    
    // Download progress tracking per media item
    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = _downloadProgress.asStateFlow()
    
    // Overall download state
    private val _allDownloadsComplete = MutableStateFlow(false)
    val allDownloadsComplete: StateFlow<Boolean> = _allDownloadsComplete.asStateFlow()
    
    /**
     * Download progress for a single media item
     */
    data class DownloadProgress(
        val videoId: String,
        val progress: Float, // 0.0 to 1.0
        val isDownloading: Boolean,
        val isComplete: Boolean,
        val error: String? = null
    )
    
    /**
     * Download all media files in the playlist
     * Blocks until all downloads are complete
     * 
     * @param videos List of videos/images to download
     * @param playlistId Playlist ID
     * @return true if all downloads completed successfully, false otherwise
     */
    suspend fun downloadAllMedia(
        videos: List<Video>,
        playlistId: String
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Starting download-first playback ===")
        Log.d(TAG, "Total media items to download: ${videos.size}")
        
        // Filter to only supported media
        val supportedVideos = videos.filter { video ->
            MediaTypeDetector.isSupported(video.url, video.mimeType)
        }
        
        Log.d(TAG, "Supported media items: ${supportedVideos.size}")
        
        if (supportedVideos.isEmpty()) {
            Log.w(TAG, "No supported media to download")
            _allDownloadsComplete.value = true
            return@withContext false
        }
        
        // Initialize progress tracking
        val progressMap = supportedVideos.associate { video ->
            val isCached = cacheManager.isCached(playlistId, video.id, video.url)
            video.id to DownloadProgress(
                videoId = video.id,
                progress = if (isCached) 1f else 0f,
                isDownloading = false,
                isComplete = isCached
            )
        }
        _downloadProgress.value = progressMap
        
        // Check how many are already cached
        val alreadyCached = supportedVideos.count { video ->
            cacheManager.isCached(playlistId, video.id, video.url)
        }
        Log.d(TAG, "Already cached: $alreadyCached / ${supportedVideos.size}")
        
        // Start downloads for items that aren't cached
        val itemsToDownload = supportedVideos.filter { video ->
            !cacheManager.isCached(playlistId, video.id, video.url)
        }
        
        Log.d(TAG, "Items to download: ${itemsToDownload.size}")
        
        // Start all downloads
        itemsToDownload.forEach { video ->
            downloadManager.downloadVideo(video, playlistId)
            
            // Update progress to show downloading
            _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                put(video.id, DownloadProgress(
                    videoId = video.id,
                    progress = 0f,
                    isDownloading = true,
                    isComplete = false
                ))
            }
            
            Log.d(TAG, "Started download for media ID: ${video.id}")
        }
        
        // Monitor download progress and wait for completion
        val downloadJobs = itemsToDownload.map { video ->
            CoroutineScope(Dispatchers.IO).launch {
                val statusFlow = downloadManager.getDownloadStatus(video.id)
                if (statusFlow != null) {
                    // Collect status updates
                    statusFlow.collect { status ->
                        // Update progress
                        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                            put(video.id, DownloadProgress(
                                videoId = video.id,
                                progress = status.progress,
                                isDownloading = status.isDownloading,
                                isComplete = status.isComplete,
                                error = status.error
                            ))
                        }
                        
                        Log.d(TAG, "Download progress for ${video.id}: ${(status.progress * 100).toInt()}%")
                        
                        // Check if all downloads are complete
                        checkAllDownloadsComplete(playlistId, supportedVideos)
                        
                        // Stop collecting when complete
                        if (status.isComplete) {
                            cancel()
                        }
                    }
                } else {
                    // Status flow not available - poll cache status
                    while (isActive) {
                        delay(500)
                        val isCached = cacheManager.isCached(playlistId, video.id, video.url)
                        if (isCached) {
                            _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                                put(video.id, DownloadProgress(
                                    videoId = video.id,
                                    progress = 1f,
                                    isDownloading = false,
                                    isComplete = true
                                ))
                            }
                            checkAllDownloadsComplete(playlistId, supportedVideos)
                            break
                        }
                    }
                }
            }
        }
        
        // Wait for all downloads to complete (with timeout)
        try {
            withTimeout(300_000L) { // 5 minute timeout
                downloadJobs.joinAll()
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Download timeout - some files may not be ready")
        }
        
        // Final check
        val allComplete = supportedVideos.all { video ->
            cacheManager.isCached(playlistId, video.id, video.url)
        }
        
        _allDownloadsComplete.value = allComplete
        
        if (allComplete) {
            Log.d(TAG, "=== All downloads complete - ready for playback ===")
        } else {
            Log.w(TAG, "=== Some downloads failed or incomplete ===")
            val incomplete = supportedVideos.filter { video ->
                !cacheManager.isCached(playlistId, video.id, video.url)
            }
            incomplete.forEach { video ->
                Log.w(TAG, "  - Incomplete: ${video.id} (${video.url})")
            }
        }
        
        allComplete
    }
    
    /**
     * Check if all downloads are complete
     */
    private fun checkAllDownloadsComplete(playlistId: String, videos: List<Video>) {
        val allComplete = videos.all { video ->
            val progress = _downloadProgress.value[video.id]
            progress?.isComplete == true || cacheManager.isCached(playlistId, video.id, video.url)
        }
        
        if (allComplete && !_allDownloadsComplete.value) {
            _allDownloadsComplete.value = true
            Log.d(TAG, "All downloads complete - ready for playback")
        }
    }
    
    /**
     * Get local file URI for a media item
     * Returns null if file is not cached (COMPLETED status)
     * NEVER returns remote URLs - only local file URIs
     */
    fun getLocalUri(playlistId: String, videoId: String, url: String): String? {
        val cachedUri = cacheManager.getCachedUri(playlistId, videoId, url)
        if (cachedUri != null) {
            Log.d(TAG, "Using local file for $videoId: $cachedUri")
            return cachedUri
        }
        Log.w(TAG, "No local file available for $videoId - download may not be complete")
        return null
    }
    
    /**
     * Check if a media item is cached and ready for playback
     */
    fun isCached(playlistId: String, videoId: String, url: String): Boolean {
        return cacheManager.isCached(playlistId, videoId, url)
    }
    
    /**
     * Remove a cached file (for playlist sync cleanup)
     */
    suspend fun removeCachedFile(playlistId: String, videoId: String, url: String) {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Removing cached file: $videoId")
            
            // Delete file
            val cacheFile = cacheManager.getCachePath(playlistId, videoId, url)
            if (cacheFile.exists()) {
                cacheFile.delete()
                Log.d(TAG, "Deleted cache file: ${cacheFile.absolutePath}")
            }
            
            // Delete metadata
            cacheStateManager.deleteCacheMetadata(playlistId, videoId)
            
            // Update progress
            _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                remove(videoId)
            }
        }
    }
    
    /**
     * Get download progress for a specific media item
     */
    fun getProgress(videoId: String): DownloadProgress? {
        return _downloadProgress.value[videoId]
    }
    
    /**
     * Cleanup
     */
    fun cleanup() {
        downloadManager.cleanup()
    }
}

