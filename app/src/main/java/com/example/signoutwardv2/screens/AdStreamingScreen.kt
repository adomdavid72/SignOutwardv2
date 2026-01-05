package com.example.signoutwardv2.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import android.util.Log
import com.example.signoutwardv2.cache.LocalCacheManager
import com.example.signoutwardv2.data.DevicePreferences
import com.example.signoutwardv2.data.MediaType
import com.example.signoutwardv2.data.MediaTypeDetector
import com.example.signoutwardv2.data.PlaybackRepository
import com.example.signoutwardv2.data.PlaylistProcessor
import com.example.signoutwardv2.data.models.PlaylistWithVideos
import com.example.signoutwardv2.data.models.Video
import com.example.signoutwardv2.playback.EnhancedPlaybackEngine
import com.example.signoutwardv2.playback.LocalPlaybackEngine
import com.example.signoutwardv2.sync.PlaylistSyncManager
import com.example.signoutwardv2.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ============================================================================
// AD STREAMING SCREEN
// ============================================================================
// Full-screen media playback with Supabase integration
// - Fetches playlists from Supabase
// - Logs playback events
// - Updates device analytics
// - Sends heartbeat to maintain 'online' status
// - Handles empty, loading, and error states
// ============================================================================

@Composable
fun AdStreamingScreen(
    screenId: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val preferences = remember { DevicePreferences(context) }
    val repository = remember { PlaybackRepository(preferences, context) }
    
    var currentScreenId by remember { mutableStateOf<String?>(null) }
    var groupId by remember { mutableStateOf<String?>(null) }
    var locationId by remember { mutableStateOf<String?>(null) }
    var showSuccessOverlay by remember { mutableStateOf(true) }
    
    // Local cache manager for download-first playback
    val localCacheManager = remember(currentScreenId) {
        currentScreenId?.let { LocalCacheManager(context, it) }
    }
    
    // Playlist sync manager for background sync
    val syncManager = remember(currentScreenId, localCacheManager) {
        currentScreenId?.let { id ->
            localCacheManager?.let { cache ->
                PlaylistSyncManager(context, id, cache, repository)
            }
        }
    }
    
    val playlistState by repository.playlistState.collectAsState()
    
    // Download progress state - observe StateFlow changes
    var downloadProgress by remember { mutableStateOf(emptyMap<String, LocalCacheManager.DownloadProgress>()) }
    var allDownloadsComplete by remember { mutableStateOf(false) }
    
    // Observe download progress changes
    LaunchedEffect(localCacheManager) {
        localCacheManager?.downloadProgress?.collect { progress ->
            downloadProgress = progress
        }
    }
    
    LaunchedEffect(localCacheManager) {
        localCacheManager?.allDownloadsComplete?.collect { complete ->
            allDownloadsComplete = complete
        }
    }
    
    // Get screen ID and start services
    // CRITICAL: Wrap network calls in try-catch to prevent crashes
    LaunchedEffect(Unit) {
        try {
            val resolvedScreenId = screenId ?: preferences.screenId.first()
            currentScreenId = resolvedScreenId
            groupId = preferences.groupId.first()
            locationId = preferences.locationId.first()
            
            resolvedScreenId?.let { id ->
                // Wrap fetchPlaylist in try-catch to handle network errors gracefully
                try {
                    repository.fetchPlaylist(id, groupId, locationId)
                    // Start playlist sync manager (2-minute interval)
                    syncManager?.startPeriodicSync(groupId, locationId)
                } catch (e: Exception) {
                    // Network error - app will show error state UI, but won't crash
                    Log.e("AdStreamingScreen", "Error fetching playlist: ${e.message}", e)
                    // Repository will set error state, UI will handle it
                }
            }
        } catch (e: Exception) {
            // DataStore or other initialization error - log but don't crash
            Log.e("AdStreamingScreen", "Error initializing screen: ${e.message}", e)
            // App will show default/error state, but remains functional
        }
    }
    
    // Handle lifecycle for heartbeat and cleanup
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    currentScreenId?.let { repository.stopHeartbeat(it) }
                    syncManager?.stopPeriodicSync()
                }
                Lifecycle.Event.ON_RESUME -> {
                    currentScreenId?.let { 
                        repository.startHeartbeat(it)
                        syncManager?.startPeriodicSync(groupId, locationId)
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    repository.cleanup()
                    syncManager?.cleanup()
                    localCacheManager?.cleanup()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            repository.cleanup()
            syncManager?.cleanup()
            localCacheManager?.cleanup()
        }
    }
    
    // Auto-hide success overlay
    LaunchedEffect(showSuccessOverlay) {
        if (showSuccessOverlay) {
            delay(3000)
            showSuccessOverlay = false
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val state = playlistState) {
            is PlaybackRepository.PlaylistLoadState.Loading -> LoadingState()
            is PlaybackRepository.PlaylistLoadState.Empty -> EmptyPlaylistState()
            is PlaybackRepository.PlaylistLoadState.Ready -> {
                // Process playlist to filter unsupported files
                val processed = PlaylistProcessor.processPlaylist(state.playlist)
                
                if (processed.hasSupportedFiles) {
                    // DOWNLOAD-FIRST PLAYBACK: Download all media before starting playback
                    DownloadFirstMediaPlayer(
                        playlist = PlaylistWithVideos(
                            playlist = state.playlist.playlist,
                            videos = processed.supportedVideos,
                            scheduleId = state.playlist.scheduleId
                        ),
                        unsupportedVideos = processed.unsupportedVideos,
                        screenId = currentScreenId ?: "",
                        repository = repository,
                        localCacheManager = localCacheManager,
                        downloadProgress = downloadProgress,
                        allDownloadsComplete = allDownloadsComplete
                    )
                } else {
                    // All files unsupported - show error
                    UnsupportedFileTypeError()
                }
            }
            is PlaybackRepository.PlaylistLoadState.Error -> ErrorState()
        }
        
        // Success overlay on first load
        AnimatedVisibility(
            visible = showSuccessOverlay,
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.9f),
            exit = fadeOut(tween(500)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            SuccessOverlay()
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        PurplePrimaryDark.copy(alpha = 0.3f),
                        Color.Black
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = PurplePrimary,
                strokeWidth = 6.dp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Loading content…",
                style = MaterialTheme.typography.titleMedium,
                color = White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun EmptyPlaylistState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(48.dp)
        ) {
            // Decorative icon
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(SurfaceLight),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(PurplePrimary, PurpleAccent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "▶",
                        fontSize = 48.sp,
                        color = White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = "You can run your ads here",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Add content from your web dashboard to start displaying ads on this screen",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 500.dp)
            )
        }
    }
}

