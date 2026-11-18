package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class QuantExtendedColors(
    val positiveEv: Color,
    val positiveEvContainer: Color,
    val correlationCyan: Color,
    val correlationContainer: Color,
    val moderateAmber: Color,
    val moderateContainer: Color,
    val negativeCoral: Color,
    val negativeContainer: Color,
    val cardBorder: Color,
    val elevatedSurface: Color
)

val LocalQuantColors = staticCompositionLocalOf {
    QuantExtendedColors(
        positiveEv = ElectricEmerald,
        positiveEvContainer = EmeraldDim,
        correlationCyan = PrecisionCyan,
        correlationContainer = CyanDim,
        moderateAmber = AmberEdge,
        moderateContainer = AmberDim,
        negativeCoral = CoralTrap,
        negativeContainer = CoralDim,
        cardBorder = BorderHairline,
        elevatedSurface = GraphiteElevated
    )
}

private val DarkQuantColors = QuantExtendedColors(
    positiveEv = ElectricEmerald,
    positiveEvContainer = EmeraldDim,
    correlationCyan = PrecisionCyan,
    correlationContainer = CyanDim,
    moderateAmber = AmberEdge,
    moderateContainer = AmberDim,
    negativeCoral = CoralTrap,
    negativeContainer = CoralDim,
    cardBorder = BorderHairline,
    elevatedSurface = GraphiteElevated
)

private val LightQuantColors = QuantExtendedColors(
    positiveEv = LightEmerald,
    positiveEvContainer = Color(0xFFD1FAE5),
    correlationCyan = LightCyan,
    correlationContainer = Color(0xFFE0F2FE),
    moderateAmber = Color(0xFFD97706),
    moderateContainer = Color(0xFFFEF3C7),
    negativeCoral = Color(0xFFDC2626),
    negativeContainer = Color(0xFFFEE2E2),
    cardBorder = LightBorder,
    elevatedSurface = Color(0xFFF1F5F9)
)

private val CorrEdgeDarkColorScheme = darkColorScheme(
    primary = ElectricEmerald,
    onPrimary = ObsidianBg,
    primaryContainer = EmeraldDim,
    onPrimaryContainer = EmeraldSoft,
    secondary = PrecisionCyan,
    onSecondary = ObsidianBg,
    secondaryContainer = CyanDim,
    onSecondaryContainer = PrecisionCyan,
    tertiary = AmberEdge,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = GraphiteCard,
    onSurfaceVariant = TextSecondary,
    outline = BorderHairline,
    error = CoralTrap,
    onError = ObsidianBg
)

private val CorrEdgeLightColorScheme = lightColorScheme(
    primary = LightEmerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = LightCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightCard,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = Color(0xFFDC2626),
    onError = Color.White
)

val CorrEdgeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CorrEdgeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CorrEdgeDarkColorScheme else CorrEdgeLightColorScheme
    val quantColors = if (darkTheme) DarkQuantColors else LightQuantColors

    CompositionLocalProvider(LocalQuantColors provides quantColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CorrEdgeTypography,
            shapes = CorrEdgeShapes,
            content = content
        )
    }
}
