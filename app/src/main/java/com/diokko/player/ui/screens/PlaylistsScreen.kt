package com.diokko.player.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diokko.player.data.models.Playlist
import com.diokko.player.data.models.PlaylistType
import com.diokko.player.ui.components.*
import com.diokko.player.ui.theme.*
import com.diokko.player.viewmodel.PlaylistViewModel

@Composable
fun PlaylistsScreen(
    viewModel: PlaylistViewModel,
    onAddPlaylist: () -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val playlists = uiState.playlists
    val backFocusRequester = remember { FocusRequester() }
    val addFocusRequester = remember { FocusRequester() }
    val listFocusRequesters = remember(playlists.size) { 
        List(playlists.size) { FocusRequester() } 
    }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    
    // Request focus with delay to ensure composables are ready
    // Use playlists.size as key so it re-runs when playlists load from database
    LaunchedEffect(playlists.size) {
        kotlinx.coroutines.delay(150) // Wait for LazyColumn items to compose
        try {
            if (playlists.isEmpty()) {
                addFocusRequester.requestFocus()
            } else {
                listFocusRequesters.firstOrNull()?.requestFocus()
            }
        } catch (e: Exception) {
            // FocusRequester might not be attached yet, ignore
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = DiokkoDimens.spacingLg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(
                    onClick = onBack,
                    focusRequester = backFocusRequester
                )
                
                Spacer(modifier = Modifier.width(DiokkoDimens.spacingMd))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Playlists",
                        style = DiokkoTypography.displaySmall
                    )
                    Spacer(modifier = Modifier.height(DiokkoDimens.spacingXxs))
                    Text(
                        text = "${playlists.size} playlist${if (playlists.size != 1) "s" else ""} configured",
                        style = DiokkoTypography.bodyMedium
                    )
                }
                
                ActionButton(
                    text = "Add Playlist",
                    icon = "➕",
                    onClick = onAddPlaylist,
                    focusRequester = addFocusRequester
                )
            }
            
            if (playlists.isEmpty()) {
                EmptyState(
                    icon = "📋",
                    title = "No Playlists",
                    message = "Add your first playlist to start watching content",
                    actionText = "Add Playlist",
                    onAction = onAddPlaylist,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(DiokkoDimens.spacingSm),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(playlists) { index, playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onRefresh = { viewModel.refreshPlaylist(playlist) },
                            onDelete = {
                                playlistToDelete = playlist
                                showDeleteDialog = true
                            },
                            focusRequester = listFocusRequesters.getOrNull(index) 
                                ?: remember { FocusRequester() }
                        )
                    }
                }
            }
        }
        
        // Delete dialog
        if (showDeleteDialog && playlistToDelete != null) {
            DeletePlaylistDialog(
                playlistName = playlistToDelete?.name ?: "",
                onConfirm = {
                    playlistToDelete?.let { viewModel.deletePlaylist(it) }
                    showDeleteDialog = false
                    playlistToDelete = null
                },
                onDismiss = {
                    showDeleteDialog = false
                    playlistToDelete = null
                }
            )
        }
    }
}

