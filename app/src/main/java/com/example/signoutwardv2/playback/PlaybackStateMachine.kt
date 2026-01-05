package com.example.signoutwardv2.playback

import android.util.Log
import com.example.signoutwardv2.data.MediaType
import com.example.signoutwardv2.playback.EnhancedPlaybackEngine.EnhancedPlaybackItem

/**
 * PlaybackStateMachine - Strict state machine for mixed media playback
 * 
 * Enforces:
 * - Exactly one completion event per playlist item
 * - Separate paths for video and image playback
 * - Video: ExoPlayer STATE_ENDED triggers advancement
 * - Image: Explicit timer completion triggers advancement
 * - No ExoPlayer interference with images
 * - Strict UI visibility enforcement
 */
sealed class PlaybackState {
    /**
     * IDLE: No media playing, ready to start
     */
    object Idle : PlaybackState()
    
    /**
     * PLAYING_VIDEO: Video is currently playing via ExoPlayer
     * - ExoPlayer is active and playing
     * - Video view is visible
     * - Image view is hidden
     * - Advances on Player.STATE_ENDED
     */
    data class PlayingVideo(
        val item: EnhancedPlaybackItem,
        val index: Int,
        val startTime: Long
    ) : PlaybackState()
    
    /**
     * PLAYING_IMAGE: Image is currently displaying
     * - ExoPlayer is STOPPED and released from view
     * - Image view is visible
     * - Video view is hidden
     * - Advances on timer completion
     */
    data class PlayingImage(
        val item: EnhancedPlaybackItem,
        val index: Int,
        val startTime: Long,
        val displayDurationMs: Long
    ) : PlaybackState()
    
    /**
     * TRANSITIONING: Brief state during transition between items
     * - Used to ensure clean state transitions
     */
    object Transitioning : PlaybackState()
}

/**
 * PlaybackStateMachine - Manages playback state transitions with enhanced error handling
 * 
 * ENHANCED FEATURES:
 * - Media type validation before state transitions
 * - Automatic skip for missing/uncached files
 * - Comprehensive state transition logging
 * - State verification before completion
 * - Lifecycle-aware state management
 */
class PlaybackStateMachine {
    companion object {
        private const val TAG = "PlaybackStateMachine"
    }
    
    private var currentState: PlaybackState = PlaybackState.Idle
    private var lastTransitionTime: Long = 0L
    
    /**
     * Get current state
     */
    fun getCurrentState(): PlaybackState = currentState
    
    /**
     * Check if state machine is in a valid state for operations
     */
    fun isValidState(): Boolean {
        return currentState !is PlaybackState.Transitioning
    }
    
    /**
     * Start playing a media item with validation
     * Returns the new state, or null if item should be skipped
     */
    fun startPlayback(
        item: EnhancedPlaybackItem, 
        index: Int,
        isFileAvailable: Boolean = true
    ): PlaybackState? {
        // Skip if file is not available
        if (!isFileAvailable) {
            Log.w(TAG, "=== STATE: SKIPPING UNAVAILABLE MEDIA ===")
            Log.w(TAG, "Index: $index | ID: ${item.video.id} | Type: ${item.mediaType.name}")
            Log.w(TAG, "Reason: File missing or not cached")
            return null
        }
        
        // Validate media type
        if (item.mediaType == MediaType.UNSUPPORTED) {
            Log.w(TAG, "=== STATE: SKIPPING UNSUPPORTED MEDIA ===")
            Log.w(TAG, "Index: $index | ID: ${item.video.id}")
            return null
        }
        
        // Transition to Transitioning state first
        if (currentState !is PlaybackState.Transitioning && currentState !is PlaybackState.Idle) {
            Log.d(TAG, "=== STATE: TRANSITIONING FROM ${currentState::class.simpleName} ===")
            currentState = PlaybackState.Transitioning
        }
        
        val newState = when (item.mediaType) {
            MediaType.VIDEO -> {
                Log.d(TAG, "=== STATE TRANSITION: IDLE/TRANSITIONING → PLAYING_VIDEO ===")
                Log.d(TAG, "Index: $index | ID: ${item.video.id} | Type: ${item.mediaType.name}")
                Log.d(TAG, "Source: ${item.sourceType.name} | URI: ${item.playbackUri}")
                Log.d(TAG, "Previous State: ${currentState::class.simpleName}")
                PlaybackState.PlayingVideo(
                    item = item,
                    index = index,
                    startTime = System.currentTimeMillis()
                )
            }
            MediaType.IMAGE -> {
                val displayDuration = (item.video.displayDurationSeconds ?: 7) * 1000L
                Log.d(TAG, "=== STATE TRANSITION: IDLE/TRANSITIONING → PLAYING_IMAGE ===")
                Log.d(TAG, "Index: $index | ID: ${item.video.id} | Type: ${item.mediaType.name}")
                Log.d(TAG, "Source: ${item.sourceType.name} | URI: ${item.playbackUri}")
                Log.d(TAG, "Display Duration: ${displayDuration}ms")
                Log.d(TAG, "Previous State: ${currentState::class.simpleName}")
                PlaybackState.PlayingImage(
                    item = item,
                    index = index,
                    startTime = System.currentTimeMillis(),
                    displayDurationMs = displayDuration
                )
            }
            MediaType.UNSUPPORTED -> {
                Log.w(TAG, "Attempted to start unsupported media at index $index")
                return null
            }
        }
        
        val previousState = currentState
        currentState = newState
        lastTransitionTime = System.currentTimeMillis()
        
        Log.d(TAG, "=== STATE TRANSITION COMPLETE ===")
        Log.d(TAG, "From: ${previousState::class.simpleName} → To: ${newState::class.simpleName}")
        Log.d(TAG, "Transition Time: ${System.currentTimeMillis() - lastTransitionTime}ms")
        
        return newState
    }
    
