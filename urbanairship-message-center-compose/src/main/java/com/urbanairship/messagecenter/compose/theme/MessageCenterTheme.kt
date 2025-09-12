package com.urbanairship.messagecenter.compose.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

private val LocalColors = compositionLocalOf { MessageCenterColors() }
@OptIn(ExperimentalMaterial3Api::class)
private val LocalListConfig = compositionLocalOf { MessageCenterListConfig() }
private val LocalErrorViewConfig = compositionLocalOf { MessageCenterErrorViewConfig() }
private val LocalLoadingViewConfig = compositionLocalOf { MessageCenterLoadingViewConfig() }

@OptIn(ExperimentalMaterial3Api::class)
private val LocalMessageViewConfig = compositionLocalOf { MessageViewConfig() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun MessageCenterTheme(
    colors: MessageCenterColors = MessageCenterColors(),
    listConfig: MessageCenterListConfig = MessageCenterListConfig(),
    messageViewConfig: MessageViewConfig = MessageViewConfig(),
    errorViewConfig: MessageCenterErrorViewConfig = MessageCenterErrorViewConfig(),
    loadingViewConfig: MessageCenterLoadingViewConfig = MessageCenterLoadingViewConfig(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalListConfig provides listConfig,
        LocalErrorViewConfig provides errorViewConfig,
        LocalLoadingViewConfig provides loadingViewConfig,
        LocalMessageViewConfig provides messageViewConfig
    ) {
        content()
    }
}

public object MessageCenterTheme {

    public val colors: MessageCenterColors
        @Composable
        @ReadOnlyComposable
        get() = LocalColors.current

    public val listConfig: MessageCenterListConfig
        @Composable
        @ReadOnlyComposable
        get() = LocalListConfig.current

    public val errorViewConfig: MessageCenterErrorViewConfig
        @Composable
        @ReadOnlyComposable
        get() = LocalErrorViewConfig.current

    public val loadingViewConfig: MessageCenterLoadingViewConfig
        @Composable
        @ReadOnlyComposable
        get() = LocalLoadingViewConfig.current

    public val messageViewConfig: MessageViewConfig
        @Composable
        @ReadOnlyComposable
        get() = LocalMessageViewConfig.current
}
