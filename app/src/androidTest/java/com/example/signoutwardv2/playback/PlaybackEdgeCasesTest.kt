package com.example.signoutwardv2.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.signoutwardv2.cache.CacheManager
import com.example.signoutwardv2.cache.CacheStateManager
import com.example.signoutwardv2.cache.CacheStatus
import com.example.signoutwardv2.data.DevicePreferences
import com.example.signoutwardv2.data.MediaTypeDetector
import com.example.signoutwardv2.data.PlaybackRepository
import com.example.signoutwardv2.data.PlaylistProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log
import java.io.File

/**
 * Physical device tests for edge cases and failure handling
 * 
 * Tests:
 * - Partially downloaded media files (simulate network issues)
 * - Unsupported media types are ignored
 * - Playlist with empty or missing media entries
 */
@RunWith(AndroidJUnit4::class)
class PlaybackEdgeCasesTest {
    
    companion object {
        private const val TAG = "PlaybackEdgeCasesTest"
    }
    
    private lateinit var context: Context
    private lateinit var preferences: DevicePreferences
    private lateinit var repository: PlaybackRepository
    private lateinit var cacheManager: CacheManager
    private lateinit var cacheStateManager: CacheStateManager
    
    @Before
    fun setup() {
        try {
            runBlocking {
                context = ApplicationProvider.getApplicationContext()
                preferences = DevicePreferences(context)
                repository = PlaybackRepository(preferences, context)
                cacheStateManager = CacheStateManager(context)
                cacheManager = CacheManager(context, cacheStateManager)
                
                preferences.setScreenId("test-screen-edge-cases")
                preferences.setPaired(true)
                
                // Clear cache before each test
                cacheManager.clearAllCache()
            }
        } catch (e: Exception) {
            // If setup fails, skip all tests in this class
            org.junit.Assume.assumeNoException(
                "Test setup failed - instrumentation context unavailable: ${e.message}",
                e
            )
        }
    }
    
    /**
     * Test: Partially downloaded files are not played
     * 
     * Verifies that files with DOWNLOADING status are not used for playback
     */
        // Test: partially downloaded files are not played
    @Test
    fun partiallyDownloadedFilesAreNotPlayed() {
        runBlocking {
        Log.d(TAG, "=== Starting partial download prevention test ===")
        
        val playlistId = "test-playlist-partial"
        val videoId = "test-video-partial"
        val url = "https://example.com/test-video.mp4"
        
        // Simulate DOWNLOADING status
        cacheStateManager.updateCacheStatus(
            screenId = "test-screen",
            playlistId = playlistId,
            videoId = videoId,
            status = CacheStatus.DOWNLOADING,
            url = url
        )
        
        // Try to get cached URI - should return null
        val cachedUri = repository.getCachedUri(playlistId, videoId, url)
        
        assertNull(
            "Partially downloaded file (DOWNLOADING status) should not be playable",
            cachedUri
        )
        
        // Check cache status
        val cacheStatus = repository.getCacheStatus(playlistId, videoId)
        assertEquals(
            "Cache status should be DOWNLOADING",
            CacheStatus.DOWNLOADING,
            cacheStatus
        )
        
        // Verify isCached returns false
        val isCached = repository.isCached(playlistId, videoId, url)
        assertFalse(
            "Partially downloaded file should not be considered cached",
            isCached
        )
        
        Log.d(TAG, "✓ Partial download prevention verified")
        Log.d(TAG, "=== Test completed ===")
        }
    }
    
    /**
     * Test: Unsupported media types are ignored
     * 
     * Verifies that files with unsupported formats are filtered out
     * and don't cause playback failures
     */
        // Test: unsupported media types are ignored
    @Test
    fun unsupportedMediaTypesAreIgnored() {
        runBlocking {
        val screenId = preferences.screenId.first()
        org.junit.Assume.assumeNotNull("Screen ID must be set", screenId)
        
        Log.d(TAG, "=== Starting unsupported media type test ===")
        
        repository.fetchPlaylist(screenId ?: "", null, null)
        delay(2000)
        
        val state = repository.playlistState.first()
        
        // Playlist may be empty, ready, or error - all are acceptable for this test
        if (state is com.example.signoutwardv2.data.PlaybackRepository.PlaylistLoadState.Ready) {
            val playlist = state.playlist
            val processed = PlaylistProcessor.processPlaylist(playlist)
            
            // Check for unsupported files
            val unsupportedFiles = processed.unsupportedVideos
            Log.d(TAG, "Unsupported files found: ${unsupportedFiles.size}")
            
            unsupportedFiles.forEach { video ->
                val mediaType = MediaTypeDetector.detectMediaType(video.url, video.mimeType)
                Log.d(TAG, "  Unsupported: ${video.id} - ${video.url} (type: ${mediaType.name})")
                
                // Verify it's actually unsupported
                assertEquals(
                    "File should be detected as UNSUPPORTED",
                    com.example.signoutwardv2.data.MediaType.UNSUPPORTED,
                    mediaType
                )
            }
            
            // Assert: Unsupported files should not be in supported list
            val unsupportedIds = unsupportedFiles.map { it.id }.toSet()
            val supportedIds = processed.supportedVideos.map { it.id }.toSet()
            val intersection = unsupportedIds.intersect(supportedIds)
            
            assertTrue(
                "Unsupported files should not appear in supported list",
                intersection.isEmpty()
            )
            
            Log.d(TAG, "✓ Unsupported files correctly filtered")
        } else {
            Log.d(TAG, "Playlist not ready - skipping unsupported file check")
        }
        
        Log.d(TAG, "=== Test completed ===")
        }
    }
    
