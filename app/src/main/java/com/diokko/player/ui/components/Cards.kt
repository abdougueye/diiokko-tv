package com.diokko.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.diokko.player.ui.theme.*

/**
 * Base focusable card with premium visual effects
 */
@Composable
fun FocusableCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
    shape: RoundedCornerShape = DiokkoShapes.medium,
    externalFocused: Boolean = false,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    var internalFocused by remember { mutableStateOf(false) }
    val isFocused = internalFocused || externalFocused
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isFocused) 24.dp else 8.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "elevation"
    )
    
    val borderAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(200),
        label = "borderAlpha"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = if (isFocused) DiokkoColors.FocusGlow else Color.Transparent,
                spotColor = if (isFocused) DiokkoColors.FocusGlow else Color.Transparent
            )
            .clip(shape)
            .background(
                brush = if (isFocused) DiokkoGradients.cardHover else DiokkoGradients.card,
                shape = shape
            )
            .border(
                width = 2.dp,
                color = DiokkoColors.FocusBorder.copy(alpha = borderAlpha),
                shape = shape
            )
            .focusRequester(focusRequester)
            .onFocusChanged { internalFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && 
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    onClick()
                    true
                } else false
            }
    ) {
        content(isFocused)
    }
}

/**
 * Content card for movies/shows with poster and info
 */
@Composable
fun ContentCard(
    title: String,
    subtitle: String? = null,
    imageUrl: String? = null,
    posterEmoji: String = "🎬",
    onClick: () -> Unit = {},
    isFocused: Boolean = false,
    modifier: Modifier = Modifier
) {
    FocusableCard(
        modifier = modifier.size(
            width = DiokkoDimens.posterWidth,
            height = DiokkoDimens.posterHeight + 70.dp
        ),
        onClick = onClick,
        externalFocused = isFocused
    ) { focused ->
        Column {
            // Poster area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DiokkoDimens.posterHeight)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                DiokkoColors.SurfaceElevated,
                                DiokkoColors.Surface
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Show image if URL provided, otherwise show emoji
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = posterEmoji,
                        style = DiokkoTypography.displayLarge.copy(fontSize = 64.sp)
                    )
                }
                
                // Gradient overlay at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    DiokkoColors.Surface.copy(alpha = 0.9f)
                                )
                            )
                        )
                )
            }
            
            // Info area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DiokkoDimens.spacingSm)
            ) {
                Text(
                    text = title,
                    style = if (isFocused) 
                        DiokkoTypography.labelLarge.copy(color = DiokkoColors.TextPrimary)
                    else 
                        DiokkoTypography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = DiokkoTypography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Focusable content card for use in LazyVerticalGrid.
 * Uses native Android focus system for proper D-Pad navigation.
 * 
 * Modifier order is critical for Fire TV:
 * 1. Layout modifiers (aspectRatio)
 * 2. onFocusChanged - track focus state
 * 3. focusable - register as focus target
 * 4. clickable - handles D-Pad Enter/Center automatically
 * 5. Visual modifiers (scale, shadow, clip, background, border)
 * 
 * @param onFocused Callback when this card receives focus (useful for tracking focused index)
 */
@Composable
fun FocusableContentCard(
    title: String,
    subtitle: String? = null,
    imageUrl: String? = null,
    posterEmoji: String = "🎬",
    onClick: () -> Unit = {},
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isFocused) 24.dp else 8.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "elevation"
    )
    
    val borderAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(200),
        label = "borderAlpha"
    )
    
    Box(
        modifier = modifier
            .aspectRatio(0.67f) // Standard poster ratio (2:3)
            // Focus modifiers FIRST (correct order for Fire TV)
            .onFocusChanged { focusState ->
                val wasFocused = isFocused
                isFocused = focusState.isFocused
                // Notify parent when we gain focus
                if (!wasFocused && isFocused) {
                    onFocused()
                }
            }
            .focusable()
            .clickable { onClick() } // Handles D-Pad Enter/Center automatically
            // Visual modifiers AFTER focus chain
            .scale(scale)
            .shadow(
                elevation = elevation,
                shape = DiokkoShapes.medium,
                ambientColor = if (isFocused) DiokkoColors.FocusGlow else Color.Transparent,
                spotColor = if (isFocused) DiokkoColors.FocusGlow else Color.Transparent
            )
            .clip(DiokkoShapes.medium)
            .background(
                brush = if (isFocused) DiokkoGradients.cardHover else DiokkoGradients.card,
                shape = DiokkoShapes.medium
            )
            .border(
                width = 2.dp,
                color = DiokkoColors.FocusBorder.copy(alpha = borderAlpha),
                shape = DiokkoShapes.medium
            )
    ) {
        Column {
            // Poster area - takes most of the card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                DiokkoColors.SurfaceElevated,
                                DiokkoColors.Surface
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = posterEmoji,
                        style = DiokkoTypography.displayLarge.copy(fontSize = 48.sp)
                    )
                }
                
                // Gradient overlay at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    DiokkoColors.Surface.copy(alpha = 0.9f)
                                )
                            )
                        )
                )
            }
            
            // Info area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DiokkoDimens.spacingSm, vertical = DiokkoDimens.spacingXs)
            ) {
                Text(
                    text = title,
                    style = if (isFocused) 
                        DiokkoTypography.labelMedium.copy(color = DiokkoColors.TextPrimary)
                    else 
                        DiokkoTypography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = DiokkoTypography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Channel card for live TV
 */
@Composable
fun ChannelCard(
    name: String,
    category: String? = null,
    logoEmoji: String = "📺",
    isLive: Boolean = true,
    onClick: () -> Unit = {},
    isFocused: Boolean = false,
    modifier: Modifier = Modifier
) {
    FocusableCard(
        modifier = modifier
            .width(DiokkoDimens.thumbnailWidth)
            .height(100.dp),
        onClick = onClick,
        shape = DiokkoShapes.medium,
        externalFocused = isFocused
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(DiokkoDimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel logo
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(DiokkoShapes.small)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                DiokkoColors.SurfaceElevated,
                                DiokkoColors.Surface
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = logoEmoji,
                    style = DiokkoTypography.displaySmall.copy(fontSize = 32.sp)
                )
            }
            
            Spacer(modifier = Modifier.width(DiokkoDimens.spacingMd))
            
            // Channel info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = name,
                    style = if (isFocused)
                        DiokkoTypography.headlineSmall.copy(color = DiokkoColors.TextPrimary)
                    else
                        DiokkoTypography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (category != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = category,
                        style = DiokkoTypography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Live indicator
            if (isLive) {
                LiveIndicator()
            }
        }
    }
}

/**
 * Animated live indicator
 */
@Composable
fun LiveIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "live")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveAlpha"
    )
    
    Row(
        modifier = modifier
            .clip(DiokkoShapes.pill)
            .background(DiokkoColors.Error.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(DiokkoColors.Error.copy(alpha = alpha))
        )
        Text(
            text = "LIVE",
            style = DiokkoTypography.labelSmall.copy(
                color = DiokkoColors.Error,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        )
    }
}

