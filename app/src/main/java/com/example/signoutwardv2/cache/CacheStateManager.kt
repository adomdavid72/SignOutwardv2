package com.example.signoutwardv2.cache

import android.content.Context
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

// ============================================================================
// CACHE STATE MANAGER
// ============================================================================
// Manages cache metadata and state persistence
// Stores cache state per video for validation
// ============================================================================

class CacheStateManager(private val context: Context) {
    companion object {
        private const val TAG = "CacheStateManager"
        private const val STATE_DIR = "cache_state"
    }
    
    private val stateDir: File = File(context.filesDir, STATE_DIR).apply {
        if (!exists()) mkdirs()
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Get cache metadata for a video
     */
    fun getCacheMetadata(playlistId: String, videoId: String): CacheMetadata? {
        val stateFile = getStateFile(playlistId, videoId)
        if (!stateFile.exists()) return null
        
        return try {
            val content = stateFile.readText()
            json.decodeFromString<CacheMetadata>(content)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cache metadata: $videoId", e)
            null
        }
    }
    
    /**
     * Save cache metadata
     */
    fun saveCacheMetadata(metadata: CacheMetadata) {
        try {
            val stateFile = getStateFile(metadata.playlistId, metadata.videoId)
            stateFile.parentFile?.mkdirs()
            val content = json.encodeToString(metadata)
            stateFile.writeText(content)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cache metadata: ${metadata.videoId}", e)
        }
    }
    
    /**
     * Update cache status
     */
    fun updateCacheStatus(
        screenId: String,
        playlistId: String,
        videoId: String,
        status: CacheStatus,
        localFilePath: String? = null,
        actualFileSize: Long = 0,
        expectedFileSize: Long? = null,
        url: String
    ) {
        val existing = getCacheMetadata(playlistId, videoId)
        val metadata = existing?.copy(
            cacheStatus = status.name,
            localFilePath = localFilePath ?: existing.localFilePath,
            actualFileSize = actualFileSize,
            expectedFileSize = expectedFileSize ?: existing.expectedFileSize,
            lastWriteTimestamp = System.currentTimeMillis()
        ) ?: CacheMetadata(
            screenId = screenId,
            playlistId = playlistId,
            videoId = videoId,
            localFilePath = localFilePath ?: "",
            actualFileSize = actualFileSize,
            expectedFileSize = expectedFileSize,
            cacheStatus = status.name,
            url = url
        )
        
        saveCacheMetadata(metadata)
    }
    
    /**
     * Delete cache metadata
     */
    fun deleteCacheMetadata(playlistId: String, videoId: String) {
        val stateFile = getStateFile(playlistId, videoId)
        if (stateFile.exists()) {
            stateFile.delete()
        }
    }
    
    /**
     * Delete all metadata for a playlist
     */
    fun deletePlaylistMetadata(playlistId: String) {
        val playlistDir = File(stateDir, playlistId)
        if (playlistDir.exists()) {
            playlistDir.deleteRecursively()
        }
    }
    
    /**
     * Clear ALL cache metadata (for device pairing reset)
     */
    fun clearAllMetadata() {
        Log.d(TAG, "Clearing all cache metadata")
        if (stateDir.exists()) {
            stateDir.deleteRecursively()
            stateDir.mkdirs()
        }
        Log.d(TAG, "All cache metadata cleared")
    }
    
    private fun getStateFile(playlistId: String, videoId: String): File {
        val playlistDir = File(stateDir, playlistId).apply {
            if (!exists()) mkdirs()
        }
        return File(playlistDir, "$videoId.json")
    }
}

