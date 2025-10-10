package com.urbanairship.devapp.inbox

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidViewBinding
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.ui.MessageCenterFragment
import com.urbanairship.messagecenter.ui.view.MessageViewState
import com.urbanairship.devapp.databinding.FragmentInboxBinding

@Composable
fun InboxScreen(
    modifier: Modifier = Modifier,
    messageId: String? = null,
    onMessageSelected: (String?) -> Unit
) {
    Scaffold(modifier = modifier) { paddingValues ->
        Surface(Modifier.padding(paddingValues)) {
            AndroidViewBinding(FragmentInboxBinding::inflate) {
                val fragment = fragmentContainerView.getFragment<MessageCenterFragment>()

                // Show the message pane if we have a message ID to display
                messageId?.let { fragment.showMessage(it) }

                fragment.listener = object : MessageCenterFragment.Listener {
                    override fun onShowMessage(message: Message): Boolean {
                        onMessageSelected(message.id)
                        return false
                    }

                    override fun onCloseMessage() {
                        onMessageSelected(null)
                    }

                    override fun onListEditModeChanged(isEditing: Boolean) = Unit
                    override fun onMessageLoaded(message: Message) = Unit
                    override fun onMessageLoadError(error: MessageViewState.Error.Type) = Unit
                }
            }
        }
    }
}