/**
 * Navigation button with focus effects
 */
@Composable
fun NavButton(
    icon: String,
    label: String,
    isSelected: Boolean = false,
    isExpanded: Boolean = true,
    onClick: () -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "navScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> DiokkoColors.Accent
            isSelected -> DiokkoColors.Selected
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "navBg"
    )
    
    val contentColor by animateColorAsState(
        targetValue = when {
            isFocused -> DiokkoColors.TextOnAccent
            isSelected -> DiokkoColors.Accent
            else -> DiokkoColors.TextSecondary
        },
        animationSpec = tween(200),
        label = "navContent"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .height(DiokkoDimens.navItemHeight)
            .padding(horizontal = DiokkoDimens.spacingXs)
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
            },
        contentAlignment = if (isExpanded) Alignment.CenterStart else Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = DiokkoDimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DiokkoDimens.spacingSm)
        ) {
            Text(
                text = icon,
                style = DiokkoTypography.headlineMedium.copy(
                    color = contentColor
                )
            )
            
            if (isExpanded) {
                Text(
                    text = label,
                    style = if (isSelected || isFocused)
                        DiokkoTypography.navItemSelected.copy(color = contentColor)
                    else
                        DiokkoTypography.navItem.copy(color = contentColor)
                )
            }
        }
    }
}

/**
 * Action button with gradient background
 */
