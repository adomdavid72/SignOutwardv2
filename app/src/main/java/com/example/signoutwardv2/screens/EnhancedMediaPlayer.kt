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
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.signoutwardv2.cache.LocalCacheManager
import com.example.signoutwardv2.data.MediaType
import com.example.signoutwardv2.data.PlaybackRepository
import com.example.signoutwardv2.data.models.Video
import com.example.signoutwardv2.playback.EnhancedPlaybackEngine
import com.example.signoutwardv2.playback.MediaSourceLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * EnhancedMediaPlayer - Optimized playback with seamless transitions and ad support
 * 
 * Features:
 * - Preloads next items to reduce lag
 * - Seamless transitions between media items
 * - Comprehensive media source logging
 * - Ad playback support (ads are regular media items)
 * - Maintains exact playlist order
 */
@Composable
fun EnhancedMediaPlayer(
    playbackItems: List<EnhancedPlaybackEngine.EnhancedPlaybackItem>,
    unsupportedVideos: List<Video>,
    screenId: String,
    repository: PlaybackRepository,
    playlistId: String,
    playbackEngine: EnhancedPlaybackEngine
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Persistent ExoPlayer instance for entire playlist
    val exoPlayer = remember {
        playbackEngine.createOptimizedPlayer()
    }
    
    // Preload video MediaItems into ExoPlayer queue
    var allMediaItemsPrepared by remember { mutableStateOf(false) }
    
    LaunchedEffect(playbackItems) {
        val videoMediaItems = playbackEngine.getVideoMediaItems(playbackItems)
        
        if (videoMediaItems.isNotEmpty()) {
            exoPlayer.setMediaItems(videoMediaItems)
            exoPlayer.prepare()
            Log.d("EnhancedMediaPlayer", "Preloaded ${videoMediaItems.size} video MediaItems")
        }
        
        allMediaItemsPrepared = true
        
        // Preload next items for smooth transitions
        scope.launch {
            playbackEngine.preloadNextItems(playbackItems, 0, exoPlayer)
        }
    }
    
    // Current playback state
    var currentIndex by remember { mutableStateOf(0) }
    var isPlayingVideo by remember { mutableStateOf(false) }
    var imageDisplayStartTime by remember { mutableStateOf<Long?>(null) }
    var playlistLoopCount by remember { mutableStateOf(0) }
    var playbackStartTime by remember { mutableStateOf<Long?>(null) }
    
    val currentItem = remember(currentIndex, playbackItems) {
        playbackItems.getOrNull(currentIndex)
    }
    
    var currentVideoIndexInQueue by remember { mutableStateOf(0) }
    
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
    
    // Start playback tracking when media changes
    LaunchedEffect(currentItem, allMediaItemsPrepared) {
        if (!allMediaItemsPrepared) return@LaunchedEffect
        
        currentItem?.let { item ->
            val transitionStartTime = System.currentTimeMillis()
            
            // Log playback start with source information
            MediaSourceLogger.logPlaybackStart(
                video = item.video,
                mediaType = item.mediaType,
                sourceType = item.sourceType,
                sourceUri = item.playbackUri
            )
            
            // Log transition if not first item
            if (currentIndex > 0) {
                val prevItem = playbackItems.getOrNull(currentIndex - 1)
                MediaSourceLogger.logTransition(
                    fromVideo = prevItem?.video,
                    toVideo = item.video,
                    fromSourceType = prevItem?.sourceType,
                    toSourceType = item.sourceType,
                    transitionDelayMs = null // Will be calculated after transition
                )
            }
            
            playbackStartTime = System.currentTimeMillis()
            repository.startPlayback(item.video.id, playlistId)
            
            if (item.mediaType == MediaType.VIDEO) {
                val videoIndexInQueue = playbackItems
                    .take(currentIndex)
                    .count { it.mediaType == MediaType.VIDEO }
                
                if (exoPlayer.currentMediaItemIndex != videoIndexInQueue && videoIndexInQueue < exoPlayer.mediaItemCount) {
                    exoPlayer.seekTo(videoIndexInQueue, 0)
                    currentVideoIndexInQueue = videoIndexInQueue
                }
                isPlayingVideo = true
                imageDisplayStartTime = null
            } else if (item.mediaType == MediaType.IMAGE) {
                isPlayingVideo = false
                imageDisplayStartTime = System.currentTimeMillis()
            }
            
            // Preload next items for smooth transitions
            scope.launch {
                playbackEngine.preloadNextItems(playbackItems, currentIndex, exoPlayer)
            }
        }
    }
    
    fun moveToNextItem() {
        if (playbackItems.isEmpty()) return
        
        val currentItem = playbackItems.getOrNull(currentIndex)
        val transitionStartTime = System.currentTimeMillis()
        
        // Log playback end
        currentItem?.let { item ->
            val durationMs = playbackStartTime?.let { System.currentTimeMillis() - it }
            MediaSourceLogger.logPlaybackEnd(
                video = item.video,
                mediaType = item.mediaType,
                sourceType = item.sourceType,
                success = true,
                durationMs = durationMs
            )
        }
        
        val nextIndex = (currentIndex + 1) % playbackItems.size
        
        if (nextIndex == 0) {
            playlistLoopCount++
            scope.launch {
                repository.logPlaylistCompleted(screenId)
            }
        }
        
        currentIndex = nextIndex
        
        // Log transition delay
        val transitionDelay = System.currentTimeMillis() - transitionStartTime
        if (transitionDelay > 100) {
            Log.w("EnhancedMediaPlayer", "Transition delay: ${transitionDelay}ms")
        }
    }
    
    // Handle ExoPlayer events
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> {
                        scope.launch {
                            currentItem?.let { item ->
                                if (item.mediaType == MediaType.VIDEO) {
                                    val durationMs = playbackStartTime?.let { System.currentTimeMillis() - it }
                                    MediaSourceLogger.logPlaybackEnd(
                                        video = item.video,
                                        mediaType = item.mediaType,
                                        sourceType = item.sourceType,
                                        success = true,
                                        durationMs = durationMs
                                    )
                                    repository.endPlayback(screenId, success = true, isVideo = true)
                                }
                            }
                            moveToNextItem()
                        }
                    }
                    Player.STATE_READY -> {
                        isPlayingVideo = true
                    }
                }
            }
            
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                scope.launch {
                    currentItem?.let { item ->
                        if (item.mediaType == MediaType.VIDEO) {
                            MediaSourceLogger.logPlaybackEnd(
                                video = item.video,
                                mediaType = item.mediaType,
                                sourceType = item.sourceType,
                                success = false
                            )
                            repository.endPlayback(screenId, success = false, errorMessage = error.message ?: "Playback error", isVideo = true)
                        }
                    }
                    moveToNextItem()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }
    
    // Handle image display timer
    LaunchedEffect(imageDisplayStartTime, currentItem) {
        val item = currentItem
        if (item != null && item.mediaType == MediaType.IMAGE && imageDisplayStartTime != null) {
            val displayDuration = (item.video.displayDurationSeconds ?: 7) * 1000L
            delay(displayDuration)
            
            scope.launch {
                val durationMs = playbackStartTime?.let { System.currentTimeMillis() - it }
                MediaSourceLogger.logPlaybackEnd(
                    video = item.video,
                    mediaType = item.mediaType,
                    sourceType = item.sourceType,
                    success = true,
                    durationMs = durationMs
                )
                repository.endPlayback(screenId, success = true, isVideo = false)
                moveToNextItem()
            }
        }
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            playbackEngine.cleanup()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        currentItem?.let { item ->
            AnimatedContent(
                targetState = currentIndex,
                transitionSpec = {
                    // Seamless transition - reduced delay
                    fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                },
                label = "mediaTransition"
            ) { index ->
                val playbackItem = playbackItems.getOrNull(index)
                when (playbackItem?.mediaType) {
                    MediaType.VIDEO -> {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = false
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            update = { view ->
                                val item = playbackItems.getOrNull(index)
                                if (item?.mediaType == MediaType.VIDEO) {
                                    val videoIndexInQueue = playbackItems
                                        .take(index)
                                        .count { it.mediaType == MediaType.VIDEO }
                                    if (exoPlayer.currentMediaItemIndex != videoIndexInQueue && videoIndexInQueue < exoPlayer.mediaItemCount) {
                                        exoPlayer.seekTo(videoIndexInQueue, 0)
                                        currentVideoIndexInQueue = videoIndexInQueue
                                    }
                                }
                            }
                        )
                    }
                    MediaType.IMAGE -> {
                        SeamlessImageDisplay(
                            imageUrl = playbackItem.playbackUri,
                            videoId = playbackItem.video.id
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Unsupported", color = androidx.compose.ui.graphics.Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeamlessImageDisplay(
    imageUrl: String,
    videoId: String
) {
    val context = LocalContext.current
    
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
                SubcomposeAsyncImageContent()
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Failed to load image",
                        style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        )
    }
}

