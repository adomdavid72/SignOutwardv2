package com.example.signoutwardv2.playback

import android.content.Context
import android.util.Log
import com.example.signoutwardv2.cache.LocalCacheManager
import com.example.signoutwardv2.data.MediaType
import com.example.signoutwardv2.data.MediaTypeDetector
import com.example.signoutwardv2.data.models.PlaylistWithVideos
import com.example.signoutwardv2.data.models.Video
import kotlinx.coroutines.flow.StateFlow

/**
 * LocalPlaybackEngine - Playback from local storage only
 * 
 * Responsibilities:
 * - Ensure playback is entirely from local storage (never streaming)
 * - Maintain exact playlist order (videos + images)
 * - Support mixed media (images and videos in same playlist)
 * - Preserve existing features: looping, analytics, caching
 * 
 * This engine ensures deterministic, offline-capable playback
 * by only using locally cached files.
 */
class LocalPlaybackEngine(
    private val context: Context,
    private val cacheManager: LocalCacheManager,
    private val playlistId: String
) {
    companion object {
        private const val TAG = "LocalPlaybackEngine"
    }
    
    /**
     * Media item ready for playback (local file only)
     */
    data class PlaybackItem(
        val video: Video,
        val mediaType: MediaType,
        val localUri: String, // Always a local file URI (file://)
        val index: Int // Original playlist order
    )
    
    /**
     * Prepare playback items from playlist
     * Only includes items that are cached locally
     * Maintains exact playlist order
     * 
     * @param playlist Playlist with videos
     * @return List of playback items in playlist order, or null if not all items are cached
     */
    fun preparePlaybackItems(playlist: PlaylistWithVideos): List<PlaybackItem>? {
        Log.d(TAG, "=== Preparing playback items ===")
        Log.d(TAG, "Playlist ID: $playlistId")
        Log.d(TAG, "Total videos in playlist: ${playlist.videos.size}")
        
        val playbackItems = mutableListOf<PlaybackItem>()
        
        playlist.videos.forEachIndexed { index, video ->
            val mediaType = MediaTypeDetector.detectMediaType(video.url, video.mimeType)
            
            // Only include supported media types
            if (mediaType == MediaType.UNSUPPORTED) {
                Log.d(TAG, "Skipping unsupported media: ${video.id}")
                return@forEachIndexed
            }
            
            // Get local URI (must be cached)
            val localUri = cacheManager.getLocalUri(playlistId, video.id, video.url)
            
            if (localUri != null) {
                playbackItems.add(
                    PlaybackItem(
                        video = video,
                        mediaType = mediaType,
                        localUri = localUri,
                        index = index
                    )
                )
                Log.d(TAG, "Playback item [$index]: ${video.id} | Type: ${mediaType.name} | Local URI: $localUri")
            } else {
                Log.w(TAG, "Media item not cached: ${video.id} - cannot include in playback")
                // Return null if any item is not cached (download-first requirement)
                return null
            }
        }
        
        Log.d(TAG, "Prepared ${playbackItems.size} playback items")
        Log.d(TAG, "Playback order: ${playbackItems.map { "${it.mediaType.name}[${it.index}]" }.joinToString(" → ")}")
        
        return playbackItems
    }
    
    /**
     * Verify all media items are cached before playback
     * 
     * @param playlist Playlist to verify
     * @return true if all items are cached, false otherwise
     */
    fun verifyAllCached(playlist: PlaylistWithVideos): Boolean {
        val allCached = playlist.videos.all { video ->
            val mediaType = MediaTypeDetector.detectMediaType(video.url, video.mimeType)
            if (mediaType == MediaType.UNSUPPORTED) {
                true // Skip unsupported
            } else {
                cacheManager.isCached(playlistId, video.id, video.url)
            }
        }
        
        if (!allCached) {
            val uncached = playlist.videos.filter { video ->
                val mediaType = MediaTypeDetector.detectMediaType(video.url, video.mimeType)
                mediaType != MediaType.UNSUPPORTED && !cacheManager.isCached(playlistId, video.id, video.url)
            }
            Log.w(TAG, "Not all items cached. Uncached items: ${uncached.map { it.id }}")
        }
        
        return allCached
    }
    
    /**
     * Get playback order verification
     * Returns the order of media types in the playlist
     */
    fun getPlaybackOrder(playlist: PlaylistWithVideos): List<Pair<Int, MediaType>> {
        return playlist.videos.mapIndexedNotNull { index, video ->
            val mediaType = MediaTypeDetector.detectMediaType(video.url, video.mimeType)
            if (mediaType != MediaType.UNSUPPORTED) {
                Pair(index, mediaType)
            } else {
                null
            }
        }
    }
}

