package com.urbanairship.messagecenter.compose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

/**
 * Message Center Theme
 *
 * This non-@composable version of the theme is used for configuring the default
 * `MessageCenterActivity` and should not be used in Composables.
 */
public data class MessageCenterTheme(
    val lightColors: MessageCenterColors = MessageCenterColors.lightDefaults(),
    val darkColors: MessageCenterColors = MessageCenterColors.darkDefaults(),
    val typography: MessageCenterTypography = MessageCenterTypography.defaults(),
    val dimens: MessageCenterDimens = MessageCenterDimens.defaults(),
    val options: MessageCenterOptions = MessageCenterOptions.defaults()
)

/**
 * Message Center Theme
 *
 * Applies the provided Message Center theming to the nested composable content.
 *
 * @param colors The Message Center colors
 * @param options The Message Center options
 * @param typography The Message Center typography
 * @param dimens The Message Center dimensions
 * @param content The composable content to apply the theme to
 */
@Composable
public fun MessageCenterTheme(
    colors: MessageCenterColors = if(isSystemInDarkTheme()) MessageCenterColors.darkDefaults() else MessageCenterColors.lightDefaults(),
    options: MessageCenterOptions = MessageCenterOptions.defaults(),
    typography: MessageCenterTypography = MessageCenterTypography.defaults(),
    dimens: MessageCenterDimens = MessageCenterDimens.defaults(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalOptions provides options,
        LocalTypography provides typography,
        LocalDimens provides dimens
    ) {
        content()
    }
}

internal object MsgCenterTheme {

    val colors: MessageCenterColors
        @Composable
        @ReadOnlyComposable
        get() = LocalColors.current

    val dimensions: MessageCenterDimens
        @Composable
        @ReadOnlyComposable
        get() = LocalDimens.current

    val options: MessageCenterOptions
        @Composable
        @ReadOnlyComposable
        get() = LocalOptions.current

    val typography: MessageCenterTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalTypography.current
}

private val LocalColors = compositionLocalOf<MessageCenterColors> {
    error("No Message Center colors provided! Message Center UI components must be wrapped in a MessageCenterTheme.")
}

private val LocalDimens = compositionLocalOf<MessageCenterDimens> {
    error("No Message Center dimensions provided! Message Center UI components must be wrapped in a MessageCenterTheme.")
}

private val LocalOptions = compositionLocalOf<MessageCenterOptions> {
    error("No Message Center options provided! Message Center UI components must be wrapped in a MessageCenterTheme.")
}

private val LocalTypography = compositionLocalOf<MessageCenterTypography> {
    error("No Message Center typography provided! Message Center UI components must be wrapped in a MessageCenterTheme.")
}
