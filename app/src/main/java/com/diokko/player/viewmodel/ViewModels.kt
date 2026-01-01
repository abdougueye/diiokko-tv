package com.diokko.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diokko.player.data.models.*
import com.diokko.player.data.repository.ContentRepository
import com.diokko.player.data.repository.PlaylistRepository
import com.diokko.player.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main ViewModel for app-wide state
 */
@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {
    
    private val _isNavExpanded = MutableStateFlow(false)
    val isNavExpanded: StateFlow<Boolean> = _isNavExpanded.asStateFlow()
    
    private val _selectedNavItem = MutableStateFlow<Screen>(Screen.TV)
    val selectedNavItem: StateFlow<Screen> = _selectedNavItem.asStateFlow()
    
    fun selectNavItem(screen: Screen) {
        _selectedNavItem.value = screen
    }
    
    fun expandNav() {
        _isNavExpanded.value = true
    }
    
    fun collapseNav() {
        _isNavExpanded.value = false
    }
    
    fun toggleNavExpanded() {
        _isNavExpanded.value = !_isNavExpanded.value
    }
}

/**
 * UI State for TV screen
 */
data class TvUiState(
    val channels: List<Channel> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * ViewModel for TV screen
 */
@HiltViewModel
class TvViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {
    
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()
    
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()
    
    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory: StateFlow<Category?> = _selectedCategory.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Contextual search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()
    
    private var channelsJob: kotlinx.coroutines.Job? = null
    private var categoriesJob: kotlinx.coroutines.Job? = null
    private var searchJob: kotlinx.coroutines.Job? = null
    
    // Store current category for search filtering
    private var currentGroupTitle: String? = null
    
    fun loadChannels() {
        // Cancel any existing jobs to avoid memory leaks
        categoriesJob?.cancel()
        channelsJob?.cancel()
        
        _isLoading.value = true
        
        // Load categories on IO thread
        categoriesJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            contentRepository.getLiveCategories().collect { cats ->
                _categories.value = cats
            }
        }
        
        // Load all channels initially on IO thread
        channelsJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            contentRepository.getAllChannels().collect { channels ->
                _channels.value = channels
                _isLoading.value = false
            }
        }
        
        // Safety timeout
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            if (_isLoading.value) {
                _isLoading.value = false
            }
        }
    }
    
    fun selectCategory(category: Category?) {
        _selectedCategory.value = category
    }
    
    fun loadChannelsForCategory(categoryId: Long) {
        // Cancel previous channels job to avoid multiple collectors
        channelsJob?.cancel()
        
        channelsJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            contentRepository.getChannelsForCategory(categoryId).collect { channelList ->
                _channels.value = channelList
                _isLoading.value = false
            }
        }
    }
    
    fun loadChannelsForGroup(groupTitle: String) {
        channelsJob?.cancel()
        searchJob?.cancel()
        currentGroupTitle = groupTitle
        
        // Clear search when explicitly selecting a category
        _searchQuery.value = ""
        _isSearchActive.value = false
        
        channelsJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            contentRepository.getChannelsByGroupTitle(groupTitle).collect { channelList ->
                _channels.value = channelList
                _isLoading.value = false
            }
        }
    }
    
    // Contextual search methods - searches ALL categories
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        _isSearchActive.value = query.isNotBlank()
        
        searchJob?.cancel()
        channelsJob?.cancel()
        
        if (query.isBlank()) {
            // Reload current category or all channels
            if (currentGroupTitle != null) {
                loadChannelsForGroup(currentGroupTitle!!)
            } else {
                loadChannels()
            }
            return
        }
        
        // Debounce search - always search ALL channels
        searchJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(300)
            
            // Always search all channels, not just current category
            contentRepository.searchChannels(query).collect { results ->
                _channels.value = results
            }
        }
    }
    
    fun clearSearch() {
        _searchQuery.value = ""
        _isSearchActive.value = false
        searchJob?.cancel()
        
        // Reload current content
        if (currentGroupTitle != null) {
            loadChannelsForGroup(currentGroupTitle!!)
        } else {
            loadChannels()
        }
    }
    
    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            contentRepository.toggleChannelFavorite(channel)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        channelsJob?.cancel()
        categoriesJob?.cancel()
        searchJob?.cancel()
    }
}

/**
 * UI State for Movies screen
 */
