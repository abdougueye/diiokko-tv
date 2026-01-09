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
 * EPG data for display
 */
data class ChannelEpgData(
    val currentProgram: EpgProgram? = null,
    val nextProgram: EpgProgram? = null,
    val upcomingPrograms: List<EpgProgram> = emptyList()
)

/**
 * ViewModel for TV screen
 */
@HiltViewModel
class TvViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val epgRepository: com.diokko.player.data.repository.EpgRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {
    
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()
    
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()
    
    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory: StateFlow<Category?> = _selectedCategory.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // EPG state
    private val _epgData = MutableStateFlow<Map<Long, ChannelEpgData>>(emptyMap())
    val epgData: StateFlow<Map<Long, ChannelEpgData>> = _epgData.asStateFlow()
    
    private val _isEpgLoading = MutableStateFlow(false)
    val isEpgLoading: StateFlow<Boolean> = _isEpgLoading.asStateFlow()
    
    private val _epgLastUpdated = MutableStateFlow<Long?>(null)
    val epgLastUpdated: StateFlow<Long?> = _epgLastUpdated.asStateFlow()
    
    // Contextual search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()
    
    private var channelsJob: kotlinx.coroutines.Job? = null
    private var categoriesJob: kotlinx.coroutines.Job? = null
    private var searchJob: kotlinx.coroutines.Job? = null
    private var epgJob: kotlinx.coroutines.Job? = null
    
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
                
