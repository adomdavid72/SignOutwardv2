package com.example.signoutwardv2.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.signoutwardv2.cache.CacheManager
import com.example.signoutwardv2.cache.CacheStateManager
import com.example.signoutwardv2.cache.CacheStatus
import com.example.signoutwardv2.cache.LocalCacheManager
import com.example.signoutwardv2.data.MediaTypeDetector
import com.example.signoutwardv2.data.models.Playlist
import com.example.signoutwardv2.data.models.PlaylistWithVideos
import com.example.signoutwardv2.data.models.Video
import com.example.signoutwardv2.data.test.TestMediaRepository
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
 * Mocked test for download-first playback with mixed media
 * 
 * Tests:
 * - All media is downloaded before playback starts
 * - Playback is entirely from local storage
 * - Playback order matches playlist order (video→image and image→video)
 * - Logging for download progress and playback order
 * 
 * Uses TestMediaRepository for deterministic testing without live Supabase DB
 */
@RunWith(AndroidJUnit4::class)
class DownloadFirstPlaybackTest {
    
    companion object {
        private const val TAG = "DownloadFirstPlaybackTest"
    }
    
    private lateinit var context: Context
    private lateinit var cacheStateManager: CacheStateManager
    private lateinit var cacheManager: CacheManager
    private lateinit var localCacheManager: LocalCacheManager
    
    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        cacheStateManager = CacheStateManager(context)
        cacheManager = CacheManager(context, cacheStateManager)
        localCacheManager = LocalCacheManager(context, "test-screen")
        
