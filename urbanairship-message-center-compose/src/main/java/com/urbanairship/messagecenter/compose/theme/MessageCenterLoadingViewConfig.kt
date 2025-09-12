package com.urbanairship.messagecenter.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

public class MessageCenterLoadingViewConfig(
    public val backgroundColor: Color? = null,
    public val spinner: (@Composable () -> Unit)? = null
) {}
