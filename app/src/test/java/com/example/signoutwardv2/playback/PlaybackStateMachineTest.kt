package com.example.signoutwardv2.playback

import com.example.signoutwardv2.data.MediaType
import com.example.signoutwardv2.data.models.Video
import com.example.signoutwardv2.playback.EnhancedPlaybackEngine.EnhancedPlaybackItem
import com.example.signoutwardv2.playback.MediaSourceLogger.SourceType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PlaybackStateMachine
 * 
 * Tests:
 * - State transitions for video and image playback
 * - Completion events for video and image
 * - State queries (isPlayingVideo, isPlayingImage)
 * - Current item and index retrieval
 */
class PlaybackStateMachineTest {
    
    private lateinit var stateMachine: PlaybackStateMachine
    
    private val testVideo = Video(
        id = "test-video-1",
        url = "https://example.com/video.mp4",
        mimeType = "video/mp4",
        displayDurationSeconds = null
    )
    
    private val testImage = Video(
        id = "test-image-1",
        url = "https://example.com/image.jpg",
        mimeType = "image/jpeg",
        displayDurationSeconds = 5
    )
    
    private fun createVideoItem(index: Int): EnhancedPlaybackItem {
        return EnhancedPlaybackItem(
            video = testVideo,
            mediaType = MediaType.VIDEO,
            playbackUri = testVideo.url,
            sourceType = SourceType.REMOTE,
            index = index,
            isCached = false
        )
    }
    
    private fun createImageItem(index: Int): EnhancedPlaybackItem {
        return EnhancedPlaybackItem(
            video = testImage,
            mediaType = MediaType.IMAGE,
            playbackUri = testImage.url,
            sourceType = SourceType.REMOTE,
            index = index,
            isCached = false
        )
    }
    
    @Before
    fun setup() {
        stateMachine = PlaybackStateMachine()
    }
    
    @Test
    fun initialStateIsIdle() {
        val state = stateMachine.getCurrentState()
        assertTrue("Initial state should be Idle", state is PlaybackState.Idle)
    }
    
    @Test
    fun startVideoPlaybackTransitionsToPlayingVideo() {
        val item = createVideoItem(0)
        val state = stateMachine.startPlayback(item, 0)
        
        assertTrue("State should be PlayingVideo", state is PlaybackState.PlayingVideo)
        assertEquals("Index should be 0", 0, (state as PlaybackState.PlayingVideo).index)
        assertEquals("Item should match", item, state.item)
        assertTrue("Should be playing video", stateMachine.isPlayingVideo())
        assertFalse("Should not be playing image", stateMachine.isPlayingImage())
    }
    
    @Test
    fun startImagePlaybackTransitionsToPlayingImage() {
        val item = createImageItem(0)
        val state = stateMachine.startPlayback(item, 0)
        
        assertTrue("State should be PlayingImage", state is PlaybackState.PlayingImage)
        assertEquals("Index should be 0", 0, (state as PlaybackState.PlayingImage).index)
        assertEquals("Item should match", item, state.item)
        assertEquals("Display duration should be 5000ms", 5000L, state.displayDurationMs)
        assertFalse("Should not be playing video", stateMachine.isPlayingVideo())
        assertTrue("Should be playing image", stateMachine.isPlayingImage())
    }
    
    @Test
    fun completeVideoReturnsPlayingVideoState() {
        val item = createVideoItem(0)
        stateMachine.startPlayback(item, 0)
        
        val completedState = stateMachine.completeVideo()
        
        assertNotNull("Completed state should not be null", completedState)
        assertEquals("Completed item should match", item, completedState!!.item)
        assertEquals("Completed index should be 0", 0, completedState.index)
        
        // State should transition to Transitioning
        val currentState = stateMachine.getCurrentState()
        assertTrue("State should be Transitioning", currentState is PlaybackState.Transitioning)
    }
    
