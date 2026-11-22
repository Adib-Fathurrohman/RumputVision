package com.rumput.vision.core.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Palette.Grass400,
    onPrimary = Palette.Grass900,
    primaryContainer = Palette.Grass700,
    onPrimaryContainer = Palette.Grass100,
    secondary = Palette.Slate300,
    onSecondary = Palette.Ink950,
    secondaryContainer = Palette.Slate800,
    onSecondaryContainer = Palette.Ink100,
    background = Palette.Ink950,
    onBackground = Palette.Ink100,
    surface = Palette.Ink950,
    onSurface = Palette.Ink100,
    surfaceVariant = Palette.Ink750,
    onSurfaceVariant = Palette.Ink300,
    surfaceContainerLowest = Palette.Ink950,
    surfaceContainerLow = Palette.Ink900,
    surfaceContainer = Palette.Ink850,
    surfaceContainerHigh = Palette.Ink800,
    surfaceContainerHighest = Palette.Ink750,
    outline = Palette.Ink600,
    outlineVariant = Palette.Ink700,
    error = Palette.Red400,
    onError = Palette.Ink950,
    errorContainer = Palette.Red900,
    onErrorContainer = Palette.Ink100,
)

// Defined for future "follow system" support; the app is dark-only for now.
private val LightScheme: ColorScheme = lightColorScheme(
    primary = Palette.Grass700,
    onPrimary = Palette.Paper,
    background = Palette.Paper,
    surface = Palette.Paper,
    surfaceContainer = Palette.PaperDim,
)

private val RumputShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun RumputTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = RumputTypography,
            shapes = RumputShapes,
            content = content,
        )
    }
}

/** Access to the Rumput design tokens that Material 3 does not model. */
object RumputTheme {
    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current

    val statusColors: StatusColors
        @Composable @ReadOnlyComposable get() = LocalStatusColors.current
}