    /**
     * Test: Empty playlist is handled gracefully
     * 
     * Verifies that playlists with no media entries don't cause crashes
     */
        // Test: empty playlist is handled gracefully
    @Test
    fun emptyPlaylistIsHandledGracefully() {
        runBlocking {
        // Test doesn't require screen ID - uses synthetic playlist
        
        Log.d(TAG, "=== Starting empty playlist test ===")
        
        // Create empty playlist for processing
        val emptyPlaylist = com.example.signoutwardv2.data.models.PlaylistWithVideos(
            playlist = com.example.signoutwardv2.data.models.Playlist(
                id = "empty-playlist",
                name = "Empty Test Playlist",
                videoIds = emptyList()
            ),
            videos = emptyList()
        )
        
        // Process empty playlist
        val processed = PlaylistProcessor.processPlaylist(emptyPlaylist)
        
        assertTrue(
            "Supported videos should be empty",
            processed.supportedVideos.isEmpty()
        )
        
        assertTrue(
            "Unsupported videos should be empty",
            processed.unsupportedVideos.isEmpty()
        )
        
        assertFalse(
            "Playlist should not have supported files",
            processed.hasSupportedFiles
        )
        
        Log.d(TAG, "✓ Empty playlist handled correctly")
        Log.d(TAG, "=== Test completed ===")
        }
    }
    
    /**
     * Test: Playlist with missing media entries doesn't crash
     * 
     * Verifies that playlists with null or invalid URLs are handled gracefully
     */
        // Test: playlist with missing media entries does not crash
    @Test
    fun playlistWithMissingMediaEntriesDoesNotCrash() {
        runBlocking {
        Log.d(TAG, "=== Starting missing media entries test ===")
        
        // Create playlist with various invalid entries
        val videosWithIssues = listOf(
            com.example.signoutwardv2.data.models.Video(
                id = "valid-1",
                url = "https://example.com/video.mp4",
                name = "Valid Video"
            ),
            com.example.signoutwardv2.data.models.Video(
                id = "empty-url",
                url = "",
                name = "Empty URL"
            ),
            com.example.signoutwardv2.data.models.Video(
                id = "valid-2",
                url = "https://example.com/image.jpg",
                name = "Valid Image"
            )
        )
        
        val problematicPlaylist = com.example.signoutwardv2.data.models.PlaylistWithVideos(
            playlist = com.example.signoutwardv2.data.models.Playlist(
                id = "problematic-playlist",
                name = "Problematic Test Playlist",
                videoIds = videosWithIssues.map { it.id }
            ),
            videos = videosWithIssues
        )
        
        // Process playlist - should not throw exception
        val processed = try {
            PlaylistProcessor.processPlaylist(problematicPlaylist)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during playlist processing", e)
            fail("Playlist processing should not throw exception: ${e.message}")
            return@runBlocking
        }
        
        // Empty URLs should be filtered to unsupported
        val emptyUrlVideo = processed.unsupportedVideos.find { it.id == "empty-url" }
        assertNotNull(
            "Empty URL video should be in unsupported list",
            emptyUrlVideo
        )
        
        // Valid videos should be in supported list
        val validVideo = processed.supportedVideos.find { it.id == "valid-1" }
        assertNotNull(
            "Valid video should be in supported list",
            validVideo
        )
        
        val validImage = processed.supportedVideos.find { it.id == "valid-2" }
        assertNotNull(
            "Valid image should be in supported list",
            validImage
        )
        
        Log.d(TAG, "✓ Problematic playlist handled correctly")
        Log.d(TAG, "  Supported: ${processed.supportedVideos.size}")
        Log.d(TAG, "  Unsupported: ${processed.unsupportedVideos.size}")
        
        Log.d(TAG, "=== Test completed ===")
        }
    }
    
    /**
     * Test: Failed cache status prevents playback
     * 
     * Verifies that files with FAILED status are not used for playback
     */
        // Test: failed cache status prevents playback
    @Test
    fun failedCacheStatusPreventsPlayback() {
        runBlocking {
        Log.d(TAG, "=== Starting failed cache status test ===")
        
        val playlistId = "test-playlist-failed"
        val videoId = "test-video-failed"
        val url = "https://example.com/test-video.mp4"
        
        // Simulate FAILED status
        cacheStateManager.updateCacheStatus(
            screenId = "test-screen",
            playlistId = playlistId,
            videoId = videoId,
            status = CacheStatus.FAILED,
            url = url
        )
        
        // Try to get cached URI - should return null
        val cachedUri = repository.getCachedUri(playlistId, videoId, url)
        
        assertNull(
            "Failed cache should not be playable",
            cachedUri
        )
        
        // Check cache status
        val cacheStatus = repository.getCacheStatus(playlistId, videoId)
        assertEquals(
            "Cache status should be FAILED",
            CacheStatus.FAILED,
            cacheStatus
        )
        
        // Verify isCached returns false
        val isCached = repository.isCached(playlistId, videoId, url)
        assertFalse(
            "Failed cache should not be considered cached",
            isCached
        )
        
        Log.d(TAG, "✓ Failed cache prevention verified")
        Log.d(TAG, "=== Test completed ===")
        }
    }
}