    @Test
    fun completeImageReturnsPlayingImageState() {
        val item = createImageItem(0)
        stateMachine.startPlayback(item, 0)
        
        val completedState = stateMachine.completeImage()
        
        assertNotNull("Completed state should not be null", completedState)
        assertEquals("Completed item should match", item, completedState!!.item)
        assertEquals("Completed index should be 0", 0, completedState.index)
        assertEquals("Display duration should be 5000ms", 5000L, completedState.displayDurationMs)
        
        // State should transition to Transitioning
        val currentState = stateMachine.getCurrentState()
        assertTrue("State should be Transitioning", currentState is PlaybackState.Transitioning)
    }
    
    @Test
    fun completeVideoWhenNotPlayingVideoReturnsNull() {
        val item = createImageItem(0)
        stateMachine.startPlayback(item, 0)
        
        val completedState = stateMachine.completeVideo()
        
        assertNull("Should return null when not playing video", completedState)
    }
    
    @Test
    fun completeImageWhenNotPlayingImageReturnsNull() {
        val item = createVideoItem(0)
        stateMachine.startPlayback(item, 0)
        
        val completedState = stateMachine.completeImage()
        
        assertNull("Should return null when not playing image", completedState)
    }
    
    @Test
    fun getCurrentItemReturnsItemWhenPlaying() {
        val videoItem = createVideoItem(0)
        stateMachine.startPlayback(videoItem, 0)
        
        val currentItem = stateMachine.getCurrentItem()
        assertNotNull("Current item should not be null", currentItem)
        assertEquals("Current item should match", videoItem, currentItem)
        
        val imageItem = createImageItem(1)
        stateMachine.startPlayback(imageItem, 1)
        
        val currentItem2 = stateMachine.getCurrentItem()
        assertNotNull("Current item should not be null", currentItem2)
        assertEquals("Current item should match", imageItem, currentItem2)
    }
    
    @Test
    fun getCurrentItemReturnsNullWhenIdle() {
        val currentItem = stateMachine.getCurrentItem()
        assertNull("Current item should be null when idle", currentItem)
    }
    
    @Test
    fun getCurrentIndexReturnsIndexWhenPlaying() {
        val item = createVideoItem(5)
        stateMachine.startPlayback(item, 5)
        
        val currentIndex = stateMachine.getCurrentIndex()
        assertEquals("Current index should be 5", 5, currentIndex)
    }
    
    @Test
    fun getCurrentIndexReturnsNullWhenIdle() {
        val currentIndex = stateMachine.getCurrentIndex()
        assertNull("Current index should be null when idle", currentIndex)
    }
    
    @Test
    fun resetReturnsToIdle() {
        val item = createVideoItem(0)
        stateMachine.startPlayback(item, 0)
        
        stateMachine.reset()
        
        val state = stateMachine.getCurrentState()
        assertTrue("State should be Idle after reset", state is PlaybackState.Idle)
        assertFalse("Should not be playing video", stateMachine.isPlayingVideo())
        assertFalse("Should not be playing image", stateMachine.isPlayingImage())
    }
    
    @Test
    fun imageDisplayDurationDefaultsTo7Seconds() {
        val imageWithoutDuration = Video(
            id = "test-image-2",
            url = "https://example.com/image2.jpg",
            mimeType = "image/jpeg",
            displayDurationSeconds = null
        )
        
        val item = EnhancedPlaybackItem(
            video = imageWithoutDuration,
            mediaType = MediaType.IMAGE,
            playbackUri = imageWithoutDuration.url,
            sourceType = SourceType.REMOTE,
            index = 0,
            isCached = false
        )
        
        val state = stateMachine.startPlayback(item, 0)
        
        assertTrue("State should be PlayingImage", state is PlaybackState.PlayingImage)
        assertEquals("Display duration should default to 7000ms", 7000L, (state as PlaybackState.PlayingImage).displayDurationMs)
    }
}

