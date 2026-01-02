package com.example.signoutwardv2.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for MediaTypeDetector
 * Tests file type validation logic
 */
class MediaTypeDetectorTest {
    
    @Test
    fun `detectMediaType returns IMAGE for supported image extensions`() {
        val imageUrls = listOf(
            "https://example.com/image.jpg",
            "https://example.com/image.jpeg",
            "https://example.com/image.png",
            "https://example.com/image.webp",
            "https://example.com/image.gif",
            "https://example.com/image.bmp",
            "https://example.com/image.tiff",
            "https://example.com/image.tif"
        )
        
        imageUrls.forEach { url ->
            val result = MediaTypeDetector.detectMediaType(url)
            assertEquals("URL $url should be detected as IMAGE", MediaType.IMAGE, result)
        }
    }
    
    @Test
    fun `detectMediaType returns VIDEO for supported video extensions`() {
        val videoUrls = listOf(
            "https://example.com/video.mp4",
            "https://example.com/video.mov",
            "https://example.com/video.avi",
            "https://example.com/video.mkv",
            "https://example.com/video.webm",
            "https://example.com/video.flv",
            "https://example.com/video.wmv"
        )
        
        videoUrls.forEach { url ->
            val result = MediaTypeDetector.detectMediaType(url)
            assertEquals("URL $url should be detected as VIDEO", MediaType.VIDEO, result)
        }
    }
    
    @Test
    fun `detectMediaType returns UNSUPPORTED for unknown extensions`() {
        val unsupportedUrls = listOf(
            "https://example.com/file.txt",
            "https://example.com/file.pdf",
            "https://example.com/file.doc",
            "https://example.com/file",
            "https://example.com/file.xyz"
        )
        
        unsupportedUrls.forEach { url ->
            val result = MediaTypeDetector.detectMediaType(url)
            assertEquals("URL $url should be detected as UNSUPPORTED", MediaType.UNSUPPORTED, result)
        }
    }
    
    @Test
    fun `detectMediaType uses MIME type when provided`() {
        // Test image MIME types
        assertEquals(MediaType.IMAGE, MediaTypeDetector.detectMediaType("file.xyz", "image/jpeg"))
        assertEquals(MediaType.IMAGE, MediaTypeDetector.detectMediaType("file.xyz", "image/png"))
        assertEquals(MediaType.IMAGE, MediaTypeDetector.detectMediaType("file.xyz", "image/webp"))
        
        // Test video MIME types
        assertEquals(MediaType.VIDEO, MediaTypeDetector.detectMediaType("file.xyz", "video/mp4"))
        assertEquals(MediaType.VIDEO, MediaTypeDetector.detectMediaType("file.xyz", "video/quicktime"))
        assertEquals(MediaType.VIDEO, MediaTypeDetector.detectMediaType("file.xyz", "video/webm"))
    }
    
    @Test
    fun `detectMediaType is case insensitive`() {
        assertEquals(MediaType.IMAGE, MediaTypeDetector.detectMediaType("file.JPG"))
        assertEquals(MediaType.IMAGE, MediaTypeDetector.detectMediaType("file.PNG"))
        assertEquals(MediaType.VIDEO, MediaTypeDetector.detectMediaType("file.MP4"))
        assertEquals(MediaType.VIDEO, MediaTypeDetector.detectMediaType("file.MOV"))
    }
    
    @Test
    fun `isSupported returns true for supported types`() {
        assertTrue(MediaTypeDetector.isSupported("file.jpg"))
        assertTrue(MediaTypeDetector.isSupported("file.mp4"))
        assertTrue(MediaTypeDetector.isSupported("file.png", "image/png"))
    }
    
    @Test
    fun `isSupported returns false for unsupported types`() {
        assertFalse(MediaTypeDetector.isSupported("file.txt"))
        assertFalse(MediaTypeDetector.isSupported("file.pdf"))
        assertFalse(MediaTypeDetector.isSupported("file.xyz"))
    }
    
    @Test
    fun `getExtension extracts extension correctly`() {
        assertEquals("jpg", MediaTypeDetector.getExtension("file.jpg"))
        assertEquals("mp4", MediaTypeDetector.getExtension("file.mp4"))
        assertEquals("", MediaTypeDetector.getExtension("file"))
        assertEquals("png", MediaTypeDetector.getExtension("path/to/file.png"))
    }
}