@Composable
fun PlaylistCard(
    playlist: Playlist,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    focusRequester: FocusRequester
) {
    var isFocused by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var focusedActionIndex by remember { mutableStateOf(0) }
    
    val refreshFocusRequester = remember { FocusRequester() }
    val deleteFocusRequester = remember { FocusRequester() }
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused || showActions) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    // When actions are shown, focus the first button
    LaunchedEffect(showActions) {
        if (showActions) {
            kotlinx.coroutines.delay(50)
            try {
                refreshFocusRequester.requestFocus()
                focusedActionIndex = 0
            } catch (e: Exception) {
                // FocusRequester might not be attached yet, ignore
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(DiokkoShapes.medium)
            .background(
                brush = if (isFocused || showActions) DiokkoGradients.cardHover else DiokkoGradients.card
            )
            .then(
                if (isFocused || showActions) {
                    Modifier.background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                DiokkoColors.Accent.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
                } else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { 
                isFocused = it.isFocused
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Enter, Key.DirectionCenter -> {
                            showActions = true
                            true
                        }
                        Key.Back, Key.Escape -> {
                            if (showActions) {
                                showActions = false
                                focusRequester.requestFocus()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .padding(DiokkoDimens.spacingMd)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Type icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(DiokkoShapes.medium)
                    .background(
                        brush = when (playlist.type) {
                            PlaylistType.M3U -> Brush.linearGradient(
                                colors = listOf(
                                    DiokkoColors.Secondary.copy(alpha = 0.3f),
                                    DiokkoColors.Secondary.copy(alpha = 0.1f)
                                )
                            )
                            PlaylistType.XTREAM_CODES -> Brush.linearGradient(
                                colors = listOf(
                                    DiokkoColors.Tertiary.copy(alpha = 0.3f),
                                    DiokkoColors.Tertiary.copy(alpha = 0.1f)
                                )
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (playlist.type) {
                        PlaylistType.M3U -> "📄"
                        PlaylistType.XTREAM_CODES -> "🌐"
                    },
                    style = DiokkoTypography.headlineMedium
                )
            }
            
            Spacer(modifier = Modifier.width(DiokkoDimens.spacingMd))
            
            // Playlist info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DiokkoDimens.spacingXs)
                ) {
                    Text(
                        text = playlist.name,
                        style = DiokkoTypography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    // Type badge
                    Box(
                        modifier = Modifier
                            .clip(DiokkoShapes.small)
                            .background(DiokkoColors.Surface)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = when (playlist.type) {
                                PlaylistType.M3U -> "M3U"
                                PlaylistType.XTREAM_CODES -> "Xtream"
                            },
                            style = DiokkoTypography.labelSmall.copy(
                                color = when (playlist.type) {
                                    PlaylistType.M3U -> DiokkoColors.Secondary
                                    PlaylistType.XTREAM_CODES -> DiokkoColors.Tertiary
                                }
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(DiokkoDimens.spacingXs))
                
                // Stats row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(DiokkoDimens.spacingMd)
                ) {
                    StatItem(icon = "📺", value = "${playlist.channelCount}", label = "channels")
                    StatItem(icon = "🎬", value = "${playlist.movieCount}", label = "movies")
                    StatItem(icon = "📺", value = "${playlist.seriesCount}", label = "series")
                }
            }
            
            Spacer(modifier = Modifier.width(DiokkoDimens.spacingMd))
            
            // Actions
            AnimatedVisibility(
                visible = showActions,
                enter = fadeIn() + slideInHorizontally { it },
                exit = fadeOut() + slideOutHorizontally { it }
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(DiokkoDimens.spacingSm)
                ) {
                    SmallActionButton(
                        icon = "🔄",
                        onClick = {
                            onRefresh()
                            showActions = false
                        },
                        color = DiokkoColors.Accent,
                        focusRequester = refreshFocusRequester,
                        onFocusLost = {
                            // If focus leaves the action buttons, hide them
                        },
                        onLeftNav = {
                            // Go back to card
                            showActions = false
                            focusRequester.requestFocus()
                        },
                        onRightNav = {
                            deleteFocusRequester.requestFocus()
                        }
                    )
                    SmallActionButton(
                        icon = "🗑️",
                        onClick = {
                            onDelete()
                            showActions = false
                        },
                        color = DiokkoColors.Error,
                        focusRequester = deleteFocusRequester,
                        onFocusLost = {
                            // If focus leaves the action buttons, hide them
                        },
                        onLeftNav = {
                            refreshFocusRequester.requestFocus()
                        },
                        onRightNav = {
                            // Stay on delete or close
                        }
                    )
                }
            }
            
            // Chevron hint
            if (!showActions) {
                Text(
                    text = if (isFocused) "Press OK" else "›",
                    style = DiokkoTypography.bodySmall.copy(
                        color = if (isFocused) DiokkoColors.Accent else DiokkoColors.TextMuted
                    )
                )
            }
        }
    }
}

@Composable
fun StatItem(
    icon: String,
    value: String,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = icon,
            style = DiokkoTypography.labelSmall
        )
        Text(
            text = value,
            style = DiokkoTypography.labelMedium.copy(color = DiokkoColors.TextPrimary)
        )
        Text(
            text = label,
            style = DiokkoTypography.labelSmall
        )
    }
}

@Composable
fun SmallActionButton(
    icon: String,
    onClick: () -> Unit,
    color: Color = DiokkoColors.Accent,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusLost: () -> Unit = {},
    onLeftNav: () -> Unit = {},
    onRightNav: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (isFocused) color.copy(alpha = 0.4f) else color.copy(alpha = 0.15f)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { 
                val wasFocused = isFocused
                isFocused = it.isFocused
                if (wasFocused && !it.isFocused) {
                    onFocusLost()
                }
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
                            onLeftNav()
                            true
                        }
                        Key.DirectionRight -> {
                            onRightNav()
                            true
                        }
                        else -> false
                    }
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            style = DiokkoTypography.headlineSmall
        )
    }
}

