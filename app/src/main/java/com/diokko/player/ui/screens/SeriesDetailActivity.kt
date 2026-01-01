package com.diokko.player.ui.screens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.diokko.player.data.models.Episode
import com.diokko.player.data.models.Series
import com.diokko.player.data.repository.ContentRepository
import com.diokko.player.player.VideoPlayerActivity
import com.diokko.player.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SeriesDetailActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_SERIES_ID = "extra_series_id"
        const val EXTRA_SERIES_NAME = "extra_series_name"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val seriesId = intent.getLongExtra(EXTRA_SERIES_ID, -1)
        val seriesName = intent.getStringExtra(EXTRA_SERIES_NAME) ?: ""
        
        setContent {
            DiokkoPlayerTheme {
                SeriesDetailScreen(
                    seriesId = seriesId,
                    seriesName = seriesName,
                    onBack = { finish() },
                    onPlayEpisode = { url, title ->
                        val playerIntent = Intent(this, VideoPlayerActivity::class.java).apply {
                            putExtra(VideoPlayerActivity.EXTRA_URL, url)
                            putExtra(VideoPlayerActivity.EXTRA_TITLE, title)
                        }
                        startActivity(playerIntent)
                    }
                )
            }
        }
    }
}

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {
    
    private val _series = MutableStateFlow<Series?>(null)
    val series: StateFlow<Series?> = _series.asStateFlow()
    
    private val _episodes = MutableStateFlow<List<Episode>>(emptyList())
    val episodes: StateFlow<List<Episode>> = _episodes.asStateFlow()
    
    private val _seasons = MutableStateFlow<List<Int>>(listOf(1))
    val seasons: StateFlow<List<Int>> = _seasons.asStateFlow()
    
    private val _selectedSeason = MutableStateFlow(1)
    val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()
    
    private var episodesJob: kotlinx.coroutines.Job? = null
    
    fun loadSeries(seriesId: Long) {
        // Cancel any existing job
        episodesJob?.cancel()
        
        viewModelScope.launch(Dispatchers.IO) {
            // Load series info
            val seriesData = contentRepository.getSeriesById(seriesId)
            _series.value = seriesData
        }
        
        // Load episodes
        episodesJob = viewModelScope.launch(Dispatchers.IO) {
            contentRepository.getEpisodesForSeries(seriesId).collect { episodeList ->
                _episodes.value = episodeList
                
                // Extract unique seasons
                if (episodeList.isNotEmpty()) {
                    val seasonNumbers = mutableListOf<Int>()
                    for (ep in episodeList) {
                        if (!seasonNumbers.contains(ep.season)) {
                            seasonNumbers.add(ep.season)
                        }
                    }
                    seasonNumbers.sort()
                    _seasons.value = seasonNumbers
                    
                    val currentSeason = _selectedSeason.value
                    if (!seasonNumbers.contains(currentSeason) && seasonNumbers.isNotEmpty()) {
                        _selectedSeason.value = seasonNumbers.first()
                    }
                } else {
                    _seasons.value = listOf(1)
                }
            }
        }
    }
    
    fun selectSeason(season: Int) {
        _selectedSeason.value = season
    }
    
    fun getEpisodesForSeason(season: Int): List<Episode> {
        val allEpisodes = _episodes.value
        val filtered = mutableListOf<Episode>()
        for (ep in allEpisodes) {
            if (ep.season == season) {
                filtered.add(ep)
            }
        }
        return filtered.sortedBy { it.episodeNum }
    }
}

