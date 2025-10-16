package com.urbanairship.messagecenter.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

@OptIn(ExperimentalMaterial3Api::class)
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

public object MessageCenterTheme {

    public val colors: MessageCenterColors
        @Composable
        @ReadOnlyComposable
        get() = LocalColors.current

    public val dimensions: MessageCenterDimens
        @Composable
        @ReadOnlyComposable
        get() = LocalDimens.current

    public val options: MessageCenterOptions
        @Composable
        @ReadOnlyComposable
        get() = LocalOptions.current

    public val typography: MessageCenterTypography
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
