package com.diokko.player.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diokko.player.data.models.PlaylistType
import com.diokko.player.ui.components.*
import com.diokko.player.ui.theme.*
import com.diokko.player.viewmodel.PlaylistViewModel

@Composable
fun AddPlaylistScreen(
    viewModel: PlaylistViewModel,
    onBack: () -> Unit
) {
    var playlistType by remember { mutableStateOf(PlaylistType.M3U) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    
    val uiState by viewModel.uiState.collectAsState()
    
    // Sync local saving state with ViewModel
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && isSaving) {
            // ViewModel finished, check for success or error
            if (uiState.successMessage != null || uiState.error != null) {
                isSaving = false
            }
        }
    }
    
    // Navigate back on success
    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) {
            isSaving = false
            viewModel.clearSuccessMessage()
            onBack()
        }
    }
    
    // Handle errors
    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            isSaving = false
        }
    }
    
    // Show error from ViewModel
    val displayError = localError ?: uiState.error
    val isLoading = isSaving || uiState.isLoading
    
    val backFocusRequester = remember { FocusRequester() }
    val m3uFocusRequester = remember { FocusRequester() }
    val xtreamFocusRequester = remember { FocusRequester() }
    val nameFocusRequester = remember { FocusRequester() }
    val urlFocusRequester = remember { FocusRequester() }
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember { FocusRequester() }
    
    // Add delay to ensure composable is attached before requesting focus
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try {
            m3uFocusRequester.requestFocus()
        } catch (e: Exception) {
            // FocusRequester might not be attached yet, ignore
        }
    }
    
    fun save() {
        if (isSaving) return // Prevent double-tap
        
        localError = null
        viewModel.clearError()
        
        if (name.isBlank()) {
            localError = "Please enter a playlist name"
            return
        }
        
        when (playlistType) {
            PlaylistType.M3U -> {
                if (url.isBlank()) {
                    localError = "Please enter a playlist URL"
                    return
                }
                isSaving = true // Set immediately before calling ViewModel
                viewModel.addM3UPlaylist(name.trim(), url.trim())
            }
            PlaylistType.XTREAM_CODES -> {
                if (url.isBlank() || username.isBlank() || password.isBlank()) {
                    localError = "Please fill in all Xtream fields"
                    return
                }
                isSaving = true // Set immediately before calling ViewModel
                viewModel.addXtreamPlaylist(name.trim(), url.trim(), username.trim(), password.trim())
            }
        }
    }
    
    // Full screen loading overlay
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = DiokkoDimens.spacingLg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(
                    onClick = { if (!isLoading) onBack() },
                    focusRequester = backFocusRequester
                )
                
                Spacer(modifier = Modifier.width(DiokkoDimens.spacingMd))
                
                Column {
                    Text(
                        text = "Add Playlist",
                        style = DiokkoTypography.displaySmall
                    )
                    Spacer(modifier = Modifier.height(DiokkoDimens.spacingXxs))
                    Text(
                        text = "Configure a new M3U or Xtream playlist",
                        style = DiokkoTypography.bodyMedium
                    )
                }
            }
            
            // Content
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(DiokkoDimens.spacingXl)
            ) {
                // Left side - Type selection
                Column(
                    modifier = Modifier.width(280.dp)
                ) {
                    Text(
                        text = "Playlist Type",
                        style = DiokkoTypography.headlineSmall,
                    modifier = Modifier.padding(bottom = DiokkoDimens.spacingMd)
                )
                
                TypeCard(
                    icon = "📄",
                    title = "M3U Playlist",
                    description = "Standard M3U/M3U8 playlist file",
                    isSelected = playlistType == PlaylistType.M3U,
                    onClick = { playlistType = PlaylistType.M3U },
                    focusRequester = m3uFocusRequester
                )
                
                Spacer(modifier = Modifier.height(DiokkoDimens.spacingSm))
                
                TypeCard(
                    icon = "🌐",
                    title = "Xtream Codes",
                    description = "Xtream Codes API connection",
                    isSelected = playlistType == PlaylistType.XTREAM_CODES,
                    onClick = { playlistType = PlaylistType.XTREAM_CODES },
                    focusRequester = xtreamFocusRequester
                )
            }
            
            // Right side - Form (scrollable for Fire TV)
            val scrollState = rememberScrollState()
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(DiokkoShapes.large)
                    .background(DiokkoColors.Surface)
                    .padding(DiokkoDimens.spacingXl)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = "Playlist Details",
                    style = DiokkoTypography.headlineSmall,
                    modifier = Modifier.padding(bottom = DiokkoDimens.spacingLg)
                )
                
                // Error message
                AnimatedVisibility(
                    visible = displayError != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DiokkoShapes.small)
                            .background(DiokkoColors.Error.copy(alpha = 0.1f))
                            .border(1.dp, DiokkoColors.Error.copy(alpha = 0.3f), DiokkoShapes.small)
                            .padding(DiokkoDimens.spacingMd)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "⚠️", style = DiokkoTypography.bodyLarge)
                            Spacer(modifier = Modifier.width(DiokkoDimens.spacingXs))
                            Text(
                                text = displayError ?: "",
                                style = DiokkoTypography.bodyMedium.copy(color = DiokkoColors.Error)
                            )
                        }
                    }
                }
                
                if (displayError != null) {
                    Spacer(modifier = Modifier.height(DiokkoDimens.spacingMd))
                }
                
                // Name field
                FormField(
                    label = "Playlist Name",
                    value = name,
                    onValueChange = { 
                        name = it
                        localError = null
                    },
                    placeholder = "My Playlist",
                    focusRequester = nameFocusRequester
                )
                
                Spacer(modifier = Modifier.height(DiokkoDimens.spacingMd))
                
                // URL field
                FormField(
                    label = if (playlistType == PlaylistType.M3U) "Playlist URL" else "Server URL",
                    value = url,
                    onValueChange = { 
                        url = it
                        localError = null
                    },
                    placeholder = if (playlistType == PlaylistType.M3U) 
                        "http://example.com/playlist.m3u" 
                    else 
                        "http://example.com:8080",
                    focusRequester = urlFocusRequester
                )
                
                // Xtream-specific fields
                AnimatedVisibility(
                    visible = playlistType == PlaylistType.XTREAM_CODES,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(DiokkoDimens.spacingMd))
                        
                        FormField(
                            label = "Username",
                            value = username,
                            onValueChange = { 
                                username = it
                                localError = null
                            },
                            placeholder = "Enter username",
                            focusRequester = usernameFocusRequester
                        )
                        
                        Spacer(modifier = Modifier.height(DiokkoDimens.spacingMd))
                        
                        FormField(
                            label = "Password",
                            value = password,
                            onValueChange = { 
                                password = it
                                localError = null
                            },
                            placeholder = "Enter password",
                            isPassword = true,
                            focusRequester = passwordFocusRequester
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(DiokkoDimens.spacingXl))
                
                // Save button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ActionButton(
                        text = if (isLoading) "Saving..." else "Save Playlist",
                        icon = if (isLoading) "⏳" else "💾",
                        enabled = !isLoading,
                        onClick = { if (!isLoading) save() },
                        focusRequester = saveFocusRequester
                    )
                }
            }
        }
        } // End Column
        
        // Loading overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Animated loading spinner
                    val infiniteTransition = rememberInfiniteTransition(label = "loading")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "rotation"
                    )
                    
                    Text(
                        text = "⏳",
                        fontSize = 48.sp,
                        modifier = Modifier.graphicsLayer { rotationZ = rotation }
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "Processing Playlist...",
                        style = DiokkoTypography.headlineMedium,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Downloading and parsing your playlist.\nThis may take a few minutes for large playlists.",
                        style = DiokkoTypography.bodyMedium,
                        color = DiokkoColors.TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Progress indicator dots
                    val dotAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dotAlpha"
                    )
                    
                    Row {
                        repeat(3) { index ->
                            val delay = index * 167
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(500, delayMillis = delay),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "dot$index"
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .padding(2.dp)
                                    .background(
                                        DiokkoColors.Accent.copy(alpha = alpha),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }
    } // End Box
}