    /**
     * Mark video as completed (ExoPlayer STATE_ENDED)
     * CRITICAL: Validates state and media type before completing
     * Returns the completed PlayingVideo state, or null if validation fails
     */
    fun completeVideo(expectedIndex: Int? = null): PlaybackState.PlayingVideo? {
        return when (val state = currentState) {
            is PlaybackState.PlayingVideo -> {
                // Validate index if provided
                if (expectedIndex != null && state.index != expectedIndex) {
                    Log.w(TAG, "=== STATE MISMATCH: Video completion index mismatch ===")
                    Log.w(TAG, "Expected index: $expectedIndex | Actual index: ${state.index}")
                    Log.w(TAG, "State: $state")
                    return null
                }
                
                // Validate media type
                if (state.item.mediaType != MediaType.VIDEO) {
                    Log.e(TAG, "=== CRITICAL: completeVideo() called for non-video media ===")
                    Log.e(TAG, "Media Type: ${state.item.mediaType.name} | Index: ${state.index}")
                    return null
                }
                
                val duration = System.currentTimeMillis() - state.startTime
                Log.d(TAG, "=== STATE TRANSITION: PLAYING_VIDEO → TRANSITIONING ===")
                Log.d(TAG, "Index: ${state.index} | ID: ${state.item.video.id}")
                Log.d(TAG, "Duration: ${duration}ms")
                Log.d(TAG, "Playback completed successfully")
                
                currentState = PlaybackState.Transitioning
                lastTransitionTime = System.currentTimeMillis()
                state
            }
            else -> {
                Log.w(TAG, "=== STATE MISMATCH: completeVideo() called in wrong state ===")
                Log.w(TAG, "Current State: ${state::class.simpleName}")
                Log.w(TAG, "Expected State: PlayingVideo")
                Log.w(TAG, "This usually means video ended event fired while playing image")
                null
            }
        }
    }
    
    /**
     * Mark image as completed (timer expired)
     * CRITICAL: Validates state and media type before completing
     * Returns the completed PlayingImage state, or null if validation fails
     */
    fun completeImage(expectedIndex: Int? = null): PlaybackState.PlayingImage? {
        return when (val state = currentState) {
            is PlaybackState.PlayingImage -> {
                // Validate index if provided
                if (expectedIndex != null && state.index != expectedIndex) {
                    Log.w(TAG, "=== STATE MISMATCH: Image completion index mismatch ===")
                    Log.w(TAG, "Expected index: $expectedIndex | Actual index: ${state.index}")
                    Log.w(TAG, "State: $state")
                    return null
                }
                
                // Validate media type
                if (state.item.mediaType != MediaType.IMAGE) {
                    Log.e(TAG, "=== CRITICAL: completeImage() called for non-image media ===")
                    Log.e(TAG, "Media Type: ${state.item.mediaType.name} | Index: ${state.index}")
                    return null
                }
                
                val duration = System.currentTimeMillis() - state.startTime
                Log.d(TAG, "=== STATE TRANSITION: PLAYING_IMAGE → TRANSITIONING ===")
                Log.d(TAG, "Index: ${state.index} | ID: ${state.item.video.id}")
                Log.d(TAG, "Duration: ${duration}ms (expected: ${state.displayDurationMs}ms)")
                Log.d(TAG, "Playback completed successfully")
                
                currentState = PlaybackState.Transitioning
                lastTransitionTime = System.currentTimeMillis()
                state
            }
            else -> {
                Log.w(TAG, "=== STATE MISMATCH: completeImage() called in wrong state ===")
                Log.w(TAG, "Current State: ${state::class.simpleName}")
                Log.w(TAG, "Expected State: PlayingImage")
                Log.w(TAG, "This usually means image timer expired while playing video")
                null
            }
        }
    }
    
