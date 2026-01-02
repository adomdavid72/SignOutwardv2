package com.example.signoutwardv2.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.signoutwardv2.data.DevicePreferences
import com.example.signoutwardv2.data.MediaTypeDetector
import com.example.signoutwardv2.data.PlaybackRepository
import com.example.signoutwardv2.data.PlaylistProcessor
import com.example.signoutwardv2.data.test.TestMediaRepository
import com.example.signoutwardv2.data.models.PlaylistWithVideos
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log

/**
 * Deterministic instrumented tests for mixed media playback
 * Uses TestMediaRepository for mocked playlist data
 * No Supabase writes - read-only tests
 * 
 * Tests:
 * - Mixed playlists (images + videos) process correctly
 * - Playback order is preserved
 * - Images are not skipped
 * - Cached content is preferred
 */
@RunWith(AndroidJUnit4::class)
class MixedMediaPlaybackDeterministicTest {
    
    companion object {
        private const val TAG = "MixedMediaPlaybackDeterministicTest"
    }
    
    private lateinit var context: Context
    private lateinit var preferences: DevicePreferences
    private lateinit var repository: PlaybackRepository
    
    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        preferences = DevicePreferences(context)
        repository = PlaybackRepository(preferences, context)
        
        preferences.setScreenId("test-screen-deterministic")
        preferences.setPaired(true)
    }
    
    /**
     * Test: Mixed playlist processes both images and videos correctly
     * 
     * Uses deterministic test data from TestMediaRepository
     * Validates that both media types are classified and included
     */
    @Test
    fun mixedPlaylistProcessesBothImagesAndVideos() {
        runBlocking {
            Log.d(TAG, "=== Starting deterministic mixed media test ===")
            
            val playlist = TestMediaRepository.getMixedMediaPlaylist()
            val processed = PlaylistProcessor.processPlaylist(playlist)
            
            Log.d(TAG, "Total items: ${playlist.videos.size}")
            Log.d(TAG, "Supported: ${processed.supportedVideos.size}")
            Log.d(TAG, "Unsupported: ${processed.unsupportedVideos.size}")
            
            // Assert: All items should be supported (mixed playlist has only supported formats)
            assertEquals(
                "All items in mixed playlist should be supported",
                playlist.videos.size,
                processed.supportedVideos.size
            )
            assertEquals(
                "No unsupported items in mixed playlist",
                0,
                processed.unsupportedVideos.size
            )
            assertTrue("Playlist should have supported files", processed.hasSupportedFiles)
            
            // Count videos and images
            val videos = processed.supportedVideos.filter { video ->
                MediaTypeDetector.detectMediaType(video.url, video.mimeType) == com.example.signoutwardv2.data.MediaType.VIDEO
            }
            val images = processed.supportedVideos.filter { video ->
                MediaTypeDetector.detectMediaType(video.url, video.mimeType) == com.example.signoutwardv2.data.MediaType.IMAGE
            }
            
            assertEquals("Should have 2 videos", 2, videos.size)
            assertEquals("Should have 2 images", 2, images.size)
            
            Log.d(TAG, "✓ Mixed playlist validated: ${videos.size} videos, ${images.size} images")
        }
    }
    
    /**
     * Test: Playback order is preserved for mixed playlists
     * 
     * Validates that the order of media items is maintained
     * after processing (video1 → image1 → video2 → image2)
     */
    @Test
    fun playbackOrderIsPreservedForMixedPlaylists() {
        runBlocking {
            Log.d(TAG, "=== Starting playback order preservation test ===")
            
            val playlist = TestMediaRepository.getMixedMediaPlaylist()
            val processed = PlaylistProcessor.processPlaylist(playlist)
            
            // Verify order is preserved
            val originalOrder = playlist.videos.map { it.id }
            val processedOrder = processed.supportedVideos.map { it.id }
            
            assertEquals(
                "Processed order should match original order",
                originalOrder,
                processedOrder
            )
            
            // Verify media type sequence
            val mediaSequence = processed.supportedVideos.map { video ->
                MediaTypeDetector.detectMediaType(video.url, video.mimeType)
            }
            
            assertEquals(com.example.signoutwardv2.data.MediaType.VIDEO, mediaSequence[0])
            assertEquals(com.example.signoutwardv2.data.MediaType.IMAGE, mediaSequence[1])
            assertEquals(com.example.signoutwardv2.data.MediaType.VIDEO, mediaSequence[2])
            assertEquals(com.example.signoutwardv2.data.MediaType.IMAGE, mediaSequence[3])
            
            Log.d(TAG, "✓ Playback order preserved: VIDEO → IMAGE → VIDEO → IMAGE")
        }
    }
    
    /**
     * Test: Images are not skipped in mixed playlists
     * 
     * Validates that all images in the original playlist
     * appear in the processed supported list
     */
    @Test
    fun imagesAreNotSkippedInMixedPlaylists() {
        runBlocking {
            Log.d(TAG, "=== Starting image skip prevention test ===")
            
            val playlist = TestMediaRepository.getMixedMediaPlaylist()
            
            // Find all images in original playlist
            val originalImages = playlist.videos.filter { video ->
                MediaTypeDetector.detectMediaType(video.url, video.mimeType) == com.example.signoutwardv2.data.MediaType.IMAGE
            }
            
            Log.d(TAG, "Original images: ${originalImages.size}")
            
            // Process playlist
            val processed = PlaylistProcessor.processPlaylist(playlist)
            
            // Find images in processed playlist
            val processedImages = processed.supportedVideos.filter { video ->
                MediaTypeDetector.detectMediaType(video.url, video.mimeType) == com.example.signoutwardv2.data.MediaType.IMAGE
            }
            
            Log.d(TAG, "Processed images: ${processedImages.size}")
            
            // Assert: All original images should be in processed list
            val originalImageIds = originalImages.map { it.id }.toSet()
            val processedImageIds = processedImages.map { it.id }.toSet()
            
            assertEquals(
                "All images should be included in processed playlist",
                originalImageIds,
                processedImageIds
            )
            
            // Assert: Images should not be in unsupported list
            val unsupportedImageIds = processed.unsupportedVideos
                .filter { video ->
                    MediaTypeDetector.detectMediaType(video.url, video.mimeType) == com.example.signoutwardv2.data.MediaType.IMAGE
                }
                .map { it.id }
                .toSet()
            
            assertTrue(
                "Images should not be in unsupported list",
                unsupportedImageIds.isEmpty()
            )
            
            Log.d(TAG, "✓ All images correctly included and not skipped")
        }
    }
    
    /**
     * Test: Unsupported files are filtered without breaking playlist
     * 
     * Validates that unsupported files are excluded but
     * supported files remain in correct order
     */
    @Test
    fun unsupportedFilesAreFilteredWithoutBreakingPlaylist() {
        runBlocking {
            Log.d(TAG, "=== Starting unsupported file filtering test ===")
            
            val playlist = TestMediaRepository.getMixedWithUnsupportedPlaylist()
            val processed = PlaylistProcessor.processPlaylist(playlist)
            
            // Should have 3 supported (video1, image1, video2) and 2 unsupported
            assertEquals(3, processed.supportedVideos.size)
            assertEquals(2, processed.unsupportedVideos.size)
            assertTrue(processed.hasSupportedFiles)
            
            // Verify supported items are in correct order (unsupported filtered out)
            val supportedIds = processed.supportedVideos.map { it.id }
            assertEquals(listOf("video1", "image1", "video2"), supportedIds)
            
            // Verify unsupported items
            val unsupportedIds = processed.unsupportedVideos.map { it.id }.toSet()
            assertTrue(unsupportedIds.contains("unsupported1"))
            assertTrue(unsupportedIds.contains("unsupported2"))
            
            // Verify no overlap
            assertTrue(supportedIds.toSet().intersect(unsupportedIds).isEmpty())
            
            Log.d(TAG, "✓ Unsupported files filtered correctly without breaking playlist")
        }
    }
    
    /**
     * Test: Empty URLs are filtered to unsupported
     * 
     * Validates that videos with empty/null URLs are
     * correctly identified as unsupported
     */
    @Test
    fun emptyUrlsAreFilteredToUnsupported() {
        runBlocking {
            Log.d(TAG, "=== Starting empty URL filtering test ===")
            
            val playlist = TestMediaRepository.getPlaylistWithEmptyUrls()
            val processed = PlaylistProcessor.processPlaylist(playlist)
            
            // Should have 2 supported (valid1, valid2) and 2 unsupported (empty1, null1)
            assertEquals(2, processed.supportedVideos.size)
            assertEquals(2, processed.unsupportedVideos.size)
            assertTrue(processed.hasSupportedFiles)
            
            // Verify supported items
            val supportedIds = processed.supportedVideos.map { it.id }.toSet()
            assertTrue(supportedIds.contains("valid1"))
            assertTrue(supportedIds.contains("valid2"))
            
            // Verify empty URLs are unsupported
            val unsupportedIds = processed.unsupportedVideos.map { it.id }.toSet()
            assertTrue(unsupportedIds.contains("empty1"))
            assertTrue(unsupportedIds.contains("null1"))
            
            Log.d(TAG, "✓ Empty URLs correctly filtered to unsupported")
        }
    }
}