                // Load EPG data (limited to avoid SQLite variable limit)
                loadEpgLimited()
            }
        }
        
        // Safety timeout
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            if (_isLoading.value) {
                _isLoading.value = false
            }
        }
        
        // Auto-refresh EPG if needed
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            checkAndRefreshEpg()
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
                // Use optimized category-based EPG query
                loadEpgForCategory(categoryId)
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
                // Use optimized group-based EPG query
                loadEpgForGroup(groupTitle)
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
            // Filter out dividers to prevent focusing on non-playable items
            contentRepository.searchChannels(query).collect { results ->
                _channels.value = results.filter { channel ->
                    !channel.isDivider && 
                    !channel.name.startsWith("#####") &&
                    channel.streamUrl.isNotBlank()
                }
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
    
    // === EPG Methods ===
    
    /**
     * Load EPG data for the current group (optimized - uses SQL JOIN)
     */
    private suspend fun loadEpgForGroup(groupTitle: String) {
        epgJob?.cancel()
        
        epgJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Get current programs using optimized JOIN query
                val currentPrograms = epgRepository.getCurrentProgramsForGroup(groupTitle)
                
                // Get upcoming programs using optimized JOIN query
                val upcomingPrograms = epgRepository.getUpcomingProgramsForGroup(groupTitle)
                
                // Build EPG data map
                val allChannelIds = (currentPrograms.keys + upcomingPrograms.keys).toSet()
                val epgMap = allChannelIds.associateWith { channelId ->
                    val current = currentPrograms[channelId]
                    val upcoming = upcomingPrograms[channelId] ?: emptyList()
                    val next = upcoming.firstOrNull { it.startTime > (current?.endTime ?: 0) }
                    
                    ChannelEpgData(
                        currentProgram = current,
                        nextProgram = next,
                        upcomingPrograms = upcoming
                    )
                }
                
                _epgData.value = epgMap
                android.util.Log.d("TvViewModel", "Loaded EPG for group '$groupTitle': ${epgMap.size} channels")
            } catch (e: Exception) {
                android.util.Log.e("TvViewModel", "Failed to load EPG data for group", e)
            }
        }
    }
    
    /**
     * Load EPG data for a category (optimized - uses SQL JOIN)
     */
    private suspend fun loadEpgForCategory(categoryId: Long) {
        epgJob?.cancel()
        
        epgJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Get current programs using optimized JOIN query
                val currentPrograms = epgRepository.getCurrentProgramsForCategory(categoryId)
                
                // Get upcoming programs using optimized JOIN query
                val upcomingPrograms = epgRepository.getUpcomingProgramsForCategory(categoryId)
                
                // Build EPG data map
                val allChannelIds = (currentPrograms.keys + upcomingPrograms.keys).toSet()
                val epgMap = allChannelIds.associateWith { channelId ->
                    val current = currentPrograms[channelId]
                    val upcoming = upcomingPrograms[channelId] ?: emptyList()
                    val next = upcoming.firstOrNull { it.startTime > (current?.endTime ?: 0) }
                    
                    ChannelEpgData(
                        currentProgram = current,
                        nextProgram = next,
                        upcomingPrograms = upcoming
                    )
                }
                
                _epgData.value = epgMap
                android.util.Log.d("TvViewModel", "Loaded EPG for category $categoryId: ${epgMap.size} channels")
            } catch (e: Exception) {
                android.util.Log.e("TvViewModel", "Failed to load EPG data for category", e)
            }
        }
    }
    
    /**
     * Load EPG data for initial view (limited to avoid memory issues)
     */
    private suspend fun loadEpgLimited() {
        epgJob?.cancel()
        
        epgJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Get limited current programs to avoid SQLite variable limit
                val currentPrograms = epgRepository.getCurrentProgramsLimited()
                
                // Build EPG data map (no upcoming for limited load)
                val epgMap = currentPrograms.mapValues { (_, program) ->
                    ChannelEpgData(
                        currentProgram = program,
                        nextProgram = null,
                        upcomingPrograms = emptyList()
                    )
                }
                
                _epgData.value = epgMap
                android.util.Log.d("TvViewModel", "Loaded limited EPG: ${epgMap.size} channels")
            } catch (e: Exception) {
                android.util.Log.e("TvViewModel", "Failed to load limited EPG data", e)
            }
        }
    }
    
    /**
     * Refresh EPG data from server
     */
    fun refreshEpg() {
        android.util.Log.i("TvViewModel", "refreshEpg() called")
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isEpgLoading.value = true
            
            try {
                android.util.Log.i("TvViewModel", "Calling epgRepository.refreshEpgForAllPlaylists()...")
                val result = epgRepository.refreshEpgForAllPlaylists()
                android.util.Log.i("TvViewModel", "EPG refresh result: success=${result.isSuccess}, value=${result.getOrNull()}")
                
                if (result.isSuccess) {
                    _epgLastUpdated.value = System.currentTimeMillis()
                    // Reload EPG based on current view context
                    if (currentGroupTitle != null) {
                        loadEpgForGroup(currentGroupTitle!!)
                    } else {
                        loadEpgLimited()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TvViewModel", "Failed to refresh EPG", e)
            } finally {
                _isEpgLoading.value = false
            }
        }
    }
    
    /**
     * Check if EPG needs refresh and refresh if necessary
     */
    private suspend fun checkAndRefreshEpg() {
        try {
            android.util.Log.i("TvViewModel", "Checking EPG status...")
            // Check if we have any EPG data
            val programCount = epgRepository.getProgramCount()
            android.util.Log.i("TvViewModel", "Current EPG program count: $programCount")
            if (programCount == 0) {
                android.util.Log.i("TvViewModel", "No EPG data found, triggering refresh...")
                refreshEpg()
            } else {
                android.util.Log.i("TvViewModel", "EPG data exists, skipping refresh")
            }
        } catch (e: Exception) {
            android.util.Log.e("TvViewModel", "Error checking EPG status", e)
        }
    }
    
    /**
     * Get EPG data for a specific channel
     */
    fun getEpgForChannel(channelId: Long): ChannelEpgData? {
        return _epgData.value[channelId]
    }
    
    override fun onCleared() {
        super.onCleared()
        channelsJob?.cancel()
        categoriesJob?.cancel()
        searchJob?.cancel()
        epgJob?.cancel()
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
