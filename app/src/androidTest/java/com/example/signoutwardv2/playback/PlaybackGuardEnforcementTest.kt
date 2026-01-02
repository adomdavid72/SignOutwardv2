package com.example.signoutwardv2.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.signoutwardv2.cache.CacheManager
import com.example.signoutwardv2.cache.CacheStateManager
import com.example.signoutwardv2.cache.CacheStatus
import com.example.signoutwardv2.data.DevicePreferences
import com.example.signoutwardv2.data.PlaybackRepository
import com.example.signoutwardv2.data.test.TestMediaRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log
import java.io.File

/**
 * Instrumented tests for playback guard enforcement
 * 
 * Tests:
 * - Playback waits for file readiness (COMPLETED status)
 * - Cached files are preferred once fully downloaded
 * - Partial downloads are never used for playback
 * - Once download completes, next playback uses local file
 */
@RunWith(AndroidJUnit4::class)
class PlaybackGuardEnforcementTest {
    
    companion object {
        private const val TAG = "PlaybackGuardEnforcementTest"
    }
    
    private lateinit var context: Context
    private lateinit var preferences: DevicePreferences
    private lateinit var repository: PlaybackRepository
    private lateinit var cacheStateManager: CacheStateManager
    
    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        preferences = DevicePreferences(context)
        repository = PlaybackRepository(preferences, context)
        cacheStateManager = CacheStateManager(context)
        
        preferences.setScreenId("test-screen-guards")
        preferences.setPaired(true)
        
