package com.diokko.player.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.diokko.player.ui.components.*
import com.diokko.player.ui.theme.*

@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val backFocusRequester = remember { FocusRequester() }
    
    // Add delay to ensure composable is attached before requesting focus
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try {
            backFocusRequester.requestFocus()
        } catch (e: Exception) {
            // FocusRequester might not be attached yet, ignore
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
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
            
            Text(
                text = "About",
                style = DiokkoTypography.displaySmall
            )
        }
        
        // Content
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(DiokkoDimens.spacingXl)
        ) {
            // Left - App info card
            Column(
                modifier = Modifier
                    .width(350.dp)
                    .clip(DiokkoShapes.large)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                DiokkoColors.Accent.copy(alpha = 0.1f),
                                DiokkoColors.Surface
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )
                    )
                    .padding(DiokkoDimens.spacingXl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated logo
                AnimatedLogo()
                
                Spacer(modifier = Modifier.height(DiokkoDimens.spacingLg))
                
                Text(
                    text = "Diokko Player",
                    style = DiokkoTypography.displaySmall.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                
                Spacer(modifier = Modifier.height(DiokkoDimens.spacingXs))
                
                Badge(
                    text = "v1.0.0",
                    color = DiokkoColors.Secondary
                )
                
                Spacer(modifier = Modifier.height(DiokkoDimens.spacingLg))
                
                Text(
                    text = "A modern IPTV player designed for Fire TV with a premium viewing experience.",
                    style = DiokkoTypography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(DiokkoDimens.spacingXl))
                
                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatBadge(value = "🔥", label = "Fire TV")
                    StatBadge(value = "4K", label = "Support")
                    StatBadge(value = "🌐", label = "Xtream")
                }
            }
            
            // Right - Features list
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(DiokkoShapes.large)
                    .background(DiokkoColors.Surface)
                    .padding(DiokkoDimens.spacingXl)
            ) {
                Text(
                    text = "Features",
                    style = DiokkoTypography.headlineLarge,
                    modifier = Modifier.padding(bottom = DiokkoDimens.spacingMd)
                )
                
                val features = listOf(
                    Feature("📺", "Live TV", "Watch live channels from M3U and Xtream playlists"),
                    Feature("🎬", "Movies", "Browse and watch movies from your playlists"),
                    Feature("📺", "TV Shows", "Enjoy series with episode organization"),
                    Feature("📋", "Playlist Support", "M3U, M3U8, and Xtream Codes API"),
                    Feature("🎮", "D-Pad Navigation", "Optimized for Fire TV remote control"),
                    Feature("🎨", "Premium UI", "Modern, beautiful interface design"),
                    Feature("⚡", "Fast Playback", "Quick channel switching and buffering"),
                    Feature("🔄", "Auto Refresh", "Keep playlists up to date automatically")
                )
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(DiokkoDimens.spacingSm)
                ) {
                    itemsIndexed(features) { index, feature ->
                        FeatureItem(
                            feature = feature,
                            index = index
                        )
                    }
                }
            }
        }
        
        // Footer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DiokkoDimens.spacingLg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Made with ❤️ for Fire TV",
                style = DiokkoTypography.bodySmall
            )
        }
    }
}

@Composable
fun AnimatedLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "logo")
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Box(
        modifier = Modifier
            .size(120.dp)
            .drawBehind {
                // Outer glow
                drawCircle(
                    color = DiokkoColors.Accent.copy(alpha = glowAlpha * 0.3f),
                    radius = size.minDimension / 1.5f
                )
                drawCircle(
                    color = DiokkoColors.Accent.copy(alpha = glowAlpha * 0.5f),
                    radius = size.minDimension / 2f
                )
            }
            .clip(DiokkoShapes.large)
            .background(
                brush = DiokkoGradients.accentButton
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "D",
            style = DiokkoTypography.displayLarge.copy(
                color = DiokkoColors.TextOnAccent,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
fun StatBadge(
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = DiokkoTypography.headlineMedium.copy(
                color = DiokkoColors.Accent
            )
        )
        Spacer(modifier = Modifier.height(DiokkoDimens.spacingXxs))
        Text(
            text = label,
            style = DiokkoTypography.labelSmall
        )
    }
}

data class Feature(
    val icon: String,
    val title: String,
    val description: String
)

@Composable
fun FeatureItem(
    feature: Feature,
    index: Int
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "featureScale"
    )
    
    // Staggered entrance animation
    val animatedAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 300,
            delayMillis = index * 50
        ),
        label = "featureAlpha"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(DiokkoShapes.medium)
            .background(
                if (isFocused) DiokkoColors.SurfaceElevated else DiokkoColors.SurfaceLight
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(DiokkoDimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(DiokkoShapes.small)
                .background(
                    if (isFocused) DiokkoColors.Accent.copy(alpha = 0.2f)
                    else DiokkoColors.Surface
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = feature.icon,
                style = DiokkoTypography.headlineSmall
            )
        }
        
        Spacer(modifier = Modifier.width(DiokkoDimens.spacingMd))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feature.title,
                style = DiokkoTypography.labelLarge.copy(
                    color = if (isFocused) DiokkoColors.TextPrimary else DiokkoColors.TextPrimary
                )
            )
            Text(
                text = feature.description,
                style = DiokkoTypography.bodySmall
            )
        }
        
        if (isFocused) {
            Text(
                text = "✓",
                style = DiokkoTypography.headlineSmall.copy(
                    color = DiokkoColors.Accent
                )
            )
        }
    }
}