@Composable
private fun ErrorState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        PurplePrimaryDark.copy(alpha = 0.2f),
                        Color.Black
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = PurplePrimary.copy(alpha = 0.6f),
                strokeWidth = 4.dp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Reconnecting…",
                style = MaterialTheme.typography.titleMedium,
                color = White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun UnsupportedFileTypeError() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(48.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Error.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚠",
                    fontSize = 64.sp,
                    color = Error
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Error: file type not recognised",
                style = MaterialTheme.typography.headlineMedium,
                color = White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "All files in this playlist are in unsupported formats.\nPlease contact support.",
                style = MaterialTheme.typography.bodyLarge,
                color = White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 500.dp)
            )
        }
    }
}

/**
 * Download-first MediaPlayer - Downloads all media before starting playback
 * Shows download progress UI and only starts playback when all files are cached locally
 */
@Composable
private fun DownloadFirstMediaPlayer(
    playlist: PlaylistWithVideos,
    unsupportedVideos: List<Video>,
    screenId: String,
    repository: PlaybackRepository,
    localCacheManager: LocalCacheManager?,
    downloadProgress: Map<String, LocalCacheManager.DownloadProgress>,
    allDownloadsComplete: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlistId = playlist.playlist.id
    
    // Enhanced playback engine with optimized buffering and source logging
    val enhancedPlaybackEngine = remember(playlistId, localCacheManager, repository) {
        EnhancedPlaybackEngine(
            context = context,
            cacheManager = localCacheManager,
            repository = repository,
            playlistId = playlistId ?: ""
        )
    }
    
    // Local playback engine for fallback (if needed)
    val localPlaybackEngine = remember(playlistId, localCacheManager) {
        localCacheManager?.let { LocalPlaybackEngine(context, it, playlistId ?: "") }
    }
    
    // Download state
    var isDownloading by remember { mutableStateOf(false) }
    var downloadStarted by remember { mutableStateOf(false) }
    
    // Start downloads when playlist is ready
    LaunchedEffect(playlist, localCacheManager) {
        if (localCacheManager == null || downloadStarted) return@LaunchedEffect
        
        downloadStarted = true
        isDownloading = true
        
        Log.d("DownloadFirstMediaPlayer", "=== Starting download-first playback ===")
        Log.d("DownloadFirstMediaPlayer", "Playlist ID: $playlistId")
        Log.d("DownloadFirstMediaPlayer", "Total media items: ${playlist.videos.size}")
        
        // Download all media files
        val allDownloaded = localCacheManager.downloadAllMedia(playlist.videos, playlistId ?: "")
        
        isDownloading = !allDownloaded
        
        if (allDownloaded) {
            Log.d("DownloadFirstMediaPlayer", "All downloads complete - ready for playback")
        } else {
            Log.w("DownloadFirstMediaPlayer", "Some downloads failed - playback may be incomplete")
        }
    }
    
    // Monitor download completion
    LaunchedEffect(allDownloadsComplete) {
        if (allDownloadsComplete) {
            isDownloading = false
            Log.d("DownloadFirstMediaPlayer", "All downloads complete - starting playback")
        }
    }
    
    // Show download progress UI while downloading
    if (isDownloading || !allDownloadsComplete) {
        DownloadProgressScreen(
            downloadProgress = downloadProgress,
            totalItems = playlist.videos.size,
            playlistName = playlist.playlist.name ?: "Playlist"
        )
        return
    }
    
    // All downloads complete - start playback using EnhancedPlaybackEngine
    var enhancedPlaybackItems by remember { mutableStateOf<List<EnhancedPlaybackEngine.EnhancedPlaybackItem>?>(null) }
    
    LaunchedEffect(playlist, allDownloadsComplete, enhancedPlaybackEngine) {
        if (!allDownloadsComplete) return@LaunchedEffect
        
        // Prepare enhanced playback items with source tracking
        val items = enhancedPlaybackEngine.preparePlaybackItems(playlist)
        enhancedPlaybackItems = items
        
        // Verify playback is ready
        val isReady = enhancedPlaybackEngine.verifyPlaybackReady(items)
        if (!isReady) {
            Log.w("DownloadFirstMediaPlayer", "Playback not ready - some items may not be cached")
        }
    }
    
    // Show enhanced media player when items are ready
    enhancedPlaybackItems?.let { items ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No media ready for playback",
                    color = White
                )
            }
        } else {
            // Use mixed media player with strict state machine for video/image separation
            MixedMediaPlayer(
                playbackItems = items,
                unsupportedVideos = unsupportedVideos,
                screenId = screenId,
                repository = repository,
                playlistId = playlistId ?: "",
                playbackEngine = enhancedPlaybackEngine
            )
        }
    } ?: run {
        // Still preparing playback items
        DownloadProgressScreen(
            downloadProgress = downloadProgress,
            totalItems = playlist.videos.size,
            playlistName = playlist.playlist.name ?: "Playlist"
        )
    }
}

