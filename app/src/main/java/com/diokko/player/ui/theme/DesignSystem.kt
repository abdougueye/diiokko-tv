package com.diokko.player.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Diokko Player Design System
 * Premium TV-optimized visual language
 */
object DiokkoColors {
    // Primary palette - Deep space theme
    val Background = Color(0xFF0A0E1A)
    val BackgroundGradientStart = Color(0xFF0F1629)
    val BackgroundGradientEnd = Color(0xFF0A0E1A)
    
    // Surface colors
    val Surface = Color(0xFF161B2E)
    val SurfaceLight = Color(0xFF1E2642)
    val SurfaceElevated = Color(0xFF252D47)
    
    // Accent colors - Vibrant coral/pink
    val Accent = Color(0xFFE94560)
    val AccentLight = Color(0xFFFF6B8A)
    val AccentDark = Color(0xFFBF3750)
    val AccentGlow = Color(0x40E94560)
    
    // Secondary accent - Electric blue
    val Secondary = Color(0xFF4D9DE0)
    val SecondaryLight = Color(0xFF7BB8EC)
    val SecondaryGlow = Color(0x404D9DE0)
    
    // Tertiary - Purple
    val Tertiary = Color(0xFF7B68EE)
    val TertiaryGlow = Color(0x407B68EE)
    
    // Text colors
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB8C0D2)
    val TextMuted = Color(0xFF6B7394)
    val TextOnAccent = Color(0xFFFFFFFF)
    
    // State colors
    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFFB74D)
    val Error = Color(0xFFEF5350)
    
    // Focus & selection
    val FocusBorder = Color(0xFFE94560)
    val FocusGlow = Color(0x60E94560)
    val Selected = Color(0x30E94560)
    
    // Card gradients
    val CardGradientStart = Color(0xFF1A2038)
    val CardGradientEnd = Color(0xFF141929)
    
    // Nav rail
    val NavRailBg = Color(0xFF0D1220)
    val NavRailGradientStart = Color(0xFF12182A)
    val NavRailGradientEnd = Color(0xFF0A0E1A)
}

object DiokkoGradients {
    val background = Brush.verticalGradient(
        colors = listOf(
            DiokkoColors.BackgroundGradientStart,
            DiokkoColors.BackgroundGradientEnd
        )
    )
    
    val backgroundRadial = Brush.radialGradient(
        colors = listOf(
            DiokkoColors.SurfaceLight.copy(alpha = 0.3f),
            Color.Transparent
        )
    )
    
    val navRail = Brush.verticalGradient(
        colors = listOf(
            DiokkoColors.NavRailGradientStart,
            DiokkoColors.NavRailGradientEnd
        )
    )
    
    val card = Brush.linearGradient(
        colors = listOf(
            DiokkoColors.CardGradientStart,
            DiokkoColors.CardGradientEnd
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )
    
    val cardHover = Brush.linearGradient(
        colors = listOf(
            DiokkoColors.SurfaceElevated,
            DiokkoColors.SurfaceLight
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )
    
    val accentButton = Brush.horizontalGradient(
        colors = listOf(
            DiokkoColors.Accent,
            DiokkoColors.AccentLight
        )
    )
    
    val secondaryButton = Brush.horizontalGradient(
        colors = listOf(
            DiokkoColors.Secondary,
            DiokkoColors.SecondaryLight
        )
    )
    
    val shimmer = Brush.linearGradient(
        colors = listOf(
            DiokkoColors.Surface,
            DiokkoColors.SurfaceLight,
            DiokkoColors.Surface
        )
    )
}

object DiokkoTypography {
    val displayLarge = TextStyle(
        color = DiokkoColors.TextPrimary,
        fontSize = 48.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    )
    
    val displayMedium = TextStyle(
        color = DiokkoColors.TextPrimary,
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25).sp
    )
    
    val displaySmall = TextStyle(
        color = DiokkoColors.TextPrimary,
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold
    )
    
    val headlineLarge = TextStyle(
        color = DiokkoColors.TextPrimary,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold
    )
    
    val headlineMedium = TextStyle(
        color = DiokkoColors.TextPrimary,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold
    )
    
    val headlineSmall = TextStyle(
        color = DiokkoColors.TextPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium
    )
    
    val titleLarge = TextStyle(
        color = DiokkoColors.TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold
    )
    
    val titleMedium = TextStyle(
        color = DiokkoColors.TextPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold
    )
    
    val titleSmall = TextStyle(
        color = DiokkoColors.TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
    )
    
    val bodyLarge = TextStyle(
        color = DiokkoColors.TextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp
    )
    
    val bodyMedium = TextStyle(
        color = DiokkoColors.TextSecondary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    )
    
    val bodySmall = TextStyle(
        color = DiokkoColors.TextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp
    )
    
    val labelLarge = TextStyle(
        color = DiokkoColors.TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    )
    
    val labelMedium = TextStyle(
        color = DiokkoColors.TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    )
    
    val labelSmall = TextStyle(
        color = DiokkoColors.TextMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    )
    
    val button = TextStyle(
        color = DiokkoColors.TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp
    )
    
    val navItem = TextStyle(
        color = DiokkoColors.TextSecondary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium
    )
    
    val navItemSelected = TextStyle(
        color = DiokkoColors.TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
    )
    
    // With shadow for overlays
    val titleWithShadow = TextStyle(
        color = DiokkoColors.TextPrimary,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.5f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        )
    )
}

