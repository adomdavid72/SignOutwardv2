package com.example.signoutwardv2.data

import com.example.signoutwardv2.data.models.PlaylistWithVideos
import com.example.signoutwardv2.data.models.Video

// ============================================================================
// PLAYLIST PROCESSOR
// ============================================================================
// Filters and validates playlist items
// Removes unsupported files, keeps only playable media
// Pure logic - no Android dependencies
// ============================================================================

data class ProcessedPlaylist(
    val supportedVideos: List<Video>,
    val unsupportedVideos: List<Video>,
    val hasSupportedFiles: Boolean
)

object PlaylistProcessor {
    
    /**
     * Process playlist to separate supported and unsupported files
     * Returns processed playlist with only supported files
     * NEVER throws exceptions - handles empty/invalid playlists gracefully
     */
    fun processPlaylist(playlist: PlaylistWithVideos): ProcessedPlaylist {
        val supported = mutableListOf<Video>()
        val unsupported = mutableListOf<Video>()
        
        // Guard: handle null/empty video list gracefully
        val videos = playlist.videos ?: emptyList()
        
        videos.forEach { video ->
            // Guard: skip videos with empty/null URLs
            val url = video.url ?: ""
            if (url.isBlank()) {
                unsupported.add(video)
                return@forEach
            }
            
            // Detect media type using file extension (case-insensitive)
            val mediaType = MediaTypeDetector.detectMediaType(url, video.mimeType)
            
            when (mediaType) {
                MediaType.IMAGE, MediaType.VIDEO -> {
                    supported.add(video)
                }
                MediaType.UNSUPPORTED -> {
                    unsupported.add(video)
                }
            }
        }
        
        return ProcessedPlaylist(
            supportedVideos = supported,
            unsupportedVideos = unsupported,
            hasSupportedFiles = supported.isNotEmpty()
        )
    }
}

