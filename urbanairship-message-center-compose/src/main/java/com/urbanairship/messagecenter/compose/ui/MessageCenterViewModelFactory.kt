package com.urbanairship.messagecenter.compose.ui

import androidx.lifecycle.ViewModelProvider
import com.urbanairship.Predicate
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.ui.view.MessageListViewModel
import com.urbanairship.messagecenter.ui.view.MessageViewModel

public class MessageCenterViewModelFactory(
    predicate: Predicate<Message>? = null
) {
    public val factory: ViewModelProvider.Factory = MessageListViewModel.factory(predicate)
}

public class MessageViewModelFactory() {
    public val factory: ViewModelProvider.Factory = MessageViewModel.factory()
}
