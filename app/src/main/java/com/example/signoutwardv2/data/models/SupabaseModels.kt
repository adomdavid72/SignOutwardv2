package com.example.signoutwardv2.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================================
// SUPABASE DATA MODELS
// ============================================================================
// Database table mappings for Supabase REST API
// All foreign keys respected:
//   - screen_id → screens.id
//   - video_id → videos.id  
//   - playlist_id → playlists.id
// ============================================================================

/**
 * TABLE: device_pairing_codes
 * 
 * Used for device pairing validation
 * 
 * Example row:
 * {
 *   "id": "uuid",
 *   "code": "ABC123",
 *   "screen_id": "uuid",
 *   "is_used": false,
 *   "expires_at": "2024-12-31T23:59:59Z",
 *   "created_at": "2024-01-01T00:00:00Z",
 *   "used_at": null
 * }
 */
@Serializable
data class DevicePairingCode(
    val id: String,
    val code: String,
    @SerialName("screen_id") val screenId: String,
    @SerialName("is_used") val isUsed: Boolean = false,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("used_at") val usedAt: String? = null
)

/**
 * TABLE: screens
 * 
 * Represents a physical display device
 */
@Serializable
data class Screen(
    val id: String,
    val name: String? = null,
    @SerialName("location_id") val locationId: String? = null,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("is_paired") val isPaired: Boolean = false,
    @SerialName("paired_at") val pairedAt: String? = null,
    @SerialName("last_sync") val lastSync: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null
)

/**
 * TABLE: schedules
 * 
 * Links screens to playlists with time-based rules
 */
@Serializable
data class Schedule(
    val id: String,
    @SerialName("screen_id") val screenId: String,
    @SerialName("playlist_id") val playlistId: String,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("days_of_week") val daysOfWeek: List<Int>? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    val priority: Int = 0,
    @SerialName("created_at") val createdAt: String? = null
)

/**
 * TABLE: playlist_assignments
 * 
 * Assigns playlists to screens, groups, or locations
 */
@Serializable
data class PlaylistAssignment(
    val id: String,
    @SerialName("playlist_id") val playlistId: String,
    @SerialName("screen_id") val screenId: String? = null,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("location_id") val locationId: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    val priority: Int = 0,
    @SerialName("created_at") val createdAt: String? = null
)

/**
 * TABLE: playlists
 * 
 * Collection of videos to play
 * 
 * Example row:
 * {
 *   "id": "uuid",
 *   "name": "Summer Campaign",
 *   "video_ids": ["uuid1", "uuid2"],
 *   "is_active": true,
 *   "loop": true
 * }
 */
@Serializable
data class Playlist(
    val id: String,
    val name: String? = null,
    @SerialName("video_ids") val videoIds: List<String> = emptyList(),
    @SerialName("is_active") val isActive: Boolean = true,
    val loop: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null
)

/**
 * TABLE: videos
 * 
 * Media assets (videos or images)
 * 
 * Example row:
 * {
 *   "id": "uuid",
 *   "name": "Product Ad",
 *   "url": "https://storage.supabase.co/...",
 *   "type": "video",
 *   "duration_seconds": 30,
 *   "display_duration_seconds": 7
 * }
 */
@Serializable
data class Video(
    val id: String,
    val name: String? = null,
    val url: String,
    val type: String = "video", // "video" or "image" (legacy field)
    @SerialName("mime_type") val mimeType: String? = null, // MIME type for better detection
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("display_duration_seconds") val displayDurationSeconds: Int? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null
)

/**
 * TABLE: playback_logs
 * 
 * Records each media playback event
 * 
 * Example INSERT:
 * {
 *   "screen_id": "uuid",           // FK → screens.id
 *   "video_id": "uuid",            // FK → videos.id
 *   "playlist_id": "uuid",         // FK → playlists.id
 *   "start_time": "2024-01-01T12:00:00Z",
 *   "end_time": "2024-01-01T12:00:30Z",
 *   "status": "completed",
 *   "duration_seconds": 30,
 *   "success": true,
 *   "device_app_version": "1.0"
 * }
 */
@Serializable
data class PlaybackLog(
    val id: String? = null,
    @SerialName("screen_id") val screenId: String,
    @SerialName("video_id") val videoId: String,
    @SerialName("playlist_id") val playlistId: String? = null,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String? = null,
    val status: String = "playing", // "playing", "completed", "error"
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    val success: Boolean = true,
    @SerialName("device_app_version") val deviceAppVersion: String? = null,
    @SerialName("location_data") val locationData: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

/**
 * TABLE: device_analytics
 * 
 * Daily aggregated analytics per screen
 * 
 * Example row:
 * {
 *   "screen_id": "uuid",
 *   "date": "2024-01-01",
 *   "total_videos_played": 150,
 *   "total_playlists_played": 10,
 *   "total_playback_duration_seconds": 4500,
 *   "successful_playbacks": 148,
 *   "failed_playbacks": 2
 * }
 */
@Serializable
data class DeviceAnalytics(
    val id: String? = null,
    @SerialName("screen_id") val screenId: String,
    val date: String? = null, // YYYY-MM-DD format
    @SerialName("total_videos_played") val totalVideosPlayed: Int = 0,
    @SerialName("total_playlists_played") val totalPlaylistsPlayed: Int = 0,
    @SerialName("total_playback_duration_seconds") val totalPlaybackDurationSeconds: Long = 0,
    @SerialName("successful_playbacks") val successfulPlaybacks: Int = 0,
    @SerialName("failed_playbacks") val failedPlaybacks: Int = 0,
    @SerialName("last_updated") val lastUpdated: String? = null
)

// ============================================================================
// API Response Wrappers
// ============================================================================

@Serializable
data class PairingResponse(
    val success: Boolean,
    val screenId: String? = null,
    val groupId: String? = null,
    val locationId: String? = null,
    val message: String? = null
)

/**
 * Complete playlist with videos for playback
 */
data class PlaylistWithVideos(
    val playlist: Playlist,
    val videos: List<Video>,
    val scheduleId: String? = null
)
