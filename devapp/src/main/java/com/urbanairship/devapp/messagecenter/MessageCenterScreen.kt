package com.urbanairship.devapp.messagecenter

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.urbanairship.messagecenter.compose.theme.MessageCenterColors
import com.urbanairship.messagecenter.compose.theme.MessageCenterTheme
import com.urbanairship.messagecenter.compose.ui.MessageCenterScreen
import com.urbanairship.messagecenter.compose.ui.MessageCenterState
import com.urbanairship.messagecenter.compose.ui.rememberMessageCenterState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageCenterScreen(
    state: MessageCenterState = rememberMessageCenterState(),
) {

    val lightColors = MessageCenterColors.lightDefaults(
        background = MaterialTheme.colorScheme.surfaceContainer,
        surface = MaterialTheme.colorScheme.surface,
        accent = MaterialTheme.colorScheme.primary,
        divider = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        error = MaterialTheme.colorScheme.error
    )

    val darkColors = MessageCenterColors.darkDefaults(
        background = MaterialTheme.colorScheme.surfaceContainer,
        surface = MaterialTheme.colorScheme.surface,
        accent = MaterialTheme.colorScheme.primary,
        divider = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        error = MaterialTheme.colorScheme.error
    )

    MessageCenterTheme(
        colors = if (isSystemInDarkTheme()) darkColors else lightColors
    ) {
        MessageCenterScreen(state = state)
    }
}
