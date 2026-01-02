package com.example.signoutwardv2.data

import com.example.signoutwardv2.data.MediaTypeDetector
import com.example.signoutwardv2.data.MediaType
import com.example.signoutwardv2.data.models.Playlist
import com.example.signoutwardv2.data.models.PlaylistWithVideos
import com.example.signoutwardv2.data.models.Video
import com.example.signoutwardv2.data.test.TestMediaRepository
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for PlaylistProcessor
 * Tests playlist filtering and unsupported file handling
 */
class PlaylistProcessorTest {
    
    @Test
    fun `processPlaylist separates supported and unsupported files`() {
        val videos = listOf(
            Video(id = "1", url = "https://example.com/video.mp4", name = "Video 1"),
            Video(id = "2", url = "https://example.com/image.jpg", name = "Image 1"),
            Video(id = "3", url = "https://example.com/file.txt", name = "Unsupported 1"),
            Video(id = "4", url = "https://example.com/video2.mov", name = "Video 2"),
            Video(id = "5", url = "https://example.com/file.pdf", name = "Unsupported 2")
        )
        
        val playlist = PlaylistWithVideos(
            playlist = Playlist(id = "playlist1", name = "Test Playlist", videoIds = videos.map { it.id }),
            videos = videos
        )
        
        val result = PlaylistProcessor.processPlaylist(playlist)
        
        assertEquals(3, result.supportedVideos.size)
        assertEquals(2, result.unsupportedVideos.size)
        assertTrue(result.hasSupportedFiles)
        
        // Check supported videos
        assertTrue(result.supportedVideos.any { it.id == "1" })
        assertTrue(result.supportedVideos.any { it.id == "2" })
        assertTrue(result.supportedVideos.any { it.id == "4" })
        
        // Check unsupported videos
        assertTrue(result.unsupportedVideos.any { it.id == "3" })
        assertTrue(result.unsupportedVideos.any { it.id == "5" })
    }
    
    @Test
    fun `processPlaylist returns empty supported list when all files are unsupported`() {
        val videos = listOf(
            Video(id = "1", url = "https://example.com/file.txt", name = "Unsupported 1"),
            Video(id = "2", url = "https://example.com/file.pdf", name = "Unsupported 2")
        )
        
        val playlist = PlaylistWithVideos(
            playlist = Playlist(id = "playlist1", name = "Test Playlist", videoIds = videos.map { it.id }),
            videos = videos
        )
        
        val result = PlaylistProcessor.processPlaylist(playlist)
        
        assertEquals(0, result.supportedVideos.size)
        assertEquals(2, result.unsupportedVideos.size)
        assertFalse(result.hasSupportedFiles)
    }
    
    @Test
    fun `processPlaylist handles empty playlist`() {
        val playlist = PlaylistWithVideos(
            playlist = Playlist(id = "playlist1", name = "Test Playlist", videoIds = emptyList()),
            videos = emptyList()
        )
        
        val result = PlaylistProcessor.processPlaylist(playlist)
        
        assertEquals(0, result.supportedVideos.size)
        assertEquals(0, result.unsupportedVideos.size)
        assertFalse(result.hasSupportedFiles)
    }
    
    @Test
    fun `processPlaylist includes both images and videos as supported`() {
        val videos = listOf(
            Video(id = "1", url = "https://example.com/image.jpg", name = "Image"),
            Video(id = "2", url = "https://example.com/video.mp4", name = "Video"),
            Video(id = "3", url = "https://example.com/image.png", name = "Image 2")
        )
        
        val playlist = PlaylistWithVideos(
            playlist = Playlist(id = "playlist1", name = "Test Playlist", videoIds = videos.map { it.id }),
            videos = videos
        )
        
        val result = PlaylistProcessor.processPlaylist(playlist)
        
        assertEquals(3, result.supportedVideos.size)
        assertEquals(0, result.unsupportedVideos.size)
        assertTrue(result.hasSupportedFiles)
    }
    
    @Test
    fun `processPlaylist preserves order for mixed media playlists`() {
        val playlist = TestMediaRepository.getMixedMediaPlaylist()
        val result = PlaylistProcessor.processPlaylist(playlist)
        
        // Verify order is preserved
        assertEquals(4, result.supportedVideos.size)
        assertEquals(0, result.unsupportedVideos.size)
        assertTrue(result.hasSupportedFiles)
        
        // Verify order: video1, image1, video2, image2
        val processedIds = result.supportedVideos.map { it.id }
        assertEquals(listOf("video1", "image1", "video2", "image2"), processedIds)
        
        // Verify media types are correctly identified
        val mediaTypes = result.supportedVideos.map { video ->
            MediaTypeDetector.detectMediaType(video.url, video.mimeType)
        }
        assertEquals(MediaType.VIDEO, mediaTypes[0])
        assertEquals(MediaType.IMAGE, mediaTypes[1])
        assertEquals(MediaType.VIDEO, mediaTypes[2])
        assertEquals(MediaType.IMAGE, mediaTypes[3])
    }
    