data class MoviesUiState(
    val movies: List<Movie> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val recentlyWatched: List<Movie> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * ViewModel for Movies screen
 */
@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {
    
    private val _movies = MutableStateFlow<List<Movie>>(emptyList())
    val movies: StateFlow<List<Movie>> = _movies.asStateFlow()
    
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()
    
    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory: StateFlow<Category?> = _selectedCategory.asStateFlow()
    
    private val _recentlyWatched = MutableStateFlow<List<Movie>>(emptyList())
    val recentlyWatched: StateFlow<List<Movie>> = _recentlyWatched.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Contextual search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()
    
    private var moviesJob: kotlinx.coroutines.Job? = null
    private var categoriesJob: kotlinx.coroutines.Job? = null
    private var recentJob: kotlinx.coroutines.Job? = null
    private var searchJob: kotlinx.coroutines.Job? = null
    
    // Store current genre for search filtering
    private var currentGenre: String? = null
    
    fun loadMovies() {
        // Cancel any existing jobs
        categoriesJob?.cancel()
        moviesJob?.cancel()
        recentJob?.cancel()
        
        _isLoading.value = true
        
        // Load genres as categories on IO thread
        categoriesJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            contentRepository.getDistinctMovieGenres().collect { genreNames ->
                val cats = genreNames.mapIndexed { index, name ->
                    Category(
                        id = index.toLong(),
                        playlistId = 0,
                        name = name,
                        type = ContentType.MOVIE
                    )
                }
                _categories.value = cats
            }
        }
        
        // Load recently watched on IO thread
        recentJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            contentRepository.getRecentlyWatchedMovies(10).collect { movies ->
                _recentlyWatched.value = movies
            }
        }
        
        // Load all movies initially on IO thread
        moviesJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            contentRepository.getAllMovies().collect { movies ->
                _movies.value = movies
                _isLoading.value = false
            }
        }
        
        // Safety timeout
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            if (_isLoading.value) {
                _isLoading.value = false
            }
        }
    }
    
    fun selectCategory(category: Category?) {
        _selectedCategory.value = category
    }
    
    fun loadMoviesForCategory(categoryId: Long) {
        val categories = _categories.value
        if (categoryId in categories.indices.map { it.toLong() }) {
            val genreName = categories[categoryId.toInt()].name
            loadMoviesForGenre(genreName)
        }
    }
    
    fun loadMoviesForGenre(genre: String) {
        // Cancel previous movies job
        moviesJob?.cancel()
        searchJob?.cancel()
        currentGenre = genre
        
        // Clear search when explicitly selecting a genre
        _searchQuery.value = ""
        _isSearchActive.value = false
        
        moviesJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            contentRepository.getMoviesByGenre(genre).collect { movies ->
                _movies.value = movies
                _isLoading.value = false
            }
        }
    }
    
    // Contextual search methods - searches ALL genres
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        _isSearchActive.value = query.isNotBlank()
        
        searchJob?.cancel()
        moviesJob?.cancel()
        
        if (query.isBlank()) {
            // Reload current category or all movies
            if (currentGenre != null) {
                loadMoviesForGenre(currentGenre!!)
            } else {
                reloadAllMovies()
            }
            return
        }
        
        // Debounce search - always search ALL movies
        searchJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(300)
            contentRepository.searchMovies(query).collect { results ->
                _movies.value = results
            }
        }
    }
    
    private fun reloadAllMovies() {
        moviesJob?.cancel()
        moviesJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            contentRepository.getAllMovies().collect { movies ->
                _movies.value = movies
            }
        }
    }
    
    fun clearSearch() {
        _searchQuery.value = ""
        _isSearchActive.value = false
        searchJob?.cancel()
        
        // Reload current content
        if (currentGenre != null) {
            loadMoviesForGenre(currentGenre!!)
        } else {
            reloadAllMovies()
        }
    }
    
    fun toggleFavorite(movie: Movie) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            contentRepository.toggleMovieFavorite(movie)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        moviesJob?.cancel()
        categoriesJob?.cancel()
        recentJob?.cancel()
        searchJob?.cancel()
    }
}

/**
 * UI State for Shows screen
 */
