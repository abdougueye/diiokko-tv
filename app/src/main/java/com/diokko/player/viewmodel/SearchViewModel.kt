package com.diokko.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diokko.player.data.database.ChannelDao
import com.diokko.player.data.database.MovieDao
import com.diokko.player.data.database.SeriesDao
import com.diokko.player.data.models.Channel
import com.diokko.player.data.models.Movie
import com.diokko.player.data.models.Series
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow(GlobalSearchResults())
    val searchResults: StateFlow<GlobalSearchResults> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    /**
     * Update search query with debounce
     */
    fun updateQuery(query: String) {
        _searchQuery.value = query
        
        // Cancel previous search
        searchJob?.cancel()
        
        if (query.isBlank()) {
            _searchResults.value = GlobalSearchResults()
            _isSearching.value = false
            return
        }
        
        // Debounce search by 300ms
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            delay(300)
            performSearch(query)
        }
    }

    /**
     * Perform global search across all content types
     */
    private suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            _searchResults.value = GlobalSearchResults()
            _isSearching.value = false
            return
        }

        try {
            // Combine all search results
            combine(
                channelDao.searchChannels(query),
                movieDao.searchMovies(query),
                seriesDao.searchSeries(query)
            ) { channels, movies, series ->
                GlobalSearchResults(
                    channels = channels,
                    movies = movies,
                    series = series
                )
            }.collect { results ->
                _searchResults.value = results
                _isSearching.value = false
            }
        } catch (e: Exception) {
            _isSearching.value = false
        }
    }

    /**
     * Clear search
     */
    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = GlobalSearchResults()
        _isSearching.value = false
        searchJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
    }
}
