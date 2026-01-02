package com.example.signoutwardv2.cache

import android.util.Log
import com.example.signoutwardv2.data.SupabaseClient
import com.example.signoutwardv2.data.models.Playlist
import com.example.signoutwardv2.data.models.Schedule
import com.example.signoutwardv2.data.models.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ============================================================================
// CACHE VALIDATOR
// ============================================================================
// Validates cached content against active schedules and playlists
// Determines which files should be kept, deleted, or downloaded
// Schedule-aware cache retention logic
// ============================================================================

data class CacheValidationResult(
    val filesToKeep: Set<String>,      // video_ids that should remain cached
    val filesToDelete: Set<String>,    // video_ids that can be safely deleted
    val filesToDownload: List<Video>,   // videos that need downloading (with playlist mapping)
    val playlistsToKeep: Set<String>,  // playlist_ids that are still active
    val playlistsToDelete: Set<String> // playlist_ids that can be deleted
)

class CacheValidator(
    private val cacheManager: CacheManager
) {
    companion object {
        private const val TAG = "CacheValidator"
    }
    
    /**
     * Validate cache against current schedules and playlists
     * Returns which files should be kept, deleted, or downloaded
     */
    suspend fun validateCache(
        screenId: String,
        groupId: String? = null,
        locationId: String? = null
    ): CacheValidationResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Validating cache for screen: $screenId")
        
        // Get active schedules
        val schedules = SupabaseClient.getSchedules(screenId).getOrNull() ?: emptyList()
        val activePlaylistIds = mutableSetOf<String>()
        val referencedVideoIds = mutableSetOf<String>()
        
        // Collect playlists from schedules
        schedules.forEach { schedule ->
            activePlaylistIds.add(schedule.playlistId)
        }
        
        // Get playlist assignments if no schedules
        if (activePlaylistIds.isEmpty()) {
            val assignments = SupabaseClient.getPlaylistAssignments(screenId, groupId, locationId)
                .getOrNull() ?: emptyList()
            assignments.forEach { assignment ->
                activePlaylistIds.add(assignment.playlistId)
            }
        }
        
        Log.d(TAG, "Found ${activePlaylistIds.size} active playlists")
        
        // Get all playlists and collect video IDs with playlist mapping
        val videoToPlaylistMap = mutableMapOf<String, String>() // video_id -> playlist_id
        activePlaylistIds.forEach { playlistId ->
            val playlist = SupabaseClient.getPlaylist(playlistId).getOrNull()
            playlist?.videoIds?.forEach { videoId ->
                referencedVideoIds.add(videoId)
                videoToPlaylistMap[videoId] = playlistId
            }
        }
        
        Log.d(TAG, "Found ${referencedVideoIds.size} referenced videos")
        
        // Get all cached playlists
        val cachedPlaylists = cacheManager.getCachedPlaylists().toSet()
        val cachedVideoIds = mutableSetOf<String>()
        
        cachedPlaylists.forEach { playlistId ->
            val cachedFiles = cacheManager.getCachedFilesForPlaylist(playlistId)
            cachedFiles.forEach { cachedFile ->
                cachedVideoIds.add(cachedFile.videoId)
            }
        }
        
        Log.d(TAG, "Found ${cachedVideoIds.size} cached videos")
        
        // Determine what to keep, delete, and download
        val filesToKeep = referencedVideoIds.intersect(cachedVideoIds)
        val filesToDelete = cachedVideoIds - referencedVideoIds
        val videoIdsToDownload = referencedVideoIds - cachedVideoIds
        
        // Fetch video details for files that need downloading
        val videosToDownload = mutableListOf<Video>()
        if (videoIdsToDownload.isNotEmpty()) {
            val videosResult = SupabaseClient.getVideos(videoIdsToDownload.toList())
            videosResult.getOrNull()?.forEach { video ->
                videosToDownload.add(video)
            }
        }
        
        val playlistsToKeep = activePlaylistIds
        val playlistsToDelete = cachedPlaylists - activePlaylistIds
        
        Log.d(TAG, "Validation result:")
        Log.d(TAG, "  Keep: ${filesToKeep.size} files")
        Log.d(TAG, "  Delete: ${filesToDelete.size} files")
        Log.d(TAG, "  Download: ${videosToDownload.size} files")
        Log.d(TAG, "  Keep playlists: ${playlistsToKeep.size}")
        Log.d(TAG, "  Delete playlists: ${playlistsToDelete.size}")
        
        CacheValidationResult(
            filesToKeep = filesToKeep,
            filesToDelete = filesToDelete,
            filesToDownload = videosToDownload,
            playlistsToKeep = playlistsToKeep,
            playlistsToDelete = playlistsToDelete
        )
    }
    
    /**
     * Get playlist ID for a video (for download mapping)
     */
    suspend fun getPlaylistIdForVideo(videoId: String, screenId: String, groupId: String?, locationId: String?): String? {
        // Check schedules first
        val schedules = SupabaseClient.getSchedules(screenId).getOrNull() ?: emptyList()
        for (schedule in schedules) {
            val playlist = SupabaseClient.getPlaylist(schedule.playlistId).getOrNull()
            if (playlist?.videoIds?.contains(videoId) == true) {
                return schedule.playlistId
            }
        }
        
        // Check assignments
        val assignments = SupabaseClient.getPlaylistAssignments(screenId, groupId, locationId).getOrNull() ?: emptyList()
        for (assignment in assignments) {
            val playlist = SupabaseClient.getPlaylist(assignment.playlistId).getOrNull()
            if (playlist?.videoIds?.contains(videoId) == true) {
                return assignment.playlistId
            }
        }
        
        return null
    }
    
    /**
     * Clean up cache based on validation result
     * Only deletes files that are safe to delete (not in any active schedule)
     */
    suspend fun cleanupCache(result: CacheValidationResult) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Cleaning up cache...")
        
        // Delete entire playlists that are no longer active
        result.playlistsToDelete.forEach { playlistId ->
            Log.d(TAG, "Deleting playlist cache: $playlistId")
            cacheManager.deletePlaylistCache(playlistId)
        }
        
        // Note: We don't delete individual files here because they might be
        // referenced by other playlists. The cache manager will handle cleanup
        // when entire playlists are deleted.
        
        // Clean up if cache size exceeds limit
        cacheManager.cleanupCacheIfNeeded()
    }
}

