package com.diokko.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.diokko.player.player.VideoPlayerActivity
import com.diokko.player.ui.components.*
import com.diokko.player.ui.screens.AboutScreen
import com.diokko.player.ui.screens.AddPlaylistScreen
import com.diokko.player.ui.screens.PlaylistsScreen
import com.diokko.player.ui.theme.*
import com.diokko.player.viewmodel.PlaylistViewModel
import com.diokko.player.viewmodel.TvViewModel
import com.diokko.player.viewmodel.MoviesViewModel
import com.diokko.player.viewmodel.ShowsViewModel
import com.diokko.player.viewmodel.SearchViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DiokkoMainContent()
        }
    }
}

sealed class Screen(val route: String, val icon: String, val label: String) {
    object Search : Screen("search", "🔍", "Search")
    object TV : Screen("tv", "📺", "Live TV")
    object Movies : Screen("movies", "🎬", "Movies")
    object Shows : Screen("shows", "📺", "TV Shows")
    object Settings : Screen("settings", "⚙️", "Settings")
    object Playlists : Screen("playlists", "📋", "Playlists")
    object AddPlaylist : Screen("add_playlist", "➕", "Add Playlist")
    object About : Screen("about", "ℹ️", "About")
}

// Focus level: 0 = nav rail, 1 = categories, 2 = content
enum class FocusLevel { NAV_RAIL, CATEGORIES, CONTENT }

@Composable
fun DiokkoMainContent() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.TV) }
    var focusLevel by remember { mutableStateOf(FocusLevel.NAV_RAIL) }
    
    // Hoist ViewModels to prevent re-initialization when switching screens
    val playlistViewModel: PlaylistViewModel = hiltViewModel()
    val tvViewModel: TvViewModel = hiltViewModel()
    val moviesViewModel: MoviesViewModel = hiltViewModel()
    val showsViewModel: ShowsViewModel = hiltViewModel()
    val searchViewModel: SearchViewModel = hiltViewModel()
    
    // Hoist LazyListStates to preserve scroll position when switching screens
    val tvCategoryListState = rememberLazyListState()
    val tvChannelListState = rememberLazyListState()
    val moviesCategoryListState = rememberLazyListState()
    val moviesContentListState = rememberLazyListState()
    val showsCategoryListState = rememberLazyListState()
    val showsContentListState = rememberLazyListState()
    
    // Handle Back button - navigate back through focus levels before exiting
    BackHandler(enabled = focusLevel != FocusLevel.NAV_RAIL || currentScreen !in listOf(Screen.Search, Screen.TV, Screen.Movies, Screen.Shows, Screen.Settings)) {
        when {
            // If in sub-screens, go back to parent
            currentScreen == Screen.AddPlaylist -> currentScreen = Screen.Playlists
            currentScreen == Screen.Playlists -> currentScreen = Screen.Settings
            currentScreen == Screen.About -> currentScreen = Screen.Settings
            currentScreen == Screen.Search -> currentScreen = Screen.TV
            // For Settings, go directly to NAV_RAIL (no categories)
            currentScreen == Screen.Settings && focusLevel == FocusLevel.CONTENT -> focusLevel = FocusLevel.NAV_RAIL
            // Navigate back through focus levels for content screens
            focusLevel == FocusLevel.CONTENT -> focusLevel = FocusLevel.CATEGORIES
            focusLevel == FocusLevel.CATEGORIES -> focusLevel = FocusLevel.NAV_RAIL
        }
    }
    
    // Collect categories state to check if screens have content
    val tvCategories by tvViewModel.categories.collectAsState()
    val moviesCategories by moviesViewModel.categories.collectAsState()
    val showsCategories by showsViewModel.categories.collectAsState()
    
    // Check if current screen has content
    val currentScreenHasContent = when (currentScreen) {
        Screen.TV -> tvCategories.isNotEmpty()
        Screen.Movies -> moviesCategories.isNotEmpty()
        Screen.Shows -> showsCategories.isNotEmpty()
        else -> false
    }
    
    // Safety: Reset focus to NAV_RAIL only for content screens without data
    // Settings and Search handle their own focus management
    LaunchedEffect(currentScreen, currentScreenHasContent, focusLevel) {
        val isContentScreen = currentScreen in listOf(Screen.TV, Screen.Movies, Screen.Shows)
        
        // Only reset for content screens (TV/Movies/Shows) that have no data
        // Don't interfere with Settings or Search focus handling
        if (isContentScreen && !currentScreenHasContent && focusLevel != FocusLevel.NAV_RAIL) {
            focusLevel = FocusLevel.NAV_RAIL
        }
    }
    
    // Check if the middle panel should actually exist
    val shouldShowMiddlePanel = currentScreen in listOf(Screen.TV, Screen.Movies, Screen.Shows) && currentScreenHasContent
    
    // Column widths based on focus level - no animation
    // Keep nav rail at minimum 72dp so it's always a valid focus target
    val navRailWidth = when (focusLevel) {
        FocusLevel.NAV_RAIL -> 200.dp
        else -> 72.dp
    }
    
    // Categories width - 0 when no content or at content level
    val categoriesWidth = when {
        !shouldShowMiddlePanel -> 0.dp // If no data, collapse the middle panel entirely
        focusLevel == FocusLevel.NAV_RAIL -> 200.dp
        focusLevel == FocusLevel.CATEGORIES -> 200.dp
        focusLevel == FocusLevel.CONTENT -> 0.dp
        else -> 0.dp
    }
    
    // Nav rail is always shown (at least collapsed) so focus can return to it
    val showNavRail = true
    // Categories only shown if there's content
    val showCategories = shouldShowMiddlePanel
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DiokkoColors.Background)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Navigation Rail
            if (showNavRail) {
                CollapsibleNavRail(
                    currentScreen = currentScreen,
                    focusLevel = focusLevel,
                    width = navRailWidth,
                    onScreenSelected = { 
                        currentScreen = it
                    },
                    onFocusRight = {
                        // For content screens with categories, go to CATEGORIES
                        if (currentScreen in listOf(Screen.TV, Screen.Movies, Screen.Shows) && currentScreenHasContent) {
                            focusLevel = FocusLevel.CATEGORIES
                        }
                        // For Settings, go directly to CONTENT (no categories)
                        else if (currentScreen == Screen.Settings) {
                            focusLevel = FocusLevel.CONTENT
                        }
                    }
                )
            }
            
            // Main Content
            val context = LocalContext.current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(
                        start = if (showNavRail) DiokkoDimens.spacingMd else 0.dp,
                        end = DiokkoDimens.spacingMd,
                        top = DiokkoDimens.spacingSm,
                        bottom = DiokkoDimens.spacingSm
                    )
            ) {
                when (currentScreen) {
                    Screen.Search -> GlobalSearchScreen(
                        viewModel = searchViewModel,
                        onChannelClick = { channel ->
                            val intent = android.content.Intent(context, VideoPlayerActivity::class.java).apply {
                                putExtra("STREAM_URL", channel.streamUrl)
                                putExtra("STREAM_TITLE", channel.name)
                            }
                            context.startActivity(intent)
                        },
                        onMovieClick = { movie ->
                            val intent = android.content.Intent(context, VideoPlayerActivity::class.java).apply {
                                putExtra("STREAM_URL", movie.streamUrl)
                                putExtra("STREAM_TITLE", movie.name)
                            }
                            context.startActivity(intent)
                        },
                        onSeriesClick = { series ->
                            android.widget.Toast.makeText(context, "Opening: ${series.name}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                    Screen.TV -> TvScreenContent(
                        viewModel = tvViewModel,
                        focusLevel = focusLevel,
                        onFocusLevelChange = { focusLevel = it },
                        showCategories = showCategories,
                        categoriesWidth = categoriesWidth,
                        categoryListState = tvCategoryListState,
                        channelListState = tvChannelListState
                    )
                    Screen.Movies -> MoviesScreenContent(
                        viewModel = moviesViewModel,
                        focusLevel = focusLevel,
                        onFocusLevelChange = { focusLevel = it },
                        showCategories = showCategories,
                        categoriesWidth = categoriesWidth,
                        categoryListState = moviesCategoryListState,
                        contentListState = moviesContentListState
                    )
                    Screen.Shows -> ShowsScreenContent(
                        viewModel = showsViewModel,
                        focusLevel = focusLevel,
                        onFocusLevelChange = { focusLevel = it },
                        showCategories = showCategories,
                        categoriesWidth = categoriesWidth,
                        categoryListState = showsCategoryListState,
                        contentListState = showsContentListState
                    )
                    Screen.Settings -> SettingsScreenContent(
                        focusLevel = focusLevel,
                        onFocusLevelChange = { focusLevel = it },
                        onPlaylistsClick = { currentScreen = Screen.Playlists },
                        onAboutClick = { currentScreen = Screen.About }
                    )
                    Screen.Playlists -> PlaylistsScreen(
                        viewModel = playlistViewModel,
                        onAddPlaylist = { currentScreen = Screen.AddPlaylist },
                        onBack = { currentScreen = Screen.Settings }
                    )
                    Screen.AddPlaylist -> AddPlaylistScreen(
                        viewModel = playlistViewModel,
                        onBack = { currentScreen = Screen.Playlists }
                    )
                    Screen.About -> AboutScreen(
                        onBack = { currentScreen = Screen.Settings }
                    )
                }
            }
        }
    }
}