/**
 * Download progress screen - Shows per-item download progress
 */
@Composable
private fun DownloadProgressScreen(
    downloadProgress: Map<String, LocalCacheManager.DownloadProgress>,
    totalItems: Int,
    playlistName: String
) {
    val completedCount = downloadProgress.values.count { it.isComplete }
    val downloadingCount = downloadProgress.values.count { it.isDownloading }
    val overallProgress = if (totalItems > 0) {
        completedCount.toFloat() / totalItems
    } else {
        0f
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(48.dp)
        ) {
            Text(
                text = "Downloading content…",
                style = MaterialTheme.typography.headlineMedium,
                color = White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Overall progress bar
            LinearProgressIndicator(
                progress = overallProgress,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = PurplePrimary,
                trackColor = White.copy(alpha = 0.2f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "$completedCount / $totalItems items downloaded",
                style = MaterialTheme.typography.bodyLarge,
                color = White.copy(alpha = 0.8f)
            )
            
            if (downloadingCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$downloadingCount downloading...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = White.copy(alpha = 0.6f)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Per-item progress (show first 5 items)
            downloadProgress.values.take(5).forEach { progress ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = progress.videoId.take(20),
                        style = MaterialTheme.typography.bodySmall,
                        color = White.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    LinearProgressIndicator(
                        progress = progress.progress,
                        modifier = Modifier
                            .width(100.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = PurplePrimary,
                        trackColor = White.copy(alpha = 0.1f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "${(progress.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = White.copy(alpha = 0.6f),
                        modifier = Modifier.width(40.dp)
                    )
                }
            }
            
            if (downloadProgress.size > 5) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "... and ${downloadProgress.size - 5} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * Local Media Player - Plays media from local storage only
 * Uses LocalPlaybackEngine to ensure deterministic playback order
 */
@Composable
private fun LocalMediaPlayer(
    playbackItems: List<LocalPlaybackEngine.PlaybackItem>,
    unsupportedVideos: List<Video>,
    screenId: String,
    repository: PlaybackRepository,
    playlistId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // PERSISTENT PLAYER: Single ExoPlayer instance for entire playlist
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
        }
    }
    
    // Preload video MediaItems (images handled separately)
    var allMediaItemsPrepared by remember { mutableStateOf(false) }
    
    LaunchedEffect(playbackItems) {
        val videoItems = playbackItems.filter { it.mediaType == MediaType.VIDEO }
        val videoMediaItems = videoItems.map { MediaItem.fromUri(it.localUri) }
        
        if (videoMediaItems.isNotEmpty()) {
            exoPlayer.setMediaItems(videoMediaItems)
            exoPlayer.prepare()
            Log.d("LocalMediaPlayer", "Preloaded ${videoMediaItems.size} video MediaItems (all local files)")
        }
        
        allMediaItemsPrepared = true
    }
    
    // Current playback state
    var currentIndex by remember { mutableStateOf(0) }
    var isPlayingVideo by remember { mutableStateOf(false) }
    var imageDisplayStartTime by remember { mutableStateOf<Long?>(null) }
    var playlistLoopCount by remember { mutableStateOf(0) }
    
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
            repository.startPlayback(item.video.id, playlistId)
            Log.d("LocalMediaPlayer", "Playback started: ${item.video.id} | Type: ${item.mediaType.name} | Local URI: ${item.localUri}")
            
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
        }
    }
    
    fun moveToNextItem() {
        if (playbackItems.isEmpty()) return
        val nextIndex = (currentIndex + 1) % playbackItems.size
        
        if (nextIndex == 0) {
            playlistLoopCount++
            scope.launch {
                repository.logPlaylistCompleted(screenId)
            }
        }
        
        currentIndex = nextIndex
    }
    
    // Handle ExoPlayer events
    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    androidx.media3.common.Player.STATE_ENDED -> {
                        scope.launch {
                            currentItem?.let { item ->
                                if (item.mediaType == MediaType.VIDEO) {
                                    repository.endPlayback(screenId, success = true, isVideo = true)
                                }
                            }
                            moveToNextItem()
                        }
                    }
                    androidx.media3.common.Player.STATE_READY -> {
                        isPlayingVideo = true
                    }
                }
            }
            
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                scope.launch {
                    currentItem?.let { item ->
                        if (item.mediaType == MediaType.VIDEO) {
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
                repository.endPlayback(screenId, success = true, isVideo = false)
                moveToNextItem()
            }
        }
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        currentItem?.let { item ->
            AnimatedContent(
                targetState = currentIndex,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                },
                label = "mediaTransition"
            ) { index ->
                val playbackItem = playbackItems.getOrNull(index)
                when (playbackItem?.mediaType) {
                    MediaType.VIDEO -> {
                        AndroidView(
                            factory = { ctx ->
                                androidx.media3.ui.PlayerView(ctx).apply {
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
                            imageUrl = playbackItem.localUri, // Local file URI
                            videoId = playbackItem.video.id
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Unsupported", color = White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaPlayer(
    playlist: PlaylistWithVideos,
    unsupportedVideos: List<Video>,
    screenId: String,
    repository: PlaybackRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val videos = playlist.videos
    val playlistId = playlist.playlist.id
    
    // PERSISTENT PLAYER: Single ExoPlayer instance for entire playlist
    // ENABLE REPEAT MODE for seamless looping
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL  // Seamless looping
            playWhenReady = true
        }
    }
    
    // Preload all MediaItems and resolve URIs off UI thread
    var mediaItems by remember(playlist) { mutableStateOf<List<MediaItemData>>(emptyList()) }
    var allMediaItemsPrepared by remember { mutableStateOf(false) }
    
    LaunchedEffect(playlist) {
        // Resolve all URIs off UI thread
        val resolvedItems = with(kotlinx.coroutines.Dispatchers.IO) {
            videos.mapIndexed { index, video ->
                val mediaType = MediaTypeDetector.detectMediaType(video.url, video.mimeType)
                val cachedUri = repository.getCachedUri(playlistId, video.id, video.url)
                val cacheStatus = repository.getCacheStatus(playlistId, video.id)
                
                val playbackUri = if (cachedUri != null && cacheStatus == com.example.signoutwardv2.cache.CacheStatus.COMPLETED) {
                    cachedUri // Local file
                } else {
                    video.url // Remote stream
                }
                
                val playbackSource = if (cachedUri != null && cacheStatus == com.example.signoutwardv2.cache.CacheStatus.COMPLETED) "LOCAL" else "REMOTE"
                Log.d("MediaPlayer", "MEDIA_ID: ${video.id} | CACHE_STATUS: ${cacheStatus.name} | PLAYBACK_SOURCE: $playbackSource | MEDIA_PREPARED: true")
                
                MediaItemData(
                    video = video,
                    mediaType = mediaType,
                    uri = playbackUri,
                    index = index,
                    isLocal = playbackSource == "LOCAL"
                )
            }
        }
        mediaItems = resolvedItems
        
        // PRELOAD ALL MEDIA: Build MediaItems for videos (images handled separately)
        val videoMediaItems = resolvedItems
            .filter { it.mediaType == MediaType.VIDEO }
            .map { MediaItem.fromUri(it.uri) }
        
        // Preload all video MediaItems into ExoPlayer queue BEFORE playback starts
        if (videoMediaItems.isNotEmpty()) {
            exoPlayer.setMediaItems(videoMediaItems)
            exoPlayer.prepare()
            Log.d("MediaPlayer", "Preloaded ${videoMediaItems.size} video MediaItems into ExoPlayer queue with REPEAT_MODE_ALL")
        }
        
        allMediaItemsPrepared = true
    }
    
    // Current playback state - track position in unified playlist (videos + images)
    var currentIndex by remember { mutableStateOf(0) }
    var isPlayingVideo by remember { mutableStateOf(false) }
    var imageDisplayStartTime by remember { mutableStateOf<Long?>(null) }
    var playlistLoopCount by remember { mutableStateOf(0) }
    
    val currentMedia = remember(currentIndex, mediaItems) {
        mediaItems.getOrNull(currentIndex)
    }
    
    // Track ExoPlayer's current video index in queue
    var currentVideoIndexInQueue by remember { mutableStateOf(0) }
    
    // Log unsupported files on first load
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
    LaunchedEffect(currentMedia, allMediaItemsPrepared) {
        if (!allMediaItemsPrepared) return@LaunchedEffect
        
        currentMedia?.let { media ->
            repository.startPlayback(media.video.id, playlistId)
            
            if (media.mediaType == MediaType.VIDEO) {
                // Seek ExoPlayer to correct video in queue
                val videoIndexInQueue = mediaItems
                    .take(currentIndex)
                    .count { it.mediaType == MediaType.VIDEO }
                
                // Only seek if we're not already at the correct position
                if (exoPlayer.currentMediaItemIndex != videoIndexInQueue && videoIndexInQueue < exoPlayer.mediaItemCount) {
                    exoPlayer.seekTo(videoIndexInQueue, 0)
                    currentVideoIndexInQueue = videoIndexInQueue
                }
                isPlayingVideo = true
                imageDisplayStartTime = null
            } else if (media.mediaType == MediaType.IMAGE) {
                // Start image display timer
                isPlayingVideo = false
                imageDisplayStartTime = System.currentTimeMillis()
            }
        }
    }
    
    // Define moveToNextItem function before it's used
    fun moveToNextItem() {
        if (mediaItems.isEmpty()) return
        val nextIndex = (currentIndex + 1) % mediaItems.size
        
        // Track playlist loop completion
        if (nextIndex == 0) {
            playlistLoopCount++
            scope.launch {
                repository.logPlaylistCompleted(screenId)
            }
        }
        
        // SEAMLESS LOOPING: Advance to next item without gap
        currentIndex = nextIndex
    }
    
    // Handle ExoPlayer events for videos
    // With REPEAT_MODE_ALL, ExoPlayer handles looping automatically
    // We just need to track when videos end to move to next item in unified playlist
    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    androidx.media3.common.Player.STATE_ENDED -> {
                        // Video ended - ExoPlayer will auto-loop, but we need to track unified playlist position
                        scope.launch {
                            currentMedia?.let { media ->
                                if (media.mediaType == MediaType.VIDEO) {
                                    repository.endPlayback(screenId, success = true, isVideo = true)
                                }
                            }
                            moveToNextItem()
                        }
                    }
                    androidx.media3.common.Player.STATE_READY -> {
                        // Video ready - seamless playback
                        isPlayingVideo = true
                    }
                }
            }
            
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                // Track when ExoPlayer transitions between videos in queue
                // This helps us sync with unified playlist position
                if (mediaItem != null && reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    // ExoPlayer auto-advanced (likely due to repeat mode)
                    // Find which video this corresponds to in our unified playlist
                    val videoIndex = exoPlayer.currentMediaItemIndex
                    currentVideoIndexInQueue = videoIndex
                    
                    // Find the corresponding item in unified playlist
                    var videoCount = 0
                    for (i in mediaItems.indices) {
                        if (mediaItems[i].mediaType == MediaType.VIDEO) {
                            if (videoCount == videoIndex) {
                                // This is the video that just started
                                scope.launch {
                                    repository.startPlayback(mediaItems[i].video.id, playlistId)
                                }
                                break
                            }
                            videoCount++
                        }
                    }
                }
            }
            
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                scope.launch {
                    currentMedia?.let { media ->
                        if (media.mediaType == MediaType.VIDEO) {
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
    LaunchedEffect(imageDisplayStartTime, currentMedia) {
        val media = currentMedia
        if (media != null && media.mediaType == MediaType.IMAGE && imageDisplayStartTime != null) {
            val displayDuration = (media.video.displayDurationSeconds ?: 7) * 1000L
            delay(displayDuration)
            
            // Image display complete
            scope.launch {
                repository.endPlayback(screenId, success = true, isVideo = false)
                moveToNextItem()
            }
        }
    }
    
    // Cleanup on dispose - release player only when MediaPlayer is disposed
    DisposableEffect(Unit) {
        onDispose {
            // Release player when MediaPlayer composable is disposed
            exoPlayer.release()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Render current media item with seamless transitions
        currentMedia?.let { media ->
            AnimatedContent(
                targetState = currentIndex,
                transitionSpec = {
                    // SEAMLESS LOOPING: Faster transition to eliminate gaps
                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                },
                label = "mediaTransition"
            ) { index ->
                val itemMedia = mediaItems.getOrNull(index)
                when (itemMedia?.mediaType) {
                    MediaType.VIDEO -> {
                        // Use persistent ExoPlayer for video
                        AndroidView(
                            factory = { ctx ->
                                androidx.media3.ui.PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = false
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            update = { view ->
                                // Update player view when index changes
                                // With REPEAT_MODE_ALL, ExoPlayer handles looping automatically
                                // We just need to ensure we're at the correct video in queue
                                val itemMedia = mediaItems.getOrNull(index)
                                if (itemMedia?.mediaType == MediaType.VIDEO) {
                                    val videoIndexInQueue = mediaItems
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
                        // Display image with seamless transition
                        SeamlessImageDisplay(
                            imageUrl = itemMedia.uri,
                            videoId = itemMedia.video.id
                        )
                    }
                    MediaType.UNSUPPORTED -> {
                        // Should not happen, but handle gracefully
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Unsupported file type",
                                color = White
                            )
                        }
                        LaunchedEffect(itemMedia.video.id) {
                            delay(2000)
                            moveToNextItem()
                        }
                    }
                    null -> {
                        // No media item at this index
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                        )
                    }
                }
            }
        } ?: run {
            // Loading state while resolving media items
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = PurplePrimary.copy(alpha = 0.6f),
                    strokeWidth = 4.dp
                )
            }
        }
    }
}

// Data class for media item with resolved URI
private data class MediaItemData(
    val video: Video,
    val mediaType: MediaType,
    val uri: String,
    val index: Int,
    val isLocal: Boolean
)

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
                // Minimal loading indicator for seamless transition
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = PurplePrimary.copy(alpha = 0.6f),
                        strokeWidth = 4.dp
                    )
                }
            },
            success = {
                SubcomposeAsyncImageContent()
            },
            error = {
                // Error state - minimal to avoid disrupting playback
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Failed to load image",
                        style = MaterialTheme.typography.bodyMedium,
                        color = White.copy(alpha = 0.6f)
                    )
                }
            }
        )
    }
}

