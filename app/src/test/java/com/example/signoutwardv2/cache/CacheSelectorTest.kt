package com.example.signoutwardv2.cache

import android.content.Context
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations.openMocks
import java.io.File

/**
 * Unit tests for CacheManager cache selection logic
 * Tests: "If file exists locally AND download completed → return local path"
 */
class CacheSelectorTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockStateManager: CacheStateManager
    
    private lateinit var cacheManager: CacheManager
    private lateinit var tempDir: File
    
    @Before
    fun setup() {
        openMocks(this)
        
        // Create temp directory for testing
        tempDir = File(System.getProperty("java.io.tmpdir"), "cache_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        
        val mockFilesDir = File(tempDir, "files")
        mockFilesDir.mkdirs()
        
        whenever(mockContext.filesDir).thenReturn(mockFilesDir)
        
        cacheManager = CacheManager(mockContext, mockStateManager)
    }
    
    @Test
    fun `getCachedUri returns null when cache status is NOT_STARTED`() {
        val metadata = CacheMetadata(
            screenId = "screen1",
            playlistId = "playlist1",
            videoId = "video1",
            localFilePath = "/path/to/file.mp4",
            cacheStatus = CacheStatus.NOT_STARTED.name,
            url = "https://example.com/video.mp4"
        )
        
        whenever(mockStateManager.getCacheMetadata("playlist1", "video1")).thenReturn(metadata)
        
        val result = cacheManager.getCachedUri("playlist1", "video1", "https://example.com/video.mp4")
        
        assertNull("Should return null when cache status is NOT_STARTED", result)
    }
    
    @Test
    fun `getCachedUri returns null when cache status is DOWNLOADING`() {
        val metadata = CacheMetadata(
            screenId = "screen1",
            playlistId = "playlist1",
            videoId = "video1",
            localFilePath = "/path/to/file.mp4",
            cacheStatus = CacheStatus.DOWNLOADING.name,
            url = "https://example.com/video.mp4"
        )
        
        whenever(mockStateManager.getCacheMetadata("playlist1", "video1")).thenReturn(metadata)
        
        val result = cacheManager.getCachedUri("playlist1", "video1", "https://example.com/video.mp4")
        
        assertNull("Should return null when cache status is DOWNLOADING (partial playback prevention)", result)
    }
    
    @Test
    fun `getCachedUri returns null when cache status is FAILED`() {
        val metadata = CacheMetadata(
            screenId = "screen1",
            playlistId = "playlist1",
            videoId = "video1",
            localFilePath = "/path/to/file.mp4",
            cacheStatus = CacheStatus.FAILED.name,
            url = "https://example.com/video.mp4"
        )
        
        whenever(mockStateManager.getCacheMetadata("playlist1", "video1")).thenReturn(metadata)
        
        val result = cacheManager.getCachedUri("playlist1", "video1", "https://example.com/video.mp4")
        
        assertNull("Should return null when cache status is FAILED", result)
    }
    
    @Test
    fun `getCachedUri returns null when file does not exist`() {
        val metadata = CacheMetadata(
            screenId = "screen1",
            playlistId = "playlist1",
            videoId = "video1",
            localFilePath = "/nonexistent/path/file.mp4",
            cacheStatus = CacheStatus.COMPLETED.name,
            url = "https://example.com/video.mp4"
        )
        
        whenever(mockStateManager.getCacheMetadata("playlist1", "video1")).thenReturn(metadata)
        
        val result = cacheManager.getCachedUri("playlist1", "video1", "https://example.com/video.mp4")
        
        assertNull("Should return null when file does not exist", result)
    }
    
    @Test
    fun `getCachedUri returns null when file is empty`() {
        val testFile = File(tempDir, "empty.mp4")
        testFile.createNewFile()
        
        val metadata = CacheMetadata(
            screenId = "screen1",
            playlistId = "playlist1",
            videoId = "video1",
            localFilePath = testFile.absolutePath,
            cacheStatus = CacheStatus.COMPLETED.name,
            url = "https://example.com/video.mp4"
        )
        
        whenever(mockStateManager.getCacheMetadata("playlist1", "video1")).thenReturn(metadata)
        
        val result = cacheManager.getCachedUri("playlist1", "video1", "https://example.com/video.mp4")
        
        assertNull("Should return null when file is empty", result)
    }
    
    @Test
    fun `getCachedUri returns file URI when file exists and is completed`() {
        val testFile = File(tempDir, "video.mp4")
        testFile.writeBytes(ByteArray(1024)) // Write 1KB of data
        
        val metadata = CacheMetadata(
            screenId = "screen1",
            playlistId = "playlist1",
            videoId = "video1",
            localFilePath = testFile.absolutePath,
            cacheStatus = CacheStatus.COMPLETED.name,
            actualFileSize = 1024L,
            url = "https://example.com/video.mp4"
        )
        
        whenever(mockStateManager.getCacheMetadata("playlist1", "video1")).thenReturn(metadata)
        
        val result = cacheManager.getCachedUri("playlist1", "video1", "https://example.com/video.mp4")
        
        assertNotNull("Should return file URI when file exists and is completed", result)
        assertTrue("Should return file:// URI", result!!.startsWith("file://"))
        assertTrue("Should contain file path", result.contains(testFile.absolutePath))
    }
    
    @Test
    fun `getCachedUri returns null when file size mismatch`() {
        val testFile = File(tempDir, "video.mp4")
        testFile.writeBytes(ByteArray(1024)) // Write 1KB
        
        val metadata = CacheMetadata(
            screenId = "screen1",
            playlistId = "playlist1",
            videoId = "video1",
            localFilePath = testFile.absolutePath,
            cacheStatus = CacheStatus.COMPLETED.name,
            expectedFileSize = 2048L, // Expected 2KB, but file is 1KB
            actualFileSize = 1024L,
            url = "https://example.com/video.mp4"
        )
        
        whenever(mockStateManager.getCacheMetadata("playlist1", "video1")).thenReturn(metadata)
        
        val result = cacheManager.getCachedUri("playlist1", "video1", "https://example.com/video.mp4")
        
        assertNull("Should return null when file size doesn't match expected", result)
    }
    
    @Test
    fun `getCachedUri returns null when file extension mismatch`() {
        val testFile = File(tempDir, "video.mp4")
        testFile.writeBytes(ByteArray(1024))
        
        val metadata = CacheMetadata(
            screenId = "screen1",
            playlistId = "playlist1",
            videoId = "video1",
            localFilePath = testFile.absolutePath,
            cacheStatus = CacheStatus.COMPLETED.name,
            actualFileSize = 1024L,
            url = "https://example.com/video.mov" // URL says .mov, but file is .mp4
        )
        
        whenever(mockStateManager.getCacheMetadata("playlist1", "video1")).thenReturn(metadata)
        
        val result = cacheManager.getCachedUri("playlist1", "video1", "https://example.com/video.mov")
        
        assertNull("Should return null when file extension doesn't match URL", result)
    }
    
    @Test
    fun `isCached returns false when status is DOWNLOADING`() {
        val metadata = CacheMetadata(
            screenId = "screen1",
            playlistId = "playlist1",
            videoId = "video1",
            localFilePath = "/path/to/file.mp4",
            cacheStatus = CacheStatus.DOWNLOADING.name,
            url = "https://example.com/video.mp4"
        )
        
        whenever(mockStateManager.getCacheMetadata("playlist1", "video1")).thenReturn(metadata)
        
        val result = cacheManager.isCached("playlist1", "video1", "https://example.com/video.mp4")
        
        assertFalse("Should return false when status is DOWNLOADING (partial playback prevention)", result)
    }
    
    @Test
    fun `isCached returns true only when COMPLETED and file is valid`() {
        val testFile = File(tempDir, "video.mp4")
        testFile.writeBytes(ByteArray(1024))
        
        val metadata = CacheMetadata(
            screenId = "screen1",
            playlistId = "playlist1",
            videoId = "video1",
            localFilePath = testFile.absolutePath,
            cacheStatus = CacheStatus.COMPLETED.name,
            actualFileSize = 1024L,
            url = "https://example.com/video.mp4"
        )
        
        whenever(mockStateManager.getCacheMetadata("playlist1", "video1")).thenReturn(metadata)
        
        val result = cacheManager.isCached("playlist1", "video1", "https://example.com/video.mp4")
        
        assertTrue("Should return true when COMPLETED and file is valid", result)
    }
    
    @Test
    fun `getCachedUri returns null when localFilePath is null`() {
        val metadata = CacheMetadata(
            screenId = "screen1",
            playlistId = "playlist1",
            videoId = "video1",
            localFilePath = "", // Empty path
            cacheStatus = CacheStatus.COMPLETED.name,
            url = "https://example.com/video.mp4"
        )
        
        whenever(mockStateManager.getCacheMetadata("playlist1", "video1")).thenReturn(metadata)
        
        val result = cacheManager.getCachedUri("playlist1", "video1", "https://example.com/video.mp4")
        
        assertNull("Should return null when localFilePath is empty", result)
    }
    
    @Test
    fun `getCachedUri returns null when metadata is null`() {
        whenever(mockStateManager.getCacheMetadata("playlist1", "video1")).thenReturn(null)
        
        val result = cacheManager.getCachedUri("playlist1", "video1", "https://example.com/video.mp4")
        
        assertNull("Should return null when metadata is null", result)
    }
    
    @Test
    fun `getCachedUri returns file URI for image files when COMPLETED`() {
        val testFile = File(tempDir, "image.jpg")
        testFile.writeBytes(ByteArray(512)) // Write 512 bytes
        
        val metadata = CacheMetadata(
            screenId = "screen1",
            playlistId = "playlist1",
            videoId = "image1",
            localFilePath = testFile.absolutePath,
            cacheStatus = CacheStatus.COMPLETED.name,
            actualFileSize = 512L,
            url = "https://example.com/image.jpg"
        )
        
        whenever(mockStateManager.getCacheMetadata("playlist1", "image1")).thenReturn(metadata)
        
        val result = cacheManager.getCachedUri("playlist1", "image1", "https://example.com/image.jpg")
        
        assertNotNull("Should return file URI for completed image", result)
        assertTrue("Should return file:// URI", result!!.startsWith("file://"))
        assertTrue("Should contain image file path", result.contains("image.jpg"))
    }
    
    @Test
    fun `getCachedUri returns file URI for webm video when COMPLETED`() {
        val testFile = File(tempDir, "video.webm")
        testFile.writeBytes(ByteArray(2048))
        
        val metadata = CacheMetadata(
            screenId = "screen1",
            playlistId = "playlist1",
            videoId = "video1",
            localFilePath = testFile.absolutePath,
            cacheStatus = CacheStatus.COMPLETED.name,
            actualFileSize = 2048L,
            url = "https://example.com/video.webm"
        )
        
        whenever(mockStateManager.getCacheMetadata("playlist1", "video1")).thenReturn(metadata)
        
        val result = cacheManager.getCachedUri("playlist1", "video1", "https://example.com/video.webm")
        
        assertNotNull("Should return file URI for completed webm video", result)
        assertTrue("Should return file:// URI", result!!.startsWith("file://"))
    }
    
    @Test
    fun `getCachedUri returns null when file size is zero`() {
        val testFile = File(tempDir, "zero.mp4")
        testFile.createNewFile() // Create empty file
        
        val metadata = CacheMetadata(
            screenId = "screen1",
            playlistId = "playlist1",
            videoId = "video1",
            localFilePath = testFile.absolutePath,
            cacheStatus = CacheStatus.COMPLETED.name,
            actualFileSize = 0L,
            url = "https://example.com/video.mp4"
        )
        
        whenever(mockStateManager.getCacheMetadata("playlist1", "video1")).thenReturn(metadata)
        
        val result = cacheManager.getCachedUri("playlist1", "video1", "https://example.com/video.mp4")
        
        assertNull("Should return null when file size is zero", result)
    }
    
    @Test
    fun `getCachedUri validates extension case-insensitively`() {
        val testFile = File(tempDir, "video.MP4") // Uppercase extension
        testFile.writeBytes(ByteArray(1024))
        
        val metadata = CacheMetadata(
            screenId = "screen1",
            playlistId = "playlist1",
            videoId = "video1",
            localFilePath = testFile.absolutePath,
            cacheStatus = CacheStatus.COMPLETED.name,
            actualFileSize = 1024L,
            url = "https://example.com/video.mp4" // Lowercase in URL
        )
        
        whenever(mockStateManager.getCacheMetadata("playlist1", "video1")).thenReturn(metadata)
        
        val result = cacheManager.getCachedUri("playlist1", "video1", "https://example.com/video.mp4")
        
        // Extension matching should be case-insensitive
        assertNotNull("Should return URI when extensions match case-insensitively", result)
    }
    
    @Test
    fun `isCached returns false when file does not exist`() {
        val metadata = CacheMetadata(
            screenId = "screen1",
            playlistId = "playlist1",
            videoId = "video1",
            localFilePath = "/nonexistent/path/file.mp4",
            cacheStatus = CacheStatus.COMPLETED.name,
            url = "https://example.com/video.mp4"
        )
        
        whenever(mockStateManager.getCacheMetadata("playlist1", "video1")).thenReturn(metadata)
        
        val result = cacheManager.isCached("playlist1", "video1", "https://example.com/video.mp4")
        
        assertFalse("Should return false when file does not exist", result)
    }
    
    @Test
    fun `isCached returns false when file is empty`() {
        val testFile = File(tempDir, "empty.mp4")
        testFile.createNewFile()
        
        val metadata = CacheMetadata(
            screenId = "screen1",
            playlistId = "playlist1",
            videoId = "video1",
            localFilePath = testFile.absolutePath,
            cacheStatus = CacheStatus.COMPLETED.name,
            actualFileSize = 0L,
            url = "https://example.com/video.mp4"
        )
        
        whenever(mockStateManager.getCacheMetadata("playlist1", "video1")).thenReturn(metadata)
        
        val result = cacheManager.isCached("playlist1", "video1", "https://example.com/video.mp4")
        
        assertFalse("Should return false when file is empty", result)
    }
    
    @Test
    fun `isCached returns false when metadata is null`() {
        whenever(mockStateManager.getCacheMetadata("playlist1", "video1")).thenReturn(null)
        
        val result = cacheManager.isCached("playlist1", "video1", "https://example.com/video.mp4")
        
        assertFalse("Should return false when metadata is null", result)
    }
    
    @Test
    fun `isCached returns false when localFilePath is empty`() {
        val metadata = CacheMetadata(
            screenId = "screen1",
            playlistId = "playlist1",
            videoId = "video1",
            localFilePath = "", // Empty path
            cacheStatus = CacheStatus.COMPLETED.name,
            url = "https://example.com/video.mp4"
        )
        
        whenever(mockStateManager.getCacheMetadata("playlist1", "video1")).thenReturn(metadata)
        
        val result = cacheManager.isCached("playlist1", "video1", "https://example.com/video.mp4")
        
        assertFalse("Should return false when localFilePath is empty", result)
    }
}

