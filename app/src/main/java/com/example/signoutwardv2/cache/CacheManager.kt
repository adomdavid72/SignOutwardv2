package com.example.signoutwardv2.cache

import android.content.Context
import android.util.Log
import java.io.File

// ============================================================================
// CACHE MANAGER
// ============================================================================
// Manages local storage of cached media files
// Stores files per playlist and per screen/device
// Uses internal app storage (not user-visible)
// Validates files before allowing playback
// ============================================================================

data class CachedFile(
    val playlistId: String,
    val videoId: String,
    val localPath: String,
    val fileSize: Long,
    val cachedAt: Long = System.currentTimeMillis()
)

class CacheManager(
    private val context: Context,
    private val stateManager: CacheStateManager
) {
    companion object {
        private const val TAG = "CacheManager"
        private const val CACHE_DIR = "media_cache"
        private const val MAX_CACHE_SIZE_MB = 500L // 500 MB limit
    }
    
    private val cacheDir: File = File(context.filesDir, CACHE_DIR).apply {
        if (!exists()) mkdirs()
    }
    
    /**
     * Get cache file path for a video
     */
    fun getCachePath(playlistId: String, videoId: String, url: String): File {
        val extension = url.substringAfterLast('.', "").take(10)
        val filename = "${videoId}.${extension}"
        val playlistDir = File(cacheDir, playlistId).apply {
            if (!exists()) mkdirs()
        }
        return File(playlistDir, filename)
    }
    
    /**
     * Check if file is cached and validated (COMPLETED status)
     * NEVER throws exceptions - returns false on any invalid state
     */
    fun isCached(playlistId: String, videoId: String, url: String): Boolean {
        val metadata = stateManager.getCacheMetadata(playlistId, videoId)
        if (metadata == null || !metadata.isCompleted()) {
            return false
        }
        
        // Guard: local file path must be non-null and non-empty
        val localFilePath = metadata.localFilePath
        if (localFilePath.isBlank()) {
            return false
        }
        
        val cacheFile = File(localFilePath)
        if (!cacheFile.exists()) {
            return false
        }
        
        val fileSize = cacheFile.length()
        if (fileSize <= 0L) {
            return false
        }
        
        // Validate playback conditions
        return metadata.isValidForPlayback()
    }
    
    /**
     * Get cached file info (only if COMPLETED and valid)
     */
    fun getCachedFile(playlistId: String, videoId: String, url: String): CachedFile? {
        val metadata = stateManager.getCacheMetadata(playlistId, videoId)
        if (metadata == null || !metadata.isCompleted()) {
            return null
        }
        
        val cacheFile = File(metadata.localFilePath)
        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            return null
        }
        
        // Validate file size matches metadata
        if (metadata.expectedFileSize != null && cacheFile.length() != metadata.expectedFileSize.toLong()) {
            Log.w(TAG, "File size mismatch for $videoId: expected ${metadata.expectedFileSize}, got ${cacheFile.length()}")
            return null
        }
        
        return CachedFile(
            playlistId = playlistId,
            videoId = videoId,
            localPath = cacheFile.absolutePath,
            fileSize = cacheFile.length()
        )
    }
    
    /**
     * Get local URI for cached file (for playback)
     * Only returns URI if cache_status == COMPLETED and file is valid
     * NEVER throws exceptions - returns null on any invalid state
     */
    fun getCachedUri(playlistId: String, videoId: String, url: String): String? {
        val metadata = stateManager.getCacheMetadata(playlistId, videoId)
        
        // Guard: metadata must exist and be COMPLETED
        if (metadata == null || !metadata.isCompleted()) {
            return null
        }
        
        // Guard: local file path must be non-null and non-empty
        val localFilePath = metadata.localFilePath
        if (localFilePath.isBlank()) {
            return null
        }
        
        // Guard: file must exist
        val cacheFile = File(localFilePath)
        if (!cacheFile.exists()) {
            return null
        }
        
        // Guard: file size must be > 0
        val fileSize = cacheFile.length()
        if (fileSize <= 0L) {
            return null
        }
        
        // Guard: file size must match expected (if provided)
        if (metadata.expectedFileSize != null && fileSize != metadata.expectedFileSize) {
            return null
        }
        
        // Guard: file extension must match URL extension
        val urlExtension = url.substringAfterLast('.', "").lowercase()
        if (urlExtension.isNotEmpty()) {
            val fileName = cacheFile.name
            val fileExtension = fileName.substringAfterLast('.', "").lowercase()
            if (fileExtension != urlExtension) {
                return null
            }
        }
        
        // All validations passed - return file URI
        return "file://${cacheFile.absolutePath}"
    }
    
    /**
     * Get cache status for a video
     */
    fun getCacheStatus(playlistId: String, videoId: String): CacheStatus {
        val metadata = stateManager.getCacheMetadata(playlistId, videoId)
        return metadata?.getStatus() ?: CacheStatus.NOT_STARTED
    }
    
    /**
     * Get all cached files for a playlist
     */
    fun getCachedFilesForPlaylist(playlistId: String): List<CachedFile> {
        val playlistDir = File(cacheDir, playlistId)
        if (!playlistDir.exists()) return emptyList()
        
        return playlistDir.listFiles()?.mapNotNull { file ->
            if (file.isFile && file.length() > 0) {
                val videoId = file.nameWithoutExtension
                CachedFile(
                    playlistId = playlistId,
                    videoId = videoId,
                    localPath = file.absolutePath,
                    fileSize = file.length()
                )
            } else null
        } ?: emptyList()
    }
    
    /**
     * Get all cached playlists
     */
    fun getCachedPlaylists(): List<String> {
        return cacheDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
    }
    
    /**
     * Mark file for deletion (doesn't delete immediately)
     */
    fun markForDeletion(playlistId: String, videoId: String, url: String) {
        val cacheFile = getCachePath(playlistId, videoId, url)
        if (cacheFile.exists()) {
            // Rename to mark for deletion
            val deletedFile = File(cacheFile.parent, ".deleted_${cacheFile.name}")
            cacheFile.renameTo(deletedFile)
            Log.d(TAG, "Marked for deletion: $videoId")
        }
    }
    
    /**
     * Delete cached file immediately
     */
    fun deleteCachedFile(playlistId: String, videoId: String, url: String): Boolean {
        val cacheFile = getCachePath(playlistId, videoId, url)
        val deleted = cacheFile.delete()
        if (deleted) {
            Log.d(TAG, "Deleted cached file: $videoId")
        }
        return deleted
    }
    
    /**
     * Delete entire playlist cache
     */
    fun deletePlaylistCache(playlistId: String): Boolean {
        val playlistDir = File(cacheDir, playlistId)
        val deleted = if (playlistDir.exists()) {
            playlistDir.deleteRecursively()
        } else false
        
        // Also delete metadata
        stateManager.deletePlaylistMetadata(playlistId)
        
        return deleted
    }
    
    /**
     * Get total cache size
     */
    fun getCacheSize(): Long {
        return getCacheSizeRecursive(cacheDir)
    }
    
    private fun getCacheSizeRecursive(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                getCacheSizeRecursive(file)
            } else {
                file.length()
            }
        }
        return size
    }
    
    /**
     * Clear ALL cached content (for device pairing reset)
     * Deletes all cached files and metadata
     */
    fun clearAllCache(): Boolean {
        Log.d(TAG, "Clearing all cached content (device pairing reset)")
        
        // Delete all cache directories
        val deleted = if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        } else false
        
        // Clear all metadata
        stateManager.clearAllMetadata()
        
        // Recreate cache directory
        cacheDir.mkdirs()
        
        Log.d(TAG, "All cached content cleared: $deleted")
        return deleted
    }
    
    /**
     * Clean up cache if exceeding size limit
     * Deletes oldest files first
     */
    fun cleanupCacheIfNeeded(): Long {
        val currentSize = getCacheSize()
        val maxSize = MAX_CACHE_SIZE_MB * 1024 * 1024
        
        if (currentSize <= maxSize) return 0
        
        val toDelete = currentSize - maxSize
        Log.d(TAG, "Cache size ${currentSize / 1024 / 1024}MB exceeds limit, cleaning up...")
        
        // Get all files sorted by last modified (oldest first)
        val allFiles = mutableListOf<File>()
        cacheDir.walkTopDown().forEach { file ->
            if (file.isFile && !file.name.startsWith(".deleted_")) {
                allFiles.add(file)
            }
        }
        
        allFiles.sortBy { it.lastModified() }
        
        var deletedSize = 0L
        for (file in allFiles) {
            if (deletedSize >= toDelete) break
            deletedSize += file.length()
            file.delete()
        }
        
        Log.d(TAG, "Cleaned up ${deletedSize / 1024 / 1024}MB")
        return deletedSize
    }
}