@Composable
fun ActionButton(
    text: String,
    icon: String? = null,
    isPrimary: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused && enabled) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "btnScale"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .height(48.dp)
            .clip(DiokkoShapes.medium)
            .then(
                if (isFocused && enabled) {
                    Modifier.shadow(
                        elevation = 16.dp,
                        shape = DiokkoShapes.medium,
                        ambientColor = if (isPrimary) DiokkoColors.AccentGlow else DiokkoColors.SecondaryGlow,
                        spotColor = if (isPrimary) DiokkoColors.AccentGlow else DiokkoColors.SecondaryGlow
                    )
                } else Modifier
            )
            .background(
                brush = when {
                    !enabled -> Brush.linearGradient(
                        colors = listOf(
                            DiokkoColors.SurfaceLight,
                            DiokkoColors.Surface
                        )
                    )
                    isPrimary -> DiokkoGradients.accentButton
                    else -> DiokkoGradients.secondaryButton
                },
                alpha = if (enabled) 1f else 0.5f
            )
            .then(
                if (isFocused && enabled) {
                    Modifier.border(
                        width = 2.dp,
                        color = DiokkoColors.TextPrimary.copy(alpha = 0.5f),
                        shape = DiokkoShapes.medium
                    )
                } else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (enabled && event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    onClick()
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = DiokkoDimens.spacingLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DiokkoDimens.spacingXs)
        ) {
            if (icon != null) {
                Text(
                    text = icon,
                    style = DiokkoTypography.bodyLarge.copy(
                        color = if (enabled) DiokkoColors.TextOnAccent else DiokkoColors.TextMuted
                    )
                )
            }
            Text(
                text = text,
                style = DiokkoTypography.button.copy(
                    color = if (enabled) DiokkoColors.TextOnAccent else DiokkoColors.TextMuted
                )
            )
        }
    }
}

/**
 * Section header with optional action
 */
@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionText: String? = null,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = DiokkoDimens.spacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = DiokkoTypography.headlineLarge
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = DiokkoTypography.bodyMedium
                )
            }
        }
        
        if (actionText != null) {
            var isFocused by remember { mutableStateOf(false) }
            
            Text(
                text = actionText,
                style = DiokkoTypography.labelLarge.copy(
                    color = if (isFocused) DiokkoColors.AccentLight else DiokkoColors.Accent
                ),
                modifier = Modifier
                    .clip(DiokkoShapes.small)
                    .background(
                        if (isFocused) DiokkoColors.Accent.copy(alpha = 0.1f) 
                        else Color.Transparent
                    )
                    .padding(horizontal = DiokkoDimens.spacingSm, vertical = DiokkoDimens.spacingXs)
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                            onAction()
                            true
                        } else false
                    }
            )
        }
    }
}

/**
 * Empty state with illustration
 */
@Composable
fun EmptyState(
    icon: String,
    title: String,
    message: String,
    actionText: String? = null,
    onAction: () -> Unit = {},
    buttonFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(DiokkoDimens.spacingXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .drawBehind {
                    drawCircle(
                        color = DiokkoColors.Accent.copy(alpha = 0.1f),
                        radius = size.minDimension / 2
                    )
                    drawCircle(
                        color = DiokkoColors.Accent.copy(alpha = 0.05f),
                        radius = size.minDimension / 1.5f
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                style = DiokkoTypography.displayLarge.copy(fontSize = 56.sp)
            )
        }
        
        Spacer(modifier = Modifier.height(DiokkoDimens.spacingLg))
        
        Text(
            text = title,
            style = DiokkoTypography.headlineLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(DiokkoDimens.spacingXs))
        
        Text(
            text = message,
            style = DiokkoTypography.bodyMedium,
            textAlign = TextAlign.Center
        )
        
        if (actionText != null) {
            Spacer(modifier = Modifier.height(DiokkoDimens.spacingLg))
            ActionButton(
                text = actionText,
                icon = "➕",
                onClick = onAction,
                focusRequester = buttonFocusRequester ?: remember { FocusRequester() }
            )
        }
    }
}

/**
 * Loading shimmer placeholder
 */
@Composable
fun ShimmerCard(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )
    
    Box(
        modifier = modifier
            .clip(DiokkoShapes.medium)
            .background(DiokkoColors.Surface)
            .drawBehind {
                val shimmerWidth = size.width * 0.3f
                val start = shimmerProgress * (size.width + shimmerWidth) - shimmerWidth
                
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            DiokkoColors.SurfaceLight.copy(alpha = 0.5f),
                            Color.Transparent
                        ),
                        startX = start,
                        endX = start + shimmerWidth
                    )
                )
            }
    )
}

/**
 * Badge/chip component
 */
@Composable
fun Badge(
    text: String,
    color: Color = DiokkoColors.Accent,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = DiokkoTypography.labelSmall.copy(
            color = color,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        ),
        modifier = modifier
            .clip(DiokkoShapes.pill)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