@Composable
fun SeriesDetailScreen(
    seriesId: Long,
    seriesName: String,
    onBack: () -> Unit,
    onPlayEpisode: (url: String, title: String) -> Unit,
    viewModel: SeriesDetailViewModel = hiltViewModel()
) {
    val series by viewModel.series.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val seasons by viewModel.seasons.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    
    val seasonFocusRequester = remember { FocusRequester() }
    val episodeListState = rememberLazyListState()
    var focusedEpisodeIndex by remember { mutableStateOf(0) }
    var isSeasonFocused by remember { mutableStateOf(true) }
    
    val currentEpisodes = remember(selectedSeason, episodes) {
        viewModel.getEpisodesForSeason(selectedSeason)
    }
    
    LaunchedEffect(seriesId) {
        if (seriesId > 0) {
            viewModel.loadSeries(seriesId)
        }
    }
    
    // Request focus on season selector initially
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try { seasonFocusRequester.requestFocus() } catch (e: Exception) {}
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DiokkoColors.Background)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Back, Key.Escape -> {
                            onBack()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(DiokkoDimens.spacingLg)
        ) {
            // Header with poster and info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(DiokkoDimens.spacingLg)
            ) {
                // Poster
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .fillMaxHeight()
                        .clip(DiokkoShapes.medium)
                        .background(DiokkoColors.Surface)
                ) {
                    val posterUrl = series?.posterUrl
                    if (!posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = seriesName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "📺", fontSize = 48.sp)
                        }
                    }
                }
                
                // Info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(DiokkoDimens.spacingSm)
                ) {
                    Text(
                        text = seriesName,
                        style = DiokkoTypography.headlineLarge,
                        color = DiokkoColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    val genreText = series?.genre
                    if (genreText != null) {
                        Text(
                            text = genreText,
                            style = DiokkoTypography.bodyMedium,
                            color = DiokkoColors.TextSecondary
                        )
                    }
                    
                    val seasonCount = seasons.size
                    val episodeCount = episodes.size
                    Text(
                        text = "$seasonCount Season${if (seasonCount != 1) "s" else ""} • $episodeCount Episode${if (episodeCount != 1) "s" else ""}",
                        style = DiokkoTypography.bodyMedium,
                        color = DiokkoColors.TextSecondary
                    )
                    
                    val plotText = series?.plot
                    if (plotText != null) {
                        Text(
                            text = plotText,
                            style = DiokkoTypography.bodySmall,
                            color = DiokkoColors.TextSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(DiokkoDimens.spacingLg))
            
            // Season selector
            Text(
                text = "Seasons",
                style = DiokkoTypography.titleMedium,
                color = DiokkoColors.TextPrimary
            )
            
            Spacer(modifier = Modifier.height(DiokkoDimens.spacingSm))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(DiokkoDimens.spacingSm),
                modifier = Modifier
                    .focusRequester(seasonFocusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && isSeasonFocused) {
                            when (event.key) {
                                Key.DirectionLeft -> {
                                    val currentIndex = seasons.indexOf(selectedSeason)
                                    if (currentIndex > 0) {
                                        viewModel.selectSeason(seasons[currentIndex - 1])
                                        focusedEpisodeIndex = 0
                                    }
                                    true
                                }
                                Key.DirectionRight -> {
                                    val currentIndex = seasons.indexOf(selectedSeason)
                                    if (currentIndex < seasons.size - 1) {
                                        viewModel.selectSeason(seasons[currentIndex + 1])
                                        focusedEpisodeIndex = 0
                                    }
                                    true
                                }
                                Key.DirectionDown -> {
                                    if (currentEpisodes.isNotEmpty()) {
                                        isSeasonFocused = false
                                        focusedEpisodeIndex = 0
                                    }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            ) {
                items(seasons) { season ->
                    val isSelected = season == selectedSeason
                    Box(
                        modifier = Modifier
                            .clip(DiokkoShapes.small)
                            .background(
                                if (isSelected && isSeasonFocused) DiokkoColors.Accent
                                else if (isSelected) DiokkoColors.SurfaceLight
                                else DiokkoColors.Surface
                            )
                            .border(
                                width = if (isSelected && isSeasonFocused) 2.dp else 0.dp,
                                color = if (isSelected && isSeasonFocused) DiokkoColors.Accent else Color.Transparent,
                                shape = DiokkoShapes.small
                            )
                            .padding(horizontal = DiokkoDimens.spacingMd, vertical = DiokkoDimens.spacingSm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Season $season",
                            style = DiokkoTypography.labelLarge,
                            color = if (isSelected && isSeasonFocused) DiokkoColors.TextOnAccent
                                   else if (isSelected) DiokkoColors.TextPrimary
                                   else DiokkoColors.TextSecondary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(DiokkoDimens.spacingLg))
            
            // Episodes
            Text(
                text = "Episodes",
                style = DiokkoTypography.titleMedium,
                color = DiokkoColors.TextPrimary
            )
            
            Spacer(modifier = Modifier.height(DiokkoDimens.spacingSm))
            
            if (currentEpisodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No episodes available",
                        style = DiokkoTypography.bodyMedium,
                        color = DiokkoColors.TextSecondary
                    )
                }
            } else {
                LazyRow(
                    state = episodeListState,
                    horizontalArrangement = Arrangement.spacedBy(DiokkoDimens.spacingSm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && !isSeasonFocused) {
                                when (event.key) {
                                    Key.DirectionUp -> {
                                        isSeasonFocused = true
                                        try { seasonFocusRequester.requestFocus() } catch (e: Exception) {}
                                        true
                                    }
                                    Key.DirectionLeft -> {
                                        if (focusedEpisodeIndex > 0) {
                                            focusedEpisodeIndex--
                                        }
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        if (focusedEpisodeIndex < currentEpisodes.size - 1) {
                                            focusedEpisodeIndex++
                                        }
                                        true
                                    }
                                    Key.Enter, Key.DirectionCenter -> {
                                        if (focusedEpisodeIndex >= 0 && focusedEpisodeIndex < currentEpisodes.size) {
                                            val episode = currentEpisodes[focusedEpisodeIndex]
                                            onPlayEpisode(episode.streamUrl, "$seriesName - ${episode.name}")
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                ) {
                    itemsIndexed(currentEpisodes) { index, episode ->
                        val isFocused = index == focusedEpisodeIndex && !isSeasonFocused
                        
                        // Episode poster card
                        Column(
                            modifier = Modifier
                                .width(140.dp)
                                .clip(DiokkoShapes.medium)
                                .background(
                                    if (isFocused) DiokkoColors.Accent
                                    else DiokkoColors.Surface
                                )
                                .border(
                                    width = if (isFocused) 3.dp else 0.dp,
                                    color = if (isFocused) DiokkoColors.Accent else Color.Transparent,
                                    shape = DiokkoShapes.medium
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Poster image
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(DiokkoShapes.medium)
                                    .background(DiokkoColors.SurfaceLight),
                                contentAlignment = Alignment.Center
                            ) {
                                val posterUrl = episode.posterUrl
                                if (!posterUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = posterUrl,
                                        contentDescription = episode.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    // Fallback: show episode number
                                    Text(
                                        text = "E${episode.episodeNum}",
                                        style = DiokkoTypography.headlineLarge,
                                        color = DiokkoColors.TextSecondary
                                    )
                                }
                                
                                // Episode number badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                        .clip(DiokkoShapes.small)
                                        .background(DiokkoColors.Background.copy(alpha = 0.8f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "E${episode.episodeNum}",
                                        style = DiokkoTypography.labelSmall,
                                        color = DiokkoColors.TextPrimary
                                    )
                                }
                                
                                // Play icon overlay when focused
                                if (isFocused) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "▶",
                                            style = DiokkoTypography.headlineLarge,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            
                            // Episode title
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = episode.name,
                                    style = DiokkoTypography.labelMedium,
                                    color = if (isFocused) DiokkoColors.TextOnAccent
                                           else DiokkoColors.TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                val episodeDuration = episode.duration
                                if (episodeDuration != null) {
                                    Text(
                                        text = episodeDuration,
                                        style = DiokkoTypography.labelSmall,
                                        color = if (isFocused) DiokkoColors.TextOnAccent.copy(alpha = 0.7f)
                                               else DiokkoColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Scroll to focused episode
    LaunchedEffect(focusedEpisodeIndex) {
        if (currentEpisodes.isNotEmpty() && focusedEpisodeIndex >= 0 && focusedEpisodeIndex < currentEpisodes.size) {
            episodeListState.animateScrollToItem(focusedEpisodeIndex)
        }
    }
}