data class ShowsUiState(
    val series: List<Series> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * ViewModel for Shows/Series screen
 */
@HiltViewModel
class ShowsViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {
    
    private val _series = MutableStateFlow<List<Series>>(emptyList())
    val series: StateFlow<List<Series>> = _series.asStateFlow()
    
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()
    
    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory: StateFlow<Category?> = _selectedCategory.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Contextual search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()
    
    private var seriesJob: kotlinx.coroutines.Job? = null
    private var categoriesJob: kotlinx.coroutines.Job? = null
    private var searchJob: kotlinx.coroutines.Job? = null
    
    // Store current genre for search filtering
    private var currentGenre: String? = null
    
    fun loadSeries() {
        // Cancel any existing jobs
        categoriesJob?.cancel()
        seriesJob?.cancel()
        
        _isLoading.value = true
        
        // Load genres as categories on IO thread
        categoriesJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            contentRepository.getDistinctSeriesGenres().collect { genreNames ->
                val cats = genreNames.mapIndexed { index, name ->
                    Category(
                        id = index.toLong(),
                        playlistId = 0,
                        name = name,
                        type = ContentType.SERIES
                    )
                }
                _categories.value = cats
            }
        }
        
        // Load all series initially on IO thread
        seriesJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            contentRepository.getAllSeries().collect { seriesList ->
                _series.value = seriesList
                _isLoading.value = false
            }
        }
        
        // Safety timeout
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            if (_isLoading.value) {
                _isLoading.value = false
            }
        }
    }
    
    fun selectCategory(category: Category?) {
        _selectedCategory.value = category
    }
    
    fun loadSeriesForCategory(categoryId: Long) {
        val categories = _categories.value
        if (categoryId in categories.indices.map { it.toLong() }) {
            val genreName = categories[categoryId.toInt()].name
            loadSeriesForGenre(genreName)
        }
    }
    
    fun loadSeriesForGenre(genre: String) {
        // Cancel previous series job
        seriesJob?.cancel()
        searchJob?.cancel()
        currentGenre = genre
        
        // Clear search when explicitly selecting a genre
        _searchQuery.value = ""
        _isSearchActive.value = false
        
        seriesJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            contentRepository.getSeriesByGenre(genre).collect { seriesList ->
                _series.value = seriesList
                _isLoading.value = false
            }
        }
    }
    
    // Contextual search methods - searches ALL genres
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        _isSearchActive.value = query.isNotBlank()
        
        searchJob?.cancel()
        seriesJob?.cancel()
        
        if (query.isBlank()) {
            // Reload current category or all series
            if (currentGenre != null) {
                loadSeriesForGenre(currentGenre!!)
            } else {
                reloadAllSeries()
            }
            return
        }
        
        // Debounce search - always search ALL series
        searchJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(300)
            contentRepository.searchSeries(query).collect { results ->
                _series.value = results
            }
        }
    }
    
    private fun reloadAllSeries() {
        seriesJob?.cancel()
        seriesJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            contentRepository.getAllSeries().collect { seriesList ->
                _series.value = seriesList
            }
        }
    }
    
    fun clearSearch() {
        _searchQuery.value = ""
        _isSearchActive.value = false
        searchJob?.cancel()
        
        // Reload current content
        if (currentGenre != null) {
            loadSeriesForGenre(currentGenre!!)
        } else {
            reloadAllSeries()
        }
    }
    
    fun toggleFavorite(series: Series) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            contentRepository.toggleSeriesFavorite(series)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        seriesJob?.cancel()
        categoriesJob?.cancel()
        searchJob?.cancel()
    }
}

/**
 * UI State for Playlist management
 */
data class PlaylistUiState(
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val parsingProgress: ParsingProgress? = null
)

/**
 * Progress state for playlist parsing
 */
data class ParsingProgress(
    val status: String = "Downloading...",
    val linesProcessed: Int = 0,
    val channelsFound: Int = 0,
    val moviesFound: Int = 0,
    val seriesFound: Int = 0
)

/**
 * ViewModel for Playlist management
 */
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()
    
    init {
        loadPlaylists()
    }
    
    private fun loadPlaylists() {
        viewModelScope.launch {
            playlistRepository.getAllPlaylists()
                .collect { playlists ->
                    _uiState.update { it.copy(playlists = playlists, isLoading = false) }
                }
        }
    }
    
    fun addM3UPlaylist(name: String, url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            android.util.Log.d("PlaylistViewModel", "Adding M3U playlist: $name, URL: $url")
            
            val result = playlistRepository.addM3UPlaylist(name, url)
            
            result.fold(
                onSuccess = {
                    android.util.Log.d("PlaylistViewModel", "Playlist added successfully")
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            successMessage = "Playlist added successfully"
                        ) 
                    }
                },
                onFailure = { error ->
                    val errorMsg = error.message ?: "Failed to add playlist"
                    android.util.Log.e("PlaylistViewModel", "Failed to add playlist: $errorMsg", error)
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            error = errorMsg
                        ) 
                    }
                }
            )
        }
    }
    
    fun addXtreamPlaylist(name: String, serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val result = playlistRepository.addXtreamPlaylist(name, serverUrl, username, password)
            
            result.fold(
                onSuccess = {
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            successMessage = "Playlist added successfully"
                        ) 
                    }
                },
                onFailure = { error ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            error = error.message ?: "Failed to add playlist"
                        ) 
                    }
                }
            )
        }
    }
    
    fun refreshPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            
            val result = playlistRepository.refreshPlaylist(playlist)
            
            result.fold(
                onSuccess = {
                    _uiState.update { 
                        it.copy(
                            isRefreshing = false, 
                            successMessage = "Playlist updated successfully"
                        ) 
                    }
                },
                onFailure = { error ->
                    _uiState.update { 
                        it.copy(
                            isRefreshing = false, 
                            error = error.message ?: "Failed to update playlist"
                        ) 
                    }
                }
            )
        }
    }
    
    fun refreshAllPlaylists() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            
            val playlists = _uiState.value.playlists
            var hasError = false
            
            playlists.forEach { playlist ->
                val result = playlistRepository.refreshPlaylist(playlist)
                if (result.isFailure) {
                    hasError = true
                }
            }
            
            _uiState.update { 
                it.copy(
                    isRefreshing = false,
                    successMessage = if (!hasError) "All playlists updated" else null,
                    error = if (hasError) "Some playlists failed to update" else null
                ) 
            }
        }
    }
    
    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlist)
            _uiState.update { it.copy(successMessage = "Playlist deleted") }
        }
    }
    
    fun togglePlaylistActive(playlist: Playlist) {
        viewModelScope.launch {
            playlistRepository.setPlaylistActive(playlist, !playlist.isActive)
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }
}