@Composable
fun BackButton(
    onClick: () -> Unit,
    focusRequester: FocusRequester
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "backScale"
    )
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (isFocused) DiokkoColors.Accent.copy(alpha = 0.2f)
                else DiokkoColors.Surface
            )
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    onClick()
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "←",
            style = DiokkoTypography.headlineMedium.copy(
                color = if (isFocused) DiokkoColors.Accent else DiokkoColors.TextSecondary
            )
        )
    }
}

@Composable
fun DeletePlaylistDialog(
    playlistName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val confirmFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }
    
    // Add delay to ensure composable is attached before requesting focus
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try {
            cancelFocusRequester.requestFocus()
        } catch (e: Exception) {
            // FocusRequester might not be attached yet, ignore
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .clip(DiokkoShapes.large)
                .background(DiokkoColors.Surface)
                .padding(DiokkoDimens.spacingXl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚠️",
                style = DiokkoTypography.displayMedium
            )
            
            Spacer(modifier = Modifier.height(DiokkoDimens.spacingMd))
            
            Text(
                text = "Delete Playlist?",
                style = DiokkoTypography.headlineLarge
            )
            
            Spacer(modifier = Modifier.height(DiokkoDimens.spacingXs))
            
            Text(
                text = "\"$playlistName\" will be permanently deleted.",
                style = DiokkoTypography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(DiokkoDimens.spacingXl))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(DiokkoDimens.spacingMd)
            ) {
                DialogButton(
                    text = "Cancel",
                    isPrimary = false,
                    onClick = onDismiss,
                    focusRequester = cancelFocusRequester
                )
                
                DialogButton(
                    text = "Delete",
                    isPrimary = true,
                    isDestructive = true,
                    onClick = onConfirm,
                    focusRequester = confirmFocusRequester
                )
            }
        }
    }
}

@Composable
fun DialogButton(
    text: String,
    isPrimary: Boolean,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
    focusRequester: FocusRequester
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val backgroundColor = when {
        isDestructive && isFocused -> DiokkoColors.Error
        isPrimary && isFocused -> DiokkoColors.Accent
        isFocused -> DiokkoColors.SurfaceElevated
        else -> DiokkoColors.SurfaceLight
    }
    
    Box(
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 120.dp)
            .clip(DiokkoShapes.medium)
            .background(backgroundColor)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    onClick()
                    true
                } else false
            }
            .padding(horizontal = DiokkoDimens.spacingLg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = DiokkoTypography.button.copy(
                color = if (isFocused) DiokkoColors.TextOnAccent else DiokkoColors.TextPrimary
            )
        )
    }
}