        // Clear cache before each test using repository
        repository.clearAllCache()
    }
    
    /**
     * Test: Playback waits for file readiness (COMPLETED status)
     * 
     * Validates that DOWNLOADING status prevents playback
     */
    @Test
    fun playbackWaitsForFileReadiness() {
        runBlocking {
            Log.d(TAG, "=== Starting file readiness guard test ===")
            
            val playlist = TestMediaRepository.getMixedMediaPlaylist()
            val testVideo = playlist.videos.first()
            val playlistId = playlist.playlist.id
            
            // Simulate DOWNLOADING status
            cacheStateManager.updateCacheStatus(
                screenId = "test-screen",
                playlistId = playlistId,
                videoId = testVideo.id,
                status = CacheStatus.DOWNLOADING,
                url = testVideo.url
            )
            
            // Try to get cached URI - should return null
            val cachedUri = repository.getCachedUri(playlistId, testVideo.id, testVideo.url)
            
            assertNull(
                "Should return null when status is DOWNLOADING (prevents partial playback)",
                cachedUri
            )
            
            // Verify isCached returns false
            val isCached = repository.isCached(playlistId, testVideo.id, testVideo.url)
            assertFalse(
                "Should return false when status is DOWNLOADING",
                isCached
            )
            
            Log.d(TAG, "✓ Playback correctly waits for COMPLETED status")
        }
    }
    
    /**
     * Test: Cached files are preferred once fully downloaded
     * 
     * Validates that once a file is COMPLETED, it returns local URI
     */
    @Test
    fun cachedFilesArePreferredOnceFullyDownloaded() {
        runBlocking {
            Log.d(TAG, "=== Starting cached file preference test ===")
            
            val playlist = TestMediaRepository.getMixedMediaPlaylist()
            val testVideo = playlist.videos.first()
            val playlistId = playlist.playlist.id
            
            // Create a test file using repository's cache manager
            val cacheManager = CacheManager(context, cacheStateManager)
            val cacheFile = cacheManager.getCachePath(playlistId, testVideo.id, testVideo.url)
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeBytes(ByteArray(1024)) // Write 1KB
            
            // Set status to COMPLETED
            cacheStateManager.updateCacheStatus(
                screenId = "test-screen",
                playlistId = playlistId,
                videoId = testVideo.id,
                status = CacheStatus.COMPLETED,
                localFilePath = cacheFile.absolutePath,
                actualFileSize = 1024L,
                url = testVideo.url
            )
            
            // Get cached URI - should return local file URI
            val cachedUri = repository.getCachedUri(playlistId, testVideo.id, testVideo.url)
            
            assertNotNull(
                "Should return local URI when COMPLETED",
                cachedUri
            )
            assertTrue(
                "Should return file:// URI",
                cachedUri!!.startsWith("file://")
            )
            assertTrue(
                "Should contain cache file path",
                cachedUri.contains(cacheFile.absolutePath)
            )
            
            // Verify isCached returns true
            val isCached = repository.isCached(playlistId, testVideo.id, testVideo.url)
            assertTrue(
                "Should return true when COMPLETED and file exists",
                isCached
            )
            
            Log.d(TAG, "✓ Cached files are preferred once fully downloaded")
        }
    }
    
    /**
     * Test: Partial downloads are never used for playback
     * 
     * Validates that DOWNLOADING status always returns null
     * even if file exists on disk
     */
    @Test
    fun partialDownloadsAreNeverUsedForPlayback() {
        runBlocking {
            Log.d(TAG, "=== Starting partial download prevention test ===")
            
            val playlist = TestMediaRepository.getMixedMediaPlaylist()
            val testVideo = playlist.videos.first()
            val playlistId = playlist.playlist.id
            
            // Create a partial file (simulating download in progress)
            val cacheManager = CacheManager(context, cacheStateManager)
            val cacheFile = cacheManager.getCachePath(playlistId, testVideo.id, testVideo.url)
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeBytes(ByteArray(512)) // Partial file (512 bytes)
            
            // Set status to DOWNLOADING (not COMPLETED)
            cacheStateManager.updateCacheStatus(
                screenId = "test-screen",
                playlistId = playlistId,
                videoId = testVideo.id,
                status = CacheStatus.DOWNLOADING,
                localFilePath = cacheFile.absolutePath,
                url = testVideo.url
            )
            
            // Try to get cached URI - should return null even though file exists
            val cachedUri = repository.getCachedUri(playlistId, testVideo.id, testVideo.url)
            
            assertNull(
                "Should return null when DOWNLOADING (prevents partial playback)",
                cachedUri
            )
            
            // Verify isCached returns false
            val isCached = repository.isCached(playlistId, testVideo.id, testVideo.url)
            assertFalse(
                "Should return false when DOWNLOADING",
                isCached
            )
            
            Log.d(TAG, "✓ Partial downloads are never used for playback")
        }
    }
    
    /**
     * Test: Once download completes, next playback uses local file
     * 
     * Validates the transition from DOWNLOADING → COMPLETED
     * and that local file is used after completion
     */
    @Test
    fun nextPlaybackUsesLocalFileAfterCompletion() {
        runBlocking {
            Log.d(TAG, "=== Starting local file transition test ===")
            
            val playlist = TestMediaRepository.getMixedMediaPlaylist()
            val testVideo = playlist.videos.first()
            val playlistId = playlist.playlist.id
            
            // Create a test file using repository's cache manager
            val cacheManager = CacheManager(context, cacheStateManager)
            val cacheFile = cacheManager.getCachePath(playlistId, testVideo.id, testVideo.url)
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeBytes(ByteArray(2048)) // Write 2KB
            
            // Step 1: Set status to DOWNLOADING
            cacheStateManager.updateCacheStatus(
                screenId = "test-screen",
                playlistId = playlistId,
                videoId = testVideo.id,
                status = CacheStatus.DOWNLOADING,
                localFilePath = cacheFile.absolutePath,
                url = testVideo.url
            )
            
            // Should return null during download
            val uriDuringDownload = repository.getCachedUri(playlistId, testVideo.id, testVideo.url)
            assertNull("Should return null during DOWNLOADING", uriDuringDownload)
            
            // Step 2: Transition to COMPLETED
            cacheStateManager.updateCacheStatus(
                screenId = "test-screen",
                playlistId = playlistId,
                videoId = testVideo.id,
                status = CacheStatus.COMPLETED,
                localFilePath = cacheFile.absolutePath,
                actualFileSize = 2048L,
                url = testVideo.url
            )
            
            // Should now return local URI
            val uriAfterCompletion = repository.getCachedUri(playlistId, testVideo.id, testVideo.url)
            
            assertNotNull(
                "Should return local URI after COMPLETED",
                uriAfterCompletion
            )
            assertTrue(
                "Should return file:// URI",
                uriAfterCompletion!!.startsWith("file://")
            )
            
            Log.d(TAG, "✓ Next playback uses local file after completion")
        }
    }
    
    /**
     * Test: Empty files are never used for playback
     * 
     * Validates that even with COMPLETED status,
     * empty files (size = 0) are rejected
     */
    @Test
    fun emptyFilesAreNeverUsedForPlayback() {
        runBlocking {
            Log.d(TAG, "=== Starting empty file prevention test ===")
            
            val playlist = TestMediaRepository.getMixedMediaPlaylist()
            val testVideo = playlist.videos.first()
            val playlistId = playlist.playlist.id
            
            // Create an empty file
            val cacheManager = CacheManager(context, cacheStateManager)
            val cacheFile = cacheManager.getCachePath(playlistId, testVideo.id, testVideo.url)
            cacheFile.parentFile?.mkdirs()
            cacheFile.createNewFile() // Empty file
            
            // Set status to COMPLETED (but file is empty)
            cacheStateManager.updateCacheStatus(
                screenId = "test-screen",
                playlistId = playlistId,
                videoId = testVideo.id,
                status = CacheStatus.COMPLETED,
                localFilePath = cacheFile.absolutePath,
                actualFileSize = 0L,
                url = testVideo.url
            )
            
            // Should return null because file is empty
            val cachedUri = repository.getCachedUri(playlistId, testVideo.id, testVideo.url)
            
            assertNull(
                "Should return null when file is empty (even if COMPLETED)",
                cachedUri
            )
            
            // Verify isCached returns false
            val isCached = repository.isCached(playlistId, testVideo.id, testVideo.url)
            assertFalse(
                "Should return false when file is empty",
                isCached
            )
            
            Log.d(TAG, "✓ Empty files are never used for playback")
        }
    }
    
    /**
     * Test: File size mismatch prevents playback
     * 
     * Validates that if file size doesn't match expected,
     * playback is prevented even with COMPLETED status
     */
    @Test
    fun fileSizeMismatchPreventsPlayback() {
        runBlocking {
            Log.d(TAG, "=== Starting file size mismatch test ===")
            
            val playlist = TestMediaRepository.getMixedMediaPlaylist()
            val testVideo = playlist.videos.first()
            val playlistId = playlist.playlist.id
            
            // Create a file with wrong size
            val cacheManager = CacheManager(context, cacheStateManager)
            val cacheFile = cacheManager.getCachePath(playlistId, testVideo.id, testVideo.url)
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeBytes(ByteArray(1024)) // Write 1KB
            
            // Set status to COMPLETED with expected size 2KB (but file is 1KB)
            cacheStateManager.updateCacheStatus(
                screenId = "test-screen",
                playlistId = playlistId,
                videoId = testVideo.id,
                status = CacheStatus.COMPLETED,
                localFilePath = cacheFile.absolutePath,
                expectedFileSize = 2048L, // Expected 2KB
                actualFileSize = 1024L,   // Actual 1KB
                url = testVideo.url
            )
            
            // Should return null because size doesn't match
            val cachedUri = repository.getCachedUri(playlistId, testVideo.id, testVideo.url)
            
            assertNull(
                "Should return null when file size doesn't match expected",
                cachedUri
            )
            
            Log.d(TAG, "✓ File size mismatch prevents playback")
        }
    }
}