@Composable
fun TypeCard(
    icon: String,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "typeScale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> DiokkoColors.Accent
            isSelected -> DiokkoColors.Accent.copy(alpha = 0.5f)
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "borderColor"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(DiokkoShapes.medium)
            .background(
                brush = when {
                    isSelected -> Brush.linearGradient(
                        colors = listOf(
                            DiokkoColors.Accent.copy(alpha = 0.15f),
                            DiokkoColors.Accent.copy(alpha = 0.05f)
                        )
                    )
                    else -> DiokkoGradients.card
                }
            )
            .border(2.dp, borderColor, DiokkoShapes.medium)
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
            .padding(DiokkoDimens.spacingMd)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(DiokkoShapes.small)
                    .background(
                        if (isSelected) DiokkoColors.Accent.copy(alpha = 0.2f)
                        else DiokkoColors.SurfaceLight
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    style = DiokkoTypography.headlineMedium
                )
            }
            
            Spacer(modifier = Modifier.width(DiokkoDimens.spacingMd))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = DiokkoTypography.headlineSmall.copy(
                        color = if (isSelected || isFocused) DiokkoColors.TextPrimary 
                            else DiokkoColors.TextSecondary
                    )
                )
                Text(
                    text = description,
                    style = DiokkoTypography.bodySmall
                )
            }
            
            // Selection indicator
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        if (isSelected) DiokkoColors.Accent else DiokkoColors.SurfaceLight
                    )
                    .border(
                        width = 2.dp,
                        color = if (isSelected) DiokkoColors.Accent else DiokkoColors.TextMuted,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Text(
                        text = "✓",
                        style = DiokkoTypography.labelSmall.copy(
                            color = DiokkoColors.TextOnAccent
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    focusRequester: FocusRequester
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Column {
        Text(
            text = label,
            style = DiokkoTypography.labelMedium,
            modifier = Modifier.padding(bottom = DiokkoDimens.spacingXs)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(DiokkoShapes.small)
                .background(DiokkoColors.SurfaceLight)
                .border(
                    width = 2.dp,
                    color = when {
                        isFocused -> DiokkoColors.Accent
                        else -> Color.Transparent
                    },
                    shape = DiokkoShapes.small
                )
                .padding(horizontal = DiokkoDimens.spacingMd)
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused },
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = DiokkoTypography.bodyLarge.copy(
                    color = DiokkoColors.TextPrimary
                ),
                cursorBrush = SolidColor(DiokkoColors.Accent),
                visualTransformation = if (isPassword) 
                    PasswordVisualTransformation() 
                else 
                    VisualTransformation.None,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = DiokkoTypography.bodyLarge.copy(
                                    color = DiokkoColors.TextMuted
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}