@Composable
fun CollapsibleNavRail(
    currentScreen: Screen,
    focusLevel: FocusLevel,
    width: Dp,
    onScreenSelected: (Screen) -> Unit,
    onFocusRight: () -> Unit
) {
    val isCollapsed = focusLevel != FocusLevel.NAV_RAIL
    val mainScreens = listOf(Screen.Search, Screen.TV, Screen.Movies, Screen.Shows, Screen.Settings)
    val focusRequesters = remember { mainScreens.map { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(1) } // Default to TV (index 1)
    
    // Request focus when returning to nav rail
    LaunchedEffect(focusLevel) {
        if (focusLevel == FocusLevel.NAV_RAIL) {
            val currentIndex = mainScreens.indexOf(currentScreen).coerceAtLeast(0)
            focusedIndex = currentIndex
            // Increased delay for more reliable focus
            kotlinx.coroutines.delay(100)
            try { focusRequesters[currentIndex].requestFocus() } catch (e: Exception) {}
        }
    }
    
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(DiokkoColors.Surface)
            .padding(vertical = DiokkoDimens.spacingMd),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Logo
        Box(
            modifier = Modifier
                .padding(vertical = DiokkoDimens.spacingSm)
                .size(if (isCollapsed) 36.dp else 44.dp)
                .clip(DiokkoShapes.small)
                .background(DiokkoColors.Accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "D",
                style = DiokkoTypography.headlineSmall.copy(
                    color = DiokkoColors.TextOnAccent,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        
        Spacer(modifier = Modifier.height(DiokkoDimens.spacingMd))
        
        // Nav items
        mainScreens.forEachIndexed { index, screen ->
            val isSelected = screen == currentScreen
            val isFocused = index == focusedIndex && focusLevel == FocusLevel.NAV_RAIL
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .clip(DiokkoShapes.small)
                    .background(
                        when {
                            isFocused -> DiokkoColors.Accent
                            isSelected -> DiokkoColors.SurfaceLight
                            else -> Color.Transparent
                        }
                    )
                    .focusRequester(focusRequesters[index])
                    // Only focusable when NavRail is active - prevents invisible focus targets
                    .focusable(enabled = focusLevel == FocusLevel.NAV_RAIL)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && focusLevel == FocusLevel.NAV_RAIL) {
                            when (event.key) {
                                Key.DirectionDown -> {
                                    if (index < mainScreens.size - 1) {
                                        focusedIndex = index + 1
                                        focusRequesters[index + 1].requestFocus()
                                    }
                                    true
                                }
                                Key.DirectionUp -> {
                                    if (index > 0) {
                                        focusedIndex = index - 1
                                        focusRequesters[index - 1].requestFocus()
                                    }
                                    true
                                }
                                Key.DirectionRight -> {
                                    // Right: Move to content of CURRENT screen only
                                    // Don't switch screens - that's what Enter is for
                                    if (currentScreen in listOf(Screen.TV, Screen.Movies, Screen.Shows, Screen.Settings)) {
                                        onFocusRight()
                                    }
                                    true
                                }
                                Key.Enter, Key.DirectionCenter -> {
                                    // Enter: Select this screen, then move to content
                                    onScreenSelected(screen)
                                    if (screen in listOf(Screen.TV, Screen.Movies, Screen.Shows, Screen.Settings)) {
                                        onFocusRight()
                                    }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = if (isCollapsed) Alignment.Center else Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = screen.icon, fontSize = 18.sp)
                    if (!isCollapsed) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = screen.label,
                            style = DiokkoTypography.bodyMedium,
                            color = if (isFocused) DiokkoColors.TextOnAccent 
                                   else if (isSelected) DiokkoColors.TextPrimary 
                                   else DiokkoColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TvScreenContent(
    viewModel: TvViewModel,
    focusLevel: FocusLevel,
    onFocusLevelChange: (FocusLevel) -> Unit,
    showCategories: Boolean,
    categoriesWidth: Dp,
    categoryListState: LazyListState,
    channelListState: LazyListState
) {
    val context = LocalContext.current
    val channels by viewModel.channels.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val categoryFocusRequester = remember { FocusRequester() }
    val epgFocusRequester = remember { FocusRequester() }
    
    // Use rememberSaveable to preserve state across recompositions
    var selectedCategoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedChannelIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedTimeSlot by rememberSaveable { mutableIntStateOf(0) }
    
    // Track previous focus level to only trigger on TRANSITION
    var previousFocusLevel by remember { mutableStateOf(focusLevel) }
    
    // Track if search bar has focus
    var searchBarHasFocus by remember { mutableStateOf(false) }
    
    // Time slots (30-min intervals, showing 5 slots)
    val timeSlots = remember {
        val calendar = java.util.Calendar.getInstance()
        val minute = calendar.get(java.util.Calendar.MINUTE)
        // Round down to nearest 30 min
        calendar.set(java.util.Calendar.MINUTE, if (minute < 30) 0 else 30)
        calendar.set(java.util.Calendar.SECOND, 0)
        
        (0..4).map { i ->
            val slotCal = calendar.clone() as java.util.Calendar
            slotCal.add(java.util.Calendar.MINUTE, i * 30)
            String.format("%02d:%02d", slotCal.get(java.util.Calendar.HOUR_OF_DAY), slotCal.get(java.util.Calendar.MINUTE))
        }
    }
    
    // Load channels only once when screen first appears
    LaunchedEffect(Unit) {
        if (channels.isEmpty()) {
            viewModel.loadChannels()
        }
    }
    
    // Only request focus when focusLevel TRANSITIONS, not when already there
    LaunchedEffect(focusLevel) {
        if (focusLevel != previousFocusLevel) {
            when (focusLevel) {
                FocusLevel.CATEGORIES -> {
                    kotlinx.coroutines.delay(50)
                    if (categories.isNotEmpty() && selectedCategoryIndex in categories.indices) {
                        categoryListState.animateScrollToItem(selectedCategoryIndex)
                    }
                    try { categoryFocusRequester.requestFocus() } catch (e: Exception) {}
                }
                FocusLevel.CONTENT -> {
                    kotlinx.coroutines.delay(100)
                    try { epgFocusRequester.requestFocus() } catch (e: Exception) {}
                }
                else -> {}
            }
            previousFocusLevel = focusLevel
        }
    }
    
    LaunchedEffect(selectedCategoryIndex) {
        if (categories.isNotEmpty() && selectedCategoryIndex in categories.indices) {
            categoryListState.animateScrollToItem(selectedCategoryIndex)
        }
    }
    
    // Collect search state
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val searchFocusRequester = remember { FocusRequester() }
    
    // Reset channel index when channels list changes (e.g., after search)
    LaunchedEffect(channels) {
        if (selectedChannelIndex >= channels.size) {
            selectedChannelIndex = 0
        }
    }
    
    LaunchedEffect(selectedCategoryIndex, categories) {
        if (categories.isNotEmpty() && selectedCategoryIndex in categories.indices) {
            viewModel.loadChannelsForGroup(categories[selectedCategoryIndex].name)
            selectedChannelIndex = 0
            selectedTimeSlot = 0
        }
    }
    
    LaunchedEffect(selectedChannelIndex) {
        if (channels.isNotEmpty() && selectedChannelIndex in channels.indices) {
            channelListState.scrollToItem(selectedChannelIndex)
        }
    }
    
    fun playChannel(url: String, title: String) {
        val intent = Intent(context, VideoPlayerActivity::class.java).apply {
            putExtra(VideoPlayerActivity.EXTRA_URL, url)
            putExtra(VideoPlayerActivity.EXTRA_TITLE, title)
        }
        context.startActivity(intent)
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Title bar with contextual search
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = DiokkoDimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "📺", fontSize = 24.sp)
            Spacer(modifier = Modifier.width(DiokkoDimens.spacingSm))
            Text(text = "Live TV", style = DiokkoTypography.headlineMedium)
            Spacer(modifier = Modifier.width(DiokkoDimens.spacingMd))
            if (categories.isNotEmpty() && selectedCategoryIndex in categories.indices) {
                Text(
                    text = "• ${categories[selectedCategoryIndex].name}",
                    style = DiokkoTypography.bodyMedium,
                    color = DiokkoColors.TextSecondary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            
            // Contextual search bar
            com.diokko.player.ui.components.ContextualSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onClear = { viewModel.clearSearch() },
                onNavigateDown = {
                    // Navigate to content grid (EPG) where search results are shown
                    searchBarHasFocus = false
                    if (channels.isNotEmpty()) {
                        onFocusLevelChange(FocusLevel.CONTENT)
                    } else {
                        try { categoryFocusRequester.requestFocus() } catch (e: Exception) {}
                    }
                },
                categoryName = if (categories.isNotEmpty() && selectedCategoryIndex in categories.indices) 
                    categories[selectedCategoryIndex].name else null,
                modifier = Modifier
                    .width(250.dp)
                    .onFocusChanged { searchBarHasFocus = it.isFocused },
                focusRequester = searchFocusRequester
            )
            
            Spacer(modifier = Modifier.width(DiokkoDimens.spacingMd))
            Text(
                text = "${channels.size} channels",
                style = DiokkoTypography.bodySmall,
                color = DiokkoColors.TextSecondary
            )
        }
        
        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = "Loading channels...", style = DiokkoTypography.bodyMedium)
            }
        } else if (categories.isEmpty()) {
            // Show simple empty state - no button, focus stays on nav rail
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = "📡",
                    title = "No Channels Yet",
                    message = "Go to Settings to add a playlist"
                )
            }
        } else {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Categories sidebar - always rendered for focus management
                Column(
                    modifier = Modifier
                        .width(categoriesWidth)
                        .fillMaxHeight()
                        .background(if (categoriesWidth > 0.dp) DiokkoColors.Surface else Color.Transparent)
                        .padding(if (categoriesWidth > 0.dp) DiokkoDimens.spacingXs else 0.dp)
                ) {
                    if (categoriesWidth > 0.dp) {
                        Text(
                            text = "Categories",
                            style = DiokkoTypography.labelMedium,
                            color = DiokkoColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = DiokkoDimens.spacingSm, vertical = DiokkoDimens.spacingXs)
                        )
                    }
                    
                    // LazyColumn always rendered for focus requester
                    LazyColumn(
                        state = categoryListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(categoryFocusRequester)
                            // Only focusable when CATEGORIES is active - prevents invisible focus
                            .focusable(enabled = focusLevel == FocusLevel.CATEGORIES)
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && focusLevel == FocusLevel.CATEGORIES) {
                                    when (event.key) {
                                        Key.DirectionDown -> {
                                            if (selectedCategoryIndex < categories.size - 1) selectedCategoryIndex++
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            if (selectedCategoryIndex > 0) {
                                                selectedCategoryIndex--
                                                true
                                            } else {
                                                // At first category, move to search bar
                                                try { searchFocusRequester.requestFocus() } catch (e: Exception) {}
                                                true
                                            }
                                        }
                                        Key.DirectionLeft -> {
                                            onFocusLevelChange(FocusLevel.NAV_RAIL)
                                            true
                                        }
                                        Key.DirectionRight, Key.Enter, Key.DirectionCenter -> {
                                            if (channels.isNotEmpty()) {
                                                onFocusLevelChange(FocusLevel.CONTENT)
                                            }
                                            true
                                        }
                                        // Menu key shortcut to jump to search from any category
                                        Key.Menu -> {
                                            try { searchFocusRequester.requestFocus() } catch (e: Exception) {}
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    ) {
                        if (categoriesWidth > 0.dp) {
                            itemsIndexed(categories) { index, category ->
                                val isSelected = index == selectedCategoryIndex && focusLevel == FocusLevel.CATEGORIES
                                val isActive = index == selectedCategoryIndex
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(DiokkoShapes.small)
                                        .background(
                                            when {
                                                isSelected -> DiokkoColors.Accent
                                                isActive -> DiokkoColors.SurfaceLight
                                                else -> Color.Transparent
                                            }
                                        )
                                        .padding(horizontal = DiokkoDimens.spacingSm, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = category.name,
                                        style = DiokkoTypography.bodySmall,
                                        color = if (isSelected) DiokkoColors.TextOnAccent 
                                               else if (isActive) DiokkoColors.TextPrimary 
                                               else DiokkoColors.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (categoriesWidth > 0.dp) {
                    Spacer(modifier = Modifier.width(DiokkoDimens.spacingSm))
                }
                
                // EPG Grid
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (channels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "No channels in this category", style = DiokkoTypography.bodyMedium, color = DiokkoColors.TextSecondary)
                        }
                    } else {
                        // Filter out dividers for EPG
                        val epgChannels = channels.filter { !it.isDivider }
                        
                        // Time bar header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DiokkoColors.Surface)
                                .padding(vertical = 4.dp)
                        ) {
                            // Channel column header
                            Box(
                                modifier = Modifier.width(160.dp).padding(horizontal = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "Channel",
                                    style = DiokkoTypography.labelSmall,
                                    color = DiokkoColors.TextSecondary
                                )
                            }
                            
                            // Time slots
                            timeSlots.forEach { time ->
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = time,
                                        style = DiokkoTypography.labelSmall,
                                        color = DiokkoColors.Accent
                                    )
                                }
                            }
                        }
                        
                        // Channel rows with program grid
                        LazyColumn(
                            state = channelListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(epgFocusRequester)
                                // Only focusable when CONTENT is active
                                .focusable(enabled = focusLevel == FocusLevel.CONTENT)
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && focusLevel == FocusLevel.CONTENT) {
                                        when (event.key) {
                                            Key.DirectionDown -> {
                                                if (selectedChannelIndex < epgChannels.size - 1) selectedChannelIndex++
                                                true
                                            }
                                            Key.DirectionUp -> {
                                                if (selectedChannelIndex > 0) {
                                                    selectedChannelIndex--
                                                    true
                                                } else {
                                                    // At top of content, jump to search bar
                                                    onFocusLevelChange(FocusLevel.CATEGORIES)
                                                    try { searchFocusRequester.requestFocus() } catch (e: Exception) {}
                                                    true
                                                }
                                            }
                                            Key.DirectionRight -> {
                                                if (selectedTimeSlot < timeSlots.size - 1) selectedTimeSlot++
                                                true
                                            }
                                            Key.DirectionLeft -> {
                                                if (selectedTimeSlot > 0) {
                                                    selectedTimeSlot--
                                                } else {
                                                    // Immediately request focus on categories to prevent search bar from intercepting
                                                    onFocusLevelChange(FocusLevel.CATEGORIES)
                                                    try { categoryFocusRequester.requestFocus() } catch (e: Exception) {}
                                                }
                                                true
                                            }
                                            Key.Enter, Key.DirectionCenter -> {
                                                if (epgChannels.isNotEmpty() && selectedChannelIndex in epgChannels.indices) {
                                                    val channel = epgChannels[selectedChannelIndex]
                                                    playChannel(channel.streamUrl, channel.name)
                                                }
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
                        ) {
                            itemsIndexed(epgChannels) { index, channel ->
                                val isChannelSelected = index == selectedChannelIndex && focusLevel == FocusLevel.CONTENT
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isChannelSelected) DiokkoColors.SurfaceLight.copy(alpha = 0.5f)
                                            else Color.Transparent
                                        )
                                        .padding(vertical = 2.dp)
                                ) {
                                    // Channel info column
                                    Row(
                                        modifier = Modifier
                                            .width(160.dp)
                                            .clip(DiokkoShapes.small)
                                            .background(
                                                if (isChannelSelected && selectedTimeSlot == -1) DiokkoColors.Accent
                                                else DiokkoColors.Surface
                                            )
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Channel logo
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(DiokkoShapes.small)
                                                .background(DiokkoColors.SurfaceLight),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!channel.logoUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = channel.logoUrl,
                                                    contentDescription = channel.name,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            } else {
                                                Text(text = "📺", fontSize = 14.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = channel.name,
                                            style = DiokkoTypography.bodySmall,
                                            color = DiokkoColors.TextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    
                                    // Program slots
                                    timeSlots.forEachIndexed { slotIndex, _ ->
                                        val isSlotSelected = isChannelSelected && slotIndex == selectedTimeSlot
                                        
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 1.dp)
                                                .clip(DiokkoShapes.small)
                                                .background(
                                                    when {
                                                        isSlotSelected -> DiokkoColors.Accent
                                                        isChannelSelected -> DiokkoColors.Surface
                                                        else -> DiokkoColors.Surface.copy(alpha = 0.5f)
                                                    }
                                                )
                                                .border(
                                                    width = if (isSlotSelected) 2.dp else 0.dp,
                                                    color = if (isSlotSelected) DiokkoColors.Accent else Color.Transparent,
                                                    shape = DiokkoShapes.small
                                                )
                                                .padding(horizontal = 6.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = "No info",
                                                style = DiokkoTypography.labelSmall,
                                                color = if (isSlotSelected) DiokkoColors.TextOnAccent 
                                                       else DiokkoColors.TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Navigation hints
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DiokkoColors.Surface)
                                .padding(vertical = 6.dp, horizontal = DiokkoDimens.spacingMd),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Text(text = "▲▼ Channel", style = DiokkoTypography.labelSmall, color = DiokkoColors.TextSecondary)
                            Text(text = "◀▶ Time", style = DiokkoTypography.labelSmall, color = DiokkoColors.TextSecondary)
                            Text(text = "OK = Play", style = DiokkoTypography.labelSmall, color = DiokkoColors.TextSecondary)
                            Text(text = "← Back", style = DiokkoTypography.labelSmall, color = DiokkoColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}




@Composable
fun MoviesScreenContent(
    viewModel: MoviesViewModel,
    focusLevel: FocusLevel,
    showCategories: Boolean,
    categoriesWidth: Dp,
    categoryListState: LazyListState,
    contentListState: LazyListState,
    onFocusLevelChange: (FocusLevel) -> Unit
) {
    val context = LocalContext.current
    val movies by viewModel.movies.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val categoryFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    
    // Use rememberSaveable to preserve state across recompositions
    var selectedCategoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var contentFocusedIndex by rememberSaveable { mutableIntStateOf(0) }
    
    // Track if initial load is done
    var initialLoadDone by remember { mutableStateOf(false) }
    
    // Track if search bar has focus (to prevent category focus from stealing it)
    var searchBarHasFocus by remember { mutableStateOf(false) }
    
    // Load movies only once when screen first appears
    LaunchedEffect(Unit) {
        if (movies.isEmpty() && categories.isEmpty()) {
            viewModel.loadMovies()
        }
    }
    
    // Request focus with increased delay for reliability
    // Only request category focus if search bar doesn't have focus
    LaunchedEffect(focusLevel, searchBarHasFocus) {
        when (focusLevel) {
            FocusLevel.CATEGORIES -> {
                if (!searchBarHasFocus) {
                    kotlinx.coroutines.delay(50)
                    // Scroll to selected category to ensure it's visible
                    if (categories.isNotEmpty() && selectedCategoryIndex in categories.indices) {
                        categoryListState.animateScrollToItem(selectedCategoryIndex)
                    }
                    try { categoryFocusRequester.requestFocus() } catch (e: Exception) {}
                }
            }
            FocusLevel.CONTENT -> {
                kotlinx.coroutines.delay(100)
                try { contentFocusRequester.requestFocus() } catch (e: Exception) {}
            }
            else -> {}
        }
    }
    
    LaunchedEffect(selectedCategoryIndex) {
        if (categories.isNotEmpty() && selectedCategoryIndex in categories.indices) {
            categoryListState.animateScrollToItem(selectedCategoryIndex)
        }
    }
    
    // Reset focused index when movies list changes (e.g., after search)
    LaunchedEffect(movies) {
        if (contentFocusedIndex >= movies.size) {
            contentFocusedIndex = 0
        }
    }
    
    // Collect search state
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val searchFocusRequester = remember { FocusRequester() }
    
    // Only load genre when category changes AFTER initial load
    LaunchedEffect(selectedCategoryIndex) {
        if (initialLoadDone && categories.isNotEmpty() && selectedCategoryIndex in categories.indices) {
            viewModel.loadMoviesForGenre(categories[selectedCategoryIndex].name)
            contentFocusedIndex = 0
        }
    }
    
    // Mark initial load done after categories are loaded
    LaunchedEffect(categories) {
        if (categories.isNotEmpty() && !initialLoadDone) {
            initialLoadDone = true
        }
    }
    
    LaunchedEffect(contentFocusedIndex, movies) {
        if (movies.isNotEmpty() && contentFocusedIndex in movies.indices) {
            contentListState.scrollToItem(contentFocusedIndex / 4)
        }
    }
    
    fun playMovie(url: String, title: String) {
        val intent = Intent(context, VideoPlayerActivity::class.java).apply {
            putExtra(VideoPlayerActivity.EXTRA_URL, url)
            putExtra(VideoPlayerActivity.EXTRA_TITLE, title)
        }
        context.startActivity(intent)
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Title bar with contextual search
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = DiokkoDimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🎬", fontSize = 24.sp)
            Spacer(modifier = Modifier.width(DiokkoDimens.spacingSm))
            Text(text = "Movies", style = DiokkoTypography.headlineMedium)
            Spacer(modifier = Modifier.width(DiokkoDimens.spacingMd))
            if (categories.isNotEmpty() && selectedCategoryIndex in categories.indices) {
                Text(
                    text = "• ${categories[selectedCategoryIndex].name}",
                    style = DiokkoTypography.bodyMedium,
                    color = DiokkoColors.TextSecondary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            
            // Contextual search bar
            com.diokko.player.ui.components.ContextualSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onClear = { viewModel.clearSearch() },
                onNavigateDown = {
                    // Navigate to content grid where search results are shown
                    searchBarHasFocus = false
                    if (movies.isNotEmpty()) {
                        onFocusLevelChange(FocusLevel.CONTENT)
                    } else {
                        try { categoryFocusRequester.requestFocus() } catch (e: Exception) {}
                    }
                },
                categoryName = if (categories.isNotEmpty() && selectedCategoryIndex in categories.indices) 
                    categories[selectedCategoryIndex].name else null,
                modifier = Modifier
                    .width(250.dp)
                    .onFocusChanged { searchBarHasFocus = it.isFocused },
                focusRequester = searchFocusRequester
            )
            
            Spacer(modifier = Modifier.width(DiokkoDimens.spacingMd))
            Text(
                text = "${movies.size} movies",
                style = DiokkoTypography.bodySmall,
                color = DiokkoColors.TextSecondary
            )
        }
        
        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = "Loading movies...", style = DiokkoTypography.bodyMedium)
            }
        } else if (categories.isEmpty()) {
            // Show simple empty state - no button, focus stays on nav rail
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = "🎬",
                    title = "No Movies Yet",
                    message = "Go to Settings to add a playlist"
                )
            }
        } else {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Categories sidebar - always rendered for focus management
                Column(
                    modifier = Modifier
                        .width(categoriesWidth)
                        .fillMaxHeight()
                        .background(if (categoriesWidth > 0.dp) DiokkoColors.Surface else Color.Transparent)
                        .padding(if (categoriesWidth > 0.dp) DiokkoDimens.spacingXs else 0.dp)
                ) {
                    if (categoriesWidth > 0.dp) {
                        Text(
                            text = "Genres",
                            style = DiokkoTypography.labelMedium,
                            color = DiokkoColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = DiokkoDimens.spacingSm, vertical = DiokkoDimens.spacingXs)
                        )
                    }
                    
                    // LazyColumn always rendered for focus requester
                    LazyColumn(
                        state = categoryListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(categoryFocusRequester)
                            // Only focusable when CATEGORIES is active
                            .focusable(enabled = focusLevel == FocusLevel.CATEGORIES)
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && focusLevel == FocusLevel.CATEGORIES) {
                                    when (event.key) {
                                        Key.DirectionDown -> {
                                            if (selectedCategoryIndex < categories.size - 1) selectedCategoryIndex++
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            if (selectedCategoryIndex > 0) {
                                                selectedCategoryIndex--
                                                true
                                            } else {
                                                // At first category, move to search bar
                                                try { searchFocusRequester.requestFocus() } catch (e: Exception) {}
                                                true
                                            }
                                        }
                                        Key.DirectionLeft -> {
                                            onFocusLevelChange(FocusLevel.NAV_RAIL)
                                            true
                                        }
                                        Key.DirectionRight, Key.Enter, Key.DirectionCenter -> {
                                            if (movies.isNotEmpty()) {
                                                onFocusLevelChange(FocusLevel.CONTENT)
                                            }
                                            true
                                        }
                                        // Menu key shortcut to jump to search from any category
                                        Key.Menu -> {
                                            try { searchFocusRequester.requestFocus() } catch (e: Exception) {}
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    ) {
                        if (categoriesWidth > 0.dp) {
                            itemsIndexed(categories) { index, category ->
                                val isSelected = index == selectedCategoryIndex && focusLevel == FocusLevel.CATEGORIES
                                val isActive = index == selectedCategoryIndex
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(DiokkoShapes.small)
                                        .background(
                                            when {
                                                isSelected -> DiokkoColors.Accent
                                                isActive -> DiokkoColors.SurfaceLight
                                                else -> Color.Transparent
                                            }
                                        )
                                        .padding(horizontal = DiokkoDimens.spacingSm, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = category.name,
                                        style = DiokkoTypography.bodySmall,
                                        color = if (isSelected) DiokkoColors.TextOnAccent 
                                               else if (isActive) DiokkoColors.TextPrimary 
                                               else DiokkoColors.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (categoriesWidth > 0.dp) {
                    Spacer(modifier = Modifier.width(DiokkoDimens.spacingSm))
                }
                
                // Movies Grid
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (movies.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "No movies in this genre", style = DiokkoTypography.bodyMedium, color = DiokkoColors.TextSecondary)
                        }
                    } else {
                        // Pre-calculate chunked list to avoid recalculation on each recomposition
                        val chunkedMovies = remember(movies) { movies.chunked(4) }
                        
                        LazyColumn(
                            state = contentListState,
                            contentPadding = PaddingValues(top = 8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(contentFocusRequester)
                                // Only focusable when CONTENT is active
                                .focusable(enabled = focusLevel == FocusLevel.CONTENT)
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && focusLevel == FocusLevel.CONTENT) {
                                        val cols = 4
                                        when (event.key) {
                                            Key.DirectionDown -> {
                                                if (contentFocusedIndex + cols < movies.size) contentFocusedIndex += cols
                                                true
                                            }
                                            Key.DirectionUp -> {
                                                if (contentFocusedIndex - cols >= 0) {
                                                    contentFocusedIndex -= cols
                                                    true
                                                } else {
                                                    // At top row of content, jump to search bar
                                                    onFocusLevelChange(FocusLevel.CATEGORIES)
                                                    try { searchFocusRequester.requestFocus() } catch (e: Exception) {}
                                                    true
                                                }
                                            }
                                            Key.DirectionRight -> {
                                                if ((contentFocusedIndex + 1) % cols != 0 && contentFocusedIndex < movies.size - 1) {
                                                    contentFocusedIndex++
                                                }
                                                true
                                            }
                                            Key.DirectionLeft -> {
                                                if (contentFocusedIndex % cols == 0) {
                                                    // Immediately request focus on categories to prevent search bar from intercepting
                                                    onFocusLevelChange(FocusLevel.CATEGORIES)
                                                    try { categoryFocusRequester.requestFocus() } catch (e: Exception) {}
                                                } else {
                                                    contentFocusedIndex--
                                                }
                                                true
                                            }
                                            Key.Enter, Key.DirectionCenter -> {
                                                if (movies.isNotEmpty() && contentFocusedIndex in movies.indices) {
                                                    val movie = movies[contentFocusedIndex]
                                                    playMovie(movie.streamUrl, movie.name)
                                                }
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                },
                            verticalArrangement = Arrangement.spacedBy(DiokkoDimens.spacingSm)
                        ) {
                            itemsIndexed(chunkedMovies) { rowIndex, row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(DiokkoDimens.spacingSm)
                                ) {
                                    row.forEachIndexed { colIndex, movie ->
                                        val absoluteIndex = rowIndex * 4 + colIndex
                                        val isSelected = absoluteIndex == contentFocusedIndex && focusLevel == FocusLevel.CONTENT
                                        
                                        ContentCard(
                                            title = movie.name,
                                            subtitle = movie.genre ?: "",
                                            imageUrl = movie.posterUrl,
                                            isFocused = isSelected,
                                            modifier = Modifier.weight(1f),
                                            onClick = { playMovie(movie.streamUrl, movie.name) }
                                        )
                                    }
                                    repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShowsScreenContent(
    viewModel: ShowsViewModel,
    focusLevel: FocusLevel,
    showCategories: Boolean,
    categoriesWidth: Dp,
    categoryListState: LazyListState,
    contentListState: LazyListState,
    onFocusLevelChange: (FocusLevel) -> Unit
) {
    val context = LocalContext.current
    val series by viewModel.series.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val categoryFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    
    // Use rememberSaveable to preserve state across recompositions
    var selectedCategoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var contentFocusedIndex by rememberSaveable { mutableIntStateOf(0) }
    
    // Track if initial load is done
    var initialLoadDone by remember { mutableStateOf(false) }
    
    // Track if search bar has focus (to prevent category focus from stealing it)
    var searchBarHasFocus by remember { mutableStateOf(false) }
    
    // Load series only once when screen first appears
    LaunchedEffect(Unit) {
        if (series.isEmpty() && categories.isEmpty()) {
            viewModel.loadSeries()
        }
    }
    
    // Request focus with increased delay for reliability
    // Only request category focus if search bar doesn't have focus
    LaunchedEffect(focusLevel, searchBarHasFocus) {
        when (focusLevel) {
            FocusLevel.CATEGORIES -> {
                if (!searchBarHasFocus) {
                    kotlinx.coroutines.delay(50)
                    // Scroll to selected category to ensure it's visible
                    if (categories.isNotEmpty() && selectedCategoryIndex in categories.indices) {
                        categoryListState.animateScrollToItem(selectedCategoryIndex)
                    }
                    try { categoryFocusRequester.requestFocus() } catch (e: Exception) {}
                }
            }
            FocusLevel.CONTENT -> {
                kotlinx.coroutines.delay(100)
                try { contentFocusRequester.requestFocus() } catch (e: Exception) {}
            }
            else -> {}
        }
    }
    
    LaunchedEffect(selectedCategoryIndex) {
        if (categories.isNotEmpty() && selectedCategoryIndex in categories.indices) {
            categoryListState.animateScrollToItem(selectedCategoryIndex)
        }
    }
    
    // Reset focused index when series list changes (e.g., after search)
    LaunchedEffect(series) {
        if (contentFocusedIndex >= series.size) {
            contentFocusedIndex = 0
        }
    }
    
    // Collect search state
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val searchFocusRequester = remember { FocusRequester() }
    
    // Only load genre when category changes AFTER initial load
    LaunchedEffect(selectedCategoryIndex) {
        if (initialLoadDone && categories.isNotEmpty() && selectedCategoryIndex in categories.indices) {
            viewModel.loadSeriesForGenre(categories[selectedCategoryIndex].name)
            contentFocusedIndex = 0
        }
    }
    
    // Mark initial load done after categories are loaded
    LaunchedEffect(categories) {
        if (categories.isNotEmpty() && !initialLoadDone) {
            initialLoadDone = true
        }
    }
    
    LaunchedEffect(contentFocusedIndex, series) {
        if (series.isNotEmpty() && contentFocusedIndex in series.indices) {
            contentListState.scrollToItem(contentFocusedIndex / 4)
        }
    }
    
    fun openSeries(show: com.diokko.player.data.models.Series) {
        val intent = android.content.Intent(context, com.diokko.player.ui.screens.SeriesDetailActivity::class.java).apply {
            putExtra(com.diokko.player.ui.screens.SeriesDetailActivity.EXTRA_SERIES_ID, show.id)
            putExtra(com.diokko.player.ui.screens.SeriesDetailActivity.EXTRA_SERIES_NAME, show.name)
        }
        context.startActivity(intent)
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Title bar with contextual search
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = DiokkoDimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "📺", fontSize = 24.sp)
            Spacer(modifier = Modifier.width(DiokkoDimens.spacingSm))
            Text(text = "TV Shows", style = DiokkoTypography.headlineMedium)
            Spacer(modifier = Modifier.width(DiokkoDimens.spacingMd))
            if (categories.isNotEmpty() && selectedCategoryIndex in categories.indices) {
                Text(
                    text = "• ${categories[selectedCategoryIndex].name}",
                    style = DiokkoTypography.bodyMedium,
                    color = DiokkoColors.TextSecondary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            
            // Contextual search bar
            com.diokko.player.ui.components.ContextualSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onClear = { viewModel.clearSearch() },
                onNavigateDown = {
                    // Navigate to content grid where search results are shown
                    searchBarHasFocus = false
                    if (series.isNotEmpty()) {
                        onFocusLevelChange(FocusLevel.CONTENT)
                    } else {
                        try { categoryFocusRequester.requestFocus() } catch (e: Exception) {}
                    }
                },
                categoryName = if (categories.isNotEmpty() && selectedCategoryIndex in categories.indices) 
                    categories[selectedCategoryIndex].name else null,
                modifier = Modifier
                    .width(250.dp)
                    .onFocusChanged { searchBarHasFocus = it.isFocused },
                focusRequester = searchFocusRequester
            )
            
            Spacer(modifier = Modifier.width(DiokkoDimens.spacingMd))
            Text(
                text = "${series.size} shows",
                style = DiokkoTypography.bodySmall,
                color = DiokkoColors.TextSecondary
            )
        }
        
        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = "Loading shows...", style = DiokkoTypography.bodyMedium)
            }
        } else if (categories.isEmpty()) {
            // Show simple empty state - no button, focus stays on nav rail
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = "📺",
                    title = "No TV Shows Yet",
                    message = "Go to Settings to add a playlist"
                )
            }
        } else {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Categories sidebar - always rendered for focus management
                Column(
                    modifier = Modifier
                        .width(categoriesWidth)
                        .fillMaxHeight()
                        .background(if (categoriesWidth > 0.dp) DiokkoColors.Surface else Color.Transparent)
                        .padding(if (categoriesWidth > 0.dp) DiokkoDimens.spacingXs else 0.dp)
                ) {
                    if (categoriesWidth > 0.dp) {
                        Text(
                            text = "Genres",
                            style = DiokkoTypography.labelMedium,
                            color = DiokkoColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = DiokkoDimens.spacingSm, vertical = DiokkoDimens.spacingXs)
                        )
                    }
                    
                    // LazyColumn always rendered for focus requester
                    LazyColumn(
                        state = categoryListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(categoryFocusRequester)
                            // Only focusable when CATEGORIES is active
                            .focusable(enabled = focusLevel == FocusLevel.CATEGORIES)
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && focusLevel == FocusLevel.CATEGORIES) {
                                    when (event.key) {
                                        Key.DirectionDown -> {
                                            if (selectedCategoryIndex < categories.size - 1) selectedCategoryIndex++
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            if (selectedCategoryIndex > 0) {
                                                selectedCategoryIndex--
                                                true
                                            } else {
                                                // At first category, move to search bar
                                                try { searchFocusRequester.requestFocus() } catch (e: Exception) {}
                                                true
                                            }
                                        }
                                        Key.DirectionLeft -> {
                                            onFocusLevelChange(FocusLevel.NAV_RAIL)
                                            true
                                        }
                                        Key.DirectionRight, Key.Enter, Key.DirectionCenter -> {
                                            if (series.isNotEmpty()) {
                                                onFocusLevelChange(FocusLevel.CONTENT)
                                            }
                                            true
                                        }
                                        // Menu key shortcut to jump to search from any category
                                        Key.Menu -> {
                                            try { searchFocusRequester.requestFocus() } catch (e: Exception) {}
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    ) {
                        if (categoriesWidth > 0.dp) {
                            itemsIndexed(categories) { index, category ->
                                val isSelected = index == selectedCategoryIndex && focusLevel == FocusLevel.CATEGORIES
                                val isActive = index == selectedCategoryIndex
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(DiokkoShapes.small)
                                        .background(
                                            when {
                                                isSelected -> DiokkoColors.Accent
                                                isActive -> DiokkoColors.SurfaceLight
                                                else -> Color.Transparent
                                            }
                                        )
                                        .padding(horizontal = DiokkoDimens.spacingSm, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = category.name,
                                        style = DiokkoTypography.bodySmall,
                                        color = if (isSelected) DiokkoColors.TextOnAccent 
                                               else if (isActive) DiokkoColors.TextPrimary 
                                               else DiokkoColors.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (categoriesWidth > 0.dp) {
                    Spacer(modifier = Modifier.width(DiokkoDimens.spacingSm))
                }
                
                // Shows Grid
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (series.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "No shows in this genre", style = DiokkoTypography.bodyMedium, color = DiokkoColors.TextSecondary)
                        }
                    } else {
                        // Pre-calculate chunked list to avoid recalculation on each recomposition
                        val chunkedSeries = remember(series) { series.chunked(4) }
                        
                        LazyColumn(
                            state = contentListState,
                            contentPadding = PaddingValues(top = 8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(contentFocusRequester)
                                // Only focusable when CONTENT is active
                                .focusable(enabled = focusLevel == FocusLevel.CONTENT)
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && focusLevel == FocusLevel.CONTENT) {
                                        val cols = 4
                                        when (event.key) {
                                            Key.DirectionDown -> {
                                                if (contentFocusedIndex + cols < series.size) contentFocusedIndex += cols
                                                true
                                            }
                                            Key.DirectionUp -> {
                                                if (contentFocusedIndex - cols >= 0) {
                                                    contentFocusedIndex -= cols
                                                    true
                                                } else {
                                                    // At top row of content, jump to search bar
                                                    onFocusLevelChange(FocusLevel.CATEGORIES)
                                                    try { searchFocusRequester.requestFocus() } catch (e: Exception) {}
                                                    true
                                                }
                                            }
                                            Key.DirectionRight -> {
                                                if ((contentFocusedIndex + 1) % cols != 0 && contentFocusedIndex < series.size - 1) {
                                                    contentFocusedIndex++
                                                }
                                                true
                                            }
                                            Key.DirectionLeft -> {
                                                if (contentFocusedIndex % cols == 0) {
                                                    // Immediately request focus on categories to prevent search bar from intercepting
                                                    onFocusLevelChange(FocusLevel.CATEGORIES)
                                                    try { categoryFocusRequester.requestFocus() } catch (e: Exception) {}
                                                } else {
                                                    contentFocusedIndex--
                                                }
                                                true
                                            }
                                            Key.Enter, Key.DirectionCenter -> {
                                                if (series.isNotEmpty() && contentFocusedIndex in series.indices) {
                                                    val show = series[contentFocusedIndex]
                                                    openSeries(show)
                                                }
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                },
                            verticalArrangement = Arrangement.spacedBy(DiokkoDimens.spacingSm)
                        ) {
                            itemsIndexed(chunkedSeries) { rowIndex, row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(DiokkoDimens.spacingSm)
                                ) {
                                    row.forEachIndexed { colIndex, show ->
                                        val absoluteIndex = rowIndex * 4 + colIndex
                                        val isSelected = absoluteIndex == contentFocusedIndex && focusLevel == FocusLevel.CONTENT
                                        
                                        ContentCard(
                                            title = show.name,
                                            subtitle = show.genre ?: "",
                                            imageUrl = show.posterUrl,
                                            isFocused = isSelected,
                                            modifier = Modifier.weight(1f),
                                            onClick = { openSeries(show) }
                                        )
                                    }
                                    repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreenContent(
    focusLevel: FocusLevel,
    onFocusLevelChange: (FocusLevel) -> Unit,
    onPlaylistsClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    val focusRequesters = remember { List(4) { FocusRequester() } }
    
    // Request focus when entering Settings or returning to CONTENT level
    LaunchedEffect(focusLevel) {
        if (focusLevel == FocusLevel.CONTENT) {
            kotlinx.coroutines.delay(100)
            try { focusRequesters[0].requestFocus() } catch (e: Exception) {}
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = DiokkoDimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "⚙️", fontSize = 24.sp)
            Spacer(modifier = Modifier.width(DiokkoDimens.spacingSm))
            Text(text = "Settings", style = DiokkoTypography.headlineMedium)
        }
        
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DiokkoDimens.spacingSm)
        ) {
            SettingsCard(
                icon = "📋",
                title = "Manage Playlists",
                description = "Add, edit, or remove your playlists",
                onClick = onPlaylistsClick,
                onNavigateLeft = { onFocusLevelChange(FocusLevel.NAV_RAIL) },
                onFocused = { onFocusLevelChange(FocusLevel.CONTENT) },
                focusRequester = focusRequesters[0]
            )
            
            SettingsCard(
                icon = "ℹ️",
                title = "About",
                description = "App information and version",
                onClick = onAboutClick,
                onNavigateLeft = { onFocusLevelChange(FocusLevel.NAV_RAIL) },
                onFocused = { onFocusLevelChange(FocusLevel.CONTENT) },
                focusRequester = focusRequesters[1]
            )
        }
    }
}

@Composable
fun SettingsCard(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit,
    onNavigateLeft: () -> Unit,
    onFocused: () -> Unit,
    focusRequester: FocusRequester
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DiokkoShapes.medium)
            .background(if (isFocused) DiokkoColors.Accent.copy(alpha = 0.2f) else DiokkoColors.Surface)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) DiokkoColors.Accent else Color.Transparent,
                shape = DiokkoShapes.medium
            )
            .focusRequester(focusRequester)
            .onFocusChanged { 
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Enter, Key.DirectionCenter -> {
                            onClick()
                            true
                        }
                        Key.DirectionLeft -> {
                            onNavigateLeft()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .padding(DiokkoDimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 28.sp)
        Spacer(modifier = Modifier.width(DiokkoDimens.spacingMd))
        Column {
            Text(
                text = title,
                style = DiokkoTypography.titleMedium,
                color = DiokkoColors.TextPrimary
            )
            Text(
                text = description,
                style = DiokkoTypography.bodySmall,
                color = DiokkoColors.TextSecondary
            )
        }
    }
}

/**
 * Global Search Screen - searches across all content types
 */
@Composable
fun GlobalSearchScreen(
    viewModel: SearchViewModel,
    onChannelClick: (com.diokko.player.data.models.Channel) -> Unit,
    onMovieClick: (com.diokko.player.data.models.Movie) -> Unit,
    onSeriesClick: (com.diokko.player.data.models.Series) -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    
    val searchFocusRequester = remember { FocusRequester() }
    val resultsListState = rememberLazyListState()
    
    // Focus state: 0 = search bar, 1+ = result index (1-based)
    var focusIndex by remember { mutableIntStateOf(0) }
    
    // Build flat list of all results for navigation
    val allResults = remember(searchResults) {
        buildList {
            searchResults.channels.take(10).forEach { add(Triple("channel", it, Unit)) }
            searchResults.movies.take(10).forEach { add(Triple("movie", it, Unit)) }
            searchResults.series.take(10).forEach { add(Triple("series", it, Unit)) }
        }
    }
    
    // Reset focus index when results change
    LaunchedEffect(allResults) {
        focusIndex = 0
    }
    
    // Request focus on search bar when screen loads
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try { searchFocusRequester.requestFocus() } catch (e: Exception) {}
    }
    
    // Scroll to selected result
    LaunchedEffect(focusIndex) {
        if (focusIndex > 0 && focusIndex <= allResults.size) {
            // Account for headers
            val channelCount = searchResults.channels.take(10).size
            val movieCount = searchResults.movies.take(10).size
            val actualIndex = focusIndex - 1
            val scrollIndex = actualIndex + 
                1 + // Channel header
                (if (actualIndex >= channelCount && movieCount > 0) 2 else 0) + // Movie header + spacer
                (if (actualIndex >= channelCount + movieCount && searchResults.series.isNotEmpty()) 2 else 0) // Series header + spacer
            resultsListState.animateScrollToItem(scrollIndex.coerceIn(0, maxOf(0, resultsListState.layoutInfo.totalItemsCount - 1)))
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(DiokkoDimens.spacingMd)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionDown -> {
                            if (focusIndex == 0 && allResults.isNotEmpty()) {
                                focusIndex = 1
                                true
                            } else if (focusIndex > 0 && focusIndex < allResults.size) {
                                focusIndex++
                                true
                            } else false
                        }
                        Key.DirectionUp -> {
                            if (focusIndex > 1) {
                                focusIndex--
                                true
                            } else if (focusIndex == 1) {
                                focusIndex = 0
                                try { searchFocusRequester.requestFocus() } catch (e: Exception) {}
                                true
                            } else false
                        }
                        Key.Enter, Key.DirectionCenter -> {
                            if (focusIndex > 0 && focusIndex <= allResults.size) {
                                val (type, item, _) = allResults[focusIndex - 1]
                                when (type) {
                                    "channel" -> onChannelClick(item as com.diokko.player.data.models.Channel)
                                    "movie" -> onMovieClick(item as com.diokko.player.data.models.Movie)
                                    "series" -> onSeriesClick(item as com.diokko.player.data.models.Series)
                                }
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
    ) {
        // Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🔍", fontSize = 28.sp)
            Spacer(modifier = Modifier.width(DiokkoDimens.spacingSm))
            Text(
                text = "Search",
                style = DiokkoTypography.headlineMedium,
                color = DiokkoColors.TextPrimary
            )
        }
        
        Spacer(modifier = Modifier.height(DiokkoDimens.spacingMd))
        
        // Search bar
        com.diokko.player.ui.components.TvSearchBar(
            query = searchQuery,
            onQueryChange = { viewModel.updateQuery(it) },
            onSearch = { },
            onClear = { viewModel.clearSearch() },
            onNavigateDown = {
                if (allResults.isNotEmpty()) {
                    focusIndex = 1
                }
            },
            placeholder = "Search channels, movies, shows...",
            modifier = Modifier.fillMaxWidth(),
            focusRequester = searchFocusRequester
        )
        
        Spacer(modifier = Modifier.height(DiokkoDimens.spacingMd))
        
        // Results
        if (isSearching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Searching...",
                    style = DiokkoTypography.bodyMedium,
                    color = DiokkoColors.TextSecondary
                )
            }
        } else if (searchQuery.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🔍", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(DiokkoDimens.spacingMd))
                    Text(
                        text = "Start typing to search",
                        style = DiokkoTypography.titleMedium,
                        color = DiokkoColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(DiokkoDimens.spacingSm))
                    Text(
                        text = "Search across Live TV, Movies, and TV Shows",
                        style = DiokkoTypography.bodySmall,
                        color = DiokkoColors.TextSecondary
                    )
                }
            }
        } else if (searchResults.isEmpty) {
            com.diokko.player.ui.components.EmptySearchResults(
                query = searchQuery,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Results list
            var currentGlobalIndex = 0
            
            LazyColumn(
                state = resultsListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(DiokkoDimens.spacingXs)
            ) {
                // Channels section
                if (searchResults.channels.isNotEmpty()) {
                    item {
                        com.diokko.player.ui.components.SearchResultHeader(
                            title = "Live TV",
                            count = searchResults.channels.size,
                            emoji = "📺"
                        )
                    }
                    items(searchResults.channels.take(10).size) { index ->
                        val channel = searchResults.channels[index]
                        val itemIndex = index + 1
                        SearchResultRow(
                            title = channel.name,
                            subtitle = channel.groupTitle ?: "",
                            emoji = "📺",
                            isSelected = focusIndex == itemIndex
                        )
                    }
                }
                
                // Movies section
                if (searchResults.movies.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(DiokkoDimens.spacingSm))
                        com.diokko.player.ui.components.SearchResultHeader(
                            title = "Movies",
                            count = searchResults.movies.size,
                            emoji = "🎬"
                        )
                    }
                    val channelCount = searchResults.channels.take(10).size
                    items(searchResults.movies.take(10).size) { index ->
                        val movie = searchResults.movies[index]
                        val itemIndex = channelCount + index + 1
                        SearchResultRow(
                            title = movie.name,
                            subtitle = movie.genre ?: "",
                            emoji = "🎬",
                            isSelected = focusIndex == itemIndex
                        )
                    }
                }
                
                // Series section
                if (searchResults.series.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(DiokkoDimens.spacingSm))
                        com.diokko.player.ui.components.SearchResultHeader(
                            title = "TV Shows",
                            count = searchResults.series.size,
                            emoji = "📺"
                        )
                    }
                    val channelCount = searchResults.channels.take(10).size
                    val movieCount = searchResults.movies.take(10).size
                    items(searchResults.series.take(10).size) { index ->
                        val series = searchResults.series[index]
                        val itemIndex = channelCount + movieCount + index + 1
                        SearchResultRow(
                            title = series.name,
                            subtitle = series.genre ?: "",
                            emoji = "📺",
                            isSelected = focusIndex == itemIndex
                        )
                    }
                }
            }
        }
    }
}

/**
 * Simple search result row (non-focusable, selection handled by parent)
 */
@Composable
fun SearchResultRow(
    title: String,
    subtitle: String,
    emoji: String,
    isSelected: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(DiokkoShapes.small)
            .background(
                if (isSelected) DiokkoColors.Accent 
                else DiokkoColors.Surface
            )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) DiokkoColors.AccentLight else Color.Transparent,
                shape = DiokkoShapes.small
            )
            .padding(horizontal = DiokkoDimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 24.sp)
        Spacer(modifier = Modifier.width(DiokkoDimens.spacingMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = DiokkoTypography.bodyMedium,
                color = if (isSelected) DiokkoColors.TextOnAccent else DiokkoColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = DiokkoTypography.bodySmall,
                    color = if (isSelected) DiokkoColors.TextOnAccent.copy(alpha = 0.7f) else DiokkoColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