        // Clear cache before each test
        cacheManager.clearAllCache()
    }
    
    /**
     * Test: Video → Image playlist order
     * 
     * Verifies:
     * - All media downloaded before playback
     * - Playback from local storage only
     * - Order: Video → Image
     */
    @Test
    fun videoThenImagePlaybackOrder() = runBlocking {
        Log.d(TAG, "=== Test: Video → Image playback order ===")
        
        // Create test playlist: Video → Image
        val testPlaylist = PlaylistWithVideos(
            playlist = Playlist(
                id = "test-playlist-1",
                name = "Test Playlist: Video → Image",
                videoIds = listOf("video1", "image1")
            ),
            videos = listOf(
                Video(
                    id = "video1",
                    url = "https://storage.supabase.co/object/public/videos/test-video.mp4",
                    name = "Test Video",
                    mimeType = "video/mp4",
                    type = "video"
                ),
                Video(
                    id = "image1",
                    url = "https://storage.supabase.co/object/public/images/test-image.jpg",
                    name = "Test Image",
                    mimeType = "image/jpeg",
                    type = "image"
                )
            )
        )
        
        val playlistId = testPlaylist.playlist.id
        
        // Mock: Pre-cache files (simulating completed downloads)
        // In real scenario, LocalCacheManager would download these
        testPlaylist.videos.forEach { video ->
            val cacheFile = cacheManager.getCachePath(playlistId, video.id, video.url)
            cacheFile.parentFile?.mkdirs()
            
            // Write dummy content (simulating downloaded file)
            cacheFile.writeBytes(ByteArray(1024))
            
            // Mark as COMPLETED
            cacheStateManager.updateCacheStatus(
                screenId = "test-screen",
                playlistId = playlistId,
                videoId = video.id,
                status = CacheStatus.COMPLETED,
                localFilePath = cacheFile.absolutePath,
                actualFileSize = 1024L,
                url = video.url
            )
            
            Log.d(TAG, "Mock cached: ${video.id} | Type: ${MediaTypeDetector.detectMediaType(video.url, video.mimeType).name}")
        }
        
        // Verify all files are cached
        val allCached = testPlaylist.videos.all { video ->
            cacheManager.isCached(playlistId, video.id, video.url)
        }
        assertTrue("All media should be cached", allCached)
        
        // Create playback engine
        val playbackEngine = LocalPlaybackEngine(context, localCacheManager, playlistId)
        
        // Verify all cached
        val allVerified = playbackEngine.verifyAllCached(testPlaylist)
        assertTrue("All media should be verified as cached", allVerified)
        
        // Prepare playback items
        val playbackItems = playbackEngine.preparePlaybackItems(testPlaylist)
        assertNotNull("Playback items should be prepared", playbackItems)
        assertEquals("Should have 2 playback items", 2, playbackItems!!.size)
        
        // Verify playback order: Video → Image
        assertEquals("First item should be video", MediaType.VIDEO, playbackItems[0].mediaType)
        assertEquals("Second item should be image", MediaType.IMAGE, playbackItems[1].mediaType)
        
        // Verify all URIs are local (file://)
        playbackItems.forEach { item ->
            assertTrue("URI should be local file", item.localUri.startsWith("file://"))
            Log.d(TAG, "Playback item: ${item.video.id} | Type: ${item.mediaType.name} | URI: ${item.localUri}")
        }
        
        // Verify playback order matches playlist order
        val playbackOrder = playbackEngine.getPlaybackOrder(testPlaylist)
        assertEquals("Playback order should match playlist", 2, playbackOrder.size)
        assertEquals("First item index", 0, playbackOrder[0].first)
        assertEquals("First item type", MediaType.VIDEO, playbackOrder[0].second)
        assertEquals("Second item index", 1, playbackOrder[1].first)
        assertEquals("Second item type", MediaType.IMAGE, playbackOrder[1].second)
        
        Log.d(TAG, "✓ Video → Image playback order verified")
    }
    
    /**
     * Test: Image → Video playlist order
     * 
     * Verifies:
     * - All media downloaded before playback
     * - Playback from local storage only
     * - Order: Image → Video
     */
    @Test
    fun imageThenVideoPlaybackOrder() = runBlocking {
        Log.d(TAG, "=== Test: Image → Video playback order ===")
        
        // Create test playlist: Image → Video
        val testPlaylist = PlaylistWithVideos(
            playlist = Playlist(
                id = "test-playlist-2",
                name = "Test Playlist: Image → Video",
                videoIds = listOf("image1", "video1")
            ),
            videos = listOf(
                Video(
                    id = "image1",
                    url = "https://storage.supabase.co/object/public/images/test-image.jpg",
                    name = "Test Image",
                    mimeType = "image/jpeg",
                    type = "image"
                ),
                Video(
                    id = "video1",
                    url = "https://storage.supabase.co/object/public/videos/test-video.mp4",
                    name = "Test Video",
                    mimeType = "video/mp4",
                    type = "video"
                )
            )
        )
        
        val playlistId = testPlaylist.playlist.id
        
        // Mock: Pre-cache files
        testPlaylist.videos.forEach { video ->
            val cacheFile = cacheManager.getCachePath(playlistId, video.id, video.url)
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeBytes(ByteArray(1024))
            
            cacheStateManager.updateCacheStatus(
                screenId = "test-screen",
                playlistId = playlistId,
                videoId = video.id,
                status = CacheStatus.COMPLETED,
                localFilePath = cacheFile.absolutePath,
                actualFileSize = 1024L,
                url = video.url
            )
            
            Log.d(TAG, "Mock cached: ${video.id} | Type: ${MediaTypeDetector.detectMediaType(video.url, video.mimeType).name}")
        }
        
        // Verify all cached
        val allCached = testPlaylist.videos.all { video ->
            cacheManager.isCached(playlistId, video.id, video.url)
        }
        assertTrue("All media should be cached", allCached)
        
        // Create playback engine
        val playbackEngine = LocalPlaybackEngine(context, localCacheManager, playlistId)
        
        // Prepare playback items
        val playbackItems = playbackEngine.preparePlaybackItems(testPlaylist)
        assertNotNull("Playback items should be prepared", playbackItems)
        assertEquals("Should have 2 playback items", 2, playbackItems!!.size)
        
        // Verify playback order: Image → Video
        assertEquals("First item should be image", MediaType.IMAGE, playbackItems[0].mediaType)
        assertEquals("Second item should be video", MediaType.VIDEO, playbackItems[1].mediaType)
        
        // Verify all URIs are local
        playbackItems.forEach { item ->
            assertTrue("URI should be local file", item.localUri.startsWith("file://"))
            Log.d(TAG, "Playback item: ${item.video.id} | Type: ${item.mediaType.name} | URI: ${item.localUri}")
        }
        
        // Verify playback order matches playlist order
        val playbackOrder = playbackEngine.getPlaybackOrder(testPlaylist)
        assertEquals("Playback order should match playlist", 2, playbackOrder.size)
        assertEquals("First item index", 0, playbackOrder[0].first)
        assertEquals("First item type", MediaType.IMAGE, playbackOrder[0].second)
        assertEquals("Second item index", 1, playbackOrder[1].first)
        assertEquals("Second item type", MediaType.VIDEO, playbackOrder[1].second)
        
        Log.d(TAG, "✓ Image → Video playback order verified")
    }
    
    /**
     * Test: Download progress tracking
     * 
     * Verifies that download progress is tracked per item
     */
    @Test
    fun downloadProgressTracking() = runBlocking {
        Log.d(TAG, "=== Test: Download progress tracking ===")
        
        val testPlaylist = TestMediaRepository.getMixedMediaPlaylist()
        val playlistId = testPlaylist.playlist.id
        
        // Mock: Cache some files, leave others uncached
        val firstVideo = testPlaylist.videos.first()
        val cacheFile = cacheManager.getCachePath(playlistId, firstVideo.id, firstVideo.url)
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeBytes(ByteArray(1024))
        
        cacheStateManager.updateCacheStatus(
            screenId = "test-screen",
            playlistId = playlistId,
            videoId = firstVideo.id,
            status = CacheStatus.COMPLETED,
            localFilePath = cacheFile.absolutePath,
            actualFileSize = 1024L,
            url = firstVideo.url
        )
        
        // Check progress for cached item
        val cachedProgress = localCacheManager.getProgress(firstVideo.id)
        assertNotNull("Progress should exist for cached item", cachedProgress)
        
        // Verify cached item shows as complete
        val isCached = localCacheManager.isCached(playlistId, firstVideo.id, firstVideo.url)
        assertTrue("First video should be cached", isCached)
        
        // Verify local URI is available
        val localUri = localCacheManager.getLocalUri(playlistId, firstVideo.id, firstVideo.url)
        assertNotNull("Local URI should be available", localUri)
        assertTrue("Local URI should be file://", localUri!!.startsWith("file://"))
        
        Log.d(TAG, "✓ Download progress tracking verified")
    }
    
    /**
     * Test: Playback only starts when all files are cached
     * 
     * Verifies that playback items are null if not all files are cached
     */
    @Test
    fun playbackOnlyWhenAllCached() = runBlocking {
        Log.d(TAG, "=== Test: Playback only when all cached ===")
        
        val testPlaylist = TestMediaRepository.getMixedMediaPlaylist()
        val playlistId = testPlaylist.playlist.id
        
        // Cache only first item
        val firstVideo = testPlaylist.videos.first()
        val cacheFile = cacheManager.getCachePath(playlistId, firstVideo.id, firstVideo.url)
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeBytes(ByteArray(1024))
        
        cacheStateManager.updateCacheStatus(
            screenId = "test-screen",
            playlistId = playlistId,
            videoId = firstVideo.id,
            status = CacheStatus.COMPLETED,
            localFilePath = cacheFile.absolutePath,
            actualFileSize = 1024L,
            url = firstVideo.url
        )
        
        // Create playback engine
        val playbackEngine = LocalPlaybackEngine(context, localCacheManager, playlistId)
        
        // Verify not all cached
        val allCached = playbackEngine.verifyAllCached(testPlaylist)
        assertFalse("Not all items should be cached", allCached)
        
        // Prepare playback items - should return null (not all cached)
        val playbackItems = playbackEngine.preparePlaybackItems(testPlaylist)
        assertNull("Playback items should be null when not all cached", playbackItems)
        
        Log.d(TAG, "✓ Playback blocked when not all cached")
    }
    
    /**
     * Test: Playback order preservation
     * 
     * Verifies that playback order matches original playlist order exactly
     */
    @Test
    fun playbackOrderPreservation() = runBlocking {
        Log.d(TAG, "=== Test: Playback order preservation ===")
        
        val testPlaylist = TestMediaRepository.getMixedMediaPlaylist()
        val playlistId = testPlaylist.playlist.id
        
        // Mock: Cache all files
        testPlaylist.videos.forEach { video ->
            val cacheFile = cacheManager.getCachePath(playlistId, video.id, video.url)
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeBytes(ByteArray(1024))
            
            cacheStateManager.updateCacheStatus(
                screenId = "test-screen",
                playlistId = playlistId,
                videoId = video.id,
                status = CacheStatus.COMPLETED,
                localFilePath = cacheFile.absolutePath,
                actualFileSize = 1024L,
                url = video.url
            )
        }
        
        // Create playback engine
        val playbackEngine = LocalPlaybackEngine(context, localCacheManager, playlistId)
        
        // Prepare playback items
        val playbackItems = playbackEngine.preparePlaybackItems(testPlaylist)
        assertNotNull("Playback items should be prepared", playbackItems)
        
        // Verify order matches original playlist
        val originalOrder = testPlaylist.videos.map { it.id }
        val playbackOrder = playbackItems!!.map { it.video.id }
        
        assertEquals("Playback order should match playlist order", originalOrder, playbackOrder)
        
        // Log playback order
        Log.d(TAG, "Original order: ${originalOrder.joinToString(" → ")}")
        Log.d(TAG, "Playback order: ${playbackOrder.joinToString(" → ")}")
        
        Log.d(TAG, "✓ Playback order preserved")
    }
}

