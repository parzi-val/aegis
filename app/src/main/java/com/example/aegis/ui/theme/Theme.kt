package com.example.aegis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// TODO Phase later: add darkColorScheme if dark mode support is desired
private val LightColorScheme = lightColorScheme(
    primary                = WarmNavy,
    onPrimary              = Color.White,
    primaryContainer       = WarmNavyContainer,
    onPrimaryContainer     = WarmNavyContainerContent,
    inversePrimary         = WarmNavyInverse,

    secondary              = WarmCharcoal,
    onSecondary            = Color.White,
    secondaryContainer     = WarmCharcoalContainer,
    onSecondaryContainer   = WarmCharcoalContainerContent,

    tertiary               = WarmGold,
    onTertiary             = Color.White,
    tertiaryContainer      = WarmGoldContainer,
    onTertiaryContainer    = WarmGoldContainerContent,

    error                  = WarmError,
    onError                = Color.White,
    errorContainer         = WarmErrorContainer,
    onErrorContainer       = WarmErrorContainerContent,

    background             = WarmBackground,
    onBackground           = WarmOnBackground,
    surface                = WarmSurface,
    onSurface              = WarmOnSurface,
    surfaceVariant         = WarmSurfaceVariant,
    onSurfaceVariant       = WarmOnSurfaceVariant,
    surfaceTint            = WarmSurfaceTint,

    surfaceContainerLowest  = WarmSurfaceContainerLowest,
    surfaceContainerLow     = WarmSurfaceContainerLow,
    surfaceContainer        = WarmSurfaceContainer,
    surfaceContainerHigh    = WarmSurfaceContainerHigh,
    surfaceContainerHighest = WarmSurfaceContainerHighest,

    inverseSurface         = WarmInverseSurface,
    inverseOnSurface       = WarmInverseOnSurface,
    outline                = WarmOutline,
    outlineVariant         = WarmOutlineVariant,
)

@Composable
fun AegisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content,
    )
}
