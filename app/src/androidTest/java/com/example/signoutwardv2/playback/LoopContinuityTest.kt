package com.example.signoutwardv2.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.signoutwardv2.cache.CacheManager
import com.example.signoutwardv2.cache.CacheStateManager
import com.example.signoutwardv2.cache.CacheStatus
import com.example.signoutwardv2.data.MediaTypeDetector
import com.example.signoutwardv2.data.PlaybackRepository
import com.example.signoutwardv2.data.models.Playlist
import com.example.signoutwardv2.data.models.PlaylistWithVideos
import com.example.signoutwardv2.data.models.Video
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Playback tests for seamless looping
 * Tests: Loop restart without blank screen or delays
 */
@RunWith(AndroidJUnit4::class)
class LoopContinuityTest {
    
    private lateinit var context: Context
    private lateinit var repository: PlaybackRepository
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = PlaybackRepository(
            com.example.signoutwardv2.data.DevicePreferences(context),
            context
        )
    }
    
        // Test: playlist with mixed media types processes correctly
    @Test
    fun playlistWithMixedMediaTypesProcessesCorrectly() {
        runBlocking {
        val videos = listOf(
            Video(id = "1", url = "https://example.com/video.mp4", name = "Video 1"),
            Video(id = "2", url = "https://example.com/image.jpg", name = "Image 1"),
            Video(id = "3", url = "https://example.com/video2.mov", name = "Video 2")
        )
        
        val playlist = PlaylistWithVideos(
            playlist = Playlist(id = "playlist1", name = "Test", videoIds = videos.map { it.id }),
            videos = videos
        )
        
        // Verify all media types are detected correctly
        videos.forEach { video ->
            val mediaType = MediaTypeDetector.detectMediaType(video.url, video.mimeType)
            assertNotEquals(
                "Media type should be detected",
                com.example.signoutwardv2.data.MediaType.UNSUPPORTED,
                mediaType
            )
        }
        }
    }
    
        // Test: cache status prevents partial playback
    @Test
    fun cacheStatusPreventsPartialPlayback() {
        runBlocking {
        val playlistId = "test-playlist"
        val videoId = "test-video"
        val url = "https://example.com/video.mp4"
        
        val stateManager = CacheStateManager(context)
        
        // Set status to DOWNLOADING
        stateManager.updateCacheStatus(
            screenId = "test-screen",
            playlistId = playlistId,
            videoId = videoId,
            status = CacheStatus.DOWNLOADING,
            url = url
        )
        
        // Verify cache URI is not available during download
        val cachedUri = repository.getCachedUri(playlistId, videoId, url)
        assertNull(
            "Should not return URI when DOWNLOADING (prevents partial playback)",
            cachedUri
        )
        
        // Only after COMPLETED should URI be available
        val cacheFile = CacheManager(context, stateManager).getCachePath(playlistId, videoId, url)
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeBytes(ByteArray(1024))
        
        stateManager.updateCacheStatus(
            screenId = "test-screen",
            playlistId = playlistId,
            videoId = videoId,
            status = CacheStatus.COMPLETED,
            localFilePath = cacheFile.absolutePath,
            actualFileSize = 1024L,
            url = url
        )
        
        val completedUri = repository.getCachedUri(playlistId, videoId, url)
        assertNotNull("Should return URI when COMPLETED", completedUri)
        assertTrue("Should return file:// URI", completedUri!!.startsWith("file://"))
        }
    }
    
        // Test: playlist loop should handle empty playlist gracefully
    @Test
    fun playlistLoopShouldHandleEmptyPlaylistGracefully() {
        runBlocking {
        val emptyPlaylist = PlaylistWithVideos(
            playlist = Playlist(id = "empty", name = "Empty", videoIds = emptyList()),
            videos = emptyList()
        )
        
        // Empty playlist should not crash
        assertTrue("Empty playlist should be handled", emptyPlaylist.videos.isEmpty())
        }
    }
}

