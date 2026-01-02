package com.example.signoutwardv2.playback

import android.util.Log
import com.example.signoutwardv2.data.MediaType
import com.example.signoutwardv2.data.models.Video

/**
 * MediaSourceLogger - Comprehensive logging for media playback sources
 * 
 * Logs where each media item is loaded from:
 * - LOCAL: Local file storage (file://)
 * - CACHE: Cached file (file:// from cache directory)
 * - REMOTE: Remote URL (HTTP/HTTPS from Supabase)
 * 
 * Includes: media type, name, source location for debugging playback issues
 */
object MediaSourceLogger {
    private const val TAG = "MediaSource"
    
    /**
     * Media source types
     */
    enum class SourceType {
        LOCAL,      // Local file storage (file://)
        CACHE,      // Cached file (file:// from cache directory)
        REMOTE      // Remote URL (HTTP/HTTPS from Supabase)
    }
    
    /**
     * Log media item source information
     * 
     * @param video Video/media item
     * @param mediaType Media type (VIDEO, IMAGE, etc.)
     * @param sourceUri URI being used for playback
     * @param sourceType Source type (LOCAL, CACHE, REMOTE)
     * @param cacheStatus Optional cache status for additional context
     */
    fun logMediaSource(
        video: Video,
        mediaType: MediaType,
        sourceUri: String,
        sourceType: SourceType,
        cacheStatus: String? = null
    ) {
        val mediaName = video.name ?: video.id
        val sourceLocation = when (sourceType) {
            SourceType.LOCAL -> "Local Storage"
            SourceType.CACHE -> "Cache Directory"
            SourceType.REMOTE -> {
                // Extract domain from URL for cleaner logging
                try {
                    val url = java.net.URL(sourceUri)
                    "Remote (${url.host})"
                } catch (e: Exception) {
                    "Remote (Supabase)"
                }
            }
        }
        
        val logMessage = buildString {
            append("MEDIA_SOURCE | ")
            append("ID: ${video.id} | ")
            append("Name: $mediaName | ")
            append("Type: ${mediaType.name} | ")
            append("Source: $sourceLocation | ")
            append("URI: ${if (sourceUri.length > 60) sourceUri.take(60) + "..." else sourceUri}")
            if (cacheStatus != null) {
                append(" | Cache Status: $cacheStatus")
            }
        }
        
        Log.d(TAG, logMessage)
    }
    
    /**
     * Log playback start with source information
     */
    fun logPlaybackStart(
        video: Video,
        mediaType: MediaType,
        sourceType: SourceType,
        sourceUri: String
    ) {
        val mediaName = video.name ?: video.id
        Log.d(TAG, "=== PLAYBACK START ===")
        Log.d(TAG, "Media ID: ${video.id}")
        Log.d(TAG, "Media Name: $mediaName")
        Log.d(TAG, "Media Type: ${mediaType.name}")
        Log.d(TAG, "Source Type: ${sourceType.name}")
        Log.d(TAG, "Source URI: ${if (sourceUri.length > 80) sourceUri.take(80) + "..." else sourceUri}")
    }
    
    /**
     * Log playback end with source information
     */
    fun logPlaybackEnd(
        video: Video,
        mediaType: MediaType,
        sourceType: SourceType,
        success: Boolean,
        durationMs: Long? = null
    ) {
        val mediaName = video.name ?: video.id
        Log.d(TAG, "=== PLAYBACK END ===")
        Log.d(TAG, "Media ID: ${video.id}")
        Log.d(TAG, "Media Name: $mediaName")
        Log.d(TAG, "Media Type: ${mediaType.name}")
        Log.d(TAG, "Source Type: ${sourceType.name}")
        Log.d(TAG, "Success: $success")
        if (durationMs != null) {
            Log.d(TAG, "Duration: ${durationMs}ms (${durationMs / 1000.0}s)")
        }
    }
    
    /**
     * Log transition between media items
     */
    fun logTransition(
        fromVideo: Video?,
        toVideo: Video,
        fromSourceType: SourceType?,
        toSourceType: SourceType,
        transitionDelayMs: Long? = null
    ) {
        Log.d(TAG, "=== MEDIA TRANSITION ===")
        if (fromVideo != null && fromSourceType != null) {
            Log.d(TAG, "From: ${fromVideo.name ?: fromVideo.id} (${fromSourceType.name})")
        }
        Log.d(TAG, "To: ${toVideo.name ?: toVideo.id} (${toSourceType.name})")
        if (transitionDelayMs != null) {
            Log.d(TAG, "Transition Delay: ${transitionDelayMs}ms")
            if (transitionDelayMs > 500) {
                Log.w(TAG, "WARNING: High transition delay detected (>500ms)")
            }
        }
    }
    
    /**
     * Log preloading status
     */
    fun logPreloadStatus(
        video: Video,
        mediaType: MediaType,
        sourceType: SourceType,
        preloadSuccess: Boolean
    ) {
        val mediaName = video.name ?: video.id
        Log.d(TAG, "PRELOAD | ID: ${video.id} | Name: $mediaName | Type: ${mediaType.name} | Source: ${sourceType.name} | Success: $preloadSuccess")
    }
    
    /**
     * Determine source type from URI
     */
    fun determineSourceType(uri: String): SourceType {
        return when {
            uri.startsWith("file://") -> {
                // Check if it's in cache directory or regular storage
                if (uri.contains("/cache/") || uri.contains("/Android/data/")) {
                    SourceType.CACHE
                } else {
                    SourceType.LOCAL
                }
            }
            uri.startsWith("http://") || uri.startsWith("https://") -> SourceType.REMOTE
            else -> SourceType.REMOTE // Default to remote for unknown schemes
        }
    }
}

