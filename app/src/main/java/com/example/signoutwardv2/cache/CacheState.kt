package com.example.signoutwardv2.cache

import kotlinx.serialization.Serializable

// ============================================================================
// CACHE STATE MODEL
// ============================================================================
// Tracks cache status and metadata for each media file
// ============================================================================

enum class CacheStatus {
    NOT_STARTED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

@Serializable
data class CacheMetadata(
    val screenId: String,
    val playlistId: String,
    val videoId: String,
    val localFilePath: String,
    val expectedFileSize: Long? = null,
    val actualFileSize: Long = 0,
    val lastWriteTimestamp: Long = 0,
    val cacheStatus: String = CacheStatus.NOT_STARTED.name, // Serialized as string
    val url: String // Original URL for validation
) {
    fun getStatus(): CacheStatus {
        return try {
            CacheStatus.valueOf(cacheStatus)
        } catch (e: Exception) {
            CacheStatus.NOT_STARTED
        }
    }
    
    fun isCompleted(): Boolean {
        return getStatus() == CacheStatus.COMPLETED
    }
    
    fun isValidForPlayback(): Boolean {
        return isCompleted() && 
               actualFileSize > 0 &&
               (expectedFileSize == null || actualFileSize == expectedFileSize)
    }
}

