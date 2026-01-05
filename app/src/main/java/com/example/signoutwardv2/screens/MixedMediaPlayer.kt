package com.example.signoutwardv2.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.view.SurfaceHolder
import android.view.SurfaceView
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.signoutwardv2.data.MediaType
import com.example.signoutwardv2.data.PlaybackRepository
import com.example.signoutwardv2.data.models.Video
import com.example.signoutwardv2.playback.EnhancedPlaybackEngine
import com.example.signoutwardv2.playback.MediaSourceLogger
import com.example.signoutwardv2.playback.PlaybackState
import com.example.signoutwardv2.playback.PlaybackStateMachine
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.net.Uri

/**
 * MixedMediaPlayer - Strict state machine-based player for mixed media playlists
 * 
 * FIXES PRODUCTION BUG:
 * - Images never display in mixed playlists
 * - Video playback loops indefinitely
 * 
 * SOLUTION:
 * - Strict state machine with separate video/image paths
 * - ExoPlayer STOPPED when playing images
 * - Explicit timer for images (no ExoPlayer dependency)
 * - UI visibility strictly enforced
 * - One completion event per item guaranteed
 */
@Composable
fun MixedMediaPlayer(
    playbackItems: List<EnhancedPlaybackEngine.EnhancedPlaybackItem>,
    unsupportedVideos: List<Video>,
    screenId: String,
    repository: PlaybackRepository,
    playlistId: String,
    playbackEngine: EnhancedPlaybackEngine
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    // State machine for strict playback control
    val stateMachine = remember { PlaybackStateMachine() }
    
    // Track if playback is paused (for lifecycle)
    var isPaused by remember { mutableStateOf(false) }
    
    // ExoPlayer instance (ONLY for videos)
    val exoPlayer = remember {
        playbackEngine.createOptimizedPlayer().apply {
            repeatMode = Player.REPEAT_MODE_OFF // CRITICAL: No looping
        }
    }
    
    // Current playlist index
    var currentIndex by remember { mutableIntStateOf(0) }
    var playlistLoopCount by remember { mutableIntStateOf(0) }
    
    // Surface readiness state - CRITICAL for hardware GPU rendering
    // ExoPlayer must wait for SurfaceView surface to be created before starting playback
    // This prevents C2BqBuffer dequeue starvation on physical devices
    var isSurfaceReady by remember { mutableStateOf(false) }
    var pendingVideoIndex by remember { mutableIntStateOf(-1) }
    
    // Callback to handle surface readiness - used from SurfaceHolder.Callback
    // This callback reads the current pendingVideoIndex value when surface is created
    val onSurfaceReady = remember {
        {
            scope.launch {
                isSurfaceReady = true
                val currentPendingIndex = pendingVideoIndex
                if (currentPendingIndex >= 0 && currentPendingIndex < exoPlayer.mediaItemCount) {
                    if (exoPlayer.playbackState == Player.STATE_IDLE) {
                        exoPlayer.prepare()
                        Log.d("MixedMediaPlayer", "ExoPlayer prepared after surface creation")
                    }
                    exoPlayer.seekTo(currentPendingIndex, 0)
                    exoPlayer.playWhenReady = true
                    Log.d("MixedMediaPlayer", "Started pending video at index $currentPendingIndex")
                    pendingVideoIndex = -1
                } else if (exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.mediaItemCount > 0) {
                    exoPlayer.prepare()
                    Log.d("MixedMediaPlayer", "ExoPlayer prepared (surface ready, no pending video)")
                }
            }
        }
    }
    
    val onSurfaceDestroyed = remember {
        {
            scope.launch {
                isSurfaceReady = false
                exoPlayer.clearVideoSurface()
                exoPlayer.stop()
                exoPlayer.playWhenReady = false
            }
        }
    }
    
    // Preload video MediaItems into ExoPlayer queue (but don't prepare yet)
    var allMediaItemsPrepared by remember { mutableStateOf(false) }
    
    LaunchedEffect(playbackItems) {
        // Log source summary for validation
        if (playbackItems.isNotEmpty()) {
            MediaSourceLogger.logPlaybackSourceSummary(playbackItems)
        }
        
        val videoMediaItems = playbackEngine.getVideoMediaItems(playbackItems)
        
        if (videoMediaItems.isNotEmpty()) {
            exoPlayer.setMediaItems(videoMediaItems)
            // CRITICAL: Do NOT call prepare() here - wait for surface readiness
            // prepare() will be called after surface is created and attached
            Log.d("MixedMediaPlayer", "Preloaded ${videoMediaItems.size} video MediaItems (waiting for surface)")
        }
        
        allMediaItemsPrepared = true
    }
    
    // Log unsupported files
    LaunchedEffect(Unit) {
        unsupportedVideos.forEach { video ->
            scope.launch {
                repository.logUnsupportedFile(
                    screenId = screenId,
                    videoId = video.id,
                    playlistId = playlistId,
                    errorMessage = "unsupported file type"
                )
            }
        }
    }
    
    // Helper function to check if media file is available
    suspend fun isMediaFileAvailable(item: EnhancedPlaybackEngine.EnhancedPlaybackItem): Boolean {
        return try {
            when {
                // Check if cached
                item.isCached -> {
                    val uri = Uri.parse(item.playbackUri)
                    val file = File(uri.path ?: "")
                    val exists = file.exists() && file.length() > 0
                    if (!exists) {
                        Log.w("MixedMediaPlayer", "Cached file missing: ${item.playbackUri}")
                    }
                    exists
                }
                // For remote URLs, assume available (will fail during playback if not)
                item.sourceType == MediaSourceLogger.SourceType.REMOTE -> {
                    true // Will be handled by ExoPlayer/Coil
                }
                // Local files
                else -> {
                    val uri = Uri.parse(item.playbackUri)
                    val file = File(uri.path ?: "")
                    val exists = file.exists() && file.length() > 0
                    if (!exists) {
                        Log.w("MixedMediaPlayer", "Local file missing: ${item.playbackUri}")
                    }
                    exists
                }
            }
        } catch (e: Exception) {
            Log.e("MixedMediaPlayer", "Error checking file availability: ${e.message}", e)
            false
        }
    }
    
    // Track state machine state changes using a key that changes when state transitions
    var stateTransitionKey by remember { mutableIntStateOf(0) }
    
    // Start playback of current item with file availability check
    // CRITICAL: Watch currentIndex to ensure progression after completion
    // CRITICAL: Wrap in try-catch to prevent crashes
    LaunchedEffect(currentIndex, allMediaItemsPrepared, isPaused, stateTransitionKey) {
        try {
            // Log current state information
            val currentState = stateMachine.getCurrentState()
            val currentItem = stateMachine.getCurrentItem()
            val currentItemIndex = stateMachine.getCurrentIndex()
            val nextIndex = if (playbackItems.isNotEmpty()) (currentIndex + 1) % playbackItems.size else 0
            val nextItem = playbackItems.getOrNull(nextIndex)
            
            Log.d("MixedMediaPlayer", "=== LaunchedEffect TRIGGERED ===")
            Log.d("MixedMediaPlayer", "currentIndex: $currentIndex/${playbackItems.size - 1}")
            Log.d("MixedMediaPlayer", "currentState: ${currentState::class.simpleName} | ${stateMachine.getStateInfo()}")
            Log.d("MixedMediaPlayer", "currentItem: ${currentItem?.video?.id} (index: $currentItemIndex)")
            Log.d("MixedMediaPlayer", "nextItemIndex: $nextIndex")
            Log.d("MixedMediaPlayer", "nextItem: ${nextItem?.video?.id} (type: ${nextItem?.mediaType?.name})")
            Log.d("MixedMediaPlayer", "allMediaItemsPrepared: $allMediaItemsPrepared")
            Log.d("MixedMediaPlayer", "isPaused: $isPaused")
            
            if (!allMediaItemsPrepared || playbackItems.isEmpty() || isPaused) {
                Log.d("MixedMediaPlayer", "LaunchedEffect early return - conditions not met")
                Log.d("MixedMediaPlayer", "  allMediaItemsPrepared: $allMediaItemsPrepared")
                Log.d("MixedMediaPlayer", "  playbackItems.isEmpty(): ${playbackItems.isEmpty()}")
                Log.d("MixedMediaPlayer", "  isPaused: $isPaused")
                return@LaunchedEffect
            }
        
            // CRITICAL: If state is Transitioning or Idle, we MUST start playback
            // If state is PlayingVideo or PlayingImage, only start if index doesn't match
            val shouldStartPlayback = when (currentState) {
            is PlaybackState.Transitioning -> {
                // CRITICAL: Transitioning means we should start the next item
                // This is the key state after image/video completion
                // ALWAYS start playback when in Transitioning state
                Log.d("MixedMediaPlayer", "=== STATE IS TRANSITIONING ===")
                Log.d("MixedMediaPlayer", "MUST start playback at index $currentIndex")
                Log.d("MixedMediaPlayer", "This triggers after image/video completion")
                Log.d("MixedMediaPlayer", "Item at index: ${playbackItems.getOrNull(currentIndex)?.video?.id}")
                true
            }
            is PlaybackState.Idle -> {
                // Idle means ready to start
                Log.d("MixedMediaPlayer", "State is Idle - ready to start playback at index $currentIndex")
                true
            }
            is PlaybackState.PlayingVideo -> {
                // Check if we need to start a different item
                val needsChange = currentItemIndex != currentIndex
                if (needsChange) {
                    Log.d("MixedMediaPlayer", "State mismatch: PlayingVideo at index $currentItemIndex, but currentIndex is $currentIndex - will start new item")
                } else {
                    Log.d("MixedMediaPlayer", "Already playing video at correct index $currentIndex - skipping")
                }
                needsChange
            }
            is PlaybackState.PlayingImage -> {
                // Check if we need to start a different item
                val needsChange = currentItemIndex != currentIndex
                if (needsChange) {
                    Log.d("MixedMediaPlayer", "State mismatch: PlayingImage at index $currentItemIndex, but currentIndex is $currentIndex - will start new item")
                } else {
                    Log.d("MixedMediaPlayer", "Already playing image at correct index $currentIndex - skipping")
                }
                needsChange
            }
        }
        
        if (!shouldStartPlayback) {
            Log.d("MixedMediaPlayer", "LaunchedEffect early return - item already playing at correct index")
            return@LaunchedEffect
        }
        
        // CRITICAL: Check if item exists BEFORE starting playback
        val item = playbackItems.getOrNull(currentIndex)
        if (item == null) {
            Log.e("MixedMediaPlayer", "=== CRITICAL ERROR: Item at index $currentIndex not found ===")
            Log.e("MixedMediaPlayer", "PlaybackItems size: ${playbackItems.size}")
            Log.e("MixedMediaPlayer", "CurrentIndex: $currentIndex")
            Log.e("MixedMediaPlayer", "Available indices: 0..${playbackItems.size - 1}")
            // Skip to next item to prevent stuck state
            val skipIndex = if (playbackItems.isNotEmpty()) (currentIndex + 1) % playbackItems.size else 0
            withContext(Dispatchers.Main.immediate) {
                currentIndex = skipIndex
                stateTransitionKey++
                stateMachine.transitionToNext()
            }
            return@LaunchedEffect
        }
        
        Log.d("MixedMediaPlayer", "=== STARTING PLAYBACK ===")
        Log.d("MixedMediaPlayer", "Index: $currentIndex/${playbackItems.size - 1}")
        Log.d("MixedMediaPlayer", "Item is null: ${item == null}")
        
        if (item == null) {
            Log.e("MixedMediaPlayer", "CRITICAL: Item is null at index $currentIndex")
            return@LaunchedEffect
        }
        
        Log.d("MixedMediaPlayer", "Attempting to access item properties...")
        try {
            val mediaType = item.mediaType.name
            val videoId = item.video.id
            val sourceType = item.sourceType.name
            val isCached = item.isCached
            val playbackUri = item.playbackUri
            
            Log.d("MixedMediaPlayer", "Media Type: $mediaType")
            Log.d("MixedMediaPlayer", "Video ID: $videoId")
            Log.d("MixedMediaPlayer", "Source: $sourceType")
            Log.d("MixedMediaPlayer", "Is Cached: $isCached")
            Log.d("MixedMediaPlayer", "Playback URI: $playbackUri")
        } catch (e: Exception) {
            Log.e("MixedMediaPlayer", "=== ERROR accessing item properties ===")
            Log.e("MixedMediaPlayer", "Exception: ${e.javaClass.name}")
            Log.e("MixedMediaPlayer", "Message: ${e.message}")
            Log.e("MixedMediaPlayer", "Stack trace:")
            e.printStackTrace()
            // Skip this item if we can't access its properties
            val nextIndex = (currentIndex + 1) % playbackItems.size
            withContext(Dispatchers.Main.immediate) {
                currentIndex = nextIndex
                stateTransitionKey++
                stateMachine.transitionToNext()
            }
            return@LaunchedEffect
        }
        
        Log.d("MixedMediaPlayer", "Item properties accessed successfully")
        
        // Check file availability with error handling
        val isFileAvailable = try {
            val available = isMediaFileAvailable(item)
            Log.d("MixedMediaPlayer", "File availability check: $available")
            available
        } catch (e: Exception) {
            Log.e("MixedMediaPlayer", "Error checking file availability: ${e.message}", e)
            e.printStackTrace()
            false // Assume unavailable on error
        }
        
        if (!isFileAvailable) {
            Log.w("MixedMediaPlayer", "=== SKIPPING UNAVAILABLE MEDIA ===")
            Log.w("MixedMediaPlayer", "Index: $currentIndex | ID: ${item.video.id}")
            Log.w("MixedMediaPlayer", "Reason: File missing or not cached")
            
            // Log error
            scope.launch {
                repository.logUnsupportedFile(
                    screenId = screenId,
                    videoId = item.video.id,
                    playlistId = playlistId,
                    errorMessage = "File missing or not cached: ${item.playbackUri}"
                )
            }
            
            // Skip to next item
            val nextIndex = (currentIndex + 1) % playbackItems.size
            val nextItem = playbackItems.getOrNull(nextIndex)
            
            Log.d("MixedMediaPlayer", "=== SKIPPING UNAVAILABLE MEDIA ===")
            Log.d("MixedMediaPlayer", "Skipping index: $currentIndex")
            Log.d("MixedMediaPlayer", "Next index: $nextIndex")
            Log.d("MixedMediaPlayer", "Next item: ${nextItem?.video?.id} (type: ${nextItem?.mediaType?.name})")
            
            if (nextIndex == 0) {
                playlistLoopCount++
                scope.launch {
                    repository.logPlaylistCompleted(screenId)
                }
            }
            
            // Transition state and update index
            stateMachine.transitionToNext()
            currentIndex = nextIndex
            
            return@LaunchedEffect
        }
        
        // Start state machine with file availability check (with error handling)
        Log.d("MixedMediaPlayer", "Calling stateMachine.startPlayback() with isFileAvailable=$isFileAvailable")
        val state = try {
            val result = stateMachine.startPlayback(item, currentIndex, isFileAvailable = isFileAvailable)
            Log.d("MixedMediaPlayer", "stateMachine.startPlayback() returned: ${result?.javaClass?.simpleName}")
            result
        } catch (e: Exception) {
            Log.e("MixedMediaPlayer", "ERROR in stateMachine.startPlayback(): ${e.message}", e)
            e.printStackTrace()
            null
        }
        
        if (state == null) {
            // State machine rejected item (unsupported or unavailable)
            Log.w("MixedMediaPlayer", "=== STATE MACHINE REJECTED ITEM ===")
            Log.w("MixedMediaPlayer", "Rejected index: $currentIndex")
            Log.w("MixedMediaPlayer", "Rejected item: ${item.video.id} (type: ${item.mediaType.name})")
            
            val nextIndex = (currentIndex + 1) % playbackItems.size
            val nextItem = playbackItems.getOrNull(nextIndex)
            
            Log.w("MixedMediaPlayer", "Skipping to next index: $nextIndex")
            Log.w("MixedMediaPlayer", "Next item: ${nextItem?.video?.id} (type: ${nextItem?.mediaType?.name})")
            
            if (nextIndex == 0) {
                playlistLoopCount++
                scope.launch {
                    repository.logPlaylistCompleted(screenId)
                }
            }
            
            // Transition state and update index
            stateMachine.transitionToNext()
            currentIndex = nextIndex
            
            return@LaunchedEffect
        }
        
        // Log media source
        Log.d("MixedMediaPlayer", "Logging playback start...")
        try {
            MediaSourceLogger.logPlaybackStart(
                video = item.video,
                mediaType = item.mediaType,
                sourceType = item.sourceType,
                sourceUri = item.playbackUri
            )
            Log.d("MixedMediaPlayer", "Media source logged successfully")
        } catch (e: Exception) {
            Log.e("MixedMediaPlayer", "Error logging media source: ${e.message}", e)
        }
        
        // Start playback tracking
        Log.d("MixedMediaPlayer", "Starting playback tracking...")
        try {
            repository.startPlayback(item.video.id, playlistId)
            Log.d("MixedMediaPlayer", "Playback tracking started")
        } catch (e: Exception) {
            Log.e("MixedMediaPlayer", "Error starting playback tracking: ${e.message}", e)
        }
        
        // Handle based on media type
        Log.d("MixedMediaPlayer", "Getting current playback state...")
        val playbackState = try {
            stateMachine.getCurrentState()
        } catch (e: Exception) {
            Log.e("MixedMediaPlayer", "ERROR getting current state: ${e.message}", e)
            e.printStackTrace()
            return@LaunchedEffect
        }
        
        Log.d("MixedMediaPlayer", "Current playback state: ${playbackState.javaClass.simpleName}")
        when (playbackState) {
            is PlaybackState.PlayingVideo -> {
                // VIDEO: Use ExoPlayer (with error handling)
                try {
                    Log.d("MixedMediaPlayer", "Starting video playback via ExoPlayer")
                    
                    // CRITICAL: Stop ExoPlayer if it was playing something else
                    if (exoPlayer.isPlaying) {
                        exoPlayer.stop()
                    }
                    
                    // Calculate video index in ExoPlayer queue
                    val videoIndexInQueue = playbackItems
                        .take(currentIndex)
                        .count { it.mediaType == MediaType.VIDEO }
                    
                    Log.d("MixedMediaPlayer", "Video index in queue: $videoIndexInQueue (mediaItemCount: ${exoPlayer.mediaItemCount})")
                    
                    // Validate video index
                    if (videoIndexInQueue >= exoPlayer.mediaItemCount || videoIndexInQueue < 0) {
                        Log.e("MixedMediaPlayer", "Video index $videoIndexInQueue out of bounds (count: ${exoPlayer.mediaItemCount}) - skipping")
                        // Skip this video and move to next
                        val nextIdx = (currentIndex + 1) % playbackItems.size
                        stateMachine.transitionToNext()
                        currentIndex = nextIdx
                        stateTransitionKey++
                        return@LaunchedEffect
                    }
                    
                    // CRITICAL: Gate playback on surface readiness for hardware GPUs
                    // On physical devices, MediaCodec requires a valid surface before dequeueing buffers
                    // If we start playback before surface is ready, we get C2BqBuffer starvation
                    if (isSurfaceReady) {
                        // Surface is ready - safe to prepare and start playback
                        try {
                            if (exoPlayer.playbackState == Player.STATE_IDLE) {
                                exoPlayer.prepare()
                                Log.d("MixedMediaPlayer", "ExoPlayer prepared (surface ready)")
                            }
                            
                            exoPlayer.seekTo(videoIndexInQueue, 0)
                            exoPlayer.playWhenReady = true
                            Log.d("MixedMediaPlayer", "ExoPlayer seeked to index $videoIndexInQueue and started (surface ready)")
                        } catch (e: Exception) {
                            Log.e("MixedMediaPlayer", "Error starting ExoPlayer: ${e.message}", e)
                            // Try to skip to next item
                            val nextIdx = (currentIndex + 1) % playbackItems.size
                            stateMachine.transitionToNext()
                            currentIndex = nextIdx
                            stateTransitionKey++
                        }
                    } else {
                        // Surface not ready yet - store pending video index
                        // Playback will start automatically when surface becomes ready
                        pendingVideoIndex = videoIndexInQueue
                        Log.d("MixedMediaPlayer", "Surface not ready - queuing video index $videoIndexInQueue for playback")
                    }
                } catch (e: Exception) {
                    Log.e("MixedMediaPlayer", "CRITICAL: Error in video playback setup: ${e.message}", e)
                    // Try to skip to next item on error
                    try {
                        val nextIdx = (currentIndex + 1) % playbackItems.size
                        stateMachine.transitionToNext()
                        currentIndex = nextIdx
                        stateTransitionKey++
                    } catch (skipError: Exception) {
                        Log.e("MixedMediaPlayer", "Error skipping to next item: ${skipError.message}", skipError)
                    }
                }
            }
            
            is PlaybackState.PlayingImage -> {
                // IMAGE: Stop ExoPlayer completely (with error handling)
                try {
                    Log.d("MixedMediaPlayer", "Starting image display - stopping ExoPlayer")
                    
                    // CRITICAL: Stop and pause ExoPlayer when playing images
                    // Clear pending video index since we're switching to image
                    pendingVideoIndex = -1
                    
                    try {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.stop()
                        }
                        exoPlayer.pause()
                        exoPlayer.playWhenReady = false
                        // Clear video surface to free GPU resources
                        exoPlayer.clearVideoSurface()
                    } catch (e: Exception) {
                        Log.w("MixedMediaPlayer", "Error stopping ExoPlayer for image: ${e.message}", e)
                        // Continue - image display doesn't require ExoPlayer to be stopped
                    }
                    
                    Log.d("MixedMediaPlayer", "ExoPlayer stopped and surface cleared for image display")
                    
                    // Start image timer
                    // CRITICAL: Store the image index to verify state hasn't changed
                    val imageIndex = playbackState.index
                    scope.launch {
                        try {
                            delay(playbackState.displayDurationMs)
                            
                            // CRITICAL: Verify we're still playing the same image before completing
                            // State may have changed to video if playlist advanced during delay
                            val currentState = stateMachine.getCurrentState()
                            if (currentState is PlaybackState.PlayingImage && currentState.index == imageIndex) {
                                // Image timer completed and state is still correct
                                // Pass index for validation
                                val completedState = try {
                                    stateMachine.completeImage(expectedIndex = imageIndex)
                                } catch (e: Exception) {
                                    Log.e("MixedMediaPlayer", "Error completing image: ${e.message}", e)
                                    null
                                }
                                
                                if (completedState != null) {
                                    Log.d("MixedMediaPlayer", "=== IMAGE COMPLETION SUCCESSFUL ===")
                                    Log.d("MixedMediaPlayer", "Completed image index: ${completedState.index}")
                                    Log.d("MixedMediaPlayer", "Completed image ID: ${completedState.item.video.id}")
                                    
                                    try {
                                        val durationMs = System.currentTimeMillis() - completedState.startTime
                                        MediaSourceLogger.logPlaybackEnd(
                                            video = completedState.item.video,
                                            mediaType = completedState.item.mediaType,
                                            sourceType = completedState.item.sourceType,
                                            success = true,
                                            durationMs = durationMs
                                        )
                                        repository.endPlayback(screenId, success = true, isVideo = false)
                                    } catch (e: Exception) {
                                        Log.e("MixedMediaPlayer", "Error logging playback end: ${e.message}", e)
                                    }
                                    
                                    // Calculate next index and let LaunchedEffect handle starting the next item
                                    // CRITICAL: Just update state and index - LaunchedEffect will detect Transitioning and start playback
                                    try {
                                        val currentIdx = completedState.index
                                        val nextIndex = (currentIdx + 1) % playbackItems.size
                                        val nextItem = playbackItems.getOrNull(nextIndex)
                                        
                                        Log.d("MixedMediaPlayer", "=== ADVANCING PLAYLIST ===")
                                        Log.d("MixedMediaPlayer", "Current index: $currentIdx")
                                        Log.d("MixedMediaPlayer", "Next index: $nextIndex")
                                        Log.d("MixedMediaPlayer", "Next item: ${nextItem?.video?.id} (type: ${nextItem?.mediaType?.name})")
                                        
                                        if (nextIndex == 0) {
                                            playlistLoopCount++
                                            scope.launch {
                                                try {
                                                    repository.logPlaylistCompleted(screenId)
                                                } catch (e: Exception) {
                                                    Log.e("MixedMediaPlayer", "Error logging playlist completion: ${e.message}", e)
                                                }
                                            }
                                            Log.d("MixedMediaPlayer", "Playlist loop completed (count: $playlistLoopCount)")
                                        }
                                        
                                        // CRITICAL: Transition state FIRST, then update index
                                        // This ensures LaunchedEffect sees Transitioning state when it triggers
                                        stateMachine.transitionToNext()
                                        
                                        // Update index on main dispatcher to ensure state change is visible
                                        withContext(Dispatchers.Main.immediate) {
                                            currentIndex = nextIndex
                                            stateTransitionKey++ // Force LaunchedEffect refresh
                                            Log.d("MixedMediaPlayer", "Index updated to $nextIndex, stateTransitionKey=$stateTransitionKey")
                                            Log.d("MixedMediaPlayer", "State is now Transitioning - LaunchedEffect will start next item")
                                        }
                                        
                                        Log.d("MixedMediaPlayer", "=== PLAYLIST ADVANCED - LaunchedEffect should start next item ===")
                                    } catch (e: Exception) {
                                        Log.e("MixedMediaPlayer", "CRITICAL: Error advancing playlist: ${e.message}", e)
                                        e.printStackTrace()
                                    }
                                } else {
                                    Log.w("MixedMediaPlayer", "=== IMAGE COMPLETION FAILED ===")
                                    Log.w("MixedMediaPlayer", "completeImage() returned null - state may have changed")
                                    Log.w("MixedMediaPlayer", "Current state: ${stateMachine.getCurrentState()}")
                                    Log.w("MixedMediaPlayer", "Expected image index: $imageIndex")
                                    
                                    // Even if completion failed, try to advance to prevent stuck state
                                    try {
                                        val nextIndex = (imageIndex + 1) % playbackItems.size
                                        Log.w("MixedMediaPlayer", "Force advancing to index: $nextIndex (recovery)")
                                        stateMachine.transitionToNext()
                                        // Update immediately (synchronous update triggers recomposition)
                                        currentIndex = nextIndex
                                        stateTransitionKey++ // Force LaunchedEffect refresh
                                        Log.d("MixedMediaPlayer", "Recovery: Index updated to $nextIndex")
                                    } catch (e: Exception) {
                                        Log.e("MixedMediaPlayer", "Error in recovery advancement: ${e.message}", e)
                                    }
                                }
                            } else {
                                // State changed during delay - don't complete image
                                Log.d("MixedMediaPlayer", "Image timer expired but state changed (was image index $imageIndex, now: $currentState) - ignoring")
                            }
                        } catch (e: Exception) {
                            Log.e("MixedMediaPlayer", "CRITICAL: Error in image timer coroutine: ${e.message}", e)
                            // Try to advance on error
                            try {
                                val nextIndex = (imageIndex + 1) % playbackItems.size
                                stateMachine.transitionToNext()
                                // Update immediately (synchronous update triggers recomposition)
                                currentIndex = nextIndex
                                stateTransitionKey++
                                Log.d("MixedMediaPlayer", "Error recovery: Index updated to $nextIndex")
                            } catch (skipError: Exception) {
                                Log.e("MixedMediaPlayer", "Error skipping after image timer error: ${skipError.message}", skipError)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MixedMediaPlayer", "CRITICAL: Error in image playback setup: ${e.message}", e)
                    // Try to skip to next item on error
                    try {
                        val nextIdx = (currentIndex + 1) % playbackItems.size
                        stateMachine.transitionToNext()
                        currentIndex = nextIdx
                        stateTransitionKey++
                    } catch (skipError: Exception) {
                        Log.e("MixedMediaPlayer", "Error skipping after image setup error: ${skipError.message}", skipError)
                    }
                }
            }
            
            else -> {
                Log.w("MixedMediaPlayer", "Unexpected state: $playbackState")
            }
        }
        } catch (e: Exception) {
            Log.e("MixedMediaPlayer", "=== CRITICAL: Unhandled exception in LaunchedEffect ===")
            Log.e("MixedMediaPlayer", "Exception type: ${e.javaClass.name}")
            Log.e("MixedMediaPlayer", "Exception message: ${e.message}")
            Log.e("MixedMediaPlayer", "Current index: $currentIndex")
            Log.e("MixedMediaPlayer", "PlaybackItems size: ${playbackItems.size}")
            e.printStackTrace()
            // Try to recover by skipping to next item
            try {
                if (playbackItems.isNotEmpty()) {
                    val nextIdx = (currentIndex + 1) % playbackItems.size
                    stateMachine.transitionToNext()
                    currentIndex = nextIdx
                    stateTransitionKey++
                }
            } catch (recoveryError: Exception) {
                Log.e("MixedMediaPlayer", "CRITICAL: Recovery failed: ${recoveryError.message}", recoveryError)
            }
        }
    }
    
    /**
     * Move to next playlist item with comprehensive logging
     */
    fun moveToNextItem() {
        if (playbackItems.isEmpty()) {
            Log.w("MixedMediaPlayer", "moveToNextItem() called but playlist is empty")
            return
        }
        
        val currentItem = playbackItems.getOrNull(currentIndex)
        val currentState = stateMachine.getCurrentState()
        val currentStateItem = stateMachine.getCurrentItem()
        val currentStateIndex = stateMachine.getCurrentIndex()
        
        Log.d("MixedMediaPlayer", "=== MOVING TO NEXT ITEM ===")
        Log.d("MixedMediaPlayer", "Current index: $currentIndex/${playbackItems.size - 1}")
        Log.d("MixedMediaPlayer", "Current state: ${currentState::class.simpleName} | ${stateMachine.getStateInfo()}")
        Log.d("MixedMediaPlayer", "Current item: ${currentItem?.video?.id} (type: ${currentItem?.mediaType?.name})")
        Log.d("MixedMediaPlayer", "State machine item: ${currentStateItem?.video?.id} (index: $currentStateIndex)")
        
        // Transition state to allow next item to start
        stateMachine.transitionToNext()
        
        // Calculate next index
        val nextIndex = (currentIndex + 1) % playbackItems.size
        val nextItem = playbackItems.getOrNull(nextIndex)
        
        Log.d("MixedMediaPlayer", "Next index: $nextIndex/${playbackItems.size - 1}")
        Log.d("MixedMediaPlayer", "Next item: ${nextItem?.video?.id} (type: ${nextItem?.mediaType?.name})")
        
        if (nextIndex == 0) {
            playlistLoopCount++
            scope.launch {
                repository.logPlaylistCompleted(screenId)
            }
            Log.d("MixedMediaPlayer", "Playlist loop completed (count: $playlistLoopCount)")
        }
        
        // CRITICAL: Update currentIndex immediately (synchronous)
        // This MUST trigger recomposition for LaunchedEffect to run
        currentIndex = nextIndex
        stateTransitionKey++ // Force LaunchedEffect refresh
        Log.d("MixedMediaPlayer", "=== NEXT ITEM SET - Index=$nextIndex, Key=$stateTransitionKey ===")
    }
    
    // Handle Activity lifecycle (pause/resume/destroy)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    Log.d("MixedMediaPlayer", "=== LIFECYCLE: ON_PAUSE ===")
                    isPaused = true
                    stateMachine.pause()
                    
                    // Pause ExoPlayer
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                        exoPlayer.playWhenReady = false
                        Log.d("MixedMediaPlayer", "ExoPlayer paused due to lifecycle")
                    }
                    
                    Log.d("MixedMediaPlayer", "Current state: ${stateMachine.getStateInfo()}")
                }
                Lifecycle.Event.ON_RESUME -> {
                    Log.d("MixedMediaPlayer", "=== LIFECYCLE: ON_RESUME ===")
                    isPaused = false
                    
                    // Resume ExoPlayer if playing video
                    if (stateMachine.isPlayingVideo() && !exoPlayer.isPlaying) {
                        exoPlayer.playWhenReady = true
                        Log.d("MixedMediaPlayer", "ExoPlayer resumed due to lifecycle")
                    }
                    
                    Log.d("MixedMediaPlayer", "Current state: ${stateMachine.getStateInfo()}")
                }
                Lifecycle.Event.ON_DESTROY -> {
                    Log.d("MixedMediaPlayer", "=== LIFECYCLE: ON_DESTROY ===")
                    
                    // Stop and release ExoPlayer
                    exoPlayer.stop()
                    exoPlayer.release()
                    Log.d("MixedMediaPlayer", "ExoPlayer stopped and released")
                    
                    // Reset state machine
                    stateMachine.reset()
                    Log.d("MixedMediaPlayer", "State machine reset")
                }
                else -> {
                    // Other lifecycle events
                }
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            Log.d("MixedMediaPlayer", "=== LIFECYCLE: DisposableEffect disposed ===")
            lifecycleOwner.lifecycle.removeObserver(observer)
            
            // Cleanup on dispose
            if (exoPlayer.isPlaying) {
                exoPlayer.stop()
            }
            exoPlayer.release()
            stateMachine.reset()
        }
    }
    
    // Handle ExoPlayer events (ONLY for videos)
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                try {
                    when (playbackState) {
                        Player.STATE_ENDED -> {
                            // CRITICAL: Only handle if we're actually playing a video
                            if (stateMachine.isPlayingVideo()) {
                                scope.launch {
                                    try {
                                        // Get current index for validation
                                        val currentVideoIndex = stateMachine.getCurrentIndex()
                                        val completedState = try {
                                            stateMachine.completeVideo(expectedIndex = currentVideoIndex)
                                        } catch (e: Exception) {
                                            Log.e("MixedMediaPlayer", "Error completing video: ${e.message}", e)
                                            null
                                        }
                                        
                                        if (completedState != null) {
                                            val completedItem = completedState.item
                                            val durationMs = System.currentTimeMillis() - completedState.startTime
                                            
                                            Log.d("MixedMediaPlayer", "=== VIDEO COMPLETION SUCCESSFUL ===")
                                            Log.d("MixedMediaPlayer", "Completed video index: ${completedState.index}")
                                            Log.d("MixedMediaPlayer", "Completed video ID: ${completedItem.video.id}")
                                            Log.d("MixedMediaPlayer", "Duration: ${durationMs}ms")
                                            
                                            try {
                                                MediaSourceLogger.logPlaybackEnd(
                                                    video = completedItem.video,
                                                    mediaType = completedItem.mediaType,
                                                    sourceType = completedItem.sourceType,
                                                    success = true,
                                                    durationMs = durationMs
                                                )
                                                repository.endPlayback(screenId, success = true, isVideo = true)
                                            } catch (e: Exception) {
                                                Log.e("MixedMediaPlayer", "Error logging video playback end: ${e.message}", e)
                                            }
                                            
                                            // Use moveToNextItem which properly handles state transitions
                                            try {
                                                moveToNextItem()
                                            } catch (e: Exception) {
                                                Log.e("MixedMediaPlayer", "Error moving to next item: ${e.message}", e)
                                                // Fallback: manual advancement
                                                try {
                                                    val nextIdx = (completedState.index + 1) % playbackItems.size
                                                    stateMachine.transitionToNext()
                                                    withContext(Dispatchers.Main) {
                                                        currentIndex = nextIdx
                                                        stateTransitionKey++
                                                    }
                                                } catch (fallbackError: Exception) {
                                                    Log.e("MixedMediaPlayer", "Fallback advancement failed: ${fallbackError.message}", fallbackError)
                                                }
                                            }
                                        } else {
                                            Log.w("MixedMediaPlayer", "=== VIDEO COMPLETION FAILED ===")
                                            Log.w("MixedMediaPlayer", "completeVideo() returned null - state may have changed")
                                            Log.w("MixedMediaPlayer", "Current state: ${stateMachine.getCurrentState()}")
                                            Log.w("MixedMediaPlayer", "Expected video index: $currentVideoIndex")
                                            
                                            // Even if completion failed, try to advance to prevent stuck state
                                            if (currentVideoIndex != null && playbackItems.isNotEmpty()) {
                                                try {
                                                    val nextIndex = (currentVideoIndex + 1) % playbackItems.size
                                                    Log.w("MixedMediaPlayer", "Force advancing to index: $nextIndex (recovery)")
                                                    stateMachine.transitionToNext()
                                                    withContext(Dispatchers.Main) {
                                                        currentIndex = nextIndex
                                                        stateTransitionKey++ // Force LaunchedEffect refresh
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("MixedMediaPlayer", "Error in recovery advancement: ${e.message}", e)
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("MixedMediaPlayer", "CRITICAL: Error in video completion handler: ${e.message}", e)
                                        e.printStackTrace()
                                        // Try to advance on error
                                        try {
                                            val currentIdx = stateMachine.getCurrentIndex()
                                            if (currentIdx != null && playbackItems.isNotEmpty()) {
                                                val nextIdx = (currentIdx + 1) % playbackItems.size
                                                stateMachine.transitionToNext()
                                                withContext(Dispatchers.Main) {
                                                    currentIndex = nextIdx
                                                    stateTransitionKey++
                                                }
                                            }
                                        } catch (recoveryError: Exception) {
                                            Log.e("MixedMediaPlayer", "Recovery after video completion error failed: ${recoveryError.message}", recoveryError)
                                        }
                                    }
                                }
                            } else {
                                Log.w("MixedMediaPlayer", "ExoPlayer STATE_ENDED but not in PlayingVideo state - ignoring")
                            }
                        }
                        Player.STATE_READY -> {
                            if (stateMachine.isPlayingVideo()) {
                                Log.d("MixedMediaPlayer", "ExoPlayer STATE_READY - video ready to play")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MixedMediaPlayer", "CRITICAL: Error in ExoPlayer state change handler: ${e.message}", e)
                    e.printStackTrace()
                }
            }
            
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                scope.launch {
                    if (stateMachine.isPlayingVideo()) {
                        val item = stateMachine.getCurrentItem()
                        if (item != null) {
                            Log.e("MixedMediaPlayer", "ExoPlayer error: ${error.message}")
                            MediaSourceLogger.logPlaybackEnd(
                                video = item.video,
                                mediaType = item.mediaType,
                                sourceType = item.sourceType,
                                success = false
                            )
                            repository.endPlayback(screenId, success = false, errorMessage = error.message ?: "Playback error", isVideo = true)
                            moveToNextItem()
                        }
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            Log.d("MixedMediaPlayer", "Cleaning up - releasing ExoPlayer")
            // Maintain existing teardown logic: clear surface, stop, then release
            exoPlayer.clearVideoSurface()
            exoPlayer.stop()
            exoPlayer.release()
            playbackEngine.cleanup()
        }
    }
    
    // Render UI based on current state
    Box(modifier = Modifier.fillMaxSize()) {
        val currentItem = playbackItems.getOrNull(currentIndex)
        
        if (currentItem != null) {
            when {
                // VIDEO: Show video view, hide image view
                stateMachine.isPlayingVideo() -> {
                    Log.d("MixedMediaPlayer", "Rendering VIDEO view")
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                
                                // CRITICAL: Gate ExoPlayer playback on surface readiness
                                // SurfaceHolder.Callback detects when SurfaceView surface is created
                                // This prevents MediaCodec buffer starvation on hardware GPUs
                                val surfaceView = this.childCount.let { count ->
                                    (0 until count).mapNotNull { i ->
                                        this.getChildAt(i)
                                    }.firstOrNull { it is SurfaceView } as? SurfaceView
                                }
                                
                                surfaceView?.holder?.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) {
                                        // Surface is now created and valid - safe to start playback
                                        // This callback runs on the main thread, so we can safely trigger state updates
                                        Log.d("MixedMediaPlayer", "Surface created - ExoPlayer can now start playback")
                                        onSurfaceReady()
                                    }
                                    
                                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                        // Surface size changed - ExoPlayer will handle this automatically
                                        Log.d("MixedMediaPlayer", "Surface changed: ${width}x${height}")
                                    }
                                    
                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        // Surface destroyed - stop playback and clear surface
                                        Log.d("MixedMediaPlayer", "Surface destroyed - stopping playback")
                                        onSurfaceDestroyed()
                                    }
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            // Only start playback if surface is ready
                            // This prevents premature playback attempts
                            if (isSurfaceReady && !exoPlayer.isPlaying && exoPlayer.playWhenReady) {
                                exoPlayer.play()
                            }
                        }
                    )
                }
                
                // IMAGE: Show image view, hide video view (ExoPlayer stopped)
                stateMachine.isPlayingImage() -> {
                    Log.d("MixedMediaPlayer", "Rendering IMAGE view")
                    ImageDisplayView(
                        imageUrl = currentItem.playbackUri,
                        videoId = currentItem.video.id
                    )
                }
                
                // TRANSITIONING: Keep showing current item to prevent blank screen
                // CRITICAL: During transition, continue showing current item until next item starts
                else -> {
                    val currentStateForUI = stateMachine.getCurrentState()
                    Log.d("MixedMediaPlayer", "Rendering TRANSITIONING/IDLE state (${currentStateForUI::class.simpleName}) - showing current item to prevent blank screen")
                    // Show current item during transition to prevent blank screen
                    when (currentItem.mediaType) {
                        MediaType.IMAGE -> {
                            // Keep showing the image during transition
                            ImageDisplayView(
                                imageUrl = currentItem.playbackUri,
                                videoId = currentItem.video.id
                            )
                        }
                        MediaType.VIDEO -> {
                            // CRITICAL: Show video view even during transition to prevent blank screen
                            // ExoPlayer may already be starting playback, so show the PlayerView
                            // Use the same video view rendering logic as PlayingVideo state
                            Log.d("MixedMediaPlayer", "Transitioning to video - showing video view immediately to prevent blank screen")
                            // Reuse the same PlayerView rendering from PlayingVideo branch above
                            // This ensures consistent behavior and no duplicate views
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = exoPlayer
                                        useController = false
                                        val surfaceView = videoSurfaceView as? SurfaceView
                                        surfaceView?.holder?.addCallback(object : SurfaceHolder.Callback {
                                            override fun surfaceCreated(holder: SurfaceHolder) {
                                                Log.d("MixedMediaPlayer", "Surface created for transition video")
                                                if (!isSurfaceReady) {
                                                    isSurfaceReady = true
                                                    onSurfaceReady()
                                                }
                                            }
                                            
                                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                                Log.d("MixedMediaPlayer", "Surface changed for transition video: ${width}x${height}")
                                            }
                                            
                                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                                Log.d("MixedMediaPlayer", "Surface destroyed for transition video")
                                                onSurfaceDestroyed()
                                            }
                                        })
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = { view ->
                                    if (isSurfaceReady && !exoPlayer.isPlaying && exoPlayer.playWhenReady) {
                                        exoPlayer.play()
                                    }
                                }
                            )
                        }
                        else -> {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * ImageDisplayView - Dedicated image display component
 * 
 * CRITICAL: This is completely separate from ExoPlayer
 * Images never enter the video pipeline
 */
@Composable
private fun ImageDisplayView(
    imageUrl: String,
    videoId: String
) {
    val context = LocalContext.current
    
    Log.d("ImageDisplayView", "Displaying image: $videoId | URL: $imageUrl")
    
    Box(modifier = Modifier.fillMaxSize()) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = com.example.signoutwardv2.ui.theme.PurplePrimary.copy(alpha = 0.6f),
                        strokeWidth = 4.dp
                    )
                }
            },
            success = {
                Log.d("ImageDisplayView", "Image loaded successfully: $videoId")
                SubcomposeAsyncImageContent()
            },
            error = {
                Log.e("ImageDisplayView", "Failed to load image: $videoId | URL: $imageUrl")
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Failed to load image",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        )
    }
}

