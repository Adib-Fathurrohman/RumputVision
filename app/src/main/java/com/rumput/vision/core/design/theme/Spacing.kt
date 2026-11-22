package com.rumput.vision.core.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    /** Horizontal screen gutter. */
    val gutter: Dp = 16.dp,
)

internal val LocalSpacing = staticCompositionLocalOf { Spacing() }
internal val LocalStatusColors = staticCompositionLocalOf { DarkStatusColors }