object DiokkoShapes {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(12.dp)
    val large = RoundedCornerShape(16.dp)
    val extraLarge = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(50)
}

object DiokkoDimens {
    // Spacing
    val spacingXxs = 4.dp
    val spacingXs = 8.dp
    val spacingSm = 12.dp
    val spacingMd = 16.dp
    val spacingLg = 24.dp
    val spacingXl = 32.dp
    val spacingXxl = 48.dp
    
    // Nav rail
    val navRailWidth = 80.dp
    val navRailExpandedWidth = 240.dp
    val navItemHeight = 56.dp
    
    // Cards
    val cardElevation = 8.dp
    val cardBorderWidth = 1.dp
    val focusBorderWidth = 2.dp
    
    // Content
    val contentMaxWidth = 1200.dp
    val posterWidth = 160.dp
    val posterHeight = 240.dp
    val thumbnailWidth = 280.dp
    val thumbnailHeight = 158.dp
}

/**
 * Focus-aware modifier that provides visual feedback
 */
@Composable
fun Modifier.focusHighlight(
    isFocused: Boolean,
    shape: RoundedCornerShape = DiokkoShapes.medium,
    glowColor: Color = DiokkoColors.FocusGlow,
    borderColor: Color = DiokkoColors.FocusBorder,
    scaleOnFocus: Float = 1.05f
): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) scaleOnFocus else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "focusScale"
    )
    
    val borderAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(200),
        label = "borderAlpha"
    )
    
    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.6f else 0f,
        animationSpec = tween(200),
        label = "glowAlpha"
    )
    
    return this
        .scale(scale)
        .then(
            if (isFocused) {
                Modifier
                    .shadow(
                        elevation = 16.dp,
                        shape = shape,
                        ambientColor = glowColor.copy(alpha = glowAlpha),
                        spotColor = glowColor.copy(alpha = glowAlpha)
                    )
                    .border(
                        width = DiokkoDimens.focusBorderWidth,
                        color = borderColor.copy(alpha = borderAlpha),
                        shape = shape
                    )
            } else {
                Modifier
            }
        )
}

/**
 * Animated glow effect for focused elements
 */
@Composable
fun Modifier.glowEffect(
    enabled: Boolean,
    color: Color = DiokkoColors.Accent,
    radius: Dp = 24.dp
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    return if (enabled) {
        this.drawBehind {
            drawCircle(
                color = color.copy(alpha = glowAlpha),
                radius = radius.toPx()
            )
        }
    } else {
        this
    }
}

/**
 * Card background with gradient
 */
fun Modifier.cardBackground(
    isFocused: Boolean = false,
    shape: RoundedCornerShape = DiokkoShapes.medium
): Modifier = this
    .clip(shape)
    .background(
        brush = if (isFocused) DiokkoGradients.cardHover else DiokkoGradients.card,
        shape = shape
    )

/**
 * Screen background with gradient
 */
fun Modifier.screenBackground(): Modifier = this
    .fillMaxSize()
    .background(DiokkoGradients.background)
