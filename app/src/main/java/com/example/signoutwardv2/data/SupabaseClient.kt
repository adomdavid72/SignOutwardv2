package com.example.signoutwardv2.data

import android.util.Log
import com.example.signoutwardv2.BuildConfig
import com.example.signoutwardv2.data.models.*
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// ============================================================================
// SUPABASE CLIENT
// ============================================================================
// REST API client for Supabase backend
// 
// Table Mappings (from metadata descriptions):
//   - device_pairing_codes: Pairing code validation
//   - screens: Device registration, status, heartbeat
//   - schedules: Time-based playlist assignments
//   - playlist_assignments: Screen/group/location playlist links
//   - playlists: Playlist metadata with video_ids
//   - videos: Media assets
//   - playback_logs: Playback event logging
//   - device_analytics: Daily aggregated stats
// ============================================================================

object SupabaseClient {
    
    private const val TAG = "SupabaseClient"
    
    private val supabaseUrl = BuildConfig.SUPABASE_URL
    private val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
    }
    
    // CRITICAL: Lazy initialization prevents HttpClient creation during app startup
    // This avoids network exceptions crashing the app before UI is ready
    private val client: HttpClient by lazy {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(json)
            }
            install(Logging) {
                level = LogLevel.BODY
                logger = object : Logger {
                    override fun log(message: String) {
                        message.chunked(3000).forEach { chunk ->
                            Log.d(TAG, chunk)
                        }
                    }
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 30000
            }
            // Note: HttpRequestRetry plugin not available in current Ktor version
            // Retry logic is handled in safeApiCall wrapper instead
            defaultRequest {
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $supabaseKey")
                contentType(ContentType.Application.Json)
            }
        }
    }
    
    /**
     * Safe API call wrapper - catches network exceptions and prevents app crashes
     * Returns Result<T> instead of throwing exceptions
     */
    private suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: java.net.SocketException) {
            Log.e(TAG, "Network connection error: ${e.message}", e)
            Result.failure(e)
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Host not found: ${e.message}", e)
            Result.failure(e)
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "Connection refused: ${e.message}", e)
            Result.failure(e)
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Connection timeout: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    private fun now(): String = Instant.now().toString()
    private fun today(): String = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)
    
    // ========================================================================
    // 1. DEVICE PAIRING
    // ========================================================================
    // Tables: device_pairing_codes, screens
    // 
    // Validation:
    //   - code matches device_pairing_codes.code
    //   - is_used = false
    //   - expires_at is IGNORED (no expiry check)
    // 
    // On success:
    //   - device_pairing_codes: is_used=true, used_at=NOW()
    //   - screens: is_paired=true, paired_at=NOW(), status='online', last_sync=NOW()
    // ========================================================================
    
    suspend fun validatePairingCode(code: String): Result<PairingResponse> {
        // Note: This function already has comprehensive error handling
        // safeApiCall wrapper not needed here, but HttpClient is lazy-initialized
        return try {
            val upperCode = code.uppercase().trim()
            Log.d(TAG, "=== PAIRING ATTEMPT ===")
            Log.d(TAG, "Code: $upperCode")
            
            // Step 1: Fetch pairing code (ignore expires_at)
            val codeResponse: HttpResponse = client.get("$supabaseUrl/rest/v1/device_pairing_codes") {
                parameter("select", "*")
                parameter("code", "eq.$upperCode")
                parameter("is_used", "eq.false")
            }
            
            val codeResponseBody = codeResponse.bodyAsText()
            Log.d(TAG, "Pairing code response: ${codeResponse.status} - $codeResponseBody")
            
            if (codeResponse.status != HttpStatusCode.OK) {
                Log.e(TAG, "Pairing code query failed with status: ${codeResponse.status}")
                Log.e(TAG, "Response body: $codeResponseBody")
                return Result.success(PairingResponse(
                    success = false,
                    message = "Server error: ${codeResponse.status}"
                ))
            }
            
            val codesArray = json.parseToJsonElement(codeResponseBody).jsonArray
            
            if (codesArray.isEmpty()) {
                // Check if code exists but is already used
                val checkUsedResponse: HttpResponse = client.get("$supabaseUrl/rest/v1/device_pairing_codes") {
                    parameter("select", "is_used")
                    parameter("code", "eq.$upperCode")
                }
                
                if (checkUsedResponse.status == HttpStatusCode.OK) {
                    val usedBody = checkUsedResponse.bodyAsText()
                    val usedArray = json.parseToJsonElement(usedBody).jsonArray
                    if (usedArray.isNotEmpty()) {
                        val isUsed = usedArray[0].jsonObject["is_used"]?.jsonPrimitive?.booleanOrNull ?: false
                        Log.w(TAG, "Code $upperCode exists but is_used=$isUsed")
                        return Result.success(PairingResponse(
                            success = false,
                            message = if (isUsed) "Pairing code already used" else "Pairing code not found"
                        ))
                    }
                }
                
                Log.w(TAG, "No pairing code found for: $upperCode")
                return Result.success(PairingResponse(
                    success = false,
                    message = "Invalid or already used pairing code"
                ))
            }
            
            val codeObj = codesArray[0].jsonObject
            
            val codeId = codeObj["id"]?.jsonPrimitive?.content
            if (codeId == null) {
                Log.e(TAG, "Pairing code object missing 'id' field. Full object: $codeObj")
                return Result.success(PairingResponse(success = false, message = "Missing code id"))
            }
            
            val screenId = codeObj["screen_id"]?.jsonPrimitive?.content
            if (screenId == null) {
                Log.e(TAG, "Pairing code object missing 'screen_id' field. Code ID: $codeId, Full object: $codeObj")
                return Result.success(PairingResponse(success = false, message = "Missing screen_id"))
            }
            
            Log.d(TAG, "Valid code found - Code ID: $codeId, Screen ID: $screenId")
            
            // Step 2: Fetch screen details
            var groupId: String? = null
            var locationId: String? = null
            
            try {
                val screenResponse: HttpResponse = client.get("$supabaseUrl/rest/v1/screens") {
                    parameter("select", "*")
                    parameter("id", "eq.$screenId")
                }
                
                if (screenResponse.status == HttpStatusCode.OK) {
                    val screensArray = json.parseToJsonElement(screenResponse.bodyAsText()).jsonArray
                    if (screensArray.isNotEmpty()) {
                        val screenObj = screensArray[0].jsonObject
                        groupId = screenObj["group_id"]?.jsonPrimitive?.contentOrNull
                        locationId = screenObj["location_id"]?.jsonPrimitive?.contentOrNull
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not fetch screen details: ${e.message}")
            }
            
            val currentTime = now()
            
            // Step 3: Mark code as used
            val patchCodeResponse = client.patch("$supabaseUrl/rest/v1/device_pairing_codes") {
                parameter("id", "eq.$codeId")
                header("Prefer", "return=minimal")
                setBody(buildJsonObject {
                    put("is_used", true)
                    put("used_at", currentTime)
                }.toString())
            }
            val patchCodeBody = patchCodeResponse.bodyAsText()
            Log.d(TAG, "Mark code used: ${patchCodeResponse.status} - $patchCodeBody")
            
            if (patchCodeResponse.status.value !in 200..299) {
                Log.e(TAG, "Failed to mark code as used. Status: ${patchCodeResponse.status}, Body: $patchCodeBody")
                return Result.success(PairingResponse(
                    success = false,
                    message = "Failed to mark code as used: ${patchCodeResponse.status}"
                ))
            }
            
            // Step 4: Update screen as paired with status='online'
            val patchScreenResponse = client.patch("$supabaseUrl/rest/v1/screens") {
                parameter("id", "eq.$screenId")
                header("Prefer", "return=minimal")
                setBody(buildJsonObject {
                    put("is_paired", true)
                    put("paired_at", currentTime)
                    put("status", "online")
                    put("last_sync", currentTime)
                }.toString())
            }
            val patchScreenBody = patchScreenResponse.bodyAsText()
            Log.d(TAG, "Update screen: ${patchScreenResponse.status} - $patchScreenBody")
            
            if (patchScreenResponse.status.value !in 200..299) {
                Log.e(TAG, "Failed to update screen. Status: ${patchScreenResponse.status}, Body: $patchScreenBody")
                return Result.success(PairingResponse(
                    success = false,
                    message = "Failed to update screen: ${patchScreenResponse.status}"
                ))
            }
            
            Log.d(TAG, "=== PAIRING SUCCESS ===")
            Log.d(TAG, "Screen ID: $screenId, Group ID: $groupId, Location ID: $locationId")
            Result.success(PairingResponse(
                success = true,
                screenId = screenId,
                groupId = groupId,
                locationId = locationId,
                message = "Device paired successfully"
            ))
        } catch (e: Exception) {
            Log.e(TAG, "=== PAIRING ERROR ===", e)
            Result.failure(e)
        }
    }
    
    // ========================================================================
    // 2. FETCH PLAYLISTS AND SCHEDULES
    // ========================================================================
    // Tables: schedules, playlist_assignments, playlists, videos
    // 
    // Priority:
    //   1. Active schedules (screen_id, is_active=true)
    //   2. Playlist assignments (screen_id > group_id > location_id)
    // 
    // Empty playlist: Return null, app shows "You can run your ads here"
    // ========================================================================
    
    suspend fun getSchedules(screenId: String): Result<List<Schedule>> {
        return try {
            Log.d(TAG, "Fetching schedules for screen: $screenId")
            
            val response: HttpResponse = client.get("$supabaseUrl/rest/v1/schedules") {
                parameter("select", "*")
                parameter("screen_id", "eq.$screenId")
                parameter("is_active", "eq.true")
                parameter("order", "priority.desc")
            }
            
            val body = response.bodyAsText()
            Log.d(TAG, "Schedules: ${response.status}")
            
            if (response.status == HttpStatusCode.OK) {
                val schedules: List<Schedule> = json.decodeFromString(body)
                Result.success(schedules)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching schedules", e)
            Result.success(emptyList())
        }
    }
    
    suspend fun getPlaylistAssignments(
        screenId: String,
        groupId: String? = null,
        locationId: String? = null
    ): Result<List<PlaylistAssignment>> {
        return try {
            Log.d(TAG, "Fetching playlist assignments")
            
            val conditions = mutableListOf("screen_id.eq.$screenId")
            groupId?.let { conditions.add("group_id.eq.$it") }
            locationId?.let { conditions.add("location_id.eq.$it") }
            
            val response: HttpResponse = client.get("$supabaseUrl/rest/v1/playlist_assignments") {
                parameter("select", "*")
                parameter("or", "(${conditions.joinToString(",")})")
                parameter("is_active", "eq.true")
                parameter("order", "priority.desc")
            }
            
            val body = response.bodyAsText()
            Log.d(TAG, "Playlist assignments: ${response.status}")
            
            if (response.status == HttpStatusCode.OK) {
                val assignments: List<PlaylistAssignment> = json.decodeFromString(body)
                Result.success(assignments)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching playlist assignments", e)
            Result.success(emptyList())
        }
    }
    
    suspend fun getPlaylist(playlistId: String): Result<Playlist?> {
        return try {
            Log.d(TAG, "Fetching playlist: $playlistId")
            
            val response: HttpResponse = client.get("$supabaseUrl/rest/v1/playlists") {
                parameter("select", "*")
                parameter("id", "eq.$playlistId")
                // Note: Removed is_active filter as column may not exist
            }
            
            val body = response.bodyAsText()
            Log.d(TAG, "Playlist response: ${response.status} - $body")
            
            if (response.status == HttpStatusCode.OK) {
                val playlists: List<Playlist> = json.decodeFromString(body)
                Result.success(playlists.firstOrNull())
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching playlist", e)
            Result.success(null)
        }
    }
    
    suspend fun getVideos(videoIds: List<String>): Result<List<Video>> {
        return try {
            if (videoIds.isEmpty()) return Result.success(emptyList())
            
            Log.d(TAG, "Fetching ${videoIds.size} videos")
            
            // Supabase REST API has a limit on URL length, so batch if needed
            val batchSize = 100
            val allVideos = mutableListOf<Video>()
            
            videoIds.chunked(batchSize).forEach { batch ->
                val idsParam = "(${batch.joinToString(",") { "\"$it\"" }})"
                
                val response: HttpResponse = client.get("$supabaseUrl/rest/v1/videos") {
                    parameter("select", "*")
                    parameter("id", "in.$idsParam")
                    // Note: Removed is_active filter - filter if column exists in your schema
                }
                
                val body = response.bodyAsText()
                Log.d(TAG, "Videos batch response: ${response.status}, body length: ${body.length}")
                
                if (response.status == HttpStatusCode.OK) {
                    try {
                        val videos: List<Video> = json.decodeFromString(body)
                        allVideos.addAll(videos)
                        Log.d(TAG, "Parsed ${videos.size} videos from batch")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing videos JSON: ${e.message}")
                        Log.e(TAG, "Response body (first 500 chars): ${body.take(500)}")
                    }
                } else {
                    Log.w(TAG, "Failed to fetch videos batch: ${response.status}")
                }
            }
            
            Log.d(TAG, "Total videos fetched: ${allVideos.size}")
            Result.success(allVideos)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching videos", e)
            Result.failure(e)
        }
    }
    
    /**
     * Fetch complete active playlist with videos
     * Returns null if no playlist available (triggers empty state, no logging)
     */
    suspend fun getActivePlaylistWithVideos(
        screenId: String,
        groupId: String? = null,
        locationId: String? = null
    ): Result<PlaylistWithVideos?> {
        return try {
            Log.d(TAG, "=== FETCHING PLAYLIST ===")
            
            // Check schedules first
            val schedules = getSchedules(screenId).getOrNull() ?: emptyList()
            var playlistId: String? = null
            var scheduleId: String? = null
            
            if (schedules.isNotEmpty()) {
                val activeSchedule = schedules.first()
                playlistId = activeSchedule.playlistId
                scheduleId = activeSchedule.id
                Log.d(TAG, "Found schedule playlist: $playlistId")
            }
            
            // Fall back to assignments
            if (playlistId == null) {
                val assignments = getPlaylistAssignments(screenId, groupId, locationId)
                    .getOrNull() ?: emptyList()
                
                if (assignments.isNotEmpty()) {
                    playlistId = assignments.first().playlistId
                    Log.d(TAG, "Found assignment playlist: $playlistId")
                }
            }
            
            if (playlistId == null) {
                Log.d(TAG, "No playlist - showing empty state")
                return Result.success(null)
            }
            
            val playlist = getPlaylist(playlistId).getOrNull()
            if (playlist == null) {
                Log.d(TAG, "Playlist not found or failed to fetch")
                return Result.success(null)
            }
            
            Log.d(TAG, "Playlist found: ${playlist.name}, video_ids count: ${playlist.videoIds.size}")
            
            if (playlist.videoIds.isEmpty()) {
                Log.d(TAG, "Playlist has no video_ids")
                return Result.success(null)
            }
            
            val videos = getVideos(playlist.videoIds).getOrNull() ?: emptyList()
            Log.d(TAG, "Fetched ${videos.size} videos from ${playlist.videoIds.size} video_ids")
            
            if (videos.isEmpty()) {
                Log.d(TAG, "No active videos found in playlist")
                return Result.success(null)
            }
            
            val sortedVideos = playlist.videoIds.mapNotNull { id ->
                videos.find { it.id == id }
            }
            
            Log.d(TAG, "=== PLAYLIST LOADED: ${sortedVideos.size} videos ===")
            Result.success(PlaylistWithVideos(playlist, sortedVideos, scheduleId))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching playlist", e)
            Result.failure(e)
        }
    }
    
    // ========================================================================
    // 3. PLAYBACK LOGGING
    // ========================================================================
    // Table: playback_logs
    // 
    // Fields: screen_id, video_id, playlist_id, start_time, end_time,
    //         status, error_message, duration_seconds, success,
    //         device_app_version, location_data
    // ========================================================================
    
    suspend fun logPlayback(log: PlaybackLog): Result<Unit> {
        return try {
            Log.d(TAG, "Logging playback: video=${log.videoId}, status=${log.status}")
            
            val body = buildJsonObject {
                put("screen_id", log.screenId)
                put("video_id", log.videoId)
                log.playlistId?.let { put("playlist_id", it) }
                put("start_time", log.startTime)
                log.endTime?.let { put("end_time", it) }
                put("status", log.status)
                log.errorMessage?.let { put("error_message", it) }
                log.durationSeconds?.let { put("duration_seconds", it) }
                put("success", log.success)
                log.deviceAppVersion?.let { put("device_app_version", it) }
                log.locationData?.let { put("location_data", it) }
            }
            
            val response = client.post("$supabaseUrl/rest/v1/playback_logs") {
                header("Prefer", "return=minimal")
                setBody(body.toString())
            }
            
            Log.d(TAG, "Playback log: ${response.status}")
            
            if (response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging playback", e)
            Result.failure(e)
        }
    }
    
    // ========================================================================
    // 4. DEVICE ANALYTICS
    // ========================================================================
    // Table: device_analytics
    // 
    // Daily aggregation per screen:
    //   - total_videos_played, total_playlists_played
    //   - total_playback_duration_seconds
    //   - successful_playbacks, failed_playbacks
    //   - date = current date
    // ========================================================================
    
    suspend fun updateAnalytics(
        screenId: String,
        videosPlayed: Int = 0,
        playlistsPlayed: Int = 0,
        durationSeconds: Long = 0,
        successful: Boolean = true
    ): Result<Unit> {
        return try {
            val currentDate = today()
            Log.d(TAG, "Updating analytics: screen=$screenId, date=$currentDate")
            
            // Check for existing record
            val getResponse: HttpResponse = client.get("$supabaseUrl/rest/v1/device_analytics") {
                parameter("select", "*")
                parameter("screen_id", "eq.$screenId")
                parameter("date", "eq.$currentDate")
            }
            
            val body = getResponse.bodyAsText()
            val existingRecords: List<DeviceAnalytics> = 
                if (getResponse.status == HttpStatusCode.OK) {
                    try { json.decodeFromString(body) } catch (e: Exception) { emptyList() }
                } else emptyList()
            
            val currentTime = now()
            
            if (existingRecords.isEmpty()) {
                // Create new record
                val insertBody = buildJsonObject {
                    put("screen_id", screenId)
                    put("date", currentDate)
                    put("total_videos_played", videosPlayed)
                    put("total_playlists_played", playlistsPlayed)
                    put("total_playback_duration_seconds", durationSeconds)
                    put("successful_playbacks", if (successful) 1 else 0)
                    put("failed_playbacks", if (!successful) 1 else 0)
                    put("last_updated", currentTime)
                }
                
                client.post("$supabaseUrl/rest/v1/device_analytics") {
                    header("Prefer", "return=minimal")
                    setBody(insertBody.toString())
                }
            } else {
                // Update existing
                val current = existingRecords.first()
                
                val updateBody = buildJsonObject {
                    put("total_videos_played", current.totalVideosPlayed + videosPlayed)
                    put("total_playlists_played", current.totalPlaylistsPlayed + playlistsPlayed)
                    put("total_playback_duration_seconds", current.totalPlaybackDurationSeconds + durationSeconds)
                    put("successful_playbacks", current.successfulPlaybacks + if (successful) 1 else 0)
                    put("failed_playbacks", current.failedPlaybacks + if (!successful) 1 else 0)
                    put("last_updated", currentTime)
                }
                
                client.patch("$supabaseUrl/rest/v1/device_analytics") {
                    parameter("screen_id", "eq.$screenId")
                    parameter("date", "eq.$currentDate")
                    header("Prefer", "return=minimal")
                    setBody(updateBody.toString())
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating analytics", e)
            Result.failure(e)
        }
    }
    
    // ========================================================================
    // 5. HEARTBEAT & ONLINE/OFFLINE TRACKING
    // ========================================================================
    // Table: screens
    // 
    // Device heartbeat (every 30-60s):
    //   UPDATE screens SET last_sync = NOW(), status = 'online' WHERE id = :screen_id
    // 
    // Offline detection (backend scheduled job):
    //   UPDATE screens SET status = 'offline' 
    //   WHERE last_sync < NOW() - INTERVAL '5 minutes' AND status = 'online'
    // ========================================================================
    
    /**
     * Send heartbeat to update last_sync and set status to 'online'
     * Should be called every 30-60 seconds
     */
    suspend fun sendHeartbeat(screenId: String): Result<Unit> {
        return try {
            val currentTime = now()
            Log.d(TAG, "Sending heartbeat for screen: $screenId")
            
            val body = buildJsonObject {
                put("last_sync", currentTime)
                put("status", "online")
            }
            
            val response = client.patch("$supabaseUrl/rest/v1/screens") {
                parameter("id", "eq.$screenId")
                header("Prefer", "return=minimal")
                setBody(body.toString())
            }
            
            Log.d(TAG, "Heartbeat: ${response.status}")
            
            if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NoContent) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Heartbeat failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending heartbeat", e)
            Result.failure(e)
        }
    }
    
    /**
     * Set device status to offline (called when app is closing/pausing)
     */
    suspend fun setOffline(screenId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Setting screen offline: $screenId")
            
            val body = buildJsonObject {
                put("status", "offline")
                put("last_sync", now())
            }
            
            val response = client.patch("$supabaseUrl/rest/v1/screens") {
                parameter("id", "eq.$screenId")
                header("Prefer", "return=minimal")
                setBody(body.toString())
            }
            
            Log.d(TAG, "Set offline: ${response.status}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting offline", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update last_sync timestamp (legacy method, use sendHeartbeat instead)
     */
    suspend fun updateLastSync(screenId: String): Result<Unit> {
        return sendHeartbeat(screenId)
    }
    
    // ========================================================================
    // 8. VISITOR COUNTS
    // ========================================================================
    // Table: visitor_counts
    // 
    // Stores hourly visitor counts per device
    // ========================================================================
    
    /**
     * Upload visitor count to Supabase
     * 
     * @param visitorCount VisitorCount object with device_id, timestamp, and count
     * @return Result indicating success or failure
     */
    suspend fun uploadVisitorCount(visitorCount: com.example.signoutwardv2.data.models.VisitorCount): Result<Unit> {
        return try {
            Log.d(TAG, "Uploading visitor count: device=${visitorCount.deviceId}, timestamp=${visitorCount.timestamp}, count=${visitorCount.count}")
            
            val response = client.post("$supabaseUrl/rest/v1/visitor_counts") {
                setBody(visitorCount)
            }
            
            when (response.status.value) {
                in 200..299 -> {
                    Log.d(TAG, "Visitor count uploaded successfully")
                    Result.success(Unit)
                }
                else -> {
                    val errorBody = response.bodyAsText()
                    Log.e(TAG, "Failed to upload visitor count: ${response.status} - $errorBody")
                    Result.failure(Exception("Upload failed: ${response.status} - $errorBody"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading visitor count", e)
            Result.failure(e)
        }
    }
}
