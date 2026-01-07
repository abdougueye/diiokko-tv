package com.diokko.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diokko.player.data.database.ChannelDao
import com.diokko.player.data.database.MovieDao
import com.diokko.player.data.database.SeriesDao
import com.diokko.player.data.models.Channel
import com.diokko.player.data.models.Movie
import com.diokko.player.data.models.Series
import com.diokko.player.data.models.SearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * Search result wrapper for global search
 */
data class GlobalSearchResults(
    val channels: List<Channel> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList()
) {
    val isEmpty: Boolean get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()
    val totalCount: Int get() = channels.size + movies.size + series.size
    
    /**
     * Build a flat list of SearchResult for easy navigation.
     */
    fun toFlatList(maxPerType: Int = 10): List<SearchResult> = buildList {
        channels.take(maxPerType).forEach { add(SearchResult.ChannelResult(it)) }
        movies.take(maxPerType).forEach { add(SearchResult.MovieResult(it)) }
        series.take(maxPerType).forEach { add(SearchResult.SeriesResult(it)) }
    }
}

/**
 * SearchViewModel using flatMapLatest for efficient Fire TV search.
 * 
 * Key benefits:
 * - flatMapLatest automatically cancels previous searches when user types
 * - No manual Job management needed
 * - Reactive flow-based architecture
 * - Dividers filtered at the source
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    /**
     * Reactive search results stream.
     * - Debounces input by 300ms to avoid excessive queries
     * - flatMapLatest automatically cancels previous search when new query arrives
     * - Filters dividers so they never reach the UI
     */
    val searchResults: StateFlow<GlobalSearchResults> = _searchQuery
        .debounce(300)
        .onEach { query ->
            if (query.isNotBlank()) _isSearching.value = true
        }
        .flatMapLatest { query ->
            if (query.isBlank()) {
                _isSearching.value = false
                flowOf(GlobalSearchResults())
            } else {
                combine(
                    channelDao.searchChannels(query),
                    movieDao.searchMovies(query),
                    seriesDao.searchSeries(query)
                ) { channels, movies, series ->
                    GlobalSearchResults(
                        // Filter out dividers and items without valid stream URLs
                        channels = channels.filter { channel ->
                            !channel.isDivider && 
                            !channel.name.startsWith("#####") &&
                            channel.streamUrl.isNotBlank()
                        },
                        movies = movies.filter { it.streamUrl.isNotBlank() },
                        series = series
                    )
                }
            }
        }
        .onEach { _isSearching.value = false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = GlobalSearchResults()
        )

    /**
     * Update search query - triggers the flatMapLatest flow
     */
    fun updateQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Clear search
     */
    fun clearSearch() {
        _searchQuery.value = ""
    }
}
