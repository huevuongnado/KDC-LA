package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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

val PortalShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

val LocalPortalSemanticColors = staticCompositionLocalOf {
    lightSemanticColors(PortalThemePreset.GREEN)
}

object PortalTheme {
    val colors: PortalSemanticColors
        @Composable get() = LocalPortalSemanticColors.current
}

@Composable
fun ThonLongAnTheme(
    themeKey: String = "green",
    baseFontSize: Int = 15,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val preset = PortalThemePreset.fromKey(themeKey)
    val semanticColors = if (darkTheme) darkSemanticColors(preset) else lightSemanticColors(preset)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = preset.darkPrimary,
            onPrimary = preset.onDarkPrimary,
            primaryContainer = preset.darkContainer,
            onPrimaryContainer = preset.onDarkContainer,
            secondary = preset.darkPrimary.copy(alpha = 0.85f),
            onSecondary = preset.onDarkPrimary,
            secondaryContainer = SurfaceVariantDark,
            onSecondaryContainer = TextPrimaryDark,
            background = SurfaceDark,
            onBackground = TextPrimaryDark,
            surface = CardDark,
            onSurface = TextPrimaryDark,
            surfaceVariant = SurfaceVariantDark,
            onSurfaceVariant = TextMutedDark,
            surfaceContainerLowest = Color(0xFF111815),
            surfaceContainerLow = Color(0xFF19221D),
            surfaceContainer = CardDark,
            surfaceContainerHigh = Color(0xFF25322B),
            surfaceContainerHighest = Color(0xFF2D3C34),
            outline = OutlineDark,
            outlineVariant = Color(0xFF2D3B33),
            error = Color(0xFFFCA5A5),
            onError = Color(0xFF450A0A),
            errorContainer = Color(0xFF5B1D1D),
            onErrorContainer = Color(0xFFFEE2E2),
        )
    } else {
        lightColorScheme(
            primary = preset.primary,
            onPrimary = Color.White,
            primaryContainer = preset.softContainer,
            onPrimaryContainer = preset.onSoftContainer,
            secondary = preset.headerEnd,
            onSecondary = Color.White,
            secondaryContainer = SurfaceVariantLight,
            onSecondaryContainer = TextPrimaryLight,
            background = SurfaceLight,
            onBackground = TextPrimaryLight,
            surface = CardLight,
            onSurface = TextPrimaryLight,
            surfaceVariant = SurfaceVariantLight,
            onSurfaceVariant = TextMutedLight,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF7FAF8),
            surfaceContainer = CardLight,
            surfaceContainerHigh = Color(0xFFEDF3EF),
            surfaceContainerHighest = Color(0xFFE3ECE6),
            outline = OutlineLight,
            outlineVariant = Color(0xFFE2E8F0),
            error = Color(0xFFB91C1C),
            onError = Color.White,
            errorContainer = Color(0xFFFEE2E2),
            onErrorContainer = Color(0xFF7F1D1D),
        )
    }

    CompositionLocalProvider(LocalPortalSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = buildPortalTypography(baseFontSize),
            shapes = PortalShapes,
            content = content,
        )
    }
}
