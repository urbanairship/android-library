package com.urbanairship.devapp.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.compose.material3.MaterialTheme as Material3Theme
import androidx.compose.material3.darkColorScheme as material3DarkColorScheme
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

internal object AirshipTvColors {
    internal val primary = Color(0xFF8FA2FF)
    internal val onPrimary = Color(0xFF001A80)
    internal val primaryContainer = Color(0xFF0023CA)
    internal val onPrimaryContainer = Color(0xFFDDE1FF)
    internal val pink = Color(0xFFFF5C7F)
    internal val onPink = Color(0xFF3D0012)
    internal val background = Color(0xFF0A0E27)
    internal val onBackground = Color(0xFFE4E7FF)
    internal val surfaceVariant = Color(0xFF1A2150)
    internal val onSurfaceVariant = Color(0xFFB8BEE8)
    internal val selected = Color(0xFF1E2A78)
    internal val onSelected = Color(0xFFDCE1FF)
    internal val focused = Color(0xFF3D5CFF)
    internal val onFocused = Color(0xFFFFFFFF)
    internal val border = Color(0xFF3A4380)
}

internal val TvColorScheme = tvDarkColorScheme(
    primary = AirshipTvColors.primary,
    onPrimary = AirshipTvColors.onPrimary,
    primaryContainer = AirshipTvColors.primaryContainer,
    onPrimaryContainer = AirshipTvColors.onPrimaryContainer,
    secondaryContainer = AirshipTvColors.selected,
    onSecondaryContainer = AirshipTvColors.onSelected,
    tertiary = AirshipTvColors.pink,
    onTertiary = AirshipTvColors.onPink,
    background = AirshipTvColors.background,
    onBackground = AirshipTvColors.onBackground,
    surface = AirshipTvColors.background,
    onSurface = AirshipTvColors.onBackground,
    surfaceVariant = AirshipTvColors.surfaceVariant,
    onSurfaceVariant = AirshipTvColors.onSurfaceVariant,
    inverseSurface = AirshipTvColors.focused,
    inverseOnSurface = AirshipTvColors.onFocused,
    border = AirshipTvColors.border,
)

internal val TvMaterial3ColorScheme = material3DarkColorScheme(
    primary = AirshipTvColors.primary,
    onPrimary = AirshipTvColors.onPrimary,
    primaryContainer = AirshipTvColors.primaryContainer,
    onPrimaryContainer = AirshipTvColors.onPrimaryContainer,
    secondaryContainer = AirshipTvColors.selected,
    onSecondaryContainer = AirshipTvColors.onSelected,
    tertiary = AirshipTvColors.pink,
    onTertiary = AirshipTvColors.onPink,
    background = AirshipTvColors.background,
    onBackground = AirshipTvColors.onBackground,
    surface = AirshipTvColors.background,
    onSurface = AirshipTvColors.onBackground,
    surfaceContainer = AirshipTvColors.background,
    surfaceVariant = AirshipTvColors.surfaceVariant,
    onSurfaceVariant = AirshipTvColors.onSurfaceVariant,
    outline = AirshipTvColors.border,
    outlineVariant = AirshipTvColors.border,
)

/**
 * Theme for the TV UI. Provides both tv-material and Material3 color schemes, since the TV screens
 * embed SDK composables built on Material3.
 */
@Composable
internal fun AirshipTvTheme(content: @Composable () -> Unit) {
    Material3Theme(colorScheme = TvMaterial3ColorScheme) {
        TvMaterialTheme(colorScheme = TvColorScheme) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                colors = SurfaceDefaults.colors(
                    containerColor = TvColorScheme.background,
                    contentColor = TvColorScheme.onBackground
                )
            ) {
                content()
            }
        }
    }
}

/** Button colors that use the theme's focus color instead of tv-material's default light pill. */
@Composable
internal fun tvButtonColors(): ButtonColors = ButtonDefaults.colors(
    containerColor = AirshipTvColors.surfaceVariant,
    contentColor = AirshipTvColors.onBackground,
    focusedContainerColor = AirshipTvColors.focused,
    focusedContentColor = AirshipTvColors.onFocused,
)