    @Test
    fun `processPlaylist correctly separates supported and unsupported in mixed playlist`() {
        val playlist = TestMediaRepository.getMixedWithUnsupportedPlaylist()
        val result = PlaylistProcessor.processPlaylist(playlist)
        
        // Should have 3 supported (video1, image1, video2) and 2 unsupported
        assertEquals(3, result.supportedVideos.size)
        assertEquals(2, result.unsupportedVideos.size)
        assertTrue(result.hasSupportedFiles)
        
        // Verify supported items
        val supportedIds = result.supportedVideos.map { it.id }.toSet()
        assertTrue(supportedIds.contains("video1"))
        assertTrue(supportedIds.contains("image1"))
        assertTrue(supportedIds.contains("video2"))
        
        // Verify unsupported items
        val unsupportedIds = result.unsupportedVideos.map { it.id }.toSet()
        assertTrue(unsupportedIds.contains("unsupported1"))
        assertTrue(unsupportedIds.contains("unsupported2"))
        
        // Verify no overlap
        assertTrue(supportedIds.intersect(unsupportedIds).isEmpty())
    }
    
    @Test
    fun `processPlaylist filters empty URLs to unsupported`() {
        val playlist = TestMediaRepository.getPlaylistWithEmptyUrls()
        val result = PlaylistProcessor.processPlaylist(playlist)
        
        // Should have 2 supported (valid1, valid2) and 2 unsupported (empty1, null1)
        assertEquals(2, result.supportedVideos.size)
        assertEquals(2, result.unsupportedVideos.size)
        assertTrue(result.hasSupportedFiles)
        
        // Verify supported items
        val supportedIds = result.supportedVideos.map { it.id }.toSet()
        assertTrue(supportedIds.contains("valid1"))
        assertTrue(supportedIds.contains("valid2"))
        
        // Verify empty URLs are unsupported
        val unsupportedIds = result.unsupportedVideos.map { it.id }.toSet()
        assertTrue(unsupportedIds.contains("empty1"))
        assertTrue(unsupportedIds.contains("null1"))
    }
    
    @Test
    fun `processPlaylist handles image-only playlist correctly`() {
        val playlist = TestMediaRepository.getImageOnlyPlaylist()
        val result = PlaylistProcessor.processPlaylist(playlist)
        
        assertEquals(3, result.supportedVideos.size)
        assertEquals(0, result.unsupportedVideos.size)
        assertTrue(result.hasSupportedFiles)
        
        // All should be images
        result.supportedVideos.forEach { video ->
            val mediaType = MediaTypeDetector.detectMediaType(video.url, video.mimeType)
            assertEquals(MediaType.IMAGE, mediaType)
        }
    }
    
    @Test
    fun `processPlaylist handles video-only playlist correctly`() {
        val playlist = TestMediaRepository.getVideoOnlyPlaylist()
        val result = PlaylistProcessor.processPlaylist(playlist)
        
        assertEquals(3, result.supportedVideos.size)
        assertEquals(0, result.unsupportedVideos.size)
        assertTrue(result.hasSupportedFiles)
        
        // All should be videos
        result.supportedVideos.forEach { video ->
            val mediaType = MediaTypeDetector.detectMediaType(video.url, video.mimeType)
            assertEquals(MediaType.VIDEO, mediaType)
        }
    }
    
    @Test
    fun `processPlaylist maintains original order when unsupported items are filtered`() {
        val videos = listOf(
            Video(id = "1", url = "https://example.com/video1.mp4", name = "Video 1"),
            Video(id = "2", url = "https://example.com/file.txt", name = "Unsupported"),
            Video(id = "3", url = "https://example.com/image1.jpg", name = "Image 1"),
            Video(id = "4", url = "https://example.com/video2.mp4", name = "Video 2"),
            Video(id = "5", url = "https://example.com/file.pdf", name = "Unsupported 2"),
            Video(id = "6", url = "https://example.com/image2.png", name = "Image 2")
        )
        
        val playlist = PlaylistWithVideos(
            playlist = Playlist(id = "playlist1", name = "Test", videoIds = videos.map { it.id }),
            videos = videos
        )
        
        val result = PlaylistProcessor.processPlaylist(playlist)
        
        // Order should be preserved: 1, 3, 4, 6 (unsupported 2 and 5 filtered out)
        val processedIds = result.supportedVideos.map { it.id }
        assertEquals(listOf("1", "3", "4", "6"), processedIds)
        
        // Verify unsupported are separate
        val unsupportedIds = result.unsupportedVideos.map { it.id }.toSet()
        assertTrue(unsupportedIds.contains("2"))
        assertTrue(unsupportedIds.contains("5"))
    }
}

