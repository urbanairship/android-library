package com.urbanairship.preferencecenter.compose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import com.urbanairship.preferencecenter.PreferenceCenter

// non-@composable version of the theme, used for configuring the activity before compose is set up
// TODO: need to be able to set this globally somehow, and have the activity use it
public data class PreferenceCenterTheme(
    val lightColors: PreferenceCenterColors = PreferenceCenterColors.lightDefaults(),
    val darkColors: PreferenceCenterColors = PreferenceCenterColors.darkDefaults(),
    val typography: PreferenceCenterTypography = PreferenceCenterTypography.defaults(),
    val dimens: PreferenceCenterDimens = PreferenceCenterDimens.defaults(),
    val shapes: PreferenceCenterShapes = PreferenceCenterShapes.defaults(),
    val options: PreferenceCenterOptions = PreferenceCenterOptions.defaults(),
)

// @Composable function that applies the theme to its children, used for embedding the PC UI in a compose hierarchy
@Composable
public fun PreferenceCenterTheme(
    colors: PreferenceCenterColors = if (isSystemInDarkTheme()) PreferenceCenterColors.darkDefaults() else PreferenceCenterColors.lightDefaults(),
    typography: PreferenceCenterTypography = PreferenceCenterTypography.defaults(),
    dimens: PreferenceCenterDimens = PreferenceCenterDimens.defaults(),
    shapes: PreferenceCenterShapes = PreferenceCenterShapes.defaults(),
    options: PreferenceCenterOptions = PreferenceCenterOptions.defaults(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTypography provides typography,
        LocalDimens provides dimens,
        LocalShapes provides shapes,
        LocalOptions provides options
    ) {
        content()
    }
}

/** Convenience access to the [PreferenceCenterTheme] values for internal use. */
internal object PrefCenterTheme {
    val colors: PreferenceCenterColors
        @Composable
        @ReadOnlyComposable
        get() = LocalColors.current

    val dimens: PreferenceCenterDimens
        @Composable
        @ReadOnlyComposable
        get() = LocalDimens.current

    val typography: PreferenceCenterTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalTypography.current

    val shapes: PreferenceCenterShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalShapes.current

    val options: PreferenceCenterOptions
        @Composable
        @ReadOnlyComposable
        get() = LocalOptions.current
}

private val LocalColors = compositionLocalOf<PreferenceCenterColors> {
    error("No Preference Center colors provided! Preference Center UI components must be wrapped in a PreferenceCenterTheme.")
}

private val LocalDimens = compositionLocalOf<PreferenceCenterDimens> {
    error("No Preference Center dimens provided! Preference Center UI components must be wrapped in a PreferenceCenterTheme.")
}

private val LocalTypography = compositionLocalOf<PreferenceCenterTypography> {
    error("No Preference Center typography provided! Preference Center UI components must be wrapped in a PreferenceCenterTheme.")
}

private val LocalShapes = compositionLocalOf<PreferenceCenterShapes> {
    error("No Preference Center shapes provided! Preference Center UI components must be wrapped in a PreferenceCenterTheme.")
}

private val LocalOptions = compositionLocalOf<PreferenceCenterOptions> {
    error("No Preference Center options provided! Preference Center UI components must be wrapped in a PreferenceCenterTheme.")
}
