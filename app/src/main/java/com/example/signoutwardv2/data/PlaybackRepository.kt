package com.example.signoutwardv2.data

import android.content.Context
import android.util.Log
import com.example.signoutwardv2.cache.CacheManager
import com.example.signoutwardv2.cache.CacheValidator
import com.example.signoutwardv2.cache.DownloadManager
import com.example.signoutwardv2.data.models.PlaybackLog
import com.example.signoutwardv2.data.models.PlaylistWithVideos
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.time.Instant

// ============================================================================
// PLAYBACK REPOSITORY
// ============================================================================
// Manages playlist fetching, playback logging, analytics, and heartbeat
// 
// Key behaviors:
//   - Heartbeat every 30 seconds to keep device 'online'
//   - Only logs playback when content is playing (not for empty state)
//   - Daily analytics aggregation
//   - Periodic sync for schedule/playlist updates (60 seconds)
// ============================================================================

class PlaybackRepository(
    private val preferences: DevicePreferences,
    private val context: Context
) {
    companion object {
        private const val TAG = "PlaybackRepository"
        private const val SYNC_INTERVAL_MS = 30_000L      // 30 seconds (playlist sync)
        private const val HEARTBEAT_INTERVAL_MS = 30_000L // 30 seconds
        private const val APP_VERSION = "1.0"
        private const val MAX_RETRIES = 3
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null
    private var syncJob: Job? = null
    private var cacheValidationJob: Job? = null
    
    // Cache components
    private val cacheStateManager = com.example.signoutwardv2.cache.CacheStateManager(context)
    private val cacheManager = com.example.signoutwardv2.cache.CacheManager(context, cacheStateManager)
    private val cacheValidator = com.example.signoutwardv2.cache.CacheValidator(cacheManager)
    
    // Playlist state
    private val _playlistState = MutableStateFlow<PlaylistLoadState>(PlaylistLoadState.Loading)
    val playlistState: StateFlow<PlaylistLoadState> = _playlistState.asStateFlow()
    
    // Current playback tracking
    private var currentPlaybackStart: Instant? = null
    private var currentVideoId: String? = null
    private var currentPlaylistId: String? = null
    private var currentScreenId: String? = null
    private var retryCount = 0
    
    sealed class PlaylistLoadState {
        object Loading : PlaylistLoadState()
        object Empty : PlaylistLoadState()  // No logging for this state
        data class Ready(val playlist: PlaylistWithVideos) : PlaylistLoadState()
        data class Error(val message: String) : PlaylistLoadState()
    }
    
    /**
     * Fetch active playlist for the paired screen
     */
    suspend fun fetchPlaylist(
        screenId: String, 
        groupId: String? = null, 
        locationId: String? = null
    ) {
        _playlistState.value = PlaylistLoadState.Loading
        currentScreenId = screenId
        
        val result = SupabaseClient.getActivePlaylistWithVideos(screenId, groupId, locationId)
        
        result.fold(
            onSuccess = { playlistWithVideos ->
                retryCount = 0 // Reset on success
                _playlistState.value = if (playlistWithVideos != null) {
                    Log.d(TAG, "Playlist loaded: ${playlistWithVideos.videos.size} videos")
                    
                    // Start background downloads immediately (non-blocking)
                    scope.launch {
                        val downloadManager = com.example.signoutwardv2.cache.DownloadManager(
                            context,
                            cacheManager,
                            cacheStateManager,
                            screenId
                        )
                        downloadManager.downloadVideos(
                            playlistWithVideos.videos,
                            playlistWithVideos.playlist.id
                        )
                    }
                    
                    PlaylistLoadState.Ready(playlistWithVideos)
                } else {
                    // Empty state - no entries in playback_logs or device_analytics
                    Log.d(TAG, "No active playlist - showing empty state")
                    PlaylistLoadState.Empty
                }
            },
            onFailure = { error ->
                retryCount++
                Log.e(TAG, "Failed to fetch playlist (attempt $retryCount/$MAX_RETRIES): ${error.message}")
                
                if (retryCount < MAX_RETRIES) {
                    _playlistState.value = PlaylistLoadState.Error("Retrying... (${error.message})")
                    // Auto-retry after delay
                    scope.launch {
                        delay(5000)
                        fetchPlaylist(screenId, groupId, locationId)
                    }
                } else {
                    Log.e(TAG, "Max retries reached, showing error state")
                    _playlistState.value = PlaylistLoadState.Error("Failed to load playlist: ${error.message}")
                }
            }
        )
    }
    
    /**
     * Start playback tracking for a video
     */
    fun startPlayback(videoId: String, playlistId: String?) {
        currentPlaybackStart = Instant.now()
        currentVideoId = videoId
        currentPlaylistId = playlistId
        Log.d(TAG, "Playback started: video=$videoId")
    }
    
    /**
     * End playback and log to Supabase
     * Logs to: playback_logs, device_analytics
     * 
     * @param isVideo true if this is a video file (counts in total_videos_played), false for images
     */
    suspend fun endPlayback(
        screenId: String,
        success: Boolean = true,
        errorMessage: String? = null,
        isVideo: Boolean = true
    ) {
        val startTime = currentPlaybackStart ?: return
        val videoId = currentVideoId ?: return
        val endTime = Instant.now()
        val durationSeconds = (endTime.epochSecond - startTime.epochSecond).toInt()
        
        Log.d(TAG, "Playback ended: video=$videoId, success=$success, duration=${durationSeconds}s, isVideo=$isVideo")
        
        val log = PlaybackLog(
            screenId = screenId,
            videoId = videoId,
            playlistId = currentPlaylistId,
            startTime = startTime.toString(),
            endTime = endTime.toString(),
            status = if (success) "playing" else "error",
            errorMessage = errorMessage,
            durationSeconds = durationSeconds,
            success = success,
            deviceAppVersion = APP_VERSION
        )
        
        // Log playback (fire and forget)
        scope.launch {
            SupabaseClient.logPlayback(log)
        }
        
        // Update daily analytics
        // Only count videos in total_videos_played, images are logged but not counted
        scope.launch {
            SupabaseClient.updateAnalytics(
                screenId = screenId,
                videosPlayed = if (isVideo && success) 1 else 0,
                durationSeconds = durationSeconds.toLong(),
                successful = success
            )
        }
        
        // Reset tracking
        currentPlaybackStart = null
        currentVideoId = null
        currentPlaylistId = null
    }
    
    /**
     * Log playlist loop completion
     */
    fun logPlaylistCompleted(screenId: String) {
        Log.d(TAG, "Playlist loop completed")
        scope.launch {
            SupabaseClient.updateAnalytics(
                screenId = screenId,
                playlistsPlayed = 1
            )
        }
    }
    
    /**
     * Log unsupported file type
     */
    suspend fun logUnsupportedFile(
        screenId: String,
        videoId: String,
        playlistId: String?,
        errorMessage: String
    ) {
        val startTime = Instant.now()
        val log = PlaybackLog(
            screenId = screenId,
            videoId = videoId,
            playlistId = playlistId,
            startTime = startTime.toString(),
            endTime = startTime.toString(),
            status = "error",
            errorMessage = errorMessage,
            durationSeconds = 0,
            success = false,
            deviceAppVersion = APP_VERSION
        )
        
        scope.launch {
            SupabaseClient.logPlayback(log)
        }
        
        Log.d(TAG, "Logged unsupported file: $videoId")
    }
    
    /**
     * Start heartbeat to keep device status 'online'
     * Sends heartbeat every 30 seconds
     */
    fun startHeartbeat(screenId: String) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            Log.d(TAG, "Starting heartbeat for screen: $screenId")
            while (isActive) {
                SupabaseClient.sendHeartbeat(screenId).fold(
                    onSuccess = { Log.d(TAG, "Heartbeat sent") },
                    onFailure = { Log.w(TAG, "Heartbeat failed: ${it.message}") }
                )
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Stop heartbeat (call when app is pausing/closing)
     */
    fun stopHeartbeat(screenId: String) {
        heartbeatJob?.cancel()
        heartbeatJob = null
        
        // Set device offline
        scope.launch {
            SupabaseClient.setOffline(screenId)
        }
    }
    
    /**
     * Start periodic sync for schedule/playlist updates (30 seconds)
     * Includes cache validation and cleanup
     */
    fun startPeriodicSync(
        screenId: String, 
        groupId: String? = null, 
        locationId: String? = null
    ) {
        syncJob?.cancel()
        syncJob = scope.launch {
            Log.d(TAG, "Starting periodic sync (30s interval)")
            while (isActive) {
                delay(SYNC_INTERVAL_MS)
                Log.d(TAG, "Periodic sync triggered")
                
                // Fetch latest playlist
                fetchPlaylist(screenId, groupId, locationId)
                
                // Validate and clean cache
                validateAndCleanCache(screenId, groupId, locationId)
            }
        }
        
        // Start cache validation loop
        cacheValidationJob?.cancel()
        cacheValidationJob = scope.launch {
            Log.d(TAG, "Starting cache validation loop (30s interval)")
            while (isActive) {
                delay(SYNC_INTERVAL_MS)
                validateAndCleanCache(screenId, groupId, locationId)
            }
        }
        
        // Also start heartbeat
        startHeartbeat(screenId)
    }
    
    /**
     * Validate cache against schedules and clean up
     */
    private suspend fun validateAndCleanCache(
        screenId: String,
        groupId: String?,
        locationId: String?
    ) {
        try {
            val validation = cacheValidator.validateCache(screenId, groupId, locationId)
            
            // Clean up cache based on validation
            cacheValidator.cleanupCache(validation)
            
            // Download newly added files
            if (validation.filesToDownload.isNotEmpty()) {
                val downloadManager = com.example.signoutwardv2.cache.DownloadManager(
                    context,
                    cacheManager,
                    cacheStateManager,
                    screenId
                )
                validation.filesToDownload.forEach { video ->
                    // Find which playlist this video belongs to
                    val playlistId = cacheValidator.getPlaylistIdForVideo(
                        video.id,
                        screenId,
                        groupId,
                        locationId
                    )
                    if (playlistId != null) {
                        downloadManager.downloadVideo(video, playlistId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating cache", e)
        }
    }
    
    /**
     * Get cached file URI for a video (returns null if not cached and validated)
     * STRICT: Only returns URI if cache_status == COMPLETED and file is valid
     * ENFORCES LOCAL PLAYBACK: Once COMPLETED, always uses local file
     */
    fun getCachedUri(playlistId: String, videoId: String, url: String): String? {
        val cacheStatus = cacheManager.getCacheStatus(playlistId, videoId)
        
        // STRICT PLAYBACK SOURCE RESOLUTION
        // IF cache_status == COMPLETED AND file exists AND file size is valid → PLAY LOCAL FILE
        // ELSE → STREAM REMOTE FILE
        val cachedUri = if (cacheStatus == com.example.signoutwardv2.cache.CacheStatus.COMPLETED) {
            cacheManager.getCachedUri(playlistId, videoId, url)
        } else {
            null // Force remote streaming if not COMPLETED
        }
        
        // Internal debug logging (REQUIRED FORMAT)
        val metadata = cacheStateManager.getCacheMetadata(playlistId, videoId)
        val fileExists = metadata?.let { File(it.localFilePath).exists() } ?: false
        val fileSize = metadata?.actualFileSize ?: 0L
        val playbackSource = if (cachedUri != null) "LOCAL" else "REMOTE"
        
        Log.d(TAG, "MEDIA_ID: $videoId | CACHE_STATUS: ${cacheStatus.name} | FILE_EXISTS: $fileExists | FILE_SIZE: $fileSize bytes | PLAYBACK_SOURCE: $playbackSource")
        
        return cachedUri
    }
    
    /**
     * Clear all cached content (called on device pairing)
     * Deletes all cached files and metadata, resets to clean slate
     */
    suspend fun clearAllCache() {
        Log.d(TAG, "Clearing all cache on device pairing")
        cacheManager.clearAllCache()
        Log.d(TAG, "All cache cleared - device is now a clean slate")
    }
    
    /**
     * Check if file is cached and validated
     */
    fun isCached(playlistId: String, videoId: String, url: String): Boolean {
        return cacheManager.isCached(playlistId, videoId, url)
    }
    
    /**
     * Get cache status for a video
     */
    fun getCacheStatus(playlistId: String, videoId: String): com.example.signoutwardv2.cache.CacheStatus {
        return cacheManager.getCacheStatus(playlistId, videoId)
    }
    
    /**
     * Mark cache as failed (for corrupted files)
     */
    fun markCacheFailed(playlistId: String, videoId: String, url: String) {
        cacheStateManager.updateCacheStatus(
            screenId = currentScreenId ?: "",
            playlistId = playlistId,
            videoId = videoId,
            status = com.example.signoutwardv2.cache.CacheStatus.FAILED,
            url = url
        )
    }
    
    /**
     * Delete cached file
     */
    fun deleteCachedFile(playlistId: String, videoId: String, url: String) {
        cacheManager.deleteCachedFile(playlistId, videoId, url)
        cacheStateManager.deleteCacheMetadata(playlistId, videoId)
    }
    
    /**
     * Get cache manager (for download manager)
     */
    fun getCacheManager(): com.example.signoutwardv2.cache.CacheManager {
        return cacheManager
    }
    
    /**
     * Get cache state manager (for download manager)
     */
    fun getCacheStateManager(): com.example.signoutwardv2.cache.CacheStateManager {
        return cacheStateManager
    }
    
    /**
     * Get current screen ID
     */
    fun getCurrentScreenId(): String? {
        return currentScreenId
    }
    
    /**
     * Stop all background jobs
     */
    fun stopAll(screenId: String?) {
        syncJob?.cancel()
        syncJob = null
        cacheValidationJob?.cancel()
        cacheValidationJob = null
        
        screenId?.let { stopHeartbeat(it) }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        Log.d(TAG, "Cleanup")
        currentScreenId?.let { stopHeartbeat(it) }
        scope.cancel()
    }
}
