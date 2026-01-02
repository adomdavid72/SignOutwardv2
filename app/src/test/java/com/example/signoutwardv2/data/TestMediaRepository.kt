package com.example.signoutwardv2.data

import com.example.signoutwardv2.data.models.Playlist
import com.example.signoutwardv2.data.models.PlaylistWithVideos
import com.example.signoutwardv2.data.models.Video

/**
 * Test repository providing deterministic mixed media playlists
 * Uses real media URLs but cached in-memory for tests
 * No network calls after initial hydration
 * Read-only - no Supabase writes
 */
object TestMediaRepository {
    
    // Deterministic mixed playlist with real URLs (from Supabase, cached)
    // Format: 2 videos (mp4) + 2 images (jpg/webp) in alternating order
    fun getMixedMediaPlaylist(): PlaylistWithVideos {
        return PlaylistWithVideos(
            playlist = Playlist(
                id = "test-mixed-playlist",
                name = "Test Mixed Media Playlist",
                videoIds = listOf("video1", "image1", "video2", "image2")
            ),
            videos = listOf(
                // Video 1 (mp4)
                Video(
                    id = "video1",
                    url = "https://storage.supabase.co/object/public/videos/sample-video-1.mp4",
                    name = "Sample Video 1",
                    mimeType = "video/mp4",
                    type = "video"
                ),
                // Image 1 (jpg)
                Video(
                    id = "image1",
                    url = "https://storage.supabase.co/object/public/images/sample-image-1.jpg",
                    name = "Sample Image 1",
                    mimeType = "image/jpeg",
                    type = "image"
                ),
                // Video 2 (mp4)
                Video(
                    id = "video2",
                    url = "https://storage.supabase.co/object/public/videos/sample-video-2.mp4",
                    name = "Sample Video 2",
                    mimeType = "video/mp4",
                    type = "video"
                ),
                // Image 2 (webp)
                Video(
                    id = "image2",
                    url = "https://storage.supabase.co/object/public/images/sample-image-2.webp",
                    name = "Sample Image 2",
                    mimeType = "image/webp",
                    type = "image"
                )
            )
        )
    }
    
    // Playlist with images only
    fun getImageOnlyPlaylist(): PlaylistWithVideos {
        return PlaylistWithVideos(
            playlist = Playlist(
                id = "test-image-playlist",
                name = "Test Image Playlist",
                videoIds = listOf("img1", "img2", "img3")
            ),
            videos = listOf(
                Video(
                    id = "img1",
                    url = "https://storage.supabase.co/object/public/images/test-1.jpg",
                    name = "Test Image 1",
                    mimeType = "image/jpeg",
                    type = "image"
                ),
                Video(
                    id = "img2",
                    url = "https://storage.supabase.co/object/public/images/test-2.png",
                    name = "Test Image 2",
                    mimeType = "image/png",
                    type = "image"
                ),
                Video(
                    id = "img3",
                    url = "https://storage.supabase.co/object/public/images/test-3.webp",
                    name = "Test Image 3",
                    mimeType = "image/webp",
                    type = "image"
                )
            )
        )
    }
    
    // Playlist with videos only
    fun getVideoOnlyPlaylist(): PlaylistWithVideos {
        return PlaylistWithVideos(
            playlist = Playlist(
                id = "test-video-playlist",
                name = "Test Video Playlist",
                videoIds = listOf("vid1", "vid2", "vid3")
            ),
            videos = listOf(
                Video(
                    id = "vid1",
                    url = "https://storage.supabase.co/object/public/videos/test-1.mp4",
                    name = "Test Video 1",
                    mimeType = "video/mp4",
                    type = "video"
                ),
                Video(
                    id = "vid2",
                    url = "https://storage.supabase.co/object/public/videos/test-2.webm",
                    name = "Test Video 2",
                    mimeType = "video/webm",
                    type = "video"
                ),
                Video(
                    id = "vid3",
                    url = "https://storage.supabase.co/object/public/videos/test-3.mp4",
                    name = "Test Video 3",
                    mimeType = "video/mp4",
                    type = "video"
                )
            )
        )
    }
    
    // Playlist with unsupported files mixed in
    fun getMixedWithUnsupportedPlaylist(): PlaylistWithVideos {
        return PlaylistWithVideos(
            playlist = Playlist(
                id = "test-mixed-unsupported",
                name = "Test Mixed with Unsupported",
                videoIds = listOf("video1", "unsupported1", "image1", "unsupported2", "video2")
            ),
            videos = listOf(
                Video(
                    id = "video1",
                    url = "https://storage.supabase.co/object/public/videos/test.mp4",
                    name = "Supported Video",
                    mimeType = "video/mp4",
                    type = "video"
                ),
                Video(
                    id = "unsupported1",
                    url = "https://storage.supabase.co/object/public/files/test.txt",
                    name = "Unsupported File",
                    mimeType = "text/plain",
                    type = "file"
                ),
                Video(
                    id = "image1",
                    url = "https://storage.supabase.co/object/public/images/test.jpg",
                    name = "Supported Image",
                    mimeType = "image/jpeg",
                    type = "image"
                ),
                Video(
                    id = "unsupported2",
                    url = "https://storage.supabase.co/object/public/files/test.pdf",
                    name = "Unsupported PDF",
                    mimeType = "application/pdf",
                    type = "file"
                ),
                Video(
                    id = "video2",
                    url = "https://storage.supabase.co/object/public/videos/test2.mp4",
                    name = "Supported Video 2",
                    mimeType = "video/mp4",
                    type = "video"
                )
            )
        )
    }
    
    // Playlist with empty/null URLs (edge case)
    fun getPlaylistWithEmptyUrls(): PlaylistWithVideos {
        return PlaylistWithVideos(
            playlist = Playlist(
                id = "test-empty-urls",
                name = "Test Empty URLs",
                videoIds = listOf("valid1", "empty1", "valid2", "null1")
            ),
            videos = listOf(
                Video(
                    id = "valid1",
                    url = "https://storage.supabase.co/object/public/videos/test.mp4",
                    name = "Valid Video",
                    mimeType = "video/mp4"
                ),
                Video(
                    id = "empty1",
                    url = "",
                    name = "Empty URL",
                    mimeType = null
                ),
                Video(
                    id = "valid2",
                    url = "https://storage.supabase.co/object/public/images/test.jpg",
                    name = "Valid Image",
                    mimeType = "image/jpeg"
                ),
                Video(
                    id = "null1",
                    url = "", // Empty URL
                    name = "Null URL",
                    mimeType = null
                )
            )
        )
    }
}

