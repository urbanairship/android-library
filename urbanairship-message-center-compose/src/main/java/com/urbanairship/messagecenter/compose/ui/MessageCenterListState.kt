package com.urbanairship.messagecenter.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.Predicate
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.compose.ui.MessageCenterListViewModel.Action
import com.urbanairship.messagecenter.compose.ui.MessageCenterListViewModel.State
import com.urbanairship.messagecenter.ui.view.MessageListAction

/**
 * State holder for the Message Center list screen.
 */
@Stable
public class MessageCenterListState internal constructor(
    private val onAction: (Action) -> Unit
) {
    internal var viewState by mutableStateOf<State>(State.Loading)

    /** Whether the list is in editing mode. */
    public val isEditing: Boolean by derivedStateOf {
        (viewState as? State.Content)?.isEditing == true
    }

    internal val selectedCount: Int by derivedStateOf {
        (viewState as? State.Content)?.selectedMessageIds?.size ?: 0
    }

    internal val areAllMessagesSelected by derivedStateOf {
        (viewState as? State.Content)?.areAllMessagesSelected == true
    }

    internal fun onAction(action: Action) {
        onAction.invoke(action)
    }
}

/**
 * Remembers a [MessageCenterListState].
 *
 * @param predicate An optional predicate to filter messages.
 * @param highlightedMessageId An optional message ID to highlight.
 */
@Composable
public fun rememberMessageCenterListState(
    predicate: Predicate<Message>? = null,
    highlightedMessageId: String? = null
): MessageCenterListState {
    val viewModel: DefaultMessageCenterListViewModel = viewModel(
        factory = DefaultMessageCenterListViewModel.Factory,
        extras = MutableCreationExtras().apply {
            set(DefaultMessageCenterListViewModel.PREDICATE_KEY, predicate)
            set(DefaultMessageCenterListViewModel.HIGHLIGHTED_MESSAGE_KEY, highlightedMessageId)
        }
    )

    return rememberMessageCenterListState(viewModel)
}

@Composable
internal fun rememberMessageCenterListState(
    viewModel: MessageCenterListViewModel
): MessageCenterListState {
    val state = remember { MessageCenterListState(viewModel::handle) }

    LaunchedEffect(Unit) {
        with(viewModel.scope.coroutineContext) {
            viewModel.states.collect {
                state.viewState = it
            }
        }
    }

    return state
}
