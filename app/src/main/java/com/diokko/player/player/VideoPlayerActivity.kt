package com.diokko.player.player

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import androidx.compose.material3.CircularProgressIndicator
import com.diokko.player.ui.theme.DiokkoPlayerTheme
import com.diokko.player.ui.theme.DiokkoColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@UnstableApi
@AndroidEntryPoint
class VideoPlayerActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_START_POSITION = "extra_start_position"
        // Content type: "LIVE_TV", "MOVIE", "SERIES"
        const val EXTRA_CONTENT_TYPE = "extra_content_type"
        // Episode info for series
        const val EXTRA_SERIES_ID = "extra_series_id"
        const val EXTRA_SEASON = "extra_season"
        const val EXTRA_EPISODE_NUM = "extra_episode_num"
        // Next episode info (pre-computed)
        const val EXTRA_NEXT_EPISODE_URL = "extra_next_episode_url"
        const val EXTRA_NEXT_EPISODE_TITLE = "extra_next_episode_title"
    }
    
    private var player: ExoPlayer? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        
        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Unknown"
        val startPosition = intent.getLongExtra(EXTRA_START_POSITION, 0L)
        val contentType = intent.getStringExtra(EXTRA_CONTENT_TYPE) ?: "LIVE_TV"
        val nextEpisodeUrl = intent.getStringExtra(EXTRA_NEXT_EPISODE_URL)
        val nextEpisodeTitle = intent.getStringExtra(EXTRA_NEXT_EPISODE_TITLE)
        
        setContent {
            DiokkoPlayerTheme {
                VideoPlayerScreen(
                    url = url,
                    title = title,
                    startPosition = startPosition,
                    contentType = contentType,
                    nextEpisodeUrl = nextEpisodeUrl,
                    nextEpisodeTitle = nextEpisodeTitle,
                    onBack = { finish() },
                    onPlayNext = { nextUrl, nextTitle ->
                        // Start new activity for next episode
                        val nextIntent = Intent(this, VideoPlayerActivity::class.java).apply {
                            putExtra(EXTRA_URL, nextUrl)
                            putExtra(EXTRA_TITLE, nextTitle)
                            putExtra(EXTRA_CONTENT_TYPE, "SERIES")
                        }
                        startActivity(nextIntent)
                        finish()
                    },
                    onPlayerReady = { exoPlayer -> player = exoPlayer }
                )
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        player?.pause()
    }
    
    override fun onResume() {
        super.onResume()
        player?.play()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}

@UnstableApi
@Composable
fun VideoPlayerScreen(
    url: String,
    title: String,
    startPosition: Long,
    contentType: String = "LIVE_TV",
    nextEpisodeUrl: String? = null,
    nextEpisodeTitle: String? = null,
    onBack: () -> Unit,
    onPlayNext: (String, String) -> Unit = { _, _ -> },
    onPlayerReady: (ExoPlayer) -> Unit
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var playbackEnded by remember { mutableStateOf(false) }
    
    // Show "Next Episode" button when 15 seconds before end
    val showNextButton = nextEpisodeUrl != null && 
                         duration > 0 && 
                         currentPosition > 0 &&
                         (duration - currentPosition) <= 15000 &&
                         !playbackEnded
    
    // Countdown for auto-play
    var autoPlayCountdown by remember { mutableIntStateOf(10) }
    var autoPlayCancelled by remember { mutableStateOf(false) }
    
    val player = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setUserAgent("Diokko Player/1.0")
        
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
                val mediaItem = MediaItem.fromUri(url)
                
                val mediaSource = when {
                    url.contains(".m3u8") || url.contains("/live/") -> {
                        HlsMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(mediaItem)
                    }
                    else -> {
                        ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(mediaItem)
                    }
                }
                
                setMediaSource(mediaSource)
                
                if (startPosition > 0) {
                    seekTo(startPosition)
                }
                
                playWhenReady = true
                prepare()
                
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_READY) {
                            duration = this@apply.duration
                        }
                        if (state == Player.STATE_ENDED) {
                            playbackEnded = true
                        }
                    }
                    
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }
                    
                    override fun onPlayerError(err: PlaybackException) {
                        error = err.message ?: "Playback error"
                    }
                })
            }
    }
    
    // Handle playback ended
    LaunchedEffect(playbackEnded) {
        if (playbackEnded && !autoPlayCancelled) {
            when (contentType) {
                "MOVIE" -> {
                    // For movies, go back to previous screen
                    delay(2000) // Brief delay before going back
                    onBack()
                }
                "SERIES" -> {
                    // For series with next episode, start countdown
                    if (nextEpisodeUrl != null && nextEpisodeTitle != null) {
                        while (autoPlayCountdown > 0 && !autoPlayCancelled) {
                            delay(1000)
                            autoPlayCountdown--
                        }
                        if (!autoPlayCancelled && autoPlayCountdown <= 0) {
                            onPlayNext(nextEpisodeUrl, nextEpisodeTitle)
                        }
                    } else {
                        // No next episode, go back
                        delay(2000)
                        onBack()
                    }
                }
                // LIVE_TV - do nothing special
            }
        }
    }
    
    LaunchedEffect(player) {
        onPlayerReady(player)
    }
    
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = player.currentPosition
            delay(1000)
        }
    }
    
    LaunchedEffect(showControls) {
        if (showControls && isPlaying) {
            delay(5000)
            showControls = false
        }
    }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // Ignore focus errors
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter -> {
                            if (showControls) {
                                if (isPlaying) player.pause() else player.play()
                            } else {
                                showControls = true
                            }
                            true
                        }
                        Key.DirectionLeft -> {
                            player.seekTo(maxOf(0, player.currentPosition - 10000))
                            showControls = true
                            true
                        }
                        Key.DirectionRight -> {
                            player.seekTo(minOf(player.duration, player.currentPosition + 10000))
                            showControls = true
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            showControls = true
                            true
                        }
                        Key.Back, Key.Escape -> {
                            if (showControls) {
                                showControls = false
                            } else {
                                onBack()
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Video Player
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Buffering indicator
        if (isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = DiokkoColors.Accent
                )
            }
        }
        
        // Error overlay
        error?.let { errorMessage ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Playback Error",
                        color = DiokkoColors.Accent,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onBack) {
                        Text("Back")
                    }
                }
            }
        }
        
        // Next Episode button (shows 15 seconds before end)
        AnimatedVisibility(
            visible = showNextButton && error == null,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(32.dp)
        ) {
            Button(
                onClick = {
                    if (nextEpisodeUrl != null && nextEpisodeTitle != null) {
                        onPlayNext(nextEpisodeUrl, nextEpisodeTitle)
                    }
                },
                colors = ButtonDefaults.colors(
                    containerColor = DiokkoColors.Accent
                ),
                modifier = Modifier.focusable()
            ) {
                Text("Next Episode ▶▶")
            }
        }
        
        // Playback ended overlay with countdown for series
        AnimatedVisibility(
            visible = playbackEnded && error == null && contentType == "SERIES" && nextEpisodeUrl != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "Up Next",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = nextEpisodeTitle ?: "Next Episode",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (!autoPlayCancelled) {
                        // Countdown circle
                        Box(
                            modifier = Modifier.size(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { autoPlayCountdown / 10f },
                                modifier = Modifier.size(80.dp),
                                color = DiokkoColors.Accent,
                                strokeWidth = 4.dp,
                                trackColor = Color.White.copy(alpha = 0.2f)
                            )
                            Text(
                                text = "$autoPlayCountdown",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineLarge
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Starting in $autoPlayCountdown seconds...",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Play Now button
                        Button(
                            onClick = {
                                if (nextEpisodeUrl != null && nextEpisodeTitle != null) {
                                    onPlayNext(nextEpisodeUrl, nextEpisodeTitle)
                                }
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = DiokkoColors.Accent
                            )
                        ) {
                            Text("Play Now")
                        }
                        
                        // Cancel button
                        Button(
                            onClick = {
                                autoPlayCancelled = true
                                onBack()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = Color.Gray
                            )
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            }
        }
        
        // Playback ended overlay for movies (simple "Playback Complete" message)
        AnimatedVisibility(
            visible = playbackEnded && error == null && contentType == "MOVIE",
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "✓",
                        color = DiokkoColors.Accent,
                        style = MaterialTheme.typography.displayLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Playback Complete",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Returning to previous screen...",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        
        // Controls overlay
        AnimatedVisibility(
            visible = showControls && error == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top gradient with title
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.8f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                
                // Center play/pause indicator
                if (!isPlaying) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "▶︎",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineLarge
                            )
                        }
                    }
                }
                
                // Bottom gradient with progress
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    // Progress bar
                    if (duration > 0) {
                        val progress = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Color.White.copy(alpha = 0.3f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .background(DiokkoColors.Accent)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(currentPosition),
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = formatTime(duration),
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Control hints
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ControlHint("◀◀", "-10s")
                        Spacer(modifier = Modifier.width(32.dp))
                        ControlHint("OK", if (isPlaying) "Pause" else "Play")
                        Spacer(modifier = Modifier.width(32.dp))
                        ControlHint("▶▶", "+10s")
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlHint(key: String, action: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .background(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = key,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = action,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
