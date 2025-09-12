package com.urbanairship.messagecenter.compose.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.ui.view.MessageViewState

@OptIn(ExperimentalMaterial3Api::class)
public class MessageViewConfig(
    /** Message Center list item app bar colors config */
    public val topAppBarColors: (@Composable () -> TopAppBarColors) = { TopAppBarDefaults.topAppBarColors() },

    /** Listener interface for `MessageView` UI updates and UI interactions. */
    public val listener: Listener? = null
) {
    public interface Listener {
        /** Called when a [message] is loaded. */
        public fun onMessageLoaded(message: Message)
        /** Called when a message load error occurs. */
        public fun onMessageLoadError(error: MessageViewState.Error.Type)
        /** Called when the retry button is clicked. */
        public fun onRetryClicked()
    }
}