@Composable
private fun VideoPlayer(
    video: Video,
    playlistId: String,
    repository: PlaybackRepository,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isBuffering by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    
    // STRICT CACHE RESOLUTION: Only use local if COMPLETED and valid
    // PARTIAL PLAYBACK PREVENTION: Never play files while DOWNLOADING
    // ENFORCED LOCAL PLAYBACK: Once COMPLETED, always use local file
    val cachedUri = remember(video.id) {
        repository.getCachedUri(playlistId, video.id, video.url)
    }
    val cacheStatus = remember(video.id) {
        repository.getCacheStatus(playlistId, video.id)
    }
    
    // Use cached file ONLY if status is COMPLETED, otherwise stream from remote
    // Explicitly forbidden: Playing local files while downloading
    val videoUri = if (cachedUri != null && cacheStatus == com.example.signoutwardv2.cache.CacheStatus.COMPLETED) {
        cachedUri // Local file URI (file://)
    } else {
        video.url // Stream from remote (HTTP/HTTPS)
    }
    
    // Handle local playback failure - fallback to remote
    var localPlaybackFailed by remember { mutableStateOf(false) }
    var useRemoteUri by remember { mutableStateOf(false) }
    
    // Reset failure state when video changes
    LaunchedEffect(video.id) {
        localPlaybackFailed = false
        useRemoteUri = false
    }
    
    // Determine final URI (remote if local failed)
    val finalVideoUri = if (useRemoteUri || localPlaybackFailed) {
        video.url // Force remote
    } else {
        videoUri
    }
    
    val exoPlayer = remember(video.id, finalVideoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(finalVideoUri))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            isBuffering = true
                        }
                        Player.STATE_READY -> {
                            isBuffering = false
                        }
                        Player.STATE_ENDED -> {
                            isBuffering = false
                            onComplete()
                        }
                    }
                }
                
                override fun onIsLoadingChanged(isLoading: Boolean) {
                    isBuffering = isLoading
                }
                
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    isBuffering = false
                    hasError = true
                    
                    // If local playback failed, mark cache as FAILED and switch to remote
                    if (finalVideoUri.startsWith("file://") && !localPlaybackFailed) {
                        Log.e("VideoPlayer", "Local playback failed for ${video.id}, switching to remote")
                        localPlaybackFailed = true
                        useRemoteUri = true
                        
                        // Mark cache as failed and delete corrupted file
                        scope.launch {
                            repository.markCacheFailed(playlistId, video.id, video.url)
                            repository.deleteCachedFile(playlistId, video.id, video.url)
                        }
                        
                        // Player will be recreated with remote URI on next recomposition
                        // For now, just log the error
                        Log.w("VideoPlayer", "Switching to remote playback for ${video.id}")
                    } else {
                        onError(error.message ?: "Playback error")
                    }
                }
            })
        }
    }
    
    DisposableEffect(video.id, finalVideoUri) {
        onDispose {
            exoPlayer.release()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Buffering overlay
        AnimatedVisibility(
            visible = isBuffering && !hasError,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = PurplePrimary,
                        strokeWidth = 6.dp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Buffering video…",
                        style = MaterialTheme.typography.titleMedium,
                        color = White.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Please wait while content loads",
                        style = MaterialTheme.typography.bodyMedium,
                        color = White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SuccessOverlay() {
    Card(
        modifier = Modifier.padding(32.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 16.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(PurplePrimary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    fontSize = 24.sp,
                    color = White
                )
            }
            
            Text(
                text = "Device paired successfully",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }
    }
}