    /**
     * Skip current item (file missing or error)
     * Safely transitions to Transitioning state
     */
    fun skipCurrentItem(reason: String = "File unavailable") {
        val previousState = currentState
        Log.w(TAG, "=== STATE: SKIPPING CURRENT ITEM ===")
        Log.w(TAG, "Previous State: ${previousState::class.simpleName}")
        Log.w(TAG, "Reason: $reason")
        
        when (val state = currentState) {
            is PlaybackState.PlayingVideo -> {
                Log.w(TAG, "Skipping video at index: ${state.index} | ID: ${state.item.video.id}")
            }
            is PlaybackState.PlayingImage -> {
                Log.w(TAG, "Skipping image at index: ${state.index} | ID: ${state.item.video.id}")
            }
            else -> {
                Log.w(TAG, "Skipping from state: ${state::class.simpleName}")
            }
        }
        
        currentState = PlaybackState.Transitioning
        lastTransitionTime = System.currentTimeMillis()
    }
    
    /**
     * Transition to next item
     */
    fun transitionToNext() {
        val previousState = currentState
        Log.d(TAG, "=== STATE TRANSITION: ${previousState::class.simpleName} → TRANSITIONING ===")
        currentState = PlaybackState.Transitioning
        lastTransitionTime = System.currentTimeMillis()
    }
    
    /**
     * Check if currently playing video
     */
    fun isPlayingVideo(): Boolean {
        return currentState is PlaybackState.PlayingVideo
    }
    
    /**
     * Check if currently playing image
     */
    fun isPlayingImage(): Boolean {
        return currentState is PlaybackState.PlayingImage
    }
    
    /**
     * Check if in transitioning state
     */
    fun isTransitioning(): Boolean {
        return currentState is PlaybackState.Transitioning
    }
    
    /**
     * Get current item (if playing)
     */
    fun getCurrentItem(): EnhancedPlaybackItem? {
        return when (val state = currentState) {
            is PlaybackState.PlayingVideo -> state.item
            is PlaybackState.PlayingImage -> state.item
            else -> null
        }
    }
    
    /**
     * Get current index (if playing)
     */
    fun getCurrentIndex(): Int? {
        return when (val state = currentState) {
            is PlaybackState.PlayingVideo -> state.index
            is PlaybackState.PlayingImage -> state.index
            else -> null
        }
    }
    
    /**
     * Get current media type (if playing)
     */
    fun getCurrentMediaType(): MediaType? {
        return when (val state = currentState) {
            is PlaybackState.PlayingVideo -> state.item.mediaType
            is PlaybackState.PlayingImage -> state.item.mediaType
            else -> null
        }
    }
    
    /**
     * Reset to idle (for lifecycle events like pause/destroy)
     */
    fun reset() {
        val previousState = currentState
        Log.d(TAG, "=== STATE TRANSITION: ${previousState::class.simpleName} → IDLE ===")
        Log.d(TAG, "Reason: State machine reset (lifecycle event)")
        currentState = PlaybackState.Idle
        lastTransitionTime = System.currentTimeMillis()
    }
    
    /**
     * Pause playback (for Activity onPause)
     */
    fun pause() {
        when (val state = currentState) {
            is PlaybackState.PlayingVideo -> {
                Log.d(TAG, "=== STATE: PAUSING VIDEO ===")
                Log.d(TAG, "Index: ${state.index} | ID: ${state.item.video.id}")
            }
            is PlaybackState.PlayingImage -> {
                Log.d(TAG, "=== STATE: PAUSING IMAGE ===")
                Log.d(TAG, "Index: ${state.index} | ID: ${state.item.video.id}")
            }
            else -> {
                Log.d(TAG, "=== STATE: PAUSE (no active playback) ===")
            }
        }
        // Don't change state - will resume from same state
    }
    
    /**
     * Get state transition log (for debugging)
     */
    fun getStateInfo(): String {
        return when (val state = currentState) {
            is PlaybackState.PlayingVideo -> {
                "PlayingVideo(index=${state.index}, id=${state.item.video.id}, duration=${System.currentTimeMillis() - state.startTime}ms)"
            }
            is PlaybackState.PlayingImage -> {
                "PlayingImage(index=${state.index}, id=${state.item.video.id}, duration=${System.currentTimeMillis() - state.startTime}ms/${state.displayDurationMs}ms)"
            }
            is PlaybackState.Transitioning -> "Transitioning"
            is PlaybackState.Idle -> "Idle"
        }
    }
}

