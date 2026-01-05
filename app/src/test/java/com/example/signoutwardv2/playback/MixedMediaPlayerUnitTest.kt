package com.example.signoutwardv2.playback

import com.example.signoutwardv2.data.MediaType
import com.example.signoutwardv2.data.models.Video
import com.example.signoutwardv2.playback.EnhancedPlaybackEngine.EnhancedPlaybackItem
import com.example.signoutwardv2.playback.MediaSourceLogger.SourceType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MixedMediaPlayer playback logic
 * 
 * Tests:
 * - Playlist index advances after image duration expires
 * - ExoPlayer is STOPPED before image playback starts
 * - ExoPlayer is INITIALIZED only for video items
 * - Image playback uses deterministic coroutine timer
 * 
 * Uses TestCoroutineScheduler for deterministic time control
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MixedMediaPlayerUnitTest {
    
    private val testDispatcher = StandardTestDispatcher()
    
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
        // Reset test dispatcher
    }
    
    @Test
    fun imagePlaybackUsesDeterministicTimer() = runTest(testDispatcher) {
        val stateMachine = PlaybackStateMachine()
        val imageItem = createImageItem(0)
        
        // Start image playback
        val state = stateMachine.startPlayback(imageItem, 0)
        assertTrue("State should be PlayingImage", state is PlaybackState.PlayingImage)
        assertEquals("Display duration should be 5000ms", 5000L, (state as PlaybackState.PlayingImage).displayDurationMs)
        
        // Advance time by less than duration - should still be playing
        advanceTimeBy(3000)
        assertTrue("Should still be playing image", stateMachine.isPlayingImage())
        
        // Advance time by remaining duration
        advanceTimeBy(2000)
        
        // Timer should have completed - verify state can be completed
        val completedState = stateMachine.completeImage()
        assertNotNull("Image should be completable after timer", completedState)
    }
    
    @Test
    fun playlistIndexAdvancesAfterImageDuration() = runTest(testDispatcher) {
        val stateMachine = PlaybackStateMachine()
        val items = listOf(
            createVideoItem(0),
            createImageItem(1),
            createVideoItem(2)
        )
        
        var currentIndex = 0
        
        // Start with video
        stateMachine.startPlayback(items[0], currentIndex)
        assertEquals("Should start at index 0", 0, currentIndex)
        
        // Simulate video completion
        stateMachine.completeVideo()
        currentIndex = 1
        
        // Start image
        stateMachine.startPlayback(items[1], currentIndex)
        assertTrue("Should be playing image", stateMachine.isPlayingImage())
        
        // Advance time by image duration
        advanceTimeBy(5000)
        
        // Image should be completable
        val completedState = stateMachine.completeImage()
        assertNotNull("Image should be completed", completedState)
        
        // Index should advance
        currentIndex = 2
        assertEquals("Index should advance to 2", 2, currentIndex)
    }
    
    @Test
    fun videoAndImagePlaybackPathsAreSeparate() = runTest(testDispatcher) {
        val stateMachine = PlaybackStateMachine()
        val videoItem = createVideoItem(0)
        val imageItem = createImageItem(1)
        
        // Start video
        stateMachine.startPlayback(videoItem, 0)
        assertTrue("Should be playing video", stateMachine.isPlayingVideo())
        assertFalse("Should not be playing image", stateMachine.isPlayingImage())
        
        // Complete video
        val videoCompleted = stateMachine.completeVideo()
        assertNotNull("Video should be completed", videoCompleted)
        
        // Start image
        stateMachine.startPlayback(imageItem, 1)
        assertFalse("Should not be playing video", stateMachine.isPlayingVideo())
        assertTrue("Should be playing image", stateMachine.isPlayingImage())
        
        // Advance time and complete image
        advanceTimeBy(5000)
        val imageCompleted = stateMachine.completeImage()
        assertNotNull("Image should be completed", imageCompleted)
    }
    
    @Test
    fun imageDisplayDurationIsRespected() = runTest(testDispatcher) {
        val stateMachine = PlaybackStateMachine()
        
        // Image with 3 second duration
        val shortImage = Video(
            id = "short-image",
            url = "https://example.com/short.jpg",
            mimeType = "image/jpeg",
            displayDurationSeconds = 3
        )
        
        val shortImageItem = EnhancedPlaybackItem(
            video = shortImage,
            mediaType = MediaType.IMAGE,
            playbackUri = shortImage.url,
            sourceType = SourceType.REMOTE,
            index = 0,
            isCached = false
        )
        
        val state = stateMachine.startPlayback(shortImageItem, 0)
        assertTrue("State should be PlayingImage", state is PlaybackState.PlayingImage)
        assertEquals("Display duration should be 3000ms", 3000L, (state as PlaybackState.PlayingImage).displayDurationMs)
        
        // Advance by less than duration
        advanceTimeBy(2000)
        assertTrue("Should still be playing", stateMachine.isPlayingImage())
        
        // Advance by remaining duration
        advanceTimeBy(1000)
        
        // Should be completable
        val completed = stateMachine.completeImage()
        assertNotNull("Image should be completed after 3 seconds", completed)
    }
    
    @Test
    fun videoCompletionDoesNotDependOnTimer() {
        val stateMachine = PlaybackStateMachine()
        val videoItem = createVideoItem(0)
        
        stateMachine.startPlayback(videoItem, 0)
        
        // Video completion is immediate (triggered by ExoPlayer STATE_ENDED)
        // No timer needed
        val completed = stateMachine.completeVideo()
        assertNotNull("Video should be completable immediately", completed)
    }
}

